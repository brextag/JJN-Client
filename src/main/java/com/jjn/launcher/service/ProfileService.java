package com.jjn.launcher.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jjn.launcher.LauncherSettings;
import com.jjn.launcher.model.ClientProfile;
import com.jjn.launcher.model.Instance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Manages saved JJN Client profiles in ~/.jjnclient/profiles.json and syncs them
 * with each instance's <gameDir>/config/jjnclient.properties + jjnclient-skin.png.
 */
public class ProfileService {

    private static final Path PROFILES_FILE = LauncherSettings.ROOT_DIR.resolve("profiles.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class ProfileStore {
        public String activeProfileId;
        public List<ClientProfile> profiles = new ArrayList<>();

        public ClientProfile findActive() {
            if (profiles.isEmpty()) {
                return null;
            }
            if (activeProfileId != null) {
                for (ClientProfile p : profiles) {
                    if (activeProfileId.equals(p.id)) {
                        return p;
                    }
                }
            }
            return profiles.get(0);
        }
    }

    public ProfileStore load() {
        ProfileStore store = null;
        if (Files.isRegularFile(PROFILES_FILE)) {
            try {
                String raw = Files.readString(PROFILES_FILE, StandardCharsets.UTF_8);
                store = GSON.fromJson(raw, ProfileStore.class);
            } catch (Exception ignored) {
            }
        }
        if (store == null || store.profiles == null || store.profiles.isEmpty()) {
            store = createDefaultStore();
            save(store);
        }
        if (store.findActive() != null) {
            store.activeProfileId = store.findActive().id;
        }
        return store;
    }

