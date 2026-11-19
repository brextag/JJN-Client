package com.jjn.launcher.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jjn.launcher.LauncherSettings;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;

/**
 * Shared persistent currency, quests, cosmetics, and emotes store at ~/.jjnclient/economy.json.
 * Read and written identically by JJN Launcher and the 26.2 / 1.21.4 Fabric client mods.
 */
public final class EconomyStore {

    public enum Category {
        CAPE("Capes"),
        BACK("3D Wings & Back"),
        HAT("3D Headgear"),
        AURA("Particle Auras"),
        EMOTE("Emotes");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    public record CatalogItem(
            String id,
            String name,
            Category category,
            int price,
            String rarity,
            String accentHex,
            String description
    ) {}

    public record QuestDef(
            String id,
            String title,
            String description,
            boolean daily,
            int target,
            int rewardCoins
    ) {}

    public static final class EconomyData {
        public int coins = 60;
        public int totalEarned = 60;
        public String dailyDate = "";
        public Set<String> ownedItems = new LinkedHashSet<>(List.of("emote_wave"));
        public String equippedCapeId = "";
        public String equippedBackId = "";
        public String equippedHatId = "";
        public String equippedAuraId = "";
        public List<String> emoteWheel = new ArrayList<>(List.of(
                "emote_wave", "", "", "", "", "", "", ""
        ));
        public Map<String, Integer> questProgress = new LinkedHashMap<>();
        public Set<String> claimedQuests = new LinkedHashSet<>();
    }

