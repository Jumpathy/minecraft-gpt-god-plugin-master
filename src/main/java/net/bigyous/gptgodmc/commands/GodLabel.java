package net.bigyous.gptgodmc.commands;

import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * Keeps /god for the god plugin. EssentialsX (god mode) and WorldGuard both define /god, and whichever
 * plugin loads first keeps the bare label (Essentials' disabled-commands does not free it). Once every
 * plugin has loaded, this puts our command back under "god" ({@code operator.claim-god-command}, on by
 * default); the other plugin's command stays reachable under its own prefix (/essentials:god, and
 * Essentials' own aliases such as /egod or /tgm; /worldguard:god). Tried on ServerLoadEvent, on the
 * first tick and 5 s later, because some plugins register their commands late; each try does nothing
 * once /god is ours.
 */
public final class GodLabel implements Listener {
    private final JavaPlugin plugin;
    private static boolean told;

    private GodLabel(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static void enable(JavaPlugin plugin) {
        told = false;
        plugin.getServer().getPluginManager().registerEvents(new GodLabel(plugin), plugin);
        Bukkit.getScheduler().runTask(plugin, () -> claim(plugin));
        Bukkit.getScheduler().runTaskLater(plugin, () -> claim(plugin), 100L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(ServerLoadEvent e) {
        claim(plugin);
    }

    /** Puts our /god back under the bare label if another plugin holds it. */
    static void claim(JavaPlugin plugin) {
        if (!plugin.isEnabled()) return;
        PluginCommand ours = plugin.getCommand("god");
        if (ours == null) return;
        CommandMap map = Bukkit.getCommandMap();
        Command owner = map.getCommand("god");
        if (owner == null || owner == ours) return;
        String who = ownerName(owner);
        if (!plugin.getConfig().getBoolean("operator.claim-god-command", true)) {
            if (!told) GPTGOD.LOGGER.warn("/god belongs to " + who + "; use /gptgod (or /gptgodmc:god) for this "
                    + "plugin, or set operator.claim-god-command: true.");
            told = true;
            return;
        }
        try {
            Map<String, Command> known = map.getKnownCommands();
            known.remove("god");
            known.put("god", ours);
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not take /god from " + who + " (" + e.getMessage() + "); use /gptgod.");
            return;
        }
        if (map.getCommand("god") != ours) {
            GPTGOD.LOGGER.warn("Could not take /god from " + who + "; use /gptgod.");
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) p.updateCommands();
        GPTGOD.LOGGER.info(note(who));
    }

    /** What the console is told once /god is ours again. */
    static String note(String who) {
        String prefix = who.toLowerCase(Locale.ROOT).replace(' ', '_');
        String extra = prefix.startsWith("essentials") ? " (or /egod)" : "";
        return "/god now reaches the gods. " + who + "'s /god is still at /" + prefix + ":god" + extra
                + " (operator.claim-god-command: false leaves /god to it).";
    }

    static String ownerName(Command owner) {
        if (owner instanceof PluginIdentifiableCommand pic && pic.getPlugin() != null) {
            return pic.getPlugin().getName();
        }
        return owner.getClass().getSimpleName();
    }
}
