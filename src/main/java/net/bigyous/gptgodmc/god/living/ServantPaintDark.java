package net.bigyous.gptgodmc.god.living;

import static net.bigyous.gptgodmc.god.living.ServantTextures.CLEAR;
import static net.bigyous.gptgodmc.god.living.ServantTextures.argb;
import static net.bigyous.gptgodmc.god.living.ServantTextures.clamp;
import static net.bigyous.gptgodmc.god.living.ServantTextures.fbm;
import static net.bigyous.gptgodmc.god.living.ServantTextures.halo;
import static net.bigyous.gptgodmc.god.living.ServantTextures.hash;
import static net.bigyous.gptgodmc.god.living.ServantTextures.lit;
import static net.bigyous.gptgodmc.god.living.ServantTextures.membrane;
import static net.bigyous.gptgodmc.god.living.ServantTextures.mix;
import static net.bigyous.gptgodmc.god.living.ServantTextures.mul;
import static net.bigyous.gptgodmc.god.living.ServantTextures.night;
import static net.bigyous.gptgodmc.god.living.ServantTextures.noise;
import static net.bigyous.gptgodmc.god.living.ServantTextures.opaque;
import static net.bigyous.gptgodmc.god.living.ServantTextures.runeColumn;

import net.bigyous.gptgodmc.god.living.ServantTextures.P;

/**
 * The skins of the servants of the dark: the cinder imp (cracked basalt over fire, a grin of flame,
 * wings of torn membrane) and the wraith (a robe cut from the night sky that frays into mist, two eyes
 * of soul-fire, a scythe with an edge of cold light).
 */
final class ServantPaintDark {
    private ServantPaintDark() {
    }

    // ---- The imp ----------------------------------------------------------------------------------------

    private static final int COAL = 0x0C0809, EMBER = 0xFF8A1A, FLAME = 0xFFE27A, BLOOD = 0xC2300E;

    static int imp(P p) {
        return switch (p.key) {
            case "head" -> opaque(impHead(p));
            case "ear" -> impEar(p);
            case "horn" -> opaque(impHorn(p, false));
            case "horn_tip" -> opaque(impHorn(p, true));
            case "torso" -> opaque(impTorso(p));
            case "arm", "leg" -> opaque(impLimb(p));
            case "tail" -> opaque(mul(impSkin(p, 66), 0.85));
            case "tail_tip" -> impTailTip(p);
            case "wing_in" -> membrane(p, 0, 4, new double[][] { { 18, 2 }, { 16, 19 }, { 2, 18 } }, WING, 222, 67);
            case "wing_out" -> impWingOut(p);
            case "flame" -> impFlame(p);
            case "glow" -> halo(p, 0xFFB050, 0xD0300A, 0.36, 0);
            default -> opaque(0xFF00FF);
        };
    }

    /** Membrane colors: {bone, bone light, skin shadow, skin lit, vein}. */
    private static final int[] WING = { 0x1E1517, 0x5A3A30, 0x3E0B08, 0xC43A18, 0xFF8A2A };

    /** Basalt skin split by cracks that glow and breathe. */
    private static int impSkin(P p, int seed) {
        int c = impHide(p, seed);
        double n = fbm(p.around() * 0.33, p.down() * 0.33, seed);
        double crack = Math.abs(noise(p.around() * 0.17 + 9, p.down() * 0.17, seed + 3) - 0.5);
        double heat = 0.55 + 0.45 * p.pulse(n * 2);
        if (crack < 0.012) return mix(EMBER, FLAME, heat);
        if (crack < 0.032) return mix(c, BLOOD, 0.8 * heat);
        return c;
    }

    /** The basalt itself, without cracks. */
    private static int impHide(P p, int seed) {
        double n = fbm(p.around() * 0.33, p.down() * 0.33, seed);
        int c = mix(0x1A1215, 0x45323A, n);
        if (p.side()) c = mul(c, 1.12 - 0.3 * p.t);
        return hash(p.u, p.v, seed) > 0.92 ? mul(c, 1.2) : c;
    }

