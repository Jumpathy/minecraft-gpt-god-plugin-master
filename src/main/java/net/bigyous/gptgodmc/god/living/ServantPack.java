package net.bigyous.gptgodmc.god.living;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import net.bigyous.gptgodmc.god.living.ServantSkin.Def;
import net.bigyous.gptgodmc.god.living.ServantSkin.Layout;
import net.bigyous.gptgodmc.god.living.ServantSkin.Region;
import net.bigyous.gptgodmc.god.living.ServantSkin.Shape;
import net.bigyous.gptgodmc.god.vessel.VesselPack;
import net.bigyous.gptgodmc.god.vessel.VesselSkin.Face;
import net.bigyous.gptgodmc.god.vessel.VesselSkin.Rect;

/**
 * The servants' part of the god's resource pack: for every kind an animated atlas
 * ({@link ServantTextures}) and for every piece an item (gptgodmc:servant/&lt;kind&gt;/&lt;piece&gt;) whose
 * model is one box, or one flat square shown from both sides, mapped onto that atlas
 * ({@link ServantSkin}). {@link VesselPack} adds these files to the pack it serves. Pure and
 * deterministic, so the pack's hash does not change between starts.
 */
public final class ServantPack {
    private ServantPack() {
    }

    /** The item_model id of a servant's piece, e.g. gptgodmc:servant/angel/wing_out. */
    public static String modelId(ServantKind kind, String key) {
        return VesselPack.NAMESPACE + ":" + path(kind, key);
    }

    static String path(ServantKind kind, String key) {
        return "servant/" + kind.id + "/" + key;
    }

    /** Every file this adds to the pack, by path. */
    public static Map<String, byte[]> files() {
        Map<String, byte[]> out = new LinkedHashMap<>();
        String ns = VesselPack.NAMESPACE;
        for (ServantKind kind : ServantKind.values()) {
            ServantTextures.Atlas atlas = ServantTextures.atlas(kind);
            Layout layout = atlas.layout();
            String tex = "assets/" + ns + "/textures/item/servant/" + kind.id + ".png";
            out.put(tex, png(strip(atlas)));
            out.put(tex + ".mcmeta", utf8(animation(layout)));
            for (Region region : layout.regions().values()) {
                String p = path(kind, region.def().key());
                out.put("assets/" + ns + "/items/" + p + ".json", utf8(item(p)));
                out.put("assets/" + ns + "/models/item/" + p + ".json", utf8(model(kind, region, layout)));
            }
        }
        return out;
    }

    static String item(String path) {
        return """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "%s:item/%s"
                  }
                }
                """.formatted(VesselPack.NAMESPACE, path);
    }

    static String animation(Layout layout) {
        return """
                {
                  "animation": {
                    "frametime": %d,
                    "interpolate": true,
                    "width": %d,
                    "height": %d
                  }
                }
                """.formatted(ServantSkin.FRAME_TICKS, layout.width(), layout.height());
    }

    /**
     * A box with each face on its rectangle of the atlas, or a flat square in the middle of the model
     * showing its picture on both sides (mirrored behind, so it is the same picture in the world from
     * either side). The displays that wear them are drawn at full brightness, whatever the light.
     */
    static String model(ServantKind kind, Region region, Layout layout) {
        Def def = region.def();
        String texture = VesselPack.NAMESPACE + ":item/servant/" + kind.id;
        StringBuilder faces = new StringBuilder();
        String from, to;
        if (def.shape() == Shape.BOX) {
            from = "[0, 0, 0]";
            to = "[16, 16, 16]";
            for (Face f : Face.values()) {
                Rect r = region.face(f);
                if (faces.length() > 0) faces.append(",\n");
                faces.append(face(f.id(), uv(r.x(), layout.width()), uv(r.y(), layout.height()),
                        uv(r.x() + r.w(), layout.width()), uv(r.y() + r.h(), layout.height())));
            }
        } else {
            from = "[0, 0, 8]";
            to = "[16, 16, 8]";
            Rect r = region.face(Face.SOUTH);
            String x0 = uv(r.x(), layout.width()), x1 = uv(r.x() + r.w(), layout.width());
            String y0 = uv(r.y(), layout.height()), y1 = uv(r.y() + r.h(), layout.height());
            faces.append(face("south", x0, y0, x1, y1)).append(",\n").append(face("north", x1, y0, x0, y1));
        }
        return """
                {
                  "textures": { "skin": "%s", "particle": "%s" },
                  "elements": [
                    {
                      "from": %s,
                      "to": %s,
                      "shade": %s,
                      "faces": {
                %s
                      }
                    }
                  ]
                }
                """.formatted(texture, texture, from, to, def.shape() == Shape.BOX, faces);
    }

    private static String face(String id, String x0, String y0, String x1, String y1) {
        return "        \"" + id + "\": { \"uv\": [" + x0 + ", " + y0 + ", " + x1 + ", " + y1 + "], \"texture\": \"#skin\" }";
    }

    private static String uv(int texel, int size) {
        String s = String.format(Locale.ROOT, "%.5f", texel * 16.0 / size).replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    /** The frames stacked top to bottom, as Minecraft reads an animated texture. */
    static BufferedImage strip(ServantTextures.Atlas atlas) {
        int w = atlas.layout().width(), h = atlas.layout().height();
        BufferedImage out = new BufferedImage(w, h * atlas.frames().length, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        for (int i = 0; i < atlas.frames().length; i++) g.drawImage(atlas.frames()[i], 0, i * h, null);
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
