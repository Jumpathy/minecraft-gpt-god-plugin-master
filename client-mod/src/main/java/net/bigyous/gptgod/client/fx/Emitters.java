package net.bigyous.gptgod.client.fx;

import java.util.Random;

import net.bigyous.gptgod.client.fx.Scene.Motion;
import net.bigyous.gptgod.client.fx.Scene.Particles;

/**
 * Where an emitter's particles are born and how they start moving. Pure. Positions and velocities are
 * relative to the scene's origin, in blocks and blocks a tick.
 */
public final class Emitters {
    private Emitters() {
    }

    private static final double TAU = Math.PI * 2;

    /** One newborn particle. swirl is its turning rate around the scene's vertical axis (radians a tick). */
    public record Spawn(double x, double y, double z, double vx, double vy, double vz, float swirl, int life) {
    }

    /** How many particles this emitter makes on tick t of its own time (0 = its first tick). */
    public static int due(Particles p, int t, Random rnd) {
        int n = t == 0 ? p.count() : 0;
        if (p.rate() > 0 && t < p.duration()) {
            int whole = (int) p.rate();
            n += whole + (rnd.nextFloat() < p.rate() - whole ? 1 : 0);
        }
        return n;
    }

    /**
     * @param i     index of this particle among the n made this tick (shapes spread evenly over i)
     * @param t     the emitter's tick
     * @param scale the scene's scale
     */
    public static Spawn spawn(Particles p, int i, int n, int t, float scale, Random rnd) {
        double r = p.radius() * scale;
        double h = p.height() * scale;
        double u = n <= 1 ? rnd.nextDouble() : (i + rnd.nextDouble() * 0.5) / n;
        double x = 0, y = 0, z = 0;
        switch (p.shape()) {
            case POINT, BURST -> {
            }
            case RING -> {
                double a = u * TAU;
                x = Math.cos(a) * r;
                z = Math.sin(a) * r;
            }
            case DISC -> {
                double a = rnd.nextDouble() * TAU, d = r * Math.sqrt(rnd.nextDouble());
                x = Math.cos(a) * d;
                z = Math.sin(a) * d;
            }
            case SPHERE, DOME -> {
                double yy = p.shape() == Scene.Shape.DOME ? rnd.nextDouble() : rnd.nextDouble() * 2 - 1;
                double a = rnd.nextDouble() * TAU, ring = Math.sqrt(1 - yy * yy);
                x = Math.cos(a) * ring * r;
                y = yy * r;
                z = Math.sin(a) * ring * r;
            }
            case COLUMN -> {
                double a = rnd.nextDouble() * TAU, d = r * Math.sqrt(rnd.nextDouble());
                x = Math.cos(a) * d;
                y = rnd.nextDouble() * h;
                z = Math.sin(a) * d;
            }
            case SPIRAL, HELIX -> {
                // A rising coil; a helix is two of them, opposite each other. Over time the coil turns.
                double a = u * TAU * p.turns() + t * 0.12 + (p.shape() == Scene.Shape.HELIX && (i & 1) == 1 ? Math.PI : 0);
                x = Math.cos(a) * r;
                y = u * h;
                z = Math.sin(a) * r;
            }
            case VORTEX -> {
                // A funnel: narrow at the bottom, wide at the top.
                double a = rnd.nextDouble() * TAU;
                double d = r * (0.15 + 0.85 * u);
                x = Math.cos(a) * d;
                y = u * h;
                z = Math.sin(a) * d;
            }
            case BEAM, LINE -> {
                double[] to = p.to() != null ? p.to() : new double[] { 0, p.height(), 0 };
                x = to[0] * scale * u;
                y = to[1] * scale * u;
                z = to[2] * scale * u;
            }
            case WINGS -> {
                // Two swept arcs behind and above the origin, feathering downward.
                double side = (i & 1) == 0 ? 1 : -1;
                double s = rnd.nextDouble();
                double span = r * (0.2 + 0.8 * s);
                x = side * span;
                y = h * (0.55 + 0.45 * Math.sin(s * Math.PI * 0.9)) - rnd.nextDouble() * h * 0.45 * s;
                z = -0.3 * scale - 0.35 * span * s;
            }
            case SIGIL -> {
                // A ground circle with a star inscribed in it.
                if (rnd.nextDouble() < 0.45) {
                    double a = u * TAU;
                    x = Math.cos(a) * r;
                    z = Math.sin(a) * r;
                } else {
                    int points = 5;
                    int k = rnd.nextInt(points);
                    double a0 = k * TAU / points, a1 = ((k + 2) % points) * TAU / points;
                    double s = rnd.nextDouble();
                    x = (Math.cos(a0) + (Math.cos(a1) - Math.cos(a0)) * s) * r;
                    z = (Math.sin(a0) + (Math.sin(a1) - Math.sin(a0)) * s) * r;
                }
                y = 0.05;
            }
            case RAIN -> {
                double a = rnd.nextDouble() * TAU, d = r * Math.sqrt(rnd.nextDouble());
                x = Math.cos(a) * d;
                y = h;
                z = Math.sin(a) * d;
            }
        }
        x += p.offset()[0] * scale;
        y += p.offset()[1] * scale;
        z += p.offset()[2] * scale;

        double speed = p.speed() * scale * (0.7 + 0.6 * rnd.nextDouble());
        double vx = 0, vy = 0, vz = 0;
        float swirl = 0;
        double ox = x - p.offset()[0] * scale, oy = y - p.offset()[1] * scale, oz = z - p.offset()[2] * scale;
        double len = Math.sqrt(ox * ox + oy * oy + oz * oz);
        Motion m = p.motion();
        switch (m) {
            case STILL -> {
            }
            case OUTWARD, INWARD -> {
                double dx, dy, dz;
                if (len < 1e-4) {
                    // From a point: any direction.
                    double yy = rnd.nextDouble() * 2 - 1, a = rnd.nextDouble() * TAU, ring = Math.sqrt(1 - yy * yy);
                    dx = Math.cos(a) * ring;
                    dy = yy;
                    dz = Math.sin(a) * ring;
                } else {
                    dx = ox / len;
                    dy = oy / len;
                    dz = oz / len;
                }
                double sign = m == Motion.OUTWARD ? 1 : -1;
                vx = dx * speed * sign;
                vy = dy * speed * sign;
                vz = dz * speed * sign;
            }
            case UP -> vy = speed;
            case FALL -> vy = -speed;
            case ORBIT -> swirl = (float) (speed / Math.max(0.3, Math.sqrt(ox * ox + oz * oz)));
            case SWIRL -> {
                swirl = (float) (speed / Math.max(0.3, Math.sqrt(ox * ox + oz * oz)));
                vy = speed * 0.5;
            }
        }
        if (p.spread() > 0) {
            double j = p.spread() * Math.max(0.02, speed > 0 ? speed : 0.05);
            vx += (rnd.nextDouble() * 2 - 1) * j;
            vy += (rnd.nextDouble() * 2 - 1) * j;
            vz += (rnd.nextDouble() * 2 - 1) * j;
        }
        int life = p.lifeMin() + (p.lifeMax() > p.lifeMin() ? rnd.nextInt(p.lifeMax() - p.lifeMin() + 1) : 0);
        return new Spawn(x, y, z, vx, vy, vz, swirl, life);
    }
}
