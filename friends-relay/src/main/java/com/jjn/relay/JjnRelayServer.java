package com.jjn.relay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Lightweight standalone JJN Friends Presence, Invite & World-Tunnel Relay Server.
 *
 * Uses a single TCP port (default 25580) for:
 *  - CONTROL: persistent newline-delimited JSON presence, friends, requests, invites
 *  - TUNNEL_JOIN <tunnelId> <joinerUuid> <joinerName>: friend joining a hosted world
 *  - TUNNEL_BIND <connId>: host outbound connection paired to TUNNEL_JOIN
 */
public final class JjnRelayServer {

    public static final int DEFAULT_PORT = 25580;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT = new Gson();

    private final int port;
    private final Path dataFile;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    private final Object lock = new Object();
    private RelayDatabase db = new RelayDatabase();

    private final Map<String, List<ControlSession>> onlineByUuid = new ConcurrentHashMap<>();
    private final Map<String, ActiveTunnel> tunnelsById = new ConcurrentHashMap<>();
    private final Map<String, PendingTunnelJoin> pendingJoins = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private static final class FriendReq {
        String fromUuid;
        String fromName;
        String toUuid;
        String toName;
        long createdAt;
    }

    private static final class RelayDatabase {
        Map<String, String> knownNames = new LinkedHashMap<>(); // uuid -> username
        Map<String, Set<String>> friends = new LinkedHashMap<>(); // uuid -> set of friend uuids
        List<FriendReq> requests = new ArrayList<>();
    }

    private static final class PresenceState {
        String uuid;
        String username;
        String role = "launcher"; // "launcher" or "game"
        String state = "In Launcher";
        String mcVersion = "";
        String worldOrServer = "";
        String directEndpoint = "";
        String tunnelId = "";
        boolean hosting = false;
        boolean friendsOnly = true;
        String capeId = "";
        String backId = "";
        String hatId = "";
        String auraId = "";
        String activeEmoteId = "";
        long activeEmoteAtMs = 0L;
        long updatedAt = System.currentTimeMillis();
    }

    private static final class ControlSession {
        final Socket socket;
        final OutputStream out;
        PresenceState presence = new PresenceState();

        ControlSession(Socket socket) throws IOException {
            this.socket = socket;
            this.out = socket.getOutputStream();
        }

        synchronized void sendJson(JsonObject obj) throws IOException {
            byte[] bytes = (COMPACT.toJson(obj) + "\n").getBytes(StandardCharsets.UTF_8);
            out.write(bytes);
            out.flush();
        }
    }

    private static final class ActiveTunnel {
        final String tunnelId;
        final String hostUuid;
        final String hostName;
        final String worldName;
        final String mcVersion;
        final boolean friendsOnly;
        final Set<String> allowedUuids = ConcurrentHashMap.newKeySet();
        volatile ControlSession hostSession;

        ActiveTunnel(String tunnelId, String hostUuid, String hostName, String worldName, String mcVersion, boolean friendsOnly) {
            this.tunnelId = tunnelId;
            this.hostUuid = hostUuid;
            this.hostName = hostName;
            this.worldName = worldName;
            this.mcVersion = mcVersion;
            this.friendsOnly = friendsOnly;
        }
    }

    private static final class PendingTunnelJoin {
        final Socket joinSocket;
        final CompletableFuture<Socket> hostBindFuture = new CompletableFuture<>();

        PendingTunnelJoin(Socket joinSocket) {
            this.joinSocket = joinSocket;
        }
    }

    private volatile ServerSocket serverSocket;
    private ScheduledExecutorService reaper;

    public JjnRelayServer(int port, Path dataFile) {
        this.port = port;
        this.dataFile = dataFile;
    }

