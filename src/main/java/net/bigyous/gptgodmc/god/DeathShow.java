package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.bukkit.util.Vector;

import net.bigyous.gptgodmc.god.Rites.Cue;
import net.bigyous.gptgodmc.god.Rites.Dot;
import net.bigyous.gptgodmc.god.Rites.Tint;

/**
 * The death of a god, as a timeline of frames (one frame = 2 ticks, like {@link Rites}). Four stages:
 * the WOUND (the body staggers, light leaks out of it, the heartbeat slows), the COLLAPSE (shockwave
 * rings, pillars of light, waves along the ground, the sky darkening), the BURST (a blinding
 * detonation with layered fireworks and lightning) and the AFTERGLOW (embers, the god's eyes closing,
 * a last echo, then silence while the loot glows). Four variants give each stage its own heart:
 * supernova, unraveling, shattering and abyssal. Pure and deterministic, so it is previewed offline;
 * {@link Godslayer} plays it.
 *
 * Offsets are in blocks from the ground under the body; h is the body's height in blocks.
 */
public final class DeathShow {
    private DeathShow() {
    }

    private static final double TAU = Math.PI * 2;

    public enum Variant {
        /** A white-gold implosion, then a blinding burst. */
        SUPERNOVA,
        /** The body comes apart in a rising spiral of glyphs. */
        UNRAVELING,
        /** It breaks like glass into falling shards, with shockwaves. */
        SHATTERING,
        /** A black hole pulls the light inward, then a violet detonation. */
        ABYSSAL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Variant parse(String s) {
            if (s == null) return null;
            for (Variant v : values()) {
                if (v.id().equalsIgnoreCase(s.trim())) return v;
            }
            return null;
        }
    }

    public enum Stage {
        WOUND, COLLAPSE, BURST, AFTERGLOW
    }

    /** Whole show length, in frames (9 seconds). */
    public static final int FRAMES = 90;
    public static final int COLLAPSE = 25;
    public static final int BURST = 50;
    public static final int AFTERGLOW = 57;
    /** The body vanishes on this frame, the hoard drops on the next. */
    public static final int VANISH = 49;
    public static final int LOOT = 57;
    /** Mortals nearby see darkness (the sky dims) between these frames. */
    public static final int DARK_FROM = 25;
    public static final int DARK_TO = 56;
    /** The heartbeats of the wound stage, slowing. */
    static final int[] HEARTBEATS = { 0, 7, 15, 24 };
    static final int[] WAVES = { 25, 33, 41, 47 };

    public static Stage stage(int f) {
        if (f < COLLAPSE) return Stage.WOUND;
        if (f < BURST) return Stage.COLLAPSE;
        if (f < AFTERGLOW) return Stage.BURST;
        return Stage.AFTERGLOW;
    }

    /** A bolt of lightning (no fire, no damage) at a ground offset. */
    public record Bolt(int frame, double x, double z) {
    }

    /** A firework burst: type is a FireworkEffect.Type name; colors are RGB. */
    public record Firework(int frame, double x, double y, double z, String type, List<Integer> colors,
            List<Integer> fades) {
    }

    /** The colors of a variant: light leaking out, its echo, sparks, the core, and firework colors. */
    public record Palette(Tint light, Tint echo, Tint spark, Tint core, List<Integer> fw, List<Integer> fade) {
    }

    public static Palette palette(Variant v) {
        return switch (v) {
            case SUPERNOVA -> new Palette(Tint.SUN, Tint.GOLD, Tint.STAR, Tint.WHITE,
                    List.of(0xFFC23A, 0xFFFFFF, 0xFFF0A0), List.of(0xFF8A1C));
            case UNRAVELING -> new Palette(Tint.VIOLET, Tint.CYAN, Tint.SPARK, Tint.STAR,
                    List.of(0xB24BF3, 0x4AE3FF, 0xFFFFFF), List.of(0x4B2BD8));
            case SHATTERING -> new Palette(Tint.WHITE, Tint.BLOOD, Tint.SPARK, Tint.CYAN,
                    List.of(0xD0102A, 0xFFFFFF, 0xBFF4FF), List.of(0x5A0000));
            case ABYSSAL -> new Palette(Tint.VIOLET, Tint.INDIGO, Tint.SOUL, Tint.SHADOW,
                    List.of(0xB24BF3, 0x7B2CBF, 0x4B2BD8), List.of(0x52E8F0));
        };
    }

