package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.imageio.ImageIO;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.RitePreviewTest.Camera;
import net.bigyous.gptgodmc.god.RitePreviewTest.Canvas;
import net.bigyous.gptgodmc.god.RitePreviewTest.Look;

/**
 * The god's death shows: every variant has all four stages, stays within the particle budget, keeps
 * its sounds, bolts and fireworks inside the show, and is rendered to
 * build/test-renders/deathshow-&lt;variant&gt;.png (ten moments of the timeline) for judging offline.
 */
class DeathShowTest {
    static final double H = 1.8;
    static final int[] KEY = { 3, 14, 24, 29, 38, 47, 50, 52, 60, 76 };

    @Test
    void everyVariantPlaysAllFourStages() {
        for (DeathShow.Variant v : DeathShow.Variant.values()) {
            Set<DeathShow.Stage> seen = EnumSet.noneOf(DeathShow.Stage.class);
            for (int f = 0; f < DeathShow.FRAMES; f++) {
                if (!DeathShow.frame(v, f, H).isEmpty()) seen.add(DeathShow.stage(f));
            }
            assertEquals(EnumSet.allOf(DeathShow.Stage.class), seen, v.id());
            assertTrue(DeathShow.frame(v, -1, H).isEmpty());
            assertTrue(DeathShow.frame(v, DeathShow.FRAMES, H).isEmpty());
            // A big body makes no more particles than the budget can thin sensibly.
            assertTrue(DeathShow.maxDots(v, 10.8) <= Spectacle.FRAME_BUDGET * 2, v.id() + " "
                    + DeathShow.maxDots(v, 10.8));
            // Deterministic.
            assertEquals(DeathShow.frame(v, 40, H), DeathShow.frame(v, 40, H));
        }
    }

    @Test
    void soundsBoltsAndFireworksFallInsideTheShow() {
        for (DeathShow.Variant v : DeathShow.Variant.values()) {
            List<Rites.Cue> cues = DeathShow.cues(v);
            assertTrue(cues.size() >= 20, v.id());
            boolean boom = false, dragon = false, wither = false, bell = false, beacon = false, thunder = false;
            int last = 0;
            for (Rites.Cue c : cues) {
                assertTrue(c.frame() >= 0 && c.frame() < DeathShow.FRAMES, c.toString());
                assertTrue(c.sound().matches("[a-z_.]+"), c.sound());
                assertTrue(c.frame() >= last, "sorted");
                last = c.frame();
                boom |= c.sound().equals("entity.warden.sonic_boom");
                dragon |= c.sound().equals("entity.ender_dragon.death");
                wither |= c.sound().equals("entity.wither.death");
                bell |= c.sound().startsWith("block.bell");
                beacon |= c.sound().equals("block.beacon.deactivate");
                thunder |= c.sound().equals("entity.lightning_bolt.thunder");
            }
            assertTrue(boom && dragon && wither && bell && beacon && thunder, v.id() + " layers every sound");
            assertTrue(last < 85, "silence at the end");
            for (DeathShow.Bolt b : DeathShow.bolts(v, H)) assertTrue(b.frame() < DeathShow.AFTERGLOW);
            List<DeathShow.Firework> fw = DeathShow.fireworks(v, H);
            assertTrue(fw.size() >= 6, "layered fireworks");
            for (DeathShow.Firework f : fw) {
                assertTrue(f.frame() >= DeathShow.BURST && f.frame() < DeathShow.FRAMES);
                assertFalse(f.colors().isEmpty());
                org.bukkit.FireworkEffect.Type.valueOf(f.type());
            }
        }
        assertTrue(DeathShow.VANISH < DeathShow.BURST && DeathShow.LOOT >= DeathShow.BURST);
    }

    @Test
    void eachGodDiesItsOwnWay() {
        assertEquals(DeathShow.Variant.SUPERNOVA, DeathShow.pick("benevolent", "shade", 1));
        assertEquals(DeathShow.Variant.SUPERNOVA, DeathShow.pick("cosmic", null, 1));
        assertEquals(DeathShow.Variant.UNRAVELING, DeathShow.pick("trickster", null, 1));
        assertEquals(DeathShow.Variant.UNRAVELING, DeathShow.pick("chill", null, 1));
        assertEquals(DeathShow.Variant.SHATTERING, DeathShow.pick("wrathful", null, 1));
        assertEquals(DeathShow.Variant.ABYSSAL, DeathShow.pick("evil", null, 1));
        assertEquals(DeathShow.Variant.ABYSSAL, DeathShow.pick("custom", "void", 1));
        assertEquals(DeathShow.Variant.SUPERNOVA, DeathShow.pick(null, "radiant", 1));
        assertEquals(DeathShow.pick("x", "y", 42), DeathShow.pick("x", "y", 42), "seeded");
        Set<DeathShow.Variant> random = EnumSet.noneOf(DeathShow.Variant.class);
        for (long s = 0; s < 64; s++) random.add(DeathShow.pick(null, null, s));
        assertEquals(4, random.size(), "every variant can come up at random");
        assertEquals(DeathShow.Variant.ABYSSAL, DeathShow.Variant.parse(" Abyssal "));
    }

