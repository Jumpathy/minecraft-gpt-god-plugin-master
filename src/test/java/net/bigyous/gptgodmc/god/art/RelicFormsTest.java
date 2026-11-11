package net.bigyous.gptgodmc.god.art;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.client.ModRelics;
import net.bigyous.gptgodmc.client.ModSessions;
import net.bigyous.gptgodmc.god.art.RelicPowers.Effect;
import net.bigyous.gptgodmc.god.art.RelicPowers.Power;
import net.bigyous.gptgodmc.god.art.RelicPowers.Trigger;
import net.bigyous.gptgodmc.god.art.Relics.Relic;
import net.bigyous.gptgodmc.god.art.SpritePainter.Art;

/** A relic as a solid thing (thickness, rounding, films, a second form) and what it is imbued with. */
class RelicFormsTest {
    @AfterEach
    void forgetRelics() {
        Relics.load(null);
    }

    private static JsonObject sprite(int size, Map<String, String> legend, String... rows) {
        JsonObject o = new JsonObject();
        o.addProperty("size", size);
        JsonObject key = new JsonObject();
        legend.forEach(key::addProperty);
        o.add("legend", key);
        JsonArray r = new JsonArray();
        for (String row : rows) r.add(row);
        o.add("rows", r);
        return o;
    }

    private static String[] rows(int size, Map<Integer, String> filled) {
        String[] rows = new String[size];
        Arrays.fill(rows, ".".repeat(size));
        filled.forEach((y, row) -> rows[y] = row + ".".repeat(size - row.length()));
        return rows;
    }

    // ---- Form ---------------------------------------------------------------------------------------------

    @Test
    void materialsHaveThickness() throws Sprite.Flaw {
        Map<String, String> legend = Map.of("s", "steel", "g", "gold thick 4", "r", "glow #7fe8ff thick 3 round", "f",
                "glow #ffb060 flat");
        JsonObject json = sprite(16, legend, rows(16, Map.of(2, "..sss", 4, "..ggg", 5, "..ggg", 6, "..ggg", 8, "..rrr", 9,
                "..rrr", 10, "..rrr", 11, "..rrr", 12, "..rrr", 14, "..fff")));
        Sprite s = Sprite.parse(json);
        assertEquals(1, s.usualThick(), "a 16 canvas is one texel thick, as vanilla items are");
        Art art = SpritePainter.paint(s, false);
        assertEquals(1, art.thick(3, 2), "steel says nothing, so it is the usual thickness");
        assertEquals(4, art.thick(3, 5));
        assertEquals(3, art.thick(3, 10), "a rounded band is thickest along its middle");
        assertEquals(2, art.thick(2, 10), "and thinner at its edges: a rod");
        assertTrue(art.drawn(3, 14));
        assertEquals(0, art.thick(3, 14), "a film is drawn but has no thickness");
        assertEquals(0, art.thick(0, 0));
        assertEquals(0, art.thick(-1, 99), "nothing lies off the canvas");

        // It is written back the way it was written.
        Sprite again = Sprite.parse(s.toJson());
        assertEquals(s.legend(), again.legend());
        assertEquals(2, Sprite.parse(sprite(32, Map.of("s", "steel"), rows(32, Map.of(3, "..sss")))).usualThick());

        Sprite.Flaw tooThick = assertThrows(Sprite.Flaw.class, () -> Sprite.ink("g", "gold thick 13"));
        assertTrue(tooThick.getMessage().contains("out of range"), tooThick.getMessage());
        assertTrue(assertThrows(Sprite.Flaw.class, () -> Sprite.ink("g", "gold thick")).getMessage().contains("thick 3"));

        // Squaring a tall sprite carries its thickness with it.
        JsonObject tall = new JsonObject();
        tall.addProperty("width", 8);
        tall.addProperty("height", 12);
        JsonObject key = new JsonObject();
        key.addProperty("g", "gold thick 3");
        tall.add("legend", key);
        JsonArray tallRows = new JsonArray();
        for (int y = 0; y < 12; y++) tallRows.add(y > 1 && y < 10 ? "..gggg.." : "........");
        tall.add("rows", tallRows);
        Art squared = SpritePainter.paint(Sprite.parse(tall), false).squared();
        assertEquals(12, squared.w());
        assertEquals(3, squared.thick(2 + 2, 5));
        assertEquals(0, squared.thick(1, 5));
    }

