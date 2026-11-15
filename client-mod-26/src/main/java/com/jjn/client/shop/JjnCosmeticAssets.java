package com.jjn.client.shop;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Generates and caches procedural Shop Cape textures and shaded 3D cosmetic textures/parts for MC 26.2.
 */
public final class JjnCosmeticAssets {

    private static final Map<String, ClientAsset.Texture> CAPE_CACHE = new HashMap<>();
    private static final Map<String, Identifier> MATERIAL_CACHE = new HashMap<>();

    private JjnCosmeticAssets() {}

    public static ClientAsset.Texture getShopCapeAsset(Minecraft mc, String capeId) {
        if (mc == null || capeId == null || capeId.isBlank()) return null;
        if (JjnEconomyManager.findItem(capeId) == null) return null;
        ClientAsset.Texture existing = CAPE_CACHE.get(capeId);
        if (existing != null) return existing;

        NativeImage img = new NativeImage(NativeImage.Format.RGBA, 64, 32, true);
        for (int py = 0; py < 18; py++) {
            for (int px = 0; px < 22; px++) {
                boolean isFront = px >= 1 && px <= 10 && py >= 1 && py <= 16;
                boolean isInner = px >= 12 && px <= 21 && py >= 1 && py <= 16;
                if (!isFront && !isInner && !(py == 0 && px >= 1 && px <= 21) && !(px == 0 || px == 11)) {
                    continue;
                }
                int u = isFront ? (px - 1) : (isInner ? (px - 12) : 0);
                int v = Math.max(0, Math.min(15, py - 1));
                int argb = sampleCapePixel(capeId, u, v, isFront);
                img.setPixel(px, py, argb);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath("jjnclient", "shop_" + capeId);
        mc.getTextureManager().register(id, new DynamicTexture(() -> "jjn_" + capeId, img));
        ClientAsset.Texture asset = new ClientAsset.ResourceTexture(id, id);
        CAPE_CACHE.put(capeId, asset);
        return asset;
    }

    public static Identifier materialTexture(Minecraft mc, String key, int argb) {
        Identifier cached = MATERIAL_CACHE.get(key);
        if (cached != null) return cached;

        NativeImage img = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
        int baseR = (argb >> 16) & 0xFF;
        int baseG = (argb >> 8) & 0xFF;
        int baseB = argb & 0xFF;
        int alpha = (argb >>> 24) & 0xFF;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                // Subtle pixel shading so 3D blocks have rich Minecraft texture depth
                int edge = (x == 0 || y == 0 || x == 15 || y == 15) ? -12 : ((x + y) % 5 == 0 ? 8 : 0);
                int r = Math.max(0, Math.min(255, baseR + edge));
                int g = Math.max(0, Math.min(255, baseG + edge));
                int b = Math.max(0, Math.min(255, baseB + edge));
                img.setPixel(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath("jjnclient", "cosmetic_mat_" + key);
        mc.getTextureManager().register(id, new DynamicTexture(() -> "jjn_mat_" + key, img));
        MATERIAL_CACHE.put(key, id);
        return id;
    }

    public static ModelPart singleBoxPart(float x, float y, float z, float w, float h, float d) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(
                "box",
                CubeListBuilder.create().texOffs(0, 0).addBox(x, y, z, w, h, d),
                PartPose.ZERO
        );
        return LayerDefinition.create(mesh, 16, 16).bakeRoot();
    }

    private static int sampleCapePixel(String capeId, int u, int v, boolean isFront) {
        boolean border = (u == 0 || u == 9 || v == 0 || v == 15);
        double t = v / 15.0;
        return switch (capeId) {
            case "cape_jjn_nova" -> {
                if (border) yield 0xFFC7D2FE;
                if (isFront && isCreeperEmblem(u - 2, v - 4)) yield 0xFFE0E7FF;
                yield lerpArgb(0xFF141824, 0xFF4F46E5, t * 0.85);
            }
            case "cape_aurora" -> {
                if (border) yield 0xFF67E8F9;
                double wave = 0.5 + 0.5 * Math.sin((u * 0.65) + (v * 0.45));
                int base = lerpArgb(0xFF0F172A, 0xFF06B6D4, t);
                yield lerpArgb(base, 0xFF34D399, wave * 0.65);
            }
            case "cape_crimson_dragon" -> {
                if (border) yield 0xFFFBBF24;
                if (isFront && ((u == 4 || u == 5) && v >= 3 && v <= 12)) yield 0xFFF59E0B;
                boolean scale = ((u + v) % 2 == 0);
                yield scale ? lerpArgb(0xFF7F1D1D, 0xFFEF4444, t * 0.6) : 0xFF1C1917;
            }
            case "cape_golden_monarch" -> {
                if (border) yield 0xFFFCD34D;
                if (isFront && isCrownEmblem(u - 2, v - 5)) yield 0xFFFDE047;
                yield lerpArgb(0xFF3B0764, 0xFF6B21A8, t);
            }
            default -> 0xFF1F2937;
        };
    }

    private static int lerpArgb(int c0, int c1, double t) {
        double ct = Math.max(0.0, Math.min(1.0, t));
        int r0 = (c0 >> 16) & 0xFF, g0 = (c0 >> 8) & 0xFF, b0 = c0 & 0xFF;
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r = (int) Math.round(r0 + (r1 - r0) * ct);
        int g = (int) Math.round(g0 + (g1 - g0) * ct);
        int b = (int) Math.round(b0 + (b1 - b0) * ct);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static boolean isCreeperEmblem(int x, int y) {
        if (x < 0 || x >= 6 || y < 0 || y >= 7) return false;
        if ((x <= 1 || x >= 4) && y <= 1) return true;
        if ((x == 2 || x == 3) && (y == 2 || y == 3)) return true;
        if ((x >= 1 && x <= 4) && y == 4) return true;
        return (x == 1 || x == 4) && (y == 5 || y == 6);
    }

    private static boolean isCrownEmblem(int x, int y) {
        if (x < 0 || x >= 6 || y < 0 || y >= 5) return false;
        if (y >= 3) return true;
        if (y == 1 && (x == 0 || x == 2 || x == 3 || x == 5)) return true;
        if (y == 2 && x != 1 && x != 4) return true;
        return y == 0 && (x == 0 || x == 5);
    }
}
