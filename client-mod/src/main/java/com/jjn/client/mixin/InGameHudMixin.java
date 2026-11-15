package com.jjn.client.mixin;

import com.jjn.client.JjnConfig;
import com.jjn.client.friends.JjnFriendsManager;
import com.jjn.client.shop.JjnEconomyManager;
import com.jjn.client.ui.JjnHudRenderer;
import com.jjn.client.ui.JjnWaypointRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "render", at = @At("TAIL"))
    private void jjn$renderHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        JjnConfig cfg = JjnConfig.get();
        if (this.client == null || this.client.player == null || this.client.options.hudHidden) {
            return;
        }

        int screenW = context.getScaledWindowWidth();
        int toastY = 8;
        JjnFriendsManager.WorldInvite toast = JjnFriendsManager.get().getLatestToastInvite();
        if (toast != null) {
            String title = toast.fromName + " invited you to \"" + toast.worldName + "\"";
            String sub = "Press [J] to Join Now  •  Esc -> Friends";
            int w = Math.max(this.client.textRenderer.getWidth(title), this.client.textRenderer.getWidth(sub)) + 20;
            int x = (screenW - w) / 2;
            context.fill(x, toastY, x + w, toastY + 32, 0xE6191B23);
            context.fill(x, toastY, x + w, toastY + 2, 0xFF86EFAC);
            context.drawBorder(x, toastY, w, 32, 0xFF353949);
            context.drawCenteredTextWithShadow(this.client.textRenderer, title, screenW / 2, toastY + 7, 0xFFFFFFFF);
            context.drawCenteredTextWithShadow(this.client.textRenderer, sub, screenW / 2, toastY + 19, 0xFFC7D2FE);
            toastY += 38;
        }

        JjnEconomyManager.Toast questToast = JjnEconomyManager.get().getActiveToast();
        if (questToast != null) {
            int w = Math.max(this.client.textRenderer.getWidth(questToast.title()), this.client.textRenderer.getWidth(questToast.subtitle())) + 24;
            int x = (screenW - w) / 2;
            context.fill(x, toastY, x + w, toastY + 32, 0xE6191B23);
            context.fill(x, toastY, x + w, toastY + 2, 0xFFFCD34D);
            context.drawBorder(x, toastY, w, 32, 0xFF353949);
            context.drawCenteredTextWithShadow(this.client.textRenderer, questToast.title(), screenW / 2, toastY + 7, 0xFFFCD34D);
            context.drawCenteredTextWithShadow(this.client.textRenderer, questToast.subtitle(), screenW / 2, toastY + 19, 0xFFE5E7EB);
        }

        if (cfg.showWaypoints && !this.client.getDebugHud().shouldShowDebugHud()) {
            JjnWaypointRenderer.renderWaypoints(context, this.client);
        }
        if (!cfg.showHud || this.client.getDebugHud().shouldShowDebugHud()) {
            return;
        }
        JjnHudRenderer.renderHud(context, this.client, false);
    }
}
