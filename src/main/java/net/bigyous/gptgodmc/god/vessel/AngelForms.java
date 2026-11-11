package net.bigyous.gptgodmc.god.vessel;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import net.bigyous.gptgodmc.god.Shapes;
import net.bigyous.gptgodmc.god.vessel.TrueForm.Form;
import net.bigyous.gptgodmc.god.vessel.TrueForm.Glyph;
import net.bigyous.gptgodmc.god.vessel.TrueForm.Mote;

/**
 * The true form's angelic shapes, as pure geometry: the ophanim (wheels within wheels full of eyes,
 * ringed with fire), the seraphim (six vast burning wings and two haloes), the Watcher (one colossal dead
 * eye with too many pupils, hung on nerves, among a swarm of lesser eyes), and the pillar (a shaft of light
 * into the sky ringed by tilting wheels of eyes). Each
 * frame is particles plus "sprites": display entities, either plain blocks everyone sees or flat
 * textured pieces from the god's resource pack (eyes, feathers). Viewers without the pack see
 * a sprite's vanilla fallback item, or the frame's fallback particles that draw the same eyes and
 * feathers. Everything is in blocks around the form's centre (y up), shared with the offline previews.
 */
public final class AngelForms {
    private static final double TAU = Math.PI * 2;
    /** At most this many fallback particles (drawn only for viewers without the pack). */
    static final int FALLBACK_BUDGET = 200;

    /**
     * One display entity of a form. Exactly one of {@code key} (a textured flat piece, model
     * gptgodmc:trueform/&lt;key&gt;: a square in the local x-y plane facing +z) or {@code block} (a
     * Material name: a box centred on the position) is set. {@code fallback} is a vanilla item (Material
     * name) shown instead of a textured piece to viewers without the pack, at {@code fallbackScale} times
     * the size; null = those viewers see the fallback particles instead. Billboarded pieces always face
     * the viewer (their rotation is ignored). A block may name a {@code box}: the pack's textured box
     * (gptgodmc:trueform/&lt;box&gt;) that viewers with the pack see in place of the vanilla block.
     */
    public record Sprite(String key, String fallback, double fallbackScale, String block, String box,
            boolean billboard, double x, double y, double z, double qx, double qy, double qz, double qw, double sx, double sy,
            double sz) {
        public boolean textured() {
            return key != null;
        }

        public Quaterniond rotation() {
            return new Quaterniond(qx, qy, qz, qw);
        }
    }

    /**
     * One frame: particles everyone sees, particles only viewers without the pack see (they stand in for
     * the textured pieces), and the sprites (always the same number per form, in the same order, so each
     * keeps its entity from frame to frame).
     */
    public record Frame(List<Mote> motes, List<Mote> fallback, List<Sprite> sprites) {
    }

    private AngelForms() {
    }

    /**
     * One frame of a form.
     *
     * @param tick     game ticks since it began
     * @param unfold   0 = folded into a point, 1 = fully unfolded
     * @param eyeOpen  0 = its eyes are shut, 1 = wide open
     * @param eyeYaw   where it looks, round the vertical: atan2(dx, dz) of the direction (radians)
     * @param eyePitch how far it looks down (negative = up), radians
     */
    public static Frame frame(Form form, int tick, double unfold, double eyeOpen, double eyeYaw, double eyePitch) {
        return frame(form, tick, unfold, eyeOpen, eyeYaw, eyePitch, 1);
    }

    /**
     * As above, with {@code focus}: 0 = it is searching (the great eye's pupil gapes, its other pupils wander),
     * 1 = it has fixed on a mortal.
     */
    public static Frame frame(Form form, int tick, double unfold, double eyeOpen, double eyeYaw, double eyePitch,
            double focus) {
        if (form == null || form == Form.HYPERCUBE) {
            return new Frame(TrueForm.frame(tick, unfold, eyeOpen, eyeYaw, eyePitch), List.of(), List.of());
        }
        double u = clamp01(unfold);
        double open = clamp01(eyeOpen);
        double t = tick;
        Out o = new Out();
        Quaterniond face = new Quaterniond();
        switch (form) {
            case OPHANIM -> ophanim(o, t, open, eyeYaw, eyePitch);
            case SERAPHIM -> {
                seraphim(o, t, open);
                face.rotateY(eyeYaw);
            }
            case EYE -> eye(o, t, open, eyeYaw, eyePitch, clamp01(focus));
            case PILLAR -> pillar(o, t, open);
            default -> {
            }
        }
        double s = Shapes.ease(u);
        // Unfolding, it also swings round out of the point it grows from.
        face.premul(new Quaterniond().rotateY((1 - s) * Math.PI));
        return finish(o, s, SCALE.getOrDefault(form, 1.0), face);
    }

    /** How much larger than drawn each form stands, so all of them tower alike. */
    private static final java.util.Map<Form, Double> SCALE = java.util.Map.of(Form.OPHANIM, 1.5);

    /** How many sprites a form always has. */
    public static int spriteCount(Form form) {
        return frame(form, 0, 1, 0, 0, 0).sprites().size();
    }

