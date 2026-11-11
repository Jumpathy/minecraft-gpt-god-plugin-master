package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntFunction;

/**
 * The solid side of a god's death ({@link DeathShow} draws the particles): display entities that move
 * through the world. The ground itself heaves as each shockwave rolls through (copies of the real
 * surface blocks lift and settle), pillars of light rise as glowing rods, and each death has its own
 * pieces: a white-gold core that implodes and flares (supernova), rune glyphs spiralling into the sky
 * (unraveling), glass shards that burst out, tumble and lie where they land (shattering), and a black
 * hole with a spinning disc that tears up the ground and swallows it (abyssal).
 *
 * Pure: a piece is a look plus a pose for every frame, built from a {@link Ground} sampler so tests and
 * previews run without a server. Offsets are in blocks from the ground spot; h is the body's height.
 */
public final class DeathPieces {
    private DeathPieces() {
    }

    private static final double TAU = Math.PI * 2;

    /** How a piece is drawn. */
    public enum Kind {
        /** A BlockDisplay everyone sees (look = block data, e.g. "minecraft:end_rod"). */
        BLOCK,
        /** A textured ItemDisplay from the pack (look = model key); players without the pack get the fallback. */
        SKIN,
        /** A TextDisplay everyone sees (look = the text). */
        TEXT
    }

