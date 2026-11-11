package net.bigyous.gptgodmc.god.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Memory;

class MemoryToolsTest {

    @BeforeEach
    void freshMemory() {
        Memory.reset();
    }

    private static ToolResult run(String tool, JsonObject args) throws ToolException {
        return ToolRegistry.get(tool).handler().run(new Args(args));
    }

    @Test
    void newPowersComeLastInTheirGroupAndKeepTheOldOrder() {
        List<String> names = new ArrayList<>();
        ToolRegistry.all().forEach(t -> names.add(t.name()));
        int resolve = names.indexOf("resolve_quest");
        assertEquals(List.of("remember", "judge", "set_quest", "resolve_quest", "recall", "search_annals",
                "rename_self"), names.subList(resolve - 3, resolve + 4));
    }

    @Test
    void searchAnnalsIsBounded() throws ToolException {
        Memory m = Memory.get();
        for (int i = 0; i < 200; i++) {
            m.annals().append("speech", List.of("Steve"), "Steve said in chat: \"praise be, line " + i + " "
                    + "y".repeat(250) + "\"");
        }
        JsonObject args = new JsonObject();
        args.addProperty("player", "steve");
        args.addProperty("limit", 40);
        String text = run("search_annals", args).text();
        assertTrue(text.length() <= MemoryTools.MAX_OUTPUT, "length " + text.length());
        assertTrue(text.contains("line 199"), "newest first");
        assertTrue(text.contains("more matches exist"), text.substring(text.length() - 120));
        assertTrue(text.split("\n").length <= MemoryTools.MAX_LINES + 2);

        JsonObject none = new JsonObject();
        none.addProperty("query", "dragon egg");
        assertTrue(run("search_annals", none).text().startsWith("Nothing in your annals matches"));

        JsonObject few = new JsonObject();
        few.addProperty("query", "line 7 ");
        few.addProperty("kind", "speech");
        few.addProperty("hours", 1);
        String some = run("search_annals", few).text();
        assertFalse(some.contains("more matches exist") && some.length() > MemoryTools.MAX_OUTPUT);
    }

    @Test
    void recallShowsTheWholeRecordWithinBounds() throws ToolException {
        Memory m = Memory.get();
        m.adjustFavor("Steve", -7, "betrayed me");
        for (int i = 0; i < GodSettings.deedsKept(); i++) {
            m.addDeed("Steve", "deed " + i + " " + "z".repeat(150), System.currentTimeMillis() - (100 - i) * 60_000L);
        }
        m.annals().append("event", List.of("Steve"), "Steve burned the chapel");
        JsonObject args = new JsonObject();
        args.addProperty("player", "ste");
        String text = run("recall", args).text();
        assertTrue(text.length() <= MemoryTools.MAX_OUTPUT, "length " + text.length());
        assertTrue(text.startsWith("### Steve — favor -7 (despised"), text.substring(0, 80));
        assertTrue(text.contains("deeds (newest first)"));

        JsonObject small = new JsonObject();
        small.addProperty("player", "Alex");
        m.addDeed("Alex", "planted a grove");
        m.annals().append("event", List.of("Alex"), "Alex planted 30 saplings");
        String alex = run("recall", small).text();
        assertTrue(alex.contains("planted a grove") && alex.contains("Alex planted 30 saplings"), alex);

        JsonObject unknown = new JsonObject();
        unknown.addProperty("player", "Nobody");
        assertThrows(ToolException.class, () -> run("recall", unknown));
    }

    @Test
    void rememberCanRecordADeed() throws ToolException {
        JsonObject args = new JsonObject();
        args.addProperty("about", "Zed");
        args.addProperty("note", "carried the relic to my altar");
        args.addProperty("kind", "deed");
        run("remember", args);
        Memory.Subject s = Memory.get().subjectIfAny("Zed");
        assertEquals("carried the relic to my altar", s.deeds.get(0).text);
        assertTrue(s.notes.isEmpty());
    }
}
