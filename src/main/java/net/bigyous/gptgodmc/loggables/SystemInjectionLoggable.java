package net.bigyous.gptgodmc.loggables;

public class SystemInjectionLoggable extends BaseLoggable {
    private final String message;

    public SystemInjectionLoggable(String message) {
        this.message = message;
    }

    @Override
    public String getLog() {
        return getFormattedTimestamp() + "[THE SYSTEM, YOUR CREATOR AND MASTER]: " + message;
    }

    // Divine commands never merge with anything
    @Override
    public boolean combine(Loggable other) {
        return false;
    }
}
