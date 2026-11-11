package net.bigyous.gptgodmc.god.hooks;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What the god may ask of WorldEdit, checked before WorldEdit is touched. Pure: no Bukkit and no WorldEdit
 * classes, so it is unit tested and loads on servers without WorldEdit.
 */
public final class WorldEditRules {
    private WorldEditRules() {
    }

    public static final List<String> ACTIONS = List.of("set", "replace", "walls", "faces", "hollow", "sphere", "cylinder",
            "pyramid", "line", "generate", "smooth", "stack", "move", "undo");
    /** Actions that work on the box between "from" and "to". */
    public static final Set<String> REGION = Set.of("set", "replace", "walls", "faces", "hollow", "generate", "smooth", "stack", "move");
    /** Actions that raise a shape at "at". */
    public static final Set<String> SHAPES = Set.of("sphere", "cylinder", "pyramid");
    /** Actions that need no pattern. */
    public static final Set<String> NO_PATTERN = Set.of("smooth", "stack", "move", "undo");
    public static final List<String> DIRECTIONS = List.of("north", "south", "east", "west", "up", "down");
    /** The longest side of a region, in blocks. */
    public static final int MAX_SPAN = 256;
    /** A region may hold this many times the block limit (walls and shapes change far fewer blocks than they span). */
    public static final int VOLUME_FACTOR = 8;

    private static final Set<String> FORBIDDEN = Set.of("bedrock", "barrier", "command_block", "chain_command_block",
            "repeating_command_block", "structure_block", "structure_void", "jigsaw", "end_portal", "end_gateway", "light");

    /** Thrown with a message meant for the god. */
    public static final class Refusal extends Exception {
        private static final long serialVersionUID = 1L;

        public Refusal(String message) {
            super(message);
        }
    }

    /** One act, already checked. Corners and places are block coordinates; unused parts are null or 0. */
    public record Request(String action, int[] from, int[] to, int[] at, String pattern, String mask, int radius, int height,
            boolean hollow, String expression, int[] direction, int count, int iterations) {
    }

    /** Is this block where the god's hand must not fall (a mortal's structure)? */
    @FunctionalInterface
    public interface Protected {
        boolean at(int x, int y, int z);
    }

    /** The unit step of a direction word. */
    public static int[] direction(String word) throws Refusal {
        return switch (word == null ? "" : word.trim().toLowerCase(Locale.ROOT)) {
            case "north" -> new int[] { 0, 0, -1 };
            case "south" -> new int[] { 0, 0, 1 };
            case "east" -> new int[] { 1, 0, 0 };
            case "west" -> new int[] { -1, 0, 0 };
            case "up" -> new int[] { 0, 1, 0 };
            case "down" -> new int[] { 0, -1, 0 };
            default -> throw new Refusal("direction must be one of " + String.join(", ", DIRECTIONS) + ".");
        };
    }

    public static long volume(int[] from, int[] to) {
        long v = 1;
        for (int k = 0; k < 3; k++) v *= Math.abs((long) to[k] - from[k]) + 1;
        return v;
    }

    /** Refuses a region that is too long on a side or holds far more blocks than may ever be changed. */
    public static void checkRegion(int[] from, int[] to, int maxBlocks) throws Refusal {
        for (int k = 0; k < 3; k++) {
            long span = Math.abs((long) to[k] - from[k]) + 1;
            if (span > MAX_SPAN) {
                throw new Refusal("That region is " + span + " blocks along one side; at most " + MAX_SPAN + ". Work in smaller pieces.");
            }
        }
        long v = volume(from, to);
        if (v > (long) maxBlocks * VOLUME_FACTOR) {
            throw new Refusal("That region holds " + v + " blocks; you may change at most " + maxBlocks
                    + " in one act. Choose a smaller region.");
        }
    }

    /** Refuses a pattern that names a block the god may never place. */
    public static void checkPattern(String pattern) throws Refusal {
        if (pattern == null || pattern.isBlank()) {
            throw new Refusal("Give a pattern: a block such as stone, or a mix such as 50%stone,50%andesite.");
        }
        if (pattern.length() > 200) throw new Refusal("That pattern is too long.");
        for (String word : pattern.toLowerCase(Locale.ROOT).split("[^a-z0-9_:]+")) {
            String id = word.startsWith("minecraft:") ? word.substring(10) : word;
            if (FORBIDDEN.contains(id)) throw new Refusal("You may not place " + id + ".");
        }
    }

    /** Checks a whole request (all but what only WorldEdit can judge: its patterns, masks and expressions). */
    public static void check(Request r, int maxBlocks) throws Refusal {
        if (!ACTIONS.contains(r.action())) throw new Refusal("Unknown action '" + r.action() + "'. Actions: " + String.join(", ", ACTIONS) + ".");
        if (r.action().equals("undo")) return;
        if (!NO_PATTERN.contains(r.action())) checkPattern(r.pattern());
        if (REGION.contains(r.action()) || r.action().equals("line")) {
            if (r.from() == null || r.to() == null) throw new Refusal(r.action() + " needs both corners: from and to, each 'x y z'.");
            checkRegion(r.from(), r.to(), maxBlocks);
        }
        if (SHAPES.contains(r.action())) {
            if (r.at() == null) throw new Refusal(r.action() + " needs a place: at.");
            if (r.radius() < 1) throw new Refusal(r.action() + " needs a radius (a pyramid: its height in layers).");
            long r1 = r.radius(), h = Math.max(1, r.height());
            long reach = switch (r.action()) {
                case "sphere" -> 5 * r1 * r1 * r1;
                case "cylinder" -> 4 * r1 * r1 * h;
                default -> 2 * r1 * r1 * r1;
            };
            if (reach > (long) maxBlocks * VOLUME_FACTOR) throw new Refusal("That " + r.action() + " is too large; use a smaller radius.");
        }
        if (r.action().equals("replace") && (r.mask() == null || r.mask().isBlank())) {
            throw new Refusal("replace needs a mask: which blocks to replace, e.g. grass_block or #logs.");
        }
        if (r.action().equals("generate")) {
            if (r.expression() == null || r.expression().isBlank()) {
                throw new Refusal("generate needs an expression in x, y, z (each -1 to 1 across the region), e.g. x^2+y^2+z^2 < 1.");
            }
            if (r.expression().length() > 300) throw new Refusal("That expression is too long.");
        }
        if (r.action().equals("stack") || r.action().equals("move")) {
            if (r.direction() == null) throw new Refusal(r.action() + " needs a direction: " + String.join(", ", DIRECTIONS) + ".");
            if (r.count() < 1) throw new Refusal(r.action() + " needs a count of 1 or more.");
            long touched = volume(r.from(), r.to()) * (r.action().equals("stack") ? r.count() : 2);
            if (touched > maxBlocks) {
                throw new Refusal("That would change about " + touched + " blocks; at most " + maxBlocks + ". Use a smaller region or count.");
            }
        }
    }
}
