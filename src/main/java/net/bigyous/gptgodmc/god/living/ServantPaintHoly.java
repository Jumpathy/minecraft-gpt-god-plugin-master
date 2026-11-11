package net.bigyous.gptgodmc.god.living;

import static net.bigyous.gptgodmc.god.living.ServantTextures.CLEAR;
import static net.bigyous.gptgodmc.god.living.ServantTextures.argb;
import static net.bigyous.gptgodmc.god.living.ServantTextures.clamp;
import static net.bigyous.gptgodmc.god.living.ServantTextures.cloth;
import static net.bigyous.gptgodmc.god.living.ServantTextures.fan;
import static net.bigyous.gptgodmc.god.living.ServantTextures.feathers;
import static net.bigyous.gptgodmc.god.living.ServantTextures.gold;
import static net.bigyous.gptgodmc.god.living.ServantTextures.goldPlate;
import static net.bigyous.gptgodmc.god.living.ServantTextures.halo;
import static net.bigyous.gptgodmc.god.living.ServantTextures.hash;
import static net.bigyous.gptgodmc.god.living.ServantTextures.marble;
import static net.bigyous.gptgodmc.god.living.ServantTextures.mix;
import static net.bigyous.gptgodmc.god.living.ServantTextures.mul;
import static net.bigyous.gptgodmc.god.living.ServantTextures.noise;
import static net.bigyous.gptgodmc.god.living.ServantTextures.opaque;
import static net.bigyous.gptgodmc.god.living.ServantTextures.rune;
import static net.bigyous.gptgodmc.god.living.ServantTextures.runeColumn;
import static net.bigyous.gptgodmc.god.living.ServantTextures.segDist;

import java.util.ArrayList;
import java.util.List;

import net.bigyous.gptgodmc.god.living.ServantTextures.Feather;
import net.bigyous.gptgodmc.god.living.ServantTextures.P;
import net.bigyous.gptgodmc.god.vessel.VesselSkin.Face;

/**
 * The skins of the servants of light: the seraph (ivory and gold, wings of pearl that end in light), the
 * hierophant (a golden mask, violet brocade, a saint's disc) and the colossus (marble veined with gold
 * round a core of cold light).
 */
final class ServantPaintHoly {
    private ServantPaintHoly() {
    }

    private static final int ROBE = 0xFBF7EC, ROBE_SHADE = 0xBDB7D2, SKIN = 0xF8EEE2, SKIN_SHADE = 0xDCC9CB;
    private static final int HAIR = 0xF7E7B4, HAIR_SHADE = 0xC9A04C, HAIR_LIGHT = 0xFFF8DA;
    private static final int LIGHT = 0xFFF3C0, SKY = 0xBFEFFF;

    // ---- The seraph -------------------------------------------------------------------------------------

    static int angel(P p) {
        return switch (p.key) {
            case "head" -> opaque(angelHead(p));
            case "torso" -> opaque(angelTorso(p));
            case "mantle" -> opaque(angelMantle(p));
            case "skirt_up", "skirt_low" -> opaque(angelSkirt(p));
            case "hem" -> angelHem(p);
            case "arm" -> opaque(angelArm(p));
            case "blade" -> angelBlade(p);
            case "hilt" -> opaque(angelHilt(p));
            case "grip" -> opaque(p.v < 6 ? (p.v % 2 == 0 ? 0xF4EEDC : gold(0.6)) : gold(0.9 - 0.3 * (p.v - 6)));
            case "veil" -> angelVeil(p);
            case "wing_in" -> wing(p, WING_IN, 0, 6, 30, 4, PEARL);
            case "wing_out" -> wing(p, WING_OUT, 0, 5, 13.5, 4, PEARL);
            case "wing_low" -> wing(p, WING_LOW, 2, 2, 18, 5.2, DUSK);
            case "halo" -> angelHalo(p);
            case "nimbus" -> halo(p, 0xFFF6D0, 0xFFC050, 0.55, 12);
            default -> opaque(0xFF00FF);
        };
    }

    private static int hair(P p) {
        double strand = noise(p.around() * 0.9, p.down() * 0.12, 11);
        int c = mix(HAIR_SHADE, HAIR, 0.25 + 0.75 * strand);
        if (hash(p.u, p.v / 3, 5) > 0.82) c = mix(c, HAIR_LIGHT, 0.6);
        return c;
    }

    private static int angelHead(P p) {
        if (p.top()) {
            double dx = p.u - 6, dy = p.v - 6;
            double st = 0.5 + 0.5 * Math.sin(Math.atan2(dy, dx) * 7 + Math.hypot(dx, dy) * 0.6);
            return mix(HAIR_SHADE, HAIR, 0.3 + 0.7 * st);
        }
        if (p.bottom()) return SKIN_SHADE;
        int u = p.u, v = p.v;
        int hair = mul(hair(p), 1.0 - 0.16 * p.t);
        if (!p.front()) {
            if (v == 3) return gold(0.7 + 0.2 * Math.sin(p.around() * 0.8));
            if (v == 4) return mul(hair, 0.8);
            return hair;
        }
        // A circlet with a gem at its peak, a fringe above it and locks down each side of the face.
        if (v == 2 && u == 6) return gold(0.95);
        if (v <= 2) return u == 6 ? mul(hair, 0.85) : hair;
        if (v == 3) return u == 6 ? mix(SKY, 0xFFFFFF, p.pulse(0)) : gold(0.85 - 0.06 * Math.abs(u - 6));
        boolean face = v >= 9 ? u >= 1 && u <= 11 : u >= 2 && u <= 10;
        if (!face) return hair;
        int c = mix(SKIN, SKIN_SHADE, 0.07 * Math.abs(u - 6) + 0.06 * Math.max(0, v - 9));
        if (v == 4) c = mix(c, HAIR_SHADE, 0.3);
        boolean eye = (u >= 3 && u <= 5) || (u >= 7 && u <= 9);
        // Eyes of light, without pupils, and a line of gold falling from each.
        if (eye && v == 6) return mix(0xFFFFFF, LIGHT, 0.4 * (1 - p.pulse(0)));
        if (eye && v == 7) return 0xFFDD86;
        if (eye && v == 5) return mix(c, HAIR_SHADE, 0.6);
        if (v >= 5 && v <= 8) c = mix(c, LIGHT, 0.4);
        if ((u == 4 || u == 8) && v >= 8 && v <= 10) return mix(c, 0xE0AE40, 0.8 - 0.15 * (v - 8));
        if (u == 6 && (v == 8 || v == 9)) c = mix(c, SKIN_SHADE, 0.6);
        if (v == 11 && u >= 5 && u <= 7) c = mix(c, 0xC98F8A, 0.55);
        return c;
    }

