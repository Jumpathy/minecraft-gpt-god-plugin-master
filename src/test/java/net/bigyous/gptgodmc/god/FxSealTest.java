package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The seal layer: its language, its geometry, and where its flat quads land. Draws build/test-renders/fx-seals.png. */
class FxSealTest {
    static final String HEALING = FxSeal.HELP.substring(FxSeal.HELP.indexOf("{\"type\":\"seal\""), FxSeal.HELP.length() - 1);

    static final String GLYPH = "{\"type\":\"seal\",\"plane\":\"upright\",\"radius\":2.6,\"color\":\"#DDF2FF\",\"spin\":25,\"parts\":["
            + "{\"shape\":\"ring\",\"r\":1,\"width\":0.1},{\"shape\":\"ring\",\"r\":0.9,\"width\":0.04},"
            + "{\"shape\":\"runes\",\"r\":0.78,\"count\":18,\"size\":0.26},{\"shape\":\"ring\",\"r\":0.66,\"width\":0.05},"
            + "{\"shape\":\"star\",\"sides\":6,\"skip\":2,\"r\":0.64,\"width\":0.07},{\"shape\":\"spokes\",\"count\":6,\"r\":[0.12,0.64]},"
            + "{\"shape\":\"curve\",\"rho\":\"0.3+0.16*abs(cos(tau*3*u))\",\"steps\":60,\"glow\":0},"
            + "{\"shape\":\"polygon\",\"sides\":6,\"r\":0.12,\"width\":0.06}]}";

    static final String WRATH = "{\"type\":\"seal\",\"radius\":4,\"color\":[\"#FF3B1F\",\"#FFB13B\"],\"parts\":["
            + "{\"shape\":\"ring\",\"r\":1,\"width\":0.14},{\"shape\":\"ring\",\"r\":0.86,\"dashes\":24,\"gap\":0.5,\"spin\":-40},"
            + "{\"shape\":\"polygon\",\"sides\":3,\"r\":0.84,\"spin\":20},{\"shape\":\"polygon\",\"sides\":3,\"r\":0.84,\"spin\":-20},"
            + "{\"shape\":\"spokes\",\"count\":12,\"r\":[0.88,1.12],\"width\":0.1},"
            + "{\"shape\":\"ring\",\"r\":0.16,\"copies\":3,\"orbit\":0.48,\"spin\":90},"
            + "{\"shape\":\"marks\",\"text\":\"☠\",\"count\":1,\"r\":0,\"size\":1.1},"
            + "{\"shape\":\"runes\",\"font\":\"illager\",\"r\":0.7,\"count\":14,\"size\":0.34,\"spin\":-15}]}";

    static final String ROSE = "{\"type\":\"seal\",\"radius\":3.2,\"color\":[\"#C58BFF\",\"#6BE4FF\"],\"scale\":\"1+0.05*sin(t/6)\",\"parts\":["
            + "{\"shape\":\"curve\",\"rho\":\"0.55+0.4*cos(tau*7*u)\",\"steps\":96,\"spin\":18},"
            + "{\"shape\":\"curve\",\"x\":\"0.5*cos(tau*3*u)+0.3*cos(tau*8*u)\",\"y\":\"0.5*sin(tau*3*u)-0.3*sin(tau*8*u)\",\"steps\":96,\"glow\":0},"
            + "{\"shape\":\"ring\",\"r\":1,\"arc\":[20,160]},{\"shape\":\"ring\",\"r\":1,\"arc\":[200,340]},"
            + "{\"shape\":\"marks\",\"text\":\"✦\",\"count\":7,\"r\":1.08,\"size\":0.5},"
            + "{\"shape\":\"motes\",\"text\":\"✦\",\"count\":8,\"r\":0.9,\"height\":3,\"size\":0.4}]}";

    private static FxSeal seal(String json) {
        return FxSeal.parse(JsonParser.parseString(json).getAsJsonObject(), 1);
    }

