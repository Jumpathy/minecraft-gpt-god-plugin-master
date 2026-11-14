package net.bigyous.gptgodmc.god.art;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.client.ModRelics;
import net.bigyous.gptgodmc.client.ModSessions;
import net.bigyous.gptgodmc.god.art.Relics.Relic;
import net.bigyous.gptgodmc.god.fx.FxSolid;

/** A special relic's true shape: checked, built in the picture's square, drawn for the artist, sent to the mod. */
class RelicShapeTest {
    @AfterEach
    void forget() {
        Relics.load(null);
    }

    private static JsonArray parts(String json) {
        return JsonParser.parseString(json).getAsJsonArray();
    }

    @Test
    void theScytheIsTracedFromItsPicture() throws Exception {
        JsonArray shape = RelicShape.normalized(Samples.SCYTHE.sprite().shape());
        assertEquals(8, shape.size());
        for (var e : shape) {
            JsonObject o = e.getAsJsonObject();
            assertEquals(1, o.get("count").getAsInt(), "one of each");
            assertFalse(o.has("light"), "matter, lit by the world");
        }
        JsonObject shaft = shape.get(0).getAsJsonObject();
        assertEquals("darkwood", shaft.get("material").getAsString());
        assertEquals(RelicTiles.name("darkwood"), shaft.get("texture").getAsString(), "a material wears its tile");
        assertFalse(shaft.has("color"), "the tile carries the colour");
        float[] faces = RelicShape.build(shape, 24);
        assertTrue(faces.length > 0 && faces.length % FxSolid.FLOATS == 0);
        double minX = 9, maxX = -9, minY = 9, maxY = -9, maxZ = 0;
        for (int k = 0; k < faces.length; k += FxSolid.VERTEX) {
            minX = Math.min(minX, faces[k]);
            maxX = Math.max(maxX, faces[k]);
            minY = Math.min(minY, faces[k + 1]);
            maxY = Math.max(maxY, faces[k + 1]);
            maxZ = Math.max(maxZ, Math.abs(faces[k + 2]));
        }
        // It fills the same square as the picture (centred, a block across) and is a little thick.
        assertTrue(minX < -0.35 && maxX > 0.35 && minY < -0.35 && maxY > 0.35, minX + ".." + maxX + ", " + minY + ".." + maxY);
        assertTrue(maxX < 0.6 && maxY < 0.6 && minX > -0.6 && minY > -0.6);
        assertTrue(maxZ > 0.01 && maxZ < 0.12, "thin, as a held thing is: " + maxZ);
        BufferedImage pic = RelicShape.picture(RelicShape.model(shape, 24), 120, 0x17111F);
        assertTrue(pic.getWidth() > 300);
        // Saved and read back, the sprite keeps its shape.
        Sprite again = Sprite.parse(Samples.SCYTHE.sprite().toJson());
        assertEquals(Samples.SCYTHE.sprite().shape(), again.shape());
        assertNull(Samples.BLADE.sprite().shape());
    }

