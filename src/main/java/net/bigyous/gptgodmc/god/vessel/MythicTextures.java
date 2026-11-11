package net.bigyous.gptgodmc.god.vessel;

import static net.bigyous.gptgodmc.god.vessel.VesselTextures.amethyst;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.cosmos;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.embers;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.eye;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.eyeColor;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.face;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.fbm;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.filigree;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.folds;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.glow;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.hash;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.mix;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.moltenMouth;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.nebula;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.obsidian;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.radial;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.robe;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.scale;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.shadeEdge;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.sigil;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.smooth;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.starfield;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.stars;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.sunburst;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.trim;
import static net.bigyous.gptgodmc.god.vessel.VesselTextures.voronoi;

import net.bigyous.gptgodmc.god.vessel.VesselSkin.Face;
import net.bigyous.gptgodmc.god.vessel.VesselSkin.Piece;
import net.bigyous.gptgodmc.god.vessel.VesselTextures.Painter;
import net.bigyous.gptgodmc.god.vessel.VesselTextures.Px;

/**
 * The mythic looks' painted skins (the original four live in {@link VesselTextures}): sky father,
 * all-father, sea lord, underworld king, war goddess, sun, jackal, forest, frost, storm, plague, celestial,
 * clockwork, jester, moon, fire titan and four eldritch forms. Generic archetypes, drawn from code;
 * deterministic so the pack hash stays stable. Face coordinates: a head's front face is 16x16 texels,
 * the torso's 16x24; p.y is up from the bottom of a box, p.z forward from its back.
 */
final class MythicTextures {
    static Painter painter(String look) {
        return switch (look) {
            case "thunder" -> MythicTextures::thunder;
            case "allfather" -> MythicTextures::allfather;
            case "tide" -> MythicTextures::tide;
            case "underworld" -> MythicTextures::underworld;
            case "warrior" -> MythicTextures::warrior;
            case "sun" -> MythicTextures::sun;
            case "jackal" -> MythicTextures::jackal;
            case "grove" -> MythicTextures::grove;
            case "frost" -> MythicTextures::frost;
            case "storm" -> MythicTextures::storm;
            case "plague" -> MythicTextures::plague;
            case "celestial" -> MythicTextures::celestial;
            case "clockwork" -> MythicTextures::clockwork;
            case "jester" -> MythicTextures::jester;
            case "moon" -> MythicTextures::moon;
            case "titan" -> MythicTextures::titan;
            case "abyss" -> MythicTextures::abyss;
            case "hollow" -> MythicTextures::hollow;
            case "prism" -> MythicTextures::prism;
            case "bloom" -> MythicTextures::bloom;
            default -> null;
        };
    }

    // ---- Sky father ----------------------------------------------------------------------------

    /** White robes with gold trim, a white beard, sky-blue cape and crackling lightning. */
    static int thunder(Px p) {
        return switch (p.piece) {
            case HEAD, HAND -> {
                int c = flesh(p, 0xC48A60, 0xE8BC92, 601);
                c = humanFace(p, c, 0xF4F4F4, 0x9A5A48, 0x9FD8FF);
                if (hairArea(p)) c = curls(p, 0xB8BEC8, 0xFFFFFF, 603);
                yield shadeEdge(p, c, 0.85);
            }
            case HAIR_TOP, HAIR_BACK -> shadeEdge(p, curls(p, 0xB8BEC8, 0xFFFFFF, 605), 0.85);
            case BEARD -> {
                int c = curls(p, 0xBCC2CC, 0xFFFFFF, 607);
                yield shadeEdge(p, beardShape(p, c, 0x6A6E78), 0.85);
            }
            case TORSO -> {
                int c = folds(p, 0xE2DED4, 0xFFFFFF);
                c = trim(p, c, 0xF2C230, 1.2, false);
                // A gold-edged sash from the shoulder to the hip.
                if (p.front() || p.back()) {
                    double sash = Math.abs(p.s - (0.18 + 0.64 * p.t));
                    if (sash < 0.09) c = filigree(p, 0xF6D03D);
                }
                if (p.front()) c = bolt(p, c, 10.5, 3, 12, 0.8, 0xBFE9FF);
                yield crackle(p, c, 0x9FD8FF, 611, 0.6);
            }
            case ARM -> {
                int c = folds(p, 0xE0DCD2, 0xFFFFFF);
                if (p.side() && p.v >= 1 && p.v <= 2) c = filigree(p, 0xF6D03D);
                yield crackle(p, c, 0xBFE9FF, 613, 0.7);
            }
            case LEG -> {
                int c = folds(p, 0xDCD6C8, 0xFAF8F2);
                if (p.side() && p.v >= p.fh - 6) c = meander(p, c, 0xE9B829, p.fh - 6);
                yield c;
            }
            case BOOT -> {
                int c = mix(0x6A4422, 0x8A5A30, fbm(p.x * 0.4, p.y * 0.4, p.z * 0.4, 615, 2));
                yield p.v % 2 == 0 ? filigree(p, 0xF6D03D) : c;
            }
            case BELT, BAND, COLLAR -> filigree(p, 0xF6D03D);
            case GEM -> radial(p, 0xFFFFFF, 0xCFF2FF, 0x3BA7E8);
            case EYE -> eye(p, eyeColor(p, 0xBFE9FF));
            case CAPE -> {
                int c = robe(p, 0x16306A, 0x3A62B8, 617);
                c = trim(p, c, 0xF2C230, 1.0, true);
                if (p.v >= p.fh - 6 && p.v < p.fh - 1) c = meander(p, c, 0xF2C230, p.fh - 6);
                yield crackle(p, c, 0xCFF2FF, 619, 0.5);
            }
            case LINING -> {
                int c = mix(0xFFFFFF, 0x8CC8F0, p.t);
                double cloud = fbm(p.x * 0.18 + p.dx(0.6), p.y * 0.12 + p.dy(0.6), 0, 621, 3);
                yield glow(c, 0xFFFFFF, smooth(0.5, 0.75, cloud) * 0.7);
            }
            case HALO -> radial(p, 0xFFFFFF, 0xFFF1B5, 0xF2C230);
            default -> 0xE8E4DA;
        };
    }

    // ---- All-father ----------------------------------------------------------------------------

    /** A one-eyed grey wanderer: weathered skin, a braided beard, runes and a raven-feather cloak. */
    static int allfather(Px p) {
        return switch (p.piece) {
            case HEAD, HAND -> {
                int c = flesh(p, 0xA67C60, 0xD2A684, 701);
                c = humanFace(p, c, 0x9A9AA0, 0x7A4A3E, 0xA8D8FF);
                if (hairArea(p)) c = strands(p, 0x6E6E76, 0xB8B8C0, 703);
                if (p.piece == Piece.HEAD) c = patchStrap(p, c);
                yield shadeEdge(p, c, 0.85);
            }
            case HAIR_TOP, HAIR_BACK -> shadeEdge(p, strands(p, 0x6E6E76, 0xB8B8C0, 705), 0.85);
            case BEARD -> {
                int c = strands(p, 0x76767E, 0xC4C4CC, 707);
                // A braid down the middle with a silver ring.
                if (p.front() && Math.abs(p.u - 6) <= 1 && p.v >= 4) {
                    c = (p.u + p.v) % 2 == 0 ? 0xD0D0D8 : 0x8A8A92;
                    if (p.v == 7) c = glow(0xB8BEC8, 0xFFFFFF, 0.5 * p.pulse(0.3));
                }
                yield shadeEdge(p, beardShape(p, c, 0x4A4A52), 0.85);
            }
            case PATCH -> {
                int c = mix(0x1A130E, 0x2E2218, fbm(p.x * 0.6, p.y * 0.6, 0, 709, 2));
                yield p.edge() < 1 ? 0x5A4630 : c;
            }
            case TORSO -> {
                int c = robe(p, 0x262C38, 0x48526A, 711);
                c = trim(p, c, 0x8A8E96, 1.0, false);
                if (p.front()) c = runeColumn(p, c, 7, 6, 3, 0x9ED0FF, 713);
                yield c;
            }
            case ARM -> {
                int c = robe(p, 0x262C38, 0x48526A, 715);
                if (p.t > 0.6) {
                    c = mix(0x4A3020, 0x6A4630, fbm(p.x * 0.5, p.y * 0.5, p.z * 0.5, 717, 2));
                    if (p.side() && (p.v - p.u) % 4 == 0) c = scale(c, 0.65);
                }
                yield c;
            }
            case LEG -> {
                int c = robe(p, 0x1C1C22, 0x34343C, 719);
                if (p.t > 0.55 && p.side() && (p.v + p.u) % 3 == 0) c = mix(c, 0x6E6A62, 0.7);
                yield c;
            }
            case BOOT -> shadeEdge(p, mix(0x3A2618, 0x5A3C26, fbm(p.x * 0.4, p.y * 0.4, p.z * 0.4, 721, 2)), 0.8);
            case BELT -> {
                int c = mix(0x3A2618, 0x5E4028, fbm(p.x * 0.5, p.y, p.z * 0.5, 723, 2));
                if (p.front() && Math.abs(p.u - 8) <= 1) c = glow(0x9AA2AE, 0xFFFFFF, 0.3 * p.pulse(0));
                yield shadeEdge(p, c, 0.8);
            }
            case BAND -> shadeEdge(p, (p.u + p.v) % 3 == 0 ? 0x7A808A : 0xB8BEC8, 0.85);
            case COLLAR -> fur(p, 0x5E5A56, 0xA8A29A, 725);
            case GEM -> radial(p, 0xFFFFFF, 0xC8E4FF, 0x5A7AA8);
            case EYE -> eye(p, eyeColor(p, 0xA8D8FF));
            case CAPE -> feathers(p, 0x0A0C12, 0x1E2638, 0x3A5A9A, 727);
            case LINING -> fur(p, 0x3E4048, 0x6E727C, 729);
            case HALO -> radial(p, 0xFFFFFF, 0xC8D4E0, 0x6E7A8A);
            default -> 0x5A5E66;
        };
    }

    /** The eyepatch's strap, running from the patch up across the brow and around the head. */
    private static int patchStrap(Px p, int c) {
        int strap = 0x2A2018;
        if (p.front()) {
            if (segDist(p.u + 0.5, p.v + 0.5, 4, 8.6, 16, 4.6) < 0.7) return strap;
        } else if (p.side() && Math.abs(p.y - (p.face == Face.WEST ? 11.8 : 11.4)) < 0.7) {
            return strap;
        }
        return c;
    }

    // ---- Sea lord ------------------------------------------------------------------------------

    /** Sea-green skin under rippling light, a weed-green beard, scale armor and a trident seal. */
    static int tide(Px p) {
        return switch (p.piece) {
            case HEAD, HAND -> {
                int c = flesh(p, 0x5E9A8E, 0x9ACCBE, 801);
                c = caustics(p, c, 0xCFFFF4, 0.35);
                c = humanFace(p, c, 0x1F5A52, 0x3A6A6A, 0x7FFFE8);
                if (hairArea(p)) c = weed(p, 803);
                yield shadeEdge(p, c, 0.85);
            }
            case HAIR_TOP, HAIR_BACK -> shadeEdge(p, weed(p, 805), 0.85);
            case BEARD -> {
                int c = weed(p, 807);
                if (hash(p.u, p.v, p.face.ordinal(), 809) > 0.95) c = glow(c, 0xF4F0E8, 0.8); // pearls
                yield shadeEdge(p, beardShape(p, c, 0x0E3A34), 0.85);
            }
            case TORSO -> {
                int c = scales(p, 0x0B4E52, 0x2FA3A0, 0x06282C, 811);
                c = trim(p, c, 0xE9C045, 1.0, false);
                if (p.front()) c = tridentMark(p, c, 8, 4, 20, 0xF2C230);
                yield c;
            }
            case ARM -> {
                int c = scales(p, 0x0B4E52, 0x2FA3A0, 0x06282C, 813);
                yield p.side() && p.v <= 1 ? filigree(p, 0xE9C045) : c;
            }
            case LEG -> {
                int c = robe(p, 0x0A2A46, 0x1A5078, 815);
                yield waves(p, c, 0xBFF4FF, 6, 0.55);
            }
            case BOOT -> {
                double h = 0.5 + 0.5 * Math.sin(p.x * 0.6 + p.y * 0.8 + p.z * 0.5 + p.phase);
                yield shadeEdge(p, mix(0xC8D8E0, hsb(0.5 + 0.25 * h, 0.25, 0.95), 0.6), 0.8);
            }
            case BELT, BAND, COLLAR -> filigree(p, 0xE9C045);
            case GEM -> radial(p, 0xFFFFFF, 0xC8FFF4, 0x2FA3A0);
            case EYE -> eye(p, eyeColor(p, 0x7FFFE8));
            case CAPE -> {
                int c = mix(0x0E5A7A, 0x041E36, p.t);
                c = waves(p, c, 0xE8FFFF, 7, 0.75);
                yield trim(p, c, 0xE9C045, 1.0, true);
            }
            case LINING -> caustics(p, mix(0x147A78, 0x0A3A4A, p.t), 0xCFFFF4, 0.6);
            case HALO -> radial(p, 0xFFFFFF, 0xC8FFF4, 0x2FA3A0);
            default -> 0x2FA3A0;
        };
    }

