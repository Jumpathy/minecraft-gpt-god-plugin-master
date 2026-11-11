package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import net.bigyous.gptgodmc.god.vessel.VesselMood;

/**
 * The god's lasting feelings. Each emotion (wrath, joy, sorrow, dread, glory) is a level from 0 to 100
 * that rises with what mortals do and fades by itself with a half-life of tens of minutes, so an insult
 * still smoulders a while later and a run of gifts leaves it warm for an hour. The strongest feeling
 * (above {@link #SHOWS}) is the mood its body and voice fall back to when no timed mood is set; all of
 * them are shown in its perception. Saved inside memory.json. Pure and thread-safe, so it is unit tested.
 */
public final class Heart {
    /** The emotions, each tied to the vessel mood it shows as. */
    public enum Feeling {
        WRATH(VesselMood.WRATHFUL, 20), JOY(VesselMood.JOYFUL, 30), SORROW(VesselMood.SORROWFUL, 45),
        DREAD(VesselMood.OMINOUS, 25), GLORY(VesselMood.EXALTED, 15);

        public final VesselMood mood;
        /** Minutes for the feeling to halve on its own. */
        public final int halfLifeMinutes;

        Feeling(VesselMood mood, int halfLifeMinutes) {
            this.mood = mood;
            this.halfLifeMinutes = halfLifeMinutes;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The feeling a mood expresses, or null for calm. */
        public static Feeling of(VesselMood m) {
            for (Feeling f : values()) {
                if (f.mood == m) return f;
            }
            return null;
        }
    }

    /** Level at which a feeling shows in its body and voice. */
    public static final double SHOWS = 35;
    static final int CAUSES_KEPT = 6;

    /** Feeling id → level 0..100. */
    public Map<String, Double> levels = new LinkedHashMap<>();
    /** When the levels were last decayed. */
    public long updated;
    /** The latest things that moved it, newest last. */
    public List<Cause> causes = new ArrayList<>();

    public static final class Cause {
        public long at;
        public String feeling;
        public String why;

        Cause() {
        }

        Cause(long at, String feeling, String why) {
            this.at = at;
            this.feeling = feeling;
            this.why = why;
        }
    }

    // ---- Changing it -----------------------------------------------------------------------------

    /** Applies the fading since the last change. */
    public synchronized void decay(long now) {
        if (levels == null) levels = new LinkedHashMap<>();
        if (causes == null) causes = new ArrayList<>();
        if (updated <= 0 || now <= updated) {
            if (updated <= 0) updated = now;
            return;
        }
        double minutes = (now - updated) / 60_000.0;
        for (Feeling f : Feeling.values()) {
            Double v = levels.get(f.id());
            if (v == null) continue;
            double left = v * Math.pow(0.5, minutes / f.halfLifeMinutes);
            if (left < 1) levels.remove(f.id());
            else levels.put(f.id(), left);
        }
        updated = now;
    }

    /** Stirs a feeling by this much (negative to ease it). */
    public synchronized void feel(Feeling f, double amount, String why, long now) {
        if (f == null || amount == 0) return;
        decay(now);
        double v = Math.max(0, Math.min(100, level(f) + amount));
        if (v < 1) levels.remove(f.id());
        else levels.put(f.id(), v);
        // Feelings crowd each other out a little: joy cools wrath, wrath sours joy.
        if (amount > 0) {
            Feeling opposite = switch (f) {
                case WRATH -> Feeling.JOY;
                case JOY -> Feeling.WRATH;
                case GLORY -> Feeling.SORROW;
                case SORROW -> Feeling.GLORY;
                case DREAD -> null;
            };
            if (opposite != null && level(opposite) > 0) {
                double o = Math.max(0, level(opposite) - amount * 0.4);
                if (o < 1) levels.remove(opposite.id());
                else levels.put(opposite.id(), o);
            }
        }
        if (amount > 0 && why != null && !why.isBlank()) {
            causes.add(new Cause(now, f.id(), why.length() > 90 ? why.substring(0, 89) + "…" : why));
            while (causes.size() > CAUSES_KEPT) causes.remove(0);
        }
    }

    /** The god settles itself (it chose calm): every feeling halves. */
    public synchronized void soothe(long now) {
        decay(now);
        levels.replaceAll((k, v) -> v / 2);
        levels.values().removeIf(v -> v < 1);
    }

    /** Lifts a feeling to at least this level (the god chose to show it). */
    public synchronized void atLeast(Feeling f, double level, String why, long now) {
        decay(now);
        double gap = level - level(f);
        if (gap > 0) feel(f, gap, why, now);
    }

    /** How strongly the god may choose to feel (the feel power). */
    public static final List<String> STRENGTHS = List.of("faint", "stirring", "strong", "gone");

