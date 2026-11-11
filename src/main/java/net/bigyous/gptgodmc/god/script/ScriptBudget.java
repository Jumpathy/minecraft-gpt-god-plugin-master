package net.bigyous.gptgodmc.god.script;

import java.util.Locale;

/**
 * What a running script has spent of its {@link ScriptLimits}. Spending past a limit throws
 * {@link ScriptStop}, which no script can catch. Used by the script's own thread only.
 */
public final class ScriptBudget {
    private final ScriptLimits limits;
    private long steps;
    private long activeNanos;
    private long resumedAt = -1;
    private int waited, hostCalls, powerCalls, commands, blocks, entities, events, displays;

    public ScriptBudget(ScriptLimits limits) {
        this.limits = limits;
    }

    public ScriptLimits limits() {
        return limits;
    }

    /** The script starts (or goes back to) computing. */
    public void resume() {
        if (resumedAt < 0) resumedAt = System.nanoTime();
    }

    /** The script stops computing for a while (a wait, the world's work, the time between events). */
    public void pause() {
        if (resumedAt >= 0) {
            activeNanos += System.nanoTime() - resumedAt;
            resumedAt = -1;
        }
    }

    /** Called by the interpreter every few thousand steps. */
    public void steps(int n) {
        steps += n;
        if (steps > limits.instructions()) {
            throw new ScriptStop(String.format(Locale.ROOT, "it ran more than %,d steps (an endless loop?)", limits.instructions()));
        }
        long active = activeNanos + (resumedAt < 0 ? 0 : System.nanoTime() - resumedAt);
        if (active > limits.seconds() * 1_000_000_000L) {
            throw new ScriptStop("it computed for more than " + limits.seconds() + " seconds");
        }
    }

    public void hostCall() {
        if (++hostCalls > limits.hostCalls()) {
            throw new ScriptStop("it made more than " + limits.hostCalls() + " world and players calls");
        }
    }

    public void powerCall() {
        if (++powerCalls > limits.powerCalls()) throw new ScriptStop("it used power() more than " + limits.powerCalls() + " times");
    }

    public void command() {
        if (++commands > limits.commands()) throw new ScriptStop("it ran more than " + limits.commands() + " commands");
    }

    public void blocks(int n) {
        blocks += n;
        if (blocks > limits.blocks()) throw new ScriptStop("it set more than " + limits.blocks() + " blocks");
    }

    public int blocksLeft() {
        return Math.max(0, limits.blocks() - blocks);
    }

    public void entities(int n) {
        entities += Math.max(1, n);
        if (entities > limits.entities()) {
            throw new ScriptStop("it called more than " + limits.entities() + " creatures and lightning bolts");
        }
    }

    public void displays(int n) {
        displays += Math.max(1, n);
        if (displays > limits.displays()) throw new ScriptStop("it hung more than " + limits.displays() + " shapes in the air");
    }

    public void waited(int ticks) {
        waited += ticks;
        if (waited > limits.waitTicks()) throw new ScriptStop("it waited more than " + limits.waitTicks() + " ticks in all");
    }

    public void event() {
        if (++events > limits.events()) {
            throw new ScriptStop("its handlers were called more than " + limits.events() + " times");
        }
    }

    public long stepsUsed() {
        return steps;
    }

    /** "1,204 steps, 12 world/players calls, 40 blocks" */
    public String spent() {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%,d steps", steps));
        add(sb, hostCalls, "world/players call", "world/players calls");
        add(sb, blocks, "block", "blocks");
        add(sb, entities, "creature or bolt", "creatures or bolts");
        add(sb, displays, "shape", "shapes");
        add(sb, powerCalls, "power", "powers");
        add(sb, commands, "command", "commands");
        add(sb, waited, "tick waited", "ticks waited");
        add(sb, events, "handler call", "handler calls");
        return sb.toString();
    }

    private static void add(StringBuilder sb, int n, String one, String many) {
        if (n > 0) sb.append(", ").append(n).append(' ').append(n == 1 ? one : many);
    }
}
