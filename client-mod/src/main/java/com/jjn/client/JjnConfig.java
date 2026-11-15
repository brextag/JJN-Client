package com.jjn.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Persisted in-game JJN Client settings stored in <gameDir>/config/jjnclient.properties.
 */
public final class JjnConfig {

    public static final int CORNER_TOP_LEFT = 0;
    public static final int CORNER_TOP_RIGHT = 1;
    public static final int CORNER_BOTTOM_LEFT = 2;
    public static final int CORNER_BOTTOM_RIGHT = 3;
    public static final int CORNER_CUSTOM = 4;

    public static final int OPACITY_TRANSPARENT = 0;
    public static final int OPACITY_GLASS = 1;
    public static final int OPACITY_SOLID = 2;

    public static final int[] ACCENT_COLORS = {
            0xFFC7D2FE,
            0xFF86EFAC,
            0xFF67E8F9,
            0xFFFCA5A5,
            0xFFFCD34D,
            0xFFFFFFFF
    };

    public static final String[] ACCENT_NAMES = {
            "Indigo", "Emerald", "Cyan", "Rose", "Amber", "White"
    };

    private static final JjnConfig INSTANCE = new JjnConfig();

    // Profile & skin identity (synced from JJNLauncher Profiles)
    public String profileName = "Default";
    public boolean localSkinEnabled = false;
    public String skinVariant = "classic";
    public boolean localCapeEnabled = false;

    public boolean customTitleScreen = true;
    public boolean titleScreenGrid = true;
    public boolean showTitlePreview = true;

    public boolean showHud = true;
    public boolean showFps = true;
    public boolean fpsCompact = false;
    public boolean showCoords = true;
    public boolean showDirection = true;
    public boolean toggleSprint = true;
    public boolean showSprintHud = true;
    public boolean showPingServer = false;
    public boolean showBiome = false;
    public boolean showKeystrokes = false;
    public boolean showCps = true;
    public boolean zoomEnabled = true;
    public boolean zoomSmoothCamera = true;
    public boolean fullbright = false;
    public boolean showArmorHud = true;
    public boolean showPotionHud = true;

    // Waypoints
    public boolean showWaypoints = true;
    public boolean waypointShowDistance = true;
    public boolean waypointShowBeacon = true;
    public boolean waypointEdgeIndicators = true;
    public int waypointMaxDistance = 0;

    // Emotes
    public boolean emoteAutoThirdPerson = true;

    public int hudCorner = CORNER_TOP_LEFT;
    public int hudCustomX = 8;
    public int hudCustomY = 8;
    public float hudScale = 1.0f;
    public int hudOpacity = OPACITY_SOLID;
    public int hudAccentIndex = 0;
    public boolean hudTextShadow = true;

    private JjnConfig() {}

    public static JjnConfig get() {
        return INSTANCE;
    }

    public int accentColor() {
        int idx = Math.max(0, Math.min(ACCENT_COLORS.length - 1, hudAccentIndex));
        return ACCENT_COLORS[idx];
    }

    public String accentName() {
        int idx = Math.max(0, Math.min(ACCENT_NAMES.length - 1, hudAccentIndex));
        return ACCENT_NAMES[idx];
    }

    public String cornerName() {
        return switch (hudCorner) {
            case CORNER_TOP_RIGHT -> "Top-Right";
            case CORNER_BOTTOM_LEFT -> "Bottom-Left";
            case CORNER_BOTTOM_RIGHT -> "Bottom-Right";
            case CORNER_CUSTOM -> "Custom (" + hudCustomX + ", " + hudCustomY + ")";
            default -> "Top-Left";
        };
    }

    public String opacityName() {
        return switch (hudOpacity) {
            case OPACITY_TRANSPARENT -> "Transparent";
            case OPACITY_GLASS -> "Glass";
            default -> "Solid";
        };
    }

    public int backgroundArgb() {
        return switch (hudOpacity) {
            case OPACITY_TRANSPARENT -> 0x0015171E;
            case OPACITY_GLASS -> 0x7A15171E;
            default -> 0xC615171E;
        };
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("jjnclient.properties");
    }

