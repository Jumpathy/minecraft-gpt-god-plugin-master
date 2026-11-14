package net.bigyous.gptgod.client.relic;

import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.bigyous.gptgod.client.fx.FxExpr;
import net.bigyous.gptgod.client.fx.FxSolid;

/**
 * A relic's true shape, as the plugin sends it in the "shape" field: solid parts in the scenes' own language
 * ({@link FxSolid}: lathe, tube, ball, box, prism, quads), standing still, in the square of the relic's
 * picture (x and y 0..16 from its bottom left, z -8..8 toward the viewer, in sixteenths). Built here exactly
 * as the plugin builds it (its RelicShape.build), into faces in the item's own space: blocks, centred on the
 * item. A part may wear a material's pixel tile (its "texture", one of the message's "textures"), laid on it
 * as {@link #texel} lays it. Pure. Anything that does not make sense gives no shape, and the relic keeps its
 * picture's form.
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
    /** A material's tile is this many texels square, and repeats. */
    public static final int TILE = 16;

    /**
     * The shape built: every face ({@link FxSolid#FLOATS} floats a face: x, y, z, r, g, b, a, u, v per corner,
     * shaded by the scenes' own sun; x, y and z in blocks from the item's centre; u and v in tiles, to be
     * repeated), the tile each face wears (by name; null for plain colour) and the tiles that came with it
     * (ARGB, {@link #TILE} square). A face may name a tile that did not come: it is drawn in its colour alone.
     */
    public record Model(float[] faces, String[] textures, Map<String, int[]> tiles) {
        public int size() {
            return faces.length / FxSolid.FLOATS;
        }
    }

    /**
     * The shape, or null when there is none or it cannot be drawn: not a list, no parts or too many, a part
     * the scene language refuses or that draws nothing, more than {@link #MAX_FACES} faces, or a corner far
     * outside the picture's square.
     *
     * @param canvas the width of the relic's picture: its tiles' texels are as large as the picture's
     * @param tiles  the tiles the message brought, by name
     */
    public static Model build(JsonElement shape, int canvas, Map<String, int[]> tiles) {
        if (shape == null || !shape.isJsonArray()) return null;
        JsonArray parts = shape.getAsJsonArray();
        if (parts.isEmpty() || parts.size() > MAX_PARTS) return null;
        try {
            float[][] built = new float[parts.size()][];
            String[] names = new String[parts.size()];
            int faces = 0, total = 0;
            double[] vars = new double[FxExpr.VARS];
            for (int i = 0; i < parts.size(); i++) {
                if (!parts.get(i).isJsonObject()) return null;
                // As the plugin makes every part: one copy, standing still all its life.
                JsonObject o = parts.get(i).getAsJsonObject().deepCopy();
                o.addProperty("count", 1);
                o.addProperty("delay", 0);
                o.addProperty("duration", FxSolid.MAX_TICKS);
                FxSolid.check(o);
                FxSolid s = FxSolid.parse(o);
                if (s == null) return null;
                faces += s.faces();
                if (faces > MAX_FACES) return null;
                float[] f = s.build(TICK, 0, 1 / 16.0, 1, vars);
                FxSolid still = FxSolid.parse(unplaced(o));
                if (still != null) texel(still.build(TICK, 0, 1, 1, vars), f, "quads".equals(s.form), canvas);
                for (int k = 0; k < f.length; k += FxSolid.VERTEX) {
                    for (int c = 0; c < 9; c++) {
                        if (!Float.isFinite(f[k + c])) return null;
                    }
                    double x = f[k] * 16, y = f[k + 1] * 16, z = f[k + 2] * 16;
                    if (x < -SLACK || x > 16 + SLACK || y < -SLACK || y > 16 + SLACK || Math.abs(z) > 8 + SLACK) return null;
                    f[k] -= 0.5f;
                    f[k + 1] -= 0.5f;
                }
                built[i] = f;
                names[i] = s.texture.isEmpty() ? null : s.texture;
                total += f.length;
            }
            if (total == 0) return null;
            float[] out = new float[total];
            String[] textures = new String[total / FxSolid.FLOATS];
            int at = 0;
            for (int i = 0; i < built.length; i++) {
                System.arraycopy(built[i], 0, out, at, built[i].length);
                java.util.Arrays.fill(textures, at / FxSolid.FLOATS, (at + built[i].length) / FxSolid.FLOATS, names[i]);
                at += built[i].length;
            }
            return new Model(out, textures, tiles == null ? Map.of() : Map.copyOf(tiles));
        } catch (FxExpr.Invalid | RuntimeException e) {
            return null;
        }
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
     * two at 32), the grid running with the part (along a shaft, not across the world). Each face takes the two
     * axes of the part's own frame that it lies most across (an end of a shaft x and z, its sides z or x with y),
     * counted from the part's lowest corner; quads, which are written where they stand, keep the picture's own
     * grid, so a blade built of several quads parts reads as one sheet. v runs up the part, as the picture's rows
     * run down. local is the part built unplaced (sixteenths, {@link #unplaced}), placed the same faces in their
     * place; u, v are written into placed. The plugin lays them the same way (its RelicShape.texel).
     */
    static void texel(float[] local, float[] placed, boolean quads, int canvas) {
        double scale = Math.max(16, Math.min(64, canvas)) / 16.0 / TILE;
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

    /** The box round the faces: min x, y, z then max x, y, z, in blocks from the item's centre. */
    public static float[] bounds(float[] faces) {
        float[] b = { Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE };
        for (int k = 0; k + 2 < faces.length; k += FxSolid.VERTEX) {
            for (int c = 0; c < 3; c++) {
                b[c] = Math.min(b[c], faces[k + c]);
                b[c + 3] = Math.max(b[c + 3], faces[k + c]);
            }
        }
        return b;
    }
}