    // ---- Underworld king -----------------------------------------------------------------------

    /** Ashen skin, black hair and beard flecked with sparks, black robes edged in embers, a bident. */
    static int underworld(Px p) {
        return switch (p.piece) {
            case HEAD, HAND -> {
                int c = flesh(p, 0x7E8086, 0xAEB0B6, 901);
                double vein = 1 - Math.abs(fbm(p.x * 0.12, p.y * 0.12, p.z * 0.12, 903, 3) - 0.5) * 20;
                if (vein > 0) c = mix(c, 0x3A3A44, vein * 0.5);
                c = humanFace(p, c, 0x0A0A0C, 0x3A2A2A, 0xFF6A2A);
                if (hairArea(p)) c = sparks(p, strands(p, 0x08080A, 0x26262E, 905), 907);
                yield shadeEdge(p, c, 0.82);
            }
            case HAIR_TOP, HAIR_BACK -> shadeEdge(p, sparks(p, strands(p, 0x08080A, 0x26262E, 909), 911), 0.85);
            case BEARD -> shadeEdge(p, beardShape(p, sparks(p, strands(p, 0x0A0A0C, 0x2A2A32, 913), 915), 0x000000),
                    0.85);
            case TORSO -> {
                int c = robe(p, 0x0A090C, 0x221F28, 917);
                c = emberTrim(p, c);
                if (p.front()) c = bident(p, c, 8, 4, 21, 0xFF7A2A);
                yield c;
            }
            case ARM -> {
                int c = robe(p, 0x0A090C, 0x221F28, 919);
                if (p.t > 0.8) c = embers(p, c, 0.08, 0.8, 921);
                yield c;
            }
            case LEG -> risingSparks(p, robe(p, 0x08070A, 0x1C1A22, 923), 925);
            case BOOT -> {
                int c = obsidian(p, 0x0E0C10, 0x3A2A2A);
                yield p.side() && p.v <= 0 ? glow(c, 0xFF5A1A, 0.6 + 0.3 * p.pulse(p.s)) : c;
            }
            case BELT, BAND, COLLAR -> {
                int c = obsidian(p, 0x26222A, 0x4A4448);
                yield p.side() && Math.abs(p.t - 0.5) < 0.25 ? glow(c, 0xFF6A1A, 0.5 + 0.3 * p.pulse(p.s * 2)) : c;
            }
            case GEM -> radial(p, 0xFFF0C0, 0xFF8A2A, 0x8A1A06);
            case EYE -> eye(p, eyeColor(p, 0xFF7A2A));
            case CAPE -> {
                double smoke = fbm(p.x * 0.2 + p.dx(0.5), p.y * 0.12 + p.dy(0.8), 0, 927, 3);
                int c = mix(0x060508, 0x24222A, smooth(0.45, 0.8, smoke));
                c = glow(c, 0xFF4A1C, smooth(0.8, 1.0, p.t) * (0.5 + 0.35 * p.pulse(p.s * 3)));
                yield risingSparks(p, c, 929);
            }
            case LINING -> embers(p, mix(0x5A0A06, 0x1A0404, p.t), 0.05, 0.5, 931);
            case HALO -> radial(p, 0xFFF0C0, 0xFF7A2A, 0x6A1206);
            default -> 0x1A181E;
        };
    }

    // ---- War goddess ---------------------------------------------------------------------------

    /** Olive skin, a bronze crested helm with cheek guards, a muscled cuirass, leather strips, crimson cloak. */
    static int warrior(Px p) {
        return switch (p.piece) {
            case HEAD, HAND -> {
                int c = flesh(p, 0xB08262, 0xD8AC88, 1001);
                c = humanFace(p, c, 0x4A3020, 0xA0605A, 0xC8E0FF);
                if (p.piece == Piece.HEAD && helmArea(p)) c = bronze(p, 1003);
                yield shadeEdge(p, c, 0.85);
            }
            case HAIR_TOP, HAIR_BACK -> {
                int c = bronze(p, 1005);
                if (p.face == Face.UP && Math.abs(p.x - p.piece.w / 2.0) < 1.2) c = scale(c, 0.7);
                yield shadeEdge(p, c, 0.8);
            }
            case TORSO -> {
                int c = bronze(p, 1007);
                if (p.front()) c = cuirass(p, c);
                yield shadeEdge(p, c, 0.8);
            }
            case ARM -> {
                int c;
                if (p.t < 0.32) c = strips(p, 0x5A3A22, 0x7A5232);
                else if (p.t > 0.74) c = bronze(p, 1009);
                else c = flesh(p, 0xB08262, 0xD8AC88, 1011);
                yield shadeEdge(p, c, 0.85);
            }
            case LEG -> {
                int c;
                if (p.t < 0.36) {
                    c = strips(p, 0x5A3A22, 0x7A5232);
                    if (p.v == (int) (p.fh * 0.3) && p.u % 2 == 1) c = bronze(p, 1013);
                } else if (p.t > 0.55) {
                    c = bronze(p, 1015);
                } else {
                    c = flesh(p, 0xB08262, 0xD8AC88, 1017);
                }
                yield shadeEdge(p, c, 0.85);
            }
            case BOOT, BAND -> shadeEdge(p, bronze(p, 1019), 0.8);
            case BELT -> {
                int c = mix(0x4A2E1A, 0x6A4428, fbm(p.x * 0.5, p.y, p.z * 0.5, 1021, 2));
                yield p.side() && p.u % 3 == 1 && p.v == 1 ? 0xE0A458 : shadeEdge(p, c, 0.8);
            }
            case COLLAR -> robe(p, 0x5A0C10, 0x9A1C22, 1023);
            case GEM -> radial(p, 0xFFFFFF, 0xD0DCF0, 0x5A6A8A);
            case EYE -> eye(p, eyeColor(p, 0xC8E0FF));
            case CAPE -> {
                int c = robe(p, 0x5E0C12, 0xA82228, 1025);
                if (p.v >= p.fh - 6 && p.v < p.fh - 1) c = meander(p, c, 0xE0A458, p.fh - 6);
                yield trim(p, c, 0xE0A458, 0.0, true);
            }
            case LINING -> robe(p, 0x3A060A, 0x6E1218, 1027);
            case HALO -> radial(p, 0xFFFFFF, 0xFFE0A0, 0xC07A3A);
            default -> 0x9A6A3A;
        };
    }

    private static boolean helmArea(Px p) {
        if (p.front()) return p.v <= 2 || ((p.u <= 1 || p.u >= 14) && p.v <= 12);
        if (p.side()) return p.y > 5 || p.z < 8;
        return p.face == Face.UP;
    }

    /** Polished bronze with a slow moving highlight. */
    private static int bronze(Px p, int seed) {
        double n = fbm(p.x * 0.3, p.y * 0.3, p.z * 0.3, seed, 2);
        int c = mix(0x8A5626, 0xD69A50, 0.35 + 0.5 * n);
        double sheen = Math.max(0, Math.sin((p.x + p.z) * 0.32 - p.y * 0.22 + p.phase));
        return glow(c, 0xFFE8B8, Math.pow(sheen, 10) * 0.55);
    }

    /** The embossed chest and belly of a muscled cuirass. */
    private static int cuirass(Px p, int c) {
        double x = p.u + 0.5, y = p.v + 0.5;
        for (double cx : new double[] { 4.6, 11.4 }) {
            double d = Math.hypot((x - cx) / 3.6, (y - 6.2) / 2.6);
            if (d < 1) c = mix(c, d > 0.8 ? scale(c, 0.6) : glow(c, 0xFFE0A8, (1 - d) * 0.3), 0.8);
        }
        if (y > 10 && y < 19 && Math.abs(x - 8) < 4) {
            double lx = (x - 8 + 4) % 4, ly = (y - 10) % 3;
            if (lx < 0.6 || ly < 0.6 || Math.abs(x - 8) < 0.5) c = scale(c, 0.65);
        }
        return c;
    }

    /** Hanging leather strips (pteruges), alternating dark and light. */
    private static int strips(Px p, int dark, int light) {
        int c = (p.u / 2) % 2 == 0 ? dark : light;
        return p.v == 0 ? scale(c, 0.7) : c;
    }

    // ---- Sun -----------------------------------------------------------------------------------

    /** Bronze skin with falcon markings, a gold-and-lapis headdress and collar, a winged sun, linen. */
    static int sun(Px p) {
        return switch (p.piece) {
            case HEAD, HAND -> {
                int c = flesh(p, 0x8A5A2C, 0xC48A4A, 1101);
                c = humanFace(p, c, 0x1A120A, 0x6A3A20, 0xFFE07A);
                if (p.piece == Piece.HEAD) {
                    c = falconMarks(p, c);
                    if (hairArea(p)) c = nemes(p, 0xE8B828, 0x1E3A8A);
                }
                yield shadeEdge(p, c, 0.85);
            }
            case HAIR_TOP, HAIR_BACK -> shadeEdge(p, nemes(p, 0xE8B828, 0x1E3A8A), 0.85);
            case TORSO -> {
                int c = flesh(p, 0x8A5A2C, 0xC48A4A, 1103);
                c = usekh(p, c, new int[] { 0xF2C230, 0x1E3A8A, 0xC2401A, 0xF2C230, 0x2A8A8A });
                if (p.front()) c = wingedSun(p, c, 8, 14.5);
                yield c;
            }
            case ARM -> {
                int c = flesh(p, 0x8A5A2C, 0xC48A4A, 1105);
                if (p.side() && p.v >= 2 && p.v <= 3) c = filigree(p, 0xF6D03D);
                if (p.side() && p.v == 4) c = 0x1E3A8A;
                yield shadeEdge(p, c, 0.85);
            }
            case LEG -> {
                int c;
                if (p.t < 0.5) {
                    c = (p.u % 3 == 0) ? 0xD8D2C0 : 0xF4F0E4; // pleated linen
                    if (p.v <= 1) c = filigree(p, 0xF6D03D);
                } else {
                    c = flesh(p, 0x8A5A2C, 0xC48A4A, 1107);
                }
                yield shadeEdge(p, c, 0.85);
            }
            case BOOT -> filigree(p, 0xF6D03D);
            case BELT, BAND, COLLAR -> (p.u / 2 + p.v) % 2 == 0 ? filigree(p, 0xF6D03D)
                    : shadeEdge(p, mix(0x16307A, 0x2A4AAA, fbm(p.x, p.y, p.z, 1109, 2)), 0.85);
            case GEM -> radial(p, 0xFFFFFF, 0xFFE07A, 0xE8601A);
            case EYE -> eye(p, eyeColor(p, 0xFFE07A));
            case CAPE -> feathers(p, 0x8A3A0A, 0xF2B830, 0x1E3A8A, 1111);
            case LINING -> {
                int c = mix(0xFFE07A, 0xC2401A, p.t);
                yield sunburst(p, c, p.fw / 2.0, 4, 18);
            }
            case HALO -> {
                // The sun disc's rim: molten gold, flaring in turn.
                double f = 0.5 + 0.5 * Math.sin(p.phase * 2 + (p.s + p.t) * 6);
                yield radial(p, 0xFFFFFF, mix(0xFFD040, 0xFFF0A0, f), 0xE8801A);
            }
            default -> 0xC48A4A;
        };
    }

    /** Kohl around the eyes with the falcon's tail and cheek stripe. */
    private static int falconMarks(Px p, int c) {
        if (!p.front()) return c;
        double x = p.u + 0.5, y = p.v + 0.5;
        for (double ex : new double[] { 4, 12 }) {
            double d = Math.hypot((x - ex) / 3.2, (y - 9) / 1.9);
            if (Math.abs(d - 1) < 0.18 && y > 8.6) c = mix(c, 0x0A0806, 0.85);
        }
        // Tails sweeping out from the outer corners, and stripes falling down the cheeks.
        if (segDist(x, y, 1, 9.6, 0, 10.2) < 0.5 || segDist(x, y, 15, 9.6, 16, 10.2) < 0.5) c = 0x0A0806;
        if ((Math.abs(x - 3.5) < 0.5 || Math.abs(x - 12.5) < 0.5) && y > 10.8 && y < 14) c = mix(c, 0x0A0806, 0.8);
        return c;
    }

    /** The striped headcloth: gold and lapis bands. */
    private static int nemes(Px p, int gold, int lapis) {
        boolean band = p.face == Face.UP ? ((int) (Math.hypot(p.x - 8.5, p.z - 8.5) / 1.5)) % 2 == 0
                : ((int) (p.y / 1.5)) % 2 == 0;
        int c = band ? gold : lapis;
        double sheen = Math.max(0, Math.sin(p.x * 0.3 + p.z * 0.3 + p.phase));
        return glow(c, 0xFFFFFF, Math.pow(sheen, 12) * 0.25);
    }

    /** A broad collar of concentric bead rows across the shoulders, front and back. */
    private static int usekh(Px p, int c, int[] colors) {
        if (!(p.front() || p.back() || p.side())) return c;
        double cx = p.side() ? p.fw / 2.0 : 8, x = p.u + 0.5, y = p.v + 0.5;
        double r = Math.hypot((x - cx) * (p.side() ? 2.4 : 1), y + 5);
        if (r < 5.5 || r > 12) return c;
        int band = (int) ((r - 5.5) / 1.3);
        int bead = colors[band % colors.length];
        if (Math.abs(((r - 5.5) % 1.3) - 0.65) > 0.5) bead = scale(bead, 0.7);
        return bead;
    }

