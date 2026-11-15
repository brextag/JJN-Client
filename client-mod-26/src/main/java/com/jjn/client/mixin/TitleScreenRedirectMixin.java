package com.jjn.client.mixin;

import com.jjn.client.JjnConfig;
import com.jjn.client.ui.JjnTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public abstract class TitleScreenRedirectMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private @Nullable Screen jjn$replaceTitleScreen(@Nullable Screen screen) {
        if (!JjnConfig.get().customTitleScreen) {
            return screen;
        }
        if (screen instanceof TitleScreen && !(screen instanceof JjnTitleScreen)) {
            return new JjnTitleScreen();
        }
        if (screen == null && this.minecraft.level == null) {
            return new JjnTitleScreen();
        }
        return screen;
    }
}
