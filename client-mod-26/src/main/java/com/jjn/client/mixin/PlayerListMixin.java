package com.jjn.client.mixin;

import com.jjn.client.friends.JjnFriendsManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
    private void jjn$enforceFriendsOnly(SocketAddress address, NameAndId nameAndId, CallbackInfoReturnable<Component> cir) {
        JjnFriendsManager mgr = JjnFriendsManager.get();
        if (!mgr.isFriendsOnlyLockActive()) {
            return;
        }
        if (this.server != null && this.server.isSingleplayerOwner(nameAndId)) {
            return;
        }
        if (!mgr.isAllowedToJoin(nameAndId.id(), nameAndId.name())) {
            cir.setReturnValue(Component.literal("This JJN Client world is Friends Only. Ask the host to add or invite you!"));
        }
    }
}
