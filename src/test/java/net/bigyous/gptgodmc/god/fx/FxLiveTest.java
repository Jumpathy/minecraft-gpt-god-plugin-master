package net.bigyous.gptgodmc.god.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.god.FxScenes;

/** What only a watcher's own game works out: the viewer in formulas, particles that keep their motion, triggers. */
class FxLiveTest {
    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void aFormulaKnowsTheMortalWatching() throws Exception {
        double[] v = new double[FxExpr.VARS];
        // Eyes 3 east and 4 south of the layer's place, looking straight back at it.
        FxExpr.viewer(v, 3, 0, 4, -0.6, 0, -0.8, 0.2);
        assertEquals(5, FxExpr.compile("d").eval(v), 1e-9);
        assertEquals(1, FxExpr.compile("look").eval(v), 1e-9);
        assertEquals(0.2, FxExpr.compile("speed").eval(v), 1e-9);
        assertEquals(7, FxExpr.compile("ex + ez + ey").eval(v), 1e-9);
        assertFalse(FxExpr.compile("d").fixed());
        // Looking the other way is 0, never negative.
        FxExpr.viewer(v, 3, 0, 4, 0.6, 0, 0.8, 0);
        assertEquals(0, FxExpr.compile("look").eval(v), 1e-9);
        FxExpr.nobody(v);
        assertEquals(FxExpr.FAR, FxExpr.compile("d").eval(v), 1e-9);
        assertEquals(0, FxExpr.compile("look").eval(v), 1e-9);
        assertTrue(FxExpr.NAMES.contains("look") && FxExpr.NAMES.contains("ex ey ez"));
    }

    @Test
    void aFieldIsOnlyFreeWhenItSaysSo() {
        assertFalse(FxField.parse(json("{\"x\":\"u\"}")).free);
        assertTrue(FxField.parse(json("{\"x\":\"u\",\"ay\":-0.02}")).free);
        assertTrue(FxField.parse(json("{\"x\":\"u\",\"life\":40}")).free);
        assertTrue(FxField.parse(json("{\"x\":\"u\",\"bounce\":0.5}")).free);
    }

    @Test
    void aFreeParticleFallsAndBouncesOffTheGround() {
        FxField f = FxField.parse(json("{\"x\":1,\"y\":5,\"z\":2,\"ay\":-0.05,\"drag\":0,\"bounce\":0.5}"));
        double[] vars = new double[FxExpr.VARS], state = new double[FxField.STATE];
        FxField.Blocks ground = (x, y, z) -> y < 0;
        f.birth(0, 1, 0, 7, vars, state);
        assertEquals(5, state[1], 1e-9);
        double lowest = 9, highestAfterBounce = -9;
        boolean bounced = false;
        for (int t = 1; t <= 200; t++) {
            f.step(0, 1, t, 7, vars, state, ground);
            assertTrue(state[1] >= 0, "it never sinks into the ground: " + state[1]);
            lowest = Math.min(lowest, state[1]);
            if (state[4] > 0) bounced = true;
            if (bounced) highestAfterBounce = Math.max(highestAfterBounce, state[1]);
        }
        assertTrue(lowest < 0.3, "it reached the ground: " + lowest);
        assertTrue(bounced, "it came back up");
        assertTrue(highestAfterBounce < 4, "but lower than it fell from: " + highestAfterBounce);
        assertEquals(1, state[0], 1e-9);
        // Without bounce it passes through.
        FxField ghost = FxField.parse(json("{\"y\":5,\"ay\":-0.05,\"drag\":0}"));
        ghost.birth(0, 1, 0, 7, vars, state);
        for (int t = 1; t <= 60; t++) ghost.step(0, 1, t, 7, vars, state, ground);
        assertTrue(state[1] < 0);
    }

    @Test
    void aFreeParticleReadsItsOwnPlaceAndIsBornAgain() {
        // Pushed away from the watcher's eyes, and born somewhere new each life.
        FxField f = FxField.parse(json("{\"x\":\"rand(k)*10\",\"ax\":\"(px-ex)*0.01\",\"life\":20,\"alpha\":\"sin(a*pi)\"}"));
        double[] vars = new double[FxExpr.VARS], state = new double[FxField.STATE], out = new double[FxField.OUT];
        FxExpr.viewer(vars, -5, 0, 0, 1, 0, 0, 0);
        f.birth(3, 10, 0, 9, vars, state);
        double first = state[0];
        for (int t = 1; t < 20; t++) f.step(3, 10, t, 9, vars, state, null);
        assertTrue(state[0] > first, "it fled the watcher at x = -5");
        f.look(3, 10, 10, 9, vars, state, out);
        assertEquals(state[0], out[FxField.X], 1e-9);
        assertTrue(out[FxField.ALPHA] > 0.9, "brightest in the middle of its life: " + out[FxField.ALPHA]);
        f.step(3, 10, 20, 9, vars, state, null);
        assertEquals(1, state[8], "its second life");
        assertEquals(20, state[6]);
        assertFalse(state[0] == first, "born somewhere new");
        assertEquals(0, state[3], 1e-9, "and at rest");
        // Speeds are capped however hard the formula pushes.
        FxField wild = FxField.parse(json("{\"x\":0,\"ax\":1000}"));
        wild.birth(0, 1, 0, 1, vars, state);
        for (int t = 1; t < 400; t++) wild.step(0, 1, t, 1, vars, state, null);
        assertTrue(state[3] <= FxField.MAX_SPEED && state[0] <= FxField.REACH);
    }

