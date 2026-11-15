package com.jjn.client.ui;

import com.jjn.client.JjnConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Interactive drag-and-drop screen to position the JJN Client HUD (1.21.4).
 */
public class JjnHudEditorScreen extends Screen {

    private final Screen parent;
    private JjnHudRenderer.Bounds lastBounds = new JjnHudRenderer.Bounds(8, 8, 120, 40);
    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public JjnHudEditorScreen(Screen parent) {
        super(Text.literal("Edit HUD Position"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int btnW = 110;
        int gap = 8;
        int totalW = btnW * 2 + gap;
        int startX = this.width / 2 - totalW / 2;
        int y = this.height - 36;

        this.addDrawableChild(new JjnButton(
                startX, y, btnW, 24,
                Text.literal("RESET TOP-LEFT"),
                btn -> {
                    JjnConfig cfg = JjnConfig.get();
                    cfg.hudCorner = JjnConfig.CORNER_TOP_LEFT;
                    cfg.hudCustomX = 8;
                    cfg.hudCustomY = 8;
                    cfg.save();
                }
        ));

        this.addDrawableChild(new JjnButton(
                startX + btnW + gap, y, btnW, 24,
                Text.literal("DONE"),
                btn -> this.close(),
                true
        ));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && lastBounds.contains(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = (int) Math.round(mouseX) - lastBounds.x();
            dragOffsetY = (int) Math.round(mouseY) - lastBounds.y();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging && button == 0) {
            JjnConfig cfg = JjnConfig.get();
            int newX = (int) Math.round(mouseX) - dragOffsetX;
            int newY = (int) Math.round(mouseY) - dragOffsetY;
            int maxX = Math.max(0, this.width - Math.max(20, lastBounds.width()));
            int maxY = Math.max(0, this.height - Math.max(20, lastBounds.height()));
            cfg.hudCorner = JjnConfig.CORNER_CUSTOM;
            cfg.hudCustomX = Math.max(0, Math.min(maxX, newX));
            cfg.hudCustomY = Math.max(0, Math.min(maxY, newY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            JjnConfig.get().save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        JjnConfig.get().save();
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, 0x88101116, 0x9915171E);

        context.fill(this.width / 2, 0, this.width / 2 + 1, this.height, 0x26C7D2FE);
        context.fill(0, this.height / 2, this.width, this.height / 2 + 1, 0x26C7D2FE);

        this.lastBounds = JjnHudRenderer.renderHud(context, this.client, true);
        if (this.lastBounds.width() > 0 && this.lastBounds.height() > 0) {
            int outlineColor = dragging ? 0xFFFFFFFF : JjnConfig.get().accentColor();
            context.drawBorder(
                    this.lastBounds.x() - 2,
                    this.lastBounds.y() - 2,
                    this.lastBounds.width() + 4,
                    this.lastBounds.height() + 4,
                    outlineColor
            );
        }

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                "Drag the JJN HUD box to move it  •  " + JjnConfig.get().cornerName(),
                this.width / 2,
                16,
                0xFFE5E7EB
        );

        super.render(context, mouseX, mouseY, delta);
    }
}
