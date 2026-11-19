package com.jjn.launcher.ui;

import com.jjn.launcher.MicrosoftAuthService;
import com.jjn.launcher.model.ClientProfile;
import com.jjn.launcher.service.ProfileService;
import com.jjn.launcher.shop.EconomyService;
import com.jjn.launcher.shop.EconomyStore;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    private boolean filterDealsOnly = false;
    private boolean adminUnlocked = false;

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
        coinsLabel.getStyleClass().add("coins-pill");

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
        feedbackLabel.getStyleClass().add("status-success");
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);

        // Left Column: Live 3D Cosmetic Preview
        previewCanvas = new SkinPreviewCanvas(220, 270);
        StackPane previewFrame = new StackPane(previewCanvas);
        previewFrame.setPadding(new Insets(8));
        previewFrame.getStyleClass().add("preview-frame");

        previewInfoLabel = new Label("Showing Equipped Loadout");
        previewInfoLabel.setWrapText(true);
        previewInfoLabel.getStyleClass().add("card-title");

        auraBadgeLabel = new Label("Aura: None");
        auraBadgeLabel.setWrapText(true);
        auraBadgeLabel.getStyleClass().add("aura-badge");
        auraBadgeLabel.setMaxWidth(Double.MAX_VALUE);

        Button flipBtn = UiUtils.secondaryButton("Flip View");
        flipBtn.setMinWidth(Region.USE_PREF_SIZE);
        flipBtn.setOnAction(e -> previewCanvas.flipView());

        Button resetBtn = UiUtils.secondaryButton("Reset Preview");
        resetBtn.setMinWidth(Region.USE_PREF_SIZE);
        resetBtn.setOnAction(e -> clearPreviews());

        FlowPane previewBtns = new FlowPane(8, 8, flipBtn, resetBtn);
        previewBtns.setAlignment(Pos.CENTER);

        VBox leftCard = UiUtils.card(
                UiUtils.sectionTitle("3D Cosmetic Preview"),
                previewFrame,
                previewInfoLabel,
                auraBadgeLabel,
                previewBtns,
                UiUtils.mutedLabel("Drag to rotate • Click Preview on any item")
        );
        leftCard.setPrefWidth(305);
        leftCard.setMinWidth(295);

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
            feedbackLabel.getStyleClass().setAll(
                    msg.startsWith("Need ") || msg.startsWith("Unknown ") || msg.startsWith("Enter ")
                            ? "status-warning"
                            : "status-success"
            );
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
        EconomyStore.CreatorCodeDef activeCode = economy.getActiveCreatorCode();

        // Creator Code & Special Deals bar
        Label creatorTitle = new Label("CREATOR CODE");
        creatorTitle.getStyleClass().add("accent-text");

        Label creatorBadge = new Label(activeCode != null
                ? activeCode.code() + "  •  " + activeCode.creatorName().toUpperCase()
                : "NO CODE ACTIVE");
        creatorBadge.getStyleClass().add(activeCode != null ? "badge-success" : "badge-muted");

        Region creatorGap = new Region();
        HBox.setHgrow(creatorGap, Priority.ALWAYS);

        Label creatorHint = UiUtils.mutedLabel(activeCode != null
                ? activeCode.tagline()
                : "Enter a Creator Code (e.g. JJN, BLUE, VIP, WELCOME) to unlock shop discounts & featured deals!");

        TextField codeField = new TextField(activeCode != null ? activeCode.code() : "");
        codeField.getStyleClass().add("input-field");
        codeField.setPromptText("Enter Creator Code...");
        codeField.setPrefWidth(210);
        HBox.setHgrow(codeField, Priority.SOMETIMES);

        Button applyCodeBtn = new Button("Apply Code");
        applyCodeBtn.getStyleClass().add("primary-button");
        applyCodeBtn.setPadding(new Insets(8, 14, 8, 14));
        applyCodeBtn.setMinWidth(Region.USE_PREF_SIZE);
        applyCodeBtn.setOnAction(e -> {
            String entered = codeField.getText();
            if (EconomyStore.isAdminCode(entered)) {
                adminUnlocked = true;
                codeField.clear();
                refreshDynamicContent();
                feedbackLabel.setText("Admin Panel unlocked.");
                feedbackLabel.getStyleClass().setAll("status-success");
                feedbackLabel.setVisible(true);
                feedbackLabel.setManaged(true);
                openAdminPanelDialog(economy);
                return;
            }
            if (economy.applyCreatorCode(entered)) {
                refreshDynamicContent();
            } else {
                String msg = economy.getLastMessage();
                if (msg != null && !msg.isBlank()) {
                    feedbackLabel.setText(msg);
                    feedbackLabel.getStyleClass().setAll("status-warning");
                    feedbackLabel.setVisible(true);
                    feedbackLabel.setManaged(true);
                }
            }
        });
        codeField.setOnAction(e -> applyCodeBtn.fire());

        Button clearCodeBtn = UiUtils.secondaryButton("Clear");
        clearCodeBtn.setMinWidth(Region.USE_PREF_SIZE);
        clearCodeBtn.setVisible(activeCode != null);
        clearCodeBtn.setManaged(activeCode != null);
        clearCodeBtn.setOnAction(e -> {
            filterDealsOnly = false;
            economy.clearCreatorCode();
            refreshDynamicContent();
        });

        Button adminOpenBtn = new Button("★ Admin Panel");
        adminOpenBtn.getStyleClass().add("primary-button");
        adminOpenBtn.setPadding(new Insets(6, 12, 6, 12));
        adminOpenBtn.setMinWidth(Region.USE_PREF_SIZE);
        adminOpenBtn.setVisible(adminUnlocked);
        adminOpenBtn.setManaged(adminUnlocked);
        adminOpenBtn.setOnAction(e -> openAdminPanelDialog(economy));

        Button adminLockBtn = UiUtils.secondaryButton("Lock");
        adminLockBtn.setPadding(new Insets(6, 12, 6, 12));
        adminLockBtn.setMinWidth(Region.USE_PREF_SIZE);
        adminLockBtn.setVisible(adminUnlocked);
        adminLockBtn.setManaged(adminUnlocked);
        adminLockBtn.setOnAction(e -> {
            adminUnlocked = false;
            refreshDynamicContent();
            feedbackLabel.setText("Admin Panel locked.");
            feedbackLabel.getStyleClass().setAll("status-success");
            feedbackLabel.setVisible(true);
            feedbackLabel.setManaged(true);
        });

        HBox creatorTop = new HBox(8, creatorTitle, creatorBadge, creatorGap, adminOpenBtn, adminLockBtn);
        creatorTop.setAlignment(Pos.CENTER_LEFT);

        HBox creatorInputRow = new HBox(8, codeField, applyCodeBtn, clearCodeBtn);
        creatorInputRow.setAlignment(Pos.CENTER_LEFT);

        VBox creatorBox = new VBox(8, creatorTop, creatorHint, creatorInputRow);
        creatorBox.getStyleClass().add(activeCode != null ? "loader-banner" : "selection-bar");
        rightContentBox.getChildren().add(creatorBox);

        if (economy.getLastMessage() != null && economy.getLastMessage().startsWith("Creator Code")) {
            feedbackLabel.getStyleClass().setAll("status-success");
        }

        // Category filter bar
        ToggleGroup catGroup = new ToggleGroup();
        FlowPane filterBar = new FlowPane(6, 6);
        filterBar.getStyleClass().add("segmented-group");

        ToggleButton allBtn = new ToggleButton("All");
        allBtn.getStyleClass().add("pill-toggle");
        allBtn.setMinWidth(Region.USE_PREF_SIZE);
        allBtn.setToggleGroup(catGroup);
        allBtn.setSelected(!filterDealsOnly && filterCategory == null);
        allBtn.setOnAction(e -> { filterDealsOnly = false; filterCategory = null; refreshDynamicContent(); });
        filterBar.getChildren().add(allBtn);

        if (activeCode != null) {
            ToggleButton dealsBtn = new ToggleButton("★ Creator Deals");
            dealsBtn.getStyleClass().add("pill-toggle");
            dealsBtn.setMinWidth(Region.USE_PREF_SIZE);
            dealsBtn.setToggleGroup(catGroup);
            dealsBtn.setSelected(filterDealsOnly);
            dealsBtn.setOnAction(e -> { filterDealsOnly = true; filterCategory = null; refreshDynamicContent(); });
            filterBar.getChildren().add(dealsBtn);
        }

        for (EconomyStore.Category cat : EconomyStore.Category.values()) {
            ToggleButton cb = new ToggleButton(cat.label);
            cb.getStyleClass().add("pill-toggle");
            cb.setMinWidth(Region.USE_PREF_SIZE);
            cb.setToggleGroup(catGroup);
            cb.setSelected(!filterDealsOnly && filterCategory == cat);
            cb.setOnAction(e -> { filterDealsOnly = false; filterCategory = cat; refreshDynamicContent(); });
            filterBar.getChildren().add(cb);
        }

        rightContentBox.getChildren().add(filterBar);

        List<EconomyStore.CatalogItem> items = EconomyStore.CATALOG.stream()
                .filter(it -> !filterDealsOnly || economy.isFeaturedCreatorDeal(it))
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
            int discountPct = economy.getDiscountPercent(item);
            int finalPrice = economy.getEffectivePrice(item);
            boolean featuredDeal = economy.isFeaturedCreatorDeal(item);

            Label catTag = new Label(item.category().label.toUpperCase() + "  •  " + item.rarity());
            catTag.setStyle("-fx-text-fill: " + item.accentHex() + "; -fx-font-size: 10px; -fx-font-weight: bold;");

            String priceText;
            if (owned) {
                priceText = equipped ? "EQUIPPED ✓" : "OWNED";
            } else if (discountPct > 0 && item.price() > 0) {
                priceText = "◈ " + finalPrice + " (was " + item.price() + ")";
            } else {
                priceText = "◈ " + item.price();
            }
            Label priceTag = new Label(priceText);
            priceTag.getStyleClass().add(owned ? "status-success" : (discountPct > 0 ? "status-warning" : "accent-text"));

            Region rGap = new Region();
            HBox.setHgrow(rGap, Priority.ALWAYS);
            HBox tagRow = new HBox(8, catTag, rGap, priceTag);
            tagRow.setAlignment(Pos.CENTER_LEFT);

            Label name = new Label(item.name());
            name.getStyleClass().add("card-title");

            HBox titleRow = new HBox(8, name);
            titleRow.setAlignment(Pos.CENTER_LEFT);
            if (!owned && discountPct > 0 && item.price() > 0) {
                Label dealBadge = new Label(featuredDeal
                        ? "★ CREATOR DEAL -" + discountPct + "%"
                        : "-" + discountPct + "% CODE");
                dealBadge.getStyleClass().add(featuredDeal ? "badge-warning" : "badge-accent");
                titleRow.getChildren().add(dealBadge);
            }

            Label desc = UiUtils.mutedLabel(item.description());
            desc.setMinHeight(34);

            Button previewBtn = UiUtils.secondaryButton("Preview");
            previewBtn.setMinWidth(Region.USE_PREF_SIZE);
            previewBtn.setOnAction(e -> applyPreviewItem(item));

            Button actionBtn;
            if (!owned) {
                actionBtn = new Button("Buy  ◈ " + finalPrice);
                actionBtn.getStyleClass().add(data.coins >= finalPrice ? "primary-button" : "secondary-button");
                actionBtn.setPadding(new Insets(8, 12, 8, 12));
                actionBtn.setOnAction(e -> {
                    economy.purchaseItem(item.id());
                    applyPreviewItem(item);
                    refreshDynamicContent();
                });
            } else if (item.category() == EconomyStore.Category.EMOTE) {
                actionBtn = UiUtils.secondaryButton("Emotes Wheel");
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
            actionBtn.setMinWidth(Region.USE_PREF_SIZE);

            FlowPane btnRow = new FlowPane(8, 8, previewBtn, actionBtn);
            btnRow.setAlignment(Pos.CENTER_LEFT);

            VBox card = UiUtils.card(tagRow, titleRow, desc, btnRow);
            card.setPadding(new Insets(14));
            if (equipped) {
                card.getStyleClass().remove("panel-card");
                card.getStyleClass().add("panel-card-active");
            } else if (!owned && featuredDeal) {
                card.getStyleClass().remove("panel-card");
                card.getStyleClass().add("panel-card-ready");
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
        title.getStyleClass().add("card-title");

        Label reward = new Label("+◈ " + q.rewardCoins() + " Coins");
        reward.getStyleClass().add("status-warning");

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox top = new HBox(8, title, gap, reward);
        top.setAlignment(Pos.CENTER_LEFT);

        Label desc = UiUtils.mutedLabel(q.description());

        ProgressBar bar = new ProgressBar(q.target() > 0 ? (double) prog / q.target() : 0.0);
        bar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(bar, Priority.ALWAYS);

        Label countLbl = new Label(prog + " / " + q.target());
        countLbl.getStyleClass().add("accent-text");

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
            card.getStyleClass().remove("panel-card");
            card.getStyleClass().add("panel-card-ready");
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
        slotTitle.getStyleClass().add("card-title");

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
                    "-fx-border-color: " + auraItem.accentHex() + ";"
                            + "-fx-text-fill: " + auraItem.accentHex() + "; -fx-font-weight: bold;"
            );
        } else {
            auraBadgeLabel.setText("Particle Aura: None active");
            auraBadgeLabel.setStyle("");
        }
    }

    private void openAdminPanelDialog(EconomyService economy) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("JJN Admin Panel");
        dialog.setHeaderText("Owner Controls — Gift Coins, Cosmetics, Quests & Creator Codes");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);
        UiUtils.styleDialog(dialog);

        VBox content = createAdminPanelContent(economy);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(660, 560);
        scroll.getStyleClass().add("scroll-pane");

        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setPrefWidth(700);
        dialog.showAndWait();
    }

    public VBox createAdminPanelContent(EconomyService economy) {
        Label summaryLbl = new Label();
        summaryLbl.getStyleClass().add("card-title");
        summaryLbl.setWrapText(true);

        Label adminStatus = new Label("Admin controls active — changes save immediately to ~/.jjnclient.");
        adminStatus.getStyleClass().add("status-success");
        adminStatus.setWrapText(true);

        ComboBox<EconomyStore.CatalogItem> itemCombo = new ComboBox<>(
                FXCollections.observableArrayList(EconomyStore.CATALOG)
        );
        itemCombo.setMaxWidth(Double.MAX_VALUE);
        itemCombo.setCellFactory(lv -> catalogComboCell(economy));
        itemCombo.setButtonCell(catalogComboCell(economy));
        if (!EconomyStore.CATALOG.isEmpty()) {
            itemCombo.getSelectionModel().selectFirst();
        }

        VBox customCodesListBox = new VBox(8);

        TextField codeInput = new TextField();
        codeInput.getStyleClass().add("input-field");
        codeInput.setPromptText("Code (e.g. PARTNER)");

        TextField creatorNameInput = new TextField();
        creatorNameInput.getStyleClass().add("input-field");
        creatorNameInput.setPromptText("Creator Name (e.g. Alex)");

        TextField discountInput = new TextField("20");
        discountInput.getStyleClass().add("input-field");
        discountInput.setPromptText("Shop % (0-90)");

        TextField dealDiscountInput = new TextField("35");
        dealDiscountInput.getStyleClass().add("input-field");
        dealDiscountInput.setPromptText("Featured Deal % (0-90)");

        TextField bonusCoinsInput = new TextField("100");
        bonusCoinsInput.getStyleClass().add("input-field");
        bonusCoinsInput.setPromptText("One-time Bonus Coins");

        TextField taglineInput = new TextField();
        taglineInput.getStyleClass().add("input-field");
        taglineInput.setPromptText("Tagline shown in Shop");

        TextField featuredIdsInput = new TextField();
        featuredIdsInput.getStyleClass().add("input-field");
        featuredIdsInput.setPromptText("Optional Featured IDs (comma-separated, e.g. cape_jjn_nova, hat_crown)");

        Runnable[] refreshRef = new Runnable[1];
        Runnable syncAll = () -> {
            EconomyStore.EconomyData d = economy.snapshot();
            summaryLbl.setText(
                    "Coins: " + d.coins + " ◈   •   Lifetime Earned: " + d.totalEarned
                            + " ◈   •   Owned: " + d.ownedItems.size() + "/" + EconomyStore.CATALOG.size()
            );
            String msg = economy.getLastMessage();
            if (msg != null && !msg.isBlank()) {
                adminStatus.setText(msg);
                adminStatus.getStyleClass().setAll("status-success");
            }
            int selIdx = Math.max(0, itemCombo.getSelectionModel().getSelectedIndex());
            itemCombo.setItems(FXCollections.observableArrayList(EconomyStore.CATALOG));
            itemCombo.setButtonCell(catalogComboCell(economy));
            if (!EconomyStore.CATALOG.isEmpty()) {
                itemCombo.getSelectionModel().select(Math.min(selIdx, EconomyStore.CATALOG.size() - 1));
            }
            if (refreshRef[0] != null) {
                refreshRef[0].run();
            }
            refreshDynamicContent();
        };

        refreshRef[0] = () -> {
            customCodesListBox.getChildren().clear();
            List<EconomyStore.CreatorCodeDef> customCodes = EconomyStore.loadCustomCreatorCodes();
            if (customCodes.isEmpty()) {
                customCodesListBox.getChildren().add(UiUtils.mutedLabel("No custom Creator Codes saved in creator-codes.json yet."));
                return;
            }
            for (EconomyStore.CreatorCodeDef def : customCodes) {
                if (def == null) continue;
                Label codeTitle = new Label(def.code() + " — " + (def.creatorName() != null ? def.creatorName() : def.code()));
                codeTitle.getStyleClass().add("card-title");

                Label codeMeta = UiUtils.mutedLabel(
                        def.discountPercent() + "% off Shop  •  " + def.dealDiscountPercent()
                                + "% Featured  •  +" + def.bonusCoins() + " ◈ bonus"
                                + (def.tagline() != null && !def.tagline().isBlank() ? "\n" + def.tagline() : "")
                );

                VBox info = new VBox(2, codeTitle, codeMeta);
                HBox.setHgrow(info, Priority.ALWAYS);

                Button editBtn = UiUtils.secondaryButton("Edit");
                editBtn.setMinWidth(Region.USE_PREF_SIZE);
                editBtn.setOnAction(e -> {
                    codeInput.setText(def.code());
                    creatorNameInput.setText(def.creatorName() != null ? def.creatorName() : "");
                    discountInput.setText(String.valueOf(def.discountPercent()));
                    dealDiscountInput.setText(String.valueOf(def.dealDiscountPercent()));
                    bonusCoinsInput.setText(String.valueOf(def.bonusCoins()));
                    taglineInput.setText(def.tagline() != null ? def.tagline() : "");
                    featuredIdsInput.setText(def.featuredItemIds() != null ? String.join(", ", def.featuredItemIds()) : "");
                    adminStatus.setText("Loaded [" + def.code() + "] into editor.");
                    adminStatus.getStyleClass().setAll("status-success");
                });

                Button deleteBtn = UiUtils.dangerButton("Delete");
                deleteBtn.setMinWidth(Region.USE_PREF_SIZE);
                deleteBtn.setOnAction(e -> {
                    economy.adminDeleteCreatorCode(def.code());
                    syncAll.run();
                });

                HBox row = new HBox(10, info, editBtn, deleteBtn);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("selection-bar");
                customCodesListBox.getChildren().add(row);
            }
        };

        // --- 1. Gift / Manage JJN Coins ---
        FlowPane quickCoinsRow = new FlowPane(8, 8);
        for (int delta : new int[]{100, 500, 1000, 5000}) {
            Button b = new Button("+" + String.format("%,d", delta) + " ◈");
            b.getStyleClass().add("primary-button");
            b.setPadding(new Insets(7, 12, 7, 12));
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setOnAction(e -> {
                economy.adminGiftCoins(delta);
                syncAll.run();
            });
            quickCoinsRow.getChildren().add(b);
        }

        TextField customCoinsField = new TextField();
        customCoinsField.getStyleClass().add("input-field");
        customCoinsField.setPromptText("Custom amount (e.g. 2500)");
        customCoinsField.setPrefWidth(210);

        Button giftCustomBtn = new Button("Gift Coins");
        giftCustomBtn.getStyleClass().add("primary-button");
        giftCustomBtn.setPadding(new Insets(8, 14, 8, 14));
        giftCustomBtn.setMinWidth(Region.USE_PREF_SIZE);
        giftCustomBtn.setOnAction(e -> {
            Integer amt = parseStrictInt(customCoinsField.getText());
            if (amt == null) {
                adminStatus.setText("Enter a valid whole number of JJN Coins to gift.");
                adminStatus.getStyleClass().setAll("status-warning");
                return;
            }
            economy.adminGiftCoins(amt);
            syncAll.run();
        });

        Button setExactBtn = UiUtils.secondaryButton("Set Balance");
        setExactBtn.setMinWidth(Region.USE_PREF_SIZE);
        setExactBtn.setOnAction(e -> {
            Integer amt = parseStrictInt(customCoinsField.getText());
            if (amt == null || amt < 0) {
                adminStatus.setText("Enter 0 or higher to set exact coin balance.");
                adminStatus.getStyleClass().setAll("status-warning");
                return;
            }
            economy.adminSetCoins(amt);
            syncAll.run();
        });

        Button resetCoinsBtn = UiUtils.dangerButton("Reset to 60 ◈");
        resetCoinsBtn.setMinWidth(Region.USE_PREF_SIZE);
        resetCoinsBtn.setOnAction(e -> {
            economy.adminSetCoins(60);
            syncAll.run();
        });

        FlowPane customCoinsRow = new FlowPane(8, 8, customCoinsField, giftCustomBtn, setExactBtn, resetCoinsBtn);
        customCoinsRow.setAlignment(Pos.CENTER_LEFT);

        VBox coinsCard = UiUtils.card(
                UiUtils.sectionTitle("Gift & Manage JJN Coins"),
                UiUtils.mutedLabel("Instantly add coins, set an exact balance, or reset to starter coins."),
                quickCoinsRow,
                customCoinsRow
        );

        // --- 2. Cosmetics & Emotes ---
        Button grantItemBtn = new Button("Grant & Equip");
        grantItemBtn.getStyleClass().add("primary-button");
        grantItemBtn.setPadding(new Insets(8, 14, 8, 14));
        grantItemBtn.setMinWidth(Region.USE_PREF_SIZE);
        grantItemBtn.setOnAction(e -> {
            EconomyStore.CatalogItem sel = itemCombo.getValue();
            if (sel != null) {
                economy.adminGrantItem(sel.id());
                syncAll.run();
            }
        });

        Button revokeItemBtn = UiUtils.dangerButton("Revoke Item");
        revokeItemBtn.setMinWidth(Region.USE_PREF_SIZE);
        revokeItemBtn.setOnAction(e -> {
            EconomyStore.CatalogItem sel = itemCombo.getValue();
            if (sel != null) {
                economy.adminRevokeItem(sel.id());
                syncAll.run();
            }
        });

        Button unlockAllBtn = UiUtils.secondaryButton("Unlock All (" + EconomyStore.CATALOG.size() + ")");
        unlockAllBtn.setMinWidth(Region.USE_PREF_SIZE);
        unlockAllBtn.setOnAction(e -> {
            economy.adminUnlockAllItems();
            syncAll.run();
        });

        Button resetItemsBtn = UiUtils.dangerButton("Reset to Starter");
        resetItemsBtn.setMinWidth(Region.USE_PREF_SIZE);
        resetItemsBtn.setOnAction(e -> {
            economy.adminResetToStarter();
            syncAll.run();
        });

        FlowPane itemBtnsRow = new FlowPane(8, 8, grantItemBtn, revokeItemBtn, unlockAllBtn, resetItemsBtn);
        itemBtnsRow.setAlignment(Pos.CENTER_LEFT);

        VBox itemsCard = UiUtils.card(
                UiUtils.sectionTitle("Cosmetics & Emotes"),
                UiUtils.mutedLabel("Grant or revoke individual Capes, Wings, Hats, Auras, and Emotes, or unlock the full catalog."),
                itemCombo,
                itemBtnsRow
        );

        // --- 3. Quests ---
        Button completeQuestsBtn = new Button("Complete All Quests");
        completeQuestsBtn.getStyleClass().add("primary-button");
        completeQuestsBtn.setPadding(new Insets(8, 14, 8, 14));
        completeQuestsBtn.setMinWidth(Region.USE_PREF_SIZE);
        completeQuestsBtn.setOnAction(e -> {
            economy.adminCompleteAllQuests();
            syncAll.run();
        });

        Button resetQuestsBtn = UiUtils.dangerButton("Reset Quests");
        resetQuestsBtn.setMinWidth(Region.USE_PREF_SIZE);
        resetQuestsBtn.setOnAction(e -> {
            economy.adminResetQuests();
            syncAll.run();
        });

        FlowPane questBtnsRow = new FlowPane(8, 8, completeQuestsBtn, resetQuestsBtn);
        questBtnsRow.setAlignment(Pos.CENTER_LEFT);

        VBox questsCard = UiUtils.card(
                UiUtils.sectionTitle("Quests"),
                UiUtils.mutedLabel("Mark all Daily & Lifetime quests ready to claim, or reset progress."),
                questBtnsRow
        );

        // --- 4. Custom Creator Codes ---
        GridPane codeGrid = new GridPane();
        codeGrid.setHgap(10);
        codeGrid.setVgap(8);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(50);
            codeGrid.getColumnConstraints().add(cc);
        }
        codeGrid.add(labeledField("Code", codeInput), 0, 0);
        codeGrid.add(labeledField("Creator Name", creatorNameInput), 1, 0);
        codeGrid.add(labeledField("Shop Discount %", discountInput), 0, 1);
        codeGrid.add(labeledField("Featured Deal %", dealDiscountInput), 1, 1);
        codeGrid.add(labeledField("Bonus Coins", bonusCoinsInput), 0, 2);
        codeGrid.add(labeledField("Tagline", taglineInput), 1, 2);
        VBox featuredWrap = labeledField("Featured Item IDs (comma-separated)", featuredIdsInput);
        codeGrid.add(featuredWrap, 0, 3, 2, 1);

        Button saveCodeBtn = new Button("Save Creator Code");
        saveCodeBtn.getStyleClass().add("primary-button");
        saveCodeBtn.setPadding(new Insets(8, 14, 8, 14));
        saveCodeBtn.setMinWidth(Region.USE_PREF_SIZE);
        saveCodeBtn.setOnAction(e -> {
            String normalized = EconomyStore.normalizeCode(codeInput.getText());
            if (normalized.isEmpty()) {
                adminStatus.setText("Enter a Creator Code name first.");
                adminStatus.getStyleClass().setAll("status-warning");
                return;
            }
            if (EconomyStore.isAdminCode(normalized)) {
                adminStatus.setText("Cannot save the secret admin access code as a Creator Code.");
                adminStatus.getStyleClass().setAll("status-warning");
                return;
            }
            Integer disc = parseIntOrZero(discountInput.getText());
            Integer dealDisc = parseIntOrZero(dealDiscountInput.getText());
            Integer bonus = parseIntOrZero(bonusCoinsInput.getText());
            if (disc == null || dealDisc == null || bonus == null) {
                adminStatus.setText("Discounts and Bonus Coins must be valid whole numbers.");
                adminStatus.getStyleClass().setAll("status-warning");
                return;
            }
            List<String> featured = new ArrayList<>();
            for (String part : featuredIdsInput.getText().split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) featured.add(trimmed);
            }
            EconomyStore.CreatorCodeDef def = new EconomyStore.CreatorCodeDef(
                    normalized,
                    creatorNameInput.getText().trim(),
                    disc,
                    dealDisc,
                    featured,
                    bonus,
                    taglineInput.getText().trim()
            );
            economy.adminSaveCreatorCode(def);
            syncAll.run();
        });

        Button openJsonBtn = UiUtils.secondaryButton("Open creator-codes.json Folder");
        openJsonBtn.setMinWidth(Region.USE_PREF_SIZE);
        openJsonBtn.setOnAction(e -> {
            EconomyStore.loadCustomCreatorCodes();
            UiUtils.openInExplorer(EconomyStore.creatorCodesFile().getParent());
        });

        FlowPane codeActionsRow = new FlowPane(8, 8, saveCodeBtn, openJsonBtn);
        codeActionsRow.setAlignment(Pos.CENTER_LEFT);

        VBox codesCard = UiUtils.card(
                UiUtils.sectionTitle("Creator Codes Manager"),
                UiUtils.mutedLabel("Built-in codes: JJN, BLUE, VIP, WELCOME. Create or delete custom codes saved in creator-codes.json."),
                codeGrid,
                codeActionsRow,
                new Separator(),
                customCodesListBox
        );

        VBox headerBanner = new VBox(6, summaryLbl, adminStatus);
        headerBanner.getStyleClass().add("loader-banner");

        VBox root = new VBox(14, headerBanner, coinsCard, itemsCard, questsCard, codesCard);
        root.setPadding(new Insets(4, 8, 8, 4));

        syncAll.run();
        return root;
    }

    private VBox labeledField(String label, Control control) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("field-label");
        return new VBox(4, lbl, control);
    }

    private ListCell<EconomyStore.CatalogItem> catalogComboCell(EconomyService economy) {
        return new ListCell<>() {
            @Override
            protected void updateItem(EconomyStore.CatalogItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String status = economy.isOwned(item.id()) ? "OWNED" : "◈ " + item.price();
                    setText(item.name() + " (" + item.category().label + ") — " + status);
                    setGraphic(null);
                }
            }
        };
    }

    private static Integer parseStrictInt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer parseIntOrZero(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
