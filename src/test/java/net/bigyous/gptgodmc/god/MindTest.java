package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The god's private thoughts come back only when they bear on the moment, and fade when they never do. */
class MindTest {
    static final long T = 1_700_000_000_000L;
    static final long MIN = 60_000L, DAY = 86_400_000L;

    static Mind.State mind() {
        Mind.State s = new Mind.State();
        Mind.hold(s, "belief", "Tyler fears me, but not enough", List.of("Tyler"), 18, T, "you");
        Mind.hold(s, "intention", "Test Ash's loyalty when she next prays", List.of("Ash"), 18, T, "you");
        Mind.hold(s, "question", "Why does no one visit my shrine on the hill?", List.of(), 18, T, "you");
        return s;
    }

    static Mind.Cue cue(List<String> names, String text, boolean quiet) {
        return new Mind.Cue(names, text, quiet);
    }

    @Test
    void anOrdinaryMomentRecallsNothing() {
        Mind.State s = mind();
        List<Mind.Recalled> r = Mind.recall(s, cue(List.of("Bo"), "Bo mined 12 stone and crafted a furnace", false), 3,
                420, T + MIN);
        assertTrue(r.isEmpty(), "nothing bears on it, so nothing is shown and nothing is spent");
    }

    @Test
    void aThoughtReturnsWhenItsMortalIsInvolved() {
        Mind.State s = mind();
        List<Mind.Recalled> r = Mind.recall(s, cue(List.of("Ash"), "Ash prays to you: \"grant me iron\"", false), 3, 420,
                T + MIN);
        assertEquals(1, r.size());
        assertEquals("intention", r.get(0).thought().kind);
        assertFalse(r.get(0).drifted());
        String shown = Mind.render(r, T + MIN);
        assertTrue(shown.startsWith("- [m2] intention ("), shown);
    }

    @Test
    void aThoughtReturnsWhenTheMomentTouchesItsWords() {
        Mind.State s = mind();
        List<Mind.Recalled> r = Mind.recall(s, cue(List.of(), "Bo built a shrine of quartz on the hill", false), 3, 420,
                T + MIN);
        assertEquals("m3", r.get(0).thought().id, "shrine and hill are its words");
        assertTrue(Mind.recall(s, cue(List.of(), "Bo walked up a hill", false), 3, 420, T + MIN).isEmpty(),
                "one shared word is not enough");
    }

    @Test
    void inAQuietMomentOneThoughtDriftsBackAndNotTheSameOneAgain() {
        Mind.State s = mind();
        List<Mind.Recalled> first = Mind.recall(s, cue(List.of(), "a quiet moment", true), 3, 420, T + 40 * MIN);
        assertEquals(1, first.size());
        assertTrue(first.get(0).drifted());
        Mind.surfaced(first, T + 40 * MIN);
        List<Mind.Recalled> second = Mind.recall(s, cue(List.of(), "a quiet moment", true), 3, 420, T + 45 * MIN);
        assertFalse(second.get(0).thought().id.equals(first.get(0).thought().id));
        assertTrue(Mind.render(first, T).contains("it drifts back to you"));
    }

    @Test
    void theStrongestComeFirstWithinTheLimits() {
        Mind.State s = mind();
        Mind.hold(s, "belief", "Tyler steals from the village at night", List.of("Tyler"), 18, T, "you");
        Mind.hold(s, "question", "Does Tyler pray only when he wants iron?", List.of("Tyler"), 18, T, "you");
        Mind.hold(s, "intention", "Reward Tyler if he rebuilds the village well", List.of("Tyler"), 18, T, "you");
        List<Mind.Recalled> r = Mind.recall(s, cue(List.of("Tyler"), "Tyler broke into the village at night", false), 2,
                420, T + MIN);
        assertEquals(2, r.size(), "never more than the limit");
        assertEquals("Tyler steals from the village at night", r.get(0).thought().text, "it shares the most with the moment");
        assertEquals(1, Mind.recall(s, cue(List.of("Tyler"), "Tyler", false), 3, 60, T + MIN).size(),
                "the character budget holds it to one");
    }

