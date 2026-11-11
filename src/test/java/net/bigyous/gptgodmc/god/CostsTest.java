package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CostsTest {
    private static final long T = 1_700_000_000_000L;

    @BeforeEach
    @AfterEach
    void clean() {
        Costs.reset();
    }

    @Test
    void windowsCountOnlyWhatFallsInThem() {
        Costs.add("main", 0.50, T - 2 * Costs.DAY); // older than a day
        Costs.add("main", 0.20, T - 5 * Costs.HOUR);
        Costs.add("main", 0.10, T - 30 * Costs.MINUTE);
        Costs.add("main", 0.03, T - 20_000);
        Costs.add("ysmere", 0.01, T - 5_000);
        assertEquals(0.04, Costs.window(null, Costs.MINUTE, T), 1e-9);
        assertEquals(0.14, Costs.window(null, Costs.HOUR, T), 1e-9);
        assertEquals(0.34, Costs.window(null, Costs.DAY, T), 1e-9);
        assertEquals(0.13, Costs.window("main", Costs.HOUR, T), 1e-9);
        assertEquals(0.01, Costs.window("ysmere", Costs.DAY, T), 1e-9);
        assertEquals(0.84, Costs.session(null), 1e-9);
        assertEquals(0.83, Costs.session("main"), 1e-9);
        assertEquals(5, Costs.requests());
    }

    @Test
    void oldBucketsAreNotCountedAgainADayLater() {
        Costs.add("main", 1.0, T);
        assertEquals(1.0, Costs.window("main", Costs.HOUR, T + 1000), 1e-9);
        assertEquals(0, Costs.window("main", Costs.HOUR, T + Costs.DAY), 1e-9);
        Costs.add("main", 0.25, T + Costs.DAY); // lands in the same slot of the ring
        assertEquals(0.25, Costs.window("main", Costs.DAY, T + Costs.DAY + 1000), 1e-9);
    }

    @Test
    void unpricedRequestsCountAsRequestsOnly() {
        Costs.add("main", 0, T);
        Costs.add("main", -1, T);
        assertEquals(0, Costs.session(null), 1e-9);
        assertEquals(2, Costs.requests());
    }

    @Test
    void perMinuteEndsWithTheCurrentMinute() {
        Costs.add("main", 0.02, T);
        Costs.add("main", 0.05, T - 3 * Costs.MINUTE);
        double[] spark = Costs.perMinute(60, T);
        assertEquals(60, spark.length);
        assertEquals(0.02, spark[59], 1e-9);
        assertEquals(0.05, spark[56], 1e-9);
        assertEquals(0, spark[58], 1e-9);
    }

    @Test
    void allTimeAddsTheUsageFileToThisSession(@TempDir Path dir) throws Exception {
        assertNull(Costs.allTime());
        Path csv = dir.resolve("usage.csv");
        Files.writeString(csv, "time,realm,model,purpose,input,cache_read,cache_write,output,cost_usd,session_total_usd\n"
                + "2026-11-01 10:00:00,main,claude-sonnet-5-5,thought #1,100,0,0,50,0.250000,0.250000\n"
                + "2026-11-01 10:01:00,main,local,thought #2,100,0,0,50,,0.250000\n"
                + "damaged row\n"
                + "2026-11-01 10:02:00,main,claude-sonnet-5-5,thought #3,100,0,0,50,0.500000,0.750000\n");
        Costs.loadBefore(csv);
        assertEquals(0.75, Costs.allTime(), 1e-9);
        Costs.add("main", 0.25, T);
        assertEquals(1.0, Costs.allTime(), 1e-9);
        Costs.loadBefore(dir.resolve("missing.csv"));
        assertEquals(0.25, Costs.allTime(), 1e-9);
    }
}
