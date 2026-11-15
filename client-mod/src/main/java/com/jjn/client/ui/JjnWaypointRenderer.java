package com.jjn.client.ui;

import com.jjn.client.JjnConfig;
import com.jjn.client.JjnWaypoints;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.List;

/**
 * Projects named world waypoints onto the 2D HUD using the live 3D camera
 * basis and FOV, plus off-screen directional edge indicators (MC 1.21.4).
 */
public final class JjnWaypointRenderer {

    private JjnWaypointRenderer() {}

    public static void renderWaypoints(DrawContext context, MinecraftClient client) {
        JjnConfig cfg = JjnConfig.get();
        if (!cfg.showWaypoints || client == null || client.player == null || client.world == null) {
            return;
        }

        String worldKey = JjnWaypoints.currentWorldKey(client);
        String dim = JjnWaypoints.currentDimension(client);
        List<JjnWaypoints.Waypoint> list = JjnWaypoints.get().waypointsFor(worldKey, dim);
        if (list.isEmpty()) {
            return;
        }

        Camera cam = client.gameRenderer.getCamera();
        if (cam == null || !cam.isReady()) {
            return;
        }

        Vec3d camPos = cam.getPos();
        Vector3f fwd = cam.getHorizontalPlane();
        Vector3f up = cam.getVerticalPlane();
        Vector3f left = cam.getDiagonalPlane();
        float fovDeg = Math.max(20.0f, Math.min(140.0f, client.options.getFov().getValue()));

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        double aspect = (double) screenW / Math.max(1, screenH);
        double tanHalfFov = Math.tan(Math.toRadians(fovDeg) * 0.5);

        var font = client.textRenderer;

        for (JjnWaypoints.Waypoint wp : list) {
            if (!wp.enabled) continue;

            double dx = (wp.x + 0.5) - camPos.x;
            double dy = (wp.y + 1.15) - camPos.y;
            double dz = (wp.z + 0.5) - camPos.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (cfg.waypointMaxDistance > 0 && dist > cfg.waypointMaxDistance) {
                continue;
            }

            double zCam = dx * fwd.x() + dy * fwd.y() + dz * fwd.z();
            double xRight = -(dx * left.x() + dy * left.y() + dz * left.z());
            double yUp = dx * up.x() + dy * up.y() + dz * up.z();

            int color = wp.argb();
            int rgb = color & 0x00FFFFFF;

            boolean inFront = zCam > 0.1;
            double sx = 0;
            double sy = 0;
            boolean onScreen = false;

            if (inFront) {
                double ndcX = xRight / (zCam * tanHalfFov * aspect);
                double ndcY = yUp / (zCam * tanHalfFov);
                sx = (screenW * 0.5) * (1.0 + ndcX);
                sy = (screenH * 0.5) * (1.0 - ndcY);
                onScreen = sx >= 8 && sx <= screenW - 8 && sy >= 8 && sy <= screenH - 8;
            }

            if (onScreen) {
                int px = (int) Math.round(sx);
                int py = (int) Math.round(sy);

                if (cfg.waypointShowBeacon) {
                    int beamAlpha = dist < 12 ? 0x26000000 : 0x48000000;
                    int outerAlpha = 0x1A000000;
                    int beamTop = Math.max(4, py - 42);
                    int beamBot = Math.min(screenH - 4, py + 18);
                    context.fill(px - 2, beamTop + 6, px + 2, beamBot, outerAlpha | rgb);
                    context.fill(px - 1, beamTop, px + 1, beamBot, beamAlpha | rgb);
                }

                context.fill(px - 3, py - 3, px + 4, py + 4, 0xEE15171E);
                context.fill(px - 2, py - 2, px + 3, py + 3, color);
                context.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFFFFFF);

                String name = wp.name == null || wp.name.isBlank() ? "Waypoint" : wp.name;
                String distStr = cfg.waypointShowDistance ? (" " + Math.round(dist) + "m") : "";
                int nameW = font.getWidth(name);
                int distW = font.getWidth(distStr);
                int pillW = nameW + distW + 10;
                int pillH = font.fontHeight + 5;

                int pillX = Math.max(4, Math.min(screenW - pillW - 4, px - pillW / 2));
                int pillY = Math.max(4, Math.min(screenH - pillH - 4, py - pillH - 7));

                context.fill(pillX, pillY, pillX + pillW, pillY + pillH, 0xCC15171E);
                context.drawBorder(pillX, pillY, pillW, pillH, 0xFF353949);
                context.fill(pillX, pillY, pillX + 2, pillY + pillH, color);

                int textY = pillY + 3;
                context.drawText(font, name, pillX + 5, textY, 0xFFF8FAFC, cfg.hudTextShadow);
                if (!distStr.isEmpty()) {
                    context.drawText(font, distStr, pillX + 5 + nameW, textY, color, cfg.hudTextShadow);
                }
            } else if (cfg.waypointEdgeIndicators) {
                double dirX = inFront ? (sx - screenW * 0.5) : (xRight / aspect);
                double dirY = inFront ? (sy - screenH * 0.5) : (-yUp);
                double len = Math.hypot(dirX, dirY);
                if (len < 1e-4) {
                    dirX = 0.0;
                    dirY = 1.0;
                } else {
                    dirX /= len;
                    dirY /= len;
                }

                String shortName = wp.name == null || wp.name.isBlank() ? "WP" : wp.name;
                if (shortName.length() > 14) {
                    shortName = shortName.substring(0, 13) + "...";
                }
                String label = cfg.waypointShowDistance ? (shortName + " " + Math.round(dist) + "m") : shortName;

                int pillW = font.getWidth(label) + 12;
                int pillH = font.fontHeight + 4;
                int marginX = pillW / 2 + 8;
                int marginY = pillH / 2 + 8;

                double halfW = Math.max(10.0, screenW * 0.5 - marginX);
                double halfH = Math.max(10.0, screenH * 0.5 - marginY);
                double tX = Math.abs(dirX) > 1e-5 ? halfW / Math.abs(dirX) : Double.MAX_VALUE;
                double tY = Math.abs(dirY) > 1e-5 ? halfH / Math.abs(dirY) : Double.MAX_VALUE;
                double t = Math.min(tX, tY);

                int cx = (int) Math.round(screenW * 0.5 + dirX * t);
                int cy = (int) Math.round(screenH * 0.5 + dirY * t);
                int bx = Math.max(6, Math.min(screenW - pillW - 6, cx - pillW / 2));
                int by = Math.max(6, Math.min(screenH - pillH - 6, cy - pillH / 2));

                context.fill(bx, by, bx + pillW, by + pillH, 0xB815171E);
                context.drawBorder(bx, by, pillW, pillH, color);
                context.fill(bx + 3, by + 3, bx + 6, by + pillH - 3, color);
                context.drawText(font, label, bx + 9, by + 2, 0xFFE5E7EB, cfg.hudTextShadow);
            }
        }
    }
}
