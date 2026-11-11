package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.EventLogger;

class DossierTest {
    /** Characters of the same memory sections (ledger, recent acts, quests, laws, empty events) before dossiers. */
    private static final int QUIET_BEFORE = 2698;

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

    /** The same server history used to measure the old perception (QUIET_BEFORE), plus deeds and words. */
    static Memory scenario() {
        Memory m = new Memory();
        long now = System.currentTimeMillis();
        m.adjustFavor("Steve", 4, "built a shrine of gold near spawn");
        m.remember("Steve", "Promised him a diamond sword if he slays the wither");
        m.remember("Steve", "He mocked me in chat once and apologised");
        m.adjustFavor("Alex", -4, "burned the chapel");
        m.remember("Alex", "Swore to rebuild the chapel by dawn");
        m.remember("Notch", "An old wanderer who left long ago");
        m.remember("world", "The spawn obelisk is sacred to me");
        m.recordOffering("Steve", "12 diamond", "vessel");
        m.addQuest("Rebuild the chapel", "same spot, stone bricks", "Alex", 30);
        for (int i = 0; i < 10; i++) {
            Memory.CycleRecord r = new Memory.CycleRecord();
            r.at = now - (10 - i) * 120_000L;
            r.trigger = i % 2 == 0 ? "Steve spoke" : "heartbeat";
            r.perceived = i % 2 == 0 ? "Steve said in chat: \"hello god, are you there?\"; 3 other event(s)"
                    : "5 other event(s)";
            if (i % 3 == 0) {
                r.acts.add("whisper{\"player\":\"Steve\",\"message\":\"I see you, little builder. Keep your promise.\"}"
                        + " → Steve heard your whisper.");
                r.acts.add("judge{\"player\":\"Steve\",\"change\":1,\"reason\":\"kept faith\"} → Steve's favor is now +5 "
                        + "(favored).");
            }
            m.recent.add(r);
        }
        // What the new memory adds over time.
        List<String> online = List.of("Steve", "Alex");
        m.addDeed("Alex", "burned the chapel near spawn", now - 7_200_000L);
        m.addDeed("Alex", "killed Steve (Steve was slain by Alex using [Iron Sword])", now - 3_600_000L);
        m.addDeed("Steve", "died: was slain by Alex using [Iron Sword]", now - 3_600_000L);
        m.addDeed("Steve", "earned [Stone Age]", now - 1_800_000L);
        m.addWords("Steve", "they prayed: \"make Alex pay\"", now - 3_500_000L);
        m.addWords("Steve", "you whispered: \"Justice will come.\"", now - 3_400_000L);
        m.subjectIfAny("Alex").summary = "Alex has defied me since the first night: he mocked my laws and paid "
                + "for it, then begged and swore oaths he broke.";
        m.seen(online, now);
        return m;
    }

    private static EventLogger.Drained quiet() {
        return new EventLogger.Drained(List.of(), List.of(), List.of(), List.of(), 0);
    }

    private static EventLogger.Drained busy() {
        return new EventLogger.Drained(List.of("[12s ago] Alex placed 3 stone_bricks near the chapel"),
                List.of("[5s ago] Alex said in chat: \"god can you come here rq\""),
                List.of("[20s ago] Steve prays to you: \"great one, punish Alex for my death\""), List.of(), 0);
    }

    @Test
    void quietPerceptionStaysSmallAndInvolvedMortalsGetDossiers() throws Exception {
        Memory m = scenario();
        List<String> online = List.of("Steve", "Alex");
        String quiet = Perception.memorySections(m, online, quiet());
        String busy = Perception.memorySections(m, online, busy());

        assertTrue(quiet.length() <= QUIET_BEFORE + 600, "quiet perception grew to " + quiet.length());
        assertFalse(quiet.contains("### "), "no dossiers when nobody is involved:\n" + quiet);
        assertFalse(quiet.contains("Notch"), "offline mortals are left out unless mentioned");
        assertTrue(quiet.contains("- Steve (+4, favored)"), quiet);

        assertEquals(List.of("Steve", "Alex"), Perception.involved(m, online, busy()));
        assertTrue(busy.contains("### Steve — favor +4 (favored)"), busy);
        assertTrue(busy.contains("### Alex — favor -4 (out of favor: you may ignore their pleas)"), busy);
        assertTrue(busy.contains("past: Alex has defied me"), busy);
        assertTrue(busy.contains("quests: [q1] ACTIVE \"Rebuild the chapel\""), busy);

        String steve = m.renderDossier("Steve", true, false, System.currentTimeMillis());
        String alex = m.renderDossier("Alex", true, false, System.currentTimeMillis());

        Path out = Path.of("build", "test-renders", "perception-sample.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, "# Memory sections of a perception (the world, vessel and mortals-present parts are "
                + "unchanged and need a live server)\n"
                + "# quiet: " + quiet.length() + " chars (same history rendered by the old code: " + QUIET_BEFORE
                + " chars)\n# with two dossiers: " + busy.length() + " chars; Steve's dossier " + steve.length()
                + " chars, Alex's " + alex.length() + " chars\n\n"
                + "======== QUIET ========\n" + quiet + "\n======== TWO DOSSIERS ========\n" + busy,
                StandardCharsets.UTF_8);
    }

    @Test
    void dossierStaysWithinBudgetAndRecallShowsMore() {
        Memory m = new Memory();
        long now = System.currentTimeMillis();
        String longText = "did something remarkable and very long-winded that goes on and on about the chapel ";
        for (int i = 0; i < 20; i++) {
            m.addDeed("Steve", longText + i, now - (20 - i) * 60_000L);
            m.remember("Steve", "note " + i + " " + longText);
            m.addWords("Steve", "they: \"" + longText + i + "\"", now - i * 1000L);
        }
        m.subjectIfAny("Steve").summary = "x".repeat(900);
        m.setTitle("Steve", "the Long-Winded", "gold");
        m.addQuest("Build a very long wall around the entire spawn", "", "Steve", 30);
        String dossier = m.renderDossier("Steve", true, false, now);
        assertTrue(dossier.length() <= Memory.DOSSIER_BUDGET, dossier.length() + "\n" + dossier);
        assertTrue(dossier.contains("+") && dossier.contains("older"), dossier);
        String full = m.renderDossier("Steve", false, true, now);
        assertTrue(full.length() > dossier.length());
        assertTrue(full.contains("not here"));
        assertTrue(full.contains(longText + "0"), "recall shows every kept deed");
        assertTrue(m.renderDossier("Nobody", false, false, now).contains("new to you"));
    }
}
