package net.bigyous.gptgodmc.god.vessel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.bigyous.gptgodmc.god.vessel.VesselLook.Slot;
import net.bigyous.gptgodmc.god.vessel.VesselSkin.Piece;

/**
 * The vessel's shape: a rig with player-skin proportions, measured in skin pixels (a player is 32
 * pixels tall), and its poses. Pure math, so tests can render it without a server.
 *
 * Body space: origin between the feet, +y up, the figure faces +z, so its left side is +x.
 */
public final class VesselModel {
    private interface P {
        Piece LEG = Piece.LEG, BOOT = Piece.BOOT, TORSO = Piece.TORSO, BELT = Piece.BELT, GEM = Piece.GEM,
                ARM = Piece.ARM, HAND = Piece.HAND, BAND = Piece.BAND, HEAD = Piece.HEAD, EYE = Piece.EYE,
                HAIR_TOP = Piece.HAIR_TOP, HAIR_BACK = Piece.HAIR_BACK, COLLAR = Piece.COLLAR, CAPE = Piece.CAPE,
                LINING = Piece.LINING, HORN = Piece.HORN, HALO = Piece.HALO, BEARD = Piece.BEARD,
                PATCH = Piece.PATCH;
    }

    public enum Bone {
        BODY, TORSO, HEAD, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, CAPE
    }

    /** A box: center and size in its bone's space (pixels), an optional tilt, and the look slot that colors it. */
    public record Part(Bone bone, Slot slot, VesselSkin.Piece piece, Vector3f center, Vector3f size,
            Quaternionf tilt) {
    }

    /** A part with its block and display transformation, for previews. */
    public record Placed(Material material, Transformation transformation, Bone bone, Part part) {
    }

    /** A bone's placement in body space (pixels): rotation, then offset. */
    public record Frame(Quaternionf rot, Vector3f pos) {
        Frame child(float x, float y, float z, Quaternionf r) {
            Vector3f p = rot.transform(new Vector3f(x, y, z)).add(pos);
            return new Frame(new Quaternionf(rot).mul(r), p);
        }
    }

    /** Blocks per skin pixel at scale 1: the vessel is 2.5 blocks tall (a player is 1.8). */
    public static final float BLOCKS_PER_PX = 2.5f / 32f;
    public static final float HEIGHT_PX = 32f;
    public static final float EYE_PX = 27.5f;
    public static final float HORN_TOP_PX = 35.5f;

    private VesselModel() {
    }

    public static float unit(double scale) {
        return (float) (scale * BLOCKS_PER_PX);
    }

    // ---- Size ----------------------------------------------------------------------------------

    /** A player's height in blocks; sizes are given in multiples of it. */
    public static final double PLAYER_BLOCKS = 1.8;
    /** The size used when nothing else is set: one and a half times a player. */
    public static final double DEFAULT_SIZE = 1.5;
    /** The named sizes, smallest first, in multiples of a player's height. */
    public static final Map<String, Double> SIZES;
    static {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("mortal", 1.0);
        m.put("lordly", 1.5);
        m.put("towering", 3.0);
        m.put("colossal", 6.0);
        SIZES = Collections.unmodifiableMap(m);
    }

