package com.jjn.launcher;

import com.google.gson.*;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs vanilla Minecraft into the shared cache (~/.jjnclient/shared) and
 * extracts Windows natives into the target instance's game folder.
 * Concurrent downloads dramatically speed up installation.
 */
public class MinecraftInstallService {

    private static final String MANIFEST =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private static final String ASSETS =
            "https://resources.download.minecraft.net/";

    private static final int DOWNLOAD_THREADS = 10;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    /** Called from the installing thread. progress is 0.0 to 1.0. */
    public interface ProgressListener {
        void update(String message, double progress);
    }

    public record DownloadItem(
            String url,
            Path destination,
            String sha1,
            long size
    ) {}

    // VERSION LIST

    public List<String> getReleaseVersions() throws Exception {

        JsonObject manifest = getJson(MANIFEST);
        List<String> releases = new ArrayList<>();

        for (JsonElement element : manifest.getAsJsonArray("versions")) {
            JsonObject version = element.getAsJsonObject();

            if ("release".equals(version.get("type").getAsString())) {
                releases.add(version.get("id").getAsString());
            }
        }

        return releases;
    }

    // INSTALL

    public void install(String selectedVersion,
                        Path gameFolder,
                        ProgressListener listener) throws Exception {
        install(selectedVersion, gameFolder, LauncherSettings.sharedDir(), listener);
    }

