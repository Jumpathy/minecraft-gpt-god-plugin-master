package net.bigyous.gptgodmc.god;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.bigyous.gptgodmc.EventLogger;

/**
 * Oaths mortals swear before the god (/swear). The god witnesses or refuses each one, may bind it with a
 * watch the server keeps (never do X / must do X N times, optionally near a place), and judges it at the
 * end: kept, broken or released. A watched breach or fulfilment reaches the god at once; the server
 * never punishes or rewards on its own. Kept in {@link Memory#oaths}; how many each mortal kept and broke
 * is kept for ever on their {@link Memory.Subject}. Pure apart from the memory it edits, so it is unit tested.
 */
public final class Oaths {
    /** Deeds a watch can look for (see {@link DeedWatch}). */
    public static final List<String> ACTS = List.of("kill_player", "kill_mob", "break_blocks", "place_blocks",
            "fell_trees", "plant", "harvest", "breed", "tame", "fish", "pray", "offer", "die", "enter");
    static final int MAX_KEPT = 40;
    static final int WORDS_MAX = 200;
    /** A watched oath that was broken again tells the god again at most this often. */
    static final long RETELL_MS = 10 * 60_000L;
    /** An oath whose term ended and that the god never judged is closed by time after this long. */
    static final long JUDGE_GRACE_MS = 24 * 3_600_000L;

    private Oaths() {
    }

    /** What the server watches for the god. */
    public static final class Clause {
        /** never | must */
        public String kind;
        public String act;
        /** A mortal, creature, block or kind it is done to; null = anything. */
        public String target;
        /** For must: how many times. */
        public int count;
        /** Only deeds within {@link #radius} blocks of this place count (radius 0 = anywhere). */
        public String world;
        public int x, y, z, radius;
        /** For must: how many times so far. */
        public int done;

        public boolean never() {
            return "never".equals(kind);
        }
    }

    public static final class Oath {
        public String id;
        public String swearer;
        /** What they swore, cleaned. */
        public String words;
        /** The oath as the god bound them to it (or why it refused). */
        public String terms;
        public long sworeAt;
        public long witnessedAt;
        /** When it ends (0 = for as long as they live). */
        public long until;
        /** pending | sworn | kept | broken | released | refused | unheard */
        public String status = "pending";
        /** null: the server cannot see it; the god judges it alone. */
        public Clause watch;
        public int breaches;
        public String breach;
        public long breachAt;
        public long toldAt;
        public long fulfilledAt;
        /** The god was told its term is over. */
        public boolean dueTold;
        public String outcome;
        public long closedAt;

        public boolean pending() {
            return "pending".equals(status);
        }

        public boolean sworn() {
            return "sworn".equals(status);
        }

        public boolean open() {
            return pending() || sworn();
        }
    }

    /** Thrown with a message phrased for whoever asked (the mortal for swear, the god for the rest). */
    public static final class Refusal extends RuntimeException {
        public Refusal(String message) {
            super(message);
        }
    }

    public enum NoticeKind {
        BREACH, FULFILLED, TERM_ENDED, UNHEARD, CLOSED_BY_TIME
    }

    /** Something to tell the god (and maybe the swearer) about an oath. */
    public record Notice(NoticeKind kind, Oath oath, String text) {
    }

    // ---- Swearing and judging -----------------------------------------------------------------------

    /** A mortal swears; the oath waits for the god's answer. */
    public static Oath swear(Memory m, String player, String words, int maxOpen, long now) {
        String w = words == null ? "" : words.trim().replaceAll("\\s+", " ");
        if (w.length() < 4) throw new Refusal("Swear something: /swear <what you swear>.");
        if (w.length() > WORDS_MAX) w = w.substring(0, WORDS_MAX - 1) + "…";
        synchronized (m) {
            int open = 0;
            for (Oath o : m.oaths) {
                if (!o.open() || !o.swearer.equalsIgnoreCase(player)) continue;
                if (o.pending()) throw new Refusal("Your oath " + o.id + " still waits for an answer.");
                open++;
            }
            if (open >= maxOpen) {
                throw new Refusal("You are already bound by " + open + " oaths. Keep them before you swear more.");
            }
            Oath o = new Oath();
            o.id = "o" + (++m.oathCounter);
            o.swearer = player;
            o.words = w;
            o.sworeAt = now;
            m.oaths.add(o);
            trim(m);
            return o;
        }
    }

