package net.bigyous.gptgodmc.god;

import java.time.Duration;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Usage;

import net.bigyous.gptgodmc.GPTGOD;

/** Owns the Anthropic client. Rebuilt whenever the key or base URL changes. */
public final class GodClient {
    private static AnthropicClient client;
    private static String builtFor;

    private GodClient() {
    }

    /** True when the configured provider can be called (a key for Claude or OpenAI, an address for a local server). */
    public static boolean hasCredentials() {
        return Llm.anthropic() ? !GodSettings.apiKey().isBlank() : Llm.ready();
    }

    /** Returns a client, or null when no API key is configured. */
    public static synchronized AnthropicClient get() {
        String key = GodSettings.apiKey();
        if (key.isBlank()) {
            return null;
        }
        String signature = key + "|" + GodSettings.baseUrl();
        if (client == null || !signature.equals(builtFor)) {
            close();
            AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                    .apiKey(key)
                    .maxRetries(3)
                    .timeout(Duration.ofSeconds(120));
            String baseUrl = GodSettings.baseUrl();
            if (baseUrl != null && !baseUrl.isBlank()) {
                builder.baseUrl(baseUrl);
            }
            client = builder.build();
            builtFor = signature;
        }
        return client;
    }

    public static synchronized void close() {
        if (client != null) {
            try {
                client.close();
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.debug("Error closing Anthropic client", e);
            }
            client = null;
            builtFor = null;
        }
    }

    /** Logs what one request used; `purpose` names it in the console and usage.csv. */
    public static void recordUsage(String model, String purpose, Usage usage) {
        if (usage == null) {
            return;
        }
        UsageLog.record(model, purpose, usage.inputTokens(), usage.outputTokens(),
                usage.cacheReadInputTokens().orElse(0L), usage.cacheCreationInputTokens().orElse(0L));
    }

    /** True for models that accept the server-side refusal fallback ("default" form). */
    public static boolean supportsDefaultFallback(String model) {
        return model.startsWith("claude-sonnet-5-5") || model.startsWith("claude-opus-5")
                || model.startsWith("claude-fable-5-1");
    }
}
