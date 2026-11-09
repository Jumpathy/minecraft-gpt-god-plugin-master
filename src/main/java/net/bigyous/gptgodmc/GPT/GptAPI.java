package net.bigyous.gptgodmc.GPT;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.utils.DebugWebhook;
import net.bigyous.gptgodmc.GPT.Json.GptFunction;
import net.bigyous.gptgodmc.GPT.Json.GptModel;
import net.bigyous.gptgodmc.GPT.Json.GptRequest;
import net.bigyous.gptgodmc.GPT.Json.GptTool;
import net.bigyous.gptgodmc.GPT.Json.ModelSerializer;
import net.bigyous.gptgodmc.GPT.Json.ParameterExclusion;

public class GptAPI implements IGptAPI {
    private GsonBuilder gson = new GsonBuilder();
    private GptRequest body;
    private String CHATGPTURL = "https://api.openai.com/v1/chat/completions";
    private Map<String, Integer> messageMap = new HashMap<String, Integer>();
    private boolean isSending = false;
    private static ExecutorService pool = Executors.newCachedThreadPool();

    public GptAPI(GptModel model) {
        this.body = new GptRequest(model, GptActions.GetAllTools());
        gson.registerTypeAdapter(GptModel.class, new ModelSerializer());
        gson.setExclusionStrategies(new ParameterExclusion());
    }

    public GptAPI(GptModel model, GptTool[] customTools) {
        this.body = new GptRequest(model, customTools);
        gson.registerTypeAdapter(GptModel.class, new ModelSerializer());
        gson.setExclusionStrategies(new ParameterExclusion());
    }

    public GptAPI(GptRequest request) {
        this.body = request;
        gson.registerTypeAdapter(GptModel.class, new ModelSerializer());
        gson.setExclusionStrategies(new ParameterExclusion());
    }

    public IGptAPI addContext(String context, String name) {
        if (this.messageMap.containsKey(name)) {
            this.body.replaceMessage(messageMap.get(name), context);
            return this;
        }
        this.body.addMessage("system", context);
        this.messageMap.put(name, this.body.getMessagesSize() - 1);
        return this;
    }

    public IGptAPI addContext(String context, String name, int index) {
        if (this.messageMap.containsKey(name)) {
            this.body.replaceMessage(messageMap.get(name), context);
            return this;
        }
        this.body.addMessage("system", context);
        for (String key : messageMap.keySet()) {
            if (messageMap.get(key) == index) {
                messageMap.replace(key, index + 1);
            }
        }
        this.messageMap.put(name, index);
        return this;
    }

    public IGptAPI setTools(GptTool[] tools) {
        this.body.setTools(tools);
        return this;
    }

    public IGptAPI addLogs(String Logs, String name) {
        if (this.messageMap.containsKey(name)) {
            this.body.replaceMessage(messageMap.get(name), Logs);
            return this;
        }
        this.body.addMessage("user", Logs);
        this.messageMap.put(name, this.body.getMessagesSize() - 1);
        return this;
    }

    public IGptAPI addLogs(String Logs, String name, int index) {
        if(this.body.getMessagesSize() <= index){
            addLogs(Logs, name);
            return this;
        }
        if (this.messageMap.containsKey(name)) {
            this.body.replaceMessage(messageMap.get(name), Logs);
            return this;
        }
        this.body.addMessage("user", Logs, index);
        for (String key : messageMap.keySet()) {
            if (messageMap.get(key) == index) {
                messageMap.replace(key, index + 1);
            }
        }
        this.messageMap.put(name, index);
        return this;
    }

    public IGptAPI setToolChoice(Object tool_choice) {
        this.body.setTool_choice(tool_choice);
        return this;
    }

    public void removeLastMessage() {
        this.body.removeLastMessage();
    }

    public int getMaxTokens() {
        return body.getModel().getTokenLimit();
    }

    public String getModelName() {
        return body.getModel().getName();
    }

    public void send() {
        CloseableHttpClient client = HttpClientBuilder.create().build();
        pool.execute(() -> {
            this.isSending = true;
            FileConfiguration config = JavaPlugin.getPlugin(GPTGOD.class).getConfig();
            String requestJson = gson.create().toJson(body);
            String requestId = UUID.randomUUID().toString();
            String modelName = body.getModel().getName();
            GPTGOD.LOGGER.info("POSTING " + gson.setPrettyPrinting().create().toJson(body));
            DebugWebhook.send("request", "openai", modelName, requestId, requestJson);
            StringEntity data = new StringEntity(requestJson, ContentType.APPLICATION_JSON);
            HttpPost post = new HttpPost(CHATGPTURL);
            post.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getString("openAiKey"));
            GPTGOD.LOGGER.info("Making POST request");
            post.setEntity(data);
            try {
                HttpResponse response = client.execute(post);
                String out = new String(response.getEntity().getContent().readAllBytes());
                EntityUtils.consume(response.getEntity());
                GPTGOD.LOGGER.info("recieved response from OpenAI: " + out);
                DebugWebhook.send("response", "openai", modelName, requestId, out);
                if (response.getStatusLine().getStatusCode() != 200) {
                    GPTGOD.LOGGER.warn("API call failed");
                    this.isSending = false;
                }
                GptActions.processResponse(out);
                client.close();
                // after everything finishes executing, the request is finished
                Bukkit.getScheduler().runTaskLater(JavaPlugin.getPlugin(GPTGOD.class), () -> {
                    this.isSending = false;
                }, 10);
            } catch (IOException e) {
                GPTGOD.LOGGER.error("There was an error making a request to GPT", e);
                this.isSending = false;
            }
        });
    }

    public void send(Map<String, GptFunction> functions) {
        CloseableHttpClient client = HttpClientBuilder.create().build();
        pool.execute(() -> {
            this.isSending = true;
            FileConfiguration config = JavaPlugin.getPlugin(GPTGOD.class).getConfig();
            String requestJson = gson.create().toJson(body);
            String requestId = UUID.randomUUID().toString();
            String modelName = body.getModel().getName();
            GPTGOD.LOGGER.info("POSTING " + gson.setPrettyPrinting().create().toJson(body));
            DebugWebhook.send("request", "openai", modelName, requestId, requestJson);
            StringEntity data = new StringEntity(requestJson, ContentType.APPLICATION_JSON);
            HttpPost post = new HttpPost(CHATGPTURL);
            post.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getString("openAiKey"));
            GPTGOD.LOGGER.info("Making POST request");
            post.setEntity(data);
            try {
                HttpResponse response = client.execute(post);
                String out = new String(response.getEntity().getContent().readAllBytes());
                EntityUtils.consume(response.getEntity());
                GPTGOD.LOGGER.info("recieved response from OpenAI: " + out);
                DebugWebhook.send("response", "openai", modelName, requestId, out);
                if (response.getStatusLine().getStatusCode() != 200) {
                    GPTGOD.LOGGER.warn("API call failed");
                    this.isSending = false;
                }
                GptActions.processResponse(out, functions);
                client.close();
                // after everything finishes, executing the request is finished
                Bukkit.getScheduler().runTaskLater(JavaPlugin.getPlugin(GPTGOD.class), () -> {
                    this.isSending = false;
                }, 20);
            } catch (IOException e) {
                GPTGOD.LOGGER.error("There was an error making a request to GPT", e);
                this.isSending = false;
            }
        });
    }

    public boolean isSending() {
        return isSending;
    }

    // DEBUG method
    public void checkRequestBody() {
        GPTGOD.LOGGER.info("POSTING " + gson.setPrettyPrinting().create().toJson(body));
    }
}