    /**
     * The variant that suits a god: by archetype, else by the look of its body, else at random (seeded,
     * so one god always dies the same way).
     */
    public static Variant pick(String archetype, String look, long seed) {
        String a = archetype == null ? "" : archetype.toLowerCase(Locale.ROOT);
        if (a.contains("benevol") || a.contains("cosmic") || a.contains("sun") || a.contains("radian")) {
            return Variant.SUPERNOVA;
        }
        if (a.contains("chill") || a.contains("trick") || a.contains("calm") || a.contains("sage")) {
            return Variant.UNRAVELING;
        }
        if (a.contains("wrath") || a.contains("war") || a.contains("storm")) return Variant.SHATTERING;
        if (a.contains("evil") || a.contains("dark") || a.contains("void")) return Variant.ABYSSAL;
        String l = look == null ? "" : look.toLowerCase(Locale.ROOT);
        if (l.contains("radiant") || l.contains("gold") || l.contains("celest")) return Variant.SUPERNOVA;
        if (l.contains("void") || l.contains("abyss")) return Variant.ABYSSAL;
        if (l.contains("infernal") || l.contains("fire") || l.contains("crystal")) return Variant.SHATTERING;
        if (l.contains("shade")) return Variant.UNRAVELING;
        Variant[] all = Variant.values();
        return all[(int) Math.floorMod(seed * 0x9E3779B97F4A7C15L >>> 33, (long) all.length)];
    }

    /** How far the shockwaves and ground waves reach for a body h blocks tall. */
    static double reach(double h) {
        return 9 + 1.5 * h;
    }

    // ---- Particles -------------------------------------------------------------------------------

    private static final class Pen {
        final List<Dot> out = new ArrayList<>();

        void at(double x, double y, double z, Tint t) {
            out.add(new Dot(x, y, z, t, true, 0, 0, 0));
        }

        void all(List<Vector> pts, Tint t) {
            for (Vector p : pts) at(p.getX(), p.getY(), p.getZ(), t);
        }

        void fly(double x, double y, double z, Tint t, double vx, double vy, double vz) {
            out.add(new Dot(x, y, z, t, true, vx, vy, vz));
        }

        void burst(int n, double x, double y, double z, double speed, Tint t, double phase) {
            for (Vector d : Shapes.sphere(n, 1, phase)) {
                fly(x + d.getX() * 0.2, y + d.getY() * 0.2, z + d.getZ() * 0.2, t, d.getX() * speed,
                        d.getY() * speed, d.getZ() * speed);
            }
        }
    }

    /** The dots of frame f for a body h blocks tall. */
    public static List<Dot> frame(Variant v, int f, double h) {
        Pen p = new Pen();
        if (f < 0 || f >= FRAMES) return p.out;
        Random rnd = new Random(v.ordinal() * 7919L + f * 104729L + 5);
        Palette pal = palette(v);
        double s = Math.max(1, h / 1.8);
        switch (stage(f)) {
            case WOUND -> wound(p, v, pal, f, h, s, rnd);
            case COLLAPSE -> collapse(p, v, pal, f, h, s, rnd);
            case BURST -> burst(p, v, pal, f, h, s, rnd);
            case AFTERGLOW -> afterglow(p, v, pal, f, h, s, rnd);
        }
        return p.out;
    }

