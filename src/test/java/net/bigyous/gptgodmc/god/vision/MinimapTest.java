package net.bigyous.gptgodmc.god.vision;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.vision.Minimap.Box;
import net.bigyous.gptgodmc.god.vision.Minimap.Foe;
import net.bigyous.gptgodmc.god.vision.Minimap.Frame;
import net.bigyous.gptgodmc.god.vision.Minimap.Level;
import net.bigyous.gptgodmc.god.vision.Minimap.Mortal;
import net.bigyous.gptgodmc.god.vision.Minimap.Panel;
import net.bigyous.gptgodmc.god.vision.Minimap.Place;
import net.bigyous.gptgodmc.god.vision.Minimap.PlaceKind;

/** The minimap: which level it is cut at, who shares a panel, where marks and labels land. */
class MinimapTest {
    @BeforeAll
    static void setup() {
        RendererTest.setup();
    }

    private static Mortal at(String name, double x, double y, double z, Level level) {
        return new Mortal(name, x, y, z, 0, level, 0xFFD23F);
    }

    // ---- Level ------------------------------------------------------------------------------------

    @Test
    void theLevelFollowsWhatIsOverTheirHead() {
        assertEquals(Level.SURFACE, Minimap.level(false, 64, 64));
        assertEquals(Level.SURFACE, Minimap.level(false, 30, 64), "open sky down a ravine is still the surface");
        assertEquals(Level.INDOORS, Minimap.level(true, 64, 68), "a roof over them");
        assertEquals(Level.UNDERGROUND, Minimap.level(true, 30, 64));
        assertFalse(Level.SURFACE.slice());
        assertTrue(Level.INDOORS.slice());
        assertTrue(Level.UNDERGROUND.slice());
    }

    // ---- Panels -----------------------------------------------------------------------------------

    @Test
    void mortalsTogetherShareOnePanelThatFitsThem() {
        Minimap.Grouping g = Minimap.group(List.of(at("A", 0, 64, 0, Level.SURFACE), at("B", 40, 64, 10, Level.SURFACE),
                at("C", -20, 70, 30, Level.SURFACE)), 24, 96, 4);
        assertEquals(1, g.panels().size());
        Panel p = g.panels().get(0);
        assertFalse(p.slice());
        assertEquals(3, p.members().size());
        Frame f = new Frame(p.cx(), p.cz(), p.radius(), 448);
        for (Mortal m : p.members()) {
            assertTrue(f.inside(m.x(), m.z()), m.name());
            assertTrue(f.px(m.x()) > 20 && f.px(m.x()) < 428, m.name() + " has room for a label");
        }
        assertEquals(40, p.radius(), "half the 60-block spread plus a margin");
    }

    @Test
    void differentLevelsAndFarPlacesGetTheirOwnPanels() {
        Mortal top = at("Top", 0, 64, 0, Level.SURFACE);
        Mortal cave = at("Cave", 5, 20, 5, Level.UNDERGROUND);
        Mortal caveMate = at("Mate", 12, 22, 0, Level.UNDERGROUND);
        Mortal deeper = at("Deep", 8, -30, 2, Level.UNDERGROUND);
        Mortal far = at("Far", 900, 64, 900, Level.SURFACE);
        Minimap.Grouping g = Minimap.group(List.of(top, cave, caveMate, deeper, far), 24, 96, 4);
        assertEquals(4, g.panels().size());
        assertEquals(List.of(top), g.panels().get(0).members());
        assertEquals(List.of(cave, caveMate), g.panels().get(1).members());
        assertTrue(g.panels().get(1).slice());
        assertEquals(20, g.panels().get(1).sliceY(), "the first mortal sets the level");
        assertEquals(-30, g.panels().get(2).sliceY());
        assertEquals("UNDERGROUND Y=-30", g.panels().get(2).title());
        assertEquals("SURFACE", g.panels().get(3).title());
        assertTrue(g.leftOut().isEmpty());

        Minimap.Grouping two = Minimap.group(List.of(top, cave, caveMate, deeper, far), 24, 96, 2);
        assertEquals(2, two.panels().size());
        assertEquals(List.of(deeper, far), two.leftOut());
    }

