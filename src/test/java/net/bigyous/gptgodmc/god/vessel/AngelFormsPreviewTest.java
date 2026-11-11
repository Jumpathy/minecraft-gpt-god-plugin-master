package net.bigyous.gptgodmc.god.vessel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.bukkit.util.Vector;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.vessel.AngelForms.Frame;
import net.bigyous.gptgodmc.god.vessel.AngelForms.Sprite;
import net.bigyous.gptgodmc.god.vessel.TrueForm.Form;

/**
 * Renders every true form offline to build/test-renders/: per form a sheet (eyes shut during the warning,
 * eyes open, from another side, and as a player without the resource pack sees it), plus one gallery of
 * all of them. Display entities are drawn as z-buffered blocks and textured squares (the pack's own
 * textures), particles as additive glow on top, with a 1.8-block mortal for scale.
 */
class AngelFormsPreviewTest {
    static final File OUT = new File("build/test-renders");
    static final int CELL = 420;

    /** lookAngle: where the mortal it watches stands (round the vertical); NaN = at the camera. */
    record Shot(String label, int tick, double eye, double camAngle, boolean pack, double lookAngle, double focus) {
        Shot(String label, int tick, double eye, double camAngle, boolean pack) {
            this(label, tick, eye, camAngle, pack, Double.NaN, Double.NaN);
        }

        Shot(String label, int tick, double eye, double camAngle, boolean pack, double lookAngle) {
            this(label, tick, eye, camAngle, pack, lookAngle, Double.NaN);
        }
    }

