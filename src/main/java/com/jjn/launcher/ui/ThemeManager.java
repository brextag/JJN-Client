package com.jjn.launcher.ui;

import com.jjn.launcher.LauncherSettings;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central UI theme, font, and tactile button engine for JJNLauncher.
 * Loads the bundled official Minecraft pixel font and dynamically builds
 * cache-busted JavaFX stylesheets so settings apply live without restarting.
 */
public final class ThemeManager {

    public record ThemePreset(
            String id,
            String name,
            String subtitle,
            String accentHex,
            String bgHex,
            String surfaceHex,
            String textHex
    ) {}

    public static final List<ThemePreset> PRESETS = List.of(
            new ThemePreset("blue-black-white", "Blue, White & Black", "Default JJN Obsidian Blue", "#2563EB", "#05070B", "#0F1626", "#FFFFFF"),
            new ThemePreset("cobalt-ice", "Cobalt & Ice", "Electric sky accents on deep slate", "#0EA5E9", "#060B14", "#101D34", "#FFFFFF"),
            new ThemePreset("arctic-white", "Arctic White & Blue", "Crisp light white cards with royal blue", "#2563EB", "#EEF3FB", "#FFFFFF", "#090D16"),
            new ThemePreset("royal-navy", "Midnight Navy", "Deep navy command center", "#3B82F6", "#030612", "#0C1836", "#F8FAFC"),
            new ThemePreset("crimson-black", "Crimson & Black", "High-contrast red & pitch black", "#DC2626", "#080406", "#180D12", "#FFFFFF"),
            new ThemePreset("emerald-bedrock", "Emerald & Bedrock", "Classic emerald on dark obsidian", "#16A34A", "#040A07", "#0D1B14", "#FFFFFF")
    );

    private static final Path THEME_CACHE_DIR = LauncherSettings.ROOT_DIR.resolve("theme-cache");
    private static final AtomicLong VERSION_SEQ = new AtomicLong(1);
    private static final List<Runnable> CHANGE_LISTENERS = new CopyOnWriteArrayList<>();

    private static boolean fontsLoaded = false;
    private static String minecraftFontFamily = "Minecraft";
    private static LauncherSettings currentSettings = new LauncherSettings();
    private static Scene activeScene;
    private static String activeThemeCssUrl;

    private ThemeManager() {}

    public static synchronized void ensureFontsLoaded() {
        if (fontsLoaded) {
            return;
        }
        fontsLoaded = true;
        try {
            var regUrl = ThemeManager.class.getResource("/com/jjn/launcher/fonts/Minecraft-Regular.ttf");
            if (regUrl != null) {
                Font reg = Font.loadFont(regUrl.toExternalForm(), 14);
                if (reg != null && reg.getFamily() != null && !reg.getFamily().isBlank()) {
                    minecraftFontFamily = reg.getFamily();
                }
            }
            var boldUrl = ThemeManager.class.getResource("/com/jjn/launcher/fonts/Minecraft-Bold.ttf");
            if (boldUrl != null) {
                Font.loadFont(boldUrl.toExternalForm(), 14);
            }
        } catch (Throwable ignored) {
        }
    }

    public static String getMinecraftFontFamily() {
        ensureFontsLoaded();
        return minecraftFontFamily;
    }

    public static LauncherSettings getCurrentSettings() {
        return currentSettings;
    }

    public static void addChangeListener(Runnable listener) {
        if (listener != null) {
            CHANGE_LISTENERS.add(listener);
        }
    }

    public static Color accentColor() {
        return parseColor(currentSettings.uiAccentColor, Color.web(LauncherSettings.DEFAULT_ACCENT_COLOR));
    }

    public static boolean isLightTheme() {
        Color bg = parseColor(currentSettings.uiBgColor, Color.web(LauncherSettings.DEFAULT_BG_COLOR));
        return luminance(bg) > 0.52;
    }

    public static ThemePreset findPreset(String id) {
        if (id == null) {
            return null;
        }
        for (ThemePreset preset : PRESETS) {
            if (preset.id().equalsIgnoreCase(id)) {
                return preset;
            }
        }
        return null;
    }

