package com.jjn.client.ui;

import com.jjn.client.JjnConfig;
import com.jjn.client.shop.JjnEconomyManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tabbed in-game JJN Client control center for Minecraft 1.21.4:
 * - JJN Modules (toggle & customize each built-in module)
 * - HUD & Theme (position, drag editor, scale, opacity, accent color)
 * - Emotes (configure 8-slot [B] wheel & unlock/trigger emotes)
 * - Shop & Quests (JJN Coins, daily/lifetime quests, 3D cosmetics & capes)
 * - Installed Mods (inspect loaded Fabric mods & open their config tabs/screens)
 */
public class JjnModMenuScreen extends Screen {

    public enum Tab {
        MODULES,
        HUD,
        EMOTES,
        SHOP,
        MODS
    }

    private enum ShopSubTab {
        CATALOG,
        QUESTS,
        WARDROBE
    }

    private enum BuiltinModule {
        FPS("FPS Counter", "Displays real-time frames per second on the HUD."),
        COORDS("Coordinates & Direction", "Shows your XYZ coordinates and cardinal facing direction."),
        SPRINT("Toggle Sprint", "Automatically keeps sprinting while moving forward."),
        PING("Ping & Server", "Displays server address / Singleplayer and connection latency."),
        BIOME("Biome Display", "Shows the biome name of your current location."),
        KEYSTROKES("Keystrokes HUD", "Renders live WASD and Spacebar key presses."),
        WAYPOINTS("Waypoints", "Create named in-world markers with distance and edge arrows (Press U)."),
        TITLE_SCREEN("Custom Front Screen", "Replaces the vanilla title screen with the JJN Client menu.");

        final String title;
        final String summary;

        BuiltinModule(String title, String summary) {
            this.title = title;
            this.summary = summary;
        }
    }

    private final Screen parent;
    private Tab activeTab;
    private BuiltinModule customModule = null;
    private ModBridge.ModEntry detailMod = null;

    // Mods tab state
    private boolean hideInternalMods = true;
    private String modSearchQuery = "";
    private int modScrollOffset = 0;
    private TextFieldWidget modSearchBox;
    private List<ModBridge.ModEntry> filteredMods = List.of();

    // Emotes tab state
    private int selectedWheelSlot = 0;
    private int emoteScrollOffset = 0;
    private List<JjnEconomyManager.CatalogItem> emoteCatalog = List.of();

    // Shop & Quests tab state
    private ShopSubTab shopSubTab = ShopSubTab.CATALOG;
    private JjnEconomyManager.Category shopFilter = null;
    private int shopScrollOffset = 0;
    private List<JjnEconomyManager.CatalogItem> filteredShopItems = List.of();
    private List<JjnEconomyManager.QuestDef> questRows = JjnEconomyManager.QUESTS;

    private String feedbackMessage = "";

    private static final int ROWS_PER_PAGE = 4;
    private static final int COMPACT_ROWS = 3;

    public JjnModMenuScreen(Screen parent) {
        this(parent, Tab.MODULES);
    }

    public JjnModMenuScreen(Screen parent, Tab initialTab) {
        super(Text.literal("JJN Client Mods"));
        this.parent = parent;
        this.activeTab = initialTab == null ? Tab.MODULES : initialTab;
    }

    private int panelW() {
        return Math.min(504, this.width - 20);
    }

    private int panelH() {
        return Math.min(316, this.height - 18);
    }

    private int panelX() {
        return (this.width - panelW()) / 2;
    }

    private int panelY() {
        return (this.height - panelH()) / 2;
    }

    @Override
    protected void init() {
        this.modSearchBox = null;

        JjnEconomyManager.get().reloadFromDisk();

        int px = panelX();
        int py = panelY();
        int pw = panelW();
        int ph = panelH();

        this.addDrawableChild(new JjnButton(
                px + pw - 60, py + 8, 50, 20,
                Text.literal("DONE"),
                btn -> this.close(),
                true
        ));

        int tabGap = 4;
        int tabW = (pw - 24 - tabGap * 4) / 5;
        int tabY = py + 34;

        List<ModBridge.ModEntry> userModsCount = ModBridge.loadInstalledMods(false);
        int claimable = JjnEconomyManager.get().countClaimableQuests();

        addTabButton(px + 12, tabY, tabW, "MODULES", Tab.MODULES);
        addTabButton(px + 12 + (tabW + tabGap), tabY, tabW, "HUD", Tab.HUD);
        addTabButton(px + 12 + (tabW + tabGap) * 2, tabY, tabW, "EMOTES", Tab.EMOTES);
        addTabButton(px + 12 + (tabW + tabGap) * 3, tabY, tabW,
                claimable > 0 ? "SHOP (" + claimable + "!)" : "SHOP", Tab.SHOP);
        addTabButton(px + 12 + (tabW + tabGap) * 4, tabY, tabW,
                "MODS (" + userModsCount.size() + ")", Tab.MODS);

        int bodyX = px + 14;
        int bodyY = tabY + 28;
        int bodyW = pw - 28;
        int bodyBottom = py + ph - 18;

        switch (activeTab) {
            case MODULES -> initModulesTab(bodyX, bodyY, bodyW);
            case HUD -> initHudTab(bodyX, bodyY, bodyW);
            case EMOTES -> initEmotesTab(bodyX, bodyY, bodyW, bodyBottom);
            case SHOP -> initShopTab(bodyX, bodyY, bodyW, bodyBottom);
            case MODS -> initModsTab(bodyX, bodyY, bodyW, bodyBottom);
        }
    }

    private void addTabButton(int x, int y, int w, String label, Tab tab) {
        this.addDrawableChild(new JjnButton(
                x, y, w, 21,
                Text.literal(label),
                btn -> {
                    activeTab = tab;
                    customModule = null;
                    detailMod = null;
                    feedbackMessage = "";
                    this.clearAndInit();
                },
                activeTab == tab
        ));
    }

    private void initModulesTab(int x, int y, int w) {
        JjnConfig cfg = JjnConfig.get();
        if (customModule != null) {
            initModuleDetail(x, y, w, cfg);
            return;
        }

        BuiltinModule[] modules = BuiltinModule.values();
        int rowH = 23;
        int gap = 4;
        int custW = 92;
        int toggleW = w - custW - 8;

        for (int i = 0; i < modules.length; i++) {
            BuiltinModule mod = modules[i];
            int ry = y + i * (rowH + gap);
            boolean enabled = isModuleEnabled(cfg, mod);

            this.addDrawableChild(new JjnButton(
                    x, ry, toggleW, rowH,
                    Text.literal(mod.title + ": " + (enabled ? "ON" : "OFF")),
                    btn -> {
                        toggleModule(cfg, mod);
                        cfg.save();
                        this.clearAndInit();
                    },
                    enabled
            ));

            this.addDrawableChild(new JjnButton(
                    x + toggleW + 8, ry, custW, rowH,
                    Text.literal("Customize >"),
                    btn -> {
                        this.customModule = mod;
                        this.clearAndInit();
                    }
            ));
        }
    }

