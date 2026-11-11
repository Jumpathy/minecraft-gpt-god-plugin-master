package net.bigyous.gptgod.client.gui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The God Manager screen's half of the protocol: decodes the plugin's gui_* messages into records and
 * writes the screen's requests. Pure. Never throws: every number is clamped, every string clipped and
 * every list capped, so a broken or hostile server cannot crash the screen.
 */
public final class GuiProtocol {
    private GuiProtocol() {
    }

    public static final int MAX_BYTES = 64 * 1024;

    // ---- What the plugin sends -------------------------------------------------------------------

    public record Open(String realm, boolean several, boolean costs, boolean pantheon) {
    }

    /** needs: "name", "confirm" or null. value: something the screen asked for (a new god's id, a player name). */
    public record Result(String id, boolean ok, String field, String error, String message, String needs,
            String value) {
    }

    public record Note(String text, String level) {
    }

    /** heart: wrath, joy, sorrow, dread, glory (0..100). health/max are NaN unless it is wounded. */
    public record God(String id, String name, String epithet, String archetype, int color, String power, String state,
            boolean thinking, String mood, float[] heart, String body, int pending, float health, float max,
            String look, boolean removable) {
        public String label() {
            return name == null ? "Unborn god" : name;
        }

        public boolean wounded() {
            return !Float.isNaN(health) && !Float.isNaN(max) && max > 0;
        }
    }

    public record Pantheon(List<God> gods) {
    }

    /** Head faces by look: size x size ARGB pixels, row by row. */
    public record Heads(int size, Map<String, int[]> pixels) {
    }

    public record Dial(String key, int value, int base) {
    }

    public record GodDetail(String realm, God god, boolean born, boolean awake, boolean paused, boolean dormant,
            List<String> heartLines, String model, String effort, String configModel, String configEffort,
            String lookSetting, String voice, String effect, String engine, boolean voiceOn, String gaze, boolean bodyOn,
            boolean trueFormAllowed, List<Dial> dials, int moodMinutes, List<String> awareLines) {
    }

    public record Command(int n, String text, boolean standing, String status, String issuer) {
    }

    public record Report(long seq, long time, String text) {
    }

    public record Commands(String realm, List<Command> pending, List<Report> reports) {
    }

    public record MortalRow(String name, int favor, String tier, boolean online, String title, String seen, int notes,
            int deeds) {
    }

    public record Mortals(String realm, String query, int page, int pages, int total, List<MortalRow> rows) {
    }

    public record Entry(long at, String text, String age) {
    }

    /** name null = nobody is open. */
    public record Mortal(String realm, String name, int favor, String tier, boolean online, String title,
            String summary, int offerings, String seen, List<Entry> notes, List<Entry> deeds, int notesTotal,
            int deedsTotal) {
    }

    public record Line(long seq, long time, String kind, String text) {
    }

    public record Journal(String realm, boolean reset, List<Line> lines) {
    }

    public record Option(String key, String label, String type, double min, double max, double step,
            List<String> choices, String fallback, String unit, String help, boolean restart, boolean locked,
            String value) {
        public boolean number() {
            return type.equals("int") || type.equals("double");
        }
    }

    public record Config(String tab, List<Option> options) {
    }

    public record Switch(String key, String label, String help, boolean on) {
    }

    public record Switches(List<Switch> list) {
    }

    public record CostGod(String id, String name, double hour, double day, double session) {
    }

    /** off = this operator may not see costs. total is NaN when unknown. */
    public record Costs(boolean off, double minute, double hour, double day, double session, double total,
            boolean complete, long requests, long input, long output, long cacheRead, long cacheWrite,
            List<CostGod> gods, double[] spark) {
    }

    /** color is -1 where the choice has none. */
    public record Named(String key, String text, int color) {
    }

    public record Preset(String id, String name, String text, boolean confirm) {
    }

    public record DialEnds(String key, String low, String high) {
    }

