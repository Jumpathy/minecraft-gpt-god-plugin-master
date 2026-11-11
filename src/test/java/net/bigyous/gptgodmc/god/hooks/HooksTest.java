package net.bigyous.gptgodmc.god.hooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The optional plugin hooks, with no such plugin (and no server) present: which is exactly how the plugin
 * must keep working. The rules and the summaries are pure and checked here; the calls into WorldGuard and
 * CoreProtect are compile-checked against their APIs.
 */
class HooksTest {

    @AfterEach
    void forget() {
        Lands.use(null);
    }

    @Test
    void withoutThePluginsEverythingIsSimplyAbsent() {
        assertFalse(Hooks.has("WorldGuard"));
        assertTrue(Hooks.found().isEmpty());
        assertTrue(Lands.names().isEmpty());
        assertNull(Lands.at(new Location(null, 0, 64, 0)));
        assertFalse(Wards.installed());
        assertFalse(LandHistory.installed());
        assertNull(LandHistory.source());
        assertFalse(Disguises.installed());
        assertFalse(Disguises.usable());
        assertFalse(Disguises.disguise(null, null));
        assertFalse(Disguises.undisguise(null));
        Wards.sweep(); // nothing to do, nothing thrown
        assertTrue(Wards.of("main").isEmpty());
        Wards.Refusal no = assertThrows(Wards.Refusal.class,
                () -> Wards.raise("main", "grove", new Location(null, 0, 64, 0), 8, List.of("no_pvp"), null, 30));
        assertTrue(no.getMessage().contains("no WorldGuard"), no.getMessage());
        // Defaults when there is no config to read.
        assertTrue(Lands.respect());
        assertEquals(6, Wards.maxWards());
        assertEquals(24, Wards.maxRadius());
        assertEquals(20, LandHistory.maxRadius());
        assertEquals(600, Disguises.maxSeconds());
    }

    @Test
    void theFirstPluginThatClaimsAPlaceAnswersAndABrokenOneIsSkipped() {
        List<Lands.Reader> readers = new ArrayList<>();
        readers.add(reader("Broken", l -> {
            throw new IllegalStateException("its API changed");
        }));
        readers.add(reader("GriefPrevention", l -> l.getBlockX() < 0 ? new Lands.Claim("GriefPrevention", null, "Steve") : null));
        readers.add(reader("Towny", l -> l.getBlockX() > 100 ? new Lands.Claim("Towny", "Eldham", "Alex") : null));
        readers.add(reader("WorldGuard", l -> l.getBlockZ() > 100 ? new Lands.Claim("WorldGuard", "spawn", null) : null));
        Lands.use(readers);
        assertEquals(List.of("Broken", "GriefPrevention", "Towny", "WorldGuard"), Lands.names());
        assertNull(Lands.at(new Location(null, 5, 64, 5)));
        assertEquals("Steve's claim (GriefPrevention)", Lands.at(new Location(null, -5, 64, 5)).describe());
        assertEquals("the town of Eldham, mayor Alex (Towny)", Lands.at(new Location(null, 500, 64, 5)).describe());
        assertEquals("the region spawn (WorldGuard)", Lands.at(new Location(null, 5, 64, 500)).describe());
        assertEquals("a claim (GriefPrevention)", new Lands.Claim("GriefPrevention", null, null).describe());
        assertEquals("the region vault, owned by Steve, Alex (WorldGuard)", new Lands.Claim("WorldGuard", "vault", "Steve, Alex").describe());
    }

    private interface At {
        Lands.Claim at(Location l) throws Exception;
    }

    private static Lands.Reader reader(String plugin, At at) {
        return new Lands.Reader() {
            @Override
            public String plugin() {
                return plugin;
            }

            @Override
            public Lands.Claim at(Location l) throws Exception {
                return at.at(l);
            }
        };
    }