    private static int impHead(P p) {
        if (p.bottom()) return COAL;
        if (!p.front()) return impSkin(p, 60);
        int c = impHide(p, 60);
        int u = p.u, v = p.v;
        int m = u <= 6 ? u : 13 - u;
        // Slanted eyes of fire under a heavy brow.
        boolean eye = (v == 4 && m >= 1 && m <= 3) || (v == 5 && m >= 1 && m <= 4) || (v == 6 && m >= 2 && m <= 4);
        if (eye) {
            // No pupils: only fire, hottest in the middle.
            boolean core = v == 5 && (m == 2 || m == 3);
            return core ? mix(0xFFFBD0, FLAME, 0.5 * (1 - p.pulse(0))) : mix(FLAME, EMBER, (v - 4) * 0.35 + 0.2);
        }
        if (v == 3 && m >= 1 && m <= 5) return COAL;
        if (v >= 3 && v <= 7 && m <= 5) c = lit(c, 0xFF6A10, 0.2);
        if (v == 7 && m == 6) return COAL;
        // A grin with fire behind the teeth.
        if (v == 8 && (u == 1 || u == 12)) return EMBER;
        if (v >= 8 && v <= 10 && u >= 2 && u <= 11) {
            if (v == 9) return mix(0xFFF0A0, 0xFFC040, 0.5 - 0.5 * Math.cos(p.phase + u));
            if (v == 8) return u % 2 == 1 ? EMBER : 0x120A0B;
            return u % 2 == 0 ? 0xFF7A14 : 0x120A0B;
        }
        if (v == 11 && m >= 3) c = lit(c, 0xFF6A10, 0.25);
        return c;
    }

    private static int impEar(P p) {
        double px = p.u + 0.5, py = p.v + 0.5;
        double top = 1.5 - 0.7 * px / 9.8, bottom = 6.5 - 5.7 * px / 9.8;
        if (py < top || py > bottom) return CLEAR;
        if (Math.min(py - top, bottom - py) < 0.9) return opaque(0x1C1416);
        return opaque(mix(BLOOD, 0x5A120C, px / 9.8));
    }

    private static int impHorn(P p, boolean tip) {
        if (!p.side()) return tip ? mix(FLAME, 0xFFFFFF, p.pulse(0)) : COAL;
        if (tip) {
            // From a dull red where it leaves the horn to a point that burns.
            double f = 1 - p.t;
            return mix(mix(0x5A120C, EMBER, f), FLAME, clamp(f * 1.6 - 0.7) * (0.6 + 0.4 * p.pulse(0)));
        }
        int c = p.v % 2 == 0 ? 0x1A1214 : 0x3A2A2C;
        if (p.v == p.fh - 1) return mix(BLOOD, EMBER, p.pulse(0.3));
        return p.v <= 1 ? mix(c, 0x5A120C, 0.6) : c;
    }

    private static int impTorso(P p) {
        int c = impSkin(p, 61);
        if (!p.front()) return c;
        int u = p.u, v = p.v;
        // The fire inside shows between its ribs.
        if (u == 4 && v >= 2 && v <= 8) return mix(FLAME, 0xFFFFFF, 0.6 * p.pulse(v * 0.1));
        if ((v == 3 || v == 5 || v == 7) && (u == 1 || u == 2 || u == 6 || u == 7)) {
            return mix(EMBER, FLAME, p.pulse(0.2 + v * 0.1));
        }
        if (v >= 2 && v <= 8) c = lit(c, 0xFF6A10, 0.2 - 0.04 * Math.abs(u - 4));
        return c;
    }

    private static int impLimb(P p) {
        int c = impSkin(p, p.key.equals("arm") ? 62 : 63);
        if (p.bottom()) return COAL;
        if (!p.side()) return c;
        // Claws: two pale points at the end of each limb.
        if (p.front() && p.u != 1) {
            if (p.v == p.fh - 1) return 0xE8DCC0;
            if (p.v == p.fh - 2) return 0x8A7A66;
        }
        return p.v >= p.fh - 2 ? mul(c, 0.7) : c;
    }

    private static int impTailTip(P p) {
        double px = p.u + 0.5, py = p.v + 0.5;
        double half = py < 4.5 ? 3.6 * py / 4.5 : py < 7 ? 3.6 * Math.pow(1 - (py - 4.5) / 2.5, 0.6) : 0;
        if (py >= 6) half = Math.max(half, 0.9);
        double d = Math.abs(px - 4);
        if (d > half) return CLEAR;
        if (half - d < 0.9) return opaque(0x1C1416);
        return opaque(d < 0.6 ? mix(EMBER, FLAME, p.pulse(py / 8)) : mix(BLOOD, 0x5A120C, d / 3.6));
    }

