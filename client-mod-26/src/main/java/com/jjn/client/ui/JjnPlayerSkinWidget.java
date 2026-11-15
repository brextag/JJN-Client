package com.jjn.client.ui;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Quaternionf;

import java.util.function.Supplier;

/**
 * Interactive 3D player model widget with cape rendering support for JJN Client (26.2).
 * Vanilla PlayerSkinWidget omits capes; this pairs the baked PLAYER_CAPE layer via GuiSkinRendererMixin.
 */
public class JjnPlayerSkinWidget extends AbstractWidget {

    private static final float MODEL_HEIGHT = 2.125F;
    private static final float FIT_SCALE = 0.97F;
    private static final float ROTATION_SENSITIVITY = 2.5F;
    private static final float ROTATION_X_LIMIT = 50.0F;

    private final Model.Simple wideModel;
    private final Model.Simple slimModel;
    private final Model.Simple capeModel;
    private final Supplier<PlayerSkin> skinSupplier;

    private float rotationX = -5.0F;
    private float rotationY = 30.0F;

    public JjnPlayerSkinWidget(int width, int height, EntityModelSet models, Supplier<PlayerSkin> skinSupplier) {
        super(0, 0, width, height, CommonComponents.EMPTY);
        this.wideModel = new Model.Simple(models.bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent);
        this.slimModel = new Model.Simple(models.bakeLayer(ModelLayers.PLAYER_SLIM), RenderTypes::entityTranslucent);

        ModelPart capeRoot = models.bakeLayer(ModelLayers.PLAYER_CAPE);
        poseCapePart(capeRoot);
        this.capeModel = new Model.Simple(capeRoot, RenderTypes::entitySolid);

        this.skinSupplier = skinSupplier;
    }

    private static void poseCapePart(ModelPart root) {
        if (root.hasChild("body")) {
            ModelPart body = root.getChild("body");
            if (body.hasChild("cape")) {
                ModelPart cape = body.getChild("cape");
                cape.rotateBy(new Quaternionf()
                        .rotateY((float) -Math.PI)
                        .rotateX((float) Math.toRadians(6.0))
                        .rotateY((float) Math.PI));
            }
        }
    }

    public void flipFrontBack() {
        this.rotationY = (this.rotationY + 180.0F) % 360.0F;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float scale = FIT_SCALE * (float) this.getHeight() / MODEL_HEIGHT;
        PlayerSkin skin = this.skinSupplier.get();
        Model.Simple bodyModel = skin.model() == PlayerModelType.SLIM ? this.slimModel : this.wideModel;

        JjnCosmetics.activePreviewBodyModel = bodyModel;
        if (skin.cape() != null) {
            JjnCosmetics.activePreviewCapeModel = this.capeModel;
            JjnCosmetics.activePreviewCapeTexture = skin.cape().texturePath();
        } else {
            JjnCosmetics.activePreviewCapeModel = null;
            JjnCosmetics.activePreviewCapeTexture = null;
        }

        graphics.skin(
                bodyModel,
                skin.body().texturePath(),
                scale,
                this.rotationX,
                this.rotationY,
                -1.0625F,
                this.getX(),
                this.getY(),
                this.getRight(),
                this.getBottom()
        );
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double deltaX, double deltaY) {
        this.rotationX = Mth.clamp(this.rotationX - (float) deltaY * ROTATION_SENSITIVITY, -ROTATION_X_LIMIT, ROTATION_X_LIMIT);
        this.rotationY += (float) deltaX * ROTATION_SENSITIVITY;
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return null;
    }
}
