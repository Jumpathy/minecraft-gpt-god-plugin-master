package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.god.fx.FxExpr;

/**
 * The {@code seal} scene layer: a flat emblem of light the god draws itself, as line art on a plane (rings,
 * polygons, stars, spokes, curves from its own formulas, letters round a circle, symbols) with symbols that
 * drift up off it. Pure: this is the language (lenient {@link #parse}, strict {@link #check}) and the geometry
 * ({@link #frame}: every line and letter at a moment, in the plane's own coordinates). {@link FxSealShow}
 * shows it with flat display entities, so every mortal sees real surfaces in the world, with or without the mod.
 */
public final class FxSeal {
    public static final int MAX_PARTS = 16;
    /** Display entities one seal may use. */
    public static final int MAX_PIECES = 500;
    public static final int DEFAULT_TICKS = 100;
    public static final double MAX_RADIUS = 12;
    public static final List<String> SHAPES = List.of("ring", "polygon", "star", "spokes", "curve", "runes", "marks", "motes");
    public static final List<String> FONTS = List.of("runes", "illager", "plain");
    public static final List<String> PLANES = List.of("ground", "upright", "facing");

    public static final String HELP = "seal {a flat emblem of light on a plane, drawn in real flat lines that every mortal "
            + "sees (not particles): magic circles, wards, glyphs on the ground or standing in the air. plane: "
            + String.join("|", PLANES) + " (facing = upright, turned to the nearest mortal), or tilt 0-90 and yaw degrees; "
            + "radius in blocks (max 12); color; spin (degrees a second); appear and fade ticks; scale (a formula of t, "
            + "e.g. \"1+0.05*sin(t/6)\" to breathe); parts: up to " + MAX_PARTS + " of {shape: ring {r, dashes, gap 0-1, arc "
            + "[from,to] degrees} | polygon {sides, r} | star {sides = points, skip, r} | spokes {count, r [inner, outer]} | "
            + "curve {rho: a formula of u (0-1 round the circle) and t for the distance from the middle, e.g. "
            + "\"0.6+0.3*cos(tau*6*u)\" is a six-petalled flower, turns; or x and y formulas of u} | runes {r, count, size, "
            + "text, font: " + String.join("|", FONTS) + ": letters round a circle} | marks {text: a symbol such as + "
            + "✦ ❄ ☽ ☠ ❤, count, r, size: laid flat round a circle, r 0 = the middle} | motes {text, count, r, "
            + "rise (blocks a second), height, size: symbols drifting up off the seal}. r is a share of the radius (1 = "
            + "the rim). Any part may have color (or [first, last]), alpha, width (blocks), glow 0|1, spin (its own, "
            + "negative turns the other way), delay (ticks before it draws itself), and copies + orbit (that many copies "
            + "round a circle of that share: small seals round a great one)}. Layer parts to make it yours: several "
            + "rings, a star inside, runes between, counter-turning. Example, a healing seal: {\"type\":\"seal\",\"radius\":3,"
            + "\"color\":\"#7CFF6B\",\"duration\":120,\"parts\":[{\"shape\":\"ring\",\"r\":1,\"width\":0.12},{\"shape\":\"ring\","
            + "\"r\":0.8,\"dashes\":12,\"spin\":-30},{\"shape\":\"star\",\"sides\":8,\"skip\":3,\"r\":0.78,\"spin\":12,\"delay\":6},"
            + "{\"shape\":\"runes\",\"r\":0.9,\"count\":20,\"size\":0.3,\"spin\":20},{\"shape\":\"ring\",\"r\":0.22,\"copies\":4,"
            + "\"orbit\":0.5,\"delay\":10},{\"shape\":\"motes\",\"text\":\"+\",\"count\":10,\"r\":0.9,\"height\":3}]}.";

    /** One part of a seal, as written (numbers already kept within their limits). */
    public static final class Part {
        public String shape = "ring";
        public double r = 1, rIn, width = 0.08, alpha = 1, spin, orbit, gap = 0.4, arcFrom, arcTo = 360, turns = 1;
        public double size = 0.35, rise = 0.6, height = 2.5;
        public int c0 = 0xFFE9A8, c1 = 0xFFE9A8, delay, copies = 1, count = 8, skip = 1, dashes, segs = 1;
        public boolean glow = true;
        public FxExpr rho, x, y;
        public String text = "", font = "plain";

        boolean lines() {
            return !(shape.equals("runes") || shape.equals("marks") || shape.equals("motes"));
        }
    }

