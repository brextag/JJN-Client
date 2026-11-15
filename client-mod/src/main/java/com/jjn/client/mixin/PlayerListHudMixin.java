package com.jjn.client.mixin;

import com.jjn.client.ui.JjnCosmetics;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void jjnclient$prefixTabListName(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(JjnCosmetics.prefixWithIcon(cir.getReturnValue()));
    }
}