    public static Path localSkinFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("jjnclient-skin.png");
    }

    public static Path localCapeFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("jjnclient-cape.png");
    }

    public void load() {
        Path file = configFile();
        if (!Files.isRegularFile(file)) {
            save();
            return;
        }
        Properties props = new Properties();
        try (var in = Files.newInputStream(file)) {
            props.load(in);
            profileName = props.getProperty("profileName", "Default");
            localSkinEnabled = bool(props, "localSkinEnabled", false);
            skinVariant = props.getProperty("skinVariant", "classic");
            localCapeEnabled = bool(props, "localCapeEnabled", false);
            customTitleScreen = bool(props, "customTitleScreen", true);
            titleScreenGrid = bool(props, "titleScreenGrid", true);
            showTitlePreview = bool(props, "showTitlePreview", true);
            showHud = bool(props, "showHud", true);
            showFps = bool(props, "showFps", true);
            fpsCompact = bool(props, "fpsCompact", false);
            showCoords = bool(props, "showCoords", true);
            showDirection = bool(props, "showDirection", true);
            toggleSprint = bool(props, "toggleSprint", true);
            showSprintHud = bool(props, "showSprintHud", true);
            showPingServer = bool(props, "showPingServer", false);
            showBiome = bool(props, "showBiome", false);
            showKeystrokes = bool(props, "showKeystrokes", false);
            showCps = bool(props, "showCps", true);
            zoomEnabled = bool(props, "zoomEnabled", true);
            zoomSmoothCamera = bool(props, "zoomSmoothCamera", true);
            fullbright = bool(props, "fullbright", false);
            showArmorHud = bool(props, "showArmorHud", true);
            showPotionHud = bool(props, "showPotionHud", true);
            showWaypoints = bool(props, "showWaypoints", true);
            waypointShowDistance = bool(props, "waypointShowDistance", true);
            waypointShowBeacon = bool(props, "waypointShowBeacon", true);
            waypointEdgeIndicators = bool(props, "waypointEdgeIndicators", true);
            waypointMaxDistance = clampInt(props, "waypointMaxDistance", 0, 0, 100000);
            emoteAutoThirdPerson = bool(props, "emoteAutoThirdPerson", true);
            hudCorner = clampInt(props, "hudCorner", CORNER_TOP_LEFT, 0, 4);
            hudCustomX = clampInt(props, "hudCustomX", 8, 0, 4000);
            hudCustomY = clampInt(props, "hudCustomY", 8, 0, 4000);
            hudScale = parseFloat(props.getProperty("hudScale"), 1.0f);
            hudOpacity = clampInt(props, "hudOpacity", OPACITY_SOLID, 0, 2);
            hudAccentIndex = clampInt(props, "hudAccentIndex", 0, 0, ACCENT_COLORS.length - 1);
            hudTextShadow = bool(props, "hudTextShadow", true);
        } catch (IOException ignored) {
        }
    }

    public void save() {
        Path file = configFile();
        try {
            Files.createDirectories(file.getParent());
            Properties props = new Properties();
            props.setProperty("profileName", profileName == null ? "Default" : profileName);
            props.setProperty("localSkinEnabled", String.valueOf(localSkinEnabled));
            props.setProperty("skinVariant", skinVariant == null ? "classic" : skinVariant);
            props.setProperty("localCapeEnabled", String.valueOf(localCapeEnabled));
            props.setProperty("customTitleScreen", String.valueOf(customTitleScreen));
            props.setProperty("titleScreenGrid", String.valueOf(titleScreenGrid));
            props.setProperty("showTitlePreview", String.valueOf(showTitlePreview));
            props.setProperty("showHud", String.valueOf(showHud));
            props.setProperty("showFps", String.valueOf(showFps));
            props.setProperty("fpsCompact", String.valueOf(fpsCompact));
            props.setProperty("showCoords", String.valueOf(showCoords));
            props.setProperty("showDirection", String.valueOf(showDirection));
            props.setProperty("toggleSprint", String.valueOf(toggleSprint));
            props.setProperty("showSprintHud", String.valueOf(showSprintHud));
            props.setProperty("showPingServer", String.valueOf(showPingServer));
            props.setProperty("showBiome", String.valueOf(showBiome));
            props.setProperty("showKeystrokes", String.valueOf(showKeystrokes));
            props.setProperty("showCps", String.valueOf(showCps));
            props.setProperty("zoomEnabled", String.valueOf(zoomEnabled));
            props.setProperty("zoomSmoothCamera", String.valueOf(zoomSmoothCamera));
            props.setProperty("fullbright", String.valueOf(fullbright));
            props.setProperty("showArmorHud", String.valueOf(showArmorHud));
            props.setProperty("showPotionHud", String.valueOf(showPotionHud));
            props.setProperty("showWaypoints", String.valueOf(showWaypoints));
            props.setProperty("waypointShowDistance", String.valueOf(waypointShowDistance));
            props.setProperty("waypointShowBeacon", String.valueOf(waypointShowBeacon));
            props.setProperty("waypointEdgeIndicators", String.valueOf(waypointEdgeIndicators));
            props.setProperty("waypointMaxDistance", String.valueOf(waypointMaxDistance));
            props.setProperty("emoteAutoThirdPerson", String.valueOf(emoteAutoThirdPerson));
            props.setProperty("hudCorner", String.valueOf(hudCorner));
            props.setProperty("hudCustomX", String.valueOf(hudCustomX));
            props.setProperty("hudCustomY", String.valueOf(hudCustomY));
            props.setProperty("hudScale", String.valueOf(hudScale));
            props.setProperty("hudOpacity", String.valueOf(hudOpacity));
            props.setProperty("hudAccentIndex", String.valueOf(hudAccentIndex));
            props.setProperty("hudTextShadow", String.valueOf(hudTextShadow));
            try (var out = Files.newOutputStream(file)) {
                props.store(out, "JJN Client In-Game Config");
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean bool(Properties p, String key, boolean def) {
        return Boolean.parseBoolean(p.getProperty(key, String.valueOf(def)));
    }

    private static int clampInt(Properties p, String key, int def, int min, int max) {
        try {
            int val = Integer.parseInt(p.getProperty(key, String.valueOf(def)));
            return Math.max(min, Math.min(max, val));
        } catch (NumberFormatException ignored) {
            return def;
        }
    }

    private static float parseFloat(String raw, float def) {
        if (raw == null || raw.isBlank()) return def;
        try {
            float v = Float.parseFloat(raw.trim());
            return Math.max(0.75f, Math.min(1.5f, v));
        } catch (NumberFormatException ignored) {
            return def;
        }
    }
}
