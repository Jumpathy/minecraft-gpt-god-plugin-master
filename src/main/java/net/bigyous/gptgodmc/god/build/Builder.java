package net.bigyous.gptgodmc.god.build;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.StructureManager;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.tools.ToolException;

/**
 * Places builds a slice per tick so big works never freeze the server, and remembers what was
 * there before so a build can be undone. Server thread only.
 */
public final class Builder {
    private record Placement(int x, int y, int z, BlockData data) {
    }

    private record Prior(Location location, BlockData data) {
    }

    private static final class Job {
        final String id;
        final World world;
        final Deque<Placement> queue = new ArrayDeque<>();
        final List<Prior> undo = new ArrayList<>();
        boolean undoable = true;
        /** When set, every full block still to be placed becomes this one. */
        Material recolor;

        Job(String id, World world) {
            this.id = id;
            this.world = world;
        }
    }

    private static final int UNDO_CAP = 250_000;
    private static final Deque<Job> jobs = new ArrayDeque<>();
    private static final Map<String, List<Prior>> undoLog = new LinkedHashMap<>();
    private static BukkitTask task;

    private Builder() {
    }

    private static int blocksPerTick() {
        return Math.max(100, GPTGOD.get().getConfig().getInt("building.blocks-per-tick", 1500));
    }

    public static int maxBlocks() {
        return Math.max(1000, GPTGOD.get().getConfig().getInt("building.max-blocks", 250_000));
    }

