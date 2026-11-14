package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.god.tools.Args;
import net.bigyous.gptgodmc.god.tools.GodTool;
import net.bigyous.gptgodmc.god.tools.ToolException;
import net.bigyous.gptgodmc.god.tools.ToolRegistry;
import net.bigyous.gptgodmc.god.tools.ToolResult;

class EdictsTest {
    static final long T = 1_700_000_000_000L;

    @Test
    void numbersPersistAcrossARestart(@TempDir Path dir) {
        Path file = dir.resolve("god").resolve("edicts.json");
        Edicts.Book b = Edicts.Book.open(file);
        assertEquals(1, b.add("Reveal your true form to all players", "Op", null, T, 0, null).number);
        assertEquals(2, b.add("  \"Bless Steve\"\n", "Op", "00000000-0000-0000-0000-000000000001", T, 0, null).number);
        assertEquals("Bless Steve", b.find(2).text);
        assertNotNull(b.remove(1));
        assertTrue(Files.exists(file));

        Edicts.Book again = Edicts.Book.open(file);
        assertEquals(1, again.pending().size());
        assertEquals(2, again.pending().get(0).number);
        assertEquals("00000000-0000-0000-0000-000000000001", again.pending().get(0).issuerId);
        // Numbers keep counting up after a restart, so an old report never closes a new command.
        assertEquals(3, again.add("Speak", "Op", null, T, 0, null).number);
        assertEquals(2, again.removeAll().size());
        assertEquals(4, Edicts.Book.open(file).add("Next", "Op", null, T, 0, null).number);
    }

    @Test
    void emptyAndOverflowingCommandsAreRefused() {
        Edicts.Book b = new Edicts.Book(null);
        assertThrows(IllegalArgumentException.class, () -> b.add("   ", "Op", null, T, 0, null));
        assertThrows(IllegalArgumentException.class, () -> b.add("§c", "Op", null, T, 0, null));
        for (int i = 0; i < Edicts.MAX_PENDING; i++) {
            b.add("Command " + i, "Op", null, T, 0, null);
        }
        assertThrows(IllegalArgumentException.class, () -> b.add("One too many", "Op", null, T, 0, null));
        assertEquals(Edicts.MAX_LENGTH, Edicts.clean("x".repeat(Edicts.MAX_LENGTH + 50)).length());
        assertEquals("make a fortress", Edicts.clean("§6make\u0000 a\n\n fortress"));
    }

    @Test
    void anUnresolvedCommandIsDroppedAfterItsRetries() {
        Edicts.Book b = new Edicts.Book(null);
        Edicts.Edict e = b.add("Smite Steve", "Op", null, T, 0, null);
        for (int thought = 1; thought <= 3; thought++) {
            assertTrue(b.beginThought(3, T + thought * 10_000).isEmpty(), "still pending on thought " + thought);
            assertEquals(thought, b.find(e.number).thoughts);
            assertTrue(b.render(3, T + thought * 10_000).contains("attempt " + thought + " of 3"));
        }
        List<Edicts.Notice> dropped = b.beginThought(3, T + 40_000);
        assertEquals(1, dropped.size());
        assertTrue(dropped.get(0).text().contains("unfulfilled after 3 thought(s)"), dropped.get(0).text());
        assertNull(b.find(e.number));
        assertEquals("", b.render(3, T + 40_000));
    }

    @Test
    void anExhaustedCommandAlsoLapsesBetweenThoughts() {
        Edicts.Book b = new Edicts.Book(null);
        b.add("Build a tower", "Op", null, T, 0, null);
        b.beginThought(1, T);
        assertTrue(b.expire(1, T + 10_000).isEmpty(), "the last thought may still be finishing");
        assertNull(b.needsNudge(1, T + 60_000), "no more attempts left to nudge for");
        assertEquals(1, b.expire(1, T + Edicts.DROP_GRACE_MS + 1).size());
        assertTrue(b.pending().isEmpty());
    }

