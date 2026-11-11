package net.bigyous.gptgodmc.god.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * Fits a perception into a small model's context. The perception is a list of "## " sections; when it
 * is too long, the sections a god can best do without are dropped first, and what remains is clipped:
 * conversations and event lists keep their newest lines, everything else its first.
 */
public final class LitePerception {
    private LitePerception() {
    }

    private record Section(String heading, String body, int rank, boolean newestMatters) {
        int length() {
            return heading.length() + body.length();
        }
    }

    /**
     * How much a section matters: 0 is never dropped (commands and what mortals said), higher numbers
     * go first. Headings are matched by how they begin, so new sections default to the middle.
     */
    static int rank(String heading) {
        String h = heading.toLowerCase(java.util.Locale.ROOT);
        if (h.startsWith("# ") || h.contains("binding commands") || h.contains("operator directive")
                || h.contains("waiting for your answer") || h.contains("conversation")) {
            return 0;
        }
        if (h.contains("mortals present") || h.contains("what mortals did")) return 1;
        if (h.contains("your ledger")) return 2;
        if (h.contains("your vessel") || h.contains("the world") || h.contains("quests") || h.contains("oaths")) return 3;
        if (h.contains("your heart") || h.contains("recent acts") || h.contains("commandments")) return 4;
        if (h.contains("your purpose") || h.contains("mortality")) return 5;
        if (h.contains("structures") || h.contains("your works") || h.contains("demigods")
                || h.contains("other gods") || h.contains("what you are doing")) {
            return 7;
        }
        return 6;
    }

    /** The perception, cut to at most `budget` characters (never below what rank 0 needs to say). */
    public static String trim(String perception, int budget) {
        if (perception == null || perception.length() <= budget) return perception;
        List<Section> sections = split(perception);
        int total = perception.length();
        // 1. Drop whole sections, least needed first.
        for (int rank = 7; rank >= 3 && total > budget; rank--) {
            for (int i = sections.size() - 1; i >= 0 && total > budget; i--) {
                if (sections.get(i).rank() == rank) total -= sections.remove(i).length();
            }
        }
        // 2. Clip the longest of what is left until it fits.
        for (int guard = 0; guard < 40 && total > budget; guard++) {
            int longest = -1;
            for (int i = 0; i < sections.size(); i++) {
                if (longest < 0 || sections.get(i).body().length() > sections.get(longest).body().length()) {
                    longest = i;
                }
            }
            Section s = sections.get(longest);
            int keep = Math.max(200, s.body().length() - (total - budget) - 40);
            if (keep >= s.body().length()) break;
            String clipped = clip(s.body(), keep, s.newestMatters());
            total -= s.body().length() - clipped.length();
            sections.set(longest, new Section(s.heading(), clipped, s.rank(), s.newestMatters()));
        }
        StringBuilder sb = new StringBuilder();
        for (Section s : sections) sb.append(s.heading()).append(s.body());
        return sb.toString();
    }

    private static List<Section> split(String text) {
        List<Section> out = new ArrayList<>();
        int at = 0;
        while (at < text.length()) {
            int lineEnd = text.indexOf('\n', at);
            if (lineEnd < 0) lineEnd = text.length() - 1;
            String heading = text.substring(at, lineEnd + 1);
            int next = text.indexOf("\n## ", lineEnd);
            int bodyEnd = next < 0 ? text.length() : next + 1;
            String body = lineEnd + 1 <= bodyEnd ? text.substring(lineEnd + 1, bodyEnd) : "";
            String h = heading.toLowerCase(java.util.Locale.ROOT);
            out.add(new Section(heading, body, rank(heading),
                    h.contains("conversation") || h.contains("what mortals did") || h.contains("recent acts")));
            at = bodyEnd;
        }
        return out;
    }

    /** Whole lines only: the last ones of a timeline, the first ones of anything else. */
    private static String clip(String body, int keep, boolean newest) {
        String note = "(… shortened …)\n";
        if (newest) {
            int from = body.length() - keep;
            int nl = body.indexOf('\n', from);
            return note + (nl < 0 || nl + 1 >= body.length() ? body.substring(from) : body.substring(nl + 1));
        }
        int nl = body.lastIndexOf('\n', keep);
        return (nl <= 0 ? body.substring(0, keep) + "\n" : body.substring(0, nl + 1)) + note;
    }
}
