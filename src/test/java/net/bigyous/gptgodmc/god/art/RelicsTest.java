package net.bigyous.gptgodmc.god.art;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.client.ModRelics;
import net.bigyous.gptgodmc.client.ModSessions;
import net.bigyous.gptgodmc.god.art.Relics.Relic;
import net.bigyous.gptgodmc.god.art.SpritePainter.Art;
import net.bigyous.gptgodmc.god.vessel.VesselPack;

class RelicsTest {
    @AfterEach
    void forgetRelics() {
        Relics.load(null);
    }

    private static JsonObject sprite(int size, Map<String, String> legend, String... rows) {
        JsonObject o = new JsonObject();
        o.addProperty("size", size);
        JsonObject key = new JsonObject();
        legend.forEach(key::addProperty);
        o.add("legend", key);
        JsonArray r = new JsonArray();
        for (String row : rows) r.add(row);
        o.add("rows", r);
        return o;
    }

    private static String[] blank(int size) {
        String[] rows = new String[size];
        java.util.Arrays.fill(rows, ".".repeat(size));
        return rows;
    }

    // ---- The painter ------------------------------------------------------------------------------------

    @Test
    void everyExamplePaintsTheSameEveryTime() {
        for (Samples.Sample s : Samples.ALL) {
            Sprite sprite = s.sprite();
            Art a = SpritePainter.paint(sprite, Atelier.halo(s.kind())), b = SpritePainter.paint(sprite, Atelier.halo(s.kind()));
            assertEquals(SpritePainter.FRAMES, a.frames().length, s.name());
            int solid = 0;
            for (int f = 0; f < a.frames().length; f++) {
                assertEquals(sprite.w() * sprite.h(), a.frames()[f].length, s.name());
                assertArrayEquals(a.frames()[f], b.frames()[f], s.name() + " frame " + f);
            }
            for (int y = 0; y < sprite.h(); y++) {
                for (int x = 0; x < sprite.w(); x++) {
                    boolean drawn = sprite.at(x, y) != null;
                    if (drawn) {
                        solid++;
                        assertEquals(255, a.at(0, x, y) >>> 24, s.name() + " is solid where it is drawn");
                    } else if (!Atelier.halo(s.kind())) {
                        assertEquals(0, a.at(0, x, y) >>> 24, s.name() + " is empty where nothing is drawn");
                    }
                }
            }
            assertTrue(solid > 20, s.name());
            // The examples follow their own advice (with two forms, it is the awake one that is judged).
            assertEquals(List.of(), SpriteLint.notes(sprite.alt() != null ? sprite.alt() : sprite, s.kind()), s.name());
            // Written out and read back, it is the same sprite.
            try {
                assertArrayEquals(a.frames()[0], SpritePainter.paint(Sprite.parse(sprite.toJson()), Atelier.halo(s.kind()))
                        .frames()[0], s.name());
            } catch (Sprite.Flaw e) {
                throw new AssertionError(e);
            }
        }
    }

    @Test
    void lightShapesEveryMaterial() throws Sprite.Flaw {
        // A square of steel: its corner toward the light is brighter than the far one, in flat tones of its ramp.
        String[] rows = blank(16);
        for (int y = 4; y < 12; y++) rows[y] = "....ssssssss....";
        Sprite s = Sprite.parse(sprite(16, Map.of("s", "steel"), rows));
        Art art = SpritePainter.paint(s, false);
        assertTrue(brightness(art.at(5, 5, 5)) > brightness(art.at(5, 10, 10)), "lit toward the top left");
        java.util.Set<Integer> tones = new java.util.HashSet<>();
        for (int v : Pigment.STEEL.ramp(-1)) tones.add(0xFF000000 | v);
        for (int y = 4; y < 12; y++) {
            for (int x = 4; x < 12; x++) assertTrue(tones.contains(art.at(5, x, y)), "only the ramp's five tones");
        }
        assertNull(art.lit(), "steel does not shine by itself");
        // The same square lit from the other side.
        JsonObject o = sprite(16, Map.of("s", "steel"), rows);
        o.addProperty("light", "top-right");
        Art right = SpritePainter.paint(Sprite.parse(o), false);
        assertTrue(brightness(right.at(5, 10, 5)) > brightness(right.at(5, 5, 10)), "lit toward the top right");
        // Light is written to the second layer, and breathes.
        Art staff = SpritePainter.paint(Samples.STAFF.sprite(), false);
        assertNotNull(staff.lit());
        assertEquals(255, staff.lit()[0][2 * 16 + 12] >>> 24, "the orb shines");
        assertEquals(0, staff.lit()[0][14 * 16] >>> 24, "the wooden foot does not");
        // A dark outline fills the empty texels that touch the shape; a halo is soft.
        o = Samples.STAFF.json();
        o.addProperty("outline", "dark");
        Art lined = SpritePainter.paint(Sprite.parse(o), false);
        assertEquals(255, lined.at(0, 1, 15) >>> 24, "outline beside the foot");
        Art wisp = SpritePainter.paint(Samples.WISP.sprite(), true);
        int alpha = wisp.at(0, 8, 2) >>> 24;
        assertTrue(alpha > 0 && alpha < 255, "a soft halo beside the wisp: " + alpha);
    }

