package com.jjn.launcher.ui;

import com.jjn.launcher.model.Instance;
import com.jjn.launcher.service.KeybindsStore;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Full Minecraft & Modded Keybinds + Controls editor backed by each instance's options.txt.
 */
public class KeybindsView {

    public interface Actions {
        List<Instance> instances();
        Instance selectedInstance();
        void onSelectInstance(Instance instance);
    }

    private static final String FILTER_ALL = "All";
    private static final String FILTER_CONFLICTS = "Conflicts";

    private Actions actions;
    private Instance activeInstance;
    private KeybindsStore.OptionsSnapshot snapshot;

    private String selectedCategory = FILTER_ALL;
    private String searchQuery = "";
    private boolean loadingControls = false;

    private Label feedbackLabel;
    private Label fileMetaLabel;
    private Slider sensitivitySlider;
    private Label sensitivityValueLabel;
    private CheckBox toggleSneakCheck;
    private CheckBox toggleSprintCheck;
    private CheckBox autoJumpCheck;
    private CheckBox invertYCheck;
    private CheckBox rawMouseCheck;
    private FlowPane categoryBar;
    private VBox keybindsListBox;

    // Active key capture state
    private KeybindsStore.KeybindEntry listeningEntry;
    private Button listeningButton;
    private boolean listeningArmed = false;
    private Scene listeningScene;
    private EventHandler<KeyEvent> sceneKeyFilter;
    private EventHandler<KeyEvent> sceneKeyTypedFilter;
    private EventHandler<MouseEvent> sceneMouseFilter;

    public Node build(Actions actions) {
        stopListening();
        this.actions = actions;

        List<Instance> all = actions.instances();
        activeInstance = actions.selectedInstance();
        if (activeInstance == null && !all.isEmpty()) {
            activeInstance = all.get(0);
        }

        if (all.isEmpty()) {
            return UiUtils.card(
                    UiUtils.sectionTitle("No Instances Found"),
                    UiUtils.mutedLabel("Create or install a Minecraft instance first, then manage all of its keybinds and controls here.")
            );
        }

        // Top instance & bulk actions bar
        Label instLbl = new Label("INSTANCE");
        instLbl.getStyleClass().add("accent-text");

        ComboBox<Instance> instanceBox = UiUtils.instanceComboBox(all, activeInstance);
        instanceBox.setPrefWidth(290);
        instanceBox.setOnAction(e -> {
            Instance picked = instanceBox.getValue();
            if (picked != null) {
                stopListening();
                activeInstance = picked;
                actions.onSelectInstance(picked);
                loadAndRender();
            }
        });

        Button reloadBtn = UiUtils.secondaryButton("Reload");
        reloadBtn.setMinWidth(Region.USE_PREF_SIZE);
        reloadBtn.setOnAction(e -> {
            stopListening();
            loadAndRender();
            setFeedback("Reloaded keybinds from " + activeInstance.name + ".", false);
        });

        Button addCustomBtn = UiUtils.secondaryButton("+ Add Keybind");
        addCustomBtn.setMinWidth(Region.USE_PREF_SIZE);
        addCustomBtn.setOnAction(e -> openAddCustomKeyDialog());

        Button applyAllBtn = UiUtils.secondaryButton("Apply to All Instances");
        applyAllBtn.setMinWidth(Region.USE_PREF_SIZE);
        applyAllBtn.setOnAction(e -> applyToAllInstances());

        Button resetAllBtn = UiUtils.dangerButton("Reset All Defaults");
        resetAllBtn.setMinWidth(Region.USE_PREF_SIZE);
        resetAllBtn.setOnAction(e -> resetAllDefaults());

        Button openFolderBtn = UiUtils.secondaryButton("Open Game Folder");
        openFolderBtn.setMinWidth(Region.USE_PREF_SIZE);
        openFolderBtn.setOnAction(e -> UiUtils.openInExplorer(activeInstance.gameFolder()));

        FlowPane topActions = new FlowPane(8, 8, reloadBtn, addCustomBtn, applyAllBtn, resetAllBtn, openFolderBtn);
        topActions.setAlignment(Pos.CENTER_LEFT);

        HBox selectorRow = new HBox(10, instLbl, instanceBox);
        selectorRow.setAlignment(Pos.CENTER_LEFT);

        VBox topBar = new VBox(10, selectorRow, topActions);
        topBar.getStyleClass().add("selection-bar");

        feedbackLabel = new Label();
        feedbackLabel.setWrapText(true);
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);