    public void install(String selectedVersion,
                        Path gameFolder,
                        Path sharedFolder,
                        ProgressListener listener) throws Exception {

        listener.update("Downloading version information...", 0);

        JsonObject manifest = getJson(MANIFEST);
        JsonObject versionEntry = null;

        for (JsonElement element : manifest.getAsJsonArray("versions")) {

            JsonObject version = element.getAsJsonObject();

            if (version.get("id").getAsString().equals(selectedVersion)) {
                versionEntry = version;
                break;
            }
        }

        if (versionEntry == null) {
            throw new Exception("Minecraft version not found: " + selectedVersion);
        }

        JsonObject versionJson = getJson(versionEntry.get("url").getAsString());

        Path versionFolder =
                sharedFolder.resolve("versions").resolve(selectedVersion);

        Files.createDirectories(versionFolder);

        Files.writeString(
                versionFolder.resolve(selectedVersion + ".json"),
                new GsonBuilder().setPrettyPrinting()
                        .create().toJson(versionJson)
        );

        List<DownloadItem> downloads = new ArrayList<>();
        List<Path> nativeJars = new ArrayList<>();

        // CLIENT JAR

        JsonObject downloadsJson = versionJson.getAsJsonObject("downloads");

        if (downloadsJson == null || !downloadsJson.has("client")) {
            throw new Exception("Client download is missing.");
        }

        JsonObject clientInfo = downloadsJson.getAsJsonObject("client");

        downloads.add(new DownloadItem(
                clientInfo.get("url").getAsString(),
                versionFolder.resolve(selectedVersion + ".jar"),
                getString(clientInfo, "sha1"),
                getLong(clientInfo, "size")
        ));

        // LIBRARIES

        Path librariesFolder = sharedFolder.resolve("libraries");
        JsonArray libraries = versionJson.getAsJsonArray("libraries");

        if (libraries != null) {
            for (JsonElement element : libraries) {

                JsonObject library = element.getAsJsonObject();

                if (!isAllowed(library)) {
                    continue;
                }

                JsonObject libraryDownloads =
                        library.getAsJsonObject("downloads");

                if (libraryDownloads == null) {
                    continue;
                }

                if (libraryDownloads.has("artifact")) {

                    JsonObject artifact =
                            libraryDownloads.getAsJsonObject("artifact");

                    Path destination = safeResolve(
                            librariesFolder,
                            artifact.get("path").getAsString()
                    );

                    downloads.add(new DownloadItem(
                            artifact.get("url").getAsString(),
                            destination,
                            getString(artifact, "sha1"),
                            getLong(artifact, "size")
                    ));
                }

                // WINDOWS NATIVE LIBRARIES

                JsonObject natives = library.getAsJsonObject("natives");

                if (natives != null && natives.has("windows")) {

                    String classifier = natives
                            .get("windows").getAsString()
                            .replace("${arch}",
                                    System.getProperty("os.arch")
                                            .contains("64") ? "64" : "32");

                    JsonObject classifiers =
                            libraryDownloads.getAsJsonObject("classifiers");

                    if (classifiers != null && classifiers.has(classifier)) {

                        JsonObject nativeInfo =
                                classifiers.getAsJsonObject(classifier);

                        Path nativePath = safeResolve(
                                librariesFolder,
                                nativeInfo.get("path").getAsString()
                        );

                        downloads.add(new DownloadItem(
                                nativeInfo.get("url").getAsString(),
                                nativePath,
                                getString(nativeInfo, "sha1"),
                                getLong(nativeInfo, "size")
                        ));

                        nativeJars.add(nativePath);
                    }
                }
            }
        }

        // ASSET INDEX AND ASSETS

        Path assetsFolder = sharedFolder.resolve("assets");
        JsonObject assetIndex = versionJson.getAsJsonObject("assetIndex");

        if (assetIndex != null) {

            String indexId = assetIndex.get("id").getAsString();

            Path indexesFolder = assetsFolder.resolve("indexes");
            Files.createDirectories(indexesFolder);

            downloads.add(new DownloadItem(
                    assetIndex.get("url").getAsString(),
                    indexesFolder.resolve(indexId + ".json"),
                    getString(assetIndex, "sha1"),
                    getLong(assetIndex, "size")
            ));

            JsonObject indexJson = getJson(assetIndex.get("url").getAsString());
            JsonObject objects = indexJson.getAsJsonObject("objects");

            for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {

                JsonObject object = entry.getValue().getAsJsonObject();
                String hash = object.get("hash").getAsString();

                Path objectPath = assetsFolder
                        .resolve("objects")
                        .resolve(hash.substring(0, 2))
                        .resolve(hash);

                downloads.add(new DownloadItem(
                        ASSETS + hash.substring(0, 2) + "/" + hash,
                        objectPath,
                        hash,
                        getLong(object, "size")
                ));
            }
        }

        // LOGGING CONFIGURATION

        JsonObject logging = versionJson.getAsJsonObject("logging");

        if (logging != null && logging.has("client")) {

            JsonObject file =
                    logging.getAsJsonObject("client").getAsJsonObject("file");

            if (file != null) {

                String fileId = file.get("id").getAsString();

                downloads.add(new DownloadItem(
                        file.get("url").getAsString(),
                        assetsFolder
                                .resolve("log_configs")
                                .resolve(fileId),
                        getString(file, "sha1"),
                        getLong(file, "size")
                ));
            }
        }

        // DOWNLOAD EVERYTHING CONCURRENTLY

        downloadAll(downloads, listener);

        // EXTRACT WINDOWS NATIVES INTO INSTANCE

        listener.update("Extracting Windows native libraries...", 0.98);

        Path nativesFolder = gameFolder.resolve("natives");
        Files.createDirectories(nativesFolder);

        for (Path nativeJar : nativeJars) {
            extractNative(nativeJar, nativesFolder);
        }

        listener.update("Minecraft " + selectedVersion + " installed!", 1.0);
    }

    public void downloadAll(List<DownloadItem> items, ProgressListener listener) throws Exception {
        if (items.isEmpty()) {
            return;
        }

        // Deduplicate by destination path
        Map<Path, DownloadItem> unique = new LinkedHashMap<>();
        for (DownloadItem item : items) {
            unique.putIfAbsent(item.destination().normalize(), item);
        }

        List<DownloadItem> queue = new ArrayList<>(unique.values());
        int total = queue.size();
        AtomicInteger completed = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(DOWNLOAD_THREADS, Math.max(1, total))
        );

