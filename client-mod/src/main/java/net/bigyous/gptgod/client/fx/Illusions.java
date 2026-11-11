package net.bigyous.gptgod.client.fx;

import java.util.Random;

/**
 * Fake blocks that only this client sees: pieces of the ground flung into the air, pillars rising,
 * blocks floating away. Pure physics; the world itself never changes.
 */
public final class Illusions {
    private Illusions() {
    }

    public enum Mode {
        /** Thrown outward and up, tumbling, falling back and bouncing once or twice. */
        FLING,
        /** Rises straight up out of the ground to a height, holds, sinks back. */
        RISE,
        /** Drifts upward, slowly turning, and shrinks away. */
        FLOAT;

        public static Mode of(String id) {
            for (Mode m : values()) {
                if (m.name().equalsIgnoreCase(id)) return m;
            }
            return FLING;
        }
    }

    /** One fake block. Positions are world coordinates of its centre's base. */
    public static final class Piece {
        public final Object block;
        public final Mode mode;
        public double x, y, z, ox, oy, oz;
        public double vx, vy, vz;
        public float pitch, yaw, roll, oPitch, oYaw, oRoll;
        float sp, sy, sr;
        public int age;
        public final int life;
        final double floor;
        final double height;
        final float gravity;
        int bounces;

        Piece(Object block, Mode mode, double x, double y, double z, int life, double height, float gravity) {
            this.block = block;
            this.mode = mode;
            this.x = ox = x;
            this.y = oy = y;
            this.z = oz = z;
            this.floor = y;
            this.life = life;
            this.height = height;
            this.gravity = gravity;
        }

        public boolean done() {
            return age >= life;
        }

        /** Size 0..1: pieces pop in and shrink away at the end instead of vanishing. */
        public float size(float partial) {
            float a = age + partial;
            float in = Math.min(1, a / 3f);
            float out = Math.min(1, (life - a) / 8f);
            float base = mode == Mode.FLOAT ? 1 - 0.6f * a / life : 1;
            return Math.max(0, Math.min(in, out)) * base;
        }

        public void tick() {
            ox = x;
            oy = y;
            oz = z;
            oPitch = pitch;
            oYaw = yaw;
            oRoll = roll;
            age++;
            switch (mode) {
                case FLING -> {
                    vy -= gravity;
                    x += vx;
                    y += vy;
                    z += vz;
                    vx *= 0.985;
                    vz *= 0.985;
                    if (y <= floor && vy < 0) {
                        y = floor;
                        if (bounces++ < 2 && vy < -0.12) {
                            vy = -vy * 0.35;
                            vx *= 0.6;
                            vz *= 0.6;
                            sp *= 0.5f;
                            sy *= 0.5f;
                            sr *= 0.5f;
                        } else {
                            vx = vy = vz = 0;
                            sp = sy = sr = 0;
                        }
                    }
                    pitch += sp;
                    yaw += sy;
                    roll += sr;
                }
                case RISE -> {
                    float t = age / (float) life;
                    // Up over the first fifth (overshooting a little), hold, down over the last fifth.
                    double up = t < 0.2 ? ease(t / 0.2) * 1.08 : t < 0.26 ? 1.08 - 0.08 * (t - 0.2) / 0.06
                            : t > 0.8 ? 1 - ease((t - 0.8) / 0.2) : 1;
                    y = floor - 1 + up * height;
                }
                case FLOAT -> {
                    vy = Math.min(0.12, vy + 0.004);
                    x += vx + Math.sin(age * 0.07 + yaw) * 0.01;
                    y += vy;
                    z += vz + Math.cos(age * 0.06 + pitch) * 0.01;
                    pitch += sp * 0.3f;
                    yaw += sy * 0.3f;
                    roll += sr * 0.3f;
                }
            }
        }

        private static double ease(double t) {
            t = Math.max(0, Math.min(1, t));
            return 1 - (1 - t) * (1 - t) * (1 - t);
        }
    }

    /**
     * A new piece at (x, y, z), launched away from the centre (cx, cz).
     *
     * @param speed outward blocks a tick; up initial upward speed; spin tumbling strength
     */
    public static Piece launch(Object block, Mode mode, double x, double y, double z, double cx, double cz, float speed,
            float up, float spin, float gravity, int life, double height, Random rnd) {
        Piece p = new Piece(block, mode, x, y, z, life, height, gravity);
        double dx = x - cx, dz = z - cz, d = Math.sqrt(dx * dx + dz * dz);
        if (d < 0.01) {
            double a = rnd.nextDouble() * Math.PI * 2;
            dx = Math.cos(a);
            dz = Math.sin(a);
            d = 1;
        }
        if (mode == Mode.FLING) {
            double s = speed * (0.5 + rnd.nextDouble());
            p.vx = dx / d * s;
            p.vz = dz / d * s;
            p.vy = up * (0.6 + 0.8 * rnd.nextDouble());
        } else if (mode == Mode.FLOAT) {
            p.vx = (rnd.nextDouble() - 0.5) * 0.02;
            p.vz = (rnd.nextDouble() - 0.5) * 0.02;
            p.vy = 0.01 + rnd.nextDouble() * 0.03;
        }
        if (mode != Mode.RISE) {
            p.sp = (rnd.nextFloat() - 0.5f) * 40 * spin;
            p.sy = (rnd.nextFloat() - 0.5f) * 40 * spin;
            p.sr = (rnd.nextFloat() - 0.5f) * 40 * spin;
        }
        return p;
    }
}
