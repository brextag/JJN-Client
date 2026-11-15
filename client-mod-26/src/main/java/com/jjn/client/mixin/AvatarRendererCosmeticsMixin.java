package com.jjn.client.mixin;

import com.jjn.client.friends.JjnFriendsManager;
import com.jjn.client.shop.JjnCosmeticsRenderLayer;
import com.jjn.client.shop.JjnEconomyManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererCosmeticsMixin<T extends Avatar & ClientAvatarEntity>
        extends LivingEntityRenderer<T, AvatarRenderState, PlayerModel> {

    protected AvatarRendererCosmeticsMixin(EntityRendererProvider.Context ctx, PlayerModel model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V", at = @At("TAIL"))
    private void jjn$addCosmeticsLayer(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        this.addLayer(new JjnCosmeticsRenderLayer(this));
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
    private void jjn$applyEmoteBodyTransforms(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
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
                poseStack.translate(0.0F, bob, 0.0F);
            }
            case "emote_backflip" -> {
                float t = (elapsed % 1600L) / 1600.0F;
                if (t < 0.75F) {
                    float ft = t / 0.75F;
                    float jumpY = 0.65F * (float) Math.sin(ft * Math.PI);
                    poseStack.translate(0.0F, jumpY + 0.9F, 0.0F);
                    poseStack.mulPose(Axis.XP.rotation((float) (-ft * Math.PI * 2.0)));
                    poseStack.translate(0.0F, -0.9F, 0.0F);
                }
            }
            case "emote_breakdance" -> {
                float bounce = 0.14F * Math.abs((float) Math.sin(sec * Math.PI * 2.0));
                poseStack.translate(0.0F, bounce, 0.0F);
                poseStack.mulPose(Axis.YP.rotation(sec * 3.8F));
            }
        }
    }
}
