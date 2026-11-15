package com.jjn.launcher.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jjn.launcher.model.Instance;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Searches and downloads Minecraft mods from both Modrinth (official v2 API)
 * and CurseForge (public curse.tools v1 mirror) directly into an instance's mods folder.
 */
public class ModCatalogService {

    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    private static final String CURSEFORGE_API = "https://api.curse.tools/v1";

    public static final String SOURCE_ALL = "All";
    public static final String SOURCE_MODRINTH = "Modrinth";
    public static final String SOURCE_CURSEFORGE = "CurseForge";

    public record ModSearchResult(
            String source,
            String projectId,
            String slug,
            String name,
            String author,
            String description,
            String iconUrl,
            long downloads,
            String pageUrl
    ) {}

    public record SearchResponse(
            List<ModSearchResult> results,
            String warning
    ) {}

    public record InstallOutcome(
            String primaryFileName,
            String resolvedVersion,
            List<String> installedFiles
    ) {}

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public SearchResponse search(
            String query,
            String sourceFilter,
            String mcVersion,
            String loaderType
    ) {
        String cleanQuery = query == null ? "" : query.trim();
        String loader = normalizeLoader(loaderType);

        boolean queryModrinth = SOURCE_ALL.equalsIgnoreCase(sourceFilter)
                || SOURCE_MODRINTH.equalsIgnoreCase(sourceFilter);
        boolean queryCurseForge = SOURCE_ALL.equalsIgnoreCase(sourceFilter)
                || SOURCE_CURSEFORGE.equalsIgnoreCase(sourceFilter);

        List<ModSearchResult> combined = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        CompletableFuture<List<ModSearchResult>> mrFuture = queryModrinth
                ? CompletableFuture.supplyAsync(() -> {
            try {
                return searchModrinth(cleanQuery, mcVersion, loader);
            } catch (Exception ex) {
                errors.add("Modrinth: " + ex.getMessage());
                return List.of();
            }
        })
                : CompletableFuture.completedFuture(List.of());

        CompletableFuture<List<ModSearchResult>> cfFuture = queryCurseForge
                ? CompletableFuture.supplyAsync(() -> {
            try {
                return searchCurseForge(cleanQuery, mcVersion, loader);
            } catch (Exception ex) {
                errors.add("CurseForge: " + ex.getMessage());
                return List.of();
            }
        })
                : CompletableFuture.completedFuture(List.of());

        combined.addAll(mrFuture.join());
        combined.addAll(cfFuture.join());

        combined.sort(Comparator.comparingLong(ModSearchResult::downloads).reversed());

        String warning = errors.isEmpty() ? null : String.join(" | ", errors);
        return new SearchResponse(combined, warning);
    }

    private List<ModSearchResult> searchModrinth(String query, String mcVersion, String loader) throws Exception {
        List<List<String>> facets = new ArrayList<>();
        facets.add(List.of("project_type:mod"));
        if (!loader.isEmpty()) {
            facets.add(List.of("categories:" + loader));
        }
        if (mcVersion != null && !mcVersion.isBlank()) {
            facets.add(List.of("versions:" + mcVersion));
        }

        StringBuilder facetsJson = new StringBuilder("[");
        for (int i = 0; i < facets.size(); i++) {
            if (i > 0) facetsJson.append(',');
            facetsJson.append("[\"").append(facets.get(i).get(0)).append("\"]");
        }
        facetsJson.append(']');

        String url = MODRINTH_API + "/search?limit=24&index=downloads"
                + "&query=" + enc(query)
                + "&facets=" + enc(facetsJson.toString());

        JsonObject root = getJson(url);
        JsonArray hits = root.getAsJsonArray("hits");
        if (hits == null) {
            return List.of();
        }

        List<ModSearchResult> list = new ArrayList<>();
        for (JsonElement elem : hits) {
            JsonObject obj = elem.getAsJsonObject();
            String id = getStr(obj, "project_id");
            String slug = getStr(obj, "slug");
            String title = getStr(obj, "title");
            String author = getStr(obj, "author");
            String desc = getStr(obj, "description");
            String icon = getStr(obj, "icon_url");
            long downloads = obj.has("downloads") && !obj.get("downloads").isJsonNull()
                    ? obj.get("downloads").getAsLong()
                    : 0L;
            String pageUrl = "https://modrinth.com/mod/" + (slug != null ? slug : id);
            list.add(new ModSearchResult(
                    SOURCE_MODRINTH, id, slug, title, author, desc, icon, downloads, pageUrl
            ));
        }
        return list;
    }

