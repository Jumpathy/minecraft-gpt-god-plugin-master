package net.bigyous.gptgodmc.god.script;

/**
 * Ends a script from outside its own control: a budget ran out, it was stopped, or the server is closing.
 * An Error on purpose: the interpreter lets no script catch it, and runs none of its finally blocks.
 */
public final class ScriptStop extends Error {
    private static final long serialVersionUID = 1L;
    private int line;

    public ScriptStop(String message) {
        super(message, null, false, false);
    }

    /** The script line it was on, or 0 when unknown. */
    public int line() {
        return line;
    }

    ScriptStop at(int line) {
        if (this.line == 0) this.line = line;
        return this;
    }
}
