package net.bigyous.gptgodmc.client;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.gui.MenuModel.Kind;

/**
 * What the God Manager screen may ask of the plugin: parsing, limits and the list of actions. Pure (no
 * Bukkit), so hostile input is unit tested: oversized, malformed or unknown requests never reach the
 * code that carries actions out.
 */
public final class ModGuiRequests {
    private ModGuiRequests() {
    }

    /** Client messages larger than this are dropped. */
    public static final int MAX_BYTES = 8 * 1024;
    /** Client messages a second per player (a bucket that refills continuously). */
    public static final double RATE = 20;
    static final int MAX_ARGS = 8;
    static final int MAX_VALUE = 1200;

    public static final String OPEN = "gui_open";
    public static final String SUB = "gui_sub";
    public static final String ACT = "gui_act";
    public static final String CLOSE = "gui_close";

    /** The parts a screen may subscribe to. */
    public static final Set<String> PARTS = Set.of("pantheon", "god", "commands", "mortals", "journal", "looks",
            "config", "switches", "costs");

    /** How an action must be confirmed before it is carried out. */
    public enum Confirm {
        NONE,
        /** confirm must be "true" (the button's Confirm was clicked). */
        YES,
        /** confirm must be the god's name, typed. */
        NAME
    }

    /**
     * One action of the screen.
     *
     * @param kind the chest menu's click it stands for (the same code carries both out), or null for
     *             the two edits only the screen and the mind viewer have (a mortal's title and summary)
     * @param args the arguments it needs
     */
    public record Spec(Kind kind, Confirm confirm, String... args) {
    }

    /** Every action, by name. */
    public static final Map<String, Spec> ACTIONS = actions();

    private static Map<String, Spec> actions() {
        Map<String, Spec> m = new LinkedHashMap<>();
        m.put("wake", new Spec(Kind.WAKE, Confirm.NONE));
        m.put("pause", new Spec(Kind.TOGGLE_PAUSE, Confirm.NONE));
        m.put("dormant", new Spec(Kind.TOGGLE_DORMANT, Confirm.NONE));
        m.put("mood", new Spec(Kind.MOOD, Confirm.NONE, "mood"));
        m.put("calm_heart", new Spec(Kind.CALM_HEART, Confirm.NONE));
        m.put("summon", new Spec(Kind.SUMMON, Confirm.NONE));
        m.put("fly_to", new Spec(Kind.FLY_TO, Confirm.NONE));
        m.put("follow", new Spec(Kind.FOLLOW, Confirm.NONE));
        m.put("vanish", new Spec(Kind.VANISH, Confirm.NONE));
        m.put("true_form", new Spec(Kind.TRUE_FORM, Confirm.YES));
        m.put("end_true_form", new Spec(Kind.END_TRUE_FORM, Confirm.NONE));
        m.put("look", new Spec(Kind.LOOK, Confirm.NONE, "look"));
        m.put("voice", new Spec(Kind.VOICE, Confirm.NONE, "voice"));
        m.put("voice_effect", new Spec(Kind.VOICE_EFFECT, Confirm.NONE, "effect"));
        m.put("voice_sample", new Spec(Kind.VOICE_SAMPLE, Confirm.NONE));
        m.put("dial", new Spec(Kind.DIAL, Confirm.NONE, "key", "value"));
        m.put("power", new Spec(Kind.POWER, Confirm.NONE, "power"));
        m.put("model", new Spec(Kind.MODEL, Confirm.NONE, "model"));
        m.put("effort", new Spec(Kind.EFFORT, Confirm.NONE, "effort"));
        m.put("rebirth", new Spec(Kind.PERSONA, Confirm.NAME, "archetype"));
        m.put("remove_god", new Spec(Kind.REMOVE_GOD, Confirm.NAME));
        m.put("create_god", new Spec(Kind.DRAFT_CREATE, Confirm.NONE, "archetype", "power"));
        m.put("command", new Spec(Kind.DELIVER, Confirm.NONE, "text"));
        m.put("preset", new Spec(Kind.PRESET, Confirm.NONE, "id"));
        m.put("cancel_command", new Spec(Kind.CANCEL_COMMAND, Confirm.NONE, "n"));
        m.put("mortal_open", new Spec(Kind.NAME_PLAYER, Confirm.NONE, "player"));
        m.put("favor", new Spec(Kind.FAVOR, Confirm.NONE, "player", "value"));
        m.put("note_add", new Spec(Kind.ADD_NOTE, Confirm.NONE, "player", "text"));
        m.put("deed_add", new Spec(Kind.ADD_DEED, Confirm.NONE, "player", "text"));
        m.put("note_del", new Spec(Kind.DELETE_NOTE, Confirm.NONE, "player", "at", "text"));
        m.put("deed_del", new Spec(Kind.DELETE_DEED, Confirm.NONE, "player", "at", "text"));
        m.put("notes_clear", new Spec(Kind.CLEAR_NOTES, Confirm.YES, "player"));
        m.put("deeds_clear", new Spec(Kind.CLEAR_DEEDS, Confirm.YES, "player"));
        m.put("forget", new Spec(Kind.FORGET_PLAYER, Confirm.YES, "player"));
        m.put("title_set", new Spec(null, Confirm.NONE, "player"));
        m.put("summary_set", new Spec(null, Confirm.NONE, "player"));
        m.put("switch", new Spec(Kind.TOGGLE_SETTING, Confirm.NONE, "key"));
        m.put("config_set", new Spec(Kind.TUNE, Confirm.NONE, "key", "value"));
        return java.util.Collections.unmodifiableMap(m);
    }

