package net.bigyous.gptgodmc.god.living;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.LivingAccess;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.loggables.GenericEventLoggable;

/**
 * Gods that live in the world: their bonds and rivalries, the holy days they proclaim, their moods
 * leaking into weather and crops, and the servants they summon. The one entry point (enable, disable,
 * perception).
 */
public final class Living {
    private static final List<BukkitTask> TASKS = new ArrayList<>();

    private Living() {
    }

    public static void enable(JavaPlugin plugin) {
        HolyDayRunner.load(plugin.getDataFolder().toPath().resolve("god").resolve("holydays.json"));
        cleanup();
        plugin.getServer().getPluginManager().registerEvents(new LivingListener(), plugin);
        LivingCommands commands = new LivingCommands();
        for (String name : new String[] { "side", "holydays", "bonds" }) {
            PluginCommand c = plugin.getCommand(name);
            if (c != null) {
                c.setExecutor(commands);
                c.setTabCompleter(commands);
            }
        }
        TASKS.add(Bukkit.getScheduler().runTaskTimer(plugin, Servants::tickAll, 40L, 2L));
        TASKS.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (LivingSettings.eventsEnabled()) HolyDayRunner.tick();
        }, 60L, 20L));
        TASKS.add(Bukkit.getScheduler().runTaskTimer(plugin, WorldMoodRunner::tick, 200L, 200L));
        TASKS.add(Bukkit.getScheduler().runTaskTimer(plugin, Living::everyMinute, 1200L, 1200L));
    }

    public static void disable() {
        TASKS.forEach(BukkitTask::cancel);
        TASKS.clear();
        Servants.shutdown();
        HolyDayRunner.shutdown();
    }

    /** Removes servants, raiders and quarries left from before a restart. */
    private static void cleanup() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (Servants.isServantEntity(e) || HolyDayRunner.tagged(e)) e.remove();
            }
        }
    }

    private static int minutes;

    private static void everyMinute() {
        long now = System.currentTimeMillis();
        if (++minutes % 5 == 0) Rivalry.contest(now);
        if (!LivingSettings.eventsEnabled()) return;
        double cadence = LivingSettings.eventsCadenceHours();
        for (Realm r : Realm.all()) {
            if (!LivingAccess.awake(r) || r.players().isEmpty() || r.dormant()) continue;
            if (!HolyDayRunner.store().due(r.id, cadence, now)) continue;
            HolyDayRunner.store().nudged(r.id, now);
            HolyDayRunner.changed();
            String text = "The season turns: no holy day has been held in your name for a long while. Consider "
                    + "proclaiming one (proclaim): a festival, a trial, a holy hunt, an omen of something great"
                    + (r.shared() ? ", or a holy war on a rival god" : "") + ", or a raid on a mortal who has earned it.";
            r.run(() -> EventLogger.addLoggable(new GenericEventLoggable(text, "a holy day is due")));
        }
    }

    // ---- Perception -----------------------------------------------------------------------------------

    /** The god's sections about bonds, holy days, servants and its mood in the world (short; "" when empty). */
    public static String perceive(Realm r) {
        StringBuilder sb = new StringBuilder();
        try {
            String bonds = Rivalry.perceive(r);
            if (!bonds.isEmpty()) {
                sb.append("## Your bonds with the other gods (your own feelings; regard / pact to change them)\n")
                        .append(bonds).append('\n');
            }
            String days = LivingSettings.eventsEnabled() ? HolyDayRunner.store().perceive(r.id, System.currentTimeMillis())
                    : "";
            if (!days.isEmpty()) sb.append("## Holy days\n").append(days).append('\n');
            String servants = Servants.perceive(r);
            if (!servants.isEmpty()) {
                sb.append("## Your servants (command_servant to direct them)\n").append(servants).append('\n');
            }
            String mood = WorldMoodRunner.describe(r);
            if (!mood.isEmpty()) sb.append("## Your mood in the world\n").append(mood).append("\n\n");
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not describe bonds, holy days or servants", e);
        }
        return sb.toString();
    }
}
