package net.bigyous.gptgodmc.god.art;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A sprite as an artist writes it: not pixels with colors, but a map of what each texel is made of. A
 * legend gives every letter a material ("G": "gold", "r": "gem #c02040", "*": "glow #9ff3ff"); the rows
 * place the letters; {@link SpritePainter} then does what a pixel artist's hand would do: shades each
 * shape from one light, darkens its far edge, lays glints on metal and facets on gems, and animates what
 * burns or shines. The artist decides the shape and the materials, which is the part that needs an eye;
 * the craft that is the same every time is done the same way every time.
 *
 * <p>A sprite is also a solid thing. Every material has a thickness, in texels, front to back ("steel
 * thick 3"), and may be rounded ("gold thick 5 round": thickest along its middle, like a rod or a ball),
 * so the same drawing that reads as a sprite from the front is a small sculpture from the side. And it
 * may have a second form, "alt_rows" on the same canvas with the same legend: the thing awakened (a hilt
 * with its blade lit, a book open, an eye unlidded), for relics that change. Pure.
 */
public record Sprite(int w, int h, char[][] cells, Map<Character, Ink> legend, char[][] accents, Light light,
        Outline outline, char[][] altCells, char[][] altAccents) {

    /** Smallest and largest canvases. Items are square; a spirit may be taller than wide. */
    public static final int MIN = 8, MAX_W = 32, MAX_H = 32;
    /** The thickest a material may be, in texels. */
    public static final int MAX_THICK = 12;
    /** The letter of an empty texel (a space means the same). */
    public static final char EMPTY = '.';

    /** Where the light comes from. */
    public enum Light {
        TOP_LEFT(1, 1), TOP(0, 1), TOP_RIGHT(-1, 1);

        /** The way light travels across the canvas (x right, y down). */
        public final int dx, dy;

        Light(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    /** soft: the far edge of every shape is shaded darker. dark: a dark line is also drawn round the whole. */
    public enum Outline {
        SOFT, DARK, NONE
    }

    /**
     * One legend entry: a pigment, its color when it takes one (else -1), a tone when the artist fixes it
     * (0 deep .. 4 bright; -1 lets the painter shade), how thick it is front to back in texels (-1 = the
     * canvas's usual thickness; 0 = a film with no thickness, for light), and whether it is rounded.
     */
    public record Ink(Pigment pigment, int color, int tone, int thick, boolean round) {
        public int[] ramp() {
            return pigment.ramp(color);
        }
    }

    /** What is wrong with a sprite, said to the artist who wrote it. */
    public static final class Flaw extends Exception {
        private static final long serialVersionUID = 1L;

        public Flaw(String message) {
            super(message);
        }
    }

    public Ink at(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return null;
        char c = cells[y][x];
        return c == 0 ? null : legend.get(c);
    }

    public char accent(int x, int y) {
        return accents == null ? 0 : accents[y][x];
    }

    /** How thick anything is here unless its legend entry says otherwise: one texel at 16, two at 32, as vanilla items are. */
    public int usualThick() {
        return Math.max(1, Math.round(Math.max(w, h) / 16f));
    }

    /** The second form as a sprite of its own (same canvas, legend, light and outline), or null when there is none. */
    public Sprite alt() {
        return altCells == null ? null : new Sprite(w, h, altCells, legend, altAccents, light, outline, null, null);
    }

    // ---- Reading one ---------------------------------------------------------------------------------

    private static final Map<String, Integer> TONES = Map.of("deep", 0, "dark", 1, "shadow", 1, "base", 2, "mid", 2,
            "light", 3, "highlight", 3, "bright", 4, "glint", 4);

    /**
     * Reads {"size": 16, "legend": {...}, "rows": [...]} (or "width"/"height" instead of "size"), with
     * optional "accents" (rows of + lighter, - darker, * sparkle), "light", "outline", and a second form
     * in "alt_rows" (and "alt_accents").
     */
    public static Sprite parse(JsonObject o) throws Flaw {
        if (o == null) throw new Flaw("No sprite was given.");
        int w = number(o, "width", number(o, "size", 16)), h = number(o, "height", number(o, "size", w));
        if (w < MIN || w > MAX_W || h < MIN || h > MAX_H) {
            throw new Flaw("The canvas is " + w + "x" + h + "; it must be " + MIN + ".." + MAX_W + " wide and " + MIN
                    + ".." + MAX_H + " tall.");
        }
        Map<Character, Ink> legend = legend(o.get("legend"));
        char[][] cells = letters(o, "rows", w, h, legend, true);
        char[][] accents = marks(o, "accents", w, h);
        char[][] altCells = letters(o, "alt_rows", w, h, legend, false);
        char[][] altAccents = altCells == null ? null : marks(o, "alt_accents", w, h);
        return new Sprite(w, h, cells, legend, accents, light(text(o, "light")), outline(text(o, "outline")), altCells,
                altAccents);
    }

    private static boolean has(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull();
    }

    /** A grid of legend letters. */
    private static char[][] letters(JsonObject o, String name, int w, int h, Map<Character, Ink> legend, boolean required)
            throws Flaw {
        if (!required && !has(o, name)) return null;
        char[][] cells = grid(o.get(name), name, w, h, required);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                char c = cells[y][x];
                if (c != 0 && !legend.containsKey(c)) {
                    throw new Flaw("Row " + y + (name.equals("rows") ? "" : " of \"" + name + "\"") + " uses '" + c
                            + "' at column " + x + ", which the legend does not name.");
                }
            }
        }
        return cells;
    }

    /** A grid of accents, or null. */
    private static char[][] marks(JsonObject o, String name, int w, int h) throws Flaw {
        if (!has(o, name)) return null;
        char[][] accents = grid(o.get(name), name, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                char c = accents[y][x];
                if (c != 0 && "+-*".indexOf(c) < 0) {
                    throw new Flaw("Accent row " + y + " has '" + c + "'; accents are only + (lighter), - (darker), "
                            + "* (sparkle) and . (none).");
                }
            }
        }
        return accents;
    }

    private static int number(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsInt() : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString().trim().toLowerCase(Locale.ROOT) : "";
    }

    private static Light light(String s) {
        return switch (s.replace('_', '-')) {
            case "top" -> Light.TOP;
            case "top-right", "right" -> Light.TOP_RIGHT;
            default -> Light.TOP_LEFT;
        };
    }

    private static Outline outline(String s) {
        return switch (s) {
            case "dark" -> Outline.DARK;
            case "none" -> Outline.NONE;
            default -> Outline.SOFT;
        };
    }

    private static Map<Character, Ink> legend(JsonElement e) throws Flaw {
        if (e == null || !e.isJsonObject() || e.getAsJsonObject().size() == 0) {
            throw new Flaw("Give a legend: an object naming each letter's material, e.g. {\"G\": \"gold\"}.");
        }
        Map<Character, Ink> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : e.getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            if (key.length() != 1 || key.charAt(0) == EMPTY || key.charAt(0) == ' ') {
                throw new Flaw("Legend key \"" + key + "\" must be one character, and not '.' or a space (those mean "
                        + "empty).");
            }
            if (!entry.getValue().isJsonPrimitive()) throw new Flaw("Legend entry '" + key + "' must be text.");
            out.put(key.charAt(0), ink(key, entry.getValue().getAsString()));
        }
        if (out.size() > 12) throw new Flaw("The legend has " + out.size() + " entries; keep it to 12 or fewer.");
        return out;
    }

    /**
     * "gold", "gem #c02040", "cloth #5a2e9e dark", "steel thick 3", "glow #7fe8ff thick 2 round", "glow flat":
     * a material, a color, a tone, a thickness, in any order.
     */
    static Ink ink(String key, String value) throws Flaw {
        Pigment pigment = null;
        int color = -1, tone = -1, thick = -1;
        boolean round = false;
        String[] words = value.trim().toLowerCase(Locale.ROOT).split("[\\s,:]+");
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) continue;
            if (word.startsWith("#")) {
                try {
                    String hex = word.substring(1);
                    if (hex.length() == 3) {
                        hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2)
                                + hex.charAt(2);
                    }
                    if (hex.length() != 6) throw new NumberFormatException();
                    color = Integer.parseInt(hex, 16);
                } catch (NumberFormatException ex) {
                    throw new Flaw("Legend entry '" + key + "': \"" + word + "\" is not a color like #c02040.");
                }
            } else if (word.equals("thick") || word.equals("thickness")) {
                try {
                    thick = Integer.parseInt(words[++i]);
                } catch (RuntimeException ex) {
                    throw new Flaw("Legend entry '" + key + "': write a thickness as \"thick 3\" (texels, front to back).");
                }
                if (thick < 0 || thick > MAX_THICK) {
                    throw new Flaw("Legend entry '" + key + "': thickness " + thick + " is out of range (0 to " + MAX_THICK
                            + " texels).");
                }
            } else if (word.equals("flat")) {
                thick = 0;
            } else if (word.equals("round") || word.equals("rounded")) {
                round = true;
            } else if (TONES.containsKey(word)) {
                tone = TONES.get(word);
            } else if (Pigment.named(word) != null) {
                pigment = Pigment.named(word);
            } else {
                throw new Flaw("Legend entry '" + key + "': \"" + word + "\" is not a material. Materials: "
                        + String.join(", ", Pigment.names(false)) + "; with a color of your choosing: "
                        + String.join(", ", Pigment.names(true)) + ". Tones: deep, dark, base, light, bright. Form: "
                        + "thick N, round, flat.");
            }
        }
        if (pigment == null) {
            if (color < 0) throw new Flaw("Legend entry '" + key + "' names no material and no color.");
            pigment = Pigment.PAINT;
        }
        return new Ink(pigment, pigment.tinted() ? color : -1, tone, thick, round);
    }

    /** Rows of letters as a grid; short rows are padded with empty texels, long ones are an error. */
    private static char[][] grid(JsonElement e, String name, int w, int h, boolean required) throws Flaw {
        if (e == null || !e.isJsonArray()) {
            throw new Flaw("Give \"" + name + "\": " + h + " strings of " + w + " characters each.");
        }
        JsonArray rows = e.getAsJsonArray();
        if (rows.size() != h) {
            throw new Flaw("\"" + name + "\" has " + rows.size() + " rows; a " + w + "x" + h + " canvas needs exactly " + h
                    + ".");
        }
        char[][] out = new char[h][w];
        for (int y = 0; y < h; y++) {
            String row = rows.get(y).isJsonPrimitive() ? rows.get(y).getAsString() : "";
            if (row.length() > w) {
                throw new Flaw("Row " + y + " of \"" + name + "\" is " + row.length() + " characters; the canvas is " + w
                        + " wide.");
            }
            for (int x = 0; x < row.length(); x++) {
                char c = row.charAt(x);
                out[y][x] = c == EMPTY || c == ' ' ? 0 : c;
            }
        }
        return out;
    }

    /** The sprite written back as the JSON an artist would send (for storing and for examples). */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        if (w == h) o.addProperty("size", w);
        else {
            o.addProperty("width", w);
            o.addProperty("height", h);
        }
        JsonObject key = new JsonObject();
        legend.forEach((c, ink) -> key.addProperty(String.valueOf(c), describe(ink)));
        o.add("legend", key);
        o.add("rows", rows(cells));
        if (accents != null) o.add("accents", rows(accents));
        if (altCells != null) o.add("alt_rows", rows(altCells));
        if (altAccents != null) o.add("alt_accents", rows(altAccents));
        if (light != Light.TOP_LEFT) o.addProperty("light", light.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        if (outline != Outline.SOFT) o.addProperty("outline", outline.name().toLowerCase(Locale.ROOT));
        return o;
    }

    private static String describe(Ink ink) {
        StringBuilder sb = new StringBuilder(ink.pigment() == Pigment.PAINT ? "" : ink.pigment().name()
                .toLowerCase(Locale.ROOT));
        if (ink.color() >= 0) sb.append(sb.length() > 0 ? " " : "").append(String.format("#%06x", ink.color()));
        if (ink.tone() >= 0) sb.append(' ').append(List.of("deep", "dark", "base", "light", "bright").get(ink.tone()));
        if (ink.thick() == 0) sb.append(" flat");
        else if (ink.thick() > 0) sb.append(" thick ").append(ink.thick());
        if (ink.round()) sb.append(" round");
        return sb.toString();
    }

    private JsonArray rows(char[][] grid) {
        JsonArray out = new JsonArray();
        for (char[] row : grid) {
            StringBuilder sb = new StringBuilder();
            for (char c : row) sb.append(c == 0 ? EMPTY : c);
            out.add(sb.toString());
        }
        return out;
    }
}
