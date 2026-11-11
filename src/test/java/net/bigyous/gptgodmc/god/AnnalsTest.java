package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnnalsTest {
    private static final ZoneId UTC = ZoneId.of("UTC");

    private static long noon(YearMonth month, int day) {
        return month.atDay(day).atTime(12, 0).atZone(UTC).toInstant().toEpochMilli();
    }

    @Test
    void writesOneJsonLinePerEntryAndSearchesNewestFirst(@TempDir Path dir) throws Exception {
        Annals a = new Annals(dir, UTC);
        long now = System.currentTimeMillis();
        a.append(now - 3000, "speech", List.of("Steve"), "Steve said in chat: \"god can you come here rq\"");
        a.append(now - 2000, "event", List.of("Alex"), "Alex placed 3 stone near the chapel");
        a.append(now - 1000, "act", List.of("Steve"), "whisper{\"player\":\"Steve\"} → Steve heard your whisper.");
        a.flush();
        Path file = dir.resolve(a.fileName(now));
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("\"kind\":\"speech\"") && lines.get(0).contains("\"players\":[\"Steve\"]"),
                lines.get(0));

        Annals.Found all = a.search(null, null, null, 0, 10);
        assertEquals(3, all.entries().size());
        assertEquals("act", all.entries().get(0).kind());
        assertFalse(all.more());

        assertEquals(2, a.search(null, "steve", null, 0, 10).entries().size());
        assertEquals(1, a.search("come HERE", null, null, 0, 10).entries().size());
        assertEquals(1, a.search(null, null, "event", 0, 10).entries().size());
        assertEquals(0, a.search("nothing like this", null, null, 0, 10).entries().size());

        // Unflushed entries are found too, ahead of the file.
        a.append("prayer", List.of("Alex"), "Alex prays to you: \"forgive me\"");
        assertEquals("prayer", a.search(null, null, null, 0, 1).entries().get(0).kind());
        assertTrue(a.search(null, null, null, 0, 1).more());
        a.flush();
        assertEquals(4, Files.readAllLines(file, StandardCharsets.UTF_8).size());

        a.append("name", List.of(), "The god took a new name: Vhal'myr <the Ash> said \"hi\"");
        a.flush();
        assertEquals(1, a.search("vhal'myr <the", null, "name", 0, 5).entries().size());
        assertEquals(1, a.search("\"hi\"", null, null, 0, 5).entries().size());
    }

    @Test
    void rotatesMonthlyAndHonoursRetention(@TempDir Path dir) throws Exception {
        Annals a = new Annals(dir, UTC);
        YearMonth thisMonth = YearMonth.now(UTC);
        long old = noon(thisMonth.minusMonths(2), 10);
        long last = noon(thisMonth.minusMonths(1), 10);
        a.append(old, "event", List.of(), "Steve burned the chapel");
        a.append(last, "event", List.of(), "Steve rebuilt the chapel");
        a.flush();
        assertTrue(Files.exists(dir.resolve(thisMonth.minusMonths(2) + ".jsonl")));
        assertTrue(Files.exists(dir.resolve(thisMonth.minusMonths(1) + ".jsonl")));

        List<Annals.Entry> chapel = a.search("chapel", null, null, 0, 10).entries();
        assertEquals(2, chapel.size());
        assertTrue(chapel.get(0).text().contains("rebuilt"), "newest first across files");
        assertEquals(1, a.search("chapel", null, null, last - 1000, 10).entries().size(), "hours window");

        long fakeNow = noon(thisMonth, 15);
        assertEquals(0, a.prune(fakeNow, 0), "0 days keeps everything");
        assertEquals(1, a.prune(fakeNow, 30));
        assertFalse(Files.exists(dir.resolve(thisMonth.minusMonths(2) + ".jsonl")));
        assertTrue(Files.exists(dir.resolve(thisMonth.minusMonths(1) + ".jsonl")));
    }

    @Test
    void readsLargeFilesBackwardsAndStopsAtTheLimit(@TempDir Path dir) {
        Annals a = new Annals(dir, UTC);
        long start = System.currentTimeMillis() - 3_000_000L;
        String pad = "x".repeat(120);
        for (int i = 0; i < 3000; i++) {
            a.append(start + i * 1000L, "event", List.of(i % 2 == 0 ? "Steve" : "Alex"), "entry e" + String.format("%04d", i) + " ünïcode " + pad);
        }
        a.flush();
        Annals.Found newest = a.search(null, null, null, 0, 5);
        assertEquals(5, newest.entries().size());
        assertTrue(newest.more());
        assertTrue(newest.entries().get(0).text().startsWith("entry e2999 ünïcode"), newest.entries().get(0).text());
        assertTrue(newest.entries().get(4).text().startsWith("entry e2995 "));
        Annals.Found first = a.search("E0007", null, null, 0, 5);
        assertEquals(1, first.entries().size());
        assertEquals(List.of("Alex"), first.entries().get(0).players());
    }

    @Test
    void ramOnlyAnnalsWorkWithoutAFolder() {
        Annals a = Annals.forDir(null);
        a.append("event", List.of(), "a tree fell");
        assertEquals(1, a.search("tree", null, null, 0, 5).entries().size());
        a.flush(); // nothing to write, no error
    }
}
