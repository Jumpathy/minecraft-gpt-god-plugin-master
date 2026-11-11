package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.living.Servants;

/** Reads the living land around mortals for the perception and survey_area. Server thread only. */
public final class LandSense {
    /** Mortals closer than this to a group's first member share its line. */
    private static final int GROUP_BLOCKS = 48;
    private static final int MAX_GROUPS = 4;
    private static final int SPACING = 2;

    private LandSense() {
    }

    public static boolean crop(Material m) {
        return Tag.CROPS.isTagged(m) || m == Material.NETHER_WART || m == Material.SWEET_BERRY_BUSH
                || m == Material.COCOA;
    }

    public static boolean sapling(Material m) {
        return Tag.SAPLINGS.isTagged(m) || m == Material.MANGROVE_PROPAGULE || m == Material.CRIMSON_FUNGUS
                || m == Material.WARPED_FUNGUS;
    }

    public static boolean ripe(Block b) {
        return b.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge();
    }

    /** What lives and grows within {@code radius} blocks of a place. */
    public static Ecology.Land sense(Location c, int radius) {
        World w = c.getWorld();
        Map<String, Integer> animals = new TreeMap<>();
        int tamed = 0, villagers = 0, golems = 0;
        for (Entity e : w.getNearbyEntities(c, radius, 16, radius)) {
            if (Servants.isServantEntity(e)) continue;
            if (e instanceof Villager) {
                villagers++;
            } else if (e instanceof IronGolem) {
                golems++;
            } else if (e instanceof Animals) {
                animals.merge(e.getType().getKey().getKey(), 1, Integer::sum);
                if (e instanceof Tameable t && t.isTamed()) tamed++;
            }
        }
        boolean surface = w.getEnvironment() == World.Environment.NORMAL;
        int crops = 0, ripe = 0, canopy = 0, sampled = 0;
        if (surface) {
            int cx = c.getBlockX(), cz = c.getBlockZ();
            for (int dx = -radius; dx <= radius; dx += SPACING) {
                for (int dz = -radius; dz <= radius; dz += SPACING) {
                    int x = cx + dx, z = cz + dz;
                    if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                    Block top = w.getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE);
                    if (top.getType() == Material.SNOW) top = top.getRelative(BlockFace.DOWN);
                    sampled++;
                    Material m = top.getType();
                    if (Tag.LEAVES.isTagged(m)) {
                        canopy++;
                    } else if (crop(m)) {
                        crops++;
                        if (ripe(top)) ripe++;
                    }
                }
            }
        }
        return new Ecology.Land(c.getBlock().getBiome().getKey().getKey(), animals, tamed, villagers, golems, crops,
                ripe, canopy, sampled, SPACING, surface);
    }

    /** "## The living land around mortals": a line per group of mortals, then their mark on it. Empty when off. */
    public static String perceive(Realm realm, List<Player> mortals) {
        if (!GodSettings.ecologyEnabled()) return "";
        try {
            int radius = GodSettings.ecologyRadius();
            StringBuilder lines = new StringBuilder();
            List<Ecology.Spot> spots = new ArrayList<>();
            List<List<Player>> groups = new ArrayList<>();
            for (Player p : mortals) {
                if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
                Location l = p.getLocation();
                spots.add(new Ecology.Spot(l.getWorld().getName(), l.getBlockX(), l.getBlockZ()));
                List<Player> home = null;
                for (List<Player> g : groups) {
                    Location a = g.get(0).getLocation();
                    if (a.getWorld().equals(l.getWorld()) && a.distanceSquared(l) <= GROUP_BLOCKS * GROUP_BLOCKS) {
                        home = g;
                        break;
                    }
                }
                if (home == null) {
                    home = new ArrayList<>();
                    groups.add(home);
                }
                home.add(p);
            }
            for (int i = 0; i < Math.min(MAX_GROUPS, groups.size()); i++) {
                List<Player> g = groups.get(i);
                Location l = g.get(0).getLocation();
                List<String> names = new ArrayList<>();
                g.forEach(p -> names.add(p.getName()));
                lines.append("- around ").append(String.join(", ", names)).append(" (").append(l.getBlockX())
                        .append(", ").append(l.getBlockZ()).append("): ").append(Ecology.describe(sense(l, radius)))
                        .append('\n');
            }
            Memory m = realm.memory();
            String marks;
            synchronized (m) {
                marks = Ecology.render(m.land, spots, System.currentTimeMillis(), GodSettings.ecologyHalfLifeMs(), 4);
            }
            if (lines.isEmpty() && marks.isEmpty()) return "";
            StringBuilder sb = new StringBuilder("## The living land around mortals\n").append(lines);
            if (!marks.isEmpty()) sb.append("Their mark on it (fades over a day or two):\n").append(marks);
            return sb.append('\n').toString();
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not sense the land", e);
            return "";
        }
    }
}
