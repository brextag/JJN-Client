package com.jjn.client.mixin;

import com.jjn.client.friends.JjnFriendsManager;
import com.jjn.client.ui.JjnEmoteWheelScreen;
import com.jjn.client.ui.JjnModMenuScreen;
import com.jjn.client.ui.JjnWaypointEditScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void jjn$onKey(long window, int action, KeyEvent keyEvent, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS && this.minecraft != null && this.minecraft.gui.screen() == null && this.minecraft.player != null) {
            if (keyEvent.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                this.minecraft.gui.setScreen(new JjnModMenuScreen(null));
            } else if (keyEvent.key() == GLFW.GLFW_KEY_U) {
                this.minecraft.gui.setScreen(new JjnWaypointEditScreen(null, null));
            } else if (keyEvent.key() == GLFW.GLFW_KEY_B) {
                this.minecraft.gui.setScreen(new JjnEmoteWheelScreen(null));
            } else if (keyEvent.key() == GLFW.GLFW_KEY_J) {
                JjnFriendsManager.WorldInvite inv = JjnFriendsManager.get().getLatestToastInvite();
                if (inv != null) {
                    JjnFriendsManager.get().joinInvite(this.minecraft, inv);
                }
            }
        }
    }
}
