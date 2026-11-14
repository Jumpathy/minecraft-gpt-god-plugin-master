package net.bigyous.gptgodmc.god;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How a mortal looks: their skin read by code, once per skin, with no model call. The god is told the
 * colour of their hair, their top and their trousers, whether a hood or a mask frames their face, an
 * unhuman face (a creeper's green), a cape, and above all how much bare skin they show (bare-chested,
 * nearly naked, NAKED), with the armor they wear taken into account. What it makes of that is its own
 * choice. The colour of a mortal's skin itself is never named.
 *
 * <p>Bare skin is found by comparing the torso and legs with the colour of the face: the most common
 * colour of the base layer's face is the character's skin, whatever its tone, and a torso painted in
 * that colour (or a shade of it) is bare. The second layer (jacket, trousers, hat) covers the first
 * where it is opaque. Pure: unit tested with painted skins.
 */
public final class Avatars {
    private Avatars() {
    }

    /** How much of their body a skin leaves bare. */
    public enum Bare {
        NONE, CHEST, LEGS, UNDERWEAR, NAKED
    }

    /**
     * What a skin shows. Colours are plain names ("dark blue"); any may be null when there is nothing
     * there (a bare chest has no top).
     */
    public record Look(String hair, String head, String top, String bottom, Bare bare, String unhuman, boolean cape,
            boolean defaultSkin) {
    }

    /** A mortal with no skin of their own (the game gives them one of its default characters). */
    public static Look defaultLook(boolean cape) {
        return new Look(null, null, null, null, Bare.NONE, null, cape, true);
    }

    // ---- Reading a skin ------------------------------------------------------------------------------

    /** Reads a 64x64 (or old 64x32) skin. Returns a default look for anything else. */
    public static Look read(BufferedImage skin, boolean cape) {
        if (skin == null || skin.getWidth() != 64 || (skin.getHeight() != 64 && skin.getHeight() != 32)) {
            return defaultLook(cape);
        }
        Skin s = new Skin(skin);
        int ref = s.skinTone();
        boolean human = ref != 0 && humanTone(ref);
        String unhuman = ref == 0 || human ? null : colorName(ref);

        // Bare skin: the chest, the legs and the band at the hips.
        double chest = human ? s.share(ref, Skin.BODY, 0, 0, 8, 8) : 0;
        double legs = human ? (s.share(ref, Skin.RIGHT_LEG, 0, 2, 4, 6) + s.share(ref, Skin.LEFT_LEG, 0, 2, 4, 6)) / 2 : 0;
        double hips = human ? (s.share(ref, Skin.BODY, 0, 9, 8, 3) + s.share(ref, Skin.RIGHT_LEG, 0, 0, 4, 2)
                + s.share(ref, Skin.LEFT_LEG, 0, 0, 4, 2)) / 3 : 0;
        boolean bareChest = chest >= 0.6, bareLegs = legs >= 0.6, coveredHips = hips < 0.45;
        Bare bare = bareChest && bareLegs ? (coveredHips ? Bare.UNDERWEAR : Bare.NAKED)
                : bareChest ? Bare.CHEST : bareLegs ? Bare.LEGS : Bare.NONE;

        // What they wear: the colours that are not their skin.
        String top = bareChest ? null : s.colors(ref, human, Skin.BODY, 0, 0, 8, 10);
        String bottom = null;
        if (unhuman != null) {
            // A creature's body is its own, not clothes.
            top = top == null ? null : article(top) + " body";
        } else if (!bareLegs) {
            String upper = s.colors(ref, human, Skin.RIGHT_LEG, 0, 0, 4, 10);
            double shins = human ? (s.share(ref, Skin.RIGHT_LEG, 0, 6, 4, 4) + s.share(ref, Skin.LEFT_LEG, 0, 6, 4, 4)) / 2
                    : 0;
            if (upper != null) bottom = upper + (shins >= 0.6 ? " shorts or skirt" : " trousers");
        }
        if (top != null && unhuman == null) top = article(top) + " top";

        // The head: hair (or none), and a hood or a mask framing the face.
        String head = null;
        if (s.overlay) {
            double ring = s.overlayRing(), centre = s.overlayCentre();
            String c = s.overlayColor();
            if (centre >= 0.7 && c != null) head = article(c) + " mask or helmet over the face";
            else if (ring >= 0.9 && s.overlayTop() >= 0.7 && c != null) head = article(c) + " hood";
        }
        String hair = null;
        if (head == null || !head.endsWith("helmet over the face")) {
            int top0 = s.dominant(Skin.HEAD_TOP, 0, 0, 8, 8);
            if (top0 != 0) {
                boolean sameAsFace = ref != 0 && skinLike(top0, ref);
                if (sameAsFace) hair = human ? "a bald head" : null;
                else if (head == null) hair = hairColor(colorName(top0)) + " hair";
            }
        }
        return new Look(hair, head, top, bottom, bare, unhuman, cape, false);
    }

