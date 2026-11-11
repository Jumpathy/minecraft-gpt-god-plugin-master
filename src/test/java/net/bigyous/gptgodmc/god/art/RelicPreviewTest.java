package net.bigyous.gptgodmc.god.art;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.art.SpritePainter.Art;

/**
 * Paints the example sprites to build/test-renders/: relic-samples.png (each one large on a dark ground,
 * on sky and grass, at hotbar size, and its frames) and relic-proof.png (the sheet the artist model is
 * shown after an attempt), so the painter's craft can be judged without a game client.
 */
class RelicPreviewTest {
    static final File OUT = new File("build/test-renders");

    static void save(BufferedImage img, String name) throws IOException {
        OUT.mkdirs();
        ImageIO.write(img, "png", new File(OUT, name + ".png"));
    }

    static void label(Graphics2D g, String text, int x, int y, int size, int rgb) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
        g.setColor(new Color(rgb));
        g.drawString(text, x, y);
    }

    /** One example in a cell: large on dark, smaller on sky and grass, in a hotbar slot, and its frames. */
    static BufferedImage cell(Samples.Sample sample, int cw, int ch) {
        Sprite sprite = sample.sprite();
        Art art = SpritePainter.paint(sprite, sample.kind().equals("spirit"));
        BufferedImage img = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x17111F));
        g.fillRect(0, 0, cw, ch);
        int big = 240 / Math.max(art.w(), art.h());
        g.drawImage(SpriteSheet.enlarged(art, 0, big, 0x17111F), 16, 34, null);
        int small = Math.max(2, big / 3);
        int sx = 16 + 240 + 14;
        BufferedImage world = new BufferedImage(art.w() * small, art.h() * small, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < world.getHeight(); y++) {
            for (int x = 0; x < world.getWidth(); x++) world.setRGB(x, y, y * 5 < world.getHeight() * 3 ? 0x8FB8E8 : 0x5E8A3C);
        }
        Graphics2D wg = world.createGraphics();
        for (int y = 0; y < art.h(); y++) {
            for (int x = 0; x < art.w(); x++) {
                int argb = art.at(0, x, y);
                if ((argb >>> 24) == 0) continue;
                wg.setColor(new Color(argb, true));
                wg.fillRect(x * small, y * small, small, small);
            }
        }
        wg.dispose();
        g.drawImage(world, sx, 34, null);
        int sy = 34 + world.getHeight() + 10;
        g.setColor(new Color(0x8B8B8B));
        g.fillRect(sx, sy, art.w() * 2 + 4, art.h() * 2 + 4);
        g.drawImage(SpriteSheet.enlarged(art, 0, 2, 0x8B8B8B), sx + 2, sy + 2, null);
        sy += art.h() * 2 + 14;
        for (int f = 0; f < SpritePainter.FRAMES; f++) {
            g.drawImage(SpriteSheet.enlarged(art, f, 2, 0x17111F), sx + (f % 4) * (art.w() * 2 + 3),
                    sy + (f / 4) * (art.h() * 2 + 3), null);
        }
        label(g, sample.name(), 16, 22, 15, 0xE8DCF5);
        label(g, sample.kind() + ", " + art.w() + "x" + art.h(), 16 + g.getFontMetrics().stringWidth(sample.name()) + 10, 22,
                12, 0x9A8FB0);
        g.dispose();
        return img;
    }

    /** A canvas to plot larger examples on, for checking that the painter holds up at 32 texels. */
    private static final class Plot {
        final char[][] c;

        Plot(int w, int h) {
            c = new char[h][w];
            for (char[] row : c) java.util.Arrays.fill(row, '.');
        }

        void set(int x, int y, char ch) {
            if (y >= 0 && y < c.length && x >= 0 && x < c[0].length) c[y][x] = ch;
        }

        void disc(double cx, double cy, double rx, double ry, char ch) {
            for (int y = 0; y < c.length; y++) {
                for (int x = 0; x < c[0].length; x++) {
                    double dx = (x - cx) / rx, dy = (y - cy) / ry;
                    if (dx * dx + dy * dy <= 1) c[y][x] = ch;
                }
            }
        }

        String[] rows() {
            String[] out = new String[c.length];
            for (int y = 0; y < c.length; y++) out[y] = new String(c[y]);
            return out;
        }
    }

    /** A greatsword whose blade is a strip of night sky edged in silver. */
    static Samples.Sample greatsword() {
        Plot p = new Plot(32, 32);
        for (int j = 0; j <= 6; j++) {
            p.set(7 - j, 23 + j, 'l');
            p.set(7 - j, 24 + j, 'l');
        }
        for (int y = 2; y <= 21; y++) {
            int c = 30 - y;
            for (int x = c - 2; x <= c + 2; x++) p.set(x, y, x == c - 2 || x == c + 2 ? 's' : 'v');
        }
        p.set(29, 0, 's');
        p.set(30, 0, 's');
        p.set(28, 1, 's');
        p.set(29, 1, 'v');
        p.set(30, 1, 's');
        for (int k = -6; k <= 6; k++) {
            p.set(8 + k, 22 + k, 'g');
            p.set(9 + k, 22 + k, 'g');
            if (Math.abs(k) >= 5) p.set(8 + k, 21 + k, 'g');
        }
        p.set(8, 22, 'r');
        p.set(9, 22, 'r');
        p.set(0, 30, 'g');
        p.set(1, 30, 'r');
        p.set(0, 31, 'g');
        p.set(1, 31, 'g');
        p.set(2, 30, 'g');
        return new Samples.Sample("Starfall Greatsword", "blade", "",
                new String[] { "s", "silver", "v", "void", "g", "gold", "l", "leather", "r", "gem #7a5cff" }, p.rows());
    }

    /** A winged eye: bone white round an amber iris, feathers either side, a ring of gold above. */
    static Samples.Sample watcher() {
        Plot p = new Plot(32, 32);
        for (int side = -1; side <= 1; side += 2) {
            for (int f = 0; f < 4; f++) {
                // Feathers fan out and down from beside the eye.
                double ang = Math.toRadians(-28 + f * 26);
                for (int i = 0; i < 12 - f; i++) {
                    double x = 16 + side * (7 + Math.cos(ang) * i), y = 15 + Math.sin(ang) * i;
                    p.set((int) Math.round(x), (int) Math.round(y), 'f');
                    p.set((int) Math.round(x), (int) Math.round(y) + 1, 'f');
                }
            }
        }
        p.disc(15.5, 6.5, 6.5, 2.6, 'g');
        p.disc(15.5, 6.5, 4.6, 1.3, '.');
        p.disc(15.5, 16, 8.2, 5.2, 'g');
        p.disc(15.5, 16, 7.2, 4.2, 'b');
        p.disc(15.5, 16, 3.4, 3.4, 'i');
        p.disc(15.5, 16, 1.4, 1.4, 'p');
        for (int i = 0; i < 6; i++) p.set(15 + (i % 2), 22 + i, 'o');
        return new Samples.Sample("The Watcher", "spirit", "", new String[] { "f", "hide #f1ecf6", "g", "gold", "b", "bone",
                "i", "gem #e0a020", "p", "#05040a deep", "o", "glow #ffd86a" }, p.rows());
    }

    @Test
    void renderSamples() throws IOException {
        List<Samples.Sample> all = new java.util.ArrayList<>(Samples.ALL);
        all.add(greatsword());
        all.add(watcher());
        int cw = 470, ch = 290, cols = 3, rows = (all.size() + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cw * cols, ch * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x17111F));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < all.size(); i++) g.drawImage(cell(all.get(i), cw, ch), (i % cols) * cw, (i / cols) * ch, null);
        g.dispose();
        save(sheet, "relic-samples");
        assertTrue(new File(OUT, "relic-samples.png").isFile());
    }

    /**
     * The exact messages the plugin sends the client mod for every example, one file each in
     * build/test-renders/relic-messages/, so the mod's own tests can read real ones.
     */
    @Test
    void writeModMessages() throws IOException {
        File dir = new File(OUT, "relic-messages");
        dir.mkdirs();
        Relics.load(java.nio.file.Files.createTempDirectory("relics"));
        try {
            List<Samples.Sample> all = new java.util.ArrayList<>(Samples.ALL);
            all.add(greatsword());
            all.add(watcher());
            for (Samples.Sample s : all) {
                Relics.Relic r = Relics.add("samples", "the first artist", s.name(), s.kind(), s.note(), s.sprite(),
                        List.of(s.powers()), 1);
                com.google.gson.JsonObject m = net.bigyous.gptgodmc.client.ModRelics.message(r, Relics.art(r));
                assertTrue(m != null, s.name() + " fits in one message");
                java.nio.file.Files.writeString(new File(dir, r.id() + ".json").toPath(),
                        net.bigyous.gptgodmc.client.ModMessages.json(m), java.nio.charset.StandardCharsets.UTF_8);
                Art awake = Relics.artOn(r);
                if (awake == null) continue;
                // A relic with two forms sends its awakened form as a second message, under <id>__on.
                com.google.gson.JsonObject on = net.bigyous.gptgodmc.client.ModRelics.message(r.id() + Relics.ON, r, awake);
                assertTrue(on != null, s.name() + " awake fits in one message");
                java.nio.file.Files.writeString(new File(dir, r.id() + Relics.ON + ".json").toPath(),
                        net.bigyous.gptgodmc.client.ModMessages.json(on), java.nio.charset.StandardCharsets.UTF_8);
            }
            // And the resource pack players without the mod are sent, holding the same relics as solid models.
            java.nio.file.Files.write(new File(OUT, "relic-pack.zip").toPath(),
                    net.bigyous.gptgodmc.god.vessel.VesselPack.build().zip());
        } finally {
            Relics.load(null);
        }
        assertTrue(new File(dir, "dawnbreaker.json").isFile());
    }

    @Test
    void renderProofSheet() throws IOException {
        save(SpriteSheet.proof(SpritePainter.paint(Samples.BLADE.sprite(), false)), "relic-proof");
        assertTrue(new File(OUT, "relic-proof.png").isFile());
        // What the artist is shown for a relic with two forms: asleep above, awake below.
        Atelier.Attempt both = Atelier.attempt(Samples.KINDLED.json(), "blade", 2, true);
        java.nio.file.Files.write(new File(OUT, "relic-proof-two-forms.png").toPath(), both.proof());
    }

    /** One relic as an object: from the front, and turned three quarters so its thickness shows. */
    static BufferedImage formCell(String title, String caption, Art art, int cw, int ch) {
        BufferedImage img = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x17111F));
        g.fillRect(0, 0, cw, ch);
        int px = Math.max(4, 208 / Math.max(art.w(), art.h()));
        g.drawImage(SpriteSheet.enlarged(art, 0, px, 0x17111F), 14, 40, null);
        BufferedImage turned = RelicForm.turned(art, 0, px, 0x17111F);
        g.drawImage(turned, 14 + art.w() * px + 16, 40 + (art.h() * px - turned.getHeight()) / 2, null);
        label(g, title, 14, 24, 17, 0xE9DDFB);
        label(g, caption, 14, ch - 12, 12, 0x9A8FB0);
        g.dispose();
        return img;
    }

    /**
     * relic-forms.png: relics as solid things. Plain ones are the painting given an even thickness;
     * special ones are sculpted (thick, rounded, films) and may have a second, awakened form.
     */
    @Test
    void renderForms() throws IOException {
        record Shown(String title, String caption, Art art) {
        }
        List<Shown> all = new java.util.ArrayList<>();
        for (Samples.Sample s : List.of(Samples.BLADE, Samples.AMULET, greatsword())) {
            Art art = SpritePainter.paint(s.sprite(), false);
            all.add(new Shown(s.name(), "plain: the painting, " + s.sprite().usualThick() + " texel"
                    + (s.sprite().usualThick() == 1 ? "" : "s") + " thick, sides shaded, " + RelicForm.bars(art).size()
                    + " boxes", art));
        }
        Sprite kindled = Samples.KINDLED.sprite();
        Art asleep = SpritePainter.paint(kindled, false), awake = SpritePainter.paint(kindled.alt(), false);
        all.add(new Shown(Samples.KINDLED.name() + ", asleep", "special, two forms: a sculpted hilt until it is woken",
                asleep));
        all.add(new Shown(Samples.KINDLED.name() + ", awake", "right-click: a blade of light, lit by the world like any "
                + "item", awake));
        Art vial = SpritePainter.paint(Samples.VIAL.sprite(), false);
        all.add(new Shown(Samples.VIAL.name(), "light painted bright, shaded like everything else", vial));
        int cw = 470, ch = 300, cols = 3, rows = (all.size() + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cw * cols, ch * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x17111F));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < all.size(); i++) {
            Shown s = all.get(i);
            g.drawImage(formCell(s.title(), s.caption(), s.art(), cw, ch), (i % cols) * cw, (i / cols) * ch, null);
            assertTrue(RelicForm.model(s.art().squared(), "t", true) != null, s.title() + " fits in boxes");
        }
        g.dispose();
        save(sheet, "relic-forms");
        assertTrue(new File(OUT, "relic-forms.png").isFile());
    }
}
