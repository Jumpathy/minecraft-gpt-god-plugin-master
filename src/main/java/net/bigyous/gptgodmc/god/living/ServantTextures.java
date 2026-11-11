package net.bigyous.gptgodmc.god.living;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.bigyous.gptgodmc.god.living.ServantSkin.Layout;
import net.bigyous.gptgodmc.god.living.ServantSkin.Region;
import net.bigyous.gptgodmc.god.living.ServantSkin.Shape;
import net.bigyous.gptgodmc.god.vessel.VesselSkin.Face;
import net.bigyous.gptgodmc.god.vessel.VesselSkin.Rect;

/**
 * Paints the servants' own skins, one animated atlas per kind, from code (no image files; the same
 * pixels every run, so the pack's hash is stable). This class is the canvas and the brushes (noise,
 * cloth, gold, runes, feathers, membranes, soft light); {@link ServantPaintHoly} and
 * {@link ServantPaintDark} are the pictures. Colors are ARGB: alpha cuts out wing and cloak shapes and
 * lets hems, mist and light fade away.
 */
public final class ServantTextures {
    /** One kind's painted frames (each a whole atlas laid out by {@link ServantSkin#layout}). */
    public record Atlas(ServantKind kind, Layout layout, BufferedImage[] frames) {
        /** The ARGB texel at face coordinates (s across, t down) of a region. */
        public int sample(String key, Face face, double s, double t, int frame) {
            Rect r = layout.region(key).face(face);
            int x = r.x() + (int) Math.min(r.w() - 1, Math.max(0, Math.floor(s * r.w())));
            int y = r.y() + (int) Math.min(r.h() - 1, Math.max(0, Math.floor(t * r.h())));
            return frames[Math.floorMod(frame, frames.length)].getRGB(x, y);
        }
    }

    private static final Map<ServantKind, Atlas> CACHE = new ConcurrentHashMap<>();

    private ServantTextures() {
    }

    public static Atlas atlas(ServantKind k) {
        return CACHE.computeIfAbsent(k, ServantTextures::paint);
    }

    @FunctionalInterface
    interface Painter {
        /** The ARGB color of a texel. */
        int paint(P p);
    }

    static Atlas paint(ServantKind k) {
        Layout layout = ServantSkin.layout(k);
        Painter painter = switch (k) {
            case ANGEL -> ServantPaintHoly::angel;
            case PRIEST -> ServantPaintHoly::priest;
            case GUARD -> ServantPaintHoly::guard;
            case IMP -> ServantPaintDark::imp;
            case SHADE -> ServantPaintDark::shade;
        };
        BufferedImage[] frames = new BufferedImage[ServantSkin.FRAMES];
        for (int f = 0; f < frames.length; f++) {
            BufferedImage img = new BufferedImage(layout.width(), layout.height(), BufferedImage.TYPE_INT_ARGB);
            for (Region region : layout.regions().values()) {
                if (region.def().shape() == Shape.BOX) {
                    for (Face face : Face.values()) paintFace(img, k, region, face, f, painter);
                } else {
                    paintFace(img, k, region, Face.SOUTH, f, painter);
                }
                fillGutter(img, region);
            }
            frames[f] = img;
        }
        return new Atlas(k, layout, frames);
    }

    /** One texel being painted: where it is on its face and on its piece, and the animation phase. */
    static final class P {
        ServantKind kind;
        String key;
        Shape shape;
        Face face;
        /** Integer texel on the face (v counts down from the top), and the face's size. */
        int u, v, fw, fh;
        /** 0..1 across and down the face, at the texel's center. */
        double s, t;
        /** The texel's point on the piece, in texels: x across (0..w), y up (0..h), z toward the front (0..d). */
        double x, y, z;
        /** The piece's size in texels. */
        int bw, bh, bd;
        int frame;
        /** 0..2pi through the animation loop. */
        double phase;

        boolean front() {
            return face == Face.SOUTH;
        }

        boolean back() {
            return face == Face.NORTH;
        }

        boolean top() {
            return face == Face.UP;
        }

        boolean bottom() {
            return face == Face.DOWN;
        }

        boolean side() {
            return face != Face.UP && face != Face.DOWN;
        }

        /** Texels down from the top of the piece (side faces), or down the face. */
        double down() {
            return side() ? bh - y : v + 0.5;
        }

