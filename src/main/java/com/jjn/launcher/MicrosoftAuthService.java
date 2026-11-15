package com.jjn.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Microsoft device-code login -> Xbox Live -> XSTS -> Minecraft Services.
 * Same flow and endpoints as MicrosoftLoginTest.java, without any JavaFX,
 * so it can be called from the main launcher.
 *
 * Tokens are only kept in memory. Nothing is written to disk.
 */
public class MicrosoftAuthService {

    private static final String CLIENT_ID =
            "d0f0614c-aa7a-45b4-8d55-c04c6cf2d59e";

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

    /** Callbacks come from the background thread. Use Platform.runLater in the UI. */
    public interface Listener {
        void status(String message);

        void deviceCode(String userCode, String verificationUri);
    }

    public static class SessionExpiredException extends Exception {
        public SessionExpiredException(String message) {
            super(message);
        }
    }

    public static class AuthResult {

        /** True when Microsoft, Xbox Live and XSTS all worked. */
        public boolean xboxAuthenticated;

        /** True when Minecraft Services rejected the app registration (HTTP 403). */
        public boolean minecraftBlocked;

        /** Only filled in when Minecraft Services accepted the login. */
        public String playerName;
        public String playerUuid;
        public String minecraftAccessToken;

        /** Microsoft OAuth refresh token used to silently restore sessions. */
        public String refreshToken;

        /** URL of the player's active skin texture, or null if none. */
        public String skinUrl;
    }

    private record MicrosoftTokens(String accessToken, String refreshToken) {}

    // SIGN IN

    public AuthResult signIn(Listener listener, BooleanSupplier cancelled)
            throws Exception {

        listener.status("Requesting Microsoft login code...");
        MicrosoftTokens tokens = getMicrosoftTokens(listener, cancelled);
        return authenticateWithMicrosoftToken(tokens.accessToken(), tokens.refreshToken(), listener);
    }

    /**
     * Silently exchanges a saved Microsoft refresh token for fresh Xbox and
     * Minecraft access tokens without requiring the user to sign in again.
     */
    public AuthResult refreshSignIn(String refreshToken, Listener listener)
            throws Exception {

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new SessionExpiredException("No saved refresh token.");
        }

        if (listener != null) {
            listener.status("Refreshing Microsoft session...");
        }

        String response = postForm(
                TOKEN_URL,
                "grant_type=" + encode("refresh_token") +
                        "&client_id=" + encode(CLIENT_ID) +
                        "&refresh_token=" + encode(refreshToken) +
                        "&scope=" + encode("XboxLive.signin offline_access")
        );

        JsonObject tokenJson = parseObject(response);

        if (tokenJson.has("error")) {
            String error = tokenJson.get("error").getAsString();
            String description = tokenJson.has("error_description")
                    ? tokenJson.get("error_description").getAsString()
                    : error;

            if ("invalid_grant".equals(error) || "interaction_required".equals(error)) {
                throw new SessionExpiredException(
                        "Your Microsoft session expired. Please sign in again."
                );
            }
            throw new Exception("Microsoft refresh error: " + description);
        }

        String accessToken = requiredString(
                tokenJson, "access_token", "Microsoft token refresh");

        String nextRefreshToken = tokenJson.has("refresh_token")
                && !tokenJson.get("refresh_token").isJsonNull()
                ? tokenJson.get("refresh_token").getAsString()
                : refreshToken;

