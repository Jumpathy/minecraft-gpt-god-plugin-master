package net.bigyous.gptgodmc.god.vessel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.god.vessel.Rift.Palette;

class RiftTest {
    @Test
    void theTimelineRunsInOrder() {
        assertTrue(0 < Rift.CRACK && Rift.CRACK < Rift.OPEN && Rift.OPEN < Rift.CLIMAX);
        assertTrue(Rift.CLIMAX + Rift.STEP_DELAY < Rift.SEAL && Rift.SEAL < Rift.SHUT && Rift.SHUT < Rift.LENGTH);
    }

    @Test
    void aSeamGrowsTearsOpenHoldsAndShuts() {
        assertEquals(0, Rift.open(-1)[0]);
        assertEquals(0, Rift.open(Rift.SHUT)[1]);
        // While it cracks: growing in height, still a thin seam.
        assertTrue(Rift.open(2)[0] < Rift.open(Rift.CRACK - 1)[0]);
        assertTrue(Rift.open(Rift.CRACK - 1)[1] < 0.1);
        // Open: full height, about full width, never far past it.
        for (int t = Rift.OPEN; t < Rift.SEAL; t++) {
            double[] o = Rift.open(t);
            assertEquals(1, o[0], 1e-9);
            assertTrue(o[1] > 0.95 && o[1] < 1.08, "open at " + t + ": " + o[1]);
        }
        for (int t = Rift.CRACK; t < Rift.OPEN; t++) assertTrue(Rift.open(t)[1] < 1.1, "overshoot at " + t);
        // Shutting: first across, then down to a point.
        double[] mid = Rift.open((Rift.SEAL + Rift.SHUT) / 2.0), last = Rift.open(Rift.SHUT - 0.5);
        assertTrue(mid[1] < 0.5 && mid[0] > 0.9, "closes across first");
        assertTrue(last[0] < 0.2, "then shrinks to a point");
    }

    @Test
    void theOutlineIsTornButClosedAtTheTips() {
        long seed = RiftTextures.SEED;
        assertEquals(0, Rift.shape(0, seed)[0]);
        assertEquals(0, Rift.shape(1, seed)[1]);
        double widest = 0, lastLeft = 0, lastRight = 0;
        int turnsLeft = 0, turnsRight = 0, unlike = 0;
        double dl = 0, dr = 0;
        for (int i = 1; i < 400; i++) {
            double[] s = Rift.shape(i / 400.0, seed);
            assertTrue(s[0] <= s[1] && s[1] - s[0] <= 2, "edges in order and in range");
            assertTrue(Math.abs((s[0] + s[1]) / 2) < 0.2, "the middle wanders only a little");
            widest = Math.max(widest, s[1] - s[0]);
            // Count where each edge turns (a torn zig-zag turns often).
            double nl = s[0] - lastLeft, nr = s[1] - lastRight;
            if (i > 1 && Math.signum(nl) != Math.signum(dl)) turnsLeft++;
            if (i > 1 && Math.signum(nr) != Math.signum(dr)) turnsRight++;
            if (i > 1 && Math.signum(-nl) != Math.signum(nr)) unlike++;
            dl = nl;
            dr = nr;
            lastLeft = s[0];
            lastRight = s[1];
        }
        assertTrue(widest > 1.4, "it opens wide in the middle: " + widest);
        assertTrue(turnsLeft > 10 && turnsRight > 10, "torn, not smooth: " + turnsLeft + ", " + turnsRight);
        assertTrue(unlike > 40, "the two sides do not mirror each other: " + unlike);
    }

    @Test
    void itIsSizedForTheBody() {
        for (double body : new double[] { 1.8, 2.7, 5.4, 10.8 }) {
            assertTrue(Rift.height(body) > body * 1.3, "taller than the body");
            assertTrue(Rift.width(body) > body * 0.55, "wide enough for its shoulders");
            assertTrue(Rift.bottom(body) < 0, "open at its feet");
            double top = Rift.bottom(body) + Rift.height(body);
            assertTrue(top > body, "over its head");
            assertTrue(Rift.stepBack(body) >= 1.2 && Rift.stepBack(body) <= 4.5);
        }
        List<Rift.Row> rows = Rift.rim(2.7, 60, RiftTextures.SEED, 14);
        assertEquals(14, rows.size());
        for (Rift.Row r : rows) assertTrue(r.left() <= r.right());
        assertTrue(Rift.rim(2.7, Rift.SHUT, RiftTextures.SEED, 14).isEmpty());
    }

    @Test
    void eachGodTearsInItsOwnLight() {
        assertEquals(Palette.VIOLET, Palette.of(VesselLook.shade().outline().asRGB()));
        assertEquals(Palette.VIOLET, Palette.of(VesselLook.voidLook().outline().asRGB()));
        assertEquals(Palette.GOLD, Palette.of(VesselLook.radiant().outline().asRGB()));
        assertEquals(Palette.EMBER, Palette.of(VesselLook.infernal().outline().asRGB()));
        assertEquals(Palette.TIDE, Palette.of(VesselLook.thunder().outline().asRGB()));
        assertEquals(Palette.VIOLET, Palette.of(0x808080), "grey tears in violet");
        for (String name : VesselLook.NAMES) assertNotNull(Palette.of(VesselLook.byName(name).outline().asRGB()), name);
    }

