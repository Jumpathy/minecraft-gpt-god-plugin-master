package net.bigyous.gptgodmc.god.tools;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import net.bigyous.gptgodmc.Structure;
import net.bigyous.gptgodmc.StructureManager;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Initiative;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.hooks.WorldEditHook;
import net.bigyous.gptgodmc.god.hooks.WorldEditRules;

/**
 * WorldEdit as one more tool in the god's hands, on servers that have the WorldEdit (or FastAsyncWorldEdit)
 * plugin. It drives WorldEdit's Java API through {@link WorldEditHook}; WorldEdit's own commands stay on the
 * command blocklist. Mortals' structures are masked out of every act and each act has a block limit.
 */
final class WorldEditTools {
    private WorldEditTools() {
    }

    static List<GodTool> all() {
        return List.of(GodTool.sync("worldedit", WorldEditTools::description,
                Schema.object()
                        .choice("action", "What to do.", true, WorldEditRules.ACTIONS)
                        .str("from", "One corner of the region, block coordinates 'x y z' (line: where it starts).", false)
                        .str("to", "The opposite corner 'x y z' (line: where it ends).", false)
                        .str("at", "sphere, cylinder, pyramid: where its base centre goes: " + Targets.TARGET_HELP + ".", false)
                        .str("pattern", "The blocks to place, as a WorldEdit pattern: stone, 50%stone,50%andesite, "
                                + "oak_stairs[facing=north], air.", false)
                        .str("mask", "replace: which blocks to replace, as a WorldEdit mask: grass_block, stone,andesite, #logs.", false)
                        .integer("radius", "sphere and cylinder: radius in blocks. pyramid: its height in layers. line: thickness.",
                                false, 0, 64)
                        .integer("height", "cylinder: height in blocks. Default 1.", false, 1, 128)
                        .bool("hollow", "sphere, cylinder, pyramid, line, generate: only the shell. Default false.", false)
                        .str("expression", "generate: true where a block belongs, in x, y, z (each -1 to 1 across the region), "
                                + "e.g. x^2+y^2+z^2 < 1 or y < sin(x*3)*0.3.", false)
                        .choice("direction", "stack, move: which way.", false, WorldEditRules.DIRECTIONS)
                        .integer("count", "stack: how many copies. move: how many blocks. Default 1.", false, 1, 64)
                        .integer("iterations", "smooth: how many passes. Default 1.", false, 1, 6),
                WorldEditTools::run));
    }

    static String description() {
        String what = "WorldEdit, for shaping many blocks in one act when your other powers are too coarse: set (fill the "
                + "region from..to with a pattern), replace (only the blocks a mask names), walls, faces, hollow (leave a "
                + "shell), sphere, cylinder and pyramid (at a place), line (from..to), generate (any shape you can write as "
                + "an expression, inside the region), smooth (soften terrain), stack (repeat the region in a direction), "
                + "move, and undo (take back your latest WorldEdit act). Mortals' structures are never changed.";
        if (!WorldEditHook.enabled()) return what + " The keepers of this server have turned it off.";
        if (!WorldEditHook.installed()) return what + " This server does not have the WorldEdit plugin, so it is not yours to use here.";
        return what + " At most " + WorldEditHook.maxBlocks() + " blocks an act.";
    }

    private static ToolResult run(Args a) throws ToolException {
        if (!WorldEditHook.enabled()) throw new ToolException("The keepers of this server have turned WorldEdit off for you.");
        WorldEditHook.Bridge bridge = WorldEditHook.bridge();
        if (bridge == null) {
            throw new ToolException("This server has no WorldEdit plugin you can use. Use build, conjure or run_commands (fill) instead.");
        }
        if (GodSettings.initiativeLevel() == Initiative.Level.WATCH) {
            throw new ToolException("The keepers of this server want you only to watch (initiative.level is watch).");
        }
        Realm realm = Realm.current();
        World world = realm.world();
        String action = a.str("action").trim().toLowerCase(Locale.ROOT);
        try {
            if (action.equals("undo")) {
                if (!bridge.undo(world, realm.id)) throw new ToolException("You have no WorldEdit act left to take back.");
                return ToolResult.ok("Your latest WorldEdit act is undone.");
            }
            int[] from = corner(a, "from"), to = corner(a, "to"), at = null;
            if (a.has("at")) {
                Location l = Targets.location(a.str("at"));
                world = l.getWorld();
                at = new int[] { l.getBlockX(), l.getBlockY(), l.getBlockZ() };
            }
            int max = WorldEditHook.maxBlocks();
            WorldEditRules.Request request = new WorldEditRules.Request(action, from, to, at, a.str("pattern", null), a.str("mask", null),
                    a.integer("radius", action.equals("line") ? 0 : 3, 0, 64), a.integer("height", 1, 1, 128), a.bool("hollow", false),
                    a.str("expression", null), a.has("direction") ? WorldEditRules.direction(a.str("direction")) : null,
                    a.integer("count", 1, 1, 64), a.integer("iterations", 1, 1, 6));
            WorldEditRules.check(request, max);
            Set<Long> mortal = mortalBlocks(world);
            int changed = bridge.run(world, realm.id, request, max, (x, y, z) -> mortal.contains(key(x, y, z)));
            String where = at != null ? " at " + at[0] + " " + at[1] + " " + at[2]
                    : from != null ? " from " + from[0] + " " + from[1] + " " + from[2] + " to " + to[0] + " " + to[1] + " " + to[2] : "";
            return ToolResult.ok("WorldEdit %s%s: %d blocks changed%s. (worldedit undo takes it back.)", action, where, changed,
                    changed == 0 ? " (nothing matched, or it was all mortals' work)" : "");
        } catch (WorldEditRules.Refusal e) {
            throw new ToolException(e.getMessage());
        }
    }

    private static int[] corner(Args a, String key) throws ToolException {
        if (!a.has(key)) return null;
        Location l = Targets.parseCoords(a.str(key));
        if (l == null) throw new ToolException("'" + key + "' must be block coordinates 'x y z'.");
        return new int[] { l.getBlockX(), l.getBlockY(), l.getBlockZ() };
    }

    /** Every block of every mortal's structure in this world. */
    private static Set<Long> mortalBlocks(World world) {
        Set<Long> out = new HashSet<>();
        for (String name : StructureManager.getStructures()) {
            Structure s = StructureManager.getStructure(name);
            if (s == null || s.getLocation() == null || !world.equals(s.getLocation().getWorld())) continue;
            for (Vector v : s.getVectors()) out.add(key(v.getBlockX(), v.getBlockY(), v.getBlockZ()));
        }
        return out;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
