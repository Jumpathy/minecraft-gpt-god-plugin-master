package net.bigyous.gptgodmc.god.music;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tune the god composed, written as text so it fits in a tool call and on a disc. Pure, so it is
 * unit tested.
 *
 * <p>A line is space-separated tokens: a note such as {@code C4}, {@code F#3} or {@code Bb4}
 * (octave 4 if left out), a chord such as {@code C4+E4+G4}, or a rest {@code R}. A token lasts one
 * beat; {@code /2} halves it, {@code *2} doubles it ({@code /4}, {@code *1.5}, ... also work). Note
 * blocks span two octaves (F#3 to F#5); notes outside are moved by whole octaves into that range.
 *
 * @param melody the main line
 * @param bass   an optional second line played under it (bass instrument), or null
 */
public record Song(String title, String melody, String bass, String instrument, int tempo, String by) {
    /** One sound: when (ticks from the start), which note-block step (0 = F#3 .. 24 = F#5), which line. */
    public record Note(int tick, int step, boolean bass) {
    }

    public static final List<String> INSTRUMENTS = List.of("harp", "bell", "flute", "chime", "guitar", "xylophone",
            "iron_xylophone", "pling", "bit", "banjo", "didgeridoo", "cow_bell", "bass");

    public static final int MAX_TOKENS = 256;
    public static final int MAX_SECONDS = 120;

    private static final Pattern NOTE = Pattern.compile("([A-Ga-g])([#b]?)(-?\\d)?");
    private static final Pattern LENGTH = Pattern.compile("^(.*?)(?:([/*])(\\d+(?:\\.\\d+)?))?$");
    private static final int[] BASE = { 9, 11, 0, 2, 4, 5, 7 }; // A B C D E F G

    /** MIDI number of a note name, e.g. C4 = 60, F#3 = 54; -1 if it is not a note. */
    public static int midi(String name) {
        Matcher m = NOTE.matcher(name.trim());
        if (!m.matches()) return -1;
        int letter = Character.toUpperCase(m.group(1).charAt(0)) - 'A';
        int semis = BASE[letter];
        if ("#".equals(m.group(2))) semis++;
        if ("b".equals(m.group(2))) semis--;
        int octave = m.group(3) == null ? 4 : Integer.parseInt(m.group(3));
        return (octave + 1) * 12 + semis;
    }

    /** The note-block step for a MIDI note: 0 (F#3) to 24 (F#5), moved by octaves into range. */
    public static int step(int midi) {
        int s = midi - 54;
        while (s < 0) s += 12;
        while (s > 24) s -= 12;
        return s;
    }

    /** The sound pitch of a note-block step (0.5 to 2.0). */
    public static float pitch(int step) {
        return (float) Math.pow(2, (step - 12) / 12.0);
    }

    /** Ticks per beat at this tempo (20 ticks a second). */
    public static double ticksPerBeat(int tempo) {
        return 1200.0 / Math.max(20, Math.min(300, tempo));
    }

    /** Every note of the song in time order. Throws IllegalArgumentException naming a bad token. */
    public List<Note> notes() {
        List<Note> out = new ArrayList<>();
        int a = line(melody, false, out);
        int b = bass == null || bass.isBlank() ? 0 : line(bass, true, out);
        out.sort((x, y) -> Integer.compare(x.tick(), y.tick()));
        if (Math.max(a, b) / 20 > MAX_SECONDS) {
            throw new IllegalArgumentException("The song is " + Math.max(a, b) / 20 + " s long; keep it under "
                    + MAX_SECONDS + " s.");
        }
        return out;
    }

    /** How long the song lasts, in ticks. */
    public int length() {
        int a = line(melody, false, new ArrayList<>());
        int b = bass == null || bass.isBlank() ? 0 : line(bass, true, new ArrayList<>());
        return Math.max(a, b);
    }

    private int line(String text, boolean isBass, List<Note> out) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("The melody is empty.");
        String[] tokens = text.trim().split("[\\s,|]+");
        if (tokens.length > MAX_TOKENS) {
            throw new IllegalArgumentException("Too many notes (" + tokens.length + "); at most " + MAX_TOKENS
                    + " per line.");
        }
        double perBeat = ticksPerBeat(tempo);
        double t = 0;
        for (String token : tokens) {
            Matcher m = LENGTH.matcher(token);
            if (!m.matches()) throw new IllegalArgumentException("Cannot read '" + token + "'.");
            String body = m.group(1);
            double beats = 1;
            if (m.group(2) != null) {
                double f = Double.parseDouble(m.group(3));
                if (f <= 0 || f > 16) throw new IllegalArgumentException("Bad length in '" + token + "'.");
                beats = m.group(2).equals("/") ? 1 / f : f;
            }
            String b = body.toUpperCase(Locale.ROOT);
            if (!(b.equals("R") || b.equals("-") || b.equals("_"))) {
                for (String part : body.split("\\+")) {
                    int midi = midi(part);
                    if (midi < 0) {
                        throw new IllegalArgumentException("'" + part + "' in '" + token + "' is not a note. Write "
                                + "notes like C4, F#3, Bb4, chords like C4+E4+G4, rests as R, lengths as /2 or *2.");
                    }
                    out.add(new Note((int) Math.round(t), step(midi), isBass));
                }
            }
            t += beats * perBeat;
        }
        return (int) Math.round(t);
    }

    /** The song with its instrument and tempo checked (unknown instrument becomes harp). */
    public Song cleaned() {
        String i = instrument == null ? "harp" : instrument.trim().toLowerCase(Locale.ROOT);
        if (!INSTRUMENTS.contains(i)) i = "harp";
        int bpm = tempo <= 0 ? 110 : Math.max(40, Math.min(240, tempo));
        String t = title == null || title.isBlank() ? "Untitled hymn" : title.trim();
        if (t.length() > 48) t = t.substring(0, 48);
        return new Song(t, melody, bass, i, bpm, by);
    }
}
