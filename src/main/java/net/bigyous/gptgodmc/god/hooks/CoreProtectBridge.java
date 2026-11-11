package net.bigyous.gptgodmc.god.hooks;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;

/**
 * Everything that names a CoreProtect class. Loaded by name from {@link LandHistory}, and only on servers
 * that have CoreProtect. Its calls read a database: never run them on the server thread.
 */
public final class CoreProtectBridge implements LandHistory.Source {
    private static final int MAX_ROWS = 4000;

    private static CoreProtectAPI api() {
        Plugin p = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (!(p instanceof CoreProtect cp)) return null;
        CoreProtectAPI api = cp.getAPI();
        return api != null && api.isEnabled() && api.APIVersion() >= 9 ? api : null;
    }

    @Override
    public List<LandHistory.Entry> lookup(Location centre, int radius, int seconds, String player) {
        List<LandHistory.Entry> out = new ArrayList<>();
        CoreProtectAPI api = api();
        if (api == null) return out;
        List<String[]> rows = api.performLookup(seconds, player == null ? null : List.of(player), null, null, null, null, radius, centre);
        if (rows == null) return out;
        for (String[] row : rows) {
            if (out.size() >= MAX_ROWS) break;
            try {
                CoreProtectAPI.ParseResult r = api.parseResult(row);
                Material type = r.getType();
                out.add(new LandHistory.Entry(r.getPlayer(), r.getActionString(), type == null ? "something" : type.getKey().getKey(),
                        r.getX(), r.getY(), r.getZ(), r.getTimestamp(), r.isRolledBack()));
            } catch (RuntimeException e) {
                // one row it cannot read is no reason to lose the rest
            }
        }
        return out;
    }

    @Override
    public int rollback(Location centre, int radius, int seconds, String player) {
        CoreProtectAPI api = api();
        if (api == null) return 0;
        List<String[]> rows = api.performRollback(seconds, List.of(player), null, null, null, null, radius, centre);
        return rows == null ? 0 : rows.size();
    }
}