    @Test
    void theExamplesAreScenesTheGodMayCast() throws Exception {
        for (String s : new String[] { HEALING, GLYPH, WRATH, ROSE }) {
            JsonArray layers = FxScenes.check("[" + s + "]");
            assertEquals("seal", layers.get(0).getAsJsonObject().get("type").getAsString());
            FxSeal seal = seal(s);
            assertTrue(seal.pieces() > 20 && seal.pieces() <= FxSeal.MAX_PIECES, "pieces " + seal.pieces());
            assertEquals(FxSeal.DEFAULT_TICKS, FxScenes.length(layers) >= 100 ? 100 : -1, "runs for its duration");
        }
        assertEquals(120, FxScenes.length(FxScenes.check("[" + HEALING + "]")));
        // Its colours may be the god's own style.
        FxScenes.check("[{\"type\":\"seal\",\"color\":\"$primary\",\"parts\":[{\"shape\":\"ring\",\"color\":[\"$accent\",\"#FFFFFF\"]}]}]");
        // Players without the mod are not sent dust for it: the seal is already in the world.
        assertTrue(FxScenes.plain(FxScenes.check("[" + GLYPH + "]"), 10, 1, new java.util.Random(1), 140).isEmpty());
    }

    @Test
    void whatItCannotDrawIsSaidPlainly() {
        assertTrue(assertThrows(FxScenes.Invalid.class, () -> FxScenes.check("[{\"type\":\"seal\"}]")).getMessage().contains("parts"));
        assertTrue(assertThrows(FxScenes.Invalid.class,
                () -> FxScenes.check("[{\"type\":\"seal\",\"parts\":[{\"shape\":\"blob\"}]}]")).getMessage().contains("ring"));
        assertTrue(assertThrows(FxScenes.Invalid.class,
                () -> FxScenes.check("[{\"type\":\"seal\",\"parts\":[{\"shape\":\"curve\",\"rho\":\"sin(\"}]}]")).getMessage().contains("rho"));
        assertTrue(assertThrows(FxScenes.Invalid.class,
                () -> FxScenes.check("[{\"type\":\"seal\",\"parts\":[{\"shape\":\"curve\"}]}]")).getMessage().contains("rho"));
        StringBuilder many = new StringBuilder("[{\"type\":\"seal\",\"radius\":12,\"plane\":\"upright\",\"parts\":[");
        for (int i = 0; i < 8; i++) many.append(i == 0 ? "" : ",").append("{\"shape\":\"ring\",\"copies\":6}");
        String msg = assertThrows(FxScenes.Invalid.class, () -> FxScenes.check(many + "]}]")).getMessage();
        assertTrue(msg.contains("too intricate"), msg);
        assertTrue(assertThrows(FxScenes.Invalid.class,
                () -> FxScenes.check("[{\"type\":\"seal\",\"plane\":\"sideways\",\"parts\":[{\"shape\":\"ring\"}]}]")).getMessage().contains("plane"));
    }

    @Test
    void everyPieceKeepsItsPlaceInTheListAsTimePasses() {
        for (String s : new String[] { HEALING, GLYPH, WRATH, ROSE }) {
            FxSeal seal = seal(s);
            FxSeal.Frame first = seal.frame(0);
            long backs = seal.twoFaced() ? first.lines().stream().filter(l -> !l.halo()).count() + first.glyphs().size() : 0;
            assertEquals(seal.pieces(), first.lines().size() + first.glyphs().size() + backs + first.motes().size());
            for (int age : new int[] { 3, 40, 99, 100, 400 }) {
                FxSeal.Frame f = seal.frame(age);
                assertEquals(first.lines().size(), f.lines().size());
                assertEquals(first.glyphs().size(), f.glyphs().size());
                assertEquals(first.motes().size(), f.motes().size());
            }
            // Nothing is drawn before it begins, all of it mid-life, nothing once it has faded.
            assertTrue(first.lines().stream().allMatch(l -> l.width() == 0));
            assertTrue(seal.frame(60).lines().stream().allMatch(l -> l.width() > 0));
            assertTrue(seal.frame(seal.duration).lines().stream().allMatch(l -> l.width() == 0));
        }
        FxSeal loop = seal(HEALING);
        loop.endless = true;
        assertTrue(loop.frame(5000).lines().stream().allMatch(l -> l.width() > 0), "a looping seal does not fade by itself");
    }