    private static int brightness(int argb) {
        return ((argb >> 16) & 255) + ((argb >> 8) & 255) + (argb & 255);
    }

    @Test
    void rampsRunFromShadowToLightWithoutPlainBlackOrWhite() {
        for (Pigment p : Pigment.values()) {
            int[] ramp = p.ramp(p.tinted() ? 0x6A3AB8 : -1);
            assertEquals(5, ramp.length);
            for (int i = 1; i < 5; i++) assertTrue(brightness(ramp[i]) >= brightness(ramp[i - 1]), p + " tone " + i);
        }
        int[] violet = Pigment.CLOTH.ramp(0x6A3AB8);
        assertTrue(brightness(violet[0]) > 0, "a shadow keeps its color");
        assertFalse((violet[0] & 0xFF) == ((violet[0] >> 16) & 0xFF), "shadows are not grey");
        assertNotNull(Pigment.named("Gold"));
        assertEquals(Pigment.FLAME, Pigment.named("fire"));
        assertNull(Pigment.named("plastic"));
        assertTrue(Pigment.names(true).contains("gem") && Pigment.names(false).contains("gold"));
    }

    // ---- What the artist may write ----------------------------------------------------------------------

    @Test
    void legendsAndTheirMistakes() throws Sprite.Flaw {
        Sprite.Ink gem = Sprite.ink("r", "gem #2aa6e0");
        assertEquals(Pigment.GEM, gem.pigment());
        assertEquals(0x2AA6E0, gem.color());
        assertEquals(-1, gem.tone());
        Sprite.Ink eye = Sprite.ink("e", "#05040a deep");
        assertEquals(Pigment.PAINT, eye.pigment());
        assertEquals(0, eye.tone());
        assertEquals(1, Sprite.ink("c", "cloth #5a2e9e dark").tone());
        assertEquals(-1, Sprite.ink("g", "gold").color(), "gold keeps its own color");
        assertEquals(0xFFAA00, Sprite.ink("x", "#fa0").color());

        Sprite.Flaw unknown = assertThrows(Sprite.Flaw.class, () -> Sprite.ink("p", "plastic"));
        assertTrue(unknown.getMessage().contains("gold") && unknown.getMessage().contains("gem"), unknown.getMessage());
        assertThrows(Sprite.Flaw.class, () -> Sprite.ink("p", "gem #12"));

        String[] rows = blank(16);
        rows[3] = "....gggg........";
        assertNotNull(Sprite.parse(sprite(16, Map.of("g", "gold"), rows)));
        // A short row is padded; a long one, a missing one and an unnamed letter are faults said plainly.
        rows[4] = "..gg";
        assertNotNull(Sprite.parse(sprite(16, Map.of("g", "gold"), rows)).at(2, 4));
        rows[4] = ".".repeat(17);
        String[] wide = rows.clone();
        assertTrue(assertThrows(Sprite.Flaw.class, () -> Sprite.parse(sprite(16, Map.of("g", "gold"), wide))).getMessage()
                .contains("Row 4"));
        rows[4] = "....q...........";
        String[] odd = rows.clone();
        assertTrue(assertThrows(Sprite.Flaw.class, () -> Sprite.parse(sprite(16, Map.of("g", "gold"), odd))).getMessage()
                .contains("'q'"));
        assertTrue(assertThrows(Sprite.Flaw.class, () -> Sprite.parse(sprite(16, Map.of("g", "gold"), blank(15))))
                .getMessage().contains("15 rows"));
        assertThrows(Sprite.Flaw.class, () -> Sprite.parse(sprite(64, Map.of("g", "gold"), blank(64))));
        assertThrows(Sprite.Flaw.class, () -> Sprite.parse(sprite(16, Map.of(), blank(16))));
    }

