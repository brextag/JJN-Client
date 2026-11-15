package com.jjn.client.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.navigation.GuiNavigation;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.entity.model.PlayerCapeModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;

import java.util.function.Supplier;

/**
 * Interactive 3D player model widget with cape rendering support for JJN Client (1.21.4).
 * Vanilla PlayerSkinWidget omits capes; this renders both the player model and PlayerCapeModel.
 */
public class JjnPlayerSkinWidget extends ClickableWidget {

    private static final float MODEL_HEIGHT = 2.125F;
    private static final float ROTATION_SENSITIVITY = 2.5F;
    private static final float ROTATION_X_LIMIT = 50.0F;

    private final PlayerEntityModel wideModel;
    private final PlayerEntityModel slimModel;
    private final PlayerCapeModel<PlayerEntityRenderState> capeModel;
    private final Supplier<SkinTextures> skinSupplier;

    private float xRotation = -5.0F;
    private float yRotation = 30.0F;

    public JjnPlayerSkinWidget(int width, int height, LoadedEntityModels models, Supplier<SkinTextures> skinSupplier) {
        super(0, 0, width, height, ScreenTexts.EMPTY);
        this.wideModel = new PlayerEntityModel(models.getModelPart(EntityModelLayers.PLAYER), false);
        this.slimModel = new PlayerEntityModel(models.getModelPart(EntityModelLayers.PLAYER_SLIM), true);
        ModelPart capeRoot = models.getModelPart(EntityModelLayers.PLAYER_CAPE);
        poseCapePart(capeRoot);
        this.capeModel = new PlayerCapeModel<>(capeRoot);
        this.skinSupplier = skinSupplier;
    }

    private static void poseCapePart(ModelPart root) {
        if (root.hasChild("body")) {
            ModelPart body = root.getChild("body");
            if (body.hasChild("cape")) {
                body.getChild("cape").rotate(new Quaternionf()
                        .rotateY((float) -Math.PI)
                        .rotateX((float) Math.toRadians(6.0))
                        .rotateY((float) Math.PI));
            }
        }
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        context.getMatrices().push();
        context.getMatrices().translate(
                (float) this.getX() + (float) this.getWidth() / 2.0F,
                (float) (this.getY() + this.getHeight()),
                100.0F
        );
        float scale = (float) this.getHeight() / MODEL_HEIGHT;
        context.getMatrices().scale(scale, scale, scale);
        context.getMatrices().translate(0.0F, -0.0625F, 0.0F);
        context.getMatrices().multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.xRotation), 0.0F, -1.0625F, 0.0F);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Y.rotationDegrees(this.yRotation));
        context.draw();
        DiffuseLighting.method_56819(RotationAxis.POSITIVE_X.rotationDegrees(this.xRotation));

        SkinTextures skin = this.skinSupplier.get();
        context.getMatrices().push();
        context.getMatrices().scale(1.0F, 1.0F, -1.0F);
        context.getMatrices().translate(0.0F, -1.501F, 0.0F);

        PlayerEntityModel playerModel = skin.model() == SkinTextures.Model.SLIM ? this.slimModel : this.wideModel;
        RenderLayer skinLayer = playerModel.getLayer(skin.texture());
        context.draw(vertexConsumers -> {
            playerModel.render(
                    context.getMatrices(),
                    vertexConsumers.getBuffer(skinLayer),
                    LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    OverlayTexture.DEFAULT_UV
            );
            if (skin.capeTexture() != null) {
                RenderLayer capeLayer = RenderLayer.getEntitySolid(skin.capeTexture());
                this.capeModel.render(
                        context.getMatrices(),
                        vertexConsumers.getBuffer(capeLayer),
                        LightmapTextureManager.MAX_LIGHT_COORDINATE,
                        OverlayTexture.DEFAULT_UV
                );
            }
        });

        context.getMatrices().pop();
        context.draw();
        DiffuseLighting.enableGuiDepthLighting();
        context.getMatrices().pop();
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        this.xRotation = MathHelper.clamp(this.xRotation - (float) deltaY * ROTATION_SENSITIVITY, -ROTATION_X_LIMIT, ROTATION_X_LIMIT);
        this.yRotation += (float) deltaX * ROTATION_SENSITIVITY;
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
    }

    @Override
    public GuiNavigationPath getNavigationPath(GuiNavigation navigation) {
        return null;
    }
}