    private List<ModSearchResult> searchCurseForge(String query, String mcVersion, String loader) throws Exception {
        StringBuilder url = new StringBuilder(CURSEFORGE_API)
                .append("/mods/search?gameId=432&classId=6&sortField=2&sortOrder=desc&pageSize=24");
        if (!query.isEmpty()) {
            url.append("&searchFilter=").append(enc(query));
        }
        if (mcVersion != null && !mcVersion.isBlank()) {
            url.append("&gameVersion=").append(enc(mcVersion));
        }
        int loaderId = curseForgeLoaderId(loader);
        if (loaderId > 0) {
            url.append("&modLoaderType=").append(loaderId);
        }

        JsonObject root = getJson(url.toString());
        JsonArray data = root.getAsJsonArray("data");
        if (data == null) {
            return List.of();
        }

        List<ModSearchResult> list = new ArrayList<>();
        for (JsonElement elem : data) {
            JsonObject obj = elem.getAsJsonObject();
            String id = obj.has("id") ? String.valueOf(obj.get("id").getAsLong()) : "";
            String slug = getStr(obj, "slug");
            String name = getStr(obj, "name");
            String desc = getStr(obj, "summary");
            long downloads = obj.has("downloadCount") && !obj.get("downloadCount").isJsonNull()
                    ? (long) obj.get("downloadCount").getAsDouble()
                    : 0L;

            String author = "CurseForge Author";
            if (obj.has("authors") && obj.getAsJsonArray("authors").size() > 0) {
                author = getStr(obj.getAsJsonArray("authors").get(0).getAsJsonObject(), "name");
            }

            String icon = null;
            if (obj.has("logo") && obj.get("logo").isJsonObject()) {
                JsonObject logo = obj.getAsJsonObject("logo");
                icon = getStr(logo, "thumbnailUrl");
                if (icon == null) {
                    icon = getStr(logo, "url");
                }
            }

            String pageUrl = "https://www.curseforge.com/minecraft/mc-mods/" + slug;
            if (obj.has("links") && obj.get("links").isJsonObject()) {
                String link = getStr(obj.getAsJsonObject("links"), "websiteUrl");
                if (link != null) {
                    pageUrl = link;
                }
            }

            list.add(new ModSearchResult(
                    SOURCE_CURSEFORGE, id, slug, name, author, desc, icon, downloads, pageUrl
            ));
        }
        return list;
    }

    public InstallOutcome installMod(
            ModSearchResult mod,
            String mcVersion,
            String loaderType,
            Path modsFolder,
            Consumer<String> status
    ) throws Exception {
        Files.createDirectories(modsFolder);
        String loader = normalizeLoader(loaderType);

        if (SOURCE_MODRINTH.equalsIgnoreCase(mod.source())) {
            return installFromModrinth(mod, mcVersion, loader, modsFolder, status);
        }
        return installFromCurseForge(mod, mcVersion, loader, modsFolder, status);
    }

    private InstallOutcome installFromModrinth(
            ModSearchResult mod,
            String mcVersion,
            String loader,
            Path modsFolder,
            Consumer<String> status
    ) throws Exception {
        List<String> installedFiles = new ArrayList<>();
        Set<String> visitedProjects = new HashSet<>();
        visitedProjects.add(mod.projectId());

        if (status != null) {
            status.accept("Resolving Modrinth file for " + mod.name() + "...");
        }

        JsonObject version = fetchModrinthBestVersion(mod.projectId(), mcVersion, loader);
        if (version == null) {
            throw new Exception(
                    "No Modrinth release found for " + mod.name()
                            + (mcVersion != null && !mcVersion.isBlank() ? " on MC " + mcVersion : "")
                            + (!loader.isEmpty() ? " (" + loader + ")" : "") + "."
            );
        }

        String resolvedVersion = getStr(version, "version_number");
        String primaryName = downloadModrinthVersionFile(version, modsFolder, status);
        installedFiles.add(primaryName);

        // Install direct required dependencies (e.g. Fabric API, Architectury, Cloth Config)
        if (version.has("dependencies") && version.get("dependencies").isJsonArray()) {
            int depCount = 0;
            for (JsonElement depEl : version.getAsJsonArray("dependencies")) {
                if (depCount >= 4) break;
                JsonObject dep = depEl.getAsJsonObject();
                String depType = getStr(dep, "dependency_type");
                String depProjectId = getStr(dep, "project_id");
                if (!"required".equalsIgnoreCase(depType)
                        || depProjectId == null
                        || !visitedProjects.add(depProjectId)) {
                    continue;
                }
                try {
                    JsonObject depVer = fetchModrinthBestVersion(depProjectId, mcVersion, loader);
                    if (depVer != null) {
                        String depFile = downloadModrinthVersionFile(depVer, modsFolder, status);
                        if (depFile != null) {
                            installedFiles.add(depFile);
                            depCount++;
                        }
                    }
                } catch (Exception ignored) {
                    // Best-effort dependency resolution; primary mod is already installed.
                }
            }
        }

        return new InstallOutcome(primaryName, resolvedVersion, installedFiles);
    }

