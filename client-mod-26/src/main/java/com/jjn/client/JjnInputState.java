package com.jjn.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tracks rolling 1-second LMB/RMB CPS timestamps and smooth [C] Zoom state for MC 26.2.
 */
public final class JjnInputState {

    private static final Deque<Long> LEFT_CLICKS = new ArrayDeque<>();
    private static final Deque<Long> RIGHT_CLICKS = new ArrayDeque<>();

    private static float zoomMultiplier = 1.0F;
    private static long lastZoomFrameNs = 0L;
    private static boolean smoothingApplied = false;
    private static boolean prevSmoothCamera = false;

    private JjnInputState() {}

    public static synchronized void recordLeftClick() {
        long now = System.currentTimeMillis();
        LEFT_CLICKS.addLast(now);
        prune(LEFT_CLICKS, now);
    }

    public static synchronized void recordRightClick() {
        long now = System.currentTimeMillis();
        RIGHT_CLICKS.addLast(now);
        prune(RIGHT_CLICKS, now);
    }

    public static synchronized int getLeftCps() {
        long now = System.currentTimeMillis();
        prune(LEFT_CLICKS, now);
        return LEFT_CLICKS.size();
    }

    public static synchronized int getRightCps() {
        long now = System.currentTimeMillis();
        prune(RIGHT_CLICKS, now);
        return RIGHT_CLICKS.size();
    }

    private static void prune(Deque<Long> q, long now) {
        long cutoff = now - 1000L;
        while (!q.isEmpty() && q.peekFirst() < cutoff) {
            q.removeFirst();
        }
    }

    public static boolean isZoomHeld(Minecraft mc) {
        if (mc == null || mc.player == null || mc.gui.screen() != null || !JjnConfig.get().zoomEnabled) {
            return false;
        }
        long handle = mc.getWindow().handle();
        return handle != 0L && GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;
    }

    public static void updateZoomTick(Minecraft mc) {
        if (mc == null || mc.options == null) return;
        boolean wantSmooth = JjnConfig.get().zoomSmoothCamera && isZoomHeld(mc);
        if (wantSmooth && !smoothingApplied) {
            prevSmoothCamera = mc.options.smoothCamera;
            mc.options.smoothCamera = true;
            smoothingApplied = true;
        } else if (!wantSmooth && smoothingApplied) {
            mc.options.smoothCamera = prevSmoothCamera;
            smoothingApplied = false;
        }
    }

    public static float nextZoomMultiplier(Minecraft mc) {
        long now = System.nanoTime();
        float dt = lastZoomFrameNs == 0L ? 0.016F : Math.min(0.1F, (now - lastZoomFrameNs) / 1_000_000_000.0F);
        lastZoomFrameNs = now;

        boolean held = isZoomHeld(mc);
        float target = held ? 0.25F : 1.0F;
        zoomMultiplier += (target - zoomMultiplier) * Math.min(1.0F, dt * 16.0F);
        if (!held && Math.abs(zoomMultiplier - 1.0F) < 0.002F) {
            zoomMultiplier = 1.0F;
        }
        return zoomMultiplier;
    }
}
