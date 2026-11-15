package com.jjn.client.ui;

import com.jjn.client.JjnConfig;
import com.jjn.client.JjnWaypoints;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * In-game Waypoints Manager screen for Minecraft 26.2:
 * add named waypoints, toggle visibility, edit name/coords/color, or delete.
 */
public class JjnWaypointsScreen extends Screen {

    private final Screen parent;
    private boolean currentDimOnly = true;
    private int scrollOffset = 0;
    private List<JjnWaypoints.Waypoint> visibleList = List.of();

    private static final int ROWS_PER_PAGE = 4;

    public JjnWaypointsScreen(Screen parent) {
        super(Component.literal("JJN Waypoints"));
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
        this.clearWidgets();

        int px = panelX();
        int py = panelY();
        int pw = panelW();
        int ph = panelH();

        this.addRenderableWidget(new JjnButton(
                px + pw - 62, py + 8, 52, 20,
                Component.literal("DONE"),
                btn -> this.onClose(),
                true
        ));

        int bx = px + 14;
        int bw = pw - 28;
        int topY = py + 36;

        // Top action bar: + Add Waypoint Here | Dimension filter | Waypoints Enabled toggle
        int toggleW = 112;
        int filterW = 112;
        int addW = bw - toggleW - filterW - 12;

        this.addRenderableWidget(new JjnButton(
                bx, topY, addW, 22,
                Component.literal("+ Add Waypoint Here"),
                btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new JjnWaypointEditScreen(this, null));
                    }
                },
                true
        ));

        this.addRenderableWidget(new JjnButton(
                bx + addW + 6, topY, filterW, 22,
                Component.literal(currentDimOnly ? "Current Dim" : "All Dimensions"),
                btn -> {
                    currentDimOnly = !currentDimOnly;
                    scrollOffset = 0;
                    this.rebuildWidgets();
                }
        ));

        JjnConfig cfg = JjnConfig.get();
        this.addRenderableWidget(new JjnButton(
                bx + addW + filterW + 12, topY, toggleW, 22,
                Component.literal("Render: " + (cfg.showWaypoints ? "ON" : "OFF")),
                btn -> {
                    cfg.showWaypoints = !cfg.showWaypoints;
                    cfg.save();
                    this.rebuildWidgets();
                },
                cfg.showWaypoints
        ));

        String worldKey = JjnWaypoints.currentWorldKey(this.minecraft);
        String dim = currentDimOnly ? JjnWaypoints.currentDimension(this.minecraft) : null;
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

            this.addRenderableWidget(new JjnButton(
                    rightX - delW - editW - onW - 12, ry + 11, onW, 22,
                    Component.literal(wp.enabled ? "ON" : "OFF"),
                    btn -> {
                        wp.enabled = !wp.enabled;
                        JjnWaypoints.get().addOrUpdate(wp);
                        this.rebuildWidgets();
                    },
                    wp.enabled
            ));

            this.addRenderableWidget(new JjnButton(
                    rightX - delW - editW - 6, ry + 11, editW, 22,
                    Component.literal("Edit"),
                    btn -> {
                        if (this.minecraft != null) {
                            this.minecraft.gui.setScreen(new JjnWaypointEditScreen(this, wp));
                        }
                    }
            ));

            this.addRenderableWidget(new JjnButton(
                    rightX - delW, ry + 11, delW, 22,
                    Component.literal("Del"),
                    btn -> {
                        JjnWaypoints.get().remove(wp.id);
                        this.rebuildWidgets();
                    }
            ));
        }

        // Pager
        int pagerY = py + ph - 30;
        JjnButton prevBtn = new JjnButton(
                bx, pagerY, 76, 20,
                Component.literal("< Up"),
                btn -> {
                    scrollOffset = Math.max(0, scrollOffset - 1);
                    this.rebuildWidgets();
                }
        );
        prevBtn.active = scrollOffset > 0;
        this.addRenderableWidget(prevBtn);

        JjnButton nextBtn = new JjnButton(
                bx + bw - 76, pagerY, 76, 20,
                Component.literal("Down >"),
                btn -> {
                    scrollOffset = Math.min(maxOffset, scrollOffset + 1);
                    this.rebuildWidgets();
                }
        );
        nextBtn.active = scrollOffset < maxOffset;
        this.addRenderableWidget(nextBtn);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!visibleList.isEmpty()) {
            int maxOffset = Math.max(0, visibleList.size() - ROWS_PER_PAGE);
            int delta = verticalAmount > 0 ? -1 : (verticalAmount < 0 ? 1 : 0);
            int next = Math.max(0, Math.min(maxOffset, scrollOffset + delta));
            if (next != scrollOffset) {
                scrollOffset = next;
                this.rebuildWidgets();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xCC101116, 0xDD15171E);

        int px = panelX();
        int py = panelY();
        int pw = panelW();
        int ph = panelH();
        int bx = px + 14;
        int bw = pw - 28;

        graphics.fill(px, py, px + pw, py + ph, 0xF0191B23);
        graphics.fill(px, py, px + pw, py + 2, 0xFFC7D2FE);
        graphics.outline(px, py, pw, ph, 0xFF353949);

        graphics.text(this.font, "JJN WAYPOINTS", px + 14, py + 12, 0xFFFFFFFF, true);
        graphics.text(this.font, "Shortcut: Press U in-game", px + 106, py + 12, 0xFFC7D2FE, false);

        int listY = py + 66;
        int rowH = 44;
        int rowGap = 5;

        if (visibleList.isEmpty()) {
            graphics.centeredText(
                    this.font,
                    "No waypoints yet. Click '+ Add Waypoint Here' or press U in-game.",
                    px + pw / 2,
                    listY + 62,
                    0xFF9CA3AF
            );
        } else {
            String curDim = JjnWaypoints.currentDimension(this.minecraft);
            int end = Math.min(visibleList.size(), scrollOffset + ROWS_PER_PAGE);
            for (int i = scrollOffset; i < end; i++) {
                JjnWaypoints.Waypoint wp = visibleList.get(i);
                int slot = i - scrollOffset;
                int ry = listY + slot * (rowH + rowGap);

                int color = wp.argb();
                graphics.fill(bx, ry, bx + bw, ry + rowH, 0xDD14161D);
                graphics.outline(bx, ry, bw, rowH, wp.enabled ? 0xFF353949 : 0xFF232632);
                graphics.fill(bx, ry, bx + 3, ry + rowH, wp.enabled ? color : 0xFF4B5563);

                String title = wp.name + (wp.enabled ? "" : "  (Hidden)");
                graphics.text(this.font, trimToWidth(title, bw - 160), bx + 10, ry + 8, wp.enabled ? 0xFFF8FAFC : 0xFF9CA3AF, true);

                String distSuffix = "";
                if (this.minecraft != null && this.minecraft.player != null && curDim.equals(wp.dimension)) {
                    double dx = (wp.x + 0.5) - this.minecraft.player.getX();
                    double dy = (wp.y + 0.5) - this.minecraft.player.getY();
                    double dz = (wp.z + 0.5) - this.minecraft.player.getZ();
                    distSuffix = "  •  " + Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + "m away";
                }
                String sub = String.format("XYZ: %d, %d, %d  •  %s%s", wp.x, wp.y, wp.z, wp.dimensionDisplayName(), distSuffix);
                graphics.text(this.font, trimToWidth(sub, bw - 160), bx + 10, ry + 23, 0xFF9CA3AF, false);
            }
        }

        int pagerY = py + ph - 24;
        String countText = visibleList.isEmpty()
                ? "0 waypoints"
                : String.format("Showing %d-%d of %d waypoint(s)",
                scrollOffset + 1,
                Math.min(visibleList.size(), scrollOffset + ROWS_PER_PAGE),
                visibleList.size());
        graphics.centeredText(this.font, countText, px + pw / 2, pagerY, 0xFF85899A);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (text == null) return "";
        if (this.font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int limit = Math.max(12, maxWidth - this.font.width(ellipsis));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (this.font.width(sb.toString() + c) > limit) break;
            sb.append(c);
        }
        return sb + ellipsis;
    }
}
