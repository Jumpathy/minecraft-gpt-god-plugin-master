package net.bigyous.gptgodmc.god;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API spend over time, for the God Manager screen's Costs page (client mod, operators only): what each
 * god spent in the last minute, hour and day, this session, and all time. Kept in ten-second buckets for
 * 24 hours. Pure (no server); fed by {@link UsageLog#record}. Chat, the chest menu and the journal still
 * never show money.
 */
public final class Costs {
    static final long BUCKET_MS = 10_000;
    static final int BUCKETS = (int) (24 * 3_600_000L / BUCKET_MS);
    public static final long MINUTE = 60_000, HOUR = 3_600_000, DAY = 24 * HOUR;

    /** One god's buckets: micro-dollars per bucket, and which bucket number each slot now holds. */
    private static final class Ring {
        final long[] micro = new long[BUCKETS];
        final long[] bucket = new long[BUCKETS];
        long session;
        long requests;
    }

    private static final Map<String, Ring> BY_GOD = new ConcurrentHashMap<>();
    /** Spend recorded in usage.csv before this session began (micro-dollars), or -1 when not read. */
    private static volatile long before = -1;

    private Costs() {
    }

    /** Records one request's cost for a god. A model with no known price adds a request and no money. */
    public static void add(String god, double usd, long now) {
        if (god == null) return;
        Ring r = BY_GOD.computeIfAbsent(god, k -> new Ring());
        long m = usd > 0 ? Math.round(usd * 1_000_000) : 0;
        long b = now / BUCKET_MS;
        int slot = (int) Math.floorMod(b, (long) BUCKETS);
        synchronized (r) {
            if (r.bucket[slot] != b) {
                r.bucket[slot] = b;
                r.micro[slot] = 0;
            }
            r.micro[slot] += m;
            r.session += m;
            r.requests++;
        }
    }

    /** USD a god (null = every god) spent in the `ms` before now (to the nearest ten seconds). */
    public static double window(String god, long ms, long now) {
        long sum = 0;
        for (Map.Entry<String, Ring> e : BY_GOD.entrySet()) {
            if (god != null && !god.equals(e.getKey())) continue;
            sum += window(e.getValue(), now - ms, now);
        }
        return sum / 1_000_000.0;
    }

    /** Micro-dollars in the buckets that started after `from`, up to the one holding `to`. */
    private static long window(Ring r, long from, long to) {
        long first = Math.max(from / BUCKET_MS + 1, to / BUCKET_MS - BUCKETS + 1);
        long last = to / BUCKET_MS;
        long sum = 0;
        synchronized (r) {
            for (long b = first; b <= last; b++) {
                int slot = (int) Math.floorMod(b, (long) BUCKETS);
                if (r.bucket[slot] == b) sum += r.micro[slot];
            }
        }
        return sum;
    }

    /** USD a god (null = every god) spent since the server started. */
    public static double session(String god) {
        long sum = 0;
        for (Map.Entry<String, Ring> e : BY_GOD.entrySet()) {
            if (god != null && !god.equals(e.getKey())) continue;
            synchronized (e.getValue()) {
                sum += e.getValue().session;
            }
        }
        return sum / 1_000_000.0;
    }

    public static long requests() {
        long n = 0;
        for (Ring r : BY_GOD.values()) {
            synchronized (r) {
                n += r.requests;
            }
        }
        return n;
    }

    /** All-time spend (usage.csv before this session plus this session), or null when the file was not read. */
    public static Double allTime() {
        long b = before;
        return b < 0 ? null : b / 1_000_000.0 + session(null);
    }

    /** Every god's spend per minute for the last `minutes` minutes, oldest first. */
    public static double[] perMinute(int minutes, long now) {
        double[] out = new double[minutes];
        long end = (now / MINUTE + 1) * MINUTE; // the end of the current minute
        for (int i = 0; i < minutes; i++) {
            long to = end - (long) (minutes - 1 - i) * MINUTE - 1;
            long sum = 0;
            for (Ring r : BY_GOD.values()) sum += window(r, to - MINUTE, to);
            out[i] = sum / 1_000_000.0;
        }
        return out;
    }

    public static List<String> gods() {
        return new ArrayList<>(BY_GOD.keySet());
    }

    /**
     * Sums the cost_usd column of usage.csv as it stands (call once at start, off the server thread).
     * A missing or unreadable file leaves the all-time total unknown.
     */
    public static void loadBefore(Path usageCsv) {
        if (usageCsv == null || !Files.isRegularFile(usageCsv)) {
            before = 0;
            return;
        }
        long sum = 0;
        try (BufferedReader in = Files.newBufferedReader(usageCsv, StandardCharsets.UTF_8)) {
            String line = in.readLine(); // header
            while ((line = in.readLine()) != null) {
                sum += costOf(line);
            }
            // What this session already wrote is in the file too; it is counted by session().
            before = Math.max(0, sum - Math.round(session(null) * 1_000_000));
        } catch (IOException | RuntimeException e) {
            before = -1;
        }
    }

    /** The cost of one usage.csv row in micro-dollars (the second to last column), 0 when blank or damaged. */
    static long costOf(String row) {
        String[] cols = row.split(",", -1);
        if (cols.length < 2) return 0;
        String c = cols[cols.length - 2].trim();
        if (c.isEmpty()) return 0;
        try {
            double v = Double.parseDouble(c);
            return v > 0 && v < 1e6 ? Math.round(v * 1_000_000) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static void reset() {
        BY_GOD.clear();
        before = -1;
    }
}
