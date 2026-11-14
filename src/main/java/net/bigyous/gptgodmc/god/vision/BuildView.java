package net.bigyous.gptgodmc.god.vision;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import net.bigyous.gptgodmc.Structure;
import net.bigyous.gptgodmc.StructureManager;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Senses;
import net.bigyous.gptgodmc.god.Sync;
import net.bigyous.gptgodmc.god.build.Builder;
import net.bigyous.gptgodmc.god.tools.Targets;
import net.bigyous.gptgodmc.god.tools.ToolException;

/**
 * Finds one build in the live world (a mortal's structure, what a mortal is placing right now, or one
 * of the god's own works) and has {@link BuildSheet} draw it from all sides.
 */
public final class BuildView {
    /** Side of one tile; the sheet is three across and two down. */
    public static final int TILE = 256;
    private static final int MAX_SPAN = 96;
    private static final int MAX_BLOCKS = 60_000;

    private BuildView() {
    }

    record Found(String name, String builder, World world, Set<Long> blocks, boolean grow, String how) {
    }

    /** A build read from the world and not yet drawn. */
    record Captured(Found found, Scene.Voxels voxels, Palette palette) {
    }

    /**
     * Draws blocks that do not exist yet (a script's preview) the way see_build draws a real build: alone,
     * from four corners and from above. Any thread; {@code Palette.get()} must have been called on the server
     * thread once before.
     *
     * @param blocks block key ({@link BuildSheet#key}) -> material ordinal
     */
    public static Vision.Result preview(String name, Map<Long, Integer> blocks) throws ToolException {
        if (blocks.isEmpty()) throw new ToolException("It placed no blocks, so there is nothing to show.");
        Set<Long> keys = new HashSet<>(blocks.keySet());
        BuildSheet.Bounds b = BuildSheet.Bounds.of(keys);
        if (Math.max(Math.max(b.width(), b.length()), b.height()) > MAX_SPAN) {
            throw new ToolException("It is too large to show as one model (" + b.width() + " x " + b.length() + " x "
                    + b.height() + " blocks). Preview a part of it.");
        }
        Scene.Voxels voxels = new Scene.Voxels() {
            @Override
            public int block(int x, int y, int z) {
                Integer m = blocks.get(BuildSheet.key(x, y, z));
                return m == null ? 0 : m;
            }

            @Override
            public int light(int x, int y, int z, double daylight) {
                return 15;
            }

            @Override
            public int highestY(int x, int z) {
                return b.maxY();
            }

            @Override
            public int minY() {
                return b.minY() - 4;
            }

            @Override
            public int maxY() {
                return b.maxY() + 2;
            }
        };
        String where = String.format(Locale.ROOT, "AT %d %d %d", (int) Math.floor(b.cx()), b.minY(),
                (int) Math.floor(b.cz()));
        byte[] png = Vision.encode(BuildSheet.render(voxels, Palette.get(), keys,
                new BuildSheet.About(name, "you (not built yet)", where), TILE));
        Vision.save(png, "preview");
        Map<String, Integer> materials = BuildSheet.materials(keys, voxels);
        String text = String.format(Locale.ROOT, "Size: %d east-west x %d north-south x %d high, %d blocks, from "
                + "(%d, %d, %d) to (%d, %d, %d). Made of: %s. The picture is one sheet: top row seen from the "
                + "south-east, the south-west and straight above (north up); bottom row from the north-west and the "
                + "north-east.", b.width(), b.length(), b.height(), keys.size(), b.minX(), b.minY(), b.minZ(), b.maxX(),
                b.maxY(), b.maxZ(), BuildSheet.materialsLine(materials, 10));
        return new Vision.Result(png, text);
    }

    /** Captures on the server thread, draws on the calling thread. Do not call from the server thread. */
    public static Vision.Result render(String target) throws ToolException {
        return draw(Sync.call(() -> capture(target), 8000));
    }

    /** Any thread. */
    static Vision.Result draw(Captured c) throws ToolException {
        Found f = c.found();
        Set<Long> blocks = f.grow() ? BuildSheet.grow(f.blocks(), c.voxels(), c.palette(), 6, MAX_BLOCKS)
                : BuildSheet.standing(f.blocks(), c.voxels(), c.palette());
        if (blocks.isEmpty()) {
            throw new ToolException("Nothing of " + f.name() + " still stands (its blocks are gone, or that part of "
                    + "the world is not loaded).");
        }
        BuildSheet.Bounds b = BuildSheet.Bounds.of(blocks);
        String where = String.format(Locale.ROOT, "AT %d %d %d", (int) Math.floor(b.cx()), b.minY(),
                (int) Math.floor(b.cz()));
        byte[] png = Vision.encode(BuildSheet.render(c.voxels(), c.palette(), blocks,
                new BuildSheet.About(f.name(), f.builder(), where), TILE));
        Vision.save(png, "build");
        Map<String, Integer> materials = BuildSheet.materials(blocks, c.voxels());
        String text = String.format(Locale.ROOT, "%s%s, shown alone from all sides (%s). Size: %d east-west x %d "
                + "north-south x %d high, %d blocks, from (%d, %d, %d) to (%d, %d, %d) in %s. Made of: %s.%n"
                + "The picture is one sheet of six tiles. Top row: seen from the south-east, from the south-west, "
                + "and from straight above (north up, higher parts brighter, with a scale bar). Bottom row: from the "
                + "north-west, from the north-east, and the same facts as text. Only the build's own blocks are "
                + "drawn solid; the faint shapes around it are the ground and whatever stands beside it. Windows "
                + "and other glass show as gaps.%n"
                + "THIS IS A SNAPSHOT of how it stood when you looked: an unfinished build will have changed by the "
                + "time you speak of it.",
                f.name(), f.builder() == null ? "" : ", built by " + f.builder(), f.how(), b.width(), b.length(),
                b.height(), blocks.size(), b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ(),
                f.world().getName(), BuildSheet.materialsLine(materials, 8));
        return new Vision.Result(png, text);
    }

