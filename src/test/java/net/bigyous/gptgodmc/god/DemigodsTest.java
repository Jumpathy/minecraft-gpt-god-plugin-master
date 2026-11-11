package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

/** Apotheosis is rare: off by default, for the most beloved, one at a time, with a long wait between. */
class DemigodsTest {
    static final long T = 1_700_000_000_000L;
    static final long HOUR = 3_600_000L;

    @Test
    void theGiftHasHardLimits() {
        Demigods d = new Demigods();
        assertTrue(d.refusal(false, "Ashwyn", 10, 8, 1, 72, T).contains("forbidden"), "off by default");
        assertTrue(d.refusal(true, "Ashwyn", 5, 8, 1, 72, T).contains("highest regard"));
        assertNull(d.refusal(true, "Ashwyn", 9, 8, 1, 72, T));
        d.raise("Ashwyn", "u1", "Vaelor", "slew the dragon alone", 0, T);
        assertTrue(d.refusal(true, "Ashwyn", 9, 8, 1, 72, T + HOUR).contains("already a demigod"));
        assertTrue(d.refusal(true, "Pip", 10, 8, 1, 72, T + HOUR).contains("already a demigod in this world"));
        d.unmake("Ashwyn");
        assertTrue(d.refusal(true, "Pip", 10, 8, 1, 72, T + HOUR).contains("spent"), "a long wait between");
        assertNull(d.refusal(true, "Pip", 10, 8, 1, 72, T + 73 * HOUR));
    }

    @Test
    void timedDivinityFades() {
        Demigods d = new Demigods();
        d.raise("Ashwyn", "u1", "Vaelor", "held the gate", 60, T);
        assertNotNull(d.find("ashwyn", T + 59 * 60_000L));
        assertNull(d.find("Ashwyn", T + 61 * 60_000L));
        assertEquals(1, d.expire(T + 61 * 60_000L).size());
        assertEquals(0, d.living(T + 61 * 60_000L));
        assertTrue(d.describe(T + 2 * HOUR, 72).contains("spent"));
    }

    @Test
    void itSurvivesARestart() {
        Demigods d = new Demigods();
        d.raise("Ashwyn", "u1", "Vaelor", "slew the dragon", 0, T);
        Demigods back = new Gson().fromJson(new Gson().toJson(d), Demigods.class);
        assertEquals("Vaelor", back.find("Ashwyn", T + HOUR).by);
        assertEquals(T, back.lastRaisedAt);
    }

    @Test
    void wingsSpreadBehindTheBackAndBeat() {
        List<Vector> folded = Demigods.wing(-1, 6), raised = Demigods.wing(1, 6);
        assertEquals(folded.size(), raised.size());
        double maxUp = raised.stream().mapToDouble(Vector::getY).max().orElse(0);
        double maxDown = folded.stream().mapToDouble(Vector::getY).max().orElse(0);
        assertTrue(maxUp > maxDown, "raised wings reach higher");
        for (Vector v : raised) {
            assertTrue(v.getZ() < 0, "behind the back");
            assertTrue(Math.abs(v.getX()) <= 2.0 && v.getY() > 0.4 && v.getY() < 3.2, v.toString());
        }
        // Facing south (yaw 0) the wings lie north of the body; facing east (yaw -90) they lie west.
        assertTrue(Demigods.facing(raised, 0).stream().allMatch(v -> v.getZ() < 0));
        assertTrue(Demigods.facing(raised, -90).stream().allMatch(v -> v.getX() < 0));
    }
}