    /**
     * Where a piece is and how big, on one frame. x, y, z is its center; sx, sy, sz its size in blocks;
     * yaw (round Y), pitch (round X) and roll (round Z) turn it, applied in that order. Size 0 hides it.
     */
    public record Pose(double x, double y, double z, double sx, double sy, double sz, double yaw, double pitch,
            double roll) {
        public boolean hidden() {
            return sx <= 1e-4 || sy <= 1e-4 || sz <= 1e-4;
        }

        static Pose hiddenAt(double x, double y, double z) {
            return new Pose(x, y, z, 0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * One display entity of the show, alive from frame `from` until (not including) `to`.
     *
     * @param fallback for SKIN pieces, what players without the pack see: block data, "text:..." for a
     *                 TextDisplay, or null for nothing
     * @param rgb      its color (glow outline, text color, and the preview)
     * @param glow     an outline in rgb
     * @param bright   full brightness, ignoring the light around it
     * @param billboard always faces the viewer
     */
    public record Piece(Kind kind, String look, String fallback, int rgb, int from, int to, boolean glow,
            boolean bright, boolean billboard, IntFunction<Pose> pose) {
        public Pose at(int f) {
            return pose.apply(f);
        }

        public boolean alive(int f) {
            return f >= from && f < to;
        }
    }

    /** A surface block near the death: its block data and the height of its top face (relative). */
    public record Tile(String block, double top) {
    }

    /** Reads the real ground around the death. Null where there is no solid block to copy. */
    public interface Ground {
        Tile tile(int dx, int dz);
    }

    /** Flat grass everywhere (tests and previews). */
    public static final Ground FLAT = (dx, dz) -> new Tile("minecraft:grass_block[snowy=false]", 0);

    /** At most this many ground blocks heave (they are the most numerous pieces). */
    public static final int MAX_TILES = 150;

    /**
     * Every piece of a death.
     *
     * @param fx, fz where the ground spot sits inside its block (0..1), so copies line up with real blocks
     */
    public static List<Piece> build(DeathShow.Variant v, double h, double fx, double fz, Ground ground) {
        List<Piece> out = new ArrayList<>();
        heave(out, v, h, fx, fz, ground);
        pillars(out, v, h);
        rings(out, v, h);
        switch (v) {
            case SUPERNOVA -> supernova(out, h);
            case UNRAVELING -> unraveling(out, h);
            case SHATTERING -> shattering(out, h);
            case ABYSSAL -> abyssal(out, h, fx, fz, ground);
        }
        return out;
    }

    static double ease(double t) {
        return Shapes.ease(t);
    }

    // ---- The ground heaves -----------------------------------------------------------------------

    /** How high a tile at distance d stands on frame f, as the shockwaves pass under it. */
    static double lift(double d, double reach, int f) {
        double up = 0;
        for (int i = 0; i < DeathShow.WAVES.length; i++) {
            int w0 = DeathShow.WAVES[i];
            double front = 1 + reach * (f - w0 + 1) / 10.0;
            double gap = front - d;
            if (gap < -0.2 || gap > 3) continue;
            double shape = Math.sin(Math.PI * (gap + 0.2) / 3.2);
            double amp = (i == 0 ? 1.0 : 0.65) * (1 - 0.55 * d / reach);
            up = Math.max(up, Math.max(0, shape) * amp);
        }
        return up;
    }

    private static void heave(List<Piece> out, DeathShow.Variant v, double h, double fx, double fz, Ground ground) {
        double reach = DeathShow.reach(h);
        // Tiles on rings, spaced so the whole field stays under MAX_TILES.
        List<int[]> cells = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        double spacing = 1.0;
        for (int tries = 0; tries < 6; tries++) {
            cells.clear();
            seen.clear();
            for (double r = 2.5; r <= reach * 0.92; r += 2.2 * spacing) {
                int n = (int) Math.max(6, TAU * r / (1.25 * spacing));
                for (int i = 0; i < n; i++) {
                    double a = i * TAU / n + r * 0.37;
                    int dx = (int) Math.floor(Math.cos(a) * r + fx);
                    int dz = (int) Math.floor(Math.sin(a) * r + fz);
                    if (seen.add(((long) dx << 32) ^ (dz & 0xffffffffL))) cells.add(new int[] { dx, dz });
                }
            }
            if (cells.size() <= MAX_TILES) break;
            spacing *= 1.25;
        }
        for (int[] c : cells) {
            Tile t = ground.tile(c[0], c[1]);
            if (t == null) continue;
            double cx = c[0] + 0.5 - fx, cz = c[1] + 0.5 - fz;
            double d = Math.hypot(cx, cz);
            double out0 = Math.atan2(cz, cx);
            double cy = t.top() - 0.5 + 0.012;
            out.add(new Piece(Kind.BLOCK, t.block(), null, 0x7A9A50, DeathShow.WAVES[0] - 1, DeathShow.BURST + 6,
                    false, false, false, f -> {
                        double up = lift(d, reach, f) * 0.9;
                        // It tips outward, away from the god, as it rises.
                        double tilt = up * 0.45;
                        return new Pose(cx, cy + up, cz, 1.0, 1.0, 1.0, -out0 + Math.PI / 2, tilt, 0);
                    }));
        }
    }

    // ---- Pillars of light ------------------------------------------------------------------------

    private static void pillars(List<Piece> out, DeathShow.Variant v, double h) {
        double reach = DeathShow.reach(h);
        int rgb = DeathShow.palette(v).light().rgb;
        for (int start = 28; start <= 44; start += 4) {
            Random pr = new Random(start * 31L + v.ordinal());
            for (int i = 0; i < 3; i++) {
                double a = pr.nextDouble() * TAU;
                double r = 3 + pr.nextDouble() * reach * 0.6;
                double x = Math.cos(a) * r, z = Math.sin(a) * r;
                int s0 = start;
                // A shaft of soft light (two crossed glowing planes); without the pack, a glowing column.
                out.add(new Piece(Kind.SKIN, "beam_" + v.id(), "glow:" + Integer.toHexString(rgb), rgb, s0,
                        DeathShow.BURST + 7, false, true, false, f -> {
                            int age = f - s0;
                            double top = Math.min(18, 3 + age * 2.6);
                            // At the burst they swell for a moment, then wink out.
                            double width = 1.3;
                            if (f >= DeathShow.BURST) width = Math.max(0, 2.6 - (f - DeathShow.BURST) * 0.45);
                            if (age < 0) return Pose.hiddenAt(x, 0, z);
                            return new Pose(x, top / 2, z, width, top, width, 0, 0, 0);
                        }));
            }
        }
    }

    // ---- Shockwave rings (pack only) -------------------------------------------------------------

    private static void rings(List<Piece> out, DeathShow.Variant v, double h) {
        double reach = DeathShow.reach(h);
        int rgb = DeathShow.palette(v).light().rgb;
        for (int w0 : DeathShow.WAVES) {
            out.add(new Piece(Kind.SKIN, "ring_" + v.id(), null, rgb, w0, w0 + 10, false, true, false, f -> {
                double r = 1 + reach * (f - w0 + 1) / 10.0;
                return new Pose(0, 0.18 + (f - w0) * 0.02, 0, r * 2, r * 2, 1, 0, Math.PI / 2, 0);
            }));
        }
        out.add(new Piece(Kind.SKIN, "ring_" + v.id(), null, rgb, DeathShow.BURST, DeathShow.BURST + 7, false, true,
                false, f -> {
                    double r = 2 + (f - DeathShow.BURST) * reach * 0.3;
                    return new Pose(0, h * 0.62, 0, r * 2, r * 2, 1, 0, Math.PI / 2, 0);
                }));
    }

    // ---- Supernova -------------------------------------------------------------------------------

    private static void supernova(List<Piece> out, double h) {
        double chest = h * 0.62;
        double s = Math.max(1, h / 1.8);
        String[] cubes = { "minecraft:pearlescent_froglight[axis=y]", "minecraft:ochre_froglight[axis=y]",
                "minecraft:shroomlight" };
        for (int i = 0; i < cubes.length; i++) {
            int k = i;
            out.add(new Piece(Kind.BLOCK, cubes[i], null, 0xFFE08A, DeathShow.COLLAPSE, DeathShow.BURST + 3, true,
                    true, false, f -> {
                        double size;
                        if (f < DeathShow.BURST) {
                            double u = (f - DeathShow.COLLAPSE) / (double) (DeathShow.BURST - DeathShow.COLLAPSE);
                            size = (2.4 - 2.0 * ease(u)) * s * (1 - k * 0.12);
                        } else {
                            size = (f - DeathShow.BURST) < 2 ? 5.5 * s : 0;
                        }
                        double spin = f * (0.25 + k * 0.1);
                        return new Pose(0, chest, 0, size, size, size, spin + k, spin * 0.7 + k * 0.8, k * 0.6);
                    }));
        }
        // The corona: a billboard of light that swells to blind at the burst.
        out.add(new Piece(Kind.SKIN, "corona", null, 0xFFF0A0, 42, DeathShow.BURST + 6, false, true, true, f -> {
            double size = f < DeathShow.BURST ? 1 + (f - 42) * 0.25 : 3 + (DeathShow.BURST + 6 - f) * 3.5 * s;
            if (f >= DeathShow.BURST + 5) size = 2;
            return new Pose(0, chest, 0, size, size, 1, 0, 0, 0);
        }));
        // Rays of light stab out of the core at the burst.
        for (int i = 0; i < 12; i++) {
            double a = i * TAU / 12;
            double tilt = (i % 3 - 1) * 0.5;
            out.add(new Piece(Kind.SKIN, "beam_supernova", null, 0xFFF0A0, DeathShow.BURST,
                    DeathShow.BURST + 6, false, true, false, f -> {
                        int age = f - DeathShow.BURST;
                        double len = age < 3 ? 4 + age * 5 : Math.max(0, 14 - (age - 2) * 4);
                        double width = age < 3 ? 1.1 : 0.7;
                        double dx = Math.cos(a) * Math.cos(tilt), dy = Math.sin(tilt), dz = Math.sin(a) * Math.cos(tilt);
                        double mid = len / 2 + 0.6;
                        // A beam stands along Y: turn it to lie along its ray.
                        double yaw = -a + Math.PI / 2;
                        double pitch = Math.PI / 2 - tilt;
                        return new Pose(dx * mid, chest + dy * mid, dz * mid, width, len, width, yaw, pitch, 0);
                    }));
        }
    }

    // ---- Unraveling ------------------------------------------------------------------------------

    static final String[] RUNES = { "ᚠ", "ᚱ", "ᛟ", "ᛉ", "ᚦ", "ᛞ", "ᛗ", "ᛃ" };

    private static void unraveling(List<Piece> out, double h) {
        double s = Math.max(1, h / 1.8);
        for (int g = 0; g < 16; g++) {
            int k = g;
            int strand = g % 3;
            double offset = g / 16.0;
            int rgb = g % 3 == 0 ? 0x4AE3FF : 0xC77DFF;
            out.add(new Piece(Kind.SKIN, "sigil_" + (g % 4), "text:" + RUNES[g % RUNES.length], rgb,
                    DeathShow.COLLAPSE + g / 2, DeathShow.BURST + 6, true, true, false, f -> {
                        double u = (f - DeathShow.COLLAPSE) / (double) (DeathShow.BURST - DeathShow.COLLAPSE);
                        // Each glyph peels off the body and climbs its strand of the spiral.
                        double t = Math.min(1, Math.max(0, u * 1.2 - offset * 0.5));
                        double y = 0.3 * h + t * (h + 18) + offset * 2;
                        double r = 0.5 * s + t * 3.5;
                        double a = strand * TAU / 3 + t * 2.5 * TAU + f * 0.08 + offset * 4;
                        double size = (0.6 + 0.6 * Math.min(1, t * 3)) * s;
                        if (f >= DeathShow.BURST) {
                            // At the burst they fly up and away, shrinking into the sky.
                            int age = f - DeathShow.BURST;
                            y += age * age * 0.9;
                            r += age * 1.2;
                            size *= Math.max(0, 1 - age / 6.0);
                        }
                        return new Pose(Math.cos(a) * r, y, Math.sin(a) * r, size, size, 0.6, -a + Math.PI / 2 + f * 0.05 * k,
                                0, 0);
                    }));
        }
    }

    // ---- Shattering ------------------------------------------------------------------------------

    /** Where a shard is: thrown from a point with a velocity, falling, and lying still once it lands. */
    static double[] shardPath(double x, double y, double z, double vx, double vy, double vz, int age) {
        double px = x, py = y, pz = z;
        for (int t = 0; t < age; t++) {
            if (py <= 0.03) {
                py = 0.03;
                break;
            }
            px += vx;
            py += vy;
            pz += vz;
            vx *= 0.94;
            vz *= 0.94;
            vy -= 0.09;
        }
        return new double[] { px, Math.max(0.03, py), pz };
    }

    private static void shattering(List<Piece> out, double h) {
        double s = Math.max(1, h / 1.8);
        Random rnd = new Random(77);
        String[] glass = { "minecraft:glass", "minecraft:red_stained_glass", "minecraft:white_stained_glass",
                "minecraft:light_blue_stained_glass" };
        for (int i = 0; i < 40; i++) {
            boolean late = i >= 22; // the second volley flies out of the burst
            int start = late ? DeathShow.BURST + 1 : DeathShow.COLLAPSE + rnd.nextInt(20);
            double a = rnd.nextDouble() * TAU;
            double y0 = late ? h * 0.62 : (0.15 + rnd.nextDouble() * 0.8) * h;
            double r0 = 0.3 * s;
            double sp = (late ? 0.5 : 0.2) + rnd.nextDouble() * (late ? 0.5 : 0.25);
            double vy = (late ? 0.3 : 0.15) + rnd.nextDouble() * 0.3;
            double size = (0.25 + rnd.nextDouble() * 0.35) * s;
            double spinA = rnd.nextDouble() * 0.6 + 0.2, spinB = rnd.nextDouble() * 0.5;
            String block = glass[i % glass.length];
            int rgb = i % 4 == 1 ? 0xD0102A : 0xCDEFFF;
            int lie = DeathShow.FRAMES - 8;
            out.add(new Piece(Kind.SKIN, "shard_" + (i % 3), block, rgb, start, DeathShow.FRAMES - 1, false, false,
                    false, f -> {
                        int age = f - start;
                        double[] p = shardPath(Math.cos(a) * r0, y0, Math.sin(a) * r0, Math.cos(a) * sp, vy,
                                Math.sin(a) * sp, age);
                        boolean landed = p[1] <= 0.031;
                        double k = f < lie ? 1 : Math.max(0, 1 - (f - lie) / 6.0);
                        double sz = size * k;
                        if (landed) {
                            // Lying flat where it fell, still a little askew.
                            return new Pose(p[0], 0.03, p[2], sz, sz, 0.06 * s, a, Math.PI / 2, spinB);
                        }
                        return new Pose(p[0], p[1], p[2], sz, sz, 0.06 * s, a + age * spinA, age * spinA * 1.3,
                                age * spinB);
                    }));
        }
    }

    // ---- Abyssal ---------------------------------------------------------------------------------

    private static void abyssal(List<Piece> out, double h, double fx, double fz, Ground ground) {
        double chest = h * 0.62;
        double s = Math.max(1, h / 1.8);
        // The black hole: three dark cubes turned against each other read as a ball.
        String[] cores = { "minecraft:black_concrete", "minecraft:obsidian", "minecraft:crying_obsidian" };
        for (int i = 0; i < 3; i++) {
            int k = i;
            out.add(new Piece(Kind.BLOCK, cores[i], null, 0x7B2CBF, DeathShow.COLLAPSE, DeathShow.BURST + 1,
                    i == 2, false, false, f -> {
                        double u = (f - DeathShow.COLLAPSE) / (double) (DeathShow.BURST - DeathShow.COLLAPSE);
                        double size = (0.4 + 1.8 * ease(u)) * s * (1 - k * 0.08);
                        if (f >= DeathShow.BURST - 1) size *= 0.3;
                        double spin = f * 0.18;
                        return new Pose(0, chest, 0, size, size, size, spin + k * 0.7, k * 0.78, k * 0.5 + spin * 0.3);
                    }));
        }
        // The disc of light it drags round itself (textured); without the pack, stained glass panes.
        for (int i = 0; i < 3; i++) {
            int k = i;
            String fallback = i == 1 ? "minecraft:black_stained_glass" : "minecraft:purple_stained_glass";
            out.add(new Piece(Kind.SKIN, i == 0 ? "disc" : "disc_" + i, fallback, 0xB24BF3, DeathShow.COLLAPSE,
                    DeathShow.BURST, true, true, false, f -> {
                        double u = (f - DeathShow.COLLAPSE) / (double) (DeathShow.BURST - DeathShow.COLLAPSE);
                        double size = (3 + 13 * ease(Math.min(1, u * 1.4))) * (1 - k * 0.15);
                        if (f >= DeathShow.BURST - 2) size *= 0.25;
                        double spin = f * 0.35 + k * TAU / 9;
                        return new Pose(0, chest + k * 0.02, 0, size, size, 0.04, spin, Math.PI / 2 - 0.25, 0);
                    }));
        }
        // It tears up the ground around it and swallows it: copies of the real blocks rise and spiral in.
        double reach = DeathShow.reach(h);
        Random rnd = new Random(31);
        for (int i = 0; i < 26; i++) {
            double a0 = rnd.nextDouble() * TAU;
            double r0 = 4 + rnd.nextDouble() * reach * 0.7;
            int dx = (int) Math.floor(Math.cos(a0) * r0 + fx), dz = (int) Math.floor(Math.sin(a0) * r0 + fz);
            Tile t = ground.tile(dx, dz);
            if (t == null) continue;
            double cx = dx + 0.5 - fx, cz = dz + 0.5 - fz;
            double rr = Math.hypot(cx, cz), aa = Math.atan2(cz, cx);
            double y0 = t.top() - 0.5 + 0.012;
            int start = DeathShow.COLLAPSE + 2 + rnd.nextInt(16);
            int fall = 14 + rnd.nextInt(6);
            double size0 = 0.6 + rnd.nextDouble() * 0.4;
            out.add(new Piece(Kind.BLOCK, t.block(), null, 0x6A5A40, start, start + fall, false, false, false, f -> {
                double u = (f - start) / (double) fall;
                double e = ease(u);
                double r = rr * (1 - e);
                double a = aa + e * 2.4;
                double y = y0 + (chest - y0) * Math.min(1, e * 1.3) + Math.sin(u * Math.PI) * 1.5;
                double size = size0 * (1 - Math.pow(u, 3));
                return new Pose(Math.cos(a) * r, y, Math.sin(a) * r, size, size, size, f * 0.4, f * 0.3, 0);
            }));
        }
    }

    /** How many pieces are alive at once at most, for tests and the console. */
    public static int peak(List<Piece> pieces) {
        int max = 0;
        for (int f = 0; f < DeathShow.FRAMES; f++) {
            int n = 0;
            for (Piece p : pieces) {
                if (p.alive(f)) n++;
            }
            max = Math.max(max, n);
        }
        return max;
    }
}
