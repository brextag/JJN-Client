package com.jjn.launcher.friends;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * Background networking coordinator for the Launcher's Friends tab:
 *  - LAN / VPN Multicast discovery + direct TCP P2P requests & invites (zero config)
 *  - Optional connection to JJN Friends Relay Server for cross-internet presence,
 *    friend requests, live invites, and local TCP forwarder for joining Relay tunnels.
 */
public final class FriendsService {

    public static final String MULTICAST_GROUP = "239.255.42.99";
    public static final int DISCOVERY_PORT = 25581;

    public static final class PeerStatus {
        public String uuid;
        public String username;
        public boolean online;
        public String role = "launcher";
        public String state = "Offline";
        public String mcVersion = "";
        public String worldOrServer = "";
        public boolean hosting = false;
        public boolean friendsOnly = true;
        public String tunnelId = "";
        public String directEndpoint = "";
        public String capeId = "";
        public String backId = "";
        public String hatId = "";
        public String auraId = "";
        public String activeEmoteId = "";
        public long emoteStartMs = 0L;
        public int lanControlPort = 0;
        public String lanAddress = "";
        public long lastSeenMs = 0L;

        public boolean isJoinable() {
            if (!online) return false;
            if (tunnelId != null && !tunnelId.isBlank()) return true;
            if (directEndpoint != null && !directEndpoint.isBlank()) return true;
            return worldOrServer != null && worldOrServer.startsWith("Server • ");
        }

        public String joinAddressFallback() {
            if (directEndpoint != null && !directEndpoint.isBlank()) return directEndpoint;
            if (worldOrServer != null && worldOrServer.startsWith("Server • ")) {
                return worldOrServer.substring("Server • ".length()).trim();
            }
            return "";
        }
    }

    public static final class WorldInvite {
        public String fromUuid;
        public String fromName;
        public String worldName;
        public String mcVersion;
        public String tunnelId;
        public String directEndpoint;
        public long sentAt;
    }

    private static final Gson GSON = new Gson();
    private final FriendsStore store = new FriendsStore();

    private volatile boolean started = false;
    private volatile boolean running = true;

    private volatile String selfUuid = "";
    private volatile String selfUsername = "";
    private volatile String selfState = "In Launcher";
    private volatile String selfMcVersion = "";
    private volatile String selfWorldOrServer = "";

    private volatile FriendsStore.StoreData data = new FriendsStore.StoreData();
    private volatile boolean relayConnected = false;
    private volatile String relayStatusText = "LAN / VPN mode (no relay set)";
    private volatile String lastNotice = "";

    private final Map<String, PeerStatus> lanPeers = new ConcurrentHashMap<>();
    private final Map<String, PeerStatus> relayPeers = new ConcurrentHashMap<>();
    private final List<WorldInvite> pendingInvites = new CopyOnWriteArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private ServerSocket lanControlServer;
    private volatile int lanControlPort = 0;

