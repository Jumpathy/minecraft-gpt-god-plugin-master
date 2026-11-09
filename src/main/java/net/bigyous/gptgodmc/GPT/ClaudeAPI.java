package net.bigyous.gptgodmc.GPT;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.utils.DebugWebhook;
import net.bigyous.gptgodmc.utils.TokenTracker;
import net.bigyous.gptgodmc.GPT.Json.ClaudeContentBlock;
import net.bigyous.gptgodmc.GPT.Json.ClaudeMessage;
import net.bigyous.gptgodmc.GPT.Json.ClaudeRequest;
import net.bigyous.gptgodmc.GPT.Json.ClaudeResponse;
import net.bigyous.gptgodmc.GPT.Json.ClaudeTool;
import net.bigyous.gptgodmc.GPT.Json.GptFunction;
import net.bigyous.gptgodmc.GPT.Json.GptFunctionReference;
import net.bigyous.gptgodmc.GPT.Json.GptModel;
import net.bigyous.gptgodmc.GPT.Json.GptTool;

public class ClaudeAPI implements IGptAPI {
    private static final String CLAUDE_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static ExecutorService pool = Executors.newCachedThreadPool();

    private LinkedHashMap<String, String> systemMessages = new LinkedHashMap<>();
    private LinkedHashMap<String, String> userMessages = new LinkedHashMap<>();
    private GptTool[] tools;
    private Object toolChoice = "auto";
    private GptModel model;
    private boolean isSending = false;
    private int maxOutputTokens;
    private Gson gson = new Gson();

    public ClaudeAPI(GptModel model, GptTool[] tools) {
        this.model = model;
        this.tools = tools;
        this.maxOutputTokens = JavaPlugin.getPlugin(GPTGOD.class).getConfig().getInt("claude-max-output-tokens", 4096);
    }

    @Override
    public IGptAPI addContext(String context, String name) {
        systemMessages.put(name, context);
        return this;
    }

    @Override
    public IGptAPI addContext(String context, String name, int index) {
        // For Claude, system is a single concatenated string; put updates in-place if key exists
        systemMessages.put(name, context);
        return this;
    }

    @Override
    public IGptAPI addLogs(String logs, String name) {
        userMessages.put(name, logs);
        return this;
    }

    @Override
    public IGptAPI addLogs(String logs, String name, int index) {
        if (userMessages.containsKey(name)) {
            // Update in-place, preserving insertion order
            userMessages.put(name, logs);
            return this;
        }
        // New key with explicit index: insert before existing entries so it appears first
        LinkedHashMap<String, String> newMap = new LinkedHashMap<>();
        newMap.put(name, logs);
        newMap.putAll(userMessages);
        userMessages = newMap;
        return this;
    }

    @Override
    public IGptAPI setTools(GptTool[] tools) {
        this.tools = tools;
        return this;
    }

    @Override
    public IGptAPI setToolChoice(Object toolChoice) {
        this.toolChoice = toolChoice;
        return this;
    }

    @Override
    public void removeLastMessage() {
        if (!userMessages.isEmpty()) {
            String lastKey = null;
            for (String key : userMessages.keySet()) {
                lastKey = key;
            }
            userMessages.remove(lastKey);
        }
    }

    @Override
    public int getMaxTokens() {
        return model.getTokenLimit();
    }

    @Override
    public String getModelName() {
        return model.getName();
    }

    @Override
    public boolean isSending() {
        return isSending;
    }

    @Override
    public void checkRequestBody() {
        ClaudeRequest request = buildRequest();
        GPTGOD.LOGGER.info("POSTING " + gson.toJson(request));
    }

    private ClaudeRequest buildRequest() {
        ClaudeRequest request = new ClaudeRequest();
        request.model = model.getName();
        request.max_tokens = maxOutputTokens;

        String systemText = String.join("\n\n", systemMessages.values());
        request.system = systemText.isEmpty() ? null : systemText;

        String userContent = String.join("\n\n", userMessages.values());
        request.messages = List.of(new ClaudeMessage("user", userContent));

        if (tools != null && tools.length > 0) {
            request.tools = Arrays.stream(tools)
                    .map(t -> ClaudeTool.fromGptFunction(t.getFunction()))
                    .toArray(ClaudeTool[]::new);
            request.tool_choice = convertToolChoice(toolChoice);
        }

        return request;
    }

    private Object convertToolChoice(Object choice) {
        if (choice instanceof GptFunctionReference) {
            JsonObject serialized = JsonParser.parseString(gson.toJson(choice)).getAsJsonObject();
            String name = serialized.getAsJsonObject("function").get("name").getAsString();
            return Map.of("type", "tool", "name", name);
        }
        return Map.of("type", "auto");
    }