    /** A watch for the god to bind an oath with; refused with guidance when it makes no sense. */
    public static Clause clause(String kind, String act, String target, int count, String world, int x, int y, int z,
            int radius) {
        if (!"never".equals(kind) && !"must".equals(kind)) throw new Refusal("watch is none, never or must.");
        if (act == null || !ACTS.contains(act)) {
            throw new Refusal("Name the deed to watch (act): " + String.join(", ", ACTS) + ".");
        }
        if (act.equals("enter") && world == null) throw new Refusal("A watch on entering needs a place.");
        Clause c = new Clause();
        c.kind = kind;
        c.act = act;
        String t = target == null ? null : normal(target);
        c.target = t == null || t.isEmpty() || t.equals("any") || t.equals("anything") ? null : t;
        c.count = "must".equals(kind) ? Math.max(1, count) : 0;
        if (world != null) {
            c.world = world;
            c.x = x;
            c.y = y;
            c.z = z;
            c.radius = Math.max(2, radius);
        }
        return c;
    }

    /** The god accepts: the oath now binds, with its terms, its length and an optional watch. */
    public static Oath witness(Memory m, String id, String terms, int minutes, Clause watch, long now) {
        synchronized (m) {
            Oath o = pendingOath(m, id);
            o.status = "sworn";
            o.terms = terms == null || terms.isBlank() ? o.words : shorten(terms.trim(), WORDS_MAX);
            o.witnessedAt = now;
            o.until = minutes > 0 ? now + minutes * 60_000L : 0;
            o.watch = watch;
            return o;
        }
    }

    /** The god will not have it. */
    public static Oath refuse(Memory m, String id, String why, long now) {
        synchronized (m) {
            Oath o = pendingOath(m, id);
            o.status = "refused";
            o.outcome = why == null || why.isBlank() ? null : shorten(why.trim(), WORDS_MAX);
            o.closedAt = now;
            return o;
        }
    }

    /** The god judges a sworn oath; the swearer's record keeps it for ever. */
    public static Oath resolve(Memory m, String id, String outcome, String note, long now) {
        if (!List.of("kept", "broken", "released").contains(outcome)) {
            throw new Refusal("outcome is kept, broken or released.");
        }
        synchronized (m) {
            Oath o = find(m, id);
            if (o == null || !o.sworn()) {
                throw new Refusal(o != null && o.pending() ? "Oath " + o.id + " was never witnessed; answer it with "
                        + "witness_oath first." : "No sworn oath '" + id + "'. Open: " + openIds(m) + ".");
            }
            close(m, o, outcome, note == null || note.isBlank() ? null : shorten(note.trim(), WORDS_MAX), now);
            return o;
        }
    }

    private static void close(Memory m, Oath o, String outcome, String note, long now) {
        o.status = outcome;
        o.outcome = note;
        o.closedAt = now;
        Memory.Subject s = m.subject(o.swearer);
        if (outcome.equals("kept")) s.oathsKept++;
        if (outcome.equals("broken")) s.oathsBroken++;
        m.addDeed(o.swearer, (outcome.equals("kept") ? "KEPT their oath " : outcome.equals("broken")
                ? "BROKE their oath " : "was released from their oath ") + o.id + ": " + shorten(o.terms, 80), now);
    }

    private static Oath pendingOath(Memory m, String id) {
        Oath o = find(m, id);
        if (o == null || !o.pending()) {
            throw new Refusal("No oath '" + id + "' waits for your answer. Waiting: " + pendingIds(m) + ".");
        }
        return o;
    }

    public static Oath find(Memory m, String id) {
        if (id == null) return null;
        String k = id.trim();
        synchronized (m) {
            for (int i = m.oaths.size() - 1; i >= 0; i--) {
                Oath o = m.oaths.get(i);
                if (o.id.equalsIgnoreCase(k)) return o;
            }
            return null;
        }
    }

    // ---- Watching -----------------------------------------------------------------------------------

