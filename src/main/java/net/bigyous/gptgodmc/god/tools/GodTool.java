package net.bigyous.gptgodmc.god.tools;

import java.util.function.Supplier;

/**
 * One of the god's powers.
 *
 * @param describer  the description; read when the tool list is built, so a power can describe
 *                   things that come from config.yml (see {@link CustomPowerTools})
 * @param mainThread true when the handler touches the world and must run on the server thread;
 *                   false for handlers that schedule their own main-thread work (e.g. rendering).
 */
public record GodTool(String name, Supplier<String> describer, Schema schema, boolean mainThread, Handler handler) {

    @FunctionalInterface
    public interface Handler {
        ToolResult run(Args args) throws ToolException;
    }

    public String description() {
        return describer.get();
    }

    public static GodTool sync(String name, String description, Schema schema, Handler handler) {
        return new GodTool(name, () -> description, schema, true, handler);
    }

    /** A main-thread power whose description is computed (e.g. from config). */
    public static GodTool sync(String name, Supplier<String> description, Schema schema, Handler handler) {
        return new GodTool(name, description, schema, true, handler);
    }

    public static GodTool async(String name, String description, Schema schema, Handler handler) {
        return new GodTool(name, () -> description, schema, false, handler);
    }
}