    @Test
    void aRingIsARingAndTurnsWithItsSpin() {
        FxSeal seal = seal("{\"type\":\"seal\",\"radius\":4,\"appear\":0,\"parts\":[{\"shape\":\"ring\",\"r\":0.5,\"glow\":0},"
                + "{\"shape\":\"spokes\",\"count\":4,\"r\":1,\"spin\":90,\"glow\":0}]}");
        FxSeal.Frame f = seal.frame(20);
        for (FxSeal.Line l : f.lines().subList(0, seal.parts.get(0).segs)) {
            assertEquals(2.0, Math.hypot(l.ax(), l.ay()), 1e-6);
            assertEquals(2.0, Math.hypot(l.bx(), l.by()), 1e-6);
        }
        // After one second at 90 degrees a second, the spoke that pointed up points left.
        FxSeal.Line spoke = f.lines().get(seal.parts.get(0).segs);
        assertEquals(-4.0, spoke.bx(), 1e-6);
        assertEquals(0.0, spoke.by(), 1e-6);
    }

    @Test
    void aStrokeQuadLandsOnItsStroke() {
        float[][] ground = FxSealShow.basis(0, 0);
        assertEquals(1, ground[0][0], 1e-6);
        assertEquals(-1, ground[1][2], 1e-6);
        assertEquals(1, ground[2][1], 1e-6);
        for (double[] plane : new double[][] { { 0, 0 }, { 90, 0 }, { 90, 135 }, { 40, -70 } }) {
            float[][] b = FxSealShow.basis(plane[0], plane[1]);
            Vector3f u = new Vector3f(b[0]), v = new Vector3f(b[1]), n = new Vector3f(b[2]);
            assertEquals(1, new Vector3f(u).cross(v).dot(n), 1e-5, "a right-handed frame");
            Quaternionf face = FxSealShow.orientation(u, v, n);
            Quaternionf rear = FxSealShow.orientation(new Vector3f(u).negate(), v, new Vector3f(n).negate());
            FxSeal.Line line = new FxSeal.Line(1, 0.5, 3, 2.5, 0.2, 0xFFFFFFFF, 0, false);
            for (boolean back : new boolean[] { false, true }) {
                Transformation t = FxSealShow.stroke(u, v, n, back ? rear : face, 0, 0, 0, line, back);
                // The unscaled quad runs from (-0.05, 0) to (0.075, 0.25); its middle must be the stroke's middle,
                // and its long axis the stroke's direction.
                Vector3f mid = corner(t, 0.0125f, 0.125f), a = corner(t, -0.05f, 0.125f), z = corner(t, 0.075f, 0.125f);
                Vector3f want = new Vector3f(u).mul(2).add(new Vector3f(v).mul(1.5f));
                assertEquals(0, mid.distance(want), 1e-4);
                assertEquals(Math.hypot(2, 2) + 0.2, a.distance(z), 1e-4, "its length, with square caps");
                Vector3f along = new Vector3f(z).sub(a).normalize();
                Vector3f dir = new Vector3f(u).add(v).normalize();
                assertEquals(1, Math.abs(along.dot(dir)), 1e-4);
                assertEquals(0.2, corner(t, 0.0125f, 0).distance(corner(t, 0.0125f, 0.25f)), 1e-4, "its width");
                // It shows its front to the side it is meant for.
                Vector3f normal = t.getLeftRotation().transform(new Vector3f(0, 0, 1));
                assertEquals(back ? -1 : 1, normal.dot(n), 1e-4);
            }
        }
    }

    private static Vector3f corner(Transformation t, float x, float y) {
        Vector3f p = new Vector3f(x, y, 0).mul(t.getScale());
        t.getLeftRotation().transform(p);
        return p.add(t.getTranslation());
    }

