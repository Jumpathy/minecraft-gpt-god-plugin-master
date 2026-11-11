package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The god's private mind: a few beliefs, intentions and questions of its own, written by itself (the muse
 * power) or distilled from its recent thoughts by the memory model ({@link Musing}). Kept in
 * {@link Memory#mind}. It is NOT shown every thought: {@link #recall} picks only the thoughts that bear on
 * the moment (a mortal they are about is involved, or their words meet what is happening), and in a quiet
 * moment lets one drift back. Thoughts it neither rewrites nor finds relevant fade. Pure, so it is unit tested.
 */
public final class Mind {
    public static final List<String> KINDS = List.of("belief", "intention", "question");
    static final int TEXT_MAX = 180;
    /** A thought that drifted back in a quiet moment does not drift back again for this long. */
    static final long WANDER_GAP_MS = 30 * 60_000L;
    /** The least a thought must score to count as bearing on the moment (one name, or two shared words). */
    static final int RELEVANT = 2;

    private Mind() {
    }

    public static final class Thought {
        public String id;
        /** belief | intention | question */
        public String kind;
        public String text;
        /** Mortals (or places, things) it concerns. */
        public List<String> about = new ArrayList<>();
        public long bornAt;
        /** When it was written, rewritten, or last bore on a moment; a thought fades from this. */
        public long usedAt;
        /** When it was last shown. */
        public long surfacedAt;
        public int surfaced;
        /** "you" (the muse power) or "reflection" (distilled from its thoughts). */
        public String source = "you";
    }

    public static final class State {
        public List<Thought> thoughts = new ArrayList<>();
        public int counter;
        /** When the memory model last distilled thoughts into this mind. */
        public long reflectedAt;
    }

    /** Thrown with a message phrased for the god. */
    public static final class Refusal extends RuntimeException {
        public Refusal(String message) {
            super(message);
        }
    }

    // ---- Writing ------------------------------------------------------------------------------------

    /**
     * Keeps a thought. One that says nearly the same as a thought already held refreshes that one instead.
     * Past {@code max}, the thought least used goes (the memory model's before the god's own).
     */
    public static Thought hold(State s, String kind, String text, Collection<String> about, int max, long now,
            String source) {
        String k = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        if (!KINDS.contains(k)) throw new Refusal("kind is belief, intention or question.");
        String t = clean(text);
        if (t.length() < 6) throw new Refusal("Say the thought in a sentence.");
        Set<String> words = words(t);
        for (Thought old : s.thoughts) {
            if (old.kind.equals(k) && alike(words, words(old.text))) {
                old.text = t;
                old.usedAt = now;
                addAbout(old, about);
                return old;
            }
        }
        Thought th = new Thought();
        th.id = "m" + (++s.counter);
        th.kind = k;
        th.text = t;
        addAbout(th, about);
        th.bornAt = now;
        th.usedAt = now;
        th.source = source == null ? "you" : source;
        s.thoughts.add(th);
        while (s.thoughts.size() > Math.max(1, max)) {
            Thought weakest = null;
            for (Thought x : s.thoughts) {
                if (x == th) continue;
                if (weakest == null || rank(x) < rank(weakest)) weakest = x;
            }
            if (weakest == null) break;
            s.thoughts.remove(weakest);
        }
        return th;
    }

    private static long rank(Thought t) {
        return t.usedAt + ("you".equals(t.source) ? 24 * 3_600_000L : 0);
    }

    public static Thought find(State s, String id) {
        if (id == null) return null;
        for (Thought t : s.thoughts) {
            if (t.id.equalsIgnoreCase(id.trim())) return t;
        }
        return null;
    }

    public static Thought revise(State s, String id, String text, long now) {
        Thought t = find(s, id);
        if (t == null) throw new Refusal("You hold no thought '" + id + "'. Yours: " + ids(s) + ".");
        String c = clean(text);
        if (c.length() < 6) throw new Refusal("Say the thought in a sentence.");
        t.text = c;
        t.usedAt = now;
        t.source = "you";
        return t;
    }

    public static Thought letGo(State s, String id) {
        Thought t = find(s, id);
        if (t == null) throw new Refusal("You hold no thought '" + id + "'. Yours: " + ids(s) + ".");
        s.thoughts.remove(t);
        return t;
    }

    /** Drops thoughts neither rewritten nor relevant for {@code fadeMs}; returns how many went. */
    public static int fade(State s, long now, long fadeMs) {
        if (fadeMs <= 0) return 0;
        int before = s.thoughts.size();
        s.thoughts.removeIf(t -> now - t.usedAt > fadeMs);
        return before - s.thoughts.size();
    }

    private static String ids(State s) {
        List<String> out = new ArrayList<>();
        for (Thought t : s.thoughts) out.add(t.id);
        return out.isEmpty() ? "none" : String.join(", ", out);
    }

    private static void addAbout(Thought t, Collection<String> about) {
        if (about == null) return;
        for (String a : about) {
            if (a == null || a.isBlank() || t.about.size() >= 4) continue;
            String n = a.trim();
            if (t.about.stream().noneMatch(n::equalsIgnoreCase)) t.about.add(n);
        }
    }

    private static String clean(String text) {
        String t = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        return t.length() > TEXT_MAX ? t.substring(0, TEXT_MAX - 1) + "…" : t;
    }

    // ---- Recalling ------------------------------------------------------------------------------------

    /**
     * The moment a thought may bear on: the mortals involved, the words of what is happening (events,
     * prayers, speech, why it looked), and whether this is the god's own quiet time.
     */
    public record Cue(Collection<String> names, String text, boolean quiet) {
    }

    /** A thought and why it came: "bears" on the moment, or "drifts" back in a quiet one. */
    public record Recalled(Thought thought, int score, boolean drifted) {
    }

    /** How much a thought bears on the moment: 3 per mortal involved that it concerns, 1 per shared word (at most 3). */
    static int score(Thought t, Cue cue, Set<String> cueWords) {
        int score = 0;
        String lower = t.text.toLowerCase(Locale.ROOT);
        int named = 0;
        for (String n : cue.names()) {
            if (n == null || n.isBlank()) continue;
            String ln = n.toLowerCase(Locale.ROOT);
            if (t.about.stream().anyMatch(n::equalsIgnoreCase) || wordIn(lower, ln)) named++;
        }
        score += Math.min(2, named) * 3;
        int shared = 0;
        for (String w : words(t.text)) {
            if (cueWords.contains(w)) shared++;
        }
        for (String a : t.about) {
            if (cueWords.contains(stem(a.toLowerCase(Locale.ROOT)))) shared++;
        }
        score += Math.min(3, shared);
        return score;
    }

    /**
     * The thoughts that bear on this moment, strongest first (ties: the one shown longest ago), within
     * {@code max} and {@code budget} characters. When none does and the moment is quiet, one thought not
     * shown lately drifts back. Often empty: then nothing is shown and nothing is spent.
     */
    public static List<Recalled> recall(State s, Cue cue, int max, int budget, long now) {
        List<Recalled> hits = new ArrayList<>();
        Set<String> cueWords = words(cue.text() == null ? "" : cue.text());
        for (Thought t : s.thoughts) {
            int sc = score(t, cue, cueWords);
            if (sc >= RELEVANT) hits.add(new Recalled(t, sc, false));
        }
        hits.sort(Comparator.comparingInt((Recalled r) -> -r.score()).thenComparingLong(r -> r.thought().surfacedAt));
        List<Recalled> out = new ArrayList<>();
        int used = 0;
        for (Recalled r : hits) {
            if (out.size() >= max) break;
            int cost = r.thought().text.length() + 24;
            if (!out.isEmpty() && used + cost > budget) continue;
            out.add(r);
            used += cost;
        }
        if (out.isEmpty() && cue.quiet() && max > 0) {
            Thought drift = null;
            for (Thought t : s.thoughts) {
                if (now - t.surfacedAt < WANDER_GAP_MS) continue;
                if (drift == null || t.surfacedAt < drift.surfacedAt) drift = t;
            }
            if (drift != null) out.add(new Recalled(drift, 0, true));
        }
        return out;
    }

    /** Marks what was shown; a thought that bore on the moment is kept from fading. */
    public static void surfaced(List<Recalled> shown, long now) {
        for (Recalled r : shown) {
            r.thought().surfacedAt = now;
            r.thought().surfaced++;
            if (!r.drifted()) r.thought().usedAt = now;
        }
    }

    /** The lines under "## On your mind". */
    public static String render(List<Recalled> shown, long now) {
        StringBuilder sb = new StringBuilder();
        for (Recalled r : shown) {
            sb.append(line(r.thought(), now));
            if (r.drifted()) sb.append(" (it drifts back to you)");
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String line(Thought t, long now) {
        return "- [" + t.id + "] " + t.kind + " (" + Memory.age(now - t.bornAt) + "): " + t.text;
    }

    /** The whole mind for the reflect power, grouped by kind; with a subject, only what touches it. */
    public static String renderAll(State s, String subject, long now) {
        Set<String> q = subject == null || subject.isBlank() ? null : words(subject);
        String raw = subject == null ? "" : subject.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (String kind : KINDS) {
            List<String> lines = new ArrayList<>();
            for (Thought t : s.thoughts) {
                if (!t.kind.equals(kind)) continue;
                if (q != null) {
                    boolean hit = t.about.stream().anyMatch(a -> a.equalsIgnoreCase(raw))
                            || t.text.toLowerCase(Locale.ROOT).contains(raw);
                    for (String w : words(t.text)) hit |= q.contains(w);
                    if (!hit) continue;
                }
                lines.add(line(t, now) + (t.about.isEmpty() ? "" : " {about " + String.join(", ", t.about) + "}")
                        + ("reflection".equals(t.source) ? " {came to you on reflection}" : ""));
            }
            if (lines.isEmpty()) continue;
            sb.append(kind.equals("belief") ? "Beliefs" : kind.equals("intention") ? "Intentions" : "Questions")
                    .append(":\n");
            lines.forEach(l -> sb.append(l).append('\n'));
        }
        return sb.toString();
    }

    // ---- Words --------------------------------------------------------------------------------------

    private static final Set<String> COMMON = Set.of("that", "this", "with", "have", "from", "they", "them", "their",
            "will", "would", "should", "could", "what", "when", "where", "which", "there", "than", "then", "been",
            "were", "your", "yours", "mine", "must", "more", "most", "some", "into", "about", "does", "only", "very",
            "before", "after", "again", "never", "always", "mortal", "mortals", "because", "still", "just", "next");

    /** The telling words of a text: four letters or more, lower case, without a plural s. */
    static Set<String> words(String text) {
        Set<String> out = new LinkedHashSet<>();
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+")) {
            if (w.length() < 4 || COMMON.contains(w)) continue;
            out.add(stem(w));
        }
        return out;
    }

    private static String stem(String w) {
        return w.length() > 4 && w.endsWith("s") && !w.endsWith("ss") ? w.substring(0, w.length() - 1) : w;
    }

    private static boolean wordIn(String lowerText, String lowerWord) {
        int at = lowerText.indexOf(lowerWord);
        while (at >= 0) {
            boolean left = at == 0 || !Character.isLetterOrDigit(lowerText.charAt(at - 1));
            int end = at + lowerWord.length();
            boolean right = end >= lowerText.length() || !Character.isLetterOrDigit(lowerText.charAt(end));
            if (left && right) return true;
            at = lowerText.indexOf(lowerWord, at + 1);
        }
        return false;
    }

    /** Two thoughts say nearly the same: they share at least four fifths of their telling words. */
    static boolean alike(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        int shared = 0;
        for (String w : a) if (b.contains(w)) shared++;
        return shared * 5 >= Math.max(a.size(), b.size()) * 4;
    }

    // ---- Reflection (the memory model distils recent thoughts) ----------------------------------------

    /** What a reflection asks for: thoughts to keep, to rewrite and to let go. */
    public record Reflection(List<Thought> add, List<String[]> revise, List<String> drop) {
    }

    public static String reflectionPrompt(String god, State s, List<Memory.CycleRecord> records, long now) {
        StringBuilder held = new StringBuilder();
        for (Thought t : s.thoughts) held.append(line(t, now)).append('\n');
        StringBuilder log = new StringBuilder();
        for (Memory.CycleRecord r : records) {
            log.append("- ").append(Memory.age(now - r.at)).append(": ");
            if (r.perceived != null && !r.perceived.isBlank()) log.append("noticed ").append(Memory.clip(r.perceived, 200)).append(". ");
            if (r.thought != null && !r.thought.isBlank()) log.append("thought: ").append(r.thought).append(' ');
            if (r.acts != null && !r.acts.isEmpty()) {
                log.append("did: ");
                for (String act : r.acts) log.append(Memory.clip(act, 120)).append("; ");
            }
            log.append('\n');
        }
        return """
                You are the inner mind of %s, the god of a Minecraft world, in a moment of reflection. From what \
                the god lately noticed, thought and did, decide what it now privately holds:
                - belief: a lasting conclusion about a mortal, the world or itself ("Tyler fears me, but not enough")
                - intention: something it means to do later ("test Ash's loyalty when she next prays")
                - question: something it wonders and wants answered ("why does no one visit my shrine?")
                Keep only what would change how it acts later. Never restate plain events, quests or favor: \
                those are remembered elsewhere. Write as the god ("I"), one sentence each, at most 25 words. \
                Add at most 3. Rewrite a held thought the log has changed; drop one that is settled, done or wrong. \
                If nothing is worth holding, add nothing.
                Reply with ONLY a JSON object: {"add": [{"kind": "belief|intention|question", "text": "...", \
                "about": ["<mortal's name>"]}], "revise": [{"id": "m3", "text": "..."}], "drop": ["m5"]}

                THOUGHTS IT HOLDS NOW:
                %s
                WHAT IT LATELY NOTICED, THOUGHT AND DID (oldest first):
                %s
                """.formatted(god, held.length() == 0 ? "(none)\n" : held, log);
    }

    /** Reads the reply leniently; null when it is not usable. */
    public static Reflection parseReflection(String text) {
        if (text == null) return null;
        int start = text.indexOf('{'), end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try {
            JsonObject o = JsonParser.parseString(text.substring(start, end + 1)).getAsJsonObject();
            List<Thought> add = new ArrayList<>();
            for (JsonElement e : array(o, "add")) {
                if (!e.isJsonObject() || add.size() >= 3) continue;
                JsonObject a = e.getAsJsonObject();
                Thought t = new Thought();
                t.kind = string(a, "kind");
                t.text = string(a, "text");
                for (JsonElement n : array(a, "about")) {
                    if (n.isJsonPrimitive()) t.about.add(n.getAsString());
                }
                if (t.kind != null && t.text != null) add.add(t);
            }
            List<String[]> revise = new ArrayList<>();
            for (JsonElement e : array(o, "revise")) {
                if (!e.isJsonObject()) continue;
                String id = string(e.getAsJsonObject(), "id"), tx = string(e.getAsJsonObject(), "text");
                if (id != null && tx != null) revise.add(new String[] { id, tx });
            }
            List<String> drop = new ArrayList<>();
            for (JsonElement e : array(o, "drop")) {
                if (e.isJsonPrimitive()) drop.add(e.getAsString());
            }
            return new Reflection(add, revise, drop);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Applies a reflection, skipping whatever in it is malformed; returns a short account of what changed. */
    public static String apply(State s, Reflection r, int max, long now) {
        int added = 0, revised = 0, dropped = 0;
        for (String id : r.drop()) {
            Thought t = find(s, id);
            if (t != null) {
                s.thoughts.remove(t);
                dropped++;
            }
        }
        for (String[] rev : r.revise()) {
            Thought t = find(s, rev[0]);
            String c = clean(rev[1]);
            if (t == null || c.length() < 6) continue;
            t.text = c;
            t.usedAt = now;
            revised++;
        }
        for (Thought t : r.add()) {
            try {
                int before = s.counter;
                hold(s, t.kind, t.text, t.about, max, now, "reflection");
                if (s.counter > before) added++;
            } catch (Refusal ignored) {
                // a malformed thought is simply not kept
            }
        }
        s.reflectedAt = now;
        return added + " new, " + revised + " rewritten, " + dropped + " let go";
    }

    private static JsonArray array(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && !o.get(key).getAsString().isBlank()
                ? o.get(key).getAsString().trim() : null;
    }
}
