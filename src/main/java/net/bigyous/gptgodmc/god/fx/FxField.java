package net.bigyous.gptgodmc.god.fx;

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
    /**
     * A field whose particles keep their motion: x, y, z are then where each is born, vx, vy, vz its first speed
     * (blocks a tick), ax, ay, az the push on it every tick, life how many ticks it lasts before it is born again.
     */
    public final boolean free;
    public final FxExpr vx, vy, vz, ax, ay, az, life;
    /** The share of its speed a free particle loses each tick, and keeps when it hits a block (below 0: it passes through). */
    public final double drag, bounce;
    public static final double MAX_SPEED = 2, MAX_PUSH = 1;
    /** What a free particle remembers: its place, its speed, when it was born, how long it lives, which life. */
    public static final int STATE = 9;
    private static final String[] MOTION = { "vx", "vy", "vz", "ax", "ay", "az", "life" };

    /** The blocks a free particle may bounce off: whether this point, in blocks from the layer's place, is inside one. */
    public interface Blocks {
        boolean solid(double x, double y, double z);
    }
    /** The colours shade runs along, first to last. */
    public final int[] colors;
    public final String sprite;
    public final float glow;
    /** The name of a painted texture each particle shows instead of a sprite, or "". */
    public final String texture;

    private FxField(int delay, int duration, int count, double[] offset, FxExpr[] f, int[] colors, String sprite, float glow,
            String texture, FxExpr[] m, double drag, double bounce) {
        boolean any = bounce >= 0;
        for (FxExpr e : m) any |= e != null;
        this.free = any;
        FxExpr zero = FxExpr.of(0);
        this.vx = m[0] == null ? zero : m[0];
        this.vy = m[1] == null ? zero : m[1];
        this.vz = m[2] == null ? zero : m[2];
        this.ax = m[3] == null ? zero : m[3];
        this.ay = m[4] == null ? zero : m[4];
        this.az = m[5] == null ? zero : m[5];
        this.life = m[6];
        this.drag = drag;
        this.bounce = bounce;
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
        FxExpr[] m = new FxExpr[MOTION.length];
        for (int k = 0; k < m.length; k++) {
            try {
                m[k] = formula(o.get(MOTION[k]), null);
            } catch (FxExpr.Invalid e) {
                m[k] = null;
            }
        }
        return new FxField((int) num(o.get("delay"), 0, 0, MAX_TICKS), (int) num(o.get("duration"), DEFAULT_TICKS, 1, MAX_TICKS),
                (int) num(o.get("count"), 200, 1, MAX_COUNT), vec(o.get("offset"), 64), f, colors,
                sprite != null && sprite.isJsonPrimitive() ? sprite.getAsString().trim().toLowerCase(Locale.ROOT) : "mote",
                (float) num(o.get("glow"), 1, 0, 1), texture, m, num(o.get("drag"), 0.02, 0, 1), num(o.get("bounce"), -1, -1, 1));
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
        for (String key : MOTION) {
            try {
                formula(o.get(key), null);
            } catch (FxExpr.Invalid bad) {
                throw new FxExpr.Invalid(key + ": " + bad.getMessage());
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
        // An unsized field is small motes, no two the same size.
        if (k == 5 || k == 3) {
            try {
                return FxExpr.compile(k == 5 ? "u" : "0.06 + 0.1*rand(7)");
            } catch (FxExpr.Invalid e) {
                return FxExpr.of(k == 5 ? 0 : 0.1);
            }
        }
        return FxExpr.of(k == 4 ? 1 : 0);
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
        fill(i, n, t, seed, vars);
        for (int k = FxExpr.PX; k <= FxExpr.A; k++) vars[k] = 0;
        out[X] = x.eval(vars, -REACH, REACH, 0);
        out[Y] = y.eval(vars, -REACH, REACH, 0);
        out[Z] = z.eval(vars, -REACH, REACH, 0);
        looks(t, vars, out);
    }

    private void fill(int i, int n, double t, long seed, double[] vars) {
        vars[FxExpr.T] = t;
        vars[FxExpr.P] = Math.max(0, Math.min(1, t / duration));
        vars[FxExpr.I] = i;
        vars[FxExpr.U] = n > 1 ? i / (double) (n - 1) : 0;
        vars[FxExpr.N] = n;
        vars[FxExpr.SEED] = seed;
    }

    /** What a free particle knows of itself. */
    private static void own(double t, double[] vars, double[] state) {
        for (int k = 0; k < 6; k++) vars[FxExpr.PX + k] = state[k];
        vars[FxExpr.K] = state[8];
        vars[FxExpr.A] = state[7] > 0 ? Math.max(0, Math.min(1, (t - state[6]) / state[7])) : 0;
    }

    /** A free particle is born (again): at x, y, z with its first speed. state holds STATE numbers; state[8] is which life. */
    public void birth(int i, int n, double t, long seed, double[] vars, double[] state) {
        fill(i, n, t, seed, vars);
        for (int k = 0; k < 8; k++) state[k] = 0;
        state[6] = t;
        own(t, vars, state);
        state[0] = x.eval(vars, -REACH, REACH, 0);
        state[1] = y.eval(vars, -REACH, REACH, 0);
        state[2] = z.eval(vars, -REACH, REACH, 0);
        state[3] = vx.eval(vars, -MAX_SPEED, MAX_SPEED, 0);
        state[4] = vy.eval(vars, -MAX_SPEED, MAX_SPEED, 0);
        state[5] = vz.eval(vars, -MAX_SPEED, MAX_SPEED, 0);
        state[7] = life == null ? 0 : life.eval(vars, 1, MAX_TICKS, 0);
    }

    /**
     * One tick of a free particle: it is pushed, slowed, moved, and turned back by blocks when it bounces; when
     * its life is over it is born again.
     *
     * @param blocks what it may hit, or null
     */
    public void step(int i, int n, double t, long seed, double[] vars, double[] state, Blocks blocks) {
        if (state[7] > 0 && t - state[6] >= state[7]) {
            state[8]++;
            birth(i, n, t, seed, vars, state);
            return;
        }
        fill(i, n, t, seed, vars);
        own(t, vars, state);
        double px = ax.eval(vars, -MAX_PUSH, MAX_PUSH, 0), py = ay.eval(vars, -MAX_PUSH, MAX_PUSH, 0),
                pz = az.eval(vars, -MAX_PUSH, MAX_PUSH, 0);
        state[3] = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, (state[3] + px) * (1 - drag)));
        state[4] = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, (state[4] + py) * (1 - drag)));
        state[5] = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, (state[5] + pz) * (1 - drag)));
        boolean hits = bounce >= 0 && blocks != null;
        for (int c = 0; c < 3; c++) {
            double was = state[c];
            state[c] = Math.max(-REACH, Math.min(REACH, was + state[3 + c]));
            if (hits && blocks.solid(state[0], state[1], state[2])) {
                state[c] = was;
                state[3 + c] = -state[3 + c] * bounce;
            }
        }
    }

    /** Where a free particle is and how it looks, as {@link #at} gives it for the others. */
    public void look(int i, int n, double t, long seed, double[] vars, double[] state, double[] out) {
        fill(i, n, t, seed, vars);
        own(t, vars, state);
        out[X] = state[0];
        out[Y] = state[1];
        out[Z] = state[2];
        looks(t, vars, out);
    }

    private void looks(double t, double[] vars, double[] out) {
        out[SIZE] = size.eval(vars, 0, 8, 0.1);
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
