package net.bigyous.gptgodmc.god.llm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Reads one streamed chat completion (server-sent events) and tells a listener what arrives: prose,
 * thinking, and each tool call the moment it is complete. Pure: fed line by line, so recorded streams
 * can be replayed in tests.
 *
 * Servers differ, and this takes them as they come: tool calls streamed in pieces by index (OpenAI,
 * vLLM, llama.cpp) or whole in one chunk, sometimes all with index 0 (Ollama); reasoning in
 * reasoning_content or reasoning, or inline between think tags; usage in a last chunk or not at all;
 * "[DONE]" or just the end of the stream.
 */
public final class OpenAiStream {
    /** The server reported an error in the middle of the stream. */
    public static final class ServerError extends RuntimeException {
        public ServerError(String message) {
            super(message);
        }
    }

    private static final class Pending {
        String id, name;
        final StringBuilder json = new StringBuilder();
    }

    private final LlmChat.Listener out;
    private final Collection<String> textCallNames;
    private final ThinkSplitter splitter = new ThinkSplitter();
    private final StringBuilder text = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private final Map<Integer, Pending> pending = new LinkedHashMap<>();
    private final List<OpenAiWire.Call> calls = new ArrayList<>();
    private Pending last;
    private int unindexed = 1000;
    private long nextBlock;
    private long openBlock = -1;
    private boolean openThinking;
    private String finish;
    private boolean doneMarker, ended, billed, wroteCallsAsText;
    private long promptTokens, completionTokens, cachedTokens;

    /**
     * @param textCallNames tool names to accept when a model writes its calls into prose instead of
     *                      calling them (small models); null or empty to accept none
     */
    public OpenAiStream(LlmChat.Listener out, Collection<String> textCallNames) {
        this.out = out;
        this.textCallNames = textCallNames == null ? List.of() : textCallNames;
    }

    /** One line of the event stream. Blank lines, comments and other fields are ignored. */
    public void line(String line) {
        if (line == null || !line.startsWith("data:")) return;
        String data = line.substring(5).trim();
        if (data.isEmpty()) return;
        if (data.equals("[DONE]")) {
            doneMarker = true;
            return;
        }
        JsonElement e;
        try {
            e = JsonParser.parseString(data);
        } catch (RuntimeException ex) {
            return; // a garbled chunk: skip it rather than lose the whole reply
        }
        if (e.isJsonObject()) chunk(e.getAsJsonObject());
    }

    /** One chunk (or a whole, unstreamed completion: its "message" is read like a delta). */
    public void chunk(JsonObject c) {
        out.token();
        if (c.has("error") && !c.get("error").isJsonNull()) {
            JsonElement err = c.get("error");
            String msg = err.isJsonObject() && err.getAsJsonObject().has("message")
                    ? string(err.getAsJsonObject().get("message"))
                    : err.toString();
            throw new ServerError("the server reported an error: " + msg);
        }
        if (c.has("usage") && c.get("usage").isJsonObject()) usage(c.getAsJsonObject("usage"));
        if (!c.has("choices") || !c.get("choices").isJsonArray() || c.getAsJsonArray("choices").isEmpty()) return;
        JsonElement first = c.getAsJsonArray("choices").get(0);
        if (!first.isJsonObject()) return;
        JsonObject choice = first.getAsJsonObject();
        JsonElement d = choice.has("delta") ? choice.get("delta") : choice.get("message");
        if (d != null && d.isJsonObject()) delta(d.getAsJsonObject());
        String reason = string(choice.get("finish_reason"));
        if (reason != null && !reason.isEmpty()) {
            finish = reason;
            settle();
        }
    }

    private void delta(JsonObject d) {
        String thought = string(d.has("reasoning_content") ? d.get("reasoning_content") : d.get("reasoning"));
        if (thought != null && !thought.isEmpty()) write(true, thought);
        String said = content(d.get("content"));
        if (said != null && !said.isEmpty()) splitter.feed(said, this::write);
        if (d.has("tool_calls") && d.get("tool_calls").isJsonArray()) {
            for (JsonElement t : d.getAsJsonArray("tool_calls")) {
                if (t.isJsonObject()) toolPiece(t.getAsJsonObject());
            }
        }
    }

    private void toolPiece(JsonObject t) {
        JsonObject fn = t.has("function") && t.get("function").isJsonObject() ? t.getAsJsonObject("function")
                : new JsonObject();
        String id = string(t.get("id"));
        String name = string(fn.get("name"));
        JsonElement a = fn.get("arguments");
        String args = a == null || a.isJsonNull() ? "" : a.isJsonPrimitive() ? a.getAsString() : a.toString();
        boolean hasId = id != null && !id.isEmpty();
        boolean hasName = name != null && !name.isEmpty();
        int index = t.has("index") && t.get("index").isJsonPrimitive() ? t.get("index").getAsInt() : -1;

        Pending p = index >= 0 ? pending.get(index) : last;
        boolean fresh = p == null
                // The same index with another id is another call (servers that number every call 0).
                || (hasId && p.id != null && !p.id.equals(id))
                || (index < 0 && hasName);
        if (fresh) {
            // Whatever came before is complete: say so and carry it out before reading on.
            splitter.end(this::write);
            closeBlock();
            flushCalls();
            p = new Pending();
            pending.put(index >= 0 ? index : unindexed++, p);
            last = p;
        }
        if (hasId && p.id == null) p.id = id;
        if (hasName && p.name == null) p.name = name;
        p.json.append(args);
    }

