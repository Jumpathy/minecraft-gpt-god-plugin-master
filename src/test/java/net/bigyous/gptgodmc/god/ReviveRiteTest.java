package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReviveRiteTest {
    @Test
    void theRiftCreepsOpenGapesAtTheBurstAndCloses() {
        assertEquals(0, ReviveRite.width(-1));
        double last = 0;
        for (int t = 0; t <= ReviveRite.OPEN_TICKS; t += 2) {
            double w = ReviveRite.width(t);
            assertTrue(w > last, "still opening at tick " + t);
            last = w;
        }
        assertEquals(1.4, ReviveRite.width(ReviveRite.OPEN_TICKS), 1e-9);
        assertTrue(ReviveRite.width(ReviveRite.OPEN_TICKS + 8) < 1.4);
        assertEquals(0, ReviveRite.width(ReviveRite.OPEN_TICKS + ReviveRite.CLOSE_TICKS), 1e-9);
        assertEquals(0, ReviveRite.width(10_000), 1e-9);
    }

    @Test
    void theRiftIsATearPointedAtBothTips() {
        assertEquals(0, ReviveRite.half(0, 1.4));
        assertEquals(0, ReviveRite.half(ReviveRite.HEIGHT, 1.4));
        assertEquals(0.7, ReviveRite.half(ReviveRite.HEIGHT / 2, 1.4), 1e-9);
        assertTrue(ReviveRite.half(0.4, 1.4) < ReviveRite.half(1.2, 1.4));
    }

    /** Draws the rift's outline at five moments (build/test-renders/rite-revive.png), for a look without a server. */
    @Test
    void rendersTheRiftsShape() throws Exception {
        int[] ticks = { 4, 24, 44, ReviveRite.OPEN_TICKS, ReviveRite.OPEN_TICKS + 12 };
        int cell = 220, h = 420, scale = 100;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(cell * ticks.length, h,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(0x0E0C14));
        g.fillRect(0, 0, img.getWidth(), h);
        java.util.Random rnd = new java.util.Random(7);
        for (int i = 0; i < ticks.length; i++) {
            double w = ReviveRite.width(ticks[i]);
            int cx = i * cell + cell / 2, ground = h - 30;
            for (double y = 0.1; y < ReviveRite.HEIGHT; y += 0.03) {
                double half = ReviveRite.half(y, w);
                int py = ground - (int) (y * scale);
                g.setColor(new java.awt.Color(0x3B0A66));
                g.drawLine(cx - (int) (half * scale), py, cx + (int) (half * scale), py);
            }
            for (double y = 0.1; y < ReviveRite.HEIGHT; y += 0.22) {
                double half = ReviveRite.half(y, w) + (rnd.nextDouble() - 0.5) * 0.08;
                int py = ground - (int) (y * scale);
                g.setColor(new java.awt.Color(0xE9D5FF));
                g.fillOval(cx - (int) (half * scale) - 4, py - 4, 8, 8);
                g.fillOval(cx + (int) (half * scale) - 4, py - 4, 8, 8);
            }
            g.setColor(new java.awt.Color(0x9A9AA8));
            g.drawString("tick " + ticks[i] + (ticks[i] == ReviveRite.OPEN_TICKS ? " (burst: revived)" : ""), i * cell + 10, 18);
            g.setColor(new java.awt.Color(0x33303C));
            g.drawLine(i * cell + 20, ground, (i + 1) * cell - 20, ground);
        }
        g.dispose();
        java.nio.file.Path out = java.nio.file.Path.of("build/test-renders/rite-revive.png");
        java.nio.file.Files.createDirectories(out.getParent());
        javax.imageio.ImageIO.write(img, "png", out.toFile());
        assertTrue(java.nio.file.Files.size(out) > 1000);
    }
}