    // ---- Preview -------------------------------------------------------------------------------

    @Test
    void writeContactSheets() throws Exception {
        File out = new File("build/test-renders");
        out.mkdirs();
        int cols = 5;
        int rows = (KEY.length + cols - 1) / cols;
        int cw = RitePreviewTest.CELL_W, ch = RitePreviewTest.CELL_H, title = RitePreviewTest.TITLE_H;
        for (DeathShow.Variant v : DeathShow.Variant.values()) {
            BufferedImage img = new BufferedImage(cw * cols, title + rows * (ch + 20), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(14, 11, 22));
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            Camera cam = new Camera(5.5, 30, 0.62, 0.32);
            Camera close = new Camera(1.4, 8.5, 0.62, 0.3);
            for (int i = 0; i < KEY.length; i++) {
                int f = KEY[i];
                Canvas c = new Canvas(f < DeathShow.COLLAPSE ? close : cam);
                c.grid();
                if (f <= DeathShow.VANISH) {
                    c.figure(0, f >= 40 ? 0.6 : 1.0);
                }
                draw(c, v, f);
                int x = (i % cols) * cw, y = title + (i / cols) * (ch + 20);
                c.into(img, x, y + 20);
                g.setColor(new Color(220, 210, 240));
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
                g.drawString(String.format("f%d  %.1fs  %s%s", f, f / 10.0, DeathShow.stage(f).name().toLowerCase(),
                        (f >= DeathShow.DARK_FROM && f < DeathShow.DARK_TO ? " (darkness)" : "")
                                + (f < DeathShow.COLLAPSE ? " (close up)" : "")), x + 8, y + 15);
            }
            g.setColor(new Color(255, 200, 120));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g.drawString("DEATH SHOW: " + v.id().toUpperCase() + "   " + DeathShow.FRAMES / 10.0 + " s, burst at "
                    + DeathShow.BURST / 10.0 + " s, peak " + DeathShow.maxDots(v, H) + " particles/frame", 10, 23);
            g.dispose();
            File file = new File(out, "deathshow-" + v.id() + ".png");
            ImageIO.write(img, "png", file);
            assertTrue(file.length() > 0);
        }
    }

    /** Frame f with a short trail of the frames before it, fireworks and lightning included. */
    static void draw(Canvas c, DeathShow.Variant v, int f) {
        double[] fade = { 1.0, 0.5, 0.3, 0.17, 0.08 };
        for (int back = 12; back >= 0; back--) {
            int g = f - back;
            if (g < 0) continue;
            for (Rites.Dot d : DeathShow.frame(v, g, H)) {
                Look l = RitePreviewTest.look(d.tint());
                if (!d.moving()) {
                    if (d.tint() == Rites.Tint.FLASH) {
                        if (back <= 1) c.splat(d.x(), d.y(), d.z(), 0xFFFFFF, 0.9, 1.2 / (1 + back * 2));
                    } else if (back < fade.length) {
                        c.splat(d.x(), d.y(), d.z(), d.tint().rgb, l.radius(), l.intensity() * fade[back]);
                    }
                    continue;
                }
                int age = back * 2 + 1;
                if (age > l.life()) continue;
                double x = d.x(), y = d.y(), z = d.z(), vx = d.vx(), vy = d.vy(), vz = d.vz();
                for (int t = 0; t < age; t++) {
                    x += vx;
                    y += vy;
                    z += vz;
                    vx *= l.drag();
                    vy = vy * l.drag() - l.gravity();
                    vz *= l.drag();
                }
                c.splat(x, y, z, d.tint().rgb, l.radius(), l.intensity() * (1 - age / (double) l.life()));
            }
        }
        for (DeathPieces.Piece piece : DeathPieces.build(v, H, 0.5, 0.5, DeathPieces.FLAT)) {
            if (piece.alive(f)) drawPiece(c, piece, piece.at(f));
        }
        for (DeathShow.Firework fw : DeathShow.fireworks(v, H)) {
            int age = f - fw.frame();
            if (age < 0 || age > 8) continue;
            double r = (fw.type().equals("BALL_LARGE") ? 1.6 : 0.9) * (age + 1);
            double w = 1 - age / 9.0;
            int k = 0;
            for (Vector d : Shapes.sphere(70, r, fw.frame())) {
                int rgb = age > 5 ? fw.fades().get(0) : fw.colors().get(k++ % fw.colors().size());
                c.splat(fw.x() + d.getX(), fw.y() + d.getY(), fw.z() + d.getZ(), rgb, 0.08, 1.2 * w);
            }
        }
        for (DeathShow.Bolt b : DeathShow.bolts(v, H)) {
            if (f < b.frame() || f > b.frame() + 1) continue;
            Random rnd = new Random(b.frame() * 31L + (long) (b.x() * 100));
            double x = b.x(), z = b.z();
            for (double y = 28; y > 0; y -= 0.25) {
                x += (rnd.nextDouble() - 0.5) * 0.35;
                z += (rnd.nextDouble() - 0.5) * 0.35;
                c.splat(x, y, z, 0xDDE8FF, 0.07, f == b.frame() ? 1.4 : 0.5);
            }
        }
    }

