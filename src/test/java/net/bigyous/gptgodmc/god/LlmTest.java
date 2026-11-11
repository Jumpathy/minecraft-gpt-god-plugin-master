package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.MessageParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.god.llm.LlmChat;
import net.bigyous.gptgodmc.god.llm.LlmTool;
import net.bigyous.gptgodmc.god.llm.OpenAiWire;
import net.bigyous.gptgodmc.god.llm.ToolAnswer;
import net.bigyous.gptgodmc.god.tools.ToolRegistry;
import net.bigyous.gptgodmc.god.tools.ToolResult;
import net.bigyous.gptgodmc.utils.TokenTracker;

/** Provider choice, the lite profile, and proof that the Anthropic path builds the same request as before. */
class LlmTest {

    @Test
    void anthropicIsTheDefaultProvider() {
        assertEquals(Llm.Provider.ANTHROPIC, Llm.provider());
        assertTrue(Llm.anthropic());
        assertFalse(Llm.lite());
        assertEquals("Claude", Llm.label());
        assertEquals(GodSettings.DEFAULT_MODEL, GodSettings.model());
        assertEquals(GodSettings.DEFAULT_MEMORY_MODEL, GodSettings.memoryModel());
        assertEquals(6, GodSettings.maxSteps());
        assertEquals(24000, GodSettings.perceptionCharBudget());
        String perception = "# Perception\n" + "x".repeat(50_000);
        assertEquals(perception, Llm.fitPerception(perception, "system"), "Claude's perception is never cut here");
    }

    @Test
    void providerNamesAreRead() {
        assertEquals(Llm.Provider.ANTHROPIC, Llm.provider(""));
        assertEquals(Llm.Provider.ANTHROPIC, Llm.provider("anthropic"));
        assertEquals(Llm.Provider.OPENAI, Llm.provider("OpenAI"));
        assertEquals(Llm.Provider.COMPATIBLE, Llm.provider("openai-compatible"));
        assertEquals(Llm.Provider.COMPATIBLE, Llm.provider("openai_compatible"));
        assertEquals(Llm.Provider.COMPATIBLE, Llm.provider("ollama"));
        assertEquals(Llm.Provider.COMPATIBLE, Llm.provider("lmstudio"));
    }

    @Test
    void liteIsChosenForLocalServers() {
        assertTrue(Llm.lite("auto", Llm.Provider.COMPATIBLE, "http://localhost:11434/v1"));
        assertTrue(Llm.lite("", Llm.Provider.COMPATIBLE, "http://192.168.1.5:1234/v1"));
        assertFalse(Llm.lite("auto", Llm.Provider.COMPATIBLE, "https://openrouter.ai/api/v1"));
        assertFalse(Llm.lite("auto", Llm.Provider.OPENAI, ""));
        assertFalse(Llm.lite("auto", Llm.Provider.ANTHROPIC, ""));
        assertFalse(Llm.lite("full", Llm.Provider.COMPATIBLE, "http://localhost:11434/v1"));
        assertTrue(Llm.lite("lite", Llm.Provider.ANTHROPIC, ""));
    }

    @Test
    void theLiteToolsExistAndStayInRegistryOrder() {
        List<String> all = ToolRegistry.all().stream().map(t -> t.name()).toList();
        for (String name : Llm.LITE_TOOLS) assertTrue(all.contains(name), name + " is a real power");
        assertTrue(Llm.LITE_TOOLS.size() <= 16, "small models cope with few tools");
        // The full list for other providers is every power, in the registry's order, with its schema.
        List<LlmTool> specs = Llm.tools();
        assertEquals(all, specs.stream().map(LlmTool::name).toList());
        JsonObject whisper = OpenAiWire.tool(specs.stream().filter(t -> t.name().equals("whisper")).findFirst()
                .orElseThrow());
        JsonObject params = whisper.getAsJsonObject("function").getAsJsonObject("parameters");
        assertEquals("object", params.get("type").getAsString());
        assertFalse(params.getAsJsonObject("properties").entrySet().isEmpty());
        params.getAsJsonObject("properties").entrySet().forEach(e -> assertTrue(
                e.getValue().getAsJsonObject().has("description"), e.getKey() + " has a description"));
        assertEquals(Llm.tools().toString(), Llm.tools().toString(), "the tool list is stable between thoughts");
    }

    @Test
    void longDescriptionsAreCutAtASentence() {
        assertEquals("Short.", Llm.brief("Short."));
        String longOne = "Speaks privately to one mortal, in your own voice. " + "Only they hear it. ".repeat(20);
        String brief = Llm.brief(longOne);
        assertTrue(brief.length() <= 240, brief);
        assertTrue(brief.startsWith("Speaks privately to one mortal"));
        assertTrue(brief.endsWith("."), brief);
    }

    @Test
    void aGodsOwnModelCountsOnlyForItsProvider() {
        // Provider here is anthropic (the default).
        assertTrue(Llm.fits("claude-opus-5-5"));
        assertFalse(Llm.fits("gpt-5-mini"), "left over from another provider: ignored");
        assertFalse(Llm.fits(" "));
    }

    @Test
    void theLitePromptIsShortAndStillTheSameGod() throws Exception {
        Persona p = PromptsTest.thyvoru();
        String lite = LitePrompt.system(p);
        String full = Prompts.system(p);
        Files.createDirectories(Path.of("build/test-renders"));
        Files.writeString(Path.of("build/test-renders/system-prompt-lite-sample.txt"), lite, StandardCharsets.UTF_8);
        assertTrue(lite.startsWith("You are Thyvoru, the Easygoing Canopy, the god of this Minecraft world."));
        assertTrue(lite.contains(p.nature));
        assertTrue(lite.contains("BINDING COMMANDS"));
        assertTrue(lite.contains("command_done"));
        assertFalse(lite.contains("%s"));
        assertTrue(lite.length() < full.length() / 3, lite.length() + " vs " + full.length());
        assertEquals(lite, LitePrompt.system(p), "stable, so servers can cache the prefix");
        // It names no power the lite profile lacks.
        for (String name : List.of("observe_player", "survey_area", "web_search", "particles")) {
            assertFalse(lite.contains(name + " ") || lite.contains(name + ","), name);
        }
    }

