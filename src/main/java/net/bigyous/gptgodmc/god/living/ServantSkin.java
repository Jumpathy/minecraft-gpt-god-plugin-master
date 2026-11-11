package net.bigyous.gptgodmc.god.living;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.bigyous.gptgodmc.god.vessel.VesselSkin.Face;
import net.bigyous.gptgodmc.god.vessel.VesselSkin.Rect;

/**
 * What each servant's own body is made of, and where every piece's skin lives in that servant's texture
 * atlas. A piece is a box (unwrapped like a player-skin limb: top and bottom above, then the four sides in
 * a row), a flat plane seen from both sides (wings, cloaks, halos: shapes cut out with alpha), or a glow
 * (a soft upright plane that turns to face the viewer; it must sit on the body's own axis, because the
 * client moves a billboard's offset with the camera). The resource pack (UVs), the body rig (sizes) and
 * the offline previews all read this, so what the previews show is what the game draws. Pure.
 */
public final class ServantSkin {
    /** Texels per block: twice vanilla's density, about what the gods' skins have. */
    public static final int TEXELS = 32;
    /** Animation frames in each atlas, and game ticks per frame. */
    public static final int FRAMES = 8;
    public static final int FRAME_TICKS = 3;
    /** Empty texels around every region so mipmaps do not bleed between pieces. */
    static final int GUTTER = 2;

    public enum Shape {
        BOX, PLANE, GLOW
    }

    /**
     * One kind of piece: its size in texels (d is 0 for planes and glows) and whether it joins the
     * glowing outline (soft, see-through pieces stay out of it).
     */
    public record Def(String key, Shape shape, int w, int h, int d, boolean outline) {
        /** Its size in blocks at one texel per 1/32 block. */
        public float bw() {
            return w / (float) TEXELS;
        }

        public float bh() {
            return h / (float) TEXELS;
        }

        public float bd() {
            return d / (float) TEXELS;
        }
    }

    /** One piece's place in the atlas, its top-left corner at (x, y). */
    public record Region(Def def, int x, int y) {
        public int width() {
            return def.shape() == Shape.BOX ? 2 * (def.w() + def.d()) : def.w();
        }

        public int height() {
            return def.shape() == Shape.BOX ? def.d() + def.h() : def.h();
        }

