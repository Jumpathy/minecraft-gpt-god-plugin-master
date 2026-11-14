package net.bigyous.gptgodmc.god.build;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * A statue of a player, built from their real skin: every block's colour is read from the skin's own
 * pixels (second layer over the first), so nothing is guessed. The figure is the game's player model,
 * 16 wide (arms out at its sides), 32 high and 8 deep at scale 1, hollow, standing on y = 0 and facing
 * -z (north). Pure: {@link #voxels} gives positions and colours, {@link #block} the nearest block.
 */
public final class SkinStatue {
    private SkinStatue() {
    }

    /** One block of the statue: where (x east, y up, z south, from its front-left foot corner) and its colour. */
    public record Voxel(int x, int y, int z, int rgb) {
    }

    /** A part of the body: its place in the model, its size, and where its two texture layers start. */
    private record Part(int x, int y, int z, int w, int h, int d, int u, int v, int ou, int ov) {
    }

    /** The statue's blocks for a 64x64 (or old 64x32) skin; scale 1..3 makes each skin pixel that many blocks. */
    public static List<Voxel> voxels(BufferedImage skin, int scale) {
        if (skin == null || skin.getWidth() != 64 || (skin.getHeight() != 64 && skin.getHeight() != 32)) {
            throw new IllegalArgumentException("not a player skin (64x64 or 64x32)");
        }
        int s = Math.max(1, Math.min(3, scale));
        boolean legacy = skin.getHeight() == 32;
        // Slim ("Alex") arms are 3 wide: their fourth column in the skin is empty.
        boolean slim = !legacy && (skin.getRGB(54, 20) >>> 24) < 128 && (skin.getRGB(46, 52) >>> 24) < 128;
        int aw = slim ? 3 : 4;
        List<Part> parts = new ArrayList<>();
        parts.add(new Part(4, 24, 0, 8, 8, 8, 0, 0, 32, 0)); // head
        parts.add(new Part(4, 12, 2, 8, 12, 4, 16, 16, 16, 32)); // body
        parts.add(new Part(4 - aw, 12, 2, aw, 12, 4, 40, 16, 40, 32)); // their right arm (on the viewer's left)
        parts.add(new Part(4, 0, 2, 4, 12, 4, 0, 16, 0, 32)); // right leg
        if (legacy) {
            parts.add(new Part(12, 12, 2, aw, 12, 4, 40, 16, -1, -1)); // old skins use the right limbs again
            parts.add(new Part(8, 0, 2, 4, 12, 4, 0, 16, -1, -1));
        } else {
            parts.add(new Part(12, 12, 2, aw, 12, 4, 32, 48, 48, 48));
            parts.add(new Part(8, 0, 2, 4, 12, 4, 16, 48, 0, 48));
        }
        List<Voxel> out = new ArrayList<>();
        for (Part p : parts) {
            int w = p.w() * s, h = p.h() * s, d = p.d() * s;
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    for (int k = 0; k < d; k++) {
                        boolean surface = i == 0 || i == w - 1 || j == 0 || j == h - 1 || k == 0 || k == d - 1;
                        if (!surface) continue;
                        int rgb = sample(skin, p, legacy, i / s, j / s, k / s);
                        if (rgb < 0) continue;
                        out.add(new Voxel(p.x() * s + i, p.y() * s + j, p.z() * s + k, rgb));
                    }
                }
            }
        }
        return out;
    }

    /** The colour of a part's surface texel (i across, j up, k back), or -1 where the skin is empty. */
    private static int sample(BufferedImage skin, Part p, boolean legacy, int i, int j, int k) {
        int w = p.w(), h = p.h(), d = p.d();
        int fu, fv; // offset within the part's unwrapped texture
        if (k == 0) { // front
            fu = d + i;
            fv = d + (h - 1 - j);
        } else if (k == d - 1) { // back
            fu = d + w + d + (w - 1 - i);
            fv = d + (h - 1 - j);
        } else if (j == h - 1) { // top
            fu = d + i;
            fv = d - 1 - k;
        } else if (j == 0) { // bottom
            fu = d + w + i;
            fv = d - 1 - k;
        } else if (i == 0) { // their right side
            fu = d - 1 - k;
            fv = d + (h - 1 - j);
        } else { // their left side
            fu = d + w + k;
            fv = d + (h - 1 - j);
        }
        boolean head = p.v() == 0;
        if (p.ou() >= 0 && (!legacy || head)) {
            int o = pixel(skin, p.ou() + fu, p.ov() + fv);
            if ((o >>> 24) >= 128) return o & 0xFFFFFF;
        }
        int b = pixel(skin, p.u() + fu, p.v() + fv);
        return (b >>> 24) >= 128 ? b & 0xFFFFFF : -1;
    }

    private static int pixel(BufferedImage skin, int x, int y) {
        return x < 0 || y < 0 || x >= skin.getWidth() || y >= skin.getHeight() ? 0 : skin.getRGB(x, y);
    }

    // ---- Blocks ---------------------------------------------------------------------------------------

    /** Solid, full blocks with flat colours (concrete, terracotta, wool and a few naturals), as name and colour. */
    static final Object[][] BLOCKS = {
            { "white_concrete", 0xCFD5D6 }, { "light_gray_concrete", 0x7D7D73 }, { "gray_concrete", 0x36393D },
            { "black_concrete", 0x080A0F }, { "brown_concrete", 0x603B1F }, { "red_concrete", 0x8E2020 },
            { "orange_concrete", 0xE06100 }, { "yellow_concrete", 0xF0AF15 }, { "lime_concrete", 0x5EA818 },
            { "green_concrete", 0x495B24 }, { "cyan_concrete", 0x157788 }, { "light_blue_concrete", 0x2389C6 },
            { "blue_concrete", 0x2C2E8F }, { "purple_concrete", 0x641F9C }, { "magenta_concrete", 0xA9309F },
            { "pink_concrete", 0xD5658E },
            { "white_terracotta", 0xD1B2A1 }, { "light_gray_terracotta", 0x876A61 }, { "gray_terracotta", 0x392A23 },
            { "black_terracotta", 0x251610 }, { "brown_terracotta", 0x4D3323 }, { "red_terracotta", 0x8F3D2E },
            { "orange_terracotta", 0xA15325 }, { "yellow_terracotta", 0xBA8523 }, { "lime_terracotta", 0x677534 },
            { "green_terracotta", 0x4C532A }, { "cyan_terracotta", 0x565B5B }, { "light_blue_terracotta", 0x716C89 },
            { "blue_terracotta", 0x4A3B5B }, { "purple_terracotta", 0x764656 }, { "magenta_terracotta", 0x95586C },
            { "pink_terracotta", 0xA14E4E }, { "terracotta", 0x985E43 },
            { "white_wool", 0xE9ECEC }, { "light_gray_wool", 0x8E8E86 }, { "gray_wool", 0x3E4447 },
            { "black_wool", 0x141519 }, { "brown_wool", 0x724728 }, { "red_wool", 0xA02722 },
            { "orange_wool", 0xF07613 }, { "yellow_wool", 0xF8C527 }, { "lime_wool", 0x70B919 },
            { "green_wool", 0x546D1B }, { "cyan_wool", 0x158991 }, { "light_blue_wool", 0x3AAFD9 },
            { "blue_wool", 0x35399D }, { "purple_wool", 0x792AAC }, { "magenta_wool", 0xBD44B3 },
            { "pink_wool", 0xED8DAC },
            { "smooth_sandstone", 0xDFD6AA }, { "stripped_birch_wood", 0xC4B077 }, { "stripped_oak_wood", 0xB29157 },
            { "stripped_jungle_wood", 0xAB8454 }, { "stripped_spruce_wood", 0x735A34 },
            { "stripped_dark_oak_wood", 0x483A23 }, { "stripped_acacia_wood", 0xAE5C3B },
            { "smooth_quartz", 0xECE6DF }, { "snow_block", 0xF9FEFE }, { "mud_bricks", 0x89674F },
            { "packed_mud", 0x8E6A4F }, { "smooth_stone", 0x9E9E9E }, { "polished_deepslate", 0x484849 },
            { "polished_blackstone", 0x35313B }, { "cut_copper", 0xBF6A50 }, { "raw_gold_block", 0xDDA92E },
            { "gold_block", 0xF6D03D }, { "iron_block", 0xDCDCDC }, { "lapis_block", 0x1F438C },
            { "redstone_block", 0xAF1805 }, { "emerald_block", 0x2ACB57 }, { "diamond_block", 0x62EDE4 },
            { "amethyst_block", 0x8561BF }, { "prismarine_bricks", 0x63AB9E }, { "dark_prismarine", 0x335B4B },
            { "nether_wart_block", 0x720202 }, { "warped_wart_block", 0x167777 }, { "honeycomb_block", 0xE5941D } };

    /** The block whose colour is nearest (a perceptual distance: reds and greens weigh by brightness). */
    public static String block(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        String best = "white_concrete";
        double bestD = Double.MAX_VALUE;
        for (Object[] e : BLOCKS) {
            int c = (Integer) e[1];
            int cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
            double mean = (r + cr) / 2.0;
            int dr = r - cr, dg = g - cg, db = b - cb;
            double dist = (2 + mean / 256) * dr * dr + 4 * dg * dg + (2 + (255 - mean) / 256) * db * db;
            if (dist < bestD) {
                bestD = dist;
                best = (String) e[0];
            }
        }
        return best;
    }
}
