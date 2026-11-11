package net.bigyous.gptgodmc.god;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.bigyous.gptgodmc.EventLogger;

/**
 * Free will: pastimes the god chooses for itself (building a home, wandering, tending a garden, visiting a
 * mortal, fishing, watching the sunset, working with its hands like a player, watching over the weak,
 * composing) and carries across thoughts. Its personality dials and heart weigh which whims suit it now
 * and whether it cares about mortals at all. Kept in {@link Memory#activity}; pure apart from the memory
 * it edits, so it is unit tested.
 */
public final class Initiative {
    private Initiative() {
    }

    /** How much the god follows its own will (initiative.level). */
    public enum Level {
        /** It only watches and answers; no pastimes. */
        WATCH(0),
        /** Pastimes in quiet moments. */
        SEMI(150),
        /** It lives by its own whims, often and between events. */
        FREE(75);

        /** Seconds between quiet thoughts while a pastime is underway (0 = no change). */
        public final int paceSeconds;

        Level(int paceSeconds) {
            this.paceSeconds = paceSeconds;
        }

        public static Level parse(String s) {
            if (s == null) return SEMI;
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "watch", "off", "none", "passive", "low" -> WATCH;
                case "free", "high", "full", "heavy" -> FREE;
                default -> SEMI;
            };
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What a god may do of its own accord, and how (with real powers). */
    public enum Kind {
        BUILD_HOME("build_home", "building a home",
                "pick a fine spot (survey_area), walk there (vessel_go), raise a house with build (a Designs "
                        + "building or blueprint, in your style), then decorate it with vessel_work place"),
        WANDER("wander", "wandering and exploring",
                "walk or fly somewhere you have not been (vessel_go), look around (look, survey_area), remark on "
                        + "what you find"),
        GARDEN("garden", "tending a garden",
                "choose a patch near your works, plant crops, flowers or saplings with vessel_work plant, come "
                        + "back to tend it"),
        VISIT("visit", "visiting a mortal",
                "walk to a mortal (vessel_go to them), face them and chat with vessel_say about their life and "
                        + "work"),
        FISH("fish", "fishing",
                "walk to water and fish with vessel_work fish; keep or give away what you catch"),
        SUNSET("sunset", "watching the sky",
                "find a high place facing west (vessel_go), sit (vessel_animate sit) and watch the sun go down; "
                        + "say a few quiet words if moved"),
        TINKER("tinker", "working with its hands",
                "act like a mortal: walk somewhere, dig or place a few blocks (vessel_work), build a small "
                        + "bench, path, lamp post or shrine"),
        WATCH_OVER("watch_over", "watching over the weak",
                "go to a weak or endangered mortal, check on them, help unasked (heal, food via give_item, "
                        + "light, shelter), then move on"),
        COMPOSE("compose", "composing music",
                "compose_song a tune that fits your mood, play_song it for whoever is near");

        public final String id, label, how;

        Kind(String id, String label, String how) {
            this.id = id;
            this.label = label;
            this.how = how;
        }

        public static Kind parse(String s) {
            if (s == null) return null;
            String k = s.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
            for (Kind x : values()) {
                if (x.id.equals(k)) return x;
            }
            return null;
        }

        public static List<String> ids() {
            List<String> out = new ArrayList<>();
            for (Kind k : values()) out.add(k.id);
            return out;
        }
    }

    /** One pastime underway or done. */
    public static final class Activity {
        public String kind;
        public String about;
        public String plan;
        /** A mortal or place it is for or about. */
        public String target;
        public long startedAt;
        public long touchedAt;
        public int steps;
        public List<String> notes = new ArrayList<>();
        /** active | done | set aside */
        public String status = "active";
        public long endedAt;

        public Kind kindOf() {
            Kind k = Kind.parse(kind);
            return k == null ? Kind.TINKER : k;
        }
    }

    /** What the god notices about this moment, for choosing a whim. */
    public record Context(boolean quiet, long dayTime, boolean raining, int mortals, List<String> weak,
            String favored) {
    }

    public static final class Refusal extends RuntimeException {
        public Refusal(String message) {
            super(message);
        }
    }

