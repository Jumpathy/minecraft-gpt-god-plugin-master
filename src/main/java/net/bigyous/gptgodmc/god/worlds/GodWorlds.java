package net.bigyous.gptgodmc.god.worlds;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * Worlds the gods make (make_world, labyrinth): whole dimensions of their own beside the server's. Each is a
 * real Bukkit world named god_&lt;name&gt;, listed in plugins/gptgodmc/worlds.json and loaded again at start; when
 * Multiverse-Core is installed it is told of each one (mv import), so /mv commands know them. A "sealed" world
 * cannot be changed by mortals: nothing in it breaks, burns or blows up. Server thread only.
 */
public final class GodWorlds implements Listener {
    public static final String PREFIX = "god_";
    public static final List<String> KINDS = List.of("void", "flat", "normal", "nether", "end");
    /** The generator Multiverse is given for a void world: gptgodmc:void (see GPTGOD.getDefaultWorldGenerator). */
    public static final String VOID_ID = "void";

    /** Refusals are phrased for the god. */
    public static final class Refusal extends Exception {
        private static final long serialVersionUID = 1L;

        public Refusal(String message) {
            super(message);
        }
    }

    /** One made world. sealed: mortals cannot change it. */
    public record Made(String name, String kind, String realm, boolean sealed, long made) {
        public String worldName() {
            return PREFIX + name;
        }
    }

