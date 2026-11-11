package net.bigyous.gptgodmc.god.hooks;

import org.bukkit.Bukkit;
import org.bukkit.World;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * The door to the WorldEdit plugin (or FastAsyncWorldEdit), when a server has it. This class never names a
 * WorldEdit class: the code that does ({@code WorldEditBridge}) is loaded by name the first time it is
 * needed, so the plugin runs the same on servers without WorldEdit.
 */
public final class WorldEditHook {
    private WorldEditHook() {
    }

    /** What the bridge does. */
    public interface Bridge {
        /** Carries out one act and returns how many blocks changed. */
        int run(World world, String realm, WorldEditRules.Request request, int maxBlocks, WorldEditRules.Protected mortal)
                throws WorldEditRules.Refusal;

        /** Reverts this god's latest act; returns false when there is none to revert. */
        boolean undo(World world, String realm) throws WorldEditRules.Refusal;
    }

    private static Bridge bridge;
    private static boolean failed;

    /** Whether the WorldEdit plugin is installed and running. */
    public static boolean installed() {
        try {
            var pm = Bukkit.getPluginManager();
            return pm.isPluginEnabled("WorldEdit") || pm.isPluginEnabled("FastAsyncWorldEdit");
        } catch (RuntimeException | LinkageError e) {
            return false; // no server (unit tests)
        }
    }

    public static boolean enabled() {
        GPTGOD plugin = GPTGOD.get();
        return plugin == null || plugin.getConfig().getBoolean("worldedit.enabled", true);
    }

    public static int maxBlocks() {
        GPTGOD plugin = GPTGOD.get();
        return Math.max(1, plugin == null ? 20000 : plugin.getConfig().getInt("worldedit.max-blocks", 20000));
    }

    /** The bridge, or null when WorldEdit is missing or its API is not the one this was built for. */
    public static synchronized Bridge bridge() {
        if (bridge != null || failed || !installed()) return bridge;
        try {
            bridge = (Bridge) Class.forName("net.bigyous.gptgodmc.god.hooks.WorldEditBridge").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            failed = true;
            GPTGOD.LOGGER.warn("WorldEdit is installed but the god cannot use it: " + e);
        }
        return bridge;
    }
}
