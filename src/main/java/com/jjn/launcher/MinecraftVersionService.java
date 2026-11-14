
package com.jjn.launcher;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public class MinecraftVersionService {

    private static final String URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    public static List<String> getReleaseVersions() throws Exception {

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new Exception("Mojang returned HTTP " + response.statusCode());
        }

        JsonObject manifest = JsonParser
                .parseString(response.body())
                .getAsJsonObject();

        JsonArray versions = manifest.getAsJsonArray("versions");

        List<String> releases = new ArrayList<>();

        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();

            if (version.get("type").getAsString().equals("release")) {
                releases.add(version.get("id").getAsString());
            }
        }

        return releases;
    }

    public static String getLatestRelease() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        JsonObject manifest = JsonParser
                .parseString(response.body())
                .getAsJsonObject();

        return manifest.getAsJsonObject("latest")
                .get("release").getAsString();
    }
}
