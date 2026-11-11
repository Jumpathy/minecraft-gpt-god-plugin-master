package net.bigyous.gptgodmc.client;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The God Manager screen's messages to the client mod (feature "gui", channel gptgod:main). Pure: built
 * from plain records, so the exact output is tested against the golden files in
 * client-mod/src/test/resources/protocol. Absent optional fields are left out; long texts are clipped so
 * every message stays well under the 32 KB cap.
 */
public final class ModGuiMessages {
    private ModGuiMessages() {
    }

    /** Pixels along one side of a god's head image. */
    public static final int HEAD = 16;

    // ---- Opening, results, notes ----------------------------------------------------------------

    /**
     * Tells the mod to open the screen.
     *
     * @param realm    the god to show first
     * @param several  more than one god: open on the pantheon
     * @param costs    whether this operator may see API costs
     * @param pantheon whether this operator may create and remove gods
     */
    public static JsonObject open(String realm, boolean several, boolean costs, boolean pantheon) {
        JsonObject o = type("gui_open");
        o.addProperty("realm", realm);
        o.addProperty("several", several);
        o.addProperty("costs", costs);
        o.addProperty("pantheon", pantheon);
        return o;
    }

    /**
     * The answer to one request.
     *
     * @param field   the input the error belongs to (null = the action itself)
     * @param needs   "name" (type the god's name) or "confirm" when a confirmation is missing
     * @param message what the server says happened (also on success)
     */
    public static JsonObject result(String id, boolean ok, String field, String error, String message, String needs) {
        JsonObject o = type("gui_result");
        o.addProperty("id", id == null ? "" : id);
        o.addProperty("ok", ok);
        if (field != null) o.addProperty("field", field);
        if (error != null) o.addProperty("error", clip(error, 400));
        if (message != null) o.addProperty("message", clip(message, 600));
        if (needs != null) o.addProperty("needs", needs);
        return o;
    }

    /** A line that would otherwise go to chat while the screen is open. level: info or error. */
    public static JsonObject note(String text, String level) {
        JsonObject o = type("gui_note");
        o.addProperty("text", clip(text, 600));
        o.addProperty("level", level == null ? "info" : level);
        return o;
    }

    // ---- The pantheon ---------------------------------------------------------------------------

    /**
     * One god as the sidebar and its hover card show it. heart: wrath, joy, sorrow, dread, glory (0..100).
     * body: formless, walks or true_form. state: awake, asleep, paused, off or slain.
     */
    public record GodRow(String id, String name, String epithet, String archetype, int color, String power,
            String state, boolean thinking, String mood, double[] heart, String body, int pending, Double health,
            Double max, String look, boolean removable) {
    }

    private static JsonObject row(GodRow g) {
        JsonObject o = new JsonObject();
        o.addProperty("id", g.id());
        if (g.name() != null) o.addProperty("name", g.name());
        if (g.epithet() != null) o.addProperty("epithet", clip(g.epithet(), 80));
        if (g.archetype() != null) o.addProperty("archetype", g.archetype());
        o.addProperty("color", ModMessages.hex(g.color()));
        o.addProperty("power", g.power());
        o.addProperty("state", g.state());
        if (g.thinking()) o.addProperty("thinking", true);
        o.addProperty("mood", g.mood());
        JsonArray heart = new JsonArray();
        for (int i = 0; i < 5; i++) heart.add(Math.round(g.heart() == null || g.heart().length <= i ? 0 : g.heart()[i]));
        o.add("heart", heart);
        o.addProperty("body", g.body());
        o.addProperty("pending", g.pending());
        if (g.health() != null && g.max() != null) {
            o.addProperty("health", Math.round(g.health()));
            o.addProperty("max", Math.round(g.max()));
        }
        o.addProperty("look", g.look());
        if (g.removable()) o.addProperty("removable", true);
        return o;
    }

    public static JsonObject pantheon(List<GodRow> gods) {
        JsonObject o = data("pantheon", null);
        JsonArray a = new JsonArray();
        for (GodRow g : gods) a.add(row(g));
        o.add("gods", a);
        return o;
    }

    /** Head faces by look: HEAD x HEAD pixels, 3 bytes each (RGB), row by row from the top left. */
    public static JsonObject heads(Map<String, byte[]> rgb) {
        JsonObject o = data("heads", null);
        o.addProperty("size", HEAD);
        JsonObject h = new JsonObject();
        rgb.forEach((look, px) -> h.addProperty(look, Base64.getEncoder().encodeToString(px)));
        o.add("heads", h);
        return o;
    }

    // ---- One god --------------------------------------------------------------------------------

    public record Dial(String key, int value, int base) {
    }

