package net.bigyous.gptgodmc.god.art;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.god.fx.FxExpr;
import net.bigyous.gptgodmc.god.fx.FxSolid;

/**
 * A special relic's true shape: real geometry instead of its picture given thickness, so a scythe's blade can
 * curve, a staff be round, an orb be a ball. It is written in the solid language of the god's scenes
 * ({@link FxSolid}: lathe, tube, prism, ball, box, quads), one part after another, in the same square as the
 * relic's picture: x 0..16 from its left, y 0..16 from its bottom, z -8..8 toward the viewer. Parts stand
 * still (numbers only), are matter (never light: a relic is lit by the world like any item) and take a colour
 * or a material's name. Each part is made of a material and wears its pixel texture ({@link RelicTiles}), laid on
 * at a texel to a sixteenth as Minecraft's blocks and items are (see {@link #texel}). Minecraft first: a part is blocky unless it says "round": true (a lathe or tube is
 * built square in section, a ball as a cube), so curves are kept for what a curve is the point of. Players with the client mod see this shape; everyone else, and every inventory icon,
 * keeps the picture. Pure.
 */
public final class RelicShape {
    private RelicShape() {
    }

    public static final int MAX_PARTS = 12;
    /** Faces in all parts together. */
    public static final int MAX_FACES = 1600;
    /** How far a part may reach past the picture's square, in its sixteenths. */
    static final double SLACK = 4;
    /** Every part is drawn as it stands at this moment of its (unchanging) life. */
    public static final int TICK = FxSolid.MAX_TICKS / 2;

    private static final List<String> FORMULAS = List.of("x", "y", "z", "scale", "yaw", "pitch", "roll", "alpha");

    /** What the artist reads about it. */
    public static final String HELP = "\"shape\": a list of parts, built the way Minecraft builds things: from blocks. "
            + "Most parts should be boxes (a shaft, a guard, a pommel, a crossbar, a head), turned with roll where they lie "
            + "on the diagonal. A lathe or tube is built square in section and a ball as a cube, unless the part says "
            + "\"round\": true; keep that for the one part whose curve is the thing itself (a scythe's blade, an orb, a "
            + "ring), never for handles and shafts. Build a long curved sheet (a blade) as several short quads parts: a part "
            + "is shaded from its own middle, and a long curved one comes out dim. Each part is one solid {form: lathe {profile [[radius,y],...] "
            + "spun round its own vertical axis, sides} | tube {path [[x,y,z],...], width or [start,end], sides} | prism "
            + "{outline [[x,z],...], height, taper} | ball {radius} | box {size [x,y,z]} | quads {quads: faces of 3 or 4 "
            + "corners [x,y,z]}; offset [x,y,z] where its own origin goes; roll, pitch, yaw: degrees it is turned (roll "
            + "-45 lays a lathe's upright axis along the diagonal from bottom left to top right); scale; material: what "
            + "it is made of, written as in your legend (darkwood, steel, gold, bone, leather, \"gem #7a5cff\", or a "
            + "#rrggbb paint). Each part is painted with its material's own Minecraft-style pixel texture at a texel to a "
            + "sixteenth (wood grain along a shaft, a metal's bands and glint, a gem's facets), so give each part the "
            + "material it is made of rather than one flat colour; parts of different materials are separate parts}. Units are sixteenths of the picture's square: "
            + "x 0-16 from its left, y 0-16 from its bottom, z -8 to 8 toward the viewer, so you can trace your picture. "
            + "Numbers only (it does not move), at most " + MAX_PARTS + " parts and " + MAX_FACES + " faces in all.";

    /** What is wrong with a shape, said to the artist. */
    public static final class Flaw extends Exception {
        private static final long serialVersionUID = 1L;

        public Flaw(String message) {
            super(message);
        }
    }

