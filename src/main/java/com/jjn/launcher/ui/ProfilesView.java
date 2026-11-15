package com.jjn.launcher.ui;

import com.jjn.launcher.MicrosoftAuthService;
import com.jjn.launcher.model.ClientProfile;
import com.jjn.launcher.model.Instance;
import com.jjn.launcher.service.MinecraftSkinService;
import com.jjn.launcher.service.ProfileService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Profiles & Customization view: manage client profiles, customize HUD/module presets,
 * preview 3D skins, import local skins, upload skins to Mojang, and equip capes.
 */
public class ProfilesView {

    public interface Actions {
        ProfileService profileService();
        ProfileService.ProfileStore profileStore();
        Instance activeInstance();
        MicrosoftAuthService.AuthResult authResult();
        void onProfileUpdated();
    }

    private final MinecraftSkinService skinService = new MinecraftSkinService();
    private MinecraftSkinService.MojangCosmetics cachedCosmetics;

    public Node build(Stage primaryStage, Actions actions) {
        ProfileService service = actions.profileService();
        ProfileService.ProfileStore store = actions.profileStore();
        ClientProfile active = store.findActive();
        Instance instance = actions.activeInstance();
        MicrosoftAuthService.AuthResult auth = actions.authResult();

        final ClientProfile[] activeRef = new ClientProfile[]{active};

        // Top Profile Selection Bar
        ComboBox<ClientProfile> profilePicker = new ComboBox<>(
                FXCollections.observableArrayList(store.profiles)
        );
        profilePicker.setPrefWidth(210);
        if (active != null) {
            profilePicker.setValue(active);
        }

        Button newBtn = UiUtils.secondaryButton("+ New");
        Button dupBtn = UiUtils.secondaryButton("Duplicate");
        Button renameBtn = UiUtils.secondaryButton("Rename");
        Button deleteBtn = UiUtils.dangerButton("Delete");
        deleteBtn.setDisable(store.profiles.size() <= 1);

        Button pullBtn = UiUtils.secondaryButton("Pull from Instance");
        pullBtn.setDisable(instance == null);

        Button applyBtn = new Button(
                instance == null ? "Apply Profile" : "Apply to " + instance.name
        );
        applyBtn.getStyleClass().add("primary-button");
        applyBtn.setPadding(new Insets(9, 16, 9, 16));
        applyBtn.setDisable(instance == null);

        Region topGap = new Region();
        HBox.setHgrow(topGap, Priority.ALWAYS);

        Label profileLbl = new Label("ACTIVE PROFILE");
        profileLbl.getStyleClass().add("accent-text");
        VBox pickerCol = new VBox(4, profileLbl, profilePicker);
        pickerCol.setAlignment(Pos.CENTER_LEFT);

        HBox topBar = new HBox(10, pickerCol, newBtn, dupBtn, renameBtn, deleteBtn, topGap, pullBtn, applyBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("selection-bar");

        Label statusLabel = UiUtils.mutedLabel(
                instance == null
                        ? "Create or select an instance on Home to sync profile presets."
                        : "Profile presets & skins sync directly with \"" + instance.name + "\"."
        );

        // LEFT CARD: 3D Skin Preview & Cosmetics Studio
        SkinPreviewCanvas skinCanvas = new SkinPreviewCanvas(210, 240);
        StackPane previewFrame = new StackPane(skinCanvas);
        previewFrame.setPadding(new Insets(8));
        previewFrame.setStyle(
                "-fx-background-color: #101116; -fx-background-radius: 14;"
                        + "-fx-border-color: #2b3040; -fx-border-radius: 14;"
        );

        Label dragHint = new Label("Drag character to rotate 3D preview");
        dragHint.setStyle("-fx-text-fill: #85899a; -fx-font-size: 11px;");

        Button flipBtn = UiUtils.secondaryButton("Flip Front / Back");
        flipBtn.setOnAction(e -> skinCanvas.flipView());
        Button resetViewBtn = UiUtils.secondaryButton("Reset Angle");
        resetViewBtn.setOnAction(e -> skinCanvas.setYaw(22.0));
        HBox viewBtns = new HBox(8, flipBtn, resetViewBtn);
        viewBtns.setAlignment(Pos.CENTER);

        // Classic vs Slim arm toggle
        ToggleGroup armGroup = new ToggleGroup();
        ToggleButton classicBtn = new ToggleButton("Classic (Steve)");
        ToggleButton slimBtn = new ToggleButton("Slim (Alex)");
        classicBtn.getStyleClass().add("pill-toggle");
        slimBtn.getStyleClass().add("pill-toggle");
        classicBtn.setToggleGroup(armGroup);
        slimBtn.setToggleGroup(armGroup);
        HBox armSwitcher = new HBox(4, classicBtn, slimBtn);
        armSwitcher.getStyleClass().add("segmented-group");

        // Local skin wardrobe picker
        List<Path> savedSkins = skinService.listSavedSkins();
        ComboBox<Path> wardrobeBox = new ComboBox<>(FXCollections.observableArrayList(savedSkins));
        wardrobeBox.setPromptText("Account / Default skin");
        wardrobeBox.setMaxWidth(Double.MAX_VALUE);
        wardrobeBox.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getFileName().toString());
            }
        });
        wardrobeBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Account / Default skin" : item.getFileName().toString());
            }
        });

        Button importSkinBtn = UiUtils.secondaryButton("Import .png Skin...");
        Button useAccountSkinBtn = UiUtils.secondaryButton("Use Account Skin");
        Button uploadMojangBtn = new Button("Upload Skin to Mojang");
        uploadMojangBtn.getStyleClass().add("primary-button");
        uploadMojangBtn.setPadding(new Insets(8, 14, 8, 14));
        uploadMojangBtn.setMaxWidth(Double.MAX_VALUE);
        uploadMojangBtn.setDisable(auth == null || auth.minecraftAccessToken == null);

        HBox skinFileBtns = new HBox(8, importSkinBtn, useAccountSkinBtn);
        HBox.setHgrow(importSkinBtn, Priority.ALWAYS);
        HBox.setHgrow(useAccountSkinBtn, Priority.ALWAYS);
        importSkinBtn.setMaxWidth(Double.MAX_VALUE);
        useAccountSkinBtn.setMaxWidth(Double.MAX_VALUE);

        // Capes section
        ComboBox<MinecraftSkinService.CapeInfo> capeBox = new ComboBox<>();
        capeBox.setPromptText(auth == null ? "Sign in to load Mojang capes" : "No cape equipped");
        capeBox.setMaxWidth(Double.MAX_VALUE);
        capeBox.setDisable(auth == null || auth.minecraftAccessToken == null);
        capeBox.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(MinecraftSkinService.CapeInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else if (item.id().isEmpty()) {
                    setText("No Cape (Hidden)");
                } else {
                    setText(item.active() ? item.alias() + "  (Equipped)" : item.alias());
                }
            }
        });
        capeBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(MinecraftSkinService.CapeInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("No Cape (Hidden)");
                } else if (item.id().isEmpty()) {
                    setText("No Cape (Hidden)");
                } else {
                    setText(item.active() ? item.alias() + "  (Equipped)" : item.alias());
                }
            }
        });
        Button applyCapeBtn = new Button("Apply Cape");
        applyCapeBtn.getStyleClass().add("primary-button");
        applyCapeBtn.setPadding(new Insets(7, 12, 7, 12));
        applyCapeBtn.setDisable(auth == null || auth.minecraftAccessToken == null);
        HBox capeRow = new HBox(8, capeBox, applyCapeBtn);
        HBox.setHgrow(capeBox, Priority.ALWAYS);

        VBox leftCard = UiUtils.card(
                UiUtils.sectionTitle("Skin & Cosmetics Studio"),
                previewFrame,
                dragHint,
                viewBtns,
                new Label("Model Variant"),
                armSwitcher,
                new Label("Saved Skin Wardrobe (applies to local & in-game 3D preview)"),
                wardrobeBox,
                skinFileBtns,
                uploadMojangBtn,
                new Label("Mojang Cape"),
                capeRow
        );
        leftCard.setPrefWidth(340);
        leftCard.setMinWidth(320);

        // RIGHT CARD: Profile HUD & Module Customizations
        CheckBox customTitleCheck = new CheckBox("JJN Custom Front Screen (In-Game Home)");
        CheckBox gridCheck = new CheckBox("Animated Architectural Grid Backdrop");
        CheckBox preview3dCheck = new CheckBox("3D Player Preview on Left Side of Home Screen");

        CheckBox masterHudCheck = new CheckBox("Enable In-Game HUD Overlay");
        CheckBox fpsCheck = new CheckBox("FPS Counter");
        CheckBox fpsCompactCheck = new CheckBox("Compact FPS label (120 FPS)");
        CheckBox coordsCheck = new CheckBox("XYZ Coordinates");
        CheckBox dirCheck = new CheckBox("Facing Direction [N/S/E/W]");
        CheckBox sprintCheck = new CheckBox("Toggle Sprint");
        CheckBox sprintHudCheck = new CheckBox("Show 'Sprint: Toggled' on HUD");
        CheckBox pingCheck = new CheckBox("Ping & Server IP");
        CheckBox biomeCheck = new CheckBox("Biome Display");
        CheckBox keystrokesCheck = new CheckBox("Keystrokes (WASD + Spacebar)");
        CheckBox shadowCheck = new CheckBox("HUD Text Drop Shadow");

        ComboBox<String> cornerBox = new ComboBox<>(FXCollections.observableArrayList(
                "Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right", "Custom"
        ));
        cornerBox.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> accentBox = new ComboBox<>(FXCollections.observableArrayList(
                "Indigo (#C7D2FE)", "Emerald (#86EFAC)", "Cyan (#67E8F9)",
                "Rose (#FCA5A5)", "Amber (#FCD34D)", "White (#FFFFFF)"
        ));
        accentBox.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> opacityBox = new ComboBox<>(FXCollections.observableArrayList(
                "Transparent (No Box)", "Glass", "Solid Dark Slate"
        ));
        opacityBox.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> scaleBox = new ComboBox<>(FXCollections.observableArrayList(
                "Small (0.85x)", "Normal (1.0x)", "Large (1.2x)"
        ));
        scaleBox.setMaxWidth(Double.MAX_VALUE);

        GridPane layoutGrid = new GridPane();
        layoutGrid.setHgap(12);
        layoutGrid.setVgap(10);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(50);
        layoutGrid.getColumnConstraints().addAll(c1, c2);

        layoutGrid.add(new VBox(4, UiUtils.mutedLabel("HUD Corner"), cornerBox), 0, 0);
        layoutGrid.add(new VBox(4, UiUtils.mutedLabel("Accent Theme"), accentBox), 1, 0);
        layoutGrid.add(new VBox(4, UiUtils.mutedLabel("Background Style"), opacityBox), 0, 1);
        layoutGrid.add(new VBox(4, UiUtils.mutedLabel("HUD Scale"), scaleBox), 1, 1);

        GridPane checksGrid = new GridPane();
        checksGrid.setHgap(16);
        checksGrid.setVgap(8);
        checksGrid.getColumnConstraints().addAll(c1, c2);
        checksGrid.add(masterHudCheck, 0, 0);
        checksGrid.add(shadowCheck, 1, 0);
        checksGrid.add(fpsCheck, 0, 1);
        checksGrid.add(fpsCompactCheck, 1, 1);
        checksGrid.add(coordsCheck, 0, 2);
        checksGrid.add(dirCheck, 1, 2);
        checksGrid.add(sprintCheck, 0, 3);
        checksGrid.add(sprintHudCheck, 1, 3);
        checksGrid.add(pingCheck, 0, 4);
        checksGrid.add(biomeCheck, 1, 4);
        checksGrid.add(keystrokesCheck, 0, 5);

        VBox rightInner = new VBox(
                14,
                UiUtils.sectionTitle("In-Game Front Screen"),
                new VBox(8, customTitleCheck, gridCheck, preview3dCheck),
                new Separator(),
                UiUtils.sectionTitle("HUD & Module Presets"),
                checksGrid,
                layoutGrid
        );
        rightInner.setPadding(new Insets(4));

        ScrollPane rightScroll = new ScrollPane(rightInner);
        rightScroll.setFitToWidth(true);
        rightScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(rightScroll, Priority.ALWAYS);

        VBox rightCard = UiUtils.card(
                UiUtils.sectionTitle("Profile Customizations"),
                UiUtils.mutedLabel("Adjust modules, colors, and front-screen behavior for this profile, then click Apply."),
                rightScroll
        );
        HBox.setHgrow(rightCard, Priority.ALWAYS);

        // Helper to load profile into controls
        final boolean[] populating = new boolean[]{false};
        Runnable loadIntoUi = () -> {
            populating[0] = true;
            ClientProfile p = activeRef[0];
            if (p == null) {
                populating[0] = false;
                return;
            }
            boolean isSlim = "slim".equalsIgnoreCase(p.skinVariant);
            slimBtn.setSelected(isSlim);
            classicBtn.setSelected(!isSlim);
            skinCanvas.setSlim(isSlim);

            Path localSkin = (p.skinFilePath == null || p.skinFilePath.isBlank()) ? null : Path.of(p.skinFilePath);
            if (localSkin != null && Files.isRegularFile(localSkin)) {
                wardrobeBox.setValue(localSkin);
            } else {
                wardrobeBox.setValue(null);
            }
            String accountSkin = (cachedCosmetics != null && cachedCosmetics.activeSkinUrl() != null)
                    ? cachedCosmetics.activeSkinUrl()
                    : (auth != null ? auth.skinUrl : null);
            skinCanvas.loadFromPathOrUrl(localSkin, accountSkin);

            Path localCape = (p.capeFilePath == null || p.capeFilePath.isBlank()) ? null : Path.of(p.capeFilePath);
            if ((localCape == null || !Files.isRegularFile(localCape)) && p.capeId != null && !p.capeId.isBlank()) {
                String cleanId = p.capeId.replaceAll("[^a-zA-Z0-9._-]", "_");
                Path candidate = MinecraftSkinService.capesDir().resolve(cleanId + ".png");
                if (Files.isRegularFile(candidate)) {
                    localCape = candidate;
                }
            }
            String capeUrl = (p.capeId == null || p.capeId.isBlank()) ? null : p.capeUrl;
            skinCanvas.loadCapeFromPathOrUrl(localCape, capeUrl);

            if (!capeBox.getItems().isEmpty()) {
                MinecraftSkinService.CapeInfo match = capeBox.getItems().get(0);
                String targetId = p.capeId == null ? "" : p.capeId;
                for (MinecraftSkinService.CapeInfo item : capeBox.getItems()) {
                    if (targetId.equals(item.id())) {
                        match = item;
                        break;
                    }
                }
                capeBox.setValue(match);
            }

            customTitleCheck.setSelected(p.customTitleScreen);
            gridCheck.setSelected(p.titleScreenGrid);
            preview3dCheck.setSelected(p.showTitlePreview);

            masterHudCheck.setSelected(p.showHud);
            fpsCheck.setSelected(p.showFps);
            fpsCompactCheck.setSelected(p.fpsCompact);
            coordsCheck.setSelected(p.showCoords);
            dirCheck.setSelected(p.showDirection);
            sprintCheck.setSelected(p.toggleSprint);
            sprintHudCheck.setSelected(p.showSprintHud);
            pingCheck.setSelected(p.showPingServer);
            biomeCheck.setSelected(p.showBiome);
            keystrokesCheck.setSelected(p.showKeystrokes);
            shadowCheck.setSelected(p.hudTextShadow);

            cornerBox.getSelectionModel().select(Math.max(0, Math.min(4, p.hudCorner)));
            accentBox.getSelectionModel().select(Math.max(0, Math.min(5, p.hudAccentIndex)));
            opacityBox.getSelectionModel().select(Math.max(0, Math.min(2, p.hudOpacity)));
            if (p.hudScale < 0.95f) scaleBox.getSelectionModel().select(0);
            else if (p.hudScale > 1.15f) scaleBox.getSelectionModel().select(2);
            else scaleBox.getSelectionModel().select(1);

            deleteBtn.setDisable(store.profiles.size() <= 1);
            populating[0] = false;
        };

        java.util.function.Consumer<MinecraftSkinService.MojangCosmetics> applyCosmeticsToUi = cos -> {
            cachedCosmetics = cos;
            List<MinecraftSkinService.CapeInfo> capeItems = new ArrayList<>();
            capeItems.add(new MinecraftSkinService.CapeInfo("", "No Cape (Hidden)", "", false));
            capeItems.addAll(cos.capes());

            populating[0] = true;
            capeBox.setItems(FXCollections.observableArrayList(capeItems));

            MinecraftSkinService.CapeInfo equipped = capeItems.get(0);
            for (MinecraftSkinService.CapeInfo c : cos.capes()) {
                if (c.active()) {
                    equipped = c;
                    break;
                }
            }
            if (activeRef[0] != null) {
                activeRef[0].capeId = equipped.id();
                activeRef[0].capeUrl = equipped.url();
                service.save(store);
            }
            capeBox.setValue(equipped);
            populating[0] = false;

            if (!equipped.id().isEmpty() && !equipped.url().isEmpty()) {
                MinecraftSkinService.CapeInfo target = equipped;
                Thread cacheThread = new Thread(() -> {
                    try {
                        Path cached = skinService.cacheCapeFile(target.id(), target.url());
                        Platform.runLater(() -> {
                            if (activeRef[0] != null && target.id().equals(activeRef[0].capeId) && cached != null) {
                                activeRef[0].capeFilePath = cached.toAbsolutePath().toString();
                                service.save(store);
                                if (instance != null) {
                                    service.syncIdentityOnLaunch(activeRef[0], instance);
                                }
                            }
                            loadIntoUi.run();
                        });
                    } catch (Exception ignored) {
                        Platform.runLater(loadIntoUi);
                    }
                }, "JJN-Cape-Cache");
                cacheThread.setDaemon(true);
                cacheThread.start();
            } else {
                if (activeRef[0] != null) {
                    activeRef[0].capeFilePath = "";
                    service.save(store);
                }
                loadIntoUi.run();
            }
        };

        java.util.function.Consumer<MinecraftSkinService.CapeInfo> equipSelectedCape = cape -> {
            if (auth == null || auth.minecraftAccessToken == null) return;
            String capeId = cape == null ? "" : cape.id();
            String capeUrl = cape == null ? "" : cape.url();
            applyCapeBtn.setDisable(true);
            statusLabel.setText(capeId.isEmpty()
                    ? "Hiding Mojang cape..."
                    : "Applying cape \"" + cape.alias() + "\" to Mojang profile...");

            Thread t = new Thread(() -> {
                try {
                    MinecraftSkinService.MojangCosmetics updated =
                            skinService.setCape(auth.minecraftAccessToken, capeId);
                    Path cachedCape = null;
                    if (!capeId.isEmpty() && capeUrl != null && !capeUrl.isBlank()) {
                        try {
                            cachedCape = skinService.cacheCapeFile(capeId, capeUrl);
                        } catch (Exception ignored) {
                        }
                    }
                    Path finalCachedCape = cachedCape;
                    Platform.runLater(() -> {
                        applyCapeBtn.setDisable(false);
                        if (activeRef[0] != null) {
                            activeRef[0].capeId = capeId;
                            activeRef[0].capeUrl = capeUrl == null ? "" : capeUrl;
                            activeRef[0].capeFilePath = finalCachedCape == null
                                    ? ""
                                    : finalCachedCape.toAbsolutePath().toString();
                            service.save(store);
                            if (instance != null) {
                                service.syncIdentityOnLaunch(activeRef[0], instance);
                            }
                        }
                        applyCosmeticsToUi.accept(updated);
                        if (!capeId.isEmpty()) {
                            skinCanvas.setYaw(198.0); // Rotate to back so user immediately sees their equipped cape!
                        }
                        statusLabel.setText(capeId.isEmpty()
                                ? "Cape hidden on Mojang profile and synced."
                                : "Applied cape \"" + cape.alias() + "\" to your Mojang account & 3D preview!");
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        applyCapeBtn.setDisable(false);
                        statusLabel.setText("Cape update failed: " + ex.getMessage());
                        UiUtils.showError(ex.getMessage());
                    });
                }
            }, "JJN-Cape-Update");
            t.setDaemon(true);
            t.start();
        };

        Runnable persistUiChanges = () -> {
            if (populating[0] || activeRef[0] == null) return;
            ClientProfile p = activeRef[0];
            p.skinVariant = slimBtn.isSelected() ? "slim" : "classic";
            skinCanvas.setSlim(slimBtn.isSelected());

            p.customTitleScreen = customTitleCheck.isSelected();
            p.titleScreenGrid = gridCheck.isSelected();
            p.showTitlePreview = preview3dCheck.isSelected();

            p.showHud = masterHudCheck.isSelected();
            p.showFps = fpsCheck.isSelected();
            p.fpsCompact = fpsCompactCheck.isSelected();
            p.showCoords = coordsCheck.isSelected();
            p.showDirection = dirCheck.isSelected();
            p.toggleSprint = sprintCheck.isSelected();
            p.showSprintHud = sprintHudCheck.isSelected();
            p.showPingServer = pingCheck.isSelected();
            p.showBiome = biomeCheck.isSelected();
            p.showKeystrokes = keystrokesCheck.isSelected();
            p.hudTextShadow = shadowCheck.isSelected();

            p.hudCorner = Math.max(0, cornerBox.getSelectionModel().getSelectedIndex());
            p.hudAccentIndex = Math.max(0, accentBox.getSelectionModel().getSelectedIndex());
            p.hudOpacity = Math.max(0, opacityBox.getSelectionModel().getSelectedIndex());
            int sIdx = scaleBox.getSelectionModel().getSelectedIndex();
            p.hudScale = sIdx == 0 ? 0.85f : (sIdx == 2 ? 1.2f : 1.0f);

            service.save(store);
            actions.onProfileUpdated();
        };

        // Wire controls
        for (CheckBox cb : List.of(customTitleCheck, gridCheck, preview3dCheck, masterHudCheck, fpsCheck,
                fpsCompactCheck, coordsCheck, dirCheck, sprintCheck, sprintHudCheck, pingCheck, biomeCheck,
                keystrokesCheck, shadowCheck)) {
            cb.setOnAction(e -> persistUiChanges.run());
        }
        cornerBox.setOnAction(e -> persistUiChanges.run());
        accentBox.setOnAction(e -> persistUiChanges.run());
        opacityBox.setOnAction(e -> persistUiChanges.run());
        scaleBox.setOnAction(e -> persistUiChanges.run());

        classicBtn.setOnAction(e -> {
            if (!classicBtn.isSelected()) classicBtn.setSelected(true);
            persistUiChanges.run();
        });
        slimBtn.setOnAction(e -> {
            if (!slimBtn.isSelected()) slimBtn.setSelected(true);
            persistUiChanges.run();
        });

        wardrobeBox.setOnAction(e -> {
            if (populating[0] || activeRef[0] == null) return;
            Path sel = wardrobeBox.getValue();
            activeRef[0].skinFilePath = sel == null ? "" : sel.toAbsolutePath().toString();
            service.save(store);
            String accountSkin = auth != null ? auth.skinUrl : null;
            skinCanvas.loadFromPathOrUrl(sel, accountSkin);
            statusLabel.setText(sel == null
                    ? "Using Microsoft account / default skin."
                    : "Selected local skin \"" + sel.getFileName() + "\" for this profile.");
            actions.onProfileUpdated();
        });

        importSkinBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Import Minecraft Skin PNG (64x64 or 64x32)");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Minecraft Skin PNG", "*.png"));
            File chosen = fc.showOpenDialog(primaryStage);
            if (chosen == null) return;
            try {
                Path imported = skinService.importSkinFile(chosen.toPath());
                List<Path> refreshed = skinService.listSavedSkins();
                wardrobeBox.setItems(FXCollections.observableArrayList(refreshed));
                activeRef[0].skinFilePath = imported.toAbsolutePath().toString();
                service.save(store);
                loadIntoUi.run();
                statusLabel.setText("Imported skin: " + imported.getFileName());
            } catch (Exception ex) {
                UiUtils.showError(ex.getMessage());
            }
        });

        useAccountSkinBtn.setOnAction(e -> {
            if (activeRef[0] == null) return;
            activeRef[0].skinFilePath = "";
            service.save(store);
            loadIntoUi.run();
            statusLabel.setText("Switched profile to Microsoft account / default skin.");
        });

        uploadMojangBtn.setOnAction(e -> {
            if (auth == null || auth.minecraftAccessToken == null) return;
            if (activeRef[0] == null || activeRef[0].skinFilePath == null || activeRef[0].skinFilePath.isBlank()) {
                UiUtils.showError("Import or select a local .png skin in the Wardrobe first to upload it to Mojang.");
                return;
            }
            Path skinPath = Path.of(activeRef[0].skinFilePath);
            String variant = slimBtn.isSelected() ? "slim" : "classic";
            uploadMojangBtn.setDisable(true);
            statusLabel.setText("Uploading skin to Mojang Services...");

            Thread t = new Thread(() -> {
                try {
                    MinecraftSkinService.MojangCosmetics cos =
                            skinService.uploadSkin(auth.minecraftAccessToken, skinPath, variant);
                    cachedCosmetics = cos;
                    if (cos.activeSkinUrl() != null) {
                        auth.skinUrl = cos.activeSkinUrl();
                    }
                    Platform.runLater(() -> {
                        uploadMojangBtn.setDisable(false);
                        statusLabel.setText("Skin uploaded to your Mojang account!");
                        actions.onProfileUpdated();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        uploadMojangBtn.setDisable(false);
                        UiUtils.showError(ex.getMessage());
                    });
                }
            }, "JJN-Skin-Upload");
            t.setDaemon(true);
            t.start();
        });

        capeBox.setOnAction(e -> {
            if (populating[0]) return;
            MinecraftSkinService.CapeInfo cape = capeBox.getValue();
            if (cape == null) return;
            equipSelectedCape.accept(cape);
        });

        applyCapeBtn.setOnAction(e -> {
            MinecraftSkinService.CapeInfo cape = capeBox.getValue();
            equipSelectedCape.accept(cape);
        });

        // Profile actions
        profilePicker.setOnAction(e -> {
            if (populating[0]) return;
            ClientProfile sel = profilePicker.getValue();
            if (sel != null) {
                activeRef[0] = sel;
                store.activeProfileId = sel.id;
                service.save(store);
                loadIntoUi.run();
                actions.onProfileUpdated();
                statusLabel.setText("Switched active profile to \"" + sel.name + "\".");
            }
        });

        newBtn.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog("Custom Profile");
            dialog.setTitle("New Profile");
            dialog.setHeaderText("Create a JJN Client profile");
            dialog.setContentText("Profile name:");
            UiUtils.styleDialog(dialog);
            Optional<String> res = dialog.showAndWait();
            if (res.isPresent() && !res.get().trim().isEmpty()) {
                ClientProfile created = service.createNew(store, res.get().trim());
                activeRef[0] = created;
                profilePicker.setItems(FXCollections.observableArrayList(store.profiles));
                profilePicker.setValue(created);
                loadIntoUi.run();
                actions.onProfileUpdated();
                statusLabel.setText("Created profile \"" + created.name + "\".");
            }
        });

        dupBtn.setOnAction(e -> {
            if (activeRef[0] == null) return;
            ClientProfile copy = service.createNew(store, activeRef[0].name + " Copy");
            activeRef[0] = copy;
            profilePicker.setItems(FXCollections.observableArrayList(store.profiles));
            profilePicker.setValue(copy);
            loadIntoUi.run();
            actions.onProfileUpdated();
            statusLabel.setText("Duplicated profile as \"" + copy.name + "\".");
        });

        renameBtn.setOnAction(e -> {
            if (activeRef[0] == null) return;
            TextInputDialog dialog = new TextInputDialog(activeRef[0].name);
            dialog.setTitle("Rename Profile");
            dialog.setHeaderText("Rename \"" + activeRef[0].name + "\"");
            dialog.setContentText("New name:");
            UiUtils.styleDialog(dialog);
            dialog.showAndWait().ifPresent(name -> {
                String clean = name.trim();
                if (!clean.isEmpty()) {
                    activeRef[0].name = clean;
                    service.save(store);
                    profilePicker.setItems(FXCollections.observableArrayList(store.profiles));
                    profilePicker.setValue(activeRef[0]);
                    actions.onProfileUpdated();
                }
            });
        });

        deleteBtn.setOnAction(e -> {
            if (activeRef[0] == null || store.profiles.size() <= 1) return;
            ClientProfile target = activeRef[0];
            store.profiles.remove(target);
            activeRef[0] = store.profiles.get(0);
            store.activeProfileId = activeRef[0].id;
            service.save(store);
            profilePicker.setItems(FXCollections.observableArrayList(store.profiles));
            profilePicker.setValue(activeRef[0]);
            loadIntoUi.run();
            actions.onProfileUpdated();
            statusLabel.setText("Deleted profile \"" + target.name + "\".");
        });

        applyBtn.setOnAction(e -> {
            if (activeRef[0] == null || instance == null) return;
            try {
                service.applyToInstance(activeRef[0], instance);
                statusLabel.setText("Applied \"" + activeRef[0].name + "\" preset, skin & cape to \"" + instance.name + "\"!");
            } catch (Exception ex) {
                UiUtils.showError("Could not apply profile: " + ex.getMessage());
            }
        });

        pullBtn.setOnAction(e -> {
            if (activeRef[0] == null || instance == null) return;
            if (service.importFromInstance(activeRef[0], instance)) {
                service.save(store);
                loadIntoUi.run();
                statusLabel.setText("Imported HUD & module settings from \"" + instance.name + "\" into \"" + activeRef[0].name + "\".");
            } else {
                statusLabel.setText("No saved config/jjnclient.properties found yet in \"" + instance.name + "\".");
            }
        });

        // Async load Mojang capes/variant if signed in
        if (auth != null && auth.minecraftAccessToken != null) {
            Thread fetchThread = new Thread(() -> {
                try {
                    MinecraftSkinService.MojangCosmetics cos = skinService.fetchMojangCosmetics(auth.minecraftAccessToken);
                    Platform.runLater(() -> applyCosmeticsToUi.accept(cos));
                } catch (Exception ex) {
                    Platform.runLater(() -> statusLabel.setText("Could not load Mojang capes: " + ex.getMessage()));
                }
            }, "JJN-Cosmetics-Fetch");
            fetchThread.setDaemon(true);
            fetchThread.start();
        }

        loadIntoUi.run();

        HBox split = new HBox(16, leftCard, rightCard);
        VBox.setVgrow(split, Priority.ALWAYS);

        VBox container = new VBox(14, topBar, statusLabel, split);
        VBox.setVgrow(container, Priority.ALWAYS);
        return container;
    }
}
