package net.bigyous.gptgodmc.god;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.bigyous.gptgodmc.EventLogger;

/**
 * The spoken conversation in one realm, as a single timeline: what mortals said and prayed and what
 * the god said back, in order, kept for a few minutes across thoughts. Perception shows it so the god
 * can tell who answered whom (chat, prayers and its own replies used to be shown apart). Thread-safe.
 */
public final class Conversation {
    /** How far back the timeline reaches. */
    public static final long WINDOW_MS = 10 * 60_000L;
    public static final int MAX_LINES = 30;
    public static final int MAX_CHARS = 3_000;

    /** One line: when, the text (already "Name said in chat: ..." / "You whispered to X: ..."), and whose. */
    public record Line(long at, String text, boolean mine) {
    }

    private final Deque<Line> lines = new ArrayDeque<>();
    /** When the god last looked (lines after it are NEW). */
    private long lastLook = 0;

    /** A mortal's words (chat, voice or prayer), as the event log phrases them. */
    public void heard(long at, String text) {
        add(new Line(at, text, false));
    }

    /** The god's own words. */
    public void spoke(long at, String text) {
        add(new Line(at, text, true));
    }

    private synchronized void add(Line line) {
        if (line.text() == null || line.text().isBlank()) return;
        lines.addLast(line);
        while (lines.size() > MAX_LINES * 2) lines.removeFirst();
    }

    /**
     * The timeline for a perception, oldest first, or "" when nothing was said lately.
     *
     * @param markSeen true for a real look: later lines count as NEW only until then
     */
    public synchronized String render(long now, boolean markSeen) {
        while (!lines.isEmpty() && now - lines.peekFirst().at() > WINDOW_MS) lines.removeFirst();
        List<String> out = new ArrayList<>();
        int chars = 0;
        // Newest first, so the cap keeps the latest words; reversed below.
        var it = lines.descendingIterator();
        while (it.hasNext() && out.size() < MAX_LINES) {
            Line l = it.next();
            String row = "[" + EventLogger.ago(Duration.ofMillis(Math.max(0, now - l.at()))) + "] "
                    + (l.mine() ? "" : l.at() > lastLook ? "NEW " : "") + l.text();
            if (chars + row.length() > MAX_CHARS && !out.isEmpty()) break;
            out.add(row);
            chars += row.length() + 1;
        }
        if (markSeen) lastLook = now;
        if (out.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = out.size() - 1; i >= 0; i--) sb.append(out.get(i)).append("\n");
        return sb.toString();
    }

    public synchronized void clear() {
        lines.clear();
        lastLook = 0;
    }
}
