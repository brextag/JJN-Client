package com.jjn.client.mixin;

import com.jjn.client.shop.JjnEconomyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelEmoteMixin extends HumanoidModel<AvatarRenderState> {

    @Shadow @Final public ModelPart leftSleeve;
    @Shadow @Final public ModelPart rightSleeve;
    @Shadow @Final public ModelPart leftPants;
    @Shadow @Final public ModelPart rightPants;
    @Shadow @Final public ModelPart jacket;

    protected PlayerModelEmoteMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void jjn$applyEmotePose(AvatarRenderState state, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
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
                this.rightArm.xRot = -2.45F;
                this.rightArm.zRot = 0.45F + 0.45F * (float) Math.sin(sec * 8.0);
                this.head.zRot = 0.12F * (float) Math.sin(sec * 4.0);
                this.leftArm.xRot = 0.15F;
            }
            case "emote_salute" -> {
                this.rightArm.xRot = -1.95F;
                this.rightArm.yRot = 0.48F;
                this.rightArm.zRot = -0.32F;
                this.head.xRot = -0.08F;
                this.leftArm.zRot = -0.08F;
            }
            case "emote_dab" -> {
                this.rightArm.xRot = -1.65F;
                this.rightArm.yRot = 0.45F;
                this.rightArm.zRot = 0.72F;
                this.leftArm.xRot = -1.45F;
                this.leftArm.yRot = -0.70F;
                this.leftArm.zRot = -0.55F;
                this.head.xRot = 0.55F;
                this.head.yRot = -0.65F;
                this.head.zRot = 0.25F;
            }
            case "emote_tpose" -> {
                this.rightArm.xRot = 0.0F;
                this.rightArm.yRot = 0.0F;
                this.rightArm.zRot = (float) (Math.PI / 2.0);
                this.leftArm.xRot = 0.0F;
                this.leftArm.yRot = 0.0F;
                this.leftArm.zRot = (float) (-Math.PI / 2.0);
                this.rightLeg.xRot = 0.0F;
                this.leftLeg.xRot = 0.0F;
            }
            case "emote_clap" -> {
                float clap = (float) Math.abs(Math.sin(sec * 9.0));
                this.rightArm.xRot = -1.25F;
                this.leftArm.xRot = -1.25F;
                this.rightArm.yRot = -0.45F + 0.34F * clap;
                this.leftArm.yRot = 0.45F - 0.34F * clap;
                this.head.xRot = 0.10F * (float) Math.sin(sec * 9.0);
            }
            case "emote_facepalm" -> {
                this.rightArm.xRot = -1.95F;
                this.rightArm.yRot = -0.45F;
                this.rightArm.zRot = -0.30F;
                this.head.xRot = 0.42F;
                this.head.yRot = 0.22F * (float) Math.sin(sec * 6.0);
            }
            case "emote_backflip" -> {
                float t = (elapsed % 1600L) / 1600.0F;
                if (t < 0.75F) {
                    this.rightArm.xRot = -2.2F;
                    this.leftArm.xRot = -2.2F;
                    this.rightLeg.xRot = 0.55F;
                    this.leftLeg.xRot = 0.55F;
                } else {
                    this.rightArm.xRot = -2.5F;
                    this.rightArm.zRot = 0.45F;
                    this.leftArm.xRot = -2.5F;
                    this.leftArm.zRot = -0.45F;
                }
            }
            case "emote_breakdance" -> {
                this.rightArm.xRot = -1.3F + 0.9F * (float) Math.sin(sec * 8.0);
                this.rightArm.zRot = 0.7F;
                this.leftArm.xRot = -1.3F - 0.9F * (float) Math.sin(sec * 8.0);
                this.leftArm.zRot = -0.7F;
                this.rightLeg.xRot = 0.65F * (float) Math.sin(sec * 8.0);
                this.leftLeg.xRot = -0.65F * (float) Math.sin(sec * 8.0);
                this.rightLeg.zRot = 0.25F;
                this.leftLeg.zRot = -0.25F;
                this.head.xRot = 0.25F * (float) Math.sin(sec * 6.0);
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
        dst.x = src.x;
        dst.y = src.y;
        dst.z = src.z;
        dst.xRot = src.xRot;
        dst.yRot = src.yRot;
        dst.zRot = src.zRot;
    }
}
