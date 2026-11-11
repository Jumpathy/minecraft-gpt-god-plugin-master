package net.bigyous.gptgodmc.god;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * Rolling record of what the gods perceived, thought and did. Feeds the console log,
 * `/god status`, `/god journal` and the debug bridge used by the test harness.
 *
 * Every entry belongs to the god that was acting when it was written; reading returns only the
 * current god's entries (see {@link Realm#current()}). Token usage is deliberately not kept here —
 * that goes to {@link UsageLog}, which players and operators cannot read in game.
 */
public final class Journal {
    public enum Kind {
        PERCEPTION, THOUGHT, TOOL, SPEECH, ERROR, INFO, CYCLE;

        public String id() {
            return name().toLowerCase();
        }
    }

    public record Entry(long seq, long time, Kind kind, String text, String realm) {
    }

    private static final int CAPACITY = 1500;
    private static final Deque<Entry> entries = new ArrayDeque<>();
    private static long seq = 0;

    private Journal() {
    }

    public static synchronized long add(Kind kind, String text) {
        String realm = Realm.current().id;
        seq++;
        entries.addLast(new Entry(seq, System.currentTimeMillis(), kind, text, realm));
        while (entries.size() > CAPACITY) {
            entries.removeFirst();
        }
        log(kind, text, realm);
        return seq;
    }

    /** The newest `limit` entries of the current god after sinceSeq. */
    public static synchronized List<Entry> since(long sinceSeq, int limit) {
        String realm = Realm.current().id;
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.seq() > sinceSeq && e.realm().equals(realm)) {
                out.add(e);
            }
        }
        if (out.size() > limit) {
            return new ArrayList<>(out.subList(out.size() - limit, out.size()));
        }
        return out;
    }

    /** The oldest `limit` entries of the current god after sinceSeq (for paging forward). */
    public static synchronized List<Entry> firstAfter(long sinceSeq, int limit) {
        String realm = Realm.current().id;
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.seq() > sinceSeq && e.realm().equals(realm)) {
                out.add(e);
                if (out.size() >= limit) break;
            }
        }
        return out;
    }

    public static synchronized long latestSeq() {
        return seq;
    }

    /** The current god's last n entries, oldest first. */
    public static synchronized List<Entry> last(int n) {
        return since(0, n);
    }

    private static void log(Kind kind, String text, String realm) {
        String oneLine = text.length() > 600 ? text.substring(0, 600) + "…" : text;
        // With one god the console reads "[God thinks] …"; with several, "[God:creative thinks] …".
        String who = Realm.several() ? "God:" + realm : "God";
        switch (kind) {
            case ERROR -> GPTGOD.LOGGER.warn("[" + who + "] " + oneLine);
            case PERCEPTION -> {
                if (GodSettings.logRequests()) {
                    GPTGOD.LOGGER.info("[" + who + " perceives]\n" + text);
                }
            }
            case THOUGHT -> GPTGOD.LOGGER.info("[" + who + " thinks] " + oneLine);
            case TOOL -> GPTGOD.LOGGER.info("[" + who + " acts] " + oneLine);
            case SPEECH -> GPTGOD.LOGGER.info("[" + who + " speaks] " + oneLine);
            default -> GPTGOD.LOGGER.info("[" + who + "] " + oneLine);
        }
    }
}
