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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Global launcher settings view: UI customization (themes, custom colors,
 * tactile button styles, corner radius, official Minecraft font), Java runtime
 * management, memory allocation, custom JVM args, resolution, and behavior.
 */
public class SettingsView {

    private final JavaProvisionService provisionService = new JavaProvisionService();

    public Node build(Stage primaryStage, LauncherSettings settings) {
        final boolean[] syncingUi = new boolean[]{false};

        // 1. APPEARANCE & THEME CUSTOMIZATION CARD
        Label themeTitle = UiUtils.sectionTitle("Appearance & Theme");
        Label themeDescription = UiUtils.mutedLabel(
                "Default palette is Blue, White & Black. Pick a preset or customize every color live."
        );

        Label activePresetBadge = new Label();
        activePresetBadge.getStyleClass().add("badge-accent");

        Button resetUiBtn = UiUtils.secondaryButton("Reset UI Defaults");

        Region themeHeaderGap = new Region();
        HBox.setHgrow(themeHeaderGap, Priority.ALWAYS);
        HBox themeHeaderRow = new HBox(10, themeTitle, activePresetBadge, themeHeaderGap, resetUiBtn);
        themeHeaderRow.setAlignment(Pos.CENTER_LEFT);

        // Preset buttons with color swatches
        FlowPane presetFlow = new FlowPane(8, 8);
        Map<String, ToggleButton> presetButtons = new LinkedHashMap<>();
        ToggleGroup presetGroup = new ToggleGroup();

        // Custom color pickers
        ColorPicker accentPicker = createColorPicker(settings.uiAccentColor, LauncherSettings.DEFAULT_ACCENT_COLOR);
        ColorPicker bgPicker = createColorPicker(settings.uiBgColor, LauncherSettings.DEFAULT_BG_COLOR);
        ColorPicker surfacePicker = createColorPicker(settings.uiSurfaceColor, LauncherSettings.DEFAULT_SURFACE_COLOR);
        ColorPicker textPicker = createColorPicker(settings.uiTextColor, LauncherSettings.DEFAULT_TEXT_COLOR);

        Label uiSaveHint = UiUtils.mutedLabel("");

        Runnable applyAndPersistUi = () -> {
            if (primaryStage != null && primaryStage.getScene() != null) {
                ThemeManager.apply(primaryStage.getScene(), settings);
            } else {
                ThemeManager.refresh();
            }
            try {
                settings.save();
                uiSaveHint.setText("Live UI updated & saved.");
            } catch (IOException ex) {
                uiSaveHint.setText("Applied live (could not save: " + ex.getMessage() + ")");
            }
        };

        Runnable syncPresetSelection = () -> {
            syncingUi[0] = true;
            ThemeManager.ThemePreset matched = ThemeManager.findPreset(settings.uiThemePreset);
            if (matched != null) {
                activePresetBadge.setText(matched.name().toUpperCase());
                ToggleButton btn = presetButtons.get(matched.id());
                if (btn != null) {
                    btn.setSelected(true);
                }
            } else {
                activePresetBadge.setText("CUSTOM THEME");
                presetGroup.selectToggle(null);
            }
            accentPicker.setValue(safeColor(settings.uiAccentColor, LauncherSettings.DEFAULT_ACCENT_COLOR));
            bgPicker.setValue(safeColor(settings.uiBgColor, LauncherSettings.DEFAULT_BG_COLOR));
            surfacePicker.setValue(safeColor(settings.uiSurfaceColor, LauncherSettings.DEFAULT_SURFACE_COLOR));
            textPicker.setValue(safeColor(settings.uiTextColor, LauncherSettings.DEFAULT_TEXT_COLOR));
            syncingUi[0] = false;
        };

        for (ThemeManager.ThemePreset preset : ThemeManager.PRESETS) {
            ToggleButton btn = new ToggleButton(preset.name());
            btn.getStyleClass().add("pill-toggle");
            btn.setToggleGroup(presetGroup);
            btn.setGraphic(buildPresetSwatch(preset));
            btn.setGraphicTextGap(8);
            btn.setOnAction(e -> {
                if (!btn.isSelected()) {
                    btn.setSelected(true);
                }
                if (syncingUi[0]) {
                    return;
                }
                settings.uiThemePreset = preset.id();
                settings.uiAccentColor = preset.accentHex();
                settings.uiBgColor = preset.bgHex();
                settings.uiSurfaceColor = preset.surfaceHex();
                settings.uiTextColor = preset.textHex();
                syncPresetSelection.run();
                applyAndPersistUi.run();
            });
            presetButtons.put(preset.id(), btn);
            presetFlow.getChildren().add(btn);
        }

        GridPane colorGrid = new GridPane();
        colorGrid.setHgap(12);
        colorGrid.setVgap(10);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(25);
            colorGrid.getColumnConstraints().add(col);
        }
        colorGrid.add(colorFieldBox("Accent / Buttons", accentPicker), 0, 0);
        colorGrid.add(colorFieldBox("Background", bgPicker), 1, 0);
        colorGrid.add(colorFieldBox("Cards & Sidebar", surfacePicker), 2, 0);
        colorGrid.add(colorFieldBox("Primary Text", textPicker), 3, 0);

