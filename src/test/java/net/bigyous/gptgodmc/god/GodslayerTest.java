package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.bigyous.gptgodmc.god.vessel.VesselMood;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** The Godslayer: health, blows, healing, death that lasts, return timing, loot and the blade's art. */
class GodslayerTest {
    private static final long T0 = 1_000_000_000L;

    @Test
    void blowsTakeHealthAndTheLastOneKills() {
        Mortality m = new Mortality();
        assertEquals(100, m.health(100));
        assertFalse(m.hurt());
        Mortality.Wound w = m.wound(15, "Steve", 100, T0);
        assertEquals(85, w.health(), 1e-9);
        assertFalse(w.killed());
        assertTrue(m.hurt());
        for (int i = 0; i < 5; i++) m.wound(15, "Steve", 100, T0 + i);
        assertEquals(10, m.health(100), 1e-9);
        Mortality.Wound last = m.wound(12, "Alex", 100, T0 + 10);
        assertTrue(last.killed());
        assertEquals(0, last.health(), 1e-9);
        assertTrue(m.isSlain());
        assertEquals("Alex", m.slainBy);
        assertEquals("Alex", m.lastSlayer);
        assertEquals(1, m.deaths);
        assertNull(m.wound(15, "Steve", 100, T0 + 20), "the dead cannot be wounded again");
        assertEquals(1, m.deaths);
    }

    @Test
    void aLowerMaximumNeverLeavesItAboveFull() {
        Mortality m = new Mortality();
        m.wound(30, "Steve", 100, T0);
        assertEquals(20, m.health(50), 1e-9);
    }

    @Test
    void anAscendedBlowKillsAWholeGodAtOnce() {
        Mortality m = new Mortality();
        Mortality.Wound w = m.wound(m.health(100) + 1, "Steve", 100, T0);
        assertTrue(w.killed(), "one blow from full health");
        assertEquals(1, m.deaths);
        assertTrue(GodslayerArt.files().containsKey(GodslayerArt.ITEM_FILE_ASCENDED));
        assertNotEquals(GodslayerArt.frame(0, false).getRGB(10, 5), GodslayerArt.frame(0, true).getRGB(10, 5),
                "the ascended blade has its own look");
    }

    @Test
    void damageScalesWithRollAndCharge() {
        assertEquals(10, Mortality.damageFor(10, 15, 1, 0), 1e-9);
        assertEquals(15, Mortality.damageFor(10, 15, 1, 1), 1e-9);
        assertEquals(12.5, Mortality.damageFor(10, 15, 1, 0.5), 1e-9);
        assertEquals(2, Mortality.damageFor(10, 15, 0, 0), 1e-9, "an uncharged swing does a fifth");
        assertEquals(15, Mortality.damageFor(15, 10, 1, 1), 1e-9, "min and max swapped still work");
        assertEquals(15, Mortality.damageFor(10, 15, 3, 7), 1e-9, "inputs are clamped");
    }

    @Test
    void woundsHealOnlyAfterTheDelayThenSteadily() {
        Mortality m = new Mortality();
        m.wound(40, "Steve", 100, T0);
        long delay = 60_000;
        assertFalse(m.heal(T0 + 30_000, delay, 10), "too soon");
        assertEquals(60, m.health(100), 1e-9);
        assertTrue(m.heal(T0 + 60_000 + 60_000, delay, 10));
        assertEquals(70, m.health(100), 1e-6);
        // Healing again later only adds the new time.
        m.heal(T0 + 60_000 + 120_000, delay, 10);
        assertEquals(80, m.health(100), 1e-6);
        // A fresh blow restarts the wait.
        m.wound(10, "Steve", 100, T0 + 200_000);
        assertFalse(m.heal(T0 + 230_000, delay, 10));
        m.heal(T0 + 200_000 + delay + 600_000, delay, 10);
        assertEquals(100, m.health(100), 1e-9);
        assertFalse(m.hurt());
    }

    @Test
    void theDeadDoNotHeal() {
        Mortality m = new Mortality();
        m.wound(200, "Steve", 100, T0);
        assertFalse(m.heal(T0 + 10_000_000, 0, 100));
        assertEquals(0, m.health(100));
    }

    @Test
    void returnsOnlyWhenAllowedAndDue() {
        Mortality m = new Mortality();
        assertFalse(m.returnDue(T0, true, 1), "alive");
        m.wound(100, "Steve", 100, T0);
        assertFalse(m.returnDue(T0 + 999_999_999, false, 60), "god-returns is off");
        assertEquals(-1, m.untilReturn(T0, false, 60));
        assertFalse(m.returnDue(T0 + 59 * 60_000, true, 60));
        assertEquals(60_000, m.untilReturn(T0 + 59 * 60_000, true, 60));
        assertTrue(m.returnDue(T0 + 60 * 60_000, true, 60));
        m.revive(T0 + 60 * 60_000);
        assertFalse(m.isSlain());
        assertEquals(100, m.health(100));
        assertEquals("Steve", m.lastSlayer, "it remembers who slew it");
        assertTrue(m.describe(100, T0 + 61 * 60_000).contains("slain once, last by Steve"));
    }

