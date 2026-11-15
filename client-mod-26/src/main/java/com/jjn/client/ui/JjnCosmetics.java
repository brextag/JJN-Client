package com.jjn.client.ui;

import com.jjn.client.JjnClientMod;
import com.jjn.client.JjnConfig;
import com.jjn.client.friends.JjnFriendsManager;
import com.jjn.client.shop.JjnCosmeticAssets;
import com.jjn.client.shop.JjnEconomyManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared client-side cosmetic helpers for JJN Client (26.2):
 * custom Creeper badge icon, font glyph prefixing, and local skin/cape texture caching.
 */
public final class JjnCosmetics {

    public static final Identifier CREEPER_GUI_ICON = Identifier.fromNamespaceAndPath("jjnclient", "textures/gui/jjn_icon.png");
    public static final Identifier CREEPER_FONT_ID = Identifier.fromNamespaceAndPath("jjnclient", "icons");
    public static final String CREEPER_GLYPH = "\uE001";

    public static final Identifier CUSTOM_SKIN_ID = Identifier.fromNamespaceAndPath("jjnclient", "custom_skin");
    public static final Identifier CUSTOM_CAPE_ID = Identifier.fromNamespaceAndPath("jjnclient", "custom_cape");

    // Shared preview cape/cosmetics hook for GuiSkinRendererMixin
    public static Model.Simple activePreviewBodyModel;
    public static Model.Simple activePreviewCapeModel;
    public static Identifier activePreviewCapeTexture;

    private static long cachedSkinMtime = Long.MIN_VALUE;
    private static ClientAsset.Texture cachedSkinAsset;

    private static long cachedCapeMtime = Long.MIN_VALUE;
    private static ClientAsset.Texture cachedCapeAsset;

    private JjnCosmetics() {}

    /**
     * Prefixes any player display/tab name component with the JJN Creeper glyph.
     * Safe to invoke on common/server threads because it uses only chat/identifier primitives.
     */
    public static Component prefixWithIcon(Component original) {
        if (original == null) {
            return null;
        }
        if (original.getString().contains(CREEPER_GLYPH)) {
            return original;
        }
        Component icon = Component.literal(CREEPER_GLYPH).withStyle(style -> style
                .withFont(new FontDescription.Resource(CREEPER_FONT_ID))
                .withColor(0xFFFFFF));
        return Component.empty().append(icon).append(Component.literal(" ")).append(original);
    }

    public static ClientAsset.Texture getCustomSkinAsset(Minecraft mc) {
        if (mc == null) {
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
            if (cachedSkinAsset != null && cachedSkinMtime == mtime) {
                return cachedSkinAsset;
            }
            try (var in = Files.newInputStream(file)) {
                NativeImage image = NativeImage.read(in);
                DynamicTexture tex = new DynamicTexture(() -> "jjn_custom_skin", image);
                mc.getTextureManager().register(CUSTOM_SKIN_ID, tex);
                cachedSkinAsset = new ClientAsset.ResourceTexture(CUSTOM_SKIN_ID, CUSTOM_SKIN_ID);
                cachedSkinMtime = mtime;
                return cachedSkinAsset;
            }
        } catch (Exception ex) {
            JjnClientMod.LOGGER.warn("Could not load local custom skin", ex);
            return null;
        }
    }

    public static ClientAsset.Texture getCustomCapeAssetForPlayer(Minecraft mc, AbstractClientPlayer player) {
        if (mc == null || player == null) {
            return null;
        }
        if (mc.player == player) {
            return getCustomCapeAsset(mc);
        }
        String name = player.getGameProfile() != null ? player.getGameProfile().name() : "";
        JjnFriendsManager.SyncedCosmetics sc = JjnFriendsManager.get().getCosmeticsForPlayer(player.getUUID(), name);
        if (sc.capeId() != null && !sc.capeId().isBlank()) {
            return JjnCosmeticAssets.getShopCapeAsset(mc, sc.capeId());
        }
        return null;
    }

    public static ClientAsset.Texture getCustomCapeAsset(Minecraft mc) {
        if (mc == null) {
            return null;
        }
        String shopCape = JjnEconomyManager.get().snapshot().equippedCapeId;
        if (shopCape != null && !shopCape.isBlank()) {
            ClientAsset.Texture shopAsset = JjnCosmeticAssets.getShopCapeAsset(mc, shopCape);
            if (shopAsset != null) {
                return shopAsset;
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
            if (cachedCapeAsset != null && cachedCapeMtime == mtime) {
                return cachedCapeAsset;
            }
            try (var in = Files.newInputStream(file)) {
                NativeImage image = NativeImage.read(in);
                DynamicTexture tex = new DynamicTexture(() -> "jjn_custom_cape", image);
                mc.getTextureManager().register(CUSTOM_CAPE_ID, tex);
                cachedCapeAsset = new ClientAsset.ResourceTexture(CUSTOM_CAPE_ID, CUSTOM_CAPE_ID);
                cachedCapeMtime = mtime;
                return cachedCapeAsset;
            }
        } catch (Exception ex) {
            JjnClientMod.LOGGER.warn("Could not load local custom cape", ex);
            return null;
        }
    }
}
