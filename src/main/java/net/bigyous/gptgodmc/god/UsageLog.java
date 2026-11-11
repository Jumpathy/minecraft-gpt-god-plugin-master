package net.bigyous.gptgodmc.god;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.utils.TokenTracker;

/**
 * Where token counts and costs go: the server console and plugins/gptgodmc/usage.csv, never the game.
 * One line per API request, so the owner can see exactly what each thought cost.
 */
public final class UsageLog {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String HEADER = "time,realm,model,purpose,input,cache_read,cache_write,output,"
            + "cost_usd,session_total_usd\n";
    private static boolean warnedFile;

    private UsageLog() {
    }

    /**
     * Records one request. `purpose` says what it was for ("thought #12 step 2", "folding the chronicle").
     * `input` is the uncached input; cache reads and writes are separate, as the API reports them.
     */
    public static void record(String model, String purpose, long input, long output, long cacheRead,
            long cacheWrite) {
        double cost = TokenTracker.recordUsage(model, input, output, cacheRead, cacheWrite);
        double total = TokenTracker.getEstimatedCost();
        String realm = Realm.current().id;
        // A model with no known price adds nothing to a god's spending (never a negative amount).
        Spending.add(realm, Math.max(0, cost), System.currentTimeMillis());
        Costs.add(realm, Math.max(0, cost), System.currentTimeMillis());
        if (GodSettings.logUsage()) {
            GPTGOD.LOGGER.info(line(realm, model, purpose, input, output, cacheRead, cacheWrite, cost, total));
        }
        if (GodSettings.usageFile()) {
            append(String.format(Locale.ROOT, "%s,%s,%s,%s,%d,%d,%d,%d,%s,%.6f%n", LocalDateTime.now().format(STAMP),
                    realm, model, purpose.replace(',', ';'), input, cacheRead, cacheWrite, output,
                    cost < 0 ? "" : String.format(Locale.ROOT, "%.6f", cost), total));
        }
    }

    /** e.g. `[God usage] thought #12 step 2 · sonnet-5-5 · in 1,234 (cache read 9,876, write 0) · out 321 · $0.0123 · session $1.2345` */
    static String line(String realm, String model, String purpose, long input, long output, long cacheRead,
            long cacheWrite, double cost, double total) {
        StringBuilder sb = new StringBuilder("[God usage] ");
        if (Realm.several()) {
            sb.append('<').append(realm).append("> ");
        }
        sb.append(purpose).append(" · ").append(model.replace("claude-", ""));
        sb.append(String.format(Locale.US, " · in %,d (cache read %,d, write %,d) · out %,d", input, cacheRead,
                cacheWrite, output));
        // Cost only where a price is known: a local model shows its tokens and nothing else.
        if (cost >= 0) {
            sb.append(" · ").append(String.format(Locale.US, "$%.4f", cost));
        } else if (model.startsWith("claude-")) {
            sb.append(" · price unknown");
        }
        if (cost >= 0 || TokenTracker.hasPricedCalls()) {
            sb.append(String.format(Locale.US, " · session $%.4f", total));
            if (!TokenTracker.isCostComplete()) {
                sb.append("+");
            }
        }
        return sb.toString();
    }

    /** The session totals as console lines (for `gptgod usage` typed in the console). */
    public static String[] summary() {
        long in = TokenTracker.getTotalInputTokens();
        long read = TokenTracker.getCacheReadTokens();
        long write = TokenTracker.getCacheWriteTokens();
        long out = TokenTracker.getTotalOutputTokens();
        return new String[] {
                String.format(Locale.US, "API requests this session: %,d", TokenTracker.getApiCallCount()),
                String.format(Locale.US, "Input tokens: %,d fresh + %,d read from cache + %,d written to cache", in,
                        read, write),
                String.format(Locale.US, "Output tokens: %,d", out),
                TokenTracker.hasPricedCalls() || TokenTracker.isCostComplete()
                        ? String.format(Locale.US, "Estimated cost: $%.4f%s", TokenTracker.getEstimatedCost(),
                                TokenTracker.isCostComplete() ? "" : " (plus calls to a model with no known price)")
                        : "Estimated cost: unknown (no price is set for this model; see llm.price-input)",
                "Per-request lines: " + (GodSettings.usageFile() ? "plugins/gptgodmc/usage.csv" : "usage file is off")
                        + (GodSettings.logUsage() ? " and this console ([God usage])" : "") };
    }

    private static synchronized void append(String row) {
        GPTGOD plugin = GPTGOD.get();
        if (plugin == null) {
            return; // unit tests
        }
        Path file = plugin.getDataFolder().toPath().resolve("usage.csv");
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, HEADER, StandardCharsets.UTF_8);
            }
            Files.writeString(file, row, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            if (!warnedFile) {
                warnedFile = true;
                GPTGOD.LOGGER.warn("Could not write usage.csv: " + e.getMessage());
            }
        }
    }
}