    private static int angelTorso(P p) {
        if (p.top()) return gold(0.5);
        if (p.bottom()) return ROBE_SHADE;
        int v = p.v, a = (int) Math.floor(p.around());
        int c = cloth(p, ROBE, ROBE_SHADE, 3);
        if (v >= 15) return v == 15 ? gold(0.9) : v == 16 ? gold(a % 4 == 1 ? 0.25 : 0.65) : gold(0.3);
        if (v <= 2) return gold(0.85 - 0.18 * v + (a % 3 == 0 ? 0.1 : 0));
        if (v == 3 && a % 4 < 2) return gold(0.45);
        if (p.front()) {
            // A sunburst over the heart.
            double dx = p.u + 0.5 - 8.5, dy = p.v + 0.5 - 9.5, r = Math.hypot(dx, dy);
            if (r < 1.3) return mix(0xFFFFFF, LIGHT, 0.5 * (1 - p.pulse(0)));
            if (r < 2.4) return gold(0.75 + 0.2 * p.pulse(0));
            double k = Math.atan2(dy, dx) / (Math.PI / 4);
            double off = Math.abs(k - Math.rint(k)) * Math.PI / 4;
            double reach = Math.floorMod((int) Math.rint(k), 2) == 0 ? 5.2 : 3.8;
            if (r * Math.sin(off) < 0.5 && r < reach + 0.6 * p.pulse(0.3)) {
                return mix(gold(0.9), 0xFFFFFF, 0.35 * p.pulse(0.1));
            }
            if (r < 6) c = mix(c, 0xFFE9A8, 0.4 * (1 - r / 6));
        } else if (p.back() && p.u == 8 && v >= 4 && v <= 13) {
            return gold(0.55 + 0.35 * p.pulse(v / 10.0));
        }
        return c;
    }

    private static int angelMantle(P p) {
        if (p.top()) return p.edge() <= 1 ? gold(0.8) : ROBE;
        if (p.bottom()) return gold(0.2);
        int a = (int) Math.floor(p.around());
        if (p.v == 0) return gold(0.97);
        if (p.v == 3) return a % 3 == 0 ? gold(0.75) : gold(0.3);
        if (p.v == 1 && a % 4 == 2) return 0xFFFFFF;
        if (p.v == 2 && a % 4 == 0) return SKY;
        return goldPlate(p, 8);
    }

    private static int angelSkirt(P p) {
        boolean low = p.key.equals("skirt_low");
        if (!p.side()) return p.bottom() ? ROBE_SHADE : ROBE;
        int c = cloth(p, ROBE, ROBE_SHADE, low ? 4 : 3);
        int a = (int) Math.floor(p.around());
        if (p.front()) {
            // A panel down the front, edged in gold, with letters that light in turn.
            int cx = p.fw / 2, off = Math.abs(p.u - cx);
            if (off == 3) return gold(0.62);
            if (off < 3) {
                c = mix(0xFFFDF5, 0xEAE1CA, 0.5 * noise(p.u * 0.7, p.v * 0.7, 9));
                int idx = low ? runeColumn(p, cx, 1, 1, 22) : runeColumn(p, cx, 0, 2, 21);
                if (idx >= 0) return mix(gold(0.7), 0xFFFFFF, 0.7 * p.pulse(-(idx + (low ? 2 : 0)) * 0.22));
            }
        }
        if (low) {
            int fromBottom = p.fh - 1 - p.v;
            if (fromBottom == 0) return gold(0.35);
            if (fromBottom == 1) return gold(0.85);
            if (fromBottom == 2 && a % 4 == 0) return gold(0.65);
            if (fromBottom == 3 && a % 4 == 2) return gold(0.65);
        }
        return c;
    }

    /** The robe does not end: it falls away in streaks of light. */
    private static int angelHem(P p) {
        if (!p.side()) return CLEAR;
        int col = (int) Math.floor(p.around());
        double len = 7 + 9 * hash(col, 0, 77);
        if (hash(col, 1, 77) < 0.18) len *= 0.45;
        double d = p.v + 0.5;
        if (d > len) return CLEAR;
        double f = d / len;
        double shimmer = 0.7 + 0.3 * Math.sin(p.phase + col * 1.9 + p.v * 0.6);
        return argb(225 * Math.pow(1 - f, 1.2) * shimmer, mix(0xFFFFFF, 0xFFE6A0, f));
    }

    private static int angelArm(P p) {
        if (p.top()) return gold(0.7);
        if (p.bottom()) return 0xE6D2CC;
        int v = p.v, a = (int) Math.floor(p.around());
        if (v <= 1) return gold(0.85 - 0.3 * v);
        if (v == 11 || v == 13) return gold(0.4);
        if (v == 12) return a % 2 == 0 ? gold(0.97) : gold(0.7);
        if (v >= 14) {
            int skin = mix(SKIN, SKIN_SHADE, 0.25 + 0.15 * (v - 14));
            return v >= 16 && p.u % 2 == 1 && (p.front() || p.back()) ? mul(skin, 0.86) : skin;
        }
        int c = cloth(p, ROBE, ROBE_SHADE, 6);
        return v == 10 ? mul(c, 0.9) : c;
    }

    /** A blade of light with a slow pulse running down it. */
    private static int angelBlade(P p) {
        int v = p.v;
        double wave = 0.5 + 0.5 * Math.sin(v * 0.55 - p.phase);
        int core = mix(0xFFF6D0, 0xFFFFFF, wave), mid = mix(0xFFE9A0, 0xFFF8D8, wave);
        int edge = mix(0xF4C44E, 0xFFE9A0, wave);
        if (p.top()) return opaque(edge);
        if (p.bottom()) return CLEAR;
        if (p.front() || p.back()) {
            // It narrows to a point.
            int off = Math.abs(p.u - 2), reach = v >= 32 ? 0 : v >= 29 ? 1 : 2;
            if (off > reach) return CLEAR;
            if (off == 0) return opaque(v % 6 == 2 && v < 27 ? 0xE8B94A : core);
            return opaque(off == reach ? edge : mid);
        }
        return v >= 29 ? CLEAR : opaque(edge);
    }

