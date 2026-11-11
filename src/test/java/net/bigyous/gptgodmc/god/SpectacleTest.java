package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.Particle;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class SpectacleTest {

    @Test
    void everyParticleResolvesAndGetsTheDataItNeeds() {
        assertTrue(Spectacle.particleNames().size() >= 25);
        for (String name : Spectacle.particleNames()) {
            Particle p = Spectacle.particle(name);
            assertNotNull(p, name);
            // A particle spawned without the data it needs throws on the server, so dataFor must cover each.
            Object data = Spectacle.dataFor(p, null, 3);
            if (p.getDataType() == Void.class) {
                assertEquals(null, data, name + " takes no data");
            } else {
                assertEquals(true, p.getDataType().isInstance(data), name + " would need data the god cannot give");
            }
        }
        assertEquals(Particle.FLAME, Spectacle.particle(" Minecraft:Flame "));
        assertNull(Spectacle.particle("block_crack"));
    }

    @Test
    void shapesAndColorsParse() {
        for (String s : Spectacle.shapeNames()) {
            assertNotNull(Spectacle.shape(s), s);
        }
        assertNull(Spectacle.shape("hexagon"));
        assertEquals(0xFF2200, Spectacle.color("#FF2200").asRGB());
        assertEquals(0xFF2200, Spectacle.color("ff2200").asRGB());
        assertNotNull(Spectacle.color("gold"));
        assertNull(Spectacle.color("#12345"));
        assertNull(Spectacle.color("sparkly"));
    }

    @Test
    void everyShapeStaysWithinItsParticleBudget() {
        Vector beam = new Vector(6, 20, -4);
        for (Spectacle.Shape shape : Spectacle.Shape.values()) {
            for (int size : new int[] { 1, 3, 8, 16 }) {
                for (int frames : new int[] { 1, 40 }) {
                    for (int max : new int[] { 40, Spectacle.FRAME_BUDGET }) {
                        for (int frame = 0; frame < frames; frame += 7) {
                            List<Vector> pts = Spectacle.points(shape, frame, frames, size, beam, max);
                            String what = shape + " size " + size + " frame " + frame + "/" + frames;
                            assertFalse(pts.isEmpty(), what);
                            assertTrue(pts.size() <= max, what + " drew " + pts.size() + " > " + max);
                            for (Vector v : pts) {
                                assertTrue(Double.isFinite(v.getX()) && Double.isFinite(v.getY())
                                        && Double.isFinite(v.getZ()), what);
                                assertTrue(v.length() < 80, what + " point too far: " + v);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void beamRunsFromTargetToOrigin() {
        Vector beam = new Vector(3, 12, 4);
        List<Vector> pts = Spectacle.points(Spectacle.Shape.BEAM, 0, 1, 3, beam, 600);
        assertEquals(0, pts.get(0).length(), 1e-9);
        assertEquals(0, pts.get(pts.size() - 1).distance(beam), 1e-9);
    }
}
