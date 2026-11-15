package com.jjn.client.ui;

import com.jjn.client.JjnClientMod;
import com.jjn.client.JjnConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.function.Supplier;

/**
 * Custom JJN Client front screen replacing the vanilla TitleScreen in Minecraft 26.2,
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
        super(Component.literal("JJN Client"));
    }

    @Override
    protected void init() {
        this.openedAtMs = System.currentTimeMillis();
        JjnConfig cfg = JjnConfig.get();
        cfg.load();
        com.jjn.client.shop.JjnEconomyManager.get().reloadFromDisk();
        JjnClientMod.LOGGER.info("JJN Client custom title screen active (width={}, height={})", this.width, this.height);

        this.showShowcasePanel = cfg.showTitlePreview && this.width >= 370 && this.minecraft != null;
        this.cardH = Math.min(218, this.height - 28);
        this.cardY = (this.height - this.cardH) / 2;

        if (showShowcasePanel) {
            this.showcaseW = Math.min(138, Math.max(98, this.width / 4));
            int gap = 14;
            this.cardW = Math.min(266, Math.max(210, this.width - this.showcaseW - gap - 28));
            int totalW = this.showcaseW + gap + this.cardW;
            this.showcaseX = (this.width - totalW) / 2;
            this.cardX = this.showcaseX + this.showcaseW + gap;

            Supplier<PlayerSkin> skinSupplier = resolveTitleSkinSupplier(cfg);
            int modelW = this.showcaseW - 14;
            int modelH = this.cardH - 56;
            JjnPlayerSkinWidget skinWidget = new JjnPlayerSkinWidget(
                    modelW,
                    modelH,
                    this.minecraft.getEntityModels(),
                    skinSupplier
            );
            skinWidget.setX(this.showcaseX + 7);
            skinWidget.setY(this.cardY + 34);
            this.addRenderableWidget(skinWidget);
        } else {
            this.cardW = Math.min(280, this.width - 28);
            this.cardX = (this.width - this.cardW) / 2;
        }

        int btnWidth = Math.min(220, this.cardW - 32);
        int btnHeight = 25;
        int gap = 7;
        int centerX = this.cardX + (this.cardW - btnWidth) / 2;
        int startY = this.cardY + 74;

        this.addRenderableWidget(new JjnButton(
                centerX, startY, btnWidth, btnHeight,
                Component.literal("SINGLEPLAYER"),
                btn -> this.minecraft.gui.setScreen(new SelectWorldScreen(this)),
                true
        ));

        int halfWidth = (btnWidth - 8) / 2;
        int row2Y = startY + (btnHeight + gap);

        this.addRenderableWidget(new JjnButton(
                centerX, row2Y, halfWidth, btnHeight,
                Component.literal("MULTIPLAYER"),
                btn -> this.minecraft.gui.setScreen(new JoinMultiplayerScreen(this))
        ));

        this.addRenderableWidget(new JjnButton(
                centerX + halfWidth + 8, row2Y, halfWidth, btnHeight,
                Component.literal("FRIENDS"),
                btn -> this.minecraft.gui.setScreen(new JjnFriendsScreen(this))
        ));

        int modsRowY = startY + (btnHeight + gap) * 2;

        this.addRenderableWidget(new JjnButton(
                centerX, modsRowY, halfWidth, btnHeight,
                Component.literal("JJN MODS"),
                btn -> this.minecraft.gui.setScreen(new JjnModMenuScreen(this, JjnModMenuScreen.Tab.MODULES))
        ));

        this.addRenderableWidget(new JjnButton(
                centerX + halfWidth + 8, modsRowY, halfWidth, btnHeight,
                Component.literal("MOD TABS"),
                btn -> this.minecraft.gui.setScreen(new JjnModMenuScreen(this, JjnModMenuScreen.Tab.MODS))
        ));

        int bottomRowY = startY + (btnHeight + gap) * 3;

        this.addRenderableWidget(new JjnButton(
                centerX, bottomRowY, halfWidth, btnHeight,
                Component.literal("OPTIONS"),
                btn -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, false))
        ));

        this.addRenderableWidget(new JjnButton(
                centerX + halfWidth + 8, bottomRowY, halfWidth, btnHeight,
                Component.literal("QUIT"),
                btn -> this.minecraft.stop()
        ));
    }

    private Supplier<PlayerSkin> resolveTitleSkinSupplier(JjnConfig cfg) {
        ClientAsset.Texture capeAsset = JjnCosmetics.getCustomCapeAsset(this.minecraft);
        ClientAsset.Texture skinAsset = JjnCosmetics.getCustomSkinAsset(this.minecraft);

        if (skinAsset != null) {
            PlayerModelType modelType = "slim".equalsIgnoreCase(cfg.skinVariant)
                    ? PlayerModelType.SLIM
                    : PlayerModelType.WIDE;
            PlayerSkin customSkin = PlayerSkin.insecure(
                    skinAsset,
                    capeAsset,
                    null,
                    modelType
            );
            return () -> customSkin;
        }

        var profile = this.minecraft.getGameProfile();
        Supplier<PlayerSkin> base = profile != null
                ? this.minecraft.getSkinManager().createLookup(profile, false)
                : DefaultPlayerSkin::getDefaultSkin;

        if (capeAsset != null) {
            ClientAsset.Texture finalCape = capeAsset;
            return () -> {
                PlayerSkin s = base.get();
                return new PlayerSkin(s.body(), finalCape, s.elytra(), s.model(), s.secure());
            };
        }
        return base;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        renderBackdrop(graphics);
        if (showShowcasePanel) {
            renderShowcasePanel(graphics);
        }
        renderCardAndBranding(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        renderFooter(graphics);
    }

    private void renderBackdrop(GuiGraphicsExtractor graphics) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xFF101116, 0xFF15171E);

        long elapsed = System.currentTimeMillis() - openedAtMs;
        double pulse = 0.5 + 0.5 * Math.sin(elapsed / 1100.0);
        int glowAlpha = (int) (18 + pulse * 18);
        int glowColor = (glowAlpha << 24) | 0xC7D2FE;
        graphics.fillGradient(0, 0, this.width, this.height / 3, glowColor, 0x00101116);

        if (JjnConfig.get().titleScreenGrid) {
            int gridColor = 0x0BFFFFFF;
            int step = 36;
            for (int x = 0; x < this.width; x += step) {
                graphics.fill(x, 0, x + 1, this.height, gridColor);
            }
            for (int y = 0; y < this.height; y += step) {
                graphics.fill(0, y, this.width, y + 1, gridColor);
            }
        }
    }

    private void renderShowcasePanel(GuiGraphicsExtractor graphics) {
        int x = this.showcaseX;
        int y = this.cardY;
        int w = this.showcaseW;
        int h = this.cardH;

        graphics.fill(x, y, x + w, y + h, 0xE6191B23);
        graphics.fill(x, y, x + w, y + 2, 0xFFC7D2FE);
        graphics.outline(x, y, w, h, 0xFF353949);

        String username = this.minecraft != null && this.minecraft.getUser() != null
                ? this.minecraft.getUser().getName()
                : "Player";
        int iconSize = 9;
        int nameW = this.font.width(username);
        int totalHeaderW = iconSize + 4 + nameW;
        int headerX = x + (w - totalHeaderW) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, JjnCosmetics.CREEPER_GUI_ICON, headerX, y + 9, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
        graphics.text(this.font, username, headerX + iconSize + 4, y + 10, 0xFFFFFFFF, true);

        String profileTag = "Profile: " + JjnConfig.get().profileName;
        if (this.font.width(profileTag) > w - 12) {
            profileTag = JjnConfig.get().profileName;
        }
        graphics.centeredText(this.font, profileTag, x + w / 2, y + 22, 0xFFC7D2FE);

        // Subtle pedestal floor under the 3D character
        graphics.fill(x + 14, y + h - 24, x + w - 14, y + h - 22, 0x44C7D2FE);
        graphics.centeredText(this.font, "Drag to rotate 3D", x + w / 2, y + h - 15, 0xFF85899A);
    }

    private void renderCardAndBranding(GuiGraphicsExtractor graphics) {
        int cx = this.cardX;
        int cy = this.cardY;
        int cw = this.cardW;
        int ch = this.cardH;
        int cardCenterX = cx + cw / 2;

        graphics.fill(cx, cy, cx + cw, cy + ch, 0xE6191B23);
        graphics.fill(cx, cy, cx + cw, cy + 2, 0xFFC7D2FE);
        graphics.outline(cx, cy, cw, ch, 0xFF353949);

        var pose = graphics.pose();
        pose.pushMatrix();
        float scale = 2.6f;
        pose.scale(scale, scale);
        String title = "JJN";
        int titleW = this.font.width(title);
        float scaledCenterX = cardCenterX / scale - titleW / 2.0f;
        float scaledY = (cy + 16) / scale;
        graphics.text(this.font, title, (int) scaledCenterX, (int) scaledY, 0xFFFFFFFF, true);
        pose.popMatrix();

        graphics.centeredText(
                this.font,
                "CLIENT  •  MINECRAFT 26.2",
                cardCenterX,
                cy + 44,
                0xFFC7D2FE
        );

        String username = this.minecraft != null && this.minecraft.getUser() != null
                ? this.clientNameSafe(this.minecraft.getUser().getName())
                : "Player";
        String prefix = "Signed in: ";
        int iconSize = 9;
        int badgeW = this.font.width(prefix) + iconSize + 4 + this.font.width(username) + 16;
        graphics.fill(12, 12, 12 + badgeW, 30, 0xCC20222C);
        graphics.fill(12, 12, 14, 30, 0xFFC7D2FE);
        graphics.text(this.font, prefix, 20, 17, 0xFFE5E7EB, true);
        int iconX = 20 + this.font.width(prefix);
        graphics.blit(RenderPipelines.GUI_TEXTURED, JjnCosmetics.CREEPER_GUI_ICON, iconX, 16, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
        graphics.text(this.font, username, iconX + iconSize + 4, 17, 0xFFFFFFFF, true);
    }

    private static String clientNameSafe(String raw) {
        return raw == null || raw.isBlank() ? "Player" : raw;
    }

    private void renderFooter(GuiGraphicsExtractor graphics) {
        String left = "JJN Client v" + JjnClientMod.VERSION + "  (Press Right-Shift in-game for Mods)";
        graphics.text(this.font, left, 12, this.height - 18, 0xFF85899A, true);

        String right = "Minecraft 26.2 / Fabric";
        int rightW = this.font.width(right);
        graphics.text(this.font, right, this.width - rightW - 12, this.height - 18, 0xFF85899A, true);
    }
}
