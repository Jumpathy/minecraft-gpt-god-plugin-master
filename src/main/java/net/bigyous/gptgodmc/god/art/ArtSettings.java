package net.bigyous.gptgodmc.god.art;

import java.util.List;
import java.util.Locale;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * The creations: section of config.yml: whether the gods may make relics with painted art, which model
 * paints them and how hard it thinks, and the limits that keep the cost in hand. Painting needs a
 * stronger model and more effort than a god's ordinary thoughts, so it is off until an operator turns it
 * on, and turning it on comes with a warning ({@link #warning}).
 */
public final class ArtSettings {
    private static final FileConfiguration DEFAULTS = new YamlConfiguration();
    /** The model that paints unless config.yml names another. */
    public static final String DEFAULT_MODEL = "claude-opus-5-5";
    /** Efforts good enough to paint with, weakest first. */
    public static final List<String> EFFORTS = List.of("high", "xhigh", "max");

    private ArtSettings() {
    }

    private static FileConfiguration cfg() {
        GPTGOD plugin = GPTGOD.get();
        return plugin == null ? DEFAULTS : plugin.getConfig();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static boolean enabled() {
        return cfg().getBoolean("creations.enabled", false);
    }

    /** creations.model: the model that paints (an Opus-class or Fable-class Claude). */
    public static String model() {
        String m = cfg().getString("creations.model", DEFAULT_MODEL);
        return m == null || m.isBlank() ? DEFAULT_MODEL : m.trim();
    }

    /** creations.effort: high, xhigh or max. Anything weaker is raised to high: weak effort paints badly. */
    public static String effort() {
        return effort(cfg().getString("creations.effort", "high"));
    }

    static String effort(String configured) {
        String e = configured == null ? "" : configured.trim().toLowerCase(Locale.ROOT);
        return EFFORTS.contains(e) ? e : "high";
    }

    /** creations.max-attempts: how many times the artist may paint and look before its last attempt stands. */
    public static int attempts() {
        return clamp(cfg().getInt("creations.max-attempts", 4), 2, 6);
    }

    /** creations.per-god-per-day: relics one god may make in 24 hours. */
    public static int perGodPerDay() {
        return clamp(cfg().getInt("creations.per-god-per-day", 3), 1, 50);
    }

    /** creations.max-total: relics kept in all (each is a few files in the resource pack). */
    public static int maxTotal() {
        return clamp(cfg().getInt("creations.max-total", 48), 1, 128);
    }

    /** creations.cooldown-minutes: the rest a god takes between two relics. */
    public static int cooldownMinutes() {
        return clamp(cfg().getInt("creations.cooldown-minutes", 10), 0, 1440);
    }

    /**
     * creations.pack-push-seconds: how long after a relic is made the rebuilt resource pack is sent to
     * players (their game reloads its resources for a few seconds). 0 = never during play; they get it
     * when they next join. Players with the client mod are not sent it: the mod draws relics at once.
     */
    public static int packPushSeconds() {
        return clamp(cfg().getInt("creations.pack-push-seconds", 30), 0, 3600);
    }

    /**
     * creations.power-budget: how much power one relic may be imbued with (see {@link RelicPowers}; each
     * property, effect and attack costs a little). 0 = relics cannot be imbued at all.
     */
    public static double powerBudget() {
        return Math.max(0, Math.min(40, cfg().getDouble("creations.power-budget", 12)));
    }

    /** creations.max-in-world: relics and spirits that may float in the world at once. */
    public static int worldMax() {
        return clamp(cfg().getInt("creations.max-in-world", 12), 0, 64);
    }

    /**
     * Why a model may not paint, or null. Pixel art asks more of a model than play does; the smaller
     * models draw shapes that do not read. Behind a gateway any name may be a strong model, so it is let be.
     */
    public static String modelProblem(String model, boolean gateway) {
        String m = model == null ? "" : model.toLowerCase(Locale.ROOT);
        if (m.contains("opus") || m.contains("fable") || m.contains("mythos") || gateway) return null;
        return "creations.model is \"" + model + "\". Painting needs an Opus-class or Fable-class model (for example "
                + DEFAULT_MODEL + "); smaller models are not allowed to paint.";
    }

    /** What an operator is told when the forge is lit: what it runs on, what that costs, what limits it. */
    public static String warning() {
        return "God-made relics are ON. Every relic is painted by " + model() + " at " + effort() + " effort over up to "
                + attempts() + " attempts, with pictures sent back each time. One relic costs many times an ordinary "
                + "thought (roughly $0.20 to $1.00 on Claude Opus 5.5, more on Fable), so this can burn through usage "
                + "fast. Limits: " + perGodPerDay() + " a day for each god, " + cooldownMinutes()
                + " minutes between them, " + maxTotal() + " kept in all (creations: in config.yml).";
    }
}