        Runnable onCustomColorChanged = () -> {
            if (syncingUi[0]) {
                return;
            }
            settings.uiThemePreset = "custom";
            settings.uiAccentColor = ThemeManager.hex(accentPicker.getValue());
            settings.uiBgColor = ThemeManager.hex(bgPicker.getValue());
            settings.uiSurfaceColor = ThemeManager.hex(surfacePicker.getValue());
            settings.uiTextColor = ThemeManager.hex(textPicker.getValue());
            syncPresetSelection.run();
            applyAndPersistUi.run();
        };
        accentPicker.setOnAction(e -> onCustomColorChanged.run());
        bgPicker.setOnAction(e -> onCustomColorChanged.run());
        surfacePicker.setOnAction(e -> onCustomColorChanged.run());
        textPicker.setOnAction(e -> onCustomColorChanged.run());

        VBox themeCard = UiUtils.card(
                themeHeaderRow,
                themeDescription,
                presetFlow,
                colorGrid
        );

        // 2. BUTTONS, CORNERS & MINECRAFT FONT CARD
        Label styleTitle = UiUtils.sectionTitle("Buttons, Layout & Official Minecraft Font");

        // Button style switcher
        ToggleGroup btnStyleGroup = new ToggleGroup();
        ToggleButton mcBevelBtn = new ToggleButton("MC 3D Bevel");
        ToggleButton borderedBtn = new ToggleButton("Crisp Bordered");
        ToggleButton pillBtn = new ToggleButton("Modern Pill");
        Map<String, ToggleButton> styleMap = Map.of(
                "mc-bevel", mcBevelBtn,
                "bordered", borderedBtn,
                "pill", pillBtn
        );
        for (var entry : styleMap.entrySet()) {
            ToggleButton tb = entry.getValue();
            tb.getStyleClass().add("pill-toggle");
            tb.setToggleGroup(btnStyleGroup);
            tb.setOnAction(e -> {
                if (!tb.isSelected()) tb.setSelected(true);
                if (syncingUi[0]) return;
                settings.uiButtonStyle = entry.getKey();
                applyAndPersistUi.run();
            });
        }
        HBox btnStyleBar = new HBox(4, mcBevelBtn, borderedBtn, pillBtn);
        btnStyleBar.getStyleClass().add("segmented-group");

        // Corner roundness switcher
        ToggleGroup cornerGroup = new ToggleGroup();
        ToggleButton sharpCornerBtn = new ToggleButton("Sharp (0px)");
        ToggleButton crispCornerBtn = new ToggleButton("Crisp (4px)");
        ToggleButton smoothCornerBtn = new ToggleButton("Smooth (10px)");
        ToggleButton roundCornerBtn = new ToggleButton("Rounded (14px)");
        Map<Integer, ToggleButton> cornerMap = new LinkedHashMap<>();
        cornerMap.put(0, sharpCornerBtn);
        cornerMap.put(4, crispCornerBtn);
        cornerMap.put(10, smoothCornerBtn);
        cornerMap.put(14, roundCornerBtn);
        for (var entry : cornerMap.entrySet()) {
            ToggleButton tb = entry.getValue();
            tb.getStyleClass().add("pill-toggle");
            tb.setToggleGroup(cornerGroup);
            tb.setOnAction(e -> {
                if (!tb.isSelected()) tb.setSelected(true);
                if (syncingUi[0]) return;
                settings.uiCornerRadius = entry.getKey();
                applyAndPersistUi.run();
            });
        }
        HBox cornerBar = new HBox(4, sharpCornerBtn, crispCornerBtn, smoothCornerBtn, roundCornerBtn);
        cornerBar.getStyleClass().add("segmented-group");

