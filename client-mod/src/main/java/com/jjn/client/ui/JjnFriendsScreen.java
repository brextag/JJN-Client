package com.jjn.client.ui;

import com.jjn.client.friends.JjnFriendsManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * In-game Friends screen for Minecraft 1.21.4:
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

    private TextFieldWidget addFriendBox;
    private TextFieldWidget relayBox;
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
        super(Text.literal("JJN Friends"));
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
        if (addFriendBox != null) draftFriendName = addFriendBox.getText();
        if (relayBox != null) draftRelayUrl = relayBox.getText();

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

        this.addDrawableChild(new JjnButton(
                px + pw - 62, py + 8, 52, 20,
                Text.literal("DONE"),
                btn -> this.close(),
                true
        ));

        int tabY = py + 34;
        int tabW = (bw - 12) / 3;
        int alertCount = invites.size() + incomingReqs.size();

        this.addDrawableChild(new JjnButton(
                bx, tabY, tabW, 21,
                Text.literal("Friends (" + friends.size() + ")"),
                btn -> {
                    activeTab = Tab.FRIENDS;
                    scrollOffset = 0;
                    this.clearAndInit();
                },
                activeTab == Tab.FRIENDS
        ));

        this.addDrawableChild(new JjnButton(
                bx + tabW + 6, tabY, tabW, 21,
                Text.literal("Invites / Req" + (alertCount > 0 ? " (" + alertCount + ")" : "")),
                btn -> {
                    activeTab = Tab.REQUESTS;
                    scrollOffset = 0;
                    this.clearAndInit();
                },
                activeTab == Tab.REQUESTS
        ));

        this.addDrawableChild(new JjnButton(
                bx + (tabW + 6) * 2, tabY, tabW, 21,
                Text.literal(mgr.isRelayConnected() ? "Relay: Online" : "Relay Setup"),
                btn -> {
                    activeTab = Tab.RELAY;
                    scrollOffset = 0;
                    this.clearAndInit();
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
        addFriendBox = new TextFieldWidget(this.textRenderer, bx, barY, bw - addBtnW - 6, 20, Text.literal("Username"));
        addFriendBox.setMaxLength(20);
        addFriendBox.setPlaceholder(Text.literal("Add friend by Minecraft username..."));
        addFriendBox.setText(draftFriendName);
        addFriendBox.setChangedListener(v -> draftFriendName = v);
        this.addDrawableChild(addFriendBox);

        this.addDrawableChild(new JjnButton(
                bx + bw - addBtnW, barY, addBtnW, 20,
                Text.literal("+ Add Friend"),
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
            this.addDrawableChild(new JjnButton(
                    rightX - delW, ry + 11, delW, 22,
                    Text.literal("Del"),
                    btn -> {
                        mgr.removeFriend(p.uuid);
                        this.clearAndInit();
                    }
            ));
            rightX -= (delW + 4);

            if (p.isJoinable()) {
                int joinW = 50;
                this.addDrawableChild(new JjnButton(
                        rightX - joinW, ry + 11, joinW, 22,
                        Text.literal("JOIN"),
                        btn -> mgr.joinPeer(this.client, p),
                        true
                ));
                rightX -= (joinW + 4);
            }

            if (weHosting && p.online) {
                int invW = 50;
                this.addDrawableChild(new JjnButton(
                        rightX - invW, ry + 11, invW, 22,
                        Text.literal("Invite"),
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
                this.addDrawableChild(new JjnButton(
                        rightX - 42, ry + 9, 42, 22,
                        Text.literal("Hide"),
                        btn -> {
                            mgr.dismissInvite(inv);
                            this.clearAndInit();
                        }
                ));
                this.addDrawableChild(new JjnButton(
                        rightX - 42 - 4 - 54, ry + 9, 54, 22,
                        Text.literal("JOIN"),
                        btn -> mgr.joinInvite(this.client, inv),
                        true
                ));
            } else if (idx < invites.size() + incomingReqs.size()) {
                JjnFriendsManager.FriendRequestEntry req = incomingReqs.get(idx - invites.size());
                this.addDrawableChild(new JjnButton(
                        rightX - 54, ry + 9, 54, 22,
                        Text.literal("Decline"),
                        btn -> {
                            mgr.declineRequest(req.uuid);
                            this.clearAndInit();
                        }
                ));
                this.addDrawableChild(new JjnButton(
                        rightX - 54 - 4 - 56, ry + 9, 56, 22,
                        Text.literal("Accept"),
                        btn -> {
                            mgr.acceptRequest(req.uuid);
                            this.clearAndInit();
                        },
                        true
                ));
            } else {
                JjnFriendsManager.PeerStatus lp = nearbyLan.get(idx - invites.size() - incomingReqs.size());
                this.addDrawableChild(new JjnButton(
                        rightX - 72, ry + 9, 72, 22,
                        Text.literal("+ Add Friend"),
                        btn -> {
                            mgr.addFriendByName(lp.username);
                            this.clearAndInit();
                        },
                        true
                ));
            }
        }

        addPager(bx, bw, py + ph - 28, maxOffset);
    }

    private void initRelayTab(JjnFriendsManager mgr, int bx, int bw, int py) {
        int y = py + 82;
        relayBox = new TextFieldWidget(this.textRenderer, bx, y, bw, 22, Text.literal("Relay Host:Port"));
        relayBox.setMaxLength(120);
        relayBox.setPlaceholder(Text.literal("e.g. play.yourdomain.com:25580 (empty = LAN/VPN)"));
        relayBox.setText(draftRelayUrl != null ? draftRelayUrl : "");
        relayBox.setChangedListener(v -> draftRelayUrl = v);
        this.addDrawableChild(relayBox);

        int third = (bw - 12) / 3;
        this.addDrawableChild(new JjnButton(
                bx, y + 30, third, 24,
                Text.literal("SAVE & CONNECT"),
                btn -> {
                    mgr.setRelayUrl(relayBox != null ? relayBox.getText() : draftRelayUrl);
                    this.clearAndInit();
                },
                true
        ));

        this.addDrawableChild(new JjnButton(
                bx + third + 6, y + 30, third, 24,
                Text.literal("TEST RELAY"),
                btn -> {
                    String url = relayBox != null ? relayBox.getText() : draftRelayUrl;
                    mgr.testRelayConnection(url);
                    this.clearAndInit();
                },
                true
        ));

        this.addDrawableChild(new JjnButton(
                bx + (third + 6) * 2, y + 30, bw - (third + 6) * 2, 24,
                Text.literal("LAN / VPN Only"),
                btn -> {
                    draftRelayUrl = "";
                    mgr.setRelayUrl("");
                    this.clearAndInit();
                }
        ));
    }

    private void addPager(int bx, int bw, int pagerY, int maxOffset) {
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

    private void submitAddFriend() {
        String name = addFriendBox != null ? addFriendBox.getText().trim() : draftFriendName.trim();
        if (!name.isEmpty()) {
            JjnFriendsManager.get().addFriendByName(name);
            draftFriendName = "";
            if (addFriendBox != null) addFriendBox.setText("");
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (++tickCounter % 15 == 0) {
            if ((addFriendBox == null || !addFriendBox.isFocused())
                    && (relayBox == null || !relayBox.isFocused())) {
                JjnFriendsManager mgr = JjnFriendsManager.get();
                String sig = buildSignature(mgr);
                if (!sig.equals(lastSignature)) {
                    this.clearAndInit();
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (activeTab == Tab.FRIENDS && addFriendBox != null && addFriendBox.isFocused()) {
                submitAddFriend();
                return true;
            }
            if (activeTab == Tab.RELAY && relayBox != null && relayBox.isFocused()) {
                JjnFriendsManager.get().setRelayUrl(relayBox.getText());
                this.clearAndInit();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

        context.drawText(this.textRenderer, "JJN FRIENDS", bx, py + 13, 0xFFFFFFFF, true);

        JjnFriendsManager mgr = JjnFriendsManager.get();
        String notice = mgr.getNotice();
        String topInfo = !notice.isEmpty() ? notice : mgr.getRelayStatusText();
        int topColor = !notice.isEmpty() ? 0xFF86EFAC : (mgr.isRelayConnected() ? 0xFF86EFAC : 0xFFC7D2FE);
        context.drawText(this.textRenderer, trimToWidth(topInfo, pw - 165), bx + 86, py + 13, topColor, false);

        if (activeTab == Tab.FRIENDS) {
            renderFriendsRows(context, bx, bw, py, ph);
        } else if (activeTab == Tab.REQUESTS) {
            renderRequestsRows(context, bx, bw, py, ph);
        } else {
            renderRelayInfo(context, mgr, bx, bw, py);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderFriendsRows(DrawContext context, int bx, int bw, int py, int ph) {
        int listY = py + 90;
        int rowH = 44;
        int rowGap = 5;

        if (friends.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    "No friends added yet. Enter a Minecraft username above!",
                    bx + bw / 2, listY + 66, 0xFF9CA3AF);
        } else {
            int end = Math.min(friends.size(), scrollOffset + ROWS_PER_PAGE);
            for (int i = scrollOffset; i < end; i++) {
                JjnFriendsManager.PeerStatus p = friends.get(i);
                int slot = i - scrollOffset;
                int ry = listY + slot * (rowH + rowGap);

                int barColor = p.hosting ? 0xFF86EFAC : (p.online ? 0xFFC7D2FE : 0xFF4B5563);
                context.fill(bx, ry, bx + bw, ry + rowH, 0xDD14161D);
                context.drawBorder(bx, ry, bw, rowH, p.online ? 0xFF353949 : 0xFF232632);
                context.fill(bx, ry, bx + 3, ry + rowH, barColor);

                context.drawTexture(RenderLayer::getGuiTextured, JjnCosmetics.CREEPER_GUI_ICON,
                        bx + 10, ry + 8, 0.0F, 0.0F, 9, 9, 9, 9);
                String nameLine = p.username + (p.hosting ? "  [HOSTING]" : (p.online ? "" : "  (Offline)"));
                context.drawText(this.textRenderer, trimToWidth(nameLine, bw - 165), bx + 23, ry + 9,
                        p.online ? 0xFFF8FAFC : 0xFF9CA3AF, true);

                String sub = formatActivity(p);
                context.drawText(this.textRenderer, trimToWidth(sub, bw - 155), bx + 10, ry + 24,
                        p.hosting ? 0xFF86EFAC : 0xFF9CA3AF, false);
            }
        }

        String footer = friends.isEmpty() ? "0 friends"
                : String.format("%d-%d of %d friend(s)",
                scrollOffset + 1, Math.min(friends.size(), scrollOffset + ROWS_PER_PAGE), friends.size());
        context.drawCenteredTextWithShadow(this.textRenderer, footer, bx + bw / 2, py + ph - 22, 0xFF85899A);
    }

    private void renderRequestsRows(DrawContext context, int bx, int bw, int py, int ph) {
        int listY = py + 66;
        int rowH = 40;
        int rowGap = 5;
        int total = invites.size() + incomingReqs.size() + nearbyLan.size();

        if (total == 0) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    "No pending world invites, friend requests, or nearby LAN players.",
                    bx + bw / 2, listY + 66, 0xFF9CA3AF);
        } else {
            int end = Math.min(total, scrollOffset + ROWS_PER_PAGE);
            for (int idx = scrollOffset; idx < end; idx++) {
                int slot = idx - scrollOffset;
                int ry = listY + slot * (rowH + rowGap);

                context.fill(bx, ry, bx + bw, ry + rowH, 0xDD14161D);
                context.drawBorder(bx, ry, bw, rowH, 0xFF353949);

                if (idx < invites.size()) {
                    JjnFriendsManager.WorldInvite inv = invites.get(idx);
                    context.fill(bx, ry, bx + 3, ry + rowH, 0xFF86EFAC);
                    context.drawText(this.textRenderer, trimToWidth("INVITE: " + inv.fromName + " -> \"" + inv.worldName + "\"", bw - 120),
                            bx + 10, ry + 8, 0xFFF8FAFC, true);
                    context.drawText(this.textRenderer, "MC " + (inv.mcVersion.isBlank() ? "1.21.4" : inv.mcVersion) + "  •  Click JOIN to connect",
                            bx + 10, ry + 22, 0xFF86EFAC, false);
                } else if (idx < invites.size() + incomingReqs.size()) {
                    JjnFriendsManager.FriendRequestEntry req = incomingReqs.get(idx - invites.size());
                    context.fill(bx, ry, bx + 3, ry + rowH, 0xFFC7D2FE);
                    context.drawText(this.textRenderer, trimToWidth("Friend Request from " + req.username, bw - 130),
                            bx + 10, ry + 8, 0xFFF8FAFC, true);
                    context.drawText(this.textRenderer, "Accept to see live worlds & join",
                            bx + 10, ry + 22, 0xFF9CA3AF, false);
                } else {
                    JjnFriendsManager.PeerStatus lp = nearbyLan.get(idx - invites.size() - incomingReqs.size());
                    context.fill(bx, ry, bx + 3, ry + rowH, 0xFF67E8F9);
                    context.drawText(this.textRenderer, trimToWidth("Nearby LAN Player: " + lp.username, bw - 100),
                            bx + 10, ry + 8, 0xFFF8FAFC, true);
                    context.drawText(this.textRenderer, trimToWidth(formatActivity(lp), bw - 100),
                            bx + 10, ry + 22, 0xFF9CA3AF, false);
                }
            }
        }

        context.drawCenteredTextWithShadow(this.textRenderer, total + " notification(s)", bx + bw / 2, py + ph - 22, 0xFF85899A);
    }

    private void renderRelayInfo(DrawContext context, JjnFriendsManager mgr, int bx, int bw, int py) {
        context.drawText(this.textRenderer, "JJN Relay Server Address (host:port)", bx, py + 68, 0xFFE5E7EB, true);
        String testMsg = mgr.getRelayTestStatus();
        if (testMsg != null && !testMsg.isBlank()) {
            int testColor = mgr.isRelayTestPending() ? 0xFFC7D2FE : (mgr.isRelayTestOk() ? 0xFF86EFAC : 0xFFFCA5A5);
            context.drawText(this.textRenderer, trimToWidth(testMsg, bw), bx, py + 140, testColor, false);
        }
        int boxY = py + 156;
        context.fill(bx, boxY, bx + bw, boxY + 96, 0xDD14161D);
        context.drawBorder(bx, boxY, bw, 96, 0xFF353949);
        context.drawText(this.textRenderer, "How World Hosting Works:", bx + 10, boxY + 10, 0xFFC7D2FE, true);
        context.drawText(this.textRenderer, "• Same Wi-Fi / LAN / Tailscale / Radmin: Works automatically with empty Relay.", bx + 10, boxY + 28, 0xFFD1D5DB, false);
        context.drawText(this.textRenderer, "• UPnP: Pause Menu -> 'Host to Friends' opens router port automatically.", bx + 10, boxY + 44, 0xFFD1D5DB, false);
        context.drawText(this.textRenderer, "• Cloud Relay: Docker / Railway / Fly.io ('friends-relay/') on port 25580.", bx + 10, boxY + 60, 0xFFD1D5DB, false);
        context.drawText(this.textRenderer, trimToWidth("Status: " + mgr.getRelayStatusText(), bw - 20), bx + 10, boxY + 76,
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
