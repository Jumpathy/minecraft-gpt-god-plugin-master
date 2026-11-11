package net.bigyous.gptgod.client.fx;

import java.util.List;
import java.util.Random;

/**
 * A small synthesizer: turns a patch (a few voices with pitch glides, envelopes and filters, plus echo)
 * into mono 16-bit audio. Pure and deterministic, so the same patch always sounds the same. The mod's
 * own sounds are patches, and the god's designed sounds use the same thing.
 */
public final class Synth {
    private Synth() {
    }

    public static final int RATE = 22050;
    public static final int MAX_MS = 6000;
    public static final int MAX_VOICES = 12;

    public enum Wave {
        SINE, TRIANGLE, SAW, SQUARE,
        /** White noise; shape it with the low-pass and high-pass. */
        NOISE,
        /** Sparse sharp clicks, f0..f1 of them a second: embers, static, bones. */
        CRACKLE
    }

    /**
     * One voice. Times in milliseconds, pitches in Hz (gliding f0 → f1 over the voice's length), gain 0..1.
     * lp0 → lp1 is a low-pass cutoff sweep (0 = none); hp a high-pass (0 = none); vibrato and tremolo are
     * a rate in Hz and a depth 0..1.
     */
    public record Voice(Wave wave, int start, float f0, float f1, int attack, int hold, int release, float gain,
            float vibratoHz, float vibrato, float lp0, float lp1, float hp, float tremoloHz, float tremolo) {
        public int end() {
            return start + attack + hold + release;
        }

        /** A plain tone or noise with an envelope. */
        public static Voice of(Wave wave, int start, float f0, float f1, int attack, int hold, int release, float gain) {
            return new Voice(wave, start, f0, f1, attack, hold, release, gain, 0, 0, 0, 0, 0, 0, 0);
        }

        public Voice vibrato(float hz, float depth) {
            return new Voice(wave, start, f0, f1, attack, hold, release, gain, hz, depth, lp0, lp1, hp, tremoloHz, tremolo);
        }

        public Voice lowpass(float from, float to) {
            return new Voice(wave, start, f0, f1, attack, hold, release, gain, vibratoHz, vibrato, from, to, hp, tremoloHz, tremolo);
        }

        public Voice highpass(float hz) {
            return new Voice(wave, start, f0, f1, attack, hold, release, gain, vibratoHz, vibrato, lp0, lp1, hz, tremoloHz, tremolo);
        }

        public Voice tremolo(float hz, float depth) {
            return new Voice(wave, start, f0, f1, attack, hold, release, gain, vibratoHz, vibrato, lp0, lp1, hp, hz, depth);
        }
    }

    /** Voices plus an echo (0 gain = none): each repeat is echoGain times quieter. */
    public record Patch(List<Voice> voices, int echoMs, float echoGain, long seed) {
        public Patch(List<Voice> voices) {
            this(voices, 0, 0, 1);
        }

        public int lengthMs() {
            int end = 0;
            for (Voice v : voices) end = Math.max(end, v.end());
            int tail = echoGain > 0 ? (int) (echoMs * Math.min(6, Math.log(0.05) / Math.log(Math.max(0.01, Math.min(0.9, echoGain))))) : 0;
            return Math.min(MAX_MS, end + tail + 20);
        }
    }

    /** Renders the patch: samples in -32767..32767 at {@link #RATE}. */
    public static short[] render(Patch patch) {
        int n = Math.max(1, patch.lengthMs() * RATE / 1000);
        float[] mix = new float[n];
        int count = 0;
        for (Voice v : patch.voices()) {
            if (count++ >= MAX_VOICES) break;
            voice(mix, v, new Random(patch.seed() * 7919 + count));
        }
        if (patch.echoGain() > 0 && patch.echoMs() > 5) {
            int d = patch.echoMs() * RATE / 1000;
            float g = Math.min(0.9f, patch.echoGain());
            for (int i = d; i < n; i++) mix[i] += mix[i - d] * g;
        }
        // Soft clip, then bring the loudest moment to just under full scale so every patch is as loud.
        float peak = 0;
        for (int i = 0; i < n; i++) {
            mix[i] = (float) Math.tanh(mix[i] * 1.2);
            peak = Math.max(peak, Math.abs(mix[i]));
        }
        float norm = peak < 1e-4f ? 0 : 0.92f / peak;
        short[] out = new short[n];
        int fade = Math.min(n, RATE / 200);
        for (int i = 0; i < n; i++) {
            float edge = Math.min(1, Math.min(i, n - 1 - i) / (float) fade);
            out[i] = (short) Math.round(mix[i] * norm * edge * 32767);
        }
        return out;
    }

    private static void voice(float[] mix, Voice v, Random rnd) {
        int from = Math.max(0, v.start()) * RATE / 1000;
        int len = Math.max(1, (v.attack() + v.hold() + v.release()) * RATE / 1000);
        int attack = Math.max(1, v.attack() * RATE / 1000), release = Math.max(1, v.release() * RATE / 1000);
        double phase = 0, lp = 0, lp2 = 0, hpIn = 0, hpOut = 0, click = 0;
        float f0 = Math.max(1, v.f0()), f1 = Math.max(1, v.f1());
        for (int i = 0; i < len && from + i < mix.length; i++) {
            double t = i / (double) len, secs = i / (double) RATE;
            double env = i < attack ? i / (double) attack : i >= len - release ? (len - i) / (double) release : 1;
            env *= env; // a curved envelope sounds more natural than a straight ramp
            double f = f0 * Math.pow(f1 / f0, t);
            if (v.vibrato() > 0) f *= 1 + v.vibrato() * 0.06 * Math.sin(2 * Math.PI * v.vibratoHz() * secs);
            double s;
            switch (v.wave()) {
                case NOISE -> s = rnd.nextDouble() * 2 - 1;
                case CRACKLE -> {
                    if (rnd.nextDouble() < f / RATE) click = (rnd.nextDouble() * 0.6 + 0.4) * (rnd.nextBoolean() ? 1 : -1);
                    s = click;
                    click *= 0.72;
                }
                default -> {
                    phase += f / RATE;
                    phase -= Math.floor(phase);
                    s = switch (v.wave()) {
                        case TRIANGLE -> 4 * Math.abs(phase - 0.5) - 1;
                        case SAW -> 2 * phase - 1;
                        case SQUARE -> phase < 0.5 ? 0.7 : -0.7;
                        default -> Math.sin(2 * Math.PI * phase);
                    };
                }
            }
            if (v.lp0() > 0) {
                double cut = v.lp0() * Math.pow(Math.max(1, v.lp1() > 0 ? v.lp1() : v.lp0()) / v.lp0(), t);
                double a = 1 - Math.exp(-2 * Math.PI * Math.min(cut, RATE * 0.45) / RATE);
                lp += a * (s - lp);
                lp2 += a * (lp - lp2);
                s = lp2;
            }
            if (v.hp() > 0) {
                double a = 1 / (1 + 2 * Math.PI * v.hp() / RATE);
                hpOut = a * (hpOut + s - hpIn);
                hpIn = s;
                s = hpOut;
            }
            if (v.tremolo() > 0) s *= 1 - v.tremolo() * (0.5 + 0.5 * Math.sin(2 * Math.PI * v.tremoloHz() * secs));
            mix[from + i] += (float) (s * env * v.gain());
        }
    }
}
