package net.bigyous.gptgodmc.god.vision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.testutil.FakeServer;

/** A look names what was built in view: the shrine a god once took for "a brown block" is told block by block. */
class SightingsTest {
    static Palette palette;

    @BeforeAll
    static void setup() {
        FakeServer.install();
        palette = Palette.blank(Material.values().length);
        for (Material m : Material.values()) {
            String n = m.name();
            if (n.startsWith("LEGACY_") || n.endsWith("AIR")) continue;
            // Torches have no map color: the picture does not draw them at all.
            palette.kind[m.ordinal()] = n.contains("TORCH") ? Palette.EMPTY : n.contains("GLASS") ? Palette.GLASS
                    : Palette.SOLID;
        }
    }

    static String name(int code) {
        return Material.values()[code].name();
    }

    /** The shrine of the live test: a cobblestone base, four mossy wall posts with torches, gold, a sign, a campfire. */
    static RendererTest.MapVoxels shrine() {
        RendererTest.MapVoxels v = new RendererTest.MapVoxels(63, 40);
        for (int x = -2; x <= 2; x++) for (int z = -10; z <= -6; z++) v.put(x, 64, z, Material.COBBLESTONE);
        for (int[] c : new int[][] { { -2, -10 }, { 2, -10 }, { -2, -6 }, { 2, -6 } }) {
            v.put(c[0], 65, c[1], Material.MOSSY_COBBLESTONE_WALL);
            v.put(c[0], 66, c[1], Material.TORCH);
        }
        v.put(0, 65, -8, Material.GOLD_BLOCK);
        v.put(0, 66, -8, Material.OAK_SIGN);
        v.put(0, 65, -6, Material.CAMPFIRE);
        return v;
    }

    @Test
    void theLandIsNotNamedButWhatWasBuiltIs() {
        assertTrue(Sightings.natural("GRASS_BLOCK") && Sightings.natural("ACACIA_LOG") && Sightings.natural("STONE")
                && Sightings.natural("SHORT_GRASS") && Sightings.natural("POPPY") && Sightings.natural("DEEPSLATE"));
        for (String built : List.of("COBBLESTONE", "MOSSY_COBBLESTONE_WALL", "GOLD_BLOCK", "OAK_SIGN", "CAMPFIRE",
                "TORCH", "OAK_PLANKS", "STONE_BRICKS", "GLASS", "CHEST", "REDSTONE_BLOCK", "SMOOTH_STONE",
                "COBBLED_DEEPSLATE", "DIAMOND_ORE", "OAK_DOOR", "LANTERN")) {
            assertFalse(Sightings.natural(built), built);
        }
    }

    @Test
    void theShrineIsToldBlockByBlock() {
        // A mortal standing south of it, looking north (yaw 180) and a little down.
        Renderer.Camera cam = new Renderer.Camera(0.5, 66.6, -2.5, 180, 10, 70);
        List<Sightings.Seen> seen = Sightings.notable(Sightings.scan(shrine(), palette, cam, 640.0 / 360,
                SightingsTest::name));
        Map<String, Sightings.Seen> by = new java.util.HashMap<>();
        for (Sightings.Seen s : seen) by.put(name(s.code()), s);
        for (String kind : List.of("CAMPFIRE", "GOLD_BLOCK", "OAK_SIGN", "TORCH", "MOSSY_COBBLESTONE_WALL", "COBBLESTONE")) {
            assertTrue(by.containsKey(kind), kind + " is in view: " + by.keySet());
        }
        assertFalse(by.containsKey("GRASS_BLOCK"), "the ground is not named");
        assertEquals(4, by.get("TORCH").blocks(), "four torches, though the picture draws none");
        assertFalse(by.get("TORCH").drawn());
        assertTrue(by.get("GOLD_BLOCK").drawn());
        assertEquals(1, by.get("GOLD_BLOCK").blocks());
        assertTrue(Math.abs(by.get("GOLD_BLOCK").side()) < 0.33, "the gold is in the middle of the frame");
        assertTrue(by.get("CAMPFIRE").nearest() < by.get("GOLD_BLOCK").nearest(), "the fire stands in front of the gold");
        assertEquals("CAMPFIRE", name(seen.get(0).code()), "nearest first");

        Sightings.Seen sign = by.get("OAK_SIGN");
        String text = Sightings.describe(seen, SightingsTest::name,
                Map.of(sign.x() + "," + sign.y() + "," + sign.z(), "VHORAXS / the eye"));
        assertTrue(text.startsWith("In view, by name (nearest first): campfire, "), text);
        assertTrue(text.contains("gold_block, "), text);
        assertTrue(text.contains("torch x4, "), text);
        assertTrue(text.contains("(too thin to show in the picture)"), text);
        assertTrue(text.contains("oak_sign, ") && text.contains("reading \"VHORAXS / the eye\""), text);
        assertTrue(text.length() < 700, "short enough to ride with every look: " + text.length());
    }

    @Test
    void anEmptyPlainSaysNothing() {
        Renderer.Camera cam = new Renderer.Camera(0.5, 66.6, 20.5, 0, 10, 70);
        List<Sightings.Seen> seen = Sightings.notable(
                Sightings.scan(new RendererTest.MapVoxels(63, 40), palette, cam, 640.0 / 360, SightingsTest::name));
        assertTrue(seen.isEmpty());
        assertEquals("", Sightings.describe(seen, SightingsTest::name, Map.of()));
    }

    @Test
    void whatStandsBehindAWallIsNotNamed() {
        RendererTest.MapVoxels v = shrine();
        for (int x = -6; x <= 6; x++) for (int y = 64; y <= 72; y++) v.put(x, y, -4, Material.OAK_PLANKS);
        Renderer.Camera cam = new Renderer.Camera(0.5, 66.6, -2.5, 180, 10, 70);
        List<Sightings.Seen> seen = Sightings.notable(Sightings.scan(v, palette, cam, 640.0 / 360, SightingsTest::name));
        assertEquals(1, seen.size(), "only the wall");
        assertEquals("OAK_PLANKS", name(seen.get(0).code()));
    }
}