    /** A flat stroke from a to b in the plane (blocks from the seal's middle); lift orders overlapping strokes. */
    public record Line(double ax, double ay, double bx, double by, double width, int argb, double lift, boolean halo) {
    }

    /** A letter or symbol lying in the plane, its top turned by angle (radians, 0 = the plane's up). */
    public record Glyph(double x, double y, double angle, double size, String text, String font, int argb) {
    }

    /** A symbol off the plane: h blocks along the plane's normal, facing whoever looks. */
    public record Mote(double x, double y, double h, double size, String text, String font, int argb) {
    }

    /** Everything at one moment. The counts and order never change with time, so pieces keep their entities. */
    public record Frame(List<Line> lines, List<Glyph> glyphs, List<Mote> motes) {
    }

    public int delay, duration = DEFAULT_TICKS, appear = 12, fade = 12;
    public double radius = 3, tilt, yaw, spin;
    public boolean facing;
    /** Set by the show for a looping scene: it never fades by itself. */
    public boolean endless;
    public double[] offset = new double[3];
    public FxExpr scale = FxExpr.of(1);
    public final List<Part> parts = new ArrayList<>();

    private FxSeal() {
    }

    /** Whether lines need a second face (a flat surface shows only its front; a seal on the ground is seen from above). */
    public boolean twoFaced() {
        return facing || tilt > 20;
    }

    /** How many display entities it takes. */
    public int pieces() {
        int n = 0;
        for (Part p : parts) {
            if (p.shape.equals("motes")) {
                n += p.count;
            } else if (p.lines()) {
                // The soft halo under a stroke is drawn on the front only.
                n += p.copies * p.segs * ((p.glow ? 2 : 1) + (twoFaced() ? 1 : 0));
            } else {
                n += p.copies * p.count * (twoFaced() ? 2 : 1);
            }
        }
        return n;
    }

    // ---- Reading what the god wrote ------------------------------------------------------------------

    /** Lenient: clamps everything, never throws. sceneScale multiplies the sizes. */
    public static FxSeal parse(JsonObject l, double sceneScale) {
        try {
            return read(l, sceneScale, false, 0);
        } catch (FxScenes.Invalid e) {
            throw new IllegalStateException(e);
        }
    }

    /** Strict: complains, in words for the god, about what it cannot draw. */
    public static void check(JsonObject l, int n) throws FxScenes.Invalid {
        FxSeal s = read(l, 1, true, n);
        if (s.parts.isEmpty()) {
            throw new FxScenes.Invalid("Layer " + n + ": a seal needs \"parts\", an array of shapes (" + SHAPES + ").");
        }
        if (s.pieces() > MAX_PIECES) {
            throw new FxScenes.Invalid("Layer " + n + ": this seal is too intricate (" + s.pieces() + " strokes, at most "
                    + MAX_PIECES + "). Use fewer parts or copies, a smaller radius, or \"glow\": 0 on some parts"
                    + (s.twoFaced() ? "; a seal on the ground costs half what a standing one does" : "") + ".");
        }
    }

