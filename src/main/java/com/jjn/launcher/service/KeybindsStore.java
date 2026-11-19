package com.jjn.launcher.service;

import com.jjn.launcher.model.Instance;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Reads, writes, and manages Minecraft + modded keybinds and control options
 * inside each instance's <game>/options.txt.
 */
public final class KeybindsStore {

    public static final String UNKNOWN_KEY = "key.keyboard.unknown";

    public static final List<String> CATEGORIES = List.of(
            "Movement",
            "Gameplay",
            "Inventory",
            "Multiplayer",
            "Misc",
            "Debug",
            "Modded"
    );

    public record KeybindDef(
            String optionKey,
            String label,
            String category,
            String defaultValue
    ) {}

    public static final class KeybindEntry {
        public final String optionKey;
        public final String label;
        public final String category;
        public final String defaultValue;
        public final boolean custom;
        public String value;

        public KeybindEntry(String optionKey, String label, String category, String defaultValue, String value, boolean custom) {
            this.optionKey = optionKey;
            this.label = label;
            this.category = category;
            this.defaultValue = defaultValue;
            this.value = value;
            this.custom = custom;
        }
    }

    public static final class ControlOptions {
        public boolean toggleCrouch = false;
        public boolean toggleSprint = false;
        public boolean autoJump = false;
        public boolean invertYMouse = false;
        public boolean rawMouseInput = true;
        public double mouseSensitivity = 0.5;
    }

    public static final class OptionsSnapshot {
        public final Path optionsFile;
        public final boolean fileExisted;
        public final LinkedHashMap<String, String> rawLines;
        public final List<KeybindEntry> entries;
        public final ControlOptions controls;

        public OptionsSnapshot(
                Path optionsFile,
                boolean fileExisted,
                LinkedHashMap<String, String> rawLines,
                List<KeybindEntry> entries,
                ControlOptions controls
        ) {
            this.optionsFile = optionsFile;
            this.fileExisted = fileExisted;
            this.rawLines = rawLines;
            this.entries = entries;
            this.controls = controls;
        }
    }

