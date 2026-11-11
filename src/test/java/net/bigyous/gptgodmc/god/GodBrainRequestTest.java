package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.fasterxml.jackson.databind.JsonNode;

/** Checks the exact request the god sends to Claude. */
class GodBrainRequestTest {

    @Test
    void requestUsesCachingThinkingEffortAndTools() throws Exception {
        MessageCreateParams params = GodBrain.params("claude-sonnet-5-5", "You are a test god.",
                List.of(MessageParam.ofUser("Perception: nothing happened.")));
        JsonNode body = ObjectMappers.jsonMapper().valueToTree(params._body());

        assertEquals("claude-sonnet-5-5", body.get("model").asText());
        assertEquals("adaptive", body.at("/thinking/type").asText());
        assertEquals("summarized", body.at("/thinking/display").asText());
        assertEquals("high", body.at("/output_config/effort").asText());
        assertEquals("ephemeral", body.at("/cache_control/type").asText(), "top-level auto caching");
        assertEquals("ephemeral", body.at("/system/0/cache_control/type").asText(), "static rules are cached");
        assertTrue(body.at("/system/1/text").asText().contains("chronicle"));
        assertTrue(body.get("tools").size() >= 30);
        assertTrue(body.get("tool_choice") == null || body.get("tool_choice").isNull(),
                "Sonnet 5.5 rejects forced tool choice");
        assertEquals("default", body.get("fallbacks").asText(), "refusal fallback is on for Sonnet 5.5");
        assertEquals("server-side-fallback-2026-07-01",
                params._headers().values("anthropic-beta").get(0));
    }

    @Test
    void noFallbackForModelsThatDoNotSupportIt() throws Exception {
        MessageCreateParams params = GodBrain.params("claude-haiku-5-5", "x", List.of(MessageParam.ofUser("y")));
        JsonNode body = ObjectMappers.jsonMapper().valueToTree(params._body());
        assertTrue(body.get("fallbacks") == null);
    }

    @Test
    void systemPromptIsCharacterNeutral() {
        Persona p = new Persona();
        p.name = "Dez";
        p.epithet = "the Unbothered";
        p.archetype = "chill";
        p.nature = "A laid-back god.";
        p.speech = "Casual.";
        p.likes = List.of("growing plants");
        p.dislikes = List.of("using hostile language");
        p.dials.put("humor", 9);
        p.dials.put("wrath", 1);
        String prompt = Prompts.system(p);
        assertTrue(prompt.startsWith("You are Dez, the Unbothered"));
        assertTrue(prompt.contains("funny and playful"));
        assertTrue(prompt.contains("slow to anger"));
        assertTrue(prompt.contains("Operator directive"));
        assertTrue(!prompt.contains("%s"));
    }
}