    /**
     * Everything small about one god, for its Overview, Nature, Body and Mind tabs.
     *
     * @param model  its own model, or null when it follows the server's (configModel)
     * @param effort its own effort, or null when it follows configEffort
     */
    public record GodDetail(GodRow row, boolean born, boolean awake, boolean paused, boolean dormant,
            List<String> heartLines, String model, String effort, String configModel, String configEffort,
            String lookSetting, String voice, String effect, String engine, boolean voiceOn, String gaze,
            boolean bodyOn, boolean trueFormAllowed, List<Dial> dials, int moodMinutes, List<String> awareLines) {
    }

    public static JsonObject god(GodDetail d) {
        JsonObject o = data("god", d.row().id());
        o.add("god", row(d.row()));
        o.addProperty("born", d.born());
        o.addProperty("awake", d.awake());
        o.addProperty("paused", d.paused());
        o.addProperty("dormant", d.dormant());
        o.add("heart_lines", strings(d.heartLines(), 6, 80));
        // What moved it lately, the sky over its world, the memory that last came back (god/Awareness).
        o.add("aware_lines", strings(d.awareLines(), 3, 200));
        if (d.model() != null) o.addProperty("model", d.model());
        if (d.effort() != null) o.addProperty("effort", d.effort());
        o.addProperty("config_model", d.configModel());
        o.addProperty("config_effort", d.configEffort());
        o.addProperty("look_setting", d.lookSetting());
        o.addProperty("voice", d.voice());
        o.addProperty("effect", d.effect());
        o.addProperty("engine", d.engine());
        o.addProperty("voice_on", d.voiceOn());
        o.addProperty("gaze", d.gaze());
        o.addProperty("body_on", d.bodyOn());
        o.addProperty("true_form_allowed", d.trueFormAllowed());
        JsonArray dials = new JsonArray();
        for (Dial x : d.dials()) {
            JsonObject j = new JsonObject();
            j.addProperty("key", x.key());
            j.addProperty("value", x.value());
            j.addProperty("base", x.base());
            dials.add(j);
        }
        o.add("dials", dials);
        o.addProperty("mood_minutes", d.moodMinutes());
        return o;
    }

    // ---- Commands -------------------------------------------------------------------------------

    public record Command(int number, String text, boolean standing, String status, String issuer) {
    }

    /** A finished, impossible or cancelled command, as the journal recorded it. */
    public record Report(long seq, long time, String text) {
    }

    public static JsonObject commands(String realm, List<Command> pending, List<Report> reports) {
        JsonObject o = data("commands", realm);
        JsonArray a = new JsonArray();
        for (Command c : pending) {
            JsonObject j = new JsonObject();
            j.addProperty("n", c.number());
            j.addProperty("text", clip(c.text(), 300));
            if (c.standing()) j.addProperty("standing", true);
            j.addProperty("status", c.status());
            if (c.issuer() != null) j.addProperty("issuer", clip(c.issuer(), 48));
            a.add(j);
        }
        o.add("pending", a);
        JsonArray r = new JsonArray();
        for (Report x : reports) {
            JsonObject j = new JsonObject();
            j.addProperty("seq", x.seq());
            j.addProperty("time", x.time());
            j.addProperty("text", clip(x.text(), 400));
            r.add(j);
        }
        o.add("reports", r);
        return o;
    }

    // ---- Mortals --------------------------------------------------------------------------------

    public record MortalRow(String name, int favor, String tier, boolean online, String title, String seen, int notes,
            int deeds) {
    }

    public static JsonObject mortals(String realm, String query, int page, int pages, int total, List<MortalRow> rows) {
        JsonObject o = data("mortals", realm);
        o.addProperty("query", query == null ? "" : query);
        o.addProperty("page", page);
        o.addProperty("pages", pages);
        o.addProperty("total", total);
        JsonArray a = new JsonArray();
        for (MortalRow m : rows) {
            JsonObject j = new JsonObject();
            j.addProperty("name", m.name());
            j.addProperty("favor", m.favor());
            j.addProperty("tier", m.tier());
            if (m.online()) j.addProperty("online", true);
            if (m.title() != null && !m.title().isBlank()) j.addProperty("title", clip(m.title(), 40));
            if (m.seen() != null) j.addProperty("seen", m.seen());
            j.addProperty("notes", m.notes());
            j.addProperty("deeds", m.deeds());
            a.add(j);
        }
        o.add("mortals", a);
        return o;
    }

    public record Entry(long at, String text, String age) {
    }

    public record MortalDetail(String name, int favor, String tier, boolean online, String title, String summary,
            int offerings, String seen, List<Entry> notes, List<Entry> deeds) {
    }

