package net.bigyous.gptgodmc.god.art;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.client.ModRelics;
import net.bigyous.gptgodmc.god.vessel.VesselPackHost;

/**
 * The one entry point of the gods' relics (enable, disable, the clocks): loads what was made before,
 * keeps relics set in the world, sends their art to players with the client mod, and, a little after a
 * relic is made, has the resource pack rebuilt and sent again to everyone else. Painting is done by
 * {@link Atelier}; this is everything around it.
 */
public final class RelicsModule {
    private static final List<BukkitTask> TASKS = new ArrayList<>();
    /** When the rebuilt pack is due to be sent (epoch ms), or 0 when nothing has changed. */
    private static volatile long pushAt;

    private RelicsModule() {
    }

    /** Call before the resource pack is first built, so it holds the relics that already exist. */
    public static void enable(JavaPlugin plugin) {
        Path dir = plugin.getDataFolder().toPath().resolve("god").resolve("relics");
        Relics.load(dir);
        RelicShrines.load(dir.resolve("shrines.json"));
        Armory.load(dir.resolve("armory.json"));
        PluginCommand command = plugin.getCommand("relics");
        if (command != null) {
            RelicCommands commands = new RelicCommands();
            command.setExecutor(commands);
            command.setTabCompleter(commands);
        }
        Bukkit.getPluginManager().registerEvents(new RelicImbue(), plugin);
        TASKS.add(Bukkit.getScheduler().runTaskTimer(plugin, RelicImbue::tick, 60L, 5L));
        TASKS.add(Bukkit.getScheduler().runTaskTimer(plugin, RelicShrines::tick, 60L, 2L));
        TASKS.add(Bukkit.getScheduler().runTaskTimer(plugin, ModRelics::tick, 60L, 10L));
        TASKS.add(Bukkit.getScheduler().runTaskTimer(plugin, RelicsModule::pushIfDue, 100L, 20L));
        if (ArtSettings.enabled()) warn();
    }

    public static void disable() {
        TASKS.forEach(BukkitTask::cancel);
        TASKS.clear();
        RelicShrines.shutdown();
        RelicImbue.shutdown();
        ModRelics.clear();
        pushAt = 0;
    }

    /** Says in the console what lighting the forge means for usage (at start, and whenever it is turned on). */
    public static void warn() {
        GPTGOD.LOGGER.warn(ArtSettings.warning());
        String problem = ArtSettings.modelProblem(ArtSettings.model(), false);
        if (problem != null) GPTGOD.LOGGER.warn(problem);
    }

    /**
     * A relic was made, repainted or unmade. Players with the client mod are sent the change within half
     * a second; the resource pack for everyone else is rebuilt after creations.pack-push-seconds (their
     * game reloads its resources when it arrives), or only when they next join if that is 0. Any thread.
     */
    public static void changed() {
        int wait = ArtSettings.packPushSeconds();
        if (wait > 0) pushAt = System.currentTimeMillis() + wait * 1000L;
    }

    private static void pushIfDue() {
        long due = pushAt;
        if (due == 0 || System.currentTimeMillis() < due) return;
        pushAt = 0;
        // Players whose mod draws relics are spared the reload; they get the new pack when they next join.
        VesselPackHost.republish(ModRelics::draws);
    }
}