    /** A deed the server saw: breaches and fulfilments of the swearer's watched oaths. */
    public static List<Notice> observe(Memory m, WorldDeed d, long now) {
        List<Notice> out = new ArrayList<>();
        synchronized (m) {
            for (Oath o : m.oaths) {
                if (!o.sworn() || o.watch == null || !o.swearer.equalsIgnoreCase(d.player())) continue;
                if (o.until > 0 && d.at() > o.until) continue;
                if (!matches(o.watch, d)) continue;
                if (o.watch.never()) {
                    o.breaches++;
                    o.breach = deedText(d);
                    o.breachAt = d.at();
                    if (o.breaches == 1 || now - o.toldAt >= RETELL_MS) {
                        o.toldAt = now;
                        out.add(new Notice(NoticeKind.BREACH, o, o.swearer + " BROKE the oath " + o.id
                                + " they swore before you (\"" + o.terms + "\"): they " + o.breach
                                + (o.breaches > 1 ? " (" + o.breaches + " breaches)" : "")
                                + ". Judge them: resolve_oath broken, or let it pass."));
                    }
                } else if (o.fulfilledAt == 0) {
                    o.watch.done++;
                    if (o.watch.done >= o.watch.count) {
                        o.fulfilledAt = now;
                        out.add(new Notice(NoticeKind.FULFILLED, o, o.swearer + " FULFILLED the oath " + o.id
                                + " they swore before you (\"" + o.terms + "\"): they " + deedText(d) + ", "
                                + o.watch.done + " of " + o.watch.count + ". Judge it: resolve_oath kept."));
                    }
                }
            }
        }
        return out;
    }

    /** Unanswered oaths lapse, ended terms are put to the god, and long-unjudged ones are closed by time. */
    public static List<Notice> tick(Memory m, long now, long answerMs) {
        List<Notice> out = new ArrayList<>();
        synchronized (m) {
            for (Oath o : m.oaths) {
                if (o.pending() && now - o.sworeAt >= answerMs) {
                    o.status = "unheard";
                    o.closedAt = now;
                    out.add(new Notice(NoticeKind.UNHEARD, o, o.swearer + "'s oath " + o.id
                            + " went unanswered and lapsed."));
                    continue;
                }
                if (!o.sworn() || o.until == 0 || now < o.until) continue;
                if (!o.dueTold) {
                    o.dueTold = true;
                    out.add(new Notice(NoticeKind.TERM_ENDED, o, o.swearer + "'s oath " + o.id + " (\"" + o.terms
                            + "\") has run its term: " + standing(o) + ". Judge it with resolve_oath."));
                } else if (now - o.until >= JUDGE_GRACE_MS) {
                    String by = broken(o) ? "broken" : "kept";
                    close(m, o, by, "closed by time; you never judged it", now);
                    out.add(new Notice(NoticeKind.CLOSED_BY_TIME, o, o.swearer + "'s oath " + o.id
                            + " was closed as " + by + " by time."));
                }
            }
            trim(m);
        }
        return out;
    }

    /** True when the watch shows it broken: a breach, or a must left undone at the end. */
    static boolean broken(Oath o) {
        if (o.watch == null) return false;
        return o.watch.never() ? o.breaches > 0 : o.fulfilledAt == 0;
    }

    private static String standing(Oath o) {
        if (o.watch == null) return "the server could not watch it; judge whether they kept it";
        if (o.watch.never()) {
            return o.breaches == 0 ? "they never broke it" : "they broke it " + o.breaches + " time(s), last: " + o.breach;
        }
        return o.fulfilledAt != 0 ? "they fulfilled it" : "they did not fulfil it (" + o.watch.done + " of "
                + o.watch.count + ")";
    }

    public static boolean matches(Clause c, WorldDeed d) {
        if (!c.act.equals(d.act())) return false;
        if (c.target != null && !targets(c.target, d)) return false;
        if (c.radius > 0) {
            if (c.world == null || !c.world.equalsIgnoreCase(d.world())) return false;
            long dx = d.x() - c.x, dz = d.z() - c.z;
            if (dx * dx + dz * dz > (long) c.radius * c.radius) return false;
        }
        return true;
    }