    @Test
    void sheets() throws IOException {
        OUT.mkdirs();
        for (Form form : Form.values()) {
            if (form == Form.HYPERCUBE) continue;
            List<Shot> shots = form == Form.EYE ? List.of(
                    new Shot("eye: warning, shut", 70, 0, 0.35, true),
                    new Shot("searching: pupils wander", 160, 1, 0.35, true, Double.NaN, 0),
                    new Shot("it has found you", 190, 1, 0.35, true, Double.NaN, 1),
                    new Shot("without the resource pack", 190, 1, 0.35, false)) : List.of(
                    new Shot(form.id + ": warning, eyes shut", 70, 0, 0.35, true),
                    new Shot("its eyes open", 160, 1, 0.35, true),
                    new Shot("from the side, watching someone else", 260, 1, 2.0, true, 0.35),
                    new Shot("without the resource pack", 160, 1, 0.35, false));
            BufferedImage sheet = new BufferedImage(CELL * 2, CELL * 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            for (int i = 0; i < shots.size(); i++) {
                int x = (i % 2) * CELL, y = (i / 2) * CELL;
                g.drawImage(render(form, shots.get(i), CELL, CELL), x, y, null);
                g.setColor(new Color(60, 40, 80));
                g.drawRect(x, y, CELL - 1, CELL - 1);
                TrueFormPreviewTest.label(g, shots.get(i).label(), x, y);
            }
            g.dispose();
            File f = new File(OUT, "trueform-" + form.id + ".png");
            ImageIO.write(sheet, "png", f);
            assertTrue(f.length() > 10_000);
        }
    }

    @Test
    void gallery() throws IOException {
        OUT.mkdirs();
        int cell = 460, cols = 3;
        Form[] forms = Form.values();
        int rows = (forms.length + cols - 1) / cols;
        BufferedImage img = new BufferedImage(cols * cell, rows * cell, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for (int i = 0; i < forms.length; i++) {
            int x = (i % cols) * cell, y = (i / cols) * cell;
            g.drawImage(render(forms[i], new Shot("", 180, 1, 0.45, true), cell, cell), x, y, null);
            TrueFormPreviewTest.label(g, forms[i].id, x, y);
        }
        g.dispose();
        File f = new File(OUT, "trueform-angels.png");
        ImageIO.write(img, "png", f);
        assertTrue(f.length() > 10_000);
    }

    // ---- Rendering -------------------------------------------------------------------------------

    /** The camera a shot is taken with. */
    static View view(Form form, Shot s, int w, int h) {
        double ground = form == null ? -TrueForm.HEIGHT : -form.rise;
        boolean tall = form == Form.PILLAR;
        double camDist = form == Form.HYPERCUBE ? 23 : tall ? 40 : 30;
        Vector cam = new Vector(Math.sin(s.camAngle()) * camDist, ground + 2.2, Math.cos(s.camAngle()) * camDist);
        Vector aim = new Vector(0, form == Form.HYPERCUBE ? -2.2 : tall ? 3 : -3.5, 0);
        Vector f = aim.clone().subtract(cam).normalize();
        Vector r = f.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Vector up = r.clone().crossProduct(f).normalize();
        double focal = (w / 2.0) / Math.tan(Math.toRadians(tall ? 70 : 64) / 2);
        return new View(w, h, cam, f, r, up, focal);
    }

    /** Where a point round the form's centre lands on a shot, as shader uv (0..1, v up). */
    static float[] uv(Form form, Shot s, int w, int h, double x, double y, double z) {
        double[] p = view(form, s, w, h).project(new Vector3d(x, y, z));
        return p == null ? new float[] { 0.5f, 0.5f } : new float[] { (float) (p[0] / w), (float) (1 - p[1] / h) };
    }

    static BufferedImage render(Form form, Shot s, int w, int h) {
        double ground = form == null ? -TrueForm.HEIGHT : -form.rise;
        View v = view(form, s, w, h);
        Vector cam = v.cam, f = v.f, r = v.r, up = v.up;
        double focal = v.focal;

        // Sky and a faint ground grid.
        for (int y = 0; y < h; y++) {
            double t = y / (double) h;
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                v.buf[0][i] = (float) (0.012 + 0.03 * t);
                v.buf[1][i] = (float) (0.006 + 0.012 * t);
                v.buf[2][i] = (float) (0.025 + 0.045 * t);
            }
        }
        for (int gx = -24; gx <= 24; gx += 2) {
            for (double q = -24; q <= 24; q += 0.12) {
                TrueFormPreviewTest.splat(v.buf, w, h, cam, f, r, up, focal, new Vector(gx, ground, q), 0x1A1E2E, 0.04,
                        0.22, false);
                TrueFormPreviewTest.splat(v.buf, w, h, cam, f, r, up, focal, new Vector(q, ground, gx), 0x1A1E2E, 0.04,
                        0.22, false);
            }
        }
        // It looks at the camera, as at the nearest mortal.
        double yaw = Double.isNaN(s.lookAngle()) ? Math.atan2(cam.getX(), cam.getZ()) : s.lookAngle();
        double pitch = -Math.atan2(cam.getY(), Math.hypot(cam.getX(), cam.getZ()));
        double focus = Double.isNaN(s.focus()) ? 1 : s.focus();
        Frame frame = AngelForms.frame(form, s.tick(), 1, s.eye(), yaw, pitch, focus);
        for (Sprite sp : frame.sprites()) if (!soft(sp)) sprite(v, sp, s.pack(), s.tick());
        // Glows are blended over what is solid, the farthest first.
        List<Sprite> glows = new java.util.ArrayList<>(frame.sprites().stream().filter(AngelFormsPreviewTest::soft)
                .toList());
        glows.sort(java.util.Comparator.comparingDouble((Sprite g) -> -cam.distanceSquared(new Vector(g.x(), g.y(),
                g.z())) - g.sx()));
        for (Sprite sp : glows) sprite(v, sp, s.pack(), s.tick());
        // Particles linger in game: earlier frames fainter behind the current one.
        for (int back = 12; back >= 0; back -= 2) {
            int tick = s.tick() - back;
            Frame fr = back == 0 ? frame : AngelForms.frame(form, tick, 1, s.eye(), yaw, pitch, focus);
            List<TrueForm.Mote> motes = new java.util.ArrayList<>(fr.motes());
            if (!s.pack()) motes.addAll(fr.fallback());
            for (TrueForm.Mote m : motes) {
                boolean glow = m.glyph() == TrueForm.Glyph.GLOW || m.glyph() == TrueForm.Glyph.FLAME
                        || m.glyph() == TrueForm.Glyph.SPARK;
                if (!glow && back > 8) continue;
                double life = glow ? 1 - back / 14.0 : Math.max(0, 1 - back / 10.0);
                Vector p = new Vector(m.x(), m.y(), m.z());
                if (hidden(v, p)) continue;
                int rgb = switch (m.glyph()) {
                    case SOUL -> TrueForm.mix(m.rgb(), 0x5FE6FF, 0.5);
                    case FLAME -> TrueForm.mix(m.rgb(), 0xFFC040, 0.3);
                    case SPARK -> 0xC8E8FF;
                    default -> m.rgb();
                };
                double size = 0.05 * m.size() * (glow ? 1.4 : 1);
                TrueFormPreviewTest.splat(v.buf, w, h, cam, f, r, up, focal, p, rgb, size,
                        (glow ? 1.3 : 0.85) * life * life, back == 0);
            }
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < w * h; i++) {
            int R = TrueFormPreviewTest.tone(v.buf[0][i]), G = TrueFormPreviewTest.tone(v.buf[1][i]),
                    B = TrueFormPreviewTest.tone(v.buf[2][i]);
            img.setRGB(i % w, i / w, (R << 16) | (G << 8) | B);
        }
        Vector feet = form == Form.HYPERCUBE ? new Vector(4, ground, 7)
                : new Vector(Math.sin(s.camAngle() + 0.3) * 10, ground, Math.cos(s.camAngle() + 0.3) * 10);
        TrueFormPreviewTest.mortal(img, cam, f, r, up, focal, feet);
        return img;
    }

    /** The camera, a light buffer and a depth buffer. */
    static final class View {
        final int w, h;
        final Vector cam, f, r, up;
        final double focal;
        final float[][] buf;
        final double[] depth;

        View(int w, int h, Vector cam, Vector f, Vector r, Vector up, double focal) {
            this.w = w;
            this.h = h;
            this.cam = cam;
            this.f = f;
            this.r = r;
            this.up = up;
            this.focal = focal;
            this.buf = new float[3][w * h];
            this.depth = new double[w * h];
            Arrays.fill(depth, Double.POSITIVE_INFINITY);
        }

        /** Screen x, y and depth, or null behind the camera. */
        double[] project(Vector3d p) {
            double dx = p.x - cam.getX(), dy = p.y - cam.getY(), dz = p.z - cam.getZ();
            double z = dx * f.getX() + dy * f.getY() + dz * f.getZ();
            if (z < 0.3) return null;
            double x = dx * r.getX() + dy * r.getY() + dz * r.getZ();
            double y = dx * up.getX() + dy * up.getY() + dz * up.getZ();
            return new double[] { w / 2.0 + x / z * focal, h / 2.0 - y / z * focal, z };
        }
    }

    private static boolean soft(Sprite s) {
        return s.textured() && s.key().startsWith("glow");
    }

    private static boolean hidden(View v, Vector p) {
        double[] s = v.project(new Vector3d(p.getX(), p.getY(), p.getZ()));
        if (s == null) return true;
        int x = (int) s[0], y = (int) s[1];
        if (x < 0 || y < 0 || x >= v.w || y >= v.h) return false;
        return s[2] > v.depth[y * v.w + x] + 0.15;
    }

    private static final Map<String, Integer> BLOCKS = Map.of("GOLD_BLOCK", 0xF4CF45, "SHROOMLIGHT", 0xF29A4C,
            "WAXED_COPPER_BLOCK", 0xC46A4A, "PEARLESCENT_FROGLIGHT", 0xF2E2EA);
    private static final Map<String, Integer> HEADS = Map.of("PLAYER_HEAD", 0xA87A58, "PIGLIN_HEAD", 0xE4A090,
            "SKELETON_SKULL", 0xD8D8D0, "WITHER_SKELETON_SKULL", 0x343434);

    /** Draws one display entity as the client would: a box, or a flat textured square (or its stand-in). */
    private static void sprite(View v, Sprite s, boolean pack, int tick) {
        Vector3d at = new Vector3d(s.x(), s.y(), s.z());
        if (!s.textured()) {
            BufferedImage skin = null;
            if (pack && s.box() != null) {
                BufferedImage[] fr = TrueFormTextures.frames(s.box());
                skin = fr[(tick / TrueFormTextures.FEATHER_FRAME_TICKS) % fr.length];
            }
            box(v, at, s.rotation(), new Vector3d(s.sx(), s.sy(), s.sz()), BLOCKS.getOrDefault(s.block(), 0xC0C0C0),
                    skin, !"light".equals(s.box()));
            return;
        }
        if (!pack) {
            if (s.fallback() == null) return; // drawn by the fallback particles instead
            double k = s.sx() * s.fallbackScale() * 0.5; // a head item is half a block across
            box(v, at, s.rotation(), new Vector3d(k, k, k), HEADS.getOrDefault(s.fallback(), 0x888888), null, true);
            return;
        }
        Quaterniond rot = s.billboard()
                ? AngelForms.facing(new Vector3d(v.cam.getX(), v.cam.getY(), v.cam.getZ()).sub(at))
                : s.rotation();
        BufferedImage[] frames = TrueFormTextures.frames(s.key());
        BufferedImage tex = frames[(tick / TrueFormTextures.FEATHER_FRAME_TICKS) % frames.length];
        Vector3d[] c = new Vector3d[4];
        double[][] uv = { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 } };
        double[][] local = { { -0.5, -0.5 }, { 0.5, -0.5 }, { 0.5, 0.5 }, { -0.5, 0.5 } };
        for (int i = 0; i < 4; i++) {
            c[i] = rot.transform(new Vector3d(local[i][0] * s.sx(), local[i][1] * s.sy(), 0)).add(at);
        }
        // Seen from behind, the back face shows the texture mirrored back the right way round.
        Vector3d normal = rot.transform(new Vector3d(0, 0, 1));
        Vector3d toCam = new Vector3d(v.cam.getX(), v.cam.getY(), v.cam.getZ()).sub(at);
        boolean back = normal.dot(toCam) < 0;
        if (back) uv = new double[][] { { 1, 0 }, { 0, 0 }, { 0, 1 }, { 1, 1 } };
        quad(v, c, uv, tex, -1, 1);
    }

