package net.bigyous.gptgodmc.god;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * A memory surfaces: in a quiet moment one old thing the god remembers comes back to it because it fits
 * the present. A mortal who is here once did this; this happened a week ago at this hour; a mortal stands
 * by something the god built; a favoured mortal has been gone for days. The server picks it (no model is
 * asked), shows one line under "## A memory surfaces", and then waits {@code awareness.memory-gap-minutes}
 * before the next, never repeating one of the last twelve. Choosing is pure and unit tested.
 */
public final class Reminiscence {
    private Reminiscence() {
    }

    /** The longest a surfaced memory may be (about 55 tokens). */
    public static final int CAP = 220;
    static final long HOUR = 3_600_000L, DAY = 24 * HOUR;
    /** A deed younger than this is not yet a memory. */
    static final long OLD_ENOUGH_MS = 3 * HOUR;
    /** Days after which "at this hour" is worth remarking on. */
    static final int[] ANNIVERSARIES = { 1, 2, 3, 7, 14, 30, 60, 90, 180, 365 };
    static final long AROUND_THE_HOUR_MS = 45 * 60_000L;
    static final long LONG_GONE_MS = 3 * DAY;
    static final int NEAR_A_WORK = 24;
    private static final int REMEMBERED = 12;

    /** A mortal who is here, and where. */
    public record Here(String name, String world, int x, int y, int z) {
    }

    /** One memory that fits the moment: a key (so it is not repeated), the line, and how well it fits. */
    public record Recalled(String key, String line, int weight) {
    }

    // ---- Choosing (pure) ----------------------------------------------------------------------------

    /** True for the god's own time: a quiet moment or its regular watch, when nothing calls for it by name. */
    public static boolean quiet(String trigger) {
        return trigger != null && (trigger.startsWith("a quiet moment") || trigger.startsWith("your regular watch"));
    }

    /** Things the server wrote down that are records, not memories worth coming back. */
    private static boolean dull(String deed) {
        return deed.startsWith("you: ") || deed.startsWith("was given your quest");
    }

    /** "Steve died: fell from a high place" (no full stop of its own). */
    private static String told(String name, String deed) {
        String d = deed.strip();
        return name + " " + (d.endsWith(".") ? d.substring(0, d.length() - 1) : d);
    }

    private static String ago(int days) {
        return switch (days) {
            case 1 -> "A day ago";
            case 7 -> "A week ago";
            case 14 -> "Two weeks ago";
            case 30 -> "A month ago";
            case 365 -> "A year ago";
            default -> days + " days ago";
        };
    }

    /** Every memory that fits this moment. The memory's lock must be held (or the memory not shared). */
    public static List<Recalled> candidates(Memory m, List<Here> here, long now) {
        List<Recalled> out = new ArrayList<>();
        for (Memory.Subject s : m.subjects.values()) {
            if (s.name == null || "world".equalsIgnoreCase(s.name)) continue;
            boolean present = here.stream().anyMatch(h -> h.name().equalsIgnoreCase(s.name));
            for (int i = 0; i < s.deeds.size(); i++) {
                Memory.Note d = s.deeds.get(i);
                if (d == null || d.text == null || dull(d.text)) continue;
                long age = now - d.at;
                String key = s.name + "@" + d.at;
                // This very hour, some days ago.
                for (int days : ANNIVERSARIES) {
                    if (Math.abs(age - days * DAY) <= AROUND_THE_HOUR_MS) {
                        out.add(new Recalled(key, ago(days) + " at this hour: " + told(s.name, d.text) + ".", 5));
                    }
                }
                if (!present || age < OLD_ENOUGH_MS) continue;
                if (i == 0 && s.deeds.size() > 1) {
                    out.add(new Recalled(key, "The first thing you remember of " + s.name + ", who is here now: "
                            + told(s.name, d.text) + " (" + Memory.age(age) + ").", 3));
                } else if (i < s.deeds.size() - 1) {
                    // Their newest deed is already on their line; older ones are not shown in a quiet look.
                    out.add(new Recalled(key, s.name + " is here now. You remember: " + told(s.name, d.text) + " ("
                            + Memory.age(age) + ").", 2));
                }
            }
            // Someone who mattered and has been gone a long while.
            if (!present && s.lastSeen > 0 && now - s.lastSeen >= LONG_GONE_MS && Math.abs(s.favor) >= 3) {
                Memory.Note last = null;
                for (Memory.Note d : s.deeds) {
                    if (d != null && d.text != null && !dull(d.text)) last = d;
                }
                out.add(new Recalled(s.name + "@gone", "You have not seen " + s.name + " (favor "
                        + (s.favor > 0 ? "+" : "") + s.favor + ") for " + (now - s.lastSeen) / DAY + " days."
                        + (last == null ? "" : " The last you knew: " + told(s.name, last.text) + "."), 3));
            }
        }
        // A mortal stands by something the god made.
        for (Memory.Work w : m.works) {
            if (w.name == null || w.world == null || now - w.builtAt < OLD_ENOUGH_MS) continue;
            for (Here h : here) {
                if (!w.world.equalsIgnoreCase(h.world())) continue;
                long dx = h.x() - w.x, dz = h.z() - w.z;
                if (dx * dx + dz * dz > NEAR_A_WORK * NEAR_A_WORK || Math.abs(h.y() - w.y) > NEAR_A_WORK) continue;
                out.add(new Recalled("work:" + w.id + ":" + h.name(), h.name() + " stands by your " + w.name
                        + ", which you raised " + Memory.age(now - w.builtAt) + ".", 4));
            }
        }
        for (int i = 0; i < out.size(); i++) {
            Recalled r = out.get(i);
            if (r.line().length() > CAP) out.set(i, new Recalled(r.key(), Memory.clip(r.line(), CAP), r.weight()));
        }
        return out;
    }