    private static FxSeal read(JsonObject l, double sceneScale, boolean strict, int n) throws FxScenes.Invalid {
        FxSeal s = new FxSeal();
        double k = Math.max(0.25, Math.min(4, sceneScale));
        s.delay = (int) num(l, "delay", 0, 0, FxScenes.MAX_TICKS);
        s.duration = (int) num(l, "duration", DEFAULT_TICKS, 10, FxScenes.MAX_TICKS);
        s.appear = (int) num(l, "appear", 12, 0, 200);
        s.fade = (int) num(l, "fade", 12, 0, 200);
        s.radius = num(l, "radius", 3, 0.3, MAX_RADIUS) * k;
        s.spin = num(l, "spin", 0, -720, 720);
        s.yaw = num(l, "yaw", 0, -360, 360);
        String plane = word(l, "plane", l.has("tilt") ? "" : "ground");
        if (strict && !plane.isEmpty() && !PLANES.contains(plane)) {
            throw new FxScenes.Invalid("Layer " + n + ": unknown plane '" + plane + "'. Choose from " + PLANES + ", or give tilt and yaw.");
        }
        s.facing = plane.equals("facing");
        s.tilt = plane.equals("upright") || s.facing ? 90 : plane.equals("ground") ? 0 : num(l, "tilt", 0, 0, 90);
        JsonElement off = l.get("offset");
        if (off != null && off.isJsonArray() && off.getAsJsonArray().size() == 3) {
            for (int i = 0; i < 3; i++) {
                try {
                    s.offset[i] = Math.max(-128, Math.min(128, off.getAsJsonArray().get(i).getAsDouble())) * k;
                } catch (RuntimeException e) {
                    s.offset[i] = 0;
                }
            }
        }
        s.scale = formula(l, "scale", FxExpr.of(1), strict, n);
        int[] base = colors(l.get("color"), new int[] { 0xFFE9A8, 0xFFE9A8 }, strict, n);
        JsonElement ps = l.get("parts");
        if (ps == null || !ps.isJsonArray()) return s;
        JsonArray arr = ps.getAsJsonArray();
        if (strict && arr.size() > MAX_PARTS) {
            throw new FxScenes.Invalid("Layer " + n + ": at most " + MAX_PARTS + " parts in one seal (you gave " + arr.size() + ").");
        }
        for (JsonElement e : arr) {
            if (s.parts.size() >= MAX_PARTS) break;
            if (!e.isJsonObject()) {
                if (strict) throw new FxScenes.Invalid("Layer " + n + ": each part of a seal is an object with a \"shape\".");
                continue;
            }
            JsonObject o = e.getAsJsonObject();
            Part p = new Part();
            p.shape = word(o, "shape", "ring");
            if (!SHAPES.contains(p.shape)) {
                if (strict) throw new FxScenes.Invalid("Layer " + n + ": unknown seal shape '" + p.shape + "'. Shapes: " + SHAPES + ".");
                continue;
            }
            int[] c = colors(o.get("color"), base, strict, n);
            p.c0 = c[0];
            p.c1 = c[1];
            p.alpha = num(o, "alpha", 1, 0.15, 1);
            p.width = num(o, "width", 0.08, 0.02, 0.6) * k;
            p.glow = num(o, "glow", 1, 0, 1) >= 0.5;
            p.spin = num(o, "spin", 0, -720, 720);
            p.delay = (int) num(o, "delay", 0, 0, FxScenes.MAX_TICKS);
            p.copies = (int) num(o, "copies", 1, 1, 16);
            p.orbit = num(o, "orbit", p.copies > 1 ? 0.6 : 0, 0, 1.5);
            JsonElement r = o.get("r");
            if (r != null && r.isJsonArray() && r.getAsJsonArray().size() >= 2) {
                try {
                    p.rIn = Math.max(0, Math.min(1.5, r.getAsJsonArray().get(0).getAsDouble()));
                    p.r = Math.max(0, Math.min(1.5, r.getAsJsonArray().get(1).getAsDouble()));
                } catch (RuntimeException ex) {
                    p.r = 1;
                }
            } else {
                boolean middle = p.shape.equals("marks") || p.shape.equals("motes");
                p.r = num(o, "r", p.shape.equals("marks") ? 0.7 : p.shape.equals("motes") ? 0.8 : 1, middle ? 0 : 0.02, 1.5);
            }
            switch (p.shape) {
                case "ring" -> {
                    p.dashes = (int) num(o, "dashes", 0, 0, 64);
                    p.gap = num(o, "gap", 0.4, 0.05, 0.9);
                    JsonElement arc = o.get("arc");
                    if (arc != null && arc.isJsonArray() && arc.getAsJsonArray().size() >= 2) {
                        try {
                            p.arcFrom = arc.getAsJsonArray().get(0).getAsDouble();
                            p.arcTo = arc.getAsJsonArray().get(1).getAsDouble();
                        } catch (RuntimeException ex) {
                            p.arcFrom = 0;
                            p.arcTo = 360;
                        }
                    }
                    double span = Math.max(5, Math.min(360, Math.abs(p.arcTo - p.arcFrom)));
                    p.arcTo = p.arcFrom + span;
                    int whole = (int) Math.max(12, Math.min(56, Math.round(10 + 7 * p.r * s.radius)));
                    double share = span / 360;
                    p.segs = p.dashes > 0 ? p.dashes * (int) Math.max(1, Math.round(whole * share / p.dashes))
                            : (int) Math.max(3, Math.round(whole * share));
                }
                case "polygon", "star" -> {
                    boolean star = p.shape.equals("star");
                    p.count = (int) num(o, "sides", star ? 5 : 6, 3, 24);
                    p.skip = (int) num(o, "skip", star ? 2 : 1, 1, Math.max(1, (p.count - 1) / 2));
                    p.segs = p.count;
                }
                case "spokes" -> {
                    p.count = (int) num(o, "count", 8, 2, 48);
                    p.segs = p.count;
                }
                case "curve" -> {
                    p.segs = (int) num(o, "steps", 48, 8, 96);
                    p.turns = num(o, "turns", 1, 0.1, 12);
                    p.rho = formula(o, "rho", null, strict, n);
                    p.x = formula(o, "x", null, strict, n);
                    p.y = formula(o, "y", null, strict, n);
                    if (p.rho == null && (p.x == null || p.y == null)) {
                        if (strict) {
                            throw new FxScenes.Invalid("Layer " + n + ": a curve needs \"rho\" (a formula of u, the distance from "
                                    + "the middle as it goes round), or both \"x\" and \"y\". You may use: " + FxExpr.NAMES + ".");
                        }
                        continue;
                    }
                }
                case "runes", "marks", "motes" -> {
                    boolean runes = p.shape.equals("runes"), motes = p.shape.equals("motes");
                    p.count = (int) num(o, "count", runes ? 16 : motes ? 8 : 6, 1, runes ? 48 : 32);
                    p.size = num(o, "size", runes ? 0.35 : 0.5, 0.1, 3) * k;
                    p.font = word(o, "font", runes ? "runes" : "plain");
                    if (!FONTS.contains(p.font)) {
                        if (strict) throw new FxScenes.Invalid("Layer " + n + ": unknown font '" + p.font + "'. Fonts: " + FONTS + ".");
                        p.font = "plain";
                    }
                    JsonElement t = o.get("text");
                    String text = t != null && t.isJsonPrimitive() ? t.getAsString().replaceAll("[\\u00a7\\p{Cntrl}\\s]+", "") : "";
                    if (text.length() > 48) text = text.substring(0, 48);
                    p.text = text.isEmpty() ? (runes ? "" : motes ? "+" : "✦") : text;
                    p.rise = num(o, "rise", 0.6, 0.05, 4) * k;
                    p.height = num(o, "height", 2.5, 0.3, 12) * k;
                    p.segs = 0;
                }
                default -> {
                }
            }
            s.parts.add(p);
        }
        return s;
    }

