package com.jjn.client.shop;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Custom 3D RenderLayer for MC 26.2 attaching Shop Hats/Headgear to the player's head
 * and 3D Wings / Aero-Packs to the player's torso.
 */
public class JjnCosmeticsRenderLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

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

    // Angel Wings (origin at wing root so rotation opens outward neatly)
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

    public JjnCosmeticsRenderLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int packedLight,
            AvatarRenderState state,
            float yRot,
            float xRot
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || state.id != mc.player.getId() || state.isSpectator) {
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
            poseStack.pushPose();
            this.getParentModel().head.translateAndRotate(poseStack);
            renderHat(mc, hatId, t, poseStack, collector, packedLight);
            poseStack.popPose();
        }

        if (!backId.isEmpty()) {
            poseStack.pushPose();
            this.getParentModel().body.translateAndRotate(poseStack);
            renderBack(mc, backId, t, poseStack, collector, packedLight);
            poseStack.popPose();
        }
    }

    private void renderHat(Minecraft mc, String hatId, double t, PoseStack ps, SubmitNodeCollector col, int light) {
        switch (hatId) {
            case "hat_halo" -> {
                Identifier goldTex = JjnCosmeticAssets.materialTexture(mc, "halo_gold", 0xFFFCD34D);
                float bob = (float) Math.sin(t * 3.0) * 0.035F;
                ps.pushPose();
                ps.translate(0.0F, bob, 0.0F);
                submit(haloFront, ps, col, goldTex, 15728880);
                submit(haloBack, ps, col, goldTex, 15728880);
                submit(haloLeft, ps, col, goldTex, 15728880);
                submit(haloRight, ps, col, goldTex, 15728880);
                ps.popPose();
            }
            case "hat_crown" -> {
                Identifier goldTex = JjnCosmeticAssets.materialTexture(mc, "crown_gold", 0xFFFBBF24);
                Identifier gemTex = JjnCosmeticAssets.materialTexture(mc, "crown_gem", 0xFF818CF8);
                submit(crownBase, ps, col, goldTex, light);
                submit(crownSpikeFL, ps, col, goldTex, light);
                submit(crownSpikeFR, ps, col, goldTex, light);
                submit(crownSpikeCenter, ps, col, goldTex, light);
                submit(crownSpikeBL, ps, col, goldTex, light);
                submit(crownSpikeBR, ps, col, goldTex, light);
                submit(crownGem, ps, col, gemTex, 15728880);
            }
            case "hat_horns" -> {
                Identifier hornTex = JjnCosmeticAssets.materialTexture(mc, "oni_horn", 0xFFE11D48);
                Identifier tipTex = JjnCosmeticAssets.materialTexture(mc, "oni_tip", 0xFFFB7185);
                submit(hornLBase, ps, col, hornTex, light);
                submit(hornRBase, ps, col, hornTex, light);
                submit(hornLTip, ps, col, tipTex, light);
                submit(hornRTip, ps, col, tipTex, light);
            }
            case "hat_fox_ears" -> {
                Identifier furTex = JjnCosmeticAssets.materialTexture(mc, "fox_fur", 0xFFF97316);
                Identifier innerTex = JjnCosmeticAssets.materialTexture(mc, "fox_inner", 0xFFFEF3C7);
                submit(earLOuter, ps, col, furTex, light);
                submit(earROuter, ps, col, furTex, light);
                submit(earLInner, ps, col, innerTex, light);
                submit(earRInner, ps, col, innerTex, light);
            }
        }
    }

    private void renderBack(Minecraft mc, String backId, double t, PoseStack ps, SubmitNodeCollector col, int light) {
        float flap = (float) Math.sin(t * 3.8) * 0.24F;
        switch (backId) {
            case "back_angel_wings" -> {
                Identifier whiteTex = JjnCosmeticAssets.materialTexture(mc, "wing_white", 0xFFF8FAFC);
                Identifier goldTex = JjnCosmeticAssets.materialTexture(mc, "wing_gold", 0xFFFDE68A);

                ps.pushPose();
                ps.translate(0.14F, 0.26F, 0.16F);
                ps.mulPose(Axis.YP.rotation(-0.42F - flap));
                submit(angelTopR, ps, col, whiteTex, light);
                submit(angelMidR, ps, col, goldTex, light);
                submit(angelLowR, ps, col, whiteTex, light);
                ps.popPose();

                ps.pushPose();
                ps.translate(-0.14F, 0.26F, 0.16F);
                ps.mulPose(Axis.YP.rotation(0.42F + flap));
                submit(angelTopL, ps, col, whiteTex, light);
                submit(angelMidL, ps, col, goldTex, light);
                submit(angelLowL, ps, col, whiteTex, light);
                ps.popPose();
            }
            case "back_dragon_wings" -> {
                Identifier boneTex = JjnCosmeticAssets.materialTexture(mc, "dragon_bone", 0xFF1E1B2E);
                Identifier memTex = JjnCosmeticAssets.materialTexture(mc, "dragon_mem", 0xFF8B5CF6);

                ps.pushPose();
                ps.translate(0.12F, 0.26F, 0.16F);
                ps.mulPose(Axis.YP.rotation(-0.48F - flap));
                submit(dragonBoneR, ps, col, boneTex, light);
                submit(dragonMemR, ps, col, memTex, 15728880);
                ps.popPose();

                ps.pushPose();
                ps.translate(-0.12F, 0.26F, 0.16F);
                ps.mulPose(Axis.YP.rotation(0.48F + flap));
                submit(dragonBoneL, ps, col, boneTex, light);
                submit(dragonMemL, ps, col, memTex, 15728880);
                ps.popPose();
            }
            case "back_cyber_jet" -> {
                Identifier hullTex = JjnCosmeticAssets.materialTexture(mc, "jet_hull", 0xFF334155);
                Identifier coreTex = JjnCosmeticAssets.materialTexture(mc, "jet_core", 0xFFC7D2FE);
                Identifier flameTex = JjnCosmeticAssets.materialTexture(mc, "jet_flame", 0xFF67E8F9);
                submit(jetPodL, ps, col, hullTex, light);
                submit(jetPodR, ps, col, hullTex, light);
                submit(jetCore, ps, col, coreTex, 15728880);
                float pulse = 0.85F + 0.35F * (float) Math.abs(Math.sin(t * 14.0));
                ps.pushPose();
                ps.scale(1.0F, pulse, 1.0F);
                submit(jetFlameL, ps, col, flameTex, 15728880);
                submit(jetFlameR, ps, col, flameTex, 15728880);
                ps.popPose();
            }
        }
    }

    private static void submit(ModelPart part, PoseStack ps, SubmitNodeCollector col, Identifier tex, int light) {
        col.submitModelPart(part, ps, RenderTypes.entityCutout(tex), light, OverlayTexture.NO_OVERLAY, null);
    }
}
