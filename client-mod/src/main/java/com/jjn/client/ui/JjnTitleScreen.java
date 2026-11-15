package com.jjn.client.ui;

import com.jjn.client.JjnClientMod;
import com.jjn.client.JjnConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

/**
 * Custom JJN Client front screen replacing the vanilla TitleScreen in Minecraft 1.21.4,
 * featuring an interactive 3D player + cape model preview on the left side.
 */
public class JjnTitleScreen extends Screen {

    private long openedAtMs = System.currentTimeMillis();
    private boolean showShowcasePanel;
    private int showcaseX;
    private int showcaseW;
    private int cardX;
    private int cardY;
    private int cardW;
    private int cardH;

    public JjnTitleScreen() {
        super(Text.literal("JJN Client"));
    }

    @Override
    protected void init() {
        this.openedAtMs = System.currentTimeMillis();
        JjnConfig cfg = JjnConfig.get();
        cfg.load();
        com.jjn.client.shop.JjnEconomyManager.get().reloadFromDisk();
        JjnClientMod.LOGGER.info("JJN Client custom title screen active (width={}, height={})", this.width, this.height);

        this.showShowcasePanel = cfg.showTitlePreview && this.width >= 370 && this.client != null;
        this.cardH = Math.min(218, this.height - 28);
        this.cardY = (this.height - this.cardH) / 2;

        if (showShowcasePanel) {
            this.showcaseW = Math.min(138, Math.max(98, this.width / 4));
            int gap = 14;
            this.cardW = Math.min(266, Math.max(210, this.width - this.showcaseW - gap - 28));
            int totalW = this.showcaseW + gap + this.cardW;
            this.showcaseX = (this.width - totalW) / 2;
            this.cardX = this.showcaseX + this.showcaseW + gap;

            Supplier<SkinTextures> skinSupplier = resolveTitleSkinSupplier(cfg);
            int modelW = this.showcaseW - 14;
            int modelH = this.cardH - 56;
            JjnPlayerSkinWidget skinWidget = new JjnPlayerSkinWidget(
                    modelW,
                    modelH,
                    this.client.getLoadedEntityModels(),
                    skinSupplier
            );
            skinWidget.setX(this.showcaseX + 7);
            skinWidget.setY(this.cardY + 34);
            this.addDrawableChild(skinWidget);
        } else {
            this.cardW = Math.min(280, this.width - 28);
            this.cardX = (this.width - this.cardW) / 2;
        }

        int btnWidth = Math.min(220, this.cardW - 32);
        int btnHeight = 25;
        int gap = 7;
        int centerX = this.cardX + (this.cardW - btnWidth) / 2;
        int startY = this.cardY + 74;

        this.addDrawableChild(new JjnButton(
                centerX, startY, btnWidth, btnHeight,
                Text.literal("SINGLEPLAYER"),
                btn -> this.client.setScreen(new SelectWorldScreen(this)),
                true
        ));

        int halfWidth = (btnWidth - 8) / 2;
        int row2Y = startY + (btnHeight + gap);

        this.addDrawableChild(new JjnButton(
                centerX, row2Y, halfWidth, btnHeight,
                Text.literal("MULTIPLAYER"),
                btn -> this.client.setScreen(new MultiplayerScreen(this))
        ));

        this.addDrawableChild(new JjnButton(
                centerX + halfWidth + 8, row2Y, halfWidth, btnHeight,
                Text.literal("FRIENDS"),
                btn -> this.client.setScreen(new JjnFriendsScreen(this))
        ));

        int modsRowY = startY + (btnHeight + gap) * 2;

        this.addDrawableChild(new JjnButton(
                centerX, modsRowY, halfWidth, btnHeight,
                Text.literal("JJN MODS"),
                btn -> this.client.setScreen(new JjnModMenuScreen(this, JjnModMenuScreen.Tab.MODULES))
        ));

        this.addDrawableChild(new JjnButton(
                centerX + halfWidth + 8, modsRowY, halfWidth, btnHeight,
                Text.literal("MOD TABS"),
                btn -> this.client.setScreen(new JjnModMenuScreen(this, JjnModMenuScreen.Tab.MODS))
        ));

        int bottomRowY = startY + (btnHeight + gap) * 3;

        this.addDrawableChild(new JjnButton(
                centerX, bottomRowY, halfWidth, btnHeight,
                Text.literal("OPTIONS"),
                btn -> this.client.setScreen(new OptionsScreen(this, this.client.options))
        ));

        this.addDrawableChild(new JjnButton(
                centerX + halfWidth + 8, bottomRowY, halfWidth, btnHeight,
                Text.literal("QUIT"),
                btn -> this.client.scheduleStop()
        ));
    }

