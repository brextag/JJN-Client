package com.jjn.launcher.ui;

import com.jjn.launcher.JavaDetector;
import com.jjn.launcher.LauncherSettings;
import com.jjn.launcher.service.JavaProvisionService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Global launcher settings view: Java runtime management (including one-click
 * Adoptium JRE provisioning), memory allocation, custom JVM args, resolution,
 * and launcher window behavior.
 */
public class SettingsView {

    private final JavaProvisionService provisionService = new JavaProvisionService();

    public Node build(Stage primaryStage, LauncherSettings settings) {

        // JAVA CARD
        Label javaTitle = UiUtils.sectionTitle("Java Runtime");
        Label javaDescription = UiUtils.mutedLabel(
                "Automatic selects the matching Java for each Minecraft version (and downloads "
                        + "Adoptium JREs into ~/.jjnclient/runtimes if missing)."
        );

        ComboBox<JavaDetector.JavaInstall> javaBox = new ComboBox<>();
        javaBox.setPrefWidth(480);

        Label javaPathLabel = UiUtils.mutedLabel("");
        Label javaInfo = UiUtils.mutedLabel("");

        javaBox.valueProperty().addListener((obs, old, value) -> {
            if (value == null || value.javaExe() == null) {
                javaPathLabel.setText("");
            } else {
                javaPathLabel.setText(value.javaExe().toString());
            }
        });

        populateJavaBox(javaBox, javaInfo, settings);

        Button detect = UiUtils.secondaryButton("Detect again");
        Button browse = UiUtils.secondaryButton("Browse...");
        Button downloadJava25 = UiUtils.secondaryButton("Install Java 25");
        Button downloadJava21 = UiUtils.secondaryButton("Install Java 21");
        Button downloadJava17 = UiUtils.secondaryButton("Install Java 17");
        Button downloadJava8 = UiUtils.secondaryButton("Install Java 8");

        detect.setOnAction(e -> populateJavaBox(javaBox, javaInfo, settings));

        browse.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select java.exe");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Java executable", "java.exe", "javaw.exe")
            );

            File file = chooser.showOpenDialog(primaryStage);
            if (file == null) {
                return;
            }

            JavaDetector.JavaInstall chosen = JavaDetector.inspect(file.toPath());
            if (chosen == null) {
                UiUtils.showError("That file isn't a usable java.exe.");
                return;
            }

            JavaDetector.JavaInstall existing = null;
            for (JavaDetector.JavaInstall item : javaBox.getItems()) {
                if (item.javaExe() != null && item.javaExe().equals(chosen.javaExe())) {
                    existing = item;
                    break;
                }
            }

            if (existing == null) {
                javaBox.getItems().add(chosen);
                existing = chosen;
            }
            javaBox.setValue(existing);
        });

        downloadJava25.setOnAction(e -> downloadRuntime(25, javaBox, javaInfo, settings, downloadJava25));
        downloadJava21.setOnAction(e -> downloadRuntime(21, javaBox, javaInfo, settings, downloadJava21));
        downloadJava17.setOnAction(e -> downloadRuntime(17, javaBox, javaInfo, settings, downloadJava17));
        downloadJava8.setOnAction(e -> downloadRuntime(8, javaBox, javaInfo, settings, downloadJava8));

        HBox javaButtons = new HBox(10, detect, browse, downloadJava25, downloadJava21, downloadJava17, downloadJava8);
        javaButtons.setAlignment(Pos.CENTER_LEFT);

        VBox javaCard = UiUtils.card(
                javaTitle,
                javaDescription,
                javaBox,
                javaPathLabel,
                javaButtons,
                javaInfo
        );

        // MEMORY & JVM ARGS CARD
        Label ramTitle = UiUtils.sectionTitle("Memory & JVM Arguments");
        long systemMb = systemMemoryMb();

        int maxRam = systemMb > 0
                ? (int) Math.max(2048, ((systemMb - 2048) / 512) * 512)
                : 16384;

        int startRam = Math.min(
                Math.max(settings.ramMb, LauncherSettings.MIN_RAM_MB),
                maxRam
        );

        Slider ramSlider = new Slider(LauncherSettings.MIN_RAM_MB, maxRam, startRam);
        ramSlider.setMajorTickUnit(512);
        ramSlider.setMinorTickCount(0);
        ramSlider.setSnapToTicks(true);
        ramSlider.setPrefWidth(480);

        Label ramValue = new Label(formatRam(startRam));
        ramValue.setStyle("-fx-text-fill: #c7d2fe; -fx-font-size: 16px; -fx-font-weight: bold;");

        ramSlider.valueProperty().addListener((obs, old, value) ->
                ramValue.setText(formatRam(value.intValue()))
        );

        Label ramHint = UiUtils.mutedLabel(
                (systemMb > 0 ? "Your PC has " + formatRam((int) systemMb) + ". " : "")
                        + "4 GB is plenty for vanilla and light Fabric packs."
        );

        Label jvmLabel = UiUtils.mutedLabel("Global extra JVM arguments (optional):");
        TextField jvmArgsField = new TextField(settings.jvmArgs == null ? "" : settings.jvmArgs);
        jvmArgsField.getStyleClass().add("input-field");
        jvmArgsField.setPromptText("e.g. -XX:+UseG1GC");
        jvmArgsField.setMaxWidth(480);

        VBox ramCard = UiUtils.card(ramTitle, ramValue, ramSlider, ramHint, jvmLabel, jvmArgsField);

        // WINDOW & LAUNCHER BEHAVIOR CARD
        Label behaviorTitle = UiUtils.sectionTitle("Window & Behavior");

        TextField widthField = new TextField(settings.windowWidth > 0 ? String.valueOf(settings.windowWidth) : "");
        widthField.getStyleClass().add("input-field");
        widthField.setPromptText("Width (e.g. 1280)");
        widthField.setPrefWidth(140);

        TextField heightField = new TextField(settings.windowHeight > 0 ? String.valueOf(settings.windowHeight) : "");
        heightField.getStyleClass().add("input-field");
        heightField.setPromptText("Height (e.g. 720)");
        heightField.setPrefWidth(140);

        Label xLabel = new Label("×");
        xLabel.setStyle("-fx-text-fill: #94a3b8;");

        HBox resolutionRow = new HBox(10, widthField, xLabel, heightField);
        resolutionRow.setAlignment(Pos.CENTER_LEFT);

        CheckBox hideOnLaunchBox = new CheckBox("Minimize launcher while Minecraft is running");
        hideOnLaunchBox.setSelected(settings.hideOnLaunch);
        hideOnLaunchBox.setStyle("-fx-text-fill: #e2e8f0;");

        VBox behaviorCard = UiUtils.card(
                behaviorTitle,
                UiUtils.mutedLabel("Default game resolution (leave blank for Minecraft default):"),
                resolutionRow,
                hideOnLaunchBox
        );

        // SAVE BAR
        Label saveStatus = UiUtils.mutedLabel("");

        Button save = new Button("Save settings");
        save.getStyleClass().add("primary-button");
        save.setPadding(new Insets(11, 22, 11, 22));

        save.setOnAction(e -> {
            JavaDetector.JavaInstall chosen = javaBox.getValue();
            settings.javaPath = (chosen == null || chosen.javaExe() == null)
                    ? ""
                    : chosen.javaExe().toString();
            settings.ramMb = (int) (Math.round(ramSlider.getValue() / 512.0) * 512);
            settings.jvmArgs = jvmArgsField.getText().trim();
            settings.windowWidth = parsePositiveInt(widthField.getText());
            settings.windowHeight = parsePositiveInt(heightField.getText());
            settings.hideOnLaunch = hideOnLaunchBox.isSelected();

            try {
                settings.save();
                saveStatus.setText("Settings saved.");
            } catch (IOException ex) {
                UiUtils.showError("Could not save settings: " + ex.getMessage());
            }
        });

        HBox saveRow = new HBox(15, save, saveStatus);
        saveRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(18, javaCard, ramCard, behaviorCard, saveRow);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private void downloadRuntime(
            int major,
            ComboBox<JavaDetector.JavaInstall> javaBox,
            Label javaInfo,
            LauncherSettings settings,
            Button trigger
    ) {
        trigger.setDisable(true);
        javaInfo.setText("Downloading Java " + major + "...");

        Thread worker = new Thread(() -> {
            try {
                provisionService.ensureJava(major, (msg, prog) ->
                        Platform.runLater(() -> javaInfo.setText(msg)));

                Platform.runLater(() -> {
                    trigger.setDisable(false);
                    populateJavaBox(javaBox, javaInfo, settings);
                    javaInfo.setText("Java " + major + " installed and ready.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    trigger.setDisable(false);
                    javaInfo.setText("Java " + major + " download failed: " + ex.getMessage());
                });
            }
        }, "JJN-Java-Downloader");
        worker.setDaemon(true);
        worker.start();
    }

    private void populateJavaBox(
            ComboBox<JavaDetector.JavaInstall> box,
            Label info,
            LauncherSettings settings
    ) {
        List<JavaDetector.JavaInstall> items = new ArrayList<>();
        items.add(JavaDetector.AUTOMATIC);

        List<JavaDetector.JavaInstall> detected = JavaDetector.detect();
        items.addAll(detected);

        JavaDetector.JavaInstall selected = JavaDetector.AUTOMATIC;
        String saved = settings.javaPath;

        if (saved != null && !saved.isBlank()) {
            Path savedPath = Paths.get(saved);
            JavaDetector.JavaInstall match = null;

            for (JavaDetector.JavaInstall item : items) {
                if (item.javaExe() != null && item.javaExe().equals(savedPath)) {
                    match = item;
                    break;
                }
            }

            if (match == null) {
                JavaDetector.JavaInstall custom = JavaDetector.inspect(savedPath);
                if (custom != null) {
                    items.add(custom);
                    match = custom;
                }
            }

            if (match != null) {
                selected = match;
            }
        }

        box.setItems(FXCollections.observableArrayList(items));
        box.setValue(selected);

        String text = detected.size() + " Java installation(s) found.";
        if (saved != null && !saved.isBlank() && selected == JavaDetector.AUTOMATIC) {
            text += " Your saved Java was not found, so Automatic is selected.";
        }
        info.setText(text);
    }

    private long systemMemoryMb() {
        try {
            var os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
                return sun.getTotalMemorySize() / (1024 * 1024);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private String formatRam(int mb) {
        return String.format("%.1f GB  (%d MB)", mb / 1024.0, mb);
    }

    private static int parsePositiveInt(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(text.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
