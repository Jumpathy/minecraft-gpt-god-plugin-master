package net.bigyous.gptgodmc.god.living;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.bigyous.gptgodmc.god.living.ServantSkin.Def;
import net.bigyous.gptgodmc.god.living.ServantSkin.Shape;
import net.bigyous.gptgodmc.god.vessel.VesselModel;

/**
 * A servant's own body, the one players with the resource pack see instead of the vanilla mob: a seraph
 * in robes of light with four feathered wings and a sword, a cinder imp, a wraith with a scythe, a masked
 * hierophant with staff and book, a marble colossus with shield and halberd. Every piece wears a region of
 * the servant's painted atlas ({@link ServantSkin}, {@link ServantTextures}).
 *
 * <p>Body space in blocks: origin at the mob's feet, +y up, facing +z (its left is +x). Pure: always the
 * same pieces in the same order for a kind, so the runner updates them in place and tests can draw them.
 */
public final class ServantBody {
    /**
     * One piece: its atlas region, where its center is, its size (planes and glows: width, height, 0) and
     * how it is turned.
     */
    public record Part(String key, Shape shape, Vector3f center, Vector3f size, Quaternionf rot) {
    }

    /**
     * What it is doing.
     *
     * @param tick   ticks since it was summoned
     * @param moving whether it is travelling (wings beat harder, legs stride)
     * @param strike ticks since its last blow began, or -1 (a blow lasts {@link #STRIKE_TICKS})
     */
    public record Motion(long tick, boolean moving, int strike) {
        public static Motion idle(long tick) {
            return new Motion(tick, false, -1);
        }
    }

    /** How long a blow's swing lasts. */
    public static final int STRIKE_TICKS = 14;
    /** The seraph's wings against their painted size (32 texels to the block). */
    static final float ANGEL_WINGS = 0.8f;

    private ServantBody() {
    }

    public static List<Part> parts(ServantKind k, Motion m) {
        Rig r = new Rig(k);
        switch (k) {
            case ANGEL -> angel(r, m);
            case IMP -> imp(r, m);
            case SHADE -> shade(r, m);
            case PRIEST -> priest(r, m);
            case GUARD -> guard(r, m);
        }
        return r.out;
    }

    /** How high the body's top reaches (for the speech bubble). */
    public static float top(ServantKind k) {
        return switch (k) {
            case ANGEL -> 2.05f;
            case IMP -> 1.45f;
            case SHADE -> 2.55f;
            case PRIEST -> 2.75f;
            case GUARD -> 3.2f;
        };
    }

    // ---- The seraph -------------------------------------------------------------------------------------

