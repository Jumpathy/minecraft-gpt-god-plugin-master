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