    @Test
    void unfitCallsAreExplainedToTheModel() {
        assertNull(GodBrain.unfit("whisper", JsonParser.parseString(
                "{\"player\":\"Ashwyn\",\"message\":\"Rise.\"}").getAsJsonObject()));
        String missing = GodBrain.unfit("whisper", new JsonObject());
        assertNotNull(missing);
        assertTrue(missing.startsWith("Invalid input for whisper: missing required argument"), missing);
        assertTrue(missing.contains("It takes {"), missing);
        String unknown = GodBrain.unfit("summon_dragon", new JsonObject());
        assertTrue(unknown.startsWith("You have no power called 'summon_dragon'."), unknown);
        assertTrue(unknown.contains("whisper"));
    }

    // ---- Anthropic stays exactly as it was -----------------------------------------------------------

    @Test
    void theAnthropicChatSendsWhatTheBrainAlwaysSent() throws Exception {
        AnthropicChat chat = new AnthropicChat(null, "claude-sonnet-5-5", "You are a test god.");
        chat.user("Perception: nothing happened.");
        LlmChat.Request request = chat.prepare("high");

        String direct = ObjectMappers.jsonMapper().writeValueAsString(GodBrain.params("claude-sonnet-5-5",
                "You are a test god.", List.of(MessageParam.ofUser("Perception: nothing happened.")), "high")
                ._body());
        assertEquals(direct, request.bodyJson(), "the provider layer adds and removes nothing");

        JsonNode body = ObjectMappers.jsonMapper().readTree(request.bodyJson());
        assertEquals("adaptive", body.at("/thinking/type").asText());
        assertEquals("ephemeral", body.at("/cache_control/type").asText());
        assertEquals("ephemeral", body.at("/system/0/cache_control/type").asText());
        assertEquals("default", body.get("fallbacks").asText());
        assertEquals(List.of("server-side-fallback-2026-07-01"), request.betas());
        assertEquals("You are a test god.", request.system());
        assertTrue(request.chronicle().contains("chronicle"));
        assertTrue(request.toolsJson().contains("\"whisper\""));
        assertEquals(ToolRegistry.all().size() + 1, body.get("tools").size(), "every power plus web search");
    }

    @Test
    void anthropicToolResultsKeepTheirShape() throws Exception {
        AnthropicChat chat = new AnthropicChat(null, "claude-haiku-5-5", "x");
        chat.user("first");
        byte[] png = { 1, 2, 3 };
        chat.toolResults(List.of(
                new ToolAnswer("toolu_1", "whisper", ToolResult.ok("Heard.")),
                new ToolAnswer("toolu_2", "heal", ToolResult.error("No such mortal.")),
                new ToolAnswer("toolu_3", "look", ToolResult.image("A valley.", png))), "[Mortals addressed you.]");
        JsonNode body = ObjectMappers.jsonMapper().readTree(chat.prepare("low").bodyJson());
        JsonNode content = body.at("/messages/1/content");
        assertEquals(4, content.size());
        assertEquals("tool_result", content.get(0).get("type").asText());
        assertEquals("toolu_1", content.get(0).get("tool_use_id").asText());
        assertEquals("Heard.", content.get(0).get("content").asText());
        assertTrue(content.get(1).get("is_error").asBoolean());
        assertEquals("image", content.get(2).at("/content/1/type").asText());
        assertEquals("image/png", content.get(2).at("/content/1/source/media_type").asText());
        assertEquals("text", content.get(3).get("type").asText());
        assertEquals("[Mortals addressed you.]", content.get(3).get("text").asText());
        assertEquals("low", body.at("/output_config/effort").asText());
        assertNull(body.get("fallbacks"), "Haiku has no refusal fallback");
    }

    // ---- Usage -----------------------------------------------------------------------------------------

    @Test
    void costIsShownOnlyWhereAPriceIsKnown() {
        TokenTracker.reset();
        try {
            assertEquals(-1, TokenTracker.costOf("qwen3:8b", 1000, 100, 0, 0));
            String local = UsageLog.line("main", "qwen3:8b", "thought #1 step 1", 2100, 60, 0, 0, -1, 0);
            assertTrue(local.contains("in 2,100"), local);
            assertTrue(local.contains("out 60"), local);
            assertFalse(local.contains("$"), "no price, no cost: " + local);
            assertFalse(local.contains("price unknown"), local);

            String claude = UsageLog.line("main", "claude-sonnet-5-5", "thought #1 step 1", 1000, 100, 0, 0, 0.003,
                    0.003);
            assertTrue(claude.contains("$0.0030"), claude);
            assertTrue(claude.contains("session $0.0030"), claude);

            TokenTracker.setFallbackPrice(model -> model.equals("gpt-test") ? new double[] { 1, 2, 0.5, 1 } : null);
            assertEquals(0.0012, TokenTracker.costOf("gpt-test", 1000, 100, 0, 0), 1e-9);
            assertEquals(-1, TokenTracker.costOf("other", 1000, 100, 0, 0));
            assertEquals(0.0022, TokenTracker.costOf("claude-sonnet-5-5", 100, 200, 0, 0), 1e-9,
                    "the built-in table is not overridden");
        } finally {
            TokenTracker.setFallbackPrice(null);
            TokenTracker.reset();
        }
    }
}
