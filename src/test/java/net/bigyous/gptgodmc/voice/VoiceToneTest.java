package net.bigyous.gptgodmc.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class VoiceToneTest {
    private static final Map<String, String> MOODS = VoiceTone.DEFAULT_MOODS;

    @Test
    void wrathTurnsTheVoiceDemonic() {
        assertEquals("demonic", VoiceTone.choose(null, "auto", "wrathful", MOODS, "divine"));
    }

    @Test
    void calmStaysEthereal() {
        assertEquals("ethereal", VoiceTone.choose(null, "auto", "calm", MOODS, "divine"));
        assertEquals("ethereal", VoiceTone.choose(null, "auto", null, MOODS, null));
        assertEquals("commanding", VoiceTone.choose(null, "auto", "ominous", MOODS, null));
    }

    @Test
    void aToneTheGodChoseWinsForThatLine() {
        assertEquals("commanding", VoiceTone.choose("Commanding", "auto", "calm", MOODS, null));
        assertEquals("demonic", VoiceTone.choose("demonic", "ethereal", "joyful", MOODS, null));
    }

    @Test
    void aFixedEffectInConfigBeatsTheMood() {
        assertEquals("booming", VoiceTone.choose(null, "booming", "wrathful", MOODS, null));
    }

    @Test
    void unknownNamesAreIgnored() {
        assertEquals("demonic", VoiceTone.choose("shouty", "weird", "wrathful", MOODS, null));
        Map<String, String> partial = new HashMap<>(MOODS);
        partial.remove("joyful");
        partial.put("sorrowful", "nonsense");
        assertEquals("divine", VoiceTone.choose(null, "auto", "joyful", partial, "divine"));
        assertEquals("ethereal", VoiceTone.choose(null, "auto", "sorrowful", partial, "bogus"));
    }

    @Test
    void everyMoodHasAValidDefault() {
        for (String effect : MOODS.values()) assertTrue(VoiceTone.isEffect(effect), effect);
        for (String mood : new String[] { "calm", "wrathful", "joyful", "sorrowful", "ominous", "exalted" }) {
            assertTrue(MOODS.containsKey(mood), mood);
        }
    }
}
