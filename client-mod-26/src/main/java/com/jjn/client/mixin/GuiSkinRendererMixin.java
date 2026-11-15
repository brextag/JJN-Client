package com.jjn.client.mixin;

import com.jjn.client.ui.JjnCosmetics;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.render.pip.GuiSkinRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.GuiSkinRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiSkinRenderer.class)
public abstract class GuiSkinRendererMixin {

    @Inject(
            method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiSkinRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At("TAIL")
    )
    private void jjnclient$renderCapeInPreview(
            GuiSkinRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CallbackInfo ci
    ) {
        if (state.playerModel() == JjnCosmetics.activePreviewBodyModel
                && JjnCosmetics.activePreviewCapeModel != null
                && JjnCosmetics.activePreviewCapeTexture != null) {
            submitNodeCollector.submitModel(
                    JjnCosmetics.activePreviewCapeModel,
                    Unit.INSTANCE,
                    poseStack,
                    JjnCosmetics.activePreviewCapeTexture,
                    15728880,
                    OverlayTexture.NO_OVERLAY,
                    0,
                    null
            );
        }
    }
}