    public record Catalog(List<Named> archetypes, List<Named> looks, List<Named> voices, List<Named> effects,
            List<Preset> presets, List<Named> moods, List<Named> powers, List<String> models, List<String> efforts,
            List<DialEnds> dials, List<String> tabs) {
        public static final Catalog EMPTY = new Catalog(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of());

        public DialEnds dial(String key) {
            for (DialEnds d : dials) if (d.key().equals(key)) return d;
            return new DialEnds(key, "Low.", "High.");
        }
    }

    /** Whether these bytes are a gui_* message (cheap check before parsing twice). */
    public static boolean mine(byte[] data) {
        if (data == null || data.length < 12 || data.length > MAX_BYTES) return false;
        String head = new String(data, 0, Math.min(data.length, 24), StandardCharsets.UTF_8);
        return head.startsWith("{\"t\":\"gui_");
    }

    /** One of the records above, or null when the bytes are not a gui message we know. */
    public static Object decode(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) return null;
        try {
            JsonElement root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            return root.isJsonObject() ? decode(root.getAsJsonObject()) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Object decode(JsonObject o) {
        try {
            return switch (str(o, "t", "", 16)) {
                case "gui_open" -> new Open(str(o, "realm", "main", 64), bool(o, "several"), bool(o, "costs"),
                        bool(o, "pantheon"));
                case "gui_result" -> new Result(str(o, "id", "", 24), bool(o, "ok"), str(o, "field", null, 64),
                        str(o, "error", null, 400), str(o, "message", null, 600), str(o, "needs", null, 16),
                        str(o, "value", null, 64));
                case "gui_note" -> new Note(str(o, "text", "", 600), str(o, "level", "info", 16));
                case "gui_data" -> data(o);
                default -> null;
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Object data(JsonObject o) {
        String realm = str(o, "realm", null, 64);
        return switch (str(o, "part", "", 24)) {
            case "pantheon" -> {
                List<God> gods = new ArrayList<>();
                for (JsonObject g : objects(o, "gods", 32)) gods.add(god(g));
                yield new Pantheon(List.copyOf(gods));
            }
            case "heads" -> heads(o);
            case "god" -> {
                List<Dial> dials = new ArrayList<>();
                for (JsonObject d : objects(o, "dials", 16)) {
                    dials.add(new Dial(str(d, "key", "", 32), integer(d, "value", 5, 0, 10), integer(d, "base", 5, 0, 10)));
                }
                JsonObject g = o.has("god") && o.get("god").isJsonObject() ? o.getAsJsonObject("god") : new JsonObject();
                yield new GodDetail(realm == null ? "main" : realm, god(g), bool(o, "born"), bool(o, "awake"),
                        bool(o, "paused"), bool(o, "dormant"), strings(o, "heart_lines", 6, 80), str(o, "model", null, 48),
                        str(o, "effort", null, 24), str(o, "config_model", "?", 48), str(o, "config_effort", "auto", 24),
                        str(o, "look_setting", "auto", 32), str(o, "voice", "", 32), str(o, "effect", "auto", 24),
                        str(o, "engine", "kokoro", 16), bool(o, "voice_on"), str(o, "gaze", "blind", 16),
                        bool(o, "body_on"), bool(o, "true_form_allowed"), List.copyOf(dials),
                        integer(o, "mood_minutes", 10, 1, 120), strings(o, "aware_lines", 3, 200));
            }
            case "commands" -> {
                List<Command> pending = new ArrayList<>();
                for (JsonObject c : objects(o, "pending", 64)) {
                    pending.add(new Command(integer(c, "n", 0, 0, 1_000_000), str(c, "text", "", 300), bool(c, "standing"),
                            str(c, "status", "", 80), str(c, "issuer", null, 48)));
                }
                List<Report> reports = new ArrayList<>();
                for (JsonObject r : objects(o, "reports", 32)) {
                    reports.add(new Report(lng(r, "seq"), lng(r, "time"), str(r, "text", "", 400)));
                }
                yield new Commands(realm, List.copyOf(pending), List.copyOf(reports));
            }
            case "mortals" -> {
                List<MortalRow> rows = new ArrayList<>();
                for (JsonObject m : objects(o, "mortals", 64)) {
                    rows.add(new MortalRow(str(m, "name", "?", 32), integer(m, "favor", 0, -10, 10),
                            str(m, "tier", "unproven", 24), bool(m, "online"), str(m, "title", null, 40),
                            str(m, "seen", null, 16), integer(m, "notes", 0, 0, 100_000),
                            integer(m, "deeds", 0, 0, 100_000)));
                }
                yield new Mortals(realm, str(o, "query", "", 64), integer(o, "page", 0, 0, 10_000),
                        integer(o, "pages", 1, 1, 10_000), integer(o, "total", 0, 0, 1_000_000), List.copyOf(rows));
            }
            case "mortal" -> bool(o, "none") || !o.has("name")
                    ? new Mortal(realm, null, 0, "unproven", false, null, null, 0, null, List.of(), List.of(), 0, 0)
                    : new Mortal(realm, str(o, "name", "?", 32), integer(o, "favor", 0, -10, 10),
                            str(o, "tier", "unproven", 24), bool(o, "online"), str(o, "title", null, 40),
                            str(o, "summary", null, 1200), integer(o, "offerings", 0, 0, 1_000_000),
                            str(o, "seen", null, 16), entries(o, "notes"), entries(o, "deeds"),
                            integer(o, "notes_total", 0, 0, 1_000_000), integer(o, "deeds_total", 0, 0, 1_000_000));
            case "journal" -> {
                List<Line> lines = new ArrayList<>();
                for (JsonObject l : objects(o, "lines", 64)) {
                    lines.add(new Line(lng(l, "seq"), lng(l, "time"), str(l, "kind", "info", 16).toLowerCase(Locale.ROOT),
                            str(l, "text", "", 500)));
                }
                yield new Journal(realm, bool(o, "reset"), List.copyOf(lines));
            }
            case "config" -> {
                List<Option> options = new ArrayList<>();
                for (JsonObject x : objects(o, "options", 64)) {
                    String type = str(x, "type", "bool", 8);
                    if (!List.of("bool", "int", "double", "choice").contains(type)) continue;
                    options.add(new Option(str(x, "key", "", 80), str(x, "label", "", 48), type, num(x, "min", 0, -1e9, 1e9),
                            num(x, "max", 0, -1e9, 1e9), Math.max(1e-6, num(x, "step", 1, 0, 1e9)),
                            strings(x, "choices", 32, 40), str(x, "fallback", "", 48), str(x, "unit", "", 16),
                            str(x, "help", "", 200), bool(x, "restart"), bool(x, "locked"), str(x, "value", "", 48)));
                }
                yield new Config(str(o, "tab", "", 24), List.copyOf(options));
            }
            case "switches" -> {
                List<Switch> list = new ArrayList<>();
                for (JsonObject s : objects(o, "switches", 32)) {
                    list.add(new Switch(str(s, "key", "", 80), str(s, "label", "", 48), str(s, "help", "", 200),
                            bool(s, "on")));
                }
                yield new Switches(List.copyOf(list));
            }
            case "costs" -> costs(o);
            case "catalog" -> {
                List<Preset> presets = new ArrayList<>();
                for (JsonObject p : objects(o, "presets", 16)) {
                    presets.add(new Preset(str(p, "id", "", 32), str(p, "name", "", 48), str(p, "text", "", 300),
                            bool(p, "confirm")));
                }
                List<DialEnds> dials = new ArrayList<>();
                for (JsonObject d : objects(o, "dials", 16)) {
                    dials.add(new DialEnds(str(d, "key", "", 32), str(d, "low", "", 60), str(d, "high", "", 60)));
                }
                yield new Catalog(named(o, "archetypes"), named(o, "looks"), named(o, "voices"), named(o, "effects"),
                        List.copyOf(presets), named(o, "moods"), named(o, "powers"), strings(o, "models", 16, 48),
                        strings(o, "efforts", 16, 24), List.copyOf(dials), strings(o, "tabs", 16, 24));
            }
            default -> null;
        };
    }

    private static God god(JsonObject g) {
        float[] heart = new float[5];
        if (g.has("heart") && g.get("heart").isJsonArray()) {
            JsonArray a = g.getAsJsonArray("heart");
            for (int i = 0; i < 5 && i < a.size(); i++) {
                JsonElement e = a.get(i);
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                    float v = e.getAsFloat();
                    heart[i] = Float.isNaN(v) ? 0 : Math.max(0, Math.min(100, v));
                }
            }
        }
        float health = Float.NaN, max = Float.NaN;
        if (g.has("health") && g.has("max")) {
            max = (float) num(g, "max", 100, 1, 1_000_000);
            health = (float) num(g, "health", 0, 0, max);
        }
        return new God(str(g, "id", "main", 64), str(g, "name", null, 48), str(g, "epithet", null, 80),
                str(g, "archetype", null, 32), color(g, "color", 0xB24BF3), str(g, "power", "supreme", 16),
                str(g, "state", "asleep", 16), bool(g, "thinking"), str(g, "mood", "calm", 16).toLowerCase(Locale.ROOT),
                heart, str(g, "body", "formless", 16), integer(g, "pending", 0, 0, 10_000), health, max,
                str(g, "look", "shade", 32), bool(g, "removable"));
    }

    private static Heads heads(JsonObject o) {
        int size = integer(o, "size", 16, 1, 32);
        Map<String, int[]> out = new LinkedHashMap<>();
        if (o.has("heads") && o.get("heads").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("heads").entrySet()) {
                if (out.size() >= 64 || !e.getValue().isJsonPrimitive() || e.getKey().length() > 32) continue;
                try {
                    byte[] rgb = Base64.getDecoder().decode(e.getValue().getAsString());
                    if (rgb.length != size * size * 3) continue;
                    int[] px = new int[size * size];
                    for (int i = 0; i < px.length; i++) {
                        px[i] = 0xFF000000 | (rgb[i * 3] & 255) << 16 | (rgb[i * 3 + 1] & 255) << 8 | (rgb[i * 3 + 2] & 255);
                    }
                    out.put(e.getKey(), px);
                } catch (IllegalArgumentException bad) {
                    // not base64: skip this head
                }
            }
        }
        return new Heads(size, out);
    }

    private static Costs costs(JsonObject o) {
        if (bool(o, "off")) {
            return new Costs(true, 0, 0, 0, 0, Double.NaN, true, 0, 0, 0, 0, 0, List.of(), new double[0]);
        }
        List<CostGod> gods = new ArrayList<>();
        for (JsonObject g : objects(o, "gods", 32)) {
            gods.add(new CostGod(str(g, "id", "?", 64), str(g, "name", null, 48), usd(g, "hour"), usd(g, "day"),
                    usd(g, "session")));
        }
        double[] spark = new double[0];
        if (o.has("spark") && o.get("spark").isJsonArray()) {
            JsonArray a = o.getAsJsonArray("spark");
            spark = new double[Math.min(120, a.size())];
            for (int i = 0; i < spark.length; i++) {
                JsonElement e = a.get(i);
                double v = e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : 0;
                spark[i] = Double.isNaN(v) ? 0 : Math.max(0, Math.min(1e6, v));
            }
        }
        return new Costs(false, usd(o, "minute"), usd(o, "hour"), usd(o, "day"), usd(o, "session"),
                o.has("total") ? usd(o, "total") : Double.NaN, !o.has("complete") || bool(o, "complete"),
                lng(o, "requests"), lng(o, "input"), lng(o, "output"), lng(o, "cache_read"), lng(o, "cache_write"),
                List.copyOf(gods), spark);
    }

    private static List<Entry> entries(JsonObject o, String key) {
        List<Entry> out = new ArrayList<>();
        for (JsonObject e : objects(o, key, 64)) {
            out.add(new Entry(lng(e, "at"), str(e, "text", "", 240), str(e, "age", null, 16)));
        }
        return List.copyOf(out);
    }

    private static List<Named> named(JsonObject o, String key) {
        List<Named> out = new ArrayList<>();
        for (JsonObject n : objects(o, key, 64)) {
            out.add(new Named(str(n, "key", "", 48), str(n, "text", "", 220), n.has("color") ? color(n, "color", -1) : -1));
        }
        return List.copyOf(out);
    }

    // ---- What the screen sends -------------------------------------------------------------------

    public static byte[] open(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "gui_open");
        o.addProperty("id", id);
        return bytes(o);
    }

    public static byte[] sub(String id, String realm, String part, Map<String, String> args) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "gui_sub");
        o.addProperty("id", id);
        if (realm != null) o.addProperty("realm", realm);
        o.addProperty("part", part);
        if (args != null && !args.isEmpty()) o.add("args", args(args));
        return bytes(o);
    }

    public static byte[] act(String id, String realm, String action, Map<String, String> args, String confirm) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "gui_act");
        o.addProperty("id", id);
        if (realm != null) o.addProperty("realm", realm);
        o.addProperty("action", action);
        if (args != null && !args.isEmpty()) o.add("args", args(args));
        if (confirm != null) o.addProperty("confirm", confirm);
        return bytes(o);
    }

    public static byte[] close() {
        JsonObject o = new JsonObject();
        o.addProperty("t", "gui_close");
        return bytes(o);
    }

    private static JsonObject args(Map<String, String> args) {
        JsonObject a = new JsonObject();
        args.forEach((k, v) -> {
            if (v != null) a.addProperty(k, v.length() > 1200 ? v.substring(0, 1200) : v);
        });
        return a;
    }

    private static byte[] bytes(JsonObject o) {
        return o.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ---- Reading fields defensively ------------------------------------------------------------

    static String str(JsonObject o, String key, String fallback, int max) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return fallback;
        String s = e.getAsString();
        StringBuilder b = new StringBuilder(Math.min(s.length(), max));
        for (int i = 0; i < s.length() && b.length() < max; i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c != 0x7F && c != '§') b.append(c);
        }
        return b.toString();
    }

    static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    static double num(JsonObject o, String key, double fallback, double lo, double hi) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return fallback;
        double v = e.getAsDouble();
        if (Double.isNaN(v)) return fallback;
        return Math.max(lo, Math.min(hi, v));
    }

    static int integer(JsonObject o, String key, int fallback, int lo, int hi) {
        return (int) Math.round(num(o, key, fallback, lo, hi));
    }

    static long lng(JsonObject o, String key) {
        return (long) num(o, key, 0, 0, 9e15);
    }

    static double usd(JsonObject o, String key) {
        return num(o, key, 0, 0, 1e9);
    }

    static int color(JsonObject o, String key, int fallback) {
        String s = str(o, key, null, 8);
        if (s == null || !s.matches("#[0-9a-fA-F]{6}")) return fallback;
        return Integer.parseInt(s.substring(1), 16);
    }

    static List<String> strings(JsonObject o, String key, int max, int length) {
        List<String> out = new ArrayList<>();
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) return List.of();
        for (JsonElement x : e.getAsJsonArray()) {
            if (x.isJsonPrimitive() && out.size() < max) {
                String s = x.getAsString();
                out.add(s.length() > length ? s.substring(0, length) : s);
            }
        }
        return List.copyOf(out);
    }

    static List<JsonObject> objects(JsonObject o, String key, int max) {
        List<JsonObject> out = new ArrayList<>();
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) return out;
        for (JsonElement x : e.getAsJsonArray()) {
            if (x.isJsonObject() && out.size() < max) out.add(x.getAsJsonObject());
        }
        return out;
    }
}
