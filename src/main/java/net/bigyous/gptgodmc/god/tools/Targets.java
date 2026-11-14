package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.StructureManager;
import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.utils.NicknameCommand;

/** Resolves the names the god uses ("Steve", "my vessel", "12 64 -30", a structure) to the world. */
public final class Targets {
    public static final String TARGET_HELP = "a player name, a structure name, \"vessel\" (your body), \"spawn\", "
            + "or coordinates \"x y z\" (optionally prefixed by a world name, also a world you made: "
            + "\"god_ashen_reach 0 64 0\")";

    private Targets() {
    }

    public static Player player(String name) throws ToolException {
        String wanted = name.trim();
        if (wanted.startsWith("@")) {
            wanted = wanted.substring(1);
        }
        Player exact = Bukkit.getPlayerExact(wanted);
        if (exact != null) {
            if (!Realm.current().governs(exact.getWorld())) {
                // Another god's world, or one no god rules: this god has no reach there.
                throw new ToolException(exact.getName() + " has left your realm (now in "
                        + exact.getWorld().getName() + "), beyond your sight and power. Present: " + onlineNames()
                        + ".");
            }
            return exact;
        }
        Player prefix = null;
        int prefixMatches = 0;
        for (Player p : Realm.current().players()) {
            String nick = NicknameCommand.getNickname(p);
            if (p.getName().equalsIgnoreCase(wanted) || (!nick.isBlank() && nick.equalsIgnoreCase(wanted))) {
                return p;
            }
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(wanted.toLowerCase(Locale.ROOT))) {
                prefix = p;
                prefixMatches++;
            }
        }
        if (prefixMatches == 1) {
            return prefix;
        }
        throw new ToolException("No mortal named '" + name + "' is in the world. Present: " + onlineNames() + ".");
    }

    /** "all"/"everyone" for every online player in survival or adventure, else one player. */
    public static List<Player> players(String nameOrAll) throws ToolException {
        String v = nameOrAll.trim().toLowerCase(Locale.ROOT);
        if (v.equals("all") || v.equals("everyone") || v.equals("@a")) {
            List<Player> out = new ArrayList<>();
            for (Player p : Realm.current().players()) {
                if (p.getGameMode() != GameMode.SPECTATOR) {
                    out.add(p);
                }
            }
            if (out.isEmpty()) {
                throw new ToolException("No living mortals are present.");
            }
            return out;
        }
        return List.of(player(nameOrAll));
    }

    public static Location location(String target) throws ToolException {
        String t = target.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.equals("vessel") || lower.equals("me") || lower.equals("self") || lower.equals("my vessel")
                || lower.equals("yourself")) {
            Location v = God.vessel().location();
            if (v == null) {
                throw new ToolException("Your vessel is not manifested. Use vessel_go to manifest it first.");
            }
            return v;
        }
        if (lower.equals("spawn")) {
            return Realm.current().world().getSpawnLocation();
        }
        if (lower.startsWith("structure:")) {
            t = t.substring("structure:".length()).trim();
        }
        if (StructureManager.hasStructure(t)) {
            Location at = StructureManager.getStructure(t).getLocation();
            if (Realm.current().governs(at.getWorld())) {
                return at;
            }
        }
        Location coords = parseCoords(t);
        if (coords != null) {
            return coords;
        }
        try {
            return player(t).getLocation();
        } catch (ToolException e) {
            if (Bukkit.getPlayerExact(t) != null) {
                throw e; // a real mortal, but outside this god's realm
            }
            throw new ToolException("Unknown target '" + target + "'. Use " + TARGET_HELP + ". Present mortals: "
                    + onlineNames() + "." + structureHint());
        }
    }

    /**
     * Parses "x y z", "x,y,z" or "world x y z"; null when the text is not coordinates. Plain
     * coordinates mean the god's home world; a named world must be one of its own.
     */
    public static Location parseCoords(String text) throws ToolException {
        String[] parts = text.replace(",", " ").trim().split("\\s+");
        Realm realm = Realm.current();
        World world = realm.world();
        int offset = 0;
        if (parts.length == 4) {
            World named = worldNamed(parts[0]);
            if (named == null) {
                return null;
            }
            if (!realm.governs(named)) {
                throw new ToolException("The world '" + named.getName() + "' lies outside your realm; you have no "
                        + "power there. Your worlds: " + worldNames() + ".");
            }
            world = named;
            offset = 1;
        } else if (parts.length != 3) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[offset].replace("~", ""));
            double y = Double.parseDouble(parts[offset + 1].replace("~", ""));
            double z = Double.parseDouble(parts[offset + 2].replace("~", ""));
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** One of the god's own worlds by name (its realm's, or one it made); refuses any other. */
    public static World world(String name) throws ToolException {
        World w = name == null || name.isBlank() ? Realm.current().world() : worldNamed(name.trim());
        if (w == null) {
            throw new ToolException("You have no world called '" + name + "'. Your worlds: " + worldNames() + ".");
        }
        if (!Realm.current().governs(w)) {
            throw new ToolException("The world '" + w.getName() + "' lies outside your realm; you have no power "
                    + "there. Your worlds: " + worldNames() + ".");
        }
        return w;
    }

    /** A world by its name, or by "nether" / "end" / "overworld" within the current god's realm. */
    private static World worldNamed(String name) {
        World exact = Bukkit.getWorld(name);
        if (exact != null) {
            return exact;
        }
        // A god-made world by the name the god gave it ("ashen_reach" for god_ashen_reach).
        net.bigyous.gptgodmc.god.worlds.GodWorlds.Made made = net.bigyous.gptgodmc.god.worlds.GodWorlds.find(name);
        if (made != null) {
            World w = net.bigyous.gptgodmc.god.worlds.GodWorlds.world(made);
            if (w != null) return w;
        }
        World.Environment wanted = switch (name.toLowerCase(Locale.ROOT)) {
            case "nether", "the_nether" -> World.Environment.NETHER;
            case "end", "the_end" -> World.Environment.THE_END;
            case "overworld" -> World.Environment.NORMAL;
            default -> null;
        };
        if (wanted == null) {
            return null;
        }
        for (World w : Realm.current().worldList()) {
            if (w.getEnvironment() == wanted) {
                return w;
            }
        }
        return null;
    }

    /** The names of the worlds the current god rules. */
    public static String worldNames() {
        List<String> names = new ArrayList<>();
        for (World w : Realm.current().worldList()) {
            names.add(w.getName());
        }
        return String.join(", ", names);
    }

    /** The mortals inside the current god's realm. */
    public static String onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Realm.current().players()) {
            names.add(p.getName());
        }
        return names.isEmpty() ? "nobody" : String.join(", ", names);
    }

    private static String structureHint() {
        List<String> structures = StructureManager.getStructures();
        return structures.isEmpty() ? "" : " Structures: " + String.join(", ", structures) + ".";
    }

    public static String describe(Location l) {
        return String.format("(%d, %d, %d in %s)", l.getBlockX(), l.getBlockY(), l.getBlockZ(),
                l.getWorld() == null ? "?" : l.getWorld().getName());
    }
}
