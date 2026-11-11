package net.bigyous.gptgodmc.voice;

import java.util.Locale;

/**
 * Turns plain text-to-speech into the voice of a god. The natural presets only make the speaker
 * larger (see {@link Preset#size()}) and put a hall around the voice: compression so every word
 * lands, a rumble underneath, a slapback, echo and reverb. The monstrous presets add a pitch
 * shifter, an octave layer, chorus and saturation on top, and are meant to sound inhuman.
 * Output is 48 kHz mono, which is what Simple Voice Chat plays.
 */
public final class GodVoiceFx {
    public static final int OUT_RATE = 48_000;

    /** Every preset name, in the order the docs and previews list them. */
    public static final String[] PRESETS = { "majestic", "divine", "booming", "commanding", "demonic", "abyssal",
            "ethereal", "none" };

    /**
     * Effect settings for one preset.
     *
     * @param size       how much larger than a man the speaker sounds, in semitones (negative = larger).
     *                   The recording is played back slower, so pitch and resonance drop together, as
     *                   they do in a bigger chest and throat; nothing is cut up or stretched, so the
     *                   voice stays natural. The speech engine is asked to talk faster by
     *                   {@link GodVoiceFx#pace} so the god does not drawl.
     * @param semitones  extra pitch change by the pitch shifter (negative = deeper). It keeps the
     *                   duration but leaves a synthetic edge: only the monstrous presets use it
     * @param octaveDown how much of a copy one octave lower is mixed in
     * @param octaveUp   how much of a copy one octave higher is mixed in
     * @param chorus     a slightly detuned double, for "many voices at once"
     * @param drive      saturation; above 1 it growls
     * @param echo       feedback of the repeating echo (0 = none)
     * @param echoMs     time between echoes
     * @param reverb     how much hall is mixed in
     * @param room       hall size, 0..1
     * @param lowpassHz  everything above this is dulled (0 = off); low values sound muffled and huge
     * @param sub        a rumble under the voice that swells with each word
     * @param slap       one hard early reflection, like a shout off a canyon wall
     * @param slapMs     how late that reflection arrives
     * @param compress   0..1: evens out loudness so quiet syllables hit as hard as loud ones
     */
    public record Preset(double size, double semitones, double octaveDown, double octaveUp, double chorus,
            double drive, double echo, int echoMs, double reverb, double room, double lowpassHz, double sub,
            double slap, int slapMs, double compress) {
    }

    private GodVoiceFx() {
    }

