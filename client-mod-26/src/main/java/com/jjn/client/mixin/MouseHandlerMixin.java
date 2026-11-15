package com.jjn.client.mixin;

import com.jjn.client.JjnInputState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onButton", at = @At("HEAD"))
    private void jjn$onButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS || this.minecraft == null || this.minecraft.player == null || this.minecraft.gui.screen() != null) {
            return;
        }
        int btn = info.button();
        if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            JjnInputState.recordLeftClick();
        } else if (btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            JjnInputState.recordRightClick();
        }
    }
}
