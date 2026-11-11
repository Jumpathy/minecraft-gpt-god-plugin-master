package net.bigyous.gptgod.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgod.client.config.ModConfig;
import net.bigyous.gptgod.client.fx.DemoScenes;
import net.bigyous.gptgod.client.fx.Emitters;
import net.bigyous.gptgod.client.fx.Illusions;
import net.bigyous.gptgod.client.fx.Meshes;
import net.bigyous.gptgod.client.fx.PostFx;
import net.bigyous.gptgod.client.fx.Scene;

/** The effects engine's pure parts: the scene language, emitters, light geometry, illusions, shader maths. */
class FxEngineTest {
    private static Scene scene(String json) {
        return Scene.decode(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void everyDemoSceneDecodesWithAllItsLayers() {
        for (var e : DemoScenes.ALL.entrySet()) {
            JsonObject o = JsonParser.parseString(e.getValue()).getAsJsonObject();
            Scene s = Scene.decode(o);
            assertEquals(o.getAsJsonArray("layers").size(), s.layers().size(), e.getKey() + ": no layer was dropped");
            assertTrue(s.life() > 1 && s.life() <= Scene.MAX_LIFE, e.getKey());
        }
    }

    @Test
    void scenesAreClampedAndBadLayersSkipped() {
        Scene s = scene("{\"scale\":99,\"life\":999999,\"layers\":[{\"type\":\"particles\",\"count\":1000000,\"rate\":5000,\"radius\":9999,"
                + "\"speed\":50,\"size\":[900,-4],\"life\":[0,99999]},{\"type\":\"warp_reality\"},17,{\"type\":\"sound\",\"key\":\"bad key\"},"
                + "{\"type\":\"glyph\",\"text\":\"\"},{\"type\":\"post\",\"kind\":\"shock\",\"intensity\":7}]}");
        assertEquals(8f, s.scale());
        assertEquals(Scene.MAX_LIFE, s.life());
        assertEquals(2, s.layers().size());
        Scene.Particles p = assertInstanceOf(Scene.Particles.class, s.layers().get(0));
        assertEquals(Scene.MAX_COUNT, p.count());
        assertEquals(Scene.MAX_RATE, p.rate());
        assertEquals(48f, p.radius());
        assertEquals(3f, p.speed());
        assertEquals(8f, p.size0());
        assertEquals(400, p.lifeMax());
        assertEquals(1f, assertInstanceOf(Scene.Post.class, s.layers().get(1)).intensity());
        StringBuilder many = new StringBuilder("{\"layers\":[");
        for (int i = 0; i < 40; i++) many.append(i == 0 ? "" : ",").append("{\"type\":\"dome\"}");
        assertEquals(Scene.MAX_LAYERS, scene(many + "]}").layers().size());
    }

    @Test
    void sugarTypesBecomeGeometry() {
        Scene s = scene("{\"layers\":[{\"type\":\"beam\",\"height\":20},{\"type\":\"shockwave\",\"radius\":12},{\"type\":\"lightning\"},"
                + "{\"type\":\"illusion\",\"mode\":\"float\"},{\"type\":\"debris\"}]}");
        assertEquals("beam", ((Scene.Mesh) s.layers().get(0)).kind());
        assertEquals("ring", ((Scene.Mesh) s.layers().get(1)).kind());
        assertEquals(12f, ((Scene.Mesh) s.layers().get(1)).radius());
        assertEquals("lightning", ((Scene.Mesh) s.layers().get(2)).kind());
        assertEquals("float", ((Scene.Debris) s.layers().get(3)).mode());
        assertEquals("fling", ((Scene.Debris) s.layers().get(4)).mode());
    }

    @Test
    void emittersPlaceParticlesOnTheirShapes() {
        Random rnd = new Random(4);
        Scene.Particles ring = (Scene.Particles) scene(
                "{\"layers\":[{\"shape\":\"ring\",\"radius\":5,\"count\":40,\"motion\":\"outward\",\"speed\":0.2,\"spread\":0}]}").layers().get(0);
        assertEquals(40, Emitters.due(ring, 0, rnd));
        assertEquals(0, Emitters.due(ring, 1, rnd));
        for (int i = 0; i < 40; i++) {
            Emitters.Spawn sp = Emitters.spawn(ring, i, 40, 0, 2f, rnd);
            assertEquals(10, Math.hypot(sp.x(), sp.z()), 0.001, "radius times scale");
            assertEquals(0, sp.y(), 1e-9);
            assertTrue(sp.x() * sp.vx() + sp.z() * sp.vz() > 0, "moving outward");
        }
        Scene.Particles stream = (Scene.Particles) scene("{\"layers\":[{\"shape\":\"column\",\"rate\":2.5,\"duration\":10,\"height\":6}]}")
                .layers().get(0);
        int total = 0;
        for (int t = 0; t < 30; t++) total += Emitters.due(stream, t, rnd);
        assertTrue(total >= 20 && total <= 30, "about rate x duration: " + total);
        Emitters.Spawn up = Emitters.spawn(stream, 0, 1, 0, 1, rnd);
        assertTrue(up.y() >= 0 && up.y() <= 6 && up.vy() > 0);
    }

    @Test
    void everyMeshKindBuildsWithinBudgetAndFadesOut() {
        for (Meshes.Kind kind : Meshes.Kind.values()) {
            Meshes.Spec spec = new Meshes.Spec(kind, 6, 10, 0.6f, null, 12, 0xFFE9A8, 0xFF7A1C, 1, 7, 1, 40, 0);
            Meshes.Quads mid = Meshes.build(spec, 12);
            assertTrue(mid.quads > 0 && mid.quads <= Meshes.MAX_QUADS, kind + ": " + mid.quads);
            for (int i = 0; i < mid.quads * 28; i++) assertTrue(Float.isFinite(mid.data[i]), kind + " vertex " + i);
            assertEquals(0, Meshes.build(spec, 41).quads, kind + " is gone after its time");
            float peak = 0;
            for (int i = 6; i < mid.quads * 28; i += 7) peak = Math.max(peak, mid.data[i]);
            assertTrue(peak > 0.05f && peak <= 1f, kind + " shows: " + peak);
        }
    }

    @Test
    void theShockRingGrowsAlongTheGround() {
        Meshes.Spec ring = new Meshes.Spec(Meshes.Kind.RING, 10, 0, 1, null, 0, 0xFFFFFF, 0xFFFFFF, 1, 1, 0, 20, 0);
        assertTrue(reach(Meshes.build(ring, 2)) < reach(Meshes.build(ring, 15)));
        assertTrue(reach(Meshes.build(ring, 19)) <= 10.7);
    }

    /** A rift with a yaw stands across the way it faces: at 0 it spans x, at 90 (facing west) it spans z. */
    @Test
    void aRiftTurnsToFaceItsYaw() {
        Meshes.Spec south = new Meshes.Spec(Meshes.Kind.RIFT, 0, 9, 2, null, 0, 0xFFFFFF, 0x9150FF, 1, 3, 0, 80, 0);
        Meshes.Spec west = new Meshes.Spec(Meshes.Kind.RIFT, 0, 9, 2, null, 0, 0xFFFFFF, 0x9150FF, 1, 3, 0, 80, 90);
        double[] s = spread(Meshes.build(south, 40)), w = spread(Meshes.build(west, 40));
        assertTrue(s[0] > 4 * s[1], "at yaw 0 it spans x: " + s[0] + " vs " + s[1]);
        assertTrue(w[1] > 4 * w[0], "at yaw 90 it spans z: " + w[0] + " vs " + w[1]);
        Scene scene = Scene.decode(com.google.gson.JsonParser.parseString(
                "{\"layers\":[{\"type\":\"rift\",\"yaw\":135,\"spin\":0}]}").getAsJsonObject());
        assertEquals(135f, ((Scene.Mesh) scene.layers().get(0)).yaw());
    }

    /** How far the vertices reach along x and along z. */
    private static double[] spread(Meshes.Quads q) {
        double x = 0, z = 0;
        for (int i = 0; i < q.quads * 4; i++) {
            x = Math.max(x, Math.abs(q.data[i * 7]));
            z = Math.max(z, Math.abs(q.data[i * 7 + 2]));
        }
        return new double[] { x, z };
    }

    private static double reach(Meshes.Quads q) {
        double r = 0;
        for (int i = 0; i < q.quads * 4; i++) r = Math.max(r, Math.hypot(q.data[i * 7], q.data[i * 7 + 2]));
        return r;
    }

    @Test
    void flungBlocksFlyOutAndComeToRestAndRisenOnesReturn() {
        Random rnd = new Random(2);
        Illusions.Piece p = Illusions.launch("stone", Illusions.Mode.FLING, 3, 64, 0, 0, 0, 0.4f, 0.7f, 1, 0.04f, 200, 0, rnd);
        double top = 64;
        for (int i = 0; i < 200; i++) {
            p.tick();
            top = Math.max(top, p.y);
            assertTrue(p.y >= 64 - 1e-9, "never below the ground it came from");
        }
        assertTrue(top > 66, "thrown well into the air: " + top);
        assertTrue(p.x > 4, "outward from the centre");
        assertEquals(0, p.vy, 1e-9);
        assertTrue(p.done());
        assertEquals(0f, p.size(0), 0.001f, "shrunk away at the end");

        Illusions.Piece pillar = Illusions.launch("stone", Illusions.Mode.RISE, 0, 64, 0, 0, 0, 0, 0, 0, 0, 100, 5, rnd);
        double high = 0;
        for (int i = 0; i < 100; i++) {
            pillar.tick();
            high = Math.max(high, pillar.y);
            if (i == 50) assertEquals(64 - 1 + 5, pillar.y, 0.01, "holds at its height");
        }
        assertTrue(high > 68);
        assertEquals(63, pillar.y, 0.01, "back in the ground");
        assertEquals(0f, pillar.yaw, "pillars do not tumble");
    }

    @Test
    void postEffectsProduceBoundedUniforms() {
        PostFx fx = new PostFx();
        ModConfig cfg = new ModConfig();
        assertNull(fx.frame(0, cfg, p -> new float[] { 0.5f, 0.5f }));
        fx.add(PostFx.Kind.SHOCK, 1, 1000, 0, new double[] { 1, 2, 3 }, 0);
        fx.add(PostFx.Kind.TINT, 0.5f, 1000, 0xFF0000, null, 0);
        fx.add(PostFx.Kind.DESATURATE, 1, 1000, 0, null, 0);
        float[] u = fx.frame(300, cfg, p -> new float[] { 0.25f, 0.75f });
        assertNotNull(u);
        assertEquals(PostFx.FLOATS, u.length);
        assertEquals(0.25f, u[0]);
        assertTrue(u[2] > 0 && u[3] > 0, "the ring has grown and still pushes");
        assertTrue(u[8] < 0.1f, "colour drained");
        assertTrue(u[12] > 0.9f && u[13] < 0.1f, "tinted red");
        assertTrue(PostFx.visible(u));
        // Behind the camera the shock is not drawn; with shaders turned down nothing is.
        float[] behind = fx.frame(300, cfg, p -> null);
        assertEquals(0f, behind[3]);
        cfg.bloom = 0;
        assertFalse(PostFx.visible(fx.frame(300, cfg, p -> new float[] { 0.5f, 0.5f })));
        assertNull(fx.frame(5000, cfg, p -> null), "all over");
        assertNull(PostFx.Kind.of("melt"));
    }

    @Test
    void noFlashingForbidsInversion() {
        PostFx fx = new PostFx();
        ModConfig cfg = new ModConfig();
        cfg.noFlashing = true;
        fx.add(PostFx.Kind.INVERT, 1, 1000, 0, null, 0);
        assertEquals(0f, fx.frame(300, cfg, p -> null)[11]);
    }
}