    public static void main(String[] args) throws Exception {
        int port = DEFAULT_PORT;
        String envPort = firstNonBlank(System.getenv("JJN_RELAY_PORT"), System.getenv("PORT"));
        if (envPort != null) {
            try {
                int parsed = Integer.parseInt(envPort.trim());
                if (parsed > 0 && parsed <= 65535) port = parsed;
            } catch (NumberFormatException ignored) {}
        }

        String envData = System.getenv("JJN_RELAY_DATA");
        Path dataFile;
        if (envData != null && !envData.isBlank()) {
            dataFile = Paths.get(envData.trim());
        } else if (Files.isDirectory(Paths.get("/data"))) {
            dataFile = Paths.get("/data/relay-data.json");
        } else {
            dataFile = Paths.get("relay-data.json");
        }

        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if ("--data".equals(args[i]) && i + 1 < args.length) {
                dataFile = Paths.get(args[++i]);
            }
        }
        JjnRelayServer server = new JjnRelayServer(port, dataFile);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "JJN-Relay-Shutdown"));
        server.start();
    }

    public int getPort() {
        ServerSocket ss = serverSocket;
        return ss != null && ss.isBound() ? ss.getLocalPort() : port;
    }

    public void start() throws IOException {
        loadDatabase();
        reaper = Executors.newSingleThreadScheduledExecutor();
        reaper.scheduleAtFixedRate(this::cleanupStale, 15, 15, TimeUnit.SECONDS);

        try (ServerSocket server = new ServerSocket()) {
            this.serverSocket = server;
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port));
            System.out.println("[JJN-Relay] Listening on TCP port " + server.getLocalPort()
                    + " (data: " + dataFile.toAbsolutePath() + ")");

            while (!server.isClosed()) {
                Socket client;
                try {
                    client = server.accept();
                } catch (IOException ex) {
                    if (server.isClosed()) break;
                    throw ex;
                }
                executor.submit(() -> handleConnection(client));
            }
        } finally {
            this.serverSocket = null;
            if (reaper != null) reaper.shutdownNow();
        }
    }

    public void stop() {
        synchronized (lock) {
            saveDatabase();
        }
        ServerSocket ss = serverSocket;
        if (ss != null) {
            try { ss.close(); } catch (IOException ignored) {}
        }
        if (reaper != null) reaper.shutdownNow();
        executor.shutdownNow();
    }

    private void handleConnection(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
            InputStream in = socket.getInputStream();
            String firstLine = readLineRaw(in, 4096);
            if (firstLine == null) {
                socket.close();
                return;
            }
            firstLine = firstLine.trim();

            if ("PING".equalsIgnoreCase(firstLine)) {
                String pong = "PONG jjn-relay " + onlineByUuid.size() + " " + tunnelsById.size() + "\n";
                OutputStream out = socket.getOutputStream();
                out.write(pong.getBytes(StandardCharsets.UTF_8));
                out.flush();
                socket.close();
            } else if ("CONTROL".equals(firstLine)) {
                handleControlSession(socket, in);
            } else if (firstLine.startsWith("TUNNEL_JOIN ")) {
                handleTunnelJoin(socket, in, firstLine.substring("TUNNEL_JOIN ".length()).trim());
            } else if (firstLine.startsWith("TUNNEL_BIND ")) {
                handleTunnelBind(socket, firstLine.substring("TUNNEL_BIND ".length()).trim());
            } else {
                socket.close();
            }
        } catch (Exception ignored) {
            try {
                socket.close();
            } catch (IOException ignored2) {}
        }
    }

    private void handleControlSession(Socket socket, InputStream in) {
        ControlSession session = null;
        try {
            socket.setSoTimeout(45_000);
            session = new ControlSession(socket);
            while (true) {
                String line = readLineRaw(in, 65536);
                if (line == null) break;
                if (line.isBlank()) continue;

                JsonObject msg;
                try {
                    msg = JsonParser.parseString(line).getAsJsonObject();
                } catch (Exception ex) {
                    continue;
                }

                String op = str(msg, "op");
                switch (op) {
                    case "hello" -> onHello(session, msg);
                    case "heartbeat" -> onHeartbeat(session, msg);
                    case "cosmetics" -> onCosmeticsUpdate(session, msg);
                    case "emote" -> onEmoteUpdate(session, msg);
                    case "add_friend" -> onAddFriend(session, msg);
                    case "accept_friend" -> onAcceptFriend(session, msg);
                    case "decline_friend" -> onDeclineFriend(session, msg);
                    case "remove_friend" -> onRemoveFriend(session, msg);
                    case "host_open" -> onHostOpen(session, msg);
                    case "host_close" -> onHostClose(session);
                    case "invite" -> onInvite(session, msg);
                    default -> {}
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (session != null && session.presence.uuid != null) {
                String uuid = session.presence.uuid;
                List<ControlSession> list = onlineByUuid.get(uuid);
                if (list != null) {
                    list.remove(session);
                    if (list.isEmpty()) {
                        onlineByUuid.remove(uuid);
                    }
                }
                for (ActiveTunnel t : tunnelsById.values()) {
                    if (t.hostSession == session) {
                        tunnelsById.remove(t.tunnelId);
                    }
                }
                pushStateToFriends(uuid);
            }
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private void onHello(ControlSession session, JsonObject msg) throws IOException {
        String uuid = normalizeUuid(str(msg, "uuid"));
        String username = str(msg, "username");
        if (uuid.isEmpty() || username.isEmpty()) return;

        session.presence.uuid = uuid;
        session.presence.username = username;
        session.presence.role = nonEmpty(str(msg, "role"), "launcher");
        session.presence.state = nonEmpty(str(msg, "state"), "Online");
        session.presence.mcVersion = str(msg, "mcVersion");
        session.presence.worldOrServer = str(msg, "worldOrServer");
        session.presence.directEndpoint = str(msg, "directEndpoint");
        applyCosmeticsFromMessage(session.presence, msg);
        session.presence.updatedAt = System.currentTimeMillis();

        synchronized (lock) {
            db.knownNames.put(uuid, username);
            db.friends.computeIfAbsent(uuid, k -> new LinkedHashSet<>());
            saveDatabase();
        }

        onlineByUuid.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>()).add(session);
        sendStateToSession(session);
        pushStateToFriends(uuid);
    }

    private void onHeartbeat(ControlSession session, JsonObject msg) throws IOException {
        if (session.presence.uuid == null) return;
        session.presence.role = nonEmpty(str(msg, "role"), session.presence.role);
        session.presence.state = nonEmpty(str(msg, "state"), session.presence.state);
        session.presence.mcVersion = str(msg, "mcVersion");
        session.presence.worldOrServer = str(msg, "worldOrServer");
        session.presence.directEndpoint = str(msg, "directEndpoint");
        session.presence.tunnelId = str(msg, "tunnelId");
        session.presence.hosting = msg.has("hosting") && msg.get("hosting").getAsBoolean();
        session.presence.friendsOnly = !msg.has("friendsOnly") || msg.get("friendsOnly").getAsBoolean();
        boolean appearanceChanged = applyCosmeticsFromMessage(session.presence, msg);
        session.presence.updatedAt = System.currentTimeMillis();

        if (appearanceChanged) {
            pushStateToFriends(session.presence.uuid);
        } else {
            sendStateToSession(session);
        }
    }

    private void onCosmeticsUpdate(ControlSession session, JsonObject msg) throws IOException {
        if (session.presence.uuid == null) return;
        applyCosmeticsFromMessage(session.presence, msg);
        session.presence.updatedAt = System.currentTimeMillis();
        pushStateToFriends(session.presence.uuid);
    }

    private void onEmoteUpdate(ControlSession session, JsonObject msg) throws IOException {
        if (session.presence.uuid == null) return;
        String emote = str(msg, "activeEmoteId");
        session.presence.activeEmoteId = emote;
        session.presence.activeEmoteAtMs = emote.isEmpty() ? 0L : System.currentTimeMillis();
        session.presence.updatedAt = System.currentTimeMillis();
        pushStateToFriends(session.presence.uuid);
    }

    private static boolean applyCosmeticsFromMessage(PresenceState p, JsonObject msg) {
        boolean changed = false;
        if (msg.has("capeId")) {
            String v = str(msg, "capeId");
            if (!v.equals(p.capeId)) { p.capeId = v; changed = true; }
        }
        if (msg.has("backId")) {
            String v = str(msg, "backId");
            if (!v.equals(p.backId)) { p.backId = v; changed = true; }
        }
        if (msg.has("hatId")) {
            String v = str(msg, "hatId");
            if (!v.equals(p.hatId)) { p.hatId = v; changed = true; }
        }
        if (msg.has("auraId")) {
            String v = str(msg, "auraId");
            if (!v.equals(p.auraId)) { p.auraId = v; changed = true; }
        }
        if (msg.has("activeEmoteId")) {
            String nextEmote = str(msg, "activeEmoteId");
            long ageMs = msg.has("emoteAgeMs") && !msg.get("emoteAgeMs").isJsonNull()
                    ? Math.max(0L, msg.get("emoteAgeMs").getAsLong())
                    : 0L;
            if (!nextEmote.equals(p.activeEmoteId)) {
                p.activeEmoteId = nextEmote;
                p.activeEmoteAtMs = nextEmote.isEmpty() ? 0L : (System.currentTimeMillis() - ageMs);
                changed = true;
            } else if (!nextEmote.isEmpty() && msg.has("emoteAgeMs")) {
                long estimatedStart = System.currentTimeMillis() - ageMs;
                if (Math.abs(estimatedStart - p.activeEmoteAtMs) > 600L) {
                    p.activeEmoteAtMs = estimatedStart;
                    changed = true;
                }
            }
        }
        return changed;
    }

    private void onAddFriend(ControlSession session, JsonObject msg) throws IOException {
        if (session.presence.uuid == null) return;
        String meUuid = session.presence.uuid;
        String meName = session.presence.username;
        String targetName = str(msg, "targetName").trim();
        String targetUuid = normalizeUuid(str(msg, "targetUuid"));

        if (targetUuid.isEmpty() && !targetName.isEmpty()) {
            targetUuid = resolveUuidByName(targetName);
        }
        if (targetUuid.isEmpty()) {
            sendError(session, "Could not find Minecraft player \"" + targetName + "\".");
            return;
        }
        if (targetUuid.equals(meUuid)) {
            sendError(session, "You cannot add yourself as a friend.");
            return;
        }

        String resolvedName = targetName;
        synchronized (lock) {
            if (resolvedName.isEmpty()) {
                resolvedName = db.knownNames.getOrDefault(targetUuid, targetUuid);
            } else {
                db.knownNames.putIfAbsent(targetUuid, resolvedName);
            }

            Set<String> myFriends = db.friends.computeIfAbsent(meUuid, k -> new LinkedHashSet<>());
            if (myFriends.contains(targetUuid)) {
                sendNotice(session, resolvedName + " is already in your friends list.");
                sendStateToSession(session);
                return;
            }

            // If target already sent us a request, accept immediately!
            FriendReq reverse = null;
            for (FriendReq r : db.requests) {
                if (r.fromUuid.equals(targetUuid) && r.toUuid.equals(meUuid)) {
                    reverse = r;
                    break;
                }
            }
            if (reverse != null) {
                db.requests.remove(reverse);
                db.friends.computeIfAbsent(meUuid, k -> new LinkedHashSet<>()).add(targetUuid);
                db.friends.computeIfAbsent(targetUuid, k -> new LinkedHashSet<>()).add(meUuid);
                saveDatabase();
            } else {
                boolean exists = false;
                for (FriendReq r : db.requests) {
                    if (r.fromUuid.equals(meUuid) && r.toUuid.equals(targetUuid)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    FriendReq req = new FriendReq();
                    req.fromUuid = meUuid;
                    req.fromName = meName;
                    req.toUuid = targetUuid;
                    req.toName = resolvedName;
                    req.createdAt = System.currentTimeMillis();
                    db.requests.add(req);
                    saveDatabase();
                }
            }
        }

        sendStateToSession(session);
        pushStateToUuid(targetUuid);
    }

    private void onAcceptFriend(ControlSession session, JsonObject msg) throws IOException {
        if (session.presence.uuid == null) return;
        String meUuid = session.presence.uuid;
        String otherUuid = normalizeUuid(str(msg, "targetUuid"));
        if (otherUuid.isEmpty()) return;

        synchronized (lock) {
            db.requests.removeIf(r ->
                    (r.fromUuid.equals(otherUuid) && r.toUuid.equals(meUuid))
                            || (r.fromUuid.equals(meUuid) && r.toUuid.equals(otherUuid)));
            db.friends.computeIfAbsent(meUuid, k -> new LinkedHashSet<>()).add(otherUuid);
            db.friends.computeIfAbsent(otherUuid, k -> new LinkedHashSet<>()).add(meUuid);
            saveDatabase();
        }
        pushStateToUuid(meUuid);
        pushStateToUuid(otherUuid);
    }

    private void onDeclineFriend(ControlSession session, JsonObject msg) throws IOException {
        if (session.presence.uuid == null) return;
        String meUuid = session.presence.uuid;
        String otherUuid = normalizeUuid(str(msg, "targetUuid"));
        if (otherUuid.isEmpty()) return;

        synchronized (lock) {
            db.requests.removeIf(r ->
                    (r.fromUuid.equals(otherUuid) && r.toUuid.equals(meUuid))
                            || (r.fromUuid.equals(meUuid) && r.toUuid.equals(otherUuid)));
            saveDatabase();
        }
        pushStateToUuid(meUuid);
        pushStateToUuid(otherUuid);
    }

    private void onRemoveFriend(ControlSession session, JsonObject msg) throws IOException {
        if (session.presence.uuid == null) return;
        String meUuid = session.presence.uuid;
        String otherUuid = normalizeUuid(str(msg, "targetUuid"));
        if (otherUuid.isEmpty()) return;

        synchronized (lock) {
            Set<String> s1 = db.friends.get(meUuid);
            if (s1 != null) s1.remove(otherUuid);
            Set<String> s2 = db.friends.get(otherUuid);
            if (s2 != null) s2.remove(meUuid);
            saveDatabase();
        }
        pushStateToUuid(meUuid);
        pushStateToUuid(otherUuid);
    }

    private void onHostOpen(ControlSession session, JsonObject msg) throws IOException {
        if (session.presence.uuid == null) return;
        String tunnelId = "jjn-" + UUID.randomUUID().toString().substring(0, 8);
        String worldName = nonEmpty(str(msg, "worldName"), "Private World");
        String mcVersion = str(msg, "mcVersion");
        boolean friendsOnly = !msg.has("friendsOnly") || msg.get("friendsOnly").getAsBoolean();

        // Close any previous tunnel from this session
        tunnelsById.values().removeIf(t -> t.hostSession == session);

        ActiveTunnel tunnel = new ActiveTunnel(
                tunnelId,
                session.presence.uuid,
                session.presence.username,
                worldName,
                mcVersion,
                friendsOnly
        );
        tunnel.hostSession = session;
        tunnel.allowedUuids.add(session.presence.uuid);

        if (msg.has("allowedUuids") && msg.get("allowedUuids").isJsonArray()) {
            for (JsonElement el : msg.getAsJsonArray("allowedUuids")) {
                String u = normalizeUuid(el.getAsString());
                if (!u.isEmpty()) tunnel.allowedUuids.add(u);
            }
        }
        synchronized (lock) {
            Set<String> fset = db.friends.getOrDefault(session.presence.uuid, Set.of());
            tunnel.allowedUuids.addAll(fset);
        }

        tunnelsById.put(tunnelId, tunnel);
        session.presence.hosting = true;
        session.presence.friendsOnly = friendsOnly;
        session.presence.tunnelId = tunnelId;
        session.presence.mcVersion = mcVersion;
        session.presence.worldOrServer = worldName;
        session.presence.state = "Hosting \"" + worldName + "\"";

        JsonObject resp = new JsonObject();
        resp.addProperty("op", "host_opened");
        resp.addProperty("tunnelId", tunnelId);
        session.sendJson(resp);

        pushStateToFriends(session.presence.uuid);
    }

    private void onHostClose(ControlSession session) {
        if (session.presence.uuid == null) return;
        tunnelsById.values().removeIf(t -> t.hostSession == session);
        session.presence.hosting = false;
        session.presence.tunnelId = "";
        pushStateToFriends(session.presence.uuid);
    }

    private void onInvite(ControlSession session, JsonObject msg) throws IOException {
        if (session.presence.uuid == null) return;
        String targetUuid = normalizeUuid(str(msg, "targetUuid"));
        if (targetUuid.isEmpty()) return;

        String tunnelId = str(msg, "tunnelId");
        if (!tunnelId.isEmpty()) {
            ActiveTunnel t = tunnelsById.get(tunnelId);
            if (t != null) {
                t.allowedUuids.add(targetUuid);
            }
        }

        JsonObject invite = new JsonObject();
        invite.addProperty("op", "invite_received");
        invite.addProperty("fromUuid", session.presence.uuid);
        invite.addProperty("fromName", session.presence.username);
        invite.addProperty("worldName", nonEmpty(str(msg, "worldName"), "Private World"));
        invite.addProperty("mcVersion", str(msg, "mcVersion"));
        invite.addProperty("tunnelId", tunnelId);
        invite.addProperty("directEndpoint", str(msg, "directEndpoint"));
        invite.addProperty("sentAt", System.currentTimeMillis());

        List<ControlSession> targets = onlineByUuid.get(targetUuid);
        if (targets != null) {
            for (ControlSession ts : targets) {
                try {
                    ts.sendJson(invite);
                } catch (IOException ignored) {}
            }
        }
    }

    private void handleTunnelJoin(Socket joinSocket, InputStream joinIn, String argLine) throws Exception {
        String[] parts = argLine.split("\\s+");
        if (parts.length < 1) {
            joinSocket.close();
            return;
        }
        String tunnelId = parts[0];
        String joinerUuid = parts.length >= 2 ? normalizeUuid(parts[1]) : "";
        String joinerName = parts.length >= 3 ? parts[2] : "Friend";

        ActiveTunnel tunnel = tunnelsById.get(tunnelId);
        if (tunnel == null || tunnel.hostSession == null) {
            joinSocket.close();
            return;
        }
        if (tunnel.friendsOnly && !tunnel.allowedUuids.contains(joinerUuid)) {
            boolean isFriend;
            synchronized (lock) {
                isFriend = db.friends.getOrDefault(tunnel.hostUuid, Set.of()).contains(joinerUuid);
            }
            if (!isFriend) {
                joinSocket.close();
                return;
            }
        }

        String connId = UUID.randomUUID().toString().substring(0, 12);
        PendingTunnelJoin pending = new PendingTunnelJoin(joinSocket);
        pendingJoins.put(connId, pending);

        JsonObject need = new JsonObject();
        need.addProperty("op", "tunnel_need");
        need.addProperty("connId", connId);
        need.addProperty("tunnelId", tunnelId);
        need.addProperty("joinerUuid", joinerUuid);
        need.addProperty("joinerName", joinerName);
        tunnel.hostSession.sendJson(need);

        Socket hostBindSocket;
        try {
            hostBindSocket = pending.hostBindFuture.get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            pendingJoins.remove(connId);
            joinSocket.close();
            return;
        }
        pendingJoins.remove(connId);

        bridgeSockets(joinSocket, joinIn, hostBindSocket, hostBindSocket.getInputStream());
    }

    private void handleTunnelBind(Socket hostBindSocket, String connId) throws IOException {
        PendingTunnelJoin pending = pendingJoins.get(connId);
        if (pending == null) {
            hostBindSocket.close();
            return;
        }
        if (!pending.hostBindFuture.complete(hostBindSocket)) {
            hostBindSocket.close();
        }
    }

    private void bridgeSockets(Socket a, InputStream aIn, Socket b, InputStream bIn) {
        try {
            a.setSoTimeout(0);
            b.setSoTimeout(0);
        } catch (IOException ignored) {}

        executor.submit(() -> pump(aIn, b, a));
        pump(bIn, a, b);
    }

    private static void pump(InputStream src, Socket dstSocket, Socket srcSocket) {
        byte[] buf = new byte[16384];
        try {
            OutputStream dst = dstSocket.getOutputStream();
            int n;
            while ((n = src.read(buf)) != -1) {
                dst.write(buf, 0, n);
                dst.flush();
            }
        } catch (IOException ignored) {
        } finally {
            try { srcSocket.close(); } catch (IOException ignored) {}
            try { dstSocket.close(); } catch (IOException ignored) {}
        }
    }

    private void pushStateToFriends(String uuid) {
        Set<String> targets = new LinkedHashSet<>();
        targets.add(uuid);
        synchronized (lock) {
            targets.addAll(db.friends.getOrDefault(uuid, Set.of()));
            for (FriendReq r : db.requests) {
                if (r.fromUuid.equals(uuid)) targets.add(r.toUuid);
                if (r.toUuid.equals(uuid)) targets.add(r.fromUuid);
            }
        }
        for (String target : targets) {
            pushStateToUuid(target);
        }
    }

    private void pushStateToUuid(String uuid) {
        List<ControlSession> sessions = onlineByUuid.get(uuid);
        if (sessions == null || sessions.isEmpty()) return;
        JsonObject stateObj = buildStateFor(uuid);
        for (ControlSession s : sessions) {
            try {
                s.sendJson(stateObj);
            } catch (IOException ignored) {}
        }
    }

    private void sendStateToSession(ControlSession session) throws IOException {
        if (session.presence.uuid == null) return;
        session.sendJson(buildStateFor(session.presence.uuid));
    }

    private JsonObject buildStateFor(String uuid) {
        JsonArray friendsArr = new JsonArray();
        JsonArray incomingArr = new JsonArray();
        JsonArray outgoingArr = new JsonArray();

        synchronized (lock) {
            for (String fUuid : db.friends.getOrDefault(uuid, Set.of())) {
                JsonObject fObj = new JsonObject();
                fObj.addProperty("uuid", fUuid);
                fObj.addProperty("username", db.knownNames.getOrDefault(fUuid, fUuid));

                PresenceState best = bestPresence(fUuid);
                if (best != null) {
                    fObj.addProperty("online", true);
                    fObj.addProperty("role", best.role);
                    fObj.addProperty("state", best.state);
                    fObj.addProperty("mcVersion", best.mcVersion);
                    fObj.addProperty("worldOrServer", best.worldOrServer);
                    fObj.addProperty("hosting", best.hosting);
                    fObj.addProperty("friendsOnly", best.friendsOnly);
                    fObj.addProperty("tunnelId", best.tunnelId);
                    fObj.addProperty("directEndpoint", best.directEndpoint);
                    fObj.addProperty("capeId", nonEmpty(best.capeId, ""));
                    fObj.addProperty("backId", nonEmpty(best.backId, ""));
                    fObj.addProperty("hatId", nonEmpty(best.hatId, ""));
                    fObj.addProperty("auraId", nonEmpty(best.auraId, ""));
                    long nowMs = System.currentTimeMillis();
                    if (best.activeEmoteId != null && !best.activeEmoteId.isEmpty()
                            && nowMs - best.activeEmoteAtMs <= 3600L) {
                        fObj.addProperty("activeEmoteId", best.activeEmoteId);
                        fObj.addProperty("emoteAgeMs", Math.max(0L, nowMs - best.activeEmoteAtMs));
                    } else {
                        if (best.activeEmoteId != null && !best.activeEmoteId.isEmpty()) {
                            best.activeEmoteId = "";
                        }
                        fObj.addProperty("activeEmoteId", "");
                        fObj.addProperty("emoteAgeMs", 0L);
                    }
                    if (best.username != null && !best.username.isBlank()) {
                        fObj.addProperty("username", best.username);
                    }
                } else {
                    fObj.addProperty("online", false);
                    fObj.addProperty("state", "Offline");
                }
                friendsArr.add(fObj);
            }

            for (FriendReq r : db.requests) {
                if (r.toUuid.equals(uuid)) {
                    JsonObject o = new JsonObject();
                    o.addProperty("uuid", r.fromUuid);
                    o.addProperty("username", db.knownNames.getOrDefault(r.fromUuid, r.fromName));
                    o.addProperty("sentAt", r.createdAt);
                    incomingArr.add(o);
                } else if (r.fromUuid.equals(uuid)) {
                    JsonObject o = new JsonObject();
                    o.addProperty("uuid", r.toUuid);
                    o.addProperty("username", db.knownNames.getOrDefault(r.toUuid, r.toName));
                    o.addProperty("sentAt", r.createdAt);
                    outgoingArr.add(o);
                }
            }
        }

        JsonObject root = new JsonObject();
        root.addProperty("op", "state");
        root.add("friends", friendsArr);
        root.add("incoming", incomingArr);
        root.add("outgoing", outgoingArr);
        return root;
    }

    private PresenceState bestPresence(String uuid) {
        List<ControlSession> list = onlineByUuid.get(uuid);
        if (list == null || list.isEmpty()) return null;
        PresenceState chosen = null;
        for (ControlSession s : list) {
            if (chosen == null) {
                chosen = s.presence;
            } else if ("game".equals(s.presence.role) && !"game".equals(chosen.role)) {
                chosen = s.presence;
            } else if (s.presence.hosting && !chosen.hosting) {
                chosen = s.presence;
            }
        }
        return chosen;
    }

    private String resolveUuidByName(String username) {
        synchronized (lock) {
            for (Map.Entry<String, String> e : db.knownNames.entrySet()) {
                if (e.getValue() != null && e.getValue().equalsIgnoreCase(username)) {
                    return e.getKey();
                }
            }
        }
        try {
            String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + encoded))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
                String rawId = str(obj, "id");
                String exactName = str(obj, "name");
                String formatted = formatUuid(rawId);
                if (!formatted.isEmpty()) {
                    synchronized (lock) {
                        db.knownNames.put(formatted, exactName.isEmpty() ? username : exactName);
                    }
                    return formatted;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private void cleanupStale() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, List<ControlSession>> e : onlineByUuid.entrySet()) {
            e.getValue().removeIf(s -> now - s.presence.updatedAt > 60_000 || s.socket.isClosed());
            if (e.getValue().isEmpty()) {
                onlineByUuid.remove(e.getKey());
            }
        }
    }

    private void loadDatabase() {
        if (!Files.isRegularFile(dataFile)) return;
        try {
            String json = Files.readString(dataFile, StandardCharsets.UTF_8);
            RelayDatabase loaded = GSON.fromJson(json, RelayDatabase.class);
            if (loaded != null) {
                if (loaded.knownNames == null) loaded.knownNames = new LinkedHashMap<>();
                if (loaded.friends == null) loaded.friends = new LinkedHashMap<>();
                if (loaded.requests == null) loaded.requests = new ArrayList<>();
                this.db = loaded;
            }
        } catch (Exception ignored) {
        }
    }

    private void saveDatabase() {
        try {
            Path parent = dataFile.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(db), StandardCharsets.UTF_8);
            Files.move(tmp, dataFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    private static void sendError(ControlSession session, String message) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("op", "notice");
        obj.addProperty("error", true);
        obj.addProperty("message", message);
        session.sendJson(obj);
    }

    private static void sendNotice(ControlSession session, String message) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("op", "notice");
        obj.addProperty("error", false);
        obj.addProperty("message", message);
        session.sendJson(obj);
    }

    private static String readLineRaw(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                return baos.toString(StandardCharsets.UTF_8);
            }
            if (b != '\r') {
                baos.write(b);
                if (baos.size() >= maxBytes) {
                    return baos.toString(StandardCharsets.UTF_8);
                }
            }
        }
        return baos.size() == 0 ? null : baos.toString(StandardCharsets.UTF_8);
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString();
    }

    private static String nonEmpty(String val, String fallback) {
        return (val == null || val.isBlank()) ? fallback : val;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private static String normalizeUuid(String raw) {
        return formatUuid(raw);
    }

    private static String formatUuid(String raw) {
        if (raw == null) return "";
        String hex = raw.replace("-", "").trim().toLowerCase(Locale.ROOT);
        if (hex.length() != 32) return raw.trim().toLowerCase(Locale.ROOT);
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
    }
}