    /** A sun disc with outspread falcon wings. */
    private static int wingedSun(Px p, int c, double cx, double cy) {
        double x = p.u + 0.5, y = p.v + 0.5, dx = Math.abs(x - cx), dy = y - cy;
        double r = Math.hypot(x - cx, dy);
        if (r < 2.3) return glow(0xE8601A, 0xFFE07A, 0.4 + 0.4 * p.pulse(0));
        if (r < 2.9) return 0xF2C230;
        if (dx > 2.5 && dx < 8 && dy > -1.6 + dx * 0.12 && dy < 1.2 - dx * 0.05) {
            int row = (int) ((dy + 1.6) / 1.0);
            return row % 3 == 0 ? 0xF2C230 : row % 3 == 1 ? 0x1E3A8A : 0xC2401A;
        }
        return c;
    }

    // ---- Jackal --------------------------------------------------------------------------------

    /** Black skin with gold kohl, a striped headcloth, lapis and gold collars, an ankh, a gold kilt. */
    static int jackal(Px p) {
        return switch (p.piece) {
            case HEAD, HAND -> {
                int c = flesh(p, 0x08080C, 0x1E1E2A, 1201);
                double sheen = Math.max(0, Math.sin((p.x + p.z) * 0.35 - p.y * 0.2 + p.phase));
                c = glow(c, 0x3A4A7A, Math.pow(sheen, 8) * 0.3);
                c = face(p, c, 0x000000, -1, 0xFFD54A);
                if (p.piece == Piece.HEAD) {
                    c = falconMarksGold(p, c);
                    if (hairArea(p)) c = nemes(p, 0xE8B828, 0x0A0A0E);
                }
                yield shadeEdge(p, c, 0.85);
            }
            case HAIR_TOP, HAIR_BACK -> shadeEdge(p, nemes(p, 0xE8B828, 0x0A0A0E), 0.85);
            case TORSO -> {
                int c = flesh(p, 0x08080C, 0x1E1E2A, 1203);
                c = usekh(p, c, new int[] { 0xF2C230, 0x1E3A8A, 0xF2C230, 0x2A8A8A });
                if (p.front()) c = ankh(p, c, 8, 15, 0xF6D03D);
                yield c;
            }
            case ARM -> {
                int c = flesh(p, 0x08080C, 0x1E1E2A, 1205);
                if (p.side() && p.v >= 2 && p.v <= 3) c = filigree(p, 0xF6D03D);
                yield shadeEdge(p, c, 0.85);
            }
            case LEG -> {
                int c;
                if (p.t < 0.55) c = (p.u % 2 == 0) ? filigree(p, 0xF6D03D) : 0x0E0E14;
                else c = flesh(p, 0x08080C, 0x1E1E2A, 1207);
                yield shadeEdge(p, c, 0.85);
            }
            case BOOT -> filigree(p, 0xF6D03D);
            case BELT, BAND, COLLAR -> (p.u / 2 + p.v) % 2 == 0 ? filigree(p, 0xF6D03D)
                    : shadeEdge(p, mix(0x16307A, 0x2A4AAA, fbm(p.x, p.y, p.z, 1209, 2)), 0.85);
            case GEM -> radial(p, 0xFFFFFF, 0x7AA8FF, 0x16307A);
            case EYE -> eye(p, eyeColor(p, 0xFFD54A));
            case CAPE -> {
                int c = robe(p, 0x050507, 0x16161C, 1211);
                c = glyphRows(p, c, 0xE8B828, 1213);
                yield trim(p, c, 0xF2C230, 1.0, true);
            }
            case LINING -> stars(p, mix(0x0E1E5A, 0x16307A, p.t), 0.95, 0xFFE08A, 1215);
            case HALO -> radial(p, 0xFFFFFF, 0xFFE08A, 0xE8B828);
            default -> 0x0E0E14;
        };
    }

    private static int falconMarksGold(Px p, int c) {
        if (!p.front()) return c;
        double x = p.u + 0.5, y = p.v + 0.5;
        if (segDist(x, y, 1, 9.4, 0, 10.4) < 0.5 || segDist(x, y, 15, 9.4, 16, 10.4) < 0.5) return 0xE8B828;
        for (double ex : new double[] { 4, 12 }) {
            double d = Math.hypot((x - ex) / 3.2, (y - 9) / 1.9);
            if (Math.abs(d - 1) < 0.15) c = mix(c, 0xE8B828, 0.8);
        }
        return c;
    }

    private static int ankh(Px p, int c, double cx, double cy, int color) {
        double x = p.u + 0.5, y = p.v + 0.5;
        double loop = Math.hypot((x - cx) / 1.7, (y - (cy - 3.6)) / 2.2);
        boolean on = Math.abs(loop - 1) < 0.32 || (Math.abs(y - cy) < 0.6 && Math.abs(x - cx) < 3.2)
                || (Math.abs(x - cx) < 0.7 && y > cy && y < cy + 6.5);
        return on ? glow(color, 0xFFFFFF, 0.25 * p.pulse(0)) : c;
    }

    /** Rows of small glyphs, like a tomb's inscriptions. */
    private static int glyphRows(Px p, int c, int color, int seed) {
        int cw = 5, ch = 7;
        int lx = Math.floorMod(p.u - 1, cw), ly = Math.floorMod(p.v - 1, ch);
        int id = (int) (hash(Math.floorDiv(p.u - 1, cw), Math.floorDiv(p.v - 1, ch), p.face.ordinal(), seed) * 32);
        return rune(lx, ly, id) ? mix(c, color, 0.85) : c;
    }

    // ---- Grove ---------------------------------------------------------------------------------

    /** Bark skin with moss, leafy hair, a hanging moss beard, a glowing spiral, vines and a leaf cloak. */
    static int grove(Px p) {
        return switch (p.piece) {
            case HEAD, HAND -> {
                int c = bark(p, 0x3E2A1A, 0x7A5A3A, 1301);
                c = face(p, c, 0x140C06, -1, 0xB8FF6A);
                if (p.piece == Piece.HEAD && hairArea(p)) c = leaves(p, 1303);
                yield shadeEdge(p, c, 0.85);
            }
            case HAIR_TOP, HAIR_BACK -> leaves(p, 1305);
            case BEARD -> {
                int c = strands(p, 0x3A5A22, 0x8AA05A, 1307);
                if (hash(p.u, p.v, 3, 1309) > 0.93) c = glow(c, 0xD8FF9A, 0.6 * p.pulse(p.s));
                yield shadeEdge(p, beardShape(p, c, 0x1A2A0E), 0.85);
            }
            case TORSO -> {
                int c = bark(p, 0x3E2A1A, 0x7A5A3A, 1311);
                if (p.t < 0.25) c = leaves(p, 1313);
                if (p.front()) c = spiral(p, c, 8, 13, 5.5, 0xB8FF6A, 3);
                yield c;
            }
            case ARM, LEG -> {
                int c = bark(p, 0x3E2A1A, 0x6E5034, p.piece == Piece.ARM ? 1315 : 1317);
                double vine = Math.abs(Math.sin((p.x + p.z) * 0.5 + p.y * 0.55));
                if (vine < 0.18) c = mix(0x3E8A2A, 0x6AB83A, fbm(p.x, p.y, p.z, 1319, 2));
                yield c;
            }
            case BOOT -> {
                int c = bark(p, 0x2A1C10, 0x4A3420, 1321);
                yield p.v >= 3 ? mix(c, 0x3A6A1E, 0.5) : c;
            }
            case BELT, BAND -> {
                int c = (p.u + p.v) % 4 < 2 ? 0x3E7A22 : 0x2A5A16;
                yield hash(p.u, p.v, p.face.ordinal(), 1323) > 0.9 ? 0xF4D8E8 : c;
            }
            case COLLAR -> leaves(p, 1325);
            case GEM -> radial(p, 0xFFFFFF, 0xD8FF9A, 0x3A9A2A);
            case EYE -> eye(p, eyeColor(p, 0xB8FF6A));
            case CAPE -> leaves(p, 1327);
            case LINING -> {
                double n = fbm(p.x * 0.3, p.y * 0.3, p.z * 0.3, 1329, 3);
                yield stars(p, mix(0x1E3A12, 0x4A7A2A, n), 0.97, 0xD8FF9A, 1331);
            }
            case HALO -> radial(p, 0xFFFFFF, 0xD8FF9A, 0x5AA032);
            default -> 0x4A3420;
        };
    }

    // ---- Frost ---------------------------------------------------------------------------------

    /** Ice-blue skin veined with frost, frozen tears, a snowflake seal, a fur collar and falling snow. */
    static int frost(Px p) {
        return switch (p.piece) {
            case HEAD, HAND -> {
                int c = flesh(p, 0x8EB8DC, 0xDCEEFF, 1401);
                c = frostLines(p, c, 1403);
                c = face(p, c, 0x1A3A6A, 0xFFFFFF, 0x9FE4FF);
                if (p.piece == Piece.HEAD && hairArea(p)) c = strands(p, 0xA8C8E8, 0xFFFFFF, 1405);
                yield shadeEdge(p, c, 0.85);
            }
            case HAIR_TOP, HAIR_BACK -> shadeEdge(p, stars(p, strands(p, 0xA8C8E8, 0xFFFFFF, 1407), 0.96,
                    0xFFFFFF, 1409), 0.85);
            case BEARD -> {
                int c = strands(p, 0xB8D4EC, 0xFFFFFF, 1411);
                // Icicles: lighter at the root, sharp blue tips.
                if (p.front() && p.v > 5 && (p.u + (int) (hash(p.u, 0, 0, 1413) * 3)) % 3 == 0) c = 0x8EC8F0;
                yield shadeEdge(p, beardShape(p, c, 0x4A7AA8), 0.85);
            }
            case TORSO -> {
                int c = folds(p, 0x9CC4E4, 0xEAF6FF);
                c = frostLines(p, c, 1415);
                c = trim(p, c, 0xFFFFFF, 1.0, false);
                if (p.front()) c = snowflake(p, c, 8, 9, 5.5, 0xFFFFFF);
                yield c;
            }
            case ARM -> {
                int c = folds(p, 0x9CC4E4, 0xEAF6FF);
                yield p.t > 0.6 ? frostLines(p, mix(c, 0xFFFFFF, 0.3), 1417) : c;
            }
            case LEG -> {
                int c = robe(p, 0x2E4E80, 0x5A82B8, 1419);
                yield p.t > 0.75 ? frostLines(p, mix(c, 0xDCEEFF, 0.4), 1421) : c;
            }
            case BOOT, BAND -> {
                double[] v = voronoi(p.x * 0.4, p.y * 0.4, p.z * 0.4, 1423);
                int c = mix(0x6AA8E0, 0xCFEFFF, v[2]);
                yield shadeEdge(p, v[1] - v[0] < 0.1 ? 0xFFFFFF : c, 0.85);
            }
            case BELT -> filigree(p, 0xC8D8E8);
            case COLLAR -> fur(p, 0xC8D4E0, 0xFFFFFF, 1425);
            case GEM -> radial(p, 0xFFFFFF, 0xC8F0FF, 0x5AA8E8);
            case EYE -> eye(p, eyeColor(p, 0xBFF4FF));
            case CAPE -> {
                int c = robe(p, 0x0E1E3A, 0x24406E, 1427);
                c = falling(p, c, 0xFFFFFF, 1429, 1);
                yield trim(p, c, 0xDCEEFF, 1.0, true);
            }
            case LINING -> fur(p, 0xC8D4E0, 0xFFFFFF, 1431);
            case HALO -> radial(p, 0xFFFFFF, 0xDCF4FF, 0x8EC8F0);
            default -> 0xA8C8E8;
        };
    }

    // ---- Storm ---------------------------------------------------------------------------------

    /** Storm-cloud flesh and robes that roil and flash with lightning, a cyclone over the heart, rain. */
    static int storm(Px p) {
        return switch (p.piece) {
            case HEAD, HAND -> {
                int c = clouds(p, 0x2E3442, 0x6A7486, 1501);
                c = face(p, c, 0x080A10, -1, 0x7FD0FF);
                c = crackle(p, c, 0xCFF4FF, 1503, 1.0);
                yield shadeEdge(p, c, 0.82);
            }
            case HAIR_TOP, HAIR_BACK -> crackle(p, clouds(p, 0x1E222C, 0x4A5262, 1505), 0xCFF4FF, 1507, 0.8);
            case TORSO -> {
                int c = clouds(p, 0x1E2430, 0x4E5A6E, 1509);
                if (p.front()) c = spiral(p, c, 8, 10, 6.5, 0x9FE4FF, 2);
                yield trim(p, crackle(p, c, 0xCFF4FF, 1511, 1.0), 0x7FD0FF, 0.9, false);
            }
            case ARM, LEG -> crackle(p, clouds(p, 0x1E2430, 0x4E5A6E, p.piece == Piece.ARM ? 1513 : 1515), 0xCFF4FF,
                    1517, 0.9);
            case BOOT -> obsidian(p, 0x1A1E26, 0x4A5A72);
            case BELT, BAND, COLLAR -> {
                int c = mix(0x5A6A80, 0xA8B8CC, fbm(p.x * 0.5, p.y * 0.5, p.z * 0.5, 1519, 2));
                yield shadeEdge(p, crackle(p, c, 0xFFFFFF, 1521, 1.0), 0.8);
            }
            case GEM -> radial(p, 0xFFFFFF, 0xA8E8FF, 0x3A7AFF);
            case EYE -> eye(p, eyeColor(p, 0xC8F4FF));
            case CAPE -> {
                int c = clouds(p, 0x141820, 0x3E4858, 1523);
                c = rain(p, c);
                yield crackle(p, c, 0xE8FBFF, 1525, 1.2);
            }
            case LINING -> crackle(p, mix(0x0A1A3A, 0x1A3A6A, p.t), 0x9FE4FF, 1527, 0.8);
            case HALO -> radial(p, 0xFFFFFF, 0xA8E8FF, 0x3A7AFF);
            default -> 0x3A4250;
        };
    }

