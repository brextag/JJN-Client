package com.jjn.launcher.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jjn.launcher.JavaDetector;
import com.jjn.launcher.LauncherSettings;
import com.jjn.launcher.MinecraftInstallService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Installs Minecraft Forge or NeoForge into an instance's game folder
 * by downloading the official installer jar and running {@code --installClient}.
 */
public class ForgeInstallService {

    private static final String FORGE_PROMOS_URL =
            "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final String FORGE_MAVEN =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/";
    private static final String NEOFORGE_LATEST_API =
            "https://maven.neoforged.net/api/maven/latest/version/releases/net/neoforged/neoforge?filter=";
    private static final String NEOFORGE_MAVEN =
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/";

    public record ForgeInstallResult(String versionId, String loaderVersion) {}

    private final MinecraftInstallService minecraftInstaller;
    private final JavaProvisionService javaProvision = new JavaProvisionService();

    public ForgeInstallService(MinecraftInstallService minecraftInstaller) {
        this.minecraftInstaller = minecraftInstaller;
    }

    public String getLatestForgeVersion(String mcVersion) throws Exception {
        JsonObject promosRoot = minecraftInstaller.getJson(FORGE_PROMOS_URL);
        JsonObject promos = promosRoot.getAsJsonObject("promos");
        if (promos == null) {
            throw new Exception("Forge promotions metadata is empty.");
        }

        String recKey = mcVersion + "-recommended";
        if (promos.has(recKey) && !promos.get(recKey).isJsonNull()) {
            return promos.get(recKey).getAsString();
        }

        String latestKey = mcVersion + "-latest";
        if (promos.has(latestKey) && !promos.get(latestKey).isJsonNull()) {
            return promos.get(latestKey).getAsString();
        }

        throw new Exception("Forge does not support Minecraft " + mcVersion + ".");
    }

    public String getLatestNeoForgeVersion(String mcVersion) throws Exception {
        String prefix = neoForgePrefix(mcVersion);
        JsonObject resp;
        try {
            resp = minecraftInstaller.getJson(NEOFORGE_LATEST_API + prefix);
        } catch (Exception ex) {
            throw new Exception("NeoForge does not support Minecraft " + mcVersion + " (or metadata unavailable).");
        }
        if (resp == null || !resp.has("version") || resp.get("version").isJsonNull()) {
            throw new Exception("No NeoForge release found for Minecraft " + mcVersion + ".");
        }
        return resp.get("version").getAsString();
    }

    public ForgeInstallResult installForge(
            String mcVersion,
            Path gameFolder,
            MinecraftInstallService.ProgressListener listener
    ) throws Exception {
        if (listener != null) {
            listener.update("Resolving Forge version for " + mcVersion + "...", 0.02);
        }
        String forgeVersion = getLatestForgeVersion(mcVersion);
        String full = mcVersion + "-" + forgeVersion;
        String installerUrl = FORGE_MAVEN + full + "/forge-" + full + "-installer.jar";
        return installFromInstaller(
                mcVersion,
                forgeVersion,
                installerUrl,
                "forge-" + full + "-installer.jar",
                "Forge",
                gameFolder,
                listener
        );
    }

    public ForgeInstallResult installNeoForge(
            String mcVersion,
            Path gameFolder,
            MinecraftInstallService.ProgressListener listener
    ) throws Exception {
        if (listener != null) {
            listener.update("Resolving NeoForge version for " + mcVersion + "...", 0.02);
        }
        String neoVersion = getLatestNeoForgeVersion(mcVersion);
        String installerUrl = NEOFORGE_MAVEN + neoVersion + "/neoforge-" + neoVersion + "-installer.jar";
        return installFromInstaller(
                mcVersion,
                neoVersion,
                installerUrl,
                "neoforge-" + neoVersion + "-installer.jar",
                "NeoForge",
                gameFolder,
                listener
        );
    }

