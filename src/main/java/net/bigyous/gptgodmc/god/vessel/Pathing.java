package net.bigyous.gptgodmc.god.vessel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Finds a walking route for the vessel over blocks, so it goes around walls and through doorways
 * instead of sliding through them. A* over standing spots: it steps up one block at most, drops
 * three at most, and needs {@code clearance} free blocks above its feet. Pure (the world is reached
 * through {@link Grid}), so it is unit tested.
 */
public final class Pathing {
    private Pathing() {
    }

    /** What the path-finder may ask about the world. */
    public interface Grid {
        /** A block one can stand on (not air, water, lava, plants or open doors). */
        boolean floor(int x, int y, int z);

        /** A block one can stand inside (air, grass, open doors, water). */
        boolean free(int x, int y, int z);

        /** A floor that hurts (lava, magma, cactus, fire, ...). */
        boolean hurts(int x, int y, int z);
    }

    public record Step(int x, int y, int z) {
    }

    private static final int[][] DIRS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 },
            { -1, -1 } };

    /** Whether a body with this clearance can stand at (x, y, z), its feet in that block. */
    public static boolean standable(Grid g, int x, int y, int z, int clearance) {
        if (!g.floor(x, y - 1, z) || g.hurts(x, y - 1, z)) return false;
        for (int h = 0; h < clearance; h++) {
            if (!g.free(x, y + h, z)) return false;
        }
        return true;
    }

    /**
     * The standing spot at or near (x, y, z): the first standable height scanning a few blocks
     * down then up, or null.
     */
    public static Step settle(Grid g, int x, int y, int z, int clearance) {
        for (int d = 0; d <= 4; d++) {
            if (standable(g, x, y - d, z, clearance)) return new Step(x, y - d, z);
        }
        for (int d = 1; d <= 3; d++) {
            if (standable(g, x, y + d, z, clearance)) return new Step(x, y + d, z);
        }
        return null;
    }

    /**
     * A route from start to goal (both standing spots), as the steps after start ending at goal; an
     * empty list if they are the same; null when there is none within maxNodes.
     */
    public static List<Step> find(Grid g, Step start, Step goal, int clearance, int maxNodes) {
        if (start.equals(goal)) return List.of();
        record Node(Step at, double f) {
        }
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
        Map<Step, Double> cost = new HashMap<>();
        Map<Step, Step> from = new HashMap<>();
        cost.put(start, 0.0);
        open.add(new Node(start, h(start, goal)));
        int seen = 0;
        while (!open.isEmpty() && seen++ < maxNodes) {
            Step cur = open.poll().at();
            if (cur.equals(goal)) {
                List<Step> path = new ArrayList<>();
                for (Step s = goal; s != null && !s.equals(start); s = from.get(s)) path.add(s);
                Collections.reverse(path);
                return path;
            }
            double base = cost.get(cur);
            for (int[] d : DIRS) {
                int nx = cur.x() + d[0], nz = cur.z() + d[1];
                boolean diagonal = d[0] != 0 && d[1] != 0;
                if (diagonal && (!passable(g, cur.x() + d[0], cur.y(), cur.z(), clearance)
                        || !passable(g, cur.x(), cur.y(), cur.z() + d[1], clearance))) {
                    continue; // never cut a corner through a wall
                }
                Step next = null;
                int climb = 0;
                if (standable(g, nx, cur.y(), nz, clearance)) {
                    next = new Step(nx, cur.y(), nz);
                } else if (standable(g, nx, cur.y() + 1, nz, clearance) && g.free(cur.x(), cur.y() + clearance, cur.z())) {
                    next = new Step(nx, cur.y() + 1, nz);
                    climb = 1;
                } else if (passable(g, nx, cur.y(), nz, clearance)) {
                    // Step off the edge and fall straight down to the first floor (three blocks at most).
                    for (int drop = 1; drop <= 3; drop++) {
                        if (standable(g, nx, cur.y() - drop, nz, clearance)) {
                            next = new Step(nx, cur.y() - drop, nz);
                            break;
                        }
                        if (!g.free(nx, cur.y() - drop, nz)) break;
                    }
                }
                if (next == null) continue;
                double step = (diagonal ? 1.414 : 1.0) + climb * 0.5;
                double c = base + step;
                Double known = cost.get(next);
                if (known == null || c < known) {
                    cost.put(next, c);
                    from.put(next, cur);
                    open.add(new Node(next, c + h(next, goal)));
                }
            }
        }
        return null;
    }

    /** Free space for the body at this column and height (to pass beside a corner). */
    private static boolean passable(Grid g, int x, int y, int z, int clearance) {
        for (int h = 0; h < clearance; h++) {
            if (!g.free(x, y + h, z)) return false;
        }
        return true;
    }

    private static double h(Step a, Step b) {
        int dx = Math.abs(a.x() - b.x()), dz = Math.abs(a.z() - b.z()), dy = Math.abs(a.y() - b.y());
        return Math.max(dx, dz) + 0.414 * Math.min(dx, dz) + dy * 0.5;
    }
}
