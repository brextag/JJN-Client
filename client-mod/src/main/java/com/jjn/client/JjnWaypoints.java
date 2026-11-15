package com.jjn.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Persists named in-world waypoints per singleplayer world / multiplayer server
 * in <gameDir>/config/jjnclient-waypoints.json.
 */
public final class JjnWaypoints {

    public static final int[] COLORS = {
            0xFFC7D2FE,
            0xFF86EFAC,
            0xFF67E8F9,
            0xFFFCA5A5,
            0xFFFCD34D,
            0xFFD8B4FE,
            0xFFFFFFFF
    };

    public static final String[] COLOR_NAMES = {
            "Indigo", "Emerald", "Cyan", "Rose", "Amber", "Violet", "White"
    };

    public static final class Waypoint {
        public String id;
        public String name;
        public String worldKey;
        public String dimension;
        public int x;
        public int y;
        public int z;
        public int colorIndex;
        public boolean enabled = true;

        public int argb() {
            int idx = Math.max(0, Math.min(COLORS.length - 1, colorIndex));
            return COLORS[idx];
        }

        public String colorName() {
            int idx = Math.max(0, Math.min(COLOR_NAMES.length - 1, colorIndex));
            return COLOR_NAMES[idx];
        }

        public String dimensionDisplayName() {
            if (dimension == null || dimension.isBlank()) return "Overworld";
            return switch (dimension) {
                case "minecraft:overworld" -> "Overworld";
                case "minecraft:the_nether" -> "Nether";
                case "minecraft:the_end" -> "The End";
                default -> dimension.contains(":") ? dimension.substring(dimension.indexOf(':') + 1) : dimension;
            };
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Waypoint>>() {}.getType();
    private static final JjnWaypoints INSTANCE = new JjnWaypoints();

    private final List<Waypoint> waypoints = new ArrayList<>();
    private boolean loaded = false;

    private JjnWaypoints() {}

    public static JjnWaypoints get() {
        if (!INSTANCE.loaded) {
            INSTANCE.load();
        }
        return INSTANCE;
    }

    private static Path storeFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("jjnclient-waypoints.json");
    }

    public synchronized void load() {
        loaded = true;
        waypoints.clear();
        Path file = storeFile();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            List<Waypoint> parsed = GSON.fromJson(json, LIST_TYPE);
            if (parsed != null) {
                for (Waypoint wp : parsed) {
                    if (wp == null) continue;
                    if (wp.id == null || wp.id.isBlank()) wp.id = UUID.randomUUID().toString();
                    if (wp.name == null || wp.name.isBlank()) wp.name = "Waypoint";
                    if (wp.worldKey == null || wp.worldKey.isBlank()) wp.worldKey = "global";
                    if (wp.dimension == null || wp.dimension.isBlank()) wp.dimension = "minecraft:overworld";
                    wp.colorIndex = Math.max(0, Math.min(COLORS.length - 1, wp.colorIndex));
                    waypoints.add(wp);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized void save() {
        Path file = storeFile();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(waypoints, LIST_TYPE), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public synchronized List<Waypoint> waypointsFor(String worldKey, String dimensionOrNull) {
        List<Waypoint> out = new ArrayList<>();
        String wk = worldKey == null || worldKey.isBlank() ? "global" : worldKey;
        for (Waypoint wp : waypoints) {
            if (!wk.equals(wp.worldKey)) continue;
            if (dimensionOrNull != null && !dimensionOrNull.equals(wp.dimension)) continue;
            out.add(wp);
        }
        return out;
    }

    public synchronized void addOrUpdate(Waypoint wp) {
        if (wp == null) return;
        if (wp.id == null || wp.id.isBlank()) {
            wp.id = UUID.randomUUID().toString();
        }
        if (wp.name == null || wp.name.isBlank()) {
            wp.name = nextDefaultName(wp.worldKey);
        }
        wp.name = wp.name.trim();
        wp.colorIndex = Math.max(0, Math.min(COLORS.length - 1, wp.colorIndex));
        for (int i = 0; i < waypoints.size(); i++) {
            if (wp.id.equals(waypoints.get(i).id)) {
                waypoints.set(i, wp);
                save();
                return;
            }
        }
        waypoints.add(wp);
        save();
    }

    public synchronized void remove(String id) {
        if (id == null) return;
        if (waypoints.removeIf(w -> id.equals(w.id))) {
            save();
        }
    }

    public synchronized String nextDefaultName(String worldKey) {
        List<Waypoint> existing = waypointsFor(worldKey, null);
        for (int n = 1; n < 1000; n++) {
            String candidate = "Waypoint " + n;
            boolean used = false;
            for (Waypoint w : existing) {
                if (candidate.equalsIgnoreCase(w.name)) {
                    used = true;
                    break;
                }
            }
            if (!used) return candidate;
        }
        return "Waypoint";
    }

    public static String currentWorldKey(MinecraftClient client) {
        if (client == null) return "global";
        try {
            IntegratedServer srv = client.getServer();
            if (srv != null) {
                Path root = srv.getSavePath(WorldSavePath.ROOT).normalize();
                Path folder = root.getFileName();
                if (folder != null) {
                    return "sp:" + folder.toString();
                }
            }
        } catch (Exception ignored) {
        }
        try {
            ServerInfo server = client.getCurrentServerEntry();
            if (server != null && server.address != null && !server.address.isBlank()) {
                return "mp:" + server.address.trim().toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
        }
        return "global";
    }

    public static String currentDimension(MinecraftClient client) {
        if (client != null && client.world != null) {
            return client.world.getRegistryKey().getValue().toString();
        }
        return "minecraft:overworld";
    }
}
