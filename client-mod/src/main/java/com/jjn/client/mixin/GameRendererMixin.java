package com.jjn.client.mixin;

import com.jjn.client.JjnConfig;
import com.jjn.client.JjnInputState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void jjn$applyZoomFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (changingFov) {
            cir.setReturnValue(cir.getReturnValue() * JjnInputState.nextZoomMultiplier(this.client));
        }
    }

    @Inject(method = "getNightVisionStrength", at = @At("HEAD"), cancellable = true)
    private static void jjn$fullbrightStrength(LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (JjnConfig.get().fullbright) {
            cir.setReturnValue(1.0F);
        }
    }
}
