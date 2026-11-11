package net.bigyous.gptgodmc.god;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * Textures for the solid pieces of a god's death ({@link DeathPieces}): shockwave rings in each death's
 * colors, the supernova's corona, the black hole's disc, rune sigils and glass shards. Each is a flat
 * plate model (gptgodmc:deathshow/&lt;key&gt;) that glows. Painted from code, deterministic; {@link #files()}
 * go into the vessel resource pack.
 */
public final class DeathArt {
    private DeathArt() {
    }

    public static final String NAMESPACE = "gptgodmc";

    /** Every model key, in a fixed order. */
    public static List<String> keys() {
        List<String> k = new ArrayList<>();
        for (DeathShow.Variant v : DeathShow.Variant.values()) k.add("ring_" + v.id());
        for (DeathShow.Variant v : DeathShow.Variant.values()) k.add("beam_" + v.id());
        k.add("corona");
        k.add("disc");
        k.add("disc_1");
        k.add("disc_2");
        for (int i = 0; i < 4; i++) k.add("sigil_" + i);
        for (int i = 0; i < 3; i++) k.add("shard_" + i);
        return k;
    }

    public static String modelId(String key) {
        return NAMESPACE + ":deathshow/" + key;
    }

    /** The texture for a key. */
    public static BufferedImage texture(String key) {
        if (key.startsWith("ring_")) {
            DeathShow.Variant v = DeathShow.Variant.parse(key.substring(5));
            return ring(DeathShow.palette(v).light().rgb, DeathShow.palette(v).echo().rgb);
        }
        if (key.startsWith("beam_")) {
            DeathShow.Variant v = DeathShow.Variant.parse(key.substring(5));
            return beam(DeathShow.palette(v).light().rgb);
        }
        if (key.equals("corona")) return corona();
        if (key.startsWith("disc")) return disc(key.equals("disc") ? 0 : Integer.parseInt(key.substring(5)));
        if (key.startsWith("sigil_")) return sigil(Integer.parseInt(key.substring(6)));
        if (key.startsWith("shard_")) return shard(Integer.parseInt(key.substring(6)));
        throw new IllegalArgumentException(key);
    }

    private static int argb(double a, int rgb) {
        int al = (int) Math.round(Math.max(0, Math.min(1, a)) * 255);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int c1, int c2, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    /** A bright ring with a soft inner and outer edge and a white-hot crest. */
    static BufferedImage ring(int light, int echo) {
        int n = 64;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                double r = Math.hypot(dx, dy);
                double crest = Math.exp(-Math.pow((r - 0.9) / 0.04, 2));
                double band = Math.exp(-Math.pow((r - 0.86) / 0.09, 2));
                double a = Math.max(crest, band * 0.75);
                if (r > 0.99) a = 0;
                int c = mix(mix(echo, light, band), 0xFFFFFF, crest * 0.7);
                img.setRGB(x, y, a < 0.04 ? 0 : argb(a, c));
            }
        }
        return img;
    }

    /**
     * A shaft of light: a white-hot core fading through the death's color to nothing at the sides,
     * softening toward the top so the beam seems to dissolve into the sky.
     */
    static BufferedImage beam(int color) {
        int n = 32;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++) {
            double up = 1 - (y + 0.5) / n; // 0 at the bottom, 1 at the top
            double fadeTop = Math.min(1, (1 - up) * 3.2);
            double fadeFoot = Math.min(1, up * 12 + 0.35);
            for (int x = 0; x < n; x++) {
                double u = Math.abs((x + 0.5) / n * 2 - 1); // 0 in the middle, 1 at the edge
                double core = Math.exp(-u * u / 0.025);
                double halo = Math.exp(-u * u / 0.18);
                double a = Math.min(1, core * 0.95 + halo * 0.55) * fadeTop * fadeFoot;
                int c = mix(color, 0xFFFFFF, core);
                img.setRGB(x, y, a < 0.03 ? 0 : argb(a, c));
            }
        }
        return img;
    }

    /** A white-gold star with long rays, fading to nothing at its edge. */
    static BufferedImage corona() {
        int n = 64;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                double r = Math.hypot(dx, dy), ang = Math.atan2(dy, dx);
                double core = Math.exp(-r * r / 0.03);
                double halo = Math.max(0, 1 - r) * 0.7;
                double rays = Math.pow(Math.abs(Math.cos(ang * 6)), 24) * Math.max(0, 1 - r) * 0.9
                        + Math.pow(Math.abs(Math.cos(ang * 6 + 0.26)), 40) * Math.max(0, 1 - r * 1.2) * 0.6;
                double a = Math.min(1, core + halo * halo + rays);
                int c = mix(0xFFB23A, 0xFFFFFF, core + rays * 0.5);
                img.setRGB(x, y, a < 0.04 ? 0 : argb(a, c));
            }
        }
        return img;
    }

    /** An accretion disc: violet spiral arms round a black hole. */
    static BufferedImage disc(int variant) {
        int n = 64;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        int arms = 3 + variant;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                double r = Math.hypot(dx, dy), ang = Math.atan2(dy, dx);
                if (r > 0.98) {
                    img.setRGB(x, y, 0);
                    continue;
                }
                double spiral = 0.5 + 0.5 * Math.cos(arms * ang + Math.log(r + 0.05) * (7 + variant * 2));
                double fade = Math.min(1, (1 - r) * 4);
                double a = (0.25 + 0.75 * Math.pow(spiral, 2)) * fade;
                int c = mix(variant == 1 ? 0x2A0A4A : 0x4B2BD8, 0xD8A0FF, spiral * (1 - r));
                if (r < 0.2) {
                    a = 1;
                    c = 0x050008; // the hole itself
                } else if (r < 0.26) {
                    a = 1;
                    c = 0xF0D8FF; // its burning rim
                }
                img.setRGB(x, y, a < 0.05 ? 0 : argb(a, c));
            }
        }
        return img;
    }

    /** A rune in a circle, drawn in glowing lines on nothing. */
    static BufferedImage sigil(int seed) {
        int n = 32;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        float[][] v = new float[n][n];
        Random rnd = new Random(1000 + seed);
        // The circle.
        for (int i = 0; i < 400; i++) {
            double a = i * Math.PI * 2 / 400;
            stamp(v, 16 + Math.cos(a) * 13.5, 16 + Math.sin(a) * 13.5, 1f);
        }
        // Strokes between points on a small grid, so it reads as writing.
        double[][] pts = new double[7][];
        for (int i = 0; i < pts.length; i++) {
            pts[i] = new double[] { 9 + rnd.nextInt(3) * 7, 7 + rnd.nextInt(4) * 6 };
        }
        line(v, 16, 6, 16, 26);
        for (int i = 0; i < 4; i++) {
            double[] p = pts[rnd.nextInt(pts.length)], q = pts[rnd.nextInt(pts.length)];
            line(v, p[0], p[1], q[0], q[1]);
        }
        int color = seed % 2 == 0 ? 0xC77DFF : 0x7AE8FF;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double a = Math.min(1, v[y][x]);
                img.setRGB(x, y, a < 0.08 ? 0 : argb(a, mix(color, 0xFFFFFF, a * a * 0.6)));
            }
        }
        return img;
    }

    private static void line(float[][] v, double x0, double y0, double x1, double y1) {
        int steps = (int) Math.max(2, Math.hypot(x1 - x0, y1 - y0) * 3);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            stamp(v, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, 1f);
        }
    }

    private static void stamp(float[][] v, double cx, double cy, float w) {
        for (int y = (int) cy - 2; y <= (int) cy + 2; y++) {
            for (int x = (int) cx - 2; x <= (int) cx + 2; x++) {
                if (x < 0 || y < 0 || x >= v.length || y >= v.length) continue;
                double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                v[y][x] = Math.max(v[y][x], (float) (w * Math.max(0, 1.3 - d * 0.75)));
            }
        }
    }

    /** A jagged sliver of glass: pale, see-through, with a bright edge and a red vein. */
    static BufferedImage shard(int seed) {
        int n = 16;
        Random rnd = new Random(500 + seed);
        double[][] poly = new double[3 + seed % 2][];
        for (int i = 0; i < poly.length; i++) {
            double a = i * Math.PI * 2 / poly.length + rnd.nextDouble() * 0.8;
            double r = 5 + rnd.nextDouble() * 3;
            poly[i] = new double[] { 8 + Math.cos(a) * r, 8 + Math.sin(a) * r };
        }
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double px = x + 0.5, py = y + 0.5;
                if (!inside(poly, px, py)) {
                    img.setRGB(x, y, 0);
                    continue;
                }
                double edge = edgeDistance(poly, px, py);
                int c;
                double a;
                if (edge < 1.0) {
                    c = 0xFFFFFF;
                    a = 0.95;
                } else if (Math.abs(px - py + (seed - 1) * 2) < 0.8) {
                    c = 0xE01030; // a red vein through the glass
                    a = 0.9;
                } else {
                    c = mix(0xBFE8FF, 0x7AB8E8, (px + py) / 32);
                    a = 0.5;
                }
                img.setRGB(x, y, argb(a, c));
            }
        }
        return img;
    }

    private static boolean inside(double[][] poly, double x, double y) {
        boolean in = false;
        for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
            if ((poly[i][1] > y) != (poly[j][1] > y)
                    && x < (poly[j][0] - poly[i][0]) * (y - poly[i][1]) / (poly[j][1] - poly[i][1]) + poly[i][0]) {
                in = !in;
            }
        }
        return in;
    }

    private static double edgeDistance(double[][] poly, double x, double y) {
        double best = Double.MAX_VALUE;
        for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
            double ax = poly[j][0], ay = poly[j][1], bx = poly[i][0], by = poly[i][1];
            double t = ((x - ax) * (bx - ax) + (y - ay) * (by - ay)) / ((bx - ax) * (bx - ax) + (by - ay) * (by - ay));
            t = Math.max(0, Math.min(1, t));
            best = Math.min(best, Math.hypot(x - (ax + t * (bx - ax)), y - (ay + t * (by - ay))));
        }
        return best;
    }

    /**
     * A flat plate one block across facing north and south, centered (the item display centers it).
     * Glowing pieces give off light so they stay bright at night.
     */
    static String model(String key, boolean glows) {
        String tex = NAMESPACE + ":item/deathshow/" + key;
        String face = "{\"uv\":[0,0,16,16],\"texture\":\"#t\"}";
        String edge = "{\"uv\":[0,0,0.1,0.1],\"texture\":\"#t\"}";
        return "{\"textures\":{\"t\":\"" + tex + "\",\"particle\":\"" + tex + "\"},\"elements\":[{\"from\":[0,0,7.9],"
                + "\"to\":[16,16,8.1],\"shade\":false" + (glows ? ",\"light_emission\":15" : "") + ",\"faces\":{"
                + "\"north\":" + face + ",\"south\":" + face + ",\"east\":" + edge + ",\"west\":" + edge + ",\"up\":"
                + edge + ",\"down\":" + edge + "}}]}";
    }

    /** Two plates crossed along the vertical axis: a beam that looks round from every side. */
    static String crossedModel(String key) {
        String tex = NAMESPACE + ":item/deathshow/" + key;
        String face = "{\"uv\":[0,0,16,16],\"texture\":\"#t\"}";
        String edge = "{\"uv\":[0,0,0.1,0.1],\"texture\":\"#t\"}";
        String a = "{\"from\":[0,0,7.95],\"to\":[16,16,8.05],\"shade\":false,\"light_emission\":15,\"faces\":{"
                + "\"north\":" + face + ",\"south\":" + face + ",\"east\":" + edge + ",\"west\":" + edge + ",\"up\":"
                + edge + ",\"down\":" + edge + "}}";
        String b = "{\"from\":[7.95,0,0],\"to\":[8.05,16,16],\"shade\":false,\"light_emission\":15,\"faces\":{"
                + "\"east\":" + face + ",\"west\":" + face + ",\"north\":" + edge + ",\"south\":" + edge + ",\"up\":"
                + edge + ",\"down\":" + edge + "}}";
        return "{\"textures\":{\"t\":\"" + tex + "\",\"particle\":\"" + tex + "\"},\"elements\":[" + a + "," + b
                + "]}";
    }

    /** The pack entries: item definitions, models and textures for every key. */
    public static Map<String, byte[]> files() {
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (String key : keys()) {
            String path = "deathshow/" + key;
            out.put("assets/" + NAMESPACE + "/items/" + path + ".json", utf8("{\"model\":{\"type\":\"minecraft:model\","
                    + "\"model\":\"" + NAMESPACE + ":item/" + path + "\"}}"));
            String model = key.startsWith("beam_") ? crossedModel(key) : model(key, !key.startsWith("shard"));
            out.put("assets/" + NAMESPACE + "/models/item/" + path + ".json", utf8(model));
            out.put("assets/" + NAMESPACE + "/textures/item/" + path + ".png", png(texture(key)));
        }
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
