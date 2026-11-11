package net.bigyous.gptgodmc.god;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The living land as the god knows it: what lives and grows around mortals ({@link Land}, sensed by
 * {@link LandSense}), and the mark each mortal leaves on it ({@link Ledger}: trees felled and planted,
 * animals slain and bred, fields sown and reaped, villagers killed), counted per 64-block cell and fading
 * with a half-life so it speaks of the last day or two. Pure, so it is unit tested.
 */
public final class Ecology {
    public static final int CELL = 64;
    static final int MAX_CELLS = 400;
    /** What the ledger counts, in the order it is told. */
    public static final List<String> WHATS = List.of("logs felled", "saplings planted", "crops sown",
            "crops harvested", "animals slain", "animals bred", "animals tamed", "villagers slain", "golems slain");

    private Ecology() {
    }

    // ---- The land around mortals --------------------------------------------------------------------

    /**
     * What lives and grows around a place. Columns are sampled every {@code spacing} blocks; surface is
     * false where the sky tells nothing (the Nether, the End), and then only creatures count.
     */
    public record Land(String biome, Map<String, Integer> animals, int tamed, int villagers, int golems,
            int cropColumns, int ripeColumns, int canopyColumns, int sampledColumns, int spacing, boolean surface) {
    }

    /** One line: "plains; 6 cows, 3 sheep (2 tamed); a village of 8 villagers and 1 iron golem; ~40 crops, 60% ripe; a few trees". */
    public static String describe(Land l) {
        List<String> parts = new ArrayList<>();
        parts.add(l.biome().replace('_', ' '));
        if (l.animals().isEmpty()) {
            parts.add("no animals");
        } else {
            List<Map.Entry<String, Integer>> top = new ArrayList<>(l.animals().entrySet());
            top.sort((a, b) -> b.getValue() - a.getValue());
            List<String> names = new ArrayList<>();
            int rest = 0;
            for (int i = 0; i < top.size(); i++) {
                if (i < 5) names.add(count(top.get(i).getKey(), top.get(i).getValue()));
                else rest += top.get(i).getValue();
            }
            if (rest > 0) names.add(rest + " other animals");
            parts.add(String.join(", ", names) + (l.tamed() > 0 ? " (" + l.tamed() + " tamed)" : ""));
        }
        if (l.villagers() >= 3) {
            parts.add("a village of " + l.villagers() + " villagers"
                    + (l.golems() > 0 ? " and " + count("iron_golem", l.golems()) : ", unguarded"));
        } else if (l.villagers() > 0) {
            parts.add(count("villager", l.villagers()) + (l.golems() > 0 ? ", " + count("iron_golem", l.golems()) : ""));
        } else if (l.golems() > 0) {
            parts.add(count("iron_golem", l.golems()));
        }
        if (l.surface() && l.sampledColumns() > 0) {
            if (l.cropColumns() > 0) {
                int crops = l.cropColumns() * l.spacing() * l.spacing();
                parts.add("~" + crops + " crops, " + Math.round(100.0 * l.ripeColumns() / l.cropColumns()) + "% ripe");
            }
            double canopy = (double) l.canopyColumns() / l.sampledColumns();
            if (canopy >= 0.4) parts.add("dense woods");
            else if (canopy >= 0.15) parts.add("wooded");
            else if (canopy >= 0.03) parts.add("a few trees");
            else if (forested(l.biome())) parts.add("a forest with its trees cut away");
            else parts.add("no trees");
        }
        return String.join("; ", parts);
    }

    /** Biomes that should have trees, so bare ground there means they were cut down. */
    static boolean forested(String biome) {
        for (String k : List.of("forest", "taiga", "jungle", "grove", "woodland", "pale_garden", "mangrove")) {
            if (biome.contains(k)) return true;
        }
        return false;
    }

    /** "1 cow", "6 cows", "3 sheep", "2 wolves", "2 iron golems". */
    static String count(String kind, int n) {
        String name = kind.replace('_', ' ');
        if (n == 1) return "1 " + name;
        if (Set.of("sheep", "cod", "salmon", "tropical fish", "pufferfish").contains(name)) return n + " " + name;
        if (name.endsWith("wolf")) return n + " " + name.substring(0, name.length() - 1) + "ves";
        if (name.endsWith("x") || name.endsWith("sh")) return n + " " + name + "es";
        return n + " " + name + "s";
    }

