package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.god.DeathPieces.Kind;
import net.bigyous.gptgodmc.god.DeathPieces.Piece;
import net.bigyous.gptgodmc.god.DeathPieces.Pose;

/** The solid pieces of a god's death: counts, motion, the pack's art, and where displays end up. */
class DeathPiecesTest {
    @Test
    void everyDeathHasItsOwnPiecesWithinLimits() {
        for (DeathShow.Variant v : DeathShow.Variant.values()) {
            List<Piece> pieces = DeathPieces.build(v, 2.7, 0.5, 0.5, DeathPieces.FLAT);
            int tiles = 0;
            for (Piece p : pieces) {
                if (p.look().startsWith("minecraft:grass_block")) tiles++;
                assertTrue(p.from() >= 0 && p.to() <= DeathShow.FRAMES && p.from() < p.to(), p.look());
                for (int f = p.from(); f < p.to(); f++) {
                    Pose pose = p.at(f);
                    assertTrue(Double.isFinite(pose.x() + pose.y() + pose.z() + pose.sx() + pose.yaw()), p.look());
                }
                if (p.kind() == Kind.SKIN) assertTrue(DeathArt.keys().contains(p.look()), p.look());
            }
            assertTrue(tiles > 40 && tiles <= DeathPieces.MAX_TILES, v.id() + " heaves " + tiles + " tiles");
            int peak = DeathPieces.peak(pieces);
            assertTrue(peak <= 260, v.id() + " peak " + peak);
            assertTrue(pieces.stream().anyMatch(p -> p.look().startsWith("beam_")), "pillars of light");
            assertFalse(pieces.stream().anyMatch(p -> p.look().contains("end_rod")), "no end rods");
        }
        // Each death's signature pieces.
        assertTrue(has(DeathShow.Variant.SUPERNOVA, "corona") && has(DeathShow.Variant.SUPERNOVA, "froglight"));
        assertTrue(has(DeathShow.Variant.UNRAVELING, "sigil_"));
        assertTrue(has(DeathShow.Variant.SHATTERING, "shard_"));
        assertTrue(has(DeathShow.Variant.ABYSSAL, "disc") && has(DeathShow.Variant.ABYSSAL, "obsidian"));
    }

    private static boolean has(DeathShow.Variant v, String look) {
        return DeathPieces.build(v, 2.7, 0, 0, DeathPieces.FLAT).stream().anyMatch(p -> p.look().contains(look));
    }

    @Test
    void noGroundMeansNoCopiedBlocks() {
        List<Piece> air = DeathPieces.build(DeathShow.Variant.ABYSSAL, 2.7, 0, 0, (dx, dz) -> null);
        assertFalse(air.stream().anyMatch(p -> p.look().startsWith("minecraft:grass")));
        assertFalse(air.isEmpty(), "the rest still plays");
    }

    @Test
    void theGroundRisesAsEachWavePasses() {
        double reach = DeathShow.reach(2.7);
        assertEquals(0, DeathPieces.lift(6, reach, DeathShow.WAVES[0] - 2), 1e-9, "still before the first wave");
        double best = 0;
        for (int f = DeathShow.WAVES[0]; f < DeathShow.WAVES[1]; f++) best = Math.max(best, DeathPieces.lift(6, reach, f));
        assertTrue(best > 0.4, "it heaves up " + best);
        assertEquals(0, DeathPieces.lift(6, reach, DeathShow.BURST + 4), 1e-9, "settled after the last wave");
        assertTrue(DeathPieces.lift(3, reach, 27) >= 0);
    }

    @Test
    void shardsFallAndLie() {
        double[] flying = DeathPieces.shardPath(0, 2, 0, 0.3, 0.2, 0, 3);
        assertTrue(flying[1] > 0.03);
        double[] landed = DeathPieces.shardPath(0, 2, 0, 0.3, 0.2, 0, 60);
        assertEquals(0.03, landed[1], 1e-9);
        assertArrayEquals(landed, DeathPieces.shardPath(0, 2, 0, 0.3, 0.2, 0, 80), 1e-9, "it stays put once landed");
    }

