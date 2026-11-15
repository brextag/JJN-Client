package com.jjn.client.mixin;

import com.jjn.client.JjnConfig;
import com.jjn.client.ui.JjnCosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void jjnclient$applyLocalSkinAndCape(CallbackInfoReturnable<PlayerSkin> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player != (Object) this) {
            return;
        }
        PlayerSkin base = cir.getReturnValue();
        if (base == null) {
            return;
        }

        JjnConfig cfg = JjnConfig.get();
        ClientAsset.Texture customSkin = JjnCosmetics.getCustomSkinAsset(mc);
        ClientAsset.Texture customCape = JjnCosmetics.getCustomCapeAsset(mc);

        if (customSkin == null && customCape == null) {
            return;
        }

        ClientAsset.Texture body = customSkin != null ? customSkin : base.body();
        ClientAsset.Texture cape = customCape != null ? customCape : base.cape();
        PlayerModelType model = customSkin != null
                ? ("slim".equalsIgnoreCase(cfg.skinVariant) ? PlayerModelType.SLIM : PlayerModelType.WIDE)
                : base.model();

        cir.setReturnValue(new PlayerSkin(body, cape, base.elytra(), model, base.secure()));
    }
}
