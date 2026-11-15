package com.jjn.client.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jjn.client.JjnConfig;
import com.jjn.client.friends.JjnFriendsManager;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;

/**
 * In-game economy, quests, 3D cosmetics, animated auras, and emote wheel manager for MC 26.2.
 * Persists identically to ~/.jjnclient/economy.json shared with JJNLauncher.
 */
public final class JjnEconomyManager {

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
            int accentArgb,
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

    public record Toast(String title, String subtitle, long expiresAtMs) {}

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
            new CatalogItem("cape_jjn_nova", "JJN Nova Gradient", Category.CAPE, 220, "RARE", 0xFF818CF8,
                    "Obsidian-to-indigo gradient cloak stamped with the glowing JJN Creeper emblem."),
            new CatalogItem("cape_aurora", "Northern Aurora", Category.CAPE, 300, "EPIC", 0xFF34D399,
                    "Shimmering polar cyan and emerald ribbons woven with stardust trim."),
            new CatalogItem("cape_crimson_dragon", "Crimson Wyrm", Category.CAPE, 360, "EPIC", 0xFFF87171,
                    "Obsidian dragon-scale weave edged with blazing crimson and ember gold."),
            new CatalogItem("cape_golden_monarch", "Royal Monarch", Category.CAPE, 450, "LEGENDARY", 0xFFFBBF24,
                    "Deep royal violet velvet bordered in 24k gold filigree with a crown crest."),

            // --- 3D BACK / WINGS ---
            new CatalogItem("back_angel_wings", "Seraph Wings", Category.BACK, 520, "LEGENDARY", 0xFFFDE68A,
                    "Articulated 3D pearl-white & gold feathered wings that gently flap behind you."),
            new CatalogItem("back_dragon_wings", "Shadow Dragon Wings", Category.BACK, 580, "LEGENDARY", 0xFFA78BFA,
                    "Sculpted obsidian dragon wings veined with glowing violet membranes."),
            new CatalogItem("back_cyber_jet", "Indigo Aero-Pack", Category.BACK, 440, "EPIC", 0xFFC7D2FE,
                    "Sleek dual-thruster aerospace pack with pulsing indigo reactor cores."),

            // --- 3D HEADGEAR ---
            new CatalogItem("hat_halo", "Celestial Halo", Category.HAT, 320, "EPIC", 0xFFFCD34D,
                    "Luminous golden 3D ring floating and gently bobbing above your head."),
            new CatalogItem("hat_crown", "Monarch Crown", Category.HAT, 400, "LEGENDARY", 0xFFFBBF24,
                    "3D crenellated golden crown set with indigo, emerald, and ruby gems."),
            new CatalogItem("hat_horns", "Oni Crimson Horns", Category.HAT, 280, "RARE", 0xFFFB7185,
                    "Curved crimson 3D horns mounted cleanly above your brow."),
            new CatalogItem("hat_fox_ears", "Kitsune Ears", Category.HAT, 260, "RARE", 0xFFFB923C,
                    "Playful 3D amber fox ears with warm ivory inner tufts."),

            // --- ANIMATED PARTICLE AURAS ---
            new CatalogItem("aura_starlight", "Starlight Orbit", Category.AURA, 300, "EPIC", 0xFFC7D2FE,
                    "Orbiting celestial sparkles spiraling around you as you explore."),
            new CatalogItem("aura_blossom", "Cherry Blossom Wind", Category.AURA, 340, "EPIC", 0xFFF9A8D4,
                    "Gentle cherry blossom petals drifting around your footsteps."),
            new CatalogItem("aura_soulfire", "Soul Flame Vortex", Category.AURA, 380, "LEGENDARY", 0xFF38BDF8,
                    "Rising cyan soul-fire embers swirling from the ground upward."),
            new CatalogItem("aura_emerald", "Creeper Overcharge", Category.AURA, 350, "EPIC", 0xFF86EFAC,
                    "Charged electric sparks & emerald bursts pulsing around your silhouette."),

