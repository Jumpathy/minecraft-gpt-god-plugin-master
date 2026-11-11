package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class FxScenesTest {
    private static final String NOVA = "[{\"type\":\"particles\",\"shape\":\"Sphere\",\"motion\":\"inward\",\"radius\":6,"
            + "\"count\":400,\"speed\":0.3,\"sprite\":\"spark\",\"color\":[\"#FFF6D0\",\"#FFB040\"]},"
            + "{\"type\":\"shockwave\",\"delay\":20,\"radius\":14,\"duration\":20,\"color\":\"#FFD86A\"},"
            + "{\"type\":\"screen\",\"delay\":20,\"kind\":\"flash\",\"intensity\":0.7,\"duration\":10},"
            + "{\"type\":\"sound\",\"delay\":20,\"key\":\"entity.generic.explode\",\"pitch\":0.6}]";

    @Test
    void theExampleInTheHelpIsValid() throws Exception {
        String example = FxScenes.HELP.substring(FxScenes.HELP.indexOf("Example: ") + 9);
        assertEquals(4, FxScenes.check(example).size());
    }

    @Test
    void checkCleansAndMeasures() throws Exception {
        JsonArray layers = FxScenes.check(NOVA);
        assertEquals("sphere", layers.get(0).getAsJsonObject().get("shape").getAsString());
        assertEquals("minecraft:entity.generic.explode", layers.get(3).getAsJsonObject().get("key").getAsString());
        assertEquals(40, FxScenes.length(layers));
        JsonObject m = FxScenes.message("omen", new double[] { 1.234, 64, -5 }, 17, 2, true, layers);
        assertEquals("scene", m.get("t").getAsString());
        assertEquals(17, m.get("follow").getAsInt());
        assertEquals(40, m.get("life").getAsInt());
        assertEquals(1.23, m.getAsJsonArray("at").get(0).getAsDouble());
        assertTrue(FxScenes.message(null, new double[3], -1, 1, false, layers).get("follow") == null);
    }

    @Test
    void mistakesAreExplainedToTheGod() {
        for (String[] bad : new String[][] { { "", "empty" }, { "nope", "JSON" }, { "[]", "no layers" },
                { "[{\"type\":\"laser\"}]", "unknown type" }, { "[{\"shape\":\"cube\"}]", "unknown shape" },
                { "[{\"color\":\"red\"}]", "#RRGGBB" }, { "[{\"type\":\"sound\",\"key\":\"bad key!\"}]", "key" },
                { "[{\"type\":\"glyph\"}]", "text" }, { "[{\"type\":\"screen\",\"kind\":\"melt\"}]", "unknown kind" } }) {
            FxScenes.Invalid e = assertThrows(FxScenes.Invalid.class, () -> FxScenes.check(bad[0]), bad[0]);
            assertTrue(e.getMessage().contains(bad[1]), e.getMessage());
        }
        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i <= FxScenes.MAX_LAYERS; i++) many.append(i == 0 ? "" : ",").append("{}");
        assertThrows(FxScenes.Invalid.class, () -> FxScenes.check(many + "]"));
    }

    @Test
    void aSingleLayerOrAnObjectWithLayersIsAccepted() throws Exception {
        assertEquals(1, FxScenes.check("{\"type\":\"dome\",\"radius\":5}").size());
        assertEquals(1, FxScenes.check("{\"layers\":[{\"shape\":\"ring\"}]}").size());
    }

    @Test
    void thePlainVersionFollowsTheTimelineAndBudget() throws Exception {
        JsonArray layers = FxScenes.check(NOVA);
        Random rnd = new Random(1);
        List<FxScenes.Dot> early = FxScenes.plain(layers, 2, 1, rnd, 140);
        assertFalse(early.isEmpty());
        for (FxScenes.Dot d : early) {
            assertTrue(Math.sqrt(d.x() * d.x() + d.y() * d.y() + d.z() * d.z()) <= 6.01, "inside the sphere");
        }
        // The shockwave has begun by tick 30: dots on a ring of radius 7, flat on the ground.
        List<FxScenes.Dot> wave = FxScenes.plain(FxScenes.check("[{\"type\":\"shockwave\",\"radius\":14,\"duration\":20}]"), 10, 1,
                rnd, 140);
        for (FxScenes.Dot d : wave) {
            assertEquals(7, Math.sqrt(d.x() * d.x() + d.z() * d.z()), 0.01);
            assertEquals(0, d.y(), 0.001);
        }
        assertTrue(FxScenes.plain(layers, 2, 1, rnd, 25).size() <= 25);
        assertTrue(FxScenes.plain(layers, 500, 1, rnd, 140).isEmpty(), "over when the scene is");
        assertEquals(1, FxScenes.cues(layers).size());
        assertEquals(20, FxScenes.cues(layers).get(0).tick());
    }

    @Test
    void theLibraryRemembersAcrossRestarts(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("library.json");
        FxLibrary lib = new FxLibrary(file);
        assertEquals("none yet", lib.index(10));
        lib.keep("Ashen Verdict!", "for the faithless", FxScenes.check(NOVA), 100);
        lib.keep("dawn", null, FxScenes.check("[{\"type\":\"dome\"}]"), 200);
        assertNotNull(lib.find("ashen verdict"));
        lib.used(lib.find("ashen_verdict"), 300);
        assertTrue(lib.index(10).startsWith("ashen_verdict (for the faithless)"), lib.index(10));
        assertTrue(Files.exists(file));
        FxLibrary again = new FxLibrary(file);
        assertEquals(2, again.size());
        assertEquals(1, again.find("ashen_verdict").uses);
        assertEquals(4, again.find("ashen_verdict").layers.size());
        // Saving over a name keeps its note and use count.
        again.keep("ashen_verdict", null, FxScenes.check("[{\"type\":\"dome\"}]"), 400);
        assertEquals("for the faithless", again.find("ashen_verdict").note);
        assertEquals(1, again.find("ashen_verdict").uses);
        assertTrue(again.forget("dawn"));
        assertNull(again.find("dawn"));
        assertThrows(IllegalArgumentException.class, () -> again.keep("!!!", null, new JsonArray(), 1));
        assertEquals(JsonParser.parseString("[]"), new JsonArray());
    }

    @Test
    void aFullLibraryDropsTheLeastUsed(@TempDir Path dir) throws Exception {
        FxLibrary lib = new FxLibrary(dir.resolve("library.json"));
        JsonArray one = FxScenes.check("[{\"type\":\"dome\"}]");
        lib.keep("favourite", null, one, 1);
        lib.used(lib.find("favourite"), 2);
        for (int i = 0; i < FxLibrary.MAX_ENTRIES + 5; i++) lib.keep("e" + i, null, one, 10 + i);
        assertEquals(FxLibrary.MAX_ENTRIES, lib.size());
        assertNotNull(lib.find("favourite"));
        assertNull(lib.find("e0"));
    }

    @Test
    void theGodCanDesignSoundsAndNameAFallback() throws Exception {
        JsonArray layers = FxScenes.check("[{\"type\":\"synth\",\"delay\":8,\"fallback\":\"entity.lightning_bolt.thunder\",\"voices\":["
                + "{\"wave\":\"Noise\",\"lowpass\":[300,6000]},{\"wave\":\"sine\",\"freq\":[80,30]}]},"
                + "{\"type\":\"sound\",\"key\":\"gptgodmc:god.smite\"}]");
        assertEquals("noise", layers.get(0).getAsJsonObject().getAsJsonArray("voices").get(0).getAsJsonObject().get("wave").getAsString());
        List<FxScenes.Cue> cues = FxScenes.cues(layers);
        assertEquals(2, cues.size());
        assertEquals("minecraft:entity.lightning_bolt.thunder", cues.get(0).key());
        assertEquals(8, cues.get(0).tick());
        assertEquals("gptgodmc:god.smite", cues.get(1).key());
        for (String[] bad : new String[][] { { "[{\"type\":\"synth\"}]", "voices" },
                { "[{\"type\":\"synth\",\"voices\":[{\"wave\":\"kazoo\"}]}]", "unknown wave" },
                { "[{\"type\":\"synth\",\"voices\":[{}],\"fallback\":\"no spaces!\"}]", "fallback" } }) {
            FxScenes.Invalid e = assertThrows(FxScenes.Invalid.class, () -> FxScenes.check(bad[0]), bad[0]);
            assertTrue(e.getMessage().contains(bad[1]), e.getMessage());
        }
        assertTrue(FxScenes.HELP.contains("synth {voices"));
    }
}