    /** A world of nothing: every chunk is empty air. */
    public static final class VoidGenerator extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World world, java.util.Random random) {
            return new Location(world, 0.5, 65, 0.5);
        }
    }

    private static final Map<String, Made> MADE = new LinkedHashMap<>();
    private static JavaPlugin plugin;

    private GodWorlds() {
    }

    public static boolean enabled() {
        return plugin != null && plugin.getConfig().getBoolean("worlds.enabled", true);
    }

    public static int max() {
        return plugin == null ? 4 : Math.max(0, plugin.getConfig().getInt("worlds.max", 4));
    }

    private static Path file() {
        return plugin.getDataFolder().toPath().resolve("worlds.json");
    }

    public static void enable(JavaPlugin p) {
        plugin = p;
        Bukkit.getPluginManager().registerEvents(new GodWorlds(), p);
        try {
            if (Files.exists(file())) {
                for (JsonElement e : JsonParser.parseString(Files.readString(file())).getAsJsonObject().getAsJsonArray("worlds")) {
                    JsonObject o = e.getAsJsonObject();
                    Made m = new Made(o.get("name").getAsString(), o.get("kind").getAsString(),
                            o.has("realm") ? o.get("realm").getAsString() : "main", o.get("sealed").getAsBoolean(), o.get("made").getAsLong());
                    MADE.put(m.name(), m);
                }
            }
        } catch (IOException | RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not read worlds.json: " + e);
        }
        if (!enabled()) return;
        for (Made m : new ArrayList<>(MADE.values())) {
            try {
                load(m);
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("Could not load the god's world " + m.worldName() + ": " + e);
            }
        }
        // Worlds made before this was known are put right too (once Multiverse is listening).
        Bukkit.getScheduler().runTask(p, () -> {
            if (plugin != null && plugin.getConfig().getBoolean("worlds.multiverse-import", true)) MADE.values().forEach(GodWorlds::settle);
        });
        Labyrinths.enable(p);
    }

    public static void disable() {
        Labyrinths.disable();
        MADE.clear();
        plugin = null;
    }

    /** What Bukkit and Multiverse get when they ask this plugin for a generator by id. */
    public static ChunkGenerator generator(String id) {
        return id == null || id.isBlank() || id.equalsIgnoreCase(VOID_ID) ? new VoidGenerator() : null;
    }

    public static List<Made> all() {
        return new ArrayList<>(MADE.values());
    }

    public static Made find(String name) {
        return MADE.get(key(name));
    }

    /** The record of a world, or null when no god made it. */
    public static Made of(World world) {
        if (world == null || !world.getName().startsWith(PREFIX)) return null;
        return MADE.get(world.getName().substring(PREFIX.length()));
    }

    public static World world(Made m) {
        return Bukkit.getWorld(m.worldName());
    }

    /** A name the god may give a world: lowercase words joined by underscores, at most 24 characters. */
    public static String key(String name) {
        if (name == null) return "";
        String k = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (k.startsWith(PREFIX)) k = k.substring(PREFIX.length());
        return k.length() > 24 ? k.substring(0, 24) : k;
    }

    /** Makes a world (or returns the one of that name this god already made). */
    public static World create(String name, String kind, String realm, boolean sealed) throws Refusal {
        if (!enabled()) throw new Refusal("The keepers of this server do not let you make worlds (worlds.enabled is off).");
        String key = key(name);
        if (key.isEmpty()) throw new Refusal("Give the world a name of a few plain words, e.g. 'ashen_reach'.");
        String k = kind == null ? "void" : kind.toLowerCase(Locale.ROOT);
        if (!KINDS.contains(k)) throw new Refusal("A world is one of: " + String.join(", ", KINDS) + ".");
        Made have = MADE.get(key);
        if (have != null) {
            World w = world(have);
            return w != null ? w : load(have);
        }
        if (MADE.size() >= max()) {
            throw new Refusal("There are already " + MADE.size() + " god-made worlds, the most this server allows (" + names()
                    + "). Unmake one first.");
        }
        Made m = new Made(key, k, realm, sealed, System.currentTimeMillis());
        World w = load(m);
        MADE.put(key, m);
        save();
        tellMultiverse(m);
        return w;
    }

    public static String names() {
        List<String> out = new ArrayList<>();
        for (Made m : MADE.values()) out.add(m.name() + " (" + m.kind() + (m.sealed() ? ", sealed" : "") + ")");
        return out.isEmpty() ? "none" : String.join(", ", out);
    }

    private static World load(Made m) {
        // Multiverse loads the worlds it knows before this plugin is enabled, and so without their generator: a void
        // world it loaded that way is let go and made again properly (see also settle).
        World loaded = Bukkit.getWorld(m.worldName());
        if (loaded != null) {
            if (!m.kind().equals("void") || loaded.getGenerator() instanceof VoidGenerator) return loaded;
            Bukkit.unloadWorld(loaded, true);
        }
        WorldCreator c = new WorldCreator(m.worldName());
        switch (m.kind()) {
            case "void" -> c.generator(new VoidGenerator()).generateStructures(false);
            case "flat" -> c.type(WorldType.FLAT).generateStructures(false);
            case "nether" -> c.environment(World.Environment.NETHER);
            case "end" -> c.environment(World.Environment.THE_END);
            default -> {
            }
        }
        World w = Bukkit.createWorld(c);
        if (w == null) throw new IllegalStateException("the server would not make " + m.worldName());
        if (m.kind().equals("void")) {
            // A place outside the world: no day, no weather, nothing that wanders in.
            w.setGameRule(GameRules.SPAWN_MOBS, false);
            w.setGameRule(GameRules.ADVANCE_TIME, false);
            w.setGameRule(GameRules.ADVANCE_WEATHER, false);
            w.setTime(18000);
            w.setSpawnLocation(0, 65, 0);
        }
        return w;
    }

    /** Multiverse-Core, when installed, is told of the world so its own commands and lists know it. */
    private static void tellMultiverse(Made m) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core") || !plugin.getConfig().getBoolean("worlds.multiverse-import", true)) return;
        String env = m.kind().equals("nether") ? "nether" : m.kind().equals("end") ? "the_end" : "normal";
        String command = "mv import " + m.worldName() + " " + env + (m.kind().equals("void") ? " -g " + plugin.getName() + ":" + VOID_ID : "");
        try {
            boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            GPTGOD.LOGGER.info("Told Multiverse of " + m.worldName() + (ok ? "" : " (it did not take the command '" + command + "')"));
            settle(m);
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("Multiverse did not take '" + command + "': " + e);
        }
    }

    /**
     * Multiverse must not load a god-made world by itself at start (it would do so before this plugin is enabled,
     * without the world's generator, and a void would fill with land): this plugin loads its own worlds.
     */
    private static void settle(Made m) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")) return;
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify " + m.worldName() + " set auto-load false");
            if (m.sealed()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify " + m.worldName() + " set gamemode adventure");
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("Multiverse would not change its settings for " + m.worldName() + ": " + e);
        }
    }

    /**
     * Unmakes a god-made world: everyone in it is sent to the server's first world, it is unloaded and
     * forgotten. Its folder stays on disk (nothing is deleted), so a keeper can bring it back.
     */
    public static String unmake(String name) throws Refusal {
        Made m = MADE.get(key(name));
        if (m == null) throw new Refusal("No god made a world called '" + name + "'. God-made worlds: " + names() + ".");
        World w = world(m);
        if (w != null) {
            Location out = Bukkit.getWorlds().get(0).getSpawnLocation();
            for (Player p : w.getPlayers()) {
                if (!Labyrinths.release(p.getUniqueId(), "the world was unmade")) p.teleport(out);
            }
            if (!Bukkit.unloadWorld(w, true)) throw new Refusal("The server would not let go of that world yet; try again in a moment.");
        }
        MADE.remove(m.name());
        save();
        if (Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv remove " + m.worldName());
            } catch (RuntimeException ignored) {
                // Multiverse keeps its own list; a keeper can tidy it
            }
        }
        return m.name();
    }

    private static void save() {
        JsonArray list = new JsonArray();
        for (Made m : MADE.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", m.name());
            o.addProperty("kind", m.kind());
            o.addProperty("realm", m.realm());
            o.addProperty("sealed", m.sealed());
            o.addProperty("made", m.made());
            list.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("worlds", list);
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), root.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            GPTGOD.LOGGER.warn("Could not save worlds.json: " + e);
        }
    }

    // ---- Sealed worlds cannot be changed by mortals ---------------------------------------------------

    private static boolean sealed(World w) {
        Made m = of(w);
        return m != null && m.sealed();
    }

    /** Keepers in creative mode may still build in a sealed world. */
    private static boolean keeper(Player p) {
        return p.hasPermission("gptgodmc.admin") && p.getGameMode() == org.bukkit.GameMode.CREATIVE;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (sealed(e.getBlock().getWorld()) && !keeper(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (sealed(e.getBlock().getWorld()) && !keeper(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent e) {
        if (sealed(e.getBlock().getWorld()) && !keeper(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent e) {
        if (sealed(e.getBlock().getWorld()) && !keeper(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (sealed(e.getLocation().getWorld())) e.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (sealed(e.getBlock().getWorld())) e.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (sealed(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChange(EntityChangeBlockEvent e) {
        if (sealed(e.getBlock().getWorld())) e.setCancelled(true);
    }
}
