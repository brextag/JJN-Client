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
}