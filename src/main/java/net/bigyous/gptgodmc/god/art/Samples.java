package net.bigyous.gptgodmc.god.art;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Worked examples of sprites, written by hand the way an artist writes them. They show the artist model
 * what good input looks like (they are quoted in its instructions), they are the offline previews, and the
 * tests paint them. Each is small on purpose: a clear shape, two to four materials, one accent that shines.
 */
public final class Samples {
    private Samples() {
    }

    /**
     * One example: what it is called, what kind of thing it is, why it is drawn this way, and the sprite;
     * for a special relic also its second (awake) form and what it is imbued with.
     */
    public record Sample(String name, String kind, String note, String[] legend, String[] rows, String[] altRows,
            String[] powers, JsonArray shape) {
        public Sample(String name, String kind, String note, String[] legend, String[] rows) {
            this(name, kind, note, legend, rows, null, new String[0], null);
        }

        public Sample(String name, String kind, String note, String[] legend, String[] rows, String[] altRows,
                String[] powers) {
            this(name, kind, note, legend, rows, altRows, powers, null);
        }

        public JsonObject json() {
            JsonObject o = new JsonObject();
            if (rows[0].length() == rows.length) o.addProperty("size", rows.length);
            else {
                o.addProperty("width", rows[0].length());
                o.addProperty("height", rows.length);
            }
            JsonObject key = new JsonObject();
            for (int i = 0; i + 1 < legend.length; i += 2) key.addProperty(legend[i], legend[i + 1]);
            o.add("legend", key);
            JsonArray r = new JsonArray();
            for (String row : rows) r.add(row);
            o.add("rows", r);
            if (altRows != null) {
                JsonArray alt = new JsonArray();
                for (String row : altRows) alt.add(row);
                o.add("alt_rows", alt);
            }
            if (shape != null) o.add("shape", shape.deepCopy());
            return o;
        }

        public Sprite sprite() {
            try {
                return Sprite.parse(json());
            } catch (Sprite.Flaw e) {
                throw new IllegalStateException(name + ": " + e.getMessage(), e);
            }
        }
    }

    public static final Sample BLADE = new Sample("Dawnbreaker", "blade",
            "Held things lie on the diagonal, grip at the bottom left, point at the top right. The blade is three "
                    + "texels wide so the painter can give it a lit edge, a flat and a dark edge.",
            new String[] { "s", "steel", "g", "gold", "l", "leather", "r", "gem #d0203c" },
            new String[] {
                    "..............ss",
                    ".............sss",
                    "............sss.",
                    "...........sss..",
                    "..........sss...",
                    ".........sss....",
                    "........sss.....",
                    ".......sss......",
                    "..g...sss.......",
                    "..gg.sss........",
                    "...ggss.........",
                    "....ggg.........",
                    "...llggg........",
                    "..ll...gg.......",
                    ".rl.............",
                    "rr.............." });

    public static final Sample STAFF = new Sample("Sunspire", "staff",
            "A plain shaft keeps the eye on the head: a ring of gold round a light. The one band of gold on the "
                    + "shaft repeats the head's metal lower down.",
            new String[] { "w", "wood", "g", "gold", "o", "glow #ffd86a" },
            new String[] {
                    "...........ggg..",
                    "..........gooog.",
                    "..........gooog.",
                    "..........gooog.",
                    "..........gggg..",
                    ".........ww.....",
                    "........ww......",
                    ".......ww.......",
                    "......ww........",
                    ".....ww.........",
                    "....gw..........",
                    "...ww...........",
                    "..ww............",
                    ".ww.............",
                    "ww..............",
                    "w..............." });

