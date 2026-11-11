package net.bigyous.gptgodmc.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

/** The mind viewer's capture: a ring of thoughts per god, prompts stored once, images capped. */
class MindTraceTest {
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
    void recordsNothingWhileOff() {
        MindTrace.setEnabled(false);
        MindTrace.Thought t = MindTrace.begin("main", 1, "x", "m", "low", "why", "seen");
        assertSame(MindTrace.Thought.NONE, t);
        long rev = MindTrace.revision();
        MindTrace.Request r = t.request(1, "m", "low");
        t.usage(r, 1, 2, 3, 4, 0, 0.1, "end_turn", true, null);
        MindTrace.Item it = t.tool(1, "smite", "{}");
        t.result(it, "done", false, new byte[] { 1 });
        t.append(t.open("thinking", 1), "hmm");
        t.end("done");
        assertEquals(rev, MindTrace.revision(), "the no-op thought never bumps the revision");
        assertNull(MindTrace.godIfAny("main"));
        assertTrue(MindTrace.Thought.NONE.items().isEmpty());
    }

    @Test
    void keepsTheLastFiftyThoughtsAndReportsChanges() {
        for (int i = 1; i <= MindTrace.THOUGHTS_KEPT + 7; i++) {
            MindTrace.begin("main", i, "t" + i, "m", "low", "", "p").end("done");
        }
        List<MindTrace.Thought> kept = MindTrace.god("main").thoughts();
        assertEquals(MindTrace.THOUGHTS_KEPT, kept.size());
        assertEquals(8, kept.get(0).number, "the oldest went first");
        long rev = MindTrace.revision();
        MindTrace.Thought live = MindTrace.begin("main", 99, "prayer", "m", "high", "a prayer", "p");
        assertEquals(List.of(live), MindTrace.god("main").since(rev));
        assertEquals(MindTrace.THOUGHTS_KEPT, MindTrace.god("main").thoughts().size());
        assertTrue(MindTrace.god("other").thoughts().isEmpty(), "gods are kept apart");
    }

    @Test
    void streamsItemsAndDropsEmptyThinking() {
        MindTrace.Thought t = MindTrace.begin("main", 1, "x", "m", "low", "", "p");
        MindTrace.Item think = t.open("thinking", 1);
        t.append(think, "I see ");
        t.append(think, "a mortal.");
        assertTrue(think.open);
        t.close(think);
        MindTrace.Item blank = t.open("thinking", 1);
        t.close(blank);
        MindTrace.Item tool = t.tool(1, "whisper", "{\"player\":\"Ash\"}");
        t.result(tool, "whispered", false, null);
        t.note("interject", 2, null, "Prayer: help");
        t.end("done: 1 act(s)");
        t.end("ended");
        assertEquals(3, t.items().size(), "the empty thinking block is gone");
        assertEquals("I see a mortal.", t.items().get(0).text());
        assertFalse(t.items().get(1).open);
        assertEquals("done: 1 act(s)", t.outcome, "the first ending sticks");
    }

    @Test
    void storesPromptsOnceAndMarksChanges() {
        MindTrace.Thought a = MindTrace.begin("main", 1, "x", "m", "low", "", "p");
        a.prompts("SYSTEM", "chron 1", "[tools]");
        assertTrue(a.systemChanged && a.chronicleChanged && a.toolsChanged, "the first is new");
        MindTrace.Thought b = MindTrace.begin("main", 2, "x", "m", "low", "", "p");
        b.prompts("SYSTEM", "chron 2", "[tools]");
        assertFalse(b.systemChanged);
        assertTrue(b.chronicleChanged);
        assertFalse(b.toolsChanged);
        assertEquals(a.system, b.system);
        assertEquals("SYSTEM", MindTrace.blobText(b.system));
        assertEquals("chron 1", MindTrace.blobText(a.chronicle));
    }

    @Test
    void capsImagesButKeepsTheirCaptions() {
        MindTrace.Thought t = MindTrace.begin("main", 1, "x", "m", "low", "", "p");
        for (int i = 0; i < MindTrace.IMAGES_KEPT + 3; i++) {
            t.result(t.tool(1, "look", "{}"), "a view", false, new byte[] { (byte) i });
        }
        List<MindTrace.Item> items = t.items();
        assertNull(items.get(0).png(), "the oldest lost its pixels");
        assertTrue(items.get(0).hadImage);
        assertNotNull(items.get(items.size() - 1).png());
        long kept = items.stream().filter(i -> i.png() != null).count();
        assertEquals(MindTrace.IMAGES_KEPT, kept);
    }

