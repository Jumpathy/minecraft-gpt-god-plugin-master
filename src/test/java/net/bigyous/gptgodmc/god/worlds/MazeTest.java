package net.bigyous.gptgodmc.god.worlds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/** The labyrinth's maze: every cell can be reached by exactly one way, and the way out is far. Draws build/test-renders/labyrinth.png. */
class MazeTest {
    @Test
    void everyCellIsReachedByOneWayOnly() {
        for (Maze.Size size : Maze.Size.values()) {
            for (long seed = 1; seed <= 5; seed++) {
                Maze m = new Maze(size.cells, seed);
                int[][] d = m.distances(m.startX, m.startZ);
                int doors = 0;
                for (int x = 0; x < m.cells; x++) {
                    for (int z = 0; z < m.cells; z++) {
                        assertTrue(d[x][z] >= 0, size + " cell " + x + "," + z + " is walled off");
                        if (m.open(x, z, 1, 0)) doors++;
                        if (m.open(x, z, 0, 1)) doors++;
                    }
                }
                assertEquals(m.cells * m.cells - 1, doors, "a maze with one way between any two cells has cells - 1 doors");
                assertEquals(d[m.exitX][m.exitZ], m.length);
                assertTrue(m.length >= m.cells * 2 - 2, size + ": the way out is long: " + m.length);
                assertFalse(m.open(0, 0, -1, 0) || m.open(m.cells - 1, m.cells - 1, 1, 0), "the outer wall is whole");
            }
        }
        assertNotEquals(new Maze(15, 1).length + ":" + new Maze(15, 1).exitX, new Maze(15, 2).length + ":" + new Maze(15, 2).exitX + "x",
                "another seed, another maze");
        assertEquals(new Maze(15, 7).length, new Maze(15, 7).length);
        assertNull(Maze.Size.of("endless"));
        assertEquals(Maze.Size.LARGE, Maze.Size.of("Large"));
    }

    @Test
    void thePlanHasCorridorsOfTheRightWidthAndAWholeRim() {
        Maze m = new Maze(9, 3);
        boolean[][] wall = m.plan(2);
        assertEquals(m.span(2), wall.length);
        assertEquals(9 * 3 + 1, wall.length);
        for (int i = 0; i < wall.length; i++) {
            assertTrue(wall[i][0] && wall[0][i] && wall[i][wall.length - 1] && wall[wall.length - 1][i], "rim");
        }
        double[] start = m.middle(m.startX, m.startZ, 2), exit = m.middle(m.exitX, m.exitZ, 2);
        assertFalse(wall[(int) start[0]][(int) start[1]]);
        assertFalse(wall[(int) exit[0]][(int) exit[1]]);
        // Walking the blocks from the start reaches the heart: the plan agrees with the maze.
        boolean[][] seen = new boolean[wall.length][wall.length];
        java.util.ArrayDeque<int[]> next = new java.util.ArrayDeque<>();
        next.add(new int[] { (int) start[0], (int) start[1] });
        seen[(int) start[0]][(int) start[1]] = true;
        int open = 0;
        while (!next.isEmpty()) {
            int[] at = next.poll();
            open++;
            for (int[] w : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                int x = at[0] + w[0], z = at[1] + w[1];
                if (wall[x][z] || seen[x][z]) continue;
                seen[x][z] = true;
                next.add(new int[] { x, z });
            }
        }
        assertTrue(seen[(int) exit[0]][(int) exit[1]]);
        assertEquals(81 * 4 + 80 * 2, open, "81 cells of 2x2 and 80 doorways of 2");
    }

    @Test
    void drawsTheLabyrinths() throws Exception {
        int scale = 6, gap = 12;
        Maze.Size[] sizes = Maze.Size.values();
        int width = gap, height = 0;
        for (Maze.Size s : sizes) {
            int span = new Maze(s.cells, 1).span(s.path) * scale;
            width += span + gap;
            height = Math.max(height, span + 2 * gap);
        }
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int ox = gap;
        for (Maze.Size s : sizes) {
            Maze m = new Maze(s.cells, 42);
            boolean[][] wall = m.plan(s.path);
            int[][] d = m.distances(m.startX, m.startZ);
            double[] exit = m.middle(m.exitX, m.exitZ, s.path), start = m.middle(m.startX, m.startZ, s.path);
            for (int x = 0; x < wall.length; x++) {
                for (int z = 0; z < wall.length; z++) {
                    int cx = Math.min(m.cells - 1, Math.max(0, (x - 1) / (s.path + 1))), cz = Math.min(m.cells - 1, Math.max(0, (z - 1) / (s.path + 1)));
                    int far = 40 + 60 * d[cx][cz] / Math.max(1, m.length);
                    int rgb = wall[x][z] ? 0x2A2A34 : Math.hypot(x + 0.5 - exit[0], z + 0.5 - exit[1]) < 1.5 ? 0xFFD040
                            : Math.hypot(x + 0.5 - start[0], z + 0.5 - start[1]) < 1.5 ? 0x60E080 : (far << 16 | (far + 30) << 8 | (far + 70));
                    for (int px = 0; px < scale; px++) {
                        for (int pz = 0; pz < scale; pz++) img.setRGB(ox + x * scale + px, gap + z * scale + pz, rgb);
                    }
                }
            }
            ox += wall.length * scale + gap;
        }
        File out = new File("build/test-renders/labyrinth.png");
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
    }
}
