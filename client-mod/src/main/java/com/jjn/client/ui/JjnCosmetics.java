package com.jjn.client.ui;

import com.jjn.client.JjnClientMod;
import com.jjn.client.JjnConfig;
import com.jjn.client.shop.JjnCosmeticAssets;
import com.jjn.client.shop.JjnEconomyManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared client-side cosmetic helpers for JJN Client (1.21.4):
 * custom Creeper badge icon, font glyph prefixing, and local skin/cape texture caching.
 */
public final class JjnCosmetics {

    public static final Identifier CREEPER_GUI_ICON = Identifier.of("jjnclient", "textures/gui/jjn_icon.png");
    public static final Identifier CREEPER_FONT_ID = Identifier.of("jjnclient", "icons");
    public static final String CREEPER_GLYPH = "\uE001";

    public static final Identifier CUSTOM_SKIN_ID = Identifier.of("jjnclient", "custom_skin");
    public static final Identifier CUSTOM_CAPE_ID = Identifier.of("jjnclient", "custom_cape");

    private static long cachedSkinMtime = Long.MIN_VALUE;
    private static Identifier cachedSkinId;

    private static long cachedCapeMtime = Long.MIN_VALUE;
    private static Identifier cachedCapeId;

    private JjnCosmetics() {}

    /**
     * Prefixes any player display/tab name text with the JJN Creeper glyph.
     * Safe on common/server threads because it uses only Text/Identifier primitives.
     */
    public static Text prefixWithIcon(Text original) {
        if (original == null) {
            return null;
        }
        if (original.getString().contains(CREEPER_GLYPH)) {
            return original;
        }
        Text icon = Text.literal(CREEPER_GLYPH).styled(style -> style
                .withFont(CREEPER_FONT_ID)
                .withColor(0xFFFFFF));
        return Text.empty().append(icon).append(Text.literal(" ")).append(original);
    }

    public static Identifier getCustomSkinId(MinecraftClient client) {
        if (client == null) {
            return null;
        }
        JjnConfig cfg = JjnConfig.get();
        if (!cfg.localSkinEnabled) {
            return null;
        }
        Path file = JjnConfig.localSkinFile();
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            long mtime = Files.getLastModifiedTime(file).toMillis();
            if (cachedSkinId != null && cachedSkinMtime == mtime) {
                return cachedSkinId;
            }
            try (var in = Files.newInputStream(file)) {
                NativeImage image = NativeImage.read(in);
                NativeImageBackedTexture tex = new NativeImageBackedTexture(image);
                client.getTextureManager().registerTexture(CUSTOM_SKIN_ID, tex);
                cachedSkinId = CUSTOM_SKIN_ID;
                cachedSkinMtime = mtime;
                return cachedSkinId;
            }
        } catch (Exception ex) {
            JjnClientMod.LOGGER.warn("Could not load local custom skin", ex);
            return null;
        }
    }

    public static Identifier getCustomCapeId(MinecraftClient client) {
        if (client == null) {
            return null;
        }
        String shopCape = JjnEconomyManager.get().snapshot().equippedCapeId;
        if (shopCape != null && !shopCape.isBlank()) {
            Identifier shopId = JjnCosmeticAssets.getShopCapeId(client, shopCape);
            if (shopId != null) {
                return shopId;
            }
        }
        JjnConfig cfg = JjnConfig.get();
        if (!cfg.localCapeEnabled) {
            return null;
        }
        Path file = JjnConfig.localCapeFile();
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            long mtime = Files.getLastModifiedTime(file).toMillis();
            if (cachedCapeId != null && cachedCapeMtime == mtime) {
                return cachedCapeId;
            }
            try (var in = Files.newInputStream(file)) {
                NativeImage image = NativeImage.read(in);
                NativeImageBackedTexture tex = new NativeImageBackedTexture(image);
                client.getTextureManager().registerTexture(CUSTOM_CAPE_ID, tex);
                cachedCapeId = CUSTOM_CAPE_ID;
                cachedCapeMtime = mtime;
                return cachedCapeId;
            }
        } catch (Exception ex) {
            JjnClientMod.LOGGER.warn("Could not load local custom cape", ex);
            return null;
        }
    }
}