    private static int angelHilt(P p) {
        if ((p.front() || p.back()) && Math.abs(p.u - 7) <= 1) {
            return p.u == 7 && p.v == 1 ? 0xFFFFFF : mix(SKY, 0xFFFFFF, 0.4 * p.pulse(0.3));
        }
        if (p.side() && (p.u == 0 || p.u == p.fw - 1)) return gold(1.0);
        return gold(p.side() ? 0.9 - 0.25 * p.v : 0.7);
    }

    /** Long hair down the back, ending in light. */
    private static int angelVeil(P p) {
        double len = 15 + 9 * hash(p.u, 0, 41) - Math.abs(p.u - 6.5) * 0.6;
        double d = p.v + 0.5;
        if (d > len) return CLEAR;
        double strand = noise(p.u * 0.9 + Math.sin(p.v * 0.2) * 0.8, p.v * 0.1, 11);
        int c = mix(HAIR_SHADE, HAIR, 0.25 + 0.75 * strand);
        double tip = clamp((d - (len - 5)) / 5);
        return argb(255 - 150 * tip, mix(c, HAIR_LIGHT, tip));
    }

    private static int angelHalo(P p) {
        double dx = (p.u + 0.5 - 12) / 12, dy = (p.v + 0.5 - 12) / 12;
        double r = Math.hypot(dx, dy), ang = Math.atan2(dy, dx);
        double ring = Math.abs(r - 0.78);
        if (ring < 0.075) {
            int c = mix(0xFFF0B8, 0xFFFFFF, clamp(1 - ring / 0.075));
            double k = ang / (2 * Math.PI) * 12;
            if (Math.abs(k - Math.rint(k)) < 0.12) c = mix(c, 0xE8B94A, 0.7);
            if (Math.cos(ang - p.phase) > 0.9) c = 0xFFFFFF;
            return opaque(c);
        }
        if (ring >= 0.3) return CLEAR;
        double f = 1 - (ring - 0.075) / 0.225;
        return argb(150 * f * f, 0xFFE9A0);
    }

    /** Feather colors by row: {light vane, shadow vane, shaft, tip light}. */
    private static final int[][] PEARL = { { 0xFFFFFF, 0xC9C4E0, 0xFFFDF2, 0xFFF1BE },
            { 0xFFF7E0, 0xD9C7A8, 0xFFFFFF, 0xFFE9A0 }, { 0xFFF3CC, 0xE9C879, 0xFFFAE6, 0xFFF6CF } };
    private static final int[][] DUSK = { { 0xF1E9F6, 0xAFA2C6, 0xFFFFFF, 0xFFE9A0 },
            { 0xFFEBB0, 0xCF9C3C, 0xFFF6CF, 0xFFF6CF } };
    private static final List<Feather> WING_IN = new ArrayList<>(), WING_OUT = new ArrayList<>(),
            WING_LOW = new ArrayList<>();

    static {
        // Inner span: flight feathers, then two rows of coverts over them, hanging from the arm.
        fan(WING_IN, 7, 2, 7, 28.4, 5, -8, 3, 25, 31, 0, 2.9, 0, 29.5, 37.5);
        fan(WING_IN, 8, 1, 6.6, 28.5, 4.6, -8, 3, 12, 15, 0, 2.4, 1, 29.5, 37.5);
        fan(WING_IN, 9, 1, 6.2, 29, 4.2, -8, 3, 6, 7, 0, 2.0, 2, 29.5, 37.5);
        // Outer span: the primaries fan from the wrist, from straight down to nearly straight out.
        fan(WING_OUT, 9, 0.5, 5.4, 13, 4.4, 4, 66, 31, 31, 6, 2.9, 0, 39.5, 41.5);
        fan(WING_OUT, 8, 0.5, 5.2, 12.5, 4.2, 4, 62, 13, 15, 1, 2.4, 1, 39.5, 41.5);
        fan(WING_OUT, 7, 0.5, 5.0, 12, 4.0, 4, 58, 6.5, 7.5, 0, 2.0, 2, 39.5, 41.5);
        fan(WING_LOW, 6, 3.5, 3, 17, 5.6, -3, 9, 24, 30, 0, 2.7, 0, 19.5, 35.5);
        fan(WING_LOW, 6, 3.5, 2.6, 17, 5.2, -3, 9, 9, 11, 0, 2.3, 1, 19.5, 35.5);
    }

    /** A wing: its feathers, and over them the golden arm they hang from. */
    private static int wing(P p, List<Feather> fs, double ax, double ay, double bx, double by, int[][] tones) {
        double d = segDist(p.u + 0.5, p.v + 0.5, ax, ay, bx, by);
        if (d < 1.5) return opaque(gold(0.97 - 0.35 * d));
        return feathers(p, fs, tones, 140);
    }

    // ---- The hierophant ---------------------------------------------------------------------------------

    private static final int VEST = 0xF4EEDD, VEST_SHADE = 0xBDB4C8, VIOLET = 0x6A3AB8, VIOLET_SHADE = 0x34186A;

    static int priest(P p) {
        return switch (p.key) {
            case "head" -> opaque(priestHead(p));
            case "mitre_low" -> opaque(priestMitre(p, false));
            case "mitre_top" -> opaque(priestMitre(p, true));
            case "disc" -> priestDisc(p);
            case "mantle" -> opaque(priestMantle(p));
            case "torso" -> opaque(priestTorso(p));
            case "skirt_up", "skirt_low" -> opaque(priestSkirt(p));
            case "hem" -> priestHem(p);
            case "arm" -> opaque(priestArm(p));
            case "shaft" -> opaque(priestShaft(p));
            case "sun" -> priestSun(p);
            case "book" -> opaque(priestBook(p));
            case "glow" -> halo(p, 0xFFFFFF, 0xFFD98A, 0.46, 8);
            default -> p.key.startsWith("glyph_") ? priestGlyph(p) : opaque(0xFF00FF);
        };
    }