    // ---- Plague --------------------------------------------------------------------------------

    /** A skull face and bone hands, a rotting shroud with open ribs, green miasma and moth holes. */
    static int plague(Px p) {
        return switch (p.piece) {
            case HEAD -> {
                int c = bone(p, 1601);
                if (p.front()) c = skull(p, c);
                yield shadeEdge(p, c, 0.8);
            }
            case HAND -> {
                int c = mix(0x14180E, 0x262C1A, fbm(p.x, p.y, p.z, 1603, 2));
                if (p.side() && p.u % 2 == 0) c = bone(p, 1605);
                yield shadeEdge(p, c, 0.8);
            }
            case TORSO -> {
                int c = miasma(p, robe(p, 0x1A1E14, 0x3A4228, 1607), 1609);
                if (p.front() && Math.abs(p.s - 0.5) < 0.32 && p.t > 0.12 && p.t < 0.8) c = ribs(p);
                yield c;
            }
            case ARM -> {
                int c = miasma(p, robe(p, 0x1A1E14, 0x3A4228, 1611), 1613);
                yield p.t > 0.8 ? bone(p, 1615) : tatters(p, c, 1617);
            }
            case LEG -> tatters(p, miasma(p, robe(p, 0x14160E, 0x2C3220, 1619), 1621), 1623);
            case BOOT -> (p.v + p.u) % 3 == 0 ? bone(p, 1625) : mix(0x3A3220, 0x5A4A30, fbm(p.x, p.y, p.z, 1627, 2));
            case BELT -> {
                int c = (p.u + p.v) % 2 == 0 ? 0x5A4A30 : 0x3E3220;
                yield p.front() && p.u % 5 == 2 ? bone(p, 1629) : c;
            }
            case BAND, COLLAR -> p.u % 3 == 0 ? 0x5A5440 : bone(p, 1631);
            case GEM -> radial(p, 0xFFFFFF, 0xC8FF8A, 0x4A8A1A);
            case EYE -> eye(p, eyeColor(p, 0xA8FF4A));
            case CAPE -> tatters(p, miasma(p, robe(p, 0x14160E, 0x30361F, 1633), 1635), 1637);
            case LINING -> miasma(p, mix(0x0E120A, 0x1E2A12, p.t), 1639);
            case HALO -> radial(p, 0xFFFFFF, 0xC8FF8A, 0x4A8A1A);
            default -> 0x2A2E1E;
        };
    }

    private static int bone(Px p, int seed) {
        double n = fbm(p.x * 0.3, p.y * 0.3, p.z * 0.3, seed, 3);
        int c = mix(0xB8AC88, 0xE8E0C4, n);
        double crack = 1 - Math.abs(fbm(p.x * 0.15, p.y * 0.15, p.z * 0.15, seed + 1, 2) - 0.5) * 24;
        return crack > 0 ? mix(c, 0x6A5E44, crack * 0.6) : c;
    }

    /** Hollow sockets with a green light deep inside, a nasal hollow and a row of teeth. */
    private static int skull(Px p, int c) {
        double x = p.u + 0.5, y = p.v + 0.5;
        for (double ex : new double[] { 4, 12 }) {
            double d = Math.hypot((x - ex) / 2.9, (y - 9) / 2.4);
            if (d < 1) return glow(0x050604, 0x7ADA2A, (1 - d) * 0.6 * (0.6 + 0.4 * p.pulse(0)));
            if (d < 1.25) c = scale(c, 0.7);
        }
        if (y > 10.5 && y < 13 && Math.abs(x - 8) < (y - 10.5) * 0.6 + 0.2) return 0x0A0A06;
        if (y > 13.2 && y < 15.2 && Math.abs(x - 8) < 4) return p.u % 2 == 0 ? 0xE8E0C4 : 0x3A3428;
        if (Math.abs(y - 15.6) < 0.5) return scale(c, 0.6);
        return c;
    }

    private static int ribs(Px p) {
        double x = p.u + 0.5, y = p.v + 0.5;
        int c = glow(0x060805, 0x3A6A12, 0.35 * p.pulse(y / 24));
        if (Math.abs(x - 8) < 0.9) return (p.v % 2 == 0) ? 0xD8D0B4 : 0x8A8268;
        double k = (y - Math.abs(x - 8) * 0.45) % 3.0;
        return k < 1.0 ? mix(0xD8D0B4, 0x8A8268, Math.abs(x - 8) / 6) : c;
    }

    /** A sickly green haze drifting through. */
    private static int miasma(Px p, int c, int seed) {
        double n = fbm(p.x * 0.14 + p.dx(0.7), p.y * 0.14 + p.dy(0.7), p.z * 0.14, seed, 3);
        return glow(c, 0x6ACC1A, smooth(0.55, 0.85, n) * 0.55);
    }

    /** Rags: a ragged hem and moth-eaten holes, both dark. */
    private static int tatters(Px p, int c, int seed) {
        if (p.side() && p.v >= p.fh - 3 && hash(p.u, 0, p.face.ordinal(), seed) > 0.4 + 0.2 * (p.fh - p.v)) {
            return 0x050604;
        }
        return hash(p.u / 2, p.v / 2, p.face.ordinal(), seed + 1) > 0.965 ? 0x060805 : c;
    }

    // ---- Celestial -----------------------------------------------------------------------------

    /** Midnight-blue skin and robes mapped with constellations, silver hair, a turning astrolabe. */
    static int celestial(Px p) {
        return switch (p.piece) {
            case HEAD, HAND -> {
                int c = flesh(p, 0x1A2450, 0x34448A, 1701);
                c = constellations(p, c, 0xE0E8FF, 1703);
                c = face(p, c, 0x05081A, 0xFFFFFF, 0xC8D8FF);
                if (p.piece == Piece.HEAD && hairArea(p)) c = stars(p, strands(p, 0xA8B4D8, 0xFFFFFF, 1705), 0.94,
                        0xFFFFFF, 1707);
                yield shadeEdge(p, c, 0.85);
            }
            case HAIR_TOP, HAIR_BACK -> shadeEdge(p, stars(p, strands(p, 0xA8B4D8, 0xFFFFFF, 1709), 0.94, 0xFFFFFF,
                    1711), 0.85);
            case TORSO -> {
                int c = constellations(p, robe(p, 0x080E2C, 0x1E2A60, 1713), 0xD0DCFF, 1715);
                c = trim(p, c, 0xC8D0E0, 1.0, false);
                if (p.front()) c = astrolabe(p, c, 8, 10.5);
                yield c;
            }
            case ARM, LEG -> {
                int c = constellations(p, robe(p, 0x080E2C, 0x1E2A60, p.piece == Piece.ARM ? 1717 : 1719),
                        0xD0DCFF, 1721);
                yield p.side() && p.v >= p.fh - 1 ? 0xC8D0E0 : c;
            }
            case BOOT, BELT, BAND, COLLAR -> filigree(p, 0xC8D0E0);
            case GEM -> radial(p, 0xFFFFFF, 0xE0E8FF, 0x6A7ADA);
            case EYE -> eye(p, eyeColor(p, 0xE0E8FF));
            case CAPE -> {
                int c = nebula(p, 0x03061A, 0x22307A, 1723);
                yield trim(p, constellations(p, c, 0xFFFFFF, 1725), 0xC8D0E0, 0.0, true);
            }
            case LINING -> {
                int c = mix(0x2A1A5A, 0xC86A8A, smooth(0.3, 1.0, p.t));
                yield stars(p, c, 0.97, 0xFFFFFF, 1727);
            }
            case HALO -> radial(p, 0xFFFFFF, 0xE0E8FF, 0x8A9AE8);
            default -> 0x1E2A60;
        };
    }

    /** Rings of an astrolabe with markers turning in opposite directions. */
    private static int astrolabe(Px p, int c, double cx, double cy) {
        double x = p.u + 0.5 - cx, y = p.v + 0.5 - cy;
        double r = Math.hypot(x, y), a = Math.atan2(y, x);
        int silver = 0xD8E0F0;
        if (Math.abs(r - 6) < 0.45 || Math.abs(r - 3.6) < 0.4) c = mix(c, silver, 0.85);
        if (Math.abs(x) < 0.4 && r < 6 || Math.abs(y) < 0.4 && r < 6) c = mix(c, silver, 0.4);
        double m1 = Math.hypot(x - 6 * Math.cos(p.phase), y - 6 * Math.sin(p.phase));
        double m2 = Math.hypot(x - 3.6 * Math.cos(-p.phase * 2 + 1), y - 3.6 * Math.sin(-p.phase * 2 + 1));
        if (m1 < 1.1) c = glow(c, 0xFFFFFF, 0.9);
        if (m2 < 0.9) c = glow(c, 0xFFE8A8, 0.9);
        if (r < 1.2) c = glow(c, 0xFFFFFF, 0.8);
        return c;
    }

    // ---- Clockwork -----------------------------------------------------------------------------

    /** Riveted brass plates, a grille mouth, turning gears in an open chest, a leather coat and a blueprint lining. */
    static int clockwork(Px p) {
        return switch (p.piece) {
            case HEAD -> {
                int c = plates(p, 0x9A7430, 0xD8B060, 1801);
                c = face(p, c, 0x1A1208, -1, 0x6AD8FF);
                if (p.front() && p.v >= 12 && p.v <= 14 && p.u >= 5 && p.u <= 10) c = p.v == 13 ? 0x1A1208 : 0x6A5020;
                if (p.front()) c = gear(p, c, 8, 2.5, 2.2, 6, p.phase / 6, 0xE8C878);
                yield c;
            }
            case HAND -> plates(p, 0x3A3A40, 0x6A6A72, 1803);
            case TORSO -> {
                int c = plates(p, 0x8A5A2A, 0xC8884A, 1805);
                if (p.front() && Math.abs(p.s - 0.5) < 0.42 && p.t > 0.12 && p.t < 0.72) {
                    double core = Math.hypot(p.u + 0.5 - 8, p.v + 0.5 - 10);
                    c = glow(0x0A0C10, 0x2A8ACF, Math.max(0, 1 - core / 7) * (0.6 + 0.3 * p.pulse(0)));
                    c = gear(p, c, 6, 7.5, 4.0, 8, p.phase / 8, 0xD8B060);
                    c = gear(p, c, 11, 13, 3.0, 6, -p.phase / 6 + 0.3, 0xC8884A);
                }
                if (p.back()) {
                    if (Math.abs(p.u - 4) < 1 || Math.abs(p.u - 11) < 1) c = mix(0x5A3A1A, 0xA8783A, (p.u % 2) * 0.5);
                }
                yield c;
            }
            case ARM -> {
                int c = plates(p, 0x8A5A2A, 0xC8884A, 1807);
                yield p.side() && Math.abs(p.s - 0.5) < 0.12 && p.t > 0.3 ? 0x3A3A40 : c;
            }
            case LEG -> {
                int c = plates(p, 0x3A3A40, 0x6A6A72, 1809);
                yield p.side() && p.t > 0.4 && p.t < 0.55 ? plates(p, 0x9A7430, 0xD8B060, 1811) : c;
            }
            case BOOT -> plates(p, 0x26262C, 0x4A4A52, 1813);
            case BELT, BAND, COLLAR -> {
                int c = plates(p, 0x8A5A2A, 0xC8884A, 1815);
                int tick = Math.floorMod(p.u - p.frame, 4);
                yield p.side() && tick == 0 && p.v == p.fh / 2 ? glow(c, 0x6AD8FF, 0.8) : c;
            }
            case GEM -> radial(p, 0xFFFFFF, 0x9FE8FF, 0x2A7ACF);
            case EYE -> eye(p, eyeColor(p, 0x8AE8FF));
            case CAPE -> {
                int c = mix(0x3A2414, 0x5A3A22, fbm(p.x * 0.3, p.y * 0.2, 0, 1817, 3));
                yield p.side() && (p.u <= 0 || p.u >= p.fw - 1) && p.v % 3 == 0 ? 0xD8B060 : c;
            }
            case LINING -> {
                int c = 0x0E2A5A;
                if (p.u % 4 == 0 || p.v % 4 == 0) c = 0x2A4A80;
                yield gear(p, c, 10, 12, 6, 10, p.phase / 10, 0xC8DCF8) == c ? c
                        : mix(c, 0xE8F0FF, 0.5);
            }
            case HALO -> {
                int c = plates(p, 0x9A7430, 0xD8B060, 1819);
                yield glow(c, 0xFFE8A8, 0.2 * p.pulse(p.s));
            }
            default -> 0x8A5A2A;
        };
    }

