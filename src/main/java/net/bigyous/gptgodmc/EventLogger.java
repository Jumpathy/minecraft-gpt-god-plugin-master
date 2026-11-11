package net.bigyous.gptgodmc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.loggables.Loggable;
import net.bigyous.gptgodmc.loggables.Loggable.Category;

/**
 * Everything mortals do and say between two of the god's thoughts. Each realm has its own buffer; the
 * static methods act on the current realm's (see {@link Realm#current()}). Thread-safe.
 */
public final class EventLogger {
    /** How far back a new event looks for one it can merge with. */
    private static final int COMBINE_WINDOW = 4;
    private static final int MAX_EVENTS = 600;

    private EventLogger() {
    }

    /** One god's pending events. */
    public static final class Buffer {
        private final List<Loggable> loggables = new ArrayList<>();
        private int droppedSinceDrain = 0;

        /** Adds an event; returns the earlier event it was merged into, or null when added on its own. */
        synchronized Loggable add(Loggable event) {
            Loggable into = null;
            int start = Math.max(0, loggables.size() - COMBINE_WINDOW);
            for (int i = loggables.size() - 1; i >= start && into == null; i--) {
                Loggable existing = loggables.get(i);
                if (existing.category() == Category.EVENT && event.category() == Category.EVENT
                        && existing.combine(event)) {
                    into = existing;
                }
            }
            if (into == null) {
                loggables.add(event);
                if (loggables.size() > MAX_EVENTS) removeOldestLowPriority();
            }
            return into;
        }

        /** True when something is waiting that should make the god look (passive events never do). */
        public synchronized boolean hasPending() {
            for (Loggable l : loggables) {
                if (!l.passive()) return true;
            }
            return false;
        }

        public synchronized int pendingCount() {
            return loggables.size();
        }

        synchronized Taken copy() {
            return new Taken(new ArrayList<>(loggables), droppedSinceDrain);
        }

        synchronized Taken take() {
            Taken t = copy();
            loggables.clear();
            droppedSinceDrain = 0;
            return t;
        }

        /** Removes only the words aimed at the god: prayers, directives, and speech that names it. */
        synchronized Taken takeUrgent() {
            List<Loggable> out = new ArrayList<>();
            Iterator<Loggable> it = loggables.iterator();
            while (it.hasNext()) {
                Loggable l = it.next();
                boolean urgent = l.category() == Category.PRAYER || l.category() == Category.DIRECTIVE
                        || (l.category() == Category.SPEECH && l.attentionReason() != null);
                if (urgent) {
                    out.add(l);
                    it.remove();
                }
            }
            return new Taken(out, 0);
        }

        synchronized void restore(Taken taken) {
            loggables.addAll(0, taken.loggables());
            droppedSinceDrain += taken.omitted();
            while (loggables.size() > MAX_EVENTS) removeOldestLowPriority();
        }

        synchronized void reset() {
            loggables.clear();
            droppedSinceDrain = 0;
        }

        private void removeOldestLowPriority() {
            for (int i = 0; i < loggables.size(); i++) {
                if (loggables.get(i).category() == Category.EVENT) {
                    net.bigyous.gptgodmc.viewer.EventFeed.dropped(loggables.remove(i));
                    droppedSinceDrain++;
                    return;
                }
            }
            net.bigyous.gptgodmc.viewer.EventFeed.dropped(loggables.remove(0));
            droppedSinceDrain++;
        }
    }

    private static Buffer buffer() {
        return Realm.current().events();
    }

    // ---- Logging -------------------------------------------------------------------------------

    /** Logs an event to the current realm. The factory may return null or throw; both mean "skip". */
    public static void log(Supplier<? extends Loggable> factory) {
        try {
            addLoggable(factory.get());
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.debug("Skipped an event that failed to log", e);
        }
    }

    /**
     * Logs an event to every god of that world; ignored where no god has jurisdiction. The factory runs
     * once per god (with that god bound), so each keeps its own copy to merge and read in its own words.
     */
    public static void log(World world, Supplier<? extends Loggable> factory) {
        Object group = net.bigyous.gptgodmc.viewer.EventFeed.openGroup();
        try {
            for (Realm realm : Realm.godsOf(world)) {
                realm.run(() -> log(factory));
            }
        } finally {
            net.bigyous.gptgodmc.viewer.EventFeed.closeGroup(group);
        }
    }

    /** Logs a plain line to every god of that world. */
    public static void addLoggable(World world, String text) {
        log(world, () -> new net.bigyous.gptgodmc.loggables.GenericEventLoggable(text));
    }

