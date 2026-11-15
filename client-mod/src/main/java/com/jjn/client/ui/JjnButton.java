package com.jjn.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

/**
 * Custom JJN slate-gray / #C7D2FE button matching the launcher's visual identity.
 */
public class JjnButton extends ButtonWidget {

    private final boolean primary;

    public JjnButton(int x, int y, int width, int height, Text message, PressAction onPress) {
        this(x, y, width, height, message, onPress, false);
    }

    public JjnButton(int x, int y, int width, int height, Text message, PressAction onPress, boolean primary) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.primary = primary;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x1 = this.getX();
        int y1 = this.getY();
        int x2 = x1 + this.getWidth();
        int y2 = y1 + this.getHeight();

        boolean hovered = this.isHovered() && this.active;
        boolean lit = this.primary && this.active;

        int bgColor;
        int borderColor;
        int textColor;

        if (lit) {
            bgColor = hovered ? 0xFFDBE3FF : 0xFFC7D2FE;
            borderColor = hovered ? 0xFFEEF2FF : 0xFFC7D2FE;
            textColor = 0xFF171923;
        } else if (this.primary) {
            bgColor = 0xFF232735;
            borderColor = 0xFF353949;
            textColor = 0xFF666B7A;
        } else {
            bgColor = hovered ? 0xFF292D3B : 0xDD191B23;
            borderColor = hovered ? 0xFFC7D2FE : 0xFF353949;
            textColor = this.active ? (hovered ? 0xFFFFFFFF : 0xFFE5E7EB) : 0xFF666B7A;
        }

        context.fill(x1, y1, x2, y2, bgColor);
        drawBorder(context, x1, y1, x2, y2, borderColor);

        if (!lit && hovered) {
            context.fill(x1, y1, x1 + 3, y2, 0xFFC7D2FE);
        }

        var textRenderer = MinecraftClient.getInstance().textRenderer;
        OrderedText ordered = this.getMessage().asOrderedText();
        int textW = textRenderer.getWidth(ordered);
        int textX = x1 + (this.getWidth() - textW) / 2;
        int textY = y1 + (this.getHeight() - textRenderer.fontHeight) / 2 + 1;

        // Draw shadowless dark text on bright #C7D2FE buttons so text never looks doubled/glitchy
        boolean useScissor = textW > this.getWidth() - 6 && this.getWidth() > 8;
        if (useScissor) {
            context.enableScissor(x1 + 3, y1 + 1, x2 - 3, y2 - 1);
        }
        context.drawText(textRenderer, ordered, textX, textY, textColor, !lit);
        if (useScissor) {
            context.disableScissor();
        }
    }

    private static void drawBorder(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y1 + 1, color);
        context.fill(x1, y2 - 1, x2, y2, color);
        context.fill(x1, y1, x1 + 1, y2, color);
        context.fill(x2 - 1, y1, x2, y2, color);
    }
}
