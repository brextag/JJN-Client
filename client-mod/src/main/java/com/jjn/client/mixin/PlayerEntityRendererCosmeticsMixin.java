package com.jjn.client.mixin;

import com.jjn.client.friends.JjnFriendsManager;
import com.jjn.client.shop.JjnCosmeticsFeatureRenderer;
import com.jjn.client.shop.JjnEconomyManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererCosmeticsMixin
        extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityRenderState, PlayerEntityModel> {

    protected PlayerEntityRendererCosmeticsMixin(EntityRendererFactory.Context ctx, PlayerEntityModel model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/render/entity/EntityRendererFactory$Context;Z)V", at = @At("TAIL"))
    private void jjn$addCosmeticsFeature(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
        this.addFeature(new JjnCosmeticsFeatureRenderer(this));
    }

    @Inject(method = "setupTransforms(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;FF)V", at = @At("TAIL"))
    private void jjn$applyEmoteBodyTransforms(PlayerEntityRenderState state, MatrixStack matrices, float animationProgress, float bodyYaw, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return;
        }
        String emoteId;
        long elapsed;
        if (mc.player != null && state.id == mc.player.getId()) {
            emoteId = JjnEconomyManager.get().getActiveEmoteId();
            elapsed = JjnEconomyManager.get().getEmoteElapsedMs();
        } else {
            JjnFriendsManager.SyncedCosmetics sc = JjnFriendsManager.get().getCosmeticsForEntityId(state.id);
            emoteId = sc.activeEmoteId();
            elapsed = Math.max(0L, System.currentTimeMillis() - sc.emoteStartMs());
        }
        if (emoteId == null || emoteId.isEmpty()) {
            return;
        }
        float sec = (elapsed % 3000L) / 1000.0F;

        switch (emoteId) {
            case "emote_tpose" -> {
                float bob = 0.06F + 0.05F * (float) Math.sin(sec * 3.0);
                matrices.translate(0.0F, bob, 0.0F);
            }
            case "emote_backflip" -> {
                float t = (elapsed % 1600L) / 1600.0F;
                if (t < 0.75F) {
                    float ft = t / 0.75F;
                    float jumpY = 0.65F * (float) Math.sin(ft * Math.PI);
                    matrices.translate(0.0F, jumpY + 0.9F, 0.0F);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotation((float) (-ft * Math.PI * 2.0)));
                    matrices.translate(0.0F, -0.9F, 0.0F);
                }
            }
            case "emote_breakdance" -> {
                float bounce = 0.14F * Math.abs((float) Math.sin(sec * Math.PI * 2.0));
                matrices.translate(0.0F, bounce, 0.0F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotation(sec * 3.8F));
            }
        }
    }
}
