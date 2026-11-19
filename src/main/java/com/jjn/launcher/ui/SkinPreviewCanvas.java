package com.jjn.launcher.ui;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Interactive 3D-projected Minecraft skin preview canvas for JJNLauncher.
 * Supports drag-to-rotate yaw/pitch, Classic/Slim arms, capes, 3D Shop Cosmetics
 * (Wings, Aero-Pack, Halo, Crown, Horns, Fox Ears), and live animated 3D Emotes.
 */
public class SkinPreviewCanvas extends Canvas {

    private Image skinImage;
    private Image capeImage;
    private boolean slim = false;
    private double yawDeg = 22.0;
    private double pitchDeg = -10.0;
    private double dragStartX;
    private double dragStartY;
    private double dragStartYaw;
    private double dragStartPitch;

    private String equippedBackId = "";
    private String equippedHatId = "";

    private String activeEmoteId = "";
    private long emoteStartMs = 0L;
    private boolean loopingAnimation = false;
    private AnimationTimer animTimer;

    private record Vec3(double x, double y, double z) {}

    private record TexturedQuad(
            Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
            int u, int v, int uw, int vh,
            double shade, double depth, boolean overlay, Image texture, Color flatColor
    ) {}

    private static final class EmotePose {
        double bodyRotX, bodyRotY, bodyRotZ, bodyYOffset;
        double headRotX, headRotY, headRotZ;
        double rArmX, rArmY, rArmZ;
        double lArmX, lArmY, lArmZ;
        double rLegX, rLegZ;
        double lLegX, lLegZ;
    }

    public SkinPreviewCanvas(double width, double height) {
        super(width, height);

        setOnMousePressed(e -> {
            dragStartX = e.getX();
            dragStartY = e.getY();
            dragStartYaw = yawDeg;
            dragStartPitch = pitchDeg;
        });

        setOnMouseDragged(e -> {
            yawDeg = dragStartYaw + (e.getX() - dragStartX) * 0.7;
            pitchDeg = Math.max(-45.0, Math.min(45.0, dragStartPitch + (e.getY() - dragStartY) * 0.45));
            redraw();
        });

        redraw();
    }

    public void setSlim(boolean slim) {
        this.slim = slim;
        redraw();
    }

    public void setYaw(double yawDeg) {
        this.yawDeg = yawDeg;
        this.pitchDeg = -10.0;
        redraw();
    }

    public void flipView() {
        yawDeg = (yawDeg + 180.0) % 360.0;
        redraw();
    }

    public void setCapeImage(Image image) {
        this.capeImage = image;
        redraw();
    }

    public void setCosmetics(String backId, String hatId) {
        this.equippedBackId = backId == null ? "" : backId;
        this.equippedHatId = hatId == null ? "" : hatId;
        updateTimerState();
        redraw();
    }

    public void playEmote(String emoteId, boolean loop) {
        this.activeEmoteId = emoteId == null ? "" : emoteId;
        this.emoteStartMs = System.currentTimeMillis();
        this.loopingAnimation = loop;
        updateTimerState();
        redraw();
    }

    public void stopEmote() {
        this.activeEmoteId = "";
        this.loopingAnimation = false;
        updateTimerState();
        redraw();
    }

    public void stopAnimations() {
        if (animTimer != null) {
            animTimer.stop();
        }
    }

    private void updateTimerState() {
        boolean needsAnim = !activeEmoteId.isEmpty() || !equippedBackId.isEmpty() || "hat_halo".equals(equippedHatId);
        if (needsAnim) {
            if (animTimer == null) {
                animTimer = new AnimationTimer() {
                    private long lastDraw = 0L;
                    @Override
                    public void handle(long now) {
                        if (now - lastDraw >= 22_000_000L) { // ~45 fps smooth preview
                            lastDraw = now;
                            redraw();
                        }
                    }
                };
            }
            animTimer.start();
        } else if (animTimer != null) {
            animTimer.stop();
        }
    }

