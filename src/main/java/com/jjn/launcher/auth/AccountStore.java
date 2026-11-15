package com.jjn.launcher.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jjn.launcher.LauncherSettings;
import com.jjn.launcher.MicrosoftAuthService;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Crypt32Util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Persists signed-in Microsoft/Minecraft accounts in ~/.jjnclient/accounts.json.
 * On Windows, refresh tokens are encrypted with the current user's Windows DPAPI key.
 */
public class AccountStore {

    private static final Path FILE = LauncherSettings.ROOT_DIR.resolve("accounts.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class SavedAccount {
        public String uuid;
        public String playerName;
        public String skinUrl;
        public String refreshToken;
        public long lastLoginEpochMs;
    }

    public static class StoreState {
        public String activeUuid;
        public final List<SavedAccount> accounts = new ArrayList<>();

        public SavedAccount findActive() {
            if (accounts.isEmpty()) {
                return null;
            }
            if (activeUuid != null) {
                for (SavedAccount account : accounts) {
                    if (activeUuid.equals(account.uuid)) {
                        return account;
                    }
                }
            }
            return accounts.get(0);
        }
    }

    public StoreState load() {
        StoreState state = new StoreState();

        if (!Files.isRegularFile(FILE)) {
            return state;
        }

        try {
            String raw = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();

            if (root.has("activeUuid") && !root.get("activeUuid").isJsonNull()) {
                state.activeUuid = root.get("activeUuid").getAsString();
            }

            if (root.has("accounts") && root.get("accounts").isJsonArray()) {
                JsonArray array = root.getAsJsonArray("accounts");
                for (JsonElement el : array) {
                    JsonObject obj = el.getAsJsonObject();
                    SavedAccount acc = new SavedAccount();
                    acc.uuid = getString(obj, "uuid");
                    acc.playerName = getString(obj, "playerName");
                    acc.skinUrl = getString(obj, "skinUrl");
                    acc.lastLoginEpochMs = obj.has("lastLoginEpochMs")
                            ? obj.get("lastLoginEpochMs").getAsLong()
                            : 0L;

                    String encrypted = getString(obj, "protectedToken");
                    if (encrypted != null && !encrypted.isBlank()) {
                        acc.refreshToken = unprotectToken(encrypted);
                    } else {
                        acc.refreshToken = getString(obj, "refreshToken");
                    }

                    if (acc.uuid != null && acc.playerName != null
                            && acc.refreshToken != null && !acc.refreshToken.isBlank()) {
                        state.accounts.add(acc);
                    }
                }
            }
        } catch (Exception ignored) {
            // Corrupted store returns empty state cleanly.
        }

        return state;
    }

    public void save(StoreState state) {
        try {
            Files.createDirectories(FILE.getParent());

            JsonObject root = new JsonObject();
            if (state.activeUuid != null) {
                root.addProperty("activeUuid", state.activeUuid);
            }

            JsonArray array = new JsonArray();
            for (SavedAccount acc : state.accounts) {
                if (acc.uuid == null || acc.refreshToken == null || acc.refreshToken.isBlank()) {
                    continue;
                }
                JsonObject obj = new JsonObject();
                obj.addProperty("uuid", acc.uuid);
                obj.addProperty("playerName", acc.playerName);
                if (acc.skinUrl != null) {
                    obj.addProperty("skinUrl", acc.skinUrl);
                }
                obj.addProperty("lastLoginEpochMs", acc.lastLoginEpochMs);

                String encrypted = protectToken(acc.refreshToken);
                if (encrypted != null) {
                    obj.addProperty("protectedToken", encrypted);
                } else {
                    obj.addProperty("refreshToken", acc.refreshToken);
                }
                array.add(obj);
            }

            root.add("accounts", array);
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Non-fatal if saving fails.
        }
    }

    public StoreState upsertFromAuth(StoreState state, MicrosoftAuthService.AuthResult result) {
        if (result == null || result.minecraftBlocked
                || result.playerUuid == null || result.refreshToken == null) {
            return state;
        }

        SavedAccount target = null;
        for (SavedAccount existing : state.accounts) {
            if (result.playerUuid.equals(existing.uuid)) {
                target = existing;
                break;
            }
        }

        if (target == null) {
            target = new SavedAccount();
            target.uuid = result.playerUuid;
            state.accounts.add(target);
        }

        target.playerName = result.playerName;
        target.skinUrl = result.skinUrl;
        target.refreshToken = result.refreshToken;
        target.lastLoginEpochMs = System.currentTimeMillis();
        state.activeUuid = target.uuid;

        save(state);
        return state;
    }

    public void removeAccount(StoreState state, String uuid) {
        if (uuid == null) {
            return;
        }
        state.accounts.removeIf(acc -> uuid.equals(acc.uuid));
        if (uuid.equals(state.activeUuid)) {
            state.activeUuid = state.accounts.isEmpty() ? null : state.accounts.get(0).uuid;
        }
        save(state);
    }

    private String protectToken(String plainText) {
        try {
            if (Platform.isWindows()) {
                byte[] bytes = plainText.getBytes(StandardCharsets.UTF_8);
                byte[] protectedBytes = Crypt32Util.cryptProtectData(bytes);
                return "dpapi:" + Base64.getEncoder().encodeToString(protectedBytes);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String unprotectToken(String stored) {
        try {
            if (stored.startsWith("dpapi:") && Platform.isWindows()) {
                byte[] encrypted = Base64.getDecoder().decode(stored.substring("dpapi:".length()));
                byte[] plain = Crypt32Util.cryptUnprotectData(encrypted);
                return new String(plain, StandardCharsets.UTF_8);
            }
        } catch (Throwable ignored) {
            return null;
        }
        return stored;
    }

    private static String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString()
                : null;
    }
}
