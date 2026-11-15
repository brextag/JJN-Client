package com.jjn.launcher.ui;

import com.jjn.launcher.MicrosoftAuthService;
import com.jjn.launcher.model.ClientProfile;
import com.jjn.launcher.service.ProfileService;
import com.jjn.launcher.shop.EconomyService;
import com.jjn.launcher.shop.EconomyStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Dedicated Emotes tab for previewing 3D emotes, unlocking new ones with JJN Coins,
 * and assigning unlocked emotes to the 8-slot in-game Emote Wheel ([B] key).
 */
public class EmotesView {

    public interface Actions {
        EconomyService economyService();
        ProfileService.ProfileStore profileStore();
        MicrosoftAuthService.AuthResult authResult();
        void openShopTab();
    }

    private Actions actions;
    private SkinPreviewCanvas previewCanvas;
    private Label previewTitleLabel;
    private Label coinsLabel;
    private Label feedbackLabel;
    private GridPane wheelGrid;
    private GridPane emotesGrid;
    private int selectedSlot = 0;
    private String previewingEmoteId = "";

    public Node build(Actions actions) {
        this.actions = actions;

        EconomyService economy = actions.economyService();
        economy.reloadFromDisk();

        // Top Header Bar
        coinsLabel = new Label();
        coinsLabel.setStyle(
                "-fx-background-color: #202432; -fx-border-color: #c7d2fe; -fx-border-radius: 10;"
                        + "-fx-background-radius: 10; -fx-padding: 7 14 7 14;"
                        + "-fx-text-fill: #c7d2fe; -fx-font-size: 13px; -fx-font-weight: bold;"
        );

        Label keyHint = UiUtils.mutedLabel("In-Game Keybind: Press [B] for Emote Wheel  •  Press [U] for Waypoints");
        Button goShopBtn = UiUtils.secondaryButton("Earn Coins in Quests & Shop");
        goShopBtn.setOnAction(e -> actions.openShopTab());

        Region headerGap = new Region();
        HBox.setHgrow(headerGap, Priority.ALWAYS);

        HBox topBar = new HBox(12, coinsLabel, keyHint, headerGap, goShopBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("selection-bar");

        feedbackLabel = new Label();
        feedbackLabel.setWrapText(true);
        feedbackLabel.setStyle("-fx-text-fill: #86efac; -fx-font-size: 12px; -fx-font-weight: bold;");
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);

        // Left: 3D Animated Emote Stage
        previewCanvas = new SkinPreviewCanvas(230, 285);
        syncCanvasAppearance(economy.snapshot());

        StackPane stageFrame = new StackPane(previewCanvas);
        stageFrame.setPadding(new Insets(8));
        stageFrame.setStyle(
                "-fx-background-color: #101116; -fx-background-radius: 14;"
                        + "-fx-border-color: #2b3040; -fx-border-radius: 14;"
        );

        previewTitleLabel = new Label("Idle Stance (Click any emote to preview)");
        previewTitleLabel.setWrapText(true);
        previewTitleLabel.setStyle("-fx-text-fill: #e5e7eb; -fx-font-size: 12px; -fx-font-weight: bold;");

        Button replayBtn = UiUtils.secondaryButton("Replay");
        replayBtn.setOnAction(e -> {
            if (!previewingEmoteId.isEmpty()) {
                previewCanvas.playEmote(previewingEmoteId, true);
            }
        });
        Button stopBtn = UiUtils.secondaryButton("Stop");
        stopBtn.setOnAction(e -> {
            previewingEmoteId = "";
            previewCanvas.stopEmote();
            previewTitleLabel.setText("Idle Stance (Click any emote to preview)");
        });
        Button flipBtn = UiUtils.secondaryButton("Flip");
        flipBtn.setOnAction(e -> previewCanvas.flipView());

        HBox stageBtns = new HBox(8, replayBtn, stopBtn, flipBtn);
        stageBtns.setAlignment(Pos.CENTER);

        Label dragHint = UiUtils.mutedLabel("Drag character to rotate 3D camera");

        VBox leftCard = UiUtils.card(
                UiUtils.sectionTitle("3D Emote Stage"),
                stageFrame,
                previewTitleLabel,
                stageBtns,
                dragHint
        );
        leftCard.setPrefWidth(300);
        leftCard.setMinWidth(290);

        // Right: Emote Wheel Slots + Emotes Library
        Label wheelTitle = UiUtils.sectionTitle("Emote Wheel Slots (Press [B] In-Game)");
        Label wheelSub = UiUtils.mutedLabel("Select a wheel slot [1-8], then click \"Bind to Slot\" on any unlocked emote below.");

        wheelGrid = new GridPane();
        wheelGrid.setHgap(8);
        wheelGrid.setVgap(8);
        for (int i = 0; i < 4; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(25);
            wheelGrid.getColumnConstraints().add(col);
        }

        VBox wheelCard = UiUtils.card(wheelTitle, wheelSub, wheelGrid);

        emotesGrid = new GridPane();
        emotesGrid.setHgap(12);
        emotesGrid.setVgap(12);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(50);
            emotesGrid.getColumnConstraints().add(col);
        }

