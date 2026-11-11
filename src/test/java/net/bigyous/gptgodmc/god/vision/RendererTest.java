package net.bigyous.gptgodmc.god.vision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.build.Designs;
import net.bigyous.gptgodmc.god.build.Plan;
import net.bigyous.gptgodmc.god.build.Style;
import net.bigyous.gptgodmc.testutil.FakeServer;

/** Renders synthetic scenes (and every building design) to build/test-renders for inspection. */
class RendererTest {
    static final File OUT = new File("build/test-renders");
    static Palette palette;

    @BeforeAll
    static void setup() {
        FakeServer.install();
        OUT.mkdirs();
        palette = Palette.blank(Material.values().length);
        for (Material m : Material.values()) {
            int i = m.ordinal();
            String n = m.name();
            // Material.isBlock() needs a live server registry now; names are enough for these scenes.
            if (n.startsWith("LEGACY_") || n.endsWith("AIR")) continue;
            palette.kind[i] = Palette.SOLID;
            palette.rgb[i] = guessColor(n);
            if (n.contains("GLASS")) palette.kind[i] = Palette.GLASS;
            if (n.equals("WATER")) palette.kind[i] = Palette.WATER;
            if (n.contains("LANTERN") || n.contains("FROGLIGHT") || n.equals("SEA_LANTERN") || n.equals("END_ROD")
                    || n.equals("CAMPFIRE")) palette.kind[i] = Palette.GLOW;
            palette.xrayHidden[i] = n.equals("STONE") || n.equals("DIRT") || n.equals("GRASS_BLOCK");
            if (n.equals("DIAMOND_ORE")) palette.precious[i] = true;
        }
    }

    static int guessColor(String n) {
        if (n.contains("PURPUR")) return 0xA97DA9;
        if (n.contains("AMETHYST")) return 0x8A5CC6;
        if (n.contains("CRYING_OBSIDIAN")) return 0x2A0B4A;
        if (n.contains("QUARTZ")) return 0xEAE4DC;
        if (n.contains("GOLD")) return 0xF9D849;
        if (n.contains("BLACKSTONE") || n.contains("BASALT")) return 0x2E272C;
        if (n.contains("RED_NETHER")) return 0x5A1010;
        if (n.contains("DEEPSLATE")) return 0x4A4A50;
        if (n.contains("STONE_BRICK") || n.contains("ANDESITE")) return 0x7A7A7A;
        if (n.contains("COBBLE")) return 0x6F6F6F;
        if (n.contains("SPRUCE")) return 0x6E5332;
        if (n.contains("OAK") || n.contains("PLANKS")) return 0xA2834F;
        if (n.contains("GRASS_BLOCK")) return 0x5C9E3A;
        if (n.contains("DIRT") || n.contains("PATH")) return 0x86603A;
        if (n.contains("SAND")) return 0xDBD3A0;
        if (n.contains("GRAVEL")) return 0x8A8480;
        if (n.contains("PURPLE")) return 0x8E44CF;
        if (n.contains("RED")) return 0xB02E26;
        if (n.contains("WHITE")) return 0xF0F0F0;
        if (n.contains("LANTERN") || n.contains("FROGLIGHT")) return 0xFFD27A;
        if (n.contains("IRON")) return 0xC8C8C8;
        if (n.contains("DIAMOND")) return 0x5DECF5;
        if (n.equals("STONE")) return 0x707070;
        return 0x9A9A9A;
    }

    /** Voxels backed by a map: a grass floor at y=63 plus whatever is placed. */
    static final class MapVoxels implements Scene.Voxels {
        final Map<Long, Integer> blocks = new HashMap<>();
        final int floorY;
        final int radius;

        MapVoxels(int floorY, int radius) {
            this.floorY = floorY;
            this.radius = radius;
        }

        static long key(int x, int y, int z) {
            return ((long) (x + 1_000) << 40) | ((long) (y + 1_000) << 20) | (z + 1_000);
        }

        void put(int x, int y, int z, Material m) {
            blocks.put(key(x, y, z), m.ordinal());
        }

        @Override
        public int block(int x, int y, int z) {
            if (Math.abs(x) > radius || Math.abs(z) > radius) return UNLOADED;
            Integer b = blocks.get(key(x, y, z));
            if (b != null) return b;
            if (y < floorY - 3) return Material.STONE.ordinal();
            if (y < floorY) return Material.DIRT.ordinal();
            if (y == floorY) return Material.GRASS_BLOCK.ordinal();
            return Material.AIR.ordinal();
        }

        @Override
        public int light(int x, int y, int z, double daylight) {
            return (int) Math.round(15 * daylight);
        }

        @Override
        public int highestY(int x, int z) {
            int top = floorY;
            for (int y = floorY + 40; y > floorY; y--) {
                Integer b = blocks.get(key(x, y, z));
                if (b != null && b != Material.AIR.ordinal()) {
                    top = y;
                    break;
                }
            }
            return top;
        }

        @Override
        public int minY() {
            return 0;
        }

        @Override
        public int maxY() {
            return 255;
        }
    }

    static void save(BufferedImage img, String name) throws IOException {
        OUT.mkdirs();
        ImageIO.write(img, "png", new File(OUT, name + ".png"));
    }