    public static final List<KeybindDef> STANDARD_KEYBINDS = List.of(
            // Movement
            new KeybindDef("key_key.forward", "Walk Forwards", "Movement", "key.keyboard.w"),
            new KeybindDef("key_key.left", "Strafe Left", "Movement", "key.keyboard.a"),
            new KeybindDef("key_key.back", "Walk Backwards", "Movement", "key.keyboard.s"),
            new KeybindDef("key_key.right", "Strafe Right", "Movement", "key.keyboard.d"),
            new KeybindDef("key_key.jump", "Jump", "Movement", "key.keyboard.space"),
            new KeybindDef("key_key.sneak", "Sneak", "Movement", "key.keyboard.left.shift"),
            new KeybindDef("key_key.sprint", "Sprint", "Movement", "key.keyboard.left.control"),

            // Gameplay
            new KeybindDef("key_key.attack", "Attack / Destroy", "Gameplay", "key.mouse.left"),
            new KeybindDef("key_key.use", "Use Item / Place Block", "Gameplay", "key.mouse.right"),
            new KeybindDef("key_key.pickItem", "Pick Block", "Gameplay", "key.mouse.middle"),
            new KeybindDef("key_key.drop", "Drop Selected Item", "Gameplay", "key.keyboard.q"),
            new KeybindDef("key_key.swapOffhand", "Swap Item With Offhand", "Gameplay", "key.keyboard.f"),

            // Inventory & Hotbar
            new KeybindDef("key_key.inventory", "Open / Close Inventory", "Inventory", "key.keyboard.e"),
            new KeybindDef("key_key.hotbar.1", "Hotbar Slot 1", "Inventory", "key.keyboard.1"),
            new KeybindDef("key_key.hotbar.2", "Hotbar Slot 2", "Inventory", "key.keyboard.2"),
            new KeybindDef("key_key.hotbar.3", "Hotbar Slot 3", "Inventory", "key.keyboard.3"),
            new KeybindDef("key_key.hotbar.4", "Hotbar Slot 4", "Inventory", "key.keyboard.4"),
            new KeybindDef("key_key.hotbar.5", "Hotbar Slot 5", "Inventory", "key.keyboard.5"),
            new KeybindDef("key_key.hotbar.6", "Hotbar Slot 6", "Inventory", "key.keyboard.6"),
            new KeybindDef("key_key.hotbar.7", "Hotbar Slot 7", "Inventory", "key.keyboard.7"),
            new KeybindDef("key_key.hotbar.8", "Hotbar Slot 8", "Inventory", "key.keyboard.8"),
            new KeybindDef("key_key.hotbar.9", "Hotbar Slot 9", "Inventory", "key.keyboard.9"),
            new KeybindDef("key_key.saveToolbarActivator", "Save Hotbar Activator", "Inventory", "key.keyboard.c"),
            new KeybindDef("key_key.loadToolbarActivator", "Load Hotbar Activator", "Inventory", "key.keyboard.x"),

            // Multiplayer & Social
            new KeybindDef("key_key.chat", "Open Chat", "Multiplayer", "key.keyboard.t"),
            new KeybindDef("key_key.command", "Open Command", "Multiplayer", "key.keyboard.slash"),
            new KeybindDef("key_key.playerlist", "List Players", "Multiplayer", "key.keyboard.tab"),
            new KeybindDef("key_key.friends", "Friends Screen", "Multiplayer", "key.keyboard.o"),
            new KeybindDef("key_key.socialInteractions", "Social Interactions", "Multiplayer", "key.keyboard.p"),
            new KeybindDef("key_key.advancements", "Advancements", "Multiplayer", "key.keyboard.l"),
            new KeybindDef("key_key.quickActions", "Quick Actions", "Multiplayer", "key.keyboard.g"),

            // Miscellaneous & Camera
            new KeybindDef("key_key.screenshot", "Take Screenshot", "Misc", "key.keyboard.f2"),
            new KeybindDef("key_key.toggleGui", "Toggle HUD", "Misc", "key.keyboard.f1"),
            new KeybindDef("key_key.togglePerspective", "Toggle Perspective", "Misc", "key.keyboard.f5"),
            new KeybindDef("key_key.fullscreen", "Toggle Fullscreen", "Misc", "key.keyboard.f11"),
            new KeybindDef("key_key.smoothCamera", "Toggle Cinematic Camera", "Misc", UNKNOWN_KEY),
            new KeybindDef("key_key.spectatorOutlines", "Highlight Players (Spectator)", "Misc", UNKNOWN_KEY),
            new KeybindDef("key_key.spectatorHotbar", "Spectator Hotbar", "Misc", "key.mouse.middle"),
            new KeybindDef("key_key.toggleSpectatorShaderEffects", "Toggle Spectator Shader", "Misc", "key.keyboard.f4"),
            new KeybindDef("key_key.modmenu.open_menu", "Open Mod Menu", "Misc", UNKNOWN_KEY),

            // Debug (F3+ chords)
            new KeybindDef("key_key.debug.overlay", "Debug Overlay", "Debug", "key.keyboard.f3"),
            new KeybindDef("key_key.debug.modifier", "Debug Modifier", "Debug", "key.keyboard.f3"),
            new KeybindDef("key_key.debug.reloadChunk", "Debug: Reload Chunks", "Debug", "key.keyboard.a"),
            new KeybindDef("key_key.debug.showHitboxes", "Debug: Show Hitboxes", "Debug", "key.keyboard.b"),
            new KeybindDef("key_key.debug.clearChat", "Debug: Clear Chat", "Debug", "key.keyboard.d"),
            new KeybindDef("key_key.debug.crash", "Debug: Force Crash", "Debug", "key.keyboard.c"),
            new KeybindDef("key_key.debug.showChunkBorders", "Debug: Show Chunk Borders", "Debug", "key.keyboard.g"),
            new KeybindDef("key_key.debug.showAdvancedTooltips", "Debug: Advanced Tooltips", "Debug", "key.keyboard.h"),
            new KeybindDef("key_key.debug.copyRecreateCommand", "Debug: Copy Recreate Command", "Debug", "key.keyboard.i"),
            new KeybindDef("key_key.debug.spectate", "Debug: Spectate", "Debug", "key.keyboard.n"),
            new KeybindDef("key_key.debug.switchGameMode", "Debug: Switch Game Mode", "Debug", "key.keyboard.f4"),
            new KeybindDef("key_key.debug.debugOptions", "Debug: Debug Options", "Debug", "key.keyboard.f6"),
            new KeybindDef("key_key.debug.focusPause", "Debug: Pause on Lost Focus", "Debug", "key.keyboard.p"),
            new KeybindDef("key_key.debug.dumpDynamicTextures", "Debug: Dump Dynamic Textures", "Debug", "key.keyboard.s"),
            new KeybindDef("key_key.debug.reloadResourcePacks", "Debug: Reload Resource Packs", "Debug", "key.keyboard.t"),
            new KeybindDef("key_key.debug.profiling", "Debug: Profiling", "Debug", "key.keyboard.l"),
            new KeybindDef("key_key.debug.copyLocation", "Debug: Copy Location", "Debug", "key.keyboard.c"),
            new KeybindDef("key_key.debug.dumpVersion", "Debug: Dump Version", "Debug", "key.keyboard.v"),
            new KeybindDef("key_key.debug.profilingChart", "Debug: Profiling Chart", "Debug", "key.keyboard.1"),
            new KeybindDef("key_key.debug.fpsCharts", "Debug: FPS Charts", "Debug", "key.keyboard.2"),
            new KeybindDef("key_key.debug.networkCharts", "Debug: Network Charts", "Debug", "key.keyboard.3"),
            new KeybindDef("key_key.debug.lightmapTexture", "Debug: Lightmap Texture", "Debug", "key.keyboard.4")
    );

