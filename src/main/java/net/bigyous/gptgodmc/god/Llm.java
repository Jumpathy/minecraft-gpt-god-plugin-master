package net.bigyous.gptgodmc.god;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.anthropic.client.AnthropicClient;
import com.google.gson.Gson;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.llm.LitePerception;
import net.bigyous.gptgodmc.god.llm.LlmChat;
import net.bigyous.gptgodmc.god.llm.LlmTool;
import net.bigyous.gptgodmc.god.llm.OpenAiChat;
import net.bigyous.gptgodmc.god.llm.OpenAiConfig;
import net.bigyous.gptgodmc.god.llm.OpenAiWire;
import net.bigyous.gptgodmc.god.tools.ToolRegistry;
import net.bigyous.gptgodmc.utils.TokenTracker;

/**
 * Which language model plays the god (the llm: section of config.yml) and how to reach it. Anthropic is
 * the default and reads its own anthropic: section as it always has; "openai" and "openai-compatible"
 * (OpenRouter, Ollama, LM Studio, llama.cpp, vLLM) share one client. Also decides the "lite" profile:
 * fewer tools, a short prompt and a small perception for small local models.
 */
public final class Llm {
    public enum Provider {
        ANTHROPIC, OPENAI, COMPATIBLE
    }

    /** The powers a small model is offered: enough to speak, give, punish, set quests, remember and appear. */
    public static final List<String> LITE_TOOLS = List.of("whisper", "announce", "vessel_say", "give_item", "heal",
            "apply_effect", "smite", "set_quest", "resolve_quest", "remember", "judge", "vessel_go", "vessel_vanish",
            "vessel_mood", "command_done", "witness_oath");

    private static final FileConfiguration DEFAULTS = new YamlConfiguration();
    /** Characters per token, on the careful side, for fitting a perception into a context window. */
    private static final double CHARS_PER_TOKEN = 3.2;

    private Llm() {
    }

    private static FileConfiguration cfg() {
        GPTGOD plugin = GPTGOD.get();
        return plugin == null ? DEFAULTS : plugin.getConfig();
    }

    private static String text(String key) {
        String v = cfg().getString(key, "");
        return v == null ? "" : v.trim();
    }

    // ---- Which provider --------------------------------------------------------------------------

    public static Provider provider() {
        return provider(text("llm.provider"));
    }

    static Provider provider(String name) {
        String p = name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (p) {
            case "openai" -> Provider.OPENAI;
            case "openai-compatible", "compatible", "local", "ollama", "lmstudio", "lm-studio", "llamacpp",
                    "llama.cpp", "vllm", "openrouter" -> Provider.COMPATIBLE;
            default -> Provider.ANTHROPIC;
        };
    }

    public static boolean anthropic() {
        return provider() == Provider.ANTHROPIC;
    }

    /** What to call the model in the journal and console ("Claude is busy", "The model is busy"). */
    public static String label() {
        return switch (provider()) {
            case ANTHROPIC -> "Claude";
            case OPENAI -> "OpenAI";
            case COMPATIBLE -> "The model";
        };
    }

    /** The name the debug webhook files requests under. */
    static String webhookName() {
        return switch (provider()) {
            case ANTHROPIC -> "claude";
            case OPENAI -> "openai";
            case COMPATIBLE -> "openai-compatible";
        };
    }

    // ---- Key, address, models --------------------------------------------------------------------

    /** llm.api-key, else OPENAI_API_KEY for the openai provider or LLM_API_KEY for a compatible one. */
    public static String apiKey() {
        String key = text("llm.api-key");
        if (!key.isEmpty()) return key;
        String env = System.getenv(provider() == Provider.OPENAI ? "OPENAI_API_KEY" : "LLM_API_KEY");
        return env == null ? "" : env.trim();
    }

    public static String apiKeySource() {
        if (!text("llm.api-key").isEmpty()) return "config.yml (llm.api-key)";
        String name = provider() == Provider.OPENAI ? "OPENAI_API_KEY" : "LLM_API_KEY";
        String env = System.getenv(name);
        if (env != null && !env.isBlank()) return name + " environment variable";
        return provider() == Provider.COMPATIBLE ? "none (not needed by local servers)" : "none";
    }

    public static String baseUrl() {
        return text("llm.base-url");
    }

    /** llm.model; OpenAI has a default, a compatible server must be told. */
    public static String configuredModel() {
        String m = text("llm.model");
        return m.isEmpty() && provider() == Provider.OPENAI ? "gpt-5-mini" : m;
    }

