package net.bigyous.gptgodmc.god.build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import net.bigyous.gptgodmc.god.tools.ToolException;
import net.bigyous.gptgodmc.god.vision.Sightings;

/**
 * Where a build may stand. The builder refuses to overlap structures on record, but much of what mortals make is on
 * no record (a few blocks placed long ago, a shrine set down by commands, another god's work): a cottage once rose
 * on top of the shrine its god had praised a quarter of an hour earlier. Before a design is raised its footprint is
 * searched for things already built; if any stand there the site moves a little, away from the target or to a side,
 * and the god is told; if nowhere near is free, the build is refused with what stands in the way.
 */
public final class Site {
    /** Built blocks in a footprint that count as "something stands here". */
    static final int ENOUGH = 3;
    /** Layers searched above the anchor (walls, roofs and towers are taller, but what matters stands near the ground). */
    static final int HEIGHT = 24;

    public record Choice(Location at, String note) {
    }

    private Site() {
    }

    /**
     * The shifts to try, in order: none; then one, two and three steps further from the target along (dx, dz); then
     * one and two steps to either side. Pure.
     */
    static List<int[]> shifts(double dx, double dz, int step) {
        double len = Math.sqrt(dx * dx + dz * dz);
        double ux = len < 0.5 ? 1 : dx / len;
        double uz = len < 0.5 ? 0 : dz / len;
        List<int[]> out = new ArrayList<>();
        out.add(new int[] { 0, 0 });
        for (int k = 1; k <= 3; k++) out.add(new int[] { (int) Math.round(ux * step * k), (int) Math.round(uz * step * k) });
        for (int k = 1; k <= 2; k++) {
            out.add(new int[] { (int) Math.round(-uz * step * k), (int) Math.round(ux * step * k) });
            out.add(new int[] { (int) Math.round(uz * step * k), (int) Math.round(-ux * step * k) });
        }
        return out;
    }

    /** "cobblestone x25, mossy_cobblestone_wall x4, torch x4": the commonest kinds first, at most five. Pure. */
    static String list(Map<String, Integer> kinds) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(kinds.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size() && i < 5; i++) {
            if (i > 0) sb.append(", ");
            sb.append(sorted.get(i).getKey());
            if (sorted.get(i).getValue() > 1) sb.append(" x").append(sorted.get(i).getValue());
        }
        if (sorted.size() > 5) sb.append(" and more");
        return sb.toString();
    }

    private static int total(Map<String, Integer> kinds) {
        int n = 0;
        for (int v : kinds.values()) n += v;
        return n;
    }

    /** Kinds of built (not natural) block inside the plan's footprint at this anchor. Server thread. */
    static Map<String, Integer> built(Plan plan, Location anchor) {
        World world = anchor.getWorld();
        int ax = anchor.getBlockX(), ay = anchor.getBlockY(), az = anchor.getBlockZ();
        int top = Math.min(world.getMaxHeight() - 1, ay + Math.min(plan.maxY, HEIGHT));
        Map<String, Integer> kinds = new TreeMap<>();
        for (int x = ax + plan.minX; x <= ax + plan.maxX; x++) {
            for (int z = az + plan.minZ; z <= az + plan.maxZ; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = Math.max(world.getMinHeight(), ay - 2); y <= top; y++) {
                    Material m = world.getBlockAt(x, y, z).getType();
                    if (m.isAir() || Sightings.natural(m.name())) continue;
                    kinds.merge(m.name().toLowerCase(Locale.ROOT), 1, Integer::sum);
                }
            }
        }
        return kinds;
    }

    /**
     * The first site, starting at {@code at}, whose footprint holds nothing built. {@code ground} snaps a shifted
     * spot to the terrain. Server thread.
     *
     * @throws ToolException when every site tried holds something built; the message names what stands at the first
     */
    public static Choice settle(Plan plan, Location at, Location target, UnaryOperator<Location> ground)
            throws ToolException {
        Map<String, Integer> first = built(plan, at);
        if (total(first) < ENOUGH) return new Choice(at, "");
        int step = Math.max(plan.width(), plan.depth()) / 2 + 3;
        Map<String, Integer> tried = new LinkedHashMap<>(first);
        for (int[] s : shifts(at.getX() - target.getX(), at.getZ() - target.getZ(), step)) {
            if (s[0] == 0 && s[1] == 0) continue;
            Location moved = ground.apply(at.clone().add(s[0], 0, s[1]));
            if (Math.abs(moved.getY() - at.getY()) > 6) continue; // a cliff or a pit: not "beside" any more
            if (total(built(plan, moved)) >= ENOUGH) continue;
            return new Choice(moved, " The spot you named already holds something built (" + list(tried)
                    + "), so it stands " + Math.round(Math.hypot(s[0], s[1])) + " blocks aside to spare that.");
        }
        throw new ToolException("Something already stands where that would rise (" + list(tried) + ") and no free "
                + "ground was found close by. It may be a mortal's work that is on no record. Look at the place, then "
                + "name another spot with coordinates, or pass over: true to build over it knowingly.");
    }
}
