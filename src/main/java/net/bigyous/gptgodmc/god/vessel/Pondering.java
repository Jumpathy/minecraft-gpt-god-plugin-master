package net.bigyous.gptgodmc.god.vessel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * What the god's body does while its mind works, so a thought that takes seconds (or a minute) reads
 * as effort in the world rather than as silence. Each thought gets a fresh plan, chosen from what woke
 * it (a blow, a prayer, a command, a death), its mood and its nature (the personality dials), with
 * chance and a memory of what it did last, so it never always does the same thing:
 * <ol>
 * <li>an <b>opening</b>: a short first reaction (it flinches and rises when struck, inclines its head
 * to a prayer, or simply turns), sometimes none;</li>
 * <li>a <b>working</b> pose it holds while it thinks (weaving its hands, weighing, brooding, arms to
 * the sky), with a particle <b>motif</b> (tendrils of light, gathering motes, a rune circle, sparks);</li>
 * <li>after some seconds the motif <b>swells</b> and a second joins it;</li>
 * <li>a long thought sinks into a <b>deep</b> working (floating cross-legged, or arms flung wide).</li>
 * </ol>
 * Animations the god wrote itself and saved as think_… or ponder_… join the working poses. Pure: the
 * poses are {@link Choreo} animations and the motifs are points, so all of it is unit tested and drawn
 * offline.
 */
public final class Pondering {
    private Pondering() {
    }

    /** Why the god began to think. */
    public enum Why {
        STRUCK, WOUNDED, PRAYER, SPOKEN, COMMAND, DEATH, DANGER, GODS, QUIET, WATCH, OTHER;

        /** The ways the game says a mortal died. */
        private static final java.util.regex.Pattern DEATHS = java.util.regex.Pattern.compile(
                "death|\\bdied\\b|slain|killed|drowned|blew up|blown up|was shot|burned|went up in flames|in lava|"
                        + "withered|suffocated|squashed|impaled|fireballed|pummeled|skewered|hit the ground|"
                        + "fell (from|off|out|while|into)|out of the world|kinetic energy|struck by lightning|starved|froze");

        /** Read from the thought's trigger (the attention reason). */
        public static Why of(String trigger) {
            String t = trigger == null ? "" : trigger.toLowerCase(Locale.ROOT);
            if (t.contains("wounded you")) return WOUNDED;
            if (t.contains("struck your vessel")) return STRUCK;
            if (t.contains("binding command") || t.contains("operator")) return COMMAND;
            if (t.contains("another god")) return GODS;
            if (t.contains("in danger")) return DANGER;
            if (t.contains("prays to you") || t.contains("offering") || t.contains("touched your vessel")) return PRAYER;
            if (t.contains(" spoke") || t.contains("addressed you")) return SPOKEN;
            if (t.contains("quiet moment")) return QUIET;
            if (t.contains("regular watch")) return WATCH;
            if (!t.contains("returned from death") && DEATHS.matcher(t).find()) return DEATH;
            return OTHER;
        }
    }

    /** What a point of a motif is drawn with. */
    public enum Ink {
        /** Dust in the god's own colour (its mood's, else its look's). */
        ACCENT, LIGHT, SPARK, EMBER, SMOKE, SOUL, GLYPH
    }

    /** Where a point hangs. */
    public enum Anchor {
        FEET, CHEST, HEAD, LHAND, RHAND
    }

    /** One particle: an offset from its anchor, in figure heights (1 = as tall as the body). */
    public record Dot(Anchor at, float x, float y, float z, Ink ink) {
    }

    /** One pose: a named animation. */
    public record Act(String id, Choreo.Anim anim) {
    }

    /**
     * The plan for one thought. Ticks count from the moment it began.
     *
     * @param opening the first reaction, or null
     * @param workAt  when the working pose and motif begin
     * @param swellAt when the motif grows and the second joins
     * @param deepAt  when the deep working begins
     */
    public record Plan(Why why, Act opening, Act working, Act deep, String motif, String second, int workAt,
            int swellAt, int deepAt, long seed) {
        /** 0 = opening, 1 = working, 2 = swelling, 3 = deep. */
        public int stage(long age) {
            return age < workAt ? 0 : age < swellAt ? 1 : age < deepAt ? 2 : 3;
        }
    }

    // ---- The library ---------------------------------------------------------------------------------