    public record SpecialKeyOption(String mcKey, String displayName) {}

    public static final List<SpecialKeyOption> SPECIAL_KEY_OPTIONS = List.of(
            new SpecialKeyOption("key.mouse.left", "Left Click (Mouse 1)"),
            new SpecialKeyOption("key.mouse.right", "Right Click (Mouse 2)"),
            new SpecialKeyOption("key.mouse.middle", "Middle Click (Mouse 3)"),
            new SpecialKeyOption("key.mouse.4", "Mouse 4 (Side Back)"),
            new SpecialKeyOption("key.mouse.5", "Mouse 5 (Side Forward)"),
            new SpecialKeyOption("key.keyboard.left.shift", "Left Shift"),
            new SpecialKeyOption("key.keyboard.right.shift", "Right Shift"),
            new SpecialKeyOption("key.keyboard.left.control", "Left Ctrl"),
            new SpecialKeyOption("key.keyboard.right.control", "Right Ctrl"),
            new SpecialKeyOption("key.keyboard.left.alt", "Left Alt"),
            new SpecialKeyOption("key.keyboard.right.alt", "Right Alt"),
            new SpecialKeyOption("key.keyboard.grave.accent", "Grave Accent (`)"),
            new SpecialKeyOption("key.keyboard.caps.lock", "Caps Lock"),
            new SpecialKeyOption("key.keyboard.tab", "Tab"),
            new SpecialKeyOption(UNKNOWN_KEY, "Unbound")
    );

    private KeybindsStore() {}

    public static Path optionsFile(Instance instance) {
        return instance.gameFolder().resolve("options.txt");
    }

    public static OptionsSnapshot load(Instance instance) {
        Path file = optionsFile(instance);
        boolean existed = Files.isRegularFile(file);
        LinkedHashMap<String, String> raw = readRawOptions(file);

        ControlOptions controls = new ControlOptions();
        controls.toggleCrouch = parseBool(raw.get("toggleCrouch"), false);
        controls.toggleSprint = parseBool(raw.get("toggleSprint"), false);
        controls.autoJump = parseBool(raw.get("autoJump"), false);
        controls.invertYMouse = parseBool(raw.get("invertYMouse"), false);
        controls.rawMouseInput = parseBool(raw.get("rawMouseInput"), true);
        controls.mouseSensitivity = clampSensitivity(parseDouble(raw.get("mouseSensitivity"), 0.5));

        List<KeybindEntry> entries = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();

        for (KeybindDef def : STANDARD_KEYBINDS) {
            String val = raw.getOrDefault(def.optionKey(), def.defaultValue());
            if (val == null || val.isBlank()) {
                val = def.defaultValue();
            }
            entries.add(new KeybindEntry(def.optionKey(), def.label(), def.category(), def.defaultValue(), val.trim(), false));
            seenKeys.add(def.optionKey());
        }

        // Discover any extra/modded key_* entries already in options.txt
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String k = e.getKey();
            if (k != null && k.startsWith("key_") && seenKeys.add(k)) {
                String val = (e.getValue() == null || e.getValue().isBlank()) ? UNKNOWN_KEY : e.getValue().trim();
                entries.add(new KeybindEntry(k, prettifyOptionKey(k), "Modded", UNKNOWN_KEY, val, true));
            }
        }