    private void initModuleDetail(int x, int y, int w, JjnConfig cfg) {
        this.addDrawableChild(new JjnButton(
                x, y, 118, 22,
                Text.literal("< Back to Modules"),
                btn -> {
                    this.customModule = null;
                    this.clearAndInit();
                }
        ));

        int cy = y + 54;
        int rowH = 26;
        int gap = 8;

        boolean enabled = isModuleEnabled(cfg, customModule);
        this.addDrawableChild(new JjnButton(
                x, cy, w, rowH,
                Text.literal("Module Enabled: " + (enabled ? "ON" : "OFF")),
                btn -> {
                    toggleModule(cfg, customModule);
                    cfg.save();
                    this.clearAndInit();
                },
                enabled
        ));
        cy += rowH + gap;

        switch (customModule) {
            case FPS -> {
                this.addDrawableChild(new JjnButton(
                        x, cy, w, rowH,
                        Text.literal("Label Format: " + (cfg.fpsCompact ? "Compact (120 FPS)" : "Standard (FPS: 120)")),
                        btn -> {
                            cfg.fpsCompact = !cfg.fpsCompact;
                            cfg.save();
                            this.clearAndInit();
                        }
                ));
                cy += rowH + gap;
                addHudShortcutButtons(x, cy, w, rowH, cfg);
            }
            case COORDS -> {
                this.addDrawableChild(new JjnButton(
                        x, cy, w, rowH,
                        Text.literal("Show Facing Direction [N/S/E/W]: " + (cfg.showDirection ? "ON" : "OFF")),
                        btn -> {
                            cfg.showDirection = !cfg.showDirection;
                            cfg.save();
                            this.clearAndInit();
                        }
                ));
                cy += rowH + gap;
                addHudShortcutButtons(x, cy, w, rowH, cfg);
            }
            case SPRINT -> {
                this.addDrawableChild(new JjnButton(
                        x, cy, w, rowH,
                        Text.literal("Show 'Sprint: Toggled' on HUD: " + (cfg.showSprintHud ? "ON" : "OFF")),
                        btn -> {
                            cfg.showSprintHud = !cfg.showSprintHud;
                            cfg.save();
                            this.clearAndInit();
                        }
                ));
            }
            case PING, BIOME, KEYSTROKES -> addHudShortcutButtons(x, cy, w, rowH, cfg);
            case WAYPOINTS -> {
                int half = (w - 8) / 2;
                this.addDrawableChild(new JjnButton(
                        x, cy, half, rowH,
                        Text.literal("Distance Labels: " + (cfg.waypointShowDistance ? "ON" : "OFF")),
                        btn -> {
                            cfg.waypointShowDistance = !cfg.waypointShowDistance;
                            cfg.save();
                            this.clearAndInit();
                        }
                ));
                this.addDrawableChild(new JjnButton(
                        x + half + 8, cy, half, rowH,
                        Text.literal("Beacon Beam: " + (cfg.waypointShowBeacon ? "ON" : "OFF")),
                        btn -> {
                            cfg.waypointShowBeacon = !cfg.waypointShowBeacon;
                            cfg.save();
                            this.clearAndInit();
                        }
                ));
                cy += rowH + gap;
                this.addDrawableChild(new JjnButton(
                        x, cy, half, rowH,
                        Text.literal("Edge Arrows: " + (cfg.waypointEdgeIndicators ? "ON" : "OFF")),
                        btn -> {
                            cfg.waypointEdgeIndicators = !cfg.waypointEdgeIndicators;
                            cfg.save();
                            this.clearAndInit();
                        }
                ));
                String distText = cfg.waypointMaxDistance <= 0 ? "Unlimited" : (cfg.waypointMaxDistance + "m");
                this.addDrawableChild(new JjnButton(
                        x + half + 8, cy, half, rowH,
                        Text.literal("Max Distance: " + distText),
                        btn -> {
                            cfg.waypointMaxDistance = nextMaxDistance(cfg.waypointMaxDistance);
                            cfg.save();
                            this.clearAndInit();
                        }
                ));
                cy += rowH + gap;
                this.addDrawableChild(new JjnButton(
                        x, cy, w, rowH,
                        Text.literal("Open Waypoints Manager...  (In-Game Quick Key: U)"),
                        btn -> {
                            if (this.client != null) {
                                this.client.setScreen(new JjnWaypointsScreen(this));
                            }
                        },
                        true
                ));
            }
            case TITLE_SCREEN -> {
                this.addDrawableChild(new JjnButton(
                        x, cy, w, rowH,
                        Text.literal("Animated Blueprint Grid Backdrop: " + (cfg.titleScreenGrid ? "ON" : "OFF")),
                        btn -> {
                            cfg.titleScreenGrid = !cfg.titleScreenGrid;
                            cfg.save();
                            this.clearAndInit();
                        }
                ));
                cy += rowH + gap;
                this.addDrawableChild(new JjnButton(
                        x, cy, w, rowH,
                        Text.literal("Left-Side 3D Player Preview: " + (cfg.showTitlePreview ? "ON" : "OFF")),
                        btn -> {
                            cfg.showTitlePreview = !cfg.showTitlePreview;
                            cfg.save();
                            this.clearAndInit();
                        }
                ));
            }
        }
    }

