package com.jjn.client.ui;

import com.jjn.client.friends.JjnFriendsManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

import java.util.List;

/**
 * Pause-menu "Host World to Friends" screen for Minecraft 26.2:
 * Configure Gamemode, Cheats, Friends-Only lock, start/stop hosting (LAN + UPnP + Relay Tunnel),
 * and send 1-click world invites to friends.
 */
public class JjnHostWorldScreen extends Screen {

    private static final GameType[] MODES = {
            GameType.SURVIVAL, GameType.CREATIVE, GameType.ADVENTURE, GameType.SPECTATOR
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
        super(Component.literal("Host World to Friends"));
        this.parent = parent;
        this.friendsOnly = JjnFriendsManager.get().isHostedFriendsOnly();
    }

    private int panelW() { return Math.min(480, this.width - 24); }
    private int panelH() { return Math.min(318, this.height - 20); }
    private int panelX() { return (this.width - panelW()) / 2; }
    private int panelY() { return (this.height - panelH()) / 2; }

    @Override
    protected void init() {
        this.clearWidgets();

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

        this.addRenderableWidget(new JjnButton(
                px + pw - 62, py + 8, 52, 20,
                Component.literal("DONE"),
                btn -> this.onClose(),
                true
        ));

        this.addRenderableWidget(new JjnButton(
                px + pw - 138, py + 8, 70, 20,
                Component.literal("Friends"),
                btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new JjnFriendsScreen(this));
                    }
                }
        ));

        // Row 1: Gamemode | Cheats | Friends-Only Access
        int optY = py + 36;
        int optW = (bw - 12) / 3;

        this.addRenderableWidget(new JjnButton(
                bx, optY, optW, 22,
                Component.literal("Mode: " + MODE_LABELS[modeIdx]),
                btn -> {
                    modeIdx = (modeIdx + 1) % MODES.length;
                    this.rebuildWidgets();
                }
        ));

        this.addRenderableWidget(new JjnButton(
                bx + optW + 6, optY, optW, 22,
                Component.literal("Cheats: " + (allowCheats ? "ON" : "OFF")),
                btn -> {
                    allowCheats = !allowCheats;
                    this.rebuildWidgets();
                },
                allowCheats
        ));

        this.addRenderableWidget(new JjnButton(
                bx + (optW + 6) * 2, optY, optW, 22,
                Component.literal(friendsOnly ? "Access: Friends" : "Access: Anyone"),
                btn -> {
                    friendsOnly = !friendsOnly;
                    if (mgr.isHosting()) {
                        mgr.setHostedFriendsOnly(friendsOnly);
                    }
                    this.rebuildWidgets();
                },
                friendsOnly
        ));

        // Row 2: Start/Stop Hosting + Copy Address
        int actY = optY + 28;
        if (!hosting) {
            this.addRenderableWidget(new JjnButton(
                    bx, actY, bw, 24,
                    Component.literal("START HOSTING TO FRIENDS"),
                    btn -> {
                        String err = mgr.startHosting(this.minecraft, MODES[modeIdx], allowCheats, friendsOnly);
                        feedbackMsg = err.isEmpty() ? "World is now live for friends!" : err;
                        this.rebuildWidgets();
                    },
                    true
            ));
        } else {
            int copyW = 114;
            int stopW = bw - copyW - 8;
            this.addRenderableWidget(new JjnButton(
                    bx, actY, stopW, 24,
                    Component.literal("STOP HOSTING WORLD"),
                    btn -> {
                        mgr.stopHosting(this.minecraft);
                        feedbackMsg = "Stopped hosting.";
                        this.rebuildWidgets();
                    }
            ));
            this.addRenderableWidget(new JjnButton(
                    bx + stopW + 8, actY, copyW, 24,
                    Component.literal("Copy IP:Port"),
                    btn -> {
                        String ep = mgr.getHostedDirectEndpoint();
                        if (this.minecraft != null && !ep.isBlank()) {
                            this.minecraft.keyboardHandler.setClipboard(ep);
                            feedbackMsg = "Copied " + ep + " to clipboard!";
                        }
                    },
                    true
            ));
        }

        // Friends Invite List
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
                    Component.literal(hosting ? "INVITE" : "Host first"),
                    btn -> {
                        if (mgr.isHosting()) {
                            mgr.inviteFriend(friend);
                            feedbackMsg = "Invited " + friend.username + "!";
                        }
                    },
                    hosting && friend.online
            );
            inviteBtn.active = hosting && friend.online;
            this.addRenderableWidget(inviteBtn);
        }

        int pagerY = py + ph - 28;
        JjnButton upBtn = new JjnButton(
                bx, pagerY, 68, 20,
                Component.literal("< Up"),
                btn -> {
                    scrollOffset = Math.max(0, scrollOffset - 1);
                    this.rebuildWidgets();
                }
        );
        upBtn.active = scrollOffset > 0;
        this.addRenderableWidget(upBtn);

        JjnButton downBtn = new JjnButton(
                bx + bw - 68, pagerY, 68, 20,
                Component.literal("Down >"),
                btn -> {
                    scrollOffset = Math.min(maxOffset, scrollOffset + 1);
                    this.rebuildWidgets();
                }
        );
        downBtn.active = scrollOffset < maxOffset;
        this.addRenderableWidget(downBtn);
    }

    @Override
    public void tick() {
        super.tick();
        if (++tickCounter % 20 == 0) {
            JjnFriendsManager mgr = JjnFriendsManager.get();
            List<JjnFriendsManager.PeerStatus> latest = mgr.getFriendsWithStatus();
            String sig = buildSignature(mgr, latest);
            if (!sig.equals(lastSignature)) {
                this.rebuildWidgets();
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
                this.rebuildWidgets();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xCC101116, 0xDD15171E);

        int px = panelX();
        int py = panelY();
        int pw = panelW();
        int ph = panelH();
        int bx = px + 14;
        int bw = pw - 28;

        graphics.fill(px, py, px + pw, py + ph, 0xF0191B23);
        graphics.fill(px, py, px + pw, py + 2, 0xFFC7D2FE);
        graphics.outline(px, py, pw, ph, 0xFF353949);

        graphics.text(this.font, "HOST WORLD TO FRIENDS", bx, py + 13, 0xFFFFFFFF, true);

        JjnFriendsManager mgr = JjnFriendsManager.get();
        int statusY = py + 94;
        graphics.fill(bx, statusY, bx + bw, statusY + 38, 0xDD14161D);
        graphics.outline(bx, statusY, bw, 38, mgr.isHosting() ? 0xFF86EFAC : 0xFF353949);

        if (mgr.isHosting()) {
            String line1 = "LIVE: \"" + mgr.getHostedWorldName() + "\"  •  Port " + mgr.getHostedPort()
                    + (mgr.isHostedFriendsOnly() ? " (Friends Only)" : " (Open)");
            graphics.text(this.font, line1, bx + 8, statusY + 7, 0xFF86EFAC, true);
            String relayTag = mgr.isRelayConnected() ? "Relay Tunnel ACTIVE" : "LAN / VPN / UPnP";
            String upnp = mgr.getHostedUpnpStatus();
            String line2 = relayTag + (upnp.isEmpty() ? "" : "  •  " + upnp);
            graphics.text(this.font, trimToWidth(line2, bw - 16), bx + 8, statusY + 21, 0xFFC7D2FE, false);
        } else {
            graphics.text(this.font, "Status: Not hosting yet", bx + 8, statusY + 7, 0xFFE5E7EB, true);
            String relayNote = mgr.isRelayConnected()
                    ? "Relay connected — friends anywhere online can join & receive invites."
                    : "LAN / VPN / UPnP ready (add a Relay in Friends tab for internet tunnels).";
            graphics.text(this.font, trimToWidth(relayNote, bw - 16), bx + 8, statusY + 21, 0xFF9CA3AF, false);
        }

        String note = !feedbackMsg.isEmpty() ? feedbackMsg : mgr.getNotice();
        graphics.text(this.font, "Invite Online Friends", bx, py + 138, 0xFFFFFFFF, true);
        if (!note.isEmpty()) {
            int nw = this.font.width(note);
            graphics.text(this.font, note, Math.max(bx + 120, bx + bw - nw), py + 138, 0xFF86EFAC, false);
        }

        int listY = py + 150;
        int rowH = 38;
        int rowGap = 5;

        if (cachedFriends.isEmpty()) {
            graphics.centeredText(this.font,
                    "No friends added yet. Click 'Friends' at top-right to add friends.",
                    px + pw / 2, listY + 46, 0xFF9CA3AF);
        } else {
            int end = Math.min(cachedFriends.size(), scrollOffset + ROWS_PER_PAGE);
            for (int i = scrollOffset; i < end; i++) {
                JjnFriendsManager.PeerStatus f = cachedFriends.get(i);
                int slot = i - scrollOffset;
                int ry = listY + slot * (rowH + rowGap);

                graphics.fill(bx, ry, bx + bw, ry + rowH, 0xDD14161D);
                graphics.outline(bx, ry, bw, rowH, f.online ? 0xFF353949 : 0xFF232632);
                graphics.fill(bx, ry, bx + 3, ry + rowH, f.online ? 0xFF86EFAC : 0xFF4B5563);

                graphics.text(this.font, f.username, bx + 10, ry + 7, f.online ? 0xFFF8FAFC : 0xFF9CA3AF, true);
                String act = f.online
                        ? (f.state + (f.mcVersion.isBlank() ? "" : " • MC " + f.mcVersion))
                        : "Offline";
                graphics.text(this.font, trimToWidth(act, bw - 100), bx + 10, ry + 20, 0xFF9CA3AF, false);
            }
        }

        String footer = cachedFriends.isEmpty() ? "0 friends"
                : String.format("%d-%d of %d friend(s)",
                scrollOffset + 1, Math.min(cachedFriends.size(), scrollOffset + ROWS_PER_PAGE), cachedFriends.size());
        graphics.centeredText(this.font, footer, px + pw / 2, py + ph - 22, 0xFF85899A);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (text == null) return "";
        if (this.font.width(text) <= maxWidth) return text;
        String ell = "...";
        int limit = Math.max(12, maxWidth - this.font.width(ell));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (this.font.width(sb.toString() + c) > limit) break;
            sb.append(c);
        }
        return sb + ell;
    }
}
