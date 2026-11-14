package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A mortal reads which god's power touched them. */
class SignsTest {
    @Test
    void theOneItWasDoneToAndThoseNearbyReadDifferentLines() {
        assertEquals("✦ Thyvoru healed you ✦", Signs.line("Thyvoru", "heal", true, false));
        assertNull(Signs.line("Thyvoru", "heal", false, true), "a heal is between the god and that mortal");
        assertEquals("✦ MusicMaker raised a ward here ✦", Signs.line("MusicMaker", "ward", false, true));
        assertEquals("✦ Thyvoru struck at you ✦", Signs.line("Thyvoru", "smite", true, false));
        assertEquals("✦ Thyvoru called down lightning ✦", Signs.line("Thyvoru", "smite", false, true));
        assertEquals("✦ A god changed the weather ✦", Signs.line("", "set_weather", false, false));
    }

    @Test
    void thoughtsNotesAndWordsHaveNoSign() {
        for (String quiet : new String[] { "remember", "look", "whisper", "vessel_say", "muse", "map" }) {
            assertFalse(Signs.has(quiet), quiet);
            assertNull(Signs.line("Thyvoru", quiet, true, true), quiet);
        }
        assertTrue(Signs.has("explode") && Signs.has("heal") && Signs.has("set_time"));
    }

    @Test
    void aJudgedMortalReadsWhichWayTheirFavorMovedAndNeverWhy() {
        // In a live test nine judgments moved favor where no mortal could see it.
        assertTrue(Signs.has("judge"));
        assertEquals("✦ Vhoraxis looks on you with favor ✦", Signs.favorLine("Vhoraxis", 2));
        assertEquals("✦ Vhoraxis is displeased with you ✦", Signs.favorLine("Vhoraxis", -3));
        assertNull(Signs.favorLine("Vhoraxis", 0));
        assertNull(Signs.line("Vhoraxis", "judge", true, true), "its line depends on the change, not on the power alone");
    }
}
