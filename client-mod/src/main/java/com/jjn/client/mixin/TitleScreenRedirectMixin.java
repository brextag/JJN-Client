package com.jjn.client.mixin;

import com.jjn.client.JjnConfig;
import com.jjn.client.ui.JjnTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftClient.class)
public abstract class TitleScreenRedirectMixin {

    @Shadow
    @Nullable
    public ClientWorld world;

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen jjn$replaceTitleScreen(Screen screen) {
        if (!JjnConfig.get().customTitleScreen) {
            return screen;
        }
        if (screen instanceof TitleScreen && !(screen instanceof JjnTitleScreen)) {
            return new JjnTitleScreen();
        }
        if (screen == null && this.world == null) {
            return new JjnTitleScreen();
        }
        return screen;
    }
}
