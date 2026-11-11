package net.bigyous.gptgodmc.viewer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

import org.bukkit.configuration.file.FileConfiguration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.MindEdits;
import net.bigyous.gptgodmc.god.MindProbe;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.tools.ToolException;

/**
 * The mind viewer: a web page, served by the plugin, where the server owner watches each god think —
 * its perception, prompts, requests, streamed thinking, tool calls and results, memory and the events
 * it is about to see. Off by default (viewer.enabled); local-only unless viewer.bind says otherwise,
 * and then a token is required. Every response is passed through {@link Redact}.
 */
public final class MindViewer {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    static final String COOKIE = "gptgod_viewer";
    private static HttpServer server;
    private static String token;
    private static boolean local;
    private static volatile Redact redact = Redact.of(List.of());
    private static volatile long redactAt;
    private static byte[] page;
    /** Required (as X-Viewer-Csrf) on every change; only the page served by us knows it. */
    private static String csrf;
    /** bind|port|token of the running server, to tell whether a config change needs a restart. */
    private static String running;
    private static String url;

    private MindViewer() {
    }

    // ---- Settings -----------------------------------------------------------------------------------

    private static FileConfiguration cfg() {
        return GPTGOD.get().getConfig();
    }

    public static boolean enabledInConfig() {
        return cfg().getBoolean("viewer.enabled", true);
    }

    public static boolean isRunning() {
        return server != null;
    }

    /** The address to open, or null when the viewer is off. */
    public static String url() {
        return server == null ? null : url;
    }

    private static String settings() {
        return cfg().getString("viewer.bind", "127.0.0.1") + "|" + cfg().getInt("viewer.port", 4673) + "|"
                + cfg().getString("viewer.token", "");
    }

    /**
     * Matches the running viewer to config.yml without a server restart: starts it when it was turned on,
     * stops it when turned off, restarts it when its port, bind or token changed. Returns a line for the
     * operator.
     */
    public static synchronized String sync() {
        boolean want = enabledInConfig();
        if (server != null && (!want || !settings().equals(running))) stop();
        if (want && server == null) start();
        if (!want) return "Mind viewer: off.";
        return server == null ? "Mind viewer: could not start (see the console; is the port in use?)."
                : "Mind viewer: " + url;
    }

    // ---- Lifecycle ----------------------------------------------------------------------------------

