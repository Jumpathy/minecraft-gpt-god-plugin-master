package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;

/**
 * What mortals write in the world, made into short text the god can read: signs, books, and a guess at
 * when placed blocks are meant as letters. Pure, so it is unit tested.
 */
public final class Writings {
    /** The most of one book the god is shown at a time. */
    public static final int BOOK_CHARS = 700;

    private Writings() {
    }

    /** A sign's lines as one phrase ("KEEP OUT / of my house"), or "" when it is blank. */
    public static String sign(List<String> lines) {
        List<String> kept = new ArrayList<>();
        for (String l : lines) {
            if (l != null && !l.isBlank()) kept.add(l.trim());
        }
        return String.join(" / ", kept);
    }

    /** A book's pages as one text, cut to `max` characters with a note of how much more there is. */
    public static String book(List<String> pages, int max) {
        StringBuilder sb = new StringBuilder();
        int written = 0;
        for (String p : pages) {
            if (p == null || p.isBlank()) continue;
            written++;
            if (sb.length() > 0) sb.append(" | ");
            sb.append(p.trim().replaceAll("\\s+", " "));
        }
        if (sb.length() <= max) return sb.toString();
        return sb.substring(0, max).trim() + "... (" + written + " pages, " + (sb.length() - max) + " more characters)";
    }

    /** The words that mark the event of a mortal finishing something written in blocks. */
    public static final String MARK = "laid out blocks that may be writing or a picture";
    /** A mortal must have stopped placing for this long before their block writing is read. */
    public static final long SETTLED_MS = 8_000;

    /** Block writing a mortal seems to have finished: where, how many blocks, and a mark of its exact shape. */
    public record Spotted(int x, int y, int z, int blocks, long signature) {
    }

    /**
     * Looks at what a mortal placed lately: if it is shaped like writing and they have stopped placing,
     * returns it; otherwise null.
     */
    public static Spotted spot(List<Senses.Change> changes, long now) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int n = 0;
        long last = 0, sig = 17;
        for (Senses.Change c : changes) {
            if (!c.placed() || now - c.at() > Senses.WINDOW_MS) continue;
            n++;
            last = Math.max(last, c.at());
            sig = sig * 31 + c.x() * 73856093L + c.y() * 19349663L + c.z() * 83492791L;
            minX = Math.min(minX, c.x());
            maxX = Math.max(maxX, c.x());
            minY = Math.min(minY, c.y());
            maxY = Math.max(maxY, c.y());
            minZ = Math.min(minZ, c.z());
            maxZ = Math.max(maxZ, c.z());
        }
        if (n == 0 || now - last < SETTLED_MS) return null;
        if (mayBeWriting(maxX - minX + 1, maxZ - minZ + 1, maxY - minY + 1, n)) {
            return new Spotted((minX + maxX) / 2, minY, (minZ + maxZ) / 2, n, sig);
        }
        // Not all in one sheet (they pillared up to look, or set a torch): try the one level most of it lies on.
        java.util.Map<Integer, Integer> levels = new java.util.HashMap<>();
        for (Senses.Change c : changes) {
            if (c.placed() && now - c.at() <= Senses.WINDOW_MS) levels.merge(c.y(), 1, Integer::sum);
        }
        int level = 0, most = 0;
        for (java.util.Map.Entry<Integer, Integer> e : levels.entrySet()) {
            if (e.getValue() > most) {
                most = e.getValue();
                level = e.getKey();
            }
        }
        if (most < 10 || most < n * 0.6) return null;
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (Senses.Change c : changes) {
            if (!c.placed() || now - c.at() > Senses.WINDOW_MS || c.y() != level) continue;
            x0 = Math.min(x0, c.x());
            x1 = Math.max(x1, c.x());
            z0 = Math.min(z0, c.z());
            z1 = Math.max(z1, c.z());
        }
        if (!mayBeWriting(x1 - x0 + 1, z1 - z0 + 1, 1, most)) return null;
        return new Spotted((x0 + x1) / 2, level, (z0 + z1) / 2, most, sig);
    }

    private static final java.util.regex.Pattern ASKS = java.util.regex.Pattern.compile(
            "(?i)\\b(wrote|written|writing|write|spell(ed|s)?|what (does|did|do) (it|this|that|they) say|"
                    + "(can|could|do) you (read|see) (it|this|that|what|my)|read (it|this|that|what|my)|"
                    + "what (did|have) i (build|built|make|made|draw|drawn)|what (is|am) (this|i (building|making))|"
                    + "look at (this|that|what|my)|see (this|what i|my))\\b");

    /** True when a mortal's words ask the god about something they wrote or built. */
    public static boolean asks(String words) {
        return words != null && ASKS.matcher(words).find();
    }

    /**
     * True when what a mortal placed looks like writing or a picture made of blocks: one block thick
     * (flat on the ground or a thin wall), wide, and with gaps rather than a filled floor or wall.
     *
     * @param w east-west, l north-south, h height of the box around the blocks; blocks how many
     */
    public static boolean mayBeWriting(int w, int l, int h, int blocks) {
        if (blocks < 10) return false;
        int a, b; // the two long sides of the thin slab
        if (h == 1) {
            a = w;
            b = l;
        } else if (Math.min(w, l) == 1) {
            a = Math.max(w, l);
            b = h;
        } else {
            return false;
        }
        if (Math.max(a, b) < 7 || Math.min(a, b) < 3) return false;
        double fill = blocks / (double) (a * b);
        return fill >= 0.15 && fill <= 0.6;
    }
}
