package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The god told a mortal to eat, then healed them: its own words come back to it with the act. */
class CoherenceTest {
    static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void anActOnSomeoneItSpokeToQuotesItsWordsOnce() {
        Coherence c = new Coherence();
        assertNull(c.after("vessel_say", json("{\"message\":\"Eat something, you're at 8 hearts. Chill.\"}"),
                "Heard by: SplitBadger1038, Jumpathy, TonoCatMeow", false));
        String note = c.after("heal", json("{\"player\":\"Jumpathy\"}"), "Healed: Jumpathy", false);
        assertTrue(note != null && note.contains("you told Jumpathy: \"Eat something, you're at 8 hearts. Chill.\""),
                note);
        assertTrue(note.contains("own the change"), note);
        assertNull(c.after("give_item", json("{\"player\":\"jumpathy\",\"item\":\"bread\"}"), "Gave", false),
                "once is enough until it speaks to them again");
        c.after("whisper", json("{\"player\":\"Jumpathy\",\"message\":\"Fine, have some bread.\"}"),
                "Jumpathy heard your whisper.", false);
        String again = c.after("give_item", json("{\"player\":\"Jumpathy\",\"item\":\"bread\"}"), "Gave", false);
        assertTrue(again != null && again.contains("Fine, have some bread."), again);
    }

    @Test
    void nothingIsAddedWhenItSpokeToNoOneOrActsOnAnother() {
        Coherence c = new Coherence();
        assertNull(c.after("heal", json("{\"player\":\"Jumpathy\"}"), "Healed: Jumpathy", false));
        c.after("whisper", json("{\"player\":\"TonoCatMeow\",\"message\":\"Plant it.\"}"), "heard", false);
        assertNull(c.after("heal", json("{\"player\":\"Jumpathy\"}"), "Healed: Jumpathy", false));
        assertNull(c.after("vessel_say", json("{\"message\":\"Hello\"}"), "No mortal was close enough to hear it.",
                false));
        assertNull(c.after("heal", json("{\"player\":\"TonoCatMeow\"}"), "no such mortal", true),
                "a power that failed did nothing to check");
        String lists = c.after("apply_effect", json("{\"players\":[\"Ash\",\"TonoCatMeow\"],\"effect\":\"speed\"}"),
                "ok", false);
        assertTrue(lists != null && lists.contains("TonoCatMeow"), lists);
    }
}