    @Override
    public void send() {
        CloseableHttpClient client = HttpClientBuilder.create().build();
        pool.execute(() -> {
            this.isSending = true;
            String claudeApiKey = JavaPlugin.getPlugin(GPTGOD.class).getConfig().getString("claudeApiKey");
            ClaudeRequest request = buildRequest();
            String requestJson = gson.toJson(request);
            String requestId = UUID.randomUUID().toString();
            String modelName = model.getName();
            GPTGOD.LOGGER.info("POSTING to Claude: " + requestJson);
            DebugWebhook.send("request", "claude", modelName, requestId, requestJson);
            StringEntity data = new StringEntity(requestJson, ContentType.APPLICATION_JSON);
            HttpPost post = new HttpPost(CLAUDE_URL);
            post.setHeader("x-api-key", claudeApiKey);
            post.setHeader("anthropic-version", ANTHROPIC_VERSION);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(data);
            try {
                HttpResponse response = client.execute(post);
                String out = new String(response.getEntity().getContent().readAllBytes());
                EntityUtils.consume(response.getEntity());
                GPTGOD.LOGGER.info("received response from Claude: " + out);
                DebugWebhook.send("response", "claude", modelName, requestId, out);
                if (response.getStatusLine().getStatusCode() != 200) {
                    GPTGOD.LOGGER.warn("Claude API call failed: " + out);
                    this.isSending = false;
                    client.close();
                    return;
                }
                ClaudeResponse claudeResponse = gson.fromJson(out, ClaudeResponse.class);
                if (claudeResponse.usage != null) {
                    TokenTracker.recordUsage(claudeResponse.usage.input_tokens, claudeResponse.usage.output_tokens);
                }
                if (claudeResponse.content != null) {
                    for (ClaudeContentBlock block : claudeResponse.content) {
                        if ("tool_use".equals(block.type)) {
                            GptActions.run(block.name, gson.toJson(block.input));
                        }
                    }
                }
                client.close();
                Bukkit.getScheduler().runTaskLater(JavaPlugin.getPlugin(GPTGOD.class), () -> {
                    this.isSending = false;
                }, 10);
            } catch (IOException e) {
                GPTGOD.LOGGER.error("There was an error making a request to Claude", e);
                this.isSending = false;
            }
        });
    }

    @Override
    public void send(Map<String, GptFunction> functions) {
        CloseableHttpClient client = HttpClientBuilder.create().build();
        pool.execute(() -> {
            this.isSending = true;
            String claudeApiKey = JavaPlugin.getPlugin(GPTGOD.class).getConfig().getString("claudeApiKey");
            ClaudeRequest request = buildRequest();
            String requestJson = gson.toJson(request);
            String requestId = UUID.randomUUID().toString();
            String modelName = model.getName();
            GPTGOD.LOGGER.info("POSTING to Claude: " + requestJson);
            DebugWebhook.send("request", "claude", modelName, requestId, requestJson);
            StringEntity data = new StringEntity(requestJson, ContentType.APPLICATION_JSON);
            HttpPost post = new HttpPost(CLAUDE_URL);
            post.setHeader("x-api-key", claudeApiKey);
            post.setHeader("anthropic-version", ANTHROPIC_VERSION);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(data);
            try {
                HttpResponse response = client.execute(post);
                String out = new String(response.getEntity().getContent().readAllBytes());
                EntityUtils.consume(response.getEntity());
                GPTGOD.LOGGER.info("received response from Claude: " + out);
                DebugWebhook.send("response", "claude", modelName, requestId, out);
                if (response.getStatusLine().getStatusCode() != 200) {
                    GPTGOD.LOGGER.warn("Claude API call failed: " + out);
                    this.isSending = false;
                    client.close();
                    return;
                }
                ClaudeResponse claudeResponse = gson.fromJson(out, ClaudeResponse.class);
                if (claudeResponse.usage != null) {
                    TokenTracker.recordUsage(claudeResponse.usage.input_tokens, claudeResponse.usage.output_tokens);
                }
                if (claudeResponse.content != null) {
                    for (ClaudeContentBlock block : claudeResponse.content) {
                        if ("tool_use".equals(block.type)) {
                            String name = block.name;
                            String jsonArgs = gson.toJson(block.input);
                            Bukkit.getScheduler().runTask(JavaPlugin.getPlugin(GPTGOD.class), () -> {
                                functions.get(name).runFunction(jsonArgs);
                            });
                        }
                    }
                }
                client.close();
                Bukkit.getScheduler().runTaskLater(JavaPlugin.getPlugin(GPTGOD.class), () -> {
                    this.isSending = false;
                }, 20);
            } catch (IOException e) {
                GPTGOD.LOGGER.error("There was an error making a request to Claude", e);
                this.isSending = false;
            }
        });
    }
}
