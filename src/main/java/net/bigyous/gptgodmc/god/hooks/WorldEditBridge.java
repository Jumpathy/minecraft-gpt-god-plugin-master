package net.bigyous.gptgodmc.god.hooks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.World;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.AbstractMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.expression.ExpressionException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.convolution.GaussianKernel;
import com.sk89q.worldedit.math.convolution.HeightMap;
import com.sk89q.worldedit.math.convolution.HeightMapFilter;
import com.sk89q.worldedit.regions.CuboidRegion;

import net.bigyous.gptgodmc.god.hooks.WorldEditRules.Refusal;
import net.bigyous.gptgodmc.god.hooks.WorldEditRules.Request;

/**
 * Every line that touches WorldEdit's own classes. Loaded by name from {@link WorldEditHook} only when the
 * WorldEdit plugin is there. Server thread only.
 */
final class WorldEditBridge implements WorldEditHook.Bridge {
    /** Acts each god can still take back. */
    private static final int KEPT = 5;
    private final Map<String, Deque<EditSession>> done = new HashMap<>();

    WorldEditBridge() {
    }

    @Override
    public int run(World world, String realm, Request r, int maxBlocks, WorldEditRules.Protected mortal) throws Refusal {
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        ParserContext ctx = new ParserContext();
        ctx.setWorld(weWorld);
        ctx.setExtent(weWorld);
        ctx.setRestricted(true);
        Pattern pattern = null;
        if (r.pattern() != null && !r.pattern().isBlank()) {
            try {
                pattern = WorldEdit.getInstance().getPatternFactory().parseFromInput(r.pattern(), ctx);
            } catch (InputParseException e) {
                throw new Refusal("WorldEdit cannot read the pattern '" + r.pattern() + "': " + e.getMessage()
                        + ". Use a block id (stone), a mix (50%stone,50%andesite) or a block with states (oak_stairs[facing=north]).");
            }
        }
        EditSession session = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).maxBlocks(maxBlocks).build();
        // Nothing is ever written into a mortal's structure, whatever the act.
        session.setMask(new AbstractMask() {
            @Override
            public boolean test(BlockVector3 v) {
                return !mortal.at(v.x(), v.y(), v.z());
            }

            /** FastAsyncWorldEdit's masks must be able to copy themselves (WorldEdit's own have no such method). */
            public Mask copy() {
                return this;
            }
        });
        boolean kept = false;
        try {
            int changed = act(session, ctx, r, pattern);
            session.close();
            Deque<EditSession> mine = done.computeIfAbsent(realm, k -> new ArrayDeque<>());
            mine.addFirst(session);
            kept = true;
            while (mine.size() > KEPT) mine.removeLast();
            return changed;
        } catch (MaxChangedBlocksException e) {
            throw new Refusal("That would change more than " + maxBlocks + " blocks, so nothing was kept. Choose a smaller region or shape.");
        } catch (ExpressionException e) {
            throw new Refusal("WorldEdit cannot read the expression: " + e.getMessage()
                    + ". It is true where a block belongs, in x, y, z (each -1 to 1 across the region), e.g. x^2+y^2+z^2 < 1.");
        } catch (InputParseException e) {
            throw new Refusal("WorldEdit cannot read the mask '" + r.mask() + "': " + e.getMessage() + ". Use a block id (grass_block), "
                    + "several (stone,andesite) or a tag (#logs).");
        } catch (RuntimeException e) {
            throw new Refusal("WorldEdit failed: " + e);
        } finally {
            if (!kept) {
                // Whatever was written before the failure is taken back.
                try {
                    session.close();
                    EditSession back = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).maxBlocks(-1).build();
                    session.undo(back);
                    back.close();
                } catch (RuntimeException ignored) {
                    // nothing more to do
                }
            }
        }
    }

    private static int act(EditSession s, ParserContext ctx, Request r, Pattern pattern)
            throws MaxChangedBlocksException, ExpressionException, InputParseException {
        CuboidRegion region = r.from() == null || r.to() == null ? null : new CuboidRegion(s.getWorld(), vec(r.from()), vec(r.to()));
        return switch (r.action()) {
            case "set" -> s.setBlocks(region, pattern);
            case "replace" -> {
                Mask what = WorldEdit.getInstance().getMaskFactory().parseFromInput(r.mask(), ctx);
                yield s.replaceBlocks(region, what, pattern);
            }
            case "walls" -> s.makeCuboidWalls(region, pattern);
            case "faces" -> s.makeCuboidFaces(region, pattern);
            case "hollow" -> hollow(s, region, pattern);
            case "sphere" -> s.makeSphere(vec(r.at()), pattern, r.radius(), !r.hollow());
            case "cylinder" -> s.makeCylinder(vec(r.at()), pattern, r.radius(), Math.max(1, r.height()), !r.hollow());
            case "pyramid" -> s.makePyramid(vec(r.at()), pattern, r.radius(), !r.hollow());
            case "line" -> s.drawLine(pattern, vec(r.from()), vec(r.to()), Math.max(0, r.radius()), !r.hollow());
            case "generate" -> {
                // As //generate does: x, y and z run from -1 to 1 across the region.
                Vector3 min = region.getMinimumPoint().toVector3(), max = region.getMaximumPoint().toVector3();
                Vector3 zero = max.add(min).multiply(0.5);
                Vector3 unit = max.subtract(zero);
                unit = Vector3.at(Math.max(1, unit.x()), Math.max(1, unit.y()), Math.max(1, unit.z()));
                yield s.makeShape(region, zero, unit, pattern, r.expression(), r.hollow(), 2000);
            }
            case "smooth" -> new HeightMap(s, region, null).applyFilter(new HeightMapFilter(new GaussianKernel(5, 1.0)),
                    Math.max(1, r.iterations()));
            case "stack" -> s.stackCuboidRegion(region, vec(r.direction()), r.count(), true);
            case "move" -> s.moveCuboidRegion(region, vec(r.direction()), r.count(), true, null);
            default -> throw new IllegalArgumentException(r.action());
        };
    }

    /**
     * WorldEdit has hollowOutRegion(region, thickness, pattern); FastAsyncWorldEdit only the form that also takes a
     * mask of what counts as the shape. Whichever this server has is called.
     */
    private static int hollow(EditSession s, CuboidRegion region, Pattern pattern) throws MaxChangedBlocksException {
        try {
            try {
                return (int) EditSession.class.getMethod("hollowOutRegion", com.sk89q.worldedit.regions.Region.class, int.class,
                        Pattern.class).invoke(s, region, 1, pattern);
            } catch (NoSuchMethodException e) {
                Mask solid = new com.sk89q.worldedit.function.mask.SolidBlockMask(s);
                return (int) EditSession.class.getMethod("hollowOutRegion", com.sk89q.worldedit.regions.Region.class, int.class,
                        Pattern.class, Mask.class).invoke(s, region, 1, pattern, solid);
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof MaxChangedBlocksException max) throw max;
            throw new IllegalStateException(String.valueOf(e.getCause()));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("this WorldEdit cannot hollow a region: " + e);
        }
    }

    @Override
    public boolean undo(World world, String realm) throws Refusal {
        Deque<EditSession> mine = done.get(realm);
        if (mine == null || mine.isEmpty()) return false;
        EditSession last = mine.removeFirst();
        try {
            EditSession back = WorldEdit.getInstance().newEditSessionBuilder().world(last.getWorld()).maxBlocks(-1).build();
            last.undo(back);
            back.close();
            return true;
        } catch (RuntimeException e) {
            throw new Refusal("WorldEdit could not take that back: " + e);
        }
    }

    private static BlockVector3 vec(int[] v) {
        return BlockVector3.at(v[0], v[1], v[2]);
    }
}
