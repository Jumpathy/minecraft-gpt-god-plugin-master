package net.bigyous.gptgodmc.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class GodVoiceFxTest {

    /** A rough vowel-like test tone at 22.05 kHz (Piper's usual rate). */
    static short[] tone(int rate, double seconds) {
        short[] s = new short[(int) (rate * seconds)];
        for (int i = 0; i < s.length; i++) {
            double t = (double) i / rate;
            double env = Math.min(1, Math.min(t * 8, (seconds - t) * 8));
            double v = 0.5 * Math.sin(2 * Math.PI * 140 * t) + 0.3 * Math.sin(2 * Math.PI * 280 * t)
                    + 0.15 * Math.sin(2 * Math.PI * 700 * t);
            s[i] = (short) (v * env * 12000);
        }
        return s;
    }

    @Test
    void presetsProduceCleanAudioWithATail() throws IOException {
        short[] input = tone(22050, 1.5);
        for (String preset : GodVoiceFx.PRESETS) {
            short[] out = GodVoiceFx.process(input, 22050, GodVoiceFx.preset(preset), 1.0);
            int expectedMin = (int) (GodVoiceFx.OUT_RATE * 1.5) - 10;
            assertTrue(out.length >= expectedMin, preset + " too short: " + out.length);
            int peak = 0;
            for (short v : out) peak = Math.max(peak, Math.abs(v));
            assertTrue(peak > 20000 && peak <= 32767, preset + " peak " + peak);
            Path dir = Path.of("build/test-renders");
            Files.createDirectories(dir);
            Files.write(dir.resolve("voice-" + preset + ".wav"), Synthesizer.wav(out, GodVoiceFx.OUT_RATE));
        }
    }

    record Clip(int rate, short[] pcm) {
    }

    /** Reads a 16-bit mono WAV. */
    static Clip readWav(byte[] b) {
        ByteBuffer buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        int rate = 22050;
        int pos = 12;
        while (pos + 8 <= b.length) {
            String id = new String(b, pos, 4, StandardCharsets.US_ASCII);
            int size = buf.getInt(pos + 4);
            if (id.equals("fmt ")) {
                rate = buf.getInt(pos + 12);
            } else if (id.equals("data")) {
                short[] pcm = new short[Math.min(size, b.length - pos - 8) / 2];
                for (int i = 0; i < pcm.length; i++) pcm[i] = buf.getShort(pos + 8 + i * 2);
                return new Clip(rate, pcm);
            }
            pos += 8 + size + (size & 1);
        }
        throw new IllegalArgumentException("no data chunk");
    }

    /**
     * One spoken sentence through every preset, for previews/voice. The dry clip is a plain Windows
     * system voice (in game the dry voice is Piper's); what these clips show is the effect chain.
     */
    @Test
    void speechThroughEveryPreset() throws IOException {
        Clip dry;
        try (InputStream in = getClass().getResourceAsStream("/voice/dry-speech.wav")) {
            assumeTrue(in != null, "no dry speech clip");
            dry = readWav(in.readAllBytes());
        }
        assertTrue(dry.pcm().length > dry.rate(), "dry clip is under a second");
        Path dir = Path.of("build/test-renders");
        Files.createDirectories(dir);
        for (String preset : GodVoiceFx.PRESETS) {
            short[] out = GodVoiceFx.process(dry.pcm(), dry.rate(), GodVoiceFx.preset(preset), 1.0);
            int peak = 0;
            for (short v : out) peak = Math.max(peak, Math.abs(v));
            assertTrue(peak > 20000 && peak <= 32767, preset + " peak " + peak);
            // A clipped signal sits on the ceiling for long runs; a clean one only touches it.
            int ceiling = 0;
            for (short v : out) if (Math.abs(v) >= 32760) ceiling++;
            assertTrue(ceiling < out.length / 2000, preset + " clips: " + ceiling + " samples at full scale");
            Files.write(dir.resolve("voice-speech-" + preset + ".wav"), Synthesizer.wav(out, GodVoiceFx.OUT_RATE));
        }
    }

    @Test
    void pitchShiftKeepsLength() {
        float[] x = GodVoiceFx.toFloat(tone(48000, 1.0));
        assertEquals(x.length, GodVoiceFx.pitchShift(x, -5).length);
        assertEquals(16000, GodVoiceFx.resample(x, 48000, 16000).length);
    }

    static float[] sine(int rate, double hz, double seconds) {
        float[] s = new float[(int) (rate * seconds)];
        for (int i = 0; i < s.length; i++) s[i] = (float) (0.5 * Math.sin(2 * Math.PI * hz * i / rate));
        return s;
    }

    /** RMS of the middle of a clip (the ends are where a filter is still filling up). */
    static double rms(float[] x) {
        double sum = 0;
        int from = x.length / 4;
        int to = x.length * 3 / 4;
        for (int i = from; i < to; i++) sum += x[i] * x[i];
        return Math.sqrt(sum / (to - from));
    }

    @Test
    void resamplerKeepsSpeechAndRemovesWhatWouldAlias() {
        double full = 0.5 / Math.sqrt(2);
        // Going up (Piper 22.05 kHz to 48 kHz): a tone near the top of the voice band keeps its level.
        assertEquals(full, rms(GodVoiceFx.resample(sine(22050, 8000, 1.0), 22050, 48000)), full * 0.05);
        // Going down (microphone 48 kHz to Whisper 16 kHz): 3 kHz survives, 11 kHz must not fold to 5 kHz.
        assertEquals(full, rms(GodVoiceFx.resample(sine(48000, 3000, 1.0), 48000, 16000)), full * 0.05);
        assertTrue(rms(GodVoiceFx.resample(sine(48000, 11000, 1.0), 48000, 16000)) < full * 0.01,
                "11 kHz leaked through the downsampler");
    }

    @Test
    void naturalPresetsNeverUseThePitchShifter() {
        for (String name : new String[] { "majestic", "divine", "booming", "commanding", "none" }) {
            GodVoiceFx.Preset p = GodVoiceFx.preset(name);
            assertEquals(0, p.semitones(), name + " semitones");
            assertEquals(0, p.octaveDown(), name + " octaveDown");
            assertEquals(0, p.octaveUp(), name + " octaveUp");
            assertEquals(0, p.chorus(), name + " chorus");
        }
        assertEquals(GodVoiceFx.preset("divine"), GodVoiceFx.preset("no-such-preset"));
    }

    @Test
    void sizeSlowsTheClipByExactlyThePaceTheEngineMakesUp() {
        // Three semitones larger, nothing else: no hall, so no tail is added.
        GodVoiceFx.Preset big = new GodVoiceFx.Preset(-3, 0, 0, 0, 0, 1.0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        double pace = GodVoiceFx.pace(big);
        assertEquals(Math.pow(2, -3 / 12.0), pace, 1e-9);
        assertEquals(1.0, GodVoiceFx.pace(GodVoiceFx.preset("none")), 1e-9);
        short[] out = GodVoiceFx.process(tone(22050, 2.0), 22050, big, 1.0);
        assertEquals(2.0 / pace, (double) out.length / GodVoiceFx.OUT_RATE, 0.01);
        // Piper is told to speak in pace x the solemn duration; the HTTP engines get speed = 1 / pace.
        assertEquals("1.120", Synthesizer.lengthScale(1.0));
        assertEquals("0.942", Synthesizer.lengthScale(pace));
    }

    @Test
    void aPartFollowedByAnotherGetsOnlyABreathOfTail() {
        GodVoiceFx.Preset hall = GodVoiceFx.preset("ethereal");
        short[] in = tone(24000, 2.0);
        long t = System.nanoTime();
        short[] last = GodVoiceFx.process(in, 24000, hall, 1.0, true);
        System.out.println("ethereal effect on a 2 s clip: " + (System.nanoTime() - t) / 1_000_000 + " ms");
        short[] middle = GodVoiceFx.process(in, 24000, hall, 1.0, false);
        double spoken = 2.0 / GodVoiceFx.pace(hall);
        assertEquals(spoken + 0.3, (double) middle.length / GodVoiceFx.OUT_RATE, 0.02);
        assertTrue(last.length > middle.length + GodVoiceFx.OUT_RATE, "the last part rings out");
        // No hall at all: a short pause still separates the sentences.
        short[] dry = GodVoiceFx.process(in, 24000, GodVoiceFx.preset("none"), 1.0, false);
        assertEquals(2.0 + 0.15, (double) dry.length / GodVoiceFx.OUT_RATE, 0.02);
    }

    @Test
    void piperVoiceUrls() throws IOException {
        assertEquals("https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_US/ryan/medium/"
                + "en_US-ryan-medium.onnx", Synthesizer.voiceUrl("en_US-ryan-medium"));
        assertEquals("https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_GB/northern_english_male/"
                + "medium/en_GB-northern_english_male-medium.onnx",
                Synthesizer.voiceUrl("en_GB-northern_english_male-medium"));
    }
}
