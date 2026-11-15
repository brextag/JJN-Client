package com.jjn.client.mixin;

import com.jjn.client.JjnInputState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraZoomMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "calculateFov(F)F", at = @At("RETURN"), cancellable = true)
    private void jjn$applyZoomFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValue() * JjnInputState.nextZoomMultiplier(this.minecraft));
    }
}
