package net.bigyous.gptgodmc.god.vision;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.vessel.Choreo;
import net.bigyous.gptgodmc.god.vessel.VesselLook;
import net.bigyous.gptgodmc.god.vessel.VesselModel;
import net.bigyous.gptgodmc.god.vessel.VesselTextures;

/**
 * Draws the keyframes of the built-in animations and one the god might write itself (vessel_animate)
 * to build/test-renders/anim-*.png: one row per animation, one column per keyframe moment.
 */
class AnimationPreviewTest {
    static final int COLS = 5;

    static Map<String, Choreo.Anim> samples() {
        Map<String, Choreo.Anim> m = new LinkedHashMap<>();
        for (String name : List.of("deep_bow", "dance", "dig", "plant", "bless_sweep", "sit", "juggle", "cast_line")) {
            m.put(name, Choreo.preset(name));
        }
        // As the god would write one: a triumphant spin with arms flung up.
        m.put("victory (written)", Choreo.parse("victory", List.of(
                "t=0 body=0,0,0 larm=10,10 rarm=10,10 head=0,0",
                "t=0.6 body=-8,180,0 larm=170,40 rarm=170,40 head=0,-30 rise=0.2 cape=80",
                "t=1.2 body=0,360,0 larm=150,70 rarm=150,70 head=0,-20 rise=0 cape=50",
                "t=2 body=0,360,0 larm=10,10 rarm=10,10 head=0,0 cape=20"), null, false, null));
        return m;
    }

    /** COLS moments spread over the keyframes (first, last and between). */
    static List<Integer> moments(Choreo.Anim a) {
        List<Integer> keys = new ArrayList<>();
        for (Choreo.Key k : a.keys()) keys.add(k.tick());
        List<Integer> out = new ArrayList<>();
        if (keys.size() <= COLS) {
            out.addAll(keys);
            while (out.size() < COLS) out.add(Math.round(a.length() * out.size() / (float) COLS));
            out.sort(Integer::compare);
            return out;
        }
        for (int i = 0; i < COLS; i++) out.add(keys.get(Math.round(i * (keys.size() - 1) / (float) (COLS - 1))));
        return out;
    }

    @Test
    void renderAnimations() throws IOException {
        Map<String, Choreo.Anim> anims = samples();
        VesselLook look = VesselLook.shade();
        VesselTextures.Atlas atlas = VesselTextures.atlas(look.name());
        VesselPreviewTest.View view = new VesselPreviewTest.View("three-quarter", 0.65, 0.18);
        int w = VesselPreviewTest.CELL_W, h = VesselPreviewTest.CELL_H;
        BufferedImage all = new BufferedImage(w * COLS, h * anims.size(), BufferedImage.TYPE_INT_RGB);
        Graphics2D ga = all.createGraphics();
        int row = 0;
        for (Map.Entry<String, Choreo.Anim> e : anims.entrySet()) {
            Choreo.Anim a = e.getValue();
            assertTrue(a.warnings().isEmpty(), e.getKey() + ": " + a.warnings());
            BufferedImage sheet = new BufferedImage(w * COLS, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            List<Integer> at = moments(a);
            for (int c = 0; c < COLS; c++) {
                VesselModel.Pose pose = Choreo.pose(a, at.get(c));
                List<VesselModel.Placed> parts = VesselModel.place(look, pose, 1.2);
                BufferedImage cell = VesselPreviewTest.render(parts, view, look.outline().asRGB(), List.of(), 0, 80,
                        atlas, 0, null);
                g.drawImage(cell, c * w, 0, null);
                ga.drawImage(cell, c * w, row * h, null);
                String label = e.getKey() + " t=" + String.format(java.util.Locale.ROOT, "%.2fs", at.get(c) / 20f);
                VesselPreviewTest.label(g, label, c * w, 0);
                VesselPreviewTest.label(ga, label, c * w, row * h);
            }
            g.dispose();
            RendererTest.save(sheet, "anim-" + Choreo.clean(e.getKey().split(" ")[0]));
            row++;
        }
        ga.dispose();
        RendererTest.save(all, "anim-all");
    }
}
