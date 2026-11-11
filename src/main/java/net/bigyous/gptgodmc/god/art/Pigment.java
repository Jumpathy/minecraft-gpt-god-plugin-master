package net.bigyous.gptgodmc.god.art;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a texel of a sprite is made of. A pigment is a material with a five-tone ramp (deep shadow,
 * shadow, base, light, glint) and a family that decides how the painter shades and animates it: metal
 * takes a hard highlight and a travelling glint, a gem has facets and a sparkle, flame flickers, glow
 * and soul-fire are lit from within. Named materials have hand-picked ramps; the tinted ones (cloth,
 * gem, glow, paint...) build theirs from any color, shifting hue toward violet in shadow and toward
 * yellow in light, the way a painter would, so no color is ever shaded with plain black and white.
 */
public enum Pigment {
    GOLD(Family.METAL, 0x5A3608, 0xA8741E, 0xE8B94A, 0xFFE08A, 0xFFF8D8),
    SILVER(Family.METAL, 0x4A4E5E, 0x8A90A4, 0xC9CEDA, 0xEEF1F6, 0xFFFFFF),
    STEEL(Family.METAL, 0x2E3440, 0x5A6478, 0x9AA6B8, 0xD0D8E4, 0xFFFFFF),
    IRON(Family.METAL, 0x1E2028, 0x3A3E4A, 0x62687A, 0x8E96A8, 0xC8D0DC),
    BRONZE(Family.METAL, 0x3E2410, 0x7A4A1E, 0xB87A34, 0xE0A858, 0xFFE0A0),
    COPPER(Family.METAL, 0x4A1E10, 0x8E4424, 0xC8703C, 0xF0A070, 0xFFD8B8),
    OBSIDIAN(Family.STONE, 0x05040A, 0x120E1E, 0x241A3A, 0x4A3A76, 0x9A86D8),
    STONE(Family.STONE, 0x2E2C34, 0x55525E, 0x8A8794, 0xB4B1BC, 0xDAD8E0),
    MARBLE(Family.STONE, 0x7D788C, 0xB8B4C2, 0xE9E6DF, 0xF7F5F0, 0xFFFFFF),
    BONE(Family.STONE, 0x5E5646, 0x9A9078, 0xD8D2BE, 0xEFEAD8, 0xFFFDF2),
    WOOD(Family.WOOD, 0x2A1A0E, 0x4E3218, 0x7A5228, 0xA8763C, 0xD0A060),
    DARKWOOD(Family.WOOD, 0x140C08, 0x2A1A12, 0x44291C, 0x64402A, 0x8E6240),
    LEATHER(Family.WOOD, 0x2E1608, 0x5A3014, 0x8A5026, 0xB4743E, 0xD89E62),
    FLAME(Family.FLAME, 0x5A120C, 0xC2300E, 0xFF8A1A, 0xFFE27A, 0xFFFBE0),
    FROST(Family.GEM, 0x2A4E7A, 0x5E9AD0, 0xA8DAF8, 0xDDF3FF, 0xFFFFFF),
    VOID(Family.VOID, 0x030208, 0x0B0914, 0x191430, 0x4A3486, 0xD8EEFF),
    SOUL(Family.GLOW, 0x0E3A4A, 0x1E8AAE, 0x38C8F0, 0xBFF6FF, 0xFFFFFF),
    CLOTH(Family.CLOTH, 0x6A3AB8),
    GEM(Family.GEM, 0xC02050),
    GLOW(Family.GLOW, 0xFFE9A0),
    HIDE(Family.HIDE, 0xC8A27C),
    PAINT(Family.STONE, 0x808080);

    /** How the painter treats a pigment. */
    public enum Family {
        METAL, STONE, WOOD, CLOTH, GEM, GLOW, FLAME, VOID, HIDE
    }

    /** The tones of a ramp, darkest first. */
    public static final int DEEP = 0, DARK = 1, BASE = 2, LIGHT = 3, BRIGHT = 4;

    public final Family family;
    private final int[] fixed;
    private final int color;
    private static final Map<Long, int[]> RAMPS = new ConcurrentHashMap<>();

    Pigment(Family family, int deep, int dark, int base, int light, int bright) {
        this.family = family;
        this.fixed = new int[] { deep, dark, base, light, bright };
        this.color = base;
    }

    Pigment(Family family, int color) {
        this.family = family;
        this.fixed = null;
        this.color = color;
    }

