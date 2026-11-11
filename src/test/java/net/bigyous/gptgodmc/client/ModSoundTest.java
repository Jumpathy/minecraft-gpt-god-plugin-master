package net.bigyous.gptgodmc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

/** The sound message matches the golden file the client mod decodes. */
class ModSoundTest {
    @Test
    void soundMatchesGolden() throws Exception {
        String golden = Files.readString(Path.of("client-mod/src/test/resources/protocol/sound.json"));
        assertEquals(JsonParser.parseString(golden), JsonParser.parseString(ModMessages.json(
                ModMessages.sound("gptgodmc:vessel.wings.unfurl.angel", new double[] { 10.5, 70, -3 }, 1.2, 0.9))));
    }

    @Test
    void volumeAndPitchAreClamped() {
        var o = ModMessages.sound("x", new double[3], 99, 0.01);
        assertEquals(4, o.get("volume").getAsDouble());
        assertEquals(0.5, o.get("pitch").getAsDouble());
    }
}
