
package com.jjn.launcher;

import com.google.gson.*;
import com.jjn.launcher.ui.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MinecraftInstaller extends Application {

    private static final String MANIFEST =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private static final String ASSETS =
            "https://resources.download.minecraft.net/";

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ComboBox<String> versionBox = new ComboBox<>();
    private final Label instanceLabel = new Label("Checking instance...");
    private final Label status = new Label("Loading Minecraft versions...");
    private final ProgressBar progress = new ProgressBar(0);

    private final Button installButton = new Button("Install Minecraft");

    private Path instanceFolder;
    private Path gameFolder;

    @Override
    public void start(Stage stage) {

        ThemeManager.ensureFontsLoaded();
        LauncherSettings settings = LauncherSettings.load();

        Label logo = new Label("JJN");
        logo.getStyleClass().add("brand-title");

        Label heading = new Label("Minecraft Installer");
        heading.getStyleClass().add("page-title");

        Label subtitle = new Label(
                "Install official Minecraft into your JJN instance."
        );
        subtitle.getStyleClass().add("muted-text");

        instanceLabel.getStyleClass().add("accent-value");

        Label versionLabel = new Label("Minecraft Version");
        versionLabel.getStyleClass().add("field-label");

        versionBox.setPromptText("Select a version");
        versionBox.setMaxWidth(Double.MAX_VALUE);

        installButton.setMaxWidth(Double.MAX_VALUE);
        installButton.setPrefHeight(45);
        installButton.getStyleClass().add("primary-button");

        status.getStyleClass().add("muted-text");
        status.setWrapText(true);

        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setVisible(false);

        installButton.setOnAction(e -> installMinecraft());

        Label selectedInstLbl = new Label("Selected JJN instance");
        selectedInstLbl.getStyleClass().add("field-label");

        VBox content = new VBox(
                17,
                logo,
                heading,
                subtitle,
                new Separator(),
                selectedInstLbl,
                instanceLabel,
                versionLabel,
                versionBox,
                installButton,
                progress,
                status
        );

        content.setPadding(new Insets(35));
        content.setAlignment(Pos.CENTER_LEFT);

        Scene scene = new Scene(content, 540, 510);
        ThemeManager.apply(scene, settings);

        stage.setTitle("JJN Client - Minecraft Installer");
        stage.setScene(scene);
        stage.setMinWidth(450);
        stage.setMinHeight(450);
        stage.show();

        loadSelectedInstance();
        loadVersions();
    }

    // SELECTED INSTANCE

    private void loadSelectedInstance() {

        try {
            Path instances = Paths.get(
                    System.getProperty("user.home"),
                    ".jjnclient",
                    "instances"
            );

            Path selectedFile = instances.resolve("selected.txt");

            if (!Files.exists(selectedFile)) {
                throw new Exception("No selected instance found.");
            }

            String id = Files.readString(selectedFile).trim();

            if (id.isEmpty() || id.contains("/") || id.contains("\\")) {
                throw new Exception("Invalid selected instance ID.");
            }

            instanceFolder = instances.resolve(id).normalize();

            if (!instanceFolder.startsWith(instances.normalize()) ||
                    !Files.isDirectory(instanceFolder)) {
                throw new Exception("Selected instance folder does not exist.");
            }

            Path propertiesFile =
                    instanceFolder.resolve("instance.properties");

            Properties properties = new Properties();

            try (InputStream input =
                         Files.newInputStream(propertiesFile)) {
                properties.load(input);
            }

            String name = properties.getProperty("name", "Unknown");

            gameFolder = instanceFolder.resolve("game");

            instanceLabel.setText(name + "  (" + id.substring(0, 8) + ")");

        } catch (Exception e) {
            instanceLabel.setText("No valid instance selected");
            status.setText("Instance error: " + e.getMessage());
            installButton.setDisable(true);
        }
    }

    // MINECRAFT VERSIONS

    private void loadVersions() {

        Thread thread = new Thread(() -> {

            try {
                JsonObject manifest = getJson(MANIFEST);

                JsonArray versions = manifest.getAsJsonArray("versions");
                List<String> releases = new ArrayList<>();

                for (JsonElement element : versions) {
                    JsonObject version = element.getAsJsonObject();

                    if ("release".equals(
                            version.get("type").getAsString())) {
                        releases.add(version.get("id").getAsString());
                    }
                }

                Platform.runLater(() -> {
                    versionBox.setItems(
                            FXCollections.observableArrayList(releases)
                    );

                    if (!releases.isEmpty()) {
                        versionBox.setValue(releases.get(0));
                    }

                    if (instanceFolder != null) {
                        status.setText(
                                "Loaded " + releases.size() +
                                        " Minecraft releases."
                        );
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() ->
                        status.setText("Could not load versions: " + e.getMessage())
                );
            }

        });

        thread.setDaemon(true);
        thread.start();
    }

    // INSTALLATION

    private void installMinecraft() {

        String selectedVersion = versionBox.getValue();

        if (selectedVersion == null) {
            status.setText("Please select a Minecraft version.");
            return;
        }

        if (gameFolder == null || instanceFolder == null) {
            status.setText("No valid JJN instance selected.");
            return;
        }

        installButton.setDisable(true);
        versionBox.setDisable(true);
        progress.setVisible(true);
        progress.setProgress(0);

        Task<Void> task = new Task<>() {

            @Override
            protected Void call() throws Exception {

                updateMessage("Downloading version information...");

                JsonObject manifest = getJson(MANIFEST);
                JsonObject versionEntry = null;

                for (JsonElement element :
                        manifest.getAsJsonArray("versions")) {

                    JsonObject version = element.getAsJsonObject();

                    if (version.get("id").getAsString()
                            .equals(selectedVersion)) {
                        versionEntry = version;
                        break;
                    }
                }

                if (versionEntry == null) {
                    throw new Exception("Minecraft version not found.");
                }

                String versionUrl =
                        versionEntry.get("url").getAsString();

                JsonObject versionJson = getJson(versionUrl);

                Path versionFolder =
                        gameFolder.resolve("versions").resolve(selectedVersion);

                Files.createDirectories(versionFolder);

                Files.writeString(
                        versionFolder.resolve(selectedVersion + ".json"),
                        new GsonBuilder().setPrettyPrinting()
                                .create().toJson(versionJson)
                );

                List<DownloadItem> downloads = new ArrayList<>();
                List<Path> nativeJars = new ArrayList<>();

                // CLIENT JAR

                JsonObject downloadsJson =
                        versionJson.getAsJsonObject("downloads");

                if (downloadsJson == null ||
                        !downloadsJson.has("client")) {
                    throw new Exception("Client download is missing.");
                }

                JsonObject clientInfo =
                        downloadsJson.getAsJsonObject("client");

                downloads.add(new DownloadItem(
                        clientInfo.get("url").getAsString(),
                        versionFolder.resolve(selectedVersion + ".jar"),
                        getString(clientInfo, "sha1"),
                        getLong(clientInfo, "size")
                ));

                // LIBRARIES

                JsonArray libraries = versionJson.getAsJsonArray("libraries");

                if (libraries != null) {
                    for (JsonElement element : libraries) {

                        JsonObject library = element.getAsJsonObject();

                        if (!isAllowed(library)) {
                            continue;
                        }

                        JsonObject libraryDownloads =
                                library.getAsJsonObject("downloads");

                        if (libraryDownloads == null) {
                            continue;
                        }

                        if (libraryDownloads.has("artifact")) {

                            JsonObject artifact =
                                    libraryDownloads.getAsJsonObject("artifact");

                            Path destination = safeResolve(
                                    gameFolder.resolve("libraries"),
                                    artifact.get("path").getAsString()
                            );

                            downloads.add(new DownloadItem(
                                    artifact.get("url").getAsString(),
                                    destination,
                                    getString(artifact, "sha1"),
                                    getLong(artifact, "size")
                            ));
                        }

                        // WINDOWS NATIVE LIBRARIES

                        JsonObject natives = library.getAsJsonObject("natives");

                        if (natives != null && natives.has("windows")) {

                            String classifier = natives
                                    .get("windows").getAsString()
                                    .replace("${arch}",
                                            System.getProperty("os.arch")
                                                    .contains("64") ? "64" : "32");

                            JsonObject classifiers =
                                    libraryDownloads.getAsJsonObject("classifiers");

                            if (classifiers != null &&
                                    classifiers.has(classifier)) {

                                JsonObject nativeInfo =
                                        classifiers.getAsJsonObject(classifier);

                                Path nativePath = safeResolve(
                                        gameFolder.resolve("libraries"),
                                        nativeInfo.get("path").getAsString()
                                );

                                downloads.add(new DownloadItem(
                                        nativeInfo.get("url").getAsString(),
                                        nativePath,
                                        getString(nativeInfo, "sha1"),
                                        getLong(nativeInfo, "size")
                                ));

                                nativeJars.add(nativePath);
                            }
                        }
                    }
                }

                // ASSET INDEX AND ASSETS

                JsonObject assetIndex =
                        versionJson.getAsJsonObject("assetIndex");

                if (assetIndex != null) {

                    String indexId =
                            assetIndex.get("id").getAsString();

                    Path indexesFolder =
                            gameFolder.resolve("assets").resolve("indexes");

                    Files.createDirectories(indexesFolder);

                    Path indexPath =
                            indexesFolder.resolve(indexId + ".json");

                    downloads.add(new DownloadItem(
                            assetIndex.get("url").getAsString(),
                            indexPath,
                            getString(assetIndex, "sha1"),
                            getLong(assetIndex, "size")
                    ));

                    JsonObject indexJson = getJson(
                            assetIndex.get("url").getAsString()
                    );

                    JsonObject objects =
                            indexJson.getAsJsonObject("objects");

                    for (Map.Entry<String, JsonElement> entry :
                            objects.entrySet()) {

                        JsonObject object = entry.getValue().getAsJsonObject();

                        String hash = object.get("hash").getAsString();

                        Path objectPath = gameFolder
                                .resolve("assets")
                                .resolve("objects")
                                .resolve(hash.substring(0, 2))
                                .resolve(hash);

                        downloads.add(new DownloadItem(
                                ASSETS + hash.substring(0, 2) + "/" + hash,
                                objectPath,
                                hash,
                                getLong(object, "size")
                        ));
                    }
                }

                // LOGGING CONFIGURATION

                JsonObject logging = versionJson.getAsJsonObject("logging");

                if (logging != null && logging.has("client")) {

                    JsonObject clientLogging =
                            logging.getAsJsonObject("client");

                    JsonObject file =
                            clientLogging.getAsJsonObject("file");

                    if (file != null) {

                        String fileId = file.get("id").getAsString();

                        downloads.add(new DownloadItem(
                                file.get("url").getAsString(),
                                gameFolder.resolve("assets")
                                        .resolve("log_configs")
                                        .resolve(fileId),
                                getString(file, "sha1"),
                                getLong(file, "size")
                        ));
                    }
                }

                // DOWNLOAD EVERYTHING

                int total = downloads.size();

                if (total == 0) {
                    throw new Exception("No downloads were found.");
                }

                int completed = 0;

                for (DownloadItem item : downloads) {

                    updateMessage(
                            "Downloading " + (completed + 1) +
                                    " of " + total + " files..."
                    );

                    downloadFile(item);

                    completed++;
                    updateProgress(completed, total);
                }

                // EXTRACT WINDOWS NATIVES

                updateMessage("Extracting Windows native libraries...");

                Path nativesFolder = gameFolder.resolve("natives");
                Files.createDirectories(nativesFolder);

                for (Path nativeJar : nativeJars) {
                    extractNative(nativeJar, nativesFolder);
                }

                updateProgress(total, total);
                updateMessage("Minecraft " + selectedVersion + " installed!");

                return null;
            }
        };

        status.textProperty().bind(task.messageProperty());
        progress.progressProperty().bind(task.progressProperty());

        task.setOnSucceeded(e -> {
            status.textProperty().unbind();
            progress.progressProperty().unbind();

            status.setText(
                    "Successfully installed Minecraft " + selectedVersion +
                            " into your JJN instance!"
            );

            progress.setProgress(1);
            installButton.setDisable(false);
            versionBox.setDisable(false);
        });

        task.setOnFailed(e -> {
            status.textProperty().unbind();
            progress.progressProperty().unbind();

            Throwable error = task.getException();

            status.setText(
                    "Installation failed: " +
                            (error == null ? "Unknown error" : error.getMessage())
            );

            installButton.setDisable(false);
            versionBox.setDisable(false);
        });

        Thread worker = new Thread(task, "JJN-Minecraft-Installer");
        worker.setDaemon(true);
        worker.start();
    }

    // DOWNLOAD HELPERS

    private JsonObject getJson(String url) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "JJNClient/1.0")
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new Exception(
                    "HTTP " + response.statusCode() + " while loading metadata."
            );
        }

        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private void downloadFile(DownloadItem item) throws Exception {

        Files.createDirectories(item.destination().getParent());

        if (Files.exists(item.destination())) {

            boolean valid = true;

            if (item.sha1() != null && !item.sha1().isBlank()) {
                valid = sha1(item.destination()).equalsIgnoreCase(item.sha1());
            } else if (item.size() > 0) {
                valid = Files.size(item.destination()) == item.size();
            }

            if (valid) {
                return;
            }
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(item.url()))
                .header("User-Agent", "JJNClient/1.0")
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );

        if (response.statusCode() != 200) {
            response.body().close();
            throw new Exception(
                    "Download failed: HTTP " + response.statusCode()
            );
        }

        Path temporary = item.destination().resolveSibling(
                item.destination().getFileName() + ".part"
        );

        try (InputStream input = response.body()) {
            Files.copy(input, temporary,
                    StandardCopyOption.REPLACE_EXISTING);
        }

        if (item.size() > 0 && Files.size(temporary) != item.size()) {
            Files.deleteIfExists(temporary);
            throw new Exception("Downloaded file has an incorrect size.");
        }

        if (item.sha1() != null && !item.sha1().isBlank()) {
            if (!sha1(temporary).equalsIgnoreCase(item.sha1())) {
                Files.deleteIfExists(temporary);
                throw new Exception("Downloaded file failed SHA-1 verification.");
            }
        }

        Files.move(
                temporary,
                item.destination(),
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    private String sha1(Path file) throws Exception {

        MessageDigest digest = MessageDigest.getInstance("SHA-1");

        try (InputStream input = Files.newInputStream(file)) {

            byte[] buffer = new byte[8192];
            int read;

            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    // WINDOWS LIBRARY RULES

    private boolean isAllowed(JsonObject library) {

        if (!library.has("rules")) {
            return true;
        }

        boolean allowed = false;
        JsonArray rules = library.getAsJsonArray("rules");

        for (JsonElement element : rules) {

            JsonObject rule = element.getAsJsonObject();

            if (!matchesWindows(rule)) {
                continue;
            }

            allowed = "allow".equals(
                    rule.get("action").getAsString()
            );
        }

        return allowed;
    }

    private boolean matchesWindows(JsonObject rule) {

        if (!rule.has("os")) {
            return true;
        }

        JsonObject os = rule.getAsJsonObject("os");

        if (os.has("name") &&
                !"windows".equals(os.get("name").getAsString())) {
            return false;
        }

        if (os.has("arch")) {
            String arch = os.get("arch").getAsString();

            if (!System.getProperty("os.arch").matches(arch)) {
                return false;
            }
        }

        if (os.has("version")) {
            String regex = os.get("version").getAsString();

            if (!System.getProperty("os.version").matches(regex)) {
                return false;
            }
        }

        return true;
    }

    // NATIVE EXTRACTION

    private void extractNative(Path jar, Path destination)
            throws Exception {

        try (ZipInputStream zip = new ZipInputStream(
                Files.newInputStream(jar))) {

            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {

                if (entry.isDirectory() ||
                        entry.getName().startsWith("META-INF/")) {
                    continue;
                }

                Path output = destination.resolve(entry.getName())
                        .normalize();

                if (!output.startsWith(destination.normalize())) {
                    throw new Exception("Unsafe native archive entry.");
                }

                Files.createDirectories(output.getParent());

                Files.copy(
                        zip,
                        output,
                        StandardCopyOption.REPLACE_EXISTING
                );

                zip.closeEntry();
            }
        }
    }

    // SAFE PATH HELPERS

    private Path safeResolve(Path root, String relative) throws Exception {

        Path result = root.resolve(relative).normalize();

        if (!result.startsWith(root.normalize())) {
            throw new Exception("Unsafe download path.");
        }

        return result;
    }

    private String getString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString()
                : null;
    }

    private long getLong(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsLong()
                : 0;
    }

    private record DownloadItem(
            String url,
            Path destination,
            String sha1,
            long size
    ) {}

    public static void main(String[] args) {
        launch(args);
    }
}
