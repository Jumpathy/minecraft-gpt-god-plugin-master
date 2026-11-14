package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.BiomeSearchResult;
import org.bukkit.util.StructureSearchResult;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.Perception;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Sync;

/**
 * locate: the nearest structure or biome, as a direction, a distance and coordinates the god can tell a
 * mortal. The words are matched against the server's own registries (so datapack structures and biomes work
 * too); a general word ("village") searches every kind of it and names the nearest.
 *
 * Not a main-thread power: each search is its own visit to the server thread (the server ticks between them),
 * and the kinds still unsearched are given up once {@link #BUDGET_MS} is spent.
 */
final class LocateTools {
    /**
     * How far a structure search reaches, as the game's /locate counts it: rings of the structure's own
     * placement grid, NOT chunks (a village's grid is 34 chunks wide, so 16 rings reach about 8,700 blocks;
     * a mineshaft's is 1 chunk, so 256 blocks). At most (2 * 16 + 1)^2 = 1,089 places are tested per kind.
     */
    static final int STRUCTURE_RADIUS = 16;
    /** At most this many kinds of one structure are searched in one use ("village" is five). */
    static final int MAX_KINDS = 8;
    /** Biome search: reach in blocks, and the game's own /locate steps (every 32 blocks, every 64 in height). */
    static final int BIOME_RADIUS = 2048;
    static final int BIOME_STEP = 32;
    static final int BIOME_HEIGHT_STEP = 64;
    /** The longest one visit to the server thread is waited for. */
    private static final long CALL_MS = 10_000;
    /** Once the structure searches of one use have taken this long, the kinds left are not searched. */
    static final long BUDGET_MS = 6_000;

    /** Scores of {@link #score}: the name itself, a kind of what was asked, a name inside the words, a part of a name. */
    static final int EXACT = 1000;
    static final int KIND = 500;
    static final int LOOSE = 100;
    static final int PART = 50;

    private static final Set<String> FILLER = Set.of("nearest", "closest", "nearby", "near", "a", "an", "the", "of",
            "any", "some", "me", "us", "to", "is", "are", "there", "here", "around", "in", "this", "world", "where",
            "find", "locate", "biome", "structure", "minecraft");

    private LocateTools() {
    }

    static List<GodTool> all() {
        return List.of(GodTool.async("locate",
                "Find the nearest structure (village, temple, stronghold, ancient city, ocean monument...) or biome "
                        + "(desert, cherry grove, mushroom fields...) in your world, far beyond what survey_area "
                        + "senses: returns its direction, distance and coordinates from a mortal, your vessel or a "
                        + "point. A general word searches every kind of it (village: all five) and names the "
                        + "nearest. Use it whenever a mortal asks where something is or which way to walk.",
                Schema.object()
                        .str("what", "What to find, in plain words: 'village', 'desert village', 'ocean monument', "
                                + "'stronghold', 'ancient city', 'cherry grove', 'desert', 'mushroom fields'.", true)
                        .str("from", "Where to measure from: " + Targets.TARGET_HELP + ". Default: your vessel if it "
                                + "is manifested, else the first mortal, else the world spawn.", false)
                        .choice("kind", "auto (default: whichever name fits the words best, structures before "
                                + "biomes), structure or biome.", false, List.of("auto", "structure", "biome")),
                LocateTools::locate));
    }

    // ---- The power -----------------------------------------------------------------------------------

    /** Where the search starts and what this server's registries hold, read on the server thread. */
    private record Scene(Location at, String label, String biomeHere, Map<String, Structure> structures,
            Map<String, Biome> biomes) {
    }

