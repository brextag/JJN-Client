package com.jjn.client.ui;

import com.jjn.client.JjnConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Interactive drag-and-drop screen to place the JJN Client HUD anywhere on screen.
 */
public class JjnHudEditorScreen extends Screen {

    private final Screen parent;
    private JjnHudRenderer.Bounds lastBounds = new JjnHudRenderer.Bounds(8, 8, 120, 40);
    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public JjnHudEditorScreen(Screen parent) {
        super(Component.literal("Edit HUD Position"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int btnW = 110;
        int gap = 8;
        int totalW = btnW * 2 + gap;
        int startX = this.width / 2 - totalW / 2;
        int y = this.height - 36;

        this.addRenderableWidget(new JjnButton(
                startX, y, btnW, 24,
                Component.literal("RESET TOP-LEFT"),
                btn -> {
                    JjnConfig cfg = JjnConfig.get();
                    cfg.hudCorner = JjnConfig.CORNER_TOP_LEFT;
                    cfg.hudCustomX = 8;
                    cfg.hudCustomY = 8;
                    cfg.save();
                }
        ));

        this.addRenderableWidget(new JjnButton(
                startX + btnW + gap, y, btnW, 24,
                Component.literal("DONE"),
                btn -> this.onClose(),
                true
        ));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && lastBounds.contains(event.x(), event.y())) {
            dragging = true;
            dragOffsetX = (int) Math.round(event.x()) - lastBounds.x();
            dragOffsetY = (int) Math.round(event.y()) - lastBounds.y();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging && event.button() == 0) {
            JjnConfig cfg = JjnConfig.get();
            int newX = (int) Math.round(event.x()) - dragOffsetX;
            int newY = (int) Math.round(event.y()) - dragOffsetY;
            int maxX = Math.max(0, this.width - Math.max(20, lastBounds.width()));
            int maxY = Math.max(0, this.height - Math.max(20, lastBounds.height()));
            cfg.hudCorner = JjnConfig.CORNER_CUSTOM;
            cfg.hudCustomX = Math.max(0, Math.min(maxX, newX));
            cfg.hudCustomY = Math.max(0, Math.min(maxY, newY));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            JjnConfig.get().save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        JjnConfig.get().save();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, this.width, this.height, 0x88101116, 0x9915171E);

        // Subtle alignment center guides
        graphics.fill(this.width / 2, 0, this.width / 2 + 1, this.height, 0x26C7D2FE);
        graphics.fill(0, this.height / 2, this.width, this.height / 2 + 1, 0x26C7D2FE);

        this.lastBounds = JjnHudRenderer.renderHud(graphics, this.minecraft, true);
        if (this.lastBounds.width() > 0 && this.lastBounds.height() > 0) {
            int outlineColor = dragging ? 0xFFFFFFFF : JjnConfig.get().accentColor();
            graphics.outline(
                    this.lastBounds.x() - 2,
                    this.lastBounds.y() - 2,
                    this.lastBounds.width() + 4,
                    this.lastBounds.height() + 4,
                    outlineColor
            );
        }

        graphics.centeredText(
                this.font,
                "Drag the JJN HUD box to move it  •  " + JjnConfig.get().cornerName(),
                this.width / 2,
                16,
                0xFFE5E7EB
        );

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
