package net.bigyous.gptgodmc.loggables;

import java.util.ArrayList;
import java.util.List;

/** A mortal gave the god something: at the vessel's feet, or lifted to the sky with /offer. */
public class OfferingLoggable extends BaseLoggable {
    public static final String AT_VESSEL = "vessel";
    public static final String TO_SKY = "sky";

    private final String player;
    private final String where;
    private final List<String> gifts = new ArrayList<>();
    private final boolean wake;
    private boolean freeOnly;
    private int total;
    /** Set once the event is in the god's log; from then on, each read means the god may have seen it. */
    private boolean armed;
    private int reads;

    /**
     * @param what     the gift, already described ("12 diamond", "diamond_sword named \"Oathkeeper\"
     *                 (sharpness 3)")
     * @param free     true when it cost them nothing (creative mode)
     * @param total    how many offerings this mortal has made in all, this one included
     * @param wake     false to let a quick follow-up offering wait for the god's next look
     */
    public OfferingLoggable(String player, String what, String where, boolean free, int total, boolean wake) {
        this.player = player;
        this.where = where;
        this.freeOnly = free;
        this.total = total;
        this.wake = wake;
        gifts.add(what);
    }

    /** Called right after this event was logged. */
    public synchronized void arm() {
        armed = true;
    }

    /** Whether the god may already have read this event (then a later gift needs an event of its own). */
    public synchronized boolean seen() {
        return reads > 0;
    }

    @Override
    public synchronized String getLog() {
        if (armed) reads++;
        StringBuilder sb = new StringBuilder(player).append(" made ")
                .append(gifts.size() == 1 ? "an OFFERING" : gifts.size() + " OFFERINGS").append(" to you: ")
                .append(String.join(", ", gifts));
        sb.append(AT_VESSEL.equals(where) ? ", laid at your vessel's feet" : ", lifted up to the sky");
        if (freeOnly) {
            sb.append(" (conjured in creative mode: it cost them nothing)");
        }
        sb.append(". The gift is yours; it was taken from them.");
        if (total > gifts.size()) {
            sb.append(" They have made ").append(total).append(" offerings in all.");
        } else if (total == 1) {
            sb.append(" Their first offering ever.");
        }
        return sb.toString();
    }

    @Override
    public synchronized boolean combine(Loggable l) {
        if (!(l instanceof OfferingLoggable o) || o == this || reads > 0 || !o.player.equals(player)
                || !o.where.equals(where)) {
            return false;
        }
        gifts.addAll(o.gifts);
        freeOnly = freeOnly && o.freeOnly;
        total = Math.max(total, o.total);
        return true;
    }

    public String player() {
        return player;
    }

    @Override
    public Category category() {
        return Category.PRAYER;
    }

    @Override
    public String attentionReason() {
        return wake ? player + " made you an offering" : null;
    }
}
