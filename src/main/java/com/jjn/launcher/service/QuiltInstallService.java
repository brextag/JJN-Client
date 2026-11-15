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
 * Installs Quilt Loader on top of an installed vanilla Minecraft version
 * using Quilt's official Meta v3 API.
 */
public class QuiltInstallService {

    private static final String QUILT_META = "https://meta.quiltmc.org/v3";

    public record QuiltInstallResult(String versionId, String loaderVersion) {}

    private final MinecraftInstallService minecraftInstaller;

    public QuiltInstallService(MinecraftInstallService minecraftInstaller) {
        this.minecraftInstaller = minecraftInstaller;
    }

    public String getLatestLoaderVersion(String mcVersion) throws Exception {
        JsonArray loaders = minecraftInstaller.getJsonArray(
                QUILT_META + "/versions/loader/" + mcVersion
        );

        if (loaders.isEmpty()) {
            throw new Exception("Quilt does not support Minecraft " + mcVersion + " yet.");
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
            if (!version.contains("-beta.") && !version.contains("-alpha.")) {
                return version;
            }
        }

        if (fallback == null) {
            throw new Exception("No Quilt loader version found for " + mcVersion + ".");
        }
        return fallback;
    }

    public QuiltInstallResult install(
            String mcVersion,
            Path gameFolder,
            MinecraftInstallService.ProgressListener listener
    ) throws Exception {

        minecraftInstaller.install(mcVersion, gameFolder, (msg, prog) -> {
            if (listener != null) {
                listener.update(msg, prog * 0.80);
            }
        });

        Path sharedFolder = LauncherSettings.sharedDir();

        if (listener != null) {
            listener.update("Resolving Quilt Loader for " + mcVersion + "...", 0.82);
        }

        String loaderVersion = getLatestLoaderVersion(mcVersion);
        String profileUrl = QUILT_META + "/versions/loader/" + mcVersion + "/" + loaderVersion + "/profile/json";
        JsonObject profileJson = minecraftInstaller.getJson(profileUrl);

        String profileId = profileJson.has("id")
                ? profileJson.get("id").getAsString()
                : "quilt-loader-" + loaderVersion + "-" + mcVersion;

        Path versionFolder = sharedFolder.resolve("versions").resolve(profileId);
        Files.createDirectories(versionFolder);
        Files.writeString(
                versionFolder.resolve(profileId + ".json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(profileJson)
        );

        List<MinecraftInstallService.DownloadItem> downloads = new ArrayList<>();
        Path librariesFolder = sharedFolder.resolve("libraries");

        if (profileJson.has("libraries") && profileJson.get("libraries").isJsonArray()) {
            for (JsonElement element : profileJson.getAsJsonArray("libraries")) {
                JsonObject lib = element.getAsJsonObject();
                if (!lib.has("name")) {
                    continue;
                }

                String mavenName = lib.get("name").getAsString();
                String relativePath = FabricInstallService.mavenCoordinateToPath(mavenName);
                String baseUrl = lib.has("url") && !lib.get("url").isJsonNull()
                        ? lib.get("url").getAsString()
                        : "https://maven.quiltmc.org/repository/release/";

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
                listener.update("Installing Quilt libraries... " + msg, 0.84 + prog * 0.16);
            }
        });

        Files.createDirectories(gameFolder.resolve("mods"));

        if (listener != null) {
            listener.update("Minecraft " + mcVersion + " (Quilt " + loaderVersion + ") installed!", 1.0);
        }

        return new QuiltInstallResult(profileId, loaderVersion);
    }
}
