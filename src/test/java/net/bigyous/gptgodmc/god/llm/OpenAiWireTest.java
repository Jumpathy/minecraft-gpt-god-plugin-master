package net.bigyous.gptgodmc.god.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.god.tools.ToolResult;

/** Checks the request an OpenAI-compatible server is sent: tools, parameters, turns, results, images. */
class OpenAiWireTest {

    static OpenAiConfig config(boolean official, boolean vision) {
        return new OpenAiConfig(official, official ? "" : "http://localhost:11434/v1", "", 900, 60, vision, -1, "",
                false, 0);
    }

    static LlmTool giveItem() {
        Map<String, Map<String, Object>> props = new LinkedHashMap<>();
        props.put("player", Map.of("type", "string", "description", "Who receives it"));
        Map<String, Object> count = new LinkedHashMap<>();
        count.put("type", "integer");
        count.put("minimum", 1);
        count.put("maximum", 64);
        count.put("description", "How many");
        props.put("count", count);
        Map<String, Object> kind = new LinkedHashMap<>();
        kind.put("type", "string");
        kind.put("enum", List.of("gift", "loan"));
        kind.put("description", "How it is given");
        props.put("kind", kind);
        return new LlmTool("give_item", "Gives a mortal an item.", props, List.of("player"));
    }

    @Test
    void toolsBecomeFunctionsWithTheirSchema() {
        JsonObject t = OpenAiWire.tool(giveItem());
        assertEquals("function", t.get("type").getAsString());
        JsonObject fn = t.getAsJsonObject("function");
        assertEquals("give_item", fn.get("name").getAsString());
        assertEquals("Gives a mortal an item.", fn.get("description").getAsString());
        JsonObject params = fn.getAsJsonObject("parameters");
        assertEquals("object", params.get("type").getAsString());
        assertEquals("integer", params.getAsJsonObject("properties").getAsJsonObject("count").get("type").getAsString());
        assertEquals(64, params.getAsJsonObject("properties").getAsJsonObject("count").get("maximum").getAsInt());
        assertEquals("loan", params.getAsJsonObject("properties").getAsJsonObject("kind").getAsJsonArray("enum")
                .get(1).getAsString());
        assertEquals("player", params.getAsJsonArray("required").get(0).getAsString());
    }

    @Test
    void bodyUsesEachServersParameterNames() {
        JsonArray messages = new JsonArray();
        messages.add(OpenAiWire.message("user", "hello"));
        JsonObject local = OpenAiWire.body(config(false, false), "qwen3:8b", messages,
                OpenAiWire.tools(List.of(giveItem())), true);
        assertEquals("qwen3:8b", local.get("model").getAsString());
        assertEquals(900, local.get("max_tokens").getAsLong());
        assertFalse(local.has("max_completion_tokens"));
        assertTrue(local.get("stream").getAsBoolean());
        assertTrue(local.getAsJsonObject("stream_options").get("include_usage").getAsBoolean());
        assertEquals(1, local.getAsJsonArray("tools").size());
        // Nothing an OpenAI-compatible server lacks is asked for.
        for (String absent : List.of("temperature", "reasoning_effort", "tool_choice", "thinking", "cache_control",
                "output_config", "fallbacks", "system")) {
            assertFalse(local.has(absent), absent);
        }

        JsonObject openai = OpenAiWire.body(new OpenAiConfig(true, "", "sk-test", 900, 60, true, 0.5, "low", false,
                0), "gpt-5-mini", messages, new JsonArray(), false);
        assertEquals(900, openai.get("max_completion_tokens").getAsLong());
        assertFalse(openai.has("max_tokens"));
        assertEquals(0.5, openai.get("temperature").getAsDouble());
        assertEquals("low", openai.get("reasoning_effort").getAsString());
        assertFalse(openai.has("tools"), "an empty tool list is left out");
        assertFalse(openai.has("stream"));
        assertFalse(openai.toString().contains("sk-test"), "the key travels in a header, never the body");
    }

    @Test
    void endpointsAreBuiltFromTheBaseUrl() {
        assertEquals("https://api.openai.com/v1/chat/completions", config(true, true).endpoint());
        assertEquals("http://localhost:11434/v1/chat/completions", config(false, false).endpoint());
        assertEquals("http://localhost:1234/v1/chat/completions", new OpenAiConfig(false, "http://localhost:1234",
                "", 1, 1, false, -1, "", false, 0).endpoint(), "a bare host gets /v1");
        assertEquals("https://openrouter.ai/api/v1/chat/completions", new OpenAiConfig(false,
                "https://openrouter.ai/api/v1/", "", 1, 1, false, -1, "", false, 0).endpoint());
        assertNull(new OpenAiConfig(false, "", "", 1, 1, false, -1, "", false, 0).endpoint());
    }

