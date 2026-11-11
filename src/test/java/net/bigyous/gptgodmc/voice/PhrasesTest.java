package net.bigyous.gptgodmc.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

/** The god's lines are spoken in parts so the first words are heard quickly. */
class PhrasesTest {
    static final String LONG = "You ask much of me, small one. Bring me an offering of gold and I shall consider "
            + "what the sea may yield to your hands, though my patience is thin. The tide does not wait for the idle! "
            + "Go now.";

    @Test
    void shortLinesStayWhole() {
        assertEquals(List.of("Rise."), Phrases.split("Rise."));
        assertEquals(List.of("No. I will not give you that."), Phrases.split(" No. I will  not give you that. "));
        assertEquals(List.of(), Phrases.split("  "));
    }

    @Test
    void nothingIsLostOrReordered() {
        List<String> parts = Phrases.split(LONG);
        assertTrue(parts.size() >= 3, parts.toString());
        assertEquals(LONG, String.join(" ", parts));
    }

    @Test
    void theFirstPartIsShort() {
        assertEquals("You ask much of me, small one.", Phrases.split(LONG).get(0));
        String oneSentence = "Bring me an offering of gold and silver, mortal, and I shall consider what the sea may "
                + "yield to your hands before the sun has set.";
        List<String> parts = Phrases.split(oneSentence);
        assertTrue(parts.get(0).length() <= Phrases.FIRST_MAX, parts.toString());
        assertTrue(parts.get(0).endsWith(","), parts.toString());
        assertEquals(oneSentence, String.join(" ", parts));
    }

    @Test
    void aOneWordOpeningJoinsTheNextSentence() {
        List<String> parts = Phrases.split("No. You have asked three times today and given nothing. Earn it first, then ask.");
        assertEquals("No. You have asked three times today and given nothing.", parts.get(0));
    }

    @Test
    void laterPartsGatherSentences() {
        for (String p : Phrases.split(LONG + " " + LONG)) {
            assertTrue(p.length() <= Phrases.REST_MAX + 40, p);
        }
    }
    @Test
    void theOpeningPhraseIsKnownBeforeTheLineIsFinished() {
        String start = "{\"player\":\"Ash\",\"message\":\"Rise, mortal, and hear me now. I have watched you toil in the dark for";
        Phrases.Partial p = Phrases.partialField(start, "message");
        assertFalse(p.closed());
        assertEquals("Rise, mortal, and hear me now.", Phrases.opening(p));
        // Too little yet to be sure where the first phrase ends.
        assertNull(Phrases.opening(Phrases.partialField("{\"message\":\"Rise, mortal, and", "message")));
        assertNull(Phrases.partialField("{\"player\":\"As", "message"));
        // A finished short line is spoken whole; escapes are read.
        Phrases.Partial done = Phrases.partialField("{\"message\":\"Don\\u0027t \\\"ask\\\".\",\"tone\":\"dem", "message");
        assertTrue(done.closed());
        assertEquals("Don't \"ask\".", Phrases.opening(done));
        assertFalse(Phrases.partialField("{\"message\":\"x\",\"tone\":\"dem", "tone").closed());
        // The head start matches what is spoken once the line is complete.
        String full = "Rise, mortal, and hear me now. I have watched you toil in the dark for many nights.";
        assertEquals(Phrases.split(full).get(0), Phrases.opening(p));
    }
}