    public static final List<CatalogItem> CATALOG = List.of(
            // --- CAPES ---
            new CatalogItem("cape_jjn_nova", "JJN Nova Gradient", Category.CAPE, 220, "RARE", "#818CF8",
                    "Obsidian-to-indigo gradient cloak stamped with the glowing JJN Creeper emblem."),
            new CatalogItem("cape_aurora", "Northern Aurora", Category.CAPE, 300, "EPIC", "#34D399",
                    "Shimmering polar cyan and emerald ribbons woven with stardust trim."),
            new CatalogItem("cape_crimson_dragon", "Crimson Wyrm", Category.CAPE, 360, "EPIC", "#F87171",
                    "Obsidian dragon-scale weave edged with blazing crimson and ember gold."),
            new CatalogItem("cape_golden_monarch", "Royal Monarch", Category.CAPE, 450, "LEGENDARY", "#FBBF24",
                    "Deep royal violet velvet bordered in 24k gold filigree with a crown crest."),

            // --- 3D BACK / WINGS ---
            new CatalogItem("back_angel_wings", "Seraph Wings", Category.BACK, 520, "LEGENDARY", "#FDE68A",
                    "Articulated 3D pearl-white & gold feathered wings that gently flap behind you."),
            new CatalogItem("back_dragon_wings", "Shadow Dragon Wings", Category.BACK, 580, "LEGENDARY", "#A78BFA",
                    "Sculpted obsidian dragon wings veined with glowing violet membranes."),
            new CatalogItem("back_cyber_jet", "Indigo Aero-Pack", Category.BACK, 440, "EPIC", "#C7D2FE",
                    "Sleek dual-thruster aerospace pack with pulsing indigo reactor cores."),

            // --- 3D HEADGEAR ---
            new CatalogItem("hat_halo", "Celestial Halo", Category.HAT, 320, "EPIC", "#FCD34D",
                    "Luminous golden 3D ring floating and gently bobbing above your head."),
            new CatalogItem("hat_crown", "Monarch Crown", Category.HAT, 400, "LEGENDARY", "#FBBF24",
                    "3D crenellated golden crown set with indigo, emerald, and ruby gems."),
            new CatalogItem("hat_horns", "Oni Crimson Horns", Category.HAT, 280, "RARE", "#FB7185",
                    "Curved crimson 3D horns mounted cleanly above your brow."),
            new CatalogItem("hat_fox_ears", "Kitsune Ears", Category.HAT, 260, "RARE", "#FB923C",
                    "Playful 3D amber fox ears with warm ivory inner tufts."),

            // --- ANIMATED PARTICLE AURAS ---
            new CatalogItem("aura_starlight", "Starlight Orbit", Category.AURA, 300, "EPIC", "#C7D2FE",
                    "Orbiting celestial sparkles spiraling around you as you explore."),
            new CatalogItem("aura_blossom", "Cherry Blossom Wind", Category.AURA, 340, "EPIC", "#F9A8D4",
                    "Gentle cherry blossom petals drifting around your footsteps."),
            new CatalogItem("aura_soulfire", "Soul Flame Vortex", Category.AURA, 380, "LEGENDARY", "#38BDF8",
                    "Rising cyan soul-fire embers swirling from the ground upward."),
            new CatalogItem("aura_emerald", "Creeper Overcharge", Category.AURA, 350, "EPIC", "#86EFAC",
                    "Charged electric sparks & emerald bursts pulsing around your silhouette."),

            // --- 3D EMOTES ---
            new CatalogItem("emote_wave", "Friendly Wave", Category.EMOTE, 0, "STARTER", "#86EFAC",
                    "Cheerful overhead wave to greet friends. Unlocked by default!"),
            new CatalogItem("emote_salute", "Commander Salute", Category.EMOTE, 110, "COMMON", "#93C5FD",
                    "Crisp tactical salute with upright stance and clean arm snap."),
            new CatalogItem("emote_clap", "Applause", Category.EMOTE, 130, "COMMON", "#FDE047",
                    "Rhythmic two-handed applause with celebratory spark particles."),
            new CatalogItem("emote_tpose", "Ascended T-Pose", Category.EMOTE, 150, "RARE", "#C7D2FE",
                    "Assert pure dominance with locked horizontal arms and a slow levitation sway."),
            new CatalogItem("emote_facepalm", "Facepalm", Category.EMOTE, 160, "RARE", "#FCA5A5",
                    "Slow dramatic palm-to-forehead shake for questionable teammate plays."),
            new CatalogItem("emote_dab", "Classic Dab", Category.EMOTE, 180, "RARE", "#67E8F9",
                    "Snap into a sharp diagonal dab pose with head tucked into the elbow."),
            new CatalogItem("emote_backflip", "Starlight Spin", Category.EMOTE, 300, "EPIC", "#A78BFA",
                    "Mid-air 360-degree tucked flip and celebratory victory finish!"),
            new CatalogItem("emote_breakdance", "Breakdance Groove", Category.EMOTE, 380, "LEGENDARY", "#F472B6",
                    "High-energy rhythmic floorwork groove with alternating arm/leg kicks & note particles.")
    );

    public static final List<QuestDef> QUESTS = List.of(
            // Daily Quests
            new QuestDef("daily_traveler", "Explorer's Stride", "Walk or sprint 500 blocks in any world", true, 500, 120),
            new QuestDef("daily_jump", "Sky Hopper", "Jump 80 times in-game", true, 80, 90),
            new QuestDef("daily_playtime", "Session Grinder", "Play for 10 minutes inside a world", true, 10, 150),
            new QuestDef("daily_emote", "Express Yourself", "Perform 3 emotes in-game (Press B)", true, 3, 100),
            new QuestDef("daily_waypoint", "Pathfinder", "Create a named Waypoint (Press U)", true, 1, 80),

            // Lifetime Milestones
            new QuestDef("quest_first_friend", "Squad Up", "Add at least 1 friend or host a world", false, 1, 200),
            new QuestDef("quest_fashion", "Style Check", "Equip any Shop cosmetic or custom Cape", false, 1, 150),
            new QuestDef("quest_marathon", "World Runner", "Travel 3,000 blocks across your worlds", false, 3000, 350),
            new QuestDef("quest_acrobat", "Cloud Dancer", "Jump 500 times total", false, 500, 300),
            new QuestDef("quest_emote_star", "Stage Star", "Perform 15 emotes in-game", false, 15, 250)
    );

