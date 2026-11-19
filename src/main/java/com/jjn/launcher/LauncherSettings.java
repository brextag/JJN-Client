package com.jjn.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Launcher-wide settings, stored in %USERPROFILE%\.jjnclient\settings.properties
 */
public class LauncherSettings {

    public static final Path ROOT_DIR = Paths.get(
            System.getProperty("user.home"),
            ".jjnclient"
    );

    private static final Path FILE = ROOT_DIR.resolve("settings.properties");

    public static final int MIN_RAM_MB = 1024;
    public static final int DEFAULT_RAM_MB = 4096;

    /** Memory given to Minecraft (-Xmx), in megabytes. */
    public int ramMb = DEFAULT_RAM_MB;

    /** Full path to java.exe. Empty means "Automatic". */
    public String javaPath = "";

    /** Extra global JVM flags appended on launch. */
    public String jvmArgs = "";

    /** Custom game window resolution (0 = Minecraft default). */
    public int windowWidth = 0;
    public int windowHeight = 0;

    /** Minimize/hide launcher window while Minecraft is running. */
    public boolean hideOnLaunch = false;

    // UI Customization defaults (Blue, White & Black + Official Minecraft Font + Tactile MC Bevel)
    public static final String DEFAULT_THEME_PRESET = "blue-black-white";
    public static final String DEFAULT_ACCENT_COLOR = "#2563EB";
    public static final String DEFAULT_BG_COLOR = "#05070B";
    public static final String DEFAULT_SURFACE_COLOR = "#0F1626";
    public static final String DEFAULT_TEXT_COLOR = "#FFFFFF";
    public static final String DEFAULT_BUTTON_STYLE = "mc-bevel";
    public static final int DEFAULT_CORNER_RADIUS = 4;
    public static final boolean DEFAULT_MINECRAFT_FONT = true;
    public static final double DEFAULT_FONT_SCALE = 1.0;
    public static final boolean DEFAULT_COMPACT_UI = false;
    public static final boolean DEFAULT_HERO_GLOW = true;

    public String uiThemePreset = DEFAULT_THEME_PRESET;
    public String uiAccentColor = DEFAULT_ACCENT_COLOR;
    public String uiBgColor = DEFAULT_BG_COLOR;
    public String uiSurfaceColor = DEFAULT_SURFACE_COLOR;
    public String uiTextColor = DEFAULT_TEXT_COLOR;
    public String uiButtonStyle = DEFAULT_BUTTON_STYLE;
    public int uiCornerRadius = DEFAULT_CORNER_RADIUS;
    public boolean uiUseMinecraftFont = DEFAULT_MINECRAFT_FONT;
    public double uiFontScale = DEFAULT_FONT_SCALE;
    public boolean uiCompactSpacing = DEFAULT_COMPACT_UI;
    public boolean uiHeroGlow = DEFAULT_HERO_GLOW;

    public void resetUiDefaults() {
        uiThemePreset = DEFAULT_THEME_PRESET;
        uiAccentColor = DEFAULT_ACCENT_COLOR;
        uiBgColor = DEFAULT_BG_COLOR;
        uiSurfaceColor = DEFAULT_SURFACE_COLOR;
        uiTextColor = DEFAULT_TEXT_COLOR;
        uiButtonStyle = DEFAULT_BUTTON_STYLE;
        uiCornerRadius = DEFAULT_CORNER_RADIUS;
        uiUseMinecraftFont = DEFAULT_MINECRAFT_FONT;
        uiFontScale = DEFAULT_FONT_SCALE;
        uiCompactSpacing = DEFAULT_COMPACT_UI;
        uiHeroGlow = DEFAULT_HERO_GLOW;
    }

    public static Path sharedDir() {
        return ROOT_DIR.resolve("shared");
    }

    public static Path runtimesDir() {
        return ROOT_DIR.resolve("runtimes");
    }

    public static Path instancesDir() {
        return ROOT_DIR.resolve("instances");
    }

