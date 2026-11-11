package net.bigyous.gptgodmc.god.vessel;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.bigyous.gptgodmc.god.vessel.VesselSkin.Face;
import net.bigyous.gptgodmc.god.vessel.VesselSkin.Layout;
import net.bigyous.gptgodmc.god.vessel.VesselSkin.Piece;
import net.bigyous.gptgodmc.god.vessel.VesselSkin.Rect;
import net.bigyous.gptgodmc.god.vessel.VesselSkin.Region;

/**
 * Paints the vessel's own skins, one animated atlas per look, from code (no image files): violet
 * nebula flesh with glowing veins and a rune sigil (shade), ivory and gold with a turning sunburst
 * (radiant), crimson flesh split by flickering ember cracks (infernal), and a living starfield with a
 * spiral galaxy over the heart (void). Deterministic, so the pack's hash stays the same between restarts.
 */
public final class VesselTextures {
    /** One look's painted frames (each frame is a whole atlas, laid out by {@link VesselSkin#LAYOUT}). */
    public record Atlas(String look, Layout layout, BufferedImage[] frames) {
        /** The texel color at face coordinates (s, t) of a region, for previews. */
        public int sample(String key, Face face, double s, double t, int frame) {
            Rect r = layout.region(key).face(face);
            int x = r.x() + (int) Math.min(r.w() - 1, Math.max(0, Math.floor(s * r.w())));
            int y = r.y() + (int) Math.min(r.h() - 1, Math.max(0, Math.floor(t * r.h())));
            return frames[Math.floorMod(frame, frames.length)].getRGB(x, y) & 0xFFFFFF;
        }
    }

    private static final Map<String, Atlas> CACHE = new ConcurrentHashMap<>();

    public static Atlas atlas(String look) {
        return CACHE.computeIfAbsent(look, VesselTextures::paint);
    }

    /** Paints every region of a look, frame by frame. */
    public static Atlas paint(String look) {
        Layout layout = VesselSkin.LAYOUT;
        Painter mythic = MythicTextures.painter(look);
        Painter painter = mythic != null ? mythic : switch (look) {
            case "radiant" -> VesselTextures::radiant;
            case "infernal" -> VesselTextures::infernal;
            case "void" -> VesselTextures::voidLook;
            default -> VesselTextures::shade;
        };
        BufferedImage[] frames = new BufferedImage[VesselSkin.FRAMES];
        for (int f = 0; f < frames.length; f++) {
            BufferedImage img = new BufferedImage(layout.width(), layout.height(), BufferedImage.TYPE_INT_ARGB);
            for (Region region : layout.regions().values()) {
                for (Face face : Face.values()) {
                    paintFace(img, region, face, f, painter);
                }
                fillGutter(img, region);
            }
            frames[f] = img;
        }
        return new Atlas(look, layout, frames);
    }

    /** One texel being painted: where it is on its face and on its box, and the animation phase. */
    static final class Px {
        String key;
        Piece piece;
        Face face;
        /** 0..1 across and down the face, at the texel's center. */
        double s, t;
        /** Integer texel on the face, and the face's size. */
        int u, v, fw, fh;
        /** The texel's point on the box surface, in texels (0..w, 0..h, 0..d). */
        double x, y, z;
        int frame;
        /** 0..2pi through the animation loop. */
        double phase;

        /** Texels to the nearest edge of the face. */
        double edge() {
            return Math.min(Math.min(u, fw - 1 - u), Math.min(v, fh - 1 - v));
        }

        boolean side() {
            return face != Face.UP && face != Face.DOWN;
        }

        boolean front() {
            return face == Face.SOUTH;
        }

        boolean back() {
            return face == Face.NORTH;
        }

        /** Looping drift through 3D noise space for animation. */
        double dx(double amount) {
            return Math.cos(phase) * amount;
        }

        double dy(double amount) {
            return Math.sin(phase) * amount;
        }

        /** A smooth 0..1 pulse through the loop, offset by a fraction of a turn. */
        double pulse(double offset) {
            return 0.5 + 0.5 * Math.sin(phase + offset * Math.PI * 2);
        }
    }

    @FunctionalInterface
    interface Painter {
        int paint(Px p);
    }

    private static void paintFace(BufferedImage img, Region region, Face face, int frame, Painter painter) {
        Rect r = region.face(face);
        Piece piece = region.piece();
        Px p = new Px();
        p.key = region.key();
        p.piece = piece;
        p.face = face;
        p.fw = r.w();
        p.fh = r.h();
        p.frame = frame;
        p.phase = Math.PI * 2 * frame / VesselSkin.FRAMES;
        for (int v = 0; v < r.h(); v++) {
            for (int u = 0; u < r.w(); u++) {
                p.u = u;
                p.v = v;
                p.s = (u + 0.5) / r.w();
                p.t = (v + 0.5) / r.h();
                float[] q = face.point(p.s, p.t);
                p.x = q[0] * piece.w;
                p.y = q[1] * piece.h;
                p.z = q[2] * piece.d;
                img.setRGB(r.x() + u, r.y() + v, 0xFF000000 | clamp(painter.paint(p)));
            }
        }
    }

