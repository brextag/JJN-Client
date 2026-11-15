package com.jjn.client.mixin;

import com.jjn.client.JjnConfig;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {

    @Shadow
    private boolean dirty;

    @Unique
    private boolean jjn$lastFullbright;

    @Inject(method = "update", at = @At("HEAD"))
    private void jjn$markDirtyOnFullbrightToggle(float delta, CallbackInfo ci) {
        boolean fb = JjnConfig.get().fullbright;
        if (fb != this.jjn$lastFullbright) {
            this.dirty = true;
            this.jjn$lastFullbright = fb;
        }
    }

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z",
                    ordinal = 0
            )
    )
    private boolean jjn$forceFullbrightNightVision(ClientPlayerEntity player, RegistryEntry<StatusEffect> effect) {
        return JjnConfig.get().fullbright || player.hasStatusEffect(effect);
    }
}