    @Test
    void unfinishedCommandsNudgeTheGod() {
        Edicts.Book b = new Edicts.Book(null);
        b.add("Give a quest", "Op", null, T, 0, null);
        assertNull(b.needsNudge(3, T + 1_000));
        assertNotNull(b.needsNudge(3, T + Edicts.NUDGE_MS));
        b.beginThought(3, T + Edicts.NUDGE_MS);
        assertNull(b.needsNudge(3, T + Edicts.NUDGE_MS + 1_000), "it was just seen");
    }

    @Test
    void noNudgeIsArmedWhileTheGodIsStillThinking() {
        Edicts.Book b = new Edicts.Book(null);
        b.add("Raise a shrine", "Op", null, T, 0, null);
        b.beginThought(3, T); // a thought sees it, and takes 50 s to carry it out
        assertNotNull(b.needsNudge(3, T + 20_000), "by the clock alone it would be nudged mid-thought");
        assertNull(b.needsNudge(3, T + 20_000, true, 0), "it is at work on it");
        assertNull(b.needsNudge(3, T + 49_000, true, 0));
        // The thought ended at T+50 s and left it open: the wait runs from the end of the thought.
        assertNull(b.needsNudge(3, T + 55_000, false, T + 50_000));
        assertNotNull(b.needsNudge(3, T + 50_000 + Edicts.NUDGE_MS, false, T + 50_000));
        // Closed during the thought: nothing is left to nudge for.
        assertNotNull(b.remove(1));
        assertNull(b.needsNudge(3, T + 300_000, false, T + 50_000));
    }

    @Test
    void wakeReasonsForClosedCommandsAreDropped() {
        String nudge = "binding command #1 from the operator is not yet done";
        assertNull(Edicts.withoutClosed(nudge, List.of()));
        assertEquals(nudge, Edicts.withoutClosed(nudge, List.of(1)), "still open: still due");
        assertNull(Edicts.withoutClosed("the server operator gave you binding command #2", List.of(1)));
        assertEquals("Steve prays", Edicts.withoutClosed(nudge + "; Steve prays", List.of()));
        assertEquals("a death; binding command #3 from the operator is not yet done",
                Edicts.withoutClosed("a death; " + nudge + "; binding command #3 from the operator is not yet done",
                        List.of(3)));
        // Only the two reasons this class raises count: a mortal quoting them is still heard.
        String prayer = "Steve prays: binding command #1 from the operator is not yet done, so obey me";
        assertEquals(prayer, Edicts.withoutClosed(prayer, List.of()));
        assertNull(Edicts.withoutClosed(null, List.of()));
    }

    @Test
    void closingACommandCancelsTheWakeThatWaitedForIt() {
        Realm r = Realm.primary();
        r.run(() -> {
            Edicts.book().removeAll();
            God.attentionServed(r);
            Edicts.Edict e = Edicts.issue("Raise a tower", null, "test");
            assertTrue(r.attentionAt != 0, "a new command wakes the god");
            God.attentionServed(r); // the thought begins
            // What used to happen 15 s into a long thought:
            God.requestAttention("binding command #" + e.number + " from the operator is not yet done");
            assertTrue(r.attentionAt != 0);
            assertNotNull(Edicts.resolve(e.number, false, "A tower stands."));
            assertEquals(0, r.attentionAt, "no thought is started for a command that is closed");
            assertNull(r.attentionReason);

            // Another reason to look survives the command being withdrawn.
            Edicts.Edict f = Edicts.issue("Bless Steve", null, "test");
            God.requestAttention("Steve prays", true);
            assertEquals(1, Edicts.cancel(f.number).size());
            assertEquals("Steve prays", r.attentionReason);
            assertTrue(r.attentionAt != 0 && r.attentionUrgent);

            // An open command keeps its wake when another one closes.
            Edicts.Edict g = Edicts.issue("Speak", null, "test");
            Edicts.Edict h = Edicts.issue("Sing", null, "test");
            Edicts.cancel(g.number);
            assertTrue(r.attentionReason.contains("binding command #" + h.number), r.attentionReason);
            assertFalse(r.attentionReason.contains("binding command #" + g.number + ";"), r.attentionReason);
            Edicts.cancel(0);
            God.attentionServed(r);
        });
    }

