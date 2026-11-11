package net.bigyous.gptgodmc.god.vessel;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import net.bigyous.gptgodmc.god.vessel.Rift.Palette;

/**
 * The rift's part of the god's resource pack: per {@link Palette} an item (gptgodmc:rift/&lt;palette&gt;)
 * whose model is one flat, self-lit plane wearing the animated picture from {@link RiftTextures} on both
 * sides (mirrored on the back, so it reads the same from either side). The plane is a block square;
 * {@link RiftShow} stretches it to the rift's size. {@link VesselPack} adds these files to the pack.
 */
public final class RiftPack {
    private RiftPack() {
    }

    /** The item_model id for a palette, e.g. gptgodmc:rift/violet. */
    public static String modelId(Palette p) {
        return VesselPack.NAMESPACE + ":rift/" + p.id();
    }

    public static Map<String, byte[]> files() {
        Map<String, byte[]> out = new LinkedHashMap<>();
        String ns = VesselPack.NAMESPACE;
        for (Palette p : Palette.values()) {
            String tex = "assets/" + ns + "/textures/item/rift/" + p.id() + ".png";
            out.put(tex, png(strip(RiftTextures.frames(p))));
            out.put(tex + ".mcmeta", utf8(animation()));
            out.put("assets/" + ns + "/items/rift/" + p.id() + ".json", utf8(item(p)));
            out.put("assets/" + ns + "/models/item/rift/" + p.id() + ".json", utf8(model(p)));
        }
        return out;
    }

    static String item(Palette p) {
        return """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "%s:item/rift/%s"
                  }
                }
                """.formatted(VesselPack.NAMESPACE, p.id());
    }

    static String model(Palette p) {
        String texture = VesselPack.NAMESPACE + ":item/rift/" + p.id();
        return """
                {
                  "textures": { "face": "%s", "particle": "%s" },
                  "elements": [
                    {
                      "from": [0, 0, 8],
                      "to": [16, 16, 8],
                      "shade": false,
                      "light_emission": 15,
                      "faces": {
                        "south": { "uv": [0, 0, 16, 16], "texture": "#face" },
                        "north": { "uv": [16, 0, 0, 16], "texture": "#face" }
                      }
                    }
                  ]
                }
                """.formatted(texture, texture);
    }

    static String animation() {
        return """
                {
                  "animation": {
                    "frametime": %d,
                    "interpolate": true,
                    "width": %d,
                    "height": %d
                  }
                }
                """.formatted(RiftTextures.FRAME_TICKS, RiftTextures.W, RiftTextures.H);
    }

    static BufferedImage strip(BufferedImage[] frames) {
        int w = frames[0].getWidth(), h = frames[0].getHeight();
        BufferedImage out = new BufferedImage(w, h * frames.length, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        for (int i = 0; i < frames.length; i++) g.drawImage(frames[i], 0, i * h, null);
        g.dispose();
        return out;
    }

    private static byte[] png(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
