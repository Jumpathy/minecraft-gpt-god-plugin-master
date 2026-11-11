package net.bigyous.gptgodmc.loggables;

import java.time.Instant;

public class BaseLoggable implements Loggable {
    private Instant timestamp;

    public BaseLoggable() {
        timestamp = Instant.now();
    }

    public BaseLoggable(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getLog() {
        return null;
    }

    public Instant getRawInstant() {
        return timestamp;
    }

    public void setTimestamp(Instant time) {
        this.timestamp = time;
    }

    public boolean combine(Loggable l) {
        return false;
    }
}
