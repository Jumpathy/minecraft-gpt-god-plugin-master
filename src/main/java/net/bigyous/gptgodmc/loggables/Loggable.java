package net.bigyous.gptgodmc.loggables;

import java.time.Instant;

public interface Loggable {
    /** How the god's perception groups an event. */
    enum Category {
        /** Something a mortal did. */
        EVENT,
        /** Words spoken in chat or voice. */
        SPEECH,
        /** Words explicitly addressed to the god (/pray, touching the vessel...). */
        PRAYER,
        /** Instructions from the server operator (/god say). */
        DIRECTIVE
    }

    /**
     * Get the string representation of this log that can be fed to the god.
     * Returning null means the event is not worth logging.
     */
    String getLog();

    /**
     * Attempt to combine two loggables.
     * E.g. two item pickup events can be combined.
     * Returns false if they were not combined, true if they were.
     * The loggable on which combine was called, e.g. loggable1.combine(...),
     * will then take into account both logs.
     */
    boolean combine(Loggable l);

    Instant getRawInstant();

    default Category category() {
        return Category.EVENT;
    }

    /**
     * Non-null when this event should make the god look at the world soon rather than
     * at the next regular heartbeat (a prayer, a death, someone saying the god's name).
     */
    default String attentionReason() {
        return null;
    }

    /**
     * True for things the god should see at its next look but that never make it look by themselves
     * (what another god did, for instance), so several gods do not keep waking one another.
     */
    default boolean passive() {
        return false;
    }

    /** False for events that are not a mortal's deed (another god's act): they are not kept as deeds. */
    default boolean remembered() {
        return true;
    }
}
