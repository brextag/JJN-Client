
package com.jjn.launcher;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class MainApp extends Application {

    private final String BG = "#101116";
    private final String PANEL = "#191b23";
    private final String ACCENT = "#c7d2fe";

    private final Path instancesPath = Paths.get(
            System.getProperty("user.home"),
            ".jjnclient",
            "instances"
    );

    private final List<Instance> instances = new ArrayList<>();

    private BorderPane root;
    private VBox mainContent;
    private Label pageTitle;
    private ComboBox<Instance> homeSelector;
    private Label homeStatus;
    private Instance selectedInstance;

    @Override
    public void start(Stage stage) {

        root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG + ";");

        loadInstances();

        createSidebar();

        showHome();

        Scene scene = new Scene(root, 1100, 720);

        stage.setTitle("JJN Client");
        stage.setMinWidth(900);
        stage.setMinHeight(650);
        stage.setScene(scene);
        stage.show();
    }

    // SIDEBAR

    private void createSidebar() {

        VBox sidebar = new VBox(12);
        sidebar.setPadding(new Insets(28, 18, 24, 18));
        sidebar.setPrefWidth(225);

        sidebar.setStyle(
                "-fx-background-color: #15171e;" +
                        "-fx-border-color: #262832;" +
                        "-fx-border-width: 0 1 0 0;"
        );

        Label logo = new Label("JJN");
        logo.setStyle(
                "-fx-font-size: 30px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: white;"
        );

        Label subtitle = new Label("CLIENT");
        subtitle.setStyle(
                "-fx-text-fill: #85899a;" +
                        "-fx-font-size: 10px;"
        );

        VBox logoBox = new VBox(0, logo, subtitle);
        logoBox.setPadding(new Insets(0, 0, 35, 8));

        Label menu = new Label("MENU");
        menu.setStyle(
                "-fx-text-fill: #666b7a;" +
                        "-fx-font-size: 10px;" +
                        "-fx-font-weight: bold;"
        );

        Button home = navButton("⌂   Home");
        Button instancesButton = navButton("▦   Instances");
        Button settings = navButton("⚙   Settings");

        home.setOnAction(e -> showHome());
        instancesButton.setOnAction(e -> showInstances());
        settings.setOnAction(e -> showComingSoon("Settings"));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label account = new Label("Player account");
        account.setStyle(
                "-fx-text-fill: white;" +
                        "-fx-font-size: 13px;" +
                        "-fx-font-weight: bold;"
        );

        Label accountStatus = new Label("Not connected");
        accountStatus.setStyle(
                "-fx-text-fill: #777d8d;" +
                        "-fx-font-size: 11px;"
        );

        VBox accountBox = new VBox(5, account, accountStatus);
        accountBox.setPadding(new Insets(15));
        accountBox.setStyle(
                "-fx-background-color: #20222c;" +
                        "-fx-background-radius: 12;"
        );

        sidebar.getChildren().addAll(
                logoBox,
                menu,
                home,
                instancesButton,
                settings,
                spacer,
                accountBox
        );

        root.setLeft(sidebar);
    }

    private Button navButton(String text) {

        Button button = new Button(text);

        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setPadding(new Insets(13, 15, 13, 15));

        button.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-background-radius: 9;" +
                        "-fx-text-fill: #9297a6;" +
                        "-fx-font-size: 13px;" +
                        "-fx-cursor: hand;"
        );

        return button;
    }

    // PAGE LAYOUT

    private void createPage(String title) {

        VBox page = new VBox(25);
        page.setPadding(new Insets(35, 45, 30, 45));

        pageTitle = new Label(title);
        pageTitle.setStyle(
                "-fx-text-fill: white;" +
                        "-fx-font-size: 26px;" +
                        "-fx-font-weight: bold;"
        );

        HBox header = new HBox(pageTitle);
        header.setAlignment(Pos.CENTER_LEFT);

        page.getChildren().add(header);

        mainContent = page;
        root.setCenter(mainContent);
    }

    // HOME PAGE

    private void showHome() {

        createPage("Welcome back");

        VBox hero = new VBox(18);
        hero.setPadding(new Insets(35));
        hero.setMinHeight(300);

        hero.setStyle(
                "-fx-background-color: #202432;" +
                        "-fx-background-radius: 20;" +
                        "-fx-border-color: #353949;" +
                        "-fx-border-radius: 20;"
        );

        Label tag = new Label("YOUR MINECRAFT EXPERIENCE");
        tag.setStyle(
                "-fx-text-fill: " + ACCENT + ";" +
                        "-fx-font-size: 10px;" +
                        "-fx-font-weight: bold;"
        );

        Label heading = new Label("Ready to play?");
        heading.setStyle(
                "-fx-text-fill: white;" +
                        "-fx-font-size: 34px;" +
                        "-fx-font-weight: bold;"
        );

        Label description = new Label(
                "Your game. Your settings. Your experience."
        );
        description.setStyle(
                "-fx-text-fill: #a2a6b5;" +
                        "-fx-font-size: 14px;"
        );

        homeSelector = new ComboBox<>(
                FXCollections.observableArrayList(instances)
        );

        homeSelector.setPrefWidth(250);
        homeSelector.setValue(selectedInstance);
        homeSelector.setPromptText("Select an instance");

        Button play = new Button("▶     PLAY MINECRAFT");
        play.setPrefSize(220, 48);
        play.setStyle(
                "-fx-background-color: " + ACCENT + ";" +
                        "-fx-background-radius: 10;" +
                        "-fx-text-fill: #171923;" +
                        "-fx-font-weight: bold;" +
                        "-fx-cursor: hand;"
        );

        homeStatus = new Label(
                "Minecraft installation will be added later."
        );
        homeStatus.setStyle(
                "-fx-text-fill: #85899a;" +
                        "-fx-font-size: 11px;"
        );

        homeSelector.setOnAction(e -> {
            selectedInstance = homeSelector.getValue();
            saveSelectedInstance();

            if (selectedInstance != null) {
                homeStatus.setText(
                        "Selected: " + selectedInstance.name
                );
            }
        });

        play.setOnAction(e -> {
            homeStatus.setText(
                    "Minecraft launching will be added in a later stage."
            );
        });

        hero.getChildren().addAll(
                tag,
                heading,
                description,
                homeSelector,
                play,
                homeStatus
        );

        mainContent.getChildren().add(hero);

        Label footer = new Label(
                "JJN CLIENT  •  YOUR MINECRAFT EXPERIENCE"
        );
        footer.setStyle(
                "-fx-text-fill: #565b69;" +
                        "-fx-font-size: 10px;"
        );

        mainContent.getChildren().add(footer);
    }

    // INSTANCE MANAGER

    private void showInstances() {

        createPage("Your instances");

        Label description = new Label(
                "Manage your separate Minecraft installations."
        );
        description.setStyle(
                "-fx-text-fill: #85899a;" +
                        "-fx-font-size: 13px;"
        );

        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label count = new Label(
                instances.size() + " instances"
        );
        count.setStyle("-fx-text-fill: #85899a;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button create = new Button("+  Create instance");
        create.setStyle(
                "-fx-background-color: " + ACCENT + ";" +
                        "-fx-background-radius: 9;" +
                        "-fx-text-fill: #171923;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 12 18 12 18;" +
                        "-fx-cursor: hand;"
        );

        create.setOnAction(e -> createInstance());

        topRow.getChildren().addAll(count, spacer, create);

        ListView<Instance> list = new ListView<>(
                FXCollections.observableArrayList(instances)
        );

        list.setPrefHeight(420);
        list.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-control-inner-background: " + BG + ";" +
                        "-fx-background-insets: 0;"
        );

        list.setCellFactory(view -> new ListCell<>() {

            @Override
            protected void updateItem(Instance instance, boolean empty) {

                super.updateItem(instance, empty);

                if (empty || instance == null) {
                    setGraphic(null);
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                    return;
                }

                Label name = new Label(instance.name);
                name.setStyle(
                        "-fx-text-fill: white;" +
                                "-fx-font-size: 16px;" +
                                "-fx-font-weight: bold;"
                );

                Label info = new Label(
                        "Minecraft version: Not installed yet"
                );
                info.setStyle(
                        "-fx-text-fill: #85899a;" +
                                "-fx-font-size: 11px;"
                );

                Label selected = new Label(
                        instance == selectedInstance ? "SELECTED" : ""
                );
                selected.setStyle(
                        "-fx-text-fill: " + ACCENT + ";" +
                                "-fx-font-size: 10px;" +
                                "-fx-font-weight: bold;"
                );

                VBox details = new VBox(8, name, info, selected);

                Button select = smallButton("Select");
                Button rename = smallButton("Rename");
                Button delete = smallButton("Delete");

                select.setOnAction(e -> {
                    selectedInstance = instance;
                    saveSelectedInstance();
                    refreshInstancePage();
                });

                rename.setOnAction(e -> renameInstance(instance));

                delete.setOnAction(e -> deleteInstance(instance));

                HBox actions = new HBox(8, select, rename, delete);
                actions.setAlignment(Pos.CENTER_RIGHT);

                Region gap = new Region();
                HBox.setHgrow(gap, Priority.ALWAYS);

                HBox card = new HBox(15, details, gap, actions);
                card.setAlignment(Pos.CENTER_LEFT);
                card.setPadding(new Insets(20));
                card.setStyle(
                        "-fx-background-color: " + PANEL + ";" +
                                "-fx-background-radius: 13;" +
                                "-fx-border-color: #292c36;" +
                                "-fx-border-radius: 13;"
                );

                setGraphic(card);
                setText(null);
                setPadding(new Insets(5));
                setStyle("-fx-background-color: transparent;");
            }
        });

        mainContent.getChildren().addAll(
                description,
                topRow,
                list
        );
    }

    private Button smallButton(String text) {

        Button button = new Button(text);

        button.setStyle(
                "-fx-background-color: #292d3b;" +
                        "-fx-background-radius: 8;" +
                        "-fx-text-fill: white;" +
                        "-fx-padding: 9 12 9 12;" +
                        "-fx-cursor: hand;"
        );

        return button;
    }

    // CREATE INSTANCE

    private void createInstance() {

        TextInputDialog dialog = new TextInputDialog();

        dialog.setTitle("Create instance");
        dialog.setHeaderText("Create a new Minecraft instance");
        dialog.setContentText("Instance name:");

        dialog.showAndWait().ifPresent(name -> {

            String cleanName = name.trim();

            if (cleanName.isEmpty()) {
                showError("Please enter an instance name.");
                return;
            }

            try {
                String id = UUID.randomUUID().toString();

                Path folder = instancesPath.resolve(id);
                Files.createDirectories(folder);

                Instance instance = new Instance(
                        id,
                        cleanName,
                        folder
                );

                saveInstance(instance);

                instances.add(instance);
                selectedInstance = instance;
                saveSelectedInstance();

                refreshInstancePage();

            } catch (IOException ex) {
                showError("Could not create instance: " + ex.getMessage());
            }
        });
    }

    // RENAME INSTANCE

    private void renameInstance(Instance instance) {

        TextInputDialog dialog = new TextInputDialog(instance.name);

        dialog.setTitle("Rename instance");
        dialog.setHeaderText("Rename " + instance.name);
        dialog.setContentText("New name:");

        dialog.showAndWait().ifPresent(name -> {

            String cleanName = name.trim();

            if (cleanName.isEmpty()) {
                showError("The name cannot be empty.");
                return;
            }

            instance.name = cleanName;

            try {
                saveInstance(instance);
                refreshInstancePage();
            } catch (IOException ex) {
                showError("Could not rename instance: " + ex.getMessage());
            }
        });
    }

    // DELETE INSTANCE

    private void deleteInstance(Instance instance) {

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);

        confirm.setTitle("Delete instance");
        confirm.setHeaderText("Delete " + instance.name + "?");
        confirm.setContentText(
                "This permanently deletes this instance and its files."
        );

        confirm.showAndWait().ifPresent(answer -> {

            if (answer != ButtonType.OK) {
                return;
            }

            try {
                deleteFolder(instance.folder);

                instances.remove(instance);

                if (instance == selectedInstance) {
                    selectedInstance = instances.isEmpty()
                            ? null
                            : instances.get(0);
                }

                saveSelectedInstance();
                refreshInstancePage();

            } catch (IOException ex) {
                showError("Could not delete instance: " + ex.getMessage());
            }
        });
    }

    // STORAGE

    private void loadInstances() {

        try {
            Files.createDirectories(instancesPath);

            try (DirectoryStream<Path> folders =
                         Files.newDirectoryStream(instancesPath)) {

                for (Path folder : folders) {

                    Path metadata = folder.resolve("instance.properties");

                    if (!Files.isDirectory(folder) ||
                            !Files.exists(metadata)) {
                        continue;
                    }

                    Properties properties = new Properties();

                    try (var input = Files.newInputStream(metadata)) {
                        properties.load(input);
                    }

                    String id = properties.getProperty("id");
                    String name = properties.getProperty("name");

                    if (id != null && name != null) {
                        instances.add(new Instance(id, name, folder));
                    }
                }
            }

            if (instances.isEmpty()) {
                String id = UUID.randomUUID().toString();
                Path folder = instancesPath.resolve(id);

                Files.createDirectories(folder);

                Instance first = new Instance(
                        id,
                        "Default instance",
                        folder
                );

                saveInstance(first);
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
            showError("Could not load instances: " + ex.getMessage());
        }
    }

    private void saveInstance(Instance instance) throws IOException {

        Properties properties = new Properties();

        properties.setProperty("id", instance.id);
        properties.setProperty("name", instance.name);

        try (var output = Files.newOutputStream(
                instance.folder.resolve("instance.properties"))) {

            properties.store(output, "JJN Client Instance");
        }
    }

    private void saveSelectedInstance() {

        try {
            Files.createDirectories(instancesPath);

            String id = selectedInstance == null
                    ? ""
                    : selectedInstance.id;

            Files.writeString(
                    instancesPath.resolve("selected.txt"),
                    id
            );

        } catch (IOException ex) {
            showError("Could not save selected instance.");
        }
    }

    private void deleteFolder(Path folder) throws IOException {

        if (!Files.exists(folder)) {
            return;
        }

        try (var paths = Files.walk(folder)) {
            for (Path path : paths.sorted(
                    Comparator.reverseOrder()
            ).toList()) {
                Files.delete(path);
            }
        }
    }

    // HELPERS

    private void refreshInstancePage() {
        showInstances();
    }

    private void showComingSoon(String name) {

        createPage(name);

        Label message = new Label("This section is coming soon.");
        message.setStyle(
                "-fx-text-fill: #85899a;" +
                        "-fx-font-size: 15px;"
        );

        mainContent.getChildren().add(message);
    }

    private void showError(String message) {

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("JJN Client");
        alert.setHeaderText("Something went wrong");
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static class Instance {

        private final String id;
        private String name;
        private final Path folder;

        public Instance(String id, String name, Path folder) {
            this.id = id;
            this.name = name;
            this.folder = folder;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