        try {
            List<Future<?>> futures = new ArrayList<>(total);
            for (DownloadItem item : queue) {
                futures.add(pool.submit(() -> {
                    downloadFile(item);
                    int done = completed.incrementAndGet();
                    if (listener != null) {
                        listener.update(
                                "Downloading " + done + " of " + total + " files...",
                                (double) done / total * 0.96
                        );
                    }
                    return null;
                }));
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException ex) {
                    pool.shutdownNow();
                    Throwable cause = ex.getCause();
                    if (cause instanceof Exception e) {
                        throw e;
                    }
                    throw new Exception(cause);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // DOWNLOAD HELPERS

    public JsonObject getJson(String url) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "JJNClient/1.0")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new Exception(
                    "HTTP " + response.statusCode() + " while loading metadata."
            );
        }

        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    public JsonArray getJsonArray(String url) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "JJNClient/1.0")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new Exception(
                    "HTTP " + response.statusCode() + " while loading metadata."
            );
        }

        return JsonParser.parseString(response.body()).getAsJsonArray();
    }

    private void downloadFile(DownloadItem item) throws Exception {

        Files.createDirectories(item.destination().getParent());

        if (Files.exists(item.destination())) {

            boolean valid = true;

            if (item.sha1() != null && !item.sha1().isBlank()) {
                valid = sha1(item.destination()).equalsIgnoreCase(item.sha1());
            } else if (item.size() > 0) {
                valid = Files.size(item.destination()) == item.size();
            }

            if (valid) {
                return;
            }
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(item.url()))
                .header("User-Agent", "JJNClient/1.0")
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );

        if (response.statusCode() != 200) {
            response.body().close();
            throw new Exception(
                    "Download failed (" + item.url() + "): HTTP " + response.statusCode()
            );
        }

        Path temporary = item.destination().resolveSibling(
                item.destination().getFileName() + "." + Thread.currentThread().threadId() + ".part"
        );

        try (InputStream input = response.body()) {
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
        }

        if (item.size() > 0 && Files.size(temporary) != item.size()) {
            Files.deleteIfExists(temporary);
            throw new Exception("Downloaded file has an incorrect size.");
        }

        if (item.sha1() != null && !item.sha1().isBlank()) {
            if (!sha1(temporary).equalsIgnoreCase(item.sha1())) {
                Files.deleteIfExists(temporary);
                throw new Exception("Downloaded file failed SHA-1 verification.");
            }
        }

        Files.move(
                temporary,
                item.destination(),
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    private String sha1(Path file) throws Exception {

        MessageDigest digest = MessageDigest.getInstance("SHA-1");

        try (InputStream input = Files.newInputStream(file)) {

            byte[] buffer = new byte[8192];
            int read;

            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    // WINDOWS LIBRARY RULES

    private boolean isAllowed(JsonObject library) {

        if (!library.has("rules")) {
            return true;
        }

        boolean allowed = false;

        for (JsonElement element : library.getAsJsonArray("rules")) {

            JsonObject rule = element.getAsJsonObject();

            if (!matchesWindows(rule)) {
                continue;
            }

            allowed = "allow".equals(rule.get("action").getAsString());
        }

        return allowed;
    }

    private boolean matchesWindows(JsonObject rule) {

        if (!rule.has("os")) {
            return true;
        }

        JsonObject os = rule.getAsJsonObject("os");

        if (os.has("name") &&
                !"windows".equals(os.get("name").getAsString())) {
            return false;
        }

        if (os.has("arch")) {
            String arch = os.get("arch").getAsString();

            if (!System.getProperty("os.arch").matches(arch)) {
                return false;
            }
        }

        if (os.has("version")) {
            String regex = os.get("version").getAsString();

            if (!System.getProperty("os.version").matches(regex)) {
                return false;
            }
        }

        return true;
    }

    // NATIVE EXTRACTION

    private void extractNative(Path jar, Path destination) throws Exception {

        try (ZipInputStream zip = new ZipInputStream(
                Files.newInputStream(jar))) {

            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {

                if (entry.isDirectory() ||
                        entry.getName().startsWith("META-INF/")) {
                    continue;
                }

                Path output = destination.resolve(entry.getName()).normalize();

                if (!output.startsWith(destination.normalize())) {
                    throw new Exception("Unsafe native archive entry.");
                }

                Files.createDirectories(output.getParent());

                Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);

                zip.closeEntry();
            }
        }
    }

    // SAFE PATH + JSON HELPERS

    public static Path safeResolve(Path root, String relative) throws Exception {

        Path result = root.resolve(relative).normalize();

        if (!result.startsWith(root.normalize())) {
            throw new Exception("Unsafe download path.");
        }

        return result;
    }

    private String getString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString()
                : null;
    }

    private long getLong(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsLong()
                : 0;
    }
}