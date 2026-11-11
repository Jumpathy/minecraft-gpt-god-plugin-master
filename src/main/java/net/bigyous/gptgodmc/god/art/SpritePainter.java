package net.bigyous.gptgodmc.god.art;

import java.util.ArrayDeque;

import net.bigyous.gptgodmc.god.art.Pigment.Family;
import net.bigyous.gptgodmc.god.art.Sprite.Ink;
import net.bigyous.gptgodmc.god.art.Sprite.Outline;

/**
 * Turns a {@link Sprite} (a map of materials) into finished, animated pixels. This is the craft, done
 * the same careful way every time:
 * <ul>
 * <li>one light for the whole sprite: every shape is lighter on the side facing it and darker on the far
 * side, in the few flat tones of its material's ramp (never a smooth airbrushed gradient);</li>
 * <li>the edge of the silhouette that faces away from the light is a tone darker, so the thing reads on
 * any background, and where two materials meet the far one is shaded so they do not melt together;</li>
 * <li>each family of material has its own surface: metal takes a hard highlight and a glint that sweeps
 * across now and then, a gem has a lit facet, a dark facet and a sparkle, wood has grain along its
 * length, cloth a weave, flame is hot in its heart and flickers, the void holds stars that twinkle;</li>
 * <li>what shines by itself (glow, flame, soul-fire, sparkles) is also written to a second layer, so it
 * can be drawn at full brightness, and may spill a soft halo into the empty texels round it.</li>
 * </ul>
 * Deterministic: the same sprite always gives the same pixels. Pure.
 */
public final class SpritePainter {
    /** Animation frames of every sprite, and game ticks per frame. */
    public static final int FRAMES = 8;
    public static final int FRAME_TICKS = 3;

    /** The four walls of a texel given thickness, in the order {@link Art#walls} holds them. */
    public static final int UP = 0, DOWN = 1, WEST = 2, EAST = 3;

    /**
     * The painted sprite: ARGB frames (row by row); the same frames holding only the texels that shine by
     * themselves (all else clear), or null when nothing does; how thick each texel is front to back, in
     * texels (0 for an empty texel and for a film of light with no thickness); and the colour of each
     * texel's walls when it is given thickness ([frame][UP, DOWN, WEST, EAST][texel]): its own material as
     * the face is painted, a tone lighter on top where the light catches it. Null walls mean the face's own
     * colour.
     */
    public record Art(int w, int h, int[][] frames, int[][] lit, byte[] depth, int[][][] walls) {
        public Art(int w, int h, int[][] frames, int[][] lit, byte[] depth) {
            this(w, h, frames, lit, depth, null);
        }

        public int at(int frame, int x, int y) {
            return frames[Math.floorMod(frame, frames.length)][y * w + x];
        }

        /** The colour of one wall of a texel (UP, DOWN, WEST or EAST). */
        public int wall(int frame, int side, int x, int y) {
            return walls == null ? at(frame, x, y) : walls[Math.floorMod(frame, walls.length)][side][y * w + x];
        }

        public int thick(int x, int y) {
            return x < 0 || y < 0 || x >= w || y >= h ? 0 : depth[y * w + x];
        }

        /** True when the texel is drawn in the first frame (solid or a film). */
        public boolean drawn(int x, int y) {
            return x >= 0 && y >= 0 && x < w && y < h && (frames[0][y * w + x] >>> 24) != 0;
        }

        /** True when the texel shines by itself in any frame. */
        public boolean shines(int x, int y) {
            if (lit == null) return false;
            for (int[] frame : lit) {
                if ((frame[y * w + x] >>> 24) != 0) return true;
            }
            return false;
        }

        /**
         * The same art centered on a square canvas (unchanged when it is square already). Minecraft
         * stretches an item's texture over a square, so what is published is always square.
         */
        public Art squared() {
            if (w == h) return this;
            int side = Math.max(w, h), ox = (side - w) / 2, oy = (side - h) / 2;
            byte[] deep = new byte[side * side];
            for (int y = 0; y < h; y++) System.arraycopy(depth, y * w, deep, (y + oy) * side + ox, w);
            int[][][] sides = null;
            if (walls != null) {
                sides = new int[walls.length][][];
                for (int f = 0; f < walls.length; f++) sides[f] = pad(walls[f], side, ox, oy);
            }
            return new Art(side, side, pad(frames, side, ox, oy), lit == null ? null : pad(lit, side, ox, oy), deep,
                    sides);
        }

