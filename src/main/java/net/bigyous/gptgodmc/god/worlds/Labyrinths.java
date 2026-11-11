package net.bigyous.gptgodmc.god.worlds;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.build.Style;
import net.bigyous.gptgodmc.loggables.GenericEventLoggable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The labyrinth, a punishment: a mortal is taken to a maze in a sealed world of its own (god_labyrinth, a
 * void with nothing else in it) and is free again when they reach the gold at its heart, or when their time
 * runs out, whichever comes first. Nothing there can be broken; they are in adventure mode; dying puts them
 * back at the start; ender pearls and chorus fruit do not work. The mortal's place, game mode and sentence
 * are kept in plugins/gptgodmc/labyrinth.json, so a restart or a logout frees nobody and strands nobody.
 * Server thread only.
 */
public final class Labyrinths implements Listener {
    public static final String WORLD = "labyrinth";
    /** Mazes stand this far apart, so two prisoners never meet. */
    static final int PLOT = 256;
    static final int FLOOR_Y = 64, WALL = 5;
    /** Blocks set a tick while a maze rises. */
    static final int PER_TICK = 6000;

    /** One mortal's sentence. Coordinates are blocks in the labyrinth world; back is where they came from. */
    static final class Sentence {
        String player, realm, backWorld, mode;
        double backX, backY, backZ;
        float backYaw;
        double startX, startY, startZ, exitX, exitZ;
        int plot, cells, length;
        long since, until;
        boolean inside;
    }

    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<UUID, Sentence> HELD = new HashMap<>();
    private static JavaPlugin plugin;
    private static BukkitTask ticker;
    private static int nextPlot;

    private Labyrinths() {
    }

    public static boolean enabled() {
        return plugin != null && plugin.getConfig().getBoolean("labyrinth.enabled", true);
    }

    public static int maxMinutes() {
        return plugin == null ? 20 : Math.max(1, plugin.getConfig().getInt("labyrinth.max-minutes", 20));
    }

    private static Path file() {
        return plugin.getDataFolder().toPath().resolve("labyrinth.json");
    }

    static void enable(JavaPlugin p) {
        plugin = p;
        Bukkit.getPluginManager().registerEvents(new Labyrinths(), p);
        try {
            if (Files.exists(file())) {
                Map<String, Sentence> read = GSON.fromJson(Files.readString(file()), new TypeToken<Map<String, Sentence>>() {
                }.getType());
                if (read != null) read.forEach((id, s) -> HELD.put(UUID.fromString(id), s));
            }
        } catch (IOException | RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not read labyrinth.json: " + e);
        }
        for (Sentence s : HELD.values()) nextPlot = Math.max(nextPlot, s.plot + 1);
        ticker = Bukkit.getScheduler().runTaskTimer(p, Labyrinths::tick, 40L, 10L);
    }

    static void disable() {
        if (ticker != null) ticker.cancel();
        ticker = null;
        HELD.clear();
        plugin = null;
    }

