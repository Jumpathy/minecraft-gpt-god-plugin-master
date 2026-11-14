package net.bigyous.gptgodmc.god.living;

/** The limits on servants: how many, for how long, who they may be set on. Pure, so it is unit tested. */
public final class ServantRules {
    private ServantRules() {
    }

    /** How many servants a god of this power may keep at once. */
    public static int cap(String power, int perGod) {
        return "lesser".equals(power) ? Math.max(1, perGod / 2) : perGod;
    }

    /** Why a servant may not be summoned now, or null. */
    public static String refusal(boolean enabled, ServantKind kind, String power, int mine, int total, int perGod,
            int maxTotal) {
        if (!enabled) return "The keeper of this world forbids servants (servants.enabled is off).";
        if (kind == null) return "Name the servant: " + String.join(", ", ServantKind.ids()) + ".";
        int cap = cap(power, perGod);
        if (mine >= cap) {
            return "You already keep " + mine + " servant" + (mine == 1 ? "" : "s") + " (your limit is " + cap
                    + "); dismiss one first.";
        }
        if (total >= maxTotal) return "The world holds as many servants of the gods as it can bear (" + maxTotal + ").";
        return null;
    }

    /** Minutes a servant stays: default 10, at most the server's limit. */
    public static int minutes(int asked, int max) {
        if (asked <= 0) return Math.min(10, max);
        return Math.max(1, Math.min(max, asked));
    }

    // ---- Holding up in the world (the strings are Bukkit's enum names, so this stays pure) -----------------

    /**
     * Harm that only comes from how a servant moves: it is steered by teleport, with no pathfinding and no
     * falling, so it can be walked into a wall, a pond or a cactus through no fault of its own. A servant
     * takes none of it. (Fire, lava, blasts, magic and every blow still hurt.)
     */
    private static final java.util.Set<String> STEERING = java.util.Set.of("SUFFOCATION", "CRAMMING", "FALL",
            "FLY_INTO_WALL", "DROWNING", "CONTACT", "HOT_FLOOR");

    /** Whether a servant takes no harm from this cause (an EntityDamageEvent.DamageCause name). */
    public static boolean shrugsOff(String cause) {
        return cause != null && STEERING.contains(cause);
    }

    /**
     * Whether a creature may make a servant its prey for this reason (an EntityTargetEvent.TargetReason name).
     * Creatures do not go hunting the servants of a god (zombies and skeletons would, for a guard is an iron
     * golem and a priest a villager underneath); one that a servant has struck may turn on it.
     */
    public static boolean mayHunt(String reason) {
        return !"CLOSEST_ENTITY".equals(reason) && !"RANDOM_TARGET".equals(reason);
    }

    /**
     * The share of an ordinary creature's blow (a zombie's fist, a skeleton's arrow, a creeper's blast) that
     * a servant feels. A temple guard is made to stand among monsters; angels and shades are hardier than
     * flesh; imps and priests are as frail as they look. Mortals and their tamed beasts always strike in full.
     */
    public static double mobHarm(ServantKind kind) {
        return switch (kind) {
            case GUARD -> 0.25;
            case ANGEL, SHADE -> 0.5;
            case IMP, PRIEST -> 1;
        };
    }

    /** "WITHER_SKELETON" -> "Wither Skeleton". */
    public static String creature(String type) {
        StringBuilder sb = new StringBuilder();
        for (String word : type.toLowerCase(java.util.Locale.ROOT).split("_")) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
        }
        return sb.toString();
    }

    /**
     * How a servant's end is told to its god and the journal: "slain by Zombie", "slain by Brakk",
     * "destroyed by an explosion". `killer` is who dealt the last blow (null when no one did), `cause` the
     * last harm's DamageCause name (null when unknown).
     */
    public static String death(String killer, String cause) {
        if (killer != null && !killer.isBlank()) return "slain by " + killer.trim();
        if (cause == null) return "slain by something unseen";
        return switch (cause) {
            case "BLOCK_EXPLOSION", "ENTITY_EXPLOSION" -> "destroyed by an explosion";
            case "FIRE", "FIRE_TICK", "LAVA", "CAMPFIRE", "HOT_FLOOR" -> "burned away";
            case "LIGHTNING" -> "struck down by lightning";
            case "SUFFOCATION", "CRAMMING" -> "crushed inside a wall";
            case "FALLING_BLOCK" -> "crushed by a falling block";
            case "DROWNING" -> "drowned in deep water";
            case "FALL", "FLY_INTO_WALL" -> "broken by a fall";
            case "VOID" -> "lost to the void";
            case "KILL" -> "unmade by a command (/kill)";
            case "MAGIC", "POISON", "WITHER" -> "undone by magic";
            default -> "destroyed by " + cause.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        };
    }

    /**
     * A note for the god when it sets a servant on a mortal it does not hate (it may, but it should mostly
     * send them against those it despises). Null when the mortal is hated.
     */
    public static String attackNote(String player, int favor) {
        if (favor <= -3) return null;
        if (favor >= 5) return player + " is one of your favored (favor " + favor + "); your faithful will see "
                + "this as betrayal.";
        return player + " is not one you despise (favor " + favor + "); mortals will wonder why.";
    }
}
