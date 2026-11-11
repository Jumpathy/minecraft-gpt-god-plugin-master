package net.bigyous.gptgodmc.god.vessel;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The true form's textures, painted from code (deterministic, the same pixels every run): eyes open and
 * shut, the Watcher's ball of void, iris wheels, bar pupil and lesser eyes, a feather of light, engraved
 * gold, living coal and solid light for the solid pieces (animated), and soft glows.
 * Used by {@link TrueFormPack} and by the offline previews.
 */
public final class TrueFormTextures {
    /** Every texture key, in pack order. */
    public static final List<String> KEYS = List.of("eye_open", "eye_shut", "sclera", "lid", "iris", "feather", "pupil",
            "eye_void", "eye_void_shut", "iris_ring", "glow", "glow_cold");
    /** How much of the sclera / lid texture the ball fills; the rest is its corona. */
    public static final double BALL_FILL = 0.8;
    /** Textures for solid pieces (a box wearing the texture on every side), in pack order. */
    public static final List<String> BOXES = List.of("gold", "ember", "light");
    /** How much of an eye_open / eye_shut texture the eye itself fills; the rest is its glow. */
    public static final double EYE_FILL = 0.7;
    /** Frames of the animated feather, and ticks per frame. */
    public static final int FEATHER_FRAMES = 4;
    public static final int FEATHER_FRAME_TICKS = 3;

    private static final Map<String, BufferedImage[]> CACHE = new ConcurrentHashMap<>();

    private TrueFormTextures() {
    }

    /** The frames of a texture (one for a still texture). */
    public static BufferedImage[] frames(String key) {
        return CACHE.computeIfAbsent(key, TrueFormTextures::paint);
    }

    /** The first frame of a texture. */
    public static BufferedImage image(String key) {
        return frames(key)[0];
    }

    private static BufferedImage[] paint(String key) {
        return switch (key) {
            case "eye_open" -> one(eye(true));
            case "eye_shut" -> one(eye(false));
            case "sclera" -> one(voidBall(true));
            case "lid" -> one(voidBall(false));
            case "iris_ring" -> one(irisRing());
            case "iris" -> one(iris());
            case "pupil" -> one(pupil());
            case "eye_void" -> one(eyeVoid(true));
            case "eye_void_shut" -> one(eyeVoid(false));
            case "glow" -> one(glow(0xFFE2A8, 0.3));
            case "glow_cold" -> one(glow(0xB88AFF, 0.34));
            case "gold" -> new BufferedImage[] { gold(0), gold(1), gold(2), gold(3) };
            case "ember" -> new BufferedImage[] { ember(0), ember(1), ember(2), ember(3) };
            case "light" -> new BufferedImage[] { light(0), light(1), light(2), light(3) };
            case "feather" -> {
                BufferedImage[] f = new BufferedImage[FEATHER_FRAMES];
                for (int i = 0; i < f.length; i++) f[i] = feather(i);
                yield f;
            }
            default -> throw new IllegalArgumentException("no true form texture " + key);
        };
    }

    private static BufferedImage[] one(BufferedImage img) {
        return new BufferedImage[] { img };
    }

    // ---- Eyes -------------------------------------------------------------------------------------

