package net.bigyous.gptgod.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgod.client.fx.SoundBank;
import net.bigyous.gptgod.client.fx.Synth;
import net.bigyous.gptgod.client.net.Protocol;

/** The synthesizer and the mod's own sounds. Also writes each sound to build/sounds/*.wav for listening. */
class SynthTest {
    private static double rms(short[] a, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) sum += (double) a[i] * a[i];
        return Math.sqrt(sum / Math.max(1, to - from)) / 32767;
    }

    @Test
    void everyWingSoundExistsAndIsAudible() throws Exception {
        Path dir = Path.of("build", "sounds");
        Files.createDirectories(dir);
        for (String cue : List.of("appear", "unfurl", "furl", "vanish", "beat")) {
            for (String style : List.of("angel", "demon")) {
                String id = "gptgodmc:vessel.wings." + cue + "." + style;
                short[] audio = SoundBank.audio(id);
                assertTrue(audio != null, id);
                double seconds = audio.length / (double) Synth.RATE;
                assertTrue(seconds > 0.2 && seconds < 3.5, id + " lasts " + seconds);
                assertTrue(rms(audio, 0, audio.length) > 0.03, id + " is audible");
                int peak = 0;
                for (short s : audio) peak = Math.max(peak, Math.abs(s));
                assertTrue(peak > 28000 && peak <= 32767, id + " peak " + peak);
                assertEquals(0, audio[0], id + " starts without a click");
                ByteBuffer bytes = ByteBuffer.allocate(audio.length * 2).order(ByteOrder.LITTLE_ENDIAN);
                for (short s : audio) bytes.putShort(s);
                try (AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(bytes.array()),
                        new AudioFormat(Synth.RATE, 16, 1, true, false), audio.length)) {
                    AudioSystem.write(in, AudioFileFormat.Type.WAVE, dir.resolve("wings-" + cue + "-" + style + ".wav").toFile());
                }
            }
        }
        // The god's own sounds: all render, are audible, and are exported too.
        int gods = 0;
        for (String id : SoundBank.ids()) {
            if (!id.startsWith("gptgodmc:god.")) continue;
            gods++;
            short[] audio = SoundBank.audio(id);
            assertTrue(rms(audio, 0, audio.length) > 0.02, id + " is audible");
            assertTrue(audio.length <= Synth.MAX_MS * Synth.RATE / 1000, id);
            assertTrue(SoundBank.patch(id).voices().size() <= Synth.MAX_VOICES, id + " has too many voices");
            ByteBuffer bytes = ByteBuffer.allocate(audio.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (short v : audio) bytes.putShort(v);
            try (AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(bytes.array()),
                    new AudioFormat(Synth.RATE, 16, 1, true, false), audio.length)) {
                AudioSystem.write(in, AudioFileFormat.Type.WAVE,
                        dir.resolve(id.substring("gptgodmc:".length()).replace('.', '-') + ".wav").toFile());
            }
        }
        assertTrue(gods >= 15, "god sounds: " + gods);
        assertNull(SoundBank.audio("gptgodmc:nope"));
    }

    @Test
    void demonWingsAreLowerAndHeavierThanAngelWings() {
        // Compare how much of the energy survives a crude low-pass: the demon's should be mostly low.
        for (String cue : List.of("unfurl", "beat")) {
            double angel = lowShare(SoundBank.audio("gptgodmc:vessel.wings." + cue + ".angel"));
            double demon = lowShare(SoundBank.audio("gptgodmc:vessel.wings." + cue + ".demon"));
            assertTrue(demon > angel + 0.1, cue + ": demon " + demon + " vs angel " + angel);
        }
    }

    private static double lowShare(short[] a) {
        double lp = 0, low = 0, all = 0;
        double k = 1 - Math.exp(-2 * Math.PI * 300 / Synth.RATE);
        for (short s : a) {
            lp += k * (s - lp);
            low += lp * lp;
            all += (double) s * s;
        }
        return low / all;
    }

    @Test
    void renderingIsDeterministicAndBounded() {
        Synth.Patch p = new Synth.Patch(List.of(Synth.Voice.of(Synth.Wave.SINE, 0, 440, 880, 10, 100, 100, 1),
                Synth.Voice.of(Synth.Wave.NOISE, 50, 1, 1, 10, 50, 50, 0.5f).lowpass(500, 4000)), 80, 0.4f, 5);
        assertArrayEquals(Synth.render(p), Synth.render(p));
        Synth.Patch huge = new Synth.Patch(List.of(Synth.Voice.of(Synth.Wave.SAW, 0, 100, 100, 0, 600_000, 0, 1)));
        assertTrue(Synth.render(huge).length <= Synth.MAX_MS * Synth.RATE / 1000);
        Synth.Patch silent = new Synth.Patch(List.of(Synth.Voice.of(Synth.Wave.SINE, 0, 440, 440, 10, 10, 10, 0)));
        for (short s : Synth.render(silent)) assertEquals(0, s);
    }

    @Test
    void theSoundMessageDecodes() throws Exception {
        try (InputStream in = SynthTest.class.getResourceAsStream("/protocol/sound.json")) {
            Protocol.Tone t = assertInstanceOf(Protocol.Tone.class, Protocol.decode(in.readAllBytes()));
            assertEquals("gptgodmc:vessel.wings.unfurl.angel", t.id());
            assertEquals(1.2f, t.volume());
            assertEquals(0.9f, t.pitch());
            assertArrayEquals(new double[] { 10.5, 70, -3 }, t.at());
            assertTrue(SoundBank.has(t.id()));
        }
    }

    @Test
    void aSceneCanCarryASoundTheGodDesigned() {
        String json = "{\"layers\":[{\"type\":\"synth\",\"delay\":10,\"volume\":2,\"echo\":[120,0.4],\"voices\":["
                + "{\"wave\":\"noise\",\"attack\":300,\"hold\":100,\"release\":400,\"gain\":0.7,\"lowpass\":[300,6000]},"
                + "{\"wave\":\"sine\",\"freq\":[80,30],\"start\":400,\"attack\":5,\"hold\":50,\"release\":900,\"gain\":1},"
                + "{\"wave\":\"kazoo\",\"freq\":99999,\"gain\":9,\"release\":99999999}]},{\"type\":\"synth\",\"voices\":[]}]}";
        net.bigyous.gptgod.client.fx.Scene s = net.bigyous.gptgod.client.fx.Scene
                .decode(com.google.gson.JsonParser.parseString(json).getAsJsonObject());
        assertEquals(1, s.layers().size(), "a synth without voices is dropped");
        net.bigyous.gptgod.client.fx.Scene.Tone tone = assertInstanceOf(net.bigyous.gptgod.client.fx.Scene.Tone.class, s.layers().get(0));
        assertEquals(3, tone.patch().voices().size());
        assertEquals(Synth.Wave.NOISE, tone.patch().voices().get(0).wave());
        assertEquals(6000f, tone.patch().voices().get(0).lp1());
        assertEquals(30f, tone.patch().voices().get(1).f1());
        assertEquals(Synth.Wave.SINE, tone.patch().voices().get(2).wave(), "an unknown wave falls back to a sine");
        assertEquals(1f, tone.patch().voices().get(2).gain());
        assertEquals(120, tone.patch().echoMs());
        short[] audio = Synth.render(tone.patch());
        assertTrue(audio.length <= Synth.MAX_MS * Synth.RATE / 1000, "never longer than the cap");
        assertTrue(rms(audio, 0, audio.length) > 0.02);
        assertTrue(s.life() >= 10 + tone.duration());
    }
}
