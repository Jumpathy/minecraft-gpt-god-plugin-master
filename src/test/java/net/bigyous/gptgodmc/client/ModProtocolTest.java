package net.bigyous.gptgodmc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.client.ModMessages.GodView;
import net.bigyous.gptgodmc.client.ModMessages.Screen;

/**
 * The plugin's messages must match the golden files the client mod's tests decode
 * (client-mod/src/test/resources/protocol), and the handshake must never treat a vanilla player as a mod user.
 */
class ModProtocolTest {
    private static JsonElement golden(String name) throws Exception {
        Path p = Path.of("client-mod/src/test/resources/protocol", name + ".json");
        return JsonParser.parseString(Files.readString(p));
    }

    private static void same(String name, com.google.gson.JsonObject actual) throws Exception {
        assertEquals(golden(name), JsonParser.parseString(ModMessages.json(actual)), name);
    }

    @Test
    void godMatchesGolden() throws Exception {
        same("god", ModMessages.god(new GodView("main", "Vel'Azhar", "the Unquiet Flame", "wrathful", 0xFF3020,
                "wrathful", 72.5, 0, 4, 10, 0, "walks", true, 40.0, 100.0, null, new double[] { 12.5, 70, -3.25 },
                true)));
    }

    @Test
    void godWithStyleAndOmenMatchGolden() throws Exception {
        var style = net.bigyous.gptgodmc.god.FxStyle.fromJson("""
                {"primary":"#FF8A1F","secondary":"#FF3020","accent":"#FFE9A8","shadow":"#2B0A05","hud":"#FFB060",
                 "sigil":"\u2739","motion":"sudden and violent","sprites":["ember","spark","ray"],
                 "shapes":["burst","beam","ring"],"sky":{"calm":"#E8B090","wrathful":"#B01010","joyful":"#FFD86B",
                 "sorrowful":"#7FB2FF","ominous":"#5A1E8C","exalted":"#FFF1B0"}}""", "wrathful");
        GodView view = new GodView("main", "Vel'Azhar", "the Unquiet Flame", "wrathful", 0xFF3020,
                "wrathful", 72.5, 0, 4, 10, 0, "walks", true, 40.0, 100.0, null, new double[] { 12.5, 70, -3.25 }, true);
        same("god-style", ModMessages.god(view, style.wire()));
        // Without a style the message is exactly the old one.
        same("god", ModMessages.god(view, null));
        same("omen", ModMessages.omen("main", "Vel'Azhar", "I saw what you buried.", "\u2739", 0xFFB060, 120));
        var bare = ModMessages.omen("main", null, " ", "\u2739", 0x102030, 99999);
        assertEquals(400, bare.get("ticks").getAsInt());
        assertEquals(null, bare.get("text"));
        assertEquals(null, bare.get("god"));
    }

    @Test
    void omensGoOnlyToModsThatAskForThem() {
        ModSessions s = new ModSessions();
        UUID old = UUID.randomUUID(), fresh = UUID.randomUUID();
        s.hello(old, "{\"protocol\":1,\"features\":[\"fx\",\"hud\",\"sky\"]}".getBytes(StandardCharsets.UTF_8), "0.2.0");
        s.hello(fresh, "{\"protocol\":1,\"features\":[\"hud\",\"omen\"]}".getBytes(StandardCharsets.UTF_8), "0.2.0");
        assertEquals(false, s.has(old, ModSessions.OMEN));
        assertEquals(true, s.has(fresh, ModSessions.OMEN));
    }

    @Test
    void standingQuestScreenMatchGolden() throws Exception {
        same("standing", ModMessages.standing("main", "favored", "Flamebearer", "gold", "halo"));
        same("quest", ModMessages.quest("main", "Vel'Azhar", "Raise a tower of gold", 35));
        same("quest-none", ModMessages.quest("main", "Vel'Azhar", null, 0));
        same("screen", ModMessages.screen(Screen.SHAKE, 0.75, 24, 0xFFFFFF, new double[] { 100.5, 64, -20 }, 80));
        same("screen-on-you", ModMessages.screen(Screen.FLASH, 0.9, 10, 0xFFF4D6, null, 0));
    }

    @Test
    void screenValuesAreClamped() {
        var o = ModMessages.screen(Screen.FLASH, 7, 99999, 0x123456, new double[] { 0, 0, 0 }, 9999);
        assertEquals(1, o.get("intensity").getAsDouble());
        assertEquals(400, o.get("ticks").getAsInt());
        assertEquals(256, o.get("radius").getAsDouble());
    }

    @Test
    void helloFromTheModGrantsFeatures() throws Exception {
        ModSessions s = new ModSessions();
        UUID id = UUID.randomUUID();
        byte[] hello = Files.readString(Path.of("client-mod/src/test/resources/protocol/hello.json"))
                .getBytes(StandardCharsets.UTF_8);
        ModSessions.Reply r = s.hello(id, hello, "0.2.0");
        assertNotNull(r.session());
        assertEquals(golden("welcome"), JsonParser.parseString(ModMessages.json(r.welcome())));
        assertTrue(s.has(id, ModSessions.FX));
        assertTrue(s.allow(id, ModSessions.HUD, 100, 0));
    }

    @Test
    void vanillaPlayersAreNeverSentAnything() {
        ModSessions s = new ModSessions();
        UUID id = UUID.randomUUID();
        assertFalse(s.has(id, ModSessions.FX));
        assertFalse(s.allow(id, ModSessions.FX, 10, 0));
    }

    @Test
    void wrongProtocolOrGarbageStaysVanilla() {
        ModSessions s = new ModSessions();
        UUID id = UUID.randomUUID();
        for (String bad : List.of("{\"protocol\":2,\"features\":[\"fx\"]}", "not json", "[1,2]", "{}",
                "{\"protocol\":\"x\"}", "{\"protocol\":1,\"features\":[]}")) {
            ModSessions.Reply r = s.hello(id, bad.getBytes(StandardCharsets.UTF_8), "0.2.0");
            assertNull(r.session(), bad);
            assertEquals(0, r.welcome().getAsJsonArray("features").size(), bad);
            assertFalse(s.has(id, ModSessions.FX), bad);
        }
    }

    @Test
    void onlyAskedAndKnownFeaturesAreGranted() {
        ModSessions s = new ModSessions();
        UUID id = UUID.randomUUID();
        s.hello(id, "{\"protocol\":1,\"features\":[\"hud\",\"teleport-everyone\"]}".getBytes(StandardCharsets.UTF_8),
                "0.2.0");
        assertTrue(s.has(id, ModSessions.HUD));
        assertFalse(s.has(id, ModSessions.FX));
        assertFalse(s.has(id, "teleport-everyone"));
        s.forget(id);
        assertFalse(s.has(id, ModSessions.HUD));
    }

    @Test
    void sizeAndRateAreLimited() {
        ModSessions s = new ModSessions();
        UUID id = UUID.randomUUID();
        s.hello(id, "{\"protocol\":1,\"features\":[\"fx\"]}".getBytes(StandardCharsets.UTF_8), "0.2.0");
        assertFalse(s.allow(id, ModSessions.FX, ModSessions.MAX_BYTES + 1, 0));
        int sent = 0;
        for (int i = 0; i < 200; i++) if (s.allow(id, ModSessions.FX, 10, 1000)) sent++;
        assertEquals((int) ModSessions.RATE, sent);
        assertTrue(s.allow(id, ModSessions.FX, 10, 1100)); // refills over time
    }
}
