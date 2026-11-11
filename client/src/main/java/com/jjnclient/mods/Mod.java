package com.jjnclient.mods;

public abstract class Mod {
    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private boolean enabled;

    public Mod(String id, String name, String description, Category category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = false;
    }

    public abstract void onEnable();
    public abstract void onDisable();
    public abstract void onTick();

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) onEnable();
        else onDisable();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public boolean isEnabled() { return enabled; }

    public enum Category {
        MOVEMENT, VISUAL, UTILITY, HUD
    }
}