    // ---- Saying it -----------------------------------------------------------------------------------

    /**
     * The words for a mortal's line: "brown hair, a dark cyan top, blue trousers". Armor covers the
     * body: a naked skin under a chestplate and leggings is "naked under their armor".
     */
    public static String phrase(Look look, boolean chestArmor, boolean legArmor) {
        if (look == null) return null;
        List<String> parts = new ArrayList<>();
        if (look.defaultSkin()) {
            parts.add("a default skin, no look of their own");
            if (look.cape()) parts.add("a cape");
            return String.join(", ", parts);
        }
        if (look.unhuman() != null) parts.add("an unhuman " + look.unhuman() + " face");
        if (look.head() != null) parts.add(look.head());
        if (look.hair() != null) parts.add(look.hair());
        boolean chestBare = (look.bare() == Bare.CHEST || look.bare() == Bare.UNDERWEAR || look.bare() == Bare.NAKED)
                && !chestArmor;
        boolean legsBare = (look.bare() == Bare.LEGS || look.bare() == Bare.UNDERWEAR || look.bare() == Bare.NAKED)
                && !legArmor;
        if (look.top() != null && !chestArmor) parts.add(look.top());
        if (look.bottom() != null && !legArmor) parts.add(look.bottom());
        if (chestBare && legsBare) {
            parts.add(look.bare() == Bare.NAKED ? "NAKED: bare skin from chest to feet, nothing covering them"
                    : "nearly naked: bare skin but for a strip at the hips");
        } else if (chestBare) {
            parts.add(look.bare() == Bare.CHEST ? "bare-chested" : "bare-chested (bare below too, under the armor)");
        } else if (legsBare) {
            parts.add(look.bare() == Bare.LEGS ? "bare legs" : "bare legs and hips below the armor");
        } else if ((look.bare() == Bare.NAKED || look.bare() == Bare.UNDERWEAR) && chestArmor && legArmor) {
            parts.add("naked under their armor");
        }
        if (look.cape()) parts.add("a cape");
        return parts.isEmpty() ? "nothing remarkable" : String.join(", ", parts);
    }

    /** True when the look shows a mortal naked or nearly so before the world (no armor over it). */
    public static boolean exposed(Look look, boolean chestArmor, boolean legArmor) {
        return look != null && !look.defaultSkin() && !chestArmor && !legArmor
                && (look.bare() == Bare.NAKED || look.bare() == Bare.UNDERWEAR);
    }

    // ---- Colours -------------------------------------------------------------------------------------

    /** A plain name for a colour: "black", "dark blue", "pale pink"... */
    public static String colorName(int rgb) {
        float[] hsv = hsv(rgb);
        float h = hsv[0], s = hsv[1], v = hsv[2];
        if (v < 0.16f) return "black";
        if (s < 0.14f) return v < 0.38f ? "dark grey" : v < 0.72f ? "grey" : "white";
        String base;
        if (h < 14 || h >= 345) base = "red";
        else if (h < 40) base = (v < 0.62f || s < 0.45f) ? "brown" : "orange";
        else if (h < 68) base = v < 0.55f ? "olive" : (s < 0.4f ? "beige" : "yellow");
        else if (h < 160) base = "green";
        else if (h < 200) base = "cyan";
        else if (h < 255) base = "blue";
        else if (h < 290) base = "purple";
        else base = s < 0.55f && v > 0.6f ? "pink" : "magenta";
        if (base.equals("red") && s < 0.5f && v > 0.65f) base = "pink";
        if (base.equals("brown") && v < 0.35f) return "dark brown";
        if (base.equals("brown") && v > 0.75f && s < 0.4f) return "tan";
        if (v < 0.42f && !base.equals("olive")) return "dark " + base;
        if (v > 0.85f && s < 0.4f && !base.equals("beige") && !base.equals("pink")) return "pale " + base;
        return base;
    }

