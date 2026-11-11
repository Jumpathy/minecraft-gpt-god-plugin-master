package net.bigyous.gptgodmc.god.vision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.DemigodWings;
import net.bigyous.gptgodmc.god.Demigods;
import net.bigyous.gptgodmc.god.vessel.VesselLook;
import net.bigyous.gptgodmc.god.vessel.VesselModel;
import net.bigyous.gptgodmc.god.vessel.VesselMood;
import net.bigyous.gptgodmc.god.vessel.VesselSkin;
import net.bigyous.gptgodmc.god.vessel.VesselWings;
import net.bigyous.gptgodmc.god.vessel.VesselTextures;

/**
 * A demigod's display-entity wings and halo on a mortal-sized stand-in, beside the old particle
 * wings, drawn to build/test-renders/demigod-wings.png; plus checks on the geometry.
 */
class DemigodWingsPreviewTest {
    record Shot(String label, DemigodWings.Pose pose, float yaw) {
    }

    private static DemigodWings.Pose pose(float open, float beat, boolean flying, boolean gliding) {
        return DemigodWings.pose(open, beat, flying, gliding, false, false, 0);
    }

    private static List<DemigodWings.Piece> pieces(DemigodWings.Pose pose) {
        return DemigodWings.pieces(pose);
    }

    // ---- Geometry -----------------------------------------------------------------------------------

    @Test
    void fewPiecesAlwaysInTheSameOrder() {
        for (DemigodWings.Pose p : List.of(pose(0, 0, false, false), pose(1, 1, true, false), pose(1, 0, false, true),
                DemigodWings.pose(0, 0, false, false, false, true, 0))) {
            List<DemigodWings.Piece> parts = pieces(p);
            assertEquals(DemigodWings.COUNT, parts.size());
            assertTrue(parts.size() <= 30, "keep it light: " + parts.size());
            List<DemigodWings.Kind> kinds = pieces(pose(0, 0, false, false)).stream().map(DemigodWings.Piece::kind)
                    .toList();
            assertEquals(kinds, parts.stream().map(DemigodWings.Piece::kind).toList());
        }
    }

    @Test
    void wingsAreSymmetricAndBehindTheBack() {
        for (DemigodWings.Pose p : List.of(pose(0, 0, false, false), pose(1, 1.2f, true, false),
                pose(1, -1.2f, true, false))) {
            List<DemigodWings.Piece> parts = pieces(p);
            int half = (DemigodWings.COUNT - DemigodWings.HALO_SEGMENTS) / 2;
            for (int i = 0; i < half; i++) {
                Vector3f l = parts.get(i).center(), r = parts.get(i + half).center();
                assertEquals(l.x, -r.x, 1e-5);
                assertEquals(l.y, r.y, 1e-5);
                assertEquals(l.z, r.z, 1e-5);
                assertTrue(l.x > 0, "left wing on the left: " + l);
                assertTrue(l.z < -0.1f, "wing behind the back: " + l);
            }
        }
    }

    @Test
    void spreadWingsAreWideAndFoldedOnesHugTheBack() {
        float folded = span(pieces(pose(0, 0, false, false)));
        float spread = span(pieces(pose(1, 0, true, false)));
        assertTrue(folded < 1.2f, "folded wings too wide: " + folded);
        assertTrue(spread > 3.0f, "spread wings too narrow: " + spread);
        // A wingbeat moves the tips up and down.
        float up = tipY(pieces(pose(1, (float) Math.PI / 2, true, false)));
        float down = tipY(pieces(pose(1, (float) -Math.PI / 2, true, false)));
        assertTrue(up - down > 0.6f, "the beat is too small: " + up + " vs " + down);
    }

    @Test
    void haloFloatsAboveTheHead() {
        List<DemigodWings.Piece> parts = pieces(pose(0, 0, false, false));
        for (DemigodWings.Piece p : parts.subList(parts.size() - DemigodWings.HALO_SEGMENTS, parts.size())) {
            assertEquals(DemigodWings.Kind.HALO, p.kind());
            assertTrue(p.center().y > 1.95f && p.center().y < 2.35f, "halo height " + p.center());
            assertTrue(Math.hypot(p.center().x, p.center().z + 0.05) < 0.35, "halo radius " + p.center());
        }
    }

    @Test
    void transformsTurnWithTheBody() {
        DemigodWings.Piece arm = pieces(pose(1, 0, true, false)).get(0);
        // Facing south (yaw 0) the wings are north of the feet; facing east (yaw -90) they are west.
        assertTrue(center(DemigodWings.transform(arm, 0)).z < 0);
        assertTrue(center(DemigodWings.transform(arm, -90)).x < 0);
        Transformation a = DemigodWings.transform(arm, 10), b = DemigodWings.transform(arm, 10);
        assertTrue(!DemigodWings.differs(a, b));
        assertTrue(DemigodWings.differs(a, DemigodWings.transform(arm, 12)));
    }

