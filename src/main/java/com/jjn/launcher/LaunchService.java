package com.jjn.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jjn.launcher.model.Instance;
import com.jjn.launcher.service.ClientModBundler;
import com.jjn.launcher.service.FabricInstallService;
import com.jjn.launcher.service.JavaProvisionService;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the Java command for an installed Minecraft version (including
 * inherited mod-loader profiles like Fabric via {@code inheritsFrom}) and
 * starts it, streaming output both to {@code logs/jjn-launch.log} and an
 * optional live console consumer.
 *
 * The launch log never contains the access token (it is replaced by ***).
 */
public class LaunchService {

    private final JavaProvisionService javaProvisionService = new JavaProvisionService();

    // SESSION

    public static class Session {

        public final String username;
        public final String uuid;
        public final String accessToken;
        public final String userType;

        public Session(String username, String uuid,
                       String accessToken, String userType) {
            this.username = username;
            this.uuid = uuid;
            this.accessToken = accessToken;
            this.userType = userType;
        }

        /** Local test session. Cannot join online servers. */
        public static Session offline(String username) {

            String uuid = UUID.nameUUIDFromBytes(
                    ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)
            ).toString();

            return new Session(username, uuid, "0", "legacy");
        }
    }

    public static class LaunchResult {

        public final Process process;
        public final Path logFile;
        public final String javaDescription;

        public LaunchResult(Process process, Path logFile, String javaDescription) {
            this.process = process;
            this.logFile = logFile;
            this.javaDescription = javaDescription;
        }
    }

    public interface StatusListener {
        void onStatus(String message);
    }

    // LAUNCH (backwards-compatible overload + full instance-aware overload)

    public LaunchResult launch(Path gameFolder,
                               String version,
                               LauncherSettings settings,
                               Session session) throws Exception {
        Instance temp = new Instance("temp", "Instance", gameFolder.getParent());
        temp.version = version;
        return launch(temp, settings, session, null, null);
    }

    public LaunchResult launch(Instance instance,
                               LauncherSettings settings,
                               Session session,
                               StatusListener statusListener,
                               Consumer<String> logConsumer) throws Exception {
        return launch(instance, settings, session, null, statusListener, logConsumer);
    }

    public LaunchResult launch(Instance instance,
                               LauncherSettings settings,
                               Session session,
                               String quickPlayOverride,
                               StatusListener statusListener,
                               Consumer<String> logConsumer) throws Exception {

        Path gameFolder = instance.gameFolder();
        Path sharedFolder = LauncherSettings.sharedDir();
        String version = instance.version;

        if (version == null || version.isBlank()) {
            throw new Exception("No Minecraft version is installed in this instance.");
        }

        ClientModBundler.ensureInstalled(instance);

        // Load child -> parent inheritance chain
        List<JsonObject> chain = loadVersionChain(version, gameFolder, sharedFolder);
        JsonObject leaf = chain.get(0);

        String mainClass = findStringInChain(chain, "mainClass");
        if (mainClass == null || mainClass.isBlank()) {
            throw new Exception("The version file has no main class.");
        }

        // JAVA

        int requiredJava = findJavaMajorInChain(chain);

        JavaDetector.JavaInstall java = chooseJava(
                settings, requiredJava, version, statusListener
        );

        Path javaExe = preferJavaw(java.javaExe());

        // CLASSPATH (walk child -> parent so child overrides duplicate group:artifact)

        List<Path> classpath = new ArrayList<>();
        Set<String> seenArtifacts = new HashSet<>();
        Set<String> seenPaths = new HashSet<>();

        for (JsonObject versionJson : chain) {
            if (!versionJson.has("libraries")) {
                continue;
            }

            for (JsonElement element : versionJson.getAsJsonArray("libraries")) {
                JsonObject library = element.getAsJsonObject();

                if (library.has("rules") && !rulesAllow(library.getAsJsonArray("rules"), Map.of())) {
                    continue;
                }

                String relative = resolveLibraryPath(library);
                if (relative == null) {
                    continue;
                }

                String artifactKey = artifactKey(library, relative);
                if (!seenArtifacts.add(artifactKey)) {
                    continue;
                }

                Path file = findExistingFile(
                        sharedFolder.resolve("libraries").resolve(relative).normalize(),
                        gameFolder.resolve("libraries").resolve(relative).normalize()
                );

                if (file == null) {
                    Path expected = sharedFolder.resolve("libraries").resolve(relative).normalize();
                    throw new Exception(
                            "A game file is missing:\n" + expected +
                                    "\nClick Install again to repair it."
                    );
                }

                if (seenPaths.add(file.toString())) {
                    classpath.add(file);
                }
            }
        }

        // Add client jar (find the version in the chain that actually has the client jar)
        Path clientJar = findClientJar(chain, gameFolder, sharedFolder);
        if (clientJar == null) {
            throw new Exception(
                    "Minecraft client jar is missing. Click Install again to repair it."
            );
        }
        classpath.add(clientJar);

        StringJoiner classpathText = new StringJoiner(File.pathSeparator);
        for (Path file : classpath) {
            classpathText.add(file.toString());
        }

        // ASSETS & LOGGING

        Path assetsFolder = Files.isDirectory(sharedFolder.resolve("assets"))
                ? sharedFolder.resolve("assets")
                : gameFolder.resolve("assets");

        String assetIndexId = resolveAssetIndexId(chain);
        String versionType = findStringInChain(chain, "type");
        if (versionType == null) {
            versionType = "release";
        }

        Path nativesFolder = gameFolder.resolve("natives");
        Files.createDirectories(nativesFolder);

        // FEATURES & VARIABLES

        int width = instance.customWidth > 0 ? instance.customWidth : settings.windowWidth;
        int height = instance.customHeight > 0 ? instance.customHeight : settings.windowHeight;
        boolean hasCustomResolution = width > 0 && height > 0;
        String rawQuickPlay = (quickPlayOverride != null && !quickPlayOverride.isBlank())
                ? quickPlayOverride
                : instance.quickPlayServer;
        String quickPlayServer = rawQuickPlay == null ? "" : rawQuickPlay.trim();
        boolean hasQuickPlayServer = !quickPlayServer.isEmpty();

        Map<String, Boolean> features = new HashMap<>();
        features.put("has_custom_resolution", hasCustomResolution);
        features.put("is_quick_play_multiplayer", hasQuickPlayServer);
        features.put("has_quick_plays_support", hasQuickPlayServer);

        Path instanceLibs = gameFolder.resolve("libraries");
        Path libraryDirectory = Files.isDirectory(instanceLibs)
                ? instanceLibs
                : sharedFolder.resolve("libraries");

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("natives_directory", nativesFolder.toString());
        vars.put("library_directory", libraryDirectory.toString());
        vars.put("classpath_separator", File.pathSeparator);
        vars.put("launcher_name", "JJN Client");
        vars.put("launcher_version", "1.0.0");
        vars.put("classpath", classpathText.toString());
        vars.put("auth_player_name", session.username);
        vars.put("version_name", version);
        vars.put("game_directory", gameFolder.toString());
        vars.put("assets_root", assetsFolder.toString());
        vars.put("game_assets", assetsFolder.toString());
        vars.put("assets_index_name", assetIndexId);
        vars.put("auth_uuid", session.uuid.replace("-", ""));
        vars.put("auth_access_token", session.accessToken);
        vars.put("auth_session", session.accessToken);
        vars.put("clientid", "0");
        vars.put("auth_xuid", "0");
        vars.put("user_type", session.userType);
        vars.put("version_type", versionType);
        vars.put("user_properties", "{}");
        if (hasCustomResolution) {
            vars.put("resolution_width", String.valueOf(width));
            vars.put("resolution_height", String.valueOf(height));
        }
        if (hasQuickPlayServer) {
            vars.put("quickPlayMultiplayer", quickPlayServer);
            vars.put("quickPlayPath", "quickPlay/log.json");
        }

        // JVM ARGUMENTS

        List<String> jvmArgs = new ArrayList<>();

        int ramMb = instance.ramMbOverride >= LauncherSettings.MIN_RAM_MB
                ? instance.ramMbOverride
                : Math.max(settings.ramMb, LauncherSettings.MIN_RAM_MB);

        jvmArgs.add("-Xmx" + ramMb + "M");
        jvmArgs.addAll(splitArgs(settings.jvmArgs));
        jvmArgs.addAll(splitArgs(instance.jvmArgs));

        // Walk parent -> child for JVM arguments
        boolean addedJvmArgsFromJson = false;
        List<JsonObject> parentFirst = new ArrayList<>(chain);
        Collections.reverse(parentFirst);

        for (JsonObject versionJson : parentFirst) {
            if (versionJson.has("arguments") && versionJson.get("arguments").isJsonObject()) {
                JsonObject arguments = versionJson.getAsJsonObject("arguments");
                if (arguments.has("jvm")) {
                    addArguments(jvmArgs, arguments.getAsJsonArray("jvm"), vars, features);
                    addedJvmArgsFromJson = true;
                }
            }
        }

        if (!addedJvmArgsFromJson) {
            jvmArgs.add(substitute("-Djava.library.path=${natives_directory}", vars));
            jvmArgs.add("-cp");
            jvmArgs.add(substitute("${classpath}", vars));
        }

        // Logging configuration (first defined in chain)
        JsonObject loggingObj = findObjectInChain(chain, "logging");
        if (loggingObj != null && loggingObj.has("client")) {
            JsonObject client = loggingObj.getAsJsonObject("client");
            if (client.has("argument") && client.has("file")) {
                String fileId = client.getAsJsonObject("file").get("id").getAsString();
                Path logConfig = assetsFolder.resolve("log_configs").resolve(fileId);
                if (Files.isRegularFile(logConfig)) {
                    jvmArgs.add(client.get("argument").getAsString()
                            .replace("${path}", logConfig.toString()));
                }
            }
        }

        // GAME ARGUMENTS (parent -> child)

        List<String> gameArgs = new ArrayList<>();
        for (JsonObject versionJson : parentFirst) {
            if (versionJson.has("arguments") && versionJson.get("arguments").isJsonObject()) {
                JsonObject arguments = versionJson.getAsJsonObject("arguments");
                if (arguments.has("game")) {
                    addArguments(gameArgs, arguments.getAsJsonArray("game"), vars, features);
                }
            } else if (versionJson.has("minecraftArguments")) {
                for (String part : versionJson.get("minecraftArguments")
                        .getAsString().trim().split("\\s+")) {
                    gameArgs.add(substitute(part, vars));
                }
            }
        }

        // Fallback Quick Play for older Minecraft versions (< 1.20) that don't have is_quick_play_multiplayer rule
        if (hasQuickPlayServer && !gameArgs.contains("--quickPlayMultiplayer") && !gameArgs.contains("--server")) {
            String[] hostPort = quickPlayServer.split(":", 2);
            gameArgs.add("--server");
            gameArgs.add(hostPort[0]);
            if (hostPort.length == 2 && !hostPort[1].isBlank()) {
                gameArgs.add("--port");
                gameArgs.add(hostPort[1]);
            }
        }

        // Fallback resolution for older versions without has_custom_resolution rule
        if (hasCustomResolution && !gameArgs.contains("--width")) {
            gameArgs.add("--width");
            gameArgs.add(String.valueOf(width));
            gameArgs.add("--height");
            gameArgs.add(String.valueOf(height));
        }

        // COMMAND

        List<String> command = new ArrayList<>();
        command.add(javaExe.toString());
        command.addAll(jvmArgs);
        command.add(mainClass);
        command.addAll(gameArgs);

        // LOG (access token is masked)

        Path logsFolder = gameFolder.resolve("logs");
        Files.createDirectories(logsFolder);

        Path logFile = logsFolder.resolve("jjn-launch.log");

        List<String> shown = new ArrayList<>();
        for (String part : command) {
            if (session.accessToken.length() > 8 && part.equals(session.accessToken)) {
                shown.add("***");
            } else {
                shown.add(part);
            }
        }

        String header =
                "JJN Client launch log\n" +
                        "Time: " + LocalDateTime.now() + "\n" +
                        "Instance: " + instance.name + "\n" +
                        "Version: " + version + "\n" +
                        "Player: " + session.username + " (" + session.userType + ")\n" +
                        "Java required: " + requiredJava + ", using: " + java + "\n" +
                        "Java path: " + javaExe + "\n\n" +
                        "Command:\n" + String.join(" ", shown) +
                        "\n\n--- game output ---\n";

        Files.writeString(logFile, header, StandardCharsets.UTF_8);

        if (logConsumer != null) {
            logConsumer.accept(header.trim());
        }

        // START + LIVE STREAM OUTPUT TO FILE AND CONSOLE

        Files.createDirectories(gameFolder);

        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(gameFolder.toFile())
                .redirectErrorStream(true);

        Process process = builder.start();

        Thread logPump = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = Files.newBufferedWriter(
                         logFile,
                         StandardCharsets.UTF_8,
                         StandardOpenOption.CREATE,
                         StandardOpenOption.APPEND)) {

                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                    if (logConsumer != null) {
                        logConsumer.accept(line);
                    }
                }
            } catch (Exception ignored) {
            }
        }, "JJN-Log-Pump");
        logPump.setDaemon(true);
        logPump.start();

        return new LaunchResult(process, logFile, java.toString());
    }

    // VERSION INHERITANCE

    private List<JsonObject> loadVersionChain(String versionId, Path gameFolder, Path sharedFolder)
            throws Exception {

        List<JsonObject> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String current = versionId;

        while (current != null && !current.isBlank()) {
            if (!visited.add(current)) {
                throw new Exception("Circular inheritsFrom in version files: " + current);
            }

            Path versionJsonFile = findExistingFile(
                    gameFolder.resolve("versions").resolve(current).resolve(current + ".json"),
                    sharedFolder.resolve("versions").resolve(current).resolve(current + ".json")
            );

            if (versionJsonFile == null) {
                throw new Exception(
                        "Minecraft version \"" + current + "\" is not installed. Click Install first."
                );
            }

            JsonObject obj = JsonParser
                    .parseString(Files.readString(versionJsonFile))
                    .getAsJsonObject();

            if (!obj.has("id")) {
                obj.addProperty("id", current);
            }

            chain.add(obj);

            current = obj.has("inheritsFrom") && !obj.get("inheritsFrom").isJsonNull()
                    ? obj.get("inheritsFrom").getAsString()
                    : null;
        }

        return chain;
    }

    private Path findClientJar(List<JsonObject> chain, Path gameFolder, Path sharedFolder) {
        // Walk from root parent -> child or find whichever version defines downloads.client / has the jar
        List<JsonObject> parentFirst = new ArrayList<>(chain);
        Collections.reverse(parentFirst);

        for (JsonObject json : parentFirst) {
            if (!json.has("id")) {
                continue;
            }
            String id = json.get("id").getAsString();
            Path jar = findExistingFile(
                    gameFolder.resolve("versions").resolve(id).resolve(id + ".jar"),
                    sharedFolder.resolve("versions").resolve(id).resolve(id + ".jar")
            );
            if (jar != null) {
                return jar;
            }
        }
        return null;
    }

    private String resolveLibraryPath(JsonObject library) {
        if (library.has("downloads")) {
            JsonObject downloads = library.getAsJsonObject("downloads");
            if (downloads.has("artifact")) {
                JsonObject artifact = downloads.getAsJsonObject("artifact");
                if (artifact.has("path")) {
                    return artifact.get("path").getAsString();
                }
            }
            return null;
        }

        if (library.has("name")) {
            return FabricInstallService.mavenCoordinateToPath(library.get("name").getAsString());
        }

        return null;
    }

    private String artifactKey(JsonObject library, String fallbackPath) {
        if (library.has("name")) {
            String[] parts = library.get("name").getAsString().split(":");
            if (parts.length >= 2) {
                String classifier = parts.length >= 4 ? ":" + parts[3] : "";
                return parts[0] + ":" + parts[1] + classifier;
            }
        }
        return fallbackPath;
    }

    private Path findExistingFile(Path first, Path second) {
        if (first != null && Files.isRegularFile(first)) {
            return first;
        }
        if (second != null && Files.isRegularFile(second)) {
            return second;
        }
        return null;
    }

    private String findStringInChain(List<JsonObject> chain, String key) {
        for (JsonObject obj : chain) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsString();
            }
        }
        return null;
    }

    private JsonObject findObjectInChain(List<JsonObject> chain, String key) {
        for (JsonObject obj : chain) {
            if (obj.has(key) && obj.get(key).isJsonObject()) {
                return obj.getAsJsonObject(key);
            }
        }
        return null;
    }

    private int findJavaMajorInChain(List<JsonObject> chain) {
        for (JsonObject obj : chain) {
            if (obj.has("javaVersion") && obj.get("javaVersion").isJsonObject()) {
                JsonObject jv = obj.getAsJsonObject("javaVersion");
                if (jv.has("majorVersion")) {
                    return jv.get("majorVersion").getAsInt();
                }
            }
        }
        return 8;
    }

    private String resolveAssetIndexId(List<JsonObject> chain) {
        for (JsonObject obj : chain) {
            if (obj.has("assetIndex") && obj.get("assetIndex").isJsonObject()) {
                return obj.getAsJsonObject("assetIndex").get("id").getAsString();
            }
            if (obj.has("assets") && !obj.get("assets").isJsonNull()) {
                return obj.get("assets").getAsString();
            }
        }
        return "legacy";
    }

    // JAVA CHOICE

    private JavaDetector.JavaInstall chooseJava(LauncherSettings settings,
                                                int required,
                                                String version,
                                                StatusListener statusListener) throws Exception {

        if (settings.javaPath != null && !settings.javaPath.isBlank()) {

            JavaDetector.JavaInstall chosen =
                    JavaDetector.inspect(Paths.get(settings.javaPath));

            if (chosen == null) {
                throw new Exception(
                        "The Java selected in Settings was not found:\n" +
                                settings.javaPath +
                                "\nChoose another one, or pick Automatic."
                );
            }

            if (chosen.major() > 0 && chosen.major() < required) {
                throw new Exception(
                        "Minecraft " + version + " needs Java " + required +
                                " or newer, but Settings uses Java " +
                                chosen.major() + ".\nChange it in Settings " +
                                "(Automatic is recommended)."
                );
            }

            return chosen;
        }

        List<JavaDetector.JavaInstall> all = JavaDetector.detect();

        JavaDetector.JavaInstall best = null;

        for (JavaDetector.JavaInstall install : all) {
            if (install.major() == required) {
                return install;
            }
            if (install.major() > required
                    && (best == null || install.major() < best.major())) {
                best = install;
            }
        }

        if (best != null) {
            return best;
        }

        // Auto-provision Adoptium JRE if missing!
        return javaProvisionService.ensureJava(required, (msg, prog) -> {
            if (statusListener != null) {
                statusListener.onStatus(msg);
            }
        });
    }

    private Path preferJavaw(Path javaExe) {
        Path javaw = javaExe.resolveSibling("javaw.exe");
        return Files.isRegularFile(javaw) ? javaw : javaExe;
    }

    // ARGUMENTS + RULES

    private void addArguments(List<String> out,
                              JsonArray array,
                              Map<String, String> vars,
                              Map<String, Boolean> features) {

        for (JsonElement element : array) {

            if (element.isJsonPrimitive()) {
                out.add(substitute(element.getAsString(), vars));
                continue;
            }

            JsonObject object = element.getAsJsonObject();

            if (object.has("rules")
                    && !rulesAllow(object.getAsJsonArray("rules"), features)) {
                continue;
            }

            if (!object.has("value")) {
                continue;
            }

            JsonElement value = object.get("value");

            if (value.isJsonArray()) {
                for (JsonElement item : value.getAsJsonArray()) {
                    out.add(substitute(item.getAsString(), vars));
                }
            } else {
                out.add(substitute(value.getAsString(), vars));
            }
        }
    }

    private String substitute(String text, Map<String, String> vars) {
        String result = text;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private boolean rulesAllow(JsonArray rules, Map<String, Boolean> features) {
        if (rules == null) {
            return true;
        }

        boolean allowed = false;
        for (JsonElement element : rules) {
            JsonObject rule = element.getAsJsonObject();
            if (!ruleApplies(rule, features)) {
                continue;
            }
            allowed = "allow".equals(rule.get("action").getAsString());
        }
        return allowed;
    }

    private boolean ruleApplies(JsonObject rule, Map<String, Boolean> features) {
        if (rule.has("os") && !matchesOs(rule.getAsJsonObject("os"))) {
            return false;
        }

        if (rule.has("features")) {
            for (Map.Entry<String, JsonElement> entry :
                    rule.getAsJsonObject("features").entrySet()) {

                boolean required = entry.getValue().getAsBoolean();
                boolean enabled = features.getOrDefault(entry.getKey(), false);
                if (required != enabled) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean matchesOs(JsonObject os) {
        if (os.has("name") && !"windows".equals(os.get("name").getAsString())) {
            return false;
        }

        if (os.has("arch")) {
            String arch = os.get("arch").getAsString();
            if (!Pattern.compile(arch).matcher(
                    System.getProperty("os.arch")).find()) {
                return false;
            }
        }

        if (os.has("version")) {
            String regex = os.get("version").getAsString();
            if (!Pattern.compile(regex).matcher(
                    System.getProperty("os.version")).find()) {
                return false;
            }
        }

        return true;
    }

    private static List<String> splitArgs(String raw) {
        List<String> list = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return list;
        }
        Matcher m = Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(raw);
        while (m.find()) {
            if (m.group(1) != null) {
                list.add(m.group(1));
            } else {
                list.add(m.group(2));
            }
        }
        return list;
    }
}