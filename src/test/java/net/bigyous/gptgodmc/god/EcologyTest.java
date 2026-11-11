package net.bigyous.gptgodmc.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

/** The god sees what lives around mortals and remembers, fading, what each did to the land. */
class EcologyTest {
    static final long T = 1_700_000_000_000L;
    static final long HOUR = 3_600_000L;
    static final long HL = 24 * HOUR;

    static WorldDeed deed(String who, String act, String kind, Set<String> tags, int x, int z, long at) {
        return new WorldDeed(who, act, kind, tags, "world", x, 64, z, at);
    }

    @Test
    void theLandIsToldInOneLine() {
        Map<String, Integer> animals = new TreeMap<>(Map.of("cow", 6, "sheep", 3, "wolf", 2));
        String line = Ecology.describe(new Ecology.Land("plains", animals, 2, 8, 1, 10, 6, 20, 625, 2, true));
        assertTrue(line.startsWith("plains; 6 cows, 3 sheep, 2 wolves (2 tamed)"), line);
        assertTrue(line.contains("a village of 8 villagers and 1 iron golem"), line);
        assertTrue(line.contains("~40 crops, 60% ripe"), line);
        assertTrue(line.contains("a few trees"), line);
    }

    @Test
    void aForestWithoutTreesIsNoticed() {
        String bare = Ecology.describe(new Ecology.Land("dark_forest", Map.of(), 0, 0, 0, 0, 0, 0, 600, 2, true));
        assertTrue(bare.contains("no animals") && bare.contains("a forest with its trees cut away"), bare);
        String plains = Ecology.describe(new Ecology.Land("plains", Map.of(), 0, 0, 0, 0, 0, 0, 600, 2, true));
        assertTrue(plains.endsWith("no trees"), plains);
        String nether = Ecology.describe(new Ecology.Land("nether_wastes", Map.of("strider", 1), 0, 0, 0, 0, 0, 0, 0, 2,
                false));
        assertEquals("nether wastes; 1 strider", nether, "no sky, no trees or fields to speak of");
    }

    @Test
    void deedsBecomeMarksOnTheLand() {
        assertEquals("logs felled", Ecology.footprint(deed("A", "fell_trees", "oak_log", Set.of("log"), 0, 0, T)));
        assertEquals("saplings planted", Ecology.footprint(deed("A", "plant", "oak_sapling", Set.of("sapling"), 0, 0, T)));
        assertEquals("crops sown", Ecology.footprint(deed("A", "plant", "wheat", Set.of("crop"), 0, 0, T)));
        assertEquals("villagers slain", Ecology.footprint(deed("A", "kill_mob", "villager", Set.of("villager"), 0, 0, T)));
        assertEquals("animals slain", Ecology.footprint(deed("A", "kill_mob", "cow", Set.of("animal"), 0, 0, T)));
        assertNull(Ecology.footprint(deed("A", "kill_mob", "zombie", Set.of("monster"), 0, 0, T)));
        assertNull(Ecology.footprint(deed("A", "break_blocks", "stone", Set.of(), 0, 0, T)));
    }

    @Test
    void aStrippedForestAndACulledHerdAreJudged() {
        Ecology.Ledger l = new Ecology.Ledger();
        for (int i = 0; i < 140; i++) Ecology.record(l, deed("Tyler", "fell_trees", "oak_log", Set.of("log"), 100, 20, T), HL);
        for (int i = 0; i < 14; i++) Ecology.record(l, deed("Ash", "kill_mob", "cow", Set.of("animal"), 110, 30, T), HL);
        Ecology.record(l, deed("Ash", "kill_mob", "villager", Set.of("villager"), 120, 40, T), HL);
        String r = Ecology.render(l, List.of(new Ecology.Spot("world", 90, 10)), T, HL, 4);
        assertTrue(r.startsWith("- near (96, 32): "), r);
        assertTrue(r.contains("Tyler 140 logs felled"), r);
        assertTrue(r.contains("Ash 14 animals slain, 1 villagers slain"), r);
        assertTrue(r.contains("the woods are being stripped"), r);
        assertTrue(r.contains("the herds are culled faster than they breed"), r);
        assertTrue(r.contains("villager blood was spilled here"), r);
    }

    @Test
    void marksFadeWithTheirHalfLife() {
        Ecology.Ledger l = new Ecology.Ledger();
        for (int i = 0; i < 64; i++) Ecology.record(l, deed("Tyler", "plant", "oak_sapling", Set.of("sapling"), 5, 5, T), HL);
        List<Ecology.Spot> here = List.of(new Ecology.Spot("world", 5, 5));
        assertTrue(Ecology.render(l, here, T + HL, HL, 4).contains("32 saplings planted"));
        assertTrue(Ecology.render(l, here, T + 3 * HL, HL, 4).contains("8 saplings planted"));
        assertEquals("", Ecology.render(l, here, T + 10 * HL, HL, 4), "a fortnight on, the land has forgotten");
        Ecology.prune(l, T + 10 * HL, HL);
        assertTrue(l.cells.isEmpty());
    }

    @Test
    void onlyCellsAroundMortalsAreTold() {
        Ecology.Ledger l = new Ecology.Ledger();
        Ecology.record(l, deed("Far", "breed", "cow", Set.of("animal"), 5000, 5000, T), HL);
        Ecology.record(l, deed("Near", "breed", "cow", Set.of("animal"), -10, 0, T), HL);
        Ecology.record(l, deed("Beyond", "breed", "cow", Set.of("animal"), -70, 0, T), HL);
        String r = Ecology.render(l, List.of(new Ecology.Spot("world", 0, 0)), T, HL, 4);
        assertTrue(r.startsWith("- near (-32, 32): Near 1 animals bred"), r);
        assertFalse(r.contains("Far") || r.contains("Beyond"), "only the cell they stand in and its neighbours: " + r);
    }

    @Test
    void theLedgerSurvivesBeingSavedAndRead() {
        Ecology.Ledger l = new Ecology.Ledger();
        Ecology.record(l, deed("Tyler", "fell_trees", "oak_log", Set.of("log"), 3, 3, T), HL);
        Gson gson = new Gson();
        Ecology.Ledger back = gson.fromJson(gson.toJson(l), Ecology.Ledger.class);
        assertEquals(1.0, back.cells.get("world:0:0").by.get("Tyler").get("logs felled"));
    }

    @Test
    void aSpreeIsToldOnceThenItKeepsQuiet() {
        Ecology.Spree s = new Ecology.Spree(3, 60_000, 600_000);
        assertFalse(s.hit("Ash", T));
        assertFalse(s.hit("Ash", T + 1000));
        assertTrue(s.hit("Ash", T + 2000));
        for (int i = 0; i < 5; i++) assertFalse(s.hit("Ash", T + 3000 + i), "quiet after telling");
        assertFalse(s.hit("Bo", T + 70_000));
        assertFalse(s.hit("Bo", T + 140_000), "too slow: outside the window");
    }
}