    @Test
    void aMortalOnAnotherLevelIsMarkedSo() {
        Panel surface = new Panel(0, 0, 32, false, 64, Level.SURFACE, List.of());
        Panel slice = new Panel(0, 0, 32, true, 20, Level.UNDERGROUND, List.of());
        Mortal top = at("Top", 0, 64, 0, Level.SURFACE);
        Mortal cave = at("Cave", 5, 22, 5, Level.UNDERGROUND);
        Mortal deeper = at("Deep", 5, 2, 5, Level.UNDERGROUND);
        assertFalse(Minimap.offLevel(surface, top));
        assertTrue(Minimap.offLevel(surface, cave));
        assertEquals("BELOW", Minimap.offWord(surface, cave));
        assertFalse(Minimap.offLevel(slice, cave));
        assertTrue(Minimap.offLevel(slice, top));
        assertEquals("ABOVE", Minimap.offWord(slice, top));
        assertEquals("BELOW", Minimap.offWord(slice, deeper));
        // A roofed room at ground level: those outside at the same height are on its level.
        Panel room = new Panel(0, 0, 32, true, 64, Level.INDOORS, List.of());
        assertFalse(Minimap.offLevel(room, top));
        assertEquals("INSIDE", Minimap.offWord(surface, at("Home", 3, 64, 3, Level.INDOORS)));
        assertTrue(Minimap.offLevel(surface, Level.UNDERGROUND, 20), "a cave creature is not drawn on the surface");
        assertFalse(Minimap.offLevel(slice, Level.UNDERGROUND, 24));
    }

    // ---- Projection -------------------------------------------------------------------------------

    @Test
    void theWorldMapsToPixelsWithNorthUp() {
        Frame f = new Frame(100, -200, 32, 448);
        assertEquals(224, f.px(100));
        assertEquals(224, f.py(-200));
        assertEquals(0, f.px(68));
        assertEquals(0, f.py(-232), "north (lower z) is the top");
        assertTrue(f.py(-180) > f.py(-200), "south is further down");
        assertTrue(f.px(120) > f.px(100), "east is to the right");
        assertTrue(f.inside(131.9, -168.1));
        assertFalse(f.inside(132.1, -200));
        assertFalse(f.inside(100, -233));
        assertEquals(1.0 / 7, f.blocksPerPx(), 1e-9);
    }

    @Test
    void theDartPointsWhereTheyFace() {
        assertArrayEquals(new int[] { 0, 10 }, Minimap.arrow(0, 10), "yaw 0 is south: down the picture");
        assertArrayEquals(new int[] { -10, 0 }, Minimap.arrow(90, 10), "west: left");
        assertArrayEquals(new int[] { 0, -10 }, Minimap.arrow(180, 10), "north: up");
        assertArrayEquals(new int[] { 10, 0 }, Minimap.arrow(-90, 10), "east: right");
    }

    @Test
    void theScaleBarIsARoundLengthThatFits() {
        assertEquals(10, Minimap.scaleBlocks(64.0 / 448, 448));
        assertEquals(20, Minimap.scaleBlocks(192.0 / 448, 448));
        assertEquals(5, Minimap.scaleBlocks(24.0 / 448, 448));
    }

    @Test
    void anImageCostsAFewHundredTokens() {
        assertEquals(268, Minimap.tokens(448, 448));
        assertEquals(358, Minimap.panelSize(448, 2));
        assertEquals(448, Minimap.panelSize(448, 1));
        assertTrue(Minimap.tokens(2 * 358 + 4, 358) < 350);
    }

    // ---- Labels -----------------------------------------------------------------------------------