    private static final Map<String, String> OPENINGS = new LinkedHashMap<>();
    private static final Map<String, String> WORKINGS = new LinkedHashMap<>();
    private static final Map<String, String> DEEPS = new LinkedHashMap<>();

    static {
        // First reactions: a second or so, ending near rest.
        OPENINGS.put("flinch_rise", """
                once
                t=0 torso=0 head=0,0 rise=0 larm=10,8 rarm=10,8
                t=0.2 torso=-14 head=0,-10 larm=30,35 rarm=30,35 body=-8,0,0 ease=snap
                t=1 torso=-4 head=0,8 larm=20,30 rarm=20,30 body=0,0,0 rise=0.3 cape=55
                t=1.6 torso=0 head=0,10 larm=15,22 rarm=15,22 rise=0.4 cape=35
                """);
        OPENINGS.put("slow_turn", """
                once
                t=0 head=0,0 torso=0 larm=10,8 rarm=10,8
                t=0.6 head=0,-6 torso=-3 larm=12,10 rarm=12,10
                t=1.5 head=0,-9 torso=-4 larm=14,12 rarm=14,12 cape=25
                """);
        OPENINGS.put("shrug_off", """
                once
                t=0 rarm=10,8 head=0,0 torso=0
                t=0.35 rarm=70,-45,30 head=20,10 torso=2
                t=0.6 rarm=62,-38,30 head=20,12
                t=0.85 rarm=70,-45,30 head=20,10
                t=1.3 rarm=10,8 head=0,0 torso=0
                """);
        OPENINGS.put("flare", """
                once
                t=0 torso=0 larm=10,10 rarm=10,10 head=0,0 rise=0
                t=0.25 torso=10 larm=-20,45 rarm=-20,45 head=0,12 body=6,0,0 ease=snap
                t=0.9 torso=-10 larm=-25,60 rarm=-25,60 head=0,-18 body=0,0,0 rise=0.12 cape=85
                t=1.5 torso=-4 larm=-10,40 rarm=-10,40 head=0,-6 rise=0.1 cape=50
                """);
        OPENINGS.put("stagger", """
                once
                t=0 torso=0 head=0,0 rarm=10,8 larm=10,8 rise=0
                t=0.25 torso=28 head=0,25 rarm=78,-38,40 larm=25,30 rise=-0.06 ease=snap
                t=1 torso=20 head=0,20 rarm=78,-38,40 larm=20,25 rise=-0.04
                t=1.6 torso=6 head=0,4 rarm=60,-30,40 larm=12,12 rise=0
                """);
        OPENINGS.put("incline", """
                once
                t=0 head=0,0 torso=0 rarm=10,8
                t=0.5 head=8,10 torso=6 rarm=45,20,-30
                t=1.3 head=6,8 torso=5 rarm=40,18,-30
                """);
        OPENINGS.put("attend", """
                once
                t=0 head=0,0 torso=0 rarm=10,8
                t=0.4 head=0,22 torso=8 rarm=75,-35,40
                t=1 head=0,20 torso=8 rarm=75,-35,40
                t=1.5 head=0,0 torso=0 rarm=20,10
                """);
        OPENINGS.put("still", """
                once
                t=0 head=0,0 torso=0 larm=10,8 rarm=10,8 cape=20
                t=0.8 head=0,24 torso=6 larm=6,4 rarm=6,4 cape=4
                t=1.6 head=0,26 torso=7 larm=6,4 rarm=6,4 cape=2
                """);
        OPENINGS.put("start", """
                once
                t=0 head=0,0 torso=0 rarm=10,8
                t=0.15 head=0,-14 torso=-6 rarm=55,25 larm=25,20 ease=snap
                t=0.9 head=0,-8 torso=-3 rarm=45,20 larm=18,15
                t=1.4 head=0,-4 torso=-1 rarm=25,12 larm=12,10
                """);

        // Working poses: loops that end where they begin.
        WORKINGS.put("weave", """
                loop
                t=0 larm=70,20,20 rarm=95,10,-20 torso=4 head=0,12
                t=0.9 larm=95,10,20 rarm=70,20,-20 torso=4 head=0,14
                t=1.8 larm=70,20,20 rarm=95,10,-20 torso=4 head=0,12
                """);
        WORKINGS.put("gather", """
                loop
                t=0 larm=80,85 rarm=80,85 torso=-6 head=0,-8 cape=45
                t=1.6 larm=85,15,30 rarm=85,15,-30 torso=6 head=0,14 cape=25
                t=2.4 larm=85,15,30 rarm=85,15,-30 torso=6 head=0,14 cape=25
                t=4 larm=80,85 rarm=80,85 torso=-6 head=0,-8 cape=45
                """);
        WORKINGS.put("uplift", """
                loop
                t=0 larm=160,25 rarm=160,25 head=0,-28 torso=-8 rise=0.2 cape=60
                t=1.5 larm=170,35 rarm=150,20 head=6,-30 torso=-9 rise=0.26 cape=70
                t=3 larm=160,25 rarm=160,25 head=0,-28 torso=-8 rise=0.2 cape=60
                """);
        WORKINGS.put("scales", """
                loop
                t=0 larm=70,60,-40 rarm=50,60,40 head=-8,6 torso=2
                t=1.4 larm=50,60,-40 rarm=70,60,40 head=8,6 torso=2
                t=2.8 larm=70,60,-40 rarm=50,60,40 head=-8,6 torso=2
                """);
        WORKINGS.put("brood", """
                loop
                t=0 larm=55,-40,60 rarm=55,-40,-60 head=0,18 torso=5 cape=10
                t=2 larm=57,-40,60 rarm=53,-40,-60 head=5,20 torso=6 cape=14
                t=4 larm=55,-40,60 rarm=55,-40,-60 head=0,18 torso=5 cape=10
                """);
        WORKINGS.put("loom", """
                loop
                t=0 rise=0.4 larm=20,40 rarm=20,40 head=0,16 torso=4 cape=55
                t=2 rise=0.5 larm=24,46 rarm=24,46 head=0,18 torso=5 cape=70
                t=4 rise=0.4 larm=20,40 rarm=20,40 head=0,16 torso=4 cape=55
                """);
        WORKINGS.put("seethe", """
                loop
                t=0 larm=-15,35 rarm=-15,35 torso=8 head=0,10 rise=0.08
                t=0.5 larm=-20,42 rarm=-12,33 torso=10 head=-4,12 rise=0.1
                t=1 larm=-15,35 rarm=-15,35 torso=8 head=0,10 rise=0.08
                """);
        WORKINGS.put("ponder", """
                loop
                t=0 rarm=120,-25,35 larm=60,-35,50 head=6,10 torso=3
                t=1.8 rarm=122,-25,35 larm=60,-35,50 head=-6,6 torso=3
                t=3.6 rarm=120,-25,35 larm=60,-35,50 head=6,10 torso=3
                """);
        WORKINGS.put("trace", """
                loop
                t=0 rarm=110,10 head=8,-6 larm=20,12
                t=0.6 rarm=130,35 head=10,-12
                t=1.2 rarm=110,55 head=6,-6
                t=1.8 rarm=90,30 head=8,0
                t=2.4 rarm=110,10 head=8,-6 larm=20,12
                """);
        WORKINGS.put("listen", """
                loop
                t=0 rarm=60,25,-35 head=10,8 torso=5
                t=2 rarm=64,28,-35 head=14,6 torso=6
                t=4 rarm=60,25,-35 head=10,8 torso=5
                """);
        WORKINGS.put("mourn", """
                loop
                t=0 rarm=75,-35,40 larm=12,10 head=0,28 torso=8 cape=5
                t=2.5 rarm=76,-35,40 larm=12,10 head=0,31 torso=9 cape=8
                t=5 rarm=75,-35,40 larm=12,10 head=0,28 torso=8 cape=5
                """);
        WORKINGS.put("gaze", """
                loop
                t=0 head=0,-32 larm=8,6 rarm=8,6 torso=-5
                t=3 head=14,-36 larm=8,6 rarm=8,6 torso=-6
                t=6 head=0,-32 larm=8,6 rarm=8,6 torso=-5
                """);

        // Deep workings, for thoughts that run long.
        DEEPS.put("channel", """
                loop
                t=0 rise=0.3 lleg=85,25 rleg=85,25 larm=45,60,-50 rarm=45,60,50 head=0,6 torso=-2 cape=40
                t=3 rise=0.38 lleg=88,25 rleg=82,25 larm=50,64,-50 rarm=50,64,50 head=0,2 torso=-3 cape=55
                t=6 rise=0.3 lleg=85,25 rleg=85,25 larm=45,60,-50 rarm=45,60,50 head=0,6 torso=-2 cape=40
                """);
        DEEPS.put("invoke", """
                loop
                t=0 rise=0.55 larm=135,70 rarm=135,70 head=0,-35 torso=-12 cape=85
                t=2.5 rise=0.65 larm=145,80 rarm=145,80 head=0,-40 torso=-14 cape=100
                t=5 rise=0.55 larm=135,70 rarm=135,70 head=0,-35 torso=-12 cape=85
                """);
        DEEPS.put("storm", """
                loop
                t=0 rise=0.35 larm=165,30 rarm=165,30 head=0,-20 torso=-6 cape=90
                t=0.4 rise=0.37 larm=170,36 rarm=160,26 head=-5,-22 torso=-7 cape=100
                t=0.8 rise=0.35 larm=165,30 rarm=165,30 head=0,-20 torso=-6 cape=90
                """);
    }