    @Test
    void theTeacherSaysWhatIsWrong() throws Sprite.Flaw {
        String[] rows = blank(16);
        rows[7] = ".......gg.......";
        rows[8] = ".......gg.......";
        List<String> small = SpriteLint.notes(Sprite.parse(sprite(16, Map.of("g", "gold"), rows)), "trinket");
        assertTrue(small.stream().anyMatch(n -> n.contains("larger")), small.toString());
        // A blade lying flat is told to lie on the diagonal; one that does is not.
        rows = blank(16);
        for (int y = 7; y <= 9; y++) rows[y] = ".ssssssssssssss.";
        List<String> flat = SpriteLint.notes(Sprite.parse(sprite(16, Map.of("s", "steel"), rows)), "blade");
        assertTrue(flat.stream().anyMatch(n -> n.contains("diagonal")), flat.toString());
        assertFalse(SpriteLint.notes(Samples.BLADE.sprite(), "blade").stream().anyMatch(n -> n.contains("diagonal")));
        // A full square has no silhouette; a speck is noise.
        String[] full = new String[16];
        java.util.Arrays.fill(full, "g".repeat(16));
        List<String> blob = SpriteLint.notes(Sprite.parse(sprite(16, Map.of("g", "gold"), full)), "trinket");
        assertTrue(blob.stream().anyMatch(n -> n.contains("silhouette")), blob.toString());
        rows = Samples.AMULET.rows().clone();
        rows[0] = "g" + rows[0].substring(1);
        rows[1] = "." + rows[1].substring(1);
        List<String> speck = SpriteLint.notes(Sprite.parse(sprite(16, Map.of("g", "gold", "b", "gem #2aa6e0"), rows)),
                "trinket");
        assertTrue(speck.stream().anyMatch(n -> n.contains("alone")), speck.toString());
    }

    // ---- The artist's side of the loop --------------------------------------------------------------------

    @Test
    void anAttemptIsPaintedAndJudged() throws IOException {
        Atelier.Attempt good = Atelier.attempt(Samples.BLADE.json(), "blade", 2);
        assertFalse(good.error());
        assertTrue(good.text().contains("2 more times"), good.text());
        BufferedImage proof = ImageIO.read(new ByteArrayInputStream(good.proof()));
        assertTrue(proof.getWidth() > 300 && proof.getHeight() > 200, proof.getWidth() + "x" + proof.getHeight());
        assertTrue(Atelier.attempt(Samples.BLADE.json(), "blade", 0).text().contains("last attempt"));
        JsonObject broken = Samples.BLADE.json();
        broken.getAsJsonArray("rows").remove(0);
        Atelier.Attempt bad = Atelier.attempt(broken, "blade", 1);
        assertTrue(bad.error() && bad.proof() == null && bad.text().contains("15 rows"), bad.text());

        String system = Atelier.system();
        for (Samples.Sample s : Samples.ALL) assertTrue(system.contains(s.name()), s.name());
        for (String m : List.of("gold", "obsidian", "soul", "gem #2aa6e0", "accents", "top-right")) {
            assertTrue(system.contains(m), m);
        }
        String ask = Atelier.commission(new Atelier.Brief("Tidecaller", "staff", "A coral staff with a pearl.",
                "The god Vaelor."), 4);
        assertTrue(ask.contains("Tidecaller") && ask.contains("diagonal") && ask.contains("up to 4") && ask.contains("Vaelor"));
    }

