package com.jjn.client.mixin;

import com.jjn.client.JjnConfig;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {

    @Shadow
    private boolean needsUpdate;

    @Unique
    private boolean jjn$lastFullbright;

    @Inject(method = "extract", at = @At("HEAD"))
    private void jjn$checkFullbrightDirty(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
        boolean fb = JjnConfig.get().fullbright;
        if (fb != this.jjn$lastFullbright) {
            this.needsUpdate = true;
            this.jjn$lastFullbright = fb;
        }
    }

    @Inject(method = "extract", at = @At("TAIL"))
    private void jjn$applyFullbright(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
        if (JjnConfig.get().fullbright) {
            state.nightVisionEffectIntensity = 1.0F;
            state.nightVisionColor = LightmapRenderStateExtractor.WHITE;
            state.darknessEffectScale = 0.0F;
        }
    }
}