    private Supplier<SkinTextures> resolveTitleSkinSupplier(JjnConfig cfg) {
        Identifier capeId = JjnCosmetics.getCustomCapeId(this.client);
        Identifier skinId = JjnCosmetics.getCustomSkinId(this.client);

        if (skinId != null) {
            SkinTextures.Model modelType = "slim".equalsIgnoreCase(cfg.skinVariant)
                    ? SkinTextures.Model.SLIM
                    : SkinTextures.Model.WIDE;
            SkinTextures customSkin = new SkinTextures(
                    skinId,
                    null,
                    capeId,
                    null,
                    modelType,
                    false
            );
            return () -> customSkin;
        }

        var profile = this.client.getGameProfile();
        Supplier<SkinTextures> base = profile != null
                ? this.client.getSkinProvider().getSkinTexturesSupplier(profile)
                : DefaultSkinHelper::getSteve;

        if (capeId != null) {
            Identifier finalCape = capeId;
            return () -> {
                SkinTextures s = base.get();
                return new SkinTextures(s.texture(), s.textureUrl(), finalCape, s.elytraTexture(), s.model(), s.secure());
            };
        }
        return base;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackdrop(context);
        if (showShowcasePanel) {
            renderShowcasePanel(context);
        }
        renderCardAndBranding(context);
        super.render(context, mouseX, mouseY, delta);
        renderFooter(context);
    }

    private void renderBackdrop(DrawContext context) {
        // Dark slate-gray gradient matching JJN Launcher (#101116 -> #15171e)
        context.fillGradient(0, 0, this.width, this.height, 0xFF101116, 0xFF15171E);

        // Subtle #c7d2fe top ambient glow
        long elapsed = System.currentTimeMillis() - openedAtMs;
        double pulse = 0.5 + 0.5 * Math.sin(elapsed / 1100.0);
        int glowAlpha = (int) (18 + pulse * 18);
        int glowColor = (glowAlpha << 24) | 0xC7D2FE;
        context.fillGradient(0, 0, this.width, this.height / 3, glowColor, 0x00101116);

        // Subtle architectural grid
        if (JjnConfig.get().titleScreenGrid) {
            int gridColor = 0x0BFFFFFF;
            int step = 36;
            for (int x = 0; x < this.width; x += step) {
                context.fill(x, 0, x + 1, this.height, gridColor);
            }
            for (int y = 0; y < this.height; y += step) {
                context.fill(0, y, this.width, y + 1, gridColor);
            }
        }
    }

