package net.bigyous.gptgodmc.voice;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Which voice effect one line is spoken with. The god may name a tone for a line; otherwise, with
 * effect auto, its mood decides (wrath turns the voice demonic, calm keeps it ethereal). Pure, so
 * it is unit tested.
 */
public final class VoiceTone {
    /** The default mood → effect map; voice.tts.moods in config.yml overrides single entries. */
    public static final Map<String, String> DEFAULT_MOODS = Map.of(
            "calm", "ethereal",
            "joyful", "ethereal",
            "exalted", "ethereal",
            "sorrowful", "ethereal",
            "ominous", "commanding",
            "wrathful", "demonic");

    /** Used when nothing else names a valid effect. */
    public static final String FALLBACK = "ethereal";

    private VoiceTone() {
    }

    public static boolean isEffect(String name) {
        if (name == null) return false;
        String n = name.trim().toLowerCase(Locale.ROOT);
        return Arrays.asList(GodVoiceFx.PRESETS).contains(n);
    }

    /**
     * @param tone       the tone the god asked for on this line, or null
     * @param configured voice.tts.effect: auto, or one effect forced for every line
     * @param mood       the god's current mood id (calm, wrathful, ...), or null
     * @param moods      mood → effect, used with auto
     * @param archetype  the archetype's voice-effect, used with auto when the mood has no entry
     */
    public static String choose(String tone, String configured, String mood, Map<String, String> moods,
            String archetype) {
        if (isEffect(tone)) return tone.trim().toLowerCase(Locale.ROOT);
        String c = configured == null ? "auto" : configured.trim().toLowerCase(Locale.ROOT);
        if (!c.equals("auto") && isEffect(c)) return c;
        if (mood != null && moods != null) {
            String byMood = moods.get(mood.toLowerCase(Locale.ROOT));
            if (isEffect(byMood)) return byMood.trim().toLowerCase(Locale.ROOT);
        }
        if (isEffect(archetype)) return archetype.trim().toLowerCase(Locale.ROOT);
        return FALLBACK;
    }
}
