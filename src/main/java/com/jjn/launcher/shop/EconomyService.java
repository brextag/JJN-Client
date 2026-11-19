package com.jjn.launcher.shop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

/**
 * Runtime coordinator for JJN Coins, Quests, Cosmetics, and Emotes in JJN Launcher.
 * Keeps ~/.jjnclient/economy.json synchronized with open Minecraft instances.
 */
public final class EconomyService {

    private final EconomyStore store = new EconomyStore();
    private volatile EconomyStore.EconomyData data = new EconomyStore.EconomyData();
    private volatile EconomyStore.CreatorState creatorState = new EconomyStore.CreatorState();
    private volatile long lastLoadedMtime = -1L;
    private volatile boolean started = false;
    private volatile boolean running = true;
    private volatile String lastMessage = "";

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService watcher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "JJN-Economy-Sync");
        t.setDaemon(true);
        return t;
    });

    public synchronized void start() {
        if (started) return;
        started = true;
        reloadFromDisk();
        watcher.scheduleAtFixedRate(this::pollDiskChanges, 2, 2, TimeUnit.SECONDS);
    }

    public void shutdown() {
        running = false;
        watcher.shutdownNow();
    }

    public void addChangeListener(Runnable listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    private void notifyListeners() {
        for (Runnable r : listeners) {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
    }

    public synchronized void reloadFromDisk() {
        this.data = store.load();
        this.creatorState = store.loadCreatorState();
        this.lastLoadedMtime = readMtime();
        notifyListeners();
    }

    private synchronized void saveAndTrack() {
        store.save(data);
        lastLoadedMtime = readMtime();
        notifyListeners();
    }

    private long readMtime() {
        try {
            Path p = EconomyStore.economyFile();
            if (Files.isRegularFile(p)) {
                return Files.getLastModifiedTime(p).toMillis();
            }
        } catch (IOException ignored) {
        }
        return -1L;
    }

    private void pollDiskChanges() {
        if (!running) return;
        long mtime = readMtime();
        if (mtime > 0 && mtime != lastLoadedMtime) {
            reloadFromDisk();
        }
    }

    public EconomyStore.EconomyData snapshot() {
        return data;
    }

    public int getCoins() {
        return data.coins;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public EconomyStore.CreatorCodeDef getActiveCreatorCode() {
        return EconomyStore.findCreatorCode(creatorState.activeCode);
    }

    public synchronized boolean applyCreatorCode(String rawCode) {
        String normalized = EconomyStore.normalizeCode(rawCode);
        if (normalized.isEmpty()) {
            lastMessage = "Enter a Creator Code first (e.g. JJN, BLUE, VIP, WELCOME).";
            notifyListeners();
            return false;
        }
        EconomyStore.CreatorCodeDef def = EconomyStore.findCreatorCode(normalized);
        if (def == null) {
            lastMessage = "Unknown Creator Code \"" + normalized + "\". Check the spelling or creator-codes.json.";
            notifyListeners();
            return false;
        }

        creatorState.activeCode = def.code();
        int bonus = 0;
        if (def.bonusCoins() > 0 && !creatorState.redeemedBonusCodes.contains(def.code())) {
            bonus = def.bonusCoins();
            creatorState.redeemedBonusCodes.add(def.code());
            data.coins += bonus;
            data.totalEarned += bonus;
            store.save(data);
            lastLoadedMtime = readMtime();
        }
        store.saveCreatorState(creatorState);

        lastMessage = "Creator Code [" + def.code() + "] active (" + def.creatorName() + "): "
                + def.tagline()
                + (bonus > 0 ? " Claimed +" + bonus + " ◈ bonus!" : "");
        notifyListeners();
        return true;
    }

    public synchronized void clearCreatorCode() {
        creatorState.activeCode = "";
        store.saveCreatorState(creatorState);
        lastMessage = "Cleared active Creator Code.";
        notifyListeners();
    }

    public int getDiscountPercent(EconomyStore.CatalogItem item) {
        return EconomyStore.discountForItem(getActiveCreatorCode(), item);
    }

    public int getEffectivePrice(EconomyStore.CatalogItem item) {
        return EconomyStore.effectivePrice(getActiveCreatorCode(), item);
    }

    public boolean isFeaturedCreatorDeal(EconomyStore.CatalogItem item) {
        return EconomyStore.isFeaturedDeal(getActiveCreatorCode(), item);
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

    public boolean isQuestReadyToClaim(EconomyStore.QuestDef q) {
        return q != null && !isQuestClaimed(q.id()) && getQuestProgress(q.id()) >= q.target();
    }

    public int countClaimableQuests() {
        int c = 0;
        for (EconomyStore.QuestDef q : EconomyStore.QUESTS) {
            if (isQuestReadyToClaim(q)) c++;
        }
        return c;
    }

    public synchronized boolean purchaseItem(String itemId) {
        EconomyStore.CatalogItem item = EconomyStore.findItem(itemId);
        if (item == null) return false;
        if (data.ownedItems.contains(itemId)) {
            lastMessage = item.name() + " is already owned.";
            notifyListeners();
            return true;
        }
        int cost = getEffectivePrice(item);
        if (data.coins < cost) {
            int need = cost - data.coins;
            lastMessage = "Need " + need + " more JJN Coins for " + item.name() + " — complete Quests to earn Coins!";
            notifyListeners();
            return false;
        }
        data.coins -= cost;
        data.ownedItems.add(itemId);
        if (item.category() == EconomyStore.Category.EMOTE) {
            assignToFirstEmptyWheelSlot(itemId);
        } else {
            applyEquipInternal(item);
        }
        EconomyStore.CreatorCodeDef activeCode = getActiveCreatorCode();
        int saved = Math.max(0, item.price() - cost);
        lastMessage = saved > 0 && activeCode != null
                ? "Unlocked " + item.name() + " with Code [" + activeCode.code() + "] (saved " + saved + " ◈)!"
                : "Unlocked " + item.name() + "!";
        saveAndTrack();
        return true;
    }

    public synchronized void toggleEquip(String itemId) {
        EconomyStore.CatalogItem item = EconomyStore.findItem(itemId);
        if (item == null || !data.ownedItems.contains(itemId)) return;
        if (item.category() == EconomyStore.Category.EMOTE) return;

        if (isEquipped(itemId)) {
            unequipCategory(item.category());
            lastMessage = "Unequipped " + item.name() + ".";
        } else {
            applyEquipInternal(item);
            lastMessage = "Equipped " + item.name() + "!";
        }
        saveAndTrack();
    }

    public synchronized void unequipCategory(EconomyStore.Category cat) {
        switch (cat) {
            case CAPE -> data.equippedCapeId = "";
            case BACK -> data.equippedBackId = "";
            case HAT -> data.equippedHatId = "";
            case AURA -> data.equippedAuraId = "";
            default -> {}
        }
        saveAndTrack();
    }

    private void applyEquipInternal(EconomyStore.CatalogItem item) {
        switch (item.category()) {
            case CAPE -> data.equippedCapeId = item.id();
            case BACK -> data.equippedBackId = item.id();
            case HAT -> data.equippedHatId = item.id();
            case AURA -> data.equippedAuraId = item.id();
            default -> {}
        }
        setQuestAtLeastInternal("quest_fashion", 1);
    }

    public synchronized void setWheelSlot(int slotIndex, String emoteId) {
        if (slotIndex < 0 || slotIndex >= 8) return;
        String val = emoteId == null ? "" : emoteId.trim();
        if (!val.isEmpty() && !data.ownedItems.contains(val)) return;
        while (data.emoteWheel.size() < 8) data.emoteWheel.add("");
        data.emoteWheel.set(slotIndex, val);
        lastMessage = val.isEmpty()
                ? "Cleared Emote Slot " + (slotIndex + 1)
                : "Bound Emote to Key [" + (slotIndex + 1) + "] in Emote Wheel!";
        saveAndTrack();
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
        EconomyStore.QuestDef q = EconomyStore.findQuest(questId);
        if (q == null || !isQuestReadyToClaim(q)) return false;
        data.claimedQuests.add(questId);
        data.coins += q.rewardCoins();
        data.totalEarned += q.rewardCoins();
        lastMessage = "Claimed +" + q.rewardCoins() + " JJN Coins from \"" + q.title() + "\"!";
        saveAndTrack();
        return true;
    }

    public synchronized void markFriendMilestone(int friendCount) {
        if (friendCount >= 1 && getQuestProgress("quest_first_friend") < 1) {
            setQuestAtLeastInternal("quest_first_friend", 1);
            saveAndTrack();
        }
    }

    private void setQuestAtLeastInternal(String questId, int val) {
        int cur = data.questProgress.getOrDefault(questId, 0);
        if (val > cur) {
            data.questProgress.put(questId, val);
        }
    }

    // --- ADMIN CONTROLS ---

    public synchronized void adminGiftCoins(int amount) {
        if (amount == 0) return;
        int next = Math.max(0, data.coins + amount);
        int delta = next - data.coins;
        data.coins = next;
        if (delta > 0) {
            data.totalEarned += delta;
            lastMessage = "[ADMIN] Gifted +" + delta + " JJN Coins! Balance: " + data.coins + " ◈";
        } else {
            lastMessage = "[ADMIN] Adjusted balance by " + delta + " ◈. Balance: " + data.coins + " ◈";
        }
        saveAndTrack();
    }

    public synchronized void adminSetCoins(int amount) {
        int clamped = Math.max(0, amount);
        if (clamped > data.coins) {
            data.totalEarned += (clamped - data.coins);
        }
        data.coins = clamped;
        lastMessage = "[ADMIN] Set balance to " + data.coins + " ◈.";
        saveAndTrack();
    }

    public synchronized void adminGrantItem(String itemId) {
        EconomyStore.CatalogItem item = EconomyStore.findItem(itemId);
        if (item == null) return;
        data.ownedItems.add(item.id());
        if (item.category() == EconomyStore.Category.EMOTE) {
            assignToFirstEmptyWheelSlot(item.id());
        } else {
            applyEquipInternal(item);
        }
        lastMessage = "[ADMIN] Granted & equipped \"" + item.name() + "\"!";
        saveAndTrack();
    }

    public synchronized void adminRevokeItem(String itemId) {
        if ("emote_wave".equals(itemId)) {
            lastMessage = "[ADMIN] Starter emote \"Friendly Wave\" cannot be removed.";
            notifyListeners();
            return;
        }
        EconomyStore.CatalogItem item = EconomyStore.findItem(itemId);
        if (item == null) return;
        data.ownedItems.remove(itemId);
        if (itemId.equals(data.equippedCapeId)) data.equippedCapeId = "";
        if (itemId.equals(data.equippedBackId)) data.equippedBackId = "";
        if (itemId.equals(data.equippedHatId)) data.equippedHatId = "";
        if (itemId.equals(data.equippedAuraId)) data.equippedAuraId = "";
        for (int i = 0; i < data.emoteWheel.size(); i++) {
            if (itemId.equals(data.emoteWheel.get(i))) {
                data.emoteWheel.set(i, "");
            }
        }
        lastMessage = "[ADMIN] Revoked \"" + item.name() + "\".";
        saveAndTrack();
    }

    public synchronized void adminUnlockAllItems() {
        for (EconomyStore.CatalogItem item : EconomyStore.CATALOG) {
            data.ownedItems.add(item.id());
            if (item.category() == EconomyStore.Category.EMOTE) {
                assignToFirstEmptyWheelSlot(item.id());
            }
        }
        lastMessage = "[ADMIN] Unlocked all " + EconomyStore.CATALOG.size() + " Shop cosmetics & emotes!";
        saveAndTrack();
    }

    public synchronized void adminResetToStarter() {
        data.ownedItems.clear();
        data.ownedItems.add("emote_wave");
        data.equippedCapeId = "";
        data.equippedBackId = "";
        data.equippedHatId = "";
        data.equippedAuraId = "";
        data.emoteWheel.clear();
        data.emoteWheel.add("emote_wave");
        while (data.emoteWheel.size() < 8) data.emoteWheel.add("");
        lastMessage = "[ADMIN] Reset owned items & equipped slots to starter.";
        saveAndTrack();
    }

    public synchronized void adminCompleteAllQuests() {
        for (EconomyStore.QuestDef q : EconomyStore.QUESTS) {
            data.questProgress.put(q.id(), q.target());
        }
        lastMessage = "[ADMIN] Completed progress on all Quests — ready to claim!";
        saveAndTrack();
    }

    public synchronized void adminResetQuests() {
        data.questProgress.clear();
        data.claimedQuests.clear();
        lastMessage = "[ADMIN] Reset all Quest progress & claims.";
        saveAndTrack();
    }

    public synchronized void adminSaveCreatorCode(EconomyStore.CreatorCodeDef def) {
        EconomyStore.saveCustomCreatorCode(def);
        lastMessage = "[ADMIN] Saved custom Creator Code [" + EconomyStore.normalizeCode(def.code()) + "]!";
        notifyListeners();
    }

    public synchronized boolean adminDeleteCreatorCode(String code) {
        boolean removed = EconomyStore.deleteCustomCreatorCode(code);
        if (removed) {
            if (EconomyStore.normalizeCode(code).equals(creatorState.activeCode)) {
                creatorState.activeCode = "";
                store.saveCreatorState(creatorState);
            }
            lastMessage = "[ADMIN] Deleted custom Creator Code [" + EconomyStore.normalizeCode(code) + "].";
            notifyListeners();
        }
        return removed;
    }
}
