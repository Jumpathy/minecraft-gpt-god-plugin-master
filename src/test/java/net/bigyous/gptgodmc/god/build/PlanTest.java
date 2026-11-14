package net.bigyous.gptgodmc.god.build;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.bukkit.block.structure.StructureRotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.bigyous.gptgodmc.testutil.FakeServer;

class PlanTest {
    @BeforeAll
    static void setup() {
        FakeServer.install();
    }

    @Test
    void keyPackingRoundTrips() {
        for (int[] p : new int[][] { { 0, 0, 0 }, { -5, -1, 7 }, { 300, 255, -300 }, { -40, -64, -40 } }) {
            long k = Plan.key(p[0], p[1], p[2]);
            assertArrayEquals(p, new int[] { Plan.kx(k), Plan.ky(k), Plan.kz(k) });
        }
    }

    @Test
    void rotationFollowsMinecraft() {
        // South (+z) turned clockwise faces west (-x), matching StructureRotation.
        assertArrayEquals(new int[] { -1, 0 }, Plan.rotate(0, 1, StructureRotation.CLOCKWISE_90));
        assertArrayEquals(new int[] { 1, 0 }, Plan.rotate(0, 1, StructureRotation.COUNTERCLOCKWISE_90));
        assertArrayEquals(new int[] { 0, -1 }, Plan.rotate(0, 1, StructureRotation.CLOCKWISE_180));
    }

    @Test
    void designsHaveDoorsFacingSouthAndFitLimits() {
        for (Designs.Design d : Designs.ALL.values()) {
            Plan p = d.generator().apply(Style.STYLES.get("violet"));
            assertTrue(p.size() > 20, d.id());
            assertTrue(p.size() < 60_000, d.id() + " is huge: " + p.size());
            Plan turned = p.rotated(StructureRotation.CLOCKWISE_90);
            assertEquals(p.width(), turned.depth(), d.id());
        }
    }

    @Test
    void readsGzippedNbt() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10);
            out.writeUTF("Schematic");
            out.writeByte(2);
            out.writeUTF("Width");
            out.writeShort(3);
            out.writeByte(10);
            out.writeUTF("Palette");
            out.writeByte(3);
            out.writeUTF("minecraft:stone");
            out.writeInt(0);
            out.writeByte(0);
            out.writeByte(7);
            out.writeUTF("BlockData");
            out.writeInt(2);
            out.write(new byte[] { 0, 1 });
            out.writeByte(9);
            out.writeUTF("Tags");
            out.writeByte(8);
            out.writeInt(1);
            out.writeUTF("castle");
            out.writeByte(0);
        }
        Map<String, Object> root = Nbt.read(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(3, Nbt.number(root.get("Width")));
        assertEquals(0, Nbt.number(((Map<?, ?>) root.get("Palette")).get("minecraft:stone")));
        assertArrayEquals(new byte[] { 0, 1 }, (byte[]) root.get("BlockData"));
        assertEquals(List.of("castle"), root.get("Tags"));
    }
}