    public static final Sample AMULET = new Sample("Heart of the Tide", "trinket",
            "A thin chain and a heavy stone: the contrast in weight is the design. The stone is one big patch so "
                    + "its facets have room.",
            new String[] { "g", "gold", "b", "gem #2aa6e0" },
            new String[] {
                    "....gggggggg....",
                    "...g........g...",
                    "..g..........g..",
                    "..g..........g..",
                    "..g..........g..",
                    "...g........g...",
                    "....g......g....",
                    ".....g....g.....",
                    "......gggg......",
                    ".....gbbbbg.....",
                    "....gbbbbbbg....",
                    "....gbbbbbbg....",
                    "....gbbbbbbg....",
                    ".....gbbbbg.....",
                    "......gbbg......",
                    ".......gg......." });

    public static final Sample TOME = new Sample("Codex of Ash", "tome",
            "A book is a flat rectangle, so it needs detail to read: gold corners, the edge of the pages down one "
                    + "side, and a sign on the cover that burns.",
            new String[] { "l", "cloth #7a2236", "p", "bone", "g", "gold", "r", "glow #ffb060" },
            new String[] {
                    "................",
                    "...llllllllll...",
                    "..glllllllllgp..",
                    "..llllllllllpp..",
                    "..llllrrllllpp..",
                    "..lllrllrlllpp..",
                    "..lllrllrlllpp..",
                    "..llllrrllllpp..",
                    "..lllllrllllpp..",
                    "..lllllrllllpp..",
                    "..llllrrrlllpp..",
                    "..llllllllllpp..",
                    "..glllllllllgp..",
                    "...lllllllllpp..",
                    "....ppppppppp...",
                    "................" });

    public static final Sample CROWN = new Sample("Crown of the First King", "trinket",
            "Three points, the middle one tallest, and stones set where the eye expects them. Symmetry suits a "
                    + "crown; most things are better a little uneven.",
            new String[] { "g", "gold", "r", "gem #d0203c", "b", "gem #2aa6e0" },
            new String[] {
                    "................",
                    "................",
                    "................",
                    "..g....gg....g..",
                    ".ggg..gggg..ggg.",
                    ".grg..gbbg..grg.",
                    ".ggg.gggggg.ggg.",
                    ".gggggggggggggg.",
                    ".gggggggggggggg.",
                    ".grggbggrggbggr.",
                    ".gggggggggggggg.",
                    "..gggggggggggg..",
                    "................",
                    "................",
                    "................",
                    "................" });

    public static final Sample VIAL = new Sample("Vial of Starlight", "trinket",
            "Glass is a pale gem; what is inside is light. The cork is the only dull thing, which makes the rest "
                    + "look brighter.",
            new String[] { "c", "wood", "a", "gem #6f9fc8", "q", "glow #9ff3ff" },
            new String[] {
                    "................",
                    "......cccc......",
                    "......cccc......",
                    "......aaaa......",
                    "......aaaa......",
                    ".....aaaaaa.....",
                    "....aaqqqqaa....",
                    "...aaqqqqqqaa...",
                    "...aqqqqqqqqa...",
                    "...aqqqqqqqqa...",
                    "...aqqqqqqqqa...",
                    "...aaqqqqqqaa...",
                    "....aaqqqqaa....",
                    ".....aaaaaa.....",
                    "................",
                    "................" });

    public static final Sample WISP = new Sample("Lantern Wisp", "spirit",
            "A spirit is a face first: two dark eyes in a body of light, then a tail that frays. Nothing else.",
            new String[] { "s", "soul", "e", "#05040a deep" },
            new String[] {
                    "........................",
                    "........................",
                    ".........ssssss.........",
                    ".......ssssssssss.......",
                    "......ssssssssssss......",
                    ".....ssssssssssssss.....",
                    ".....ssssssssssssss.....",
                    "....sssseesssseessss....",
                    "....sssseesssseessss....",
                    "....sssseesssseessss....",
                    "....ssssssssssssssss....",
                    "....ssssssssssssssss....",
                    ".....ssssssssssssss.....",
                    ".....ssssssssssssss.....",
                    "......ssssssssssss......",
                    "......sssss.sssss.ss....",
                    ".......sss...sss...s....",
                    ".......ss.....ss........",
                    "........s.....s.........",
                    "........................",
                    "........................",
                    "........................",
                    "........................",
                    "........................" });