    // ---- Nature ---------------------------------------------------------------------------------

    /** A dial (0..10, 5 = neutral) as -1..1. */
    static double d(Map<String, Integer> dials, String key) {
        Integer v = dials == null ? null : dials.get(key);
        return ((v == null ? 5 : Math.max(0, Math.min(10, v))) - 5) / 5.0;
    }

    /** A feeling (0..100) as 0..1. */
    static double f(Map<String, Double> heart, String id) {
        Double v = heart == null ? null : heart.get(id);
        return v == null ? 0 : Math.max(0, Math.min(1, v / 100.0));
    }

    /**
     * How much the god cares about mortals, -1 (cold, they are not its concern) to 1 (tender, it helps
     * unasked). Generosity, mercy and meddling warm it; pride and wrath chill it; so does a heart full of
     * wrath, while joy and sorrow soften it.
     */
    public static double care(Map<String, Integer> dials, Map<String, Double> heart) {
        double c = 0.35 * d(dials, "generosity") + 0.3 * d(dials, "mercy") + 0.2 * d(dials, "meddling")
                + 0.15 * d(dials, "chattiness") - 0.25 * d(dials, "pride") - 0.2 * d(dials, "wrath")
                + 0.2 * f(heart, "joy") + 0.1 * f(heart, "sorrow") - 0.35 * f(heart, "wrath");
        return Math.max(-1, Math.min(1, c));
    }

    public static String careWord(double care) {
        if (care >= 0.35) return "caring";
        if (care >= 0.05) return "kindly";
        if (care > -0.3) return "aloof";
        return "cold";
    }

    static boolean dusk(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return (t >= 11000 && t <= 13200) || t >= 22800 || t <= 300;
    }

    static boolean night(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t > 13200 && t < 22800;
    }

    /** How much each pastime appeals now (no randomness). */
    public static Map<Kind, Double> weights(Map<String, Integer> dials, Map<String, Double> heart, Context ctx) {
        double care = care(dials, heart);
        double gen = d(dials, "generosity"), wrath = d(dials, "wrath"), chat = d(dials, "chattiness"),
                humor = d(dials, "humor"), meddle = d(dials, "meddling"), patience = d(dials, "patience"),
                pride = d(dials, "pride"), curious = d(dials, "curiosity"), vanity = d(dials, "vanity");
        double joy = f(heart, "joy"), sorrow = f(heart, "sorrow"), fury = f(heart, "wrath"),
                dread = f(heart, "dread"), glory = f(heart, "glory");
        boolean mortals = ctx.mortals() > 0;
        boolean weak = ctx.weak() != null && !ctx.weak().isEmpty();
        Map<Kind, Double> w = new EnumMap<>(Kind.class);
        w.put(Kind.BUILD_HOME, 1 + 0.6 * vanity + 0.4 * pride + 0.3 * patience + 0.5 * glory
                + (ctx.favored() != null && care > 0 ? 0.3 : 0));
        w.put(Kind.WANDER, 1 + 0.8 * curious - 0.3 * patience + 0.4 * fury - 0.3 * dread);
        w.put(Kind.GARDEN, 1 + 0.6 * patience + 0.3 * gen - 0.4 * wrath + 0.4 * joy + 0.3 * sorrow
                - (night(ctx.dayTime()) ? 0.5 : 0));
        w.put(Kind.VISIT, mortals ? 0.6 + 0.7 * chat + 0.8 * care + 0.4 * joy - 0.5 * fury : 0);
        w.put(Kind.FISH, 0.8 + 0.7 * patience - 0.4 * meddle + 0.2 * humor + 0.3 * sorrow + (ctx.raining() ? 0.2 : 0));
        w.put(Kind.SUNSET, (0.6 + 0.5 * patience + 0.2 * vanity + 0.6 * sorrow) * (dusk(ctx.dayTime()) ? 2.6 : 0.25));
        w.put(Kind.TINKER, 1 + 0.5 * curious + 0.4 * meddle + 0.3 * humor - 0.3 * pride);
        w.put(Kind.WATCH_OVER, !mortals ? 0 : (0.15 + 1.3 * care) * (weak && care > 0 ? 2.8 : 1));
        w.put(Kind.COMPOSE, 0.6 + 0.5 * humor + 0.4 * vanity + 0.5 * joy + 0.4 * sorrow);
        for (Map.Entry<Kind, Double> e : w.entrySet()) {
            e.setValue(Math.max(e.getKey() == Kind.VISIT || e.getKey() == Kind.WATCH_OVER ? 0 : 0.05, e.getValue()));
        }
        return w;
    }