    private JsonObject fetchModrinthBestVersion(String projectId, String mcVersion, String loader) throws Exception {
        StringBuilder url = new StringBuilder(MODRINTH_API)
                .append("/project/").append(enc(projectId)).append("/version");
        List<String> params = new ArrayList<>();
        if (!loader.isEmpty()) {
            params.add("loaders=" + enc("[\"" + loader + "\"]"));
        }
        if (mcVersion != null && !mcVersion.isBlank()) {
            params.add("game_versions=" + enc("[\"" + mcVersion + "\"]"));
        }
        if (!params.isEmpty()) {
            url.append("?").append(String.join("&", params));
        }

        JsonArray versions = getJsonArray(url.toString());
        if (versions.isEmpty()) {
            return null;
        }

        for (JsonElement elem : versions) {
            JsonObject v = elem.getAsJsonObject();
            if ("release".equalsIgnoreCase(getStr(v, "version_type"))) {
                return v;
            }
        }
        return versions.get(0).getAsJsonObject();
    }

    private String downloadModrinthVersionFile(
            JsonObject version,
            Path modsFolder,
            Consumer<String> status
    ) throws Exception {
        JsonArray files = version.getAsJsonArray("files");
        if (files == null || files.isEmpty()) {
            throw new Exception("Mod version contains no downloadable files.");
        }

        JsonObject chosen = files.get(0).getAsJsonObject();
        for (JsonElement el : files) {
            JsonObject f = el.getAsJsonObject();
            if (f.has("primary") && f.get("primary").getAsBoolean()) {
                chosen = f;
                break;
            }
        }

        String fileName = sanitizeFileName(getStr(chosen, "filename"));
        String downloadUrl = getStr(chosen, "url");
        if (fileName == null || downloadUrl == null) {
            throw new Exception("Modrinth file metadata missing URL or filename.");
        }

        String sha1 = null;
        if (chosen.has("hashes") && chosen.get("hashes").isJsonObject()) {
            sha1 = getStr(chosen.getAsJsonObject("hashes"), "sha1");
        }

        Path dest = modsFolder.resolve(fileName);
        if (Files.isRegularFile(dest) && sha1 != null && sha1(dest).equalsIgnoreCase(sha1)) {
            return fileName;
        }

        if (status != null) {
            status.accept("Downloading " + fileName + "...");
        }
        downloadFile(downloadUrl, dest, sha1);
        return fileName;
    }

    private InstallOutcome installFromCurseForge(
            ModSearchResult mod,
            String mcVersion,
            String loader,
            Path modsFolder,
            Consumer<String> status
    ) throws Exception {
        if (status != null) {
            status.accept("Resolving CurseForge file for " + mod.name() + "...");
        }

        StringBuilder url = new StringBuilder(CURSEFORGE_API)
                .append("/mods/").append(enc(mod.projectId())).append("/files?pageSize=25");
        if (mcVersion != null && !mcVersion.isBlank()) {
            url.append("&gameVersion=").append(enc(mcVersion));
        }
        int loaderId = curseForgeLoaderId(loader);
        if (loaderId > 0) {
            url.append("&modLoaderType=").append(loaderId);
        }

        JsonObject root = getJson(url.toString());
        JsonArray data = root.getAsJsonArray("data");

        if ((data == null || data.isEmpty()) && loaderId > 0 && mcVersion != null && !mcVersion.isBlank()) {
            // Retry without loader filter and check gameVersions tags client-side
            String fallbackUrl = CURSEFORGE_API + "/mods/" + enc(mod.projectId())
                    + "/files?pageSize=25&gameVersion=" + enc(mcVersion);
            JsonObject fallbackRoot = getJson(fallbackUrl);
            data = fallbackRoot.getAsJsonArray("data");
        }

        if (data == null || data.isEmpty()) {
            throw new Exception(
                    "No CurseForge file found for " + mod.name()
                            + (mcVersion != null && !mcVersion.isBlank() ? " on MC " + mcVersion : "")
                            + (!loader.isEmpty() ? " (" + loader + ")" : "") + "."
            );
        }

        JsonObject chosen = pickBestCurseForgeFile(data, loader);
        if (chosen == null) {
            throw new Exception(
                    "No CurseForge " + (loader.isEmpty() ? "" : loader + " ")
                            + "compatible .jar found for " + mod.name() + "."
            );
        }

        String fileName = sanitizeFileName(getStr(chosen, "fileName"));
        String downloadUrl = getStr(chosen, "downloadUrl");
        if ((downloadUrl == null || downloadUrl.isBlank()) && chosen.has("id") && fileName != null) {
            long fileId = chosen.get("id").getAsLong();
            long high = fileId / 1000;
            long low = fileId % 1000;
            downloadUrl = "https://edge.forgecdn.net/files/" + high + "/" + low + "/" + enc(fileName).replace("+", "%20");
        }

        if (downloadUrl == null || downloadUrl.isBlank() || fileName == null) {
            throw new Exception(
                    "This CurseForge mod restricts direct downloads. Open its page to download:\n" + mod.pageUrl()
            );
        }

        String sha1 = null;
        if (chosen.has("hashes") && chosen.get("hashes").isJsonArray()) {
            for (JsonElement h : chosen.getAsJsonArray("hashes")) {
                JsonObject hashObj = h.getAsJsonObject();
                if (hashObj.has("algo") && hashObj.get("algo").getAsInt() == 1) {
                    sha1 = getStr(hashObj, "value");
                    break;
                }
            }
        }

        if (status != null) {
            status.accept("Downloading " + fileName + "...");
        }
        try {
            downloadFile(downloadUrl, modsFolder.resolve(fileName), sha1);
        } catch (Exception ex) {
            throw new Exception(
                    "Could not download " + fileName + " (" + ex.getMessage()
                            + "). You can download it manually at: " + mod.pageUrl()
            );
        }

        String displayVersion = getStr(chosen, "displayName");
        return new InstallOutcome(fileName, displayVersion == null ? fileName : displayVersion, List.of(fileName));
    }

