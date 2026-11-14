package net.bigyous.gptgodmc.god.vision;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.god.art.Armory;
import net.bigyous.gptgodmc.god.art.Samples;
import net.bigyous.gptgodmc.god.art.Sprite;
import net.bigyous.gptgodmc.god.art.SpritePainter;
import net.bigyous.gptgodmc.god.art.SpritePainter.Art;
import net.bigyous.gptgodmc.god.vessel.Choreo;
import net.bigyous.gptgodmc.god.vessel.VesselHeld;
import net.bigyous.gptgodmc.god.vessel.VesselLook;
import net.bigyous.gptgodmc.god.vessel.VesselModel;
import net.bigyous.gptgodmc.god.vessel.VesselModel.Bone;
import net.bigyous.gptgodmc.god.vessel.VesselModel.Frame;
import net.bigyous.gptgodmc.god.vessel.VesselTextures;

/**
 * The god striking with relics from its armory: each weapon animation (slash, overhead, thrust, reap, whirl)
 * at five moments, the blow's moment among them, with a real relic in the vessel's hand (built from its art as
 * the resource pack builds it), to build/test-renders/weapon-*.png and weapon-all.png.
 */
class WeaponPreviewTest {
    static final int COLS = 5;

    /** A reaper's scythe, as an artist might draw it: a long dark shaft and a steel crescent at its head. */
    static Sprite scythe() {
        int n = 24;
        char[][] g = new char[n][n];
        for (char[] row : g) java.util.Arrays.fill(row, '.');
        for (int y = 3; y <= 22; y++) {
            int x = 22 - y;
            g[y][x] = 'w';
            g[y][x + 1] = 'w';
        }
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 21; x++) {
                double d = Math.hypot(x - 14.5, y - 12.5);
                if (d >= 9.6 && d <= 11.8 && g[y][x] == '.') g[y][x] = 's';
            }
        }
        g[2][19] = 'g';
        g[2][20] = 'g';
        g[3][20] = 'g';
        g[3][21] = 'g';
        JsonObject o = new JsonObject();
        o.addProperty("size", n);
        JsonObject key = new JsonObject();
        key.addProperty("w", "darkwood");
        key.addProperty("s", "steel");
        key.addProperty("g", "gold thick 3");
        o.add("legend", key);
        JsonArray rows = new JsonArray();
        for (char[] row : g) rows.add(new String(row));
        o.add("rows", rows);
        try {
            return Sprite.parse(o);
        } catch (Sprite.Flaw e) {
            throw new AssertionError(e);
        }
    }

    record Held(String name, Art art, String kind) {
    }

    @Test
    void renderStrikes() throws IOException {
        Art sky = SpritePainter.paint(Samples.KINDLED.sprite().alt(), false), dawn = SpritePainter.paint(Samples.BLADE.sprite(), false),
                reaper = SpritePainter.paint(scythe(), false);
        Map<String, Held> rows = new LinkedHashMap<>();
        rows.put("slash", new Held("Skysplinter", sky, "blade"));
        rows.put("overhead", new Held("Skysplinter", sky, "blade"));
        rows.put("thrust", new Held("Dawnbreaker", dawn, "blade"));
        rows.put("reap", new Held("scythe", reaper, "staff"));
        rows.put("whirl", new Held("scythe", reaper, "staff"));
        VesselLook look = VesselLook.shade();
        VesselTextures.Atlas atlas = VesselTextures.atlas(look.name());
        VesselPreviewTest.View view = new VesselPreviewTest.View("three-quarter", 0.65, 0.18);
        int w = VesselPreviewTest.CELL_W, h = VesselPreviewTest.CELL_H;
        double scale = 1.2, ppb = 70;
        float unit = VesselModel.unit(scale);
        BufferedImage all = new BufferedImage(w * COLS, h * rows.size(), BufferedImage.TYPE_INT_RGB);
        Graphics2D ga = all.createGraphics();
        int row = 0;
        for (Map.Entry<String, Held> e : rows.entrySet()) {
            Choreo.Anim a = Choreo.preset(e.getKey());
            Held held = e.getValue();
            Art art = held.art().squared();
            float[] ends = Armory.ends(art, true);
            VesselHeld.Weapon weapon = new VesselHeld.Weapon(held.name(), held.name(), null, null,
                    Armory.length(held.kind()), ends[0], ends[1], ends[2], ends[3], 0xFFFFFF);
            List<Integer> at = moments(a);
            BufferedImage sheet = new BufferedImage(w * COLS, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            for (int c = 0; c < COLS; c++) {
                VesselModel.Pose pose = Choreo.pose(a, at.get(c));
                Frame arm = VesselModel.frames(pose).get(Bone.RIGHT_ARM);
                Transformation t = VesselHeld.transform(weapon, arm, unit);
                List<VesselModel.Placed> parts = VesselModel.place(look, pose, scale);
                BufferedImage cell = VesselPreviewTest.render(parts, view, look.outline().asRGB(), List.of(), 0, ppb, atlas,
                        0, null, (img, depth, cam) -> drawRelic(img, depth, cam, art, t));
                g.drawImage(cell, c * w, 0, null);
                ga.drawImage(cell, c * w, row * h, null);
                int moment = at.get(c);
                boolean blow = a.cues().stream().anyMatch(q -> q.hit() && q.tick() == moment);
                String label = e.getKey() + " t=" + String.format(java.util.Locale.ROOT, "%.2fs", at.get(c) / 20f)
                        + (blow ? "  HIT" : "");
                VesselPreviewTest.label(g, label, c * w, 0);
                VesselPreviewTest.label(ga, label, c * w, row * h);
            }
            g.dispose();
            RendererTest.save(sheet, "weapon-" + e.getKey());
            row++;
        }
        ga.dispose();
        RendererTest.save(all, "weapon-all");
        assertTrue(new java.io.File("build/test-renders/weapon-all.png").isFile());
    }

    /** Five moments: the keyframes spread as for any animation, with the blow's own moment always among them. */
    static List<Integer> moments(Choreo.Anim a) {
        List<Integer> out = new ArrayList<>(AnimationPreviewTest.moments(a));
        int hit = a.cues().stream().filter(Choreo.Cue::hit).mapToInt(Choreo.Cue::tick).findFirst().orElse(-1);
        if (hit >= 0 && !out.contains(hit)) {
            int nearest = 1;
            for (int i = 1; i < out.size() - 1; i++) {
                if (Math.abs(out.get(i) - hit) < Math.abs(out.get(nearest) - hit)) nearest = i;
            }
            out.set(nearest, hit);
            out.sort(Integer::compare);
        }
        return out;
    }

    /** The relic as boxes, one per texel as thick as painted, placed by the held display's transformation. */
    static void drawRelic(BufferedImage img, double[] depth, Quaternionf cam, Art art, Transformation t) {
        int n = art.w();
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int argb = art.at(0, x, y);
                if ((argb >>> 24) < 128) continue;
                float half = Math.max(0.15f, art.thick(x, y)) / n / 2;
                float x0 = x / (float) n - 0.5f, x1 = (x + 1) / (float) n - 0.5f;
                float y1 = 0.5f - y / (float) n, y0 = 0.5f - (y + 1) / (float) n;
                Vector3f[] c = new Vector3f[8];
                for (int i = 0; i < 8; i++) {
                    Vector3f p = new Vector3f((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? -half : half);
                    Vector3f v = new Quaternionf(t.getRightRotation()).transform(p).mul(t.getScale());
                    new Quaternionf(t.getLeftRotation()).transform(v).add(t.getTranslation());
                    c[i] = cam.transform(v);
                }
                int[][] faces = { { 0, 2, 3, 1 }, { 4, 5, 7, 6 }, { 0, 1, 5, 4 }, { 2, 6, 7, 3 }, { 0, 4, 6, 2 }, { 1, 3, 7, 5 } };
                double[] shade = { 0.8, 0.8, 0.55, 1.0, 0.65, 0.65 };
                for (int f = 0; f < 6; f++) {
                    int[] q = faces[f];
                    int rgb = argb & 0xFFFFFF;
                    int r = (int) (((rgb >> 16) & 255) * shade[f]), gr = (int) (((rgb >> 8) & 255) * shade[f]),
                            b = (int) ((rgb & 255) * shade[f]);
                    int col = (r << 16) | (gr << 8) | b;
                    tri(img, depth, c[q[0]], c[q[1]], c[q[2]], col);
                    tri(img, depth, c[q[0]], c[q[2]], c[q[3]], col);
                }
            }
        }
    }

    private static void tri(BufferedImage img, double[] depth, Vector3f a, Vector3f b, Vector3f c, int rgb) {
        int W = img.getWidth(), H = img.getHeight();
        double ax = VesselPreviewTest.sx(a), ay = VesselPreviewTest.sy(a), bx = VesselPreviewTest.sx(b),
                by = VesselPreviewTest.sy(b), cx = VesselPreviewTest.sx(c), cy = VesselPreviewTest.sy(c);
        double area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if (Math.abs(area) < 1e-9) return;
        int minX = (int) Math.max(0, Math.floor(Math.min(ax, Math.min(bx, cx))));
        int maxX = (int) Math.min(W - 1, Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int minY = (int) Math.max(0, Math.floor(Math.min(ay, Math.min(by, cy))));
        int maxY = (int) Math.min(H - 1, Math.ceil(Math.max(ay, Math.max(by, cy))));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5, py = y + 0.5;
                double w0 = ((bx - px) * (cy - py) - (by - py) * (cx - px)) / area;
                double w1 = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                double z = w0 * a.z + w1 * b.z + w2 * c.z;
                int i = y * W + x;
                if (z > depth[i]) {
                    depth[i] = z;
                    img.setRGB(x, y, rgb);
                }
            }
        }
    }
}
