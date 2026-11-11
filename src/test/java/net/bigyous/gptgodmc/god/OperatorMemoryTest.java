package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The God Manager's edits to what the god remembers about a mortal. */
class OperatorMemoryTest {

    @Test
    void favorIsSetOutrightAndClamped() {
        Memory m = new Memory();
        assertEquals(-10, m.setFavor("Korvath", -40, "You have come to despise them."));
        assertEquals(-10, m.favorOf("korvath"));
        assertEquals("You have come to despise them.", m.subjectIfAny("Korvath").notes.get(0).text);
        assertEquals(7, m.setFavor("Korvath", 7, null));
        assertEquals(1, m.subjectIfAny("Korvath").notes.size(), "no note without one");
    }

    @Test
    void singleNotesAndDeedsCanBeErased() {
        Memory m = new Memory();
        m.remember("Ashwyn", "Promised her a crown.");
        m.remember("Ashwyn", "She lied about the chapel.");
        m.addDeed("Ashwyn", "burned the chapel", 1_000L);
        m.addDeed("Ashwyn", "made an offering of gold", 9_000_000L);
        Memory.Subject s = m.subjectIfAny("Ashwyn");
        Memory.Note lie = s.notes.get(1);
        assertTrue(m.removeNote("ashwyn", lie.at, lie.text));
        assertEquals(1, s.notes.size());
        assertEquals("Promised her a crown.", s.notes.get(0).text);
        assertFalse(m.removeNote("Ashwyn", lie.at, lie.text), "already gone");
        assertTrue(m.removeDeed("Ashwyn", 1_000L, "burned the chapel"));
        assertEquals(1, s.deeds.size());
        assertFalse(m.removeDeed("Nobody", 1L, "x"));
    }

    @Test
    void clearingAndForgetting() {
        Memory m = new Memory();
        m.remember("Pip", "a note");
        m.remember("Pip", "another");
        m.addDeed("Pip", "tamed a wolf", 5L);
        m.subjectIfAny("Pip").summary = "An old friend.";
        m.adjustFavor("Pip", 4, null);
        assertEquals(2, m.clearNotes("Pip"));
        assertEquals(1, m.clearDeeds("Pip"));
        Memory.Subject s = m.subjectIfAny("Pip");
        assertTrue(s.notes.isEmpty() && s.deeds.isEmpty());
        assertNull(s.summary);
        assertEquals(4, s.favor, "clearing memories keeps favor");
        assertTrue(m.forget("pip"));
        assertNull(m.subjectIfAny("Pip"));
        assertFalse(m.forget("Pip"));
        assertFalse(m.forget("world"));
        assertEquals(0, m.clearNotes("Nobody"));
    }
}
