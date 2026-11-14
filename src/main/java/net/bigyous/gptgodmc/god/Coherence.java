package net.bigyous.gptgodmc.god;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Keeps the god's words and acts together within one thought. The god speaks first (so mortals hear it at
 * once) and acts after, and a model that has already said "eat something" can still go on to heal the
 * same mortal. This remembers what it said to whom in the thought, and when a later power names one of
 * those mortals, the power's result quotes its own words back to it once, so it can see a contradiction
 * and own it in character (or see that there is none). Pure: unit tested.
 */
public final class Coherence {
    /** The powers that speak to mortals. */
    static final Set<String> SPEECH = Set.of("whisper", "vessel_say");
    static final int WORDS_MAX = 160;

    private final Map<String, String> said = new LinkedHashMap<>(); // lower-case name -> their words
    private final Map<String, String> names = new LinkedHashMap<>(); // lower-case name -> as written
    private final Set<String> reminded = new HashSet<>();

    /**
     * After a power ran: remembers what was said, or for any other power that names a mortal it spoke to
     * in this thought, returns the note to add to the power's result (null for nothing to add).
     */
    public String after(String tool, JsonObject input, String result, boolean error) {
        if (error || tool == null || input == null) return null;
        if (SPEECH.contains(tool)) {
            String words = str(input, "message");
            if (words == null || words.isBlank()) return null;
            if (tool.equals("whisper")) {
                heard(str(input, "player"), words);
            } else if (result != null && result.startsWith("Heard by: ")) {
                for (String n : result.substring("Heard by: ".length()).split(",\\s*")) heard(n, words);
            }
            return null;
        }
        if (said.isEmpty()) return null;
        StringBuilder note = new StringBuilder();
        for (String value : strings(input, new java.util.ArrayList<>())) {
            String key = value.trim().toLowerCase(Locale.ROOT);
            if (!said.containsKey(key) || !reminded.add(key)) continue;
            note.append(note.isEmpty() ? "" : " ").append("Earlier in this turn you told ").append(names.get(key))
                    .append(": \"").append(said.get(key)).append("\".");
        }
        if (note.isEmpty()) return null;
        return " (" + note + " Your acts must agree with your words. If this one does not (you told them to "
                + "see to it themselves and then did it for them, refused and then granted it, warned and then "
                + "let it pass), own the change now in a few words, in character. If it agrees, carry on and say "
                + "nothing more about it.)";
    }

    private void heard(String name, String words) {
        if (name == null || name.isBlank()) return;
        String key = name.trim().toLowerCase(Locale.ROOT);
        String w = words.length() > WORDS_MAX ? words.substring(0, WORDS_MAX) + "…" : words;
        String before = said.get(key);
        said.put(key, before == null ? w : clip(before + " / " + w));
        names.put(key, name.trim());
        reminded.remove(key); // new words: the next act on them is checked again
    }

    static String clip(String s) {
        return s.length() > WORDS_MAX * 2 ? "…" + s.substring(s.length() - WORDS_MAX * 2) : s;
    }

    static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    /** Every string in the arguments (players, targets, lists of names), one level of nesting deep. */
    static java.util.List<String> strings(JsonElement e, java.util.List<String> out) {
        if (e == null || e.isJsonNull()) return out;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            String s = e.getAsString();
            for (String part : s.split(",")) out.add(part);
        } else if (e.isJsonArray()) {
            for (JsonElement x : (JsonArray) e) strings(x, out);
        } else if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) {
                if (!x.getKey().equals("message")) strings(x.getValue(), out);
            }
        }
        return out;
    }
}
