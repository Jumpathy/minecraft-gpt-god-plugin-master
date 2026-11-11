package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.util.Vector;

/**
 * Geometry for the god's particle art: rings, star polygons, magic circles, helixes, spheres and a
 * four-dimensional cube. Pure math (no world access), in blocks, y up, centered on the origin, so the
 * same points drive the particles in game and the offline previews.
 */
public final class Shapes {
    private static final double TAU = Math.PI * 2;
    /** The golden angle: stepping by it spreads points evenly round a circle or over a sphere. */
    private static final double GOLDEN = 2.39996322972865332;

    private Shapes() {
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Smooth start and stop for t in 0..1 (clamped). */
    public static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** A flat circle of n points at height y. */
    public static List<Vector> ring(double r, int n, double phase, double y) {
        List<Vector> out = new ArrayList<>(Math.max(0, n));
        for (int i = 0; i < n; i++) {
            double a = phase + i * TAU / n;
            out.add(new Vector(Math.cos(a) * r, y, Math.sin(a) * r));
        }
        return out;
    }

    /** The outline of a regular polygon lying flat, perEdge points along each side. */
    public static List<Vector> polygon(int sides, double r, double phase, double y, int perEdge) {
        return star(sides, 1, r, phase, y, perEdge);
    }

    /**
     * A star polygon lying flat: every corner joined to the one {@code skip} places round. Five points
     * with skip 2 is a pentagram, six with skip 2 a hexagram (two triangles), eight with skip 3 an
     * eight-pointed star.
     */
    public static List<Vector> star(int points, int skip, double r, double phase, double y, int perEdge) {
        List<Vector> out = new ArrayList<>(points * Math.max(1, perEdge));
        for (int i = 0; i < points; i++) {
            double a0 = phase + i * TAU / points;
            double a1 = phase + (i + skip) * TAU / points;
            double x0 = Math.cos(a0) * r, z0 = Math.sin(a0) * r;
            double x1 = Math.cos(a1) * r, z1 = Math.sin(a1) * r;
            for (int j = 0; j < perEdge; j++) {
                double t = j / (double) perEdge;
                out.add(new Vector(lerp(x0, x1, t), y, lerp(z0, z1, t)));
            }
        }
        return out;
    }

    /**
     * A summoning circle lying flat: two rings with rune marks between them, a hexagram inscribed in
     * the inner ring and a triangle turning the other way at its heart.
     *
     * @param density points per block of line; 2 is sparse, 4 is solid
     */
    public static List<Vector> magicCircle(double r, double phase, double y, double density) {
        List<Vector> out = new ArrayList<>();
        double inner = r * 0.86;
        out.addAll(ring(r, count(TAU * r, density), phase, y));
        out.addAll(ring(inner, count(TAU * inner, density), -phase, y));
        // A hexagram's side is the inner radius times the square root of three.
        out.addAll(star(6, 2, inner, phase, y, count(inner * Math.sqrt(3), density)));
        double heart = inner * 0.5;
        out.addAll(polygon(3, heart, -phase * 2, y, count(heart * Math.sqrt(3), density)));
        for (int i = 0; i < 12; i++) {
            double a = phase + (i + 0.5) * TAU / 12;
            out.add(new Vector(Math.cos(a) * r * 0.93, y, Math.sin(a) * r * 0.93));
        }
        return out;
    }

    private static int count(double length, double density) {
        return Math.max(3, (int) Math.round(length * density));
    }

    /**
     * Strands winding round the vertical axis from height y0 to y1, the radius running from r0 at the
     * bottom to r1 at the top.
     */
    public static List<Vector> helix(int strands, double r0, double r1, double y0, double y1, double turns,
            int perStrand, double phase) {
        List<Vector> out = new ArrayList<>(strands * perStrand);
        for (int s = 0; s < strands; s++) {
            for (int i = 0; i < perStrand; i++) {
                double t = perStrand == 1 ? 0 : i / (double) (perStrand - 1);
                double a = phase + s * TAU / strands + t * turns * TAU;
                double r = lerp(r0, r1, t);
                out.add(new Vector(Math.cos(a) * r, lerp(y0, y1, t), Math.sin(a) * r));
            }
        }
        return out;
    }

    /** Points spread evenly over a sphere, turned by phase round the vertical. */
    public static List<Vector> sphere(int n, double r, double phase) {
        List<Vector> out = new ArrayList<>(Math.max(0, n));
        for (int i = 0; i < n; i++) {
            double y = 1 - 2 * (i + 0.5) / n;
            double ring = Math.sqrt(Math.max(0, 1 - y * y));
            double a = i * GOLDEN + phase;
            out.add(new Vector(Math.cos(a) * ring * r, y * r, Math.sin(a) * ring * r));
        }
        return out;
    }

    /** Rays pointing out of the center in every direction, each drawn from radius r0 to r1. */
    public static List<Vector> spikes(int rays, double r0, double r1, int perRay, double phase) {
        List<Vector> out = new ArrayList<>(rays * perRay);
        for (Vector dir : sphere(rays, 1, phase)) {
            for (int j = 0; j < perRay; j++) {
                double t = perRay == 1 ? 0 : j / (double) (perRay - 1);
                out.add(dir.clone().multiply(lerp(r0, r1, t)));
            }
        }
        return out;
    }

    /** n points from a to b, both ends included. */
    public static List<Vector> line(Vector a, Vector b, int n) {
        List<Vector> out = new ArrayList<>(Math.max(0, n));
        for (int i = 0; i < n; i++) {
            double t = n == 1 ? 0 : i / (double) (n - 1);
            out.add(new Vector(lerp(a.getX(), b.getX(), t), lerp(a.getY(), b.getY(), t),
                    lerp(a.getZ(), b.getZ(), t)));
        }
        return out;
    }

    /** The same points moved up by dy (new list). */
    public static List<Vector> lift(List<Vector> points, double dy) {
        List<Vector> out = new ArrayList<>(points.size());
        for (Vector p : points) out.add(new Vector(p.getX(), p.getY() + dy, p.getZ()));
        return out;
    }

    /**
     * The same points turned about the origin (new list): first tipped round the x axis by pitch, then
     * rolled round the z axis, then swung round the vertical by yaw. Radians.
     */
    public static List<Vector> turn(List<Vector> points, double pitch, double yaw, double roll) {
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        double cr = Math.cos(roll), sr = Math.sin(roll);
        List<Vector> out = new ArrayList<>(points.size());
        for (Vector p : points) {
            double x = p.getX(), y = p.getY(), z = p.getZ();
            double y1 = y * cp - z * sp, z1 = y * sp + z * cp;
            double x2 = x * cr - y1 * sr, y2 = x * sr + y1 * cr;
            out.add(new Vector(x2 * cy + z1 * sy, y2, -x2 * sy + z1 * cy));
        }
        return out;
    }

    /**
     * The wire frame of a tesseract (a cube in four dimensions) turning through the fourth dimension,
     * seen in three. As it turns, the inner cube swells and swallows the outer one: a shape that keeps
     * turning inside out.
     *
     * @param xw,yw,zw how far it has turned in each plane that includes the fourth axis (radians)
     * @param scale    roughly the radius of the outer cube when at rest
     * @param perEdge  points along each of its 32 edges
     */
    public static List<Vector> tesseract(double xw, double yw, double zw, double scale, int perEdge) {
        double[][] corners = new double[16][];
        for (int i = 0; i < 16; i++) {
            double[] v = { (i & 1) == 0 ? -1 : 1, (i & 2) == 0 ? -1 : 1, (i & 4) == 0 ? -1 : 1,
                    (i & 8) == 0 ? -1 : 1 };
            rotate(v, 0, 3, xw);
            rotate(v, 1, 3, yw);
            rotate(v, 2, 3, zw);
            // Seen from a little way off along the fourth axis: what is nearer in w looks larger.
            double k = scale * 1.25 / (2.5 - v[3]);
            corners[i] = new double[] { v[0] * k, v[1] * k, v[2] * k };
        }
        List<Vector> out = new ArrayList<>(32 * perEdge);
        for (int i = 0; i < 16; i++) {
            for (int bit = 1; bit < 16; bit <<= 1) {
                int j = i | bit;
                if (j == i) continue; // each edge once, from the corner with that bit clear
                double[] a = corners[i], b = corners[j];
                for (int s = 0; s < perEdge; s++) {
                    double t = (s + 0.5) / perEdge;
                    out.add(new Vector(lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)));
                }
            }
        }
        return out;
    }