    @Test
    void partsWearTheirMaterialsAtATexelASixteenth() throws Exception {
        // Each material has its own tile, opaque, in more than one tone, and they differ from one another.
        java.util.Map<String, int[]> seen = new java.util.HashMap<>();
        for (String material : new String[] { "darkwood", "steel", "gold", "bone", "leather", "gem #7a5cff", "#c9ced6" }) {
            int[] tile = RelicTiles.paint(material);
            assertEquals(RelicTiles.SIZE * RelicTiles.SIZE, tile.length);
            java.util.Set<Integer> tones = new java.util.HashSet<>();
            for (int c : tile) {
                assertEquals(0xFF, c >>> 24, material + " is opaque");
                tones.add(c);
            }
            assertTrue(tones.size() >= 2, material + " has a texture, not one flat colour");
            for (var other : seen.entrySet()) assertFalse(java.util.Arrays.equals(other.getValue(), tile), material + " vs " + other.getKey());
            seen.put(material, tile);
        }
        // The shaft: 18 sixteenths long and 1.1 across, so its sides span 18 texels along and about one across.
        RelicShape.Model model = RelicShape.model(RelicShape.normalized(Samples.SCYTHE.sprite().shape()), 16);
        float[] f = model.faces();
        double longest = 0, widest = 0;
        for (int at = 0; at < 6 * FxSolid.FLOATS; at += FxSolid.FLOATS) {
            double u0 = 9, u1 = -9, v0 = 9, v1 = -9;
            for (int k = 0; k < 4; k++) {
                u0 = Math.min(u0, f[at + k * FxSolid.VERTEX + 7]);
                u1 = Math.max(u1, f[at + k * FxSolid.VERTEX + 7]);
                v0 = Math.min(v0, f[at + k * FxSolid.VERTEX + 8]);
                v1 = Math.max(v1, f[at + k * FxSolid.VERTEX + 8]);
            }
            longest = Math.max(longest, (v1 - v0) * RelicTiles.SIZE);
            widest = Math.max(widest, (u1 - u0) * RelicTiles.SIZE);
        }
        assertEquals(18.0, longest, 0.01, "a texel a sixteenth along the shaft");
        assertEquals(1.1, widest, 0.01, "and across it");
        assertEquals("m_darkwood", model.textures()[0]);
        // On a 32 canvas the texels are half as large, as the picture's are.
        float[] fine = RelicShape.build(RelicShape.normalized(Samples.SCYTHE.sprite().shape()), 32);
        int c = 7 + 2 * FxSolid.VERTEX;
        assertEquals(2 * (f[c] - f[7]), fine[c] - fine[7], 1e-4);
        assertTrue(Math.abs(f[c] - f[7]) > 0.05);
        assertEquals("m_steel", model.textures()[model.textures().length - 1]);
        // Turning a part does not move its texture: the grid runs with the part.
        JsonArray upright = RelicShape.normalized(parts("[{\"form\":\"box\",\"size\":[1,8,1],\"offset\":[8,8,0],\"material\":\"wood\"}]"));
        JsonArray turned = RelicShape.normalized(parts("[{\"form\":\"box\",\"size\":[1,8,1],\"offset\":[8,8,0],\"roll\":-45,\"material\":\"wood\"}]"));
        float[] a = RelicShape.build(upright, 16), b = RelicShape.build(turned, 16);
        for (int k = 0; k < a.length; k += FxSolid.VERTEX) {
            assertEquals(a[k + 7], b[k + 7], 1e-5);
            assertEquals(a[k + 8], b[k + 8], 1e-5);
        }
    }

    @Test
    void shapesAreBlockyUnlessARoundOneIsAskedFor() throws Exception {
        JsonArray n = RelicShape.normalized(parts("[{\"form\":\"lathe\",\"profile\":[[1,0],[1,6]],\"sides\":16,"
                + "\"offset\":[8,4,0]},{\"form\":\"ball\",\"radius\":1.5,\"offset\":[8,12,0]},{\"form\":\"tube\","
                + "\"path\":[[3,3,0],[6,6,0]],\"sides\":12},{\"form\":\"ball\",\"radius\":1,\"offset\":[12,12,0],"
                + "\"round\":true}]"));
        assertEquals(4, n.get(0).getAsJsonObject().get("sides").getAsInt(), "a post, not a cylinder");
        assertEquals(45, n.get(0).getAsJsonObject().get("yaw").getAsInt(), "a face to the front");
        assertEquals("box", n.get(1).getAsJsonObject().get("form").getAsString(), "a ball is a cube");
        assertEquals(3.0, n.get(1).getAsJsonObject().get("size").getAsDouble(), 1e-9);
        assertEquals(4, n.get(2).getAsJsonObject().get("sides").getAsInt());
        assertEquals("ball", n.get(3).getAsJsonObject().get("form").getAsString(), "round when asked");
        assertFalse(n.get(3).getAsJsonObject().has("round"));
        // The scythe: its shaft, pommel and collar are boxes; only its blade curves.
        JsonArray scythe = RelicShape.normalized(Samples.SCYTHE.sprite().shape());
        for (int i = 0; i < 3; i++) assertEquals("box", scythe.get(i).getAsJsonObject().get("form").getAsString());
        for (int i = 3; i < scythe.size(); i++) assertEquals("quads", scythe.get(i).getAsJsonObject().get("form").getAsString());
        assertTrue(RelicShape.HELP.contains("Minecraft") && RelicShape.HELP.contains("\"round\": true"));
    }

