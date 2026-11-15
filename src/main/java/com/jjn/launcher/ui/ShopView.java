package com.jjn.launcher.ui;

import com.jjn.launcher.MicrosoftAuthService;
import com.jjn.launcher.model.ClientProfile;
import com.jjn.launcher.service.ProfileService;
import com.jjn.launcher.shop.EconomyService;
import com.jjn.launcher.shop.EconomyStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Full Cosmetics Shop, Quests & JJN Coins Hub, and 3D Wardrobe View.
 */
public class ShopView {

    public enum SubTab {
        SHOP, QUESTS, WARDROBE
    }

    public interface Actions {
        EconomyService economyService();
        ProfileService.ProfileStore profileStore();
        MicrosoftAuthService.AuthResult authResult();
        void openEmotesTab();
    }

    private Actions actions;
    private SubTab currentTab = SubTab.SHOP;
    private EconomyStore.Category filterCategory = null; // null = All

    private SkinPreviewCanvas previewCanvas;
    private Label coinsLabel;
    private Label earnedLabel;
    private Label feedbackLabel;
    private Label previewInfoLabel;
    private Label auraBadgeLabel;
    private ToggleButton questsTabBtn;
    private VBox rightContentBox;

    // Preview overrides (empty = show currently equipped)
    private String previewCapeOverride = null;
    private String previewBackOverride = null;
    private String previewHatOverride = null;
    private String previewAuraOverride = null;

