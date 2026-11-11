package com.jjnclient.mods.impl;

import com.jjnclient.mods.Mod;

public class PingDisplayMod extends Mod {
    public PingDisplayMod() {
        super("ping", "Ping Display", "Server latency indicator", Category.HUD);
    }

    @Override public void onEnable() {}
    @Override public void onDisable() {}
    @Override public void onTick() {}
}
