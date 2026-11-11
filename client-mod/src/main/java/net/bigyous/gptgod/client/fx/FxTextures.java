package net.bigyous.gptgod.client.fx;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;

import net.bigyous.gptgod.client.GodClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The textures a god painted for its effects, sent by the plugin as {"t":"texture","id","rev","w","h",
 * "frames","ticks","png"} (png: a base64 PNG of the frames stacked top to bottom). Each frame becomes a
 * small texture of its own that repeats when a solid tiles it. Decoding is pure and never throws; a
 * message that makes no sense is dropped. The game's textures are made on the client thread, the first
 * time a picture is drawn.
 */
public final class FxTextures {
    private FxTextures() {
    }

    public static final int MAX = 48;
    public static final int MAX_SIDE = 32, MAX_FRAMES = 8;
    private static final byte[] PREFIX = "{\"t\":\"texture\"".getBytes(StandardCharsets.US_ASCII);

    /** One painted texture: frames of w*h ARGB pixels. */
    public record Picture(String id, int rev, int w, int h, int ticks, int[][] frames) {
    }

    private static final Map<String, Picture> PICTURES = new LinkedHashMap<>();
    /** Texture names are gptgod:fx/&lt;slot&gt;_&lt;frame&gt;, slots being reused, so the game never holds more than MAX of ours. */
    private static final Map<String, Integer> SLOTS = new HashMap<>();
    private static final Map<String, Integer> SHOWN = new HashMap<>();
    private static final BitSet TAKEN = new BitSet();

    /** The picture in these bytes, or null when they are not a texture message (or not a usable one). */
    public static Picture decode(byte[] data) {
        if (data == null || data.length < PREFIX.length || data.length > 64 * 1024) return null;
        for (int i = 0; i < PREFIX.length; i++) {
            if (data[i] != PREFIX[i]) return null;
        }
        try {
            JsonElement root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return null;
            JsonObject o = root.getAsJsonObject();
            String id = o.get("id").getAsString();
            int w = o.get("w").getAsInt(), h = o.get("h").getAsInt(), frames = o.get("frames").getAsInt();
            if (!id.matches(FxSolid.TEXTURE_NAME) || w < 1 || h < 1 || w > MAX_SIDE || h > MAX_SIDE || frames < 1 || frames > MAX_FRAMES) return null;
            byte[] png = Base64.getDecoder().decode(o.get("png").getAsString());
            // The size a PNG claims is read before any of it is decoded.
            if (png.length < 33 || int32(png, 16) != w || int32(png, 20) != h * frames) return null;
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null || image.getWidth() != w || image.getHeight() != h * frames) return null;
            int[][] out = new int[frames][];
            for (int f = 0; f < frames; f++) out[f] = image.getRGB(0, f * h, w, h, null, 0, w);
            int ticks = o.has("ticks") ? Math.max(1, Math.min(40, o.get("ticks").getAsInt())) : 3;
            return new Picture(id, o.has("rev") ? o.get("rev").getAsInt() : 0, w, h, ticks, out);
        } catch (RuntimeException | java.io.IOException | LinkageError e) {
            return null;
        }
    }

    private static int int32(byte[] b, int at) {
        return (b[at] & 255) << 24 | (b[at + 1] & 255) << 16 | (b[at + 2] & 255) << 8 | (b[at + 3] & 255);
    }

    /** Takes a texture message. True when the bytes were one (so nobody else need look at them). */
    public static boolean receive(byte[] data) {
        if (data == null || data.length < PREFIX.length) return false;
        for (int i = 0; i < PREFIX.length; i++) {
            if (data[i] != PREFIX[i]) return false;
        }
        Picture p = decode(data);
        if (p != null) put(p);
        return true;
    }

    /** The sample textures the mod carries for its demo scenes (/godfx scene idol). */
    public static void samples() {
        for (String name : new String[] { "texture-veins", "texture-sigil" }) {
            try (java.io.InputStream in = FxTextures.class.getResourceAsStream("/gptgod-samples/" + name + ".json")) {
                if (in != null) receive(in.readAllBytes());
            } catch (java.io.IOException | RuntimeException e) {
                // no sample, no harm: the demo is drawn plain
            }
        }
    }

    public static synchronized void put(Picture p) {
        if (!PICTURES.containsKey(p.id()) && PICTURES.size() >= MAX) {
            String oldest = PICTURES.keySet().iterator().next();
            PICTURES.remove(oldest);
            SHOWN.remove(oldest);
            Integer slot = SLOTS.remove(oldest);
            if (slot != null) TAKEN.clear(slot);
        }
        PICTURES.put(p.id(), p);
        SHOWN.remove(p.id()); // repainted: its textures are made anew when next drawn
    }

    public static synchronized Picture get(String id) {
        return PICTURES.get(id);
    }

    /**
     * The game texture showing this picture's frame for now, or null when the picture is unknown (it is then
     * drawn plain). Client thread only.
     */
    public static synchronized Identifier frame(String id) {
        Picture p = id == null || id.isEmpty() ? null : PICTURES.get(id);
        if (p == null) return null;
        Integer slot = SLOTS.get(id);
        if (slot == null) {
            slot = TAKEN.nextClearBit(0);
            TAKEN.set(slot);
            SLOTS.put(id, slot);
        }
        Integer shown = SHOWN.get(id);
        if (shown == null || shown != p.rev()) {
            try {
                for (int f = 0; f < MAX_FRAMES; f++) {
                    Identifier name = GodClientMod.id("fx/" + slot + "_" + f);
                    if (f >= p.frames().length) {
                        Minecraft.getInstance().getTextureManager().release(name);
                        continue;
                    }
                    // Registering under a name that is taken closes the texture that had it.
                    Minecraft.getInstance().getTextureManager().register(name, new Tile(id, image(p.frames()[f], p.w(), p.h())));
                }
            } catch (RuntimeException | LinkageError e) {
                PICTURES.remove(id);
                return null;
            }
            SHOWN.put(id, p.rev());
        }
        long tick = System.currentTimeMillis() / 50;
        return GodClientMod.id("fx/" + slot + "_" + (int) ((tick / p.ticks()) % p.frames().length));
    }

    /** Sharp texels, and it repeats, so a solid can tile it. */
    private static final class Tile extends DynamicTexture {
        Tile(String id, NativeImage image) {
            super(() -> "gptgod fx texture " + id, image);
            sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
        }
    }

    private static NativeImage image(int[] argb, int width, int height) {
        NativeImage image = new NativeImage(width, height, false);
        try {
            for (int y = 0, at = 0; y < height; y++) {
                for (int x = 0; x < width; x++) image.setPixel(x, y, argb[at++]);
            }
            return image;
        } catch (RuntimeException e) {
            image.close();
            throw e;
        }
    }
}
