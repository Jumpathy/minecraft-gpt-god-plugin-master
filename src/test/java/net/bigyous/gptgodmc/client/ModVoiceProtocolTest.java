package net.bigyous.gptgodmc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The voice messages (say, voice) must match the golden files the client mod's tests decode. */
class ModVoiceProtocolTest {
    private static void same(String name, JsonObject actual) throws Exception {
        Path p = Path.of("client-mod/src/test/resources/protocol", name + ".json");
        assertEquals(JsonParser.parseString(Files.readString(p)), JsonParser.parseString(ModMessages.json(actual)), name);
    }

    @Test
    void sayAndVoiceMatchGolden() throws Exception {
        same("say", ModMessages.say("main", "Vel'Azhar", 0xFF3020, "Kneel, and I may yet be merciful.", "demonic",
                ModVoice.VESSEL, new double[] { 12.5, 70, -3.25 }, 2400,
                new float[] { 0f, 0.42f, 1f, 0.8f, 0.35f, 0.05f }));
        same("say-whisper", ModMessages.say("main", null, 0xC77DFF, "I see you.", null, ModVoice.WHISPER, null, 1200,
                null));
        same("voice", ModMessages.voice("main", 1500, new float[] { 0.2f, 0.9f, 0.6f }));
    }

    @Test
    void sayIsClamped() {
        float[] long_ = new float[5000];
        java.util.Arrays.fill(long_, 7f);
        long_[0] = Float.NaN;
        long_[1] = -3;
        JsonObject o = ModMessages.say("main", "X", 0xFFFFFF, "hi", null, ModVoice.ANNOUNCE, null, 99_999_999, long_);
        assertEquals(60_000, o.get("ms").getAsInt());
        assertEquals(ModMessages.SAY_MAX_ENV, o.getAsJsonArray("env").size());
        assertEquals(0, o.getAsJsonArray("env").get(0).getAsDouble());
        assertEquals(0, o.getAsJsonArray("env").get(1).getAsDouble());
        assertEquals(1, o.getAsJsonArray("env").get(2).getAsDouble());
        assertFalse(o.has("from"));
        assertFalse(o.has("tone"));
        assertFalse(ModMessages.say("main", "X", 0, "hi", null, "whisper", null, 1000, new float[0]).has("env"));
        // The longest line with the longest envelope still fits one message.
        assertTrue(ModMessages.bytes(ModMessages.say("main", "X".repeat(48), 0, "é".repeat(280), "demonic", "vessel",
                new double[] { 1, 2, 3 }, 60_000, long_)).length < ModSessions.MAX_BYTES);
    }

    @Test
    void subtitlesAreAFeatureTheModMustAskFor() {
        ModSessions s = new ModSessions();
        UUID with = UUID.randomUUID();
        UUID without = UUID.randomUUID();
        s.hello(with, "{\"protocol\":1,\"features\":[\"fx\",\"subs\"]}".getBytes(StandardCharsets.UTF_8), "0.2.0");
        s.hello(without, "{\"protocol\":1,\"features\":[\"fx\"]}".getBytes(StandardCharsets.UTF_8), "0.2.0");
        assertTrue(s.has(with, ModSessions.SUBS));
        assertFalse(s.has(without, ModSessions.SUBS));
        assertFalse(s.has(UUID.randomUUID(), ModSessions.SUBS));
    }
}
