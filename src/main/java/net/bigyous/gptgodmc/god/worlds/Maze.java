package net.bigyous.gptgodmc.god.worlds;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * A labyrinth: a maze with exactly one way between any two of its cells, carved by a walk that never crosses
 * itself, so it has long winding corridors and many dead ends. Pure. The prisoner starts in one corner and
 * the way out is the cell farthest from it, which is never close.
 *
 * <p>The maze is first a grid of cells; {@link #plan} turns it into blocks: corridors `path` blocks wide
 * between walls one block thick.
 */
public final class Maze {
    /** A size the god may ask for: cells across, and how wide a corridor is in blocks. */
    public enum Size {
        SMALL(9, 2), MEDIUM(15, 2), LARGE(23, 2), VAST(31, 2);

        public final int cells, path;

        Size(int cells, int path) {
            this.cells = cells;
            this.path = path;
        }

        public static Size of(String name) {
            for (Size s : values()) {
                if (s.name().equalsIgnoreCase(name)) return s;
            }
            return null;
        }

        public static List<String> names() {
            List<String> out = new ArrayList<>();
            for (Size s : values()) out.add(s.name().toLowerCase(java.util.Locale.ROOT));
            return out;
        }
    }

    private static final int[][] WAYS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    public final int cells;
    /** Whether the wall between a cell and its neighbour to the east (x + 1) or south (z + 1) is open. */
    private final boolean[][] east, south;
    public final int startX, startZ, exitX, exitZ;
    /** Cells walked from the start to the way out. */
    public final int length;

    public Maze(int cells, long seed) {
        if (cells < 2 || cells > 63) throw new IllegalArgumentException("cells: " + cells);
        this.cells = cells;
        this.east = new boolean[cells][cells];
        this.south = new boolean[cells][cells];
        Random rnd = new Random(seed);
        boolean[][] seen = new boolean[cells][cells];
        Deque<int[]> walk = new ArrayDeque<>();
        walk.push(new int[] { 0, 0 });
        seen[0][0] = true;
        List<int[]> ways = new ArrayList<>(Arrays.asList(WAYS));
        while (!walk.isEmpty()) {
            int[] at = walk.peek();
            Collections.shuffle(ways, rnd);
            boolean moved = false;
            for (int[] w : ways) {
                int x = at[0] + w[0], z = at[1] + w[1];
                if (x < 0 || z < 0 || x >= cells || z >= cells || seen[x][z]) continue;
                carve(at[0], at[1], x, z);
                seen[x][z] = true;
                walk.push(new int[] { x, z });
                moved = true;
                break;
            }
            if (!moved) walk.pop();
        }
        int[][] far = distances(0, 0);
        int bestX = 0, bestZ = 0;
        for (int x = 0; x < cells; x++) {
            for (int z = 0; z < cells; z++) {
                if (far[x][z] > far[bestX][bestZ]) {
                    bestX = x;
                    bestZ = z;
                }
            }
        }
        this.startX = 0;
        this.startZ = 0;
        this.exitX = bestX;
        this.exitZ = bestZ;
        this.length = far[bestX][bestZ];
    }

    private void carve(int x0, int z0, int x1, int z1) {
        if (x1 != x0) east[Math.min(x0, x1)][z0] = true;
        else south[x0][Math.min(z0, z1)] = true;
    }

    /** Whether one can step from a cell to the neighbour this way (dx, dz one of them 1 or -1). */
    public boolean open(int x, int z, int dx, int dz) {
        int nx = x + dx, nz = z + dz;
        if (nx < 0 || nz < 0 || nx >= cells || nz >= cells) return false;
        return dx != 0 ? east[Math.min(x, nx)][z] : south[x][Math.min(z, nz)];
    }

    /** Steps from this cell to every cell (each can be reached, by one way only). */
    public int[][] distances(int fromX, int fromZ) {
        int[][] d = new int[cells][cells];
        for (int[] row : d) Arrays.fill(row, -1);
        Deque<int[]> next = new ArrayDeque<>();
        next.add(new int[] { fromX, fromZ });
        d[fromX][fromZ] = 0;
        while (!next.isEmpty()) {
            int[] at = next.poll();
            for (int[] w : WAYS) {
                if (!open(at[0], at[1], w[0], w[1])) continue;
                int x = at[0] + w[0], z = at[1] + w[1];
                if (d[x][z] >= 0) continue;
                d[x][z] = d[at[0]][at[1]] + 1;
                next.add(new int[] { x, z });
            }
        }
        return d;
    }

    /** How many blocks the maze is across when its corridors are this wide. */
    public int span(int path) {
        return cells * (path + 1) + 1;
    }

    /**
     * The maze as blocks, seen from above: true where a wall stands. [x][z], span(path) each way. The outer
     * wall is whole: the way out is a place inside, not a door.
     */
    public boolean[][] plan(int path) {
        int span = span(path), step = path + 1;
        boolean[][] wall = new boolean[span][span];
        for (boolean[] row : wall) Arrays.fill(row, true);
        for (int cx = 0; cx < cells; cx++) {
            for (int cz = 0; cz < cells; cz++) {
                int x0 = 1 + cx * step, z0 = 1 + cz * step;
                for (int x = 0; x < path; x++) {
                    for (int z = 0; z < path; z++) wall[x0 + x][z0 + z] = false;
                }
                if (cx + 1 < cells && east[cx][cz]) {
                    for (int z = 0; z < path; z++) wall[x0 + path][z0 + z] = false;
                }
                if (cz + 1 < cells && south[cx][cz]) {
                    for (int x = 0; x < path; x++) wall[x0 + x][z0 + path] = false;
                }
            }
        }
        return wall;
    }

    /** The middle of a cell's floor, in blocks from the maze's corner: x and z. */
    public double[] middle(int cellX, int cellZ, int path) {
        int step = path + 1;
        return new double[] { 1 + cellX * step + path / 2.0, 1 + cellZ * step + path / 2.0 };
    }
}
