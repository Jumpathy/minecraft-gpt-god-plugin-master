package net.bigyous.gptgodmc.god;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

/**
 * anthropic.effort: auto — how hard the god thinks this time, chosen before the thought from what woke
 * it: low for a routine look, medium when a mortal simply talks to it, high for judgment calls
 * (requests, several voices, operator commands, deaths, mortals it loves or hates). Chosen once per
 * thought and kept for all its steps. Pure, so it is unit tested.
 */
public final class EffortPicker {
    public static final List<String> LEVELS = List.of("low", "medium", "high");

    /** What a thought is about to see. */
    public record Signals(List<String> prayers, List<String> speech, List<String> events, boolean command,
            String godName, ToIntFunction<String> favorOf) {
    }

    public record Choice(String effort, String reason) {
    }

    private EffortPicker() {
    }

    /** Words that make a line a request for something. */
    static final Pattern REQUEST = Pattern.compile("\\b(give|gimme|can i|can you|could you|would you|will you|"
            + "please|pls|plz|i want|i need|i'd like|we need|we want|spawn|summon|make me|make us|build|bring|"
            + "find me|send|heal|grant|bless|let me|may i|teleport|tp me|kill|smite|punish|destroy|help)\\b",
            Pattern.CASE_INSENSITIVE);

    /** The effort to use: the configured level, or with "auto" one chosen from the signals. */
    public static Choice choose(String configured, Signals s) {
        String c = configured == null ? "auto" : configured.trim().toLowerCase(Locale.ROOT);
        if (LEVELS.contains(c)) return new Choice(c, "set in config");
        if (s.command()) return new Choice("high", "an operator command");
        Set<String> addressedBy = speakers(s.prayers());
        for (String line : s.speech()) {
            if (mentionsGod(line, s.godName())) addressedBy.add(speaker(line));
        }
        addressedBy.remove(null);
        for (String line : s.prayers()) {
            if (REQUEST.matcher(said(line)).find()) return new Choice("high", "a mortal asks for something");
        }
        for (String line : s.speech()) {
            if (mentionsGod(line, s.godName()) && REQUEST.matcher(said(line)).find()) {
                return new Choice("high", "a mortal asks for something");
            }
        }
        for (String e : s.events()) {
            if (e.contains("DEATH:")) return new Choice("high", "a death");
        }
        if (!addressedBy.isEmpty()) {
            Set<String> talking = speakers(s.speech());
            talking.addAll(addressedBy);
            talking.remove(null);
            if (talking.size() >= 2) return new Choice("high", "several mortals talking");
            for (String who : addressedBy) {
                int f = s.favorOf() == null ? 0 : s.favorOf().applyAsInt(who);
                if (Math.abs(f) >= 6) return new Choice("high", who + " is " + Memory.favorLabel(f));
            }
            return new Choice("medium", "a mortal speaks to you");
        }
        return new Choice("low", "a routine look");
    }

    /** Resolves "auto" (or nonsense) to a real level for callers that have no signals. */
    public static String fixed(String configured) {
        String c = configured == null ? "" : configured.trim().toLowerCase(Locale.ROOT);
        return LEVELS.contains(c) ? c : "high";
    }

    // ---- Reading event lines ("[5s ago] Alex prays to you: \"...\"") ----------------------------

    static Set<String> speakers(Collection<String> lines) {
        Set<String> out = new LinkedHashSet<>();
        for (String l : lines) out.add(speaker(l));
        return out;
    }

    /** The first word after the time stamp: the mortal's name. */
    static String speaker(String line) {
        String t = line;
        int close = t.indexOf("] ");
        if (t.startsWith("[") && close > 0) t = t.substring(close + 2);
        int space = t.indexOf(' ');
        return space > 0 ? t.substring(0, space) : null;
    }

    /** What was said (inside the quotes), or the whole line. */
    static String said(String line) {
        int q = line.indexOf('"');
        int end = line.lastIndexOf('"');
        return q >= 0 && end > q ? line.substring(q + 1, end) : line;
    }

    static boolean mentionsGod(String line, String godName) {
        String s = said(line).toLowerCase(Locale.ROOT);
        if (Pattern.compile("\\bgods?\\b").matcher(s).find()) return true;
        return godName != null && !godName.isBlank() && s.contains(godName.toLowerCase(Locale.ROOT));
    }
}