    private static final String[] HILT = {
            ".......ggggg............",
            "......ggggg.............",
            "......srs...............",
            ".....lll................",
            "....lll.................",
            "...sss..................",
            "..ggg...................",
            ".ggg....................",
            ".gg.....................",
            "........................" };

    private static String[] kindled(boolean lit) {
        String[] rows = new String[24];
        for (int y = 0; y < 14; y++) {
            StringBuilder sb = new StringBuilder("........................");
            // The blade: three texels across, one step left for every row down, its tip cut by the canvas.
            for (int x = 22 - y; lit && x <= 24 - y; x++) {
                if (x < 24) sb.setCharAt(x, 'b');
            }
            rows[y] = sb.toString();
        }
        System.arraycopy(HILT, 0, rows, 14, HILT.length);
        return rows;
    }

    /**
     * A special relic: sculpted, with two forms. Asleep it is only a hilt; awake, a blade of light stands
     * in it. The hilt is on the same texels in both, so the blade seems to leap from it in the hand.
     */
    public static final Sample KINDLED = new Sample("Skysplinter", "blade",
            "A special relic, sculpted and with two forms. \"rows\" is the hilt alone, small in its corner; "
                    + "\"alt_rows\" adds the blade of light, and the hilt does not move by a texel. The blade is a "
                    + "narrow stroke on the diagonal, so it keeps the usual thickness; its pale colour with a white "
                    + "heart says light. The collar and pommel are thicker than the grip, and the switch stands "
                    + "proud of it.",
            new String[] { "b", "glow #7fe8ff", "g", "gold thick 5", "s", "steel thick 3", "l",
                    "leather thick 3", "r", "gem #7fe8ff thick 5" },
            kindled(false), kindled(true),
            new String[] { "on_use: toggle", "held: light 12 | when on", "held: sound block.beacon.ambient 1.8 | when on",
                    "stat: attack_damage 3 | when off", "stat: attack_damage 9 | when on",
                    "stat: attack_speed 1.8 | when on", "on_hit: ignite 3 | when on", "on_swing: slash 2 | when on",
                    "on_use: wave 6 | cooldown 8 | when on" });

