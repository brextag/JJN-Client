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
