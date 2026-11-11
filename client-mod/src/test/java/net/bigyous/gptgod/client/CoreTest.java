package net.bigyous.gptgod.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonParser;

import net.bigyous.gptgod.client.config.ModConfig;
import net.bigyous.gptgod.client.fx.ScreenFx;
import net.bigyous.gptgod.client.fx.SkyMood;
import net.bigyous.gptgod.client.net.Protocol;
import net.bigyous.gptgod.client.state.GodState;

/** The mod's pure parts. The golden files are the same ones the plugin's ModProtocolTest writes against. */
class CoreTest {
    private static byte[] golden(String name) throws Exception {
        try (InputStream in = CoreTest.class.getResourceAsStream("/protocol/" + name + ".json")) {
            return in.readAllBytes();
        }
    }

    @Test
    void decodesTheGodFromThePlugin() throws Exception {
        Protocol.God g = assertInstanceOf(Protocol.God.class, Protocol.decode(golden("god")));
        assertEquals("main", g.realm());
        assertEquals("Vel'Azhar", g.name());
        assertEquals(0xFF3020, g.color());
        assertEquals("wrathful", g.mood());
        assertEquals(72.5f, g.heart().wrath());
        assertTrue(g.thinking());
        assertTrue(g.wounded());
        assertEquals(40f, g.health());
        assertArrayEquals(new double[] { 12.5, 70, -3.25 }, g.pos());
        assertTrue(g.here());
    }

    @Test
    void decodesStandingQuestScreenWelcome() throws Exception {
        Protocol.Standing s = assertInstanceOf(Protocol.Standing.class, Protocol.decode(golden("standing")));
        assertEquals("favored", s.tier());
        assertEquals("Flamebearer", s.title());
        assertEquals("halo", s.mark());
        Protocol.Quest q = assertInstanceOf(Protocol.Quest.class, Protocol.decode(golden("quest")));
        assertEquals(35, q.progress());
        assertNull(assertInstanceOf(Protocol.Quest.class, Protocol.decode(golden("quest-none"))).title());
        Protocol.Screen sc = assertInstanceOf(Protocol.Screen.class, Protocol.decode(golden("screen")));
        assertEquals("shake", sc.kind());
        assertEquals(24, sc.ticks());
        assertEquals(80f, sc.radius());
        assertNull(assertInstanceOf(Protocol.Screen.class, Protocol.decode(golden("screen-on-you"))).from());
        Protocol.Welcome w = assertInstanceOf(Protocol.Welcome.class, Protocol.decode(golden("welcome")));
        assertTrue(w.accepted());
        assertEquals(List.of("fx", "hud", "sky"), w.features());
    }

    @Test
    void helloMatchesGolden() throws Exception {
        assertEquals(JsonParser.parseString(new String(golden("hello"), StandardCharsets.UTF_8)),
                JsonParser.parseString(new String(Protocol.hello("0.1.0", List.of("fx", "hud", "sky")), StandardCharsets.UTF_8)));
    }

    @Test
    void hostileInputNeverThrowsAndIsClamped() {
        for (String bad : List.of("", "x", "[]", "{\"t\":5}", "{\"t\":\"god\",\"heart\":7}", "{\"t\":\"nope\"}",
                "{\"t\":\"screen\",\"intensity\":\"a\"}")) {
            Protocol.decode(bad.getBytes(StandardCharsets.UTF_8));
        }
        Protocol.Screen s = (Protocol.Screen) Protocol.decode(
                "{\"t\":\"screen\",\"kind\":\"FLASH\",\"intensity\":99,\"ticks\":-5,\"color\":\"red\",\"from\":[1e300,0,0],\"radius\":1e9}"
                        .getBytes(StandardCharsets.UTF_8));
        assertEquals("flash", s.kind());
        assertEquals(1f, s.intensity());
        assertEquals(1, s.ticks());
        assertEquals(0xFFFFFF, s.color());
        assertNull(s.from());
        assertEquals(256f, s.radius());
        Protocol.God g = (Protocol.God) Protocol.decode(("{\"t\":\"god\",\"name\":\"§kEvil\\u0007" + "x".repeat(500)
                + "\",\"heart\":{\"wrath\":500}}").getBytes(StandardCharsets.UTF_8));
        assertFalse(g.name().contains("§"));
        assertEquals(48, g.name().length());
        assertEquals(100f, g.heart().wrath());
        assertFalse(g.wounded());
        assertInstanceOf(Protocol.Unknown.class, Protocol.decode(new byte[Protocol.MAX_BYTES + 1]));
    }

