package com.dsproject.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public final class HttpJson {
    private static final Gson GSON = new Gson();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    private HttpJson() {
    }

    /**
     * Sends HTTP GET and parses JSON object response into a map.
     * Returns empty map on timeout/network error/non-200 response.
     */
    public static Map<String, Object> getJson(String url, Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new HashMap<>();
            }
            String json = response.body();
            if (json == null || json.isEmpty()) {
                return new HashMap<>();
            }
            Map<String, Object> map = GSON.fromJson(json, MAP_TYPE);
            return map != null ? map : new HashMap<>();
        } catch (IOException | InterruptedException e) {
            return new HashMap<>();
        }
    }

    /**
     * Sends HTTP POST with JSON body and parses JSON response into a map.
     * Returns empty map on timeout/network error/non-200 response.
     */
    public static Map<String, Object> postJson(String url, Map<String, Object> body, Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new HashMap<>();
            }
            String json = response.body();
            if (json == null || json.isEmpty()) {
                return new HashMap<>();
            }
            Map<String, Object> map = GSON.fromJson(json, MAP_TYPE);
            return map != null ? map : new HashMap<>();
        } catch (IOException | InterruptedException e) {
            return new HashMap<>();
        }
    }
}