        private int[][] pad(int[][] from, int side, int ox, int oy) {
            int[][] out = new int[from.length][side * side];
            for (int f = 0; f < from.length; f++) {
                for (int y = 0; y < h; y++) System.arraycopy(from[f], y * w, out[f], (y + oy) * side + ox, w);
            }
            return out;
        }
    }

    private SpritePainter() {
    }

    /**
     * @param halo let lights spill a soft halo into empty texels (right for a spirit seen flat in the
     *             world; wrong for a held item, whose every texel is given thickness)
     */
    public static Art paint(Sprite s, boolean halo) {
        Field f = new Field(s);
        int[][] frames = new int[FRAMES][], lit = new int[FRAMES][];
        int[][][] walls = new int[FRAMES][][];
        int[] turn = WALL_TONES[s.light().ordinal()];
        boolean any = false;
        for (int frame = 0; frame < FRAMES; frame++) {
            int[] px = new int[s.w() * s.h()], glow = new int[s.w() * s.h()];
            int[][] wall = new int[4][s.w() * s.h()];
            for (int y = 0; y < s.h(); y++) {
                for (int x = 0; x < s.w(); x++) {
                    Ink ink = s.at(x, y);
                    if (ink == null) continue;
                    double tone = tone(f, ink, x, y, frame);
                    boolean shines = ink.pigment().emissive() || (ink.pigment().family == Family.VOID && tone >= 3.5);
                    char accent = s.accent(x, y);
                    if (accent == '+') tone += 1;
                    else if (accent == '-') tone -= 1;
                    else if (accent == '*') {
                        tone = Math.floorMod(frame + (int) (hash(x, y, 3) * FRAMES), FRAMES) < 5 ? 4 : 3;
                        shines = true;
                    }
                    if (ink.pigment().family == Family.GEM && tone >= 3.6) shines = true;
                    int step = (int) Math.max(0, Math.min(4, Math.round(tone)));
                    int rgb = ink.ramp()[step];
                    px[y * s.w() + x] = 0xFF000000 | rgb;
                    for (int side = 0; side < 4; side++) {
                        wall[side][y * s.w() + x] = 0xFF000000 | ink.ramp()[Math.max(0, Math.min(4, step + turn[side]))];
                    }
                    if (shines) {
                        glow[y * s.w() + x] = 0xFF000000 | rgb;
                        any = true;
                    }
                }
            }
            if (s.outline() == Outline.DARK) outline(s, px);
            if (halo) halo(s, px, glow, frame);
            // An outline or a halo has no material of its own: its walls are its own colour.
            for (int i = 0; i < px.length; i++) {
                if ((px[i] >>> 24) == 0) continue;
                for (int side = 0; side < 4; side++) {
                    if (wall[side][i] == 0) wall[side][i] = px[i];
                }
            }
            frames[frame] = px;
            lit[frame] = glow;
            walls[frame] = wall;
        }
        return new Art(s.w(), s.h(), frames, any ? lit : null, depth(s, f, frames[0]), walls);
    }

    /**
     * How many tones each wall is from the face, per {@link Sprite.Light} (UP, DOWN, WEST, EAST): the top
     * catches the light. The underside and the sides keep the face's tone: whatever shows them (the game,
     * the client mod) already shades a face by the way it points, and a tone darker on top of that went
     * nearly black at night (seen in a real client).
     */
    private static final int[][] WALL_TONES = { { 1, 0, 0, 0 }, { 1, 0, 0, 0 }, { 1, 0, 0, 0 } };

