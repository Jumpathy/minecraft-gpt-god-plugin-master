package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The shape of a demigod's wings and halo, built from a few display-entity boxes: per wing a golden
 * arm, three gilded coverts and six white flight feathers, and a golden ring above the head.
 * Pure math, so tests can render it without a server; {@link DemigodRig} puts it in the world.
 *
 * Body space, in blocks: origin between the player's feet, +y up, the player faces +z, so their left
 * side is +x (the same as the vessel's rig).
 */
public final class DemigodWings {
    public enum Kind {
        ARM, COVERT, PRIMARY, HALO
    }

    /** A box: center, size and rotation in body space (blocks), and its block. */
    public record Piece(Kind kind, Material material, Vector3f center, Vector3f size, Quaternionf rot) {
    }

    /**
     * How the wings are held. open: 0 folded down the back, 1 spread. raise: the arm's angle above the
     * horizontal (radians). sweep: how far the wings are swept back (radians). lean: the whole body
     * tipped forward around the feet (radians; gliding, swimming, sneaking). drop: lowers everything
     * (blocks; sneaking).
     */
    public record Pose(float open, float raise, float sweep, float lean, float drop) {
    }

    public static final int COVERTS = 3, PRIMARIES = 6, HALO_SEGMENTS = 8;
    /** Boxes in a pair of wings. */
    public static final int WING_PIECES = 2 * (1 + COVERTS + PRIMARIES);
    /** Every entity a demigod carries: two wings and the halo. */
    public static final int COUNT = 2 * (1 + COVERTS + PRIMARIES) + HALO_SEGMENTS;
    /** Ticks per wingbeat in flight. */
    public static final int BEAT_TICKS = 18;

    /** Where each wing meets the upper back (the left one; the right is mirrored). */
    public static final Vector3f ROOT = new Vector3f(0.11f, 1.36f, -0.2f);
    static final float ARM_LENGTH = 0.85f;
    static final Vector3f HALO_CENTER = new Vector3f(0, 2.13f, -0.05f);
    static final float HALO_RADIUS = 0.27f;

    private DemigodWings() {
    }

    // ---- Poses -----------------------------------------------------------------------------------

    /**
     * The pose for a moment. open is the current spread (eased by the caller), beat the wingbeat phase
     * in radians, pitch the player's view pitch in degrees (down is positive).
     */
    public static Pose pose(float open, float beat, boolean flying, boolean gliding, boolean swimming,
            boolean sneaking, float pitch) {
        float o = smooth(open);
        float raise, sweep;
        if (gliding) {
            // Held out stiff and swept back like a hawk's.
            raise = 0.1f + 0.04f * sin(beat);
            sweep = 0.55f;
        } else {
            float s = sin(beat);
            raise = flying ? 0.3f + 0.55f * s : 0.18f + 0.03f * s;
            sweep = flying ? 0.32f - 0.14f * s : 0.32f;
        }
        // Folded: the arms point back and a little up, the feathers hang down the back.
        raise = lerp(0.42f, raise, o);
        sweep = lerp(1.32f, sweep, o);
        float lean = 0, drop = 0;
        if (gliding || swimming) {
            lean = (float) Math.toRadians(clamp(90 + pitch, 20, 160));
        } else if (sneaking) {
            lean = 0.45f;
            drop = 0.12f;
        }
        return new Pose(open, raise, sweep, lean, drop);
    }

    // ---- Shape -----------------------------------------------------------------------------------

    /**
     * Who wears the wings: a demigod's gold and white, the exalted god's brighter gold and light, or
     * the wrathful god's black membrane over blood-red ribs, longer and scalloped.
     */
    public enum Style {
        DEMIGOD(Material.GOLD_BLOCK, Material.OCHRE_FROGLIGHT, Material.WHITE_CONCRETE, Material.SNOW_BLOCK, 0.24f, 0),
        ANGEL(Material.GOLD_BLOCK, Material.OCHRE_FROGLIGHT, Material.SNOW_BLOCK, Material.PEARLESCENT_FROGLIGHT, 0.24f,
                0),
        DEMON(Material.NETHERITE_BLOCK, Material.NETHER_WART_BLOCK, Material.BLACK_CONCRETE, Material.CRYING_OBSIDIAN,
                0.3f, 0.16f);

        public final Material arm, covert, feather, featherAlt;
        final float width, scallop;

        Style(Material arm, Material covert, Material feather, Material featherAlt, float width, float scallop) {
            this.arm = arm;
            this.covert = covert;
            this.feather = feather;
            this.featherAlt = featherAlt;
            this.width = width;
            this.scallop = scallop;
        }
    }

    /** Every box in body space: the left wing, the right wing, then the halo (always this order). */
    public static List<Piece> pieces(Pose pose) {
        List<Piece> out = new ArrayList<>(COUNT);
        out.addAll(wings(pose, Style.DEMIGOD));
        halo(out);
        if (pose.lean() != 0 || pose.drop() != 0) {
            Quaternionf lean = new Quaternionf().rotationX(pose.lean());
            out.replaceAll(p -> new Piece(p.kind(), p.material(),
                    lean.transform(new Vector3f(p.center())).sub(0, pose.drop(), 0), p.size(),
                    new Quaternionf(lean).mul(p.rot())));
        }
        return out;
    }

    /** Both wings (left, then the mirrored right) in body space, without lean or drop. */
    public static List<Piece> wings(Pose pose, Style style) {
        List<Piece> wing = new ArrayList<>(COUNT / 2);
        float o = smooth(pose.open());
        // The wing is drawn in its own plane (x out along the arm, y up, z = body z), then swept back
        // and raised around the root.
        Quaternionf w = new Quaternionf().rotationY(pose.sweep()).rotateZ(pose.raise());
        // The arm along the leading edge.
        add(wing, w, Kind.ARM, style.arm, new Vector3f(ARM_LENGTH / 2, 0, -0.045f),
                new Vector3f(ARM_LENGTH + 0.08f, 0.11f, 0.09f), new Quaternionf());
        // Coverts: short feathers over the arm.
        for (int k = 0; k < COVERTS; k++) {
            float f = k / (float) (COVERTS - 1);
            float along = ARM_LENGTH * lerp(0.18f, 0.78f, f);
            float ang = deg(lerp(lerp(-96, -92, f), lerp(-100, -58, f), o));
            float len = lerp(0.48f, 0.4f, f);
            feather(wing, w, Kind.COVERT, style.covert, along, -0.03f - 0.004f * k, ang, len, 0.25f, 0.035f);
        }
        // Flight feathers, fanning out from hanging down by the body to pointing out at the tip.
        for (int k = 0; k < PRIMARIES; k++) {
            float f = k / (float) (PRIMARIES - 1);
            float along = ARM_LENGTH * lerp(0.1f, 1.0f, f);
            float ang = deg(lerp(lerp(-97, -85, f), lerp(-100, -20, f), o));
            float len = lerp(0.82f, 1.1f, f) * lerp(0.92f, 1f, o) * (1 + (k % 2 == 0 ? style.scallop : -style.scallop / 2));
            Material m = k % 2 == 0 ? style.feather : style.featherAlt;
            feather(wing, w, Kind.PRIMARY, m, along, -0.004f * k, ang, len, style.width, 0.03f);
        }
        List<Piece> out = new ArrayList<>(wing.size() * 2);
        out.addAll(wing);
        for (Piece p : wing) out.add(mirror(p));
        return out;
    }

    /** A feather hanging from a point along the arm at an angle (radians from the arm, + toward up). */
    private static void feather(List<Piece> out, Quaternionf w, Kind kind, Material m, float along, float z,
            float ang, float len, float width, float thick) {
        Vector3f dir = new Vector3f((float) Math.cos(ang), (float) Math.sin(ang), 0);
        Vector3f center = new Vector3f(along, 0, z).add(dir.x * len / 2, dir.y * len / 2, 0);
        // The box's long side is its y; turn y onto dir.
        Quaternionf r = new Quaternionf().rotationZ(ang - (float) Math.PI / 2);
        add(out, w, kind, m, center, new Vector3f(width, len, thick), r);
    }

    private static void add(List<Piece> out, Quaternionf w, Kind kind, Material m, Vector3f local, Vector3f size,
            Quaternionf rot) {
        Vector3f c = w.transform(new Vector3f(local)).add(ROOT);
        out.add(new Piece(kind, m, c, size, new Quaternionf(w).mul(rot)));
    }

    /** The same box on the other side (mirrored across x = 0). */
    static Piece mirror(Piece p) {
        Quaternionf q = p.rot();
        return new Piece(p.kind(), p.material(), new Vector3f(-p.center().x, p.center().y, p.center().z), p.size(),
                new Quaternionf(q.x, -q.y, -q.z, q.w));
    }

    /** A ring tilted a little back above the head. */
    private static void halo(List<Piece> out) {
        Quaternionf tilt = new Quaternionf().rotationX(0.28f);
        float len = (float) (2 * Math.PI * HALO_RADIUS / HALO_SEGMENTS) + 0.035f;
        for (int i = 0; i < HALO_SEGMENTS; i++) {
            float a = (float) (2 * Math.PI * (i + 0.5) / HALO_SEGMENTS);
            Vector3f at = tilt.transform(new Vector3f(HALO_RADIUS * (float) Math.cos(a), 0,
                    HALO_RADIUS * (float) Math.sin(a))).add(HALO_CENTER);
            out.add(new Piece(Kind.HALO, Material.GOLD_BLOCK, at, new Vector3f(0.06f, 0.06f, len),
                    new Quaternionf(tilt).rotateY(-a)));
        }
    }

    // ---- Display transformations ------------------------------------------------------------------

    /**
     * The block display transformation of a piece for a player whose body faces yaw (Minecraft
     * degrees), relative to an entity standing at the player's feet with yaw 0.
     */
    public static Transformation transform(Piece p, float yaw) {
        Quaternionf turn = new Quaternionf().rotationY((float) Math.toRadians(-yaw));
        Quaternionf rot = turn.mul(p.rot(), new Quaternionf());
        Vector3f half = new Vector3f(p.size()).mul(0.5f);
        Vector3f corner = turn.transform(new Vector3f(p.center())).sub(rot.transform(half));
        return new Transformation(corner, rot, new Vector3f(p.size()), new Quaternionf());
    }

    /** True when two transformations differ enough to be worth sending. */
    public static boolean differs(Transformation a, Transformation b) {
        if (a == null || b == null) return true;
        return a.getTranslation().distanceSquared(b.getTranslation()) > 1e-6
                || Math.abs(a.getLeftRotation().dot(b.getLeftRotation())) < 0.99999f
                || a.getScale().distanceSquared(b.getScale()) > 1e-6;
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    private static float deg(float d) {
        return (float) Math.toRadians(d);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smooth(float t) {
        float c = clamp(t, 0, 1);
        return c * c * (3 - 2 * c);
    }

    private static float sin(float a) {
        return (float) Math.sin(a);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
