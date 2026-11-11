package net.bigyous.gptgod.client.relic;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of a relic, as pure maths: a sprite frame built of little stacked cubes. Every drawn texel
 * is a box as thick as the relic's depth map says (one sixteenth of a block everywhere when there is
 * none, like the game's item/generated models): a front, a back, and side walls wherever its neighbour
 * is thinner or empty. A texel of no thickness is a film, two faces in the middle plane. Faces are
 * joined where neighbours match.
 */
public final class RelicMesh {
    private RelicMesh() {
    }

    /** Which way a face looks, seen from the front of the sprite. */
    public enum Side {
        FRONT, BACK, UP, DOWN, LEFT, RIGHT
    }

    /**
     * A face coloured by the texels x0..x1-1, y0..y1-1 of its frame, between the depths z0 and z1 (blocks,
     * back to front). FRONT and BACK cover that rectangle at one depth; the others are a run one texel
     * thick and stand on its upper, lower, left or right border.
     */
    public record Face(Side side, int x0, int y0, int x1, int y1, float z0, float z1) {
        /** A face of a relic one sixteenth of a block thick, as all are without a depth map. */
        public Face(Side side, int x0, int y0, int x1, int y1) {
            this(side, x0, y0, x1, y1, side == Side.FRONT ? FRONT_Z : BACK_Z, side == Side.BACK ? BACK_Z : FRONT_Z);
        }

        /** One side of a film: texels with no thickness, lying in the middle plane. */
        public boolean film() {
            return z0 == MIDDLE_Z && z1 == MIDDLE_Z;
        }
    }

    /** More faces than any real sprite needs, depth map and all; past this a frame is drawn as a flat card instead. */
    public static final int MAX_FACES = 6000;
    /** The plane a relic is centred on, and the back and front of one without a depth map, in blocks. */
    public static final float MIDDLE_Z = 0.5f, BACK_Z = 7.5f / 16f, FRONT_Z = 8.5f / 16f;
    /** How far texture coordinates stay inside their texels, so a face never picks up its neighbour's colour. */
    private static final float INSET = 0.02f;

