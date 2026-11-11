package net.bigyous.gptgodmc.god.vessel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/**
 * Renders the true form offline to build/test-renders/: a contact sheet of the form through its
 * unfolding, the eye opening and the turning of the hypercube (additive glowing motes over a night
 * sky, with a 1.8-block mortal for scale), and a top-down diagram of who its gaze kills.
 */
class TrueFormPreviewTest {
    static final int CELL = 380;
    static final File OUT = new File("build/test-renders");

    record Moment(String label, int tick, double unfold, double eye, double camAngle) {
    }

    @Test
    void contactSheet() throws IOException {
        List<Moment> moments = List.of(
                new Moment("t=0.5s unfolding, eye shut", 10, 0.25, 0, 0.3),
                new Moment("t=1.2s unfolding", 24, 0.62, 0, 0.3),
                new Moment("warning: AVERT YOUR EYES", 60, 1, 0, 0.3),
                new Moment("the eye opens", 84, 1, 0.45, 0.3),
                new Moment("open: it sees (4D phase A)", 120, 1, 1, 0.3),
                new Moment("4D phase B", 210, 1, 1, 0.9),
                new Moment("4D phase C", 330, 1, 1, 1.7),
                new Moment("4D phase D, from below", 460, 1, 1, 2.6));
        int cols = 4, rows = 2;
        BufferedImage sheet = new BufferedImage(cols * CELL, rows * CELL, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        for (int i = 0; i < moments.size(); i++) {
            Moment m = moments.get(i);
            BufferedImage cell = render(m, CELL, CELL);
            int x = (i % cols) * CELL, y = (i / cols) * CELL;
            g.drawImage(cell, x, y, null);
            g.setColor(new Color(60, 40, 80));
            g.drawRect(x, y, CELL - 1, CELL - 1);
            label(g, m.label(), x, y);
        }
        g.dispose();
        OUT.mkdirs();
        File f = new File(OUT, "trueform-forms.png");
        ImageIO.write(sheet, "png", f);
        assertTrue(f.length() > 10_000);

        // One large still of the open form, for a closer look.
        BufferedImage hero = render(new Moment("", 150, 1, 1, 0.5), 900, 900);
        Graphics2D hg = hero.createGraphics();
        label(hg, "the true form, eye open (1.8-block mortal for scale)", 0, 0);
        hg.dispose();
        ImageIO.write(hero, "png", new File(OUT, "trueform-hero.png"));
    }

    /** Perspective, additive soft glow on a near-black sky. */
    static BufferedImage render(Moment m, int w, int h) {
        double ground = -TrueForm.HEIGHT;
        double camDist = m.label().contains("below") ? 18 : 23;
        double camY = m.label().contains("below") ? ground + 1.7 : ground + 5;
        Vector cam = new Vector(Math.sin(m.camAngle()) * camDist, camY, Math.cos(m.camAngle()) * camDist);
        Vector aim = new Vector(0, m.label().contains("below") ? 0 : -2.5, 0);
        Vector f = aim.clone().subtract(cam).normalize();
        Vector r = f.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Vector up = r.clone().crossProduct(f).normalize();
        double focal = (w / 2.0) / Math.tan(Math.toRadians(62) / 2);

        float[][] buf = new float[3][w * h];
        // Sky: a faint violet glow near the horizon.
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            double t = y / (double) h;
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                buf[0][i] = (float) (0.012 + 0.03 * t);
                buf[1][i] = (float) (0.006 + 0.012 * t);
                buf[2][i] = (float) (0.025 + 0.045 * t);
            }
        }
        // The ground: a faint grid round the spot beneath it.
        for (int gx = -12; gx <= 12; gx += 2) {
            for (double s = -12; s <= 12; s += 0.12) {
                splat(buf, w, h, cam, f, r, up, focal, new Vector(gx, ground, s), 0x1A1E2E, 0.04, 0.22, false);
                splat(buf, w, h, cam, f, r, up, focal, new Vector(s, ground, gx), 0x1A1E2E, 0.04, 0.22, false);
            }
        }
        // The great eye looks at the viewer. Particles linger in game, so earlier frames are drawn
        // fainter behind the current one: dust for about 12 ticks, the self-lit sparks for longer.
        double yaw = Math.atan2(cam.getX(), cam.getZ());
        double pitch = -Math.atan2(cam.getY(), Math.hypot(cam.getX(), cam.getZ()));
        for (int back = 14; back >= 0; back -= 2) {
            int tick = m.tick() - back;
            if (tick < 0) continue;
            double unfold = m.unfold() * (m.unfold() < 1 ? Math.max(0, tick) / (double) m.tick() : 1);
            double fade = 1 - back / 16.0;
            for (TrueForm.Mote mote : TrueForm.frame(tick, unfold, m.eye(), yaw, pitch)) {
                boolean glow = mote.glyph() == TrueForm.Glyph.GLOW;
                if (!glow && back > 10) continue;
                double life = glow ? fade : Math.max(0, 1 - back / 12.0);
                double size = 0.05 * mote.size() * (glow ? 1.3 : 1);
                int rgb = mote.glyph() == TrueForm.Glyph.SOUL ? mixSoul(mote.rgb()) : mote.rgb();
                splat(buf, w, h, cam, f, r, up, focal, new Vector(mote.x(), mote.y(), mote.z()), rgb, size,
                        (glow ? 1.3 : 0.85) * life * life, back == 0);
            }
        }
        for (int i = 0; i < w * h; i++) {
            int R = tone(buf[0][i]), G = tone(buf[1][i]), B = tone(buf[2][i]);
            img.setRGB(i % w, i / w, (R << 16) | (G << 8) | B);
        }
        mortal(img, cam, f, r, up, focal, new Vector(4, ground, 6));
        return img;
    }

    private static int mixSoul(int rgb) {
        return TrueForm.mix(rgb, 0x5FE6FF, 0.5);
    }

    static int tone(float v) {
        double c = 1 - Math.exp(-v * 1.25);
        return (int) Math.max(0, Math.min(255, Math.round(Math.pow(c, 1 / 1.6) * 255)));
    }

    /** A gaussian blob plus a wide faint halo, added into the buffer. */
    static void splat(float[][] buf, int w, int h, Vector cam, Vector f, Vector r, Vector up, double focal,
            Vector p, int rgb, double worldSigma, double gain, boolean halo) {
        Vector d = p.clone().subtract(cam);
        double z = d.dot(f);
        if (z < 0.3) return;
        double sx = w / 2.0 + d.dot(r) / z * focal;
        double sy = h / 2.0 - d.dot(up) / z * focal;
        double sigma = Math.max(0.7, worldSigma * focal / z);
        double haloSigma = sigma * 4.5;
        int reach = (int) Math.ceil((halo ? haloSigma : sigma) * 2.6);
        if (sx < -reach || sy < -reach || sx > w + reach || sy > h + reach) return;
        float cr = ((rgb >> 16) & 255) / 255f, cg = ((rgb >> 8) & 255) / 255f, cb = (rgb & 255) / 255f;
        double core = gain * 1.1, glow = gain * 0.035;
        for (int y = (int) (sy - reach); y <= sy + reach; y++) {
            if (y < 0 || y >= h) continue;
            for (int x = (int) (sx - reach); x <= sx + reach; x++) {
                if (x < 0 || x >= w) continue;
                double dd = (x - sx) * (x - sx) + (y - sy) * (y - sy);
                double k = core * Math.exp(-dd / (2 * sigma * sigma));
                if (halo) k += glow * Math.exp(-dd / (2 * haloSigma * haloSigma));
                if (k < 1e-4) continue;
                int i = y * w + x;
                buf[0][i] += (float) (cr * k);
                buf[1][i] += (float) (cg * k);
                buf[2][i] += (float) (cb * k);
            }
        }
    }

    /** A dark 1.8-block silhouette of a player, standing on the ground for scale. */
    static void mortal(BufferedImage img, Vector cam, Vector f, Vector r, Vector up, double focal,
            Vector feet) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(150, 150, 165));
        double[][] boxes = { { -0.25, 0, 0.25, 0.75 }, { -0.25, 0.75, 0.25, 1.4 }, { -0.42, 0.75, -0.25, 1.4 },
                { 0.25, 0.75, 0.42, 1.4 }, { -0.25, 1.4, 0.25, 1.85 } };
        for (double[] b : boxes) {
            double[] a = project(img, cam, f, r, up, focal, feet.clone().add(new Vector(b[0], b[1], 0)));
            double[] c = project(img, cam, f, r, up, focal, feet.clone().add(new Vector(b[2], b[3], 0)));
            if (a == null || c == null) continue;
            int x0 = (int) Math.min(a[0], c[0]), y0 = (int) Math.min(a[1], c[1]);
            g.fillRect(x0, y0, Math.max(1, (int) Math.abs(c[0] - a[0])), Math.max(1, (int) Math.abs(c[1] - a[1])));
        }
        g.dispose();
    }

    private static double[] project(BufferedImage img, Vector cam, Vector f, Vector r, Vector up, double focal,
            Vector p) {
        Vector d = p.clone().subtract(cam);
        double z = d.dot(f);
        if (z < 0.3) return null;
        return new double[] { img.getWidth() / 2.0 + d.dot(r) / z * focal, img.getHeight() / 2.0 - d.dot(up) / z * focal };
    }

    // ---- The gaze, from above ------------------------------------------------------------------

    record Mortal(String name, double x, double z, double lookDeg) {
    }

    @Test
    void gazeDiagram() throws IOException {
        int size = 640;
        double scale = 5.6; // pixels per block
        double maxDeg = 14, range = 48;
        // A wall to the south-east of the form.
        double[] wall = { 10, 14, 22, 8 };
        List<Mortal> mortals = List.of(
                new Mortal("stares", -18, -10, 0),
                new Mortal("glances 10deg", 20, -22, 10),
                new Mortal("looks 20deg away", -26, 14, 20),
                new Mortal("turned away", 6, -30, 180),
                new Mortal("behind the wall", 24, 20, 0),
                new Mortal("too far", -52, -6, 0),
                new Mortal("beneath it, eyes ahead", 2, 2, 50));
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(10, 8, 18));
        g.fillRect(0, 0, size, size);
        double cx = size / 2.0, cz = size / 2.0;
        g.setColor(new Color(60, 30, 90));
        g.drawOval((int) (cx - range * scale), (int) (cz - range * scale), (int) (range * 2 * scale),
                (int) (range * 2 * scale));
        g.setColor(new Color(200, 160, 255));
        g.fillOval((int) cx - 9, (int) cz - 9, 18, 18);
        g.setStroke(new BasicStroke(4));
        g.setColor(new Color(140, 140, 150));
        g.draw(new Line2D.Double(cx + wall[0] * scale, cz + wall[1] * scale, cx + wall[2] * scale,
                cz + wall[3] * scale));
        g.setStroke(new BasicStroke(1.5f));
        Vector centre = new Vector(0, TrueForm.HEIGHT, 0);
        for (Mortal m : mortals) {
            Vector eye = new Vector(m.x(), 1.62, m.z());
            Vector to = centre.clone().subtract(eye);
            double base = Math.atan2(to.getZ(), to.getX());
            double a = base + Math.toRadians(m.lookDeg());
            double horiz = Math.hypot(to.getX(), to.getZ());
            Vector look = new Vector(Math.cos(a) * horiz, to.getY(), Math.sin(a) * horiz).normalize();
            boolean looks = TrueForm.gazes(eye, look, centre, maxDeg, range, TrueForm.CORE_RADIUS);
            boolean blocked = Line2D.linesIntersect(m.x(), m.z(), 0, 0, wall[0], wall[1], wall[2], wall[3]);
            boolean dies = looks && !blocked;
            double px = cx + m.x() * scale, pz = cz + m.z() * scale;
            // The cone the mortal sees.
            g.setColor(dies ? new Color(255, 60, 60, 90) : new Color(90, 220, 140, 70));
            double half = Math.toRadians(maxDeg);
            int[] xs = { (int) px, (int) (px + Math.cos(a - half) * 80), (int) (px + Math.cos(a + half) * 80) };
            int[] zs = { (int) pz, (int) (pz + Math.sin(a - half) * 80), (int) (pz + Math.sin(a + half) * 80) };
            g.fillPolygon(xs, zs, 3);
            g.setColor(dies ? new Color(255, 70, 70) : new Color(110, 235, 150));
            g.fillOval((int) px - 6, (int) pz - 6, 12, 12);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            String text = m.name() + (dies ? " - DIES" : blocked && looks ? " - shielded" : " - spared");
            int tw = g.getFontMetrics().stringWidth(text);
            int tx = px + 9 + tw > size - 4 ? (int) px - 9 - tw : (int) px + 9;
            g.drawString(text, tx, (int) pz - 8);
        }
        label(g, "true form gaze, top-down: 14deg cone, 48-block range, walls shield", 0, 0);
        g.dispose();
        OUT.mkdirs();
        File f = new File(OUT, "trueform-gaze.png");
        ImageIO.write(img, "png", f);
        assertTrue(f.exists());
    }

    static void label(Graphics2D g, String text, int x, int y) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(new Color(0, 0, 0));
        g.drawString(text, x + 9, y + 19);
        g.setColor(new Color(225, 212, 240));
        g.drawString(text, x + 8, y + 18);
    }
}
