package net.bigyous.gptgodmc.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Draws the guide's book pages as the game shows them, with the vanilla font and book texture read at test
 * time from the local Minecraft 26.3 client jar (nothing from it is copied into the repository), into
 * build/test-renders/guide-book-player.png and guide-book-operator.png, plus the plain text of every page
 * (guide-book-*.txt). Also checks GuideBook's character widths against the real glyphs. Skipped when the
 * client jar is not there.
 */
class GuidePreviewTest {
    static final File JAR = new File(System.getenv().getOrDefault("APPDATA", ""), ".minecraft/versions/26.3/26.3.jar");
    static final Path OUT = Path.of("build/test-renders");
    static final int S = 2;
    static BufferedImage font, book;

    @BeforeAll
    static void load() throws Exception {
        if (!JAR.isFile()) return;
        try (ZipFile zip = new ZipFile(JAR)) {
            font = ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/font/ascii.png")));
            book = ImageIO.read(zip.getInputStream(zip.getEntry("assets/minecraft/textures/gui/book.png")));
        }
    }

    /** The glyph's advance as the game measures it: its rightmost lit column + 1, plus a 1-pixel gap. */
    static int realAdvance(char c) {
        if (c == ' ') return 4;
        int cell = font.getWidth() / 16;
        int gx = (c % 16) * cell, gy = (c / 16) * cell;
        int right = -1;
        for (int x = 0; x < cell; x++) {
            for (int y = 0; y < cell; y++) {
                if ((font.getRGB(gx + x, gy + y) >>> 24) != 0) right = Math.max(right, x);
            }
        }
        return (right + 1) * 8 / cell + 1;
    }

    @Test
    void widthsMatchTheVanillaFont() {
        assumeTrue(font != null, "no Minecraft 26.3 client jar");
        for (char c = 32; c < 127; c++) {
            assertEquals(realAdvance(c), GuideBook.advance(c), "advance of '" + c + "'");
        }
    }

    @Test
    void renderBooks() throws Exception {
        assumeTrue(font != null && book != null, "no Minecraft 26.3 client jar");
        Files.createDirectories(OUT);
        render("player", GuideTest.player());
        render("operator", GuideTest.operator());
    }

    private static void render(String name, Guide.Book b) throws Exception {
        List<GuideBook.Page> pages = GuideBook.pages(b);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            text.append("---- page ").append(i + 1).append(" of ").append(pages.size()).append(" ----\n");
            text.append(pages.get(i).text());
        }
        Files.writeString(OUT.resolve("guide-book-" + name + ".txt"), text, StandardCharsets.UTF_8);

        int cols = 5, rows = (pages.size() + cols - 1) / cols, pw = 192 * S, ph = 192 * S, gap = 8 * S;
        BufferedImage img = new BufferedImage(cols * (pw + gap) + gap, rows * (ph + gap) + gap, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) img.setRGB(x, y, 0x2B2B33);
        }
        for (int i = 0; i < pages.size(); i++) {
            int ox = gap + (i % cols) * (pw + gap), oy = gap + (i / cols) * (ph + gap);
            for (int y = 0; y < 192; y++) {
                for (int x = 0; x < 192; x++) {
                    int argb = book.getRGB(x * book.getWidth() / 256, y * book.getHeight() / 256);
                    if ((argb >>> 24) == 0) argb = 0x2B2B33;
                    fill(img, ox + x * S, oy + y * S, argb);
                }
            }
            String num = "Page " + (i + 1) + " of " + pages.size();
            draw(img, ox, oy, 192 - 44 - GuideBook.width(num, false), 18, num, 0x000000, false);
            List<GuideBook.Line> lines = pages.get(i).lines();
            for (int l = 0; l < lines.size(); l++) {
                int x = 36;
                for (GuideBook.Run r : lines.get(l).runs()) {
                    int color = r.color() >= 0 ? r.color() : r.style().color >= 0 ? r.style().color : 0x000000;
                    x = draw(img, ox, oy, x, 32 + l * 9, r.text(), color, r.style().bold);
                }
            }
        }
        ImageIO.write(img, "png", OUT.resolve("guide-book-" + name + ".png").toFile());
    }

    /** Draws text at book pixel (x, y); returns the x after it. */
    private static int draw(BufferedImage img, int ox, int oy, int x, int y, String s, int rgb, boolean bold) {
        int cell = font.getWidth() / 16;
        for (char c : s.toCharArray()) {
            if (c < 128 && c != ' ') {
                int gx = (c % 16) * cell, gy = (c / 16) * cell;
                for (int py = 0; py < cell; py++) {
                    for (int px = 0; px < cell; px++) {
                        if ((font.getRGB(gx + px, gy + py) >>> 24) == 0) continue;
                        int bx = x * cell / 8 + px, by = y * cell / 8 + py; // in glyph pixels
                        for (int k = 0; k <= (bold ? 1 : 0); k++) {
                            dot(img, ox, oy, bx + k * cell / 8, by, cell, rgb);
                        }
                    }
                }
            }
            x += GuideBook.advance(c) + (bold ? 1 : 0);
        }
        return x;
    }

    private static void dot(BufferedImage img, int ox, int oy, int gx, int gy, int cell, int rgb) {
        int size = Math.max(1, S * 8 / cell);
        int x0 = ox + gx * S * 8 / cell, y0 = oy + gy * S * 8 / cell;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (x0 + x < img.getWidth() && y0 + y < img.getHeight()) img.setRGB(x0 + x, y0 + y, rgb);
            }
        }
    }

    private static void fill(BufferedImage img, int x0, int y0, int rgb) {
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) img.setRGB(x0 + x, y0 + y, rgb);
        }
    }
}
