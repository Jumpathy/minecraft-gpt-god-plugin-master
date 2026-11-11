package net.bigyous.gptgod.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.bigyous.gptgod.client.fx.FxExpr;
import net.bigyous.gptgod.client.fx.FxField;
import net.bigyous.gptgod.client.fx.FxSolid;
import net.bigyous.gptgod.client.fx.Scene;

/** The scene layers the god writes with formulas (field, solid), as the mod decodes and works them out. */
class FormulaTest {
    private static Scene scene(String json) {
        return Scene.decode(JsonParser.parseString(json).getAsJsonObject());
    }

    /** The plugin's own message (its FxFormulasTest writes this file) decodes with every layer. */
    @Test
    void thePluginsSceneDecodes() throws Exception {
        Scene s = scene(Files.readString(Path.of("src/test/resources/protocol/scene-formulas.json")));
        assertEquals("henge", s.id());
        assertEquals(6, s.layers().size());
        assertEquals(170, s.life());
        Scene.Solid pillars = assertInstanceOf(Scene.Solid.class, s.layers().get(0));
        assertEquals("box", pillars.spec().form);
        assertEquals(10, pillars.spec().copies());
        assertEquals(170, pillars.duration());
        assertTrue(assertInstanceOf(Scene.Solid.class, s.layers().get(3)).spec().light, "the ring is made of light");
        assertEquals(50, s.layers().get(3).delay());
        Scene.Field motes = assertInstanceOf(Scene.Field.class, s.layers().get(4));
        assertEquals(320, motes.spec().count);
        double[] vars = new double[FxExpr.VARS], out = new double[FxField.OUT];
        for (int i = 0; i < 320; i++) {
            motes.spec().at(i, 320, 60, 9, vars, out);
            assertTrue(Math.hypot(out[FxField.X], out[FxField.Z]) <= 6.5 + 1e-9 && out[FxField.Y] >= 0 && out[FxField.Y] <= 8);
        }
        float[] built = pillars.spec().build(100, 9, 1, 1, vars);
        assertEquals(10 * 6 * FxSolid.FLOATS, built.length);
        for (int at = 0; at < built.length; at += FxSolid.VERTEX) {
            assertTrue(Math.hypot(built[at], built[at + 2]) < 8 && built[at + 1] > -0.2f && built[at + 1] < 5.2f, "risen pillars in a ring");
        }
    }

    /** A broken or hostile scene never throws and never asks for more than the budgets. */
    @Test
    void badFormulaLayersAreClampedOrSkipped() {
        Scene s = scene("{\"layers\":[{\"type\":\"field\",\"count\":9999999,\"duration\":999999,\"x\":\"nonsense((\",\"y\":\"1/0\",\"z\":[1],"
                + "\"size\":\"t*999\",\"color\":[\"red\",7]},{\"type\":\"solid\"},{\"type\":\"solid\",\"form\":\"tube\",\"path\":\"no\"},"
                + "{\"type\":\"solid\",\"form\":\"ball\",\"sides\":9999,\"count\":9999,\"scale\":\"1/0\",\"yaw\":\"0/0\"}]}");
        assertEquals(2, s.layers().size(), "the solids with nothing to draw are skipped");
        FxField f = assertInstanceOf(Scene.Field.class, s.layers().get(0)).spec();
        assertEquals(FxField.MAX_COUNT, f.count);
        assertEquals(Scene.MAX_LIFE, f.duration);
        double[] vars = new double[FxExpr.VARS], out = new double[FxField.OUT];
        f.at(3, f.count, 500, 1, vars, out);
        assertEquals(0, out[FxField.X]);
        assertEquals(FxField.REACH, out[FxField.Y]);
        assertEquals(8, out[FxField.SIZE]);
        assertEquals(FxField.DEFAULT_COLOR, f.color(0.5));
        FxSolid ball = assertInstanceOf(Scene.Solid.class, s.layers().get(1)).spec();
        float[] built = ball.build(10, 1, 8, 1, vars);
        assertTrue(built.length > 0 && built.length <= FxSolid.MAX_FACES * FxSolid.FLOATS);
        for (float v : built) assertTrue(Float.isFinite(v));
    }

    /** The plugin's painted textures (its FxPaintTest writes these files) decode; nonsense is dropped. */
    @Test
    void paintedTexturesDecode() throws Exception {
        for (String name : new String[] { "veins", "sigil" }) {
            byte[] data = Files.readAllBytes(Path.of("src/test/resources/protocol/texture-" + name + ".json"));
            net.bigyous.gptgod.client.fx.FxTextures.Picture p = net.bigyous.gptgod.client.fx.FxTextures.decode(data);
            assertEquals("demo." + name, p.id());
            assertEquals(16, p.w());
            assertEquals(8, p.frames().length);
            assertEquals(256, p.frames()[7].length);
            assertTrue(net.bigyous.gptgod.client.fx.FxTextures.receive(data));
            assertEquals(p.rev(), net.bigyous.gptgod.client.fx.FxTextures.get("demo." + name).rev());
        }
        assertEquals(0, net.bigyous.gptgod.client.fx.FxTextures.decode(Files.readAllBytes(Path.of("src/test/resources/protocol/texture-sigil.json")))
                .frames()[0][0] >>> 24, "empty texels are see-through");
        for (String bad : new String[] { "{\"t\":\"texture\"}", "{\"t\":\"texture\",\"id\":\"a\",\"w\":9999,\"h\":16,\"frames\":1,\"png\":\"AAAA\"}",
                "{\"t\":\"texture\",\"id\":\"a\",\"w\":16,\"h\":16,\"frames\":1,\"png\":\"not base64!\"}" }) {
            byte[] data = bad.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(null, net.bigyous.gptgod.client.fx.FxTextures.decode(data), bad);
            assertTrue(net.bigyous.gptgod.client.fx.FxTextures.receive(data), "still ours: nobody else should look at it");
        }
        assertTrue(!net.bigyous.gptgod.client.fx.FxTextures.receive("{\"t\":\"scene\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Scene s = scene("{\"layers\":[{\"type\":\"solid\",\"form\":\"box\",\"texture\":\"demo.veins\",\"tile\":[2,3]}]}");
        FxSolid box = assertInstanceOf(Scene.Solid.class, s.layers().get(0)).spec();
        assertEquals("demo.veins", box.texture);
        float[] built = box.build(20, 1, 1, 1, new double[FxExpr.VARS]);
        float maxU = 0, maxV = 0;
        for (int at = 0; at < built.length; at += FxSolid.VERTEX) {
            assertEquals(built[at + 3], built[at + 5], 1e-6f, "a painted solid is shaded but not tinted unless a colour is given");
            maxU = Math.max(maxU, built[at + 7]);
            maxV = Math.max(maxV, built[at + 8]);
        }
        assertEquals(2f, maxU);
        assertEquals(3f, maxV);
    }
}