    private void usage(JsonObject u) {
        billed = true;
        promptTokens = number(u.get("prompt_tokens"));
        completionTokens = number(u.get("completion_tokens"));
        if (u.has("prompt_tokens_details") && u.get("prompt_tokens_details").isJsonObject()) {
            cachedTokens = number(u.getAsJsonObject("prompt_tokens_details").get("cached_tokens"));
        }
    }

    /** The stream is over (cleanly or not). Safe to call more than once. */
    public void end() {
        if (ended) return;
        ended = true;
        settle();
        if (calls.isEmpty() && finish != null && !textCallNames.isEmpty()) {
            for (ToolCheck.TextCall c : ToolCheck.fromText(text.toString(), textCallNames)) {
                OpenAiWire.Call call = new OpenAiWire.Call("call_" + (calls.size() + 1), c.name(), c.json());
                calls.add(call);
                wroteCallsAsText = true;
                out.call(call.id(), call.name(), call.json());
            }
        }
    }

    private void settle() {
        splitter.end(this::write);
        closeBlock();
        flushCalls();
    }

    private void flushCalls() {
        if (pending.isEmpty()) return;
        List<Pending> ready = new ArrayList<>(pending.values());
        pending.clear();
        last = null;
        for (Pending p : ready) {
            String id = p.id == null || p.id.isEmpty() ? "call_" + (calls.size() + 1) : p.id;
            OpenAiWire.Call call = new OpenAiWire.Call(id, p.name == null ? "" : p.name, p.json.toString());
            calls.add(call);
            out.call(call.id(), call.name(), call.json());
        }
    }

    private void write(boolean thinking, String piece) {
        if (openBlock >= 0 && openThinking != thinking) closeBlock();
        if (openBlock < 0) {
            // Whitespace between blocks is not worth opening one for.
            if (piece.isBlank()) return;
            openBlock = nextBlock++;
            openThinking = thinking;
            out.open(openBlock, thinking);
        }
        (thinking ? reasoning : text).append(piece);
        out.delta(openBlock, piece);
    }

    private void closeBlock() {
        if (openBlock < 0) return;
        long b = openBlock;
        openBlock = -1;
        out.close(b);
    }

    /** What the stream amounted to. Call after {@link #end}. */
    public LlmReply reply() {
        LlmReply r = new LlmReply();
        r.finished = doneMarker || finish != null;
        r.stopText = finish == null ? "none" : finish;
        if (!calls.isEmpty()) {
            r.stop = LlmReply.Stop.TOOL_USE;
        } else if (finish == null || finish.equals("stop") || finish.equals("tool_calls")
                || finish.equals("function_call")) {
            r.stop = LlmReply.Stop.END_TURN;
        } else if (finish.equals("length")) {
            r.stop = LlmReply.Stop.MAX_TOKENS;
        } else if (finish.equals("content_filter")) {
            r.stop = LlmReply.Stop.REFUSAL;
            r.refusalDetail = " (content filter)";
        } else {
            r.stop = LlmReply.Stop.OTHER;
        }
        r.billed = billed;
        r.cacheRead = Math.min(cachedTokens, promptTokens);
        r.in = promptTokens - r.cacheRead;
        r.out = completionTokens;
        r.replayable = r.finished;
        // Calls it wrote out as prose go back as proper calls, without the prose.
        JsonObject turn = OpenAiWire.assistant(wroteCallsAsText ? "" : text.toString(), calls);
        r.turn = turn;
        if (r.finished) {
            JsonObject whole = turn.deepCopy();
            if (wroteCallsAsText) whole.addProperty("content", text.toString());
            if (reasoning.length() > 0) whole.addProperty("reasoning", reasoning.toString());
            whole.addProperty("finish_reason", r.stopText);
            if (billed) {
                JsonObject u = new JsonObject();
                u.addProperty("prompt_tokens", promptTokens);
                u.addProperty("completion_tokens", completionTokens);
                u.addProperty("cached_tokens", cachedTokens);
                whole.add("usage", u);
            }
            String json = whole.toString();
            r.responseJson = () -> json;
        }
        return r;
    }

    public List<OpenAiWire.Call> calls() {
        return calls;
    }

    public String text() {
        return text.toString();
    }

    private static String string(JsonElement e) {
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? null : e.getAsString();
    }

    /** Content as text: a plain string, or the text parts of a list. */
    static String content(JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        if (e.isJsonPrimitive()) return e.getAsString();
        if (!e.isJsonArray()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonElement part : (JsonArray) e) {
            if (part.isJsonObject() && part.getAsJsonObject().has("text")) {
                String t = string(part.getAsJsonObject().get("text"));
                if (t != null) sb.append(t);
            }
        }
        return sb.toString();
    }

    private static long number(JsonElement e) {
        try {
            return e == null || e.isJsonNull() ? 0 : e.getAsLong();
        } catch (RuntimeException ex) {
            return 0;
        }
    }
}
