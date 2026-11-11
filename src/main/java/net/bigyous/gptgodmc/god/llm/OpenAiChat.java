package net.bigyous.gptgodmc.god.llm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A conversation with any OpenAI-compatible chat completions server (OpenAI, OpenRouter, Ollama,
 * LM Studio, llama.cpp, vLLM), over the JDK's own HTTP client. What such servers lack (prompt-cache
 * controls, adaptive thinking, web search, refusal fallback) is simply not asked for.
 */
public final class OpenAiChat implements LlmChat {
    // HTTP/1.1 on purpose: several local servers mishandle the JDK's HTTP/2 upgrade request.
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10)).build();
    private static final ScheduledExecutorService WATCH = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "god-llm-watchdog");
        t.setDaemon(true);
        return t;
    });

    private final OpenAiConfig cfg;
    private final String model, system, chronicle;
    private final List<LlmTool> tools;
    private final JsonArray toolsJson;
    private final JsonArray messages = new JsonArray();

    public OpenAiChat(OpenAiConfig cfg, String model, String system, String chronicle, List<LlmTool> tools) {
        this.cfg = cfg;
        this.model = model;
        this.system = system == null ? "" : system;
        this.chronicle = chronicle == null ? "" : chronicle;
        this.tools = tools;
        this.toolsJson = OpenAiWire.tools(tools);
        // One system message: many local chat templates allow only one.
        messages.add(OpenAiWire.message("system",
                this.chronicle.isBlank() ? this.system : this.system + "\n\n" + this.chronicle));
    }

    @Override
    public void user(String text) {
        messages.add(OpenAiWire.message("user", text));
    }

    @Override
    public void user(String text, byte[] png) {
        if (png == null || png.length == 0 || !cfg.vision()) {
            user(text);
            return;
        }
        messages.add(OpenAiWire.pictured(text, png));
    }

    @Override
    public boolean sees() {
        return cfg.vision();
    }

    @Override
    public void assistant(LlmReply reply) {
        if (reply.turn instanceof JsonObject turn) messages.add(turn);
    }

    @Override
    public void toolResults(List<ToolAnswer> answers, String extraText) {
        OpenAiWire.results(messages, answers, extraText, cfg.vision());
    }

    /** The conversation so far (for tests). */
    public JsonArray messages() {
        return messages;
    }

    private record Prepared(String body, String systemText, String chronicleText, JsonArray toolList)
            implements Request {
        @Override
        public String bodyJson() {
            return body;
        }

        @Override
        public List<String> betas() {
            return List.of();
        }

        @Override
        public String system() {
            return systemText;
        }

        @Override
        public String chronicle() {
            return chronicleText;
        }

        @Override
        public String toolsJson() {
            return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(toolList);
        }
    }

    @Override
    public Request prepare(String effort) {
        // The body is fixed here, as text, so what is traced is exactly what is sent.
        return new Prepared(OpenAiWire.body(cfg, model, messages, toolsJson, true).toString(), system, chronicle,
                toolsJson);
    }

    @Override
    public LlmReply send(Request request, Listener listener) {
        List<String> names = new ArrayList<>();
        if (cfg.textCalls()) tools.forEach(t -> names.add(t.name()));
        OpenAiStream stream = new OpenAiStream(listener, names);
        LlmReply.Error error = null;
        String url = cfg.endpoint();
        if (url == null) {
            error = LlmReply.Error.broken("llm.base-url is not set");
        } else {
            try {
                HttpResponse<InputStream> response = open(url, request.bodyJson(), true);
                if (response.statusCode() / 100 != 2) {
                    error = LlmReply.Error.service(response.statusCode(), errorText(response.body()));
                } else {
                    read(response, stream);
                }
            } catch (OpenAiStream.ServerError e) {
                error = LlmReply.Error.broken(e.getMessage());
            } catch (IOException e) {
                error = LlmReply.Error.network(describe(e));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                error = LlmReply.Error.network("interrupted");
            } catch (RuntimeException e) {
                error = LlmReply.Error.broken(e.toString());
            }
        }
        if (error == null) {
            try {
                stream.end();
            } catch (RuntimeException e) {
                error = LlmReply.Error.broken(e.toString());
            }
        }
        LlmReply reply = stream.reply();
        reply.error = error;
        if (error != null) {
            // Whatever arrived before the failure was acted on, but the turn is not whole.
            reply.finished = false;
            reply.replayable = false;
            reply.responseJson = null;
        }
        return reply;
    }

    /** POSTs the body; busy servers and refused connections are retried a little before giving up. */
    private HttpResponse<InputStream> open(String url, String body, boolean streaming)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(5, cfg.timeoutSeconds())))
                .header("Content-Type", "application/json")
                .header("Accept", streaming ? "text/event-stream" : "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (cfg.apiKey() != null && !cfg.apiKey().isBlank()) {
            b.header("Authorization", "Bearer " + cfg.apiKey().trim());
        }
        HttpRequest request = b.build();
        IOException failed = null;
        int retries = Math.max(0, cfg.retries());
        for (int attempt = 0; attempt <= retries; attempt++) {
            if (attempt > 0) Thread.sleep(attempt * 1500L);
            try {
                HttpResponse<InputStream> r = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int s = r.statusCode();
                boolean busy = s == 429 || s == 500 || s == 502 || s == 503 || s == 529;
                if (!busy || attempt == retries) return r;
                r.body().close();
            } catch (java.net.http.HttpTimeoutException e) {
                throw e; // already waited the whole timeout: do not wait again
            } catch (IOException e) {
                failed = e;
            }
        }
        throw failed != null ? failed : new IOException("no answer");
    }

    private void read(HttpResponse<InputStream> response, OpenAiStream stream) throws IOException {
        String type = response.headers().firstValue("Content-Type").orElse("");
        InputStream in = response.body();
        long limit = Math.max(5, cfg.timeoutSeconds()) * 1000L;
        AtomicLong lastData = new AtomicLong(System.currentTimeMillis());
        AtomicBoolean acting = new AtomicBoolean(false);
        AtomicBoolean stalled = new AtomicBoolean(false);
        // The request timeout only covers the wait for the first byte; this covers silence afterwards.
        ScheduledFuture<?> watch = WATCH.scheduleAtFixedRate(() -> {
            if (!acting.get() && System.currentTimeMillis() - lastData.get() > limit) {
                stalled.set(true);
                try {
                    in.close();
                } catch (IOException e) {
                    // closing is all that matters
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            if (type.contains("application/json")) {
                // A server that ignored "stream": the whole completion in one piece.
                StringBuilder all = new StringBuilder();
                char[] buf = new char[8192];
                for (int n; (n = reader.read(buf)) >= 0;) {
                    all.append(buf, 0, n);
                    lastData.set(System.currentTimeMillis());
                }
                acting.set(true);
                stream.chunk(JsonParser.parseString(all.toString()).getAsJsonObject());
                return;
            }
            for (String line; (line = reader.readLine()) != null;) {
                // A tool carried out mid-stream may take a while; that is not the server stalling.
                acting.set(true);
                try {
                    stream.line(line);
                } finally {
                    lastData.set(System.currentTimeMillis());
                    acting.set(false);
                }
            }
        } catch (IOException e) {
            if (stalled.get()) {
                throw new IOException("the server sent nothing for " + cfg.timeoutSeconds() + " seconds");
            }
            throw e;
        } finally {
            watch.cancel(false);
        }
    }

    private static String describe(IOException e) {
        if (e instanceof java.net.http.HttpTimeoutException) return "timed out waiting for the server";
        if (e instanceof java.net.ConnectException) return "connection refused (is the server running?)";
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }

    /** The server's error message, shortened. */
    private static String errorText(InputStream in) {
        String raw;
        try (in) {
            raw = new String(in.readNBytes(4000), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
        try {
            JsonElement e = JsonParser.parseString(raw);
            if (e.isJsonObject() && e.getAsJsonObject().has("error")) {
                JsonElement err = e.getAsJsonObject().get("error");
                if (err.isJsonObject() && err.getAsJsonObject().has("message")) {
                    raw = err.getAsJsonObject().get("message").getAsString();
                } else if (err.isJsonPrimitive()) {
                    raw = err.getAsString();
                }
            }
        } catch (RuntimeException e) {
            // not JSON: show it as it is
        }
        return raw.length() > 500 ? raw.substring(0, 500) + "…" : raw;
    }

    // ---- One plain question (the memory model) ----------------------------------------------------

    /** The answer to a single-turn request, with what it used (zeros when the server did not say). */
    public record Completion(String text, long in, long out, long cacheRead, boolean billed) {
    }

    /** Asks one question without tools or streaming. Throws IOException with a readable message on failure. */
    public static Completion complete(OpenAiConfig cfg, String model, String prompt) throws IOException {
        String url = cfg.endpoint();
        if (url == null) throw new IOException("llm.base-url is not set");
        JsonArray messages = new JsonArray();
        messages.add(OpenAiWire.message("user", prompt));
        String body = OpenAiWire.body(cfg, model, messages, null, false).toString();
        OpenAiChat chat = new OpenAiChat(cfg, model, "", "", List.of());
        try {
            HttpResponse<InputStream> response = chat.open(url, body, false);
            if (response.statusCode() / 100 != 2) {
                throw new IOException(response.statusCode() + ": " + errorText(response.body()));
            }
            String raw;
            try (InputStream in = response.body()) {
                raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            StringBuilder said = new StringBuilder();
            OpenAiStream stream = new OpenAiStream(new Listener() {
                private boolean prose;

                @Override
                public void token() {
                }

                @Override
                public void open(long block, boolean thinking) {
                    prose = !thinking;
                }

                @Override
                public void delta(long block, String text) {
                    if (prose) said.append(text);
                }

                @Override
                public void close(long block) {
                }

                @Override
                public void call(String id, String name, String json) {
                }

                @Override
                public void search(String query) {
                }
            }, null);
            stream.chunk(JsonParser.parseString(raw).getAsJsonObject());
            stream.end();
            LlmReply r = stream.reply();
            return new Completion(said.toString().trim(), r.in, r.out, r.cacheRead, r.billed);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        } catch (IOException e) {
            throw new IOException(describe(e));
        } catch (RuntimeException e) {
            throw new IOException(String.valueOf(e.getMessage()));
        }
    }
}