    /** Pastimes from most to least appealing, with a whim (seeded noise) so the choice varies over time. */
    public static List<Kind> rank(Map<String, Integer> dials, Map<String, Double> heart, Context ctx, long seed) {
        Map<Kind, Double> w = weights(dials, heart, ctx);
        Map<Kind, Double> scored = new EnumMap<>(Kind.class);
        for (Map.Entry<Kind, Double> e : w.entrySet()) {
            double whim = 0.7 + 0.6 * noise(seed, e.getKey().ordinal());
            scored.put(e.getKey(), e.getValue() * whim);
        }
        List<Kind> out = new ArrayList<>(scored.keySet());
        out.removeIf(k -> scored.get(k) <= 0);
        out.sort((a, b) -> Double.compare(scored.get(b), scored.get(a)));
        return out;
    }

    private static double noise(long seed, int i) {
        long x = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L;
        x ^= x >>> 31;
        x *= 0x94D049BB133111EBL;
        x ^= x >>> 29;
        return (x >>> 11) / (double) (1L << 53);
    }

    // ---- The current pastime ----------------------------------------------------------------------

    public static Activity current(Memory m) {
        synchronized (m) {
            return m.activity != null && "active".equals(m.activity.status) ? m.activity : null;
        }
    }

    public static Activity begin(Memory m, Kind kind, String about, String plan, String target, long now) {
        if (kind == null) throw new Refusal("Unknown pastime. Choose one of: " + String.join(", ", Kind.ids()) + ".");
        synchronized (m) {
            Activity old = current(m);
            if (old != null) finish(m, old, "set aside", "set aside for " + kind.label, now);
            Activity a = new Activity();
            a.kind = kind.id;
            a.about = about == null || about.isBlank() ? kind.label : shorten(about.trim(), 80);
            a.plan = plan == null ? "" : shorten(plan.trim(), 300);
            a.target = target == null || target.isBlank() ? null : shorten(target.trim(), 60);
            a.startedAt = now;
            a.touchedAt = now;
            m.activity = a;
            return a;
        }
    }

    public static Activity progress(Memory m, String note, long now) {
        synchronized (m) {
            Activity a = current(m);
            if (a == null) throw new Refusal("You are doing nothing in particular; begin a pastime first.");
            a.steps++;
            a.touchedAt = now;
            if (note != null && !note.isBlank()) {
                a.notes.add(shorten(note.trim(), 160));
                while (a.notes.size() > 4) a.notes.remove(0);
            }
            return a;
        }
    }

    public static Activity end(Memory m, String note, long now) {
        synchronized (m) {
            Activity a = current(m);
            if (a == null) throw new Refusal("You are doing nothing in particular.");
            finish(m, a, "done", note, now);
            return a;
        }
    }

    private static void finish(Memory m, Activity a, String status, String note, long now) {
        a.status = status;
        a.endedAt = now;
        if (note != null && !note.isBlank()) {
            a.notes.add(shorten(note.trim(), 160));
            while (a.notes.size() > 4) a.notes.remove(0);
        }
        m.pastActivities.add(a);
        while (m.pastActivities.size() > 6) m.pastActivities.remove(0);
        if (m.activity == a) m.activity = null;
    }

    /** One line for /god status and the tab list: what it is doing now. */
    public static String line(Memory m, long now) {
        Activity a = current(m);
        if (a == null) return "nothing in particular";
        return a.kindOf().label + ": " + a.about + (a.target == null ? "" : " (" + a.target + ")") + ", for "
                + EventLogger.ago(Duration.ofMillis(now - a.startedAt)) + ", " + a.steps + " steps";
    }

