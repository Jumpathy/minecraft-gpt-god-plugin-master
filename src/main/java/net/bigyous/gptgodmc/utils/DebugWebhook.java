package net.bigyous.gptgodmc.utils;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.GPTGOD;

public class DebugWebhook {
    private static final ExecutorService pool = Executors.newCachedThreadPool();
    private static final Gson gson = new Gson();

    /**
     * Fire-and-forget POST to the configured debug URL.
     *
     * @param type      "request" or "response"
     * @param api       "openai" or "claude"
     * @param model     model name string
     * @param requestId UUID shared by the matching request/response pair
     * @param bodyJson  raw JSON string of the request or response body
     */
    public static void send(String type, String api, String model, String requestId, String bodyJson) {
        JavaPlugin plugin = JavaPlugin.getPlugin(GPTGOD.class);
        if (!plugin.getConfig().getBoolean("enableDebug", false)) return;
        String url = plugin.getConfig().getString("debugUrl", "");
        if (url == null || url.isBlank()) return;

        pool.execute(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("type", type);
                payload.addProperty("api", api);
                payload.addProperty("model", model);
                payload.addProperty("requestId", requestId);
                payload.addProperty("timestamp", Instant.now().toString());

                // Embed body as a real JSON value so the receiver doesn't need to double-parse
                try {
                    JsonElement body = JsonParser.parseString(bodyJson);
                    payload.add("body", body);
                } catch (Exception e) {
                    payload.addProperty("body", bodyJson);
                }

                CloseableHttpClient client = HttpClientBuilder.create().build();
                HttpPost post = new HttpPost(url);
                post.setHeader("Content-Type", "application/json");
                post.setEntity(new StringEntity(gson.toJson(payload), ContentType.APPLICATION_JSON));
                client.execute(post);
                client.close();
            } catch (IOException e) {
                GPTGOD.LOGGER.warn("Failed to send debug webhook: " + e.getMessage());
            }
        });
    }
}
