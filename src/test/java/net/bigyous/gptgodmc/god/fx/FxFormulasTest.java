package net.bigyous.gptgodmc.god.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.god.FxScenes;

/**
 * The scene layers the god writes with formulas: checking, the dust sketch, the message the mod decodes, and a
 * picture of the mod's three demo scenes (build/test-renders/fx-formulas.png) drawn with the same maths.
 */
class FxFormulasTest {
    private static final Path FIXTURE = Path.of("client-mod/src/test/resources/protocol/scene-formulas.json");

    /** The mod's demo scenes that use formulas, read from its source so there is one copy of them. */
    private static Map<String, String> demos() throws Exception {
        String src = Files.readString(Path.of("client-mod/src/main/java/net/bigyous/gptgod/client/fx/DemoScenes.java"));
        Matcher m = Pattern.compile("ALL\\.put\\(\"(\\w+)\",\\s*\"\"\"(.*?)\"\"\"\\)", Pattern.DOTALL).matcher(src);
        Map<String, String> out = new LinkedHashMap<>();
        while (m.find()) {
            if (m.group(2).contains("\"field\"") || m.group(2).contains("\"solid\"")) out.put(m.group(1), m.group(2));
        }
        assertTrue(out.size() >= 3, "the formula demos in DemoScenes.java: " + out.keySet());
        return out;
    }

    private static JsonObject one(String layer) throws Exception {
        return FxScenes.check("[" + layer + "]").get(0).getAsJsonObject();
    }

    @Test
    void theDemosAreScenesTheGodCouldWrite() throws Exception {
        for (var e : demos().entrySet()) {
            JsonArray layers = FxScenes.check(e.getValue());
            assertTrue(FxScenes.length(layers) >= 160, e.getKey());
            assertTrue(e.getValue().length() < FxScenes.MAX_BYTES, e.getKey());
        }
        assertTrue(FxScenes.HELP.contains("field {count") && FxScenes.HELP.contains("solid {form"));
        assertEquals(FxFormulas.DEFAULT_TICKS, FxScenes.length(FxScenes.check("[{\"type\":\"field\",\"x\":\"u\"}]")));
    }

    @Test
    void mistakesAreExplainedToTheGod() {
        for (String[] bad : new String[][] { { "{\"type\":\"field\"}", "needs formulas" },
                { "{\"type\":\"field\",\"x\":\"cos(q)\"}", "Layer 1: x: I do not know the name 'q'" },
                { "{\"type\":\"field\",\"x\":1,\"size\":[1,2]}", "size: a formula is a number or text" },
                { "{\"type\":\"field\",\"x\":1,\"count\":99999}", "at most 1500" },
                { "{\"type\":\"field\",\"x\":1,\"sprite\":\"blob\"}", "unknown sprite" },
                { "{\"type\":\"solid\"}", "needs a \"form\"" }, { "{\"type\":\"solid\",\"form\":\"torus\"}", "not 'torus'" },
                { "{\"type\":\"solid\",\"form\":\"tube\",\"path\":[[0,0,0]]}", "\"path\"" },
                { "{\"type\":\"solid\",\"form\":\"lathe\",\"profile\":[[1,0],[1,2,3]]}", "[radius,y]" },
                { "{\"type\":\"solid\",\"form\":\"prism\",\"outline\":[[0,0],[1,0]]}", "3 to 32 points" },
                { "{\"type\":\"solid\",\"form\":\"quads\",\"quads\":[[[0,0,0],[1,0,0]]]}", "quad 1" },
                { "{\"type\":\"solid\",\"form\":\"box\",\"count\":500}", "at most 64 copies" },
                { "{\"type\":\"solid\",\"form\":\"box\",\"yaw\":\"spin(t)\"}", "yaw: I do not know the function 'spin'" } }) {
            FxScenes.Invalid e = assertThrows(FxScenes.Invalid.class, () -> FxScenes.check("[" + bad[0] + "]"), bad[0]);
            assertTrue(e.getMessage().contains(bad[1]), bad[0] + " -> " + e.getMessage());
        }
    }