    @Test
    void labelsStayInsideAndOffEachOtherAndTheMarkers() {
        List<Box> anchors = new ArrayList<>();
        List<int[]> sizes = new ArrayList<>();
        // A huddle in the middle, one in each corner, one against the right edge.
        int[][] spots = { { 200, 200 }, { 212, 204 }, { 205, 216 }, { 190, 208 }, { 6, 6 }, { 440, 440 }, { 444, 200 },
                { 8, 440 } };
        for (int[] s : spots) {
            anchors.add(new Box(s[0] - 10, s[1] - 10, 21, 21));
            sizes.add(new int[] { 88, 28 });
        }
        List<Box> reserved = List.of(new Box(0, 0, 120, 31), new Box(424, 0, 24, 36));
        List<Box> placed = Minimap.place(anchors, sizes, 448, 448, reserved);
        assertEquals(anchors.size(), placed.size());
        for (int i = 0; i < placed.size(); i++) {
            Box b = placed.get(i);
            assertTrue(b.x() >= 0 && b.y() >= 0 && b.x() + b.w() <= 448 && b.y() + b.h() <= 448, "inside: " + b);
            for (int j = 0; j < placed.size(); j++) {
                if (i != j) assertFalse(b.hits(placed.get(j)), "labels " + i + " and " + j + " overlap");
            }
            for (int j = 0; j < anchors.size(); j++) {
                if (i != j) assertFalse(b.hits(anchors.get(j)), "label " + i + " covers marker " + j);
            }
            for (Box r : reserved) assertFalse(b.hits(r), "label " + i + " covers the title or compass");
            // Each label stays near its own marker.
            Box a = anchors.get(i);
            double dx = Math.abs((b.x() + b.w() / 2.0) - (a.x() + 10)), dy = Math.abs((b.y() + b.h() / 2.0) - (a.y() + 10));
            assertTrue(dx < 150 && dy < 125, "label " + i + " strayed: " + b);
        }
    }

    @Test
    void aLabelWithNoFreeSpotIsStillInsideThePicture() {
        Box a = new Box(20, 20, 21, 21);
        List<Box> placed = Minimap.place(List.of(a), List.of(new int[] { 60, 60 }), 64, 64, List.of(new Box(0, 0, 64, 64)));
        Box b = placed.get(0);
        assertTrue(b.x() >= 0 && b.y() >= 0 && b.x() + b.w() <= 64 && b.y() + b.h() <= 64);
    }

    // ---- Unasked ----------------------------------------------------------------------------------

    @Test
    void anUnaskedMapIsSentOnlyWhenItHelps() {
        List<String> danger = List.of("Steve is in danger they may not see: creeper 4 blocks BEHIND them (health 12).");
        List<String> chatter = List.of("Steve broke 12 stone.", "Alex: nice weather");
        long gap = 60_000;
        assertFalse(Minimap.useful("off", danger, "build a tower here", 999_999, gap));
        assertFalse(Minimap.useful(null, danger, "", 999_999, gap));
        assertTrue(Minimap.useful("always", chatter, "", 0, gap));
        assertTrue(Minimap.useful("when-useful", danger, "", 999_999, gap));
        assertFalse(Minimap.useful("when-useful", danger, "", 5_000, gap), "not twice within the gap");
        assertFalse(Minimap.useful("when-useful", chatter, "", 999_999, gap));
        assertTrue(Minimap.useful("when-useful", chatter, "#3 from Tyler: build a shrine next to Steve", 999_999, gap));
        assertTrue(Minimap.useful("when-useful", chatter, "#4: strike 120 64 -30", 999_999, gap));
        assertFalse(Minimap.useful("when-useful", chatter, "#5: speak only in rhyme", 999_999, gap));
        assertFalse(Minimap.useful("when-useful", chatter, "## BINDING COMMANDS\nCarry out each one here and there.\n"
                + "#5 (given 2 min ago, attempt 1 of 3): speak only in rhyme", 999_999, gap), "the heading does not count");
        assertTrue(Minimap.useful("when-useful", chatter, "## BINDING COMMANDS\nNever ignore one.\n"
                + "#6 (given 10 s ago, attempt 1 of 3): raise a wall around Alex", 999_999, gap));
        assertTrue(Minimap.useful("when-useful", List.of("prayer: Steve prays: please come here and save me"), "",
                999_999, gap));
    }

    // ---- Pictures ---------------------------------------------------------------------------------