    // ---- The picture --------------------------------------------------------------------------------------

    @Test
    void drawsTheSamples() throws Exception {
        Map<String, String> samples = new LinkedHashMap<>();
        samples.put("a healing seal (ground)", HEALING);
        samples.put("a glyph standing in the air", GLYPH);
        samples.put("a seal of wrath (ground)", WRATH);
        samples.put("curves from the god's own formulas", ROSE);
        int cell = 560, pad = 34;
        BufferedImage img = new BufferedImage(cell * 2, (cell + pad) * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x0B0E17));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        int i = 0;
        for (Map.Entry<String, String> e : samples.entrySet()) {
            int x0 = (i % 2) * cell, y0 = (i / 2) * (cell + pad);
            JsonObject o = JsonParser.parseString(e.getValue()).getAsJsonObject();
            FxSeal seal = FxSeal.parse(o, 1);
            draw(g, seal, seal.frame(58), x0 + cell / 2.0, y0 + cell / 2.0 + (seal.tilt < 20 ? 60 : 0), cell * 0.4 / seal.radius);
            g.setColor(new Color(0xB8C2D8));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
            g.drawString(e.getKey() + "  ·  " + seal.pieces() + " flat pieces", x0 + 16, y0 + cell + 18);
            i++;
        }
        g.dispose();
        File out = new File("build/test-renders/fx-seals.png");
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        assertTrue(out.length() > 20_000);
    }

    /** A sketch of what the game shows: a ground seal seen from a little above, a standing one from the front. */
    private static void draw(Graphics2D g, FxSeal seal, FxSeal.Frame f, double cx, double cy, double px) {
        double squash = seal.tilt < 20 ? 0.46 : 1;
        if (seal.tilt < 20) {
            g.setStroke(new BasicStroke(1));
            g.setColor(new Color(0x141A26));
            for (int k = -6; k <= 6; k++) {
                g.draw(new Line2D.Double(cx + k * px, cy - 6 * px * squash, cx + k * px, cy + 6 * px * squash));
                g.draw(new Line2D.Double(cx - 6 * px, cy + k * px * squash, cx + 6 * px, cy + k * px * squash));
            }
        }
        for (FxSeal.Line l : f.lines()) {
            if (l.width() <= 0) continue;
            g.setColor(new Color(l.argb(), true));
            g.setStroke(new BasicStroke((float) Math.max(1, l.width() * px * (squash < 1 ? 0.8 : 1)), l.halo() ? BasicStroke.CAP_BUTT : BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            g.draw(new Line2D.Double(cx + l.ax() * px, cy - l.ay() * px * squash, cx + l.bx() * px, cy - l.by() * px * squash));
        }
        for (FxSeal.Glyph gl : f.glyphs()) {
            if (gl.size() <= 0) continue;
            AffineTransform keep = g.getTransform();
            g.translate(cx + gl.x() * px, cy - gl.y() * px * squash);
            g.scale(1, squash);
            g.rotate(-gl.angle());
            glyph(g, gl.text(), gl.font(), gl.argb(), gl.size() * px);
            g.setTransform(keep);
        }
        for (FxSeal.Mote m : f.motes()) {
            if (m.size() <= 0) continue;
            AffineTransform keep = g.getTransform();
            g.translate(cx + m.x() * px, cy - m.y() * px * squash - m.h() * px * 0.8);
            glyph(g, m.text(), m.font(), m.argb(), m.size() * px);
            g.setTransform(keep);
        }
    }

    private static void glyph(Graphics2D g, String text, String font, int argb, double size) {
        // The game draws runes in the enchanting-table alphabet; a serif face stands in for it here.
        g.setFont(new Font(font.equals("plain") ? Font.DIALOG : Font.SERIF, font.equals("plain") ? Font.BOLD : Font.ITALIC, (int) Math.max(6, size * 1.25)));
        g.setColor(new Color(argb, true));
        int w = g.getFontMetrics().stringWidth(text);
        g.drawString(text, -w / 2f, (float) (size * 0.42));
    }
}
