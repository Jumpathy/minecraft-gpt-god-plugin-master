package net.bigyous.gptgodmc.god.vessel;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import net.bigyous.gptgodmc.god.vessel.VesselSkin.Face;
import net.bigyous.gptgodmc.god.vessel.VesselSkin.Layout;
import net.bigyous.gptgodmc.god.vessel.VesselSkin.Piece;
import net.bigyous.gptgodmc.god.vessel.VesselSkin.Rect;

/**
 * The resource pack that gives the vessel its own skin: for every look an animated texture atlas
 * ({@link VesselTextures}), and for every body part a one-box item model whose faces map onto that
 * atlas ({@link VesselSkin}). Body parts are ItemDisplays whose item_model component names these
 * (gptgodmc:vessel/&lt;look&gt;/&lt;part&gt;). Pure: builds the zip in memory, the same bytes every time.
 */
public final class VesselPack {
    public static final String NAMESPACE = "gptgodmc";
    /** Minecraft 26.3's resource pack format (resource_major 97). */
    public static final int PACK_FORMAT = 97;

    /** Pieces drawn without the game's face shading, so they read as light sources. */
    private static boolean glows(Piece p) {
        return p == Piece.EYE || p == Piece.GEM || p == Piece.HALO;
    }

    /** The item_model id for a look's region, e.g. gptgodmc:vessel/shade/head. */
    public static String modelId(String look, String key) {
        return NAMESPACE + ":" + path(look, key);
    }

    static String path(String look, String key) {
        return "vessel/" + look + "/" + key;
    }

    public record Built(byte[] zip, byte[] sha1) {
        public String hashHex() {
            StringBuilder sb = new StringBuilder();
            for (byte b : sha1) sb.append(String.format(Locale.ROOT, "%02x", b));
            return sb.toString();
        }
    }

    /** Every file of the pack, by path. */
    public static Map<String, byte[]> files() {
        Map<String, byte[]> out = new LinkedHashMap<>();
        out.put("pack.mcmeta", utf8(packMeta()));
        out.put("pack.png", png(icon()));
        Layout layout = VesselSkin.LAYOUT;
        for (String look : VesselLook.NAMES) {
            VesselTextures.Atlas atlas = VesselTextures.atlas(look);
            String tex = "assets/" + NAMESPACE + "/textures/item/vessel/" + look + ".png";
            out.put(tex, png(strip(atlas)));
            out.put(tex + ".mcmeta", utf8(animationMeta(layout)));
            for (String key : VesselSkin.keys()) {
                String p = path(look, key);
                out.put("assets/" + NAMESPACE + "/items/" + p + ".json", utf8(itemDefinition(look, key)));
                out.put("assets/" + NAMESPACE + "/models/item/" + p + ".json", utf8(model(look, key, layout)));
            }
        }
        // The Godslayer sword's animated model rides in the same pack.
        out.putAll(net.bigyous.gptgodmc.god.GodslayerArt.files());
        out.putAll(net.bigyous.gptgodmc.god.DeathArt.files());
        // The true form's eyes, feathers and faces too.
        out.putAll(TrueFormPack.files());
        // The rift a god steps out of when it takes form.
        out.putAll(RiftPack.files());
        // The servants' own bodies (seraph, imp, wraith, hierophant, colossus).
        out.putAll(net.bigyous.gptgodmc.god.living.ServantPack.files());
        // The relics the gods have made (their sprites, as items).
        out.putAll(net.bigyous.gptgodmc.god.art.RelicPack.files());
        // The God Manager's painted icons (own item models; no vanilla item changes).
        out.putAll(net.bigyous.gptgodmc.gui.MenuIcons.files());
        return out;
    }

    public static Built build() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> e : files().entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTime(315532800000L); // fixed, so the hash never changes between builds
                zip.putNextEntry(entry);
                zip.write(e.getValue());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        byte[] zipBytes = bytes.toByteArray();
        try {
            return new Built(zipBytes, MessageDigest.getInstance("SHA-1").digest(zipBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String packMeta() {
        return """
                {
                  "pack": {
                    "description": "GPTGOD: the god's own skin",
                    "min_format": %d,
                    "max_format": 1000
                  }
                }
                """.formatted(PACK_FORMAT);
    }

    static String animationMeta(Layout layout) {
        return """
                {
                  "animation": {
                    "frametime": %d,
                    "interpolate": true,
                    "width": %d,
                    "height": %d
                  }
                }
                """.formatted(VesselSkin.FRAME_TICKS, layout.width(), layout.height());
    }

    static String itemDefinition(String look, String key) {
        return """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "%s:item/%s"
                  }
                }
                """.formatted(NAMESPACE, path(look, key));
    }

    /** One full box, each face mapped to its rectangle of the look's atlas (UVs in sixteenths). */
    static String model(String look, String key, Layout layout) {
        Piece piece = VesselSkin.pieceOf(key);
        VesselSkin.Region region = layout.region(key);
        StringBuilder faces = new StringBuilder();
        for (Face f : Face.values()) {
            Rect r = region.face(f);
            if (faces.length() > 0) faces.append(",\n");
            faces.append(String.format(Locale.ROOT,
                    "        \"%s\": { \"uv\": [%s, %s, %s, %s], \"texture\": \"#skin\" }", f.id(),
                    uv(r.x(), layout.width()), uv(r.y(), layout.height()), uv(r.x() + r.w(), layout.width()),
                    uv(r.y() + r.h(), layout.height())));
        }
        String texture = NAMESPACE + ":item/vessel/" + look;
        return """
                {
                  "textures": { "skin": "%s", "particle": "%s" },
                  "elements": [
                    {
                      "from": [0, 0, 0],
                      "to": [16, 16, 16],
                      "shade": %s,
                      "faces": {
                %s
                      }
                    }
                  ]
                }
                """.formatted(texture, texture, !glows(piece), faces);
    }

    private static String uv(int texel, int size) {
        double v = texel * 16.0 / size;
        String s = String.format(Locale.ROOT, "%.5f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    /** The frames stacked top to bottom, as Minecraft reads an animated texture. */
    static BufferedImage strip(VesselTextures.Atlas atlas) {
        int w = atlas.layout().width(), h = atlas.layout().height();
        BufferedImage out = new BufferedImage(w, h * atlas.frames().length, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        for (int i = 0; i < atlas.frames().length; i++) g.drawImage(atlas.frames()[i], 0, i * h, null);
        g.dispose();
        return out;
    }

    /** The pack's icon: the shade's face, scaled up. */
    static BufferedImage icon() {
        VesselTextures.Atlas atlas = VesselTextures.atlas("shade");
        Rect r = atlas.layout().region("head").face(Face.SOUTH);
        BufferedImage face = atlas.frames()[0].getSubimage(r.x(), r.y(), r.w(), r.h());
        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(face.getScaledInstance(64, 64, Image.SCALE_FAST), 0, 0, null);
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

    private VesselPack() {
    }
}
