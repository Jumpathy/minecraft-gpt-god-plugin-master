package net.bigyous.gptgodmc.god.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The lite profile's perception: what is dropped first, what never is; and the inline-thinking splitter. */
class LitePerceptionTest {

    private static String perception() {
        StringBuilder sb = new StringBuilder("# Perception — day 3, noon\n");
        sb.append("## BINDING COMMANDS from the server's operator (the power above you)\n#4 Build a well.\n\n");
        sb.append("## The world\n").append("Plains, clear sky. ".repeat(40)).append("\n\n");
        sb.append("## Your vessel\nFormless.\n\n");
        sb.append("## Structures mortals have built\n").append("A hut at 10 64 10.\n".repeat(120)).append("\n");
        sb.append("## Your works (things you built)\n").append("A shrine.\n".repeat(80)).append("\n");
        sb.append("## Your ledger (full dossiers on the mortals in this moment; recall(player) for anyone else)\n");
        for (int i = 0; i < 60; i++) sb.append("ledger line ").append(i).append("\n");
        sb.append("\n## What mortals did since you last looked (oldest first)\n");
        for (int i = 0; i < 80; i++) sb.append("[event ").append(i).append("] Ashwyn mined stone\n");
        sb.append("\n## Conversation (last 10 minutes, oldest first)\n");
        for (int i = 0; i < 50; i++) sb.append("Ashwyn: line ").append(i).append("\n");
        sb.append("\n## Waiting for your answer (each about what THEY said; see the conversation)\n");
        sb.append("Ashwyn prays: please heal me\n");
        return sb.toString();
    }

    @Test
    void aShortPerceptionIsLeftAlone() {
        String p = "# Perception\n## The world\nQuiet.\n";
        assertSame(p, LitePerception.trim(p, 6000));
    }

    @Test
    void theLeastNeededSectionsGoFirst() {
        String full = perception();
        String cut = LitePerception.trim(full, 5000);
        assertTrue(cut.length() <= 5000, "fits: " + cut.length());
        assertFalse(cut.contains("## Structures mortals have built"));
        assertFalse(cut.contains("## Your works"));
        assertTrue(cut.startsWith("# Perception — day 3, noon\n"));
        assertTrue(cut.contains("#4 Build a well."), "binding commands are never dropped");
        assertTrue(cut.contains("Ashwyn prays: please heal me"));
        assertTrue(cut.contains("## Conversation"));
        assertTrue(cut.contains("## Your ledger"), "the ledger outlives the scenery");
    }

    @Test
    void timelinesKeepTheirNewestLines() {
        String cut = LitePerception.trim(perception(), 1200);
        assertTrue(cut.length() <= 1200, "fits: " + cut.length());
        assertTrue(cut.contains("#4 Build a well."));
        assertTrue(cut.contains("Ashwyn prays: please heal me"));
        assertTrue(cut.contains("Ashwyn: line 49"), "the newest words stay");
        assertFalse(cut.contains("Ashwyn: line 0\n"), "the oldest go");
        assertTrue(cut.contains("shortened"));
    }

    @Test
    void ranksFollowTheHeadings() {
        assertEquals(0, LitePerception.rank("## Operator directive (from the server operator — follow it)\n"));
        assertEquals(0, LitePerception.rank("## Waiting for your answer\n"));
        assertEquals(1, LitePerception.rank("## Mortals present\n"));
        assertEquals(7, LitePerception.rank("## Other gods of this world\n"));
        assertEquals(6, LitePerception.rank("## Something new\n"));
    }

    @Test
    void thinkTagsAreSplitWhereverTheChunksFall() {
        String whole = "<think>plan the answer</think>Hello <b>mortal</b>, <think>hm</think>rise.";
        for (int size = 1; size <= 9; size++) {
            ThinkSplitter s = new ThinkSplitter();
            StringBuilder thinking = new StringBuilder(), prose = new StringBuilder();
            List<String> order = new ArrayList<>();
            for (int i = 0; i < whole.length(); i += size) {
                s.feed(whole.substring(i, Math.min(whole.length(), i + size)), (t, text) -> {
                    (t ? thinking : prose).append(text);
                    order.add(t ? "T" : "P");
                });
            }
            s.end((t, text) -> (t ? thinking : prose).append(text));
            assertEquals("plan the answerhm", thinking.toString(), "chunks of " + size);
            assertEquals("Hello <b>mortal</b>, rise.", prose.toString(), "chunks of " + size);
            assertEquals("T", order.get(0));
        }
        ThinkSplitter open = new ThinkSplitter();
        StringBuilder tail = new StringBuilder();
        open.feed("a <thi", (t, text) -> tail.append(text));
        open.end((t, text) -> tail.append(text));
        assertEquals("a <thi", tail.toString(), "a tag that never completes is ordinary text");
    }
}