    // ---- Jester --------------------------------------------------------------------------------

    /** A white painted face with diamond eyes and a red grin, motley diamonds, a ruff and gold bells. */
    static int jester(Px p) {
        return switch (p.piece) {
            case HEAD -> {
                int c = flesh(p, 0xE4E0DC, 0xFFFFFF, 1901);
                if (p.front()) c = jesterFace(p, c);
                if (hairArea(p)) c = harlequin(p, 0x5A1A8A, 0xE8B828, 2, 3);
                yield shadeEdge(p, c, 0.85);
            }
            case HAND -> shadeEdge(p, 0xF4F0EC, 0.85);
            case HAIR_TOP, HAIR_BACK -> harlequin(p, 0x5A1A8A, 0xE8B828, 3, 3);
            case TORSO -> {
                boolean left = p.face == Face.WEST || ((p.front() || p.back()) && (p.u < 8) == p.front());
                int c = left ? harlequin(p, 0x5A1A8A, 0xE8B828, 2, 3) : harlequin(p, 0x1E7A3A, 0x0E0E14, 2, 3);
                if (p.front() && Math.abs(p.u + 0.5 - 8) < 1.2 && p.v % 5 == 3) c = 0xFF4AE0; // pompoms
                yield c;
            }
            case ARM -> {
                int c = ((p.v + p.u) / 3) % 2 == 0 ? 0xE8B828 : 0x5A1A8A;
                yield shadeEdge(p, c, 0.85);
            }
            case LEG -> harlequin(p, 0x1E7A3A, 0x5A1A8A, 2, 3);
            case BOOT -> {
                int c = (p.u + p.v) % 4 < 2 ? 0x5A1A8A : 0x3A0E5A;
                yield p.v == 0 ? 0xE8B828 : c;
            }
            case BELT -> {
                int c = filigree(p, 0xE8B828);
                yield p.side() && p.u % 4 == 2 ? glow(c, 0xFFFFFF, 0.5 * p.pulse(p.u / 8.0)) : c;
            }
            case BAND, COLLAR -> {
                // A white ruff: scalloped pleats with soft shadows.
                double k = 0.5 + 0.5 * Math.cos((p.u + p.x * 0.2) * Math.PI / 1.5);
                yield mix(0xB8B4C0, 0xFFFFFF, 0.3 + 0.7 * k);
            }
            case GEM -> {
                int c = radial(p, 0xFFFFFF, 0xFFE07A, 0xC8901A);
                yield glow(c, 0xFFFFFF, 0.3 * p.pulse(p.s));
            }
            case EYE -> eye(p, eyeColor(p, 0xFF8AE8));
            case CAPE -> {
                int c = p.u < p.fw / 2 ? harlequin(p, 0x5A1A8A, 0xE8B828, 3, 4) : harlequin(p, 0x1E7A3A, 0x0E0E14, 3, 4);
                if (p.v >= p.fh - 3) {
                    int k = Math.floorMod(p.u, 4);
                    if (p.v - (p.fh - 3) > Math.abs(k - 1.5)) c = 0xE8B828;
                }
                yield c;
            }
            case LINING -> folds(p, 0xC8901A, 0xFFD54A);
            case HALO -> radial(p, 0xFFFFFF, 0xFF8AE8, 0xB21AA8);
            default -> 0x5A1A8A;
        };
    }

    private static int jesterFace(Px p, int c) {
        double x = p.u + 0.5, y = p.v + 0.5;
        // Diamonds through each eye, one violet and one gold.
        for (int i = 0; i < 2; i++) {
            double ex = i == 0 ? 4 : 12;
            double d = Math.abs(x - ex) / 2.2 + Math.abs(y - 9) / 4.5;
            if (d < 1) c = mix(c, i == 0 ? 0x5A1A8A : 0xC8901A, d > 0.8 ? 1 : 0.75);
        }
        // Rosy cheeks.
        for (double cx : new double[] { 3, 13 }) {
            double d = Math.hypot(x - cx, y - 12.5);
            if (d < 1.6) c = mix(c, 0xFF8AA8, 0.5 * (1 - d / 1.6));
        }
        // A wide red grin curling up at the ends.
        if (Math.abs(x - 8) < 4.2 && Math.abs(y - (13.6 - 0.07 * Math.pow(x - 8, 2))) < 0.6) c = 0xD0102A;
        return c;
    }

    /** Diamonds of two colors with thin dark seams; w and h are a diamond's half size in texels. */
    private static int harlequin(Px p, int a, int b, int w, int h) {
        double u = p.side() ? p.u + 0.5 : p.x, v = p.side() ? p.v + 0.5 : p.z;
        double i = (u / w + v / h) / 2, j = (u / w - v / h) / 2;
        int c = ((int) Math.floor(i) + (int) Math.floor(j)) % 2 == 0 ? a : b;
        double fi = i - Math.floor(i), fj = j - Math.floor(j);
        if (Math.min(Math.min(fi, 1 - fi), Math.min(fj, 1 - fj)) < 0.06) c = scale(c, 0.55);
        return c;
    }

    // ---- Moon ----------------------------------------------------------------------------------

    /** Silver-lilac skin, long silver hair, pale silks with the moon's phases, a moonrise on the cloak. */
    static int moon(Px p) {
        return switch (p.piece) {
            case HEAD, HAND -> {
                int c = flesh(p, 0xC0C4DC, 0xF2F2FF, 2001);
                c = humanFace(p, c, 0xA8B0D0, 0x9A86B8, 0xC8D8FF);
                if (p.piece == Piece.HEAD) {
                    if (p.front() && moonDisc(p.u + 0.5, p.v + 0.5, 8, 4.2, 1.3, 0.9) == 1) c = glow(c, 0xFFFFFF, 0.9);
                    if (hairArea(p)) c = strands(p, 0xB8BCD8, 0xFFFFFF, 2003);
                }
                yield shadeEdge(p, c, 0.88);
            }
            case HAIR_TOP, HAIR_BACK -> shadeEdge(p, strands(p, 0xB8BCD8, 0xFFFFFF, 2005), 0.88);
            case TORSO -> {
                int c = folds(p, 0x6A7AAA, 0xB4C2EC);
                c = trim(p, c, 0xE0E4F0, 1.0, false);
                if (p.front()) {
                    double[] offs = { 1.3, 1.9, 99, -1.9, -1.3 };
                    for (int i = 0; i < 5; i++) {
                        int m = moonDisc(p.u + 0.5, p.v + 0.5, 2.4 + i * 2.8, 4.5, 1.2, offs[i]);
                        if (m == 1) c = 0xF4F4FF;
                        else if (m == 0) c = 0x2A3260;
                    }
                    int big = moonDisc(p.u + 0.5, p.v + 0.5, 8, 13, 4.2, 1.6);
                    if (big == 1) c = glow(0xE8ECFF, 0xFFFFFF, 0.3 * p.pulse(0));
                }
                yield c;
            }
            case ARM -> stars(p, folds(p, 0x6A7AAA, 0xB4C2EC), 0.95, 0xFFFFFF, 2007);
            case LEG -> {
                int c = folds(p, 0x3E4A7E, 0x7484BA);
                yield p.side() && p.v >= p.fh - 1 ? 0xE0E4F0 : c;
            }
            case BOOT, BELT, BAND, COLLAR -> filigree(p, 0xD8DCE8);
            case GEM -> radial(p, 0xFFFFFF, 0xE0E8FF, 0x8A9AD8);
            case EYE -> eye(p, eyeColor(p, 0xE8F0FF));
            case CAPE -> {
                int c = stars(p, mix(0x2A2A6A, 0x0A0E2A, p.t), 0.96, 0xFFFFFF, 2009);
                if (p.back()) {
                    double d = Math.hypot(p.u + 0.5 - p.fw / 2.0, p.v + 0.5 - 9);
                    if (d < 6.5) {
                        double crater = fbm(p.u * 0.4, p.v * 0.4, 0, 2011, 3);
                        c = mix(0xF0F2FF, 0xA8ACC8, smooth(0.45, 0.75, crater));
                    } else if (d < 8.5) {
                        c = glow(c, 0xC8D8FF, (8.5 - d) / 2 * 0.5);
                    }
                }
                yield trim(p, c, 0xD8DCE8, 0.0, true);
            }
            case LINING -> folds(p, 0xA8B0D0, 0xE8ECFF);
            case HALO -> radial(p, 0xFFFFFF, 0xF0F4FF, 0xA8B8E8);
            default -> 0xA8B0D0;
        };
    }

    /** 1 when (x, y) is on the lit part of a moon disc, 0 on its shadow, -1 off it. */
    private static int moonDisc(double x, double y, double cx, double cy, double r, double shadowShift) {
        if (Math.hypot(x - cx, y - cy) > r) return -1;
        return Math.hypot(x - cx - shadowShift * r, y - cy) < r ? 0 : 1;
    }

    // ---- Fire titan ----------------------------------------------------------------------------

    /** Cracked basalt with wide molten seams, flame hair and beard, a molten heart, a cape burning at the hem. */
    static int titan(Px p) {
        return switch (p.piece) {
            case HEAD, HAND, TORSO, ARM, LEG -> {
                int c = lavaRock(p, 2101);
                if (p.piece == Piece.HEAD && p.front()) {
                    c = face(p, c, 0x2A0A00, -1, 0xFFB030);
                    c = moltenMouth(p, c);
                }
                if (p.piece == Piece.TORSO && p.front()) {
                    double d = Math.hypot(p.u + 0.5 - 8, p.v + 0.5 - 8);
                    if (d < 3.5) c = glow(c, d < 1.6 ? 0xFFF0B0 : 0xFF8A1E, (1 - d / 3.5) * (0.7 + 0.3 * p.pulse(0)));
                }
                yield shadeEdge(p, c, 0.8);
            }
            case HAIR_TOP, HAIR_BACK, BEARD -> flame(p, 2103);
            case BOOT -> lavaRock(p, 2105);
            case BELT, BAND, COLLAR -> {
                int c = obsidian(p, 0x1E1A1C, 0x3E3436);
                yield p.side() && p.edge() < 1 ? glow(c, 0xFF6A00, 0.6 + 0.3 * p.pulse(p.s)) : c;
            }
            case GEM -> radial(p, 0xFFFFFF, 0xFFD040, 0xE84A0A);
            case EYE -> eye(p, eyeColor(p, 0xFFD040));
            case CAPE -> {
                double n = fbm(p.x * 0.2, p.y * 0.2, p.z * 0.2, 2107, 3);
                int c = mix(0x0A0606, 0x22140E, n);
                double heat = smooth(0.6, 1.0, p.t);
                c = glow(c, 0xFF5A0A, heat * (0.6 + 0.35 * p.pulse(p.s * 3)));
                yield risingSparks(p, c, 2109);
            }
            case LINING -> embers(p, mix(0xC2400A, 0x5A1404, p.t), 0.08, 0.6, 2111);
            case HALO -> radial(p, 0xFFFFFF, 0xFFD040, 0xE84A0A);
            default -> 0x1E1614;
        };
    }

    /** Dark basalt plates split by wide glowing seams that pulse. */
    private static int lavaRock(Px p, int seed) {
        double[] v = voronoi(p.x * 0.22, p.y * 0.22, p.z * 0.22, seed);
        double e = v[1] - v[0];
        int c = mix(0x161210, 0x3A302C, 0.3 + 0.5 * v[2]);
        c = mix(c, scale(c, 0.6), smooth(0.0, 0.5, v[0]));
        double flicker = 0.7 + 0.3 * Math.sin(p.phase * 2 + v[2] * 10);
        if (e < 0.16) {
            double k = 1 - e / 0.16;
            c = mix(c, mix(0xE8400A, 0xFFE07A, k * k), Math.min(1, k * 1.5) * flicker);
        } else if (e < 0.3) {
            c = glow(c, 0x8A1A04, (0.3 - e) / 0.14 * 0.5);
        }
        return c;
    }

    /** Tongues of flame licking upward, white-hot near the roots. */
    private static int flame(Px p, int seed) {
        double up = p.y / Math.max(1, p.piece.h);
        double n = fbm(p.x * 0.35 + p.dx(0.7), p.y * 0.3 + p.dy(1.2), p.z * 0.35, seed, 3);
        double heat = n * 1.45 - up * 0.55 + 0.1;
        return ramp(heat, 0x1A0400, 0x8A1A04, 0xE84A0A, 0xFF9A1E, 0xFFE8A0);
    }

    // ---- Abyss ---------------------------------------------------------------------------------