    // ---- The mark mortals leave on it ---------------------------------------------------------------

    /** Every cell a mortal has marked lately, keyed "world:cx:cz". Kept in the god's memory. */
    public static final class Ledger {
        public Map<String, Cell> cells = new LinkedHashMap<>();
    }

    /** One 64x64 cell: what each mortal did there, fading by half every half-life. */
    public static final class Cell {
        public String world;
        public int cx, cz;
        /** When the amounts were last faded. */
        public long at;
        /** When a mortal last did something here. */
        public long touched;
        /** mortal -> what (one of {@link #WHATS}) -> how much, faded. */
        public Map<String, Map<String, Double>> by = new LinkedHashMap<>();

        double total(String what) {
            double t = 0;
            for (Map<String, Double> m : by.values()) t += m.getOrDefault(what, 0.0);
            return t;
        }
    }

    /** What a deed does to the land, as one of {@link #WHATS}, or null when it does nothing to it. */
    public static String footprint(WorldDeed d) {
        return switch (d.act()) {
            case "fell_trees" -> "logs felled";
            case "plant" -> d.is("sapling") ? "saplings planted" : d.is("crop") ? "crops sown" : null;
            case "harvest" -> "crops harvested";
            case "breed" -> "animals bred";
            case "tame" -> "animals tamed";
            case "kill_mob" -> d.is("villager") ? "villagers slain" : d.is("golem") ? "golems slain"
                    : d.is("animal") ? "animals slain" : null;
            default -> null;
        };
    }

    public static String key(String world, int x, int z) {
        return world + ":" + Math.floorDiv(x, CELL) + ":" + Math.floorDiv(z, CELL);
    }

    /** Counts a deed in its cell; returns what it did to the land, or null. */
    public static String record(Ledger ledger, WorldDeed d, long halfLifeMs) {
        String what = footprint(d);
        if (what == null) return null;
        String k = key(d.world(), d.x(), d.z());
        Cell c = ledger.cells.get(k);
        if (c == null) {
            c = new Cell();
            c.world = d.world();
            c.cx = Math.floorDiv(d.x(), CELL);
            c.cz = Math.floorDiv(d.z(), CELL);
            c.at = d.at();
            ledger.cells.put(k, c);
        }
        fade(c, d.at(), halfLifeMs);
        c.by.computeIfAbsent(d.player(), p -> new LinkedHashMap<>()).merge(what, 1.0, Double::sum);
        c.touched = Math.max(c.touched, d.at());
        if (ledger.cells.size() > MAX_CELLS) prune(ledger, d.at(), halfLifeMs);
        return what;
    }

    /** Fades a cell to now: every amount halves each half-life; what falls below one half is forgotten. */
    static void fade(Cell c, long now, long halfLifeMs) {
        if (halfLifeMs <= 0 || now <= c.at) return;
        double f = Math.pow(0.5, (double) (now - c.at) / halfLifeMs);
        c.at = now;
        c.by.values().removeIf(m -> {
            m.replaceAll((w, v) -> v * f);
            m.values().removeIf(v -> v < 0.5);
            return m.isEmpty();
        });
    }

    /** Fades every cell, drops the empty ones, and past the limit the ones touched longest ago. */
    static void prune(Ledger ledger, long now, long halfLifeMs) {
        for (Cell c : ledger.cells.values()) fade(c, now, halfLifeMs);
        ledger.cells.values().removeIf(c -> c.by.isEmpty());
        if (ledger.cells.size() <= MAX_CELLS) return;
        List<Map.Entry<String, Cell>> all = new ArrayList<>(ledger.cells.entrySet());
        all.sort((a, b) -> Long.compare(a.getValue().touched, b.getValue().touched));
        for (int i = 0; i < all.size() - MAX_CELLS; i++) ledger.cells.remove(all.get(i).getKey());
    }

    /** A place a mortal stands, for finding the cells around them. */
    public record Spot(String world, int x, int z) {
    }