    private ForgeInstallResult installFromInstaller(
            String mcVersion,
            String loaderVersion,
            String installerUrl,
            String installerFileName,
            String loaderLabel,
            Path gameFolder,
            MinecraftInstallService.ProgressListener listener
    ) throws Exception {

        // 1. Install base vanilla Minecraft first (into shared cache + natives)
        minecraftInstaller.install(mcVersion, gameFolder, (msg, prog) -> {
            if (listener != null) {
                listener.update(msg, 0.04 + prog * 0.50);
            }
        });

        Files.createDirectories(gameFolder);
        Files.createDirectories(gameFolder.resolve("mods"));
        ensureLauncherProfilesStub(gameFolder);
        seedVanillaIntoGameFolder(mcVersion, gameFolder);

        // 2. Download installer jar
        if (listener != null) {
            listener.update("Downloading " + loaderLabel + " " + loaderVersion + " installer...", 0.58);
        }
        Path downloadsDir = gameFolder.resolve("downloads");
        Files.createDirectories(downloadsDir);
        Path installerJar = downloadsDir.resolve(installerFileName);
        Files.deleteIfExists(installerJar);

        minecraftInstaller.downloadAll(
                List.of(new MinecraftInstallService.DownloadItem(installerUrl, installerJar, null, 0L)),
                (msg, prog) -> {
                    if (listener != null) {
                        listener.update("Downloading " + loaderLabel + " installer...", 0.58 + prog * 0.14);
                    }
                }
        );

        String profileVersionId = readVersionIdFromInstaller(installerJar);

        // 3. Pick Java (>= 17, prefer >= 21) for running installer
        JavaDetector.JavaInstall java = pickInstallerJava(listener);

        if (listener != null) {
            listener.update("Running " + loaderLabel + " installer (this may take a moment)...", 0.76);
        }

        ProcessBuilder pb = new ProcessBuilder(
                java.javaExe().toString(),
                "-jar",
                installerJar.toAbsolutePath().toString(),
                "--installClient",
                gameFolder.toAbsolutePath().toString()
        )
                .directory(gameFolder.toFile())
                .redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder outputTail = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (outputTail.length() > 4000) {
                    outputTail.delete(0, outputTail.length() - 3000);
                }
                outputTail.append(line).append('\n');
                if (listener != null && !line.isBlank()) {
                    String shortLine = line.length() > 75 ? line.substring(0, 75) + "..." : line;
                    listener.update(loaderLabel + ": " + shortLine, 0.85);
                }
            }
        }

        int exit = process.waitFor();
        Files.deleteIfExists(installerJar);

        if (exit != 0) {
            throw new Exception(
                    loaderLabel + " installer exited with code " + exit + ".\n" + outputTail
            );
        }

        String resolvedId = resolveInstalledVersionId(gameFolder, mcVersion, profileVersionId, loaderLabel);

        if (listener != null) {
            listener.update("Minecraft " + mcVersion + " (" + loaderLabel + " " + loaderVersion + ") installed!", 1.0);
        }

        return new ForgeInstallResult(resolvedId, loaderVersion);
    }

    private static String neoForgePrefix(String mcVersion) {
        if (mcVersion.startsWith("1.")) {
            String rest = mcVersion.substring(2); // e.g. "21.4" or "21"
            if (!rest.contains(".")) {
                return rest + ".0.";
            }
            return rest + ".";
        }
        return mcVersion + ".";
    }

    private static void ensureLauncherProfilesStub(Path gameFolder) throws Exception {
        Path profiles = gameFolder.resolve("launcher_profiles.json");
        if (!Files.exists(profiles)) {
            Files.writeString(profiles, "{\"profiles\":{}}\n", StandardCharsets.UTF_8);
        }
    }

    /**
     * Copies cached vanilla jar/json into gameFolder/versions/<mc> so the Forge/NeoForge
     * installer doesn't need to re-download the vanilla client jar.
     */
    private static void seedVanillaIntoGameFolder(String mcVersion, Path gameFolder) {
        try {
            Path sharedVer = LauncherSettings.sharedDir().resolve("versions").resolve(mcVersion);
            Path localVer = gameFolder.resolve("versions").resolve(mcVersion);
            Files.createDirectories(localVer);
            for (String ext : new String[]{".jar", ".json"}) {
                Path src = sharedVer.resolve(mcVersion + ext);
                Path dst = localVer.resolve(mcVersion + ext);
                if (Files.isRegularFile(src) && !Files.isRegularFile(dst)) {
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String readVersionIdFromInstaller(Path installerJar) {
        try (ZipFile zip = new ZipFile(installerJar.toFile())) {
            ZipEntry entry = zip.getEntry("install_profile.json");
            if (entry != null) {
                try (var in = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(in).getAsJsonObject();
                    if (obj.has("version") && !obj.get("version").isJsonNull()) {
                        return obj.get("version").getAsString();
                    }
                }
            }
            ZipEntry verEntry = zip.getEntry("version.json");
            if (verEntry != null) {
                try (var in = new InputStreamReader(zip.getInputStream(verEntry), StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(in).getAsJsonObject();
                    if (obj.has("id") && !obj.get("id").isJsonNull()) {
                        return obj.get("id").getAsString();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String resolveInstalledVersionId(
            Path gameFolder,
            String mcVersion,
            String hintId,
            String loaderLabel
    ) throws Exception {
        Path versionsDir = gameFolder.resolve("versions");
        if (hintId != null && Files.isRegularFile(versionsDir.resolve(hintId).resolve(hintId + ".json"))) {
            return hintId;
        }
        String needle = loaderLabel.toLowerCase();
        if (Files.isDirectory(versionsDir)) {
            try (var stream = Files.list(versionsDir)) {
                for (Path dir : stream.toList()) {
                    if (!Files.isDirectory(dir)) continue;
                    String name = dir.getFileName().toString();
                    if (!name.equals(mcVersion)
                            && name.toLowerCase().contains(needle)
                            && Files.isRegularFile(dir.resolve(name + ".json"))) {
                        return name;
                    }
                }
            }
        }
        if (hintId != null) {
            return hintId;
        }
        throw new Exception(loaderLabel + " installer finished, but could not locate the installed version profile.");
    }

    private JavaDetector.JavaInstall pickInstallerJava(MinecraftInstallService.ProgressListener listener)
            throws Exception {
        List<JavaDetector.JavaInstall> installs = JavaDetector.detect();
        JavaDetector.JavaInstall preferred = null;
        for (JavaDetector.JavaInstall install : installs) {
            if (install.major() >= 21) {
                return install;
            }
            if (install.major() >= 17 && preferred == null) {
                preferred = install;
            }
        }
        if (preferred != null) {
            return preferred;
        }
        return javaProvision.ensureJava(21, (msg, prog) -> {
            if (listener != null) {
                listener.update(msg, 0.74);
            }
        });
    }
}