    // ---- Ophanim: wheels within wheels ------------------------------------------------------------

    private static void ophanim(Out o, double t, double open, double yaw, double pitch) {
        double[] radius = { 5.0, 5.0, 3.5 };
        String[] mat = { "GOLD_BLOCK", "GOLD_BLOCK", "SHROOMLIGHT" };
        int segs = 18, eyes = 8;
        for (int k = 0; k < 3; k++) {
            Quaterniond wheel = switch (k) {
                case 0 -> new Quaterniond().rotateY(t * 0.004).rotateX(Math.PI / 2);
                case 1 -> new Quaterniond().rotateY(t * 0.004 + Math.PI / 2).rotateX(Math.PI / 2);
                default -> new Quaterniond().rotateY(-t * 0.009).rotateZ(0.5 + 0.15 * Math.sin(t * 0.01));
            };
            double dir = k == 1 ? -1 : 1;
            double phase = dir * t * (0.02 + 0.01 * k);
            double r = radius[k];
            double segLen = TAU * r / segs * 1.06;
            for (int i = 0; i < segs; i++) {
                double a = phase + i * TAU / segs;
                Vector3d at = wheel.transform(new Vector3d(Math.cos(a) * r, 0, Math.sin(a) * r));
                Quaterniond rot = new Quaterniond(wheel).rotateY(-a - Math.PI / 2);
                o.block(mat[k], at, rot, segLen, 0.3, 0.42);
                if (i % 2 == 0) {
                    // Fire runs along the rims.
                    double fr = r + 0.5 + 0.15 * Math.sin(t * 0.3 + i);
                    o.mote(wheel.transform(new Vector3d(Math.cos(a) * fr, 0, Math.sin(a) * fr)),
                            k == 2 ? 0xFFB050 : 0xFF7A20, 1.2f, Glyph.FLAME);
                }
            }
            for (int j = 0; j < eyes; j++) {
                // Set into the flat of the wheel, on one face then the other, lying along the rim.
                double a = phase + (j + 0.5) * TAU / eyes;
                double side = j % 2 == 0 ? 1 : -1;
                Vector3d at = wheel.transform(new Vector3d(Math.cos(a) * r, side * 0.17, Math.sin(a) * r));
                Quaterniond rot = new Quaterniond(wheel).rotateY(-a - Math.PI / 2).rotateX(-side * Math.PI / 2);
                eyeAt(o, at, rot, 1.5, open * blink(t, k * eyes + j), 0xFFC850);
            }
        }
        // A ring of fire round it all, rising and falling like breath.
        int flames = 30;
        for (int i = 0; i < flames; i++) {
            double a = t * 0.012 + i * TAU / flames;
            double fr = 6.6 + 0.3 * Math.sin(t * 0.08 + i * 0.9);
            o.mote(new Vector3d(Math.cos(a) * fr, 0.6 * Math.sin(a * 3 + t * 0.05), Math.sin(a) * fr), 0xFF6A1A,
                    1.3f, Glyph.FLAME);
        }
        // The spirit in the wheels: a heart of amber light, and one eye in it that turns to the mortals.
        for (int i = 0; i < 14; i++) {
            double a = t * 0.05 + i * 2.399;
            double y = -1 + 2 * ((i + 0.5) / 14.0);
            double rr = Math.sqrt(1 - y * y) * 0.9;
            o.mote(new Vector3d(Math.cos(a) * rr, y * 0.9, Math.sin(a) * rr), i % 4 == 0 ? 0xFFFFFF : 0xFFC860,
                    1.1f, i % 4 == 0 ? Glyph.GLOW : Glyph.DUST);
        }
        // Closest to the heart, two small wheels crossed and spinning fast: a wheel in the middle of a wheel.
        ring(o, "GOLD_BLOCK", new Vector3d(), new Quaterniond().rotateY(t * 0.05).rotateX(Math.PI / 2), 1.9, 10,
                t * 0.09, 0.16, 0.22);
        ring(o, "GOLD_BLOCK", new Vector3d(), new Quaterniond().rotateY(t * 0.05).rotateZ(Math.PI / 2 + t * 0.03), 1.9,
                10, -t * 0.09, 0.16, 0.22);
        eyeAt(o, new Vector3d(), facing(look(yaw, pitch)), 2.3, open, 0xFFE08A);
        lightning(o, t, new Vector3d(), 5.0);
        lightning(o, t + 7, new Vector3d(), 6.5);
        o.glow("glow", new Vector3d(), 6.5 + 0.6 * Math.sin(t * 0.1));
        o.glow("glow", new Vector3d(), 17);
    }

    // ---- Seraphim: six burning wings --------------------------------------------------------------

