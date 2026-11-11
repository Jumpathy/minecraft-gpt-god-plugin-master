package net.bigyous.gptgodmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.loggables.ChatLoggable;
import net.bigyous.gptgodmc.loggables.GenericEventLoggable;

class EventLoggerTest {

    @Test
    void takenEventsCanBeRestoredAfterAFailedThought() {
        EventLogger.reset();
        EventLogger.addLoggable(new GenericEventLoggable("Steve planted a tree"));
        EventLogger.addLoggable(new ChatLoggable("Steve", "hello there"));
        EventLogger.Taken taken = EventLogger.take();
        assertEquals(0, EventLogger.pendingCount());
        EventLogger.addLoggable(new GenericEventLoggable("Alex arrived"));
        EventLogger.restore(taken);
        EventLogger.Drained d = EventLogger.drain();
        assertEquals(2, d.events().size());
        assertTrue(d.events().get(0).contains("Steve planted a tree"), d.events().toString());
        assertEquals(1, d.speech().size());
        assertTrue(d.speech().get(0).contains("hello there"));
        assertEquals(0, EventLogger.pendingCount());
    }

    @Test
    void nullLogsAreSkipped() {
        EventLogger.reset();
        EventLogger.addLoggable(new GenericEventLoggable(null));
        assertEquals(0, EventLogger.pendingCount());
    }
}