    private static void box(View v, Vector3d at, Quaterniond rot, Vector3d size, int rgb, BufferedImage skin,
            boolean shaded) {
        int[][] faces = { { 0, 1, 3, 2 }, { 4, 6, 7, 5 }, { 0, 4, 5, 1 }, { 2, 3, 7, 6 }, { 0, 2, 6, 4 },
                { 1, 5, 7, 3 } };
        Vector3d[] corner = new Vector3d[8];
        for (int i = 0; i < 8; i++) {
            Vector3d l = new Vector3d(((i & 4) == 0 ? -0.5 : 0.5) * size.x, ((i & 2) == 0 ? -0.5 : 0.5) * size.y,
                    ((i & 1) == 0 ? -0.5 : 0.5) * size.z);
            corner[i] = rot.transform(l).add(at);
        }
        for (int[] fc : faces) {
            Vector3d a = corner[fc[0]], b = corner[fc[1]], c = corner[fc[2]];
            Vector3d n = new Vector3d(b).sub(a).cross(new Vector3d(c).sub(a)).normalize();
            double shade = n.y * n.y * (n.y > 0 ? 1.0 : 0.5) + n.x * n.x * 0.62 + n.z * n.z * 0.8;
            Vector3d[] q = { corner[fc[0]], corner[fc[1]], corner[fc[2]], corner[fc[3]] };
            int col = scale(rgb, shade);
            quad(v, q, new double[][] { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 } }, skin, col, shaded ? shade : 1);
        }
    }

    private static void quad(View v, Vector3d[] c, double[][] uv, BufferedImage tex, int rgb, double shade) {
        tri(v, c[0], c[1], c[2], uv[0], uv[1], uv[2], tex, rgb, shade);
        tri(v, c[0], c[2], c[3], uv[0], uv[2], uv[3], tex, rgb, shade);
    }

    /** A triangle, perspective-correct, depth-tested; textured pixels are cut out where alpha is low. */
    private static void tri(View v, Vector3d a, Vector3d b, Vector3d c, double[] ua, double[] ub, double[] uc,
            BufferedImage tex, int rgb, double shade) {
        double[] p = v.project(a), q = v.project(b), s = v.project(c);
        if (p == null || q == null || s == null) return;
        int x0 = (int) Math.max(0, Math.floor(Math.min(p[0], Math.min(q[0], s[0]))));
        int x1 = (int) Math.min(v.w - 1, Math.ceil(Math.max(p[0], Math.max(q[0], s[0]))));
        int y0 = (int) Math.max(0, Math.floor(Math.min(p[1], Math.min(q[1], s[1]))));
        int y1 = (int) Math.min(v.h - 1, Math.ceil(Math.max(p[1], Math.max(q[1], s[1]))));
        double area = (q[0] - p[0]) * (s[1] - p[1]) - (s[0] - p[0]) * (q[1] - p[1]);
        if (Math.abs(area) < 1e-9) return;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double px = x + 0.5, py = y + 0.5;
                double w0 = ((q[0] - px) * (s[1] - py) - (s[0] - px) * (q[1] - py)) / area;
                double w1 = ((s[0] - px) * (p[1] - py) - (p[0] - px) * (s[1] - py)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                double iz = w0 / p[2] + w1 / q[2] + w2 / s[2];
                double z = 1 / iz;
                int i = y * v.w + x;
                if (z >= v.depth[i]) continue;
                int col = rgb;
                if (tex != null) {
                    double u = (w0 * ua[0] / p[2] + w1 * ub[0] / q[2] + w2 * uc[0] / s[2]) * z;
                    double t = (w0 * ua[1] / p[2] + w1 * ub[1] / q[2] + w2 * uc[1] / s[2]) * z;
                    int tx = (int) Math.max(0, Math.min(tex.getWidth() - 1, Math.floor(u * tex.getWidth())));
                    int ty = (int) Math.max(0, Math.min(tex.getHeight() - 1, Math.floor((1 - t) * tex.getHeight())));
                    int argb = tex.getRGB(tx, ty);
                    int alpha = argb >>> 24;
                    if (alpha < 3) continue;
                    col = scale(argb & 0xFFFFFF, shade);
                    if (alpha < 250) {
                        // Translucent: blended over what is behind, and it hides nothing.
                        float k = alpha / 255f;
                        v.buf[0][i] = v.buf[0][i] * (1 - k) + light((col >> 16) & 255) * k;
                        v.buf[1][i] = v.buf[1][i] * (1 - k) + light((col >> 8) & 255) * k;
                        v.buf[2][i] = v.buf[2][i] * (1 - k) + light(col & 255) * k;
                        continue;
                    }
                }
                v.depth[i] = z;
                v.buf[0][i] = light((col >> 16) & 255);
                v.buf[1][i] = light((col >> 8) & 255);
                v.buf[2][i] = light(col & 255);
            }
        }
    }

    /** The light value that TrueFormPreviewTest.tone turns back into this 0-255 channel. */
    private static float light(int channel) {
        double c = Math.min(0.995, Math.pow(channel / 255.0, 1.6));
        return (float) (-Math.log(1 - c) / 1.25);
    }

    private static int scale(int rgb, double k) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * k);
        int g = (int) Math.min(255, ((rgb >> 8) & 255) * k);
        int b = (int) Math.min(255, (rgb & 255) * k);
        return (r << 16) | (g << 8) | b;
    }
}