    /** One mortal's record; m null = nobody is open. Notes and deeds are newest first, at most 40 each. */
    public static JsonObject mortal(String realm, MortalDetail m) {
        JsonObject o = data("mortal", realm);
        if (m == null) {
            o.addProperty("none", true);
            return o;
        }
        o.addProperty("name", m.name());
        o.addProperty("favor", m.favor());
        o.addProperty("tier", m.tier());
        o.addProperty("online", m.online());
        if (m.title() != null && !m.title().isBlank()) o.addProperty("title", clip(m.title(), 40));
        if (m.summary() != null && !m.summary().isBlank()) o.addProperty("summary", clip(m.summary(), 1200));
        o.addProperty("offerings", m.offerings());
        if (m.seen() != null) o.addProperty("seen", m.seen());
        o.add("notes", entries(m.notes()));
        o.add("deeds", entries(m.deeds()));
        o.addProperty("notes_total", m.notes().size());
        o.addProperty("deeds_total", m.deeds().size());
        return o;
    }

    private static JsonArray entries(List<Entry> list) {
        JsonArray a = new JsonArray();
        for (int i = 0; i < list.size() && i < 40; i++) {
            Entry e = list.get(i);
            JsonObject j = new JsonObject();
            j.addProperty("at", e.at());
            j.addProperty("text", clip(e.text(), 240));
            if (e.age() != null) j.addProperty("age", e.age());
            a.add(j);
        }
        return a;
    }

    // ---- Journal --------------------------------------------------------------------------------

    public record JournalLine(long seq, long time, String kind, String text) {
    }

    /** New journal lines, oldest first. reset = forget what you had (a fresh subscription). */
    public static JsonObject journal(String realm, List<JournalLine> lines, boolean reset) {
        JsonObject o = data("journal", realm);
        if (reset) o.addProperty("reset", true);
        JsonArray a = new JsonArray();
        for (JournalLine l : lines) {
            JsonObject j = new JsonObject();
            j.addProperty("seq", l.seq());
            j.addProperty("time", l.time());
            j.addProperty("kind", l.kind());
            j.addProperty("text", clip(l.text(), 500));
            a.add(j);
        }
        o.add("lines", a);
        return o;
    }

    // ---- Server pages ---------------------------------------------------------------------------

    /**
     * One setting of config.yml. type: bool, int, double or choice.
     *
     * @param locked shown but not changeable from the screen (ports and restart-only settings)
     */
    public record Option(String key, String label, String type, double min, double max, double step,
            List<String> choices, String fallback, String unit, String help, boolean restart, boolean locked,
            String value) {
    }

    public static JsonObject config(String tab, List<Option> options) {
        JsonObject o = data("config", null);
        o.addProperty("tab", tab);
        JsonArray a = new JsonArray();
        for (Option x : options) {
            JsonObject j = new JsonObject();
            j.addProperty("key", x.key());
            j.addProperty("label", x.label());
            j.addProperty("type", x.type());
            if (x.type().equals("int") || x.type().equals("double")) {
                j.addProperty("min", num(x.min()));
                j.addProperty("max", num(x.max()));
                j.addProperty("step", num(x.step()));
            }
            if (x.type().equals("choice")) j.add("choices", strings(x.choices(), 32, 40));
            j.addProperty("fallback", x.fallback());
            if (x.unit() != null && !x.unit().isEmpty()) j.addProperty("unit", x.unit());
            j.addProperty("help", clip(x.help(), 200));
            if (x.restart()) j.addProperty("restart", true);
            if (x.locked()) j.addProperty("locked", true);
            j.addProperty("value", x.value());
            a.add(j);
        }
        o.add("options", a);
        return o;
    }

    public record Switch(String key, String label, String help, boolean on) {
    }

    public static JsonObject switches(List<Switch> list) {
        JsonObject o = data("switches", null);
        JsonArray a = new JsonArray();
        for (Switch s : list) {
            JsonObject j = new JsonObject();
            j.addProperty("key", s.key());
            j.addProperty("label", s.label());
            j.addProperty("help", clip(s.help(), 200));
            j.addProperty("on", s.on());
            a.add(j);
        }
        o.add("switches", a);
        return o;
    }

    public record CostGod(String id, String name, double hour, double day, double session) {
    }

    /**
     * API spend in US dollars.
     *
     * @param total    all time (from usage.csv), or null when unknown
     * @param complete false when some requests used a model with no known price
     * @param spark    the last 60 minutes, oldest first
     */
    public record CostView(double minute, double hour, double day, double session, Double total, boolean complete,
            long requests, long input, long output, long cacheRead, long cacheWrite, List<CostGod> gods,
            double[] spark) {
    }