    /**
     * Queues a plan at an anchor (the plan's origin goes to anchor; y=0 sits on the ground).
     * Clears natural terrain inside the build's box and fills a foundation under it.
     */
    public static Memory.Work build(Plan plan, Location anchor, String name, String blueprint, boolean clear,
            Material foundation) throws ToolException {
        if (plan.size() == 0) {
            throw new ToolException("That blueprint is empty.");
        }
        if (plan.size() > maxBlocks()) {
            throw new ToolException("That would place " + plan.size() + " blocks; the server allows " + maxBlocks() + ".");
        }
        World world = anchor.getWorld();
        int ax = anchor.getBlockX();
        int ay = anchor.getBlockY();
        int az = anchor.getBlockZ();
        int minX = ax + plan.minX, maxX = ax + plan.maxX;
        int minY = ay + Math.max(plan.minY, 0), maxY = ay + plan.maxY;
        int minZ = az + plan.minZ, maxZ = az + plan.maxZ;
        if (maxY > world.getMaxHeight() - 1 || ay + plan.minY < world.getMinHeight()) {
            throw new ToolException("The build does not fit between the world's height limits here.");
        }
        String overlap = overlappingStructure(world, minX, ay + plan.minY, minZ, maxX, maxY, maxZ);
        if (overlap != null) {
            throw new ToolException("That would build over " + overlap + ", a mortal's structure. Choose another "
                    + "place (e.g. coordinates a little further away).");
        }

        Memory.Work work = Memory.get().addWork(name, blueprint, world.getName(), ax, ay, az, plan.size(),
                (maxX - minX + 1) + "x" + (maxY - (ay + plan.minY) + 1) + "x" + (maxZ - minZ + 1));
        Job job = new Job(work.id, world);
        Set<Long> planned = new HashSet<>(plan.blocks().keySet());

        long boxVolume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (clear && boxVolume > 400_000) {
            clear = false; // too large to scan in one tick; build over the terrain instead
        }
        if (clear) {
            for (int x = minX; x <= maxX; x++)
                for (int y = minY; y <= maxY; y++)
                    for (int z = minZ; z <= maxZ; z++) {
                        if (planned.contains(Plan.key(x - ax, y - ay, z - az))) continue;
                        Block b = world.getBlockAt(x, y, z);
                        if (!b.getType().isAir()) job.queue.add(new Placement(x, y, z, Material.AIR.createBlockData()));
                    }
        }
        if (foundation != null) {
            Map<Long, Integer> lowest = new LinkedHashMap<>();
            for (long k : plan.blocks().keySet()) {
                long col = ((long) Plan.kx(k) << 32) | (Plan.kz(k) & 0xFFFFFFFFL);
                lowest.merge(col, Plan.ky(k), Math::min);
            }
            for (Map.Entry<Long, Integer> e : lowest.entrySet()) {
                int x = ax + (int) (e.getKey() >> 32);
                int z = az + (int) (long) e.getKey().intValue();
                for (int y = ay + e.getValue() - 1, n = 0; n < 10 && y > world.getMinHeight(); y--, n++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType().isSolid() && !b.getType().name().contains("LEAVES")) break;
                    job.queue.add(new Placement(x, y, z, foundation.createBlockData()));
                }
            }
        }
        List<Map.Entry<Long, BlockData>> ordered = new ArrayList<>(plan.blocks().entrySet());
        ordered.sort(Comparator.comparingInt(e -> Plan.ky(e.getKey())));
        for (Map.Entry<Long, BlockData> e : ordered) {
            long k = e.getKey();
            job.queue.add(new Placement(ax + Plan.kx(k), ay + Plan.ky(k), az + Plan.kz(k), e.getValue()));
        }
        job.undoable = job.queue.size() <= UNDO_CAP;
        enqueue(job, anchor);
        return work;
    }

    /**
     * Adds more blocks to a work begun with {@link #build} (a script's later placements): the same guard for
     * mortals' structures, the same gradual placing, and one undo for the whole work. No terrain is cleared.
     */
    public static void extend(Memory.Work work, Plan plan, Location anchor) throws ToolException {
        if (plan.size() == 0) return;
        if (work.blocks + plan.size() > maxBlocks()) {
            throw new ToolException("That would bring the work to " + (work.blocks + plan.size()) + " blocks; the server allows "
                    + maxBlocks() + ".");
        }
        World world = anchor.getWorld();
        int ax = anchor.getBlockX(), ay = anchor.getBlockY(), az = anchor.getBlockZ();
        if (ay + plan.maxY > world.getMaxHeight() - 1 || ay + plan.minY < world.getMinHeight()) {
            throw new ToolException("The build does not fit between the world's height limits here.");
        }
        String overlap = overlappingStructure(world, ax + plan.minX, ay + plan.minY, az + plan.minZ, ax + plan.maxX,
                ay + plan.maxY, az + plan.maxZ);
        if (overlap != null) {
            throw new ToolException("That would build over " + overlap + ", a mortal's structure. Choose another place.");
        }
        Job job = null;
        for (Job j : jobs) {
            if (j.id.equals(work.id) && j.world.equals(world)) job = j;
        }
        boolean queued = job != null;
        if (!queued) {
            job = new Job(work.id, world);
            List<Prior> had = undoLog.remove(work.id);
            if (had != null) job.undo.addAll(had);
        }
        List<Map.Entry<Long, BlockData>> ordered = new ArrayList<>(plan.blocks().entrySet());
        ordered.sort(Comparator.comparingInt(e -> Plan.ky(e.getKey())));
        for (Map.Entry<Long, BlockData> e : ordered) {
            long k = e.getKey();
            job.queue.add(new Placement(ax + Plan.kx(k), ay + Plan.ky(k), az + Plan.kz(k), e.getValue()));
        }
        work.blocks += plan.size();
        if (!queued) {
            jobs.add(job);
            if (task == null || task.isCancelled()) {
                task = Bukkit.getScheduler().runTaskTimer(GPTGOD.get(), Builder::tick, 1L, 1L);
            }
        }
    }

    /** Places a vanilla .nbt structure (template or user file) with undo support. */
    public static Memory.Work placeStructure(Structure structure, Location anchor, StructureRotation rotation,
            String name, String blueprint) throws ToolException {
        BlockVector size = structure.getSize();
        int volume = size.getBlockX() * size.getBlockY() * size.getBlockZ();
        if (volume > maxBlocks()) {
            throw new ToolException("That structure is too large (" + volume + " blocks).");
        }
        // Center the footprint on the anchor, accounting for rotation.
        int sx = size.getBlockX();
        int sz = size.getBlockZ();
        int[] half = Plan.rotate(sx / 2, sz / 2, rotation);
        Location origin = anchor.clone().subtract(half[0], 0, half[1]);
        int[] far = Plan.rotate(sx - 1, sz - 1, rotation);
        int minX = Math.min(origin.getBlockX(), origin.getBlockX() + far[0]);
        int maxX = Math.max(origin.getBlockX(), origin.getBlockX() + far[0]);
        int minZ = Math.min(origin.getBlockZ(), origin.getBlockZ() + far[1]);
        int maxZ = Math.max(origin.getBlockZ(), origin.getBlockZ() + far[1]);
        int minY = origin.getBlockY();
        int maxY = minY + size.getBlockY() - 1;
        String overlap = overlappingStructure(anchor.getWorld(), minX, minY, minZ, maxX, maxY, maxZ);
        if (overlap != null) {
            throw new ToolException("That would build over " + overlap + ", a mortal's structure. Choose another place.");
        }
        List<Prior> priors = new ArrayList<>();
        World w = anchor.getWorld();
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    priors.add(new Prior(new Location(w, x, y, z), w.getBlockAt(x, y, z).getBlockData()));
        structure.place(origin, true, rotation, Mirror.NONE, -1, 1f, new Random());
        // Jigsaw connectors are only replaced during world generation; hide them.
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() == Material.JIGSAW || b.getType() == Material.STRUCTURE_BLOCK) {
                        b.setType(Material.AIR, false);
                    }
                }
        Memory.Work work = Memory.get().addWork(name, blueprint, w.getName(), anchor.getBlockX(), anchor.getBlockY(),
                anchor.getBlockZ(), volume, (maxX - minX + 1) + "x" + size.getBlockY() + "x" + (maxZ - minZ + 1));
        undoLog.put(work.id, priors);
        trimUndo();
        flourish(anchor);
        return work;
    }

    /**
     * Turns every full block of one of the god's works into another block. A work still rising keeps
     * that block for the rest of its placements. Stairs, slabs, glass, doors and lights keep theirs.
     *
     * @return how many blocks changed now (a rising work changes more as it is placed)
     */
    public static int recolor(String id, Material to) throws ToolException {
        Memory.Work work = Memory.get().findWork(id);
        if (work == null) throw new ToolException("No work '" + id + "'.");
        int changed = 0;
        for (Job j : jobs) {
            if (j.id.equals(work.id)) {
                j.recolor = to;
                for (Prior p : j.undo) changed += recolorAt(p.location(), to);
                return changed;
            }
        }
        List<Prior> priors = undoLog.get(work.id);
        if (priors == null) {
            throw new ToolException("Work " + work.id + " cannot be recolored (too large or built by a vanilla "
                    + "generator). Use run_commands with fill ... replace over its area instead.");
        }
        for (Prior p : priors) changed += recolorAt(p.location(), to);
        return changed;
    }

    private static int recolorAt(Location at, Material to) {
        Block b = at.getBlock();
        Material m = b.getType();
        if (m.isAir() || m == to || !m.isOccluding() || m.name().endsWith("_LEAVES")) return 0;
        b.setType(to, false);
        return 1;
    }

    public static String undo(String id) throws ToolException {
        Memory.Work work = Memory.get().findWork(id);
        if (work == null) {
            throw new ToolException("No work '" + id + "'.");
        }
        for (Job j : jobs) {
            if (j.id.equals(work.id)) {
                jobs.remove(j);
                undoLog.put(work.id, j.undo);
                break;
            }
        }
        List<Prior> priors = undoLog.remove(work.id);
        if (priors == null) {
            throw new ToolException("Work " + work.id + " cannot be undone (too large, built by a vanilla generator, "
                    + "or the server restarted since).");
        }
        for (int i = priors.size() - 1; i >= 0; i--) {
            Prior p = priors.get(i);
            p.location().getBlock().setBlockData(p.data(), false);
        }
        Memory.get().removeWork(work.id);
        return "Unmade " + work.name + " (" + priors.size() + " blocks restored).";
    }

    /** Where a work's blocks are (from its undo data, even mid-build), or null when that is not kept. Server thread. */
    public static List<Location> blocksOf(String id) {
        List<Prior> priors = undoLog.get(id);
        for (Job j : jobs) {
            if (j.id.equals(id)) priors = j.undo;
        }
        if (priors == null) return null;
        List<Location> out = new ArrayList<>(priors.size());
        for (Prior p : priors) out.add(p.location());
        return out;
    }

    public static int pendingJobs() {
        return jobs.size();
    }

    private static void enqueue(Job job, Location at) {
        jobs.add(job);
        flourish(at);
        if (task == null || task.isCancelled()) {
            task = Bukkit.getScheduler().runTaskTimer(GPTGOD.get(), Builder::tick, 1L, 1L);
        }
    }

    /** Drops queued builds and undo data (e.g. when a round world is reset). */
    public static void cancelAll() {
        jobs.clear();
        undoLog.clear();
    }

    private static void tick() {
        Job job = jobs.peek();
        while (job != null && Bukkit.getWorld(job.world.getUID()) == null) {
            jobs.poll(); // its world was unloaded
            job = jobs.peek();
        }
        if (job == null) {
            if (task != null) {
                task.cancel();
                task = null;
            }
            return;
        }
        int budget = blocksPerTick();
        Location last = null;
        while (budget-- > 0 && !job.queue.isEmpty()) {
            Placement p = job.queue.poll();
            Block b = job.world.getBlockAt(p.x(), p.y(), p.z());
            if (job.undoable) {
                job.undo.add(new Prior(b.getLocation(), b.getBlockData()));
            }
            BlockData data = p.data();
            if (job.recolor != null && data.getMaterial().isOccluding()) data = job.recolor.createBlockData();
            b.setBlockData(data, false);
            last = b.getLocation();
        }
        if (last != null && last.getBlockX() % 3 == 0) {
            job.world.spawnParticle(Particle.REVERSE_PORTAL, last.add(0.5, 0.5, 0.5), 6, 0.5, 0.5, 0.5, 0.02);
        }
        if (job.queue.isEmpty()) {
            jobs.poll();
            if (job.undoable) {
                undoLog.put(job.id, job.undo);
                trimUndo();
            }
        }
    }

    private static void trimUndo() {
        while (undoLog.size() > 12) {
            undoLog.remove(undoLog.keySet().iterator().next());
        }
    }

    private static void flourish(Location at) {
        at.getWorld().playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.6f);
        at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, 2, 0), 80, 3, 3, 3, 0.05);
    }

    /** Name of the mortal-built structure that holds this block, or null (vessel_work leaves those alone). */
    public static String mortalStructureAt(org.bukkit.Location l) {
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        return overlappingStructure(l.getWorld(), x, y, z, x, y, z);
    }

    /** Name of a mortal-built structure inside the box, or null. */
    private static String overlappingStructure(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (String name : StructureManager.getStructures()) {
            net.bigyous.gptgodmc.Structure structure = StructureManager.getStructure(name);
            if (structure == null || !world.equals(structure.getLocation().getWorld())) continue;
            for (Vector v : structure.getVectors()) {
                if (v.getBlockX() >= x1 && v.getBlockX() <= x2 && v.getBlockY() >= y1 && v.getBlockY() <= y2
                        && v.getBlockZ() >= z1 && v.getBlockZ() <= z2) {
                    return name;
                }
            }
        }
        return null;
    }
}