    public Node build(Actions actions) {
        this.actions = actions;
        EconomyService economy = actions.economyService();
        economy.reloadFromDisk();

        // Sub-tab segmented switcher
        ToggleGroup tabGroup = new ToggleGroup();
        ToggleButton shopTabBtn = new ToggleButton("Shop");
        questsTabBtn = new ToggleButton("Quests");
        ToggleButton wardrobeTabBtn = new ToggleButton("Wardrobe");
        for (ToggleButton b : List.of(shopTabBtn, questsTabBtn, wardrobeTabBtn)) {
            b.getStyleClass().add("pill-toggle");
            b.setToggleGroup(tabGroup);
        }
        shopTabBtn.setSelected(currentTab == SubTab.SHOP);
        questsTabBtn.setSelected(currentTab == SubTab.QUESTS);
        wardrobeTabBtn.setSelected(currentTab == SubTab.WARDROBE);

        shopTabBtn.setOnAction(e -> { currentTab = SubTab.SHOP; refreshDynamicContent(); });
        questsTabBtn.setOnAction(e -> { currentTab = SubTab.QUESTS; refreshDynamicContent(); });
        wardrobeTabBtn.setOnAction(e -> { currentTab = SubTab.WARDROBE; refreshDynamicContent(); });

        HBox tabSegment = new HBox(4, shopTabBtn, questsTabBtn, wardrobeTabBtn);
        tabSegment.getStyleClass().add("segmented-group");

        coinsLabel = new Label();
        coinsLabel.setStyle(
                "-fx-background-color: #202432; -fx-border-color: #c7d2fe; -fx-border-radius: 10;"
                        + "-fx-background-radius: 10; -fx-padding: 7 14 7 14;"
                        + "-fx-text-fill: #c7d2fe; -fx-font-size: 13px; -fx-font-weight: bold;"
        );

        earnedLabel = UiUtils.mutedLabel("");
        Button refreshBtn = UiUtils.secondaryButton("Sync");
        refreshBtn.setOnAction(e -> economy.reloadFromDisk());

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        HBox topBar = new HBox(12, tabSegment, gap, earnedLabel, coinsLabel, refreshBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("selection-bar");

        feedbackLabel = new Label();
        feedbackLabel.setWrapText(true);
        feedbackLabel.setStyle("-fx-text-fill: #86efac; -fx-font-size: 12px; -fx-font-weight: bold;");
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);

        // Left Column: Live 3D Cosmetic Preview
        previewCanvas = new SkinPreviewCanvas(220, 270);
        StackPane previewFrame = new StackPane(previewCanvas);
        previewFrame.setPadding(new Insets(8));
        previewFrame.setStyle(
                "-fx-background-color: #101116; -fx-background-radius: 14;"
                        + "-fx-border-color: #2b3040; -fx-border-radius: 14;"
        );

        previewInfoLabel = new Label("Showing Equipped Loadout");
        previewInfoLabel.setWrapText(true);
        previewInfoLabel.setStyle("-fx-text-fill: #e5e7eb; -fx-font-size: 12px; -fx-font-weight: bold;");

        auraBadgeLabel = new Label("Aura: None");
        auraBadgeLabel.setWrapText(true);
        auraBadgeLabel.setStyle(
                "-fx-background-color: #15171e; -fx-border-color: #2b3040; -fx-border-radius: 8;"
                        + "-fx-background-radius: 8; -fx-padding: 6 10 6 10;"
                        + "-fx-text-fill: #9ca3af; -fx-font-size: 11px;"
        );
        auraBadgeLabel.setMaxWidth(Double.MAX_VALUE);

        Button flipBtn = UiUtils.secondaryButton("Flip Front / Back");
        flipBtn.setOnAction(e -> previewCanvas.flipView());

        Button resetBtn = UiUtils.secondaryButton("Reset Preview");
        resetBtn.setOnAction(e -> clearPreviews());

        HBox previewBtns = new HBox(8, flipBtn, resetBtn);
        previewBtns.setAlignment(Pos.CENTER);

        VBox leftCard = UiUtils.card(
                UiUtils.sectionTitle("3D Cosmetic Preview"),
                previewFrame,
                previewInfoLabel,
                auraBadgeLabel,
                previewBtns,
                UiUtils.mutedLabel("Drag to rotate • Click Preview on any item")
        );
        leftCard.setPrefWidth(295);
        leftCard.setMinWidth(285);

        rightContentBox = new VBox(14);
        rightContentBox.setPadding(new Insets(2, 4, 8, 2));

        ScrollPane rightScroll = new ScrollPane(rightContentBox);
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

    private void clearPreviews() {
        previewCapeOverride = null;
        previewBackOverride = null;
        previewHatOverride = null;
        previewAuraOverride = null;
        if (previewCanvas != null) {
            previewCanvas.stopEmote();
            previewCanvas.setYaw(22.0);
        }
        refreshDynamicContent();
    }

    public void refreshDynamicContent() {
        if (actions == null || coinsLabel == null) return;
        EconomyService economy = actions.economyService();
        EconomyStore.EconomyData data = economy.snapshot();

        coinsLabel.setText("◈  " + data.coins + " JJN Coins");
        earnedLabel.setText("Lifetime Earned: " + data.totalEarned + " ◈");
        int claimable = economy.countClaimableQuests();
        questsTabBtn.setText(claimable > 0 ? "Quests (" + claimable + ")" : "Quests");

        String msg = economy.getLastMessage();
        if (msg != null && !msg.isBlank()) {
            feedbackLabel.setText(msg);
            feedbackLabel.setVisible(true);
            feedbackLabel.setManaged(true);
        }

        syncPreviewCanvas(data);

        rightContentBox.getChildren().clear();
        switch (currentTab) {
            case SHOP -> buildShopSection(economy, data);
            case QUESTS -> buildQuestsSection(economy, data);
            case WARDROBE -> buildWardrobeSection(economy, data);
        }
    }

    private void buildShopSection(EconomyService economy, EconomyStore.EconomyData data) {
        // Category filter bar
        ToggleGroup catGroup = new ToggleGroup();
        HBox filterBar = new HBox(4);
        filterBar.getStyleClass().add("segmented-group");

        ToggleButton allBtn = new ToggleButton("All");
        allBtn.getStyleClass().add("pill-toggle");
        allBtn.setToggleGroup(catGroup);
        allBtn.setSelected(filterCategory == null);
        allBtn.setOnAction(e -> { filterCategory = null; refreshDynamicContent(); });
        filterBar.getChildren().add(allBtn);

        for (EconomyStore.Category cat : EconomyStore.Category.values()) {
            ToggleButton cb = new ToggleButton(cat.label);
            cb.getStyleClass().add("pill-toggle");
            cb.setToggleGroup(catGroup);
            cb.setSelected(filterCategory == cat);
            cb.setOnAction(e -> { filterCategory = cat; refreshDynamicContent(); });
            filterBar.getChildren().add(cb);
        }

        rightContentBox.getChildren().add(filterBar);

        List<EconomyStore.CatalogItem> items = EconomyStore.CATALOG.stream()
                .filter(it -> filterCategory == null || it.category() == filterCategory)
                .toList();

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(50);
            grid.getColumnConstraints().add(col);
        }

        int idx = 0;
        for (EconomyStore.CatalogItem item : items) {
            boolean owned = economy.isOwned(item.id());
            boolean equipped = economy.isEquipped(item.id());

            Label catTag = new Label(item.category().label.toUpperCase() + "  •  " + item.rarity());
            catTag.setStyle("-fx-text-fill: " + item.accentHex() + "; -fx-font-size: 10px; -fx-font-weight: bold;");

            Label priceTag = new Label(owned
                    ? (equipped ? "EQUIPPED ✓" : "OWNED")
                    : "◈ " + item.price());
            priceTag.setStyle("-fx-text-fill: " + (owned ? "#86efac" : "#c7d2fe") + "; -fx-font-size: 12px; -fx-font-weight: bold;");

            Region rGap = new Region();
            HBox.setHgrow(rGap, Priority.ALWAYS);
            HBox tagRow = new HBox(8, catTag, rGap, priceTag);
            tagRow.setAlignment(Pos.CENTER_LEFT);

            Label name = new Label(item.name());
            name.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 15px; -fx-font-weight: bold;");

            Label desc = UiUtils.mutedLabel(item.description());
            desc.setMinHeight(34);

            Button previewBtn = UiUtils.secondaryButton("Preview");
            previewBtn.setOnAction(e -> applyPreviewItem(item));

            Button actionBtn;
            if (!owned) {
                actionBtn = new Button("Buy  ◈ " + item.price());
                actionBtn.getStyleClass().add(data.coins >= item.price() ? "primary-button" : "secondary-button");
                actionBtn.setPadding(new Insets(8, 12, 8, 12));
                actionBtn.setOnAction(e -> {
                    economy.purchaseItem(item.id());
                    applyPreviewItem(item);
                    refreshDynamicContent();
                });
            } else if (item.category() == EconomyStore.Category.EMOTE) {
                actionBtn = UiUtils.secondaryButton("Open Emotes Wheel");
                actionBtn.setOnAction(e -> actions.openEmotesTab());
            } else {
                actionBtn = new Button(equipped ? "Unequip" : "Equip");
                actionBtn.getStyleClass().add(equipped ? "secondary-button" : "primary-button");
                if (!equipped) actionBtn.setPadding(new Insets(8, 14, 8, 14));
                actionBtn.setOnAction(e -> {
                    economy.toggleEquip(item.id());
                    clearPreviews();
                });
            }

            HBox btnRow = new HBox(8, previewBtn, actionBtn);
            btnRow.setAlignment(Pos.CENTER_LEFT);

            VBox card = UiUtils.card(tagRow, name, desc, btnRow);
            card.setPadding(new Insets(14));
            if (equipped) {
                card.setStyle("-fx-background-color: #202432; -fx-background-radius: 15; -fx-border-color: #c7d2fe; -fx-border-radius: 15;");
            }
            grid.add(card, idx % 2, idx / 2);
            idx++;
        }

        rightContentBox.getChildren().add(grid);
    }