    @Test
    void aFieldPutsItsParticlesWhereTheFormulasSay() throws Exception {
        FxField f = FxField.parse(one("{\"type\":\"field\",\"count\":50,\"duration\":40,\"x\":\"cos(u*tau)*5\",\"z\":\"sin(u*tau)*5\","
                + "\"y\":\"t*0.1\",\"size\":\"0.2+u\",\"color\":[\"#000000\",\"#FF0000\",\"#FFFFFF\"]}"));
        double[] vars = new double[FxExpr.VARS], o = new double[FxField.OUT];
        for (int i = 0; i < 50; i++) {
            f.at(i, 50, 20, 1, vars, o);
            assertEquals(5, Math.hypot(o[FxField.X], o[FxField.Z]), 1e-9);
            assertEquals(2, o[FxField.Y], 1e-9);
            assertEquals(1, o[FxField.ALPHA], 1e-9);
        }
        f.at(49, 50, 20, 1, vars, o);
        assertEquals(1.2, o[FxField.SIZE], 1e-9);
        assertEquals(1, o[FxField.SHADE], 1e-9, "shade runs along the particles unless a formula says otherwise");
        assertEquals(0x000000, f.color(0));
        assertEquals(0xFF0000, f.color(0.5));
        assertEquals(0xFF8080, f.color(0.75));
        f.at(0, 50, 40, 1, vars, o);
        assertEquals(0, o[FxField.ALPHA], 1e-9, "faded out by its last tick");
        // The sketch for players without the mod: the same ring, scaled, within the budget.
        JsonArray scene = FxScenes.check("[{\"type\":\"field\",\"count\":600,\"duration\":40,\"x\":\"cos(u*tau)*5\",\"z\":\"sin(u*tau)*5\"}]");
        List<FxScenes.Dot> dots = FxScenes.plain(scene, 10, 2, new Random(3), 140);
        assertTrue(dots.size() >= 40 && dots.size() <= 140, "dots: " + dots.size());
        for (FxScenes.Dot d : dots) assertEquals(10, Math.hypot(d.x(), d.z()), 1e-6);
        assertTrue(FxScenes.plain(scene, 10, 2, new Random(3), 20).size() <= 20);
        assertTrue(FxScenes.plain(scene, 200, 2, new Random(3), 140).isEmpty(), "over when the layer is");
    }

    @Test
    void solidsAreBuiltFromTheirFormAndMovedByFormulas() throws Exception {
        double[] vars = new double[FxExpr.VARS];
        FxSolid box = FxSolid.parse(one("{\"type\":\"solid\",\"form\":\"box\",\"size\":[2,4,6],\"count\":3,\"x\":\"i*10\",\"duration\":50}"));
        assertEquals(6, box.faces());
        float[] built = box.build(25, 0, 1, 1, vars);
        assertEquals(3 * 6 * FxSolid.FLOATS, built.length);
        float[] lo = { 99, 99, 99 }, hi = { -99, -99, -99 };
        for (int at = 0; at < 6 * FxSolid.FLOATS; at += FxSolid.VERTEX) {
            for (int k = 0; k < 3; k++) {
                lo[k] = Math.min(lo[k], built[at + k]);
                hi[k] = Math.max(hi[k], built[at + k]);
            }
        }
        assertEquals(List.of(-1f, -2f, -3f, 1f, 2f, 3f), List.of(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]), "round its own middle");
        assertEquals(20f, built[2 * 6 * FxSolid.FLOATS] - built[0], 1e-4, "the third copy stands 20 blocks along");
        // Matter is lit from above: the top face is brighter than the bottom one.
        float top = 0, bottom = 0;
        for (int f = 0; f < 6; f++) {
            int at = f * FxSolid.FLOATS;
            if (built[at + 1] == 2 && built[at + FxSolid.VERTEX + 1] == 2 && built[at + 2 * FxSolid.VERTEX + 1] == 2) top = built[at + 3];
            if (built[at + 1] == -2 && built[at + FxSolid.VERTEX + 1] == -2 && built[at + 2 * FxSolid.VERTEX + 1] == -2) bottom = built[at + 3];
        }
        assertTrue(top > bottom + 0.2f, top + " vs " + bottom);
        assertEquals(0, box.build(51, 0, 1, 1, vars).length, "gone after its time");
        // A quarter turn swaps a box's width and depth.
        FxSolid turned = FxSolid.parse(one("{\"type\":\"solid\",\"form\":\"box\",\"size\":[2,4,6],\"yaw\":90}"));
        float wide = 0;
        for (float[] b = turned.build(25, 0, 1, 1, vars); wide == 0;) {
            for (int at = 0; at < b.length; at += FxSolid.VERTEX) wide = Math.max(wide, Math.abs(b[at]));
        }
        assertEquals(3f, wide, 1e-4);