    @Test
    void mistakesInAShapeAreNamed() {
        String[][] bad = {
                { "[]", "no parts" },
                { "[{\"form\":\"ball\",\"radius\":2,\"x\":\"sin(t)\"}]", "must be a number" },
                { "[{\"form\":\"ball\",\"radius\":2,\"color\":\"mauve\"}]", "neither #rrggbb nor a material" },
                { "[{\"form\":\"ball\",\"radius\":3,\"offset\":[40,8,0]}]", "outside the picture" },
                { "[{\"form\":\"spiral\"}]", "Part 1" },
                { "[{\"form\":\"tube\",\"path\":[[1,1,0]]}]", "Part 1" },
                { "[1]", "must be an object" } };
        for (String[] b : bad) {
            RelicShape.Flaw f = assertThrows(RelicShape.Flaw.class, () -> RelicShape.normalized(parts(b[0])), b[0]);
            assertTrue(f.getMessage().contains(b[1]), b[0] + " -> " + f.getMessage());
        }
        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i <= RelicShape.MAX_PARTS; i++) many.append(i > 0 ? "," : "").append("{\"form\":\"ball\",\"radius\":1,\"offset\":[8,8,0]}");
        assertThrows(RelicShape.Flaw.class, () -> RelicShape.normalized(parts(many + "]")));
        // Light and textures are left out: a relic is matter, lit by the world.
        try {
            JsonObject o = RelicShape.normalized(parts("[{\"form\":\"ball\",\"radius\":1,\"offset\":[8,8,0],\"light\":true,"
                    + "\"texture\":\"veins\",\"color\":[\"gold\",\"#fff\"]}]")).get(0).getAsJsonObject();
            assertFalse(o.has("light") || o.has("color"));
            assertEquals("gold", o.get("material").getAsString(), "the first colour is what it is made of");
            assertEquals("m_gold", o.get("texture").getAsString(), "its own tile, not the scene's texture");
        } catch (RelicShape.Flaw e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void theArtistSeesItsShapeAndItsMistakes() throws Exception {
        Atelier.Attempt good = Atelier.attempt(Samples.SCYTHE.json(), "staff", 2, false);
        assertFalse(good.error(), good.text());
        assertTrue(good.text().contains("true shape"), good.text());
        BufferedImage proof = ImageIO.read(new ByteArrayInputStream(good.proof()));
        BufferedImage plain = ImageIO.read(new ByteArrayInputStream(Atelier.attempt(Samples.STAFF.json(), "staff", 2, false).proof()));
        assertTrue(proof.getHeight() > plain.getHeight() + 100, "the shape is drawn under the picture");
        JsonObject broken = Samples.SCYTHE.json();
        broken.add("shape", parts("[{\"form\":\"ball\",\"radius\":2,\"color\":\"mauve\"}]"));
        Atelier.Attempt bad = Atelier.attempt(broken, "staff", 2, false);
        assertTrue(bad.error() && bad.text().contains("shape cannot be built"), bad.text());
        assertTrue(Atelier.system().contains("true shape") && Atelier.system().contains("lathe {profile"));
    }

    @Test
    void theModIsSentEachFormsShape(@TempDir Path dir) throws Exception {
        Relics.load(dir);
        Samples.Sample s = Samples.SCYTHE;
        Relic r = Relics.add("samples", "the first artist", s.name(), s.kind(), s.note(), s.sprite(), List.of(s.powers()), 1);
        assertNotNull(Relics.find("gravecall").sprite().get("shape"), "kept with the relic");
        JsonObject m = ModRelics.message(r, Relics.art(r));
        assertEquals(RelicShape.normalized(s.sprite().shape()), m.getAsJsonArray("shape"));
        assertTrue(m.toString().getBytes(StandardCharsets.UTF_8).length <= ModSessions.MAX_BYTES);
        JsonObject textures = m.getAsJsonObject("textures");
        assertEquals(java.util.Set.of("m_darkwood", "m_gold", "m_steel"), textures.keySet());
        for (String name : textures.keySet()) {
            BufferedImage tile = ImageIO.read(new ByteArrayInputStream(java.util.Base64.getDecoder().decode(textures.get(name).getAsString())));
            assertEquals(RelicTiles.SIZE, tile.getWidth());
            assertEquals(RelicTiles.SIZE, tile.getHeight());
        }
        Relic plain = Relics.add("main", "Vaelor", "Dawnbreaker", "blade", "", Samples.BLADE.sprite(), 2);
        assertFalse(ModRelics.message(plain, Relics.art(plain)).has("shape"));

        // A preview: the picture, and the same relic as its true shape from the front and turned.
        BufferedImage picture = SpriteSheet.enlarged(Relics.art(r), 0, 10, 0x17111F);
        BufferedImage shape = RelicShape.picture(RelicShape.model(RelicShape.normalized(s.sprite().shape()), s.sprite().w()), 200, 0x17111F);
        BufferedImage sheet = new BufferedImage(picture.getWidth() + 16 + shape.getWidth(),
                Math.max(picture.getHeight(), shape.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new java.awt.Color(0x17111F));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.drawImage(picture, 0, (sheet.getHeight() - picture.getHeight()) / 2, null);
        g.drawImage(shape, picture.getWidth() + 16, 0, null);
        g.dispose();
        File out = new File("build/test-renders");
        out.mkdirs();
        ImageIO.write(sheet, "png", new File(out, "relic-shape-gravecall.png"));
        // Close, as it is held: every texel of its materials visible.
        ImageIO.write(RelicShape.view(RelicShape.model(RelicShape.normalized(s.sprite().shape()), s.sprite().w()), Math.toRadians(-42),
                Math.toRadians(20), 560, 0x17111F), "png", new File(out, "relic-shape-gravecall-close.png"));
    }
}
