package net.bigyous.gptgodmc.god.hooks;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.hooks.WorldEditRules.Refusal;
import net.bigyous.gptgodmc.god.hooks.WorldEditRules.Request;

/** What the god may ask of WorldEdit, before WorldEdit is touched. */
class WorldEditRulesTest {
    private static final int MAX = 20000;
    private static final int[] A = { 0, 64, 0 }, B = { 9, 68, 9 };

    private static Request req(String action, int[] from, int[] to, int[] at, String pattern, String mask, int radius, String expr,
            int[] dir, int count) {
        return new Request(action, from, to, at, pattern, mask, radius, 4, false, expr, dir, count, 1);
    }

    private static void refused(Request r, String why) {
        Refusal e = assertThrows(Refusal.class, () -> WorldEditRules.check(r, MAX), r.toString());
        assertTrue(e.getMessage().contains(why), e.getMessage());
    }

    @Test
    void directionsAreUnitSteps() throws Exception {
        assertArrayEquals(new int[] { 0, 0, -1 }, WorldEditRules.direction("North"));
        assertArrayEquals(new int[] { 0, 1, 0 }, WorldEditRules.direction(" up "));
        assertArrayEquals(new int[] { -1, 0, 0 }, WorldEditRules.direction("west"));
        assertThrows(Refusal.class, () -> WorldEditRules.direction("sideways"));
        assertThrows(Refusal.class, () -> WorldEditRules.direction(null));
    }

    @Test
    void regionsHaveALimit() {
        assertEquals(500, WorldEditRules.volume(A, B));
        assertEquals(500, WorldEditRules.volume(B, A), "corners in either order");
        assertDoesNotThrow(() -> WorldEditRules.checkRegion(A, B, MAX));
        assertThrows(Refusal.class, () -> WorldEditRules.checkRegion(A, new int[] { 300, 64, 0 }, MAX), "too long a side");
        assertThrows(Refusal.class, () -> WorldEditRules.checkRegion(A, new int[] { 100, 164, 100 }, MAX), "too many blocks");
        assertThrows(Refusal.class, () -> WorldEditRules.checkRegion(new int[] { Integer.MIN_VALUE, 0, 0 }, new int[] { Integer.MAX_VALUE, 0, 0 }, MAX));
    }

    @Test
    void forbiddenBlocksAreRefusedInAnyPattern() {
        for (String ok : new String[] { "stone", "50%stone,50%andesite", "oak_stairs[facing=north]", "air", "minecraft:gold_block", "light_blue_wool" }) {
            assertDoesNotThrow(() -> WorldEditRules.checkPattern(ok), ok);
        }
        for (String bad : new String[] { "bedrock", "50%stone,50%BEDROCK", "minecraft:barrier", "command_block[conditional=true]",
                "repeating_command_block", "structure_block", "light", "", null }) {
            assertThrows(Refusal.class, () -> WorldEditRules.checkPattern(bad), String.valueOf(bad));
        }
    }

    @Test
    void eachActionNeedsWhatItUses() {
        assertDoesNotThrow(() -> WorldEditRules.check(req("set", A, B, null, "stone", null, 0, null, null, 1), MAX));
        assertDoesNotThrow(() -> WorldEditRules.check(req("undo", null, null, null, null, null, 0, null, null, 1), MAX));
        assertDoesNotThrow(() -> WorldEditRules.check(req("smooth", A, B, null, null, null, 0, null, null, 1), MAX));
        assertDoesNotThrow(() -> WorldEditRules.check(req("sphere", null, null, A, "glass", null, 8, null, null, 1), MAX));
        assertDoesNotThrow(() -> WorldEditRules.check(req("generate", A, B, null, "stone", null, 0, "x^2+y^2+z^2<1", null, 1), MAX));
        assertDoesNotThrow(() -> WorldEditRules.check(req("stack", A, B, null, null, null, 0, null, new int[] { 0, 1, 0 }, 3), MAX));
        refused(req("melt", A, B, null, "stone", null, 0, null, null, 1), "Unknown action");
        refused(req("set", A, null, null, "stone", null, 0, null, null, 1), "both corners");
        refused(req("set", A, B, null, null, null, 0, null, null, 1), "pattern");
        refused(req("set", A, B, null, "bedrock", null, 0, null, null, 1), "may not place bedrock");
        refused(req("replace", A, B, null, "stone", " ", 0, null, null, 1), "needs a mask");
        refused(req("sphere", null, null, null, "stone", null, 5, null, null, 1), "needs a place");
        refused(req("sphere", null, null, A, "stone", null, 0, null, null, 1), "radius");
        refused(req("sphere", null, null, A, "stone", null, 60, null, null, 1), "too large");
        refused(req("generate", A, B, null, "stone", null, 0, null, null, 1), "expression");
        refused(req("move", A, B, null, null, null, 0, null, null, 1), "direction");
        refused(req("stack", A, B, null, null, null, 0, null, new int[] { 1, 0, 0 }, 64), "would change about 32000");
    }

    @Test
    void everyActionIsSorted() {
        for (String action : WorldEditRules.ACTIONS) {
            int kinds = (WorldEditRules.REGION.contains(action) ? 1 : 0) + (WorldEditRules.SHAPES.contains(action) ? 1 : 0)
                    + (action.equals("line") || action.equals("undo") ? 1 : 0);
            assertEquals(1, kinds, action);
        }
        assertFalse(WorldEditHook.installed(), "no server in a unit test, and no crash asking");
    }
}