    private static void seraphim(Out o, double t, double open) {
        double beat = Math.sin(t * 0.11);
        double veil = Shapes.ease(open);
        // Its body is not a body: a seam of white fire down the middle of the wings.
        o.block("PEARLESCENT_FROGLIGHT", new Vector3d(0, -0.5, -0.1), new Quaterniond().rotateY(t * 0.03), 0.3, 9.5, 0.3);
        for (int i = 0; i < 12; i++) {
            double y = -5 + i * 0.85;
            double a = t * 0.21 + i * 1.7;
            o.mote(new Vector3d(Math.cos(a) * 0.55, y, Math.sin(a) * 0.55), i % 3 == 0 ? 0xFFFFFF : 0xFFC860, 1.2f,
                    i % 3 == 0 ? Glyph.GLOW : Glyph.FLAME);
        }
        // Behind its head, two haloes of gold: one still, one slowly tumbling.
        ring(o, "GOLD_BLOCK", new Vector3d(0, 3.3, -0.9), new Quaterniond().rotateX(Math.PI / 2), 2.7, 16, t * 0.01,
                0.18, 0.22);
        ring(o, "GOLD_BLOCK", new Vector3d(0, 3.3, -0.9),
                new Quaterniond().rotateY(t * 0.013).rotateX(Math.PI / 2 + 0.5 * Math.sin(t * 0.009)), 4.3, 20,
                -t * 0.016, 0.16, 0.2);
        // Its face: one eye, veiled by the upper wings until it opens.
        eyeAt(o, new Vector3d(0, 3.2, 0.45), new Quaterniond(), 2.3, open, 0xFFE08A);
        for (int side = -1; side <= 1; side += 2) {
            double s = side;
            // Two to fly with: vast, beating slowly.
            Vector3d[] fly = { new Vector3d(s * 0.6, 1.0, -0.3), new Vector3d(s * 5.0, 7.0 + 1.2 * beat, -0.9),
                    new Vector3d(s * 10.5, 4.0 + 2.6 * beat, -0.5) };
            wing(o, t, fly, new Vector3d(s * 0.35, -1, -0.05), new Vector3d(s * 1.0, -0.2, -0.1), 13, 6.4, 2.1,
                    1000 + side * 10);
            wing(o, t, new Vector3d[] { new Vector3d(fly[0]).add(0, 0, 0.12), new Vector3d(fly[1]).add(0, 0, 0.12),
                    new Vector3d(fly[2]).add(0, 0, 0.12) }, new Vector3d(s * 0.3, -1, 0), new Vector3d(s * 0.9, -0.45, 0),
                    8, 2.8, 1.7, 1500 + side * 10);
            // Eyes open along the great wings.
            eyeAt(o, bezier(fly, 0.45).add(0, -0.5, 0.25), new Quaterniond(), 1.3, open * blink(t, 10 + side), 0xFFC850);
            eyeAt(o, bezier(fly, 0.8).add(0, -0.6, 0.25), new Quaterniond(), 1.0, open * blink(t, 20 + side), 0xFFC850);
            // Two to cover its face: folded across it while the eye is shut, lifted high like a crown when it opens.
            Vector3d[] shut = { new Vector3d(s * 0.6, 3.6, 0.3), new Vector3d(s * 2.4, 5.6, 1.3),
                    new Vector3d(-s * 0.9, 4.6, 1.5) };
            Vector3d[] up = { new Vector3d(s * 0.5, 3.0, -0.2), new Vector3d(s * 2.6, 7.2, -0.5),
                    new Vector3d(s * 1.3, 11.0, -0.3) };
            Vector3d[] face = { shut[0].lerp(up[0], veil, new Vector3d()), shut[1].lerp(up[1], veil, new Vector3d()),
                    shut[2].lerp(up[2], veil, new Vector3d()) };
            Vector3d hang = new Vector3d(-s * 0.1, -1, 0.05).lerp(new Vector3d(s * 0.55, -0.85, 0), veil);
            Vector3d sweep = new Vector3d(-s * 0.25, -1, 0.05).lerp(new Vector3d(s * 0.8, 0.6, 0), veil);
            wing(o, t, face, hang, sweep, 9, 4.2, 1.7, 2000 + side * 10);
            // Two to cover its feet.
            double sway = 0.25 * Math.sin(t * 0.05 + side);
            Vector3d[] feet = { new Vector3d(s * 0.5, -0.8, -0.1), new Vector3d(s * 2.4 + sway, -2.2, 1.0),
                    new Vector3d(-s * 0.5, -4.0, 1.4) };
            wing(o, t, feet, new Vector3d(s * 0.25, -1, 0.05), new Vector3d(-s * 0.35, -1, 0.1), 9, 5.0, 1.7,
                    3000 + side * 10);
        }
        // Embers shed from its wings, falling.
        for (int i = 0; i < 26; i++) {
            double x = (hash(i * 7 + 1) - 0.5) * 20;
            double fall = ((t * 0.06 + hash(i * 13 + 5) * 7) % 7);
            o.mote(new Vector3d(x, 3.5 - fall - Math.abs(x) * 0.15, (hash(i * 31 + 2) - 0.5) * 1.5), 0xFFB040, 0.9f,
                    i % 5 == 0 ? Glyph.GLOW : Glyph.FLAME);
        }
        lightning(o, t, new Vector3d(0, 3, 0), 6.0);
        o.glow("glow", new Vector3d(0, 3.3, -1.0), 9 + 0.6 * Math.sin(t * 0.08));
        o.glow("glow", new Vector3d(0, 0.5, -1.2), 24);
    }

