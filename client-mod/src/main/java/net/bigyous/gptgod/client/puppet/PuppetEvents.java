package net.bigyous.gptgod.client.puppet;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.bigyous.gptgod.client.net.Payloads;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * What this player perceives, as a numbered log the harness reads with GET /events: chat, system messages,
 * action bar, titles, deaths, respawns, disconnects, and one short line per message from the GPTGOD plugin.
 */
final class PuppetEvents {
    private static final int KEEP = 2000;
    private static final int MAX_TEXT = 2000;
    private static final ArrayDeque<Event> RING = new ArrayDeque<>();
    private static long seq;

    private record Event(long seq, long tick, String kind, String text, String sender) {
    }

    private PuppetEvents() {
    }

    static void add(String kind, String text) {
        add(kind, text, null);
    }

    static synchronized void add(String kind, String text, String sender) {
        String t = text == null ? "" : text;
        if (t.length() > MAX_TEXT) t = t.substring(0, MAX_TEXT) + "…";
        RING.addLast(new Event(++seq, PuppetTasks.now, kind, t, sender));
        while (RING.size() > KEEP) RING.removeFirst();
    }

    static synchronized long latest() {
        return seq;
    }

    /** {events:[{seq,tick,kind,text[,sender]}], latest}: the events after seq "since", oldest first, at most "limit". */
    static synchronized JsonObject since(long since, int limit) {
        int max = Math.max(1, Math.min(limit, KEEP));
        JsonArray list = new JsonArray();
        for (Event e : RING) {
            if (e.seq <= since) continue;
            if (list.size() >= max) break;
            JsonObject o = new JsonObject();
            o.addProperty("seq", e.seq);
            o.addProperty("tick", e.tick);
            o.addProperty("kind", e.kind);
            o.addProperty("text", e.text);
            if (e.sender != null) o.addProperty("sender", e.sender);
            list.add(o);
        }
        JsonObject out = PuppetServer.ok();
        out.add("events", list);
        out.addProperty("latest", seq);
        return out;
    }

    // ---- listeners (registered only in puppet mode) ----

