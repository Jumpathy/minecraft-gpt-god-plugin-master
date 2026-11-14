package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.god.tools.GodTool;
import net.bigyous.gptgodmc.god.tools.ToolRegistry;

/** The god-brain half of the effects: style, colour tokens, routines, bound effects, the library file. */
class FxBrainTest {
    private static final Set<String> POWERS = Set.of("smite", "heal", "announce", "fx_replay", "vessel_mood");

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    // ---- Style -----------------------------------------------------------------------------------------

    @Test
    void everyArchetypeHasACompleteStyleOfItsOwn() {
        List<String> seen = new ArrayList<>();
        for (String a : new String[] { "benevolent", "chill", "trickster", "evil", "wrathful", "cosmic", "librarian", "", null }) {
            FxStyle s = FxStyle.fallback(a);
            for (String c : List.of(s.primary, s.secondary, s.accent, s.shadow, s.hud)) assertTrue(FxStyle.isHex(c), a + " " + c);
            assertFalse(s.sprites.isEmpty());
            assertTrue(FxScenes.SPRITES.containsAll(s.sprites), a);
            assertTrue(FxScenes.SHAPES.containsAll(s.shapes), a);
            assertFalse(s.motion.isBlank());
            assertFalse(s.sigil.isBlank());
            assertEquals(FxStyle.MOODS, List.copyOf(s.sky.keySet()));
            s.sky.values().forEach(c -> assertTrue(FxStyle.isHex(c), a + " sky " + c));
            // Deterministic: the same archetype always starts the same.
            assertEquals(s.wire(), FxStyle.fallback(a).wire());
            if (a != null && !a.isEmpty()) {
                assertFalse(seen.contains(s.primary + s.shadow), a + " looks like another archetype");
                seen.add(s.primary + s.shadow);
            }
        }
        // A wrathful god's wrath is not the stock red: it leans to its own colour.
        assertNotEquals(FxStyle.fallback("wrathful").sky.get("wrathful"), FxStyle.fallback("chill").sky.get("wrathful"));
    }

    @Test
    void styleSurvivesJsonAndFillsWhatIsMissing() {
        FxStyle s = FxStyle.fallback("cosmic");
        s.by = "self";
        s.updated = 42;
        FxStyle back = FxStyle.fromJson(s.toJson(), "cosmic");
        assertEquals(s.wire(), back.wire());
        assertEquals("self", back.by);
        assertEquals(42, back.updated);
        // An old or hand-edited file: what it has is kept, the rest comes from the archetype.
        FxStyle part = FxStyle.fromJson("{\"primary\":\"#102030\",\"sky\":{\"calm\":\"#abcdef\",\"wrathful\":\"red\"}}", "evil");
        assertEquals("#102030", part.primary);
        assertEquals("#102030", part.hud, "the HUD accent follows the primary when none is set");
        assertEquals("#abcdef", part.sky.get("calm"));
        assertEquals(FxStyle.fallback("evil").sky.get("wrathful"), part.sky.get("wrathful"));
        assertEquals(FxStyle.fallback("evil").sprites, part.sprites);
        assertNull(FxStyle.fromJson("not json at all {", "evil"));
        // The wire form carries everything the mod needs.
        assertTrue(s.wire().keySet().containsAll(List.of("primary", "secondary", "accent", "shadow", "hud", "sigil", "motion",
                "sprites", "shapes", "sky")));
        assertEquals(6, s.wire().getAsJsonObject("sky").size());
    }

