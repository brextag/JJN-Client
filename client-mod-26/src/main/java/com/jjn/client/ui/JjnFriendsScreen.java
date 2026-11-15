package com.jjn.client.ui;

import com.jjn.client.friends.JjnFriendsManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * In-game Friends screen for Minecraft 26.2:
 * - Add friends by username
 * - See live activity & hosted worlds with 1-click Join / Invite
 * - Accept/decline friend requests & world invites
 * - Configure Relay server host:port right in-game
 */
public class JjnFriendsScreen extends Screen {

    public enum Tab {
        FRIENDS, REQUESTS, RELAY
    }

    private final Screen parent;
    private Tab activeTab = Tab.FRIENDS;
    private int scrollOffset = 0;
    private int tickCounter = 0;

    private EditBox addFriendBox;
    private EditBox relayBox;
    private String draftFriendName = "";
    private String draftRelayUrl;

    private List<JjnFriendsManager.PeerStatus> friends = List.of();
    private List<JjnFriendsManager.WorldInvite> invites = List.of();
    private List<JjnFriendsManager.FriendRequestEntry> incomingReqs = List.of();
    private List<JjnFriendsManager.PeerStatus> nearbyLan = List.of();
    private String lastSignature = "";

    private static final int ROWS_PER_PAGE = 4;

    public JjnFriendsScreen(Screen parent) {
        this(parent, Tab.FRIENDS);
    }

    public JjnFriendsScreen(Screen parent, Tab initialTab) {
        super(Component.literal("JJN Friends"));
        this.parent = parent;
        this.activeTab = initialTab;
        String saved = JjnFriendsManager.get().getRelayUrl();
        this.draftRelayUrl = (saved == null || saved.isBlank())
                ? JjnFriendsManager.get().getEffectiveRelayUrl()
                : saved;
    }

    private int panelW() { return Math.min(504, this.width - 20); }
    private int panelH() { return Math.min(324, this.height - 18); }
    private int panelX() { return (this.width - panelW()) / 2; }
    private int panelY() { return (this.height - panelH()) / 2; }

    @Override
    protected void init() {
        if (addFriendBox != null) draftFriendName = addFriendBox.getValue();
        if (relayBox != null) draftRelayUrl = relayBox.getValue();
        this.clearWidgets();

        JjnFriendsManager mgr = JjnFriendsManager.get();
        friends = mgr.getFriendsWithStatus();
        invites = mgr.getPendingInvites();
        incomingReqs = mgr.getIncomingRequests();
        nearbyLan = mgr.getNearbyLanPlayers();
        lastSignature = buildSignature(mgr);

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

        // Tabs row
        int tabY = py + 34;
        int tabW = (bw - 12) / 3;
        int alertCount = invites.size() + incomingReqs.size();

        this.addRenderableWidget(new JjnButton(
                bx, tabY, tabW, 21,
                Component.literal("Friends (" + friends.size() + ")"),
                btn -> {
                    activeTab = Tab.FRIENDS;
                    scrollOffset = 0;
                    this.rebuildWidgets();
                },
                activeTab == Tab.FRIENDS
        ));

        this.addRenderableWidget(new JjnButton(
                bx + tabW + 6, tabY, tabW, 21,
                Component.literal("Invites / Req" + (alertCount > 0 ? " (" + alertCount + ")" : "")),
                btn -> {
                    activeTab = Tab.REQUESTS;
                    scrollOffset = 0;
                    this.rebuildWidgets();
                },
                activeTab == Tab.REQUESTS
        ));

        this.addRenderableWidget(new JjnButton(
                bx + (tabW + 6) * 2, tabY, tabW, 21,
                Component.literal(mgr.isRelayConnected() ? "Relay: Online" : "Relay Setup"),
                btn -> {
                    activeTab = Tab.RELAY;
                    scrollOffset = 0;
                    this.rebuildWidgets();
                },
                activeTab == Tab.RELAY
        ));

        addFriendBox = null;
        relayBox = null;

        if (activeTab == Tab.FRIENDS) {
            initFriendsTab(mgr, bx, bw, py, ph);
        } else if (activeTab == Tab.REQUESTS) {
            initRequestsTab(mgr, bx, bw, py, ph);
        } else {
            initRelayTab(mgr, bx, bw, py);
        }
    }