    @Test
    void shapesThoughtsIntoJson() {
        MindTrace.Thought t = MindTrace.begin("main", 4, "a prayer", "claude-sonnet-5-5", "medium", "prayed", "PERCEPTION");
        t.prompts("S", "C", "T");
        MindTrace.Request r = t.request(1, "claude-sonnet-5-5", "medium");
        t.firstToken(r);
        t.usage(r, 100, 50, 9000, 0, 0, 0.012, "tool_use", true, null);
        MindTrace.Request r2 = t.request(2, "claude-sonnet-5-5", "medium");
        t.usage(r2, 20, 10, 9100, 0, 1, 0.02, "end_turn", true, null);
        MindTrace.Item look = t.tool(1, "look", "{\"target\":\"Ash\"}");
        t.result(look, "x".repeat(TraceJson.RESULT_MAX + 10), false, new byte[] { 1, 2 });
        t.note("search", 2, "web_search", "creepers");
        t.end("done: 2 act(s)");

        JsonObject o = TraceJson.thought("main", t);
        assertEquals(4, o.get("number").getAsInt());
        assertEquals("a prayer", o.get("trigger").getAsString());
        assertEquals("PERCEPTION", o.get("perception").getAsString());
        assertTrue(o.get("done").getAsBoolean());
        assertTrue(o.getAsJsonObject("prompts").getAsJsonObject("system").get("changed").getAsBoolean());
        assertEquals(2, o.getAsJsonArray("requests").size());
        JsonObject total = o.getAsJsonObject("total");
        assertEquals(120, total.get("input").getAsLong());
        assertEquals(18100, total.get("cacheRead").getAsLong());
        assertEquals(0.032, total.get("cost").getAsDouble(), 1e-9);
        assertEquals(2, o.get("acts").getAsInt());
        JsonObject item = o.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals("look", item.get("name").getAsString());
        assertEquals("Ash", item.getAsJsonObject("input").get("target").getAsString(), "inputs stay JSON");
        assertEquals("kept", item.get("image").getAsString());
        assertTrue(item.get("result").getAsString().contains("more chars"), "long results are cut in lists");
        assertTrue(o.getAsJsonArray("requests").get(0).getAsJsonObject().has("firstTokenMs"));
    }

    @Test
    void keepsWholeRequestsInPartsWithImagesShortened() {
        MindTrace.Thought t = MindTrace.begin("main", 1, "x", "m", "low", "", "p");
        MindTrace.Request r = t.request(1, "m", "low");
        String img = "A".repeat(50_000);
        String body = "{\"model\":\"m\",\"max_tokens\":100,\"system\":[{\"type\":\"text\",\"text\":\"RULES\","
                + "\"cache_control\":{\"type\":\"ephemeral\"}},{\"type\":\"text\",\"text\":\"CHRON\"}],"
                + "\"tools\":[{\"name\":\"smite\"},{\"name\":\"look\"}],\"messages\":[{\"role\":\"user\",\"content\":"
                + "[{\"type\":\"tool_result\",\"tool_use_id\":\"t1\",\"content\":[{\"type\":\"image\",\"source\":"
                + "{\"type\":\"base64\",\"media_type\":\"image/png\",\"data\":\"" + img + "\"}}]}]}]}";
        t.requestBody(r, body, List.of("beta-1"));
        assertEquals(2, r.systemCount);
        assertEquals(2, r.toolCount);
        assertEquals(1, r.messageCount);
        assertTrue(MindTrace.blobText(r.params).contains("\"max_tokens\":100"));
        assertTrue(MindTrace.blobText(r.params).contains("beta-1"));
        assertFalse(MindTrace.blobText(r.params).contains("RULES"), "system is kept apart");
        assertTrue(MindTrace.blobText(r.system).contains("cache_control"));
        String msgs = MindTrace.blobText(r.messages);
        assertFalse(msgs.contains(img), "image data is not kept");
        assertTrue(msgs.contains("image/png, 36 KB, not shown"), msgs);
        MindTrace.Request r2 = t.request(2, "m", "low");
        t.requestBody(r2, body, null);
        assertEquals(r.tools, r2.tools, "the same tool list is stored once");
        t.responseBody(r2, "{\"role\":\"assistant\",\"content\":[{\"type\":\"thinking\",\"thinking\":\"hm\",\"signature\":\""
                + "s".repeat(500) + "\"}]}");
        assertTrue(MindTrace.blobText(r2.response).contains("signature, 500 chars, not shown"));
        JsonObject j = TraceJson.thought("main", t).getAsJsonArray("requests").get(0).getAsJsonObject();
        assertEquals(r.messages, j.getAsJsonObject("body").get("messages").getAsString());
    }

    @Test
    void unknownCostIsLeftOut() {
        MindTrace.Thought t = MindTrace.begin("main", 1, "x", "custom-model", "low", "", "p");
        t.usage(t.request(1, "custom-model", "low"), 1, 1, 0, 0, 0, -1, "end_turn", true, null);
        assertFalse(TraceJson.thought("main", t).getAsJsonObject("total").has("cost"));
    }
}