    @Test
    void theGodRewritesItsStyleStrictly() throws Exception {
        FxStyle s = FxStyle.fallback("chill");
        String before = s.toJson();
        List<String> changed = s.apply(obj("{\"primary\":\"ff0080\",\"sprites\":[\"Rune\",\"star\"],\"shapes\":\"helix, wings\","
                + "\"motion\":\"sharp and electric\",\"sigil\":\"☽\",\"sky\":\"{\\\"wrathful\\\":\\\"#B01010\\\"}\",\"sky_calm\":\"#CFE4FF\"}"),
                true);
        assertEquals("#FF0080", s.primary);
        assertEquals(List.of("rune", "star"), s.sprites);
        assertEquals(List.of("helix", "wings"), s.shapes);
        assertEquals("sharp and electric", s.motion);
        assertEquals("☽", s.sigil);
        assertEquals("#B01010", s.sky.get("wrathful"));
        assertEquals("#CFE4FF", s.sky.get("calm"));
        assertTrue(changed.containsAll(List.of("primary", "sprites", "shapes", "motion", "sigil", "sky.wrathful", "sky.calm")), changed.toString());
        assertTrue(s.describe().contains("#FF0080") && s.describe().contains("sharp and electric"));
        assertTrue(s.apply(new JsonObject(), true).isEmpty(), "no fields = just reading");

        String good = s.toJson();
        for (String bad : new String[] { "{\"primary\":\"crimson\"}", "{\"sprites\":[\"spark\",\"unicorn\"]}",
                "{\"shapes\":[]}", "{\"sky\":{\"hangry\":\"#FF0000\"}}", "{\"sky\":{\"calm\":\"blue\"}}", "{\"sky\":\"dark\"}",
                "{\"sigil\":\"far too long a sigil\"}", "{\"motion\":\"" + "x".repeat(61) + "\"}",
                "{\"accent\":\"#00FF00\",\"shadow\":\"nope\"}" }) {
            FxScenes.Invalid e = assertThrows(FxScenes.Invalid.class, () -> s.apply(obj(bad), true), bad);
            assertFalse(e.getMessage().isBlank());
            assertEquals(good, s.toJson(), "a refused change changes nothing: " + bad);
        }
        assertNotEquals(before, good);
    }

    @Test
    void aModelsDraftIsReadLeniently() {
        String reply = "Here is the style:\n```json\n{\"primary\":\"#3A0CA3\",\"secondary\":\"#7209B7\",\"accent\":\"gold\","
                + "\"sprites\":[\"rune\",\"dragon\"],\"motion\":\"slow and tidal\",\"sigil\":\"☽\","
                + "\"sky\":{\"calm\":\"#1B1B3A\",\"furious\":\"#FF0000\"}}\n```";
        FxStyle s = FxStyle.drafted("cosmic", reply);
        FxStyle f = FxStyle.fallback("cosmic");
        assertEquals("birth", s.by);
        assertEquals("#3A0CA3", s.primary);
        assertEquals(f.accent, s.accent, "an unfit value keeps the archetype's");
        assertEquals(List.of("rune"), s.sprites);
        assertEquals("slow and tidal", s.motion);
        assertEquals("#1B1B3A", s.sky.get("calm"));
        assertEquals(f.sky.get("wrathful"), s.sky.get("wrathful"));
        // No model, no key, or nonsense: the archetype's style stands.
        for (String none : new String[] { null, "", "I cannot do that.", "{broken" }) {
            FxStyle d = FxStyle.drafted("cosmic", none);
            assertEquals(f.wire(), d.wire());
            assertEquals("archetype", d.by);
        }
    }

    // ---- Tokens ----------------------------------------------------------------------------------------

    private static final String TOKENED = "[{\"type\":\"particles\",\"shape\":\"ring\",\"color\":[\"$primary\",\"$Shadow\"]},"
            + "{\"type\":\"sky\",\"color\":\"$mood\",\"strength\":0.6},{\"type\":\"dome\",\"color\":\"#102030\"},"
            + "{\"type\":\"glyph\",\"text\":\"$sigil\",\"color\":\"$accent\"},{\"type\":\"glyph\",\"text\":\"costs $5\"}]";

    @Test
    void scenesMayNameTheStyleInsteadOfColours() throws Exception {
        JsonArray layers = FxScenes.check(TOKENED);
        assertEquals(5, layers.size());
        FxScenes.Invalid e = assertThrows(FxScenes.Invalid.class,
                () -> FxScenes.check("[{\"type\":\"dome\",\"color\":\"$nope\"}]"));
        assertTrue(e.getMessage().contains("$primary"), e.getMessage());
        assertThrows(FxScenes.Invalid.class, () -> FxScenes.check("[{\"type\":\"dome\",\"color\":\"$sigil\"}]"));
        assertTrue(FxStyle.isColorToken(" $MOOD "));
        assertFalse(FxStyle.isColorToken("$sigil"));
        assertFalse(FxStyle.isColorToken(null));
    }