    /** A point on the curve from p[0] past p[1] to p[2]. */
    private static Vector3d bezier(Vector3d[] p, double u) {
        double a = (1 - u) * (1 - u), b = 2 * u * (1 - u), c = u * u;
        return new Vector3d(p[0].x * a + p[1].x * b + p[2].x * c, p[0].y * a + p[1].y * b + p[2].y * c,
                p[0].z * a + p[1].z * b + p[2].z * c);
    }

    /**
     * A wing: an arm curving from shoulder past wrist to tip, with feathers set along it that hang one way
     * near the shoulder and sweep another toward the tip, longest past the wrist.
     */
    private static void wing(Out o, double t, Vector3d[] arm, Vector3d hang, Vector3d sweep, int feathers, double length,
            double width, int seed) {
        for (int j = 0; j < feathers; j++) {
            double u = 0.12 + 0.88 * j / (feathers - 1.0);
            Vector3d at = bezier(arm, u);
            Vector3d dir = new Vector3d(hang).lerp(sweep, Math.pow(u, 1.6));
            double len = length * (0.5 + 0.5 * Math.sin(Math.PI * Math.pow(u, 0.75) * 0.82 + 0.25));
            feather(o, t, at, dir, len, width * (0.8 + 0.3 * u), seed + j);
        }
        // The arm of the wing burns.
        for (int i = 0; i <= 6; i++) {
            Vector3d p = bezier(arm, i / 6.0);
            o.mote(p, 0xFFF0C0, 1.1f, i % 2 == 0 ? Glyph.GLOW : Glyph.FLAME);
        }
    }

    /** A ring of thin block segments round a centre, in the plane {@code rot} turns the ground into. */
    private static void ring(Out o, String material, Vector3d centre, Quaterniond rot, double r, int segs, double phase,
            double thick, double deep) {
        double segLen = TAU * r / segs * 1.07;
        for (int i = 0; i < segs; i++) {
            double a = phase + i * TAU / segs;
            Vector3d at = rot.transform(new Vector3d(Math.cos(a) * r, 0, Math.sin(a) * r)).add(centre);
            o.block(material, at, new Quaterniond(rot).rotateY(-a - Math.PI / 2), segLen, thick, deep);
        }
    }

    /** One burning feather from a root along a direction: a textured quill, or a line of fire without the pack. */
    private static void feather(Out o, double t, Vector3d root, Vector3d dir, double length, double width, int seed) {
        Vector3d d = new Vector3d(dir).normalize();
        Vector3d mid = new Vector3d(d).mul(length / 2).add(root);
        o.item("feather", null, 1, false, mid, along(d, new Vector3d(0, 0, 1)), width, length, 1);
        int n = 4;
        for (int i = 0; i < n; i++) {
            double f = (i + 1) / (double) n;
            Vector3d p = new Vector3d(d).mul(length * f).add(root);
            int rgb = f < 0.5 ? TrueForm.mix(0xFFFFFF, 0xFFE9B0, f * 2) : TrueForm.mix(0xFFE9B0, 0xFF8A2A, f * 2 - 1);
            o.fallback(p, rgb, 1.2f, i == 1 ? Glyph.GLOW : Glyph.DUST);
        }
        // Their tips burn, for everyone.
        if (Math.floorMod(seed + (int) (t / 2), 4) == 0) o.mote(new Vector3d(d).mul(length * 1.02).add(root), 0xFF8A2A,
                1f, Glyph.FLAME);
    }

    // ---- The Watcher: one colossal eye --------------------------------------------------------------

