package net.bigyous.gptgodmc.god.build;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.structure.StructureRotation;

/**
 * Blocks to place, relative to an anchor. y = 0 is the first layer above the ground; designs
 * face south (+z is the front). Later writes replace earlier ones.
 */
public final class Plan {
    private final Map<Long, BlockData> blocks = new LinkedHashMap<>();
    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0xFFFFF) << 22) | (z & 0x3FFFFF);
    }

    static int kx(long k) {
        return (int) (k << 0 >> 42);
    }

    static int ky(long k) {
        return (int) ((k << 22) >> 44);
    }

    static int kz(long k) {
        return (int) ((k << 42) >> 42);
    }

    public Map<Long, BlockData> blocks() {
        return blocks;
    }

    public int size() {
        return blocks.size();
    }

    public void set(int x, int y, int z, BlockData data) {
        blocks.put(key(x, y, z), data);
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
        maxZ = Math.max(maxZ, z);
    }

    public void set(int x, int y, int z, Material m) {
        set(x, y, z, m.createBlockData());
    }

    public void air(int x, int y, int z) {
        set(x, y, z, Material.AIR);
    }

    public void box(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, m);
    }

    /** Four walls (no floor or ceiling). */
    public void walls(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    if (x == x1 || x == x2 || z == z1 || z == z2) set(x, y, z, m);
    }

    public void stairs(int x, int y, int z, Material m, BlockFace facing, boolean upsideDown) {
        BlockData d = m.createBlockData();
        if (d instanceof Stairs s) {
            s.setFacing(facing);
            s.setHalf(upsideDown ? Bisected.Half.TOP : Bisected.Half.BOTTOM);
        }
        set(x, y, z, d);
    }

    public void facing(int x, int y, int z, Material m, BlockFace facing) {
        BlockData d = m.createBlockData();
        if (d instanceof Directional dir && dir.getFaces().contains(facing)) {
            dir.setFacing(facing);
        }
        set(x, y, z, d);
    }

    public void lantern(int x, int y, int z, Material m, boolean hanging) {
        BlockData d = m.createBlockData();
        if (d instanceof Lantern l) {
            l.setHanging(hanging);
        }
        set(x, y, z, d);
    }

    /** Two-block door; facing is the direction someone walking in through it faces. */
    public void door(int x, int y, int z, Material m, BlockFace facing) {
        Door lower = (Door) m.createBlockData();
        lower.setFacing(facing);
        lower.setHalf(Bisected.Half.BOTTOM);
        Door upper = (Door) lower.clone();
        upper.setHalf(Bisected.Half.TOP);
        set(x, y, z, lower);
        set(x, y + 1, z, upper);
    }

    /** Bed with its head at (x,y,z) and the foot one block opposite to headFacing. */
    public void bed(int x, int y, int z, Material m, BlockFace headFacing) {
        Bed head = (Bed) m.createBlockData();
        head.setFacing(headFacing);
        head.setPart(Bed.Part.HEAD);
        Bed foot = (Bed) head.clone();
        foot.setPart(Bed.Part.FOOT);
        set(x, y, z, head);
        set(x - headFacing.getModX(), y, z - headFacing.getModZ(), foot);
    }

    /** Bars or panes connected along the given faces (they do not auto-connect when placed by code). */
    public void bars(int x, int y, int z, Material m, BlockFace... faces) {
        BlockData d = m.createBlockData();
        if (d instanceof MultipleFacing mf) {
            for (BlockFace f : faces) {
                if (mf.getAllowedFaces().contains(f)) mf.setFace(f, true);
            }
        }
        set(x, y, z, d);
    }

    /** Copies another plan into this one, rotated around its origin and then offset. */
    public void merge(Plan other, int ox, int oy, int oz, StructureRotation rotation) {
        for (Map.Entry<Long, BlockData> e : other.blocks.entrySet()) {
            int[] p = rotate(kx(e.getKey()), kz(e.getKey()), rotation);
            BlockData d = e.getValue().clone();
            if (rotation != StructureRotation.NONE) d.rotate(rotation);
            set(p[0] + ox, ky(e.getKey()) + oy, p[1] + oz, d);
        }
    }

    public Plan rotated(StructureRotation rotation) {
        if (rotation == StructureRotation.NONE) return this;
        Plan p = new Plan();
        p.merge(this, 0, 0, 0, rotation);
        return p;
    }

    /** Minecraft's convention: clockwise 90 maps (x, z) to (-z, x). */
    static int[] rotate(int x, int z, StructureRotation r) {
        return switch (r) {
            case CLOCKWISE_90 -> new int[] { -z, x };
            case CLOCKWISE_180 -> new int[] { -x, -z };
            case COUNTERCLOCKWISE_90 -> new int[] { z, -x };
            default -> new int[] { x, z };
        };
    }

    public int width() {
        return blocks.isEmpty() ? 0 : maxX - minX + 1;
    }

    public int depth() {
        return blocks.isEmpty() ? 0 : maxZ - minZ + 1;
    }

    public int height() {
        return blocks.isEmpty() ? 0 : maxY - minY + 1;
    }
}
