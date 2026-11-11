package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Mortals swear, the god witnesses and judges, the server watches what it can and never punishes alone. */
class OathsTest {
    static final long T = 1_700_000_000_000L;
    static final long MIN = 60_000L;

    static WorldDeed deed(String who, String act, String kind, Set<String> tags, int x, int z, long at) {
        return new WorldDeed(who, act, kind, tags, "world", x, 64, z, at);
    }

    @Test
    void anOathWaitsForTheGodThenLapsesUnheard() {
        Memory m = new Memory();
        Oaths.Oath o = Oaths.swear(m, "Tyler", "I will never harm a villager again", 3, T);
        assertEquals("o1", o.id);
        assertTrue(Oaths.render(m, T + MIN).contains("WAITING FOR YOUR ANSWER"));
        assertThrows(Oaths.Refusal.class, () -> Oaths.swear(m, "Tyler", "another oath", 3, T),
                "one oath waits at a time");
        assertTrue(Oaths.tick(m, T + 5 * MIN, 10 * MIN).isEmpty());
        List<Oaths.Notice> n = Oaths.tick(m, T + 11 * MIN, 10 * MIN);
        assertEquals(Oaths.NoticeKind.UNHEARD, n.get(0).kind());
        assertEquals("unheard", o.status);
    }

    @Test
    void aWatchedNeverIsBrokenByTheDeedAndToldOnce() {
        Memory m = new Memory();
        Oaths.swear(m, "Tyler", "I will never harm a villager again", 3, T);
        Oaths.Clause c = Oaths.clause("never", "kill_mob", "villagers", 0, null, 0, 0, 0, 0);
        Oaths.witness(m, "o1", "Never again shall you slay a villager", 0, c, T);
        assertTrue(Oaths.observe(m, deed("Tyler", "kill_mob", "zombie", Set.of("monster"), 0, 0, T + MIN), T + MIN).isEmpty());
        assertTrue(Oaths.observe(m, deed("Ash", "kill_mob", "villager", Set.of("villager"), 0, 0, T + MIN), T + MIN)
                .isEmpty(), "only the swearer is bound");
        List<Oaths.Notice> n = Oaths.observe(m, deed("Tyler", "kill_mob", "villager", Set.of("villager"), 4, 9, T + 2 * MIN),
                T + 2 * MIN);
        assertEquals(Oaths.NoticeKind.BREACH, n.get(0).kind());
        assertTrue(n.get(0).text().contains("BROKE the oath o1") && n.get(0).text().contains("killed a villager"));
        assertTrue(Oaths.observe(m, deed("Tyler", "kill_mob", "villager", Set.of("villager"), 4, 9, T + 3 * MIN),
                T + 3 * MIN).isEmpty(), "a second breach soon after is counted, not retold");
        assertEquals(2, Oaths.find(m, "o1").breaches);
        assertTrue(Oaths.render(m, T + 4 * MIN).contains("BROKEN 2x"));
        assertEquals("sworn", Oaths.find(m, "o1").status, "the server never judges; the god does");
    }

    @Test
    void aMustIsFulfilledByCountingDeedsNearAPlace() {
        Memory m = new Memory();
        Oaths.swear(m, "Ash", "I will replant the grove I cut", 3, T);
        Oaths.Clause c = Oaths.clause("must", "plant", "sapling", 3, "world", 100, 64, 100, 16);
        Oaths.witness(m, "o1", "Plant three saplings in the grove", 60, c, T);
        Set<String> sap = Set.of("sapling");
        assertTrue(Oaths.observe(m, deed("Ash", "plant", "oak_sapling", sap, 500, 500, T + MIN), T + MIN).isEmpty(),
                "far from the grove it does not count");
        Oaths.observe(m, deed("Ash", "plant", "oak_sapling", sap, 101, 99, T + MIN), T + MIN);
        Oaths.observe(m, deed("Ash", "plant", "birch_sapling", sap, 103, 99, T + MIN), T + MIN);
        List<Oaths.Notice> n = Oaths.observe(m, deed("Ash", "plant", "oak_sapling", sap, 105, 99, T + MIN), T + MIN);
        assertEquals(Oaths.NoticeKind.FULFILLED, n.get(0).kind());
        assertTrue(Oaths.describe(c).contains("(3/3)"), Oaths.describe(c));
    }

