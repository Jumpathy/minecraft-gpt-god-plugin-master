package net.bigyous.gptgodmc.god.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;

import net.bigyous.gptgodmc.god.FxRoutines;
import net.bigyous.gptgodmc.god.Guard;
import net.bigyous.gptgodmc.god.Power;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.ScriptLibrary;
import net.bigyous.gptgodmc.god.script.ScriptApi;
import net.bigyous.gptgodmc.god.script.ScriptHost;
import net.bigyous.gptgodmc.god.script.ScriptLimits;
import net.bigyous.gptgodmc.god.script.ScriptRun;

/** The script power around the interpreter: saved scripts and their parameters, the arguments, the tool itself. */
class ScriptToolsTest {

    /** A host that only counts blocks. */
    private static final class Counting implements ScriptHost {
        final List<Placement> blocks = new ArrayList<>();

        @Override
        public String block(String id) {
            return id;
        }

        @Override
        public List<Outcome> perform(List<Call> calls) {
            List<Outcome> out = new ArrayList<>();
            for (Call c : calls) {
                if (c.fn().equals("world.place")) c.args().forEach(p -> blocks.add((Placement) p));
                out.add(Outcome.ok(null));
            }
            return out;
        }

        @Override
        public Map<String, Object> power(String name, Map<String, Object> args) {
            return Map.of("ok", true, "text", "");
        }

        @Override
        public void sleep(int ticks) {
        }
    }

    private static final String TOWER = "for (var y = 0; y < args.height; y++) world.set(args.x, 64 + y, 0, args.block);\n"
            + "log(args.height + ' of ' + args.block);";

    private static ScriptRun.Result run(ScriptLibrary.Entry e, String given, Counting host) throws ToolException {
        JsonObject merged = ScriptLibrary.merged(e, ScriptTools.arguments(given == null ? null : new JsonPrimitive(given)));
        Map<String, Object> args = new Gson().fromJson(merged, new TypeToken<LinkedHashMap<String, Object>>() {
        }.getType());
        return new ScriptRun(e.code, args, ScriptLimits.DEFAULT, host).run();
    }

    @Test
    void aSavedScriptRunsAgainByNameWithItsParameters(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("scripts.json");
        ScriptLibrary lib = new ScriptLibrary(file);
        assertEquals("none yet", lib.index(10));
        JsonObject saved = JsonParser.parseString("{\"height\": 3, \"block\": \"stone\", \"x\": 5}").getAsJsonObject();
        ScriptLibrary.Entry e = lib.keep("Stone Tower!", "a plain tower", TOWER, saved, 1000);
        assertEquals("stone_tower", e.name);
        assertEquals("height=3, block=\"stone\", x=5", e.params());

        // With no arguments it runs as it was saved.
        Counting host = new Counting();
        ScriptRun.Result r = run(lib.find("stone tower"), null, host);
        assertTrue(r.ok(), r.error());
        assertEquals("3 of stone", r.log());
        assertEquals(3, host.blocks.size());

        // Arguments given now replace the saved ones, one by one.
        host = new Counting();
        r = run(lib.find("stone_tower"), "{\"height\": 6, \"block\": \"gold_block\"}", host);
        assertEquals("6 of gold_block", r.log());
        assertEquals(6, host.blocks.size());
        assertEquals(new ScriptHost.Placement(5, 69, 0, "gold_block"), host.blocks.get(5));
        assertEquals(3, lib.find("stone_tower").defaults.get("height").getAsInt(), "a run does not change what was saved");

        // It is on disk, and comes back after a restart with its uses.
        lib.used(e, 2000);
        assertTrue(Files.readString(file).contains("\"stone_tower\""));
        ScriptLibrary again = new ScriptLibrary(file);
        assertEquals(1, again.find("stone_tower").uses);
        assertEquals(TOWER, again.find("stone_tower").code);
        assertEquals("stone_tower(height=3, block=\"stone\", x=5): a plain tower", again.index(10));

        // Saving under the same name refines it and keeps its history; forgetting removes it.
        ScriptLibrary.Entry better = again.keep("stone_tower", null, TOWER + "\n// better", new JsonObject(), 3000);
        assertEquals(1, better.uses);
        assertEquals("a plain tower", better.note);
        assertEquals("", better.params());
        assertTrue(again.forget("Stone Tower"));
        assertNull(again.find("stone_tower"));
        assertFalse(again.forget("stone_tower"));
    }

    @Test
    void theLibraryIsBoundedAndListedByName() {
        ScriptLibrary lib = new ScriptLibrary(null);
        for (int i = 0; i < ScriptLibrary.MAX_ENTRIES + 5; i++) {
            ScriptLibrary.Entry e = lib.keep("s" + String.format("%02d", i), null, "log(" + i + ")", null, i);
            if (i == 0) for (int n = 0; n < 3; n++) lib.used(e, i); // the one that is used stays
        }
        assertEquals(ScriptLibrary.MAX_ENTRIES, lib.size());
        assertNotNull(lib.find("s00"));
        assertNull(lib.find("s01"));
        // By name, so using a script never reorders the tool's description (prompt cache).
        String before = lib.index(40);
        lib.used(lib.find("s20"), 99);
        assertEquals(before, lib.index(40));
        assertTrue(before.startsWith("s00; s06; s07"), before);
        assertThrows(IllegalArgumentException.class, () -> lib.keep("!!!", null, "log(1)", null, 1));
        assertThrows(IllegalArgumentException.class, () -> lib.keep("x", null, " ", null, 1));
        assertThrows(IllegalArgumentException.class, () -> lib.keep("x", null, "x".repeat(ScriptLimits.MAX_CODE + 1), null, 1));
    }

