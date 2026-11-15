package com.jjn.client.friends;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jjn.client.JjnClientMod;
import com.jjn.client.ui.JjnTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.GameMode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-game Friends, Live Activity, World Hosting (LAN / UPnP / Relay Tunnel),
 * Friend-Only Access Lock, and 1-Click World Join coordinator for MC 1.21.4.
 */
public final class JjnFriendsManager {

    public static final String MC_VERSION = "1.21.4";
    public static final String MULTICAST_GROUP = "239.255.42.99";
    public static final int DISCOVERY_PORT = 25581;
    public static final int DEFAULT_RELAY_PORT = 25580;

    public static final class FriendEntry {
        public String uuid;
        public String username;
        public String skinUrl = "";
        public long addedAt;
    }

    public static final class FriendRequestEntry {
        public String uuid;
        public String username;
        public long sentAt;
    }

    public static final class StoreData {
        public String relayUrl = "";
        public List<FriendEntry> friends = new ArrayList<>();
        public List<FriendRequestEntry> incomingRequests = new ArrayList<>();
        public List<FriendRequestEntry> outgoingRequests = new ArrayList<>();
    }

    public static final class PendingJoin {
        public String hostName = "";
        public String worldName = "";
        public String mcVersion = "";
        public String directEndpoint = "";
        public String tunnelId = "";
        public String relayUrl = "";
        public long createdAt;
    }

    public static final class PeerStatus {
        public String uuid = "";
        public String username = "";
        public boolean online;
        public String role = "";
        public String state = "Offline";
        public String mcVersion = "";
        public String worldOrServer = "";
        public boolean hosting = false;
        public boolean friendsOnly = true;
        public String tunnelId = "";
        public String directEndpoint = "";
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
        public String fromUuid = "";
        public String fromName = "";
        public String worldName = "";
        public String mcVersion = "";
        public String tunnelId = "";
        public String directEndpoint = "";
        public long sentAt;
    }

    private static final JjnFriendsManager INSTANCE = new JjnFriendsManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT_GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    private volatile boolean started = false;
    private volatile boolean running = true;

    private volatile String selfUuid = "";
    private volatile String selfUsername = "";
    private volatile String selfState = "In Menu";
    private volatile String selfWorldOrServer = "";

    private volatile StoreData data = new StoreData();
    private volatile long lastDiskMtime = 0L;

    // Hosting state
    private volatile boolean hosting = false;
    private volatile boolean hostingClosedLock = false;
    private volatile int hostedPort = 0;
    private volatile boolean hostedFriendsOnly = true;
    private volatile String hostedWorldName = "";
    private volatile String hostedTunnelId = "";
    private volatile String hostedDirectEndpoint = "";
    private volatile String hostedUpnpStatus = "";
    private volatile UpnpPortMapper.MappingResult activeUpnpMapping;
    private final Set<String> tempInvitedUuids = ConcurrentHashMap.newKeySet();
    private final Set<Socket> activeHostTunnelSockets = ConcurrentHashMap.newKeySet();

    // Relay & LAN state
    private volatile boolean relayConnected = false;
    private volatile String relayStatusText = "LAN / VPN mode (no relay set)";
    private volatile String lastNotice = "";
    private volatile long lastNoticeAtMs = 0L;

    private final Map<String, PeerStatus> lanPeers = new ConcurrentHashMap<>();
    private final Map<String, PeerStatus> relayPeers = new ConcurrentHashMap<>();
    private final List<WorldInvite> pendingInvites = new CopyOnWriteArrayList<>();

