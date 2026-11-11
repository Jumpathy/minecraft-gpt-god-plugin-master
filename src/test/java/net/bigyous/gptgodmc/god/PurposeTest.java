package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

/** The god keeps a few goals of its own, measures them, is reminded of neglected ones, and remembers outcomes. */
class PurposeTest {
    static final long T = 1_700_000_000_000L;
    static final long MIN = 60_000L;

    @Test
    void aGodWithNoPurposeIsToldToChooseOne() {
        Memory m = new Memory();
        assertTrue(Purpose.render(m, T, 10, true).contains("You pursue nothing"));
        assertEquals("", Purpose.render(m, T, 10, false), "nobody to show it to: no push");
    }

    @Test
    void goalsAreMeasuredNeglectedAndClosed() {
        Memory m = new Memory();
        Purpose.Goal g = Purpose.set(m, "Raise my temple on the hill", "Quartz, a spire, an altar", "a blessing for all",
                true, 3, T);
        assertEquals("g1", g.id);
        Purpose.update(m, "temple", 40, "Ashwyn laid the foundation", T + 2 * MIN);
        assertEquals(40, Purpose.find(m, "g1").progress);
        String fresh = Purpose.render(m, T + 5 * MIN, 10, true);
        assertTrue(fresh.contains("MISSION") && fresh.contains("40%") && !fresh.contains("Untouched for"), fresh);
        String stale = Purpose.render(m, T + 20 * MIN, 10, true);
        assertTrue(stale.contains("Untouched for") && stale.contains("Do not press mortals"), stale);
        assertEquals(g, Purpose.banner(m));
        Purpose.close(m, "g1", true, "A spire of quartz stands", T + 30 * MIN);
        assertNull(Purpose.banner(m));
        String after = Purpose.render(m, T + 31 * MIN, 10, true);
        assertTrue(after.contains("achieved") && after.contains("You pursue nothing"), after);
    }

    @Test
    void itPursuesOnlyAFewAtOnce() {
        Memory m = new Memory();
        Purpose.set(m, "One", null, null, false, 2, T);
        Purpose.set(m, "Two", null, null, false, 2, T);
        assertThrows(Purpose.Refusal.class, () -> Purpose.set(m, "Three", null, null, false, 2, T));
        assertThrows(Purpose.Refusal.class, () -> Purpose.update(m, "nothing like it", 5, null, T));
        assertNull(Purpose.banner(m), "private ambitions are not shown on the bar");
    }

    @Test
    void goalsSurviveARestart() {
        Memory m = new Memory();
        Purpose.set(m, "See the dragon slain", null, "elytra", true, 3, T);
        Memory back = new Gson().fromJson(new Gson().toJson(m), Memory.class);
        assertEquals("See the dragon slain", Purpose.find(back, "g1").title);
        assertEquals(1, back.goalCounter);
    }
}
