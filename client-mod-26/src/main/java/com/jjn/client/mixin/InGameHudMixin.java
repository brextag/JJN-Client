package com.jjn.client.mixin;

import com.jjn.client.JjnConfig;
import com.jjn.client.friends.JjnFriendsManager;
import com.jjn.client.shop.JjnEconomyManager;
import com.jjn.client.ui.JjnHudRenderer;
import com.jjn.client.ui.JjnWaypointRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class InGameHudMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    public abstract boolean isHidden();

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void jjn$renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        JjnConfig cfg = JjnConfig.get();
        if (this.minecraft == null || this.minecraft.player == null || this.isHidden()) {
            return;
        }

        int toastY = 8;
        JjnFriendsManager.WorldInvite toast = JjnFriendsManager.get().getLatestToastInvite();
        if (toast != null) {
            String title = toast.fromName + " invited you to \"" + toast.worldName + "\"";
            String sub = "Press [J] to Join Now  •  Esc -> Friends";
            int w = Math.max(this.minecraft.font.width(title), this.minecraft.font.width(sub)) + 20;
            int x = (graphics.guiWidth() - w) / 2;
            graphics.fill(x, toastY, x + w, toastY + 32, 0xE6191B23);
            graphics.fill(x, toastY, x + w, toastY + 2, 0xFF86EFAC);
            graphics.outline(x, toastY, w, 32, 0xFF353949);
            graphics.centeredText(this.minecraft.font, title, graphics.guiWidth() / 2, toastY + 7, 0xFFFFFFFF);
            graphics.centeredText(this.minecraft.font, sub, graphics.guiWidth() / 2, toastY + 19, 0xFFC7D2FE);
            toastY += 38;
        }

        JjnEconomyManager.Toast questToast = JjnEconomyManager.get().getActiveToast();
        if (questToast != null) {
            int w = Math.max(this.minecraft.font.width(questToast.title()), this.minecraft.font.width(questToast.subtitle())) + 24;
            int x = (graphics.guiWidth() - w) / 2;
            graphics.fill(x, toastY, x + w, toastY + 32, 0xE6191B23);
            graphics.fill(x, toastY, x + w, toastY + 2, 0xFFFCD34D);
            graphics.outline(x, toastY, w, 32, 0xFF353949);
            graphics.centeredText(this.minecraft.font, questToast.title(), graphics.guiWidth() / 2, toastY + 7, 0xFFFCD34D);
            graphics.centeredText(this.minecraft.font, questToast.subtitle(), graphics.guiWidth() / 2, toastY + 19, 0xFFE5E7EB);
        }

        if (cfg.showWaypoints && !this.minecraft.getDebugOverlay().showDebugScreen()) {
            JjnWaypointRenderer.renderWaypoints(graphics, this.minecraft);
        }
        if (!cfg.showHud || this.minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }
        JjnHudRenderer.renderHud(graphics, this.minecraft, false);
    }
}