    private static void wound(Pen p, Variant v, Palette pal, int f, double h, double s, Random rnd) {
        double t = f / (double) (COLLAPSE - 1);
        double chest = h * 0.62;
        // Light leaks through cracks in the body: short rays from its surface, more of them each frame.
        int rays = 5 + (int) (f * 1.3);
        double half = 0.3 * s;
        for (int i = 0; i < rays; i++) {
            double a = rnd.nextDouble() * TAU;
            double y = 0.15 * h + rnd.nextDouble() * h * 0.8;
            double x0 = Math.cos(a) * half, z0 = Math.sin(a) * half;
            double len = (0.3 + t * 1.3) * s;
            for (int k = 0; k < 3; k++) {
                double r = half + len * (k + 1) / 3.0;
                p.at(Math.cos(a) * r, y + (k * 0.06 - 0.03) * s, Math.sin(a) * r, k == 0 ? pal.light() : pal.echo());
            }
            if (v == Variant.SHATTERING && i % 2 == 0) {
                // Jagged cracks running over the surface.
                double y1 = y;
                double a1 = a;
                for (int k = 0; k < 4; k++) {
                    a1 += (rnd.nextDouble() - 0.5) * 0.5;
                    y1 += (rnd.nextDouble() - 0.5) * 0.25 * s;
                    p.at(Math.cos(a1) * half * 1.02, y1, Math.sin(a1) * half * 1.02, Tint.WHITE);
                }
            }
        }
        // Each slowing heartbeat throbs out of the chest.
        for (int beat : HEARTBEATS) {
            if (f == beat) {
                if (beat >= 15) p.at(0, chest, 0, Tint.FLASH);
                p.burst(18 + (int) (t * 18), 0, chest, 0, 0.12 + 0.05 * s, Tint.BLOOD, beat);
                p.all(Shapes.ring(0.55 * s, 20, beat, chest), pal.core());
            }
        }
        // Smoke curls off it; at the end a glow wraps the body.
        if (f % 3 == 0) p.fly(0, h * 0.3, 0, Tint.SMOKE, (rnd.nextDouble() - 0.5) * 0.05, 0.04, 0);
        if (f >= 14) {
            for (Vector q : Shapes.helix(2, half * 1.4, half * 1.4, 0, h, 2, 14, f * 0.4)) {
                p.at(q.getX(), q.getY(), q.getZ(), pal.light());
            }
        }
    }

