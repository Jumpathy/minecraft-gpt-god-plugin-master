package net.bigyous.gptgodmc.god;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import net.bigyous.gptgodmc.god.vessel.Vessel;
import net.bigyous.gptgodmc.god.vessel.VesselMood;

/**
 * A god's own visual style, written by the god: its palette, the sprites and shapes it favours, how its
 * effects move, the colour of its sky in each mood, its HUD accent and its sigil. style.json in the god's
 * folder. First drawn at birth (by the memory model from the persona, else from the archetype), changed by
 * the god with fx_style. Scenes may name its colours as tokens ("$primary", "$mood", ...), resolved by
 * {@link #resolve} when a scene is shown, so one saved effect looks different for each god and mood.
 * Everything but {@link #of}, {@link #born}, {@link #save} and {@link #currentMood} is pure.
 */
public final class FxStyle {
    public static final List<String> MOODS = List.of("calm", "wrathful", "joyful", "sorrowful", "ominous", "exalted");
    /** Colour tokens a scene may use in place of "#RRGGBB". */
    public static final List<String> COLOR_TOKENS = List.of("$primary", "$secondary", "$accent", "$shadow", "$hud", "$mood");
    /** Replaced by the god's sigil text (in a glyph layer's text). */
    public static final String SIGIL_TOKEN = "$sigil";
    public static final int MAX_FAVOURITES = 4;
    public static final int MAX_MOTION = 60;
    public static final int MAX_SIGIL = 12;

    public static final String TOKEN_HELP = "Instead of a fixed colour a scene may name your style: "
            + String.join(", ", COLOR_TOKENS) + " ($mood is your sky colour for the mood you are in when it plays); "
            + "a glyph's text may be \"" + SIGIL_TOKEN + "\".";

    public String primary;
    public String secondary;
    public String accent;
    public String shadow;
    public String hud;
    public List<String> sprites = new ArrayList<>();
    public List<String> shapes = new ArrayList<>();
    /** A few words on how its effects move ("slow and heavy", "sharp and electric"). */
    public String motion;
    /** Sky colour per mood (keys from {@link #MOODS}). */
    public Map<String, String> sky = new LinkedHashMap<>();
    public String sigil;
    /** Who drew it last: archetype (fallback), birth (memory model) or self (the god). */
    public String by;
    public long updated;

    // ---- Fallback from the archetype ---------------------------------------------------------------

    private record Base(int primary, int secondary, int accent, int shadow, List<String> sprites, List<String> shapes,
            String motion, String sigil) {
    }

    private static Base base(String archetype) {
        String a = archetype == null ? "" : archetype.trim().toLowerCase(Locale.ROOT);
        return switch (a) {
            case "benevolent" -> new Base(0xFFE066, 0xFFF8DC, 0x8FE3FF, 0x6B4E16, List.of("mote", "star", "ray"),
                    List.of("ring", "column", "wings"), "slow, warm and rising", "✦");
            case "chill" -> new Base(0x6FD3FF, 0xC8F1FF, 0xB8FFD9, 0x1D3A52, List.of("mote", "smoke"),
                    List.of("disc", "spiral", "rain"), "lazy and drifting", "≈");
            case "trickster" -> new Base(0x7CF06B, 0xF06BD8, 0xFFE94A, 0x2A1140, List.of("spark", "shard", "star"),
                    List.of("burst", "helix", "sigil"), "quick, darting and uneven", "✧");
            case "evil" -> new Base(0xD0302A, 0x5A0E1C, 0xFF7A3D, 0x12060A, List.of("ember", "smoke", "shard"),
                    List.of("vortex", "sigil", "column"), "slow, heavy and closing in", "†");
            case "wrathful" -> new Base(0xFF8A1F, 0xFF3020, 0xFFE9A8, 0x2B0A05, List.of("ember", "spark", "ray"),
                    List.of("burst", "beam", "ring"), "sudden and violent", "✹");
            case "cosmic" -> new Base(0xB070FF, 0x4C6BFF, 0xE8D8FF, 0x0B0720, List.of("star", "rune", "mote"),
                    List.of("sphere", "spiral", "sigil"), "vast, slow and orbiting", "✶");
            default -> hashed(a);
        };
    }