    public static void apply(Scene scene, LauncherSettings settings) {
        ensureFontsLoaded();
        if (settings != null) {
            currentSettings = settings;
        }
        if (scene != null) {
            activeScene = scene;
        }
        String newUrl = writeThemeStylesheet(currentSettings);
        if (activeScene != null && newUrl != null) {
            var sheets = activeScene.getStylesheets();
            if (activeThemeCssUrl != null) {
                sheets.remove(activeThemeCssUrl);
            }
            String baseUrl = baseCssUrl();
            if (baseUrl != null && !sheets.contains(baseUrl)) {
                sheets.add(baseUrl);
            }
            sheets.add(newUrl);
        }
        activeThemeCssUrl = newUrl;

        for (Runnable listener : CHANGE_LISTENERS) {
            try {
                listener.run();
            } catch (Throwable ignored) {
            }
        }
    }

    public static void refresh() {
        apply(activeScene, currentSettings);
    }

    public static void applyToDialog(Dialog<?> dialog) {
        if (dialog == null) {
            return;
        }
        ensureFontsLoaded();
        DialogPane pane = dialog.getDialogPane();
        var sheets = pane.getStylesheets();
        String baseUrl = baseCssUrl();
        if (baseUrl != null && !sheets.contains(baseUrl)) {
            sheets.add(baseUrl);
        }
        if (activeThemeCssUrl == null) {
            activeThemeCssUrl = writeThemeStylesheet(currentSettings);
        }
        if (activeThemeCssUrl != null) {
            sheets.removeIf(s -> s.contains("ui-theme-"));
            sheets.add(activeThemeCssUrl);
        }
    }

    private static String baseCssUrl() {
        var url = ThemeManager.class.getResource("/com/jjn/launcher/styles.css");
        return url == null ? null : url.toExternalForm();
    }

    private static synchronized String writeThemeStylesheet(LauncherSettings settings) {
        try {
            Files.createDirectories(THEME_CACHE_DIR);
            try (DirectoryStream<Path> old = Files.newDirectoryStream(THEME_CACHE_DIR, "ui-theme-*.css")) {
                for (Path p : old) {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                }
            }
            long ver = VERSION_SEQ.getAndIncrement();
            Path out = THEME_CACHE_DIR.resolve("ui-theme-" + ver + ".css");
            Files.writeString(out, buildThemeCss(settings));
            return out.toUri().toString();
        } catch (IOException ex) {
            return null;
        }
    }

