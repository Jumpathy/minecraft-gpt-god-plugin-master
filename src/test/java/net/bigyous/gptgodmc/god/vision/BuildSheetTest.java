package net.bigyous.gptgodmc.god.vision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.vision.BuildSheet.Bounds;
import net.bigyous.gptgodmc.god.vision.BuildSheet.Side;
import net.bigyous.gptgodmc.god.vision.BuildSheet.Tile;

/** Seeing one build from all sides: which blocks are it, where the cameras stand, how the sheet is laid out. */
class BuildSheetTest {
    @BeforeAll
    static void setup() {
        RendererTest.setup();
    }

    /** A block the world holds and the build owns. */
    private static void put(RendererTest.MapVoxels v, Set<Long> build, int x, int y, int z, Material m) {
        v.put(x, y, z, m);
        if (build != null) build.add(BuildSheet.key(x, y, z));
    }

    /** A cottage: cobble walls with a door and windows, plank floor, a pitched spruce roof, a chimney. */
    static Set<Long> house(RendererTest.MapVoxels v, int ox, int oz) {
        Set<Long> b = new HashSet<>();
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 6; z++) {
                put(v, b, ox + x, 63, oz + z, Material.OAK_PLANKS);
                boolean wall = x == 0 || x == 8 || z == 0 || z == 6;
                for (int y = 64; y <= 67 && wall; y++) {
                    boolean corner = (x == 0 || x == 8) && (z == 0 || z == 6);
                    boolean door = x == 4 && z == 6 && y <= 65;
                    boolean window = y == 65 && !corner && (x == 2 || x == 6 || z == 3);
                    if (door) continue;
                    put(v, b, ox + x, y, oz + z, corner ? Material.SPRUCE_LOG : window ? Material.GLASS
                            : Material.COBBLESTONE);
                }
            }
        }
        // The roof slopes up from both long sides to a ridge.
        for (int step = 0; step <= 4; step++) {
            for (int x = -1; x <= 9; x++) {
                put(v, b, ox + x, 68 + step, oz - 1 + step, Material.SPRUCE_PLANKS);
                put(v, b, ox + x, 68 + step, oz + 7 - step, Material.SPRUCE_PLANKS);
            }
            for (int z = step; z <= 6 - step; z++) {
                put(v, b, ox, 68 + step, oz + z, Material.OAK_PLANKS);
                put(v, b, ox + 8, 68 + step, oz + z, Material.OAK_PLANKS);
            }
        }
        for (int y = 68; y <= 73; y++) put(v, b, ox + 7, y, oz + 1, Material.STONE_BRICKS);
        put(v, b, ox + 5, 64, oz + 7, Material.LANTERN);
        return b;
    }

    /** A round stone tower with a gold cap, battlements and a banner of red wool. */
    static Set<Long> tower(RendererTest.MapVoxels v, int ox, int oz) {
        Set<Long> b = new HashSet<>();
        for (int y = 64; y <= 84; y++) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    double d = Math.hypot(x, z);
                    boolean shell = d > 2.4 && d < 3.7;
                    boolean floor = d <= 2.4 && (y == 64 || y == 84 || y == 74);
                    boolean slit = (y % 5 == 2) && (x == 0 || z == 0) && y < 84;
                    if (floor) put(v, b, ox + x, y, oz + z, Material.OAK_PLANKS);
                    else if (shell && !slit) put(v, b, ox + x, y, oz + z, y % 7 == 0 ? Material.DEEPSLATE_BRICKS
                            : Material.STONE_BRICKS);
                }
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                double d = Math.hypot(x, z);
                if (d > 2.4 && d < 3.7 && (x + z) % 2 == 0) put(v, b, ox + x, 85, oz + z, Material.STONE_BRICKS);
            }
        }
        for (int y = 85; y <= 89; y++) put(v, b, ox, y, oz, Material.SPRUCE_LOG);
        for (int k = 1; k <= 3; k++) {
            put(v, b, ox + k, 89, oz, Material.RED_WOOL);
            put(v, b, ox + k, 88, oz, Material.RED_WOOL);
        }
        put(v, b, ox, 90, oz, Material.GOLD_BLOCK);
        return b;
    }

    // ---- Blocks -----------------------------------------------------------------------------------

    @Test
    void positionsPackAndUnpack() {
        int[][] spots = { { 0, 0, 0 }, { -1, -64, -1 }, { 30_000_000, 319, -30_000_000 }, { -123456, -2047, 654321 } };
        for (int[] s : spots) {
            long k = BuildSheet.key(s[0], s[1], s[2]);
            assertEquals(s[0], BuildSheet.x(k));
            assertEquals(s[1], BuildSheet.y(k));
            assertEquals(s[2], BuildSheet.z(k));
        }
        assertNotEquals(BuildSheet.key(1, 2, 3), BuildSheet.key(3, 2, 1));
    }

    @Test
    void aFewPlacedBlocksGrowIntoTheWholeBuildAndNoFurther() {
        RendererTest.MapVoxels v = new RendererTest.MapVoxels(63, 80);
        Set<Long> house = house(v, 0, 0);
        // A neighbour's shed 20 blocks away, a tree beside the house, and the ground under it all.
        Set<Long> shed = new HashSet<>();
        for (int x = 28; x <= 31; x++) {
            for (int y = 64; y <= 66; y++) put(v, shed, x, y, 2, Material.OAK_PLANKS);
        }
        v.put(-2, 64, 3, Material.OAK_LEAVES);
        v.put(-2, 65, 3, Material.OAK_LEAVES);
        Set<Long> seeds = Set.of(BuildSheet.key(0, 64, 0), BuildSheet.key(1, 64, 0), BuildSheet.key(4, 67, 6));
        Set<Long> grown = BuildSheet.grow(seeds, v, RendererTest.palette, 6, 60_000);
        // The floor is at ground height but is planks, so it belongs; the ridge is 12 blocks from the seeds' box.
        Bounds seedBox = Bounds.of(seeds);
        int inReach = 0;
        for (long k : house) {
            if (seedBox.holds(BuildSheet.x(k), BuildSheet.y(k), BuildSheet.z(k), 6)) {
                inReach++;
                assertTrue(grown.contains(k), "missing a house block at " + BuildSheet.x(k) + " " + BuildSheet.y(k)
                        + " " + BuildSheet.z(k));
            }
        }
        assertTrue(inReach > 150, "most of the house is in reach: " + inReach);
        for (long k : grown) {
            assertTrue(house.contains(k), "took a block that is not the house: " + BuildSheet.x(k) + " "
                    + BuildSheet.y(k) + " " + BuildSheet.z(k));
        }
        for (long k : shed) assertFalse(grown.contains(k), "the neighbour's shed is not part of it");
        assertFalse(grown.contains(BuildSheet.key(-2, 64, 3)), "leaves are not built");
        assertFalse(grown.contains(BuildSheet.key(3, 62, 3)), "the dirt under the floor is terrain");

        // A cap on size holds, and seeds that were broken since are dropped.
        assertTrue(BuildSheet.grow(seeds, v, RendererTest.palette, 6, 40).size() <= 40);
        assertTrue(BuildSheet.grow(Set.of(BuildSheet.key(50, 70, 50)), v, RendererTest.palette, 6, 100).isEmpty());
        // A hut of dirt: the placed dirt itself counts, the ground beside it does not.
        v.put(-20, 64, -20, Material.DIRT);
        v.put(-20, 65, -20, Material.DIRT);
        assertEquals(1, BuildSheet.grow(Set.of(BuildSheet.key(-20, 65, -20)), v, RendererTest.palette, 6, 100).size());
    }

    @Test
    void whatNoLongerStandsIsLeftOutAndMaterialsAreCounted() {
        RendererTest.MapVoxels v = new RendererTest.MapVoxels(63, 80);
        Set<Long> tower = tower(v, 0, 0);
        int before = tower.size();
        v.put(0, 90, 0, Material.AIR);
        Set<Long> standing = BuildSheet.standing(tower, v, RendererTest.palette);
        assertEquals(before - 1, standing.size());
        Bounds b = Bounds.of(standing);
        assertEquals(7, b.width());
        assertEquals(7, b.length());
        assertEquals(26, b.height(), "from y 64 to the top of the mast at 89");
        Map<String, Integer> m = BuildSheet.materials(standing, v);
        assertEquals("stone_bricks", m.keySet().iterator().next(), "the most used first");
        assertEquals(6, m.get("red_wool"));
        assertFalse(m.containsKey("gold_block"));
        String line = BuildSheet.materialsLine(m, 2);
        assertTrue(line.startsWith(m.get("stone_bricks") + " stone_bricks, "), line);
        assertTrue(line.endsWith("+" + (m.size() - 2) + " other kinds"), line);
    }

    // ---- Cameras ----------------------------------------------------------------------------------

    @Test
    void everyCameraHoldsTheWholeBuildInFrame() {
        for (Bounds b : List.of(new Bounds(0, 64, 0, 8, 73, 6), new Bounds(-3, 64, -3, 3, 90, 3),
                new Bounds(100, -20, -300, 160, -15, -290), new Bounds(5, 70, 5, 5, 70, 5))) {
            for (Side side : Side.values()) {
                Renderer.Camera cam = BuildSheet.camera(b, side);
                assertTrue(cam.y() > b.maxY() - 1 || b.height() > 20, "it looks down on the build");
                assertEquals(side.dx > 0, cam.x() > b.cx(), side + " east or west of it");
                assertEquals(side.dz > 0, cam.z() > b.cz(), side + " south or north of it");
                double[] mid = BuildSheet.project(cam, b.cx(), b.cy(), b.cz());
                assertEquals(0, mid[0], 1e-6, "the middle of the build is the middle of the picture");
                assertEquals(0, mid[1], 1e-6);
                double widest = 0;
                for (int i = 0; i < 8; i++) {
                    double[] p = BuildSheet.project(cam, (i & 1) == 0 ? b.minX() : b.maxX() + 1,
                            (i & 2) == 0 ? b.minY() : b.maxY() + 1, (i & 4) == 0 ? b.minZ() : b.maxZ() + 1);
                    assertTrue(p[2] > 0, "in front of the camera");
                    assertTrue(Math.abs(p[0]) <= 1 && Math.abs(p[1]) <= 1, side + " cuts off a corner of " + b);
                    widest = Math.max(widest, Math.max(Math.abs(p[0]), Math.abs(p[1])));
                }
                assertTrue(widest > 0.45, "and it is not a speck: " + widest);
            }
        }
    }

    @Test
    void theTopViewFitsTheFootprintWithARimOfGround() {
        Bounds b = new Bounds(0, 64, 0, 19, 70, 9);
        double scale = BuildSheet.topScale(b, 256);
        assertEquals(256.0 / 26, scale, 1e-9);
        assertTrue(b.width() * scale < 256 && b.length() * scale < 256);
    }

    // ---- The sheet --------------------------------------------------------------------------------

    @Test
    void theSheetIsSixLabelledTilesThatDoNotOverlap() {
        List<Tile> tiles = BuildSheet.tiles(256);
        assertEquals(6, tiles.size());
        assertEquals(List.of("FROM SOUTH-EAST", "FROM SOUTH-WEST", "FROM ABOVE", "FROM NORTH-WEST", "FROM NORTH-EAST",
                "ABOUT"), tiles.stream().map(Tile::label).toList());
        for (Tile a : tiles) {
            assertTrue(a.x() >= 0 && a.y() >= 0 && a.x() + a.size() <= 768 && a.y() + a.size() <= 512);
            for (Tile c : tiles) {
                if (a != c) assertFalse(a.x() < c.x() + c.size() && c.x() < a.x() + a.size() && a.y() < c.y() + c.size()
                        && c.y() < a.y() + a.size(), a.label() + " overlaps " + c.label());
            }
        }
        assertTrue(Minimap.tokens(768, 512) < 530, "one sheet stays modest");
    }

    @Test
    void previewsOfAHouseAndATower() throws IOException {
        RendererTest.MapVoxels v = new RendererTest.MapVoxels(63, 80);
        Set<Long> house = house(v, 0, 0);
        Set<Long> tower = tower(v, 16, 3);
        // A hill and a path beside them: ground that is not part of either build.
        for (int x = -6; x <= -2; x++) {
            for (int z = -3; z <= 4; z++) v.put(x, 64, z, Material.GRASS_BLOCK);
        }
        for (int z = 7; z <= 12; z++) v.put(4, 63, z, Material.GRAVEL);

        BufferedImage h = BuildSheet.render(v, RendererTest.palette, house,
                new BuildSheet.About("Cobblestone_Structure_3", "Ashwyn", "AT 4 63 3"), 256);
        RendererTest.save(h, "build-house");
        assertEquals(768, h.getWidth());
        assertEquals(512, h.getHeight());
        BufferedImage t = BuildSheet.render(v, RendererTest.palette, tower,
                new BuildSheet.About("What Juno is building now", "Juno", "AT 16 64 3"), 256);
        RendererTest.save(t, "build-tower");

        // The build is drawn, in every tile: the middle of each view is not the empty backdrop.
        int back = h.getRGB(700, 500) & 0xFFFFFF;
        for (Tile tile : BuildSheet.tiles(256).subList(0, 5)) {
            int mid = h.getRGB(tile.x() + 128, tile.y() + 140) & 0xFFFFFF;
            assertNotEquals(back, mid, tile.label() + " shows the house");
        }
        // The tower beside the house is not part of the house: only a ghost of it, never solid.
        Set<Long> both = new HashSet<>(house);
        both.addAll(tower);
        BufferedImage together = BuildSheet.render(v, RendererTest.palette, both, new BuildSheet.About("Both", null,
                null), 256);
        assertNotEquals(pixels(h), pixels(together));
        System.out.printf("[build sheet] %dx%d ~%d tokens%n", h.getWidth(), h.getHeight(),
                Minimap.tokens(h.getWidth(), h.getHeight()));
    }

    private static long pixels(BufferedImage img) {
        long sum = 0;
        for (int y = 0; y < img.getHeight(); y += 3) {
            for (int x = 0; x < img.getWidth(); x += 3) sum = sum * 31 + img.getRGB(x, y);
        }
        return sum;
    }
}
