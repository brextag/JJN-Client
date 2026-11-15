package com.jjn.launcher.ui;

import com.jjn.launcher.LauncherSettings;
import com.jjn.launcher.model.Instance;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Instance management view: create, select, rename, delete, open folder,
 * manage mods (.jar / .jar.disabled), and configure per-instance overrides.
 */
public class InstancesView {

    public interface Actions {
        List<Instance> instances();
        Instance selectedInstance();
        void selectInstance(Instance instance);
        void installOrChangeLoader(Instance instance);
        void createInstance();
        void renameInstance(Instance instance);
        void deleteInstance(Instance instance);
        void refresh();
    }

    public Node build(Stage primaryStage, Actions actions) {
        List<Instance> instances = actions.instances();
        Instance selectedInstance = actions.selectedInstance();

        Label description = UiUtils.mutedLabel(
                "Manage separate Minecraft instances, choose mod loaders (Fabric, Forge, NeoForge, Quilt, Vanilla), and configure mods."
        );

        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label count = new Label(instances.size() + " instance(s)");
        count.getStyleClass().add("muted-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button create = new Button("+  Create instance");
        create.getStyleClass().add("primary-button");
        create.setPadding(new Insets(10, 16, 10, 16));
        create.setOnAction(e -> actions.createInstance());

        topRow.getChildren().addAll(count, spacer, create);

        ListView<Instance> list = new ListView<>(
                FXCollections.observableArrayList(instances)
        );
        list.setPrefHeight(460);
        VBox.setVgrow(list, Priority.ALWAYS);
        list.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-control-inner-background: transparent;" +
                        "-fx-background-insets: 0;"
        );

        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Instance instance, boolean empty) {
                super.updateItem(instance, empty);

                if (empty || instance == null) {
                    setGraphic(null);
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }

                Label name = new Label(instance.name);
                name.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 16px; -fx-font-weight: bold;");

                Label info = new Label(instance.displaySummary());
                info.getStyleClass().add("muted-text");

                HBox titleRow = new HBox(8, name);
                titleRow.setAlignment(Pos.CENTER_LEFT);

                if (instance == selectedInstance) {
                    Label badge = new Label("SELECTED");
                    badge.setStyle(
                            "-fx-background-color: #292d3b;" +
                                    "-fx-text-fill: #c7d2fe;" +
                                    "-fx-font-size: 10px;" +
                                    "-fx-font-weight: bold;" +
                                    "-fx-padding: 3 8 3 8;" +
                                    "-fx-background-radius: 999;"
                    );
                    titleRow.getChildren().add(badge);
                }

                if (instance.isInstalled()) {
                    boolean hasJjnUi = com.jjn.launcher.service.ClientModBundler.isSupported(instance);
                    String loaderText = hasJjnUi
                            ? "FABRIC • JJN UI"
                            : instance.loaderDisplayName().toUpperCase();
                    Label loaderBadge = new Label(loaderText);
                    loaderBadge.setStyle(
                            "-fx-background-color: #202432;" +
                                    "-fx-text-fill: " + (hasJjnUi ? "#c7d2fe" : "#a2a6b5") + ";" +
                                    "-fx-font-size: 10px;" +
                                    "-fx-font-weight: bold;" +
                                    "-fx-padding: 3 8 3 8;" +
                                    "-fx-background-radius: 999;"
                    );
                    titleRow.getChildren().add(loaderBadge);
                }

                VBox details = new VBox(5, titleRow, info);

                Button selectBtn = UiUtils.secondaryButton("Select");
                selectBtn.setDisable(instance == selectedInstance);
                selectBtn.setOnAction(e -> actions.selectInstance(instance));

                Button loaderBtn = UiUtils.secondaryButton("Loader / Version");
                loaderBtn.setOnAction(e -> actions.installOrChangeLoader(instance));

                Button modsBtn = UiUtils.secondaryButton("Mods");
                modsBtn.setOnAction(e -> openModsDialog(primaryStage, instance));

                Button configBtn = UiUtils.secondaryButton("Configure");
                configBtn.setOnAction(e -> openConfigureDialog(instance, actions::refresh));

                Button folderBtn = UiUtils.secondaryButton("Folder");
                folderBtn.setOnAction(e -> UiUtils.openInExplorer(instance.gameFolder()));

                Button renameBtn = UiUtils.secondaryButton("Rename");
                renameBtn.setOnAction(e -> actions.renameInstance(instance));

                Button deleteBtn = UiUtils.dangerButton("Delete");
                deleteBtn.setOnAction(e -> actions.deleteInstance(instance));

                HBox actionsRow = new HBox(7, selectBtn, loaderBtn, modsBtn, configBtn, folderBtn, renameBtn, deleteBtn);
                actionsRow.setAlignment(Pos.CENTER_RIGHT);

                Region gap = new Region();
                HBox.setHgrow(gap, Priority.ALWAYS);

                HBox card = new HBox(15, details, gap, actionsRow);
                card.setAlignment(Pos.CENTER_LEFT);
                card.setPadding(new Insets(18));
                card.getStyleClass().add("panel-card");

                setGraphic(card);
                setText(null);
                setPadding(new Insets(5, 0, 5, 0));
                setStyle("-fx-background-color: transparent;");
            }
        });

        VBox container = new VBox(16, description, topRow, list);
        VBox.setVgrow(container, Priority.ALWAYS);
        return container;
    }

    private void openModsDialog(Stage primaryStage, Instance instance) {
        Path modsDir = instance.modsFolder();
        try {
            Files.createDirectories(modsDir);
        } catch (IOException ex) {
            UiUtils.showError("Could not access mods folder: " + ex.getMessage());
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Mods - " + instance.name);
        dialog.setHeaderText("Manage mods in " + instance.name);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        UiUtils.styleDialog(dialog);

        ListView<Path> modList = new ListView<>();
        modList.setPrefSize(520, 320);
        modList.setStyle("-fx-control-inner-background: #101116; -fx-background-color: #101116;");

        Supplier<List<Path>> scanMods = () -> {
            List<Path> found = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir)) {
                for (Path entry : stream) {
                    String fileName = entry.getFileName().toString().toLowerCase();
                    if (Files.isRegularFile(entry) && (fileName.endsWith(".jar") || fileName.endsWith(".jar.disabled"))) {
                        found.add(entry);
                    }
                }
            } catch (IOException ignored) {
            }
            found.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));
            return found;
        };

        Runnable reload = () -> modList.setItems(FXCollections.observableArrayList(scanMods.get()));
        reload.run();

        modList.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Path modPath, boolean empty) {
                super.updateItem(modPath, empty);
                if (empty || modPath == null) {
                    setGraphic(null);
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }

                String fileName = modPath.getFileName().toString();
                boolean disabled = fileName.endsWith(".disabled");
                String displayName = disabled
                        ? fileName.substring(0, fileName.length() - ".disabled".length())
                        : fileName;

                Label nameLbl = new Label(displayName);
                nameLbl.setStyle("-fx-text-fill: " + (disabled ? "#64748b" : "#f8fafc") + "; -fx-font-weight: bold;");

                Label stateLbl = new Label(disabled ? "DISABLED" : "ENABLED");
                stateLbl.setStyle("-fx-text-fill: " + (disabled ? "#f59e0b" : "#34d399") + "; -fx-font-size: 10px; -fx-font-weight: bold;");

                Button toggleBtn = UiUtils.secondaryButton(disabled ? "Enable" : "Disable");
                toggleBtn.setOnAction(e -> {
                    String nextName = disabled
                            ? displayName
                            : fileName + ".disabled";
                    try {
                        Files.move(modPath, modPath.resolveSibling(nextName), StandardCopyOption.REPLACE_EXISTING);
                        reload.run();
                    } catch (IOException ex) {
                        UiUtils.showError("Could not toggle mod: " + ex.getMessage());
                    }
                });

                Button removeBtn = UiUtils.dangerButton("Remove");
                removeBtn.setOnAction(e -> {
                    try {
                        Files.deleteIfExists(modPath);
                        reload.run();
                    } catch (IOException ex) {
                        UiUtils.showError("Could not remove mod: " + ex.getMessage());
                    }
                });

                Region gap = new Region();
                HBox.setHgrow(gap, Priority.ALWAYS);

                HBox row = new HBox(10, nameLbl, stateLbl, gap, toggleBtn, removeBtn);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6));
                setGraphic(row);
                setText(null);
                setStyle("-fx-background-color: transparent;");
            }
        });

        Button addBtn = new Button("+ Add .jar mods...");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setPadding(new Insets(8, 14, 8, 14));
        addBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Mod JAR files");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Minecraft Mod JAR", "*.jar")
            );
            List<File> files = chooser.showOpenMultipleDialog(primaryStage);
            if (files == null || files.isEmpty()) {
                return;
            }
            for (File file : files) {
                try {
                    Files.copy(
                            file.toPath(),
                            modsDir.resolve(file.getName()),
                            StandardCopyOption.REPLACE_EXISTING
                    );
                } catch (IOException ex) {
                    UiUtils.showError("Could not copy " + file.getName() + ": " + ex.getMessage());
                }
            }
            reload.run();
        });

        Button openFolderBtn = UiUtils.secondaryButton("Open mods folder");
        openFolderBtn.setOnAction(e -> UiUtils.openInExplorer(modsDir));

        String noteText;
        if (Instance.LOADER_VANILLA.equalsIgnoreCase(instance.loaderType)) {
            noteText = "Note: Vanilla does not load mods. Click Loader / Version (or use Home) to switch to Fabric, Forge, NeoForge, or Quilt.";
        } else if (Instance.LOADER_FABRIC.equalsIgnoreCase(instance.loaderType)) {
            noteText = "Fabric Loader is configured (JJN Client UI auto-bundled on 26.2 / 1.21.4).";
        } else {
            noteText = instance.loaderDisplayName() + " Loader is configured for this instance.";
        }
        Label note = UiUtils.mutedLabel(noteText);

        HBox buttons = new HBox(10, addBtn, openFolderBtn);
        VBox content = new VBox(12, note, modList, buttons);
        content.setPadding(new Insets(10));

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void openConfigureDialog(Instance instance, Runnable onSaved) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Configure - " + instance.name);
        dialog.setHeaderText("Per-instance settings for " + instance.name);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        UiUtils.styleDialog(dialog);

        TextField ramField = new TextField(instance.ramMbOverride > 0 ? String.valueOf(instance.ramMbOverride) : "");
        ramField.setPromptText("0 or empty = inherit global");

        TextField widthField = new TextField(instance.customWidth > 0 ? String.valueOf(instance.customWidth) : "");
        widthField.setPromptText("Width (e.g. 1280)");

        TextField heightField = new TextField(instance.customHeight > 0 ? String.valueOf(instance.customHeight) : "");
        heightField.setPromptText("Height (e.g. 720)");

        TextField serverField = new TextField(instance.quickPlayServer == null ? "" : instance.quickPlayServer);
        serverField.setPromptText("Optional server (e.g. mc.hypixel.net)");

        TextField jvmField = new TextField(instance.jvmArgs == null ? "" : instance.jvmArgs);
        jvmField.setPromptText("Extra JVM arguments for this instance");

        HBox resRow = new HBox(10, widthField, new Label("x"), heightField);
        resRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(
                10,
                new Label("Memory override (MB, min " + LauncherSettings.MIN_RAM_MB + ", empty = global):"),
                ramField,
                new Label("Window resolution override (empty = global):"),
                resRow,
                new Label("Quick Play server address:"),
                serverField,
                new Label("Extra JVM arguments:"),
                jvmField
        );
        content.setPrefWidth(430);
        content.setPadding(new Insets(10));

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) {
                return;
            }
            instance.ramMbOverride = parsePositiveInt(ramField.getText());
            if (instance.ramMbOverride > 0 && instance.ramMbOverride < LauncherSettings.MIN_RAM_MB) {
                instance.ramMbOverride = LauncherSettings.MIN_RAM_MB;
            }
            instance.customWidth = parsePositiveInt(widthField.getText());
            instance.customHeight = parsePositiveInt(heightField.getText());
            instance.quickPlayServer = serverField.getText().trim();
            instance.jvmArgs = jvmField.getText().trim();
            try {
                instance.save();
                onSaved.run();
            } catch (IOException ex) {
                UiUtils.showError("Could not save instance settings: " + ex.getMessage());
            }
        });
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