    private static void angel(Rig r, Motion m) {
        float t = m.tick();
        float sway = sin(t * 0.07f);
        r.box("hem", 0, -0.17f, 0);
        r.box("skirt_low", 0, 0.243f, 0);
        r.box("skirt_up", 0, 0.6175f, 0);
        r.box("torso", 0, 1.071f, 0);
        r.box("mantle", 0, 1.33f, 0);
        r.box("head", 0, 1.583f, 0.01f);
        // The sword of light, point down before it, both hands on the grip; a blow raises and brings it down.
        Quaternionf swing = new Quaternionf().rotationX(swing(m.strike(), 0, -2.5f, -1.0f));
        Vector3f pivot = new Vector3f(0, 1.2f, 0.1f);
        r.box("grip", about(pivot, swing, 0, 0.9f, 0.27f), swing);
        r.box("hilt", about(pivot, swing, 0, 0.74f, 0.27f), swing);
        r.box("blade", about(pivot, swing, 0, 0.7f - r.def("blade").bh() / 2, 0.27f), swing);
        for (int side = 1; side >= -1; side -= 2) {
            r.hangTo("arm", new Vector3f(side * 0.33f, 1.3f, 0), about(pivot, swing, side * 0.05f, 0.9f, 0.27f));
        }
        // Great wings: an inner span from the shoulder and an outer fan of primaries from the wrist.
        int period = m.moving() ? 16 : 30;
        float beat = (float) (2 * Math.PI * (m.tick() % period) / period);
        float raise = m.moving() ? 0.30f + 0.50f * sin(beat) : 0.26f + 0.20f * sin(beat);
        float fold = m.moving() ? -0.10f + 0.42f * sin(beat - 1.0f) : -0.12f + 0.20f * sin(beat - 0.9f);
        float sweep = 0.38f;
        Vector3f shoulder = new Vector3f(0.12f, 1.24f, -0.15f);
        Quaternionf in = new Quaternionf().rotationY(sweep).rotateZ(raise);
        r.scale = ANGEL_WINGS;
        Vector3f wrist = in.transform(new Vector3f(30 / 32f, 2 / 32f, 0).mul(ANGEL_WINGS)).add(shoulder);
        Quaternionf out = new Quaternionf().rotationY(sweep + 0.12f).rotateZ(raise + fold);
        r.pair("wing_in", shoulder, in, 0, 6);
        r.pair("wing_out", wrist, out, 0, 5);
        // The lesser pair, folded down around the robe.
        r.pair("wing_low", new Vector3f(0.15f, 1.08f, -0.16f),
                new Quaternionf().rotationY(1.0f).rotateZ(-0.12f + 0.04f * sway), 2, 2);
        r.scale = 1;
        r.plane("veil", new Vector3f(0, 1.77f, -0.215f), new Quaternionf().rotationX(0.10f + 0.03f * sway), 7, 0);
        r.plane("halo", new Vector3f(0, 1.95f, -0.02f), new Quaternionf().rotationX((float) -Math.PI / 2 + 0.2f), 12,
                12);
        r.glow("nimbus", 1.58f, 1.5f, 1.5f);
    }

    // ---- The imp ----------------------------------------------------------------------------------------

