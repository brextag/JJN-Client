package com.jjn.launcher.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jjn.launcher.LauncherSettings;
import javafx.scene.image.Image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Manages the local skin wardrobe (~/.jjnclient/skins) and official Minecraft Services
 * skin/cape API actions when signed in with Microsoft.
 */
public class MinecraftSkinService {

    private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final String SKINS_URL = "https://api.minecraftservices.com/minecraft/profile/skins";
    private static final String ACTIVE_SKIN_URL = "https://api.minecraftservices.com/minecraft/profile/skins/active";
    private static final String ACTIVE_CAPE_URL = "https://api.minecraftservices.com/minecraft/profile/capes/active";

    public record CapeInfo(String id, String alias, String url, boolean active) {}

    public record MojangCosmetics(
            String activeSkinUrl,
            String skinVariant,
            List<CapeInfo> capes
    ) {}

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public static Path skinsDir() {
        return LauncherSettings.ROOT_DIR.resolve("skins");
    }

    public static Path capesDir() {
        return LauncherSettings.ROOT_DIR.resolve("capes");
    }

    public List<Path> listSavedSkins() {
        List<Path> list = new ArrayList<>();
        Path dir = skinsDir();
        if (!Files.isDirectory(dir)) {
            return list;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.png")) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    list.add(p);
                }
            }
        } catch (IOException ignored) {
        }
        list.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));
        return list;
    }

    public Path importSkinFile(Path sourcePng) throws Exception {
        if (sourcePng == null || !Files.isRegularFile(sourcePng)) {
            throw new Exception("Selected skin file does not exist.");
        }
        try (var in = Files.newInputStream(sourcePng)) {
            Image img = new Image(in);
            if (img.isError()) {
                throw new Exception("File is not a valid PNG image.");
            }
            int w = (int) img.getWidth();
            int h = (int) img.getHeight();
            if (w != 64 || (h != 64 && h != 32)) {
                throw new Exception("Minecraft skins must be 64x64 or 64x32 PNG (got " + w + "x" + h + ").");
            }
        }
        Files.createDirectories(skinsDir());
        String cleanName = sourcePng.getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!cleanName.toLowerCase().endsWith(".png")) {
            cleanName += ".png";
        }
        Path dest = skinsDir().resolve(cleanName);
        Files.copy(sourcePng, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    public Path cacheCapeFile(String capeId, String capeUrl) throws Exception {
        if (capeId == null || capeId.isBlank() || capeUrl == null || capeUrl.isBlank()) {
            return null;
        }
        String cleanId = capeId.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path dest = capesDir().resolve(cleanId + ".png");
        if (Files.isRegularFile(dest) && Files.size(dest) > 64) {
            return dest;
        }
        Files.createDirectories(capesDir());
        String httpsUrl = capeUrl.replace("http://", "https://");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(httpsUrl))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() < 200 || res.statusCode() >= 300 || res.body() == null || res.body().length < 32) {
            throw new Exception("Could not download cape texture (HTTP " + res.statusCode() + ").");
        }
        Files.write(dest, res.body());
        return dest;
    }

    public MojangCosmetics fetchMojangCosmetics(String accessToken) throws Exception {
        requireToken(accessToken);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(PROFILE_URL))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 401) {
            throw new Exception("Minecraft session token expired (401). Sign out and sign in again.");
        }
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new Exception("Could not fetch Minecraft cosmetics (HTTP " + res.statusCode() + ").");
        }
        return parseCosmetics(res.body());
    }

    public MojangCosmetics uploadSkin(String accessToken, Path skinPng, String variant) throws Exception {
        requireToken(accessToken);
        if (skinPng == null || !Files.isRegularFile(skinPng)) {
            throw new Exception("Choose a local .png skin file first.");
        }
        String normVariant = "slim".equalsIgnoreCase(variant) ? "slim" : "classic";
        byte[] pngBytes = Files.readAllBytes(skinPng);

        String boundary = "----JJNBoundary" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeFormField(body, boundary, "variant", normVariant);
        writeFilePart(body, boundary, "file", skinPng.getFileName().toString(), pngBytes);
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(SKINS_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 401) {
            throw new Exception("Minecraft session token expired (401). Sign out and sign in again.");
        }
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new Exception("Mojang rejected skin upload (HTTP " + res.statusCode() + "): " + res.body());
        }
        return fetchMojangCosmetics(accessToken);
    }

    public void resetSkin(String accessToken) throws Exception {
        requireToken(accessToken);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ACTIVE_SKIN_URL))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .DELETE()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new Exception("Could not reset skin (HTTP " + res.statusCode() + ").");
        }
    }

    public MojangCosmetics setCape(String accessToken, String capeId) throws Exception {
        requireToken(accessToken);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json");

        boolean equipping = capeId != null && !capeId.isBlank();
        if (!equipping) {
            builder.uri(URI.create(ACTIVE_CAPE_URL)).DELETE();
        } else {
            JsonObject payload = new JsonObject();
            payload.addProperty("capeId", capeId);
            builder.uri(URI.create(ACTIVE_CAPE_URL))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(payload.toString()));
        }

        HttpResponse<String> res = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 401) {
            throw new Exception("Minecraft session token expired (401). Sign out and sign in again.");
        }
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new Exception("Could not update cape (HTTP " + res.statusCode() + "): " + res.body());
        }

        if (res.body() != null && !res.body().isBlank()) {
            try {
                MojangCosmetics updated = parseCosmetics(res.body());
                if (!updated.capes().isEmpty()) {
                    return updated;
                }
            } catch (Exception ignored) {
            }
        }
        return fetchMojangCosmetics(accessToken);
    }

    private static MojangCosmetics parseCosmetics(String jsonBody) {
        JsonObject profile = JsonParser.parseString(jsonBody).getAsJsonObject();
        String skinUrl = null;
        String variant = "classic";
        if (profile.has("skins") && profile.get("skins").isJsonArray()) {
            for (JsonElement el : profile.getAsJsonArray("skins")) {
                JsonObject skin = el.getAsJsonObject();
                if ("ACTIVE".equalsIgnoreCase(getStr(skin, "state"))) {
                    String u = getStr(skin, "url");
                    if (u != null) {
                        skinUrl = u.replace("http://", "https://");
                    }
                    String v = getStr(skin, "variant");
                    if ("SLIM".equalsIgnoreCase(v)) {
                        variant = "slim";
                    }
                    break;
                }
            }
        }

        List<CapeInfo> capes = new ArrayList<>();
        if (profile.has("capes") && profile.get("capes").isJsonArray()) {
            for (JsonElement el : profile.getAsJsonArray("capes")) {
                JsonObject c = el.getAsJsonObject();
                String id = getStr(c, "id");
                String alias = getStr(c, "alias");
                String url = getStr(c, "url");
                boolean active = "ACTIVE".equalsIgnoreCase(getStr(c, "state"));
                if (id != null) {
                    capes.add(new CapeInfo(
                            id,
                            alias == null ? "Cape" : alias,
                            url == null ? "" : url.replace("http://", "https://"),
                            active
                    ));
                }
            }
        }

        return new MojangCosmetics(skinUrl, variant, capes);
    }

    private static void requireToken(String accessToken) throws Exception {
        if (accessToken == null || accessToken.isBlank()) {
            throw new Exception("Sign in with a Microsoft account first.");
        }
    }

    private static void writeFormField(ByteArrayOutputStream out, String boundary, String name, String value)
            throws IOException {
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFilePart(
            ByteArrayOutputStream out,
            String boundary,
            String name,
            String filename,
            byte[] bytes
    ) throws IOException {
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: image/png\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String getStr(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }
}