    private void initFriendsTab(JjnFriendsManager mgr, int bx, int bw, int py, int ph) {
        int barY = py + 62;
        int addBtnW = 86;
        addFriendBox = new EditBox(this.font, bx, barY, bw - addBtnW - 6, 20, Component.literal("Username"));
        addFriendBox.setMaxLength(20);
        addFriendBox.setHint(Component.literal("Add friend by Minecraft username..."));
        addFriendBox.setValue(draftFriendName);
        addFriendBox.setResponder(v -> draftFriendName = v);
        this.addRenderableWidget(addFriendBox);

        this.addRenderableWidget(new JjnButton(
                bx + bw - addBtnW, barY, addBtnW, 20,
                Component.literal("+ Add Friend"),
                btn -> submitAddFriend(),
                true
        ));

        int maxOffset = Math.max(0, friends.size() - ROWS_PER_PAGE);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset));

        int listY = py + 90;
        int rowH = 44;
        int rowGap = 5;
        boolean weHosting = mgr.isHosting();

        int end = Math.min(friends.size(), scrollOffset + ROWS_PER_PAGE);
        for (int i = scrollOffset; i < end; i++) {
            JjnFriendsManager.PeerStatus p = friends.get(i);
            int slot = i - scrollOffset;
            int ry = listY + slot * (rowH + rowGap);

            int rightX = bx + bw - 6;
            int delW = 32;
            this.addRenderableWidget(new JjnButton(
                    rightX - delW, ry + 11, delW, 22,
                    Component.literal("Del"),
                    btn -> {
                        mgr.removeFriend(p.uuid);
                        this.rebuildWidgets();
                    }
            ));
            rightX -= (delW + 4);

            if (p.isJoinable()) {
                int joinW = 50;
                this.addRenderableWidget(new JjnButton(
                        rightX - joinW, ry + 11, joinW, 22,
                        Component.literal("JOIN"),
                        btn -> mgr.joinPeer(this.minecraft, p),
                        true
                ));
                rightX -= (joinW + 4);
            }

            if (weHosting && p.online) {
                int invW = 50;
                this.addRenderableWidget(new JjnButton(
                        rightX - invW, ry + 11, invW, 22,
                        Component.literal("Invite"),
                        btn -> mgr.inviteFriend(p),
                        true
                ));
            }
        }

        addPager(bx, bw, py + ph - 28, maxOffset);
    }

    private void initRequestsTab(JjnFriendsManager mgr, int bx, int bw, int py, int ph) {
        int listY = py + 66;
        int rowH = 40;
        int rowGap = 5;
        int totalRows = invites.size() + incomingReqs.size() + nearbyLan.size();
        int maxOffset = Math.max(0, totalRows - ROWS_PER_PAGE);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset));

        int start = scrollOffset;
        int end = Math.min(totalRows, start + ROWS_PER_PAGE);

        for (int idx = start; idx < end; idx++) {
            int slot = idx - start;
            int ry = listY + slot * (rowH + rowGap);
            int rightX = bx + bw - 6;

            if (idx < invites.size()) {
                JjnFriendsManager.WorldInvite inv = invites.get(idx);
                this.addRenderableWidget(new JjnButton(
                        rightX - 42, ry + 9, 42, 22,
                        Component.literal("Hide"),
                        btn -> {
                            mgr.dismissInvite(inv);
                            this.rebuildWidgets();
                        }
                ));
                this.addRenderableWidget(new JjnButton(
                        rightX - 42 - 4 - 54, ry + 9, 54, 22,
                        Component.literal("JOIN"),
                        btn -> mgr.joinInvite(this.minecraft, inv),
                        true
                ));
            } else if (idx < invites.size() + incomingReqs.size()) {
                JjnFriendsManager.FriendRequestEntry req = incomingReqs.get(idx - invites.size());
                this.addRenderableWidget(new JjnButton(
                        rightX - 54, ry + 9, 54, 22,
                        Component.literal("Decline"),
                        btn -> {
                            mgr.declineRequest(req.uuid);
                            this.rebuildWidgets();
                        }
                ));
                this.addRenderableWidget(new JjnButton(
                        rightX - 54 - 4 - 56, ry + 9, 56, 22,
                        Component.literal("Accept"),
                        btn -> {
                            mgr.acceptRequest(req.uuid);
                            this.rebuildWidgets();
                        },
                        true
                ));
            } else {
                JjnFriendsManager.PeerStatus lp = nearbyLan.get(idx - invites.size() - incomingReqs.size());
                this.addRenderableWidget(new JjnButton(
                        rightX - 72, ry + 9, 72, 22,
                        Component.literal("+ Add Friend"),
                        btn -> {
                            mgr.addFriendByName(lp.username);
                            this.rebuildWidgets();
                        },
                        true
                ));
            }
        }

        addPager(bx, bw, py + ph - 28, maxOffset);
    }

    private void initRelayTab(JjnFriendsManager mgr, int bx, int bw, int py) {
        int y = py + 82;
        relayBox = new EditBox(this.font, bx, y, bw, 22, Component.literal("Relay Host:Port"));
        relayBox.setMaxLength(120);
        relayBox.setHint(Component.literal("e.g. play.yourdomain.com:25580 (empty = LAN/VPN)"));
        relayBox.setValue(draftRelayUrl != null ? draftRelayUrl : "");
        relayBox.setResponder(v -> draftRelayUrl = v);
        this.addRenderableWidget(relayBox);

        int third = (bw - 12) / 3;
        this.addRenderableWidget(new JjnButton(
                bx, y + 30, third, 24,
                Component.literal("SAVE & CONNECT"),
                btn -> {
                    mgr.setRelayUrl(relayBox != null ? relayBox.getValue() : draftRelayUrl);
                    this.rebuildWidgets();
                },
                true
        ));

        this.addRenderableWidget(new JjnButton(
                bx + third + 6, y + 30, third, 24,
                Component.literal("TEST RELAY"),
                btn -> {
                    String url = relayBox != null ? relayBox.getValue() : draftRelayUrl;
                    mgr.testRelayConnection(url);
                    this.rebuildWidgets();
                },
                true
        ));

        this.addRenderableWidget(new JjnButton(
                bx + (third + 6) * 2, y + 30, bw - (third + 6) * 2, 24,
                Component.literal("LAN / VPN Only"),
                btn -> {
                    draftRelayUrl = "";
                    mgr.setRelayUrl("");
                    this.rebuildWidgets();
                }
        ));
    }

    private void addPager(int bx, int bw, int pagerY, int maxOffset) {
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

    private void submitAddFriend() {
        String name = addFriendBox != null ? addFriendBox.getValue().trim() : draftFriendName.trim();
        if (!name.isEmpty()) {
            JjnFriendsManager.get().addFriendByName(name);
            draftFriendName = "";
            if (addFriendBox != null) addFriendBox.setValue("");
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (++tickCounter % 15 == 0) {
            // Only rebuild automatically when no EditBox currently holds text input focus, OR keep draft values safely
            if ((addFriendBox == null || !addFriendBox.isFocused())
                    && (relayBox == null || !relayBox.isFocused())) {
                JjnFriendsManager mgr = JjnFriendsManager.get();
                String sig = buildSignature(mgr);
                if (!sig.equals(lastSignature)) {
                    this.rebuildWidgets();
                }
            }
        }
    }

    private String buildSignature(JjnFriendsManager mgr) {
        StringBuilder sb = new StringBuilder();
        sb.append(mgr.isRelayConnected()).append('|').append(mgr.isHosting()).append('|')
                .append(mgr.isRelayTestPending()).append('|').append(mgr.getRelayTestStatus()).append('|')
                .append(mgr.getPendingInvites().size()).append('|').append(mgr.getIncomingRequests().size())
                .append('|').append(mgr.getNearbyLanPlayers().size()).append('|');
        for (JjnFriendsManager.PeerStatus p : mgr.getFriendsWithStatus()) {
            sb.append(p.uuid).append(':').append(p.online).append(':').append(p.hosting)
                    .append(':').append(p.state).append(';');
        }
        return sb.toString();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (activeTab == Tab.FRIENDS && addFriendBox != null && addFriendBox.isFocused()) {
                submitAddFriend();
                return true;
            }
            if (activeTab == Tab.RELAY && relayBox != null && relayBox.isFocused()) {
                JjnFriendsManager.get().setRelayUrl(relayBox.getValue());
                this.rebuildWidgets();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int count = activeTab == Tab.FRIENDS ? friends.size()
                : (activeTab == Tab.REQUESTS ? (invites.size() + incomingReqs.size() + nearbyLan.size()) : 0);
        if (count > ROWS_PER_PAGE) {
            int maxOffset = count - ROWS_PER_PAGE;
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

        graphics.text(this.font, "JJN FRIENDS", bx, py + 13, 0xFFFFFFFF, true);

        JjnFriendsManager mgr = JjnFriendsManager.get();
        String notice = mgr.getNotice();
        String topInfo = !notice.isEmpty() ? notice : mgr.getRelayStatusText();
        int topColor = !notice.isEmpty() ? 0xFF86EFAC : (mgr.isRelayConnected() ? 0xFF86EFAC : 0xFFC7D2FE);
        graphics.text(this.font, trimToWidth(topInfo, pw - 165), bx + 86, py + 13, topColor, false);

        if (activeTab == Tab.FRIENDS) {
            renderFriendsRows(graphics, bx, bw, py, ph);
        } else if (activeTab == Tab.REQUESTS) {
            renderRequestsRows(graphics, bx, bw, py, ph);
        } else {
            renderRelayInfo(graphics, mgr, bx, bw, py);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void renderFriendsRows(GuiGraphicsExtractor graphics, int bx, int bw, int py, int ph) {
        int listY = py + 90;
        int rowH = 44;
        int rowGap = 5;

        if (friends.isEmpty()) {
            graphics.centeredText(this.font,
                    "No friends added yet. Enter a Minecraft username above!",
                    bx + bw / 2, listY + 66, 0xFF9CA3AF);
        } else {
            int end = Math.min(friends.size(), scrollOffset + ROWS_PER_PAGE);
            for (int i = scrollOffset; i < end; i++) {
                JjnFriendsManager.PeerStatus p = friends.get(i);
                int slot = i - scrollOffset;
                int ry = listY + slot * (rowH + rowGap);

                int barColor = p.hosting ? 0xFF86EFAC : (p.online ? 0xFFC7D2FE : 0xFF4B5563);
                graphics.fill(bx, ry, bx + bw, ry + rowH, 0xDD14161D);
                graphics.outline(bx, ry, bw, rowH, p.online ? 0xFF353949 : 0xFF232632);
                graphics.fill(bx, ry, bx + 3, ry + rowH, barColor);

                graphics.blit(RenderPipelines.GUI_TEXTURED, JjnCosmetics.CREEPER_GUI_ICON,
                        bx + 10, ry + 8, 0.0F, 0.0F, 9, 9, 9, 9);
                String nameLine = p.username + (p.hosting ? "  [HOSTING]" : (p.online ? "" : "  (Offline)"));
                graphics.text(this.font, trimToWidth(nameLine, bw - 165), bx + 23, ry + 9,
                        p.online ? 0xFFF8FAFC : 0xFF9CA3AF, true);

                String sub = formatActivity(p);
                graphics.text(this.font, trimToWidth(sub, bw - 155), bx + 10, ry + 24,
                        p.hosting ? 0xFF86EFAC : 0xFF9CA3AF, false);
            }
        }

        String footer = friends.isEmpty() ? "0 friends"
                : String.format("%d-%d of %d friend(s)",
                scrollOffset + 1, Math.min(friends.size(), scrollOffset + ROWS_PER_PAGE), friends.size());
        graphics.centeredText(this.font, footer, bx + bw / 2, py + ph - 22, 0xFF85899A);
    }

    private void renderRequestsRows(GuiGraphicsExtractor graphics, int bx, int bw, int py, int ph) {
        int listY = py + 66;
        int rowH = 40;
        int rowGap = 5;
        int total = invites.size() + incomingReqs.size() + nearbyLan.size();

        if (total == 0) {
            graphics.centeredText(this.font,
                    "No pending world invites, friend requests, or nearby LAN players.",
                    bx + bw / 2, listY + 66, 0xFF9CA3AF);
        } else {
            int end = Math.min(total, scrollOffset + ROWS_PER_PAGE);
            for (int idx = scrollOffset; idx < end; idx++) {
                int slot = idx - scrollOffset;
                int ry = listY + slot * (rowH + rowGap);

                graphics.fill(bx, ry, bx + bw, ry + rowH, 0xDD14161D);
                graphics.outline(bx, ry, bw, rowH, 0xFF353949);

                if (idx < invites.size()) {
                    JjnFriendsManager.WorldInvite inv = invites.get(idx);
                    graphics.fill(bx, ry, bx + 3, ry + rowH, 0xFF86EFAC);
                    graphics.text(this.font, trimToWidth("INVITE: " + inv.fromName + " -> \"" + inv.worldName + "\"", bw - 120),
                            bx + 10, ry + 8, 0xFFF8FAFC, true);
                    graphics.text(this.font, "MC " + (inv.mcVersion.isBlank() ? "26.2" : inv.mcVersion) + "  •  Click JOIN to connect",
                            bx + 10, ry + 22, 0xFF86EFAC, false);
                } else if (idx < invites.size() + incomingReqs.size()) {
                    JjnFriendsManager.FriendRequestEntry req = incomingReqs.get(idx - invites.size());
                    graphics.fill(bx, ry, bx + 3, ry + rowH, 0xFFC7D2FE);
                    graphics.text(this.font, trimToWidth("Friend Request from " + req.username, bw - 130),
                            bx + 10, ry + 8, 0xFFF8FAFC, true);
                    graphics.text(this.font, "Accept to see live worlds & join",
                            bx + 10, ry + 22, 0xFF9CA3AF, false);
                } else {
                    JjnFriendsManager.PeerStatus lp = nearbyLan.get(idx - invites.size() - incomingReqs.size());
                    graphics.fill(bx, ry, bx + 3, ry + rowH, 0xFF67E8F9);
                    graphics.text(this.font, trimToWidth("Nearby LAN Player: " + lp.username, bw - 100),
                            bx + 10, ry + 8, 0xFFF8FAFC, true);
                    graphics.text(this.font, trimToWidth(formatActivity(lp), bw - 100),
                            bx + 10, ry + 22, 0xFF9CA3AF, false);
                }
            }
        }

        graphics.centeredText(this.font, total + " notification(s)", bx + bw / 2, py + ph - 22, 0xFF85899A);
    }

    private void renderRelayInfo(GuiGraphicsExtractor graphics, JjnFriendsManager mgr, int bx, int bw, int py) {
        graphics.text(this.font, "JJN Relay Server Address (host:port)", bx, py + 68, 0xFFE5E7EB, true);
        String testMsg = mgr.getRelayTestStatus();
        if (testMsg != null && !testMsg.isBlank()) {
            int testColor = mgr.isRelayTestPending() ? 0xFFC7D2FE : (mgr.isRelayTestOk() ? 0xFF86EFAC : 0xFFFCA5A5);
            graphics.text(this.font, trimToWidth(testMsg, bw), bx, py + 140, testColor, false);
        }
        int boxY = py + 156;
        graphics.fill(bx, boxY, bx + bw, boxY + 96, 0xDD14161D);
        graphics.outline(bx, boxY, bw, 96, 0xFF353949);
        graphics.text(this.font, "How World Hosting Works:", bx + 10, boxY + 10, 0xFFC7D2FE, true);
        graphics.text(this.font, "• Same Wi-Fi / LAN / Tailscale / Radmin: Works automatically with empty Relay.", bx + 10, boxY + 28, 0xFFD1D5DB, false);
        graphics.text(this.font, "• UPnP: Pause Menu -> 'Host to Friends' opens router port automatically.", bx + 10, boxY + 44, 0xFFD1D5DB, false);
        graphics.text(this.font, "• Cloud Relay: Docker / Railway / Fly.io ('friends-relay/') on port 25580.", bx + 10, boxY + 60, 0xFFD1D5DB, false);
        graphics.text(this.font, trimToWidth("Status: " + mgr.getRelayStatusText(), bw - 20), bx + 10, boxY + 76,
                mgr.isRelayConnected() ? 0xFF86EFAC : 0xFF9CA3AF, false);
    }

    private static String formatActivity(JjnFriendsManager.PeerStatus p) {
        if (!p.online) return "Offline";
        String ver = (p.mcVersion != null && !p.mcVersion.isBlank()) ? "MC " + p.mcVersion : "";
        String world = p.worldOrServer == null ? "" : p.worldOrServer.trim();
        if (p.hosting) return (ver.isEmpty() ? "" : ver + " • ") + (world.isEmpty() ? "Hosting Private World" : world);
        if ("game".equals(p.role)) {
            if (!ver.isEmpty() && !world.isEmpty()) return ver + " • " + world;
            if (!ver.isEmpty()) return "Playing " + ver;
            if (!world.isEmpty()) return world;
            return p.state;
        }
        return p.state.isBlank() ? "In Launcher" : p.state;
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