        return authenticateWithMicrosoftToken(accessToken, nextRefreshToken, listener);
    }

    private AuthResult authenticateWithMicrosoftToken(
            String microsoftToken,
            String refreshToken,
            Listener listener
    ) throws Exception {

        AuthResult result = new AuthResult();
        result.refreshToken = refreshToken;

        // STEP 2: Xbox Live

        if (listener != null) {
            listener.status("Authenticating with Xbox Live...");
        }

        JsonObject xboxProperties = new JsonObject();
        xboxProperties.addProperty("AuthMethod", "RPS");
        xboxProperties.addProperty("SiteName", "user.auth.xboxlive.com");
        xboxProperties.addProperty("RpsTicket", "d=" + microsoftToken);

        JsonObject xboxRequest = new JsonObject();
        xboxRequest.add("Properties", xboxProperties);
        xboxRequest.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xboxRequest.addProperty("TokenType", "JWT");

        JsonObject xboxResponse = postJson(XBOX_AUTH_URL, xboxRequest);

        String xboxToken = requiredString(xboxResponse, "Token", "Xbox Live");

        String userHash = xboxResponse
                .getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui")
                .get(0)
                .getAsJsonObject()
                .get("uhs")
                .getAsString();

        // STEP 3: XSTS

        if (listener != null) {
            listener.status("Requesting Xbox security token...");
        }

        JsonArray userTokens = new JsonArray();
        userTokens.add(xboxToken);

        JsonObject xstsProperties = new JsonObject();
        xstsProperties.addProperty("SandboxId", "RETAIL");
        xstsProperties.add("UserTokens", userTokens);

        JsonObject xstsRequest = new JsonObject();
        xstsRequest.add("Properties", xstsProperties);
        xstsRequest.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsRequest.addProperty("TokenType", "JWT");

        JsonObject xstsResponse = postJson(XSTS_URL, xstsRequest);

        String xstsToken = requiredString(xstsResponse, "Token", "Xbox XSTS");

        result.xboxAuthenticated = true;

        // STEP 4: Minecraft Services login

        if (listener != null) {
            listener.status("Connecting to Minecraft Services...");
        }

        JsonObject minecraftRequest = new JsonObject();
        minecraftRequest.addProperty(
                "identityToken",
                "XBL3.0 x=" + userHash + ";" + xstsToken
        );

        HttpResult minecraft = post(MINECRAFT_LOGIN_URL, minecraftRequest);

        if (minecraft.status() == 403
                && minecraft.body().contains("Invalid app registration")) {

            // Expected until the AppID approval request is granted.
            result.minecraftBlocked = true;
            return result;
        }

        if (minecraft.status() < 200 || minecraft.status() >= 300) {
            throw new Exception(
                    "Minecraft Services returned HTTP " +
                            minecraft.status() + ":\n" + minecraft.body()
            );
        }

        String minecraftToken = requiredString(
                parseObject(minecraft.body()),
                "access_token",
                "Minecraft"
        );

        // STEP 5: Ownership

        if (listener != null) {
            listener.status("Checking Minecraft ownership...");
        }

        JsonObject entitlements = getJson(ENTITLEMENTS_URL, minecraftToken);

        boolean ownsMinecraft = false;

        if (entitlements.has("items")
                && entitlements.get("items").isJsonArray()) {

            JsonArray items = entitlements.getAsJsonArray("items");

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

        // STEP 6: Profile

        if (listener != null) {
            listener.status("Retrieving Minecraft profile...");
        }

        JsonObject profile = getJson(PROFILE_URL, minecraftToken);

        result.playerName = requiredString(profile, "name", "Minecraft profile");
        result.playerUuid = formatUuid(
                requiredString(profile, "id", "Minecraft profile")
        );
        result.minecraftAccessToken = minecraftToken;

        // Active skin (used to show the player's head in the launcher)
        if (profile.has("skins") && profile.get("skins").isJsonArray()) {

            for (var element : profile.getAsJsonArray("skins")) {

                JsonObject skin = element.getAsJsonObject();

                boolean active = skin.has("state")
                        && "ACTIVE".equals(skin.get("state").getAsString());

                if (active && skin.has("url")) {
                    result.skinUrl = skin.get("url").getAsString()
                            .replace("http://", "https://");
                    break;
                }
            }
        }

        return result;
    }

    // MICROSOFT DEVICE CODE

    private MicrosoftTokens getMicrosoftTokens(Listener listener, BooleanSupplier cancelled)
            throws Exception {

        String deviceResponse = postForm(
                DEVICE_CODE_URL,
                "client_id=" + encode(CLIENT_ID) +
                        "&scope=" + encode("XboxLive.signin offline_access")
        );

        JsonObject deviceJson = parseObject(deviceResponse);

        checkMicrosoftError(deviceJson);

        String deviceCode = requiredString(
                deviceJson, "device_code", "Microsoft device-code response");

        String userCode = requiredString(
                deviceJson, "user_code", "Microsoft device-code response");

        String verificationUri = deviceJson.has("verification_uri")
                ? deviceJson.get("verification_uri").getAsString()
                : "https://microsoft.com/devicelogin";

        int expiresIn = deviceJson.get("expires_in").getAsInt();

        int interval = deviceJson.has("interval")
                ? deviceJson.get("interval").getAsInt()
                : 5;

        listener.deviceCode(userCode, verificationUri);
        listener.status("Enter the code on Microsoft's login page.");

        long deadline = System.currentTimeMillis() + expiresIn * 1000L;

        while (System.currentTimeMillis() < deadline) {

            // Sleep in 1-second steps so cancelling is quick.
            for (int i = 0; i < interval; i++) {
                if (cancelled.getAsBoolean()) {
                    throw new Exception("Sign-in cancelled.");
                }
                Thread.sleep(1000);
            }

            String response = postForm(
                    TOKEN_URL,
                    "grant_type=" + encode(
                            "urn:ietf:params:oauth:grant-type:device_code") +
                            "&client_id=" + encode(CLIENT_ID) +
                            "&device_code=" + encode(deviceCode)
            );

            JsonObject tokenJson = parseObject(response);

            if (tokenJson.has("access_token")) {
                String accessToken = tokenJson.get("access_token").getAsString();
                String refreshToken = tokenJson.has("refresh_token")
                        && !tokenJson.get("refresh_token").isJsonNull()
                        ? tokenJson.get("refresh_token").getAsString()
                        : null;
                return new MicrosoftTokens(accessToken, refreshToken);
            }

            String error = tokenJson.has("error")
                    ? tokenJson.get("error").getAsString()
                    : "unknown_error";

            if (error.equals("authorization_pending")) {
                listener.status("Waiting for Microsoft sign-in...");

            } else if (error.equals("slow_down")) {
                interval += 5;

            } else if (error.equals("authorization_declined")) {
                throw new Exception("Microsoft sign-in was declined.");

            } else if (error.equals("expired_token")) {
                throw new Exception("Microsoft login code expired. Try again.");

            } else {
                String description = tokenJson.has("error_description")
                        ? tokenJson.get("error_description").getAsString()
                        : error;

                throw new Exception("Microsoft token error: " + description);
            }
        }

        throw new Exception("Microsoft login code expired.");
    }

    private void checkMicrosoftError(JsonObject json) throws Exception {

        if (json.has("error")) {

            String error = json.get("error").getAsString();

            String description = json.has("error_description")
                    ? json.get("error_description").getAsString()
                    : error;

            throw new Exception(
                    "Microsoft rejected the request: " + error + "\n" + description
            );
        }
    }

    // HTTP HELPERS

    private record HttpResult(int status, String body) {}

    private HttpResult post(String url, JsonObject body) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = http.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        return new HttpResult(response.statusCode(), response.body());
    }

    private JsonObject postJson(String url, JsonObject body) throws Exception {

        HttpResult response = post(url, body);

        if (response.status() < 200 || response.status() >= 300) {
            throw new Exception(
                    "Authentication service returned HTTP " +
                            response.status() + ":\n" + response.body()
            );
        }

        return parseObject(response.body());
    }

    private JsonObject getJson(String url, String accessToken) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = http.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() == 404 && url.equals(PROFILE_URL)) {
            throw new Exception(
                    "Minecraft account has no Java profile yet. " +
                            "Create a profile at minecraft.net first."
            );
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new Exception(
                    "Minecraft service returned HTTP " +
                            response.statusCode() + ":\n" + response.body()
            );
        }

        return parseObject(response.body());
    }

    private String postForm(String url, String form) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = http.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        return response.body();
    }

    private JsonObject parseObject(String response) throws Exception {

        try {
            return JsonParser.parseString(response).getAsJsonObject();
        } catch (Exception e) {
            throw new Exception("Unexpected server response:\n" + response);
        }
    }

    private String requiredString(JsonObject json, String key, String source)
            throws Exception {

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
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}