    private static void rotate(double[] v, int i, int j, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        double a = v[i], b = v[j];
        v[i] = a * c - b * s;
        v[j] = a * s + b * c;
    }

    /**
     * An open eye standing upright in the x-y plane, looking along +z: two lids, an iris and a slit
     * pupil. Turn it with {@link #turn} to face somewhere.
     *
     * @param width half the eye's width
     * @param open  half its height at the middle; 0 is shut
     */
    public static List<Vector> eye(double width, double open, int perLid) {
        List<Vector> out = new ArrayList<>(perLid * 2 + 24);
        for (int i = 0; i < perLid; i++) {
            double u = perLid == 1 ? 0 : -1 + 2.0 * i / (perLid - 1);
            double y = open * (1 - u * u);
            out.add(new Vector(u * width, y, 0));
            if (y > 1e-6) out.add(new Vector(u * width, -y, 0));
        }
        double iris = Math.min(open * 0.72, width * 0.5);
        if (iris > 0.05) {
            int n = Math.max(8, perLid);
            for (int i = 0; i < n; i++) {
                double a = i * TAU / n;
                out.add(new Vector(Math.cos(a) * iris, Math.sin(a) * iris, 0));
            }
            for (int i = 0; i < 5; i++) {
                out.add(new Vector(0, lerp(-iris * 0.8, iris * 0.8, i / 4.0), 0));
            }
        }
        return out;
    }
}