    /** Violet brocade: folds, a lattice of lighter thread and gold where the threads cross. */
    private static int brocade(P p, int seed) {
        int c = cloth(p, VIOLET, VIOLET_SHADE, seed);
        int a = (int) Math.floor(p.around()), d = (int) Math.floor(p.down());
        boolean one = Math.floorMod(a + d, 6) == 0, two = Math.floorMod(a - d, 6) == 0;
        if (one && two) return gold(0.8);
        return one || two ? mix(c, 0xA47AE8, 0.45) : c;
    }

    private static int priestHead(P p) {
        int c = cloth(p, 0xFDF9EE, 0xD9CFC6, 14);
        if (p.top()) return c;
        if (p.bottom()) return 0x8E6A2A;
        int u = p.u, v = p.v;
        if (!p.front()) {
            if (v == p.fh - 1) return gold(0.5);
            if (v == p.fh - 2) return gold(0.85);
            boolean nearFace = (p.face == Face.EAST && u == 0) || (p.face == Face.WEST && u == p.fw - 1);
            if (nearFace) return gold(0.7);
            int lozenge = Math.abs(u - 6) + Math.abs(v - 6);
            if (!p.back() && lozenge <= 2) return lozenge == 2 ? gold(0.7) : VIOLET;
            if (p.back()) {
                int dx = Math.abs(u - 6), dy = Math.abs(v - 6);
                if (dx + dy <= 1) return gold(0.95);
                if ((dx == 0 || dy == 0 || dx == dy) && Math.max(dx, dy) <= 3) return gold(0.6);
            }
            return c;
        }
        // A golden mask in the dark of the hood: slits of light for eyes, a third eye on the brow.
        boolean mask = u >= 2 && u <= 10 && v >= 2;
        if (v == 11) mask = u >= 3 && u <= 9;
        if (v == 12) mask = u >= 4 && u <= 8;
        if (!mask) {
            if (v == 1) return gold(0.7);
            if (v == 0 || u == 0 || u == 12) return c;
            return mix(0x22103F, 0x45267C, noise(u * 0.8, v * 0.8, 15));
        }
        double sh = 0.86 - 0.014 * (u - 6) * (u - 6) - 0.022 * (v - 2);
        int m = gold(sh);
        boolean rim = u == 2 || u == 10 || v == 2 || v == 12 || (v == 11 && (u == 3 || u == 9));
        if (rim) m = gold(0.36);
        boolean eye = (u >= 3 && u <= 5) || (u >= 7 && u <= 9);
        if (u == 6 && v >= 5 && v <= 9) m = gold(sh + 0.2);
        if (eye && v == 5) m = gold(0.45);
        if (eye && v == 6) return mix(0xFFFFFF, 0xCFF3FF, 0.5 * (1 - p.pulse(0)));
        if (eye && v == 7) m = gold(0.3);
        if (u == 6 && v == 3) return mix(SKY, 0xFFFFFF, p.pulse(0.5));
        if (u == 6 && v == 4) return 0x8FDCF8;
        if (v == 4 && (u == 5 || u == 7)) m = gold(0.4);
        if ((u == 4 || u == 8) && v >= 8 && v <= 9) m = gold(0.42);
        if (v == 11 && u >= 5 && u <= 7) m = gold(0.3);
        return m;
    }

    private static int priestMitre(P p, boolean top) {
        int c = cloth(p, 0xFDF9EE, 0xD9CFC6, top ? 16 : 15);
        if (p.top()) return top ? gold(0.9) : c;
        if (p.bottom()) return VEST_SHADE;
        int cx = p.fw / 2, off = Math.abs(p.u - cx);
        if (p.u == 0 || p.u == p.fw - 1) return gold(0.6);
        if (top) {
            if (p.v <= 1) return gold(0.97 - 0.2 * p.v);
            if (off <= 1) return gold(off == 0 ? 0.9 : 0.6);
            return (p.u + p.v) % 4 == 0 ? mix(c, 0xA47AE8, 0.7) : c;
        }
        if (p.v >= p.fh - 2) return gold(p.v == p.fh - 2 ? 0.9 : 0.4);
        if (p.v == 0) return gold(0.7);
        if (off <= 1) {
            if (p.v == 3 && off == 0) return mix(0xB78CFF, 0xFFFFFF, 0.6 * p.pulse(0));
            if (p.v >= 2 && p.v <= 4) return gold(off == 0 ? 0.35 : 0.95);
            return gold(off == 0 ? 0.9 : 0.6);
        }
        // Violet lozenges stitched into the linen.
        int lx = Math.abs(off - 4), ly = Math.abs(p.v - 3);
        if (lx + ly <= 1) return lx + ly == 0 ? gold(0.85) : VIOLET;
        return c;
    }

    /** A saint's disc: an engraved ring of gold round thin rays, with glass of light between them. */
    private static int priestDisc(P p) {
        double dx = (p.u + 0.5 - 18) / 18, dy = (p.v + 0.5 - 18) / 18;
        double r = Math.hypot(dx, dy), ang = Math.atan2(dy, dx);
        if (r > 0.98) return CLEAR;
        if (r >= 0.82) {
            double k = ang / (2 * Math.PI) * 16;
            if (Math.abs(k - Math.rint(k)) < 0.14 && Math.abs(r - 0.9) < 0.035) return opaque(gold(0.15));
            double g = 0.55 + 0.3 * Math.cos((r - 0.9) * 22);
            if (Math.cos(ang - p.phase) > 0.94) g = 1;
            return opaque(gold(g));
        }
        if (r >= 0.77) return opaque(gold(0.2));
        if (r < 0.34) return argb(45, LIGHT);
        if (r < 0.41) return opaque(gold(0.75));
        // Open work: rays of gold, long and short in turn, with the world showing between them.
        double k = ang / (2 * Math.PI) * 16;
        double off = Math.abs(k - Math.rint(k)) * 2 * Math.PI / 16 * r * 18;
        boolean longRay = Math.floorMod((int) Math.rint(k), 2) == 0;
        if (!longRay && off < 1.5 && Math.abs(r - 0.66) < 0.05) return opaque(mix(SKY, 0xFFFFFF, p.pulse(k * 0.1)));
        if (off < 0.95 && (longRay || r < 0.62)) return opaque(mix(gold(0.8), 0xFFFFFF, 0.45 * p.pulse(r)));
        return argb(26 + 14 * p.pulse(0.5), 0xFFE9A0);
    }