    public record CreatorCodeDef(
            String code,
            String creatorName,
            int discountPercent,
            int dealDiscountPercent,
            List<String> featuredItemIds,
            int bonusCoins,
            String tagline
    ) {}

    public static final class CreatorState {
        public String activeCode = "";
        public Set<String> redeemedBonusCodes = new LinkedHashSet<>();
    }

    public static final class CustomCreatorCodesFile {
        public List<CreatorCodeDef> codes = new ArrayList<>();
    }

    public static final List<CreatorCodeDef> BUILT_IN_CREATOR_CODES = List.of(
            new CreatorCodeDef(
                    "JJN",
                    "JJN Official",
                    20,
                    35,
                    List.of("cape_jjn_nova", "hat_crown", "back_angel_wings", "emote_backflip"),
                    100,
                    "20% off Shop + 35% off Featured Creator Deals + 100 ◈ bonus!"
            ),
            new CreatorCodeDef(
                    "BLUE",
                    "JJN Blue Team",
                    20,
                    40,
                    List.of("cape_aurora", "back_cyber_jet", "aura_soulfire", "emote_salute"),
                    75,
                    "20% off Shop + 40% off Blue Cosmic Deals + 75 ◈ bonus!"
            ),
            new CreatorCodeDef(
                    "VIP",
                    "JJN VIP",
                    30,
                    45,
                    List.of("cape_golden_monarch", "back_dragon_wings", "emote_breakdance"),
                    150,
                    "30% off entire Shop + 45% off Legendary Deals + 150 ◈ bonus!"
            ),
            new CreatorCodeDef(
                    "WELCOME",
                    "Welcome Bundle",
                    15,
                    30,
                    List.of("hat_fox_ears", "aura_starlight", "emote_dab"),
                    100,
                    "15% off Shop + 30% off Starter Picks + 100 ◈ welcome gift!"
            )
    );

    public static final String ADMIN_ACCESS_CODE = "JJNADMIN";

