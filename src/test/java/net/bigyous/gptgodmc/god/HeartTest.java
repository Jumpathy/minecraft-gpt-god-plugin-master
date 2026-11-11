package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

import net.bigyous.gptgodmc.god.Heart.Feeling;
import net.bigyous.gptgodmc.god.vessel.VesselMood;

/** The god's feelings build from what mortals do, fade by themselves, and survive a restart. */
class HeartTest {
    static final long T = 1_700_000_000_000L;
    static final long MIN = 60_000L;

    @Test
    void anInsultSmouldersThenFades() {
        Heart h = new Heart();
        h.moved("prayer", "Korvath prays to you: \"you are a fake god\"", true, "Korvath", -2, "you are a fake god", T);
        h.moved("speech", "Korvath said in chat: \"god is so stupid\"", true, "Korvath", -2, "god is so stupid", T + 1000);
        assertTrue(h.level(Feeling.WRATH) >= Heart.SHOWS, "two insults anger it: " + h.level(Feeling.WRATH));
        assertEquals(VesselMood.WRATHFUL, h.mood(T + 2000));
        assertTrue(h.describe(T + 5 * MIN).contains("Korvath insulted you"), h.describe(T + 5 * MIN));
        // Wrath halves every 20 minutes: after an hour it no longer shows.
        assertEquals(VesselMood.CALM, h.mood(T + 60 * MIN));
        assertTrue(h.level(Feeling.WRATH) > 0 && h.level(Feeling.WRATH) < 10);
    }

    @Test
    void giftsAndPraiseWarmItAndCoolWrath() {
        Heart h = new Heart();
        h.feel(Feeling.WRATH, 50, "someone struck you", T);
        for (int i = 0; i < 4; i++) {
            h.moved("prayer", "Ashwyn OFFERING: 12 gold_ingot", true, "Ashwyn", 8, null, T + i * 1000);
        }
        h.moved("prayer", "Ashwyn prays to you: \"praise be, thank you great one\"", true, "Ashwyn", 8,
                "praise be, thank you great one", T + 5000);
        assertTrue(h.level(Feeling.JOY) > 60, "joy " + h.level(Feeling.JOY));
        assertTrue(h.level(Feeling.WRATH) < 25, "joy cools wrath: " + h.level(Feeling.WRATH));
        assertTrue(h.level(Feeling.GLORY) > 0, "worship stirs glory");
        assertEquals(VesselMood.JOYFUL, h.mood(T + 6000));
    }

    @Test
    void plainChatNotAimedAtItLeavesItUnmoved() {
        Heart h = new Heart();
        h.moved("speech", "Steve said in chat: \"this is stupid\"", false, "Steve", 0, "this is stupid", T);
        assertEquals(0, h.level(Feeling.WRATH));
        assertNull(h.dominant(T));
        assertEquals("At peace: nothing stirs you.", h.describe(T));
    }

    @Test
    void whatItFeelsAboutTheWorldIsItsOwnChoice() {
        Heart h = new Heart();
        // Faint is its own: named in its heart with the reason, too weak to show in its body.
        assertEquals(15, h.choose(Feeling.JOY, "faint", "the sky cleared", T), 1e-9);
        assertNull(h.dominant(T));
        assertTrue(h.describe(T).contains("faint joy") && h.describe(T).contains("the sky cleared"), h.describe(T));
        assertEquals(List.of("joy ← the sky cleared (0s ago)"), h.whys(T, 2));
        // Stirring shows; strong grips; a weaker choice never lowers what it already feels.
        assertEquals(40, h.choose(Feeling.JOY, "stirring", "the storm passed", T), 1e-9);
        assertEquals(VesselMood.JOYFUL, h.mood(T));
        assertEquals(70, h.choose(Feeling.JOY, "strong", "elated", T), 1e-9);
        assertEquals(70, h.choose(Feeling.JOY, "faint", "still glad", T), 1e-9);
        // It may let a feeling go, and choosing nothing at all changes nothing.
        assertEquals(0, h.choose(Feeling.JOY, "gone", "bored of it", T), 1e-9);
        assertEquals(VesselMood.CALM, h.mood(T));
        assertEquals(List.of("faint", "stirring", "strong", "gone"), Heart.STRENGTHS);
    }

    @Test
    void theDeathOfAFavouriteGrievesIt() {
        Heart h = new Heart();
        h.moved("event", "DEATH: Ashwyn was slain by Korvath", false, "Ashwyn", 8, null, T);
        assertTrue(h.level(Feeling.SORROW) >= Heart.SHOWS, "sorrow " + h.level(Feeling.SORROW));
        assertTrue(h.level(Feeling.DREAD) > 0);
        Heart other = new Heart();
        other.moved("event", "DEATH: Pip fell from a high place", false, "Pip", 0, null, T);
        assertTrue(other.level(Feeling.SORROW) < Heart.SHOWS, "a stranger's death only touches it");
    }

    @Test
    void choosingAMoodFeedsItAndCalmSettlesIt() {
        Heart h = new Heart();
        h.acted("vessel_mood", "wrathful", T);
        assertEquals(60, h.level(Feeling.WRATH), 0.01);
        h.acted("vessel_mood", "calm", T);
        assertEquals(30, h.level(Feeling.WRATH), 0.01);
        h.acted("smite", "wrathful", T); // only vessel_mood speaks for its feelings
        assertEquals(30, h.level(Feeling.WRATH), 0.01);
        h.clear(T);
        assertNull(h.dominant(T));
    }

    @Test
    void prideAndVanityScaleWhatMovesIt() {
        Heart humble = new Heart(), proud = new Heart();
        humble.moved("prayer", "K prays to you: \"you are weak\"", true, "K", 0, "you are weak", T, 0, 5);
        proud.moved("prayer", "K prays to you: \"you are weak\"", true, "K", 0, "you are weak", T, 10, 5);
        assertEquals(9, humble.level(Feeling.WRATH), 0.01);
        assertEquals(27, proud.level(Feeling.WRATH), 0.01);
        Heart vain = new Heart();
        vain.moved("prayer", "A OFFERING: 1 gold_ingot", true, "A", 0, null, T, 5, 10);
        assertEquals(15, vain.level(Feeling.JOY), 0.01);
    }

    @Test
    void itSurvivesARestart() {
        Heart h = new Heart();
        h.feel(Feeling.DREAD, 70, "blood was spilled", T);
        Heart back = new Gson().fromJson(new Gson().toJson(h), Heart.class);
        assertEquals(70, back.level(Feeling.DREAD), 0.01);
        assertEquals(VesselMood.OMINOUS, back.mood(T + MIN));
        assertTrue(back.describe(T + MIN).contains("blood was spilled"));
        // Old memory files have no heart: they get an empty one.
        Heart empty = new Gson().fromJson("{}", Heart.class);
        assertEquals(VesselMood.CALM, empty.mood(T));
    }
}