    /** "villagers" matches a villager, "log" an oak_log, "Ash" the mortal Ash, "animal" any animal. */
    static boolean targets(String target, WorldDeed d) {
        if (d.is(target)) return true;
        if (target.endsWith("s") && d.is(target.substring(0, target.length() - 1))) return true;
        boolean block = d.act().equals("break_blocks") || d.act().equals("place_blocks") || d.act().equals("plant");
        return block && target.length() >= 3 && d.kind().toLowerCase(Locale.ROOT).contains(target);
    }

    private static String normal(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "").replace(' ', '_');
    }

    // ---- Words --------------------------------------------------------------------------------------

    /** The deed in the past tense: "killed a villager", "broke oak_log at (3, 64, 9)", "killed Ash". */
    static String deedText(WorldDeed d) {
        String k = d.kind().replace('_', ' ');
        String at = " at (" + d.x() + ", " + d.y() + ", " + d.z() + ")";
        return switch (d.act()) {
            case "kill_player" -> "killed " + d.kind() + at;
            case "kill_mob" -> "killed a " + k + at;
            case "break_blocks" -> "broke " + k + at;
            case "place_blocks" -> "placed " + k + at;
            case "fell_trees" -> "felled " + k + at;
            case "plant" -> "planted " + k + at;
            case "harvest" -> "harvested " + k + at;
            case "breed" -> "bred " + k + at;
            case "tame" -> "tamed a " + k + at;
            case "fish" -> "caught a fish" + at;
            case "pray" -> "prayed";
            case "offer" -> "made an offering";
            case "die" -> "died" + at;
            case "enter" -> "entered the place" + at;
            default -> d.act() + at;
        };
    }

    /** The watch in plain words: "never kill villager within 32 blocks of (10, 64, -3)", "must plant sapling (12/64)". */
    public static String describe(Clause c) {
        String what = switch (c.act) {
            case "kill_player" -> "kill " + (c.target == null ? "any mortal" : c.target);
            case "kill_mob" -> "kill " + (c.target == null ? "any creature" : c.target);
            case "break_blocks" -> "break " + (c.target == null ? "blocks" : c.target);
            case "place_blocks" -> "place " + (c.target == null ? "blocks" : c.target);
            case "fell_trees" -> "fell trees";
            case "plant" -> "plant " + (c.target == null ? "crops or saplings" : c.target);
            case "harvest" -> "harvest " + (c.target == null ? "crops" : c.target);
            case "breed" -> "breed " + (c.target == null ? "animals" : c.target);
            case "tame" -> "tame " + (c.target == null ? "an animal" : c.target);
            case "fish" -> "catch fish";
            case "pray" -> "pray";
            case "offer" -> "make offerings";
            case "die" -> "die";
            case "enter" -> "enter";
            default -> c.act;
        };
        String where = c.radius > 0 ? " within " + c.radius + " blocks of (" + c.x + ", " + c.y + ", " + c.z + ")" : "";
        return c.never() ? "never " + what + where : "must " + what + where + " (" + c.done + "/" + c.count + ")";
    }

    /** The "## Oaths sworn before you" body: open oaths, then a few closed in the last day. Empty when none. */
    public static String render(Memory m, long now) {
        StringBuilder sb = new StringBuilder();
        synchronized (m) {
            for (Oath o : m.oaths) {
                if (!o.open()) continue;
                sb.append("- [").append(o.id).append("] ").append(o.swearer);
                if (o.pending()) {
                    sb.append(", WAITING FOR YOUR ANSWER (sworn ").append(ago(now - o.sworeAt)).append(" ago): \"")
                            .append(o.words).append("\" — witness_oath to accept (your terms, a length, a watch) or "
                                    + "refuse it.\n");
                    continue;
                }
                sb.append(", sworn ").append(ago(now - o.witnessedAt)).append(" ago");
                if (o.until > 0) {
                    sb.append(now < o.until ? ", ends in " + ago(o.until - now) : ", TERM OVER: judge it");
                } else {
                    sb.append(", for life");
                }
                sb.append(": \"").append(o.terms).append("\"");
                sb.append(o.watch == null ? " (unwatched: judge it yourself)" : " (watched: " + describe(o.watch) + ")");
                if (o.breaches > 0) {
                    sb.append(" — BROKEN ").append(o.breaches).append("x, last ").append(ago(now - o.breachAt))
                            .append(" ago: ").append(o.breach);
                }
                if (o.fulfilledAt > 0) sb.append(" — FULFILLED ").append(ago(now - o.fulfilledAt)).append(" ago");
                sb.append('\n');
            }
            int shown = 0;
            for (int i = m.oaths.size() - 1; i >= 0 && shown < 3; i--) {
                Oath o = m.oaths.get(i);
                if (o.open() || now - o.closedAt > 24 * 3_600_000L) continue;
                sb.append("- (").append(o.status).append(' ').append(ago(now - o.closedAt)).append(" ago) [")
                        .append(o.id).append("] ").append(o.swearer).append(": \"")
                        .append(o.terms != null ? o.terms : o.words).append('"')
                        .append(o.outcome == null ? "" : " — " + o.outcome).append('\n');
                shown++;
            }
        }
        return sb.toString();
    }

    /** What a mortal sees with /swear alone: their open oaths before this god. Empty when none. */
    public static String listFor(Memory m, String player, long now) {
        StringBuilder sb = new StringBuilder();
        synchronized (m) {
            for (Oath o : m.oaths) {
                if (!o.open() || !o.swearer.equalsIgnoreCase(player)) continue;
                sb.append(o.id).append(": ");
                if (o.pending()) {
                    sb.append("\"").append(o.words).append("\" (awaiting an answer)\n");
                    continue;
                }
                sb.append("\"").append(o.terms).append("\"");
                sb.append(o.until > 0 ? (now < o.until ? " (ends in " + ago(o.until - now) + ")" : " (its term is over)")
                        : " (for life)");
                if (o.watch != null && !o.watch.never()) sb.append(" ").append(o.watch.done).append('/').append(o.watch.count);
                if (o.breaches > 0) sb.append(" BROKEN");
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /** "oaths: kept 2, broken 1; open: o5, o7", or null when they have none. */
    public static String dossierLine(Memory m, Memory.Subject s) {
        List<String> open = new ArrayList<>();
        synchronized (m) {
            for (Oath o : m.oaths) {
                if (o.open() && o.swearer.equalsIgnoreCase(s.name)) open.add(o.id + (o.breaches > 0 ? " (broken)" : ""));
            }
        }
        if (s.oathsKept == 0 && s.oathsBroken == 0 && open.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("kept ").append(s.oathsKept).append(", broken ").append(s.oathsBroken);
        if (s.oathsBroken > 0) sb.append(" (FORSWORN)");
        if (!open.isEmpty()) sb.append("; open: ").append(String.join(", ", open));
        return sb.toString();
    }

    /** Places that sworn "enter" watches look at, for the server to check where mortals stand. */
    public static List<Oath> enterWatches(Memory m) {
        List<Oath> out = new ArrayList<>();
        synchronized (m) {
            for (Oath o : m.oaths) {
                if (o.sworn() && o.watch != null && o.watch.act.equals("enter")) out.add(o);
            }
        }
        return out;
    }

    static String openIds(Memory m) {
        List<String> ids = new ArrayList<>();
        for (Oath o : m.oaths) if (o.sworn()) ids.add(o.id + " (" + o.swearer + ")");
        return ids.isEmpty() ? "none" : String.join(", ", ids);
    }

    static String pendingIds(Memory m) {
        List<String> ids = new ArrayList<>();
        for (Oath o : m.oaths) if (o.pending()) ids.add(o.id + " (" + o.swearer + ")");
        return ids.isEmpty() ? "none" : String.join(", ", ids);
    }

    /** Keeps every open oath and the newest closed ones, up to {@link #MAX_KEPT} in all. */
    private static void trim(Memory m) {
        while (m.oaths.size() > MAX_KEPT) {
            Oath oldest = null;
            for (Oath o : m.oaths) {
                if (!o.open()) {
                    oldest = o;
                    break;
                }
            }
            if (oldest == null) break;
            m.oaths.remove(oldest);
        }
    }

    private static String ago(long ms) {
        return EventLogger.ago(Duration.ofMillis(Math.max(0, ms)));
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