    /**
     * The best-fitting memory that was not surfaced lately, or null. Among equals the seed chooses, so the
     * same moment gives the same memory and different moments give different ones.
     */
    public static Recalled pick(List<Recalled> candidates, Collection<String> lately, long seed) {
        List<Recalled> best = new ArrayList<>();
        for (Recalled r : candidates) {
            if (lately.contains(r.key())) continue;
            if (!best.isEmpty() && r.weight() < best.get(0).weight()) continue;
            if (!best.isEmpty() && r.weight() > best.get(0).weight()) best.clear();
            best.add(r);
        }
        return best.isEmpty() ? null : best.get((int) Math.floorMod(seed, best.size()));
    }

    /** When memories last surfaced for one god, and which. */
    static final class Recent {
        long at;
        final Deque<String> keys = new ArrayDeque<>();
        /** The line last shown, for the mind viewer and the God Manager. */
        String line;

        boolean due(long now, long gapMs) {
            return at == 0 || now - at >= gapMs;
        }

        void surfaced(Recalled r, long now) {
            at = now;
            line = r.line();
            keys.addLast(r.key());
            while (keys.size() > REMEMBERED) keys.removeFirst();
        }
    }

    // ---- In the perception ------------------------------------------------------------------------

    private static final Map<String, Recent> RECENT = new ConcurrentHashMap<>();

    /** The memory that last surfaced for this god and when, or null: {line, epoch millis as text}. */
    public static String[] last(String realmId) {
        Recent r = RECENT.get(realmId);
        if (r == null || r.line == null) return null;
        synchronized (r) {
            return new String[] { r.line, String.valueOf(r.at) };
        }
    }

    /**
     * "## A memory surfaces" for a quiet thought, or "". Server thread.
     *
     * @param preview only looking (the debug bridge): nothing is marked as surfaced
     */
    public static String perceive(Realm realm, List<Player> mortals, String trigger, boolean preview) {
        try {
            if (!GodSettings.awarenessMemories() || Llm.lite() || !quiet(trigger)) return "";
            long now = System.currentTimeMillis();
            Recent recent = RECENT.computeIfAbsent(realm.id, k -> new Recent());
            synchronized (recent) {
                if (!recent.due(now, GodSettings.awarenessMemoryGapMs())) return "";
                List<Here> here = new ArrayList<>();
                for (Player p : mortals) {
                    if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
                    Location l = p.getLocation();
                    here.add(new Here(p.getName(), l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ()));
                }
                Memory m = realm.memory();
                Recalled r;
                synchronized (m) {
                    r = pick(candidates(m, here, now), recent.keys, now / 60_000);
                }
                if (r == null) return "";
                if (!preview) recent.surfaced(r, now);
                return "## A memory surfaces (yours to dwell on, speak of, or let pass)\n" + r.line() + "\n\n";
            }
        } catch (RuntimeException e) {
            return "";
        }
    }
}