    /** An archetype the table does not know (operators may write their own) gets colours from its name. */
    private static Base hashed(String a) {
        int h = a.hashCode();
        float hue = Math.floorMod(h, 360) / 360f;
        List<String> sp = FxScenes.SPRITES, sh = FxScenes.SHAPES;
        return new Base(hsv(hue, 0.65f, 1f), hsv((hue + 0.08f) % 1f, 0.35f, 1f), hsv((hue + 0.5f) % 1f, 0.55f, 1f),
                hsv(hue, 0.7f, 0.15f), List.of(sp.get(Math.floorMod(h, sp.size())), sp.get(Math.floorMod(h / 7, sp.size()))),
                List.of(sh.get(Math.floorMod(h / 3, sh.size())), sh.get(Math.floorMod(h / 11, sh.size()))),
                "steady and deliberate", "✦");
    }

    private static final Map<String, Integer> MOOD_SKY = Map.of("wrathful", 0xFF2A1A, "joyful", 0xFFD86B, "sorrowful",
            0x7FB2FF, "ominous", 0x5A1E8C, "exalted", 0xFFF1B0);

    /** The style a god of this archetype starts with when no model drew one. Deterministic. */
    public static FxStyle fallback(String archetype) {
        Base b = base(archetype);
        FxStyle s = new FxStyle();
        s.primary = hex(b.primary());
        s.secondary = hex(b.secondary());
        s.accent = hex(b.accent());
        s.shadow = hex(b.shadow());
        s.hud = hex(b.primary());
        s.sprites = new ArrayList<>(b.sprites());
        s.shapes = new ArrayList<>(b.shapes());
        s.motion = b.motion();
        s.sigil = b.sigil();
        for (String mood : MOODS) {
            // Each mood's usual colour, pulled a third of the way toward this god's own.
            int usual = mood.equals("calm") ? mix(b.primary(), 0xDCEBFF, 0.55) : MOOD_SKY.get(mood);
            s.sky.put(mood, hex(mood.equals("calm") ? usual : mix(usual, b.primary(), 0.3)));
        }
        s.by = "archetype";
        return s;
    }

    /** Fills anything missing (an old or hand-edited file) from the archetype's style. */
    FxStyle complete(String archetype) {
        FxStyle f = fallback(archetype);
        if (!isHex(primary)) primary = f.primary;
        if (!isHex(secondary)) secondary = f.secondary;
        if (!isHex(accent)) accent = f.accent;
        if (!isHex(shadow)) shadow = f.shadow;
        if (!isHex(hud)) hud = primary;
        if (sprites == null || sprites.isEmpty()) sprites = f.sprites;
        if (shapes == null || shapes.isEmpty()) shapes = f.shapes;
        if (motion == null || motion.isBlank()) motion = f.motion;
        if (sigil == null || sigil.isBlank()) sigil = f.sigil;
        if (sky == null) sky = new LinkedHashMap<>();
        Map<String, String> full = new LinkedHashMap<>();
        for (String mood : MOODS) full.put(mood, isHex(sky.get(mood)) ? sky.get(mood) : f.sky.get(mood));
        sky = full;
        if (by == null) by = "archetype";
        return this;
    }

    // ---- Changing it ---------------------------------------------------------------------------------

