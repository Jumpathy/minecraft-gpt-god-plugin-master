package net.bigyous.gptgodmc.god.vessel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.bigyous.gptgodmc.god.vessel.VesselSkin.Face;

/**
 * The face of each look's head as a small image, for the God Manager screen of the client mod: cut from
 * the same painted atlas the vessel wears ({@link VesselTextures}), with its eyes drawn where the body
 * has them. Pure and deterministic. Painting an atlas takes a moment the first time: call it off the
 * server thread.
 */
public final class VesselHeads {
    /** Pixels along one side (a head is 8 skin pixels at {@link VesselSkin#DENSITY} 2). */
    public static final int SIZE = 16;
    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();

    private VesselHeads() {
    }

    /** SIZE x SIZE pixels, 3 bytes each (RGB), row by row from the top left. An unknown look gives shade's. */
    public static byte[] head(String look) {
        String name = look != null && VesselLook.NAMES.contains(look) ? look : "shade";
        return CACHE.computeIfAbsent(name, VesselHeads::paint).clone();
    }

    private static byte[] paint(String look) {
        VesselTextures.Atlas atlas = VesselTextures.atlas(look);
        byte[] out = new byte[SIZE * SIZE * 3];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                put(out, x, y, atlas.sample("head", Face.SOUTH, (x + 0.5) / SIZE, (y + 0.5) / SIZE, 0));
            }
        }
        // The eyes are their own boxes on the body (2 x 1 skin pixels, 2 pixels either side of the middle,
        // 3.5 above the neck): draw their front faces where a viewer sees them.
        for (int eye = 0; eye < 2; eye++) {
            int left = eye == 0 ? 2 : 10;
            for (int j = 0; j < 2; j++) {
                for (int i = 0; i < 4; i++) {
                    put(out, left + i, 8 + j, atlas.sample("eye", Face.SOUTH, (i + 0.5) / 4, (j + 0.5) / 2, 0));
                }
            }
        }
        return out;
    }

    private static void put(byte[] out, int x, int y, int rgb) {
        int i = (y * SIZE + x) * 3;
        out[i] = (byte) (rgb >> 16);
        out[i + 1] = (byte) (rgb >> 8);
        out[i + 2] = (byte) rgb;
    }
}
