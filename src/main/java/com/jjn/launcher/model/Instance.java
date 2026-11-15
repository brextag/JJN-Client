package com.jjn.launcher.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Represents a separate Minecraft instance under ~/.jjnclient/instances/<id>.
 */
public class Instance {

    public static final String LOADER_VANILLA = "vanilla";
    public static final String LOADER_FABRIC = "fabric";
    public static final String LOADER_QUILT = "quilt";
    public static final String LOADER_NEOFORGE = "neoforge";
    public static final String LOADER_FORGE = "forge";

    public final String id;
    public String name;
    public final Path folder;

    /** Launchable version folder name (e.g. "1.21.4" or "fabric-loader-0.16.10-1.21.4"). */
    public String version;

    /** Base vanilla Minecraft version (e.g. "1.21.4"). */
    public String mcVersion;

    /** "vanilla", "fabric", "quilt", "neoforge", or "forge". */
    public String loaderType = LOADER_VANILLA;

    /** Mod loader version when loaderType != "vanilla". */
    public String loaderVersion = "";

    /** 0 means inherit global LauncherSettings.ramMb. */
    public int ramMbOverride = 0;

    /** Extra JVM args specific to this instance. */
    public String jvmArgs = "";

    /** Optional server address for Quick Play on launch. */
    public String quickPlayServer = "";

    /** 0 means inherit global LauncherSettings window size. */
    public int customWidth = 0;
    public int customHeight = 0;

    public Instance(String id, String name, Path folder) {
        this.id = id;
        this.name = name;
        this.folder = folder;
    }

    public Path gameFolder() {
        return folder.resolve("game");
    }

    public Path modsFolder() {
        return gameFolder().resolve("mods");
    }

    public boolean isInstalled() {
        return version != null && !version.isBlank();
    }

    public String loaderDisplayName() {
        return displayNameForLoader(loaderType);
    }

    public static String displayNameForLoader(String loaderType) {
        if (loaderType == null) {
            return "Vanilla";
        }
        return switch (loaderType.toLowerCase()) {
            case LOADER_FABRIC -> "Fabric";
            case LOADER_QUILT -> "Quilt";
            case LOADER_NEOFORGE -> "NeoForge";
            case LOADER_FORGE -> "Forge";
            default -> "Vanilla";
        };
    }

    public static String loaderFromDisplayName(String display) {
        if (display == null) {
            return LOADER_VANILLA;
        }
        String lower = display.trim().toLowerCase();
        if (lower.startsWith("fabric")) return LOADER_FABRIC;
        if (lower.startsWith("quilt")) return LOADER_QUILT;
        if (lower.startsWith("neoforge")) return LOADER_NEOFORGE;
        if (lower.startsWith("forge")) return LOADER_FORGE;
        return LOADER_VANILLA;
    }

    public String displaySummary() {
        String mc = (mcVersion != null && !mcVersion.isBlank()) ? mcVersion : version;
        if (!isInstalled()) {
            if (mc != null && !mc.isBlank()) {
                return "Not installed  •  Target: Minecraft " + mc + " (" + loaderDisplayName() + ")";
            }
            return "Not installed yet";
        }
        if (LOADER_VANILLA.equalsIgnoreCase(loaderType)) {
            return "Minecraft " + mc + " (Vanilla)";
        }
        String loaderVer = (loaderVersion == null || loaderVersion.isBlank()) ? "" : " " + loaderVersion;
        return "Minecraft " + mc + " (" + loaderDisplayName() + loaderVer + ")";
    }

    public static Instance load(Path folder) throws IOException {
        Path metadata = folder.resolve("instance.properties");
        if (!Files.isDirectory(folder) || !Files.exists(metadata)) {
            return null;
        }

        Properties properties = new Properties();
        try (var input = Files.newInputStream(metadata)) {
            properties.load(input);
        }

        String id = properties.getProperty("id");
        String name = properties.getProperty("name");
        if (id == null || name == null) {
            return null;
        }

        Instance instance = new Instance(id, name, folder);
        instance.version = blankToNull(properties.getProperty("version"));
        instance.mcVersion = blankToNull(properties.getProperty("mcVersion"));
        instance.loaderType = properties.getProperty("loaderType", LOADER_VANILLA).trim();
        if (instance.loaderType.isEmpty()) {
            instance.loaderType = LOADER_VANILLA;
        }
        instance.loaderVersion = properties.getProperty("loaderVersion", "").trim();
        instance.jvmArgs = properties.getProperty("jvmArgs", "").trim();
        instance.quickPlayServer = properties.getProperty("quickPlayServer", "").trim();

        instance.ramMbOverride = parseInt(properties.getProperty("ramMbOverride"), 0);
        instance.customWidth = parseInt(properties.getProperty("customWidth"), 0);
        instance.customHeight = parseInt(properties.getProperty("customHeight"), 0);

        // Backwards compatibility with older instance.properties
        if (instance.mcVersion == null && instance.version != null) {
            instance.mcVersion = instance.version;
        }

        return instance;
    }

    public void save() throws IOException {
        Files.createDirectories(folder);

        Properties properties = new Properties();
        properties.setProperty("id", id);
        properties.setProperty("name", name);
        if (version != null && !version.isBlank()) {
            properties.setProperty("version", version);
        }
        if (mcVersion != null && !mcVersion.isBlank()) {
            properties.setProperty("mcVersion", mcVersion);
        }
        properties.setProperty("loaderType", loaderType == null ? LOADER_VANILLA : loaderType);
        properties.setProperty("loaderVersion", loaderVersion == null ? "" : loaderVersion);
        properties.setProperty("ramMbOverride", String.valueOf(Math.max(0, ramMbOverride)));
        properties.setProperty("jvmArgs", jvmArgs == null ? "" : jvmArgs);
        properties.setProperty("quickPlayServer", quickPlayServer == null ? "" : quickPlayServer);
        properties.setProperty("customWidth", String.valueOf(Math.max(0, customWidth)));
        properties.setProperty("customHeight", String.valueOf(Math.max(0, customHeight)));

        try (var output = Files.newOutputStream(folder.resolve("instance.properties"))) {
            properties.store(output, "JJN Client Instance");
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