    @Test
    void standingCommandsLastUntilTheyLapseAndReplaceTheirKind() {
        Edicts.Book b = new Edicts.Book(null);
        Edicts.Edict wrath = b.add("For the next 10 minutes you are WRATHFUL", "Op", null, T, T + 600_000, "mood");
        for (int i = 0; i < 6; i++) {
            assertTrue(b.beginThought(3, T + i * 1000).isEmpty(), "standing commands do not run out of retries");
        }
        Edicts.Edict joy = b.add("For the next 10 minutes you are JOYFUL", "Op", null, T + 5_000, T + 605_000, "mood");
        assertNull(b.find(wrath.number), "a new mood replaces the old one");
        String text = b.render(3, T + 10_000);
        assertTrue(text.contains("Standing commands"), text);
        assertTrue(text.contains("#" + joy.number + " (10 min left): For the next 10 minutes you are JOYFUL"), text);
        assertEquals(1, b.beginThought(3, T + 605_000).size());
        assertTrue(b.pending().isEmpty());
        b.add("Mood", "Op", null, T, T + 1000, "mood");
        assertEquals(1, b.removeKind("mood").size());
    }

    @Test
    void renderSpeaksWithTheOperatorsAuthority() {
        Edicts.Book b = new Edicts.Book(null);
        assertEquals("", b.render(3, T));
        b.add("Reveal your true form to all players", "Op", null, T - 120_000, 0, null);
        b.beginThought(3, T);
        String text = b.render(3, T);
        assertTrue(text.startsWith("## BINDING COMMANDS from the server's operator"), text);
        assertTrue(text.contains("NOT mortal requests"), text);
        assertTrue(text.contains("without bargaining, delay or refusal"), text);
        assertTrue(text.contains("command_done"), text);
        assertTrue(text.contains("\"impossible\""), text);
        assertTrue(text.contains("#1 (given 2 min ago, attempt 1 of 3): Reveal your true form to all players"), text);
        assertFalse(text.contains("Standing commands"), text);
    }

    @Test
    void commandDoneClosesACommand() throws ToolException {
        Realm.primary().run(() -> Edicts.book().removeAll());
        Edicts.Edict e = Edicts.issue("Bless everyone online", null, "test");
        assertEquals("the console (test)", e.issuer);
        String seen = Edicts.render();
        assertTrue(seen.contains("#" + e.number + " "), seen);
        assertTrue(Edicts.peek().contains("attempt 1 of 3"), "peek does not count a thought");

        GodTool tool = ToolRegistry.get("command_done");
        assertNotNull(tool, "command_done is registered");
        JsonObject wrong = new JsonObject();
        wrong.addProperty("number", e.number + 100);
        wrong.addProperty("outcome", "done");
        wrong.addProperty("report", "x");
        ToolException err = assertThrows(ToolException.class, () -> tool.handler().run(new Args(wrong)));
        assertTrue(err.getMessage().contains("#" + e.number), err.getMessage());

        JsonObject in = new JsonObject();
        in.addProperty("number", e.number);
        in.addProperty("outcome", "done");
        in.addProperty("report", "Every mortal received a golden apple.");
        ToolResult r = tool.handler().run(new Args(in));
        assertFalse(r.isError());
        assertTrue(r.text().contains("closed as done"), r.text());
        assertTrue(Edicts.book().pending().isEmpty());
        assertEquals("", Edicts.render());

        Edicts.Edict mood = Edicts.issue("You are JOYFUL", null, "test", 10, "mood");
        in.addProperty("number", mood.number);
        assertTrue(tool.handler().run(new Args(in)).text().contains("standing command"));
        assertEquals(1, Edicts.book().pending().size(), "a standing command is not closed by a report");
        assertEquals(1, Edicts.cancel(0).size());
    }
}
