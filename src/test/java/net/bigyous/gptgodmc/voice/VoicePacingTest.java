package net.bigyous.gptgodmc.voice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A slow voice server holds the first words so the line plays without gaps; a fast one starts at once. */
class VoicePacingTest {
    @Test
    void aFastServerStartsAfterItsFirstPart() {
        VoicePacing p = new VoicePacing();
        assertTrue(p.mayStart(1500, 60), "nothing known yet: start");
        p.learned(50, 400); // 8 ms a character
        assertTrue(p.mayStart(1500, 120));
    }

    @Test
    void aSlowServerWaitsUntilTheRestFits() {
        VoicePacing p = new VoicePacing();
        p.learned(50, 3100); // about as slow as the speech is long
        assertFalse(p.mayStart(1600, 60), "the second part would come late");
        assertTrue(p.mayStart(3200, 0), "everything is made");
        assertTrue(p.mayStart(3600, 50), "enough is ready to cover the rest");
        p.learned(50, 2); // a part prepared ahead of time teaches nothing
        assertFalse(p.mayStart(1600, 60));
    }
}