    private void renderShowcasePanel(DrawContext context) {
        int x = this.showcaseX;
        int y = this.cardY;
        int w = this.showcaseW;
        int h = this.cardH;

        context.fill(x, y, x + w, y + h, 0xE6191B23);
        context.fill(x, y, x + w, y + 2, 0xFFC7D2FE);
        context.drawBorder(x, y, w, h, 0xFF353949);

        String username = this.client != null && this.client.getSession() != null
                ? clientNameSafe(this.client.getSession().getUsername())
                : "Player";
        int iconSize = 9;
        int nameW = this.textRenderer.getWidth(username);
        int totalHeaderW = iconSize + 4 + nameW;
        int headerX = x + (w - totalHeaderW) / 2;
        context.drawTexture(RenderLayer::getGuiTextured, JjnCosmetics.CREEPER_GUI_ICON, headerX, y + 9, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
        context.drawTextWithShadow(this.textRenderer, username, headerX + iconSize + 4, y + 10, 0xFFFFFFFF);

        String profileTag = "Profile: " + JjnConfig.get().profileName;
        if (this.textRenderer.getWidth(profileTag) > w - 12) {
            profileTag = JjnConfig.get().profileName;
        }
        context.drawCenteredTextWithShadow(this.textRenderer, profileTag, x + w / 2, y + 22, 0xFFC7D2FE);

        // Subtle pedestal floor under the 3D character
        context.fill(x + 14, y + h - 24, x + w - 14, y + h - 22, 0x44C7D2FE);
        context.drawCenteredTextWithShadow(this.textRenderer, "Drag to rotate 3D", x + w / 2, y + h - 15, 0xFF85899A);
    }

    private void renderCardAndBranding(DrawContext context) {
        int cx = this.cardX;
        int cy = this.cardY;
        int cw = this.cardW;
        int ch = this.cardH;
        int cardCenterX = cx + cw / 2;

        // Card panel (#202432 / #191b23) with #353949 border and #c7d2fe top accent
        context.fill(cx, cy, cx + cw, cy + ch, 0xE6191B23);
        context.fill(cx, cy, cx + cw, cy + 2, 0xFFC7D2FE);
        context.drawBorder(cx, cy, cw, ch, 0xFF353949);

        var matrices = context.getMatrices();
        matrices.push();
        float scale = 2.6f;
        matrices.scale(scale, scale, 1.0f);
        String title = "JJN";
        int titleW = this.textRenderer.getWidth(title);
        float scaledCenterX = cardCenterX / scale - titleW / 2.0f;
        float scaledY = (cy + 16) / scale;
        context.drawTextWithShadow(this.textRenderer, title, (int) scaledCenterX, (int) scaledY, 0xFFFFFFFF);
        matrices.pop();

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                "CLIENT  •  MINECRAFT 1.21.4",
                cardCenterX,
                cy + 44,
                0xFFC7D2FE
        );

        String username = this.client != null && this.client.getSession() != null
                ? clientNameSafe(this.client.getSession().getUsername())
                : "Player";
        String prefix = "Signed in: ";
        int iconSize = 9;
        int badgeW = this.textRenderer.getWidth(prefix) + iconSize + 4 + this.textRenderer.getWidth(username) + 16;
        context.fill(12, 12, 12 + badgeW, 30, 0xCC20222C);
        context.fill(12, 12, 14, 30, 0xFFC7D2FE);
        context.drawTextWithShadow(this.textRenderer, prefix, 20, 17, 0xFFE5E7EB);
        int iconX = 20 + this.textRenderer.getWidth(prefix);
        context.drawTexture(RenderLayer::getGuiTextured, JjnCosmetics.CREEPER_GUI_ICON, iconX, 16, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
        context.drawTextWithShadow(this.textRenderer, username, iconX + iconSize + 4, 17, 0xFFFFFFFF);
    }

    private static String clientNameSafe(String raw) {
        return raw == null || raw.isBlank() ? "Player" : raw;
    }

    private void renderFooter(DrawContext context) {
        String left = "JJN Client v" + JjnClientMod.VERSION + "  (Press Right-Shift in-game for Mods)";
        context.drawTextWithShadow(this.textRenderer, left, 12, this.height - 18, 0xFF85899A);

        String right = "Minecraft 1.21.4 / Fabric";
        int rightW = this.textRenderer.getWidth(right);
        context.drawTextWithShadow(this.textRenderer, right, this.width - rightW - 12, this.height - 18, 0xFF85899A);
    }
}
