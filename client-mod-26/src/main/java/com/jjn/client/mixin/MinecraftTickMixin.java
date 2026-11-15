package com.jjn.client.mixin;

import com.jjn.client.JjnConfig;
import com.jjn.client.JjnInputState;
import com.jjn.client.friends.JjnFriendsManager;
import com.jjn.client.shop.JjnEconomyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftTickMixin {

    @Shadow
    @Nullable
    public LocalPlayer player;

    @Inject(method = "tick", at = @At("TAIL"))
    private void jjn$onTick(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        JjnFriendsManager.get().tick(mc);
        JjnEconomyManager.get().tick(mc);
        JjnInputState.updateZoomTick(mc);

        if (!JjnConfig.get().toggleSprint || this.player == null) {
            return;
        }
        if (mc.options.keyUp.isDown()
                && !this.player.isShiftKeyDown()
                && !this.player.isUsingItem()
                && this.player.getFoodData().getFoodLevel() > 6) {
            this.player.setSprinting(true);
        }
    }
}
