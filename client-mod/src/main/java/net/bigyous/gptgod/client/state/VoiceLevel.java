package net.bigyous.gptgod.client.state;

import java.util.HashMap;
import java.util.Map;

/**
 * How loud each god's voice is right now (0..1), from the loudness envelopes the server sends with its
 * spoken lines. Pure; client thread only. The subtitles draw a bar from it, and the god's body can move
 * its lips with it.
 */
public final class VoiceLevel {
    /** The server sends ten loudness values a second. */
    public static final int STEP_MS = 100;

    private record Clip(long start, int ms, float[] env) {
    }

    private final Map<String, Clip> clips = new HashMap<>();

    /** This god's voice starts a clip now. env null = it speaks, but there is no audio to follow. */
    public void start(String realm, long now, int ms, float[] env) {
        clips.put(realm, new Clip(now, Math.max(0, ms), env));
    }

    public void clear() {
        clips.clear();
    }

    /** Whether this god is speaking right now. */
    public boolean speaking(String realm, long now) {
        Clip c = clips.get(realm);
        return c != null && now >= c.start && now - c.start < c.ms;
    }

    /** Whether the level for this god comes from real audio (else it is a gentle made-up murmur). */
    public boolean measured(String realm, long now) {
        Clip c = clips.get(realm);
        return c != null && c.env != null && speaking(realm, now);
    }

    /** This god's loudness now, 0..1; 0 when it is silent. */
    public float level(String realm, long now) {
        Clip c = clips.get(realm);
        if (c == null || now < c.start || now - c.start >= c.ms) return 0;
        long t = now - c.start;
        return c.env != null ? sample(c.env, t) : murmur(t);
    }

    /** The loudest god speaking now, 0..1. */
    public float level(long now) {
        float max = 0;
        for (String realm : clips.keySet()) max = Math.max(max, level(realm, now));
        return max;
    }

    /**
     * The envelope at a time into the clip: each value stands for the middle of its tenth of a second,
     * blended linearly with its neighbours; silence before the first and after the last.
     */
    public static float sample(float[] env, long elapsedMs) {
        if (env == null || env.length == 0 || elapsedMs < 0) return 0;
        double p = elapsedMs / (double) STEP_MS - 0.5;
        int i = (int) Math.floor(p);
        float f = (float) (p - i);
        float a = i >= 0 && i < env.length ? env[i] : 0;
        float b = i + 1 >= 0 && i + 1 < env.length ? env[i + 1] : 0;
        return clamp(a + (b - a) * f);
    }

    /** A syllable-like rise and fall for lines with no audio to measure (about four beats a second). */
    public static float murmur(long elapsedMs) {
        double t = elapsedMs / 1000.0;
        double v = 0.45 + 0.3 * Math.sin(t * 2 * Math.PI * 4.1) + 0.2 * Math.sin(t * 2 * Math.PI * 1.3 + 1);
        return clamp((float) v);
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }
}
