package net.bigyous.gptgod.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgod.client.config.ModConfig;
import net.bigyous.gptgod.client.net.Protocol;
import net.bigyous.gptgod.client.relic.RelicArt;
import net.bigyous.gptgod.client.relic.RelicMesh;
import net.bigyous.gptgod.client.relic.RelicMesh.Face;
import net.bigyous.gptgod.client.relic.RelicMesh.Side;
import net.bigyous.gptgod.client.relic.RelicProtocol;
import net.bigyous.gptgod.client.relic.RelicSamples;
import net.bigyous.gptgod.client.relic.RelicStore;

/** Relic art: decoding the plugin's messages, the store, and the shape a sprite is drawn as. All pure. */
class RelicTest {
    private static final int STEEL = 0xFFE4ECF2, WOOD = 0xFF6B4423, CYAN = 0xFF62F5FF;

    private static byte[] golden(String name) throws Exception {
        try (InputStream in = RelicTest.class.getResourceAsStream("/protocol/" + name + ".json")) {
            return in.readAllBytes();
        }
    }

    /** A PNG of these ARGB pixels, as the base64 text a message carries. */
    private static String png(int w, int h, int[] argb) throws Exception {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, w, h, argb, 0, w);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    /** A well-formed relic message: w x h, the given frames, every texel steel. */
    private static JsonObject message(String id, int rev, int w, int h, int frames) throws Exception {
        int[] pixels = new int[w * h * frames];
        java.util.Arrays.fill(pixels, STEEL);
        JsonObject o = new JsonObject();
        o.addProperty("t", "relic");
        o.addProperty("id", id);
        o.addProperty("rev", rev);
        o.addProperty("name", "Test Relic");
        o.addProperty("kind", "trinket");
        o.addProperty("w", w);
        o.addProperty("h", h);
        o.addProperty("frames", frames);
        o.addProperty("ticks", 4);
        o.addProperty("png", png(w, h * frames, pixels));
        return o;
    }

    private static RelicProtocol.Message decode(JsonObject o) {
        return RelicProtocol.decode(o.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static RelicProtocol.Message decode(String json) {
        return RelicProtocol.decode(json.getBytes(StandardCharsets.UTF_8));
    }

    private static RelicArt art(JsonObject o) {
        return assertInstanceOf(RelicProtocol.Art.class, decode(o)).art();
    }

    /** Art painted straight from a picture: '#' steel, '*' glowing cyan, '~' too faint to draw, anything else empty. */
    private static RelicArt painted(String... rows) {
        int w = rows[0].length(), h = rows.length;
        int[] base = new int[w * h], lit = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                char c = rows[y].charAt(x);
                if (c == '#' || c == '*') base[y * w + x] = STEEL;
                if (c == '*') lit[y * w + x] = CYAN;
                if (c == '~') base[y * w + x] = 0x14FFFFFF;
            }
        }
        return RelicArt.of("painted", 1, "Painted", RelicArt.Kind.TRINKET, w, h, 1, 1, base, lit);
    }

    @Test
    void decodesTheRelicFromThePlugin() throws Exception {
        RelicArt a = assertInstanceOf(RelicProtocol.Art.class, RelicProtocol.decode(golden("relic"))).art();
        assertEquals("ember_blade", a.id());
        assertEquals(3, a.rev());
        assertEquals("Ember Blade", a.name());
        assertEquals(RelicArt.Kind.BLADE, a.kind());
        assertTrue(a.kind().handheld());
        assertEquals(8, a.w());
        assertEquals(8, a.h());
        assertEquals(2, a.frames());
        assertEquals(4, a.ticks());
        assertEquals(8 * 16, a.pixels().length);
        // The blade runs up the diagonal: wood at the grip, steel above it, nothing beside it.
        assertEquals(WOOD, a.pixel(0, 0, 7));
        assertEquals(STEEL, a.pixel(0, 4, 3));
        assertEquals(STEEL, a.pixel(1, 4, 3), "frame 2 is the rows below frame 1");
        assertEquals(0, a.pixel(0, 0, 0));
        assertFalse(a.visible(0, 0, 0));
        assertFalse(a.glows(0, 4, 3));
        // The glowing layer lies over the picture: its solid texel replaces the steel, its half-clear one tints it.
        assertEquals(CYAN, a.pixel(0, 7, 0));
        assertEquals(0xFFFFFFFF, a.pixel(1, 7, 0));
        assertTrue(a.glows(0, 7, 0));
        assertTrue(a.glows(0, 6, 1));
        int tinted = a.pixel(0, 6, 1);
        assertEquals(255, tinted >>> 24);
        assertTrue(((tinted >> 16) & 255) > 0x62 && ((tinted >> 16) & 255) < 0xE4, "between cyan and steel");
        assertFalse(a.translucent());

        RelicProtocol.Gone gone = assertInstanceOf(RelicProtocol.Gone.class, RelicProtocol.decode(golden("relic-gone")));
        assertEquals("ember_blade", gone.id());
    }

    @Test
    void leavesEveryOtherMessageAlone() throws Exception {
        for (String name : List.of("god", "standing", "quest", "screen", "welcome", "say", "gui-open")) {
            assertNull(RelicProtocol.decode(golden(name)), name);
        }
        assertNull(decode("{\"t\":\"say\",\"text\":\"Behold my relic\"}"), "the word alone does not make it ours");
        assertNull(decode("{\"t\":\"relics\"}"));
        assertNull(RelicProtocol.decode(null));
        assertNull(RelicProtocol.decode(new byte[0]));
        // And the relic messages mean nothing to the main decoder.
        assertInstanceOf(Protocol.Unknown.class, Protocol.decode(golden("relic")));
    }