    public static String buildThemeCss(LauncherSettings s) {
        Color bg = parseColor(s.uiBgColor, Color.web(LauncherSettings.DEFAULT_BG_COLOR));
        Color surface = parseColor(s.uiSurfaceColor, Color.web(LauncherSettings.DEFAULT_SURFACE_COLOR));
        Color accent = parseColor(s.uiAccentColor, Color.web(LauncherSettings.DEFAULT_ACCENT_COLOR));
        Color text = parseColor(s.uiTextColor, Color.web(LauncherSettings.DEFAULT_TEXT_COLOR));

        boolean light = luminance(bg) > 0.52;

        Color sidebar = light ? mix(bg, surface, 0.35) : mix(bg, surface, 0.55);
        Color elevated = light ? mix(surface, accent, 0.08) : mix(surface, Color.WHITE, 0.07);
        Color border = light ? mix(bg, accent, 0.28) : mix(surface, accent, 0.32);
        Color borderStrong = mix(accent, Color.WHITE, light ? 0.12 : 0.32);
        Color inputBg = light ? Color.WHITE : mix(bg, surface, 0.38);

        // Tactile button surfaces clearly distinct from panels/backgrounds
        Color btnBg = light ? mix(Color.WHITE, accent, 0.07) : mix(surface, accent, 0.14).interpolate(Color.WHITE, 0.06);
        Color btnHover = light ? mix(Color.WHITE, accent, 0.17) : mix(btnBg, accent, 0.28).interpolate(Color.WHITE, 0.08);
        Color btnPressed = light ? mix(btnBg, accent, 0.25) : mix(btnBg, bg, 0.45);
        Color btnBorder = light ? mix(border, accent, 0.35) : mix(surface, accent, 0.48).interpolate(Color.WHITE, 0.14);
        Color btnTopHl = light ? Color.WHITE : mix(btnBg, Color.WHITE, 0.28);
        Color btnBotLip = light ? mix(btnBorder, Color.BLACK, 0.22) : mix(bg, Color.BLACK, 0.55);

        // Accent primary button + active states
        Color accentHover = mix(accent, Color.WHITE, 0.18);
        Color accentPressed = mix(accent, Color.BLACK, 0.22);
        Color accentBorder = mix(accent, Color.WHITE, 0.46);
        Color accentLip = mix(accent, Color.BLACK, 0.46);
        Color accentText = luminance(accent) > 0.60 ? Color.web("#070B14") : Color.WHITE;
        Color accentFg = light ? mix(accent, Color.BLACK, 0.18) : mix(accent, Color.WHITE, 0.44);
        Color accentSoft = light ? mix(Color.WHITE, accent, 0.14) : mix(surface, accent, 0.24);

        Color muted = mix(text, bg, light ? 0.36 : 0.30);
        Color subtle = mix(text, bg, light ? 0.48 : 0.46);

        Color dangerBg = light ? Color.web("#FEE2E2") : Color.web("#331219");
        Color dangerHover = light ? Color.web("#FECACA") : Color.web("#4B1923");
        Color dangerPressed = light ? Color.web("#FCA5A5") : Color.web("#230A10");
        Color dangerBorder = light ? Color.web("#EF4444") : Color.web("#B91C1C");
        Color dangerLip = light ? Color.web("#991B1B") : Color.web("#18060A");
        Color dangerText = light ? Color.web("#991B1B") : Color.web("#FECACA");

        Color successBg = light ? Color.web("#DCFCE7") : Color.web("#0E291D");
        Color successBorder = light ? Color.web("#22C55E") : Color.web("#16A34A");
        Color successText = light ? Color.web("#166534") : Color.web("#86EFAC");

        Color warningBg = light ? Color.web("#FEF3C7") : Color.web("#2E200B");
        Color warningBorder = light ? Color.web("#F59E0B") : Color.web("#D97706");
        Color warningText = light ? Color.web("#92400E") : Color.web("#FCD34D");

        String fontStack = s.uiUseMinecraftFont
                ? "\"" + minecraftFontFamily + "\", \"Segoe UI\", sans-serif"
                : "\"Segoe UI\", \"Inter\", system-ui, sans-serif";

        double scale = Math.max(0.85, Math.min(1.25, s.uiFontScale));
        double basePx = (s.uiUseMinecraftFont ? 12.5 : 13.0) * scale;

        String btnStyle = s.uiButtonStyle == null ? LauncherSettings.DEFAULT_BUTTON_STYLE : s.uiButtonStyle.toLowerCase(Locale.ROOT);
        int baseRadius = Math.max(0, Math.min(20, s.uiCornerRadius));
        int btnRadius = switch (btnStyle) {
            case "pill" -> 999;
            case "bordered" -> Math.max(4, Math.min(12, baseRadius));
            default -> Math.min(6, baseRadius);
        };
        int navRadius = switch (btnStyle) {
            case "pill" -> 16;
            case "bordered" -> Math.max(4, Math.min(12, baseRadius));
            default -> Math.min(6, baseRadius);
        };
        int cardRadius = Math.max(2, baseRadius + 4);
        int heroRadius = Math.max(4, baseRadius + 8);

        boolean mcBevel = !"bordered".equals(btnStyle) && !"pill".equals(btnStyle);
        String btnBorderWidths = mcBevel ? "1.5 1.5 3.5 1.5" : "1.5";
        String btnPressedBorderWidths = mcBevel ? "3.5 1.5 1.5 1.5" : "1.5";
        String btnShadow = mcBevel
                ? "dropshadow(gaussian, rgba(0, 0, 0, " + (light ? "0.18" : "0.55") + "), 6, 0, 0, 3)"
                : "dropshadow(gaussian, rgba(0, 0, 0, " + (light ? "0.12" : "0.40") + "), 8, 0, 0, 2)";
        String primaryGlow = "dropshadow(gaussian, " + rgba(accent, light ? 0.35 : 0.50) + ", 14, 0, 0, 3)";

        int navPadV = s.uiCompactSpacing ? 8 : 10;
        int cardPad = s.uiCompactSpacing ? 14 : 20;
        int secBtnPadV = s.uiCompactSpacing ? 7 : 9;
        int secBtnPadH = s.uiCompactSpacing ? 12 : 14;

        String heroBgRule = s.uiHeroGlow
                ? "linear-gradient(to bottom right, " + hex(mix(surface, accent, light ? 0.10 : 0.22)) + ", " + hex(surface) + " 60%, " + hex(mix(bg, surface, 0.5)) + ")"
                : hex(surface);
        String heroEffect = s.uiHeroGlow
                ? "dropshadow(gaussian, " + rgba(accent, light ? 0.16 : 0.26) + ", 22, 0, 0, 5)"
                : "none";

        StringBuilder css = new StringBuilder();
        css.append(".root {\n");
        css.append("    -jjn-bg: ").append(hex(bg)).append(";\n");
        css.append("    -jjn-sidebar: ").append(hex(sidebar)).append(";\n");
        css.append("    -jjn-surface: ").append(hex(surface)).append(";\n");
        css.append("    -jjn-elevated: ").append(hex(elevated)).append(";\n");
        css.append("    -jjn-border: ").append(hex(border)).append(";\n");
        css.append("    -jjn-border-strong: ").append(hex(borderStrong)).append(";\n");
        css.append("    -jjn-input-bg: ").append(hex(inputBg)).append(";\n");
        css.append("    -jjn-btn-bg: ").append(hex(btnBg)).append(";\n");
        css.append("    -jjn-btn-hover: ").append(hex(btnHover)).append(";\n");
        css.append("    -jjn-btn-pressed: ").append(hex(btnPressed)).append(";\n");
        css.append("    -jjn-btn-border: ").append(hex(btnBorder)).append(";\n");
        css.append("    -jjn-btn-top-hl: ").append(hex(btnTopHl)).append(";\n");
        css.append("    -jjn-btn-bot-lip: ").append(hex(btnBotLip)).append(";\n");
        css.append("    -jjn-accent: ").append(hex(accent)).append(";\n");
        css.append("    -jjn-accent-hover: ").append(hex(accentHover)).append(";\n");
        css.append("    -jjn-accent-pressed: ").append(hex(accentPressed)).append(";\n");
        css.append("    -jjn-accent-border: ").append(hex(accentBorder)).append(";\n");
        css.append("    -jjn-accent-lip: ").append(hex(accentLip)).append(";\n");
        css.append("    -jjn-accent-text: ").append(hex(accentText)).append(";\n");
        css.append("    -jjn-accent-fg: ").append(hex(accentFg)).append(";\n");
        css.append("    -jjn-accent-soft: ").append(hex(accentSoft)).append(";\n");
        css.append("    -jjn-text: ").append(hex(text)).append(";\n");
        css.append("    -jjn-muted: ").append(hex(muted)).append(";\n");
        css.append("    -jjn-subtle: ").append(hex(subtle)).append(";\n");
        css.append("    -jjn-danger-bg: ").append(hex(dangerBg)).append(";\n");
        css.append("    -jjn-danger-hover: ").append(hex(dangerHover)).append(";\n");
        css.append("    -jjn-danger-pressed: ").append(hex(dangerPressed)).append(";\n");
        css.append("    -jjn-danger-border: ").append(hex(dangerBorder)).append(";\n");
        css.append("    -jjn-danger-lip: ").append(hex(dangerLip)).append(";\n");
        css.append("    -jjn-danger-text: ").append(hex(dangerText)).append(";\n");
        css.append("    -jjn-success-bg: ").append(hex(successBg)).append(";\n");
        css.append("    -jjn-success-border: ").append(hex(successBorder)).append(";\n");
        css.append("    -jjn-success-text: ").append(hex(successText)).append(";\n");
        css.append("    -jjn-warning-bg: ").append(hex(warningBg)).append(";\n");
        css.append("    -jjn-warning-border: ").append(hex(warningBorder)).append(";\n");
        css.append("    -jjn-warning-text: ").append(hex(warningText)).append(";\n");
        css.append("    -fx-font-family: ").append(fontStack).append(";\n");
        css.append("    -fx-font-size: ").append(fmt(basePx)).append("px;\n");
        css.append("    -fx-background-color: -jjn-bg;\n");
        css.append("}\n\n");

        // Typography scaling
        css.append(".brand-title { -fx-font-size: ").append(fmt(26 * scale)).append("px; }\n");
        css.append(".brand-subtitle { -fx-font-size: ").append(fmt(10 * scale)).append("px; }\n");
        css.append(".page-title { -fx-font-size: ").append(fmt(23 * scale)).append("px; }\n");
        css.append(".hero-heading { -fx-font-size: ").append(fmt(28 * scale)).append("px; }\n");
        css.append(".hero-subtitle { -fx-font-size: ").append(fmt(13 * scale)).append("px; }\n");
        css.append(".section-title { -fx-font-size: ").append(fmt(15 * scale)).append("px; }\n");
        css.append(".muted-text { -fx-font-size: ").append(fmt(11.5 * scale)).append("px; }\n");
        css.append(".accent-text { -fx-font-size: ").append(fmt(10.5 * scale)).append("px; }\n\n");

        // Cards & radii
        css.append(".panel-card { -fx-background-radius: ").append(cardRadius)
                .append("; -fx-border-radius: ").append(cardRadius)
                .append("; -fx-padding: ").append(cardPad).append("; }\n");
        css.append(".hero-card { -fx-background-color: ").append(heroBgRule)
                .append("; -fx-background-radius: ").append(heroRadius)
                .append("; -fx-border-radius: ").append(heroRadius)
                .append("; -fx-border-color: -jjn-border-strong; -fx-border-width: 1.5; -fx-effect: ")
                .append(heroEffect).append("; }\n");
        css.append(".account-card { -fx-background-radius: ").append(cardRadius)
                .append("; -fx-border-radius: ").append(cardRadius).append("; }\n");
        css.append(".selection-bar { -fx-background-radius: ").append(cardRadius)
                .append("; -fx-border-radius: ").append(cardRadius).append("; }\n");
        css.append(".segmented-group { -fx-background-radius: ").append(Math.max(4, navRadius))
                .append("; -fx-border-radius: ").append(Math.max(4, navRadius)).append("; }\n");
        css.append(".input-field, .combo-box { -fx-background-radius: ").append(Math.max(2, navRadius))
                .append("; -fx-border-radius: ").append(Math.max(2, navRadius)).append("; }\n\n");

        // Tactile Primary Button
        css.append(".primary-button {\n");
        css.append("    -fx-background-color: -jjn-accent-border, -jjn-accent;\n");
        css.append("    -fx-background-insets: 0, 1 1 2 1;\n");
        css.append("    -fx-background-radius: ").append(btnRadius).append(", ").append(Math.max(0, btnRadius - 1)).append(";\n");
        css.append("    -fx-border-color: -jjn-accent-border -jjn-accent-border -jjn-accent-lip -jjn-accent-border;\n");
        css.append("    -fx-border-width: ").append(btnBorderWidths).append(";\n");
        css.append("    -fx-border-radius: ").append(btnRadius).append(";\n");
        css.append("    -fx-text-fill: -jjn-accent-text;\n");
        css.append("    -fx-font-weight: bold;\n");
        css.append("    -fx-effect: ").append(primaryGlow).append(";\n");
        css.append("}\n");
        css.append(".primary-button:hover {\n");
        css.append("    -fx-background-color: #FFFFFF, -jjn-accent-hover;\n");
        css.append("    -fx-border-color: #FFFFFF #FFFFFF -jjn-accent-lip #FFFFFF;\n");
        css.append("}\n");
        css.append(".primary-button:pressed {\n");
        css.append("    -fx-background-color: -jjn-accent-pressed;\n");
        css.append("    -fx-background-insets: 0;\n");
        css.append("    -fx-border-color: -jjn-accent-lip -jjn-accent-border -jjn-accent-border -jjn-accent-border;\n");
        css.append("    -fx-border-width: ").append(btnPressedBorderWidths).append(";\n");
        css.append("    -fx-translate-y: 1.5;\n");
        css.append("}\n\n");

        // Tactile Secondary Button
        css.append(".secondary-button {\n");
        css.append("    -fx-background-color: -jjn-btn-top-hl, -jjn-btn-bg;\n");
        css.append("    -fx-background-insets: 0, 1 0 0 0;\n");
        css.append("    -fx-background-radius: ").append(btnRadius).append(", ").append(Math.max(0, btnRadius - 1)).append(";\n");
        css.append("    -fx-border-color: -jjn-btn-border -jjn-btn-border -jjn-btn-bot-lip -jjn-btn-border;\n");
        css.append("    -fx-border-width: ").append(btnBorderWidths).append(";\n");
        css.append("    -fx-border-radius: ").append(btnRadius).append(";\n");
        css.append("    -fx-padding: ").append(secBtnPadV).append(" ").append(secBtnPadH).append(" ").append(secBtnPadV).append(" ").append(secBtnPadH).append(";\n");
        css.append("    -fx-font-weight: bold;\n");
        css.append("    -fx-effect: ").append(btnShadow).append(";\n");
        css.append("}\n");
        css.append(".secondary-button:hover {\n");
        css.append("    -fx-background-color: -jjn-accent-border, -jjn-btn-hover;\n");
        css.append("    -fx-border-color: -jjn-accent-border -jjn-accent-border -jjn-btn-bot-lip -jjn-accent-border;\n");
        css.append("    -fx-text-fill: -jjn-text;\n");
        css.append("}\n");
        css.append(".secondary-button:pressed {\n");
        css.append("    -fx-background-color: -jjn-btn-pressed;\n");
        css.append("    -fx-background-insets: 0;\n");
        css.append("    -fx-border-color: -jjn-btn-bot-lip -jjn-btn-border -jjn-btn-border -jjn-btn-border;\n");
        css.append("    -fx-border-width: ").append(btnPressedBorderWidths).append(";\n");
        css.append("    -fx-translate-y: 1.5;\n");
        css.append("}\n\n");

        // Tactile Danger Button
        css.append(".danger-button {\n");
        css.append("    -fx-background-color: -jjn-danger-border, -jjn-danger-bg;\n");
        css.append("    -fx-background-insets: 0, 1 0 0 0;\n");
        css.append("    -fx-background-radius: ").append(btnRadius).append(", ").append(Math.max(0, btnRadius - 1)).append(";\n");
        css.append("    -fx-border-color: -jjn-danger-border -jjn-danger-border -jjn-danger-lip -jjn-danger-border;\n");
        css.append("    -fx-border-width: ").append(btnBorderWidths).append(";\n");
        css.append("    -fx-border-radius: ").append(btnRadius).append(";\n");
        css.append("    -fx-padding: ").append(secBtnPadV).append(" ").append(secBtnPadH).append(" ").append(secBtnPadV).append(" ").append(secBtnPadH).append(";\n");
        css.append("    -fx-font-weight: bold;\n");
        css.append("    -fx-effect: ").append(btnShadow).append(";\n");
        css.append("}\n");
        css.append(".danger-button:hover {\n");
        css.append("    -fx-background-color: -jjn-danger-text, -jjn-danger-hover;\n");
        css.append("    -fx-border-color: -jjn-danger-text -jjn-danger-border -jjn-danger-lip -jjn-danger-border;\n");
        css.append("}\n");
        css.append(".danger-button:pressed {\n");
        css.append("    -fx-background-color: -jjn-danger-pressed;\n");
        css.append("    -fx-background-insets: 0;\n");
        css.append("    -fx-border-width: ").append(btnPressedBorderWidths).append(";\n");
        css.append("    -fx-translate-y: 1.5;\n");
        css.append("}\n\n");

        // Tactile Sidebar Navigation Buttons
        css.append(".nav-button {\n");
        css.append("    -fx-background-color: -jjn-btn-top-hl, -jjn-btn-bg;\n");
        css.append("    -fx-background-insets: 0, 1 0 0 0;\n");
        css.append("    -fx-background-radius: ").append(navRadius).append(", ").append(Math.max(0, navRadius - 1)).append(";\n");
        css.append("    -fx-border-color: -jjn-border -jjn-border -jjn-btn-bot-lip -jjn-border;\n");
        css.append("    -fx-border-width: ").append(mcBevel ? "1.2 1.2 2.8 1.2" : "1.2").append(";\n");
        css.append("    -fx-border-radius: ").append(navRadius).append(";\n");
        css.append("    -fx-padding: ").append(navPadV).append(" 14 ").append(navPadV).append(" 14;\n");
        css.append("    -fx-font-size: ").append(fmt(12.5 * scale)).append("px;\n");
        css.append("    -fx-font-weight: bold;\n");
        css.append("    -fx-effect: ").append(btnShadow).append(";\n");
        css.append("}\n");
        css.append(".nav-button:hover {\n");
        css.append("    -fx-background-color: -jjn-accent-border, -jjn-btn-hover;\n");
        css.append("    -fx-border-color: -jjn-accent-border -jjn-accent-border -jjn-btn-bot-lip -jjn-accent-border;\n");
        css.append("    -fx-text-fill: -jjn-text;\n");
        css.append("}\n");
        css.append(".nav-button:pressed {\n");
        css.append("    -fx-background-color: -jjn-btn-pressed;\n");
        css.append("    -fx-background-insets: 0;\n");
        css.append("    -fx-border-width: ").append(mcBevel ? "2.8 1.2 1.2 1.2" : "1.2").append(";\n");
        css.append("    -fx-translate-y: 1.5;\n");
        css.append("}\n");
        css.append(".nav-button-active {\n");
        css.append("    -fx-background-color: -jjn-accent-border, -jjn-accent;\n");
        css.append("    -fx-background-insets: 0, 1 1 2 1;\n");
        css.append("    -fx-background-radius: ").append(navRadius).append(", ").append(Math.max(0, navRadius - 1)).append(";\n");
        css.append("    -fx-border-color: #FFFFFF -jjn-accent-border -jjn-accent-lip #FFFFFF;\n");
        css.append("    -fx-border-width: ").append(mcBevel ? "1.5 1.5 3.0 3.5" : "1.5 1.5 1.5 4.0").append(";\n");
        css.append("    -fx-border-radius: ").append(navRadius).append(";\n");
        css.append("    -fx-text-fill: -jjn-accent-text;\n");
        css.append("    -fx-effect: ").append(primaryGlow).append(";\n");
        css.append("}\n");
        css.append(".nav-button-active:hover {\n");
        css.append("    -fx-background-color: #FFFFFF, -jjn-accent-hover;\n");
        css.append("    -fx-text-fill: -jjn-accent-text;\n");
        css.append("}\n\n");

        // Shop Nav Button
        css.append(".nav-button-shop {\n");
        css.append("    -fx-background-color: -jjn-accent-border, -jjn-accent-soft;\n");
        css.append("    -fx-background-insets: 0, 1 0 0 0;\n");
        css.append("    -fx-background-radius: ").append(navRadius).append(", ").append(Math.max(0, navRadius - 1)).append(";\n");
        css.append("    -fx-border-color: -jjn-accent -jjn-accent -jjn-accent-lip -jjn-accent;\n");
        css.append("    -fx-border-width: ").append(mcBevel ? "1.5 1.5 3.0 1.5" : "1.5").append(";\n");
        css.append("    -fx-border-radius: ").append(navRadius).append(";\n");
        css.append("    -fx-padding: ").append(navPadV).append(" 14 ").append(navPadV).append(" 14;\n");
        css.append("    -fx-font-size: ").append(fmt(12 * scale)).append("px;\n");
        css.append("    -fx-font-weight: bold;\n");
        css.append("    -fx-text-fill: -jjn-accent-fg;\n");
        css.append("    -fx-effect: ").append(btnShadow).append(";\n");
        css.append("}\n");
        css.append(".nav-button-shop:hover {\n");
        css.append("    -fx-background-color: -jjn-accent-border, -jjn-btn-hover;\n");
        css.append("    -fx-border-color: #FFFFFF -jjn-accent-border -jjn-accent-lip -jjn-accent-border;\n");
        css.append("    -fx-text-fill: -jjn-text;\n");
        css.append("}\n");
        css.append(".nav-button-shop:pressed {\n");
        css.append("    -fx-translate-y: 1.5;\n");
        css.append("}\n");
        css.append(".nav-button-shop.nav-button-active {\n");
        css.append("    -fx-background-color: -jjn-accent-border, -jjn-accent;\n");
        css.append("    -fx-text-fill: -jjn-accent-text;\n");
        css.append("    -fx-border-color: #FFFFFF -jjn-accent-border -jjn-accent-lip #FFFFFF;\n");
        css.append("}\n\n");

        // Tactile Pill Toggles
        int pillRadius = "pill".equals(btnStyle) ? 999 : Math.max(2, btnRadius - 1);
        css.append(".pill-toggle {\n");
        css.append("    -fx-background-color: -jjn-btn-bg;\n");
        css.append("    -fx-background-radius: ").append(pillRadius).append(";\n");
        css.append("    -fx-border-color: -jjn-border -jjn-border -jjn-btn-bot-lip -jjn-border;\n");
        css.append("    -fx-border-width: ").append(mcBevel ? "1 1 2.5 1" : "1").append(";\n");
        css.append("    -fx-border-radius: ").append(pillRadius).append(";\n");
        css.append("    -fx-font-size: ").append(fmt(11.5 * scale)).append("px;\n");
        css.append("    -fx-padding: 7 12 7 12;\n");
        css.append("}\n");
        css.append(".pill-toggle:hover {\n");
        css.append("    -fx-background-color: -jjn-btn-hover;\n");
        css.append("    -fx-border-color: -jjn-accent-border -jjn-accent-border -jjn-btn-bot-lip -jjn-accent-border;\n");
        css.append("    -fx-text-fill: -jjn-text;\n");
        css.append("}\n");
        css.append(".pill-toggle:pressed {\n");
        css.append("    -fx-translate-y: 1.0;\n");
        css.append("}\n");
        css.append(".pill-toggle:selected {\n");
        css.append("    -fx-background-color: -jjn-accent-border, -jjn-accent;\n");
        css.append("    -fx-background-insets: 0, 1 0 0 0;\n");
        css.append("    -fx-border-color: #FFFFFF -jjn-accent-border -jjn-accent-lip -jjn-accent-border;\n");
        css.append("    -fx-border-width: ").append(mcBevel ? "1.2 1.2 2.5 1.2" : "1.2").append(";\n");
        css.append("    -fx-text-fill: -jjn-accent-text;\n");
        css.append("}\n\n");

        // Dialog Buttons
        css.append(".dialog-pane .button {\n");
        css.append("    -fx-background-color: -jjn-btn-top-hl, -jjn-btn-bg;\n");
        css.append("    -fx-background-insets: 0, 1 0 0 0;\n");
        css.append("    -fx-background-radius: ").append(btnRadius).append(", ").append(Math.max(0, btnRadius - 1)).append(";\n");
        css.append("    -fx-border-color: -jjn-btn-border -jjn-btn-border -jjn-btn-bot-lip -jjn-btn-border;\n");
        css.append("    -fx-border-width: ").append(btnBorderWidths).append(";\n");
        css.append("    -fx-border-radius: ").append(btnRadius).append(";\n");
        css.append("    -fx-text-fill: -jjn-text;\n");
        css.append("    -fx-font-weight: bold;\n");
        css.append("}\n");
        css.append(".dialog-pane .button:hover { -fx-background-color: -jjn-accent-border, -jjn-btn-hover; }\n");
        css.append(".dialog-pane .button:pressed { -fx-translate-y: 1.5; }\n");
        css.append(".dialog-pane .button:default {\n");
        css.append("    -fx-background-color: -jjn-accent-border, -jjn-accent;\n");
        css.append("    -fx-border-color: #FFFFFF -jjn-accent-border -jjn-accent-lip -jjn-accent-border;\n");
        css.append("    -fx-text-fill: -jjn-accent-text;\n");
        css.append("}\n");
        css.append(".dialog-pane .button:default:hover { -fx-background-color: #FFFFFF, -jjn-accent-hover; }\n");

        return css.toString();
    }

    private static Color parseColor(String hex, Color fallback) {
        if (hex == null || hex.isBlank()) {
            return fallback;
        }
        try {
            return Color.web(hex.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Color mix(Color a, Color b, double t) {
        return a.interpolate(b, Math.max(0.0, Math.min(1.0, t)));
    }

    private static double luminance(Color c) {
        return 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
    }

    public static String hex(Color c) {
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return String.format("#%02X%02X%02X", clamp(r), clamp(g), clamp(b));
    }

    private static String rgba(Color c, double alpha) {
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return String.format(Locale.US, "rgba(%d, %d, %d, %.2f)", clamp(r), clamp(g), clamp(b), alpha);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static String fmt(double d) {
        return String.format(Locale.US, "%.1f", d);
    }
}
