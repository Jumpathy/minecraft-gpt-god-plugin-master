package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import net.bigyous.gptgodmc.god.llm.LlmChat;
import net.bigyous.gptgodmc.god.llm.LlmReply;
import net.bigyous.gptgodmc.god.llm.LlmTool;
import net.bigyous.gptgodmc.god.llm.OpenAiChat;
import net.bigyous.gptgodmc.god.llm.OpenAiConfig;
import net.bigyous.gptgodmc.god.llm.OpenAiWire;
import net.bigyous.gptgodmc.god.llm.ToolAnswer;
import net.bigyous.gptgodmc.god.llm.ToolCheck;
import net.bigyous.gptgodmc.god.tools.ToolRegistry;
import net.bigyous.gptgodmc.god.tools.ToolResult;

/**
 * Whole thoughts against a stub server that answers the way each real server does (OpenAI, Ollama,
 * LM Studio, llama.cpp, vLLM, OpenRouter), with the god's real tool list and the same loop the brain
 * runs: send, carry out each call as it arrives, send the results back, until the model is done.
 * No provider is contacted and no model is needed.
 */
class ProviderScenarioTest {
    private HttpServer server;
    private final List<JsonObject> received = new ArrayList<>();
    private final ConcurrentLinkedQueue<String> script = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            JsonObject body = JsonParser.parseString(new String(ex.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            synchronized (received) {
                received.add(body);
            }
            String next = script.poll();
            byte[] bytes = (next == null ? "data: {\"error\":{\"message\":\"unexpected request\"}}\n\n" : next)
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, 0);
            try (OutputStream out = ex.getResponseBody()) {
                // Odd-sized pieces, so events are split across reads like on a real connection.
                for (int i = 0; i < bytes.length; i += 61) {
                    out.write(bytes, i, Math.min(61, bytes.length - i));
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

    private static String sample(String name) throws IOException {
        try (InputStream in = ProviderScenarioTest.class.getResourceAsStream("/llm/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String sse(String... chunks) {
        StringBuilder sb = new StringBuilder();
        for (String c : chunks) sb.append("data: ").append(c).append("\n\n");
        return sb.append("data: [DONE]\n\n").toString();
    }

    private static final String DONE_TALKING = sse(
            "{\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"It is done.\"},"
                    + "\"finish_reason\":null}]}",
            "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}",
            "{\"choices\":[],\"usage\":{\"prompt_tokens\":4500,\"completion_tokens\":6}}");

    /** What one stubbed thought did. */
    private static final class Thought {
        final List<String> acts = new ArrayList<>();
        final List<String> refused = new ArrayList<>();
        final StringBuilder thinking = new StringBuilder(), prose = new StringBuilder();
        final List<LlmReply> replies = new ArrayList<>();
        int steps;
    }

    /**
     * The brain's loop in miniature: the same order of calls GodBrain.cycle makes on a chat, with the
     * lite profile's strict checking (GodBrain.unfit) and its one repair retry.
     */
    private Thought think(OpenAiChat chat, List<LlmTool> tools, boolean strict, int maxSteps) {
        Thought t = new Thought();
        boolean repaired = false;
        for (int step = 0; step < maxSteps; step++) {
            t.steps++;
            List<ToolAnswer> results = new ArrayList<>();
            int[] malformed = { 0 };
            java.util.Map<Long, Boolean> kinds = new java.util.HashMap<>();
            LlmReply reply = chat.send(chat.prepare("high"), new LlmChat.Listener() {
                @Override
                public void token() {
                }

                @Override
                public void open(long block, boolean isThinking) {
                    kinds.put(block, isThinking);
                }

                @Override
                public void delta(long block, String text) {
                    (Boolean.TRUE.equals(kinds.get(block)) ? t.thinking : t.prose).append(text);
                }

                @Override
                public void close(long block) {
                }

                @Override
                public void call(String id, String name, String json) {
                    JsonObject input = strict ? ToolCheck.parse(json) : GodBrain.parseInput(json);
                    String unfit = input == null ? "Invalid input: the arguments were not a complete JSON object. "
                            + "Call the power again with complete arguments."
                            : strict ? GodBrain.unfit(name, input) : null;
                    if (unfit != null) {
                        malformed[0]++;
                        t.refused.add(name + ": " + unfit);
                        results.add(new ToolAnswer(id, name, ToolResult.error(unfit)));
                        return;
                    }
                    // A stand-in for the real power: the world is not here, the arguments are.
                    t.acts.add(name + input);
                    results.add(new ToolAnswer(id, name, ToolResult.ok("Done: " + name)));
                }

                @Override
                public void search(String query) {
                }
            });
            t.replies.add(reply);
            if (reply.error != null || !reply.finished || results.isEmpty()
                    || reply.stop != LlmReply.Stop.TOOL_USE) {
                break;
            }
            if (strict && malformed[0] > 0) {
                if (repaired) break;
                repaired = true;
            }
            chat.assistant(reply);
            chat.toolResults(results, null);
        }
        return t;
    }

    private OpenAiChat chat(boolean official, List<LlmTool> tools) {
        OpenAiConfig cfg = new OpenAiConfig(official, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "", 1500, 20, false, -1, "", !official, 0);
        OpenAiChat chat = new OpenAiChat(cfg, "stub-model", LitePrompt.system(PromptsTest.thyvoru()),
                "# Your chronicle\n(Nothing has happened yet.)", tools);
        chat.user("# Perception — day 3, noon\n## Mortals present\nAshwyn (favor 6)\n"
                + "## Waiting for your answer\nAshwyn prays: I planted the grove you asked for. Bless me?");
        return chat;
    }

    private static List<LlmTool> liteTools() {
        return ToolRegistry.specs().stream().filter(s -> Llm.LITE_TOOLS.contains(s.name())).toList();
    }

    @ParameterizedTest
    @ValueSource(strings = { "openai", "lmstudio", "llamacpp", "vllm", "openrouter" })
    void everyServerFlavourPlaysTheSameThought(String flavour) throws IOException {
        script.add(sample("flavour-" + flavour + ".sse"));
        script.add(DONE_TALKING);
        boolean official = flavour.equals("openai");
        List<LlmTool> tools = official ? ToolRegistry.specs() : liteTools();
        OpenAiChat chat = chat(official, tools);

        Thought t = think(chat, tools, !official, 4);

        assertEquals(List.of("whisper{\"player\":\"Ashwyn\",\"message\":\"Your faith is seen.\"}",
                "give_item{\"player\":\"Ashwyn\",\"item\":\"golden_apple\",\"count\":2}"), t.acts, flavour);
        assertTrue(t.refused.isEmpty(), flavour + ": " + t.refused);
        assertEquals(2, t.steps, flavour);
        LlmReply first = t.replies.get(0), last = t.replies.get(1);
        assertNull(first.error, flavour);
        assertTrue(first.finished && first.replayable, flavour);
        assertEquals(LlmReply.Stop.TOOL_USE, first.stop, flavour);
        assertTrue(first.billed, flavour + " reports usage");
        assertTrue(first.in + first.cacheRead > 3000, flavour);
        assertTrue(first.out > 0, flavour);
        assertEquals(LlmReply.Stop.END_TURN, last.stop, flavour);
        assertTrue(t.prose.toString().endsWith("It is done."), flavour);

        // What the stub server was sent the second time: the turn and both results, in order.
        assertEquals(2, received.size(), flavour);
        JsonObject one = received.get(0);
        assertEquals(tools.size(), one.getAsJsonArray("tools").size(), flavour);
        assertTrue(one.has(official ? "max_completion_tokens" : "max_tokens"), flavour);
        JsonArray msgs = received.get(1).getAsJsonArray("messages");
        assertEquals(5, msgs.size(), flavour);
        JsonArray calls = msgs.get(2).getAsJsonObject().getAsJsonArray("tool_calls");
        assertEquals(2, calls.size(), flavour);
        for (int i = 0; i < 2; i++) {
            JsonObject call = calls.get(i).getAsJsonObject();
            JsonObject result = msgs.get(3 + i).getAsJsonObject();
            assertEquals("tool", result.get("role").getAsString(), flavour);
            assertEquals(call.get("id").getAsString(), result.get("tool_call_id").getAsString(),
                    flavour + ": each result answers its own call");
            assertNotNull(JsonParser.parseString(call.getAsJsonObject("function").get("arguments").getAsString())
                    .getAsJsonObject().get("player"), flavour);
        }
        assertEquals("Done: whisper", msgs.get(3).getAsJsonObject().get("content").getAsString());
    }

    @Test
    void whatEachFlavourReportsAboutItself() throws IOException {
        // OpenAI: cached input is split off, so it is not counted (or priced) twice.
        script.add(sample("flavour-openai.sse"));
        LlmReply openai = think(chat(true, ToolRegistry.specs()), ToolRegistry.specs(), false, 1).replies.get(0);
        assertEquals(160, openai.in);
        assertEquals(8960, openai.cacheRead);
        assertEquals(212, openai.out);

        // LM Studio and OpenRouter send their reasoning; it is shown, never replayed.
        script.add(sample("flavour-lmstudio.sse"));
        Thought lm = think(chat(false, liteTools()), liteTools(), true, 1);
        assertEquals("Ashwyn asks for help. I will answer and give bread.", lm.thinking.toString());
        script.add(sample("flavour-openrouter.sse"));
        Thought or = think(chat(false, liteTools()), liteTools(), true, 1);
        assertEquals("The mortal has been loyal.", or.thinking.toString());
        assertEquals("A gift, then.", or.prose.toString());
        JsonObject turn = (JsonObject) or.replies.get(0).turn;
        assertEquals("A gift, then.", turn.get("content").getAsString());
        assertFalse(turn.toString().contains("loyal"), "reasoning does not go back to the model");
        assertEquals(9050, or.replies.get(0).in);

        // vLLM repeats running usage on every chunk; the last one counts.
        script.add(sample("flavour-vllm.sse"));
        LlmReply vllm = think(chat(false, liteTools()), liteTools(), true, 1).replies.get(0);
        assertEquals(4102, vllm.in);
        assertEquals(58, vllm.out);
    }

    @Test
    void aSmallModelsBadCallIsExplainedAndRepairedOnce() {
        // 1: a 7B model invents an argument and forgets a required one.
        script.add(sse("{\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"\","
                + "\"tool_calls\":[{\"index\":0,\"id\":\"call_a\",\"type\":\"function\",\"function\":{\"name\":"
                + "\"whisper\",\"arguments\":\"{\\\"target\\\":\\\"Ashwyn\\\",\\\"message\\\":\\\"Bless you.\\\"}\"}}]},"
                + "\"finish_reason\":null}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}"));
        // 2: told what was wrong, it sends the call again correctly (as a string-encoded number too).
        script.add(sse("{\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"\","
                + "\"tool_calls\":[{\"index\":0,\"id\":\"call_b\",\"type\":\"function\",\"function\":{\"name\":"
                + "\"whisper\",\"arguments\":\"{\\\"player\\\":\\\"Ashwyn\\\",\\\"message\\\":\\\"Bless you.\\\"}\"}},"
                + "{\"index\":1,\"id\":\"call_c\",\"type\":\"function\",\"function\":{\"name\":\"give_item\","
                + "\"arguments\":\"{\\\"player\\\":\\\"Ashwyn\\\",\\\"item\\\":\\\"bread\\\",\\\"count\\\":\\\"3\\\"}\"}}]},"
                + "\"finish_reason\":null}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}]}"));
        script.add(DONE_TALKING);

        Thought t = think(chat(false, liteTools()), liteTools(), true, 4);

        assertEquals(1, t.refused.size());
        assertTrue(t.refused.get(0).contains("unknown argument \"target\"")
                || t.refused.get(0).contains("missing required argument \"player\""), t.refused.get(0));
        assertEquals(2, t.acts.size(), "nothing ran until the call was right");
        assertEquals("whisper{\"player\":\"Ashwyn\",\"message\":\"Bless you.\"}", t.acts.get(0));
        assertTrue(t.acts.get(1).contains("\"count\":3"), "\"3\" was read as 3: " + t.acts.get(1));
        assertEquals(3, t.steps);

        // The model was told exactly what was wrong and what the power takes.
        JsonArray second = received.get(1).getAsJsonArray("messages");
        String told = second.get(3).getAsJsonObject().get("content").getAsString();
        assertTrue(told.startsWith("ERROR: Invalid input for whisper:"), told);
        assertTrue(told.contains("\"player\": string (required)"), told);
        assertEquals("call_a", second.get(3).getAsJsonObject().get("tool_call_id").getAsString());
    }

    @Test
    void aSecondBadCallEndsTheThought() {
        String bad = sse("{\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"\","
                + "\"tool_calls\":[{\"index\":0,\"id\":\"call_x\",\"type\":\"function\",\"function\":{\"name\":"
                + "\"summon_dragon\",\"arguments\":\"{}\"}}]},\"finish_reason\":\"tool_calls\"}]}");
        script.add(bad);
        script.add(bad);
        script.add(DONE_TALKING);

        Thought t = think(chat(false, liteTools()), liteTools(), true, 6);

        assertEquals(2, t.steps, "one retry, then the thought ends rather than looping");
        assertEquals(2, received.size());
        assertTrue(t.acts.isEmpty());
        assertTrue(t.refused.get(0).contains("You have no power called 'summon_dragon'"));
        String told = received.get(1).getAsJsonArray("messages").get(3).getAsJsonObject().get("content")
                .getAsString();
        assertTrue(told.contains("Your powers: ") && told.contains("whisper"), told);
    }

    @Test
    void aCallWrittenAsProseByASmallModelStillRuns() {
        script.add(sse("{\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"<think>\\nShe "
                + "kept her word.\\n</think>\\n\\n<tool_call>\\n{\\\"name\\\": \\\"heal\\\", \\\"arguments\\\": "
                + "{\\\"player\\\": \\\"Ashwyn\\\"}}\\n</tool_call>\"},\"finish_reason\":null}]}",
                "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}"));
        script.add(DONE_TALKING);

        Thought t = think(chat(false, liteTools()), liteTools(), true, 4);

        assertEquals(List.of("heal{\"player\":\"Ashwyn\"}"), t.acts);
        assertTrue(t.thinking.toString().contains("She kept her word."));
        JsonObject turn = received.get(1).getAsJsonArray("messages").get(2).getAsJsonObject();
        assertEquals("heal", turn.getAsJsonArray("tool_calls").get(0).getAsJsonObject().getAsJsonObject("function")
                .get("name").getAsString(), "it goes back to the model as a proper call");
    }

    @Test
    void theLiteRequestFitsAnEightThousandTokenContext() {
        String system = LitePrompt.system(PromptsTest.thyvoru());
        int tools = OpenAiWire.tools(liteTools().stream().map(Llm::liteTool).toList()).toString().length();
        // Defaults: chronicle cut to 1500, perception 6000, room for a pressed note, a 1024-token reply.
        int chronicle = 1500, perception = 6000, pressed = 800;
        double tokens = (system.length() + tools + chronicle + perception + pressed) / 3.2 + 1024;
        assertTrue(tokens < 8192, "lite prompt " + system.length() + " chars, tools " + tools + " chars, about "
                + Math.round(tokens) + " tokens with the reply");
    }
}
