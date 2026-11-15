package com.jjn.launcher.ui;

import com.jjn.launcher.friends.FriendsService;
import com.jjn.launcher.friends.FriendsStore;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Friends & Cross-World Hosting tab for JJN Client Launcher:
 *  - Add friends by Minecraft username
 *  - See live in-game / in-launcher activity & hosted worlds
 *  - Receive & respond to world invites and friend requests
 *  - 1-click Join Friend's world (via LAN, UPnP, or JJN Relay Server)
 */
public class FriendsView {

    public interface Actions {
        FriendsService friendsService();
        boolean isGameRunning();
        boolean hasSignedInAccount();
        void onJoinPeer(FriendsService.PeerStatus peer);
        void onJoinInvite(FriendsService.WorldInvite invite);
        void onShowRelayHelp();
    }

    private Actions actions;
    private TextField relayField;
    private TextField addFriendField;
    private Label relayStatusLabel;
    private Label relayTestLabel;
    private Label noticeLabel;
    private VBox invitesBox;
    private VBox requestsBox;
    private VBox friendsListBox;
    private VBox nearbyBox;

    private final Map<String, Image> headCache = new ConcurrentHashMap<>();
    private final Set<String> headLookupStarted = ConcurrentHashMap.newKeySet();

    public Node build(Actions actions) {
        this.actions = actions;
        FriendsService service = actions.friendsService();
        FriendsStore.StoreData data = service.getDataSnapshot();

        // 1. Relay & Network Connection Card
        Label netTitle = UiUtils.sectionTitle("Network & Relay Server");
        relayStatusLabel = new Label();
        relayStatusLabel.setStyle("-fx-text-fill: #c7d2fe; -fx-font-size: 12px; -fx-font-weight: bold;");

        Label netHint = UiUtils.mutedLabel(
                "LAN & VPN (Tailscale / Radmin) work automatically with zero setup. "
                        + "Enter a JJN Relay host:port below (or set JJN_RELAY_URL) to play with friends anywhere over the internet."
        );

        String effectiveRelay = service.getEffectiveRelayUrl();
        relayField = new TextField(data.relayUrl != null && !data.relayUrl.isBlank() ? data.relayUrl : effectiveRelay);
        relayField.getStyleClass().add("input-field");
        relayField.setPromptText("Relay server host:port (e.g. relay.yourdomain.com:25580 or empty for LAN)");
        HBox.setHgrow(relayField, Priority.ALWAYS);

        Button saveRelayBtn = new Button("Save & Connect");
        saveRelayBtn.getStyleClass().add("primary-button");
        saveRelayBtn.setPadding(new Insets(9, 14, 9, 14));
        saveRelayBtn.setOnAction(e -> service.setRelayUrl(relayField.getText()));
        relayField.setOnAction(e -> service.setRelayUrl(relayField.getText()));

        Button testRelayBtn = UiUtils.secondaryButton("Test Relay");
        relayTestLabel = new Label();
        relayTestLabel.setWrapText(true);
        relayTestLabel.setVisible(false);
        relayTestLabel.setManaged(false);
        testRelayBtn.setOnAction(e -> {
            testRelayBtn.setDisable(true);
            relayTestLabel.setStyle("-fx-text-fill: #c7d2fe; -fx-font-size: 12px; -fx-font-weight: bold;");
            relayTestLabel.setText("Pinging Relay server...");
            relayTestLabel.setVisible(true);
            relayTestLabel.setManaged(true);
            service.testRelayConnection(relayField.getText(), res -> Platform.runLater(() -> {
                testRelayBtn.setDisable(false);
                relayTestLabel.setStyle((res.reachable()
                        ? "-fx-text-fill: #86efac;"
                        : "-fx-text-fill: #fca5a5;")
                        + " -fx-font-size: 12px; -fx-font-weight: bold;");
                relayTestLabel.setText((res.reachable() ? "✓ " : "✕ ") + res.message());
            }));
        });

        Button clearRelayBtn = UiUtils.secondaryButton("LAN Only");
        clearRelayBtn.setOnAction(e -> {
            relayField.clear();
            service.setRelayUrl("");
            relayTestLabel.setVisible(false);
            relayTestLabel.setManaged(false);
        });

        Button relayHelpBtn = UiUtils.secondaryButton("Host Relay Info");
        relayHelpBtn.setOnAction(e -> actions.onShowRelayHelp());

        HBox relayRow = new HBox(8, relayField, saveRelayBtn, testRelayBtn, clearRelayBtn, relayHelpBtn);
        relayRow.setAlignment(Pos.CENTER_LEFT);

        noticeLabel = new Label();
        noticeLabel.setWrapText(true);
        noticeLabel.setStyle("-fx-text-fill: #86efac; -fx-font-size: 12px; -fx-font-weight: bold;");

        VBox networkCard = new VBox(8, netTitle, relayStatusLabel, netHint, relayRow, relayTestLabel, noticeLabel);
        networkCard.setPadding(new Insets(16));
        networkCard.getStyleClass().add("panel-card");

        // 2. Add Friend Bar
        Label addTitle = UiUtils.sectionTitle("Add Friend");
        addFriendField = new TextField();
        addFriendField.getStyleClass().add("input-field");
        addFriendField.setPromptText("Enter friend's Minecraft username...");
        HBox.setHgrow(addFriendField, Priority.ALWAYS);

        Button addBtn = new Button("+ Add Friend");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setPadding(new Insets(9, 16, 9, 16));
        Runnable submitAdd = () -> {
            String target = addFriendField.getText().trim();
            if (!target.isEmpty()) {
                service.addFriendByName(target);
                addFriendField.clear();
            }
        };
        addBtn.setOnAction(e -> submitAdd.run());
        addFriendField.setOnAction(e -> submitAdd.run());

        HBox addRow = new HBox(10, addFriendField, addBtn);
        addRow.setAlignment(Pos.CENTER_LEFT);

        VBox addCard = new VBox(8, addTitle, addRow);
        addCard.setPadding(new Insets(16));
        addCard.getStyleClass().add("panel-card");

        // 3. Dynamic sections (Invites, Requests, Friends, Nearby LAN)
        invitesBox = new VBox(8);
        requestsBox = new VBox(8);
        friendsListBox = new VBox(10);
        nearbyBox = new VBox(8);

        VBox scrollContent = new VBox(14, invitesBox, requestsBox, friendsListBox, nearbyBox);
        scrollContent.setPadding(new Insets(2, 4, 8, 2));

        ScrollPane scroll = new ScrollPane(scrollContent);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-pane");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox root = new VBox(14, networkCard, addCard, scroll);
        VBox.setVgrow(root, Priority.ALWAYS);

        refreshDynamicContent();
        return root;
    }