    public static synchronized void start() {
        if (!enabledInConfig() || server != null) return;
        String bind = cfg().getString("viewer.bind", "127.0.0.1");
        if (bind == null || bind.isBlank()) bind = "127.0.0.1";
        int port = cfg().getInt("viewer.port", 4673);
        try {
            InetAddress addr = InetAddress.getByName(bind);
            local = addr.isLoopbackAddress();
            token = resolveToken();
            csrf = randomHex(16);
            page = new String(loadPage(), StandardCharsets.UTF_8).replace("__CSRF__", csrf)
                    .getBytes(StandardCharsets.UTF_8);
            server = HttpServer.create(new InetSocketAddress(addr, port), 0);
            server.setExecutor(Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "god-viewer");
                t.setDaemon(true);
                return t;
            }));
            server.createContext("/", MindViewer::handle);
            server.start();
            MindTrace.setEnabled(true);
            String host = local ? "127.0.0.1" : addr.isAnyLocalAddress() ? "<this-server>" : bind;
            running = settings();
            url = "http://" + host + ":" + port + "/" + (local ? "" : "?token=" + token);
            GPTGOD.LOGGER.info("Mind viewer: " + url);
            if (!local) {
                GPTGOD.LOGGER.warn("The mind viewer is reachable from the network (viewer.bind " + bind + "). It shows "
                        + "everything the gods see and think; keep the token secret.");
            }
        } catch (IOException e) {
            server = null;
            GPTGOD.LOGGER.error("Could not start the mind viewer on " + bind + ":" + port + ": " + e.getMessage());
        }
    }

    public static synchronized void stop() {
        MindTrace.setEnabled(false);
        if (server != null) {
            server.stop(0);
            server = null;
            GPTGOD.LOGGER.info("Mind viewer stopped.");
        }
    }

    /** viewer.token, else the one saved in viewer.json, else a new one (saved there). */
    private static String resolveToken() {
        String set = cfg().getString("viewer.token", "");
        if (set != null && !set.isBlank()) return set.trim();
        Path file = GPTGOD.get().getDataFolder().toPath().resolve("viewer.json");
        try {
            if (Files.exists(file)) {
                JsonElement e = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (e.isJsonObject() && e.getAsJsonObject().has("token")) {
                    String t = e.getAsJsonObject().get("token").getAsString();
                    if (t.length() >= 16) return t;
                }
            }
        } catch (IOException | RuntimeException e) {
            // unreadable: make a new one
        }
        String t = randomHex(24);
        JsonObject info = new JsonObject();
        info.addProperty("token", t);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(info), StandardCharsets.UTF_8);
        } catch (IOException e) {
            GPTGOD.LOGGER.warn("Could not save " + file, e);
        }
        return t;
    }

    private static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    static byte[] loadPage() throws IOException {
        try (InputStream in = MindViewer.class.getResourceAsStream("/viewer/index.html")) {
            if (in == null) throw new IOException("viewer/index.html is missing from the jar");
            return in.readAllBytes();
        }
    }

    /** Secrets from config.yml and the environment, refreshed every 30 s. */
    private static Redact redactor() {
        long now = System.currentTimeMillis();
        if (now - redactAt > 30_000) {
            List<String> secrets = new ArrayList<>(Redact.secretsIn(cfg().getValues(true)));
            secrets.add(GodSettings.apiKey());
            secrets.add(net.bigyous.gptgodmc.god.Llm.apiKey());
            String env = System.getenv("ANTHROPIC_API_KEY");
            if (env != null) secrets.add(env);
            for (String name : new String[] { "OPENAI_API_KEY", "LLM_API_KEY" }) {
                String other = System.getenv(name);
                if (other != null) secrets.add(other);
            }
            secrets.add(token);
            redact = Redact.of(secrets);
            redactAt = now;
        }
        return redact;
    }

    // ---- Requests -----------------------------------------------------------------------------------

    private static void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            Map<String, String> q = query(ex.getRequestURI().getRawQuery());
            String method = ex.getRequestMethod();
            if (!"GET".equals(method) && !"POST".equals(method)) {
                send(ex, 405, error("GET or POST only"));
                return;
            }
            if (!authorized(ex, q)) {
                send(ex, 401, error("missing or wrong token: open the URL from the server console"));
                return;
            }
            if ("POST".equals(method)) {
                String given = ex.getRequestHeaders().getFirst("X-Viewer-Csrf");
                if (given == null || !MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8),
                        csrf.getBytes(StandardCharsets.UTF_8))) {
                    send(ex, 403, error("stale page: reload it"));
                    return;
                }
                if (!"/api/edit".equals(path)) {
                    send(ex, 404, error("not found"));
                    return;
                }
                JsonObject body = readBody(ex);
                JsonObject o = new JsonObject();
                o.addProperty("ok", true);
                o.addProperty("done", MindEdits.apply(realm(q.isEmpty() ? godOf(body) : q), body));
                send(ex, 200, o);
                return;
            }
            switch (path) {
                case "/", "/index.html" -> {
                    if (!local && q.containsKey("token")) {
                        ex.getResponseHeaders().add("Set-Cookie",
                                COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Strict");
                    }
                    ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                    ex.getResponseHeaders().add("Cache-Control", "no-store");
                    ex.getResponseHeaders().add("Referrer-Policy", "no-referrer");
                    ex.sendResponseHeaders(200, page.length);
                    try (OutputStream os = ex.getResponseBody()) {
                        os.write(page);
                    }
                }
                case "/api/gods" -> send(ex, 200, MindProbe.gods());
                case "/api/thoughts" -> send(ex, 200, thoughts(q));
                case "/api/events" -> send(ex, 200, EventFeed.since(parseLong(q.get("since"), 0),
                        (int) Math.max(1, Math.min(EventFeed.KEPT, parseLong(q.get("limit"), 600)))));
                case "/api/thought" -> send(ex, 200, fullThought(q));
                case "/api/blob" -> {
                    String text = MindTrace.blobText(q.getOrDefault("h", ""));
                    if (text == null) {
                        send(ex, 404, error("no longer kept"));
                        return;
                    }
                    JsonObject o = new JsonObject();
                    o.addProperty("text", text);
                    send(ex, 200, o);
                }
                case "/api/image" -> image(ex, q);
                case "/api/memory" -> send(ex, 200, MindProbe.memory(realm(q)));
                case "/api/prompt" -> send(ex, 200, MindProbe.prompt(realm(q)));
                case "/api/upcoming" -> send(ex, 200, MindProbe.upcoming(realm(q), "1".equals(q.get("perception"))));
                default -> send(ex, 404, error("not found"));
            }
        } catch (ToolException e) {
            send(ex, 400, error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            send(ex, 400, error(e.getMessage()));
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("Mind viewer request failed", e);
            send(ex, 500, error(e.toString()));
        } finally {
            ex.close();
        }
    }

    private static boolean authorized(HttpExchange ex, Map<String, String> q) {
        if (local) {
            // No token on localhost, but refuse foreign Host headers (DNS rebinding).
            return localHost(ex.getRequestHeaders().getFirst("Host"));
        }
        String given = q.get("token");
        if (given == null) given = cookie(ex.getRequestHeaders().getFirst("Cookie"), COOKIE);
        if (given == null) {
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) given = auth.substring(7).trim();
        }
        return given != null && MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    static boolean localHost(String hostHeader) {
        if (hostHeader == null) return true;
        String h = hostHeader.trim().toLowerCase(Locale.ROOT);
        if (h.startsWith("[")) {
            int end = h.indexOf(']');
            h = end > 0 ? h.substring(1, end) : h;
        } else {
            int colon = h.indexOf(':');
            if (colon >= 0) h = h.substring(0, colon);
        }
        return h.equals("localhost") || h.equals("127.0.0.1") || h.equals("::1") || h.endsWith(".localhost");
    }

    static String cookie(String header, String name) {
        if (header == null) return null;
        for (String part : header.split(";")) {
            String p = part.trim();
            if (p.startsWith(name + "=")) return p.substring(name.length() + 1);
        }
        return null;
    }

    private static Map<String, String> godOf(JsonObject body) {
        Map<String, String> m = new HashMap<>();
        if (body.has("god") && body.get("god").isJsonPrimitive()) m.put("god", body.get("god").getAsString());
        return m;
    }

    private static JsonObject readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] data = in.readNBytes(4 << 20);
            JsonElement e = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            if (!e.isJsonObject()) throw new IllegalArgumentException("body must be a JSON object");
            return e.getAsJsonObject();
        } catch (com.google.gson.JsonParseException e) {
            throw new IllegalArgumentException("body is not JSON");
        }
    }

    private static Realm realm(Map<String, String> q) {
        String id = q.get("god");
        Realm r = id == null ? Realm.primary() : Realm.byId(id);
        if (r == null) throw new IllegalArgumentException("no god " + id);
        return r;
    }

    private static JsonObject thoughts(Map<String, String> q) {
        long since = parseLong(q.get("since"), 0);
        JsonObject o = new JsonObject();
        o.addProperty("rev", MindTrace.revision());
        com.google.gson.JsonArray all = new com.google.gson.JsonArray();
        List<String> ids = new ArrayList<>();
        String god = q.get("god");
        if (god != null && !god.isBlank()) ids.add(god);
        else Realm.all().forEach(r -> ids.add(r.id));
        for (String id : ids) {
            MindTrace.GodTrace t = MindTrace.godIfAny(id);
            if (t != null) all.addAll(TraceJson.thoughts(id, t.since(since)));
        }
        o.add("thoughts", all);
        return o;
    }

    private static MindTrace.Thought findThought(Map<String, String> q) {
        MindTrace.GodTrace t = MindTrace.godIfAny(q.getOrDefault("god", ""));
        MindTrace.Thought th = t == null ? null : t.find(parseLong(q.get("id"), -1));
        if (th == null) throw new IllegalArgumentException("thought no longer kept");
        return th;
    }

    /** One thought with its results in full. */
    private static JsonObject fullThought(Map<String, String> q) {
        MindTrace.Thought th = findThought(q);
        JsonObject o = TraceJson.thought(q.get("god"), th);
        com.google.gson.JsonArray items = o.getAsJsonArray("items");
        List<MindTrace.Item> list = th.items();
        for (int i = 0; i < items.size() && i < list.size(); i++) {
            if (list.get(i).result != null) items.get(i).getAsJsonObject().addProperty("result", list.get(i).result);
        }
        return o;
    }

    private static void image(HttpExchange ex, Map<String, String> q) throws IOException {
        q.putIfAbsent("id", q.get("thought"));
        MindTrace.Thought th = findThought(q);
        MindTrace.Item it = th.item(parseLong(q.get("item"), -1));
        byte[] png = it == null ? null : it.png();
        if (png == null) {
            send(ex, 404, error("image no longer kept"));
            return;
        }
        ex.getResponseHeaders().add("Content-Type", "image/png");
        ex.getResponseHeaders().add("Cache-Control", "private, max-age=3600");
        ex.sendResponseHeaders(200, png.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(png);
        }
    }

    // ---- HTTP helpers -------------------------------------------------------------------------------

    private static void send(HttpExchange ex, int status, JsonObject body) throws IOException {
        byte[] data = redactor().apply(GSON.toJson(body)).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    static Map<String, String> query(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null) return out;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            try {
                if (i > 0) {
                    out.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
                } else if (!pair.isEmpty()) {
                    out.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
                }
            } catch (IllegalArgumentException e) {
                // a malformed pair is skipped
            }
        }
        return out;
    }

    private static long parseLong(String s, long fallback) {
        try {
            return s == null ? fallback : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("error", message);
        return o;
    }
}