    /** Server thread. */
    static Captured capture(String target) throws ToolException {
        Found f = find(target == null ? "" : target.trim());
        if (f.blocks().isEmpty()) throw new ToolException("There is nothing of " + f.name() + " to see.");
        BuildSheet.Bounds b = BuildSheet.Bounds.of(f.blocks());
        if (Math.max(b.width(), b.length()) > MAX_SPAN) {
            throw new ToolException(f.name() + " is too large to show as one model (" + b.width() + " x " + b.length()
                    + " blocks). Use look with view overhead or orbit instead.");
        }
        int radius = Math.max(b.width(), b.length()) / 2 + 12;
        return new Captured(f, SnapshotVoxels.capture(f.world(), (int) Math.floor(b.cx()), (int) Math.floor(b.cz()),
                radius), Palette.get());
    }

    /**
     * No placement by this mortal is on record and no structure is theirs here. That is not proof that nothing
     * stands there (blocks set by commands, by other hands or long ago leave no record), so see_build answers it
     * with what the mortal is looking at instead of a refusal the god could take for "there is nothing".
     */
    public static final class NoRecord extends ToolException {
        public final String player;

        NoRecord(String player) {
            super("You have no record of " + player + " placing blocks lately, and no structure is theirs here. That "
                    + "is no proof that nothing stands there: look (view eyes, target " + player + ") before you answer.");
            this.player = player;
        }
    }

    /** A structure by name, one of the god's works by id or name, or a mortal (what they are building). */
    private static Found find(String t) throws ToolException {
        Realm realm = Realm.current();
        if (t.isEmpty()) {
            throw new ToolException("Say whose build: a mortal's name, a structure name, or one of your works.");
        }
        String name = t.toLowerCase(Locale.ROOT).startsWith("structure:") ? t.substring(10).trim() : t;
        if (StructureManager.hasStructure(name)) return structure(name, realm);
        Memory.Work work = realm.memory().findWork(t.toLowerCase(Locale.ROOT).startsWith("work:") ? t.substring(5).trim()
                : t);
        if (work != null) {
            List<Location> at = Builder.blocksOf(work.id);
            World world = org.bukkit.Bukkit.getWorld(work.world);
            if (at == null || world == null) {
                throw new ToolException("Your work " + work.name + " is not kept block by block (too large, a vanilla "
                        + "structure, or made before the last restart). Use look with view orbit and target "
                        + work.x + " " + work.y + " " + work.z + " instead.");
            }
            Set<Long> blocks = new HashSet<>();
            for (Location l : at) blocks.add(BuildSheet.key(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
            return new Found("\"" + work.name + "\" (your work " + work.id + ")", "you", world, blocks, false,
                    "the blocks you placed");
        }
        Player p;
        try {
            p = Targets.player(t);
        } catch (ToolException e) {
            throw new ToolException("No structure, work or mortal called '" + t + "'. Structures: "
                    + (StructureManager.getStructures().isEmpty() ? "none" : String.join(", ",
                            StructureManager.getStructures())) + ".");
        }
        // What they are placing right now, grown to everything built that it touches.
        Set<Long> recent = new HashSet<>();
        for (Senses.Change c : Senses.recentWork(p.getUniqueId())) {
            if (c.placed()) recent.add(BuildSheet.key(c.x(), c.y(), c.z()));
        }
        if (recent.size() >= 3) {
            return new Found("What " + p.getName() + " is building now", p.getName(), p.getWorld(), recent, true,
                    "the blocks they placed in the last minutes and everything built that touches them");
        }
        // Otherwise the nearest thing they built.
        String best = null;
        double bestDist = Double.MAX_VALUE;
        for (String s : StructureManager.getStructures()) {
            Structure st = StructureManager.getStructure(s);
            if (st == null || st.getBuilder() == null || !st.getBuilder().getUniqueId().equals(p.getUniqueId())) continue;
            Location at = st.getLocation();
            if (at.getWorld() == null || !at.getWorld().equals(p.getWorld())) continue;
            double d = at.distanceSquared(p.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = s;
            }
        }
        if (best == null) {
            throw new NoRecord(p.getName());
        }
        return structure(best, realm);
    }

    private static Found structure(String name, Realm realm) throws ToolException {
        Structure st = StructureManager.getStructure(name);
        Location at = st.getLocation();
        if (at.getWorld() == null || !realm.governs(at.getWorld())) {
            throw new ToolException(name + " stands outside your realm.");
        }
        Set<Long> blocks = new HashSet<>();
        for (Vector v : new ArrayList<>(st.getVectors())) {
            blocks.add(BuildSheet.key(v.getBlockX(), v.getBlockY(), v.getBlockZ()));
        }
        return new Found(name, st.getBuilder() == null ? null : st.getBuilder().getName(), at.getWorld(), blocks,
                false, "the blocks mortals placed");
    }
}