    private static int impWingOut(P p) {
        // A thumb claw above the wrist.
        if (ServantTextures.segDist(p.u + 0.5, p.v + 0.5, 0.5, 4, 2.5, 0.5) < 0.8) return opaque(0xE8DCC0);
        return membrane(p, 0, 4, new double[][] { { 23, 3 }, { 22, 15 }, { 13, 25 }, { 1, 21 } }, WING, 222, 68);
    }

    /** A tongue of flame that sways and flickers. */
    private static int impFlame(P p) {
        double x = (p.u + 0.5 - 5) / 5, y = 1 - (p.v + 0.5) / 14;
        double sway = 0.3 * Math.sin(y * 4 - p.phase * 2) * y * y;
        // A teardrop: round below, drawn out to a point above.
        double width = y < 0.25 ? 0.9 * Math.sqrt(y / 0.25) : 0.9 * Math.pow(1 - (y - 0.25) / 0.75, 1.3);
        width *= 0.88 + 0.24 * noise(p.u * 0.7, p.v * 0.6 - p.frame * 1.7, 5);
        double d = Math.abs(x - sway) / Math.max(0.05, width);
        if (d > 1) return CLEAR;
        double heat = clamp(1 - d - 0.5 * y);
        int c = heat > 0.55 ? mix(FLAME, 0xFFF6C0, (heat - 0.55) / 0.45)
                : heat > 0.2 ? mix(EMBER, FLAME, (heat - 0.2) / 0.35) : mix(0xB02808, EMBER, heat / 0.2);
        return argb(235 * (1 - 0.6 * d * d * d), c);
    }

    // ---- The wraith ------------------------------------------------------------------------------------

    private static final int VOID = 0x040308, SOUL = 0xBFF6FF, SOUL_DEEP = 0x38C8F0, MIST = 0x4A3F7A,
            FRAY = 0x5A4A9A, BONE = 0xD8D2BE;

    static int shade(P p) {
        return switch (p.key) {
            case "face" -> opaque(shadeFace(p));
            case "hood" -> shadeHood(p);
            case "hood_peak" -> opaque(night(p, 70));
            case "mantle" -> shadeMantle(p);
            case "torso" -> opaque(shadeTorso(p));
            case "skirt" -> shadeSkirt(p);
            case "cloak" -> shadeCloak(p);
            case "arm" -> shadeArm(p);
            case "hand" -> shadeHand(p);
            case "fist" -> opaque(shadeFist(p));
            case "shaft" -> opaque(shadeShaft(p));
            case "scythe" -> shadeScythe(p);
            case "smoke" -> shadeSmoke(p);
            case "glow" -> halo(p, SOUL, 0x5A4AC8, 0.3, 0);
            case "wisp" -> shadeWisp(p);
            default -> opaque(0xFF00FF);
        };
    }

    /** Nothing in the hood but two eyes of cold fire. */
    private static int shadeFace(P p) {
        if (!p.front()) return VOID;
        int u = p.u, v = p.v;
        int c = mix(0x020206, 0x0A0816, v / 13.0);
        for (int side = 0; side < 2; side++) {
            double ex = side == 0 ? 4.5 : 9.5, ey = 6.0;
            double flicker = 0.75 + 0.25 * Math.sin(p.phase * 2 + ex);
            if ((v == 5 || v == 6) && Math.abs(u + 0.5 - ex) < 1.5) {
                return v == 5 ? mix(SOUL, 0xFFFFFF, flicker) : mix(SOUL_DEEP, SOUL, flicker);
            }
            double d = Math.hypot((u + 0.5 - ex) * 0.8, v + 0.5 - ey);
            double g = clamp(1 - d / 3.6);
            c = lit(c, SOUL_DEEP, g * g * 0.9 * flicker);
            // A thread of fire rising from the outer corner of each eye.
            int corner = side == 0 ? 2 : 11;
            if (u == corner && v >= 2 && v <= 4) c = lit(c, 0x7FE8FF, 0.2 * (v - 1) * p.pulse(side * 0.5 + v * 0.1));
        }
        return c;
    }

    private static boolean hoodOpen(int u, int v) {
        if (u < 2 || u > 13 || v < 3 || v > 15) return false;
        if (v < 5 && (u < 4 || u > 11)) return false;
        return !(v == 3 && (u < 5 || u > 10));
    }

