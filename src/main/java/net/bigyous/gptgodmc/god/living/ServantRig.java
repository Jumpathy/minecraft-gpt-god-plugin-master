package net.bigyous.gptgodmc.god.living;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.bigyous.gptgodmc.god.DemigodWings;

/**
 * The display-entity pieces a servant wears over its vanilla mob: an angel's feathered wings and halo
 * (the demigod wings, scaled to it), an imp's membrane wings and horns, a shade's hood and tattered cloak,
 * a guard's golden crest and pauldrons, a priest's halo. Body space in blocks: origin at the mob's feet,
 * +y up, facing +z (left = +x). Pure, so tests can check and render it. Always the same pieces in the same
 * order for a kind, so the runner can update them in place.
 */
public final class ServantRig {
    /** One box: what it is (for the pack and the client mod), its block, center, size and rotation. */
    public record Piece(String part, Material material, Vector3f center, Vector3f size, Quaternionf rot) {
    }

    /** Ticks per wingbeat while hovering. */
    public static int beatTicks(ServantKind k) {
        return k == ServantKind.IMP ? 8 : 26;
    }

    private ServantRig() {
    }

    /**
     * Every piece at a moment.
     *
     * @param tick   ticks since it was summoned (drives the wingbeat and the halo's bob)
     * @param moving whether it is moving (wings beat harder)
     */
    public static List<Piece> pieces(ServantKind k, long tick, boolean moving) {
        List<Piece> out = new ArrayList<>();
        if (k.wings != null) wings(out, k, tick, moving);
        if (k.halo) halo(out, k, tick);
        if (k.horns) horns(out, k);
        if (k.cloak) cloak(out, k, tick);
        if (k == ServantKind.GUARD) crest(out, k);
        return out;
    }

    // ---- Wings ------------------------------------------------------------------------------------------

    /** Where its wings meet its back, and how large they are against a player's. */
    static Vector3f wingRoot(ServantKind k) {
        return switch (k) {
            case IMP -> new Vector3f(0.06f, 0.78f, -0.1f);
            default -> new Vector3f(0.1f, 0.98f, -0.16f);
        };
    }

    static float wingScale(ServantKind k) {
        return k == ServantKind.IMP ? 0.62f : 1.05f;
    }

    private static void wings(List<Piece> out, ServantKind k, long tick, boolean moving) {
        float beat = (float) (2 * Math.PI * (tick % beatTicks(k)) / beatTicks(k));
        DemigodWings.Pose pose = DemigodWings.pose(1, beat, moving || k.flies, false, false, false, 0);
        if (!moving) {
            // Hovering in place: a slower, shallower beat.
            pose = new DemigodWings.Pose(pose.open(), 0.2f + 0.35f * (float) Math.sin(beat), pose.sweep() + 0.1f, 0, 0);
        }
        float f = wingScale(k);
        Vector3f root = wingRoot(k);
        for (DemigodWings.Piece p : DemigodWings.wings(pose, k.wings)) {
            Vector3f c = new Vector3f(p.center()).sub(sign(p.center().x) * DemigodWings.ROOT.x, DemigodWings.ROOT.y,
                    DemigodWings.ROOT.z).mul(f).add(sign(p.center().x) * root.x, root.y, root.z);
            out.add(new Piece("wing_" + p.kind().name().toLowerCase(java.util.Locale.ROOT), p.material(), c,
                    new Vector3f(p.size()).mul(f), new Quaternionf(p.rot())));
        }
    }

    private static float sign(float x) {
        return x < 0 ? -1 : 1;
    }

    // ---- Halo, horns, cloak, crest ------------------------------------------------------------------------

    static final int HALO_SEGMENTS = 8;

    private static void halo(List<Piece> out, ServantKind k, long tick) {
        float r = k == ServantKind.ANGEL ? 0.3f : 0.24f;
        float y = (float) k.height() + 0.18f + 0.03f * (float) Math.sin(tick * 0.1);
        Quaternionf tilt = new Quaternionf().rotationX(0.24f);
        float len = (float) (2 * Math.PI * r / HALO_SEGMENTS) + 0.03f;
        for (int i = 0; i < HALO_SEGMENTS; i++) {
            float a = (float) (2 * Math.PI * (i + 0.5) / HALO_SEGMENTS);
            Vector3f at = tilt.transform(new Vector3f(r * (float) Math.cos(a), 0, r * (float) Math.sin(a)))
                    .add(0, y, -0.04f);
            out.add(new Piece("halo", Material.GOLD_BLOCK, at, new Vector3f(0.055f, 0.055f, len),
                    new Quaternionf(tilt).rotateY(-a)));
        }
    }

