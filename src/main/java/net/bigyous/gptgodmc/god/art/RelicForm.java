package net.bigyous.gptgodmc.god.art;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.bigyous.gptgodmc.god.art.SpritePainter.Art;

/**
 * A relic as a solid thing. Every texel of its art is a little block as thick as the painter made it
 * (a guard thicker than its blade, a pommel rounded like a ball), so the sprite seen from the front is a
 * small sculpture from the side. This turns painted art into the boxes of that sculpture and writes them
 * as the item model a resource pack needs, and draws the form turned three quarters for proof sheets and
 * previews. Pure.
 *
 * <p>A relic is lit like any other item in the world: by the light where it is, darker at night, each face
 * shaded by the way it points. Nothing in it is forced bright, not even what is painted as light (a relic
 * that shone in the dark, with no shadow on it, looked pasted onto the world). Its walls show its painted
 * walls ({@link Art#walls}), a tone lighter on top, rather than one colour stretched over them.
 */
public final class RelicForm {
    private RelicForm() {
    }

    /** A model with more boxes than this is written as a plain flat item instead. */
    public static final int MAX_BARS = 520;

    /** A run of texels in one row that share a thickness: one box of the model (a film when thick is 0). */
    public record Bar(int x0, int x1, int row, int thick) {
    }

    /** The textures a model is drawn with: the sprite, and its painted walls ({@link #walls}; null = the sprite's own texels). */
    public record Look(String front, String walls) {
        public Look(String front) {
            this(front, null);
        }
    }

    /** How thick a texel is built, in model units (sixteenths of a block). */
    static double depth(Art art, int x, int y) {
        return art.drawn(x, y) ? art.thick(x, y) * 16.0 / art.w() : 0;
    }

    /** The boxes of the form, row by row, left to right. */
    public static List<Bar> bars(Art art) {
        List<Bar> out = new ArrayList<>();
        for (int y = 0; y < art.h(); y++) {
            int x = 0;
            while (x < art.w()) {
                if (!art.drawn(x, y)) {
                    x++;
                    continue;
                }
                int thick = art.thick(x, y), end = x + 1;
                while (end < art.w() && art.drawn(end, y) && art.thick(end, y) == thick) end++;
                out.add(new Bar(x, end, y, thick));
                x = end;
            }
        }
        return out;
    }