    /**
     * The parts as they will be drawn, checked: each a still solid of matter, untextured, with its colours
     * written as #rrggbb. The client mod draws exactly these (see {@link #build}).
     */
    public static JsonArray normalized(JsonArray parts) throws Flaw {
        if (parts == null || parts.isEmpty()) throw new Flaw("The shape has no parts.");
        if (parts.size() > MAX_PARTS) throw new Flaw("The shape has " + parts.size() + " parts; at most " + MAX_PARTS + ".");
        JsonArray out = new JsonArray();
        int faces = 0, n = 0;
        for (JsonElement e : parts) {
            n++;
            if (!e.isJsonObject()) throw new Flaw("Part " + n + " must be an object such as {\"form\": \"lathe\", ...}.");
            JsonObject o = e.getAsJsonObject().deepCopy();
            String material = material(o, n);
            o.remove("texture");
            o.remove("light");
            o.remove("tile");
            o.remove("color");
            boolean round = o.has("round") && o.get("round").isJsonPrimitive() && o.get("round").getAsBoolean();
            o.remove("round");
            if (!round) blocky(o);
            for (String key : FORMULAS) {
                JsonElement v = o.get(key);
                if (v != null && !(v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber())) {
                    throw new Flaw("Part " + n + ": " + key + " must be a number (a relic's shape stands still).");
                }
            }
            o.addProperty("material", material);
            o.addProperty("texture", RelicTiles.name(material));
            o.addProperty("count", 1);
            o.addProperty("delay", 0);
            o.addProperty("duration", FxSolid.MAX_TICKS);
            try {
                FxSolid.check(o);
            } catch (FxExpr.Invalid bad) {
                throw new Flaw("Part " + n + ": " + bad.getMessage() + ".");
            }
            FxSolid s = FxSolid.parse(o);
            if (s == null) throw new Flaw("Part " + n + " draws nothing.");
            faces += s.faces();
            if (faces > MAX_FACES) throw new Flaw("The shape has more than " + MAX_FACES + " faces; use fewer sides or parts.");
            float[] built = s.build(TICK, 0, 1, 1, new double[FxExpr.VARS]);
            for (int k = 0; k < built.length; k += FxSolid.VERTEX) {
                double x = built[k], y = built[k + 1], z = built[k + 2];
                if (x < -SLACK || x > 16 + SLACK || y < -SLACK || y > 16 + SLACK || Math.abs(z) > 8 + SLACK) {
                    throw new Flaw(String.format(Locale.ROOT, "Part %d reaches (%.1f, %.1f, %.1f), outside the picture's "
                            + "square (x and y 0-16, z -8 to 8).", n, x, y, z));
                }
            }
            out.add(o);
        }
        return out;
    }

    /**
     * Makes a part Minecraft-square: a lathe or tube four-sided, turned so its faces lie square with the picture
     * (an upright lathe's faces, not its corners, face front), and a ball a cube of the same width.
     */
    static void blocky(JsonObject o) {
        String form = o.has("form") && o.get("form").isJsonPrimitive() ? o.get("form").getAsString().toLowerCase(Locale.ROOT) : "";
        switch (form) {
            case "lathe" -> {
                o.addProperty("sides", 4);
                // Four sides start with a corner to the front; upright, an eighth of a turn puts a face there.
                if (!o.has("yaw") && !o.has("pitch") && !o.has("roll")) o.addProperty("yaw", 45);
            }
            case "tube" -> o.addProperty("sides", 4);
            case "ball" -> {
                double r = o.has("radius") && o.get("radius").isJsonPrimitive() ? o.get("radius").getAsDouble() : 1;
                o.remove("radius");
                o.remove("sides");
                o.addProperty("form", "box");
                o.addProperty("size", 2 * r);
            }
            default -> {
            }
        }
    }

    /**
     * What a part is made of: its "material", else its colour (the first, when it gives two), as a legend entry
     * such as "darkwood", "gem #7a5cff" or "#c9ced6"; iron when it says nothing. Checked by painting its tile.
     */
    private static String material(JsonObject o, int n) throws Flaw {
        JsonElement e = o.has("material") ? o.get("material") : o.get("color");
        if (e != null && e.isJsonArray()) e = e.getAsJsonArray().isEmpty() ? null : e.getAsJsonArray().get(0);
        String w = e != null && e.isJsonPrimitive() ? e.getAsString().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") : "";
        if (w.isEmpty()) w = "iron";
        if (w.length() > 40) throw new Flaw("Part " + n + ": material \"" + w.substring(0, 40) + "...\" is too long.");
        try {
            RelicTiles.paint(w);
        } catch (Sprite.Flaw bad) {
            throw new Flaw("Part " + n + ": material \"" + w + "\" is neither #rrggbb nor a material (gold, silver, steel, "
                    + "iron, bronze, copper, obsidian, stone, marble, bone, wood, darkwood, leather, \"gem #rrggbb\"...).");
        }
        return w;
    }

    /** Every material's tile the shape wears, by the name its parts give as "texture": ARGB, 16 x 16. */
    public static Map<String, int[]> tiles(JsonArray normalized) {
        Map<String, int[]> out = new LinkedHashMap<>();
        for (JsonElement e : normalized) {
            JsonObject o = e.getAsJsonObject();
            if (!o.has("material") || !o.has("texture")) continue;
            String name = o.get("texture").getAsString();
            if (out.containsKey(name)) continue;
            try {
                out.put(name, RelicTiles.paint(o.get("material").getAsString()));
            } catch (Sprite.Flaw bad) {
                // normalized() has painted it already.
            }
        }
        return out;
    }

