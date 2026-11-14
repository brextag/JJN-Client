
package com.jjn.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class MicrosoftLoginTest extends Application {

    private static final String CLIENT_ID =
            "f71019fc-5610-46c7-b41a-7f3817663333";

    private static final String DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";

    private static final String TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";

    private static final String XBOX_AUTH_URL =
            "https://user.auth.xboxlive.com/user/authenticate";

    private static final String XSTS_URL =
            "https://xsts.auth.xboxlive.com/xsts/authorize";

    private static final String MINECRAFT_LOGIN_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox";

    private static final String ENTITLEMENTS_URL =
            "https://api.minecraftservices.com/entitlements/mcstore";

    private static final String PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private Label codeLabel;
    private Label statusLabel;
    private Label profileLabel;
    private Button loginButton;
    private Button browserButton;

    private String verificationUri;

    @Override
    public void start(Stage stage) {

        Label title = new Label("JJN CLIENT");
        title.setFont(Font.font("Arial", 28));
        title.setStyle(
                "-fx-text-fill: white; -fx-font-weight: bold;"
        );

        Label subtitle = new Label("Minecraft Account Login");
        subtitle.setStyle("-fx-text-fill: #aaaaaa;");

        codeLabel = new Label("Ready to connect");
        codeLabel.setFont(Font.font("Arial", 22));
        codeLabel.setStyle(
                "-fx-text-fill: white; -fx-font-weight: bold;"
        );
        codeLabel.setWrapText(true);

        statusLabel = new Label("Sign in with your Microsoft account.");
        statusLabel.setStyle("-fx-text-fill: #aaaaaa;");
        statusLabel.setWrapText(true);

        profileLabel = new Label("");
        profileLabel.setStyle(
                "-fx-text-fill: #ffffff; -fx-font-size: 14px;"
        );
        profileLabel.setWrapText(true);

        loginButton = new Button("Login with Microsoft");
        loginButton.setPrefWidth(230);
        loginButton.setStyle(
                "-fx-background-color: white;" +
                        "-fx-text-fill: #111111;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 12;" +
                        "-fx-padding: 13 20;"
        );

        loginButton.setOnAction(event -> startLogin());

        browserButton = new Button("Open Microsoft Login");
        browserButton.setDisable(true);

        browserButton.setOnAction(event -> {
            if (verificationUri != null) {
                getHostServices().showDocument(verificationUri);
            }
        });

        VBox root = new VBox(
                18,
                title,
                subtitle,
                codeLabel,
                loginButton,
                browserButton,
                statusLabel,
                profileLabel
        );

        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(35));
        root.setStyle("-fx-background-color: #151515;");

        Scene scene = new Scene(root, 500, 440);

        stage.setTitle("JJN Client - Account Login");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    private void startLogin() {

        loginButton.setDisable(true);
        browserButton.setDisable(true);

        verificationUri = null;

        codeLabel.setText("Connecting...");
        statusLabel.setText("Starting Microsoft authentication...");
        profileLabel.setText("");

        Task<Void> task = new Task<>() {

            @Override
            protected Void call() throws Exception {

                // STEP 1: Microsoft OAuth

                updateStatus("Requesting Microsoft login code...");

                String microsoftToken = getMicrosoftToken();

                // STEP 2: Xbox Live authentication

                updateStatus("Authenticating with Xbox Live...");

                JsonObject xboxProperties = new JsonObject();
                xboxProperties.addProperty("AuthMethod", "RPS");
                xboxProperties.addProperty(
                        "SiteName",
                        "user.auth.xboxlive.com"
                );
                xboxProperties.addProperty(
                        "RpsTicket",
                        "d=" + microsoftToken
                );

                JsonObject xboxRequest = new JsonObject();
                xboxRequest.add("Properties", xboxProperties);
                xboxRequest.addProperty(
                        "RelyingParty",
                        "http://auth.xboxlive.com"
                );
                xboxRequest.addProperty("TokenType", "JWT");

                JsonObject xboxResponse = postJson(
                        XBOX_AUTH_URL,
                        xboxRequest
                );

                String xboxToken = requiredString(
                        xboxResponse,
                        "Token",
                        "Xbox Live"
                );

                String userHash = xboxResponse
                        .getAsJsonObject("DisplayClaims")
                        .getAsJsonArray("xui")
                        .get(0)
                        .getAsJsonObject()
                        .get("uhs")
                        .getAsString();

                // STEP 3: Xbox XSTS authentication

                updateStatus("Requesting Xbox security token...");

                JsonArray userTokens = new JsonArray();
                userTokens.add(xboxToken);

                JsonObject xstsProperties = new JsonObject();
                xstsProperties.addProperty(
                        "SandboxId",
                        "RETAIL"
                );
                xstsProperties.add("UserTokens", userTokens);

                JsonObject xstsRequest = new JsonObject();
                xstsRequest.add("Properties", xstsProperties);
                xstsRequest.addProperty(
                        "RelyingParty",
                        "rp://api.minecraftservices.com/"
                );
                xstsRequest.addProperty("TokenType", "JWT");

                JsonObject xstsResponse = postJson(
                        XSTS_URL,
                        xstsRequest
                );

                String xstsToken = requiredString(
                        xstsResponse,
                        "Token",
                        "Xbox XSTS"
                );

                // STEP 4: Minecraft authentication

                updateStatus("Connecting to Minecraft Services...");

                JsonObject minecraftRequest = new JsonObject();

                minecraftRequest.addProperty(
                        "identityToken",
                        "XBL3.0 x=" + userHash + ";" + xstsToken
                );

                JsonObject minecraftResponse = postJson(
                        MINECRAFT_LOGIN_URL,
                        minecraftRequest
                );

                String minecraftToken = requiredString(
                        minecraftResponse,
                        "access_token",
                        "Minecraft"
                );

                // STEP 5: Check Minecraft ownership

                updateStatus("Checking Minecraft ownership...");

                JsonObject entitlements = getJson(
                        ENTITLEMENTS_URL,
                        minecraftToken
                );

                boolean ownsMinecraft = false;

                if (entitlements.has("items")
                        && entitlements.get("items").isJsonArray()) {

                    JsonArray items = entitlements
                            .getAsJsonArray("items");

                    for (int i = 0; i < items.size(); i++) {

                        JsonObject item = items.get(i).getAsJsonObject();

                        if (!item.has("name")) {
                            continue;
                        }

                        String name = item.get("name").getAsString();

                        if (name.equals("game_minecraft")
                                || name.equals("product_minecraft")) {

                            ownsMinecraft = true;
                            break;
                        }
                    }
                }

                if (!ownsMinecraft) {
                    throw new Exception(
                            "This Microsoft account does not appear " +
                                    "to have Minecraft Java Edition ownership.\n" +
                                    "Check your Minecraft purchase or Game Pass."
                    );
                }

                // STEP 6: Retrieve Minecraft profile

                updateStatus("Retrieving Minecraft profile...");

                JsonObject profile = getJson(
                        PROFILE_URL,
                        minecraftToken
                );

                String playerName = requiredString(
                        profile,
                        "name",
                        "Minecraft profile"
                );

                String playerId = requiredString(
                        profile,
                        "id",
                        "Minecraft profile"
                );

                String formattedUuid = formatUuid(playerId);

                Platform.runLater(() -> {

                    codeLabel.setText("CONNECTED");

                    statusLabel.setText(
                            "Minecraft authentication successful!"
                    );

                    profileLabel.setText(
                            "Player: " + playerName +
                                    "\nUUID: " + formattedUuid +
                                    "\nMinecraft ownership: Verified"
                    );

                    browserButton.setDisable(true);
                });

                return null;
            }
        };

        task.setOnSucceeded(event -> {
            loginButton.setDisable(false);
        });

        task.setOnFailed(event -> {

            Throwable error = task.getException();

            statusLabel.setText(
                    error == null
                            ? "Authentication failed."
                            : error.getMessage()
            );

            codeLabel.setText("LOGIN FAILED");

            loginButton.setDisable(false);
            browserButton.setDisable(true);
        });

        Thread thread = new Thread(task, "JJN-Minecraft-Login");
        thread.setDaemon(true);
        thread.start();
    }

    private String getMicrosoftToken() throws Exception {

        String deviceResponse = postForm(
                DEVICE_CODE_URL,
                "client_id=" + encode(CLIENT_ID) +
                        "&scope=" + encode(
                        "XboxLive.signin offline_access"
                )
        );

        JsonObject deviceJson = parseObject(deviceResponse);

        checkMicrosoftError(deviceJson);

        String deviceCode = requiredString(
                deviceJson,
                "device_code",
                "Microsoft device-code response"
        );

        String userCode = requiredString(
                deviceJson,
                "user_code",
                "Microsoft device-code response"
        );

        verificationUri = deviceJson.has("verification_uri")
                ? deviceJson.get("verification_uri").getAsString()
                : "https://microsoft.com/devicelogin";

        int expiresIn = deviceJson.get("expires_in").getAsInt();

        int interval = deviceJson.has("interval")
                ? deviceJson.get("interval").getAsInt()
                : 5;

        Platform.runLater(() -> {
            codeLabel.setText(userCode);
            statusLabel.setText(
                    "Enter the code on Microsoft's login page."
            );
            browserButton.setDisable(false);
        });

        long deadline =
                System.currentTimeMillis() + expiresIn * 1000L;

        while (System.currentTimeMillis() < deadline) {

            Thread.sleep(interval * 1000L);

            String response = postForm(
                    TOKEN_URL,
                    "grant_type=" + encode(
                            "urn:ietf:params:oauth:grant-type:device_code"
                    ) +
                            "&client_id=" + encode(CLIENT_ID) +
                            "&device_code=" + encode(deviceCode)
            );

            JsonObject tokenJson = parseObject(response);

            if (tokenJson.has("access_token")) {
                return tokenJson.get("access_token").getAsString();
            }

            String error = tokenJson.has("error")
                    ? tokenJson.get("error").getAsString()
                    : "unknown_error";

            if (error.equals("authorization_pending")) {
                updateStatus("Waiting for Microsoft sign-in...");

            } else if (error.equals("slow_down")) {
                interval += 5;

            } else if (error.equals("authorization_declined")) {
                throw new Exception(
                        "Microsoft sign-in was declined."
                );

            } else if (error.equals("expired_token")) {
                throw new Exception(
                        "Microsoft login code expired. Try again."
                );

            } else {
                String description =
                        tokenJson.has("error_description")
                                ? tokenJson.get("error_description").getAsString()
                                : error;

                throw new Exception(
                        "Microsoft token error: " + description
                );
            }
        }

        throw new Exception("Microsoft login code expired.");
    }

    private void checkMicrosoftError(JsonObject json)
            throws Exception {

        if (json.has("error")) {

            String error = json.get("error").getAsString();

            String description = json.has("error_description")
                    ? json.get("error_description").getAsString()
                    : error;

            throw new Exception(
                    "Microsoft rejected the request: " +
                            error + "\n" + description
            );
        }
    }

    private JsonObject postJson(
            String url,
            JsonObject body
    ) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                body.toString()
                        )
                )
                .build();

        HttpResponse<String> response = http.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            throw new Exception(
                    "Authentication service returned HTTP " +
                            response.statusCode() + ":\n" +
                            response.body()
            );
        }

        return parseObject(response.body());
    }

    private JsonObject getJson(
            String url,
            String accessToken
    ) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header(
                        "Authorization",
                        "Bearer " + accessToken
                )
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = http.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() == 404
                && url.equals(PROFILE_URL)) {

            throw new Exception(
                    "Minecraft account has no Java profile yet. " +
                            "Create a profile at minecraft.net first."
            );
        }

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            throw new Exception(
                    "Minecraft service returned HTTP " +
                            response.statusCode() + ":\n" +
                            response.body()
            );
        }

        return parseObject(response.body());
    }

    private String postForm(String url, String form)
            throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header(
                        "Content-Type",
                        "application/x-www-form-urlencoded"
                )
                .POST(
                        HttpRequest.BodyPublishers.ofString(form)
                )
                .build();

        HttpResponse<String> response = http.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        return response.body();
    }

    private JsonObject parseObject(String response)
            throws Exception {

        try {
            return JsonParser.parseString(response).getAsJsonObject();
        } catch (Exception e) {
            throw new Exception(
                    "Unexpected server response:\n" + response
            );
        }
    }

    private String requiredString(
            JsonObject json,
            String key,
            String source
    ) throws Exception {

        if (!json.has(key) || json.get(key).isJsonNull()) {
            throw new Exception(
                    source + " did not return the required field: " + key
            );
        }

        return json.get(key).getAsString();
    }

    private String formatUuid(String uuid) {

        if (uuid.length() != 32) {
            return uuid;
        }

        return uuid.substring(0, 8) + "-" +
                uuid.substring(8, 12) + "-" +
                uuid.substring(12, 16) + "-" +
                uuid.substring(16, 20) + "-" +
                uuid.substring(20);
    }

    private String encode(String value) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }

    private void updateStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