    private void addHudShortcutButtons(int x, int y, int w, int rowH, JjnConfig cfg) {
        int half = (w - 8) / 2;
        this.addDrawableChild(new JjnButton(
                x, y, half, rowH,
                Text.literal("Position: " + cfg.cornerName()),
                btn -> {
                    cfg.hudCorner = (cfg.hudCorner + 1) % 4;
                    cfg.save();
                    this.clearAndInit();
                }
        ));
        this.addDrawableChild(new JjnButton(
                x + half + 8, y, half, rowH,
                Text.literal("Accent: " + cfg.accentName()),
                btn -> {
                    cfg.hudAccentIndex = (cfg.hudAccentIndex + 1) % JjnConfig.ACCENT_COLORS.length;
                    cfg.save();
                    this.clearAndInit();
                }
        ));
        this.addDrawableChild(new JjnButton(
                x, y + rowH + 8, w, rowH,
                Text.literal("Drag & Move HUD Position on Screen..."),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new JjnHudEditorScreen(this));
                    }
                }
        ));
    }

    private void initHudTab(int x, int y, int w) {
        JjnConfig cfg = JjnConfig.get();
        int half = (w - 8) / 2;
        int rowH = 26;
        int gap = 8;

        this.addDrawableChild(new JjnButton(
                x, y, w, rowH,
                Text.literal("Master In-Game HUD Overlay: " + (cfg.showHud ? "ON" : "OFF")),
                btn -> {
                    cfg.showHud = !cfg.showHud;
                    cfg.save();
                    this.clearAndInit();
                },
                cfg.showHud
        ));

        int r1 = y + rowH + gap;
        this.addDrawableChild(new JjnButton(
                x, r1, half, rowH,
                Text.literal("Position: " + cfg.cornerName()),
                btn -> {
                    cfg.hudCorner = (cfg.hudCorner + 1) % 4;
                    cfg.save();
                    this.clearAndInit();
                }
        ));
        this.addDrawableChild(new JjnButton(
                x + half + 8, r1, half, rowH,
                Text.literal("Accent: " + cfg.accentName()),
                btn -> {
                    cfg.hudAccentIndex = (cfg.hudAccentIndex + 1) % JjnConfig.ACCENT_COLORS.length;
                    cfg.save();
                    this.clearAndInit();
                }
        ));

        int r2 = r1 + rowH + gap;
        this.addDrawableChild(new JjnButton(
                x, r2, half, rowH,
                Text.literal("Background: " + cfg.opacityName()),
                btn -> {
                    cfg.hudOpacity = (cfg.hudOpacity + 1) % 3;
                    cfg.save();
                    this.clearAndInit();
                }
        ));
        this.addDrawableChild(new JjnButton(
                x + half + 8, r2, half, rowH,
                Text.literal("Scale: " + formatScale(cfg.hudScale)),
                btn -> {
                    if (cfg.hudScale < 0.95f) {
                        cfg.hudScale = 1.0f;
                    } else if (cfg.hudScale < 1.15f) {
                        cfg.hudScale = 1.2f;
                    } else {
                        cfg.hudScale = 0.85f;
                    }
                    cfg.save();
                    this.clearAndInit();
                }
        ));

        int r3 = r2 + rowH + gap;
        this.addDrawableChild(new JjnButton(
                x, r3, half, rowH,
                Text.literal("Text Shadow: " + (cfg.hudTextShadow ? "ON" : "OFF")),
                btn -> {
                    cfg.hudTextShadow = !cfg.hudTextShadow;
                    cfg.save();
                    this.clearAndInit();
                }
        ));
        this.addDrawableChild(new JjnButton(
                x + half + 8, r3, half, rowH,
                Text.literal("Reset HUD Defaults"),
                btn -> {
                    cfg.hudCorner = JjnConfig.CORNER_TOP_LEFT;
                    cfg.hudCustomX = 8;
                    cfg.hudCustomY = 8;
                    cfg.hudScale = 1.0f;
                    cfg.hudOpacity = JjnConfig.OPACITY_SOLID;
                    cfg.hudAccentIndex = 0;
                    cfg.hudTextShadow = true;
                    cfg.save();
                    this.clearAndInit();
                }
        ));

        int r4 = r3 + rowH + gap + 4;
        this.addDrawableChild(new JjnButton(
                x, r4, w, 28,
                Text.literal("OPEN INTERACTIVE HUD POSITION EDITOR"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new JjnHudEditorScreen(this));
                    }
                },
                true
        ));
    }

    // --- EMOTES TAB ---

    private void initEmotesTab(int x, int y, int w, int bodyBottom) {
        JjnEconomyManager mgr = JjnEconomyManager.get();
        JjnEconomyManager.EconomyData data = mgr.snapshot();
        JjnConfig cfg = JjnConfig.get();

        int half = (w - 8) / 2;
        this.addDrawableChild(new JjnButton(
                x, y, half, 20,
                Text.literal("Auto 3rd-Person Camera: " + (cfg.emoteAutoThirdPerson ? "ON" : "OFF")),
                btn -> {
                    cfg.emoteAutoThirdPerson = !cfg.emoteAutoThirdPerson;
                    cfg.save();
                    this.clearAndInit();
                }
        ));
        JjnButton openWheelBtn = new JjnButton(
                x + half + 8, y, half, 20,
                Text.literal("Open [B] Emote Wheel Now"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new JjnEmoteWheelScreen(this.parent));
                    }
                },
                true
        );
        openWheelBtn.active = this.client != null && this.client.player != null;
        this.addDrawableChild(openWheelBtn);

        // 8 Wheel slot pills (2 rows of 4)
        int slotGap = 4;
        int slotW = (w - slotGap * 3) / 4;
        int slotY = y + 25;
        for (int i = 0; i < 8; i++) {
            final int slot = i;
            String id = i < data.emoteWheel.size() ? data.emoteWheel.get(i) : "";
            JjnEconomyManager.CatalogItem item = JjnEconomyManager.findItem(id);
            String shortName = item != null ? item.name() : "Empty";
            if (shortName.length() > 11) shortName = shortName.substring(0, 10) + "..";
            int bx = x + (i % 4) * (slotW + slotGap);
            int by = slotY + (i / 4) * 21;
            this.addDrawableChild(new JjnButton(
                    bx, by, slotW, 19,
                    Text.literal("[" + (i + 1) + "] " + shortName),
                    btn -> {
                        selectedWheelSlot = slot;
                        this.clearAndInit();
                    },
                    i == selectedWheelSlot
            ));
        }

        emoteCatalog = JjnEconomyManager.CATALOG.stream()
                .filter(it -> it.category() == JjnEconomyManager.Category.EMOTE)
                .toList();
        int maxOffset = Math.max(0, emoteCatalog.size() - COMPACT_ROWS);
        emoteScrollOffset = Math.max(0, Math.min(maxOffset, emoteScrollOffset));

        int listY = slotY + 46;
        int rowH = 38;
        int rowGap = 5;
        int end = Math.min(emoteCatalog.size(), emoteScrollOffset + COMPACT_ROWS);
        boolean inWorld = this.client != null && this.client.player != null;

        for (int i = emoteScrollOffset; i < end; i++) {
            JjnEconomyManager.CatalogItem emote = emoteCatalog.get(i);
            int slot = i - emoteScrollOffset;
            int ry = listY + slot * (rowH + rowGap);
            boolean owned = mgr.isOwned(emote.id());
            boolean boundHere = selectedWheelSlot < data.emoteWheel.size()
                    && emote.id().equals(data.emoteWheel.get(selectedWheelSlot));

            if (!owned) {
                this.addDrawableChild(new JjnButton(
                        x + w - 106, ry + 8, 98, 22,
                        Text.literal("Buy ◈ " + emote.price()),
                        btn -> {
                            mgr.purchaseItem(emote.id());
                            feedbackMessage = mgr.getLastMessage();
                            this.clearAndInit();
                        },
                        data.coins >= emote.price()
                ));
            } else {
                JjnButton playBtn = new JjnButton(
                        x + w - 156, ry + 8, 52, 22,
                        Text.literal("Play"),
                        btn -> {
                            mgr.playEmote(this.client, emote.id());
                            this.close();
                        },
                        true
                );
                playBtn.active = inWorld;
                this.addDrawableChild(playBtn);

                this.addDrawableChild(new JjnButton(
                        x + w - 100, ry + 8, 92, 22,
                        Text.literal(boundHere ? "Clear [" + (selectedWheelSlot + 1) + "]" : "Bind [" + (selectedWheelSlot + 1) + "]"),
                        btn -> {
                            mgr.setWheelSlot(selectedWheelSlot, boundHere ? "" : emote.id());
                            feedbackMessage = mgr.getLastMessage();
                            this.clearAndInit();
                        }
                ));
            }
        }

        addPagerButtons(x, bodyBottom - 20, w, emoteScrollOffset > 0, emoteScrollOffset < maxOffset,
                () -> { emoteScrollOffset = Math.max(0, emoteScrollOffset - 1); this.clearAndInit(); },
                () -> { emoteScrollOffset = Math.min(maxOffset, emoteScrollOffset + 1); this.clearAndInit(); });
    }

    // --- SHOP & QUESTS TAB ---

    private void initShopTab(int x, int y, int w, int bodyBottom) {
        JjnEconomyManager mgr = JjnEconomyManager.get();
        JjnEconomyManager.EconomyData data = mgr.snapshot();

        int subGap = 6;
        int subW = (w - subGap * 2) / 3;
        int claimable = mgr.countClaimableQuests();
        addShopSubBtn(x, y, subW, "Cosmetics Shop", ShopSubTab.CATALOG);
        addShopSubBtn(x + subW + subGap, y, subW,
                claimable > 0 ? "Quests (" + claimable + " Ready)" : "Quests & Coins", ShopSubTab.QUESTS);
        addShopSubBtn(x + (subW + subGap) * 2, y, subW, "Wardrobe", ShopSubTab.WARDROBE);

        if (shopSubTab == ShopSubTab.CATALOG) {
            JjnEconomyManager.Category[] cats = JjnEconomyManager.Category.values();
            int fGap = 4;
            int count = cats.length + 1;
            int fW = (w - fGap * (count - 1)) / count;
            int fy = y + 25;
            this.addDrawableChild(new JjnButton(
                    x, fy, fW, 19,
                    Text.literal("All"),
                    btn -> { shopFilter = null; shopScrollOffset = 0; this.clearAndInit(); },
                    shopFilter == null
            ));
            for (int c = 0; c < cats.length; c++) {
                JjnEconomyManager.Category cat = cats[c];
                String label = switch (cat) {
                    case CAPE -> "Capes";
                    case BACK -> "Wings";
                    case HAT -> "Hats";
                    case AURA -> "Auras";
                    case EMOTE -> "Emotes";
                };
                this.addDrawableChild(new JjnButton(
                        x + (c + 1) * (fW + fGap), fy, fW, 19,
                        Text.literal(label),
                        btn -> { shopFilter = cat; shopScrollOffset = 0; this.clearAndInit(); },
                        shopFilter == cat
                ));
            }

            filteredShopItems = JjnEconomyManager.CATALOG.stream()
                    .filter(it -> shopFilter == null || it.category() == shopFilter)
                    .toList();
            int maxOffset = Math.max(0, filteredShopItems.size() - COMPACT_ROWS);
            shopScrollOffset = Math.max(0, Math.min(maxOffset, shopScrollOffset));

            int listY = fy + 24;
            int rowH = 38;
            int rowGap = 5;
            int end = Math.min(filteredShopItems.size(), shopScrollOffset + COMPACT_ROWS);

            for (int i = shopScrollOffset; i < end; i++) {
                JjnEconomyManager.CatalogItem item = filteredShopItems.get(i);
                int slot = i - shopScrollOffset;
                int ry = listY + slot * (rowH + rowGap);
                boolean owned = mgr.isOwned(item.id());
                boolean equipped = mgr.isEquipped(item.id());

                if (!owned) {
                    this.addDrawableChild(new JjnButton(
                            x + w - 98, ry + 8, 90, 22,
                            Text.literal("Buy ◈ " + item.price()),
                            btn -> {
                                mgr.purchaseItem(item.id());
                                feedbackMessage = mgr.getLastMessage();
                                this.clearAndInit();
                            },
                            data.coins >= item.price()
                    ));
                } else if (item.category() == JjnEconomyManager.Category.EMOTE) {
                    this.addDrawableChild(new JjnButton(
                            x + w - 98, ry + 8, 90, 22,
                            Text.literal("Emote Tab >"),
                            btn -> { activeTab = Tab.EMOTES; this.clearAndInit(); }
                    ));
                } else {
                    this.addDrawableChild(new JjnButton(
                            x + w - 98, ry + 8, 90, 22,
                            Text.literal(equipped ? "Unequip" : "Equip"),
                            btn -> {
                                mgr.toggleEquip(item.id());
                                feedbackMessage = mgr.getLastMessage();
                                this.clearAndInit();
                            },
                            !equipped
                    ));
                }
            }

            addPagerButtons(x, bodyBottom - 20, w, shopScrollOffset > 0, shopScrollOffset < maxOffset,
                    () -> { shopScrollOffset = Math.max(0, shopScrollOffset - 1); this.clearAndInit(); },
                    () -> { shopScrollOffset = Math.min(maxOffset, shopScrollOffset + 1); this.clearAndInit(); });

        } else if (shopSubTab == ShopSubTab.QUESTS) {
            questRows = JjnEconomyManager.QUESTS;
            int maxOffset = Math.max(0, questRows.size() - ROWS_PER_PAGE);
            shopScrollOffset = Math.max(0, Math.min(maxOffset, shopScrollOffset));

            int listY = y + 26;
            int rowH = 39;
            int rowGap = 5;
            int end = Math.min(questRows.size(), shopScrollOffset + ROWS_PER_PAGE);

            for (int i = shopScrollOffset; i < end; i++) {
                JjnEconomyManager.QuestDef q = questRows.get(i);
                int slot = i - shopScrollOffset;
                int ry = listY + slot * (rowH + rowGap);
                boolean claimed = mgr.isQuestClaimed(q.id());
                boolean ready = mgr.isQuestReadyToClaim(q);

                JjnButton claimBtn = new JjnButton(
                        x + w - 102, ry + 8, 94, 22,
                        Text.literal(claimed ? "Claimed" : (ready ? "Claim +◈ " + q.rewardCoins() : "In Progress")),
                        btn -> {
                            mgr.claimQuest(q.id());
                            feedbackMessage = mgr.getLastMessage();
                            this.clearAndInit();
                        },
                        ready
                );
                claimBtn.active = ready;
                this.addDrawableChild(claimBtn);
            }

            addPagerButtons(x, bodyBottom - 20, w, shopScrollOffset > 0, shopScrollOffset < maxOffset,
                    () -> { shopScrollOffset = Math.max(0, shopScrollOffset - 1); this.clearAndInit(); },
                    () -> { shopScrollOffset = Math.min(maxOffset, shopScrollOffset + 1); this.clearAndInit(); });

        } else {
            JjnEconomyManager.Category[] cats = {
                    JjnEconomyManager.Category.CAPE,
                    JjnEconomyManager.Category.BACK,
                    JjnEconomyManager.Category.HAT,
                    JjnEconomyManager.Category.AURA
            };
            int rowH = 38;
            int rowGap = 6;
            int listY = y + 28;
            for (int i = 0; i < cats.length; i++) {
                final JjnEconomyManager.Category cat = cats[i];
                int ry = listY + i * (rowH + rowGap);
                String eqId = switch (cat) {
                    case CAPE -> data.equippedCapeId;
                    case BACK -> data.equippedBackId;
                    case HAT -> data.equippedHatId;
                    case AURA -> data.equippedAuraId;
                    default -> "";
                };
                JjnEconomyManager.CatalogItem eq = JjnEconomyManager.findItem(eqId);
                JjnButton clearBtn = new JjnButton(
                        x + w - 94, ry + 8, 86, 22,
                        Text.literal("Unequip"),
                        btn -> {
                            mgr.unequipCategory(cat);
                            this.clearAndInit();
                        }
                );
                clearBtn.active = eq != null;
                this.addDrawableChild(clearBtn);
            }
        }
    }

    private void addShopSubBtn(int x, int y, int w, String label, ShopSubTab sub) {
        this.addDrawableChild(new JjnButton(
                x, y, w, 20,
                Text.literal(label),
                btn -> {
                    shopSubTab = sub;
                    shopScrollOffset = 0;
                    feedbackMessage = "";
                    this.clearAndInit();
                },
                shopSubTab == sub
        ));
    }

    private void addPagerButtons(int x, int pagerY, int w, boolean canPrev, boolean canNext, Runnable onPrev, Runnable onNext) {
        JjnButton prevBtn = new JjnButton(x, pagerY, 76, 20, Text.literal("< Up"), btn -> onPrev.run());
        prevBtn.active = canPrev;
        this.addDrawableChild(prevBtn);

        JjnButton nextBtn = new JjnButton(x + w - 76, pagerY, 76, 20, Text.literal("Down >"), btn -> onNext.run());
        nextBtn.active = canNext;
        this.addDrawableChild(nextBtn);
    }

    private void initModsTab(int x, int y, int w, int bodyBottom) {
        if (detailMod != null) {
            initModDetail(x, y, w);
            return;
        }

        int folderBtnW = 86;
        int libsBtnW = 94;
        int searchW = w - folderBtnW - libsBtnW - 12;

        modSearchBox = new TextFieldWidget(this.textRenderer, x, y, searchW, 20, Text.literal("Search mods"));
        modSearchBox.setMaxLength(64);
        modSearchBox.setPlaceholder(Text.literal("Search installed mods..."));
        modSearchBox.setText(modSearchQuery);
        modSearchBox.setChangedListener(val -> {
            if (!val.equals(modSearchQuery)) {
                modSearchQuery = val;
                modScrollOffset = 0;
                this.clearAndInit();
                if (this.modSearchBox != null) {
                    this.setFocused(this.modSearchBox);
                    this.modSearchBox.setFocused(true);
                }
            }
        });
        this.addDrawableChild(modSearchBox);

        this.addDrawableChild(new JjnButton(
                x + searchW + 6, y, libsBtnW, 20,
                Text.literal(hideInternalMods ? "User Mods" : "All + Libs"),
                btn -> {
                    hideInternalMods = !hideInternalMods;
                    modScrollOffset = 0;
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new JjnButton(
                x + searchW + 6 + libsBtnW + 6, y, folderBtnW, 20,
                Text.literal("Mods Folder"),
                btn -> ModBridge.openModsFolder()
        ));

        refreshFilteredMods();

        int maxOffset = Math.max(0, filteredMods.size() - ROWS_PER_PAGE);
        modScrollOffset = Math.max(0, Math.min(maxOffset, modScrollOffset));

        int listY = y + 26;
        int rowH = 42;
        int rowGap = 5;

        int end = Math.min(filteredMods.size(), modScrollOffset + ROWS_PER_PAGE);
        for (int i = modScrollOffset; i < end; i++) {
            ModBridge.ModEntry entry = filteredMods.get(i);
            int slot = i - modScrollOffset;
            int ry = listY + slot * (rowH + rowGap);

            int infoW = 54;
            int tabW = 78;
            int rightX = x + w;

            JjnButton openTabBtn = new JjnButton(
                    rightX - infoW - tabW - 6, ry + 10, tabW, 22,
                    Text.literal(entry.hasConfigTab() ? "Open Tab" : "No GUI"),
                    btn -> {
                        if (!ModBridge.openConfigTab(this.client, entry.id(), this)) {
                            feedbackMessage = entry.name() + " has no standalone GUI. View Details or install 'Mod Menu'.";
                        }
                    },
                    entry.hasConfigTab()
            );
            openTabBtn.active = entry.hasConfigTab();
            this.addDrawableChild(openTabBtn);

            this.addDrawableChild(new JjnButton(
                    rightX - infoW, ry + 10, infoW, 22,
                    Text.literal("Details"),
                    btn -> {
                        this.detailMod = entry;
                        this.feedbackMessage = "";
                        this.clearAndInit();
                    }
            ));
        }

        addPagerButtons(x, bodyBottom - 20, w, modScrollOffset > 0, modScrollOffset < maxOffset,
                () -> { modScrollOffset = Math.max(0, modScrollOffset - 1); this.clearAndInit(); },
                () -> { modScrollOffset = Math.min(maxOffset, modScrollOffset + 1); this.clearAndInit(); });
    }

    private void initModDetail(int x, int y, int w) {
        this.addDrawableChild(new JjnButton(
                x, y, 128, 22,
                Text.literal("< Back to Mods"),
                btn -> {
                    this.detailMod = null;
                    this.feedbackMessage = "";
                    this.clearAndInit();
                }
        ));

        int btnY = y + 172;
        int btnW = (w - 12) / 3;

        JjnButton configBtn = new JjnButton(
                x, btnY, btnW, 24,
                Text.literal(detailMod.hasConfigTab() ? "Open Mod Tab" : "No Config Screen"),
                btn -> {
                    if (!ModBridge.openConfigTab(this.client, detailMod.id(), this)) {
                        feedbackMessage = "Install 'Mod Menu' in the Launcher Mods tab to open ModMenuApi screens.";
                    }
                },
                detailMod.hasConfigTab()
        );
        configBtn.active = detailMod.hasConfigTab();
        this.addDrawableChild(configBtn);

        JjnButton siteBtn = new JjnButton(
                x + btnW + 6, btnY, btnW, 24,
                Text.literal("Homepage"),
                btn -> ModBridge.openUrl(detailMod.homepageUrl())
        );
        siteBtn.active = detailMod.homepageUrl() != null && !detailMod.homepageUrl().isBlank();
        this.addDrawableChild(siteBtn);

        this.addDrawableChild(new JjnButton(
                x + (btnW + 6) * 2, btnY, btnW, 24,
                Text.literal("Mods Folder"),
                btn -> ModBridge.openModsFolder()
        ));
    }

    private void refreshFilteredMods() {
        List<ModBridge.ModEntry> all = ModBridge.loadInstalledMods(!hideInternalMods);
        String q = modSearchQuery == null ? "" : modSearchQuery.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            this.filteredMods = all;
            return;
        }
        List<ModBridge.ModEntry> matches = new ArrayList<>();
        for (ModBridge.ModEntry m : all) {
            if (m.name().toLowerCase(Locale.ROOT).contains(q)
                    || m.id().toLowerCase(Locale.ROOT).contains(q)
                    || m.description().toLowerCase(Locale.ROOT).contains(q)) {
                matches.add(m);
            }
        }
        this.filteredMods = matches;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int delta = verticalAmount > 0 ? -1 : (verticalAmount < 0 ? 1 : 0);
        if (delta != 0) {
            if (activeTab == Tab.MODS && detailMod == null && !filteredMods.isEmpty()) {
                int max = Math.max(0, filteredMods.size() - ROWS_PER_PAGE);
                int next = Math.max(0, Math.min(max, modScrollOffset + delta));
                if (next != modScrollOffset) {
                    modScrollOffset = next;
                    this.clearAndInit();
                    return true;
                }
            } else if (activeTab == Tab.EMOTES && !emoteCatalog.isEmpty()) {
                int max = Math.max(0, emoteCatalog.size() - COMPACT_ROWS);
                int next = Math.max(0, Math.min(max, emoteScrollOffset + delta));
                if (next != emoteScrollOffset) {
                    emoteScrollOffset = next;
                    this.clearAndInit();
                    return true;
                }
            } else if (activeTab == Tab.SHOP) {
                int total = shopSubTab == ShopSubTab.CATALOG ? filteredShopItems.size()
                        : (shopSubTab == ShopSubTab.QUESTS ? questRows.size() : 0);
                int perPage = shopSubTab == ShopSubTab.CATALOG ? COMPACT_ROWS : ROWS_PER_PAGE;
                if (total > 0) {
                    int max = Math.max(0, total - perPage);
                    int next = Math.max(0, Math.min(max, shopScrollOffset + delta));
                    if (next != shopScrollOffset) {
                        shopScrollOffset = next;
                        this.clearAndInit();
                        return true;
                    }
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        if (this.client != null) {
            if (!JjnConfig.get().customTitleScreen && this.parent instanceof JjnTitleScreen) {
                this.client.setScreen(new TitleScreen());
            } else {
                this.client.setScreen(this.parent);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, 0xCC101116, 0xDD15171E);

        int px = panelX();
        int py = panelY();
        int pw = panelW();
        int ph = panelH();

        context.fill(px, py, px + pw, py + ph, 0xF0191B23);
        context.fill(px, py, px + pw, py + 2, 0xFFC7D2FE);
        context.drawBorder(px, py, pw, ph, 0xFF353949);

        context.drawText(this.textRenderer, "JJN CLIENT", px + 14, py + 12, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, "In-Game Control Center", px + 84, py + 12, 0xFFC7D2FE, true);

        String coinsText = "◈ " + JjnEconomyManager.get().getCoins() + " Coins";
        int coinsW = this.textRenderer.getWidth(coinsText) + 12;
        int coinsX = px + pw - 66 - coinsW;
        context.fill(coinsX, py + 9, coinsX + coinsW, py + 27, 0xFF202432);
        context.drawBorder(coinsX, py + 9, coinsW, 18, 0xFFC7D2FE);
        context.drawText(this.textRenderer, coinsText, coinsX + 6, py + 14, 0xFFFCD34D, true);

        int bodyX = px + 14;
        int bodyY = py + 62;
        int bodyW = pw - 28;
        int bodyBottom = py + ph - 18;

        if (activeTab == Tab.MODULES && customModule != null) {
            context.drawText(this.textRenderer, customModule.title, bodyX, bodyY + 30, 0xFFFFFFFF, true);
            context.drawText(this.textRenderer, customModule.summary, bodyX, bodyY + 41, 0xFF9CA3AF, false);
        } else if (activeTab == Tab.EMOTES) {
            renderEmotesRows(context, bodyX, bodyY + 71, bodyW, bodyBottom);
        } else if (activeTab == Tab.SHOP) {
            renderShopOrQuestRows(context, bodyX, bodyY, bodyW, bodyBottom);
        } else if (activeTab == Tab.MODS) {
            if (detailMod != null) {
                renderModDetailText(context, bodyX, bodyY, bodyW);
            } else {
                renderModRowsBackground(context, bodyX, bodyY + 26, bodyW, bodyBottom);
            }
        }

        if (feedbackMessage != null && !feedbackMessage.isBlank()) {
            context.drawCenteredTextWithShadow(this.textRenderer, feedbackMessage, this.width / 2, py + ph - 14, 0xFF86EFAC);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderEmotesRows(DrawContext context, int x, int listY, int w, int bodyBottom) {
        JjnEconomyManager mgr = JjnEconomyManager.get();
        int rowH = 38;
        int rowGap = 5;
        int end = Math.min(emoteCatalog.size(), emoteScrollOffset + COMPACT_ROWS);

        for (int i = emoteScrollOffset; i < end; i++) {
            JjnEconomyManager.CatalogItem emote = emoteCatalog.get(i);
            int slot = i - emoteScrollOffset;
            int ry = listY + slot * (rowH + rowGap);
            boolean owned = mgr.isOwned(emote.id());

            context.fill(x, ry, x + w, ry + rowH, 0xDD14161D);
            context.drawBorder(x, ry, w, rowH, owned ? 0xFF474E6B : 0xFF2B2F3D);
            context.fill(x, ry, x + 3, ry + rowH, emote.accentArgb());

            String title = emote.name() + "  [" + emote.rarity() + "]" + (owned ? "" : "  •  ◈ " + emote.price());
            context.drawText(this.textRenderer, trimToWidth(title, w - 168), x + 10, ry + 7, 0xFFF8FAFC, true);
            context.drawText(this.textRenderer, trimToWidth(emote.description(), w - 168), x + 10, ry + 20, 0xFF9CA3AF, false);
        }

        String count = String.format("Emotes %d-%d of %d  (Editing Slot [%d])",
                emoteScrollOffset + 1, end, emoteCatalog.size(), selectedWheelSlot + 1);
        context.drawCenteredTextWithShadow(this.textRenderer, count, x + w / 2, bodyBottom - 14, 0xFF85899A);
    }

    private void renderShopOrQuestRows(DrawContext context, int x, int bodyY, int w, int bodyBottom) {
        JjnEconomyManager mgr = JjnEconomyManager.get();
        JjnEconomyManager.EconomyData data = mgr.snapshot();

        if (shopSubTab == ShopSubTab.CATALOG) {
            int listY = bodyY + 49;
            int rowH = 38;
            int rowGap = 5;
            int end = Math.min(filteredShopItems.size(), shopScrollOffset + COMPACT_ROWS);

            for (int i = shopScrollOffset; i < end; i++) {
                JjnEconomyManager.CatalogItem item = filteredShopItems.get(i);
                int slot = i - shopScrollOffset;
                int ry = listY + slot * (rowH + rowGap);
                boolean owned = mgr.isOwned(item.id());
                boolean equipped = mgr.isEquipped(item.id());

                context.fill(x, ry, x + w, ry + rowH, 0xDD14161D);
                context.drawBorder(x, ry, w, rowH, equipped ? 0xFFC7D2FE : 0xFF2B2F3D);
                context.fill(x, ry, x + 3, ry + rowH, item.accentArgb());

                String badge = equipped ? "EQUIPPED" : (owned ? "OWNED" : ("◈ " + item.price()));
                String title = item.name() + "  (" + item.category().label + ")  •  " + badge;
                context.drawText(this.textRenderer, trimToWidth(title, w - 110), x + 10, ry + 7, 0xFFF8FAFC, true);
                context.drawText(this.textRenderer, trimToWidth(item.description(), w - 110), x + 10, ry + 20, 0xFF9CA3AF, false);
            }

            String count = filteredShopItems.isEmpty() ? "0 items"
                    : String.format("Showing %d-%d of %d items", shopScrollOffset + 1, end, filteredShopItems.size());
            context.drawCenteredTextWithShadow(this.textRenderer, count, x + w / 2, bodyBottom - 14, 0xFF85899A);

        } else if (shopSubTab == ShopSubTab.QUESTS) {
            int listY = bodyY + 26;
            int rowH = 39;
            int rowGap = 5;
            int end = Math.min(questRows.size(), shopScrollOffset + ROWS_PER_PAGE);

            for (int i = shopScrollOffset; i < end; i++) {
                JjnEconomyManager.QuestDef q = questRows.get(i);
                int slot = i - shopScrollOffset;
                int ry = listY + slot * (rowH + rowGap);
                int prog = Math.min(q.target(), mgr.getQuestProgress(q.id()));
                boolean ready = mgr.isQuestReadyToClaim(q);
                boolean claimed = mgr.isQuestClaimed(q.id());

                context.fill(x, ry, x + w, ry + rowH, 0xDD14161D);
                context.drawBorder(x, ry, w, rowH, ready ? 0xFF86EFAC : 0xFF2B2F3D);
                context.fill(x, ry, x + 3, ry + rowH, q.daily() ? 0xFF67E8F9 : 0xFFFCD34D);

                String prefix = q.daily() ? "[DAILY] " : "[MILESTONE] ";
                String header = prefix + q.title() + "  •  +◈ " + q.rewardCoins();
                context.drawText(this.textRenderer, trimToWidth(header, w - 116), x + 10, ry + 6, 0xFFF8FAFC, true);
                context.drawText(this.textRenderer, trimToWidth(q.description(), w - 180), x + 10, ry + 18, 0xFF9CA3AF, false);

                int barX = x + 10;
                int barY = ry + 30;
                int barW = w - 178;
                context.fill(barX, barY, barX + barW, barY + 4, 0xFF101116);
                int fillW = q.target() > 0 ? Math.round((barW * (float) prog) / q.target()) : barW;
                context.fill(barX, barY, barX + fillW, barY + 4, claimed ? 0xFF4B5563 : (ready ? 0xFF86EFAC : 0xFFC7D2FE));
                context.drawText(this.textRenderer, prog + "/" + q.target(), barX + barW + 6, ry + 27, 0xFFC7D2FE, false);
            }

            String count = String.format("Quests %d-%d of %d  (Lifetime Earned: ◈ %d)",
                    shopScrollOffset + 1, end, questRows.size(), data.totalEarned);
            context.drawCenteredTextWithShadow(this.textRenderer, count, x + w / 2, bodyBottom - 14, 0xFF85899A);

        } else {
            JjnEconomyManager.Category[] cats = {
                    JjnEconomyManager.Category.CAPE,
                    JjnEconomyManager.Category.BACK,
                    JjnEconomyManager.Category.HAT,
                    JjnEconomyManager.Category.AURA
            };
            int listY = bodyY + 28;
            int rowH = 38;
            int rowGap = 6;
            for (int i = 0; i < cats.length; i++) {
                JjnEconomyManager.Category cat = cats[i];
                int ry = listY + i * (rowH + rowGap);
                String eqId = switch (cat) {
                    case CAPE -> data.equippedCapeId;
                    case BACK -> data.equippedBackId;
                    case HAT -> data.equippedHatId;
                    case AURA -> data.equippedAuraId;
                    default -> "";
                };
                JjnEconomyManager.CatalogItem eq = JjnEconomyManager.findItem(eqId);

                context.fill(x, ry, x + w, ry + rowH, 0xDD14161D);
                context.drawBorder(x, ry, w, rowH, eq != null ? 0xFFC7D2FE : 0xFF2B2F3D);
                if (eq != null) {
                    context.fill(x, ry, x + 3, ry + rowH, eq.accentArgb());
                }
                context.drawText(this.textRenderer, cat.label, x + 10, ry + 7, 0xFFC7D2FE, true);
                context.drawText(this.textRenderer, eq != null ? eq.name() + "  (" + eq.rarity() + ")" : "None Equipped — Visit Cosmetics Shop",
                        x + 10, ry + 21, eq != null ? 0xFFFFFFFF : 0xFF9CA3AF, false);
            }
        }
    }

    private void renderModRowsBackground(DrawContext context, int x, int listY, int w, int bodyBottom) {
        int rowH = 42;
        int rowGap = 5;

        if (filteredMods.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    "No mods match \"" + modSearchQuery + "\"",
                    x + w / 2,
                    listY + 46,
                    0xFF9CA3AF
            );
        } else {
            int end = Math.min(filteredMods.size(), modScrollOffset + ROWS_PER_PAGE);
            for (int i = modScrollOffset; i < end; i++) {
                ModBridge.ModEntry entry = filteredMods.get(i);
                int slot = i - modScrollOffset;
                int ry = listY + slot * (rowH + rowGap);

                context.fill(x, ry, x + w, ry + rowH, 0xDD14161D);
                context.drawBorder(x, ry, w, rowH, entry.hasConfigTab() ? 0xFF474E6B : 0xFF2B2F3D);
                if (entry.id().equals("jjnclient") || entry.hasConfigTab()) {
                    context.fill(x, ry, x + 2, ry + rowH, 0xFFC7D2FE);
                }

                String header = entry.name() + "  v" + entry.version();
                int maxTitleW = w - 156;
                String trimmedHeader = trimToWidth(header, maxTitleW);
                context.drawText(this.textRenderer, trimmedHeader, x + 10, ry + 7, 0xFFF8FAFC, true);

                String desc = entry.description().isBlank() ? ("Mod ID: " + entry.id()) : entry.description();
                String trimmedDesc = trimToWidth(desc, maxTitleW);
                context.drawText(this.textRenderer, trimmedDesc, x + 10, ry + 21, 0xFF9CA3AF, false);
            }
        }

        int pagerY = bodyBottom - 14;
        String countText = filteredMods.isEmpty()
                ? "0 mods"
                : String.format("Showing %d-%d of %d mod(s)",
                modScrollOffset + 1,
                Math.min(filteredMods.size(), modScrollOffset + ROWS_PER_PAGE),
                filteredMods.size());
        context.drawCenteredTextWithShadow(this.textRenderer, countText, x + w / 2, pagerY, 0xFF85899A);
    }

    private void renderModDetailText(DrawContext context, int x, int y, int w) {
        context.fill(x, y + 28, x + w, y + 164, 0xDD14161D);
        context.drawBorder(x, y + 28, w, 136, 0xFF353949);

        context.drawText(this.textRenderer, detailMod.name() + "  v" + detailMod.version(), x + 10, y + 38, 0xFFFFFFFF, true);
        context.drawText(
                this.textRenderer,
                "ID: " + detailMod.id() + "   •   Authors: " + trimToWidth(detailMod.authors(), w - 160),
                x + 10, y + 52, 0xFFC7D2FE, false
        );

        String desc = detailMod.description().isBlank() ? "No description provided by this mod." : detailMod.description();
        List<OrderedText> wrapped = this.textRenderer.wrapLines(Text.literal(desc), w - 20);
        int dy = y + 70;
        int maxLines = 4;
        for (int i = 0; i < Math.min(maxLines, wrapped.size()); i++) {
            context.drawText(this.textRenderer, wrapped.get(i), x + 10, dy, 0xFFD1D5DB, false);
            dy += 12;
        }

        String deps = detailMod.dependencies().isEmpty()
                ? "Dependencies: None"
                : "Depends on: " + String.join(", ", detailMod.dependencies());
        context.drawText(this.textRenderer, trimToWidth(deps, w - 20), x + 10, y + 132, 0xFF85899A, false);

        if (!detailMod.hasConfigTab()) {
            context.drawText(
                    this.textRenderer,
                    "Tip: Install 'Mod Menu' from Launcher Mods tab if this mod exposes a ModMenu config GUI.",
                    x + 10, y + 146, 0xFF9CA3AF, false
            );
        }
    }

    private String trimToWidth(String text, int maxWidth) {
        if (text == null) return "";
        if (this.textRenderer.getWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int limit = Math.max(12, maxWidth - this.textRenderer.getWidth(ellipsis));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (this.textRenderer.getWidth(sb.toString() + c) > limit) break;
            sb.append(c);
        }
        return sb + ellipsis;
    }

    private static boolean isModuleEnabled(JjnConfig cfg, BuiltinModule mod) {
        return switch (mod) {
            case FPS -> cfg.showFps;
            case COORDS -> cfg.showCoords;
            case SPRINT -> cfg.toggleSprint;
            case PING -> cfg.showPingServer;
            case BIOME -> cfg.showBiome;
            case KEYSTROKES -> cfg.showKeystrokes;
            case WAYPOINTS -> cfg.showWaypoints;
            case TITLE_SCREEN -> cfg.customTitleScreen;
        };
    }

    private static void toggleModule(JjnConfig cfg, BuiltinModule mod) {
        switch (mod) {
            case FPS -> cfg.showFps = !cfg.showFps;
            case COORDS -> cfg.showCoords = !cfg.showCoords;
            case SPRINT -> cfg.toggleSprint = !cfg.toggleSprint;
            case PING -> cfg.showPingServer = !cfg.showPingServer;
            case BIOME -> cfg.showBiome = !cfg.showBiome;
            case KEYSTROKES -> cfg.showKeystrokes = !cfg.showKeystrokes;
            case WAYPOINTS -> cfg.showWaypoints = !cfg.showWaypoints;
            case TITLE_SCREEN -> cfg.customTitleScreen = !cfg.customTitleScreen;
        }
    }

    private static int nextMaxDistance(int current) {
        return switch (current) {
            case 0 -> 250;
            case 250 -> 500;
            case 500 -> 1000;
            case 1000 -> 2500;
            default -> 0;
        };
    }

    private static String formatScale(float s) {
        if (s < 0.95f) return "Small (0.85x)";
        if (s > 1.15f) return "Large (1.2x)";
        return "Normal (1.0x)";
    }
}
