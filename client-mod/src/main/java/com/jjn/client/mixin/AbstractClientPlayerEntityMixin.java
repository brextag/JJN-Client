package com.jjn.client.mixin;

import com.jjn.client.JjnConfig;
import com.jjn.client.ui.JjnCosmetics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void jjnclient$applyLocalSkinAndCape(CallbackInfoReturnable<SkinTextures> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player != (Object) this) {
            return;
        }
        SkinTextures base = cir.getReturnValue();
        if (base == null) {
            return;
        }

        JjnConfig cfg = JjnConfig.get();
        Identifier customSkin = JjnCosmetics.getCustomSkinId(client);
        Identifier customCape = JjnCosmetics.getCustomCapeId(client);

        if (customSkin == null && customCape == null) {
            return;
        }

        Identifier texture = customSkin != null ? customSkin : base.texture();
        String textureUrl = customSkin != null ? null : base.textureUrl();
        Identifier cape = customCape != null ? customCape : base.capeTexture();
        SkinTextures.Model model = customSkin != null
                ? ("slim".equalsIgnoreCase(cfg.skinVariant) ? SkinTextures.Model.SLIM : SkinTextures.Model.WIDE)
                : base.model();

        cir.setReturnValue(new SkinTextures(texture, textureUrl, cape, base.elytraTexture(), model, base.secure()));
    }
}
