package com.jjn.client.ui;

import com.jjn.client.friends.JjnFriendsManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

import java.util.List;

/**
 * Pause-menu "Host World to Friends" screen for Minecraft 1.21.4.
 */
public class JjnHostWorldScreen extends Screen {

    private static final GameMode[] MODES = {
            GameMode.SURVIVAL, GameMode.CREATIVE, GameMode.ADVENTURE, GameMode.SPECTATOR
    };
    private static final String[] MODE_LABELS = {
            "Survival", "Creative", "Adventure", "Spectator"
    };

    private final Screen parent;
    private int modeIdx = 0;
    private boolean allowCheats = false;
    private boolean friendsOnly = true;
    private int scrollOffset = 0;
    private int tickCounter = 0;
    private List<JjnFriendsManager.PeerStatus> cachedFriends = List.of();
    private String lastSignature = "";
    private String feedbackMsg = "";

    private static final int ROWS_PER_PAGE = 3;

    public JjnHostWorldScreen(Screen parent) {
        super(Text.literal("Host World to Friends"));
        this.parent = parent;
        this.friendsOnly = JjnFriendsManager.get().isHostedFriendsOnly();
    }

    private int panelW() { return Math.min(480, this.width - 24); }
    private int panelH() { return Math.min(318, this.height - 20); }
    private int panelX() { return (this.width - panelW()) / 2; }
    private int panelY() { return (this.height - panelH()) / 2; }

