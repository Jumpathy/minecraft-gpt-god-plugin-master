package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.Senses.Change;
import net.bigyous.gptgodmc.god.Senses.Threat;

/** The god knows what a mortal cannot see, and what they are making, in a line or two. */
class SensesTest {
    @Test
    void sidesFollowWhereTheMortalFaces() {
        // Yaw 0 faces south (+z).
        assertEquals("ahead", Senses.side(0, 0, 5));
        assertEquals("behind", Senses.side(0, 0, -5));
        assertEquals("left", Senses.side(0, 5, 0));
        assertEquals("right", Senses.side(0, -5, 0));
        // Yaw 90 faces west (-x).
        assertEquals("ahead", Senses.side(90, -5, 0));
        assertEquals("behind", Senses.side(90, 5, 0));
    }

    @Test
    void aCreeperBehindThemIsUrgentAndNamedFirst() {
        List<Threat> t = List.of(new Threat("zombie", 9, "ahead", true, true),
                new Threat("creeper", 4, "behind", true, true));
        assertEquals(2, Senses.urgency(t, 20));
        String s = Senses.describe(t);
        assertTrue(s.startsWith("creeper 4 blocks BEHIND them"), s);
        assertTrue(s.contains("zombie in view"), s);
        // A zombie they are looking at is no surprise.
        assertEquals(0, Senses.urgency(List.of(new Threat("zombie", 3, "ahead", true, true)), 20));
        // Three hunters and little health is urgent even in plain sight.
        List<Threat> pack = List.of(new Threat("zombie", 5, "ahead", true, true),
                new Threat("skeleton", 8, "ahead", true, true), new Threat("spider", 6, "left", true, true));
        assertEquals(2, Senses.urgency(pack, 6));
        assertEquals("none", Senses.describe(List.of()));
    }

    @Test
    void needIsSpelledOutSoSurvivalIsNotMistakenForSin() {
        assertNull(Senses.hunger(20));
        assertNull(Senses.hunger(13));
        assertEquals("getting hungry", Senses.hunger(12));
        assertEquals("hungry", Senses.hunger(6));
        assertEquals("starving", Senses.hunger(0));
        assertNull(Senses.hurt(20, 20));
        assertEquals("hurt", Senses.hurt(10, 20));
        assertEquals("badly hurt", Senses.hurt(7, 20));
        assertEquals("badly hurt", Senses.hurt(14, 40), "measured against their own full health");
        assertNull(Senses.need(18, 20, 20));
        assertEquals("hungry", Senses.need(5, 20, 20));
        assertEquals("hurt", Senses.need(18, 9, 20));
        assertEquals("starving and badly hurt", Senses.need(0, 3, 20));
    }

    @Test
    void recentPlacingReadsAsWhatIsBeingBuilt() {
        long now = 1_000_000;
        List<Change> walls = new ArrayList<>();
        for (int y = 64; y < 68; y++) {
            for (int x = 0; x < 7; x++) {
                walls.add(new Change(now - 1000, "oak_planks", x, y, 0, true));
                walls.add(new Change(now - 1000, "oak_planks", x, y, 4, true));
            }
        }
        walls.add(new Change(now - 500, "glass", 3, 65, 0, true));
        String s = Senses.describeWork(walls, now);
        assertTrue(s.startsWith("building: 57 blocks (56 oak_planks, 1 glass)"), s);
        assertTrue(s.contains("7x5 wide, 4 high") && s.contains("house taking shape"), s);
        assertNull(Senses.describeWork(walls, now + Senses.WINDOW_MS + 5000), "old work is forgotten");
        assertNull(Senses.describeWork(walls.subList(0, 3), now), "three blocks say nothing");
    }

    @Test
    void shapesAndDigsAreGuessedFromTheirOutline() {
        assertEquals("looks like a tower or pillar", Senses.shape(3, 3, 14, 60, Set.of("stone_bricks")));
        assertEquals("looks like a floor or platform", Senses.shape(8, 6, 1, 40, Set.of("oak_planks")));
        assertEquals("looks like a farm or garden", Senses.shape(9, 9, 1, 30, Set.of("farmland", "wheat")));
        assertEquals("they are placing TNT", Senses.shape(2, 2, 1, 6, Set.of("tnt")));
        long now = 5_000_000;
        List<Change> shaft = new ArrayList<>();
        for (int y = 64; y > 40; y--) shaft.add(new Change(now - 100, "stone", 0, y, 0, false));
        assertTrue(Senses.describeWork(shaft, now).contains("a shaft going down"), Senses.describeWork(shaft, now));
    }
}