    /**
     * The god takes a feeling to heart by its own choice: faint (15, below what its body shows), stirring
     * (40) or strong (70), never lowering what it already feels; "gone" lets that feeling go. Returns
     * the feeling's level afterwards.
     */
    public synchronized double choose(Feeling f, String strength, String why, long now) {
        decay(now);
        switch (strength) {
            case "gone" -> levels.remove(f.id());
            case "faint" -> atLeast(f, 15, why, now);
            case "stirring" -> atLeast(f, 40, why, now);
            case "strong" -> atLeast(f, 70, why, now);
            default -> throw new IllegalArgumentException("unknown strength " + strength);
        }
        return level(f);
    }

    /** "faint", "stirring", "strong" or "consuming" for a level. */
    public static String wordFor(double level) {
        return word(level);
    }

    /** Clears every feeling (the operator's "calm its heart", a new god). */
    public synchronized void clear(long now) {
        levels.clear();
        causes.clear();
        updated = now;
    }

    // ---- Reading it -------------------------------------------------------------------------------

    public synchronized double level(Feeling f) {
        Double v = levels == null ? null : levels.get(f.id());
        return v == null ? 0 : v;
    }

    /** The strongest feeling right now, if it is strong enough to show; else null. */
    public synchronized Feeling dominant(long now) {
        decay(now);
        Feeling best = null;
        double top = SHOWS;
        for (Feeling f : Feeling.values()) {
            if (level(f) >= top) {
                best = f;
                top = level(f);
            }
        }
        return best;
    }

    /** The mood its body falls back to: the dominant feeling's, else calm. */
    public VesselMood mood(long now) {
        Feeling f = dominant(now);
        return f == null ? VesselMood.CALM : f.mood;
    }

    static String word(double level) {
        if (level >= 80) return "consuming";
        if (level >= 55) return "strong";
        if (level >= SHOWS) return "stirring";
        return "faint";
    }

    /**
     * For the god's perception: "strong wrath (62), faint joy (12)" and what caused them, or that it is
     * at peace.
     */
    public synchronized String describe(long now) {
        decay(now);
        List<String> parts = new ArrayList<>();
        List<Feeling> order = new ArrayList<>(List.of(Feeling.values()));
        order.sort((a, b) -> Double.compare(level(b), level(a)));
        for (Feeling f : order) {
            double v = level(f);
            if (v >= 5) parts.add(word(v) + " " + f.id() + " (" + Math.round(v) + ")");
        }
        StringBuilder sb = new StringBuilder();
        if (parts.isEmpty()) {
            sb.append("At peace: nothing stirs you.");
        } else {
            sb.append(String.join(", ", parts)).append('.');
            Feeling d = dominant(now);
            if (d != null) sb.append(" Your body and voice show ").append(d.mood.id()).append(" unless you choose a mood.");
        }
        List<String> why = whys(now, 4);
        if (!why.isEmpty()) sb.append("\nWhy: ").append(String.join("; ", why)).append('.');
        return sb.toString();
    }

    /** What moved it lately and still shows, newest first: "sorrow ← rain began to fall (3m ago)". */
    public synchronized List<String> whys(long now, int max) {
        decay(now);
        List<String> why = new ArrayList<>();
        for (int i = causes.size() - 1; i >= 0 && why.size() < max; i--) {
            Cause c = causes.get(i);
            Feeling f = parse(c.feeling);
            if (f == null || level(f) < 5) continue;
            why.add(c.feeling + " ← " + c.why + " (" + net.bigyous.gptgodmc.EventLogger.ago(java.time.Duration.ofMillis(now - c.at)) + ")");
        }
        return why;
    }

    /** One short line for the God Manager: "strong wrath, faint joy" or "at peace". */
    public synchronized List<String> summary(long now) {
        decay(now);
        List<String> out = new ArrayList<>();
        List<Feeling> order = new ArrayList<>(List.of(Feeling.values()));
        order.sort((a, b) -> Double.compare(level(b), level(a)));
        for (Feeling f : order) {
            double v = level(f);
            if (v >= 5) out.add(word(v) + " " + f.id() + " (" + Math.round(v) + ")");
        }
        return out;
    }

