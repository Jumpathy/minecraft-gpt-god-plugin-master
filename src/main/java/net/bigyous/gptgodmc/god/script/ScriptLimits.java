package net.bigyous.gptgodmc.god.script;

import java.util.Locale;

/**
 * What one script may spend, its event handlers included (config.yml `scripts:`). Every value is clamped
 * to a sane range here, so no config can switch a limit off.
 *
 * @param instructions    interpreter steps in all
 * @param seconds         seconds of computing (waits and the server's own work are not counted)
 * @param waitTicks       ticks it may spend in wait() in all
 * @param hostCalls       calls into world.* and players.* in all
 * @param powerCalls      uses of power() (world.explode and world.spawn are such uses)
 * @param commands        uses of command()
 * @param blocks          blocks set or filled
 * @param entities        creatures spawned and lightning bolts called
 * @param listeners       on() / every() handlers alive at once
 * @param listenerSeconds how long handlers stay alive after the script's last line
 * @param events          handler calls in all
 * @param displays        shapes hung in the air (world.display)
 */
public record ScriptLimits(int instructions, int seconds, int waitTicks, int hostCalls, int powerCalls, int commands,
        int blocks, int entities, int listeners, int listenerSeconds, int events, int displays) {

    /** Longest program text, in characters. */
    public static final int MAX_CODE = 12_000;
    /** Longest log a script returns, in characters. */
    public static final int MAX_LOG = 4_000;

    public static final ScriptLimits DEFAULT = new ScriptLimits(5_000_000, 5, 600, 400, 24, 60, 2000, 12, 4, 120, 400, 64);

    public ScriptLimits {
        instructions = clamp(instructions, 10_000, 50_000_000);
        seconds = clamp(seconds, 1, 60);
        waitTicks = clamp(waitTicks, 0, 6000);
        hostCalls = clamp(hostCalls, 1, 10_000);
        powerCalls = clamp(powerCalls, 0, 200);
        commands = clamp(commands, 0, 500);
        blocks = clamp(blocks, 0, 100_000);
        entities = clamp(entities, 0, 200);
        listeners = clamp(listeners, 0, 16);
        listenerSeconds = clamp(listenerSeconds, 1, 1800);
        events = clamp(events, 0, 10_000);
        displays = clamp(displays, 0, 512);
    }

    /** Without a number of shapes: the default. Kept for callers written before shapes existed. */
    public ScriptLimits(int instructions, int seconds, int waitTicks, int hostCalls, int powerCalls, int commands, int blocks,
            int entities, int listeners, int listenerSeconds, int events) {
        this(instructions, seconds, waitTicks, hostCalls, powerCalls, commands, blocks, entities, listeners, listenerSeconds, events, 64);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** One line for the god: what a script may spend. */
    public String describe() {
        return String.format(Locale.ROOT, "%,d steps, %d s of computing, %d ticks of waiting, %d world/players "
                + "calls, %d power() uses, %d commands, %d blocks, %d creatures or bolts, %d shapes, %d handlers living "
                + "%d s, %d handler calls", instructions, seconds, waitTicks, hostCalls, powerCalls, commands, blocks, entities,
                displays, listeners, listenerSeconds, events);
    }
}
