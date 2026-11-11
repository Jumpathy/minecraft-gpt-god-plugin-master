package net.bigyous.gptgodmc.god.tools;

/** A tool could not run; the message is shown to the god so it can correct itself. */
public class ToolException extends Exception {
    public ToolException(String message) {
        super(message);
    }
}
