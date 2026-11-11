package net.bigyous.gptgodmc.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.loggables.Loggable;

/** The World tab's feed: one entry per world event, and what each god did with its copy. */
class EventFeedTest {
    /** A plain event for tests. */
    record Ev(String text, Category cat, String attention) implements Loggable {
        public String getLog() {
            return text;
        }

        public boolean combine(Loggable l) {
            return false;
        }

        public Instant getRawInstant() {
            return Instant.ofEpochMilli(1000);
        }

        public Category category() {
            return cat;
        }

        public String attentionReason() {
            return attention;
        }
    }

    @BeforeEach
    void on() {
        EventFeed.clear();
        MindTrace.setEnabled(true);
    }

    @AfterEach
    void off() {
        MindTrace.setEnabled(false);
        EventFeed.clear();
    }

    static Ev ev(String text) {
        return new Ev(text, Loggable.Category.EVENT, null);
    }

    static JsonArray all() {
        return EventFeed.since(0, 100).getAsJsonArray("events");
    }

    @Test
    void copiesOfOneEventShareAnEntryAndTrackEachGod() {
        Ev forMain = ev("Ash died to a creeper"), forSea = ev("Ash died to a creeper (far away)");
        Object g = EventFeed.openGroup();
        EventFeed.logged("main", forMain, null, forMain.text(), 1000);
        EventFeed.logged("sea", forSea, null, forSea.text(), 1000);
        EventFeed.closeGroup(g);
        Ev alone = ev("Bram placed a torch");
        EventFeed.logged("main", alone, null, alone.text(), 2000);

        JsonArray a = all();
        assertEquals(2, a.size(), "the grouped copies are one entry");
        JsonObject first = a.get(0).getAsJsonObject();
        assertEquals(2, first.getAsJsonArray("gods").size());
        assertEquals("pending", first.getAsJsonArray("gods").get(0).getAsJsonObject().get("state").getAsString());
        assertTrue(first.getAsJsonArray("gods").get(1).getAsJsonObject().has("text"), "a different wording is kept");

        long rev = EventFeed.revision();
        EventFeed.seen(List.of(forMain, alone), 7, 99, false);
        EventFeed.seen(List.of(forSea), 3, 0, true);
        JsonArray changed = EventFeed.since(rev, 100).getAsJsonArray("events");
        assertEquals(2, changed.size(), "only changed entries come back");
        JsonObject main = all().get(0).getAsJsonObject().getAsJsonArray("gods").get(0).getAsJsonObject();
        assertEquals("seen", main.get("state").getAsString());
        assertEquals(7, main.get("thought").getAsInt());
        JsonObject sea = all().get(0).getAsJsonObject().getAsJsonArray("gods").get(1).getAsJsonObject();
        assertEquals("heard", sea.get("state").getAsString());

        EventFeed.restored(List.of(alone));
        JsonObject back = all().get(1).getAsJsonObject().getAsJsonArray("gods").get(0).getAsJsonObject();
        assertEquals("pending", back.get("state").getAsString());
        assertFalse(back.has("thought"));
    }

    @Test
    void mergedAndDroppedCopiesAreMarked() {
        Ev first = ev("Corin broke 4 stone"), more = ev("Corin broke 6 stone");
        EventFeed.logged("main", first, null, first.text(), 1000);
        EventFeed.logged("main", more, first, more.text(), 1100);
        EventFeed.dropped(first);
        JsonArray a = all();
        assertEquals("dropped", a.get(0).getAsJsonObject().getAsJsonArray("gods").get(0).getAsJsonObject()
                .get("state").getAsString());
        JsonObject merged = a.get(1).getAsJsonObject().getAsJsonArray("gods").get(0).getAsJsonObject();
        assertEquals("merged", merged.get("state").getAsString());
        assertEquals(1, merged.get("mergedInto").getAsLong());
    }

    @Test
    void wordsAimedAtAGodKeepTheirReasonAndNothingIsKeptWhileOff() {
        Ev prayer = new Ev("Ash prays: rain", Loggable.Category.PRAYER, "Ash prays: rain");
        EventFeed.logged("main", prayer, null, prayer.text(), 1000);
        JsonObject e = all().get(0).getAsJsonObject();
        assertEquals("prayer", e.get("category").getAsString());
        assertEquals("Ash prays: rain", e.get("attention").getAsString());

        MindTrace.setEnabled(false);
        EventFeed.logged("main", ev("unseen"), null, "unseen", 1000);
        MindTrace.setEnabled(true);
        assertEquals(1, all().size());
    }
}
