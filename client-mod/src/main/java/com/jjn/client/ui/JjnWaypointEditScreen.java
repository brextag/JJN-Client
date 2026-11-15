package com.jjn.client.ui;

import com.jjn.client.JjnWaypoints;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * Create or edit a named waypoint (name, XYZ coordinates, color, visibility) in MC 1.21.4.
 */
public class JjnWaypointEditScreen extends Screen {

    private final Screen parent;
    private final JjnWaypoints.Waypoint editing;
    private String dimension;

    private String draftName;
    private String draftX;
    private String draftY;
    private String draftZ;
    private int colorIndex;
    private boolean enabled;

    private TextFieldWidget nameBox;
    private TextFieldWidget xBox;
    private TextFieldWidget yBox;
    private TextFieldWidget zBox;
    private boolean initialFocusDone = false;

    public JjnWaypointEditScreen(Screen parent, JjnWaypoints.Waypoint existing) {
        super(Text.literal(existing == null ? "New Waypoint" : "Edit Waypoint"));
        this.parent = parent;
        this.editing = existing;
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
        if (this.editing == null && !initialFocusDone && this.client != null) {
            if (this.client.player != null) {
                this.draftX = String.valueOf(this.client.player.getBlockX());
                this.draftY = String.valueOf(this.client.player.getBlockY());
                this.draftZ = String.valueOf(this.client.player.getBlockZ());
            }
            this.dimension = JjnWaypoints.currentDimension(this.client);
            this.colorIndex = JjnWaypoints.get().waypointsFor(
                    JjnWaypoints.currentWorldKey(this.client), null).size() % JjnWaypoints.COLORS.length;
        }

        if (this.nameBox != null) draftName = this.nameBox.getText();
        if (this.xBox != null) draftX = this.xBox.getText();
        if (this.yBox != null) draftY = this.yBox.getText();
        if (this.zBox != null) draftZ = this.zBox.getText();

        int pw = Math.min(346, this.width - 24);
        int ph = 228;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;
        int bx = px + 16;
        int bw = pw - 32;

        nameBox = new TextFieldWidget(this.textRenderer, bx, py + 48, bw, 22, Text.literal("Waypoint Name"));
        nameBox.setMaxLength(40);
        nameBox.setPlaceholder(Text.literal("e.g. Home Base, Portal, Mine..."));
        nameBox.setText(draftName);
        nameBox.setChangedListener(val -> draftName = val);
        this.addDrawableChild(nameBox);

        int hereW = 58;
        int coordGap = 6;
        int coordW = (bw - hereW - coordGap * 3) / 3;
        int cy = py + 92;

        xBox = createCoordBox(bx, cy, coordW, "X", draftX, val -> draftX = val);
        yBox = createCoordBox(bx + coordW + coordGap, cy, coordW, "Y", draftY, val -> draftY = val);
        zBox = createCoordBox(bx + (coordW + coordGap) * 2, cy, coordW, "Z", draftZ, val -> draftZ = val);

        this.addDrawableChild(new JjnButton(
                bx + (coordW + coordGap) * 3, cy, hereW, 20,
                Text.literal("Here"),
                btn -> {
                    if (this.client != null && this.client.player != null) {
                        draftX = String.valueOf(this.client.player.getBlockX());
                        draftY = String.valueOf(this.client.player.getBlockY());
                        draftZ = String.valueOf(this.client.player.getBlockZ());
                        dimension = JjnWaypoints.currentDimension(this.client);
                        xBox.setText(draftX);
                        yBox.setText(draftY);
                        zBox.setText(draftZ);
                    }
                }
        ));

        int optY = py + 128;
        int halfW = (bw - 8) / 2;
        this.addDrawableChild(new JjnButton(
                bx, optY, halfW, 24,
                Text.literal("Color: " + JjnWaypoints.COLOR_NAMES[colorIndex]),
                btn -> {
                    colorIndex = (colorIndex + 1) % JjnWaypoints.COLORS.length;
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new JjnButton(
                bx + halfW + 8, optY, halfW, 24,
                Text.literal("Visible: " + (enabled ? "ON" : "OFF")),
                btn -> {
                    enabled = !enabled;
                    this.clearAndInit();
                },
                enabled
        ));

        int btnY = py + ph - 36;
        int cancelW = 72;
        int manageW = 94;
        int saveW = bw - cancelW - manageW - 12;

        this.addDrawableChild(new JjnButton(
                bx, btnY, saveW, 24,
                Text.literal(editing == null ? "SAVE WAYPOINT" : "UPDATE WAYPOINT"),
                btn -> saveAndClose(),
                true
        ));

        this.addDrawableChild(new JjnButton(
                bx + saveW + 6, btnY, manageW, 24,
                Text.literal("Manage All"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new JjnWaypointsScreen(this.parent));
                    }
                }
        ));

        this.addDrawableChild(new JjnButton(
                bx + saveW + manageW + 12, btnY, cancelW, 24,
                Text.literal("Cancel"),
                btn -> this.close()
        ));

        if (!initialFocusDone) {
            initialFocusDone = true;
            this.setFocused(nameBox);
            nameBox.setFocused(true);
        }
    }

