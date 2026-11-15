package com.jjn.launcher.friends;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jjn.launcher.LauncherSettings;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;

/**
 * Shared persisted friends list, requests, relay URL, and pending-join handoff
 * stored under ~/.jjnclient/friends/.
 */
public final class FriendsStore {

    public static final int DEFAULT_RELAY_PORT = 25580;

    public static final class FriendEntry {
        public String uuid;
        public String username;
        public String skinUrl = "";
        public long addedAt;

        public FriendEntry() {}

        public FriendEntry(String uuid, String username, String skinUrl, long addedAt) {
            this.uuid = normalizeUuid(uuid);
            this.username = username;
            this.skinUrl = skinUrl == null ? "" : skinUrl;
            this.addedAt = addedAt;
        }
    }

    public static final class FriendRequestEntry {
        public String uuid;
        public String username;
        public long sentAt;

        public FriendRequestEntry() {}

        public FriendRequestEntry(String uuid, String username, long sentAt) {
            this.uuid = normalizeUuid(uuid);
            this.username = username;
            this.sentAt = sentAt;
        }
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
        public long createdAt = System.currentTimeMillis();
    }

    public record MojangProfile(String uuid, String username, String skinUrl, boolean onlineVerified) {}

    public record RelayTestResult(boolean reachable, long latencyMs, int onlineUsers, int activeTunnels, String message) {}

    public static String defaultRelayFallback() {
        String prop = System.getProperty("jjn.relay.url");
        if (prop != null && !prop.isBlank()) return prop.trim();
        String env = System.getenv("JJN_RELAY_URL");
        if (env != null && !env.isBlank()) return env.trim();
        return "";
    }

    public static String resolveEffectiveRelayUrl(String configured) {
        if (configured != null && !configured.isBlank()) return configured.trim();
        return defaultRelayFallback();
    }