    public static Preset preset(String name) {
        return switch (name == null ? "divine" : name.toLowerCase(Locale.ROOT)) {
            // ---- Natural: the same voice, from a larger being, in a larger place ----
            // The lightest touch: a big man in a stone hall. Closest to the plain voice.
            case "majestic" -> new Preset(-2.5, 0, 0, 0, 0, 1.0, 0.08, 250, 0.24, 0.84, 0, 0.05, 0.10, 75, 0.3);
            // A voice from the sky: enormous hall, chest-shaking bass, slow rolling echo.
            case "booming" -> new Preset(-4, 0, 0, 0, 0, 1.2, 0.22, 310, 0.44, 0.93, 7500, 0.25, 0.30, 95, 0.6);
            // A voice that gives orders: close, loud and hard-edged, every syllable at full weight.
            case "commanding" -> new Preset(-2, 0, 0, 0, 0, 1.4, 0.10, 180, 0.2, 0.76, 9500, 0.10, 0.26, 70, 0.85);
            // ---- Monstrous: meant to sound inhuman ----
            case "demonic" -> new Preset(-3, -2, 0.5, 0, 0.25, 1.8, 0.28, 320, 0.38, 0.88, 4200, 0.25, 0.15, 110,
                    0.5);
            // Something speaking from far below: very deep, muffled, growling, with a long dark tail.
            case "abyssal" -> new Preset(-4, -3, 0.6, 0, 0.3, 2.1, 0.33, 430, 0.52, 0.96, 3300, 0.5, 0.2, 135, 0.55);
            // A choir of one: a shimmering octave above, a detuned double, a long bright tail.
            case "ethereal" -> new Preset(-1, 0, 0, 0.15, 0.3, 1.0, 0.35, 380, 0.5, 0.92, 9000, 0, 0, 0, 0.2);
            case "none" -> new Preset(0, 0, 0, 0, 0, 1.0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            // divine: larger than majestic, in a cathedral.
            default -> new Preset(-3, 0, 0, 0, 0, 1.05, 0.15, 280, 0.34, 0.88, 10000, 0.08, 0.12, 85, 0.4);
        };
    }

    /**
     * How much faster than normal the speech engine should talk for this preset, as a factor on
     * duration (0.84 = spoken in 84% of the usual time). {@link #process} then slows the recording
     * by the same factor to make the speaker larger, and the pace comes out normal.
     */
    public static double pace(Preset p) {
        return Math.pow(2, p.size() / 12.0);
    }

    public static short[] process(short[] pcm, int sampleRate, Preset p, double volume) {
        return process(pcm, sampleRate, p, volume, true);
    }

    /**
     * @param last false for a part of a line that another part follows at once: it gets only a breath
     *             of tail instead of the full ring-out, so the sentences run on naturally
     */
    public static short[] process(short[] pcm, int sampleRate, Preset p, double volume, boolean last) {
        // Declaring the recording slower than it is makes the resampler stretch it: a larger speaker.
        int declaredRate = Math.max(4000, (int) Math.round(sampleRate * pace(p)));
        float[] x = resample(toFloat(pcm), declaredRate, OUT_RATE);
        boolean wet = p.reverb() > 0 || p.echo() > 0 || p.slap() > 0;
        // Room for the echoes to die away after the last word; bigger halls ring longer.
        int tail = !last ? (int) (OUT_RATE * (wet ? 0.3 : 0.15))
                : wet ? (int) (OUT_RATE * (p.room() > 0.9 ? 2.2 : 1.4)) : 0;
        float[] padded = new float[x.length + tail];
        System.arraycopy(x, 0, padded, 0, x.length);

        float[] voice = p.semitones() == 0 ? padded.clone() : pitchShift(padded, p.semitones());
        if (p.octaveDown() > 0) addInto(voice, pitchShift(padded, p.semitones() - 12), p.octaveDown());
        if (p.octaveUp() > 0) addInto(voice, pitchShift(padded, p.semitones() + 12), p.octaveUp());
        if (p.chorus() > 0) addInto(voice, chorus(voice), p.chorus());
        if (p.compress() > 0) compress(voice, p.compress());
        if (p.sub() > 0) addInto(voice, subBass(voice), p.sub());
        if (p.drive() > 1.01) saturate(voice, p.drive());
        if (p.lowpassHz() > 0) lowpass(voice, p.lowpassHz());
        if (p.slap() > 0) slapback(voice, p.slapMs() * OUT_RATE / 1000, p.slap());
        if (p.echo() > 0) echo(voice, p.echoMs() * OUT_RATE / 1000, p.echo());
        if (p.reverb() > 0) {
            float[] hall = reverb(voice, p.room());
            for (int i = 0; i < voice.length; i++) {
                voice[i] = (float) (voice[i] * (1 - p.reverb() * 0.5) + hall[i] * p.reverb());
            }
        }
        if (wet) fadeOut(voice, OUT_RATE / 4);
        return toShort(normalize(voice, 0.89 * volume));
    }

    // ---- Building blocks ---------------------------------------------------------------------------

    static float[] toFloat(short[] s) {
        float[] f = new float[s.length];
        for (int i = 0; i < s.length; i++) f[i] = s[i] / 32768f;
        return f;
    }

    static short[] toShort(float[] f) {
        short[] s = new short[f.length];
        for (int i = 0; i < f.length; i++) {
            s[i] = (short) Math.max(-32768, Math.min(32767, Math.round(f[i] * 32767)));
        }
        return s;
    }

    private static final int SINC_ZERO_CROSSINGS = 12;
    private static final int SINC_PHASES = 1024;

    /**
     * Band-limited sample rate conversion (windowed sinc). Plain linear interpolation dulls the top
     * of a voice and, when going down in rate (48 kHz microphone to 16 kHz for Whisper), folds
     * high frequencies back into the speech band as noise; this does neither.
     */
    public static float[] resample(float[] in, int from, int to) {
        if (from == to || in.length == 0) return in;
        int outLen = (int) ((long) in.length * to / from);
        float[] out = new float[outLen];
        double step = (double) from / to;
        // When lowering the rate, cut everything above the new Nyquist and widen the kernel to match.
        double cutoff = Math.min(1.0, (double) to / from);
        int half = (int) Math.ceil(SINC_ZERO_CROSSINGS / cutoff);
        int taps = 2 * half;
        // One kernel per fractional position; each row is scaled so its weights sum to 1.
        float[][] kernel = new float[SINC_PHASES][taps];
        for (int ph = 0; ph < SINC_PHASES; ph++) {
            double frac = (double) ph / SINC_PHASES;
            double sum = 0;
            for (int t = 0; t < taps; t++) {
                double d = (t - half + 1) - frac;
                double a = Math.PI * d * cutoff;
                double sinc = Math.abs(a) < 1e-9 ? 1 : Math.sin(a) / a;
                double window = 0.5 + 0.5 * Math.cos(Math.PI * d / half);
                double w = sinc * window;
                kernel[ph][t] = (float) w;
                sum += w;
            }
            for (int t = 0; t < taps; t++) kernel[ph][t] /= (float) sum;
        }
        for (int i = 0; i < outLen; i++) {
            double pos = i * step;
            int centre = (int) pos;
            float[] k = kernel[(int) ((pos - centre) * SINC_PHASES)];
            int first = centre - half + 1;
            double acc = 0;
            for (int t = Math.max(0, -first), end = Math.min(taps, in.length - first); t < end; t++) {
                acc += in[first + t] * k[t];
            }
            out[i] = (float) acc;
        }
        return out;
    }

    /** Two crossfaded read heads sweeping a delay line: changes pitch, keeps duration. */
    static float[] pitchShift(float[] in, double semitones) {
        double ratio = Math.pow(2, semitones / 12.0);
        int window = 2048;
        float[] out = new float[in.length];
        double phase = 0;
        double slope = 1 - ratio;
        for (int n = 0; n < in.length; n++) {
            double d1 = phase;
            double d2 = (phase + window / 2.0) % window;
            out[n] = (float) (read(in, n - d1) * hann(d1, window) + read(in, n - d2) * hann(d2, window));
            phase += slope;
            if (phase >= window) phase -= window;
            if (phase < 0) phase += window;
        }
        return out;
    }

    private static double hann(double d, int window) {
        double s = Math.sin(Math.PI * d / window);
        return s * s;
    }

    private static double read(float[] in, double pos) {
        if (pos < 0) return 0;
        int i0 = (int) pos;
        if (i0 >= in.length - 1) return i0 < in.length ? in[i0] : 0;
        double frac = pos - i0;
        return in[i0] * (1 - frac) + in[i0 + 1] * frac;
    }

    private static float[] chorus(float[] in) {
        float[] out = new float[in.length];
        double base = OUT_RATE * 0.018;
        double depth = OUT_RATE * 0.004;
        for (int n = 0; n < in.length; n++) {
            double d = base + depth * Math.sin(2 * Math.PI * 0.35 * n / OUT_RATE);
            out[n] = (float) read(in, n - d);
        }
        return out;
    }

    private static void addInto(float[] target, float[] add, double gain) {
        for (int i = 0; i < target.length && i < add.length; i++) target[i] += (float) (add[i] * gain);
    }

    private static void saturate(float[] x, double drive) {
        double norm = Math.tanh(drive);
        for (int i = 0; i < x.length; i++) x[i] = (float) (Math.tanh(x[i] * drive) / norm);
    }

    private static void lowpass(float[] x, double cutoff) {
        double rc = 1.0 / (2 * Math.PI * cutoff);
        double dt = 1.0 / OUT_RATE;
        double a = dt / (rc + dt);
        double y = 0;
        for (int i = 0; i < x.length; i++) {
            y += a * (x[i] - y);
            x[i] = (float) y;
        }
    }

    private static void echo(float[] x, int delay, double feedback) {
        if (delay <= 0) return;
        for (int i = delay; i < x.length; i++) x[i] += (float) (x[i - delay] * feedback);
    }

    /** One early reflection with no repeats. Walking backwards reads only the untouched signal. */
    private static void slapback(float[] x, int delay, double gain) {
        if (delay <= 0) return;
        for (int i = x.length - 1; i >= delay; i--) x[i] += (float) (x[i - delay] * gain);
    }

    /**
     * A rumble that follows the voice's loudness: 58 Hz, with its octave so small speakers still
     * carry it. It rises with each word and sinks between them, which is what makes a voice "boom".
     */
    private static float[] subBass(float[] voice) {
        float[] out = new float[voice.length];
        double attack = 1 - Math.exp(-1.0 / (OUT_RATE * 0.012));
        double release = 1 - Math.exp(-1.0 / (OUT_RATE * 0.16));
        double env = 0;
        for (int n = 0; n < voice.length; n++) {
            double level = Math.abs(voice[n]);
            env += (level > env ? attack : release) * (level - env);
            double t = (double) n / OUT_RATE;
            out[n] = (float) (env * (Math.sin(2 * Math.PI * 58 * t) + 0.45 * Math.sin(2 * Math.PI * 116 * t + 0.6)));
        }
        return out;
    }

    /** Turns down whatever rises above a threshold, so the final normalize lifts the quiet parts. */
    private static void compress(float[] x, double amount) {
        float peak = 1e-6f;
        for (float v : x) peak = Math.max(peak, Math.abs(v));
        double threshold = 0.5 - 0.36 * amount;
        double ratio = 1 + 5 * amount;
        double attack = 1 - Math.exp(-1.0 / (OUT_RATE * 0.004));
        double release = 1 - Math.exp(-1.0 / (OUT_RATE * 0.12));
        double env = 0;
        for (int n = 0; n < x.length; n++) {
            double level = Math.abs(x[n]) / peak;
            env += (level > env ? attack : release) * (level - env);
            if (env > threshold) {
                x[n] *= (float) ((threshold + (env - threshold) / ratio) / env);
            }
        }
    }

    /** Eases the last samples to silence so a long tail never ends in a click. */
    private static void fadeOut(float[] x, int samples) {
        int n = Math.min(samples, x.length);
        for (int i = 0; i < n; i++) x[x.length - 1 - i] *= (float) i / n;
    }

    /** Freeverb-style: parallel damped combs, then series allpasses. */
    private static float[] reverb(float[] in, double room) {
        int[] combs = { 1557, 1617, 1491, 1422, 1277, 1356 };
        int[] allpasses = { 556, 441, 341, 225 };
        double scale = OUT_RATE / 44100.0;
        double feedback = 0.7 + 0.28 * room;
        double damp = 0.25;
        float[] sum = new float[in.length];
        for (int c : combs) {
            int len = (int) (c * scale);
            float[] buf = new float[len];
            int idx = 0;
            double store = 0;
            for (int n = 0; n < in.length; n++) {
                float out = buf[idx];
                store = out * (1 - damp) + store * damp;
                buf[idx] = (float) (in[n] * 0.015 + store * feedback);
                sum[n] += out;
                idx = (idx + 1) % len;
            }
        }
        for (int a : allpasses) {
            int len = (int) (a * scale);
            float[] buf = new float[len];
            int idx = 0;
            for (int n = 0; n < sum.length; n++) {
                float b = buf[idx];
                float out = -sum[n] + b;
                buf[idx] = (float) (sum[n] + b * 0.5);
                sum[n] = out;
                idx = (idx + 1) % len;
            }
        }
        return sum;
    }

    private static float[] normalize(float[] x, double peak) {
        float max = 1e-6f;
        for (float v : x) max = Math.max(max, Math.abs(v));
        float g = (float) (peak / max);
        for (int i = 0; i < x.length; i++) x[i] *= g;
        return x;
    }
}