    /** True when it takes a color of the artist's choosing ("cloth #5a2e9e"). */
    public boolean tinted() {
        return fixed == null;
    }

    /** True when it shines by itself: drawn at full brightness whatever the light. */
    public boolean emissive() {
        return family == Family.GLOW || family == Family.FLAME;
    }

    /** Its five tones; a tinted pigment builds them from the color given (negative = its own default). */
    public int[] ramp(int rgb) {
        if (fixed != null) return fixed;
        int base = rgb < 0 ? color : rgb & 0xFFFFFF;
        return RAMPS.computeIfAbsent(((long) ordinal() << 32) | base, k -> build(base, family));
    }

    /**
     * A ramp from one color. Value spreads wider for hard, shiny things than for soft ones; shadows turn
     * toward violet and gain a little saturation, lights turn toward yellow and lose it; a tone pushed
     * past full brightness pales toward white instead of clipping.
     */
    static int[] build(int rgb, Family family) {
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
        double[] value = switch (family) {
            case METAL -> new double[] { 0.30, 0.62, 1, 1.24, 1.55 };
            case GEM -> new double[] { 0.30, 0.60, 1, 1.32, 1.75 };
            case GLOW, FLAME -> new double[] { 0.50, 0.78, 1, 1.18, 1.45 };
            case CLOTH -> new double[] { 0.40, 0.68, 1, 1.17, 1.36 };
            default -> new double[] { 0.42, 0.70, 1, 1.15, 1.32 };
        };
        double[] sat = { 1.18, 1.09, 1, 0.84, 0.5 };
        double[] cool = { 0.16, 0.08, 0, 0, 0 }, warm = { 0, 0, 0, 0.07, 0.12 };
        int[] out = new int[5];
        for (int i = 0; i < 5; i++) {
            double h = toward(toward(hsb[0], 260 / 360.0, cool[i]), 52 / 360.0, warm[i]);
            double v = hsb[2] * value[i], s = hsb[1] * sat[i];
            if (v > 1) {
                s *= Math.max(0, 1 - (v - 1) * 1.6);
                v = 1;
            }
            out[i] = Color.HSBtoRGB((float) h, (float) Math.max(0, Math.min(1, s)), (float) Math.max(0, v)) & 0xFFFFFF;
        }
        return out;
    }

    /** Moves a hue (0..1) a fraction of the way to another, the short way round the wheel. */
    private static double toward(double hue, double target, double fraction) {
        double d = target - hue;
        if (d > 0.5) d -= 1;
        if (d < -0.5) d += 1;
        double h = hue + d * fraction;
        return h - Math.floor(h);
    }

    /** The names an artist may use, with the pigment each means. */
    private static final Map<String, Pigment> NAMES = Map.ofEntries(Map.entry("gold", GOLD), Map.entry("silver", SILVER),
            Map.entry("steel", STEEL), Map.entry("iron", IRON), Map.entry("bronze", BRONZE), Map.entry("copper", COPPER),
            Map.entry("obsidian", OBSIDIAN), Map.entry("stone", STONE), Map.entry("marble", MARBLE),
            Map.entry("bone", BONE), Map.entry("ivory", BONE), Map.entry("wood", WOOD), Map.entry("darkwood", DARKWOOD),
            Map.entry("ebony", DARKWOOD), Map.entry("leather", LEATHER), Map.entry("flame", FLAME),
            Map.entry("fire", FLAME), Map.entry("ember", FLAME), Map.entry("frost", FROST), Map.entry("ice", FROST),
            Map.entry("void", VOID), Map.entry("night", VOID), Map.entry("soul", SOUL), Map.entry("cloth", CLOTH),
            Map.entry("silk", CLOTH), Map.entry("gem", GEM), Map.entry("crystal", GEM), Map.entry("glass", GEM),
            Map.entry("glow", GLOW), Map.entry("hide", HIDE), Map.entry("skin", HIDE),
            Map.entry("fur", HIDE), Map.entry("scale", HIDE), Map.entry("feather", HIDE), Map.entry("paint", PAINT));

    public static Pigment named(String name) {
        return name == null ? null : NAMES.get(name.trim().toLowerCase(Locale.ROOT));
    }

    /** Every name, for telling an artist what exists: fixed materials first, then the ones that take a color. */
    public static List<String> names(boolean tinted) {
        List<String> out = new ArrayList<>();
        NAMES.forEach((name, p) -> {
            if (p.tinted() == tinted) out.add(name);
        });
        out.sort(null);
        return out;
    }
}