    private static void eye(Out o, double t, double open, double yaw, double pitch, double focus) {
        double big = 5.4;
        Vector3d d = look(yaw, pitch);
        Quaterniond toward = facing(d);
        double wide = Shapes.ease((open - 0.35) / 0.65);
        double shown = Math.max(0.001, wide);
        // Nothing about it is an animal's eye. A ball of void lit at its limb like an eclipse, which turns its
        // face to every viewer; its iris sits on it toward the mortal it has fixed on, so that one meets it
        // dead-on and everyone else sees it looking past them.
        double ball = big * 2 / TrueFormTextures.BALL_FILL;
        o.item(open >= 0.5 ? "sclera" : "lid", null, 1, true, new Vector3d(), new Quaterniond(), ball,
                ball * Shapes.lerp(0.55, 1, wide), 1);
        // The iris is three wheels of teeth turning against each other round a heart of cold fire.
        double ir = big * 1.05;
        double[] sizes = { 1.0, 0.74, 0.52 };
        double[] speeds = { 0.011, -0.019, 0.031 };
        for (int k = 0; k < 3; k++) {
            Quaterniond rot = new Quaterniond(toward).rotateZ(t * speeds[k] + k);
            double size = ir * sizes[k] * shown;
            o.item("iris_ring", null, 1, false, new Vector3d(d).mul(big * (0.9 + 0.012 * k)), rot, size, size, 1);
        }
        double core = ir * 0.38 * shown;
        o.item("iris", null, 1, false, new Vector3d(d).mul(big * 0.94), toward, core, core, 1);
        // Its pupil is a bar. It gapes while it searches and shuts to a line when it has found someone.
        double bar = ir * Shapes.lerp(0.5, 0.36, focus) * shown;
        o.item("pupil", null, 1, false, new Vector3d(d).mul(big * 0.96), toward, bar, bar * Shapes.lerp(0.42, 0.1, focus),
                1);
        // It has more pupils than it should, at angles to each other. They wander the iris, and slide into the
        // first when it finds you.
        for (int k = 0; k < 3; k++) {
            double a = t * (0.021 + 0.007 * k) + k * 2.2;
            double away = ir * 0.33 * (1 - focus) * (0.8 + 0.2 * Math.sin(t * 0.05 + k));
            Vector3d at = toward.transform(new Vector3d(Math.cos(a) * away, Math.sin(a) * away, 0))
                    .add(new Vector3d(d).mul(big * (0.975 + 0.008 * k)));
            double size = ir * (0.2 - 0.03 * k) * shown * (0.2 + 0.8 * (1 - focus));
            Quaterniond rot = new Quaterniond(toward).rotateZ((k + 1) * 1.05 * (1 - focus) + t * 0.01 * (1 - focus));
            o.item("pupil", null, 1, false, at, rot, size, size * 0.36, 1);
        }
        greatEyeMotes(o.fallback, new Vector3d(d).mul(big * 0.5), toward, big, open, focus);
        // Filaments trail from it toward the ground, feeling for it.
        for (int k = 0; k < 9; k++) {
            double a = k * TAU / 9 + 0.3;
            double r0 = big * 0.55;
            for (int i = 1; i <= 7; i++) {
                double f = i / 7.0;
                double wob = (0.4 + 1.3 * f) * Math.sin(t * 0.045 + k * 1.9 + f * 3.5);
                double r = r0 * (1 - 0.45 * f) + wob * 0.6;
                o.mote(new Vector3d(Math.cos(a) * r + wob * 0.4, -big * 0.8 - f * 8.0, Math.sin(a) * r),
                        TrueForm.mix(0xE0D0FF, 0x5A2A9A, f), 1.4f - (float) f * 0.5f, i == 7 ? Glyph.GLOW : Glyph.DUST);
            }
        }
        // Lesser eyes hang in the air round it, no two alike, opening one after another; all look where it looks.
        int swarm = 18;
        for (int i = 0; i < swarm; i++) {
            double a = hash(i * 17 + 3) * TAU + 0.15 * Math.sin(t * 0.013 + i);
            double e = (hash(i * 29 + 7) - 0.35) * 1.9;
            double r = big + 2.6 + 4.2 * hash(i * 41 + 11) + 0.4 * Math.sin(t * 0.02 + i * 2.1);
            Vector3d at = new Vector3d(Math.cos(a) * Math.cos(e) * r, Math.sin(e) * r * 0.8, Math.sin(a) * Math.cos(e) * r);
            double size = (0.9 + 1.6 * hash(i * 53 + 13)) / TrueFormTextures.EYE_FILL;
            double mine = clamp01(open * 2.2 - i / (double) swarm) * blink(t, 60 + i);
            Quaterniond rot = new Quaterniond(toward).rotateZ((hash(i * 61 + 5) - 0.5) * 1.2);
            o.item(mine >= 0.5 ? "eye_void" : "eye_void_shut", null, 1, false, at, rot, size, size, 1);
            if (i % 2 == 0) eyeMotes(o.fallback, new Vector3d(at), rot, size * 0.35, mine, 0xC8A8FF, 5);
        }
        o.item("glow_cold", null, 1, false, new Vector3d(d).mul(-1.6), toward, 23, 23, 1);
    }

    /** The Watcher in particles, for viewers without the pack: a limb of light, ringed irises and a bar pupil. */
    private static void greatEyeMotes(List<Mote> out, Vector3d at, Quaterniondc rot, double half, double open,
            double focus) {
        double op = clamp01(open);
        int limb = 40;
        for (int i = 0; i < limb; i++) {
            double a = i * TAU / limb;
            add(out, at, rot, Math.cos(a) * half, Math.sin(a) * half * Math.max(0.08, op), 0xE8DCFF, 1.2f,
                    i % 5 == 0 ? Glyph.GLOW : Glyph.DUST);
        }
        if (op <= 0.3) {
            for (int i = -4; i <= 4; i++) add(out, at, rot, i * half * 0.2, 0, 0xFFFFFF, 1.1f, Glyph.GLOW);
            return;
        }
        double r = half * 0.5;
        for (int i = 0; i < 20; i++) {
            double a = i * TAU / 20;
            add(out, at, rot, Math.cos(a) * r, Math.sin(a) * r, 0xB070FF, 1.2f, Glyph.DUST);
            if (i % 2 == 0) add(out, at, rot, Math.cos(a) * r * 0.66, Math.sin(a) * r * 0.66, 0xF4F0FF, 1.1f, Glyph.DUST);
        }
        // The bar of its pupil, shutting to a point of light as it finds someone.
        double bar = r * Shapes.lerp(0.5, 0.2, focus);
        for (int i = -3; i <= 3; i++) add(out, at, rot, i * bar / 3, 0, 0xFFFFFF, 1.3f, Glyph.GLOW);
    }