    private TextFieldWidget createCoordBox(int x, int y, int w, String label, String value, java.util.function.Consumer<String> onChange) {
        TextFieldWidget box = new TextFieldWidget(this.textRenderer, x, y, w, 20, Text.literal(label));
        box.setMaxLength(10);
        box.setText(value);
        box.setChangedListener(onChange);
        this.addDrawableChild(box);
        return box;
    }

    private void saveAndClose() {
        String activeWorldKey = this.editing != null
                ? this.editing.worldKey
                : JjnWaypoints.currentWorldKey(this.client);
        String activeDim = this.editing != null
                ? this.dimension
                : JjnWaypoints.currentDimension(this.client);

        String name = nameBox != null ? nameBox.getText().trim() : draftName.trim();
        if (name.isEmpty()) {
            name = JjnWaypoints.get().nextDefaultName(activeWorldKey);
        }

        int px = parseCoord(xBox != null ? xBox.getText() : draftX, 0);
        int py = parseCoord(yBox != null ? yBox.getText() : draftY, 64);
        int pz = parseCoord(zBox != null ? zBox.getText() : draftZ, 0);

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

        JjnWaypoints.get().addOrUpdate(wp);
        if (this.editing == null) {
            com.jjn.client.shop.JjnEconomyManager.get().recordWaypointCreated();
        }
        this.close();
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            saveAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

        int pw = Math.min(346, this.width - 24);
        int ph = 228;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;
        int bx = px + 16;

        context.fill(px, py, px + pw, py + ph, 0xF0191B23);
        context.fill(px, py, px + pw, py + 2, JjnWaypoints.COLORS[colorIndex]);
        context.drawBorder(px, py, pw, ph, 0xFF353949);

        String title = editing == null ? "NEW WAYPOINT" : "EDIT WAYPOINT";
        context.drawText(this.textRenderer, title, bx, py + 14, 0xFFFFFFFF, true);
        String dimLabel = dimension.contains(":") ? dimension.substring(dimension.indexOf(':') + 1) : dimension;
        context.drawText(this.textRenderer, "Dim: " + dimLabel, px + pw - 16 - this.textRenderer.getWidth("Dim: " + dimLabel), py + 14, 0xFFC7D2FE, false);

        context.drawText(this.textRenderer, "Waypoint Name", bx, py + 36, 0xFF9CA3AF, false);
        context.drawText(this.textRenderer, "Coordinates (X / Y / Z)", bx, py + 80, 0xFF9CA3AF, false);

        int swatchColor = JjnWaypoints.COLORS[colorIndex];
        context.fill(bx, py + 160, bx + 10, py + 170, swatchColor);
        context.drawBorder(bx, py + 160, 10, 10, 0xFF353949);
        context.drawText(this.textRenderer, "Tip: Press Enter to save immediately", bx + 16, py + 161, 0xFF85899A, false);

        super.render(context, mouseX, mouseY, delta);
    }
}