    /** A village green: a house with a roof, a pond, a path, trees; and a cave system far below it. */
    private static RendererTest.MapVoxels world() {
        RendererTest.MapVoxels v = new RendererTest.MapVoxels(63, 140);
        // A house (walls, floor, roof) at x 8..18, z -14..-4.
        for (int x = 8; x <= 18; x++) {
            for (int z = -14; z <= -4; z++) {
                boolean wall = x == 8 || x == 18 || z == -14 || z == -4;
                v.put(x, 63, z, Material.SPRUCE_PLANKS);
                for (int y = 64; y <= 66; y++) {
                    if (wall && !(x == 13 && z == -4 && y < 66)) v.put(x, y, z, Material.COBBLESTONE);
                }
                v.put(x, 67, z, Material.OAK_PLANKS);
            }
        }
        v.put(10, 64, -12, Material.CHEST);
        v.put(16, 64, -12, Material.RED_WOOL);
        v.put(16, 64, -11, Material.RED_WOOL);
        v.put(10, 64, -6, Material.LANTERN);
        // A pond and a gravel path.
        for (int x = -26; x <= -12; x++) {
            for (int z = 6; z <= 18; z++) {
                if (Math.hypot(x + 19, (z - 12) * 1.2) < 7) v.put(x, 63, z, Material.WATER);
            }
        }
        for (int z = -3; z <= 28; z++) {
            v.put(13 + (int) Math.round(Math.sin(z / 6.0) * 2), 63, z, Material.GRAVEL);
        }
        // Trees.
        int[][] trees = { { -8, -18 }, { -22, -8 }, { 26, 14 }, { -4, 24 }, { 30, -20 } };
        for (int[] t : trees) {
            for (int y = 64; y <= 67; y++) v.put(t[0], y, t[1], Material.SPRUCE_LOG);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) < 4) v.put(t[0] + dx, 68, t[1] + dz, Material.GRASS_BLOCK);
                }
            }
        }
        // Caves at y 30..32: a winding tunnel, a chamber with a lava pool and ore, a side passage.
        for (int x = -40; x <= 40; x++) {
            int cz = (int) Math.round(Math.sin(x / 7.0) * 6);
            for (int z = cz - 2; z <= cz + 2; z++) {
                for (int y = 30; y <= 32; y++) v.put(x, y, z, Material.AIR);
            }
        }
        for (int x = 6; x <= 24; x++) {
            for (int z = 4; z <= 22; z++) {
                if (Math.hypot(x - 15, z - 13) < 9.5) {
                    for (int y = 30; y <= 34; y++) v.put(x, y, z, Material.AIR);
                    if (Math.hypot(x - 19, z - 16) < 3) v.put(x, 29, z, Material.GOLD_BLOCK);
                }
            }
        }
        for (int z = -30; z <= -3; z++) {
            for (int x = -17; x <= -15; x++) {
                for (int y = 30; y <= 32; y++) v.put(x, y, z, Material.AIR);
            }
        }
        v.put(9, 30, 9, Material.DIAMOND_ORE);
        v.put(-16, 29, -20, Material.WATER);
        v.put(-16, 29, -21, Material.WATER);
        v.put(-15, 29, -21, Material.WATER);
        return v;
    }

    private static final List<Place> PLACES = List.of(
            new Place("VAEL (YOU)", 2, 4, PlaceKind.OWN_VESSEL),
            new Place("Orrin", -30, -24, PlaceKind.OTHER_VESSEL),
            new Place("Steve's house", 13, -9, PlaceKind.STRUCTURE),
            new Place("Shrine of Vael", 24, 24, PlaceKind.WORK));

    private static BufferedImage draw(List<Panel> panels, List<Mortal> everyone, List<Foe> foes, int size) {
        List<BufferedImage> out = new ArrayList<>();
        int each = Minimap.panelSize(size, panels.size());
        for (Panel p : panels) {
            out.add(Minimap.render(world(), RendererTest.palette, p, everyone, foes, PLACES, each));
        }
        return Minimap.compose(out);
    }

    private static int pngBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.size();
    }

    @Test
    void previewsOfTheSurfaceACaveAndAMixedGroup() throws IOException {
        Mortal steve = new Mortal("Steve", -2.5, 64, 2.5, 200, Level.SURFACE, 0xFFD23F);
        Mortal alex = new Mortal("Alex", 1.5, 64, 6.5, 30, Level.SURFACE, 0x3FD2FF);
        Mortal noor = new Mortal("Noor_Builds", -24.5, 64, 22.5, -80, Level.SURFACE, 0xFF7AE0);
        Mortal kai = new Mortal("Kai", 27.5, 64, -6.5, 100, Level.SURFACE, 0x7CFF6B);
        Mortal home = new Mortal("Mira", 12.5, 64, -10.5, 140, Level.INDOORS, 0xFFA03F);
        Mortal miner = new Mortal("Dig_Dug", 14.5, 30, 12.5, -40, Level.UNDERGROUND, 0x9DB4FF);
        Mortal mate = new Mortal("Juno", -15.5, 30, -12.5, 10, Level.UNDERGROUND, 0xFF5E5E);
        List<Foe> topFoes = List.of(new Foe(-6.5, 64, -2.5, Level.SURFACE), new Foe(31.5, 64, -12.5, Level.SURFACE),
                new Foe(33.5, 64, -10.5, Level.SURFACE));
        List<Foe> caveFoes = List.of(new Foe(20.5, 30, 8.5, Level.UNDERGROUND), new Foe(22.5, 30, 18.5, Level.UNDERGROUND),
                new Foe(-10.5, 30, -3.5, Level.UNDERGROUND));

        // 1. The surface with several mortals.
        List<Mortal> top = List.of(steve, alex, noor, kai);
        Minimap.Grouping g = Minimap.group(top, 32, 96, 4);
        assertEquals(1, g.panels().size());
        BufferedImage surface = draw(g.panels(), top, topFoes, 448);
        RendererTest.save(surface, "minimap-surface");
        assertEquals(448, surface.getWidth());

        // 2. A cave: the tunnel they stand in, not the village above it.
        List<Mortal> under = List.of(miner, mate);
        g = Minimap.group(under, 32, 96, 4);
        assertEquals(1, g.panels().size());
        assertTrue(g.panels().get(0).slice());
        BufferedImage cave = draw(g.panels(), under, caveFoes, 448);
        RendererTest.save(cave, "minimap-cave");
        Frame f = new Frame(g.panels().get(0).cx(), g.panels().get(0).cz(), g.panels().get(0).radius(), 448);
        int rock = cave.getRGB(f.px(-28), f.py(26)) & 0xFFFFFF, floor = cave.getRGB(f.px(11.5), f.py(18.5)) & 0xFFFFFF;
        assertTrue(Renderer.green(floor) > Renderer.green(rock) + 30, "open floor is brighter than solid rock");
        BufferedImage aboveThem = Minimap.render(world(), RendererTest.palette, new Panel(g.panels().get(0).cx(),
                g.panels().get(0).cz(), g.panels().get(0).radius(), false, 64, Level.SURFACE, List.of()), List.of(),
                List.of(), List.of(), 448);
        assertNotEquals(aboveThem.getRGB(f.px(-28), f.py(26)), cave.getRGB(f.px(-28), f.py(26)),
                "the slice is not the surface above");

        // 3. Mixed: some on the surface, one in the house, two in the cave below: a panel per level.
        List<Mortal> all = List.of(steve, alex, home, miner, mate, kai);
        List<Foe> allFoes = new ArrayList<>(topFoes);
        allFoes.addAll(caveFoes);
        g = Minimap.group(all, 32, 96, 4);
        assertEquals(3, g.panels().size());
        assertEquals("INDOORS Y=64", g.panels().get(1).title());
        BufferedImage mixed = draw(g.panels(), all, allFoes, 448);
        RendererTest.save(mixed, "minimap-mixed");

        // 4. One mortal's own map: the level of the one asked about, the others marked above or below.
        BufferedImage focus = draw(List.of(new Panel(14, 12, 24, true, 30, Level.UNDERGROUND, List.of(miner))), all,
                caveFoes, 448);
        RendererTest.save(focus, "minimap-focus");

        System.out.printf("[minimap] surface %dx%d ~%d tokens, %d KB png; mixed %dx%d ~%d tokens, %d KB png%n",
                surface.getWidth(), surface.getHeight(), Minimap.tokens(surface.getWidth(), surface.getHeight()),
                pngBytes(surface) / 1024, mixed.getWidth(), mixed.getHeight(),
                Minimap.tokens(mixed.getWidth(), mixed.getHeight()), pngBytes(mixed) / 1024);
        assertTrue(Minimap.tokens(surface.getWidth(), surface.getHeight()) < 300);
        assertEquals(3 * 358 + 8, mixed.getWidth(), "three panels in a row, no empty tile");
        assertTrue(Minimap.tokens(mixed.getWidth(), mixed.getHeight()) < 530);
    }
}
