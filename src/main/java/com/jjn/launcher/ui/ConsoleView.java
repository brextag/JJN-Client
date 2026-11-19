package com.jjn.launcher.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Live game output console view, capturing up to 2,000 recent lines.
 */
public class ConsoleView {

    private static final int MAX_LINES = 2000;

    private final Deque<String> lines = new ArrayDeque<>();
    private TextArea logArea;
    private Path currentLogFile;

    public synchronized void clear() {
        lines.clear();
        Platform.runLater(() -> {
            if (logArea != null) {
                logArea.clear();
            }
        });
    }

    public void setCurrentLogFile(Path logFile) {
        this.currentLogFile = logFile;
    }

    public synchronized void appendLine(String line) {
        if (line == null) {
            return;
        }
        lines.addLast(line);
        while (lines.size() > MAX_LINES) {
            lines.removeFirst();
        }
        Platform.runLater(() -> {
            if (logArea != null) {
                logArea.appendText(line + "\n");
            }
        });
    }

    public Node build() {
        Label desc = UiUtils.mutedLabel(
                "Live output from the running Minecraft process. Helpful for debugging mods or crashes."
        );
        HBox.setHgrow(desc, Priority.SOMETIMES);

        Button copyBtn = UiUtils.secondaryButton("Copy logs");
        copyBtn.setMinWidth(Region.USE_PREF_SIZE);
        Button openLogFolderBtn = UiUtils.secondaryButton("Open logs folder");
        openLogFolderBtn.setMinWidth(Region.USE_PREF_SIZE);
        Button clearBtn = UiUtils.secondaryButton("Clear");
        clearBtn.setMinWidth(Region.USE_PREF_SIZE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(10, desc, spacer, copyBtn, openLogFolderBtn, clearBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.getStyleClass().add("console-area");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        synchronized (this) {
            if (!lines.isEmpty()) {
                logArea.setText(String.join("\n", lines) + "\n");
                logArea.positionCaret(logArea.getText().length());
            } else {
                logArea.setPromptText("Launch Minecraft to see live output here...");
            }
        }

        copyBtn.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(logArea.getText());
            Clipboard.getSystemClipboard().setContent(content);
        });

        openLogFolderBtn.setOnAction(e -> {
            if (currentLogFile != null && currentLogFile.getParent() != null) {
                UiUtils.openInExplorer(currentLogFile.getParent());
            } else {
                UiUtils.showInfo("Logs", "Launch an instance first to generate a log folder.");
            }
        });

        clearBtn.setOnAction(e -> clear());

        VBox container = new VBox(14, toolbar, logArea);
        VBox.setVgrow(container, Priority.ALWAYS);
        return container;
    }
}