    @Test
    void theSameThoughtTwiceIsOneThoughtAndTheLeastUsedGoes() {
        Mind.State s = new Mind.State();
        Mind.hold(s, "belief", "Tyler fears me, but not enough", List.of(), 3, T, "you");
        Mind.Thought again = Mind.hold(s, "belief", "Tyler fears me but not enough.", List.of("Tyler"), 3, T + MIN, "you");
        assertEquals(1, s.thoughts.size());
        assertEquals(List.of("Tyler"), again.about);
        Mind.hold(s, "belief", "The desert folk are loyal", List.of(), 3, T + 2 * MIN, "reflection");
        Mind.hold(s, "question", "Who lit the forest fire?", List.of(), 3, T + 3 * MIN, "you");
        Mind.hold(s, "intention", "Raise a storm over the next liar", List.of(), 3, T + 4 * MIN, "you");
        assertEquals(3, s.thoughts.size());
        assertNull(Mind.find(s, "m2"), "what came on reflection goes before what it wrote itself");
        assertThrows(Mind.Refusal.class, () -> Mind.hold(s, "dream", "Flying over the sea", List.of(), 3, T, "you"));
    }

    @Test
    void thoughtsThatNeverMatterFade() {
        Mind.State s = mind();
        List<Mind.Recalled> r = Mind.recall(s, cue(List.of("Ash"), "Ash prays", false), 3, 420, T + 6 * DAY);
        Mind.surfaced(r, T + 6 * DAY);
        assertEquals(2, Mind.fade(s, T + 8 * DAY, 7 * DAY));
        assertEquals("m2", s.thoughts.get(0).id, "the one that bore on a moment stays");
        Mind.revise(s, "m2", "Ash is loyal; test her no more", T + 8 * DAY);
        assertEquals("Ash is loyal; test her no more", Mind.letGo(s, "m2").text);
        assertThrows(Mind.Refusal.class, () -> Mind.letGo(s, "m2"));
    }

    @Test
    void reflectionAddsRewritesAndDrops() {
        Mind.State s = mind();
        String reply = """
                Here is what I hold:
                {"add": [{"kind": "belief", "text": "Bo gives only when watched", "about": ["Bo"]},
                         {"kind": "nonsense", "text": "ignored"}],
                 "revise": [{"id": "m1", "text": "Tyler no longer fears me at all"}, {"id": "m99", "text": "nothing"}],
                 "drop": ["m3"]}""";
        Mind.Reflection r = Mind.parseReflection(reply);
        assertEquals("1 new, 1 rewritten, 1 let go", Mind.apply(s, r, 18, T + 30 * MIN));
        assertEquals("Tyler no longer fears me at all", Mind.find(s, "m1").text);
        assertNull(Mind.find(s, "m3"));
        assertEquals("reflection", s.thoughts.get(s.thoughts.size() - 1).source);
        assertEquals(T + 30 * MIN, s.reflectedAt);
        assertNull(Mind.parseReflection("I have nothing to add."));
        assertTrue(Mind.renderAll(s, "Bo", T + 31 * MIN).contains("Bo gives only when watched"));
        assertFalse(Mind.renderAll(s, "Bo", T + 31 * MIN).contains("Tyler"));
        assertTrue(Mind.renderAll(s, null, T + 31 * MIN).startsWith("Beliefs:\n"));
    }

    @Test
    void theReflectionPromptCarriesTheMindAndTheThoughts() {
        Mind.State s = mind();
        Memory.CycleRecord rec = new Memory.CycleRecord();
        rec.at = T;
        rec.thought = "Ash knelt at once. Is it devotion or fear?";
        rec.acts.add("whisper{\"player\":\"Ash\"} → said");
        String p = Mind.reflectionPrompt("Vaelor the Unblinking", s, List.of(rec), T + MIN);
        assertTrue(p.contains("Vaelor the Unblinking") && p.contains("[m1] belief") && p.contains("devotion or fear"), p);
    }
}