    private void buildQuestsSection(EconomyService economy, EconomyStore.EconomyData data) {
        Label dailyTitle = UiUtils.sectionTitle("Daily Quests");
        Label dailySub = UiUtils.mutedLabel("Resets daily (" + data.dailyDate + ") • Play in any JJN Fabric world to progress automatically!");
        VBox dailyBox = new VBox(10);
        for (EconomyStore.QuestDef q : EconomyStore.QUESTS) {
            if (q.daily()) dailyBox.getChildren().add(buildQuestCard(economy, q));
        }

        Label lifeTitle = UiUtils.sectionTitle("Lifetime Milestones");
        Label lifeSub = UiUtils.mutedLabel("Permanent achievements granting big JJN Coin bonuses.");
        VBox lifeBox = new VBox(10);
        for (EconomyStore.QuestDef q : EconomyStore.QUESTS) {
            if (!q.daily()) lifeBox.getChildren().add(buildQuestCard(economy, q));
        }

        rightContentBox.getChildren().addAll(dailyTitle, dailySub, dailyBox, new Separator(), lifeTitle, lifeSub, lifeBox);
    }

    private VBox buildQuestCard(EconomyService economy, EconomyStore.QuestDef q) {
        int prog = Math.min(q.target(), economy.getQuestProgress(q.id()));
        boolean claimed = economy.isQuestClaimed(q.id());
        boolean ready = economy.isQuestReadyToClaim(q);

        Label title = new Label(q.title());
        title.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label reward = new Label("+◈ " + q.rewardCoins() + " Coins");
        reward.setStyle("-fx-text-fill: #fcd34d; -fx-font-size: 12px; -fx-font-weight: bold;");

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox top = new HBox(8, title, gap, reward);
        top.setAlignment(Pos.CENTER_LEFT);

        Label desc = UiUtils.mutedLabel(q.description());

        ProgressBar bar = new ProgressBar(q.target() > 0 ? (double) prog / q.target() : 0.0);
        bar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(bar, Priority.ALWAYS);

        Label countLbl = new Label(prog + " / " + q.target());
        countLbl.setStyle("-fx-text-fill: #c7d2fe; -fx-font-size: 12px; -fx-font-weight: bold;");

        Button claimBtn;
        if (claimed) {
            claimBtn = UiUtils.secondaryButton("Claimed ✓");
            claimBtn.setDisable(true);
        } else if (ready) {
            claimBtn = new Button("Claim +◈ " + q.rewardCoins());
            claimBtn.getStyleClass().add("primary-button");
            claimBtn.setPadding(new Insets(7, 14, 7, 14));
            claimBtn.setOnAction(e -> {
                economy.claimQuest(q.id());
                refreshDynamicContent();
            });
        } else {
            claimBtn = UiUtils.secondaryButton("In Progress");
            claimBtn.setDisable(true);
        }

        HBox progRow = new HBox(12, bar, countLbl, claimBtn);
        progRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = UiUtils.card(top, desc, progRow);
        card.setPadding(new Insets(14));
        if (ready) {
            card.setStyle("-fx-background-color: #202432; -fx-background-radius: 15; -fx-border-color: #86efac; -fx-border-radius: 15;");
        }
        return card;
    }