    private static void collapse(Pen p, Variant v, Palette pal, int f, double h, double s, Random rnd) {
        double u = (f - COLLAPSE) / (double) (BURST - COLLAPSE);
        double reach = reach(h);
        double chest = h * 0.62;
        // Shockwave rings roll out along the ground; each throws a wave of particles before it.
        for (int w0 : WAVES) {
            int age = f - w0;
            if (age < 0 || age > 9) continue;
            double r = 1 + reach * (age + 1) / 10.0;
            int n = Math.max(16, Math.min(80, (int) (TAU * r * 0.9)));
            p.all(Shapes.ring(r, n, w0 * 0.3, 0.15), pal.light());
            if (age < 6) p.all(Shapes.ring(r * 0.93, n / 2, w0 * 0.3 + 0.1, 0.35), pal.echo());
            if (age == 0) {
                for (Vector d : Shapes.ring(1, 64, w0, 0)) {
                    p.fly(d.getX() * 1.2, 0.2, d.getZ() * 1.2, w0 % 2 == 0 ? pal.spark() : Tint.SOUL,
                            d.getX() * 0.85, 0.01, d.getZ() * 0.85);
                }
            }
        }
        // Pillars of light rise from the ground around it.
        for (int start = 28; start <= 44; start += 4) {
            int age = f - start;
            if (age < 0 || age > 7) continue;
            Random pr = new Random(start * 31L + v.ordinal());
            for (int i = 0; i < 3; i++) {
                double a = pr.nextDouble() * TAU;
                double r = 3 + pr.nextDouble() * reach * 0.6;
                double x = Math.cos(a) * r, z = Math.sin(a) * r;
                double top = Math.min(18, 3 + age * 2.6);
                // The pillar itself is a display (DeathPieces); sparks leap off its top and lick its foot.
                p.fly(x, top, z, Tint.STAR, 0, 0.6, 0);
                p.at(x, top, z, Tint.STAR);
                if (age < 3) p.fly(x, 0.2, z, pal.echo(), 0, 0.25, 0);
            }
        }
        // The heart of the collapse, different for each death.
        switch (v) {
            case SUPERNOVA -> {
                // Implosion: a shell of light falls into the chest, brightening as it shrinks.
                double r = 0.5 + 7 * (1 - Shapes.ease(u));
                int n = 26 + (int) (u * 30);
                for (Vector q : Shapes.sphere(n, r, f * 0.2)) {
                    p.at(q.getX(), chest + q.getY(), q.getZ(), n % 2 == 0 ? Tint.SUN : Tint.GOLD);
                }
                for (Vector d : Shapes.sphere(14, 1, f)) {
                    p.fly(d.getX() * 7, chest + d.getY() * 7, d.getZ() * 7, Tint.STAR, -d.getX() * 0.35,
                            -d.getY() * 0.35, -d.getZ() * 0.35);
                }
                if (f >= 44) {
                    p.all(Shapes.lift(Shapes.sphere(30, 0.35 + (f - 44) * 0.05, f), chest), Tint.WHITE);
                    if (f % 2 == 0) p.at(0, chest, 0, Tint.FLASH);
                }
            }
            case UNRAVELING -> {
                // The body comes apart: a spiral of glyphs rising into the sky.
                double y0 = u * 6;
                double y1 = h + 4 + u * 22;
                List<Vector> spiral = Shapes.helix(3, 0.5 * s, 3.5, y0, y1, 2.5, 18, f * 0.25);
                for (int i = 0; i < spiral.size(); i++) {
                    Vector q = spiral.get(i);
                    p.at(q.getX(), q.getY(), q.getZ(), i % 3 == 0 ? Tint.CYAN : Tint.VIOLET);
                }
                // The glyphs themselves are displays (DeathPieces).
                for (int i = 0; i < 8; i++) {
                    double a = rnd.nextDouble() * TAU;
                    p.fly(Math.cos(a) * 0.3 * s, rnd.nextDouble() * h, Math.sin(a) * 0.3 * s, Tint.SPARK,
                            Math.cos(a) * 0.05, 0.35 + rnd.nextDouble() * 0.2, Math.sin(a) * 0.05);
                }
            }
            case SHATTERING -> {
                // It breaks apart: shards spit from the body and rain down; cracks blaze across it.
                // The shards are displays (DeathPieces); here only the glitter they shed.
                for (int i = 0; i < 8; i++) {
                    double a = rnd.nextDouble() * TAU;
                    double y = rnd.nextDouble() * h;
                    double sp = 0.15 + rnd.nextDouble() * 0.25;
                    p.fly(Math.cos(a) * 0.3 * s, y, Math.sin(a) * 0.3 * s, i % 3 == 0 ? Tint.CYAN : Tint.WHITE,
                            Math.cos(a) * sp, 0.12 - rnd.nextDouble() * 0.2, Math.sin(a) * sp);
                }
                for (int i = 0; i < 6; i++) {
                    double a = rnd.nextDouble() * TAU;
                    double r = 1 + rnd.nextDouble() * 7;
                    p.fly(Math.cos(a) * r, h + 3 + rnd.nextDouble() * 4, Math.sin(a) * r, Tint.WHITE, 0, -0.45, 0);
                }
                // The ground itself cracks open in jagged lines running out from where it stands.
                Random cr = new Random(v.ordinal() * 17L);
                double grown = Shapes.ease(u * 1.6) * reach(h) * 0.75;
                for (int line = 0; line < 7; line++) {
                    double a = line * TAU / 7 + cr.nextDouble() * 0.4;
                    double x = 0, z = 0;
                    for (double r = 0.8; r < grown; r += 0.35) {
                        a += (cr.nextDouble() - 0.5) * 0.35;
                        x = Math.cos(a) * r;
                        z = Math.sin(a) * r;
                        p.at(x, 0.08, z, ((int) (r * 3)) % 3 == 0 ? Tint.BLOOD : Tint.WHITE);
                    }
                }
                double half = 0.31 * s;
                for (int c = 0; c < 5; c++) {
                    double a = c * TAU / 5 + f * 0.05;
                    double y = h * (0.1 + rnd.nextDouble() * 0.8);
                    for (int k = 0; k < 6; k++) {
                        a += (rnd.nextDouble() - 0.5) * 0.4;
                        y += (rnd.nextDouble() - 0.4) * 0.3 * s;
                        p.at(Math.cos(a) * half, y, Math.sin(a) * half, k % 2 == 0 ? Tint.WHITE : Tint.BLOOD);
                    }
                }
            }
            case ABYSSAL -> {
                // A black hole: a spinning disc spirals into a growing knot of darkness.
                for (int arm = 0; arm < 3; arm++) {
                    for (int i = 0; i < 18; i++) {
                        double r = 0.6 + 10 * i / 17.0;
                        double a = arm * TAU / 3 + (10.6 - r) * 0.6 + f * 0.35;
                        p.at(Math.cos(a) * r, chest + Math.sin(a * 2) * 0.15, Math.sin(a) * r,
                                i % 2 == 0 ? Tint.VIOLET : Tint.INDIGO);
                    }
                }
                p.all(Shapes.lift(Shapes.sphere(36, 0.4 + u * 1.5 * s, f * 0.3), chest), Tint.SHADOW);
                for (Vector d : Shapes.ring(1, 18, f * 0.7, 0)) {
                    double r = 9;
                    p.fly(d.getX() * r, 0.3 + rnd.nextDouble() * h, d.getZ() * r, Tint.SOUL,
                            -d.getX() * 0.4 - d.getZ() * 0.18, (chest - 1) * 0.02, -d.getZ() * 0.4 + d.getX() * 0.18);
                }
            }
        }
    }