    @Test
    void wardsKeepToTheirRules() throws Exception {
        assertEquals("god_main_holy_grove", Wards.id("main", "Holy Grove!"));
        assertTrue(Wards.id("r2", "x".repeat(80)).length() <= Wards.PREFIX.length() + 2 + 1 + 24);
        assertTrue(Wards.id("main", "a/b c").matches("[a-z0-9_]+"), "only what WorldGuard takes in a region id");

        assertEquals(List.of("no_pvp", "no_mobs"), Wards.check("grove", List.of("No PvP", "no-mobs", "no_pvp"), null, 8, 30, 24, 120));
        assertEquals(List.of(), Wards.check("grove", List.of(), "Tread softly.", 8, 30, 24, 120));
        for (String effect : Wards.EFFECTS) assertEquals(List.of(effect), Wards.check("w", List.of(effect), null, 1, 1, 24, 120));

        assertRefused("cannot do 'kill_all'", () -> Wards.check("grove", List.of("kill_all"), null, 8, 30, 24, 120));
        assertRefused("radius is 1 to 24", () -> Wards.check("grove", List.of("no_pvp"), null, 25, 30, 24, 120));
        assertRefused("lasts 1 to 120", () -> Wards.check("grove", List.of("no_pvp"), null, 8, 121, 24, 120));
        assertRefused("needs at least one effect", () -> Wards.check("grove", List.of(), " ", 8, 30, 24, 120));
        assertRefused("a name", () -> Wards.check("!!!", List.of("no_pvp"), null, 8, 30, 24, 120));

        Wards.Ward old = ward("old", 1000), young = ward("young", 9000);
        assertEquals(List.of(old), Wards.expired(List.of(old, young), 5000));
        assertTrue(young.describe(8000).contains("1 min left"), young.describe(8000));
        assertTrue(young.describe(8000).startsWith("young at 1 64 2, radius 8 (no_pvp)"), young.describe(8000));
    }

    private static Wards.Ward ward(String name, long until) {
        Wards.Ward w = new Wards.Ward();
        w.id = Wards.id("main", name);
        w.realm = "main";
        w.name = name;
        w.world = "world";
        w.x = 1;
        w.y = 64;
        w.z = 2;
        w.radius = 8;
        w.effects = List.of("no_pvp");
        w.until = until;
        return w;
    }

    private interface Check {
        void run() throws Wards.Refusal;
    }

    private static void assertRefused(String part, Check check) {
        Wards.Refusal r = assertThrows(Wards.Refusal.class, check::run);
        assertTrue(r.getMessage().contains(part), r.getMessage());
    }

    @Test
    void theLandsRecordReadsAsWhoDidWhat() {
        long now = 10_000_000L;
        List<LandHistory.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 9; i++) entries.add(new LandHistory.Entry("Steve", "break", "oak_log", i, 64, 0, now - 600_000 - i * 1000, false));
        for (int i = 0; i < 2; i++) entries.add(new LandHistory.Entry("Steve", "break", "oak_leaves", i, 66, 0, now - 500_000, false));
        entries.add(new LandHistory.Entry("Steve", "place", "torch", 1, 64, 1, now - 30_000, false));
        entries.add(new LandHistory.Entry("Alex", "place", "cobblestone", 5, 64, 5, now - 7_200_000, false));
        entries.add(new LandHistory.Entry("#creeper", "break", "grass_block", 9, 63, 9, now - 86_400_000L * 3, false));
        entries.add(new LandHistory.Entry("Alex", "break", "chest", 5, 64, 6, now - 100_000, true));
        String s = LandHistory.summarize(entries, now, 8, 3);
        assertEquals("""
                Steve: broke 11 (oak_log 9, oak_leaves 2); placed 1 (torch 1)
                Alex: placed 1 (cobblestone 1)
                creeper (not a mortal): broke 1 (grass_block 1)
                1 act(s) there were already taken back.
                Latest:
                  30 s ago Steve placed torch at 1 64 1
                  8 min ago Steve broke oak_leaves at 0 66 0
                  8 min ago Steve broke oak_leaves at 1 66 0""", s);
        assertEquals("Nothing is recorded there in that time.", LandHistory.summarize(List.of(), now, 8, 3));
        assertTrue(LandHistory.summarize(entries, now, 1, 0).contains("and 2 more."));
        assertEquals("2 h", LandHistory.ago(7_200_000));
        assertEquals("3 d", LandHistory.ago(86_400_000L * 3));
    }
}
