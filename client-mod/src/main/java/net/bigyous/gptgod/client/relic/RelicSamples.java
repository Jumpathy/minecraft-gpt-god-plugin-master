package net.bigyous.gptgod.client.relic;

import java.util.List;

/**
 * Relics painted here in code, for /godfx relics and the screenshot test: what the plugin would send,
 * without a server. Pure. Between them they cover a plain 16x16 item, an animated strip, a soft
 * see-through haze, a sprite that is not square, and (the last three, which carry a depth map) a real
 * form: a sword with a pale blade and a see-through film round it, the same sword awakened, and a shield
 * that rises in steps to its boss. What the samples paint on the "lit" layer is drawn like the rest.
 */
public final class RelicSamples {
    private RelicSamples() {
    }

    public static List<RelicArt> all() {
        return List.of(blade(), orb(), staff(), lightblade(false), lightblade(true), shield());
    }

    /** One frame strip being painted: the picture, the "lit" layer over it, and how thick each texel is. */
    private static final class Canvas {
        final int w, h, frames;
        final int[] base, lit;
        /** Null until something is given a thickness: then the relic has a depth map, and what was not is a film. */
        byte[] depth;

        Canvas(int w, int h, int frames) {
            this.w = w;
            this.h = h;
            this.frames = frames;
            base = new int[w * h * frames];
            lit = new int[w * h * frames];
        }

        void dot(int[] layer, int frame, int x, int y, int argb) {
            if (x >= 0 && y >= 0 && x < w && y < h) layer[(frame * h + y) * w + x] = argb;
        }

        /** How thick the texel x, y is in every frame, in texels. */
        void deep(int x, int y, int texels) {
            if (depth == null) depth = new byte[w * h];
            if (x >= 0 && y >= 0 && x < w && y < h) depth[y * w + x] = (byte) texels;
        }

        RelicArt art(String id, String name, RelicArt.Kind kind, int ticks) {
            return RelicArt.of(id, 1, name, kind, w, h, frames, ticks, base, lit, depth);
        }
    }

    /** A 16x16 sword lying on the diagonal like the game's own, with bright runes down the blade. */
    static RelicArt blade() {
        Canvas c = new Canvas(16, 16, 1);
        for (int i = 0; i < 16; i++) {
            int x = i, y = 15 - i;
            if (i < 4) { // grip, with a pommel at the very end
                c.dot(c.base, 0, x, y, i == 0 ? 0xFFE8C34A : 0xFF6B4423);
            } else if (i == 4) { // the guard, across the blade
                for (int k = -2; k <= 2; k++) c.dot(c.base, 0, x + k, y + k, Math.abs(k) == 2 ? 0xFFB8922E : 0xFFE8C34A);
            } else { // the blade: a bright middle between two darker edges, coming to a point
                c.dot(c.base, 0, x, y, 0xFFE4ECF2);
                if (i < 15) {
                    c.dot(c.base, 0, x - 1, y, 0xFF8FA3B5);
                    c.dot(c.base, 0, x, y + 1, 0xFFB9C7D4);
                }
                if (i >= 6 && i <= 12 && i % 2 == 0) c.dot(c.lit, 0, x, y, 0xFF62F5FF);
            }
        }
        return c.art("demo_blade", "Runed Blade", RelicArt.Kind.BLADE, 1);
    }