    // ---- Perception -------------------------------------------------------------------------------

    /** Past this, the perception suggests finishing or changing pastime. */
    static final long LONG_MS = 25 * 60_000L;

    /**
     * The "## What you are doing" body: the pastime underway (continue it), or whims that suit its nature
     * now when it is idle; and how much it cares about mortals.
     */
    public static String render(Memory m, Map<String, Integer> dials, Map<String, Double> heart, Context ctx,
            Level level, long now) {
        if (level == Level.WATCH) return "";
        StringBuilder sb = new StringBuilder();
        double care = care(dials, heart);
        Activity a = current(m);
        if (a != null) {
            long age = now - a.startedAt;
            sb.append("- Now: ").append(a.kindOf().label).append(" — \"").append(a.about).append('"');
            if (a.target != null) sb.append(" (").append(a.target).append(')');
            sb.append(", for ").append(EventLogger.ago(Duration.ofMillis(age))).append(", ").append(a.steps)
                    .append(" steps so far.");
            if (a.plan != null && !a.plan.isEmpty()) sb.append(" Plan: ").append(a.plan);
            if (a.notes != null && !a.notes.isEmpty()) sb.append(" Last: ").append(a.notes.get(a.notes.size() - 1));
            sb.append('\n');
            if (ctx.quiet() || level == Level.FREE) {
                sb.append("  Carry it on now: take its next step with real powers (").append(a.kindOf().how)
                        .append("), then record it with activity progress. End it (activity end) when done");
                sb.append(age > LONG_MS ? "; you have been at it a while, so finish or change to another whim.\n"
                        : " or bored.\n");
            }
        } else if (ctx.quiet() || level == Level.FREE) {
            List<Kind> ranked = rank(dials, heart, ctx, now / (10 * 60_000L));
            sb.append("- You are doing nothing in particular. ").append(ctx.quiet()
                    ? "This is your own time: instead of merely watching, choose a pastime that fits your nature "
                            + "and mood, begin it (activity begin) and start it now. "
                    : "When nothing demands you, follow a whim. ")
                    .append("Whims that suit you now:\n");
            for (int i = 0; i < Math.min(3, ranked.size()); i++) {
                Kind k = ranked.get(i);
                sb.append("  ").append(i + 1).append(". ").append(k.id).append(" — ").append(k.how);
                if (k == Kind.WATCH_OVER && ctx.weak() != null && !ctx.weak().isEmpty()) {
                    sb.append(" (in need: ").append(String.join(", ", ctx.weak())).append(')');
                }
                if (k == Kind.BUILD_HOME && ctx.favored() != null && care > 0) {
                    sb.append(" (for yourself, or for ").append(ctx.favored()).append(", your favorite)");
                }
                sb.append('\n');
            }
            if (!m.pastActivities.isEmpty()) {
                Activity last = m.pastActivities.get(m.pastActivities.size() - 1);
                sb.append("  Last pastime: ").append(last.kindOf().label).append(" \"").append(last.about)
                        .append("\" (").append(last.status).append(' ')
                        .append(EventLogger.ago(Duration.ofMillis(now - last.endedAt))).append(" ago).\n");
            }
        }
        if (ctx.mortals() > 0) {
            String word = careWord(care);
            sb.append("- Your nature toward mortals: ").append(word).append(". ");
            switch (word) {
                case "caring" -> sb.append("Check on the weak and endangered and help them unasked");
                case "kindly" -> sb.append("Help mortals when it suits you, and notice those in trouble");
                case "aloof" -> sb.append("Mortals interest you only now and then; your own pursuits come first");
                default -> sb.append("Mortals are not your concern unless they call on you or offend you; go "
                        + "about your own business and ignore their small troubles");
            }
            if (ctx.weak() != null && !ctx.weak().isEmpty() && care > -0.3) {
                sb.append(" (struggling now: ").append(String.join(", ", ctx.weak())).append(')');
            }
            sb.append(".\n");
        }
        return sb.toString();
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