    /** A reaper's scythe as a picture: a long dark shaft on the diagonal, a steel crescent at its head, a gold collar and a gem. */
    private static String[] scytheRows() {
        int n = 24;
        char[][] g = new char[n][n];
        for (char[] row : g) java.util.Arrays.fill(row, '.');
        for (int y = 3; y <= 22; y++) {
            g[y][22 - y] = 'w';
            g[y][23 - y] = 'w';
        }
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 21; x++) {
                double d = Math.hypot(x - 14.5, y - 12.5);
                if (d >= 9.6 && d <= 11.8 && g[y][x] == '.') g[y][x] = 's';
            }
        }
        g[2][19] = 'g';
        g[2][20] = 'g';
        g[3][20] = 'r';
        g[3][21] = 'g';
        String[] rows = new String[n];
        for (int y = 0; y < n; y++) rows[y] = new String(g[y]);
        return rows;
    }

    /** The same scythe as a true shape: a turned shaft, a pommel, a gold collar and a curved, tapering blade. */
    private static JsonArray scytheShape() {
        JsonArray parts = new JsonArray();
        // Blocks first, as Minecraft builds: the shaft a long box laid on the diagonal (roll -45), a cube for the
        // pommel and a broader box for the collar. Only the blade, whose curve is what makes it a scythe, is round.
        parts.add(part("{\"form\":\"box\",\"size\":[1.1,18.0,1.1],\"offset\":[7.46,7.16,0],\"roll\":-45,"
                + "\"material\":\"darkwood\"}"));
        parts.add(part("{\"form\":\"box\",\"size\":1.7,\"offset\":[1.0,0.7,0],\"roll\":-45,\"material\":\"gold\"}"));
        parts.add(part("{\"form\":\"box\",\"size\":[2.1,1.4,2.1],\"offset\":[13.6,13.3,0],\"roll\":-45,"
                + "\"material\":\"gold\"}"));
        // The blade: a thin crescent from the head of the shaft round to the left, its edge on the inside. It is
        // built in short pieces: the scenes' shading lights the side of a part facing away from its own middle,
        // and only a short piece of a curved sheet has its middle inside it.
        int steps = 10, per = 2;
        double cx = 8.6, cy = 9.0;
        double[][] out = new double[steps + 1][], in = new double[steps + 1][];
        double[] half = new double[steps + 1];
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps, a = Math.toRadians(42 + 160 * t);
            double ro = 6.7, ri = 4.9 + (6.6 - 4.9) * t;
            out[i] = new double[] { cx + Math.cos(a) * ro, cy + Math.sin(a) * ro };
            in[i] = new double[] { cx + Math.cos(a) * ri, cy + Math.sin(a) * ri };
            half[i] = 0.32 - 0.26 * t;
        }
        for (int from = 0; from < steps; from += per) {
            JsonArray quads = new JsonArray();
            for (int i = from; i < Math.min(steps, from + per); i++) {
                double h0 = half[i], h1 = half[i + 1];
                quads.add(quad(out[i], h0, out[i + 1], h1, in[i + 1], h1 * 0.2, in[i], h0 * 0.2));
                quads.add(quad(in[i], -h0 * 0.2, in[i + 1], -h1 * 0.2, out[i + 1], -h1, out[i], -h0));
                quads.add(quad(out[i], -h0, out[i + 1], -h1, out[i + 1], h1, out[i], h0));
                quads.add(quad(in[i], h0 * 0.2, in[i + 1], h1 * 0.2, in[i + 1], -h1 * 0.2, in[i], -h0 * 0.2));
            }
            if (from == 0) quads.add(quad(out[0], half[0], in[0], half[0] * 0.2, in[0], -half[0] * 0.2, out[0], -half[0]));
            JsonObject blade = new JsonObject();
            blade.addProperty("form", "quads");
            blade.add("quads", quads);
            blade.addProperty("material", "steel");
            parts.add(blade);
        }
        return parts;
    }

    private static JsonObject part(String json) {
        return com.google.gson.JsonParser.parseString(json).getAsJsonObject();
    }

    private static JsonArray quad(double[] a, double za, double[] b, double zb, double[] c, double zc, double[] d, double zd) {
        JsonArray q = new JsonArray();
        for (double[] p : new double[][] { { a[0], a[1], za }, { b[0], b[1], zb }, { c[0], c[1], zc }, { d[0], d[1], zd } }) {
            JsonArray v = new JsonArray();
            for (double x : p) v.add(Math.round(x * 100) / 100.0);
            q.add(v);
        }
        return q;
    }

    /**
     * A special relic with a true shape: its picture is what everyone sees in an inventory and what players
     * without the client mod hold; with the mod it is a real scythe, a round shaft and a curved blade.
     */
    public static final Sample SCYTHE = new Sample("Gravecall", "staff",
            "A special relic with a true shape (\"shape\"): the picture traced in parts, built as Minecraft builds. "
                    + "The shaft is a long box laid on the diagonal (roll -45), the pommel a cube, the collar a broader "
                    + "box; only the blade curves, because the curve is what makes it a scythe: quads, a thin crescent "
                    + "of steel thickest at the collar and fading to an edge and a point, in five short pieces.",
            new String[] { "w", "darkwood", "s", "steel", "g", "gold", "r", "gem #7a5cff" }, scytheRows(), null,
            new String[] { "stat: attack_damage 9", "stat: attack_speed 1.0", "on_hit: potion wither 1 4",
                    "on_swing: trail #9aa3b5" },
            scytheShape());

    /** Every example, in the order they are shown. */
    public static final List<Sample> ALL = List.of(BLADE, STAFF, AMULET, TOME, CROWN, VIAL, WISP, KINDLED, SCYTHE);
}