    private static FxExpr formula(JsonObject o, String key, FxExpr fallback, boolean strict, int n) throws FxScenes.Invalid {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return fallback;
        try {
            return e.getAsJsonPrimitive().isNumber() ? FxExpr.of(e.getAsDouble()) : FxExpr.compile(e.getAsString());
        } catch (FxExpr.Invalid | RuntimeException ex) {
            if (strict) throw new FxScenes.Invalid("Layer " + n + ": in \"" + key + "\", " + ex.getMessage() + ".");
            return fallback;
        }
    }

    private static int[] colors(JsonElement e, int[] fallback, boolean strict, int n) throws FxScenes.Invalid {
        if (e == null) return fallback;
        try {
            if (e.isJsonArray() && e.getAsJsonArray().size() >= 2) {
                return new int[] { hex(e.getAsJsonArray().get(0).getAsString()), hex(e.getAsJsonArray().get(1).getAsString()) };
            }
            if (e.isJsonPrimitive()) {
                int c = hex(e.getAsString());
                return new int[] { c, c };
            }
        } catch (RuntimeException ex) {
            // A style token is filled in before the seal is shown; while checking, it stands for itself.
            if (strict && tokens(e)) return fallback;
        }
        if (strict) {
            throw new FxScenes.Invalid("Layer " + n + ": a seal's colors are \"#RRGGBB\" (or [first, last]), or a style token: "
                    + FxStyle.COLOR_TOKENS + ".");
        }
        return fallback;
    }

    private static boolean tokens(JsonElement e) {
        if (e.isJsonArray()) {
            for (JsonElement c : e.getAsJsonArray()) {
                if (!c.isJsonPrimitive() || !(FxStyle.isColorToken(c.getAsString()) || c.getAsString().matches("#[0-9a-fA-F]{6}"))) {
                    return false;
                }
            }
            return true;
        }
        return e.isJsonPrimitive() && FxStyle.isColorToken(e.getAsString());
    }

    private static int hex(String s) {
        if (!s.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException(s);
        return Integer.parseInt(s.substring(1), 16);
    }

    private static double num(JsonObject o, String key, double fallback, double lo, double hi) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return fallback;
        double v = e.getAsDouble();
        return Double.isNaN(v) ? fallback : Math.max(lo, Math.min(hi, v));
    }

