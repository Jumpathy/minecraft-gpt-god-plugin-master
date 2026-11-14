package net.bigyous.gptgodmc.god.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.god.FxScenes;

/** The reskin layer: which blocks it picks, how far it reaches, and what the god is told when it writes one wrongly. */
class FxReskinTest {
    private static FxReskin one(String layer) throws Exception {
        return FxReskin.parse(FxScenes.check("[" + layer + "]").get(0).getAsJsonObject());
    }

    @Test
    void wiltingSomeOfTheGrassInASmallCircle() throws Exception {
        FxReskin r = one("{\"type\":\"reskin\",\"blocks\":[\"Grass_Block\",\"minecraft:short_grass\"],\"as\":\"coarse_dirt\","
                + "\"radius\":2.5,\"density\":0.4,\"duration\":300}");
        assertEquals("coarse_dirt", r.as);
        assertTrue(r.changes("grass_block") && r.changes("minecraft:short_grass"));
        assertFalse(r.changes("stone"));
        double[] vars = new double[FxExpr.VARS];
        assertEquals(2.5, r.reach(10, vars));
        // A 5 by 5 circle on the ground: the middle and its ring are in, the corners and anything high above are not.
        assertTrue(r.inside(0, 0, 0, 2.5) && r.inside(2, 0, 1, 2.5) && r.inside(0, 2, 0, 2.5));
        assertFalse(r.inside(2, 0, 2, 2.5) || r.inside(3, 0, 0, 2.5) || r.inside(0, 3, 0, 2.5));
        // Only a share of the blocks is picked, the same ones every time it is asked.
        int picked = 0;
        for (int x = 0; x < 50; x++) {
            for (int z = 0; z < 50; z++) {
                boolean now = r.picks(x, 64, z);
                assertEquals(now, r.picks(x, 64, z));
                if (now) picked++;
            }
        }
        assertTrue(picked > 2500 * 0.33 && picked < 2500 * 0.47, "about 40%: " + picked);
        // And they are scattered, not clumped: a picked block's neighbour is picked about as often as any block.
        int pairs = 0, both = 0;
        for (int x = 0; x < 49; x++) {
            for (int z = 0; z < 50; z++) {
                if (!r.picks(x, 64, z)) continue;
                pairs++;
                if (r.picks(x + 1, 64, z)) both++;
            }
        }
        assertTrue(both > pairs * 0.3 && both < pairs * 0.5, "neighbours picked: " + both + " of " + pairs);
        assertEquals(300, FxScenes.length(FxScenes.check("[{\"type\":\"reskin\",\"as\":\"dirt\",\"duration\":300}]")));
        assertEquals(FxReskin.DEFAULT_TICKS, FxScenes.length(FxScenes.check("[{\"type\":\"reskin\",\"as\":\"dirt\"}]")));
    }

    @Test
    void itCanCreepOutwardAndBeABall() throws Exception {
        FxReskin r = one("{\"type\":\"reskin\",\"color\":\"#3A2A12\",\"strength\":0.8,\"radius\":\"min(6, t*0.1)\",\"shape\":\"sphere\"}");
        double[] vars = new double[FxExpr.VARS];
        assertEquals(0, r.reach(0, vars));
        assertEquals(3, r.reach(30, vars), 1e-9);
        assertEquals(6, r.reach(500, vars));
        assertTrue(r.ball && r.inside(0, 5, 0, 6) && !r.inside(4, 4, 4, 6));
        assertTrue(r.changes("anything"), "no kinds named means any solid block");
        assertTrue(r.picks(1, 2, 3), "full density picks every block");
        assertEquals(0x3A2A12, r.color);
        assertEquals(FxReskin.MAX_RADIUS, one("{\"type\":\"reskin\",\"as\":\"dirt\",\"radius\":999}").reach(1, vars));
    }

    @Test
    void mistakesAreExplainedAndNonsenseIsHarmless() {
        for (String[] bad : new String[][] { { "{\"type\":\"reskin\"}", "what the blocks should look like" },
                { "{\"type\":\"reskin\",\"as\":\"not a block!\"}", "\"as\" is a block id" },
                { "{\"type\":\"reskin\",\"as\":\"dirt\",\"blocks\":[\"grass block!\"]}", "\"blocks\" lists block ids" },
                { "{\"type\":\"reskin\",\"as\":\"dirt\",\"radius\":\"grow(t)\"}", "radius: I do not know the function 'grow'" } }) {
            FxScenes.Invalid e = assertThrows(FxScenes.Invalid.class, () -> FxScenes.check("[" + bad[0] + "]"), bad[0]);
            assertTrue(e.getMessage().contains(bad[1]), bad[0] + " -> " + e.getMessage());
        }
        assertNull(FxReskin.parse(JsonParser.parseString("{\"type\":\"reskin\"}").getAsJsonObject()), "nothing to show");
        FxReskin wild = FxReskin.parse(JsonParser.parseString("{\"as\":\"dirt\",\"radius\":\"((\",\"limit\":999999,\"height\":999,\"density\":7,"
                + "\"duration\":999999,\"blocks\":7}").getAsJsonObject());
        assertEquals(FxReskin.MAX_BLOCKS, wild.limit);
        assertEquals(FxReskin.MAX_HEIGHT, wild.height);
        assertEquals(FxReskin.MAX_TICKS, wild.duration);
        assertEquals(4, wild.reach(1, new double[FxExpr.VARS]));
        assertTrue(FxScenes.HELP.contains("reskin {blocks"));
    }
}