        /** Texels along the piece's girth, continuous round its four sides (front, left, back, right). */
        double around() {
            return switch (face) {
                case SOUTH -> x;
                case EAST -> bw + (bd - z);
                case NORTH -> bw + bd + (bw - x);
                case WEST -> 2 * bw + bd + z;
                default -> x;
            };
        }

        /** Texels to the nearest edge of the face. */
        int edge() {
            return Math.min(Math.min(u, fw - 1 - u), Math.min(v, fh - 1 - v));
        }

        /** A smooth 0..1 pulse through the loop, offset by a fraction of a turn. */
        double pulse(double offset) {
            return 0.5 + 0.5 * Math.sin(phase + offset * Math.PI * 2);
        }

        /** Looping drift for animated noise. */
        double dx(double amount) {
            return Math.cos(phase) * amount;
        }

        double dy(double amount) {
            return Math.sin(phase) * amount;
        }
    }

    private static void paintFace(BufferedImage img, ServantKind kind, Region region, Face face, int frame,
            Painter painter) {
        Rect r = region.face(face);
        P p = new P();
        p.kind = kind;
        p.key = region.def().key();
        p.shape = region.def().shape();
        p.face = face;
        p.fw = r.w();
        p.fh = r.h();
        p.bw = region.def().w();
        p.bh = region.def().h();
        p.bd = Math.max(1, region.def().d());
        p.frame = frame;
        p.phase = Math.PI * 2 * frame / ServantSkin.FRAMES;
        for (int v = 0; v < r.h(); v++) {
            for (int u = 0; u < r.w(); u++) {
                p.u = u;
                p.v = v;
                p.s = (u + 0.5) / r.w();
                p.t = (v + 0.5) / r.h();
                if (p.shape == Shape.BOX) {
                    float[] q = face.point(p.s, p.t);
                    p.x = q[0] * p.bw;
                    p.y = q[1] * p.bh;
                    p.z = q[2] * p.bd;
                } else {
                    p.x = u + 0.5;
                    p.y = r.h() - (v + 0.5);
                    p.z = 0;
                }
                int c = painter.paint(p);
                if ((c >>> 24) != 0) img.setRGB(r.x() + u, r.y() + v, c);
            }
        }
    }

    /**
     * Copies each face's border texels outward into the gutter round the region, so mipmaps do not pull
     * in a neighbor's colors. Never writes inside the region itself: a face left see-through stays so.
     */
    private static void fillGutter(BufferedImage img, Region region) {
        int g = ServantSkin.GUTTER;
        Face[] faces = region.def().shape() == Shape.BOX ? Face.values() : new Face[] { Face.SOUTH };
        for (Face face : faces) {
            Rect r = region.face(face);
            for (int i = 1; i <= g; i++) {
                for (int u = 0; u < r.w(); u++) {
                    set(img, region, r.x() + u, r.y() - i, img.getRGB(r.x() + u, r.y()));
                    set(img, region, r.x() + u, r.y() + r.h() - 1 + i, img.getRGB(r.x() + u, r.y() + r.h() - 1));
                }
                for (int v = -i; v < r.h() + i; v++) {
                    int vv = Math.max(0, Math.min(r.h() - 1, v));
                    set(img, region, r.x() - i, r.y() + v, img.getRGB(r.x(), r.y() + vv));
                    set(img, region, r.x() + r.w() - 1 + i, r.y() + v, img.getRGB(r.x() + r.w() - 1, r.y() + vv));
                }
            }
        }
    }

    private static void set(BufferedImage img, Region region, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return;
        if (x >= region.x() && x < region.x() + region.width() && y >= region.y()
                && y < region.y() + region.height()) {
            // Inside the region: only the unused corners of a box's unwrap may be filled.
            Face[] faces = region.def().shape() == Shape.BOX ? Face.values() : new Face[] { Face.SOUTH };
            for (Face f : faces) {
                Rect r = region.face(f);
                if (x >= r.x() && x < r.x() + r.w() && y >= r.y() && y < r.y() + r.h()) return;
            }
        }
        if ((img.getRGB(x, y) >>> 24) == 0 && (argb >>> 24) != 0) img.setRGB(x, y, argb);
    }

    // ---- Color ---------------------------------------------------------------------------------------

