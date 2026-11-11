package net.bigyous.gptgodmc.utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * Optional: POSTs every Claude request and response to debug.webhook-url (for the companion
 * debug server). Each pair shares a requestId.
 */
public final class DebugWebhook {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private DebugWebhook() {
    }

    public static void send(String type, String api, String model, String requestId, String bodyJson) {
        String url = GPTGOD.get().getConfig().getString("debug.webhook-url", "");
        if (url == null || url.isBlank()) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("type", type);
        payload.addProperty("api", api);
        payload.addProperty("model", model);
        payload.addProperty("requestId", requestId);
        payload.addProperty("timestamp", Instant.now().toString());
        try {
            JsonElement body = JsonParser.parseString(bodyJson);
            payload.add("body", body);
        } catch (RuntimeException e) {
            payload.addProperty("body", bodyJson);
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload))).build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding()).exceptionally(e -> {
                GPTGOD.LOGGER.debug("Debug webhook failed: " + e.getMessage());
                return null;
            });
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.debug("Debug webhook failed: " + e.getMessage());
        }
    }
}