    public void refreshDynamicContent() {
        if (actions == null || friendsListBox == null) return;
        FriendsService service = actions.friendsService();
        FriendsStore.StoreData data = service.getDataSnapshot();

        String statusPrefix = service.isRelayConnected() ? "● Connected  •  " : "○ ";
        relayStatusLabel.setText(statusPrefix + service.getRelayStatusText());
        relayStatusLabel.setStyle(service.isRelayConnected()
                ? "-fx-text-fill: #86efac; -fx-font-size: 12px; -fx-font-weight: bold;"
                : "-fx-text-fill: #c7d2fe; -fx-font-size: 12px; -fx-font-weight: bold;");

        String notice = service.getLastNotice();
        if (notice == null || notice.isBlank()) {
            noticeLabel.setVisible(false);
            noticeLabel.setManaged(false);
        } else {
            noticeLabel.setText(notice);
            noticeLabel.setVisible(true);
            noticeLabel.setManaged(true);
        }

        // Invites
        invitesBox.getChildren().clear();
        List<FriendsService.WorldInvite> invites = service.getPendingInvites();
        if (invites.isEmpty()) {
            invitesBox.setVisible(false);
            invitesBox.setManaged(false);
        } else {
            invitesBox.setVisible(true);
            invitesBox.setManaged(true);
            invitesBox.getChildren().add(UiUtils.sectionTitle("World Invites (" + invites.size() + ")"));
            for (FriendsService.WorldInvite inv : invites) {
                Label title = new Label(inv.fromName + " invited you to join \"" + inv.worldName + "\"");
                title.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 13px; -fx-font-weight: bold;");
                Label sub = UiUtils.mutedLabel(
                        (inv.mcVersion != null && !inv.mcVersion.isBlank() ? "Minecraft " + inv.mcVersion + "  •  " : "")
                                + "Private world invite"
                );
                VBox info = new VBox(3, title, sub);
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Button joinBtn = new Button(actions.isGameRunning() ? "Join in Game" : "Join World");
                joinBtn.getStyleClass().add("primary-button");
                joinBtn.setPadding(new Insets(8, 14, 8, 14));
                joinBtn.setOnAction(e -> actions.onJoinInvite(inv));

                Button dismissBtn = UiUtils.secondaryButton("Dismiss");
                dismissBtn.setOnAction(e -> service.dismissInvite(inv));

                HBox card = new HBox(10, info, spacer, joinBtn, dismissBtn);
                card.setAlignment(Pos.CENTER_LEFT);
                card.setPadding(new Insets(12, 16, 12, 16));
                card.getStyleClass().add("hero-card");
                invitesBox.getChildren().add(card);
            }
        }

        // Friend Requests
        requestsBox.getChildren().clear();
        List<FriendsStore.FriendRequestEntry> incoming = data.incomingRequests;
        List<FriendsStore.FriendRequestEntry> outgoing = data.outgoingRequests;
        if (incoming.isEmpty() && outgoing.isEmpty()) {
            requestsBox.setVisible(false);
            requestsBox.setManaged(false);
        } else {
            requestsBox.setVisible(true);
            requestsBox.setManaged(true);
            requestsBox.getChildren().add(UiUtils.sectionTitle("Friend Requests"));
            for (FriendsStore.FriendRequestEntry req : incoming) {
                Label lbl = new Label(req.username + " wants to add you as a friend");
                lbl.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 13px; -fx-font-weight: bold;");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Button accept = new Button("Accept");
                accept.getStyleClass().add("primary-button");
                accept.setPadding(new Insets(7, 14, 7, 14));
                accept.setOnAction(e -> service.acceptRequest(req.uuid));
                Button decline = UiUtils.dangerButton("Decline");
                decline.setOnAction(e -> service.declineRequest(req.uuid));

                HBox card = new HBox(10, lbl, spacer, accept, decline);
                card.setAlignment(Pos.CENTER_LEFT);
                card.setPadding(new Insets(12, 16, 12, 16));
                card.getStyleClass().add("panel-card");
                requestsBox.getChildren().add(card);
            }
            for (FriendsStore.FriendRequestEntry req : outgoing) {
                Label lbl = new Label("Outgoing request to " + req.username);
                lbl.setStyle("-fx-text-fill: #a2a6b5; -fx-font-size: 12px;");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Button cancel = UiUtils.secondaryButton("Cancel");
                cancel.setOnAction(e -> service.declineRequest(req.uuid));

                HBox card = new HBox(10, lbl, spacer, cancel);
                card.setAlignment(Pos.CENTER_LEFT);
                card.setPadding(new Insets(10, 16, 10, 16));
                card.getStyleClass().add("panel-card");
                requestsBox.getChildren().add(card);
            }
        }

        // Friends List
        friendsListBox.getChildren().clear();
        List<FriendsService.PeerStatus> friends = service.getFriendsWithStatus();
        friendsListBox.getChildren().add(UiUtils.sectionTitle("Friends (" + friends.size() + ")"));

        Map<String, String> skinUrlMap = new HashMap<>();
        for (FriendsStore.FriendEntry fe : data.friends) {
            if (fe.skinUrl != null && !fe.skinUrl.isBlank()) {
                skinUrlMap.put(FriendsStore.normalizeUuid(fe.uuid), fe.skinUrl);
            }
        }

        if (friends.isEmpty()) {
            VBox emptyCard = UiUtils.card(
                    UiUtils.mutedLabel("No friends added yet. Enter a friend's Minecraft username above, or launch with friends on your LAN / Relay server.")
            );
            friendsListBox.getChildren().add(emptyCard);
        } else {
            for (FriendsService.PeerStatus friend : friends) {
                friendsListBox.getChildren().add(buildFriendRow(friend, skinUrlMap.get(friend.uuid), service));
            }
        }

        // Nearby LAN Players (not yet added)
        nearbyBox.getChildren().clear();
        List<FriendsService.PeerStatus> nearby = service.getNearbyLanPlayers();
        if (nearby.isEmpty()) {
            nearbyBox.setVisible(false);
            nearbyBox.setManaged(false);
        } else {
            nearbyBox.setVisible(true);
            nearbyBox.setManaged(true);
            nearbyBox.getChildren().add(UiUtils.sectionTitle("Nearby LAN / VPN Players (" + nearby.size() + ")"));
            for (FriendsService.PeerStatus lp : nearby) {
                Label name = new Label(lp.username);
                name.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 13px; -fx-font-weight: bold;");
                Label activity = UiUtils.mutedLabel(formatActivity(lp));
                VBox col = new VBox(2, name, activity);
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Button addBtn = UiUtils.secondaryButton("+ Add Friend");
                addBtn.setOnAction(e -> service.addFriendByName(lp.username));

                HBox row = new HBox(12, UiUtils.creeperBadgeView(18), col, spacer, addBtn);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(12, 16, 12, 16));
                row.getStyleClass().add("panel-card");
                nearbyBox.getChildren().add(row);
            }
        }
    }

    private HBox buildFriendRow(FriendsService.PeerStatus friend, String knownSkinUrl, FriendsService service) {
        Circle dot = new Circle(5);
        if (friend.online && friend.isJoinable()) {
            dot.setFill(Color.web("#86efac"));
        } else if (friend.online) {
            dot.setFill(Color.web("#c7d2fe"));
        } else {
            dot.setFill(Color.web("#4b5563"));
        }

        ImageView avatar = new ImageView();
        avatar.setFitWidth(34);
        avatar.setFitHeight(34);
        avatar.setSmooth(false);
        Image cachedHead = headCache.get(friend.uuid);
        if (cachedHead != null) {
            avatar.setImage(cachedHead);
        } else {
            avatar.setImage(UiUtils.creeperBadgeImage());
            triggerHeadLoad(friend.uuid, knownSkinUrl);
        }

        ImageView badge = UiUtils.creeperBadgeView(14);
        Label nameLbl = new Label(friend.username != null && !friend.username.isBlank() ? friend.username : friend.uuid);
        nameLbl.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label pill = new Label(badgeText(friend));
        pill.setStyle(badgeStyle(friend));

        HBox topLine = new HBox(7, badge, nameLbl, pill);
        topLine.setAlignment(Pos.CENTER_LEFT);

        Label activityLbl = UiUtils.mutedLabel(formatActivity(friend));

        VBox textCol = new VBox(3, topLine, activityLbl);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12, dot, avatar, textCol, spacer);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));
        row.getStyleClass().add("panel-card");

        if (friend.isJoinable()) {
            Button joinBtn = new Button(actions.isGameRunning() ? "Join in Game" : "Join World");
            joinBtn.getStyleClass().add("primary-button");
            joinBtn.setPadding(new Insets(8, 14, 8, 14));
            joinBtn.setOnAction(e -> actions.onJoinPeer(friend));
            row.getChildren().add(joinBtn);
        }

        Button removeBtn = UiUtils.dangerButton("Remove");
        removeBtn.setOnAction(e -> service.removeFriend(friend.uuid));
        row.getChildren().add(removeBtn);

        return row;
    }

    private void triggerHeadLoad(String uuid, String knownSkinUrl) {
        if (uuid == null || uuid.isBlank() || !headLookupStarted.add(uuid)) {
            return;
        }
        if (knownSkinUrl != null && !knownSkinUrl.isBlank()) {
            UiUtils.loadHeadAsync(knownSkinUrl, () -> true, img -> {
                headCache.put(uuid, img);
                Platform.runLater(this::refreshDynamicContent);
            });
            return;
        }
        Thread t = new Thread(() -> {
            String fetched = FriendsStore.fetchSkinUrl(uuid);
            if (fetched != null && !fetched.isBlank()) {
                UiUtils.loadHeadAsync(fetched, () -> true, img -> {
                    headCache.put(uuid, img);
                    Platform.runLater(this::refreshDynamicContent);
                });
            }
        }, "JJN-Friend-Head");
        t.setDaemon(true);
        t.start();
    }

    private static String badgeText(FriendsService.PeerStatus p) {
        if (!p.online) return "OFFLINE";
        if (p.hosting) return "HOSTING WORLD";
        if ("game".equals(p.role)) return "IN GAME";
        return "IN LAUNCHER";
    }

    private static String badgeStyle(FriendsService.PeerStatus p) {
        if (!p.online) {
            return "-fx-background-color: #20222c; -fx-text-fill: #6b7280; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 8 2 8; -fx-background-radius: 999;";
        }
        if (p.hosting) {
            return "-fx-background-color: #1f3328; -fx-text-fill: #86efac; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 8 2 8; -fx-background-radius: 999;";
        }
        return "-fx-background-color: #202432; -fx-text-fill: #c7d2fe; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 8 2 8; -fx-background-radius: 999;";
    }

    private static String formatActivity(FriendsService.PeerStatus p) {
        if (!p.online) return "Offline";
        String ver = (p.mcVersion != null && !p.mcVersion.isBlank()) ? "Minecraft " + p.mcVersion : "";
        String world = p.worldOrServer == null ? "" : p.worldOrServer.trim();
        if (p.hosting) {
            String wLabel = world.isEmpty() ? "Private World" : world;
            return (ver.isEmpty() ? "Playing" : "Playing " + ver) + "  •  " + wLabel;
        }
        if ("game".equals(p.role)) {
            if (!ver.isEmpty() && !world.isEmpty()) return "Playing " + ver + "  •  " + world;
            if (!ver.isEmpty()) return "Playing " + ver;
            if (!world.isEmpty()) return world;
            return p.state != null && !p.state.isBlank() ? p.state : "In Game";
        }
        return p.state != null && !p.state.isBlank() ? p.state : "In Launcher";
    }
}