        // Font scale switcher
        ToggleGroup fontScaleGroup = new ToggleGroup();
        ToggleButton scale90Btn = new ToggleButton("90%");
        ToggleButton scale100Btn = new ToggleButton("100%");
        ToggleButton scale110Btn = new ToggleButton("110%");
        Map<Double, ToggleButton> scaleMap = new LinkedHashMap<>();
        scaleMap.put(0.9, scale90Btn);
        scaleMap.put(1.0, scale100Btn);
        scaleMap.put(1.1, scale110Btn);
        for (var entry : scaleMap.entrySet()) {
            ToggleButton tb = entry.getValue();
            tb.getStyleClass().add("pill-toggle");
            tb.setToggleGroup(fontScaleGroup);
            tb.setOnAction(e -> {
                if (!tb.isSelected()) tb.setSelected(true);
                if (syncingUi[0]) return;
                settings.uiFontScale = entry.getKey();
                applyAndPersistUi.run();
            });
        }
        HBox fontScaleBar = new HBox(4, scale90Btn, scale100Btn, scale110Btn);
        fontScaleBar.getStyleClass().add("segmented-group");

        // Font & layout checkboxes
        CheckBox mcFontCheck = new CheckBox("Use Official Minecraft Font across the launcher");
        mcFontCheck.setOnAction(e -> {
            if (syncingUi[0]) return;
            settings.uiUseMinecraftFont = mcFontCheck.isSelected();
            applyAndPersistUi.run();
        });

        CheckBox heroGlowCheck = new CheckBox("Blue Glow & Gradient on Home Hero Banner");
        heroGlowCheck.setOnAction(e -> {
            if (syncingUi[0]) return;
            settings.uiHeroGlow = heroGlowCheck.isSelected();
            applyAndPersistUi.run();
        });

        CheckBox compactUiCheck = new CheckBox("Compact Spacing (tighter sidebar & cards)");
        compactUiCheck.setOnAction(e -> {
            if (syncingUi[0]) return;
            settings.uiCompactSpacing = compactUiCheck.isSelected();
            applyAndPersistUi.run();
        });

        Runnable syncLayoutControls = () -> {
            syncingUi[0] = true;
            ToggleButton styleBtn = styleMap.getOrDefault(
                    settings.uiButtonStyle == null ? "mc-bevel" : settings.uiButtonStyle.toLowerCase(),
                    mcBevelBtn
            );
            styleBtn.setSelected(true);

            int nearestCorner = 4;
            int bestDiff = Integer.MAX_VALUE;
            for (int key : cornerMap.keySet()) {
                int diff = Math.abs(key - settings.uiCornerRadius);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    nearestCorner = key;
                }
            }
            cornerMap.get(nearestCorner).setSelected(true);

            double nearestScale = 1.0;
            double bestScaleDiff = Double.MAX_VALUE;
            for (double key : scaleMap.keySet()) {
                double diff = Math.abs(key - settings.uiFontScale);
                if (diff < bestScaleDiff) {
                    bestScaleDiff = diff;
                    nearestScale = key;
                }
            }
            scaleMap.get(nearestScale).setSelected(true);

