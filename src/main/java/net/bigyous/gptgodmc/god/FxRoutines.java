package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

/**
 * Routines: a god's saved sequences of its own powers, replayed as one act (library_run). Pure: the step
 * format, its limits, and which powers may be steps or carry a bound effect.
 */
public final class FxRoutines {
    private FxRoutines() {
    }

    public static final int MAX_STEPS = 12;
    /** Longest wait before one step, in ticks. */
    public static final int MAX_DELAY = 200;
    /** Longest a whole routine may wait, in ticks (the god's thought is held while it runs). */
    public static final int MAX_TOTAL_DELAY = 400;
    public static final int MAX_ARGS_CHARS = 6000;

    /** Never a step: a routine may not run routines or edit the library, and never answers an operator. */
    public static final Set<String> NOT_STEPS = Set.of("library_run", "library_save", "library_forget", "command_done");

    public static final String HELP = "Steps are a JSON array, in order: [{\"tool\":\"vessel_mood\",\"args\":{\"mood\":"
            + "\"wrathful\"}},{\"tool\":\"fx_replay\",\"args\":{\"name\":\"ashen_verdict\",\"at\":\"vessel\"},\"delay_ticks\":20}]. "
            + "\"tool\" is any power of yours (not " + String.join(", ", new java.util.TreeSet<>(NOT_STEPS)) + "), \"args\" "
            + "exactly what you would pass it, \"delay_ticks\" the wait before that step (20 = 1 s, at most " + MAX_DELAY
            + " per step and " + MAX_TOTAL_DELAY + " in all). At most " + MAX_STEPS + " steps.";

    public static final class Step {
        public String tool;
        public JsonObject args;
        @SerializedName("delay_ticks")
        public int delay;
    }

    /**
     * Parses and validates the steps the god wrote (a JSON array, as text or already parsed).
     *
     * @param known whether a power of this name exists
     */
    public static List<Step> check(JsonElement raw, Predicate<String> known) throws FxScenes.Invalid {
        JsonElement root = raw;
        if (root != null && root.isJsonPrimitive()) {
            String text = root.getAsString();
            if (text.isBlank()) throw new FxScenes.Invalid("The routine has no steps. " + HELP);
            try {
                root = JsonParser.parseString(text);
            } catch (JsonParseException e) {
                throw new FxScenes.Invalid("The steps are not valid JSON: " + e.getMessage());
            }
        }
        if (root != null && root.isJsonObject() && root.getAsJsonObject().has("steps")) root = root.getAsJsonObject().get("steps");
        if (root == null || !root.isJsonArray()) throw new FxScenes.Invalid("The steps must be a JSON array. " + HELP);
        JsonArray in = root.getAsJsonArray();
        if (in.isEmpty()) throw new FxScenes.Invalid("The routine has no steps.");
        if (in.size() > MAX_STEPS) throw new FxScenes.Invalid("At most " + MAX_STEPS + " steps in one routine (you gave " + in.size() + ").");
        List<Step> out = new ArrayList<>();
        int total = 0, n = 0;
        for (JsonElement e : in) {
            n++;
            if (!e.isJsonObject()) throw new FxScenes.Invalid("Step " + n + " is not an object.");
            JsonObject o = e.getAsJsonObject();
            JsonElement t = o.get("tool");
            String tool = t != null && t.isJsonPrimitive() ? t.getAsString().trim().toLowerCase(Locale.ROOT) : "";
            if (tool.isEmpty()) throw new FxScenes.Invalid("Step " + n + " needs a \"tool\".");
            if (NOT_STEPS.contains(tool)) throw new FxScenes.Invalid("Step " + n + ": '" + tool + "' cannot be part of a routine.");
            if (!known.test(tool)) throw new FxScenes.Invalid("Step " + n + ": you have no power called '" + tool + "'.");
            JsonElement a = o.get("args");
            if (a != null && a.isJsonPrimitive() && a.getAsJsonPrimitive().isString()) {
                try {
                    a = JsonParser.parseString(a.getAsString());
                } catch (JsonParseException ex) {
                    throw new FxScenes.Invalid("Step " + n + ": \"args\" is not valid JSON.");
                }
            }
            if (a != null && !a.isJsonNull() && !a.isJsonObject()) throw new FxScenes.Invalid("Step " + n + ": \"args\" must be an object.");
            JsonObject args = a == null || a.isJsonNull() ? new JsonObject() : a.getAsJsonObject().deepCopy();
            if (args.toString().length() > MAX_ARGS_CHARS) throw new FxScenes.Invalid("Step " + n + ": its args are too long.");
            JsonElement d = o.has("delay_ticks") ? o.get("delay_ticks") : o.get("delay");
            int delay = 0;
            if (d != null && !d.isJsonNull()) {
                try {
                    delay = (int) Math.round(d.getAsDouble());
                } catch (RuntimeException ex) {
                    throw new FxScenes.Invalid("Step " + n + ": \"delay_ticks\" must be a number.");
                }
            }
            if (delay < 0 || delay > MAX_DELAY) throw new FxScenes.Invalid("Step " + n + ": delay_ticks must be 0 to " + MAX_DELAY + ".");
            total += delay;
            if (total > MAX_TOTAL_DELAY) {
                throw new FxScenes.Invalid("The routine waits too long in all (" + total + " ticks by step " + n + "; at most "
                        + MAX_TOTAL_DELAY + ").");
            }
            Step s = new Step();
            s.tool = tool;
            s.args = args;
            s.delay = delay;
            out.add(s);
        }
        return out;
    }

    public static int totalDelay(List<Step> steps) {
        int t = 0;
        for (Step s : steps) t += Math.max(0, s.delay);
        return t;
    }

    /** "smite → fx_replay (+20t) → announce" */
    public static String outline(List<Step> steps) {
        StringBuilder sb = new StringBuilder();
        for (Step s : steps) {
            if (sb.length() > 0) sb.append(" → ");
            sb.append(s.tool);
            if (s.delay > 0) sb.append(" (+").append(s.delay).append("t)");
        }
        return sb.toString();
    }

    /** May an effect be bound to this power? Not to the effect and library powers themselves. */
    public static boolean bindable(String tool) {
        return tool != null && !tool.startsWith("fx_") && !tool.startsWith("library_") && !tool.equals("command_done");
    }

    private static final List<String> TARGET_KEYS = List.of("player", "target", "at", "where", "location", "to");

    /** Where a power was aimed, from its arguments (a player, place or structure name), or null. */
    public static String targetOf(JsonObject input) {
        if (input == null) return null;
        for (String k : TARGET_KEYS) {
            JsonElement e = input.get(k);
            if (e != null && e.isJsonPrimitive() && !e.getAsString().isBlank()) return e.getAsString().trim();
        }
        return null;
    }
}