    @Test
    void paintingNeedsAStrongModelAndRealEffort() {
        assertNull(ArtSettings.modelProblem("claude-opus-5-5", false));
        assertNull(ArtSettings.modelProblem("claude-fable-5-2", false));
        assertNotNull(ArtSettings.modelProblem("claude-sonnet-5-5", false));
        assertNotNull(ArtSettings.modelProblem("claude-haiku-5-5", false));
        assertNull(ArtSettings.modelProblem("my-gateway-model", true), "a gateway's names are not judged");
        assertEquals("high", ArtSettings.effort("low"));
        assertEquals("high", ArtSettings.effort("medium"));
        assertEquals("xhigh", ArtSettings.effort("XHigh"));
        assertEquals("max", ArtSettings.effort("max"));
        assertFalse(ArtSettings.enabled(), "off until an operator turns it on");
        String warning = ArtSettings.warning();
        assertTrue(warning.contains(ArtSettings.DEFAULT_MODEL) && warning.contains("usage") && warning.contains("$"),
                warning);
    }

    // ---- Keeping, limits, the pack and the mod ------------------------------------------------------------

    @Test
    void relicsAreKeptAndFoundAgain(@TempDir Path dir) {
        Relics.load(dir);
        long now = 1_700_000_000_000L;
        Relic blade = Relics.add("main", "Vaelor", "Dawn-breaker!", "blade", "A blade of first light.", Samples.BLADE.sprite(),
                now);
        assertEquals("dawn_breaker", blade.id());
        assertEquals(1, blade.rev());
        assertTrue(Files.isRegularFile(dir.resolve("dawn_breaker.json")));
        assertEquals(blade, Relics.find("Dawn-breaker!"));
        assertEquals(blade, Relics.find("dawn"));
        // Painted again by the same god it is the same relic, revised; another god's gets its own id.
        assertEquals(2, Relics.add("main", "Vaelor", "Dawn-breaker!", "blade", "", Samples.BLADE.sprite(), now).rev());
        assertEquals("dawn_breaker_2", Relics.add("other", "Zel", "Dawn Breaker", "blade", "", Samples.BLADE.sprite(), now)
                .id());
        Relics.add("main", "Vaelor", "Lantern Wisp", "spirit", "", Samples.WISP.sprite(), now);
        Relics.load(dir);
        assertEquals(3, Relics.all().size());
        assertEquals(2, Relics.find("dawn_breaker").rev());
        assertEquals(24, Relics.art(Relics.find("lantern_wisp")).w());
        assertTrue(Relics.remove("dawn_breaker_2"));
        assertFalse(Files.exists(dir.resolve("dawn_breaker_2.json")));
        assertNull(Relics.find("dawn_breaker_2"));
        assertEquals(List.of("A relic with a long", "description that goes…"),
                Relics.wrap("A relic with a long description that goes on and on", 22, 2));
    }

    @Test
    void theForgeHasLimits() {
        long now = 1_700_000_000_000L, hour = 3_600_000L;
        Sprite s = Samples.VIAL.sprite();
        List<Relic> none = List.of();
        assertTrue(Relics.refusal(none, "main", now, false, 3, 10, 48).contains("creations.enabled"));
        assertNull(Relics.refusal(none, "main", now, true, 3, 10, 48));
        Relic recent = new Relic("a", "A", "trinket", "main", "V", "", now - 5 * 60_000, 1, s.toJson(), List.of());
        assertTrue(Relics.refusal(List.of(recent), "main", now, true, 3, 10, 48).contains("cooling"));
        assertNull(Relics.refusal(List.of(recent), "other", now, true, 3, 10, 48), "another god's forge is its own");
        Relic old = new Relic("b", "B", "trinket", "main", "V", "", now - 2 * hour, 1, s.toJson(), List.of());
        assertNull(Relics.refusal(List.of(old), "main", now, true, 3, 10, 48));
        assertTrue(Relics.refusal(List.of(old, old, old), "main", now, true, 3, 10, 48).contains("today"));
        assertTrue(Relics.refusal(List.of(old), "other", now, true, 3, 10, 1).contains("as many relics"));
        Relic sample = new Relic("c", "C", "trinket", "samples", "", "", now, 1, s.toJson(), List.of());
        assertNull(Relics.refusal(List.of(sample), "main", now, true, 3, 10, 1), "the examples do not count");
    }

