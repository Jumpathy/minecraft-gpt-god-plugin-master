package net.bigyous.gptgod.client.fx;

import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A "field" layer of a scene: particles whose place, size, brightness and colour are each a formula
 * ({@link FxExpr}) of the time and of which particle it is, so the god can draw any shape and any motion it
 * can write down. Pure. {@link #parse} never throws and clamps everything (a broken or hostile scene draws
 * nothing absurd); {@link #check} is the strict reading, with messages meant for the god.
 *
 * This file is word for word the same in the plugin and in the client mod, apart from the package line.
 */
public final class FxField {
    public static final int MAX_COUNT = 1500;
    public static final int MAX_TICKS = 20 * 60;
    public static final int DEFAULT_TICKS = 100;
    public static final int MAX_STOPS = 6;
    /** How far from the layer's place a particle may be, in blocks (before the scene's scale). */
    public static final double REACH = 96;
    public static final int DEFAULT_COLOR = 0xFFE9A8;
    /** Where {@link #at} writes its answers. */
    public static final int X = 0, Y = 1, Z = 2, SIZE = 3, ALPHA = 4, SHADE = 5, OUT = 6;

    private static final String[] FORMULAS = { "x", "y", "z", "size", "alpha", "shade" };

    public final int delay, duration, count;
    public final double[] offset;
    public final FxExpr x, y, z, size, alpha, shade;
    /** The colours shade runs along, first to last. */
    public final int[] colors;
    public final String sprite;
    public final float glow;
    /** The name of a painted texture each particle shows instead of a sprite, or "". */
    public final String texture;

    private FxField(int delay, int duration, int count, double[] offset, FxExpr[] f, int[] colors, String sprite, float glow,
            String texture) {
        this.delay = delay;
        this.duration = duration;
        this.count = count;
        this.offset = offset;
        this.x = f[0];
        this.y = f[1];
        this.z = f[2];
        this.size = f[3];
        this.alpha = f[4];
        this.shade = f[5];
        this.colors = colors;
        this.sprite = sprite;
        this.glow = glow;
        this.texture = texture;
    }

    /** Reads a layer leniently: a formula that cannot be read becomes its default. */
    public static FxField parse(JsonObject o) {
        FxExpr[] f = new FxExpr[FORMULAS.length];
        for (int k = 0; k < f.length; k++) {
            try {
                f[k] = formula(o.get(FORMULAS[k]), fallback(k));
            } catch (FxExpr.Invalid e) {
                f[k] = fallback(k);
            }
        }
        JsonElement sprite = o.get("sprite"), tex = o.get("texture");
        String texture = tex != null && tex.isJsonPrimitive() ? tex.getAsString().trim().toLowerCase(Locale.ROOT) : "";
        if (!texture.matches("[a-z0-9_.-]{1,80}")) texture = "";
        int[] colors = texture.isEmpty() || o.has("color") ? colors(o.get("color")) : new int[] { 0xFFFFFF };
        return new FxField((int) num(o.get("delay"), 0, 0, MAX_TICKS), (int) num(o.get("duration"), DEFAULT_TICKS, 1, MAX_TICKS),
                (int) num(o.get("count"), 200, 1, MAX_COUNT), vec(o.get("offset"), 64), f, colors,
                sprite != null && sprite.isJsonPrimitive() ? sprite.getAsString().trim().toLowerCase(Locale.ROOT) : "mote",
                (float) num(o.get("glow"), 1, 0, 1), texture);
    }

    /** The strict reading: says what is wrong with the first formula that cannot be read. */
    public static void check(JsonObject o) throws FxExpr.Invalid {
        boolean any = false;
        for (int k = 0; k < FORMULAS.length; k++) {
            JsonElement e = o.get(FORMULAS[k]);
            if (e == null || e.isJsonNull()) continue;
            any |= k < 3;
            try {
                formula(e, fallback(k));
            } catch (FxExpr.Invalid bad) {
                throw new FxExpr.Invalid(FORMULAS[k] + ": " + bad.getMessage());
            }
        }
        if (!any) {
            throw new FxExpr.Invalid("a field needs formulas for where its particles are: \"x\", \"y\" and \"z\" (blocks from "
                    + "the scene's place), e.g. \"x\":\"cos(u*tau)*4\". A formula may use: " + FxExpr.NAMES + ".");
        }
        JsonElement tex = o.get("texture");
        if (tex != null && !(tex.isJsonPrimitive() && tex.getAsString().trim().toLowerCase(Locale.ROOT).matches("[a-z0-9_.-]{1,80}"))) {
            throw new FxExpr.Invalid("\"texture\" is the name of a texture you painted, e.g. \"veins\"");
        }
        JsonElement n = o.get("count");
        if (n != null && n.isJsonPrimitive() && n.getAsJsonPrimitive().isNumber() && n.getAsDouble() > MAX_COUNT) {
            throw new FxExpr.Invalid("a field has at most " + MAX_COUNT + " particles");
        }
    }

    private static FxExpr fallback(int k) {
        // The shade of an unshaded field runs along its particles: the first has the first colour, the last the last.
        if (k == 5) {
            try {
                return FxExpr.compile("u");
            } catch (FxExpr.Invalid e) {
                return FxExpr.of(0);
            }
        }
        return FxExpr.of(k == 3 ? 0.25 : k == 4 ? 1 : 0);
    }

    /** A number, or a formula written as text. */
    static FxExpr formula(JsonElement e, FxExpr fallback) throws FxExpr.Invalid {
        if (e == null || e.isJsonNull()) return fallback;
        if (!e.isJsonPrimitive()) throw new FxExpr.Invalid("a formula is a number or text such as \"sin(t*0.1)*3\"");
        if (e.getAsJsonPrimitive().isNumber()) {
            double v = e.getAsDouble();
            return Double.isNaN(v) || Double.isInfinite(v) ? fallback : FxExpr.of(v);
        }
        return FxExpr.compile(e.getAsString());
    }

    /**
     * Where particle i of n is at tick t, and how it looks, written into out (X, Y, Z in blocks from the layer's
     * place, SIZE in blocks, ALPHA and SHADE 0..1).
     *
     * @param vars a scratch array of FxExpr.VARS numbers
     */
    public void at(int i, int n, double t, long seed, double[] vars, double[] out) {
        vars[FxExpr.T] = t;
        vars[FxExpr.P] = Math.max(0, Math.min(1, t / duration));
        vars[FxExpr.I] = i;
        vars[FxExpr.U] = n > 1 ? i / (double) (n - 1) : 0;
        vars[FxExpr.N] = n;
        vars[FxExpr.SEED] = seed;
        out[X] = x.eval(vars, -REACH, REACH, 0);
        out[Y] = y.eval(vars, -REACH, REACH, 0);
        out[Z] = z.eval(vars, -REACH, REACH, 0);
        out[SIZE] = size.eval(vars, 0, 8, 0.25);
        out[ALPHA] = alpha.eval(vars, 0, 1, 0) * fade(t, duration);
        out[SHADE] = shade.eval(vars, 0, 1, 0);
    }

    /** A layer appears over its first ticks and fades over its last ones instead of popping. */
    public static double fade(double t, int duration) {
        if (t < 0 || t > duration) return 0;
        double in = Math.min(4, duration * 0.2), out = Math.min(8, duration * 0.3);
        return Math.max(0, Math.min(1, Math.min(in <= 0 ? 1 : (t + 1) / in, out <= 0 ? 1 : (duration - t) / out)));
    }

    /** The colour (RGB) this far along the colours, 0..1. */
    public int color(double shade) {
        return along(colors, shade);
    }

    static int along(int[] stops, double shade) {
        if (stops.length == 1) return stops[0];
        double at = Math.max(0, Math.min(1, shade)) * (stops.length - 1);
        int k = Math.min(stops.length - 2, (int) at);
        return mix(stops[k], stops[k + 1], at - k);
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }

    // ---- Reading fields defensively --------------------------------------------------------------------

    static double num(JsonElement e, double fallback, double lo, double hi) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return fallback;
        double v = e.getAsDouble();
        return Double.isNaN(v) ? fallback : Math.max(lo, Math.min(hi, v));
    }

    static double[] vec(JsonElement e, double limit) {
        double[] v = new double[3];
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() != 3) return v;
        for (int k = 0; k < 3; k++) v[k] = num(e.getAsJsonArray().get(k), 0, -limit, limit);
        return v;
    }

    /** "#RRGGBB" or a list of them (at most MAX_STOPS); anything else is left out. */
    static int[] colors(JsonElement e) {
        int[] out = new int[MAX_STOPS];
        int n = 0;
        if (e != null && e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (int k = 0; k < a.size() && n < MAX_STOPS; k++) {
                int c = color(a.get(k));
                if (c >= 0) out[n++] = c;
            }
        } else {
            int c = color(e);
            if (c >= 0) out[n++] = c;
        }
        if (n == 0) return new int[] { DEFAULT_COLOR };
        return java.util.Arrays.copyOf(out, n);
    }

    private static int color(JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) return -1;
        String s = e.getAsString().trim();
        return s.matches("#[0-9a-fA-F]{6}") ? Integer.parseInt(s.substring(1), 16) : -1;
    }
}
