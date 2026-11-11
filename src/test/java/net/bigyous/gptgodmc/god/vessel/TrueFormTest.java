package net.bigyous.gptgodmc.god.vessel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class TrueFormTest {

    @Test
    void framesStayInBudgetFiniteAndBounded() {
        for (int tick = 0; tick < 2400; tick += 7) {
            for (double unfold : new double[] { 0, 0.1, 0.4, 0.75, 1 }) {
                for (double eye : new double[] { 0, 0.5, 1 }) {
                    List<TrueForm.Mote> motes = TrueForm.frame(tick, unfold, eye, tick * 0.01, 0.3);
                    assertFalse(motes.isEmpty());
                    assertTrue(motes.size() <= TrueForm.BUDGET, "too many motes: " + motes.size());
                    for (TrueForm.Mote m : motes) {
                        assertTrue(Double.isFinite(m.x()) && Double.isFinite(m.y()) && Double.isFinite(m.z()));
                        double r = Math.sqrt(m.x() * m.x() + m.y() * m.y() + m.z() * m.z());
                        assertTrue(r < TrueForm.HEIGHT - 0.4, "a mote would sink into the ground: r=" + r);
                        assertTrue(m.size() > 0 && m.size() < 4);
                        assertTrue(m.rgb() >= 0 && m.rgb() <= 0xFFFFFF);
                    }
                }
            }
        }
    }

    @Test
    void theFullFormUsesMostOfItsBudgetAndEveryGlyph() {
        List<TrueForm.Mote> motes = TrueForm.frame(300, 1, 1, 0, 0);
        assertTrue(motes.size() > 250, "the full form should be dense, got " + motes.size());
        Set<TrueForm.Glyph> used = EnumSet.noneOf(TrueForm.Glyph.class);
        motes.forEach(m -> used.add(m.glyph()));
        assertTrue(used.contains(TrueForm.Glyph.DUST) && used.contains(TrueForm.Glyph.GLOW));
        // Folded away it is a single point of light.
        assertEquals(1, TrueForm.frame(300, 0, 0, 0, 0).size());
    }

    @Test
    void itMovesOverTimeAndUnfoldsOutward() {
        List<TrueForm.Mote> a = TrueForm.frame(100, 1, 1, 0, 0);
        List<TrueForm.Mote> b = TrueForm.frame(140, 1, 1, 0, 0);
        double moved = 0;
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            moved += Math.abs(a.get(i).x() - b.get(i).x()) + Math.abs(a.get(i).y() - b.get(i).y());
        }
        assertTrue(moved / a.size() > 0.05, "the form should be turning");
        assertTrue(extent(TrueForm.frame(100, 0.3, 0, 0, 0)) < extent(TrueForm.frame(100, 1, 0, 0, 0)));
        assertTrue(extent(TrueForm.frame(100, 1, 0, 0, 0)) > 6, "bigger than the body: several blocks across");
    }

    @Test
    void theEyeOpens() {
        // The open eye adds an iris and a pupil.
        assertTrue(TrueForm.frame(200, 1, 1, 0, 0).stream().filter(m -> m.rgb() == 0xFF1A33).count() > 5);
        assertEquals(0, TrueForm.frame(200, 1, 0, 0, 0).stream().filter(m -> m.rgb() == 0xFF1A33).count());
    }

    private static double extent(List<TrueForm.Mote> motes) {
        double max = 0;
        for (TrueForm.Mote m : motes) max = Math.max(max, Math.sqrt(m.x() * m.x() + m.y() * m.y() + m.z() * m.z()));
        return max;
    }

    // ---- The gaze ------------------------------------------------------------------------------

    @Test
    void gazeDeadOnKills() {
        Vector eye = new Vector(0, 1.6, 0);
        Vector target = new Vector(20, 8, 5);
        Vector look = target.clone().subtract(eye);
        assertTrue(TrueForm.gazes(eye, look, target, 14, 48));
    }

    @Test
    void gazeTwentyDegreesOffIsSpared() {
        Vector eye = new Vector(0, 0, 0);
        Vector target = new Vector(0, 0, 20);
        double a = Math.toRadians(20);
        Vector look = new Vector(Math.sin(a), 0, Math.cos(a));
        assertFalse(TrueForm.gazes(eye, look, target, 14, 48));
        double b = Math.toRadians(10);
        assertTrue(TrueForm.gazes(eye, new Vector(Math.sin(b), 0, Math.cos(b)), target, 14, 48));
        assertEquals(20, TrueForm.angleTo(eye, look, target), 1e-6);
    }

    @Test
    void gazeBehindOrOutOfRangeIsSpared() {
        Vector eye = new Vector(0, 0, 0);
        Vector target = new Vector(0, 0, 20);
        assertFalse(TrueForm.gazes(eye, new Vector(0, 0, -1), target, 14, 48));
        assertFalse(TrueForm.gazes(eye, new Vector(0, 0, 1), new Vector(0, 0, 60), 14, 48));
        assertFalse(TrueForm.gazes(eye, new Vector(0, 0, 0), target, 14, 48));
    }

    @Test
    void gazeConeWidensForTheHeartUpClose() {
        Vector eye = new Vector(0, 0, 0);
        Vector target = new Vector(0, 0, 4);
        double a = Math.toRadians(25);
        Vector look = new Vector(Math.sin(a), 0, Math.cos(a));
        assertFalse(TrueForm.gazes(eye, look, target, 14, 48));
        assertTrue(TrueForm.gazes(eye, look, target, 14, 48, TrueForm.CORE_RADIUS));
        // From afar the heart is small and the plain cone rules.
        Vector far = new Vector(0, 0, 40);
        assertFalse(TrueForm.gazes(eye, look, far, 14, 48, TrueForm.CORE_RADIUS));
    }

    // ---- Timing --------------------------------------------------------------------------------

    @Test
    void holdTimeIsClamped() {
        assertEquals(12, TrueForm.holdSeconds(12, 4, 20));
        assertEquals(20, TrueForm.holdSeconds(90, 4, 20));
        assertEquals(6, TrueForm.holdSeconds(1, 4, 20));
        // A maximum shorter than the warning still leaves two seconds of open eye.
        assertEquals(12, TrueForm.holdSeconds(30, 10, 5));
    }

    @Test
    void cooldownArithmetic() {
        assertEquals(0, TrueForm.cooldownLeft(null, 1_000_000, 600));
        assertEquals(600, TrueForm.cooldownLeft(1_000_000L, 1_000_000, 600));
        assertEquals(1, TrueForm.cooldownLeft(1_000_000L, 1_000_000 + 599_500, 600));
        assertEquals(0, TrueForm.cooldownLeft(1_000_000L, 1_000_000 + 600_000, 600));
        assertEquals(0, TrueForm.cooldownLeft(1_000_000L, 1_000_000, 0));
    }
}
