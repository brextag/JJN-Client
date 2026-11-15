package com.jjn.client.mixin;

import com.jjn.client.ui.JjnCosmetics;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void jjnclient$prefixPlayerDisplayName(CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(JjnCosmetics.prefixWithIcon(cir.getReturnValue()));
    }
}
