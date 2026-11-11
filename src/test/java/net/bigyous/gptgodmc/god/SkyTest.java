package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.Sky.View;
import net.bigyous.gptgodmc.god.Sky.Weather;

/** The god notices the moon and how long the rain has lasted, in a few words or none. */
class SkyTest {
    static final long T = 1_700_000_000_000L;
    static final long MIN = 60_000L;
    static final long NOON = 6000, NIGHT = 18000;

    @Test
    void aPlainDaySaysNothing() {
        assertNull(Sky.describe(new View(NOON, 3, Weather.CLEAR, -1, null)));
        assertNull(Sky.describe(new View(NOON, 3, Weather.CLEAR, 40 * MIN, Weather.RAIN)), "the rain is long gone");
        // Rain it has only ever seen raining: the world line already says "weather: rain".
        assertNull(Sky.describe(new View(NOON, 3, Weather.RAIN, -1, null)));
    }

    @Test
    void theMoonTurnsOnceEveryEightNights() {
        assertEquals("Sky: a full moon.", Sky.describe(new View(NIGHT, 0, Weather.CLEAR, -1, null)));
        assertEquals("Sky: a new moon, the night at its darkest.",
                Sky.describe(new View(NIGHT, 4, Weather.CLEAR, -1, null)));
        assertEquals(Sky.moon(2), Sky.moon(10));
        // Hidden behind the clouds, and not up at dusk or dawn.
        assertEquals("Sky: rain for 6 min.", Sky.describe(new View(NIGHT, 0, Weather.RAIN, 6 * MIN, Weather.CLEAR)));
        assertNull(Sky.describe(new View(12500, 0, Weather.CLEAR, -1, null)));
        assertNull(Sky.describe(new View(23500, 0, Weather.CLEAR, -1, null)));
    }

    @Test
    void rainAndStormsAreTimedAndTheirEndIsNoticed() {
        assertEquals("Sky: a thunderstorm for 1 h 12 min.",
                Sky.describe(new View(NOON, 1, Weather.THUNDER, 72 * MIN, Weather.RAIN)));
        assertEquals("Sky: rain for under a minute.",
                Sky.describe(new View(NOON, 1, Weather.RAIN, 20_000, Weather.CLEAR)));
        assertEquals("Sky: a waxing crescent moon; the storm passed 4 min ago.",
                Sky.describe(new View(NIGHT, 5, Weather.CLEAR, 4 * MIN, Weather.THUNDER)));
        assertEquals("Sky: the rain stopped 9 min ago.",
                Sky.describe(new View(NOON, 5, Weather.CLEAR, 9 * MIN, Weather.RAIN)));
    }

    @Test
    void theLogDatesEachTurnOfTheWeather() {
        Sky.Log log = new Sky.Log();
        assertEquals(-1, log.view("w", NOON, 1, T).sinceMs(), "never seen");
        log.note("w", Weather.CLEAR, T);
        assertEquals(-1, log.view("w", NOON, 1, T + MIN).sinceMs(), "clear since before it looked");
        log.note("w", Weather.RAIN, T + 10 * MIN);
        log.note("w", Weather.RAIN, T + 12 * MIN); // the same weather seen again changes nothing
        assertEquals(new View(NOON, 1, Weather.RAIN, 5 * MIN, Weather.CLEAR), log.view("w", NOON, 1, T + 15 * MIN));
        log.note("w", Weather.THUNDER, T + 20 * MIN);
        assertEquals(3 * MIN, log.view("w", NOON, 1, T + 23 * MIN).sinceMs(), "the thunder is timed by itself");
        // Thunder easing back to rain is the same wet spell, counted from when the sky closed.
        log.note("w", Weather.RAIN, T + 30 * MIN);
        assertEquals(25 * MIN, log.view("w", NOON, 1, T + 35 * MIN).sinceMs());
        log.note("w", Weather.CLEAR, T + 40 * MIN);
        assertEquals("Sky: the rain stopped 2 min ago.", Sky.describe(log.view("w", NOON, 1, T + 42 * MIN)));
        // Another world keeps its own sky.
        assertEquals(-1, log.view("other", NOON, 1, T + 42 * MIN).sinceMs());
    }

    @Test
    void theRulesMatchThePerceptionsWeatherWord() {
        assertEquals(Weather.CLEAR, Sky.weather(false, false));
        assertEquals(Weather.RAIN, Sky.weather(true, false));
        assertEquals(Weather.THUNDER, Sky.weather(true, true));
        assertEquals(Weather.CLEAR, Sky.weather(false, true), "thunder without rain is a clear sky");
    }

    @Test
    void theLogTellsWhatTheWeatherTurnedFrom() {
        Sky.Log log = new Sky.Log();
        assertNull(log.note("w", Weather.RAIN, T), "the first sighting is not a turn");
        assertNull(log.note("w", Weather.RAIN, T + MIN));
        assertEquals(Weather.RAIN, log.note("w", Weather.THUNDER, T + 2 * MIN));
        assertEquals(Weather.THUNDER, log.note("w", Weather.CLEAR, T + 3 * MIN));
    }

    @Test
    void theLineNeverGrowsPastItsCap() {
        long[] spans = { -1, 0, 59 * MIN, 600 * MIN + 59 * MIN, 100_000 * MIN };
        for (long time : new long[] { NOON, NIGHT }) {
            for (int day = 0; day < 8; day++) {
                for (Weather w : Weather.values()) {
                    for (Weather before : Weather.values()) {
                        for (long since : spans) {
                            String s = Sky.describe(new View(time, day, w, since, before));
                            assertTrue(s == null || s.length() <= Sky.CAP, s);
                        }
                    }
                }
            }
        }
    }
}