    // ---- The pillar of eyes -----------------------------------------------------------------------

    private static void pillar(Out o, double t, double open) {
        // A shaft of light from the ground into the sky.
        o.block("PEARLESCENT_FROGLIGHT", new Vector3d(0, 17, 0), new Quaterniond().rotateY(t * 0.01), 1.2, 60, 1.2);
        for (int i = 0; i < 30; i++) {
            double y = -12 + ((t * 0.35 + i * 1.6) % 46);
            double a = t * 0.05 + i * 2.1;
            o.mote(new Vector3d(Math.cos(a) * 1.1, y, Math.sin(a) * 1.1), 0xFFFFFF, 1.1f, Glyph.GLOW);
        }
        // Round it, wheels of gold tilting against each other like a great gyroscope, full of eyes looking out.
        double[] height = { -6.5, -2.5, 1.5, 5.5, 9.5 };
        double[] radius = { 3.4, 5.2, 6.6, 5.2, 3.4 };
        int[] eyes = { 7, 9, 11, 9, 7 };
        int n = 0;
        for (int k = 0; k < height.length; k++) {
            double dir = k % 2 == 0 ? 1 : -1;
            double r = radius[k];
            Quaterniond tilt = new Quaterniond().rotateY(dir * t * 0.006 + k * 1.3)
                    .rotateZ(0.22 + 0.08 * Math.sin(t * 0.01 + k));
            Vector3d c = new Vector3d(0, height[k] + 0.25 * Math.sin(t * 0.04 + k), 0);
            double phase = dir * t * (0.014 + 0.004 * k);
            ring(o, "GOLD_BLOCK", c, tilt, r, 14, phase, 0.5, 0.3);
            for (int j = 0; j < eyes[k]; j++) {
                double a = phase + (j + 0.5) * TAU / eyes[k];
                Vector3d at = tilt.transform(new Vector3d(Math.cos(a) * (r + 0.2), 0, Math.sin(a) * (r + 0.2))).add(c);
                Quaterniond rot = new Quaterniond(tilt).rotateY(Math.PI / 2 - a);
                eyeAt(o, at, rot, 1.7, open * blink(t, n++), 0xFFC850);
            }
            for (int i = 0; i < 8; i++) {
                double a = -phase * 2 + i * TAU / 8;
                o.mote(tilt.transform(new Vector3d(Math.cos(a) * (r + 0.7), 0, Math.sin(a) * (r + 0.7))).add(c), 0xFF7A20,
                        1.1f, Glyph.FLAME);
            }
        }
        // The crown: greater eyes looking out and down upon the world, rays of light between them.
        int crown = 8;
        double cy = 13.5;
        for (int i = 0; i < crown; i++) {
            double a = -t * 0.008 + i * TAU / crown;
            Vector3d out = new Vector3d(Math.cos(a) * Math.cos(0.45), -Math.sin(0.45), Math.sin(a) * Math.cos(0.45));
            eyeAt(o, new Vector3d(Math.cos(a) * 4.4, cy, Math.sin(a) * 4.4), facing(out), 2.6, open * blink(t, 80 + i),
                    0xFFE08A);
            double b = a + TAU / crown / 2;
            for (int j = 1; j <= 5; j++) {
                double f = j / 5.0;
                double rr = 3.0 + f * 4.0 * (0.8 + 0.2 * Math.sin(t * 0.09 + i));
                o.mote(new Vector3d(Math.cos(b) * rr, cy + f * 4.5, Math.sin(b) * rr), j == 5 ? 0xFFFFFF : 0xFFE08A, 1.1f,
                        j >= 4 ? Glyph.GLOW : Glyph.DUST);
            }
        }
        // Where the shaft meets the ground, the earth burns in a widening and closing ring.
        for (int i = 0; i < 20; i++) {
            double a = t * 0.02 + i * TAU / 20;
            double rr = 2.2 + 1.2 * Math.sin(t * 0.06);
            o.mote(new Vector3d(Math.cos(a) * rr, -12.6, Math.sin(a) * rr), 0x5FE6FF, 1.1f,
                    i % 2 == 0 ? Glyph.SOUL : Glyph.FLAME);
        }
        lightning(o, t, new Vector3d(0, cy, 0), 7.0);
        o.glow("glow", new Vector3d(0, cy, 0), 15 + Math.sin(t * 0.07));
        o.glow("glow", new Vector3d(0, 1.5, 0), 22);
        o.glow("glow", new Vector3d(0, -12.5, 0), 9);
    }

    // ---- Pieces -----------------------------------------------------------------------------------

