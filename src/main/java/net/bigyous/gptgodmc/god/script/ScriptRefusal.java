package net.bigyous.gptgodmc.god.script;

/** The world said no to one call (unknown block, a power's limit, a mortal who is not there). The script may catch it. */
public final class ScriptRefusal extends Exception {
    private static final long serialVersionUID = 1L;

    public ScriptRefusal(String message) {
        super(message);
    }
}