    @Test
    void aTriggerFiresWhenItsConditionHasHeld() {
        FxTrigger once = FxTrigger.parse(json("{\"when\":\"d < 4\",\"hold\":3,\"tell\":\"came close\"}"));
        assertNotNull(once);
        FxTrigger.Watch w = new FxTrigger.Watch();
        double[] v = new double[FxExpr.VARS];
        FxExpr.viewer(v, 10, 0, 0, 1, 0, 0, 0);
        for (int t = 0; t < 10; t++) assertFalse(w.tick(once, t, 1, v));
        FxExpr.viewer(v, 2, 0, 0, 1, 0, 0, 0);
        assertFalse(w.tick(once, 10, 1, v));
        assertFalse(w.tick(once, 11, 1, v));
        assertTrue(w.tick(once, 12, 1, v), "after three ticks of it");
        for (int t = 13; t < 400; t++) assertFalse(w.tick(once, t, 1, v), "only once");

        FxTrigger again = FxTrigger.parse(json("{\"when\":\"d < 4\",\"again\":5,\"tell\":\"x\"}"));
        assertEquals(FxTrigger.MIN_AGAIN, again.again, "never oftener than the least wait");
        w = new FxTrigger.Watch();
        int fires = 0;
        // Watched every 2 ticks, as the server does for mortals without the mod.
        for (int t = 0; t < 100; t += 2) fires += w.tick(again, t, 2, v) ? 1 : 0;
        assertEquals(5, fires);
        // Stepping away breaks a hold.
        FxTrigger held = FxTrigger.parse(json("{\"when\":\"look > 0.9\",\"hold\":4,\"tell\":\"x\"}"));
        w = new FxTrigger.Watch();
        for (int t = 0; t < 40; t++) {
            FxExpr.viewer(v, 5, 0, 0, t % 3 == 0 ? 1 : -1, 0, 0, 0);
            assertFalse(w.tick(held, t, 1, v));
        }
    }

    @Test
    void aTriggerIsReadCarefully() {
        assertNull(FxTrigger.parse(json("{\"tell\":\"x\"}")));
        assertNull(FxTrigger.parse(json("{\"when\":\"zap(1)\"}")));
        FxTrigger t = FxTrigger.parse(json("{\"when\":1,\"tell\":\"  saw\\nit \\u00a7c all  \",\"wake\":true,\"key\":\"ab12\","
                + "\"then\":[{\"type\":\"sound\",\"key\":\"minecraft:block.bell.use\"},{\"type\":\"trigger\",\"when\":1}]}"));
        assertEquals("sawit c all", t.tell);
        assertTrue(t.wake);
        assertEquals("ab12", t.key);
        assertEquals(1, t.then.size(), "a trigger sets off no further triggers");
        assertEquals("", FxTrigger.parse(json("{\"when\":1,\"key\":\"bad key!\"}")).key);
    }

    @Test
    void theGodIsToldWhatIsWrongWithATrigger() throws Exception {
        for (String[] bad : new String[][] { { "{\"type\":\"trigger\",\"tell\":\"x\"}", "needs \"when\"" },
                { "{\"type\":\"trigger\",\"when\":\"d < q\",\"tell\":\"x\"}", "when: I do not know the name 'q'" },
                { "{\"type\":\"trigger\",\"when\":\"d < 4\"}", "needs \"then\"" },
                { "{\"type\":\"trigger\",\"when\":1,\"then\":[{\"type\":\"trigger\",\"when\":1,\"tell\":\"y\"}]}", "another trigger" },
                { "{\"type\":\"trigger\",\"when\":1,\"then\":[{\"type\":\"glyph\",\"text\":\"hi\",\"color\":\"$primary\"}]}", "#RRGGBB" },
                { "{\"type\":\"trigger\",\"when\":1,\"then\":[{\"type\":\"blob\"}]}", "in \"then\": Layer 1: unknown type" },
                { "{\"type\":\"field\",\"x\":1,\"ax\":\"push(1)\"}", "ax: I do not know the function 'push'" } }) {
            FxScenes.Invalid e = assertThrows(FxScenes.Invalid.class, () -> FxScenes.check("[" + bad[0] + "]"), bad[0]);
            assertTrue(e.getMessage().contains(bad[1]), bad[0] + " -> " + e.getMessage());
        }
        var ok = FxScenes.check("[{\"type\":\"trigger\",\"when\":\"(look > 0.97) * (d < 24)\",\"hold\":10,\"tell\":\"looked into the eye\","
                + "\"then\":{\"type\":\"sound\",\"key\":\"block.bell.use\"}}]");
        assertEquals(FxTrigger.DEFAULT_TICKS, FxScenes.length(ok));
        assertEquals("minecraft:block.bell.use", ok.get(0).getAsJsonObject().getAsJsonArray("then").get(0).getAsJsonObject().get("key").getAsString(),
                "the layers it plays are cleaned like any scene's");
        assertTrue(FxScenes.HELP.contains("trigger {when") && FxScenes.HELP.contains("keep their motion"));
        // Nothing of a trigger is sketched in dust.
        assertTrue(FxScenes.plain(ok, 0, 1, new java.util.Random(1), 100).isEmpty());
    }
}
