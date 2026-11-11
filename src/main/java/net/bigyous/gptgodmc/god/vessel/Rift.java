package net.bigyous.gptgodmc.god.vessel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The tear in the air a god steps out of when it takes form. Pure: its timeline, its size for a body,
 * its colours, and the shape of its jagged edges. {@link RiftShow} plays it in the world, {@link RiftTextures}
 * paints it for the resource pack, {@link RiftScenes} writes it for the client mod.
 *
 * <p>Sizes are in blocks; a rift's "base" is the point on the ground under its middle. It stands upright,
 * facing the way the god will walk out of it, and reaches a little below the ground so it is already open
 * at the god's feet.
 */
public final class Rift {
    private Rift() {
    }

    // ---- The timeline, in ticks --------------------------------------------------------------------

    /**
     * When things happen, in ticks from the first seam: it has cracked to full height, torn fully open,
     * reached its climax (the body forms in it, or is swallowed by it), starts to close, is shut, and the
     * last sparks have faded.
     */
    public record Timeline(int crack, int open, int climax, int seal, int shut, int length) {
        /**
         * How open it is at a tick: {height, width}, each a fraction of the full size. A seam grows from
         * the middle to full height, tears open (overshooting a little), breathes while it holds, then
         * closes across and finally shrinks to a point.
         */
        public double[] open(double tick) {
            if (tick < 0 || tick >= shut) return new double[] { 0, 0 };
            if (tick < crack) return new double[] { 0.12 + 0.88 * easeOut(tick / crack), SEAM };
            if (tick < open) return new double[] { 1, SEAM + (1 - SEAM) * easeOutBack((tick - crack) / (double) (open - crack)) };
            if (tick < seal) return new double[] { 1, 1 + 0.03 * Math.sin((tick - open) * 0.35) };
            double t = (tick - seal) / (double) (shut - seal);
            if (t < 0.6) return new double[] { 1, SEAM + (1 - SEAM) * (1 - easeIn(t / 0.6)) };
            return new double[] { 1 - 0.95 * easeIn((t - 0.6) / 0.4), SEAM };
        }
    }

    /** A god arriving: the body forms at the climax (where the arrival sound lands), then steps out. */
    public static final Timeline ARRIVAL = new Timeline(14, 38, 48, 82, 98, 106);

    public static final int CRACK = ARRIVAL.crack();
    public static final int OPEN = ARRIVAL.open();
    public static final int CLIMAX = ARRIVAL.climax();
    /** Ticks after the climax before the body, condensed, steps out. */
    public static final int STEP_DELAY = 12;
    public static final int SEAL = ARRIVAL.seal();
    public static final int SHUT = ARRIVAL.shut();
    public static final int LENGTH = ARRIVAL.length();

    /** Leaving: ticks after the first seam before the god turns and walks into its rift. */
    public static final int DEPART_WALK = 22;

    /**
     * A god leaving: its rift opens behind it, it walks in (walk ticks), is swallowed at the climax, and
     * the rift seals after it.
     */
    public static Timeline departure(int walk) {
        int swallow = DEPART_WALK + Math.max(0, Math.min(80, walk)) + 4;
        return new Timeline(10, 26, swallow, swallow + 12, swallow + 26, swallow + 32);
    }

    // ---- Size ----------------------------------------------------------------------------------------

    /** How tall the tear is for a body this many blocks tall. */
    public static double height(double body) {
        return body * 1.55;
    }

    /** How wide the tear is at its widest (wide enough for the body's shoulders). */
    public static double width(double body) {
        return body * 0.72;
    }

    /** Where its lower tip is, below the ground (negative). */
    public static double bottom(double body) {
        return -body * 0.22;
    }

    /** The height of its middle above the ground. */
    public static double centre(double body) {
        return bottom(body) + height(body) / 2;
    }

    /** How far behind the spot where the god will stand the rift opens (it walks this far out). */
    public static double stepBack(double body) {
        return Math.max(1.2, Math.min(4.5, body * 0.55));
    }

    // ---- Opening and closing -------------------------------------------------------------------------

    /** How open an arrival's rift is at a tick (see {@link Timeline#open}). */
    public static double[] open(double tick) {
        return ARRIVAL.open(tick);
    }

    /** How wide the seam is before it tears, and after it has closed, as a fraction of the full width. */
    static final double SEAM = 0.05;

    static double easeOut(double t) {
        double u = 1 - clamp(t);
        return 1 - u * u * u;
    }

    static double easeIn(double t) {
        double u = clamp(t);
        return u * u;
    }

