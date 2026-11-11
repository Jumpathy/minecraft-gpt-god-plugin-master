package net.bigyous.gptgod.client.fx;

import java.util.Random;

/**
 * Real 3D geometry for scenes, as pure maths: each kind turns a spec and an age into coloured quads
 * (drawn additively, so they are light). Positions are relative to the scene's place, in blocks.
 */
public final class Meshes {
    private Meshes() {
    }

    public enum Kind {
        BEAM, LIGHTNING, RING, DOME, RIFT, TENDRILS, SPIKES, HALO;

        public static Kind of(String id) {
            for (Kind k : values()) {
                if (k.name().equalsIgnoreCase(id)) return k;
            }
            return null;
        }
    }

    /**
     * What to build. Sizes in blocks (already scaled); colours RGB; alpha 0..1; duration in ticks; yaw turns the
     * whole mesh round the vertical (degrees, Minecraft's sense: a rift at yaw 0 faces south, toward +z).
     */
    public record Spec(Kind kind, float radius, float height, float width, double[] to, int count, int color0, int color1,
            float alpha, long seed, float spin, int duration, float yaw) {
    }

    /** Quads as 7 floats a vertex (x, y, z, r, g, b, a), 4 vertices a quad. */
    public static final class Quads {
        public float[] data = new float[28 * 64];
        public int quads;

        void vertex(int at, double x, double y, double z, int rgb, float a) {
            data[at] = (float) x;
            data[at + 1] = (float) y;
            data[at + 2] = (float) z;
            data[at + 3] = ((rgb >> 16) & 255) / 255f;
            data[at + 4] = ((rgb >> 8) & 255) / 255f;
            data[at + 5] = (rgb & 255) / 255f;
            data[at + 6] = Math.max(0, Math.min(1, a));
        }

        /** Adds a quad a-b-c-d with a colour and alpha per vertex. */
        void quad(double[] a, double[] b, double[] c, double[] d, int ca, float aa, int cb, float ab, int cc, float ac,
                int cd, float ad) {
            if (quads >= MAX_QUADS) return;
            if ((quads + 1) * 28 > data.length) data = java.util.Arrays.copyOf(data, data.length * 2);
            int at = quads * 28;
            vertex(at, a[0], a[1], a[2], ca, aa);
            vertex(at + 7, b[0], b[1], b[2], cb, ab);
            vertex(at + 14, c[0], c[1], c[2], cc, ac);
            vertex(at + 21, d[0], d[1], d[2], cd, ad);
            quads++;
        }
    }

    public static final int MAX_QUADS = 4000;
    private static final double TAU = Math.PI * 2;

    /** Rises over the first part of its life, holds, fades over the last part. */
    static float envelope(float t, float in, float out) {
        if (t <= 0 || t >= 1) return 0;
        return Math.min(1, Math.min(t / in, (1 - t) / out));
    }

    public static Quads build(Spec s, float age) {
        Quads q = new Quads();
        float t = age / Math.max(1, s.duration());
        if (t < 0 || t > 1) return q;
        switch (s.kind()) {
            case BEAM -> beam(q, s, age, t);
            case LIGHTNING -> lightning(q, s, age, t);
            case RING -> ring(q, s, t);
            case DOME -> dome(q, s, age, t);
            case RIFT -> rift(q, s, age, t);
            case TENDRILS -> tendrils(q, s, age, t);
            case SPIKES -> spikes(q, s, t);
            case HALO -> halo(q, s, age, t);
        }
        if (s.yaw() != 0) turn(q, Math.toRadians(s.yaw()));
        return q;
    }

    /** Turns every vertex round the vertical: a point across (+x) goes to (cos, sin), the way the yaw faces. */
    static void turn(Quads q, double yaw) {
        float c = (float) Math.cos(yaw), sn = (float) Math.sin(yaw);
        for (int v = 0; v < q.quads * 4; v++) {
            int at = v * 7;
            float x = q.data[at], z = q.data[at + 2];
            q.data[at] = x * c - z * sn;
            q.data[at + 2] = x * sn + z * c;
        }
    }

    private static double[] end(Spec s) {
        return s.to() != null ? s.to() : new double[] { 0, s.height(), 0 };
    }