        VBox libraryWrap = new VBox(12, wheelCard, UiUtils.sectionTitle("All 3D Emotes"), emotesGrid);
        libraryWrap.setPadding(new Insets(2, 4, 8, 2));

        ScrollPane rightScroll = new ScrollPane(libraryWrap);
        rightScroll.setFitToWidth(true);
        rightScroll.getStyleClass().add("scroll-pane");
        HBox.setHgrow(rightScroll, Priority.ALWAYS);

        HBox body = new HBox(18, leftCard, rightScroll);
        VBox.setVgrow(body, Priority.ALWAYS);

        VBox root = new VBox(12, topBar, feedbackLabel, body);
        VBox.setVgrow(root, Priority.ALWAYS);

        refreshDynamicContent();
        return root;
    }

    public void onLeave() {
        if (previewCanvas != null) {
            previewCanvas.stopAnimations();
        }
    }

    public void refreshDynamicContent() {
        if (actions == null || coinsLabel == null) return;
        EconomyService economy = actions.economyService();
        EconomyStore.EconomyData data = economy.snapshot();

        coinsLabel.setText("◈  " + data.coins + " JJN Coins");
        String msg = economy.getLastMessage();
        if (msg != null && !msg.isBlank()) {
            feedbackLabel.setText(msg);
            feedbackLabel.setVisible(true);
            feedbackLabel.setManaged(true);
        }

        syncCanvasAppearance(data);

        // Rebuild 8 Wheel Slots
        wheelGrid.getChildren().clear();
        for (int i = 0; i < 8; i++) {
            final int slotIdx = i;
            String boundId = i < data.emoteWheel.size() ? data.emoteWheel.get(i) : "";
            EconomyStore.CatalogItem boundItem = EconomyStore.findItem(boundId);
            String labelText = "[" + (i + 1) + "] " + (boundItem != null ? boundItem.name() : "Empty");

            Button slotBtn = new Button(labelText);
            slotBtn.setMaxWidth(Double.MAX_VALUE);
            slotBtn.setWrapText(true);
            boolean activeTarget = (i == selectedSlot);
            slotBtn.setStyle(activeTarget
                    ? "-fx-background-color: #2f3547; -fx-border-color: #c7d2fe; -fx-border-radius: 10;"
                    + "-fx-background-radius: 10; -fx-text-fill: #c7d2fe; -fx-font-weight: bold; -fx-padding: 9 10 9 10; -fx-cursor: hand;"
                    : "-fx-background-color: #15171e; -fx-border-color: #2b3040; -fx-border-radius: 10;"
                    + "-fx-background-radius: 10; -fx-text-fill: " + (boundItem != null ? "#e5e7eb" : "#666b7a")
                    + "; -fx-padding: 9 10 9 10; -fx-cursor: hand;"
            );
            slotBtn.setOnAction(e -> {
                selectedSlot = slotIdx;
                if (boundItem != null) {
                    startEmotePreview(boundItem);
                }
                refreshDynamicContent();
            });
            wheelGrid.add(slotBtn, i % 4, i / 4);
        }

        // Rebuild Emotes Grid
        emotesGrid.getChildren().clear();
        List<EconomyStore.CatalogItem> emotes = EconomyStore.CATALOG.stream()
                .filter(it -> it.category() == EconomyStore.Category.EMOTE)
                .toList();

        int idx = 0;
        for (EconomyStore.CatalogItem emote : emotes) {
            boolean owned = economy.isOwned(emote.id());
            int boundSlot = -1;
            for (int s = 0; s < data.emoteWheel.size(); s++) {
                if (emote.id().equals(data.emoteWheel.get(s))) {
                    boundSlot = s;
                    break;
                }
            }

            Label rarity = new Label(emote.rarity());
            rarity.setStyle(
                    "-fx-text-fill: " + emote.accentHex() + "; -fx-font-size: 10px; -fx-font-weight: bold;"
                            + "-fx-background-color: #15171e; -fx-background-radius: 6; -fx-padding: 3 8 3 8;"
            );

            Label priceOrOwned = new Label(owned
                    ? (boundSlot >= 0 ? "SLOT [" + (boundSlot + 1) + "]" : "UNLOCKED")
                    : "◈ " + emote.price());
            priceOrOwned.setStyle("-fx-text-fill: " + (owned ? "#86efac" : "#c7d2fe") + "; -fx-font-size: 11px; -fx-font-weight: bold;");

            Region rGap = new Region();
            HBox.setHgrow(rGap, Priority.ALWAYS);
            HBox topTags = new HBox(8, rarity, rGap, priceOrOwned);
            topTags.setAlignment(Pos.CENTER_LEFT);

            Label title = new Label(emote.name());
            title.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 15px; -fx-font-weight: bold;");

            Label desc = UiUtils.mutedLabel(emote.description());
            desc.setMinHeight(34);

            Button previewBtn = UiUtils.secondaryButton("Preview");
            previewBtn.setOnAction(e -> startEmotePreview(emote));

            Button actionBtn;
            if (!owned) {
                actionBtn = new Button("Unlock  ◈ " + emote.price());
                actionBtn.getStyleClass().add(data.coins >= emote.price() ? "primary-button" : "secondary-button");
                actionBtn.setPadding(new Insets(8, 12, 8, 12));
                actionBtn.setOnAction(e -> {
                    economy.purchaseItem(emote.id());
                    startEmotePreview(emote);
                    refreshDynamicContent();
                });
            } else if (boundSlot == selectedSlot) {
                actionBtn = UiUtils.dangerButton("Clear Slot [" + (selectedSlot + 1) + "]");
                actionBtn.setOnAction(e -> {
                    economy.setWheelSlot(selectedSlot, "");
                    refreshDynamicContent();
                });
            } else {
                actionBtn = new Button("Bind to Slot [" + (selectedSlot + 1) + "]");
                actionBtn.getStyleClass().add("primary-button");
                actionBtn.setPadding(new Insets(8, 12, 8, 12));
                actionBtn.setOnAction(e -> {
                    economy.setWheelSlot(selectedSlot, emote.id());
                    startEmotePreview(emote);
                    refreshDynamicContent();
                });
            }

            HBox btnRow = new HBox(8, previewBtn, actionBtn);
            btnRow.setAlignment(Pos.CENTER_LEFT);

            VBox card = UiUtils.card(topTags, title, desc, btnRow);
            card.setPadding(new Insets(14));
            if (emote.id().equals(previewingEmoteId)) {
                card.setStyle("-fx-background-color: #202432; -fx-background-radius: 15; -fx-border-color: #c7d2fe; -fx-border-radius: 15;");
            }
            emotesGrid.add(card, idx % 2, idx / 2);
            idx++;
        }
    }

    private void startEmotePreview(EconomyStore.CatalogItem emote) {
        previewingEmoteId = emote.id();
        previewTitleLabel.setText("Playing: " + emote.name());
        previewCanvas.playEmote(emote.id(), true);
    }

    private void syncCanvasAppearance(EconomyStore.EconomyData data) {
        if (previewCanvas == null || actions == null) return;
        ProfileService.ProfileStore pStore = actions.profileStore();
        ClientProfile profile = pStore != null ? pStore.findActive() : null;
        MicrosoftAuthService.AuthResult auth = actions.authResult();

        boolean slim = profile != null && "slim".equalsIgnoreCase(profile.skinVariant);
        previewCanvas.setSlim(slim);

        Path localSkin = (profile != null && profile.skinFilePath != null && !profile.skinFilePath.isBlank())
                ? Path.of(profile.skinFilePath)
                : null;
        if (localSkin != null && !Files.isRegularFile(localSkin)) localSkin = null;
        previewCanvas.loadFromPathOrUrl(localSkin, auth != null ? auth.skinUrl : null);

        if (data.equippedCapeId != null && !data.equippedCapeId.isBlank()) {
            previewCanvas.setCapeImage(EconomyStore.createCapePreviewImage(data.equippedCapeId));
        } else if (profile != null && profile.capeFilePath != null && !profile.capeFilePath.isBlank()) {
            previewCanvas.loadCapeFromPathOrUrl(Path.of(profile.capeFilePath), profile.capeUrl);
        } else {
            previewCanvas.setCapeImage(null);
        }
        previewCanvas.setCosmetics(data.equippedBackId, data.equippedHatId);
    }
}