    /** A display piece as dots over its surface (rings, discs and glyphs by their pictures). */
    static void drawPiece(Canvas c, DeathPieces.Piece p, DeathPieces.Pose pose) {
        if (pose.hidden()) return;
        List<double[]> local = new java.util.ArrayList<>();
        String look = p.look();
        int rgb = p.rgb();
        double intensity = p.bright() ? 1.0 : 0.45;
        boolean tile = p.kind() == DeathPieces.Kind.BLOCK && !p.bright() && !p.glow();
        if (look.startsWith("ring_")) {
            for (int i = 0; i < 90; i++) {
                double a = i * Math.PI * 2 / 90;
                local.add(new double[] { Math.cos(a) * 0.45, Math.sin(a) * 0.45, 0 });
            }
        } else if (look.equals("corona")) {
            for (int i = 0; i < 60; i++) {
                double a = i * 2.4, r = Math.sqrt(i / 60.0) * 0.45;
                local.add(new double[] { Math.cos(a) * r, Math.sin(a) * r, 0 });
            }
            intensity = 1.6;
        } else if (look.startsWith("disc")) {
            for (int arm = 0; arm < 3; arm++) {
                for (int i = 0; i < 30; i++) {
                    double r = 0.1 + 0.38 * i / 29.0, a = arm * 2.09 + Math.log(r) * 4;
                    local.add(new double[] { Math.cos(a) * r, Math.sin(a) * r, 0 });
                }
            }
        } else if (look.startsWith("beam_")) {
            // A soft shaft: a bright core and a wider faint halo, fading toward its top.
            int n = (int) Math.max(4, pose.sy() * 3);
            for (int i = 0; i < n; i++) {
                double y = (i + 0.5) / n - 0.5;
                local.add(new double[] { 0, y, 0 });
                local.add(new double[] { 0.25, y, 0 });
                local.add(new double[] { -0.25, y, 0 });
            }
            intensity = 0.55;
        } else {
            int n = tile ? 3 : 4;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    double u = (i + 0.5) / n - 0.5, w = (j + 0.5) / n - 0.5;
                    local.add(new double[] { u, 0.5, w });
                    local.add(new double[] { u, w, 0.5 });
                    local.add(new double[] { 0.5, u, w });
                    if (!tile) {
                        local.add(new double[] { u, w, -0.5 });
                        local.add(new double[] { -0.5, u, w });
                    }
                }
            }
            if (tile) {
                rgb = 0x5A7A38;
                intensity = 0.3;
            }
            if (look.contains("end_rod")) rgb = 0xFFFFFF;
            if (look.contains("black_concrete") || look.contains("obsidian")) rgb = 0x080010;
        }
        double cy = Math.cos(pose.yaw()), sy = Math.sin(pose.yaw());
        double cx = Math.cos(pose.pitch()), sx = Math.sin(pose.pitch());
        double cz = Math.cos(pose.roll()), sz = Math.sin(pose.roll());
        for (double[] q : local) {
            double x = q[0] * pose.sx(), y = q[1] * pose.sy(), z = q[2] * pose.sz();
            double x1 = x * cz - y * sz, y1 = x * sz + y * cz, z1 = z; // roll
            double y2 = y1 * cx - z1 * sx, z2 = y1 * sx + z1 * cx, x2 = x1; // pitch
            double x3 = x2 * cy + z2 * sy, z3 = -x2 * sy + z2 * cy, y3 = y2; // yaw
            c.splat(pose.x() + x3, pose.y() + y3, pose.z() + z3, rgb, 0.05, intensity);
        }
    }
}
