package net.bigyous.gptgodmc.god.hooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * What happened to the land, from CoreProtect's records (only on servers with CoreProtect): who broke,
 * placed, used or killed what near a place, and taking one mortal's changes back. {@link #summarize} is
 * pure; the part that names CoreProtect classes is {@code CoreProtectBridge}, loaded by name.
 */
public final class LandHistory {
    private LandHistory() {
    }

    /**
     * @param player who did it; names starting with # are not mortals (#creeper, #tnt, #fire)
     * @param action break, place, click or kill
     * @param what   the block or creature
     * @param at     when, epoch milliseconds
     */
    public record Entry(String player, String action, String what, int x, int y, int z, long at, boolean rolledBack) {
    }

    /** CoreProtect's database. Never on the server thread. */
    public interface Source {
        /** @param player only this mortal's acts, or null for everyone's */
        List<Entry> lookup(Location centre, int radius, int seconds, String player);

        /** Takes this mortal's block changes back; returns how many. */
        int rollback(Location centre, int radius, int seconds, String player);
    }

    private static Source source;
    private static boolean failed;

    public static boolean installed() {
        return Hooks.has("CoreProtect");
    }

    public static boolean enabled() {
        return Hooks.on("coreprotect.enabled");
    }

    public static boolean rollbackAllowed() {
        return Hooks.on("coreprotect.rollback");
    }

    public static int maxRadius() {
        return Hooks.number("coreprotect.max-radius", 20, 1, 64);
    }

    public static int maxHours() {
        return Hooks.number("coreprotect.max-hours", 72, 1, 720);
    }

    /** The source, or null when CoreProtect is missing or its API is not one this can use. */
    public static synchronized Source source() {
        if (source != null || failed || !installed()) return source;
        try {
            source = (Source) Class.forName("net.bigyous.gptgodmc.god.hooks.CoreProtectBridge").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            failed = true;
            GPTGOD.LOGGER.warn("CoreProtect is installed but the god cannot read it: " + e);
        }
        return source;
    }

    private static String verb(String action) {
        return switch (action == null ? "" : action) {
            case "break" -> "broke";
            case "place" -> "placed";
            case "click" -> "used";
            case "kill" -> "killed";
            default -> action;
        };
    }

    /**
     * The records as the god reads them: one line per actor (most active first) with what they did and to
     * what, then the latest few acts with how long ago.
     */
    public static String summarize(List<Entry> entries, long now, int maxActors, int maxRecent) {
        if (entries == null || entries.isEmpty()) return "Nothing is recorded there in that time.";
        // actor -> verb -> thing -> count
        Map<String, Map<String, Map<String, Integer>>> by = new LinkedHashMap<>();
        Map<String, Integer> totals = new LinkedHashMap<>();
        int undone = 0;
        for (Entry e : entries) {
            if (e.rolledBack()) {
                undone++;
                continue;
            }
            by.computeIfAbsent(e.player(), k -> new LinkedHashMap<>()).computeIfAbsent(verb(e.action()), k -> new LinkedHashMap<>())
                    .merge(e.what(), 1, Integer::sum);
            totals.merge(e.player(), 1, Integer::sum);
        }
        List<String> actors = new ArrayList<>(totals.keySet());
        actors.sort(Comparator.comparingInt((String a) -> -totals.get(a)).thenComparing(a -> a.startsWith("#")).thenComparing(a -> a));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < actors.size() && i < maxActors; i++) {
            String actor = actors.get(i);
            sb.append(actor.startsWith("#") ? actor.substring(1) + " (not a mortal)" : actor).append(": ");
            boolean first = true;
            for (Map.Entry<String, Map<String, Integer>> v : by.get(actor).entrySet()) {
                if (!first) sb.append("; ");
                first = false;
                int sum = v.getValue().values().stream().mapToInt(Integer::intValue).sum();
                List<Map.Entry<String, Integer>> things = new ArrayList<>(v.getValue().entrySet());
                things.sort(Comparator.comparingInt((Map.Entry<String, Integer> t) -> -t.getValue()).thenComparing(Map.Entry::getKey));
                sb.append(v.getKey()).append(' ').append(sum).append(" (");
                for (int t = 0; t < things.size() && t < 4; t++) {
                    if (t > 0) sb.append(", ");
                    sb.append(things.get(t).getKey()).append(' ').append(things.get(t).getValue());
                }
                if (things.size() > 4) sb.append(", …");
                sb.append(')');
            }
            sb.append('\n');
        }
        if (actors.size() > maxActors) sb.append("and ").append(actors.size() - maxActors).append(" more.\n");
        if (undone > 0) sb.append(undone).append(" act(s) there were already taken back.\n");
        List<Entry> latest = new ArrayList<>(entries);
        latest.removeIf(Entry::rolledBack);
        latest.sort(Comparator.comparingLong((Entry e) -> -e.at()));
        if (!latest.isEmpty()) sb.append("Latest:");
        for (int i = 0; i < latest.size() && i < maxRecent; i++) {
            Entry e = latest.get(i);
            sb.append("\n  ").append(ago(now - e.at())).append(" ago ").append(e.player().startsWith("#") ? e.player().substring(1) : e.player())
                    .append(' ').append(verb(e.action())).append(' ').append(e.what()).append(" at ").append(e.x()).append(' ')
                    .append(e.y()).append(' ').append(e.z());
        }
        return sb.toString().trim();
    }

    static String ago(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s < 90) return s + " s";
        if (s < 5400) return (s / 60) + " min";
        if (s < 172_800) return (s / 3600) + " h";
        return (s / 86_400) + " d";
    }
}
