package net.bigyous.gptgodmc.god;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each god spent on the API in the last hour (from the price table in TokenTracker), so a god over
 * pantheon.budget.usd-per-hour-per-god grows weary and answers only those who call on it. Costs are
 * never shown in game: only the console and usage.csv carry money.
 */
public final class Spending {
    private static final long HOUR = 3_600_000L;
    private static final Map<String, Deque<long[]>> BY_GOD = new ConcurrentHashMap<>();

    private Spending() {
    }

    /** Records one request's cost (in micro-dollars, so it fits a long). */
    public static void add(String god, double usd, long now) {
        if (god == null || !(usd > 0)) return;
        Deque<long[]> d = BY_GOD.computeIfAbsent(god, k -> new ArrayDeque<>());
        synchronized (d) {
            d.addLast(new long[] { now, Math.round(usd * 1_000_000) });
            trim(d, now);
        }
    }

    /** USD this god spent in the hour before now. */
    public static double lastHour(String god, long now) {
        Deque<long[]> d = BY_GOD.get(god);
        if (d == null) return 0;
        synchronized (d) {
            trim(d, now);
            long sum = 0;
            for (long[] e : d) sum += e[1];
            return sum / 1_000_000.0;
        }
    }

    /** True when the god has spent its hourly budget (0 = no limit). */
    public static boolean weary(String god, double budget, long now) {
        return budget > 0 && lastHour(god, now) >= budget;
    }

    static void reset() {
        BY_GOD.clear();
    }

    private static void trim(Deque<long[]> d, long now) {
        while (!d.isEmpty() && now - d.peekFirst()[0] > HOUR) d.removeFirst();
    }
}
