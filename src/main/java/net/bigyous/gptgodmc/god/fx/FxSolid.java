package net.bigyous.gptgodmc.god.fx;

import java.util.List;
import java.util.Locale;
import java.util.Random;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A "solid" layer of a scene: geometry the god designs itself (a box, a ball, a tube along a path, a profile
 * spun round the vertical, an outline pulled upward, or raw faces), as matter or as light, in up to 64 copies
 * that each move, turn and grow by formulas ({@link FxExpr}). Pure. {@link #parse} never throws and clamps
 * everything; {@link #check} is the strict reading, with messages meant for the god.
 *
 * This file is word for word the same in the plugin and in the client mod, apart from the package line.
 */
public final class FxSolid {
    public static final List<String> FORMS = List.of("box", "ball", "tube", "lathe", "prism", "quads");
    public static final int MAX_POINTS = 32;
    public static final int MAX_RAW = 200;
    public static final int MAX_COPIES = 64;
    /** Faces one layer may draw, over all its copies. */
    public static final int MAX_FACES = 4000;
    public static final int MAX_TICKS = 20 * 60;
    public static final int DEFAULT_TICKS = 100;
    public static final double REACH = 64;
    /** Floats a corner takes in what {@link #build} returns: x, y, z, r, g, b, a, and u, v across its texture. */
    public static final int VERTEX = 9;
    /** Floats a face takes: four corners. */
    public static final int FLOATS = 4 * VERTEX;
    public static final String TEXTURE_NAME = "[a-z0-9_.-]{1,80}";

    private static final String[] FORMULAS = { "x", "y", "z", "scale", "yaw", "pitch", "roll", "alpha" };
    private static final double[] SUN = { 0.37, 0.82, 0.43 };
    private static final float[] NOTHING = new float[0];
    private static final double TAU = Math.PI * 2;

    public final int delay, duration, count;
    public final double[] offset;
    public final String form;
    /** Made of light (it adds to what is behind it) rather than of matter. */
    public final boolean light;
    /** The colours it runs through from its lowest point to its highest. */
    public final int[] colors;
    public final FxExpr x, y, z, scale, yaw, pitch, roll, alpha;
    /** The name of the painted texture that covers it, or "" when it is plain colour. */
    public final String texture;

    /** The shape in its own space: 12 floats a face (four corners of x, y, z). */
    private final float[] model;
    /** Where each corner is on the texture: 8 floats a face. */
    private final float[] uv;
    private final int faces;
    private final double low, high, midX, midY, midZ;

    private FxSolid(int delay, int duration, int count, double[] offset, String form, boolean light, int[] colors, FxExpr[] f,
            Faces m, String texture) {
        this.delay = delay;
        this.duration = duration;
        this.count = count;
        this.offset = offset;
        this.form = form;
        this.light = light;
        this.colors = colors;
        this.x = f[0];
        this.y = f[1];
        this.z = f[2];
        this.scale = f[3];
        this.yaw = f[4];
        this.pitch = f[5];
        this.roll = f[6];
        this.alpha = f[7];
        this.texture = texture;
        this.model = java.util.Arrays.copyOf(m.v, m.n * 12);
        this.uv = java.util.Arrays.copyOf(m.uv, m.n * 8);
        this.faces = m.n;
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE, sx = 0, sy = 0, sz = 0;
        for (int k = 0; k < model.length; k += 3) {
            lo = Math.min(lo, model[k + 1]);
            hi = Math.max(hi, model[k + 1]);
            sx += model[k];
            sy += model[k + 1];
            sz += model[k + 2];
        }
        int corners = Math.max(1, model.length / 3);
        this.low = lo;
        this.high = hi;
        this.midX = sx / corners;
        this.midY = sy / corners;
        this.midZ = sz / corners;
    }

    public int faces() {
        return faces;
    }

    /** How many copies are drawn: what was asked, as far as the face budget allows. */
    public int copies() {
        return Math.max(1, Math.min(count, MAX_FACES / Math.max(1, faces)));
    }

    /** Reads a layer leniently. Null when it has no shape to draw. */
    public static FxSolid parse(JsonObject o) {
        String form = word(o.get("form"));
        Faces m = model(form, o);
        if (m == null || m.n == 0) return null;
        FxExpr[] f = new FxExpr[FORMULAS.length];
        for (int k = 0; k < f.length; k++) {
            FxExpr fallback = FxExpr.of(k == 3 || k == 7 ? 1 : 0);
            try {
                f[k] = FxField.formula(o.get(FORMULAS[k]), fallback);
            } catch (FxExpr.Invalid e) {
                f[k] = fallback;
            }
        }
        JsonElement light = o.get("light");
        String texture = word(o.get("texture"));
        if (!texture.matches(TEXTURE_NAME)) texture = "";
        // A painted solid shows its paint as it is unless a colour is given to tint it.
        int[] colors = texture.isEmpty() || o.has("color") ? FxField.colors(o.get("color")) : new int[] { 0xFFFFFF };
        return new FxSolid((int) FxField.num(o.get("delay"), 0, 0, MAX_TICKS),
                (int) FxField.num(o.get("duration"), DEFAULT_TICKS, 1, MAX_TICKS), (int) FxField.num(o.get("count"), 1, 1, MAX_COPIES),
                FxField.vec(o.get("offset"), 64), form,
                light != null && light.isJsonPrimitive() && light.getAsJsonPrimitive().isBoolean() && light.getAsBoolean(),
                colors, f, m, texture);
    }

    /** The strict reading: says what is wrong with the shape or with the first formula that cannot be read. */
    public static void check(JsonObject o) throws FxExpr.Invalid {
        String form = word(o.get("form"));
        if (!FORMS.contains(form)) {
            throw new FxExpr.Invalid("a solid needs a \"form\": " + String.join(", ", FORMS) + (form.isEmpty() ? "" : " (not '" + form + "')"));
        }
        switch (form) {
            case "tube" -> points(o, "path", 3, 2, MAX_POINTS, "[x,y,z]");
            case "lathe" -> points(o, "profile", 2, 2, MAX_POINTS, "[radius,y]");
            case "prism" -> points(o, "outline", 2, 3, MAX_POINTS, "[x,z]");
            case "quads" -> {
                JsonElement e = o.get("quads");
                if (e == null || !e.isJsonArray() || e.getAsJsonArray().isEmpty()) {
                    throw new FxExpr.Invalid("form quads needs \"quads\": a list of faces, each a list of 3 or 4 corners [x,y,z]");
                }
                if (e.getAsJsonArray().size() > MAX_RAW) throw new FxExpr.Invalid("at most " + MAX_RAW + " quads in one solid");
                int n = 0;
                for (JsonElement face : e.getAsJsonArray()) {
                    n++;
                    double[][] corners = list(face, 3, 4, REACH);
                    if (corners == null || corners.length < 3) {
                        throw new FxExpr.Invalid("quad " + n + " must be 3 or 4 corners, each [x,y,z]");
                    }
                }
            }
            default -> {
            }
        }
        if (o.has("texture") && !word(o.get("texture")).matches(TEXTURE_NAME)) {
            throw new FxExpr.Invalid("\"texture\" is the name of a texture you painted, e.g. \"veins\"");
        }
        JsonElement n = o.get("count");
        if (n != null && n.isJsonPrimitive() && n.getAsJsonPrimitive().isNumber() && n.getAsDouble() > MAX_COPIES) {
            throw new FxExpr.Invalid("a solid has at most " + MAX_COPIES + " copies (count)");
        }
        for (String key : FORMULAS) {
            try {
                FxField.formula(o.get(key), null);
            } catch (FxExpr.Invalid bad) {
                throw new FxExpr.Invalid(key + ": " + bad.getMessage());
            }
        }
    }

    private static void points(JsonObject o, String key, int dims, int least, int most, String each) throws FxExpr.Invalid {
        JsonElement e = o.get(key);
        String need = "this form needs \"" + key + "\": a list of " + least + " to " + most + " points, each " + each;
        if (e == null || !e.isJsonArray()) throw new FxExpr.Invalid(need);
        if (e.getAsJsonArray().size() > most) throw new FxExpr.Invalid(need + " (you gave " + e.getAsJsonArray().size() + ")");
        double[][] got = list(e, dims, most, REACH);
        if (got == null || got.length < least || got.length != e.getAsJsonArray().size()) throw new FxExpr.Invalid(need);
    }

    // ---- Drawing it -----------------------------------------------------------------------------------

    /**
     * Every copy's faces at tick t, in blocks from the scene's place: {@link #FLOATS} floats a face. Matter is
     * lit from above and to one side; light is not shaded.
     *
     * @param size     the scene's scale
     * @param strength multiplies the alpha (a viewer's own setting)
     * @param vars     a scratch array of FxExpr.VARS numbers
     */
    public float[] build(double t, long seed, double size, double strength, double[] vars) {
        if (t < 0 || t > duration) return NOTHING;
        int copies = copies();
        float[] out = new float[copies * faces * FLOATS];
        int at = 0;
        double fade = FxField.fade(t, duration);
        double arrive = FxExpr.ease(Math.min(1, (t + 1) / 5.0));
        double[][] c = new double[4][3];
        for (int i = 0; i < copies; i++) {
            vars[FxExpr.T] = t;
            vars[FxExpr.P] = Math.max(0, Math.min(1, t / duration));
            vars[FxExpr.I] = i;
            vars[FxExpr.U] = copies > 1 ? i / (double) (copies - 1) : 0;
            vars[FxExpr.N] = copies;
            vars[FxExpr.SEED] = seed;
            double a = alpha.eval(vars, 0, 1, 0) * fade * strength * (light ? 0.6 : 1);
            double s = scale.eval(vars, 0, 16, 1) * size * arrive;
            if (a < 0.004 || s <= 0) continue;
            double px = (offset[0] + x.eval(vars, -REACH, REACH, 0)) * size, py = (offset[1] + y.eval(vars, -REACH, REACH, 0)) * size,
                    pz = (offset[2] + z.eval(vars, -REACH, REACH, 0)) * size;
            double ya = Math.toRadians(yaw.eval(vars, -1e6, 1e6, 0)), pi = Math.toRadians(pitch.eval(vars, -1e6, 1e6, 0)),
                    ro = Math.toRadians(roll.eval(vars, -1e6, 1e6, 0));
            double cy = Math.cos(ya), sy = Math.sin(ya), cp = Math.cos(pi), sp = Math.sin(pi), cr = Math.cos(ro), sr = Math.sin(ro);
            double[] mid = turn(midX, midY, midZ, cy, sy, cp, sp, cr, sr, new double[3]);
            for (int f = 0; f < faces; f++) {
                for (int k = 0; k < 4; k++) {
                    int m = f * 12 + k * 3;
                    turn(model[m], model[m + 1], model[m + 2], cy, sy, cp, sp, cr, sr, c[k]);
                }
                double shade = 1;
                if (!light) {
                    // Newell's method gives a normal even when two corners of the face are the same point.
                    double nx = 0, ny = 0, nz = 0, fx = 0, fy = 0, fz = 0;
                    for (int k = 0; k < 4; k++) {
                        double[] p = c[k], q = c[(k + 1) & 3];
                        nx += (p[1] - q[1]) * (p[2] + q[2]);
                        ny += (p[2] - q[2]) * (p[0] + q[0]);
                        nz += (p[0] - q[0]) * (p[1] + q[1]);
                        fx += p[0] * 0.25;
                        fy += p[1] * 0.25;
                        fz += p[2] * 0.25;
                    }
                    double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (len > 1e-9) {
                        // Whichever way the corners were listed, the lit side is the one facing away from the middle.
                        double away = nx * (fx - mid[0]) + ny * (fy - mid[1]) + nz * (fz - mid[2]);
                        double lit = (nx * SUN[0] + ny * SUN[1] + nz * SUN[2]) / len * (away < 0 ? -1 : 1);
                        shade = Math.max(0.22, Math.min(1, 0.62 + 0.38 * lit));
                    }
                }
                for (int k = 0; k < 4; k++) {
                    double height = high > low ? (model[f * 12 + k * 3 + 1] - low) / (high - low) : 0;
                    int rgb = FxField.along(colors, height);
                    out[at++] = (float) (px + c[k][0] * s);
                    out[at++] = (float) (py + c[k][1] * s);
                    out[at++] = (float) (pz + c[k][2] * s);
                    out[at++] = (float) (((rgb >> 16) & 255) / 255.0 * shade);
                    out[at++] = (float) (((rgb >> 8) & 255) / 255.0 * shade);
                    out[at++] = (float) ((rgb & 255) / 255.0 * shade);
                    out[at++] = (float) a;
                    out[at++] = uv[f * 8 + k * 2];
                    out[at++] = uv[f * 8 + k * 2 + 1];
                }
            }
        }
        return at == out.length ? out : java.util.Arrays.copyOf(out, at);
    }

    /** Roll, then pitch, then yaw (the angles' cosines and sines), into out. */
    private static double[] turn(double x, double y, double z, double cy, double sy, double cp, double sp, double cr, double sr,
            double[] out) {
        double x1 = x * cr - y * sr, y1 = x * sr + y * cr;
        double y2 = y1 * cp - z * sp, z2 = y1 * sp + z * cp;
        out[0] = x1 * cy - z2 * sy;
        out[1] = y2;
        out[2] = x1 * sy + z2 * cy;
        return out;
    }

    /**
     * Points along the edges of what {@link #build} made, for a sketch in dots where the real thing cannot be
     * drawn: each is x, y, z and a colour (RGB, as a double).
     */
    public static void sketch(float[] built, int points, Random rnd, List<double[]> out) {
        int n = built.length / FLOATS;
        if (n == 0) return;
        for (int k = 0; k < points; k++) {
            int face = rnd.nextInt(n) * FLOATS, corner = rnd.nextInt(4);
            int a = face + corner * VERTEX, b = face + ((corner + 1) & 3) * VERTEX;
            double s = rnd.nextDouble();
            int rgb = Math.round(built[a + 3] * 255) << 16 | Math.round(built[a + 4] * 255) << 8 | Math.round(built[a + 5] * 255);
            out.add(new double[] { built[a] + (built[b] - built[a]) * s, built[a + 1] + (built[b + 1] - built[a + 1]) * s,
                    built[a + 2] + (built[b + 2] - built[a + 2]) * s, rgb });
        }
    }

    // ---- The shapes -----------------------------------------------------------------------------------

    private static final class Faces {
        float[] v = new float[12 * 64];
        float[] uv = new float[8 * 64];
        int n;
        /** How many times the texture repeats across and up. */
        double tileU = 1, tileV = 1;

        /** A face whose first corner is the texture's bottom left, then top left, top right, bottom right. */
        void quad(double[] a, double[] b, double[] c, double[] d) {
            quad(a, b, c, d, 0, 1, 0, 0, 1, 0, 1, 1);
        }

        /** A face with each corner's place on the texture: u, v for a, b, c and d. */
        void quad(double[] a, double[] b, double[] c, double[] d, double... t) {
            if (n >= MAX_FACES) return;
            if ((n + 1) * 12 > v.length) {
                v = java.util.Arrays.copyOf(v, v.length * 2);
                uv = java.util.Arrays.copyOf(uv, uv.length * 2);
            }
            int at = n * 12;
            for (double[] p : new double[][] { a, b, c, d }) {
                v[at++] = (float) p[0];
                v[at++] = (float) p[1];
                v[at++] = (float) p[2];
            }
            for (int k = 0; k < 8; k += 2) {
                uv[n * 8 + k] = (float) (t[k] * tileU);
                uv[n * 8 + k + 1] = (float) (t[k + 1] * tileV);
            }
            n++;
        }
    }

    private static Faces model(String form, JsonObject o) {
        Faces m = new Faces();
        JsonElement tile = o.get("tile");
        m.tileU = m.tileV = FxField.num(tile, 1, 0.05, 32);
        if (tile != null && tile.isJsonArray() && tile.getAsJsonArray().size() == 2) {
            m.tileU = FxField.num(tile.getAsJsonArray().get(0), 1, 0.05, 32);
            m.tileV = FxField.num(tile.getAsJsonArray().get(1), 1, 0.05, 32);
        }
        switch (form) {
            case "box" -> box(m, o);
            case "ball" -> ball(m, o);
            case "tube" -> tube(m, o);
            case "lathe" -> lathe(m, o);
            case "prism" -> prism(m, o);
            case "quads" -> raw(m, o);
            default -> {
                return null;
            }
        }
        return m;
    }

    private static double[] p(double x, double y, double z) {
        return new double[] { x, y, z };
    }

    /** A box round its own middle. size is [x, y, z] or one number. */
    private static void box(Faces m, JsonObject o) {
        JsonElement e = o.get("size");
        double all = FxField.num(e, 1, 0.02, REACH);
        double x = all / 2, y = all / 2, z = all / 2;
        if (e != null && e.isJsonArray() && e.getAsJsonArray().size() == 3) {
            x = FxField.num(e.getAsJsonArray().get(0), 1, 0.02, REACH) / 2;
            y = FxField.num(e.getAsJsonArray().get(1), 1, 0.02, REACH) / 2;
            z = FxField.num(e.getAsJsonArray().get(2), 1, 0.02, REACH) / 2;
        }
        m.quad(p(x, -y, -z), p(x, y, -z), p(x, y, z), p(x, -y, z));
        m.quad(p(-x, -y, z), p(-x, y, z), p(-x, y, -z), p(-x, -y, -z));
        m.quad(p(-x, y, -z), p(-x, y, z), p(x, y, z), p(x, y, -z));
        m.quad(p(-x, -y, z), p(-x, -y, -z), p(x, -y, -z), p(x, -y, z));
        m.quad(p(x, -y, z), p(x, y, z), p(-x, y, z), p(-x, -y, z));
        m.quad(p(-x, -y, -z), p(-x, y, -z), p(x, y, -z), p(x, -y, -z));
    }

    /** A ball round its own middle. */
    private static void ball(Faces m, JsonObject o) {
        double r = FxField.num(o.get("radius"), 1, 0.02, REACH);
        int round = (int) FxField.num(o.get("sides"), 12, 6, 24), bands = Math.max(3, round / 2);
        for (int i = 0; i < bands; i++) {
            double t0 = -Math.PI / 2 + Math.PI * i / bands, t1 = -Math.PI / 2 + Math.PI * (i + 1) / bands;
            for (int j = 0; j < round; j++) {
                double a0 = TAU * j / round, a1 = TAU * (j + 1) / round;
                double u0 = j / (double) round, u1 = (j + 1) / (double) round, v0 = 1 - i / (double) bands, v1 = 1 - (i + 1) / (double) bands;
                m.quad(ball(r, t0, a0), ball(r, t0, a1), ball(r, t1, a1), ball(r, t1, a0), u0, v0, u1, v0, u1, v1, u0, v1);
            }
        }
    }

    private static double[] ball(double r, double up, double round) {
        return p(Math.cos(up) * Math.cos(round) * r, Math.sin(up) * r, Math.cos(up) * Math.sin(round) * r);
    }

    /** A profile of [radius, y] points spun round the vertical: a chalice, a pillar, a bell, a spire. */
    private static void lathe(Faces m, JsonObject o) {
        double[][] pts = list(o.get("profile"), 2, MAX_POINTS, REACH);
        if (pts == null || pts.length < 2) return;
        int round = (int) FxField.num(o.get("sides"), 12, 3, 32);
        for (int k = 0; k + 1 < pts.length; k++) {
            double r0 = Math.abs(pts[k][0]), y0 = pts[k][1], r1 = Math.abs(pts[k + 1][0]), y1 = pts[k + 1][1];
            if (r0 < 1e-6 && r1 < 1e-6) continue;
            for (int j = 0; j < round; j++) {
                double a0 = TAU * j / round, a1 = TAU * (j + 1) / round;
                double u0 = j / (double) round, u1 = (j + 1) / (double) round, v0 = 1 - k / (pts.length - 1.0), v1 = 1 - (k + 1) / (pts.length - 1.0);
                m.quad(ring(r0, y0, a0), ring(r0, y0, a1), ring(r1, y1, a1), ring(r1, y1, a0), u0, v0, u1, v0, u1, v1, u0, v1);
            }
        }
        // An open end is closed with a flat lid, so it reads as solid.
        for (double[] end : new double[][] { pts[0], pts[pts.length - 1] }) {
            double r = Math.abs(end[0]);
            if (r < 1e-6) continue;
            for (int j = 0; j < round; j++) {
                double a0 = TAU * j / round, a1 = TAU * (j + 1) / round;
                double c0 = 0.5 + Math.cos(a0) * 0.5, s0 = 0.5 + Math.sin(a0) * 0.5, c1 = 0.5 + Math.cos(a1) * 0.5, s1 = 0.5 + Math.sin(a1) * 0.5;
                m.quad(p(0, end[1], 0), ring(r, end[1], a0), ring(r, end[1], a1), ring(r, end[1], a1), 0.5, 0.5, c0, s0, c1, s1, c1, s1);
            }
        }
    }

    private static double[] ring(double r, double y, double a) {
        return p(Math.cos(a) * r, y, Math.sin(a) * r);
    }

    /** An outline of [x, z] points on the ground pulled up to a height; taper narrows the top (0 = to a point). */
    private static void prism(Faces m, JsonObject o) {
        double[][] pts = list(o.get("outline"), 2, MAX_POINTS, REACH);
        if (pts == null || pts.length < 3) return;
        double h = FxField.num(o.get("height"), 1, 0.02, REACH), taper = FxField.num(o.get("taper"), 1, 0, 4);
        double cx = 0, cz = 0;
        for (double[] q : pts) {
            cx += q[0] / pts.length;
            cz += q[1] / pts.length;
        }
        double span = 1e-6;
        for (double[] q : pts) span = Math.max(span, 2 * Math.max(Math.abs(q[0] - cx), Math.abs(q[1] - cz)));
        for (int k = 0; k < pts.length; k++) {
            double[] a = pts[k], b = pts[(k + 1) % pts.length];
            double[] a0 = p(a[0], 0, a[1]), b0 = p(b[0], 0, b[1]);
            double[] a1 = p(cx + (a[0] - cx) * taper, h, cz + (a[1] - cz) * taper), b1 = p(cx + (b[0] - cx) * taper, h, cz + (b[1] - cz) * taper);
            double u0 = k / (double) pts.length, u1 = (k + 1) / (double) pts.length;
            m.quad(a0, b0, b1, a1, u0, 1, u1, 1, u1, 0, u0, 0);
            double ua = 0.5 + (a[0] - cx) / span, va = 0.5 + (a[1] - cz) / span, ub = 0.5 + (b[0] - cx) / span, vb = 0.5 + (b[1] - cz) / span;
            m.quad(p(cx, 0, cz), a0, b0, b0, 0.5, 0.5, ua, va, ub, vb, ub, vb);
            if (taper > 1e-6) m.quad(p(cx, h, cz), a1, b1, b1, 0.5, 0.5, ua, va, ub, vb, ub, vb);
        }
    }

    /** A tube along a path of [x, y, z] points. width is its thickness, or [at the start, at the end]. */
    private static void tube(Faces m, JsonObject o) {
        double[][] pts = list(o.get("path"), 3, MAX_POINTS, REACH);
        if (pts == null || pts.length < 2) return;
        int round = (int) FxField.num(o.get("sides"), 6, 3, 12);
        JsonElement w = o.get("width");
        double w0 = FxField.num(w, 0.4, 0, 16), w1 = w0;
        if (w != null && w.isJsonArray() && w.getAsJsonArray().size() >= 2) {
            w0 = FxField.num(w.getAsJsonArray().get(0), 0.4, 0, 16);
            w1 = FxField.num(w.getAsJsonArray().get(1), 0.4, 0, 16);
        }
        double[][] prev = null;
        double[] side = null;
        for (int k = 0; k < pts.length; k++) {
            double[] before = pts[Math.max(0, k - 1)], after = pts[Math.min(pts.length - 1, k + 1)];
            double[] along = unit(after[0] - before[0], after[1] - before[1], after[2] - before[2]);
            if (along == null) along = p(0, 1, 0);
            // Carry the last ring's sideways direction forward, so the tube does not twist at a bend.
            double[] s = null;
            if (side != null) {
                double d = side[0] * along[0] + side[1] * along[1] + side[2] * along[2];
                s = unit(side[0] - along[0] * d, side[1] - along[1] * d, side[2] - along[2] * d);
            }
            if (s == null) s = Math.abs(along[1]) > 0.9 ? unit(1 - along[0] * along[0], -along[0] * along[1], -along[0] * along[2])
                    : unit(-along[1] * along[0], 1 - along[1] * along[1], -along[1] * along[2]);
            if (s == null) s = p(1, 0, 0);
            side = s;
            double[] other = { along[1] * s[2] - along[2] * s[1], along[2] * s[0] - along[0] * s[2], along[0] * s[1] - along[1] * s[0] };
            double r = (w0 + (w1 - w0) * k / (pts.length - 1)) / 2;
            double[][] ring = new double[round][];
            for (int j = 0; j < round; j++) {
                double a = TAU * j / round, c = Math.cos(a) * r, sn = Math.sin(a) * r;
                ring[j] = p(pts[k][0] + s[0] * c + other[0] * sn, pts[k][1] + s[1] * c + other[1] * sn, pts[k][2] + s[2] * c + other[2] * sn);
            }
            for (int j = 0; j < round; j++) {
                int j1 = (j + 1) % round;
                double u0 = j / (double) round, u1 = (j + 1) / (double) round, v0 = 1 - (k - 1) / (pts.length - 1.0), v1 = 1 - k / (pts.length - 1.0);
                if (prev != null) m.quad(prev[j], prev[j1], ring[j1], ring[j], u0, v0, u1, v0, u1, v1, u0, v1);
                if ((k == 0 || k == pts.length - 1) && r > 1e-6) m.quad(pts[k], ring[j], ring[j1], ring[j1]);
            }
            prev = ring;
        }
    }

    private static double[] unit(double x, double y, double z) {
        double len = Math.sqrt(x * x + y * y + z * z);
        return len < 1e-9 ? null : p(x / len, y / len, z / len);
    }

    /** Faces given corner by corner: three or four [x, y, z] each. */
    private static void raw(Faces m, JsonObject o) {
        JsonElement e = o.get("quads");
        if (e == null || !e.isJsonArray()) return;
        JsonArray a = e.getAsJsonArray();
        for (int k = 0; k < a.size() && m.n < MAX_RAW; k++) {
            double[][] c = list(a.get(k), 3, 4, REACH);
            if (c == null || c.length < 3) continue;
            m.quad(c[0], c[1], c[2], c[c.length - 1]);
        }
    }

    // ---- Reading fields defensively --------------------------------------------------------------------

    private static String word(JsonElement e) {
        return e != null && e.isJsonPrimitive() ? e.getAsString().trim().toLowerCase(Locale.ROOT) : "";
    }

    /** A list of points of this many numbers each (at most max; malformed ones are left out), or null. */
    private static double[][] list(JsonElement e, int dims, int max, double limit) {
        if (e == null || !e.isJsonArray()) return null;
        JsonArray a = e.getAsJsonArray();
        double[][] out = new double[Math.min(max, a.size())][];
        int n = 0;
        for (int k = 0; k < a.size() && n < out.length; k++) {
            JsonElement pt = a.get(k);
            if (!pt.isJsonArray() || pt.getAsJsonArray().size() != dims) continue;
            double[] v = new double[dims];
            boolean ok = true;
            for (int d = 0; d < dims; d++) {
                v[d] = FxField.num(pt.getAsJsonArray().get(d), Double.NaN, -limit, limit);
                ok &= !Double.isNaN(v[d]);
            }
            if (ok) out[n++] = v;
        }
        return n == out.length ? out : java.util.Arrays.copyOf(out, n);
    }
}
