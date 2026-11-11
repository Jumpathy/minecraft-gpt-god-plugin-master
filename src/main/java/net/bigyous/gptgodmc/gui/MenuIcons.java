package net.bigyous.gptgodmc.gui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

/**
 * The God Manager's painted icons, drawn from code (the same pixels every run): a dark medallion with a
 * rim in its group's colour and a glyph. They ride in the vessel resource pack as their own item models
 * (gptgodmc:menu/&lt;key&gt;), set with item_model only on the menu's copies for players who loaded the
 * pack, so no vanilla item ever changes its look. Everyone else sees the vanilla item.
 */
public final class MenuIcons {
    public static final int SIZE = 32;

    /** Group colours: rims and glyph tints. */
    static final int NAV = 0xE8E4F0, SERVER = 0xB57BFF, WHO = 0xFF6FD8, SHOW = 0x45D6F0, DOES = 0xFFC23D,
            ON = 0x5EE36A, OFF = 0xFF5050;

    /** Every icon, with its rim colour. */
    static final Map<String, Integer> RIMS = rims();

    public static final List<String> KEYS = List.copyOf(RIMS.keySet());

    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private MenuIcons() {
    }

    private static Map<String, Integer> rims() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("back", NAV);
        m.put("home", NAV);
        m.put("close", OFF);
        m.put("help", NAV);
        m.put("server", SERVER);
        m.put("create", ON);
        m.put("switches", SERVER);
        m.put("config", SERVER);
        m.put("god_lesser", 0xD98A4E);
        m.put("god_greater", 0xF2C94C);
        m.put("god_supreme", 0x7FF0FF);
        m.put("personality", WHO);
        m.put("nature", WHO);
        m.put("might", WHO);
        m.put("mood", SHOW);
        m.put("presence", SHOW);
        m.put("looks", SHOW);
        m.put("voice", SHOW);
        m.put("commands", DOES);
        m.put("bonds", DOES);
        m.put("wake", DOES);
        m.put("pause", DOES);
        m.put("resume", ON);
        m.put("power_on", ON);
        m.put("power_off", OFF);
        m.put("remove", OFF);
        return m;
    }

    /** The item_model for an icon key, or null when there is no such painted icon. */
    public static String modelFor(String key) {
        return key != null && RIMS.containsKey(key) ? "gptgodmc:menu/" + key : null;
    }

    /** The pack entry that must exist before the model may be used (a missing one shows a purple cube). */
    public static String itemFile(String key) {
        return "assets/gptgodmc/items/menu/" + key + ".json";
    }

    public static BufferedImage image(String key) {
        return CACHE.computeIfAbsent(key, MenuIcons::paint);
    }

    /** The pack entries for every icon (added to the vessel pack). */
    public static Map<String, byte[]> files() {
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (String key : KEYS) {
            out.put(itemFile(key), utf8("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"gptgodmc:item/menu/"
                    + key + "\"}}"));
            out.put("assets/gptgodmc/models/item/menu/" + key + ".json", utf8("{\"parent\":\"minecraft:item/generated\","
                    + "\"textures\":{\"layer0\":\"gptgodmc:item/menu/" + key + "\"}}"));
            out.put("assets/gptgodmc/textures/item/menu/" + key + ".png", png(image(key)));
        }
        return out;
    }

    // ---- Painting ----------------------------------------------------------------------------------

    /** A glyph's shape: true where it is drawn. */
    static final class Mask {
        final boolean[][] on = new boolean[SIZE][SIZE];

        void set(int x, int y, boolean v) {
            if (x >= 0 && y >= 0 && x < SIZE && y < SIZE) on[y][x] = v;
        }

        boolean get(int x, int y) {
            return x >= 0 && y >= 0 && x < SIZE && y < SIZE && on[y][x];
        }

        Mask disc(double cx, double cy, double r, boolean v) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    double dx = x + 0.5 - cx, dy = y + 0.5 - cy;
                    if (dx * dx + dy * dy <= r * r) set(x, y, v);
                }
            }
            return this;
        }

        Mask disc(double cx, double cy, double r) {
            return disc(cx, cy, r, true);
        }

        /** Adds the pixels between radius r0 and r1 (nothing inside is cleared). */
        Mask ring(double cx, double cy, double r0, double r1) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    double dx = x + 0.5 - cx, dy = y + 0.5 - cy, d2 = dx * dx + dy * dy;
                    if (d2 > r0 * r0 && d2 <= r1 * r1) on[y][x] = true;
                }
            }
            return this;
        }

        /** Adds an ellipse with these radii. */
        Mask oval(double cx, double cy, double rx, double ry, boolean v) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    double dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry;
                    if (dx * dx + dy * dy <= 1) on[y][x] = v;
                }
            }
            return this;
        }

        Mask rect(int x0, int y0, int x1, int y1, boolean v) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) set(x, y, v);
            }
            return this;
        }

        Mask rect(int x0, int y0, int x1, int y1) {
            return rect(x0, y0, x1, y1, true);
        }

        /** A thick segment from (ax,ay) to (bx,by). */
        Mask line(double ax, double ay, double bx, double by, double width, boolean v) {
            double lx = bx - ax, ly = by - ay, len2 = lx * lx + ly * ly;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    double px = x + 0.5 - ax, py = y + 0.5 - ay;
                    double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, (px * lx + py * ly) / len2));
                    double dx = px - t * lx, dy = py - t * ly;
                    if (dx * dx + dy * dy <= width * width / 4) set(x, y, v);
                }
            }
            return this;
        }

        Mask line(double ax, double ay, double bx, double by, double width) {
            return line(ax, ay, bx, by, width, true);
        }

        Mask tri(double ax, double ay, double bx, double by, double cx, double cy, boolean v) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    double px = x + 0.5, py = y + 0.5;
                    double d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
                    double d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
                    double d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
                    boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
                    if (!(neg && pos)) set(x, y, v);
                }
            }
            return this;
        }

        Mask tri(double ax, double ay, double bx, double by, double cx, double cy) {
            return tri(ax, ay, bx, by, cx, cy, true);
        }

        /** Rows of a small bitmap ('#' on), scaled by s, top-left at (x0,y0). */
        Mask bitmap(int x0, int y0, int s, String... rows) {
            for (int r = 0; r < rows.length; r++) {
                for (int c = 0; c < rows[r].length(); c++) {
                    if (rows[r].charAt(c) == '#') rect(x0 + c * s, y0 + r * s, x0 + c * s + s - 1, y0 + r * s + s - 1);
                }
            }
            return this;
        }
    }

    static BufferedImage paint(String key) {
        Integer rim = RIMS.get(key);
        if (rim == null) throw new IllegalArgumentException("no menu icon " + key);
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        medallion(img, rim);
        Mask glyph = glyph(key);
        int tint = key.startsWith("god_") ? rim : mix(rim, 0xFFFFFF, 0.55);
        draw(img, glyph, tint);
        if (key.equals("looks")) {
            // Paint dabs on the palette.
            dot(img, 12, 13, 0xFF4F6D);
            dot(img, 16, 11, 0xFFD34F);
            dot(img, 20, 13, 0x4FC3FF);
            dot(img, 12, 18, 0x7CFF6B);
        }
        return img;
    }

    /** The round medallion: a dark face with a soft top light, a rim in the group's colour and a dark edge. */
    private static void medallion(BufferedImage img, int rim) {
        double c = SIZE / 2.0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double dx = x + 0.5 - c, dy = y + 0.5 - c, d = Math.sqrt(dx * dx + dy * dy);
                if (d > 15.5) continue;
                int rgb;
                if (d > 14.5) {
                    rgb = 0x0C0814;
                } else if (d > 12.5) {
                    // The rim, lit from the top left.
                    double light = 0.5 + 0.5 * (-(dx + dy) / (d * 1.4142));
                    rgb = mix(mix(rim, 0x000000, 0.45), mix(rim, 0xFFFFFF, 0.25), light);
                } else {
                    double t = Math.max(0, Math.min(1, (y + 0.5) / SIZE));
                    rgb = mix(mix(0x2A2140, rim, 0.12), 0x120C1E, t);
                }
                img.setRGB(x, y, 0xFF000000 | rgb);
            }
        }
    }

    /** Fills the glyph with a top-to-bottom shade of its tint and a dark outline around it. */
    private static void draw(BufferedImage img, Mask m, int tint) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (m.get(x, y)) continue;
                boolean edge = m.get(x - 1, y) || m.get(x + 1, y) || m.get(x, y - 1) || m.get(x, y + 1);
                if (edge && (img.getRGB(x, y) >>> 24) != 0) img.setRGB(x, y, 0xFF06040A);
            }
        }
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (!m.get(x, y)) continue;
                double t = (y - 7) / 18.0;
                int rgb = t < 0.5 ? mix(mix(tint, 0xFFFFFF, 0.35), tint, t * 2) : mix(tint, mix(tint, 0x000000, 0.35),
                        (t - 0.5) * 2);
                img.setRGB(x, y, 0xFF000000 | rgb);
            }
        }
    }

    private static void dot(BufferedImage img, int cx, int cy, int rgb) {
        for (int y = cy - 1; y <= cy; y++) {
            for (int x = cx - 1; x <= cx; x++) img.setRGB(x, y, 0xFF000000 | rgb);
        }
    }

    /** The glyph of each icon, inside the face (about 8..24). */
    static Mask glyph(String key) {
        Mask m = new Mask();
        switch (key) {
            case "back" -> m.tri(8, 16, 16, 9, 16, 23).rect(15, 13, 24, 18);
            case "home" -> m.tri(16, 7, 6, 16, 26, 16).rect(9, 16, 23, 24).rect(14, 18, 18, 24, false);
            case "close" -> m.line(10, 10, 22, 22, 3.6).line(22, 10, 10, 22, 3.6);
            case "help" -> m.bitmap(10, 7, 2,
                    ".###.",
                    "##.##",
                    "...##",
                    "..##.",
                    "..#..",
                    ".....",
                    "..#..").rect(14, 21, 17, 24);
            case "server" -> {
                // A four-pointed star.
                m.tri(16, 6, 13, 16, 19, 16).tri(16, 26, 13, 16, 19, 16).tri(6, 16, 16, 13, 16, 19)
                        .tri(26, 16, 16, 13, 16, 19).disc(16, 16, 3.5);
            }
            case "create" -> m.rect(14, 8, 18, 24).rect(8, 14, 24, 18);
            case "switches" -> {
                // Two toggles: the top one on (knob right), the bottom one off (knob left).
                m.disc(11.5, 11.5, 3.6).disc(20.5, 11.5, 3.6).rect(11, 8, 21, 15).disc(20.5, 11.5, 2.2, false);
                m.disc(11.5, 20.5, 3.6).disc(20.5, 20.5, 3.6).rect(11, 17, 21, 24).disc(11.5, 20.5, 2.2, false);
            }
            case "config" -> {
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    m.line(16, 16, 16 + Math.cos(a) * 9.5, 16 + Math.sin(a) * 9.5, 3.6);
                }
                m.disc(16, 16, 7).disc(16, 16, 3, false);
            }
            case "god_lesser" -> m.disc(16, 17, 5).tri(16, 7, 12, 15, 20, 15);
            case "god_greater" -> crown(m, 3);
            case "god_supreme" -> crown(m, 3).rect(7, 18, 25, 24).disc(11, 21, 1.5, false).disc(16, 21, 1.5, false)
                    .disc(21, 21, 1.5, false).disc(16, 13, 1.3, false);
            case "personality" -> {
                // A mask: a face with two eyes and a smile.
                m.disc(16, 16, 9).rect(7, 9, 25, 14);
                m.disc(12.5, 14, 2, false).disc(19.5, 14, 2, false);
                m.line(12, 19.5, 16, 21.5, 1.6, false).line(16, 21.5, 20, 19.5, 1.6, false);
            }
            case "nature" -> {
                // Three sliders.
                for (int i = 0; i < 3; i++) m.rect(10 + i * 6, 8, 11 + i * 6, 24);
                m.rect(8, 18, 13, 21).rect(14, 10, 19, 13).rect(20, 15, 25, 18);
            }
            case "might" -> m.tri(18, 6, 9, 18, 16, 18).tri(16, 14, 23, 14, 13, 26);
            case "mood" -> m.disc(12, 13, 4.6).disc(20, 13, 4.6).tri(7.6, 14.5, 24.4, 14.5, 16, 25);
            case "presence" -> m.disc(16, 10, 3.8).tri(16, 13, 8, 25, 24, 25).rect(10, 15, 22, 17);
            case "looks" -> m.disc(16, 16, 9).disc(21, 20, 3, false).rect(22, 21, 26, 26, false);
            case "voice" -> {
                // A speaker with two sound waves.
                m.ring(14, 16, 4.6, 6.4).ring(14, 16, 8.4, 10.2).rect(0, 0, 16, 31, false);
                m.rect(7, 13, 10, 19).tri(9, 16, 15, 8, 15, 24);
            }
            case "commands" -> {
                // A scroll with lines of writing.
                m.rect(9, 8, 23, 24).disc(9, 9.5, 2).disc(23, 22.5, 2);
                for (int r = 0; r < 4; r++) m.rect(12, 11 + r * 3, 20 - (r % 2) * 3, 11 + r * 3, false);
            }
            case "wake" -> {
                // An open eye: an almond, the iris a ring, a bright pupil.
                m.oval(16, 16, 10.5, 6, true).disc(16, 16, 4, false).disc(16, 16, 2);
            }
            case "bonds" -> m.ring(12, 16, 3.2, 5.8).ring(20, 16, 3.2, 5.8);
            case "pause" -> m.rect(10, 9, 14, 23).rect(18, 9, 22, 23);
            case "resume" -> m.tri(11, 8, 11, 24, 24, 16);
            case "power_on", "power_off" -> m.ring(16, 17, 5.5, 8.5).rect(13, 5, 19, 12, false).rect(15, 6, 17, 16);
            case "remove" -> {
                // A bin with a lid.
                m.rect(9, 9, 23, 11).rect(14, 7, 18, 8).rect(10, 13, 22, 24);
                for (int i = 0; i < 3; i++) m.rect(12 + i * 3 + (i > 0 ? 1 : 0), 15, 12 + i * 3 + (i > 0 ? 1 : 0), 22, false);
            }
            default -> m.disc(16, 16, 5);
        }
        return m;
    }

    /** A crown with this many points. */
    private static Mask crown(Mask m, int points) {
        m.rect(8, 18, 24, 23);
        for (int i = 0; i < points; i++) {
            double x = 8 + (16.0 * i / (points - 1));
            m.tri(x, 8, x - 3, 19, x + 3, 19).disc(x, 8, 1.4);
        }
        return m;
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
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