    private static int priestMantle(P p) {
        if (p.top()) return p.edge() <= 1 ? gold(0.8) : VEST;
        if (p.bottom()) return gold(0.2);
        int a = (int) Math.floor(p.around());
        if (p.v == 0) return gold(0.97);
        if (p.v == 4) return a % 2 == 0 ? gold(0.6) : gold(0.25);
        if (p.v == 2 && a % 5 == 2) return mix(0x9A5AE8, 0xFFFFFF, 0.4 * p.pulse(a * 0.1));
        return goldPlate(p, 18);
    }

    /** The pale band down the front of the vestments, edged in gold, with its letters; or -1 off it. */
    private static int stole(P p, int top, int count, int seed, int first) {
        if (!p.front()) return -1;
        int cx = p.fw / 2, off = Math.abs(p.u - cx);
        if (off == 3) return gold(0.62);
        if (off > 3) return -1;
        int idx = runeColumn(p, cx, top, count, seed);
        if (idx >= 0) return mix(gold(0.55), 0xFFFFFF, 0.75 * p.pulse(-(idx + first) * 0.2));
        return mix(0xF8F2E2, 0xDAD0C2, 0.6 * noise(p.u * 0.7, p.v * 0.7, seed));
    }

    private static int priestTorso(P p) {
        if (p.top()) return gold(0.5);
        if (p.bottom()) return VIOLET_SHADE;
        int v = p.v, a = (int) Math.floor(p.around());
        if (v == 15) return gold(0.85);
        if (v == 16) return a % 2 == 0 ? gold(0.55) : gold(0.3);
        int s = stole(p, 1, 2, 31, 0);
        return s >= 0 ? s : brocade(p, 17);
    }

    private static int priestSkirt(P p) {
        boolean low = p.key.equals("skirt_low");
        if (!p.side()) return VIOLET_SHADE;
        int a = (int) Math.floor(p.around());
        int fromBottom = p.fh - 1 - p.v;
        if (low) {
            if (fromBottom == 0) return VEST;
            if (fromBottom == 1) return gold(0.4);
            if (fromBottom == 2) return gold(0.88);
        }
        int s = low ? stole(p, 0, 1, 33, 4) : stole(p, 0, 2, 32, 2);
        if (s >= 0) return s;
        int c = brocade(p, low ? 21 : 20);
        if (low && fromBottom == 3 && a % 4 < 2) return gold(0.65);
        if (low && fromBottom == 4 && a % 4 >= 2) return gold(0.5);
        return c;
    }

    /** It does not walk: it stands on a pool of light. */
    private static int priestHem(P p) {
        if (p.top()) return CLEAR;
        if (p.bottom()) return argb(120, LIGHT);
        double f = (p.v + 0.5) / p.fh;
        double shimmer = 0.75 + 0.25 * Math.sin(p.phase + p.around() * 0.9);
        return argb(175 * f * f * shimmer, mix(0xFFE9A0, 0xFFFFFF, f));
    }

    private static int priestArm(P p) {
        if (p.top()) return gold(0.6);
        if (p.bottom()) return 0xE9DCC8;
        int v = p.v, a = (int) Math.floor(p.around());
        if (v >= 13) return mix(0xF6F0E0, 0xCFC4C8, 0.2 + 0.15 * (v - 13));
        if (v == 10) return gold(0.88);
        if (v == 11) return a % 3 == 0 ? 0xFFFFFF : gold(0.65);
        if (v == 12) return gold(0.35);
        if (v == 0) return gold(0.7);
        return brocade(p, 19);
    }

    private static int priestShaft(P p) {
        if (!p.side()) return gold(0.7);
        int v = p.v;
        if (v < 3) return gold(0.92);
        if (v % 12 <= 1) return gold(0.78 - 0.3 * (v % 12));
        if (Math.floorMod(v + (int) Math.floor(p.around()) * 2, 8) == 0) return gold(0.6);
        return mix(0x3A2416, 0x5A3A22, hash(p.u, v, 7));
    }

    /** The staff's head: a sun, a ring of gold round a core of light. */
    private static int priestSun(P p) {
        double dx = p.u + 0.5 - 9, dy = p.v + 0.5 - 9, r = Math.hypot(dx, dy), ang = Math.atan2(dy, dx);
        if (r < 2.6) return opaque(mix(0xFFFFFF, 0xFFF0B0, r / 2.6 * (0.6 + 0.4 * (1 - p.pulse(0)))));
        if (r < 3.4) return argb(150, 0xFFE9A0);
        if (r < 4.5) return opaque(gold(0.72 + 0.25 * Math.cos(ang * 2 - p.phase)));
        double k = ang / (Math.PI / 4);
        double off = Math.abs(k - Math.rint(k)) * Math.PI / 4;
        double reach = Math.floorMod((int) Math.rint(k), 2) == 0 ? 8.8 : 7.0;
        if (r < reach && r * Math.sin(off) < 1.5 * (1 - (r - 4.5) / (reach - 4.5))) {
            return opaque(gold(0.6 + 0.37 * p.pulse(r / 9)));
        }
        return CLEAR;
    }

    private static int priestBook(P p) {
        if (p.bottom()) return p.edge() == 0 ? gold(0.6) : 0x3B1F6B;
        if (!p.top()) return p.v == 0 ? 0xEFE6CC : 0x4A2A86;
        // The open pages: lines of writing that light one after another.
        int u = p.u, v = p.v;
        if (u == 5 || u == 6) return 0xCFC2A2;
        if (p.edge() == 0) return 0xD8CBA6;
        int page = mix(0xFFFBEE, 0xEDE3C8, Math.abs(u - 5.5) / 6.0);
        if (v % 2 == 1 && u != 1 && u != 10 && hash(u, v, 5) > 0.25) {
            double lineLit = p.pulse(-(v / 2) * 0.22 - (u > 5 ? 0.5 : 0));
            return lineLit > 0.72 ? 0xF0A820 : 0x6A3AB8;
        }
        return page;
    }

