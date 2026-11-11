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
            String[] powers) {
        public Sample(String name, String kind, String note, String[] legend, String[] rows) {
            this(name, kind, note, legend, rows, null, new String[0]);
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

    /** Every example, in the order they are shown. */
    public static final List<Sample> ALL = List.of(BLADE, STAFF, AMULET, TOME, CROWN, VIAL, WISP, KINDLED);
}
