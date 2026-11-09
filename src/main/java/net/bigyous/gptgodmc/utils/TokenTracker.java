package net.bigyous.gptgodmc.utils;

public class TokenTracker {
    private static long totalInputTokens = 0;
    private static long totalOutputTokens = 0;
    private static int apiCallCount = 0;

    public static synchronized void recordUsage(int inputTokens, int outputTokens) {
        totalInputTokens += inputTokens;
        totalOutputTokens += outputTokens;
        apiCallCount++;
    }

    public static long getTotalInputTokens() {
        return totalInputTokens;
    }

    public static long getTotalOutputTokens() {
        return totalOutputTokens;
    }

    public static int getApiCallCount() {
        return apiCallCount;
    }

    public static long getTotalTokens() {
        return totalInputTokens + totalOutputTokens;
    }

    public static void reset() {
        totalInputTokens = 0;
        totalOutputTokens = 0;
        apiCallCount = 0;
    }
}