    @Test
    void oneSceneLooksDifferentForEachGodAndMood() throws Exception {
        JsonArray layers = FxScenes.check(TOKENED);
        String kept = layers.toString();
        FxStyle wrath = FxStyle.fallback("wrathful"), chill = FxStyle.fallback("chill");
        JsonArray a = wrath.resolve(layers, "calm"), b = chill.resolve(layers, "calm"), c = wrath.resolve(layers, "WRATHFUL");
        assertEquals(kept, layers.toString(), "the saved scene keeps its tokens");
        assertFalse(a.toString().contains("$primary") || a.toString().contains("$mood") || a.toString().contains("$sigil"));
        assertEquals(wrath.primary, a.get(0).getAsJsonObject().getAsJsonArray("color").get(0).getAsString());
        assertEquals(wrath.shadow, a.get(0).getAsJsonObject().getAsJsonArray("color").get(1).getAsString());
        assertEquals(chill.primary, b.get(0).getAsJsonObject().getAsJsonArray("color").get(0).getAsString());
        assertNotEquals(a.toString(), b.toString());
        assertEquals(wrath.sky.get("calm"), a.get(1).getAsJsonObject().get("color").getAsString());
        assertEquals(wrath.sky.get("wrathful"), c.get(1).getAsJsonObject().get("color").getAsString());
        assertEquals("#102030", a.get(2).getAsJsonObject().get("color").getAsString());
        assertEquals(wrath.sigil, a.get(3).getAsJsonObject().get("text").getAsString());
        assertEquals("costs $5", a.get(4).getAsJsonObject().get("text").getAsString(), "other text is left alone");
        assertEquals(0.6, a.get(1).getAsJsonObject().get("strength").getAsDouble());
        // A mood the style has no sky for falls back to the primary; resolved scenes are plain scenes again.
        assertEquals(wrath.primary, wrath.colorOf("$mood", "bored"));
        assertNull(wrath.colorOf("$sigil", "calm"));
        assertEquals(5, FxScenes.check(a.toString()).size());
        // The plain (no mod) rendering draws the god's colour, not the default.
        List<FxScenes.Dot> dots = FxScenes.plain(a, 0, 1, new Random(1), 50);
        assertFalse(dots.isEmpty());
        assertEquals(FxStyle.rgb(wrath.primary), dots.get(0).rgb());
    }

    // ---- Routines --------------------------------------------------------------------------------------

    private static final String ROUTINE = "[{\"tool\":\"Vessel_Mood\",\"args\":{\"mood\":\"wrathful\"}},"
            + "{\"tool\":\"fx_replay\",\"args\":\"{\\\"name\\\":\\\"ashen_verdict\\\",\\\"at\\\":\\\"Steve\\\"}\",\"delay_ticks\":20},"
            + "{\"tool\":\"smite\",\"args\":{\"player\":\"Steve\"},\"delay\":30},{\"tool\":\"announce\"}]";

    private static JsonElement text(String s) {
        return new com.google.gson.JsonPrimitive(s);
    }

    @Test
    void routinesAreValidatedStepByStep() throws Exception {
        List<FxRoutines.Step> steps = FxRoutines.check(text(ROUTINE), POWERS::contains);
        assertEquals(4, steps.size());
        assertEquals("vessel_mood", steps.get(0).tool);
        assertEquals("Steve", steps.get(1).args.get("at").getAsString(), "args given as JSON text are parsed");
        assertEquals(20, steps.get(1).delay);
        assertEquals(30, steps.get(2).delay, "\"delay\" is read as delay_ticks");
        assertEquals(0, steps.get(3).args.size());
        assertEquals(50, FxRoutines.totalDelay(steps));
        assertEquals("vessel_mood → fx_replay (+20t) → smite (+30t) → announce", FxRoutines.outline(steps));
        // Already-parsed arrays and {"steps": [...]} work too.
        assertEquals(4, FxRoutines.check(JsonParser.parseString(ROUTINE), POWERS::contains).size());
        assertEquals(1, FxRoutines.check(text("{\"steps\":[{\"tool\":\"heal\"}]}"), POWERS::contains).size());
        // The example in the help is itself valid.
        String example = FxRoutines.HELP.substring(FxRoutines.HELP.indexOf('['), FxRoutines.HELP.indexOf("]. ") + 1);
        assertEquals(2, FxRoutines.check(text(example), POWERS::contains).size());
    }

