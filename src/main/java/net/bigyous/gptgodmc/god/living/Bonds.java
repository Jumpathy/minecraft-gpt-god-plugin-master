package net.bigyous.gptgodmc.god.living;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * How one god regards each other god of its world: a score from -100 (sworn enemies) to 100 (true allies),
 * a short "why", the latest things that moved it, and the pact the two share (alliance, truce or holy war).
 * Each god keeps its own view in memory.json (they need not agree); pacts are written to both sides by
 * {@link Rivalry}. Pure and thread-safe, so it is unit tested.
 */
public final class Bonds {
    public static final int MIN = -100, MAX = 100;
    static final int CHANGES_KEPT = 6;

    /** Pacts between two gods (both sides hold the same one). */
    public enum Pact {
        NONE, ALLIANCE, TRUCE, WAR;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Pact parse(String s) {
            if (s == null) return NONE;
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return NONE;
            }
        }
    }

    /** One other god, as this god sees it. */
    public static final class Bond {
        public String name;
        public int score;
        public String why;
        public long since;
        public long updated;
        public String pact = "none";
        public long pactSince;
        /** A truce lapses on its own; 0 = until broken. */
        public long pactUntil;
        /** What the other god has offered and this one has not answered: alliance or truce. */
        public String offered;
        public long offeredAt;
        public long lastSabotage;
        public List<Change> changes = new ArrayList<>();

        public Pact pact(long now) {
            Pact p = Pact.parse(pact);
            if (p == Pact.TRUCE && pactUntil > 0 && now >= pactUntil) return Pact.NONE;
            return p;
        }
    }

    public static final class Change {
        public long at;
        public int delta;
        public String why;

        Change() {
        }

        Change(long at, int delta, String why) {
            this.at = at;
            this.delta = delta;
            this.why = why;
        }
    }

    /** Other god's realm id → bond. */
    public Map<String, Bond> with = new LinkedHashMap<>();

    // ---- Words ---------------------------------------------------------------------------------------

    /** The plain word for a score. */
    public static String stance(int score) {
        if (score <= -60) return "sworn enemy";
        if (score <= -20) return "rival";
        if (score < -5) return "wary";
        if (score <= 5) return "neutral";
        if (score < 20) return "cordial";
        if (score < 60) return "friend";
        return "ally";
    }

    /** Operator presets ("make them friends") → score. Null for an unknown word. */
    public static Integer preset(String word) {
        if (word == null) return null;
        return switch (word.trim().toLowerCase(Locale.ROOT)) {
            case "allies", "ally", "allied" -> 80;
            case "friends", "friend", "friendly" -> 40;
            case "neutral", "reset", "strangers" -> 0;
            case "rivals", "rival", "rivalry" -> -40;
            case "enemies", "enemy", "foes", "hostile" -> -80;
            default -> null;
        };
    }

    public static int clamp(int v) {
        return Math.max(MIN, Math.min(MAX, v));
    }

    // ---- Changing ------------------------------------------------------------------------------------

    /** The bond with this god, created neutral if new. */
    public synchronized Bond of(String id, String name, long now) {
        if (with == null) with = new LinkedHashMap<>();
        Bond b = with.computeIfAbsent(id.toLowerCase(Locale.ROOT), k -> {
            Bond n = new Bond();
            n.since = now;
            n.updated = now;
            return n;
        });
        if (name != null && !name.isBlank()) b.name = name;
        if (b.changes == null) b.changes = new ArrayList<>();
        return b;
    }

    /** The bond if there is one, else null. */
    public synchronized Bond find(String id) {
        return with == null || id == null ? null : with.get(id.toLowerCase(Locale.ROOT));
    }

    public synchronized int score(String id) {
        Bond b = find(id);
        return b == null ? 0 : b.score;
    }

    /** Moves the regard by delta (clamped) and remembers why. Returns the new score. */
    public synchronized int move(String id, String name, int delta, String why, long now) {
        Bond b = of(id, name, now);
        int before = b.score;
        b.score = clamp(b.score + delta);
        int moved = b.score - before;
        if (moved == 0) return b.score;
        b.updated = now;
        String w = clip(why, 100);
        if (!w.isEmpty()) {
            if (Math.abs(delta) >= 5 || b.why == null || b.why.isBlank()) b.why = w;
            b.changes.add(new Change(now, moved, w));
            while (b.changes.size() > CHANGES_KEPT) b.changes.remove(0);
        }
        return b.score;
    }

    /** Sets the regard outright (the god's own choice, or the operator). */
    public synchronized int set(String id, String name, int score, String why, long now) {
        Bond b = of(id, name, now);
        return move(id, name, clamp(score) - b.score, why, now);
    }

    public synchronized void setPact(String id, String name, Pact pact, long until, long now) {
        Bond b = of(id, name, now);
        b.pact = pact.id();
        b.pactSince = now;
        b.pactUntil = pact == Pact.TRUCE ? until : 0;
        b.offered = null;
        b.offeredAt = 0;
    }

    public synchronized void offer(String id, String name, Pact pact, long now) {
        Bond b = of(id, name, now);
        b.offered = pact.id();
        b.offeredAt = now;
    }

    /** Forgets a god that is gone. */
    public synchronized void drop(String id) {
        if (with != null && id != null) with.remove(id.toLowerCase(Locale.ROOT));
    }

    // ---- What moves it on its own --------------------------------------------------------------------

    /** How a rival's act moves this god: a delta (0 = not at all) and why. */
    public record Effect(int delta, String why) {
        public static final Effect NONE = new Effect(0, "");
    }

    static final Pattern INSULT = Pattern.compile("\\b(weak|pathetic|fraud|false|feeble|fool|coward|lesser|"
            + "pretender|usurper|worm|nothing|joke|liar|upstart|petty|mock|laugh at|kneel before me|bow to me|"
            + "beneath me|stupid|fake|useless|loser)\\b", Pattern.CASE_INSENSITIVE);
    static final Pattern PRAISE = Pattern.compile("\\b(friend|ally|sister|brother|kin|honou?r|respect|"
            + "together|peace|wise|mighty|glorious|great|thank|blessed|admire|praise)\\b", Pattern.CASE_INSENSITIVE);

    static final java.util.Set<String> HARM = java.util.Set.of("smite", "explode", "fling", "meteor", "take_item",
            "mark", "earthquake");
    static final java.util.Set<String> GIFTS = java.util.Set.of("give_item", "supply_chest", "heal", "grant_xp",
            "revive", "apply_effect", "aura", "bestow_title", "empower", "joint_blessing");

    /**
     * How another god's act moves this god's regard for it.
     *
     * @param tool        what the other god did
     * @param words       what it said (announce, vessel_say, commune), or null
     * @param namesMe     whether its words name this god
     * @param victim      the mortal the act touched, or null
     * @param victimFavor this god's favor toward that mortal (-10..10)
     */
    public static Effect witnessed(String tool, String words, boolean namesMe, String victim, int victimFavor) {
        if (tool == null) return Effect.NONE;
        if (words != null && namesMe) {
            if (INSULT.matcher(words).find()) return new Effect(-8, "mocked you: \"" + clip(words, 50) + "\"");
            if (PRAISE.matcher(words).find()) return new Effect(4, "spoke well of you");
            return Effect.NONE;
        }
        if (victim == null || victim.isBlank()) return Effect.NONE;
        boolean harsh = HARM.contains(tool) || ("apply_effect".equals(tool) && words != null && words.contains("harm"));
        if (harsh) {
            if (victimFavor >= 3) return new Effect(-6 - victimFavor, "struck your faithful " + victim + " (" + tool + ")");
            if (victimFavor <= -5) return new Effect(3, "punished " + victim + ", whom you despise");
            return Effect.NONE;
        }
        if (GIFTS.contains(tool)) {
            if (victimFavor >= 3) return new Effect(3, "blessed your faithful " + victim);
            if (victimFavor <= -5) return new Effect(-5, "blessed " + victim + ", whom you despise");
        }
        return Effect.NONE;
    }

    /** Both gods court the same mortal: each grows a little jealous of the other. */
    public static Effect contested(String mortal, int mine, int theirs) {
        if (mine >= 4 && theirs >= 4) return new Effect(-1, "courts " + mortal + ", who is devoted to you too");
        return Effect.NONE;
    }

    // ---- Reading ---------------------------------------------------------------------------------------

    /**
     * One line for the perception: "Vorthak: rival (-45), at holy war — struck your faithful Steve (smite)".
     * Gods not in the list (gone) are skipped.
     */
    public synchronized String line(String id, String name, long now) {
        Bond b = find(id);
        int score = b == null ? 0 : b.score;
        StringBuilder sb = new StringBuilder("- ").append(name).append(": ").append(stance(score)).append(" (")
                .append(score).append(")");
        if (b != null) {
            Pact p = b.pact(now);
            if (p == Pact.ALLIANCE) sb.append(", your ally by pact");
            else if (p == Pact.TRUCE) sb.append(", under truce");
            else if (p == Pact.WAR) sb.append(", at holy war with you");
            if (b.offered != null) sb.append("; it offers you ").append(Pact.parse(b.offered) == Pact.ALLIANCE
                    ? "an alliance" : "a truce").append(" (pact accept/refuse)");
            if (b.why != null && !b.why.isBlank()) sb.append(" — ").append(b.why);
        }
        return sb.toString();
    }

    public static String clip(String s, int max) {
        String t = s == null ? "" : s.replace('\n', ' ').trim();
        return t.length() > max ? t.substring(0, max - 1) + "…" : t;
    }
}
