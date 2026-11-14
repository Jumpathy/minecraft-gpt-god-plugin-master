package net.bigyous.gptgodmc.god.build;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Where a build moves to when its spot already holds something built. */
class SiteTest {
    @Test
    void itTriesTheSpotThenFurtherFromTheTargetThenEitherSide() {
        // The build stands north of the mortal who asked (dz < 0): further away is further north.
        List<int[]> s = Site.shifts(0, -8, 9);
        assertEquals(8, s.size());
        assertArrayEquals(new int[] { 0, 0 }, s.get(0));
        assertArrayEquals(new int[] { 0, -9 }, s.get(1));
        assertArrayEquals(new int[] { 0, -18 }, s.get(2));
        assertArrayEquals(new int[] { 0, -27 }, s.get(3));
        assertArrayEquals(new int[] { 9, 0 }, s.get(4));
        assertArrayEquals(new int[] { -9, 0 }, s.get(5));
        assertArrayEquals(new int[] { 18, 0 }, s.get(6));
        assertArrayEquals(new int[] { -18, 0 }, s.get(7));
    }

    @Test
    void aBuildOnTheTargetItselfStillHasSomewhereToGo() {
        List<int[]> s = Site.shifts(0, 0, 6);
        assertArrayEquals(new int[] { 6, 0 }, s.get(1));
        assertArrayEquals(new int[] { 0, 6 }, s.get(4));
        assertArrayEquals(new int[] { 0, -6 }, s.get(5));
    }

    @Test
    void whatStandsInTheWayIsNamedCommonestFirst() {
        Map<String, Integer> kinds = new LinkedHashMap<>();
        kinds.put("campfire", 1);
        kinds.put("cobblestone", 25);
        kinds.put("gold_block", 1);
        kinds.put("mossy_cobblestone_wall", 4);
        kinds.put("oak_sign", 1);
        kinds.put("torch", 4);
        assertEquals("cobblestone x25, mossy_cobblestone_wall x4, torch x4, campfire, gold_block and more", Site.list(kinds));
        assertEquals("oak_planks x2", Site.list(Map.of("oak_planks", 2)));
    }
}