    public static boolean isAdminCode(String rawCode) {
        return ADMIN_ACCESS_CODE.equals(normalizeCode(rawCode));
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path economyFile() {
        return LauncherSettings.ROOT_DIR.resolve("economy.json");
    }

    public static Path creatorCodesFile() {
        return LauncherSettings.ROOT_DIR.resolve("creator-codes.json");
    }

    public static Path creatorStateFile() {
        return LauncherSettings.ROOT_DIR.resolve("creator-state.json");
    }

    public static CatalogItem findItem(String id) {
        if (id == null) return null;
        for (CatalogItem item : CATALOG) {
            if (item.id().equals(id)) return item;
        }
        return null;
    }

    public static QuestDef findQuest(String id) {
        if (id == null) return null;
        for (QuestDef q : QUESTS) {
            if (q.id().equals(id)) return q;
        }
        return null;
    }

    public static List<CreatorCodeDef> allCreatorCodes() {
        Map<String, CreatorCodeDef> merged = new LinkedHashMap<>();
        for (CreatorCodeDef def : BUILT_IN_CREATOR_CODES) {
            String key = normalizeCode(def.code());
            if (!key.isEmpty()) {
                merged.put(key, def);
            }
        }
        for (CreatorCodeDef custom : loadCustomCreatorCodes()) {
            if (custom == null) continue;
            String key = normalizeCode(custom.code());
            if (key.isEmpty()) continue;
            String creator = (custom.creatorName() == null || custom.creatorName().isBlank())
                    ? key
                    : custom.creatorName().trim();
            int basePct = clampPercent(custom.discountPercent());
            int dealPct = custom.dealDiscountPercent() > 0
                    ? clampPercent(custom.dealDiscountPercent())
                    : basePct;
            List<String> featured = custom.featuredItemIds() == null
                    ? List.of()
                    : custom.featuredItemIds().stream().filter(Objects::nonNull).toList();
            int bonus = Math.max(0, custom.bonusCoins());
            String tag = (custom.tagline() == null || custom.tagline().isBlank())
                    ? (basePct + "% off Shop" + (bonus > 0 ? " + " + bonus + " ◈ bonus" : ""))
                    : custom.tagline().trim();
            merged.put(key, new CreatorCodeDef(key, creator, basePct, dealPct, featured, bonus, tag));
        }
        return List.copyOf(merged.values());
    }

    public static CreatorCodeDef findCreatorCode(String rawCode) {
        String key = normalizeCode(rawCode);
        if (key.isEmpty()) return null;
        for (CreatorCodeDef def : allCreatorCodes()) {
            if (def.code().equals(key)) {
                return def;
            }
        }
        return null;
    }

    public static String normalizeCode(String rawCode) {
        if (rawCode == null) return "";
        return rawCode.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isFeaturedDeal(CreatorCodeDef code, CatalogItem item) {
        if (code == null || item == null || code.featuredItemIds() == null) return false;
        return code.featuredItemIds().contains(item.id());
    }

    public static int discountForItem(CreatorCodeDef code, CatalogItem item) {
        if (code == null || item == null || item.price() <= 0) return 0;
        if (isFeaturedDeal(code, item)) {
            return clampPercent(Math.max(code.discountPercent(), code.dealDiscountPercent()));
        }
        return clampPercent(code.discountPercent());
    }

    public static int effectivePrice(CreatorCodeDef code, CatalogItem item) {
        if (item == null) return 0;
        int pct = discountForItem(code, item);
        if (pct <= 0 || item.price() <= 0) return item.price();
        return Math.max(1, (int) Math.round(item.price() * (100 - pct) / 100.0));
    }

    private static int clampPercent(int pct) {
        return Math.max(0, Math.min(90, pct));
    }

    public static synchronized List<CreatorCodeDef> loadCustomCreatorCodes() {
        Path file = creatorCodesFile();
        if (!Files.isRegularFile(file)) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(new CustomCreatorCodesFile()), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }
            return List.of();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json.trim().startsWith("[")) {
                CreatorCodeDef[] arr = GSON.fromJson(json, CreatorCodeDef[].class);
                return arr == null ? List.of() : Arrays.asList(arr);
            }
            CustomCreatorCodesFile parsed = GSON.fromJson(json, CustomCreatorCodesFile.class);
            return parsed != null && parsed.codes != null ? parsed.codes : List.of();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public static synchronized void saveCustomCreatorCode(CreatorCodeDef def) {
        if (def == null) return;
        String key = normalizeCode(def.code());
        if (key.isEmpty() || isAdminCode(key)) return;
        String creator = (def.creatorName() == null || def.creatorName().isBlank()) ? key : def.creatorName().trim();
        int basePct = clampPercent(def.discountPercent());
        int dealPct = def.dealDiscountPercent() > 0 ? clampPercent(def.dealDiscountPercent()) : basePct;
        List<String> featured = def.featuredItemIds() == null
                ? List.of()
                : def.featuredItemIds().stream().filter(Objects::nonNull).toList();
        int bonus = Math.max(0, def.bonusCoins());
        String tag = (def.tagline() == null || def.tagline().isBlank())
                ? (basePct + "% off Shop" + (bonus > 0 ? " + " + bonus + " ◈ bonus" : ""))
                : def.tagline().trim();
        CreatorCodeDef clean = new CreatorCodeDef(key, creator, basePct, dealPct, featured, bonus, tag);

        List<CreatorCodeDef> updated = new ArrayList<>();
        for (CreatorCodeDef existing : loadCustomCreatorCodes()) {
            if (existing != null && !normalizeCode(existing.code()).equals(key)) {
                updated.add(existing);
            }
        }
        updated.add(clean);
        writeCustomCreatorCodesFile(updated);
    }

    public static synchronized boolean deleteCustomCreatorCode(String rawCode) {
        String key = normalizeCode(rawCode);
        if (key.isEmpty()) return false;
        List<CreatorCodeDef> existing = loadCustomCreatorCodes();
        List<CreatorCodeDef> remaining = new ArrayList<>();
        boolean removed = false;
        for (CreatorCodeDef def : existing) {
            if (def != null && normalizeCode(def.code()).equals(key)) {
                removed = true;
            } else if (def != null) {
                remaining.add(def);
            }
        }
        if (removed) {
            writeCustomCreatorCodesFile(remaining);
        }
        return removed;
    }

    private static void writeCustomCreatorCodesFile(List<CreatorCodeDef> codes) {
        try {
            Path file = creatorCodesFile();
            Files.createDirectories(file.getParent());
            CustomCreatorCodesFile wrapper = new CustomCreatorCodesFile();
            wrapper.codes = new ArrayList<>(codes);
            Path tmp = file.resolveSibling("creator-codes.json.tmp");
            Files.writeString(tmp, GSON.toJson(wrapper), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    public synchronized CreatorState loadCreatorState() {
        Path file = creatorStateFile();
        CreatorState state = null;
        if (Files.isRegularFile(file)) {
            try {
                state = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), CreatorState.class);
            } catch (Exception ignored) {
            }
        }
        if (state == null) {
            state = new CreatorState();
        }
        if (state.activeCode == null) {
            state.activeCode = "";
        }
        if (state.redeemedBonusCodes == null) {
            state.redeemedBonusCodes = new LinkedHashSet<>();
        }
        return state;
    }

    public synchronized void saveCreatorState(CreatorState state) {
        try {
            Files.createDirectories(creatorStateFile().getParent());
            Path tmp = creatorStateFile().resolveSibling("creator-state.json.tmp");
            Files.writeString(tmp, GSON.toJson(state), StandardCharsets.UTF_8);
            Files.move(tmp, creatorStateFile(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    public synchronized EconomyData load() {
        Path file = economyFile();
        EconomyData data = null;
        if (Files.isRegularFile(file)) {
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                data = GSON.fromJson(json, EconomyData.class);
            } catch (Exception ignored) {
            }
        }
        boolean dirty = false;
        if (data == null) {
            data = new EconomyData();
            dirty = true;
        }
        if (data.ownedItems == null) {
            data.ownedItems = new LinkedHashSet<>();
            dirty = true;
        }
        if (data.ownedItems.add("emote_wave")) {
            dirty = true;
        }
        if (data.equippedCapeId == null) data.equippedCapeId = "";
        if (data.equippedBackId == null) data.equippedBackId = "";
        if (data.equippedHatId == null) data.equippedHatId = "";
        if (data.equippedAuraId == null) data.equippedAuraId = "";
        if (data.emoteWheel == null) {
            data.emoteWheel = new ArrayList<>();
            dirty = true;
        }
        while (data.emoteWheel.size() < 8) {
            data.emoteWheel.add(data.emoteWheel.isEmpty() ? "emote_wave" : "");
            dirty = true;
        }
        if (data.questProgress == null) {
            data.questProgress = new LinkedHashMap<>();
            dirty = true;
        }
        if (data.claimedQuests == null) {
            data.claimedQuests = new LinkedHashSet<>();
            dirty = true;
        }

        String today = LocalDate.now().toString();
        if (!today.equals(data.dailyDate)) {
            data.dailyDate = today;
            for (QuestDef q : QUESTS) {
                if (q.daily()) {
                    data.questProgress.put(q.id(), 0);
                    data.claimedQuests.remove(q.id());
                }
            }
            dirty = true;
        }

        if (dirty || !Files.isRegularFile(file)) {
            save(data);
        }
        return data;
    }

    public synchronized void save(EconomyData data) {
        try {
            Files.createDirectories(economyFile().getParent());
            Path tmp = economyFile().resolveSibling("economy.json.tmp");
            Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
            Files.move(tmp, economyFile(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    /**
     * Procedurally generates crisp 64x32 Minecraft-format cape artwork for JavaFX previewing
     * (matching the exact in-game cape textures).
     */
    public static Image createCapePreviewImage(String capeId) {
        if (capeId == null || capeId.isBlank()) return null;
        if (findItem(capeId) == null) return null;

        WritableImage img = new WritableImage(64, 32);
        PixelWriter pw = img.getPixelWriter();

        // Transparent fill
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                pw.setColor(x, y, Color.TRANSPARENT);
            }
        }

        // Front panel (x: 1..10, y: 1..16) and back/inner panel (x: 12..21, y: 1..16) + trim
        for (int py = 0; py < 18; py++) {
            for (int px = 0; px < 22; px++) {
                boolean isFront = px >= 1 && px <= 10 && py >= 1 && py <= 16;
                boolean isInner = px >= 12 && px <= 21 && py >= 1 && py <= 16;
                if (!isFront && !isInner && !(py == 0 && px >= 1 && px <= 21) && !(px == 0 || px == 11)) {
                    continue;
                }
                int u = isFront ? (px - 1) : (isInner ? (px - 12) : 0);
                int v = Math.max(0, Math.min(15, py - 1));
                Color c = sampleCapePixel(capeId, u, v, isFront);
                pw.setColor(px, py, c);
            }
        }
        return img;
    }

    private static Color sampleCapePixel(String capeId, int u, int v, boolean isFront) {
        boolean border = (u == 0 || u == 9 || v == 0 || v == 15);
        double t = v / 15.0;
        switch (capeId) {
            case "cape_jjn_nova" -> {
                if (border) return Color.web("#c7d2fe");
                // Center Creeper glyph (u in 2..7, v in 4..10)
                if (isFront && isCreeperEmblem(u - 2, v - 4)) {
                    return Color.web("#e0e7ff");
                }
                return Color.web("#141824").interpolate(Color.web("#4f46e5"), t * 0.85);
            }
            case "cape_aurora" -> {
                if (border) return Color.web("#67e8f9");
                double wave = 0.5 + 0.5 * Math.sin((u * 0.65) + (v * 0.45));
                Color c1 = Color.web("#0f172a").interpolate(Color.web("#06b6d4"), t);
                return c1.interpolate(Color.web("#34d399"), wave * 0.65);
            }
            case "cape_crimson_dragon" -> {
                if (border) return Color.web("#fbbf24");
                if (isFront && ((u == 4 || u == 5) && v >= 3 && v <= 12)) {
                    return Color.web("#f59e0b");
                }
                boolean scale = ((u + v) % 2 == 0);
                return scale ? Color.web("#7f1d1d").interpolate(Color.web("#ef4444"), t * 0.6)
                        : Color.web("#1c1917");
            }
            case "cape_golden_monarch" -> {
                if (border) return Color.web("#fcd34d");
                if (isFront && isCrownEmblem(u - 2, v - 5)) {
                    return Color.web("#fde047");
                }
                return Color.web("#3b0764").interpolate(Color.web("#6b21a8"), t);
            }
            default -> {
                return Color.web("#1f2937");
            }
        }
    }

    private static boolean isCreeperEmblem(int x, int y) {
        if (x < 0 || x >= 6 || y < 0 || y >= 7) return false;
        // Two 2x2 eyes
        if ((x <= 1 || x >= 4) && y <= 1) return true;
        // Center nose
        if ((x == 2 || x == 3) && (y == 2 || y == 3)) return true;
        // Mouth
        if ((x >= 1 && x <= 4) && y == 4) return true;
        if ((x == 1 || x == 4) && (y == 5 || y == 6)) return true;
        return false;
    }

    private static boolean isCrownEmblem(int x, int y) {
        if (x < 0 || x >= 6 || y < 0 || y >= 5) return false;
        if (y >= 3) return true;
        if (y == 1 && (x == 0 || x == 2 || x == 3 || x == 5)) return true;
        if (y == 2 && x != 1 && x != 4) return true;
        return y == 0 && (x == 0 || x == 5);
    }
}
