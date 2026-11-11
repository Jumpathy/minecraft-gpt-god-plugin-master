package net.bigyous.gptgodmc.god.living;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.Heart;

class WorldMoodTest {
    @Test
    void calmOrFaintFeelingsDoNothing() {
        assertTrue(WorldMood.of(null, 0, 1, false).calm());
        assertTrue(WorldMood.of(Heart.Feeling.WRATH, 20, 1, true).calm());
        assertTrue(WorldMood.of(Heart.Feeling.WRATH, 90, 0, true).calm(), "strength off");
        assertEquals("", WorldMood.Effects.CALM.describe());
    }

    @Test
    void wrathStormsAndEmboldensOnlyAtNight() {
        WorldMood.Effects stirring = WorldMood.of(Heart.Feeling.WRATH, 40, 1, true);
        assertEquals(WorldMood.Weather.RAIN, stirring.weather());
        assertFalse(stirring.boldMobs());
        assertFalse(stirring.darkness());
        WorldMood.Effects strong = WorldMood.of(Heart.Feeling.WRATH, 70, 1, true);
        assertEquals(WorldMood.Weather.STORM, strong.weather());
        assertTrue(strong.boldMobs());
        assertFalse(WorldMood.of(Heart.Feeling.WRATH, 70, 1, false).boldMobs(), "day");
        assertTrue(WorldMood.of(Heart.Feeling.WRATH, 90, 1, false).darkness(), "consuming wrath darkens the sky");
        assertTrue(strong.describe().startsWith("Your wrath touches the world"));
    }

    @Test
    void joyClearsAndGrows() {
        WorldMood.Effects j = WorldMood.of(Heart.Feeling.JOY, 70, 1, false);
        assertEquals(WorldMood.Weather.CLEAR, j.weather());
        assertTrue(j.cropTicks() > 0);
        assertTrue(j.timeDrift() < 0, "the day lingers");
        assertTrue(j.describe().contains("crops grow quickly"));
    }

    @Test
    void strengthScalesButStaysCapped() {
        WorldMood.Effects subtle = WorldMood.of(Heart.Feeling.DREAD, 80, WorldMood.strength("subtle"), false);
        WorldMood.Effects strong = WorldMood.of(Heart.Feeling.DREAD, 80, WorldMood.strength("strong"), false);
        assertTrue(strong.timeDrift() > subtle.timeDrift());
        assertEquals(WorldMood.MAX_DRIFT, WorldMood.drift(10_000, 1));
        assertEquals(-WorldMood.MAX_DRIFT / 6, WorldMood.drift(-10_000, 1 / 6.0));
        assertEquals(0, WorldMood.strength("off"));
    }

    @Test
    void weatherIsRateLimited() {
        assertFalse(WorldMood.weatherDue(1_000_000, 1_000_000 + 60_000));
        assertTrue(WorldMood.weatherDue(1_000_000, 1_000_000 + WorldMood.WEATHER_GAP_MS));
    }
}