    private ServerSocket lanControlServer;
    private volatile int lanControlPort = 0;
    private volatile Socket relaySocket;
    private volatile OutputStream relayOut;
    private volatile ServerSocket activeJoinForwarder;

    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "JJN-Game-Friends-Worker");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "JJN-Game-Friends-Timer");
        t.setDaemon(true);
        return t;
    });

    private JjnFriendsManager() {}

    public static JjnFriendsManager get() {
        return INSTANCE;
    }

    private static Path storeDir() {
        return Paths.get(System.getProperty("user.home"), ".jjnclient", "friends");
    }

    private static Path storeFile() {
        return storeDir().resolve("friends.json");
    }

    private static Path pendingJoinFile() {
        return storeDir().resolve("pending-join.json");
    }

    public synchronized void start() {
        if (started) return;
        started = true;
        refreshSelfIdentity(MinecraftClient.getInstance());
        loadFromDisk();
        startLanControlServer();
        startLanDiscoveryListener();
        startRelayLoop();
        scheduler.scheduleAtFixedRate(this::backgroundTick, 1, 2, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "JJN-Friends-Shutdown"));
    }

    public void shutdown() {
        running = false;
        closeJoinForwarder();
        closeHostTunnelSockets();
        UpnpPortMapper.MappingResult m = activeUpnpMapping;
        activeUpnpMapping = null;
        if (m != null) UpnpPortMapper.unmap(m);
        closeRelaySocket();
        try {
            if (lanControlServer != null) lanControlServer.close();
        } catch (IOException ignored) {}
        scheduler.shutdownNow();
        pool.shutdownNow();
    }

    public synchronized void loadFromDisk() {
        Path file = storeFile();
        if (!Files.isRegularFile(file)) {
            data = new StoreData();
            saveToDisk();
            return;
        }
        try {
            lastDiskMtime = Files.getLastModifiedTime(file).toMillis();
            String json = Files.readString(file, StandardCharsets.UTF_8);
            StoreData loaded = GSON.fromJson(json, StoreData.class);
            if (loaded == null) loaded = new StoreData();
            if (loaded.relayUrl == null) loaded.relayUrl = "";
            if (loaded.friends == null) loaded.friends = new ArrayList<>();
            if (loaded.incomingRequests == null) loaded.incomingRequests = new ArrayList<>();
            if (loaded.outgoingRequests == null) loaded.outgoingRequests = new ArrayList<>();
            this.data = loaded;
        } catch (Exception ignored) {
        }
    }

    private synchronized void saveToDisk() {
        try {
            Files.createDirectories(storeDir());
            Path file = storeFile();
            Path tmp = storeDir().resolve("friends.json.tmp");
            Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            lastDiskMtime = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException ignored) {
        }
    }

    private void checkDiskReload() {
        try {
            Path file = storeFile();
            if (Files.isRegularFile(file)) {
                long mtime = Files.getLastModifiedTime(file).toMillis();
                if (mtime > lastDiskMtime + 250) {
                    String prevRelay = data.relayUrl;
                    loadFromDisk();
                    if (!prevRelay.equals(data.relayUrl)) {
                        closeRelaySocket();
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    // --- IDENTITY & TICK ---

    private void refreshSelfIdentity(MinecraftClient mc) {
        if (mc == null) return;
        try {
            var session = mc.getSession();
            if (session != null) {
                UUID rawUuid = session.getUuidOrNull();
                String uuid = rawUuid != null ? normalizeUuid(rawUuid.toString()) : "";
                String name = session.getUsername() == null ? "" : session.getUsername().trim();
                if (!uuid.isEmpty()) this.selfUuid = uuid;
                if (!name.isEmpty()) this.selfUsername = name;
            }
        } catch (Exception ignored) {
        }
    }

    public void tick(MinecraftClient mc) {
        if (mc == null) return;
        refreshSelfIdentity(mc);

        IntegratedServer srv = mc.getServer();
        if (srv != null) {
            String worldName = "Private World";
            try {
                Path p = srv.getSavePath(WorldSavePath.ROOT).normalize().getFileName();
                if (p != null) worldName = p.toString();
            } catch (Exception ignored) {
            }
            if (hosting && !srv.isRemote()) {
                cleanupHostNetworking();
            }
            if (hosting) {
                selfState = "Hosting \"" + (hostedWorldName.isEmpty() ? worldName : hostedWorldName) + "\"";
                selfWorldOrServer = "Hosting \"" + (hostedWorldName.isEmpty() ? worldName : hostedWorldName) + "\"";
            } else {
                selfState = "Singleplayer";
                selfWorldOrServer = "Singleplayer • " + worldName;
            }
        } else {
            hostingClosedLock = false;
            if (hosting) {
                cleanupHostNetworking();
            }
            ServerInfo sd = mc.getCurrentServerEntry();
            if (sd != null && sd.address != null && !sd.address.isBlank()) {
                selfState = "Playing Multiplayer";
                selfWorldOrServer = "Server • " + sd.address.trim();
            } else {
                selfState = "In Menu";
                selfWorldOrServer = "";
            }
        }

        checkPendingJoinFile(mc);
    }

    private long lastPendingCheckMs = 0L;

    private void checkPendingJoinFile(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        if (now - lastPendingCheckMs < 1500) return;
        lastPendingCheckMs = now;

        Path pjFile = pendingJoinFile();
        if (!Files.isRegularFile(pjFile)) return;
        try {
            String json = Files.readString(pjFile, StandardCharsets.UTF_8);
            Files.deleteIfExists(pjFile);
            PendingJoin pj = COMPACT_GSON.fromJson(json, PendingJoin.class);
            if (pj == null || now - pj.createdAt > 90_000) return;

            PeerStatus synthetic = new PeerStatus();
            synthetic.username = pj.hostName;
            synthetic.worldOrServer = pj.worldName;
            synthetic.mcVersion = pj.mcVersion;
            synthetic.directEndpoint = pj.directEndpoint;
            synthetic.tunnelId = pj.tunnelId;
            synthetic.hosting = true;
            synthetic.online = true;
            String relayOverride = pj.relayUrl != null && !pj.relayUrl.isBlank() ? pj.relayUrl : data.relayUrl;
            joinPeerInternal(mc, synthetic, relayOverride);
        } catch (Exception ignored) {
        }
    }

    private void backgroundTick() {
        if (!running) return;
        checkDiskReload();
        sendLanBeacon();
        sendRelayHeartbeat();
        long now = System.currentTimeMillis();
        lanPeers.entrySet().removeIf(e -> now - e.getValue().lastSeenMs > 12_000);
        pendingInvites.removeIf(inv -> now - inv.sentAt > 180_000);
    }

    // --- HOSTING ---

    public boolean isHosting() {
        return hosting;
    }

    public boolean isHostingClosedLock() {
        return hostingClosedLock;
    }

    public boolean isHostedFriendsOnly() {
        return hostedFriendsOnly;
    }

    public int getHostedPort() {
        return hostedPort;
    }

    public String getHostedWorldName() {
        return hostedWorldName;
    }

    public String getHostedDirectEndpoint() {
        return hostedDirectEndpoint;
    }

    public String getHostedUpnpStatus() {
        return hostedUpnpStatus;
    }

    public boolean isFriendsOnlyLockActive() {
        return hosting && hostedFriendsOnly;
    }

    public boolean isAllowedToJoin(UUID uuid, String playerName) {
        if (hostingClosedLock) return false;
        if (!hosting || !hostedFriendsOnly) return true;
        String norm = uuid == null ? "" : normalizeUuid(uuid.toString());
        if (!norm.isEmpty() && norm.equals(selfUuid)) return true;
        if (!norm.isEmpty() && tempInvitedUuids.contains(norm)) return true;
        if (!norm.isEmpty()) {
            for (FriendEntry f : data.friends) {
                if (norm.equals(normalizeUuid(f.uuid))) return true;
            }
        }
        if (playerName != null && !playerName.isBlank()) {
            String offlineUuid = normalizeUuid(UUID.nameUUIDFromBytes(
                    ("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8)).toString());
            if (tempInvitedUuids.contains(offlineUuid)) return true;
            for (FriendEntry f : data.friends) {
                if (playerName.equalsIgnoreCase(f.username)) return true;
            }
        }
        return false;
    }

    public synchronized String startHosting(MinecraftClient mc, GameMode gameMode, boolean allowCheats, boolean friendsOnly) {
        if (mc == null) return "Minecraft not ready.";
        IntegratedServer srv = mc.getServer();
        if (srv == null) return "You must be inside a Singleplayer world.";

        int port = srv.isRemote() ? srv.getServerPort() : findPort();
        if (!srv.isRemote()) {
            boolean ok = srv.openToLan(gameMode, allowCheats, port);
            if (!ok) {
                return "Failed to open local server port " + port + ".";
            }
        }

        String worldName = "Private World";
        try {
            Path p = srv.getSavePath(WorldSavePath.ROOT).normalize().getFileName();
            if (p != null) worldName = p.toString();
        } catch (Exception ignored) {
        }

        this.hostingClosedLock = false;
        this.hosting = true;
        this.hostedPort = port;
        this.hostedFriendsOnly = friendsOnly;
        this.hostedWorldName = worldName;
        this.hostedTunnelId = UUID.randomUUID().toString();
        String localIp = UpnpPortMapper.findOutboundLanIp();
        this.hostedDirectEndpoint = localIp + ":" + port;
        this.hostedUpnpStatus = "Mapping UPnP port " + port + "...";

        setNotice("Hosting \"" + worldName + "\" on port " + port + "!");
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(
                    "[JJN] World hosted on port " + port + " (" + (friendsOnly ? "Friends Only" : "Open") + ")."
            ), false);
        }

        pool.submit(() -> {
            UpnpPortMapper.MappingResult res = UpnpPortMapper.mapTcpPort(port);
            this.activeUpnpMapping = res;
            if (res.success() && !res.externalIp().isBlank()) {
                this.hostedDirectEndpoint = res.externalIp() + ":" + port;
                this.hostedUpnpStatus = res.message();
            } else {
                this.hostedDirectEndpoint = res.localIp() + ":" + port;
                this.hostedUpnpStatus = res.message();
            }
            sendRelayHostOpen();
            sendLanBeacon();
        });

        sendRelayHostOpen();
        sendLanBeacon();
        return "";
    }

    public synchronized void setHostedFriendsOnly(boolean friendsOnly) {
        this.hostedFriendsOnly = friendsOnly;
        if (hosting) {
            sendRelayHostOpen();
            sendLanBeacon();
        }
    }

    public synchronized void stopHosting(MinecraftClient mc) {
        if (mc != null && mc.getServer() != null && mc.getServer().isRemote()) {
            hostingClosedLock = true;
        }
        cleanupHostNetworking();
        setNotice("Stopped hosting world.");
        if (mc != null && mc.player != null) {
            mc.player.sendMessage(Text.literal("[JJN] Stopped hosting world to friends."), false);
        }
    }

    private void cleanupHostNetworking() {
        hosting = false;
        hostedPort = 0;
        hostedTunnelId = "";
        hostedDirectEndpoint = "";
        hostedUpnpStatus = "";
        closeHostTunnelSockets();
        UpnpPortMapper.MappingResult m = activeUpnpMapping;
        activeUpnpMapping = null;
        if (m != null) {
            pool.submit(() -> UpnpPortMapper.unmap(m));
        }
        if (relayConnected) {
            JsonObject obj = new JsonObject();
            obj.addProperty("op", "host_close");
            sendRelayJson(obj);
        }
        sendLanBeacon();
    }

    private void sendRelayHostOpen() {
        if (!relayConnected || !hosting) return;
        JsonObject obj = new JsonObject();
        obj.addProperty("op", "host_open");
        obj.addProperty("tunnelId", hostedTunnelId);
        obj.addProperty("worldName", hostedWorldName);
        obj.addProperty("mcVersion", MC_VERSION);
        obj.addProperty("directEndpoint", hostedDirectEndpoint);
        obj.addProperty("friendsOnly", hostedFriendsOnly);
        sendRelayJson(obj);
    }

    public void inviteFriend(PeerStatus friend) {
        if (!hosting || friend == null || friend.uuid == null || friend.uuid.isBlank()) return;
        String norm = normalizeUuid(friend.uuid);
        tempInvitedUuids.add(norm);

        if (relayConnected) {
            JsonObject cmd = new JsonObject();
            cmd.addProperty("op", "invite");
            cmd.addProperty("targetUuid", norm);
            cmd.addProperty("worldName", hostedWorldName);
            cmd.addProperty("mcVersion", MC_VERSION);
            cmd.addProperty("tunnelId", hostedTunnelId);
            cmd.addProperty("directEndpoint", hostedDirectEndpoint);
            sendRelayJson(cmd);
        }

        for (PeerStatus lp : lanPeers.values()) {
            if (norm.equals(lp.uuid) && lp.lanControlPort > 0 && lp.lanAddress != null && !lp.lanAddress.isBlank()) {
                pool.submit(() -> {
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(lp.lanAddress, lp.lanControlPort), 2000);
                        JsonObject msg = new JsonObject();
                        msg.addProperty("type", "INVITE");
                        msg.addProperty("fromUuid", selfUuid);
                        msg.addProperty("fromName", selfUsername);
                        msg.addProperty("worldName", hostedWorldName);
                        msg.addProperty("mcVersion", MC_VERSION);
                        msg.addProperty("tunnelId", hostedTunnelId);
                        msg.addProperty("directEndpoint", lp.lanAddress.equals("127.0.0.1")
                                ? "127.0.0.1:" + hostedPort
                                : UpnpPortMapper.findOutboundLanIp() + ":" + hostedPort);
                        s.getOutputStream().write((COMPACT_GSON.toJson(msg) + "\n").getBytes(StandardCharsets.UTF_8));
                        s.getOutputStream().flush();
                    } catch (IOException ignored) {
                    }
                });
                break;
            }
        }
        setNotice("Sent world invite to " + (friend.username.isBlank() ? norm : friend.username) + "!");
    }

    // --- JOINING ---

    public void joinPeer(MinecraftClient mc, PeerStatus peer) {
        joinPeerInternal(mc, peer, data.relayUrl);
    }

    public void joinInvite(MinecraftClient mc, WorldInvite invite) {
        if (invite == null) return;
        pendingInvites.remove(invite);
        PeerStatus p = new PeerStatus();
        p.uuid = invite.fromUuid;
        p.username = invite.fromName;
        p.worldOrServer = invite.worldName;
        p.mcVersion = invite.mcVersion;
        p.tunnelId = invite.tunnelId;
        p.directEndpoint = invite.directEndpoint;
        p.hosting = true;
        p.online = true;
        joinPeerInternal(mc, p, data.relayUrl);
    }

    private void joinPeerInternal(MinecraftClient mc, PeerStatus peer, String relayUrlToUse) {
        if (mc == null || peer == null) return;
        pool.submit(() -> {
            try {
                boolean sameLan = isFreshLanPeer(peer.uuid);
                String direct = peer.joinAddressFallback();
                String tunnelId = peer.tunnelId == null ? "" : peer.tunnelId.trim();
                String relayUrl = relayUrlToUse == null ? "" : relayUrlToUse.trim();

                String targetAddr;
                boolean isLanConn;

                if (sameLan && peer.lanAddress != null && !peer.lanAddress.isBlank() && !direct.isEmpty()) {
                    String[] hp = parseHostPort(direct);
                    targetAddr = hp != null ? peer.lanAddress + ":" + hp[1] : direct;
                    isLanConn = true;
                } else if (peer.hosting && !sameLan && !tunnelId.isEmpty() && !relayUrl.isEmpty()) {
                    int localPort = startLocalTunnelForwarder(relayUrl, tunnelId);
                    targetAddr = "127.0.0.1:" + localPort;
                    isLanConn = false;
                } else if (!direct.isEmpty()) {
                    targetAddr = direct;
                    isLanConn = sameLan;
                } else if (!tunnelId.isEmpty() && !relayUrl.isEmpty()) {
                    int localPort = startLocalTunnelForwarder(relayUrl, tunnelId);
                    targetAddr = "127.0.0.1:" + localPort;
                    isLanConn = false;
                } else {
                    setNotice("No reachable endpoint for " + peer.username);
                    return;
                }

                String displayTitle = peer.username != null && !peer.username.isBlank()
                        ? peer.username + "'s World"
                        : "Friend's World";

                mc.execute(() -> connectToAddress(mc, displayTitle, targetAddr, isLanConn));
            } catch (Exception ex) {
                setNotice("Join failed: " + ex.getMessage());
            }
        });
    }

    private void connectToAddress(MinecraftClient mc, String name, String addr, boolean lan) {
        if (hosting) {
            stopHosting(mc);
        }
        if (mc.world != null) {
            boolean sp = mc.isInSingleplayer();
            mc.world.disconnect();
            if (sp) {
                mc.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
            } else {
                mc.disconnect();
            }
        }
        JjnTitleScreen backScreen = new JjnTitleScreen();
        ServerAddress serverAddress = ServerAddress.parse(addr);
        ServerInfo serverInfo = new ServerInfo(name, addr, lan ? ServerInfo.ServerType.LAN : ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(backScreen, mc, serverAddress, serverInfo, false, null);
    }

    private int startLocalTunnelForwarder(String relayUrl, String tunnelId) throws IOException {
        closeJoinForwarder();
        String[] hp = parseHostPort(relayUrl);
        if (hp == null) throw new IOException("Invalid Relay address");
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

    private void bridgeJoinSocket(Socket mcClient, String relayHost, int relayPort, String tunnelId) {
        try {
            mcClient.setTcpNoDelay(true);
            Socket relay = new Socket();
            relay.connect(new InetSocketAddress(relayHost, relayPort), 8000);
            relay.setTcpNoDelay(true);
            String hdr = "TUNNEL_JOIN " + tunnelId + " "
                    + (selfUuid.isEmpty() ? "player" : selfUuid) + " "
                    + (selfUsername.isEmpty() ? "Player" : selfUsername.replace(' ', '_')) + "\n";
            relay.getOutputStream().write(hdr.getBytes(StandardCharsets.UTF_8));
            relay.getOutputStream().flush();

            pool.submit(() -> pump(mcClient, relay));
            pump(relay, mcClient);
        } catch (IOException ignored) {
            try { mcClient.close(); } catch (IOException ignored2) {}
        }
    }

    private void closeJoinForwarder() {
        ServerSocket ss = activeJoinForwarder;
        activeJoinForwarder = null;
        if (ss != null) {
            try { ss.close(); } catch (IOException ignored) {}
        }
    }

    private void closeHostTunnelSockets() {
        for (Socket s : activeHostTunnelSockets) {
            try { s.close(); } catch (IOException ignored) {}
        }
        activeHostTunnelSockets.clear();
    }

    // --- FRIENDS & REQUESTS ---

    public List<PeerStatus> getFriendsWithStatus() {
        long now = System.currentTimeMillis();
        Map<String, PeerStatus> map = new LinkedHashMap<>();
        for (FriendEntry f : data.friends) {
            PeerStatus p = new PeerStatus();
            p.uuid = normalizeUuid(f.uuid);
            p.username = f.username;
            p.online = false;
            p.state = "Offline";
            map.put(p.uuid, p);
        }

        for (PeerStatus rp : relayPeers.values()) {
            if (rp.uuid == null || rp.uuid.equals(selfUuid)) continue;
            PeerStatus target = map.computeIfAbsent(rp.uuid, k -> {
                PeerStatus c = new PeerStatus();
                c.uuid = rp.uuid;
                c.username = rp.username;
                return c;
            });
            mergePeer(target, rp);
        }

        for (PeerStatus lp : lanPeers.values()) {
            if (lp.uuid == null || lp.uuid.equals(selfUuid)) continue;
            if (now - lp.lastSeenMs > 10_000) continue;
            PeerStatus target = map.get(lp.uuid);
            if (target != null) {
                mergePeer(target, lp);
            }
        }

        List<PeerStatus> out = new ArrayList<>(map.values());
        out.sort((a, b) -> {
            if (a.online != b.online) return a.online ? -1 : 1;
            if (a.isJoinable() != b.isJoinable()) return a.isJoinable() ? -1 : 1;
            return a.username.compareToIgnoreCase(b.username);
        });
        return out;
    }

    public List<PeerStatus> getNearbyLanPlayers() {
        long now = System.currentTimeMillis();
        Set<String> friendSet = new HashSet<>();
        for (FriendEntry f : data.friends) {
            friendSet.add(normalizeUuid(f.uuid));
        }
        Map<String, PeerStatus> out = new LinkedHashMap<>();
        for (PeerStatus lp : lanPeers.values()) {
            if (lp.uuid == null || lp.uuid.equals(selfUuid)) continue;
            if (now - lp.lastSeenMs > 10_000) continue;
            if (friendSet.contains(lp.uuid)) continue;
            out.merge(lp.uuid, lp, (a, b) -> "game".equals(b.role) ? b : a);
        }
        return new ArrayList<>(out.values());
    }

    private static void mergePeer(PeerStatus dst, PeerStatus src) {
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
            if (!src.tunnelId.isBlank()) dst.tunnelId = src.tunnelId;
            if (!src.directEndpoint.isBlank()) dst.directEndpoint = src.directEndpoint;
            if (src.lanControlPort > 0) {
                dst.lanControlPort = src.lanControlPort;
                dst.lanAddress = src.lanAddress;
            }
            dst.lastSeenMs = Math.max(dst.lastSeenMs, src.lastSeenMs);
        }
    }

    public boolean isFreshLanPeer(String uuid) {
        if (uuid == null || uuid.isBlank()) return false;
        String norm = normalizeUuid(uuid);
        long now = System.currentTimeMillis();
        for (PeerStatus lp : lanPeers.values()) {
            if (norm.equals(lp.uuid) && now - lp.lastSeenMs <= 10_000) return true;
        }
        return false;
    }

    public List<FriendRequestEntry> getIncomingRequests() {
        return new ArrayList<>(data.incomingRequests);
    }

    public List<FriendRequestEntry> getOutgoingRequests() {
        return new ArrayList<>(data.outgoingRequests);
    }

    public List<WorldInvite> getPendingInvites() {
        long cutoff = System.currentTimeMillis() - 180_000;
        pendingInvites.removeIf(inv -> inv.sentAt < cutoff);
        return new ArrayList<>(pendingInvites);
    }

    public WorldInvite getLatestToastInvite() {
        long now = System.currentTimeMillis();
        for (WorldInvite inv : pendingInvites) {
            if (now - inv.sentAt <= 25_000) return inv;
        }
        return null;
    }

    public String getRelayUrl() {
        return data.relayUrl == null ? "" : data.relayUrl;
    }

    public void setRelayUrl(String url) {
        data.relayUrl = url == null ? "" : url.trim();
        saveToDisk();
        closeRelaySocket();
        relayStatusText = data.relayUrl.isBlank() ? "LAN / VPN mode (no relay set)" : "Connecting to " + data.relayUrl + "...";
        setNotice(data.relayUrl.isBlank() ? "Switched to LAN / VPN mode." : "Relay updated: " + data.relayUrl);
    }

    public boolean isRelayConnected() {
        return relayConnected;
    }

    public String getRelayStatusText() {
        return relayStatusText;
    }

    public String getNotice() {
        if (System.currentTimeMillis() - lastNoticeAtMs > 8_000) return "";
        return lastNotice;
    }

    private void setNotice(String text) {
        this.lastNotice = text == null ? "" : text;
        this.lastNoticeAtMs = System.currentTimeMillis();
    }

    public void addFriendByName(String username) {
        String clean = username == null ? "" : username.trim();
        if (clean.isEmpty()) return;
        pool.submit(() -> {
            String[] resolved = lookupUuidAndName(clean);
            String uuid = resolved[0];
            String exactName = resolved[1];
            if (uuid.equals(selfUuid)) {
                setNotice("Cannot add yourself as a friend.");
                return;
            }
            for (FriendEntry f : data.friends) {
                if (uuid.equals(normalizeUuid(f.uuid))) {
                    setNotice(exactName + " is already your friend.");
                    return;
                }
            }
            if (relayConnected) {
                JsonObject cmd = new JsonObject();
                cmd.addProperty("op", "add_friend");
                cmd.addProperty("targetName", exactName);
                cmd.addProperty("targetUuid", uuid);
                sendRelayJson(cmd);
                setNotice("Sent friend request to " + exactName + " via Relay.");
            } else {
                sendLanPacket("FRIEND_REQ", uuid);
                FriendEntry entry = new FriendEntry();
                entry.uuid = uuid;
                entry.username = exactName;
                entry.addedAt = System.currentTimeMillis();
                data.friends.add(entry);
                saveToDisk();
                setNotice("Added " + exactName + " to friends!");
            }
        });
    }

    public void acceptRequest(String targetUuid) {
        String norm = normalizeUuid(targetUuid);
        String name = norm;
        for (FriendRequestEntry r : data.incomingRequests) {
            if (norm.equals(normalizeUuid(r.uuid))) {
                name = r.username;
                break;
            }
        }
        data.incomingRequests.removeIf(r -> norm.equals(normalizeUuid(r.uuid)));
        if (data.friends.stream().noneMatch(f -> norm.equals(normalizeUuid(f.uuid)))) {
            FriendEntry f = new FriendEntry();
            f.uuid = norm;
            f.username = name;
            f.addedAt = System.currentTimeMillis();
            data.friends.add(f);
        }
        saveToDisk();
        if (relayConnected) {
            JsonObject cmd = new JsonObject();
            cmd.addProperty("op", "accept_friend");
            cmd.addProperty("targetUuid", norm);
            sendRelayJson(cmd);
        }
        sendLanPacket("FRIEND_ACCEPT", norm);
        setNotice("Added " + name + "!");
    }

    public void declineRequest(String targetUuid) {
        String norm = normalizeUuid(targetUuid);
        data.incomingRequests.removeIf(r -> norm.equals(normalizeUuid(r.uuid)));
        data.outgoingRequests.removeIf(r -> norm.equals(normalizeUuid(r.uuid)));
        saveToDisk();
        if (relayConnected) {
            JsonObject cmd = new JsonObject();
            cmd.addProperty("op", "decline_friend");
            cmd.addProperty("targetUuid", norm);
            sendRelayJson(cmd);
        }
    }

    public void removeFriend(String targetUuid) {
        String norm = normalizeUuid(targetUuid);
        data.friends.removeIf(f -> norm.equals(normalizeUuid(f.uuid)));
        saveToDisk();
        if (relayConnected) {
            JsonObject cmd = new JsonObject();
            cmd.addProperty("op", "remove_friend");
            cmd.addProperty("targetUuid", norm);
            sendRelayJson(cmd);
        }
    }

    public void dismissInvite(WorldInvite inv) {
        pendingInvites.remove(inv);
    }

    // --- LAN CONTROL + BEACON ---

    private void startLanControlServer() {
        try {
            lanControlServer = new ServerSocket(0);
            lanControlPort = lanControlServer.getLocalPort();
            pool.submit(() -> {
                while (running && !lanControlServer.isClosed()) {
                    try {
                        Socket s = lanControlServer.accept();
                        pool.submit(() -> handleLanMessage(s));
                    } catch (IOException ignored) {
                        break;
                    }
                }
            });
        } catch (IOException ignored) {
        }
    }

    private void handleLanMessage(Socket s) {
        try (s) {
            String line = readLineRaw(s.getInputStream(), 16384);
            if (line == null || line.isBlank()) return;
            JsonObject msg = JsonParser.parseString(line).getAsJsonObject();
            String type = str(msg, "type");
            String fromUuid = normalizeUuid(str(msg, "fromUuid"));
            String fromName = str(msg, "fromName");
            if (fromUuid.isEmpty() || fromUuid.equals(selfUuid)) return;

            if ("FRIEND_REQ".equals(type)) {
                boolean friend = data.friends.stream().anyMatch(f -> fromUuid.equals(normalizeUuid(f.uuid)));
                boolean req = data.incomingRequests.stream().anyMatch(r -> fromUuid.equals(normalizeUuid(r.uuid)));
                if (!friend && !req) {
                    FriendRequestEntry e = new FriendRequestEntry();
                    e.uuid = fromUuid;
                    e.username = fromName;
                    e.sentAt = System.currentTimeMillis();
                    data.incomingRequests.add(e);
                    saveToDisk();
                    setNotice(fromName + " sent you a friend request!");
                }
            } else if ("FRIEND_ACCEPT".equals(type)) {
                data.outgoingRequests.removeIf(r -> fromUuid.equals(normalizeUuid(r.uuid)));
                if (data.friends.stream().noneMatch(f -> fromUuid.equals(normalizeUuid(f.uuid)))) {
                    FriendEntry f = new FriendEntry();
                    f.uuid = fromUuid;
                    f.username = fromName;
                    f.addedAt = System.currentTimeMillis();
                    data.friends.add(f);
                    saveToDisk();
                }
                setNotice(fromName + " accepted your friend request!");
            } else if ("INVITE".equals(type)) {
                addIncomingInvite(
                        fromUuid, fromName,
                        str(msg, "worldName"), str(msg, "mcVersion"),
                        str(msg, "tunnelId"), str(msg, "directEndpoint")
                );
            }
        } catch (Exception ignored) {
        }
    }

    private void addIncomingInvite(String fromUuid, String fromName, String worldName,
                                   String mcVersion, String tunnelId, String directEndpoint) {
        WorldInvite inv = new WorldInvite();
        inv.fromUuid = normalizeUuid(fromUuid);
        inv.fromName = fromName.isBlank() ? "Friend" : fromName;
        inv.worldName = worldName.isBlank() ? "Private World" : worldName;
        inv.mcVersion = mcVersion;
        inv.tunnelId = tunnelId;
        inv.directEndpoint = directEndpoint;
        inv.sentAt = System.currentTimeMillis();
        pendingInvites.removeIf(old -> inv.fromUuid.equals(old.fromUuid));
        pendingInvites.add(0, inv);
        setNotice(inv.fromName + " invited you to \"" + inv.worldName + "\"! Press [J] to join.");

        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal(
                        "[JJN] " + inv.fromName + " invited you to \"" + inv.worldName + "\"! Press [J] to join."
                ), false);
            }
        });
    }

    private void sendLanPacket(String type, String targetUuid) {
        if (selfUuid.isEmpty() || selfUsername.isEmpty()) return;
        for (PeerStatus lp : lanPeers.values()) {
            if (targetUuid.equals(lp.uuid) && lp.lanControlPort > 0 && lp.lanAddress != null) {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(lp.lanAddress, lp.lanControlPort), 2000);
                    JsonObject obj = new JsonObject();
                    obj.addProperty("type", type);
                    obj.addProperty("fromUuid", selfUuid);
                    obj.addProperty("fromName", selfUsername);
                    s.getOutputStream().write((COMPACT_GSON.toJson(obj) + "\n").getBytes(StandardCharsets.UTF_8));
                    s.getOutputStream().flush();
                    return;
                } catch (IOException ignored) {
                }
            }
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
                        String uuid = normalizeUuid(str(obj, "uuid"));
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
                        ps.lanControlPort = obj.has("controlPort") ? obj.get("controlPort").getAsInt() : 0;
                        ps.lanAddress = pkt.getAddress().getHostAddress();
                        ps.lastSeenMs = System.currentTimeMillis();
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
            obj.addProperty("role", "game");
            obj.addProperty("state", selfState);
            obj.addProperty("mcVersion", MC_VERSION);
            obj.addProperty("worldOrServer", selfWorldOrServer);
            obj.addProperty("hosting", hosting);
            obj.addProperty("friendsOnly", hostedFriendsOnly);
            obj.addProperty("tunnelId", hostedTunnelId);
            obj.addProperty("directEndpoint", hostedDirectEndpoint);
            obj.addProperty("controlPort", lanControlPort);
            byte[] bytes = COMPACT_GSON.toJson(obj).getBytes(StandardCharsets.UTF_8);
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            ds.send(new DatagramPacket(bytes, bytes.length, group, DISCOVERY_PORT));
        } catch (Exception ignored) {
        }
    }

    // --- RELAY ---

    private void startRelayLoop() {
        pool.submit(() -> {
            while (running) {
                String url = data.relayUrl == null ? "" : data.relayUrl.trim();
                if (url.isEmpty() || selfUuid.isEmpty() || selfUsername.isEmpty()) {
                    relayConnected = false;
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) { break; }
                    continue;
                }
                String[] hp = parseHostPort(url);
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
                    relayStatusText = "Connected (" + hp[0] + ":" + hp[1] + ")";
                    sendRelayHello();
                    if (hosting) sendRelayHostOpen();

                    InputStream in = sock.getInputStream();
                    while (running && !sock.isClosed()) {
                        String line = readLineRaw(in, 262144);
                        if (line == null) break;
                        if (!line.isBlank()) handleRelayMessage(line, hp[0], Integer.parseInt(hp[1]));
                    }
                } catch (Exception ignored) {
                    relayStatusText = "Relay offline — LAN / VPN mode active";
                } finally {
                    relayConnected = false;
                    relaySocket = null;
                    relayOut = null;
                }
                try { Thread.sleep(4000); } catch (InterruptedException ignored) { break; }
            }
        });
    }

    private void handleRelayMessage(String line, String relayHost, int relayPort) {
        try {
            JsonObject msg = JsonParser.parseString(line).getAsJsonObject();
            String op = str(msg, "op");
            if ("state".equals(op)) {
                Map<String, PeerStatus> next = new ConcurrentHashMap<>();
                List<FriendEntry> merged = new ArrayList<>();
                if (msg.has("friends") && msg.get("friends").isJsonArray()) {
                    for (JsonElement el : msg.getAsJsonArray("friends")) {
                        JsonObject f = el.getAsJsonObject();
                        PeerStatus ps = new PeerStatus();
                        ps.uuid = normalizeUuid(str(f, "uuid"));
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
                        ps.lastSeenMs = System.currentTimeMillis();
                        next.put(ps.uuid, ps);

                        FriendEntry fe = new FriendEntry();
                        fe.uuid = ps.uuid;
                        fe.username = ps.username;
                        fe.addedAt = System.currentTimeMillis();
                        merged.add(fe);
                    }
                }
                relayPeers.clear();
                relayPeers.putAll(next);

                for (FriendEntry rf : merged) {
                    if (data.friends.stream().noneMatch(l -> rf.uuid.equals(normalizeUuid(l.uuid)))) {
                        data.friends.add(rf);
                    }
                }
                if (msg.has("incoming") && msg.get("incoming").isJsonArray()) {
                    data.incomingRequests.clear();
                    for (JsonElement el : msg.getAsJsonArray("incoming")) {
                        JsonObject r = el.getAsJsonObject();
                        FriendRequestEntry req = new FriendRequestEntry();
                        req.uuid = normalizeUuid(str(r, "uuid"));
                        req.username = str(r, "username");
                        req.sentAt = r.has("sentAt") ? r.get("sentAt").getAsLong() : System.currentTimeMillis();
                        data.incomingRequests.add(req);
                    }
                }
                if (msg.has("outgoing") && msg.get("outgoing").isJsonArray()) {
                    data.outgoingRequests.clear();
                    for (JsonElement el : msg.getAsJsonArray("outgoing")) {
                        JsonObject r = el.getAsJsonObject();
                        FriendRequestEntry req = new FriendRequestEntry();
                        req.uuid = normalizeUuid(str(r, "uuid"));
                        req.username = str(r, "username");
                        req.sentAt = r.has("sentAt") ? r.get("sentAt").getAsLong() : System.currentTimeMillis();
                        data.outgoingRequests.add(req);
                    }
                }
                saveToDisk();
            } else if ("invite_received".equals(op)) {
                addIncomingInvite(
                        str(msg, "fromUuid"), str(msg, "fromName"),
                        str(msg, "worldName"), str(msg, "mcVersion"),
                        str(msg, "tunnelId"), str(msg, "directEndpoint")
                );
            } else if ("tunnel_need".equals(op)) {
                String connId = str(msg, "connId");
                String tunnelId = str(msg, "tunnelId");
                String joinerUuid = normalizeUuid(str(msg, "joinerUuid"));
                String joinerName = str(msg, "joinerName");
                if (!hosting || hostedPort <= 0 || !tunnelId.equals(hostedTunnelId)) return;
                if (hostedFriendsOnly && !isAllowedToJoin(parseUuidOrNull(joinerUuid), joinerName)) {
                    JjnClientMod.LOGGER.info("Blocked non-friend tunnel join from {} ({})", joinerName, joinerUuid);
                    return;
                }
                pool.submit(() -> bridgeHostTunnel(relayHost, relayPort, connId, hostedPort));
            } else if ("notice".equals(op)) {
                String n = str(msg, "message");
                if (!n.isBlank()) setNotice(n);
            }
        } catch (Exception ignored) {
        }
    }

    private void bridgeHostTunnel(String relayHost, int relayPort, String connId, int localMcPort) {
        Socket relayConn = null;
        Socket localMc = null;
        try {
            relayConn = new Socket();
            relayConn.connect(new InetSocketAddress(relayHost, relayPort), 8000);
            relayConn.setTcpNoDelay(true);
            relayConn.getOutputStream().write(("TUNNEL_BIND " + connId + "\n").getBytes(StandardCharsets.UTF_8));
            relayConn.getOutputStream().flush();

            localMc = new Socket();
            localMc.connect(new InetSocketAddress("127.0.0.1", localMcPort), 5000);
            localMc.setTcpNoDelay(true);

            activeHostTunnelSockets.add(relayConn);
            activeHostTunnelSockets.add(localMc);

            Socket finalRelay = relayConn;
            Socket finalMc = localMc;
            pool.submit(() -> {
                pump(finalRelay, finalMc);
                activeHostTunnelSockets.remove(finalRelay);
                activeHostTunnelSockets.remove(finalMc);
            });
            pump(finalMc, finalRelay);
        } catch (IOException ignored) {
            if (relayConn != null) try { relayConn.close(); } catch (IOException ignored2) {}
            if (localMc != null) try { localMc.close(); } catch (IOException ignored2) {}
        } finally {
            activeHostTunnelSockets.remove(relayConn);
            activeHostTunnelSockets.remove(localMc);
        }
    }

    private void sendRelayHello() {
        if (!relayConnected || selfUuid.isEmpty() || selfUsername.isEmpty()) return;
        JsonObject hello = new JsonObject();
        hello.addProperty("op", "hello");
        hello.addProperty("uuid", selfUuid);
        hello.addProperty("username", selfUsername);
        hello.addProperty("role", "game");
        hello.addProperty("state", selfState);
        hello.addProperty("mcVersion", MC_VERSION);
        hello.addProperty("worldOrServer", selfWorldOrServer);
        hello.addProperty("hosting", hosting);
        hello.addProperty("friendsOnly", hostedFriendsOnly);
        hello.addProperty("tunnelId", hostedTunnelId);
        hello.addProperty("directEndpoint", hostedDirectEndpoint);
        sendRelayJson(hello);
    }

    private void sendRelayHeartbeat() {
        if (!relayConnected || selfUuid.isEmpty()) return;
        JsonObject hb = new JsonObject();
        hb.addProperty("op", "heartbeat");
        hb.addProperty("role", "game");
        hb.addProperty("state", selfState);
        hb.addProperty("mcVersion", MC_VERSION);
        hb.addProperty("worldOrServer", selfWorldOrServer);
        hb.addProperty("hosting", hosting);
        hb.addProperty("friendsOnly", hostedFriendsOnly);
        hb.addProperty("tunnelId", hostedTunnelId);
        hb.addProperty("directEndpoint", hostedDirectEndpoint);
        sendRelayJson(hb);
    }

    private synchronized void sendRelayJson(JsonObject obj) {
        OutputStream out = relayOut;
        if (out == null) return;
        try {
            out.write((COMPACT_GSON.toJson(obj) + "\n").getBytes(StandardCharsets.UTF_8));
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

    // --- UTILITIES ---

    private static int findPort() {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        } catch (IOException e) {
            return 25565;
        }
    }

    private static String[] lookupUuidAndName(String username) {
        try {
            String enc = URLEncoder.encode(username, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + enc))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
                String id = normalizeUuid(str(obj, "id"));
                String name = str(obj, "name");
                if (!id.isEmpty()) {
                    return new String[]{id, name.isEmpty() ? username : name};
                }
            }
        } catch (Exception ignored) {
        }
        String offline = normalizeUuid(UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString());
        return new String[]{offline, username};
    }

    private static UUID parseUuidOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(normalizeUuid(raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String[] parseHostPort(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        if (trimmed.startsWith("tcp://")) trimmed = trimmed.substring(6);
        int idx = trimmed.lastIndexOf(':');
        if (idx > 0 && idx < trimmed.length() - 1) {
            String host = trimmed.substring(0, idx).trim();
            String portStr = trimmed.substring(idx + 1).trim();
            try {
                int p = Integer.parseInt(portStr);
                if (p > 0 && p <= 65535) return new String[]{host, String.valueOf(p)};
            } catch (NumberFormatException ignored) {}
        }
        return new String[]{trimmed, String.valueOf(DEFAULT_RELAY_PORT)};
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

    public static String normalizeUuid(String raw) {
        if (raw == null) return "";
        String hex = raw.replace("-", "").trim().toLowerCase(Locale.ROOT);
        if (hex.length() != 32) return raw.trim().toLowerCase(Locale.ROOT);
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString();
    }
}
