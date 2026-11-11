package net.bigyous.gptgodmc.god;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * The Godslayer's own look in the vessel resource pack: a 16x16 netherite blade with a crimson crack
 * down its core that pulses (4 animated frames), an obsidian guard and an ember pommel. Painted from
 * code, deterministic. {@link #files()} are the pack entries; the blade's item_model is {@link #MODEL}.
 * Pure, so it is unit tested and previewed.
 */
public final class GodslayerArt {
    private GodslayerArt() {
    }

    public static final String MODEL = "gptgodmc:godslayer";
    public static final String ITEM_FILE = "assets/gptgodmc/items/godslayer.json";
    /** The Ascended Godslayer: a white-gold blade with a blazing core, one blow ends a god. */
    public static final String MODEL_ASCENDED = "gptgodmc:godslayer_ascended";
    public static final String ITEM_FILE_ASCENDED = "assets/gptgodmc/items/godslayer_ascended.json";
    public static final int FRAMES = 4;

    private static final int OUTLINE = 0xFF0C060E;
    private static final int EDGE_DARK = 0xFF2A2430;
    private static final int EDGE_LIGHT = 0xFF5A5060;
    private static final int GUARD = 0xFF45285C;
    private static final int GUARD_GEM = 0xFF9D4DFF;
    private static final int GRIP = 0xFF3B1F14;
    private static final int GRIP_WRAP = 0xFF6B1E1E;
    private static final int POMMEL = 0xFFFF8A1E;

    /** One frame (0..FRAMES-1) of the texture, 16x16 ARGB. */
    public static BufferedImage frame(int f) {
        return frame(f, false);
    }

    /** One frame of the plain or the ascended blade. */
    public static BufferedImage frame(int f, boolean ascended) {
        int edgeLight = ascended ? 0xFFFFF4C8 : EDGE_LIGHT;
        int edgeDark = ascended ? 0xFFC8962E : EDGE_DARK;
        int guardC = ascended ? 0xFFE8C060 : GUARD;
        int gem = ascended ? 0xFF6AF0FF : GUARD_GEM;
        int[][] px = new int[16][16];
        // Blade: the diagonal band x+y in 14..16 from the guard to the tip, light edge above, dark below.
        for (int x = 5; x <= 15; x++) {
            for (int s = 14; s <= 16; s++) {
                int y = s - x;
                if (y < 0 || y > 15) continue;
                if (x == 15 && s != 15) continue; // a sharp tip
                int c;
                if (s == 15) {
                    c = ascended ? radiance(x, f) : glow(x, f);
                } else {
                    c = s == 14 ? edgeLight : edgeDark;
                }
                px[y][x] = c;
            }
        }
        // Guard: across the blade at its root, with a violet gem in the middle.
        int[][] guard = { { 2, 9 }, { 3, 10 }, { 4, 11 }, { 5, 12 }, { 6, 13 } };
        for (int[] g : guard) px[g[1]][g[0]] = guardC;
        px[11][4] = gem;
        if (ascended) {
            // Wings on the guard.
            px[8][2] = guardC;
            px[13][7] = guardC;
        }
        // Grip and pommel.
        px[12][3] = GRIP_WRAP;
        px[13][2] = GRIP;
        px[14][1] = POMMEL;
        px[15][0] = 0xFFFFC266;
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = px[y][x];
                if (c == 0 && touches(px, x, y)) c = ascended ? 0xFF3A2208 : OUTLINE;
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    /** The ascended core: white light with a wave of gold and cyan running up it. */
    static int radiance(int x, int f) {
        double t = 0.5 + 0.5 * Math.cos((x - f * 2.5) * Math.PI / 5.0);
        int r = 0xFF;
        int g = 0xE0 + (int) (0x1F * t);
        int b = 0x90 + (int) (0x6F * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** The core crack: a wave of crimson-to-gold light running up the blade, one step a frame. */
    static int glow(int x, int f) {
        double t = 0.5 + 0.5 * Math.cos((x - f * 2.5) * Math.PI / 5.0);
        int r = 0xB0 + (int) (0x4F * t);
        int g = 0x10 + (int) (0x90 * t * t);
        int b = 0x10 + (int) (0x20 * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static boolean touches(int[][] px, int x, int y) {
        int[][] n = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] d : n) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && px[ny][nx] != 0 && px[ny][nx] != OUTLINE) return true;
        }
        return false;
    }

    /** All frames stacked top to bottom, as Minecraft animates them. */
    public static BufferedImage strip() {
        return strip(false);
    }

    public static BufferedImage strip(boolean ascended) {
        BufferedImage img = new BufferedImage(16, 16 * FRAMES, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < FRAMES; f++) {
            BufferedImage one = frame(f, ascended);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) img.setRGB(x, f * 16 + y, one.getRGB(x, y));
            }
        }
        return img;
    }

    /** The pack entries for the blade (add them to the vessel pack). */
    public static Map<String, byte[]> files() {
        Map<String, byte[]> out = new LinkedHashMap<>();
        out.put(ITEM_FILE, utf8("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"gptgodmc:item/godslayer\"}}"));
        out.put("assets/gptgodmc/models/item/godslayer.json",
                utf8("{\"parent\":\"minecraft:item/handheld\",\"textures\":{\"layer0\":\"gptgodmc:item/godslayer\"}}"));
        out.put("assets/gptgodmc/textures/item/godslayer.png", png(strip()));
        out.put("assets/gptgodmc/textures/item/godslayer.png.mcmeta",
                utf8("{\"animation\":{\"frametime\":4,\"interpolate\":true}}"));
        out.put(ITEM_FILE_ASCENDED,
                utf8("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"gptgodmc:item/godslayer_ascended\"}}"));
        out.put("assets/gptgodmc/models/item/godslayer_ascended.json", utf8("{\"parent\":\"minecraft:item/handheld\","
                + "\"textures\":{\"layer0\":\"gptgodmc:item/godslayer_ascended\"}}"));
        out.put("assets/gptgodmc/textures/item/godslayer_ascended.png", png(strip(true)));
        out.put("assets/gptgodmc/textures/item/godslayer_ascended.png.mcmeta",
                utf8("{\"animation\":{\"frametime\":3,\"interpolate\":true}}"));
        return out;
    }

    private static byte[] png(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
