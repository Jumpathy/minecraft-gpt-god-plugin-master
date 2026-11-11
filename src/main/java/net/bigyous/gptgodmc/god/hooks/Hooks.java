package net.bigyous.gptgodmc.god.hooks;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * Other plugins the god can use when a server happens to have them (config.yml `hooks:`). None is required:
 * each is a soft dependency, its classes are only touched after {@link #has} says the plugin is running, and
 * a hook that fails to load is logged once and then simply absent. WorldGuard, GriefPrevention and Towny
 * tell the god whose land a place is ({@link Lands}); WorldGuard also lets it raise wards ({@link Wards});
 * CoreProtect shows who changed the land and can take a mortal's changes back ({@link LandHistory});
 * LibsDisguises lets it change a mortal's shape ({@link Disguises}).
 */
public final class Hooks {
    private Hooks() {
    }

    /** Whether a plugin of this name is installed and running. False without a server (unit tests). */
    public static boolean has(String plugin) {
        try {
            return Bukkit.getPluginManager().isPluginEnabled(plugin);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    static Plugin plugin(String name) {
        return has(name) ? Bukkit.getPluginManager().getPlugin(name) : null;
    }

    static FileConfiguration cfg() {
        return GPTGOD.get() == null ? null : GPTGOD.get().getConfig();
    }

    /** hooks.<key>, default on. */
    public static boolean on(String key) {
        FileConfiguration c = cfg();
        return c == null || c.getBoolean("hooks." + key, true);
    }

    static int number(String key, int fallback, int min, int max) {
        FileConfiguration c = cfg();
        return Math.max(min, Math.min(max, c == null ? fallback : c.getInt("hooks." + key, fallback)));
    }

    /** The hooks this server has, for the console and /god status. */
    public static List<String> found() {
        List<String> out = new ArrayList<>(Lands.names());
        if (LandHistory.installed()) out.add("CoreProtect");
        if (Disguises.installed()) out.add("LibsDisguises");
        return out;
    }

    /** Called once the server has enabled every plugin's classes (from the plugin's onEnable). */
    public static void enable(Plugin self) {
        // Other plugins may enable after this one; look when the server has finished starting.
        Bukkit.getScheduler().runTask(self, () -> {
            List<String> found = found();
            GPTGOD.LOGGER.info(found.isEmpty() ? "Plugin hooks: none of WorldGuard, GriefPrevention, Towny, CoreProtect, "
                    + "LibsDisguises is installed (all optional)." : "Plugin hooks: " + String.join(", ", found) + ".");
            Wards.sweep();
        });
        Bukkit.getScheduler().runTaskTimer(self, Wards::sweep, 20L * 30, 20L * 30);
    }
}
