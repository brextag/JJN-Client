package com.jjn.client.mixin;

import com.jjn.client.friends.JjnFriendsManager;
import com.jjn.client.ui.JjnButton;
import com.jjn.client.ui.JjnFriendsScreen;
import com.jjn.client.ui.JjnHostWorldScreen;
import com.jjn.client.ui.JjnModMenuScreen;
import com.jjn.client.ui.JjnWaypointsScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void jjn$addPauseButtons(CallbackInfo ci) {
        JjnFriendsManager mgr = JjnFriendsManager.get();
        int alerts = mgr.getPendingInvites().size() + mgr.getIncomingRequests().size();
        String friendsLabel = alerts > 0 ? "FRIENDS (" + alerts + ")" : "FRIENDS";
        int friendsW = alerts > 0 ? 78 : 64;

        this.addRenderableWidget(new JjnButton(
                10, 10, friendsW, 22,
                Component.literal(friendsLabel),
                btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new JjnFriendsScreen(
                                this,
                                alerts > 0 ? JjnFriendsScreen.Tab.REQUESTS : JjnFriendsScreen.Tab.FRIENDS
                        ));
                    }
                },
                alerts > 0
        ));

        if (this.minecraft != null && this.minecraft.getSingleplayerServer() != null) {
            boolean hosting = mgr.isHosting();
            int hostW = hosting ? 108 : 104;
            this.addRenderableWidget(new JjnButton(
                    10 + friendsW + 6, 10, hostW, 22,
                    Component.literal(hosting ? "HOSTING • INVITE" : "HOST TO FRIENDS"),
                    btn -> {
                        if (this.minecraft != null) {
                            this.minecraft.gui.setScreen(new JjnHostWorldScreen(this));
                        }
                    },
                    hosting
            ));
        }

        int wpW = 76;
        int modsW = 104;
        int hudW = 108;
        int rightMargin = 10;

        this.addRenderableWidget(new JjnButton(
                this.width - rightMargin - hudW, 10, hudW, 22,
                Component.literal("JJN MODS & HUD"),
                btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new JjnModMenuScreen(this, JjnModMenuScreen.Tab.MODULES));
                    }
                },
                true
        ));

        this.addRenderableWidget(new JjnButton(
                this.width - rightMargin - hudW - 6 - modsW, 10, modsW, 22,
                Component.literal("INSTALLED MODS"),
                btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new JjnModMenuScreen(this, JjnModMenuScreen.Tab.MODS));
                    }
                }
        ));

        this.addRenderableWidget(new JjnButton(
                this.width - rightMargin - hudW - 6 - modsW - 6 - wpW, 10, wpW, 22,
                Component.literal("WAYPOINTS"),
                btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new JjnWaypointsScreen(this));
                    }
                }
        ));

        int shopW = 114;
        this.addRenderableWidget(new JjnButton(
                this.width - rightMargin - shopW, 36, shopW, 22,
                Component.literal("SHOP & EMOTES"),
                btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new JjnModMenuScreen(this, JjnModMenuScreen.Tab.SHOP));
                    }
                }
        ));
    }
}