    /** Copies each region's border texels outward into its gutter. */
    private static void fillGutter(BufferedImage img, Region region) {
        int g = VesselSkin.GUTTER;
        for (Face face : Face.values()) {
            Rect r = region.face(face);
            for (int i = 1; i <= g; i++) {
                for (int u = 0; u < r.w(); u++) {
                    set(img, r.x() + u, r.y() - i, img.getRGB(r.x() + u, r.y()), region);
                    set(img, r.x() + u, r.y() + r.h() - 1 + i, img.getRGB(r.x() + u, r.y() + r.h() - 1), region);
                }
                for (int v = -i; v < r.h() + i; v++) {
                    int vv = Math.max(0, Math.min(r.h() - 1, v));
                    set(img, r.x() - i, r.y() + v, img.getRGB(r.x(), r.y() + vv), region);
                    set(img, r.x() + r.w() - 1 + i, r.y() + v, img.getRGB(r.x() + r.w() - 1, r.y() + vv), region);
                }
            }
        }
    }

    /** Fills a texel only if nothing has been painted there yet (never over a face). */
    private static void set(BufferedImage img, int x, int y, int argb, Region region) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return;
        if ((img.getRGB(x, y) >>> 24) == 0) img.setRGB(x, y, argb);
    }

    // ---- The looks -----------------------------------------------------------------------------

    /** Violet nebula flesh with glowing veins, a robe of night with a pulsing rune sigil, amethyst bands. */
    static int shade(Px p) {
        return switch (p.piece) {
            case HEAD, HAND -> shadeFlesh(p);
            case TORSO -> {
                int c = robe(p, 0x10061C, 0x2E1050, 11);
                c = trim(p, c, 0x7B3FC4, 0.9, false);
                if (p.front()) c = sigil(p, c, 8, 6.8, 4.6, 0xC77DFF, 0xFFFFFF, 3);
                else if (p.back()) c = sigil(p, c, 8, 9, 6, 0x8E4FD6, 0xE9D8FF, 5);
                yield runeColumn(p, c, 0xB57EDC, 17);
            }
            case ARM -> {
                int c = robe(p, 0x170828, 0x3A1466, 13);
                if (p.side() && (p.v == 2 || p.v == 9)) c = mix(c, 0x7B3FC4, 0.7);
                if (p.side() && p.v >= 3 && p.v < 9 && zigzag(p.u, p.v - 3, 6)) {
                    c = glow(c, 0xC77DFF, 0.45 + 0.4 * p.pulse(p.u / 8.0));
                }
                yield trim(p, c, 0x7B3FC4, 0.0, true);
            }
            case LEG -> {
                int c = robe(p, 0x0A0412, 0x1E0A36, 15);
                if (p.side() && Math.abs(p.s - 0.5) < 0.09) c = glow(c, 0x9D4EDD, 0.35 + 0.35 * p.pulse(p.t));
                yield c;
            }
            case BOOT -> obsidian(p, 0x0B0714, 0x4B2A7A);
            case BELT, BAND, COLLAR -> amethyst(p);
            case GEM -> radial(p, 0xFFFFFF, 0xF4C9E8, 0xA66BD8);
            case EYE -> eye(p, eyeColor(p, 0xE2C8FF));
            case CAPE -> trim(p, starfield(p, 0x06030C, 0x2A0E45, 0x0E0620, 31), 0x5B21A0, 0.0, true);
            case LINING -> nebula(p, 0x2A0848, 0x9D4EDD, 33);
            case HALO -> radial(p, 0xFFFFFF, 0xE0C3FF, 0xB24BF3);
            case HAIR_TOP, HAIR_BACK -> robe(p, 0x12061F, 0x3A1466, 37);
            default -> 0xFF00FF;
        };
    }

    /** The shade's skin: deep violet nebula, faint drifting stars, and luminous veins that pulse. */
    static int shadeFlesh(Px p) {
        double n = fbm(p.x * 0.16 + p.dx(0.6), p.y * 0.16 + p.dy(0.6), p.z * 0.16, 101, 4);
        int c = mix(0x260B44, 0x5A21A0, smooth(0.25, 0.8, n));
        double vein = fbm(p.x * 0.11, p.y * 0.11, p.z * 0.11, 103, 3);
        double ridge = 1 - Math.abs(vein - 0.5) * 16;
        if (ridge > 0) c = glow(c, 0xD9A6FF, ridge * (0.45 + 0.4 * p.pulse(vein * 3)));
        c = stars(p, c, 0.985, 0xF1E4FF, 105);
        if (p.piece == Piece.HEAD && p.front()) {
            c = face(p, c, 0x07020D, 0xE2C8FF, 0xC77DFF);
            // A third eye: a small glowing diamond on the brow.
            double ds = Math.abs(p.u + 0.5 - 8) + Math.abs(p.v + 0.5 - 3.4);
            if (ds < 1.6) c = glow(c, 0xF1E4FF, 0.6 + 0.4 * p.pulse(0.25));
            else if (ds < 2.6) c = glow(c, 0xB24BF3, 0.35 * p.pulse(0.25));
        }
        if (p.piece == Piece.HEAD && p.face == Face.UP) c = mix(c, 0x14062A, 0.35);
        return shadeEdge(p, c, 0.82);
    }

    /** Ivory and gold: warm luminous skin with golden filigree, white robes and a turning sunburst. */
    static int radiant(Px p) {
        return switch (p.piece) {
            case HEAD, HAND -> {
                double n = fbm(p.x * 0.14, p.y * 0.14, p.z * 0.14, 201, 3);
                int c = mix(0xE9D1A0, 0xFBF0D8, smooth(0.2, 0.85, n));
                double vein = fbm(p.x * 0.12, p.y * 0.12, p.z * 0.12, 203, 3);
                double ridge = 1 - Math.abs(vein - 0.5) * 22;
                if (ridge > 0) c = glow(c, 0xF2B632, ridge * (0.25 + 0.25 * p.pulse(vein * 2)));
                if (p.piece == Piece.HEAD && p.front()) {
                    c = face(p, c, 0xB88A3C, -1, 0xFFD54A);
                    c = sunMark(p, c, 8, 3.2);
                }
                yield p.edge() < 1 ? mix(c, 0xF2C230, 0.35) : c;
            }
            case HAIR_TOP, HAIR_BACK -> {
                double strand = hash(p.face == Face.UP ? (int) p.x : p.u, 0, p.face.ordinal(), 211);
                double wave = 0.5 + 0.5 * Math.sin(p.y * 0.9 + strand * 6);
                int c = mix(0xC9971A, 0xFFE27A, 0.35 + 0.45 * strand * wave);
                if (hash(p.u, p.v, p.face.ordinal(), 213) > 0.93) c = glow(c, 0xFFFFFF, 0.6 * p.pulse(strand));
                yield shadeEdge(p, c, 0.85);
            }
            case TORSO -> {
                int c = folds(p, 0xE7E2D6, 0xFFFFFF);
                c = trim(p, c, 0xF2C230, 1.2, false);
                if (p.front()) c = sunburst(p, c, 8, 6.8, 9);
                if (p.back()) c = sunburst(p, c, 8, 10, 6);
                yield c;
            }
            case ARM -> {
                int c = folds(p, 0xE4DFD2, 0xFFFFFF);
                if (p.side() && p.t > 0.72 && p.t < 0.86) c = filigree(p, 0xF6D03D);
                yield c;
            }
            case LEG -> {
                int c = folds(p, 0xE0DACB, 0xFAF8F2);
                if (p.side() && p.t > 0.86) c = filigree(p, 0xE9B829);
                if (p.front() && Math.abs(p.s - 0.5) < 0.1) c = mix(c, 0xF6D03D, 0.75);
                yield c;
            }
            case BOOT, BELT, BAND, COLLAR -> filigree(p, 0xF6D03D);
            case GEM -> radial(p, 0xFFFFFF, 0xBFF0FF, 0x3BA7E8);
            case EYE -> eye(p, eyeColor(p, 0x9FE4FF));
            case CAPE -> trim(p, folds(p, 0xE8E4DA, 0xFFFFFF), 0xF2C230, 1.2, true);
            case LINING -> {
                double ny = 1 - p.y / p.piece.h;
                int c = mix(0xFFB547, 0xFFF1B5, ny);
                double a = Math.atan2(p.y - p.piece.h, p.x - p.piece.w / 2.0);
                double ray = Math.pow(Math.abs(Math.sin(a * 9 + p.phase / 3)), 6);
                yield glow(c, 0xFFFFFF, ray * 0.5);
            }
            case HALO -> radial(p, 0xFFFFFF, 0xFFF1B5, 0xFFC234);
            default -> 0xFF00FF;
        };
    }

    /** Crimson flesh split by flickering ember cracks, charred hands, molten seams and an ash cape. */
    static int infernal(Px p) {
        return switch (p.piece) {
            case HEAD, TORSO, ARM -> {
                double n = fbm(p.x * 0.08, p.y * 0.08, p.z * 0.08, 301, 3);
                int c = mix(0x5A0B08, 0x9E1C12, smooth(0.25, 0.8, n));
                double char_ = fbm(p.x * 0.07 + 7, p.y * 0.07, p.z * 0.07, 303, 3);
                if (char_ > 0.56) c = mix(c, 0x1A0505, smooth(0.56, 0.7, char_) * 0.8);
                c = embers(p, c, 0.07, 0.5, 305);
                if (p.piece == Piece.HEAD && p.front()) {
                    c = face(p, c, 0x050000, -1, 0xFF4A1C);
                    c = moltenMouth(p, c);
                    // A brand on the brow: a downward triangle.
                    double bx = Math.abs(p.u + 0.5 - 8), by = p.v + 0.5 - 1.6;
                    if (by > 0 && by < 3.2 && bx < (3.2 - by) * 0.75 && bx > (3.2 - by) * 0.75 - 1.1) {
                        c = glow(c, 0xFF7B29, 0.6 + 0.35 * p.pulse(0.1));
                    }
                }
                if (p.piece == Piece.TORSO && p.front()) c = sigil(p, c, 8, 6.8, 4.4, 0xFF4A1C, 0xFFD36E, 7);
                yield shadeEdge(p, c, 0.78);
            }
            case HAND -> {
                double n = fbm(p.x * 0.12, p.y * 0.12, p.z * 0.12, 311, 3);
                int c = mix(0x0E0303, 0x2A0A08, n);
                yield shadeEdge(p, embers(p, c, 0.08, 0.7, 313), 0.8);
            }
            case LEG -> {
                double n = fbm(p.x * 0.1, p.y * 0.1, p.z * 0.1, 321, 3);
                yield shadeEdge(p, embers(p, mix(0x0C0606, 0x24100E, n), 0.05, 0.45, 323), 0.8);
            }
            case BOOT -> {
                int c = obsidian(p, 0x1A161E, 0x3A3340);
                if (p.side() && p.v <= 1) c = glow(c, 0xFF6A00, 0.7 + 0.3 * p.pulse(p.s));
                yield c;
            }
            case BELT, COLLAR -> {
                int c = obsidian(p, 0x221C26, 0x403844);
                if (p.side() && Math.abs(p.t - 0.5) < 0.2) c = glow(c, 0xFF8A1E, 0.75 + 0.25 * p.pulse(p.s * 2));
                yield c;
            }
            case BAND -> embers(p, mix(0x2A0904, 0x4A1206, fbm(p.x * 0.3, p.y * 0.3, p.z * 0.3, 331, 2)), 0.16,
                    0.9, 333);
            case GEM -> radial(p, 0xFFF6C8, 0xFFB12E, 0xC2300A);
            case EYE -> eye(p, eyeColor(p, 0xFF9A1E));
            case CAPE -> {
                double n = fbm(p.x * 0.2, p.y * 0.2, p.z * 0.2, 341, 3);
                int c = mix(0x070404, 0x1C1212, n);
                double heat = smooth(0.75, 1.0, p.t);
                c = glow(c, 0xFF4A1C, heat * (0.55 + 0.35 * p.pulse(p.s * 3)));
                // Embers rising off the hem.
                double rise = (p.t * 6 + p.frame / (double) VesselSkin.FRAMES) % 1.0;
                if (hash(p.u, (int) Math.floor(p.t * 6 + p.frame / (double) VesselSkin.FRAMES), 1, 343) > 0.86
                        && rise < 0.18) {
                    c = glow(c, 0xFFB347, 0.8);
                }
                yield c;
            }
            case LINING -> {
                int c = mix(0x8C1A10, 0x3A0606, p.t);
                yield embers(p, c, 0.05, 0.5, 351);
            }
            case HALO -> radial(p, 0xFFF1B0, 0xFF7B29, 0xC2300A);
            case HAIR_TOP, HAIR_BACK -> obsidian(p, 0x120A0A, 0x3A1A12);
            default -> 0xFF00FF;
        };
    }

    /** A living starfield: drifting nebulae, twinkling stars and a spiral galaxy turning over the heart. */
    static int voidLook(Px p) {
        return switch (p.piece) {
            case HEAD, HAND -> {
                int c = cosmos(p, 0x03010A, 401, p.piece == Piece.HEAD ? 0.972 : 0.96);
                if (p.piece == Piece.HEAD && p.front()) c = face(p, c, 0x000000, -1, 0x8E7CFF);
                yield shadeEdge(p, c, 0.85);
            }
            case TORSO -> {
                int c = cosmos(p, 0x0A0418, 411);
                if (p.front()) c = galaxy(p, c, 8, 7.5, 7.5);
                if (p.back()) c = galaxy(p, c, 8, 9, 5);
                yield trim(p, c, 0x5A189A, 0.0, false);
            }
            case ARM, LEG -> shadeEdge(p, cosmos(p, 0x0C0420, p.piece == Piece.ARM ? 421 : 431), 0.85);
            case BOOT -> stars(p, obsidian(p, 0x05030A, 0x2A1450), 0.97, 0xFFFFFF, 441);
            case BELT, BAND, COLLAR -> amethyst(p);
            case GEM -> radial(p, 0xFFFFFF, 0xDCD4FF, 0x6F5BFF);
            case EYE -> eye(p, eyeColor(p, 0xD8D0FF));
            case CAPE -> trim(p, starfield(p, 0x020107, 0x1A0A3A, 0x0A1430, 451), 0x3C1E7A, 0.0, true);
            case LINING -> nebula(p, 0x1A0640, 0x7B5CFF, 453);
            case HALO -> radial(p, 0xFFFFFF, 0xC8B8FF, 0x7B5CFF);
            case HAIR_TOP, HAIR_BACK -> cosmos(p, 0x05020C, 461);
            default -> 0xFF00FF;
        };
    }

    // ---- Motifs --------------------------------------------------------------------------------

    /** Eyes glow from a white core; a mood's eyes ("eye_wrathful"...) take the mood's color. */
    static int eyeColor(Px p, int own) {
        return switch (p.key) {
            case "eye_wrathful" -> 0xFF2A1A;
            case "eye_ominous" -> 0x7FD8FF;
            case "eye_exalted" -> 0xFFF1B0;
            default -> own;
        };
    }

    static int eye(Px p, int color) {
        double cx = Math.abs(p.s - 0.5) * 2, cy = Math.abs(p.t - 0.5) * 2;
        double d = Math.max(cx, cy * 0.8);
        return mix(0xFFFFFF, color, smooth(0.15, 0.95, d) * 0.85);
    }

    /** A bright core fading out to a rim, the same on every face (gems, halo). */
    static int radial(Px p, int core, int mid, int rim) {
        double d = Math.hypot(p.s - 0.5, p.t - 0.5) * 2;
        double shimmer = 0.1 * Math.sin(p.phase + (p.s + p.t) * 4);
        d = Math.max(0, d + shimmer);
        return d < 0.5 ? mix(core, mid, d / 0.5) : mix(mid, rim, Math.min(1, (d - 0.5) / 0.6));
    }

    /**
     * Eye sockets and glowing tears on a head's front face. The eyes themselves are separate parts at
     * s = 0.25 and 0.75, t = 0.56.
     */
    static int face(Px p, int c, int socket, int tear, int glowColor) {
        for (double ex : new double[] { 0.25, 0.75 }) {
            double dx = (p.s - ex) / 0.17, dy = (p.t - 0.5625) / 0.11;
            double d = dx * dx + dy * dy;
            if (d < 1) c = mix(c, socket, 0.75 * (1 - d * 0.6));
            else if (d < 1.9) c = glow(c, glowColor, 0.25 * (1.9 - d));
            // A thin trail of light running down from each eye, flowing with the animation.
            if (tear >= 0 && Math.abs(p.s - ex) < 0.04 && p.t > 0.62 && p.t < 0.9) {
                double flow = 0.5 + 0.5 * Math.sin(p.t * 26 - p.phase * 2);
                c = glow(c, tear, 0.25 + 0.45 * flow * (0.9 - p.t) / 0.28);
            }
        }
        // A faint line of a mouth.
        if (p.v == (int) (p.fh * 0.81) && p.s > 0.36 && p.s < 0.64) c = mix(c, socket, 0.55);
        return c;
    }

    static int moltenMouth(Px p, int c) {
        double line = 0.8 + 0.035 * Math.sin(p.s * 23);
        if (Math.abs(p.t - line) < 0.035 && p.s > 0.3 && p.s < 0.7) {
            c = mix(0xFFD36E, 0xFF4A1C, Math.abs(p.s - 0.5) * 4) ;
        }
        return c;
    }

    static int sunMark(Px p, int c, double cx, double cy) {
        double dx = p.u + 0.5 - cx, dy = p.v + 0.5 - cy;
        double r = Math.hypot(dx, dy);
        if (r < 1.2) return glow(c, 0xFFFFFF, 0.9);
        if (r < 2.4) {
            double a = Math.atan2(dy, dx);
            double ray = Math.pow(Math.abs(Math.cos(a * 4 + p.phase / 4)), 4);
            return glow(c, 0xFFD54A, 0.25 + 0.6 * ray);
        }
        return c;
    }

    /** A robe of woven darkness: soft folds and a slow sheen. */
    static int robe(Px p, int dark, int light, int seed) {
        double n = fbm(p.x * 0.2, p.y * 0.08, p.z * 0.2, seed, 3);
        double fold = 0.5 + 0.5 * Math.sin((p.x + p.z) * 0.9 + n * 3);
        int c = mix(dark, light, 0.25 + 0.5 * fold * n);
        double sheen = Math.max(0, Math.sin((p.x + p.z) * 0.35 - p.y * 0.25 + p.phase));
        return glow(c, light, Math.pow(sheen, 8) * 0.25);
    }

    /** White cloth with vertical folds. */
    static int folds(Px p, int shadow, int light) {
        double n = fbm(p.x * 0.25, p.y * 0.06, p.z * 0.25, 221, 2);
        double fold = 0.5 + 0.5 * Math.sin((p.x + p.z) * 1.1 + n * 2.5);
        return mix(shadow, light, 0.35 + 0.65 * fold);
    }

    /** A colored border along a face's sides (and bottom when asked), the way a skin trims its robes. */
    static int trim(Px p, int c, int color, double width, boolean bottom) {
        if (!p.side()) return c;
        boolean edge = width > 0 && (p.u < width || p.u >= p.fw - width);
        boolean hem = bottom && p.v >= p.fh - 1;
        return edge || hem ? mix(c, color, 0.8) : c;
    }

    /** Darkens a face's outermost ring of texels, like a skin's drawn outline. */
    static int shadeEdge(Px p, int c, double factor) {
        return p.edge() < 1 ? scale(c, factor) : c;
    }

    /** Polished stone: Voronoi facets with lighter edges and the occasional glint. */
    static int obsidian(Px p, int base, int edge) {
        double[] v = voronoi(p.x * 0.35, p.y * 0.35, p.z * 0.35, 501);
        int c = mix(base, scale(base, 1.6), v[2]);
        double e = v[1] - v[0];
        if (e < 0.12) c = mix(c, edge, (0.12 - e) / 0.12 * 0.8);
        if (hash(p.u, p.v, p.face.ordinal(), 503) > 0.97) c = glow(c, edge, 0.5 + 0.5 * p.pulse(v[2]));
        return c;
    }

    /** Amethyst crystal: lilac facets with glowing edges that brighten in turn. */
    static int amethyst(Px p) {
        double[] v = voronoi(p.x * 0.45, p.y * 0.45, p.z * 0.45, 511);
        int c = mix(0x5E3496, 0xC9A0FF, 0.25 + 0.75 * v[2]);
        double e = v[1] - v[0];
        if (e < 0.14) c = glow(c, 0xF4E6FF, (0.14 - e) / 0.14 * (0.4 + 0.5 * p.pulse(v[2])));
        return shadeEdge(p, c, 0.8);
    }

    /** Gold worked with a filigree pattern and points of inlaid light. */
    static int filigree(Px p, int gold) {
        double pattern = Math.sin(p.x * 1.6 + Math.sin(p.y * 1.2) * 2) * Math.sin(p.y * 1.6 + Math.cos(p.z * 1.2) * 2)
                + Math.sin(p.z * 1.6);
        int c = mix(scale(gold, 0.72), mix(gold, 0xFFF3B0, 0.4), 0.5 + 0.25 * pattern);
        if (Math.abs(pattern) < 0.12) c = scale(gold, 0.55);
        if (hash(p.u, p.v, p.face.ordinal(), 523) > 0.96) c = glow(c, 0xFFFFFF, 0.5 + 0.5 * p.pulse(p.s));
        return shadeEdge(p, c, 0.82);
    }

    /** Flickering ember cracks: thin bright seams between Voronoi cells, each cell burning on its own beat. */
    static int embers(Px p, int c, double width, double freq, int seed) {
        double[] v = voronoi(p.x * 0.22 * freq, p.y * 0.22 * freq, p.z * 0.22 * freq, seed);
        double e = v[1] - v[0];
        if (e < width) {
            double k = 1 - e / width;
            double flicker = 0.6 + 0.4 * Math.sin(p.phase * 2 + v[2] * 12);
            int hot = mix(0xFF3A10, 0xFFE08A, k * k);
            c = mix(c, hot, Math.min(1, k * 1.4) * flicker);
        } else if (e < width * 2.2) {
            c = glow(c, 0x8A1A08, (width * 2.2 - e) / (width * 1.2) * 0.4);
        }
        return c;
    }

    /** Sparse twinkling stars. */
    static int stars(Px p, int c, double threshold, int color, int seed) {
        double h = hash(p.u, p.v, p.face.ordinal() + p.piece.ordinal() * 7, seed);
        if (h > threshold) {
            double tw = 0.5 + 0.5 * Math.sin(p.phase + h * 977);
            c = glow(c, color, 0.35 + 0.65 * tw);
        }
        return c;
    }

    /** Black sky with faint nebula and stars. */
    static int starfield(Px p, int base, int nebulaA, int nebulaB, int seed) {
        double n = fbm(p.x * 0.12 + p.dx(0.5), p.y * 0.12 + p.dy(0.5), p.z * 0.12, seed, 4);
        int c = mix(base, nebulaA, smooth(0.5, 0.85, n));
        double m = fbm(p.x * 0.09 + 13, p.y * 0.09, p.z * 0.09, seed + 1, 3);
        c = glow(c, nebulaB, smooth(0.55, 0.9, m) * 0.8);
        return stars(p, c, 0.965, 0xFFFFFF, seed + 2);
    }

    /** A bright nebula with swirling filaments and stars. */
    static int nebula(Px p, int dark, int light, int seed) {
        double q = fbm(p.x * 0.1, p.y * 0.1, p.z * 0.1, seed, 3);
        double n = fbm(p.x * 0.14 + q * 3 + p.dx(0.7), p.y * 0.14 + q * 3 + p.dy(0.7), p.z * 0.14, seed + 1, 4);
        int c = mix(dark, light, smooth(0.25, 0.85, n));
        return stars(p, c, 0.975, 0xFFFFFF, seed + 2);
    }

    /** The void's flesh: deep space with violet and blue nebulae and many stars, some with flares. */
    static int cosmos(Px p, int base, int seed) {
        return cosmos(p, base, seed, 0.955);
    }

    static int cosmos(Px p, int base, int seed, double starThreshold) {
        double n = fbm(p.x * 0.12 + p.dx(0.5), p.y * 0.12 + p.dy(0.5), p.z * 0.12, seed, 4);
        int c = glow(base, 0x4A1A9A, smooth(0.5, 0.85, n) * 0.9);
        double m = fbm(p.x * 0.1 + 31, p.y * 0.1, p.z * 0.1 + 17, seed + 1, 4);
        c = glow(c, 0x1E3A9A, smooth(0.55, 0.88, m) * 0.8);
        c = stars(p, c, starThreshold, 0xE8E4FF, seed + 2);
        // A few bright stars throw a little cross of light.
        for (int[] o : new int[][] { { 0, 0 }, { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            double h = hash(p.u + o[0], p.v + o[1], p.face.ordinal() + p.piece.ordinal() * 7, seed + 3);
            if (h > 0.992) {
                double tw = 0.5 + 0.5 * Math.sin(p.phase + h * 613);
                c = glow(c, 0xFFFFFF, (o[0] == 0 && o[1] == 0 ? 1.0 : 0.45) * (0.4 + 0.6 * tw));
            }
        }
        return c;
    }

    /** A spiral galaxy around a point on the face (texels), turning through the loop. */
    static int galaxy(Px p, int c, double cx, double cy, double radius) {
        double dx = p.u + 0.5 - cx, dy = p.v + 0.5 - cy;
        double r = Math.hypot(dx, dy) / radius;
        if (r > 1.3) return c;
        double a = Math.atan2(dy, dx);
        double arm = Math.pow(0.5 + 0.5 * Math.cos(2 * a - r * 5.5 + p.phase), 3);
        double fall = Math.max(0, 1 - r);
        c = glow(c, 0x6E8BFF, fall * 0.25);
        c = glow(c, 0xB9A4FF, arm * fall);
        c = glow(c, 0xFFFFFF, Math.max(0, 1 - r * 2.6));
        return c;
    }

    /** A rune circle around a point on the face: a ring, glyph ticks and a bright center, pulsing. */
    static int sigil(Px p, int c, double cx, double cy, double radius, int color, int core, int seed) {
        double dx = p.u + 0.5 - cx, dy = p.v + 0.5 - cy;
        double r = Math.hypot(dx, dy);
        double a = Math.atan2(dy, dx);
        double pulse = 0.6 + 0.4 * p.pulse(0);
        if (Math.abs(r - radius) < 0.6) c = glow(c, color, 0.85 * pulse);
        else if (Math.abs(r - radius) < 1.3) c = glow(c, color, 0.3 * pulse);
        // Six ticks crossing the ring, like glyphs on a seal.
        double tick = Math.abs(Math.sin(a * 3 + seed));
        if (r > radius - 2.0 && r < radius + 1.6 && tick < 0.08) c = glow(c, core, 0.8 * pulse);
        // An inner triangle.
        double tri = Math.cos(Math.floor((a + Math.PI / 2) / (Math.PI * 2 / 3) + 0.5) * (Math.PI * 2 / 3)
                - (a + Math.PI / 2)) * r;
        if (Math.abs(tri - radius * 0.5) < 0.45 && r < radius) c = glow(c, color, 0.6 * pulse);
        if (r < 1.0) c = glow(c, core, 0.7);
        return c;
    }

    /** Golden rays from a point, turning slowly through the loop. */
    static int sunburst(Px p, int c, double cx, double cy, double radius) {
        double dx = p.u + 0.5 - cx, dy = p.v + 0.5 - cy;
        double r = Math.hypot(dx, dy);
        double a = Math.atan2(dy, dx);
        double ray = Math.pow(Math.abs(Math.cos(a * 6 + p.phase / 6)), 10);
        double fall = Math.max(0, 1 - r / radius);
        c = mix(c, 0xF6C431, ray * fall * 0.9);
        if (Math.abs(r - radius * 0.38) < 0.55) c = mix(c, 0xE9B829, 0.85);
        if (r < radius * 0.3) c = glow(c, 0xFFF6C8, 0.6 * (1 - r / (radius * 0.3)));
        return c;
    }

    /** A chain of small glowing diamonds down the middle of the torso's front, below the sigil. */
    static int runeColumn(Px p, int c, int color, int seed) {
        if (!p.front()) return c;
        int top = 13, bottom = p.fh - 4;
        if (p.v < top || p.v >= bottom) return c;
        int bead = (p.v - top) / 4;
        double cy = top + bead * 4 + 1.5, dx = Math.abs(p.u + 0.5 - 8), dy = Math.abs(p.v + 0.5 - cy - 0.5);
        double d = dx + dy;
        if (d <= 1.5) return glow(c, color, 0.45 + 0.45 * p.pulse(bead / 4.0 + seed));
        if (dx < 0.6) return glow(c, color, 0.18);
        return c;
    }

    /** A glowing zigzag between two lines, for bands of ornament; height is the band's height in texels. */
    static boolean zigzag(int u, int v, int height) {
        int period = (height - 1) * 2;
        int k = Math.floorMod(u, period);
        int y = k < height ? k : period - k;
        return v == y;
    }

    // ---- Noise and color -----------------------------------------------------------------------

    static double hash(int x, int y, int z, int seed) {
        int h = seed * 0x27d4eb2d;
        h ^= x * 0x165667b1;
        h = Integer.rotateLeft(h, 13) * 0x61c88647;
        h ^= y * 0x9e3779b9;
        h = Integer.rotateLeft(h, 11) * 0x2c1b3c6d;
        h ^= z * 0x297a2d39;
        h ^= h >>> 15;
        h *= 0x2c1b3c6d;
        h ^= h >>> 12;
        h *= 0x297a2d39;
        h ^= h >>> 15;
        return (h >>> 8) / (double) (1 << 24);
    }

    static double noise(double x, double y, double z, int seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double fx = fade(x - ix), fy = fade(y - iy), fz = fade(z - iz);
        double a = lerp(hash(ix, iy, iz, seed), hash(ix + 1, iy, iz, seed), fx);
        double b = lerp(hash(ix, iy + 1, iz, seed), hash(ix + 1, iy + 1, iz, seed), fx);
        double c = lerp(hash(ix, iy, iz + 1, seed), hash(ix + 1, iy, iz + 1, seed), fx);
        double d = lerp(hash(ix, iy + 1, iz + 1, seed), hash(ix + 1, iy + 1, iz + 1, seed), fx);
        return lerp(lerp(a, b, fy), lerp(c, d, fy), fz);
    }

    static double fbm(double x, double y, double z, int seed, int octaves) {
        double sum = 0, amp = 0.5, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x, y, z, seed + i * 17);
            norm += amp;
            amp *= 0.5;
            x *= 2.03;
            y *= 2.03;
            z *= 2.03;
        }
        return sum / norm;
    }

    /** Nearest and second-nearest feature distance, and a 0..1 id for the nearest cell. */
    static double[] voronoi(double x, double y, double z, int seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double f1 = 9, f2 = 9, id = 0;
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                for (int c = -1; c <= 1; c++) {
                    int cx = ix + a, cy = iy + b, cz = iz + c;
                    double px = cx + hash(cx, cy, cz, seed), py = cy + hash(cx, cy, cz, seed + 1),
                            pz = cz + hash(cx, cy, cz, seed + 2);
                    double d = Math.sqrt((px - x) * (px - x) + (py - y) * (py - y) + (pz - z) * (pz - z));
                    if (d < f1) {
                        f2 = f1;
                        f1 = d;
                        id = hash(cx, cy, cz, seed + 3);
                    } else if (d < f2) {
                        f2 = d;
                    }
                }
            }
        }
        return new double[] { f1, f2, id };
    }

    private static double fade(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Adds light: screen-blends a color in by an amount. */
    static int glow(int c, int light, double amount) {
        amount = Math.max(0, Math.min(1, amount));
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            double a = ((c >> shift) & 255) / 255.0, b = ((light >> shift) & 255) / 255.0 * amount;
            double v = 1 - (1 - a) * (1 - b);
            out |= ((int) Math.round(v * 255)) << shift;
        }
        return out;
    }

    static int scale(int c, double f) {
        int r = (int) Math.min(255, ((c >> 16) & 255) * f), g = (int) Math.min(255, ((c >> 8) & 255) * f),
                b = (int) Math.min(255, (c & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    static int clamp(int rgb) {
        return rgb & 0xFFFFFF;
    }

    private VesselTextures() {
    }
}