    private static void imp(Rig r, Motion m) {
        float t = m.tick();
        float wob = sin(t * 0.2f), slow = sin(t * 0.09f);
        float lunge = swing(m.strike(), 0.0f, 1.0f, 0.5f);
        r.box("torso", new Vector3f(0, 0.5f, 0), new Quaternionf().rotationX(0.32f));
        r.box("head", new Vector3f(0, 0.88f, 0.1f), new Quaternionf().rotationX(0.1f + 0.04f * wob));
        // Horns curl out and back from the brow.
        Vector3f brow = new Vector3f(0.15f, 1.04f, 0.04f);
        Quaternionf horn = new Quaternionf().rotationZ(-0.5f).rotateX(-0.3f);
        Vector3f bend = horn.transform(new Vector3f(0, r.def("horn").bh() - 0.03f, 0)).add(brow);
        r.pairStand("horn", brow, horn);
        r.pairStand("horn_tip", bend, new Quaternionf().rotationZ(-1.0f).rotateX(-0.7f));
        r.pair("ear", new Vector3f(0.215f, 0.9f, 0), new Quaternionf().rotationY(0.6f).rotateZ(0.3f + 0.06f * wob), 0, 4);
        for (int side = 1; side >= -1; side -= 2) {
            // Claws held out before it; a blow rakes them forward.
            r.hangTo("arm", new Vector3f(side * 0.17f, 0.64f, 0.06f),
                    new Vector3f(side * (0.3f - 0.1f * lunge), 0.36f + 0.2f * lunge + 0.02f * wob * side,
                            0.36f + 0.12f * lunge));
        }
        for (int side = 1; side >= -1; side -= 2) {
            r.hangTo("leg", new Vector3f(side * 0.08f, 0.36f, -0.03f),
                    new Vector3f(side * 0.11f, 0.09f, -0.14f + 0.04f * wob * side));
        }
        // The tail hangs back, curls up and sways.
        Vector3f base = new Vector3f(0, 0.4f, -0.1f);
        Quaternionf first = aim(new Vector3f(0.18f * slow, -0.45f, -0.9f));
        Vector3f mid = first.transform(new Vector3f(0, -r.def("tail").bh() + 0.02f, 0)).add(base);
        Vector3f dir = new Vector3f(0.4f * slow, 0.3f, -0.9f).normalize();
        Quaternionf second = aim(dir);
        Vector3f end = second.transform(new Vector3f(0, -r.def("tail").bh() + 0.02f, 0)).add(mid);
        r.hang("tail", base, first);
        r.hang("tail", mid, second);
        r.plane("tail_tip", end, new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir).rotateY(0.8f), 4, 7.5f);
        // Membrane wings that beat fast.
        float beat = (float) (2 * Math.PI * (m.tick() % ServantRig.beatTicks(ServantKind.IMP))
                / ServantRig.beatTicks(ServantKind.IMP));
        float raise = 0.35f + 0.55f * sin(beat), fold = -0.25f + 0.5f * sin(beat - 1.0f);
        Vector3f shoulder = new Vector3f(0.07f, 0.68f, -0.1f);
        Quaternionf in = new Quaternionf().rotationY(0.45f).rotateZ(raise);
        Vector3f wrist = in.transform(new Vector3f(18 / 32f, 2 / 32f, 0)).add(shoulder);
        r.pair("wing_in", shoulder, in, 0, 4);
        r.pair("wing_out", wrist, new Quaternionf().rotationY(0.55f).rotateZ(raise + fold), 0, 4);
        r.glow("flame", 1.23f, 0.26f, 0.36f);
        r.glow("glow", 0.72f, 1.25f, 1.25f);
    }

    // ---- The wraith ------------------------------------------------------------------------------------

    private static void shade(Rig r, Motion m) {
        float t = m.tick();
        float sway = sin(t * 0.06f);
        r.planeAt("smoke", new Vector3f(0, 0.22f, 0), new Quaternionf());
        r.planeAt("smoke", new Vector3f(0, 0.22f, 0), new Quaternionf().rotationY((float) Math.PI / 2));
        r.box("skirt", 0, 0.549f, 0);
        r.box("torso", 0, 1.324f, 0);
        r.box("mantle", 0, 1.655f, 0);
        r.box("face", 0, 1.955f, 0);
        r.box("hood", 0, 1.97f, 0.02f);
        r.stand("hood_peak", new Vector3f(0, 2.13f, -0.19f), new Quaternionf().rotationX(-1.9f));
        r.plane("cloak", new Vector3f(0, 1.74f, -0.27f), new Quaternionf().rotationX(0.12f + 0.05f * sway), 16, 0);
        // The left hand reaches, open, for the living; the right is closed round the scythe.
        Vector3f left = new Vector3f(0.32f, 1.12f + 0.03f * sway, 0.44f);
        Quaternionf la = r.hangTo("arm", new Vector3f(0.33f, 1.6f, 0.02f), left);
        Vector3f shoulder = new Vector3f(-0.33f, 1.6f, 0.02f);
        Quaternionf ra = aim(new Vector3f(-0.36f, 1.1f, 0.38f).sub(shoulder));
        // It grips where the sleeve ends: the fist comes out of the cuff with the shaft through it.
        Vector3f grip = r.hang("arm", shoulder, ra).add(ra.transform(new Vector3f(0, -0.03f, 0)));
        r.plane("hand", left, la, 4.5f, 0);
        Quaternionf chop = new Quaternionf().rotationX(swing(m.strike(), 0, -0.5f, 1.3f));
        Vector3f up = chop.transform(new Vector3f(-0.10f, 1, 0.16f).normalize());
        Quaternionf pole = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), up);
        float len = r.def("shaft").bh();
        r.box("fist", grip, pole);
        r.box("shaft", new Vector3f(up).mul(0.25f).add(grip), pole);
        r.plane("scythe", new Vector3f(up).mul(0.25f + len / 2 - 0.06f).add(grip), pole, 1, 4);
        // Three souls circle it, each two crossed planes so it shows from every side.
        for (int i = 0; i < 3; i++) {
            float a = t * 0.06f + i * (float) (2 * Math.PI / 3);
            Vector3f at = new Vector3f(0.62f * (float) Math.cos(a), 0.75f + 0.5f * i + 0.08f * sin(t * 0.1f + i),
                    0.62f * (float) Math.sin(a));
            r.planeAt("wisp", at, new Quaternionf().rotationY(-a));
            r.planeAt("wisp", at, new Quaternionf().rotationY(-a + (float) Math.PI / 2));
        }
        r.glow("glow", 1.95f, 1.0f, 1.0f);
    }

    // ---- The hierophant ---------------------------------------------------------------------------------

    private static void priest(Rig r, Motion m) {
        float t = m.tick();
        r.box("hem", 0, 0.11f, 0);
        r.box("skirt_low", 0, 0.2845f, 0);
        r.box("skirt_up", 0, 0.703f, 0);
        r.box("torso", 0, 1.171f, 0);
        r.box("mantle", 0, 1.45f, 0);
        r.box("head", 0, 1.673f, 0.01f);
        r.box("mitre_low", 0, 1.985f, 0);
        r.box("mitre_top", 0, 2.215f, 0);
        r.planeAt("disc", new Vector3f(0, 1.72f, -0.24f), new Quaternionf().rotationX(-0.06f));
        // The right hand holds the staff; the left is raised under a floating book.
        float lift = 0.03f * sin(t * 0.08f);
        r.hangTo("arm", new Vector3f(0.32f, 1.4f, 0), new Vector3f(0.44f, 1.2f + lift, 0.36f));
        r.hangTo("arm", new Vector3f(-0.32f, 1.4f, 0), new Vector3f(-0.4f, 1.0f, 0.3f));
        r.box("shaft", -0.4f, 0.05f + r.def("shaft").bh() / 2, 0.3f);
        // The staff's head: a sun seen from every side, with its light behind it.
        r.planeAt("sun", new Vector3f(-0.4f, 2.44f, 0.3f), new Quaternionf());
        r.planeAt("sun", new Vector3f(-0.4f, 2.44f, 0.3f), new Quaternionf().rotationY((float) Math.PI / 2));
        r.planeAt("glow", new Vector3f(-0.4f, 2.44f, 0.288f), new Quaternionf());
        r.box("book", new Vector3f(0.44f, 1.33f + lift * 1.5f, 0.5f), new Quaternionf().rotationX(0.9f));
        // Its words circle it, each letter turning slowly in the air like a coin.
        for (int i = 0; i < 4; i++) {
            float a = t * 0.035f + i * (float) (Math.PI / 2);
            r.planeAt("glyph_" + i, new Vector3f(0.82f * (float) Math.cos(a),
                    1.15f + 0.3f * sin(t * 0.05f + i * 1.7f), 0.82f * (float) Math.sin(a)),
                    new Quaternionf().rotationY(t * 0.06f + i * 1.3f + 0.5f));
        }
    }

    // ---- The colossus ----------------------------------------------------------------------------------

    private static void guard(Rig r, Motion m) {
        float t = m.tick();
        float walk = m.moving() ? sin(t * 0.35f) : 0;
        float sway = sin(t * 0.05f);
        float blow = swing(m.strike(), 0, -0.35f, 1.0f);
        r.hang("leg", new Vector3f(0.25f, 0.9375f, 0), new Quaternionf().rotationX(-0.45f * walk));
        r.hang("leg", new Vector3f(-0.25f, 0.9375f, 0), new Quaternionf().rotationX(0.45f * walk));
        r.box("fauld", 0, 1.04f, 0);
        r.plane("tabard", new Vector3f(0, 0.95f, 0.265f), new Quaternionf().rotationX(-0.06f - 0.12f * Math.abs(walk)),
                6.5f, 0);
        r.box("torso", 0, 1.566f, 0);
        r.box("helm", 0, 2.19f, 0.03f);
        r.planeAt("crest", new Vector3f(0, 2.52f, -0.05f), new Quaternionf().rotationY((float) Math.PI / 2));
        r.box("pauldron", new Vector3f(0.67f, 1.97f, 0), new Quaternionf().rotationZ(-0.2f));
        r.box("pauldron", new Vector3f(-0.67f, 1.97f, 0), new Quaternionf().rotationZ(0.2f));
        // The shield arm.
        Quaternionf lu = new Quaternionf().rotationX(-0.25f - 0.12f * walk);
        Vector3f le = r.hang("arm_up", new Vector3f(0.66f, 1.84f, 0), lu);
        r.hang("gauntlet", le, new Quaternionf().rotationX(-1.0f - 0.1f * walk));
        r.box("shield", new Vector3f(0.7f, 1.22f, 0.9f), new Quaternionf().rotationY(0.28f));
        // The halberd arm; a blow tips the halberd forward and down.
        Quaternionf ru = new Quaternionf().rotationX(-0.2f + 0.12f * walk - 0.5f * blow);
        Vector3f re = r.hang("arm_up", new Vector3f(-0.66f, 1.84f, 0), ru);
        Quaternionf rf = new Quaternionf().rotationX(-0.8f - 0.4f * blow);
        Vector3f fist = r.hang("gauntlet", re, rf);
        fist.add(rf.transform(new Vector3f(0, 0.1f, 0)));
        Quaternionf tip = new Quaternionf().rotationX(1.25f * blow);
        Vector3f up = tip.transform(new Vector3f(0, 1, 0));
        float len = r.def("shaft").bh();
        // Upright, its butt on the ground; in a blow it pivots at the fist.
        float below = fist.y - 0.02f;
        r.box("shaft", new Vector3f(up).mul(len / 2 - below).add(fist), tip);
        r.plane("blade", new Vector3f(up).mul(len - below - 0.32f).add(fist), tip, 11, 22);
        r.plane("cape", new Vector3f(0, 1.94f, -0.32f),
                new Quaternionf().rotationX(0.1f + 0.04f * sway + 0.14f * Math.abs(walk)), 16.5f, 0);
        // The core's light, spilling over the breastplate.
        r.planeAt("glow", new Vector3f(0, 1.6f, 0.31f), new Quaternionf());
        // The ground it holds, marked in light that slowly turns.
        r.scale = 1.25f;
        r.planeAt("ward", new Vector3f(0, 0.04f, 0),
                new Quaternionf().rotationX((float) -Math.PI / 2).rotateZ(t * 0.02f));
        r.scale = 1;
    }

    // ---- Building blocks ---------------------------------------------------------------------------------

    private static final class Rig {
        final ServantKind kind;
        final List<Part> out = new ArrayList<>();
        /** Planes are placed at this many times their painted size. */
        float scale = 1;

        Rig(ServantKind kind) {
            this.kind = kind;
        }

        Def def(String key) {
            return ServantSkin.def(kind, key);
        }

        private Vector3f size(Def d) {
            return new Vector3f(d.bw(), d.bh(), d.bd());
        }

        void box(String key, float cx, float cy, float cz) {
            box(key, new Vector3f(cx, cy, cz), new Quaternionf());
        }

        void box(String key, Vector3f center, Quaternionf rot) {
            Def d = def(key);
            out.add(new Part(key, d.shape(), new Vector3f(center), size(d), new Quaternionf(rot)));
        }

        /** A box hanging from the middle of its top; returns the middle of its bottom. */
        Vector3f hang(String key, Vector3f pivot, Quaternionf rot) {
            float h = def(key).bh();
            box(key, rot.transform(new Vector3f(0, -h / 2, 0)).add(pivot), rot);
            return rot.transform(new Vector3f(0, -h, 0)).add(pivot);
        }

        /** A box standing on the middle of its bottom. */
        void stand(String key, Vector3f pivot, Quaternionf rot) {
            float h = def(key).bh();
            box(key, rot.transform(new Vector3f(0, h / 2, 0)).add(pivot), rot);
        }

        /** A limb from a joint toward a point; returns how it is turned. */
        Quaternionf hangTo(String key, Vector3f pivot, Vector3f toward) {
            Quaternionf rot = aim(new Vector3f(toward).sub(pivot));
            hang(key, pivot, rot);
            return rot;
        }

        /** A plane whose texel (hx, hy), counted from its top-left corner, sits at a point. */
        void plane(String key, Vector3f at, Quaternionf rot, float hx, float hy) {
            Def d = def(key);
            Vector3f local = new Vector3f((hx - d.w() / 2f) / ServantSkin.TEXELS, (d.h() / 2f - hy) / ServantSkin.TEXELS,
                    0).mul(scale);
            out.add(new Part(key, d.shape(), new Vector3f(at).sub(rot.transform(local)),
                    new Vector3f(d.bw() * scale, d.bh() * scale, 0), new Quaternionf(rot)));
        }

        void planeAt(String key, Vector3f center, Quaternionf rot) {
            Def d = def(key);
            plane(key, center, rot, d.w() / 2f, d.h() / 2f);
        }

        /** The same plane on the left (as given) and mirrored on the right. */
        void pair(String key, Vector3f at, Quaternionf rot, float hx, float hy) {
            plane(key, at, rot, hx, hy);
            out.add(mirror(out.get(out.size() - 1)));
        }

        void pairStand(String key, Vector3f pivot, Quaternionf rot) {
            stand(key, pivot, rot);
            out.add(mirror(out.get(out.size() - 1)));
        }

        /**
         * A soft light at a height on the body's axis that turns (upright) to face the viewer. It cannot
         * be set off to a side: the client applies a billboard's offset in the camera's frame, so only an
         * offset straight up stays where it is put.
         */
        void glow(String key, float y, float w, float h) {
            out.add(new Part(key, Shape.GLOW, new Vector3f(0, y, 0), new Vector3f(w, h, 0), new Quaternionf()));
        }
    }

    /** The same piece on the other side (mirrored across x = 0); a plane's picture is mirrored with it. */
    static Part mirror(Part p) {
        Quaternionf q = p.rot();
        Quaternionf m = new Quaternionf(q.x, -q.y, -q.z, q.w);
        if (p.shape() == Shape.PLANE) m.rotateY((float) Math.PI);
        return new Part(p.key(), p.shape(), new Vector3f(-p.center().x, p.center().y, p.center().z),
                new Vector3f(p.size()), m);
    }

    /** The turn that points a hanging limb (its -y) along a direction. */
    static Quaternionf aim(Vector3f dir) {
        return new Quaternionf().rotationTo(new Vector3f(0, -1, 0), new Vector3f(dir).normalize());
    }

    /** A point (x, y, z) turned about a pivot. */
    private static Vector3f about(Vector3f pivot, Quaternionf q, float x, float y, float z) {
        return q.transform(new Vector3f(x, y, z).sub(pivot)).add(pivot);
    }

    /**
     * A blow's angle through its {@link #STRIKE_TICKS}: from rest it winds to back, snaps to hit, and
     * eases home.
     */
    static float swing(int strike, float rest, float back, float hit) {
        if (strike < 0 || strike >= STRIKE_TICKS) return rest;
        if (strike < 4) return lerp(rest, back, smooth(strike / 4f));
        if (strike < 7) return lerp(back, hit, smooth((strike - 4) / 3f));
        return lerp(hit, rest, smooth((strike - 7) / (float) (STRIKE_TICKS - 7)));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smooth(float t) {
        float c = Math.max(0, Math.min(1, t));
        return c * c * (3 - 2 * c);
    }

    private static float sin(float a) {
        return (float) Math.sin(a);
    }

    // ---- Display transformation ---------------------------------------------------------------------------

    /**
     * The item display transformation of a piece for a body facing yaw (Minecraft degrees), relative to
     * an entity at the servant's feet with yaw 0. Item models are drawn centered and turned half a circle,
     * which the right rotation undoes. Glows are upright billboards (Billboard.VERTICAL) on the body's
     * axis, so they are only raised and sized.
     */
    public static Transformation transform(Part p, float yaw) {
        Quaternionf turn = new Quaternionf().rotationY((float) Math.toRadians(-yaw));
        Vector3f at = turn.transform(new Vector3f(p.center()));
        if (p.shape() == Shape.GLOW) {
            return new Transformation(at, new Quaternionf(), new Vector3f(p.size().x, p.size().y, 1),
                    new Quaternionf(VesselModel.ITEM_FLIP));
        }
        Vector3f scale = p.shape() == Shape.PLANE ? new Vector3f(p.size().x, p.size().y, 1) : new Vector3f(p.size());
        return new Transformation(at, turn.mul(p.rot(), new Quaternionf()), scale,
                new Quaternionf(VesselModel.ITEM_FLIP));
    }
}
