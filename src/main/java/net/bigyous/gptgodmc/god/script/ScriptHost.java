package net.bigyous.gptgodmc.god.script;

import java.util.List;
import java.util.Map;

/**
 * Everything a script can reach outside itself. The interpreter knows only this interface, so it runs (and
 * is tested) without a server; the real one is the plugin's, on top of Paper.
 */
public interface ScriptHost {
    /**
     * One call a script made.
     *
     * @param fn   its name in {@link ScriptApi}, e.g. "world.particle"; "world.place" carries {@link Placement}s
     * @param args plain values: null, Boolean, Double, String, List, Map
     * @param line the script line that made it
     */
    record Call(String fn, List<Object> args, int line) {
    }

    /** One block to place. */
    record Placement(int x, int y, int z, String block) {
    }

    /** What a call gave back: a plain value, or why the world refused it. */
    record Outcome(Object value, String refused) {
        public static Outcome ok(Object value) {
            return new Outcome(value, null);
        }

        public static Outcome no(String why) {
            return new Outcome(null, why == null || why.isBlank() ? "refused" : why);
        }
    }

    /** Checks a block id and gives its proper form ("Oak Planks" becomes "oak_planks"). Any thread. */
    String block(String id) throws ScriptRefusal;

    /** Carries out these calls in order, in one visit to the world. One outcome for each, in the same order. */
    List<Outcome> perform(List<Call> calls);

    /** Uses one of the god's own powers, exactly as the god would. Gives {ok, text}. */
    Map<String, Object> power(String name, Map<String, Object> args) throws ScriptRefusal;

    /** Lets this many server ticks pass. */
    void sleep(int ticks) throws InterruptedException;
}
