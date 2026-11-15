package com.jjn.client.ui;

import com.jjn.client.JjnConfig;
import com.jjn.client.JjnInputState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared JJN HUD, Keystrokes, CPS, Armor Status, and Potion Status renderer for MC 26.2.
 */
public final class JjnHudRenderer {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private JjnHudRenderer() {}

    public record Bounds(int x, int y, int width, int height) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx <= x + width && my >= y && my <= y + height;
        }
    }

    private record ArmorEntry(ItemStack stack, int durabilityPct, boolean damageable) {}

    private record PotionEntry(String label, int color) {}

    public static Bounds renderHud(GuiGraphicsExtractor graphics, Minecraft mc, boolean previewMode) {
        JjnConfig cfg = JjnConfig.get();
        if ((!cfg.showHud && !previewMode) || mc == null) {
            return new Bounds(0, 0, 0, 0);
        }

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        List<String> lines = buildLines(mc, cfg, previewMode);
        boolean hasKeystrokes = cfg.showKeystrokes;
        if (lines.isEmpty() && !hasKeystrokes) {
            if (!previewMode) {
                renderArmorHud(graphics, mc, cfg, false, screenW, screenH, 0, 0, 0, 0);
                renderPotionHud(graphics, mc, cfg, false, screenW, screenH, 0, 0, 0, 0);
                return new Bounds(0, 0, 0, 0);
            }
            lines.add("JJN Client HUD");
        }

        var font = mc.font;
        int padX = 7;
        int padY = 5;
        int lineH = font.lineHeight + 2;

        int textBoxW = 0;
        int textBoxH = 0;
        if (!lines.isEmpty()) {
            int maxW = 0;
            for (String line : lines) {
                maxW = Math.max(maxW, font.width(line));
            }
            textBoxW = maxW + padX * 2 + 2;
            textBoxH = lines.size() * lineH + padY * 2 - 2;
        }

        int keySize = 18;
        int keyGap = 2;
        int keysW = hasKeystrokes ? keySize * 3 + keyGap * 2 : 0;
        int keysH = hasKeystrokes ? (keySize * 2 + 12 + keyGap * 2) : 0;

        int rawW = Math.max(textBoxW, keysW);
        int rawH = textBoxH + (hasKeystrokes && !lines.isEmpty() ? 6 : 0) + keysH;

        float scale = cfg.hudScale;
        int scaledW = Math.round(rawW * scale);
        int scaledH = Math.round(rawH * scale);

        int[] pos = computeOrigin(cfg, screenW, screenH, scaledW, scaledH);
        int originX = pos[0];
        int originY = pos[1];

        int accent = cfg.accentColor();
        int bg = cfg.backgroundArgb();

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(originX, originY);
        pose.scale(scale, scale);

        if (!lines.isEmpty()) {
            if ((bg >>> 24) > 0) {
                graphics.fill(0, 0, textBoxW, textBoxH, bg);
                graphics.fill(0, 0, 2, textBoxH, accent);
            }
            int drawY = padY;
            for (String line : lines) {
                graphics.text(font, line, padX + 2, drawY, 0xFFE5E7EB, cfg.hudTextShadow);
                drawY += lineH;
            }
        }

        if (hasKeystrokes) {
            int ky = lines.isEmpty() ? 0 : textBoxH + 6;
            renderKeystrokes(graphics, mc, 0, ky, keySize, keyGap, bg, accent, cfg.hudTextShadow, previewMode);
        }

        pose.popMatrix();

        renderArmorHud(graphics, mc, cfg, previewMode, screenW, screenH, originX, originY, scaledW, scaledH);
        renderPotionHud(graphics, mc, cfg, previewMode, screenW, screenH, originX, originY, scaledW, scaledH);

        return new Bounds(originX, originY, scaledW, scaledH);
    }

    private static List<String> buildLines(Minecraft mc, JjnConfig cfg, boolean previewMode) {
        List<String> lines = new ArrayList<>();
        if (cfg.showFps) {
            int fps = mc.getFps();
            lines.add(cfg.fpsCompact ? (fps + " FPS") : ("FPS: " + fps));
        }

        if (cfg.showCps) {
            int lmb = previewMode ? 9 : JjnInputState.getLeftCps();
            int rmb = previewMode ? 3 : JjnInputState.getRightCps();
            lines.add("CPS: " + lmb + " | " + rmb);
        }

        if (cfg.showCoords) {
            if (mc.player != null) {
                String coord = String.format(
                        "XYZ: %d, %d, %d",
                        mc.player.getBlockX(),
                        mc.player.getBlockY(),
                        mc.player.getBlockZ()
                );
                if (cfg.showDirection) {
                    Direction dir = mc.player.getDirection();
                    coord += " [" + shortDir(dir) + "]";
                }
                lines.add(coord);
            } else if (previewMode) {
                lines.add(cfg.showDirection ? "XYZ: 128, 64, -240 [N]" : "XYZ: 128, 64, -240");
            }
        }

        if (cfg.toggleSprint && cfg.showSprintHud) {
            lines.add("Sprint: Toggled");
        }

        if (cfg.showPingServer) {
            if (mc.player != null && mc.getConnection() != null) {
                var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                int ping = info != null ? info.getLatency() : 0;
                var server = mc.getCurrentServer();
                String host = server != null ? server.ip : "Singleplayer";
                lines.add(host + " • " + ping + " ms");
            } else if (previewMode) {
                lines.add("play.jjnclient.net • 24 ms");
            }
        }

        if (cfg.showBiome) {
            if (mc.player != null && mc.level != null) {
                BlockPos bp = mc.player.blockPosition();
                String biomeId = mc.level.getBiome(bp).unwrapKey()
                        .map(k -> k.identifier().getPath())
                        .orElse("unknown");
                lines.add("Biome: " + formatBiome(biomeId));
            } else if (previewMode) {
                lines.add("Biome: Cherry Grove");
            }
        }
        return lines;
    }

    private static void renderArmorHud(
            GuiGraphicsExtractor graphics,
            Minecraft mc,
            JjnConfig cfg,
            boolean previewMode,
            int screenW,
            int screenH,
            int mainX,
            int mainY,
            int mainW,
            int mainH
    ) {
        if (!cfg.showArmorHud) {
            return;
        }
        List<ArmorEntry> entries = new ArrayList<>();
        if (mc.player != null) {
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                ItemStack stack = mc.player.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    boolean dmg = stack.isDamageableItem() && stack.getMaxDamage() > 0;
                    int pct = dmg
                            ? Math.max(0, Math.min(100, Math.round(100.0F * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage())))
                            : 100;
                    entries.add(new ArmorEntry(stack, pct, dmg));
                }
            }
        }
        if (entries.isEmpty() && previewMode) {
            entries.add(new ArmorEntry(new ItemStack(Items.DIAMOND_HELMET), 88, true));
            entries.add(new ArmorEntry(new ItemStack(Items.DIAMOND_CHESTPLATE), 74, true));
            entries.add(new ArmorEntry(new ItemStack(Items.DIAMOND_LEGGINGS), 92, true));
            entries.add(new ArmorEntry(new ItemStack(Items.DIAMOND_BOOTS), 65, true));
        }
        if (entries.isEmpty()) {
            return;
        }

        var font = mc.font;
        int rowH = 19;
        int cardW = 54;
        int cardH = entries.size() * rowH + 6;
        int x = 8;
        int y = Math.max(8, (screenH - cardH) / 2);

        if (mainW > 0 && mainX < x + cardW + 6 && y < mainY + mainH + 6 && y + cardH > mainY - 6) {
            if (mainY + mainH + 8 + cardH <= screenH - 8) {
                y = mainY + mainH + 8;
            } else {
                x = screenW - cardW - 8;
            }
        }

        int bg = (cfg.backgroundArgb() >>> 24) > 0 ? cfg.backgroundArgb() : 0xA815171E;
        int accent = cfg.accentColor();
        graphics.fill(x, y, x + cardW, y + cardH, bg);
        graphics.fill(x, y, x + 2, y + cardH, accent);
        graphics.outline(x, y, cardW, cardH, 0xFF353949);

        for (int i = 0; i < entries.size(); i++) {
            ArmorEntry e = entries.get(i);
            int ry = y + 3 + i * rowH;
            graphics.item(e.stack(), x + 5, ry + 1);

            String label = e.damageable() ? (e.durabilityPct() + "%") : "--";
            int color = e.durabilityPct() >= 50 ? 0xFF86EFAC : (e.durabilityPct() >= 20 ? 0xFFFCD34D : 0xFFFCA5A5);
            graphics.text(font, label, x + 24, ry + 4, color, cfg.hudTextShadow);

            if (e.damageable()) {
                int barX = x + 24;
                int barY = ry + 14;
                int barW = 24;
                graphics.fill(barX, barY, barX + barW, barY + 2, 0xFF101116);
                int fillW = Math.max(1, Math.round(barW * (e.durabilityPct() / 100.0F)));
                graphics.fill(barX, barY, barX + fillW, barY + 2, color);
            }
        }
    }

    private static void renderPotionHud(
            GuiGraphicsExtractor graphics,
            Minecraft mc,
            JjnConfig cfg,
            boolean previewMode,
            int screenW,
            int screenH,
            int mainX,
            int mainY,
            int mainW,
            int mainH
    ) {
        if (!cfg.showPotionHud) {
            return;
        }
        List<PotionEntry> entries = new ArrayList<>();
        if (mc.player != null) {
            for (MobEffectInstance inst : mc.player.getActiveEffects()) {
                String name = inst.getEffect().value().getDisplayName().getString();
                String amp = romanAmplifier(inst.getAmplifier());
                String dur = MobEffectUtil.formatDuration(inst, 1.0F, 20.0F).getString();
                String label = amp.isEmpty() ? (name + "  " + dur) : (name + " " + amp + "  " + dur);
                int col = 0xFF000000 | inst.getEffect().value().getColor();
                entries.add(new PotionEntry(label, col));
            }
        }
        if (entries.isEmpty() && previewMode) {
            entries.add(new PotionEntry("Speed II  01:45", 0xFF38BDF8));
            entries.add(new PotionEntry("Night Vision  04:20", 0xFFC7D2FE));
        }
        if (entries.isEmpty()) {
            return;
        }

        var font = mc.font;
        int padX = 7;
        int rowH = 16;
        int gap = 3;
        int y = 8;

        int maxPillW = 0;
        for (PotionEntry pe : entries) {
            maxPillW = Math.max(maxPillW, font.width(pe.label()) + padX * 2 + 2);
        }
        if (mainW > 0 && mainX + mainW > screenW - maxPillW - 14 && mainY < y + entries.size() * (rowH + gap) + 8) {
            y = Math.min(screenH - rowH - 8, mainY + mainH + 6);
        }

        int bg = (cfg.backgroundArgb() >>> 24) > 0 ? cfg.backgroundArgb() : 0xA815171E;
        for (int i = 0; i < entries.size(); i++) {
            PotionEntry pe = entries.get(i);
            int pillW = font.width(pe.label()) + padX * 2 + 2;
            int px = screenW - pillW - 8;
            int py = y + i * (rowH + gap);
            if (py + rowH > screenH - 6) break;
            graphics.fill(px, py, px + pillW, py + rowH, bg);
            graphics.fill(px, py, px + 2, py + rowH, pe.color());
            graphics.outline(px, py, pillW, rowH, 0xFF353949);
            graphics.text(font, pe.label(), px + padX + 1, py + 4, 0xFFE5E7EB, cfg.hudTextShadow);
        }
    }

    private static String romanAmplifier(int amplifier) {
        return switch (amplifier) {
            case 0 -> "";
            case 1 -> "II";
            case 2 -> "III";
            case 3 -> "IV";
            case 4 -> "V";
            default -> String.valueOf(amplifier + 1);
        };
    }

    private static void renderKeystrokes(
            GuiGraphicsExtractor graphics,
            Minecraft mc,
            int x,
            int y,
            int size,
            int gap,
            int bg,
            int accent,
            boolean shadow,
            boolean previewMode
    ) {
        int boxBg = (bg >>> 24) > 0 ? bg : 0x7A15171E;
        boolean wDown = previewMode || mc.options.keyUp.isDown();
        boolean aDown = mc.options.keyLeft.isDown();
        boolean sDown = mc.options.keyDown.isDown();
        boolean dDown = mc.options.keyRight.isDown();
        boolean spDown = mc.options.keyJump.isDown();

        drawKey(graphics, mc, "W", x + size + gap, y, size, size, wDown, boxBg, accent, shadow);
        drawKey(graphics, mc, "A", x, y + size + gap, size, size, aDown, boxBg, accent, shadow);
        drawKey(graphics, mc, "S", x + size + gap, y + size + gap, size, size, sDown, boxBg, accent, shadow);
        drawKey(graphics, mc, "D", x + (size + gap) * 2, y + size + gap, size, size, dDown, boxBg, accent, shadow);
        drawKey(graphics, mc, "SPACE", x, y + (size + gap) * 2, size * 3 + gap * 2, 12, spDown, boxBg, accent, shadow);
    }

    private static void drawKey(
            GuiGraphicsExtractor graphics,
            Minecraft mc,
            String label,
            int x,
            int y,
            int w,
            int h,
            boolean pressed,
            int bg,
            int accent,
            boolean shadow
    ) {
        int fill = pressed ? accent : bg;
        int textColor = pressed ? 0xFF171923 : 0xFFE5E7EB;
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.outline(x, y, w, h, pressed ? accent : 0xFF353949);

        var font = mc.font;
        int tx = x + (w - font.width(label)) / 2;
        int ty = y + (h - font.lineHeight) / 2 + 1;
        graphics.text(font, label, tx, ty, textColor, !pressed && shadow);
    }

    private static int[] computeOrigin(JjnConfig cfg, int screenW, int screenH, int boxW, int boxH) {
        int margin = 8;
        return switch (cfg.hudCorner) {
            case JjnConfig.CORNER_TOP_RIGHT -> new int[]{Math.max(margin, screenW - boxW - margin), margin};
            case JjnConfig.CORNER_BOTTOM_LEFT -> new int[]{margin, Math.max(margin, screenH - boxH - margin)};
            case JjnConfig.CORNER_BOTTOM_RIGHT ->
                    new int[]{Math.max(margin, screenW - boxW - margin), Math.max(margin, screenH - boxH - margin)};
            case JjnConfig.CORNER_CUSTOM -> new int[]{
                    Math.max(0, Math.min(Math.max(0, screenW - boxW), cfg.hudCustomX)),
                    Math.max(0, Math.min(Math.max(0, screenH - boxH), cfg.hudCustomY))
            };
            default -> new int[]{margin, margin};
        };
    }

    private static String shortDir(Direction d) {
        return switch (d) {
            case NORTH -> "N";
            case SOUTH -> "S";
            case EAST -> "E";
            case WEST -> "W";
            default -> "?";
        };
    }

    private static String formatBiome(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