    private static void horns(List<Piece> out, ServantKind k) {
        float top = (float) k.height() - 0.02f;
        for (int side = -1; side <= 1; side += 2) {
            // Curling out and back from the top of the head: a thick base and a dark tip.
            out.add(new Piece("horn", Material.RED_CONCRETE, new Vector3f(side * 0.24f, top + 0.08f, 0.02f),
                    new Vector3f(0.11f, 0.3f, 0.11f), new Quaternionf().rotationZ(-side * 0.55f).rotateX(-0.3f)));
            out.add(new Piece("horn_tip", Material.BLACK_CONCRETE, new Vector3f(side * 0.36f, top + 0.27f, -0.05f),
                    new Vector3f(0.07f, 0.2f, 0.07f), new Quaternionf().rotationZ(-side * 1.0f).rotateX(-0.55f)));
        }
    }

    private static void cloak(List<Piece> out, ServantKind k, long tick) {
        float h = (float) k.height();
        Material cloth = Material.BLACK_CONCRETE, edge = Material.CRYING_OBSIDIAN;
        // Hood: a box open at the face, a little larger than the skull.
        float head = h - 0.33f;
        out.add(new Piece("hood", cloth, new Vector3f(0, head + 0.34f, -0.02f), new Vector3f(0.74f, 0.07f, 0.74f),
                new Quaternionf()));
        out.add(new Piece("hood", cloth, new Vector3f(0.36f, head, -0.02f), new Vector3f(0.06f, 0.66f, 0.74f),
                new Quaternionf()));
        out.add(new Piece("hood", cloth, new Vector3f(-0.36f, head, -0.02f), new Vector3f(0.06f, 0.66f, 0.74f),
                new Quaternionf()));
        out.add(new Piece("hood", cloth, new Vector3f(0, head, -0.37f), new Vector3f(0.74f, 0.66f, 0.06f),
                new Quaternionf()));
        // Pale soul-fire eyes glowing in the dark of the hood.
        for (int side = -1; side <= 1; side += 2) {
            out.add(new Piece("eye", Material.SEA_LANTERN, new Vector3f(side * 0.12f, head + 0.04f, 0.31f),
                    new Vector3f(0.11f, 0.06f, 0.02f), new Quaternionf()));
        }
        // Cloak: a back panel and four tatters that sway.
        float sway = 0.08f * (float) Math.sin(tick * 0.12);
        out.add(new Piece("cloak", cloth, new Vector3f(0, h * 0.5f, -0.3f), new Vector3f(0.86f, h * 0.62f, 0.05f),
                new Quaternionf().rotationX(0.08f + sway)));
        for (int i = 0; i < 4; i++) {
            float x = -0.33f + i * 0.22f;
            float len = 0.5f + 0.12f * (i % 2);
            Quaternionf r = new Quaternionf().rotationX(0.12f + sway * (1 + i % 2)).rotateZ(0.05f * (i - 1.5f));
            out.add(new Piece("tatter", i % 2 == 0 ? cloth : edge, new Vector3f(x, h * 0.16f, -0.33f - 0.02f * i),
                    new Vector3f(0.16f, len, 0.04f), r));
        }
    }

    private static void crest(List<Piece> out, ServantKind k) {
        float h = (float) k.height();
        out.add(new Piece("crest", Material.GOLD_BLOCK, new Vector3f(0, h + 0.08f, -0.05f),
                new Vector3f(0.09f, 0.22f, 0.66f), new Quaternionf()));
        out.add(new Piece("plume", Material.RED_WOOL, new Vector3f(0, h + 0.14f, -0.42f),
                new Vector3f(0.08f, 0.34f, 0.3f), new Quaternionf().rotationX(-0.6f)));
        for (int side = -1; side <= 1; side += 2) {
            out.add(new Piece("pauldron", Material.GOLD_BLOCK, new Vector3f(side * 0.72f, h * 0.76f, 0),
                    new Vector3f(0.42f, 0.16f, 0.56f), new Quaternionf().rotationZ(side * 0.25f)));
        }
    }

    // ---- Display transformation ---------------------------------------------------------------------------

    /** The block display transformation of a piece for a body facing yaw (Minecraft degrees). */
    public static Transformation transform(Piece p, float yaw) {
        Quaternionf turn = new Quaternionf().rotationY((float) Math.toRadians(-yaw));
        Quaternionf rot = turn.mul(p.rot(), new Quaternionf());
        Vector3f half = new Vector3f(p.size()).mul(0.5f);
        Vector3f corner = turn.transform(new Vector3f(p.center())).sub(rot.transform(half));
        return new Transformation(corner, rot, new Vector3f(p.size()), new Quaternionf());
    }
}