    @Test
    void aPaintingBecomesBoxes() {
        Art blade = SpritePainter.paint(Samples.BLADE.sprite(), false);
        List<RelicForm.Bar> bars = RelicForm.bars(blade);
        int drawn = 0, inBars = 0;
        for (int y = 0; y < blade.h(); y++) {
            for (int x = 0; x < blade.w(); x++) drawn += blade.drawn(x, y) ? 1 : 0;
        }
        for (RelicForm.Bar b : bars) inBars += b.x1() - b.x0();
        assertEquals(drawn, inBars, "every drawn texel is in exactly one box");

        JsonObject model = JsonParser.parseString(RelicForm.model(blade, "gptgodmc:item/relic/x", true)).getAsJsonObject();
        assertEquals("minecraft:item/handheld", model.get("parent").getAsString());
        assertEquals("gptgodmc:item/relic/x", model.getAsJsonObject("textures").get("layer0").getAsString());
        JsonArray elements = model.getAsJsonArray("elements");
        assertEquals(bars.size(), elements.size());
        for (int i = 0; i < elements.size(); i++) {
            JsonObject e = elements.get(i).getAsJsonObject();
            JsonArray from = e.getAsJsonArray("from"), to = e.getAsJsonArray("to");
            for (int axis = 0; axis < 3; axis++) {
                assertTrue(from.get(axis).getAsDouble() >= 0 && to.get(axis).getAsDouble() <= 16, e.toString());
                assertTrue(from.get(axis).getAsDouble() <= to.get(axis).getAsDouble(), e.toString());
            }
            // One texel thick on a 16 canvas: half a sixteenth either side of the middle.
            assertEquals(7.5, from.get(2).getAsDouble(), 1e-6);
            assertEquals(8.5, to.get(2).getAsDouble(), 1e-6);
            assertTrue(e.getAsJsonObject("faces").has("south") && e.getAsJsonObject("faces").has("north"));
            assertFalse(e.has("light_emission"), "lit by the world");
        }
        assertEquals("minecraft:item/generated", JsonParser.parseString(RelicForm.model(blade, "t", false)).getAsJsonObject()
                .get("parent").getAsString());

        // What shines by itself carries its own light; a film has only a front and a back.
        Art vial = SpritePainter.paint(Samples.VIAL.sprite(), false);
        assertFalse(RelicForm.model(vial, "t", false).contains("light_emission"), "its light is lit by the world");
        Art film;
        try {
            film = SpritePainter.paint(Sprite.parse(sprite(16, Map.of("f", "glow #ffb060 flat"),
                    rows(16, Map.of(7, "....ffff", 8, "....ffff")))), false);
        } catch (Sprite.Flaw e) {
            throw new AssertionError(e);
        }
        JsonObject filmModel = JsonParser.parseString(RelicForm.model(film, "t", false)).getAsJsonObject();
        for (var e : filmModel.getAsJsonArray("elements")) {
            assertEquals(2, e.getAsJsonObject().getAsJsonObject("faces").size(), "a film is two faces");
        }

        // A form too intricate for boxes falls back to the flat model.
        int n = 32;
        int[] frame = new int[n * n];
        byte[] depth = new byte[n * n];
        for (int i = 0; i < n * n; i++) {
            frame[i] = 0xFF808080;
            depth[i] = (byte) (1 + (i + i / n) % 2);
        }
        assertNull(RelicForm.model(new Art(n, n, new int[][] { frame }, null, depth), "t", false));
        assertThrows(IllegalArgumentException.class, () -> RelicForm.model(new Art(8, 4, new int[][] { new int[32] }, null,
                new byte[32]), "t", false));

        BufferedImage turned = RelicForm.turned(blade, 0, 6, 0x17111F);
        assertTrue(turned.getWidth() > 16 * 3 && turned.getHeight() > 16 * 5);
    }