    /** An eye: a textured piece for the pack, a small particle eye for everyone else. */
    private static void eyeAt(Out o, Vector3d at, Quaterniondc rot, double size, double open, int iris) {
        // The texture keeps a margin for the eye's glow, so the piece is that much larger than the eye.
        double piece = size / TrueFormTextures.EYE_FILL;
        o.item(open >= 0.5 ? "eye_open" : "eye_shut", null, 1, false, at, rot, piece, piece, 1);
        Vector3d front = rot.transform(new Vector3d(0, 0, 0.04)).add(at);
        eyeMotes(o.fallback, front, rot, size * 0.5, open, iris, 5);
    }

    /**
     * A particle eye in the local x-y plane looking along +z, turned by {@code rot}: two lids (a seam of
     * light while shut), an iris and a slit pupil.
     */
    static void eyeMotes(List<Mote> out, Vector3d at, Quaterniondc rot, double half, double open, int iris,
            int perLid) {
        double o = clamp01(open);
        double h = Math.max(0.05 * half, 0.6 * half * o);
        int lid = o > 0.3 ? 0xFFF3DA : 0xFFC860;
        for (int i = 0; i < perLid; i++) {
            double u = perLid == 1 ? 0 : -1 + 2.0 * i / (perLid - 1);
            double y = h * (1 - u * u);
            boolean corner = i == 0 || i == perLid - 1;
            add(out, at, rot, u * half, y, lid, 1f, corner ? Glyph.GLOW : Glyph.DUST);
            if (!corner) add(out, at, rot, u * half, -y, lid, 1f, Glyph.DUST);
        }
        if (o > 0.3) {
            double r = Math.min(h * 0.8, half * 0.45);
            int n = Math.max(5, perLid + 1);
            for (int i = 0; i < n; i++) {
                double a = i * TAU / n;
                add(out, at, rot, Math.cos(a) * r, Math.sin(a) * r, iris, 1.1f, Glyph.DUST);
            }
            if (perLid >= 15) {
                // A great eye: a filled iris of fire and a long black slit.
                for (double k : new double[] { 0.45, 0.72 }) {
                    int m = (int) Math.round(n * k);
                    for (int i = 0; i < m; i++) {
                        double a = i * TAU / m + k;
                        add(out, at, rot, Math.cos(a) * r * k, Math.sin(a) * r * k, k < 0.6 ? 0xFF4A10 : 0xFFA020,
                                1.3f, Glyph.DUST);
                    }
                }
                for (int i = -4; i <= 4; i++) add(out, at, rot, 0, i * r * 0.2, 0x0A0004, 1.6f, Glyph.DUST);
            } else {
                for (int i = -1; i <= 1; i++) add(out, at, rot, 0, i * r * 0.6, 0x14000A, 1.25f, Glyph.DUST);
            }
        } else {
            add(out, at, rot, 0, 0, 0xFFFFFF, 1f, Glyph.GLOW);
        }
    }

    /** Now and then a jagged bolt leaps out from the heart. */
    private static void lightning(Out o, double t, Vector3d from, double reach) {
        int beat = (int) (t / 14);
        if ((t % 14) > 5) return;
        double a = hash(beat * 3) * TAU, e = (hash(beat * 5 + 1) - 0.5) * 1.6;
        Vector3d dir = new Vector3d(Math.cos(a) * Math.cos(e), Math.sin(e), Math.sin(a) * Math.cos(e));
        Vector3d p = new Vector3d(from);
        int steps = 8;
        for (int i = 0; i < steps; i++) {
            p.add(new Vector3d(dir).mul(reach / steps)).add((hash(beat * 7 + i) - 0.5) * 0.6,
                    (hash(beat * 11 + i) - 0.5) * 0.6, (hash(beat * 13 + i) - 0.5) * 0.6);
            o.mote(new Vector3d(p), 0xD8F0FF, 1f, Glyph.SPARK);
        }
    }

    /**
     * How open one of many lesser eyes is (0..1): each blinks on its own beat, shut for a few ticks.
     */
    static double blink(double t, int i) {
        int period = 70 + Math.floorMod(i * 37, 50);
        double phase = (t + i * 23) % period;
        if (phase >= 6) return 1;
        return Math.abs(phase - 3) / 3.0;
    }

    // ---- Maths ------------------------------------------------------------------------------------

    /** The direction an eye with this yaw and pitch looks (as in TrueFormShow's aim). */
    static Vector3d look(double yaw, double pitch) {
        double cp = Math.cos(pitch);
        return new Vector3d(cp * Math.sin(yaw), -Math.sin(pitch), cp * Math.cos(yaw));
    }

    /** A rotation that turns local +z to {@code dir}, keeping local y as near straight up as it can. */
    static Quaterniond facing(Vector3d dir) {
        Vector3d z = new Vector3d(dir).normalize();
        Vector3d up = Math.abs(z.y) > 0.97 ? new Vector3d(0, 0, 1) : new Vector3d(0, 1, 0);
        Vector3d x = up.cross(z, new Vector3d()).normalize();
        Vector3d y = z.cross(x, new Vector3d()).normalize();
        return axes(x, y, z);
    }