    /**
     * The letters the painter keeps at the canvas's usual thickness although the legend asks for more: a
     * stroke a few texels across that runs on the diagonal. Given thickness, every row of it would stand
     * out from the next like a stair.
     */
    public static java.util.Set<Character> flattened(Sprite s) {
        Field f = new Field(s);
        java.util.Set<Character> out = new java.util.TreeSet<>();
        for (int y = 0; y < s.h(); y++) {
            for (int x = 0; x < s.w(); x++) {
                Ink ink = s.at(x, y);
                if (ink == null || !f.stroke(f.patch[y][x])) continue;
                if (ink.thick() > s.usualThick() || (ink.round() && ink.thick() > 1)) out.add(s.cells()[y][x]);
            }
        }
        return out;
    }

    /**
     * How thick each texel is. A material is as thick as its legend entry says (else the canvas's usual
     * thickness); a rounded one is thickest along the middle of its patch and thins to its edges like the
     * cross-section of a rod or a ball. A narrow stroke on the diagonal stays at the usual thickness
     * whatever it asks ({@link #flattened}). A dark outline is as thick as the usual; a halo is a film.
     */
    private static byte[] depth(Sprite s, Field f, int[] first) {
        byte[] out = new byte[s.w() * s.h()];
        for (int y = 0; y < s.h(); y++) {
            for (int x = 0; x < s.w(); x++) {
                Ink ink = s.at(x, y);
                if (ink == null) {
                    int alpha = first[y * s.w() + x] >>> 24;
                    out[y * s.w() + x] = (byte) (alpha == 255 ? s.usualThick() : 0);
                    continue;
                }
                int thick = ink.thick() >= 0 ? ink.thick() : s.usualThick();
                if (f.stroke(f.patch[y][x]) && thick > 0) {
                    out[y * s.w() + x] = (byte) Math.min(thick, s.usualThick());
                    continue;
                }
                if (ink.round() && thick > 1) {
                    int p = f.patch[y][x];
                    double r = f.deepest[p] - 0.5, off = f.deepest[p] - f.inner[y][x];
                    thick = Math.max(1, (int) Math.round(thick * Math.sqrt(Math.max(0, r * r - off * off)) / r));
                }
                out[y * s.w() + x] = (byte) thick;
            }
        }
        return out;
    }

    // ---- What the painter knows about the shape ---------------------------------------------------------

    /** The sprite measured once: how deep each texel lies inside the shape, and the patch of material it belongs to. */
    private static final class Field {
        final Sprite s;
        /** Texels to the nearest empty one (1 on the silhouette's edge); 0 for empty. */
        final int[][] depth;
        /** Which connected patch of one letter each texel belongs to. */
        final int[][] patch;
        /** Texels to the edge of its own patch (1 on the patch's edge), and the deepest any texel of a patch lies. */
        final int[][] inner;
        int[] deepest;
        /** Per patch: where it starts and ends along the light, its middle, its size and how it lies. */
        double[] near, far, cx, cy, top, bottom;
        int[] size, grain;
        /** Per patch: whether it runs on the diagonal and is at least six texels long. */
        boolean[] diagonal;