    public static final List<String> MOTIFS = List.of("tendrils", "gather", "runes", "orbit", "halo", "sparks",
            "embers", "motes", "stars", "mist");

    private static final Map<String, Act> ACTS = new LinkedHashMap<>();

    static {
        for (Map<String, String> m : List.of(OPENINGS, WORKINGS, DEEPS)) {
            m.forEach((id, text) -> ACTS.put(id, new Act(id, Choreo.fromText(id, text))));
        }
    }

    public static Collection<Act> acts() {
        return ACTS.values();
    }

    public static Act act(String id) {
        return ACTS.get(id);
    }

    public static List<String> openings() {
        return List.copyOf(OPENINGS.keySet());
    }

    public static List<String> workings() {
        return List.copyOf(WORKINGS.keySet());
    }

    public static List<String> deeps() {
        return List.copyOf(DEEPS.keySet());
    }

    /** Whether an animation the god saved is meant for thinking (its name says so). */
    public static boolean isOwn(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("think") || n.startsWith("ponder");
    }

    // ---- Choosing --------------------------------------------------------------------------------------

    /** 0.4 for a dial at 0, 1.0 at 5, 1.6 at 10. */
    static double lean(Map<String, Integer> dials, String dial) {
        int d = dials == null ? 5 : dials.getOrDefault(dial, 5);
        return 0.4 + Math.max(0, Math.min(10, d)) * 0.12;
    }