    private static int shadeHood(P p) {
        if (p.bottom()) return opaque(VOID);
        int c = night(p, 70);
        if (p.front()) {
            int u = p.u, v = p.v;
            if (hoodOpen(u, v)) return CLEAR;
            if (hoodOpen(u - 1, v) || hoodOpen(u + 1, v) || hoodOpen(u, v + 1)) c = mix(c, FRAY, 0.6);
        }
        if (p.side() && p.v == p.fh - 1 && hash((int) Math.floor(p.around()), 3, 71) > 0.55) return CLEAR;
        return opaque(c);
    }

    private static int shadeMantle(P p) {
        if (p.bottom()) return opaque(VOID);
        int c = night(p, 72);
        if (!p.side()) return opaque(c);
        int a = (int) Math.floor(p.around());
        if (p.v == 5 && hash(a / 2, 0, 72) > 0.4) return CLEAR;
        if (p.v == 4 && hash(a / 2, 0, 72) > 0.8) return CLEAR;
        if (p.v >= 4) c = mix(c, FRAY, 0.4);
        if (p.front()) {
            // A clasp: a soul caught in a ring of iron, on a chain.
            int dx = Math.abs(p.u - 13), dy = Math.abs(p.v - 2);
            if (dx == 0 && dy == 0) return opaque(mix(SOUL, 0xFFFFFF, p.pulse(0)));
            if (dx <= 1 && dy <= 1) return opaque(0x56607A);
            if (p.v == 2 && dx <= 6 && dx % 2 == 0) return opaque(0x6A7A9A);
        }
        return opaque(c);
    }

    private static int shadeTorso(P p) {
        int c = night(p, 73);
        if (!p.side()) return c;
        if (p.v == 17) return ((int) Math.floor(p.around())) % 3 == 0 ? 0x5A5470 : 0x2E2A40;
        if (p.front()) {
            int idx = runeColumn(p, 8, 2, 2, 73);
            if (idx >= 0) return mix(c, SOUL_DEEP, 0.35 + 0.55 * p.pulse(-idx * 0.3));
        }
        return c;
    }

    /** The robe frays into strips, the strips into holes, the holes into mist. */
    private static int shadeSkirt(P p) {
        if (p.top()) return opaque(night(p, 74));
        if (p.bottom()) return CLEAR;
        int a = (int) Math.floor(p.around());
        double len = 17 + 13 * hash(a / 3, 0, 75) - Math.abs(Math.floorMod(a, 3) - 1) * 2.5;
        double d = p.v + 0.5;
        if (d > len) return CLEAR;
        double end = clamp((d - (len - 9)) / 9);
        if (end > 0.3 && fbm(a * 0.5, p.v * 0.5, 76) > 0.7) return CLEAR;
        return argb(255 - 120 * end * end, mix(night(p, 74), MIST, 0.45 * end));
    }

    private static int shadeCloak(P p) {
        int u = p.u, v = p.v;
        double inset = v < 14 ? 4.0 * (1 - v / 14.0) : 0;
        if (u + 0.5 < inset || u + 0.5 > 32 - inset) return CLEAR;
        double len = 36 + 16 * hash(u / 3, 1, 80) - Math.abs(u % 3 - 1) * 3;
        if (v + 0.5 > len) return CLEAR;
        double end = clamp((v + 0.5 - (len - 12)) / 12);
        if (end > 0.25 && fbm(u * 0.45, v * 0.45, 82) > 0.72) return CLEAR;
        int c = mix(night(p, 81), MIST, 0.4 * end);
        if (u + 0.5 < inset + 1 || u + 0.5 > 31 - inset) c = mix(c, FRAY, 0.5);
        return argb(255 - 130 * end * end, c);
    }

    private static int shadeArm(P p) {
        if (p.bottom()) return opaque(VOID);
        int c = night(p, 77);
        if (!p.side()) return opaque(c);
        int a = (int) Math.floor(p.around());
        if (p.v >= 19) {
            if (hash(a, 0, 78) > 1.0 - 0.25 * (p.v - 18)) return CLEAR;
            c = mix(c, FRAY, 0.45);
        }
        return opaque(c);
    }

