package net.bigyous.gptgodmc.god.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The god's songs are read from text: notes, chords, rests and lengths, in time with the tempo. */
class SongTest {
    @Test
    void notesChordsRestsAndLengthsFallOnTheBeat() {
        // 120 bpm = 10 ticks a beat.
        Song s = new Song("Hymn", "C4 E4 G4*2 R C5+E5/2 G4", null, "harp", 120, "Vaelor");
        List<Song.Note> n = s.notes();
        assertEquals(List.of(0, 10, 20, 50, 50, 55), n.stream().map(Song.Note::tick).toList());
        assertEquals(65, s.length());
        assertEquals(Song.step(Song.midi("C4")), n.get(0).step());
    }

    @Test
    void namesBecomeNoteBlockSteps() {
        assertEquals(60, Song.midi("C4"));
        assertEquals(54, Song.midi("F#3"));
        assertEquals(70, Song.midi("Bb4"));
        assertEquals(60, Song.midi("c"), "octave 4 when left out");
        assertEquals(0, Song.step(54));
        assertEquals(24, Song.step(78));
        assertEquals(Song.step(60), Song.step(48), "too low moves up an octave");
        assertEquals(Song.step(72), Song.step(84), "too high moves down an octave");
        assertEquals(1.0f, Song.pitch(12), 1e-6);
        assertEquals(0.5f, Song.pitch(0), 1e-6);
        assertEquals(2.0f, Song.pitch(24), 1e-6);
    }

    @Test
    void theBassPlaysUnderTheMelody() {
        Song s = new Song("Dirge", "E4 D4 C4*2", "C3*2 G2*2", "bell", 60, null);
        List<Song.Note> n = s.notes();
        assertEquals(5, n.size());
        assertTrue(n.stream().filter(Song.Note::bass).map(Song.Note::tick).toList().equals(List.of(0, 40)));
    }

    @Test
    void aBadTokenIsNamedSoTheGodCanFixIt() {
        Song s = new Song("Bad", "C4 H4 E4", null, "harp", 100, null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, s::notes);
        assertTrue(e.getMessage().contains("H4"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new Song("Long", "C4*16 ".repeat(40), null, "harp", 40,
                null).notes(), "longer than two minutes");
    }

    @Test
    void cleaningFixesTheInstrumentTempoAndTitle() {
        Song s = new Song(" ", "C4", null, "kazoo", 0, null).cleaned();
        assertEquals("harp", s.instrument());
        assertEquals(110, s.tempo());
        assertEquals("Untitled hymn", s.title());
    }
}
