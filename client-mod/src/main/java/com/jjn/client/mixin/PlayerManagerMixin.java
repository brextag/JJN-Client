package com.jjn.client.mixin;

import com.jjn.client.friends.JjnFriendsManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {

    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "checkCanJoin", at = @At("HEAD"), cancellable = true)
    private void jjn$enforceFriendsOnly(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Text> cir) {
        JjnFriendsManager mgr = JjnFriendsManager.get();
        if (this.server != null && this.server.isHost(profile)) {
            return;
        }
        if (mgr.isHostingClosedLock()) {
            cir.setReturnValue(Text.literal("The host stopped sharing this world."));
            return;
        }
        if (!mgr.isFriendsOnlyLockActive()) {
            return;
        }
        if (!mgr.isAllowedToJoin(profile.getId(), profile.getName())) {
            cir.setReturnValue(Text.literal("This JJN Client world is Friends Only. Ask the host to add or invite you!"));
        }
    }
}