    /** The shape built, with which tile each face wears (by its part's "texture"; null where it has none). */
    public record Model(float[] faces, String[] textures, Map<String, int[]> tiles) {
    }

    /** @param canvas the width of the relic's picture: its texture's texels are the picture's own size */
    public static Model model(JsonArray normalized, int canvas) {
        List<String> names = new ArrayList<>();
        float[] faces = build(normalized, canvas, names);
        return new Model(faces, names.toArray(new String[0]), tiles(normalized));
    }

    /**
     * Every face of the shape in the item's own space (blocks, centred on the item like its picture): {@link
     * FxSolid#FLOATS} floats a face (x, y, z, r, g, b, a, u, v per corner), shaded by the scenes' own sun, its u
     * and v on its part's tile as {@link #texel} lays it, its texels as large as those of a picture canvas wide.
     */
    public static float[] build(JsonArray normalized, int canvas) {
        return build(normalized, canvas, null);
    }

    private static float[] build(JsonArray normalized, int canvas, List<String> textures) {
        List<float[]> all = new ArrayList<>();
        int total = 0;
        double[] vars = new double[FxExpr.VARS];
        for (JsonElement e : normalized) {
            JsonObject o = e.getAsJsonObject();
            FxSolid s = FxSolid.parse(o);
            if (s == null) continue;
            float[] f = s.build(TICK, 0, 1 / 16.0, 1, vars);
            FxSolid still = FxSolid.parse(unplaced(o));
            if (still != null) texel(still.build(TICK, 0, 1, 1, vars), f, "quads".equals(o.get("form").getAsString()), canvas);
            for (int k = 0; k < f.length; k += FxSolid.VERTEX) {
                f[k] -= 0.5f;
                f[k + 1] -= 0.5f;
            }
            if (textures != null) {
                String name = o.has("texture") ? o.get("texture").getAsString() : null;
                for (int k = 0; k < f.length / FxSolid.FLOATS; k++) textures.add(name);
            }
            all.add(f);
            total += f.length;
        }
        float[] out = new float[total];
        int at = 0;
        for (float[] f : all) {
            System.arraycopy(f, 0, out, at, f.length);
            at += f.length;
        }
        return out;
    }

    /** A part as it is made, before it is put in its place and turned: where its texture is laid from. */
    static JsonObject unplaced(JsonObject part) {
        JsonObject o = part.deepCopy();
        for (String key : new String[] { "offset", "x", "y", "z", "yaw", "pitch", "roll" }) o.remove(key);
        return o;
    }

    /**
     * Lays the tile on a part as Minecraft lays a texture on a block: texels of one size on every face whatever
     * its shape, as large as the relic picture's own (canvas texels across the item: a texel to a sixteenth at 16,
     * two at 32), the grid running with the part (along a shaft, not across the
     * world). Each face takes the two axes of the part's own frame that it lies most across (an end of a shaft
     * x and z, its sides z or x with y), counted from the part's lowest corner; quads, which are written where
     * they stand, keep the picture's own grid, so a blade built of several quads parts reads as one sheet. v runs
     * up the part, as the picture's rows run down. local is the part built unplaced (sixteenths, {@link
     * #unplaced}), placed the same faces in their place; u, v are written into placed. The client mod lays them
     * the same way.
     */
    public static void texel(float[] local, float[] placed, boolean quads, int canvas) {
        double scale = Math.max(16, Math.min(64, canvas)) / 16.0 / RelicTiles.SIZE;
        int n = Math.min(local.length, placed.length) / FxSolid.FLOATS;
        double[] min = { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE };
        for (int k = 0; k < n * FxSolid.FLOATS; k += FxSolid.VERTEX) {
            for (int c = 0; c < 3; c++) min[c] = Math.min(min[c], local[k + c]);
        }
        if (quads) min[0] = min[1] = min[2] = 0;
        for (int f = 0; f < n; f++) {
            int at = f * FxSolid.FLOATS;
            double nx = 0, ny = 0, nz = 0;
            for (int k = 0; k < 4; k++) {
                int p = at + k * FxSolid.VERTEX, q = at + ((k + 1) & 3) * FxSolid.VERTEX;
                nx += (local[p + 1] - local[q + 1]) * (local[p + 2] + local[q + 2]);
                ny += (local[p + 2] - local[q + 2]) * (local[p] + local[q]);
                nz += (local[p] - local[q]) * (local[p + 1] + local[q + 1]);
            }
            double ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
            boolean side = ax >= ay && ax >= az, end = !side && ay >= az;
            int across = side ? 2 : 0, up = end ? 2 : 1;
            for (int k = 0; k < 4; k++) {
                int p = at + k * FxSolid.VERTEX;
                placed[p + 7] = (float) ((local[p + across] - min[across]) * scale);
                placed[p + 8] = (float) (-(local[p + up] - min[up]) * scale);
            }
        }
    }