    public static List<Face> build(RelicArt art, int frame) {
        int w = art.w(), h = art.h();
        List<Face> faces = new ArrayList<>();
        // 1 for a drawn texel, 0 for an empty one; and how far each drawn texel reaches either side of the middle.
        byte[] kind = new byte[w * h];
        float[] half = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!art.visible(frame, x, y)) continue;
                kind[y * w + x] = 1;
                half[y * w + x] = art.thickness(x, y) / 2;
            }
        }
        // Front and back: the largest rectangles of one thickness, taken greedily from the top left.
        boolean[] used = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int at = y * w + x;
                byte k = kind[at];
                if (k == 0 || used[at]) continue;
                float reach = half[at];
                int x1 = x + 1;
                while (x1 < w && same(kind, half, used, y * w + x1, k, reach)) x1++;
                int y1 = y + 1;
                grow: while (y1 < h) {
                    for (int i = x; i < x1; i++) {
                        if (!same(kind, half, used, y1 * w + i, k, reach)) break grow;
                    }
                    y1++;
                }
                for (int j = y; j < y1; j++) {
                    for (int i = x; i < x1; i++) used[j * w + i] = true;
                }
                faces.add(new Face(Side.FRONT, x, y, x1, y1, MIDDLE_Z + reach, MIDDLE_Z + reach));
                faces.add(new Face(Side.BACK, x, y, x1, y1, MIDDLE_Z - reach, MIDDLE_Z - reach));
            }
        }
        // Sides: along each row and column, runs of texels that stand proud of their neighbour.
        for (int y = 0; y < h; y++) {
            runs(faces, kind, half, w, h, y, true, -1, Side.UP);
            runs(faces, kind, half, w, h, y, true, 1, Side.DOWN);
        }
        for (int x = 0; x < w; x++) {
            runs(faces, kind, half, w, h, x, false, -1, Side.LEFT);
            runs(faces, kind, half, w, h, x, false, 1, Side.RIGHT);
        }
        if (faces.size() > MAX_FACES) {
            faces.clear();
            faces.add(new Face(Side.FRONT, 0, 0, w, h));
            faces.add(new Face(Side.BACK, 0, 0, w, h));
        }
        return faces;
    }

    private static boolean same(byte[] kind, float[] half, boolean[] used, int at, byte k, float reach) {
        return kind[at] == k && half[at] == reach && !used[at];
    }

    /**
     * One row (or column) `line`: walls for every run of texels thicker than the neighbour `step`
     * away. Against an empty neighbour or a film the wall is the texel's whole side; against a thinner
     * one it is the two steps that stand out, in front and behind.
     */
    private static void runs(List<Face> out, byte[] kind, float[] half, int w, int h, int line, boolean row, int step, Side side) {
        int length = row ? w : h;
        int start = -1;
        byte running = 0;
        float reach = 0, beside = 0;
        for (int i = 0; i <= length; i++) {
            byte k = 0;
            float mine = 0, next = 0;
            if (i < length) {
                int x = row ? i : line, y = row ? line : i;
                int nx = row ? x : x + step, ny = row ? y + step : y;
                mine = half[y * w + x];
                next = nx < 0 || ny < 0 || nx >= w || ny >= h ? 0 : half[ny * w + nx];
                if (mine > next) k = kind[y * w + x];
            }
            if (k == running && (k == 0 || (mine == reach && next == beside))) continue;
            if (running != 0) {
                int x0 = row ? start : line, y0 = row ? line : start, x1 = row ? i : line + 1, y1 = row ? line + 1 : i;
                if (beside == 0) {
                    out.add(new Face(side, x0, y0, x1, y1, MIDDLE_Z - reach, MIDDLE_Z + reach));
                } else {
                    out.add(new Face(side, x0, y0, x1, y1, MIDDLE_Z + beside, MIDDLE_Z + reach));
                    out.add(new Face(side, x0, y0, x1, y1, MIDDLE_Z - reach, MIDDLE_Z - beside));
                }
            }
            running = k;
            reach = mine;
            beside = next;
            start = i;
        }
    }

    /** Blocks per texel: the sprite keeps its shape and its longer side fills the item's one-block square. */
    public static float texel(RelicArt art) {
        return 1f / Math.max(art.w(), art.h());
    }

    /** The box around the whole relic, in blocks: min x, y, z then max x, y, z. It sits in the middle of the square. */
    public static float[] bounds(RelicArt art) {
        float s = texel(art);
        float x = (1 - art.w() * s) / 2, y = (1 - art.h() * s) / 2, z = art.thickest() / 2;
        return new float[] { x, y, MIDDLE_Z - z, 1 - x, 1 - y, MIDDLE_Z + z };
    }

    /** A face's flat box in blocks: min x, y, z then max x, y, z. Texel rows count down from the top. */
    public static float[] box(RelicArt art, Face f) {
        float s = texel(art);
        float[] b = bounds(art);
        float left = b[0] + f.x0() * s, right = b[0] + f.x1() * s, top = b[4] - f.y0() * s, bottom = b[4] - f.y1() * s;
        return switch (f.side()) {
            case FRONT -> new float[] { left, bottom, f.z1(), right, top, f.z1() };
            case BACK -> new float[] { left, bottom, f.z0(), right, top, f.z0() };
            case UP -> new float[] { left, top, f.z0(), right, top, f.z1() };
            case DOWN -> new float[] { left, bottom, f.z0(), right, bottom, f.z1() };
            case LEFT -> new float[] { left, bottom, f.z0(), left, top, f.z1() };
            case RIGHT -> new float[] { right, bottom, f.z0(), right, top, f.z1() };
        };
    }

    /**
     * Where on the texture (0..1 across the strip, 0..1 down all its frames) a corner of a face at block
     * position x, y takes its colour. A side face reads the middle of its run's texels.
     */
    public static float[] uv(RelicArt art, Face f, int frame, float x, float y) {
        float[] t = texelAt(art, f, x, y);
        return new float[] { t[0] / art.w(), (frame * art.h() + t[1]) / (art.h() * art.frames()) };
    }

    /**
     * The same for a side face on the painted walls (RelicArt.walls): the quarter of the walls' frame
     * that shows this side, at the face's own texels. Front and back faces have no walls.
     */
    public static float[] wallUv(RelicArt art, Face f, int frame, float x, float y) {
        float[] t = texelAt(art, f, x, y);
        float across = switch (f.side()) {
            case DOWN, RIGHT -> art.w();
            default -> 0;
        };
        float down = switch (f.side()) {
            case LEFT, RIGHT -> art.h();
            default -> 0;
        };
        return new float[] { (across + t[0]) / (2f * art.w()),
                (art.wallFrame(frame) * 2f * art.h() + down + t[1]) / (2f * art.h() * Math.max(1, art.wallFrames())) };
    }

    /** Which texel (x, y, in texels from the top left of a frame) a corner of a face at block position x, y reads. */
    private static float[] texelAt(RelicArt art, Face f, float x, float y) {
        float s = texel(art);
        float[] b = bounds(art);
        float u = switch (f.side()) {
            case LEFT, RIGHT -> f.x0() + 0.5f;
            default -> Math.max(f.x0() + INSET, Math.min(f.x1() - INSET, (x - b[0]) / s));
        };
        float v = switch (f.side()) {
            case UP, DOWN -> f.y0() + 0.5f;
            default -> Math.max(f.y0() + INSET, Math.min(f.y1() - INSET, (b[4] - y) / s));
        };
        return new float[] { u, v };
    }
}