    /**
     * An eye of light, 64x64: dark bronze lids, a sclera of shadow, and an iris that burns from gold to
     * white round a black slit; or shut, a seam of light. Its glow spills past the lids (soft alpha).
     */
    static BufferedImage eye(boolean open) {
        int n = 64;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < n; py++) {
            for (int px = 0; px < n; px++) {
                double u = (px + 0.5 - n / 2.0) / (n / 2.0) / EYE_FILL;
                double v = (py + 0.5 - n / 2.0) / (n / 2.0) / EYE_FILL;
                double edge = 0.66 * (1 - u * u);
                if (Math.abs(v) > edge || Math.abs(u) > 1) {
                    // Outside the lids: light spilling out, fading.
                    double d = Math.abs(u) < 1 ? Math.abs(v) - edge : Math.hypot(Math.abs(u) - 1, v);
                    double g = Math.max(0, 1 - d / 0.38);
                    int alpha = (int) Math.round((open ? 150 : 70) * g * g);
                    if (alpha > 2) img.setRGB(px, py, (alpha << 24) | 0xFFD98A);
                    continue;
                }
                double rim = edge - Math.abs(v);
                double r = Math.hypot(u, v);
                int rgb;
                if (rim < 0.085) {
                    rgb = mix(0x2A1A06, 0xD8A63A, rim / 0.085);
                } else if (open) {
                    if (r < 0.46) {
                        double a = Math.atan2(v, u);
                        double fib = 0.5 + 0.5 * Math.sin(a * 31 + 3 * Math.sin(a * 5) + r * 11);
                        rgb = mix(0xFFF6DC, 0xE0A830, Math.pow(r / 0.46, 0.8));
                        rgb = mix(rgb, 0x8A5A10, 0.3 * fib * (r / 0.46));
                        if (r > 0.41) rgb = mix(rgb, 0x2A1A06, (r - 0.41) / 0.05 * 0.8);
                        double slit = 0.06 * Math.sqrt(Math.max(0, 1 - (v / 0.4) * (v / 0.4)));
                        if (Math.abs(u) < slit) rgb = 0x050302;
                        else if (Math.abs(u) < slit + 0.02) rgb = mix(rgb, 0xFFFFFF, 0.6);
                    } else {
                        rgb = mix(0x4A3410, 0x0E0904, Math.min(1, (r - 0.46) / 0.28));
                        double vein = Math.abs(Math.sin(Math.atan2(v, u) * 7 + r * 9));
                        if (vein < 0.06) rgb = mix(rgb, 0xA07820, 0.55);
                    }
                } else {
                    rgb = mix(0x7A5A1E, 0x2A1A06, (v + edge) / (2 * edge));
                    rgb = mix(rgb, 0x1A1004, 0.25 * noise(px, py));
                    double seam = Math.abs(v - 0.04);
                    if (seam < 0.045) rgb = mix(0xFFF6DC, 0xF2CF6A, Math.abs(u));
                    else if (seam < 0.14) rgb = mix(rgb, 0xF2CF6A, 0.5 * (1 - (seam - 0.045) / 0.095));
                }
                img.setRGB(px, py, 0xFF000000 | rgb);
            }
        }
        return img;
    }

    /**
     * The Watcher's eyeball, 64x64: not flesh. A ball of void with violet currents and far stars in it, its
     * limb lit like an eclipse, a corona of rays beyond (soft alpha). Shut: the same ball, dark, with one
     * seam of light across it.
     */
    static BufferedImage voidBall(boolean open) {
        int n = 64;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < n; py++) {
            for (int px = 0; px < n; px++) {
                double u = (px + 0.5 - n / 2.0) / (n / 2.0) / BALL_FILL, v = (py + 0.5 - n / 2.0) / (n / 2.0) / BALL_FILL;
                double r = Math.hypot(u, v);
                double a = Math.atan2(v, u);
                if (r >= 1) {
                    double rays = 0.55 + 0.45 * Math.sin(a * 17 + 2 * Math.sin(a * 5));
                    double g = Math.max(0, 1 - (r - 1) / (1 / BALL_FILL - 1));
                    int alpha = (int) Math.round((open ? 210 : 90) * g * g * rays);
                    if (alpha > 2) img.setRGB(px, py, (alpha << 24) | mix(0xF4F0FF, 0xB070FF, 1 - g));
                    continue;
                }
                double swirl = 0.5 + 0.5 * Math.sin(u * 5 + 3 * Math.sin(v * 4 + u * 2) + r * 6);
                int rgb = mix(0x050308, 0x2A1040, 0.55 * swirl * swirl);
                if (noise(px, py) > 0.975) rgb = mix(rgb, 0xF4F0FF, 0.8);
                if (r > 0.8) rgb = mix(rgb, open ? 0xF4F0FF : 0x6A40A0, Math.pow((r - 0.8) / 0.2, 2.5));
                if (!open) {
                    double seam = Math.abs(v - 0.04 * Math.sin(u * 3));
                    double width = 0.035 * (1 - u * u);
                    if (seam < width) rgb = 0xFFFFFF;
                    else if (seam < width + 0.12) rgb = mix(rgb, 0xC8A8FF, 0.7 * (1 - (seam - width) / 0.12));
                }
                img.setRGB(px, py, 0xFF000000 | rgb);
            }
        }
        return img;
    }

    /**
     * One wheel of the Watcher's iris, 64x64: a ring of bright teeth of unequal length round an empty
     * middle. Three of these, turning against each other, are its iris.
     */
    static BufferedImage irisRing() {
        int n = 64;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        int teeth = 28;
        for (int py = 0; py < n; py++) {
            for (int px = 0; px < n; px++) {
                double u = (px + 0.5 - n / 2.0) / (n / 2.0), v = (py + 0.5 - n / 2.0) / (n / 2.0);
                double r = Math.hypot(u, v);
                if (r >= 1 || r < 0.6) continue;
                double turn = (Math.atan2(v, u) + Math.PI) / (Math.PI * 2) * teeth;
                int tooth = (int) Math.floor(turn) % teeth;
                double across = turn - Math.floor(turn);
                double inner = 0.66 + 0.2 * noise(tooth * 13, 7);
                boolean band = r > 0.93 && r < 0.985;
                boolean solid = band || (across > 0.14 && across < 0.86 && r > inner && r < 0.93);
                if (solid) {
                    double edge = Math.min(Math.min(across - 0.14, 0.86 - across) * 4, 1);
                    int rgb = band ? 0xFFFFFF : mix(0xB070FF, 0xFFFFFF, Math.max(0, edge));
                    img.setRGB(px, py, 0xFF000000 | rgb);
                } else {
                    img.setRGB(px, py, (70 << 24) | 0x8A50E0);
                }
            }
        }
        return img;
    }

    /** The heart of the Watcher's iris, 64x64: a disc of cold fire, white at its rim, dark at its middle. */
    static BufferedImage iris() {
        int n = 64;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < n; py++) {
            for (int px = 0; px < n; px++) {
                double u = (px + 0.5 - n / 2.0) / (n / 2.0), v = (py + 0.5 - n / 2.0) / (n / 2.0);
                double r = Math.hypot(u, v);
                if (r >= 1) continue;
                double a = Math.atan2(v, u);
                double fib = 0.5 + 0.5 * Math.sin(a * 37 + 4 * Math.sin(a * 6) + r * 9);
                int rgb = r < 0.7 ? mix(0x12061E, 0xB070FF, Math.pow(r / 0.7, 1.6)) : mix(0xB070FF, 0xFFFFFF, (r - 0.7) / 0.3);
                rgb = mix(rgb, 0x2A1040, 0.35 * fib * (1 - r * 0.6));
                img.setRGB(px, py, 0xFF000000 | rgb);
            }
        }
        return img;
    }

    /** A pupil, 32x32: no circle but a bar, as in a goat's eye; perfect black, edged with light. */
    static BufferedImage pupil() {
        int n = 32;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < n; py++) {
            for (int px = 0; px < n; px++) {
                double u = (px + 0.5 - n / 2.0) / (n / 2.0), v = (py + 0.5 - n / 2.0) / (n / 2.0);
                double d = Math.pow(Math.abs(u), 4) + Math.pow(Math.abs(v), 4);
                if (d >= 1) continue;
                img.setRGB(px, py, 0xFF000000 | (d > 0.72 ? mix(0x000000, 0xF4F0FF, (d - 0.72) / 0.28) : 0x000000));
            }
        }
        return img;
    }

    /**
     * A lesser eye of the Watcher, 64x64: a slit of void rimmed with light, a burning iris and a bar pupil;
     * or shut, a seam of light. Its glow spills past the lids (soft alpha).
     */
    static BufferedImage eyeVoid(boolean open) {
        int n = 64;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < n; py++) {
            for (int px = 0; px < n; px++) {
                double u = (px + 0.5 - n / 2.0) / (n / 2.0) / EYE_FILL;
                double v = (py + 0.5 - n / 2.0) / (n / 2.0) / EYE_FILL;
                double edge = 0.66 * (1 - u * u);
                if (Math.abs(v) > edge || Math.abs(u) > 1) {
                    double d = Math.abs(u) < 1 ? Math.abs(v) - edge : Math.hypot(Math.abs(u) - 1, v);
                    double g = Math.max(0, 1 - d / 0.38);
                    int alpha = (int) Math.round((open ? 150 : 60) * g * g);
                    if (alpha > 2) img.setRGB(px, py, (alpha << 24) | 0xB88AFF);
                    continue;
                }
                double rim = edge - Math.abs(v);
                double r = Math.hypot(u, v);
                int rgb = mix(0x050308, 0x1A0A2A, noise(px, py) * 0.6);
                if (rim < 0.07) rgb = mix(0xF4F0FF, 0x6A40A0, rim / 0.07);
                else if (open && r < 0.44) {
                    rgb = mix(0xB070FF, 0xFFFFFF, Math.pow(r / 0.44, 2));
                    if (Math.abs(v) < 0.075 && Math.abs(u) < 0.3) rgb = 0x000000;
                } else if (!open) {
                    double seam = Math.abs(v - 0.03);
                    if (seam < 0.04) rgb = 0xFFFFFF;
                    else if (seam < 0.13) rgb = mix(rgb, 0xB070FF, 0.6 * (1 - (seam - 0.04) / 0.09));
                }
                img.setRGB(px, py, 0xFF000000 | rgb);
            }
        }
        return img;
    }

    // ---- The burning feather ----------------------------------------------------------------------

    /**
     * One frame of a feather of light, 32x128, tip at the top: a white-hot shaft, ivory vanes going to gold
     * at soft, flickering edges, the tip dissolving into fire, and one small eye in it.
     */
    static BufferedImage feather(int frame) {
        int w = 32, h = 128;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < h; py++) {
            double v = (py + 0.5) / h; // 0 = tip, 1 = quill
            double width = 0.78 * Math.pow(Math.sin(Math.PI * Math.min(1, v * 1.06)), 0.65)
                    * (v > 0.92 ? (1 - v) / 0.08 : 1);
            for (int px = 0; px < w; px++) {
                double u = (px + 0.5 - w / 2.0) / (w / 2.0);
                double a = Math.abs(u) / Math.max(0.02, width);
                if (a > 1.28) continue;
                int rgb = mix(0xFFFBEE, mix(0xF2CF6A, 0xE8A838, 1 - v), Math.pow(Math.min(1, a), 1.5));
                double barb = 0.5 + 0.5 * Math.sin(py * 0.9 + Math.abs(u) * 11);
                rgb = mix(rgb, 0xB8862A, 0.13 * barb * Math.min(1, a * 1.5));
                if (Math.abs(u) < 0.07) rgb = 0xFFFFFF;
                double alpha = 1;
                if (a > 0.78) {
                    double flick = 0.75 + 0.25 * Math.sin(py * 0.7 + frame * 1.6 + (u > 0 ? 2 : 0));
                    alpha = Math.pow(Math.max(0, 1 - (a - 0.78) / 0.5), 2) * flick;
                    rgb = mix(rgb, 0xFFB040, 0.5);
                }
                if (v < 0.16) {
                    // The tip is flame: it thins out and breaks up, differently each frame.
                    double f = v / 0.16;
                    alpha *= f * (0.55 + 0.45 * noise(px + frame * 7, py - frame * 5));
                    rgb = mix(0xFF9A30, rgb, f);
                }
                double ex = u / 0.5, ey = (v - 0.3) / 0.045;
                double er = Math.hypot(ex, ey);
                if (er < 1) {
                    rgb = er < 0.35 ? (Math.abs(ex) < 0.1 ? 0x050302 : 0xFFF6DC) : er < 0.75 ? 0xE0A830 : 0x8A6418;
                    alpha = 1;
                }
                int al = (int) Math.round(255 * Math.max(0, Math.min(1, alpha)));
                if (al > 2) img.setRGB(px, py, (al << 24) | rgb);
            }
        }
        return img;
    }

    // ---- Metal, fire and light: the solid pieces ----------------------------------------------------

    /** Burnished gold, 32x32: brushed, bevelled, with a band of runes that light up in turn. */
    static BufferedImage gold(int frame) {
        int n = 32;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double streak = 0.6 * noise(x / 5, y) + 0.4 * noise(x, y * 3);
                int rgb = mix(0xC8962E, 0xFFE08A, 0.25 + 0.6 * streak);
                int d = Math.min(Math.min(x, y), Math.min(n - 1 - x, n - 1 - y));
                if (d < 1) rgb = mix(0x8A6418, rgb, 0.4);
                else if (d < 3) rgb = mix(rgb, y < n / 2 ? 0xFFF6DC : 0x9A7020, 0.4);
                if (y >= 13 && y <= 18) {
                    rgb = y == 13 || y == 18 ? 0x8A6418 : 0x5A3C0C;
                    int cell = x / 4;
                    boolean stroke = y > 13 && y < 18 && x % 4 != 3
                            && noise(cell * 3 + (y - 14) * 17, (x % 4) * 5 + cell) > 0.3;
                    if (stroke) {
                        double pulse = 0.55 + 0.45 * Math.sin(frame * Math.PI / 2 - cell * 0.9);
                        rgb = mix(0xC8962E, 0xFFFBEE, pulse);
                    }
                }
                img.setRGB(x, y, 0xFF000000 | rgb);
            }
        }
        return img;
    }

    /** A wheel of living coal, 32x32: black scale split by cracks of molten light that breathe. */
    static BufferedImage ember(int frame) {
        int n = 32;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        double pulse = 0.5 + 0.5 * Math.sin(frame * Math.PI / 2);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int rgb = mix(0x140A04, 0x3A200C, noise(x, y));
                double c1 = Math.abs(Math.sin(x * 0.42 + Math.sin(y * 0.37) * 2.2));
                double c2 = Math.abs(Math.sin(y * 0.47 + Math.sin(x * 0.31) * 2.6));
                double crack = Math.min(c1, c2);
                if (crack < 0.1) rgb = mix(0xFFF0C0, 0xFF8A20, crack / 0.1 * (1 - 0.4 * pulse));
                else if (crack < 0.3) rgb = mix(mix(0xFF7A18, 0xC84A0A, pulse), rgb, (crack - 0.1) / 0.2);
                img.setRGB(x, y, 0xFF000000 | rgb);
            }
        }
        return img;
    }

    /** Solid light, 16x64: white, with paler gold currents streaming up it. */
    static BufferedImage light(int frame) {
        int w = 16, h = 64;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double u = (x + 0.5 - w / 2.0) / (w / 2.0);
                double flow = 0.5 + 0.5 * Math.sin((y + frame * 16) * Math.PI / 32 + Math.sin(x * 1.3) * 1.5);
                int rgb = mix(0xFFFFFF, 0xFFE9B0, 0.55 * u * u + 0.35 * flow);
                img.setRGB(x, y, 0xFF000000 | rgb);
            }
        }
        return img;
    }

    /** A soft round glow, 128x128: light with no edge, for haloes behind things. */
    static BufferedImage glow(int rgb, double strength) {
        int n = 128;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < n; py++) {
            for (int px = 0; px < n; px++) {
                double u = (px + 0.5 - n / 2.0) / (n / 2.0), v = (py + 0.5 - n / 2.0) / (n / 2.0);
                double r = Math.hypot(u, v);
                if (r >= 1) continue;
                // Dithered a little, so the fall-off shows no rings.
                int alpha = (int) Math.round(255 * strength * Math.pow(1 - r, 2.6) + noise(px, py) - 0.5);
                if (alpha > 1) img.setRGB(px, py, (alpha << 24) | mix(0xFFFFFF, rgb, Math.min(1, r * 2.5)));
            }
        }
        return img;
    }

    /** A steady pseudo-random number in [0, 1) for a pixel. */
    static double noise(int x, int y) {
        int h = x * 0x9E3779B1 + y * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h >>> 8) / (double) (1 << 24);
    }

    // ---- Colour helpers ---------------------------------------------------------------------------

    static int mix(int a, int b, double t) {
        return TrueForm.mix(a, b, t);
    }

    private static int scale(int rgb, double k) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * k);
        int g = (int) Math.min(255, ((rgb >> 8) & 255) * k);
        int b = (int) Math.min(255, (rgb & 255) * k);
        return (r << 16) | (g << 8) | b;
    }
}
