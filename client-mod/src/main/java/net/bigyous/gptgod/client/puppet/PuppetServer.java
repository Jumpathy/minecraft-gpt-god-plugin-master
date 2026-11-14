package net.bigyous.gptgod.client.puppet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * A remote control for end-to-end tests: with the JVM property gptgod.puppet.port set, a small HTTP server on
 * 127.0.0.1 lets a test harness drive this client as a player (walk, chat, click, screenshot) without any real
 * keyboard or mouse input. Without the property nothing here is started or registered.
 *
 * <p>Every request is handed to the client thread (a queue emptied at the end of each client tick) and the HTTP
 * thread waits for its answer. Replies are JSON: {"ok":true,...} or {"ok":false,"error":"..."}.
 */
final class PuppetServer {
    static final Logger LOG = LoggerFactory.getLogger("gptgod-puppet");
    private static final int MAX_BODY = 64 * 1024;
    private static final long WAIT_MS = 10_000;

    private static String token;
    /** Work for the client thread, run at the end of the next client tick. */
    static final ConcurrentLinkedQueue<Runnable> JOBS = new ConcurrentLinkedQueue<>();

    private PuppetServer() {
    }

    /** A request's answer, completed on any thread. The handler may ask for more time than the usual ten seconds. */
    static final class Reply {
        private final CompletableFuture<JsonObject> future = new CompletableFuture<>();
        private volatile long waitMs = WAIT_MS;

        void ok(JsonObject o) {
            future.complete(o);
        }

        void fail(int status, String error) {
            future.completeExceptionally(new Refusal(status, error));
        }

        /** This request is a task that runs for some client ticks: wait that long (at 20 ticks a second, doubled) and ten seconds more. */
        void allowTicks(int ticks) {
            waitMs = Math.max(waitMs, ticks * 100L + WAIT_MS);
        }

        boolean done() {
            return future.isDone();
        }
    }

    /** A request that cannot be carried out: becomes {"ok":false,"error":message} with this HTTP status. */
    static final class Refusal extends RuntimeException {
        final int status;

        Refusal(int status, String message) {
            super(message, null, false, false);
            this.status = status;
        }
    }

    interface Handler {
        void handle(JsonObject body, Map<String, String> query, Reply reply) throws Exception;
    }

    static JsonObject ok() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        return o;
    }

    static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("gptgod.puppet." + name));
    }

    /** Called by {@link Puppet#start()} when -Dgptgod.puppet.port=<port> was given. */
    static void start() {
        String p = System.getProperty("gptgod.puppet.port");
        if (p == null || p.isBlank()) return;
        try {
            int port = Integer.parseInt(p.trim());
            String t = System.getProperty("gptgod.puppet.token");
            token = t == null || t.isEmpty() ? null : t;
            HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);
            http.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread th = new Thread(r, "gptgod-puppet-http");
                th.setDaemon(true);
                return th;
            }));
            http.createContext("/", PuppetServer::serve);
            PuppetEvents.register();
            ClientTickEvents.END_CLIENT_TICK.register(PuppetTasks::tick);
            http.start();
            LOG.info("[gptgod-puppet] listening on 127.0.0.1:{}", http.getAddress().getPort());
            PuppetEvents.add("info", "puppet listening on 127.0.0.1:" + http.getAddress().getPort());
        } catch (Throwable e) {
            LOG.error("[gptgod-puppet] could not start on port {}", p, e);
        }
    }

    private static void serve(HttpExchange ex) throws IOException {
        int status = 200;
        JsonObject out;
        try {
            out = answer(ex);
        } catch (Refusal r) {
            status = r.status;
            out = error(r.getMessage());
        } catch (Throwable e) {
            status = 500;
            out = error(e.getClass().getSimpleName() + ": " + e.getMessage());
            LOG.warn("[gptgod-puppet] {} {} failed", ex.getRequestMethod(), ex.getRequestURI(), e);
        }
        byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("error", message == null ? "error" : message);
        return o;
    }

    private static JsonObject answer(HttpExchange ex) throws Exception {
        // A web page must never be able to drive the player: browsers send Origin, the harness does not.
        if (ex.getRequestHeaders().getFirst("Origin") != null) throw new Refusal(403, "requests from a browser are refused");
        if (token != null && !("Bearer " + token).equals(ex.getRequestHeaders().getFirst("Authorization"))) {
            throw new Refusal(401, "missing or wrong Authorization: Bearer token");
        }
        String method = ex.getRequestMethod().toUpperCase(java.util.Locale.ROOT);
        String path = ex.getRequestURI().getPath();
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        Map<String, String> query = query(ex.getRequestURI().getRawQuery());
        JsonObject body = body(ex);

        if (path.equals("/events")) { // no game access: answered right here, even while the client thread is busy
            if (!method.equals("GET")) throw new Refusal(405, "use GET " + path);
            return PuppetEvents.since(number(query.get("since"), 0), (int) number(query.get("limit"), 200));
        }
        Handler handler = PuppetActions.ROUTES.get(method + " " + path);
        if (handler == null) {
            boolean other = PuppetActions.ROUTES.containsKey((method.equals("GET") ? "POST " : "GET ") + path);
            throw other ? new Refusal(405, "use " + (method.equals("GET") ? "POST " : "GET ") + path)
                    : new Refusal(404, "no such endpoint: " + method + " " + path);
        }
        Reply reply = new Reply();
        JOBS.add(() -> {
            try {
                handler.handle(body, query, reply);
            } catch (Refusal r) {
                reply.fail(r.status, r.getMessage());
            } catch (Throwable e) {
                LOG.warn("[gptgod-puppet] {} failed", ex.getRequestURI(), e);
                reply.fail(500, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
        long start = System.currentTimeMillis();
        while (true) {
            long left = start + reply.waitMs - System.currentTimeMillis();
            if (left <= 0) throw new Refusal(504, "the client thread did not answer in " + reply.waitMs / 1000 + " s");
            try {
                return reply.future.get(Math.min(left, 250), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                // look again: the handler may have asked for more time
            } catch (ExecutionException e) {
                if (e.getCause() instanceof Refusal r) throw r;
                throw new Refusal(500, String.valueOf(e.getCause()));
            }
        }
    }

    private static long number(String s, long fallback) {
        if (s == null || s.isBlank()) return fallback;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            throw new Refusal(400, "not a number: " + s);
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null) return map;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            map.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return map;
    }

    private static JsonObject body(HttpExchange ex) throws IOException {
        byte[] bytes;
        try (InputStream in = ex.getRequestBody()) {
            bytes = in.readNBytes(MAX_BODY + 1);
        }
        if (bytes.length > MAX_BODY) throw new Refusal(413, "body larger than " + MAX_BODY + " bytes");
        String text = new String(bytes, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) return new JsonObject();
        try {
            JsonElement el = JsonParser.parseString(text);
            if (!el.isJsonObject()) throw new Refusal(400, "the body must be a JSON object");
            return el.getAsJsonObject();
        } catch (com.google.gson.JsonParseException e) {
            throw new Refusal(400, "the body is not valid JSON: " + e.getMessage());
        }
    }
}