    private static String word(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString().trim().toLowerCase(Locale.ROOT) : fallback;
    }

    // ---- Geometry -----------------------------------------------------------------------------------------

    /**
     * Every piece at this age (ticks since the layer began; may run past the end, where everything has faded).
     * Coordinates are blocks in the seal's plane: x to its right, y up it.
     */
    public Frame frame(double age) {
        List<Line> lines = new ArrayList<>();
        List<Glyph> glyphs = new ArrayList<>();
        List<Mote> motes = new ArrayList<>();
        double[] vars = new double[FxExpr.VARS];
        FxExpr.nobody(vars);
        vars[FxExpr.T] = age;
        vars[FxExpr.P] = Math.max(0, Math.min(1, age / Math.max(1, duration)));
        double whole = radius * scale.eval(vars, 0.05, 4, 1);
        double out = endless ? 1 : fade <= 0 ? (age < duration ? 1 : 0) : Math.max(0, Math.min(1, (duration - age) / fade));
        double turned = Math.toRadians(spin * age / 20.0);
        for (int pi = 0; pi < parts.size(); pi++) {
            Part p = parts.get(pi);
            double since = age - p.delay;
            double in = since < 0 ? 0 : appear <= 0 ? 1 : FxExpr.ease(since / appear);
            double k = whole * in;
            // Each part unwinds a quarter turn as it draws itself, alternate parts the other way.
            double twist = (1 - in) * (Math.PI / 2) * ((pi & 1) == 0 ? 1 : -1);
            double own = Math.toRadians(p.spin * age / 20.0) + twist;
            double wide = p.width * Math.min(1, in * 2) * out;
            int alpha = (int) Math.round(255 * p.alpha);
            if (p.shape.equals("motes")) {
                for (int i = 0; i < p.count; i++) {
                    double a = FxExpr.hash(i, pi, 1, 7) * Math.PI * 2, rr = Math.sqrt(FxExpr.hash(i, pi, 2, 7)) * p.r * whole;
                    double f = fract(Math.max(0, since) * p.rise / 20.0 / p.height + FxExpr.hash(i, pi, 3, 7));
                    double shown = since < 0 ? 0 : Math.sin(Math.PI * f) * out;
                    motes.add(new Mote(Math.cos(a) * rr, Math.sin(a) * rr, f * p.height, shown < 0.12 ? 0 : p.size,
                            symbol(p, i, pi), p.font, argb(mix(p.c0, p.c1, f), (int) (alpha * Math.max(0.12, shown)))));
                }
                continue;
            }
            for (int j = 0; j < p.copies; j++) {
                double around = p.copies > 1 ? Math.PI * 2 * j / p.copies : 0;
                double ox = p.orbit * Math.cos(around + Math.PI / 2), oy = p.orbit * Math.sin(around + Math.PI / 2);
                double rot = own + (p.copies > 1 ? around : 0);
                double cosR = Math.cos(rot), sinR = Math.sin(rot), cosT = Math.cos(turned), sinT = Math.sin(turned);
                if (!p.lines()) {
                    for (int i = 0; i < p.count; i++) {
                        double th = Math.PI / 2 - Math.PI * 2 * i / p.count;
                        double lx = p.r * Math.cos(th), ly = p.r * Math.sin(th);
                        double x = ox + lx * cosR - ly * sinR, y = oy + lx * sinR + ly * cosR;
                        double angle = (p.r < 1e-6 ? 0 : th - Math.PI / 2) + rot + turned;
                        double size = p.size * Math.min(1, in * 2) * (out > 0 ? 1 : 0);
                        glyphs.add(new Glyph(k * (x * cosT - y * sinT), k * (x * sinT + y * cosT), angle, size, symbol(p, i, pi), p.font,
                                argb(mix(p.c0, p.c1, p.count < 2 ? 0 : i / (double) (p.count - 1)), (int) (alpha * Math.max(0.12, out)))));
                    }
                    continue;
                }
                double[] pts = points(p, vars, j);
                int n = pts.length / 4;
                for (int i = 0; i < n; i++) {
                    double ax = pts[i * 4], ay = pts[i * 4 + 1], bx = pts[i * 4 + 2], by = pts[i * 4 + 3];
                    double x1 = ox + ax * cosR - ay * sinR, y1 = oy + ax * sinR + ay * cosR;
                    double x2 = ox + bx * cosR - by * sinR, y2 = oy + bx * sinR + by * cosR;
                    double wx1 = k * (x1 * cosT - y1 * sinT), wy1 = k * (x1 * sinT + y1 * cosT);
                    double wx2 = k * (x2 * cosT - y2 * sinT), wy2 = k * (x2 * sinT + y2 * cosT);
                    int rgb = mix(p.c0, p.c1, n < 2 ? 0 : i / (double) (n - 1));
                    double lift = pi * 0.004;
                    if (p.glow) lines.add(new Line(wx1, wy1, wx2, wy2, wide * 3.4, argb(rgb, (int) (alpha * 0.2)), lift - 0.002, true));
                    lines.add(new Line(wx1, wy1, wx2, wy2, wide, argb(rgb, alpha), lift, false));
                }
            }
        }
        return new Frame(lines, glyphs, motes);
    }

