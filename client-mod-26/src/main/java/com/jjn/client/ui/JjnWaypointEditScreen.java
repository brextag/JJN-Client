package com.jjn.client.ui;

import com.jjn.client.JjnWaypoints;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * Create or edit a named waypoint (name, XYZ coordinates, color, visibility).
 * Pressing Enter saves immediately.
 */
public class JjnWaypointEditScreen extends Screen {

    private final Screen parent;
    private final JjnWaypoints.Waypoint editing;
    private final String worldKey;
    private String dimension;

    private String draftName;
    private String draftX;
    private String draftY;
    private String draftZ;
    private int colorIndex;
    private boolean enabled;

    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private boolean initialFocusDone = false;

    public JjnWaypointEditScreen(Screen parent, JjnWaypoints.Waypoint existing) {
        super(Component.literal(existing == null ? "New Waypoint" : "Edit Waypoint"));
        this.parent = parent;
        this.editing = existing;
        this.worldKey = existing != null ? existing.worldKey : JjnWaypoints.currentWorldKey(null);
        this.dimension = existing != null ? existing.dimension : "minecraft:overworld";
        this.draftName = existing != null ? existing.name : "";
        this.draftX = existing != null ? String.valueOf(existing.x) : "0";
        this.draftY = existing != null ? String.valueOf(existing.y) : "64";
        this.draftZ = existing != null ? String.valueOf(existing.z) : "0";
        this.colorIndex = existing != null ? existing.colorIndex : 0;
        this.enabled = existing == null || existing.enabled;
    }

    @Override
    protected void init() {
        if (this.editing == null && !initialFocusDone && this.minecraft != null) {
            if (this.minecraft.player != null) {
                this.draftX = String.valueOf(this.minecraft.player.getBlockX());
                this.draftY = String.valueOf(this.minecraft.player.getBlockY());
                this.draftZ = String.valueOf(this.minecraft.player.getBlockZ());
            }
            this.dimension = JjnWaypoints.currentDimension(this.minecraft);
            this.colorIndex = JjnWaypoints.get().waypointsFor(
                    JjnWaypoints.currentWorldKey(this.minecraft), null).size() % JjnWaypoints.COLORS.length;
        }

        if (this.nameBox != null) draftName = this.nameBox.getValue();
        if (this.xBox != null) draftX = this.xBox.getValue();
        if (this.yBox != null) draftY = this.yBox.getValue();
        if (this.zBox != null) draftZ = this.zBox.getValue();

        this.clearWidgets();

        int pw = Math.min(346, this.width - 24);
        int ph = 228;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;
        int bx = px + 16;
        int bw = pw - 32;

        // Name box
        nameBox = new EditBox(this.font, bx, py + 48, bw, 22, Component.literal("Waypoint Name"));
        nameBox.setMaxLength(40);
        nameBox.setHint(Component.literal("e.g. Home Base, Portal, Mine..."));
        nameBox.setValue(draftName);
        nameBox.setResponder(val -> draftName = val);
        this.addRenderableWidget(nameBox);

        // X, Y, Z boxes + "Here" button
        int hereW = 58;
        int coordGap = 6;
        int coordW = (bw - hereW - coordGap * 3) / 3;
        int cy = py + 92;

        xBox = createCoordBox(bx, cy, coordW, "X", draftX, val -> draftX = val);
        yBox = createCoordBox(bx + coordW + coordGap, cy, coordW, "Y", draftY, val -> draftY = val);
        zBox = createCoordBox(bx + (coordW + coordGap) * 2, cy, coordW, "Z", draftZ, val -> draftZ = val);

        this.addRenderableWidget(new JjnButton(
                bx + (coordW + coordGap) * 3, cy, hereW, 20,
                Component.literal("Here"),
                btn -> {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        draftX = String.valueOf(this.minecraft.player.getBlockX());
                        draftY = String.valueOf(this.minecraft.player.getBlockY());
                        draftZ = String.valueOf(this.minecraft.player.getBlockZ());
                        dimension = JjnWaypoints.currentDimension(this.minecraft);
                        xBox.setValue(draftX);
                        yBox.setValue(draftY);
                        zBox.setValue(draftZ);
                    }
                }
        ));

        // Color + Visibility row
        int optY = py + 128;
        int halfW = (bw - 8) / 2;
        this.addRenderableWidget(new JjnButton(
                bx, optY, halfW, 24,
                Component.literal("Color: " + JjnWaypoints.COLOR_NAMES[colorIndex]),
                btn -> {
                    colorIndex = (colorIndex + 1) % JjnWaypoints.COLORS.length;
                    this.rebuildWidgets();
                }
        ));

