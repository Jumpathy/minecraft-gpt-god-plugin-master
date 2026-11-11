package net.bigyous.gptgodmc.utils;

import java.util.Map;

/** Running token totals and an approximate dollar cost for the current session. */
public final class TokenTracker {
    /** USD per million tokens: input, output, cache read, cache write (5 minute). */
    private static final Map<String, double[]> PRICES = Map.of(
            "claude-sonnet-5-5", new double[] { 2.00, 10.00, 0.20, 2.50 },
            "claude-sonnet-5", new double[] { 2.00, 10.00, 0.20, 2.50 },
            "claude-haiku-5-5", new double[] { 0.10, 0.50, 0.01, 0.125 },
            "claude-opus-5-5", new double[] { 4.00, 20.00, 0.20, 5.00 },
            "claude-opus-5", new double[] { 5.00, 25.00, 0.50, 6.25 },
            "claude-fable-5-1", new double[] { 10.00, 50.00, 0.25, 12.50 },
            "claude-fable-5-2", new double[] { 8.00, 40.00, 0.20, 10.00 });

    private static long totalInputTokens = 0;
    private static long totalOutputTokens = 0;
    private static long cacheReadTokens = 0;
    private static long cacheWriteTokens = 0;
    private static int apiCallCount = 0;
    private static double estimatedCost = 0;
    private static boolean costComplete = true;

    /** Prices for models not in the table (set from config for other providers); null = unknown. */
    private static volatile java.util.function.Function<String, double[]> fallbackPrice = model -> null;
    private static boolean anyPriced = false;

    private TokenTracker() {
    }

    /** Where to ask for the price of a model the table does not have: input, output, cache read, cache write. */
    public static void setFallbackPrice(java.util.function.Function<String, double[]> prices) {
        fallbackPrice = prices == null ? model -> null : prices;
    }

    /** True once some call this session had a known price (so a session cost is worth showing). */
    public static synchronized boolean hasPricedCalls() {
        return anyPriced;
    }

    /**
     * Adds one API call to the session totals. `input` is the uncached input only; cache reads and
     * writes are counted (and priced) separately. Returns what this call cost in USD, or -1 when the
     * model has no known price.
     */
    public static synchronized double recordUsage(String model, long input, long output, long cacheRead,
            long cacheWrite) {
        totalInputTokens += input;
        totalOutputTokens += output;
        cacheReadTokens += cacheRead;
        cacheWriteTokens += cacheWrite;
        apiCallCount++;
        double cost = costOf(model, input, output, cacheRead, cacheWrite);
        if (cost < 0) {
            costComplete = false;
            return -1;
        }
        estimatedCost += cost;
        anyPriced = true;
        return cost;
    }

    /** Web searches cost $10 per 1,000, on top of tokens. */
    public static final double WEB_SEARCH_USD = 0.01;

    /** Adds a cost that is not tokens (web searches) to the session total. */
    public static synchronized void addCost(double usd) {
        estimatedCost += usd;
    }

    /** The price of one call in USD, or -1 for a model that is not in the price table. */
    public static double costOf(String model, long input, long output, long cacheRead, long cacheWrite) {
        double[] p = PRICES.get(model);
        if (p == null) {
            try {
                p = fallbackPrice.apply(model);
            } catch (RuntimeException e) {
                p = null;
            }
        }
        if (p == null || p.length < 4) {
            return -1;
        }
        return (input * p[0] + output * p[1] + cacheRead * p[2] + cacheWrite * p[3]) / 1_000_000.0;
    }

    public static synchronized long getTotalInputTokens() {
        return totalInputTokens;
    }

    public static synchronized long getTotalOutputTokens() {
        return totalOutputTokens;
    }

    public static synchronized long getCacheReadTokens() {
        return cacheReadTokens;
    }

    public static synchronized long getCacheWriteTokens() {
        return cacheWriteTokens;
    }

    public static synchronized int getApiCallCount() {
        return apiCallCount;
    }

    public static synchronized double getEstimatedCost() {
        return estimatedCost;
    }

    /** False when some calls used a model without a known price. */
    public static synchronized boolean isCostComplete() {
        return costComplete;
    }

    public static synchronized void reset() {
        totalInputTokens = 0;
        totalOutputTokens = 0;
        cacheReadTokens = 0;
        cacheWriteTokens = 0;
        apiCallCount = 0;
        estimatedCost = 0;
        costComplete = true;
        anyPriced = false;
    }
}