    @Test
    void glidingLaysTheRigAlongTheLook() {
        // Gliding level (pitch 0), "up" in body space points forward: the halo is ahead of the feet.
        List<DemigodWings.Piece> parts = DemigodWings.pieces(DemigodWings.pose(1, 0, false, true, false, false, 0));
        DemigodWings.Piece halo = parts.get(parts.size() - 1);
        assertTrue(halo.center().z > 1.8f && Math.abs(halo.center().y) < 0.5f, "gliding halo " + halo.center());
    }

    private static Vector3f center(Transformation t) {
        Vector3f c = new Vector3f(t.getScale()).mul(0.5f);
        return t.getLeftRotation().transform(c).add(t.getTranslation());
    }

    private static float span(List<DemigodWings.Piece> parts) {
        float max = 0;
        for (DemigodWings.Piece p : parts) {
            for (Vector3f c : corners(p)) max = Math.max(max, Math.abs(c.x));
        }
        return 2 * max;
    }

    private static float tipY(List<DemigodWings.Piece> parts) {
        float y = 0, best = -1;
        for (DemigodWings.Piece p : parts) {
            for (Vector3f c : corners(p)) {
                if (c.x > best) {
                    best = c.x;
                    y = c.y;
                }
            }
        }
        return y;
    }

    private static List<Vector3f> corners(DemigodWings.Piece p) {
        List<Vector3f> out = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Vector3f c = new Vector3f((i & 1) - 0.5f, ((i >> 1) & 1) - 0.5f, ((i >> 2) & 1) - 0.5f).mul(p.size());
            out.add(p.rot().transform(c).add(p.center()));
        }
        return out;
    }

    // ---- Preview -----------------------------------------------------------------------------------

    /** A hero standing in for the player: the warrior look at a mortal's height, with no helm or cloak. */
    private static List<VesselModel.Placed> standIn(VesselModel.Pose pose) {
        VesselLook w = VesselLook.resolve("warrior", null);
        VesselLook look = new VesselLook(w.name(), w.materials(), VesselLook.Ornament.NONE, false, w.outline(),
                w.mote(), w.smokeFrom(), w.smokeTo(), w.sparkle());
        return new ArrayList<>(VesselModel.place(look, pose, VesselModel.scaleForSize(1.0)));
    }

    /** The rig as placed boxes the preview rasterizer understands (untextured, block colors). */
    private static List<VesselModel.Placed> rig(DemigodWings.Pose pose) {
        List<VesselModel.Placed> out = new ArrayList<>();
        for (DemigodWings.Piece p : DemigodWings.pieces(pose)) {
            VesselModel.Part part = new VesselModel.Part(VesselModel.Bone.BODY, VesselLook.Slot.ORNAMENT,
                    VesselSkin.Piece.HORN, p.center(), p.size(), p.rot());
            out.add(new VesselModel.Placed(p.material(), DemigodWings.transform(p, 0), VesselModel.Bone.BODY, part));
        }
        return out;
    }

    /** The old particle wings and halo, as sparks. */
    private static List<VesselPreviewTest.Spark> particles(boolean flying, double tick) {
        double flap = flying ? Math.sin(tick * 0.9) : -0.6;
        List<VesselPreviewTest.Spark> out = new ArrayList<>();
        for (Vector v : Demigods.wing(flap, flying ? 7 : 5)) {
            out.add(new VesselPreviewTest.Spark(new Vector3f((float) v.getX(), (float) v.getY(), (float) v.getZ()),
                    v.getY() > 1.9 ? 0xFFF6D8 : 0xFFD86B));
        }
        for (int k = 0; k < 10; k++) {
            double a = k * Math.PI * 2 / 10;
            out.add(new VesselPreviewTest.Spark(
                    new Vector3f((float) (Math.cos(a) * 0.32), 2.25f, (float) (Math.sin(a) * 0.32)), 0xFFD86B));
        }
        return out;
    }

    @Test
    void renderDemigodWings() throws IOException {
        int bg = 0x17111F;
        double ppb = 84;
        VesselModel.Pose standing = VesselModel.pose("idle", 0, 0, 0, null, null);
        VesselModel.Pose flying = VesselModel.pose(new VesselModel.Motion("idle", 0, 37, 0, 0, 1, 0,
                net.bigyous.gptgodmc.god.vessel.VesselMood.CALM, null, null));
        record Row(String label, VesselModel.Pose body, DemigodWings.Pose wings, boolean flying, double lift) {
        }
        List<Row> rows = List.of(
                new Row("folded (standing)", standing, pose(0, 0, false, false), false, 0),
                new Row("flying, upstroke", flying, pose(1, (float) Math.PI / 2, true, false), true, 0.35),
                new Row("flying, downstroke", flying, pose(1, (float) -Math.PI / 2, true, false), true, 0.35),
                new Row("hovering, mid-beat", flying, pose(1, 0, true, false), true, 0.35));
        List<VesselPreviewTest.View> views = List.of(VesselPreviewTest.VIEWS.get(0), VesselPreviewTest.VIEWS.get(1),
                VesselPreviewTest.VIEWS.get(3));
        int cols = views.size() + 1;
        int w = VesselPreviewTest.CELL_W, h = VesselPreviewTest.CELL_H;
        BufferedImage sheet = new BufferedImage(w * cols, h * rows.size(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        VesselTextures.Atlas atlas = VesselTextures.atlas("warrior");
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            List<VesselModel.Placed> parts = standIn(row.body());
            parts.addAll(rig(row.wings()));
            for (int c = 0; c < views.size(); c++) {
                g.drawImage(VesselPreviewTest.render(parts, views.get(c), bg, List.of(), row.lift(), ppb, atlas, 0,
                        null), c * w, r * h, null);
                VesselPreviewTest.label(g, "NEW " + row.label() + " / " + views.get(c).name(), c * w, r * h);
            }
            // The old particle version from behind, for comparison.
            g.drawImage(VesselPreviewTest.render(standIn(row.body()), views.get(2), bg,
                    particles(row.flying(), r * 1.7), row.lift(), ppb, atlas, 0, null), views.size() * w, r * h, null);
            VesselPreviewTest.label(g, "OLD particles / back", views.size() * w, r * h);
        }
        g.dispose();
        RendererTest.save(sheet, "demigod-wings");
        assertTrue(new java.io.File(RendererTest.OUT, "demigod-wings.png").isFile());
    }

    // ---- The god's wings ----------------------------------------------------------------------------

    @Test
    void vesselWingsRideTheTorsoAndComeWithMoods() {
        assertEquals(DemigodWings.Style.ANGEL, net.bigyous.gptgodmc.god.vessel.VesselWings.style(
                net.bigyous.gptgodmc.god.vessel.VesselMood.EXALTED.wings));
        assertEquals(DemigodWings.Style.DEMON, net.bigyous.gptgodmc.god.vessel.VesselWings.style(
                net.bigyous.gptgodmc.god.vessel.VesselMood.WRATHFUL.wings));
        assertEquals(null, net.bigyous.gptgodmc.god.vessel.VesselWings.style(
                net.bigyous.gptgodmc.god.vessel.VesselMood.CALM.wings));
        VesselModel.Pose pose = VesselModel.pose("idle", 0, 0, 0, null, null);
        float unit = VesselModel.unit(1.2);
        List<DemigodWings.Piece> w = net.bigyous.gptgodmc.god.vessel.VesselWings.place(
                net.bigyous.gptgodmc.god.vessel.VesselWings.pose(1, 10, 0, DemigodWings.Style.ANGEL),
                DemigodWings.Style.ANGEL, VesselModel.frames(pose).get(VesselModel.Bone.TORSO), unit);
        assertEquals(DemigodWings.WING_PIECES, w.size());
        float span = span(w), height = VesselModel.HEIGHT_PX * unit;
        assertTrue(span > 1.8f * height, "a god's wings are wider than it is tall: " + span + " vs " + height);
        for (DemigodWings.Piece p : w) assertTrue(p.center().z < 0, "behind the back: " + p.center());
    }

    @Test
    void renderVesselWings() throws IOException {
        record Row(String label, VesselLook look, net.bigyous.gptgodmc.god.vessel.VesselMood mood, float time,
                float flight) {
        }
        net.bigyous.gptgodmc.god.vessel.VesselMood ex = net.bigyous.gptgodmc.god.vessel.VesselMood.EXALTED,
                wr = net.bigyous.gptgodmc.god.vessel.VesselMood.WRATHFUL;
        List<Row> rows = List.of(new Row("exalted, radiant", VesselLook.radiant(), ex, 17, 0),
                new Row("exalted, upstroke in flight", VesselLook.radiant(), ex, 10, 1),
                new Row("wrathful, infernal", VesselLook.resolve("infernal", null), wr, 12, 0),
                new Row("wrathful, shade", VesselLook.shade(), wr, 36, 0));
        List<VesselPreviewTest.View> views = List.of(VesselPreviewTest.VIEWS.get(0), VesselPreviewTest.VIEWS.get(1),
                VesselPreviewTest.VIEWS.get(3));
        int w = VesselPreviewTest.CELL_W, h = VesselPreviewTest.CELL_H, cols = views.size() + 1;
        BufferedImage sheet = new BufferedImage(w * cols, h * rows.size(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        double scale = 1.2, ppb = 34;
        float unit = VesselModel.unit(scale);
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            VesselLook look = VesselPreviewTest.moodLook(row.look(), row.mood());
            VesselModel.Pose pose = VesselModel.pose(new VesselModel.Motion("idle", 20, row.time(), 0, 0, 1,
                    row.flight(), row.mood(), new Vector3f(0.2f, -0.3f, 1), null));
            List<VesselModel.Placed> body = VesselModel.place(look, pose, scale);
            List<VesselModel.Placed> parts = new ArrayList<>(body);
            DemigodWings.Style style = net.bigyous.gptgodmc.god.vessel.VesselWings.style(row.mood().wings);
            for (DemigodWings.Piece p : net.bigyous.gptgodmc.god.vessel.VesselWings.place(
                    net.bigyous.gptgodmc.god.vessel.VesselWings.pose(1, row.time(), row.flight(), style), style,
                    VesselModel.frames(pose).get(VesselModel.Bone.TORSO), unit)) {
                VesselModel.Part part = new VesselModel.Part(VesselModel.Bone.TORSO, VesselLook.Slot.ORNAMENT,
                        VesselSkin.Piece.HORN, p.center(), p.size(), p.rot());
                parts.add(new VesselModel.Placed(p.material(), DemigodWings.transform(p, 0), VesselModel.Bone.TORSO,
                        part));
            }
            String eyes = row.mood().eyes != null ? VesselSkin.eyeKey(row.mood().id()) : null;
            VesselTextures.Atlas atlas = VesselTextures.atlas(look.name());
            for (int c = 0; c < views.size(); c++) {
                g.drawImage(VesselPreviewTest.render(parts, views.get(c), look.outline().asRGB(), List.of(), 0.5, ppb,
                        atlas, 0, eyes), c * w, r * h, null);
                VesselPreviewTest.label(g, "NEW " + row.label() + " / " + views.get(c).name(), c * w, r * h);
            }
            g.drawImage(VesselPreviewTest.render(body, views.get(2), look.outline().asRGB(),
                    VesselPreviewTest.wingSparks(row.mood().wings, pose, row.time(), scale), 0.5, ppb, atlas, 0, eyes),
                    views.size() * w, r * h, null);
            VesselPreviewTest.label(g, "OLD particles / back", views.size() * w, r * h);
        }
        g.dispose();
        RendererTest.save(sheet, "vessel-wings");
    }

    // ---- Wings that come and go ---------------------------------------------------------------------

    @Test
    void wingsComeOutWhenFloatingOrAngryAndAwayWhenWalking() {
        VesselWings.Choice auto = VesselWings.Choice.AUTO;
        VesselMood.Wings none = VesselMood.Wings.NONE;
        VesselWings.Want hidden = VesselWings.Want.HIDDEN, spread = VesselWings.Want.SPREAD;
        assertEquals(hidden, VesselWings.want(auto, true, none, 0, 0), "walking or standing: away");
        assertEquals(spread, VesselWings.want(auto, true, none, 0.9f, 0), "floating: out");
        assertEquals(spread, VesselWings.want(auto, true, none, 0, 0.8f), "flying: out");
        assertEquals(spread, VesselWings.want(auto, true, VesselMood.WRATHFUL.wings, 0, 0), "angry: out");
        assertEquals(VesselWings.Want.FOLDED, VesselWings.want(VesselWings.Choice.FOLD, true, none, 0.9f, 0));
        assertEquals(spread, VesselWings.want(VesselWings.Choice.SPREAD, true, none, 0, 0));
        assertEquals(hidden, VesselWings.want(VesselWings.Choice.HIDE, true, VesselMood.EXALTED.wings, 1, 1));
        assertEquals(hidden, VesselWings.want(VesselWings.Choice.SPREAD, false, none, 1, 1), "the server's switch wins");
        assertEquals(DemigodWings.Style.DEMON, VesselWings.style(none, "infernal"));
        assertEquals(DemigodWings.Style.ANGEL, VesselWings.style(none, "radiant"));
        assertEquals(DemigodWings.Style.ANGEL, VesselWings.style(VesselMood.EXALTED.wings, "infernal"), "the mood wins");
        assertEquals(VesselWings.Choice.FOLD, VesselWings.Choice.parse(" Folded "));
        assertEquals("gptgodmc:vessel.wings.unfurl.demon", VesselWings.Cue.UNFURL.soundId(DemigodWings.Style.DEMON));
    }

    @Test
    void wingsGrowThenUnfurlThenFurlThenSinkAway() {
        VesselWings.Motion m = new VesselWings.Motion();
        List<VesselWings.Cue> cues = new ArrayList<>();
        int t = 0;
        for (; t < 60 && !(m.open == 1 && m.unfurl < 0); t++) {
            VesselWings.Cue c = m.step(VesselWings.Want.SPREAD);
            if (c != VesselWings.Cue.NONE) cues.add(c);
        }
        assertTrue(t < 30, "unfurling takes too long: " + t);
        assertEquals(List.of(VesselWings.Cue.APPEAR, VesselWings.Cue.UNFURL), cues);
        cues.clear();
        for (t = 0; t < 60 && !m.gone(); t++) {
            VesselWings.Cue c = m.step(VesselWings.Want.HIDDEN);
            if (c != VesselWings.Cue.NONE) cues.add(c);
        }
        assertTrue(t < 25, "furling takes too long: " + t);
        assertEquals(List.of(VesselWings.Cue.FURL, VesselWings.Cue.VANISH), cues);
        // Folding keeps them on the back.
        for (t = 0; t < 40; t++) m.step(VesselWings.Want.FOLDED);
        assertEquals(1f, m.grow);
        assertEquals(0f, m.open);
    }

    /** The unfurl and the furl, frame by frame, from the three-quarter back. */
    @Test
    void renderWingSequences() throws IOException {
        record Seq(String label, VesselLook look, DemigodWings.Style style, boolean opening) {
        }
        List<Seq> seqs = List.of(new Seq("angel unfurls", VesselLook.radiant(), DemigodWings.Style.ANGEL, true),
                new Seq("angel furls away", VesselLook.radiant(), DemigodWings.Style.ANGEL, false),
                new Seq("demon unfurls", VesselLook.resolve("infernal", null), DemigodWings.Style.DEMON, true),
                new Seq("demon furls away", VesselLook.resolve("infernal", null), DemigodWings.Style.DEMON, false));
        int frames = 8, w = VesselPreviewTest.CELL_W, h = VesselPreviewTest.CELL_H;
        BufferedImage sheet = new BufferedImage(w * frames, h * seqs.size(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        double scale = 1.2;
        float unit = VesselModel.unit(scale);
        VesselPreviewTest.View view = new VesselPreviewTest.View("three-quarter back", Math.PI - 0.6, 0.2);
        VesselModel.Pose pose = VesselModel.pose("idle", 0, 0, 0, null, null);
        VesselModel.Frame torso = VesselModel.frames(pose).get(VesselModel.Bone.TORSO);
        for (int r = 0; r < seqs.size(); r++) {
            Seq s = seqs.get(r);
            VesselWings.Motion m = new VesselWings.Motion();
            if (!s.opening()) {
                for (int i = 0; i < 40; i++) m.step(VesselWings.Want.SPREAD);
            }
            VesselWings.Want want = s.opening() ? VesselWings.Want.SPREAD : VesselWings.Want.HIDDEN;
            int tick = 0;
            for (int c = 0; c < frames; c++) {
                int until = s.opening() ? c * 3 : c * 2 + 1;
                while (tick < until) {
                    m.step(want);
                    tick++;
                }
                List<VesselModel.Placed> parts = new ArrayList<>(VesselModel.place(s.look(), pose, scale));
                if (!m.gone()) {
                    for (DemigodWings.Piece p : VesselWings.place(VesselWings.pose(m, tick, 0, s.style()), s.style(),
                            torso, unit, m.grow)) {
                        VesselModel.Part part = new VesselModel.Part(VesselModel.Bone.TORSO, VesselLook.Slot.ORNAMENT,
                                VesselSkin.Piece.HORN, p.center(), p.size(), p.rot());
                        parts.add(new VesselModel.Placed(p.material(), DemigodWings.transform(p, 0),
                                VesselModel.Bone.TORSO, part));
                    }
                }
                g.drawImage(VesselPreviewTest.render(parts, view, s.look().outline().asRGB(), List.of(), 0, 34,
                        VesselTextures.atlas(s.look().name()), 0, null), c * w, r * h, null);
                VesselPreviewTest.label(g, s.label() + "  tick " + tick, c * w, r * h);
            }
        }
        g.dispose();
        RendererTest.save(sheet, "vessel-wings-sequence");
    }
}
