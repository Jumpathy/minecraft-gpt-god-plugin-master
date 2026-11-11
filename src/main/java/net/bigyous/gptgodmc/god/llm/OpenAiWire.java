package net.bigyous.gptgodmc.god.llm;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Builds the JSON of an OpenAI chat completions request. Pure, so the mapping is unit tested. */
public final class OpenAiWire {
    private static final Gson GSON = new Gson();
    static final String BLIND_NOTE = "\n(An image came with this, but you cannot see images. Go by the words.)";

    private OpenAiWire() {
    }

    /** One finished tool call of a reply. */
    public record Call(String id, String name, String json) {
    }

    public static JsonObject tool(LlmTool t) {
        JsonObject params = new JsonObject();
        params.addProperty("type", "object");
        params.add("properties", GSON.toJsonTree(t.properties()));
        params.add("required", GSON.toJsonTree(t.required()));
        JsonObject fn = new JsonObject();
        fn.addProperty("name", t.name());
        fn.addProperty("description", t.description());
        fn.add("parameters", params);
        JsonObject o = new JsonObject();
        o.addProperty("type", "function");
        o.add("function", fn);
        return o;
    }

    public static JsonArray tools(List<LlmTool> tools) {
        JsonArray a = new JsonArray();
        tools.forEach(t -> a.add(tool(t)));
        return a;
    }

    public static JsonObject message(String role, String text) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", text);
        return m;
    }

    /** The whole request body. `messages` and `tools` are used as they are (not copied). */
    public static JsonObject body(OpenAiConfig cfg, String model, JsonArray messages, JsonArray tools,
            boolean stream) {
        JsonObject b = new JsonObject();
        b.addProperty("model", model);
        b.add("messages", messages);
        if (tools != null && !tools.isEmpty()) b.add("tools", tools);
        // api.openai.com's newer models take only max_completion_tokens; other servers know max_tokens.
        b.addProperty(cfg.official() ? "max_completion_tokens" : "max_tokens", cfg.maxTokens());
        if (cfg.temperature() >= 0) b.addProperty("temperature", cfg.temperature());
        if (cfg.reasoningEffort() != null && !cfg.reasoningEffort().isBlank()) {
            b.addProperty("reasoning_effort", cfg.reasoningEffort().trim());
        }
        if (stream) {
            b.addProperty("stream", true);
            JsonObject opts = new JsonObject();
            opts.addProperty("include_usage", true);
            b.add("stream_options", opts);
        }
        return b;
    }

    /** The model's own turn, to send back. Arguments that were not valid JSON are replayed as {}. */
    public static JsonObject assistant(String text, List<Call> calls) {
        JsonObject m = new JsonObject();
        m.addProperty("role", "assistant");
        boolean silent = text == null || text.isBlank();
        if (calls.isEmpty()) {
            // An empty turn breaks some chat templates, so silence is said out loud.
            m.addProperty("content", silent ? "(I say and do nothing.)" : text);
            return m;
        }
        if (silent) m.add("content", com.google.gson.JsonNull.INSTANCE);
        else m.addProperty("content", text);
        JsonArray list = new JsonArray();
        for (Call c : calls) {
            JsonObject fn = new JsonObject();
            fn.addProperty("name", c.name());
            JsonObject parsed = ToolCheck.parse(c.json());
            fn.addProperty("arguments", parsed == null ? "{}" : parsed.toString());
            JsonObject tc = new JsonObject();
            tc.addProperty("id", c.id());
            tc.addProperty("type", "function");
            tc.add("function", fn);
            list.add(tc);
        }
        m.add("tool_calls", list);
        return m;
    }

    /** A user message of words followed by one picture. */
    public static JsonObject pictured(String text, byte[] png) {
        JsonArray parts = new JsonArray();
        JsonObject words = new JsonObject();
        words.addProperty("type", "text");
        words.addProperty("text", text);
        parts.add(words);
        JsonObject url = new JsonObject();
        url.addProperty("url", "data:image/png;base64," + Base64.getEncoder().encodeToString(png));
        JsonObject part = new JsonObject();
        part.addProperty("type", "image_url");
        part.add("image_url", url);
        parts.add(part);
        JsonObject m = new JsonObject();
        m.addProperty("role", "user");
        m.add("content", parts);
        return m;
    }

    /**
     * Appends one "tool" message per result, then, if there are images the model can see or words that
     * arrived meanwhile, one user message holding them (tool messages carry text only).
     */
    public static void results(JsonArray messages, List<ToolAnswer> answers, String extraText, boolean vision) {
        List<ToolAnswer> pictures = new ArrayList<>();
        for (ToolAnswer a : answers) {
            String text = a.result().text() == null || a.result().text().isBlank() ? "done" : a.result().text();
            if (a.result().isError()) text = "ERROR: " + text;
            if (a.result().hasImage()) {
                if (vision) pictures.add(a);
                else text += BLIND_NOTE;
            }
            JsonObject m = new JsonObject();
            m.addProperty("role", "tool");
            m.addProperty("tool_call_id", a.id());
            m.addProperty("content", text);
            messages.add(m);
        }
        boolean extra = extraText != null && !extraText.isBlank();
        if (pictures.isEmpty()) {
            if (extra) messages.add(message("user", extraText));
            return;
        }
        StringBuilder say = new StringBuilder("[What your powers showed you, in order: ");
        for (int i = 0; i < pictures.size(); i++) {
            say.append(i == 0 ? "" : ", ").append(pictures.get(i).name());
        }
        say.append("]");
        if (extra) say.append("\n").append(extraText);
        JsonArray parts = new JsonArray();
        JsonObject words = new JsonObject();
        words.addProperty("type", "text");
        words.addProperty("text", say.toString());
        parts.add(words);
        for (ToolAnswer a : pictures) {
            JsonObject url = new JsonObject();
            url.addProperty("url", "data:image/png;base64," + Base64.getEncoder().encodeToString(a.result().png()));
            JsonObject part = new JsonObject();
            part.addProperty("type", "image_url");
            part.add("image_url", url);
            parts.add(part);
        }
        JsonObject m = new JsonObject();
        m.addProperty("role", "user");
        m.add("content", parts);
        messages.add(m);
    }
}
