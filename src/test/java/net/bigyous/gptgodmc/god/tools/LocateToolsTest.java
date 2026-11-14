package net.bigyous.gptgodmc.god.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** locate: the pure parts (reading the god's words, matching registry names, the compass, the answer). */
class LocateToolsTest {
    /** Names as Paper 26.3's structure registry holds them, plus one from a datapack. */
    private static final List<String> STRUCTURES = List.of("abandoned_camp_bamboo_jungle", "abandoned_camp_birch_forest",
            "abandoned_camp_cherry_grove", "abandoned_camp_dappled_forest", "abandoned_camp_flower_forest",
            "abandoned_camp_forest", "abandoned_camp_meadow", "abandoned_camp_old_growth_birch_forest",
            "abandoned_camp_old_growth_pine_taiga", "abandoned_camp_old_growth_spruce_taiga",
            "abandoned_camp_pale_garden", "abandoned_camp_savanna", "abandoned_camp_snowy_taiga",
            "abandoned_camp_sparse_jungle", "abandoned_camp_swamp", "abandoned_camp_taiga",
            "abandoned_camp_windswept_forest", "abandoned_camp_wooded_badlands", "ancient_city", "bastion_remnant",
            "buried_treasure", "desert_pyramid", "end_city", "fortress", "igloo", "jungle_pyramid", "mansion",
            "mineshaft", "mineshaft_mesa", "monument", "nether_fossil", "ocean_ruin_cold", "ocean_ruin_warm",
            "pillager_outpost", "ruined_portal", "ruined_portal_desert", "ruined_portal_jungle",
            "ruined_portal_mountain", "ruined_portal_nether", "ruined_portal_ocean", "ruined_portal_swamp",
            "shipwreck", "shipwreck_beached", "stronghold", "swamp_hut", "trail_ruins", "trial_chambers",
            "village_desert", "village_plains", "village_savanna", "village_snowy", "village_taiga",
            "mypack:sky_tower");

    private static final List<String> BIOMES = List.of("badlands", "bamboo_jungle", "basalt_deltas", "beach",
            "birch_forest", "cherry_grove", "cold_ocean", "crimson_forest", "dappled_forest", "dark_forest",
            "deep_cold_ocean", "deep_dark", "deep_frozen_ocean", "deep_lukewarm_ocean", "deep_ocean", "desert",
            "dripstone_caves", "end_barrens", "end_highlands", "end_midlands", "eroded_badlands", "flower_forest",
            "forest", "frozen_ocean", "frozen_peaks", "frozen_river", "grove", "ice_spikes", "jagged_peaks", "jungle",
            "lukewarm_ocean", "lush_caves", "mangrove_swamp", "meadow", "mushroom_fields", "nether_wastes", "ocean",
            "old_growth_birch_forest", "old_growth_pine_taiga", "old_growth_spruce_taiga", "pale_garden", "plains",
            "river", "savanna", "savanna_plateau", "small_end_islands", "snowy_beach", "snowy_plains", "snowy_slopes",
            "snowy_taiga", "soul_sand_valley", "sparse_jungle", "stony_peaks", "stony_shore", "sulfur_caves",
            "sunflower_plains", "swamp", "taiga", "the_end", "the_void", "warm_ocean", "warped_forest",
            "windswept_forest", "windswept_gravelly_hills", "windswept_hills", "windswept_savanna", "wooded_badlands");

    private static LocateTools.Choice choose(String what) throws ToolException {
        return LocateTools.choose(what, "auto", STRUCTURES, BIOMES);
    }

    @Test
    void wordsDropTheFillerAndThePlural() {
        assertEquals(List.of("desert", "village"), LocateTools.words("the nearest Desert Villages"));
        assertEquals(List.of("ocean", "monument"), LocateTools.words("Ocean-Monument"));
        assertEquals(List.of("ancient", "city"), LocateTools.words("minecraft:ancient_city"));
        assertEquals(List.of("ancient", "city"), LocateTools.words("ancient cities"));
        assertEquals(List.of("fortress"), LocateTools.words("a fortress"), "a word ending in ss is not a plural");
        assertEquals(List.of("end"), LocateTools.words("the_end"));
        assertEquals(List.of("pyramid"), LocateTools.words("temple"));
        assertEquals(List.of("swamp", "hut"), LocateTools.words("witch hut"));
        assertEquals(List.of("mushroom", "field"), LocateTools.words("mushroom island"));
        assertEquals(List.of(), LocateTools.words("the nearest"));
        assertEquals("village_plains", LocateTools.name("minecraft:village_plains"));
        assertEquals("mypack:sky_tower", LocateTools.name("mypack:sky_tower"));
        assertEquals("desert village", LocateTools.label("the nearest Desert Villages"));
        assertEquals("trial chamber", LocateTools.label("trial chambers"));
    }

    @Test
    void scoresTellAnExactNameFromAKindOfItAndAPart() {
        assertEquals(LocateTools.EXACT, LocateTools.score(List.of("desert", "village"), "village_desert"));
        assertEquals(LocateTools.EXACT, LocateTools.score(LocateTools.words("mushroom fields"), "mushroom_fields"));
        assertEquals(LocateTools.KIND, LocateTools.score(List.of("village"), "village_plains"));
        assertEquals(LocateTools.LOOSE + 1, LocateTools.score(List.of("ocean", "monument"), "monument"));
        assertEquals(LocateTools.PART, LocateTools.score(List.of("ship"), "shipwreck"));
        assertEquals(0, LocateTools.score(List.of("ocean", "monument"), "ocean_ruin_cold"));
        assertEquals(0, LocateTools.score(List.of("en"), "end_city"), "two letters are not a part of a name");
        assertEquals(0, LocateTools.score(List.of(), "village_plains"));
    }

    @Test
    void aGeneralWordIsEveryKindOfIt() throws ToolException {
        LocateTools.Choice village = choose("village");
        assertEquals("structure", village.kind());
        assertEquals(List.of("village_desert", "village_plains", "village_savanna", "village_snowy", "village_taiga"),
                village.names());
        assertEquals(village.names(), choose("is there a Village near us").names());

        LocateTools.Choice portal = choose("ruined portal");
        assertEquals("ruined_portal", portal.names().get(0), "the name itself comes first");
        assertEquals(7, portal.names().size());

        LocateTools.Choice ocean = choose("ocean");
        assertEquals("biome", ocean.kind());
        assertEquals("ocean", ocean.names().get(0));
        assertEquals(9, ocean.names().size());
        assertTrue(ocean.names().containsAll(List.of("deep_ocean", "warm_ocean", "frozen_ocean")));

        assertEquals(List.of("desert_pyramid", "jungle_pyramid"), choose("temple").names());
        assertEquals(List.of("shipwreck", "shipwreck_beached"), choose("ship").names());
        assertEquals(18, choose("abandoned camps").names().size());
    }

    @Test
    void theWordsPickOneStructureOrBiome() throws ToolException {
        assertEquals(new LocateTools.Choice("structure", List.of("village_desert")), choose("nearest desert village"));
        assertEquals(new LocateTools.Choice("structure", List.of("monument")), choose("ocean monument"));
        assertEquals(new LocateTools.Choice("structure", List.of("stronghold")), choose("stronghold"));
        assertEquals(new LocateTools.Choice("structure", List.of("ancient_city")), choose("Ancient City"));
        assertEquals(new LocateTools.Choice("structure", List.of("desert_pyramid")), choose("desert temple"));
        assertEquals(new LocateTools.Choice("structure", List.of("swamp_hut")), choose("a witch hut"));
        assertEquals(new LocateTools.Choice("structure", List.of("fortress")), choose("nether fortress"));
        assertEquals(new LocateTools.Choice("structure", List.of("mansion")), choose("woodland mansion"));
        assertEquals(new LocateTools.Choice("structure", List.of("trial_chambers")), choose("trial chamber"));
        assertEquals(new LocateTools.Choice("structure", List.of("mypack:sky_tower")), choose("sky tower"));
        assertEquals(new LocateTools.Choice("structure", List.of("mypack:sky_tower")), choose("tower"));
        assertEquals(new LocateTools.Choice("biome", List.of("desert")), choose("desert"));
        assertEquals(new LocateTools.Choice("biome", List.of("cherry_grove")), choose("cherry grove"));
        assertEquals(new LocateTools.Choice("biome", List.of("mushroom_fields")), choose("mushroom fields"));
        assertEquals(new LocateTools.Choice("biome", List.of("mushroom_fields")), choose("mushroom island"));
        assertEquals(new LocateTools.Choice("biome", List.of("dark_forest")), choose("dark oak forest"));
        assertEquals(new LocateTools.Choice("biome", List.of("deep_dark")), choose("the deep dark"));
    }

    @Test
    void kindNarrowsTheSearch() throws ToolException {
        assertEquals(new LocateTools.Choice("structure",
                List.of("desert_pyramid", "ruined_portal_desert", "village_desert")),
                LocateTools.choose("desert", "structure", STRUCTURES, BIOMES));
        assertEquals(new LocateTools.Choice("biome", List.of("plains", "snowy_plains", "sunflower_plains")),
                LocateTools.choose("plains", "biome", STRUCTURES, BIOMES));
        ToolException none = assertThrows(ToolException.class,
                () -> LocateTools.choose("village", "biome", STRUCTURES, BIOMES));
        assertTrue(none.getMessage().startsWith("No biome of this world is called 'village'"), none.getMessage());
    }

    @Test
    void anUnknownNameIsRefusedWithTheClosestRealOnes() {
        ToolException typo = assertThrows(ToolException.class, () -> choose("vilage"));
        assertTrue(typo.getMessage().contains("Closest names: village_desert, village_plains, village_savanna, "
                + "village_snowy, village_taiga"), typo.getMessage());
        ToolException dungeon = assertThrows(ToolException.class, () -> choose("dungeon"));
        String listed = dungeon.getMessage().substring(dungeon.getMessage().indexOf("Closest names: ") + 15,
                dungeon.getMessage().indexOf(". Ask with"));
        assertEquals(15, listed.split(", ").length, dungeon.getMessage());
        ToolException nothing = assertThrows(ToolException.class, () -> choose("the nearest"));
        assertTrue(nothing.getMessage().startsWith("Say what to find"), nothing.getMessage());
        assertEquals(1, LocateTools.edits("vilage", "village"));
        assertEquals(List.of("stronghold"), LocateTools.closest(List.of("strongold"), STRUCTURES, 1));
    }

    @Test
    void theKindsNamedAfterTheBiomeHereAreSearchedFirst() throws ToolException {
        List<String> camps = LocateTools.likeliestFirst(choose("camp").names(), "minecraft:birch_forest");
        assertEquals(18, camps.size());
        assertEquals(List.of("abandoned_camp_birch_forest", "abandoned_camp_dappled_forest",
                "abandoned_camp_flower_forest", "abandoned_camp_forest", "abandoned_camp_old_growth_birch_forest",
                "abandoned_camp_windswept_forest"), camps.subList(0, 6));
        assertEquals("abandoned_camp_bamboo_jungle", camps.get(6), "the rest keep their order");
        assertEquals(choose("camp").names(), LocateTools.likeliestFirst(choose("camp").names(), ""));
    }

    @Test
    void compassWordsFollowTheGamesAxes() {
        assertEquals("north", LocateTools.compass(0, -10));
        assertEquals("north-east", LocateTools.compass(10, -10));
        assertEquals("east", LocateTools.compass(10, 0));
        assertEquals("south-east", LocateTools.compass(10, 10));
        assertEquals("south", LocateTools.compass(0, 10));
        assertEquals("south-west", LocateTools.compass(-10, 10));
        assertEquals("west", LocateTools.compass(-10, 0));
        assertEquals("north-west", LocateTools.compass(-10, -10));
        assertEquals("east", LocateTools.compass(100, -30), "within 22.5 degrees of east");
        assertEquals("north-east", LocateTools.compass(100, -50));
        assertEquals("1,180", LocateTools.blocks(1180));
        assertEquals("96", LocateTools.blocks(96));
    }

    @Test
    void theAnswerGivesDirectionDistanceAndPlaceNearestFirst() {
        List<LocateTools.Hit> hits = List.of(new LocateTools.Hit("village_plains", -1591, 0, 387, false),
                new LocateTools.Hit("village_savanna", -120, 0, 96, false));
        assertEquals("Nearest village (village_savanna): 412 blocks north-east of Wren, at (-120, ~, 96 in world). "
                + "Next nearest kinds: village_plains 1,180 blocks west.",
                LocateTools.found("village", "Wren", -411, 387, "world", hits));
        assertEquals("Nearest stronghold: 1,000 blocks south of the world spawn, at (8, ~, 1000 in world).",
                LocateTools.found("stronghold", "the world spawn", 8, 0, "world",
                        List.of(new LocateTools.Hit("stronghold", 8, 0, 1000, false))));
        assertEquals("Nearest cherry grove: 500 blocks east of your vessel, at (500, 96, 0 in world_nether).",
                LocateTools.found("cherry grove", "your vessel", 0, 0, "world_nether",
                        List.of(new LocateTools.Hit("cherry_grove", 500, 96, 0, true))));
        assertEquals("Nearest desert: right here, within 16 blocks of Wren, at (3, 64, 4 in world).",
                LocateTools.found("desert", "Wren", 0.5, 0.5, "world",
                        List.of(new LocateTools.Hit("desert", 3, 64, 4, true))));
    }

    @Test
    void atMostThreeOtherKindsAreNamed() {
        List<LocateTools.Hit> hits = new ArrayList<>();
        for (int i = 5; i >= 1; i--) hits.add(new LocateTools.Hit("kind_" + i, i * 100, 0, 0, false));
        String text = LocateTools.found("kind", "Wren", 0, 0, "world", hits);
        assertTrue(text.startsWith("Nearest kind (kind_1): 100 blocks east of Wren"), text);
        assertTrue(text.endsWith("Next nearest kinds: kind_2 200 blocks east, kind_3 300 blocks east, "
                + "kind_4 400 blocks east."), text);
        assertFalse(text.contains("kind_5"), text);
    }

    @Test
    void nothingFoundIsSaidPlainlyWithTheReach() {
        assertEquals("No cherry grove within about 2,048 blocks of Wren in world.",
                LocateTools.notFound("cherry grove", "biome", "Wren", "world", List.of("cherry_grove")));
        assertEquals("No ocean within about 2,048 blocks of Wren in world (searched: ocean, deep_ocean).",
                LocateTools.notFound("ocean", "biome", "Wren", "world", List.of("ocean", "deep_ocean")));
        String none = LocateTools.notFound("village", "structure", "Wren", "world",
                List.of("village_desert", "village_plains"));
        assertTrue(none.startsWith("No village found around Wren in world (searched: village_desert, village_plains). "),
                none);
        assertTrue(LocateTools.notFound("stronghold", "structure", "Wren", "world", List.of("stronghold"))
                .startsWith("No stronghold found around Wren in world. "));
    }

    @Test
    void thePowerIsRegisteredLast() {
        List<GodTool> all = new ArrayList<>(ToolRegistry.all());
        GodTool locate = all.get(all.size() - 1);
        assertEquals("locate", locate.name());
        assertFalse(locate.mainThread(), "each search is its own visit to the server thread");
        assertEquals(List.of("what"), locate.schema().required());
        assertEquals(List.of("what", "from", "kind"), new ArrayList<>(locate.schema().properties().keySet()));
        assertEquals(List.of("auto", "structure", "biome"), locate.schema().properties().get("kind").get("enum"));
    }
}