    /** Black-teal flesh with glowing spots and blinking eyes everywhere, a great eye in the chest. */
    static int abyss(Px p) {
        return switch (p.piece) {
            case HEAD, HAND -> {
                int c = deep(p, 2201);
                if (p.piece == Piece.HEAD && p.front()) {
                    c = face(p, c, 0x000000, -1, 0x2AE8D0);
                    c = openEye(p, c, 8, 3.2, 2.6, 1.4, 0xE8F04A, 0);
                } else {
                    c = eyesField(p, c, 2203, 0.3);
                }
                yield shadeEdge(p, c, 0.85);
            }
            case HAIR_TOP, HAIR_BACK -> {
                // Hanging tendrils with glowing suckers.
                int c = (p.u % 3 == 0) ? 0x021014 : mix(0x0A3A40, 0x16606A, fbm(p.x, p.y * 0.3, p.z, 2205, 2));
                yield p.u % 3 == 1 && p.v % 3 == 0 ? glow(c, 0x7FFFE8, 0.5 * p.pulse(p.v / 9.0)) : c;
            }
            case TORSO -> {
                int c = deep(p, 2207);
                if (p.front()) {
                    c = openEye(p, c, 8, 10, 6, 3.2, 0xE8F04A, 1);
                    if (Math.hypot((p.u + 0.5 - 8) / 6, (p.v + 0.5 - 10) / 3.2) > 1.2) c = eyesField(p, c, 2209, 0.25);
                } else {
                    c = eyesField(p, c, 2211, 0.3);
                }
                yield c;
            }
            case ARM, LEG -> eyesField(p, deep(p, p.piece == Piece.ARM ? 2213 : 2215), 2217, 0.28);
            case BOOT -> {
                double[] v = voronoi(p.x * 0.5, p.y * 0.5, p.z * 0.5, 2219);
                yield mix(0x2A3A3A, 0x6A7A72, 1 - v[0]);
            }
            case BELT, BAND, COLLAR -> {
                int c = mix(0x2A5A4A, 0x5AA08A, fbm(p.x * 0.4, p.y * 0.4, p.z * 0.4, 2221, 3));
                yield shadeEdge(p, c, 0.75);
            }
            case GEM -> {
                int c = radial(p, 0x000000, 0xE8F04A, 0x6A7A0A);
                yield Math.abs(p.s - 0.5) < 0.12 ? 0x000000 : c;
            }
            case EYE -> eye(p, eyeColor(p, 0xE8F04A));
            case CAPE -> {
                int c = mix(0x020A0E, 0x06202A, fbm(p.x * 0.15, p.y * 0.15, 0, 2223, 3));
                c = falling(p, c, 0x7FFFE8, 2225, -1);
                yield eyesField(p, c, 2227, 0.15);
            }
            case LINING -> {
                int c = mix(0x04161C, 0x0A2E36, p.t);
                double band = Math.abs(Math.sin(p.x * 0.6 + Math.sin(p.y * 0.3 + p.phase) * 2));
                yield band < 0.12 ? glow(c, 0x2AE8D0, 0.7) : c;
            }
            case HALO -> radial(p, 0xFFFFFF, 0x7FFFE8, 0x0E6A6A);
            default -> 0x06202A;
        };
    }

    private static int deep(Px p, int seed) {
        double n = fbm(p.x * 0.12 + p.dx(0.4), p.y * 0.12 + p.dy(0.4), p.z * 0.12, seed, 3);
        int c = mix(0x020C10, 0x0C2E36, smooth(0.25, 0.85, n));
        double h = hash(p.u / 2, p.v / 2, p.face.ordinal() + p.piece.ordinal() * 7, seed + 1);
        if (h > 0.93 && (p.u + p.v) % 2 == 0) c = glow(c, 0x2AE8D0, 0.4 + 0.5 * p.pulse(h * 7));
        return c;
    }

    /** One almond eye with a slit pupil that looks around, blinking once a loop (offset in frames). */
    private static int openEye(Px p, int c, double cx, double cy, double rx, double ry, int iris, int blinkAt) {
        double x = (p.u + 0.5 - cx) / rx, y = (p.v + 0.5 - cy) / ry;
        double lid = 1 - x * x;
        if (lid <= 0) return c;
        if (Math.floorMod(p.frame - blinkAt * 3, 8) == 0) {
            return Math.abs(y) < 0.25 ? 0x000000 : c;
        }
        if (Math.abs(y) > lid) return Math.abs(y) < lid + 0.25 ? 0x000000 : c;
        double look = 0.25 * Math.sin(p.phase);
        double ix = x - look, ir = Math.hypot(ix, y);
        if (Math.abs(ix) < 0.13) return 0x000000;
        if (ir < 0.55) return mix(glow(iris, 0xFFFFFF, 0.3), scale(iris, 0.6), ir / 0.55);
        return mix(0xC8C8A8, 0x6A6A52, Math.abs(y) / Math.max(0.01, lid));
    }

    /** Small eyes scattered over a face, each blinking on its own beat. */
    private static int eyesField(Px p, int c, int seed, double chance) {
        int cw = 5, ch = 4;
        int ci = Math.floorDiv(p.u, cw), cj = Math.floorDiv(p.v, ch);
        double h = hash(ci, cj, p.face.ordinal() + p.piece.ordinal() * 7, seed);
        if (h > chance) return c;
        double x = (Math.floorMod(p.u, cw) + 0.5 - cw / 2.0) / 2.2, y = (Math.floorMod(p.v, ch) + 0.5 - ch / 2.0) / 1.4;
        double lid = 1 - x * x;
        if (lid <= 0 || Math.abs(y) > lid * 0.9) return c;
        if (Math.floorMod(p.frame + (int) (h * 80), 8) == 0) return Math.abs(y) < 0.3 ? 0x000000 : c;
        if (Math.abs(x) < 0.2) return 0x000000;
        return Math.abs(x) < 0.6 ? 0xE8F04A : 0x8A8A6A;
    }

    // ---- Hollow --------------------------------------------------------------------------------

    /** A porcelain mask mended with gold, a black veil, black robes cracked open on a starry void. */
    static int hollow(Px p) {
        return switch (p.piece) {
            case HEAD -> {
                int c = mix(0xE4DCCC, 0xFFFBF2, fbm(p.x * 0.2, p.y * 0.2, p.z * 0.2, 2301, 2));
                c = kintsugi(p, c, 2303, 0.3);
                if (p.front()) {
                    for (double ex : new double[] { 4, 12 }) {
                        double d = Math.hypot((p.u + 0.5 - ex) / 2.4, (p.v + 0.5 - 9) / 1.6);
                        if (d < 1) c = 0x000000;
                        else if (d < 1.35) c = glow(c, 0xF6E3A8, (1.35 - d) * 1.6);
                    }
                    if (Math.abs(p.u + 0.5 - 4 - (p.v - 9) * 0.25) < 0.4 && p.v > 10 && p.v < 15) c = 0xE8B828;
                }
                if (hairArea(p)) c = robe(p, 0x050507, 0x18181E, 2305);
                yield shadeEdge(p, c, 0.88);
            }
            case HAND, BOOT -> kintsugi(p, mix(0xE4DCCC, 0xFFFBF2, fbm(p.x * 0.2, p.y * 0.2, p.z * 0.2, 2307, 2)),
                    2309, 0.35);
            case HAIR_TOP, HAIR_BACK -> robe(p, 0x040406, 0x16161C, 2311);
            case TORSO -> {
                int c = kintsugi(p, robe(p, 0x060608, 0x1A1A20, 2313), 2315, 0.2);
                if (p.front()) {
                    double x = p.u + 0.5 - 8, y = p.v + 0.5 - 10;
                    double rift = Math.abs(x + Math.sin(y * 0.9) * 1.2) - (6 - Math.abs(y)) * 0.32;
                    if (Math.abs(y) < 6 && rift < 0) c = cosmos(p, 0x050208, 2317);
                    else if (Math.abs(y) < 6.5 && rift < 0.7) c = glow(0xE8B828, 0xFFFFFF, 0.3 * p.pulse(0));
                }
                yield c;
            }
            case ARM, LEG -> kintsugi(p, robe(p, 0x060608, 0x1A1A20, p.piece == Piece.ARM ? 2319 : 2321), 2323, 0.2);
            case BELT, BAND, COLLAR -> filigree(p, 0xE8B828);
            case GEM -> radial(p, 0xFFFFFF, 0xFFF0C0, 0xC8901A);
            case EYE -> eye(p, eyeColor(p, 0xFFF0C0));
            case CAPE -> kintsugi(p, robe(p, 0x040406, 0x141418, 2325), 2327, 0.15);
            case LINING -> cosmos(p, 0x020104, 2329);
            case HALO -> radial(p, 0xFFFFFF, 0xFFF0C0, 0xE8B828);
            default -> 0x0A0A0E;
        };
    }

    /** Gold seams along some of the cracks between Voronoi cells, glinting in turn. */
    private static int kintsugi(Px p, int c, int seed, double density) {
        double[] v = voronoi(p.x * 0.16, p.y * 0.16, p.z * 0.16, seed);
        if (v[1] - v[0] < 0.06 && v[2] < density + 0.12) {
            return glow(0xD8A42A, 0xFFF0B0, 0.2 + 0.4 * p.pulse(v[2] * 5));
        }
        return c;
    }

    // ---- Prism ---------------------------------------------------------------------------------

    /** Crystal facets cycling through every color with white edges, a prism splitting light in the chest. */
    static int prism(Px p) {
        return switch (p.piece) {
            case HEAD, HAND -> {
                int c = facets(p, 2401, 0.35, 0.95);
                c = face(p, c, 0x1A0A3A, -1, 0xFFFFFF);
                yield c;
            }
            case HAIR_TOP, HAIR_BACK -> facets(p, 2403, 0.5, 1.0);
            case TORSO -> {
                int c = facets(p, 2405, 0.75, 0.35);
                if (p.front()) c = prismMark(p, c);
                yield c;
            }
            case ARM, LEG -> facets(p, p.piece == Piece.ARM ? 2407 : 2409, 0.6, 0.55);
            case BOOT -> facets(p, 2411, 0.8, 0.3);
            case BELT, BAND, COLLAR -> {
                double h = (p.x + p.y + p.z) * 0.03 + p.phase / (Math.PI * 2);
                yield shadeEdge(p, mix(0xE8ECF4, hsb(h, 0.5, 1), 0.35), 0.8);
            }
            case GEM, HALO -> {
                int c = radial(p, 0xFFFFFF, 0xFFFFFF, hsb(p.phase / (Math.PI * 2) + p.s * 0.3, 0.7, 1));
                yield c;
            }
            case EYE -> eye(p, eyeColor(p, hsb(p.phase / (Math.PI * 2), 0.35, 1)));
            case CAPE -> {
                int c = mix(0x06060E, 0x14102A, fbm(p.x * 0.2, p.y * 0.2, 0, 2413, 3));
                double streak = Math.abs(Math.sin((p.x * 0.5 - p.y * 0.35) + p.phase));
                if (streak < 0.08) c = glow(c, hsb(p.y * 0.04 + p.phase / (Math.PI * 2), 0.8, 1), 0.7);
                yield stars(p, c, 0.97, 0xFFFFFF, 2415);
            }
            case LINING -> hsb(p.t * 0.85 + p.phase / (Math.PI * 2), 0.65, 0.9);
            default -> 0xC8C8E8;
        };
    }

    /** Crystal facets: each Voronoi cell its own hue, turning through the loop, with white edges. */
    private static int facets(Px p, int seed, double dark, double saturation) {
        double[] v = voronoi(p.x * 0.17, p.y * 0.17, p.z * 0.17, seed);
        double hue = 0.55 + 0.35 * v[2] + p.phase / (Math.PI * 2);
        int c = hsb(hue, 0.35 * saturation + 0.1, 1 - dark * 0.6);
        // Each facet is lit from one side, like a cut gem.
        double tilt = hash((int) (v[2] * 1000), 0, 0, seed + 1);
        c = mix(c, scale(c, 0.65), 0.6 * tilt + 0.4 * v[0]);
        if (v[1] - v[0] < 0.09) c = glow(c, 0xFFFFFF, 0.75);
        return c;
    }

    /** A triangle prism catching a white beam and throwing a rainbow. */
    private static int prismMark(Px p, int c) {
        double x = p.u + 0.5, y = p.v + 0.5;
        double cx = 8, top = 6, bottom = 13.5;
        double half = (y - top) / (bottom - top) * 3.6;
        if (y > top && y < bottom && Math.abs(x - cx) < half) {
            return Math.abs(Math.abs(x - cx) - half) < 0.6 || y > bottom - 0.8 ? 0xFFFFFF : mix(0xC8D8FF, 0xFFFFFF, 0.4);
        }
        if (x < cx - 1 && segDist(x, y, 0, 8, cx - 1.6, 10.2) < 0.5) return 0xFFFFFF;
        if (x > cx + 1.5) {
            double spread = (y - 10.2) / Math.max(0.5, x - (cx + 1.5));
            if (spread > -0.1 && spread < 0.9) return hsb(spread * 0.8, 0.85, 1);
        }
        return c;
    }

    // ---- Bloom ---------------------------------------------------------------------------------

