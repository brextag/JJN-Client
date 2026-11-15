package com.jjn.client.ui;

import com.jjn.client.JjnConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared JJN HUD & Keystrokes renderer for Minecraft 1.21.4.
 */
public final class JjnHudRenderer {

    private JjnHudRenderer() {}

    public record Bounds(int x, int y, int width, int height) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx <= x + width && my >= y && my <= y + height;
        }
    }

    public static Bounds renderHud(DrawContext context, MinecraftClient client, boolean previewMode) {
        JjnConfig cfg = JjnConfig.get();
        if ((!cfg.showHud && !previewMode) || client == null) {
            return new Bounds(0, 0, 0, 0);
        }

        List<String> lines = buildLines(client, cfg, previewMode);
        boolean hasKeystrokes = cfg.showKeystrokes;
        if (lines.isEmpty() && !hasKeystrokes) {
            if (!previewMode) {
                return new Bounds(0, 0, 0, 0);
            }
            lines.add("JJN Client HUD");
        }

        var tr = client.textRenderer;
        int padX = 7;
        int padY = 5;
        int lineH = tr.fontHeight + 2;

        int textBoxW = 0;
        int textBoxH = 0;
        if (!lines.isEmpty()) {
            int maxW = 0;
            for (String line : lines) {
                maxW = Math.max(maxW, tr.getWidth(line));
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

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        int[] pos = computeOrigin(cfg, screenW, screenH, scaledW, scaledH);
        int originX = pos[0];
        int originY = pos[1];

        int accent = cfg.accentColor();
        int bg = cfg.backgroundArgb();

        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(originX, originY, 0.0f);
        matrices.scale(scale, scale, 1.0f);

        if (!lines.isEmpty()) {
            if ((bg >>> 24) > 0) {
                context.fill(0, 0, textBoxW, textBoxH, bg);
                context.fill(0, 0, 2, textBoxH, accent);
            }
            int drawY = padY;
            for (String line : lines) {
                context.drawText(tr, line, padX + 2, drawY, 0xFFE5E7EB, cfg.hudTextShadow);
                drawY += lineH;
            }
        }

        if (hasKeystrokes) {
            int ky = lines.isEmpty() ? 0 : textBoxH + 6;
            renderKeystrokes(context, client, 0, ky, keySize, keyGap, bg, accent, cfg.hudTextShadow, previewMode);
        }

        matrices.pop();
        return new Bounds(originX, originY, scaledW, scaledH);
    }

    private static List<String> buildLines(MinecraftClient client, JjnConfig cfg, boolean previewMode) {
        List<String> lines = new ArrayList<>();
        if (cfg.showFps) {
            int fps = client.getCurrentFps();
            lines.add(cfg.fpsCompact ? (fps + " FPS") : ("FPS: " + fps));
        }

        if (cfg.showCoords) {
            if (client.player != null) {
                String coord = String.format(
                        "XYZ: %d, %d, %d",
                        client.player.getBlockX(),
                        client.player.getBlockY(),
                        client.player.getBlockZ()
                );
                if (cfg.showDirection) {
                    Direction dir = client.player.getHorizontalFacing();
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
            if (client.player != null && client.getNetworkHandler() != null) {
                var info = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
                int ping = info != null ? info.getLatency() : 0;
                var server = client.getCurrentServerEntry();
                String host = server != null ? server.address : "Singleplayer";
                lines.add(host + " • " + ping + " ms");
            } else if (previewMode) {
                lines.add("play.jjnclient.net • 24 ms");
            }
        }

        if (cfg.showBiome) {
            if (client.player != null && client.world != null) {
                BlockPos bp = client.player.getBlockPos();
                String biomeId = client.world.getBiome(bp).getKey()
                        .map(k -> k.getValue().getPath())
                        .orElse("unknown");
                lines.add("Biome: " + formatBiome(biomeId));
            } else if (previewMode) {
                lines.add("Biome: Cherry Grove");
            }
        }
        return lines;
    }

    private static void renderKeystrokes(
            DrawContext context,
            MinecraftClient client,
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
        boolean wDown = previewMode || client.options.forwardKey.isPressed();
        boolean aDown = client.options.leftKey.isPressed();
        boolean sDown = client.options.backKey.isPressed();
        boolean dDown = client.options.rightKey.isPressed();
        boolean spDown = client.options.jumpKey.isPressed();

        drawKey(context, client, "W", x + size + gap, y, size, size, wDown, boxBg, accent, shadow);
        drawKey(context, client, "A", x, y + size + gap, size, size, aDown, boxBg, accent, shadow);
        drawKey(context, client, "S", x + size + gap, y + size + gap, size, size, sDown, boxBg, accent, shadow);
        drawKey(context, client, "D", x + (size + gap) * 2, y + size + gap, size, size, dDown, boxBg, accent, shadow);
        drawKey(context, client, "SPACE", x, y + (size + gap) * 2, size * 3 + gap * 2, 12, spDown, boxBg, accent, shadow);
    }

    private static void drawKey(
            DrawContext context,
            MinecraftClient client,
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
        context.fill(x, y, x + w, y + h, fill);
        context.drawBorder(x, y, w, h, pressed ? accent : 0xFF353949);

        var tr = client.textRenderer;
        int tx = x + (w - tr.getWidth(label)) / 2;
        int ty = y + (h - tr.fontHeight) / 2 + 1;
        context.drawText(tr, label, tx, ty, textColor, !pressed && shadow);
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
