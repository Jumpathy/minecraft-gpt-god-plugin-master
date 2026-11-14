package net.bigyous.gptgodmc.god.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.SkinPainter;
import net.bigyous.gptgodmc.god.art.ForgeBar;

/** A statue copied from a skin is that skin: its front shows the face, the shirt and the trousers where they are. */
class SkinStatueTest {
    static final int SKIN = 0xE0AC8C, HAIR = 0x4A2E18, SHIRT = 0x00A8A8, LEGS = 0x3030A0, SHOES = 0x6E6E6E;

    static BufferedImage skin() {
        return SkinPainter.person(SKIN).hair(HAIR).shirt(SHIRT).trousers(LEGS).shoes(SHOES).img;
    }

    /** The front of the statue (the blocks nearest z = 0), by x and y. */
    static Map<Long, Integer> front(List<SkinStatue.Voxel> voxels) {
        Map<Long, int[]> best = new HashMap<>();
        for (SkinStatue.Voxel v : voxels) {
            long k = v.x() * 1000L + v.y();
            int[] b = best.get(k);
            if (b == null || v.z() < b[0]) best.put(k, new int[] { v.z(), v.rgb() });
        }
        Map<Long, Integer> out = new HashMap<>();
        best.forEach((k, v) -> out.put(k, v[1]));
        return out;
    }

    @Test
    void theStatueIsThePlayerModelInTheSkinsColours() {
        List<SkinStatue.Voxel> v = SkinStatue.voxels(skin(), 1);
        int maxX = 0, maxY = 0, maxZ = 0;
        for (SkinStatue.Voxel b : v) {
            maxX = Math.max(maxX, b.x());
            maxY = Math.max(maxY, b.y());
            maxZ = Math.max(maxZ, b.z());
        }
        assertEquals(15, maxX);
        assertEquals(31, maxY);
        assertEquals(7, maxZ);
        assertTrue(v.size() > 900 && v.size() < 1400, "hollow: " + v.size());
        Map<Long, Integer> f = front(v);
        assertEquals("cyan", name(f.get(8 * 1000L + 18)), "the shirt on the chest");
        assertEquals("blue", name(f.get(6 * 1000L + 6)), "trousers on the right leg");
        assertEquals("blue", name(f.get(10 * 1000L + 6)), "and on the left");
        assertEquals("skin", name(f.get(8 * 1000L + 27)), "the face");
        assertEquals("hair", name(f.get(8 * 1000L + 31)), "hair at the top of the head");
        assertEquals("skin", name(f.get(1 * 1000L + 14)), "a bare forearm");
        assertEquals(8 * v.size() / 2, SkinStatue.voxels(skin(), 2).size() * 1.0, v.size() * 1.2, "scale 2 is about four times the shell");
        assertThrows(IllegalArgumentException.class, () -> SkinStatue.voxels(new BufferedImage(32, 32, 2), 1));
    }

    static String name(Integer rgb) {
        if (rgb == null) return "nothing";
        if (near(rgb, SHIRT)) return "cyan";
        if (near(rgb, LEGS)) return "blue";
        if (near(rgb, HAIR)) return "hair";
        if (near(rgb, SKIN)) return "skin";
        return Integer.toHexString(rgb);
    }

    static boolean near(int a, int b) {
        return Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) < 24 && Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)) < 24
                && Math.abs((a & 255) - (b & 255)) < 24;
    }

    @Test
    void coloursBecomeTheNearestBlock() {
        assertEquals("black_concrete", SkinStatue.block(0x050505));
        assertTrue(SkinStatue.block(0xF5F5F5).matches("snow_block|white_wool|smooth_quartz"), SkinStatue.block(0xF5F5F5));
        assertTrue(SkinStatue.block(0xC02020).startsWith("red"), SkinStatue.block(0xC02020));
        assertTrue(SkinStatue.block(0x2030A0).matches("blue_.*|lapis_block"), SkinStatue.block(0x2030A0));
        for (Object[] b : SkinStatue.BLOCKS) assertEquals(b[0], SkinStatue.block((Integer) b[1]), "each block is its own nearest");
    }

    @Test
    void theForgeBarSaysWhereTheWorkIs() {
        assertEquals("✦ Thyvoru forges Tidefang: sketching it", ForgeBar.words("Thyvoru", "Tidefang", 0, 4));
        assertEquals("✦ Thyvoru forges Tidefang: painting it, try 3 of 4", ForgeBar.words("Thyvoru", "Tidefang", 2, 4));
        assertEquals("✦ Thyvoru forges a relic: finishing it", ForgeBar.words("Thyvoru", "", 4, 4));
        assertTrue(ForgeBar.progress(0, 4) < ForgeBar.progress(2, 4) && ForgeBar.progress(4, 4) == 1f);
    }

    /** build/test-renders/statue-skin.png: the skin's front beside the statue's front and back in block colours. */
    @Test
    void drawsTheStatue() throws IOException {
        List<SkinStatue.Voxel> v = SkinStatue.voxels(skin(), 1);
        int s = 10;
        BufferedImage img = new BufferedImage(2 * 16 * s + 30, 32 * s + 20, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) img.setRGB(x, y, 0x24262B);
        Map<Long, int[]> front = new HashMap<>(), back = new HashMap<>();
        for (SkinStatue.Voxel b : v) {
            long k = b.x() * 1000L + b.y();
            int rgb = colour(SkinStatue.block(b.rgb()));
            int[] f = front.get(k), bk = back.get(k);
            if (f == null || b.z() < f[0]) front.put(k, new int[] { b.z(), rgb });
            if (bk == null || b.z() > bk[0]) back.put(k, new int[] { b.z(), rgb });
        }
        paint(img, front, 10, 10, s, false);
        paint(img, back, 20 + 16 * s, 10, s, true);
        File out = new File("build/test-renders/statue-skin.png");
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
    }

    static int colour(String block) {
        for (Object[] b : SkinStatue.BLOCKS) if (b[0].equals(block)) return (Integer) b[1];
        return 0xFF00FF;
    }

    static void paint(BufferedImage img, Map<Long, int[]> face, int ox, int oy, int s, boolean mirror) {
        face.forEach((k, c) -> {
            int x = (int) (k / 1000), y = (int) (k % 1000);
            int px = ox + (mirror ? 15 - x : x) * s, py = oy + (31 - y) * s;
            for (int dy = 0; dy < s - 1; dy++) for (int dx = 0; dx < s - 1; dx++) img.setRGB(px + dx, py + dy, c[1]);
        });
    }
}
