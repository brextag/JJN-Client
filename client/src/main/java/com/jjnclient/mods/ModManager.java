package com.jjnclient.mods;

import com.jjnclient.mods.impl.*;
import java.util.ArrayList;
import java.util.List;

public class ModManager {
    private final List<Mod> mods = new ArrayList<>();

    public void init() {
        // Register all mods
        register(new ToggleSprintMod());
        register(new ZoomMod());
        register(new FreelookMod());
        register(new KeystrokesMod());
        register(new CpsCounterMod());
        register(new FpsDisplayMod());
        register(new ArmorHudMod());
        register(new CoordinatesMod());
        register(new PingDisplayMod());
        register(new CustomCrosshairMod());
        // Additional mods can be registered here

        mods.forEach(Mod::onEnable);
    }

    private void register(Mod mod) {
        mods.add(mod);
    }

    public List<Mod> getMods() {
        return mods;
    }

    public Mod getMod(String id) {
        return mods.stream()
                .filter(m -> m.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}
