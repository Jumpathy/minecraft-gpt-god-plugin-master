package net.bigyous.gptgodmc.god.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import net.bigyous.gptgodmc.god.tools.ToolResult;

/**
 * A whole tool round trip against a fake OpenAI-compatible server on localhost: the request the server
 * receives, a streamed tool call carried out mid-stream, the results sent back, and the final answer.
 */
class OpenAiChatServerTest {
    /** What the fake server answers with: status, content type, body. */
    private record Answer(int status, String type, String body) {
    }

    private HttpServer server;
    private final List<JsonObject> received = new ArrayList<>();
    private final List<String> auth = new ArrayList<>();
    private final ConcurrentLinkedQueue<Function<JsonObject, Answer>> script = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            JsonObject body = JsonParser.parseString(new String(ex.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            synchronized (received) {
                received.add(body);
                auth.add(String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
            }
            Function<JsonObject, Answer> next = script.poll();
            Answer a = next == null ? new Answer(500, "text/plain", "unexpected request") : next.apply(body);
            byte[] bytes = a.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", a.type());
            ex.sendResponseHeaders(a.status(), 0);
            try (OutputStream out = ex.getResponseBody()) {
                // In pieces, so the client really reads a stream.
                for (int i = 0; i < bytes.length; i += 97) {
                    out.write(bytes, i, Math.min(97, bytes.length - i));
                    out.flush();
                }
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private OpenAiConfig config(String key, int retries) {
        return new OpenAiConfig(false, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", key, 500, 20,
                false, -1, "", false, retries);
    }

    private static Answer sse(String... chunks) {
        StringBuilder sb = new StringBuilder();
        for (String c : chunks) sb.append("data: ").append(c).append("\n\n");
        sb.append("data: [DONE]\n\n");
        return new Answer(200, "text/event-stream", sb.toString());
    }

    @Test
    void aFullToolRoundTrip() {
        script.add(body -> sse(
                "{\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"She is hurt.\"}}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":"
                        + "\"function\",\"function\":{\"name\":\"heal\",\"arguments\":\"{\\\"player\\\":\"}}]}}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":"
                        + "\"\\\"Ashwyn\\\"}\"}}]}}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}]}",
                "{\"choices\":[],\"usage\":{\"prompt_tokens\":300,\"completion_tokens\":20}}"));
        script.add(body -> sse(
                "{\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"It is done.\"}}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}",
                "{\"choices\":[],\"usage\":{\"prompt_tokens\":340,\"completion_tokens\":5}}"));

        OpenAiChat chat = new OpenAiChat(config("local-test-key", 0), "test-model", "You are a test god.",
                "# Your chronicle\n(empty)", List.of(OpenAiWireTest.giveItem()));
        chat.user("Perception: Ashwyn prays for healing.");

        // ---- First request: the model calls a tool; it is carried out while the reply streams.
        List<ToolAnswer> results = new ArrayList<>();
        Recorder rec = new Recorder() {
            @Override
            public void call(String id, String name, String json) {
                super.call(id, name, json);
                JsonObject input = ToolCheck.parse(json);
                results.add(new ToolAnswer(id, name, ToolResult.ok("Healed " + input.get("player").getAsString())));
            }
        };
        LlmChat.Request first = chat.prepare("high");
        LlmReply reply = chat.send(first, rec);

        assertNull(reply.error);
        assertTrue(reply.finished);
        assertEquals(LlmReply.Stop.TOOL_USE, reply.stop);
        assertEquals("She is hurt.", rec.prose.toString());
        assertEquals(1, results.size());
        assertEquals("Healed Ashwyn", results.get(0).result().text());
        assertEquals(300, reply.in);
        assertEquals(20, reply.out);

        JsonObject sent = received.get(0);
        assertEquals(first.bodyJson(), sent.toString(), "what is traced is exactly what is sent");
        assertEquals("Bearer local-test-key", auth.get(0));
        assertFalse(sent.toString().contains("local-test-key"));
        assertEquals("test-model", sent.get("model").getAsString());
        assertTrue(sent.get("stream").getAsBoolean());
        JsonArray msgs = sent.getAsJsonArray("messages");
        assertEquals(2, msgs.size());
        assertEquals("system", msgs.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("You are a test god.\n\n# Your chronicle\n(empty)",
                msgs.get(0).getAsJsonObject().get("content").getAsString(), "one system message, chronicle after");
        assertEquals("give_item", sent.getAsJsonArray("tools").get(0).getAsJsonObject().getAsJsonObject("function")
                .get("name").getAsString());
        assertEquals("You are a test god.", first.system());
        assertTrue(first.toolsJson().contains("give_item"));
        assertTrue(first.betas().isEmpty());

        // ---- Second request: the turn and its result go back; the model finishes.
        chat.assistant(reply);
        chat.toolResults(results, "[While you were acting, mortals addressed you.]\nPrayer: thank you");
        Recorder rec2 = new Recorder();
        LlmReply done = chat.send(chat.prepare("high"), rec2);

        assertNull(done.error);
        assertEquals(LlmReply.Stop.END_TURN, done.stop);
        assertEquals("It is done.", rec2.prose.toString());
        JsonArray msgs2 = received.get(1).getAsJsonArray("messages");
        assertEquals(5, msgs2.size());
        JsonObject turn = msgs2.get(2).getAsJsonObject();
        assertEquals("assistant", turn.get("role").getAsString());
        assertEquals("She is hurt.", turn.get("content").getAsString());
        JsonObject call = turn.getAsJsonArray("tool_calls").get(0).getAsJsonObject();
        assertEquals("call_1", call.get("id").getAsString());
        assertEquals("{\"player\":\"Ashwyn\"}", call.getAsJsonObject("function").get("arguments").getAsString());
        JsonObject result = msgs2.get(3).getAsJsonObject();
        assertEquals("tool", result.get("role").getAsString());
        assertEquals("call_1", result.get("tool_call_id").getAsString());
        assertEquals("Healed Ashwyn", result.get("content").getAsString());
        assertEquals("user", msgs2.get(4).getAsJsonObject().get("role").getAsString());
        assertTrue(msgs2.get(4).getAsJsonObject().get("content").getAsString().contains("Prayer: thank you"));
        // The first messages are unchanged: the conversation is append-only (servers cache the prefix).
        assertEquals(msgs.get(0), msgs2.get(0));
        assertEquals(msgs.get(1), msgs2.get(1));
    }

    @Test
    void aRejectedKeyIsAServiceError() {
        script.add(body -> new Answer(401, "application/json",
                "{\"error\":{\"message\":\"Incorrect API key provided\",\"type\":\"invalid_request_error\"}}"));
        OpenAiChat chat = new OpenAiChat(config("", 0), "m", "s", "", List.of());
        chat.user("hi");
        LlmReply reply = chat.send(chat.prepare("low"), new Recorder());
        assertNotNull(reply.error);
        assertEquals(LlmReply.Error.Kind.SERVICE, reply.error.kind());
        assertEquals(401, reply.error.status());
        assertEquals("Incorrect API key provided", reply.error.body());
        assertFalse(reply.finished);
        assertFalse(reply.billed);
        assertEquals("null", auth.get(0), "no key, no Authorization header");
    }

    @Test
    void aBusyServerIsTriedAgain() {
        script.add(body -> new Answer(503, "text/plain", "loading model"));
        script.add(body -> sse("{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Awake.\"},"
                + "\"finish_reason\":\"stop\"}]}"));
        OpenAiChat chat = new OpenAiChat(config("", 1), "m", "s", "", List.of());
        chat.user("hi");
        Recorder rec = new Recorder();
        LlmReply reply = chat.send(chat.prepare("low"), rec);
        assertNull(reply.error);
        assertEquals("Awake.", rec.prose.toString());
        assertEquals(2, received.size());
    }

    @Test
    void anUnreachableServerIsANetworkError() {
        int port = server.getAddress().getPort();
        server.stop(0);
        OpenAiChat chat = new OpenAiChat(new OpenAiConfig(false, "http://127.0.0.1:" + port + "/v1", "", 100, 5,
                false, -1, "", false, 0), "m", "s", "", List.of());
        chat.user("hi");
        LlmReply reply = chat.send(chat.prepare("low"), new Recorder());
        assertNotNull(reply.error);
        assertEquals(LlmReply.Error.Kind.NETWORK, reply.error.kind());
        assertFalse(reply.finished);
    }

    @Test
    void aServerThatIgnoresStreamingStillWorks() {
        script.add(body -> new Answer(200, "application/json", "{\"choices\":[{\"index\":0,\"message\":{\"role\":"
                + "\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"c9\",\"type\":\"function\",\"function\":"
                + "{\"name\":\"give_item\",\"arguments\":\"{\\\"player\\\":\\\"A\\\"}\"}}]},\"finish_reason\":"
                + "\"tool_calls\"}],\"usage\":{\"prompt_tokens\":9,\"completion_tokens\":4}}"));
        OpenAiChat chat = new OpenAiChat(config("", 0), "m", "s", "", List.of(OpenAiWireTest.giveItem()));
        chat.user("hi");
        Recorder rec = new Recorder();
        LlmReply reply = chat.send(chat.prepare("low"), rec);
        assertNull(reply.error);
        assertEquals(1, rec.calls.size());
        assertEquals("give_item", rec.calls.get(0)[1]);
        assertEquals(LlmReply.Stop.TOOL_USE, reply.stop);
        assertTrue(reply.finished);
    }

    @Test
    void theMemoryModelAsksOnePlainQuestion() throws IOException {
        script.add(body -> new Answer(200, "application/json", "{\"choices\":[{\"index\":0,\"message\":{\"role\":"
                + "\"assistant\",\"content\":\"<think>names</think>{\\\"name\\\":\\\"Veyrath\\\"}\"},"
                + "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":50,\"completion_tokens\":12}}"));
        OpenAiChat.Completion c = OpenAiChat.complete(config("", 0), "small", "Invent a god.");
        assertEquals("{\"name\":\"Veyrath\"}", c.text(), "inline thinking is dropped");
        assertEquals(50, c.in());
        assertEquals(12, c.out());
        JsonObject sent = received.get(0);
        assertFalse(sent.has("stream"));
        assertFalse(sent.has("tools"));
        assertEquals("user", sent.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString());

        script.add(body -> new Answer(404, "application/json", "{\"error\":\"model 'small' not found\"}"));
        IOException e = assertThrows(IOException.class, () -> OpenAiChat.complete(config("", 0), "small", "x"));
        assertTrue(e.getMessage().contains("404"));
        assertTrue(e.getMessage().contains("not found"));
    }
}