    /**
     * Lines about the cells around these places (the cell each stands in and its eight neighbours),
     * most marked first, at most {@code max}: who did what there, and what it is doing to the land.
     */
    public static String render(Ledger ledger, List<Spot> near, long now, long halfLifeMs, int max) {
        Map<String, Cell> chosen = new LinkedHashMap<>();
        for (Spot s : near) {
            int cx = Math.floorDiv(s.x(), CELL), cz = Math.floorDiv(s.z(), CELL);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    String k = s.world() + ":" + (cx + dx) + ":" + (cz + dz);
                    Cell c = ledger.cells.get(k);
                    if (c == null) continue;
                    fade(c, now, halfLifeMs);
                    if (!c.by.isEmpty()) chosen.put(k, c);
                }
            }
        }
        List<Cell> cells = new ArrayList<>(chosen.values());
        cells.sort((a, b) -> Double.compare(weight(b), weight(a)));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(max, cells.size()); i++) {
            Cell c = cells.get(i);
            sb.append("- near (").append(c.cx * CELL + CELL / 2).append(", ").append(c.cz * CELL + CELL / 2)
                    .append("): ");
            List<String> who = new ArrayList<>();
            for (Map.Entry<String, Map<String, Double>> e : c.by.entrySet()) {
                List<String> did = new ArrayList<>();
                for (String w : WHATS) {
                    long n = Math.round(e.getValue().getOrDefault(w, 0.0));
                    if (n >= 1) did.add(n + " " + w);
                }
                if (!did.isEmpty()) who.add(e.getKey() + " " + String.join(", ", did));
            }
            sb.append(String.join("; ", who));
            List<String> verdicts = judge(c);
            if (!verdicts.isEmpty()) sb.append(" — ").append(String.join("; ", verdicts));
            sb.append('\n');
        }
        return sb.toString();
    }

    /** What the tallies mean for the land. */
    static List<String> judge(Cell c) {
        double logs = c.total("logs felled"), saplings = c.total("saplings planted");
        double slain = c.total("animals slain"), bred = c.total("animals bred");
        double sown = c.total("crops sown"), reaped = c.total("crops harvested");
        List<String> out = new ArrayList<>();
        if (c.total("villagers slain") >= 1) out.add("villager blood was spilled here");
        if (logs >= 48 && saplings * 8 < logs) out.add("the woods are being stripped (few saplings planted)");
        else if (saplings >= 16 && saplings * 4 >= logs) out.add("trees are being planted");
        if (slain >= 12 && bred * 3 < slain) out.add("the herds are culled faster than they breed");
        else if (bred >= 8 && slain * 2 <= bred) out.add("the herds are growing");
        if (sown + reaped >= 32) out.add("the fields are tended");
        if (c.total("golems slain") >= 1) out.add("a village's guardian was slain");
        return out;
    }

    private static double weight(Cell c) {
        double w = 0;
        for (Map<String, Double> m : c.by.values()) {
            for (Map.Entry<String, Double> e : m.entrySet()) {
                w += e.getValue() * (e.getKey().equals("villagers slain") ? 40 : e.getKey().startsWith("animals") ? 3 : 1);
            }
        }
        return w;
    }

    // ---- Sprees -------------------------------------------------------------------------------------

    /** Notices when one mortal does something many times in a short while (then keeps quiet for a time). */
    public static final class Spree {
        private final int threshold;
        private final long windowMs;
        private final long quietMs;
        private final Map<String, Deque<Long>> hits = new HashMap<>();
        private final Map<String, Long> told = new HashMap<>();

        public Spree(int threshold, long windowMs, long quietMs) {
            this.threshold = threshold;
            this.windowMs = windowMs;
            this.quietMs = quietMs;
        }

        /** Counts one more; true when this crosses the threshold within the window and it was not told lately. */
        public synchronized boolean hit(String who, long now) {
            Deque<Long> d = hits.computeIfAbsent(who, k -> new ArrayDeque<>());
            d.add(now);
            while (!d.isEmpty() && now - d.peekFirst() > windowMs) d.pollFirst();
            if (d.size() < threshold) return false;
            Long last = told.get(who);
            if (last != null && now - last < quietMs) return false;
            told.put(who, now);
            d.clear();
            return true;
        }

        public synchronized void forget(String who) {
            hits.remove(who);
        }
    }
}
