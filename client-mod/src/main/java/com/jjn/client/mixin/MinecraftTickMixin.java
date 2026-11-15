package com.jjn.client.mixin;

import com.jjn.client.JjnConfig;
import com.jjn.client.friends.JjnFriendsManager;
import com.jjn.client.shop.JjnEconomyManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftTickMixin {

    @Shadow
    @Nullable
    public ClientPlayerEntity player;

    @Inject(method = "tick", at = @At("TAIL"))
    private void jjn$onTick(CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient) (Object) this;
        JjnFriendsManager.get().tick(mc);
        JjnEconomyManager.get().tick(mc);
        if (!JjnConfig.get().toggleSprint || this.player == null) {
            return;
        }
        if (mc.options.forwardKey.isPressed()
                && !this.player.isSneaking()
                && !this.player.isUsingItem()
                && this.player.getHungerManager().getFoodLevel() > 6) {
            this.player.setSprinting(true);
        }
    }
}