    @Test
    void aSpecialRelicHasASecondForm() throws IOException, Sprite.Flaw {
        Sprite asleep = Samples.KINDLED.sprite(), awake = asleep.alt();
        assertNotNull(awake);
        assertNull(awake.alt());
        assertNull(Samples.BLADE.sprite().alt());
        int hilt = 0;
        for (int y = 0; y < asleep.h(); y++) {
            for (int x = 0; x < asleep.w(); x++) {
                if (asleep.cells()[y][x] == 0) continue;
                hilt++;
                assertEquals(asleep.cells()[y][x], awake.cells()[y][x], "the hilt does not move: " + x + "," + y);
            }
        }
        assertTrue(hilt > 20);
        Art lit = SpritePainter.paint(awake, false);
        assertTrue(lit.shines(18, 5), "the blade is light");
        assertEquals(2, lit.thick(18, 5), "light is given no thickness: it is the usual");
        assertEquals(2, lit.thick(17, 5));
        assertEquals(5, lit.thick(9, 14), "the collar is thicker than the blade");
        assertNotNull(RelicForm.model(lit, "t", true), "it fits in boxes");
        assertEquals(asleep.toJson(), Sprite.parse(asleep.toJson()).toJson());

        // The artist is shown both forms, and told when one is missing.
        Atelier.Attempt both = Atelier.attempt(Samples.KINDLED.json(), "blade", 1, true);
        assertFalse(both.error());
        assertTrue(both.text().contains("awake"), both.text());
        BufferedImage proof = ImageIO.read(new ByteArrayInputStream(both.proof()));
        BufferedImage single = SpriteSheet.proof(SpritePainter.paint(asleep, false));
        assertTrue(proof.getHeight() > single.getHeight() * 2 - 10, "two sheets, one above the other");
        assertTrue(Atelier.attempt(Samples.BLADE.json(), "blade", 1, true).text().contains("asks for two forms"));
        assertFalse(Atelier.attempt(Samples.BLADE.json(), "blade", 1).text().contains("two forms"));

        JsonObject jumped = Samples.KINDLED.json();
        JsonArray moved = new JsonArray();
        for (int y = 0; y < 24; y++) moved.add(y < 10 ? "...gggggggggg..........." : "........................");
        jumped.add("alt_rows", moved);
        assertTrue(Atelier.attempt(jumped, "blade", 1, true).text().contains("same texels"));

        JsonObject stray = Samples.KINDLED.json();
        stray.getAsJsonArray("alt_rows").set(0, new com.google.gson.JsonPrimitive("Z" + ".".repeat(23)));
        assertTrue(Atelier.attempt(stray, "blade", 1, true).text().contains("alt_rows"));

        String ask = Atelier.commission(new Atelier.Brief("Skysplinter", "blade", "A hilt that kindles.", "", true, true), 4);
        assertTrue(ask.contains("special") && ask.contains("alt_rows"), ask);
        assertFalse(Atelier.commission(new Atelier.Brief("Spoon", "trinket", "A spoon.", ""), 4).contains("special"));
        assertTrue(Atelier.system().contains("thick N") && Atelier.system().contains("Skysplinter"));
    }

