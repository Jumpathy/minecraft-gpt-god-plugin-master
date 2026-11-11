package net.bigyous.gptgod.client.relic;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Decodes the plugin's relic messages (JSON on gptgod:main): "relic" carries one relic's art as a PNG
 * strip (with, if the plugin likes, a lit layer laid over it, a depth map and painted walls), "relic_gone" says to
 * forget it. Pure. Never throws: a relic message that makes no sense becomes
 * {@link Bad} and is dropped, so a broken or hostile server cannot crash the client or make it hold huge
 * pictures.
 */
public final class RelicProtocol {
    private RelicProtocol() {
    }

    /** The plugin sends at most 32 KB a message; the channel itself hands on no more than this. */
    public static final int MAX_BYTES = 64 * 1024;

    public sealed interface Message permits Art, Gone, Bad {
    }

    public record Art(RelicArt art) implements Message {
    }

    public record Gone(String id) implements Message {
    }

    /** A relic message that could not be used. It is still ours: nobody else should look at it. */
    public record Bad() implements Message {
    }

    /** The message in these bytes, or null when they are not a relic message at all. */
    public static Message decode(byte[] data) {
        if (data == null || data.length > MAX_BYTES || !mentionsRelic(data)) return null;
        try {
            JsonElement root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            return root.isJsonObject() ? decode(root.getAsJsonObject()) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A cheap look before parsing: every other message goes on to its own decoder untouched. */
    private static boolean mentionsRelic(byte[] data) {
        for (int i = 0; i + 4 < data.length; i++) {
            if (data[i] == 'r' && data[i + 1] == 'e' && data[i + 2] == 'l' && data[i + 3] == 'i' && data[i + 4] == 'c') return true;
        }
        return false;
    }

    static Message decode(JsonObject o) {
        String t = str(o, "t", "", 24);
        if (t.equals("relic_gone")) {
            String id = id(o);
            return id == null ? new Bad() : new Gone(id);
        }
        if (!t.equals("relic")) return null;
        try {
            RelicArt art = art(o);
            return art == null ? new Bad() : new Art(art);
        } catch (RuntimeException e) {
            return new Bad();
        }
    }

    private static RelicArt art(JsonObject o) {
        String id = id(o);
        Integer w = whole(o, "w"), h = whole(o, "h"), frames = whole(o, "frames");
        if (id == null || w == null || h == null || frames == null) return null;
        if (w < RelicArt.MIN_W || w > RelicArt.MAX_W || h < RelicArt.MIN_H || h > RelicArt.MAX_H || frames < 1
                || frames > RelicArt.MAX_FRAMES) {
            return null;
        }
        int[] base = png(str(o, "png", null, MAX_BYTES), w, h * frames);
        if (base == null) return null;
        // The lit layer is optional: one that cannot be read is left out, and the picture kept.
        String litData = str(o, "lit", null, MAX_BYTES);
        int[] lit = litData == null || litData.isEmpty() ? null : png(litData, w, h * frames);
        Integer rev = whole(o, "rev"), ticks = whole(o, "ticks");
        String name = str(o, "name", null, 64);
        // So are the painted walls: walls that cannot be read leave the sides the colour of their faces.
        int[] walls = null;
        int wallFrames = 0;
        byte[] wallsPng = bytes(str(o, "walls", null, MAX_BYTES));
        if (wallsPng != null && wallsPng.length >= 33) {
            int height = int32(wallsPng, 20);
            if (height > 0 && height % (2 * h) == 0 && height / (2 * h) <= frames) {
                walls = png(wallsPng, 2 * w, height);
                wallFrames = walls == null ? 0 : height / (2 * h);
            }
        }
        return RelicArt.of(id, rev == null ? 0 : rev, name == null || name.isBlank() ? id : name.trim(),
                RelicArt.Kind.of(str(o, "kind", null, 16)), w, h, frames, ticks == null ? 4 : ticks, base, lit,
                bytes(str(o, "depth", null, MAX_BYTES)), walls, wallFrames);
    }

    /** An optional field's bytes as sent. Whether they fit the picture is for others to say; what is not base64 is nothing. */
    private static byte[] bytes(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A relic's id: 1 to 40 of a-z, 0-9 and underscore, as in its item_model. Anything else is refused. */
    private static String id(JsonObject o) {
        String id = str(o, "id", null, 41);
        if (id == null || id.isEmpty() || id.length() > 40) return null;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_') return null;
        }
        return id;
    }

    /**
     * The relic an art belongs to. A relic's second form is its own art, named after the first with a
     * double underscore (starbrand__on); the server switches a stack between them.
     */
    public static String relicOf(String id) {
        int at = id.indexOf("__");
        return at <= 0 ? id : id.substring(0, at);
    }

    /**
     * A base64 PNG of exactly this size, as ARGB pixels row by row, or null. The size is read from the
     * PNG's header before anything is unpacked, so a small file claiming to be enormous costs nothing.
     */
    static int[] png(String base64, int width, int height) {
        if (base64 == null) return null;
        try {
            return png(Base64.getDecoder().decode(base64), width, height);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A PNG of exactly this size, as ARGB pixels row by row, or null. */
    static int[] png(byte[] bytes, int width, int height) {
        if (bytes == null) return null;
        try {
            if (bytes.length < 33 || !isPngOf(bytes, width, height)) return null;
            BufferedImage image = read(bytes);
            if (image == null || image.getWidth() != width || image.getHeight() != height) return null;
            return image.getRGB(0, 0, width, height, null, 0, width);
        } catch (IOException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static final int[] PNG_START = { 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R' };

    private static boolean isPngOf(byte[] b, int width, int height) {
        for (int i = 0; i < PNG_START.length; i++) {
            if ((b[i] & 255) != PNG_START[i]) return false;
        }
        return int32(b, 16) == width && int32(b, 20) == height;
    }

    private static int int32(byte[] b, int at) {
        return (b[at] & 255) << 24 | (b[at + 1] & 255) << 16 | (b[at + 2] & 255) << 8 | (b[at + 3] & 255);
    }

    /** Read in memory, as a PNG and nothing else, without the notes a PNG may carry. */
    private static BufferedImage read(byte[] bytes) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
        if (!readers.hasNext()) return null;
        ImageReader reader = readers.next();
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(in, true, true);
            return reader.read(0);
        } finally {
            reader.dispose();
        }
    }

    // ---- Reading fields defensively ------------------------------------------------------------

    private static String str(JsonObject o, String key, String fallback, int max) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return fallback;
        String s = e.getAsString();
        StringBuilder b = new StringBuilder(Math.min(s.length(), max));
        for (int i = 0; i < s.length() && b.length() < max; i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c != 0x7F && c != '§') b.append(c); // no control chars or legacy colour codes
        }
        return b.toString();
    }

    /** A whole number, or null when the field is missing or is anything else. Clamped to the int range. */
    private static Integer whole(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;
        double v = e.getAsDouble();
        if (Double.isNaN(v) || v != Math.rint(v)) return null;
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v));
    }
}
