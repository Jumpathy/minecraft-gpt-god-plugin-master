package net.bigyous.gptgodmc.voice;

/**
 * When a line's first words may start playing. A line is made part by part; if the parts are made slower
 * than they play (a voice server on a busy CPU), starting at once leaves a silence before every later
 * part, which is heard as a choppy voice. So the first parts are held until what is ready lasts about as
 * long as the rest will take to make. A fast voice server still starts after its first part. Pure.
 */
public final class VoicePacing {
    /** A pause this long between two phrases sounds natural. */
    public static final int SLACK_MS = 250;

    private double msPerChar; // how long the voice server takes per character lately; 0 = not known yet

    /** Learns from one part that was really synthesized (not one prepared ahead of time). */
    public synchronized void learned(int chars, long tookMs) {
        if (chars < 8 || tookMs < 30) return;
        double now = tookMs / (double) chars;
        msPerChar = msPerChar <= 0 ? now : msPerChar * 0.6 + now * 0.4;
    }

    public synchronized double msPerChar() {
        return msPerChar;
    }

    /**
     * True when playback may start: nothing is left to make, or what is ready ({@code readyMs} of sound)
     * lasts until the remaining characters are made, give or take a natural pause.
     */
    public synchronized boolean mayStart(long readyMs, int remainingChars) {
        if (remainingChars <= 0 || msPerChar <= 0) return true;
        return readyMs + SLACK_MS >= remainingChars * msPerChar * 1.1;
    }
}
