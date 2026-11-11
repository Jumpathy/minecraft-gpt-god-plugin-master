package net.bigyous.gptgodmc.god.vessel;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** The real world as {@link Pathing} sees it. Never loads chunks: unloaded ground is a wall. Server thread only. */
public final class WorldGrid implements Pathing.Grid {
    private static final Set<Material> HURTS = EnumSet.of(Material.LAVA, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.FIRE, Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH,
            Material.POWDER_SNOW, Material.WITHER_ROSE, Material.POINTED_DRIPSTONE);

    private final World world;
    private final Map<Long, Block> cache = new HashMap<>();

    public WorldGrid(World world) {
        this.world = world;
    }

    private Block at(int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return null;
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        long key = ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
        return cache.computeIfAbsent(key, k -> world.getBlockAt(x, y, z));
    }

    @Override
    public boolean floor(int x, int y, int z) {
        Block b = at(x, y, z);
        return b != null && !b.isPassable() && !b.isLiquid();
    }

    @Override
    public boolean free(int x, int y, int z) {
        Block b = at(x, y, z);
        return b != null && b.isPassable() && b.getType() != Material.LAVA;
    }

    @Override
    public boolean hurts(int x, int y, int z) {
        Block b = at(x, y, z);
        return b != null && HURTS.contains(b.getType());
    }
}