        this.addRenderableWidget(new JjnButton(
                bx + halfW + 8, optY, halfW, 24,
                Component.literal("Visible: " + (enabled ? "ON" : "OFF")),
                btn -> {
                    enabled = !enabled;
                    this.rebuildWidgets();
                },
                enabled
        ));

        // Bottom actions: Save, Manage All, Cancel
        int btnY = py + ph - 36;
        int cancelW = 72;
        int manageW = 94;
        int saveW = bw - cancelW - manageW - 12;

        this.addRenderableWidget(new JjnButton(
                bx, btnY, saveW, 24,
                Component.literal(editing == null ? "SAVE WAYPOINT" : "UPDATE WAYPOINT"),
                btn -> saveAndClose(),
                true
        ));

        this.addRenderableWidget(new JjnButton(
                bx + saveW + 6, btnY, manageW, 24,
                Component.literal("Manage All"),
                btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new JjnWaypointsScreen(this.parent));
                    }
                }
        ));

        this.addRenderableWidget(new JjnButton(
                bx + saveW + manageW + 12, btnY, cancelW, 24,
                Component.literal("Cancel"),
                btn -> this.onClose()
        ));

        if (!initialFocusDone) {
            initialFocusDone = true;
            this.setFocused(nameBox);
            nameBox.setFocused(true);
        }
    }

    private EditBox createCoordBox(int x, int y, int w, String label, String value, java.util.function.Consumer<String> onChange) {
        EditBox box = new EditBox(this.font, x, y, w, 20, Component.literal(label));
        box.setMaxLength(10);
        box.setValue(value);
        box.setResponder(onChange);
        this.addRenderableWidget(box);
        return box;
    }

    private void saveAndClose() {
        String activeWorldKey = this.editing != null
                ? this.editing.worldKey
                : JjnWaypoints.currentWorldKey(this.minecraft);
        String activeDim = this.editing != null
                ? this.dimension
                : JjnWaypoints.currentDimension(this.minecraft);

        String name = nameBox != null ? nameBox.getValue().trim() : draftName.trim();
        if (name.isEmpty()) {
            name = JjnWaypoints.get().nextDefaultName(activeWorldKey);
        }

        int px = parseCoord(xBox != null ? xBox.getValue() : draftX, 0);
        int py = parseCoord(yBox != null ? yBox.getValue() : draftY, 64);
        int pz = parseCoord(zBox != null ? zBox.getValue() : draftZ, 0);

        JjnWaypoints.Waypoint wp = this.editing != null ? this.editing : new JjnWaypoints.Waypoint();
        if (wp.id == null || wp.id.isBlank()) {
            wp.id = UUID.randomUUID().toString();
        }
        wp.name = name;
        wp.worldKey = activeWorldKey;
        wp.dimension = activeDim;
        wp.x = px;
        wp.y = py;
        wp.z = pz;
        wp.colorIndex = colorIndex;
        wp.enabled = enabled;

        boolean isNew = this.editing == null;
        JjnWaypoints.get().addOrUpdate(wp);
        if (isNew) {
            com.jjn.client.shop.JjnEconomyManager.get().recordWaypointCreated();
        }
        this.onClose();
    }

    private static int parseCoord(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            saveAndClose();
            return true;
        }
        return super.keyPressed(event);
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

        int pw = Math.min(346, this.width - 24);
        int ph = 228;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;
        int bx = px + 16;

        graphics.fill(px, py, px + pw, py + ph, 0xF0191B23);
        graphics.fill(px, py, px + pw, py + 2, JjnWaypoints.COLORS[colorIndex]);
        graphics.outline(px, py, pw, ph, 0xFF353949);

        String title = editing == null ? "NEW WAYPOINT" : "EDIT WAYPOINT";
        graphics.text(this.font, title, bx, py + 14, 0xFFFFFFFF, true);
        String dimLabel = dimension.contains(":") ? dimension.substring(dimension.indexOf(':') + 1) : dimension;
        graphics.text(this.font, "Dim: " + dimLabel, px + pw - 16 - this.font.width("Dim: " + dimLabel), py + 14, 0xFFC7D2FE, false);

        graphics.text(this.font, "Waypoint Name", bx, py + 36, 0xFF9CA3AF, false);
        graphics.text(this.font, "Coordinates (X / Y / Z)", bx, py + 80, 0xFF9CA3AF, false);

        // Color swatch next to bottom hint
        int swatchColor = JjnWaypoints.COLORS[colorIndex];
        graphics.fill(bx, py + 160, bx + 10, py + 170, swatchColor);
        graphics.outline(bx, py + 160, 10, 10, 0xFF353949);
        graphics.text(this.font, "Tip: Press Enter to save immediately", bx + 16, py + 161, 0xFF85899A, false);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