    public static Feeling parse(String id) {
        if (id == null) return null;
        try {
            return Feeling.valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---- What moves it ---------------------------------------------------------------------------

    static final Pattern INSULT = Pattern.compile("\\b(stupid|dumb|idiot|fake|fraud|useless|weak|pathetic|loser|trash|"
            + "garbage|shut up|hate you|i hate|you suck|sucks|go away|nobody cares|not real|false god|kill yourself|"
            + "fuck|f\\*ck|stfu|bitch|dick|lame|cringe|boring|clown)\\b", Pattern.CASE_INSENSITIVE);
    static final Pattern PRAISE = Pattern.compile("\\b(thank|thanks|thx|ty|praise|glory|worship|bless you|"
            + "love you|we love|amazing|awesome|great one|almighty|holy one|my lord|my god|hail|all hail|"
            + "you('| a)re the best|grateful|mercy)\\b", Pattern.CASE_INSENSITIVE);
    static final Pattern GLORIFY = Pattern.compile("\\b(praise|glory|worship|hail|almighty|bow|kneel)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * How an event moves it. Called with the memory lock held, from {@link Memory#noticeEvent}.
     *
     * @param kind     prayer, speech or event
     * @param addressed whether the words were aimed at the god
     * @param speaker  who did it (first name in the event), or null
     * @param favor    the god's favor toward the speaker (-10..10)
     * @param said     the words spoken, when it is speech or a prayer
     */
    public void moved(String kind, String text, boolean addressed, String speaker, int favor, String said, long now) {
        moved(kind, text, addressed, speaker, favor, said, now, 5, 5);
    }

    /**
     * @param pride  its pride dial (0..10): insults and strikes anger a proud god more (x0.5 at 0, x1.5 at 10)
     * @param vanity its vanity dial (0..10): praise, worship and offerings please a vain god more
     */
    public void moved(String kind, String text, boolean addressed, String speaker, int favor, String said, long now,
            int pride, int vanity) {
        moved(kind, text, addressed, speaker, favor, said, now, pride, vanity, 5);
    }

    /** @param wrath its wrath dial (0..10): a wrathful god is angered more by insults and strikes (x0.6 to x1.4) */
    public void moved(String kind, String text, boolean addressed, String speaker, int favor, String said, long now,
            int pride, int vanity, int wrath) {
        String who = speaker == null ? "a mortal" : speaker;
        double warmth = 1 + Math.max(0, favor) / 10.0; // it feels more for those it loves
        double proud = (0.5 + Math.max(0, Math.min(10, pride)) / 10.0)
                * (0.6 + Math.max(0, Math.min(10, wrath)) * 0.08);
        double vain = 0.5 + Math.max(0, Math.min(10, vanity)) / 10.0;
        switch (kind) {
            case "prayer", "speech" -> {
                if (text.contains("WOUNDED you with the Godslayer")) {
                    feel(Feeling.WRATH, 20 * proud, who + " wounded you with the Godslayer", now);
                    feel(Feeling.DREAD, 18, who + " wounded you with the Godslayer", now);
                    return;
                }
                if (text.contains("STRUCK your vessel")) {
                    feel(Feeling.WRATH, 22 * proud, who + " struck your vessel", now);
                    return;
                }
                if (text.contains(" OFFERING")) {
                    feel(Feeling.JOY, 10 * warmth * vain, who + " made you an offering", now);
                    return;
                }
                if (!addressed && kind.equals("speech")) return;
                String words = said == null ? text : said;
                if (INSULT.matcher(words).find()) {
                    feel(Feeling.WRATH, (favor >= 5 ? 10 : 18) * proud, who + " insulted you: \"" + clip(words) + "\"", now);
                } else if (PRAISE.matcher(words).find()) {
                    feel(Feeling.JOY, 8 * warmth * vain, who + " praised you", now);
                    if (GLORIFY.matcher(words).find()) feel(Feeling.GLORY, 8 * vain, who + " worships you", now);
                } else if (kind.equals("prayer")) {
                    feel(Feeling.JOY, 2, null, now); // being sought out pleases it a little
                }
            }
            case "event" -> {
                if (text.startsWith("DEATH: ")) {
                    if (favor >= 3) feel(Feeling.SORROW, 20 + favor * 3, who + ", whom you favor, died", now);
                    else if (favor <= -5) feel(Feeling.GLORY, 4, who + ", whom you despise, died", now);
                    else feel(Feeling.SORROW, 5, who + " died", now);
                    if (text.contains(" by ") || text.contains("slain")) feel(Feeling.DREAD, 6, "blood was spilled", now);
                }
            }
            default -> {
            }
        }
    }

    /** How its own acts move it: showing a mood keeps that feeling alive; calm settles it. */
    public void acted(String tool, String mood, long now) {
        if (!"vessel_mood".equals(tool) || mood == null) return;
        VesselMood m = VesselMood.parse(mood);
        if (m == null) return;
        if (m == VesselMood.CALM) {
            soothe(now);
            return;
        }
        atLeast(Feeling.of(m), 60, "you chose to be " + m.id(), now);
    }

    private static String clip(String s) {
        String t = s.replace('\n', ' ').trim();
        return t.length() > 40 ? t.substring(0, 39) + "…" : t;
    }
}
