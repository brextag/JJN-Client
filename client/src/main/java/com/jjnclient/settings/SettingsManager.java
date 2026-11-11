package com.jjnclient.settings;

import java.io.File;

public class SettingsManager {
    private final File configFile = new File("config/jjn-client.json");

    public void load() {
        // Load persisted settings from disk
    }

    public void save() {
        // Persist current settings
    }
}
