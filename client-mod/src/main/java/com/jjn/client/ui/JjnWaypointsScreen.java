package com.jjn.client.ui;

import com.jjn.client.JjnConfig;
import com.jjn.client.JjnWaypoints;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * In-game Waypoints Manager screen for Minecraft 1.21.4:
 * add named waypoints, toggle visibility, edit name/coords/color, or delete.
 */
public class JjnWaypointsScreen extends Screen {

    private final Screen parent;
    private boolean currentDimOnly = true;
    private int scrollOffset = 0;
    private List<JjnWaypoints.Waypoint> visibleList = List.of();

    private static final int ROWS_PER_PAGE = 4;

    public JjnWaypointsScreen(Screen parent) {
        super(Text.literal("JJN Waypoints"));
        this.parent = parent;
    }

    private int panelW() {
        return Math.min(488, this.width - 24);
    }

    private int panelH() {
        return Math.min(312, this.height - 20);
    }

    private int panelX() {
        return (this.width - panelW()) / 2;
    }

    private int panelY() {
        return (this.height - panelH()) / 2;
    }

    @Override
    protected void init() {
        int px = panelX();
        int py = panelY();
        int pw = panelW();
        int ph = panelH();

        this.addDrawableChild(new JjnButton(
                px + pw - 62, py + 8, 52, 20,
                Text.literal("DONE"),
                btn -> this.close(),
                true
        ));

        int bx = px + 14;
        int bw = pw - 28;
        int topY = py + 36;

        int toggleW = 112;
        int filterW = 112;
        int addW = bw - toggleW - filterW - 12;

        this.addDrawableChild(new JjnButton(
                bx, topY, addW, 22,
                Text.literal("+ Add Waypoint Here"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new JjnWaypointEditScreen(this, null));
                    }
                },
                true
        ));

        this.addDrawableChild(new JjnButton(
                bx + addW + 6, topY, filterW, 22,
                Text.literal(currentDimOnly ? "Current Dim" : "All Dimensions"),
                btn -> {
                    currentDimOnly = !currentDimOnly;
                    scrollOffset = 0;
                    this.clearAndInit();
                }
        ));

        JjnConfig cfg = JjnConfig.get();
        this.addDrawableChild(new JjnButton(
                bx + addW + filterW + 12, topY, toggleW, 22,
                Text.literal("Render: " + (cfg.showWaypoints ? "ON" : "OFF")),
                btn -> {
                    cfg.showWaypoints = !cfg.showWaypoints;
                    cfg.save();
                    this.clearAndInit();
                },
                cfg.showWaypoints
        ));

        String worldKey = JjnWaypoints.currentWorldKey(this.client);
        String dim = currentDimOnly ? JjnWaypoints.currentDimension(this.client) : null;
        visibleList = JjnWaypoints.get().waypointsFor(worldKey, dim);

        int maxOffset = Math.max(0, visibleList.size() - ROWS_PER_PAGE);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset));

        int listY = topY + 30;
        int rowH = 44;
        int rowGap = 5;

        int end = Math.min(visibleList.size(), scrollOffset + ROWS_PER_PAGE);
        for (int i = scrollOffset; i < end; i++) {
            JjnWaypoints.Waypoint wp = visibleList.get(i);
            int slot = i - scrollOffset;
            int ry = listY + slot * (rowH + rowGap);

            int delW = 38;
            int editW = 46;
            int onW = 42;
            int rightX = bx + bw;

            this.addDrawableChild(new JjnButton(
                    rightX - delW - editW - onW - 12, ry + 11, onW, 22,
                    Text.literal(wp.enabled ? "ON" : "OFF"),
                    btn -> {
                        wp.enabled = !wp.enabled;
                        JjnWaypoints.get().addOrUpdate(wp);
                        this.clearAndInit();
                    },
                    wp.enabled
            ));

            this.addDrawableChild(new JjnButton(
                    rightX - delW - editW - 6, ry + 11, editW, 22,
                    Text.literal("Edit"),
                    btn -> {
                        if (this.client != null) {
                            this.client.setScreen(new JjnWaypointEditScreen(this, wp));
                        }
                    }
            ));

            this.addDrawableChild(new JjnButton(
                    rightX - delW, ry + 11, delW, 22,
                    Text.literal("Del"),
                    btn -> {
                        JjnWaypoints.get().remove(wp.id);
                        this.clearAndInit();
                    }
            ));
        }

        int pagerY = py + ph - 30;
        JjnButton prevBtn = new JjnButton(
                bx, pagerY, 76, 20,
                Text.literal("< Up"),
                btn -> {
                    scrollOffset = Math.max(0, scrollOffset - 1);
                    this.clearAndInit();
                }
        );
        prevBtn.active = scrollOffset > 0;
        this.addDrawableChild(prevBtn);

        JjnButton nextBtn = new JjnButton(
                bx + bw - 76, pagerY, 76, 20,
                Text.literal("Down >"),
                btn -> {
                    scrollOffset = Math.min(maxOffset, scrollOffset + 1);
                    this.clearAndInit();
                }
        );
        nextBtn.active = scrollOffset < maxOffset;
        this.addDrawableChild(nextBtn);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!visibleList.isEmpty()) {
            int maxOffset = Math.max(0, visibleList.size() - ROWS_PER_PAGE);
            int delta = verticalAmount > 0 ? -1 : (verticalAmount < 0 ? 1 : 0);
            int next = Math.max(0, Math.min(maxOffset, scrollOffset + delta));
            if (next != scrollOffset) {
                scrollOffset = next;
                this.clearAndInit();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, 0xCC101116, 0xDD15171E);

        int px = panelX();
        int py = panelY();
        int pw = panelW();
        int ph = panelH();
        int bx = px + 14;
        int bw = pw - 28;

        context.fill(px, py, px + pw, py + ph, 0xF0191B23);
        context.fill(px, py, px + pw, py + 2, 0xFFC7D2FE);
        context.drawBorder(px, py, pw, ph, 0xFF353949);

        context.drawText(this.textRenderer, "JJN WAYPOINTS", px + 14, py + 12, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, "Shortcut: Press U in-game", px + 106, py + 12, 0xFFC7D2FE, false);

        int listY = py + 66;
        int rowH = 44;
        int rowGap = 5;

        if (visibleList.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    "No waypoints yet. Click '+ Add Waypoint Here' or press U in-game.",
                    px + pw / 2,
                    listY + 62,
                    0xFF9CA3AF
            );
        } else {
            String curDim = JjnWaypoints.currentDimension(this.client);
            int end = Math.min(visibleList.size(), scrollOffset + ROWS_PER_PAGE);
            for (int i = scrollOffset; i < end; i++) {
                JjnWaypoints.Waypoint wp = visibleList.get(i);
                int slot = i - scrollOffset;
                int ry = listY + slot * (rowH + rowGap);

                int color = wp.argb();
                context.fill(bx, ry, bx + bw, ry + rowH, 0xDD14161D);
                context.drawBorder(bx, ry, bw, rowH, wp.enabled ? 0xFF353949 : 0xFF232632);
                context.fill(bx, ry, bx + 3, ry + rowH, wp.enabled ? color : 0xFF4B5563);

                String title = wp.name + (wp.enabled ? "" : "  (Hidden)");
                context.drawText(this.textRenderer, trimToWidth(title, bw - 160), bx + 10, ry + 8, wp.enabled ? 0xFFF8FAFC : 0xFF9CA3AF, true);

                String distSuffix = "";
                if (this.client != null && this.client.player != null && curDim.equals(wp.dimension)) {
                    double dx = (wp.x + 0.5) - this.client.player.getX();
                    double dy = (wp.y + 0.5) - this.client.player.getY();
                    double dz = (wp.z + 0.5) - this.client.player.getZ();
                    distSuffix = "  •  " + Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + "m away";
                }
                String sub = String.format("XYZ: %d, %d, %d  •  %s%s", wp.x, wp.y, wp.z, wp.dimensionDisplayName(), distSuffix);
                context.drawText(this.textRenderer, trimToWidth(sub, bw - 160), bx + 10, ry + 23, 0xFF9CA3AF, false);
            }
        }

        int pagerY = py + ph - 24;
        String countText = visibleList.isEmpty()
                ? "0 waypoints"
                : String.format("Showing %d-%d of %d waypoint(s)",
                scrollOffset + 1,
                Math.min(visibleList.size(), scrollOffset + ROWS_PER_PAGE),
                visibleList.size());
        context.drawCenteredTextWithShadow(this.textRenderer, countText, px + pw / 2, pagerY, 0xFF85899A);

        super.render(context, mouseX, mouseY, delta);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (text == null) return "";
        if (this.textRenderer.getWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int limit = Math.max(12, maxWidth - this.textRenderer.getWidth(ellipsis));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (this.textRenderer.getWidth(sb.toString() + c) > limit) break;
            sb.append(c);
        }
        return sb + ellipsis;
    }
}
