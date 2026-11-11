package net.bigyous.gptgodmc.god.living;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.bigyous.gptgodmc.god.Heart;

/**
 * How a god's heart leaks into the world near it: wrath gathers storms, darkens the sky and makes night
 * mobs bolder; joy clears the sky, quickens crops and stretches the day; sorrow brings soft rain; dread
 * drags night closer; glory brings bright, clear days. Only feelings strong enough to show do anything,
 * and the strength setting scales how much. Pure: {@link #of} decides, {@link WorldMoodRunner} applies it
 * (rate-limited) in the world.
 */
public final class WorldMood {
    public enum Weather {
        KEEP, CLEAR, RAIN, STORM
    }

    /**
     * What the world should do right now.
     *
     * @param weather    the weather to drift toward (KEEP = leave it alone)
     * @param timeDrift  game ticks added (or taken, negative) per minute: the day hurries or lingers
     * @param darkness   whether nearby mortals briefly feel the sky darken (the Darkness effect)
     * @param boldMobs   whether hostile mobs near mortals grow bolder at night (faster, hunt farther)
     * @param cropTicks  extra growth steps per minute for crops near mortals
     * @param ambient    an ambient sign near mortals and the god (particle + sound), or null
     * @param feeling    the feeling behind it, or null when calm
     * @param level      how strong that feeling is (0..100)
     */
    public record Effects(Weather weather, int timeDrift, boolean darkness, boolean boldMobs, int cropTicks,
            Ambient ambient, Heart.Feeling feeling, double level) {
        public static final Effects CALM = new Effects(Weather.KEEP, 0, false, false, 0, null, null, 0);

        public boolean calm() {
            return feeling == null;
        }

        /** What the god is told (one short line), or "" when nothing leaks. */
        public String describe() {
            if (calm()) return "";
            List<String> parts = new ArrayList<>();
            switch (weather) {
                case STORM -> parts.add("storms gather");
                case RAIN -> parts.add("rain falls");
                case CLEAR -> parts.add("the sky clears");
                default -> {
                }
            }
            if (timeDrift > 0) parts.add("the day hurries on");
            if (timeDrift < 0) parts.add("the day lingers");
            if (darkness) parts.add("the sky darkens over mortals");
            if (boldMobs) parts.add("night creatures grow bold");
            if (cropTicks > 0) parts.add("crops grow quickly");
            if (parts.isEmpty() && ambient != null) parts.add("the air itself stirs");
            return "Your " + feeling.id() + " touches the world near you: " + String.join(", ", parts) + ".";
        }
    }

    /** An ambient sign: a particle name (Spectacle's list) and a sound key, both vanilla. */
    public record Ambient(String particle, String sound, float pitch) {
    }

    /** Strength names → multiplier. */
    public static double strength(String name) {
        if (name == null) return 1;
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "off", "none" -> 0;
            case "faint", "subtle" -> 1;
            case "noticeable", "medium" -> 1.6;
            case "strong", "high" -> 2.2;
            default -> 1;
        };
    }

    /**
     * The effects for a feeling at a level, scaled by strength (0 = off, 1 subtle, ~2 strong).
     *
     * @param night whether it is night in that world (bold mobs only come out at night)
     */
    public static Effects of(Heart.Feeling f, double level, double strength, boolean night) {
        if (f == null || strength <= 0 || level < Heart.SHOWS) return Effects.CALM;
        double s = (level - Heart.SHOWS) / (100 - Heart.SHOWS); // 0 at "stirring", 1 at full
        boolean strong = level >= 55;
        boolean consuming = level >= 80;
        double k = strength * (0.5 + s);
        return switch (f) {
            case WRATH -> new Effects(strong ? Weather.STORM : Weather.RAIN, (int) Math.round(30 * k),
                    consuming && strength >= 1, strong && night, 0,
                    new Ambient("ash", "entity.lightning_bolt.thunder", 0.6f), f, level);
            case JOY -> new Effects(Weather.CLEAR, (int) Math.round(-25 * k), false, false,
                    strong ? (int) Math.round(6 * k) : (int) Math.round(2 * k),
                    new Ambient("happy_villager", "block.amethyst_block.chime", 1.4f), f, level);
            case SORROW -> new Effects(strong ? Weather.RAIN : Weather.KEEP, (int) Math.round(-15 * k), false, false, 0,
                    new Ambient("falling_water", "ambient.cave", 0.8f), f, level);
            case DREAD -> new Effects(Weather.KEEP, (int) Math.round(40 * k), consuming && strength >= 1,
                    strong && night, 0, new Ambient("sculk_soul", "ambient.soul_sand_valley.mood", 0.7f), f, level);
            case GLORY -> new Effects(Weather.CLEAR, 0, false, false, strong ? (int) Math.round(3 * k) : 0,
                    new Ambient("end_rod", "block.beacon.ambient", 1.6f), f, level);
        };
    }

    // ---- Rate limits (pure, so they are tested) ----------------------------------------------------------

    /** Minimum gap between weather changes the heart causes. */
    public static final long WEATHER_GAP_MS = 10 * 60_000L;
    /** Minimum gap between darkness pulses on one mortal. */
    public static final long DARKNESS_GAP_MS = 3 * 60_000L;
    /** The most time can drift per minute, either way (a day is 24000 ticks). */
    public static final int MAX_DRIFT = 120;

    /** True when the weather may be changed now. */
    public static boolean weatherDue(long lastChange, long now) {
        return now - lastChange >= WEATHER_GAP_MS;
    }

    public static int drift(int perMinute, double minutes) {
        int d = (int) Math.round(Math.max(-MAX_DRIFT, Math.min(MAX_DRIFT, perMinute)) * minutes);
        return d;
    }
}