    public void save(ProfileStore store) {
        try {
            Files.createDirectories(PROFILES_FILE.getParent());
            Files.writeString(PROFILES_FILE, GSON.toJson(store), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static ProfileStore createDefaultStore() {
        ProfileStore store = new ProfileStore();

        ClientProfile def = new ClientProfile("default", "Default", "BALANCED");
        def.showFps = true;
        def.showCoords = true;
        def.showDirection = true;
        def.toggleSprint = true;
        def.showKeystrokes = false;
        def.hudAccentIndex = 0;

        ClientProfile pvp = new ClientProfile("pvp", "PvP Competitive", "PVP");
        pvp.showFps = true;
        pvp.fpsCompact = true;
        pvp.showCoords = true;
        pvp.showDirection = true;
        pvp.toggleSprint = true;
        pvp.showSprintHud = true;
        pvp.showPingServer = true;
        pvp.showKeystrokes = true;
        pvp.hudOpacity = 1; // Glass
        pvp.hudAccentIndex = 2; // Cyan

        ClientProfile survival = new ClientProfile("survival", "Minimal Survival", "SURVIVAL");
        survival.showFps = false;
        survival.showCoords = true;
        survival.showDirection = true;
        survival.showBiome = true;
        survival.toggleSprint = true;
        survival.showSprintHud = false;
        survival.hudOpacity = 1;
        survival.hudAccentIndex = 1; // Emerald

        store.profiles.add(def);
        store.profiles.add(pvp);
        store.profiles.add(survival);
        store.activeProfileId = def.id;
        return store;
    }

    public ClientProfile createNew(ProfileStore store, String name) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        ClientProfile active = store.findActive();
        ClientProfile created = active != null
                ? active.copyWith(id, name.trim())
                : new ClientProfile(id, name.trim(), "CUSTOM");
        created.tag = "CUSTOM";
        store.profiles.add(created);
        store.activeProfileId = created.id;
        save(store);
        return created;
    }

    /**
     * Writes all profile HUD/module & skin settings into the target instance's config.
     */
    public void applyToInstance(ClientProfile profile, Instance instance) throws IOException {
        if (profile == null || instance == null) {
            return;
        }
        Path configDir = instance.gameFolder().resolve("config");
        Files.createDirectories(configDir);
        Path propFile = configDir.resolve("jjnclient.properties");

        Properties props = new Properties();
        if (Files.isRegularFile(propFile)) {
            try (var in = Files.newInputStream(propFile)) {
                props.load(in);
            } catch (IOException ignored) {
            }
        }

        props.setProperty("profileName", profile.name == null ? "Default" : profile.name);
        props.setProperty("skinVariant", "slim".equalsIgnoreCase(profile.skinVariant) ? "slim" : "classic");
        props.setProperty("customTitleScreen", String.valueOf(profile.customTitleScreen));
        props.setProperty("titleScreenGrid", String.valueOf(profile.titleScreenGrid));
        props.setProperty("showTitlePreview", String.valueOf(profile.showTitlePreview));
        props.setProperty("showHud", String.valueOf(profile.showHud));
        props.setProperty("showFps", String.valueOf(profile.showFps));
        props.setProperty("fpsCompact", String.valueOf(profile.fpsCompact));
        props.setProperty("showCoords", String.valueOf(profile.showCoords));
        props.setProperty("showDirection", String.valueOf(profile.showDirection));
        props.setProperty("toggleSprint", String.valueOf(profile.toggleSprint));
        props.setProperty("showSprintHud", String.valueOf(profile.showSprintHud));
        props.setProperty("showPingServer", String.valueOf(profile.showPingServer));
        props.setProperty("showBiome", String.valueOf(profile.showBiome));
        props.setProperty("showKeystrokes", String.valueOf(profile.showKeystrokes));
        props.setProperty("hudCorner", String.valueOf(profile.hudCorner));
        props.setProperty("hudScale", String.valueOf(profile.hudScale));
        props.setProperty("hudOpacity", String.valueOf(profile.hudOpacity));
        props.setProperty("hudAccentIndex", String.valueOf(profile.hudAccentIndex));
        props.setProperty("hudTextShadow", String.valueOf(profile.hudTextShadow));

        syncLocalSkinFile(profile, configDir, props);
        syncLocalCapeFile(profile, configDir, props);

        try (var out = Files.newOutputStream(propFile)) {
            props.store(out, "JJN Client Profile: " + profile.name);
        }
    }

    /**
     * Light sync on game launch: updates active profile label + local skin & cape overrides
     * without overwriting HUD tweaks the player made in-game.
     */
    public void syncIdentityOnLaunch(ClientProfile profile, Instance instance) {
        if (profile == null || instance == null) {
            return;
        }
        try {
            Path configDir = instance.gameFolder().resolve("config");
            Files.createDirectories(configDir);
            Path propFile = configDir.resolve("jjnclient.properties");

            Properties props = new Properties();
            if (Files.isRegularFile(propFile)) {
                try (var in = Files.newInputStream(propFile)) {
                    props.load(in);
                }
            }
            props.setProperty("profileName", profile.name == null ? "Default" : profile.name);
            props.setProperty("skinVariant", "slim".equalsIgnoreCase(profile.skinVariant) ? "slim" : "classic");
            syncLocalSkinFile(profile, configDir, props);
            syncLocalCapeFile(profile, configDir, props);

            try (var out = Files.newOutputStream(propFile)) {
                props.store(out, "JJN Client Config");
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Imports current in-game HUD/module settings from an instance into the selected profile.
     */
    public boolean importFromInstance(ClientProfile profile, Instance instance) {
        if (profile == null || instance == null) {
            return false;
        }
        Path propFile = instance.gameFolder().resolve("config").resolve("jjnclient.properties");
        if (!Files.isRegularFile(propFile)) {
            return false;
        }
        Properties p = new Properties();
        try (var in = Files.newInputStream(propFile)) {
            p.load(in);
            profile.customTitleScreen = bool(p, "customTitleScreen", profile.customTitleScreen);
            profile.titleScreenGrid = bool(p, "titleScreenGrid", profile.titleScreenGrid);
            profile.showTitlePreview = bool(p, "showTitlePreview", profile.showTitlePreview);
            profile.showHud = bool(p, "showHud", profile.showHud);
            profile.showFps = bool(p, "showFps", profile.showFps);
            profile.fpsCompact = bool(p, "fpsCompact", profile.fpsCompact);
            profile.showCoords = bool(p, "showCoords", profile.showCoords);
            profile.showDirection = bool(p, "showDirection", profile.showDirection);
            profile.toggleSprint = bool(p, "toggleSprint", profile.toggleSprint);
            profile.showSprintHud = bool(p, "showSprintHud", profile.showSprintHud);
            profile.showPingServer = bool(p, "showPingServer", profile.showPingServer);
            profile.showBiome = bool(p, "showBiome", profile.showBiome);
            profile.showKeystrokes = bool(p, "showKeystrokes", profile.showKeystrokes);
            profile.hudCorner = integer(p, "hudCorner", profile.hudCorner);
            profile.hudScale = flt(p, "hudScale", profile.hudScale);
            profile.hudOpacity = integer(p, "hudOpacity", profile.hudOpacity);
            profile.hudAccentIndex = integer(p, "hudAccentIndex", profile.hudAccentIndex);
            profile.hudTextShadow = bool(p, "hudTextShadow", profile.hudTextShadow);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void syncLocalSkinFile(ClientProfile profile, Path configDir, Properties props) throws IOException {
        Path destSkin = configDir.resolve("jjnclient-skin.png");
        if (profile.skinFilePath != null && !profile.skinFilePath.isBlank()) {
            Path srcSkin = Path.of(profile.skinFilePath);
            if (Files.isRegularFile(srcSkin)) {
                Files.copy(srcSkin, destSkin, StandardCopyOption.REPLACE_EXISTING);
                props.setProperty("localSkinEnabled", "true");
                return;
            }
        }
        props.setProperty("localSkinEnabled", "false");
        Files.deleteIfExists(destSkin);
    }

    private static void syncLocalCapeFile(ClientProfile profile, Path configDir, Properties props) throws IOException {
        Path destCape = configDir.resolve("jjnclient-cape.png");
        if (profile.capeId == null || profile.capeId.isBlank()) {
            props.setProperty("localCapeEnabled", "false");
            Files.deleteIfExists(destCape);
            return;
        }
        Path srcCape = null;
        if (profile.capeFilePath != null && !profile.capeFilePath.isBlank()) {
            Path candidate = Path.of(profile.capeFilePath);
            if (Files.isRegularFile(candidate)) {
                srcCape = candidate;
            }
        }
        if (srcCape == null) {
            String cleanId = profile.capeId.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path cached = MinecraftSkinService.capesDir().resolve(cleanId + ".png");
            if (Files.isRegularFile(cached)) {
                srcCape = cached;
            }
        }
        if (srcCape == null && profile.capeUrl != null && !profile.capeUrl.isBlank()) {
            try {
                srcCape = new MinecraftSkinService().cacheCapeFile(profile.capeId, profile.capeUrl);
            } catch (Exception ignored) {
            }
        }
        if (srcCape != null && Files.isRegularFile(srcCape)) {
            Files.copy(srcCape, destCape, StandardCopyOption.REPLACE_EXISTING);
            props.setProperty("localCapeEnabled", "true");
            return;
        }
        props.setProperty("localCapeEnabled", "false");
        Files.deleteIfExists(destCape);
    }

    private static boolean bool(Properties p, String k, boolean def) {
        return Boolean.parseBoolean(p.getProperty(k, String.valueOf(def)));
    }

    private static int integer(Properties p, String k, int def) {
        try {
            return Integer.parseInt(p.getProperty(k, String.valueOf(def)));
        } catch (NumberFormatException ignored) {
            return def;
        }
    }

    private static float flt(Properties p, String k, float def) {
        try {
            return Float.parseFloat(p.getProperty(k, String.valueOf(def)));
        } catch (NumberFormatException ignored) {
            return def;
        }
    }
}
