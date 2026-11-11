package net.bigyous.gptgodmc.god.tools;

import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Initiative;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.build.Style;
import net.bigyous.gptgodmc.god.worlds.GodWorlds;
import net.bigyous.gptgodmc.god.worlds.Labyrinths;
import net.bigyous.gptgodmc.god.worlds.Maze;

/** Worlds of the god's own making, and the labyrinth that stands in one of them. */
final class DimensionTools {
    private DimensionTools() {
    }

    static List<GodTool> all() {
        return List.of(
                GodTool.sync("labyrinth",
                        () -> "A punishment (or a trial): take a mortal out of the world into a labyrinth that exists nowhere "
                                + "else, a maze in a sealed void of its own where nothing can be broken, dug or climbed. They "
                                + "are free, back exactly where they stood, when they find the gold at its heart or when their "
                                + "time runs out; dying there only returns them to its start. You are told how it ends. "
                                + "action release frees one early (your mercy). Sizes: small (a minute or two), medium, large, "
                                + "vast (long and cruel). At most " + Labyrinths.maxMinutes() + " minutes. In it now: "
                                + Labyrinths.held() + ".",
                        Schema.object()
                                .choice("action", "cast (default) or release.", false, List.of("cast", "release"))
                                .str("player", "The mortal.", true)
                                .choice("size", "How large the maze is. Default medium.", false, Maze.Size.names())
                                .choice("style", "What it is built of: violet, divine, dark, rustic, stone, or auto (suits "
                                        + "you). Default auto.", false, List.of("auto", "violet", "divine", "dark", "rustic", "stone"))
                                .integer("minutes", "How long before the walls let them go by themselves. Default 10.", false, 1, 120),
                        DimensionTools::labyrinth),
                GodTool.sync("make_world",
                        () -> "Make a whole world of your own beside this one, a dimension that did not exist: void (nothing "
                                + "but a small platform, for you to fill), flat, normal (new lands), nether or end. create makes "
                                + "it (or finds the one of that name), send takes a mortal to it (name 'home' returns them to the "
                                + "first world's spawn), list names them, unmake closes one (its mortals are sent home; nothing "
                                + "is deleted from disk). A sealed world cannot be changed by mortals at all. Your other powers "
                                + "still act in the world you rule; this only makes places and moves mortals between them. "
                                + "At most " + GodWorlds.max() + " god-made worlds. They are: " + GodWorlds.names() + ".",
                        Schema.object()
                                .choice("action", "What to do.", true, List.of("create", "send", "list", "unmake"))
                                .str("name", "The world's name, a few plain words, e.g. ashen_reach ('home' with send).", false)
                                .choice("kind", "create: what the world is. Default void.", false, GodWorlds.KINDS)
                                .bool("sealed", "create: mortals cannot break or place anything there. Default false.", false)
                                .str("player", "send: the mortal to take there.", false),
                        DimensionTools::world));
    }

    private static void mayAct() throws ToolException {
        if (GodSettings.initiativeLevel() == Initiative.Level.WATCH) {
            throw new ToolException("The keepers of this server want you only to watch (initiative.level is watch).");
        }
    }

    private static ToolResult labyrinth(Args a) throws ToolException {
        if (a.str("action", "cast").equalsIgnoreCase("release")) {
            // A mortal in the labyrinth is outside the world you rule (and may be offline): they are found by name.
            String freed = Labyrinths.releaseByName(a.str("player"), "you let them go");
            if (freed == null) {
                throw new ToolException(a.str("player") + " is not in the labyrinth. In it now: " + Labyrinths.held() + ".");
            }
            return ToolResult.ok("The labyrinth opens and %s stands where they stood before.", freed);
        }
        Player p = Targets.player(a.str("player"));
        mayAct();
        Maze.Size size = Maze.Size.of(a.str("size", "medium"));
        if (size == null) throw new ToolException("A labyrinth is " + String.join(", ", Maze.Size.names()) + ".");
        try {
            return ToolResult.ok(Labyrinths.cast(Realm.current(), p, size, Style.resolve(a.str("style", "auto")),
                    a.integer("minutes", 10, 1, 120)));
        } catch (GodWorlds.Refusal e) {
            throw new ToolException(e.getMessage());
        }
    }

    private static ToolResult world(Args a) throws ToolException {
        String action = a.str("action").toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "list":
                    return ToolResult.ok("God-made worlds: %s.", GodWorlds.names());
                case "create": {
                    mayAct();
                    if (!a.has("name")) throw new ToolException("Give the new world a name.");
                    String kind = a.str("kind", "void").toLowerCase(Locale.ROOT);
                    boolean existed = GodWorlds.find(a.str("name")) != null;
                    World w = GodWorlds.create(a.str("name"), kind, Realm.current().id, a.bool("sealed", false));
                    if (!existed && kind.equals("void")) {
                        // Somewhere to stand in the nothing.
                        for (int x = -3; x <= 3; x++) {
                            for (int z = -3; z <= 3; z++) w.getBlockAt(x, 64, z).setType(Material.SMOOTH_STONE, false);
                        }
                    }
                    return ToolResult.ok(existed ? "The world '%s' already exists (%s)." : "A new world exists: '%s' (%s). Send "
                            + "mortals there with action send.", GodWorlds.key(a.str("name")), GodWorlds.find(a.str("name")).kind());
                }
                case "send": {
                    mayAct();
                    // Whoever it is may stand in one of your made worlds, outside the world you rule.
                    Player p = Bukkit.getPlayerExact(a.str("player", ""));
                    if (p == null) p = Targets.player(a.str("player", ""));
                    if (Labyrinths.holds(p.getUniqueId())) throw new ToolException(p.getName() + " is in the labyrinth; release them first.");
                    String name = a.str("name", "home");
                    if (name.equalsIgnoreCase("home")) {
                        p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                        return ToolResult.ok("%s is returned to the first world.", p.getName());
                    }
                    GodWorlds.Made m = GodWorlds.find(name);
                    World w = m == null ? null : GodWorlds.world(m);
                    if (w == null || m.name().equals(Labyrinths.WORLD)) {
                        throw new ToolException("No god-made world to send them to is called '" + name + "'. They are: " + GodWorlds.names()
                                + " (the labyrinth is entered only by the labyrinth power).");
                    }
                    Location spawn = w.getSpawnLocation().add(0.5, 0, 0.5);
                    if (!m.kind().equals("void")) spawn.setY(w.getHighestBlockYAt(spawn) + 1);
                    p.setFallDistance(0);
                    p.teleport(spawn);
                    return ToolResult.ok("%s stands in '%s' now.", p.getName(), m.name());
                }
                case "unmake": {
                    mayAct();
                    if (!a.has("name")) throw new ToolException("Which world?");
                    return ToolResult.ok("The world '%s' is closed; any mortals in it were sent home.", GodWorlds.unmake(a.str("name")));
                }
                default:
                    throw new ToolException("action is create, send, list or unmake.");
            }
        } catch (GodWorlds.Refusal e) {
            throw new ToolException(e.getMessage());
        }
    }
}