    /** A line part's strokes in its own unit space: ax, ay, bx, by for each, always {@code segs} of them. */
    private static double[] points(Part p, double[] vars, int copy) {
        double[] out = new double[p.segs * 4];
        switch (p.shape) {
            case "ring" -> {
                double from = Math.toRadians(p.arcFrom), span = Math.toRadians(p.arcTo - p.arcFrom);
                if (p.dashes > 0) {
                    int per = p.segs / p.dashes;
                    double slot = span / p.dashes, drawn = slot * (1 - p.gap);
                    for (int d = 0; d < p.dashes; d++) {
                        for (int i = 0; i < per; i++) {
                            arc(out, d * per + i, p.r, from + d * slot + drawn * i / per, from + d * slot + drawn * (i + 1) / per);
                        }
                    }
                } else {
                    for (int i = 0; i < p.segs; i++) arc(out, i, p.r, from + span * i / p.segs, from + span * (i + 1) / p.segs);
                }
            }
            case "polygon", "star" -> {
                for (int i = 0; i < p.count; i++) {
                    double a = Math.PI / 2 + Math.PI * 2 * i / p.count, b = Math.PI / 2 + Math.PI * 2 * (i + p.skip) / p.count;
                    put(out, i, p.r * Math.cos(a), p.r * Math.sin(a), p.r * Math.cos(b), p.r * Math.sin(b));
                }
            }
            case "spokes" -> {
                for (int i = 0; i < p.count; i++) {
                    double a = Math.PI / 2 + Math.PI * 2 * i / p.count;
                    put(out, i, p.rIn * Math.cos(a), p.rIn * Math.sin(a), p.r * Math.cos(a), p.r * Math.sin(a));
                }
            }
            case "curve" -> {
                vars[FxExpr.I] = copy;
                vars[FxExpr.N] = p.copies;
                double px = 0, py = 0;
                for (int i = 0; i <= p.segs; i++) {
                    double u = i / (double) p.segs;
                    vars[FxExpr.U] = u;
                    double x, y;
                    if (p.rho != null) {
                        double rr = p.rho.eval(vars, -1.5, 1.5, 0), a = Math.PI / 2 + u * Math.PI * 2 * p.turns;
                        x = rr * Math.cos(a);
                        y = rr * Math.sin(a);
                    } else {
                        x = p.x.eval(vars, -1.5, 1.5, 0);
                        y = p.y.eval(vars, -1.5, 1.5, 0);
                    }
                    if (i > 0) put(out, i - 1, px, py, x, y);
                    px = x;
                    py = y;
                }
            }
            default -> {
            }
        }
        return out;
    }

    private static void arc(double[] out, int i, double r, double a, double b) {
        put(out, i, r * Math.cos(a), r * Math.sin(a), r * Math.cos(b), r * Math.sin(b));
    }

    private static void put(double[] out, int i, double ax, double ay, double bx, double by) {
        out[i * 4] = ax;
        out[i * 4 + 1] = ay;
        out[i * 4 + 2] = bx;
        out[i * 4 + 3] = by;
    }

    private static String symbol(Part p, int i, int part) {
        if (p.text.isEmpty()) return String.valueOf((char) ('A' + (int) (FxExpr.hash(i, part, 9, 11) * 26) % 26));
        int[] cps = p.text.codePoints().toArray();
        // Marks and motes show their whole symbol; runes spell their text round the circle.
        return p.shape.equals("runes") || cps.length > 3 ? new String(Character.toChars(cps[i % cps.length])) : p.text;
    }

    private static double fract(double v) {
        return v - Math.floor(v);
    }

    static int argb(int rgb, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | (rgb & 0xFFFFFF);
    }

    static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }
}
