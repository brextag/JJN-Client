package com.jjn.client.ui;

import com.jjn.client.JjnClientMod;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;

/**
 * Discovers installed Fabric mods and opens their in-game config tabs/screens when available.
 */
public final class ModBridge {

    public record ModEntry(
            String id,
            String name,
            String version,
            String description,
            String authors,
            String homepageUrl,
            String issuesUrl,
            List<String> dependencies,
            boolean internal,
            boolean hasConfigTab
    ) {}

    private ModBridge() {}

    public static List<ModEntry> loadInstalledMods(boolean includeInternal) {
        List<ModEntry> out = new ArrayList<>();
        boolean modMenuLoaded = FabricLoader.getInstance().isModLoaded("modmenu");

        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            ModMetadata meta = container.getMetadata();
            String id = meta.getId();
            boolean internal = isInternalMod(id, meta);
            if (!includeInternal && internal) {
                continue;
            }

            String name = meta.getName() == null || meta.getName().isBlank() ? id : meta.getName();
            String version = meta.getVersion() != null ? meta.getVersion().getFriendlyString() : "?";
            String desc = meta.getDescription() == null ? "" : meta.getDescription().replace('\n', ' ').trim();

            List<String> authorNames = new ArrayList<>();
            for (Person p : meta.getAuthors()) {
                if (p.getName() != null && !p.getName().isBlank()) {
                    authorNames.add(p.getName());
                }
            }
            String authors = authorNames.isEmpty() ? "Unknown" : String.join(", ", authorNames);

            String homepage = meta.getContact().get("homepage")
                    .or(() -> meta.getContact().get("sources"))
                    .orElse("");
            String issues = meta.getContact().get("issues").orElse("");

            List<String> deps = new ArrayList<>();
            for (ModDependency dep : meta.getDependencies()) {
                if (dep.getKind() == ModDependency.Kind.DEPENDS) {
                    deps.add(dep.getModId());
                }
            }

            boolean hasConfig = canOpenConfigTab(id, modMenuLoaded);
            out.add(new ModEntry(id, name, version, desc, authors, homepage, issues, deps, internal, hasConfig));
        }

        out.sort((a, b) -> {
            if (a.id().equals("jjnclient")) return -1;
            if (b.id().equals("jjnclient")) return 1;
            if (a.hasConfigTab() != b.hasConfigTab()) return a.hasConfigTab() ? -1 : 1;
            return a.name().compareToIgnoreCase(b.name());
        });
        return out;
    }

    private static boolean isInternalMod(String id, ModMetadata meta) {
        if ("minecraft".equals(id) || "java".equals(id) || "fabricloader".equals(id)
                || "mixinextras".equals(id) || id.startsWith("fabric-")) {
            return true;
        }
        return meta.getCustomValue("fabric-api:module-lifecycle") != null;
    }

    public static boolean canOpenConfigTab(String modId, boolean modMenuLoaded) {
        if ("jjnclient".equals(modId)) {
            return true;
        }
        if (modMenuLoaded) {
            try {
                Class<?> modMenuCls = Class.forName("com.terraformersmc.modmenu.ModMenu");
                Method hasScreen = modMenuCls.getMethod("hasConfigScreen", String.class);
                Object res = hasScreen.invoke(null, modId);
                if (res instanceof Boolean b && b) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        if ("sodium".equals(modId)) {
            return classExists("net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI")
                    || classExists("me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI");
        }
        if ("iris".equals(modId)) {
            return classExists("net.irisshaders.iris.gui.screen.ShaderPackScreen")
                    || classExists("net.coderbot.iris.gui.screen.ShaderPackScreen");
        }
        if ("modmenu".equals(modId)) {
            return classExists("com.terraformersmc.modmenu.gui.ModsScreen");
        }
        return false;
    }

    public static boolean openConfigTab(Minecraft mc, String modId, Screen parent) {
        if (mc == null) {
            return false;
        }
        if ("jjnclient".equals(modId)) {
            mc.gui.setScreen(new JjnModMenuScreen(parent, JjnModMenuScreen.Tab.MODULES));
            return true;
        }

        if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            try {
                Class<?> modMenuCls = Class.forName("com.terraformersmc.modmenu.ModMenu");
                Method getScreen = modMenuCls.getMethod("getConfigScreen", String.class, Screen.class);
                Object screen = getScreen.invoke(null, modId, parent);
                if (screen instanceof Screen s) {
                    mc.gui.setScreen(s);
                    return true;
                }
            } catch (Throwable t) {
                JjnClientMod.LOGGER.warn("Could not open ModMenu config screen for {}", modId, t);
            }
        }

        if ("sodium".equals(modId)) {
            Screen s = tryInvokeCreateScreen("net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI", parent);
            if (s == null) {
                s = tryInvokeCreateScreen("me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI", parent);
            }
            if (s != null) {
                mc.gui.setScreen(s);
                return true;
            }
        }

        if ("iris".equals(modId)) {
            Screen s = tryConstructScreen("net.irisshaders.iris.gui.screen.ShaderPackScreen", parent);
            if (s == null) {
                s = tryConstructScreen("net.coderbot.iris.gui.screen.ShaderPackScreen", parent);
            }
            if (s != null) {
                mc.gui.setScreen(s);
                return true;
            }
        }

        if ("modmenu".equals(modId)) {
            Screen s = tryConstructScreen("com.terraformersmc.modmenu.gui.ModsScreen", parent);
            if (s != null) {
                mc.gui.setScreen(s);
                return true;
            }
        }

        return false;
    }

    public static void openUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            Util.getPlatform().openUri(url);
        } catch (Exception ignored) {
        }
    }

    public static void openModsFolder() {
        try {
            Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
            Util.getPlatform().openPath(modsDir);
        } catch (Exception ignored) {
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, ModBridge.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Screen tryInvokeCreateScreen(String className, Screen parent) {
        try {
            Class<?> cls = Class.forName(className);
            Method m = cls.getMethod("createScreen", Screen.class);
            Object res = m.invoke(null, parent);
            if (res instanceof Screen s) {
                return s;
            }
        } catch (Throwable ignored) {
        }
        return tryConstructScreen(className, parent);
    }

    private static Screen tryConstructScreen(String className, Screen parent) {
        try {
            Class<?> cls = Class.forName(className);
            for (Constructor<?> ctor : cls.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 1 && params[0].isAssignableFrom(Screen.class)) {
                    Object res = ctor.newInstance(parent);
                    if (res instanceof Screen s) {
                        return s;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