    public static void addLoggable(Loggable event) {
        if (event == null) return;
        String text = event.getLog();
        if (text == null) return;
        Loggable mergedInto = buffer().add(event);
        try {
            net.bigyous.gptgodmc.viewer.EventFeed.logged(Realm.current().id, event, mergedInto, text,
                    event.getRawInstant().toEpochMilli());
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.debug("Mind viewer could not record an event", e);
        }
        if (event.category() == Category.SPEECH || event.category() == Category.PRAYER) {
            try {
                Realm.current().conversation().heard(event.getRawInstant().toEpochMilli(), text);
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.debug("Could not add to the conversation", e);
            }
        }
        String attention = event.attentionReason();
        if (event.remembered()) remember(event, text, attention != null);
        if (attention != null) {
            // Words aimed at the god deserve an answer within a moment; deeds can wait a beat.
            God.requestAttention(attention, event.category() != Category.EVENT);
        }
    }

    /** Every event goes to the god's annals, and the notable ones become deeds (see Memory.noticeEvent). */
    private static void remember(Loggable event, String text, boolean addressed) {
        try {
            Realm.current().memory().noticeEvent(event.category().name(), text, addressed, onlineNames());
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.debug("Could not remember an event", e);
        }
    }

    /** Online mortals' names when called on the server thread; empty elsewhere (and without a server). */
    private static List<String> onlineNames() {
        if (Bukkit.getServer() == null || !Bukkit.isPrimaryThread()) return List.of();
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        return out;
    }

    // ---- Reading -------------------------------------------------------------------------------

    public static boolean hasPending() {
        return buffer().hasPending();
    }

    public static int pendingCount() {
        return buffer().pendingCount();
    }

    /** The events since the god last looked, split by kind, oldest first. */
    public record Drained(List<String> events, List<String> speech, List<String> prayers,
            List<String> directives, int omitted) {
        public boolean isEmpty() {
            return events.isEmpty() && speech.isEmpty() && prayers.isEmpty() && directives.isEmpty();
        }
    }

    /** Events taken out of the log, so they can be put back if the god fails to look at them. */
    public record Taken(List<Loggable> loggables, int omitted) {
    }

    /** Removes and returns everything logged so far, trimmed to the perception budget. */
    public static Drained drain() {
        return render(take());
    }

    /** Like drain() but leaves the events in place. */
    public static Drained peek() {
        return render(buffer().copy());
    }

    /** Removes everything logged so far, unrendered. Pair with {@link #restore} on failure. */
    public static Taken take() {
        return buffer().take();
    }

    /** Removes only prayers, directives and speech that names the god (for answering mid-thought). */
    public static Taken takeUrgent() {
        return buffer().takeUrgent();
    }

    /** Puts taken events back (they keep their original timestamps). */
    public static void restore(Taken taken) {
        if (taken != null) buffer().restore(taken);
    }

    public static Drained render(Taken taken) {
        List<Loggable> snapshot = new ArrayList<>(taken.loggables());
        snapshot.sort(Comparator.comparing(Loggable::getRawInstant));
        int omitted = taken.omitted();

        Instant now = Instant.now();
        int budget = GodSettings.perceptionCharBudget();
        List<String[]> lines = new ArrayList<>();
        int total = 0;
        for (Loggable l : snapshot) {
            String text = l.getLog();
            if (text == null) {
                continue;
            }
            String line = "[" + ago(Duration.between(l.getRawInstant(), now)) + "] " + text;
            lines.add(new String[] { l.category().name(), line });
            total += line.length() + 1;
        }
        // Over budget: drop the oldest ordinary events first, never words or directives.
        for (int i = 0; i < lines.size() && total > budget; i++) {
            if (lines.get(i)[0].equals(Category.EVENT.name())) {
                total -= lines.get(i)[1].length() + 1;
                lines.remove(i--);
                omitted++;
            }
        }

        List<String> events = new ArrayList<>();
        List<String> speech = new ArrayList<>();
        List<String> prayers = new ArrayList<>();
        List<String> directives = new ArrayList<>();
        for (String[] l : lines) {
            switch (Category.valueOf(l[0])) {
                case EVENT -> events.add(l[1]);
                case SPEECH -> speech.add(l[1]);
                case PRAYER -> prayers.add(l[1]);
                case DIRECTIVE -> directives.add(l[1]);
            }
        }
        return new Drained(events, speech, prayers, directives, omitted);
    }

    public static void reset() {
        buffer().reset();
    }

    public static String ago(Duration d) {
        long s = Math.max(0, d.getSeconds());
        if (s < 60) {
            return s + "s ago";
        }
        if (s < 3600) {
            return (s / 60) + "m" + (s % 60 > 0 ? (s % 60) + "s" : "") + " ago";
        }
        return (s / 3600) + "h" + ((s % 3600) / 60) + "m ago";
    }
}