    /** A rotation that turns local +y to {@code dir} and local +z as near {@code zHint} as it can. */
    static Quaterniond along(Vector3d dir, Vector3d zHint) {
        Vector3d y = new Vector3d(dir).normalize();
        Vector3d z = new Vector3d(zHint).sub(new Vector3d(y).mul(y.dot(zHint)));
        if (z.lengthSquared() < 1e-8) z = Math.abs(y.x) < 0.9 ? new Vector3d(1, 0, 0) : new Vector3d(0, 1, 0);
        z.sub(new Vector3d(y).mul(y.dot(z))).normalize();
        Vector3d x = y.cross(z, new Vector3d()).normalize();
        return axes(x, y, z);
    }

    private static Quaterniond axes(Vector3d x, Vector3d y, Vector3d z) {
        Matrix3d m = new Matrix3d(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z);
        return new Quaterniond().setFromNormalized(m).normalize();
    }

    private static void add(List<Mote> out, Vector3d at, Quaterniondc rot, double x, double y, int rgb, float size,
            Glyph g) {
        Vector3d p = rot.transform(new Vector3d(x, y, 0)).add(at);
        out.add(new Mote(p.x, p.y, p.z, rgb, size, g));
    }

    /** Grows everything out of the centre by {@code s} and turns it all by {@code face}. */
    private static Frame finish(Out o, double s, double scale, Quaterniond face) {
        double k = Math.max(0.001, s) * scale;
        List<Mote> motes = new ArrayList<>(o.motes.size());
        if (s <= 0.001) {
            motes.add(new Mote(0, 0, 0, 0xFFFFFF, 1.4f, Glyph.GLOW));
        } else {
            for (Mote m : o.motes) motes.add(moved(m, k, face));
        }
        List<Mote> fallback = new ArrayList<>(o.fallback.size());
        if (s > 0.001) for (Mote m : o.fallback) fallback.add(moved(m, k, face));
        fallback = TrueForm.fit(fallback, FALLBACK_BUDGET);
        motes = TrueForm.fit(motes, TrueForm.BUDGET - fallback.size());
        List<Sprite> sprites = new ArrayList<>(o.sprites.size());
        for (Sprite sp : o.sprites) {
            Vector3d p = face.transform(new Vector3d(sp.x(), sp.y(), sp.z()).mul(k));
            Quaterniond r = sp.billboard() ? new Quaterniond() : new Quaterniond(face).mul(sp.rotation()).normalize();
            sprites.add(new Sprite(sp.key(), sp.fallback(), sp.fallbackScale(), sp.block(), sp.box(), sp.billboard(), p.x, p.y,
                    p.z, r.x, r.y, r.z, r.w, sp.sx() * k, sp.sy() * k, sp.sz() * k));
        }
        return new Frame(motes, fallback, sprites);
    }

    private static Mote moved(Mote m, double k, Quaterniond face) {
        Vector3d p = face.transform(new Vector3d(m.x(), m.y(), m.z()).mul(k));
        return new Mote(p.x, p.y, p.z, m.rgb(), m.size(), m.glyph());
    }

    /** A steady pseudo-random number in [0, 1) for an integer. */
    static double hash(int n) {
        int h = n * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h >>> 8) / (double) (1 << 24);
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
    }

    /** What one frame is being built into. */
    private static final class Out {
        final List<Mote> motes = new ArrayList<>(360);
        final List<Mote> fallback = new ArrayList<>(240);
        final List<Sprite> sprites = new ArrayList<>(96);

        void mote(Vector3d p, int rgb, float size, Glyph g) {
            motes.add(new Mote(p.x, p.y, p.z, rgb, size, g));
        }

        void fallback(Vector3d p, int rgb, float size, Glyph g) {
            fallback.add(new Mote(p.x, p.y, p.z, rgb, size, g));
        }

        void block(String material, Vector3d at, Quaterniondc rot, double sx, double sy, double sz) {
            String box = switch (material) {
                case "GOLD_BLOCK" -> "gold";
                case "SHROOMLIGHT" -> "ember";
                case "PEARLESCENT_FROGLIGHT" -> "light";
                default -> null;
            };
            sprites.add(new Sprite(null, null, 1, material, box, false, at.x, at.y, at.z, rot.x(), rot.y(), rot.z(),
                    rot.w(), sx, sy, sz));
        }

        void item(String key, String fallback, double fallbackScale, boolean billboard, Vector3d at, Quaterniondc rot,
                double sx, double sy, double sz) {
            sprites.add(new Sprite(key, fallback, fallbackScale, null, null, billboard, at.x, at.y, at.z, rot.x(),
                    rot.y(), rot.z(), rot.w(), sx, sy, sz));
        }

        /** A soft halo of light with no edge, always turned to the viewer. */
        void glow(String key, Vector3d at, double size) {
            item(key, null, 1, true, at, new Quaterniond(), size, size, 1);
        }
    }
}
