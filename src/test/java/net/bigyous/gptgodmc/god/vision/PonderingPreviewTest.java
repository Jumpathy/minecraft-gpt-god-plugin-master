package net.bigyous.gptgodmc.god.vision;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.vessel.Choreo;
import net.bigyous.gptgodmc.god.vessel.Pondering;
import net.bigyous.gptgodmc.god.vessel.VesselLook;
import net.bigyous.gptgodmc.god.vessel.VesselModel;
import net.bigyous.gptgodmc.god.vessel.VesselTextures;

/**
 * Draws what the god's body does while it thinks to build/test-renders/pondering-*.png: one row per
 * kind of moment, with its first reaction, its working pose and motif, the swell and the deep working.
 */
class PonderingPreviewTest {
    /** One row: a title, the opening, the working pose, its motif, the second motif, the deep working, a colour. */
    record Row(String title, String opening, String working, String motif, String second, String deep, int rgb) {
    }

    static final List<Row> ROWS = List.of(
            new Row("struck: flinches and rises, then looms", "flinch_rise", "loom", "mist", "runes", "channel", 0xB24BF3),
            new Row("struck, wrathful: flares, then seethes", "flare", "seethe", "sparks", "embers", "storm", 0xFF3A1A),
            new Row("a prayer: inclines, then weighs it", "incline", "scales", "orbit", "motes", "channel", 0xFFD86B),
            new Row("a command: attends, then weaves", "attend", "weave", "tendrils", "runes", "invoke", 0x7AD7FF),
            new Row("a hard task: gathers power", null, "gather", "gather", "runes", "invoke", 0xB24BF3),
            new Row("joyful: arms to the sky", null, "uplift", "halo", "motes", "invoke", 0xFFE9A0),
            new Row("a death: stills, then mourns", "still", "mourn", "motes", "mist", "channel", 0x9FB4D9),
            new Row("its own time: studies the stars", null, "gaze", "stars", "tendrils", "channel", 0xC9A6FF),
            new Row("a puzzle: chin in hand", null, "ponder", "stars", "motes", "channel", 0x8DE0B0),
            new Row("draws a sign in the air", null, "trace", "runes", "tendrils", "invoke", 0x6CF0E0));

    static final float H = 2.16f; // the preview figure's height in blocks (scale 1.2)

    static int ink(Pondering.Ink i, int accent) {
        return switch (i) {
            case ACCENT -> accent;
            case LIGHT -> 0xFFFBEA;
            case SPARK -> 0xBFF4FF;
            case EMBER -> 0xFF8A1C;
            case SMOKE -> 0x6A6470;
            case SOUL -> 0x52E8F0;
            case GLYPH -> 0xE8D8FF;
        };
    }

    /** Roughly where each anchor is on the figure, in figure heights. */
    static Vector3f anchor(Pondering.Anchor a, VesselModel.Pose pose) {
        float rise = pose.offset.y / VesselModel.HEIGHT_PX;
        return switch (a) {
            case CHEST -> new Vector3f(0, 0.62f + rise, 0);
            case HEAD -> new Vector3f(0, 0.9f + rise, 0);
            case LHAND -> new Vector3f(0.3f, 0.5f + rise, 0.18f);
            case RHAND -> new Vector3f(-0.3f, 0.5f + rise, 0.18f);
            default -> new Vector3f(0, 0, 0);
        };
    }

    static List<VesselPreviewTest.Spark> sparks(String motif, String second, int stage, VesselModel.Pose pose, int rgb) {
        List<VesselPreviewTest.Spark> out = new ArrayList<>();
        if (stage < 1) return out;
        List<Pondering.Dot> dots = new ArrayList<>();
        // Several moments laid over each other, as the eye sees particles that linger.
        for (int t = 0; t < 24; t += 2) {
            dots.addAll(Pondering.motif(motif, 100 + t, stage, 7));
            if (stage >= 2) dots.addAll(Pondering.motif(second, 100 + t, stage - 1, 14));
        }
        for (Pondering.Dot d : dots) {
            Vector3f at = anchor(d.at(), pose).add(d.x(), d.y(), d.z()).mul(H);
            out.add(new VesselPreviewTest.Spark(at, ink(d.ink(), rgb)));
        }
        return out;
    }

    @Test
    void renderPondering() throws IOException {
        VesselLook look = VesselLook.shade();
        VesselTextures.Atlas atlas = VesselTextures.atlas(look.name());
        VesselPreviewTest.View view = new VesselPreviewTest.View("three-quarter", 0.65, 0.18);
        int w = VesselPreviewTest.CELL_W, h = VesselPreviewTest.CELL_H, cols = 4;
        String[] heads = { "first reaction", "working", "it swells", "a long thought" };
        BufferedImage all = new BufferedImage(w * cols, h * ROWS.size(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = all.createGraphics();
        for (int r = 0; r < ROWS.size(); r++) {
            Row row = ROWS.get(r);
            for (int c = 0; c < cols; c++) {
                Choreo.Anim anim;
                int tick;
                if (c == 0) {
                    anim = row.opening() == null ? null : Pondering.act(row.opening()).anim();
                    tick = anim == null ? 0 : Math.round(anim.length() * 0.6f);
                } else if (c == 3) {
                    anim = Pondering.act(row.deep()).anim();
                    tick = anim.length() / 2;
                } else {
                    anim = Pondering.act(row.working()).anim();
                    tick = Math.round(anim.length() * (c == 1 ? 0.0f : 0.5f));
                }
                VesselModel.Pose pose = anim == null ? VesselModel.pose("idle", 0, 0, 0, null, null)
                        : Choreo.pose(anim, tick);
                List<VesselModel.Placed> parts = VesselModel.place(look, pose, 1.2);
                BufferedImage cell = VesselPreviewTest.render(parts, view, row.rgb(),
                        sparks(row.motif(), row.second(), c, pose, row.rgb()), 0, 80, atlas, 0, null);
                g.drawImage(cell, c * w, r * h, null);
                String label = c == 0 ? row.title()
                        : heads[c] + ": " + (c == 3 ? row.deep() : row.working()) + " + " + row.motif()
                                + (c >= 2 ? " + " + row.second() : "");
                VesselPreviewTest.label(g, label, c * w, r * h);
                if (c == 0 && row.opening() == null) VesselPreviewTest.label(g, "(no reaction: it simply begins)", 0, r * h + 14);
            }
        }
        g.dispose();
        RendererTest.save(all, "pondering-all");
        // Two smaller sheets that are easier to look at.
        int half = ROWS.size() / 2;
        RendererTest.save(all.getSubimage(0, 0, w * cols, h * half), "pondering-1");
        RendererTest.save(all.getSubimage(0, h * half, w * cols, h * (ROWS.size() - half)), "pondering-2");
    }
}