    @Override
    protected void init() {
        JjnFriendsManager mgr = JjnFriendsManager.get();
        boolean hosting = mgr.isHosting();
        if (hosting) {
            friendsOnly = mgr.isHostedFriendsOnly();
        }

        int px = panelX();
        int py = panelY();
        int pw = panelW();
        int ph = panelH();
        int bx = px + 14;
        int bw = pw - 28;

        this.addDrawableChild(new JjnButton(
                px + pw - 62, py + 8, 52, 20,
                Text.literal("DONE"),
                btn -> this.close(),
                true
        ));

        this.addDrawableChild(new JjnButton(
                px + pw - 138, py + 8, 70, 20,
                Text.literal("Friends"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new JjnFriendsScreen(this));
                    }
                }
        ));

        int optY = py + 36;
        int optW = (bw - 12) / 3;

        this.addDrawableChild(new JjnButton(
                bx, optY, optW, 22,
                Text.literal("Mode: " + MODE_LABELS[modeIdx]),
                btn -> {
                    modeIdx = (modeIdx + 1) % MODES.length;
                    this.clearAndInit();
                }
        ));

        this.addDrawableChild(new JjnButton(
                bx + optW + 6, optY, optW, 22,
                Text.literal("Cheats: " + (allowCheats ? "ON" : "OFF")),
                btn -> {
                    allowCheats = !allowCheats;
                    this.clearAndInit();
                },
                allowCheats
        ));

        this.addDrawableChild(new JjnButton(
                bx + (optW + 6) * 2, optY, optW, 22,
                Text.literal(friendsOnly ? "Access: Friends" : "Access: Anyone"),
                btn -> {
                    friendsOnly = !friendsOnly;
                    if (mgr.isHosting()) {
                        mgr.setHostedFriendsOnly(friendsOnly);
                    }
                    this.clearAndInit();
                },
                friendsOnly
        ));

        int actY = optY + 28;
        if (!hosting) {
            this.addDrawableChild(new JjnButton(
                    bx, actY, bw, 24,
                    Text.literal("START HOSTING TO FRIENDS"),
                    btn -> {
                        String err = mgr.startHosting(this.client, MODES[modeIdx], allowCheats, friendsOnly);
                        feedbackMsg = err.isEmpty() ? "World is now live for friends!" : err;
                        this.clearAndInit();
                    },
                    true
            ));
        } else {
            int copyW = 114;
            int stopW = bw - copyW - 8;
            this.addDrawableChild(new JjnButton(
                    bx, actY, stopW, 24,
                    Text.literal("STOP HOSTING WORLD"),
                    btn -> {
                        mgr.stopHosting(this.client);
                        feedbackMsg = "Stopped hosting.";
                        this.clearAndInit();
                    }
            ));
            this.addDrawableChild(new JjnButton(
                    bx + stopW + 8, actY, copyW, 24,
                    Text.literal("Copy IP:Port"),
                    btn -> {
                        String ep = mgr.getHostedDirectEndpoint();
                        if (this.client != null && !ep.isBlank()) {
                            this.client.keyboard.setClipboard(ep);
                            feedbackMsg = "Copied " + ep + " to clipboard!";
                        }
                    },
                    true
            ));
        }

        cachedFriends = mgr.getFriendsWithStatus();
        lastSignature = buildSignature(mgr, cachedFriends);

        int maxOffset = Math.max(0, cachedFriends.size() - ROWS_PER_PAGE);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset));

        int listY = py + 150;
        int rowH = 38;
        int rowGap = 5;
        int end = Math.min(cachedFriends.size(), scrollOffset + ROWS_PER_PAGE);

        for (int i = scrollOffset; i < end; i++) {
            JjnFriendsManager.PeerStatus friend = cachedFriends.get(i);
            int slot = i - scrollOffset;
            int ry = listY + slot * (rowH + rowGap);

            int invW = 72;
            JjnButton inviteBtn = new JjnButton(
                    bx + bw - invW - 6, ry + 8, invW, 22,
                    Text.literal(hosting ? "INVITE" : "Host first"),
                    btn -> {
                        if (mgr.isHosting()) {
                            mgr.inviteFriend(friend);
                            feedbackMsg = "Invited " + friend.username + "!";
                        }
                    },
                    hosting && friend.online
            );
            inviteBtn.active = hosting && friend.online;
            this.addDrawableChild(inviteBtn);
        }

        int pagerY = py + ph - 28;
        JjnButton upBtn = new JjnButton(
                bx, pagerY, 68, 20,
                Text.literal("< Up"),
                btn -> {
                    scrollOffset = Math.max(0, scrollOffset - 1);
                    this.clearAndInit();
                }
        );
        upBtn.active = scrollOffset > 0;
        this.addDrawableChild(upBtn);

        JjnButton downBtn = new JjnButton(
                bx + bw - 68, pagerY, 68, 20,
                Text.literal("Down >"),
                btn -> {
                    scrollOffset = Math.min(maxOffset, scrollOffset + 1);
                    this.clearAndInit();
                }
        );
        downBtn.active = scrollOffset < maxOffset;
        this.addDrawableChild(downBtn);
    }

    @Override
    public void tick() {
        super.tick();
        if (++tickCounter % 20 == 0) {
            JjnFriendsManager mgr = JjnFriendsManager.get();
            List<JjnFriendsManager.PeerStatus> latest = mgr.getFriendsWithStatus();
            String sig = buildSignature(mgr, latest);
            if (!sig.equals(lastSignature)) {
                this.clearAndInit();
            }
        }
    }

    private static String buildSignature(JjnFriendsManager mgr, List<JjnFriendsManager.PeerStatus> list) {
        StringBuilder sb = new StringBuilder();
        sb.append(mgr.isHosting()).append('|').append(mgr.isRelayConnected()).append('|')
                .append(mgr.getHostedDirectEndpoint()).append('|').append(mgr.getHostedUpnpStatus()).append('|');
        for (JjnFriendsManager.PeerStatus p : list) {
            sb.append(p.uuid).append(':').append(p.online).append(':').append(p.state).append(';');
        }
        return sb.toString();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!cachedFriends.isEmpty()) {
            int maxOffset = Math.max(0, cachedFriends.size() - ROWS_PER_PAGE);
            int delta = verticalAmount > 0 ? -1 : (verticalAmount < 0 ? 1 : 0);
            int next = Math.max(0, Math.min(maxOffset, scrollOffset + delta));
            if (next != scrollOffset) {
                scrollOffset = next;
                this.clearAndInit();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, 0xCC101116, 0xDD15171E);

        int px = panelX();
        int py = panelY();
        int pw = panelW();
        int ph = panelH();
        int bx = px + 14;
        int bw = pw - 28;

        context.fill(px, py, px + pw, py + ph, 0xF0191B23);
        context.fill(px, py, px + pw, py + 2, 0xFFC7D2FE);
        context.drawBorder(px, py, pw, ph, 0xFF353949);

        context.drawText(this.textRenderer, "HOST WORLD TO FRIENDS", bx, py + 13, 0xFFFFFFFF, true);

        JjnFriendsManager mgr = JjnFriendsManager.get();
        int statusY = py + 94;
        context.fill(bx, statusY, bx + bw, statusY + 38, 0xDD14161D);
        context.drawBorder(bx, statusY, bw, 38, mgr.isHosting() ? 0xFF86EFAC : 0xFF353949);

        if (mgr.isHosting()) {
            String line1 = "LIVE: \"" + mgr.getHostedWorldName() + "\"  •  Port " + mgr.getHostedPort()
                    + (mgr.isHostedFriendsOnly() ? " (Friends Only)" : " (Open)");
            context.drawText(this.textRenderer, line1, bx + 8, statusY + 7, 0xFF86EFAC, true);
            String relayTag = mgr.isRelayConnected() ? "Relay Tunnel ACTIVE" : "LAN / VPN / UPnP";
            String upnp = mgr.getHostedUpnpStatus();
            String line2 = relayTag + (upnp.isEmpty() ? "" : "  •  " + upnp);
            context.drawText(this.textRenderer, trimToWidth(line2, bw - 16), bx + 8, statusY + 21, 0xFFC7D2FE, false);
        } else {
            context.drawText(this.textRenderer, "Status: Not hosting yet", bx + 8, statusY + 7, 0xFFE5E7EB, true);
            String relayNote = mgr.isRelayConnected()
                    ? "Relay connected — friends anywhere online can join & receive invites."
                    : "LAN / VPN / UPnP ready (add a Relay in Friends tab for internet tunnels).";
            context.drawText(this.textRenderer, trimToWidth(relayNote, bw - 16), bx + 8, statusY + 21, 0xFF9CA3AF, false);
        }

        String note = !feedbackMsg.isEmpty() ? feedbackMsg : mgr.getNotice();
        context.drawText(this.textRenderer, "Invite Online Friends", bx, py + 138, 0xFFFFFFFF, true);
        if (!note.isEmpty()) {
            int nw = this.textRenderer.getWidth(note);
            context.drawText(this.textRenderer, note, Math.max(bx + 120, bx + bw - nw), py + 138, 0xFF86EFAC, false);
        }

        int listY = py + 150;
        int rowH = 38;
        int rowGap = 5;

        if (cachedFriends.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    "No friends added yet. Click 'Friends' at top-right to add friends.",
                    px + pw / 2, listY + 46, 0xFF9CA3AF);
        } else {
            int end = Math.min(cachedFriends.size(), scrollOffset + ROWS_PER_PAGE);
            for (int i = scrollOffset; i < end; i++) {
                JjnFriendsManager.PeerStatus f = cachedFriends.get(i);
                int slot = i - scrollOffset;
                int ry = listY + slot * (rowH + rowGap);

                context.fill(bx, ry, bx + bw, ry + rowH, 0xDD14161D);
                context.drawBorder(bx, ry, bw, rowH, f.online ? 0xFF353949 : 0xFF232632);
                context.fill(bx, ry, bx + 3, ry + rowH, f.online ? 0xFF86EFAC : 0xFF4B5563);

                context.drawText(this.textRenderer, f.username, bx + 10, ry + 7, f.online ? 0xFFF8FAFC : 0xFF9CA3AF, true);
                String act = f.online
                        ? (f.state + (f.mcVersion.isBlank() ? "" : " • MC " + f.mcVersion))
                        : "Offline";
                context.drawText(this.textRenderer, trimToWidth(act, bw - 100), bx + 10, ry + 20, 0xFF9CA3AF, false);
            }
        }

        String footer = cachedFriends.isEmpty() ? "0 friends"
                : String.format("%d-%d of %d friend(s)",
                scrollOffset + 1, Math.min(cachedFriends.size(), scrollOffset + ROWS_PER_PAGE), cachedFriends.size());
        context.drawCenteredTextWithShadow(this.textRenderer, footer, px + pw / 2, py + ph - 22, 0xFF85899A);

        super.render(context, mouseX, mouseY, delta);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (text == null) return "";
        if (this.textRenderer.getWidth(text) <= maxWidth) return text;
        String ell = "...";
        int limit = Math.max(12, maxWidth - this.textRenderer.getWidth(ell));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (this.textRenderer.getWidth(sb.toString() + c) > limit) break;
            sb.append(c);
        }
        return sb + ell;
    }
}
