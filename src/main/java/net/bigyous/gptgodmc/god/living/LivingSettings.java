package net.bigyous.gptgodmc.god.living;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.bigyous.gptgodmc.GPTGOD;

/** config.yml settings for rivalries, holy days, moods in the world and servants. */
public final class LivingSettings {
    private static final FileConfiguration DEFAULTS = new YamlConfiguration();

    private LivingSettings() {
    }

    private static FileConfiguration cfg() {
        GPTGOD plugin = GPTGOD.get();
        return plugin == null ? DEFAULTS : plugin.getConfig();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---- Rivalry -------------------------------------------------------------------------------------

    /** rivalry.enabled: gods keep bonds with one another, make pacts, sabotage and wage holy wars. */
    public static boolean rivalryEnabled() {
        return cfg().getBoolean("rivalry.enabled", true);
    }

    /** rivalry.sabotage: whether gods may spoil each other's works and missions. */
    public static boolean sabotageAllowed() {
        return cfg().getBoolean("rivalry.sabotage", true);
    }

    /** rivalry.sabotage-cooldown-minutes: between two sabotages of the same rival. */
    public static int sabotageCooldownMinutes() {
        return clamp(cfg().getInt("rivalry.sabotage-cooldown-minutes", 20), 1, 1440);
    }

    /** rivalry.sabotage-blocks: the most blocks one sabotage may touch. */
    public static int sabotageBlocks() {
        return clamp(cfg().getInt("rivalry.sabotage-blocks", 24), 1, 200);
    }

    // ---- Holy days -----------------------------------------------------------------------------------

    public static boolean eventsEnabled() {
        return cfg().getBoolean("events.enabled", true);
    }

    /** events.cadence-hours: remind a god to hold a holy day after this long without one (0 = never). */
    public static double eventsCadenceHours() {
        return Math.max(0, Math.min(168, cfg().getDouble("events.cadence-hours", 4)));
    }

    public static int eventsMaxPerGod() {
        return clamp(cfg().getInt("events.max-per-god", 2), 1, 6);
    }

    public static int eventsMaxTotal() {
        return clamp(cfg().getInt("events.max-total", 4), 1, 12);
    }

    /** events.raids: whether gods may raid mortals they dislike. */
    public static boolean raidsAllowed() {
        return cfg().getBoolean("events.raids", true);
    }

    /** events.raid-waves: waves in a raid. */
    public static int raidWaves() {
        return clamp(cfg().getInt("events.raid-waves", 3), 1, 8);
    }

    /** events.raid-max-mobs: the most mobs in one wave. */
    public static int raidMaxMobs() {
        return clamp(cfg().getInt("events.raid-max-mobs", 6), 1, 20);
    }

    /** events.raid-grace-minutes: a mortal who just joined cannot be raided for this long. */
    public static int raidGraceMinutes() {
        return clamp(cfg().getInt("events.raid-grace-minutes", 5), 0, 60);
    }

    /** events.reward-xp: experience for the best three when a holy day ends (halved, then quartered). */
    public static int rewardXp() {
        return clamp(cfg().getInt("events.reward-xp", 60), 0, 2000);
    }

    // ---- Moods in the world --------------------------------------------------------------------------

    public static boolean worldMoodEnabled() {
        return cfg().getBoolean("world-mood.enabled", true);
    }

    /** world-mood.strength: subtle | noticeable | strong. */
    public static String worldMoodStrength() {
        return cfg().getString("world-mood.strength", "subtle");
    }

    public static boolean worldMoodWeather() {
        return cfg().getBoolean("world-mood.weather", true);
    }

    public static boolean worldMoodTime() {
        return cfg().getBoolean("world-mood.time", true);
    }

    public static boolean worldMoodMobs() {
        return cfg().getBoolean("world-mood.mobs", true);
    }

    public static boolean worldMoodCrops() {
        return cfg().getBoolean("world-mood.crops", true);
    }

    public static boolean worldMoodAmbient() {
        return cfg().getBoolean("world-mood.ambient", true);
    }

    /** world-mood.radius: how far from the god's body (or, formless, from mortals) its mood reaches. */
    public static int worldMoodRadius() {
        return clamp(cfg().getInt("world-mood.radius", 48), 8, 160);
    }

    // ---- Servants ------------------------------------------------------------------------------------

    public static boolean servantsEnabled() {
        return cfg().getBoolean("servants.enabled", true);
    }

    public static int servantsPerGod() {
        return clamp(cfg().getInt("servants.max-per-god", 3), 1, 10);
    }

    public static int servantsTotal() {
        return clamp(cfg().getInt("servants.max-total", 8), 1, 30);
    }

    /** servants.max-minutes: the longest a servant may stay. */
    public static int servantsMaxMinutes() {
        return clamp(cfg().getInt("servants.max-minutes", 30), 1, 240);
    }

    /**
     * servants.textures: their own painted bodies for players who loaded the resource pack (it rides in
     * the vessel's pack, so vessel.textures must be custom too). Off: everyone sees the vanilla mobs.
     */
    public static boolean servantsTextured() {
        return cfg().getBoolean("servants.textures", true);
    }

    /** servants.attack-players: whether servants may be set on mortals at all. */
    public static boolean servantsAttackPlayers() {
        return cfg().getBoolean("servants.attack-players", true);
    }
}