        fileMetaLabel = UiUtils.mutedLabel("");

        // Controls & Mouse Card
        sensitivitySlider = new Slider(0, 200, 100);
        sensitivitySlider.setPrefWidth(240);
        sensitivitySlider.setBlockIncrement(5);

        sensitivityValueLabel = new Label("100% (Default)");
        sensitivityValueLabel.getStyleClass().add("accent-value");

        sensitivitySlider.valueProperty().addListener((obs, oldV, newV) -> {
            updateSensitivityLabel(newV.intValue());
            if (!loadingControls && snapshot != null && !sensitivitySlider.isValueChanging()) {
                commitControlsChange();
            }
        });
        sensitivitySlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging && !loadingControls && snapshot != null) {
                commitControlsChange();
            }
        });

        HBox sensRow = new HBox(12, new Label("Mouse Sensitivity:") {{ getStyleClass().add("field-label"); }}, sensitivitySlider, sensitivityValueLabel);
        sensRow.setAlignment(Pos.CENTER_LEFT);

        toggleSneakCheck = new CheckBox("Toggle Sneak");
        toggleSprintCheck = new CheckBox("Toggle Sprint");
        autoJumpCheck = new CheckBox("Auto-Jump");
        invertYCheck = new CheckBox("Invert Y Mouse");
        rawMouseCheck = new CheckBox("Raw Mouse Input");

        for (CheckBox cb : List.of(toggleSneakCheck, toggleSprintCheck, autoJumpCheck, invertYCheck, rawMouseCheck)) {
            cb.setOnAction(e -> {
                if (!loadingControls && snapshot != null) {
                    commitControlsChange();
                }
            });
        }

        FlowPane togglesRow = new FlowPane(16, 8, toggleSneakCheck, toggleSprintCheck, autoJumpCheck, invertYCheck, rawMouseCheck);
        togglesRow.setAlignment(Pos.CENTER_LEFT);

        VBox controlsCard = UiUtils.card(
                UiUtils.sectionTitle("Mouse & Movement Controls"),
                sensRow,
                togglesRow
        );

        // Search + Category filter card
        TextField searchField = new TextField(searchQuery);
        searchField.getStyleClass().add("input-field");
        searchField.setPromptText("Search actions, key names, or option IDs (e.g. jump, shift, f3, modmenu)...");
        searchField.textProperty().addListener((obs, oldV, newV) -> {
            searchQuery = newV == null ? "" : newV.trim();
            renderKeybindRows();
        });

        categoryBar = new FlowPane(6, 6);
        categoryBar.getStyleClass().add("segmented-group");

        VBox filterCard = UiUtils.card(
                UiUtils.sectionTitle("Keybinds"),
                UiUtils.mutedLabel("Click any binding button, then press any keyboard key or click inside that button for Mouse 1-5 (ESC = Unbind)."),
                searchField,
                categoryBar
        );

        keybindsListBox = new VBox(8);
        ScrollPane listScroll = new ScrollPane(keybindsListBox);
        listScroll.setFitToWidth(true);
        listScroll.getStyleClass().add("scroll-pane");
        VBox.setVgrow(listScroll, Priority.ALWAYS);

        VBox root = new VBox(12, topBar, fileMetaLabel, feedbackLabel, controlsCard, filterCard, listScroll);
        VBox.setVgrow(root, Priority.ALWAYS);

        loadAndRender();
        return root;
    }

    public void onLeave() {
        stopListening();
    }

    private void loadAndRender() {
        if (activeInstance == null) return;
        snapshot = KeybindsStore.load(activeInstance);

        loadingControls = true;
        try {
            KeybindsStore.ControlOptions c = snapshot.controls;
            int pct = (int) Math.round(KeybindsStore.clampSensitivity(c.mouseSensitivity) * 200.0);
            sensitivitySlider.setValue(pct);
            updateSensitivityLabel(pct);
            toggleSneakCheck.setSelected(c.toggleCrouch);
            toggleSprintCheck.setSelected(c.toggleSprint);
            autoJumpCheck.setSelected(c.autoJump);
            invertYCheck.setSelected(c.invertYMouse);
            rawMouseCheck.setSelected(c.rawMouseInput);
        } finally {
            loadingControls = false;
        }

        if (snapshot.fileExisted) {
            fileMetaLabel.setText(
                    "Editing " + snapshot.optionsFile + "  •  " + snapshot.entries.size() + " keybinds loaded"
            );
        } else {
            fileMetaLabel.setText(
                    "No options.txt yet for " + activeInstance.name
                            + " — showing Minecraft defaults. Any edit will create " + snapshot.optionsFile
            );
        }

        renderCategoryPills();
        renderKeybindRows();
    }

    private void updateSensitivityLabel(int pct) {
        String tag = pct == 100 ? " (Default)" : (pct == 200 ? " (HYPERSPEED)" : (pct == 0 ? " (*yawn*)" : ""));
        sensitivityValueLabel.setText(pct + "%" + tag);
    }

    private void commitControlsChange() {
        if (snapshot == null || activeInstance == null) return;
        KeybindsStore.ControlOptions c = snapshot.controls;
        c.mouseSensitivity = KeybindsStore.clampSensitivity(sensitivitySlider.getValue() / 200.0);
        c.toggleCrouch = toggleSneakCheck.isSelected();
        c.toggleSprint = toggleSprintCheck.isSelected();
        c.autoJump = autoJumpCheck.isSelected();
        c.invertYMouse = invertYCheck.isSelected();
        c.rawMouseInput = rawMouseCheck.isSelected();
        saveCurrentSnapshot("Saved control settings to " + activeInstance.name + ".");
    }

    private void renderCategoryPills() {
        if (categoryBar == null || snapshot == null) return;
        categoryBar.getChildren().clear();

        KeybindsStore.ConflictReport conflicts = KeybindsStore.analyzeConflicts(snapshot.entries);
        ToggleGroup group = new ToggleGroup();

        List<String> cats = new ArrayList<>();
        cats.add(FILTER_ALL);
        cats.addAll(KeybindsStore.CATEGORIES);
        cats.add(FILTER_CONFLICTS);

        for (String cat : cats) {
            String label = FILTER_CONFLICTS.equals(cat)
                    ? "Conflicts (" + conflicts.count() + ")"
                    : cat;
            ToggleButton tb = new ToggleButton(label);
            tb.getStyleClass().add("pill-toggle");
            tb.setMinWidth(Region.USE_PREF_SIZE);
            tb.setToggleGroup(group);
            tb.setSelected(cat.equals(selectedCategory));
            tb.setOnAction(e -> {
                stopListening();
                selectedCategory = cat;
                renderKeybindRows();
            });
            categoryBar.getChildren().add(tb);
        }
    }

    private void renderKeybindRows() {
        if (keybindsListBox == null || snapshot == null) return;
        stopListening();
        keybindsListBox.getChildren().clear();

        KeybindsStore.ConflictReport conflicts = KeybindsStore.analyzeConflicts(snapshot.entries);
        String q = searchQuery.toLowerCase(Locale.ROOT);

        String lastCategoryHeader = null;
        int renderedCount = 0;

        for (KeybindsStore.KeybindEntry entry : snapshot.entries) {
            boolean conflicted = conflicts.isConflicting(entry.optionKey);
            if (FILTER_CONFLICTS.equals(selectedCategory)) {
                if (!conflicted) continue;
            } else if (!FILTER_ALL.equals(selectedCategory)) {
                if (!entry.category.equalsIgnoreCase(selectedCategory)) continue;
            }

            if (!q.isEmpty()) {
                String hay = (entry.label + " " + entry.optionKey + " " + entry.category + " "
                        + KeybindsStore.formatKeyDisplay(entry.value) + " " + entry.value)
                        .toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) continue;
            }

            if (FILTER_ALL.equals(selectedCategory) && !entry.category.equals(lastCategoryHeader)) {
                lastCategoryHeader = entry.category;
                Label hdr = UiUtils.sectionTitle(entry.category);
                hdr.setPadding(new Insets(renderedCount == 0 ? 0 : 8, 0, 2, 2));
                keybindsListBox.getChildren().add(hdr);
            }

            keybindsListBox.getChildren().add(buildKeybindRow(entry, conflicted));
            renderedCount++;
        }

        if (renderedCount == 0) {
            String emptyMsg = FILTER_CONFLICTS.equals(selectedCategory)
                    ? "No keybind conflicts detected!"
                    : ( "Modded".equals(selectedCategory)
                        ? "No extra modded key_* entries found in options.txt yet. Launch with mods once or click '+ Add Keybind'."
                        : "No keybinds match your current filter." );
            keybindsListBox.getChildren().add(UiUtils.card(UiUtils.mutedLabel(emptyMsg)));
        }
    }

    private HBox buildKeybindRow(KeybindsStore.KeybindEntry entry, boolean conflicted) {
        Label nameLbl = new Label(entry.label);
        nameLbl.getStyleClass().add("card-title");

        HBox titleLine = new HBox(8, nameLbl);
        titleLine.setAlignment(Pos.CENTER_LEFT);
        if (conflicted) {
            Label badge = new Label("CONFLICT");
            badge.getStyleClass().add("badge-warning");
            titleLine.getChildren().add(badge);
        }
        if (entry.custom) {
            Label badge = new Label("CUSTOM / MOD");
            badge.getStyleClass().add("badge-accent");
            titleLine.getChildren().add(badge);
        }

        Label idLbl = UiUtils.mutedLabel(entry.optionKey + "  •  " + entry.value);
        VBox left = new VBox(2, titleLine, idLbl);
        left.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button bindBtn = new Button(KeybindsStore.formatKeyDisplay(entry.value));
        bindBtn.getStyleClass().add(conflicted ? "danger-button" : "secondary-button");
        bindBtn.setMinWidth(Region.USE_PREF_SIZE);
        bindBtn.setPrefWidth(180);
        bindBtn.setOnAction(e -> startListening(entry, bindBtn));

        Button pickBtn = UiUtils.secondaryButton("Pick...");
        pickBtn.setMinWidth(Region.USE_PREF_SIZE);
        pickBtn.setOnAction(e -> openPickKeyDialog(entry));

        Button unbindBtn = UiUtils.secondaryButton("Unbind");
        unbindBtn.setMinWidth(Region.USE_PREF_SIZE);
        unbindBtn.setDisable(KeybindsStore.UNKNOWN_KEY.equals(entry.value));
        unbindBtn.setOnAction(e -> {
            stopListening();
            entry.value = KeybindsStore.UNKNOWN_KEY;
            saveCurrentSnapshot("Unbound \"" + entry.label + "\".");
            renderCategoryPills();
            renderKeybindRows();
        });

        Button resetBtn = UiUtils.secondaryButton("Reset");
        resetBtn.setMinWidth(Region.USE_PREF_SIZE);
        resetBtn.setDisable(entry.defaultValue.equals(entry.value));
        resetBtn.setOnAction(e -> {
            stopListening();
            entry.value = entry.defaultValue;
            saveCurrentSnapshot("Reset \"" + entry.label + "\" to " + KeybindsStore.formatKeyDisplay(entry.defaultValue) + ".");
            renderCategoryPills();
            renderKeybindRows();
        });

        FlowPane controlsRow = new FlowPane(6, 6, bindBtn, pickBtn, unbindBtn, resetBtn);
        controlsRow.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(12, left, spacer, controlsRow);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("selection-bar");
        return row;
    }

    private void startListening(KeybindsStore.KeybindEntry entry, Button bindBtn) {
        if (listeningEntry == entry && listeningButton == bindBtn && listeningArmed) {
            return;
        }
        stopListening();

        Scene scene = bindBtn.getScene();
        if (scene == null) return;

        listeningEntry = entry;
        listeningButton = bindBtn;
        listeningArmed = false;
        listeningScene = scene;

        bindBtn.getStyleClass().setAll("button", "primary-button");
        bindBtn.setPadding(new Insets(8, 12, 8, 12));
        bindBtn.setText("> Press key / click <");

        sceneKeyFilter = ev -> {
            ev.consume();
            String mapped = KeybindsStore.fromJavaFxKeyEvent(ev);
            if (mapped == null) {
                setFeedback("Unsupported key (" + ev.getCode() + ") — use Pick... to select or type a Minecraft key.", true);
                stopListening();
                renderKeybindRows();
                return;
            }
            applyCapturedBinding(entry, mapped);
        };
        sceneKeyTypedFilter = KeyEvent::consume;

        sceneMouseFilter = ev -> {
            if (!listeningArmed) return;
            if (isDescendant(bindBtn, ev.getTarget())) {
                ev.consume();
                String mapped = KeybindsStore.fromJavaFxMouseButton(ev.getButton());
                if (mapped != null) {
                    applyCapturedBinding(entry, mapped);
                } else {
                    stopListening();
                    renderKeybindRows();
                }
            } else {
                stopListening();
                renderKeybindRows();
            }
        };

        scene.addEventFilter(KeyEvent.KEY_PRESSED, sceneKeyFilter);
        scene.addEventFilter(KeyEvent.KEY_TYPED, sceneKeyTypedFilter);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, sceneMouseFilter);

        Platform.runLater(() -> listeningArmed = true);
    }

    private void applyCapturedBinding(KeybindsStore.KeybindEntry entry, String mcKey) {
        stopListening();
        entry.value = mcKey;
        String msg = KeybindsStore.UNKNOWN_KEY.equals(mcKey)
                ? "Unbound \"" + entry.label + "\"."
                : "Bound \"" + entry.label + "\" to " + KeybindsStore.formatKeyDisplay(mcKey) + ".";
        saveCurrentSnapshot(msg);
        renderCategoryPills();
        renderKeybindRows();
    }

    private void stopListening() {
        listeningArmed = false;
        if (listeningScene != null) {
            if (sceneKeyFilter != null) listeningScene.removeEventFilter(KeyEvent.KEY_PRESSED, sceneKeyFilter);
            if (sceneKeyTypedFilter != null) listeningScene.removeEventFilter(KeyEvent.KEY_TYPED, sceneKeyTypedFilter);
            if (sceneMouseFilter != null) listeningScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, sceneMouseFilter);
        }
        sceneKeyFilter = null;
        sceneKeyTypedFilter = null;
        sceneMouseFilter = null;
        listeningScene = null;
        listeningEntry = null;
        listeningButton = null;
    }

    private static boolean isDescendant(Node parent, Object target) {
        if (!(target instanceof Node n)) return false;
        Node cur = n;
        while (cur != null) {
            if (cur == parent) return true;
            cur = cur.getParent();
        }
        return false;
    }

    private void openPickKeyDialog(KeybindsStore.KeybindEntry entry) {
        stopListening();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Pick Key - " + entry.label);
        dialog.setHeaderText("Choose a preset or enter any raw Minecraft key ID for " + entry.label);
        ButtonType applyBtn = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyBtn, ButtonType.CANCEL);
        UiUtils.styleDialog(dialog);

        TextField rawField = new TextField(entry.value);
        rawField.getStyleClass().add("input-field");
        rawField.setPromptText("e.g. key.keyboard.right.shift or key.mouse.4");

        FlowPane quickGrid = new FlowPane(8, 8);
        for (KeybindsStore.SpecialKeyOption opt : KeybindsStore.SPECIAL_KEY_OPTIONS) {
            Button btn = UiUtils.secondaryButton(opt.displayName());
            btn.setMinWidth(Region.USE_PREF_SIZE);
            btn.setOnAction(e -> rawField.setText(opt.mcKey()));
            quickGrid.getChildren().add(btn);
        }

        VBox content = new VBox(
                10,
                UiUtils.mutedLabel("Quick Select (Right modifiers, Mouse 1-5, Special keys):"),
                quickGrid,
                new Label("Raw Minecraft Key ID (options.txt value):") {{ getStyleClass().add("field-label"); }},
                rawField
        );
        content.setPrefWidth(520);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(res -> {
            if (res != applyBtn) return;
            String val = rawField.getText().trim();
            if (val.isEmpty()) val = KeybindsStore.UNKNOWN_KEY;
            entry.value = val;
            saveCurrentSnapshot("Updated \"" + entry.label + "\" to " + KeybindsStore.formatKeyDisplay(val) + ".");
            renderCategoryPills();
            renderKeybindRows();
        });
    }

    private void openAddCustomKeyDialog() {
        stopListening();
        if (snapshot == null || activeInstance == null) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Custom Keybind");
        dialog.setHeaderText("Add or pre-bind any modded key_* entry in options.txt");
        ButtonType addBtn = new ButtonType("Add Keybind", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);
        UiUtils.styleDialog(dialog);

        TextField keyIdField = new TextField();
        keyIdField.getStyleClass().add("input-field");
        keyIdField.setPromptText("e.g. key.iris.toggleShaders or key_key.xaero.waypoints");

        TextField keyValueField = new TextField(KeybindsStore.UNKNOWN_KEY);
        keyValueField.getStyleClass().add("input-field");
        keyValueField.setPromptText("e.g. key.keyboard.k or key.mouse.4");

        VBox content = new VBox(
                10,
                UiUtils.mutedLabel("Prefix 'key_' / 'key_key.' is added automatically if omitted."),
                new Label("Keybind Option ID:") {{ getStyleClass().add("field-label"); }},
                keyIdField,
                new Label("Minecraft Key Value:") {{ getStyleClass().add("field-label"); }},
                keyValueField
        );
        content.setPrefWidth(480);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        Platform.runLater(keyIdField::requestFocus);

        dialog.showAndWait().ifPresent(res -> {
            if (res != addBtn) return;
            String normalizedKey = KeybindsStore.normalizeOptionKey(keyIdField.getText());
            if (normalizedKey.isEmpty()) {
                setFeedback("Enter a keybind ID first.", true);
                return;
            }
            String val = keyValueField.getText().trim();
            if (val.isEmpty()) val = KeybindsStore.UNKNOWN_KEY;

            KeybindsStore.KeybindEntry existing = null;
            for (KeybindsStore.KeybindEntry e : snapshot.entries) {
                if (e.optionKey.equals(normalizedKey)) {
                    existing = e;
                    break;
                }
            }
            if (existing != null) {
                existing.value = val;
            } else {
                snapshot.entries.add(new KeybindsStore.KeybindEntry(
                        normalizedKey,
                        KeybindsStore.prettifyOptionKey(normalizedKey),
                        "Modded",
                        KeybindsStore.UNKNOWN_KEY,
                        val,
                        true
                ));
            }
            saveCurrentSnapshot("Saved custom keybind " + normalizedKey + " = " + val + ".");
            selectedCategory = "Modded";
            renderCategoryPills();
            renderKeybindRows();
        });
    }

    private void applyToAllInstances() {
        stopListening();
        if (snapshot == null) return;
        List<Instance> all = actions.instances();
        if (all.size() <= 1) {
            saveCurrentSnapshot("Saved keybinds to " + activeInstance.name + ".");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Apply Keybinds to All Instances");
        confirm.setHeaderText("Copy keybinds & controls from \"" + activeInstance.name + "\" to all " + all.size() + " instances?");
        confirm.setContentText("Other options in each instance's options.txt (graphics, video, sound, language) will be preserved.");
        UiUtils.styleDialog(confirm);
        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) return;

        int updated = 0;
        for (Instance inst : all) {
            try {
                KeybindsStore.save(inst, snapshot.entries, snapshot.controls);
                updated++;
            } catch (IOException ex) {
                setFeedback("Failed saving to " + inst.name + ": " + ex.getMessage(), true);
                return;
            }
        }
        setFeedback("Applied all keybinds & controls to " + updated + " instance(s).", false);
    }

    private void resetAllDefaults() {
        stopListening();
        if (snapshot == null || activeInstance == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Reset Keybinds & Controls");
        confirm.setHeaderText("Reset all keybinds and controls for \"" + activeInstance.name + "\" to defaults?");
        confirm.setContentText("Standard Minecraft keys will return to vanilla defaults; custom/modded keys will be unbound.");
        UiUtils.styleDialog(confirm);
        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) return;

        for (KeybindsStore.KeybindEntry entry : snapshot.entries) {
            entry.value = entry.defaultValue;
        }
        snapshot.controls.toggleCrouch = false;
        snapshot.controls.toggleSprint = false;
        snapshot.controls.autoJump = false;
        snapshot.controls.invertYMouse = false;
        snapshot.controls.rawMouseInput = true;
        snapshot.controls.mouseSensitivity = 0.5;

        saveCurrentSnapshot("Reset all keybinds & controls to defaults for " + activeInstance.name + ".");
        loadAndRender();
    }

    private void saveCurrentSnapshot(String successMessage) {
        if (snapshot == null || activeInstance == null) return;
        try {
            KeybindsStore.save(activeInstance, snapshot.entries, snapshot.controls);
            fileMetaLabel.setText(
                    "Editing " + snapshot.optionsFile + "  •  " + snapshot.entries.size() + " keybinds loaded"
            );
            setFeedback(successMessage, false);
        } catch (IOException ex) {
            setFeedback("Could not write options.txt: " + ex.getMessage(), true);
        }
    }

    private void setFeedback(String message, boolean warning) {
        if (feedbackLabel == null) return;
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().setAll(warning ? "status-warning" : "status-success");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }
}