    /** One of its letters, written in light. */
    private static int priestGlyph(P p) {
        int id = p.key.charAt(p.key.length() - 1) - '0';
        int letter = id * 2 + 1;
        if (rune(letter, p.u - 2, p.v - 1)) return opaque(mix(0xFFE08A, 0xFFFFFF, p.pulse(id * 0.25)));
        int[][] near = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] n : near) {
            if (rune(letter, p.u - 2 + n[0], p.v - 1 + n[1])) return argb(70, 0xFFE08A);
        }
        return CLEAR;
    }

    // ---- The colossus ----------------------------------------------------------------------------------

    private static final int CORE = 0xBFF4FF, CORE_DEEP = 0x5FCFF0, GROOVE = 0x7D788C, PIT = 0x3A3648;
    private static final int INDIGO = 0x34338F, INDIGO_SHADE = 0x14133A;

    static int guard(P p) {
        return switch (p.key) {
            case "leg" -> opaque(guardLeg(p));
            case "fauld" -> opaque(guardFauld(p));
            case "tabard" -> guardTabard(p);
            case "torso" -> opaque(guardTorso(p));
            case "pauldron" -> opaque(guardPauldron(p));
            case "arm_up" -> opaque(guardArm(p));
            case "gauntlet" -> opaque(guardGauntlet(p));
            case "helm" -> opaque(guardHelm(p));
            case "crest" -> guardCrest(p);
            case "shield" -> opaque(guardShield(p));
            case "shaft" -> opaque(guardShaft(p));
            case "blade" -> guardBlade(p);
            case "cape" -> guardCape(p);
            case "glow" -> halo(p, 0xFFFFFF, 0x7FDFFF, 0.42, 0);
            case "ward" -> guardWard(p);
            default -> opaque(0xFF00FF);
        };
    }

    /** The ground it holds: a turning circle of light, two rings with letters between and a star within. */
    private static int guardWard(P p) {
        double dx = (p.u + 0.5 - 24) / 24, dy = (p.v + 0.5 - 24) / 24;
        double r = Math.hypot(dx, dy), ang = Math.atan2(dy, dx);
        if (r > 0.99) return CLEAR;
        double pulse = 0.7 + 0.3 * p.pulse(0);
        if (Math.abs(r - 0.965) < 0.022 || Math.abs(r - 0.69) < 0.02) return argb(220 * pulse, mix(CORE, 0xFFFFFF, 0.5));
        if (r > 0.725 && r < 0.935) {
            double k = ang / (2 * Math.PI) * 12 + 12;
            int cell = (int) Math.floor(k);
            int gx = (int) Math.floor((k - cell) * 5) - 1, gy = (int) Math.floor((0.935 - r) / 0.21 * 5);
            if (rune(cell * 3 + 1, gx, gy)) return argb(235 * pulse, 0xFFE9A0);
        } else if (r < 0.69) {
            for (int i = 0; i < 6; i++) {
                double a = ang - i * Math.PI / 3;
                if (Math.abs(r * Math.cos(a) - 0.34) < 0.017 && r * Math.abs(Math.sin(a)) < 0.6) {
                    return argb(175 * pulse, CORE);
                }
            }
        }
        return argb(34 * (1 - r) + (r > 0.69 ? 18 : 0), CORE_DEEP);
    }

    /** A seam between plates through which the light inside shows. */
    private static int seam(P p, double offset) {
        return mix(CORE_DEEP, 0xFFFFFF, p.pulse(offset + p.around() * 0.03));
    }

    private static int guardTorso(P p) {
        int c = marble(p, 40);
        if (p.top()) return p.edge() <= 1 ? gold(0.6) : c;
        if (p.bottom()) return GROOVE;
        int u = p.u, v = p.v;
        if (v <= 1) return gold(0.92 - 0.35 * v);
        if (v >= 24) return gold(v == 24 ? 0.72 : 0.35);
        if (p.front()) {
            // The core: a seal of gold round a disc of light whose marks turn.
            double dx = u + 0.5 - 17.5, dy = v + 0.5 - 10.5, r = Math.hypot(dx, dy), ang = Math.atan2(dy, dx);
            if (r < 3.3) {
                double k = (ang + p.phase) / (2 * Math.PI) * 6;
                boolean mark = Math.abs(k - Math.rint(k)) < 0.17 && r > 1.5;
                return mark ? 0x3FA9D0 : mix(0xFFFFFF, CORE, r / 3.3);
            }
            if (r < 4.5) return gold(0.85 - 0.25 * (r - 3.3));
            if (r < 5.4) return mix(c, PIT, 0.75);
            if (r < 6.5) return gold(0.5 + 0.3 * Math.cos(ang * 8));
            if (r < 11) c = mix(c, CORE, 0.34 * (1 - (r - 6.5) / 4.5) * (0.7 + 0.3 * p.pulse(0)));
            if (Math.abs(Math.abs(dx) - Math.abs(dy)) < 0.6 && r < 13 && v < 19) return gold(0.6);
            if (u == 17 && v > 17) c = mix(c, GROOVE, 0.8);
            if (v == 19 || v == 22) return mix(c, GROOVE, 0.85);
            if (v == 20 || v == 23) c = mix(c, 0xFFFFFF, 0.5);
        } else {
            if (v == 12) return mix(c, GROOVE, 0.85);
            if (v == 13) c = mix(c, 0xFFFFFF, 0.5);
            if (p.back()) {
                int idx = runeColumn(p, 17, 3, 3, 51);
                if (idx >= 0) return mix(CORE_DEEP, 0xFFFFFF, p.pulse(-idx * 0.25));
                if (Math.abs(u - 17) == 3 && v >= 2 && v <= 23) return gold(0.5);
            }
        }
        if (p.side() && (u == 2 || u == p.fw - 3) && (v == 4 || v == 21)) return gold(0.97);
        return c;
    }

    private static int guardHelm(P p) {
        int c = marble(p, 41);
        int u = p.u, v = p.v;
        if (p.top()) return u == 7 ? gold(0.8) : p.edge() == 0 ? gold(0.5) : c;
        if (p.bottom()) return PIT;
        if (v <= 1) return gold(0.92 - 0.3 * v);
        if (v == 2 && u % 4 == 1) return CORE;
        if (v >= p.fh - 2) return gold(v == p.fh - 2 ? 0.62 : 0.3);
        if (p.front()) {
            // A great helm: no face, only a slit of light and the gold bar of the nasal.
            boolean nasal = u >= 6 && u <= 8 && v >= 3;
            if (nasal) return gold(u == 7 ? 0.92 : 0.6);
            if (v >= 5 && v <= 8 && u >= 2 && u <= 12) {
                if (v == 5 || v == 8) return PIT;
                return mix(0x7FDFFF, 0xFFFFFF, 0.6 + 0.4 * Math.sin(p.phase - u * 0.5));
            }
            if ((v == 4 || v == 9) && u >= 1 && u <= 13) return gold(0.55);
            if ((u == 1 || u == 13) && v >= 4 && v <= 9) return gold(0.55);
            if (v >= 10 && v <= 12 && (u == 2 || u == 4 || u == 10 || u == 12)) return PIT;
            if (v == 4 || v == 9) c = mix(c, CORE, 0.3);
        } else {
            if (v == 6) return gold(0.5);
            if (v == 7) c = mix(c, GROOVE, 0.6);
            if (p.back() && u == 7 && v > 7) c = mix(c, GROOVE, 0.7);
        }
        return c;
    }

    private static int guardPauldron(P p) {
        if (p.bottom()) return PIT;
        if (p.edge() == 0) return gold(0.88);
        if (p.edge() == 1) return gold(0.45);
        int c = marble(p, 42);
        if (p.top()) {
            double r = Math.hypot(p.u + 0.5 - p.fw / 2.0, p.v + 0.5 - p.fh / 2.0);
            if (r < 1.7) return mix(0xFFFFFF, 0x7FDFFF, r / 1.7);
            if (r < 2.8) return gold(0.7);
        }
        if ((p.face == Face.EAST || p.face == Face.WEST) && rune(3, p.u - (p.fw / 2 - 1), p.v - 3)) {
            return mix(CORE_DEEP, 0xFFFFFF, p.pulse(0.2));
        }
        return c;
    }

    private static int guardArm(P p) {
        int c = marble(p, 43);
        if (!p.side()) return p.bottom() ? PIT : c;
        if (p.v == 6) return gold(0.8);
        if (p.v == 7) return gold(0.4);
        if (p.v == p.fh - 1) return seam(p, 0.1);
        return c;
    }

    private static int guardGauntlet(P p) {
        if (p.top()) return gold(0.5);
        if (p.bottom()) return 0x9A95A8;
        int v = p.v;
        int c = marble(p, 44);
        if (v <= 1) return gold(0.88 - 0.3 * v);
        if (v == 8 || v == 13) return seam(p, v / 22.0);
        if (v == 9 || v == 14) c = mix(c, 0xFFFFFF, 0.4);
        if (v == 16) return gold(0.92);
        if (v == 17) return gold(0.5);
        if (v > 17) {
            c = mix(c, 0xA9A3B8, 0.3);
            if (v > 18 && (p.front() || p.back()) && p.u % 3 == 2) c = mix(c, GROOVE, 0.55);
        }
        return c;
    }

    private static int guardLeg(P p) {
        int c = marble(p, 45);
        if (p.top()) return c;
        if (p.bottom()) return 0x4A4658;
        int u = p.u, v = p.v;
        if (v <= 1) return gold(0.82 - 0.3 * v);
        if (p.front() && v >= 9 && v <= 14) {
            // The knee: a boss of gold with a light in it.
            int off = Math.abs(u - 6), half = v == 9 || v == 14 ? 3 : 4;
            if (off <= half) {
                if (off == 0 && (v == 11 || v == 12)) return mix(CORE, 0xFFFFFF, p.pulse(0.4));
                return gold(0.88 - 0.08 * (v - 9) - 0.06 * off);
            }
        }
        if (v >= 25) {
            if (v == 25) return gold(0.95);
            if (p.front() && v >= 27 && u % 4 == 2) return gold(0.2);
            return gold(0.7 - 0.09 * (v - 25));
        }
        if (v == 20) return seam(p, 0.6);
        if (v == 8 || v == 15) return mix(c, GROOVE, 0.8);
        if (p.front() && u == 6 && v > 15 && v < 25) c = mix(c, 0xFFFFFF, 0.6);
        return c;
    }

    private static int guardFauld(P p) {
        if (p.top()) return gold(0.45);
        if (p.bottom()) return 0x4A4658;
        int a = (int) Math.floor(p.around()), v = p.v;
        if (v == 0) return gold(0.92);
        if (v == 1) return a % 4 < 2 ? gold(0.75) : gold(0.5);
        if (v == 2) return gold(0.35);
        // Hanging plates, each ending in a point.
        int cell = Math.floorMod(a, 6);
        if (cell == 5) return PIT;
        if (v == 7 && (cell == 0 || cell == 4)) return PIT;
        if (v == 8 && cell != 2) return PIT;
        if (cell == 0 || cell == 4 || v == 8 || (v == 7 && (cell == 1 || cell == 3))) return gold(0.58);
        return marble(p, 46);
    }

    private static int indigo(P p, int seed) {
        double f = 0.5 + 0.5 * Math.sin(p.u * 0.75 + 0.8 * Math.sin(p.v * 0.08 + seed));
        f = 0.75 * f + 0.25 * noise(p.u * 0.4, p.v * 0.2, seed);
        int c = mix(INDIGO_SHADE, INDIGO, f);
        return ((p.u + p.v) & 1) == 0 ? mul(c, 0.95) : c;
    }

    /** A sun stitched in gold, centered at (cx, cy): a disc, a ring and rays out to reach. */
    private static boolean stitchedSun(P p, double cx, double cy, double disc, double reach) {
        double dx = p.u + 0.5 - cx, dy = p.v + 0.5 - cy, r = Math.hypot(dx, dy);
        if (r < disc) return true;
        if (r < disc + 0.9) return false;
        if (r < disc + 1.9) return true;
        double k = Math.atan2(dy, dx) / (Math.PI / 6);
        double off = Math.abs(k - Math.rint(k)) * Math.PI / 6;
        double end = Math.floorMod((int) Math.rint(k), 2) == 0 ? reach : reach * 0.78;
        return r > disc + 2.6 && r < end && r * Math.sin(off) < 0.75;
    }

    private static int guardTabard(P p) {
        int u = p.u, v = p.v;
        int notch = v - 16;
        if (v >= 17 && Math.abs(u - 6) < notch) return CLEAR;
        if (u == 0 || u == 12 || (v >= 17 && Math.abs(u - 6) == notch)) return opaque(gold(0.7));
        if (v == 0) return opaque(gold(0.5));
        double dx = u + 0.5 - 6.5, dy = v + 0.5 - 8.5, r = Math.hypot(dx, dy);
        if (r < 1.7) return opaque(gold(0.95));
        boolean ray = Math.abs(dx) < 0.6 || Math.abs(dy) < 0.6 || Math.abs(Math.abs(dx) - Math.abs(dy)) < 0.6;
        if (ray && r > 2.5 && r < 4.4) return opaque(gold(0.7));
        return opaque(indigo(p, 48));
    }

    private static int guardShield(P p) {
        int u = p.u, v = p.v;
        if (p.back()) {
            if (p.edge() == 0) return gold(0.5);
            if (v == 14 || v == 15 || v == 22 || v == 23) return 0x6A4A2A;
            return mix(0x3A2A1C, 0x4E3824, hash(u / 4, v, 9) * 0.6 + (u % 4 == 0 ? -0.4 : 0.2));
        }
        if (!p.front()) return gold(0.6);
        if (p.edge() == 0) return gold(0.92);
        if (p.edge() == 1) return gold(0.5);
        int c = marble(p, 47);
        if (v == 9 || v == 28) return gold(0.78);
        if (v == 10 || v == 29) return gold(0.4);
        // The sun of its god, with a core of light.
        double dx = u + 0.5 - 11.5, dy = v + 0.5 - 19, r = Math.hypot(dx, dy);
        if (r < 2.5) return mix(0xFFFFFF, CORE, r / 2.5 * (0.6 + 0.4 * (1 - p.pulse(0))));
        if (r < 3.7) return gold(0.85);
        double k = Math.atan2(dy, dx) / (Math.PI / 6);
        double off = Math.abs(k - Math.rint(k)) * Math.PI / 6;
        double reach = Math.floorMod((int) Math.rint(k), 2) == 0 ? 8.4 : 6.4;
        if (r > 4.4 && r < reach && r * Math.sin(off) < 0.9 * (1 - (r - 4.4) / (reach - 4.4)) + 0.25) return gold(0.7);
        if (r < 8.6) c = mix(c, CORE, 0.25 * (1 - r / 8.6));
        if ((u == 4 || u == 18) && (v == 4 || v == 33)) return gold(0.97);
        if (u == 11 && (v < 9 || v > 29)) c = mix(c, GROOVE, 0.7);
        return c;
    }

    private static int guardShaft(P p) {
        if (!p.side()) return gold(0.7);
        int v = p.v;
        if (v < 4 || v > 90) return gold(0.9 - 0.1 * (v % 4));
        if (v % 12 <= 1) return gold(0.78 - 0.3 * (v % 12));
        return mix(0x1E1A22, 0x322C3A, hash(p.u, v, 8));
    }

    /** The halberd's head: a crescent axe with an edge of light, a spike above and a spike behind. */
    private static int guardBlade(P p) {
        double px = p.u + 0.5, py = p.v + 0.5;
        if (px >= 9.6 && px <= 12.4 && py >= 8.5) {
            return opaque(p.v % 5 == 0 ? gold(0.92) : gold(0.65 - 0.2 * Math.abs(px - 11)));
        }
        int steel = mix(0xE4E9F1, 0x8E97AB, 0.55 * noise(px * 0.5, py * 0.5, 3));
        if (py < 9.5) {
            double half = 2.3 * (py / 9.5);
            if (Math.abs(px - 11) > half) return CLEAR;
            return opaque(Math.abs(px - 11) < 0.6 ? 0xFFFFFF : half - Math.abs(px - 11) < 0.8 ? CORE : steel);
        }
        if (px < 9.6) {
            double r = Math.hypot(px - 10.5, py - 17);
            boolean tall = py >= 8.6 && py <= 25.4;
            boolean bitten = Math.hypot(px - 7.2, py - 7.6) <= 3.0 || Math.hypot(px - 7.2, py - 26.4) <= 3.0;
            if (r <= 9.6 && tall && !bitten) {
                if (r > 8.3) return opaque(mix(CORE, 0xFFFFFF, p.pulse(py / 28)));
                if (Math.abs(r - 5.8) < 0.55) return opaque(gold(0.72));
                return opaque(mix(steel, 0x6E7890, clamp((8.3 - r) / 8.3) * 0.35));
            }
            if (r > 9.6 && r < 10.9 && tall) return argb(120 * (1 - (r - 9.6) / 1.3), CORE);
            return CLEAR;
        }
        double half = 2.7 * (1 - (px - 12.4) / 8.4);
        if (half > 0 && Math.abs(py - 16.5) <= half) return opaque(Math.abs(py - 16.5) < 0.6 ? 0xFFFFFF : steel);
        return CLEAR;
    }

    private static int guardCape(P p) {
        int u = p.u, v = p.v;
        double edge = 39.5 + 4 * Math.abs(Math.sin(Math.PI * (u + 0.5) / 11));
        if (v + 0.5 > edge) return CLEAR;
        if (u <= 1 || u >= 31 || v + 0.5 > edge - 2) return opaque(gold(u == 0 || u == 32 ? 0.5 : 0.75));
        if (stitchedSun(p, 16.5, 18, 3.4, 11)) return opaque(gold(0.62 + 0.25 * noise(u * 0.8, v * 0.8, 6)));
        return opaque(indigo(p, 49));
    }

    /** A crest of dark horsehair that ends in light, on a fin of gold. */
    private static int guardCrest(P p) {
        int u = p.u, v = p.v;
        if (v >= 10) return u >= 2 && u <= 19 ? opaque(gold(v == 10 ? 0.9 : v == 11 ? 0.6 : 0.35)) : CLEAR;
        double f = clamp((u + 0.5) / 22.0);
        double h = 9.5 * Math.pow(Math.sin(Math.PI * (0.08 + 0.84 * f)), 0.6) - 1.6 * hash(u, 0, 12);
        double up = 10 - (v + 0.5);
        if (up > h) return CLEAR;
        double tip = clamp(up / Math.max(1, h));
        int c = mix(mix(INDIGO_SHADE, INDIGO, hash(u, 1, 12)), CORE, tip * tip);
        return argb(255 - 110 * tip * tip * tip, c);
    }
}