    private volatile Socket relaySocket;
    private volatile OutputStream relayOut;

    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "JJN-Friends-Worker");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "JJN-Friends-Timer");
        t.setDaemon(true);
        return t;
    });

    private volatile ServerSocket activeJoinForwarder;
    private volatile long lastLoadedMtime = -1L;
    private volatile String activeRelayUrl = "";

    public synchronized void start() {
        if (started) return;
        started = true;
        data = store.load();
        lastLoadedMtime = readStoreMtime();
        activeRelayUrl = FriendsStore.resolveEffectiveRelayUrl(data.relayUrl);
        if (!activeRelayUrl.isEmpty() && (data.relayUrl == null || data.relayUrl.isBlank())) {
            relayStatusText = "Using default Relay (" + activeRelayUrl + ")...";
        }
        startLanControlServer();
        startLanDiscoveryListener();
        startRelayLoop();
        scheduler.scheduleAtFixedRate(this::periodicTick, 1, 2, TimeUnit.SECONDS);
    }

    public void shutdown() {
        running = false;
        closeActiveJoinForwarder();
        closeRelaySocket();
        try {
            if (lanControlServer != null) lanControlServer.close();
        } catch (IOException ignored) {}
        scheduler.shutdownNow();
        pool.shutdownNow();
    }

    public void addChangeListener(Runnable listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Runnable r : listeners) {
            try {
                r.run();
            } catch (Exception ignored) {}
        }
    }

    public void setSelfIdentity(String uuid, String username) {
        String norm = FriendsStore.normalizeUuid(uuid);
        String name = username == null ? "" : username.trim();
        boolean changed = !norm.equals(this.selfUuid) || !name.equals(this.selfUsername);
        this.selfUuid = norm;
        this.selfUsername = name;
        if (changed && !norm.isEmpty() && !name.isEmpty()) {
            sendRelayHello();
            sendLanBeacon();
        }
    }

    public void setSelfActivity(String state, String mcVersion, String worldOrServer) {
        this.selfState = state == null || state.isBlank() ? "In Launcher" : state;
        this.selfMcVersion = mcVersion == null ? "" : mcVersion;
        this.selfWorldOrServer = worldOrServer == null ? "" : worldOrServer;
        sendRelayHeartbeat();
        sendLanBeacon();
    }

    public FriendsStore.StoreData getDataSnapshot() {
        return data;
    }

    public String getEffectiveRelayUrl() {
        return FriendsStore.resolveEffectiveRelayUrl(data.relayUrl);
    }

    public void testRelayConnection(String rawUrl, java.util.function.Consumer<FriendsStore.RelayTestResult> callback) {
        String target = FriendsStore.resolveEffectiveRelayUrl(rawUrl);
        pool.submit(() -> {
            FriendsStore.RelayTestResult res = FriendsStore.pingRelay(target, 4000);
            if (callback != null) {
                callback.accept(res);
            }
        });
    }

    public boolean isRelayConnected() {
        return relayConnected;
    }

    public String getRelayStatusText() {
        return relayStatusText;
    }

    public String getLastNotice() {
        return lastNotice;
    }

    public String getSelfUuid() {
        return selfUuid;
    }

    public List<WorldInvite> getPendingInvites() {
        long cutoff = System.currentTimeMillis() - 180_000;
        pendingInvites.removeIf(inv -> inv.sentAt < cutoff);
        return new ArrayList<>(pendingInvites);
    }

    public void dismissInvite(WorldInvite invite) {
        pendingInvites.remove(invite);
        notifyListeners();
    }

    public void reloadFromDisk() {
        this.data = store.load();
        this.lastLoadedMtime = readStoreMtime();
        String newUrl = FriendsStore.resolveEffectiveRelayUrl(this.data.relayUrl);
        if (!newUrl.equals(this.activeRelayUrl)) {
            this.activeRelayUrl = newUrl;
            closeRelaySocket();
            relayStatusText = newUrl.isEmpty()
                    ? "LAN / VPN mode (no relay set)"
                    : "Connecting to " + newUrl + "...";
        }
        notifyListeners();
    }

    public void setRelayUrl(String rawUrl) {
        data.relayUrl = rawUrl == null ? "" : rawUrl.trim();
        activeRelayUrl = FriendsStore.resolveEffectiveRelayUrl(data.relayUrl);
        saveStore();
        closeRelaySocket();
        if (activeRelayUrl.isBlank()) {
            relayStatusText = "LAN / VPN mode (no relay set)";
        } else {
            relayStatusText = "Connecting to " + activeRelayUrl + "...";
        }
        notifyListeners();
    }

    public List<PeerStatus> getFriendsWithStatus() {
        long now = System.currentTimeMillis();
        Map<String, PeerStatus> result = new LinkedHashMap<>();

        for (FriendsStore.FriendEntry f : data.friends) {
            PeerStatus ps = new PeerStatus();
            ps.uuid = FriendsStore.normalizeUuid(f.uuid);
            ps.username = f.username;
            ps.online = false;
            ps.state = "Offline";
            result.put(ps.uuid, ps);
        }

        // Overlay Relay peers
        for (Map.Entry<String, PeerStatus> e : relayPeers.entrySet()) {
            PeerStatus rp = e.getValue();
            if (rp.uuid == null || rp.uuid.equals(selfUuid)) continue;
            PeerStatus target = result.computeIfAbsent(rp.uuid, k -> {
                PeerStatus created = new PeerStatus();
                created.uuid = rp.uuid;
                created.username = rp.username;
                return created;
            });
            mergePeerStatus(target, rp);
        }

        // Overlay fresh LAN peers (prefer game role over launcher role)
        for (PeerStatus lp : lanPeers.values()) {
            if (lp.uuid == null || lp.uuid.equals(selfUuid)) continue;
            if (now - lp.lastSeenMs > 10_000) continue;
            PeerStatus target = result.get(lp.uuid);
            if (target != null) {
                mergePeerStatus(target, lp);
            }
        }

        List<PeerStatus> list = new ArrayList<>(result.values());
        list.sort((a, b) -> {
            if (a.online != b.online) return a.online ? -1 : 1;
            if (a.isJoinable() != b.isJoinable()) return a.isJoinable() ? -1 : 1;
            String na = a.username == null ? "" : a.username;
            String nb = b.username == null ? "" : b.username;
            return na.compareToIgnoreCase(nb);
        });
        return list;
    }

    public List<PeerStatus> getNearbyLanPlayers() {
        long now = System.currentTimeMillis();
        Set<String> friendSet = new HashSet<>();
        for (FriendsStore.FriendEntry f : data.friends) {
            friendSet.add(FriendsStore.normalizeUuid(f.uuid));
        }
        Map<String, PeerStatus> out = new LinkedHashMap<>();
        for (PeerStatus lp : lanPeers.values()) {
            if (lp.uuid == null || lp.uuid.equals(selfUuid)) continue;
            if (now - lp.lastSeenMs > 10_000) continue;
            if (friendSet.contains(lp.uuid)) continue;
            out.merge(lp.uuid, lp, (a, b) -> ("game".equals(b.role) ? b : a));
        }
        return new ArrayList<>(out.values());
    }

    private static void mergePeerStatus(PeerStatus dst, PeerStatus src) {
        if (src.username != null && !src.username.isBlank()) dst.username = src.username;
        if (!src.online) return;
        boolean preferSrc = !dst.online
                || ("game".equals(src.role) && !"game".equals(dst.role))
                || (src.hosting && !dst.hosting)
                || ("game".equals(src.role) && "game".equals(dst.role));
        if (preferSrc) {
            dst.online = true;
            dst.role = src.role;
            dst.state = src.state;
            dst.mcVersion = src.mcVersion;
            dst.worldOrServer = src.worldOrServer;
            dst.hosting = src.hosting;
            dst.friendsOnly = src.friendsOnly;
            if (src.tunnelId != null && !src.tunnelId.isBlank()) dst.tunnelId = src.tunnelId;
            if (src.directEndpoint != null && !src.directEndpoint.isBlank()) dst.directEndpoint = src.directEndpoint;
            if ("game".equals(src.role) || !"game".equals(dst.role)) {
                dst.capeId = src.capeId;
                dst.backId = src.backId;
                dst.hatId = src.hatId;
                dst.auraId = src.auraId;
                dst.activeEmoteId = src.activeEmoteId;
                dst.emoteStartMs = src.emoteStartMs;
            }
            if (src.lanControlPort > 0) {
                dst.lanControlPort = src.lanControlPort;
                dst.lanAddress = src.lanAddress;
            }
            dst.lastSeenMs = Math.max(dst.lastSeenMs, src.lastSeenMs);
        }
    }

    public void addFriendByName(String username) {
        String clean = username == null ? "" : username.trim();
        if (clean.isEmpty()) return;

        pool.submit(() -> {
            FriendsStore.MojangProfile prof = FriendsStore.lookupPlayer(clean);
            if (prof == null) {
                lastNotice = "Enter a valid Minecraft username.";
                notifyListeners();
                return;
            }
            if (prof.uuid().equals(selfUuid)) {
                lastNotice = "You cannot add yourself as a friend.";
                notifyListeners();
                return;
            }

            // If already friends
            for (FriendsStore.FriendEntry f : data.friends) {
                if (prof.uuid().equals(FriendsStore.normalizeUuid(f.uuid))) {
                    lastNotice = prof.username() + " is already in your friends list.";
                    notifyListeners();
                    return;
                }
            }

            // If relay is connected, let relay coordinate request + persistence
            if (relayConnected) {
                JsonObject cmd = new JsonObject();
                cmd.addProperty("op", "add_friend");
                cmd.addProperty("targetName", prof.username());
                cmd.addProperty("targetUuid", prof.uuid());
                sendRelayJson(cmd);
                lastNotice = "Friend request sent to " + prof.username() + " via Relay.";
            } else {
                // LAN / Direct mode: add directly locally + send P2P friend request if visible on LAN
                boolean sentLanReq = sendLanFriendPacket("FRIEND_REQ", prof.uuid());
                boolean alreadyFriend = false;
                for (FriendsStore.FriendEntry f : data.friends) {
                    if (prof.uuid().equals(FriendsStore.normalizeUuid(f.uuid))) {
                        alreadyFriend = true;
                        break;
                    }
                }
                if (!alreadyFriend) {
                    data.friends.add(new FriendsStore.FriendEntry(
                            prof.uuid(), prof.username(), prof.skinUrl(), System.currentTimeMillis()));
                    saveStore();
                }
                lastNotice = sentLanReq
                        ? "Added " + prof.username() + " and notified them on LAN!"
                        : "Added " + prof.username() + " (" + (prof.onlineVerified() ? "verified MC account" : "offline UUID") + ").";
            }
            notifyListeners();
        });
    }

    public void acceptRequest(String targetUuid) {
        String norm = FriendsStore.normalizeUuid(targetUuid);
        String name = norm;
        for (FriendsStore.FriendRequestEntry r : data.incomingRequests) {
            if (norm.equals(FriendsStore.normalizeUuid(r.uuid))) {
                name = r.username;
                break;
            }
        }
        final String finalName = name;
        data.incomingRequests.removeIf(r -> norm.equals(FriendsStore.normalizeUuid(r.uuid)));
        if (data.friends.stream().noneMatch(f -> norm.equals(FriendsStore.normalizeUuid(f.uuid)))) {
            data.friends.add(new FriendsStore.FriendEntry(norm, finalName, "", System.currentTimeMillis()));
        }
        saveStore();

        if (relayConnected) {
            JsonObject cmd = new JsonObject();
            cmd.addProperty("op", "accept_friend");
            cmd.addProperty("targetUuid", norm);
            sendRelayJson(cmd);
        }
        sendLanFriendPacket("FRIEND_ACCEPT", norm);
        lastNotice = "You and " + finalName + " are now friends!";
        notifyListeners();
    }

    public void declineRequest(String targetUuid) {
        String norm = FriendsStore.normalizeUuid(targetUuid);
        data.incomingRequests.removeIf(r -> norm.equals(FriendsStore.normalizeUuid(r.uuid)));
        data.outgoingRequests.removeIf(r -> norm.equals(FriendsStore.normalizeUuid(r.uuid)));
        saveStore();
        if (relayConnected) {
            JsonObject cmd = new JsonObject();
            cmd.addProperty("op", "decline_friend");
            cmd.addProperty("targetUuid", norm);
            sendRelayJson(cmd);
        }
        notifyListeners();
    }

    public void removeFriend(String targetUuid) {
        String norm = FriendsStore.normalizeUuid(targetUuid);
        data.friends.removeIf(f -> norm.equals(FriendsStore.normalizeUuid(f.uuid)));
        saveStore();
        if (relayConnected) {
            JsonObject cmd = new JsonObject();
            cmd.addProperty("op", "remove_friend");
            cmd.addProperty("targetUuid", norm);
            sendRelayJson(cmd);
        }
        notifyListeners();
    }

    public boolean isFreshLanPeer(String uuid) {
        if (uuid == null || uuid.isBlank()) return false;
        String norm = FriendsStore.normalizeUuid(uuid);
        long now = System.currentTimeMillis();
        for (PeerStatus lp : lanPeers.values()) {
            if (norm.equals(lp.uuid) && now - lp.lastSeenMs <= 10_000) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the best endpoint to connect to right now (direct LAN/server or local Relay tunnel forwarder)
     * for launching Minecraft directly into the friend's world via QuickPlay.
     */
    public String prepareJoinForLaunch(PeerStatus peer) throws IOException {
        if (peer == null) return "";
        String direct = peer.joinAddressFallback();
        boolean sameLan = isFreshLanPeer(peer.uuid);
        String tunnelId = peer.tunnelId == null ? "" : peer.tunnelId.trim();
        String relayUrl = getEffectiveRelayUrl();

        // If hosting via Relay and peer is NOT on the same LAN, prefer the reliable Relay TCP tunnel
        // (unless the friend is on a public Server or only directEndpoint exists).
        if (peer.hosting && !sameLan && !tunnelId.isEmpty() && !relayUrl.isEmpty()) {
            int localPort = startLocalTunnelForwarder(relayUrl, tunnelId);
            return "127.0.0.1:" + localPort;
        }

        if (sameLan && peer.lanAddress != null && !peer.lanAddress.isBlank() && !direct.isEmpty()) {
            String[] hp = FriendsStore.parseHostPort(direct);
            if (hp != null) {
                return peer.lanAddress + ":" + hp[1];
            }
        }

        if (!direct.isEmpty()) {
            return direct;
        }
        if (!tunnelId.isEmpty() && !relayUrl.isEmpty()) {
            int localPort = startLocalTunnelForwarder(relayUrl, tunnelId);
            return "127.0.0.1:" + localPort;
        }
        return "";
    }

    public String prepareInviteJoinForLaunch(WorldInvite invite) throws IOException {
        return prepareJoinForLaunch(toPeerStatus(invite));
    }

    /**
     * Hands off a join request to an already-running Fabric client via ~/.jjnclient/friends/pending-join.json.
     */
    public void sendJoinToRunningGame(PeerStatus peer) {
        if (peer == null) return;
        FriendsStore.PendingJoin pj = new FriendsStore.PendingJoin();
        pj.hostName = peer.username == null ? "Friend" : peer.username;
        pj.worldName = peer.worldOrServer == null ? "" : peer.worldOrServer;
        pj.mcVersion = peer.mcVersion == null ? "" : peer.mcVersion;
        pj.directEndpoint = peer.joinAddressFallback();
        if (isFreshLanPeer(peer.uuid) && peer.lanAddress != null && !peer.lanAddress.isBlank() && !pj.directEndpoint.isEmpty()) {
            String[] hp = FriendsStore.parseHostPort(pj.directEndpoint);
            if (hp != null) {
                pj.directEndpoint = peer.lanAddress + ":" + hp[1];
            }
        }
        pj.tunnelId = peer.tunnelId == null ? "" : peer.tunnelId;
        pj.relayUrl = getEffectiveRelayUrl();
        pj.createdAt = System.currentTimeMillis();
        store.writePendingJoin(pj);
        lastNotice = "Sent join request to your open Minecraft window!";
        notifyListeners();
    }

    public void sendInviteJoinToRunningGame(WorldInvite invite) {
        sendJoinToRunningGame(toPeerStatus(invite));
    }

    private PeerStatus toPeerStatus(WorldInvite invite) {
        PeerStatus p = new PeerStatus();
        p.uuid = invite.fromUuid;
        p.username = invite.fromName;
        p.worldOrServer = invite.worldName;
        p.mcVersion = invite.mcVersion;
        p.directEndpoint = invite.directEndpoint;
        p.tunnelId = invite.tunnelId;
        p.hosting = true;
        p.online = true;
        return p;
    }

    public int startLocalTunnelForwarder(String relayUrl, String tunnelId) throws IOException {
        closeActiveJoinForwarder();
        String[] hp = FriendsStore.parseHostPort(relayUrl);
        if (hp == null) {
            throw new IOException("No Relay server configured for tunnel join.");
        }
        String relayHost = hp[0];
        int relayPort = Integer.parseInt(hp[1]);

        ServerSocket server = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
        activeJoinForwarder = server;
        int localPort = server.getLocalPort();

        pool.submit(() -> {
            while (!server.isClosed()) {
                try {
                    Socket mcClient = server.accept();
                    pool.submit(() -> bridgeJoinSocket(mcClient, relayHost, relayPort, tunnelId));
                } catch (IOException ignored) {
                    break;
                }
            }
        });
        return localPort;
    }

    public void closeActiveJoinForwarder() {
        ServerSocket ss = activeJoinForwarder;
        activeJoinForwarder = null;
        if (ss != null) {
            try { ss.close(); } catch (IOException ignored) {}
        }
    }

    private void bridgeJoinSocket(Socket mcClient, String relayHost, int relayPort, String tunnelId) {
        try {
            mcClient.setTcpNoDelay(true);
            Socket relay = new Socket();
            relay.connect(new InetSocketAddress(relayHost, relayPort), 8000);
            relay.setTcpNoDelay(true);
            String joinHeader = "TUNNEL_JOIN " + tunnelId + " "
                    + (selfUuid.isEmpty() ? "launcher" : selfUuid) + " "
                    + (selfUsername.isEmpty() ? "Player" : selfUsername.replace(' ', '_')) + "\n";
            OutputStream rOut = relay.getOutputStream();
            rOut.write(joinHeader.getBytes(StandardCharsets.UTF_8));
            rOut.flush();

            pool.submit(() -> pump(mcClient, relay));
            pump(relay, mcClient);
        } catch (IOException ignored) {
            try { mcClient.close(); } catch (IOException ignored2) {}
        }
    }

    private static void pump(Socket src, Socket dst) {
        byte[] buf = new byte[16384];
        try {
            InputStream in = src.getInputStream();
            OutputStream out = dst.getOutputStream();
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (IOException ignored) {
        } finally {
            try { src.close(); } catch (IOException ignored) {}
            try { dst.close(); } catch (IOException ignored) {}
        }
    }

    // --- LAN MULTICAST + P2P ---

    private void startLanControlServer() {
        try {
            lanControlServer = new ServerSocket(0);
            lanControlPort = lanControlServer.getLocalPort();
            pool.submit(() -> {
                while (running && !lanControlServer.isClosed()) {
                    try {
                        Socket s = lanControlServer.accept();
                        pool.submit(() -> handleLanPeerMessage(s));
                    } catch (IOException ignored) {
                        break;
                    }
                }
            });
        } catch (IOException ignored) {
        }
    }

    private void handleLanPeerMessage(Socket s) {
        try (s) {
            String line = readLineRaw(s.getInputStream(), 16384);
            if (line == null || line.isBlank()) return;
            JsonObject msg = JsonParser.parseString(line).getAsJsonObject();
            String type = msg.has("type") ? msg.get("type").getAsString() : "";
            String fromUuid = FriendsStore.normalizeUuid(msg.has("fromUuid") ? msg.get("fromUuid").getAsString() : "");
            String fromName = msg.has("fromName") ? msg.get("fromName").getAsString() : "Player";
            if (fromUuid.isEmpty() || fromUuid.equals(selfUuid)) return;

            if ("FRIEND_REQ".equals(type)) {
                boolean alreadyFriend = data.friends.stream()
                        .anyMatch(f -> fromUuid.equals(FriendsStore.normalizeUuid(f.uuid)));
                boolean alreadyReq = data.incomingRequests.stream()
                        .anyMatch(r -> fromUuid.equals(FriendsStore.normalizeUuid(r.uuid)));
                if (!alreadyFriend && !alreadyReq) {
                    data.incomingRequests.add(new FriendsStore.FriendRequestEntry(
                            fromUuid, fromName, System.currentTimeMillis()));
                    saveStore();
                    lastNotice = fromName + " sent you a friend request!";
                    notifyListeners();
                }
            } else if ("FRIEND_ACCEPT".equals(type)) {
                data.outgoingRequests.removeIf(r -> fromUuid.equals(FriendsStore.normalizeUuid(r.uuid)));
                if (data.friends.stream().noneMatch(f -> fromUuid.equals(FriendsStore.normalizeUuid(f.uuid)))) {
                    data.friends.add(new FriendsStore.FriendEntry(
                            fromUuid, fromName, "", System.currentTimeMillis()));
                    saveStore();
                }
                lastNotice = fromName + " accepted your friend request!";
                notifyListeners();
            } else if ("INVITE".equals(type)) {
                WorldInvite inv = new WorldInvite();
                inv.fromUuid = fromUuid;
                inv.fromName = fromName;
                inv.worldName = msg.has("worldName") ? msg.get("worldName").getAsString() : "Private World";
                inv.mcVersion = msg.has("mcVersion") ? msg.get("mcVersion").getAsString() : "";
                inv.tunnelId = msg.has("tunnelId") ? msg.get("tunnelId").getAsString() : "";
                inv.directEndpoint = msg.has("directEndpoint") ? msg.get("directEndpoint").getAsString() : "";
                inv.sentAt = System.currentTimeMillis();
                pendingInvites.removeIf(old -> fromUuid.equals(old.fromUuid));
                pendingInvites.add(0, inv);
                lastNotice = fromName + " invited you to join \"" + inv.worldName + "\"!";
                notifyListeners();
            }
        } catch (Exception ignored) {
        }
    }

    private boolean sendLanFriendPacket(String type, String targetUuid) {
        if (selfUuid.isEmpty() || selfUsername.isEmpty()) return false;
        PeerStatus target = null;
        for (PeerStatus lp : lanPeers.values()) {
            if (targetUuid.equals(lp.uuid) && lp.lanControlPort > 0 && lp.lanAddress != null) {
                target = lp;
                if ("game".equals(lp.role)) break;
            }
        }
        if (target == null) return false;

        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(target.lanAddress, target.lanControlPort), 2000);
            JsonObject obj = new JsonObject();
            obj.addProperty("type", type);
            obj.addProperty("fromUuid", selfUuid);
            obj.addProperty("fromName", selfUsername);
            s.getOutputStream().write((GSON.toJson(obj) + "\n").getBytes(StandardCharsets.UTF_8));
            s.getOutputStream().flush();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void startLanDiscoveryListener() {
        pool.submit(() -> {
            try (MulticastSocket socket = new MulticastSocket(DISCOVERY_PORT)) {
                InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
                socket.setReuseAddress(true);
                socket.joinGroup(new InetSocketAddress(group, DISCOVERY_PORT), null);
                byte[] buf = new byte[8192];
                while (running && !socket.isClosed()) {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    socket.receive(pkt);
                    String raw = new String(pkt.getData(), pkt.getOffset(), pkt.getLength(), StandardCharsets.UTF_8);
                    try {
                        JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
                        if (!"PRESENCE".equals(str(obj, "type"))) continue;
                        String uuid = FriendsStore.normalizeUuid(str(obj, "uuid"));
                        if (uuid.isEmpty() || uuid.equals(selfUuid)) continue;
                        String role = str(obj, "role");
                        String key = uuid + ":" + role;

                        PeerStatus ps = new PeerStatus();
                        ps.uuid = uuid;
                        ps.username = str(obj, "username");
                        ps.online = true;
                        ps.role = role;
                        ps.state = str(obj, "state");
                        ps.mcVersion = str(obj, "mcVersion");
                        ps.worldOrServer = str(obj, "worldOrServer");
                        ps.hosting = obj.has("hosting") && obj.get("hosting").getAsBoolean();
                        ps.friendsOnly = !obj.has("friendsOnly") || obj.get("friendsOnly").getAsBoolean();
                        ps.tunnelId = str(obj, "tunnelId");
                        ps.directEndpoint = str(obj, "directEndpoint");
                        ps.capeId = str(obj, "capeId");
                        ps.backId = str(obj, "backId");
                        ps.hatId = str(obj, "hatId");
                        ps.auraId = str(obj, "auraId");
                        ps.activeEmoteId = str(obj, "activeEmoteId");
                        if (!ps.activeEmoteId.isEmpty() && obj.has("emoteAgeMs")) {
                            long age = Math.max(0L, obj.get("emoteAgeMs").getAsLong());
                            ps.emoteStartMs = System.currentTimeMillis() - age;
                        }
                        ps.lanControlPort = obj.has("controlPort") ? obj.get("controlPort").getAsInt() : 0;
                        ps.lanAddress = pkt.getAddress().getHostAddress();
                        ps.lastSeenMs = System.currentTimeMillis();

                        // If peer advertises a local-only port on localhost, fix IP to sender LAN address
                        if (ps.directEndpoint.startsWith("0.0.0.0:")) {
                            ps.directEndpoint = ps.lanAddress + ps.directEndpoint.substring("0.0.0.0".length());
                        }
                        lanPeers.put(key, ps);
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void sendLanBeacon() {
        if (selfUuid.isEmpty() || selfUsername.isEmpty()) return;
        try (DatagramSocket ds = new DatagramSocket()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", "PRESENCE");
            obj.addProperty("uuid", selfUuid);
            obj.addProperty("username", selfUsername);
            obj.addProperty("role", "launcher");
            obj.addProperty("state", selfState);
            obj.addProperty("mcVersion", selfMcVersion);
            obj.addProperty("worldOrServer", selfWorldOrServer);
            obj.addProperty("hosting", false);
            obj.addProperty("controlPort", lanControlPort);
            byte[] bytes = GSON.toJson(obj).getBytes(StandardCharsets.UTF_8);
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            ds.send(new DatagramPacket(bytes, bytes.length, group, DISCOVERY_PORT));
        } catch (Exception ignored) {
        }
    }

    // --- RELAY CLIENT ---

    private void startRelayLoop() {
        pool.submit(() -> {
            while (running) {
                String url = getEffectiveRelayUrl();
                if (url.isEmpty() || selfUuid.isEmpty() || selfUsername.isEmpty()) {
                    relayConnected = false;
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) { break; }
                    continue;
                }
                String[] hp = FriendsStore.parseHostPort(url);
                if (hp == null) {
                    relayConnected = false;
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) { break; }
                    continue;
                }
                try (Socket sock = new Socket()) {
                    sock.connect(new InetSocketAddress(hp[0], Integer.parseInt(hp[1])), 5000);
                    sock.setTcpNoDelay(true);
                    sock.setSoTimeout(30_000);
                    relaySocket = sock;
                    relayOut = sock.getOutputStream();
                    relayOut.write("CONTROL\n".getBytes(StandardCharsets.UTF_8));
                    relayOut.flush();
                    relayConnected = true;
                    relayStatusText = "Connected to Relay (" + hp[0] + ":" + hp[1] + ")";
                    sendRelayHello();
                    notifyListeners();

                    InputStream in = sock.getInputStream();
                    while (running && !sock.isClosed()) {
                        String line = readLineRaw(in, 262144);
                        if (line == null) break;
                        if (!line.isBlank()) handleRelayMessage(line);
                    }
                } catch (Exception ex) {
                    relayStatusText = "Relay offline (" + hp[0] + ":" + hp[1] + ") — LAN mode active";
                } finally {
                    relayConnected = false;
                    relaySocket = null;
                    relayOut = null;
                    notifyListeners();
                }
                try { Thread.sleep(4000); } catch (InterruptedException ignored) { break; }
            }
        });
    }

    private void handleRelayMessage(String line) {
        try {
            JsonObject msg = JsonParser.parseString(line).getAsJsonObject();
            String op = str(msg, "op");
            if ("state".equals(op)) {
                Map<String, PeerStatus> nextRelayPeers = new ConcurrentHashMap<>();
                List<FriendsStore.FriendEntry> mergedFriends = new ArrayList<>();
                if (msg.has("friends") && msg.get("friends").isJsonArray()) {
                    for (JsonElement el : msg.getAsJsonArray("friends")) {
                        JsonObject f = el.getAsJsonObject();
                        PeerStatus ps = new PeerStatus();
                        ps.uuid = FriendsStore.normalizeUuid(str(f, "uuid"));
                        ps.username = str(f, "username");
                        ps.online = f.has("online") && f.get("online").getAsBoolean();
                        ps.role = str(f, "role");
                        ps.state = str(f, "state");
                        ps.mcVersion = str(f, "mcVersion");
                        ps.worldOrServer = str(f, "worldOrServer");
                        ps.hosting = f.has("hosting") && f.get("hosting").getAsBoolean();
                        ps.friendsOnly = !f.has("friendsOnly") || f.get("friendsOnly").getAsBoolean();
                        ps.tunnelId = str(f, "tunnelId");
                        ps.directEndpoint = str(f, "directEndpoint");
                        ps.capeId = str(f, "capeId");
                        ps.backId = str(f, "backId");
                        ps.hatId = str(f, "hatId");
                        ps.auraId = str(f, "auraId");
                        ps.activeEmoteId = str(f, "activeEmoteId");
                        if (!ps.activeEmoteId.isEmpty() && f.has("emoteAgeMs")) {
                            long age = Math.max(0L, f.get("emoteAgeMs").getAsLong());
                            ps.emoteStartMs = System.currentTimeMillis() - age;
                        }
                        ps.lastSeenMs = System.currentTimeMillis();
                        nextRelayPeers.put(ps.uuid, ps);
                        mergedFriends.add(new FriendsStore.FriendEntry(ps.uuid, ps.username, "", System.currentTimeMillis()));
                    }
                }
                relayPeers.clear();
                relayPeers.putAll(nextRelayPeers);

                // Merge into local store so it stays cached offline too
                for (FriendsStore.FriendEntry rf : mergedFriends) {
                    if (data.friends.stream().noneMatch(local -> rf.uuid.equals(FriendsStore.normalizeUuid(local.uuid)))) {
                        data.friends.add(rf);
                    }
                }
                if (msg.has("incoming") && msg.get("incoming").isJsonArray()) {
                    data.incomingRequests.clear();
                    for (JsonElement el : msg.getAsJsonArray("incoming")) {
                        JsonObject r = el.getAsJsonObject();
                        data.incomingRequests.add(new FriendsStore.FriendRequestEntry(
                                str(r, "uuid"), str(r, "username"),
                                r.has("sentAt") ? r.get("sentAt").getAsLong() : System.currentTimeMillis()));
                    }
                }
                if (msg.has("outgoing") && msg.get("outgoing").isJsonArray()) {
                    data.outgoingRequests.clear();
                    for (JsonElement el : msg.getAsJsonArray("outgoing")) {
                        JsonObject r = el.getAsJsonObject();
                        data.outgoingRequests.add(new FriendsStore.FriendRequestEntry(
                                str(r, "uuid"), str(r, "username"),
                                r.has("sentAt") ? r.get("sentAt").getAsLong() : System.currentTimeMillis()));
                    }
                }
                saveStore();
                notifyListeners();
            } else if ("invite_received".equals(op)) {
                WorldInvite inv = new WorldInvite();
                inv.fromUuid = FriendsStore.normalizeUuid(str(msg, "fromUuid"));
                inv.fromName = str(msg, "fromName");
                inv.worldName = str(msg, "worldName");
                inv.mcVersion = str(msg, "mcVersion");
                inv.tunnelId = str(msg, "tunnelId");
                inv.directEndpoint = str(msg, "directEndpoint");
                inv.sentAt = System.currentTimeMillis();
                pendingInvites.removeIf(old -> inv.fromUuid.equals(old.fromUuid));
                pendingInvites.add(0, inv);
                lastNotice = inv.fromName + " invited you to join \"" + inv.worldName + "\"!";
                notifyListeners();
            } else if ("notice".equals(op)) {
                lastNotice = str(msg, "message");
                notifyListeners();
            }
        } catch (Exception ignored) {
        }
    }

    private void sendRelayHello() {
        if (!relayConnected || selfUuid.isEmpty() || selfUsername.isEmpty()) return;
        JsonObject hello = new JsonObject();
        hello.addProperty("op", "hello");
        hello.addProperty("uuid", selfUuid);
        hello.addProperty("username", selfUsername);
        hello.addProperty("role", "launcher");
        hello.addProperty("state", selfState);
        hello.addProperty("mcVersion", selfMcVersion);
        hello.addProperty("worldOrServer", selfWorldOrServer);
        sendRelayJson(hello);
    }

    private void sendRelayHeartbeat() {
        if (!relayConnected || selfUuid.isEmpty()) return;
        JsonObject hb = new JsonObject();
        hb.addProperty("op", "heartbeat");
        hb.addProperty("role", "launcher");
        hb.addProperty("state", selfState);
        hb.addProperty("mcVersion", selfMcVersion);
        hb.addProperty("worldOrServer", selfWorldOrServer);
        hb.addProperty("hosting", false);
        sendRelayJson(hb);
    }

    private synchronized void sendRelayJson(JsonObject obj) {
        OutputStream out = relayOut;
        if (out == null) return;
        try {
            out.write((GSON.toJson(obj) + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
            closeRelaySocket();
        }
    }

    private void closeRelaySocket() {
        relayConnected = false;
        Socket s = relaySocket;
        relaySocket = null;
        relayOut = null;
        if (s != null) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    private synchronized void saveStore() {
        store.save(data);
        lastLoadedMtime = readStoreMtime();
    }

    private long readStoreMtime() {
        try {
            Path file = FriendsStore.storeFile();
            if (Files.isRegularFile(file)) {
                return Files.getLastModifiedTime(file).toMillis();
            }
        } catch (IOException ignored) {}
        return -1L;
    }

    private void checkDiskReload() {
        long mtime = readStoreMtime();
        if (mtime > 0 && mtime != lastLoadedMtime) {
            reloadFromDisk();
        }
    }

    private void periodicTick() {
        if (!running) return;
        checkDiskReload();
        sendLanBeacon();
        sendRelayHeartbeat();
        long now = System.currentTimeMillis();
        boolean removed = lanPeers.entrySet().removeIf(e -> now - e.getValue().lastSeenMs > 12_000);
        if (removed || !lanPeers.isEmpty() || relayConnected) {
            notifyListeners();
        }
    }

    private static String readLineRaw(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') return baos.toString(StandardCharsets.UTF_8);
            if (b != '\r') {
                baos.write(b);
                if (baos.size() >= maxBytes) return baos.toString(StandardCharsets.UTF_8);
            }
        }
        return baos.size() == 0 ? null : baos.toString(StandardCharsets.UTF_8);
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString();
    }
}