    @Test
    void targetsMatchKindsNamesAndBlockParts() {
        Oaths.Clause logs = Oaths.clause("never", "break_blocks", "log", 0, null, 0, 0, 0, 0);
        assertTrue(Oaths.matches(logs, deed("A", "break_blocks", "oak_log", Set.of("log"), 0, 0, T)));
        assertTrue(!Oaths.matches(logs, deed("A", "break_blocks", "stone", Set.of(), 0, 0, T)));
        Oaths.Clause diamonds = Oaths.clause("never", "break_blocks", "diamond", 0, null, 0, 0, 0, 0);
        assertTrue(Oaths.matches(diamonds, deed("A", "break_blocks", "deepslate_diamond_ore", Set.of("ore"), 0, 0, T)));
        Oaths.Clause ash = Oaths.clause("never", "kill_player", "Ash", 0, null, 0, 0, 0, 0);
        assertTrue(Oaths.matches(ash, deed("Tyler", "kill_player", "Ash", Set.of("player"), 0, 0, T)));
        assertTrue(!Oaths.matches(ash, deed("Tyler", "kill_player", "Bo", Set.of("player"), 0, 0, T)));
        Oaths.Clause any = Oaths.clause("never", "kill_player", "any", 0, null, 0, 0, 0, 0);
        assertNull(any.target);
        assertThrows(Oaths.Refusal.class, () -> Oaths.clause("never", "hug", null, 0, null, 0, 0, 0, 0));
        assertThrows(Oaths.Refusal.class, () -> Oaths.clause("never", "enter", null, 0, null, 0, 0, 0, 0));
    }

    @Test
    void judgingIsRememberedForEver() {
        Memory m = new Memory();
        Oaths.swear(m, "Tyler", "I will guard the village", 3, T);
        Oaths.witness(m, "o1", "Guard the village", 0, null, T);
        Oaths.resolve(m, "o1", "broken", "he fled the raid", T + MIN);
        Oaths.swear(m, "Tyler", "I will do better", 3, T + 2 * MIN);
        Oaths.witness(m, "o2", "Do better", 0, null, T + 2 * MIN);
        Oaths.resolve(m, "o2", "kept", null, T + 3 * MIN);
        Memory.Subject s = m.subjectIfAny("Tyler");
        assertEquals(1, s.oathsKept);
        assertEquals(1, s.oathsBroken);
        assertTrue(Oaths.dossierLine(m, s).contains("FORSWORN"));
        assertTrue(m.renderDossier("Tyler", true, false, T + 4 * MIN).contains("oaths: kept 1, broken 1"));
        assertThrows(Oaths.Refusal.class, () -> Oaths.resolve(m, "o2", "kept", null, T), "already judged");
    }

    @Test
    void anEndedTermIsPutToTheGodThenClosedByTime() {
        Memory m = new Memory();
        Oaths.swear(m, "Ash", "No more slaughter", 3, T);
        Oaths.witness(m, "o1", "Slay no animal for an hour", 60,
                Oaths.clause("never", "kill_mob", "animal", 0, null, 0, 0, 0, 0), T);
        assertTrue(Oaths.tick(m, T + 30 * MIN, 10 * MIN).isEmpty());
        List<Oaths.Notice> due = Oaths.tick(m, T + 61 * MIN, 10 * MIN);
        assertEquals(Oaths.NoticeKind.TERM_ENDED, due.get(0).kind());
        assertTrue(due.get(0).text().contains("they never broke it"), due.get(0).text());
        assertTrue(Oaths.tick(m, T + 62 * MIN, 10 * MIN).isEmpty(), "told once");
        List<Oaths.Notice> closed = Oaths.tick(m, T + 60 * MIN + 25 * 60 * MIN, 10 * MIN);
        assertEquals(Oaths.NoticeKind.CLOSED_BY_TIME, closed.get(0).kind());
        assertEquals("kept", Oaths.find(m, "o1").status);
        assertEquals(1, m.subjectIfAny("Ash").oathsKept);
    }

    @Test
    void aMortalHoldsOnlySoManyOaths() {
        Memory m = new Memory();
        for (int i = 1; i <= 2; i++) {
            Oaths.swear(m, "Bo", "oath number " + i, 2, T);
            Oaths.witness(m, "o" + i, null, 0, null, T);
        }
        assertThrows(Oaths.Refusal.class, () -> Oaths.swear(m, "Bo", "a third oath", 2, T));
        assertTrue(Oaths.listFor(m, "Bo", T).contains("oath number 2"));
    }
}