    /** The shape from the front and turned three quarters, side by side, for proof sheets and previews. */
    public static BufferedImage picture(Model model, int px, int ground) {
        BufferedImage front = view(model, 0, 0, px, ground),
                turned = view(model, Math.toRadians(-42), Math.toRadians(20), px, ground);
        BufferedImage img = new BufferedImage(front.getWidth() + turned.getWidth() + 8, front.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(ground));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(front, 0, 0, null);
        g.drawImage(turned, front.getWidth() + 8, 0, null);
        g.dispose();
        return img;
    }

    /** One view: turned by yaw, tilted by pitch, drawn with a depth buffer, each face in its tile (nearest texel). */
    static BufferedImage view(Model model, double yaw, double pitch, int px, int ground) {
        float[] faces = model.faces();
        int size = (int) (px * 1.5);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        double[] depth = new double[size * size];
        java.util.Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        for (int i = 0; i < size * size; i++) img.setRGB(i % size, i / size, ground);
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        double[][] c = new double[4][5];
        for (int f = 0; f + FxSolid.FLOATS <= faces.length; f += FxSolid.FLOATS) {
            for (int k = 0; k < 4; k++) {
                int v = f + k * FxSolid.VERTEX;
                double x = faces[v], y = faces[v + 1], z = faces[v + 2];
                double x1 = x * cy + z * sy, z1 = -x * sy + z * cy;
                double y2 = y * cp - z1 * sp, z2 = y * sp + z1 * cp;
                c[k][0] = size / 2.0 + x1 * px;
                c[k][1] = size / 2.0 - y2 * px;
                c[k][2] = z2;
                c[k][3] = faces[v + 7];
                c[k][4] = faces[v + 8];
            }
            String name = f / FxSolid.FLOATS < model.textures().length ? model.textures()[f / FxSolid.FLOATS] : null;
            int[] tile = name == null ? null : model.tiles().get(name);
            float[] tint = { Math.min(1, faces[f + 3]), Math.min(1, faces[f + 4]), Math.min(1, faces[f + 5]) };
            tri(img, depth, c[0], c[1], c[2], tint, tile);
            tri(img, depth, c[0], c[2], c[3], tint, tile);
        }
        return img;
    }

    private static int texelColor(float[] tint, int[] tile, double u, double v) {
        int base = 0xFFFFFF;
        if (tile != null) {
            int tu = Math.floorMod((int) Math.floor(u * RelicTiles.SIZE), RelicTiles.SIZE);
            int tv = Math.floorMod((int) Math.floor(v * RelicTiles.SIZE), RelicTiles.SIZE);
            base = tile[tv * RelicTiles.SIZE + tu];
        }
        int r = Math.round(((base >> 16) & 255) * tint[0]), g = Math.round(((base >> 8) & 255) * tint[1]),
                b = Math.round((base & 255) * tint[2]);
        return r << 16 | g << 8 | b;
    }

    private static void tri(BufferedImage img, double[] depth, double[] a, double[] b, double[] c, float[] tint, int[] tile) {
        int n = img.getWidth();
        double area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
        if (Math.abs(area) < 1e-9) return;
        int minX = (int) Math.max(0, Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
        int maxX = (int) Math.min(n - 1, Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        int minY = (int) Math.max(0, Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
        int maxY = (int) Math.min(n - 1, Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5, py = y + 0.5;
                double w0 = ((b[0] - px) * (c[1] - py) - (b[1] - py) * (c[0] - px)) / area;
                double w1 = ((c[0] - px) * (a[1] - py) - (c[1] - py) * (a[0] - px)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                double z = w0 * a[2] + w1 * b[2] + w2 * c[2];
                if (z > depth[y * n + x]) {
                    depth[y * n + x] = z;
                    img.setRGB(x, y, texelColor(tint, tile, w0 * a[3] + w1 * b[3] + w2 * c[3], w0 * a[4] + w1 * b[4] + w2 * c[4]));
                }
            }
        }
    }
}