        /** The rectangle of a box face; planes and glows have one picture, whatever the face. */
        public Rect face(Face f) {
            int w = def.w(), h = def.h(), d = def.d();
            if (def.shape() != Shape.BOX) return new Rect(x, y, w, h);
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

    public record Layout(ServantKind kind, int width, int height, Map<String, Region> regions) {
        public Region region(String key) {
            Region r = regions.get(key);
            if (r == null) throw new IllegalArgumentException("no " + kind.id + " skin region " + key);
            return r;
        }
    }

    private static final Map<ServantKind, List<Def>> DEFS = new EnumMap<>(ServantKind.class);
    private static final Map<ServantKind, Layout> LAYOUTS = new EnumMap<>(ServantKind.class);

    static {
        for (ServantKind k : ServantKind.values()) {
            DEFS.put(k, List.copyOf(define(k)));
            LAYOUTS.put(k, pack(k, 256));
        }
    }

    private ServantSkin() {
    }

    public static List<Def> defs(ServantKind k) {
        return DEFS.get(k);
    }

    public static Def def(ServantKind k, String key) {
        return layout(k).region(key).def();
    }

    public static Layout layout(ServantKind k) {
        return LAYOUTS.get(k);
    }

    private static Def box(String key, int w, int h, int d) {
        return new Def(key, Shape.BOX, w, h, d, true);
    }

    /** A box that is partly see-through light or mist: no outline. */
    private static Def veil(String key, int w, int h, int d) {
        return new Def(key, Shape.BOX, w, h, d, false);
    }

    private static Def plane(String key, int w, int h) {
        return new Def(key, Shape.PLANE, w, h, 0, true);
    }

    private static Def mist(String key, int w, int h) {
        return new Def(key, Shape.PLANE, w, h, 0, false);
    }

    private static Def glow(String key, int w, int h) {
        return new Def(key, Shape.GLOW, w, h, 0, false);
    }

    /** Every piece of a servant, in a fixed order. */
    private static List<Def> define(ServantKind k) {
        return switch (k) {
            // A seraph: robes of light that end in a falling shimmer, four wings, a sword of light.
            case ANGEL -> List.of(box("head", 13, 13, 13), box("torso", 17, 18, 9), box("mantle", 21, 4, 12),
                    box("skirt_up", 17, 12, 10), box("skirt_low", 21, 13, 12), veil("hem", 23, 16, 14),
                    box("arm", 6, 18, 6), box("blade", 5, 34, 1), box("hilt", 15, 3, 3), box("grip", 3, 8, 3),
                    plane("veil", 14, 24), plane("wing_in", 30, 38), plane("wing_out", 40, 42),
                    plane("wing_low", 20, 36), plane("halo", 24, 24), glow("nimbus", 40, 40));
            // A cinder imp: cracked basalt skin over fire, membrane wings, horns, a barbed tail.
            case IMP -> List.of(box("head", 14, 12, 12), plane("ear", 10, 7), box("horn", 3, 8, 3),
                    box("horn_tip", 2, 7, 2), box("torso", 9, 11, 6), box("arm", 3, 13, 3), box("leg", 3, 10, 3),
                    box("tail", 2, 9, 2), plane("tail_tip", 8, 8), plane("wing_in", 18, 20),
                    plane("wing_out", 24, 26), glow("flame", 10, 14), glow("glow", 32, 32));
            // A wraith: a hood of night over two soul-fire eyes, a robe that frays into mist, a scythe.
            case SHADE -> List.of(box("face", 14, 14, 12), box("hood", 16, 16, 16), box("hood_peak", 8, 7, 9),
                    box("mantle", 27, 6, 14), box("torso", 17, 22, 9), box("skirt", 21, 30, 12),
                    plane("cloak", 32, 52), box("arm", 6, 22, 6), plane("hand", 9, 10), box("fist", 5, 5, 5),
                    box("shaft", 2, 72, 2),
                    plane("scythe", 32, 18), mist("smoke", 22, 14), glow("glow", 32, 32), mist("wisp", 9, 9));
            // A hierophant: a golden mask under a mitre, a saint's disc, vestments, a staff and a book.
            case PRIEST -> List.of(box("head", 13, 13, 13), box("mitre_low", 13, 8, 10), box("mitre_top", 9, 8, 6),
                    plane("disc", 36, 36), box("mantle", 21, 5, 13), box("torso", 17, 18, 9),
                    box("skirt_up", 17, 13, 10), box("skirt_low", 21, 15, 12), veil("hem", 25, 7, 16),
                    box("arm", 7, 16, 7), box("shaft", 2, 72, 2), plane("sun", 18, 18), box("book", 12, 2, 9),
                    mist("glyph_0", 7, 7), mist("glyph_1", 7, 7), mist("glyph_2", 7, 7), mist("glyph_3", 7, 7),
                    mist("glow", 32, 32));
            // A colossus: marble armor veined with gold around a core of light, tower shield and halberd.
            case GUARD -> List.of(box("leg", 13, 30, 13), box("fauld", 29, 9, 16), plane("tabard", 13, 22),
                    box("torso", 35, 26, 19), box("pauldron", 16, 10, 19), box("arm_up", 10, 15, 10),
                    box("gauntlet", 12, 22, 12), box("helm", 15, 15, 15), plane("crest", 22, 13),
                    box("shield", 23, 38, 3), box("shaft", 2, 94, 2), plane("blade", 22, 28), plane("cape", 33, 44),
                    mist("glow", 32, 32), mist("ward", 48, 48));
        };
    }

    /** Shelf-packs every region into a strip this wide; the height becomes a power of two. */
    private static Layout pack(ServantKind k, int width) {
        List<Def> order = new ArrayList<>(DEFS.get(k));
        order.sort(Comparator.comparingInt((Def d) -> new Region(d, 0, 0).height()).reversed()
                .thenComparing(Def::key));
        record Shelf(int y, int height, int[] used) {
        }
        List<Shelf> shelves = new ArrayList<>();
        Map<String, Region> placed = new LinkedHashMap<>();
        int bottom = 0;
        for (Def d : order) {
            Region size = new Region(d, 0, 0);
            int w = size.width() + GUTTER * 2, h = size.height() + GUTTER * 2;
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
            placed.put(d.key(), new Region(d, home.used()[0] + GUTTER, home.y() + GUTTER));
            home.used()[0] += w;
        }
        int height = Integer.highestOneBit(Math.max(1, bottom - 1)) * 2;
        Map<String, Region> ordered = new LinkedHashMap<>();
        for (Def d : DEFS.get(k)) ordered.put(d.key(), placed.get(d.key()));
        return new Layout(k, width, height, Collections.unmodifiableMap(ordered));
    }
}
