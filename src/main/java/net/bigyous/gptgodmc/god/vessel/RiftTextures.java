package net.bigyous.gptgodmc.god.vessel;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.bigyous.gptgodmc.god.vessel.Rift.Palette;

/**
 * Paints the rift for the resource pack, in code: one animated picture per {@link Palette}. A torn
 * zig-zag outline ({@link Rift#shape}) with a white-hot edge, a band of the god's colour inside it, and
 * through it the far side: a dark void with slow clouds, twinkling stars and motes rushing out of its
 * depth toward the viewer. Outside the edge a stepped glow and arcs of light that crackle off it frame by
 * frame. Deterministic (same pixels every run, so the pack's hash is stable).
 */
public final class RiftTextures {
    private RiftTextures() {
    }

    public static final int W = 80, H = 128;
    public static final int FRAMES = 12;
    public static final int FRAME_TICKS = 3;
    /** The share of the picture's width the tear spans at its widest; the rest is its glow. */
    public static final double TEAR = 0.72;
    /** The share of its height between the tips; above and below them is glow. */
    public static final double TALL = 0.94;
    /** The outline every rift is torn along (the particle sketch uses the same). */
    public static final long SEED = 7341;

    private static final Map<Palette, BufferedImage[]> CACHE = new ConcurrentHashMap<>();

    public static BufferedImage[] frames(Palette p) {
        return CACHE.computeIfAbsent(p, k -> {
            BufferedImage[] out = new BufferedImage[FRAMES];
            for (int f = 0; f < FRAMES; f++) out[f] = paint(k, f);
            return out;
        });
    }

    private static final double HALF_PX = W * TEAR / 2;
    private static final double TALL_PX = H * TALL;