    @Test
    void theSlainStateSurvivesARestartAndForgetting(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("memory.json");
        Memory m = Memory.open(file);
        m.mortality.wound(100, "Steve", 100, T0);
        m.save();
        assertTrue(Files.readString(file).contains("\"slain\": true"));
        Memory back = Memory.open(file);
        assertTrue(back.mortality.isSlain());
        assertEquals("Steve", back.mortality.slainBy);
        assertEquals(T0, back.mortality.slainAt);

        // An old memory.json without the field is a living god.
        Files.writeString(file, "{\"chronicle\": \"old\"}");
        Memory old = Memory.open(file);
        assertNotNull(old.mortality);
        assertFalse(old.mortality.isSlain());
    }

    @Test
    void woundsMoveTheHeartToWrathAndDread() {
        Memory m = new Memory();
        m.noticeEvent("PRAYER", "Steve WOUNDED you with the Godslayer (health 85/100)", true, List.of("Steve"));
        long now = System.currentTimeMillis();
        assertTrue(m.heart.level(Heart.Feeling.WRATH) > 10, "wrath");
        assertTrue(m.heart.level(Heart.Feeling.DREAD) > 10, "dread");
        assertEquals(0, m.heart.level(Heart.Feeling.JOY), 1e-9, "a wound is no prayer");
        assertTrue(m.renderDossier("Steve", true, true, now).contains("WOUNDED you with the Godslayer"),
                "kept as a deed");
    }

    @Test
    void theTabListShowsASlainGodWithoutMoodOrThought() {
        TabList.Text t = TabList.compose("Vaelor", null, "wrathful", VesselMood.WRATHFUL, TabList.State.SLAIN, true);
        String header = PlainTextComponentSerializer.plainText().serialize(t.header());
        assertTrue(header.endsWith("has been slain"), header);
    }

    @Test
    void lootLinesParse() {
        GodslayerLoot.Parsed p = GodslayerLoot.parse(Arrays.asList("relic", "nether_star 3", "Minecraft:DIAMOND_BLOCK 8-16",
                "elytra", "totem_of_undying x2", "diamond 9-3", "netherite_ingot 9999", "bad item!", "apple lots", "",
                null));
        List<GodslayerLoot.Drop> d = p.drops();
        assertEquals(7, d.size());
        assertTrue(d.get(0).relic());
        assertEquals(new GodslayerLoot.Drop("nether_star", 3, 3), d.get(1));
        assertEquals(new GodslayerLoot.Drop("diamond_block", 8, 16), d.get(2));
        assertEquals(new GodslayerLoot.Drop("elytra", 1, 1), d.get(3));
        assertEquals(new GodslayerLoot.Drop("totem_of_undying", 2, 2), d.get(4));
        assertEquals(new GodslayerLoot.Drop("diamond", 3, 9), d.get(5), "a reversed range is fixed");
        assertEquals(GodslayerLoot.MAX_COUNT, d.get(6).max(), "huge counts are capped");
        assertEquals(2, p.problems().size(), p.problems().toString());
        assertEquals(8, d.get(2).count(0));
        assertEquals(16, d.get(2).count(1));
        assertTrue(GodslayerLoot.parse(GodslayerLoot.DEFAULTS).problems().isEmpty());
        assertTrue(GodslayerLoot.parse(GodslayerLoot.DEFAULTS).drops().get(0).relic());
    }

    @Test
    void theBladeIsPaintedAndPacked() throws Exception {
        BufferedImage a = GodslayerArt.frame(0);
        BufferedImage b = GodslayerArt.frame(2);
        assertEquals(16, a.getWidth());
        assertNotEquals(a.getRGB(10, 5), b.getRGB(10, 5), "the crack pulses");
        assertEquals(0, a.getRGB(0, 0) >>> 24, "transparent background");
        assertEquals(64, GodslayerArt.strip().getHeight());
        Map<String, byte[]> files = GodslayerArt.files();
        assertTrue(files.containsKey(GodslayerArt.ITEM_FILE));
        assertTrue(new String(files.get(GodslayerArt.ITEM_FILE)).contains("gptgodmc:item/godslayer"));
        assertTrue(files.containsKey("assets/gptgodmc/textures/item/godslayer.png"));
        assertTrue(Arrays.equals(files.get("assets/gptgodmc/textures/item/godslayer.png"),
                GodslayerArt.files().get("assets/gptgodmc/textures/item/godslayer.png")), "deterministic");

        // Preview: every frame at 16x, side by side.
        int s = 16;
        BufferedImage out = new BufferedImage(16 * s * GodslayerArt.FRAMES + 8 * (GodslayerArt.FRAMES - 1), 16 * s,
                BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < GodslayerArt.FRAMES; f++) {
            BufferedImage fr = GodslayerArt.frame(f);
            for (int y = 0; y < 16 * s; y++) {
                for (int x = 0; x < 16 * s; x++) {
                    int c = fr.getRGB(x / s, y / s);
                    boolean check = ((x / 16) + (y / 16)) % 2 == 0;
                    if ((c >>> 24) == 0) c = check ? 0xFF2A2A2A : 0xFF333333;
                    out.setRGB(f * (16 * s + 8) + x, y, c);
                }
            }
        }
        File dirOut = new File("build/test-renders");
        dirOut.mkdirs();
        ImageIO.write(out, "png", new File(dirOut, "godslayer.png"));
    }
}