    /**
     * Applies the fields present in changes (primary, secondary, accent, shadow, hud, sprites, shapes, motion,
     * sigil, sky {mood: colour} or sky_&lt;mood&gt;). Strict: the first unfit value is an error phrased for the
     * god and nothing changes. Lenient (a model's draft): unfit values are skipped.
     *
     * @return the names of the fields that changed
     */
    public List<String> apply(JsonObject changes, boolean strict) throws FxScenes.Invalid {
        FxStyle next = copy();
        List<String> changed = new ArrayList<>();
        for (String key : List.of("primary", "secondary", "accent", "shadow", "hud")) {
            if (!present(changes, key)) continue;
            String c = color(changes.get(key));
            if (c == null) {
                if (strict) throw new FxScenes.Invalid("'" + key + "' must be a colour like \"#FFB040\".");
                continue;
            }
            switch (key) {
                case "primary" -> next.primary = c;
                case "secondary" -> next.secondary = c;
                case "accent" -> next.accent = c;
                case "shadow" -> next.shadow = c;
                default -> next.hud = c;
            }
            changed.add(key);
        }
        if (present(changes, "sprites")) {
            List<String> l = names(changes.get("sprites"), FxScenes.SPRITES, "sprite", strict);
            if (!l.isEmpty()) {
                next.sprites = l;
                changed.add("sprites");
            }
        }
        if (present(changes, "shapes")) {
            List<String> l = names(changes.get("shapes"), FxScenes.SHAPES, "shape", strict);
            if (!l.isEmpty()) {
                next.shapes = l;
                changed.add("shapes");
            }
        }
        if (present(changes, "motion")) {
            String m = text(changes.get("motion"));
            if (m.isEmpty() || m.length() > MAX_MOTION) {
                if (strict) throw new FxScenes.Invalid("'motion' is a few words, at most " + MAX_MOTION + " characters.");
            } else {
                next.motion = m;
                changed.add("motion");
            }
        }
        if (present(changes, "sigil")) {
            String g = text(changes.get("sigil"));
            if (g.isEmpty() || g.codePointCount(0, g.length()) > MAX_SIGIL) {
                if (strict) throw new FxScenes.Invalid("'sigil' is a short mark or word, at most " + MAX_SIGIL + " characters.");
            } else {
                next.sigil = g;
                changed.add("sigil");
            }
        }
        JsonObject skies = new JsonObject();
        if (present(changes, "sky")) {
            JsonElement e = changes.get("sky");
            if (e.isJsonPrimitive()) {
                try {
                    e = JsonParser.parseString(e.getAsString());
                } catch (JsonParseException ex) {
                    e = null;
                }
            }
            if (e != null && e.isJsonObject()) {
                skies = e.getAsJsonObject().deepCopy();
            } else if (strict) {
                throw new FxScenes.Invalid("'sky' is an object of mood: colour, e.g. {\"wrathful\":\"#B01010\"}. Moods: " + MOODS + ".");
            }
        }
        for (String mood : MOODS) {
            if (present(changes, "sky_" + mood)) skies.add(mood, changes.get("sky_" + mood));
        }
        for (Map.Entry<String, JsonElement> e : skies.entrySet()) {
            String mood = e.getKey().trim().toLowerCase(Locale.ROOT);
            String c = color(e.getValue());
            if (!MOODS.contains(mood) || c == null) {
                if (strict) {
                    throw new FxScenes.Invalid(!MOODS.contains(mood) ? "Unknown mood '" + mood + "'. Moods: " + MOODS + "."
                            : "The sky colour for " + mood + " must be like \"#B01010\".");
                }
                continue;
            }
            next.sky.put(mood, c);
            changed.add("sky." + mood);
        }
        if (!changed.isEmpty()) take(next);
        return changed;
    }

    private static boolean present(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && !e.isJsonNull() && !(e.isJsonPrimitive() && e.getAsString().isBlank());
    }