    @Test
    void stateFollowsTheServer() throws Exception {
        GodState st = new GodState();
        assertFalse(st.has("hud"));
        st.accept(Protocol.decode(golden("god")), 1);
        assertFalse(st.has("hud")); // nothing counts before the welcome
        st.accept(Protocol.decode(golden("welcome")), 2);
        st.accept(Protocol.decode(golden("god")), 3);
        st.accept(Protocol.decode(golden("quest")), 4);
        assertTrue(st.has("hud"));
        assertEquals("Vel'Azhar", st.main().name());
        assertEquals(35, st.quest("main").progress());
        st.accept(Protocol.decode(golden("quest-none")), 5);
        assertNull(st.quest("main"));
        st.unlink();
        assertNull(st.main());
        assertFalse(st.has("fx"));
    }

    @Test
    void shakeFadesWithDistanceAndTime() {
        ModConfig cfg = new ModConfig();
        ScreenFx fx = new ScreenFx();
        Protocol.Screen far = new Protocol.Screen("shake", 1, 20, 0xFFFFFF, new double[] { 100, 0, 0 }, 50);
        assertFalse(fx.add(far, new double[] { 0, 0, 0 }, 0), "beyond the radius");
        Protocol.Screen near = new Protocol.Screen("shake", 1, 20, 0xFFFFFF, new double[] { 10, 0, 0 }, 50);
        assertTrue(fx.add(near, new double[] { 0, 0, 0 }, 0));
        float early = 0, late = 0;
        for (int ms = 10; ms < 150; ms += 7) early = Math.max(early, Math.abs(fx.frame(ms, cfg).yaw()));
        for (int ms = 850; ms < 990; ms += 7) late = Math.max(late, Math.abs(fx.frame(ms, cfg).yaw()));
        assertTrue(early > late * 4, early + " vs " + late);
        assertTrue(early <= ScreenFx.MAX_SHAKE_DEGREES);
        assertTrue(fx.frame(1001, cfg).idle());
        assertFalse(fx.busy());
    }

    @Test
    void settingsScaleAndNoFlashingSoftens() {
        ModConfig cfg = new ModConfig();
        ScreenFx fx = new ScreenFx();
        fx.add(ScreenFx.Kind.FLASH, 1, 1000, 0xFFFFFF, 0);
        float peak = 0;
        for (int ms = 0; ms < 1000; ms += 5) peak = Math.max(peak, fx.frame(ms, cfg).flashAlpha());
        assertTrue(peak > 0.6f);
        cfg.noFlashing = true;
        fx.add(ScreenFx.Kind.FLASH, 1, 1000, 0xFFFFFF, 2000);
        float soft = 0;
        for (int ms = 2000; ms < 3000; ms += 5) soft = Math.max(soft, fx.frame(ms, cfg).flashAlpha());
        assertTrue(soft <= 0.25f, "no flashing caps it: " + soft);
        cfg.shake = 0;
        fx.add(ScreenFx.Kind.SHAKE, 1, 1000, 0, 5000);
        assertEquals(0f, fx.frame(5100, cfg).yaw());
    }

    @Test
    void skyMoodBlendsNearTheGodAndEases() {
        SkyMood sky = new SkyMood();
        sky.target("wrathful", "walks", new double[] { 0, 64, 0 }, true, 1);
        sky.tick();
        float firstTick = sky.strength(0, 64, 0);
        sky.settle();
        float near = sky.strength(0, 64, 0);
        assertTrue(firstTick < near * 0.2f, "eases in");
        assertEquals(SkyMood.look("wrathful", "walks").mix(), near, 0.01f);
        assertTrue(sky.strength(80, 64, 0) < near);
        float[] tinted = sky.sky(0.5f, 0.7f, 1f, 0, 64, 0);
        assertTrue(tinted[0] > tinted[2], "the sky turns red");
        sky.target("calm", "walks", null, true, 1);
        sky.settle();
        assertFalse(sky.active());
        sky.target("joyful", "walks", null, false, 1);
        sky.settle();
        assertEquals(0f, sky.strength(0, 64, 0), "another world's formless god does not tint ours");
        sky.target("sorrowful", "watches", null, true, 0.5f);
        sky.settle();
        assertEquals(SkyMood.look("sorrowful", "watches").mix() * 0.5f * SkyMood.FORMLESS, sky.strength(0, 0, 0), 0.01f);
    }

    @Test
    void configRoundTripsAndSurvivesGarbage(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("gptgod-client.json");
        ModConfig c = new ModConfig();
        c.shake = 0.3;
        c.noFlashing = true;
        c.hud = ModConfig.HudMode.COMPACT;
        c.save(f);
        ModConfig back = ModConfig.load(f);
        assertEquals(0.3, back.shake);
        assertTrue(back.noFlashing);
        assertEquals(ModConfig.HudMode.COMPACT, back.hud);
        Files.writeString(f, "{\"shake\": 9, \"hud\": \"WAT\"");
        ModConfig broken = ModConfig.load(f);
        assertEquals(1.0, broken.shake);
        c.vanilla = true;
        assertTrue(c.features().isEmpty(), "plain vanilla look asks for nothing");
    }
}
