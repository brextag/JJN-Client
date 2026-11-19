package com.jjn.launcher.ui;

import com.jjn.launcher.model.Instance;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared UI factory helpers and image utilities for JJN Client views.
 */
public final class UiUtils {

    private static Image cachedAppLogo;
    private static Image cachedCreeperBadge;

    private UiUtils() {}

    public static Image appLogoImage() {
        if (cachedAppLogo == null) {
            var url = UiUtils.class.getResource("/com/jjn/launcher/icon.png");
            if (url != null) {
                cachedAppLogo = new Image(url.toExternalForm());
            }
        }
        return cachedAppLogo;
    }

    public static Image creeperBadgeImage() {
        if (cachedCreeperBadge == null) {
            var url = UiUtils.class.getResource("/com/jjn/launcher/creeper_badge.png");
            if (url != null) {
                cachedCreeperBadge = new Image(url.toExternalForm());
            } else {
                return appLogoImage();
            }
        }
        return cachedCreeperBadge;
    }

    public static ImageView creeperBadgeView(double size) {
        ImageView view = new ImageView(creeperBadgeImage());
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    public static void applyAppIcon(Stage stage) {
        if (stage == null) {
            return;
        }
        Image logo = appLogoImage();
        if (logo != null && !stage.getIcons().contains(logo)) {
            stage.getIcons().add(logo);
        }
    }

    public static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    public static Label mutedLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("muted-text");
        return label;
    }

    public static VBox card(Node... children) {
        VBox box = new VBox(10, children);
        box.getStyleClass().add("panel-card");
        return box;
    }

    public static Button secondaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        return button;
    }

    public static Button dangerButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("danger-button");
        return button;
    }

    public static void styleDialog(Dialog<?> dialog) {
        if (dialog == null) {
            return;
        }
        ThemeManager.applyToDialog(dialog);
        DialogPane pane = dialog.getDialogPane();
        dialog.setOnShown(e -> {
            if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage s) {
                applyAppIcon(s);
            }
        });
    }

    public static ComboBox<Instance> instanceComboBox(List<Instance> instances, Instance selected) {
        ComboBox<Instance> box = new ComboBox<>(FXCollections.observableArrayList(instances));
        box.setPromptText("Select an instance");
        box.setCellFactory(listView -> createInstanceCell(true));
        box.setButtonCell(createInstanceCell(false));
        if (selected != null) {
            box.setValue(selected);
        }
        return box;
    }

    private static ListCell<Instance> createInstanceCell(boolean showBadge) {
        return new ListCell<>() {
            @Override
            protected void updateItem(Instance inst, boolean empty) {
                super.updateItem(inst, empty);
                if (empty || inst == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label nameLbl = new Label(inst.name);
                nameLbl.getStyleClass().add("card-title");

                String meta = inst.isInstalled()
                        ? (inst.mcVersion != null ? inst.mcVersion : inst.version) + " • " + inst.loaderDisplayName()
                        : "Not installed";
                Label metaLbl = new Label(meta);
                metaLbl.getStyleClass().add(showBadge ? "badge-accent" : "muted-text");

                Region gap = new Region();
                HBox.setHgrow(gap, Priority.ALWAYS);
                HBox row = new HBox(10, nameLbl, gap, metaLbl);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
                setText(null);
            }
        };
    }

    public static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("JJN Client");
        alert.setHeaderText("Something went wrong");
        alert.setContentText(message);
        styleDialog(alert);
        alert.showAndWait();
    }

    public static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("JJN Client");
        alert.setHeaderText(title);
        alert.setContentText(message);
        styleDialog(alert);
        alert.showAndWait();
    }

    public static void openInExplorer(Path folder) {
        try {
            Files.createDirectories(folder);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folder.toFile());
            } else {
                new ProcessBuilder("explorer.exe", folder.toString()).start();
            }
        } catch (Exception ex) {
            showError("Could not open folder: " + ex.getMessage());
        }
    }

    /**
     * Loads the 8x8 player head (+ hat layer) from a Mojang skin URL asynchronously.
     */
    public static void loadHeadAsync(
            String skinUrl,
            Supplier<Boolean> stillRelevant,
            Consumer<Image> onLoaded
    ) {
        if (skinUrl == null || skinUrl.isBlank()) {
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                Image skin = new Image(skinUrl, false);
                if (skin.isError() || skin.getWidth() < 64 || skin.getHeight() < 16) {
                    return;
                }

                PixelReader reader = skin.getPixelReader();
                int scale = 5;
                WritableImage head = new WritableImage(8 * scale, 8 * scale);
                PixelWriter writer = head.getPixelWriter();

                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        int face = reader.getArgb(8 + x, 8 + y);
                        int hat = reader.getArgb(40 + x, 8 + y);
                        int pixel = ((hat >>> 24) & 0xFF) > 127 ? hat : face;

                        for (int dy = 0; dy < scale; dy++) {
                            for (int dx = 0; dx < scale; dx++) {
                                writer.setArgb(x * scale + dx, y * scale + dy, pixel);
                            }
                        }
                    }
                }

                Platform.runLater(() -> {
                    if (stillRelevant.get()) {
                        onLoaded.accept(head);
                    }
                });
            } catch (Exception ignored) {
            }
        }, "JJN-Skin-Loader");
        thread.setDaemon(true);
        thread.start();
    }

    public static void setHeadImage(ImageView view, Image image) {
        if (image == null) {
            view.setImage(null);
            view.setVisible(false);
            view.setManaged(false);
        } else {
            view.setImage(image);
            view.setVisible(true);
            view.setManaged(true);
        }
    }
}