    public static RelayTestResult pingRelay(String rawUrl, int timeoutMs) {
        String[] hp = parseHostPort(rawUrl);
        if (hp == null) {
            return new RelayTestResult(false, -1, 0, 0, "Enter a Relay address like host:25580 first.");
        }
        String host = hp[0];
        int port = Integer.parseInt(hp[1]);
        long start = System.nanoTime();
        try (java.net.Socket sock = new java.net.Socket()) {
            sock.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            sock.setSoTimeout(timeoutMs);
            sock.getOutputStream().write("PING\n".getBytes(StandardCharsets.UTF_8));
            sock.getOutputStream().flush();

            StringBuilder sb = new StringBuilder();
            java.io.InputStream in = sock.getInputStream();
            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n') break;
                if (b != '\r' && sb.length() < 256) sb.append((char) b);
            }
            long ms = Math.max(1L, (System.nanoTime() - start) / 1_000_000L);
            String reply = sb.toString().trim();
            if (reply.startsWith("PONG jjn-relay")) {
                String[] parts = reply.split("\\s+");
                int online = parts.length >= 3 ? parseIntSafe(parts[2], 0) : 0;
                int tunnels = parts.length >= 4 ? parseIntSafe(parts[3], 0) : 0;
                return new RelayTestResult(true, ms, online, tunnels,
                        "Relay reachable in " + ms + " ms • " + online + " online • " + tunnels + " active world tunnel(s)");
            }
            return new RelayTestResult(true, ms, 0, 0,
                    "TCP port reachable in " + ms + " ms (" + host + ":" + port + ")");
        } catch (java.net.UnknownHostException ex) {
            return new RelayTestResult(false, -1, 0, 0, "Unknown host: " + host);
        } catch (java.net.SocketTimeoutException ex) {
            return new RelayTestResult(false, -1, 0, 0, "Connection timed out to " + host + ":" + port + " (check firewall / port 25580)");
        } catch (IOException ex) {
            return new RelayTestResult(false, -1, 0, 0, "Cannot reach " + host + ":" + port + " (" + ex.getMessage() + ")");
        }
    }

    private static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    public static Path dir() {
        return LauncherSettings.ROOT_DIR.resolve("friends");
    }

    public static Path storeFile() {
        return dir().resolve("friends.json");
    }

    public static Path pendingJoinFile() {
        return dir().resolve("pending-join.json");
    }

    public synchronized StoreData load() {
        Path file = storeFile();
        if (!Files.isRegularFile(file)) {
            StoreData data = new StoreData();
            save(data);
            return data;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            StoreData data = GSON.fromJson(json, StoreData.class);
            if (data == null) data = new StoreData();
            if (data.relayUrl == null) data.relayUrl = "";
            if (data.friends == null) data.friends = new ArrayList<>();
            if (data.incomingRequests == null) data.incomingRequests = new ArrayList<>();
            if (data.outgoingRequests == null) data.outgoingRequests = new ArrayList<>();
            return data;
        } catch (Exception ignored) {
            return new StoreData();
        }
    }

    public synchronized void save(StoreData data) {
        try {
            Files.createDirectories(dir());
            Path file = storeFile();
            Path tmp = dir().resolve("friends.json.tmp");
            Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    public synchronized void writePendingJoin(PendingJoin join) {
        try {
            Files.createDirectories(dir());
            Files.writeString(pendingJoinFile(), GSON.toJson(join), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static MojangProfile lookupPlayer(String username) {
        String clean = username == null ? "" : username.trim();
        if (clean.isEmpty()) {
            return null;
        }
        try {
            String encoded = URLEncoder.encode(clean, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + encoded))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
                String rawId = obj.has("id") ? obj.get("id").getAsString() : "";
                String exactName = obj.has("name") ? obj.get("name").getAsString() : clean;
                String uuid = normalizeUuid(rawId);
                String skinUrl = fetchSkinUrl(rawId);
                return new MojangProfile(uuid, exactName, skinUrl, true);
            }
        } catch (Exception ignored) {
        }

        String offlineUuid = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + clean).getBytes(StandardCharsets.UTF_8)
        ).toString();
        return new MojangProfile(offlineUuid, clean, "", false);
    }

    public static String fetchSkinUrl(String uuidNoDashesOrDashed) {
        String compact = uuidNoDashesOrDashed == null ? "" : uuidNoDashesOrDashed.replace("-", "").trim();
        if (compact.length() != 32) {
            return "";
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + compact))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return "";
            }
            JsonObject profile = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonArray props = profile.getAsJsonArray("properties");
            if (props == null) return "";
            for (JsonElement el : props) {
                JsonObject p = el.getAsJsonObject();
                if ("textures".equals(p.get("name").getAsString())) {
                    String decoded = new String(
                            Base64.getDecoder().decode(p.get("value").getAsString()),
                            StandardCharsets.UTF_8
                    );
                    JsonObject tex = JsonParser.parseString(decoded).getAsJsonObject();
                    JsonObject textures = tex.getAsJsonObject("textures");
                    if (textures != null && textures.has("SKIN")) {
                        return textures.getAsJsonObject("SKIN").get("url").getAsString();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public static String[] parseHostPort(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        if (trimmed.startsWith("tcp://")) trimmed = trimmed.substring(6);
        int idx = trimmed.lastIndexOf(':');
        if (idx > 0 && idx < trimmed.length() - 1) {
            String host = trimmed.substring(0, idx).trim();
            String portStr = trimmed.substring(idx + 1).trim();
            try {
                int p = Integer.parseInt(portStr);
                if (p > 0 && p <= 65535) {
                    return new String[]{host, String.valueOf(p)};
                }
            } catch (NumberFormatException ignored) {}
        }
        return new String[]{trimmed, String.valueOf(DEFAULT_RELAY_PORT)};
    }

    public static String normalizeUuid(String raw) {
        if (raw == null) return "";
        String hex = raw.replace("-", "").trim().toLowerCase(Locale.ROOT);
        if (hex.length() != 32) return raw.trim().toLowerCase(Locale.ROOT);
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
    }
}
