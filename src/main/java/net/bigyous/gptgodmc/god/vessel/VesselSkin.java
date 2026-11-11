package net.bigyous.gptgodmc.god.vessel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where each body part's skin lives in a look's texture atlas. Every part is a box, unwrapped the way
 * a player skin unwraps a limb: top and bottom above, then right side, front, left side and back in
 * one row. The resource pack (UVs) and the preview renderer (texture lookups) both read this, so
 * what the previews show is what the game draws. Pure, no server needed.
 */
public final class VesselSkin {
    /** Texels per skin pixel: twice a player skin's density, so runes and stars have room. */
    public static final int DENSITY = 2;
    /** Animation frames in each atlas; the client cycles them. */
    public static final int FRAMES = 8;
    /** Game ticks per animation frame. */
    public static final int FRAME_TICKS = 3;
    /** Empty texels between regions, filled with each region's edge so mipmaps do not bleed. */
    static final int GUTTER = 1;

    /** Box faces, named as in Minecraft models (north = -z, the vessel's back). */
    public enum Face {
        DOWN, UP, NORTH, SOUTH, WEST, EAST;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * The point of the unit box under texture coordinates (s across, t down) of this face, as
         * Minecraft's default face UVs map them (u = x on the south face, u = 1 - x on the north...).
         */
        public float[] point(double s, double t) {
            float a = (float) s, b = (float) t;
            return switch (this) {
                case SOUTH -> new float[] { a, 1 - b, 1 };
                case NORTH -> new float[] { 1 - a, 1 - b, 0 };
                case EAST -> new float[] { 1, 1 - b, 1 - a };
                case WEST -> new float[] { 0, 1 - b, a };
                case UP -> new float[] { a, 1, b };
                case DOWN -> new float[] { a, 0, 1 - b };
            };
        }

        /** Texture coordinates (s, t) of a point on this face of the unit box; the inverse of point. */
        public double[] st(double x, double y, double z) {
            return switch (this) {
                case SOUTH -> new double[] { x, 1 - y };
                case NORTH -> new double[] { 1 - x, 1 - y };
                case EAST -> new double[] { 1 - z, 1 - y };
                case WEST -> new double[] { z, 1 - y };
                case UP -> new double[] { x, z };
                case DOWN -> new double[] { x, 1 - z };
            };
        }

        /** The face whose outward normal is this axis (0 x, 1 y, 2 z) and sign (0 negative, 1 positive). */
        public static Face of(int axis, int positive) {
            return switch (axis) {
                case 0 -> positive == 1 ? EAST : WEST;
                case 1 -> positive == 1 ? UP : DOWN;
                default -> positive == 1 ? SOUTH : NORTH;
            };
        }
    }

    /**
     * The kinds of box the vessel is made of, with their size in texels (x, y, z). Horns have no
     * texture: they keep their block look on purpose (so do the other head ornaments: crowns, antlers,
     * laurels...). BEARD and PATCH (an eyepatch) are worn only by some looks.
     */
    public enum Piece {
        HEAD(8, 8, 8), TORSO(8, 12, 4), ARM(4, 9, 4), HAND(4, 3, 4), BAND(4.4f, 1.2f, 4.4f), LEG(4, 9, 4),
        BOOT(4.3f, 3.2f, 4.3f), BELT(8.5f, 1.7f, 4.5f), GEM(2, 2, 2), EYE(2, 1, 0.5f), HAIR_TOP(8.5f, 1.5f, 8.5f),
        HAIR_BACK(8.5f, 4.6f, 0.9f), COLLAR(8.6f, 1.5f, 4.8f), CAPE(10, 17, 0.5f), LINING(10, 17, 0.5f),
        HALO(1, 1, 2.6f), BEARD(6.6f, 4.8f, 1.6f), PATCH(2.4f, 1.7f, 0.4f), HORN(0, 0, 0);

        public final int w, h, d;

        Piece(float x, float y, float z) {
            this.w = texels(x);
            this.h = texels(y);
            this.d = texels(z);
        }

        private static int texels(float px) {
            return px == 0 ? 0 : Math.max(1, Math.round(px * DENSITY));
        }

        public boolean textured() {
            return this != HORN;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Eye colors a mood can put in the vessel's eyes, each with its own region. */
    public static final List<String> MOOD_EYES = List.of("wrathful", "ominous", "exalted");

    public record Rect(int x, int y, int w, int h) {
    }

    /** One part's unwrapped box in the atlas, its top-left corner at (x, y). */
    public record Region(String key, Piece piece, int x, int y) {
        public int width() {
            return 2 * (piece.w + piece.d);
        }

        public int height() {
            return piece.d + piece.h;
        }

        public Rect face(Face f) {
            int w = piece.w, h = piece.h, d = piece.d;
            return switch (f) {
                case UP -> new Rect(x + d, y, w, d);
                case DOWN -> new Rect(x + d + w, y, w, d);
                case WEST -> new Rect(x, y + d, d, h);
                case SOUTH -> new Rect(x + d, y + d, w, h);
                case EAST -> new Rect(x + d + w, y + d, d, h);
                case NORTH -> new Rect(x + 2 * d + w, y + d, w, h);
            };
        }
    }

    /** Where every region sits in one frame of an atlas. */
    public record Layout(int width, int height, Map<String, Region> regions) {
        public Region region(String key) {
            Region r = regions.get(key);
            if (r == null) throw new IllegalArgumentException("no skin region " + key);
            return r;
        }
    }

    /** The region names: one per textured piece, plus the mood eyes ("eye_wrathful"...). */
    public static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (Piece p : Piece.values()) {
            if (p.textured()) keys.add(p.id());
        }
        for (String mood : MOOD_EYES) keys.add(eyeKey(mood));
        return keys;
    }

    public static String eyeKey(String mood) {
        return "eye_" + mood;
    }

    public static Piece pieceOf(String key) {
        return key.startsWith("eye_") ? Piece.EYE : Piece.valueOf(key.toUpperCase(Locale.ROOT));
    }

    public static final Layout LAYOUT = pack(256);

    /** Shelf-packs every region into a strip this wide; the height becomes a power of two. */
    static Layout pack(int width) {
        List<String> keys = new ArrayList<>(keys());
        keys.sort(Comparator.comparingInt((String k) -> pieceOf(k).d + pieceOf(k).h).reversed()
                .thenComparing(Comparator.naturalOrder()));
        record Shelf(int y, int height, int[] used) {
        }
        List<Shelf> shelves = new ArrayList<>();
        Map<String, Region> placed = new LinkedHashMap<>();
        int bottom = 0;
        for (String key : keys) {
            Piece p = pieceOf(key);
            int w = 2 * (p.w + p.d) + GUTTER * 2, h = p.d + p.h + GUTTER * 2;
            Shelf home = null;
            for (Shelf s : shelves) {
                if (s.height() >= h && s.used()[0] + w <= width) {
                    home = s;
                    break;
                }
            }
            if (home == null) {
                home = new Shelf(bottom, h, new int[] { 0 });
                shelves.add(home);
                bottom += h;
            }
            placed.put(key, new Region(key, p, home.used()[0] + GUTTER, home.y() + GUTTER));
            home.used()[0] += w;
        }
        int height = Integer.highestOneBit(Math.max(1, bottom - 1)) * 2;
        Map<String, Region> ordered = new LinkedHashMap<>();
        for (String k : keys()) ordered.put(k, placed.get(k));
        return new Layout(width, height, Collections.unmodifiableMap(ordered));
    }

    private VesselSkin() {
    }
}