    @Test
    void argumentsAreAJsonObjectOrItsText() throws Exception {
        assertEquals(0, ScriptTools.arguments(null).size());
        assertEquals(0, ScriptTools.arguments(new JsonPrimitive("  ")).size());
        assertEquals(12, ScriptTools.arguments(new JsonPrimitive("{\"height\": 12}")).get("height").getAsInt());
        JsonObject o = new JsonObject();
        o.addProperty("block", "glass");
        assertEquals("glass", ScriptTools.arguments(o).get("block").getAsString());
        assertTrue(assertThrows(ToolException.class, () -> ScriptTools.arguments(new JsonPrimitive("{height: "))).getMessage()
                .contains("not valid JSON"));
        assertTrue(assertThrows(ToolException.class, () -> ScriptTools.arguments(new JsonPrimitive("[1, 2]"))).getMessage()
                .contains("must be a JSON object"));
    }

    @Test
    void thePowerIsRegisteredAndTeachesItsLanguage() {
        List<String> names = new ArrayList<>();
        for (GodTool t : ToolRegistry.all()) names.add(t.name());
        // Appended after every power that existed before it (FxBrainTest pins the exact tail of the list).
        assertTrue(names.indexOf("script") > names.indexOf("feel"), names.toString());
        GodTool tool = ToolRegistry.get("script");
        assertFalse(tool.mainThread(), "a program never runs on the server thread");
        String d = tool.description();
        assertTrue(d.contains(ScriptApi.HELP));
        assertTrue(d.contains("Your saved scripts: none yet"), d);
        assertTrue(d.contains(ScriptLimits.DEFAULT.describe()));
        assertEquals(d, tool.description(), "the description is stable from thought to thought");
        assertTrue(d.length() < 7000, "the description costs every thought: " + d.length() + " characters");
        assertEquals(ScriptLimits.DEFAULT, ScriptTools.limits());
        assertTrue(ScriptTools.enabled());
    }

    @Test
    void savedScriptsAreReadAndForgottenThroughThePower() throws Exception {
        ScriptLibrary lib = ScriptLibrary.of(Realm.current());
        lib.keep("test_tower", "for the test", TOWER, JsonParser.parseString("{\"height\": 2}").getAsJsonObject(), 1);
        try {
            GodTool tool = ToolRegistry.get("script");
            ToolResult read = tool.handler().run(new Args(JsonParser.parseString("{\"action\":\"read\",\"name\":\"Test Tower\"}").getAsJsonObject()));
            assertTrue(read.text().contains(TOWER));
            assertTrue(read.text().contains("height=2"));
            assertTrue(tool.description().contains("test_tower(height=2): for the test"));
            ToolException none = assertThrows(ToolException.class,
                    () -> tool.handler().run(new Args(JsonParser.parseString("{\"action\":\"read\",\"name\":\"nope\"}").getAsJsonObject())));
            assertTrue(none.getMessage().contains("test_tower"), none.getMessage());
            ToolResult gone = tool.handler().run(new Args(JsonParser.parseString("{\"action\":\"forget\",\"name\":\"test_tower\"}").getAsJsonObject()));
            assertFalse(gone.isError());
            assertNull(lib.find("test_tower"));
            ToolException neither = assertThrows(ToolException.class, () -> tool.handler().run(new Args(new JsonObject())));
            assertTrue(neither.getMessage().contains("Give code"), neither.getMessage());
        } finally {
            lib.forget("test_tower");
        }
    }

    @Test
    void aScriptReachesNoPowerItShouldNot() {
        // Never itself, a routine (which may hold a script), the library's writers, or the operator's answer.
        assertTrue(ScriptApi.NOT_POWERS.containsAll(List.of("script", "library_run", "command_done")));
        for (String n : ScriptApi.NOT_POWERS) assertNotNull(ToolRegistry.get(n), n);
        // A script may be a step of a routine.
        assertFalse(FxRoutines.NOT_STEPS.contains("script"));
        // The powers whose gates the script's direct calls borrow all exist, and are the ones a lesser or
        // greater god is refused, or the guard locks.
        for (String gate : List.of("smite", "build", "fling", "teleport", "apply_effect", "set_time", "set_weather", "run_commands",
                "explode", "spawn_entity")) {
            assertNotNull(ToolRegistry.get(gate), gate);
        }
        assertFalse(Power.LESSER.allows("build"));
        assertFalse(Power.LESSER.allows("smite"));
        assertFalse(Power.LESSER.allows("fling"));
        assertFalse(Power.LESSER.allows("teleport"));
        assertFalse(Power.GREATER.allows("run_commands"));
        assertFalse(Power.GREATER.allows("set_time"));
        assertTrue(Guard.RESTRICTED.contains("run_commands"));
        assertTrue(Guard.RESTRICTED.contains("explode"));
    }
}
