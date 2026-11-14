package net.bigyous.gptgod.client.relic;

import java.util.Locale;

/**
 * One relic's art, ready to draw. Pure. pixels are ARGB, w wide and h * frames tall: frame i is rows
 * i*h .. i*h+h-1. The plugin's "lit" layer is laid over the picture and glow marks the texels it touched;
 * they are drawn like every other texel (a relic is lit and shaded like any item). depth, when the plugin sent one,
 * is each texel's thickness front to back, in texels (w * h bytes, the same for every frame): 0 is a
 * film with no thickness at all. Without it (null) the relic is one sixteenth of a block thick all over.
 *
 * walls, when the plugin sent them, are the colours the painter gave each texel's sides: per walls
 * frame a square 2w x 2h whose quarters are the sides seen from above (top left), from below (top
 * right), from the left (bottom left) and from the right (bottom right), wallFrames of them stacked top
 * to bottom. Without them (null) a side has the colour of its texel's face.
 *
 * shape, when the plugin sent one, is the relic's true form (see {@link RelicShape#build}): its faces and
 * their material tiles, built once. With it the relic is drawn as that shape everywhere but in inventories, which keep the picture.
 */
public record RelicArt(String id, int rev, String name, Kind kind, int w, int h, int frames, int ticks, int[] pixels,
        boolean[] glow, byte[] depth, int[] walls, int wallFrames, RelicShape.Model shape) {
    public static final int MIN_W = 8, MAX_W = 32, MIN_H = 8, MAX_H = 48, MAX_FRAMES = 8, MAX_TICKS = 20;
    /** The thickest a texel may be, in texels. */
    public static final int MAX_DEPTH = 12;
    /** How thick a relic without a depth map is, in blocks: what the game's flat items are. */
    public static final float FLAT_THICKNESS = 1 / 16f;
    /** Fainter texels are not drawn at all (the game's item shader throws away alpha under 0.1). */
    public static final int VISIBLE_ALPHA = 26;

    /** What the relic is; it decides how the hand holds it. */
    public enum Kind {
        BLADE, STAFF, TRINKET, TOME, SPIRIT;

        /** Held like a tool (item/handheld) rather than like a plain item (item/generated). */
        public boolean handheld() {
            return this == BLADE || this == STAFF;
        }

        /** Unknown kinds (a newer plugin) are drawn as plain items. */
        public static Kind of(String name) {
            if (name != null) {
                for (Kind k : values()) {
                    if (k.name().equals(name.toUpperCase(Locale.ROOT))) return k;
                }
            }
            return TRINKET;
        }
    }

    /**
     * Lays lit over base (both ARGB strips of w x h*frames; lit may be null) and marks the texels lit
     * touched. Null when a size is out of range or an array does not fit it.
     */
    public static RelicArt of(String id, int rev, String name, Kind kind, int w, int h, int frames, int ticks, int[] base,
            int[] lit) {
        return of(id, rev, name, kind, w, h, frames, ticks, base, lit, null);
    }

    /** The same with a depth map. One that does not fit (not w * h bytes, or a texel thicker than 12) is left out. */
    public static RelicArt of(String id, int rev, String name, Kind kind, int w, int h, int frames, int ticks, int[] base,
            int[] lit, byte[] depth) {
        return of(id, rev, name, kind, w, h, frames, ticks, base, lit, depth, null, 0);
    }

    /**
     * The same with painted walls: wallFrames squares of 2w x 2h, ARGB (see above). Walls that do not
     * fit (another size, no frames, or more frames than the picture) are left out.
     */
    public static RelicArt of(String id, int rev, String name, Kind kind, int w, int h, int frames, int ticks, int[] base,
            int[] lit, byte[] depth, int[] walls, int wallFrames) {
        if (w < MIN_W || w > MAX_W || h < MIN_H || h > MAX_H || frames < 1 || frames > MAX_FRAMES) return null;
        int n = w * h * frames;
        if (id == null || kind == null || base == null || base.length != n || (lit != null && lit.length != n)) return null;
        int[] pixels = new int[n];
        boolean[] glow = new boolean[n];
        for (int i = 0; i < n; i++) {
            int top = lit == null ? 0 : lit[i];
            glow[i] = (top >>> 24) != 0;
            pixels[i] = glow[i] ? over(top, base[i]) : base[i];
        }
        boolean painted = walls != null && wallFrames >= 1 && wallFrames <= frames && walls.length == 4 * w * h * wallFrames;
        return new RelicArt(id, Math.max(0, rev), name == null ? id : name, kind, w, h, frames,
                Math.max(1, Math.min(MAX_TICKS, ticks)), pixels, glow, fits(depth, w * h) ? depth.clone() : null,
                painted ? seeThroughAsFaces(walls, pixels, w, h, frames, wallFrames) : null, painted ? wallFrames : 0, null);
    }

    /** The same art with a true shape (from RelicShape.build; null for none). */
    public RelicArt withShape(RelicShape.Model shape) {
        return new RelicArt(id, rev, name, kind, w, h, frames, ticks, pixels, glow, depth, walls, wallFrames, shape);
    }

    /**
     * A copy of the walls whose texels are exactly as see-through as their faces (the most opaque of the
     * frames that share them), so a side is never cut away from a texel that is drawn, nor drawn where
     * there is none.
     */
    private static int[] seeThroughAsFaces(int[] walls, int[] pixels, int w, int h, int frames, int wallFrames) {
        int[] out = walls.clone();
        for (int g = 0; g < wallFrames; g++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int alpha = 0;
                    for (int f = 0; f < frames; f++) {
                        if (f * wallFrames / frames == g) alpha = Math.max(alpha, pixels[(f * h + y) * w + x] >>> 24);
                    }
                    for (int q = 0; q < 4; q++) {
                        int at = (g * 2 * h + (q / 2) * h + y) * 2 * w + (q % 2) * w + x;
                        out[at] = alpha << 24 | (out[at] & 0xFFFFFF);
                    }
                }
            }
        }
        return out;
    }

    private static boolean fits(byte[] depth, int texels) {
        if (depth == null || depth.length != texels) return false;
        for (byte d : depth) {
            if (d < 0 || d > MAX_DEPTH) return false;
        }
        return true;
    }

    /** Ordinary alpha blending of one ARGB colour over another (neither premultiplied). */
    static int over(int top, int under) {
        int ta = top >>> 24, ua = under >>> 24;
        if (ta == 255 || ua == 0) return top;
        int rest = ua * (255 - ta); // the part of the lower colour that still shows, times 255
        int a = ta * 255 + rest;
        int out = Math.round(a / 255f) << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int c = (((top >> shift) & 255) * ta * 255 + ((under >> shift) & 255) * rest + a / 2) / a;
            out |= Math.min(255, c) << shift;
        }
        return out;
    }

    /** The frame to show at this client tick: each lasts `ticks` ticks, then the strip loops. */
    public int frameAt(long tick) {
        return (int) Math.floorMod(Math.floorDiv(tick, (long) ticks), (long) frames);
    }

    public int pixel(int frame, int x, int y) {
        return pixels[(frame * h + y) * w + x];
    }

    /** Whether this texel is drawn. Outside the frame nothing is. */
    public boolean visible(int frame, int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && (pixel(frame, x, y) >>> 24) >= VISIBLE_ALPHA;
    }

    public boolean glows(int frame, int x, int y) {
        return glow[(frame * h + y) * w + x];
    }

    /** Which of the painted walls' frames goes with this frame of the picture (spread evenly when there are fewer). */
    public int wallFrame(int frame) {
        return wallFrames <= 0 ? 0 : frame * wallFrames / frames;
    }

    /** How thick this texel is, front to back, in blocks: a texel of depth is as deep as it is wide. 0 is a film. */
    public float thickness(int x, int y) {
        return depth == null ? FLAT_THICKNESS : depth[y * w + x] / (float) Math.max(w, h);
    }

    /** The thickest texel, in blocks. */
    public float thickest() {
        if (depth == null) return FLAT_THICKNESS;
        int most = 0;
        for (byte d : depth) most = Math.max(most, d);
        return most / (float) Math.max(w, h);
    }

    /** Whether any drawn texel is see-through, so the art needs blending rather than a plain cut-out. */
    public boolean translucent() {
        for (int p : pixels) {
            int a = p >>> 24;
            if (a >= VISIBLE_ALPHA && a < 255) return true;
        }
        return false;
    }
}
