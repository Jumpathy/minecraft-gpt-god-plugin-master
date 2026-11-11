package net.bigyous.gptgodmc.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.llm.LlmChat;
import net.bigyous.gptgodmc.god.llm.LlmReply;
import net.bigyous.gptgodmc.god.llm.LlmTool;
import net.bigyous.gptgodmc.god.llm.OpenAiChat;
import net.bigyous.gptgodmc.god.llm.OpenAiConfig;
import net.bigyous.gptgodmc.god.llm.OpenAiStream;
import net.bigyous.gptgodmc.god.llm.ToolAnswer;
import net.bigyous.gptgodmc.god.tools.ToolResult;

/** The mind viewer records requests and replies of an OpenAI-compatible provider like Claude's. */
class MindTraceOpenAiTest {
    @BeforeEach
    void on() {
        MindTrace.clear();
        MindTrace.setEnabled(true);
    }

    @AfterEach
    void off() {
        MindTrace.setEnabled(false);
        MindTrace.clear();
    }

    @Test
    void anOpenAiRequestAndReplyAreRecorded() {
        OpenAiConfig cfg = new OpenAiConfig(false, "http://localhost:11434/v1", "", 800, 60, true, -1, "", false, 0);
        LlmTool tool = new LlmTool("whisper", "Speak to one mortal.",
                Map.of("player", Map.of("type", "string", "description", "Who")), List.of("player"));
        OpenAiChat chat = new OpenAiChat(cfg, "qwen3:8b", "RULES", "# Your chronicle\nold days", List.of(tool));
        chat.user("Perception: Ashwyn prays.");

        // A reply with a tool call, then its result with an image, as the brain would add them.
        OpenAiStream stream = new OpenAiStream(new LlmChat.Listener() {
            @Override
            public void token() {
            }

            @Override
            public void open(long block, boolean thinking) {
            }

            @Override
            public void delta(long block, String text) {
            }

            @Override
            public void close(long block) {
            }

            @Override
            public void call(String id, String name, String json) {
            }

            @Override
            public void search(String query) {
            }
        }, null);
        stream.line("data: {\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":\"She prays.\",\"tool_calls\":"
                + "[{\"index\":0,\"id\":\"c1\",\"function\":{\"name\":\"whisper\",\"arguments\":\"{\\\"player\\\":"
                + "\\\"Ashwyn\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}],\"usage\":{\"prompt_tokens\":900,"
                + "\"completion_tokens\":30}}");
        stream.end();
        LlmReply reply = stream.reply();
        chat.assistant(reply);
        chat.toolResults(List.of(new ToolAnswer("c1", "whisper", ToolResult.image("Seen.", new byte[4096]))), null);
        LlmChat.Request request = chat.prepare("high");

        MindTrace.Thought t = MindTrace.begin("main", 1, "prayer", "qwen3:8b", "high", "a prayer", "seen");
        t.prompts(request.system(), request.chronicle(), request.toolsJson());
        MindTrace.Request r = t.request(1, "qwen3:8b", "high");
        t.requestBody(r, request.bodyJson(), request.betas());
        t.responseBody(r, reply.responseJson.get());
        t.usage(r, reply.in, reply.out, reply.cacheRead, reply.cacheWrite, 0, -1, reply.stopText, true, null);

        assertEquals(1, r.toolCount);
        assertEquals(5, r.messageCount, "system, user, assistant, tool, and the image message");
        assertTrue(MindTrace.blobText(r.params).contains("\"model\":\"qwen3:8b\""));
        assertTrue(MindTrace.blobText(r.params).contains("\"max_tokens\":800"));
        assertFalse(MindTrace.blobText(r.params).contains("anthropic-beta"));
        assertTrue(MindTrace.blobText(r.tools).contains("whisper"));
        String msgs = MindTrace.blobText(r.messages);
        assertTrue(msgs.contains("RULES"), "the system prompt travels as the first message");
        assertTrue(msgs.contains("tool_call_id"));
        assertTrue(msgs.length() < 3000, "the image is shortened, not stored whole: " + msgs.length());
        String response = MindTrace.blobText(r.response);
        assertTrue(response.contains("She prays."), "reasoning is kept for the viewer");
        assertTrue(response.contains("\"finish_reason\":\"tool_calls\""));
        assertEquals("RULES", MindTrace.blobText(t.system));
        assertEquals(900, r.input);
        assertEquals("tool_calls", r.stop);
        assertEquals(-1, r.cost, "no price, no cost");
    }
}