    private static void burst(Pen p, Variant v, Palette pal, int f, double h, double s, Random rnd) {
        double chest = h * 0.62;
        if (f == BURST) {
            p.at(0, chest, 0, Tint.FLASH);
            p.at(0, chest + 2, 0, Tint.FLASH);
            p.at(0, chest + 5, 0, Tint.FLASH);
            p.burst(150, 0, chest, 0, 0.95, Tint.STAR, 0);
            p.burst(110, 0, chest, 0, 0.6, pal.light(), 0.3);
            for (Vector d : Shapes.ring(1, 90, 0, 0)) {
                p.fly(d.getX(), 0.25, d.getZ(), pal.echo(), d.getX() * 1.1, 0.02, d.getZ() * 1.1);
            }
        } else if (f == BURST + 1) {
            p.burst(110, 0, chest, 0, 0.45, Tint.SOUL, 0.7);
            p.burst(90, 0, chest, 0, 0.75, pal.spark(), 1.1);
            switch (v) {
                case SUPERNOVA -> p.burst(160, 0, chest, 0, 1.3, Tint.SUN, 0.2);
                case UNRAVELING -> {
                    for (double y = 0; y < 26; y += 0.3) p.at(0, y, 0, y % 1.2 < 0.4 ? Tint.CYAN : Tint.STAR);
                    p.burst(80, 0, 24, 0, 0.6, Tint.VIOLET, 0);
                }
                case SHATTERING -> {
                    for (int i = 0; i < 60; i++) {
                        double a = rnd.nextDouble() * TAU;
                        double sp = 0.4 + rnd.nextDouble() * 0.6;
                        p.fly(0, chest, 0, i % 4 == 0 ? Tint.BLOOD : Tint.WHITE, Math.cos(a) * sp,
                                (rnd.nextDouble() - 0.3) * 0.6, Math.sin(a) * sp);
                    }
                }
                case ABYSSAL -> {
                    p.burst(160, 0, chest, 0, 1.1, Tint.VIOLET, 0.5);
                    p.all(Shapes.ring(4 * s, 60, 0, chest), Tint.INDIGO);
                }
            }
        } else {
            // Aftershocks high in the air, and the flash fading.
            int k = f - BURST;
            Random br = new Random(v.ordinal() * 13L + k);
            double a = br.nextDouble() * TAU;
            double r = 2 + br.nextDouble() * 4;
            p.burst(60, Math.cos(a) * r, chest + 4 + br.nextDouble() * 6, Math.sin(a) * r, 0.45,
                    k % 2 == 0 ? pal.light() : pal.echo(), k);
            p.burst(30, 0, chest, 0, 0.3, Tint.STAR, k * 0.4);
            if (k == 2) p.at(0, chest, 0, Tint.FLASH);
        }
    }

