package com.jjn.client.mixin;

import com.jjn.client.friends.JjnFriendsManager;
import com.jjn.client.ui.JjnButton;
import com.jjn.client.ui.JjnFriendsScreen;
import com.jjn.client.ui.JjnHostWorldScreen;
import com.jjn.client.ui.JjnModMenuScreen;
import com.jjn.client.ui.JjnWaypointsScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void jjn$addPauseButtons(CallbackInfo ci) {
        JjnFriendsManager mgr = JjnFriendsManager.get();
        int alerts = mgr.getPendingInvites().size() + mgr.getIncomingRequests().size();
        String friendsLabel = alerts > 0 ? "FRIENDS (" + alerts + ")" : "FRIENDS";
        int friendsW = alerts > 0 ? 78 : 64;

        this.addDrawableChild(new JjnButton(
                10, 10, friendsW, 22,
                Text.literal(friendsLabel),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new JjnFriendsScreen(
                                this,
                                alerts > 0 ? JjnFriendsScreen.Tab.REQUESTS : JjnFriendsScreen.Tab.FRIENDS
                        ));
                    }
                },
                alerts > 0
        ));

        if (this.client != null && this.client.getServer() != null) {
            boolean hosting = mgr.isHosting();
            int hostW = hosting ? 108 : 104;
            this.addDrawableChild(new JjnButton(
                    10 + friendsW + 6, 10, hostW, 22,
                    Text.literal(hosting ? "HOSTING • INVITE" : "HOST TO FRIENDS"),
                    btn -> {
                        if (this.client != null) {
                            this.client.setScreen(new JjnHostWorldScreen(this));
                        }
                    },
                    hosting
            ));
        }

        int wpW = 76;
        int modsW = 104;
        int hudW = 108;
        int rightMargin = 10;

        this.addDrawableChild(new JjnButton(
                this.width - rightMargin - hudW, 10, hudW, 22,
                Text.literal("JJN MODS & HUD"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new JjnModMenuScreen(this, JjnModMenuScreen.Tab.MODULES));
                    }
                },
                true
        ));

        this.addDrawableChild(new JjnButton(
                this.width - rightMargin - hudW - 6 - modsW, 10, modsW, 22,
                Text.literal("INSTALLED MODS"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new JjnModMenuScreen(this, JjnModMenuScreen.Tab.MODS));
                    }
                }
        ));

        this.addDrawableChild(new JjnButton(
                this.width - rightMargin - hudW - 6 - modsW - 6 - wpW, 10, wpW, 22,
                Text.literal("WAYPOINTS"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new JjnWaypointsScreen(this));
                    }
                }
        ));

        int shopW = 114;
        this.addDrawableChild(new JjnButton(
                this.width - rightMargin - shopW, 36, shopW, 22,
                Text.literal("SHOP & EMOTES"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new JjnModMenuScreen(this, JjnModMenuScreen.Tab.SHOP));
                    }
                }
        ));
    }
}
