package com.jjnclient.mods.impl;

import com.jjnclient.mods.Mod;

public class ToggleSprintMod extends Mod {
    public ToggleSprintMod() {
        super("toggle-sprint", "Toggle Sprint", "Keep sprinting without holding the key", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        // Implementation: listen for sprint key and keep sprint state
    }

    @Override
    public void onDisable() {}

    @Override
    public void onTick() {
        // Apply sprint if enabled
    }
}
