package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;

import net.bigyous.gptgodmc.god.vessel.Pathing;
import net.bigyous.gptgodmc.god.vessel.WorldGrid;

/**
 * Where summoned creatures may stand: open spots with solid, harmless ground near a target, and a
 * check that a creature's whole body is clear of blocks (a wolf beside a crafting table must not
 * suffocate in it). Server thread only.
 */
final class Spots {
    private Spots() {
    }

    /** Standing spots (block centres) within radius of center, nearest first. */
    static List<Location> near(Location center, int radius) {
        World w = center.getWorld();
        WorldGrid g = new WorldGrid(w);
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        List<Location> out = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Pathing.Step s = Pathing.settle(g, cx + dx, cy + 1, cz + dz, 2);
                if (s == null || Math.abs(s.y() - cy) > 4) continue;
                out.add(new Location(w, s.x() + 0.5, s.y(), s.z() + 0.5));
            }
        }
        out.sort(Comparator.comparingDouble(l -> l.distanceSquared(center)));
        return out;
    }

    /** Whether any block's collision shape (or lava) overlaps the entity's body. */
    static boolean collides(Entity e) {
        BoundingBox box = e.getBoundingBox();
        World w = e.getWorld();
        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX() - 1e-6); x++) {
            for (int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(box.getMaxY() - 1e-6); y++) {
                for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ() - 1e-6); z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() == org.bukkit.Material.LAVA) return true;
                    if (b.isPassable()) continue;
                    for (BoundingBox part : b.getCollisionShape().getBoundingBoxes()) {
                        if (part.clone().shift(x, y, z).overlaps(box)) return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Moves a just-spawned entity to the first of these spots where its whole body fits (starting
     * at index first). Returns false if none fits.
     */
    static boolean fit(Entity e, List<Location> spots, int first) {
        if (!collides(e)) return true;
        for (int i = 0; i < spots.size(); i++) {
            Location spot = spots.get((first + i) % spots.size()).clone();
            spot.setYaw(e.getLocation().getYaw());
            e.teleport(spot);
            if (!collides(e)) return true;
        }
        return false;
    }
}