    @Test
    void routinesHaveLimits() {
        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i <= FxRoutines.MAX_STEPS; i++) many.append(i == 0 ? "" : ",").append("{\"tool\":\"heal\"}");
        String[][] bad = { { many + "]", "At most 12" }, { "[]", "no steps" }, { "", "no steps" }, { "{nope", "not valid JSON" },
                { "\"heal\"", "JSON array" }, { "[\"heal\"]", "not an object" }, { "[{\"args\":{}}]", "needs a \"tool\"" },
                { "[{\"tool\":\"teleport_everyone\"}]", "no power called" },
                { "[{\"tool\":\"library_run\",\"args\":{\"name\":\"me\"}}]", "cannot be part" },
                { "[{\"tool\":\"command_done\"}]", "cannot be part" }, { "[{\"tool\":\"library_save\"}]", "cannot be part" },
                { "[{\"tool\":\"heal\",\"args\":[1]}]", "must be an object" },
                { "[{\"tool\":\"heal\",\"args\":\"{oops\"}]", "not valid JSON" },
                { "[{\"tool\":\"heal\",\"delay_ticks\":201}]", "0 to 200" }, { "[{\"tool\":\"heal\",\"delay_ticks\":-1}]", "0 to 200" },
                { "[{\"tool\":\"heal\",\"delay_ticks\":\"soon\"}]", "must be a number" },
                { "[{\"tool\":\"heal\",\"delay_ticks\":200},{\"tool\":\"heal\",\"delay_ticks\":200},{\"tool\":\"heal\",\"delay_ticks\":1}]",
                        "too long in all" },
                { "[{\"tool\":\"heal\",\"args\":{\"x\":\"" + "y".repeat(FxRoutines.MAX_ARGS_CHARS) + "\"}}]", "too long" } };
        for (String[] b : bad) {
            FxScenes.Invalid e = assertThrows(FxScenes.Invalid.class, () -> FxRoutines.check(text(b[0]), POWERS::contains),
                    b[0].length() > 80 ? b[1] : b[0]);
            assertTrue(e.getMessage().contains(b[1]), e.getMessage());
        }
        assertThrows(FxScenes.Invalid.class, () -> FxRoutines.check(null, POWERS::contains));
        // Exactly at the limits is fine.
        StringBuilder full = new StringBuilder("[");
        for (int i = 0; i < FxRoutines.MAX_STEPS; i++) full.append(i == 0 ? "" : ",").append("{\"tool\":\"heal\",\"delay_ticks\":")
                .append(i < 2 ? 200 : 0).append('}');
        assertEquals(FxRoutines.MAX_STEPS, assertNoThrow(full + "]").size());
    }

    private static List<FxRoutines.Step> assertNoThrow(String steps) {
        try {
            return FxRoutines.check(text(steps), POWERS::contains);
        } catch (FxScenes.Invalid e) {
            throw new AssertionError(e.getMessage());
        }
    }

    @Test
    void powersNameTheirTargetAndSomeCannotBeBound() {
        assertEquals("Steve", FxRoutines.targetOf(obj("{\"player\":\" Steve \",\"at\":\"spawn\"}")));
        assertEquals("spawn", FxRoutines.targetOf(obj("{\"power\":3,\"at\":\"spawn\"}")));
        assertEquals("10 64 -3", FxRoutines.targetOf(obj("{\"target\":\"10 64 -3\"}")));
        assertNull(FxRoutines.targetOf(obj("{\"player\":\"\",\"text\":\"hi\"}")));
        assertNull(FxRoutines.targetOf(null));
        assertTrue(FxRoutines.bindable("smite"));
        assertTrue(FxRoutines.bindable("omen"), "the world omen is a power like any other");
        for (String no : new String[] { "fx_cast", "fx_replay", "fx_bind", "fx_omen", "library_run", "command_done", null }) {
            assertFalse(FxRoutines.bindable(no), String.valueOf(no));
        }
    }

    // ---- The library file ------------------------------------------------------------------------------

    @Test
    void routinesAndEffectsShareTheLibraryFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("library.json");
        FxLibrary lib = new FxLibrary(file);
        lib.keep("ashen_verdict", "for the faithless", FxScenes.check(TOKENED), 100);
        lib.keepRoutine("Judgement Day", "smite with ceremony", FxRoutines.check(text(ROUTINE), POWERS::contains), 200);
        assertEquals(2, lib.size());
        assertNull(lib.find("judgement_day"), "fx_replay never finds a routine");
        assertTrue(lib.any("judgement day").isRoutine());
        assertFalse(lib.any("ashen_verdict").isRoutine());
        assertEquals("ashen_verdict (for the faithless)", lib.index(10));
        assertEquals("judgement_day (smite with ceremony)", lib.routines(10));
        lib.used(lib.any("judgement_day"), 300);

        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"delay_ticks\": 20"), json);
        assertTrue(json.contains("\"kind\": \"routine\""), json);
        assertTrue(json.contains("$primary"), "the saved effect keeps its tokens");

        FxLibrary again = new FxLibrary(file);
        FxLibrary.Entry r = again.any("judgement_day");
        assertEquals(4, r.steps.size());
        assertEquals(1, r.uses);
        assertEquals("Steve", r.steps.get(2).args.get("player").getAsString());
        assertEquals(30, r.steps.get(2).delay);
        assertEquals(5, again.find("ashen_verdict").layers.size());
        // Saving a routine again refines it and keeps its note and count.
        again.keepRoutine("judgement_day", null, FxRoutines.check(text("[{\"tool\":\"heal\"}]"), POWERS::contains), 400);
        assertEquals(1, again.any("judgement_day").steps.size());
        assertEquals("smite with ceremony", again.any("judgement_day").note);
        assertEquals(1, again.any("judgement_day").uses);
        assertTrue(again.forget("judgement_day"));
        assertEquals("none yet", again.routines(10));
        assertEquals(1, again.all().size());
    }

    @Test
    void aLibraryWrittenBeforeRoutinesStillLoads(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("library.json");
        Files.writeString(file, """
                [
                  {"name": "dawn", "kind": "fx", "note": "morning", "layers": [{"type": "dome", "radius": 5}],
                   "uses": 3, "lastUsed": 50, "created": 10},
                  {"name": "old_no_kind", "layers": [{"type": "beam"}], "uses": 0, "lastUsed": 0, "created": 0},
                  {"name": "broken"},
                  null
                ]
                """, StandardCharsets.UTF_8);
        FxLibrary lib = new FxLibrary(file);
        assertEquals(2, lib.size());
        FxLibrary.Entry dawn = lib.find("dawn");
        assertEquals(3, dawn.uses);
        assertEquals("morning", dawn.note);
        assertEquals("fx", dawn.kind);
        assertNull(dawn.steps);
        assertNull(dawn.bound);
        assertEquals("fx", lib.find("old_no_kind").kind);
        assertEquals("none", lib.bindingsText());
        assertNull(lib.boundTo("smite"));
        // And it is written back in a form the old reader understood: a list of entries with layers.
        lib.used(dawn, 60);
        JsonArray written = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonArray();
        assertEquals(2, written.size());
        assertTrue(written.get(0).getAsJsonObject().has("layers"));
        assertFalse(written.get(0).getAsJsonObject().has("steps"));
        assertFalse(written.get(0).getAsJsonObject().has("bound"));
    }

    @Test
    void effectsAreBoundToPowers(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("library.json");
        FxLibrary lib = new FxLibrary(file);
        JsonArray dome = FxScenes.check("[{\"type\":\"dome\"}]");
        lib.keep("ashen_verdict", null, dome, 1);
        lib.keep("dawn_ring", null, dome, 2);
        lib.keepRoutine("show", null, FxRoutines.check(text("[{\"tool\":\"heal\"}]"), POWERS::contains), 3);
        assertNull(lib.bind("smite", "no_such_effect"));
        assertNull(lib.bind("smite", "show"), "a routine cannot be bound");
        assertEquals("ashen_verdict", lib.bind("smite", "Ashen Verdict").name);
        lib.bind("heal", "dawn_ring");
        lib.bind("revive", "dawn_ring");
        assertEquals("ashen_verdict", lib.boundTo("smite").name);
        assertEquals("dawn_ring", lib.boundTo("revive").name);
        assertNull(lib.boundTo("meteor"));
        assertNull(lib.boundTo(null));
        assertEquals(Map.of("smite", "ashen_verdict", "heal", "dawn_ring", "revive", "dawn_ring"), lib.bindings());
        assertEquals("smite → ashen_verdict; heal → dawn_ring; revive → dawn_ring", lib.bindingsText());
        // One effect per power: binding again moves it.
        lib.bind("smite", "dawn_ring");
        assertEquals("dawn_ring", lib.boundTo("smite").name);
        assertNull(lib.find("ashen_verdict").bound);
        // Bindings survive a restart and a refinement of the effect; they go with the effect when it is forgotten.
        FxLibrary again = new FxLibrary(file);
        assertEquals(3, again.bindings().size());
        again.keep("dawn_ring", "brighter", FxScenes.check("[{\"type\":\"beam\"}]"), 9);
        assertEquals("dawn_ring", again.boundTo("heal").name);
        assertEquals("beam", again.boundTo("heal").layers.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("dawn_ring", again.unbind("heal"));
        assertNull(again.unbind("heal"));
        assertNull(again.boundTo("heal"));
        assertTrue(again.forget("dawn_ring"));
        assertEquals("none", again.bindingsText());
        // A cap, so the god's every act does not become a light show; rebinding a bound power is still allowed.
        for (int i = 0; i < FxLibrary.MAX_BINDINGS; i++) again.bind("power" + i, "ashen_verdict");
        assertThrows(IllegalStateException.class, () -> again.bind("one_more", "ashen_verdict"));
        assertNotNull(again.bind("power0", "ashen_verdict"));
        assertEquals(FxLibrary.MAX_BINDINGS, again.bindings().size());
    }

    // ---- The tools -------------------------------------------------------------------------------------

    @Test
    void theNewPowersComeLastAndInOrder() {
        List<String> names = new ArrayList<>();
        for (GodTool t : ToolRegistry.all()) names.add(t.name());
        List<String> mine = List.of("fx_style", "library_save", "library_run", "library_read", "library_forget", "fx_bind", "fx_omen");
        // Powers added since (`map`, `see_build`, `drop_block`, the forge's relic powers, the living land and
        // oaths, then `feel`) come after these, never before or among them.
        int from = names.indexOf("fx_style");
        assertEquals(mine, names.subList(from, from + mine.size()));
        assertEquals(List.of("map", "see_build", "drop_block", "forge_relic", "give_relic", "show_relic", "imbue_relic",
                "tend", "witness_oath", "resolve_oath", "worldedit", "fx_paint", "feel", "muse", "reflect", "script", "labyrinth", "make_world", "ward", "land_history", "disguise", "armory", "strike", "statue", "search_web", "locate"), names.subList(from + mine.size(), names.size()));
        assertTrue(names.indexOf("fx_cast") < names.indexOf("fx_style"));
        // Live descriptions work with an empty library.
        assertTrue(ToolRegistry.get("fx_bind").description().contains("Your bindings: none"));
        assertTrue(ToolRegistry.get("library_run").description().contains("Your routines: none yet"));
        // Every power a routine may never contain really exists (or is the routine runner itself).
        for (String n : FxRoutines.NOT_STEPS) assertNotNull(ToolRegistry.get(n), n);
    }
}