    private static void put(Map<String, Double> m, String id, double w) {
        if (w > 0) m.merge(id, w, Double::sum);
    }

    private static void scale(Map<String, Double> m, String id, double by) {
        m.computeIfPresent(id, (k, v) -> v * by);
    }

    /** How likely each opening is ("" = none). */
    static Map<String, Double> openingWeights(Why why, VesselMood mood, Map<String, Integer> dials) {
        Map<String, Double> w = new LinkedHashMap<>();
        double wrath = lean(dials, "wrath"), pride = lean(dials, "pride"), humor = lean(dials, "humor");
        double patience = lean(dials, "patience"), mercy = lean(dials, "mercy"), curiosity = lean(dials, "curiosity");
        switch (why) {
            case STRUCK -> {
                put(w, "flinch_rise", 1.0);
                put(w, "slow_turn", 0.9 * pride);
                put(w, "shrug_off", 0.5 * humor * patience);
                put(w, "flare", 0.9 * wrath);
                put(w, "", 0.3);
            }
            case WOUNDED -> {
                put(w, "stagger", 1.5);
                put(w, "flare", 0.9 * wrath);
                put(w, "flinch_rise", 0.7);
            }
            case PRAYER, SPOKEN -> {
                put(w, "incline", 1.0 * curiosity);
                put(w, "", 0.8);
            }
            case COMMAND -> {
                put(w, "attend", 1.5);
                put(w, "incline", 0.5);
                put(w, "", 0.3);
            }
            case DEATH -> {
                put(w, "still", 1.0 * mercy);
                put(w, "", 0.7);
            }
            case DANGER -> {
                put(w, "start", 1.3);
                put(w, "", 0.4);
            }
            case GODS -> {
                put(w, "slow_turn", 1.0);
                put(w, "", 1.0);
            }
            default -> put(w, "", 1.0);
        }
        if (mood == VesselMood.WRATHFUL) {
            scale(w, "flare", 2.2);
            scale(w, "shrug_off", 0.2);
        }
        if (mood == VesselMood.JOYFUL) scale(w, "shrug_off", 2.0);
        if (mood == VesselMood.SORROWFUL) scale(w, "still", 1.8);
        return w;
    }