    @Test
    void theModSceneIsValidAndFacesTheWayOut() {
        for (Palette p : Palette.values()) {
            for (double body : new double[] { 1.8, 2.7, 10.8 }) {
                JsonArray layers = RiftScenes.arrival(p, body, 135);
                JsonObject rift = null;
                for (JsonElement e : layers) {
                    if (e.getAsJsonObject().get("type").getAsString().equals("rift")) rift = e.getAsJsonObject();
                }
                assertNotNull(rift, "has a rift");
                assertEquals(135, rift.get("yaw").getAsDouble(), 1e-6);
                assertEquals(0, rift.get("spin").getAsDouble(), 1e-6, "it does not turn");
                assertTrue(rift.get("height").getAsDouble() <= 128, "the mod's limit");
                // The last ring fades a little after it shuts.
                assertTrue(net.bigyous.gptgodmc.god.FxScenes.length(layers) <= Rift.LENGTH + 10);
                for (JsonElement e : layers) {
                    JsonObject l = e.getAsJsonObject();
                    if (l.get("type").getAsString().equals("sky")) throw new AssertionError("no darkened sky");
                }
            }
        }
    }

    /** It stands still where it opened, facing the way the god walks: it never turns toward viewers. */
    @Test
    void theRiftStandsStillFacingTheWayOut() {
        for (JsonArray layers : List.of(RiftScenes.arrival(Palette.GOLD, 2.7, 45),
                RiftScenes.departure(Palette.EMBER, 2.7, 45, Rift.departure(20)))) {
            for (JsonElement e : layers) {
                JsonObject l = e.getAsJsonObject();
                if (!l.get("type").getAsString().equals("rift")) continue;
                assertEquals(45, l.get("yaw").getAsDouble(), 1e-6);
                assertEquals(0, l.get("spin").getAsDouble(), 1e-6);
                assertTrue(!l.has("face"), "it does not turn toward the camera");
            }
        }
    }

    @Test
    void leavingOpensWalksInAndSeals() {
        for (int walk : new int[] { 0, 22, 60, 500 }) {
            Rift.Timeline t = Rift.departure(walk);
            assertTrue(t.crack() < t.open() && t.open() <= Rift.DEPART_WALK + 4 && t.climax() >= Rift.DEPART_WALK);
            assertTrue(t.climax() < t.seal() && t.seal() < t.shut() && t.shut() < t.length());
            assertTrue(t.length() < 150, "it does not hang about: " + t.length());
            assertTrue(t.open(t.climax())[1] > 0.95, "wide open when the body is taken");
            assertEquals(0, t.open(t.shut())[0]);
        }
        assertTrue(Rift.departure(40).climax() > Rift.departure(10).climax(), "a longer walk is waited for");
        JsonArray scene = RiftScenes.departure(Palette.VIOLET, 2.7, 90, Rift.departure(22));
        assertTrue(net.bigyous.gptgodmc.god.FxScenes.length(scene) <= Rift.departure(22).length() + 10);
    }

    @Test
    void thePackHasAnAnimatedPicturePerPalette() throws IOException {
        Map<String, byte[]> files = RiftPack.files();
        for (Palette p : Palette.values()) {
            String tex = "assets/gptgodmc/textures/item/rift/" + p.id() + ".png";
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(files.get(tex)));
            assertEquals(RiftTextures.W, img.getWidth());
            assertEquals(RiftTextures.H * RiftTextures.FRAMES, img.getHeight());
            assertTrue(new String(files.get(tex + ".mcmeta"), StandardCharsets.UTF_8).contains("\"frametime\""));
            JsonObject model = JsonParser.parseString(new String(
                    files.get("assets/gptgodmc/models/item/rift/" + p.id() + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(15, model.getAsJsonArray("elements").get(0).getAsJsonObject().get("light_emission").getAsInt());
            assertTrue(files.containsKey("assets/gptgodmc/items/rift/" + p.id() + ".json"));
            assertEquals("gptgodmc:rift/" + p.id(), RiftPack.modelId(p));
        }
        assertTrue(VesselPack.files().keySet().containsAll(files.keySet()), "the vessel pack carries the rifts");
    }

    @Test
    void thePictureIsADarkTearRingedInLight() {
        BufferedImage f = RiftTextures.frames(Palette.VIOLET)[0];
        int corner = f.getRGB(0, 0);
        assertEquals(0, corner >>> 24, "outside the glow is clear");
        int middle = f.getRGB(RiftTextures.W / 2 + 6, RiftTextures.H / 2 + 14);
        assertEquals(255, middle >>> 24, "the far side is solid, so the body is hidden behind it");
        assertTrue(luma(middle) < 140, "and dark: " + Integer.toHexString(middle));
        // Somewhere along the middle row the edge is near white.
        int brightest = 0;
        for (int x = 0; x < RiftTextures.W; x++) brightest = Math.max(brightest, luma(f.getRGB(x, RiftTextures.H / 2)));
        assertTrue(brightest > 220, "a white-hot edge");
        // Frames differ (it is animated) but are painted the same every time (stable pack hash).
        assertTrue(f.getRGB(40, 40) != RiftTextures.frames(Palette.VIOLET)[3].getRGB(40, 40)
                || !java.util.Arrays.equals(raster(f), raster(RiftTextures.frames(Palette.VIOLET)[3])));
        assertTrue(java.util.Arrays.equals(raster(RiftTextures.paint(Palette.GOLD, 2)), raster(RiftTextures.paint(Palette.GOLD, 2))));
    }

    private static int[] raster(BufferedImage img) {
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }

    private static int luma(int argb) {
        return (int) (0.3 * ((argb >> 16) & 255) + 0.59 * ((argb >> 8) & 255) + 0.11 * (argb & 255));
    }
}