    static void register() {
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> add("chat", message.getString(),
                sender == null ? null : sender.name()));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) add("system", message.getString());
            else if (overlayField == null) actionBar(message.getString()); // otherwise the Hud is read every tick, which sees every action bar
        });
        ClientReceiveMessageEvents.TITLE.register(title -> add("title", title.getString()));
        ClientReceiveMessageEvents.SUBTITLE.register(subtitle -> add("subtitle", subtitle.getString()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var server = client.getCurrentServer();
            add("info", "joined " + (server == null ? "a world" : server.ip));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> left = PuppetTasks.now);
        watchModMessages();
    }

    /**
     * The mod's own receiver for gptgod:main stays as it is; in puppet mode it is wrapped so each incoming message
     * also leaves a line here. Must run after the mod registered its receiver.
     */
    @SuppressWarnings("unchecked")
    private static void watchModMessages() {
        try {
            ClientPlayNetworking.PlayPayloadHandler<?> mod = ClientPlayNetworking.unregisterGlobalReceiver(Payloads.MAIN_ID);
            if (mod == null) return;
            ClientPlayNetworking.PlayPayloadHandler<Payloads.Main> inner = (ClientPlayNetworking.PlayPayloadHandler<Payloads.Main>) mod;
            boolean wrapped = false;
            try {
                wrapped = ClientPlayNetworking.registerGlobalReceiver(Payloads.Main.TYPE, (payload, context) -> {
                    try {
                        mod(payload.data());
                    } catch (RuntimeException e) {
                        // never let the log break the mod
                    }
                    inner.receive(payload, context);
                });
            } finally {
                if (!wrapped) ClientPlayNetworking.registerGlobalReceiver(Payloads.Main.TYPE, inner); // the mod keeps its receiver, whatever happens
            }
        } catch (RuntimeException e) {
            PuppetServer.LOG.warn("[gptgod-puppet] mod messages will not be logged", e);
        }
    }

    private static final Pattern TYPE = Pattern.compile("\"t\"\\s*:\\s*\"([^\"]{0,40})\"");
    /** Sent again and again with small changes: one line every five seconds is enough. */
    private static final Set<String> CHATTY = Set.of("god", "voice", "ping", "gui");
    private static final Map<String, Long> lastMod = new HashMap<>();

    private static void mod(byte[] data) {
        String head = new String(data, 0, Math.min(data.length, 600), StandardCharsets.UTF_8);
        Matcher m = TYPE.matcher(head);
        String type = m.find() ? m.group(1) : "?";
        if (CHATTY.contains(type)) {
            Long last = lastMod.get(type);
            if (last != null && PuppetTasks.now - last < 100) return;
            lastMod.put(type, PuppetTasks.now);
        }
        add("mod", type + " " + (head.length() > 200 ? head.substring(0, 200) + "…" : head));
    }

    // ---- things only seen by looking every tick ----

    private static boolean fieldsFound;
    private static Field overlayField;
    private static Field deathCauseField;
    private static Object lastOverlay;
    private static String lastBar;
    private static long lastBarTick;
    private static boolean wasDead;
    private static boolean deathTold;
    private static long deadSince;
    private static long left = -1;
    private static Screen lastDisconnectScreen;

    /** A private field of the game read by name (the game is not obfuscated); null if this version has no such field. */
    private static Field field(Class<?> owner, String name) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            PuppetServer.LOG.warn("[gptgod-puppet] cannot read {}.{}: {}", owner.getSimpleName(), name, e.toString());
            return null;
        }
    }

    private static Object read(Field f, Object from) {
        try {
            return f == null || from == null ? null : f.get(from);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** The same line is often sent again every few ticks to keep it on screen: that is one event. */
    private static void actionBar(String text) {
        boolean repeat = text.equals(lastBar) && PuppetTasks.now - lastBarTick <= 60;
        lastBar = text;
        lastBarTick = PuppetTasks.now;
        if (!repeat && !text.isEmpty()) add("actionbar", text);
    }

    /** End of every client tick: the action bar, death and respawn (with automatic respawn), and disconnects. */
    static void tick(Minecraft mc) {
        long now = PuppetTasks.now;
        if (!fieldsFound) {
            fieldsFound = true;
            overlayField = field(Hud.class, "overlayMessageString");
            deathCauseField = field(DeathScreen.class, "causeOfDeath");
        }
        Object overlay = read(overlayField, mc.gui.hud);
        if (overlay != lastOverlay) {
            lastOverlay = overlay;
            if (overlay instanceof Component c) actionBar(c.getString());
        }

        Screen screen = mc.gui.screen();
        boolean dead = mc.player != null && mc.player.isDeadOrDying();
        if (dead && !wasDead) {
            deadSince = now;
            deathTold = false;
        }
        if (dead && !deathTold && (screen instanceof DeathScreen || now - deadSince >= 10)) {
            deathTold = true;
            Object cause = screen instanceof DeathScreen ? read(deathCauseField, screen) : null;
            add("death", cause instanceof Component c ? c.getString() : "");
        }
        if (dead && PuppetTasks.AUTO_RESPAWN && now - deadSince >= 40 && (now - deadSince) % 40 == 0) {
            mc.player.respawn(); // asked again every two seconds until the server answers
        }
        if (!dead && wasDead && mc.player != null) add("respawn", "");
        wasDead = dead;

        if (screen instanceof DisconnectedScreen && screen != lastDisconnectScreen) {
            lastDisconnectScreen = screen;
            left = -1;
            add("disconnect", screen.getNarrationMessage().getString());
        } else if (left >= 0 && now - left > 20) {
            left = -1;
            add("disconnect", "left the server");
        }
    }
}