    /** Hair has its own words: blond, ginger. */
    static String hairColor(String name) {
        return switch (name) {
            case "yellow", "beige", "pale yellow", "tan" -> "blond";
            case "orange" -> "ginger";
            default -> name;
        };
    }

    static String article(String color) {
        char c = color.charAt(0);
        return ("aeiou".indexOf(c) >= 0 ? "an " : "a ") + color;
    }

    /** A tone a human face could have (any shade from pale to deep brown), not a creature's green or blue. */
    static boolean humanTone(int rgb) {
        float[] hsv = hsv(rgb);
        boolean warm = hsv[0] <= 52 || hsv[0] >= 340;
        return warm && hsv[1] >= 0.1f && hsv[1] <= 0.85f && hsv[2] >= 0.14f;
    }

    /** The same colour as the reference skin, or a lighter or darker shade of it. */
    static boolean skinLike(int rgb, int ref) {
        float[] a = hsv(rgb), b = hsv(ref);
        float dh = Math.abs(a[0] - b[0]);
        dh = Math.min(dh, 360 - dh);
        if (b[1] < 0.2f || a[1] < 0.2f) dh = Math.min(dh, 10); // hue means little near grey
        float ratio = b[2] <= 0 ? 0 : a[2] / b[2];
        return dh <= 14 && Math.abs(a[1] - b[1]) <= 0.22f && ratio >= 0.62f && ratio <= 1.4f;
    }