    static final int CLEAR = 0;

    static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    static int argb(double alpha, int rgb) {
        int a = (int) Math.round(Math.max(0, Math.min(255, alpha)));
        return a == 0 ? CLEAR : (a << 24) | (rgb & 0xFFFFFF);
    }

    static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    static int mix(int a, int b, double t) {
        double f = clamp(t);
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * f);
        int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * f);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * f);
        return (r << 16) | (g << 8) | bl;
    }

    /** Brighter (f over 1) or darker. */
    static int mul(int rgb, double f) {
        int r = (int) Math.min(255, Math.round(((rgb >> 16) & 255) * f));
        int g = (int) Math.min(255, Math.round(((rgb >> 8) & 255) * f));
        int b = (int) Math.min(255, Math.round((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    /** Light added on top of a color. */
    static int lit(int rgb, int light, double t) {
        double f = Math.max(0, t);
        int r = (int) Math.min(255, Math.round(((rgb >> 16) & 255) + ((light >> 16) & 255) * f));
        int g = (int) Math.min(255, Math.round(((rgb >> 8) & 255) + ((light >> 8) & 255) * f));
        int b = (int) Math.min(255, Math.round((rgb & 255) + (light & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    // ---- Noise ---------------------------------------------------------------------------------------

    static double hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    static double noise(double x, double y, int seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(x0, y0, seed), b = hash(x0 + 1, y0, seed), c = hash(x0, y0 + 1, seed),
                d = hash(x0 + 1, y0 + 1, seed);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    static double fbm(double x, double y, int seed) {
        return 0.5 * noise(x, y, seed) + 0.3 * noise(x * 2.03, y * 2.03, seed + 7)
                + 0.2 * noise(x * 4.1, y * 4.1, seed + 13);
    }

    static double segDist(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double t = clamp(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy));
        return Math.hypot(px - ax - dx * t, py - ay - dy * t);
    }

    // ---- Materials -----------------------------------------------------------------------------------

    /** Gold, from shadow to glint. */
    static final int G0 = 0x74490F, G1 = 0xC48B28, G2 = 0xF0C552, G3 = 0xFFF1B4;

    /** Gold at a brightness 0 (deep shadow) .. 1 (glint). */
    static int gold(double t) {
        double f = clamp(t) * 3;
        return f < 1 ? mix(G0, G1, f) : f < 2 ? mix(G1, G2, f - 1) : mix(G2, G3, f - 2);
    }

    /** Beaten gold: brighter above, with a soft grain and travelling glints. */
    static int goldPlate(P p, int seed) {
        double g = 0.62 - 0.3 * p.t + 0.22 * (noise(p.around() * 0.6, p.down() * 0.6, seed) - 0.5);
        double glint = Math.sin((p.around() + p.down()) * 0.45 - p.phase);
        if (glint > 0.93) g += 0.28;
        return gold(g);
    }

    /** Cloth that falls in folds round the piece, between a light and a shadow color. */
    static int cloth(P p, int light, int dark, int seed) {
        double a = p.around(), d = p.down();
        double f = 0.5 + 0.5 * Math.sin(a * 0.62 + 1.5 * Math.sin(d * 0.11 + seed) + seed * 1.7);
        f = 0.7 * f + 0.3 * noise(a * 0.45, d * 0.16, seed);
        if (!p.side()) f = 0.55 + 0.25 * noise(p.u * 0.5, p.v * 0.5, seed);
        int c = mix(dark, light, f);
        return ((p.u + p.v) & 1) == 0 ? mul(c, 0.97) : c;
    }

    /** Polished marble: pale cloud, a carved bevel at each face's edges, and thin veins of gold. */
    static int marble(P p, int seed) {
        double n = fbm(p.around() * 0.14, p.down() * 0.14, seed);
        int c = mix(0xC9C6D3, 0xF3F1EC, 0.25 + 0.75 * n);
        double vein = Math.abs(noise(p.around() * 0.07 + 3, p.down() * 0.07 + p.around() * 0.05, seed + 31) - 0.5);
        if (vein < 0.007) c = mix(c, 0xD9A53A, 0.85);
        else if (vein < 0.016) c = mix(c, 0xA9A3B8, 0.3);
        if (p.side()) {
            if (p.v == 0 || p.u == 0) c = mix(c, 0xFFFFFF, 0.55);
            if (p.v == p.fh - 1 || p.u == p.fw - 1) c = mix(c, 0x8D889C, 0.6);
        }
        return c;
    }

    /** A sky of night: dark weave, drifting violet nebula and a few stars that twinkle. */
    static int night(P p, int seed) {
        double a = p.around(), d = p.down();
        double fold = 0.5 + 0.5 * Math.sin(a * 0.55 + 1.4 * Math.sin(d * 0.09 + seed) + seed);
        int c = mix(0x07060D, 0x191430, 0.2 + 0.8 * fold * (0.6 + 0.4 * noise(a * 0.4, d * 0.2, seed)));
        double neb = fbm(a * 0.11 + p.dx(0.6), d * 0.11 + p.dy(0.6), seed + 5);
        c = mix(c, 0x4A3486, clamp((neb - 0.5) * 2.2) * 0.55);
        double star = hash((int) Math.floor(a), (int) Math.floor(d), seed + 9);
        if (star > 0.982) c = mix(c, 0xD8EEFF, 0.35 + 0.65 * p.pulse(star * 37));
        return c;
    }

    // ---- Runes ---------------------------------------------------------------------------------------

    /** Eight invented letters, 3 wide and 5 tall (bits row by row, left to right). */
    private static final int[] RUNES = { 0b110100110100100, 0b101101010010010, 0b010101101101010, 0b010011110010010,
            0b101111101101101, 0b010101010101101, 0b100110011001001, 0b001010100010001 };

    static boolean rune(int id, int gx, int gy) {
        if (gx < 0 || gx > 2 || gy < 0 || gy > 4) return false;
        return ((RUNES[Math.floorMod(id, RUNES.length)] >> (14 - (gy * 3 + gx))) & 1) == 1;
    }

    /**
     * A column of letters down a face, centered on column cx, starting at row top. Returns -1 off a
     * letter, else which letter in the column the texel belongs to (for pulsing them in turn).
     */
    static int runeColumn(P p, int cx, int top, int count, int seed) {
        int gy = p.v - top;
        if (gy < 0) return -1;
        int index = gy / 7;
        if (index >= count) return -1;
        return rune((int) (hash(index, seed, 3) * 8), p.u - (cx - 1), gy % 7) ? index : -1;
    }

    // ---- Light ---------------------------------------------------------------------------------------

    /** A soft round light that fades to nothing at the edge of its square, with faint rays. */
    static int halo(P p, int core, int rim, double strength, int rays) {
        double cx = p.fw / 2.0, cy = p.fh / 2.0;
        double dx = (p.u + 0.5 - cx) / cx, dy = (p.v + 0.5 - cy) / cy;
        double r = Math.hypot(dx, dy);
        if (r >= 1) return CLEAR;
        double a = (1 - r) * (1 - r);
        if (rays > 0) a *= 0.72 + 0.28 * Math.cos(Math.atan2(dy, dx) * rays + p.phase);
        a *= 0.85 + 0.15 * p.pulse(0);
        return argb(255 * strength * a, mix(core, rim, r));
    }

    // ---- Feathers ------------------------------------------------------------------------------------

    /** A feather from its quill (bx, by) to its tip, in texels; w is half its width; row picks its colors. */
    record Feather(double bx, double by, double tx, double ty, double w, int row) {
    }

    /**
     * The topmost feather over a texel (later feathers lie over earlier ones). tones[row] is {light vane,
     * shadow vane, shaft, tip light}; tips fade to tipAlpha so the wing's edge is half light.
     */
    static int feathers(P p, List<Feather> fs, int[][] tones, double tipAlpha) {
        double px = p.u + 0.5, py = p.v + 0.5;
        for (int i = fs.size() - 1; i >= 0; i--) {
            Feather f = fs.get(i);
            double dx = f.tx - f.bx, dy = f.ty - f.by, len = Math.hypot(dx, dy);
            double ax = dx / len, ay = dy / len;
            double rx = px - f.bx, ry = py - f.by;
            double a = (rx * ax + ry * ay) / len;
            if (a < -0.02 || a > 1) continue;
            double d = -rx * ay + ry * ax;
            double q = (a - 0.7) / 0.3;
            double prof = a < 0.15 ? 0.45 + 0.55 * a / 0.15 : a > 0.7 ? Math.sqrt(Math.max(0, 1 - q * q)) : 1;
            double hw = f.w * prof;
            if (Math.abs(d) > hw) continue;
            int[] tone = tones[f.row];
            int c = mix(tone[1], tone[0], clamp(0.3 + 0.7 * (d / f.w * 0.5 + 0.5)));
            if (Math.floorMod((int) Math.floor(a * len * 0.9 + Math.abs(d) * 0.9), 3) == 0) c = mul(c, 0.93);
            if (Math.abs(d) < 0.55) c = tone[2];
            if (hw - Math.abs(d) < 0.75) c = mix(c, tone[1], 0.6);
            double tip = clamp((a - 0.55) / 0.45);
            c = mix(c, tone[3], tip * 0.85);
            c = mix(c, tone[3], 0.25 * p.pulse(a * 0.8 + i * 0.07) * tip);
            return argb(255 - (255 - tipAlpha) * tip * tip, c);
        }
        return CLEAR;
    }

    /** Feathers hanging from a line of quills: n of them from (x0, y0) to (x1, y1), fanning from angle a0 to a1. */
    static void fan(List<Feather> out, int n, double x0, double y0, double x1, double y1, double a0, double a1,
            double len0, double len1, double bulge, double w, int row, double maxX, double maxY) {
        for (int i = 0; i < n; i++) {
            double f = n == 1 ? 0 : i / (double) (n - 1);
            double bx = x0 + (x1 - x0) * f, by = y0 + (y1 - y0) * f;
            double ang = Math.toRadians(a0 + (a1 - a0) * f);
            double sin = Math.sin(ang), cos = Math.cos(ang);
            double len = len0 + (len1 - len0) * f + bulge * Math.sin(Math.PI * f);
            if (sin > 1e-3) len = Math.min(len, (maxX - bx) / sin);
            if (cos > 1e-3) len = Math.min(len, (maxY - by) / cos);
            out.add(new Feather(bx, by, bx + sin * len, by + cos * len, w, row));
        }
    }

    // ---- Membranes -----------------------------------------------------------------------------------

    /**
     * A wing of skin stretched between finger bones that radiate from a joint (jx, jy) to tips given in
     * order round the joint; the edge between two tips sags in a scallop, and the skin is torn in places.
     * colors: {bone, bone light, skin shadow, skin lit, vein}.
     */
    static int membrane(P p, double jx, double jy, double[][] tips, int[] colors, double alpha, int seed) {
        double px = p.u + 0.5, py = p.v + 0.5;
        double bone = 1e9;
        for (double[] t : tips) bone = Math.min(bone, segDist(px, py, jx, jy, t[0], t[1]));
        if (bone < 0.9) return opaque(bone < 0.45 ? colors[1] : colors[0]);
        double ang = Math.atan2(py - jy, px - jx), r = Math.hypot(px - jx, py - jy);
        for (int i = 0; i + 1 < tips.length; i++) {
            double a0 = Math.atan2(tips[i][1] - jy, tips[i][0] - jx);
            double a1 = Math.atan2(tips[i + 1][1] - jy, tips[i + 1][0] - jx);
            if (ang < a0 || ang > a1) continue;
            double f = (ang - a0) / (a1 - a0);
            double r0 = Math.hypot(tips[i][0] - jx, tips[i][1] - jy);
            double r1 = Math.hypot(tips[i + 1][0] - jx, tips[i + 1][1] - jy);
            double edge = (r0 + (r1 - r0) * f) * (1 - 0.27 * Math.sin(Math.PI * f));
            if (r > edge) return CLEAR;
            double n = fbm(px * 0.42, py * 0.42, seed);
            double out = r / edge;
            if (out > 0.5 && n > 0.72 + 0.1 * (1 - out)) return CLEAR;
            int c = mix(colors[2], colors[3], clamp(0.15 + 0.75 * out * (0.55 + 0.45 * n)));
            c = lit(c, colors[4], 0.16 * p.pulse(out + i * 0.3) * out);
            if (Math.abs(Math.sin(f * Math.PI * 3)) < 0.11 && out > 0.18) c = mix(c, colors[4], 0.5);
            if (r > edge - 1.1) c = mix(c, colors[0], 0.65);
            return argb(alpha, c);
        }
        return CLEAR;
    }
}
