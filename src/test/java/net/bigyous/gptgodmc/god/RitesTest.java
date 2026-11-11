package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class RitesTest {
    /** Most dots a single rite may draw in one frame. */
    static final int FRAME_MAX = 180;

    @Test
    void everyTintCarriesTheDataItsParticleNeeds() {
        for (Rites.Tint t : Rites.Tint.values()) {
            Class<?> type = t.particle.getDataType();
            if (type == Void.class) {
                assertNull(t.data(), t + " takes no data");
            } else {
                assertTrue(type.isInstance(t.data()), t + " needs " + type.getSimpleName());
            }
        }
    }

    @Test
    void framesStayWithinBudgetAndBounds() {
        for (Rites.Kind kind : Rites.Kind.values()) {
            int drawn = 0;
            for (int f = 0; f < kind.frames; f++) {
                List<Rites.Dot> dots = Rites.frame(kind, f);
                String what = kind + " frame " + f;
                assertTrue(dots.size() <= FRAME_MAX, what + " drew " + dots.size());
                if (!dots.isEmpty()) drawn++;
                for (Rites.Dot d : dots) {
                    for (double v : new double[] { d.x(), d.y(), d.z(), d.vx(), d.vy(), d.vz() }) {
                        assertTrue(Double.isFinite(v), what);
                    }
                    assertTrue(Math.abs(d.x()) <= 8 && Math.abs(d.z()) <= 8, what + " too wide: " + d);
                    assertTrue(d.y() >= -1 && d.y() <= 18, what + " too high or low: " + d);
                    assertTrue(Math.abs(d.vx()) < 1 && Math.abs(d.vy()) < 1 && Math.abs(d.vz()) < 1, what);
                }
                assertEquals(dots, Rites.frame(kind, f), what + " is not deterministic");
            }
            assertTrue(drawn >= kind.frames * 0.8, kind + " is blank too often");
            assertTrue(Rites.frame(kind, -1).isEmpty() && Rites.frame(kind, kind.frames).isEmpty());
            // Something special happens at the climax: a flash or particles thrown outward.
            boolean special = Rites.frame(kind, kind.climax).stream()
                    .anyMatch(d -> d.moving() || d.tint() == Rites.Tint.FLASH);
            assertTrue(special, kind + " has no climax effect");
        }
    }

    @Test
    void cuesAreRealSoundsWithinTheRite() {
        for (Rites.Kind kind : Rites.Kind.values()) {
            List<Rites.Cue> cues = Rites.cues(kind);
            assertFalse(cues.isEmpty(), kind + " is silent");
            for (Rites.Cue c : cues) {
                assertTrue(c.frame() >= 0 && c.frame() < kind.frames, kind + " cue out of range " + c);
                assertTrue(c.volume() > 0 && c.volume() <= 2, kind + " volume " + c);
                assertTrue(c.pitch() >= 0.5f && c.pitch() <= 2.0f, kind + " pitch " + c);
                String field = c.sound().toUpperCase(Locale.ROOT).replace('.', '_');
                assertDoesNotThrow(() -> org.bukkit.Sound.class.getField(field), "no such sound " + c.sound());
            }
        }
    }

    @Test
    void kindsRoundTrip() {
        assertEquals(Rites.Kind.values().length, Rites.kindNames().size());
        for (String n : Rites.kindNames()) assertEquals(n, Rites.kind(n).id());
        assertSame(Rites.Kind.ASCENSION, Rites.kind(" Ascension "));
        assertNull(Rites.kind("tea_party"));
        assertNull(Rites.kind(null));
    }

    @Test
    void aRiteThatCannotBeShownStillRunsItsClimax() {
        AtomicInteger ran = new AtomicInteger();
        assertFalse(Rites.play(Rites.Kind.ANOINT, new Location(null, 0, 0, 0), null, ran::incrementAndGet));
        assertEquals(1, ran.get());
        assertFalse(Rites.running());
        assertEquals(0, Rites.tick(600));
    }

    @Test
    void ascensionSpiralTightensAndCircleStaysOnTheGround() {
        double early = maxRadius(Rites.frame(Rites.Kind.ASCENSION, 5), false);
        double late = maxRadius(Rites.frame(Rites.Kind.ASCENSION, 31), false);
        assertTrue(early > 1.8 && late < 0.6, early + " -> " + late);
        assertTrue(Rites.frame(Rites.Kind.ASCENSION, 10).stream().anyMatch(Rites.Dot::fixed));
        assertTrue(Rites.frame(Rites.Kind.ASCENSION, 10).stream().filter(Rites.Dot::fixed)
                .allMatch(d -> d.y() < 0.2));
    }

    private static double maxRadius(List<Rites.Dot> dots, boolean fixed) {
        return dots.stream().filter(d -> d.fixed() == fixed && d.tint() == Rites.Tint.SUN)
                .mapToDouble(d -> Math.hypot(d.x(), d.z())).max().orElse(0);
    }
}