        for (String form : new String[] { "{\"form\":\"ball\",\"radius\":2}",
                "{\"form\":\"tube\",\"path\":[[0,0,0],[0,3,0],[2,5,0],[2,5,4]],\"width\":[0.6,0.1]}",
                "{\"form\":\"lathe\",\"profile\":[[0,0],[1,0.5],[0.4,2],[0,3]]}",
                "{\"form\":\"prism\",\"outline\":[[1,0],[0,1],[-1,0],[0,-1]],\"height\":3,\"taper\":0}",
                "{\"form\":\"quads\",\"quads\":[[[0,0,0],[1,0,0],[1,1,0],[0,1,0]],[[0,0,0],[0,0,1],[0,1,1]]]}" }) {
            FxSolid s = FxSolid.parse(one(form.replace("{", "{\"type\":\"solid\",\"count\":64,\"light\":true,")));
            assertNotNull(s, form);
            float[] b = s.build(30, 5, 1, 1, vars);
            assertTrue(b.length > 0 && b.length <= FxSolid.MAX_FACES * FxSolid.FLOATS, form + ": " + b.length);
            for (float v : b) assertTrue(Float.isFinite(v), form);
            assertTrue(s.copies() * s.faces() <= FxSolid.MAX_FACES, form);
        }
        assertNull(FxSolid.parse(JsonParser.parseString("{\"form\":\"tube\"}").getAsJsonObject()), "nothing to draw");
        List<FxScenes.Dot> dots = FxScenes.plain(FxScenes.check("[{\"type\":\"solid\",\"form\":\"box\",\"size\":2}]"), 20, 1, new Random(1), 140);
        assertFalse(dots.isEmpty());
        for (FxScenes.Dot d : dots) {
            assertTrue(Math.max(Math.abs(d.x()), Math.max(Math.abs(d.y()), Math.abs(d.z()))) > 0.999, "on the box's edges");
        }
    }

    /** What the mod's tests decode. Run with the environment variable FX_FIXTURES_WRITE=1 to write it anew. */
    @Test
    void theMessageMatchesTheGoldenFile() throws Exception {
        JsonObject message = FxScenes.message("henge", new double[] { 10.5, 64, -3 }, -1, 1, false, FxScenes.check(demos().get("henge")));
        if (System.getenv("FX_FIXTURES_WRITE") != null || !Files.exists(FIXTURE)) Files.writeString(FIXTURE, message + "\n");
        assertEquals(JsonParser.parseString(Files.readString(FIXTURE)), message);
    }

    // ---- The picture ----------------------------------------------------------------------------------

    private static final int W = 480, H = 340;
    private static final double[] EYE = { 0, 10, -23 }, LOOK = { 0, 5, 0 };

    @Test
    void drawsTheDemos() throws Exception {
        Map<String, String> demos = demos();
        int[] times = { 24, 60, 100, 140 };
        BufferedImage sheet = new BufferedImage(W * times.length, H * demos.size(), BufferedImage.TYPE_INT_RGB);
        int row = 0;
        for (String json : demos.values()) {
            JsonArray layers = FxScenes.check(json);
            for (int col = 0; col < times.length; col++) {
                int[] px = frame(layers, times[col]);
                sheet.setRGB(col * W, row * H, W, H, px, 0, W);
                int lit = 0;
                for (int p : px) if ((p & 0xFF) + ((p >> 8) & 0xFF) + ((p >> 16) & 0xFF) > 120) lit++;
                assertTrue(lit > 100, "something is drawn at tick " + times[col] + " of demo " + row + ": " + lit);
            }
            row++;
        }
        File out = new File("build/test-renders/fx-formulas.png");
        out.getParentFile().mkdirs();
        ImageIO.write(sheet, "png", out);
    }

    /** One frame of a scene's fields and solids, seen from a little above: matter with a depth buffer, light added. */
    private static int[] frame(JsonArray layers, int tick) {
        float[] rgb = new float[W * H * 3], depth = new float[W * H];
        Arrays.fill(depth, Float.MAX_VALUE);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float g = 0.03f + 0.05f * y / H;
                rgb[(y * W + x) * 3] = g * 0.8f;
                rgb[(y * W + x) * 3 + 1] = g * 0.8f;
                rgb[(y * W + x) * 3 + 2] = g * 1.3f;
            }
        }
        double[] vars = new double[FxExpr.VARS];
        for (int pass = 0; pass < 2; pass++) { // matter first, so light and particles are hidden by it
            for (JsonElement e : layers) {
                JsonObject l = e.getAsJsonObject();
                if (!"solid".equals(l.get("type").getAsString())) continue;
                FxSolid s = FxSolid.parse(l);
                if (s == null || s.light != (pass == 1)) continue;
                float[] b = s.build(tick - s.delay, 7, 1, 1, vars);
                for (int at = 0; at < b.length; at += FxSolid.FLOATS) {
                    triangle(rgb, depth, b, at, 0, 1, 2, s.light);
                    triangle(rgb, depth, b, at, 0, 2, 3, s.light);
                }
            }
        }
        double[] o = new double[FxField.OUT];
        for (JsonElement e : layers) {
            JsonObject l = e.getAsJsonObject();
            if (!"field".equals(l.get("type").getAsString())) continue;
            FxField f = FxField.parse(l);
            int t = tick - f.delay;
            if (t < 0 || t > f.duration) continue;
            for (int i = 0; i < f.count; i++) {
                f.at(i, f.count, t, 7, vars, o);
                double[] p = project(f.offset[0] + o[FxField.X], f.offset[1] + o[FxField.Y], f.offset[2] + o[FxField.Z]);
                if (p == null) continue;
                double radius = Math.max(1.2, o[FxField.SIZE] * H * 0.9 / p[2] * 0.9);
                int c = f.color(o[FxField.SHADE]);
                double a = o[FxField.ALPHA] * 0.75;
                for (int y = (int) (p[1] - radius); y <= p[1] + radius; y++) {
                    for (int x = (int) (p[0] - radius); x <= p[0] + radius; x++) {
                        if (x < 0 || y < 0 || x >= W || y >= H || p[2] > depth[y * W + x]) continue;
                        double d = Math.hypot(x - p[0], y - p[1]) / radius;
                        if (d >= 1) continue;
                        double w = (1 - d * d) * (1 - d * d) * a;
                        rgb[(y * W + x) * 3] += ((c >> 16) & 255) / 255f * w;
                        rgb[(y * W + x) * 3 + 1] += ((c >> 8) & 255) / 255f * w;
                        rgb[(y * W + x) * 3 + 2] += (c & 255) / 255f * w;
                    }
                }
            }
        }
        int[] px = new int[W * H];
        for (int k = 0; k < px.length; k++) {
            px[k] = (int) (Math.min(1, rgb[k * 3]) * 255) << 16 | (int) (Math.min(1, rgb[k * 3 + 1]) * 255) << 8
                    | (int) (Math.min(1, rgb[k * 3 + 2]) * 255);
        }
        return px;
    }

    /** Screen x, y and the distance along the view, or null behind the eye. */
    private static double[] project(double x, double y, double z) {
        double fx = LOOK[0] - EYE[0], fy = LOOK[1] - EYE[1], fz = LOOK[2] - EYE[2];
        double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl;
        fy /= fl;
        fz /= fl;
        double rx = fz, rz = -fx, rl = Math.hypot(rx, rz); // right = up x forward, with up = +y
        rx /= rl;
        rz /= rl;
        double ux = fy * rz, uy = fz * rx - fx * rz, uz = -fy * rx; // up = forward x right
        double dx = x - EYE[0], dy = y - EYE[1], dz = z - EYE[2];
        double cz = dx * fx + dy * fy + dz * fz;
        if (cz < 0.3) return null;
        double focal = H * 0.9;
        return new double[] { W / 2.0 + (dx * rx + dz * rz) / cz * focal, H / 2.0 - (dx * ux + dy * uy + dz * uz) / cz * focal, cz };
    }

    private static void triangle(float[] rgb, float[] depth, float[] b, int face, int i0, int i1, int i2, boolean light) {
        int a = face + i0 * FxSolid.VERTEX, c = face + i1 * FxSolid.VERTEX, d = face + i2 * FxSolid.VERTEX;
        double[] p0 = project(b[a], b[a + 1], b[a + 2]), p1 = project(b[c], b[c + 1], b[c + 2]), p2 = project(b[d], b[d + 1], b[d + 2]);
        if (p0 == null || p1 == null || p2 == null) return;
        double area = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p2[0] - p0[0]) * (p1[1] - p0[1]);
        if (Math.abs(area) < 1e-9) return;
        int x0 = (int) Math.max(0, Math.floor(Math.min(p0[0], Math.min(p1[0], p2[0])))), x1 = (int) Math.min(W - 1, Math.ceil(Math.max(p0[0], Math.max(p1[0], p2[0]))));
        int y0 = (int) Math.max(0, Math.floor(Math.min(p0[1], Math.min(p1[1], p2[1])))), y1 = (int) Math.min(H - 1, Math.ceil(Math.max(p0[1], Math.max(p1[1], p2[1]))));
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double w0 = ((p1[0] - x) * (p2[1] - y) - (p2[0] - x) * (p1[1] - y)) / area;
                double w1 = ((p2[0] - x) * (p0[1] - y) - (p0[0] - x) * (p2[1] - y)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                float z = (float) (w0 * p0[2] + w1 * p1[2] + w2 * p2[2]);
                int at = y * W + x;
                if (z > depth[at]) continue;
                float alpha = (float) (w0 * b[a + 6] + w1 * b[c + 6] + w2 * b[d + 6]);
                for (int k = 0; k < 3; k++) {
                    float v = (float) (w0 * b[a + 3 + k] + w1 * b[c + 3 + k] + w2 * b[d + 3 + k]);
                    rgb[at * 3 + k] = light ? rgb[at * 3 + k] + v * alpha : rgb[at * 3 + k] * (1 - alpha) + v * alpha;
                }
                if (!light) depth[at] = z;
            }
        }
    }
}
