package net.bigyous.gptgodmc.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.GodClient;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Journal;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Perception;
import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.Sync;
import net.bigyous.gptgodmc.god.tools.GodTool;
import net.bigyous.gptgodmc.god.tools.ToolException;
import net.bigyous.gptgodmc.god.tools.ToolRegistry;
import net.bigyous.gptgodmc.god.vision.Vision;
import net.bigyous.gptgodmc.loggables.GenericEventLoggable;
import net.bigyous.gptgodmc.utils.TokenTracker;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * A small HTTP API for tests and for "external" god mode, where an outside agent plays the god.
 * Off by default; requires a token; local-only unless bridge.bind says otherwise.
 */
public final class DebugBridge {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static HttpServer server;
    private static String token;

    private DebugBridge() {
    }

    public static void start() {
        if (!GodSettings.bridgeEnabled()) return;
        try {
            token = GodSettings.bridgeToken();
            if (token == null || token.isBlank()) {
                byte[] b = new byte[24];
                new SecureRandom().nextBytes(b);
                token = HexFormat.of().formatHex(b);
            }
            String bind = GodSettings.bridgeBind();
            int port = GodSettings.bridgePort();
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(bind), port), 0);
            server.setExecutor(Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "god-bridge");
                t.setDaemon(true);
                return t;
            }));
            server.createContext("/", DebugBridge::handle);
            server.start();
            JsonObject info = new JsonObject();
            info.addProperty("port", port);
            info.addProperty("token", token);
            info.addProperty("host", bind);
            Path file = GPTGOD.get().getDataFolder().toPath().resolve("bridge.json");
            Files.writeString(file, GSON.toJson(info), StandardCharsets.UTF_8);
            GPTGOD.LOGGER.info("Debug bridge listening on " + bind + ":" + port + " (token in " + file + ")");
            if (!bind.startsWith("127.") && !bind.equals("localhost")) {
                GPTGOD.LOGGER.warn("The debug bridge is reachable from the network and can run console commands. "
                        + "Keep the token secret and consider bridge.allowed-ips.");
            }
        } catch (IOException e) {
            GPTGOD.LOGGER.error("Could not start the debug bridge", e);
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private static void handle(HttpExchange ex) throws IOException {
        try {
            if (!allowed(ex)) {
                send(ex, 403, error("forbidden"));
                return;
            }
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            String expected = "Bearer " + token;
            if (auth == null || !MessageDigest.isEqual(auth.getBytes(StandardCharsets.UTF_8),
                    expected.getBytes(StandardCharsets.UTF_8))) {
                send(ex, 401, error("missing or wrong bearer token"));
                return;
            }
            String path = ex.getRequestURI().getPath();
            Map<String, String> q = query(ex.getRequestURI().getRawQuery());
            String method = ex.getRequestMethod();
            JsonObject body = "POST".equals(method) ? readBody(ex) : new JsonObject();
            switch (method + " " + path) {
                case "GET /status" -> send(ex, 200, status());
                case "GET /journal" -> send(ex, 200, journal(q));
                case "GET /perception" -> send(ex, 200, perception("true".equalsIgnoreCase(q.get("consume"))));
                case "GET /tools" -> send(ex, 200, tools());
                case "GET /memory" -> send(ex, 200, memory());
                case "GET /view" -> view(ex, q);
                case "POST /wake" -> {
                    God.wake(str(body, "directive"));
                    JsonObject o = ok();
                    o.addProperty("queued", true);
                    send(ex, 200, o);
                }
                case "POST /tool" -> {
                    String name = str(body, "name");
                    JsonObject input = body.has("input") && body.get("input").isJsonObject()
                            ? body.getAsJsonObject("input") : new JsonObject();
                    ToolRegistry.Outcome out = ToolRegistry.execute(name, input, "bridge");
                    JsonObject o = ok();
                    o.addProperty("isError", out.result().isError());
                    o.addProperty("result", out.result().text());
                    o.addProperty("hasImage", out.result().hasImage());
                    send(ex, 200, o);
                }
                case "POST /event" -> {
                    String text = str(body, "text");
                    if (text == null || text.isBlank()) {
                        send(ex, 400, error("text is required"));
                        return;
                    }
                    EventLogger.addLoggable(new GenericEventLoggable(text, "an observed event"));
                    send(ex, 200, ok());
                }
                case "POST /console" -> send(ex, 200, console(str(body, "command")));
                case "POST /memory/reset" -> {
                    Memory.reset();
                    send(ex, 200, ok());
                }
                default -> send(ex, 404, error("unknown endpoint " + method + " " + path));
            }
        } catch (ToolException e) {
            send(ex, 400, error(e.getMessage()));
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("Bridge request failed", e);
            send(ex, 500, error(e.toString()));
        } finally {
            ex.close();
        }
    }

    private static boolean allowed(HttpExchange ex) {
        InetAddress remote = ex.getRemoteAddress().getAddress();
        if (remote.isLoopbackAddress()) return true;
        List<String> allow = GPTGOD.get().getConfig().getStringList("bridge.allowed-ips");
        if (allow.isEmpty()) return true;
        return allow.contains(remote.getHostAddress());
    }

    private static JsonObject status() throws ToolException {
        return Sync.call(() -> {
            JsonObject o = new JsonObject();
            o.addProperty("awake", God.isAwake());
            o.addProperty("paused", God.isPaused());
            o.addProperty("mode", GodSettings.brainMode());
            o.addProperty("model", GodSettings.model());
            o.addProperty("effort", GodSettings.effort());
            o.addProperty("thinking", God.brain() != null && God.brain().isThinking());
            o.addProperty("keyConfigured", GodClient.hasCredentials());
            o.addProperty("lastError", God.brain() == null ? null : God.brain().lastError());
            JsonObject persona = new JsonObject();
            Persona p = Persona.current();
            if (p != null) {
                persona.addProperty("name", p.name);
                persona.addProperty("epithet", p.epithet);
                persona.addProperty("archetype", p.archetype);
            }
            o.add("persona", persona);
            JsonObject vessel = new JsonObject();
            Location v = God.vessel().location();
            vessel.addProperty("manifested", v != null);
            if (v != null) {
                vessel.addProperty("world", v.getWorld().getName());
                vessel.addProperty("x", v.getX());
                vessel.addProperty("y", v.getY());
                vessel.addProperty("z", v.getZ());
            }
            vessel.addProperty("description", God.vessel().describe());
            o.add("vessel", vessel);
            JsonArray players = new JsonArray();
            for (Player pl : Bukkit.getOnlinePlayers()) {
                JsonObject j = new JsonObject();
                j.addProperty("name", pl.getName());
                j.addProperty("world", pl.getWorld().getName());
                j.addProperty("x", pl.getLocation().getX());
                j.addProperty("y", pl.getLocation().getY());
                j.addProperty("z", pl.getLocation().getZ());
                j.addProperty("health", pl.getHealth());
                j.addProperty("gamemode", pl.getGameMode().name().toLowerCase());
                players.add(j);
            }
            o.add("players", players);
            JsonObject usage = new JsonObject();
            usage.addProperty("calls", TokenTracker.getApiCallCount());
            usage.addProperty("inputTokens", TokenTracker.getTotalInputTokens());
            usage.addProperty("outputTokens", TokenTracker.getTotalOutputTokens());
            usage.addProperty("cacheReadTokens", TokenTracker.getCacheReadTokens());
            usage.addProperty("cacheWriteTokens", TokenTracker.getCacheWriteTokens());
            usage.addProperty("estimatedCostUsd", TokenTracker.getEstimatedCost());
            o.add("usage", usage);
            o.addProperty("pendingEvents", EventLogger.pendingCount());
            o.addProperty("journalSeq", Journal.latestSeq());
            return o;
        }, 5000);
    }

    private static JsonObject journal(Map<String, String> q) {
        long since = parseLong(q.get("since"), 0);
        int limit = (int) Math.max(1, Math.min(500, parseLong(q.get("limit"), 100)));
        List<Journal.Entry> entries = Journal.firstAfter(since, limit + 1);
        boolean more = entries.size() > limit;
        if (more) entries = entries.subList(0, limit);
        JsonArray arr = new JsonArray();
        for (Journal.Entry e : entries) {
            JsonObject j = new JsonObject();
            j.addProperty("seq", e.seq());
            j.addProperty("time", e.time());
            j.addProperty("kind", e.kind().id());
            j.addProperty("text", e.text());
            arr.add(j);
        }
        JsonObject o = new JsonObject();
        o.add("entries", arr);
        o.addProperty("latestSeq", Journal.latestSeq());
        o.addProperty("more", more);
        return o;
    }

    private static JsonObject perception(boolean consume) throws ToolException {
        String text = Sync.call(() -> consume ? Perception.build("an outside mind is looking through your eyes",
                EventLogger.drain()) : Perception.build("preview (events not consumed)", EventLogger.peek(), true), 8000);
        if (consume) {
            Journal.add(Journal.Kind.PERCEPTION, text);
        }
        JsonObject o = new JsonObject();
        o.addProperty("text", text);
        return o;
    }

    private static JsonObject tools() {
        JsonArray arr = new JsonArray();
        for (GodTool t : ToolRegistry.all()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", t.name());
            j.addProperty("description", t.description());
            j.add("input_schema", GSON.toJsonTree(t.schema().toMap()));
            arr.add(j);
        }
        JsonObject o = new JsonObject();
        o.add("tools", arr);
        return o;
    }

    private static JsonObject memory() throws ToolException {
        return Sync.call(() -> {
            Memory m = Memory.get();
            List<String> online = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> online.add(p.getName()));
            JsonObject o = new JsonObject();
            o.addProperty("chronicle", m.renderChronicle());
            o.addProperty("ledger", m.renderLedger(online));
            o.addProperty("quests", m.renderQuests());
            o.addProperty("recent", m.renderRecent());
            o.addProperty("works", m.renderWorks());
            return o;
        }, 5000);
    }

    private static void view(HttpExchange ex, Map<String, String> q) throws IOException, ToolException {
        Vision.Result r = Vision.render(new Vision.Request(Vision.View.parse(q.getOrDefault("type", "overhead")),
                q.get("target"), "true".equalsIgnoreCase(q.get("xray")), (int) parseLong(q.get("radius"), 0),
                q.getOrDefault("layer", "auto")));
        ex.getResponseHeaders().add("Content-Type", "image/png");
        ex.getResponseHeaders().add("X-View-Caption", URLEncoder.encode(r.caption(), StandardCharsets.UTF_8));
        ex.sendResponseHeaders(200, r.png().length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(r.png());
        }
    }

    private static JsonObject console(String command) throws ToolException {
        if (command == null || command.isBlank()) {
            throw new ToolException("command is required");
        }
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        List<String> feedback = new ArrayList<>();
        boolean ok = Sync.call(() -> {
            CommandSender sender = Bukkit.createCommandSender(c -> feedback.add(
                    PlainTextComponentSerializer.plainText().serialize(c)));
            try {
                return Bukkit.dispatchCommand(sender, cmd);
            } catch (RuntimeException e) {
                // A command that throws (some vanilla syntax errors do) is a failed command, not a
                // failed request: report why instead of answering HTTP 400.
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                String why = cause.getMessage();
                feedback.add("Command failed: " + (why == null || why.isBlank() ? cause.toString() : why));
                return false;
            }
        }, 10_000);
        JsonObject o = new JsonObject();
        o.addProperty("ok", ok);
        o.add("feedback", GSON.toJsonTree(feedback));
        Journal.add(Journal.Kind.INFO, "Bridge ran console command: " + cmd);
        return o;
    }

    // ---- HTTP helpers ------------------------------------------------------------------------------

    private static JsonObject readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] data = in.readNBytes(1 << 20);
            if (data.length == 0) return new JsonObject();
            JsonElement e = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private static void send(HttpExchange ex, int status, JsonObject body) throws IOException {
        byte[] data = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null) return out;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                out.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    private static long parseLong(String s, long fallback) {
        try {
            return s == null ? fallback : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static JsonObject ok() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        return o;
    }

    private static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("error", message);
        return o;
    }
}