    /** A strip from a to b of half-width w, as crossed planes so it reads from every side. */
    private static void strip(Quads q, double[] a, double[] b, double wa, double wb, int ca, float aa, int cb, float ab, int planes) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-5) return;
        dx /= len;
        dy /= len;
        dz /= len;
        // Any vector not parallel to the axis gives us a perpendicular pair.
        double ux = Math.abs(dy) > 0.9 ? 1 : 0, uy = Math.abs(dy) > 0.9 ? 0 : 1, uz = 0;
        double px = dy * uz - dz * uy, py = dz * ux - dx * uz, pz = dx * uy - dy * ux;
        double pl = Math.sqrt(px * px + py * py + pz * pz);
        px /= pl;
        py /= pl;
        pz /= pl;
        double qx = dy * pz - dz * py, qy = dz * px - dx * pz, qz = dx * py - dy * px;
        for (int i = 0; i < planes; i++) {
            double ang = Math.PI * i / planes;
            double cx = Math.cos(ang) * px + Math.sin(ang) * qx, cy = Math.cos(ang) * py + Math.sin(ang) * qy,
                    cz = Math.cos(ang) * pz + Math.sin(ang) * qz;
            // Each plane is two halves, bright along the axis and fading to nothing at the edges.
            double[] a0 = { a[0], a[1], a[2] }, b0 = { b[0], b[1], b[2] };
            double[] a1 = { a[0] + cx * wa, a[1] + cy * wa, a[2] + cz * wa }, b1 = { b[0] + cx * wb, b[1] + cy * wb, b[2] + cz * wb };
            double[] a2 = { a[0] - cx * wa, a[1] - cy * wa, a[2] - cz * wa }, b2 = { b[0] - cx * wb, b[1] - cy * wb, b[2] - cz * wb };
            q.quad(a0, b0, b1, a1, ca, aa, cb, ab, cb, 0, ca, 0);
            q.quad(a0, b0, b2, a2, ca, aa, cb, ab, cb, 0, ca, 0);
        }
    }

    private static void beam(Quads q, Spec s, float age, float t) {
        float e = envelope(t, 0.12f, 0.25f);
        float flicker = 0.9f + 0.1f * (float) Math.sin(age * 1.7 + s.seed());
        double w = s.width() * (0.4 + 0.6 * e) * flicker;
        double[] a = { 0, 0, 0 }, b = end(s);
        float al = s.alpha() * e;
        // A white-hot core inside a coloured body inside a wide soft halo.
        strip(q, a, b, w * 0.35, w * 0.3, 0xFFFFFF, al, 0xFFFFFF, al * 0.9f, 3);
        strip(q, a, b, w, w * 0.85, s.color0(), al * 0.75f, s.color0(), al * 0.6f, 3);
        strip(q, a, b, w * 2.6, w * 2.2, s.color1(), al * 0.3f, s.color1(), al * 0.18f, 3);
    }

    private static void lightning(Quads q, Spec s, float age, float t) {
        // A new jagged path every two ticks: it flickers like the real thing.
        Random rnd = new Random(s.seed() * 31 + (long) (age / 2));
        float e = envelope(t, 0.05f, 0.4f) * (0.6f + 0.4f * rnd.nextFloat());
        double[] top = end(s);
        int bolts = Math.max(1, Math.min(6, s.count()));
        for (int b = 0; b < bolts; b++) {
            double spread = s.radius();
            double gx = (rnd.nextDouble() * 2 - 1) * spread, gz = (rnd.nextDouble() * 2 - 1) * spread;
            bolt(q, s, rnd, top, new double[] { gx, 0, gz }, s.width(), e * s.alpha(), 2);
        }
    }

    private static void bolt(Quads q, Spec s, Random rnd, double[] from, double[] to, double w, float al, int forks) {
        int segs = 9;
        double len = Math.sqrt(sq(to[0] - from[0]) + sq(to[1] - from[1]) + sq(to[2] - from[2]));
        double jag = len * 0.07;
        double[] prev = from;
        for (int i = 1; i <= segs; i++) {
            double u = i / (double) segs;
            double[] p = { from[0] + (to[0] - from[0]) * u, from[1] + (to[1] - from[1]) * u, from[2] + (to[2] - from[2]) * u };
            if (i < segs) {
                p[0] += (rnd.nextDouble() * 2 - 1) * jag;
                p[1] += (rnd.nextDouble() * 2 - 1) * jag * 0.4;
                p[2] += (rnd.nextDouble() * 2 - 1) * jag;
            }
            strip(q, prev, p, w * 0.3, w * 0.3, 0xFFFFFF, al, 0xFFFFFF, al, 2);
            strip(q, prev, p, w * 1.6, w * 1.6, s.color0(), al * 0.5f, s.color1(), al * 0.5f, 2);
            if (forks > 0 && i > 2 && i < segs - 1 && rnd.nextFloat() < 0.3f) {
                double fl = len * (0.15 + 0.2 * rnd.nextDouble());
                double[] f = { p[0] + (rnd.nextDouble() * 2 - 1) * fl, p[1] - fl * (0.4 + 0.6 * rnd.nextDouble()),
                        p[2] + (rnd.nextDouble() * 2 - 1) * fl };
                bolt(q, s, rnd, p, f, w * 0.6, al * 0.7f, forks - 1);
            }
            prev = p;
        }
    }

    private static double sq(double v) {
        return v * v;
    }

    /** A ring of light on the ground, growing from the centre to its radius and thinning as it goes. */
    private static void ring(Quads q, Spec s, float t) {
        double grown = 1 - (1 - t) * (1 - t);
        double r = Math.max(0.05, s.radius() * grown);
        double w = Math.max(0.1, s.width() * (1 - 0.5 * t));
        float al = s.alpha() * (1 - t) * Math.min(1, t / 0.05f);
        int n = 64;
        for (int i = 0; i < n; i++) {
            double a0 = TAU * i / n, a1 = TAU * (i + 1) / n;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            double in = Math.max(0, r - w * 2.5), out = r + w * 0.6;
            // A long soft tail behind the bright front, a short falloff ahead of it.
            q.quad(p(c0 * in, 0.06, s0 * in), p(c1 * in, 0.06, s1 * in), p(c1 * r, 0.06, s1 * r), p(c0 * r, 0.06, s0 * r),
                    s.color1(), 0, s.color1(), 0, s.color0(), al, s.color0(), al);
            q.quad(p(c0 * r, 0.06, s0 * r), p(c1 * r, 0.06, s1 * r), p(c1 * out, 0.06, s1 * out), p(c0 * out, 0.06, s0 * out),
                    s.color0(), al, s.color0(), al, s.color1(), 0, s.color1(), 0);
            // A low wall of light standing on the front.
            double h = w * 1.2 * (1 - t);
            q.quad(p(c0 * r, 0.06, s0 * r), p(c1 * r, 0.06, s1 * r), p(c1 * r, h, s1 * r), p(c0 * r, h, s0 * r), s.color0(), al * 0.7f,
                    s.color0(), al * 0.7f, s.color1(), 0, s.color1(), 0);
        }
    }

    private static double[] p(double x, double y, double z) {
        return new double[] { x, y, z };
    }

    /** A shell of light: brightest at its rim near the ground, with a band of light sweeping over it. */
    private static void dome(Quads q, Spec s, float age, float t) {
        float e = envelope(t, 0.15f, 0.2f);
        double r = s.radius() * (0.3 + 0.7 * Math.min(1, t / 0.15));
        int lat = 10, lon = 32;
        double sweep = (age * 0.03 + s.seed() * 0.1) % 1.0;
        for (int i = 0; i < lat; i++) {
            double v0 = i / (double) lat, v1 = (i + 1) / (double) lat;
            double th0 = v0 * Math.PI / 2, th1 = v1 * Math.PI / 2;
            float a0 = shell(v0, sweep) * e * s.alpha(), a1 = shell(v1, sweep) * e * s.alpha();
            int c0 = mix(s.color1(), s.color0(), v0), c1 = mix(s.color1(), s.color0(), v1);
            for (int j = 0; j < lon; j++) {
                double p0 = TAU * j / lon + age * s.spin() * 0.01, p1 = TAU * (j + 1) / lon + age * s.spin() * 0.01;
                q.quad(sph(r, th0, p0), sph(r, th0, p1), sph(r, th1, p1), sph(r, th1, p0), c0, a0, c0, a0, c1, a1, c1, a1);
            }
        }
    }

    private static float shell(double v, double sweep) {
        double rim = 0.12 + 0.55 * Math.pow(1 - v, 3);
        double band = Math.exp(-sq((v - sweep) / 0.08)) * 0.5;
        return (float) ((rim + band) * 0.4);
    }

    private static double[] sph(double r, double theta, double phi) {
        return new double[] { Math.cos(theta) * Math.cos(phi) * r, Math.sin(theta) * r, Math.cos(theta) * Math.sin(phi) * r };
    }

    /** A tear in the air: two jagged edges of light that part, with a glow between them. */
    private static void rift(Quads q, Spec s, float age, float t) {
        float e = envelope(t, 0.2f, 0.2f);
        Random rnd = new Random(s.seed());
        int segs = 14;
        double h = s.height(), open = s.width() * e;
        double yaw = s.spin() * age * 0.01;
        double ax = Math.cos(yaw), az = Math.sin(yaw);
        double[] lPrev = null, rPrev = null, mPrev = null;
        for (int i = 0; i <= segs; i++) {
            double v = i / (double) segs;
            double profile = Math.sin(v * Math.PI);
            double wob = (rnd.nextDouble() * 2 - 1) * 0.25 + 0.12 * Math.sin(age * 0.2 + i);
            double half = open * profile * (0.7 + 0.5 * rnd.nextDouble());
            double mid = wob * open;
            double y = v * h;
            double[] l = { ax * (mid - half), y, az * (mid - half) }, r = { ax * (mid + half), y, az * (mid + half) },
                    m = { ax * mid, y, az * mid };
            if (lPrev != null) {
                float al = s.alpha() * e;
                q.quad(lPrev, l, m, mPrev, s.color0(), al, s.color0(), al, s.color1(), al * 0.25f, s.color1(), al * 0.25f);
                q.quad(rPrev, r, m, mPrev, s.color0(), al, s.color0(), al, s.color1(), al * 0.25f, s.color1(), al * 0.25f);
                strip(q, lPrev, l, 0.18 + open * 0.08, 0.18 + open * 0.08, 0xFFFFFF, al, 0xFFFFFF, al, 2);
                strip(q, rPrev, r, 0.18 + open * 0.08, 0.18 + open * 0.08, 0xFFFFFF, al, 0xFFFFFF, al, 2);
            }
            lPrev = l;
            rPrev = r;
            mPrev = m;
        }
    }

    /** Arms of light that rise from a circle and sway. */
    private static void tendrils(Quads q, Spec s, float age, float t) {
        float e = envelope(t, 0.2f, 0.25f);
        int n = Math.max(1, Math.min(24, s.count()));
        Random rnd = new Random(s.seed());
        int segs = 12;
        double grown = Math.min(1, t / 0.3);
        for (int k = 0; k < n; k++) {
            double base = TAU * k / n + rnd.nextDouble() * 0.5;
            double phase = rnd.nextDouble() * TAU, lean = 0.3 + 0.6 * rnd.nextDouble();
            double h = s.height() * (0.6 + 0.4 * rnd.nextDouble()) * grown;
            double[] prev = null;
            for (int i = 0; i <= segs; i++) {
                double v = i / (double) segs;
                double sway = Math.sin(age * 0.12 + phase + v * 3) * lean * v * s.radius() * 0.5;
                double rr = s.radius() * (1 - 0.35 * v);
                double ang = base + v * 0.8 + age * s.spin() * 0.01;
                double[] pt = { Math.cos(ang) * rr + Math.cos(base + 1.57) * sway, v * h, Math.sin(ang) * rr + Math.sin(base + 1.57) * sway };
                if (prev != null) {
                    double w0 = s.width() * (1 - (v - 1.0 / segs)) + 0.02, w1 = s.width() * (1 - v) + 0.02;
                    float al = s.alpha() * e;
                    strip(q, prev, pt, w0 * 0.4, w1 * 0.4, 0xFFFFFF, al * 0.8f, 0xFFFFFF, al * 0.8f, 2);
                    strip(q, prev, pt, w0 * 1.5, w1 * 1.5, mix(s.color0(), s.color1(), v), al * 0.5f, mix(s.color0(), s.color1(), v), al * 0.5f, 2);
                }
                prev = pt;
            }
        }
    }

    /** Crystals of light that burst out of the ground, leaning outward. */
    private static void spikes(Quads q, Spec s, float t) {
        int n = Math.max(1, Math.min(48, s.count()));
        Random rnd = new Random(s.seed());
        float fade = t > 0.7f ? 1 - (t - 0.7f) / 0.3f : 1;
        for (int k = 0; k < n; k++) {
            double ang = rnd.nextDouble() * TAU, d = s.radius() * Math.sqrt(rnd.nextDouble());
            double delay = 0.25 * d / Math.max(0.1, s.radius());
            double grown = Math.max(0, Math.min(1, (t - delay) / 0.08));
            grown = 1 - (1 - grown) * (1 - grown);
            double h = s.height() * (0.4 + 0.6 * rnd.nextDouble()) * grown;
            double w = s.width() * (0.6 + 0.6 * rnd.nextDouble());
            double lean = 0.15 + 0.35 * d / Math.max(0.1, s.radius());
            if (grown <= 0) continue;
            double bx = Math.cos(ang) * d, bz = Math.sin(ang) * d;
            double[] tip = { bx + Math.cos(ang) * h * lean, h, bz + Math.sin(ang) * h * lean };
            double turn = rnd.nextDouble() * TAU;
            float al = s.alpha() * fade;
            for (int f = 0; f < 3; f++) {
                double a0 = turn + TAU * f / 3, a1 = turn + TAU * (f + 1) / 3;
                double[] v0 = { bx + Math.cos(a0) * w, 0, bz + Math.sin(a0) * w }, v1 = { bx + Math.cos(a1) * w, 0, bz + Math.sin(a1) * w };
                float shade = 0.5f + 0.25f * f;
                q.quad(v0, v1, tip, tip, s.color1(), al * shade * 0.6f, s.color1(), al * shade * 0.6f, s.color0(), al, s.color0(), al);
            }
        }
    }

    /** A crown of light hovering at a height, with rays fanning from it, slowly turning. */
    private static void halo(Quads q, Spec s, float age, float t) {
        float e = envelope(t, 0.15f, 0.2f);
        double y = s.height(), r = s.radius(), w = s.width();
        float al = s.alpha() * e;
        int n = 48;
        double turn = age * s.spin() * 0.01;
        for (int i = 0; i < n; i++) {
            double a0 = turn + TAU * i / n, a1 = turn + TAU * (i + 1) / n;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            q.quad(p(c0 * (r - w), y, s0 * (r - w)), p(c1 * (r - w), y, s1 * (r - w)), p(c1 * r, y, s1 * r), p(c0 * r, y, s0 * r), s.color1(), 0,
                    s.color1(), 0, 0xFFFFFF, al, 0xFFFFFF, al);
            q.quad(p(c0 * r, y, s0 * r), p(c1 * r, y, s1 * r), p(c1 * (r + w), y, s1 * (r + w)), p(c0 * (r + w), y, s0 * (r + w)), 0xFFFFFF, al,
                    0xFFFFFF, al, s.color0(), 0, s.color0(), 0);
        }
        int rays = Math.max(0, Math.min(32, s.count()));
        for (int k = 0; k < rays; k++) {
            double a = turn * 0.5 + TAU * k / rays;
            double len = r * (0.8 + 0.5 * Math.sin(age * 0.1 + k * 1.7));
            double[] from = { Math.cos(a) * r, y, Math.sin(a) * r }, to = { Math.cos(a) * (r + len), y + len * 0.25, Math.sin(a) * (r + len) };
            strip(q, from, to, w * 0.8, 0.01, s.color0(), al * 0.7f, s.color1(), 0, 2);
        }
    }

    static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }
}