    private static void save() {
        Map<String, Sentence> out = new HashMap<>();
        HELD.forEach((id, s) -> out.put(id.toString(), s));
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(out), StandardCharsets.UTF_8);
        } catch (IOException e) {
            GPTGOD.LOGGER.warn("Could not save labyrinth.json: " + e);
        }
    }

    public static boolean holds(UUID id) {
        return HELD.containsKey(id);
    }

    /** "Steve (4 min left)", for the god. */
    public static String held() {
        List<String> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Sentence s : HELD.values()) out.add(s.player + " (" + Math.max(1, (s.until - now + 59_999) / 60_000) + " min left)");
        return out.isEmpty() ? "nobody" : String.join(", ", out);
    }

    /**
     * Raises a maze and casts a mortal into it.
     *
     * @return what happened, for the god
     */
    public static String cast(Realm realm, Player p, Maze.Size size, Style style, int minutes) throws GodWorlds.Refusal {
        if (!enabled()) throw new GodWorlds.Refusal("The keepers of this server have closed the labyrinth (labyrinth.enabled is off).");
        if (HELD.containsKey(p.getUniqueId())) throw new GodWorlds.Refusal(p.getName() + " is already in the labyrinth.");
        World world = GodWorlds.create(WORLD, "void", realm.id, true);
        int mins = Math.max(1, Math.min(maxMinutes(), minutes));
        Maze maze = new Maze(size.cells, System.nanoTime());
        Sentence s = new Sentence();
        s.player = p.getName();
        s.realm = realm.id;
        s.plot = nextPlot++;
        s.cells = maze.cells;
        s.length = maze.length;
        Location back = p.getLocation();
        s.backWorld = back.getWorld().getName();
        s.backX = back.getX();
        s.backY = back.getY();
        s.backZ = back.getZ();
        s.backYaw = back.getYaw();
        s.mode = p.getGameMode().name();
        int ox = s.plot * PLOT, oz = 0;
        double[] start = maze.middle(maze.startX, maze.startZ, size.path), exit = maze.middle(maze.exitX, maze.exitZ, size.path);
        s.startX = ox + start[0];
        s.startY = FLOOR_Y + 1;
        s.startZ = oz + start[1];
        s.exitX = ox + exit[0];
        s.exitZ = oz + exit[1];
        s.since = System.currentTimeMillis();
        s.until = s.since + mins * 60_000L;
        HELD.put(p.getUniqueId(), s);
        save();
        raise(world, maze, size.path, ox, oz, style, () -> enter(p.getUniqueId()));
        return String.format("%s is taken to a %s labyrinth of %s (%d by %d cells; the way out is %d cells long). They are "
                + "free when they reach the gold at its heart, or after %d minutes. You will be told how it ends.", p.getName(),
                size.name().toLowerCase(java.util.Locale.ROOT), style.id(), maze.cells, maze.cells, maze.length, mins);
    }

    /** Sets the maze's blocks a few thousand a tick, then runs done. */
    private static void raise(World world, Maze maze, int path, int ox, int oz, Style style, Runnable done) {
        boolean[][] wall = maze.plan(path);
        int span = wall.length;
        double[] exit = maze.middle(maze.exitX, maze.exitZ, path);
        new BukkitRunnable() {
            int x, z;

            @Override
            public void run() {
                int budget = PER_TICK;
                while (budget > 0 && x < span) {
                    boolean isWall = wall[x][z];
                    boolean heart = Math.abs(x + 0.5 - exit[0]) < path / 2.0 + 0.01 && Math.abs(z + 0.5 - exit[1]) < path / 2.0 + 0.01;
                    Material floor = heart ? Material.GOLD_BLOCK : !isWall && (x * 31 + z * 17) % 23 == 0 ? Material.SEA_LANTERN : style.floor();
                    world.getBlockAt(ox + x, FLOOR_Y, oz + z).setType(floor, false);
                    for (int y = 1; y <= WALL; y++) {
                        world.getBlockAt(ox + x, FLOOR_Y + y, oz + z).setType(isWall ? (y == WALL ? style.trim() : style.wall()) : Material.AIR, false);
                    }
                    // A lid nobody can see or climb past.
                    world.getBlockAt(ox + x, FLOOR_Y + WALL + 1, oz + z).setType(Material.BARRIER, false);
                    budget -= WALL + 2;
                    if (++z >= span) {
                        z = 0;
                        x++;
                    }
                }
                if (x >= span) {
                    cancel();
                    done.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Puts a held mortal at the start of their maze (when it has risen, or when they come back online). */
    private static void enter(UUID id) {
        Sentence s = HELD.get(id);
        Player p = Bukkit.getPlayer(id);
        GodWorlds.Made made = GodWorlds.find(WORLD);
        World world = made == null ? null : GodWorlds.world(made);
        if (s == null || p == null || world == null) return;
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(false);
        p.setFallDistance(0);
        s.inside = true;
        p.teleport(new Location(world, s.startX, s.startY, s.startZ));
        p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.6f, 0.6f);
        p.sendMessage(Component.text("You stand in the labyrinth. Find the gold at its heart and you are free.", NamedTextColor.GOLD));
        save();
    }

    /** Frees a mortal: back where they were, as they were. False when they were not held. */
    public static boolean release(UUID id, String why) {
        Sentence s = HELD.remove(id);
        if (s == null) return false;
        save();
        Player p = Bukkit.getPlayer(id);
        if (p != null) restore(p, s);
        else PENDING.put(id, s);
        long minutes = Math.max(0, (System.currentTimeMillis() - s.since) / 60_000);
        Realm realm = Realm.byId(s.realm);
        if (realm != null) {
            String text = s.player + " is out of the labyrinth after " + minutes + " min: " + why + ".";
            realm.run(() -> EventLogger.log(() -> new GenericEventLoggable(text, text)));
        }
        return true;
    }

    /** Frees the held mortal of this name (online or not). Their name as it is written, or null when none is held. */
    public static String releaseByName(String name) {
        return releaseByName(name, "they were let go");
    }

    public static String releaseByName(String name, String why) {
        for (Map.Entry<UUID, Sentence> e : new ArrayList<>(HELD.entrySet())) {
            if (e.getValue().player.equalsIgnoreCase(name == null ? "" : name.trim())) {
                String written = e.getValue().player;
                release(e.getKey(), why);
                return written;
            }
        }
        return null;
    }

    /** Mortals freed while offline: they are put back in their own place when they next join. */
    private static final Map<UUID, Sentence> PENDING = new HashMap<>();

    private static void restore(Player p, Sentence s) {
        World w = Bukkit.getWorld(s.backWorld);
        Location back = w == null ? Bukkit.getWorlds().get(0).getSpawnLocation() : new Location(w, s.backX, s.backY, s.backZ, s.backYaw, 0);
        p.setFallDistance(0);
        p.teleport(back);
        try {
            p.setGameMode(GameMode.valueOf(s.mode));
        } catch (IllegalArgumentException e) {
            p.setGameMode(Bukkit.getDefaultGameMode());
        }
        p.playSound(back, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
    }

    private static void tick() {
        if (HELD.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Sentence> e : new ArrayList<>(HELD.entrySet())) {
            Sentence s = e.getValue();
            if (now >= s.until) {
                release(e.getKey(), "their time ran out and the walls let them go");
                continue;
            }
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !s.inside || GodWorlds.of(p.getWorld()) == null) continue;
            // Multiverse (and others) set a world's own game mode a moment after anyone arrives: put it back.
            if (p.getGameMode() != GameMode.ADVENTURE && p.getGameMode() != GameMode.SPECTATOR) p.setGameMode(GameMode.ADVENTURE);
            Location at = p.getLocation();
            if (Math.abs(at.getX() - s.exitX) < 1.3 && Math.abs(at.getZ() - s.exitZ) < 1.3 && at.getY() < FLOOR_Y + 3) {
                p.sendMessage(Component.text("You found the heart of the labyrinth. You are free.", NamedTextColor.GOLD));
                release(e.getKey(), "they found the way out by themselves");
                continue;
            }
            long left = (s.until - now + 999) / 1000;
            p.sendActionBar(Component.text(String.format("The labyrinth holds you  %d:%02d", left / 60, left % 60), NamedTextColor.GRAY));
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Sentence s = HELD.get(e.getPlayer().getUniqueId());
        GodWorlds.Made made = GodWorlds.find(WORLD);
        World world = made == null ? null : GodWorlds.world(made);
        if (s != null && s.inside && world != null) e.setRespawnLocation(new Location(world, s.startX, s.startY, s.startZ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!HELD.containsKey(e.getPlayer().getUniqueId())) return;
        PlayerTeleportEvent.TeleportCause cause = e.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || cause == PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        Sentence freed = PENDING.remove(id);
        if (freed != null) {
            Bukkit.getScheduler().runTask(plugin, () -> restore(p, freed));
            return;
        }
        GodWorlds.Made here = GodWorlds.of(p.getWorld());
        boolean inLabyrinth = here != null && here.name().equals(WORLD);
        if (HELD.containsKey(id)) {
            if (!inLabyrinth) Bukkit.getScheduler().runTask(plugin, () -> enter(id));
        } else if (inLabyrinth && !p.hasPermission("gptgodmc.admin")) {
            // Left behind by a sentence that ended while they were away and was since forgotten.
            Bukkit.getScheduler().runTask(plugin, () -> p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation()));
        }
    }
}
