package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** What mortals write: signs, books, and blocks laid out as letters. */
class WritingsTest {
    @Test
    void aSignIsOnePhraseWithoutItsBlankLines() {
        assertEquals("KEEP OUT / of my house", Writings.sign(Arrays.asList("KEEP OUT", "", "  of my house ", null)));
        assertEquals("", Writings.sign(List.of("", " ", "")));
    }

    @Test
    void aBookIsJoinedAndCutToLength() {
        assertEquals("Day one. | I hate Vael.", Writings.book(Arrays.asList("Day one.\n", "", null, "I hate\n Vael."), 700));
        String longBook = Writings.book(List.of("a".repeat(500), "b".repeat(500)), 700);
        assertTrue(longBook.startsWith("a".repeat(500) + " | " + "b".repeat(197)), longBook.substring(495, 520));
        assertTrue(longBook.endsWith("... (2 pages, 303 more characters)"), longBook.substring(690));
        assertEquals("", Writings.book(List.of(), 700));
    }

    /** A row of 5-high strokes on the ground: 9 letters' worth, about a third of the box filled. */
    private static List<Senses.Change> strokes(long at, boolean placed) {
        List<Senses.Change> out = new java.util.ArrayList<>();
        for (int x = 0; x < 27; x += 3) {
            for (int z = 0; z < 5; z++) out.add(new Senses.Change(at, "dirt", 100 + x, 64, 200 + z, placed));
        }
        return out;
    }

    @Test
    void finishedBlockWritingIsSpottedOnce() {
        long now = 1_000_000;
        // Still placing: wait until they stop.
        assertEquals(null, Writings.spot(strokes(now - 2_000, true), now));
        Writings.Spotted s = Writings.spot(strokes(now - 20_000, true), now);
        assertEquals(45, s.blocks());
        assertEquals(112, s.x());
        assertEquals(64, s.y());
        assertEquals(202, s.z());
        // The same blocks give the same mark (noticed once); one more block gives another.
        assertEquals(s.signature(), Writings.spot(strokes(now - 30_000, true), now + 5_000).signature());
        List<Senses.Change> more = strokes(now - 20_000, true);
        more.add(new Senses.Change(now - 15_000, "dirt", 101, 64, 202, true));
        assertTrue(Writings.spot(more, now).signature() != s.signature());
        // Digging is not writing; old work has left the window; a filled floor is a floor.
        assertEquals(null, Writings.spot(strokes(now - 20_000, false), now));
        assertEquals(null, Writings.spot(strokes(now - Senses.WINDOW_MS - 1, true), now));
        List<Senses.Change> floor = new java.util.ArrayList<>();
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 6; z++) floor.add(new Senses.Change(now - 20_000, "oak_planks", x, 64, z, true));
        }
        assertEquals(null, Writings.spot(floor, now));
        assertEquals(null, Writings.spot(List.of(), now));
    }

    @Test
    void sparseFlatBlocksMayBeWriting() {
        // "I HATE VAEL" in 5-high letters on the ground: about 60 blocks in a 43 x 5 box.
        assertTrue(Writings.mayBeWriting(43, 5, 1, 62));
        // The same standing up as a thin wall, facing either way.
        assertTrue(Writings.mayBeWriting(43, 1, 5, 62));
        assertTrue(Writings.mayBeWriting(1, 43, 5, 62));
        // A floor, a solid wall, a fence line, a tower and a handful of blocks are not.
        assertFalse(Writings.mayBeWriting(8, 6, 1, 40));
        assertFalse(Writings.mayBeWriting(12, 1, 4, 44));
        assertFalse(Writings.mayBeWriting(20, 1, 1, 20));
        assertFalse(Writings.mayBeWriting(3, 3, 14, 60));
        assertFalse(Writings.mayBeWriting(9, 5, 1, 6));
        assertFalse(Writings.mayBeWriting(40, 40, 1, 30), "a few scattered blocks over a field");
        assertTrue(Senses.shape(43, 5, 1, 62, Set.of("dirt")).contains("letters"));
        assertEquals("looks like a floor or platform", Senses.shape(8, 6, 1, 40, Set.of("oak_planks")));
    }
    @org.junit.jupiter.api.Test
    void aMortalAskingAboutTheirWritingIsHeard() {
        org.junit.jupiter.api.Assertions.assertTrue(Writings.asks("Jumpathy: what does it say"));
        org.junit.jupiter.api.Assertions.assertTrue(Writings.asks("Jumpathy: god tell me what i wrote with blocks"));
        org.junit.jupiter.api.Assertions.assertTrue(Writings.asks("Tono: can you read this"));
        org.junit.jupiter.api.Assertions.assertTrue(Writings.asks("Tono: what did I build?"));
        org.junit.jupiter.api.Assertions.assertFalse(Writings.asks("Tono: give me bread please"));
        org.junit.jupiter.api.Assertions.assertFalse(Writings.asks("Tono: hello god"));
    }
}
