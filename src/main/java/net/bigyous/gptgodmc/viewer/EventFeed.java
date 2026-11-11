package net.bigyous.gptgodmc.viewer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.loggables.Loggable;

/**
 * Every event the gods were told about, for the mind viewer's World tab: what happened, which gods it
 * reached, and for each god whether it is still waiting, was seen in a thought (or heard mid-thought),
 * merged into an earlier event, put back after a failed thought, or dropped from a full buffer.
 *
 * One world event is logged once per god (each god gets its own copy); copies logged inside one
 * {@link #openGroup()} … {@link #closeGroup} share an entry. Pure and cheap; records nothing while the
 * viewer is off.
 */
public final class EventFeed {
    public static final int KEPT = 1500;

    /** One god's copy of an event. */
    public static final class Copy {
        public final String god;
        /** pending | seen | heard | merged | dropped */
        public String state = "pending";
        public int thought;
        public long thoughtId;
        public long at;
        /** For a merged copy: the entry it was folded into. */
        public long mergedInto;
        /** This god's wording, when it differs from the entry's. */
        public String text;

        Copy(String god) {
            this.god = god;
        }
    }

    public static final class Entry {
        public final long seq;
        public final long at;
        public final String category;
        public final String text;
        public final String attention;
        final Map<String, Copy> gods = new LinkedHashMap<>();
        long rev;

        Entry(long seq, long at, String category, String text, String attention) {
            this.seq = seq;
            this.at = at;
            this.category = category;
            this.text = text;
            this.attention = attention;
        }
    }

    private record Link(Entry entry, Copy copy) {
    }

    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();
    private static final Map<Loggable, Link> LINKS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Entry[]> GROUP = new ThreadLocal<>();
    private static long seq;
    private static long rev;

    private EventFeed() {
    }

    private static boolean on() {
        return MindTrace.isEnabled();
    }

    public static synchronized void clear() {
        ENTRIES.clear();
        LINKS.clear();
        seq = 0;
    }

    public static synchronized long revision() {
        return rev;
    }

    // ---- Recording --------------------------------------------------------------------------------

    /** Starts a group: the copies of one world event logged to several gods share one entry. */
    public static Object openGroup() {
        Entry[] previous = GROUP.get();
        GROUP.set(new Entry[1]);
        return previous == null ? Boolean.FALSE : previous;
    }

    public static void closeGroup(Object token) {
        if (token instanceof Entry[] previous) GROUP.set(previous);
        else GROUP.remove();
    }

    /**
     * An event logged to a god. `mergedInto` is the earlier event it was folded into (its text now
     * covers both), or null when it was added as its own line.
     */
    public static synchronized void logged(String god, Loggable event, Loggable mergedInto, String text, long at) {
        if (!on() || event == null) return;
        Entry[] group = GROUP.get();
        Entry entry = group != null ? group[0] : null;
        if (entry == null) {
            entry = new Entry(++seq, at, event.category().name().toLowerCase(), text, safeAttention(event));
            ENTRIES.addLast(entry);
            while (ENTRIES.size() > KEPT) ENTRIES.removeFirst();
            if (group != null) group[0] = entry;
        }
        Copy c = entry.gods.computeIfAbsent(god, Copy::new);
        if (text != null && !text.equals(entry.text)) c.text = text;
        if (mergedInto != null) {
            c.state = "merged";
            Link into = LINKS.get(mergedInto);
            if (into != null) c.mergedInto = into.entry.seq;
        } else {
            LINKS.put(event, new Link(entry, c));
        }
        entry.rev = ++rev;
    }

    /** A god took these events into a thought (`heard` = slipped into a thought already running). */
    public static synchronized void seen(List<Loggable> events, int thought, long thoughtId, boolean heard) {
        if (!on() || events == null) return;
        long now = System.currentTimeMillis();
        for (Loggable l : events) {
            Link k = LINKS.get(l);
            if (k == null) continue;
            k.copy.state = heard ? "heard" : "seen";
            k.copy.thought = thought;
            k.copy.thoughtId = thoughtId;
            k.copy.at = now;
            k.entry.rev = ++rev;
        }
    }

    /** A thought failed and gave these events back: they wait for the next one. */
    public static synchronized void restored(List<Loggable> events) {
        if (!on() || events == null) return;
        for (Loggable l : events) {
            Link k = LINKS.get(l);
            if (k == null) continue;
            k.copy.state = "pending";
            k.copy.thought = 0;
            k.copy.thoughtId = 0;
            k.entry.rev = ++rev;
        }
    }

    /** A full buffer dropped this event before any god saw it. */
    public static synchronized void dropped(Loggable event) {
        if (!on() || event == null) return;
        Link k = LINKS.get(event);
        if (k == null) return;
        k.copy.state = "dropped";
        k.entry.rev = ++rev;
    }

    private static String safeAttention(Loggable l) {
        try {
            return l.attentionReason();
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- Reading ----------------------------------------------------------------------------------

    /** Entries changed after a revision, oldest first, at most `limit` (the newest). */
    public static synchronized JsonObject since(long after, int limit) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : ENTRIES) {
            if (e.rev > after) out.add(e);
        }
        if (out.size() > limit) out = out.subList(out.size() - limit, out.size());
        JsonArray arr = new JsonArray();
        for (Entry e : out) arr.add(json(e));
        JsonObject o = new JsonObject();
        o.addProperty("rev", rev);
        o.add("events", arr);
        return o;
    }

    static JsonObject json(Entry e) {
        JsonObject o = new JsonObject();
        o.addProperty("seq", e.seq);
        o.addProperty("at", e.at);
        o.addProperty("rev", e.rev);
        o.addProperty("category", e.category);
        o.addProperty("text", e.text);
        if (e.attention != null) o.addProperty("attention", e.attention);
        JsonArray gods = new JsonArray();
        for (Copy c : e.gods.values()) {
            JsonObject g = new JsonObject();
            g.addProperty("god", c.god);
            g.addProperty("state", c.state);
            if (c.thought != 0) g.addProperty("thought", c.thought);
            if (c.thoughtId != 0) g.addProperty("thoughtId", c.thoughtId);
            if (c.at != 0) g.addProperty("at", c.at);
            if (c.text != null) g.addProperty("text", c.text);
            if (c.mergedInto != 0) g.addProperty("mergedInto", c.mergedInto);
            gods.add(g);
        }
        o.add("gods", gods);
        return o;
    }
}