    /** c null = this operator may not see costs. */
    public static JsonObject costs(CostView c) {
        JsonObject o = data("costs", null);
        if (c == null) {
            o.addProperty("off", true);
            return o;
        }
        o.addProperty("minute", usd(c.minute()));
        o.addProperty("hour", usd(c.hour()));
        o.addProperty("day", usd(c.day()));
        o.addProperty("session", usd(c.session()));
        if (c.total() != null) o.addProperty("total", usd(c.total()));
        o.addProperty("complete", c.complete());
        o.addProperty("requests", c.requests());
        o.addProperty("input", c.input());
        o.addProperty("output", c.output());
        o.addProperty("cache_read", c.cacheRead());
        o.addProperty("cache_write", c.cacheWrite());
        JsonArray gods = new JsonArray();
        for (CostGod g : c.gods()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", g.id());
            if (g.name() != null) j.addProperty("name", g.name());
            j.addProperty("hour", usd(g.hour()));
            j.addProperty("day", usd(g.day()));
            j.addProperty("session", usd(g.session()));
            gods.add(j);
        }
        o.add("gods", gods);
        JsonArray spark = new JsonArray();
        for (double v : c.spark()) spark.add(usd(v));
        o.add("spark", spark);
        return o;
    }

    // ---- The catalog (sent once) ----------------------------------------------------------------

    /** A choice with its description; color is null where it has none. */
    public record Named(String key, String text, Integer color) {
    }

    public record Preset(String id, String name, String text, boolean confirm) {
    }

    /** What a dial means at 0 and at 10. */
    public record DialEnds(String key, String low, String high) {
    }

    public record Catalog(List<Named> archetypes, List<Named> looks, List<Named> voices, List<Named> effects,
            List<Preset> presets, List<Named> moods, List<Named> powers, List<String> models, List<String> efforts,
            List<DialEnds> dials, List<String> tabs) {
    }

    public static JsonObject catalog(Catalog c) {
        JsonObject o = data("catalog", null);
        o.add("archetypes", named(c.archetypes()));
        o.add("looks", named(c.looks()));
        o.add("voices", named(c.voices()));
        o.add("effects", named(c.effects()));
        JsonArray presets = new JsonArray();
        for (Preset p : c.presets()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", p.id());
            j.addProperty("name", p.name());
            j.addProperty("text", clip(p.text(), 300));
            if (p.confirm()) j.addProperty("confirm", true);
            presets.add(j);
        }
        o.add("presets", presets);
        o.add("moods", named(c.moods()));
        o.add("powers", named(c.powers()));
        o.add("models", strings(c.models(), 16, 48));
        o.add("efforts", strings(c.efforts(), 16, 24));
        JsonArray dials = new JsonArray();
        for (DialEnds d : c.dials()) {
            JsonObject j = new JsonObject();
            j.addProperty("key", d.key());
            j.addProperty("low", d.low());
            j.addProperty("high", d.high());
            dials.add(j);
        }
        o.add("dials", dials);
        o.add("tabs", strings(c.tabs(), 16, 24));
        return o;
    }

    private static JsonArray named(List<Named> list) {
        JsonArray a = new JsonArray();
        for (int i = 0; i < list.size() && i < 64; i++) {
            Named n = list.get(i);
            JsonObject j = new JsonObject();
            j.addProperty("key", n.key());
            j.addProperty("text", clip(n.text() == null ? "" : n.text(), 220));
            if (n.color() != null) j.addProperty("color", ModMessages.hex(n.color()));
            a.add(j);
        }
        return a;
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private static JsonObject type(String t) {
        JsonObject o = new JsonObject();
        o.addProperty("t", t);
        return o;
    }

    private static JsonObject data(String part, String realm) {
        JsonObject o = type("gui_data");
        o.addProperty("part", part);
        if (realm != null) o.addProperty("realm", realm);
        return o;
    }

    private static JsonArray strings(List<String> list, int max, int length) {
        JsonArray a = new JsonArray();
        if (list == null) return a;
        for (int i = 0; i < list.size() && i < max; i++) a.add(clip(list.get(i), length));
        return a;
    }

    public static String clip(String s, int max) {
        if (s == null) return "";
        String t = s.replace('\n', ' ').replace('\r', ' ');
        return t.length() > max ? t.substring(0, max - 1) + "…" : t;
    }

    /** Whole numbers as ints, else as they are. */
    private static Number num(double v) {
        return v == Math.rint(v) ? (Number) (long) v : (Number) v;
    }

    /** Dollars to four decimals (a hundredth of a cent). */
    private static Number usd(double v) {
        double r = Math.round(Math.max(0, v) * 10_000) / 10_000.0;
        return r == Math.rint(r) ? (Number) (long) r : (Number) r;
    }

    static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
