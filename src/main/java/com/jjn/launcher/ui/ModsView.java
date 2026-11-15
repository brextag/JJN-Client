package com.jjn.launcher.ui;

import com.jjn.launcher.model.Instance;
import com.jjn.launcher.service.ModCatalogService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dedicated Mods browser & manager tab: search CurseForge and Modrinth side-by-side
 * and install mods directly into any selected instance.
 */
public class ModsView {

    public interface Actions {
        List<Instance> instances();
        Instance selectedInstance();
        void onSelectInstance(Instance instance);
        void onChangeLoader(Instance instance);
    }

    private final ModCatalogService catalogService = new ModCatalogService();
    private final Map<String, Image> iconCache = new ConcurrentHashMap<>();
    private final Set<String> installedKeysThisSession = ConcurrentHashMap.newKeySet();

    private String activeSource = ModCatalogService.SOURCE_ALL;
    private String lastQuery = "";
    private boolean showInstalledOnly = false;
    private List<ModCatalogService.ModSearchResult> currentResults = new ArrayList<>();

    public Node build(Stage primaryStage, Actions actions) {
        List<Instance> allInstances = actions.instances();
        Instance current = actions.selectedInstance();
        if (current == null && !allInstances.isEmpty()) {
            current = allInstances.get(0);
        }
        final Instance[] targetRef = new Instance[]{current};

        // Top target instance & mode bar
        ComboBox<Instance> instancePicker = UiUtils.instanceComboBox(allInstances, current);
        instancePicker.setPrefWidth(290);

        ToggleGroup modeGroup = new ToggleGroup();
        ToggleButton browseModeBtn = new ToggleButton("Browse Online");
        browseModeBtn.getStyleClass().add("pill-toggle");
        browseModeBtn.setToggleGroup(modeGroup);

        ToggleButton installedModeBtn = new ToggleButton("Installed Mods");
        installedModeBtn.getStyleClass().add("pill-toggle");
        installedModeBtn.setToggleGroup(modeGroup);

        if (showInstalledOnly) {
            installedModeBtn.setSelected(true);
        } else {
            browseModeBtn.setSelected(true);
        }

        HBox modeSwitcher = new HBox(4, browseModeBtn, installedModeBtn);
        modeSwitcher.getStyleClass().add("segmented-group");
        modeSwitcher.setAlignment(Pos.CENTER_LEFT);

        Button openFolderBtn = UiUtils.secondaryButton("Open mods folder");
        openFolderBtn.setOnAction(e -> {
            if (targetRef[0] != null) {
                UiUtils.openInExplorer(targetRef[0].modsFolder());
            }
        });

        Label targetLabel = new Label("TARGET INSTANCE");
        targetLabel.getStyleClass().add("accent-text");

        VBox targetCol = new VBox(4, targetLabel, instancePicker);
        targetCol.setAlignment(Pos.CENTER_LEFT);

        Region barGap = new Region();
        HBox.setHgrow(barGap, Priority.ALWAYS);

        HBox targetBar = new HBox(14, targetCol, barGap, modeSwitcher, openFolderBtn);
        targetBar.setAlignment(Pos.CENTER_LEFT);
        targetBar.getStyleClass().add("selection-bar");

        // Vanilla / uninstalled gentle banner
        Label loaderBannerText = UiUtils.mutedLabel("");
        Button switchLoaderBtn = UiUtils.secondaryButton("Choose Mod Loader...");
        Region bannerGap = new Region();
        HBox.setHgrow(bannerGap, Priority.ALWAYS);
        HBox loaderBanner = new HBox(12, loaderBannerText, bannerGap, switchLoaderBtn);
        loaderBanner.setAlignment(Pos.CENTER_LEFT);
        loaderBanner.setPadding(new Insets(10, 14, 10, 14));
        loaderBanner.setStyle(
                "-fx-background-color: #202432; -fx-background-radius: 12;"
                        + "-fx-border-color: #353949; -fx-border-radius: 12;"
        );

        switchLoaderBtn.setOnAction(e -> {
            if (targetRef[0] != null) {
                actions.onChangeLoader(targetRef[0]);
            }
        });

        // Search + source bar
        TextField searchField = new TextField(lastQuery);
        searchField.getStyleClass().add("input-field");
        searchField.setPromptText("Search Modrinth & CurseForge (e.g. Sodium, JEI, Iris, Waystones)...");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().add("primary-button");
        searchBtn.setPadding(new Insets(9, 18, 9, 18));

        ToggleGroup sourceGroup = new ToggleGroup();
        ToggleButton allSrcBtn = new ToggleButton("All");
        ToggleButton mrSrcBtn = new ToggleButton("Modrinth");
        ToggleButton cfSrcBtn = new ToggleButton("CurseForge");
        for (ToggleButton tb : List.of(allSrcBtn, mrSrcBtn, cfSrcBtn)) {
            tb.getStyleClass().add("pill-toggle");
            tb.setToggleGroup(sourceGroup);
        }
        if (ModCatalogService.SOURCE_MODRINTH.equalsIgnoreCase(activeSource)) {
            mrSrcBtn.setSelected(true);
        } else if (ModCatalogService.SOURCE_CURSEFORGE.equalsIgnoreCase(activeSource)) {
            cfSrcBtn.setSelected(true);
        } else {
            allSrcBtn.setSelected(true);
        }

        HBox sourceSwitcher = new HBox(4, allSrcBtn, mrSrcBtn, cfSrcBtn);
        sourceSwitcher.getStyleClass().add("segmented-group");

        CheckBox matchInstanceCheck = new CheckBox("Match instance MC version & loader");
        matchInstanceCheck.setSelected(true);

        Label filterPill = new Label("");
        filterPill.setStyle(
                "-fx-background-color: #202432; -fx-text-fill: #c7d2fe;"
                        + "-fx-font-size: 11px; -fx-font-weight: bold;"
                        + "-fx-padding: 4 10 4 10; -fx-background-radius: 999;"
        );

        HBox searchTopRow = new HBox(10, searchField, searchBtn, sourceSwitcher);
        searchTopRow.setAlignment(Pos.CENTER_LEFT);

        HBox searchSubRow = new HBox(12, matchInstanceCheck, filterPill);
        searchSubRow.setAlignment(Pos.CENTER_LEFT);

        VBox searchBar = new VBox(10, searchTopRow, searchSubRow);
        searchBar.getStyleClass().add("selection-bar");

        // Status label
        Label statusLabel = UiUtils.mutedLabel("");

        // Lists for Browse vs Installed
        ListView<ModCatalogService.ModSearchResult> resultsList = new ListView<>();
        resultsList.setStyle(
                "-fx-background-color: transparent;"
                        + "-fx-control-inner-background: transparent;"
                        + "-fx-background-insets: 0;"
        );
        VBox.setVgrow(resultsList, Priority.ALWAYS);

        ListView<Path> localModsList = new ListView<>();
        localModsList.setStyle(
                "-fx-background-color: transparent;"
                        + "-fx-control-inner-background: transparent;"
                        + "-fx-background-insets: 0;"
        );
        VBox.setVgrow(localModsList, Priority.ALWAYS);

        Button addLocalBtn = UiUtils.secondaryButton("+ Add local .jar mod...");
        addLocalBtn.setOnAction(e -> {
            Instance inst = targetRef[0];
            if (inst == null) return;
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Mod JAR files");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Minecraft Mod JAR", "*.jar"));
            List<File> files = chooser.showOpenMultipleDialog(primaryStage);
            if (files == null || files.isEmpty()) return;
            try {
                Files.createDirectories(inst.modsFolder());
                for (File f : files) {
                    Files.copy(f.toPath(), inst.modsFolder().resolve(f.getName()), StandardCopyOption.REPLACE_EXISTING);
                }
                refreshLocalList(inst, localModsList, installedModeBtn);
            } catch (IOException ex) {
                UiUtils.showError("Could not copy mod: " + ex.getMessage());
            }
        });

        VBox installedBox = new VBox(10, addLocalBtn, localModsList);
        VBox.setVgrow(installedBox, Priority.ALWAYS);

        StackPane contentArea = new StackPane(resultsList, installedBox);
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        Runnable syncUiState = () -> {
            Instance inst = targetRef[0];
            boolean compatibleLoader = inst != null
                    && inst.isInstalled()
                    && !Instance.LOADER_VANILLA.equalsIgnoreCase(inst.loaderType);

            loaderBanner.setVisible(!compatibleLoader);
            loaderBanner.setManaged(!compatibleLoader);
            if (inst == null) {
                loaderBannerText.setText("Create or select an instance to install mods into.");
                switchLoaderBtn.setVisible(false);
            } else if (!inst.isInstalled()) {
                loaderBannerText.setText(inst.name + " is not installed yet. Install Fabric, Forge, NeoForge, or Quilt to load mods.");
                switchLoaderBtn.setVisible(true);
            } else if (Instance.LOADER_VANILLA.equalsIgnoreCase(inst.loaderType)) {
                loaderBannerText.setText(inst.name + " uses Vanilla " + inst.mcVersion + " and cannot run mods until you switch to Fabric, Forge, NeoForge, or Quilt.");
                switchLoaderBtn.setVisible(true);
            }

            if (inst != null && matchInstanceCheck.isSelected() && inst.mcVersion != null) {
                String loaderText = Instance.LOADER_VANILLA.equalsIgnoreCase(inst.loaderType)
                        ? "Any loader"
                        : inst.loaderDisplayName();
                filterPill.setText("MC " + inst.mcVersion + " • " + loaderText);
            } else {
                filterPill.setText("All MC versions & loaders");
            }

            searchBar.setVisible(!showInstalledOnly);
            searchBar.setManaged(!showInstalledOnly);
            resultsList.setVisible(!showInstalledOnly);
            resultsList.setManaged(!showInstalledOnly);
            installedBox.setVisible(showInstalledOnly);
            installedBox.setManaged(showInstalledOnly);
        };

        Runnable runSearch = () -> {
            Instance inst = targetRef[0];
            String mcVer = (matchInstanceCheck.isSelected() && inst != null) ? inst.mcVersion : null;
            String loader = (matchInstanceCheck.isSelected() && inst != null) ? inst.loaderType : null;
            String q = searchField.getText();
            lastQuery = q;

            searchBtn.setDisable(true);
            statusLabel.setText("Searching " + activeSource + "...");

            Thread worker = new Thread(() -> {
                ModCatalogService.SearchResponse resp = catalogService.search(q, activeSource, mcVer, loader);
                Platform.runLater(() -> {
                    searchBtn.setDisable(false);
                    currentResults = resp.results();
                    resultsList.setItems(FXCollections.observableArrayList(currentResults));
                    String summary = "Found " + currentResults.size() + " mod(s)";
                    if (resp.warning() != null && !resp.warning().isBlank()) {
                        summary += "  (" + resp.warning() + ")";
                    }
                    statusLabel.setText(summary);
                });
            }, "JJN-Mod-Search");
            worker.setDaemon(true);
            worker.start();
        };

        resultsList.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(ModCatalogService.ModSearchResult mod, boolean empty) {
                super.updateItem(mod, empty);
                if (empty || mod == null) {
                    setGraphic(null);
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }

                ImageView iconView = new ImageView();
                iconView.setFitWidth(44);
                iconView.setFitHeight(44);
                iconView.setPreserveRatio(true);
                Rectangle clip = new Rectangle(44, 44);
                clip.setArcWidth(12);
                clip.setArcHeight(12);
                iconView.setClip(clip);
                loadIconAsync(mod.iconUrl(), iconView);

                StackPane iconWrap = new StackPane(iconView);
                iconWrap.setPrefSize(44, 44);
                iconWrap.setMinSize(44, 44);
                iconWrap.setStyle("-fx-background-color: #202432; -fx-background-radius: 10;");

                Label titleLbl = new Label(mod.name());
                titleLbl.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 15px; -fx-font-weight: bold;");

                boolean isMr = ModCatalogService.SOURCE_MODRINTH.equalsIgnoreCase(mod.source());
                Label sourceBadge = new Label(isMr ? "MODRINTH" : "CURSEFORGE");
                sourceBadge.setStyle(
                        isMr
                                ? "-fx-background-color: #1c3329; -fx-text-fill: #86efac; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 8 2 8; -fx-background-radius: 999;"
                                : "-fx-background-color: #36241b; -fx-text-fill: #fdba74; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 8 2 8; -fx-background-radius: 999;"
                );

                Label authorLbl = UiUtils.mutedLabel(
                        "by " + (mod.author() == null ? "Unknown" : mod.author())
                                + "  •  " + ModCatalogService.formatDownloads(mod.downloads())
                );

                HBox headRow = new HBox(8, titleLbl, sourceBadge);
                headRow.setAlignment(Pos.CENTER_LEFT);

                Label descLbl = new Label(mod.description() == null ? "" : mod.description());
                descLbl.setWrapText(true);
                descLbl.setMaxHeight(36);
                descLbl.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 12px;");

                VBox textCol = new VBox(4, headRow, authorLbl, descLbl);
                HBox.setHgrow(textCol, Priority.ALWAYS);

                Button pageBtn = UiUtils.secondaryButton("Page");
                pageBtn.setOnAction(e -> openWebUrl(mod.pageUrl()));

                String sessionKey = sessionInstallKey(targetRef[0], mod);
                boolean alreadyInstalled = installedKeysThisSession.contains(sessionKey);

                Button installBtn = new Button(alreadyInstalled ? "Installed ✓" : "Install");
                installBtn.getStyleClass().add("primary-button");
                installBtn.setPadding(new Insets(8, 16, 8, 16));
                installBtn.setDisable(targetRef[0] == null);

                installBtn.setOnAction(e -> {
                    Instance target = targetRef[0];
                    if (target == null) {
                        return;
                    }
                    if (Instance.LOADER_VANILLA.equalsIgnoreCase(target.loaderType)) {
                        UiUtils.showError(
                                target.name + " is currently Vanilla (" + target.mcVersion + ").\n"
                                        + "Switch it to Fabric, Forge, NeoForge, or Quilt first so Minecraft can load mods."
                        );
                        return;
                    }
                    String mcVer = target.mcVersion != null ? target.mcVersion : "26.2";
                    String loader = target.loaderType;

                    installBtn.setDisable(true);
                    installBtn.setText("Installing...");
                    statusLabel.setText("Installing " + mod.name() + " into " + target.name + "...");

                    Thread dlThread = new Thread(() -> {
                        try {
                            ModCatalogService.InstallOutcome out = catalogService.installMod(
                                    mod,
                                    mcVer,
                                    loader,
                                    target.modsFolder(),
                                    msg -> Platform.runLater(() -> statusLabel.setText(msg))
                            );
                            installedKeysThisSession.add(sessionInstallKey(target, mod));
                            Platform.runLater(() -> {
                                installBtn.setDisable(false);
                                installBtn.setText("Installed ✓");
                                String extra = out.installedFiles().size() > 1
                                        ? " (+ " + (out.installedFiles().size() - 1) + " required dependency)"
                                        : "";
                                statusLabel.setText(
                                        "Installed " + out.primaryFileName() + extra + " into " + target.name + "!"
                                );
                                refreshLocalList(target, localModsList, installedModeBtn);
                            });
                        } catch (Exception ex) {
                            Platform.runLater(() -> {
                                installBtn.setDisable(false);
                                installBtn.setText("Install");
                                statusLabel.setText("Install failed: " + ex.getMessage());
                                UiUtils.showError(ex.getMessage());
                            });
                        }
                    }, "JJN-Mod-Installer");
                    dlThread.setDaemon(true);
                    dlThread.start();
                });

                HBox card = new HBox(14, iconWrap, textCol, pageBtn, installBtn);
                card.setAlignment(Pos.CENTER_LEFT);
                card.setPadding(new Insets(14, 16, 14, 16));
                card.getStyleClass().add("panel-card");

                setGraphic(card);
                setText(null);
                setPadding(new Insets(4, 0, 4, 0));
                setStyle("-fx-background-color: transparent;");
            }
        });

        localModsList.setCellFactory(v -> new ListCell<>() {
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

                Label stateBadge = new Label(disabled ? "DISABLED" : "ENABLED");
                stateBadge.setStyle(
                        disabled
                                ? "-fx-background-color: #332717; -fx-text-fill: #fbbf24; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 8 2 8; -fx-background-radius: 999;"
                                : "-fx-background-color: #1c3329; -fx-text-fill: #86efac; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 8 2 8; -fx-background-radius: 999;"
                );

                Button toggleBtn = UiUtils.secondaryButton(disabled ? "Enable" : "Disable");
                toggleBtn.setOnAction(e -> {
                    String next = disabled ? displayName : fileName + ".disabled";
                    try {
                        Files.move(modPath, modPath.resolveSibling(next), StandardCopyOption.REPLACE_EXISTING);
                        refreshLocalList(targetRef[0], localModsList, installedModeBtn);
                    } catch (IOException ex) {
                        UiUtils.showError("Could not toggle mod: " + ex.getMessage());
                    }
                });

                Button removeBtn = UiUtils.dangerButton("Remove");
                removeBtn.setOnAction(e -> {
                    try {
                        Files.deleteIfExists(modPath);
                        refreshLocalList(targetRef[0], localModsList, installedModeBtn);
                    } catch (IOException ex) {
                        UiUtils.showError("Could not remove mod: " + ex.getMessage());
                    }
                });

                Region gap = new Region();
                HBox.setHgrow(gap, Priority.ALWAYS);
                HBox card = new HBox(12, nameLbl, stateBadge, gap, toggleBtn, removeBtn);
                card.setAlignment(Pos.CENTER_LEFT);
                card.setPadding(new Insets(12, 16, 12, 16));
                card.getStyleClass().add("panel-card");

                setGraphic(card);
                setText(null);
                setPadding(new Insets(4, 0, 4, 0));
                setStyle("-fx-background-color: transparent;");
            }
        });

        // Wire events
        instancePicker.setOnAction(e -> {
            targetRef[0] = instancePicker.getValue();
            actions.onSelectInstance(targetRef[0]);
            syncUiState.run();
            refreshLocalList(targetRef[0], localModsList, installedModeBtn);
            if (!showInstalledOnly) {
                runSearch.run();
            }
        });

        browseModeBtn.setOnAction(e -> {
            if (!browseModeBtn.isSelected()) browseModeBtn.setSelected(true);
            showInstalledOnly = false;
            syncUiState.run();
        });

        installedModeBtn.setOnAction(e -> {
            if (!installedModeBtn.isSelected()) installedModeBtn.setSelected(true);
            showInstalledOnly = true;
            syncUiState.run();
            refreshLocalList(targetRef[0], localModsList, installedModeBtn);
        });

        allSrcBtn.setOnAction(e -> {
            if (!allSrcBtn.isSelected()) allSrcBtn.setSelected(true);
            activeSource = ModCatalogService.SOURCE_ALL;
            runSearch.run();
        });
        mrSrcBtn.setOnAction(e -> {
            if (!mrSrcBtn.isSelected()) mrSrcBtn.setSelected(true);
            activeSource = ModCatalogService.SOURCE_MODRINTH;
            runSearch.run();
        });
        cfSrcBtn.setOnAction(e -> {
            if (!cfSrcBtn.isSelected()) cfSrcBtn.setSelected(true);
            activeSource = ModCatalogService.SOURCE_CURSEFORGE;
            runSearch.run();
        });

        matchInstanceCheck.setOnAction(e -> {
            syncUiState.run();
            runSearch.run();
        });

        searchBtn.setOnAction(e -> runSearch.run());
        searchField.setOnAction(e -> runSearch.run());

        syncUiState.run();
        refreshLocalList(targetRef[0], localModsList, installedModeBtn);
        if (currentResults.isEmpty()) {
            runSearch.run();
        } else {
            resultsList.setItems(FXCollections.observableArrayList(currentResults));
        }

        VBox container = new VBox(14, targetBar, loaderBanner, searchBar, statusLabel, contentArea);
        VBox.setVgrow(container, Priority.ALWAYS);
        return container;
    }

    private static String sessionInstallKey(Instance inst, ModCatalogService.ModSearchResult mod) {
        String id = inst == null ? "" : inst.id;
        return id + "::" + mod.source() + "::" + mod.projectId();
    }

    private void refreshLocalList(Instance inst, ListView<Path> localModsList, ToggleButton installedModeBtn) {
        if (inst == null) {
            localModsList.setItems(FXCollections.emptyObservableList());
            installedModeBtn.setText("Installed Mods (0)");
            return;
        }
        List<Path> found = new ArrayList<>();
        Path modsDir = inst.modsFolder();
        if (Files.isDirectory(modsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir)) {
                for (Path entry : stream) {
                    String lower = entry.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (Files.isRegularFile(entry) && (lower.endsWith(".jar") || lower.endsWith(".jar.disabled"))) {
                        found.add(entry);
                    }
                }
            } catch (IOException ignored) {
            }
        }
        found.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
        localModsList.setItems(FXCollections.observableArrayList(found));
        installedModeBtn.setText("Installed Mods (" + found.size() + ")");
    }

    private void loadIconAsync(String url, ImageView view) {
        if (url == null || url.isBlank()) {
            view.setImage(null);
            return;
        }
        Image cached = iconCache.get(url);
        if (cached != null) {
            view.setImage(cached);
            return;
        }
        view.setImage(null);
        Thread t = new Thread(() -> {
            try {
                Image img = new Image(url, 44, 44, true, true, false);
                if (!img.isError()) {
                    iconCache.put(url, img);
                    Platform.runLater(() -> view.setImage(img));
                }
            } catch (Exception ignored) {
            }
        }, "JJN-Mod-Icon");
        t.setDaemon(true);
        t.start();
    }

    private static void openWebUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {
        }
    }
}
