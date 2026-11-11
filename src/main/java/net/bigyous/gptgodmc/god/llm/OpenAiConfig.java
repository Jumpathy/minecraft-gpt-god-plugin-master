package net.bigyous.gptgodmc.god.llm;

import java.net.URI;

/**
 * How to reach an OpenAI-compatible chat completions server.
 *
 * @param official        api.openai.com itself (newer parameter names); false for every compatible server
 * @param baseUrl         e.g. http://localhost:11434/v1; blank means api.openai.com for the official provider
 * @param apiKey          sent as a bearer token when not blank (local servers need none)
 * @param maxTokens       the longest reply
 * @param timeoutSeconds  how long to wait for the reply to start, and between pieces of it
 * @param vision          the model can see images (they are sent as image_url); otherwise a note in words
 * @param temperature     negative = leave it to the server
 * @param reasoningEffort blank = do not send it
 * @param textCalls       also accept tool calls a small model wrote into its prose
 * @param retries         extra attempts when the server is busy or unreachable before anything streamed
 */
public record OpenAiConfig(boolean official, String baseUrl, String apiKey, long maxTokens, int timeoutSeconds,
        boolean vision, double temperature, String reasoningEffort, boolean textCalls, int retries) {

    public static final String OPENAI_URL = "https://api.openai.com/v1";

    /** The chat completions URL, or null when no server is configured. */
    public String endpoint() {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.isEmpty()) {
            if (!official) return null;
            base = OPENAI_URL;
        }
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.endsWith("/chat/completions")) return base;
        try {
            String path = URI.create(base).getPath();
            // "http://localhost:1234" alone: every server this talks to serves the API under /v1.
            if (path == null || path.isEmpty()) base += "/v1";
        } catch (IllegalArgumentException e) {
            return null;
        }
        return base + "/chat/completions";
    }

    /** True when the server runs on this machine or the local network (no key, small models likely). */
    public static boolean isLocal(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return false;
        String host;
        try {
            host = URI.create(baseUrl.trim()).getHost();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (host == null) return false;
        host = host.toLowerCase(java.util.Locale.ROOT);
        if (host.startsWith("[")) host = host.substring(1, host.length() - 1);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                || host.equals("host.docker.internal") || host.equals("::1") || host.equals("0.0.0.0")) {
            return true;
        }
        if (host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.")) return true;
        if (host.startsWith("172.")) {
            String[] p = host.split("\\.");
            try {
                int second = Integer.parseInt(p[1]);
                return second >= 16 && second <= 31;
            } catch (RuntimeException e) {
                return false;
            }
        }
        // A bare machine name on the LAN ("gamingpc:1234").
        return !host.contains(".");
    }
}