    @Test
    void narrowDiagonalStrokesStayThin() throws Sprite.Flaw {
        // A blade three texels across on the diagonal, asked to be thick and round; and the same upright.
        String[] diagonal = new String[16], upright = new String[16];
        for (int y = 0; y < 16; y++) {
            StringBuilder d = new StringBuilder(".".repeat(16)), u = new StringBuilder(".".repeat(16));
            for (int x = 14 - y; x <= 16 - y; x++) {
                if (x >= 0 && x < 16) d.setCharAt(x, 's');
            }
            if (y > 0 && y < 15) u.replace(6, 9, "sss");
            diagonal[y] = d.toString();
            upright[y] = u.toString();
        }
        Sprite slanted = Sprite.parse(sprite(16, Map.of("s", "steel thick 4 round"), diagonal));
        Art art = SpritePainter.paint(slanted, false);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (art.drawn(x, y)) assertEquals(1, art.thick(x, y), "kept at the usual thickness: " + x + "," + y);
            }
        }
        assertEquals(java.util.Set.of('s'), SpritePainter.flattened(slanted));
        assertTrue(SpriteLint.notes(slanted, "blade").stream().anyMatch(n -> n.contains("like a stair")),
                SpriteLint.notes(slanted, "blade").toString());

        Sprite straight = Sprite.parse(sprite(16, Map.of("s", "steel thick 4 round"), upright));
        Art rod = SpritePainter.paint(straight, false);
        assertEquals(4, rod.thick(7, 7), "an upright rod keeps its thickness");
        assertEquals(3, rod.thick(6, 7), "and its roundness");
        assertTrue(SpritePainter.flattened(straight).isEmpty());
        assertTrue(SpritePainter.flattened(Sprite.parse(sprite(16, Map.of("s", "steel thick 4"), diagonal))).contains('s'));
        // Broad parts on the diagonal, and short slanted ones, keep theirs.
        String[] broad = new String[16], stub = new String[16];
        for (int y = 0; y < 16; y++) {
            StringBuilder b = new StringBuilder(".".repeat(16));
            for (int x = 12 - y; x <= 18 - y; x++) {
                if (x >= 0 && x < 16 && y > 1 && y < 14) b.setCharAt(x, 's');
            }
            broad[y] = b.toString();
            stub[y] = y >= 6 && y < 9 ? ".".repeat(9 - y) + "sss" + ".".repeat(4 + y) : ".".repeat(16);
        }
        assertTrue(SpritePainter.flattened(Sprite.parse(sprite(16, Map.of("s", "steel thick 4"), broad))).isEmpty());
        Sprite pommel = Sprite.parse(sprite(16, Map.of("s", "gold thick 4"), stub));
        assertTrue(SpritePainter.flattened(pommel).isEmpty());
        assertEquals(4, SpritePainter.paint(pommel, false).thick(4, 7));
        assertTrue(SpritePainter.flattened(Samples.KINDLED.sprite().alt()).isEmpty(), "Skysplinter's hilt is sculpted");
    }

    @Test
    void wallsAreShadedLikeTheFace() {
        Art blade = SpritePainter.paint(Samples.BLADE.sprite(), false);
        int brighter = 0, solid = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (!blade.drawn(x, y)) continue;
                solid++;
                int face = luma(blade.at(0, x, y));
                assertTrue(luma(blade.wall(0, SpritePainter.UP, x, y)) >= face, "the top catches the light");
                // The game shades the other walls by the way they face; the painter adds no darkness of its own.
                for (int side : new int[] { SpritePainter.DOWN, SpritePainter.WEST, SpritePainter.EAST }) {
                    assertEquals(blade.at(0, x, y), blade.wall(0, side, x, y), "wall " + side + " at " + x + "," + y);
                }
                brighter += luma(blade.wall(0, SpritePainter.UP, x, y)) > face ? 1 : 0;
            }
        }
        assertTrue(brighter > solid / 2, brighter + " of " + solid);
        // Light is shaded like any material: a tone lighter on top, else its own.
        Art lit = SpritePainter.paint(Samples.KINDLED.sprite().alt(), false);
        assertTrue(luma(lit.wall(3, SpritePainter.UP, 18, 5)) >= luma(lit.at(3, 18, 5)));
        assertEquals(lit.at(3, 18, 5), lit.wall(3, SpritePainter.DOWN, 18, 5));
        // Walls survive squaring, and the walls texture holds the four quarters.
        int[][] quarters = RelicForm.wallFrames(blade);
        assertEquals(SpritePainter.FRAMES, quarters.length);
        assertEquals(32 * 32, quarters[0].length);
        assertEquals(blade.wall(0, SpritePainter.EAST, 9, 6), quarters[0][(16 + 6) * 32 + 16 + 9]);
        assertEquals(blade.wall(0, SpritePainter.UP, 9, 6), quarters[0][6 * 32 + 9]);
        assertEquals(0, quarters[0][0], "nothing where nothing is drawn");
    }

    @Test
    void relicsAreLitLikeAnyItem() {
        // Nothing is forced bright: not steel, not gold, not light itself.
        for (Samples.Sample sample : Samples.ALL) {
            Sprite sprite = sample.sprite();
            for (Sprite form : sprite.alt() == null ? List.of(sprite) : List.of(sprite, sprite.alt())) {
                Art art = SpritePainter.paint(form, Atelier.halo(sample.kind())).squared();
                String model = RelicForm.model(art, new RelicForm.Look("f", "w"), true);
                assertFalse(model.contains("light_emission") || model.contains("shade"), sample.name());
                assertFalse(model.contains("glow"), sample.name());
            }
        }
        // Light is a solid like any other part, at the thickness it is painted.
        Art awake = SpritePainter.paint(Samples.KINDLED.sprite().alt(), false);
        assertEquals(2 * 16 / 24.0, RelicForm.depth(awake, 18, 5), 1e-9);
        assertEquals(5 * 16 / 24.0, RelicForm.depth(awake, 9, 14), 1e-9);
        JsonArray elements = JsonParser.parseString(RelicForm.model(awake, new RelicForm.Look("f", "w"), true))
                .getAsJsonObject().getAsJsonArray("elements");
        for (var e : elements) {
            JsonObject el = e.getAsJsonObject();
            assertTrue(el.getAsJsonArray("to").get(2).getAsDouble() > el.getAsJsonArray("from").get(2).getAsDouble(),
                    "nothing in Skysplinter is a film");
            for (var f : el.getAsJsonObject("faces").entrySet()) {
                String side = f.getKey(), tex = f.getValue().getAsJsonObject().get("texture").getAsString();
                assertEquals(side.equals("south") || side.equals("north") ? "#layer0" : "#walls", tex, side);
            }
        }
        // Without a walls texture the model is the plain one: every face from the sprite.
        assertFalse(RelicForm.model(awake, "f", true).contains("#walls"));
    }

    private static int luma(int argb) {
        return 299 * ((argb >> 16) & 255) + 587 * ((argb >> 8) & 255) + 114 * (argb & 255);
    }

    // ---- Powers -------------------------------------------------------------------------------------------

    @Test
    void powersAreReadAndTheirMistakesSaid() throws RelicPowers.Flaw {
        Power ignite = RelicPowers.parse("on_hit: ignite 3 | chance 50");
        assertEquals(Trigger.HIT, ignite.trigger());
        assertEquals(Effect.IGNITE, ignite.effect());
        assertEquals(3, ignite.amount());
        assertEquals(50, ignite.chance());

        Power wave = RelicPowers.parse("  On Use:  Wave 6  #7FE8FF | cooldown 8 | when on ");
        assertEquals(Trigger.USE, wave.trigger());
        assertEquals(Effect.WAVE, wave.effect());
        assertEquals(0x7FE8FF, wave.color());
        assertEquals(8, wave.cooldown());
        assertEquals("on", wave.when());
        assertEquals(6, RelicPowers.parse("on_use: beam 4").cooldown(), "an act by hand rests six seconds unless told");
        assertEquals(1, RelicPowers.parse("on_use: toggle").cooldown());
        assertEquals(1, RelicPowers.parse("on_swing: slash 2").cooldown());

        assertEquals(14, RelicPowers.parse("stat: attack_damage 99").amount(), "every number is clamped");
        assertEquals(RelicPowers.Stat.SPEED, RelicPowers.parse("stat: speed +15%").stat());
        assertEquals("self", RelicPowers.parse("held: potion night_vision 1 30").aim());
        assertEquals("foe", RelicPowers.parse("on_hit: potion poison 2 5").aim());
        assertEquals("near", RelicPowers.parse("on_use: potion regeneration 1 10 near").aim());
        assertEquals("minecraft:block.beacon.activate", RelicPowers.parse("on_use: sound block.beacon.activate 1.9").word());
        assertEquals(Effect.BLINK, RelicPowers.parse("on_use: teleport 8").effect(), "common other names are understood");
        assertEquals("ember ring", RelicPowers.parse("on_kill: fx ember ring").word());

        for (String bad : List.of("ignite 3", "sometimes: ignite 3", "on_hit: dance", "held: slash 3", "on_use: bolt cannon",
                "on_hit: ignite lots", "on_hit: ignite 3 | maybe", "on_hit: ignite 3 | when later", "stat: charisma 4",
                "on_use: wave 4 #zzz", "on_use: sound !!", "held: aura", "")) {
            RelicPowers.Flaw flaw = assertThrows(RelicPowers.Flaw.class, () -> RelicPowers.parse(bad), bad);
            assertFalse(flaw.getMessage().isBlank());
        }
        assertTrue(assertThrows(RelicPowers.Flaw.class, () -> RelicPowers.parse("held: slash 3")).getMessage()
                .contains("on_swing or on_use"));
    }

    @Test
    void aRelicCanRunItsMakersScripts() throws RelicPowers.Flaw {
        Power p = RelicPowers.parse("on_use: script frost_nova {\"radius\": 4, \"block\": \"ice\"} | cooldown 8 | when on");
        assertEquals(Effect.SCRIPT, p.effect());
        assertEquals("frost_nova", p.word());
        assertEquals("{\"radius\":4,\"block\":\"ice\"}", p.data(), "kept whole, commas and all");
        assertEquals(8, p.cooldown());
        assertEquals("on", p.when());
        Power hit = RelicPowers.parse("on_hit: script chain_lightning");
        assertEquals("", hit.data());
        assertEquals(RelicPowers.SCRIPT_REST, hit.cooldown(), "never more often than every few seconds");
        assertEquals(RelicPowers.SCRIPT_REST, RelicPowers.parse("on_swing: script x | cooldown 1").cooldown());
        assertTrue(RelicPowers.parse("on_kill: script x").cost() >= 4, "a program is the strongest thing a relic holds");
        assertEquals("{\"a\":\"x|y\"}", RelicPowers.parse("on_use: script x {\"a\": \"x|y\"}").data(), "bars inside strings");
        for (String bad : List.of("held: script x", "on_use: script", "on_use: script a b", "on_use: script x {\"a\": 1",
                "on_use: script x [1, 2]", "on_use: script x {not json}", "on_hit: ignite 3 {\"a\": 1}",
                "on_use: script x {\"a\": \"" + "y".repeat(RelicPowers.MAX_DATA) + "\"}")) {
            assertThrows(RelicPowers.Flaw.class, () -> RelicPowers.parse(bad), bad);
        }
        assertTrue(RelicPowers.HELP.contains("script <one of your saved scripts>") && RelicPowers.HELP.contains("args.bearer"));
        // Its tooltip names the script, not its arguments.
        assertEquals(List.of("◆ Right-click: frost nova (6s)"), RelicPowers.lore(List.of("on_use: script frost_nova {\"radius\": 4}")));

        // A relic's script is a moment: no handlers, and a fraction of what the god's own scripts may spend.
        net.bigyous.gptgodmc.god.script.ScriptLimits mine = net.bigyous.gptgodmc.god.script.ScriptLimits.DEFAULT;
        net.bigyous.gptgodmc.god.script.ScriptLimits relic = net.bigyous.gptgodmc.god.tools.RelicScripts.limits(mine);
        assertEquals(0, relic.listeners());
        assertEquals(0, relic.events());
        assertTrue(relic.instructions() <= mine.instructions() && relic.seconds() <= 2 && relic.blocks() <= 256
                && relic.powerCalls() <= 4 && relic.commands() <= 3 && relic.entities() <= 4, relic.describe());
        net.bigyous.gptgodmc.god.script.ScriptLimits tight = new net.bigyous.gptgodmc.god.script.ScriptLimits(20_000, 1, 0,
                10, 0, 0, 0, 0, 2, 30, 50);
        assertEquals(0, net.bigyous.gptgodmc.god.tools.RelicScripts.limits(tight).powerCalls(), "never more than the god's own");
        assertEquals(0, net.bigyous.gptgodmc.god.tools.RelicScripts.limits(tight).blocks());
    }

    @Test
    void aRelicHoldsOnlySoMuchPower() throws RelicPowers.Flaw {
        // Stronger costs more; resting longer or working rarely costs less.
        assertTrue(RelicPowers.parse("on_use: beam 8").cost() > RelicPowers.parse("on_use: beam 3").cost());
        assertTrue(RelicPowers.parse("on_use: beam 8 | cooldown 30").cost() < RelicPowers.parse("on_use: beam 8 | cooldown 3")
                .cost());
        assertTrue(RelicPowers.parse("on_hit: ignite 4 | chance 25").cost() < RelicPowers.parse("on_hit: ignite 4").cost());
        assertEquals(0, RelicPowers.parse("stat: attack_damage 6").cost(), "an iron sword's edge is free");
        assertEquals(0, RelicPowers.parse("stat: attack_damage 2").cost());
        assertTrue(RelicPowers.parse("stat: attack_damage 10").cost() > 2);
        assertEquals(0, RelicPowers.parse("on_use: toggle").cost());

        List<String> sample = List.of(Samples.KINDLED.powers());
        RelicPowers.Reading reading = RelicPowers.read(sample, true, 12);
        assertEquals(sample.size(), reading.powers().size());
        assertTrue(reading.toggles() && reading.needsTwoForms());
        assertTrue(reading.cost() > 6 && reading.cost() <= 12, "the example is strong but fits: " + reading.cost());

        RelicPowers.Flaw tooMuch = assertThrows(RelicPowers.Flaw.class, () -> RelicPowers.read(List.of("on_hit: damage 12",
                "on_use: beam 10 | cooldown 1", "on_use: slam 10 | cooldown 1"), false, 12));
        assertTrue(tooMuch.getMessage().contains("more power"), tooMuch.getMessage());
        assertTrue(assertThrows(RelicPowers.Flaw.class, () -> RelicPowers.read(sample, false, 12)).getMessage()
                .contains("two forms"));
        assertTrue(assertThrows(RelicPowers.Flaw.class, () -> RelicPowers.read(List.of("held: light 9 | when on"), true, 12))
                .getMessage().contains("on_use: toggle"));
        assertThrows(RelicPowers.Flaw.class, () -> RelicPowers.read(List.of("on_hit: ignite 1"), false, 0));
        assertThrows(RelicPowers.Flaw.class, () -> RelicPowers.read(java.util.Collections.nCopies(13, "on_hit: ignite 1"),
                false, 40));
        assertTrue(RelicPowers.read(List.of(), false, 0).powers().isEmpty(), "no powers is always allowed");
        assertFalse(RelicPowers.read(List.of("on_hit: ignite 2", " "), false, 12).needsTwoForms());

        // The tooltip says what a mortal can do with it, not how it was written.
        List<String> lore = RelicPowers.lore(sample);
        assertTrue(lore.contains("◆ Right-click: wake (sneak + right-click: sleep)"), lore.toString());
        assertTrue(lore.contains("◆ On hit, awake: ignite 3"), lore.toString());
        assertTrue(lore.contains("◆ Right-click, awake: wave 6 (8s)"), lore.toString());
        assertTrue(lore.stream().noneMatch(l -> l.contains("stat") || l.contains("sound")), lore.toString());
        assertEquals(List.of("◆ Right-click: wake or sleep"), RelicPowers.lore(List.of("on_use: toggle", "gibberish")));
        assertTrue(RelicPowers.HELP.contains("on_swing") && RelicPowers.HELP.contains("toggle"));
    }

    @Test
    void bothFormsRideInThePackAndGoToTheMod(@TempDir Path dir) throws IOException {
        Relics.load(dir);
        Samples.Sample k = Samples.KINDLED;
        Relic r = Relics.add("samples", "the first artist", k.name(), k.kind(), k.note(), k.sprite(), List.of(k.powers()), 1);
        assertEquals("skysplinter", r.id());
        assertTrue(r.twoFormed());
        assertEquals(k.powers().length, r.powers().size());
        assertEquals(k.powers().length, RelicPowers.of(r).size());
        Relic plain = Relics.add("main", "Vaelor", "Dawnbreaker", "blade", "", Samples.BLADE.sprite(), 2);
        assertFalse(plain.twoFormed());
        assertNull(Relics.artOn(plain));
        assertTrue(plain.powers().isEmpty());

        Map<String, byte[]> files = RelicPack.files();
        for (String id : List.of("skysplinter", "skysplinter__on")) {
            assertTrue(files.containsKey("assets/gptgodmc/models/item/relic/" + id + ".json"), id);
            assertTrue(files.containsKey("assets/gptgodmc/items/relic/" + id + ".json"), id);
            assertTrue(files.containsKey("assets/gptgodmc/textures/item/relic/" + id + ".png"), id);
            assertTrue(files.containsKey("assets/gptgodmc/textures/item/relic/" + id + ".png.mcmeta"), id);
        }
        assertFalse(files.containsKey("assets/gptgodmc/models/item/relic/dawnbreaker__on.json"));
        // Painted walls for every form; a flat icon for inventories; no glow, by day or night.
        for (String id : List.of("skysplinter", "skysplinter__on", "dawnbreaker")) {
            assertTrue(files.containsKey("assets/gptgodmc/textures/item/relic/" + id + "__walls.png"), id);
            assertTrue(files.containsKey("assets/gptgodmc/textures/item/relic/" + id + "__walls.png.mcmeta"), id);
            String icon = new String(files.get("assets/gptgodmc/models/item/relic/" + id + "__icon.json"),
                    StandardCharsets.UTF_8);
            assertFalse(icon.contains("elements"), "the icon is the flat sprite");
            JsonObject item = JsonParser.parseString(new String(files.get("assets/gptgodmc/items/relic/" + id + ".json"),
                    StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("model");
            assertEquals("minecraft:select", item.get("type").getAsString());
            assertEquals("minecraft:display_context", item.get("property").getAsString());
            JsonObject gui = item.getAsJsonArray("cases").get(0).getAsJsonObject();
            assertEquals("gui", gui.get("when").getAsString());
            assertEquals("gptgodmc:item/relic/" + id + "__icon", gui.getAsJsonObject("model").get("model").getAsString());
            assertEquals("gptgodmc:item/relic/" + id, item.getAsJsonObject("fallback").get("model").getAsString());
        }
        assertTrue(files.keySet().stream().noneMatch(f -> f.contains("__glow") || f.contains("__night")), "no glow");
        String awake = new String(files.get("assets/gptgodmc/models/item/relic/skysplinter__on.json"), StandardCharsets.UTF_8);
        assertTrue(awake.contains("minecraft:item/handheld") && !awake.contains("light_emission"));
        assertTrue(awake.contains("gptgodmc:item/relic/skysplinter__on"));
        assertTrue(new String(files.get("assets/gptgodmc/models/item/relic/dawnbreaker.json"), StandardCharsets.UTF_8)
                .contains("\"elements\""), "a plain relic is its painting given thickness");

        JsonObject asleep = ModRelics.message(r, Relics.art(r));
        JsonObject on = ModRelics.message(r.id() + Relics.ON, r, Relics.artOn(r));
        assertEquals("skysplinter", asleep.get("id").getAsString());
        assertEquals("skysplinter__on", on.get("id").getAsString());
        assertEquals(24 * 24, Base64.getDecoder().decode(asleep.get("depth").getAsString()).length);
        byte[] depth = Base64.getDecoder().decode(on.get("depth").getAsString());
        assertEquals(2, depth[5 * 24 + 18], "the blade's middle, row by row");
        assertEquals(5, depth[14 * 24 + 9], "the collar");
        BufferedImage walls = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(on.get("walls").getAsString())));
        assertEquals(48, walls.getWidth(), "four quarters, each the size of the art");
        assertEquals(48 * SpritePainter.FRAMES, walls.getHeight());
        assertEquals(0, depth[0]);
        assertTrue(on.has("lit"));
        for (JsonObject m : List.of(asleep, on)) {
            assertTrue(m.toString().getBytes(StandardCharsets.UTF_8).length <= ModSessions.MAX_BYTES);
        }

        // Imbuing anew is kept, and tells every copy by its revision.
        Relic again = Relics.imbue(r.id(), List.of("on_hit: ignite 2"));
        assertEquals(r.rev() + 1, again.rev());
        assertNull(Relics.imbue("nothing", List.of()));
        Relics.load(dir);
        assertEquals(List.of("on_hit: ignite 2"), Relics.find("Skysplinter").powers());
        assertEquals(1, RelicPowers.of(Relics.find("skysplinter")).size());
        assertTrue(Relics.find("skysplinter").twoFormed(), "its second form is kept with it");
    }
}