    private static ToolResult locate(Args a) throws ToolException {
        String given = a.str("what");
        String what = given.length() > 80 ? given.substring(0, 80) : given;
        String kind = a.str("kind", "auto").toLowerCase(Locale.ROOT);
        String from = a.str("from", null);
        Scene scene = Sync.call(() -> scene(from), CALL_MS);
        Choice choice = choose(what, kind, scene.structures().keySet(), scene.biomes().keySet());
        String asked = label(what);
        Location at = scene.at();
        World world = at.getWorld();

        if (choice.kind().equals("biome")) {
            Biome[] wanted = new Biome[choice.names().size()];
            for (int i = 0; i < wanted.length; i++) wanted[i] = scene.biomes().get(choice.names().get(i));
            BiomeSearchResult r = Sync.call(
                    () -> world.locateNearestBiome(at, BIOME_RADIUS, BIOME_STEP, BIOME_HEIGHT_STEP, wanted), CALL_MS);
            if (r == null) {
                return ToolResult.ok(notFound(asked, "biome", scene.label(), world.getName(), choice.names()));
            }
            String name = choice.names().get(0);
            for (String n : choice.names()) {
                if (scene.biomes().get(n).equals(r.getBiome())) name = n;
            }
            Location l = r.getLocation();
            return ToolResult.ok(found(asked, scene.label(), at.getX(), at.getZ(), world.getName(),
                    List.of(new Hit(name, l.getBlockX(), l.getBlockY(), l.getBlockZ(), true))));
        }

        List<String> names = choice.names();
        List<String> tooMany = List.of();
        if (names.size() > MAX_KINDS) {
            List<String> ordered = likeliestFirst(names, scene.biomeHere());
            names = ordered.subList(0, MAX_KINDS);
            tooMany = ordered.subList(MAX_KINDS, ordered.size());
        }
        List<Hit> hits = new ArrayList<>();
        List<String> searched = new ArrayList<>();
        List<String> late = new ArrayList<>();
        long started = System.nanoTime();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if ((System.nanoTime() - started) / 1_000_000 > BUDGET_MS) {
                late.add(name);
                continue;
            }
            Structure structure = scene.structures().get(name);
            StructureSearchResult r;
            try {
                r = Sync.call(() -> world.locateNearestStructure(at, structure, STRUCTURE_RADIUS, false), CALL_MS);
            } catch (ToolException e) {
                if (hits.isEmpty()) throw e;
                // The server is still busy with that search: tell what was found and stop asking it for more.
                late.addAll(names.subList(i, names.size()));
                break;
            }
            searched.add(name);
            if (r != null) {
                Location l = r.getLocation();
                hits.add(new Hit(name, l.getBlockX(), l.getBlockY(), l.getBlockZ(), false));
            }
        }
        StringBuilder out = new StringBuilder(hits.isEmpty()
                ? notFound(asked, "structure", scene.label(), world.getName(), searched)
                : found(asked, scene.label(), at.getX(), at.getZ(), world.getName(), hits));
        if (!late.isEmpty()) {
            out.append(" Not searched, the search was taking too long: ").append(String.join(", ", late))
                    .append(" (ask for one by name).");
        }
        if (!tooMany.isEmpty()) {
            out.append(" Not searched, too many kinds for one search: ").append(String.join(", ", tooMany))
                    .append(" (ask for one by name).");
        }
        return ToolResult.ok(out.toString());
    }

    private static Scene scene(String from) throws ToolException {
        Location at;
        String label;
        if (from == null) {
            Location vessel = God.vessel().location();
            List<Player> mortals = Realm.current().players();
            if (vessel != null) {
                at = vessel;
                label = "your vessel";
            } else if (!mortals.isEmpty()) {
                at = mortals.get(0).getLocation();
                label = mortals.get(0).getName();
            } else {
                at = Realm.current().world().getSpawnLocation();
                label = "the world spawn";
            }
        } else {
            at = Targets.location(from); // refuses a world outside this god's realm
            label = fromLabel(from, at);
        }
        if (at.getWorld() == null) throw new ToolException("That place is in no world.");
        String here = "";
        try {
            here = name(at.getWorld().getBiome(at).key().asString());
        } catch (RuntimeException e) {
            // Only used to choose which kinds to search first.
        }
        return new Scene(at, label, here, names(RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE)),
                names(RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME)));
    }

    private static <T extends org.bukkit.Keyed> Map<String, T> names(Registry<T> registry) {
        Map<String, T> out = new LinkedHashMap<>();
        registry.keyStream().sorted(Comparator.comparing(NamespacedKey::toString)).forEach(k -> {
            T value = registry.get(k);
            if (value != null) out.put(name(k.toString()), value);
        });
        return out;
    }

    /** What to call the place measured from. */
    private static String fromLabel(String from, Location at) {
        String lower = from.trim().toLowerCase(Locale.ROOT);
        if (List.of("vessel", "me", "self", "my vessel", "yourself").contains(lower)) return "your vessel";
        if (lower.equals("spawn")) return "the world spawn";
        try {
            if (Targets.parseCoords(from) != null) return "(" + at.getBlockX() + ", " + at.getBlockZ() + ")";
            Player p = Targets.player(from);
            if (p.getWorld().equals(at.getWorld()) && p.getLocation().distanceSquared(at) < 1) return p.getName();
        } catch (ToolException e) {
            // Not a mortal: a structure's name, said as given.
        }
        return from.trim();
    }

    // ---- Pure: words and names -----------------------------------------------------------------------

    /** A registry key as the god reads it: "minecraft:village_plains" is "village_plains", others keep their namespace. */
    static String name(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.startsWith("minecraft:") ? k.substring("minecraft:".length()) : k;
    }

    /** One word in the singular: "villages" and "village", "cities" and "city" are the same word. */
    static String stem(String word) {
        if (word.length() > 4 && word.endsWith("ies")) return word.substring(0, word.length() - 3) + "y";
        if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")) return word.substring(0, word.length() - 1);
        return word;
    }

    /**
     * The telling words of a request or of a registry name: lower case, split at anything that is not a letter
     * or digit, filler dropped ("the nearest"), singular, and a few common names turned into the game's own
     * ("temple" is a pyramid, a "witch hut" a swamp hut, a "mushroom island" mushroom fields).
     */
    static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.isEmpty() || FILLER.contains(raw)) continue;
            String w = stem(raw);
            if (FILLER.contains(w)) continue;
            out.add(w.equals("temple") ? "pyramid" : w);
        }
        if (out.contains("hut") && out.contains("witch")) out.set(out.indexOf("witch"), "swamp");
        if (out.contains("mushroom") && out.contains("island")) out.set(out.indexOf("island"), "field");
        return out;
    }

    /** The request as the result names it: its words without the filler, the last one singular ("desert village"). */
    static String label(String what) {
        List<String> out = new ArrayList<>();
        for (String raw : what.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!raw.isEmpty() && !FILLER.contains(raw) && !FILLER.contains(stem(raw))) out.add(raw);
        }
        if (out.isEmpty()) return what.trim();
        out.set(out.size() - 1, stem(out.get(out.size() - 1)));
        return String.join(" ", out);
    }

    /**
     * How well a registry name fits the asked words ({@link #words}): {@link #EXACT} when they are the same
     * words in any order ("desert village" and village_desert), {@link #KIND} when the name has every asked
     * word and more ("village" and village_plains), {@link #LOOSE} plus the name's length in words when the
     * whole name is among the asked words ("ocean monument" and monument), {@link #PART} when every asked
     * word is at least part of the name ("ship" and shipwreck), else 0.
     */
    static int score(List<String> asked, String name) {
        List<String> key = words(name);
        if (asked.isEmpty() || key.isEmpty()) return 0;
        Set<String> a = new HashSet<>(asked);
        Set<String> k = new HashSet<>(key);
        if (a.equals(k)) return EXACT;
        if (k.containsAll(a)) return KIND;
        if (a.containsAll(k)) return LOOSE + k.size();
        String flat = String.join("_", key);
        for (String w : asked) {
            if (w.length() < 3 || !flat.contains(w)) return 0;
        }
        return PART;
    }

    /**
     * The names that fit, best first: the name itself and every kind of it when there are any ("ruined portal"
     * is ruined_portal and its six variants), else only the names that fit best.
     */
    static List<String> matches(List<String> asked, Collection<String> names) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        int best = 0;
        for (String n : names) {
            int s = score(asked, n);
            if (s > 0) scores.put(n, s);
            best = Math.max(best, s);
        }
        int least = best >= KIND ? KIND : best;
        List<String> out = new ArrayList<>();
        scores.forEach((n, s) -> {
            if (s >= least) out.add(n);
        });
        out.sort(Comparator.comparing((String n) -> -scores.get(n)).thenComparing(Comparator.naturalOrder()));
        return out;
    }

    /** What will be searched: "structure" or "biome", and the registry names, best first. */
    record Choice(String kind, List<String> names) {
    }

    /**
     * Picks what the words mean. kind is auto, structure or biome; auto takes whichever registry has the better
     * fit, structures on a tie ("village" is the structures, "desert" the biome, "desert village" one structure).
     *
     * @throws ToolException phrased for the god, with the closest real names, when nothing fits
     */
    static Choice choose(String what, String kind, Collection<String> structures, Collection<String> biomes)
            throws ToolException {
        List<String> asked = words(what);
        if (asked.isEmpty()) {
            throw new ToolException("Say what to find: a structure or a biome, such as village, stronghold, desert "
                    + "or cherry grove.");
        }
        boolean wantStructures = !"biome".equals(kind);
        boolean wantBiomes = !"structure".equals(kind);
        List<String> s = wantStructures ? matches(asked, structures) : List.of();
        List<String> b = wantBiomes ? matches(asked, biomes) : List.of();
        int ss = s.isEmpty() ? 0 : score(asked, s.get(0));
        int bs = b.isEmpty() ? 0 : score(asked, b.get(0));
        if (ss == 0 && bs == 0) {
            List<String> pool = new ArrayList<>();
            if (wantStructures) pool.addAll(structures);
            if (wantBiomes) pool.addAll(biomes);
            String sort = wantStructures && wantBiomes ? "structure or biome" : wantStructures ? "structure" : "biome";
            throw new ToolException("No " + sort + " of this world is called '" + what.trim() + "'. Closest names: "
                    + String.join(", ", closest(asked, pool, 15)) + ". Ask with one of these, or a general word "
                    + "such as village, temple, ocean or forest.");
        }
        return ss >= bs ? new Choice("structure", s) : new Choice("biome", b);
    }

    /** Up to max names nearest in spelling to the asked words, nearest first, for a refusal. */
    static List<String> closest(List<String> asked, Collection<String> names, int max) {
        Map<String, Integer> far = new LinkedHashMap<>();
        for (String n : names) {
            int best = Integer.MAX_VALUE;
            for (String k : words(n)) {
                for (String w : asked) {
                    int d = k.contains(w) || (k.length() >= 4 && w.contains(k)) ? 0 : edits(w, k);
                    best = Math.min(best, d);
                }
            }
            far.put(n, best);
        }
        List<String> out = new ArrayList<>(far.keySet());
        out.sort(Comparator.comparing((String n) -> far.get(n)).thenComparing(Comparator.naturalOrder()));
        return out.size() > max ? out.subList(0, max) : out;
    }

    /** Edit distance between two words. */
    static int edits(String a, String b) {
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] row = new int[b.length() + 1];
            row[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int swap = prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                row[j] = Math.min(swap, Math.min(prev[j], row[j - 1]) + 1);
            }
            prev = row;
        }
        return prev[b.length()];
    }

    /**
     * When there are more kinds than one use may search: the kinds named after the biome the search starts in
     * come first (a camp in a forest is likeliest a forest camp), the rest keep their order.
     */
    static List<String> likeliestFirst(List<String> names, String biomeHere) {
        Set<String> here = new HashSet<>(words(biomeHere));
        List<String> out = new ArrayList<>(names);
        out.sort(Comparator.comparing((String n) -> {
            for (String w : words(n)) {
                if (here.contains(w)) return 0;
            }
            return 1;
        }));
        return out;
    }

    // ---- Pure: direction and the answer --------------------------------------------------------------

    /** The compass word for a step of dx (east is +x) and dz (south is +z), e.g. "north-east". */
    static String compass(double dx, double dz) {
        return Perception.facing((float) Math.toDegrees(Math.atan2(-dx, dz)));
    }

    /** A whole number of blocks with thousands separators: 1,180. */
    static String blocks(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    /** One thing found: its registry name and place. height is false when y is not known (structures). */
    record Hit(String name, int x, int y, int z, boolean height) {
        long distance(double fromX, double fromZ) {
            return Math.round(Math.hypot(x - fromX, z - fromZ));
        }

        /** "412 blocks north-east", or "right here" within 16 blocks. */
        String way(double fromX, double fromZ) {
            long d = distance(fromX, fromZ);
            return d < 16 ? "right here" : blocks(d) + " blocks " + compass(x - fromX, z - fromZ);
        }
    }

    /**
     * The answer the god relays: the nearest hit with its direction, distance and coordinates from the
     * starting point, then up to three other kinds that were found farther away.
     */
    static String found(String asked, String from, double fromX, double fromZ, String world, List<Hit> hits) {
        List<Hit> sorted = new ArrayList<>(hits);
        sorted.sort(Comparator.comparingLong((Hit h) -> h.distance(fromX, fromZ)).thenComparing(Hit::name));
        Hit first = sorted.get(0);
        StringBuilder sb = new StringBuilder("Nearest ").append(asked);
        if (!first.name().equals(asked.replace(' ', '_'))) sb.append(" (").append(first.name()).append(")");
        long d = first.distance(fromX, fromZ);
        sb.append(": ");
        if (d < 16) {
            sb.append("right here, within 16 blocks of ").append(from);
        } else {
            sb.append(blocks(d)).append(" blocks ").append(compass(first.x() - fromX, first.z() - fromZ))
                    .append(" of ").append(from);
        }
        sb.append(", at (").append(first.x()).append(", ").append(first.height() ? String.valueOf(first.y()) : "~")
                .append(", ").append(first.z()).append(" in ").append(world).append(").");
        if (sorted.size() > 1) {
            List<String> others = new ArrayList<>();
            for (Hit h : sorted.subList(1, Math.min(sorted.size(), 4))) {
                others.add(h.name() + " " + h.way(fromX, fromZ));
            }
            sb.append(" Next nearest kinds: ").append(String.join(", ", others)).append(".");
        }
        return sb.toString();
    }

    /** The honest "none": what was looked for, round where, and how far the search reached. */
    static String notFound(String asked, String kind, String from, String world, List<String> searched) {
        String names = searched.isEmpty() || (searched.size() == 1 && searched.get(0).equals(asked.replace(' ', '_')))
                ? "" : " (searched: " + String.join(", ", searched) + ")";
        if (kind.equals("biome")) {
            return "No " + asked + " within about " + blocks(BIOME_RADIUS) + " blocks of " + from + " in " + world
                    + names + ".";
        }
        return "No " + asked + " found around " + from + " in " + world + names + ". The search reaches thousands of "
                + "blocks for villages, temples and most structures, a few hundred for mineshafts, fossils and "
                + "buried treasure; a structure of another dimension is never found from here.";
    }
}