    @Test
    void relicsRideInThePackAndGoToTheMod(@TempDir Path dir) throws IOException {
        Relics.load(dir);
        Relic blade = Relics.add("main", "Vaelor", "Dawnbreaker", "blade", "", Samples.BLADE.sprite(), 1);
        Relic vial = Relics.add("main", "Vaelor", "Vial", "trinket", "", Samples.VIAL.sprite(), 2);
        Map<String, byte[]> files = RelicPack.files();
        String model = new String(files.get("assets/gptgodmc/models/item/relic/dawnbreaker.json"), StandardCharsets.UTF_8);
        assertTrue(model.contains("minecraft:item/handheld") && model.contains("gptgodmc:item/relic/dawnbreaker"), model);
        assertTrue(new String(files.get("assets/gptgodmc/models/item/relic/vial.json"), StandardCharsets.UTF_8)
                .contains("minecraft:item/generated"));
        assertTrue(new String(files.get("assets/gptgodmc/items/relic/vial.json"), StandardCharsets.UTF_8)
                .contains("gptgodmc:item/relic/vial"));
        BufferedImage strip = ImageIO.read(new ByteArrayInputStream(files.get(
                "assets/gptgodmc/textures/item/relic/dawnbreaker.png")));
        assertEquals(16, strip.getWidth());
        assertEquals(16 * SpritePainter.FRAMES, strip.getHeight());
        assertTrue(files.containsKey("assets/gptgodmc/textures/item/relic/dawnbreaker.png.mcmeta"));
        assertEquals("gptgodmc:relic/vial", RelicPack.modelId(vial.id()));
        assertTrue(VesselPack.files().keySet().containsAll(files.keySet()), "they ride in the god's pack");

        // The message the client mod reads.
        JsonObject m = ModRelics.message(vial, Relics.art(vial));
        assertEquals("relic", m.get("t").getAsString());
        assertEquals("vial", m.get("id").getAsString());
        assertEquals(16, m.get("w").getAsInt());
        assertEquals(SpritePainter.FRAMES, m.get("frames").getAsInt());
        assertTrue(m.toString().getBytes(StandardCharsets.UTF_8).length <= ModSessions.MAX_BYTES);
        BufferedImage sent = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(m.get("png").getAsString())));
        assertEquals(16, sent.getWidth());
        assertEquals(16 * SpritePainter.FRAMES, sent.getHeight());
        assertTrue(m.has("lit"), "the vial's light goes in its own layer");
        assertFalse(ModRelics.message(blade, Relics.art(blade)).has("lit") && false);
        assertEquals("relic_gone", ModRelics.gone("vial").get("t").getAsString());
        assertTrue(ModSessions.SERVER_FEATURES.contains(ModRelics.FEATURE));

        // What is published is square, whatever the sprite's shape.
        JsonObject tall = new JsonObject();
        tall.addProperty("width", 12);
        tall.addProperty("height", 20);
        JsonObject key = new JsonObject();
        key.addProperty("s", "soul");
        tall.add("legend", key);
        JsonArray rows = new JsonArray();
        for (int y = 0; y < 20; y++) rows.add(y > 2 && y < 17 ? "..ssssssss.." : "............");
        tall.add("rows", rows);
        try {
            Relic ghost = Relics.add("main", "Vaelor", "Tall Ghost", "spirit", "", Sprite.parse(tall), 3);
            JsonObject g = ModRelics.message(ghost, Relics.art(ghost));
            assertEquals(20, g.get("w").getAsInt());
            assertEquals(20, g.get("h").getAsInt());
            Art squared = Relics.art(ghost).squared();
            assertEquals(Relics.art(ghost).at(0, 3, 5), squared.at(0, 3 + 4, 5), "centered across");
        } catch (Sprite.Flaw e) {
            throw new AssertionError(e);
        }
    }
}