    @Test
    void numbersAreClampedStringsCleanedAndUnknownFieldsIgnored() throws Exception {
        JsonObject o = message("moon_1", -5, 16, 16, 1);
        o.addProperty("ticks", 99);
        o.addProperty("name", "  §kLoud\u0007 " + "x".repeat(200));
        o.addProperty("kind", "BLADE");
        o.addProperty("some_new_field", 7);
        o.add("another", new JsonObject());
        RelicArt a = art(o);
        assertEquals(0, a.rev());
        assertEquals(RelicArt.MAX_TICKS, a.ticks());
        assertFalse(a.name().contains("§"));
        assertTrue(a.name().startsWith("kLoud"));
        assertTrue(a.name().length() <= 64);
        assertEquals(RelicArt.Kind.BLADE, a.kind());

        o.addProperty("ticks", 0);
        o.addProperty("kind", "chalice");
        o.remove("name");
        o.remove("rev");
        a = art(o);
        assertEquals(1, a.ticks());
        assertEquals(RelicArt.Kind.TRINKET, a.kind(), "a kind this mod does not know is drawn as a plain item");
        assertFalse(a.kind().handheld());
        assertEquals("moon_1", a.name());
        assertEquals(0, a.rev());
        o.remove("ticks");
        assertEquals(4, art(o).ticks());
        assertTrue(RelicArt.Kind.STAFF.handheld());
        assertFalse(RelicArt.Kind.TOME.handheld());
        assertFalse(RelicArt.Kind.SPIRIT.handheld());
    }

    @Test
    void malformedRelicsAreDroppedAndNothingThrows() throws Exception {
        for (String id : new String[] { "", "Ember", "ember blade", "ember-blade", "../x", "a".repeat(41), "émber" }) {
            JsonObject o = message("ok", 1, 16, 16, 1);
            o.addProperty("id", id);
            assertInstanceOf(RelicProtocol.Bad.class, decode(o), "id '" + id + "'");
            assertInstanceOf(RelicProtocol.Bad.class, decode("{\"t\":\"relic_gone\",\"id\":\"" + id + "\"}"), "gone '" + id + "'");
        }
        assertNotNull(art(message("a".repeat(40), 1, 16, 16, 1)));
        assertInstanceOf(RelicProtocol.Bad.class, decode("{\"t\":\"relic_gone\"}"));
        assertInstanceOf(RelicProtocol.Bad.class, decode("{\"t\":\"relic\"}"));
        assertInstanceOf(RelicProtocol.Bad.class, decode("{\"t\":\"relic\",\"id\":7,\"w\":\"16\",\"h\":[],\"frames\":{},\"png\":5}"));

        // Sizes out of range, or not whole numbers.
        int[][] sizes = { { 7, 16, 1 }, { 33, 16, 1 }, { 16, 7, 1 }, { 16, 49, 1 }, { 16, 16, 0 }, { 16, 16, 9 }, { -16, 16, 1 },
                { 1 << 20, 1 << 20, 8 } };
        for (int[] s : sizes) {
            JsonObject o = message("ok", 1, 16, 16, 1);
            o.addProperty("w", s[0]);
            o.addProperty("h", s[1]);
            o.addProperty("frames", s[2]);
            assertInstanceOf(RelicProtocol.Bad.class, decode(o), s[0] + "x" + s[1] + "x" + s[2]);
        }
        JsonObject half = message("ok", 1, 16, 16, 1);
        half.addProperty("w", 16.5);
        assertInstanceOf(RelicProtocol.Bad.class, decode(half));
        JsonObject missing = message("ok", 1, 16, 16, 1);
        missing.remove("frames");
        assertInstanceOf(RelicProtocol.Bad.class, decode(missing));
        // The largest and smallest that are allowed do decode.
        assertNotNull(art(message("big", 1, 32, 48, 8)));
        assertNotNull(art(message("small", 1, 8, 8, 1)));

        // A picture that is not base64, not a PNG, or cut short.
        String good = message("ok", 1, 16, 16, 1).get("png").getAsString();
        byte[] raw = Base64.getDecoder().decode(good);
        byte[] damaged = raw.clone();
        for (int i = 40; i < damaged.length; i++) damaged[i] ^= 0x5A;
        String[] pictures = { "", "!!!not base64!!!", Base64.getEncoder().encodeToString("hello, this is no picture at all".getBytes()),
                good.substring(0, good.length() / 2), Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(raw, 40)),
                Base64.getEncoder().encodeToString(damaged) };
        for (String picture : pictures) {
            JsonObject o = message("ok", 1, 16, 16, 1);
            o.addProperty("png", picture);
            assertInstanceOf(RelicProtocol.Bad.class, decode(o), picture.length() > 40 ? picture.substring(0, 40) : picture);
        }
        JsonObject none = message("ok", 1, 16, 16, 1);
        none.remove("png");
        assertInstanceOf(RelicProtocol.Bad.class, decode(none));

