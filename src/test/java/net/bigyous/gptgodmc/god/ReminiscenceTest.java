package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.Reminiscence.Here;
import net.bigyous.gptgodmc.god.Reminiscence.Recalled;

/** In a quiet moment an old memory that fits the present comes back to the god, one at a time. */
class ReminiscenceTest {
    static final long T = 1_700_000_000_000L;
    static final long MIN = 60_000L, HOUR = 60 * MIN, DAY = 24 * HOUR;

    private Chronicler.Model savedModel;
    private Executor savedExecutor;

    @BeforeEach
    void offline() {
        savedModel = Chronicler.model;
        savedExecutor = Chronicler.executor;
        Chronicler.executor = Runnable::run;
        Chronicler.model = (prompt, max, purpose) -> null;
    }

    @AfterEach
    void restore() {
        Chronicler.model = savedModel;
        Chronicler.executor = savedExecutor;
    }

    static Here here(String name, int x, int z) {
        return new Here(name, "world", x, 64, z);
    }

    static Recalled byKey(List<Recalled> all, String key) {
        return all.stream().filter(r -> r.key().equals(key)).findFirst().orElse(null);
    }

    @Test
    void onlyItsOwnTimeIsQuiet() {
        assertTrue(Reminiscence.quiet("a quiet moment; nothing demands you"));
        assertTrue(Reminiscence.quiet("your regular watch over the world"));
        assertFalse(Reminiscence.quiet("Steve prays to you"));
        assertFalse(Reminiscence.quiet(null));
    }

    @Test
    void anOldDeedOfSomeoneHereComesBack() {
        Memory m = new Memory();
        m.addDeed("Steve", "first entered your world", T - 20 * DAY - 5 * HOUR);
        m.addDeed("Steve", "built a shrine of gold near spawn", T - 12 * DAY - 5 * HOUR);
        m.addDeed("Steve", "earned [Stone Age]", T - 10 * MIN);
        List<Recalled> all = Reminiscence.candidates(m, List.of(here("Steve", 0, 0)), T);
        assertEquals(2, all.size(), all.toString());
        Recalled first = all.get(0), shrine = all.get(1);
        assertEquals("The first thing you remember of Steve, who is here now: Steve first entered your world (20d ago).",
                first.line());
        assertEquals("Steve is here now. You remember: Steve built a shrine of gold near spawn (12d ago).",
                shrine.line());
        assertTrue(first.weight() > shrine.weight());
        // The newest deed is on their line already, and nobody absent is dug up without a reason.
        assertTrue(Reminiscence.candidates(m, List.of(here("Alex", 0, 0)), T).isEmpty());
    }

    @Test
    void thisHourSomeDaysAgoComesBackEvenForTheAbsent() {
        Memory m = new Memory();
        m.addDeed("Alex", "burned the chapel near spawn.", T - 7 * DAY + 20 * MIN);
        m.addDeed("Alex", "died: fell from a high place", T - 5 * DAY);
        List<Recalled> all = Reminiscence.candidates(m, List.of(), T);
        assertEquals(1, all.size(), all.toString());
        assertEquals("A week ago at this hour: Alex burned the chapel near spawn.", all.get(0).line());
        assertEquals(5, all.get(0).weight());
    }

    @Test
    void aMortalByItsWorkAndAFavouriteLongGone() {
        Memory m = new Memory();
        m.addWork("Temple of the Violet Hour", "temple", "world", 10, 64, 20, 1800, "15x17x21");
        m.works.get(0).builtAt = T - 12 * DAY;
        m.adjustFavor("Ashwyn", 7, "faithful");
        m.addDeed("Ashwyn", "offered 12 gold_ingot at your vessel", T - 9 * DAY);
        m.subjectIfAny("Ashwyn").lastSeen = T - 9 * DAY;
        m.adjustFavor("Nobody", 1, "passed through");
        m.subjectIfAny("Nobody").lastSeen = T - 40 * DAY;

        List<Recalled> all = Reminiscence.candidates(m, List.of(here("Steve", 20, 30), here("Kaya", 500, 500)), T);
        Recalled work = byKey(all, "work:" + m.works.get(0).id + ":Steve");
        assertEquals("Steve stands by your Temple of the Violet Hour, which you raised 12d ago.", work.line());
        Recalled gone = byKey(all, "Ashwyn@gone");
        assertEquals("You have not seen Ashwyn (favor +7) for 9 days. The last you knew: Ashwyn offered 12 gold_ingot "
                + "at your vessel.", gone.line());
        assertEquals(2, all.size(), "Kaya is far from the temple and Nobody never mattered: " + all);
    }

    @Test
    void recordsOfItsOwnActsAreNotMemories() {
        Memory m = new Memory();
        m.addDeed("Steve", "you: heal — Healed Steve to full health", T - 3 * DAY);
        m.addDeed("Steve", "was given your quest q1 \"Slay 10 zombies\"", T - 2 * DAY);
        m.addDeed("Steve", "earned [Stone Age]", T - DAY - 2 * HOUR);
        m.addDeed("Steve", "tamed a wolf", T - 5 * MIN);
        List<Recalled> all = Reminiscence.candidates(m, List.of(here("Steve", 0, 0)), T);
        assertEquals(1, all.size(), all.toString());
        assertTrue(all.get(0).line().contains("earned [Stone Age]"), all.get(0).line());
    }

    @Test
    void theBestFitIsPickedAndNotRepeated() {
        List<Recalled> all = List.of(new Recalled("a", "old deed A", 2), new Recalled("b", "old deed B", 2),
                new Recalled("w", "by a work", 4));
        assertEquals("w", Reminiscence.pick(all, Set.of(), 0).key());
        // Among equals the moment chooses, and the same moment chooses the same.
        assertEquals(Reminiscence.pick(all, Set.of("w"), 7).key(), Reminiscence.pick(all, Set.of("w"), 7).key());
        assertNotEquals(Reminiscence.pick(all, Set.of("w"), 0).key(), Reminiscence.pick(all, Set.of("w"), 1).key());
        assertEquals("b", Reminiscence.pick(all, Set.of("w", "a"), 0).key());
        assertNull(Reminiscence.pick(all, Set.of("w", "a", "b"), 0));
    }

    @Test
    void oneSurfacesThenItWaitsAndForgetsOnlyTheOldest() {
        Reminiscence.Recent recent = new Reminiscence.Recent();
        assertTrue(recent.due(T, 10 * MIN));
        recent.surfaced(new Recalled("k0", "line", 2), T);
        assertFalse(recent.due(T + 9 * MIN, 10 * MIN));
        assertTrue(recent.due(T + 10 * MIN, 10 * MIN));
        for (int i = 1; i <= 12; i++) recent.surfaced(new Recalled("k" + i, "line", 2), T + i * HOUR);
        assertFalse(recent.keys.contains("k0"), "after twelve others it may come back");
        assertTrue(recent.keys.contains("k1") && recent.keys.contains("k12"));
    }

    @Test
    void aLongMemoryIsCutToItsCap() {
        Memory m = new Memory();
        m.addDeed("Steve", "prayed: \"" + "please ".repeat(20) + "\"", T - 2 * DAY);
        m.addDeed("Steve", "prayed: \"" + "o great one ".repeat(13) + "\"", T - DAY - 3 * HOUR);
        m.addDeed("Steve", "tamed a wolf", T - MIN);
        for (Recalled r : Reminiscence.candidates(m, List.of(here("Steve", 0, 0)), T)) {
            assertTrue(r.line().length() <= Reminiscence.CAP, r.line().length() + ": " + r.line());
        }
    }
}