    public void loadFromPathOrUrl(Path localFile, String fallbackUrl) {
        if (localFile != null && Files.isRegularFile(localFile)) {
            Image img = new Image(localFile.toUri().toString(), false);
            if (!img.isError()) {
                this.skinImage = img;
                redraw();
                return;
            }
        }
        if (fallbackUrl != null && !fallbackUrl.isBlank()) {
            Image remote = new Image(fallbackUrl, true);
            remote.progressProperty().addListener((obs, o, n) -> {
                if (n.doubleValue() >= 1.0 && !remote.isError()) {
                    this.skinImage = remote;
                    redraw();
                }
            });
            if (!remote.isError() && remote.getWidth() >= 64) {
                this.skinImage = remote;
                redraw();
                return;
            }
        }
        this.skinImage = null;
        redraw();
    }

    public void loadCapeFromPathOrUrl(Path localFile, String fallbackUrl) {
        if (localFile != null && Files.isRegularFile(localFile)) {
            Image img = new Image(localFile.toUri().toString(), false);
            if (!img.isError()) {
                this.capeImage = img;
                redraw();
                return;
            }
        }
        if (fallbackUrl != null && !fallbackUrl.isBlank()) {
            Image remote = new Image(fallbackUrl, true);
            remote.progressProperty().addListener((obs, o, n) -> {
                if (n.doubleValue() >= 1.0 && !remote.isError()) {
                    this.capeImage = remote;
                    redraw();
                }
            });
            if (!remote.isError() && remote.getWidth() >= 64) {
                this.capeImage = remote;
                redraw();
                return;
            }
        }
        this.capeImage = null;
        redraw();
    }

    private EmotePose sampleEmotePose() {
        EmotePose p = new EmotePose();
        if (activeEmoteId == null || activeEmoteId.isEmpty()) return p;

        long elapsed = System.currentTimeMillis() - emoteStartMs;
        double duration = 3000.0;
        if (!loopingAnimation && elapsed > duration) {
            activeEmoteId = "";
            return p;
        }
        double sec = (elapsed % 3000L) / 1000.0;
        double radPhase = sec * Math.PI * 2.0;

        switch (activeEmoteId) {
            case "emote_wave" -> {
                p.rArmX = -2.4;
                p.rArmZ = 0.45 + 0.45 * Math.sin(sec * 8.0);
                p.headRotZ = 0.12 * Math.sin(sec * 4.0);
                p.lArmX = 0.15;
            }
            case "emote_salute" -> {
                p.rArmX = -1.95;
                p.rArmY = 0.48;
                p.rArmZ = -0.32;
                p.headRotX = -0.08;
                p.lArmZ = -0.08;
            }
            case "emote_dab" -> {
                p.rArmX = -1.65;
                p.rArmY = 0.45;
                p.rArmZ = 0.72;
                p.lArmX = -1.45;
                p.lArmY = -0.7;
                p.lArmZ = -0.55;
                p.headRotX = 0.55;
                p.headRotY = -0.65;
                p.headRotZ = 0.25;
            }
            case "emote_tpose" -> {
                p.rArmZ = Math.PI / 2.0;
                p.lArmZ = -Math.PI / 2.0;
                p.bodyYOffset = -0.6 - 0.5 * Math.sin(sec * 3.0);
            }
            case "emote_clap" -> {
                double clap = Math.abs(Math.sin(sec * 9.0));
                p.rArmX = -1.25;
                p.lArmX = -1.25;
                p.rArmY = -0.45 + 0.32 * clap;
                p.lArmY = 0.45 - 0.32 * clap;
                p.headRotX = 0.1 * Math.sin(sec * 9.0);
            }
            case "emote_facepalm" -> {
                p.rArmX = -1.95;
                p.rArmY = -0.45;
                p.rArmZ = -0.3;
                p.headRotX = 0.42;
                p.headRotY = 0.22 * Math.sin(sec * 6.0);
            }
            case "emote_backflip" -> {
                double t = (elapsed % 1600L) / 1600.0;
                if (t < 0.75) {
                    double ft = t / 0.75;
                    p.bodyRotX = -ft * Math.PI * 2.0;
                    p.bodyYOffset = -4.5 * Math.sin(ft * Math.PI);
                    p.rArmX = -2.2;
                    p.lArmX = -2.2;
                    p.rLegX = 0.5;
                    p.lLegX = 0.5;
                } else {
                    p.rArmX = -2.5;
                    p.rArmZ = 0.45;
                    p.lArmX = -2.5;
                    p.lArmZ = -0.45;
                }
            }
            case "emote_breakdance" -> {
                p.bodyRotY = sec * 3.2;
                p.bodyYOffset = -1.2 * Math.abs(Math.sin(radPhase * 2.0));
                p.rArmX = -1.3 + 0.9 * Math.sin(sec * 8.0);
                p.rArmZ = 0.7;
                p.lArmX = -1.3 - 0.9 * Math.sin(sec * 8.0);
                p.lArmZ = -0.7;
                p.rLegX = 0.65 * Math.sin(sec * 8.0);
                p.lLegX = -0.65 * Math.sin(sec * 8.0);
                p.rLegZ = 0.25;
                p.lLegZ = -0.25;
                p.headRotX = 0.25 * Math.sin(sec * 6.0);
            }
            default -> {}
        }
        return p;
    }

