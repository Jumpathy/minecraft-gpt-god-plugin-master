package net.bigyous.gptgodmc.god.art;

import java.util.Locale;

import net.bigyous.gptgodmc.god.art.Sprite.Ink;

/**
 * The surface of a material as Minecraft paints a block: a 16 x 16 tile in the few flat tones of the
 * material's ramp, that repeats seamlessly. A relic's true shape ({@link RelicShape}) wears one on every part,
 * laid on at about a texel to a sixteenth of a block, so a shaft shows wood grain along its length, a blade a
 * metal's bands and glint, a gem its facets: the look of a made Minecraft thing, not a smooth plain model and
 * not the relic's picture stretched over it. u runs across a part, v along it. Deterministic. Pure.
 */
public final class RelicTiles {
    private RelicTiles() {
    }

    public static final int SIZE = 16;

    /** The name a material's tile goes by in a shape ("m_" and its legend words), as scenes name textures. */
    public static String name(String material) {
        String k = material.trim().toLowerCase(Locale.ROOT).replace("#", "").replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return "m_" + (k.length() > 40 ? k.substring(0, 40) : k);
    }

    /** The tile for a material written as a legend entry ("darkwood", "gem #7a5cff", "#c9ced6"): ARGB, row by row. */
    public static int[] paint(String material) throws Sprite.Flaw {
        Ink ink = Sprite.ink("t", material);
        return paint(ink);
    }

    static int[] paint(Ink ink) {
        int[] ramp = ink.ramp();
        int[] out = new int[SIZE * SIZE];
        for (int v = 0; v < SIZE; v++) {
            for (int u = 0; u < SIZE; u++) {
                int tone = ink.tone() >= 0 ? ink.tone() : tone(ink.pigment().family, u, v);
                out[v * SIZE + u] = 0xFF000000 | ramp[Math.max(0, Math.min(4, tone))];
            }
        }
        return out;
    }

    private static int tone(Pigment.Family family, int u, int v) {
        double n = SpritePainter.hash(u, v, 31);
        return switch (family) {
            // Metal: brushed, short lighter and darker streaks along its length, a glint now and then.
            case METAL -> {
                double streak = SpritePainter.hash(u, (v + u * 5) / 4, 23);
                int t = streak < 0.25 ? 1 : streak > 0.7 ? 3 : 2;
                if (n < 0.08) t = Math.max(1, t - 1);
                if (n > 0.965) t = 4;
                yield t;
            }
            // Wood and leather: grain that runs along the part, each line its own tone, a knot here and there.
            case WOOD -> {
                double line = SpritePainter.hash(u, (v + u * 3) / 4, 17);
                int t = line < 0.3 ? 1 : line > 0.78 ? 3 : 2;
                if (Math.hypot(u - 10.5, v - 5.5) < 1.6) t = 1;
                if (n < 0.1) t += 1;
                else if (n > 0.92) t -= 1;
                yield t;
            }
            // Cloth: a fine weave.
            case CLOTH -> ((u + v) & 1) == 0 ? (n < 0.2 ? 3 : 2) : (n < 0.15 ? 1 : 2);
            // A cut stone: facets meeting on the diagonals.
            case GEM -> {
                int a = Math.abs(u - 7) + Math.abs(v - 7);
                int t = a < 3 ? 4 : u < v ? (u + v < 15 ? 3 : 1) : (u + v < 15 ? 2 : 1);
                if (a == 7) t = 1;
                yield t;
            }
            // Light and fire: bright, breathing unevenly.
            case GLOW, FLAME -> n < 0.3 ? 4 : 3;
            // The night: dark, with the odd star.
            case VOID -> n > 0.95 ? 4 : n < 0.3 ? 0 : 1;
            // Skin and fur: speckled.
            case HIDE -> n < 0.25 ? 1 : n > 0.85 ? 3 : 2;
            // Stone, bone, marble, plain paint: soft clumps of tone, as Minecraft's stones are.
            default -> {
                double clump = SpritePainter.hash(u / 3, v / 3, 7);
                int t = clump < 0.3 ? 1 : clump > 0.75 ? 3 : 2;
                if (n < 0.1) t = Math.max(0, t - 1);
                yield t;
            }
        };
    }
}
