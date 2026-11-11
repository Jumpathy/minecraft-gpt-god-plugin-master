package net.bigyous.gptgodmc.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoudnessTest {
    private static short[] tone(int rate, double seconds, double amplitude) {
        short[] pcm = new short[(int) (rate * seconds)];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) (Math.sin(i * 2 * Math.PI * 220 / rate) * amplitude * 32767);
        }
        return pcm;
    }

    @Test
    void tenValuesASecondBetweenZeroAndOne() {
        short[] pcm = tone(48_000, 2.35, 0.5);
        float[] env = Loudness.envelope(pcm, 48_000);
        assertEquals(24, env.length);
        assertEquals(2350, Loudness.millis(pcm, 48_000));
        for (float v : env) assertTrue(v >= 0 && v <= 1, "value " + v);
        assertEquals(1f, env[3], 0.02f);
        assertTrue(env[23] < env[3], "a half-filled last window is quieter");
    }

    @Test
    void followsLoudAndQuietAndSilence() {
        int rate = 48_000;
        short[] pcm = new short[rate * 3 / 2];
        System.arraycopy(tone(rate, 0.5, 0.8), 0, pcm, 0, rate / 2);
        System.arraycopy(tone(rate, 0.5, 0.1), 0, pcm, rate, rate / 2);
        float[] env = Loudness.envelope(pcm, rate);
        assertEquals(15, env.length);
        assertEquals(1f, env[2], 0.02f);
        assertEquals(0f, env[7], 0.001f);
        assertTrue(env[12] > 0.15f && env[12] < 0.5f, "a quiet part stays visible: " + env[12]);
        // A quiet recording is scaled to its own peak.
        assertEquals(1f, Loudness.envelope(tone(rate, 0.5, 0.02), rate)[2], 0.02f);
    }

    @Test
    void emptySilentAndLongClips() {
        assertEquals(0, Loudness.envelope(null, 48_000).length);
        assertEquals(0, Loudness.envelope(new short[0], 48_000).length);
        assertEquals(0, Loudness.envelope(new short[10], 0).length);
        for (float v : Loudness.envelope(new short[48_000], 48_000)) assertEquals(0f, v);
        assertEquals(Loudness.MAX_VALUES, Loudness.envelope(new short[8000 * 90], 8000).length);
    }

    @Test
    void lineLengthGuesses() {
        assertEquals(1200, Loudness.estimateMs("Hi."));
        assertEquals(500 + 70 * 40, Loudness.estimateMs("x".repeat(40)));
        assertEquals(30_000, Loudness.estimateMs("x".repeat(5000)));
        assertEquals(1200, Loudness.estimateMs(null));
        // The first part took 2 s for 20 of 60 characters: the line takes about 6 s.
        assertEquals(6000, Loudness.scaleMs(2000, 20, 60));
        assertEquals(2000, Loudness.scaleMs(2000, 20, 20));
        assertEquals(300, Loudness.scaleMs(0, 0, 50));
    }
}
