package com.jjn.client.ui;

import com.jjn.client.JjnConfig;
import com.jjn.client.shop.JjnEconomyManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Quick [B] key Emote Wheel Picker for Minecraft 26.2.
 * Click any of the 8 wheel slots or press keys [1]-[8] to trigger an emote immediately.
 */
public class JjnEmoteWheelScreen extends Screen {

    private static final int[][] GRID_POS = {
            {0, 0}, // Slot 1: top-left
            {1, 0}, // Slot 2: top-center
            {2, 0}, // Slot 3: top-right
            {2, 1}, // Slot 4: mid-right
            {2, 2}, // Slot 5: bottom-right
            {1, 2}, // Slot 6: bottom-center
            {0, 2}, // Slot 7: bottom-left
            {0, 1}  // Slot 8: mid-left
    };

    private final Screen parent;

    public JjnEmoteWheelScreen(Screen parent) {
        super(Component.literal("JJN Emote Wheel"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        JjnEconomyManager mgr = JjnEconomyManager.get();
        mgr.reloadFromDisk();
        JjnEconomyManager.EconomyData data = mgr.snapshot();

        int pw = Math.min(358, this.width - 20);
        int ph = 216;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        this.addRenderableWidget(new JjnButton(
                px + pw - 56, py + 8, 46, 18,
                Component.literal("CLOSE"),
                btn -> this.onClose()
        ));

        int cellGap = 6;
        int cellW = (pw - 24 - cellGap * 2) / 3;
        int cellH = 34;
        int gridX = px + 12;
        int gridY = py + 34;

        for (int i = 0; i < 8; i++) {
            final int slot = i;
            String emoteId = i < data.emoteWheel.size() ? data.emoteWheel.get(i) : "";
            JjnEconomyManager.CatalogItem item = JjnEconomyManager.findItem(emoteId);
            boolean has = item != null && mgr.isOwned(emoteId);
            boolean playing = has && emoteId.equals(mgr.getActiveEmoteId());

            String label = "[" + (i + 1) + "] " + (has ? item.name() : "Empty");
            int col = GRID_POS[i][0];
            int row = GRID_POS[i][1];
            int bx = gridX + col * (cellW + cellGap);
            int by = gridY + row * (cellH + cellGap);

            JjnButton slotBtn = new JjnButton(
                    bx, by, cellW, cellH,
                    Component.literal(label),
                    btn -> triggerSlot(slot),
                    playing || has
            );
            slotBtn.active = has;
            this.addRenderableWidget(slotBtn);
        }

        // Center cell: Stop Emote
        this.addRenderableWidget(new JjnButton(
                gridX + cellW + cellGap, gridY + cellH + cellGap, cellW, cellH,
                Component.literal("STOP EMOTE"),
                btn -> {
                    mgr.stopEmote(this.minecraft);
                    this.onClose();
                }
        ));

        // Bottom controls
        int footY = py + ph - 32;
        int half = (pw - 30) / 2;
        JjnConfig cfg = JjnConfig.get();
        this.addRenderableWidget(new JjnButton(
                px + 12, footY, half, 22,
                Component.literal("Auto 3rd-Person: " + (cfg.emoteAutoThirdPerson ? "ON" : "OFF")),
                btn -> {
                    cfg.emoteAutoThirdPerson = !cfg.emoteAutoThirdPerson;
                    cfg.save();
                    this.rebuildWidgets();
                }
        ));

        this.addRenderableWidget(new JjnButton(
                px + 18 + half, footY, half, 22,
                Component.literal("Customize Wheel & Shop >"),
                btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new JjnModMenuScreen(this.parent, JjnModMenuScreen.Tab.EMOTES));
                    }
                },
                true
        ));
    }

    private void triggerSlot(int slot) {
        JjnEconomyManager mgr = JjnEconomyManager.get();
        JjnEconomyManager.EconomyData data = mgr.snapshot();
        if (slot < 0 || slot >= data.emoteWheel.size()) return;
        String emoteId = data.emoteWheel.get(slot);
        if (emoteId == null || emoteId.isBlank() || !mgr.isOwned(emoteId)) return;
        mgr.playEmote(this.minecraft, emoteId);
        this.onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_B || key == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_8) {
            triggerSlot(key - GLFW.GLFW_KEY_1);
            return true;
        }
        if (key >= GLFW.GLFW_KEY_KP_1 && key <= GLFW.GLFW_KEY_KP_8) {
            triggerSlot(key - GLFW.GLFW_KEY_KP_1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, this.width, this.height, 0x88101116, 0xAA15171E);

        int pw = Math.min(358, this.width - 20);
        int ph = 216;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        graphics.fill(px, py, px + pw, py + ph, 0xEC191B23);
        graphics.fill(px, py, px + pw, py + 2, 0xFFC7D2FE);
        graphics.outline(px, py, pw, ph, 0xFF353949);

        graphics.text(this.font, "EMOTE WHEEL", px + 12, py + 12, 0xFFFFFFFF, true);
        String hint = "Press [1-8] or Click  •  [B] Close";
        graphics.text(this.font, hint, px + 88, py + 12, 0xFFC7D2FE, false);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