    /** A 32x32 orb, six frames: a dark shell, a core that swells and fades, a soft halo and a spark going round. */
    static RelicArt orb() {
        Canvas c = new Canvas(32, 32, 6);
        for (int f = 0; f < c.frames; f++) {
            double beat = 0.5 + 0.5 * Math.sin(f * Math.PI * 2 / c.frames);
            double core = 4.5 + 2.5 * beat;
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    double dx = x - 15.5, dy = y - 15.5, d = Math.sqrt(dx * dx + dy * dy);
                    if (d <= 10) { // the shell, lighter towards the upper left
                        double shade = 0.55 + 0.45 * Math.max(0, (-dx - dy) / 14.0);
                        c.dot(c.base, f, x, y, 0xFF000000 | (int) (70 * shade) << 16 | (int) (40 * shade) << 8 | (int) (140 * shade));
                    }
                    if (d <= core) {
                        c.dot(c.lit, f, x, y, d <= core - 2 ? 0xFFFFF6D0 : 0xFFFFC14A);
                    } else if (d > 10 && d <= 14.5) { // the halo thins out to nothing
                        int alpha = (int) ((0.25 + 0.3 * beat) * 255 * (14.5 - d) / 4.5);
                        if (alpha > 0) c.dot(c.lit, f, x, y, alpha << 24 | 0xFFB040);
                    }
                }
            }
            double angle = f * Math.PI * 2 / c.frames;
            c.dot(c.lit, f, (int) Math.round(15.5 + Math.cos(angle) * 12.5), (int) Math.round(15.5 + Math.sin(angle) * 12.5), 0xFFFFFFFF);
        }
        return c.art("demo_orb", "Ember Spirit", RelicArt.Kind.SPIRIT, 3);
    }

    /** A 16x32 staff, four frames: a wooden shaft and a crystal at its head whose bright heart breathes. */
    static RelicArt staff() {
        Canvas c = new Canvas(16, 32, 4);
        for (int f = 0; f < c.frames; f++) {
            for (int y = 9; y < 32; y++) { // the shaft leans from the lower left to the upper right
                int x = 2 + (31 - y) * 9 / 22;
                c.dot(c.base, f, x, y, 0xFF8A5A2B);
                c.dot(c.base, f, x + 1, y, 0xFF5E3B1A);
            }
            int[] brightAt = { 0xFF7FE8FF, 0xFFB5F4FF, 0xFFFFFFFF, 0xFFB5F4FF };
            for (int y = 1; y <= 9; y++) { // the crystal, a diamond; the brightest part moves through the frames
                int half = 4 - Math.abs(y - 5);
                for (int x = 11 - half; x <= 11 + half; x++) {
                    boolean rim = Math.abs(x - 11) == half;
                    c.dot(c.base, f, x, y, rim ? 0xFF2C6E8A : 0xFF49B6D6);
                    if (!rim && Math.abs(x - 11) + Math.abs(y - 5) <= 1 + f % 3) c.dot(c.lit, f, x, y, brightAt[f]);
                }
            }
        }
        return c.art("demo_staff", "Tidecaller Staff", RelicArt.Kind.STAFF, 5);
    }

    /**
     * A 16x16 sword with a form: a stout pommel and guard (3 texels thick), a slim grip, and a pale blade
     * (2 thick) inside a see-through film that breathes over four frames. Awake, the blade turns gold and
     * its film reaches further. The two are forms of one relic: the second is named after the first with
     * __on.
     */
    static RelicArt lightblade(boolean awake) {
        Canvas c = new Canvas(16, 16, 4);
        int core = awake ? 0xFFFFF8D8 : 0xFFE6FBFF, edge = awake ? 0xFFFFC53D : 0xFF5CD6FF, haze = awake ? 0xFFB52E : 0x3FC4FF;
        for (int f = 0; f < c.frames; f++) {
            double breath = 0.75 + 0.25 * Math.sin(f * Math.PI * 2 / c.frames);
            for (int i = 0; i < 16; i++) {
                int x = i, y = 15 - i;
                if (i == 0) {
                    c.dot(c.base, f, x, y, 0xFFE8C34A);
                    c.deep(x, y, 3);
                } else if (i < 4) {
                    c.dot(c.base, f, x, y, 0xFF6B4423);
                    c.deep(x, y, 1);
                } else if (i == 4) {
                    for (int k = -2; k <= 2; k++) {
                        c.dot(c.base, f, x + k, y + k, Math.abs(k) == 2 ? 0xFFB8922E : 0xFFE8C34A);
                        c.deep(x + k, y + k, 3);
                    }
                } else {
                    int[][] texels = i < 15 ? new int[][] { { x, y, core }, { x - 1, y, edge }, { x, y + 1, edge } } : new int[][] { { x, y, core } };
                    for (int[] t : texels) {
                        c.dot(c.base, f, t[0], t[1], t[2]);
                        c.dot(c.lit, f, t[0], t[1], t[2]);
                        c.deep(t[0], t[1], 2);
                    }
                }
            }
            // The film: empty texels within reach of the blade, fainter with distance. They have no thickness.
            int reach = awake ? 3 : 2;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    if ((c.base[(f * 16 + y) * 16 + x] >>> 24) != 0) continue;
                    double nearest = 99;
                    for (int i = 5; i < 16; i++) nearest = Math.min(nearest, Math.hypot(x - i, y - (15 - i)));
                    if (nearest > reach + 0.5) continue;
                    int alpha = (int) (breath * 190 * (1 - (nearest - 1) / (reach + 0.5)));
                    c.dot(c.lit, f, x, y, Math.max(0, Math.min(255, alpha)) << 24 | haze);
                }
            }
        }
        return c.art(awake ? "demo_lightblade__on" : "demo_lightblade", awake ? "Dawnfire, Awakened" : "Dawnfire", RelicArt.Kind.BLADE, 4);
    }

    /** A 16x16 round shield rising in steps from a thin rim to a pale boss six texels thick. */
    static RelicArt shield() {
        Canvas c = new Canvas(16, 16, 1);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double d = Math.hypot(x - 7.5, y - 7.5);
                if (d > 7.6) continue;
                int step = d < 2 ? 6 : d < 4.2 ? 4 : d < 6.2 ? 2 : 1;
                int colour = switch (step) {
                    case 6 -> 0xFFFFE9A8;
                    case 4 -> 0xFF9AA8B8;
                    case 2 -> (x + y) % 2 == 0 ? 0xFF7C2D2D : 0xFF8F3838;
                    default -> 0xFFD9B13B;
                };
                c.dot(c.base, 0, x, y, colour);
                if (step == 6) c.dot(c.lit, 0, x, y, colour);
                c.deep(x, y, step);
            }
        }
        return c.art("demo_shield", "Aegis of Dawn", RelicArt.Kind.TRINKET, 1);
    }
}
