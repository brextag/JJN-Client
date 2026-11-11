package com.jjnclient;

import com.jjnclient.mods.ModManager;
import com.jjnclient.hud.HudManager;
import com.jjnclient.settings.SettingsManager;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JJNClient implements ClientModInitializer {
    public static final String MOD_ID = "jjn-client";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static JJNClient instance;

    private ModManager modManager;
    private HudManager hudManager;
    private SettingsManager settingsManager;

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("Initializing JJN Client...");

        settingsManager = new SettingsManager();
        settingsManager.load();

        modManager = new ModManager();
        modManager.init();

        hudManager = new HudManager();
        hudManager.init();

        LOGGER.info("JJN Client initialized successfully.");
    }

    public static JJNClient getInstance() {
        return instance;
    }

    public ModManager getModManager() {
        return modManager;
    }

    public HudManager getHudManager() {
        return hudManager;
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }
}
