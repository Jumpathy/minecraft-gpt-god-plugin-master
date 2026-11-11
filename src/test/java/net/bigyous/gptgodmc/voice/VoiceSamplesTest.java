package net.bigyous.gptgodmc.voice;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Renders the clips behind voice_samples/: every dry voice in build/voice-dry through every effect
 * preset, with the plugin's own effect code, into build/voice-samples/&lt;voice&gt;/&lt;preset&gt;.wav.
 *
 * <p>A dry clip is the speech engine's raw output, named {@code <voice>@<pace>.wav}. The pace is
 * {@link GodVoiceFx#pace} of the preset it is meant for, to three decimals: presets that make the
 * speaker larger slow the recording down, so their dry clip was spoken faster to begin with (the
 * plugin asks the engine for exactly that at run time). Skipped when there are no dry clips, since
 * the speech engines are not part of the build.
 */
class VoiceSamplesTest {

    @Test
    void renderEveryVoiceThroughEveryPreset() throws IOException {
        Path dry = Path.of("build/voice-dry");
        assumeTrue(Files.isDirectory(dry), "no dry voice clips in build/voice-dry");
        List<Path> clips;
        try (Stream<Path> files = Files.list(dry)) {
            clips = files.filter(p -> p.getFileName().toString().endsWith("@1.000.wav")).sorted().toList();
        }
        assumeTrue(!clips.isEmpty(), "no dry voice clips in build/voice-dry");
        Path out = Path.of("build/voice-samples");
        int rendered = 0;
        for (Path plain : clips) {
            String file = plain.getFileName().toString();
            String voice = file.substring(0, file.indexOf('@'));
            Path dir = out.resolve(voice);
            Files.createDirectories(dir);
            for (String name : GodVoiceFx.PRESETS) {
                GodVoiceFx.Preset preset = GodVoiceFx.preset(name);
                Path source = dry.resolve(voice + "@" + String.format(Locale.ROOT, "%.3f", GodVoiceFx.pace(preset))
                        + ".wav");
                if (!Files.exists(source)) continue;
                GodVoiceFxTest.Clip clip = GodVoiceFxTest.readWav(Files.readAllBytes(source));
                short[] pcm = GodVoiceFx.process(clip.pcm(), clip.rate(), preset, 1.0);
                int ceiling = 0;
                for (short v : pcm) if (Math.abs(v) >= 32760) ceiling++;
                assertTrue(ceiling < pcm.length / 2000, voice + "/" + name + " clips");
                Files.write(dir.resolve(name + ".wav"), Synthesizer.wav(pcm, GodVoiceFx.OUT_RATE));
                rendered++;
            }
        }
        assertTrue(rendered > 0, "dry clips were found but none matched a preset's pace");
    }
}