    /** llm.memory-model, else the main model. */
    public static String memoryModel() {
        String m = text("llm.memory-model");
        return m.isEmpty() ? configuredModel() : m;
    }

    /**
     * Whether a god's own model override (profile.json) belongs to the provider in use. A Claude model
     * picked for a god is ignored while another provider plays the gods, and the other way round, so
     * switching provider never leaves a god asking for a model its server does not have.
     */
    public static boolean fits(String model) {
        if (model == null || model.isBlank()) return false;
        boolean claude = model.trim().toLowerCase(Locale.ROOT).startsWith("claude-");
        // Behind a gateway (anthropic.base-url) any name may be a real model, so it is kept.
        return anthropic() ? claude || !text("anthropic.base-url").isEmpty() : !claude;
    }

    /** Null when the god can think; otherwise what the owner must do, for the console and operators. */
    public static String notReady() {
        switch (provider()) {
            case ANTHROPIC:
                return GodSettings.apiKey().isBlank()
                        ? "no Anthropic API key. Run `gptgod setkey <key>` in the server console, or set "
                                + "ANTHROPIC_API_KEY."
                        : null;
            case OPENAI:
                return apiKey().isEmpty() && baseUrl().isEmpty()
                        ? "no OpenAI API key. Run `gptgod setkey <key>` in the server console, or set "
                                + "OPENAI_API_KEY."
                        : null;
            default:
                if (baseUrl().isEmpty()) {
                    return "llm.base-url is not set (for example http://localhost:11434/v1 for Ollama).";
                }
                return configuredModel().isEmpty() ? "llm.model is not set (the model's name on your server)." : null;
        }
    }

    public static boolean ready() {
        return notReady() == null;
    }

    // ---- The lite profile ------------------------------------------------------------------------

    /** llm.profile: full, lite, or auto (lite when a compatible server runs on this machine or network). */
    public static boolean lite() {
        return lite(text("llm.profile"), provider(), baseUrl());
    }

    static boolean lite(String profile, Provider provider, String baseUrl) {
        String p = profile == null ? "" : profile.trim().toLowerCase(Locale.ROOT);
        if (p.equals("lite")) return true;
        if (p.equals("full")) return false;
        return provider == Provider.COMPATIBLE && OpenAiConfig.isLocal(baseUrl);
    }

    /** The names of the powers offered in the lite profile (llm.lite.tools, or the built-in core set). */
    public static Set<String> liteToolNames() {
        List<String> own = cfg().getStringList("llm.lite.tools");
        Set<String> out = new LinkedHashSet<>();
        for (String name : own) {
            if (name != null && ToolRegistry.get(name.trim()) != null) out.add(name.trim());
        }
        if (out.isEmpty()) out.addAll(LITE_TOOLS);
        return out;
    }

    /** True when the god now thinking may use this power (always, outside the lite profile). */
    public static boolean offers(String tool) {
        return !lite() || liteToolNames().contains(tool);
    }

    public static int maxSteps(int configured) {
        return lite() ? Math.min(configured, Math.max(1, cfg().getInt("llm.lite.max-steps", 3))) : configured;
    }

    /** The lite profile's whole perception, in characters. */
    public static int litePerceptionChars() {
        return Math.max(1500, cfg().getInt("llm.lite.perception-chars", 6000));
    }

    public static int contextTokens() {
        return Math.max(0, cfg().getInt("llm.context-tokens", 0));
    }

    public static long maxOutputTokens() {
        long max = Math.max(128, cfg().getLong("llm.max-output-tokens", 2000));
        // A lite god says a line or two and calls a few powers; a long reply only eats its context.
        return lite() ? Math.min(max, Math.max(128, cfg().getLong("llm.lite.max-output-tokens", 1024))) : max;
    }

    /**
     * Cuts a perception to what the model can take: the lite budget, and whatever llm.context-tokens
     * leaves after the prompt, the tool list and the reply. Unchanged with Anthropic's full profile.
     */
    public static String fitPerception(String perception, String system) {
        int budget = Integer.MAX_VALUE;
        if (lite()) budget = litePerceptionChars();
        int context = contextTokens();
        if (context > 0 && !anthropic()) {
            long prompt = system.length() + new Gson().toJson(OpenAiWire.tools(tools())).length() + 1500L;
            long room = (long) ((context - maxOutputTokens()) * CHARS_PER_TOKEN) - prompt;
            budget = (int) Math.max(1500, Math.min(budget, room));
        }
        return budget == Integer.MAX_VALUE ? perception : LitePerception.trim(perception, budget);
    }