    static float[] hsv(int rgb) {
        float[] out = new float[3];
        java.awt.Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, out);
        out[0] *= 360;
        return out;
    }

    // ---- The skin's layout ---------------------------------------------------------------------------

    /** A skin and where each part's front face sits in it (and its second layer). */
    static final class Skin {
        /** A front face: base at (x, y), second layer at (ox, oy), w x h texels. */
        record Face(int x, int y, int ox, int oy, int w, int h) {
        }

        static final Face HEAD = new Face(8, 8, 40, 8, 8, 8);
        static final Face HEAD_TOP = new Face(8, 0, 40, 0, 8, 8);
        static final Face BODY = new Face(20, 20, 20, 36, 8, 12);
        static final Face RIGHT_LEG = new Face(4, 20, 4, 36, 4, 12);
        static final Face LEFT_LEG = new Face(20, 52, 4, 52, 4, 12);

        final BufferedImage img;
        final boolean overlay; // a 64x64 skin with a second layer

        Skin(BufferedImage img) {
            this.img = img;
            this.overlay = img.getHeight() == 64;
        }

        /** The colour shown at a texel of a face: the second layer where it is opaque, else the base. */
        int visible(Face f, int dx, int dy) {
            Face g = f == LEFT_LEG && !overlay ? RIGHT_LEG : f; // old skins mirror the left leg
            if (overlay || g == HEAD || g == HEAD_TOP) {
                int o = img.getRGB(g.ox() + dx, g.oy() + dy);
                if ((o >>> 24) >= 128) return o & 0xFFFFFF;
            }
            int b = img.getRGB(g.x() + dx, g.y() + dy);
            return (b >>> 24) >= 128 ? b & 0xFFFFFF : -1;
        }

        int base(Face f, int dx, int dy) {
            int b = img.getRGB(f.x() + dx, f.y() + dy);
            return (b >>> 24) >= 128 ? b & 0xFFFFFF : -1;
        }

        /** The character's own skin: the most common colour of the face (base layer, under any mask). */
        int skinTone() {
            Map<Integer, List<Integer>> buckets = new HashMap<>();
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int c = base(HEAD, x, y);
                    if (c < 0) continue;
                    buckets.computeIfAbsent(quant(c), k -> new ArrayList<>()).add(c);
                }
            }
            List<Integer> best = null;
            for (List<Integer> l : buckets.values()) if (best == null || l.size() > best.size()) best = l;
            if (best == null || best.size() < 6) return 0;
            return average(best);
        }

        /** The share of a part of a face (x, y, w, h within it) that is bare skin. */
        double share(int ref, Face f, int x0, int y0, int w, int h) {
            int bare = 0, all = 0;
            for (int y = y0; y < y0 + h && y < f.h(); y++) {
                for (int x = x0; x < x0 + w && x < f.w(); x++) {
                    int c = visible(f, x, y);
                    if (c < 0) continue;
                    all++;
                    if (skinLike(c, ref)) bare++;
                }
            }
            return all == 0 ? 0 : bare / (double) all;
        }

        /** The most common colour of a part of a face, or 0 when it is empty. */
        int dominant(Face f, int x0, int y0, int w, int h) {
            Map<Integer, List<Integer>> buckets = new HashMap<>();
            for (int y = y0; y < y0 + h; y++) {
                for (int x = x0; x < x0 + w; x++) {
                    int c = visible(f, x, y);
                    if (c >= 0) buckets.computeIfAbsent(quant(c), k -> new ArrayList<>()).add(c);
                }
            }
            List<Integer> best = null;
            for (List<Integer> l : buckets.values()) if (best == null || l.size() > best.size()) best = l;
            return best == null ? 0 : Math.max(1, average(best));
        }

        /** What is worn on a part: "red" or "red and white", skipping bare skin; null when nothing is. */
        String colors(int ref, boolean human, Face f, int x0, int y0, int w, int h) {
            Map<String, Integer> names = new HashMap<>();
            int worn = 0;
            for (int y = y0; y < y0 + h && y < f.h(); y++) {
                for (int x = x0; x < x0 + w && x < f.w(); x++) {
                    int c = visible(f, x, y);
                    if (c < 0 || (human && skinLike(c, ref))) continue;
                    worn++;
                    names.merge(colorName(c), 1, Integer::sum);
                }
            }
            if (worn < w * h / 4) return null;
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(names.entrySet());
            sorted.sort((a, b) -> b.getValue() - a.getValue());
            String first = sorted.get(0).getKey();
            if (sorted.size() > 1 && sorted.get(1).getValue() >= worn * 0.3) {
                return first + " and " + sorted.get(1).getKey();
            }
            return first;
        }

        /** The share of the hat layer's outer ring (the face's edge) that is opaque. */
        double overlayRing() {
            int on = 0, all = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    if (x != 0 && x != 7 && y != 0) continue;
                    all++;
                    if ((img.getRGB(40 + x, 8 + y) >>> 24) >= 128) on++;
                }
            }
            return on / (double) all;
        }

        /** The share of the hat layer over the middle of the face (eyes and mouth) that is opaque. */
        double overlayCentre() {
            int on = 0;
            for (int y = 2; y < 7; y++) {
                for (int x = 2; x < 6; x++) {
                    if ((img.getRGB(40 + x, 8 + y) >>> 24) >= 128) on++;
                }
            }
            return on / 20.0;
        }

        double overlayTop() {
            int on = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    if ((img.getRGB(40 + x, y) >>> 24) >= 128) on++;
                }
            }
            return on / 64.0;
        }

        /** The hat layer's most common colour (front and top), or null when it is empty. */
        String overlayColor() {
            Map<Integer, List<Integer>> buckets = new HashMap<>();
            for (int y = 0; y < 16; y++) {
                for (int x = 40; x < 48; x++) {
                    int o = img.getRGB(x, y);
                    if ((o >>> 24) >= 128) buckets.computeIfAbsent(quant(o & 0xFFFFFF), k -> new ArrayList<>()).add(o & 0xFFFFFF);
                }
            }
            List<Integer> best = null;
            for (List<Integer> l : buckets.values()) if (best == null || l.size() > best.size()) best = l;
            return best == null ? null : colorName(average(best));
        }

        static int quant(int c) {
            return ((c >> 21) & 7) << 6 | ((c >> 13) & 7) << 3 | ((c >> 5) & 7); // 8 steps a channel: shading stays together
        }

        static int average(List<Integer> colors) {
            long r = 0, g = 0, b = 0;
            for (int c : colors) {
                r += (c >> 16) & 255;
                g += (c >> 8) & 255;
                b += c & 255;
            }
            int n = colors.size();
            return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
        }
    }
}
