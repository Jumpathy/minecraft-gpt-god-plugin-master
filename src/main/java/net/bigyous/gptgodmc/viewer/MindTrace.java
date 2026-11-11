package net.bigyous.gptgodmc.viewer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * What each god's mind did, thought by thought, for the mind viewer: the perception it was given, the
 * prompts and tools it was sent (stored once each, by hash), every request with its usage, streamed
 * thinking and text, tool calls with their results and images, interjections and the outcome.
 *
 * Pure and cheap: the brain thread only appends small objects under short locks. Nothing is kept
 * while {@link #enabled} is false; then every call returns {@link Thought#NONE}, which ignores all.
 */
public final class MindTrace {
    /** Thoughts kept per god. */
    public static final int THOUGHTS_KEPT = 50;
    /** Images (from look) kept per god; older ones keep their caption but lose their pixels. */
    public static final int IMAGES_KEPT = 40;
    /** Distinct texts kept (prompts, tool lists, request bodies), oldest dropped first. */
    static final int BLOBS_KEPT = 6000;
    /** ...and at most this many characters in all. */
    static final long BLOB_CHARS = 48_000_000L;
    /** Strings longer than this (other than known opaque fields) are cut. */
    static final int LONG_STRING = 100_000;
    /** Streamed text kept per item. */
    static final int ITEM_TEXT_MAX = 200_000;

    private static volatile boolean enabled;
    private static final AtomicLong REV = new AtomicLong();
    private static final AtomicLong IDS = new AtomicLong();
    private static final Map<String, GodTrace> GODS = new ConcurrentHashMap<>();
    private static final Map<String, String> BLOBS = new LinkedHashMap<>();
    private static long blobChars;

    private MindTrace() {
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Bumped on every change; the viewer asks for what changed since a revision. */
    public static long revision() {
        return REV.get();
    }

    static long bump() {
        return REV.incrementAndGet();
    }

    public static void clear() {
        GODS.clear();
        synchronized (BLOBS) {
            BLOBS.clear();
            blobChars = 0;
        }
    }

    // ---- Blobs ------------------------------------------------------------------------------------

    /** Stores text once and returns its hash ("" for null). */
    static String blob(String text) {
        if (text == null) return "";
        String h = hash(text);
        synchronized (BLOBS) {
            String old = BLOBS.remove(h);
            if (old != null) blobChars -= old.length();
            BLOBS.put(h, text);
            blobChars += text.length();
            while (BLOBS.size() > 1 && (BLOBS.size() > BLOBS_KEPT || blobChars > BLOB_CHARS)) {
                String first = BLOBS.keySet().iterator().next();
                blobChars -= BLOBS.remove(first).length();
            }
        }
        return h;
    }

    public static String blobText(String hash) {
        synchronized (BLOBS) {
            return BLOBS.get(hash);
        }
    }

    static String hash(String text) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    // ---- Gods -------------------------------------------------------------------------------------

    public static GodTrace god(String id) {
        return GODS.computeIfAbsent(id, GodTrace::new);
    }

    public static GodTrace godIfAny(String id) {
        return GODS.get(id);
    }

    /** Starts recording one thought, or {@link Thought#NONE} when the viewer is off. */
    public static Thought begin(String god, int number, String trigger, String model, String effort,
            String effortReason, String perception) {
        if (!enabled) return Thought.NONE;
        return god(god).begin(number, trigger, model, effort, effortReason, perception);
    }

    /** One god's recent thoughts, oldest first. */
    public static final class GodTrace {
        public final String id;
        private final Deque<Thought> thoughts = new ArrayDeque<>();
        private String lastSystem = "", lastChronicle = "", lastTools = "";
        private int images;

        GodTrace(String id) {
            this.id = id;
        }

        synchronized Thought begin(int number, String trigger, String model, String effort, String effortReason,
                String perception) {
            Thought t = new Thought(this, IDS.incrementAndGet(), number, trigger, model, effort, effortReason,
                    perception);
            thoughts.addLast(t);
            while (thoughts.size() > THOUGHTS_KEPT) {
                thoughts.removeFirst();
            }
            return t;
        }

        /** Remembers the prompts this thought was sent and says which changed since the last thought. */
        synchronized String[] prompts(String system, String chronicle, String tools) {
            String s = blob(system), c = blob(chronicle), t = blob(tools);
            String[] out = { s, c, t, lastSystem, lastChronicle, lastTools };
            lastSystem = s;
            lastChronicle = c;
            lastTools = t;
            return out;
        }

        /** Counts an image in; returns true when the oldest kept image must drop its pixels. */
        synchronized void imageAdded() {
            images++;
            if (images <= IMAGES_KEPT) return;
            for (Thought t : thoughts) {
                if (t.dropOldestImage()) {
                    images--;
                    if (images <= IMAGES_KEPT) return;
                }
            }
        }

        public synchronized List<Thought> thoughts() {
            return new ArrayList<>(thoughts);
        }

        public synchronized Thought find(long id) {
            for (Thought t : thoughts) {
                if (t.id == id) return t;
            }
            return null;
        }

        /** Thoughts changed after a revision. */
        public synchronized List<Thought> since(long rev) {
            List<Thought> out = new ArrayList<>();
            for (Thought t : thoughts) {
                if (t.rev() > rev) out.add(t);
            }
            return out;
        }
    }

    /**
     * A copy with base64 image data, thinking signatures and other opaque strings replaced by short
     * placeholders, so a request can be read and kept cheaply.
     */
    static JsonElement shorten(JsonElement e) {
        if (e == null || e.isJsonNull() || e.isJsonPrimitive()) return e;
        if (e.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement x : e.getAsJsonArray()) out.add(shorten(x));
            return out;
        }
        JsonObject in = e.getAsJsonObject();
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> en : in.entrySet()) {
            JsonElement v = en.getValue();
            String k = en.getKey();
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                String s = v.getAsString();
                // An OpenAI-style inline image (image_url.url = "data:image/png;base64,...") is opaque too.
                boolean inlineImage = k.equals("url") && s.startsWith("data:");
                boolean opaque = k.equals("data") || k.equals("signature") || k.startsWith("encrypted_")
                        || inlineImage;
                if (opaque && s.length() > 64) {
                    String what = inlineImage ? s.substring(5, Math.max(5, s.indexOf(';')))
                            : k.equals("data") && in.has("media_type") ? in.get("media_type").getAsString() : k;
                    String size = s.length() >= 2048 ? (s.length() * 3L / 4 / 1024) + " KB" : s.length() + " chars";
                    out.addProperty(k, "[" + what + ", " + size + ", not shown]");
                    continue;
                }
                if (s.length() > LONG_STRING) {
                    out.addProperty(k, s.substring(0, LONG_STRING) + "… [" + s.length() + " chars]");
                    continue;
                }
            }
            out.add(k, shorten(v));
        }
        return out;
    }

    // ---- One thought ------------------------------------------------------------------------------

    /** One request to Claude within a thought. */
    public static final class Request {
        public final int step;
        public final String model, effort;
        public final long startedAt;
        public long firstTokenAt, endedAt;
        public long input, output, cacheRead, cacheWrite, searches;
        public double cost = -1;
        public String stop, error;
        public boolean finished;
        /** Hashes of what was sent, split up: other parameters, system blocks, tools, messages; and the reply. */
        public String params = "", system = "", tools = "", messages = "", response = "";
        public int messageCount, toolCount, systemCount;

        Request(int step, String model, String effort, long startedAt) {
            this.step = step;
            this.model = model;
            this.effort = effort;
            this.startedAt = startedAt;
        }
    }

    /** One entry of a thought's timeline. */
    public static final class Item {
        public final long id;
        public final long at;
        /** thinking | text | tool | search | interject | note | error | info */
        public final String kind;
        public final int step;
        public final String name;
        final StringBuilder text = new StringBuilder();
        public String input;
        public String result;
        public boolean isError;
        public boolean open = true;
        public long doneAt;
        byte[] png;
        public boolean hadImage;

        Item(long id, long at, String kind, int step, String name) {
            this.id = id;
            this.at = at;
            this.kind = kind;
            this.step = step;
            this.name = name;
        }

        public String text() {
            return text.toString();
        }

        public byte[] png() {
            return png;
        }
    }

    public static class Thought {
        /** Records nothing; returned while the viewer is off. */
        public static final Thought NONE = new Thought(null, 0, 0, "", "", "", "", "");

        private final GodTrace owner;
        public final long id;
        public final int number;
        public final long startedAt;
        public final String trigger, model, effort, effortReason, perception;
        public long endedAt;
        public String outcome;
        public String system = "", chronicle = "", tools = "";
        public boolean systemChanged, chronicleChanged, toolsChanged;
        final List<Request> requests = new ArrayList<>();
        final List<Item> items = new ArrayList<>();
        private volatile long rev;

        Thought(GodTrace owner, long id, int number, String trigger, String model, String effort,
                String effortReason, String perception) {
            this.owner = owner;
            this.id = id;
            this.number = number;
            this.startedAt = System.currentTimeMillis();
            this.trigger = trigger;
            this.model = model;
            this.effort = effort;
            this.effortReason = effortReason;
            this.perception = perception;
            touch();
        }

        private boolean live() {
            return owner != null;
        }

        private void touch() {
            if (owner != null) rev = bump();
        }

        public long rev() {
            return rev;
        }

        public boolean done() {
            return endedAt != 0;
        }

        /** The system prompt, chronicle block and tool list (JSON) sent with the first request. */
        public void prompts(String systemText, String chronicleText, String toolsJson) {
            if (!live()) return;
            String[] h = owner.prompts(systemText, chronicleText, toolsJson);
            synchronized (this) {
                system = h[0];
                chronicle = h[1];
                tools = h[2];
                systemChanged = !h[0].equals(h[3]);
                chronicleChanged = !h[1].equals(h[4]);
                toolsChanged = !h[2].equals(h[5]);
            }
            touch();
        }

        public synchronized Request request(int step, String requestModel, String requestEffort) {
            Request r = new Request(step, requestModel, requestEffort, System.currentTimeMillis());
            if (!live()) return r;
            requests.add(r);
            touch();
            return r;
        }

        /**
         * The whole request body as sent (JSON), and its beta headers. Kept in parts so the tool list and
         * system prompt are stored once; images and signatures are shortened to placeholders.
         */
        public void requestBody(Request r, String json, List<String> betas) {
            if (!live() || r == null || json == null) return;
            JsonObject body;
            try {
                body = JsonParser.parseString(json).getAsJsonObject();
            } catch (RuntimeException e) {
                return;
            }
            JsonElement sys = body.remove("system");
            JsonElement tl = body.remove("tools");
            JsonElement msgs = body.remove("messages");
            if (betas != null && !betas.isEmpty()) {
                JsonArray b = new JsonArray();
                betas.forEach(b::add);
                body.add("_anthropic-beta", b);
            }
            String p = blob(body.toString());
            String s = sys == null ? "" : blob(sys.toString());
            String t = tl == null ? "" : blob(tl.toString());
            String m = msgs == null ? "" : blob(shorten(msgs).toString());
            synchronized (this) {
                r.params = p;
                r.system = s;
                r.tools = t;
                r.messages = m;
                r.systemCount = sys == null ? 0 : sys.isJsonArray() ? sys.getAsJsonArray().size() : 1;
                r.toolCount = tl != null && tl.isJsonArray() ? tl.getAsJsonArray().size() : 0;
                r.messageCount = msgs != null && msgs.isJsonArray() ? msgs.getAsJsonArray().size() : 0;
            }
            touch();
        }

        /** Claude's whole reply message (JSON) for a request. */
        public void responseBody(Request r, String json) {
            if (!live() || r == null || json == null) return;
            String h;
            try {
                h = blob(shorten(JsonParser.parseString(json)).toString());
            } catch (RuntimeException e) {
                return;
            }
            synchronized (this) {
                r.response = h;
            }
            touch();
        }

        /** Marks the first streamed event of a request. */
        public void firstToken(Request r) {
            if (!live() || r.firstTokenAt != 0) return;
            synchronized (this) {
                r.firstTokenAt = System.currentTimeMillis();
            }
            touch();
        }

        public void usage(Request r, long in, long out, long cacheRead, long cacheWrite, long searches, double cost,
                String stop, boolean finished, String error) {
            if (!live()) return;
            synchronized (this) {
                r.input = in;
                r.output = out;
                r.cacheRead = cacheRead;
                r.cacheWrite = cacheWrite;
                r.searches = searches;
                r.cost = cost;
                r.stop = stop;
                r.finished = finished;
                r.error = error;
                r.endedAt = System.currentTimeMillis();
            }
            touch();
        }

        /** Opens a streamed item (thinking or text) that grows with {@link #append}. */
        public synchronized Item open(String kind, int step) {
            Item it = new Item(IDS.incrementAndGet(), System.currentTimeMillis(), kind, step, null);
            if (!live()) return it;
            items.add(it);
            touch();
            return it;
        }

        public void append(Item it, String more) {
            if (!live() || it == null || more == null || more.isEmpty()) return;
            synchronized (this) {
                if (it.text.length() < ITEM_TEXT_MAX) it.text.append(more);
            }
            touch();
        }

        public void close(Item it) {
            if (!live() || it == null) return;
            synchronized (this) {
                it.open = false;
                it.doneAt = System.currentTimeMillis();
                // Empty streamed blocks (a thinking block with no summary) are not worth a line.
                if (it.text.toString().isBlank() && ("thinking".equals(it.kind) || "text".equals(it.kind))) {
                    items.remove(it);
                }
            }
            touch();
        }

        /** A tool call about to run; finish it with {@link #result}. */
        public synchronized Item tool(int step, String name, String inputJson) {
            Item it = new Item(IDS.incrementAndGet(), System.currentTimeMillis(), "tool", step, name);
            it.input = inputJson;
            if (!live()) return it;
            items.add(it);
            touch();
            return it;
        }

        public void result(Item it, String text, boolean isError, byte[] png) {
            if (!live() || it == null) return;
            boolean image = png != null && png.length > 0;
            synchronized (this) {
                it.result = text;
                it.isError = isError;
                it.png = image ? png : null;
                it.hadImage = image;
                it.open = false;
                it.doneAt = System.currentTimeMillis();
            }
            if (image) owner.imageAdded();
            touch();
        }

        /** A one-off line: search, interject, note, error or info. */
        public synchronized Item note(String kind, int step, String name, String text) {
            Item it = new Item(IDS.incrementAndGet(), System.currentTimeMillis(), kind, step, name);
            if (text != null) it.text.append(text.length() > ITEM_TEXT_MAX ? text.substring(0, ITEM_TEXT_MAX) : text);
            it.open = false;
            it.doneAt = it.at;
            if (!live()) return it;
            items.add(it);
            touch();
            return it;
        }

        public void end(String outcomeText) {
            if (!live()) return;
            synchronized (this) {
                if (endedAt != 0) return;
                endedAt = System.currentTimeMillis();
                outcome = outcomeText;
                for (Item it : items) {
                    if (it.open) {
                        it.open = false;
                        it.doneAt = endedAt;
                    }
                }
            }
            touch();
        }

        synchronized boolean dropOldestImage() {
            for (Item it : items) {
                if (it.png != null) {
                    it.png = null;
                    return true;
                }
            }
            return false;
        }

        public synchronized List<Request> requests() {
            return new ArrayList<>(requests);
        }

        public synchronized List<Item> items() {
            return new ArrayList<>(items);
        }

        public synchronized Item item(long itemId) {
            for (Item it : items) {
                if (it.id == itemId) return it;
            }
            return null;
        }

        /** Runs work while holding this thought's lock (a consistent snapshot for JSON). */
        public synchronized <T> T read(java.util.function.Supplier<T> work) {
            return work.get();
        }
    }
}