    /** Rosy skin, rose-gold hair woven with blossoms, flower-printed silks, vines and drifting petals. */
    static int bloom(Px p) {
        return switch (p.piece) {
            case HEAD, HAND -> {
                int c = flesh(p, 0xD49A82, 0xF6CDB4, 2501);
                c = humanFace(p, c, 0x6A3A2A, 0xC8506A, 0xFF9AC8);
                if (p.piece == Piece.HEAD) {
                    if (p.front()) {
                        for (double cx : new double[] { 3, 13 }) {
                            double d = Math.hypot(p.u + 0.5 - cx, p.v + 0.5 - 12);
                            if (d < 1.8) c = mix(c, 0xFF8AA8, 0.35 * (1 - d / 1.8));
                        }
                    }
                    if (hairArea(p)) c = flowers(p, strands(p, 0xA8506A, 0xE8909A, 2503), 2505, 6, 0.35);
                }
                yield shadeEdge(p, c, 0.88);
            }
            case HAIR_TOP, HAIR_BACK -> shadeEdge(p, flowers(p, strands(p, 0xA8506A, 0xE8909A, 2507), 2509, 6, 0.35),
                    0.88);
            case TORSO -> {
                int c = flowers(p, folds(p, 0xE8A0B4, 0xFFE4EC), 2511, 6, 0.55);
                yield vineTrim(p, c);
            }
            case ARM -> vineTrim(p, flowers(p, folds(p, 0xE8A0B4, 0xFFE4EC), 2513, 6, 0.45));
            case LEG -> {
                int c = mix(folds(p, 0xE8A0B4, 0xFFE4EC), folds(p, 0x4A8A3A, 0x86C860), smooth(0.2, 0.9, p.t));
                yield flowers(p, c, 2515, 6, 0.4);
            }
            case BOOT -> mix(0x3A7A2A, 0x6AB04A, fbm(p.x * 0.4, p.y * 0.4, p.z * 0.4, 2517, 2));
            case BELT, BAND, COLLAR -> {
                int c = (p.u + p.v) % 4 < 2 ? 0x3E8A2A : 0x5AA83A;
                yield flowers(p, c, 2519, 4, 0.5);
            }
            case GEM -> radial(p, 0xFFFFFF, 0xFFC8DC, 0xE8507A);
            case EYE -> eye(p, eyeColor(p, 0xFFB8D8));
            case CAPE -> {
                int c = mix(0xF0A0B8, 0x6AB04A, smooth(0.4, 1.0, p.t));
                c = falling(p, c, 0xFFE4EC, 2521, 1);
                yield flowers(p, c, 2523, 7, 0.3);
            }
            case LINING -> flowers(p, folds(p, 0xF4E8DC, 0xFFFAF2), 2525, 6, 0.4);
            case HALO -> radial(p, 0xFFFFFF, 0xFFC8DC, 0xE8507A);
            default -> 0xE8A0B4;
        };
    }

    private static int vineTrim(Px p, int c) {
        if (!p.side()) return c;
        boolean edge = p.u <= 0 || p.u >= p.fw - 1;
        boolean leaf = (p.u == 1 || p.u == p.fw - 2) && p.v % 4 == 0;
        return edge ? 0x3E8A2A : leaf ? 0x6AB83A : c;
    }

    // ---- Shared motifs -------------------------------------------------------------------------

    /** Mortal-like skin: soft noise between two tones. */
    static int flesh(Px p, int dark, int light, int seed) {
        double n = fbm(p.x * 0.15, p.y * 0.15, p.z * 0.15, seed, 3);
        return mix(dark, light, smooth(0.2, 0.85, n));
    }

    /** A mortal-like face on a head's front: shaded sockets with a faint glow, brows, a nose and lips. */
    static int humanFace(Px p, int c, int brow, int lip, int glowColor) {
        if (!(p.piece == Piece.HEAD && p.front())) return c;
        for (double ex : new double[] { 0.25, 0.75 }) {
            double dx = (p.s - ex) / 0.17, dy = (p.t - 0.5625) / 0.11;
            double d = dx * dx + dy * dy;
            if (d < 1) c = mix(c, scale(c, 0.55), 0.6 * (1 - d * 0.5));
            else if (d < 1.9) c = glow(c, glowColor, 0.12 * (1.9 - d));
            if (p.v == 6 && Math.abs(p.s - ex) < 0.17) c = mix(c, brow, 0.85);
        }
        if (p.v >= 9 && p.v <= 11 && p.u == 8) c = scale(c, 0.8);
        if (p.v == 11 && (p.u == 7 || p.u == 9)) c = scale(c, 0.88);
        if (p.v == 13 && p.u >= 6 && p.u <= 9) c = mix(c, lip, 0.7);
        return c;
    }

    /** Where hair shows on a head box: the top, the back, the upper sides and a hairline over the brow. */
    static boolean hairArea(Px p) {
        if (p.piece != Piece.HEAD) return false;
        return switch (p.face) {
            case UP -> true;
            case NORTH -> p.y > 3;
            case SOUTH -> p.v <= 1 || (p.v == 2 && (p.u < 3 || p.u > 12));
            case WEST, EAST -> p.y > 12.5 || (p.z < 7 && p.y > 4);
            default -> false;
        };
    }

    /** Hair: strands with a slow sheen running down them. */
    static int strands(Px p, int dark, int light, int seed) {
        double lane = (p.face == Face.WEST || p.face == Face.EAST) ? p.z : p.x;
        double strand = hash((int) Math.floor(lane), 0, p.face.ordinal(), seed);
        double n = fbm(lane * 0.5, (p.y + p.z) * 0.12, 0, seed + 1, 2);
        int c = mix(dark, light, 0.2 + 0.6 * strand * (0.5 + 0.5 * n) + 0.15 * Math.sin(p.y * 0.8 + strand * 6));
        double sheen = Math.max(0, Math.sin(p.y * 0.4 - p.phase + strand * 2));
        return glow(c, light, Math.pow(sheen, 12) * 0.3);
    }

    /** Tight curls, for beards and hair. */
    static int curls(Px p, int dark, int light, int seed) {
        double[] v = voronoi(p.x * 0.6, p.y * 0.6, p.z * 0.6, seed);
        int c = mix(dark, light, 0.25 + 0.75 * (1 - Math.min(1, v[0] * 1.3)));
        return v[1] - v[0] < 0.08 ? scale(c, 0.8) : c;
    }

    /** A beard: a mouth line and mustache at the top, the lower corners shadowed so it tapers. */
    static int beardShape(Px p, int c, int dark) {
        if (!p.front()) return c;
        int mid = p.fw / 2;
        if (p.v == 2 && Math.abs(p.u - mid) <= 2) return mix(c, dark, 0.8);
        if (p.v <= 1 && Math.abs(p.u - mid) >= 1 && Math.abs(p.u - mid) <= 4) c = scale(c, 0.85);
        if (p.v >= 5 && Math.abs(p.u - mid) > 6 - (p.v - 5) * 1.2) return mix(c, dark, 0.65);
        return c;
    }

    /** Seaweed hair and beard: wavy green strands that sway. */
    private static int weed(Px p, int seed) {
        double sway = Math.sin(p.y * 0.5 + p.phase) * 1.2;
        double lane = ((p.face == Face.WEST || p.face == Face.EAST) ? p.z : p.x) + sway;
        double strand = hash((int) Math.floor(lane), 0, p.face.ordinal(), seed);
        return mix(0x0E4A42, 0x4FB0A0, 0.2 + 0.7 * strand);
    }

    /** Fur: soft streaks between two tones. */
    static int fur(Px p, int dark, int light, int seed) {
        double n = fbm(p.x * 0.8, p.y * 0.25, p.z * 0.8, seed, 3);
        double streak = hash((int) (p.x + p.z), (int) (p.y / 2), 0, seed + 1);
        return mix(dark, light, 0.2 + 0.6 * n + 0.2 * streak);
    }

    /** Cloth woven in a band of meander (a Greek key), from row top down on the side faces. */
    static int meander(Px p, int c, int color, int top) {
        String[] key = { "######", "#.....", "#.###.", "#.#.#.", "#...#.", "#####." };
        int r = p.v - top;
        if (r < 0 || r >= key.length || !p.side()) return c;
        return key[r].charAt(Math.floorMod(p.u, 6)) == '#' ? mix(c, color, 0.9) : c;
    }

    /** A lightning bolt emblem from top to bottom (texels on the face). */
    static int bolt(Px p, int c, double cx, double top, double bottom, double width, int color) {
        double x = p.u + 0.5, y = p.v + 0.5, h = bottom - top;
        double d = Math.min(Math.min(segDist(x, y, cx + 2.2, top, cx - 1.2, top + h * 0.5),
                segDist(x, y, cx - 1.2, top + h * 0.5, cx + 1.4, top + h * 0.45)),
                segDist(x, y, cx + 1.4, top + h * 0.45, cx - 2.2, bottom));
        double flick = 0.75 + 0.25 * Math.sin(p.phase * 3);
        if (d < width) return glow(c, color, 0.95 * flick);
        if (d < width + 1.2) return glow(c, color, 0.35 * flick);
        return c;
    }

    /** Lightning crackling through: bright ridges of noise that flash on some frames. */
    static int crackle(Px p, int c, int color, int seed, double strength) {
        double ridge = 1 - Math.abs(fbm(p.x * 0.12, p.y * 0.12, p.z * 0.12, seed, 3) - 0.5) * 22;
        if (ridge <= 0) return c;
        double flash = hash(p.frame, 0, 0, seed) > 0.45 ? 1 : 0.2;
        return glow(c, color, Math.min(1, ridge * flash * strength));
    }

    /** Light playing as through water. */
    static int caustics(Px p, int c, int color, double amount) {
        double a = Math.sin(p.x * 0.55 + Math.sin(p.y * 0.45 + p.phase) * 1.6 + p.z * 0.4);
        double b = Math.sin(p.y * 0.5 - Math.cos(p.x * 0.4 - p.phase) * 1.6 + p.z * 0.3);
        double k = Math.pow(Math.max(0, 1 - Math.abs(a + b)), 4);
        return glow(c, color, k * amount);
    }

    /** Overlapping scales (or feathers), with a shimmer that runs across them. */
    static int scales(Px p, int dark, int light, int edge, int seed) {
        int row = Math.floorDiv(p.v, 3);
        int off = Math.floorMod(row, 2) * 2;
        double lx = Math.floorMod(p.u + off, 4) + 0.5 - 2, ly = Math.floorMod(p.v, 3) + 0.5;
        int col = Math.floorDiv(p.u + off, 4);
        double r = Math.hypot(lx / 2.1, (ly + 0.6) / 3.4);
        int c = mix(light, dark, ly / 3);
        if (r > 0.95) c = edge;
        double shimmer = Math.pow(0.5 + 0.5 * Math.sin(p.phase + row * 0.6 + col * 0.9), 6);
        return glow(c, light, shimmer * 0.35 * hash(col, row, p.face.ordinal(), seed));
    }

    /** Rows of feathers with a bright shaft, banded in two colors. */
    static int feathers(Px p, int dark, int light, int accent, int seed) {
        int row = Math.floorDiv(p.v, 3);
        int c = scales(p, dark, row % 3 == 2 ? accent : light, scale(dark, 0.6), seed);
        int off = Math.floorMod(row, 2) * 2;
        if (Math.floorMod(p.u + off, 4) == 2) c = mix(c, light, 0.3);
        return c;
    }

    /** Bands of waves rolling sideways, crests in a color. */
    static int waves(Px p, int c, int color, double period, double amount) {
        double w = Math.sin((p.x + p.z) * 0.6 + p.phase) * 1.2;
        double band = Math.floorMod((int) Math.floor((p.y + w) * 4), (int) (period * 4)) / 4.0;
        if (band < 0.75) return mix(c, color, amount);
        if (band < 1.5) return mix(c, color, amount * 0.3);
        return c;
    }

    /** Bark: vertical grooves and grain, with moss in the hollows. */
    static int bark(Px p, int dark, int light, int seed) {
        double n = fbm((p.x + p.z) * 0.35, p.y * 0.07, 0, seed, 3);
        double groove = Math.abs(Math.sin((p.x + p.z) * 1.3 + n * 6));
        int c = mix(dark, light, 0.25 + 0.75 * n);
        if (groove < 0.25) c = scale(c, 0.55);
        double moss = fbm(p.x * 0.15, p.y * 0.15, p.z * 0.15, seed + 5, 3);
        if (moss > 0.58) c = mix(c, 0x4E7A2A, smooth(0.58, 0.72, moss));
        return c;
    }

    /** Leaves: cells of different greens with dark edges, an autumn leaf now and then. */
    static int leaves(Px p, int seed) {
        double[] v = voronoi(p.x * 0.42, p.y * 0.42, p.z * 0.42, seed);
        int[] greens = { 0x2E6A22, 0x3E8A2A, 0x5AA032, 0x2A5A1E, 0x4A7A26 };
        int col = v[2] > 0.95 ? 0xC08A2A : greens[(int) (v[2] * greens.length) % greens.length];
        int c = mix(scale(col, 0.7), col, 1 - Math.min(1, v[0]));
        if (v[1] - v[0] < 0.08) c = scale(c, 0.5);
        return glow(c, 0xC8FF8A, Math.pow(Math.max(0, Math.sin(p.phase + v[2] * 12)), 8) * 0.18);
    }

