package net.bigyous.gptgodmc.god.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class ToolRegistryTest {

    @Test
    void everyToolHasAValidSchema() {
        Set<String> names = new HashSet<>();
        for (GodTool t : ToolRegistry.all()) {
            assertTrue(names.add(t.name()), "duplicate tool " + t.name());
            assertTrue(t.name().matches("^[a-z_]{3,64}$"), "bad tool name " + t.name());
            assertTrue(t.description().length() > 20, t.name() + " needs a real description");
            for (String req : t.schema().required()) {
                assertTrue(t.schema().properties().containsKey(req), t.name() + " requires unknown field " + req);
            }
            t.schema().properties().forEach((k, v) -> assertNotNull(v.get("description"), t.name() + "." + k));
        }
        assertTrue(names.size() >= 30, "expected the full set of powers, got " + names);
        assertTrue(names.containsAll(List.of("whisper", "announce", "look", "build", "vessel_go", "run_commands",
                "remember", "judge", "set_quest", "smite", "conjure", "particles", "fling", "meteor", "earthquake",
                "custom_power")));
    }

    @Test
    void sdkToolDefinitionsBuild() {
        assertEquals(ToolRegistry.all().size(), ToolRegistry.sdkTools().size());
        assertTrue(ToolRegistry.sdkTools().get(0).isTool());
    }

    @Test
    void blocklistCatchesNestedCommands() {
        List<String> blocked = List.of("op", "stop", "ban");
        assertEquals("op", WorldTools.blockedBy("op Steve", blocked));
        assertEquals("op", WorldTools.blockedBy("minecraft:op Steve", blocked));
        assertEquals("stop", WorldTools.blockedBy("execute as @a run stop", blocked));
        assertNull(WorldTools.blockedBy("summon zombie ~ ~ ~", blocked));
        assertNotNull(WorldTools.blockedBy("gamerule doMobSpawning false", blocked));
    }

    @Test
    void argsParsingIsForgiving() throws ToolException {
        JsonObject o = new JsonObject();
        o.addProperty("count", 99.6);
        o.addProperty("flag", "true");
        o.addProperty("list", "a, b ,c");
        Args a = new Args(o);
        assertEquals(64, a.integer("count", 1, 1, 64));
        assertTrue(a.bool("flag", false));
        assertEquals(List.of("a", "b", "c"), a.strings("list"));
        assertFalse(a.has("missing"));
    }
}