    /** How likely each working pose is. */
    static Map<String, Double> workingWeights(Why why, VesselMood mood, Map<String, Integer> dials) {
        Map<String, Double> w = new LinkedHashMap<>();
        double wrath = lean(dials, "wrath"), pride = lean(dials, "pride"), humor = lean(dials, "humor");
        double patience = lean(dials, "patience"), mercy = lean(dials, "mercy"), curiosity = lean(dials, "curiosity");
        double stingy = 2.0 - lean(dials, "generosity");
        switch (why) {
            case STRUCK, WOUNDED -> {
                put(w, "loom", 1.0 * pride);
                put(w, "seethe", 1.2 * wrath);
                put(w, "brood", 0.8 * patience);
                put(w, "ponder", 0.6 * humor);
                put(w, "scales", 0.8 * mercy);
                put(w, "weave", 0.6);
            }
            case PRAYER, SPOKEN -> {
                put(w, "listen", 1.0);
                put(w, "scales", 1.0 * mercy);
                put(w, "ponder", 1.0 * curiosity);
                put(w, "weave", 0.8);
                put(w, "trace", 0.7);
                put(w, "brood", 0.5 * stingy);
            }
            case COMMAND -> {
                put(w, "weave", 1.2);
                put(w, "gather", 1.2);
                put(w, "uplift", 1.0);
                put(w, "trace", 1.0);
            }
            case DEATH -> {
                put(w, "mourn", 1.5 * mercy);
                put(w, "brood", 1.0);
                put(w, "gaze", 0.5);
            }
            case DANGER -> {
                put(w, "gather", 1.0);
                put(w, "weave", 1.0);
                put(w, "loom", 0.8);
            }
            case GODS -> {
                put(w, "loom", 1.0 * pride);
                put(w, "brood", 1.0);
                put(w, "ponder", 0.8);
            }
            default -> {
                put(w, "gaze", 1.0 * curiosity);
                put(w, "ponder", 1.0);
                put(w, "trace", 0.6);
                put(w, "brood", 0.5);
            }
        }
        switch (mood == null ? VesselMood.CALM : mood) {
            case WRATHFUL -> {
                put(w, "seethe", 0.6);
                scale(w, "seethe", 2.5);
                scale(w, "loom", 1.5);
                scale(w, "listen", 0.4);
            }
            case JOYFUL -> {
                put(w, "uplift", 0.4);
                scale(w, "uplift", 1.8);
                scale(w, "trace", 1.4);
                scale(w, "brood", 0.3);
            }
            case SORROWFUL -> {
                put(w, "mourn", 0.4);
                scale(w, "mourn", 2.0);
                scale(w, "brood", 1.8);
                scale(w, "uplift", 0.3);
            }
            case OMINOUS -> {
                put(w, "loom", 0.5);
                scale(w, "loom", 2.0);
                scale(w, "brood", 1.5);
                scale(w, "gather", 1.3);
            }
            case EXALTED -> {
                put(w, "uplift", 0.6);
                scale(w, "uplift", 2.2);
                scale(w, "gather", 1.3);
            }
            default -> {
            }
        }
        return w;
    }

    static Map<String, Double> deepWeights(Why why, VesselMood mood, Map<String, Integer> dials) {
        Map<String, Double> w = new LinkedHashMap<>();
        put(w, "channel", 1.0);
        put(w, "invoke", why == Why.COMMAND ? 1.4 : 0.9);
        put(w, "storm", 0.5 * lean(dials, "wrath"));
        if (mood == VesselMood.WRATHFUL) scale(w, "storm", 4.0);
        if (mood == VesselMood.EXALTED || mood == VesselMood.JOYFUL) scale(w, "invoke", 2.0);
        if (mood == VesselMood.SORROWFUL || mood == VesselMood.CALM || mood == null) scale(w, "channel", 1.6);
        return w;
    }

    /** The particle motifs that suit a working pose, most fitting first. */
    static List<String> motifsFor(String working, VesselMood mood) {
        boolean wrath = mood == VesselMood.WRATHFUL;
        return switch (working) {
            case "weave" -> List.of("tendrils", "runes");
            case "gather" -> List.of("gather", "runes");
            case "uplift" -> List.of("halo", "motes");
            case "scales" -> List.of("orbit", "motes");
            case "brood" -> wrath ? List.of("embers", "mist") : List.of("mist", "embers");
            case "loom" -> wrath ? List.of("sparks", "mist") : List.of("mist", "runes");
            case "seethe" -> List.of("sparks", "embers");
            case "ponder" -> List.of("stars", "motes");
            case "trace" -> List.of("runes", "tendrils");
            case "listen" -> List.of("motes", "halo");
            case "mourn" -> List.of("motes", "mist");
            case "gaze" -> List.of("stars", "motes");
            default -> List.of(moodMotif(mood), "tendrils");
        };
    }

