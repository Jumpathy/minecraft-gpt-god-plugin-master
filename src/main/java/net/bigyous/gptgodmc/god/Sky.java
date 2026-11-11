package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * The sky as the god notices it: one short line under "## The world" with the moon's phase on a clear
 * night, how long it has rained or stormed, and rain that has only just stopped. It says nothing when
 * there is nothing to add (a plain day), so it costs a dozen tokens at most. The judgments are pure and
 * unit tested.
 */
public final class Sky implements Listener {
    public enum Weather {
        CLEAR, RAIN, THUNDER
    }

    /** The longest the line may be, so it can never bloat a perception. */
    public static final int CAP = 100;
    /** Rain that stopped longer ago than this is no longer worth a word. */
    static final long JUST_NOW_MS = 10 * 60_000;

    private static final String[] MOONS = { "a full moon", "a waning gibbous moon", "a waning half moon",
            "a waning crescent moon", "a new moon, the night at its darkest", "a waxing crescent moon",
            "a waxing half moon", "a waxing gibbous moon" };

    // ---- What there is to say (pure) ----------------------------------------------------------------

    /**
     * The sky at one moment.
     *
     * @param dayTime ticks into the day (0 = dawn)
     * @param day     whole days the world has lived (the moon turns once every eight)
     * @param sinceMs how long this weather has lasted (for rain: since the sky was last clear), or -1 when unknown
     * @param before  the weather before this, or null when unknown
     */
    public record View(long dayTime, long day, Weather weather, long sinceMs, Weather before) {
    }

    public static String moon(long day) {
        return MOONS[(int) Math.floorMod(day, 8)];
    }

    /** The same rule as Perception's "thunderstorm / rain / clear": thunder without rain is a clear sky. */
    public static Weather weather(boolean storm, boolean thunder) {
        return !storm ? Weather.CLEAR : thunder ? Weather.THUNDER : Weather.RAIN;
    }

    /** "Sky: a full moon; the rain stopped 4 min ago." or null when the sky adds nothing. */
    public static String describe(View v) {
        List<String> parts = new ArrayList<>();
        boolean night = v.dayTime() >= 13000 && v.dayTime() < 23000;
        if (night && v.weather() == Weather.CLEAR) parts.add(moon(v.day()));
        if (v.sinceMs() >= 0) {
            if (v.weather() == Weather.RAIN) {
                parts.add("rain for " + span(v.sinceMs()));
            } else if (v.weather() == Weather.THUNDER) {
                parts.add("a thunderstorm for " + span(v.sinceMs()));
            } else if (v.before() != null && v.before() != Weather.CLEAR && v.sinceMs() < JUST_NOW_MS) {
                parts.add((v.before() == Weather.THUNDER ? "the storm passed " : "the rain stopped ")
                        + span(v.sinceMs()) + " ago");
            }
        }
        return parts.isEmpty() ? null : "Sky: " + String.join("; ", parts) + ".";
    }

    static String span(long ms) {
        long m = ms / 60_000;
        if (m < 1) return "under a minute";
        if (m < 60) return m + " min";
        return m / 60 + " h" + (m % 60 >= 10 ? " " + m % 60 + " min" : "");
    }

    // ---- Remembering when the weather turned (pure) -------------------------------------------------

    /** Each world's weather and when it turned; the first sighting has no known beginning. */
    public static final class Log {
        private record Entry(Weather now, long since, Weather before, long wetSince) {
        }

        private final Map<String, Entry> worlds = new ConcurrentHashMap<>();

        /** Returns the weather it turned from, or null when nothing turned (or this is the first sighting). */
        public Weather note(String world, Weather w, long at) {
            Weather[] from = { null };
            worlds.compute(world, (k, e) -> {
                if (e == null) return new Entry(w, -1, null, -1);
                if (e.now == w) return e;
                from[0] = e.now;
                long wet = w == Weather.CLEAR ? -1 : e.now == Weather.CLEAR ? at : e.wetSince;
                return new Entry(w, at, e.now, wet);
            });
            return from[0];
        }

        public View view(String world, long dayTime, long day, long at) {
            Entry e = worlds.get(world);
            if (e == null) return new View(dayTime, day, Weather.CLEAR, -1, null);
            // Rain that follows thunder is the same wet spell, so it is counted from when the sky closed.
            long from = e.now == Weather.RAIN && e.wetSince >= 0 ? e.wetSince : e.since;
            return new View(dayTime, day, e.now, from < 0 ? -1 : Math.max(0, at - from), e.before);
        }
    }

    // ---- Watching the world -------------------------------------------------------------------------

    private static final Log LOG = new Log();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        LOG.note(e.getWorld().getName(), weather(e.toWeatherState(), e.getWorld().isThundering()),
                System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThunder(ThunderChangeEvent e) {
        LOG.note(e.getWorld().getName(), weather(e.getWorld().hasStorm(), e.toThunderState()),
                System.currentTimeMillis());
    }

    /** The sky over a world right now ("Sky: a full moon."), or null when there is nothing to say. Server thread. */
    public static String now(World world) {
        if (world.getEnvironment() != World.Environment.NORMAL) return null;
        long now = System.currentTimeMillis();
        LOG.note(world.getName(), weather(world.hasStorm(), world.isThundering()), now);
        return describe(LOG.view(world.getName(), world.getTime(), world.getFullTime() / 24000, now));
    }

    /** The line for the perception, ending in a newline, or "" (no sky in the Nether or the End). Server thread. */
    public static String line(World world) {
        try {
            // Also catches a change the listener missed (a reload), dated to this look.
            String s = now(world);
            return s == null || !GodSettings.awarenessSky() || Llm.lite() ? "" : s + "\n";
        } catch (RuntimeException e) {
            return "";
        }
    }
}
