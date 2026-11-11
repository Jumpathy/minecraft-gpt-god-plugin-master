package net.bigyous.gptgod.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import net.bigyous.gptgod.client.config.ModConfig;
import net.bigyous.gptgod.client.hud.SubtitleMath;
import net.bigyous.gptgod.client.hud.SubtitleMath.Bearing;
import net.bigyous.gptgod.client.hud.SubtitleMath.Where;
import net.bigyous.gptgod.client.net.Protocol;
import net.bigyous.gptgod.client.state.VoiceLevel;

/** Phase 3, the god's voice: the say/voice messages, the reveal timing, the loudness level, the speaker marker. */
class VoiceTest {
    private static byte[] golden(String name) throws Exception {
        try (InputStream in = VoiceTest.class.getResourceAsStream("/protocol/" + name + ".json")) {
            return in.readAllBytes();
        }
    }

    private static Protocol.Message decode(String json) {
        return Protocol.decode(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void decodesSayAndVoiceFromThePlugin() throws Exception {
        Protocol.Say s = assertInstanceOf(Protocol.Say.class, Protocol.decode(golden("say")));
        assertEquals("main", s.realm());
        assertEquals("Vel'Azhar", s.name());
        assertEquals(0xFF3020, s.color());
        assertEquals("Kneel, and I may yet be merciful.", s.text());
        assertEquals("demonic", s.tone());
        assertEquals("vessel", s.kind());
        assertArrayEquals(new double[] { 12.5, 70, -3.25 }, s.from());
        assertEquals(2400, s.ms());
        assertArrayEquals(new float[] { 0f, 0.42f, 1f, 0.8f, 0.35f, 0.05f }, s.env());

        Protocol.Say w = assertInstanceOf(Protocol.Say.class, Protocol.decode(golden("say-whisper")));
        assertEquals("whisper", w.kind());
        assertNull(w.name());
        assertNull(w.tone());
        assertNull(w.from());
        assertNull(w.env());

        Protocol.Voice v = assertInstanceOf(Protocol.Voice.class, Protocol.decode(golden("voice")));
        assertEquals(1500, v.ms());
        assertArrayEquals(new float[] { 0.2f, 0.9f, 0.6f }, v.env());
    }

    @Test
    void hostileSayIsClampedOrDropped() {
        assertInstanceOf(Protocol.Unknown.class, decode("{\"t\":\"say\"}"));
        assertInstanceOf(Protocol.Unknown.class, decode("{\"t\":\"say\",\"text\":\"   \"}"));
        StringBuilder env = new StringBuilder("[\"x\",-4,9,null");
        for (int i = 0; i < 5000; i++) env.append(",0.5");
        env.append(']');
        Protocol.Say s = assertInstanceOf(Protocol.Say.class, decode("{\"t\":\"say\",\"text\":\"" + "a§c\\u0007 ".repeat(200)
                + "\",\"kind\":\"SCREAM\",\"ms\":1e12,\"from\":[1,2],\"color\":\"red\",\"env\":" + env + "}"));
        assertTrue(s.text().length() <= 300);
        assertFalse(s.text().contains("§"));
        assertEquals("announce", s.kind());
        assertEquals(60_000, s.ms());
        assertNull(s.from());
        assertEquals(0xC77DFF, s.color());
        assertEquals(600, s.env().length);
        assertEquals(0f, s.env()[0]);
        assertEquals(0f, s.env()[1]);
        assertEquals(1f, s.env()[2]);
        assertEquals(0f, s.env()[3]);
        assertNull(assertInstanceOf(Protocol.Say.class, decode("{\"t\":\"say\",\"text\":\"hi\",\"env\":7}")).env());
        assertEquals(300, assertInstanceOf(Protocol.Say.class, decode("{\"t\":\"say\",\"text\":\"hi\",\"ms\":-5}")).ms());
        assertEquals(100, assertInstanceOf(Protocol.Voice.class, decode("{\"t\":\"voice\",\"ms\":0,\"env\":[]}")).ms());
    }

    @Test
    void subtitlesAreAskedForOnlyWhenOn() {
        ModConfig c = new ModConfig();
        assertTrue(c.features().contains("subs"));
        c.subtitles = false;
        assertFalse(c.features().contains("subs"));
        assertTrue(c.features().contains("fx"));
    }

    @Test
    void wordsAppearInStepWithTheVoice() {
        String[] words = "Kneel, and I may yet be merciful.".split(" ");
        int[] at = SubtitleMath.revealTimes(words, 3000);
        assertEquals(0, at[0]);
        for (int i = 1; i < at.length; i++) assertTrue(at[i] > at[i - 1], "in order");
        assertTrue(at[at.length - 1] < 3000 * SubtitleMath.REVEAL_SHARE, "the last word appears before the voice ends");
        // The comma after "Kneel," is a pause: the gap after it is longer than after the short "I".
        assertTrue(at[1] - at[0] > at[3] - at[2]);
        assertEquals(0, SubtitleMath.revealed(at, -1));
        assertEquals(1, SubtitleMath.revealed(at, 0));
        assertEquals(words.length, SubtitleMath.revealed(at, 3000));
        assertEquals(3, SubtitleMath.revealed(at, at[2]));

        assertEquals(0f, SubtitleMath.wordAlpha(500, 499));
        assertEquals(0.5f, SubtitleMath.wordAlpha(500, 500 + SubtitleMath.WORD_FADE_MS / 2), 0.01f);
        assertEquals(1f, SubtitleMath.wordAlpha(500, 5000));
        assertEquals(0, SubtitleMath.revealTimes(new String[0], 1000).length);
        assertEquals(0, SubtitleMath.revealTimes(new String[] { "one" }, 0)[0]);
    }

    @Test
    void theLineFadesInHoldsAndFadesOut() {
        int linger = SubtitleMath.lingerMs(40);
        assertTrue(linger >= 1200 && linger <= 4000);
        assertEquals(1200, SubtitleMath.lingerMs(3));
        assertEquals(4000, SubtitleMath.lingerMs(280));
        assertEquals(0f, SubtitleMath.lineAlpha(-10, 2000, linger));
        assertEquals(0.5f, SubtitleMath.lineAlpha(SubtitleMath.FADE_IN_MS / 2, 2000, linger), 0.01f);
        assertEquals(1f, SubtitleMath.lineAlpha(1000, 2000, linger));
        assertEquals(1f, SubtitleMath.lineAlpha(2000 + linger, 2000, linger));
        assertEquals(0.5f, SubtitleMath.lineAlpha(2000 + linger + SubtitleMath.FADE_OUT_MS / 2, 2000, linger), 0.01f);
        assertEquals(0f, SubtitleMath.lineAlpha(2000 + linger + SubtitleMath.FADE_OUT_MS, 2000, linger));
        assertFalse(SubtitleMath.over(2000 + linger, 2000, linger));
        assertTrue(SubtitleMath.over(2000 + linger + SubtitleMath.FADE_OUT_MS, 2000, linger));
    }

    @Test
    void wrapsIntoRowsAndPages() {
        // Words 30 wide with a space of 4 in a row of 100: three fit (98), the fourth starts a new row.
        assertArrayEquals(new int[] { 0, 0, 0, 1, 1 }, SubtitleMath.wrap(new int[] { 30, 30, 30, 30, 30 }, 4, 100));
        assertArrayEquals(new int[] { 0, 1, 2 }, SubtitleMath.wrap(new int[] { 300, 20, 300 }, 4, 100));
        assertEquals(0, SubtitleMath.wrap(new int[0], 4, 100).length);
        // Two rows a page: rows 0-1, then 2-3, then 4.
        assertEquals(0, SubtitleMath.firstRow(1, 5, 2));
        assertEquals(2, SubtitleMath.firstRow(2, 5, 2));
        assertEquals(2, SubtitleMath.firstRow(3, 5, 2));
        assertEquals(4, SubtitleMath.firstRow(4, 5, 2));
        assertEquals(0, SubtitleMath.firstRow(1, 2, 2));
    }

    @Test
    void theLevelFollowsTheEnvelope() {
        float[] env = { 0f, 1f, 0.5f };
        assertEquals(0f, VoiceLevel.sample(env, 50), 1e-4f); // the middle of the first tenth
        assertEquals(1f, VoiceLevel.sample(env, 150), 1e-4f);
        assertEquals(0.5f, VoiceLevel.sample(env, 100), 1e-4f); // halfway between them
        assertEquals(0.75f, VoiceLevel.sample(env, 200), 1e-4f);
        assertEquals(0.25f, VoiceLevel.sample(env, 300), 1e-4f); // fading into the silence after
        assertEquals(0f, VoiceLevel.sample(env, 400), 1e-4f);
        assertEquals(0f, VoiceLevel.sample(env, -5));
        assertEquals(0f, VoiceLevel.sample(null, 100));
        assertEquals(0f, VoiceLevel.sample(new float[0], 100));

        VoiceLevel v = new VoiceLevel();
        assertEquals(0f, v.level(1000));
        v.start("main", 1000, 300, env);
        assertTrue(v.speaking("main", 1150));
        assertTrue(v.measured("main", 1150));
        assertEquals(1f, v.level("main", 1150), 1e-4f);
        assertEquals(1f, v.level(1150), 1e-4f);
        assertEquals(0f, v.level("other", 1150));
        assertFalse(v.speaking("main", 1300));
        assertEquals(0f, v.level("main", 1300));
        assertEquals(0f, v.level("main", 900));
        // No audio to follow: a murmur between 0 and 1 while the line lasts, flagged as not measured.
        v.start("main", 2000, 1000, null);
        assertFalse(v.measured("main", 2500));
        boolean moved = false;
        for (long t = 2000; t < 3000; t += 20) {
            float l = v.level("main", t);
            assertTrue(l >= 0 && l <= 1);
            moved |= l > 0.5f;
        }
        assertTrue(moved);
        assertEquals(0f, v.level("main", 3000));
        v.clear();
        assertFalse(v.speaking("main", 2500));
    }

    @Test
    void theMarkerPointsAtASpeakerOffScreen() {
        double[] eye = { 0, 64, 0 };
        float fov = 70;
        float aspect = 16f / 9f;
        // Looking south (yaw 0, +Z). Straight ahead: on screen, no lean.
        Bearing ahead = SubtitleMath.bearing(eye, 0, 0, new double[] { 0, 64, 10 }, fov, aspect);
        assertEquals(Where.AHEAD, ahead.where());
        assertEquals(0f, ahead.yaw(), 0.01f);
        assertEquals(0f, SubtitleMath.nudge(ahead.yaw()), 0.01f);
        // West (-X) is to the right of someone facing south.
        Bearing right = SubtitleMath.bearing(eye, 0, 0, new double[] { -10, 64, 0 }, fov, aspect);
        assertEquals(Where.RIGHT, right.where());
        assertEquals(90f, right.yaw(), 0.01f);
        assertEquals(1f, SubtitleMath.nudge(right.yaw()), 0.01f);
        Bearing left = SubtitleMath.bearing(eye, 0, 0, new double[] { 10, 64, 0 }, fov, aspect);
        assertEquals(Where.LEFT, left.where());
        assertEquals(-1f, SubtitleMath.nudge(left.yaw()), 0.01f);
        // Behind: off screen to one side, the subtitle only half leaning.
        Bearing behind = SubtitleMath.bearing(eye, 0, 0, new double[] { -1, 64, -10 }, fov, aspect);
        assertEquals(Where.RIGHT, behind.where());
        assertTrue(Math.abs(SubtitleMath.nudge(behind.yaw())) < 0.6f);
        // Turning to face it puts it ahead again (yaw 90 = west).
        assertEquals(Where.AHEAD, SubtitleMath.bearing(eye, 90, 0, new double[] { -10, 64, 0 }, fov, aspect).where());
        // Slightly off centre but inside the view: still on screen, leaning a little.
        Bearing slight = SubtitleMath.bearing(eye, 0, 0, new double[] { -3, 64, 10 }, fov, aspect);
        assertEquals(Where.AHEAD, slight.where());
        assertTrue(SubtitleMath.nudge(slight.yaw()) > 0.1f && SubtitleMath.nudge(slight.yaw()) < 0.3f);
        // High above (a towering vessel) and below.
        assertEquals(Where.ABOVE, SubtitleMath.bearing(eye, 0, 0, new double[] { 0, 90, 6 }, fov, aspect).where());
        assertEquals(Where.BELOW, SubtitleMath.bearing(eye, 0, 0, new double[] { 0, 40, 6 }, fov, aspect).where());
        // Looking up at it (pitch negative = up) brings it on screen.
        assertEquals(Where.AHEAD, SubtitleMath.bearing(eye, 0, -70, new double[] { 0, 90, 6 }, fov, aspect).where());
        // Standing inside the speaker: nothing to point at.
        assertEquals(Where.AHEAD, SubtitleMath.bearing(eye, 123, 45, eye, fov, aspect).where());
        assertEquals(-170f, SubtitleMath.wrap(190), 0.01f);
        assertEquals(180f, SubtitleMath.wrap(-180), 0.01f);
    }
}