    /** Frost: bright lines along crystal edges and a few glints. */
    static int frostLines(Px p, int c, int seed) {
        double[] v = voronoi(p.x * 0.3, p.y * 0.3, p.z * 0.3, seed);
        double e = v[1] - v[0];
        if (e < 0.07) c = glow(c, 0xFFFFFF, (0.07 - e) / 0.07 * 0.75);
        return stars(p, c, 0.985, 0xFFFFFF, seed + 1);
    }

    /** A six-armed snowflake with side branches, pulsing. */
    static int snowflake(Px p, int c, double cx, double cy, double r, int color) {
        double dx = p.u + 0.5 - cx, dy = p.v + 0.5 - cy;
        double rr = Math.hypot(dx, dy);
        if (rr > r + 0.5) return c;
        double sector = Math.PI / 3;
        double a = Math.atan2(dy, dx);
        double a6 = ((a % sector) + sector) % sector - sector / 2;
        double along = rr * Math.cos(a6), perp = Math.abs(rr * Math.sin(a6));
        double pulse = 0.6 + 0.4 * p.pulse(0);
        boolean on = perp < 0.55 && rr < r;
        for (double b : new double[] { r * 0.45, r * 0.72 }) {
            if (along > b && Math.abs((along - b) - perp) < 0.5 && perp < r * 0.3) on = true;
        }
        if (rr < 1.2) return glow(c, 0xFFFFFF, 0.9);
        return on ? glow(c, color, 0.8 * pulse) : c;
    }

    /** Stars joined into constellations by faint lines. */
    static int constellations(Px p, int c, int color, int seed) {
        int cell = 7;
        int ci = Math.floorDiv(p.u, cell), cj = Math.floorDiv(p.v, cell);
        int fs = p.face.ordinal() + p.piece.ordinal() * 7;
        double x = p.u + 0.5, y = p.v + 0.5, best = 9;
        double line = 9;
        for (int i = ci - 1; i <= ci + 1; i++) {
            for (int j = cj - 1; j <= cj + 1; j++) {
                double sx = i * cell + 1 + hash(i, j, fs, seed) * (cell - 2);
                double sy = j * cell + 1 + hash(i, j, fs, seed + 1) * (cell - 2);
                best = Math.min(best, Math.hypot(x - sx, y - sy));
                if (hash(i, j, fs, seed + 2) > 0.45) {
                    double tx = (i + 1) * cell + 1 + hash(i + 1, j, fs, seed) * (cell - 2);
                    double ty = j * cell + 1 + hash(i + 1, j, fs, seed + 1) * (cell - 2);
                    line = Math.min(line, segDist(x, y, sx, sy, tx, ty));
                }
                if (hash(i, j, fs, seed + 3) > 0.6) {
                    double tx = i * cell + 1 + hash(i, j + 1, fs, seed) * (cell - 2);
                    double ty = (j + 1) * cell + 1 + hash(i, j + 1, fs, seed + 1) * (cell - 2);
                    line = Math.min(line, segDist(x, y, sx, sy, tx, ty));
                }
            }
        }
        if (best < 0.75) return glow(c, 0xFFFFFF, 0.6 + 0.4 * p.pulse(hash(ci, cj, fs, seed + 4)));
        if (line < 0.35) return glow(c, color, 0.3);
        return stars(p, c, 0.985, color, seed + 5);
    }

    /** A gear turning about (cx, cy); rot should advance one tooth per loop so the animation is seamless. */
    static int gear(Px p, int c, double cx, double cy, double r, int teeth, double rot, int color) {
        double dx = p.u + 0.5 - cx, dy = p.v + 0.5 - cy;
        double rr = Math.hypot(dx, dy), a = Math.atan2(dy, dx) - rot;
        double outer = r + (Math.cos(teeth * a) > 0.2 ? 1.1 : 0);
        if (rr > outer) return c;
        if (rr < r * 0.28) return mix(color, 0xFFFFFF, 0.3);
        if (rr < r * 0.72 && Math.abs(Math.sin(a * 2)) > 0.3) return c;
        double shade = 0.8 + 0.2 * Math.cos(a + rot - 0.8);
        return scale(color, rr > outer - 0.6 ? shade * 0.75 : shade);
    }

    /** Metal plates with seams and rivets, and a highlight sliding across. */
    static int plates(Px p, int base, int light, int seed) {
        int row = Math.floorDiv(p.v, 6);
        int lu = Math.floorMod(p.u + Math.floorMod(row, 2) * 4, 8), lv = Math.floorMod(p.v, 6);
        int plate = Math.floorDiv(p.u + Math.floorMod(row, 2) * 4, 8);
        int c = mix(base, light, 0.3 + 0.5 * hash(plate, row, p.face.ordinal(), seed));
        if (lu == 0 || lv == 0) c = scale(base, 0.55);
        else if ((lu == 1 || lu == 7) && (lv == 1 || lv == 5)) c = mix(light, 0xFFFFFF, 0.3);
        double sheen = Math.max(0, Math.sin((p.x + p.z) * 0.3 - p.y * 0.2 + p.phase));
        return glow(c, 0xFFF0C8, Math.pow(sheen, 10) * 0.35);
    }

    /** A spiral of light around a point, turning through the loop. */
    static int spiral(Px p, int c, double cx, double cy, double r, int color, int arms) {
        double dx = p.u + 0.5 - cx, dy = p.v + 0.5 - cy;
        double rr = Math.hypot(dx, dy);
        if (rr > r) return c;
        double k = Math.sin(arms * Math.atan2(dy, dx) + rr * 1.3 - p.phase);
        if (k > 0.6) c = glow(c, color, (k - 0.6) / 0.4 * (1 - rr / r) + 0.15);
        return rr < 1 ? glow(c, 0xFFFFFF, 0.7) : c;
    }

    /** Things drifting down (dir 1) or up (-1) through the loop: snow, petals, motes. */
    static int falling(Px p, int c, int color, int seed, int dir) {
        int cell = 8;
        int v = p.v - dir * p.frame;
        int ci = Math.floorDiv(p.u, cell), cj = Math.floorDiv(v, cell);
        int fs = p.face.ordinal() + p.piece.ordinal() * 7;
        double sx = ci * cell + 1 + hash(ci, cj, fs, seed) * (cell - 2);
        double sy = cj * cell + 1 + hash(ci, cj, fs, seed + 1) * (cell - 2);
        double d = Math.hypot(p.u + 0.5 - sx, v + 0.5 - sy);
        return d < 0.8 ? glow(c, color, 0.9) : d < 1.4 ? glow(c, color, 0.25) : c;
    }

    /** Embers rising off a hem. */
    static int risingSparks(Px p, int c, int seed) {
        if (p.t < 0.45) return c;
        int v = p.v + p.frame;
        if (hash(p.u, Math.floorDiv(v, 4), 1, seed) > 0.86 && Math.floorMod(v, 4) == 0) {
            return glow(c, 0xFFB347, 0.85 * smooth(0.45, 1.0, p.t));
        }
        return c;
    }

    /** Sparks caught in dark hair, glinting on and off. */
    static int sparks(Px p, int c, int seed) {
        double h = hash(p.u, p.v, p.face.ordinal() + p.piece.ordinal() * 7, seed);
        return h > 0.975 ? glow(c, 0xFF8A2A, 0.4 + 0.6 * p.pulse(h * 9)) : c;
    }

    /** Embers along a robe's side edges and hem. */
    static int emberTrim(Px p, int c) {
        if (!p.side()) return c;
        if (p.u <= 0 || p.u >= p.fw - 1 || p.v >= p.fh - 1) return glow(c, 0xFF5A1A, 0.6 + 0.3 * p.pulse(p.t * 2));
        return c;
    }

    /** A two-pronged staff, glowing. */
    static int bident(Px p, int c, double cx, double top, double bottom, int color) {
        double x = p.u + 0.5, y = p.v + 0.5;
        double fork = top + 5;
        boolean on = (Math.abs(x - (cx - 2)) < 0.6 || Math.abs(x - (cx + 2)) < 0.6) && y > top && y < fork
                || Math.abs(y - fork) < 0.6 && Math.abs(x - cx) < 2.6
                || Math.abs(x - cx) < 0.6 && y > fork && y < bottom;
        return on ? glow(c, color, 0.7 + 0.3 * p.pulse(y / 24)) : c;
    }

    /** A trident: three prongs with barbs, a crossbar and a shaft. */
    static int tridentMark(Px p, int c, double cx, double top, double bottom, int color) {
        double x = p.u + 0.5, y = p.v + 0.5;
        double bar = top + 5;
        boolean on = Math.abs(x - cx) < 0.6 && y > top - 1 && y < bottom
                || (Math.abs(x - (cx - 3)) < 0.6 || Math.abs(x - (cx + 3)) < 0.6) && y > top && y < bar
                || Math.abs(y - bar) < 0.6 && Math.abs(x - cx) < 3.6
                || Math.abs(y - (top + 1)) < 0.5 && (Math.abs(x - (cx - 3.6)) < 0.5 || Math.abs(x - (cx + 3.6)) < 0.5);
        return on ? filigree(p, color) : c;
    }

    /** Small runes stacked down the front of a robe, each glowing in turn. */
    static int runeColumn(Px p, int c, int left, int top, int count, int color, int seed) {
        int lx = p.u - left + 1, ly = p.v - top;
        if (lx < 0 || lx >= 5 || ly < 0 || ly >= count * 7) return c;
        int i = ly / 7;
        int id = (int) (hash(i, 0, 0, seed) * 32);
        if (rune(lx, ly % 7, id)) return glow(c, color, 0.5 + 0.45 * p.pulse(i / (double) count));
        return c;
    }

    /** A rune-like glyph on a 5x7 grid: a stave and branches chosen by id's bits. */
    static boolean rune(int x, int y, int id) {
        if (y < 1 || y > 5) return false;
        if (x == 2) return true;
        boolean on = false;
        if ((id & 1) != 0) on |= y - 1 == x - 2 && x >= 2; // upper right
        if ((id & 2) != 0) on |= y - 1 == 2 - x && x <= 2; // upper left
        if ((id & 4) != 0) on |= y - 3 == x - 2 && x >= 2; // lower right
        if ((id & 8) != 0) on |= y - 3 == 2 - x && x <= 2; // lower left
        if ((id & 16) != 0 && (id & 5) == 0) on |= y == 3 && x == 3;
        return on;
    }

    /** Storm clouds: roiling grey noise drifting through the loop. */
    static int clouds(Px p, int dark, int light, int seed) {
        double q = fbm(p.x * 0.1 + p.dx(0.6), p.y * 0.1 + p.dy(0.6), p.z * 0.1, seed, 3);
        double n = fbm(p.x * 0.16 + q * 2, p.y * 0.16 + q * 2, p.z * 0.16, seed + 1, 4);
        return mix(dark, light, smooth(0.3, 0.8, n));
    }

    /** Slanting rain streaks falling through the loop. */
    static int rain(Px p, int c) {
        int k = Math.floorMod(p.u * 3 - p.v - p.frame * 3, 11);
        return k == 0 && hash(p.u, 0, 0, 77) > 0.4 ? mix(c, 0x9AB8D8, 0.45) : c;
    }

    /** Flowers with five petals and a gold heart, scattered on a grid of cells. */
    static int flowers(Px p, int c, int seed, int cell, double density) {
        double u = p.side() ? p.u + 0.5 : p.x, v = p.side() ? p.v + 0.5 : p.z;
        int ci = (int) Math.floor(u / cell), cj = (int) Math.floor(v / cell);
        int fs = p.face.ordinal() + p.piece.ordinal() * 7;
        if (hash(ci, cj, fs, seed) > density) return c;
        double cx = ci * cell + 1.5 + hash(ci, cj, fs, seed + 1) * (cell - 3);
        double cy = cj * cell + 1.5 + hash(ci, cj, fs, seed + 2) * (cell - 3);
        double dx = u - cx, dy = v - cy, r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
        double petal = cell * 0.36 * (0.55 + 0.45 * Math.abs(Math.cos(2.5 * a + hash(ci, cj, fs, seed + 3) * 3)));
        int[] colors = { 0xFF8AB0, 0xFFFFFF, 0xF4C8E0, 0xE8507A, 0xFFE08A };
        int col = colors[(int) (hash(ci, cj, fs, seed + 4) * colors.length) % colors.length];
        if (r < petal * 0.35) return 0xF6D03D;
        if (r < petal) return mix(mix(col, 0xFFFFFF, 0.3), col, r / petal);
        return c;
    }

    static int hsb(double h, double s, double b) {
        double hh = h - Math.floor(h);
        return java.awt.Color.HSBtoRGB((float) hh, (float) Math.max(0, Math.min(1, s)),
                (float) Math.max(0, Math.min(1, b))) & 0xFFFFFF;
    }

    /** A color ramp through evenly spaced stops, t from 0 to 1. */
    static int ramp(double t, int... stops) {
        t = Math.max(0, Math.min(1, t)) * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) Math.floor(t));
        return mix(stops[i], stops[i + 1], t - i);
    }

    static double segDist(double px, double py, double ax, double ay, double bx, double by) {
        double vx = bx - ax, vy = by - ay;
        double len = vx * vx + vy * vy;
        double k = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * vx + (py - ay) * vy) / len));
        return Math.hypot(px - (ax + vx * k), py - (ay + vy * k));
    }

    private MythicTextures() {
    }
}