    /** The motif of a mood, for poses with none of their own and as the second that joins. */
    static String moodMotif(VesselMood mood) {
        return switch (mood == null ? VesselMood.CALM : mood) {
            case WRATHFUL -> "sparks";
            case JOYFUL -> "motes";
            case SORROWFUL -> "mist";
            case OMINOUS -> "runes";
            case EXALTED -> "halo";
            default -> "tendrils";
        };
    }

    private static String pick(Map<String, Double> weights, Collection<String> recent, SplittableRandom rnd) {
        Map<String, Double> w = new LinkedHashMap<>(weights);
        // What it did lately is much less likely, so it does not repeat itself.
        if (recent != null && w.size() > 1) {
            for (String r : recent) scale(w, r, 0.15);
        }
        double total = 0;
        for (double v : w.values()) total += v;
        if (total <= 0) return w.isEmpty() ? "" : w.keySet().iterator().next();
        double at = rnd.nextDouble() * total;
        String last = "";
        for (Map.Entry<String, Double> e : w.entrySet()) {
            last = e.getKey();
            at -= e.getValue();
            if (at <= 0) return last;
        }
        return last;
    }

    /**
     * Plans what the body does for one thought.
     *
     * @param complex the thought is likely long (high effort, a command): the working begins sooner
     * @param recent  the ids of what it did in its last few thoughts
     * @param own     animations the god saved for thinking (think_…, ponder_…), which join the working poses
     */
    public static Plan choose(Why why, VesselMood mood, Map<String, Integer> dials, boolean complex,
            Collection<String> recent, Collection<Choreo.Anim> own, long seed) {
        // Well mixed, so thoughts that begin close together still choose differently.
        SplittableRandom rnd = new SplittableRandom(seed);
        String open = pick(openingWeights(why, mood, dials), recent, rnd);
        Map<String, Double> ww = workingWeights(why, mood, dials);
        Map<String, Act> mine = new LinkedHashMap<>();
        if (own != null) {
            for (Choreo.Anim a : own) {
                if (a == null || !a.loop()) continue; // a working pose is held, so it must loop
                String id = "own:" + a.name();
                mine.put(id, new Act(id, a));
                put(ww, id, 2.0);
            }
        }
        String work = pick(ww, recent, rnd);
        String deep = pick(deepWeights(why, mood, dials), recent, rnd);
        List<String> motifs = motifsFor(work, mood);
        String motif = motifs.get(rnd.nextDouble() < 0.7 ? 0 : 1);
        String second = moodMotif(mood);
        if (second.equals(motif)) second = motifs.get(0).equals(motif) ? motifs.get(1) : motifs.get(0);
        boolean quiet = why == Why.QUIET || why == Why.WATCH || why == Why.OTHER;
        int workAt = quiet ? 80 : complex ? 30 : 50;
        Act opening = open.isEmpty() ? null : ACTS.get(open);
        Act working = mine.containsKey(work) ? mine.get(work) : ACTS.get(work);
        return new Plan(why, opening, working, ACTS.get(deep), motif, second, workAt, workAt + 140, workAt + 400, seed);
    }

    // ---- Motifs ----------------------------------------------------------------------------------------

    private static final float TAU = (float) (Math.PI * 2);