    public static LauncherSettings load() {

        LauncherSettings settings = new LauncherSettings();

        if (!Files.exists(FILE)) {
            return settings;
        }

        try (var input = Files.newInputStream(FILE)) {

            Properties properties = new Properties();
            properties.load(input);

            try {
                int ram = Integer.parseInt(
                        properties.getProperty("ramMb",
                                String.valueOf(DEFAULT_RAM_MB)).trim()
                );
                settings.ramMb = Math.max(MIN_RAM_MB, ram);
            } catch (NumberFormatException ignored) {
                settings.ramMb = DEFAULT_RAM_MB;
            }

            settings.javaPath = properties.getProperty("javaPath", "").trim();
            settings.jvmArgs = properties.getProperty("jvmArgs", "").trim();
            settings.windowWidth = parseInt(properties.getProperty("windowWidth"), 0);
            settings.windowHeight = parseInt(properties.getProperty("windowHeight"), 0);
            settings.hideOnLaunch = Boolean.parseBoolean(
                    properties.getProperty("hideOnLaunch", "false").trim()
            );

            settings.uiThemePreset = nonBlankOrDefault(
                    properties.getProperty("uiThemePreset"), DEFAULT_THEME_PRESET
            );
            settings.uiAccentColor = validHexOrDefault(
                    properties.getProperty("uiAccentColor"), DEFAULT_ACCENT_COLOR
            );
            settings.uiBgColor = validHexOrDefault(
                    properties.getProperty("uiBgColor"), DEFAULT_BG_COLOR
            );
            settings.uiSurfaceColor = validHexOrDefault(
                    properties.getProperty("uiSurfaceColor"), DEFAULT_SURFACE_COLOR
            );
            settings.uiTextColor = validHexOrDefault(
                    properties.getProperty("uiTextColor"), DEFAULT_TEXT_COLOR
            );
            settings.uiButtonStyle = nonBlankOrDefault(
                    properties.getProperty("uiButtonStyle"), DEFAULT_BUTTON_STYLE
            );
            settings.uiCornerRadius = Math.max(0, Math.min(20, parseInt(
                    properties.getProperty("uiCornerRadius"), DEFAULT_CORNER_RADIUS
            )));
            settings.uiUseMinecraftFont = Boolean.parseBoolean(
                    properties.getProperty("uiUseMinecraftFont", String.valueOf(DEFAULT_MINECRAFT_FONT)).trim()
            );
            settings.uiFontScale = parseDoubleClamped(
                    properties.getProperty("uiFontScale"), DEFAULT_FONT_SCALE, 0.85, 1.25
            );
            settings.uiCompactSpacing = Boolean.parseBoolean(
                    properties.getProperty("uiCompactSpacing", String.valueOf(DEFAULT_COMPACT_UI)).trim()
            );
            settings.uiHeroGlow = Boolean.parseBoolean(
                    properties.getProperty("uiHeroGlow", String.valueOf(DEFAULT_HERO_GLOW)).trim()
            );

        } catch (IOException ignored) {
            // Fall back to defaults if the file can't be read.
        }

        return settings;
    }

    public void save() throws IOException {

        Files.createDirectories(FILE.getParent());

        Properties properties = new Properties();
        properties.setProperty("ramMb", String.valueOf(ramMb));
        properties.setProperty("javaPath", javaPath == null ? "" : javaPath);
        properties.setProperty("jvmArgs", jvmArgs == null ? "" : jvmArgs);
        properties.setProperty("windowWidth", String.valueOf(Math.max(0, windowWidth)));
        properties.setProperty("windowHeight", String.valueOf(Math.max(0, windowHeight)));
        properties.setProperty("hideOnLaunch", String.valueOf(hideOnLaunch));

        properties.setProperty("uiThemePreset", nonBlankOrDefault(uiThemePreset, DEFAULT_THEME_PRESET));
        properties.setProperty("uiAccentColor", validHexOrDefault(uiAccentColor, DEFAULT_ACCENT_COLOR));
        properties.setProperty("uiBgColor", validHexOrDefault(uiBgColor, DEFAULT_BG_COLOR));
        properties.setProperty("uiSurfaceColor", validHexOrDefault(uiSurfaceColor, DEFAULT_SURFACE_COLOR));
        properties.setProperty("uiTextColor", validHexOrDefault(uiTextColor, DEFAULT_TEXT_COLOR));
        properties.setProperty("uiButtonStyle", nonBlankOrDefault(uiButtonStyle, DEFAULT_BUTTON_STYLE));
        properties.setProperty("uiCornerRadius", String.valueOf(Math.max(0, Math.min(20, uiCornerRadius))));
        properties.setProperty("uiUseMinecraftFont", String.valueOf(uiUseMinecraftFont));
        properties.setProperty("uiFontScale", String.valueOf(uiFontScale));
        properties.setProperty("uiCompactSpacing", String.valueOf(uiCompactSpacing));
        properties.setProperty("uiHeroGlow", String.valueOf(uiHeroGlow));

        try (var output = Files.newOutputStream(FILE)) {
            properties.store(output, "JJN Client Settings");
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double parseDoubleClamped(String value, double fallback, double min, double max) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                return fallback;
            }
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String nonBlankOrDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private static String validHexOrDefault(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.matches("^#[0-9a-fA-F]{6}$")) {
            return trimmed.toUpperCase();
        }
        return fallback;
    }
}