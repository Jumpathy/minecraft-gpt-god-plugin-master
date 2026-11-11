package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class ShapesTest {
    static final double EPS = 1e-9;

    @Test
    void ringHasItsRadiusAndCount() {
        List<Vector> r = Shapes.ring(2.5, 24, 0.3, 1.5);
        assertEquals(24, r.size());
        for (Vector v : r) {
            assertEquals(2.5, Math.hypot(v.getX(), v.getZ()), EPS);
            assertEquals(1.5, v.getY(), EPS);
        }
    }

    @Test
    void starPolygonsHaveTheirCornersAndEdges() {
        List<Vector> pentagram = Shapes.star(5, 2, 2, 0, 0, 6);
        assertEquals(30, pentagram.size());
        // The first point of each edge is a corner on the circle; there are exactly five distinct corners.
        Set<String> corners = new HashSet<>();
        for (int i = 0; i < pentagram.size(); i += 6) {
            Vector c = pentagram.get(i);
            assertEquals(2, Math.hypot(c.getX(), c.getZ()), EPS);
            corners.add(Math.round(c.getX() * 1000) + "," + Math.round(c.getZ() * 1000));
        }
        assertEquals(5, corners.size());
        // A pentagram's edges cross inside: some points lie well within the circle.
        assertTrue(pentagram.stream().anyMatch(v -> Math.hypot(v.getX(), v.getZ()) < 1));
        assertEquals(6 * 4, Shapes.polygon(6, 1, 0, 0, 4).size());
        assertEquals(8 * 5, Shapes.star(8, 3, 1.3, 0, 0, 5).size());
    }

    @Test
    void helixRunsFromBottomToTop() {
        List<Vector> h = Shapes.helix(2, 2, 0.5, 0, 3, 1.5, 20, 0);
        assertEquals(40, h.size());
        assertEquals(0, h.get(0).getY(), EPS);
        assertEquals(3, h.get(19).getY(), EPS);
        assertEquals(2, Math.hypot(h.get(0).getX(), h.get(0).getZ()), EPS);
        assertEquals(0.5, Math.hypot(h.get(19).getX(), h.get(19).getZ()), EPS);
        // The second strand starts opposite the first.
        assertEquals(-h.get(0).getX(), h.get(20).getX(), 1e-6);
    }

    @Test
    void spherePointsLieOnTheSphere() {
        for (Vector v : Shapes.sphere(50, 1.7, 0.4)) assertEquals(1.7, v.length(), 1e-9);
    }

    @Test
    void tesseractHasAllItsEdgesAndStaysBounded() {
        for (double a = 0; a < Math.PI * 2; a += 0.37) {
            List<Vector> t = Shapes.tesseract(a, a * 0.7, a * 0.3, 1.5, 4);
            assertEquals(32 * 4, t.size());
            for (Vector v : t) assertTrue(v.length() < 1.5 * 3, "escaped at " + a + ": " + v);
        }
    }

    @Test
    void turnPreservesLengths() {
        List<Vector> pts = Shapes.sphere(20, 1.3, 0);
        pts.add(new Vector(1, 2, 3));
        List<Vector> turned = Shapes.turn(pts, 0.7, -1.1, 0.4);
        for (int i = 0; i < pts.size(); i++) assertEquals(pts.get(i).length(), turned.get(i).length(), 1e-9);
    }

    @Test
    void easeAndLerp() {
        assertEquals(0, Shapes.ease(0), EPS);
        assertEquals(1, Shapes.ease(1), EPS);
        assertEquals(0.5, Shapes.ease(0.5), EPS);
        assertEquals(0, Shapes.ease(-3), EPS);
        assertEquals(1, Shapes.ease(9), EPS);
        assertEquals(2.5, Shapes.lerp(2, 3, 0.5), EPS);
        List<Vector> line = Shapes.line(new Vector(0, 0, 0), new Vector(4, 2, 0), 5);
        assertEquals(new Vector(4, 2, 0), line.get(4));
        assertEquals(1, Shapes.lift(line, 1).get(0).getY(), EPS);
    }

    @Test
    void ritesCircleHalvesMakeTheWhole() {
        List<Vector> all = Shapes.magicCircle(2.4, 0.3, 0.05, 2.8);
        List<Vector> a = Rites.circleHalf(2.4, 0.3, 0.05, 2.8, true);
        List<Vector> b = Rites.circleHalf(2.4, 0.3, 0.05, 2.8, false);
        assertEquals(all.size(), a.size() + b.size());
        for (Vector v : a) assertTrue(Math.hypot(v.getX(), v.getZ()) > 2.0);
    }
}