    /** A size name ("towering") or a number of player heights ("2.5", "2.5x"); null if it is neither. */
    public static Double parseSize(String text) {
        if (text == null) return null;
        String t = text.trim().toLowerCase(java.util.Locale.ROOT);
        Double named = SIZES.get(t);
        if (named != null) return named;
        if (t.endsWith("x")) t = t.substring(0, t.length() - 1).trim();
        try {
            double v = Double.parseDouble(t);
            return v > 0 && !Double.isInfinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The model scale (1 = 2.5 blocks tall) of a vessel this many player heights tall. */
    public static double scaleForSize(double playerHeights) {
        return playerHeights * PLAYER_BLOCKS / (HEIGHT_PX * BLOCKS_PER_PX);
    }

    /** "towering (3x a mortal, 5.4 blocks tall)". */
    public static String describeSize(double playerHeights) {
        String name = null;
        for (Map.Entry<String, Double> e : SIZES.entrySet()) {
            if (Math.abs(e.getValue() - playerHeights) < 0.01) name = e.getKey();
        }
        String times = playerHeights == Math.rint(playerHeights) ? String.valueOf((long) playerHeights)
                : String.format(java.util.Locale.ROOT, "%.1f", playerHeights);
        String text = String.format(java.util.Locale.ROOT, "%sx a mortal's height, %.1f blocks tall", times,
                playerHeights * PLAYER_BLOCKS);
        return name == null ? text : name + " (" + text + ")";
    }

    // ---- Shape ---------------------------------------------------------------------------------

    public static List<Part> parts(VesselLook look) {
        List<Part> m = new ArrayList<>();
        // Legs: trousers and boots. Hips pivot at (+-1.9, 12).
        for (int side : new int[] { 1, -1 }) {
            Bone leg = side > 0 ? Bone.LEFT_LEG : Bone.RIGHT_LEG;
            box(m, leg, Slot.LEGS, P.LEG, 0.1f * side, -4.5f, 0, 4, 9, 4);
            box(m, leg, Slot.BOOTS, P.BOOT, 0.1f * side, -10.5f, 0, 4.3f, 3.2f, 4.3f);
        }
        // Torso (pivot at the waist), belt and a glowing gem over the heart.
        box(m, Bone.TORSO, Slot.TORSO, P.TORSO, 0, 6, 0, 8, 12, 4);
        box(m, Bone.TORSO, Slot.BELT, P.BELT, 0, 0.95f, 0, 8.5f, 1.7f, 4.5f);
        box(m, Bone.TORSO, Slot.GEM, P.GEM, 0, 0.95f, 2.32f, 1.6f, 1.3f, 0.3f);
        m.add(new Part(Bone.TORSO, Slot.GEM, P.GEM, new Vector3f(0, 8.6f, 2.1f), new Vector3f(1.7f, 1.7f, 0.3f),
                new Quaternionf().rotationZ((float) (Math.PI / 4))));
        // Arms (pivot at the shoulder): sleeve, hand and a band at the wrist.
        for (int side : new int[] { 1, -1 }) {
            Bone arm = side > 0 ? Bone.LEFT_ARM : Bone.RIGHT_ARM;
            box(m, arm, Slot.SLEEVES, P.ARM, side, -2.5f, 0, 4, 9, 4);
            box(m, arm, Slot.HANDS, P.HAND, side, -8.5f, 0, 4, 3, 4);
            box(m, arm, Slot.BANDS, P.BAND, side, -6.6f, 0, 4.4f, 1.2f, 4.4f);
        }
        // Head (pivot at the neck) with glowing eyes where a skin draws them.
        box(m, Bone.HEAD, Slot.SKIN, P.HEAD, 0, 4, 0, 8, 8, 8);
        box(m, Bone.HEAD, Slot.EYES, P.EYE, 2, 3.5f, 4.1f, 2, 1.1f, 0.3f);
        if (look.material(Slot.PATCH) != null) {
            // One-eyed: a leather patch over the right eye (the painted skin draws its strap).
            box(m, Bone.HEAD, Slot.PATCH, P.PATCH, -2, 3.55f, 4.15f, 2.4f, 1.7f, 0.4f);
        } else {
            box(m, Bone.HEAD, Slot.EYES, P.EYE, -2, 3.5f, 4.1f, 2, 1.1f, 0.3f);
        }
        // A beard over the jaw, hanging a little below the chin (only looks with a BEARD material).
        box(m, Bone.HEAD, Slot.BEARD, P.BEARD, 0, 0.2f, 4.0f, 6.6f, 4.8f, 1.6f);
        box(m, Bone.HEAD, Slot.HAIR, P.HAIR_TOP, 0, 7.55f, 0, 8.5f, 1.5f, 8.5f);
        box(m, Bone.HEAD, Slot.HAIR, P.HAIR_BACK, 0, 4.6f, -3.95f, 8.5f, 4.6f, 0.9f);
        switch (look.ornament()) {
            case HORNS -> horns(m);
            case HALO -> halo(m, false);
            case SHATTERED -> halo(m, true);
            case LAUREL -> laurel(m);
            case CROWN -> crown(m, false);
            case SHARDS -> crown(m, true);
            case TRIDENT -> trident(m);
            case CREST -> crest(m);
            case ANTLERS -> antlers(m);
            case EARS -> ears(m);
            case SUNDISK -> sundisk(m);
            case CRESCENT -> crescent(m);
            case COG -> cog(m);
            case JESTER -> jester(m);
            case HAT -> hat(m);
            default -> {
            }
        }
        if (look.cape()) {
            // A cloak collar across the shoulders, fastened with two gems.
            box(m, Bone.TORSO, Slot.CAPE, P.COLLAR, 0, 11.3f, -0.2f, 8.6f, 1.5f, 4.8f);
            box(m, Bone.TORSO, Slot.GEM, P.GEM, 3.1f, 10.9f, 2.3f, 1.2f, 1.2f, 0.3f);
            box(m, Bone.TORSO, Slot.GEM, P.GEM, -3.1f, 10.9f, 2.3f, 1.2f, 1.2f, 0.3f);
            box(m, Bone.CAPE, Slot.CAPE_LINING, P.LINING, 0, -8.5f, -0.25f, 10, 17, 0.4f);
            box(m, Bone.CAPE, Slot.CAPE, P.CAPE, 0, -8.5f, -0.65f, 10, 17, 0.4f);
        }
        m.removeIf(p -> look.material(p.slot()) == null);
        return m;
    }

    private static void box(List<Part> m, Bone bone, Slot slot, VesselSkin.Piece piece, float cx, float cy,
            float cz, float sx, float sy, float sz) {
        m.add(new Part(bone, slot, piece, new Vector3f(cx, cy, cz), new Vector3f(sx, sy, sz), new Quaternionf()));
    }

    /** Ram horns: tapering segments that rise from the crown, curl outward and wrap down past the temples. */
    private static void horns(List<Part> m) {
        int segments = 8;
        for (int side : new int[] { 1, -1 }) {
            Vector3f prev = hornPoint(0, side);
            for (int i = 1; i <= segments; i++) {
                Vector3f next = hornPoint(i / (float) segments, side);
                float t = (i - 0.5f) / segments;
                float thick = 2.7f - 1.75f * t;
                Vector3f dir = new Vector3f(next).sub(prev);
                Vector3f mid = new Vector3f(prev).add(next).mul(0.5f);
                Quaternionf tilt = new Quaternionf().rotationTo(0, 1, 0, dir.x, dir.y, dir.z);
                m.add(new Part(Bone.HEAD, Slot.ORNAMENT, P.HORN, mid, new Vector3f(thick, dir.length() + thick * 0.5f, thick),
                        tilt));
                prev = next;
            }
        }
    }

    /** A point along a horn's centerline (t from root 0 to tip 1), in head space. */
    public static Vector3f hornPoint(float t, int side) {
        double theta = Math.toRadians(187 - 337 * t);
        double r = 3.42 * (1 - 0.55 * t);
        float x = (float) (5.6 + r * Math.cos(theta));
        float y = (float) (8.0 + r * Math.sin(theta));
        float z = (float) (0.8 - 2.6 * Math.sin(Math.PI * t) + 1.2 * t);
        return new Vector3f(x * side, y, z);
    }

    /** A tilted ring floating above the head; shattered, a few pieces are missing and the rest askew. */
    private static void halo(List<Part> m, boolean shattered) {
        int n = 12;
        float r = 4.4f;
        Quaternionf tilt = new Quaternionf().rotationX(0.3f);
        Vector3f center = new Vector3f(0, 10.9f, -0.9f);
        float len = (float) (2 * Math.PI * r / n) + 0.3f;
        for (int i = 0; i < n; i++) {
            if (shattered && (i == 2 || i == 7 || i == 8)) continue;
            float a = (float) (2 * Math.PI * i / n);
            float rr = shattered ? r + 0.4f * (float) Math.sin(i * 2.7) : r;
            float dy = shattered ? 0.35f * (float) Math.cos(i * 1.9) : 0;
            Vector3f at = tilt.transform(new Vector3f(rr * (float) Math.cos(a), dy, rr * (float) Math.sin(a))).add(center);
            Quaternionf rot = new Quaternionf(tilt).rotateY(-a);
            if (shattered) rot.rotateX(0.3f * (float) Math.sin(i * 3.1));
            m.add(new Part(Bone.HEAD, Slot.ORNAMENT, P.HALO, at,
                    new Vector3f(0.8f, 0.8f, shattered ? len * 0.8f : len), rot));
        }
    }

    /** A box from a to b (head space), thick across and deep front to back; for antlers, caps and ears. */
    private static void segment(List<Part> m, Vector3f a, Vector3f b, float thick, float deep) {
        Vector3f dir = new Vector3f(b).sub(a);
        Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
        Quaternionf tilt = new Quaternionf().rotationTo(0, 1, 0, dir.x, dir.y, dir.z);
        m.add(new Part(Bone.HEAD, Slot.ORNAMENT, P.HORN, mid,
                new Vector3f(thick, dir.length() + Math.min(thick, deep) * 0.5f, deep), tilt));
    }

    /** An untextured ornament block (it keeps its block look, like the horns). */
    private static void ornament(List<Part> m, Vector3f at, Vector3f size, Quaternionf rot) {
        m.add(new Part(Bone.HEAD, Slot.ORNAMENT, P.HORN, at, size, rot));
    }

    private static void gem(List<Part> m, Vector3f at, Vector3f size, Quaternionf rot) {
        m.add(new Part(Bone.HEAD, Slot.GEM, P.GEM, at, size, rot));
    }

    /** The four sides of the head: outward normal (x, z) and the direction along that side (x, z). */
    private static final float[][] SIDES = { { 0, 1, 1, 0 }, { 1, 0, 0, -1 }, { 0, -1, -1, 0 }, { -1, 0, 0, 1 } };

    /** A wreath of leaves around the brow, open over the forehead. */
    private static void laurel(List<Part> m) {
        for (int k = 0; k < 4; k++) {
            float nx = SIDES[k][0], nz = SIDES[k][1], tx = SIDES[k][2], tz = SIDES[k][3];
            float[] offsets = k == 0 ? new float[] { -3.3f, -1.7f, 1.7f, 3.3f } : new float[] { -2.8f, 0, 2.8f };
            float yaw = (float) Math.atan2(-tz, tx);
            for (float off : offsets) {
                Vector3f at = new Vector3f(nx * 4.55f + tx * off, 6.9f, nz * 4.55f + tz * off);
                // The leaves lean toward the front, like a wreath tied at the back.
                float lean = k == 0 ? -Math.signum(off) * 0.55f : k == 2 ? 0 : 0.45f;
                ornament(m, at, new Vector3f(2.1f, 0.85f, 0.5f), new Quaternionf().rotationY(yaw).rotateZ(lean));
            }
        }
    }

    /** A band around the brow with spikes; jagged, the spikes are uneven and lean out (ice, fire, crystal). */
    private static void crown(List<Part> m, boolean jagged) {
        band(m);
        int i = 0;
        for (int k = 0; k < 4; k++) {
            float nx = SIDES[k][0], nz = SIDES[k][1], tx = SIDES[k][2], tz = SIDES[k][3];
            for (float off : new float[] { -3f, 0, 3f }) {
                i++;
                float jitter = jagged ? (float) Math.sin(i * 12.9898) * 0.7f : 0;
                float h = jagged ? 1.6f + 2.8f * (float) Math.abs(Math.sin(i * 78.233))
                        : (k == 0 && off == 0 ? 2.9f : 1.8f);
                float lean = jagged ? 0.3f : 0.1f;
                // Turning about (y x n) leans the spike's top outward.
                Quaternionf rot = new Quaternionf().rotationAxis(lean, nz, 0, -nx).rotateY((float) Math.PI / 4);
                Vector3f up = rot.transform(new Vector3f(0, h / 2, 0));
                Vector3f at = new Vector3f(nx * 4.4f + tx * (off + jitter), 7.4f, nz * 4.4f + tz * (off + jitter)).add(up);
                float w = jagged ? 0.75f + 0.35f * (float) Math.abs(Math.sin(i * 3.7)) : 0.85f;
                ornament(m, at, new Vector3f(w, h, w), rot);
            }
        }
    }

    private static void band(List<Part> m) {
        ornament(m, new Vector3f(0, 7.1f, 4.4f), new Vector3f(9.3f, 1.0f, 0.5f), new Quaternionf());
        ornament(m, new Vector3f(0, 7.1f, -4.4f), new Vector3f(9.3f, 1.0f, 0.5f), new Quaternionf());
        ornament(m, new Vector3f(4.4f, 7.1f, 0), new Vector3f(0.5f, 1.0f, 8.8f), new Quaternionf());
        ornament(m, new Vector3f(-4.4f, 7.1f, 0), new Vector3f(0.5f, 1.0f, 8.8f), new Quaternionf());
    }

    /** A sea crown: three trident prongs over the brow, short spikes around. */
    private static void trident(List<Part> m) {
        band(m);
        Quaternionf diamond = new Quaternionf().rotationZ((float) Math.PI / 4);
        for (float x : new float[] { -2.7f, 0, 2.7f }) {
            float h = x == 0 ? 4.6f : 3.2f;
            ornament(m, new Vector3f(x, 7.5f + h / 2, 4.4f), new Vector3f(0.9f, h, 0.7f), new Quaternionf());
            ornament(m, new Vector3f(x, 7.6f + h, 4.4f), new Vector3f(1.2f, 1.2f, 0.6f), diamond);
        }
        // Barbs on the outer prongs, curling outward.
        for (int side : new int[] { 1, -1 }) {
            ornament(m, new Vector3f(side * 3.4f, 9.5f, 4.4f), new Vector3f(1.5f, 0.6f, 0.6f),
                    new Quaternionf().rotationZ(side * 0.7f));
        }
        Quaternionf turned = new Quaternionf().rotationY((float) Math.PI / 4);
        for (float[] s : new float[][] { { 4.4f, 2.5f }, { 4.4f, -2.5f }, { -4.4f, 2.5f }, { -4.4f, -2.5f },
                { 0, -4.4f } }) {
            ornament(m, new Vector3f(s[0], 8.3f, s[1]), new Vector3f(0.7f, 1.7f, 0.7f), turned);
        }
    }

    /** A helm's crest: a fan of horsehair from the brow over the crown, trailing down behind. */
    private static void crest(List<Part> m) {
        ornament(m, new Vector3f(0, 8.45f, -1f), new Vector3f(1.3f, 0.6f, 8.8f), new Quaternionf());
        for (int i = 0; i < 7; i++) {
            float z = 3.2f - i * 1.45f;
            float h = 1.4f + 1.9f * (float) Math.sin(Math.PI * (i + 0.6) / 7.5);
            float y = 8.4f + h / 2 - (i >= 5 ? 1.3f * (i - 4) : 0);
            ornament(m, new Vector3f(0, y, z), new Vector3f(0.9f, h, 1.6f), new Quaternionf().rotationX(-0.12f * i));
        }
    }

    /** Branching antlers rising from the temples. */
    private static void antlers(List<Part> m) {
        float[][] beam = { { 2.6f, 7.8f, 0.2f }, { 4.4f, 10.0f, -0.4f }, { 5.8f, 12.4f, -0.9f }, { 6.6f, 14.8f, -1.4f },
                { 6.9f, 16.8f, -1.9f } };
        float[][][] tines = { { beam[1], { 5.0f, 12.6f, 1.7f } }, { beam[2], { 4.5f, 14.8f, 0.4f } },
                { beam[3], { 8.9f, 16.3f, -1.0f } } };
        for (int side : new int[] { 1, -1 }) {
            for (int i = 1; i < beam.length; i++) {
                float thick = 1.4f - 0.17f * i;
                segment(m, v(beam[i - 1], side), v(beam[i], side), thick, thick);
            }
            for (float[][] t : tines) segment(m, v(t[0], side), v(t[1], side), 0.65f, 0.65f);
        }
    }

    private static Vector3f v(float[] p, int side) {
        return new Vector3f(p[0] * side, p[1], p[2]);
    }

    /** A jackal's tall pointed ears and long muzzle. */
    private static void ears(List<Part> m) {
        float[][] ear = { { 2.6f, 7.6f, -0.6f }, { 3.0f, 10.3f, -0.9f }, { 3.3f, 12.7f, -1.1f }, { 3.45f, 14.6f, -1.2f } };
        float[] width = { 2.5f, 1.6f, 0.8f };
        for (int side : new int[] { 1, -1 }) {
            for (int i = 1; i < ear.length; i++) segment(m, v(ear[i - 1], side), v(ear[i], side), width[i - 1], 0.8f);
        }
        ornament(m, new Vector3f(0, 1.9f, 5.3f), new Vector3f(3.4f, 2.4f, 3.2f), new Quaternionf());
        ornament(m, new Vector3f(0, 2.75f, 6.95f), new Vector3f(1.5f, 0.9f, 0.7f), new Quaternionf());
    }

    /** A burning disc held upright above the head, a gem at its heart. */
    private static void sundisk(List<Part> m) {
        ring(m, new Vector3f(0, 12.6f, -1.5f), 3.9f, 14, 0.9f, 0, 360);
        gem(m, new Vector3f(0, 12.6f, -1.6f), new Vector3f(5.0f, 5.0f, 0.5f), new Quaternionf());
        gem(m, new Vector3f(0, 12.6f, -1.6f), new Vector3f(5.0f, 5.0f, 0.5f),
                new Quaternionf().rotationZ((float) Math.PI / 4));
    }

    /**
     * An upright ring, or an arc between two angles (degrees, tapering to points), of halo segments in
     * the head's xy plane, so the painted skin can make it glow.
     */
    private static void ring(List<Part> m, Vector3f center, float r, int n, float thick, float from, float to) {
        boolean closed = to - from >= 359;
        float span = (float) Math.toRadians(to - from);
        float len = (float) (span * r / n) + 0.25f;
        for (int i = 0; i < n; i++) {
            float a = (float) Math.toRadians(from) + span * (i + (closed ? 0 : 0.5f)) / n;
            float taper = closed ? 1 : 0.35f + 0.65f * (float) Math.sin(Math.PI * (i + 0.5) / n);
            Vector3f at = new Vector3f(center).add(r * (float) Math.cos(a), r * (float) Math.sin(a), 0);
            m.add(new Part(Bone.HEAD, Slot.ORNAMENT, P.HALO, at, new Vector3f(thick * taper, len, 0.8f),
                    new Quaternionf().rotationZ(a)));
        }
    }

    /** A crescent moon cupped above the head, horns up, with a small star between them. */
    private static void crescent(List<Part> m) {
        ring(m, new Vector3f(0, 12.4f, -1.2f), 4.0f, 10, 1.7f, 190, 350);
        gem(m, new Vector3f(0, 13.4f, -1.2f), new Vector3f(1.2f, 1.2f, 0.4f),
                new Quaternionf().rotationZ((float) Math.PI / 4));
    }

    /** A great cog standing behind the head like a halo, a gem at its hub. */
    private static void cog(List<Part> m) {
        Vector3f c = new Vector3f(0, 6.5f, -5.2f);
        ring(m, c, 5.2f, 10, 0.9f, 0, 360);
        for (int i = 0; i < 10; i++) {
            float a = (float) (2 * Math.PI * (i + 0.5) / 10);
            ornament(m, new Vector3f(c).add(6.05f * (float) Math.cos(a), 6.05f * (float) Math.sin(a), 0),
                    new Vector3f(1.2f, 1.1f, 0.8f), new Quaternionf().rotationZ(a));
        }
        gem(m, new Vector3f(c), new Vector3f(1.8f, 1.8f, 0.5f), new Quaternionf());
    }

    /** A fool's cap: three floppy points with a bell at each tip. */
    private static void jester(List<Part> m) {
        float[][] side = { { 1.6f, 8.0f, 0 }, { 3.8f, 10.3f, 0 }, { 6.2f, 11.1f, 0 }, { 8.2f, 10.0f, 0 },
                { 9.0f, 7.8f, 0 } };
        float[][] back = { { 0, 8.2f, -1.6f }, { 0, 10.6f, -3.4f }, { 0, 11.1f, -5.8f }, { 0, 9.8f, -7.6f },
                { 0, 7.8f, -8.2f } };
        for (float[][] pt : new float[][][] { side, back }) {
            for (int s : pt == back ? new int[] { 1 } : new int[] { 1, -1 }) {
                for (int i = 1; i < pt.length; i++) {
                    float thick = 2.8f - 0.45f * i;
                    segment(m, v(pt[i - 1], s), v(pt[i], s), thick, thick);
                }
                gem(m, v(pt[pt.length - 1], s).add(0, -0.6f, 0), new Vector3f(1.5f, 1.5f, 1.5f), new Quaternionf());
            }
        }
    }

    /** A wanderer's wide-brimmed hat, its crown slouching, with a leather band. */
    private static void hat(List<Part> m) {
        ornament(m, new Vector3f(0, 7.9f, 0.2f), new Vector3f(12.6f, 0.45f, 12.6f), new Quaternionf().rotationX(0.05f));
        ornament(m, new Vector3f(0, 9.2f, -0.1f), new Vector3f(8.8f, 2.2f, 8.8f), new Quaternionf().rotationX(-0.06f));
        ornament(m, new Vector3f(0, 10.85f, -0.5f), new Vector3f(6.6f, 1.5f, 6.6f), new Quaternionf().rotationX(-0.14f));
        ornament(m, new Vector3f(0, 11.9f, -1.0f), new Vector3f(4.0f, 1.0f, 4.0f), new Quaternionf().rotationX(-0.25f));
        m.add(new Part(Bone.HEAD, Slot.BELT, P.BELT, new Vector3f(0, 8.5f, -0.1f), new Vector3f(9.0f, 0.6f, 9.0f),
                new Quaternionf().rotationX(-0.06f)));
    }

    // ---- Skeleton ------------------------------------------------------------------------------

    public static Map<Bone, Frame> frames(Pose pose) {
        Map<Bone, Frame> f = new EnumMap<>(Bone.class);
        // The whole figure leans (in flight) around its middle.
        Quaternionf lean = new Quaternionf().rotationY(pose.bodyYaw).rotateX(pose.bodyPitch).rotateZ(pose.bodyRoll);
        Vector3f pivot = new Vector3f(0, 16, 0);
        Frame body = new Frame(lean, new Vector3f(pivot).sub(lean.transform(new Vector3f(pivot))).add(pose.offset));
        Frame torso = body.child(0, 12, 0, new Quaternionf().rotationX(pose.torsoPitch));
        f.put(Bone.BODY, body);
        f.put(Bone.TORSO, torso);
        f.put(Bone.HEAD, torso.child(0, 12, 0, new Quaternionf().rotationY(pose.headYaw).rotateX(pose.headPitch)));
        f.put(Bone.LEFT_ARM, torso.child(5, 10, 0, pose.leftArm));
        f.put(Bone.RIGHT_ARM, torso.child(-5, 10, 0, pose.rightArm));
        f.put(Bone.LEFT_LEG, body.child(1.9f, 12, 0, pose.leftLeg));
        f.put(Bone.RIGHT_LEG, body.child(-1.9f, 12, 0, pose.rightLeg));
        f.put(Bone.CAPE, torso.child(0, 12, -2.15f, new Quaternionf().rotationX(pose.capePitch)));
        return f;
    }

    /**
     * The display transformation for a part, in blocks. grow below 1 shrinks the figure toward its chest
     * (for appearing and vanishing).
     */
    public static Transformation transform(Part part, Frame frame, float unit, float grow) {
        Quaternionf rot = new Quaternionf(frame.rot()).mul(part.tilt());
        Vector3f half = new Vector3f(part.size()).mul(0.5f);
        Vector3f corner = frame.rot().transform(new Vector3f(part.center())).add(frame.pos())
                .sub(rot.transform(half)).mul(unit);
        Vector3f size = new Vector3f(part.size()).mul(unit);
        if (grow != 1f) {
            Vector3f chest = new Vector3f(0, 18 * unit, 0);
            corner.sub(chest).mul(grow).add(chest);
            size.mul(grow);
        }
        return new Transformation(corner, rot, size, new Quaternionf());
    }

    /**
     * The same placement for an ItemDisplay showing the part's custom model. Item models are drawn
     * centered on the entity and turned half a circle around y, so the box moves to its center and a
     * right rotation of half a turn undoes the client's own.
     */
    public static Transformation itemTransform(Part part, Frame frame, float unit, float grow) {
        return toItem(transform(part, frame, unit, grow));
    }

    /** Converts a block display transformation (unit cube at the corner) to an item display one. */
    public static Transformation toItem(Transformation block) {
        Vector3f center = new Vector3f(block.getScale()).mul(0.5f);
        block.getLeftRotation().transform(center).add(block.getTranslation());
        return new Transformation(center, block.getLeftRotation(), block.getScale(), ITEM_FLIP);
    }

    /** Undoes the half turn the client gives every item display. */
    public static final Quaternionf ITEM_FLIP = new Quaternionf().rotationY((float) Math.PI);

    /** Every part of a look in a pose, for previews and tests. */
    public static List<Placed> place(VesselLook look, Pose pose, double scale) {
        Map<Bone, Frame> frames = frames(pose);
        float unit = unit(scale);
        List<Placed> out = new ArrayList<>();
        for (Part p : parts(look)) {
            out.add(new Placed(look.material(p.slot()), transform(p, frames.get(p.bone()), unit, 1f), p.bone(), p));
        }
        return out;
    }

    // ---- Poses ---------------------------------------------------------------------------------

    /** Joint angles in radians. Quaternions are relative to the bone's parent. */
    public static final class Pose {
        /** The whole figure leaning forward (flight). */
        public float bodyPitch;
        public float torsoPitch;
        public float headYaw;
        public float headPitch;
        public float capePitch;
        /** The whole figure turning and tilting sideways (animations). */
        public float bodyYaw;
        public float bodyRoll;
        /** The whole figure moved (pixels, body space), e.g. sunk low to sit (animations). */
        public final Vector3f offset = new Vector3f();
        public final Quaternionf leftArm = new Quaternionf();
        public final Quaternionf rightArm = new Quaternionf();
        public final Quaternionf leftLeg = new Quaternionf();
        public final Quaternionf rightLeg = new Quaternionf();

        /** Moves this pose a fraction of the way toward another. */
        public void approach(Pose target, float f) {
            bodyPitch += (target.bodyPitch - bodyPitch) * f;
            torsoPitch += (target.torsoPitch - torsoPitch) * f;
            headYaw += (target.headYaw - headYaw) * f;
            headPitch += (target.headPitch - headPitch) * f;
            capePitch += (target.capePitch - capePitch) * f;
            // The shortest way round, so a spin that wraps past half a turn does not unwind.
            float dy = (float) Math.IEEEremainder(target.bodyYaw - bodyYaw, Math.PI * 2);
            bodyYaw = (float) Math.IEEEremainder(bodyYaw + dy * f, Math.PI * 2);
            bodyRoll += (target.bodyRoll - bodyRoll) * f;
            offset.lerp(target.offset, f);
            leftArm.slerp(target.leftArm, f);
            rightArm.slerp(target.rightArm, f);
            leftLeg.slerp(target.leftLeg, f);
            rightLeg.slerp(target.rightLeg, f);
        }

        public void set(Pose o) {
            bodyPitch = o.bodyPitch;
            torsoPitch = o.torsoPitch;
            headYaw = o.headYaw;
            headPitch = o.headPitch;
            capePitch = o.capePitch;
            bodyYaw = o.bodyYaw;
            bodyRoll = o.bodyRoll;
            offset.set(o.offset);
            leftArm.set(o.leftArm);
            rightArm.set(o.rightArm);
            leftLeg.set(o.leftLeg);
            rightLeg.set(o.rightLeg);
        }
    }

    /** Every gesture and how long it lasts (game ticks). Speech lasts as long as the words. */
    public static final Map<String, Integer> GESTURE_TICKS = gestureTicks();
    public static final List<String> GESTURES = List.copyOf(GESTURE_TICKS.keySet());

    private static Map<String, Integer> gestureTicks() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("idle", 0);
        m.put("raise_arms", 70);
        m.put("spread_arms", 70);
        m.put("point", 70);
        m.put("beckon", 70);
        m.put("judge", 70);
        m.put("bow", 70);
        m.put("speak", 60);
        m.put("cast", 70);
        m.put("slam", 56);
        m.put("clap", 34);
        m.put("summon", 72);
        m.put("roar", 50);
        m.put("laugh", 50);
        m.put("wave", 50);
        m.put("nod", 30);
        m.put("shake_head", 36);
        m.put("fold_arms", 140);
        m.put("meditate", 400);
        return Collections.unmodifiableMap(m);
    }

    /** When a gesture's blow lands (ticks after it starts), for its sound and particles; -1 for none. */
    public static int impactTick(String gesture) {
        return switch (gesture) {
            case "slam" -> 30;
            case "clap" -> 18;
            case "summon" -> 64;
            case "roar" -> 4;
            default -> -1;
        };
    }

    /** Fast gestures need the pose to follow sharply. */
    public static boolean snappy(String gesture) {
        return switch (gesture) {
            case "slam", "clap", "nod", "shake_head" -> true;
            default -> false;
        };
    }

    /**
     * Everything that shapes the pose.
     *
     * @param age    ticks since the gesture began
     * @param time   game ticks, for breathing and sway
     * @param walk   0 = still, 1 = striding
     * @param phase  the stride cycle (radians)
     * @param air    0 = near the ground, 1 = levitating
     * @param flight 0 = hovering, 1 = flying forward at speed
     * @param look   body-space direction from the eyes to what it watches, or null
     * @param point  body-space direction from the right shoulder to whoever it gestures at, or null
     */
    public record Motion(String gesture, float age, float time, float walk, float phase, float air, float flight,
            VesselMood mood, Vector3f look, Vector3f point) {
    }

    /** A pose on the ground in a calm mood, for previews and tests. */
    public static Pose pose(String gesture, float time, float walk, float phase, Vector3f look, Vector3f point) {
        return pose(new Motion(gesture, 20, time, walk, phase, 0, 0, VesselMood.CALM, look, point));
    }

    /** The pose the vessel is reaching for. */
    public static Pose pose(Motion m) {
        Pose p = new Pose();
        float time = m.time();
        float air = m.air();
        float fly = m.flight();
        float walk = m.walk() * (1 - air);
        float still = 1 - walk;
        float swing = (float) Math.cos(m.phase()) * 0.62f * walk;
        float sway = 0.07f + 0.035f * sin(time * 0.045f);
        float drift = 0.05f * sin(time * 0.067f);

        arm(p.leftArm, -swing * 0.8f + drift, sway + 0.12f * air);
        arm(p.rightArm, swing * 0.8f - drift, -sway - 0.12f * air);
        float dangleL = still * (0.07f + 0.04f * sin(time * 0.05f));
        float dangleR = still * (-0.03f + 0.04f * sin(time * 0.05f + 1.7f));
        p.leftLeg.rotationX(-(swing + dangleL)).rotateZ(0.035f * still);
        p.rightLeg.rotationX(-(-swing + dangleR)).rotateZ(-0.035f * still);
        if (air > 0) {
            // Levitating: the legs hang together, toes pointed down, drifting slowly.
            p.leftLeg.slerp(new Quaternionf().rotationX(0.17f + 0.05f * sin(time * 0.06f)).rotateZ(0.02f), air);
            p.rightLeg.slerp(new Quaternionf().rotationX(0.07f + 0.05f * sin(time * 0.06f + 0.9f)).rotateZ(-0.02f),
                    air);
        }
        p.torsoPitch = 0.09f * walk + 0.015f * sin(time * 0.045f);
        p.capePitch = 0.1f + 0.5f * walk + (0.035f + 0.08f * walk) * sin(time * (0.08f + 0.2f * walk))
                + air * (0.12f + 0.07f * sin(time * 0.11f));

        Vector3f look = m.look();
        if (look != null && look.lengthSquared() > 1e-4f) {
            p.headYaw = clamp((float) Math.atan2(look.x, look.z), -1.15f, 1.15f);
            p.headPitch = clamp((float) Math.atan2(-look.y, Math.hypot(look.x, look.z)), -0.7f, 0.75f);
        } else {
            p.headYaw = 0.35f * sin(time * 0.012f);
            p.headPitch = 0.05f + 0.06f * sin(time * 0.019f);
        }

        mood(p, m.mood() == null ? VesselMood.CALM : m.mood(), time, 1 - Math.max(walk, fly));

        if (fly > 0) {
            // Flight: lean into the wind; arms and legs trail behind and the cape streams.
            p.bodyPitch = 0.85f * fly;
            p.leftArm.slerp(arm(new Quaternionf(), -0.5f, 0.3f), fly);
            p.rightArm.slerp(arm(new Quaternionf(), -0.5f, -0.3f), fly);
            float flutter = 0.04f * sin(time * 0.9f);
            p.leftLeg.slerp(new Quaternionf().rotationX(0.32f + flutter).rotateZ(0.03f), fly);
            p.rightLeg.slerp(new Quaternionf().rotationX(0.26f - flutter).rotateZ(-0.03f), fly);
            // The cape streams out behind, roughly level with the line of flight.
            p.capePitch += fly * (0.3f + 0.12f * sin(time * 0.75f));
            p.headPitch -= 0.8f * p.bodyPitch;
        }

        gesture(p, m, time);
        return p;
    }

    /** Posture for a mood, blended in by weight (it gives way to walking and flying). */
    private static void mood(Pose p, VesselMood mood, float time, float weight) {
        switch (mood) {
            case WRATHFUL -> {
                // Fists clenched out from the sides, trembling; chest out, glaring down.
                float tremble = 0.03f * sin(time * 1.9f);
                p.leftArm.slerp(arm(new Quaternionf(), 0.3f + tremble, 0.72f), weight);
                p.rightArm.slerp(arm(new Quaternionf(), 0.3f - tremble, -0.72f), weight);
                p.leftLeg.rotateZ(0.1f);
                p.rightLeg.rotateZ(-0.1f);
                p.torsoPitch -= 0.07f;
                p.headPitch += 0.12f;
                p.capePitch += 0.35f + 0.18f * sin(time * 0.37f);
            }
            case JOYFUL -> {
                float lilt = 0.08f * sin(time * 0.12f);
                p.leftArm.slerp(arm(new Quaternionf(), 0.35f + lilt, 0.55f), weight);
                p.rightArm.slerp(arm(new Quaternionf(), 0.35f - lilt, -0.55f), weight);
                p.headPitch -= 0.14f;
                p.capePitch += 0.1f;
            }
            case SORROWFUL -> {
                p.leftArm.slerp(arm(new Quaternionf(), 0.22f, 0.04f), weight);
                p.rightArm.slerp(arm(new Quaternionf(), 0.22f, -0.04f), weight);
                p.torsoPitch += 0.2f;
                p.headPitch = 0.5f + 0.03f * sin(time * 0.03f);
                p.headYaw *= 0.3f;
                p.capePitch = 0.05f;
            }
            case OMINOUS -> {
                p.leftArm.slerp(arm(new Quaternionf(), 0.45f, 0.32f), weight);
                p.rightArm.slerp(arm(new Quaternionf(), 0.45f, -0.32f), weight);
                p.headPitch += 0.2f;
                p.capePitch = 0.15f + 0.1f * sin(time * 0.05f);
            }
            case EXALTED -> {
                float breathe = 0.05f * sin(time * 0.05f);
                p.leftArm.slerp(arm(new Quaternionf(), 0.2f, 1.95f + breathe), weight);
                p.rightArm.slerp(arm(new Quaternionf(), 0.2f, -1.95f - breathe), weight);
                p.headPitch = -0.3f;
                p.capePitch += 0.25f;
            }
            default -> {
            }
        }
    }

    private static void gesture(Pose p, Motion m, float time) {
        String g = m.gesture() == null ? "idle" : m.gesture();
        int len = Math.max(1, GESTURE_TICKS.getOrDefault(g, 70));
        float k = clamp(m.age() / len, 0, 1);
        switch (g) {
            case "raise_arms" -> {
                arm(p.leftArm, 2.75f, 0.45f);
                arm(p.rightArm, 2.75f, -0.45f);
                p.headPitch = -0.45f;
                p.capePitch += 0.15f;
            }
            case "spread_arms" -> {
                arm(p.leftArm, 0.35f, 1.3f);
                arm(p.rightArm, 0.35f, -1.3f);
                p.headPitch -= 0.12f;
            }
            case "point" -> aim(p.rightArm, m.point(), 0f);
            case "judge" -> {
                aim(p.rightArm, m.point(), 0.45f);
                arm(p.leftArm, 0.1f, 0.45f);
                p.torsoPitch += 0.06f;
                p.headPitch += 0.12f;
            }
            case "beckon" -> arm(p.rightArm, 1.35f + 0.4f * sin(time * 0.5f), -0.25f);
            case "bow" -> {
                p.torsoPitch = 0.62f;
                p.headPitch = 0.3f;
                p.headYaw *= 0.3f;
                arm(p.leftArm, 0.7f, 0.08f);
                arm(p.rightArm, 0.7f, -0.08f);
                p.capePitch = 0.08f;
            }
            case "speak" -> arm(p.rightArm, 0.8f + 0.12f * sin(time * 0.2f), -0.35f);
            case "cast" -> {
                arm(p.leftArm, 1.45f, 0.2f);
                arm(p.rightArm, 1.45f, -0.2f);
                p.capePitch += 0.2f;
            }
            case "slam" -> {
                // Both fists rise overhead, then come down hard.
                float up = smooth(k / 0.45f);
                float down = smooth((k - 0.48f) / 0.07f);
                float fwd = lerp(lerp(0.3f, 2.95f, up), 0.7f, down);
                arm(p.leftArm, fwd, 0.22f);
                arm(p.rightArm, fwd, -0.22f);
                p.torsoPitch = lerp(lerp(p.torsoPitch, -0.2f, up), 0.5f, down);
                p.headPitch = lerp(lerp(p.headPitch, -0.35f, up), 0.35f, down);
                // The cape flares on the way up and falls over the back as the body bends.
                p.capePitch = lerp(p.capePitch + 0.3f * up, -0.2f, down);
            }
            case "clap" -> {
                // Arms swing wide, then the palms meet in front of the chest.
                float open = smooth(k / 0.38f);
                float shut = smooth((k - 0.44f) / 0.09f);
                float fwd = lerp(0.25f, 1.42f, open);
                float out = lerp(lerp(0.1f, 0.75f, open), -0.36f, shut);
                arm(p.leftArm, fwd, out);
                arm(p.rightArm, fwd + 0.04f, -out);
                p.headPitch -= 0.1f * open;
            }
            case "summon" -> {
                // Palms lift slowly from low to high as power gathers.
                float rise = smooth(k);
                arm(p.leftArm, lerp(0.5f, 2.7f, rise), lerp(0.25f, 0.55f, rise));
                arm(p.rightArm, lerp(0.5f, 2.7f, rise), -lerp(0.25f, 0.55f, rise));
                p.headPitch = lerp(0.15f, -0.45f, rise);
                p.torsoPitch -= 0.1f * rise;
                p.capePitch += 0.3f * rise;
            }
            case "roar" -> {
                float in = smooth(k / 0.2f);
                arm(p.leftArm, lerp(0.1f, 0.55f, in), lerp(0.1f, 1.0f, in));
                arm(p.rightArm, lerp(0.1f, 0.55f, in), -lerp(0.1f, 1.0f, in));
                p.torsoPitch = lerp(p.torsoPitch, -0.22f, in);
                p.headPitch = lerp(p.headPitch, -0.55f, in) + 0.04f * sin(time * 2.5f);
                p.headYaw *= 1 - in;
                p.capePitch += in * (0.5f + 0.15f * sin(time * 0.8f));
            }
            case "laugh" -> {
                float shake = sin(time * 1.3f);
                p.headPitch = -0.38f + 0.07f * shake;
                p.torsoPitch = -0.1f + 0.05f * shake;
                arm(p.leftArm, -0.1f + 0.05f * shake, 0.32f);
                arm(p.rightArm, -0.1f + 0.05f * shake, -0.32f);
            }
            case "wave" -> arm(p.rightArm, 0.25f, -2.55f + 0.38f * sin(m.age() * 0.55f));
            case "nod" -> {
                float s = sin((float) (k * Math.PI * 2));
                p.headPitch = 0.3f * p.headPitch + 0.34f * s * s;
            }
            case "shake_head" -> {
                p.headYaw = 0.45f * sin((float) (k * Math.PI * 5)) * (1 - 0.5f * k);
                p.headPitch = 0.1f;
            }
            case "fold_arms" -> {
                arm(p.leftArm, 1.3f, -0.78f);
                arm(p.rightArm, 1.18f, 0.8f);
                p.torsoPitch -= 0.04f;
                p.headPitch -= 0.06f;
            }
            case "meditate" -> {
                // Seated in the air, legs crossed, hands resting on the knees, head bowed.
                p.leftLeg.rotationX(-1.5f).rotateZ(-0.42f);
                p.rightLeg.rotationX(-1.36f).rotateZ(0.42f);
                arm(p.leftArm, 0.62f, 0.12f);
                arm(p.rightArm, 0.62f, -0.12f);
                p.bodyPitch = 0;
                p.torsoPitch = 0.04f + 0.015f * sin(time * 0.04f);
                p.headPitch = 0.22f;
                p.headYaw *= 0.2f;
                p.capePitch = 0.05f;
            }
            default -> {
            }
        }
    }

    /** forward: how far the arm swings forward; outward: sideways, positive toward +x. */
    private static Quaternionf arm(Quaternionf q, float forward, float outward) {
        return q.rotationX(-forward).rotateZ(outward);
    }

    /** Points the arm along a direction (lowered by drop), or straight ahead without one. */
    private static void aim(Quaternionf q, Vector3f dir, float drop) {
        Vector3f d = dir == null || dir.lengthSquared() < 1e-4f ? new Vector3f(0, -0.1f, 1) : new Vector3f(dir);
        d.normalize();
        d.y -= drop;
        d.normalize();
        q.rotationTo(0, -1, 0, d.x, d.y, d.z);
    }

    // ---- Wings --------------------------------------------------------------------------------

    /** A point of a particle wing in body space (pixels); edge points trace its outline. */
    public record Dot(Vector3f pos, boolean edge) {
    }

    /** Points tracing a pair of wings on the upper back. They follow the torso and beat slowly. */
    public static List<Dot> wings(VesselMood.Wings style, float time, Map<Bone, Frame> frames) {
        List<Dot> out = new ArrayList<>();
        if (style == null || style == VesselMood.Wings.NONE) return out;
        boolean angel = style == VesselMood.Wings.ANGEL;
        Frame torso = frames.get(Bone.TORSO);
        float beat = sin(time * (angel ? 0.09f : 0.13f));
        float sweep = 0.5f - 0.22f * beat;
        float lift = 0.12f * beat;
        int bones = angel ? 7 : 4;
        float step = angel ? 2.6f : 2.3f;
        for (int side : new int[] { 1, -1 }) {
            Vector3f root = new Vector3f(1.6f * side, 9.5f, -2.5f);
            Vector3f[] tips = new Vector3f[bones];
            for (int k = 0; k < bones; k++) {
                float f = k / (float) (bones - 1);
                float ang = lerp(1.2f, -0.75f, f) + lift;
                float len = angel ? lerp(30f, 13f, f) : lerp(28f, 17f, f);
                float ox = (float) Math.cos(ang), oy = (float) Math.sin(ang);
                Vector3f dir = new Vector3f(side * ox * (float) Math.cos(sweep), oy,
                        -ox * (float) Math.sin(sweep) - 0.2f).normalize();
                for (float d = 3f; d <= len; d += step) {
                    out.add(dot(torso, new Vector3f(dir).mul(d).add(root), false));
                }
                tips[k] = new Vector3f(dir).mul(len).add(root);
            }
            // The trailing edge: a smooth sweep of feathers, or a bat's scalloped membrane.
            for (int k = 0; k < bones - 1; k++) {
                int n = Math.max(2, (int) (tips[k].distance(tips[k + 1]) / step));
                for (int s = 1; s < n; s++) {
                    float t = s / (float) n;
                    Vector3f q = new Vector3f(tips[k]).lerp(tips[k + 1], t);
                    if (!angel) {
                        q.lerp(root, 0.28f * (float) Math.sin(Math.PI * t));
                    }
                    out.add(dot(torso, q, true));
                }
            }
        }
        return out;
    }

    private static Dot dot(Frame torso, Vector3f p, boolean edge) {
        return new Dot(torso.rot().transform(p).add(torso.pos()), edge);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Eases 0..1 (input clamped). */
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
