package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.god.art.Sprite;
import net.bigyous.gptgodmc.god.art.SpritePainter.Art;

/**
 * The textures a god paints for its effects (fx_paint): keeping them, naming them in scenes, and the message
 * the mod decodes. Writes the mod's two sample textures (client-mod/src/test/resources/protocol/texture-*.json;
 * run with the environment variable FX_FIXTURES_WRITE=1 to write them anew) and build/test-renders/fx-textures.png.
 */
class FxPaintTest {
    private static final Path FIXTURES = Path.of("client-mod/src/test/resources/protocol");

    /** Cracked stone with fire in the cracks, as a god would write it: a legend and 16 rows of 16 letters. */
    static JsonObject veins() {
        JsonArray rows = new JsonArray();
        for (int y = 0; y < 16; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < 16; x++) {
                // Two wandering cracks from top to bottom and a short branch, so the tile joins itself up and down.
                boolean crack = x == 4 + (int) Math.round(2 * Math.sin(y * Math.PI / 8)) || x == 11 + (int) Math.round(1.5 * Math.cos(y * Math.PI / 4))
                        || (y == 9 && x > 5 && x < 11);
                row.append(crack ? ((x + y) % 7 == 0 ? 'g' : 'v') : (x * 5 + y * 3) % 23 == 0 ? 'd' : 's');
            }
            rows.add(row.toString());
        }
        JsonObject o = JsonParser.parseString("{\"size\":16,\"legend\":{\"s\":\"stone\",\"d\":\"obsidian\",\"v\":\"flame\",\"g\":\"gem #ff3a2a\"}}")
                .getAsJsonObject();
        o.add("rows", rows);
        return o;
    }

    /** A ring and a cross of light on nothing. */
    static JsonObject sigil() {
        JsonArray rows = new JsonArray();
        for (int y = 0; y < 16; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < 16; x++) {
                double dx = x - 7.5, dy = y - 7.5, r = Math.hypot(dx, dy);
                boolean lit = Math.abs(r - 6.3) < 0.8 || (Math.abs(dx) < 1 && r < 6) || (Math.abs(dy + 1.5) < 1 && Math.abs(dx) < 3.5);
                row.append(lit ? '*' : '.');
            }
            rows.add(row.toString());
        }
        JsonObject o = JsonParser.parseString("{\"size\":16,\"legend\":{\"*\":\"glow #ffd86a\"}}").getAsJsonObject();
        o.add("rows", rows);
        return o;
    }

    @Test
    void texturesAreKeptAcrossRestarts(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("textures.json");
        FxPaint paint = new FxPaint(file);
        assertEquals("none yet", paint.index());
        FxPaint.Entry e = paint.keep("Ember Veins!", veins(), "the idol's stone", 100);
        assertEquals("ember_veins", e.name);
        assertEquals(1, e.rev);
        assertEquals("none", e.sprite.get("outline").getAsString(), "a texture runs edge to edge unless told otherwise");
        Art art = paint.art(e);
        assertEquals(16, art.w());
        assertEquals(8, art.frames().length);
        assertFalse(java.util.Arrays.equals(art.frames()[0], art.frames()[3]), "the fire in the cracks moves");
        assertEquals(2, paint.keep("ember veins", veins(), null, 200).rev, "painting over a name is a new revision");
        assertEquals("ember_veins (the idol's stone)", paint.index());
        FxPaint again = new FxPaint(file);
        assertEquals(1, again.size());
        assertEquals(2, again.find("Ember Veins").rev);
        assertTrue(again.forget("ember_veins"));
        assertNull(new FxPaint(file).find("ember_veins"));
        // What is wrong with a sprite is said to the god; a bad name or a full shelf too.
        JsonObject bad = veins();
        bad.getAsJsonArray("rows").remove(0);
        assertTrue(assertThrows(Sprite.Flaw.class, () -> paint.keep("x", bad, null, 1)).getMessage().contains("rows"));
        assertThrows(IllegalArgumentException.class, () -> paint.keep("!!!", veins(), null, 1));
        FxPaint full = new FxPaint(null);
        for (int i = 0; i < FxPaint.MAX; i++) full.keep("t" + i, sigil(), null, i);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> full.keep("one_more", sigil(), null, 1)).getMessage().contains("forget"));
        assertNotNull(full.keep("t3", veins(), null, 1), "painting over one is still allowed");
    }

    @Test
    void scenesNameTexturesAndTheWireNamesThemPerGod() throws Exception {
        JsonArray layers = FxScenes.check("[{\"type\":\"solid\",\"form\":\"box\",\"texture\":\"Veins\",\"tile\":[2,3]},"
                + "{\"type\":\"field\",\"x\":\"u\",\"texture\":\"sigil\"},{\"type\":\"dome\"}]");
        assertEquals(Set.of("veins", "sigil"), FxPaint.names(layers));
        FxPaint paint = new FxPaint(null);
        assertEquals("sigil", paint.missing(layers));
        paint.keep("sigil", sigil(), null, 1);
        paint.keep("veins", veins(), null, 1);
        assertNull(paint.missing(layers));
        JsonArray wired = FxPaint.wired(layers, "main");
        assertEquals("main.veins", wired.get(0).getAsJsonObject().get("texture").getAsString());
        assertEquals("Veins", layers.get(0).getAsJsonObject().get("texture").getAsString(), "the god's own scene is left as written");
        assertTrue(assertThrows(FxScenes.Invalid.class, () -> FxScenes.check("[{\"type\":\"solid\",\"form\":\"box\",\"texture\":\"no spaces!\"}]"))
                .getMessage().contains("texture you painted"));
        assertTrue(FxScenes.HELP.contains("fx_paint"));
    }

    @Test
    void theMessagesMatchTheGoldenFilesAndThePictureIsDrawn() throws Exception {
        FxPaint paint = new FxPaint(null);
        BufferedImage sheet = new BufferedImage(8 * 132 + 4, 2 * 132 + 4, BufferedImage.TYPE_INT_RGB);
        int row = 0;
        for (String name : new String[] { "veins", "sigil" }) {
            FxPaint.Entry e = paint.keep(name, name.equals("veins") ? veins() : sigil(), null, 1);
            Art art = paint.art(e);
            JsonObject message = FxPaint.message(FxPaint.id("demo", name), e.rev, art);
            assertNotNull(message);
            assertEquals("demo." + name, message.get("id").getAsString());
            assertEquals(8, message.get("frames").getAsInt());
            Path file = FIXTURES.resolve("texture-" + name + ".json");
            // The mod reads the first bytes to tell a texture message from the rest: "t" comes first.
            assertTrue(message.toString().startsWith("{\"t\":\"texture\""));
            if (System.getenv("FX_FIXTURES_WRITE") != null || !Files.exists(file)) Files.writeString(file, message + "\n");
            assertEquals(JsonParser.parseString(Files.readString(file)), message, name);
            for (int f = 0; f < 8; f++) {
                for (int y = 0; y < 128; y++) {
                    for (int x = 0; x < 128; x++) {
                        int p = art.frames()[f][(y / 8) * 16 + x / 8], a = p >>> 24;
                        int ground = ((x / 16 + y / 16) & 1) == 0 ? 0x18 : 0x24;
                        int r = (((p >> 16) & 255) * a + ground * (255 - a)) / 255, g = (((p >> 8) & 255) * a + ground * (255 - a)) / 255,
                                b = ((p & 255) * a + ground * (255 - a)) / 255;
                        sheet.setRGB(4 + f * 132 + x, 4 + row * 132 + y, r << 16 | g << 8 | b);
                    }
                }
            }
            row++;
        }
        File out = new File("build/test-renders/fx-textures.png");
        out.getParentFile().mkdirs();
        ImageIO.write(sheet, "png", out);
    }
}
