package com.jjn.client.shop;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Custom 3D FeatureRenderer for MC 1.21.4 attaching Shop Hats/Headgear to the player's head
 * and 3D Wings / Aero-Packs to the player's torso.
 */
public class JjnCosmeticsFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    // Headgear parts
    private final ModelPart haloFront = JjnCosmeticAssets.singleBoxPart(-4.5F, -11.5F, -4.5F, 9F, 1F, 1.2F);
    private final ModelPart haloBack = JjnCosmeticAssets.singleBoxPart(-4.5F, -11.5F, 3.3F, 9F, 1F, 1.2F);
    private final ModelPart haloLeft = JjnCosmeticAssets.singleBoxPart(-4.5F, -11.5F, -3.3F, 1.2F, 1F, 6.6F);
    private final ModelPart haloRight = JjnCosmeticAssets.singleBoxPart(3.3F, -11.5F, -3.3F, 1.2F, 1F, 6.6F);

    private final ModelPart crownBase = JjnCosmeticAssets.singleBoxPart(-4.3F, -9.8F, -4.3F, 8.6F, 2.0F, 8.6F);
    private final ModelPart crownSpikeFL = JjnCosmeticAssets.singleBoxPart(-4.3F, -11.6F, -4.3F, 1.6F, 1.8F, 1.6F);
    private final ModelPart crownSpikeFR = JjnCosmeticAssets.singleBoxPart(2.7F, -11.6F, -4.3F, 1.6F, 1.8F, 1.6F);
    private final ModelPart crownSpikeCenter = JjnCosmeticAssets.singleBoxPart(-0.8F, -12.0F, -4.3F, 1.6F, 2.2F, 1.4F);
    private final ModelPart crownSpikeBL = JjnCosmeticAssets.singleBoxPart(-4.3F, -11.6F, 2.7F, 1.6F, 1.8F, 1.6F);
    private final ModelPart crownSpikeBR = JjnCosmeticAssets.singleBoxPart(2.7F, -11.6F, 2.7F, 1.6F, 1.8F, 1.6F);
    private final ModelPart crownGem = JjnCosmeticAssets.singleBoxPart(-0.9F, -9.5F, -4.7F, 1.8F, 1.4F, 0.5F);

    private final ModelPart hornLBase = JjnCosmeticAssets.singleBoxPart(-5.2F, -10.5F, -1.2F, 1.5F, 3.0F, 1.8F);
    private final ModelPart hornLTip = JjnCosmeticAssets.singleBoxPart(-6.0F, -12.8F, -1.5F, 1.2F, 2.6F, 1.4F);
    private final ModelPart hornRBase = JjnCosmeticAssets.singleBoxPart(3.7F, -10.5F, -1.2F, 1.5F, 3.0F, 1.8F);
    private final ModelPart hornRTip = JjnCosmeticAssets.singleBoxPart(4.8F, -12.8F, -1.5F, 1.2F, 2.6F, 1.4F);

    private final ModelPart earLOuter = JjnCosmeticAssets.singleBoxPart(-4.2F, -11.2F, -1.0F, 2.6F, 3.2F, 1.8F);
    private final ModelPart earLInner = JjnCosmeticAssets.singleBoxPart(-3.7F, -10.6F, -1.2F, 1.5F, 2.2F, 0.8F);
    private final ModelPart earROuter = JjnCosmeticAssets.singleBoxPart(1.6F, -11.2F, -1.0F, 2.6F, 3.2F, 1.8F);
    private final ModelPart earRInner = JjnCosmeticAssets.singleBoxPart(2.2F, -10.6F, -1.2F, 1.5F, 2.2F, 0.8F);

    // Angel Wings
    private final ModelPart angelTopR = JjnCosmeticAssets.singleBoxPart(0.5F, -2.5F, -0.5F, 11.0F, 5.0F, 1.0F);
    private final ModelPart angelMidR = JjnCosmeticAssets.singleBoxPart(1.5F, 2.5F, -0.4F, 12.0F, 4.0F, 0.9F);
    private final ModelPart angelLowR = JjnCosmeticAssets.singleBoxPart(1.0F, 6.5F, -0.4F, 8.0F, 4.0F, 0.8F);

    private final ModelPart angelTopL = JjnCosmeticAssets.singleBoxPart(-11.5F, -2.5F, -0.5F, 11.0F, 5.0F, 1.0F);
    private final ModelPart angelMidL = JjnCosmeticAssets.singleBoxPart(-13.5F, 2.5F, -0.4F, 12.0F, 4.0F, 0.9F);
    private final ModelPart angelLowL = JjnCosmeticAssets.singleBoxPart(-9.0F, 6.5F, -0.4F, 8.0F, 4.0F, 0.8F);

    // Dragon Wings
    private final ModelPart dragonBoneR = JjnCosmeticAssets.singleBoxPart(0.0F, -3.5F, -0.6F, 13.0F, 2.0F, 1.2F);
    private final ModelPart dragonMemR = JjnCosmeticAssets.singleBoxPart(0.5F, -1.5F, -0.4F, 11.5F, 8.0F, 0.8F);
    private final ModelPart dragonBoneL = JjnCosmeticAssets.singleBoxPart(-13.0F, -3.5F, -0.6F, 13.0F, 2.0F, 1.2F);
    private final ModelPart dragonMemL = JjnCosmeticAssets.singleBoxPart(-12.0F, -1.5F, -0.4F, 11.5F, 8.0F, 0.8F);

    // Aero-Pack
    private final ModelPart jetPodL = JjnCosmeticAssets.singleBoxPart(-4.5F, 1.5F, 2.2F, 3.2F, 8.5F, 3.0F);
    private final ModelPart jetPodR = JjnCosmeticAssets.singleBoxPart(1.3F, 1.5F, 2.2F, 3.2F, 8.5F, 3.0F);
    private final ModelPart jetCore = JjnCosmeticAssets.singleBoxPart(-1.3F, 3.0F, 2.2F, 2.6F, 5.0F, 2.2F);
    private final ModelPart jetFlameL = JjnCosmeticAssets.singleBoxPart(-4.0F, 10.0F, 2.6F, 2.2F, 2.4F, 2.2F);
    private final ModelPart jetFlameR = JjnCosmeticAssets.singleBoxPart(1.8F, 10.0F, 2.6F, 2.2F, 2.4F, 2.2F);

    public JjnCosmeticsFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            PlayerEntityRenderState state,
            float limbAngle,
            float limbDistance
    ) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || state.id != mc.player.getId() || state.spectator) {
            return;
        }

        JjnEconomyManager.EconomyData data = JjnEconomyManager.get().snapshot();
        String hatId = data.equippedHatId == null ? "" : data.equippedHatId;
        String backId = data.equippedBackId == null ? "" : data.equippedBackId;
        if (hatId.isEmpty() && backId.isEmpty()) {
            return;
        }

        double t = System.currentTimeMillis() / 1000.0;

        if (!hatId.isEmpty()) {
            matrices.push();
            this.getContextModel().head.rotate(matrices);
            renderHat(mc, hatId, t, matrices, vertexConsumers, light);
            matrices.pop();
        }

        if (!backId.isEmpty()) {
            matrices.push();
            this.getContextModel().body.rotate(matrices);
            renderBack(mc, backId, t, matrices, vertexConsumers, light);
            matrices.pop();
        }
    }

    private void renderHat(MinecraftClient mc, String hatId, double t, MatrixStack matrices, VertexConsumerProvider vcs, int light) {
        switch (hatId) {
            case "hat_halo" -> {
                Identifier goldTex = JjnCosmeticAssets.materialTexture(mc, "halo_gold", 0xFFFCD34D);
                float bob = (float) Math.sin(t * 3.0) * 0.035F;
                matrices.push();
                matrices.translate(0.0F, bob, 0.0F);
                drawPart(haloFront, matrices, vcs, goldTex, 15728880);
                drawPart(haloBack, matrices, vcs, goldTex, 15728880);
                drawPart(haloLeft, matrices, vcs, goldTex, 15728880);
                drawPart(haloRight, matrices, vcs, goldTex, 15728880);
                matrices.pop();
            }
            case "hat_crown" -> {
                Identifier goldTex = JjnCosmeticAssets.materialTexture(mc, "crown_gold", 0xFFFBBF24);
                Identifier gemTex = JjnCosmeticAssets.materialTexture(mc, "crown_gem", 0xFF818CF8);
                drawPart(crownBase, matrices, vcs, goldTex, light);
                drawPart(crownSpikeFL, matrices, vcs, goldTex, light);
                drawPart(crownSpikeFR, matrices, vcs, goldTex, light);
                drawPart(crownSpikeCenter, matrices, vcs, goldTex, light);
                drawPart(crownSpikeBL, matrices, vcs, goldTex, light);
                drawPart(crownSpikeBR, matrices, vcs, goldTex, light);
                drawPart(crownGem, matrices, vcs, gemTex, 15728880);
            }
            case "hat_horns" -> {
                Identifier hornTex = JjnCosmeticAssets.materialTexture(mc, "oni_horn", 0xFFE11D48);
                Identifier tipTex = JjnCosmeticAssets.materialTexture(mc, "oni_tip", 0xFFFB7185);
                drawPart(hornLBase, matrices, vcs, hornTex, light);
                drawPart(hornRBase, matrices, vcs, hornTex, light);
                drawPart(hornLTip, matrices, vcs, tipTex, light);
                drawPart(hornRTip, matrices, vcs, tipTex, light);
            }
            case "hat_fox_ears" -> {
                Identifier furTex = JjnCosmeticAssets.materialTexture(mc, "fox_fur", 0xFFF97316);
                Identifier innerTex = JjnCosmeticAssets.materialTexture(mc, "fox_inner", 0xFFFEF3C7);
                drawPart(earLOuter, matrices, vcs, furTex, light);
                drawPart(earROuter, matrices, vcs, furTex, light);
                drawPart(earLInner, matrices, vcs, innerTex, light);
                drawPart(earRInner, matrices, vcs, innerTex, light);
            }
        }
    }

    private void renderBack(MinecraftClient mc, String backId, double t, MatrixStack matrices, VertexConsumerProvider vcs, int light) {
        float flap = (float) Math.sin(t * 3.8) * 0.24F;
        switch (backId) {
            case "back_angel_wings" -> {
                Identifier whiteTex = JjnCosmeticAssets.materialTexture(mc, "wing_white", 0xFFF8FAFC);
                Identifier goldTex = JjnCosmeticAssets.materialTexture(mc, "wing_gold", 0xFFFDE68A);

                matrices.push();
                matrices.translate(0.14F, 0.26F, 0.16F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotation(-0.42F - flap));
                drawPart(angelTopR, matrices, vcs, whiteTex, light);
                drawPart(angelMidR, matrices, vcs, goldTex, light);
                drawPart(angelLowR, matrices, vcs, whiteTex, light);
                matrices.pop();

                matrices.push();
                matrices.translate(-0.14F, 0.26F, 0.16F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotation(0.42F + flap));
                drawPart(angelTopL, matrices, vcs, whiteTex, light);
                drawPart(angelMidL, matrices, vcs, goldTex, light);
                drawPart(angelLowL, matrices, vcs, whiteTex, light);
                matrices.pop();
            }
            case "back_dragon_wings" -> {
                Identifier boneTex = JjnCosmeticAssets.materialTexture(mc, "dragon_bone", 0xFF1E1B2E);
                Identifier memTex = JjnCosmeticAssets.materialTexture(mc, "dragon_mem", 0xFF8B5CF6);

                matrices.push();
                matrices.translate(0.12F, 0.26F, 0.16F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotation(-0.48F - flap));
                drawPart(dragonBoneR, matrices, vcs, boneTex, light);
                drawPart(dragonMemR, matrices, vcs, memTex, 15728880);
                matrices.pop();

                matrices.push();
                matrices.translate(-0.12F, 0.26F, 0.16F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotation(0.48F + flap));
                drawPart(dragonBoneL, matrices, vcs, boneTex, light);
                drawPart(dragonMemL, matrices, vcs, memTex, 15728880);
                matrices.pop();
            }
            case "back_cyber_jet" -> {
                Identifier hullTex = JjnCosmeticAssets.materialTexture(mc, "jet_hull", 0xFF334155);
                Identifier coreTex = JjnCosmeticAssets.materialTexture(mc, "jet_core", 0xFFC7D2FE);
                Identifier flameTex = JjnCosmeticAssets.materialTexture(mc, "jet_flame", 0xFF67E8F9);
                drawPart(jetPodL, matrices, vcs, hullTex, light);
                drawPart(jetPodR, matrices, vcs, hullTex, light);
                drawPart(jetCore, matrices, vcs, coreTex, 15728880);
                float pulse = 0.85F + 0.35F * (float) Math.abs(Math.sin(t * 14.0));
                matrices.push();
                matrices.scale(1.0F, pulse, 1.0F);
                drawPart(jetFlameL, matrices, vcs, flameTex, 15728880);
                drawPart(jetFlameR, matrices, vcs, flameTex, 15728880);
                matrices.pop();
            }
        }
    }

    private static void drawPart(ModelPart part, MatrixStack matrices, VertexConsumerProvider vcs, Identifier tex, int light) {
        VertexConsumer vc = vcs.getBuffer(RenderLayer.getEntityCutout(tex));
        part.render(matrices, vc, light, OverlayTexture.DEFAULT_UV);
    }
}