    static BufferedImage paint(Palette pal, int frame) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        double phase = Math.PI * 2 * frame / FRAMES;
        double[][] arcs = arcs(frame);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int c = texel(pal, x, y, frame, phase, arcs);
                if ((c >>> 24) != 0) img.setRGB(x, y, c);
            }
        }
        return img;
    }

    /** Texels from the edge: negative inside the tear. */
    static double edge(double x, double y) {
        double a = (x - W / 2.0) / HALF_PX;
        double v = ((H - y) / H - (1 - TALL) / 2) / TALL;
        if (v <= 0 || v >= 1) {
            double over = (v <= 0 ? -v : v - 1) * TALL_PX;
            return Math.hypot(a * HALF_PX, over);
        }
        double[] s = Rift.shape(v, SEED);
        return Math.max(s[0] - a, a - s[1]) * HALF_PX;
    }

    private static int texel(Palette pal, int x, int y, int frame, double phase, double[][] arcs) {
        double cx = x + 0.5, cy = y + 0.5;
        // The edge shivers a little from frame to frame (sampled round a circle, so the loop is seamless).
        double shiver = 1.0 * (noise(cy * 0.35 + Math.cos(phase) * 1.3, Math.sin(phase) * 1.3 + 5) - 0.5);
        double d = edge(cx, cy) + shiver;
        if (d < 0) return 0xFF000000 | inside(pal, cx, cy, -d, frame, phase);
        // Arcs of light crackling off the edge.
        double arc = arcDistance(arcs, cx, cy);
        if (arc < 0.7) return argb(235, pal.rim);
        if (arc < 1.5) return argb(150, pal.glow);
        // A stepped glow round the tear.
        int alpha = d < 1.5 ? 200 : d < 3 ? 140 : d < 5 ? 85 : d < 8 ? 40 : d < 11 ? 16 : 0;
        if (alpha == 0) return 0;
        return argb(alpha, mix(pal.glow, pal.band, Math.min(1, d / 9)));
    }

    /** depth = texels in from the edge. */
    private static int inside(Palette pal, double cx, double cy, double depth, int frame, double phase) {
        if (depth < 1.0) return mix(0xFFFFFF, pal.rim, 0.3);
        if (depth < 2.2) return pal.rim;
        if (depth < 3.6) return pal.glow;
        int c = farSide(pal, cx, cy, frame, phase);
        // The edge's light spilling inward, in flat steps.
        double light = Math.exp(-(depth - 3.6) / 2.4);
        if (light > 0.6) return mix(c, pal.band, 0.8);
        if (light > 0.32) return mix(c, pal.band, 0.5);
        if (light > 0.14) return mix(c, pal.band, 0.22);
        return c;
    }

    /** The other side: void, churning clouds, a far light at its heart, stars and motes flying out of it. */
    private static int farSide(Palette pal, double cx, double cy, int frame, double phase) {
        double px = (cx - W / 2.0) / HALF_PX, py = (cy - H / 2.0) / (TALL_PX / 2);
        double r = Math.hypot(px, py);
        // Clouds wound round the heart, churning in place.
        double swirl = 2.2 * Math.max(0, 1 - r);
        double sx = px * Math.cos(swirl) - py * Math.sin(swirl), sy = px * Math.sin(swirl) + py * Math.cos(swirl);
        double n = fbm(sx * 3.2 + Math.cos(phase) * 0.9, sy * 3.2 + Math.sin(phase) * 0.9);
        double cloud = Math.max(0, Math.min(1, (n - 0.42) * 2.6)) * (0.5 + 0.5 * Math.max(0, 1 - r));
        int c = cloud > 0.62 ? pal.cloudLight : cloud > 0.36 ? mix(pal.cloud, pal.cloudLight, 0.45)
                : cloud > 0.14 ? pal.cloud : pal.deep;
        // A far light at the heart, as if down a long way, breathing with the loop.
        double heart = Math.hypot(cx - W / 2.0, cy - H / 2.0) - 0.5 * Math.sin(phase);
        if (heart < 1.6) c = mix(pal.rim, 0xFFFFFF, 0.5);
        else if (heart < 3.0) c = mix(pal.glow, pal.rim, 0.45);
        else if (heart < 5.2) c = mix(c, pal.glow, 0.5);
        else if (heart < 8.5) c = mix(c, pal.cloudLight, 0.35);
        // Stars that twinkle.
        int ix = (int) cx, iy = (int) cy;
        double s = Rift.hash(ix * 977L + iy, 31);
        if (s > 0.972) {
            double tw = 0.5 + 0.5 * Math.sin(phase + s * 600);
            c = mix(c, pal.star, tw > 0.66 ? 1 : tw > 0.33 ? 0.6 : 0.3);
        }
        // Motes rushing out of the depth toward whoever looks in.
        for (int k = 0; k < 20; k++) {
            double a = Rift.hash(k, 57) * Math.PI * 2;
            double p = (frame / (double) FRAMES + Rift.hash(k, 91)) % 1.0;
            double rr = 0.08 + 0.95 * p * p;
            double mx = Math.cos(a) * rr * HALF_PX + W / 2.0, my = Math.sin(a) * rr * TALL_PX / 2 + H / 2.0;
            double dx = cx - mx, dy = cy - my;
            double dist = Math.hypot(dx, dy);
            if (dist < 0.75) return mix(c, pal.star, 0.4 + 0.6 * p);
            // a short trail back toward the heart
            double tx = mx - W / 2.0, ty = my - H / 2.0, tl = Math.hypot(tx, ty);
            if (tl > 1e-6 && p > 0.3) {
                double along = -(dx * tx + dy * ty) / tl, off = Math.abs(dx * ty - dy * tx) / tl;
                if (along > 0 && along < 1 + 3 * p && off < 0.55) c = mix(c, pal.glow, 0.45 * p);
            }
        }
        return c;
    }

    // ---- Arcs ----------------------------------------------------------------------------------------

    /** Segments {x0, y0, x1, y1} of the arcs crackling off the edge in this frame. */
    private static double[][] arcs(int frame) {
        java.util.List<double[]> segs = new java.util.ArrayList<>();
        for (int k = 0; k < 4; k++) {
            long seed = frame * 131L + k * 17L + 3;
            double v = 0.15 + 0.7 * Rift.hash(k, seed);
            double side = Rift.hash(k, seed + 1) < 0.5 ? -1 : 1;
            double[] s = Rift.shape(v, SEED);
            double x = W / 2.0 + (side < 0 ? s[0] : s[1]) * HALF_PX;
            double y = H - ((1 - TALL) / 2 + v * TALL) * H;
            double len = 5 + 7 * Rift.hash(k, seed + 2);
            double dir = side * (0.6 + 0.6 * Rift.hash(k, seed + 3)), up = (Rift.hash(k, seed + 4) - 0.5) * 1.2;
            double norm = Math.hypot(dir, up);
            dir /= norm;
            up /= norm;
            int steps = 4;
            for (int i = 0; i < steps; i++) {
                double step = len / steps;
                double jx = (Rift.hash(i, seed + 9) - 0.5) * 2.2, jy = (Rift.hash(i, seed + 11) - 0.5) * 2.2;
                double nx = x + dir * step + jx * 0.5, ny = y + up * step + jy;
                segs.add(new double[] { x, y, nx, ny });
                x = nx;
                y = ny;
            }
        }
        return segs.toArray(new double[0][]);
    }

    private static double arcDistance(double[][] segs, double px, double py) {
        double best = Double.MAX_VALUE;
        for (double[] s : segs) {
            double dx = s[2] - s[0], dy = s[3] - s[1];
            double l2 = dx * dx + dy * dy;
            double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((px - s[0]) * dx + (py - s[1]) * dy) / l2));
            best = Math.min(best, Math.hypot(px - s[0] - dx * t, py - s[1] - dy * t));
        }
        return best;
    }

    // ---- Brushes -------------------------------------------------------------------------------------

    static int argb(int alpha, int rgb) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    static int mix(int a, int b, double t) {
        double f = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * f);
        int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * f);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * f);
        return (r << 16) | (g << 8) | bl;
    }

    private static double noise(double x, double y) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = Rift.hash(x0 * 7919L + y0, 5), b = Rift.hash((x0 + 1) * 7919L + y0, 5),
                c = Rift.hash(x0 * 7919L + y0 + 1, 5), d = Rift.hash((x0 + 1) * 7919L + y0 + 1, 5);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    private static double fbm(double x, double y) {
        return 0.55 * noise(x, y) + 0.3 * noise(x * 2.07 + 3.1, y * 2.07 + 1.7) + 0.15 * noise(x * 4.3 + 7, y * 4.3 + 2);
    }
}