            // --- 3D EMOTES ---
            new CatalogItem("emote_wave", "Friendly Wave", Category.EMOTE, 0, "STARTER", 0xFF86EFAC,
                    "Cheerful overhead wave to greet friends. Unlocked by default!"),
            new CatalogItem("emote_salute", "Commander Salute", Category.EMOTE, 110, "COMMON", 0xFF93C5FD,
                    "Crisp tactical salute with upright stance and clean arm snap."),
            new CatalogItem("emote_clap", "Applause", Category.EMOTE, 130, "COMMON", 0xFFFDE047,
                    "Rhythmic two-handed applause with celebratory spark particles."),
            new CatalogItem("emote_tpose", "Ascended T-Pose", Category.EMOTE, 150, "RARE", 0xFFC7D2FE,
                    "Assert pure dominance with locked horizontal arms and a slow levitation sway."),
            new CatalogItem("emote_facepalm", "Facepalm", Category.EMOTE, 160, "RARE", 0xFFFCA5A5,
                    "Slow dramatic palm-to-forehead shake for questionable teammate plays."),
            new CatalogItem("emote_dab", "Classic Dab", Category.EMOTE, 180, "RARE", 0xFF67E8F9,
                    "Snap into a sharp diagonal dab pose with head tucked into the elbow."),
            new CatalogItem("emote_backflip", "Starlight Spin", Category.EMOTE, 300, "EPIC", 0xFFA78BFA,
                    "Mid-air 360-degree tucked flip and celebratory victory finish!"),
            new CatalogItem("emote_breakdance", "Breakdance Groove", Category.EMOTE, 380, "LEGENDARY", 0xFFF472B6,
                    "High-energy rhythmic floorwork groove with alternating arm/leg kicks & note particles.")
    );

    public static final List<QuestDef> QUESTS = List.of(
            new QuestDef("daily_traveler", "Explorer's Stride", "Walk or sprint 500 blocks in any world", true, 500, 120),
            new QuestDef("daily_jump", "Sky Hopper", "Jump 80 times in-game", true, 80, 90),
            new QuestDef("daily_playtime", "Session Grinder", "Play for 10 minutes inside a world", true, 10, 150),
            new QuestDef("daily_emote", "Express Yourself", "Perform 3 emotes in-game (Press B)", true, 3, 100),
            new QuestDef("daily_waypoint", "Pathfinder", "Create a named Waypoint (Press U)", true, 1, 80),

            new QuestDef("quest_first_friend", "Squad Up", "Add at least 1 friend or host a world", false, 1, 200),
            new QuestDef("quest_fashion", "Style Check", "Equip any Shop cosmetic or custom Cape", false, 1, 150),
            new QuestDef("quest_marathon", "World Runner", "Travel 3,000 blocks across your worlds", false, 3000, 350),
            new QuestDef("quest_acrobat", "Cloud Dancer", "Jump 500 times total", false, 500, 300),
            new QuestDef("quest_emote_star", "Stage Star", "Perform 15 emotes in-game", false, 15, 250)
    );

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final JjnEconomyManager INSTANCE = new JjnEconomyManager();
    private static final long EMOTE_DURATION_MS = 3200L;

    private EconomyData data = new EconomyData();
    private long lastLoadedMtime = -1L;
    private String lastMessage = "";
    private Toast activeToast;

    // Emote state
    private String activeEmoteId = "";
    private long emoteStartMs = 0L;
    private CameraType savedCameraType = null;

    // Live quest tracking
    private int tickCounter = 0;
    private boolean dirty = false;
    private double lastX = Double.NaN;
    private double lastZ = Double.NaN;
    private double distAccum = 0.0;
    private boolean wasOnGround = true;
    private int playtimeTicks = 0;

    private JjnEconomyManager() {}

    public static JjnEconomyManager get() {
        return INSTANCE;
    }

    public static Path economyFile() {
        return Path.of(System.getProperty("user.home"), ".jjnclient", "economy.json");
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

    public synchronized void start() {
        reloadFromDisk();
    }

    public synchronized void reloadFromDisk() {
        Path file = economyFile();
        EconomyData loaded = null;
        if (Files.isRegularFile(file)) {
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                loaded = GSON.fromJson(json, EconomyData.class);
            } catch (Exception ignored) {
            }
        }
        boolean writeBack = false;
        if (loaded == null) {
            loaded = new EconomyData();
            writeBack = true;
        }
        if (loaded.ownedItems == null) {
            loaded.ownedItems = new LinkedHashSet<>();
            writeBack = true;
        }
        if (loaded.ownedItems.add("emote_wave")) writeBack = true;
        if (loaded.equippedCapeId == null) loaded.equippedCapeId = "";
        if (loaded.equippedBackId == null) loaded.equippedBackId = "";
        if (loaded.equippedHatId == null) loaded.equippedHatId = "";
        if (loaded.equippedAuraId == null) loaded.equippedAuraId = "";
        if (loaded.emoteWheel == null) {
            loaded.emoteWheel = new ArrayList<>();
            writeBack = true;
        }
        while (loaded.emoteWheel.size() < 8) {
            loaded.emoteWheel.add(loaded.emoteWheel.isEmpty() ? "emote_wave" : "");
            writeBack = true;
        }
        if (loaded.questProgress == null) {
            loaded.questProgress = new LinkedHashMap<>();
            writeBack = true;
        }
        if (loaded.claimedQuests == null) {
            loaded.claimedQuests = new LinkedHashSet<>();
            writeBack = true;
        }

        String today = LocalDate.now().toString();
        if (!today.equals(loaded.dailyDate)) {
            loaded.dailyDate = today;
            for (QuestDef q : QUESTS) {
                if (q.daily()) {
                    loaded.questProgress.put(q.id(), 0);
                    loaded.claimedQuests.remove(q.id());
                }
            }
            writeBack = true;
        }

        this.data = loaded;
        if (writeBack || !Files.isRegularFile(file)) {
            save();
        } else {
            this.lastLoadedMtime = readMtime();
        }
    }

    public synchronized void save() {
        try {
            Path file = economyFile();
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("economy.json.tmp");
            Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            lastLoadedMtime = readMtime();
            dirty = false;
        } catch (IOException ignored) {
        }
    }

    private long readMtime() {
        try {
            Path p = economyFile();
            if (Files.isRegularFile(p)) {
                return Files.getLastModifiedTime(p).toMillis();
            }
        } catch (IOException ignored) {
        }
        return -1L;
    }

    public EconomyData snapshot() {
        return data;
    }

    public int getCoins() {
        return data.coins;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public boolean isOwned(String itemId) {
        return data.ownedItems.contains(itemId);
    }

    public boolean isEquipped(String itemId) {
        if (itemId == null || itemId.isEmpty()) return false;
        return itemId.equals(data.equippedCapeId)
                || itemId.equals(data.equippedBackId)
                || itemId.equals(data.equippedHatId)
                || itemId.equals(data.equippedAuraId);
    }

    public int getQuestProgress(String questId) {
        return data.questProgress.getOrDefault(questId, 0);
    }

    public boolean isQuestClaimed(String questId) {
        return data.claimedQuests.contains(questId);
    }

    public boolean isQuestReadyToClaim(QuestDef q) {
        return q != null && !isQuestClaimed(q.id()) && getQuestProgress(q.id()) >= q.target();
    }

    public int countClaimableQuests() {
        int c = 0;
        for (QuestDef q : QUESTS) {
            if (isQuestReadyToClaim(q)) c++;
        }
        return c;
    }

    public Toast getActiveToast() {
        if (activeToast != null && System.currentTimeMillis() > activeToast.expiresAtMs()) {
            activeToast = null;
        }
        return activeToast;
    }

    public void showToast(String title, String subtitle) {
        activeToast = new Toast(title, subtitle, System.currentTimeMillis() + 5000L);
    }

    public synchronized boolean purchaseItem(String itemId) {
        CatalogItem item = findItem(itemId);
        if (item == null) return false;
        if (data.ownedItems.contains(itemId)) {
            lastMessage = item.name() + " already owned.";
            return true;
        }
        if (data.coins < item.price()) {
            lastMessage = "Need " + (item.price() - data.coins) + " more JJN Coins!";
            return false;
        }
        data.coins -= item.price();
        data.ownedItems.add(itemId);
        if (item.category() == Category.EMOTE) {
            assignToFirstEmptyWheelSlot(itemId);
        } else {
            applyEquipInternal(item);
        }
        lastMessage = "Unlocked " + item.name() + "!";
        save();
        if (item.category() != Category.EMOTE) {
            JjnFriendsManager.get().notifyCosmeticsOrEmoteChanged();
        }
        return true;
    }

    public synchronized void toggleEquip(String itemId) {
        CatalogItem item = findItem(itemId);
        if (item == null || !data.ownedItems.contains(itemId)) return;
        if (item.category() == Category.EMOTE) return;

        if (isEquipped(itemId)) {
            unequipCategory(item.category());
            lastMessage = "Unequipped " + item.name() + ".";
        } else {
            applyEquipInternal(item);
            lastMessage = "Equipped " + item.name() + "!";
            save();
            JjnFriendsManager.get().notifyCosmeticsOrEmoteChanged();
        }
    }

    public synchronized void unequipCategory(Category cat) {
        switch (cat) {
            case CAPE -> data.equippedCapeId = "";
            case BACK -> data.equippedBackId = "";
            case HAT -> data.equippedHatId = "";
            case AURA -> data.equippedAuraId = "";
            default -> {}
        }
        save();
        JjnFriendsManager.get().notifyCosmeticsOrEmoteChanged();
    }

    private void applyEquipInternal(CatalogItem item) {
        switch (item.category()) {
            case CAPE -> data.equippedCapeId = item.id();
            case BACK -> data.equippedBackId = item.id();
            case HAT -> data.equippedHatId = item.id();
            case AURA -> data.equippedAuraId = item.id();
            default -> {}
        }
        incrementQuest("quest_fashion", 1);
    }

    public synchronized void setWheelSlot(int slotIndex, String emoteId) {
        if (slotIndex < 0 || slotIndex >= 8) return;
        String val = emoteId == null ? "" : emoteId.trim();
        if (!val.isEmpty() && !data.ownedItems.contains(val)) return;
        while (data.emoteWheel.size() < 8) data.emoteWheel.add("");
        data.emoteWheel.set(slotIndex, val);
        lastMessage = val.isEmpty()
                ? "Cleared Slot [" + (slotIndex + 1) + "]"
                : "Bound Emote to Key [" + (slotIndex + 1) + "]";
        save();
    }

    private void assignToFirstEmptyWheelSlot(String emoteId) {
        if (data.emoteWheel.contains(emoteId)) return;
        for (int i = 0; i < data.emoteWheel.size(); i++) {
            if (data.emoteWheel.get(i) == null || data.emoteWheel.get(i).isBlank()) {
                data.emoteWheel.set(i, emoteId);
                return;
            }
        }
    }

    public synchronized boolean claimQuest(String questId) {
        QuestDef q = findQuest(questId);
        if (q == null || !isQuestReadyToClaim(q)) return false;
        data.claimedQuests.add(questId);
        data.coins += q.rewardCoins();
        data.totalEarned += q.rewardCoins();
        lastMessage = "Claimed +" + q.rewardCoins() + " Coins from " + q.title() + "!";
        showToast("REWARD CLAIMED: +" + q.rewardCoins() + " ◈", q.title());
        save();
        return true;
    }

    public synchronized void recordWaypointCreated() {
        incrementQuest("daily_waypoint", 1);
        save();
    }

    public synchronized void playEmote(Minecraft mc, String emoteId) {
        if (emoteId == null || emoteId.isBlank() || !data.ownedItems.contains(emoteId)) return;
        this.activeEmoteId = emoteId;
        this.emoteStartMs = System.currentTimeMillis();

        if (mc != null && mc.options != null && JjnConfig.get().emoteAutoThirdPerson) {
            if (savedCameraType == null) {
                CameraType current = mc.options.getCameraType();
                if (current == CameraType.FIRST_PERSON) {
                    savedCameraType = current;
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                }
            }
        }

        incrementQuest("daily_emote", 1);
        incrementQuest("quest_emote_star", 1);
        save();
        JjnFriendsManager.get().notifyCosmeticsOrEmoteChanged();
    }

    public synchronized void stopEmote(Minecraft mc) {
        boolean wasActive = this.activeEmoteId != null && !this.activeEmoteId.isEmpty();
        this.activeEmoteId = "";
        this.emoteStartMs = 0L;
        if (mc != null && mc.options != null && savedCameraType != null) {
            mc.options.setCameraType(savedCameraType);
            savedCameraType = null;
        }
        if (wasActive) {
            JjnFriendsManager.get().notifyCosmeticsOrEmoteChanged();
        }
    }

    public String getActiveEmoteId() {
        if (activeEmoteId == null || activeEmoteId.isEmpty()) return "";
        if (System.currentTimeMillis() - emoteStartMs > EMOTE_DURATION_MS) {
            return "";
        }
        return activeEmoteId;
    }

    public long getEmoteElapsedMs() {
        return System.currentTimeMillis() - emoteStartMs;
    }

    public void tick(Minecraft mc) {
        tickCounter++;
        if (tickCounter % 40 == 0) {
            long mtime = readMtime();
            if (mtime > 0 && mtime != lastLoadedMtime && !dirty) {
                String prev = data.equippedCapeId + "|" + data.equippedBackId + "|" + data.equippedHatId + "|" + data.equippedAuraId;
                reloadFromDisk();
                String curr = data.equippedCapeId + "|" + data.equippedBackId + "|" + data.equippedHatId + "|" + data.equippedAuraId;
                if (!prev.equals(curr)) {
                    JjnFriendsManager.get().notifyCosmeticsOrEmoteChanged();
                }
            }
        }

        // Auto-expire emote or cancel if player starts walking
        if (activeEmoteId != null && !activeEmoteId.isEmpty()) {
            long elapsed = System.currentTimeMillis() - emoteStartMs;
            boolean moving = mc != null && mc.player != null && elapsed > 400L
                    && (mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.006 || mc.player.isShiftKeyDown());
            if (elapsed > EMOTE_DURATION_MS || moving) {
                stopEmote(mc);
            }
        }

        if (mc == null || mc.player == null || mc.level == null) {
            lastX = Double.NaN;
            lastZ = Double.NaN;
            return;
        }

        Player player = mc.player;

        // Track horizontal distance traveled
        double px = player.getX();
        double pz = player.getZ();
        if (!Double.isNaN(lastX) && !Double.isNaN(lastZ)) {
            double dx = px - lastX;
            double dz = pz - lastZ;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 0.01 && d < 8.0) {
                distAccum += d;
                if (distAccum >= 1.0) {
                    int whole = (int) distAccum;
                    distAccum -= whole;
                    incrementQuest("daily_traveler", whole);
                    incrementQuest("quest_marathon", whole);
                }
            }
        }
        lastX = px;
        lastZ = pz;

        // Track Jumps
        boolean onGround = player.onGround();
        if (wasOnGround && !onGround && player.getDeltaMovement().y > 0.15) {
            incrementQuest("daily_jump", 1);
            incrementQuest("quest_acrobat", 1);
        }
        wasOnGround = onGround;

        // Track Playtime (1 minute = 1200 ticks)
        playtimeTicks++;
        if (playtimeTicks >= 1200) {
            playtimeTicks -= 1200;
            incrementQuest("daily_playtime", 1);
        }

        // Passive milestones sync
        if (tickCounter % 60 == 0) {
            if (!data.equippedCapeId.isEmpty() || !data.equippedBackId.isEmpty()
                    || !data.equippedHatId.isEmpty() || !data.equippedAuraId.isEmpty()
                    || JjnConfig.get().localCapeEnabled) {
                incrementQuest("quest_fashion", 1);
            }
            JjnFriendsManager fm = JjnFriendsManager.get();
            if (!fm.getFriendsWithStatus().isEmpty() || fm.isHosting()) {
                incrementQuest("quest_first_friend", 1);
            }
        }

        if (dirty && tickCounter % 100 == 0) {
            save();
        }

        // Spawn Aura & Emote particles for self and synced friends
        JjnFriendsManager fm = JjnFriendsManager.get();
        for (Player p : mc.level.players()) {
            if (p == null) continue;
            if (p == player) {
                spawnCosmeticParticles(mc.level, p, data.equippedAuraId, getActiveEmoteId());
            } else {
                String name = p.getGameProfile() != null ? p.getGameProfile().name() : "";
                JjnFriendsManager.SyncedCosmetics sc = fm.getCosmeticsForPlayer(p.getUUID(), name);
                if (!sc.auraId().isEmpty() || !sc.activeEmoteId().isEmpty()) {
                    spawnCosmeticParticles(mc.level, p, sc.auraId(), sc.activeEmoteId());
                }
            }
        }
    }

    private void incrementQuest(String questId, int delta) {
        if (delta <= 0) return;
        QuestDef def = findQuest(questId);
        if (def == null) return;
        int prev = data.questProgress.getOrDefault(questId, 0);
        if (prev >= def.target()) return;
        int next = Math.min(def.target(), prev + delta);
        data.questProgress.put(questId, next);
        dirty = true;
        if (prev < def.target() && next >= def.target() && !isQuestClaimed(questId)) {
            showToast("QUEST COMPLETE: " + def.title(), "Claim +" + def.rewardCoins() + " ◈ in JJN Menu -> Shop & Quests!");
            save();
        }
    }

    private void spawnCosmeticParticles(ClientLevel level, Player player, String aura, String emote) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        double t = System.currentTimeMillis() / 1000.0;

        if (aura != null && !aura.isEmpty()) {
            switch (aura) {
                case "aura_starlight" -> {
                    if (tickCounter % 3 == 0) {
                        double a = t * 2.6;
                        level.addParticle(ParticleTypes.END_ROD,
                                x + Math.cos(a) * 0.72, y + 0.9 + Math.sin(t * 2.0) * 0.35, z + Math.sin(a) * 0.72,
                                0, 0.015, 0);
                    }
                }
                case "aura_blossom" -> {
                    if (tickCounter % 4 == 0) {
                        double rx = (Math.random() - 0.5) * 1.4;
                        double rz = (Math.random() - 0.5) * 1.4;
                        level.addParticle(ParticleTypes.CHERRY_LEAVES,
                                x + rx, y + 1.85, z + rz,
                                (Math.random() - 0.5) * 0.02, -0.02, (Math.random() - 0.5) * 0.02);
                    }
                }
                case "aura_soulfire" -> {
                    if (tickCounter % 3 == 0) {
                        double a = t * 3.8;
                        level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                                x + Math.cos(a) * 0.65, y + 0.15, z + Math.sin(a) * 0.65,
                                0, 0.045, 0);
                    }
                }
                case "aura_emerald" -> {
                    if (tickCounter % 4 == 0) {
                        double a = t * 3.2;
                        level.addParticle(tickCounter % 8 == 0 ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.ELECTRIC_SPARK,
                                x + Math.cos(a) * 0.75, y + 0.6 + (Math.random() * 0.8), z + Math.sin(a) * 0.75,
                                0, 0.02, 0);
                    }
                }
            }
        }

        // Emote flair particles
        if (emote != null && !emote.isEmpty() && tickCounter % 5 == 0) {
            switch (emote) {
                case "emote_clap" -> level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        x + (Math.random() - 0.5) * 0.5, y + 1.35, z + (Math.random() - 0.5) * 0.5,
                        0, 0.04, 0);
                case "emote_backflip" -> level.addParticle(ParticleTypes.END_ROD,
                        x + (Math.random() - 0.5) * 0.6, y + 1.1, z + (Math.random() - 0.5) * 0.6,
                        0, 0.03, 0);
                case "emote_breakdance" -> level.addParticle(ParticleTypes.NOTE,
                        x + (Math.random() - 0.5) * 0.9, y + 1.65, z + (Math.random() - 0.5) * 0.9,
                        Math.random(), 0.04, 0);
                case "emote_tpose" -> level.addParticle(ParticleTypes.END_ROD,
                        x + Math.cos(t * 4) * 0.85, y + 0.25, z + Math.sin(t * 4) * 0.85,
                        0, 0.02, 0);
            }
        }
    }
}
