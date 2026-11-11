package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * The god's effect language on the server side. Pure. A scene is a list of layers on a timeline (ticks):
 * particle emitters, beams, shockwaves, domes, screen effects, sky tints, sounds, flying blocks and
 * words of light. {@link #check} validates what the god wrote (with messages phrased for the god);
 * {@link #message} wraps it for the client mod, which renders it; {@link #plain} turns it into plain
 * dust-particle dots for players without the mod.
 */
public final class FxScenes {
    private FxScenes() {
    }

    public static final int MAX_LAYERS = 24;
    public static final int MAX_BYTES = 24 * 1024;
    public static final int MAX_TICKS = 20 * 60;

    public static final List<String> TYPES = List.of("particles", "beam", "shockwave", "dome", "screen", "post", "sky", "sound",
            "debris", "glyph", "lightning", "rift", "tendrils", "spikes", "halo", "illusion", "camera", "synth", "field", "solid");
    public static final List<String> WAVES = List.of("sine", "triangle", "saw", "square", "noise", "crackle");
    /** Sounds the client mod already has; usable as a sound layer's key (others hear nothing for these). */
    public static final List<String> MOD_SOUNDS = List.of("gptgodmc:god.arrival", "gptgodmc:god.departure", "gptgodmc:god.trueform",
            "gptgodmc:god.smite", "gptgodmc:god.wound", "gptgodmc:god.blessing", "gptgodmc:god.curse", "gptgodmc:god.meteor",
            "gptgodmc:god.quake", "gptgodmc:god.omen", "gptgodmc:god.death.fall", "gptgodmc:god.death.supernova",
            "gptgodmc:god.death.unraveling", "gptgodmc:god.death.shattering", "gptgodmc:god.death.abyssal");
    public static final int MAX_VOICES = 12;
    public static final List<String> CAMERAS = List.of("zoom", "roll", "letterbox", "gaze");
    public static final List<String> ILLUSIONS = List.of("fling", "rise", "float");
    public static final List<String> SHAPES = List.of("point", "ring", "disc", "sphere", "column", "spiral", "helix",
            "vortex", "beam", "line", "burst", "wings", "sigil", "rain", "dome");
    public static final List<String> MOTIONS = List.of("still", "outward", "inward", "up", "fall", "orbit", "swirl");
    public static final List<String> SPRITES = List.of("mote", "spark", "ember", "rune", "smoke", "star", "shard", "ray");
    public static final List<String> SCREENS = List.of("shake", "flash", "vignette", "pulse");
    /** Full-screen shader effects (client mod only). */
    public static final List<String> POSTS = List.of("shock", "aberration", "blur", "bloom", "rays", "desaturate", "tint",
            "contrast", "invert", "grain");

    /** The reference the god reads in the tool description. */
    public static final String HELP = "A scene is a JSON array of layers; each layer is an object with \"type\", an "
            + "optional \"delay\" and \"duration\" in ticks (20 = 1 s) and \"offset\" [x,y,z] from the scene's place. Types: "
            + "particles {shape: " + String.join("|", SHAPES) + "; motion: " + String.join("|", MOTIONS) + "; sprite: "
            + String.join("|", SPRITES) + "; count (all at once, max 1500) and/or rate (per tick, max 80); radius, "
            + "height, turns; speed (blocks/tick), spread, gravity, drag, turbulence; life [min,max] ticks; size "
            + "[start,end] blocks; color \"#RRGGBB\" or [start,end]; alpha; glow 1 = light that adds up, 0 = solid like "
            + "smoke; grow}. Solid light (real geometry, color [core, edge]): beam {height or to [x,y,z], width}; lightning "
            + "{height, radius, count, width: forked bolts}; shockwave {radius, width, duration: a ring racing over the "
            + "ground}; dome {radius}; rift {height, width, yaw: a tear in the air, facing yaw degrees}; tendrils {radius, height, count}; spikes "
            + "{radius, height, count: crystals bursting from the ground}; halo {radius, height, count of rays}. "
            + "illusion {mode: rise|float|fling, count, radius, height: the ground itself seems to rise as pillars, float "
            + "away or be flung, a harmless illusion}. screen {kind: " + String.join("|", SCREENS) + ", intensity 0-1, duration, "
            + "color}. post {kind: " + String.join("|", POSTS) + ", intensity 0-1, duration, color: full-screen "
            + "shader effects; shock is a ring that bends the world as it expands from the scene, rays is light "
            + "streaming from it, bloom makes light spill}. camera {kind: zoom|roll|letterbox|gaze, amount -1 to 1, duration: a brief move of each "
            + "viewer's camera; zoom punches in (or out if negative), roll tilts the horizon, letterbox adds cinema bars, "
            + "gaze draws their view toward the scene}. sky {color, strength 0-1, duration}. sound {key e.g. minecraft:block.bell.use, volume, pitch "
            + "0.5-2; your own ready-made sounds: " + String.join(", ", MOD_SOUNDS) + "}. synth {voices: up to 12 of {wave: "
            + String.join("|", WAVES) + ", freq Hz or [start,end] glide, start/attack/hold/release in ms, gain 0-1, vibrato [Hz, "
            + "depth], tremolo [Hz, depth], lowpass Hz or [start,end] sweep, highpass Hz}, echo [ms, gain], volume, pitch, "
            + "fallback: a vanilla sound key for mortals without the mod. A sound of your own design, max 6 s: noise "
            + "with a rising lowpass is a whoosh, low sines falling in pitch are booms, crackle is embers or static, "
            + "several sines a chord}. debris {count, radius, speed, up, spin: tears blocks loose from the ground as a harmless "
            + "illusion}. glyph {text, color, size, duration: words of light in the air}. "
            + net.bigyous.gptgodmc.god.fx.FxFormulas.HELP + " Example: "
            + "[{\"type\":\"particles\",\"shape\":\"sphere\",\"motion\":\"inward\",\"radius\":6,\"count\":400,\"speed\":0.3,"
            + "\"sprite\":\"spark\",\"color\":[\"#FFF6D0\",\"#FFB040\"]},{\"type\":\"shockwave\",\"delay\":20,\"radius\":14,"
            + "\"duration\":20,\"color\":\"#FFD86A\"},{\"type\":\"screen\",\"delay\":20,\"kind\":\"flash\",\"intensity\":0.7,"
            + "\"duration\":10},{\"type\":\"sound\",\"delay\":20,\"key\":\"minecraft:entity.generic.explode\",\"pitch\":0.6}]";

    /** Thrown with a message meant for the god. */
    public static final class Invalid extends Exception {
        public Invalid(String message) {
            super(message);
        }
    }

    /** Parses and validates the layers the god wrote. Returns them cleaned (unknown layers are an error). */
    public static JsonArray check(String layersJson) throws Invalid {
        if (layersJson == null || layersJson.isBlank()) throw new Invalid("The scene is empty. " + HELP);
        if (layersJson.length() > MAX_BYTES) throw new Invalid("The scene is too long (max " + MAX_BYTES + " characters).");
        JsonElement root;
        try {
            root = JsonParser.parseString(layersJson);
        } catch (JsonParseException e) {
            throw new Invalid("The scene is not valid JSON: " + e.getMessage());
        }
        if (root.isJsonObject() && root.getAsJsonObject().has("layers")) root = root.getAsJsonObject().get("layers");
        if (root.isJsonObject()) {
            JsonArray one = new JsonArray();
            one.add(root);
            root = one;
        }
        if (!root.isJsonArray()) throw new Invalid("The scene must be a JSON array of layers.");
        JsonArray in = root.getAsJsonArray();
        if (in.isEmpty()) throw new Invalid("The scene has no layers.");
        if (in.size() > MAX_LAYERS) throw new Invalid("At most " + MAX_LAYERS + " layers in one scene (you gave " + in.size() + ").");
        JsonArray out = new JsonArray();
        int n = 0;
        for (JsonElement e : in) {
            n++;
            if (!e.isJsonObject()) throw new Invalid("Layer " + n + " is not an object.");
            JsonObject l = e.getAsJsonObject();
            String type = word(l, "type", "particles");
            if (!TYPES.contains(type)) throw new Invalid("Layer " + n + ": unknown type '" + type + "'. Types: " + TYPES + ".");
            l.addProperty("type", type);
            member(l, "shape", SHAPES, n);
            member(l, "motion", MOTIONS, n);
            member(l, "sprite", SPRITES, n);
            if (net.bigyous.gptgodmc.god.fx.FxFormulas.TYPES.contains(type)) net.bigyous.gptgodmc.god.fx.FxFormulas.check(l, type, n);
            if (type.equals("screen")) member(l, "kind", SCREENS, n);
            if (type.equals("post")) member(l, "kind", POSTS, n);
            if (type.equals("illusion")) member(l, "mode", ILLUSIONS, n);
            if (type.equals("camera")) member(l, "kind", CAMERAS, n);
            if (type.equals("synth")) {
                JsonElement vs = l.get("voices");
                if (vs == null || !vs.isJsonArray() || vs.getAsJsonArray().isEmpty()) {
                    throw new Invalid("Layer " + n + ": a synth needs \"voices\", an array of at least one voice.");
                }
                if (vs.getAsJsonArray().size() > MAX_VOICES) throw new Invalid("Layer " + n + ": at most " + MAX_VOICES + " voices.");
                for (JsonElement v : vs.getAsJsonArray()) {
                    if (!v.isJsonObject()) throw new Invalid("Layer " + n + ": each voice is an object.");
                    member(v.getAsJsonObject(), "wave", WAVES, n);
                }
                if (l.has("fallback")) {
                    String key = word(l, "fallback", "");
                    if (!key.matches("[a-z0-9_.:/-]+")) throw new Invalid("Layer " + n + ": fallback is a sound key like block.bell.use.");
                    l.addProperty("fallback", key.contains(":") ? key : "minecraft:" + key);
                }
            }
            if (type.equals("sound")) {
                String key = word(l, "key", "");
                if (!key.matches("[a-z0-9_.:/-]+")) throw new Invalid("Layer " + n + ": a sound needs a key like minecraft:block.bell.use.");
                if (!key.contains(":")) l.addProperty("key", "minecraft:" + key);
            }
            if (type.equals("glyph") && word(l, "text", "").isBlank()) throw new Invalid("Layer " + n + ": a glyph needs text.");
            for (String c : Set.of("color")) color(l.get(c), n);
            out.add(l);
        }
        return out;
    }

    private static String word(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString().trim().toLowerCase(Locale.ROOT) : fallback;
    }

    private static void member(JsonObject l, String key, List<String> allowed, int n) throws Invalid {
        if (!l.has(key)) return;
        String v = word(l, key, "");
        if (!allowed.contains(v)) throw new Invalid("Layer " + n + ": unknown " + key + " '" + v + "'. Choose from " + allowed + ".");
        l.addProperty(key, v);
    }

    private static void color(JsonElement e, int n) throws Invalid {
        if (e == null) return;
        if (e.isJsonArray()) {
            for (JsonElement c : e.getAsJsonArray()) color(c, n);
            return;
        }
        // A style token ("$primary", "$mood", ...) stands for the god's own colour; FxStyle.resolve fills it in.
        if (e.isJsonPrimitive() && FxStyle.isColorToken(e.getAsString())) return;
        if (!e.isJsonPrimitive() || !e.getAsString().matches("#[0-9a-fA-F]{6}")) {
            throw new Invalid("Layer " + n + ": colors are \"#RRGGBB\" (or [start, end]), or a style token: "
                    + FxStyle.COLOR_TOKENS + ".");
        }
    }

    /** How long the scene runs, in ticks. */
    public static int length(JsonArray layers) {
        int longest = 1;
        for (JsonElement e : layers) {
            JsonObject l = e.getAsJsonObject();
            longest = Math.max(longest, (int) num(l, "delay", 0, 0, MAX_TICKS) + (int) num(l, "duration", defaultDuration(l), 1, MAX_TICKS));
        }
        return Math.min(MAX_TICKS, longest);
    }

    private static int defaultDuration(JsonObject l) {
        return switch (word(l, "type", "particles")) {
            case "screen" -> 20;
            case "post" -> 30;
            case "camera" -> 40;
            case "sky" -> 100;
            case "glyph" -> 60;
            case "field", "solid" -> net.bigyous.gptgodmc.god.fx.FxFormulas.DEFAULT_TICKS;
            default -> 1;
        };
    }

    /** The message for the client mod. at is the world position; follow an entity id or -1. */
    public static JsonObject message(String id, double[] at, int follow, double scale, boolean loop, JsonArray layers) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "scene");
        if (id != null && !id.isBlank()) o.addProperty("id", id);
        JsonArray p = new JsonArray();
        for (double c : at) p.add(Math.round(c * 100) / 100.0);
        o.add("at", p);
        if (follow >= 0) o.addProperty("follow", follow);
        if (scale != 1) o.addProperty("scale", scale);
        if (loop) o.addProperty("loop", true);
        o.addProperty("life", length(layers));
        o.add("layers", layers);
        return o;
    }

    public static JsonObject stop(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "scene_stop");
        o.addProperty("id", id == null ? "" : id);
        return o;
    }

    // ---- The plain version, for players without the mod ------------------------------------------

    /** One dust particle, relative to the scene's place. */
    public record Dot(double x, double y, double z, int rgb, float size) {
    }

    /** A sound to play at the scene's place on a tick. */
    public record Cue(int tick, String key, float volume, float pitch) {
    }

    public static List<Cue> cues(JsonArray layers) {
        List<Cue> out = new ArrayList<>();
        for (JsonElement e : layers) {
            JsonObject l = e.getAsJsonObject();
            String type = word(l, "type", "");
            if (type.equals("synth") && l.has("fallback")) {
                out.add(new Cue((int) num(l, "delay", 0, 0, MAX_TICKS), word(l, "fallback", ""), (float) num(l, "volume", 1, 0, 4),
                        (float) num(l, "pitch", 1, 0.5, 2)));
            }
            if (!type.equals("sound")) continue;
            out.add(new Cue((int) num(l, "delay", 0, 0, MAX_TICKS), word(l, "key", ""), (float) num(l, "volume", 1, 0, 4),
                    (float) num(l, "pitch", 1, 0.5, 2)));
        }
        return out;
    }

    /**
     * The dots to show on this tick (call every second tick). A sketch of the scene in vanilla dust: the
     * same shapes, colours and timing, far fewer particles, no motion blur or glow.
     */
    public static List<Dot> plain(JsonArray layers, int tick, double scale, Random rnd, int max) {
        List<Dot> out = new ArrayList<>();
        for (JsonElement e : layers) {
            JsonObject l = e.getAsJsonObject();
            String type = word(l, "type", "particles");
            if (net.bigyous.gptgodmc.god.fx.FxFormulas.TYPES.contains(type)) {
                net.bigyous.gptgodmc.god.fx.FxFormulas.plain(l, type, tick, scale, rnd, max, out);
                continue;
            }
            if (type.equals("lightning")) type = "beam";
            if (type.equals("halo") || type.equals("spikes") || type.equals("tendrils")) {
                l = l.deepCopy();
                l.addProperty("shape", type.equals("halo") ? "ring" : type.equals("spikes") ? "disc" : "column");
                if (type.equals("halo")) l.add("offset", halo(l));
                if (!l.has("duration")) l.addProperty("duration", 60);
                l.addProperty("rate", 6);
                type = "particles";
            }
            if (!(type.equals("particles") || type.equals("beam") || type.equals("shockwave") || type.equals("dome"))) continue;
            int delay = (int) num(l, "delay", 0, 0, MAX_TICKS);
            int duration = (int) num(l, "duration", 1, 1, MAX_TICKS);
            int t = tick - delay;
            if (t < 0) continue;
            String shape = type.equals("beam") ? "beam" : type.equals("shockwave") ? "ring" : type.equals("dome") ? "dome"
                    : word(l, "shape", "burst");
            String motion = word(l, "motion", type.equals("shockwave") || shape.equals("burst") || shape.equals("point") ? "outward" : "still");
            double radius = num(l, "radius", 2, 0, 48) * scale;
            double height = num(l, "height", 3, 0, 96) * scale;
            double speed = num(l, "speed", motion.equals("still") ? 0 : 0.15, 0, 3) * scale;
            double[] life = pair(l.get("life"), 20, 40);
            int lifeMax = (int) Math.max(life[0], life[1]);
            // A burst keeps showing while its particles would still be alive; a stream while it flows.
            boolean burst = num(l, "count", -1, -1, 1500) > 0 || num(l, "rate", 0, 0, 80) == 0;
            int shows = type.equals("shockwave") ? duration : burst ? Math.max(duration, lifeMax) : duration + lifeMax / 2;
            if (t > shows) continue;
            double grown = type.equals("shockwave") ? radius * t / Math.max(1, duration)
                    : motion.equals("outward") ? Math.min(48, speed * t * 0.8)
                    : motion.equals("inward") ? -Math.min(radius, speed * t * 0.8) : 0;
            double rise = motion.equals("up") ? speed * t * 0.5 : motion.equals("fall") ? -speed * t * 0.5 : 0;
            int[] colors = colors(l.get("color"));
            double u = Math.min(1, t / (double) Math.max(1, shows));
            int rgb = mix(colors[0], colors[1], u);
            double[] off = vec(l.get("offset"));
            double want = Math.min(max, 10 + Math.max(num(l, "count", 0, 0, 1500) / 12, num(l, "rate", 0, 0, 80) * 1.5));
            int n = (int) Math.max(4, want * (1 - 0.5 * u));
            float size = (float) Math.max(0.6, Math.min(3.5, pair(l.get("size"), 0.25, 0.25)[0] * 4 * scale));
            for (int i = 0; i < n && out.size() < max; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double x = 0, y = 0, z = 0;
                switch (shape) {
                    case "ring", "sigil" -> {
                        double r = type.equals("shockwave") ? grown : radius + grown;
                        x = Math.cos(a) * r;
                        z = Math.sin(a) * r;
                    }
                    case "disc", "rain" -> {
                        double r = radius * Math.sqrt(rnd.nextDouble());
                        x = Math.cos(a) * r;
                        z = Math.sin(a) * r;
                        if (shape.equals("rain")) y = height * rnd.nextDouble();
                    }
                    case "sphere", "dome", "burst", "point" -> {
                        double yy = shape.equals("dome") ? rnd.nextDouble() : rnd.nextDouble() * 2 - 1;
                        double ring = Math.sqrt(1 - yy * yy);
                        double r = shape.equals("burst") || shape.equals("point") ? grown : Math.max(0, radius + grown);
                        x = Math.cos(a) * ring * r;
                        y = yy * r;
                        z = Math.sin(a) * ring * r;
                    }
                    case "column" -> {
                        double r = radius * Math.sqrt(rnd.nextDouble());
                        x = Math.cos(a) * r;
                        y = height * rnd.nextDouble();
                        z = Math.sin(a) * r;
                    }
                    case "spiral", "helix", "vortex" -> {
                        double v = rnd.nextDouble();
                        double ang = v * Math.PI * 2 * num(l, "turns", 3, 0.1, 24) + t * 0.12 + (shape.equals("helix") && (i & 1) == 1 ? Math.PI : 0);
                        double r = shape.equals("vortex") ? radius * (0.15 + 0.85 * v) : radius;
                        x = Math.cos(shape.equals("vortex") ? a : ang) * r;
                        y = v * height;
                        z = Math.sin(shape.equals("vortex") ? a : ang) * r;
                    }
                    case "beam", "line" -> {
                        double[] to = l.has("to") ? vec(l.get("to")) : new double[] { 0, num(l, "height", 3, 0, 96), 0 };
                        double v = rnd.nextDouble();
                        x = to[0] * scale * v;
                        y = to[1] * scale * v;
                        z = to[2] * scale * v;
                    }
                    case "wings" -> {
                        double side = (i & 1) == 0 ? 1 : -1, s = rnd.nextDouble(), span = radius * (0.2 + 0.8 * s);
                        x = side * span;
                        y = height * (0.55 + 0.45 * Math.sin(s * Math.PI * 0.9)) - rnd.nextDouble() * height * 0.45 * s;
                        z = -0.3 * scale - 0.35 * span * s;
                    }
                    default -> {
                    }
                }
                out.add(new Dot(x + off[0] * scale, y + rise + off[1] * scale, z + off[2] * scale, rgb, size));
            }
        }
        return out;
    }

    private static JsonArray halo(JsonObject l) {
        JsonArray a = new JsonArray();
        a.add(0);
        a.add(num(l, "height", 2.6, 0, 128));
        a.add(0);
        return a;
    }

    private static double num(JsonObject o, String key, double fallback, double lo, double hi) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return fallback;
        double v = e.getAsDouble();
        return Double.isNaN(v) ? fallback : Math.max(lo, Math.min(hi, v));
    }

    private static double[] pair(JsonElement e, double a, double b) {
        try {
            if (e != null && e.isJsonArray() && e.getAsJsonArray().size() >= 2) {
                return new double[] { e.getAsJsonArray().get(0).getAsDouble(), e.getAsJsonArray().get(1).getAsDouble() };
            }
            if (e != null && e.isJsonPrimitive()) return new double[] { e.getAsDouble(), e.getAsDouble() };
        } catch (RuntimeException ignored) {
            // fall through
        }
        return new double[] { a, b };
    }

    private static double[] vec(JsonElement e) {
        double[] v = new double[3];
        try {
            if (e != null && e.isJsonArray() && e.getAsJsonArray().size() == 3) {
                for (int i = 0; i < 3; i++) v[i] = Math.max(-128, Math.min(128, e.getAsJsonArray().get(i).getAsDouble()));
            }
        } catch (RuntimeException ignored) {
            return new double[3];
        }
        return v;
    }

    private static int[] colors(JsonElement e) {
        try {
            if (e != null && e.isJsonArray() && e.getAsJsonArray().size() >= 2) {
                return new int[] { Integer.parseInt(e.getAsJsonArray().get(0).getAsString().substring(1), 16),
                        Integer.parseInt(e.getAsJsonArray().get(1).getAsString().substring(1), 16) };
            }
            if (e != null && e.isJsonPrimitive()) {
                int c = Integer.parseInt(e.getAsString().substring(1), 16);
                return new int[] { c, c };
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return new int[] { 0xFFE9A8, 0xFFE9A8 };
    }

    private static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }
}