        // Not ours at all, or too big to be anything.
        for (String junk : List.of("relic", "[\"relic\"]", "{\"t\":\"relic\"", "{\"t\":5,\"x\":\"relic\"}", "\"relic\"")) {
            assertNull(decode(junk), junk);
        }
        assertNull(RelicProtocol.decode(new byte[RelicProtocol.MAX_BYTES + 1]));
    }

    @Test
    void thePictureMustBeExactlyTheSizeTheMessageSays() throws Exception {
        int[] pixels = new int[16 * 16];
        java.util.Arrays.fill(pixels, STEEL);
        JsonObject o = message("ok", 1, 16, 16, 1);
        o.addProperty("frames", 2); // needs 16 x 32, the picture is 16 x 16
        assertInstanceOf(RelicProtocol.Bad.class, decode(o));
        o = message("ok", 1, 16, 32, 1);
        o.addProperty("png", png(32, 16, new int[32 * 16])); // right area, wrong way round
        assertInstanceOf(RelicProtocol.Bad.class, decode(o));
        o = message("ok", 1, 16, 16, 1);
        o.addProperty("png", png(17, 16, new int[17 * 16]));
        assertInstanceOf(RelicProtocol.Bad.class, decode(o));

        // A small file whose header claims an enormous picture is refused by its header alone.
        byte[] huge = Base64.getDecoder().decode(png(16, 16, pixels));
        huge[16] = 0x7F; // width, most significant byte
        huge[20] = 0x7F; // height
        o = message("ok", 1, 16, 16, 1);
        o.addProperty("png", Base64.getEncoder().encodeToString(huge));
        assertInstanceOf(RelicProtocol.Bad.class, decode(o));
    }

    @Test
    void aBrokenGlowLayerCostsOnlyTheGlow() throws Exception {
        JsonObject o = message("ok", 1, 16, 16, 2);
        int[] lit = new int[16 * 32];
        lit[16 * 16 + 3] = CYAN; // frame 2, texel 3, 0
        lit[5] = 0x40FF0000; // frame 1, texel 5, 0: a faint red wash
        o.addProperty("lit", png(16, 32, lit));
        RelicArt a = art(o);
        assertTrue(a.glows(1, 3, 0));
        assertEquals(CYAN, a.pixel(1, 3, 0));
        assertFalse(a.glows(0, 3, 0));
        assertTrue(a.glows(0, 5, 0));
        assertEquals(255, a.pixel(0, 5, 0) >>> 24, "a wash over a solid texel stays solid");

        o.addProperty("lit", png(16, 16, new int[16 * 16])); // one frame short
        a = art(o);
        assertEquals(STEEL, a.pixel(1, 3, 0));
        for (boolean g : a.glow()) assertFalse(g);
        o.addProperty("lit", "###");
        assertNotNull(art(o));
        o.addProperty("lit", "");
        assertNotNull(art(o));
    }

    @Test
    void theStoreReplacesByRevForgetsAndKeepsAtMost128() throws Exception {
        RelicStore store = new RelicStore();
        RelicArt first = art(message("blade", 1, 16, 16, 1));
        RelicStore.Change c = store.accept(new RelicProtocol.Art(first));
        assertEquals(List.of("blade"), c.fresh());
        assertTrue(c.gone().isEmpty());
        assertSame(first, store.get("blade"));

        // The same painting again changes nothing; a new rev replaces it.
        assertTrue(store.accept(new RelicProtocol.Art(art(message("blade", 1, 16, 16, 1)))).isEmpty());
        assertSame(first, store.get("blade"));
        RelicArt repainted = art(message("blade", 2, 32, 32, 4));
        c = store.accept(new RelicProtocol.Art(repainted));
        assertEquals(List.of("blade"), c.fresh());
        assertTrue(c.gone().isEmpty(), "its texture is replaced, not freed");
        assertSame(repainted, store.get("blade"));
        assertEquals(1, store.size());

        // Bad messages and strangers change nothing.
        assertTrue(store.accept(new RelicProtocol.Bad()).isEmpty());
        assertTrue(store.accept(null).isEmpty());
        assertTrue(store.accept(new RelicProtocol.Gone("nobody")).isEmpty());

        c = store.accept(decode("{\"t\":\"relic_gone\",\"id\":\"blade\"}"));
        assertEquals(List.of("blade"), c.gone());
        assertNull(store.get("blade"));
        assertEquals(0, store.size());

        // The 129th relic pushes out the oldest; one that was repainted meanwhile is no longer the oldest.
        RelicArt shape = art(message("r0", 1, 8, 8, 1));
        for (int i = 0; i < RelicStore.MAX; i++) {
            assertTrue(store.put(RelicArt.of("r" + i, 1, "r", RelicArt.Kind.TOME, 8, 8, 1, 1, shape.pixels(), null)).gone().isEmpty());
        }
        assertEquals(RelicStore.MAX, store.size());
        store.put(RelicArt.of("r0", 2, "r", RelicArt.Kind.TOME, 8, 8, 1, 1, shape.pixels(), null));
        c = store.put(RelicArt.of("one_more", 1, "r", RelicArt.Kind.TOME, 8, 8, 1, 1, shape.pixels(), null));
        assertEquals(List.of("one_more"), c.fresh());
        assertEquals(List.of("r1"), c.gone());
        assertEquals(RelicStore.MAX, store.size());
        assertNotNull(store.get("r0"));
        assertNull(store.get("r1"));

        c = store.clear();
        assertEquals(RelicStore.MAX, c.gone().size());
        assertEquals(0, store.size());
        assertTrue(store.clear().isEmpty());
    }

    @Test
    void framesLoopAtTheirPace() {
        RelicArt a = RelicArt.of("a", 1, "a", RelicArt.Kind.SPIRIT, 8, 8, 3, 5, new int[8 * 8 * 3], null);
        assertEquals(0, a.frameAt(0));
        assertEquals(0, a.frameAt(4));
        assertEquals(1, a.frameAt(5));
        assertEquals(2, a.frameAt(14));
        assertEquals(0, a.frameAt(15));
        assertEquals(2, a.frameAt(-1), "never out of range, whatever the clock says");
        int late = a.frameAt(Long.MAX_VALUE);
        assertTrue(late >= 0 && late < 3);
        assertNull(RelicArt.of("a", 1, "a", RelicArt.Kind.SPIRIT, 8, 8, 3, 5, new int[8 * 8], null), "pixels that do not fit");
        assertNull(RelicArt.of("a", 1, "a", RelicArt.Kind.SPIRIT, 64, 8, 1, 5, new int[64 * 8], null));
    }

    @Test
    void aSpriteBecomesASlabWithSidesWhereItEnds() {
        // One texel: a front, a back and four sides, all on that texel.
        List<Face> one = RelicMesh.build(painted("........", "........", "...#....", "........", "........", "........", "........", "........"), 0);
        assertEquals(6, one.size());
        Set<Side> sides = new HashSet<>();
        for (Face f : one) {
            sides.add(f.side());
            assertEquals(new Face(f.side(), 3, 2, 4, 3), f);
        }
        assertEquals(Set.of(Side.values()), sides);

        // A solid block is one front, one back and one long face along each edge.
        RelicArt full = painted("########", "########", "########", "########", "########", "########", "########", "########");
        List<Face> block = RelicMesh.build(full, 0);
        assertEquals(6, block.size());
        assertTrue(block.contains(new Face(Side.FRONT, 0, 0, 8, 8)));
        assertTrue(block.contains(new Face(Side.UP, 0, 0, 8, 1)));
        assertTrue(block.contains(new Face(Side.DOWN, 0, 7, 8, 8)));
        assertTrue(block.contains(new Face(Side.LEFT, 0, 0, 1, 8)));
        assertTrue(block.contains(new Face(Side.RIGHT, 7, 0, 8, 8)));

        // A texel on the "lit" layer is drawn like the rest, with no faces of its own; faint texels are as good as empty.
        List<Face> mixed = RelicMesh.build(painted("........", ".##*~...", "........", "........", "........", "........", "........", "........"), 0);
        assertTrue(mixed.contains(new Face(Side.FRONT, 1, 1, 4, 2)));
        assertTrue(mixed.contains(new Face(Side.BACK, 1, 1, 4, 2)));
        assertTrue(mixed.contains(new Face(Side.UP, 1, 1, 4, 2)));
        assertTrue(mixed.contains(new Face(Side.DOWN, 1, 1, 4, 2)));
        assertTrue(mixed.contains(new Face(Side.LEFT, 1, 1, 2, 2)));
        assertTrue(mixed.contains(new Face(Side.RIGHT, 3, 1, 4, 2)), "the faint texel beside it does not hide the edge");
        assertEquals(6, mixed.size());

        // Every drawn texel is under exactly one front face, and no face covers an empty one.
        for (RelicArt art : RelicSamples.all()) {
            for (int frame = 0; frame < art.frames(); frame++) {
                int[] covered = new int[art.w() * art.h()];
                List<Face> faces = RelicMesh.build(art, frame);
                assertTrue(faces.size() <= RelicMesh.MAX_FACES);
                for (Face f : faces) {
                    assertTrue(f.x0() >= 0 && f.y0() >= 0 && f.x1() <= art.w() && f.y1() <= art.h() && f.x0() < f.x1() && f.y0() < f.y1());
                    for (int y = f.y0(); y < f.y1(); y++) {
                        for (int x = f.x0(); x < f.x1(); x++) {
                            assertTrue(art.visible(frame, x, y), art.id() + " " + f);
                            if (f.side() == Side.FRONT) covered[y * art.w() + x]++;
                        }
                    }
                }
                for (int y = 0; y < art.h(); y++) {
                    for (int x = 0; x < art.w(); x++) assertEquals(art.visible(frame, x, y) ? 1 : 0, covered[y * art.w() + x]);
                }
            }
        }

        // A chequered sprite is ragged but still worth its sides...
        int[] checks = new int[32 * 48];
        for (int i = 0; i < checks.length; i++) checks[i] = (i % 32 + i / 32) % 2 == 0 ? STEEL : 0;
        assertEquals(768 * 6, RelicMesh.build(RelicArt.of("checks", 1, "c", RelicArt.Kind.TRINKET, 32, 48, 1, 1, checks, null), 0).size());
        // ...one where every texel differs in depth from all its neighbours is not: it is drawn as a flat card.
        int[] solid = new int[32 * 48];
        java.util.Arrays.fill(solid, STEEL);
        byte[] jagged = new byte[32 * 48];
        for (int i = 0; i < jagged.length; i++) jagged[i] = (byte) ((i % 32 + i / 32) % 2 == 0 ? 9 : 2);
        List<Face> card = RelicMesh.build(RelicArt.of("jagged", 1, "j", RelicArt.Kind.TRINKET, 32, 48, 1, 1, solid, null, jagged), 0);
        assertEquals(List.of(new Face(Side.FRONT, 0, 0, 32, 48), new Face(Side.BACK, 0, 0, 32, 48)), card);
    }

    @Test
    void theSlabSitsInTheItemSquareAndReadsItsOwnFrame() {
        // 8 wide, 16 tall, two frames: half a block wide in the middle of the square, a full block high.
        RelicArt tall = RelicArt.of("tall", 1, "t", RelicArt.Kind.STAFF, 8, 16, 2, 1, new int[8 * 16 * 2], null);
        assertEquals(1 / 16f, RelicMesh.texel(tall));
        float[] b = RelicMesh.bounds(tall);
        assertEquals(0.25f, b[0]);
        assertEquals(0f, b[1]);
        assertEquals(0.75f, b[3]);
        assertEquals(1f, b[4]);
        assertEquals(1 / 16f, b[5] - b[2], 1e-6, "one sixteenth of a block thick, like the game's flat items");

        // The front of the top-left texel, and the upper edge of the same texel.
        Face front = new Face(Side.FRONT, 0, 0, 1, 1);
        float[] box = RelicMesh.box(tall, front);
        assertEquals(0.25f, box[0]);
        assertEquals(15 / 16f, box[1]);
        assertEquals(RelicMesh.FRONT_Z, box[2]);
        assertEquals(0.3125f, box[3]);
        assertEquals(1f, box[4]);
        assertEquals(RelicMesh.FRONT_Z, box[5]);
        float[] up = RelicMesh.box(tall, new Face(Side.UP, 0, 0, 1, 1));
        assertEquals(1f, up[1]);
        assertEquals(1f, up[4]);
        assertEquals(RelicMesh.BACK_Z, up[2]);
        assertEquals(RelicMesh.FRONT_Z, up[5]);
        float[] right = RelicMesh.box(tall, new Face(Side.RIGHT, 7, 15, 8, 16));
        assertEquals(0.75f, right[0]);
        assertEquals(0.75f, right[3]);
        assertEquals(0f, right[1]);

        // Texture coordinates stay inside the face's own texels, in the rows of its own frame.
        for (int frame = 0; frame < 2; frame++) {
            for (float[] corner : new float[][] { { box[0], box[1] }, { box[3], box[4] }, { box[0], box[4] }, { box[3], box[1] } }) {
                float[] uv = RelicMesh.uv(tall, front, frame, corner[0], corner[1]);
                assertTrue(uv[0] > 0 && uv[0] < 1 / 8f, "u " + uv[0]);
                assertTrue(uv[1] > frame / 2f && uv[1] < frame / 2f + 1 / 32f, "v " + uv[1]);
            }
        }
        float[] low = RelicMesh.uv(tall, front, 0, box[0], box[1]), high = RelicMesh.uv(tall, front, 0, box[3], box[4]);
        assertTrue(low[0] < high[0], "left of the face is left on the texture");
        assertTrue(low[1] > high[1], "the bottom of the face is further down the texture");
        float[] edge = RelicMesh.uv(tall, new Face(Side.UP, 2, 5, 6, 6), 1, 0.4f, 0.6f);
        assertEquals((16 + 5.5f) / 32f, edge[1], 1e-6, "a side reads the middle of its row");
    }

    @Test
    void theSamplesAreWhatThePluginCouldSend() {
        List<RelicArt> samples = RelicSamples.all();
        assertEquals(6, samples.size());
        RelicStore store = new RelicStore();
        for (RelicArt a : samples) {
            assertNotNull(a, "sizes in range");
            assertTrue(a.id().matches("[a-z0-9_]{1,40}"));
            assertEquals(List.of(a.id()), store.put(a).fresh());
            boolean glows = false, drawn = false;
            for (int i = 0; i < a.pixels().length; i++) {
                glows |= a.glow()[i];
                drawn |= (a.pixels()[i] >>> 24) >= RelicArt.VISIBLE_ALPHA;
            }
            assertTrue(glows && drawn, a.id());
        }
        assertEquals(6, store.size());
        assertFalse(store.get("demo_blade").translucent());
        assertTrue(store.get("demo_orb").translucent(), "its halo is see-through");
        assertTrue(store.get("demo_orb").frames() > 1);
        assertTrue(store.get("demo_staff").h() > store.get("demo_staff").w());
        assertTrue(store.get("demo_blade").kind().handheld());

        // The older samples have no depth map; the sword of light and the shield have a form.
        assertNull(store.get("demo_blade").depth());
        assertEquals(1 / 16f, store.get("demo_orb").thickest());
        RelicArt sword = store.get("demo_lightblade"), awake = store.get("demo_lightblade__on"), shield = store.get("demo_shield");
        assertEquals(3 / 16f, sword.thickest());
        assertEquals(3 / 16f, sword.thickness(0, 15), "the pommel");
        assertEquals(2 / 16f, sword.thickness(10, 5), "the blade");
        assertTrue(sword.glows(0, 10, 5), "the blade is painted on the lit layer");
        int films = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (!sword.visible(0, x, y) || sword.thickness(x, y) > 0) continue;
                films++;
                assertTrue((sword.pixel(0, x, y) >>> 24) < 255, "the film round the blade is see-through");
            }
        }
        assertTrue(films > 10, "a film round the blade: " + films);
        assertTrue(sword.translucent());
        assertEquals("demo_lightblade", RelicProtocol.relicOf(awake.id()));
        assertEquals(6 / 16f, shield.thickest());
        assertEquals(6 / 16f, shield.thickness(7, 7));
        assertEquals(1 / 16f, shield.thickness(7, 0));
        assertTrue(shield.glows(0, 7, 7));
    }

    /** Art from a picture ('#' plain, '*' glowing, anything else empty) and a map of thickness in texels (digits). */
    private static RelicArt formed(String[] picture, String[] thickness) {
        int w = picture[0].length(), h = picture.length;
        int[] base = new int[w * h], lit = new int[w * h];
        byte[] depth = new byte[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                char c = picture[y].charAt(x);
                if (c == '#' || c == '*') base[y * w + x] = STEEL;
                if (c == '*') lit[y * w + x] = CYAN;
                depth[y * w + x] = (byte) Math.max(0, thickness[y].charAt(x) - '0');
            }
        }
        return RelicArt.of("formed", 1, "Formed", RelicArt.Kind.TRINKET, w, h, 1, 1, base, lit, depth);
    }

    @Test
    void theDepthMapIsOptionalAndABadOneCostsOnlyTheForm() throws Exception {
        // The golden message: a gem three texels thick in the middle, one at its rim, a film of light beside it.
        RelicArt gem = assertInstanceOf(RelicProtocol.Art.class, RelicProtocol.decode(golden("relic-depth"))).art();
        assertEquals("sun_gem__on", gem.id(), "a second form is just another id");
        assertEquals("sun_gem", RelicProtocol.relicOf(gem.id()));
        assertEquals("sun_gem", RelicProtocol.relicOf("sun_gem"));
        assertEquals(64, gem.depth().length);
        assertEquals(3 / 8f, gem.thickness(3, 3));
        assertEquals(1 / 8f, gem.thickness(2, 2));
        assertEquals(3 / 8f, gem.thickest());
        assertTrue(gem.visible(0, 6, 3) && gem.glows(0, 6, 3));
        assertEquals(0f, gem.thickness(6, 3), "drawn, with no thickness: a film");
        float[] bounds = RelicMesh.bounds(gem);
        assertEquals(0.5f - 1.5f / 8, bounds[2]);
        assertEquals(0.5f + 1.5f / 8, bounds[5]);

        // Without the field a relic is what it always was: one sixteenth of a block thick, whatever its size.
        JsonObject o = message("ok", 1, 32, 32, 2);
        RelicArt flat = art(o);
        assertNull(flat.depth());
        assertEquals(1 / 16f, flat.thickness(5, 5));
        assertEquals(1 / 16f, flat.thickest());

        // One byte a texel, the same for every frame, at most 12.
        byte[] depth = new byte[32 * 32];
        java.util.Arrays.fill(depth, (byte) 12);
        depth[33] = 0;
        o.addProperty("depth", Base64.getEncoder().encodeToString(depth));
        RelicArt deep = art(o);
        assertEquals(12 / 32f, deep.thickness(0, 0));
        assertEquals(0f, deep.thickness(1, 1));
        assertEquals(12 / 32f, deep.thickest());

        // Anything else is left out, and the picture is kept.
        depth[7] = 13;
        String tooThick = Base64.getEncoder().encodeToString(depth);
        String[] bad = { tooThick, Base64.getEncoder().encodeToString(new byte[32 * 32 - 1]), Base64.getEncoder().encodeToString(new byte[32 * 32 * 2]),
                Base64.getEncoder().encodeToString(new byte[] { (byte) 200 }), "***", "" };
        for (String field : bad) {
            o.addProperty("depth", field);
            RelicArt kept = art(o);
            assertNull(kept.depth(), field.length() > 20 ? field.substring(0, 20) : field);
            assertEquals(1 / 16f, kept.thickest());
            assertEquals(STEEL, kept.pixel(1, 3, 3));
        }
        o.addProperty("depth", 7);
        assertNull(art(o).depth());
        o.add("depth", new JsonObject());
        assertNull(art(o).depth());
        assertNull(RelicArt.of("a", 1, "a", RelicArt.Kind.TOME, 8, 8, 1, 1, new int[64], null, new byte[] { 1, 2, 3 }).depth());
    }

    @Test
    void thicknessMakesStepsAndAFilmIsTwoFaces() {
        String[] nothing = { "........", "........", "........", "........", "........", "........", "........", "........" };
        // A film alone: seen from the front and from behind, in the middle plane, with no sides.
        String[] one = nothing.clone();
        one[3] = "...#....";
        List<Face> film = RelicMesh.build(formed(one, new String[] { "00000000", "00000000", "00000000", "00000000", "00000000", "00000000", "00000000", "00000000" }), 0);
        assertEquals(List.of(new Face(Side.FRONT, 3, 3, 4, 4, RelicMesh.MIDDLE_Z, RelicMesh.MIDDLE_Z),
                new Face(Side.BACK, 3, 3, 4, 4, RelicMesh.MIDDLE_Z, RelicMesh.MIDDLE_Z)), film);

        // A texel three thick beside one a single texel thick (an eighth of a block each, on this 8x8 sprite).
        String[] two = nothing.clone();
        two[3] = "...##...";
        String[] depths = { "00000000", "00000000", "00000000", "00031000", "00000000", "00000000", "00000000", "00000000" };
        RelicArt stepped = formed(two, depths);
        float mid = RelicMesh.MIDDLE_Z, thick = stepped.thickness(3, 3) / 2, thin = stepped.thickness(4, 3) / 2;
        assertEquals(1.5f / 8, thick);
        assertEquals(0.5f / 8, thin);
        List<Face> faces = RelicMesh.build(stepped, 0);
        assertEquals(12, faces.size());
        assertTrue(faces.contains(new Face(Side.FRONT, 3, 3, 4, 4, mid + thick, mid + thick)));
        assertTrue(faces.contains(new Face(Side.BACK, 3, 3, 4, 4, mid - thick, mid - thick)));
        assertTrue(faces.contains(new Face(Side.FRONT, 4, 3, 5, 4, mid + thin, mid + thin)));
        assertTrue(faces.contains(new Face(Side.BACK, 4, 3, 5, 4, mid - thin, mid - thin)));
        // Towards its thinner neighbour the thick texel shows only the two steps that stand proud of it...
        assertTrue(faces.contains(new Face(Side.RIGHT, 3, 3, 4, 4, mid + thin, mid + thick)));
        assertTrue(faces.contains(new Face(Side.RIGHT, 3, 3, 4, 4, mid - thick, mid - thin)));
        // ...its other sides are whole, the thin one has no wall towards it, and its own outer sides are whole too.
        assertTrue(faces.contains(new Face(Side.LEFT, 3, 3, 4, 4, mid - thick, mid + thick)));
        assertTrue(faces.contains(new Face(Side.UP, 3, 3, 4, 4, mid - thick, mid + thick)));
        assertTrue(faces.contains(new Face(Side.DOWN, 3, 3, 4, 4, mid - thick, mid + thick)));
        assertTrue(faces.contains(new Face(Side.RIGHT, 4, 3, 5, 4, mid - thin, mid + thin)));
        assertTrue(faces.contains(new Face(Side.UP, 4, 3, 5, 4, mid - thin, mid + thin)));
        assertTrue(faces.contains(new Face(Side.DOWN, 4, 3, 5, 4, mid - thin, mid + thin)));
        for (Face f : faces) assertFalse(f.side() == Side.LEFT && f.x0() == 4, "no wall where the neighbour is thicker");
        float[] step = RelicMesh.box(stepped, new Face(Side.RIGHT, 3, 3, 4, 4, mid + thin, mid + thick));
        assertEquals(0.5f, step[0]);
        assertEquals(mid + thin, step[2]);
        assertEquals(mid + thick, step[5]);

        // Equal neighbours join: a row of four of one thickness is one front, one back and four walls.
        String[] row = nothing.clone();
        row[3] = "..####..";
        assertEquals(6, RelicMesh.build(formed(row, new String[] { "00000000", "00000000", "00000000", "00222200", "00000000", "00000000", "00000000", "00000000" }), 0).size());
        // A film beside a thick texel leaves that texel's whole side showing, and has none of its own.
        List<Face> beside = RelicMesh.build(formed(two, new String[] { "00000000", "00000000", "00000000", "00020000", "00000000", "00000000", "00000000", "00000000" }), 0);
        assertEquals(8, beside.size());
        float two2 = 1f / 8;
        assertTrue(beside.contains(new Face(Side.RIGHT, 3, 3, 4, 4, mid - two2, mid + two2)));
        assertTrue(beside.contains(new Face(Side.FRONT, 4, 3, 5, 4, mid, mid)));
    }

    /** The plugin's own painted relics, exactly as its ModRelics.message wrote them (one file each). */
    private static final List<String> PLUGIN_RELICS = List.of("skysplinter", "skysplinter__on", "dawnbreaker", "starfall_greatsword",
            "sunspire", "codex_of_ash", "crown_of_the_first_king", "heart_of_the_tide", "vial_of_starlight", "lantern_wisp", "the_watcher");

    @Test
    void everyRelicThePluginPaintsIsAccepted() throws Exception {
        RelicStore store = new RelicStore();
        for (String id : PLUGIN_RELICS) {
            byte[] bytes = golden("relics/" + id);
            JsonObject sent = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            RelicProtocol.Message m = RelicProtocol.decode(bytes);
            RelicArt a = assertInstanceOf(RelicProtocol.Art.class, m, id + " was not accepted").art();
            assertEquals(id, a.id());
            assertEquals(sent.get("id").getAsString(), a.id());
            assertEquals(sent.get("rev").getAsInt(), a.rev(), id);
            assertEquals(sent.get("name").getAsString(), a.name(), id);
            assertEquals(sent.get("kind").getAsString(), a.kind().name().toLowerCase(java.util.Locale.ROOT), id);
            assertEquals(sent.get("w").getAsInt(), a.w(), id);
            assertEquals(sent.get("h").getAsInt(), a.h(), id);
            assertEquals(8, a.frames(), id);
            assertEquals(3, a.ticks(), id);
            assertEquals(sent.get("ticks").getAsInt(), a.ticks(), id);

            // The picture is the one in the message, frame under frame; the glowing layer is there exactly when it was sent.
            int[] base = pixels(sent.get("png").getAsString());
            int[] lit = sent.has("lit") ? pixels(sent.get("lit").getAsString()) : null;
            assertEquals(a.w() * a.h() * 8, base.length, id);
            boolean anyGlow = false;
            for (int i = 0; i < base.length; i++) {
                boolean glows = lit != null && (lit[i] >>> 24) != 0;
                assertEquals(glows, a.glow()[i], id + " texel " + i);
                if (!glows) assertEquals(base[i], a.pixels()[i], id + " texel " + i);
                else if ((lit[i] >>> 24) == 255) assertEquals(lit[i], a.pixels()[i], id + " texel " + i);
                anyGlow |= glows;
            }
            assertEquals(sent.has("lit"), anyGlow, id + ": its glowing layer");

            // And painted walls for every frame, in the colours sent, each as see-through as its texel's face.
            int[] walls = pixels(sent.get("walls").getAsString());
            assertEquals(8, a.wallFrames(), id);
            assertEquals(walls.length, a.walls().length, id);
            for (int i = 0; i < walls.length; i++) {
                int row = i / (2 * a.w()), column = i % (2 * a.w());
                int frame = row / (2 * a.h()), y = row % a.h(), x = column % a.w();
                assertEquals(walls[i] & 0xFFFFFF, a.walls()[i] & 0xFFFFFF, id + " wall texel " + i);
                assertEquals(a.pixel(frame, x, y) >>> 24, a.walls()[i] >>> 24, id + " wall texel " + i);
            }

            // Every one carries a depth map now: a byte a texel, kept as it was sent.
            byte[] depth = Base64.getDecoder().decode(sent.get("depth").getAsString());
            assertEquals(a.w() * a.h(), depth.length, id);
            assertNotNull(a.depth(), id + ": its depth map was refused");
            assertTrue(java.util.Arrays.equals(depth, a.depth()), id);
            for (int y = 0; y < a.h(); y++) {
                for (int x = 0; x < a.w(); x++) assertEquals(depth[y * a.w() + x] / (float) a.w(), a.thickness(x, y), id);
            }

            // Every frame has something to draw and is drawn as a real slab, not the flat card for ragged sprites.
            for (int frame = 0; frame < a.frames(); frame++) {
                List<Face> faces = RelicMesh.build(a, frame);
                assertTrue(faces.size() > 2 && faces.size() <= RelicMesh.MAX_FACES, id + " frame " + frame + ": " + faces.size() + " faces");
            }
            assertEquals(List.of(id), store.accept(m).fresh());
        }
        assertEquals(11, store.size());
        assertTrue(store.get("lantern_wisp").translucent(), "its halo is soft");
        assertTrue(store.get("dawnbreaker").kind().handheld() && store.get("sunspire").kind().handheld());
        assertFalse(store.get("the_watcher").kind().handheld());
        assertEquals(1 / 16f, store.get("dawnbreaker").thickest(), "a 16-wide relic one texel thick is what it always was");
        assertEquals(2 / 32f, store.get("starfall_greatsword").thickest());

        // The wisp's soft halo is films: see-through texels with no thickness, drawn as a front and a back.
        RelicArt wisp = store.get("lantern_wisp");
        int films = 0;
        for (int y = 0; y < wisp.h(); y++) {
            for (int x = 0; x < wisp.w(); x++) {
                if (!wisp.visible(0, x, y) || wisp.thickness(x, y) > 0) continue;
                films++;
                assertTrue((wisp.pixel(0, x, y) >>> 24) < 255);
            }
        }
        assertTrue(films > 20, "films: " + films);
        int filmFaces = 0;
        for (Face f : RelicMesh.build(wisp, 0)) {
            if (!f.film()) continue;
            filmFaces++;
            assertTrue(f.side() == Side.FRONT || f.side() == Side.BACK, "a film is a front and a back, no sides: " + f);
        }
        assertTrue(filmFaces >= 2 && filmFaces % 2 == 0);

        // The plugin's two-form relic: a hilt asleep; awake, the same hilt with a blade, up to 5 texels thick.
        RelicArt asleep = store.get("skysplinter"), awake = store.get("skysplinter__on");
        assertEquals("skysplinter", RelicProtocol.relicOf(awake.id()));
        assertEquals(RelicProtocol.relicOf(asleep.id()), RelicProtocol.relicOf(awake.id()));
        assertEquals(asleep.kind(), awake.kind());
        assertEquals(5 / 24f, awake.thickest());
        assertEquals(5 / 24f, asleep.thickest());
        int hilt = 0, blade = 0;
        for (int frame = 0; frame < 8; frame++) {
            for (int y = 0; y < 24; y++) {
                for (int x = 0; x < 24; x++) {
                    if (asleep.visible(frame, x, y)) {
                        hilt++;
                        assertTrue(awake.visible(frame, x, y), "the hilt stays where it was: " + x + "," + y);
                        assertEquals(asleep.thickness(x, y), awake.thickness(x, y));
                    } else if (awake.visible(frame, x, y)) {
                        blade++;
                    }
                }
            }
        }
        assertTrue(hilt > 100 && blade > 100, hilt + " hilt texels, " + blade + " of the blade");

        // Unmaking a relic: the plugin says gone for the relic and for its second form, whether it had one or not.
        assertEquals(List.of("skysplinter__on"), store.accept(decode("{\"t\":\"relic_gone\",\"id\":\"skysplinter__on\"}")).gone());
        assertEquals(List.of("skysplinter"), store.accept(decode("{\"t\":\"relic_gone\",\"id\":\"skysplinter\"}")).gone());
        assertEquals(List.of("dawnbreaker"), store.accept(decode("{\"t\":\"relic_gone\",\"id\":\"dawnbreaker\"}")).gone());
        RelicProtocol.Message never = decode("{\"t\":\"relic_gone\",\"id\":\"dawnbreaker__on\"}");
        assertEquals("dawnbreaker__on", assertInstanceOf(RelicProtocol.Gone.class, never).id());
        assertTrue(store.accept(never).isEmpty(), "a form the store never had: nothing to do");
        assertTrue(store.accept(decode("{\"t\":\"relic_gone\",\"id\":\"skysplinter__on\"}")).isEmpty(), "nor one already gone");
        assertEquals(8, store.size());
        assertTrue(new RelicStore().accept(never).isEmpty());
    }

    @Test
    void paintedWallsGiveEachSideItsOwnColour() throws Exception {
        // Skysplinter awake, its golden collar: the texel at 9, 14 is a tone lighter on top, its own tone on the other sides.
        RelicArt awake = assertInstanceOf(RelicProtocol.Art.class, RelicProtocol.decode(golden("relics/skysplinter__on"))).art();
        assertEquals(24, awake.w());
        int own = 0xFFE8B94A;
        assertEquals(own, awake.pixel(0, 9, 14));
        int up = 0xFFFFE08A, down = own, left = own, right = own;
        Side[] sides = { Side.UP, Side.DOWN, Side.LEFT, Side.RIGHT };
        int[] colours = { up, down, left, right };
        for (int frame : new int[] { 0, 3, 7 }) {
            for (int i = 0; i < 4; i++) {
                Side side = sides[i];
                Face face = new Face(side, 9, 14, 10, 15, 0.4f, 0.6f);
                float[] box = RelicMesh.box(awake, face);
                // Every corner of the side reads that one texel of its own quarter, in its own frame.
                for (float[] corner : new float[][] { { box[0], box[1] }, { box[3], box[4] }, { box[0], box[4] }, { box[3], box[1] } }) {
                    float[] uv = RelicMesh.wallUv(awake, face, frame, corner[0], corner[1]);
                    int column = (int) Math.floor(uv[0] * 48), row = (int) Math.floor(uv[1] * 48 * 8);
                    assertEquals((side == Side.DOWN || side == Side.RIGHT ? 24 : 0) + 9, column, side + " column");
                    assertEquals(frame * 48 + (side == Side.LEFT || side == Side.RIGHT ? 24 : 0) + 14, row, side + " row");
                    if (frame == 0) assertEquals(colours[i], awake.walls()[row * 48 + column], side + " colour");
                }
            }
        }

        // Absent: the sides are the colour of their faces, as before.
        JsonObject o = message("ok", 1, 16, 16, 2);
        assertNull(art(o).walls());
        assertEquals(0, art(o).wallFrames());

        // One frame of walls serves every frame of the picture.
        int[] one = new int[32 * 32];
        java.util.Arrays.fill(one, 0xFF336699);
        one[0] = 0x00336699; // drawn on the face but clear in the walls: the wall is drawn all the same
        o.addProperty("walls", png(32, 32, one));
        RelicArt shared = art(o);
        assertEquals(1, shared.wallFrames());
        assertEquals(0, shared.wallFrame(1));
        assertEquals(0xFF336699, shared.walls()[0], "the wall is as see-through as its face");
        Face top = new Face(Side.UP, 3, 0, 4, 1);
        float[] uv = RelicMesh.wallUv(shared, top, 1, RelicMesh.box(shared, top)[0], RelicMesh.box(shared, top)[1]);
        assertEquals(3, (int) Math.floor(uv[0] * 32));
        assertEquals(0, (int) Math.floor(uv[1] * 32), "the second frame reads the one frame of walls there is");

        // Walls for each frame, and for fewer frames spread evenly.
        o = message("ok", 1, 16, 16, 4);
        o.addProperty("walls", png(32, 32 * 4, new int[32 * 32 * 4]));
        assertEquals(4, art(o).wallFrames());
        assertEquals(3, art(o).wallFrame(3));
        o.addProperty("walls", png(32, 32 * 2, new int[32 * 32 * 2]));
        assertEquals(2, art(o).wallFrames());
        assertEquals(0, art(o).wallFrame(1));
        assertEquals(1, art(o).wallFrame(2));
        assertEquals(0, art(o).walls()[0] & 0xFFFFFF);
        assertEquals(255, art(o).walls()[0] >>> 24, "made as see-through as the steel face");

        // Anything that does not fit costs the walls, not the picture or its form.
        byte[] depth = new byte[16 * 16];
        java.util.Arrays.fill(depth, (byte) 2);
        o.addProperty("depth", Base64.getEncoder().encodeToString(depth));
        String good = png(32, 32 * 4, new int[32 * 32 * 4]);
        byte[] damaged = Base64.getDecoder().decode(good);
        for (int i = 40; i < damaged.length; i++) damaged[i] ^= 0x5A;
        String[] bad = { png(33, 32 * 4, new int[33 * 32 * 4]), png(32, 33, new int[32 * 33]), png(32, 32 * 5, new int[32 * 32 * 5]),
                png(16, 16 * 4, new int[16 * 16 * 4]), "###", "", Base64.getEncoder().encodeToString("no picture here at all, just words".getBytes()),
                Base64.getEncoder().encodeToString(damaged), good.substring(0, 30) };
        for (String walls : bad) {
            o.addProperty("walls", walls);
            RelicArt kept = art(o);
            assertNull(kept.walls(), walls.length() > 20 ? walls.substring(0, 20) : walls);
            assertEquals(0, kept.wallFrames());
            assertEquals(2 / 16f, kept.thickness(0, 0), "the form stays");
            assertEquals(STEEL, kept.pixel(3, 2, 2));
        }
        o.addProperty("walls", 12);
        assertNull(art(o).walls());
        assertNull(RelicArt.of("a", 1, "a", RelicArt.Kind.TOME, 8, 8, 1, 1, new int[64], null, null, new int[5], 1).walls());
        assertNull(RelicArt.of("a", 1, "a", RelicArt.Kind.TOME, 8, 8, 1, 1, new int[64], null, null, new int[256 * 2], 2).walls());
    }

    /** A message's base64 PNG read on its own, as ARGB row by row. */
    private static int[] pixels(String base64) throws Exception {
        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    @Test
    void theHelloAsksForRelics() {
        ModConfig c = new ModConfig();
        assertTrue(c.features().contains("relics"));
        c.vanilla = true;
        assertFalse(c.features().contains("relics"));
    }
}