    /** Eases out past 1 by a few percent, then settles. */
    static double easeOutBack(double t) {
        double u = clamp(t) - 1;
        double k = 1.4;
        return 1 + (k + 1) * u * u * u + k * u * u;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    // ---- Its shape -----------------------------------------------------------------------------------

    /** Straight runs of each edge, tip to tip: a torn zig-zag, like the client mod's rift. */
    public static final int SEGMENTS = 14;

    /**
     * The tear's outline at a height: {left edge, right edge}, as fractions of its widest half-width from
     * the centre line (left negative), for v from 0 (lower tip) to 1 (upper tip). Each edge zig-zags on its
     * own between points chosen by the seed, with finer splinters between them, so the two sides do not
     * mirror each other; the middle wanders a little and comes back to the centre at the tips.
     */
    public static double[] shape(double v, long seed) {
        if (v <= 0 || v >= 1) return new double[] { 0, 0 };
        double k = v * SEGMENTS;
        int i = (int) Math.floor(k);
        double f = k - i;
        double profile = Math.pow(Math.sin(Math.PI * v), 0.7);
        double mid = lerp(wander(i, seed), wander(i + 1, seed), f) * (0.25 + 0.75 * profile);
        double left = profile * (lerp(jag(i, seed), jag(i + 1, seed), f) + splinter(v, seed + 7));
        double right = profile * (lerp(jag(i, seed + 53), jag(i + 1, seed + 53), f) + splinter(v, seed + 61));
        return new double[] { mid - Math.min(1, left), mid + Math.min(1, right) };
    }

    private static double jag(int i, long seed) {
        return 0.68 + 0.3 * hash(i, seed);
    }

    /** Small notches between the big zig-zags: three to a run. */
    private static double splinter(double v, long seed) {
        double k = v * SEGMENTS * 3;
        int i = (int) Math.floor(k);
        return lerp(hash(i, seed), hash(i + 1, seed), k - i) * 0.07 - 0.035;
    }

    private static double wander(int i, long seed) {
        if (i <= 0 || i >= SEGMENTS) return 0;
        return (hash(i, seed + 101) - 0.5) * 0.26;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** 0..1, the same for the same inputs. */
    static double hash(long i, long seed) {
        long h = i * 0x9E3779B97F4A7C15L + seed * 0xC2B2AE3D27D4EB4FL + 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53);
    }

    /** One row of the outline in the world's terms: blocks across from the centre line, and up from the lower tip. */
    public record Row(double left, double right, double up) {
    }

    /**
     * The edges at a tick, for drawing them in particles: rows from the lower tip to the upper, in blocks
     * across (negative = its left) and up from where its lower tip would be when fully grown. A seam grows
     * from the middle, so while it grows the rows bunch round the centre.
     */
    public static List<Row> rim(double body, double tick, long seed, int rows) {
        return rim(ARRIVAL, body, tick, seed, rows);
    }

    public static List<Row> rim(Timeline when, double body, double tick, long seed, int rows) {
        double[] o = when.open(tick);
        List<Row> out = new ArrayList<>();
        if (o[0] <= 0) return out;
        double h = height(body), half = width(body) / 2 * o[1];
        double flicker = 0.04 * Math.sin(tick * 0.9);
        for (int r = 0; r < rows; r++) {
            double v = (r + 0.5) / rows;
            double[] s = shape(v, seed);
            double mid = (s[0] + s[1]) / 2 * width(body) / 2, w = (s[1] - s[0]) / 2 * half * (1 + flicker);
            double up = h / 2 + (v - 0.5) * h * o[0];
            out.add(new Row(mid - w, mid + w, up));
        }
        return out;
    }

    // ---- Colours -------------------------------------------------------------------------------------

    /**
     * The rift's colours, picked from the god's own glow so each god tears in its own light: the white-hot
     * edge, its glow, the band of colour inside the edge, and the far side (the void, its clouds and stars).
     */
    public enum Palette {
        VIOLET(0xF1DDFF, 0xB070FF, 0x7A2CE0, 0x07020F, 0x2A0E5A, 0x6A28B8, 0xF4ECFF),
        GOLD(0xFFF4C8, 0xFFC23A, 0xD8861A, 0x0E0703, 0x3E2206, 0x9A5A16, 0xFFF8E0),
        EMBER(0xFFE2B0, 0xFF6A20, 0xC8240E, 0x0C0202, 0x420804, 0x8E220A, 0xFFD8A0),
        TIDE(0xE6F8FF, 0x5AB8FF, 0x2462D8, 0x02060F, 0x0A2450, 0x1C5E9E, 0xE8F8FF),
        VERDANT(0xEEFFD8, 0x7AE86A, 0x2A8A36, 0x020803, 0x0C3212, 0x266E26, 0xF0FFE0);

        public final int rim, glow, band, deep, cloud, cloudLight, star;

        Palette(int rim, int glow, int band, int deep, int cloud, int cloudLight, int star) {
            this.rim = rim;
            this.glow = glow;
            this.band = band;
            this.deep = deep;
            this.cloud = cloud;
            this.cloudLight = cloudLight;
            this.star = star;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The palette closest to a colour (a look's outline): by hue, grey colours tear in violet. */
        public static Palette of(int rgb) {
            float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
            if (hsb[1] < 0.18f) return VIOLET;
            double hue = hsb[0] * 360;
            if (hue < 25 || hue >= 335) return EMBER;
            if (hue < 70) return GOLD;
            if (hue < 165) return VERDANT;
            if (hue < 235) return TIDE;
            return VIOLET;
        }
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }
}
