package net.bigyous.gptgodmc.voice;

/**
 * A coarse loudness envelope of a spoken clip: {@link #PER_SECOND} values a second, each 0..1, for the
 * client mod (a subtitle's level bar, later the vessel's lips). Pure, so it is unit tested.
 */
public final class Loudness {
    public static final int PER_SECOND = 10;
    /** A minute of speech; longer clips are cut (the mod simply stops moving). */
    public static final int MAX_VALUES = 600;

    private Loudness() {
    }

    /** How long the clip plays, in milliseconds. */
    public static int millis(short[] pcm, int sampleRate) {
        if (pcm == null || sampleRate <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, pcm.length * 1000L / sampleRate);
    }

    /**
     * The RMS loudness of each tenth of a second, relative to the clip's loudest tenth (so a quiet voice
     * still moves the bar fully), on a gentle curve that keeps soft syllables visible. Silence gives zeros.
     */
    public static float[] envelope(short[] pcm, int sampleRate) {
        if (pcm == null || pcm.length == 0 || sampleRate <= 0) return new float[0];
        int window = Math.max(1, sampleRate / PER_SECOND);
        int n = Math.min(MAX_VALUES, (pcm.length + window - 1) / window);
        double[] rms = new double[n];
        double peak = 0;
        for (int i = 0; i < n; i++) {
            int from = i * window;
            int to = Math.min(pcm.length, from + window);
            double sum = 0;
            for (int j = from; j < to; j++) {
                double v = pcm[j] / 32768.0;
                sum += v * v;
            }
            // Divided by the full window, so a short last piece does not count as louder than it is.
            rms[i] = Math.sqrt(sum / window);
            peak = Math.max(peak, rms[i]);
        }
        float[] out = new float[n];
        if (peak < 1e-4) return out;
        for (int i = 0; i < n; i++) {
            out[i] = (float) Math.pow(rms[i] / peak, 0.6);
        }
        return out;
    }

    /**
     * A guess at how long a line takes to say when there is no audio to measure: about 14 characters a
     * second plus a breath, between 1.2 s and 30 s.
     */
    public static int estimateMs(String text) {
        int chars = text == null ? 0 : text.trim().length();
        return Math.max(1200, Math.min(30_000, 500 + chars * 70));
    }

    /**
     * The whole line's length judged from its first spoken part: that part's measured time, scaled up by
     * how much of the text it covers.
     */
    public static int scaleMs(int partMs, int partChars, int lineChars) {
        if (partMs <= 0 || partChars <= 0 || lineChars <= partChars) return Math.max(300, partMs);
        return (int) Math.max(300, Math.min(60_000, (long) partMs * lineChars / partChars));
    }
}