        return new OptionsSnapshot(file, existed, raw, entries, controls);
    }

    public static void save(Instance instance, List<KeybindEntry> entries, ControlOptions controls) throws IOException {
        Path file = optionsFile(instance);
        Files.createDirectories(file.getParent());

        LinkedHashMap<String, String> merged = readRawOptions(file);
        if (controls != null) {
            merged.put("autoJump", String.valueOf(controls.autoJump));
            merged.put("invertYMouse", String.valueOf(controls.invertYMouse));
            merged.put("toggleCrouch", String.valueOf(controls.toggleCrouch));
            merged.put("toggleSprint", String.valueOf(controls.toggleSprint));
            merged.put("mouseSensitivity", formatSensitivity(controls.mouseSensitivity));
            merged.put("rawMouseInput", String.valueOf(controls.rawMouseInput));
        }

        if (entries != null) {
            for (KeybindEntry entry : entries) {
                if (entry == null || entry.optionKey == null || entry.optionKey.isBlank()) {
                    continue;
                }
                String val = (entry.value == null || entry.value.isBlank()) ? UNKNOWN_KEY : entry.value.trim();
                merged.put(entry.optionKey, val);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : merged.entrySet()) {
            sb.append(e.getKey()).append(':').append(e.getValue()).append('\n');
        }

        Path tmp = file.resolveSibling("options.txt.tmp");
        Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    public static boolean isDebugKey(String optionKey) {
        return optionKey != null && optionKey.startsWith("key_key.debug.");
    }

    /**
     * Counts how many times each bound key appears within the same conflict group
     * (standard keys vs. F3+ debug chord keys).
     */
    public static ConflictReport analyzeConflicts(List<KeybindEntry> entries) {
        Map<String, Integer> normalCounts = new HashMap<>();
        Map<String, Integer> debugCounts = new HashMap<>();

        if (entries != null) {
            for (KeybindEntry e : entries) {
                if (e == null || e.value == null || e.value.isBlank() || UNKNOWN_KEY.equals(e.value)) {
                    continue;
                }
                Map<String, Integer> target = isDebugKey(e.optionKey) ? debugCounts : normalCounts;
                target.merge(e.value, 1, Integer::sum);
            }
        }

        Set<String> conflictingOptionKeys = new LinkedHashSet<>();
        if (entries != null) {
            for (KeybindEntry e : entries) {
                if (e == null || e.value == null || e.value.isBlank() || UNKNOWN_KEY.equals(e.value)) {
                    continue;
                }
                // F3 overlay + modifier share F3 by design in MC 1.21+/26.2
                if ("key_key.debug.overlay".equals(e.optionKey) || "key_key.debug.modifier".equals(e.optionKey)) {
                    continue;
                }
                Map<String, Integer> target = isDebugKey(e.optionKey) ? debugCounts : normalCounts;
                if (target.getOrDefault(e.value, 0) > 1) {
                    conflictingOptionKeys.add(e.optionKey);
                }
            }
        }
        return new ConflictReport(conflictingOptionKeys);
    }

    public record ConflictReport(Set<String> conflictingOptionKeys) {
        public boolean isConflicting(String optionKey) {
            return conflictingOptionKeys.contains(optionKey);
        }

        public int count() {
            return conflictingOptionKeys.size();
        }
    }

    public static String normalizeOptionKey(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.startsWith("key_")) {
            return trimmed;
        }
        if (trimmed.startsWith("key.")) {
            return "key_" + trimmed;
        }
        return "key_key." + trimmed;
    }

    public static String prettifyOptionKey(String optionKey) {
        if (optionKey == null || optionKey.isBlank()) return "Custom Key";
        String clean = optionKey;
        if (clean.startsWith("key_key.")) {
            clean = clean.substring("key_key.".length());
        } else if (clean.startsWith("key_")) {
            clean = clean.substring("key_".length());
        }
        String[] parts = clean.split("[._]+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
        }
        return sb.isEmpty() ? optionKey : sb.toString();
    }

    public static String formatKeyDisplay(String mcKey) {
        if (mcKey == null || mcKey.isBlank() || UNKNOWN_KEY.equals(mcKey)) {
            return "Unbound";
        }
        return switch (mcKey) {
            case "key.mouse.left" -> "Left Click";
            case "key.mouse.right" -> "Right Click";
            case "key.mouse.middle" -> "Middle Click";
            case "key.mouse.4" -> "Mouse 4";
            case "key.mouse.5" -> "Mouse 5";
            case "key.mouse.6" -> "Mouse 6";
            case "key.mouse.7" -> "Mouse 7";
            case "key.mouse.8" -> "Mouse 8";
            case "key.keyboard.left.shift" -> "Left Shift";
            case "key.keyboard.right.shift" -> "Right Shift";
            case "key.keyboard.left.control" -> "Left Ctrl";
            case "key.keyboard.right.control" -> "Right Ctrl";
            case "key.keyboard.left.alt" -> "Left Alt";
            case "key.keyboard.right.alt" -> "Right Alt";
            case "key.keyboard.left.win" -> "Left Win";
            case "key.keyboard.right.win" -> "Right Win";
            case "key.keyboard.space" -> "Space";
            case "key.keyboard.enter" -> "Enter";
            case "key.keyboard.tab" -> "Tab";
            case "key.keyboard.backspace" -> "Backspace";
            case "key.keyboard.caps.lock" -> "Caps Lock";
            case "key.keyboard.num.lock" -> "Num Lock";
            case "key.keyboard.scroll.lock" -> "Scroll Lock";
            case "key.keyboard.page.up" -> "Page Up";
            case "key.keyboard.page.down" -> "Page Down";
            case "key.keyboard.grave.accent" -> "` (Grave)";
            case "key.keyboard.minus" -> "- (Minus)";
            case "key.keyboard.equal" -> "= (Equal)";
            case "key.keyboard.left.bracket" -> "[ (L Bracket)";
            case "key.keyboard.right.bracket" -> "] (R Bracket)";
            case "key.keyboard.backslash" -> "\\ (Backslash)";
            case "key.keyboard.semicolon" -> "; (Semicolon)";
            case "key.keyboard.apostrophe" -> "' (Apostrophe)";
            case "key.keyboard.comma" -> ", (Comma)";
            case "key.keyboard.period" -> ". (Period)";
            case "key.keyboard.slash" -> "/ (Slash)";
            case "key.keyboard.print.screen" -> "Print Screen";
            case "key.keyboard.keypad.decimal" -> "Numpad .";
            case "key.keyboard.keypad.divide" -> "Numpad /";
            case "key.keyboard.keypad.multiply" -> "Numpad *";
            case "key.keyboard.keypad.subtract" -> "Numpad -";
            case "key.keyboard.keypad.add" -> "Numpad +";
            case "key.keyboard.keypad.enter" -> "Numpad Enter";
            case "key.keyboard.keypad.equal" -> "Numpad =";
            default -> {
                String raw = mcKey;
                if (raw.startsWith("key.keyboard.keypad.")) {
                    yield "Numpad " + raw.substring("key.keyboard.keypad.".length()).toUpperCase(Locale.ROOT);
                }
                if (raw.startsWith("key.keyboard.")) {
                    yield raw.substring("key.keyboard.".length()).replace('.', ' ').toUpperCase(Locale.ROOT);
                }
                yield raw;
            }
        };
    }

    public static String fromJavaFxMouseButton(MouseButton button) {
        if (button == null) return null;
        return switch (button) {
            case PRIMARY -> "key.mouse.left";
            case SECONDARY -> "key.mouse.right";
            case MIDDLE -> "key.mouse.middle";
            case BACK -> "key.mouse.4";
            case FORWARD -> "key.mouse.5";
            default -> null;
        };
    }

    public static String fromJavaFxKeyEvent(KeyEvent event) {
        if (event == null) return null;
        KeyCode code = event.getCode();
        if (code == null) return null;
        if (code == KeyCode.ESCAPE) {
            return UNKNOWN_KEY;
        }
        if (code.isLetterKey()) {
            return "key.keyboard." + code.name().toLowerCase(Locale.ROOT);
        }
        if (code.isDigitKey()) {
            String n = code.name(); // DIGIT0 .. DIGIT9
            if (n.startsWith("DIGIT") && n.length() == 6) {
                return "key.keyboard." + n.charAt(5);
            }
        }
        return switch (code) {
            case SPACE -> "key.keyboard.space";
            case ENTER -> "key.keyboard.enter";
            case TAB -> "key.keyboard.tab";
            case BACK_SPACE -> "key.keyboard.backspace";
            case INSERT -> "key.keyboard.insert";
            case DELETE -> "key.keyboard.delete";
            case HOME -> "key.keyboard.home";
            case END -> "key.keyboard.end";
            case PAGE_UP -> "key.keyboard.page.up";
            case PAGE_DOWN -> "key.keyboard.page.down";
            case UP -> "key.keyboard.up";
            case DOWN -> "key.keyboard.down";
            case LEFT -> "key.keyboard.left";
            case RIGHT -> "key.keyboard.right";
            case SHIFT -> "key.keyboard.left.shift";
            case CONTROL -> "key.keyboard.left.control";
            case ALT, ALT_GRAPH -> "key.keyboard.left.alt";
            case WINDOWS, META, COMMAND -> "key.keyboard.left.win";
            case CAPS -> "key.keyboard.caps.lock";
            case SCROLL_LOCK -> "key.keyboard.scroll.lock";
            case NUM_LOCK -> "key.keyboard.num.lock";
            case PRINTSCREEN -> "key.keyboard.print.screen";
            case PAUSE -> "key.keyboard.pause";
            case CONTEXT_MENU -> "key.keyboard.menu";
            case BACK_QUOTE -> "key.keyboard.grave.accent";
            case MINUS -> "key.keyboard.minus";
            case EQUALS -> "key.keyboard.equal";
            case OPEN_BRACKET -> "key.keyboard.left.bracket";
            case CLOSE_BRACKET -> "key.keyboard.right.bracket";
            case BACK_SLASH -> "key.keyboard.backslash";
            case SEMICOLON -> "key.keyboard.semicolon";
            case QUOTE -> "key.keyboard.apostrophe";
            case COMMA -> "key.keyboard.comma";
            case PERIOD -> "key.keyboard.period";
            case SLASH -> "key.keyboard.slash";
            case NUMPAD0 -> "key.keyboard.keypad.0";
            case NUMPAD1 -> "key.keyboard.keypad.1";
            case NUMPAD2 -> "key.keyboard.keypad.2";
            case NUMPAD3 -> "key.keyboard.keypad.3";
            case NUMPAD4 -> "key.keyboard.keypad.4";
            case NUMPAD5 -> "key.keyboard.keypad.5";
            case NUMPAD6 -> "key.keyboard.keypad.6";
            case NUMPAD7 -> "key.keyboard.keypad.7";
            case NUMPAD8 -> "key.keyboard.keypad.8";
            case NUMPAD9 -> "key.keyboard.keypad.9";
            case DECIMAL -> "key.keyboard.keypad.decimal";
            case DIVIDE -> "key.keyboard.keypad.divide";
            case MULTIPLY -> "key.keyboard.keypad.multiply";
            case SUBTRACT -> "key.keyboard.keypad.subtract";
            case ADD -> "key.keyboard.keypad.add";
            case F1 -> "key.keyboard.f1";
            case F2 -> "key.keyboard.f2";
            case F3 -> "key.keyboard.f3";
            case F4 -> "key.keyboard.f4";
            case F5 -> "key.keyboard.f5";
            case F6 -> "key.keyboard.f6";
            case F7 -> "key.keyboard.f7";
            case F8 -> "key.keyboard.f8";
            case F9 -> "key.keyboard.f9";
            case F10 -> "key.keyboard.f10";
            case F11 -> "key.keyboard.f11";
            case F12 -> "key.keyboard.f12";
            case F13 -> "key.keyboard.f13";
            case F14 -> "key.keyboard.f14";
            case F15 -> "key.keyboard.f15";
            case F16 -> "key.keyboard.f16";
            case F17 -> "key.keyboard.f17";
            case F18 -> "key.keyboard.f18";
            case F19 -> "key.keyboard.f19";
            case F20 -> "key.keyboard.f20";
            default -> null;
        };
    }

    private static LinkedHashMap<String, String> readRawOptions(Path file) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        if (file == null || !Files.isRegularFile(file)) {
            return map;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String key = line.substring(0, colon);
                    String val = line.substring(colon + 1);
                    map.put(key, val);
                }
            }
        } catch (IOException ignored) {
        }
        return map;
    }

    private static boolean parseBool(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        return Boolean.parseBoolean(raw.trim());
    }

    private static double parseDouble(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public static double clampSensitivity(double val) {
        if (Double.isNaN(val) || Double.isInfinite(val)) return 0.5;
        return Math.max(0.0, Math.min(1.0, val));
    }

    private static String formatSensitivity(double val) {
        double c = clampSensitivity(val);
        return String.format(Locale.ROOT, "%.4f", c).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
