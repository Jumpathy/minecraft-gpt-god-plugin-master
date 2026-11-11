package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeedsTest {
    private Chronicler.Model savedModel;
    private Executor savedExecutor;

    @BeforeEach
    void offline() {
        savedModel = Chronicler.model;
        savedExecutor = Chronicler.executor;
        Chronicler.executor = Runnable::run;
        Chronicler.model = (prompt, max, purpose) -> null;
    }

    @AfterEach
    void restore() {
        Chronicler.model = savedModel;
        Chronicler.executor = savedExecutor;
    }

    @Test
    void overflowingDeedsAreDroppedWhenTheModelIsAway() {
        Memory m = new Memory();
        long t = System.currentTimeMillis() - 100_000;
        for (int i = 0; i < 30; i++) {
            m.addDeed("Steve", "deed number " + i, t + i * 1000L);
        }
        Memory.Subject s = m.subjectIfAny("Steve");
        assertTrue(s.deeds.size() <= GodSettings.deedsKept(), "kept " + s.deeds.size());
        assertEquals("deed number 29", s.deeds.get(s.deeds.size() - 1).text);
        assertNull(s.summary);
        assertTrue(!s.foldingDeeds);
    }

    @Test
    void overflowingDeedsFoldIntoTheSummary() {
        StringBuilder seen = new StringBuilder();
        Chronicler.model = (prompt, max, purpose) -> {
            seen.append(prompt);
            return "I remember Steve: he burned my chapel, then rebuilt it.";
        };
        Memory m = new Memory();
        for (int i = 0; i < 25; i++) {
            m.addDeed("Steve", i == 0 ? "burned the chapel" : "deed " + i, 1000L + i * 1000L);
        }
        Memory.Subject s = m.subjectIfAny("Steve");
        assertEquals("I remember Steve: he burned my chapel, then rebuilt it.", s.summary);
        assertEquals(GodSettings.deedsKept() / 2, s.deeds.size());
        assertTrue(seen.toString().contains("burned the chapel"), "the oldest deeds were sent to be folded");
        assertTrue(m.renderDossier("Steve", true, false, System.currentTimeMillis()).contains("past: I remember Steve"));
    }

    @Test
    void repeatedDeedsMerge() {
        Memory m = new Memory();
        m.addDeed("Steve", "touched your vessel", 1000);
        m.addDeed("Steve", "touched your vessel", 5000);
        assertEquals(1, m.subjectIfAny("Steve").deeds.size());
        assertEquals(5000, m.subjectIfAny("Steve").deeds.get(0).at);
    }

    @Test
    void notableEventsBecomeDeedsAndWords() {
        Memory m = new Memory();
        List<String> online = List.of("Steve", "Alex");
        m.noticeEvent("PRAYER", "Steve prays to you: \"send rain\"", true, online);
        m.noticeEvent("SPEECH", "Alex said in chat: \"god can you come here rq\"", true, online);
        m.noticeEvent("SPEECH", "Alex said in chat: \"lol\"", false, online);
        m.noticeEvent("EVENT", "DEATH: Steve was slain by Alex using [Iron Sword]", false, online);
        m.noticeEvent("EVENT", "Fluffy was killed by Alex", false, online);
        m.noticeEvent("EVENT", "Alex has made the advancement [Stone Age]", false, online);
        m.noticeEvent("EVENT", "Steve placed 3 stone near the chapel", false, online);
        m.noticeEvent("PRAYER", "Alex reached out and touched your vessel", true, online);
        m.noticeEvent("EVENT", "Herobrine123 entered the world", false, online);

        Memory.Subject steve = m.subjectIfAny("Steve");
        Memory.Subject alex = m.subjectIfAny("Alex");
        assertEquals(List.of("prayed: \"send rain\"", "died: was slain by Alex using [Iron Sword]"),
                steve.deeds.stream().map(n -> n.text).toList());
        List<String> alexDeeds = alex.deeds.stream().map(n -> n.text).toList();
        assertTrue(alexDeeds.get(0).startsWith("killed Steve"), alexDeeds.toString());
        assertTrue(alexDeeds.contains("killed Fluffy"), alexDeeds.toString());
        assertTrue(alexDeeds.contains("earned [Stone Age]"), alexDeeds.toString());
        assertTrue(alexDeeds.contains("reached out and touched your vessel"), alexDeeds.toString());
        assertEquals(1, alex.words.size(), "only words aimed at the god are kept");
        assertEquals("they: \"god can you come here rq\"", alex.words.get(0).text);
        assertEquals("first entered your world", m.subjectIfAny("Herobrine123").deeds.get(0).text);

        // Everything, notable or not, is in the annals.
        assertEquals(9, m.annals().search(null, null, null, 0, 50).entries().size());
        assertEquals(List.of("Steve"), m.annals().search("placed stone", null, null, 0, 5).entries().get(0).players());
    }

    @Test
    void actsBecomeDeedsWordsAndAnnals() {
        Memory m = new Memory();
        m.noticeEvent("SPEECH", "Steve said in chat: \"god are you there\"", true, List.of("Steve", "Alex"));
        m.subject("Alex");
        Memory.CycleRecord r = new Memory.CycleRecord();
        r.at = System.currentTimeMillis();
        r.trigger = "Steve spoke";
        r.thought = "Steve calls; I shall answer him.";
        r.acts.add("whisper{\"player\":\"Steve\",\"message\":\"I hear you, little one.\"} → Steve heard your whisper.");
        r.acts.add("smite{\"player\":\"Alex\",\"reason\":\"murder\"} → Lightning struck Alex.");
        r.acts.add("heal{\"player\":\"Steve\"} ✗ Steve is not online.");
        r.acts.add("announce{\"message\":\"Behold!\"} → All mortals heard you.");
        m.record(r);
        Memory.Subject steve = m.subjectIfAny("Steve");
        assertEquals(List.of("they: \"god are you there\"", "you whispered: \"I hear you, little one.\"",
                "you proclaimed: \"Behold!\""), steve.words.stream().map(n -> n.text).toList());
        assertTrue(steve.deeds.isEmpty(), "a failed power is no deed: " + steve.deeds);
        assertEquals("you: smite — Lightning struck Alex.", m.subjectIfAny("Alex").deeds.get(0).text);
        assertEquals(4, m.annals().search(null, null, "act", 0, 10).entries().size());
        assertEquals(1, m.annals().search(null, null, "thought", 0, 10).entries().size());
    }

    @Test
    void questsOfferingsAndRememberedDeeds() {
        Memory m = new Memory();
        Memory.Quest q = m.addQuest("Rebuild the chapel", "", "Alex", 10);
        q.status = "failed";
        q.resolvedAt = System.currentTimeMillis();
        m.questEnded(q);
        m.recordOffering("Alex", "12 diamond", "vessel");
        List<String> deeds = m.subjectIfAny("Alex").deeds.stream().map(n -> n.text).toList();
        assertEquals(List.of("was given your quest q1 \"Rebuild the chapel\"", "failed your quest \"Rebuild the chapel\"",
                "offered 12 diamond at your vessel"), deeds);
        Memory.Quest all = m.addQuest("Plant trees", "", "all", 10);
        assertNotNull(all);
        assertEquals(3, m.subjectIfAny("Alex").deeds.size());
    }

    @Test
    void oldAndPartialMemoryFilesLoad(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("memory.json");
        Files.writeString(file, "{\"chronicle\":null,\"subjects\":{\"steve\":{\"name\":\"Steve\",\"favor\":-4,"
                + "\"notes\":[{\"at\":1,\"text\":\"rude\"},null],\"deeds\":null,\"words\":null,\"offered\":null},"
                + "\"alex\":null},\"quests\":[null],\"recent\":[{\"at\":5,\"acts\":null},null],\"works\":null}");
        Memory m = Memory.open(file);
        Memory.Subject s = m.subjectIfAny("Steve");
        assertEquals(-4, s.favor);
        assertTrue(s.deeds.isEmpty() && s.words.isEmpty());
        assertEquals(1, s.notes.size());
        m.addDeed("Steve", "came back");
        String dossier = m.renderDossier("Steve", true, false, System.currentTimeMillis());
        assertTrue(dossier.contains("out of favor: you may ignore their pleas"), dossier);
        assertTrue(dossier.contains("came back"), dossier);
        assertEquals(1, m.recent.size());
        assertTrue(m.renderRecent().contains("You did nothing"), m.renderRecent());

        Memory back = Memory.open(file);
        back.save();
        Memory again = Memory.open(file);
        assertEquals(-4, again.favorOf("steve"));
    }

    @Test
    void foldedChroniclesCarryPlayerSentences() {
        Chronicler.Folded f = Chronicler.parseFold("Here: {\"chronicle\":\"- I met Steve.\",\"players\":{\"Steve\":"
                + "\"He promised me a temple.\",\"Ghost\":\"\"}}");
        assertEquals("- I met Steve.", f.chronicle());
        assertEquals(1, f.players().size());
        assertEquals("- plain prose", Chronicler.parseFold("- plain prose").chronicle());
        assertNull(Chronicler.parseFold("{broken"));
        assertNull(Chronicler.parseFold(null));

        Memory m = new Memory();
        m.subject("Steve");
        m.addToSummary("Steve", "He promised me a temple.");
        m.addToSummary("Nobody", "ignored");
        assertEquals("He promised me a temple.", m.subjectIfAny("Steve").summary);
        assertNull(m.subjectIfAny("Nobody"));
    }
}