    private static void afterglow(Pen p, Variant v, Palette pal, int f, double h, double s, Random rnd) {
        double u = (f - AFTERGLOW) / (double) (FRAMES - AFTERGLOW);
        // Embers drift up from where it fell, fewer and fewer.
        int embers = (int) Math.round(16 * (1 - u));
        for (int i = 0; i < embers; i++) {
            double a = rnd.nextDouble() * TAU;
            double r = rnd.nextDouble() * 4;
            p.fly(Math.cos(a) * r, 0.2 + rnd.nextDouble() * 1.5, Math.sin(a) * r,
                    i % 3 == 0 ? Tint.EMBER : pal.light(), (rnd.nextDouble() - 0.5) * 0.03,
                    0.05 + rnd.nextDouble() * 0.07, (rnd.nextDouble() - 0.5) * 0.03);
        }
        // Its eyes hang where its head was, and slowly close.
        if (f < 82 && f % 2 == 1) {
            double open = Math.max(0, 0.06 * s * (1 - (f - AFTERGLOW) / 25.0));
            double eyeY = h * 0.92;
            Tint eye = v == Variant.SUPERNOVA ? Tint.SUN : v == Variant.SHATTERING ? Tint.BLOOD : Tint.VIOLET;
            for (int side = -1; side <= 1; side += 2) {
                for (Vector q : Shapes.eye(0.11 * s, open, 7)) {
                    p.at(side * 0.13 * s + q.getX(), eyeY + q.getY(), q.getZ(), eye);
                }
            }
        }
        // A last glow over the hoard.
        if (f % 2 == 0 && u < 0.8) p.all(Shapes.ring(1.3, 18, f * 0.2, 0.1), pal.echo());
        if (f % 4 == 0) p.fly(0, 0.3, 0, Tint.SMOKE, 0, 0.05, 0);
    }

    // ---- Sounds, lightning and fireworks ---------------------------------------------------------

    public static List<Cue> cues(Variant v) {
        List<Cue> c = new ArrayList<>(List.of(
                new Cue(0, "entity.warden.heartbeat", 3f, 1.0f),
                new Cue(0, "entity.wither.hurt", 3f, 0.5f),
                new Cue(7, "entity.warden.heartbeat", 3f, 0.85f),
                new Cue(10, "block.respawn_anchor.deplete", 2.5f, 0.6f),
                new Cue(15, "entity.warden.heartbeat", 3f, 0.7f),
                new Cue(24, "entity.warden.heartbeat", 3.5f, 0.55f),
                new Cue(25, "entity.warden.sonic_boom", 3f, 0.6f),
                new Cue(25, "block.beacon.deactivate", 3f, 0.5f),
                new Cue(33, "entity.lightning_bolt.thunder", 4f, 0.7f),
                new Cue(41, "entity.warden.sonic_boom", 3f, 0.5f),
                new Cue(45, "block.bell.resonate", 3f, 0.5f),
                new Cue(49, "entity.warden.sonic_boom", 4f, 0.4f),
                new Cue(50, "entity.wither.death", 5f, 0.6f),
                new Cue(50, "entity.ender_dragon.death", 5f, 0.8f),
                new Cue(50, "entity.generic.explode", 5f, 0.5f),
                new Cue(50, "entity.lightning_bolt.thunder", 5f, 0.5f),
                new Cue(55, "block.bell.use", 4f, 0.5f),
                new Cue(60, "block.beacon.deactivate", 3f, 0.4f),
                new Cue(62, "block.bell.use", 3f, 0.45f),
                new Cue(70, "block.bell.use", 2f, 0.4f),
                new Cue(80, "block.respawn_anchor.deplete", 1.5f, 0.5f)));
        switch (v) {
            case SUPERNOVA -> {
                c.add(new Cue(30, "block.beacon.power_select", 3f, 0.5f));
                c.add(new Cue(40, "block.end_portal.spawn", 2f, 1.2f));
                c.add(new Cue(47, "block.respawn_anchor.charge", 3f, 0.5f));
                c.add(new Cue(51, "item.totem.use", 3f, 0.6f));
            }
            case UNRAVELING -> {
                c.add(new Cue(28, "block.enchantment_table.use", 3f, 0.5f));
                c.add(new Cue(31, "entity.evoker.prepare_summon", 3f, 0.6f));
                c.add(new Cue(38, "block.amethyst_block.resonate", 3f, 0.5f));
                c.add(new Cue(45, "block.portal.trigger", 1.5f, 1.2f));
            }
            case SHATTERING -> {
                c.add(new Cue(20, "block.glass.break", 3f, 0.6f));
                c.add(new Cue(26, "block.amethyst_block.break", 3f, 0.5f));
                c.add(new Cue(33, "block.glass.break", 3f, 0.5f));
                c.add(new Cue(41, "block.glass.break", 3f, 0.4f));
                c.add(new Cue(50, "block.glass.break", 5f, 0.5f));
                c.add(new Cue(51, "block.amethyst_cluster.break", 4f, 0.5f));
            }
            case ABYSSAL -> {
                c.add(new Cue(25, "block.portal.trigger", 2f, 0.5f));
                c.add(new Cue(30, "entity.enderman.stare", 3f, 0.5f));
                c.add(new Cue(38, "block.end_portal.spawn", 3f, 0.5f));
                c.add(new Cue(46, "entity.wither.spawn", 3f, 0.6f));
                c.add(new Cue(51, "entity.enderman.death", 3f, 0.5f));
            }
        }
        c.sort((a, b) -> Integer.compare(a.frame(), b.frame()));
        return c;
    }