    public void redraw() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        gc.clearRect(0, 0, w, h);

        // Pedestal ring
        Color accent = ThemeManager.accentColor();
        gc.setFill(Color.rgb(8, 12, 22, 0.72));
        gc.fillOval(w * 0.24, h * 0.86, w * 0.52, h * 0.08);
        gc.setStroke(Color.color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0.55));
        gc.setLineWidth(1.5);
        gc.strokeOval(w * 0.24, h * 0.86, w * 0.52, h * 0.08);

        EmotePose pose = sampleEmotePose();
        List<TexturedQuad> quads = new ArrayList<>();
        int armW = slim ? 3 : 4;
        boolean modern = skinImage != null && skinImage.getHeight() >= 64;
        double pad = 0.35;

        // Head (pivot at neck y = -8)
        addLimbBox(quads, -4, -8, -4, 8, 8, 8, 0, 0, 8, 8, 8, false,
                0, -8, 0, pose.headRotX, pose.headRotY, pose.headRotZ, pose);
        addLimbBox(quads, -4 - pad, -8 - pad, -4 - pad, 8 + pad * 2, 8 + pad * 2, 8 + pad * 2, 32, 0, 8, 8, 8, true,
                0, -8, 0, pose.headRotX, pose.headRotY, pose.headRotZ, pose);

        // 3D Headgear cosmetics
        addHeadCosmeticQuads(quads, pose);

        // Torso
        addLimbBox(quads, -4, -8, -2, 8, 12, 4, 16, 16, 8, 12, 4, false,
                0, 0, 0, 0, 0, 0, pose);
        if (modern) {
            addLimbBox(quads, -4 - pad * 0.7, -8 - pad * 0.7, -2 - pad * 0.7,
                    8 + pad * 1.4, 12 + pad * 1.4, 4 + pad * 1.4, 16, 32, 8, 12, 4, true,
                    0, 0, 0, 0, 0, 0, pose);
        }

        // Right Arm (pivot at shoulder x = -4 - armW/2, y = -6)
        double rShX = -4.0 - armW / 2.0;
        addLimbBox(quads, -armW / 2.0, -2, -2, armW, 12, 4, 40, 16, armW, 12, 4, false,
                rShX, -6, 0, pose.rArmX, pose.rArmY, pose.rArmZ, pose);
        if (modern) {
            addLimbBox(quads, -armW / 2.0 - pad * 0.7, -2 - pad * 0.7, -2 - pad * 0.7,
                    armW + pad * 1.4, 12 + pad * 1.4, 4 + pad * 1.4, 40, 32, armW, 12, 4, true,
                    rShX, -6, 0, pose.rArmX, pose.rArmY, pose.rArmZ, pose);
        }

        // Left Arm (pivot at shoulder x = 4 + armW/2, y = -6)
        double lShX = 4.0 + armW / 2.0;
        int lArmU = modern ? 32 : 40;
        int lArmV = modern ? 48 : 16;
        addLimbBox(quads, -armW / 2.0, -2, -2, armW, 12, 4, lArmU, lArmV, armW, 12, 4, false,
                lShX, -6, 0, pose.lArmX, pose.lArmY, pose.lArmZ, pose);
        if (modern) {
            addLimbBox(quads, -armW / 2.0 - pad * 0.7, -2 - pad * 0.7, -2 - pad * 0.7,
                    armW + pad * 1.4, 12 + pad * 1.4, 4 + pad * 1.4, 48, 48, armW, 12, 4, true,
                    lShX, -6, 0, pose.lArmX, pose.lArmY, pose.lArmZ, pose);
        }

        // Right Leg (pivot at hip x = -2, y = 4)
        addLimbBox(quads, -2, 0, -2, 4, 12, 4, 0, 16, 4, 12, 4, false,
                -2, 4, 0, pose.rLegX, 0, pose.rLegZ, pose);
        if (modern) {
            addLimbBox(quads, -2 - pad * 0.7, -pad * 0.7, -2 - pad * 0.7,
                    4 + pad * 1.4, 12 + pad * 1.4, 4 + pad * 1.4, 0, 32, 4, 12, 4, true,
                    -2, 4, 0, pose.rLegX, 0, pose.rLegZ, pose);
        }

        // Left Leg (pivot at hip x = +2, y = 4)
        addLimbBox(quads, -2, 0, -2, 4, 12, 4, modern ? 16 : 0, modern ? 48 : 16, 4, 12, 4, false,
                2, 4, 0, pose.lLegX, 0, pose.lLegZ, pose);
        if (modern) {
            addLimbBox(quads, -2 - pad * 0.7, -pad * 0.7, -2 - pad * 0.7,
                    4 + pad * 1.4, 12 + pad * 1.4, 4 + pad * 1.4, 0, 48, 4, 12, 4, true,
                    2, 4, 0, pose.lLegX, 0, pose.lLegZ, pose);
        }

        // 3D Back / Wings cosmetics
        addBackCosmeticQuads(quads, pose);

        // Cape
        if (capeImage != null) {
            addCapeBox(quads, capeImage, pose);
        }

        quads.sort(Comparator.comparingDouble(TexturedQuad::depth).reversed()
                .thenComparing(TexturedQuad::overlay));

        double scale = Math.min(w / 24.0, h / 38.0);
        double cx = w / 2.0;
        double cy = h * 0.48;

        for (TexturedQuad q : quads) {
            drawQuad(gc, q, cx, cy, scale);
        }
    }

    private void addHeadCosmeticQuads(List<TexturedQuad> out, EmotePose pose) {
        if (equippedHatId == null || equippedHatId.isEmpty()) return;
        double t = System.currentTimeMillis() / 1000.0;
        switch (equippedHatId) {
            case "hat_halo" -> {
                double bob = Math.sin(t * 3.0) * 0.45;
                Color gold = Color.web("#fcd34d");
                addHeadColoredBox(out, -4.5, -11.5 + bob, -4.5, 9, 1, 1.2, gold, pose);
                addHeadColoredBox(out, -4.5, -11.5 + bob, 3.3, 9, 1, 1.2, gold, pose);
                addHeadColoredBox(out, -4.5, -11.5 + bob, -3.3, 1.2, 1, 6.6, gold, pose);
                addHeadColoredBox(out, 3.3, -11.5 + bob, -3.3, 1.2, 1, 6.6, gold, pose);
            }
            case "hat_crown" -> {
                Color gold = Color.web("#fbbf24");
                Color gem = Color.web("#818cf8");
                addHeadColoredBox(out, -4.3, -9.8, -4.3, 8.6, 2.0, 8.6, gold, pose);
                addHeadColoredBox(out, -4.3, -11.6, -4.3, 1.6, 1.8, 1.6, gold, pose);
                addHeadColoredBox(out, 2.7, -11.6, -4.3, 1.6, 1.8, 1.6, gold, pose);
                addHeadColoredBox(out, -0.8, -12.0, -4.3, 1.6, 2.2, 1.4, gold, pose);
                addHeadColoredBox(out, -4.3, -11.6, 2.7, 1.6, 1.8, 1.6, gold, pose);
                addHeadColoredBox(out, 2.7, -11.6, 2.7, 1.6, 1.8, 1.6, gold, pose);
                addHeadColoredBox(out, -0.9, -9.5, 4.3, 1.8, 1.4, 0.5, gem, pose);
            }
            case "hat_horns" -> {
                Color horn = Color.web("#e11d48");
                Color tip = Color.web("#fb7185");
                addHeadColoredBox(out, -5.2, -10.5, -1.2, 1.5, 3.0, 1.8, horn, pose);
                addHeadColoredBox(out, -6.0, -12.8, -0.8, 1.2, 2.6, 1.4, tip, pose);
                addHeadColoredBox(out, 3.7, -10.5, -1.2, 1.5, 3.0, 1.8, horn, pose);
                addHeadColoredBox(out, 4.8, -12.8, -0.8, 1.2, 2.6, 1.4, tip, pose);
            }
            case "hat_fox_ears" -> {
                Color fur = Color.web("#f97316");
                Color inner = Color.web("#fef3c7");
                addHeadColoredBox(out, -4.2, -11.2, -1.0, 2.6, 3.2, 1.8, fur, pose);
                addHeadColoredBox(out, -3.7, -10.6, 0.3, 1.5, 2.2, 0.8, inner, pose);
                addHeadColoredBox(out, 1.6, -11.2, -1.0, 2.6, 3.2, 1.8, fur, pose);
                addHeadColoredBox(out, 2.2, -10.6, 0.3, 1.5, 2.2, 0.8, inner, pose);
            }
        }
    }

    private void addBackCosmeticQuads(List<TexturedQuad> out, EmotePose pose) {
        if (equippedBackId == null || equippedBackId.isEmpty()) return;
        double t = System.currentTimeMillis() / 1000.0;
        double flap = Math.sin(t * 3.8) * 0.28;

        switch (equippedBackId) {
            case "back_angel_wings" -> {
                Color white = Color.web("#f8fafc");
                Color gold = Color.web("#fde68a");
                for (int side : new int[]{-1, 1}) {
                    double yaw = side * (0.45 + flap);
                    addWingBox(out, side > 0 ? 0.5 : -11.5, -6, -1, 11, 5, 1.1, white, -2.5 * side, -5, -2.6, yaw, pose);
                    addWingBox(out, side > 0 ? 1.5 : -13.5, -1, -0.9, 12, 4, 1.0, gold, -2.5 * side, -5, -2.6, yaw, pose);
                    addWingBox(out, side > 0 ? 1.0 : -9.0, 3, -0.8, 8, 4, 0.9, white, -2.5 * side, -5, -2.6, yaw, pose);
                }
            }
            case "back_dragon_wings" -> {
                Color bone = Color.web("#1e1b2e");
                Color mem = Color.web("#8b5cf6");
                for (int side : new int[]{-1, 1}) {
                    double yaw = side * (0.50 + flap);
                    addWingBox(out, side > 0 ? 0.0 : -13.0, -7, -1.1, 13, 2, 1.3, bone, -2.2 * side, -5, -2.6, yaw, pose);
                    addWingBox(out, side > 0 ? 0.5 : -12.0, -5, -0.9, 11.5, 8, 0.9, mem, -2.2 * side, -5, -2.6, yaw, pose);
                }
            }
            case "back_cyber_jet" -> {
                Color hull = Color.web("#334155");
                Color core = Color.web("#c7d2fe");
                Color flame = Color.web("#67e8f9");
                addLimbColoredBox(out, -4.5, -6, -5.2, 3.2, 8.5, 3.0, hull, pose);
                addLimbColoredBox(out, 1.3, -6, -5.2, 3.2, 8.5, 3.0, hull, pose);
                addLimbColoredBox(out, -1.3, -4.5, -4.6, 2.6, 5.0, 2.2, core, pose);
                addLimbColoredBox(out, -4.0, 2.5, -4.8, 2.2, 2.2 + 0.8 * Math.abs(Math.sin(t * 14)), 2.2, flame, pose);
                addLimbColoredBox(out, 1.8, 2.5, -4.8, 2.2, 2.2 + 0.8 * Math.abs(Math.sin(t * 14)), 2.2, flame, pose);
            }
        }
    }

    private void addHeadColoredBox(List<TexturedQuad> out, double x, double y, double z,
                                   double bw, double bh, double bd, Color c, EmotePose pose) {
        addTransformedColoredBox(out, x, y, z, bw, bh, bd, c, 0, -8, 0, pose.headRotX, pose.headRotY, pose.headRotZ, pose);
    }

    private void addLimbColoredBox(List<TexturedQuad> out, double x, double y, double z,
                                   double bw, double bh, double bd, Color c, EmotePose pose) {
        addTransformedColoredBox(out, x, y, z, bw, bh, bd, c, 0, 0, 0, 0, 0, 0, pose);
    }

    private void addWingBox(List<TexturedQuad> out, double x, double y, double z,
                            double bw, double bh, double bd, Color c,
                            double px, double py, double pz, double ry, EmotePose pose) {
        addTransformedColoredBox(out, x, y, z, bw, bh, bd, c, px, py, pz, 0.12, ry, 0, pose);
    }

    private void addTransformedColoredBox(
            List<TexturedQuad> out,
            double lx, double ly, double lz, double bw, double bh, double bd,
            Color c,
            double pivX, double pivY, double pivZ,
            double rx, double ry, double rz,
            EmotePose pose
    ) {
        Vec3 v000 = transformPoint(lx, ly, lz, pivX, pivY, pivZ, rx, ry, rz, pose);
        Vec3 v100 = transformPoint(lx + bw, ly, lz, pivX, pivY, pivZ, rx, ry, rz, pose);
        Vec3 v110 = transformPoint(lx + bw, ly + bh, lz, pivX, pivY, pivZ, rx, ry, rz, pose);
        Vec3 v010 = transformPoint(lx, ly + bh, lz, pivX, pivY, pivZ, rx, ry, rz, pose);
        Vec3 v001 = transformPoint(lx, ly, lz + bd, pivX, pivY, pivZ, rx, ry, rz, pose);
        Vec3 v101 = transformPoint(lx + bw, ly, lz + bd, pivX, pivY, pivZ, rx, ry, rz, pose);
        Vec3 v111 = transformPoint(lx + bw, ly + bh, lz + bd, pivX, pivY, pivZ, rx, ry, rz, pose);
        Vec3 v011 = transformPoint(lx, ly + bh, lz + bd, pivX, pivY, pivZ, rx, ry, rz, pose);

        tryAddColoredFace(out, v001, v101, v111, v011, 1.0, c);
        tryAddColoredFace(out, v100, v000, v010, v110, 0.85, c);
        tryAddColoredFace(out, v000, v001, v011, v010, 0.9, c);
        tryAddColoredFace(out, v101, v100, v110, v111, 0.9, c);
        tryAddColoredFace(out, v000, v100, v101, v001, 0.97, c);
        tryAddColoredFace(out, v011, v111, v110, v010, 0.75, c);
    }

    private void addLimbBox(
            List<TexturedQuad> out,
            double lx, double ly, double lz,
            double bw, double bh, double bd,
            int tx, int ty, int tw, int th, int td,
            boolean overlay,
            double pivX, double pivY, double pivZ,
            double rx, double ry, double rz,
            EmotePose pose
    ) {
        Vec3 v000 = transformPoint(lx, ly, lz, pivX, pivY, pivZ, rx, ry, rz, pose);
        Vec3 v100 = transformPoint(lx + bw, ly, lz, pivX, pivY, pivZ, rx, ry, rz, pose);
        Vec3 v110 = transformPoint(lx + bw, ly + bh, lz, pivX, pivY, pivZ, rx, ry, rz, pose);
        Vec3 v010 = transformPoint(lx, ly + bh, lz, pivX, pivY, pivZ, rx, ry, rz, pose);

        Vec3 v001 = transformPoint(lx, ly, lz + bd, pivX, pivY, pivZ, rx, ry, rz, pose);
        Vec3 v101 = transformPoint(lx + bw, ly, lz + bd, pivX, pivY, pivZ, rx, ry, rz, pose);
        Vec3 v111 = transformPoint(lx + bw, ly + bh, lz + bd, pivX, pivY, pivZ, rx, ry, rz, pose);
        Vec3 v011 = transformPoint(lx, ly + bh, lz + bd, pivX, pivY, pivZ, rx, ry, rz, pose);

        tryAddFace(out, v001, v101, v111, v011, tx + td, ty + td, tw, th, 1.0, overlay, skinImage);
        tryAddFace(out, v100, v000, v010, v110, tx + td + tw + td, ty + td, tw, th, 0.82, overlay, skinImage);
        tryAddFace(out, v000, v001, v011, v010, tx, ty + td, td, th, 0.88, overlay, skinImage);
        tryAddFace(out, v101, v100, v110, v111, tx + td + tw, ty + td, td, th, 0.88, overlay, skinImage);
        tryAddFace(out, v000, v100, v101, v001, tx + td, ty, tw, td, 0.96, overlay, skinImage);
        tryAddFace(out, v011, v111, v110, v010, tx + td + tw, ty, tw, td, 0.72, overlay, skinImage);
    }

    private Vec3 transformPoint(
            double lx, double ly, double lz,
            double pivX, double pivY, double pivZ,
            double rx, double ry, double rz,
            EmotePose pose
    ) {
        // Rotate local limb around Z, Y, X
        double x = lx, y = ly, z = lz;
        if (rz != 0) {
            double c = Math.cos(rz), s = Math.sin(rz);
            double nx = x * c - y * s;
            double ny = x * s + y * c;
            x = nx; y = ny;
        }
        if (rx != 0) {
            double c = Math.cos(rx), s = Math.sin(rx);
            double ny = y * c - z * s;
            double nz = y * s + z * c;
            y = ny; z = nz;
        }
        if (ry != 0) {
            double c = Math.cos(ry), s = Math.sin(ry);
            double nx = x * c + z * s;
            double nz = -x * s + z * c;
            x = nx; z = nz;
        }
        // Place at limb pivot
        x += pivX;
        y += pivY;
        z += pivZ;

        // Whole-body emote rotation & bounce
        if (pose.bodyRotX != 0) {
            double c = Math.cos(pose.bodyRotX), s = Math.sin(pose.bodyRotX);
            double ny = y * c - z * s;
            double nz = y * s + z * c;
            y = ny; z = nz;
        }
        if (pose.bodyRotY != 0) {
            double c = Math.cos(pose.bodyRotY), s = Math.sin(pose.bodyRotY);
            double nx = x * c + z * s;
            double nz = -x * s + z * c;
            x = nx; z = nz;
        }
        y += pose.bodyYOffset;

        return rotate(x, y, z);
    }

    private void addCapeBox(List<TexturedQuad> out, Image tex, EmotePose pose) {
        double tiltRad = Math.toRadians(12.0);
        double cosT = Math.cos(tiltRad);
        double sinT = Math.sin(tiltRad);
        double pivotY = -8.0;
        double pivotZ = -2.25;

        Vec3 v000 = transformPointCape(-5, 0, -1, pivotY, pivotZ, cosT, sinT, pose);
        Vec3 v100 = transformPointCape(5, 0, -1, pivotY, pivotZ, cosT, sinT, pose);
        Vec3 v110 = transformPointCape(5, 16, -1, pivotY, pivotZ, cosT, sinT, pose);
        Vec3 v010 = transformPointCape(-5, 16, -1, pivotY, pivotZ, cosT, sinT, pose);

        Vec3 v001 = transformPointCape(-5, 0, 0, pivotY, pivotZ, cosT, sinT, pose);
        Vec3 v101 = transformPointCape(5, 0, 0, pivotY, pivotZ, cosT, sinT, pose);
        Vec3 v111 = transformPointCape(5, 16, 0, pivotY, pivotZ, cosT, sinT, pose);
        Vec3 v011 = transformPointCape(-5, 16, 0, pivotY, pivotZ, cosT, sinT, pose);

        tryAddFace(out, v100, v000, v010, v110, 1, 1, 10, 16, 0.96, false, tex);
        tryAddFace(out, v001, v101, v111, v011, 12, 1, 10, 16, 0.78, false, tex);
        tryAddFace(out, v000, v001, v011, v010, 0, 1, 1, 16, 0.85, false, tex);
        tryAddFace(out, v101, v100, v110, v111, 11, 1, 1, 16, 0.85, false, tex);
        tryAddFace(out, v000, v100, v101, v001, 1, 0, 10, 1, 0.95, false, tex);
        tryAddFace(out, v011, v111, v110, v010, 11, 0, 10, 1, 0.72, false, tex);
    }

    private Vec3 transformPointCape(double x, double dy, double dz, double py, double pz, double cosT, double sinT, EmotePose pose) {
        double ry = dy * cosT + dz * sinT;
        double rz = -dy * sinT + dz * cosT;
        return transformPoint(x, py + ry, pz + rz, 0, 0, 0, 0, 0, 0, pose);
    }

    private void tryAddFace(
            List<TexturedQuad> out,
            Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
            int u, int v, int uw, int vh,
            double shade, boolean overlay, Image texture
    ) {
        double nx = (p1.x - p0.x) * (p2.y - p0.y) - (p1.y - p0.y) * (p2.x - p0.x);
        if (nx <= 0) return;
        double avgZ = (p0.z + p1.z + p2.z + p3.z) * 0.25;
        out.add(new TexturedQuad(p0, p1, p2, p3, u, v, uw, vh, shade, avgZ, overlay, texture, null));
    }

    private void tryAddColoredFace(
            List<TexturedQuad> out,
            Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
            double shade, Color color
    ) {
        double nx = (p1.x - p0.x) * (p2.y - p0.y) - (p1.y - p0.y) * (p2.x - p0.x);
        if (nx <= 0) return;
        double avgZ = (p0.z + p1.z + p2.z + p3.z) * 0.25;
        out.add(new TexturedQuad(p0, p1, p2, p3, 0, 0, 1, 1, shade, avgZ, false, null, color));
    }

    private Vec3 rotate(double x, double y, double z) {
        double radY = Math.toRadians(yawDeg);
        double radX = Math.toRadians(pitchDeg);

        double x1 = x * Math.cos(radY) + z * Math.sin(radY);
        double z1 = -x * Math.sin(radY) + z * Math.cos(radY);

        double y2 = y * Math.cos(radX) - z1 * Math.sin(radX);
        double z2 = y * Math.sin(radX) + z1 * Math.cos(radX);

        return new Vec3(x1, y2, -z2);
    }

    private void drawQuad(GraphicsContext gc, TexturedQuad q, double cx, double cy, double scale) {
        double[] xs = new double[4];
        double[] ys = new double[4];
        Vec3[] pts = {q.p0, q.p1, q.p2, q.p3};
        for (int i = 0; i < 4; i++) {
            double persp = 1.0 / (1.0 + pts[i].z * 0.008);
            xs[i] = cx + pts[i].x * scale * persp;
            ys[i] = cy + pts[i].y * scale * persp;
        }

        if (q.flatColor != null) {
            gc.setFill(q.flatColor.deriveColor(0, 1.0, q.shade, 1.0));
            gc.fillPolygon(xs, ys, 4);
            return;
        }

        PixelReader reader = q.texture != null ? q.texture.getPixelReader() : null;
        if (reader == null) {
            if (q.overlay) return;
            gc.setFill(fallbackColor(q.u, q.v, q.shade));
            gc.fillPolygon(xs, ys, 4);
            return;
        }

        int texW = (int) q.texture.getWidth();
        int texH = (int) q.texture.getHeight();

        for (int py = 0; py < q.vh; py++) {
            double v0 = (double) py / q.vh;
            double v1 = (double) (py + 1) / q.vh;
            for (int px = 0; px < q.uw; px++) {
                int sx = Math.min(texW - 1, Math.max(0, q.u + px));
                int sy = Math.min(texH - 1, Math.max(0, q.v + py));
                int argb = reader.getArgb(sx, sy);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha < 10) continue;

                double u0 = (double) px / q.uw;
                double u1 = (double) (px + 1) / q.uw;

                double[] pxXs = {
                        bilerp(xs, u0, v0),
                        bilerp(xs, u1, v0),
                        bilerp(xs, u1, v1),
                        bilerp(xs, u0, v1)
                };
                double[] pxYs = {
                        bilerp(ys, u0, v0),
                        bilerp(ys, u1, v0),
                        bilerp(ys, u1, v1),
                        bilerp(ys, u0, v1)
                };

                int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * q.shade));
                int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * q.shade));
                int b = Math.min(255, (int) ((argb & 0xFF) * q.shade));
                gc.setFill(Color.rgb(r, g, b, alpha / 255.0));
                gc.fillPolygon(pxXs, pxYs, 4);
            }
        }
    }

    private static double bilerp(double[] c, double u, double v) {
        double top = c[0] * (1.0 - u) + c[1] * u;
        double bot = c[3] * (1.0 - u) + c[2] * u;
        return top * (1.0 - v) + bot * v;
    }

    private static Color fallbackColor(int u, int v, double shade) {
        Color base;
        if (v < 16) {
            base = Color.web("#d8a684");
        } else if (u >= 16 && u < 40) {
            base = Color.web("#3898a8");
        } else if (u >= 40) {
            base = Color.web("#d8a684");
        } else {
            base = Color.web("#3e4785");
        }
        return base.deriveColor(0, 1.0, shade, 1.0);
    }
}