            mcFontCheck.setSelected(settings.uiUseMinecraftFont);
            heroGlowCheck.setSelected(settings.uiHeroGlow);
            compactUiCheck.setSelected(settings.uiCompactSpacing);
            syncingUi[0] = false;
        };

        resetUiBtn.setOnAction(e -> {
            settings.resetUiDefaults();
            syncPresetSelection.run();
            syncLayoutControls.run();
            applyAndPersistUi.run();
        });

        syncPresetSelection.run();
        syncLayoutControls.run();

        HBox controlsRow1 = new HBox(
                18,
                new VBox(5, UiUtils.mutedLabel("Button Style"), btnStyleBar),
                new VBox(5, UiUtils.mutedLabel("Text Scale"), fontScaleBar)
        );
        controlsRow1.setAlignment(Pos.CENTER_LEFT);

        VBox styleCard = UiUtils.card(
                styleTitle,
                controlsRow1,
                new VBox(5, UiUtils.mutedLabel("Corner Roundness"), cornerBar),
                mcFontCheck,
                heroGlowCheck,
                compactUiCheck,
                uiSaveHint
        );

        // 3. JAVA CARD
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

        FlowPane javaButtons = new FlowPane(8, 8, detect, browse, downloadJava25, downloadJava21, downloadJava17, downloadJava8);

        VBox javaCard = UiUtils.card(
                javaTitle,
                javaDescription,
                javaBox,
                javaPathLabel,
                javaButtons,
                javaInfo
        );

        // 4. MEMORY & JVM ARGS CARD
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
        ramValue.getStyleClass().add("accent-value");

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

        // 5. WINDOW & LAUNCHER BEHAVIOR CARD
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
        xLabel.getStyleClass().add("muted-text");

        HBox resolutionRow = new HBox(10, widthField, xLabel, heightField);
        resolutionRow.setAlignment(Pos.CENTER_LEFT);

        CheckBox hideOnLaunchBox = new CheckBox("Minimize launcher while Minecraft is running");
        hideOnLaunchBox.setSelected(settings.hideOnLaunch);

        VBox behaviorCard = UiUtils.card(
                behaviorTitle,
                UiUtils.mutedLabel("Default game resolution (leave blank for Minecraft default):"),
                resolutionRow,
                hideOnLaunchBox
        );

        // SAVE BAR
        Label saveStatus = UiUtils.mutedLabel("");

        Button save = new Button("Save all settings");
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
                if (primaryStage != null && primaryStage.getScene() != null) {
                    ThemeManager.apply(primaryStage.getScene(), settings);
                }
                saveStatus.setText("All settings saved.");
            } catch (IOException ex) {
                UiUtils.showError("Could not save settings: " + ex.getMessage());
            }
        });

        HBox saveRow = new HBox(15, save, saveStatus);
        saveRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(16, themeCard, styleCard, javaCard, ramCard, behaviorCard, saveRow);
        content.setPadding(new Insets(0, 6, 12, 0));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private static ColorPicker createColorPicker(String hex, String fallback) {
        ColorPicker picker = new ColorPicker(safeColor(hex, fallback));
        picker.setMaxWidth(Double.MAX_VALUE);
        return picker;
    }

    private static Color safeColor(String hex, String fallback) {
        try {
            return Color.web(hex == null || hex.isBlank() ? fallback : hex.trim());
        } catch (Exception ignored) {
            return Color.web(fallback);
        }
    }

    private static VBox colorFieldBox(String title, ColorPicker picker) {
        Label lbl = UiUtils.mutedLabel(title);
        return new VBox(5, lbl, picker);
    }

    private static HBox buildPresetSwatch(ThemeManager.ThemePreset preset) {
        Rectangle bgDot = new Rectangle(10, 10, safeColor(preset.bgHex(), "#05070B"));
        bgDot.setArcWidth(3);
        bgDot.setArcHeight(3);
        bgDot.setStroke(Color.web("#8FA6CB"));
        bgDot.setStrokeWidth(0.8);

        Rectangle accentDot = new Rectangle(10, 10, safeColor(preset.accentHex(), "#2563EB"));
        accentDot.setArcWidth(3);
        accentDot.setArcHeight(3);

        Rectangle textDot = new Rectangle(10, 10, safeColor(preset.textHex(), "#FFFFFF"));
        textDot.setArcWidth(3);
        textDot.setArcHeight(3);
        textDot.setStroke(Color.web("#8FA6CB"));
        textDot.setStrokeWidth(0.8);

        HBox box = new HBox(2, bgDot, accentDot, textDot);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
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
