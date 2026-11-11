package net.bigyous.gptgodmc.god;

/**
 * The god's wounds and death, kept inside memory.json so a slain god stays dead across restarts.
 * Only the Godslayer blade can wound it (see {@link Godslayer}). Health is the configured maximum
 * minus the wounds taken, so changing godslayer.health never leaves it above its maximum. Wounds
 * heal slowly once it has been left alone a while. Pure and thread-safe, so it is unit tested.
 */
public final class Mortality {
    /** Damage taken and not yet healed (0 = whole). */
    public double wounds;
    /** When it was last wounded (0 = never). */
    public long lastWoundAt;
    /** Healing has been applied up to this moment. */
    public long healedTo;
    /** True while it lies slain. */
    public boolean slain;
    /** When it was slain, and by whom (while slain). */
    public long slainAt;
    public String slainBy;
    /** How many times it has been slain, and the last slayer and time (kept after it returns). */
    public int deaths;
    public String lastSlayer;
    public long lastSlainAt;
    /** When it last came back from death (0 = never). */
    public long revivedAt;

    /** What one blow did. */
    public record Wound(double amount, double health, double max, boolean killed) {
    }

    public synchronized double health(double max) {
        if (slain) return 0;
        return Math.max(0, Math.min(max, max - wounds));
    }

    /** Alive with wounds not yet healed. */
    public synchronized boolean hurt() {
        return !slain && wounds > 0.001;
    }

    public synchronized boolean isSlain() {
        return slain;
    }

    /**
     * One blow of the Godslayer. Null when it is already dead. A blow that leaves no health kills it.
     */
    public synchronized Wound wound(double amount, String by, double max, long now) {
        if (slain || amount <= 0) return null;
        wounds = Math.min(max, Math.max(0, wounds) + amount);
        lastWoundAt = now;
        healedTo = now;
        boolean killed = wounds >= max - 0.001;
        if (killed) {
            slain = true;
            slainAt = now;
            slainBy = by;
            lastSlayer = by;
            lastSlainAt = now;
            deaths++;
            wounds = max;
        }
        return new Wound(amount, health(max), max, killed);
    }

    /**
     * Heals what time allows: nothing until delayMs after the last blow, then perMinute health a minute.
     *
     * @return true when the health changed
     */
    public synchronized boolean heal(long now, long delayMs, double perMinute) {
        if (slain || wounds <= 0 || perMinute <= 0) return false;
        long start = Math.max(lastWoundAt + delayMs, healedTo);
        if (now <= start) return false;
        wounds -= perMinute * (now - start) / 60_000.0;
        healedTo = now;
        if (wounds < 0.001) wounds = 0;
        return true;
    }

    /** True when a slain god may come back by itself now. */
    public synchronized boolean returnDue(long now, boolean returns, double minutes) {
        return slain && returns && now - slainAt >= minutes * 60_000.0;
    }

    /** Milliseconds until it returns by itself, or -1 when it will not. */
    public synchronized long untilReturn(long now, boolean returns, double minutes) {
        if (!slain || !returns) return -1;
        return Math.max(0, slainAt + (long) (minutes * 60_000.0) - now);
    }

    /** Comes back whole. It keeps the memory of who slew it ({@link #lastSlayer}). */
    public synchronized void revive(long now) {
        slain = false;
        slainBy = null;
        slainAt = 0;
        wounds = 0;
        healedTo = now;
        revivedAt = now;
    }

    /**
     * How hard one blow lands: between min and max (roll 0..1 picks where), scaled by how charged
     * the swing was (a spammed click does a fifth).
     */
    public static double damageFor(double min, double max, double charge, double roll) {
        double lo = Math.max(0, Math.min(min, max));
        double hi = Math.max(min, max);
        double c = Math.max(0, Math.min(1, charge));
        double r = Math.max(0, Math.min(1, roll));
        return (lo + (hi - lo) * r) * (0.2 + 0.8 * c);
    }

    /** Health as a whole number, for text. */
    public static String fmt(double v) {
        return String.valueOf((int) Math.ceil(v - 1e-6));
    }

    /** The lines the god reads about its own body and death. */
    public synchronized String describe(double max, long now) {
        StringBuilder sb = new StringBuilder();
        if (slain) {
            sb.append("You are SLAIN (by ").append(slainBy).append(", ").append(Memory.age(now - slainAt))
                    .append(").");
            return sb.toString();
        }
        sb.append("Health ").append(fmt(health(max))).append("/").append(fmt(max)).append(".");
        if (hurt()) {
            sb.append(" WOUNDED, last struck ").append(Memory.age(now - lastWoundAt))
                    .append("; your wounds close slowly while no one strikes you.");
        }
        sb.append(" Only the Godslayer blade can wound your vessel; at 0 health you die and stay dead.");
        if (deaths > 0) {
            sb.append(" You have been slain ").append(deaths == 1 ? "once" : deaths + " times")
                    .append(", last by ").append(lastSlayer).append(" ").append(Memory.age(now - lastSlainAt))
                    .append(", and returned.");
        }
        return sb.toString();
    }
}
