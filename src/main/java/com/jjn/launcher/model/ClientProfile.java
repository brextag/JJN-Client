package com.jjn.launcher.model;

/**
 * Saved JJN Client profile combining skin/cosmetic preferences and in-game HUD/module presets.
 */
public class ClientProfile {

    public String id;
    public String name;
    public String tag = "CUSTOM";

    // Skin & appearance
    public String skinFilePath = "";
    public String skinVariant = "classic"; // "classic" or "slim"
    public String capeId = "";
    public String capeUrl = "";
    public String capeFilePath = "";

    // Client modules & HUD settings (mirrors JjnConfig)
    public boolean customTitleScreen = true;
    public boolean titleScreenGrid = true;
    public boolean showTitlePreview = true;

    public boolean showHud = true;
    public boolean showFps = true;
    public boolean fpsCompact = false;
    public boolean showCoords = true;
    public boolean showDirection = true;
    public boolean toggleSprint = true;
    public boolean showSprintHud = true;
    public boolean showPingServer = false;
    public boolean showBiome = false;
    public boolean showKeystrokes = false;

    public int hudCorner = 0;       // 0=TL, 1=TR, 2=BL, 3=BR, 4=Custom
    public float hudScale = 1.0f;
    public int hudOpacity = 2;      // 0=Transparent, 1=Glass, 2=Solid
    public int hudAccentIndex = 0;  // 0=Indigo, 1=Emerald, 2=Cyan, 3=Rose, 4=Amber, 5=White
    public boolean hudTextShadow = true;

    public ClientProfile() {}

    public ClientProfile(String id, String name, String tag) {
        this.id = id;
        this.name = name;
        this.tag = tag;
    }

    public ClientProfile copyWith(String newId, String newName) {
        ClientProfile p = new ClientProfile(newId, newName, this.tag);
        p.skinFilePath = this.skinFilePath;
        p.skinVariant = this.skinVariant;
        p.capeId = this.capeId;
        p.capeUrl = this.capeUrl;
        p.capeFilePath = this.capeFilePath;
        p.customTitleScreen = this.customTitleScreen;
        p.titleScreenGrid = this.titleScreenGrid;
        p.showTitlePreview = this.showTitlePreview;
        p.showHud = this.showHud;
        p.showFps = this.showFps;
        p.fpsCompact = this.fpsCompact;
        p.showCoords = this.showCoords;
        p.showDirection = this.showDirection;
        p.toggleSprint = this.toggleSprint;
        p.showSprintHud = this.showSprintHud;
        p.showPingServer = this.showPingServer;
        p.showBiome = this.showBiome;
        p.showKeystrokes = this.showKeystrokes;
        p.hudCorner = this.hudCorner;
        p.hudScale = this.hudScale;
        p.hudOpacity = this.hudOpacity;
        p.hudAccentIndex = this.hudAccentIndex;
        p.hudTextShadow = this.hudTextShadow;
        return p;
    }

    @Override
    public String toString() {
        return name == null ? "Profile" : name;
    }
}
