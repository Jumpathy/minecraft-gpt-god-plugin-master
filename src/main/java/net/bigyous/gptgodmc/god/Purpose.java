package net.bigyous.gptgodmc.god;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.bigyous.gptgodmc.EventLogger;

/**
 * The god's purpose: a few long ambitions of its own ("a temple raised in my name", "the dragon
 * slain", "the faithful multiplied") that it pursues across many thoughts, announces as missions,
 * measures and rewards. Kept in {@link Memory#goals}; the perception shows them every thought and
 * reminds it of any it has neglected, so it is never merely there. Pure apart from the memory it
 * edits, so it is unit tested.
 */
public final class Purpose {
    private Purpose() {
    }

    /** One ambition. */
    public static final class Goal {
        public String id;
        public String title;
        public String details;
        /** What mortals will receive when it is achieved (for a mission). */
        public String reward;
        /** A mission announced to mortals (shown on a bar) rather than a private ambition. */
        public boolean mission;
        /** 0..100, as the god judges it. */
        public int progress;
        public String note;
        public long createdAt;
        /** When the god last advanced or judged it. */
        public long touchedAt;
        /** active | achieved | abandoned */
        public String status = "active";
        public String outcome;
        public long closedAt;

        public boolean isActive() {
            return "active".equals(status);
        }
    }

    /** Thrown with a message phrased for the god. */
    public static final class Refusal extends RuntimeException {
        public Refusal(String message) {
            super(message);
        }
    }

    public static List<Goal> active(Memory m) {
        synchronized (m) {
            List<Goal> out = new ArrayList<>();
            for (Goal g : m.goals) {
                if (g.isActive()) out.add(g);
            }
            return out;
        }
    }

    public static Goal set(Memory m, String title, String details, String reward, boolean mission, int max, long now) {
        if (title == null || title.isBlank()) throw new Refusal("A goal needs a title.");
        synchronized (m) {
            if (active(m).size() >= max) {
                throw new Refusal("You already pursue " + max + " goals; achieve or abandon one first (close_goal).");
            }
            Goal g = new Goal();
            g.id = "g" + (++m.goalCounter);
            g.title = shorten(title.trim(), 60);
            g.details = details == null ? "" : shorten(details.trim(), 400);
            g.reward = reward == null || reward.isBlank() ? null : shorten(reward.trim(), 160);
            g.mission = mission;
            g.createdAt = now;
            g.touchedAt = now;
            m.goals.add(g);
            while (m.goals.size() > 30) {
                Goal oldest = null;
                for (Goal x : m.goals) {
                    if (!x.isActive()) {
                        oldest = x;
                        break;
                    }
                }
                if (oldest == null) break;
                m.goals.remove(oldest);
            }
            return g;
        }
    }

    public static Goal find(Memory m, String idOrTitle) {
        if (idOrTitle == null) return null;
        String k = idOrTitle.trim();
        synchronized (m) {
            for (Goal g : m.goals) {
                if (g.isActive() && (g.id.equalsIgnoreCase(k) || g.title.equalsIgnoreCase(k))) return g;
            }
            for (Goal g : m.goals) {
                if (g.isActive() && g.title.toLowerCase(Locale.ROOT).contains(k.toLowerCase(Locale.ROOT))) return g;
            }
            return null;
        }
    }

    public static Goal update(Memory m, String idOrTitle, int progress, String note, long now) {
        synchronized (m) {
            Goal g = find(m, idOrTitle);
            if (g == null) throw new Refusal("No active goal '" + idOrTitle + "'. Yours: " + titles(m) + ".");
            g.progress = Math.max(0, Math.min(100, progress));
            if (note != null && !note.isBlank()) g.note = shorten(note.trim(), 200);
            g.touchedAt = now;
            return g;
        }
    }

    public static Goal close(Memory m, String idOrTitle, boolean achieved, String outcome, long now) {
        synchronized (m) {
            Goal g = find(m, idOrTitle);
            if (g == null) throw new Refusal("No active goal '" + idOrTitle + "'. Yours: " + titles(m) + ".");
            g.status = achieved ? "achieved" : "abandoned";
            if (achieved) g.progress = 100;
            g.outcome = outcome == null ? null : shorten(outcome.trim(), 200);
            g.closedAt = now;
            g.touchedAt = now;
            return g;
        }
    }

    static String titles(Memory m) {
        List<String> t = new ArrayList<>();
        for (Goal g : active(m)) t.add(g.id + " \"" + g.title + "\"");
        return t.isEmpty() ? "none" : String.join(", ", t);
    }

    /**
     * The "## Your purpose" body: active goals with progress and how long since it advanced each,
     * a reminder for neglected ones, a push to choose one when it has none, and recent outcomes.
     */
    public static String render(Memory m, long now, int nudgeMinutes, boolean mortalsPresent) {
        StringBuilder sb = new StringBuilder();
        synchronized (m) {
            List<Goal> act = active(m);
            for (Goal g : act) {
                long idle = now - g.touchedAt;
                sb.append("- [").append(g.id).append("] ").append(g.mission ? "MISSION for mortals: " : "Ambition: ")
                        .append('"').append(g.title).append("\" ").append(g.progress).append("% (set ")
                        .append(EventLogger.ago(Duration.ofMillis(now - g.createdAt))).append(" ago");
                if (g.reward != null) sb.append("; promised: ").append(g.reward);
                sb.append(")");
                if (g.details != null && !g.details.isEmpty()) sb.append(" — ").append(g.details);
                if (g.note != null) sb.append(" Last judged: ").append(g.note);
                if (idle >= nudgeMinutes * 60_000L && mortalsPresent) {
                    sb.append(" Untouched for ").append(EventLogger.ago(Duration.ofMillis(idle)))
                            .append(": when you have a quiet moment, move it forward YOURSELF (go there, build, "
                                    + "look, judge it with update_goal), or drop it if it bores you. Do not press "
                                    + "mortals about it again; they know.");
                }
                sb.append('\n');
            }
            if (act.isEmpty() && mortalsPresent) {
                sb.append("You pursue nothing. When it suits you, take up an ambition of your own with set_goal, "
                        + "one you can work at with your own hands. Make it a mission for mortals only if it truly "
                        + "needs them.\n");
            }
            int shown = 0;
            for (int i = m.goals.size() - 1; i >= 0 && shown < 3; i--) {
                Goal g = m.goals.get(i);
                if (g.isActive()) continue;
                sb.append("- (").append(g.status).append(' ')
                        .append(EventLogger.ago(Duration.ofMillis(now - g.closedAt))).append(" ago) \"")
                        .append(g.title).append('"').append(g.outcome == null ? "" : ": " + g.outcome).append('\n');
                shown++;
            }
        }
        return sb.toString();
    }

    /** The mission shown on the bar: the most advanced active mission, or null. */
    public static Goal banner(Memory m) {
        Goal best = null;
        for (Goal g : active(m)) {
            if (g.mission && (best == null || g.progress > best.progress)) best = g;
        }
        return best;
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