    @Test
    void perspectiveShowsSkyAboveAndGroundBelow() throws IOException {
        MapVoxels v = new MapVoxels(63, 80);
        for (int y = 64; y < 70; y++) v.put(5, y, 10, Material.STONE_BRICKS);
        v.put(0, 60, 6, Material.DIAMOND_ORE);
        // A winding cave with a hidden chest, to see x-ray cave shapes.
        for (int z = 4; z <= 40; z++) {
            int cx = (int) Math.round(Math.sin(z / 5.0) * 4);
            for (int x = cx - 1; x <= cx + 1; x++)
                for (int y = 54; y <= 56; y++) v.put(x, y, z, Material.AIR);
        }
        v.put(2, 54, 20, Material.CHEST);
        List<Scene.Box> boxes = List.of(new Scene.Box(-2, 64, 8, 0.3, 1.8, 0xFFD23F, "Steve", false, true),
                new Scene.Box(3, 64, 6, 0.45, 3.8, 0xB24BF3, "VAEL (YOU)", true, false));
        Scene s = new Scene(v, palette, boxes, List.of(), Scene.Dimension.OVERWORLD, 1.0, 0, false);
        BufferedImage img = Renderer.perspective(s, new Renderer.Camera(0, 65.6, 0, 0, 10, 70), 640, 360, 96, false);
        save(img, "pov");
        int sky = img.getRGB(320, 5) & 0xFFFFFF;
        int ground = img.getRGB(320, 355) & 0xFFFFFF;
        assertTrue(Renderer.blue(sky) > Renderer.red(sky), "sky should be blue-ish: " + Integer.toHexString(sky));
        assertTrue(Renderer.green(ground) > Renderer.blue(ground), "ground should be grass: " + Integer.toHexString(ground));

        BufferedImage xray = Renderer.perspective(s, new Renderer.Camera(0, 65.6, 0, 0, 35, 70), 640, 360, 64, true);
        save(xray, "pov-xray");
        assertNotEquals(img.getRGB(320, 300), xray.getRGB(320, 300));
    }

    @Test
    void panoramaAndOverheadRender() throws IOException {
        MapVoxels v = new MapVoxels(63, 80);
        for (int x = -3; x <= 3; x++)
            for (int y = 64; y <= 67; y++) v.put(x, y, -12, Material.OAK_PLANKS);
        List<Scene.Box> boxes = List.of(new Scene.Box(4, 64, 4, 0.3, 1.8, 0xFFD23F, "Alex", false, true));
        Scene s = new Scene(v, palette, boxes, List.of(new Scene.Marker(0, -12, "House")), Scene.Dimension.OVERWORLD,
                0.15, 0, false);
        BufferedImage pano = Renderer.panorama(s, 0, 65.6, 0, 768, 320, 80, false);
        save(pano, "panorama-night");
        assertEquals(768, pano.getWidth());
        BufferedImage map = Renderer.overhead(s, 0, 64, 0, 32, 512, Renderer.OverheadMode.SURFACE);
        save(map, "overhead");
        assertEquals(512, map.getHeight());
    }

    @Test
    void lookAtMatchesMinecraftYaw() {
        double[] south = Renderer.lookAt(0, 0, 0, 0, 0, 10);
        double[] west = Renderer.lookAt(0, 0, 0, -10, 0, 0);
        assertEquals(0, south[0], 1e-6);
        assertEquals(90, west[0], 1e-6);
        double[] f = Renderer.forward(90, 0);
        assertEquals(-1, f[0], 1e-6);
    }

    /** Every design, in every style it is likely to be seen in, rendered from a three-quarter view. */
    @Test
    void designsRender() throws IOException {
        for (Designs.Design d : Designs.ALL.values()) {
            for (String styleId : List.of("violet", "rustic", "dark")) {
                if (!styleId.equals("violet") && !(d.id().equals("cottage") || d.id().equals("temple")
                        || d.id().equals("prison"))) continue;
                Plan plan = d.generator().apply(Style.STYLES.get(styleId));
                assertTrue(plan.size() > 10, d.id() + " is empty");
                MapVoxels v = new MapVoxels(63, 120);
                for (Map.Entry<Long, BlockData> e : plan.blocks().entrySet()) {
                    v.put(PlanAccess.x(e.getKey()), 64 + PlanAccess.y(e.getKey()), PlanAccess.z(e.getKey()),
                            e.getValue().getMaterial());
                }
                int size = Math.max(plan.width(), Math.max(plan.depth(), plan.height()));
                double dist = size * 1.25 + 6;
                double camX = dist * 0.7, camZ = dist * 0.85, camY = 64 + size * 0.7;
                double[] yp = Renderer.lookAt(camX, camY, camZ, 0, 64 + plan.height() / 3.0, 0);
                Scene s = new Scene(v, palette, List.of(), List.of(), Scene.Dimension.OVERWORLD, 1.0, 0, false);
                BufferedImage img = Renderer.perspective(s, new Renderer.Camera(camX, camY, camZ, yp[0], yp[1], 60),
                        640, 360, 160, false);
                save(img, "design-" + d.id() + "-" + styleId);
            }
        }
    }
}