    @Test
    void localAddressesAreRecognised() {
        for (String local : List.of("http://localhost:11434/v1", "http://127.0.0.1:1234/v1", "http://192.168.1.20:8000",
                "http://10.0.0.5:8080/v1", "http://172.20.0.2:8000/v1", "http://gamingpc:1234/v1",
                "http://[::1]:8080/v1", "http://host.docker.internal:11434/v1")) {
            assertTrue(OpenAiConfig.isLocal(local), local);
        }
        for (String remote : List.of("https://api.openai.com/v1", "https://openrouter.ai/api/v1",
                "http://172.50.0.2:8000/v1", "", "not a url")) {
            assertFalse(OpenAiConfig.isLocal(remote), remote);
        }
    }

    @Test
    void assistantTurnsAreReplayedWithValidArguments() {
        JsonObject turn = OpenAiWire.assistant("I act.", List.of(
                new OpenAiWire.Call("c1", "whisper", "{\"player\":\"A\",\"message\":\"hi\"}"),
                new OpenAiWire.Call("c2", "heal", "{\"player\": \"A\""),
                new OpenAiWire.Call("c3", "announce", "")));
        assertEquals("I act.", turn.get("content").getAsString());
        JsonArray calls = turn.getAsJsonArray("tool_calls");
        assertEquals("c1", calls.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("function", calls.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("{\"player\":\"A\",\"message\":\"hi\"}", calls.get(0).getAsJsonObject().getAsJsonObject("function")
                .get("arguments").getAsString());
        assertEquals("{}", calls.get(1).getAsJsonObject().getAsJsonObject("function").get("arguments").getAsString());
        assertEquals("{}", calls.get(2).getAsJsonObject().getAsJsonObject("function").get("arguments").getAsString());

        JsonObject silent = OpenAiWire.assistant("  ", List.of());
        assertFalse(silent.has("tool_calls"));
        assertFalse(silent.get("content").getAsString().isBlank(), "an empty turn breaks some chat templates");
    }

    @Test
    void resultsBecomeToolMessagesAndErrorsAreMarked() {
        JsonArray messages = new JsonArray();
        OpenAiWire.results(messages, List.of(
                new ToolAnswer("c1", "whisper", ToolResult.ok("Ashwyn heard you.")),
                new ToolAnswer("c2", "heal", ToolResult.error("No such mortal.")),
                new ToolAnswer("c3", "announce", ToolResult.ok(""))), "[A mortal spoke.]", false);
        assertEquals(4, messages.size());
        JsonObject first = messages.get(0).getAsJsonObject();
        assertEquals("tool", first.get("role").getAsString());
        assertEquals("c1", first.get("tool_call_id").getAsString());
        assertEquals("Ashwyn heard you.", first.get("content").getAsString());
        assertEquals("ERROR: No such mortal.", messages.get(1).getAsJsonObject().get("content").getAsString());
        assertEquals("done", messages.get(2).getAsJsonObject().get("content").getAsString());
        JsonObject heard = messages.get(3).getAsJsonObject();
        assertEquals("user", heard.get("role").getAsString());
        assertEquals("[A mortal spoke.]", heard.get("content").getAsString());
    }

    @Test
    void imagesGoAsImageUrlOnlyToModelsThatSee() {
        byte[] png = { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3 };
        List<ToolAnswer> answers = List.of(new ToolAnswer("c1", "look", ToolResult.image("A valley at dusk.", png)));

        JsonArray seeing = new JsonArray();
        OpenAiWire.results(seeing, answers, null, true);
        assertEquals(2, seeing.size());
        assertEquals("A valley at dusk.", seeing.get(0).getAsJsonObject().get("content").getAsString());
        JsonArray parts = seeing.get(1).getAsJsonObject().getAsJsonArray("content");
        assertEquals("user", seeing.get(1).getAsJsonObject().get("role").getAsString());
        assertEquals("text", parts.get(0).getAsJsonObject().get("type").getAsString());
        assertTrue(parts.get(0).getAsJsonObject().get("text").getAsString().contains("look"));
        assertEquals("image_url", parts.get(1).getAsJsonObject().get("type").getAsString());
        assertTrue(parts.get(1).getAsJsonObject().getAsJsonObject("image_url").get("url").getAsString()
                .startsWith("data:image/png;base64,iVBORwEC"));

        JsonArray blind = new JsonArray();
        OpenAiWire.results(blind, answers, null, false);
        assertEquals(1, blind.size(), "no image message for a model that cannot see");
        String text = blind.get(0).getAsJsonObject().get("content").getAsString();
        assertTrue(text.startsWith("A valley at dusk."));
        assertTrue(text.contains("cannot see images"));
        assertFalse(blind.toString().contains("base64"));
    }
}
