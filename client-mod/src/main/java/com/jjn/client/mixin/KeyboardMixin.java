package com.jjn.client.mixin;

import com.jjn.client.friends.JjnFriendsManager;
import com.jjn.client.ui.JjnEmoteWheelScreen;
import com.jjn.client.ui.JjnModMenuScreen;
import com.jjn.client.ui.JjnWaypointEditScreen;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onKey", at = @At("HEAD"))
    private void jjn$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS && this.client != null && this.client.currentScreen == null && this.client.player != null) {
            if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                this.client.setScreen(new JjnModMenuScreen(null));
            } else if (key == GLFW.GLFW_KEY_U) {
                this.client.setScreen(new JjnWaypointEditScreen(null, null));
            } else if (key == GLFW.GLFW_KEY_B) {
                this.client.setScreen(new JjnEmoteWheelScreen(null));
            } else if (key == GLFW.GLFW_KEY_J) {
                JjnFriendsManager.WorldInvite inv = JjnFriendsManager.get().getLatestToastInvite();
                if (inv != null) {
                    JjnFriendsManager.get().joinInvite(this.client, inv);
                }
            }
        }
    }
}