    /**
     * One parsed request. Every string is clipped and free of control characters.
     *
     * @param id      the client's request id (echoed in the result), "" when none
     * @param confirm the typed name or "true", or null
     */
    public record Request(String type, String id, String realm, String part, String action, Map<String, String> args,
            String confirm) {
        public String arg(String key) {
            return args.get(key);
        }

        /** The argument, or "" when absent. */
        public String text(String key) {
            String v = args.get(key);
            return v == null ? "" : v;
        }
    }

    /** Parses a client message, or returns null when it is too large, not JSON or not one of ours. */
    public static Request parse(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) return null;
        try {
            JsonElement root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return null;
            JsonObject o = root.getAsJsonObject();
            String type = str(o, "t", 16);
            if (type == null || !Set.of(OPEN, SUB, ACT, CLOSE).contains(type)) return null;
            String part = str(o, "part", 24);
            String action = str(o, "action", 32);
            if (type.equals(SUB) && (part == null || !PARTS.contains(part))) return null;
            if (type.equals(ACT) && (action == null || !ACTIONS.containsKey(action))) return null;
            Map<String, String> args = new LinkedHashMap<>();
            if (o.has("args") && o.get("args").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("args").entrySet()) {
                    if (args.size() >= MAX_ARGS) break;
                    if (!e.getValue().isJsonPrimitive() || e.getKey().length() > 24) continue;
                    args.put(e.getKey(), clean(e.getValue().getAsString(), MAX_VALUE));
                }
            }
            String id = str(o, "id", 24);
            return new Request(type, id == null ? "" : id, str(o, "realm", 64), part, action,
                    java.util.Collections.unmodifiableMap(args), str(o, "confirm", 64));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The first argument the action needs and the request lacks (blank counts as missing), or null. */
    public static String missing(Request r) {
        Spec spec = ACTIONS.get(r.action());
        if (spec == null) return null;
        for (String a : spec.args()) {
            String v = r.arg(a);
            if (v == null || v.isBlank()) return a;
        }
        return null;
    }

    /**
     * What a confirmation is still missing: null when the action may go ahead, else "confirm" or "name".
     *
     * @param godName the name that must be typed for a NAME confirmation (the god's name, or its id
     *                while it is unborn)
     */
    public static String needs(Spec spec, String confirm, String godName) {
        return switch (spec.confirm()) {
            case NONE -> null;
            case YES -> "true".equalsIgnoreCase(confirm) ? null : "confirm";
            case NAME -> confirm != null && godName != null && confirm.trim().equalsIgnoreCase(godName.trim()) ? null
                    : "name";
        };
    }

    private static String str(JsonObject o, String key, int max) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return null;
        return clean(e.getAsString(), max);
    }

    /** Clips, and drops control characters and legacy colour codes. */
    static String clean(String s, int max) {
        StringBuilder b = new StringBuilder(Math.min(s.length(), max));
        for (int i = 0; i < s.length() && b.length() < max; i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\t') b.append(' ');
            else if (c >= 0x20 && c != 0x7F && c != '§') b.append(c);
        }
        return b.toString().trim();
    }

    /** A token bucket per player for client messages. */
    public static final class Limiter {
        private final Map<UUID, double[]> buckets = new ConcurrentHashMap<>();

        /** Whether this player may send one more message now. */
        public boolean allow(UUID player, long nowMs) {
            double[] b = buckets.computeIfAbsent(player, k -> new double[] { RATE, -1 });
            synchronized (b) {
                if (b[1] >= 0) b[0] = Math.min(RATE, b[0] + Math.max(0, nowMs - b[1]) / 1000.0 * RATE);
                b[1] = nowMs;
                if (b[0] < 1) return false;
                b[0] -= 1;
                return true;
            }
        }

        public void forget(UUID player) {
            buckets.remove(player);
        }
    }
}
