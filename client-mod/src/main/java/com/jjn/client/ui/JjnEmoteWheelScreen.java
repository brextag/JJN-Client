package com.jjn.client.ui;

import com.jjn.client.JjnConfig;
import com.jjn.client.shop.JjnEconomyManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Quick [B] key Emote Wheel Picker for Minecraft 1.21.4.
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
        super(Text.literal("JJN Emote Wheel"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        JjnEconomyManager mgr = JjnEconomyManager.get();
        mgr.reloadFromDisk();
        JjnEconomyManager.EconomyData data = mgr.snapshot();

        int pw = Math.min(358, this.width - 20);
        int ph = 216;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        this.addDrawableChild(new JjnButton(
                px + pw - 56, py + 8, 46, 18,
                Text.literal("CLOSE"),
                btn -> this.close()
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
                    Text.literal(label),
                    btn -> triggerSlot(slot),
                    playing || has
            );
            slotBtn.active = has;
            this.addDrawableChild(slotBtn);
        }

        // Center cell: Stop Emote
        this.addDrawableChild(new JjnButton(
                gridX + cellW + cellGap, gridY + cellH + cellGap, cellW, cellH,
                Text.literal("STOP EMOTE"),
                btn -> {
                    mgr.stopEmote(this.client);
                    this.close();
                }
        ));

        // Bottom controls
        int footY = py + ph - 32;
        int half = (pw - 30) / 2;
        JjnConfig cfg = JjnConfig.get();
        this.addDrawableChild(new JjnButton(
                px + 12, footY, half, 22,
                Text.literal("Auto 3rd-Person: " + (cfg.emoteAutoThirdPerson ? "ON" : "OFF")),
                btn -> {
                    cfg.emoteAutoThirdPerson = !cfg.emoteAutoThirdPerson;
                    cfg.save();
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new JjnButton(
                px + 18 + half, footY, half, 22,
                Text.literal("Customize Wheel & Shop >"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new JjnModMenuScreen(this.parent, JjnModMenuScreen.Tab.EMOTES));
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
        mgr.playEmote(this.client, emoteId);
        this.close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_B || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_8) {
            triggerSlot(keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_8) {
            triggerSlot(keyCode - GLFW.GLFW_KEY_KP_1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, 0x88101116, 0xAA15171E);

        int pw = Math.min(358, this.width - 20);
        int ph = 216;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        context.fill(px, py, px + pw, py + ph, 0xEC191B23);
        context.fill(px, py, px + pw, py + 2, 0xFFC7D2FE);
        context.drawBorder(px, py, pw, ph, 0xFF353949);

        context.drawText(this.textRenderer, "EMOTE WHEEL", px + 12, py + 12, 0xFFFFFFFF, true);
        String hint = "Press [1-8] or Click  •  [B] Close";
        context.drawText(this.textRenderer, hint, px + 88, py + 12, 0xFFC7D2FE, false);

        super.render(context, mouseX, mouseY, delta);
    }
}
