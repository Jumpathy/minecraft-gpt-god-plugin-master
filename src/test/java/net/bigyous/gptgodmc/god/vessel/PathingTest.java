package net.bigyous.gptgodmc.god.vessel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.vessel.Pathing.Step;

/** The vessel walks around walls and through doorways, steps up one block and never cuts corners. */
class PathingTest {
    /** A flat floor at y = 0 (stand at y = 1) with solid blocks placed by hand. */
    static final class Blocks implements Pathing.Grid {
        final Set<String> solid = new HashSet<>();
        final Set<String> lava = new HashSet<>();

        Blocks put(int x, int y, int z) {
            solid.add(x + "," + y + "," + z);
            return this;
        }

        Blocks wall(int x, int z1, int z2, int height) {
            for (int z = z1; z <= z2; z++)
                for (int y = 1; y <= height; y++) put(x, y, z);
            return this;
        }

        boolean at(int x, int y, int z) {
            return y <= 0 || solid.contains(x + "," + y + "," + z);
        }

        @Override
        public boolean floor(int x, int y, int z) {
            return at(x, y, z);
        }

        @Override
        public boolean free(int x, int y, int z) {
            return !at(x, y, z);
        }

        @Override
        public boolean hurts(int x, int y, int z) {
            return lava.contains(x + "," + y + "," + z);
        }
    }

    static boolean crosses(List<Step> path, int x) {
        return path.stream().anyMatch(s -> s.x() == x);
    }

    @Test
    void itGoesThroughTheDoorwayNotTheWall() {
        // A wall at x = 3 from z = -10 to 10, with a doorway at z = 5.
        Blocks b = new Blocks().wall(3, -10, 4, 3).wall(3, 6, 10, 3);
        List<Step> path = Pathing.find(b, new Step(0, 1, 0), new Step(6, 1, 0), 2, 4000);
        assertNotNull(path);
        for (Step s : path) {
            if (s.x() == 3) assertEquals(5, s.z(), "it crosses the wall only at the doorway: " + path);
        }
        assertEquals(new Step(6, 1, 0), path.get(path.size() - 1));
    }

    @Test
    void aClosedRoomHasNoWayInAndATallBodyNeedsHeadroom() {
        Blocks room = new Blocks().wall(3, -400, 400, 3);
        assertNull(Pathing.find(room, new Step(0, 1, 0), new Step(6, 1, 0), 2, 3000), "no way through");
        // A doorway two blocks high: a body that needs three blocks of room cannot pass.
        Blocks low = new Blocks().wall(3, -400, 4, 3).wall(3, 6, 400, 3).put(3, 3, 5);
        assertNotNull(Pathing.find(low, new Step(0, 1, 0), new Step(6, 1, 0), 2, 4000));
        assertNull(Pathing.find(low, new Step(0, 1, 0), new Step(6, 1, 0), 3, 4000));
    }

    @Test
    void itStepsUpOneBlockButNotTwo() {
        Blocks step = new Blocks();
        for (int z = -20; z <= 20; z++)
            for (int x = 3; x <= 8; x++) step.put(x, 1, z);
        List<Step> up = Pathing.find(step, new Step(0, 1, 0), new Step(6, 2, 0), 2, 4000);
        assertNotNull(up, "a one-block ledge is climbed");
        Blocks cliff = new Blocks();
        for (int z = -40; z <= 40; z++)
            for (int x = 3; x <= 8; x++) {
                cliff.put(x, 1, z);
                cliff.put(x, 2, z);
            }
        assertNull(Pathing.find(cliff, new Step(0, 1, 0), new Step(6, 3, 0), 2, 3000), "two blocks is too high");
        assertNotNull(Pathing.find(cliff, new Step(6, 3, 0), new Step(0, 1, 0), 2, 3000), "but it may jump down");
    }

    @Test
    void itNeverCutsACornerOrWalksOnLava() {
        Blocks b = new Blocks().put(1, 1, 0).put(1, 2, 0).put(0, 1, 1).put(0, 2, 1);
        List<Step> path = Pathing.find(b, new Step(0, 1, 0), new Step(1, 1, 1), 2, 2000);
        assertTrue(path == null || path.size() > 1, "no diagonal squeeze between two blocks");
        Blocks hot = new Blocks();
        for (int z = -1; z <= 1; z++) hot.lava.add("2,0," + z);
        List<Step> around = Pathing.find(hot, new Step(0, 1, 0), new Step(4, 1, 0), 2, 4000);
        assertNotNull(around);
        assertTrue(around.stream().noneMatch(s -> s.x() == 2 && Math.abs(s.z()) <= 1), "it walks around the lava");
        assertEquals(new Step(5, 1, 0), Pathing.settle(new Blocks(), 5, 3, 0, 2), "settles onto the floor");
    }
}
