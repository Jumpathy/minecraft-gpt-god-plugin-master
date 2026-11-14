package net.bigyous.gptgodmc.god.vision;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.Avatars;
import net.bigyous.gptgodmc.god.SkinPainter;

/**
 * Draws build/test-renders/avatars.png: painted test skins from the front, each with the words the
 * god reads in that mortal's line ("looks: ...").
 */
class AvatarsPreviewTest {
    record Sample(String title, SkinPainter skin, boolean chestArmor, boolean legArmor) {
    }

    static final int LIGHT = 0xE0AC8C, DEEP = 0x5C3A28, OLIVE = 0xB98A64;

    @Test
    void drawsTheLooks() throws IOException {
        List<Sample> samples = List.of(
                new Sample("clothed", SkinPainter.person(LIGHT).hair(0x4A2E18).shirt(0x00A8A8).trousers(0x3030A0)
                        .shoes(0x6E6E6E), false, false),
                new Sample("shirtless", SkinPainter.person(DEEP).hair(0x161616).trousers(0x3030A0), false, false),
                new Sample("shorts", SkinPainter.person(OLIVE).hair(0x161616).shirt(0xC02020).shorts(0x203060),
                        false, false),
                new Sample("underwear", SkinPainter.person(LIGHT).hair(0xD8B040).underwear(0xF0F0F0), false, false),
                new Sample("naked", SkinPainter.person(LIGHT).hair(0x4A2E18), false, false),
                new Sample("naked, deep tone", SkinPainter.person(DEEP).hair(0x161616), false, false),
                new Sample("naked in iron armor", SkinPainter.person(LIGHT).hair(0x4A2E18), true, true),
                new Sample("hooded", SkinPainter.person(LIGHT).hair(0x161616).shirt(0x3A3A3A).trousers(0x2A2A2A)
                        .hood(0x2E5E2E), false, false),
                new Sample("masked", SkinPainter.person(LIGHT).hair(0x161616).shirt(0x7030B0).trousers(0x161616)
                        .mask(0x8A8A8A), false, false),
                new Sample("creeper", SkinPainter.person(0x4CAF50), false, false),
                new Sample("jacket over bare skin", SkinPainter.person(LIGHT).hair(0x161616).jacket(0xA02020),
                        false, false),
                new Sample("old 64x32 skin", new SkinPainter(LIGHT, true).hair(0x161616).shirt(0x00A8A8)
                        .trousers(0x3030A0), false, false));

        int cols = 4, cellW = 300, cellH = 330, scale = 6;
        int rows = (samples.size() + cols - 1) / cols;
        BufferedImage img = new BufferedImage(cols * cellW, rows * cellH + 30, BufferedImage.TYPE_INT_RGB);
        fill(img, 0, 0, img.getWidth(), img.getHeight(), 0x1E1F24);
        PixelFont.draw(img, "HOW THE GOD SEES A MORTAL'S SKIN", 10, 8, 0xFFFFFF, 2);
        for (int i = 0; i < samples.size(); i++) {
            Sample s = samples.get(i);
            int cx = (i % cols) * cellW, cy = 30 + (i / cols) * cellH;
            fill(img, cx + 6, cy + 6, cellW - 12, cellH - 12, 0x2B2D33);
            boolean legacy = s.skin().img.getHeight() == 32;
            BufferedImage front = front(s.skin().img, legacy);
            if (s.chestArmor()) armor(front, 0, 8, 16, 12);
            if (s.legArmor()) armor(front, 4, 20, 8, 10);
            int fx = cx + (cellW - 16 * scale) / 2, fy = cy + 30;
            blit(img, front, fx, fy, scale);
            PixelFont.draw(img, s.title().toUpperCase(), cx + 14, cy + 12, 0xFFD86B, 1);
            Avatars.Look look = Avatars.read(s.skin().img, false);
            String words = "looks: " + Avatars.phrase(look, s.chestArmor(), s.legArmor());
            int y = fy + 32 * scale + 8;
            for (String line : wrap(words, cellW - 28)) {
                PixelFont.draw(img, line, cx + 14, y, Avatars.exposed(look, s.chestArmor(), s.legArmor()) ? 0xFF7A6A
                        : 0xE6E6E6, 1);
                y += 11;
            }
        }
        File out = new File("build/test-renders/avatars.png");
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
    }

    /** The skin seen from the front: head, torso, arms and legs, second layer over the first. */
    static BufferedImage front(BufferedImage skin, boolean legacy) {
        BufferedImage out = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
        part(out, skin, 8, 8, 40, 8, 8, 8, 4, 0, true);
        part(out, skin, 20, 20, 20, 36, 8, 12, 4, 8, !legacy);
        part(out, skin, 44, 20, 44, 36, 4, 12, 0, 8, !legacy);
        if (legacy) part(out, skin, 44, 20, 44, 36, 4, 12, 12, 8, false);
        else part(out, skin, 36, 52, 52, 52, 4, 12, 12, 8, true);
        part(out, skin, 4, 20, 4, 36, 4, 12, 4, 20, !legacy);
        if (legacy) part(out, skin, 4, 20, 4, 36, 4, 12, 8, 20, false);
        else part(out, skin, 20, 52, 4, 52, 4, 12, 8, 20, true);
        return out;
    }

    static void part(BufferedImage out, BufferedImage skin, int bx, int by, int ox, int oy, int w, int h, int dx,
            int dy, boolean layer) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = skin.getRGB(bx + x, by + y);
                if (layer && oy + y < skin.getHeight()) {
                    int o = skin.getRGB(ox + x, oy + y);
                    if ((o >>> 24) >= 128) c = o;
                }
                out.setRGB(dx + x, dy + y, c);
            }
        }
    }

    /** Iron armor over a part: a light grey plate with a darker rim. */
    static void armor(BufferedImage front, int x0, int y0, int w, int h) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                boolean rim = x == x0 || x == x0 + w - 1 || y == y0 || y == y0 + h - 1;
                front.setRGB(x, y, 0xFF000000 | (rim ? 0x8C8C8C : 0xC8C8C8));
            }
        }
    }

    static void blit(BufferedImage img, BufferedImage src, int x0, int y0, int scale) {
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int c = src.getRGB(x, y);
                if ((c >>> 24) < 128) continue;
                fill(img, x0 + x * scale, y0 + y * scale, scale, scale, c & 0xFFFFFF);
            }
        }
    }

    static void fill(BufferedImage img, int x0, int y0, int w, int h, int rgb) {
        for (int y = y0; y < y0 + h && y < img.getHeight(); y++) {
            for (int x = x0; x < x0 + w && x < img.getWidth(); x++) {
                img.setRGB(x, y, rgb);
            }
        }
    }

    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (PixelFont.width(next, 1) > width && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }
}
