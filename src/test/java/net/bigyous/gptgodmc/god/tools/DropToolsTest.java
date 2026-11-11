package net.bigyous.gptgodmc.god.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.Power;

/** Dropped blocks fall as themselves, and how hard they hit is capped by the server. */
class DropToolsTest {
    @Test
    void anvilsHitHarderThanOtherBlocksAndRespectTheCap() {
        assertEquals(16f, DropTools.damage(Material.ANVIL, 8)[1]);
        assertEquals(2f, DropTools.damage(Material.ANVIL, 8)[0]);
        assertEquals(4f, DropTools.damage(Material.CHIPPED_ANVIL, 2)[1], "a low cap wins");
        assertEquals(12f, DropTools.damage(Material.GOLD_BLOCK, 10)[1], "ordinary blocks hurt less");
        assertTrue(DropTools.damage(Material.POINTED_DRIPSTONE, 20)[1] <= 40f);
    }

    @Test
    void thePowerIsRegisteredLastAndNotForLesserGods() {
        assertNotNull(ToolRegistry.get("drop_block"));
        assertTrue(Power.MIGHTY.contains("drop_block"));
    }
}
