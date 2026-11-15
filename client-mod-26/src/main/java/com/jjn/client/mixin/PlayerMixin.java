package com.jjn.client.mixin;

import com.jjn.client.ui.JjnCosmetics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void jjnclient$prefixPlayerDisplayName(CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(JjnCosmetics.prefixWithIcon(cir.getReturnValue()));
    }
}
