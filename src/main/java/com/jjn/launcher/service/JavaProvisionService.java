package com.jjn.launcher.service;

import com.jjn.launcher.JavaDetector;
import com.jjn.launcher.LauncherSettings;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads and extracts Eclipse Adoptium Temurin JREs into ~/.jjnclient/runtimes
 * when the user does not already have a matching Java version installed.
 */
public class JavaProvisionService {

    public interface ProgressListener {
        void update(String message, double progress);
    }

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public JavaDetector.JavaInstall ensureJava(int majorVersion, ProgressListener listener) throws Exception {
        for (JavaDetector.JavaInstall install : JavaDetector.detect()) {
            if (install.major() == majorVersion) {
                return install;
            }
        }

        Path targetDir = LauncherSettings.runtimesDir().resolve("java-" + majorVersion);
        Path existingExe = targetDir.resolve("bin").resolve("java.exe");
        JavaDetector.JavaInstall existing = JavaDetector.inspect(existingExe);
        if (existing != null && existing.major() >= majorVersion) {
            return existing;
        }

        if (listener != null) {
            listener.update("Downloading Java " + majorVersion + " runtime (Adoptium)...", 0.05);
        }

        String arch = System.getProperty("os.arch", "").contains("64") ? "x64" : "x86";
        String url = "https://api.adoptium.net/v3/binary/latest/"
                + majorVersion + "/ga/windows/" + arch + "/jre/hotspot/normal/eclipse";

        Files.createDirectories(LauncherSettings.runtimesDir());
        Path zipPath = LauncherSettings.runtimesDir().resolve("java-" + majorVersion + ".zip.part");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "JJNClient/1.0")
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            throw new Exception("Failed to download Java " + majorVersion + " (HTTP " + response.statusCode() + ").");
        }

        long contentLength = response.headers()
                .firstValueAsLong("Content-Length")
                .orElse(-1L);

        try (InputStream in = response.body(); var out = Files.newOutputStream(zipPath)) {
            byte[] buffer = new byte[16384];
            long totalRead = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalRead += read;
                if (listener != null && contentLength > 0) {
                    double pct = 0.05 + 0.75 * Math.min(1.0, (double) totalRead / contentLength);
                    listener.update(
                            String.format("Downloading Java %d (%.1f / %.1f MB)...",
                                    majorVersion, totalRead / (1024.0 * 1024.0), contentLength / (1024.0 * 1024.0)),
                            pct
                    );
                }
            }
        }

        if (listener != null) {
            listener.update("Extracting Java " + majorVersion + " runtime...", 0.85);
        }

        Path stagingDir = LauncherSettings.runtimesDir().resolve("java-" + majorVersion + ".tmp");
        deleteDirectory(stagingDir);
        Files.createDirectories(stagingDir);

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                int slash = name.indexOf('/');
                if (slash < 0 || slash == name.length() - 1) {
                    zip.closeEntry();
                    continue;
                }
                String stripped = name.substring(slash + 1);
                Path outPath = stagingDir.resolve(stripped).normalize();
                if (!outPath.startsWith(stagingDir.normalize())) {
                    throw new Exception("Unsafe archive path in Java download.");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    Files.copy(zip, outPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        } finally {
            Files.deleteIfExists(zipPath);
        }

        deleteDirectory(targetDir);
        Files.move(stagingDir, targetDir, StandardCopyOption.REPLACE_EXISTING);

        JavaDetector.JavaInstall installed = JavaDetector.inspect(existingExe);
        if (installed == null) {
            throw new Exception("Downloaded Java " + majorVersion + " runtime could not be verified.");
        }

        if (listener != null) {
            listener.update("Java " + majorVersion + " ready.", 1.0);
        }

        return installed;
    }

    private static void deleteDirectory(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (Exception ignored) {
        }
    }
}
