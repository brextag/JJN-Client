package com.jjn.launcher.service;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jjn.launcher.LauncherSettings;
import com.jjn.launcher.MinecraftInstallService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Installs Fabric Loader on top of an installed vanilla Minecraft version
 * using Fabric's official Meta v2 API.
 */
public class FabricInstallService {

    private static final String FABRIC_META = "https://meta.fabricmc.net/v2";

    public record FabricInstallResult(String versionId, String loaderVersion) {}

    private final MinecraftInstallService minecraftInstaller;

    public FabricInstallService(MinecraftInstallService minecraftInstaller) {
        this.minecraftInstaller = minecraftInstaller;
    }

    public String getLatestLoaderVersion(String mcVersion) throws Exception {
        JsonArray loaders = minecraftInstaller.getJsonArray(
                FABRIC_META + "/versions/loader/" + mcVersion
        );

        if (loaders.isEmpty()) {
            throw new Exception("Fabric does not support Minecraft " + mcVersion + " yet.");
        }

        String fallback = null;
        for (JsonElement element : loaders) {
            JsonObject entry = element.getAsJsonObject();
            JsonObject loader = entry.getAsJsonObject("loader");
            if (loader == null || !loader.has("version")) {
                continue;
            }
            String version = loader.get("version").getAsString();
            if (fallback == null) {
                fallback = version;
            }
            if (loader.has("stable") && loader.get("stable").getAsBoolean()) {
                return version;
            }
        }

        if (fallback == null) {
            throw new Exception("No Fabric loader version found for " + mcVersion + ".");
        }
        return fallback;
    }

    public FabricInstallResult install(
            String mcVersion,
            Path gameFolder,
            MinecraftInstallService.ProgressListener listener
    ) throws Exception {

        // 1. Install base vanilla Minecraft first
        minecraftInstaller.install(mcVersion, gameFolder, (msg, prog) -> {
            if (listener != null) {
                listener.update(msg, prog * 0.80);
            }
        });

        Path sharedFolder = LauncherSettings.sharedDir();

        if (listener != null) {
            listener.update("Resolving Fabric Loader for " + mcVersion + "...", 0.82);
        }

        String loaderVersion = getLatestLoaderVersion(mcVersion);
        String profileUrl = FABRIC_META + "/versions/loader/" + mcVersion + "/" + loaderVersion + "/profile/json";
        JsonObject profileJson = minecraftInstaller.getJson(profileUrl);

        String profileId = profileJson.has("id")
                ? profileJson.get("id").getAsString()
                : "fabric-loader-" + loaderVersion + "-" + mcVersion;

        Path versionFolder = sharedFolder.resolve("versions").resolve(profileId);
        Files.createDirectories(versionFolder);
        Files.writeString(
                versionFolder.resolve(profileId + ".json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(profileJson)
        );

        // 2. Download Fabric Maven libraries
        List<MinecraftInstallService.DownloadItem> downloads = new ArrayList<>();
        Path librariesFolder = sharedFolder.resolve("libraries");

        if (profileJson.has("libraries") && profileJson.get("libraries").isJsonArray()) {
            for (JsonElement element : profileJson.getAsJsonArray("libraries")) {
                JsonObject lib = element.getAsJsonObject();
                if (!lib.has("name")) {
                    continue;
                }

                String mavenName = lib.get("name").getAsString();
                String relativePath = mavenCoordinateToPath(mavenName);
                String baseUrl = lib.has("url") && !lib.get("url").isJsonNull()
                        ? lib.get("url").getAsString()
                        : "https://repo1.maven.org/maven2/";

                if (!baseUrl.endsWith("/")) {
                    baseUrl += "/";
                }

                String sha1 = lib.has("sha1") && !lib.get("sha1").isJsonNull()
                        ? lib.get("sha1").getAsString()
                        : null;
                long size = lib.has("size") && !lib.get("size").isJsonNull()
                        ? lib.get("size").getAsLong()
                        : 0L;

                Path dest = MinecraftInstallService.safeResolve(librariesFolder, relativePath);
                downloads.add(new MinecraftInstallService.DownloadItem(
                        baseUrl + relativePath,
                        dest,
                        sha1,
                        size
                ));
            }
        }

        minecraftInstaller.downloadAll(downloads, (msg, prog) -> {
            if (listener != null) {
                listener.update("Installing Fabric libraries... " + msg, 0.84 + prog * 0.16);
            }
        });

        Files.createDirectories(gameFolder.resolve("mods"));
        ClientModBundler.ensureInstalled(mcVersion, gameFolder);

        if (listener != null) {
            listener.update("Minecraft " + mcVersion + " (Fabric " + loaderVersion + ") installed!", 1.0);
        }

        return new FabricInstallResult(profileId, loaderVersion);
    }

    /**
     * Converts "group:artifact:version[:classifier]" into a Maven repository relative path.
     */
    public static String mavenCoordinateToPath(String coordinate) {
        String ext = "jar";
        String clean = coordinate;
        int atIndex = clean.indexOf('@');
        if (atIndex >= 0) {
            ext = clean.substring(atIndex + 1);
            clean = clean.substring(0, atIndex);
        }

        String[] parts = clean.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid Maven coordinate: " + coordinate);
        }

        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length >= 4 ? "-" + parts[3] : "";

        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + "." + ext;
    }
}
