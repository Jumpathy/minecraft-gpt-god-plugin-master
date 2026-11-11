package net.bigyous.gptgodmc.god.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

/** Strict tool-call handling for small models: what is mended, what is refused, and how it is explained. */
class ToolCheckTest {
    private static final LlmTool TOOL = OpenAiWireTest.giveItem();

    @Test
    void harmlessSlipsAreMended() {
        assertEquals("{\"a\":1}", ToolCheck.parse("{\"a\":1}").toString());
        assertEquals("{}", ToolCheck.parse("").toString(), "no arguments is an empty object");
        assertEquals("{\"a\":1}", ToolCheck.parse("```json\n{\"a\":1}\n```").toString(), "a code fence");
        assertEquals("{\"a\":1}", ToolCheck.parse("\"{\\\"a\\\":1}\"").toString(), "arguments as a JSON string");
        assertEquals("{\"a\":1,\"b\":[1,2]}", ToolCheck.parse("{\"a\":1,\"b\":[1,2,],}").toString(), "trailing commas");
        assertEquals("{\"a\":1}", ToolCheck.parse("Here you go: {\"a\":1} hope that helps").toString());
    }

    @Test
    void brokenJsonIsNeverGuessed() {
        assertNull(ToolCheck.parse("{\"player\": \"Ashwyn\", \"item\": \"diamond_sw"), "cut off");
        assertNull(ToolCheck.parse("[1,2,3]"), "not an object");
        assertNull(ToolCheck.parse("player=Ashwyn"));
        assertNull(ToolCheck.parse("{player: }"));
    }

    @Test
    void argumentsMustFitTheSchema() {
        JsonObject ok = ToolCheck.parse("{\"player\":\"Ashwyn\",\"count\":\"5\",\"kind\":\"GIFT\",\"extra\":null}");
        assertNull(ToolCheck.problem(TOOL, ok));
        assertEquals(5, ok.get("count").getAsInt(), "\"5\" is read as 5");
        assertTrue(ok.get("count").getAsJsonPrimitive().isNumber());
        assertEquals("gift", ok.get("kind").getAsString(), "enum values are matched without regard to case");
        assertTrue(!ok.has("extra"), "nulls are dropped");

        assertTrue(ToolCheck.problem(TOOL, ToolCheck.parse("{\"count\":2}")).contains("missing required"));
        assertTrue(ToolCheck.problem(TOOL, ToolCheck.parse("{\"player\":\"A\",\"amount\":2}"))
                .contains("unknown argument \"amount\""));
        assertTrue(ToolCheck.problem(TOOL, ToolCheck.parse("{\"player\":\"A\",\"count\":\"many\"}"))
                .contains("must be a number"));
        assertTrue(ToolCheck.problem(TOOL, ToolCheck.parse("{\"player\":\"A\",\"count\":2.5}"))
                .contains("whole number"));
        assertTrue(ToolCheck.problem(TOOL, ToolCheck.parse("{\"player\":\"A\",\"kind\":\"theft\"}"))
                .contains("must be one of"));
        assertTrue(ToolCheck.problem(TOOL, ToolCheck.parse("{\"player\":{\"name\":\"A\"}}")).contains("must be a string"));
    }

    @Test
    void theHintNamesEveryArgument() {
        String hint = ToolCheck.hint(TOOL);
        assertTrue(hint.contains("\"player\": string (required)"), hint);
        assertTrue(hint.contains("\"count\": integer"), hint);
        assertTrue(hint.contains("one of [gift, loan]"), hint);
    }

    @Test
    void callsWrittenAsTextAreFoundOnlyForKnownTools() {
        List<String> names = List.of("whisper", "heal");
        List<ToolCheck.TextCall> tagged = ToolCheck.fromText(
                "Sure.\n<tool_call>{\"name\":\"whisper\",\"arguments\":{\"player\":\"A\",\"message\":\"hi\"}}</tool_call>"
                        + "<tool_call>{\"name\":\"heal\",\"arguments\":\"{\\\"player\\\":\\\"A\\\"}\"}</tool_call>",
                names);
        assertEquals(2, tagged.size());
        assertEquals("whisper", tagged.get(0).name());
        assertNotNull(ToolCheck.parse(tagged.get(1).json()).get("player"));

        assertEquals(1, ToolCheck.fromText("```json\n{\"name\":\"heal\",\"parameters\":{\"player\":\"A\"}}\n```", names)
                .size());
        assertEquals(1, ToolCheck.fromText("{\"function\":{\"name\":\"heal\",\"arguments\":{}}}", names).size());
        assertEquals(2, ToolCheck.fromText("[{\"name\":\"heal\"},{\"name\":\"whisper\",\"arguments\":{}}]", names).size());
        assertTrue(ToolCheck.fromText("{\"name\":\"explode\",\"arguments\":{}}", names).isEmpty(), "unknown tool");
        assertTrue(ToolCheck.fromText("I will whisper to them.", names).isEmpty());
        assertTrue(ToolCheck.fromText("{\"mood\":\"calm\"}", names).isEmpty(), "JSON that is not a call");
    }
}