    /** A steady pseudo-random number in 0..1 for (seed, i). */
    static float hash(long seed, int i) {
        long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    /**
     * The particles of a motif at a tick. intensity 1 is the working stage, 2 the swell, 3 the deep
     * working. At most {@link #MAX_DOTS} points.
     */
    public static List<Dot> motif(String name, long tick, int intensity, long seed) {
        List<Dot> out = new ArrayList<>();
        int k = Math.max(1, Math.min(3, intensity));
        float t = tick;
        switch (name == null ? "" : name) {
            case "tendrils" -> {
                // Strands of light spiral up out of the ground round the body.
                int strands = 2 + k;
                for (int s = 0; s < strands; s++) {
                    float p = ((t + s * 60f / strands) % 60) / 60f;
                    for (int j = 0; j < 5; j++) {
                        float pj = p - j * 0.035f;
                        if (pj < 0) break;
                        float a = s * TAU / strands + pj * TAU * 1.4f + t * 0.012f;
                        float r = 0.62f - 0.3f * pj;
                        out.add(new Dot(Anchor.FEET, (float) Math.cos(a) * r, pj * 1.35f, (float) Math.sin(a) * r,
                                j == 0 ? Ink.LIGHT : Ink.ACCENT));
                    }
                }
            }
            case "gather" -> {
                // Motes fall inward from all sides into the chest.
                int n = 5 * k;
                for (int i = 0; i < n; i++) {
                    float p = ((t + hash(seed, i) * 50) % 50) / 50f;
                    float az = hash(seed, i + 100) * TAU, el = (hash(seed, i + 200) - 0.35f) * 1.6f;
                    float r = 1.15f * (float) Math.pow(1 - p, 1.4) + 0.05f;
                    out.add(new Dot(Anchor.CHEST, (float) (Math.cos(az) * Math.cos(el)) * r, (float) Math.sin(el) * r,
                            (float) (Math.sin(az) * Math.cos(el)) * r, p > 0.82f ? Ink.LIGHT : Ink.ACCENT));
                }
            }
            case "runes" -> {
                // A turning circle on the ground with a figure inside it, and glyphs drifting up.
                float r = 0.62f + 0.14f * k;
                int n = 10 + 4 * k;
                for (int i = 0; i < n; i++) {
                    if ((i + tick / 2) % 2 != 0) continue; // half each time, the eye joins them
                    float a = i * TAU / n + t * 0.02f;
                    out.add(new Dot(Anchor.FEET, (float) Math.cos(a) * r, 0.03f, (float) Math.sin(a) * r, Ink.ACCENT));
                }
                int sides = 3 + (int) (Math.abs(seed) % 3);
                for (int e = 0; e < sides; e++) {
                    float a0 = e * TAU / sides - t * 0.015f, a1 = (e + 1) * TAU / sides - t * 0.015f;
                    for (int j = 0; j < 3; j++) {
                        float f = (j + (tick / 2 % 2) * 0.5f) / 3f;
                        float x = (float) (Math.cos(a0) * (1 - f) + Math.cos(a1) * f) * r * 0.86f;
                        float z = (float) (Math.sin(a0) * (1 - f) + Math.sin(a1) * f) * r * 0.86f;
                        out.add(new Dot(Anchor.FEET, x, 0.03f, z, Ink.ACCENT));
                    }
                }
                for (int i = 0; i < k; i++) {
                    float a = hash(seed + tick / 6, i) * TAU, rr = hash(seed + tick / 6, i + 9) * r;
                    out.add(new Dot(Anchor.FEET, (float) Math.cos(a) * rr, 0.1f + hash(seed + tick / 6, i + 5) * 0.5f,
                            (float) Math.sin(a) * rr, Ink.GLYPH));
                }
            }
            case "orbit" -> {
                // A mote above each palm, ringed by smaller ones: weighing one thing against another.
                for (Anchor hand : new Anchor[] { Anchor.LHAND, Anchor.RHAND }) {
                    float bob = (float) Math.sin(t * 0.09f + (hand == Anchor.LHAND ? 0 : Math.PI)) * 0.04f;
                    out.add(new Dot(hand, 0, 0.1f + bob, 0, Ink.LIGHT));
                    int n = 1 + k;
                    for (int i = 0; i < n; i++) {
                        float a = t * 0.2f + i * TAU / n + (hand == Anchor.LHAND ? 0 : 1.3f);
                        out.add(new Dot(hand, (float) Math.cos(a) * 0.09f, 0.1f + bob, (float) Math.sin(a) * 0.09f,
                                Ink.ACCENT));
                    }
                }
            }
            case "halo" -> {
                // A ring over the head, and rings that open outward from it.
                int n = 8 + 2 * k;
                for (int i = 0; i < n; i++) {
                    float a = i * TAU / n + t * 0.05f;
                    out.add(new Dot(Anchor.HEAD, (float) Math.cos(a) * 0.2f, 0.22f, (float) Math.sin(a) * 0.2f, Ink.ACCENT));
                }
                float p = (t % 40) / 40f;
                float r = 0.2f + p * (0.3f + 0.15f * k);
                int m = 6 + 3 * k;
                for (int i = 0; i < m; i++) {
                    float a = i * TAU / m - t * 0.03f;
                    out.add(new Dot(Anchor.HEAD, (float) Math.cos(a) * r, 0.22f + p * 0.12f, (float) Math.sin(a) * r,
                            Ink.LIGHT));
                }
            }
            case "sparks" -> {
                // Crackles along the arms and shoulders; embers at the fists.
                int n = 2 + 2 * k;
                long beat = tick / 2;
                for (int i = 0; i < n; i++) {
                    float x = (hash(seed + beat, i) - 0.5f) * 0.9f, y = (hash(seed + beat, i + 40) - 0.3f) * 0.6f;
                    float z = (hash(seed + beat, i + 80) - 0.5f) * 0.6f;
                    out.add(new Dot(Anchor.CHEST, x, y, z, Ink.SPARK));
                }
                out.add(new Dot(Anchor.LHAND, 0, 0.02f, 0, Ink.EMBER));
                out.add(new Dot(Anchor.RHAND, 0, 0.02f, 0, Ink.EMBER));
            }
            case "embers" -> {
                // Embers climb out of the ground round its feet, with a little smoke.
                int n = 3 * k;
                for (int i = 0; i < n; i++) {
                    float p = ((t + hash(seed, i) * 70) % 70) / 70f;
                    float a = hash(seed, i + 30) * TAU, r = 0.25f + hash(seed, i + 60) * 0.5f;
                    float sway = (float) Math.sin(t * 0.1f + i) * 0.05f;
                    out.add(new Dot(Anchor.FEET, (float) Math.cos(a) * r + sway, p * 1.4f, (float) Math.sin(a) * r,
                            Ink.EMBER));
                }
                if (tick % 6 == 0) out.add(new Dot(Anchor.FEET, 0, 0.05f, 0, Ink.SMOKE));
            }
            case "motes" -> {
                // Slow sparkles sink through the air round it.
                int n = 3 * k;
                for (int i = 0; i < n; i++) {
                    float p = ((t + hash(seed, i) * 90) % 90) / 90f;
                    float a = hash(seed, i + 30) * TAU + t * 0.004f, r = 0.35f + hash(seed, i + 60) * 0.6f;
                    out.add(new Dot(Anchor.FEET, (float) Math.cos(a) * r, 1.5f - p * 1.3f, (float) Math.sin(a) * r,
                            i % 3 == 0 ? Ink.LIGHT : Ink.ACCENT));
                }
            }
            case "stars" -> {
                // A small constellation it studies above its head: fixed for the thought, twinkling.
                int n = 4 + 2 * k;
                for (int i = 0; i < n; i++) {
                    if (hash(seed + tick / 4, i) < 0.35f) continue; // twinkle
                    float x = (hash(seed, i) - 0.5f) * 0.9f, z = (hash(seed, i + 30) - 0.5f) * 0.5f + 0.25f;
                    float y = 0.25f + hash(seed, i + 60) * 0.35f;
                    out.add(new Dot(Anchor.HEAD, x, y, z, i % 2 == 0 ? Ink.LIGHT : Ink.ACCENT));
                }
            }
            case "mist" -> {
                // Smoke pools and turns at its feet; now and then a wisp of soul-light rises.
                int n = 4 + 2 * k;
                for (int i = 0; i < n; i++) {
                    if ((i + tick / 2) % 2 != 0) continue;
                    float a = i * TAU / n + t * 0.015f, r = 0.5f + 0.12f * k + (float) Math.sin(t * 0.05f + i) * 0.08f;
                    out.add(new Dot(Anchor.FEET, (float) Math.cos(a) * r, 0.06f, (float) Math.sin(a) * r,
                            i % 3 == 0 ? Ink.SMOKE : Ink.ACCENT));
                }
                for (int i = 0; i < k; i++) {
                    float p = ((t + hash(seed, i) * 80) % 80) / 80f;
                    float a = hash(seed, i + 30) * TAU;
                    out.add(new Dot(Anchor.FEET, (float) Math.cos(a) * 0.45f, p * 1.1f, (float) Math.sin(a) * 0.45f, Ink.SOUL));
                }
            }
            default -> {
            }
        }
        return out.size() > MAX_DOTS ? out.subList(0, MAX_DOTS) : out;
    }

    /** The most particles one motif draws at once. */
    public static final int MAX_DOTS = 40;
}
