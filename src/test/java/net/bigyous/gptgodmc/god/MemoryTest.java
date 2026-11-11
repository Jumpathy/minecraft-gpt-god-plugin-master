package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class MemoryTest {

    @Test
    void favorIsClampedAndNoted() {
        Memory m = new Memory();
        assertEquals(5, m.adjustFavor("Steve", 5, "built a shrine"));
        assertEquals(10, m.adjustFavor("steve", 9, "prayed"));
        assertEquals(10, m.favorOf("STEVE"));
        String ledger = m.renderLedger(List.of("Steve"));
        assertTrue(ledger.contains("chosen"), ledger);
        assertTrue(ledger.contains("built a shrine"), ledger);
    }

    @Test
    void questsExpireAndRender() {
        Memory m = new Memory();
        Memory.Quest q = m.addQuest("Build a shrine", "gold blocks", "Steve", 10);
        assertEquals("q1", q.id);
        assertNotNull(m.findQuest("build a shrine"));
        q.expiresAt = System.currentTimeMillis() - 1;
        assertEquals(1, m.expireQuests().size());
        assertTrue(m.renderQuests().contains("EXPIRED"), m.renderQuests());
    }

    @Test
    void survivesJsonRoundTrip() {
        Memory m = new Memory();
        m.adjustFavor("Steve", 3, "kind");
        m.addQuest("Plant trees", "", "all", 5);
        m.addWork("Shrine", "shrine", "world", 0, 64, 0, 40, "7x7x7");
        Memory.CycleRecord r = new Memory.CycleRecord();
        r.at = 1;
        r.trigger = "test";
        r.acts.add("whisper{} → ok");
        m.recent.add(r);
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        Memory back = gson.fromJson(gson.toJson(m), Memory.class);
        assertEquals(3, back.favorOf("Steve"));
        assertEquals(1, back.recent.size());
        assertEquals("whisper{} → ok", back.recent.peekFirst().acts.get(0));
        assertEquals(1, back.activeQuests().size());
        assertNotNull(back.findWork("Shrine"));
    }

    @Test
    void devotionSurvivesJsonRoundTrip() {
        Memory m = new Memory();
        m.addCommandment("Feed the wolves");
        m.setTitle("Steve", "the Faithful", "aqua");
        m.setMark("Steve", "curse", Long.MAX_VALUE);
        m.recordOffering("Steve", "3 gold_ingot", "vessel");
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        Memory back = gson.fromJson(gson.toJson(m), Memory.class);
        assertEquals(List.of("Feed the wolves"), back.commandments());
        assertEquals("the Faithful", back.titleOf("steve"));
        assertEquals("aqua", back.subjectIfAny("Steve").titleColor);
        assertEquals("curse", back.activeMark("Steve", System.currentTimeMillis()));
        assertEquals(1, back.subjectIfAny("Steve").offerings);
        assertEquals("3 gold_ingot", back.subjectIfAny("Steve").offered.get(0).what);
    }

    @Test
    void oldMemoryFilesStillLoad(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path file = dir.resolve("memory.json");
        // A memory.json written before commandments, titles, marks and offerings existed.
        java.nio.file.Files.writeString(file, "{\"chronicle\":\"Long ago\",\"subjects\":{\"steve\":{\"name\":\"Steve\","
                + "\"favor\":4,\"notes\":[{\"at\":1,\"text\":\"kind\"}]}},\"quests\":[],\"questCounter\":0,"
                + "\"recent\":[],\"works\":[],\"workCounter\":0}");
        Memory m = Memory.open(file);
        assertEquals(4, m.favorOf("Steve"));
        assertTrue(m.commandments().isEmpty());
        assertEquals(null, m.titleOf("Steve"));
        assertEquals(null, m.activeMark("Steve", System.currentTimeMillis()));
        assertEquals(1, m.recordOffering("Steve", "1 bread", "sky"));
        assertEquals(1, m.addCommandment("Be kind"));
        assertTrue(m.renderLedger(List.of("Steve")).contains("offerings: 1 in all"));

        java.nio.file.Files.writeString(file, "{\"commandments\":null,\"subjects\":{\"alex\":{\"name\":\"Alex\","
                + "\"offered\":null,\"notes\":null}}}");
        Memory n = Memory.open(file);
        assertTrue(n.commandments().isEmpty());
        assertEquals(1, n.recordOffering("Alex", "1 dirt", "sky"));
        assertTrue(n.renderLedger(List.of("Alex")).contains("1 dirt"));
    }

    @Test
    void worksAreListed() {
        Memory m = new Memory();
        Memory.Work w = m.addWork("Temple of Dusk", "temple", "world", 1, 64, 2, 900, "15x17x21");
        assertEquals("w1", w.id);
        assertTrue(m.renderWorks().contains("Temple of Dusk"));
        m.removeWork("w1");
        assertTrue(m.renderWorks().isEmpty());
    }
}
