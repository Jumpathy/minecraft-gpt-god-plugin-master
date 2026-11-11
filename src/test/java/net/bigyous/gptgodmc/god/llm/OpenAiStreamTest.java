package net.bigyous.gptgodmc.god.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Replays recorded event streams (src/test/resources/llm) through the parser. */
class OpenAiStreamTest {

    @Test
    void openAiStreamsToolCallsInPieces() throws Exception {
        Recorder rec = new Recorder();
        LlmReply reply = Recorder.replay("openai-tool-calls.sse", rec, null);

        assertEquals("The mortal prays. I answer.", rec.prose.toString());
        assertEquals(2, rec.calls.size());
        assertEquals("call_abc", rec.calls.get(0)[0]);
        assertEquals("whisper", rec.calls.get(0)[1]);
        assertEquals("{\"player\":\"Ashwyn\",\"message\":\"I hear you.\"}", rec.calls.get(0)[2]);
        assertEquals("heal", rec.calls.get(1)[1]);
        // Prose closes before the first call, and the first call runs before the second arrives whole.
        assertEquals(List.of("open 0 text", "close 0", "call whisper", "call heal"), rec.log);

        assertTrue(reply.finished);
        assertTrue(reply.replayable);
        assertEquals(LlmReply.Stop.TOOL_USE, reply.stop);
        assertEquals("tool_calls", reply.stopText);
        assertTrue(reply.billed);
        assertEquals(176, reply.in, "uncached input only");
        assertEquals(1024, reply.cacheRead);
        assertEquals(45, reply.out);
        assertEquals(0, reply.cacheWrite);

        JsonObject turn = (JsonObject) reply.turn;
        assertEquals("assistant", turn.get("role").getAsString());
        assertEquals("The mortal prays. I answer.", turn.get("content").getAsString());
        assertEquals(2, turn.getAsJsonArray("tool_calls").size());
        JsonObject whole = JsonParser.parseString(reply.responseJson.get()).getAsJsonObject();
        assertEquals(1200, whole.getAsJsonObject("usage").get("prompt_tokens").getAsLong());
    }

    @Test
    void ollamaSendsWholeCallsAllNumberedZero() throws Exception {
        Recorder rec = new Recorder();
        LlmReply reply = Recorder.replay("ollama-tool-calls.sse", rec, null);

        assertEquals("A prayer. I should answer.", rec.thinking.toString());
        assertEquals("", rec.prose.toString());
        assertEquals(2, rec.calls.size(), "two calls with the same index but different ids are two calls");
        assertEquals("whisper", rec.calls.get(0)[1]);
        assertEquals("give_item", rec.calls.get(1)[1]);
        assertEquals(List.of("open 0 thinking", "close 0", "call whisper", "call give_item"), rec.log);
        assertEquals(LlmReply.Stop.TOOL_USE, reply.stop, "finish_reason stop with calls still means tool use");
        assertEquals(2100, reply.in);
        assertEquals(60, reply.out);
        // Thinking is not replayed to the model.
        assertTrue(((JsonObject) reply.turn).get("content").isJsonNull());
    }

    @Test
    void cutOffToolJsonIsHandedOverAsItIsAndReplayedEmpty() throws Exception {
        Recorder rec = new Recorder();
        LlmReply reply = Recorder.replay("malformed-tool-json.sse", rec, null);

        assertEquals(1, rec.calls.size());
        String json = rec.calls.get(0)[2];
        assertEquals("{\"player\": \"Ashwyn\", \"item\": \"diamond_sw", json);
        assertNull(ToolCheck.parse(json), "a cut-off call is never completed by guessing");
        assertTrue(reply.finished);
        assertEquals(LlmReply.Stop.TOOL_USE, reply.stop);
        assertEquals("length", reply.stopText);
        assertFalse(reply.billed, "no usage was reported");
        // Servers reject an assistant turn whose arguments are not JSON, so it goes back as {}.
        JsonObject fn = ((JsonObject) reply.turn).getAsJsonArray("tool_calls").get(0).getAsJsonObject()
                .getAsJsonObject("function");
        assertEquals("{}", fn.get("arguments").getAsString());
    }

    @Test
    void inlineThinkingAndACallWrittenAsText() throws Exception {
        Recorder rec = new Recorder();
        LlmReply reply = Recorder.replay("inline-think-text-call.sse", rec, List.of("announce", "whisper"));

        assertEquals("They greeted me.", rec.thinking.toString());
        assertEquals(1, rec.calls.size());
        assertEquals("announce", rec.calls.get(0)[1]);
        assertEquals("{\"message\":\"Welcome, mortals.\"}", rec.calls.get(0)[2]);
        assertTrue(reply.finished, "a finish reason is enough; [DONE] is optional");
        assertEquals(LlmReply.Stop.TOOL_USE, reply.stop);
        JsonObject turn = (JsonObject) reply.turn;
        assertTrue(turn.get("content").isJsonNull(), "the written-out call goes back as a real call");
        assertEquals("announce", turn.getAsJsonArray("tool_calls").get(0).getAsJsonObject()
                .getAsJsonObject("function").get("name").getAsString());
    }

    @Test
    void textCallsAreIgnoredOutsideTheLiteProfile() throws Exception {
        Recorder rec = new Recorder();
        LlmReply reply = Recorder.replay("inline-think-text-call.sse", rec, null);
        assertTrue(rec.calls.isEmpty());
        assertEquals(LlmReply.Stop.END_TURN, reply.stop);
        assertTrue(rec.prose.toString().contains("<tool_call>"));
    }

    @Test
    void aStreamThatJustStopsIsNotFinished() {
        Recorder rec = new Recorder();
        OpenAiStream s = new OpenAiStream(rec, null);
        s.line("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"I was sayi\"}}]}");
        s.end();
        LlmReply reply = s.reply();
        assertFalse(reply.finished);
        assertFalse(reply.replayable);
        assertNull(reply.responseJson);
        assertEquals("I was sayi", rec.prose.toString());
        assertEquals(List.of("open 0 text", "close 0"), rec.log);
    }

    @Test
    void anErrorInTheStreamIsThrown() {
        OpenAiStream s = new OpenAiStream(new Recorder(), null);
        OpenAiStream.ServerError e = assertThrows(OpenAiStream.ServerError.class,
                () -> s.line("data: {\"error\":{\"message\":\"model not loaded\",\"type\":\"server_error\"}}"));
        assertTrue(e.getMessage().contains("model not loaded"));
    }

    @Test
    void aWholeCompletionIsReadLikeOneChunk() {
        Recorder rec = new Recorder();
        OpenAiStream s = new OpenAiStream(rec, null);
        s.chunk(JsonParser.parseString("""
                {"choices":[{"index":0,"message":{"role":"assistant","content":"<think>hm</think>Xal'thor",
                "reasoning_content":null},"finish_reason":"stop"}],
                "usage":{"prompt_tokens":10,"completion_tokens":3}}""").getAsJsonObject());
        s.end();
        LlmReply reply = s.reply();
        assertEquals("Xal'thor", rec.prose.toString());
        assertEquals("hm", rec.thinking.toString());
        assertEquals(LlmReply.Stop.END_TURN, reply.stop);
        assertNotNull(reply.responseJson);
        assertEquals(10, reply.in);
    }

    @Test
    void contentFilterIsARefusal() {
        OpenAiStream s = new OpenAiStream(new Recorder(), null);
        s.line("data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"content_filter\"}]}");
        s.end();
        assertEquals(LlmReply.Stop.REFUSAL, s.reply().stop);
    }
}
