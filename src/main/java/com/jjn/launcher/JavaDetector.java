package com.jjn.launcher;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds Java installations on Windows by looking in the usual install
 * folders, JAVA_HOME and PATH. Nothing is changed on the system.
 */
public final class JavaDetector {

    private JavaDetector() {}

    public record JavaInstall(Path javaExe, int major, String version, String vendor) {

        @Override
        public String toString() {

            if (javaExe == null) {
                return "Automatic (recommended)";
            }

            StringBuilder text = new StringBuilder();

            text.append(major > 0 ? "Java " + major : "Java (unknown version)");

            if (version != null && !version.isBlank()) {
                text.append("  (").append(version).append(")");
            }

            if (vendor != null && !vendor.isBlank()) {
                text.append("  -  ").append(vendor);
            }

            return text.toString();
        }
    }

    /** Placeholder entry meaning "pick the right Java automatically". */
    public static final JavaInstall AUTOMATIC =
            new JavaInstall(null, -1, "", "");

    private static final String[] VENDOR_FOLDERS = {
            "Eclipse Adoptium",
            "Eclipse Foundation",
            "Java",
            "Microsoft",
            "Zulu",
            "Amazon Corretto",
            "BellSoft",
            "Semeru",
            "AdoptOpenJDK",
            "OpenJDK",
            "GraalVM"
    };

    // DETECTION

    public static List<JavaInstall> detect() {

        Map<String, JavaInstall> found = new LinkedHashMap<>();

        // Launcher-provisioned runtimes (~/.jjnclient/runtimes)
        Path runtimesDir = LauncherSettings.runtimesDir();
        if (Files.isDirectory(runtimesDir)) {
            try (DirectoryStream<Path> runtimes = Files.newDirectoryStream(runtimesDir)) {
                for (Path runtime : runtimes) {
                    add(found, runtime.resolve("bin").resolve("java.exe"));
                }
            } catch (IOException ignored) {
                // Skip unreadable folders.
            }
        }

        // Standard install folders
        for (String variable : new String[]{"ProgramFiles", "ProgramFiles(x86)"}) {

            String base = System.getenv(variable);

            if (base == null) {
                continue;
            }

            for (String vendor : VENDOR_FOLDERS) {

                Path vendorFolder = Paths.get(base, vendor);

                if (!Files.isDirectory(vendorFolder)) {
                    continue;
                }

                try (DirectoryStream<Path> homes =
                             Files.newDirectoryStream(vendorFolder)) {

                    for (Path home : homes) {
                        add(found, home.resolve("bin").resolve("java.exe"));
                    }

                } catch (IOException ignored) {
                    // Skip folders we can't read.
                }
            }
        }

        // JAVA_HOME
        String javaHome = System.getenv("JAVA_HOME");

        if (javaHome != null && !javaHome.isBlank()) {
            add(found, Paths.get(javaHome, "bin", "java.exe"));
        }

        // PATH
        String path = System.getenv("PATH");

        if (path != null) {
            for (String entry : path.split(java.io.File.pathSeparator)) {

                if (entry.isBlank()) {
                    continue;
                }

                try {
                    add(found, Paths.get(entry.trim()).resolve("java.exe"));
                } catch (Exception ignored) {
                    // Ignore invalid PATH entries.
                }
            }
        }

        List<JavaInstall> result = new ArrayList<>(found.values());

        // Newest Java first
        result.sort(Comparator.comparingInt(JavaInstall::major).reversed());

        return result;
    }

    private static void add(Map<String, JavaInstall> found, Path javaExe) {

        JavaInstall install = inspect(javaExe);

        if (install != null) {
            found.putIfAbsent(
                    install.javaExe().toString().toLowerCase(Locale.ROOT),
                    install
            );
        }
    }

    // INSPECT ONE JAVA

    /** Returns null if the file doesn't exist or isn't a java executable. */
    public static JavaInstall inspect(Path javaExe) {

        if (javaExe == null) {
            return null;
        }

        Path exe = javaExe.toAbsolutePath().normalize();

        if (!Files.isRegularFile(exe)) {
            return null;
        }

        String fileName = exe.getFileName().toString().toLowerCase(Locale.ROOT);

        if (!fileName.equals("java.exe") && !fileName.equals("javaw.exe")) {
            return null;
        }

        String version = null;
        String vendor = "";

        // Most JDK/JRE folders have a "release" file next to bin/
        Path bin = exe.getParent();
        Path home = bin == null ? null : bin.getParent();

        if (home != null) {

            Path release = home.resolve("release");

            if (Files.isRegularFile(release)) {

                try {
                    for (String line : Files.readAllLines(release)) {

                        if (line.startsWith("JAVA_VERSION=")) {
                            version = unquote(line.substring("JAVA_VERSION=".length()));

                        } else if (line.startsWith("IMPLEMENTOR=")) {
                            vendor = unquote(line.substring("IMPLEMENTOR=".length()));
                        }
                    }
                } catch (IOException ignored) {
                    // Fall through to the process check below.
                }
            }
        }

        // Fallback: ask java itself (e.g. launcher shims without a release file)
        if (version == null) {
            version = versionFromProcess(exe);
        }

        int major = version == null ? -1 : parseMajor(version);

        return new JavaInstall(exe, major, version == null ? "" : version, vendor);
    }

    private static String versionFromProcess(Path exe) {

        try {
            Process process = new ProcessBuilder(exe.toString(), "-version")
                    .redirectErrorStream(true)
                    .start();

            String output = new String(process.getInputStream().readAllBytes());

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }

            Matcher matcher = Pattern.compile("version \"([^\"]+)\"").matcher(output);

            if (matcher.find()) {
                return matcher.group(1);
            }

        } catch (Exception ignored) {
            // Couldn't run it.
        }

        return null;
    }

    private static String unquote(String value) {
        return value.trim().replace("\"", "");
    }

    /** "1.8.0_402" -> 8, "21.0.13" -> 21, "25" -> 25 */
    static int parseMajor(String version) {

        try {
            if (version.startsWith("1.")) {
                String[] parts = version.split("[._-]");
                return Integer.parseInt(parts[1]);
            }

            Matcher matcher = Pattern.compile("^(\\d+)").matcher(version);

            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }

        } catch (Exception ignored) {
            // Unknown format
        }

        return -1;
    }
}