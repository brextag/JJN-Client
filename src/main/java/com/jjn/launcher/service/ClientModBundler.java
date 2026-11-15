package com.jjn.launcher.service;

import com.jjn.launcher.model.Instance;

import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Bundles the built-in JJN Client in-game Fabric mod into compatible Fabric
 * instances automatically on install and launch (unless disabled by the user).
 */
public final class ClientModBundler {

    public static final String DEFAULT_MC_VERSION = "26.2";

    private static final Map<String, String> MOD_FILES = Map.of(
            "26.2", "jjn-client-26.2.jar",
            "1.21.4", "jjn-client-1.21.4.jar"
    );

    private ClientModBundler() {}

    public static boolean isMcVersionSupported(String mcVersion) {
        return mcVersion != null && MOD_FILES.containsKey(mcVersion);
    }

    public static String modFilenameFor(String mcVersion) {
        return mcVersion == null ? null : MOD_FILES.get(mcVersion);
    }

    public static boolean isSupported(Instance instance) {
        return instance != null
                && Instance.LOADER_FABRIC.equalsIgnoreCase(instance.loaderType)
                && isMcVersionSupported(instance.mcVersion);
    }

    public static void ensureInstalled(Instance instance) {
        if (!isSupported(instance)) {
            return;
        }
        ensureInstalled(instance.mcVersion, instance.gameFolder());
    }

    public static void ensureInstalled(String mcVersion, Path gameFolder) {
        String modFilename = modFilenameFor(mcVersion);
        if (modFilename == null || gameFolder == null) {
            return;
        }

        Path modsDir = gameFolder.resolve("mods");
        removeMismatchedBuiltInJars(modsDir, modFilename);

        Path disabledTarget = modsDir.resolve(modFilename + ".disabled");
        if (Files.exists(disabledTarget)) {
            // User explicitly disabled the built-in JJN Client mod in Mods Manager.
            return;
        }

        String resourcePath = "/com/jjn/launcher/mods/" + modFilename;
        try (InputStream stream = ClientModBundler.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return;
            }

            Files.createDirectories(modsDir);
            Path target = modsDir.resolve(modFilename);
            byte[] bytes = stream.readAllBytes();

            if (Files.isRegularFile(target)) {
                String expectedSha = sha256(bytes);
                String actualSha = sha256(Files.readAllBytes(target));
                if (expectedSha.equals(actualSha)) {
                    return;
                }
            }

            Path temp = modsDir.resolve(modFilename + ".part");
            Files.write(temp, bytes);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
            // Non-fatal: game can still launch if copy fails.
        }
    }

    private static void removeMismatchedBuiltInJars(Path modsDir, String keepFilename) {
        if (!Files.isDirectory(modsDir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "jjn-client-*.jar")) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (!name.equals(keepFilename)) {
                    Files.deleteIfExists(entry);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(data));
    }
}