        Field(Sprite s) {
            this.s = s;
            int w = s.w(), h = s.h();
            depth = new int[h][w];
            patch = new int[h][w];
            // Depth: a flood from every empty texel (and from beyond the canvas).
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (s.at(x, y) == null) continue;
                    boolean edge = s.at(x - 1, y) == null || s.at(x + 1, y) == null || s.at(x, y - 1) == null
                            || s.at(x, y + 1) == null;
                    if (edge) {
                        depth[y][x] = 1;
                        queue.add(new int[] { x, y });
                    }
                }
            }
            int[][] step = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
            while (!queue.isEmpty()) {
                int[] at = queue.poll();
                for (int[] d : step) {
                    int x = at[0] + d[0], y = at[1] + d[1];
                    if (s.at(x, y) == null || depth[y][x] != 0) continue;
                    depth[y][x] = depth[at[1]][at[0]] + 1;
                    queue.add(new int[] { x, y });
                }
            }
            // Patches: texels of one letter that touch.
            for (int[] row : patch) java.util.Arrays.fill(row, -1);
            int count = 0;
            java.util.List<double[]> stats = new java.util.ArrayList<>();
            for (int y0 = 0; y0 < h; y0++) {
                for (int x0 = 0; x0 < w; x0++) {
                    if (s.at(x0, y0) == null || patch[y0][x0] >= 0) continue;
                    char letter = s.cells()[y0][x0];
                    // near, far, sum x, sum y, n, sum xy, sum xx, sum yy, top, bottom
                    double[] st = { 1e9, -1e9, 0, 0, 0, 0, 0, 0, 1e9, -1e9 };
                    queue.add(new int[] { x0, y0 });
                    patch[y0][x0] = count;
                    while (!queue.isEmpty()) {
                        int[] at = queue.poll();
                        int x = at[0], y = at[1];
                        double along = x * s.light().dx + y * s.light().dy;
                        st[0] = Math.min(st[0], along);
                        st[1] = Math.max(st[1], along);
                        st[2] += x;
                        st[3] += y;
                        st[4]++;
                        st[5] += x * y;
                        st[6] += x * x;
                        st[7] += y * y;
                        st[8] = Math.min(st[8], y);
                        st[9] = Math.max(st[9], y);
                        for (int[] d : step) {
                            int nx = x + d[0], ny = y + d[1];
                            if (s.at(nx, ny) == null || patch[ny][nx] >= 0 || s.cells()[ny][nx] != letter) continue;
                            patch[ny][nx] = count;
                            queue.add(new int[] { nx, ny });
                        }
                    }
                    stats.add(st);
                    count++;
                }
            }
            near = new double[count];
            far = new double[count];
            cx = new double[count];
            cy = new double[count];
            top = new double[count];
            bottom = new double[count];
            size = new int[count];
            grain = new int[count];
            diagonal = new boolean[count];
            for (int i = 0; i < count; i++) {
                double[] st = stats.get(i);
                double n = st[4];
                near[i] = st[0];
                far[i] = st[1];
                cx[i] = st[2] / n;
                cy[i] = st[3] / n;
                top[i] = st[8];
                bottom[i] = st[9];
                size[i] = (int) n;
                // How the patch lies, for grain that runs along its length: 0 upright, 1 flat, 2 falling
                // to the right, 3 rising to the right.
                double vx = st[6] / n - cx[i] * cx[i], vy = st[7] / n - cy[i] * cy[i];
                double cov = st[5] / n - cx[i] * cy[i];
                if (Math.abs(cov) > 0.55 * Math.max(vx, vy)) grain[i] = cov > 0 ? 2 : 3;
                else grain[i] = vx > vy ? 1 : 0;
                // A stroke is long as well as slanted: a short slanted blob (a pommel) steps once or twice at most.
                double spread = (vx + vy) / 2 + Math.sqrt((vx - vy) * (vx - vy) / 4 + cov * cov);
                diagonal[i] = n >= 4 && Math.abs(cov) > 0.45 * Math.max(vx, vy) && Math.sqrt(12 * spread) >= 6;
            }
            // How deep each texel lies inside its own patch, for rounding a material's thickness.
            inner = new int[h][w];
            deepest = new int[count];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (patch[y][x] < 0) continue;
                    boolean rim = false;
                    for (int[] d : step) {
                        int nx = x + d[0], ny = y + d[1];
                        rim |= nx < 0 || ny < 0 || nx >= w || ny >= h || patch[ny][nx] != patch[y][x];
                    }
                    if (rim) {
                        inner[y][x] = 1;
                        queue.add(new int[] { x, y });
                    }
                }
            }
            while (!queue.isEmpty()) {
                int[] at = queue.poll();
                int p = patch[at[1]][at[0]];
                deepest[p] = Math.max(deepest[p], inner[at[1]][at[0]]);
                for (int[] d : step) {
                    int x = at[0] + d[0], y = at[1] + d[1];
                    if (x < 0 || y < 0 || x >= w || y >= h || patch[y][x] != p || inner[y][x] != 0) continue;
                    inner[y][x] = inner[at[1]][at[0]] + 1;
                    queue.add(new int[] { x, y });
                }
            }
        }

        /** True for a narrow stroke (no texel more than two from its edge) that runs on the diagonal. */
        boolean stroke(int p) {
            return diagonal[p] && deepest[p] <= 2;
        }

        /** 0 on the side of its patch nearest the light, 1 on the far side; 0.5 across a patch one texel thick. */
        double across(int x, int y) {
            int p = patch[y][x];
            double range = far[p] - near[p];
            return range < 1.5 ? 0.5 : (x * s.light().dx + y * s.light().dy - near[p]) / range;
        }

        /** What lies one texel toward the light (toward = true) or away: 0 the same patch, 1 another material, 2 nothing. */
        int beside(int x, int y, boolean toward) {
            int sign = toward ? -1 : 1, worst = 0;
            int[][] ways = s.light().dx == 0 ? new int[][] { { 0, 1 } }
                    : new int[][] { { s.light().dx, 0 }, { 0, s.light().dy } };
            for (int[] d : ways) {
                int nx = x + sign * d[0], ny = y + sign * d[1];
                if (s.at(nx, ny) == null) worst = 2;
                else if (patch[ny][nx] != patch[y][x]) worst = Math.max(worst, 1);
            }
            return worst;
        }
    }

    // ---- Tone -----------------------------------------------------------------------------------------

    /** The tone (0 deep .. 4 bright, before rounding) of one texel in one frame. */
    private static double tone(Field f, Ink ink, int x, int y, int frame) {
        Family family = ink.pigment().family;
        double phase = 2 * Math.PI * frame / FRAMES;
        if (ink.tone() >= 0) {
            // The artist chose the tone; what shines still breathes a little.
            return ink.pigment().emissive() ? ink.tone() + 0.45 * Math.sin(phase + (x + y) * 0.5) : ink.tone();
        }
        int p = f.patch[y][x];
        double across = f.across(x, y);
        int toward = f.beside(x, y, true), away = f.beside(x, y, false);
        switch (family) {
            case GLOW: {
                // Light itself: brightest in its heart, a little softer at its rim, slowly breathing.
                // (A slim rod or small orb of light, two texels deep, is all heart along its middle: a white-hot
                // core inside a coloured edge.)
                double heart = f.size[p] <= 4 ? 1
                        : f.deepest[p] == 2 ? f.inner[y][x] - 1 : Math.min(1, (f.depth[y][x] - 1) / 2.0);
                return 2.7 + 1.0 * heart + 0.35 * Math.sin(phase + (x + y) * 0.3);
            }
            case FLAME: {
                // Hot in the heart and low down, cooler at the rim and the tips; it flickers.
                double height = f.bottom[p] - f.top[p];
                double low = height < 1 ? 0.5 : (y - f.top[p]) / height;
                double heat = 0.75 * Math.min(1, (f.depth[y][x] - 1) / 1.5) + 0.75 * low - 0.15;
                heat += 0.5 * hash(x, y + frame * 3, 7) - 0.25;
                return Math.max(0.7, Math.min(4, 1 + heat * 3));
            }
            case VOID: {
                // Night sky: dark, a violet rim toward the light, and stars that twinkle.
                double star = hash(x, y, 11);
                if (star > 0.9 && f.depth[y][x] > 1) {
                    return Math.floorMod(frame + (int) (star * 97), FRAMES) < 3 ? 4 : 2.6;
                }
                double t = 0.7 + 1.3 * hash(x / 2, y / 2, 13) * (1 - across);
                return toward == 2 ? Math.max(t, 2.6) : t;
            }
            case GEM: {
                // A cut stone: a lit facet toward the light, a dark one away, and one sparkle.
                double dx = x - f.cx[p], dy = y - f.cy[p];
                double side = (dx * f.s.light().dx + dy * f.s.light().dy);
                double reach = Math.max(1, Math.sqrt(f.size[p]) / 2);
                double t = 2 - 1.5 * side / reach;
                if (away == 2) t -= 0.8;
                int sx = (int) Math.round(f.cx[p] - f.s.light().dx * reach * 0.45);
                int sy = (int) Math.round(f.cy[p] - f.s.light().dy * reach * 0.45);
                if (x == sx && y == sy && f.size[p] >= 3) return frame < 5 ? 4 : 3.4;
                return Math.max(0.6, Math.min(3.4, t + 0.25 * Math.sin(phase)));
            }
            default:
                break;
        }
        double spread = switch (family) {
            case METAL -> 2.3;
            case WOOD -> 1.3;
            default -> 1.6;
        };
        double t = 2 + (0.5 - across) * spread;
        // Edges: lit where the shape faces the light, a tone down where it faces away, so the far edge
        // draws the outline; a neighbor of another material casts a smaller edge of its own.
        if (toward == 2) t += family == Family.METAL ? 0.9 : 0.6;
        else if (toward == 1) t += 0.3;
        if (away == 2 && f.s.outline() != Outline.NONE) t -= 1.1;
        else if (away == 1) t -= 0.5;
        switch (family) {
            case METAL: {
                if (t >= 3.4) t = 4;
                // A glint crosses the whole sprite along the light, then rests.
                double along = x * f.s.light().dx + y * f.s.light().dy;
                double lo = f.s.light().dx < 0 ? -(f.s.w() - 1) : 0;
                double hi = (f.s.light().dx > 0 ? f.s.w() - 1 : 0) + f.s.h() - 1;
                if (frame < 5 && Math.abs(along - (lo + frame / 4.0 * (hi - lo))) < 0.75 && t >= 1.5) {
                    t = Math.max(t + 1, 3.6);
                }
                break;
            }
            case WOOD: {
                int line = switch (f.grain[p]) {
                    case 1 -> y;
                    case 2 -> x - y;
                    case 3 -> x + y;
                    default -> x;
                };
                if (hash(line, p, 17) < 0.3) t -= 0.7;
                break;
            }
            case CLOTH:
                // Soft, with gentle folds down its length: less contrast than anything hard.
                t = 2 + (t - 2) * 0.75 + 0.2 * Math.sin(x * 0.9 + p * 2.1);
                break;
            case HIDE:
                t += hash(x, y / 2, 19) < 0.25 ? -0.6 : 0;
                break;
            default:
                t += 0.6 * hash(x, y, 23) - 0.3;
                break;
        }
        return Math.max(0, Math.min(family == Family.METAL ? 4 : 3.4, t));
    }

    // ---- Outline and halo -----------------------------------------------------------------------------

    /** A dark line in every empty texel that touches the shape, tinted by what it touches. */
    private static void outline(Sprite s, int[] px) {
        int w = s.w(), h = s.h();
        int[] out = px.clone();
        int[][] step = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (s.at(x, y) != null) continue;
                for (int[] d : step) {
                    Ink ink = s.at(x + d[0], y + d[1]);
                    if (ink == null) continue;
                    out[y * w + x] = 0xFF000000 | scale(ink.ramp()[Pigment.DEEP], 0.5);
                    break;
                }
            }
        }
        System.arraycopy(out, 0, px, 0, px.length);
    }

    /** Light spilling from what shines into the empty texels within two of it, fading and breathing. */
    private static void halo(Sprite s, int[] px, int[] glow, int frame) {
        int w = s.w(), h = s.h();
        double breath = 0.8 + 0.2 * Math.sin(2 * Math.PI * frame / FRAMES);
        int[] source = glow.clone();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((px[y * w + x] >>> 24) != 0) continue;
                int best = 99, rgb = 0;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        int nx = x + dx, ny = y + dy, d = Math.abs(dx) + Math.abs(dy);
                        if (d == 0 || d > 2 || nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        if ((source[ny * w + nx] >>> 24) == 0 || d >= best) continue;
                        best = d;
                        rgb = source[ny * w + nx] & 0xFFFFFF;
                    }
                }
                if (best > 2) continue;
                int alpha = (int) ((best == 1 ? 120 : 50) * breath);
                px[y * w + x] = (alpha << 24) | rgb;
                glow[y * w + x] = (alpha << 24) | rgb;
            }
        }
    }

    private static int scale(int rgb, double f) {
        int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    static double hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }
}
