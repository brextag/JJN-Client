package com.jjn.launcher;

import com.jjn.launcher.auth.AccountStore;
import com.jjn.launcher.friends.FriendsService;
import com.jjn.launcher.model.ClientProfile;
import com.jjn.launcher.model.Instance;
import com.jjn.launcher.service.ClientModBundler;
import com.jjn.launcher.service.FabricInstallService;
import com.jjn.launcher.service.ForgeInstallService;
import com.jjn.launcher.service.ProfileService;
import com.jjn.launcher.service.QuiltInstallService;
import com.jjn.launcher.shop.EconomyService;
import com.jjn.launcher.ui.ConsoleView;
import com.jjn.launcher.ui.EmotesView;
import com.jjn.launcher.ui.FriendsView;
import com.jjn.launcher.ui.InstancesView;
import com.jjn.launcher.ui.KeybindsView;
import com.jjn.launcher.ui.ModsView;
import com.jjn.launcher.ui.ProfilesView;
import com.jjn.launcher.ui.SettingsView;
import com.jjn.launcher.ui.ShopView;
import com.jjn.launcher.ui.ThemeManager;
import com.jjn.launcher.ui.UiUtils;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MainApp extends Application {

    private enum Page {
        HOME, INSTANCES, MODS, PROFILES, FRIENDS, EMOTES, KEYBINDS, CONSOLE, SETTINGS, SHOP
    }

    private final Path instancesPath = LauncherSettings.instancesDir();
    private final List<Instance> instances = new ArrayList<>();

    private final MinecraftInstallService installService = new MinecraftInstallService();
    private final FabricInstallService fabricService = new FabricInstallService(installService);
    private final QuiltInstallService quiltService = new QuiltInstallService(installService);
    private final ForgeInstallService forgeService = new ForgeInstallService(installService);
    private final MicrosoftAuthService authService = new MicrosoftAuthService();
    private final AccountStore accountStore = new AccountStore();
    private final ProfileService profileService = new ProfileService();
    private final LaunchService launchService = new LaunchService();
    private final FriendsService friendsService = new FriendsService();
    private final EconomyService economyService = new EconomyService();

    private final ConsoleView consoleView = new ConsoleView();
    private final InstancesView instancesView = new InstancesView();
    private final ModsView modsView = new ModsView();
    private final ProfilesView profilesView = new ProfilesView();
    private final FriendsView friendsView = new FriendsView();
    private final EmotesView emotesView = new EmotesView();
    private final KeybindsView keybindsView = new KeybindsView();
    private final SettingsView settingsView = new SettingsView();
    private final ShopView shopView = new ShopView();

    private LauncherSettings settings = new LauncherSettings();
    private AccountStore.StoreState accountState = new AccountStore.StoreState();
    private ProfileService.ProfileStore profileStore = new ProfileService.ProfileStore();

    private final List<String> releases = new ArrayList<>();
    private boolean loadingVersions = false;

    private boolean installing = false;
    private Instance installingInstance;
    private boolean gameRunning = false;

    // Account state
    private MicrosoftAuthService.AuthResult authResult;
    private String activeDisplayUuid;
    private boolean signingIn = false;
    private boolean refreshingSession = false;

    private Stage primaryStage;
    private BorderPane root;
    private VBox mainContent;
    private Page currentPage = Page.HOME;

    // Sidebar nav buttons
    private Button navHome;
    private Button navInstances;
    private Button navMods;
    private Button navProfiles;
    private Button navFriends;
    private Button navEmotes;
    private Button navKeybinds;
    private Button navConsole;
    private Button navSettings;
    private Button navShop;

    // Home controls
    private ComboBox<Instance> homeSelector;
    private ComboBox<String> versionBox;
    private HBox homeLoaderBar;
    private final AtomicReference<String> homeLoaderValue = new AtomicReference<>("Fabric");
    private TextField quickPlayField;
    private Button installButton;
    private Button playButton;
    private Button offlineButton;
    private ProgressBar installProgress;
    private Label installedLabel;
    private Label homeStatus;
    private Instance selectedInstance;

    // Account box controls
    private Label accountNameLabel;
    private Label accountStatusLabel;
    private Button accountButton;
    private Button switchAccountButton;
    private ImageView accountHead;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        ThemeManager.ensureFontsLoaded();
        settings = LauncherSettings.load();
        accountState = accountStore.load();
        profileStore = profileService.load();

        root = new BorderPane();

        loadInstances();
        createSidebar();
        showHome();

        Scene scene = new Scene(root, 1180, 760);
        ThemeManager.apply(scene, settings);

        stage.setTitle("JJN Client");
        UiUtils.applyAppIcon(stage);
        stage.setMinWidth(980);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.show();

        friendsService.start();
        economyService.start();
        friendsService.addChangeListener(() -> Platform.runLater(() -> {
            updateFriendsNavBadge();
            economyService.markFriendMilestone(friendsService.getDataSnapshot().friends.size());
            if (currentPage == Page.FRIENDS) {
                friendsView.refreshDynamicContent();
            }
        }));
        economyService.addChangeListener(() -> Platform.runLater(() -> {
            updateShopNavBadge();
            if (currentPage == Page.SHOP) {
                shopView.refreshDynamicContent();
            } else if (currentPage == Page.EMOTES) {
                emotesView.refreshDynamicContent();
            }
        }));
        updateShopNavBadge();

        restoreSavedSession();
    }

    @Override
    public void stop() {
        emotesView.onLeave();
        keybindsView.onLeave();
        shopView.onLeave();
        friendsService.shutdown();
        economyService.shutdown();
    }

    // SIDEBAR

    private void createSidebar() {
        VBox sidebar = new VBox(6);
        sidebar.setPadding(new Insets(18, 14, 16, 14));
        sidebar.setPrefWidth(264);
        sidebar.setMinWidth(256);
        sidebar.getStyleClass().add("sidebar");

        ImageView brandIcon = new ImageView(UiUtils.appLogoImage());
        brandIcon.setFitWidth(34);
        brandIcon.setFitHeight(34);
        brandIcon.setPreserveRatio(true);
        brandIcon.setSmooth(true);

        Label logo = new Label("JJN");
        logo.getStyleClass().add("brand-title");

        Label subtitle = new Label("CLIENT LAUNCHER");
        subtitle.getStyleClass().add("brand-subtitle");

        VBox brandText = new VBox(1, logo, subtitle);
        brandText.setAlignment(Pos.CENTER_LEFT);

        HBox logoBox = new HBox(10, brandIcon, brandText);
        logoBox.setAlignment(Pos.CENTER_LEFT);
        logoBox.getStyleClass().add("brand-header");

        Label menu = new Label("NAVIGATION");
        menu.getStyleClass().add("nav-section-label");
        menu.setPadding(new Insets(8, 0, 2, 4));

        navHome = navButton("⌂   Home", false);
        navInstances = navButton("▦   Instances", false);
        navMods = navButton("◈   Mods", false);
        navProfiles = navButton("☺   Profiles & Skin", false);
        navFriends = navButton("◎   Friends", false);
        navEmotes = navButton("✧   Emotes", false);
        navKeybinds = navButton("⌨   Keybinds", false);
        navConsole = navButton(">_  Console", false);
        navSettings = navButton("⚙   Settings", false);

        navHome.setOnAction(e -> showHome());
        navInstances.setOnAction(e -> showInstances());
        navMods.setOnAction(e -> showMods());
        navProfiles.setOnAction(e -> showProfiles());
        navFriends.setOnAction(e -> showFriends());
        navEmotes.setOnAction(e -> showEmotes());
        navKeybinds.setOnAction(e -> showKeybinds());
        navConsole.setOnAction(e -> showConsole());
        navSettings.setOnAction(e -> showSettings());

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        navShop = navButton("✦   Shop  •  0 ◈", true);
        navShop.setOnAction(e -> showShop());

        // Account card
        ImageView accountCreeperBadge = UiUtils.creeperBadgeView(14);

        accountNameLabel = new Label("Player account");
        accountNameLabel.setWrapText(true);
        accountNameLabel.getStyleClass().add("account-name");

        HBox accountNameRow = new HBox(6, accountCreeperBadge, accountNameLabel);
        accountNameRow.setAlignment(Pos.CENTER_LEFT);

        accountStatusLabel = new Label("Not connected");
        accountStatusLabel.setWrapText(true);
        accountStatusLabel.getStyleClass().add("muted-text");

        accountHead = new ImageView();
        accountHead.setFitWidth(36);
        accountHead.setFitHeight(36);
        accountHead.setSmooth(false);
        UiUtils.setHeadImage(accountHead, null);

        VBox nameBox = new VBox(2, accountNameRow, accountStatusLabel);
        nameBox.setAlignment(Pos.CENTER_LEFT);

        HBox identity = new HBox(10, accountHead, nameBox);
        identity.setAlignment(Pos.CENTER_LEFT);

        accountButton = new Button("Sign in with Microsoft");
        accountButton.setMaxWidth(Double.MAX_VALUE);
        accountButton.getStyleClass().add("primary-button");
        accountButton.setPadding(new Insets(9, 10, 9, 10));

        accountButton.setOnAction(e -> {
            if (authResult == null && !refreshingSession) {
                startSignIn();
            } else {
                signOutCurrentAccount();
            }
        });

        switchAccountButton = UiUtils.secondaryButton("Switch Account");
        switchAccountButton.setMaxWidth(Double.MAX_VALUE);
        switchAccountButton.setVisible(false);
        switchAccountButton.setManaged(false);
        switchAccountButton.setOnAction(e -> openAccountSwitcher());

        VBox accountBox = new VBox(8, identity, accountButton, switchAccountButton);
        accountBox.setPadding(new Insets(12));
        accountBox.getStyleClass().add("account-card");

        sidebar.getChildren().addAll(
                logoBox,
                menu,
                navHome,
                navInstances,
                navMods,
                navProfiles,
                navFriends,
                navEmotes,
                navKeybinds,
                navConsole,
                navSettings,
                spacer,
                navShop,
                accountBox
        );

        root.setLeft(sidebar);
        updateNavHighlight();
    }

    private Button navButton(String text, boolean isShop) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add(isShop ? "nav-button-shop" : "nav-button");
        return button;
    }

    private void updateNavHighlight() {
        List<Button> standard = List.of(navHome, navInstances, navMods, navProfiles, navFriends, navEmotes, navKeybinds, navConsole, navSettings);
        for (Button btn : standard) {
            btn.getStyleClass().remove("nav-button-active");
        }
        navShop.getStyleClass().remove("nav-button-active");

        switch (currentPage) {
            case HOME -> navHome.getStyleClass().add("nav-button-active");
            case INSTANCES -> navInstances.getStyleClass().add("nav-button-active");
            case MODS -> navMods.getStyleClass().add("nav-button-active");
            case PROFILES -> navProfiles.getStyleClass().add("nav-button-active");
            case FRIENDS -> navFriends.getStyleClass().add("nav-button-active");
            case EMOTES -> navEmotes.getStyleClass().add("nav-button-active");
            case KEYBINDS -> navKeybinds.getStyleClass().add("nav-button-active");
            case CONSOLE -> navConsole.getStyleClass().add("nav-button-active");
            case SETTINGS -> navSettings.getStyleClass().add("nav-button-active");
            case SHOP -> navShop.getStyleClass().add("nav-button-active");
        }
    }

    private void updateFriendsNavBadge() {
        if (navFriends == null) return;
        int alerts = friendsService.getPendingInvites().size()
                + friendsService.getDataSnapshot().incomingRequests.size();
        navFriends.setText(alerts > 0 ? "◎   Friends  (" + alerts + ")" : "◎   Friends");
    }

    private void updateShopNavBadge() {
        if (navShop == null) return;
        int coins = economyService.getCoins();
        int ready = economyService.countClaimableQuests();
        navShop.setText("✦   Shop  •  " + coins + " ◈" + (ready > 0 ? " (!)" : ""));
    }

    // PERSISTENT MICROSOFT AUTH

    private void restoreSavedSession() {
        AccountStore.SavedAccount saved = accountState.findActive();
        if (saved == null) {
            updateAccountBox();
            return;
        }

        refreshingSession = true;
        activeDisplayUuid = saved.uuid;
        friendsService.setSelfIdentity(saved.uuid, saved.playerName);
        accountNameLabel.setText(saved.playerName);
        accountStatusLabel.setText("Restoring session...");
        accountButton.setText("Sign out");
        updateSwitchButtonVisibility();
        UiUtils.loadHeadAsync(
                saved.skinUrl,
                () -> saved.uuid.equals(activeDisplayUuid),
                img -> UiUtils.setHeadImage(accountHead, img)
        );

        Thread thread = new Thread(() -> {
            try {
                MicrosoftAuthService.AuthResult refreshed =
                        authService.refreshSignIn(saved.refreshToken, null);

                Platform.runLater(() -> {
                    refreshingSession = false;
                    authResult = refreshed;
                    activeDisplayUuid = refreshed.playerUuid;
                    accountState = accountStore.upsertFromAuth(accountState, refreshed);
                    updateAccountBox();
                    if (currentPage == Page.PROFILES) {
                        showProfiles();
                    }
                });
            } catch (MicrosoftAuthService.SessionExpiredException expired) {
                Platform.runLater(() -> {
                    refreshingSession = false;
                    authResult = null;
                    accountStore.removeAccount(accountState, saved.uuid);
                    activeDisplayUuid = null;
                    updateAccountBox();
                });
            } catch (Exception offlineOrTransient) {
                Platform.runLater(() -> {
                    refreshingSession = false;
                    authResult = null;
                    accountNameLabel.setText(saved.playerName);
                    accountStatusLabel.setText("Session refresh failed (offline?)");
                    accountButton.setText("Sign in again");
                    updateSwitchButtonVisibility();
                });
            }
        }, "JJN-Session-Restore");
        thread.setDaemon(true);
        thread.start();
    }

    private void startSignIn() {
        if (signingIn) {
            return;
        }

        signingIn = true;
        accountButton.setDisable(true);
        accountStatusLabel.setText("Signing in...");

        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<String> verificationUri = new AtomicReference<>();

        Label codeLabel = new Label("Connecting...");
        codeLabel.getStyleClass().add("hero-heading");

        Label hint = new Label("Open the Microsoft page, then enter this code.");
        hint.setWrapText(true);
        hint.getStyleClass().add("muted-text");

        Label statusLabel = new Label("Starting Microsoft authentication...");
        statusLabel.setWrapText(true);
        statusLabel.getStyleClass().add("muted-text");

        Button openButton = new Button("Open Microsoft login page");
        openButton.setDisable(true);
        openButton.getStyleClass().add("primary-button");
        openButton.setPadding(new Insets(10, 16, 10, 16));

        openButton.setOnAction(e -> {
            String uri = verificationUri.get();
            if (uri != null) {
                getHostServices().showDocument(uri);
            }
        });

        VBox content = new VBox(14, hint, codeLabel, openButton, statusLabel);
        content.setPadding(new Insets(10));
        content.setPrefWidth(400);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("JJN Client");
        dialog.setHeaderText("Sign in with Microsoft");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        UiUtils.styleDialog(dialog);
        dialog.setOnHidden(e -> cancelled.set(true));

        Task<MicrosoftAuthService.AuthResult> task = new Task<>() {
            @Override
            protected MicrosoftAuthService.AuthResult call() throws Exception {
                return authService.signIn(
                        new MicrosoftAuthService.Listener() {
                            @Override
                            public void status(String message) {
                                Platform.runLater(() -> statusLabel.setText(message));
                            }

                            @Override
                            public void deviceCode(String userCode, String uri) {
                                verificationUri.set(uri);
                                Platform.runLater(() -> {
                                    codeLabel.setText(userCode);
                                    openButton.setDisable(false);
                                });
                            }
                        },
                        cancelled::get
                );
            }
        };

        task.setOnSucceeded(e -> {
            signingIn = false;
            accountButton.setDisable(false);

            MicrosoftAuthService.AuthResult result = task.getValue();
            authResult = result;
            activeDisplayUuid = result.playerUuid;
            openButton.setDisable(true);

            if (result.minecraftBlocked) {
                codeLabel.setText("SIGNED IN");
                statusLabel.setText(
                        "Microsoft and Xbox sign-in worked, but Minecraft Services rejected the app registration."
                );
            } else {
                accountState = accountStore.upsertFromAuth(accountState, result);
                codeLabel.setText("CONNECTED");
                statusLabel.setText(
                        "Signed in as " + result.playerName
                                + "\nSession saved for future launches."
                );
            }

            updateAccountBox();
        });

        task.setOnFailed(e -> {
            signingIn = false;
            accountButton.setDisable(false);
            Throwable error = task.getException();
            codeLabel.setText("SIGN-IN FAILED");
            statusLabel.setText(error == null ? "Authentication failed." : error.getMessage());
            openButton.setDisable(true);
            updateAccountBox();
        });

        Thread thread = new Thread(task, "JJN-Microsoft-Login");
        thread.setDaemon(true);
        thread.start();

        dialog.show();
    }

    private void signOutCurrentAccount() {
        String removeUuid = authResult != null ? authResult.playerUuid : activeDisplayUuid;
        if (removeUuid != null) {
            accountStore.removeAccount(accountState, removeUuid);
        }
        authResult = null;
        activeDisplayUuid = null;
        refreshingSession = false;

        AccountStore.SavedAccount next = accountState.findActive();
        if (next != null) {
            restoreSavedSession();
        } else {
            updateAccountBox();
        }
    }

    private void openAccountSwitcher() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Accounts");
        dialog.setHeaderText("Saved Minecraft accounts");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        UiUtils.styleDialog(dialog);

        VBox list = new VBox(8);
        list.setPadding(new Insets(8));
        list.setPrefWidth(360);

        for (AccountStore.SavedAccount acc : new ArrayList<>(accountState.accounts)) {
            ImageView badge = UiUtils.creeperBadgeView(15);
            Label name = new Label(acc.playerName);
            name.getStyleClass().add("card-title");
            HBox nameWithIcon = new HBox(7, badge, name);
            nameWithIcon.setAlignment(Pos.CENTER_LEFT);

            boolean isActive = acc.uuid.equals(activeDisplayUuid);
            Button useBtn = UiUtils.secondaryButton(isActive ? "Active" : "Use");
            useBtn.setDisable(isActive);
            useBtn.setOnAction(e -> {
                accountState.activeUuid = acc.uuid;
                accountStore.save(accountState);
                authResult = null;
                dialog.close();
                restoreSavedSession();
            });

            Button removeBtn = UiUtils.dangerButton("Remove");
            removeBtn.setOnAction(e -> {
                accountStore.removeAccount(accountState, acc.uuid);
                if (acc.uuid.equals(activeDisplayUuid)) {
                    authResult = null;
                    activeDisplayUuid = null;
                }
                dialog.close();
                if (accountState.findActive() != null) {
                    restoreSavedSession();
                } else {
                    updateAccountBox();
                }
            });

            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            HBox row = new HBox(10, nameWithIcon, gap, useBtn, removeBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8));
            row.getStyleClass().add("panel-card");
            list.getChildren().add(row);
        }

        Button addBtn = new Button("+ Add Microsoft account");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setPadding(new Insets(10, 14, 10, 14));
        addBtn.setOnAction(e -> {
            dialog.close();
            startSignIn();
        });

        VBox box = new VBox(12, list, addBtn);
        dialog.getDialogPane().setContent(box);
        dialog.showAndWait();
    }

    private void updateAccountBox() {
        UiUtils.setHeadImage(accountHead, null);

        if (authResult == null) {
            accountNameLabel.setText("Player account");
            accountStatusLabel.setText("Not connected");
            accountButton.setText("Sign in with Microsoft");
            if (!refreshingSession) {
                friendsService.setSelfIdentity("", "");
            }
        } else if (authResult.minecraftBlocked) {
            accountNameLabel.setText("Microsoft account connected");
            accountStatusLabel.setText("Waiting for Minecraft approval");
            accountButton.setText("Sign out");
            friendsService.setSelfIdentity("", "");
        } else {
            accountNameLabel.setText(authResult.playerName);
            accountStatusLabel.setText("Minecraft account verified");
            accountButton.setText("Sign out");
            friendsService.setSelfIdentity(authResult.playerUuid, authResult.playerName);

            if (authResult.skinUrl != null) {
                MicrosoftAuthService.AuthResult expected = authResult;
                UiUtils.loadHeadAsync(
                        expected.skinUrl,
                        () -> authResult == expected,
                        img -> UiUtils.setHeadImage(accountHead, img)
                );
            }
        }

        updateSwitchButtonVisibility();
    }

    private void updateSwitchButtonVisibility() {
        boolean showSwitch = authResult != null || !accountState.accounts.isEmpty();
        switchAccountButton.setVisible(showSwitch);
        switchAccountButton.setManaged(showSwitch);
    }

    // PAGE SHELL

    private void createPage(String title, Page page) {
        if (currentPage == Page.EMOTES && page != Page.EMOTES) {
            emotesView.onLeave();
        }
        if (currentPage == Page.KEYBINDS && page != Page.KEYBINDS) {
            keybindsView.onLeave();
        }
        if (currentPage == Page.SHOP && page != Page.SHOP) {
            shopView.onLeave();
        }
        currentPage = page;
        updateNavHighlight();

        VBox container = new VBox(22);
        container.setPadding(new Insets(32, 42, 28, 42));

        Label pageTitle = new Label(title);
        pageTitle.getStyleClass().add("page-title");

        HBox header = new HBox(pageTitle);
        header.setAlignment(Pos.CENTER_LEFT);

        container.getChildren().add(header);
        mainContent = container;
        root.setCenter(mainContent);
    }

    private void setPageBody(Node body) {
        if (body instanceof Region r) {
            VBox.setVgrow(r, Priority.ALWAYS);
        }
        mainContent.getChildren().add(body);
    }

    // HOME PAGE

    private void showHome() {
        createPage("Welcome back", Page.HOME);

        VBox hero = new VBox(15);
        hero.setPadding(new Insets(28));
        hero.getStyleClass().add("hero-card");

        Label tag = new Label("YOUR MINECRAFT EXPERIENCE");
        tag.getStyleClass().add("accent-text");

        installedLabel = new Label();
        installedLabel.getStyleClass().add("badge-accent");
        updateInstalledLabel();

        Region heroTopGap = new Region();
        HBox.setHgrow(heroTopGap, Priority.ALWAYS);
        HBox heroTopRow = new HBox(10, tag, heroTopGap, installedLabel);
        heroTopRow.setAlignment(Pos.CENTER_LEFT);

        Label heading = new Label("Ready to play?");
        heading.getStyleClass().add("hero-heading");

        Label description = new Label("Your game. Your settings. Your experience.");
        description.getStyleClass().add("hero-subtitle");

        homeSelector = UiUtils.instanceComboBox(instances, selectedInstance);
        homeSelector.setPrefWidth(280);
        homeSelector.setMaxWidth(Double.MAX_VALUE);

        versionBox = new ComboBox<>();
        versionBox.setPrefWidth(135);
        versionBox.setMinWidth(120);
        versionBox.setPromptText("Version");

        selectDefaultLoader();
        homeLoaderBar = createHomeLoaderBar();
        homeLoaderBar.setMaxWidth(Region.USE_PREF_SIZE);

        if (!releases.isEmpty()) {
            versionBox.setItems(FXCollections.observableArrayList(releases));
            selectDefaultVersion();
        } else {
            loadVersions();
        }

        installButton = UiUtils.secondaryButton("Install / Update");
        installButton.setPrefHeight(36);
        installButton.setMinWidth(Region.USE_PREF_SIZE);

        VBox instanceCol = new VBox(4, subtleLabel("INSTANCE"), homeSelector);
        HBox.setHgrow(instanceCol, Priority.ALWAYS);
        VBox versionCol = new VBox(4, subtleLabel("VERSION"), versionBox);
        VBox actionCol = new VBox(4, subtleLabel(" "), installButton);

        HBox selectionTopRow = new HBox(12, instanceCol, versionCol, actionCol);
        selectionTopRow.setAlignment(Pos.CENTER_LEFT);

        VBox loaderCol = new VBox(4, subtleLabel("MOD LOADER"), homeLoaderBar);
        VBox selectionBar = new VBox(10, selectionTopRow, loaderCol);
        selectionBar.getStyleClass().add("selection-bar");

        quickPlayField = new TextField(
                selectedInstance != null && selectedInstance.quickPlayServer != null
                        ? selectedInstance.quickPlayServer
                        : ""
        );
        quickPlayField.getStyleClass().add("input-field");
        quickPlayField.setPromptText("Quick Play server (optional, e.g. mc.hypixel.net)");
        quickPlayField.setMaxWidth(440);
        quickPlayField.focusedProperty().addListener((obs, was, now) -> {
            if (!now) {
                persistQuickPlayField();
            }
        });

        installProgress = new ProgressBar(0);
        installProgress.setPrefWidth(440);
        installProgress.setVisible(false);
        installProgress.setManaged(false);

        playButton = new Button("▶   PLAY MINECRAFT");
        playButton.setPrefSize(240, 48);
        playButton.getStyleClass().add("primary-button");

        offlineButton = UiUtils.secondaryButton("Offline test launch");
        offlineButton.setPrefHeight(48);

        HBox playRow = new HBox(12, playButton, offlineButton);
        playRow.setAlignment(Pos.CENTER_LEFT);

        homeStatus = UiUtils.mutedLabel("");

        if (selectedInstance != null && !selectedInstance.isInstalled()) {
            homeStatus.setText("Pick a version & loader above, then click Install / Update.");
        } else if (selectedInstance != null) {
            homeStatus.setText("Selected: " + selectedInstance.name);
        }

        if (installing) {
            setInstallControlsDisabled(true);
            installProgress.setVisible(true);
            installProgress.setManaged(true);
            homeStatus.setText("Installing Minecraft...");
        }

        if (gameRunning) {
            setPlayControlsDisabled(true);
            homeStatus.setText("Minecraft is running... Check Console for live logs.");
        }

        homeSelector.setOnAction(e -> {
            persistQuickPlayField();
            selectedInstance = homeSelector.getValue();
            saveSelectedInstance();
            showHome();
        });

        installButton.setOnAction(e -> installSelectedVersion());
        playButton.setOnAction(e -> playMinecraft(false));
        offlineButton.setOnAction(e -> playMinecraft(true));

        hero.getChildren().addAll(
                heroTopRow,
                heading,
                description,
                selectionBar,
                quickPlayField,
                installProgress,
                playRow,
                homeStatus
        );

        // Overview cards row below hero
        VBox clientModCard;
        if (ClientModBundler.isSupported(selectedInstance)) {
            clientModCard = UiUtils.card(
                    UiUtils.sectionTitle("In-Game JJN Client UI"),
                    UiUtils.mutedLabel(
                            "Active for " + selectedInstance.name + " (" + selectedInstance.mcVersion
                                    + " Fabric): Custom Front Screen, Right-Shift Mod Menu, HUD (FPS & Coords), and ToggleSprint."
                    )
            );
        } else if (selectedInstance != null
                && selectedInstance.isInstalled()
                && Instance.LOADER_VANILLA.equalsIgnoreCase(selectedInstance.loaderType)
                && ClientModBundler.isMcVersionSupported(selectedInstance.mcVersion)) {
            Button enableFabricBtn = UiUtils.secondaryButton(
                    "Enable JJN Client UI (Install Fabric " + selectedInstance.mcVersion + ")"
            );
            enableFabricBtn.setDisable(installing || gameRunning);
            enableFabricBtn.setOnAction(e -> {
                if (versionBox != null && selectedInstance.mcVersion != null) {
                    versionBox.setValue(selectedInstance.mcVersion);
                }
                setHomeLoaderSelection("Fabric");
                installSelectedVersion();
            });
            clientModCard = UiUtils.card(
                    UiUtils.sectionTitle("In-Game JJN Client UI"),
                    UiUtils.mutedLabel(
                            selectedInstance.name + " is currently running Vanilla " + selectedInstance.mcVersion
                                    + ". Vanilla does not load client UI mods — install Fabric to activate the custom Front Screen UI."
                    ),
                    enableFabricBtn
            );
        } else if (selectedInstance != null && selectedInstance.isInstalled()) {
            clientModCard = UiUtils.card(
                    UiUtils.sectionTitle("In-Game JJN Client UI"),
                    UiUtils.mutedLabel(
                            selectedInstance.name + " uses " + selectedInstance.loaderDisplayName() + " " + selectedInstance.mcVersion
                                    + ". The built-in JJN Client UI activates on Fabric 26.2 or 1.21.4."
                    )
            );
        } else {
            clientModCard = UiUtils.card(
                    UiUtils.sectionTitle("In-Game JJN Client UI"),
                    UiUtils.mutedLabel(
                            "Select Minecraft 26.2 or 1.21.4 + Fabric and click Install / Update to enable the custom JJN Front Screen UI and HUD."
                    )
            );
        }
        clientModCard.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(clientModCard, Priority.ALWAYS);

        Button browseModsBtn = UiUtils.secondaryButton("Browse Mods");
        browseModsBtn.setMinWidth(Region.USE_PREF_SIZE);
        browseModsBtn.setOnAction(e -> showMods());

        Button openProfilesBtn = UiUtils.secondaryButton("Profiles & Skin");
        openProfilesBtn.setMinWidth(Region.USE_PREF_SIZE);
        openProfilesBtn.setOnAction(e -> showProfiles());

        ClientProfile activeProfile = profileStore.findActive();
        String profileNote = activeProfile == null
                ? "Active profile: Default"
                : "Active profile: " + activeProfile.name;

        FlowPane modCardActions = new FlowPane(8, 8, browseModsBtn, openProfilesBtn);

        VBox modsSummaryCard = UiUtils.card(
                UiUtils.sectionTitle("Active Instance & Profile"),
                UiUtils.mutedLabel(describeSelectedMods() + "\n" + profileNote),
                modCardActions
        );
        modsSummaryCard.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(modsSummaryCard, Priority.ALWAYS);

        HBox infoRow = new HBox(16, clientModCard, modsSummaryCard);

        Label footer = new Label("JJN CLIENT  •  YOUR MINECRAFT EXPERIENCE");
        footer.getStyleClass().add("footer-text");

        setPageBody(new VBox(18, hero, infoRow, footer));
    }

    private String describeSelectedMods() {
        if (selectedInstance == null) {
            return "Select an instance to view mods.";
        }
        Path modsDir = selectedInstance.modsFolder();
        int enabled = 0;
        if (Files.isDirectory(modsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
                for (Path ignored : stream) {
                    enabled++;
                }
            } catch (IOException ignored) {
            }
        }
        return selectedInstance.name + ": " + enabled + " active mod(s). Manage or download mods in the Mods tab.";
    }

    private void persistQuickPlayField() {
        if (selectedInstance == null || quickPlayField == null) {
            return;
        }
        String text = quickPlayField.getText().trim();
        if (!text.equals(selectedInstance.quickPlayServer)) {
            selectedInstance.quickPlayServer = text;
            try {
                selectedInstance.save();
            } catch (IOException ignored) {
            }
        }
    }

    // PLAY

    private void playMinecraft(boolean offline) {
        if (gameRunning) {
            return;
        }
        if (installing) {
            homeStatus.setText("Wait for the installation to finish first.");
            return;
        }
        if (selectedInstance == null) {
            homeStatus.setText("Select an instance first.");
            return;
        }
        if (!selectedInstance.isInstalled()) {
            homeStatus.setText("Install a Minecraft version first.");
            return;
        }

        if (Instance.LOADER_VANILLA.equalsIgnoreCase(selectedInstance.loaderType)
                && ClientModBundler.isMcVersionSupported(selectedInstance.mcVersion)) {
            Alert prompt = new Alert(Alert.AlertType.CONFIRMATION);
            prompt.setTitle("Enable JJN Client In-Game UI?");
            prompt.setHeaderText(
                    selectedInstance.name + " is currently Vanilla " + selectedInstance.mcVersion
            );
            prompt.setContentText(
                    "Vanilla Minecraft cannot load the custom JJN Front Screen UI, Right-Shift Mod Menu, or HUD.\n\n"
                            + "Would you like to install Fabric + JJN Client UI onto \""
                            + selectedInstance.name + "\" and launch now?"
            );
            ButtonType installAndPlay = new ButtonType("Install Fabric + JJN UI & Play", ButtonBar.ButtonData.OK_DONE);
            ButtonType launchVanilla = new ButtonType("Launch Vanilla Anyway", ButtonBar.ButtonData.NO);
            ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            prompt.getButtonTypes().setAll(installAndPlay, launchVanilla, cancel);
            styleDialog(prompt);

            Optional<ButtonType> choice = prompt.showAndWait();
            if (choice.isEmpty() || choice.get() == cancel) {
                return;
            }
            if (choice.get() == installAndPlay) {
                Instance target = selectedInstance;
                String mcVer = target.mcVersion;
                if (versionBox != null && mcVer != null) {
                    versionBox.setValue(mcVer);
                }
                setHomeLoaderSelection("Fabric");
                installVersionAndLoader(target, mcVer, "Fabric", () -> playMinecraftPrepared(target, offline));
                return;
            }
        }

        playMinecraftPrepared(selectedInstance, offline);
    }

    private void playMinecraftPrepared(Instance target, boolean offline) {
        playMinecraftPrepared(target, offline, null);
    }

    private void playMinecraftPrepared(Instance target, boolean offline, String quickPlayOverride) {
        if (gameRunning || installing || target == null || !target.isInstalled()) {
            return;
        }

        if (quickPlayOverride == null) {
            persistQuickPlayField();
        }

        LaunchService.Session session;
        if (offline) {
            String offlineName = authResult != null && authResult.playerName != null
                    ? authResult.playerName
                    : "JJNPlayer";
            session = LaunchService.Session.offline(offlineName);
            if (friendsService.getSelfUuid().isBlank()) {
                friendsService.setSelfIdentity(session.uuid, session.username);
            }
        } else {
            if (refreshingSession) {
                if (homeStatus != null) {
                    homeStatus.setText("Still restoring your Microsoft session, try again in a moment...");
                }
                return;
            }
            if (authResult == null) {
                if (homeStatus != null) {
                    homeStatus.setText("Sign in with Microsoft first (bottom left), or use Offline test launch.");
                }
                return;
            }
            if (authResult.minecraftBlocked || authResult.minecraftAccessToken == null) {
                if (homeStatus != null) {
                    homeStatus.setText("Your Minecraft account isn't verified yet. Use Offline test launch for now.");
                }
                return;
            }
            session = new LaunchService.Session(
                    authResult.playerName,
                    authResult.playerUuid,
                    authResult.minecraftAccessToken,
                    "msa"
            );
        }

        consoleView.clear();
        gameRunning = true;
        setPlayControlsDisabled(true);
        if (homeStatus != null) {
            homeStatus.setText("Starting " + target.displaySummary() + "...");
        }
        String launchMcVer = target.mcVersion != null && !target.mcVersion.isBlank()
                ? target.mcVersion
                : target.version;
        friendsService.setSelfActivity("Playing Minecraft " + launchMcVer, launchMcVer, "");

        Thread thread = new Thread(() -> {
            try {
                profileService.syncIdentityOnLaunch(profileStore.findActive(), target);
                LaunchService.LaunchResult launched = launchService.launch(
                        target,
                        settings,
                        session,
                        quickPlayOverride,
                        msg -> Platform.runLater(() -> {
                            if (homeStatus != null) {
                                homeStatus.setText(msg);
                            }
                        }),
                        consoleView::appendLine
                );
                consoleView.setCurrentLogFile(launched.logFile);

                Platform.runLater(() -> {
                    if (homeStatus != null) {
                        homeStatus.setText(
                                target.displaySummary() + " is running as " + session.username + "."
                        );
                    }
                    if (settings.hideOnLaunch) {
                        primaryStage.setIconified(true);
                    }
                    if (currentPage == Page.FRIENDS) {
                        friendsView.refreshDynamicContent();
                    }
                });

                int exitCode = launched.process.waitFor();

                friendsService.closeActiveJoinForwarder();
                friendsService.setSelfActivity("In Launcher", "", "");

                Platform.runLater(() -> {
                    gameRunning = false;
                    setPlayControlsDisabled(false);
                    if (settings.hideOnLaunch && primaryStage.isIconified()) {
                        primaryStage.setIconified(false);
                    }
                    if (homeStatus != null) {
                        homeStatus.setText(
                                exitCode == 0
                                        ? "Minecraft closed normally."
                                        : "Minecraft exited with code " + exitCode
                                        + ". Check Console or:\n" + launched.logFile
                        );
                    }
                    if (currentPage == Page.FRIENDS) {
                        friendsView.refreshDynamicContent();
                    }
                });
            } catch (Exception ex) {
                friendsService.closeActiveJoinForwarder();
                friendsService.setSelfActivity("In Launcher", "", "");
                Platform.runLater(() -> {
                    gameRunning = false;
                    setPlayControlsDisabled(false);
                    if (homeStatus != null) {
                        homeStatus.setText("Could not start Minecraft:\n" + ex.getMessage());
                    }
                    if (currentPage == Page.FRIENDS) {
                        friendsView.refreshDynamicContent();
                    }
                });
            }
        }, "JJN-Minecraft-Game");

        thread.setDaemon(true);
        thread.start();
    }

    private void setPlayControlsDisabled(boolean disabled) {
        if (playButton != null) {
            playButton.setDisable(disabled);
        }
        if (offlineButton != null) {
            offlineButton.setDisable(disabled);
        }
    }

    // OTHER PAGES

    private void showInstances() {
        createPage("Instances", Page.INSTANCES);
        Node body = instancesView.build(primaryStage, new InstancesView.Actions() {
            @Override
            public List<Instance> instances() {
                return instances;
            }

            @Override
            public Instance selectedInstance() {
                return selectedInstance;
            }

            @Override
            public void selectInstance(Instance instance) {
                selectedInstance = instance;
                saveSelectedInstance();
                showInstances();
            }

            @Override
            public void installOrChangeLoader(Instance instance) {
                MainApp.this.openChangeLoaderDialog(instance);
            }

            @Override
            public void createInstance() {
                MainApp.this.createInstance();
            }

            @Override
            public void renameInstance(Instance instance) {
                MainApp.this.renameInstance(instance);
            }

            @Override
            public void deleteInstance(Instance instance) {
                MainApp.this.deleteInstance(instance);
            }

            @Override
            public void refresh() {
                showInstances();
            }
        });
        setPageBody(body);
    }

    private void showMods() {
        createPage("Mods", Page.MODS);
        Node body = modsView.build(primaryStage, new ModsView.Actions() {
            @Override
            public List<Instance> instances() {
                return instances;
            }

            @Override
            public Instance selectedInstance() {
                return selectedInstance;
            }

            @Override
            public void onSelectInstance(Instance instance) {
                selectedInstance = instance;
                saveSelectedInstance();
            }

            @Override
            public void onChangeLoader(Instance instance) {
                openChangeLoaderDialog(instance);
            }
        });
        setPageBody(body);
    }

    private void showProfiles() {
        createPage("Profiles & Skin", Page.PROFILES);
        Node body = profilesView.build(primaryStage, new ProfilesView.Actions() {
            @Override
            public ProfileService profileService() {
                return profileService;
            }

            @Override
            public ProfileService.ProfileStore profileStore() {
                return profileStore;
            }

            @Override
            public Instance activeInstance() {
                return selectedInstance;
            }

            @Override
            public MicrosoftAuthService.AuthResult authResult() {
                return authResult;
            }

            @Override
            public void onProfileUpdated() {
                updateAccountBox();
            }
        });
        setPageBody(body);
    }

    private void showFriends() {
        createPage("Friends", Page.FRIENDS);
        friendsService.reloadFromDisk();
        Node body = friendsView.build(new FriendsView.Actions() {
            @Override
            public FriendsService friendsService() {
                return friendsService;
            }

            @Override
            public boolean isGameRunning() {
                return gameRunning;
            }

            @Override
            public boolean hasSignedInAccount() {
                return authResult != null && !authResult.minecraftBlocked && authResult.minecraftAccessToken != null;
            }

            @Override
            public void onJoinPeer(FriendsService.PeerStatus peer) {
                joinFriendFromLauncher(peer, null);
            }

            @Override
            public void onJoinInvite(FriendsService.WorldInvite invite) {
                joinFriendFromLauncher(null, invite);
            }

            @Override
            public void onShowRelayHelp() {
                showRelayHelpDialog();
            }
        });
        setPageBody(body);
        updateFriendsNavBadge();
    }

    private void joinFriendFromLauncher(FriendsService.PeerStatus peer, FriendsService.WorldInvite invite) {
        if (installing) {
            UiUtils.showError("Wait for the current Minecraft installation to finish first.");
            return;
        }
        if (gameRunning) {
            if (invite != null) {
                friendsService.sendInviteJoinToRunningGame(invite);
                friendsService.dismissInvite(invite);
            } else if (peer != null) {
                friendsService.sendJoinToRunningGame(peer);
            }
            UiUtils.showInfo(
                    "Joining in Open Game",
                    "Sent the join request directly to your running Minecraft instance (Fabric JJN Client UI)."
            );
            return;
        }

        String hostName = invite != null ? invite.fromName : (peer != null ? peer.username : "Friend");
        String targetMcVer = invite != null ? invite.mcVersion : (peer != null ? peer.mcVersion : "");
        Instance chosen = chooseInstanceForJoin(targetMcVer);
        if (chosen == null) {
            String verHint = targetMcVer != null && !targetMcVer.isBlank() ? targetMcVer : ClientModBundler.DEFAULT_MC_VERSION;
            UiUtils.showError("No installed Minecraft instance found.\nGo to Home or Instances and install Minecraft " + verHint + " (Fabric) first.");
            return;
        }

        boolean hasMsa = authResult != null && !authResult.minecraftBlocked && authResult.minecraftAccessToken != null;
        boolean useOffline = false;
        if (!hasMsa) {
            if (refreshingSession) {
                UiUtils.showError("Still restoring your Microsoft session. Please wait a moment and click Join again.");
                return;
            }
            Alert ask = new Alert(Alert.AlertType.CONFIRMATION);
            ask.setTitle("Not Signed In");
            ask.setHeaderText("Join " + hostName + "'s world in Offline Mode?");
            ask.setContentText("You are not signed in with a Microsoft account. You can only join LAN/unauthenticated hosts in offline mode.");
            styleDialog(ask);
            Optional<ButtonType> res = ask.showAndWait();
            if (res.isEmpty() || res.get() != ButtonType.OK) {
                return;
            }
            useOffline = true;
        }

        try {
            String endpoint = invite != null
                    ? friendsService.prepareInviteJoinForLaunch(invite)
                    : friendsService.prepareJoinForLaunch(peer);
            if (endpoint == null || endpoint.isBlank()) {
                UiUtils.showError("Could not determine host address or Relay tunnel for " + hostName + ".");
                return;
            }
            if (invite != null) {
                friendsService.dismissInvite(invite);
            }
            selectedInstance = chosen;
            saveSelectedInstance();
            showHome();
            playMinecraftPrepared(chosen, useOffline, endpoint);
        } catch (IOException ex) {
            UiUtils.showError("Could not connect to friend's world:\n" + ex.getMessage());
        }
    }

    private Instance chooseInstanceForJoin(String targetMcVer) {
        String ver = targetMcVer == null ? "" : targetMcVer.trim();
        if (!ver.isEmpty()) {
            if (selectedInstance != null && selectedInstance.isInstalled()
                    && ver.equalsIgnoreCase(selectedInstance.mcVersion)
                    && Instance.LOADER_FABRIC.equalsIgnoreCase(selectedInstance.loaderType)) {
                return selectedInstance;
            }
            for (Instance inst : instances) {
                if (inst.isInstalled() && ver.equalsIgnoreCase(inst.mcVersion)
                        && Instance.LOADER_FABRIC.equalsIgnoreCase(inst.loaderType)) {
                    return inst;
                }
            }
            for (Instance inst : instances) {
                if (inst.isInstalled() && ver.equalsIgnoreCase(inst.mcVersion)) {
                    return inst;
                }
            }
        }
        if (selectedInstance != null && selectedInstance.isInstalled()) {
            return selectedInstance;
        }
        for (Instance inst : instances) {
            if (inst.isInstalled()) {
                return inst;
            }
        }
        return null;
    }

    private void showRelayHelpDialog() {
        String msg = """
                There are 3 ways friends can join your hosted Singleplayer world:

                1. Same Wi-Fi / LAN or VPN (Tailscale / Radmin / ZeroTier)
                   Leave Relay empty. Click Pause -> "Host to Friends" in-game and friends appear automatically.

                2. UPnP Direct Router Mapping
                   Click "Host to Friends" -> Start Hosting. JJN automatically asks your router for UPnP port mapping.

                3. JJN Relay Server (Works across ANY internet / CGNAT!)
                   • Docker / VPS:
                       cd friends-relay && docker compose up -d --build
                   • Cloud PaaS (Railway / Fly.io / Render):
                       Deploy using friends-relay/Dockerfile, expose TCP port 25580, mount /data.
                   • Direct Gradle / Java 21+:
                       gradlew :friends-relay:run --args="--port 25580"

                   Enter <host>:25580 in both friends' Friends tab -> click "Test Relay" -> "Save & Connect".
                   Tip: You can also set JJN_RELAY_URL=<host>:25580 in your environment.
                """;
        UiUtils.showInfo("How JJN Friends & World Hosting Works", msg);
    }

    private void showEmotes() {
        createPage("Emotes", Page.EMOTES);
        Node body = emotesView.build(new EmotesView.Actions() {
            @Override
            public EconomyService economyService() {
                return economyService;
            }

            @Override
            public ProfileService.ProfileStore profileStore() {
                return profileStore;
            }

            @Override
            public MicrosoftAuthService.AuthResult authResult() {
                return authResult;
            }

            @Override
            public void openShopTab() {
                showShop();
            }
        });
        setPageBody(body);
    }

    private void showKeybinds() {
        createPage("Keybinds & Controls", Page.KEYBINDS);
        Node body = keybindsView.build(new KeybindsView.Actions() {
            @Override
            public List<Instance> instances() {
                return instances;
            }

            @Override
            public Instance selectedInstance() {
                return selectedInstance;
            }

            @Override
            public void onSelectInstance(Instance instance) {
                selectedInstance = instance;
                saveSelectedInstance();
            }
        });
        setPageBody(body);
    }

    private void showConsole() {
        createPage("Console", Page.CONSOLE);
        setPageBody(consoleView.build());
    }

    private void showSettings() {
        createPage("Settings", Page.SETTINGS);
        setPageBody(settingsView.build(primaryStage, settings));
    }

    private void showShop() {
        createPage("Shop & Quests", Page.SHOP);
        Node body = shopView.build(new ShopView.Actions() {
            @Override
            public EconomyService economyService() {
                return economyService;
            }

            @Override
            public ProfileService.ProfileStore profileStore() {
                return profileStore;
            }

            @Override
            public MicrosoftAuthService.AuthResult authResult() {
                return authResult;
            }

            @Override
            public void openEmotesTab() {
                showEmotes();
            }
        });
        setPageBody(body);
    }

    // VERSION LIST & INSTALL

    private void loadVersions() {
        if (loadingVersions) {
            return;
        }
        loadingVersions = true;

        Thread thread = new Thread(() -> {
            try {
                List<String> loaded = installService.getReleaseVersions();
                Platform.runLater(() -> {
                    loadingVersions = false;
                    releases.clear();
                    releases.addAll(loaded);
                    if (versionBox != null) {
                        versionBox.setItems(FXCollections.observableArrayList(releases));
                        selectDefaultVersion();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    loadingVersions = false;
                    if (homeStatus != null) {
                        homeStatus.setText("Could not load versions: " + ex.getMessage());
                    }
                });
            }
        }, "JJN-Version-Loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void selectDefaultVersion() {
        if (versionBox == null || releases.isEmpty()) {
            return;
        }
        if (selectedInstance != null
                && selectedInstance.mcVersion != null
                && releases.contains(selectedInstance.mcVersion)) {
            versionBox.setValue(selectedInstance.mcVersion);
        } else if (releases.contains(ClientModBundler.DEFAULT_MC_VERSION)) {
            versionBox.setValue(ClientModBundler.DEFAULT_MC_VERSION);
        } else {
            versionBox.setValue(releases.get(0));
        }
    }

    private void selectDefaultLoader() {
        if (selectedInstance != null
                && (selectedInstance.isInstalled()
                || (selectedInstance.mcVersion != null && !selectedInstance.mcVersion.isBlank()))) {
            homeLoaderValue.set(selectedInstance.loaderDisplayName());
        } else {
            homeLoaderValue.set("Fabric");
        }
    }

    private HBox createHomeLoaderBar() {
        List<String> loaders = List.of("Fabric", "Forge", "NeoForge", "Quilt", "Vanilla");
        ToggleGroup group = new ToggleGroup();
        HBox bar = new HBox(4);
        bar.getStyleClass().add("segmented-group");
        bar.setAlignment(Pos.CENTER_LEFT);

        String active = homeLoaderValue.get();
        for (String name : loaders) {
            ToggleButton btn = new ToggleButton(name);
            btn.getStyleClass().add("pill-toggle");
            btn.setMinWidth(Region.USE_PREF_SIZE);
            btn.setToggleGroup(group);
            btn.setSelected(name.equalsIgnoreCase(active));
            btn.setOnAction(e -> {
                if (!btn.isSelected()) {
                    btn.setSelected(true);
                }
                homeLoaderValue.set(name);
            });
            bar.getChildren().add(btn);
        }

        if (group.getSelectedToggle() == null && !bar.getChildren().isEmpty()) {
            ((ToggleButton) bar.getChildren().get(0)).setSelected(true);
            homeLoaderValue.set(loaders.get(0));
        }
        return bar;
    }

    private void setHomeLoaderSelection(String loaderName) {
        homeLoaderValue.set(loaderName);
        if (homeLoaderBar != null) {
            for (Node node : homeLoaderBar.getChildren()) {
                if (node instanceof ToggleButton tb) {
                    tb.setSelected(tb.getText().equalsIgnoreCase(loaderName));
                }
            }
        }
    }

    private static Label subtleLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("subtle-text");
        return label;
    }

    private void updateInstalledLabel() {
        if (installedLabel == null) {
            return;
        }
        if (selectedInstance == null) {
            installedLabel.setText("No instance selected");
        } else {
            installedLabel.setText(selectedInstance.displaySummary());
        }
    }

    private void setInstallControlsDisabled(boolean disabled) {
        if (versionBox != null) {
            versionBox.setDisable(disabled);
        }
        if (homeLoaderBar != null) {
            homeLoaderBar.setDisable(disabled);
        }
        if (installButton != null) {
            installButton.setDisable(disabled);
        }
        if (homeSelector != null) {
            homeSelector.setDisable(disabled);
        }
    }

    private void installSelectedVersion() {
        if (selectedInstance == null) {
            if (homeStatus != null) {
                homeStatus.setText("Select an instance first.");
            }
            return;
        }
        String mcVersion = versionBox != null ? versionBox.getValue() : null;
        if (mcVersion == null) {
            if (homeStatus != null) {
                homeStatus.setText("Please select a Minecraft version.");
            }
            return;
        }
        String loaderDisplay = homeLoaderValue.get() != null ? homeLoaderValue.get() : "Fabric";
        installVersionAndLoader(selectedInstance, mcVersion, loaderDisplay, null);
    }

    private void installVersionAndLoader(
            Instance target,
            String mcVersion,
            String loaderDisplay,
            Runnable onAfterSuccess
    ) {
        if (installing) {
            return;
        }
        if (gameRunning) {
            if (homeStatus != null) {
                homeStatus.setText("Close Minecraft before installing.");
            } else {
                UiUtils.showError("Close Minecraft before installing.");
            }
            return;
        }
        if (target == null || mcVersion == null || mcVersion.isBlank()) {
            return;
        }

        String normalizedLoader = Instance.loaderFromDisplayName(loaderDisplay);
        Path gameFolder = target.gameFolder();

        installing = true;
        installingInstance = target;

        if (currentPage == Page.HOME) {
            setInstallControlsDisabled(true);
            if (installProgress != null) {
                installProgress.setVisible(true);
                installProgress.setManaged(true);
                installProgress.setProgress(0);
            }
            if (homeStatus != null) {
                homeStatus.setText("Starting " + Instance.displayNameForLoader(normalizedLoader) + " installation...");
            }
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                switch (normalizedLoader) {
                    case Instance.LOADER_FABRIC -> {
                        FabricInstallService.FabricInstallResult res = fabricService.install(
                                mcVersion,
                                gameFolder,
                                (message, progress) -> {
                                    updateMessage(message);
                                    updateProgress(progress, 1.0);
                                }
                        );
                        target.mcVersion = mcVersion;
                        target.loaderType = Instance.LOADER_FABRIC;
                        target.loaderVersion = res.loaderVersion();
                        target.version = res.versionId();
                    }
                    case Instance.LOADER_QUILT -> {
                        QuiltInstallService.QuiltInstallResult res = quiltService.install(
                                mcVersion,
                                gameFolder,
                                (message, progress) -> {
                                    updateMessage(message);
                                    updateProgress(progress, 1.0);
                                }
                        );
                        target.mcVersion = mcVersion;
                        target.loaderType = Instance.LOADER_QUILT;
                        target.loaderVersion = res.loaderVersion();
                        target.version = res.versionId();
                    }
                    case Instance.LOADER_NEOFORGE -> {
                        ForgeInstallService.ForgeInstallResult res = forgeService.installNeoForge(
                                mcVersion,
                                gameFolder,
                                (message, progress) -> {
                                    updateMessage(message);
                                    updateProgress(progress, 1.0);
                                }
                        );
                        target.mcVersion = mcVersion;
                        target.loaderType = Instance.LOADER_NEOFORGE;
                        target.loaderVersion = res.loaderVersion();
                        target.version = res.versionId();
                    }
                    case Instance.LOADER_FORGE -> {
                        ForgeInstallService.ForgeInstallResult res = forgeService.installForge(
                                mcVersion,
                                gameFolder,
                                (message, progress) -> {
                                    updateMessage(message);
                                    updateProgress(progress, 1.0);
                                }
                        );
                        target.mcVersion = mcVersion;
                        target.loaderType = Instance.LOADER_FORGE;
                        target.loaderVersion = res.loaderVersion();
                        target.version = res.versionId();
                    }
                    default -> {
                        installService.install(
                                mcVersion,
                                gameFolder,
                                (message, progress) -> {
                                    updateMessage(message);
                                    updateProgress(progress, 1.0);
                                }
                        );
                        target.mcVersion = mcVersion;
                        target.loaderType = Instance.LOADER_VANILLA;
                        target.loaderVersion = "";
                        target.version = mcVersion;
                    }
                }
                return null;
            }
        };

        task.messageProperty().addListener((obs, old, message) -> {
            if (homeStatus != null && message != null) {
                homeStatus.setText(message);
            }
        });

        task.progressProperty().addListener((obs, old, value) -> {
            if (installProgress != null) {
                installProgress.setProgress(value.doubleValue());
            }
        });

        task.setOnSucceeded(e -> {
            installing = false;
            installingInstance = null;

            try {
                target.save();
            } catch (IOException ex) {
                UiUtils.showError("Installed, but could not save instance metadata: " + ex.getMessage());
            }

            if (currentPage == Page.HOME) {
                showHome();
                if (installProgress != null) {
                    installProgress.setVisible(true);
                    installProgress.setManaged(true);
                    installProgress.setProgress(1);
                }
                if (homeStatus != null) {
                    homeStatus.setText(target.displaySummary() + " installed into " + target.name + "!");
                }
            } else if (currentPage == Page.INSTANCES) {
                showInstances();
            }

            if (onAfterSuccess != null) {
                onAfterSuccess.run();
            }
        });

        task.setOnFailed(e -> {
            installing = false;
            installingInstance = null;
            Throwable error = task.getException();
            String msg = "Installation failed: " + (error == null ? "Unknown error" : error.getMessage());
            if (homeStatus != null) {
                homeStatus.setText(msg);
            }
            setInstallControlsDisabled(false);
            UiUtils.showError(msg);
        });

        Thread worker = new Thread(task, "JJN-Minecraft-Installer");
        worker.setDaemon(true);
        worker.start();
    }

    // INSTANCE CRUD & STORAGE

    private void createInstance() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Create instance");
        dialog.setHeaderText("New Minecraft Instance");
        ButtonType createBtnType = new ButtonType("Create Instance", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createBtnType, ButtonType.CANCEL);
        styleDialog(dialog);

        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Survival 26.2 or Forge Modpack");
        nameField.getStyleClass().add("input-field");

        ComboBox<String> mcVersionPicker = createDialogVersionPicker(ClientModBundler.DEFAULT_MC_VERSION);

        AtomicReference<String> chosenLoader = new AtomicReference<>("Fabric");
        Label loaderHint = UiUtils.mutedLabel("");
        HBox loaderButtons = createLoaderToggleRow("Fabric", chosenLoader, loaderHint);

        CheckBox installNowCheck = new CheckBox("Install Minecraft + loader immediately");
        installNowCheck.setSelected(true);

        VBox content = new VBox(
                12,
                fieldLabel("Instance name"),
                nameField,
                fieldLabel("Minecraft version"),
                mcVersionPicker,
                fieldLabel("Mod loader"),
                loaderButtons,
                loaderHint,
                installNowCheck
        );
        content.setPrefWidth(480);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        Platform.runLater(nameField::requestFocus);

        dialog.showAndWait().ifPresent(result -> {
            if (result != createBtnType) {
                return;
            }
            String cleanName = nameField.getText().trim();
            if (cleanName.isEmpty()) {
                UiUtils.showError("Please enter an instance name.");
                return;
            }

            String mcVer = mcVersionPicker.getValue();
            if (mcVer == null || mcVer.isBlank()) {
                mcVer = ClientModBundler.DEFAULT_MC_VERSION;
            }
            String loaderDisplay = chosenLoader.get();

            try {
                String id = UUID.randomUUID().toString();
                Path folder = instancesPath.resolve(id);
                Files.createDirectories(folder);

                Instance instance = new Instance(id, cleanName, folder);
                instance.mcVersion = mcVer;
                instance.loaderType = Instance.loaderFromDisplayName(loaderDisplay);
                instance.save();

                instances.add(instance);
                selectedInstance = instance;
                saveSelectedInstance();

                if (installNowCheck.isSelected()) {
                    showHome();
                    installVersionAndLoader(instance, mcVer, loaderDisplay, null);
                } else {
                    showInstances();
                }
            } catch (IOException ex) {
                UiUtils.showError("Could not create instance: " + ex.getMessage());
            }
        });
    }

    private void openChangeLoaderDialog(Instance instance) {
        if (installing || gameRunning) {
            UiUtils.showError("Cannot change loader while installing or running Minecraft.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Loader & Version - " + instance.name);
        dialog.setHeaderText("Choose Mod Loader & Version for " + instance.name);
        ButtonType installBtnType = new ButtonType("Install / Apply", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(installBtnType, ButtonType.CANCEL);
        styleDialog(dialog);

        String initialVersion = instance.mcVersion != null && !instance.mcVersion.isBlank()
                ? instance.mcVersion
                : ClientModBundler.DEFAULT_MC_VERSION;
        ComboBox<String> mcVersionPicker = createDialogVersionPicker(initialVersion);

        String initialLoader = instance.isInstalled() ? instance.loaderDisplayName() : "Fabric";
        AtomicReference<String> chosenLoader = new AtomicReference<>(initialLoader);
        Label loaderHint = UiUtils.mutedLabel("");
        HBox loaderButtons = createLoaderToggleRow(initialLoader, chosenLoader, loaderHint);

        VBox content = new VBox(
                12,
                fieldLabel("Minecraft version"),
                mcVersionPicker,
                fieldLabel("Mod loader"),
                loaderButtons,
                loaderHint
        );
        content.setPrefWidth(480);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(result -> {
            if (result != installBtnType) {
                return;
            }
            String mcVer = mcVersionPicker.getValue();
            if (mcVer == null || mcVer.isBlank()) {
                return;
            }
            String loaderDisplay = chosenLoader.get();
            selectedInstance = instance;
            saveSelectedInstance();
            showHome();
            installVersionAndLoader(instance, mcVer, loaderDisplay, null);
        });
    }

    private ComboBox<String> createDialogVersionPicker(String preferredVersion) {
        List<String> seed = releases.isEmpty()
                ? List.of("26.2", "1.21.4", "1.21.1", "1.20.4", "1.20.1")
                : releases;
        ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(seed));
        box.setMaxWidth(Double.MAX_VALUE);
        if (preferredVersion != null && seed.contains(preferredVersion)) {
            box.setValue(preferredVersion);
        } else {
            box.setValue(seed.get(0));
        }

        if (releases.isEmpty()) {
            Thread loaderThread = new Thread(() -> {
                try {
                    List<String> loaded = installService.getReleaseVersions();
                    Platform.runLater(() -> {
                        releases.clear();
                        releases.addAll(loaded);
                        String current = box.getValue();
                        box.setItems(FXCollections.observableArrayList(loaded));
                        if (current != null && loaded.contains(current)) {
                            box.setValue(current);
                        } else if (preferredVersion != null && loaded.contains(preferredVersion)) {
                            box.setValue(preferredVersion);
                        } else if (!loaded.isEmpty()) {
                            box.setValue(loaded.get(0));
                        }
                    });
                } catch (Exception ignored) {
                }
            }, "JJN-Dialog-Versions");
            loaderThread.setDaemon(true);
            loaderThread.start();
        }
        return box;
    }

    private HBox createLoaderToggleRow(
            String initialLoader,
            AtomicReference<String> chosenLoader,
            Label hintLabel
    ) {
        List<String> loaders = List.of("Fabric", "Forge", "NeoForge", "Quilt", "Vanilla");
        ToggleGroup group = new ToggleGroup();
        HBox row = new HBox(4);
        row.getStyleClass().add("segmented-group");
        row.setAlignment(Pos.CENTER_LEFT);

        for (String name : loaders) {
            ToggleButton btn = new ToggleButton(name);
            btn.getStyleClass().add("pill-toggle");
            btn.setToggleGroup(group);
            btn.setSelected(name.equalsIgnoreCase(initialLoader));
            btn.setOnAction(e -> {
                if (!btn.isSelected()) {
                    btn.setSelected(true);
                }
                chosenLoader.set(name);
                hintLabel.setText(loaderDescription(chosenLoader.get()));
            });
            row.getChildren().add(btn);
        }

        if (group.getSelectedToggle() == null && !row.getChildren().isEmpty()) {
            ((ToggleButton) row.getChildren().get(0)).setSelected(true);
            chosenLoader.set(loaders.get(0));
        }
        hintLabel.setText(loaderDescription(chosenLoader.get()));
        return row;
    }

    private static String loaderDescription(String loader) {
        return switch (loader.toLowerCase()) {
            case "fabric" -> "Recommended: Loads Fabric mods + built-in JJN Custom Front Screen UI (26.2 / 1.21.4).";
            case "forge" -> "Classic Minecraft Forge mod loader (supports Forge .jar mods).";
            case "neoforge" -> "Modern NeoForge mod loader for 1.20.4+ and 26.x.";
            case "quilt" -> "Quilt mod loader via Quilt Meta v3.";
            default -> "Official unmodded Vanilla Minecraft (no mods / no JJN UI).";
        };
    }

    private static Label fieldLabel(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("field-label");
        return lbl;
    }

    private void styleDialog(Dialog<?> dialog) {
        UiUtils.styleDialog(dialog);
    }

    private void renameInstance(Instance instance) {
        TextInputDialog dialog = new TextInputDialog(instance.name);
        dialog.setTitle("Rename instance");
        dialog.setHeaderText("Rename " + instance.name);
        dialog.setContentText("New name:");
        dialog.getEditor().getStyleClass().add("input-field");
        styleDialog(dialog);

        dialog.showAndWait().ifPresent(name -> {
            String cleanName = name.trim();
            if (cleanName.isEmpty()) {
                UiUtils.showError("The name cannot be empty.");
                return;
            }
            instance.name = cleanName;
            try {
                instance.save();
                showInstances();
            } catch (IOException ex) {
                UiUtils.showError("Could not rename instance: " + ex.getMessage());
            }
        });
    }

    private void deleteInstance(Instance instance) {
        if (installing && instance == installingInstance) {
            UiUtils.showError("Minecraft is being installed into this instance. Wait for it to finish first.");
            return;
        }
        if (gameRunning && instance == selectedInstance) {
            UiUtils.showError("Minecraft is running. Close it before deleting this instance.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete instance");
        confirm.setHeaderText("Delete " + instance.name + "?");
        confirm.setContentText("This permanently deletes this instance and its files.");
        styleDialog(confirm);

        confirm.showAndWait().ifPresent(answer -> {
            if (answer != ButtonType.OK) {
                return;
            }
            try {
                deleteFolder(instance.folder);
                instances.remove(instance);
                if (instance == selectedInstance) {
                    selectedInstance = instances.isEmpty() ? null : instances.get(0);
                }
                saveSelectedInstance();
                showInstances();
            } catch (IOException ex) {
                UiUtils.showError("Could not delete instance: " + ex.getMessage());
            }
        });
    }

    private void loadInstances() {
        try {
            Files.createDirectories(instancesPath);

            try (DirectoryStream<Path> folders = Files.newDirectoryStream(instancesPath)) {
                for (Path folder : folders) {
                    Instance loaded = Instance.load(folder);
                    if (loaded != null) {
                        instances.add(loaded);
                    }
                }
            }

            if (instances.isEmpty()) {
                String id = UUID.randomUUID().toString();
                Path folder = instancesPath.resolve(id);
                Files.createDirectories(folder);

                Instance first = new Instance(id, "Default instance", folder);
                first.save();
                instances.add(first);
            }

            String savedId = Files.exists(instancesPath.resolve("selected.txt"))
                    ? Files.readString(instancesPath.resolve("selected.txt")).trim()
                    : "";

            selectedInstance = instances.stream()
                    .filter(i -> i.id.equals(savedId))
                    .findFirst()
                    .orElse(instances.get(0));

            saveSelectedInstance();
        } catch (IOException ex) {
            UiUtils.showError("Could not load instances: " + ex.getMessage());
        }
    }

    private void saveSelectedInstance() {
        try {
            Files.createDirectories(instancesPath);
            String id = selectedInstance == null ? "" : selectedInstance.id;
            Files.writeString(instancesPath.resolve("selected.txt"), id);
        } catch (IOException ex) {
            UiUtils.showError("Could not save selected instance.");
        }
    }

    private void deleteFolder(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (var paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}