    // ---- Tools -----------------------------------------------------------------------------------

    /** The powers offered to an OpenAI-compatible model in the current profile, in the registry's order. */
    public static List<LlmTool> tools() {
        List<LlmTool> all = ToolRegistry.specs();
        if (!lite()) return all;
        Set<String> names = liteToolNames();
        List<LlmTool> out = new ArrayList<>();
        for (LlmTool t : all) {
            if (names.contains(t.name())) out.add(liteTool(t));
        }
        return out;
    }

    /** A power as the lite profile describes it: the same name and arguments, said briefly. */
    static LlmTool liteTool(LlmTool t) {
        java.util.Map<String, java.util.Map<String, Object>> props = new java.util.LinkedHashMap<>();
        t.properties().forEach((name, prop) -> {
            java.util.Map<String, Object> p = new java.util.LinkedHashMap<>(prop);
            if (p.get("description") instanceof String d) p.put("description", brief(d, 90));
            props.put(name, p);
        });
        return new LlmTool(t.name(), brief(t.description(), 200), props, t.required());
    }

    /** The first sentence or two of a description: small models lose the thread in long ones. */
    static String brief(String description) {
        return brief(description, 240);
    }

    static String brief(String description, int max) {
        if (description == null) return "";
        String d = description.trim();
        if (d.length() <= max) return d;
        int cut = -1;
        for (int i = Math.min(30, max / 3); i < max; i++) {
            if (d.charAt(i) == '.' && Character.isWhitespace(d.charAt(i + 1))) cut = i + 1;
        }
        return cut > 0 ? d.substring(0, cut) : d.substring(0, max - 1) + "…";
    }

    // ---- Talking to it ---------------------------------------------------------------------------

    static OpenAiConfig config() {
        String vision = text("llm.vision").toLowerCase(Locale.ROOT);
        boolean sees = vision.equals("true") || vision.equals("on") || vision.equals("yes")
                || (!vision.equals("false") && !vision.equals("off") && !vision.equals("no")
                        && provider() == Provider.OPENAI);
        return new OpenAiConfig(provider() == Provider.OPENAI, baseUrl(), apiKey(),
                maxOutputTokens(), Math.max(5, cfg().getInt("llm.timeout-seconds", 120)), sees,
                cfg().getDouble("llm.temperature", -1), text("llm.reasoning-effort"), lite(), 2);
    }

    /** Opens a thought's conversation with the configured provider, or null when it is not ready. */
    static LlmChat open(String model, String system) {
        if (anthropic()) {
            AnthropicClient client = GodClient.get();
            return client == null ? null : new AnthropicChat(client, model, system);
        }
        if (!ready()) return null;
        String chronicle = Prompts.chronicle();
        if (lite() && chronicle.length() > 1500) chronicle = chronicle.substring(0, 1500) + "…";
        return new OpenAiChat(config(), model, system, chronicle, tools());
    }

    /**
     * One plain question to an OpenAI-compatible model (the memory model's chores). Null on failure.
     * Inline thinking is dropped and the tokens it used are logged.
     */
    static String complete(String model, String prompt, long maxTokens, String purpose) {
        if (!ready()) return null;
        OpenAiConfig base = config();
        OpenAiConfig cfg = new OpenAiConfig(base.official(), base.baseUrl(), base.apiKey(), maxTokens + 500,
                base.timeoutSeconds(), false, base.temperature(), base.reasoningEffort(), false, 1);
        try {
            OpenAiChat.Completion c = OpenAiChat.complete(cfg, model, prompt);
            if (c.billed()) UsageLog.record(model, purpose, c.in(), c.out(), c.cacheRead(), 0);
            return c.text().isBlank() ? null : c.text();
        } catch (IOException e) {
            GPTGOD.LOGGER.warn("Memory model request failed: " + e.getMessage());
            return null;
        }
    }

    // ---- Prices ----------------------------------------------------------------------------------

    /**
     * Lets the owner give a price for models the plugin has none for (llm.price-input and
     * llm.price-output, USD per million tokens); without one, only tokens are shown.
     */
    public static void installPrices() {
        TokenTracker.setFallbackPrice(model -> {
            if (anthropic()) return null;
            double in = cfg().getDouble("llm.price-input", 0), out = cfg().getDouble("llm.price-output", 0);
            if (in <= 0 && out <= 0) return null;
            double cached = cfg().getDouble("llm.price-cached-input", in);
            return new double[] { in, out, cached, in };
        });
    }
}
