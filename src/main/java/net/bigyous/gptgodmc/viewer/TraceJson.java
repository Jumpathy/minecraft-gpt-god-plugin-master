package net.bigyous.gptgodmc.viewer;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Shapes {@link MindTrace} thoughts into the JSON the viewer page reads. Pure. */
public final class TraceJson {
    /** Perception and results longer than this are cut in list responses (full text: ?full). */
    static final int RESULT_MAX = 20_000;

    private TraceJson() {
    }

    public static JsonObject thought(String god, MindTrace.Thought t) {
        return t.read(() -> {
            JsonObject o = new JsonObject();
            o.addProperty("god", god);
            o.addProperty("id", t.id);
            o.addProperty("number", t.number);
            o.addProperty("rev", t.rev());
            o.addProperty("startedAt", t.startedAt);
            if (t.endedAt != 0) o.addProperty("endedAt", t.endedAt);
            o.addProperty("done", t.done());
            o.addProperty("trigger", t.trigger);
            o.addProperty("model", t.model);
            o.addProperty("effort", t.effort);
            o.addProperty("effortReason", t.effortReason);
            o.addProperty("perception", t.perception);
            o.addProperty("outcome", t.outcome);
            JsonObject prompts = new JsonObject();
            prompt(prompts, "system", t.system, t.systemChanged);
            prompt(prompts, "chronicle", t.chronicle, t.chronicleChanged);
            prompt(prompts, "tools", t.tools, t.toolsChanged);
            o.add("prompts", prompts);

            JsonArray reqs = new JsonArray();
            long in = 0, out = 0, cr = 0, cw = 0;
            double cost = 0;
            boolean costKnown = true;
            for (MindTrace.Request r : t.requests) {
                reqs.add(request(r));
                in += r.input;
                out += r.output;
                cr += r.cacheRead;
                cw += r.cacheWrite;
                if (r.cost < 0) costKnown = false;
                else cost += r.cost;
            }
            o.add("requests", reqs);
            JsonObject total = new JsonObject();
            total.addProperty("input", in);
            total.addProperty("output", out);
            total.addProperty("cacheRead", cr);
            total.addProperty("cacheWrite", cw);
            if (costKnown && !t.requests.isEmpty()) total.addProperty("cost", cost);
            o.add("total", total);

            JsonArray items = new JsonArray();
            int acts = 0;
            for (MindTrace.Item it : t.items) {
                items.add(item(it));
                if ("tool".equals(it.kind) || "search".equals(it.kind)) acts++;
            }
            o.add("items", items);
            o.addProperty("acts", acts);
            return o;
        });
    }

    private static void prompt(JsonObject into, String key, String hash, boolean changed) {
        JsonObject p = new JsonObject();
        p.addProperty("hash", hash);
        p.addProperty("changed", changed);
        String text = MindTrace.blobText(hash);
        p.addProperty("length", text == null ? 0 : text.length());
        into.add(key, p);
    }

    static JsonObject request(MindTrace.Request r) {
        JsonObject o = new JsonObject();
        o.addProperty("step", r.step);
        o.addProperty("model", r.model);
        o.addProperty("effort", r.effort);
        o.addProperty("startedAt", r.startedAt);
        if (r.firstTokenAt != 0) o.addProperty("firstTokenMs", r.firstTokenAt - r.startedAt);
        if (r.endedAt != 0) o.addProperty("ms", r.endedAt - r.startedAt);
        o.addProperty("input", r.input);
        o.addProperty("output", r.output);
        o.addProperty("cacheRead", r.cacheRead);
        o.addProperty("cacheWrite", r.cacheWrite);
        if (r.searches > 0) o.addProperty("searches", r.searches);
        if (r.cost >= 0) o.addProperty("cost", r.cost);
        o.addProperty("stop", r.stop);
        o.addProperty("finished", r.finished);
        o.addProperty("done", r.endedAt != 0);
        o.addProperty("error", r.error);
        if (!r.params.isEmpty()) {
            JsonObject b = new JsonObject();
            b.addProperty("params", r.params);
            b.addProperty("system", r.system);
            b.addProperty("tools", r.tools);
            b.addProperty("messages", r.messages);
            b.addProperty("response", r.response);
            b.addProperty("systemCount", r.systemCount);
            b.addProperty("toolCount", r.toolCount);
            b.addProperty("messageCount", r.messageCount);
            o.add("body", b);
        }
        return o;
    }

    static JsonObject item(MindTrace.Item it) {
        JsonObject o = new JsonObject();
        o.addProperty("id", it.id);
        o.addProperty("at", it.at);
        o.addProperty("kind", it.kind);
        o.addProperty("step", it.step);
        if (it.name != null) o.addProperty("name", it.name);
        String text = it.text();
        if (!text.isEmpty()) o.addProperty("text", text);
        if (it.input != null) o.add("input", jsonOrString(it.input));
        if (it.result != null) o.addProperty("result", cut(it.result, RESULT_MAX));
        if (it.isError) o.addProperty("isError", true);
        o.addProperty("open", it.open);
        if (it.doneAt != 0) o.addProperty("ms", it.doneAt - it.at);
        if (it.hadImage) o.addProperty("image", it.png() != null ? "kept" : "dropped");
        return o;
    }

    static JsonElement jsonOrString(String s) {
        try {
            return JsonParser.parseString(s);
        } catch (RuntimeException e) {
            return new com.google.gson.JsonPrimitive(s);
        }
    }

    static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "\n… (" + (s.length() - max) + " more chars)";
    }

    /** Every recorded thought of a god changed after a revision, oldest first. */
    public static JsonArray thoughts(String god, List<MindTrace.Thought> list) {
        JsonArray arr = new JsonArray();
        for (MindTrace.Thought t : list) {
            arr.add(thought(god, t));
        }
        return arr;
    }
}
