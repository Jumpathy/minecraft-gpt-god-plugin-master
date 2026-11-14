package net.bigyous.gptgodmc.god;

import java.awt.image.BufferedImage;
import java.util.SplittableRandom;

/** Paints test skins in Minecraft's skin layout: a body of one skin tone, then hair, clothes, layers. */
public final class SkinPainter {
    public final BufferedImage img;
    private final SplittableRandom random = new SplittableRandom(7);

    /** A bare body: every base face in the skin tone (lightly shaded), with eyes. */
    public SkinPainter(int skin, boolean legacy) {
        img = new BufferedImage(64, legacy ? 32 : 64, BufferedImage.TYPE_INT_ARGB);
        fill(0, 0, 32, 16, skin, true); // head
        fill(16, 16, 24, 16, skin, true); // body
        fill(0, 16, 16, 16, skin, true); // right leg
        fill(40, 16, 16, 16, skin, true); // right arm
        if (!legacy) {
            fill(16, 48, 16, 16, skin, true); // left leg
            fill(32, 48, 16, 16, skin, true); // left arm
        }
        // Eyes.
        px(9, 12, 0xFFFFFF);
        px(10, 12, 0x3050A0);
        px(13, 12, 0x3050A0);
        px(14, 12, 0xFFFFFF);
        px(11, 14, darker(skin));
        px(12, 14, darker(skin));
    }

    public static SkinPainter person(int skin) {
        return new SkinPainter(skin, false);
    }

    public SkinPainter hair(int c) {
        fill(8, 0, 8, 8, c, false); // top
        fill(8, 8, 8, 1, c, false); // fringe
        fill(8, 9, 1, 2, c, false);
        fill(15, 9, 1, 2, c, false);
        fill(0, 8, 8, 4, c, false); // sides and back
        fill(16, 8, 8, 4, c, false);
        fill(24, 8, 8, 8, c, false);
        return this;
    }

    /** A shirt with short sleeves (body rows 20..29, both arms' top rows). */
    public SkinPainter shirt(int c) {
        fill(16, 20, 24, 10, c, false);
        fill(40, 20, 16, 4, c, false);
        if (img.getHeight() == 64) fill(32, 52, 16, 4, c, false);
        return this;
    }

    /** Trousers from the belt to the ankles. */
    public SkinPainter trousers(int c) {
        fill(16, 30, 24, 2, c, false); // belt
        fill(0, 20, 16, 10, c, false);
        if (img.getHeight() == 64) fill(16, 52, 16, 10, c, false);
        return this;
    }

    /** Shorts down to above the knee. */
    public SkinPainter shorts(int c) {
        fill(16, 30, 24, 2, c, false);
        fill(0, 20, 16, 5, c, false);
        if (img.getHeight() == 64) fill(16, 52, 16, 5, c, false);
        return this;
    }

    /** A strip at the hips only. */
    public SkinPainter underwear(int c) {
        fill(16, 29, 24, 3, c, false);
        fill(0, 20, 16, 2, c, false);
        if (img.getHeight() == 64) fill(16, 52, 16, 2, c, false);
        return this;
    }

    public SkinPainter shoes(int c) {
        fill(0, 30, 16, 2, c, false);
        if (img.getHeight() == 64) fill(16, 62, 16, 2, c, false);
        return this;
    }

    /** A jacket on the second layer over the torso (front and back). */
    public SkinPainter jacket(int c) {
        fill(16, 36, 24, 12, c, false);
        return this;
    }

    /** A hood on the hat layer: the top and a ring round the face, the face left open. */
    public SkinPainter hood(int c) {
        fill(40, 0, 8, 8, c, false);
        fill(40, 8, 8, 1, c, false);
        fill(40, 8, 1, 8, c, false);
        fill(47, 8, 1, 8, c, false);
        fill(32, 8, 8, 8, c, false);
        fill(48, 8, 16, 8, c, false);
        return this;
    }

    /** A mask over the whole face on the hat layer. */
    public SkinPainter mask(int c) {
        fill(40, 0, 8, 8, c, false);
        fill(40, 8, 8, 8, c, false);
        return this;
    }

    void fill(int x0, int y0, int w, int h, int c, boolean shade) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                int rgb = shade ? jitter(c) : c;
                img.setRGB(x, y, 0xFF000000 | rgb);
            }
        }
    }

    void px(int x, int y, int c) {
        img.setRGB(x, y, 0xFF000000 | c);
    }

    /** A skin artist's shading: each texel a little lighter or darker. */
    int jitter(int c) {
        int d = random.nextInt(-10, 11);
        int r = clamp(((c >> 16) & 255) + d), g = clamp(((c >> 8) & 255) + d), b = clamp((c & 255) + d);
        return r << 16 | g << 8 | b;
    }

    static int darker(int c) {
        return clamp(((c >> 16) & 255) - 40) << 16 | clamp(((c >> 8) & 255) - 40) << 8 | clamp((c & 255) - 40);
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
