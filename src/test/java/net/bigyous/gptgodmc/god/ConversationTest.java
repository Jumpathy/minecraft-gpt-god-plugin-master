package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The conversation timeline the god reads: one ordered list, its own words included. */
class ConversationTest {
    static final long T = 1_700_000_000_000L;

    @Test
    void theNapExchangeReadsInOrder() {
        // What happened on the test server: the god spoke, Jumpathy reacted, then Tono prayed "hi".
        Conversation c = new Conversation();
        c.spoke(T, "You said aloud through your vessel: \"Sorry, was napping. Pillagers are gone now.\"");
        c.render(T + 1000, true); // the god's look ends here
        c.heard(T + 6000, "TonoCatMeow said in chat: \"oo\"");
        c.heard(T + 12000, "TonoCatMeow said in chat: \"it said something in chat\"");
        c.heard(T + 13000, "Jumpathy said in chat: \"was napping is cdrazy\"");
        c.heard(T + 17000, "TonoCatMeow prays to you: \"hi\"");
        String shown = c.render(T + 18000, true);
        List<String> lines = shown.lines().toList();
        assertEquals(5, lines.size(), shown);
        assertEquals("[18s ago] You said aloud through your vessel: \"Sorry, was napping. Pillagers are gone now.\"",
                lines.get(0));
        assertEquals("[5s ago] NEW Jumpathy said in chat: \"was napping is cdrazy\"", lines.get(3));
        assertEquals("[1s ago] NEW TonoCatMeow prays to you: \"hi\"", lines.get(4));
    }

    @Test
    void earlierTalkStaysButIsNoLongerNew() {
        Conversation c = new Conversation();
        c.heard(T, "Kaya said in chat: \"hello\"");
        assertTrue(c.render(T + 1000, true).contains("NEW Kaya"));
        String second = c.render(T + 60_000, true);
        assertTrue(second.contains("Kaya said in chat"), second);
        assertFalse(second.contains("NEW"), second);
    }

    @Test
    void aPreviewDoesNotMarkAnythingSeen() {
        Conversation c = new Conversation();
        c.heard(T, "Kaya said in chat: \"hello\"");
        c.render(T + 1000, false);
        assertTrue(c.render(T + 2000, true).contains("NEW Kaya"));
    }

    @Test
    void oldTalkFallsOutOfTheWindow() {
        Conversation c = new Conversation();
        c.heard(T, "Kaya said in chat: \"long ago\"");
        c.heard(T + Conversation.WINDOW_MS, "Bram said in chat: \"just now\"");
        String shown = c.render(T + Conversation.WINDOW_MS + 1000, true);
        assertFalse(shown.contains("long ago"), shown);
        assertTrue(shown.contains("just now"), shown);
        assertEquals("", new Conversation().render(T, true));
    }

    @Test
    void theNewestLinesWinWhenItIsLong() {
        Conversation c = new Conversation();
        for (int i = 0; i < 80; i++) c.heard(T + i * 1000L, "P" + i + " said in chat: \"line " + i + "\"");
        String shown = c.render(T + 81_000, true);
        assertEquals(Conversation.MAX_LINES, shown.lines().count());
        assertTrue(shown.contains("line 79") && !shown.contains("line 10\""), shown);
        assertTrue(shown.length() <= Conversation.MAX_CHARS + 200);
    }
}
