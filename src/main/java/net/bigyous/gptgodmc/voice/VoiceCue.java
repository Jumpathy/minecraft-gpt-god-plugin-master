package net.bigyous.gptgodmc.voice;

/**
 * Told about a spoken line as it plays, so something else can follow the voice (the client mod's
 * subtitles). A line is spoken in parts; each part reports the moment its turn to play comes.
 */
public interface VoiceCue {
    /**
     * A part of the line starts playing now. Called on the playback thread; for the first part, just
     * before the line's text is shown.
     *
     * @param envelope loudness 0..1, {@link Loudness#PER_SECOND} values a second
     * @param ms       how long this part plays
     * @param chars    how many characters of the line this part speaks
     */
    void part(float[] envelope, int ms, int chars);
}