    private void buildWardrobeSection(EconomyService economy, EconomyStore.EconomyData data) {
        rightContentBox.getChildren().addAll(
                UiUtils.sectionTitle("Equipped Cosmetics Wardrobe"),
                UiUtils.mutedLabel("Manage your unlocked Capes, 3D Wings, Headgear, and Particle Auras. Changes sync live with Minecraft!"),
                buildWardrobeSlotCard(economy, EconomyStore.Category.CAPE, data.equippedCapeId),
                buildWardrobeSlotCard(economy, EconomyStore.Category.BACK, data.equippedBackId),
                buildWardrobeSlotCard(economy, EconomyStore.Category.HAT, data.equippedHatId),
                buildWardrobeSlotCard(economy, EconomyStore.Category.AURA, data.equippedAuraId)
        );
    }

    private VBox buildWardrobeSlotCard(EconomyService economy, EconomyStore.Category cat, String equippedId) {
        EconomyStore.CatalogItem active = EconomyStore.findItem(equippedId);
        Label slotTitle = new Label(cat.label + ": " + (active != null ? active.name() : "None Equipped"));
        slotTitle.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 14px; -fx-font-weight: bold;");

        Button clearBtn = UiUtils.secondaryButton("Unequip");
        clearBtn.setDisable(active == null);
        clearBtn.setOnAction(e -> {
            economy.unequipCategory(cat);
            clearPreviews();
        });

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox header = new HBox(10, slotTitle, gap, clearBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        FlowPane ownedFlow = new FlowPane(8, 8);
        List<EconomyStore.CatalogItem> ownedInCat = EconomyStore.CATALOG.stream()
                .filter(it -> it.category() == cat && economy.isOwned(it.id()))
                .toList();

        if (ownedInCat.isEmpty()) {
            ownedFlow.getChildren().add(UiUtils.mutedLabel("None unlocked yet — visit the Shop tab!"));
        } else {
            for (EconomyStore.CatalogItem it : ownedInCat) {
                boolean isEq = it.id().equals(equippedId);
                Button btn = isEq ? new Button(it.name() + " ✓") : UiUtils.secondaryButton(it.name());
                if (isEq) {
                    btn.getStyleClass().add("primary-button");
                    btn.setPadding(new Insets(7, 12, 7, 12));
                }
                btn.setOnAction(e -> {
                    economy.toggleEquip(it.id());
                    clearPreviews();
                });
                ownedFlow.getChildren().add(btn);
            }
        }

        return UiUtils.card(header, ownedFlow);
    }

    private void applyPreviewItem(EconomyStore.CatalogItem item) {
        switch (item.category()) {
            case CAPE -> {
                previewCapeOverride = item.id();
                previewCanvas.setYaw(198.0);
            }
            case BACK -> {
                previewBackOverride = item.id();
                previewCanvas.setYaw(198.0);
            }
            case HAT -> {
                previewHatOverride = item.id();
                previewCanvas.setYaw(22.0);
            }
            case AURA -> {
                previewAuraOverride = item.id();
            }
            case EMOTE -> {
                previewCanvas.setYaw(22.0);
                previewCanvas.playEmote(item.id(), true);
            }
        }
        previewInfoLabel.setText("Previewing: " + item.name());
        syncPreviewCanvas(actions.economyService().snapshot());
    }

    private void syncPreviewCanvas(EconomyStore.EconomyData data) {
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

        String capeId = previewCapeOverride != null ? previewCapeOverride : data.equippedCapeId;
        String backId = previewBackOverride != null ? previewBackOverride : data.equippedBackId;
        String hatId = previewHatOverride != null ? previewHatOverride : data.equippedHatId;
        String auraId = previewAuraOverride != null ? previewAuraOverride : data.equippedAuraId;

        if (capeId != null && !capeId.isBlank()) {
            previewCanvas.setCapeImage(EconomyStore.createCapePreviewImage(capeId));
        } else if (profile != null && profile.capeFilePath != null && !profile.capeFilePath.isBlank()) {
            previewCanvas.loadCapeFromPathOrUrl(Path.of(profile.capeFilePath), profile.capeUrl);
        } else {
            previewCanvas.setCapeImage(null);
        }
        previewCanvas.setCosmetics(backId, hatId);

        EconomyStore.CatalogItem auraItem = EconomyStore.findItem(auraId);
        if (auraItem != null) {
            auraBadgeLabel.setText("✦ Active Aura: " + auraItem.name() + " (renders particles in-game)");
            auraBadgeLabel.setStyle(
                    "-fx-background-color: #15171e; -fx-border-color: " + auraItem.accentHex() + "; -fx-border-radius: 8;"
                            + "-fx-background-radius: 8; -fx-padding: 6 10 6 10;"
                            + "-fx-text-fill: " + auraItem.accentHex() + "; -fx-font-size: 11px; -fx-font-weight: bold;"
            );
        } else {
            auraBadgeLabel.setText("Particle Aura: None active");
            auraBadgeLabel.setStyle(
                    "-fx-background-color: #15171e; -fx-border-color: #2b3040; -fx-border-radius: 8;"
                            + "-fx-background-radius: 8; -fx-padding: 6 10 6 10;"
                            + "-fx-text-fill: #9ca3af; -fx-font-size: 11px;"
            );
        }
    }
}
