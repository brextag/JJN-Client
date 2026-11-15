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
        if (data.coins < item.price()) {
            int need = item.price() - data.coins;
            lastMessage = "Need " + need + " more JJN Coins for " + item.name() + " — complete Quests to earn Coins!";
            notifyListeners();
            return false;
        }
        data.coins -= item.price();
        data.ownedItems.add(itemId);
        if (item.category() == EconomyStore.Category.EMOTE) {
            assignToFirstEmptyWheelSlot(itemId);
        } else {
            applyEquipInternal(item);
        }
        lastMessage = "Unlocked " + item.name() + "!";
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
}