    private static String text(JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) return "";
        return e.getAsString().replaceAll("[\\u00a7\\p{Cntrl}]+", " ").trim();
    }

    /** "#RRGGBB" in upper case, or null. Accepts a missing '#'. */
    static String color(JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) return null;
        String s = e.getAsString().trim();
        if (!s.startsWith("#")) s = "#" + s;
        return s.matches("#[0-9a-fA-F]{6}") ? s.toUpperCase(Locale.ROOT) : null;
    }

    private static List<String> names(JsonElement e, List<String> allowed, String what, boolean strict) throws FxScenes.Invalid {
        List<String> raw = new ArrayList<>();
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) if (x.isJsonPrimitive()) raw.add(x.getAsString());
        } else if (e.isJsonPrimitive()) {
            String s = e.getAsString().trim();
            if (s.startsWith("[")) {
                try {
                    for (JsonElement x : JsonParser.parseString(s).getAsJsonArray()) raw.add(x.getAsString());
                } catch (RuntimeException ex) {
                    raw.clear();
                }
            } else {
                raw.addAll(List.of(s.split("[,\\s]+")));
            }
        }
        List<String> out = new ArrayList<>();
        for (String r : raw) {
            String n = r.trim().toLowerCase(Locale.ROOT);
            if (n.isEmpty()) continue;
            if (!allowed.contains(n)) {
                if (strict) throw new FxScenes.Invalid("Unknown " + what + " '" + n + "'. Choose from " + allowed + ".");
                continue;
            }
            if (!out.contains(n) && out.size() < MAX_FAVOURITES) out.add(n);
        }
        if (out.isEmpty() && strict) throw new FxScenes.Invalid("Name one to " + MAX_FAVOURITES + " " + what + "s from " + allowed + ".");
        return out;
    }

    private FxStyle copy() {
        FxStyle c = new FxStyle();
        c.take(this);
        return c;
    }

    private void take(FxStyle o) {
        primary = o.primary;
        secondary = o.secondary;
        accent = o.accent;
        shadow = o.shadow;
        hud = o.hud;
        sprites = new ArrayList<>(o.sprites);
        shapes = new ArrayList<>(o.shapes);
        motion = o.motion;
        sky = new LinkedHashMap<>(o.sky);
        sigil = o.sigil;
        by = o.by;
        updated = o.updated;
    }

    /** Reads a model's reply (a JSON object somewhere in the text) over the archetype's style. Never throws. */
    public static FxStyle drafted(String archetype, String modelText) {
        FxStyle s = fallback(archetype);
        if (modelText == null) return s;
        try {
            int start = modelText.indexOf('{'), end = modelText.lastIndexOf('}');
            JsonObject o = JsonParser.parseString(modelText.substring(start, end + 1)).getAsJsonObject();
            if (!s.apply(o, false).isEmpty()) s.by = "birth";
        } catch (RuntimeException | FxScenes.Invalid e) {
            // the archetype's style stands
        }
        return s;
    }

    // ---- Using it ------------------------------------------------------------------------------------

    /** The colour a token stands for now, or null when it is not a colour token. */
    public String colorOf(String token, String mood) {
        if (token == null) return null;
        return switch (token.trim().toLowerCase(Locale.ROOT)) {
            case "$primary" -> primary;
            case "$secondary" -> secondary;
            case "$accent" -> accent;
            case "$shadow" -> shadow;
            case "$hud" -> hud;
            case "$mood" -> sky.getOrDefault(mood == null ? "calm" : mood.toLowerCase(Locale.ROOT), primary);
            default -> null;
        };
    }

    public static boolean isColorToken(String s) {
        return s != null && COLOR_TOKENS.contains(s.trim().toLowerCase(Locale.ROOT));
    }

    /** A copy of the layers with every style token replaced by what it stands for in this mood. */
    public JsonArray resolve(JsonArray layers, String mood) {
        return swap(layers, mood).getAsJsonArray();
    }

    private JsonElement swap(JsonElement e, String mood) {
        if (e == null || e.isJsonNull()) return e;
        if (e.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement x : e.getAsJsonArray()) out.add(swap(x, mood));
            return out;
        }
        if (e.isJsonObject()) {
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) out.add(x.getKey(), swap(x.getValue(), mood));
            return out;
        }
        if (e.getAsJsonPrimitive().isString()) {
            String s = e.getAsString().trim();
            if (s.startsWith("$")) {
                if (s.equalsIgnoreCase(SIGIL_TOKEN)) return new JsonPrimitive(sigil);
                String c = colorOf(s, mood);
                if (c != null) return new JsonPrimitive(c);
            }
        }
        return e;
    }

    /** What mod players are told, so their sky and HUD follow this god's own colours. */
    public JsonObject wire() {
        JsonObject o = new JsonObject();
        o.addProperty("primary", primary);
        o.addProperty("secondary", secondary);
        o.addProperty("accent", accent);
        o.addProperty("shadow", shadow);
        o.addProperty("hud", hud);
        o.addProperty("sigil", sigil);
        o.addProperty("motion", motion);
        JsonArray sp = new JsonArray();
        sprites.forEach(sp::add);
        o.add("sprites", sp);
        JsonArray sh = new JsonArray();
        shapes.forEach(sh::add);
        o.add("shapes", sh);
        JsonObject skies = new JsonObject();
        for (String mood : MOODS) skies.addProperty(mood, sky.get(mood));
        o.add("sky", skies);
        return o;
    }

    /** The style in words, for the god. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("primary ").append(primary).append(", secondary ").append(secondary).append(", accent ").append(accent)
                .append(", shadow ").append(shadow).append(", HUD ").append(hud).append("; sprites ").append(String.join(", ", sprites))
                .append("; shapes ").append(String.join(", ", shapes)).append("; motion: ").append(motion).append("; sigil: ")
                .append(sigil).append("; sky");
        for (String mood : MOODS) sb.append(' ').append(mood).append(' ').append(sky.get(mood));
        return sb.toString();
    }

    // ---- JSON and the file -----------------------------------------------------------------------------

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String toJson() {
        return GSON.toJson(this);
    }

    /** Reads style.json text; missing or unfit fields come from the archetype. Null when it is not JSON at all. */
    public static FxStyle fromJson(String json, String archetype) {
        try {
            FxStyle s = GSON.fromJson(json, FxStyle.class);
            return s == null ? null : s.complete(archetype);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final Map<String, FxStyle> BY_REALM = new ConcurrentHashMap<>();

    /** This god's style: from its file, else its archetype's (kept once the god is born). */
    public static FxStyle of(Realm r) {
        FxStyle have = BY_REALM.get(r.id);
        if (have != null) return have;
        Persona p = r.persona;
        String archetype = p == null ? null : p.archetype;
        FxStyle s = null;
        Path file = r.file("style.json");
        if (file != null && Files.exists(file)) {
            try {
                s = fromJson(Files.readString(file, StandardCharsets.UTF_8), archetype);
            } catch (IOException e) {
                s = null;
            }
        }
        if (s == null) s = fallback(archetype);
        if (p != null) BY_REALM.put(r.id, s); // before birth the archetype is unknown: look again later
        return s;
    }

    /** A newborn god's style: the memory model's draft (may be null) over its archetype's. */
    public static FxStyle born(Realm r, Persona p, String modelText) {
        FxStyle s = drafted(p == null ? null : p.archetype, modelText);
        s.updated = System.currentTimeMillis();
        BY_REALM.put(r.id, s);
        save(r, s);
        return s;
    }

    public static void save(Realm r, FxStyle s) {
        Path file = r.file("style.json");
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, s.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // kept in memory; written again on the next change
        }
    }

    /** The mood this god shows now: its body's, or its heart's while formless. Server thread. */
    public static String currentMood(Realm r) {
        try {
            Vessel v = r.vesselIfCreated();
            VesselMood m = v != null && v.hasBody() ? v.mood() : r.memory().heart.mood(System.currentTimeMillis());
            return m == null ? "calm" : m.id();
        } catch (RuntimeException e) {
            return "calm";
        }
    }

    // ---- Colour maths ----------------------------------------------------------------------------------

    static boolean isHex(String s) {
        return s != null && s.matches("#[0-9a-fA-F]{6}");
    }

    static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    public static int rgb(String hex) {
        return Integer.parseInt(hex.substring(1), 16);
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }

    private static int hsv(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((h * 6) % 2 - 1)), m = v - c;
        float r, g, b;
        switch ((int) (h * 6) % 6) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
    }
}
