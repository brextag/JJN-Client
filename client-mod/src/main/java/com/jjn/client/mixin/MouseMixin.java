package com.jjn.client.mixin;

import com.jjn.client.JjnInputState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void jjn$onMouseButton(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS || this.client == null || this.client.player == null || this.client.currentScreen != null) {
            return;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            JjnInputState.recordLeftClick();
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            JjnInputState.recordRightClick();
        }
    }
}
