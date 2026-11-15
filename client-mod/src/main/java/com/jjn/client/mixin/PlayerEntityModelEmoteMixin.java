package com.jjn.client.mixin;

import com.jjn.client.shop.JjnEconomyManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelEmoteMixin extends BipedEntityModel<PlayerEntityRenderState> {

    @Shadow @Final public ModelPart leftSleeve;
    @Shadow @Final public ModelPart rightSleeve;
    @Shadow @Final public ModelPart leftPants;
    @Shadow @Final public ModelPart rightPants;
    @Shadow @Final public ModelPart jacket;

    protected PlayerEntityModelEmoteMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void jjn$applyEmotePose(PlayerEntityRenderState state, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || state.id != mc.player.getId()) {
            return;
        }

        String emoteId = JjnEconomyManager.get().getActiveEmoteId();
        if (emoteId == null || emoteId.isEmpty()) {
            return;
        }

        long elapsed = JjnEconomyManager.get().getEmoteElapsedMs();
        float sec = (elapsed % 3000L) / 1000.0F;

        switch (emoteId) {
            case "emote_wave" -> {
                this.rightArm.pitch = -2.45F;
                this.rightArm.roll = 0.45F + 0.45F * (float) Math.sin(sec * 8.0);
                this.head.roll = 0.12F * (float) Math.sin(sec * 4.0);
                this.leftArm.pitch = 0.15F;
            }
            case "emote_salute" -> {
                this.rightArm.pitch = -1.95F;
                this.rightArm.yaw = 0.48F;
                this.rightArm.roll = -0.32F;
                this.head.pitch = -0.08F;
                this.leftArm.roll = -0.08F;
            }
            case "emote_dab" -> {
                this.rightArm.pitch = -1.65F;
                this.rightArm.yaw = 0.45F;
                this.rightArm.roll = 0.72F;
                this.leftArm.pitch = -1.45F;
                this.leftArm.yaw = -0.70F;
                this.leftArm.roll = -0.55F;
                this.head.pitch = 0.55F;
                this.head.yaw = -0.65F;
                this.head.roll = 0.25F;
            }
            case "emote_tpose" -> {
                this.rightArm.pitch = 0.0F;
                this.rightArm.yaw = 0.0F;
                this.rightArm.roll = (float) (Math.PI / 2.0);
                this.leftArm.pitch = 0.0F;
                this.leftArm.yaw = 0.0F;
                this.leftArm.roll = (float) (-Math.PI / 2.0);
                this.rightLeg.pitch = 0.0F;
                this.leftLeg.pitch = 0.0F;
            }
            case "emote_clap" -> {
                float clap = (float) Math.abs(Math.sin(sec * 9.0));
                this.rightArm.pitch = -1.25F;
                this.leftArm.pitch = -1.25F;
                this.rightArm.yaw = -0.45F + 0.34F * clap;
                this.leftArm.yaw = 0.45F - 0.34F * clap;
                this.head.pitch = 0.10F * (float) Math.sin(sec * 9.0);
            }
            case "emote_facepalm" -> {
                this.rightArm.pitch = -1.95F;
                this.rightArm.yaw = -0.45F;
                this.rightArm.roll = -0.30F;
                this.head.pitch = 0.42F;
                this.head.yaw = 0.22F * (float) Math.sin(sec * 6.0);
            }
            case "emote_backflip" -> {
                float t = (elapsed % 1600L) / 1600.0F;
                if (t < 0.75F) {
                    this.rightArm.pitch = -2.2F;
                    this.leftArm.pitch = -2.2F;
                    this.rightLeg.pitch = 0.55F;
                    this.leftLeg.pitch = 0.55F;
                } else {
                    this.rightArm.pitch = -2.5F;
                    this.rightArm.roll = 0.45F;
                    this.leftArm.pitch = -2.5F;
                    this.leftArm.roll = -0.45F;
                }
            }
            case "emote_breakdance" -> {
                this.rightArm.pitch = -1.3F + 0.9F * (float) Math.sin(sec * 8.0);
                this.rightArm.roll = 0.7F;
                this.leftArm.pitch = -1.3F - 0.9F * (float) Math.sin(sec * 8.0);
                this.leftArm.roll = -0.7F;
                this.rightLeg.pitch = 0.65F * (float) Math.sin(sec * 8.0);
                this.leftLeg.pitch = -0.65F * (float) Math.sin(sec * 8.0);
                this.rightLeg.roll = 0.25F;
                this.leftLeg.roll = -0.25F;
                this.head.pitch = 0.25F * (float) Math.sin(sec * 6.0);
            }
        }

        syncPose(this.head, this.hat);
        syncPose(this.body, this.jacket);
        syncPose(this.rightArm, this.rightSleeve);
        syncPose(this.leftArm, this.leftSleeve);
        syncPose(this.rightLeg, this.rightPants);
        syncPose(this.leftLeg, this.leftPants);
    }

    private static void syncPose(ModelPart src, ModelPart dst) {
        if (src == null || dst == null) return;
        dst.pivotX = src.pivotX;
        dst.pivotY = src.pivotY;
        dst.pivotZ = src.pivotZ;
        dst.pitch = src.pitch;
        dst.yaw = src.yaw;
        dst.roll = src.roll;
    }
}