    /**
     * The walls texture: per frame a square twice the art's size, its quarters the walls seen from above,
     * below, the west and the east ({@link SpritePainter#UP} and so on) as top-left, top-right,
     * bottom-left and bottom-right.
     */
    public static int[][] wallFrames(Art art) {
        int w = art.w(), h = art.h(), side = 2 * w;
        int[][] out = new int[art.frames().length][];
        for (int f = 0; f < out.length; f++) {
            int[] img = new int[side * 2 * h];
            for (int wall = 0; wall < 4; wall++) {
                int ox = (wall % 2) * w, oy = (wall / 2) * h;
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if (art.drawn(x, y)) img[(oy + y) * side + ox + x] = art.wall(f, wall, x, y);
                    }
                }
            }
            out[f] = img;
        }
        return out;
    }

    // ---- The item model ---------------------------------------------------------------------------------

    /** The same as {@link #model(Art, Look, boolean)} with only the sprite (no painted walls). */
    public static String model(Art art, String texture, boolean handheld) {
        return model(art, new Look(texture), handheld);
    }

    /**
     * The item model for square art: solid boxes first (each face showing its own texels, its walls its
     * painted walls, faces buried between neighbours at least as thick left out), then films (only a front
     * and a back). It inherits how it is held and shown from Minecraft's own sword (handheld) or plain item
     * models. Null when the form has too many boxes; the caller then falls back to the flat model.
     */
    public static String model(Art art, Look look, boolean handheld) {
        if (art.w() != art.h()) throw new IllegalArgumentException("a model needs square art");
        List<Bar> bars = bars(art);
        if (bars.isEmpty() || bars.size() > MAX_BARS) return null;
        double unit = 16.0 / art.w();
        List<String> els = new ArrayList<>();
        for (boolean solids : new boolean[] { true, false }) {
            for (Bar b : bars) {
                if ((b.thick() > 0) == solids) els.add(element(art, look, b, unit));
            }
        }
        StringBuilder textures = new StringBuilder("\"layer0\": \"" + look.front() + "\", \"particle\": \"" + look.front()
                + "\"");
        if (look.walls() != null) textures.append(", \"walls\": \"").append(look.walls()).append("\"");
        return "{\n  \"parent\": \"minecraft:item/" + (handheld ? "handheld" : "generated") + "\",\n  \"textures\": { "
                + textures + " },\n  \"elements\": [\n" + String.join(",\n", els) + "\n  ]\n}\n";
    }

    private static String element(Art art, Look look, Bar b, double unit) {
        double x0 = b.x0() * unit, x1 = b.x1() * unit, y1 = 16 - b.row() * unit, y0 = y1 - unit;
        double deep = b.thick() * unit, half = deep / 2;
        // Texture coordinates of the run in the sprite (u across, v down), in sixteenths.
        String u0 = num(x0), u1 = num(x1), v0 = num(b.row() * unit), v1 = num((b.row() + 1) * unit);
        List<String> faces = new ArrayList<>();
        faces.add(face("south", u0, v0, u1, v1, "layer0"));
        faces.add(face("north", u1, v0, u0, v1, "layer0"));
        if (deep > 0) {
            if (!covered(art, b.x0() - 1, b.x0(), b.row(), deep)) {
                faces.add(wall(look, "west", SpritePainter.WEST, b.x0(), b.x0() + 1, b.row(), art.w()));
            }
            if (!covered(art, b.x1(), b.x1() + 1, b.row(), deep)) {
                faces.add(wall(look, "east", SpritePainter.EAST, b.x1() - 1, b.x1(), b.row(), art.w()));
            }
            if (!covered(art, b.x0(), b.x1(), b.row() - 1, deep)) {
                faces.add(wall(look, "up", SpritePainter.UP, b.x0(), b.x1(), b.row(), art.w()));
            }
            if (!covered(art, b.x0(), b.x1(), b.row() + 1, deep)) {
                faces.add(wall(look, "down", SpritePainter.DOWN, b.x0(), b.x1(), b.row(), art.w()));
            }
        }
        return "    { \"from\": [" + num(x0) + ", " + num(y0) + ", " + num(8 - half) + "], \"to\": [" + num(x1) + ", "
                + num(y1) + ", " + num(8 + half) + "], \"faces\": { " + String.join(", ", faces) + " } }";
    }

    /** A wall: the texels [x0, x1) of one row, from the painted walls when there are any, else from the sprite. */
    private static String wall(Look look, String side, int which, int x0, int x1, int row, int w) {
        if (look.walls() == null) {
            double unit = 16.0 / w;
            return face(side, num(x0 * unit), num(row * unit), num(x1 * unit), num((row + 1) * unit), "layer0");
        }
        // The walls texture is twice as wide and twice as tall: one quarter for each way a wall faces.
        double unit = 8.0 / w, ox = (which % 2) * 8, oy = (which / 2) * 8;
        return face(side, num(ox + x0 * unit), num(oy + row * unit), num(ox + x1 * unit), num(oy + (row + 1) * unit),
                "walls");
    }

    /** True when every texel of columns [x0, x1) in a row is built at least this deep (so a face against them is buried). */
    private static boolean covered(Art art, int x0, int x1, int row, double deep) {
        for (int x = x0; x < x1; x++) {
            if (depth(art, x, row) < deep - 1e-9) return false;
        }
        return true;
    }

    private static String face(String side, String u0, String v0, String u1, String v1, String texture) {
        return "\"" + side + "\": { \"uv\": [" + u0 + ", " + v0 + ", " + u1 + ", " + v1 + "], \"texture\": \"#" + texture
                + "\" }";
    }

    private static String num(double v) {
        String s = String.format(Locale.ROOT, "%.4f", v).replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    // ---- Seen from three quarters -----------------------------------------------------------------------

    /**
     * The form turned to the right and seen a little from above: what the thing is as an object, which the
     * front view cannot show. Faces show their painted colours (walls their own tones), shaded by which
     * way they point, as the game shades an item.
     *
     * @param px     screen pixels per texel
     * @param ground background color
     */
    public static BufferedImage turned(Art art, int frame, int px, int ground) {
        double yaw = Math.toRadians(-42), pitch = Math.toRadians(20);
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        int deepest = 1;
        for (byte d : art.depth()) deepest = Math.max(deepest, d);
        int width = (int) Math.ceil((art.w() * Math.abs(cy) + deepest * Math.abs(sy)) * px) + 2 * px;
        int height = (int) Math.ceil((art.h() * cp + (art.w() * Math.abs(sy) + deepest * Math.abs(cy)) * sp) * px) + 2 * px;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(ground));
        g.fillRect(0, 0, width, height);
        record Box(int x, int y, double half, double depth) {
        }
        List<Box> boxes = new ArrayList<>();
        for (int y = 0; y < art.h(); y++) {
            for (int x = 0; x < art.w(); x++) {
                if (!art.drawn(x, y)) continue;
                double mx = x + 0.5 - art.w() / 2.0, my = art.h() / 2.0 - (y + 0.5);
                boxes.add(new Box(x, y, art.thick(x, y) / 2.0, (-mx * sy) * cp + my * sp));
            }
        }
        boxes.sort(Comparator.comparingDouble(Box::depth));
        // Corners of a unit box (x, y, z each 0 or 1) per face, which painted colour it shows, and how bright.
        int[][][] faces = { { { 0, 0, 1 }, { 1, 0, 1 }, { 1, 1, 1 }, { 0, 1, 1 } }, { { 1, 0, 0 }, { 0, 0, 0 }, { 0, 1, 0 }, { 1, 1, 0 } },
                { { 1, 0, 1 }, { 1, 0, 0 }, { 1, 1, 0 }, { 1, 1, 1 } }, { { 0, 0, 0 }, { 0, 0, 1 }, { 0, 1, 1 }, { 0, 1, 0 } },
                { { 0, 1, 1 }, { 1, 1, 1 }, { 1, 1, 0 }, { 0, 1, 0 } }, { { 0, 0, 0 }, { 1, 0, 0 }, { 1, 0, 1 }, { 0, 0, 1 } } };
        double[][] normals = { { 0, 0, 1 }, { 0, 0, -1 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 } };
        int[] wallOf = { -1, -1, SpritePainter.EAST, SpritePainter.WEST, SpritePainter.UP, SpritePainter.DOWN };
        double[] shade = { 1.0, 0.7, 0.8, 0.8, 1.0, 0.6 };
        for (Box b : boxes) {
            int face = art.at(frame, b.x(), b.y());
            double alpha = (face >>> 24) / 255.0;
            boolean film = b.half() == 0;
            for (int f = 0; f < 6; f++) {
                if (film && f != 0) continue;
                double[] n = normals[f];
                double nz1 = -n[0] * sy + n[2] * cy;
                if (!film && n[1] * sp + nz1 * cp <= 0) continue;
                Polygon poly = new Polygon();
                for (int[] c : faces[f]) {
                    double mx = b.x() + c[0] - art.w() / 2.0, my = art.h() / 2.0 - (b.y() + 1) + c[1];
                    double mz = (c[2] * 2 - 1) * b.half();
                    double x1 = mx * cy + mz * sy, z1 = -mx * sy + mz * cy;
                    double y2 = my * cp - z1 * sp;
                    poly.addPoint((int) Math.round(width / 2.0 + x1 * px), (int) Math.round(height / 2.0 - y2 * px));
                }
                int argb = wallOf[f] < 0 ? face : art.wall(frame, wallOf[f], b.x(), b.y());
                double k = shade[f];
                int r = (int) Math.min(255, ((argb >> 16) & 255) * k), gr = (int) Math.min(255, ((argb >> 8) & 255) * k),
                        bl = (int) Math.min(255, (argb & 255) * k);
                g.setColor(new Color(r, gr, bl, (int) Math.round(255 * alpha)));
                g.fillPolygon(poly);
                if (alpha >= 1) g.drawPolygon(poly);
            }
        }
        g.dispose();
        return img;
    }
}