    /** A hand of bone: a palm, four long fingers and a thumb. */
    private static int shadeHand(P p) {
        int u = p.u, v = p.v;
        boolean palm = v <= 3 && u >= 2 && u <= 6;
        int[] ends = { 8, 9, 9, 7 };
        boolean finger = false, tip = false;
        for (int i = 0; i < 4; i++) {
            int col = 1 + i * 2;
            if (u == col && v >= 3 && v <= ends[i]) {
                finger = true;
                tip = v == ends[i];
            }
        }
        boolean thumb = (u == 7 && v == 2) || (u == 8 && (v == 3 || v == 4));
        if (!palm && !finger && !thumb) return CLEAR;
        if (tip) return opaque(mix(SOUL_DEEP, SOUL, p.pulse(u * 0.1)));
        int c = mix(BONE, 0x8E8A80, 0.15 * v / 9.0 + (finger && (v == 5 || v == 7) ? 0.6 : 0));
        return opaque(c);
    }

    /** The other hand, closed round the scythe: three finger bones wrapped about the shaft, one above another. */
    private static int shadeFist(P p) {
        if (!p.side()) return p.u == 2 && p.v == 2 ? 0x17141C : mix(BONE, 0x8E8A80, 0.35);
        if (p.v == 1 || p.v == 3) return 0x5E5A54;
        int c = mix(BONE, 0x8E8A80, 0.1 * p.v);
        // Knuckles at the corners, and the fingertips meeting in front with the same cold light as the open hand.
        if (p.u == 0 || p.u == p.fw - 1) c = mix(c, 0x8E8A80, 0.55);
        if (p.front() && p.u == 2) return mix(SOUL_DEEP, SOUL, p.pulse(p.v * 0.1));
        return c;
    }

    private static int shadeShaft(P p) {
        if (!p.side()) return 0x4A4658;
        int v = p.v;
        if (v % 9 == 0) return 0x56506A;
        if (v % 18 == 5) return mix(0x2E2A38, SOUL_DEEP, 0.3 + 0.6 * p.pulse(v / 72.0));
        return mix(0x17141C, 0x2E2A38, hash(p.u, v, 79));
    }

    /** The scythe's blade: dark steel that curves down to a point, its inner edge alight. */
    private static int shadeScythe(P p) {
        double px = p.u + 0.5, py = p.v + 0.5;
        double f = px / 31.0;
        double outer = 2 - 1.5 * Math.sin(Math.PI * px / 40) + 14 * Math.pow(f, 3.2);
        double inner = outer + 5.5 * Math.pow(Math.max(0, 1 - f), 0.8) + 0.6;
        if (py < outer) return CLEAR;
        if (py > inner) return py < inner + 1.3 ? argb(110 * (1 - (py - inner) / 1.3), SOUL) : CLEAR;
        if (inner - py < 1.2) return opaque(mix(SOUL, 0xFFFFFF, p.pulse(f)));
        int c = mix(0x5A5876, 0x24222F, clamp((py - outer) / (inner - outer)));
        if (py - outer < 0.9) c = mix(c, 0x8A88A8, 0.6);
        if (p.u % 6 == 3 && Math.abs(py - (outer + inner) / 2) < 0.7) c = mix(c, SOUL_DEEP, 0.4 + 0.5 * p.pulse(f));
        return opaque(c);
    }

    /** Mist that rises round where its feet should be. */
    private static int shadeSmoke(P p) {
        double n = fbm(p.u * 0.24 + p.dx(0.5), p.v * 0.3 + p.dy(0.5), 83);
        double across = Math.sin(Math.PI * p.s), up = p.t;
        double a = clamp((n - 0.36) * 3.2) * across * up * up;
        return argb(150 * a, mix(0x2A2440, 0x6A5AA8, n));
    }

    /** A soul that circles it: a core of cold fire with a tail. */
    private static int shadeWisp(P p) {
        double dx = p.u + 0.5 - 4.5, dy = p.v + 0.5 - 5.5;
        double r = Math.hypot(dx, dy);
        double flicker = 0.8 + 0.2 * Math.sin(p.phase * 2);
        if (r < 1.3) return opaque(mix(0xFFFFFF, SOUL, r / 1.3));
        double tail = dy < 0 ? Math.abs(dx) < 1.2 + dy * 0.22 ? 1 + dy / 5.5 : 0 : 0;
        double a = Math.max(clamp(1 - r / 3.6), tail * 0.8);
        return argb(210 * a * a * flicker, mix(SOUL, SOUL_DEEP, clamp(r / 3.6)));
    }
}