    @Test
    void displaysAreCenteredOnTheirPose() {
        Transformation t = DeathStage.transform(new Pose(1, 2, 3, 2, 2, 2, 0, 0, 0), Kind.BLOCK);
        assertEquals(new Vector3f(0, 1, 2), t.getTranslation(), "a block's corner sits half its size back");
        Transformation turned = DeathStage.transform(new Pose(0, 0, 0, 1, 1, 1, Math.PI / 2, 0, 0), Kind.BLOCK);
        Vector3f mid = turned.getLeftRotation().transform(new Vector3f(0.5f, 0.5f, 0.5f)).add(turned.getTranslation());
        assertEquals(0, mid.length(), 1e-5, "turned, its middle stays on the pose");
        Transformation item = DeathStage.transform(new Pose(1, 2, 3, 1, 1, 1, 0, 0, 0), Kind.SKIN);
        assertEquals(new Vector3f(1, 2, 3), item.getTranslation());
        Transformation gone = DeathStage.transform(new Pose(0, 0, 0, 0, 0, 0, 0, 0, 0), Kind.SKIN);
        assertTrue(gone.getScale().x() > 0 && gone.getScale().x() < 1e-3, "hidden, never zero");
    }

    @Test
    void theArtIsPackedAndDrawn() throws Exception {
        Map<String, byte[]> files = DeathArt.files();
        assertEquals(DeathArt.keys().size() * 3, files.size());
        for (String key : DeathArt.keys()) {
            assertTrue(files.containsKey("assets/gptgodmc/items/deathshow/" + key + ".json"), key);
            String model = new String(files.get("assets/gptgodmc/models/item/deathshow/" + key + ".json"));
            assertTrue(model.contains("gptgodmc:item/deathshow/" + key), key);
        }
        assertTrue(files.containsKey("assets/gptgodmc/items/deathshow/corona.json"), "DeathStage checks for this");
        assertArrayEquals(files.get("assets/gptgodmc/textures/item/deathshow/disc.png"),
                DeathArt.files().get("assets/gptgodmc/textures/item/deathshow/disc.png"), "deterministic");

        // A sheet of every texture on a dark checker, at 4x.
        List<String> keys = DeathArt.keys();
        int cell = 64 * 3 + 16;
        BufferedImage img = new BufferedImage(cell * 6, (cell + 18) * ((keys.size() + 5) / 6), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(14, 11, 22));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < keys.size(); i++) {
            BufferedImage t = DeathArt.texture(keys.get(i));
            int ox = (i % 6) * cell + 8, oy = (i / 6) * (cell + 18) + 22;
            int sz = 64 * 3;
            for (int y = 0; y < sz; y++) {
                for (int x = 0; x < sz; x++) {
                    int c = t.getRGB(x * t.getWidth() / sz, y * t.getHeight() / sz);
                    int a = c >>> 24;
                    int bg = ((x / 12) + (y / 12)) % 2 == 0 ? 0x1C1826 : 0x241F30;
                    int r = (((c >> 16) & 255) * a + ((bg >> 16) & 255) * (255 - a)) / 255;
                    int gg = (((c >> 8) & 255) * a + ((bg >> 8) & 255) * (255 - a)) / 255;
                    int b = ((c & 255) * a + (bg & 255) * (255 - a)) / 255;
                    img.setRGB(ox + x, oy + y, (r << 16) | (gg << 8) | b);
                }
            }
            g.setColor(new Color(220, 210, 240));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.drawString(keys.get(i), ox, oy - 6);
        }
        g.dispose();
        File out = new File("build/test-renders");
        out.mkdirs();
        ImageIO.write(img, "png", new File(out, "deathshow-art.png"));
    }
}
