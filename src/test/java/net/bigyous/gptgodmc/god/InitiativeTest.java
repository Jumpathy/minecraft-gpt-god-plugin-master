package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.Initiative.Context;
import net.bigyous.gptgodmc.god.Initiative.Kind;

class InitiativeTest {
    static Map<String, Integer> dials(Object... kv) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    static final Map<String, Integer> CARING = dials("generosity", 10, "mercy", 10, "meddling", 8, "chattiness", 8,
            "pride", 1, "wrath", 1);
    static final Map<String, Integer> COLD = dials("generosity", 0, "mercy", 0, "meddling", 1, "chattiness", 1,
            "pride", 10, "wrath", 9, "vanity", 10, "curiosity", 8);
    static final Context DAY = new Context(true, 6000, false, 2, List.of(), null);
    static final Context WEAK = new Context(true, 6000, false, 2, List.of("Steve (hurt)"), "Alex");

    @Test
    void careFollowsDialsAndHeart() {
        assertTrue(Initiative.care(CARING, Map.of()) > 0.35);
        assertTrue(Initiative.care(COLD, Map.of()) < -0.3);
        assertEquals("caring", Initiative.careWord(Initiative.care(CARING, Map.of())));
        assertEquals("cold", Initiative.careWord(Initiative.care(COLD, Map.of())));
        double calm = Initiative.care(dials(), Map.of());
        double furious = Initiative.care(dials(), Map.of("wrath", 100.0));
        assertTrue(furious < calm, "wrath cools its care");
    }

    @Test
    void aCaringGodWatchesOverTheWeak() {
        List<Kind> r = Initiative.rank(CARING, Map.of(), WEAK, 1);
        assertEquals(Kind.WATCH_OVER, r.get(0), r.toString());
        Map<Kind, Double> w = Initiative.weights(CARING, Map.of(), WEAK);
        assertTrue(w.get(Kind.VISIT) > w.get(Kind.FISH));
    }

    @Test
    void aColdGodGoesAboutItsOwnBusiness() {
        Map<Kind, Double> w = Initiative.weights(COLD, Map.of(), WEAK);
        assertEquals(0, w.get(Kind.WATCH_OVER), 1e-9);
        for (long seed = 0; seed < 20; seed++) {
            List<Kind> r = Initiative.rank(COLD, Map.of(), WEAK, seed);
            assertTrue(r.indexOf(Kind.WATCH_OVER) < 0 || r.indexOf(Kind.WATCH_OVER) > 2, r.toString());
            assertTrue(List.of(Kind.BUILD_HOME, Kind.WANDER, Kind.TINKER, Kind.COMPOSE, Kind.FISH).contains(r.get(0)),
                    r.toString());
        }
    }

    @Test
    void natureShapesTheWhim() {
        Map<Kind, Double> patient = Initiative.weights(dials("patience", 10, "meddling", 0, "curiosity", 2), Map.of(),
                DAY);
        assertTrue(patient.get(Kind.FISH) > patient.get(Kind.WANDER));
        Map<Kind, Double> curious = Initiative.weights(dials("curiosity", 10, "patience", 1), Map.of(), DAY);
        assertTrue(curious.get(Kind.WANDER) > curious.get(Kind.FISH));
        Map<Kind, Double> vain = Initiative.weights(dials("vanity", 10, "pride", 10), Map.of("glory", 80.0), DAY);
        assertEquals(Kind.BUILD_HOME, Initiative.rank(dials("vanity", 10, "pride", 10, "patience", 8),
                Map.of("glory", 80.0), DAY, 3).get(0));
        assertTrue(vain.get(Kind.BUILD_HOME) > vain.get(Kind.GARDEN));
    }

    @Test
    void duskCallsForTheSunsetAndLonelinessForbidsVisits() {
        Context dusk = new Context(true, 12000, false, 1, List.of(), null);
        Map<String, Integer> d = dials("patience", 8);
        assertTrue(Initiative.weights(d, Map.of(), dusk).get(Kind.SUNSET) > 3
                * Initiative.weights(d, Map.of(), DAY).get(Kind.SUNSET));
        Context alone = new Context(true, 6000, false, 0, List.of(), null);
        List<Kind> r = Initiative.rank(CARING, Map.of(), alone, 4);
        assertFalse(r.contains(Kind.VISIT));
        assertFalse(r.contains(Kind.WATCH_OVER));
    }

    @Test
    void whimsVaryWithTheSeed() {
        java.util.Set<Kind> firsts = new java.util.HashSet<>();
        for (long s = 0; s < 40; s++) firsts.add(Initiative.rank(dials(), Map.of(), DAY, s).get(0));
        assertTrue(firsts.size() >= 2, firsts.toString());
    }

    @Test
    void beginProgressEnd() {
        Memory m = new Memory();
        assertNull(Initiative.current(m));
        assertThrows(Initiative.Refusal.class, () -> Initiative.progress(m, "x", 0));
        Initiative.Activity a = Initiative.begin(m, Kind.GARDEN, "Moonflowers", "till, sow, water", null, 1000);
        assertEquals(a, Initiative.current(m));
        Initiative.progress(m, "sowed a row", 2000);
        assertEquals(1, Initiative.current(m).steps);
        Initiative.begin(m, Kind.FISH, null, null, null, 3000);
        assertEquals("set aside", m.pastActivities.get(0).status);
        assertEquals("fishing", Initiative.current(m).about);
        Initiative.end(m, "caught a salmon", 4000);
        assertNull(Initiative.current(m));
        assertEquals(2, m.pastActivities.size());
        assertThrows(Initiative.Refusal.class, () -> Initiative.begin(m, null, null, null, null, 0));
    }

    @Test
    void rendersByLevel() {
        Memory m = new Memory();
        assertEquals("", Initiative.render(m, CARING, Map.of(), WEAK, Initiative.Level.WATCH, 0));
        String idle = Initiative.render(m, CARING, Map.of(), WEAK, Initiative.Level.SEMI, 0);
        assertTrue(idle.contains("Whims that suit you now"), idle);
        assertTrue(idle.contains("caring") && idle.contains("Steve"), idle);
        String busy = Initiative.render(m, COLD, Map.of(), new Context(false, 6000, false, 2, List.of(), null),
                Initiative.Level.SEMI, 0);
        assertFalse(busy.contains("Whims"), "an event-driven thought under semi suggests nothing: " + busy);
        assertTrue(busy.contains("not your concern"), busy);
        Initiative.begin(m, Kind.BUILD_HOME, "A tower", "foundation, walls", "Alex", 0);
        String doing = Initiative.render(m, COLD, Map.of(), DAY, Initiative.Level.SEMI, 60_000);
        assertTrue(doing.contains("Carry it on") && doing.contains("A tower"), doing);
        assertEquals(Initiative.Level.FREE, Initiative.Level.parse("free"));
        assertEquals(Initiative.Level.WATCH, Initiative.Level.parse("watch"));
        assertEquals(Initiative.Level.SEMI, Initiative.Level.parse(null));
    }
}
