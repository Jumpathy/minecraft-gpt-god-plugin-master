package net.bigyous.gptgodmc.god.art;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

import net.bigyous.gptgodmc.god.art.SpritePainter.Art;

/**
 * Pictures of a painted sprite. The proof sheet is what the artist is shown after every attempt: the
 * sprite large on a dark ground with its rows and columns numbered (so a fault can be named by where it
 * is), the same on a bright ground (a thing must read on both), turned three quarters so its thickness
 * shows, at the size it has in a hotbar, and its frames side by side. Also the strip Minecraft reads as
 * an animated texture. No fonts needed: the few digits are drawn from a built-in bitmap, so it works on
 * a headless server.
 */
public final class SpriteSheet {
    private SpriteSheet() {
    }

    private static final int BACK = 0x0E0A14, DARK = 0x17111F, DARK_ALT = 0x1E1728, SKY = 0x8FB8E8, GRASS = 0x5E8A3C,
            SLOT = 0x8B8B8B, INK = 0x9A8FB0;
    private static final int FRAMES_ACROSS = 4;

    /** Digits 0-9, 3 wide and 5 tall, bits row by row. */
    private static final int[] DIGITS = { 0b111101101101111, 0b010110010010111, 0b111001111100111, 0b111001111001111,
            0b101101111001001, 0b111100111001111, 0b111100111101111, 0b111001001001001, 0b111101111101111,
            0b111101111001111 };

    /** The proof sheet for one attempt. */
    public static BufferedImage proof(Art art) {
        int w = art.w(), h = art.h();
        int k = Math.max(4, Math.min(22, 352 / Math.max(w, h)));
        int margin = 18, big = margin + w * k, tall = margin + h * k;
        int small = Math.max(2, k / 2);
        BufferedImage turned = RelicForm.turned(art, 0, small, DARK);
        int frames = FRAMES_ACROSS * (w * 2 + 4);
        int side = Math.max(w * small + 8 + turned.getWidth(), frames);
        int upper = Math.max(h * small, turned.getHeight());
        int rows = (art.frames().length + FRAMES_ACROSS - 1) / FRAMES_ACROSS;
        BufferedImage img = new BufferedImage(big + 12 + side + 8,
                Math.max(tall, 4 + upper + 8 + h * 2 + 10 + rows * (h * 2 + 4)) + 8, BufferedImage.TYPE_INT_RGB);
        fill(img, 0, 0, img.getWidth(), img.getHeight(), BACK);
        // Large, on a dark checker, numbered.
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int ground = ((x / 4 + y / 4) & 1) == 0 ? DARK : DARK_ALT;
                fill(img, margin + x * k, margin + y * k, k, k, over(ground, art.at(0, x, y)));
            }
        }
        for (int x = 0; x < w; x += 4) number(img, x, margin + x * k + 1, 4);
        for (int y = 0; y < h; y += 4) number(img, y, 1, margin + y * k + 1);
        // On sky and grass, half the size; beside it, turned three quarters so its thickness shows.
        int ox = big + 12, oy = 4;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                fill(img, ox + x * small, oy + y * small, small, small, over(y * 5 < h * 3 ? SKY : GRASS, art.at(0, x, y)));
            }
        }
        Graphics2D g = img.createGraphics();
        g.drawImage(turned, ox + w * small + 8, oy, null);
        g.dispose();
        // As in a hotbar slot, at twice true size and at true size; then the frames.
        oy += upper + 8;
        fill(img, ox, oy, w * 2 + 4, h * 2 + 4, SLOT);
        blit(img, art, 0, ox + 2, oy + 2, 2, SLOT);
        fill(img, ox + w * 2 + 10, oy + (h * 2 + 4 - h - 2) / 2, w + 2, h + 2, SLOT);
        blit(img, art, 0, ox + w * 2 + 11, oy + (h * 2 + 4 - h - 2) / 2 + 1, 1, SLOT);
        oy += h * 2 + 10;
        for (int f = 0; f < art.frames().length; f++) {
            blit(img, art, f, ox + (f % FRAMES_ACROSS) * (w * 2 + 4), oy + (f / FRAMES_ACROSS) * (h * 2 + 4), 2, DARK);
        }
        return img;
    }

    /** Two pictures one above the other (a relic's two forms on one sheet). */
    public static BufferedImage stacked(BufferedImage top, BufferedImage bottom) {
        BufferedImage img = new BufferedImage(Math.max(top.getWidth(), bottom.getWidth()),
                top.getHeight() + 6 + bottom.getHeight(), BufferedImage.TYPE_INT_RGB);
        fill(img, 0, 0, img.getWidth(), img.getHeight(), BACK);
        Graphics2D g = img.createGraphics();
        g.drawImage(top, 0, 0, null);
        g.drawImage(bottom, 0, top.getHeight() + 6, null);
        g.dispose();
        return img;
    }

    /** One frame enlarged over a ground color, for previews. */
    public static BufferedImage enlarged(Art art, int frame, int scale, int ground) {
        BufferedImage img = new BufferedImage(art.w() * scale, art.h() * scale, BufferedImage.TYPE_INT_RGB);
        blit(img, art, frame, 0, 0, scale, ground);
        return img;
    }

    /** The frames stacked top to bottom with their alpha, as Minecraft reads an animated texture. */
    public static BufferedImage strip(int w, int h, int[][] frames) {
        BufferedImage img = new BufferedImage(w, h * frames.length, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < frames.length; f++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) img.setRGB(x, f * h + y, frames[f][y * w + x]);
            }
        }
        return img;
    }

    public static byte[] png(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void blit(BufferedImage img, Art art, int frame, int ox, int oy, int scale, int ground) {
        for (int y = 0; y < art.h(); y++) {
            for (int x = 0; x < art.w(); x++) {
                fill(img, ox + x * scale, oy + y * scale, scale, scale, over(ground, art.at(frame, x, y)));
            }
        }
    }

    private static int over(int ground, int argb) {
        double a = (argb >>> 24) / 255.0;
        int r = (int) Math.round(((argb >> 16) & 255) * a + ((ground >> 16) & 255) * (1 - a));
        int g = (int) Math.round(((argb >> 8) & 255) * a + ((ground >> 8) & 255) * (1 - a));
        int b = (int) Math.round((argb & 255) * a + (ground & 255) * (1 - a));
        return (r << 16) | (g << 8) | b;
    }

    private static void fill(BufferedImage img, int x, int y, int w, int h, int rgb) {
        for (int py = Math.max(0, y); py < Math.min(img.getHeight(), y + h); py++) {
            for (int px = Math.max(0, x); px < Math.min(img.getWidth(), x + w); px++) img.setRGB(px, py, rgb);
        }
    }

    /** A row or column number in the margin. */
    private static void number(BufferedImage img, int n, int x, int y) {
        String s = Integer.toString(n);
        for (int i = 0; i < s.length(); i++) {
            int bits = DIGITS[s.charAt(i) - '0'];
            for (int gy = 0; gy < 5; gy++) {
                for (int gx = 0; gx < 3; gx++) {
                    if (((bits >> (14 - (gy * 3 + gx))) & 1) == 1) fill(img, x + i * 8 + gx * 2, y + gy * 2, 2, 2, INK);
                }
            }
        }
    }
}