    private static JsonObject pickBestCurseForgeFile(JsonArray data, String loader) {
        JsonObject fallbackJar = null;
        for (JsonElement elem : data) {
            JsonObject f = elem.getAsJsonObject();
            String fname = getStr(f, "fileName");
            if (fname == null || !fname.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                continue;
            }
            if (fallbackJar == null) {
                fallbackJar = f;
            }
            if (loader.isEmpty()) {
                return f;
            }
            if (f.has("gameVersions") && f.get("gameVersions").isJsonArray()) {
                for (JsonElement gv : f.getAsJsonArray("gameVersions")) {
                    if (gv.getAsString().equalsIgnoreCase(loader)) {
                        return f;
                    }
                }
            }
        }
        return loader.isEmpty() ? fallbackJar : null;
    }

    private void downloadFile(String url, Path dest, String expectedSha1) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "JJNClient/1.0")
                .timeout(Duration.ofSeconds(45))
                .GET()
                .build();

        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            res.body().close();
            throw new Exception("HTTP " + res.statusCode());
        }

        Path temp = dest.resolveSibling(dest.getFileName() + ".part");
        try (InputStream in = res.body()) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }

        if (expectedSha1 != null && !expectedSha1.isBlank()) {
            if (!sha1(temp).equalsIgnoreCase(expectedSha1)) {
                Files.deleteIfExists(temp);
                throw new Exception("SHA-1 verification failed");
            }
        }

        Files.move(temp, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    private JsonObject getJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "JJNClient/1.0")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new Exception("HTTP " + res.statusCode());
        }
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private JsonArray getJsonArray(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "JJNClient/1.0")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new Exception("HTTP " + res.statusCode());
        }
        return JsonParser.parseString(res.body()).getAsJsonArray();
    }

    private static String normalizeLoader(String loaderType) {
        if (loaderType == null || loaderType.isBlank()
                || Instance.LOADER_VANILLA.equalsIgnoreCase(loaderType)) {
            return "";
        }
        return loaderType.trim().toLowerCase(Locale.ROOT);
    }

    private static int curseForgeLoaderId(String loader) {
        return switch (loader) {
            case "forge" -> 1;
            case "fabric" -> 4;
            case "quilt" -> 5;
            case "neoforge" -> 6;
            default -> 0;
        };
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return Path.of(name).getFileName().toString().replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String getStr(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private static String enc(String text) {
        return URLEncoder.encode(text == null ? "" : text, StandardCharsets.UTF_8);
    }

    private static String sha1(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                digest.update(buf, 0, r);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String formatDownloads(long count) {
        if (count >= 1_000_000) {
            return String.format(Locale.US, "%.1fM downloads", count / 1_000_000.0);
        }
        if (count >= 1_000) {
            return String.format(Locale.US, "%.1fK downloads", count / 1_000.0);
        }
        return count + " downloads";
    }
}
