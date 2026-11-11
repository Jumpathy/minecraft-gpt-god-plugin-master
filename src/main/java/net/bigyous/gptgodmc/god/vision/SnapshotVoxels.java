package net.bigyous.gptgodmc.god.vision;

import org.bukkit.ChunkSnapshot;
import org.bukkit.World;

/** Chunk snapshots around a point; thread-safe to read once captured. */
final class SnapshotVoxels implements Scene.Voxels {
    private final ChunkSnapshot[] chunks;
    private final int minCx;
    private final int minCz;
    private final int span;
    private final int minY;
    private final int maxY;

    private SnapshotVoxels(ChunkSnapshot[] chunks, int minCx, int minCz, int span, int minY, int maxY) {
        this.chunks = chunks;
        this.minCx = minCx;
        this.minCz = minCz;
        this.span = span;
        this.minY = minY;
        this.maxY = maxY;
    }

    /** Server thread only. Captures already-loaded chunks within radius blocks; never loads chunks. */
    static SnapshotVoxels capture(World world, int centerX, int centerZ, int radius) {
        int r = Math.max(1, (radius >> 4) + 1);
        int ccx = centerX >> 4;
        int ccz = centerZ >> 4;
        int span = r * 2 + 1;
        ChunkSnapshot[] chunks = new ChunkSnapshot[span * span];
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = ccx + dx;
                int cz = ccz + dz;
                if (world.isChunkLoaded(cx, cz)) {
                    chunks[(dx + r) * span + (dz + r)] = world.getChunkAt(cx, cz).getChunkSnapshot(true, false, false,
                            true);
                }
            }
        }
        return new SnapshotVoxels(chunks, ccx - r, ccz - r, span, world.getMinHeight(), world.getMaxHeight() - 1);
    }

    private ChunkSnapshot chunk(int x, int z) {
        int ix = (x >> 4) - minCx;
        int iz = (z >> 4) - minCz;
        if (ix < 0 || iz < 0 || ix >= span || iz >= span) {
            return null;
        }
        return chunks[ix * span + iz];
    }

    @Override
    public int block(int x, int y, int z) {
        ChunkSnapshot c = chunk(x, z);
        if (c == null) {
            return UNLOADED;
        }
        if (y < minY || y > maxY) {
            return 0;
        }
        return c.getBlockType(x & 15, y, z & 15).ordinal();
    }

    @Override
    public int light(int x, int y, int z, double daylight) {
        ChunkSnapshot c = chunk(x, z);
        if (c == null || y < minY || y > maxY) {
            return 15;
        }
        try {
            int sky = (int) Math.round(c.getBlockSkyLight(x & 15, y, z & 15) * daylight);
            return Math.max(sky, c.getBlockEmittedLight(x & 15, y, z & 15));
        } catch (RuntimeException e) {
            return 15;
        }
    }

    @Override
    public int highestY(int x, int z) {
        ChunkSnapshot c = chunk(x, z);
        return c == null ? Integer.MIN_VALUE : c.getHighestBlockYAt(x & 15, z & 15);
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return maxY;
    }
}
