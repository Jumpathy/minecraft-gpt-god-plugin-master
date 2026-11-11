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

/**
 * The true form's part of the god's resource pack: for every {@link TrueFormTextures} key an item
 * (gptgodmc:trueform/&lt;key&gt;) whose model is one flat, self-lit square showing the texture on both
 * sides (mirrored on the back, so it reads the same from either side, whichever way the client turns
 * item displays). {@link VesselPack} adds these files to the pack it serves.
 */
public final class TrueFormPack {
    private TrueFormPack() {
    }

    /** The item_model id for a texture key, e.g. gptgodmc:trueform/eye_open. */
    public static String modelId(String key) {
        return VesselPack.NAMESPACE + ":trueform/" + key;
    }

    /** Every file this adds to the pack, by path. */
    public static Map<String, byte[]> files() {
        Map<String, byte[]> out = new LinkedHashMap<>();
        String ns = VesselPack.NAMESPACE;
        for (String key : TrueFormTextures.KEYS) {
            BufferedImage[] frames = TrueFormTextures.frames(key);
            String tex = "assets/" + ns + "/textures/item/trueform/" + key + ".png";
            out.put(tex, png(strip(frames)));
            if (frames.length > 1) {
                out.put(tex + ".mcmeta", utf8(animation(frames[0].getWidth(), frames[0].getHeight())));
            }
            out.put("assets/" + ns + "/items/trueform/" + key + ".json", utf8(item(key)));
            out.put("assets/" + ns + "/models/item/trueform/" + key + ".json", utf8(model(key)));
        }
        for (String key : TrueFormTextures.BOXES) {
            BufferedImage[] frames = TrueFormTextures.frames(key);
            String tex = "assets/" + ns + "/textures/item/trueform/" + key + ".png";
            out.put(tex, png(strip(frames)));
            if (frames.length > 1) {
                out.put(tex + ".mcmeta", utf8(animation(frames[0].getWidth(), frames[0].getHeight())));
            }
            out.put("assets/" + ns + "/items/trueform/" + key + ".json", utf8(item(key)));
            out.put("assets/" + ns + "/models/item/trueform/" + key + ".json", utf8(boxModel(key)));
        }
        return out;
    }

    static String item(String key) {
        return """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "%s:item/trueform/%s"
                  }
                }
                """.formatted(VesselPack.NAMESPACE, key);
    }

    static String model(String key) {
        String texture = VesselPack.NAMESPACE + ":item/trueform/" + key;
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

    /** A full, self-lit box wearing the texture on every side (stretched to whatever shape it is scaled to). */
    static String boxModel(String key) {
        String texture = VesselPack.NAMESPACE + ":item/trueform/" + key;
        StringBuilder faces = new StringBuilder();
        for (String f : new String[] { "north", "south", "east", "west", "up", "down" }) {
            if (faces.length() > 0) faces.append(",\n");
            faces.append("        \"").append(f).append("\": { \"uv\": [0, 0, 16, 16], \"texture\": \"#face\" }");
        }
        return """
                {
                  "textures": { "face": "%s", "particle": "%s" },
                  "elements": [
                    {
                      "from": [0, 0, 0],
                      "to": [16, 16, 16],
                      "shade": %s,
                      "light_emission": 15,
                      "faces": {
                %s
                      }
                    }
                  ]
                }
                """.formatted(texture, texture, !key.equals("light"), faces);
    }

    static String animation(int w, int h) {
        return """
                {
                  "animation": {
                    "frametime": %d,
                    "interpolate": true,
                    "width": %d,
                    "height": %d
                  }
                }
                """.formatted(TrueFormTextures.FEATHER_FRAME_TICKS, w, h);
    }

    private static BufferedImage strip(BufferedImage[] frames) {
        if (frames.length == 1) return frames[0];
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
