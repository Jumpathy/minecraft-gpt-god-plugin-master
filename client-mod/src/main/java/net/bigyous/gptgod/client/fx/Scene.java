package net.bigyous.gptgod.client.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A scene: the effect language the god writes. A place, a lifetime and layers on a timeline (ticks).
 * Pure. {@link #decode} never throws and clamps everything, so no scene can ask for more than the
 * budgets below; unknown layer types are skipped (newer plugins, older mods).
 */
public record Scene(String id, double[] at, int follow, float scale, int life, boolean loop, long seed,
        List<Layer> layers) {
    public static final int MAX_LAYERS = 24;
    public static final int MAX_LIFE = 20 * 60;
    public static final int MAX_COUNT = 1500;
    public static final int MAX_RATE = 80;
    public static final int MAX_DEBRIS = 150;

    public sealed interface Layer permits Particles, Mesh, Screen, Post, Camera, Sky, Sound, Tone, Debris, Glyph, Field, Solid {
        int delay();

        int duration();
    }

    /** Where particles appear. */
    public enum Shape {
        POINT, RING, DISC, SPHERE, COLUMN, SPIRAL, HELIX, VORTEX, BEAM, LINE, BURST, WINGS, SIGIL, RAIN, DOME
    }

    /** How they move once they exist. */
    public enum Motion {
        STILL, OUTWARD, INWARD, UP, FALL, ORBIT, SWIRL
    }

    /** The mod's sprite sheet, in order (see tools/make_textures.py). */
    public enum Sprite {
        MOTE, SPARK, EMBER, RUNE, SMOKE, STAR, SHARD, RAY
    }

    /**
     * An emitter. count particles at once when it starts, and/or rate a tick while it lasts. Sizes are in
     * blocks, speeds in blocks a tick, colours RGB; "0" values are a particle's birth, "1" its death.
     */
    public record Particles(int delay, int duration, double[] offset, Shape shape, Motion motion, Sprite sprite,
            int count, float rate, float radius, float height, double[] to, float turns, float speed, float spread,
            float gravity, float drag, float turbulence, int lifeMin, int lifeMax, float size0, float size1,
            int color0, int color1, float alpha, float glow, float grow) implements Layer {
    }

    /**
     * Real geometry made of light (see Meshes.Kind): beam, lightning, ring, dome, rift, tendrils, spikes, halo.
     * yaw turns it round the vertical (degrees, Minecraft's sense): a rift faces that way.
     */
    public record Mesh(int delay, int duration, double[] offset, String kind, float radius, float height, float width,
            double[] to, int count, int color0, int color1, float alpha, float spin, float yaw) implements Layer {
    }

    public record Screen(int delay, int duration, String kind, float intensity, int color) implements Layer {
    }

    /** A full-screen shader effect (see PostFx.Kind): shock, aberration, blur, bloom, rays, desaturate, tint... */
    public record Post(int delay, int duration, String kind, float intensity, int color, double[] offset) implements Layer {
    }

    /** A brief camera move (see CameraFx.Kind): zoom, roll, letterbox, gaze (toward the scene). */
    public record Camera(int delay, int duration, String kind, float amount, double[] offset) implements Layer {
    }

    /** A tint over sky, fog and clouds while it lasts (fading in and out). */
    public record Sky(int delay, int duration, int color, float strength) implements Layer {
    }

    /** A sound the god designed: a synth patch (see Synth), rendered and played at the scene. */
    public record Tone(int delay, int duration, Synth.Patch patch, float volume, float pitch) implements Layer {
    }

    public record Sound(int delay, int duration, String key, float volume, float pitch) implements Layer {
    }

    /**
     * Blocks torn loose and flung. blocks lists explicit ones ("minecraft:stone" at an offset); otherwise
     * count blocks are picked from the ground within radius.
     */
    public record Debris(int delay, int duration, int count, float radius, float speed, float up, float spin,
            float gravity, int life, List<Chunk> blocks, String mode, float height) implements Layer {
    }

    public record Chunk(String block, double x, double y, double z) {
    }

    /** Words of light hanging in the world. */
    public record Glyph(int delay, int duration, double[] offset, String text, int color, float size, float rise)
            implements Layer {
    }

    /** Particles placed every tick by the god's own formulas (see FxField). */
    public record Field(int delay, int duration, FxField spec) implements Layer {
    }

    /** Geometry the god designed itself, moved by its formulas (see FxSolid). */
    public record Solid(int delay, int duration, FxSolid spec) implements Layer {
    }

    public static Scene decode(JsonObject o) {
        List<Layer> layers = new ArrayList<>();
        JsonElement ls = o.get("layers");
        if (ls != null && ls.isJsonArray()) {
            for (JsonElement e : ls.getAsJsonArray()) {
                if (layers.size() >= MAX_LAYERS) break;
                if (!e.isJsonObject()) continue;
                try {
                    Layer l = layer(e.getAsJsonObject());
                    if (l != null) layers.add(l);
                } catch (RuntimeException ignored) {
                    // one bad layer does not spoil the scene
                }
            }
        }
        boolean loop = bool(o, "loop");
        int longest = 0;
        for (Layer l : layers) longest = Math.max(longest, l.delay() + l.duration());
        int life = (int) num(o, "life", Math.max(1, longest), 1, MAX_LIFE);
        return new Scene(str(o, "id", "", 48), vec(o, "at", null, 30_000_000), (int) num(o, "follow", -1, -1, Integer.MAX_VALUE),
                (float) num(o, "scale", 1, 0.1, 8), life, loop, (long) num(o, "seed", 0, 0, 1L << 40),
                List.copyOf(layers));
    }

    static Layer layer(JsonObject o) {
        String type = str(o, "type", "particles", 16).toLowerCase(Locale.ROOT);
        int delay = (int) num(o, "delay", 0, 0, MAX_LIFE);
        int duration = (int) num(o, "duration", 1, 1, MAX_LIFE);
        double[] offset = vec(o, "offset", new double[3], 64);
        return switch (type) {
            case "particles" -> particles(o, delay, duration, offset, null);
            // Sugar: these are emitters with a fitting shape, sprite and motion already chosen.
            case "mesh" -> mesh(o, delay, offset, str(o, "kind", "beam", 16).toLowerCase(Locale.ROOT));
            case "beam", "lightning", "dome", "rift", "tendrils", "spikes", "halo" -> mesh(o, delay, offset, type);
            case "shockwave" -> mesh(o, delay, offset, "ring");
            case "illusion" -> debris(o, delay, str(o, "mode", "rise", 8));
            case "screen" -> new Screen(delay, (int) num(o, "duration", 20, 1, 400), str(o, "kind", "shake", 16).toLowerCase(Locale.ROOT),
                    (float) num(o, "intensity", 0.5, 0, 1), color(o.get("color"), 0xFFFFFF));
            case "post" -> new Post(delay, (int) num(o, "duration", 30, 2, MAX_LIFE), str(o, "kind", "bloom", 16).toLowerCase(Locale.ROOT),
                    (float) num(o, "intensity", 0.6, 0, 1), color(o.get("color"), 0xFFFFFF), offset);
            case "camera" -> new Camera(delay, (int) num(o, "duration", 40, 3, 240), str(o, "kind", "zoom", 16).toLowerCase(Locale.ROOT),
                    (float) num(o, "amount", 0.4, -1, 1), offset);
            case "sky" -> new Sky(delay, (int) num(o, "duration", 100, 1, MAX_LIFE), color(o.get("color"), 0x202030),
                    (float) num(o, "strength", 0.6, 0, 1));
            case "sound" -> {
                String key = str(o, "key", "", 80).toLowerCase(Locale.ROOT);
                yield key.matches("[a-z0-9_.:/-]+") ? new Sound(delay, 1, key, (float) num(o, "volume", 1, 0, 4),
                        (float) num(o, "pitch", 1, 0.5, 2)) : null;
            }
            case "synth" -> tone(o, delay);
            case "field" -> {
                FxField f = FxField.parse(o);
                yield new Field(f.delay, f.duration, f);
            }
            case "solid" -> {
                FxSolid s = FxSolid.parse(o);
                yield s == null ? null : new Solid(s.delay, s.duration, s);
            }
            case "debris" -> debris(o, delay, str(o, "mode", "fling", 8));
            case "glyph" -> {
                String text = str(o, "text", "", 64);
                yield text.isBlank() ? null : new Glyph(delay, (int) num(o, "duration", 60, 1, MAX_LIFE), offset, text,
                        color(o.get("color"), 0xFFE9A8), (float) num(o, "size", 1, 0.2, 8), (float) num(o, "rise", 0.01, -0.2, 0.2));
            }
            default -> null;
        };
    }

    private static Particles particles(JsonObject o, int delay, int duration, double[] offset, String sugar) {
        Shape shape = pick(Shape.class, str(o, "shape", null, 16), Shape.BURST);
        Motion motion = pick(Motion.class, str(o, "motion", null, 16), null);
        Sprite sprite = pick(Sprite.class, str(o, "sprite", null, 16), Sprite.MOTE);
        float radius = (float) num(o, "radius", 2, 0, 48);
        float height = (float) num(o, "height", 3, 0, 96);
        int count = (int) num(o, "count", -1, -1, MAX_COUNT);
        float rate = (float) num(o, "rate", 0, 0, MAX_RATE);
        float speed = (float) num(o, "speed", Double.NaN, 0, 3);
        float grow = (float) num(o, "grow", 0, -2, 4);
        float size0 = Float.NaN, size1 = Float.NaN;
        int[] colors = colors(o.get("color"), 0xFFE9A8);
        int lifeMin = -1, lifeMax = -1;
        if ("beam".equals(sugar)) {
            shape = Shape.BEAM;
            sprite = pick(Sprite.class, str(o, "sprite", null, 16), Sprite.RAY);
            if (motion == null) motion = Motion.STILL;
            float width = (float) num(o, "width", 0.6, 0.05, 6);
            size0 = width;
            size1 = width * 0.6f;
            if (rate == 0 && count < 0) rate = Math.min(MAX_RATE, 6 + height * 0.5f);
            lifeMin = 6;
            lifeMax = 10;
        } else if ("shockwave".equals(sugar)) {
            shape = Shape.RING;
            if (motion == null) motion = Motion.OUTWARD;
            float reach = radius;
            radius = (float) num(o, "from", 0.3, 0, 48);
            lifeMin = lifeMax = Math.max(4, duration);
            if (Float.isNaN(speed)) speed = Math.max(0.02f, (reach - radius) / lifeMax);
            if (count < 0) count = (int) Math.min(MAX_COUNT, 60 + reach * 24);
            duration = 1;
        } else if ("dome".equals(sugar)) {
            shape = Shape.DOME;
            if (motion == null) motion = Motion.STILL;
            if (rate == 0 && count < 0) rate = Math.min(MAX_RATE, 10 + radius * radius * 0.6f);
            lifeMin = 12;
            lifeMax = 24;
        }
        if (motion == null) {
            motion = switch (shape) {
                case BURST, POINT -> Motion.OUTWARD;
                case COLUMN, SPIRAL, HELIX -> Motion.UP;
                case RAIN -> Motion.FALL;
                case VORTEX -> Motion.SWIRL;
                default -> Motion.STILL;
            };
        }
        if (count < 0) count = rate > 0 ? 0 : 60;
        if (Float.isNaN(speed)) speed = motion == Motion.STILL ? 0 : 0.15f;
        double[] sizes = pair(o.get("size"), 0.25, 0.25, 0.02, 8);
        if (Float.isNaN(size0) || o.has("size")) {
            size0 = (float) sizes[0];
            size1 = (float) sizes[1];
            if (!o.has("size")) size1 = size0 * 0.3f;
        }
        double[] lives = pair(o.get("life"), 20, 40, 2, 400);
        if (lifeMin < 0 || o.has("life")) {
            lifeMin = (int) Math.min(lives[0], lives[1]);
            lifeMax = (int) Math.max(lives[0], lives[1]);
        }
        return new Particles(delay, duration, offset, shape, motion, sprite, count, rate, radius, height,
                vec(o, "to", null, 128), (float) num(o, "turns", 3, 0.1, 24), speed, (float) num(o, "spread", 0.15, 0, 4),
                (float) num(o, "gravity", 0, -0.2, 0.2), (float) num(o, "drag", 0.04, 0, 0.5),
                (float) num(o, "turbulence", 0, 0, 0.3), lifeMin, lifeMax, size0, size1, colors[0], colors[1],
                (float) num(o, "alpha", 1, 0, 1), (float) num(o, "glow", 1, 0, 1), grow);
    }

    private static Tone tone(JsonObject o, int delay) {
        List<Synth.Voice> voices = new ArrayList<>();
        JsonElement vs = o.get("voices");
        if (vs != null && vs.isJsonArray()) {
            for (JsonElement e : vs.getAsJsonArray()) {
                if (voices.size() >= Synth.MAX_VOICES || !e.isJsonObject()) continue;
                JsonObject v = e.getAsJsonObject();
                Synth.Wave wave = pick(Synth.Wave.class, str(v, "wave", null, 12), Synth.Wave.SINE);
                double[] f = pair(v.get("freq"), 440, 440, 1, 12000);
                double[] lp = pair(v.get("lowpass"), 0, 0, 0, 12000);
                double[] vib = pair(v.get("vibrato"), 0, 0, 0, 40);
                double[] trem = pair(v.get("tremolo"), 0, 0, 0, 40);
                voices.add(new Synth.Voice(wave, (int) num(v, "start", 0, 0, Synth.MAX_MS), (float) f[0], (float) f[1],
                        (int) num(v, "attack", 10, 0, Synth.MAX_MS), (int) num(v, "hold", 100, 0, Synth.MAX_MS),
                        (int) num(v, "release", 300, 0, Synth.MAX_MS), (float) num(v, "gain", 0.5, 0, 1), (float) vib[0],
                        (float) Math.min(1, vib[1]), (float) lp[0], (float) lp[1], (float) num(v, "highpass", 0, 0, 12000),
                        (float) trem[0], (float) Math.min(1, trem[1])));
            }
        }
        if (voices.isEmpty()) return null;
        double[] echo = pair(o.get("echo"), 0, 0, 0, 1000);
        Synth.Patch patch = new Synth.Patch(List.copyOf(voices), (int) echo[0], (float) Math.min(0.9, echo[1]),
                (long) num(o, "seed", 1, 0, 1L << 30));
        return new Tone(delay, Math.max(1, patch.lengthMs() / 50), patch, (float) num(o, "volume", 1, 0, 4),
                (float) num(o, "pitch", 1, 0.5, 2));
    }

    private static Mesh mesh(JsonObject o, int delay, double[] offset, String kind) {
        double[] d = switch (kind) { // duration, radius, height, width, count
            case "lightning" -> new double[] { 8, 0.5, 24, 0.12, 1 };
            case "ring" -> new double[] { 20, 8, 0, 0.8, 0 };
            case "dome" -> new double[] { 80, 5, 0, 0, 0 };
            case "rift" -> new double[] { 80, 0, 5, 1.2, 0 };
            case "tendrils" -> new double[] { 80, 2.5, 5, 0.15, 8 };
            case "spikes" -> new double[] { 60, 4, 3, 0.35, 16 };
            case "halo" -> new double[] { 80, 1.2, 2.6, 0.25, 12 };
            default -> new double[] { 40, 0, 30, 0.6, 0 };
        };
        int[] colors = colors(o.get("color"), 0xFFE9A8);
        return new Mesh(delay, (int) num(o, "duration", d[0], 2, MAX_LIFE), offset, kind, (float) num(o, "radius", d[1], 0, 64),
                (float) num(o, "height", d[2], 0, 128), (float) num(o, "width", d[3], 0.02, 12), vec(o, "to", null, 128),
                (int) num(o, "count", d[4], 0, 48), colors[0], colors[1], (float) num(o, "alpha", 1, 0, 1),
                (float) num(o, "spin", 1, -20, 20), (float) num(o, "yaw", 0, -720, 720));
    }

    private static Debris debris(JsonObject o, int delay, String mode) {
        List<Chunk> blocks = new ArrayList<>();
        JsonElement bs = o.get("blocks");
        if (bs != null && bs.isJsonArray()) {
            for (JsonElement e : bs.getAsJsonArray()) {
                if (blocks.size() >= MAX_DEBRIS || !e.isJsonObject()) continue;
                JsonObject b = e.getAsJsonObject();
                String id = str(b, "block", "", 120).toLowerCase(Locale.ROOT);
                double[] p = vec(b, "at", new double[3], 64);
                if (id.matches("[a-z0-9_.:/\\[\\]=,-]+")) blocks.add(new Chunk(id, p[0], p[1], p[2]));
            }
        }
        return new Debris(delay, 1, (int) num(o, "count", 24, 0, MAX_DEBRIS), (float) num(o, "radius", 4, 0.5, 24),
                (float) num(o, "speed", 0.35, 0, 2), (float) num(o, "up", 0.6, 0, 2.5), (float) num(o, "spin", 1, 0, 4),
                (float) num(o, "gravity", 0.04, 0, 0.2), (int) num(o, "life", mode.equalsIgnoreCase("fling") ? 60 : 100, 5, 400),
                List.copyOf(blocks), mode.toLowerCase(Locale.ROOT), (float) num(o, "height", 4, 0.5, 24));
    }

    // ---- Reading fields defensively ------------------------------------------------------------

    private static <E extends Enum<E>> E pick(Class<E> type, String name, E fallback) {
        if (name == null) return fallback;
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    static String str(JsonObject o, String key, String fallback, int max) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return fallback;
        String s = e.getAsString();
        StringBuilder b = new StringBuilder();
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
        return num(o.get(key), fallback, lo, hi);
    }

    static double num(JsonElement e, double fallback, double lo, double hi) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return fallback;
        double v = e.getAsDouble();
        if (Double.isNaN(v)) return fallback;
        return Math.max(lo, Math.min(hi, v));
    }

    /** A number or [start, end]. */
    private static double[] pair(JsonElement e, double a, double b, double lo, double hi) {
        if (e != null && e.isJsonArray() && e.getAsJsonArray().size() >= 2) {
            JsonArray arr = e.getAsJsonArray();
            return new double[] { num(arr.get(0), a, lo, hi), num(arr.get(1), b, lo, hi) };
        }
        if (e != null && e.isJsonPrimitive()) {
            double v = num(e, a, lo, hi);
            return new double[] { v, v };
        }
        return new double[] { a, b };
    }

    static double[] vec(JsonObject o, String key, double[] fallback, double limit) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() != 3) return fallback;
        double[] v = new double[3];
        for (int i = 0; i < 3; i++) {
            double c = num(e.getAsJsonArray().get(i), Double.NaN, -limit, limit);
            if (Double.isNaN(c)) return fallback;
            v[i] = c;
        }
        return v;
    }

    static int color(JsonElement e, int fallback) {
        if (e == null || !e.isJsonPrimitive()) return fallback;
        String s = e.getAsString();
        return s.matches("#[0-9a-fA-F]{6}") ? Integer.parseInt(s.substring(1), 16) : fallback;
    }

    /** "#RRGGBB" or ["#start", "#end"]. */
    private static int[] colors(JsonElement e, int fallback) {
        if (e != null && e.isJsonArray() && e.getAsJsonArray().size() >= 2) {
            int a = color(e.getAsJsonArray().get(0), fallback);
            return new int[] { a, color(e.getAsJsonArray().get(1), a) };
        }
        int c = color(e, fallback);
        return new int[] { c, c };
    }
}