    public static List<Bolt> bolts(Variant v, double h) {
        Random rnd = new Random(v.ordinal() * 991L + 3);
        List<Bolt> out = new ArrayList<>();
        double reach = reach(h);
        out.add(ring(rnd, 12, 3, 5));
        for (int w : new int[] { 25, 33, 41 }) {
            out.add(ring(rnd, w, reach * 0.4, reach * 0.8));
            out.add(ring(rnd, w, reach * 0.4, reach * 0.8));
        }
        for (int i = 0; i < 6; i++) {
            double a = i * TAU / 6 + rnd.nextDouble() * 0.3;
            double r = 3 + h;
            out.add(new Bolt(BURST, Math.cos(a) * r, Math.sin(a) * r));
        }
        for (int i = 0; i < 3; i++) out.add(ring(rnd, BURST + 3, 4, reach * 0.6));
        return out;
    }

    private static Bolt ring(Random rnd, int frame, double r0, double r1) {
        double a = rnd.nextDouble() * TAU;
        double r = r0 + rnd.nextDouble() * (r1 - r0);
        return new Bolt(frame, Math.cos(a) * r, Math.sin(a) * r);
    }

    public static List<Firework> fireworks(Variant v, double h) {
        Palette pal = palette(v);
        double chest = h * 0.62;
        List<Integer> c = pal.fw();
        List<Integer> fade = pal.fade();
        String big = "BALL_LARGE";
        String small = switch (v) {
            case SUPERNOVA -> "BALL";
            case UNRAVELING -> "STAR";
            case SHATTERING -> "BURST";
            case ABYSSAL -> "BURST";
        };
        double top = v == Variant.UNRAVELING ? 22 : 9;
        List<Firework> out = new ArrayList<>();
        out.add(new Firework(BURST, 0, chest + 1, 0, big, c, fade));
        out.add(new Firework(BURST + 1, 3, chest + 4, 1, small, c, fade));
        out.add(new Firework(BURST + 1, -2.5, chest + 5, -2, small, c, fade));
        out.add(new Firework(BURST + 2, 1, chest + top, -1, big, c, fade));
        out.add(new Firework(BURST + 3, -3, chest + 7, 2.5, small, List.of(c.get(1)), fade));
        out.add(new Firework(BURST + 4, 2, chest + 3, -3, small, List.of(c.get(0)), fade));
        out.add(new Firework(BURST + 6, 0, chest + top + 4, 0, big, c, fade));
        out.add(new Firework(AFTERGLOW + 4, 0, chest + 12, 0, small, List.of(c.get(2)), fade));
        return out;
    }

    /** Particles in one frame at most (the show draws within Spectacle's budget anyway). */
    public static int maxDots(Variant v, double h) {
        int max = 0;
        for (int f = 0; f < FRAMES; f++) max = Math.max(max, frame(v, f, h).size());
        return max;
    }
}
