package net.bigyous.gptgodmc.god.vessel;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;

/**
 * How the vessel is dressed: one block material per body region (like the regions of a player skin),
 * an ornament on its head, a cape, and the colors of its outline and aura. With the resource pack the
 * god wears its own painted skin of the same name ({@link VesselTextures}); the blocks are the fallback.
 */
public record VesselLook(String name, Map<Slot, Material> materials, Ornament ornament, boolean cape,
        Color outline, Color mote, Color smokeFrom, Color smokeTo, Particle sparkle) {

    /** Regions of the body, like the parts of a skin texture. BEARD and PATCH are left out by most looks. */
    public enum Slot {
        SKIN, EYES, TORSO, SLEEVES, HANDS, LEGS, BOOTS, BELT, BANDS, GEM, HAIR, ORNAMENT, CAPE, CAPE_LINING,
        BEARD, PATCH
    }

    /** What the vessel wears on its head; built from blocks in the ORNAMENT material (see VesselModel). */
    public enum Ornament {
        HORNS, HALO, NONE, LAUREL, CROWN, SHARDS, TRIDENT, CREST, ANTLERS, EARS, SUNDISK, CRESCENT, COG, JESTER,
        HAT, SHATTERED;

        /** "halo", "Antlers"... or the fallback when the text names no ornament. */
        public static Ornament parse(String text, Ornament fallback) {
            if (text == null) return fallback;
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }
    }

    /** Every look, in menu order, with a one-line description. The first four are the original forms. */
    public static final Map<String, String> DESCRIPTIONS = descriptions();

    public static final List<String> NAMES = List.copyOf(DESCRIPTIONS.keySet());

    private static Map<String, String> descriptions() {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("shade", "Dark violet nebula flesh, glowing veins, a rune seal and amethyst ram horns (the default).");
        d.put("radiant", "Pale and golden in white robes with a turning sunburst and a halo.");
        d.put("infernal", "Crimson flesh split by ember cracks, burning eyes and blackstone horns.");
        d.put("void", "A living starfield with a spiral galaxy over the heart and an amethyst halo.");
        d.put("thunder", "A sky father: white robes, gold trim, white beard, golden laurel and lightning.");
        d.put("allfather", "A one-eyed wanderer in grey, with a long braided beard, a wide hat and a raven cloak.");
        d.put("tide", "Lord of the sea: scale armor, a seaweed beard, a coral crown and a trident seal.");
        d.put("underworld", "King of the dead: ashen skin, black robes trimmed in embers, an iron crown.");
        d.put("warrior", "A wise war goddess in a bronze cuirass, crested helm and crimson cloak.");
        d.put("sun", "The falcon-eyed sun: bronze skin, a striped headdress and a burning disc above.");
        d.put("jackal", "The black jackal of the tombs, with gold and lapis collars and tall ears.");
        d.put("grove", "A forest god of bark and leaves, with a mossy beard and great antlers.");
        d.put("frost", "The winter king: ice-blue skin, frozen tears, a snowflake seal and an icicle crown.");
        d.put("storm", "A thunderhead given form: storm-cloud flesh crackling with lightning, and horns.");
        d.put("plague", "The bone lord: a skull face, rotting shroud, open ribs and a green miasma.");
        d.put("celestial", "Midnight-blue skin mapped with constellations and a turning astrolabe.");
        d.put("clockwork", "A machine god of brass plates and rivets, gears turning in an open chest.");
        d.put("jester", "The trickster: a painted grin, motley diamonds, a ruff and a belled cap.");
        d.put("moon", "A moon goddess, silver and lilac, with the phases on her breast and a crescent above.");
        d.put("titan", "A fire titan of cracked basalt, molten seams, a beard of flame and a burning crown.");
        d.put("abyss", "An eldritch thing of the deep, dotted with lights and blinking eyes, with sculk horns.");
        d.put("hollow", "A porcelain mask mended with gold over a black veil, and a shattered halo.");
        d.put("prism", "A crystalline being whose facets shift through every color, crowned with shards.");
        d.put("bloom", "A spring goddess in rose silk printed with blossoms, crowned with cherry leaves.");
        return d;
    }

    public static String description(String name) {
        return DESCRIPTIONS.getOrDefault(name, "");
    }

    /** A material for the slot, or null when this look leaves that region out. */
    public Material material(Slot slot) {
        return materials.get(slot);
    }

    public VesselLook with(Ornament newOrnament, boolean newCape, Color newOutline) {
        return new VesselLook(name, materials, newOrnament, newCape, newOutline, mote, smokeFrom, smokeTo, sparkle);
    }

    // ---- Choosing a look -----------------------------------------------------------------------

    /** The looks that suit each archetype, best first. */
    static final Map<String, List<String>> BY_ARCHETYPE = Map.of(
            "benevolent", List.of("radiant", "sun", "bloom", "moon", "grove"),
            "wrathful", List.of("radiant", "thunder", "warrior", "storm", "allfather"),
            "evil", List.of("infernal", "underworld", "plague", "jackal", "titan", "hollow"),
            "cosmic", List.of("void", "celestial", "abyss", "prism", "clockwork", "hollow"),
            "trickster", List.of("jester", "prism", "shade", "storm"),
            "chill", List.of("shade", "tide", "grove", "moon", "bloom"));

    /** Words in a god's name or epithet that call for a look ("the Storm-Caller" -> storm). */
    static final Map<String, Pattern> KEYWORDS = keywords();

    private static Map<String, Pattern> keywords() {
        Map<String, String> words = new LinkedHashMap<>();
        words.put("thunder", "thunder|lightning|sky ?father|heavens?");
        words.put("storm", "storms?|tempest|gale|cyclone|hurricane|winds?");
        words.put("tide", "seas?|oceans?|tides?|waves?|deeps? waters?|depths of the sea|brine|trident");
        words.put("frost", "frost|ice|icy|winter|snows?|cold|glacier|rime");
        words.put("sun", "suns?|solar|dawn|daybreak|noon|falcon");
        words.put("moon", "moons?|lunar|night|dusk|tides of silver");
        words.put("celestial", "stars?|starry|constellations?|heavenly|celestial|astral");
        words.put("grove", "forests?|groves?|woods?|trees?|wild|antler|oak|green|verdant");
        words.put("bloom", "bloom|blossoms?|flowers?|spring|love|petals?|rose");
        words.put("underworld", "underworld|dead|death|graves?|souls?|shades|ferryman");
        words.put("plague", "plague|pestilence|rot|decay|bones?|blight|famine");
        words.put("titan", "fire|flames?|embers?|forge|magma|volcano|burning|cinders?");
        words.put("warrior", "war|battle|spears?|shields?|valou?r|wisdom|strategy");
        words.put("allfather", "all-?father|ravens?|wanderer|runes?|one-?eyed|gallows");
        words.put("clockwork", "machines?|gears?|clocks?|clockwork|iron|engines?|cogs?");
        words.put("jester", "jest|jester|tricks?|trickster|mischief|fools?|jokes?|riddles?");
        words.put("jackal", "jackal|tombs?|embalm|judge|scales of|mummy|sands?");
        words.put("abyss", "abyss|abyssal|eyes|leviathan|kraken|drowned|unfathomable");
        words.put("hollow", "hollow|masks?|porcelain|veiled|silent|empty");
        words.put("prism", "prism|crystals?|glass|shards?|rainbows?|light-?bender");
        words.put("void", "void|nothing|beyond|cosmos|galax(y|ies)");
        words.put("infernal", "hell|infernal|demons?|brimstone|sin");
        words.put("radiant", "radiant|holy|light|glory|grace");
        Map<String, Pattern> out = new LinkedHashMap<>();
        words.forEach((look, re) -> out.put(look, Pattern.compile("\\b(" + re + ")\\b", Pattern.CASE_INSENSITIVE)));
        return out;
    }

    /** The configured look; "auto" picks one that suits the god's archetype. */
    public static VesselLook resolve(String configured, String archetype) {
        return resolve(configured, archetype, null);
    }

    /**
     * The configured look. "auto" first looks for a fitting word in the hint (the god's epithet and
     * name: "the Drowned King" wears tide), else takes its archetype's best look.
     */
    public static VesselLook resolve(String configured, String archetype, String hint) {
        String key = configured == null ? "shade" : configured.toLowerCase(Locale.ROOT).trim();
        if (key.equals("auto")) key = autoName(archetype, hint);
        return byName(key);
    }

    /** The look "auto" chooses for a god of this archetype and name/epithet. */
    public static String autoName(String archetype, String hint) {
        if (hint != null && !hint.isBlank()) {
            for (Map.Entry<String, Pattern> e : KEYWORDS.entrySet()) {
                if (e.getValue().matcher(hint).find()) return e.getKey();
            }
        }
        List<String> pool = BY_ARCHETYPE.get(archetype == null ? "" : archetype.toLowerCase(Locale.ROOT));
        return pool == null ? "shade" : pool.get(0);
    }

    /** The looks that suit an archetype, best first (every look when it is unknown). */
    public static List<String> suited(String archetype) {
        return BY_ARCHETYPE.getOrDefault(archetype == null ? "" : archetype.toLowerCase(Locale.ROOT), NAMES);
    }

    public static VesselLook byName(String key) {
        return switch (key == null ? "" : key) {
            case "radiant" -> radiant();
            case "infernal" -> infernal();
            case "void" -> voidLook();
            case "thunder" -> thunder();
            case "allfather" -> allfather();
            case "tide" -> tide();
            case "underworld" -> underworld();
            case "warrior" -> warrior();
            case "sun" -> sun();
            case "jackal" -> jackal();
            case "grove" -> grove();
            case "frost" -> frost();
            case "storm" -> storm();
            case "plague" -> plague();
            case "celestial" -> celestial();
            case "clockwork" -> clockwork();
            case "jester" -> jester();
            case "moon" -> moon();
            case "titan" -> titan();
            case "abyss" -> abyss();
            case "hollow" -> hollow();
            case "prism" -> prism();
            case "bloom" -> bloom();
            default -> shade();
        };
    }

    // ---- The original forms --------------------------------------------------------------------

    /** Dark violet skin, glowing eyes, amethyst ram horns and a void-black cape. */
    public static VesselLook shade() {
        Map<Slot, Material> m = new EnumMap<>(Slot.class);
        m.put(Slot.SKIN, Material.PURPLE_CONCRETE);
        m.put(Slot.EYES, Material.WHITE_CONCRETE);
        m.put(Slot.TORSO, Material.PURPLE_CONCRETE);
        m.put(Slot.SLEEVES, Material.PURPLE_CONCRETE);
        m.put(Slot.HANDS, Material.PURPLE_CONCRETE);
        m.put(Slot.LEGS, Material.BLACK_CONCRETE);
        m.put(Slot.BOOTS, Material.OBSIDIAN);
        m.put(Slot.BELT, Material.AMETHYST_BLOCK);
        m.put(Slot.BANDS, Material.AMETHYST_BLOCK);
        m.put(Slot.GEM, Material.PEARLESCENT_FROGLIGHT);
        m.put(Slot.ORNAMENT, Material.AMETHYST_BLOCK);
        m.put(Slot.CAPE, Material.BLACK_CONCRETE);
        m.put(Slot.CAPE_LINING, Material.PURPLE_WOOL);
        return new VesselLook("shade", m, Ornament.HORNS, true, Color.fromRGB(0xB24BF3), Color.fromRGB(0xC77DFF),
                Color.fromRGB(0x9D4EDD), Color.fromRGB(0x240046), Particle.END_ROD);
    }

    /** Pale and golden, in white robes with a halo. */
    public static VesselLook radiant() {
        Map<Slot, Material> m = new EnumMap<>(Slot.class);
        m.put(Slot.SKIN, Material.SMOOTH_SANDSTONE);
        m.put(Slot.EYES, Material.LIGHT_BLUE_CONCRETE);
        m.put(Slot.HAIR, Material.GOLD_BLOCK);
        m.put(Slot.TORSO, Material.WHITE_CONCRETE);
        m.put(Slot.SLEEVES, Material.WHITE_CONCRETE);
        m.put(Slot.HANDS, Material.SMOOTH_SANDSTONE);
        m.put(Slot.LEGS, Material.WHITE_CONCRETE);
        m.put(Slot.BOOTS, Material.GOLD_BLOCK);
        m.put(Slot.BELT, Material.GOLD_BLOCK);
        m.put(Slot.BANDS, Material.GOLD_BLOCK);
        m.put(Slot.GEM, Material.LIGHT_BLUE_CONCRETE);
        m.put(Slot.ORNAMENT, Material.GOLD_BLOCK);
        m.put(Slot.CAPE, Material.WHITE_WOOL);
        m.put(Slot.CAPE_LINING, Material.YELLOW_CONCRETE);
        return new VesselLook("radiant", m, Ornament.HALO, true, Color.fromRGB(0xFFD54A), Color.fromRGB(0xFFF1B5),
                Color.fromRGB(0xFFE08A), Color.fromRGB(0xFFFFFF), Particle.END_ROD);
    }

    /** Crimson skin, blackstone horns, burning eyes and a smouldering belt. */
    public static VesselLook infernal() {
        Map<Slot, Material> m = new EnumMap<>(Slot.class);
        m.put(Slot.SKIN, Material.RED_CONCRETE);
        m.put(Slot.EYES, Material.OCHRE_FROGLIGHT);
        m.put(Slot.TORSO, Material.RED_CONCRETE);
        m.put(Slot.SLEEVES, Material.RED_CONCRETE);
        m.put(Slot.HANDS, Material.BLACK_CONCRETE);
        m.put(Slot.LEGS, Material.BLACK_CONCRETE);
        m.put(Slot.BOOTS, Material.POLISHED_BLACKSTONE);
        m.put(Slot.BELT, Material.POLISHED_BLACKSTONE);
        m.put(Slot.BANDS, Material.MAGMA_BLOCK);
        m.put(Slot.GEM, Material.MAGMA_BLOCK);
        m.put(Slot.ORNAMENT, Material.POLISHED_BLACKSTONE);
        m.put(Slot.CAPE, Material.BLACK_CONCRETE);
        m.put(Slot.CAPE_LINING, Material.RED_WOOL);
        return new VesselLook("infernal", m, Ornament.HORNS, true, Color.fromRGB(0xFF4A1C), Color.fromRGB(0xFF7B29),
                Color.fromRGB(0xD62828), Color.fromRGB(0x1A0A0A), Particle.SMALL_FLAME);
    }

    /** A star-flecked silhouette from beyond the sky, crowned with an amethyst halo. */
    public static VesselLook voidLook() {
        Map<Slot, Material> m = new EnumMap<>(Slot.class);
        m.put(Slot.SKIN, Material.BLACK_CONCRETE);
        m.put(Slot.EYES, Material.WHITE_CONCRETE);
        m.put(Slot.TORSO, Material.CRYING_OBSIDIAN);
        m.put(Slot.SLEEVES, Material.CRYING_OBSIDIAN);
        m.put(Slot.HANDS, Material.BLACK_CONCRETE);
        m.put(Slot.LEGS, Material.CRYING_OBSIDIAN);
        m.put(Slot.BOOTS, Material.BLACK_CONCRETE);
        m.put(Slot.BELT, Material.AMETHYST_BLOCK);
        m.put(Slot.BANDS, Material.AMETHYST_BLOCK);
        m.put(Slot.GEM, Material.WHITE_CONCRETE);
        m.put(Slot.ORNAMENT, Material.AMETHYST_BLOCK);
        m.put(Slot.CAPE, Material.BLACK_CONCRETE);
        m.put(Slot.CAPE_LINING, Material.CRYING_OBSIDIAN);
        return new VesselLook("void", m, Ornament.HALO, true, Color.fromRGB(0x8E7CFF), Color.fromRGB(0xB8A9FF),
                Color.fromRGB(0x5A189A), Color.fromRGB(0x05010A), Particle.REVERSE_PORTAL);
    }

    // ---- Mythic forms --------------------------------------------------------------------------

    /**
     * A look from slot/material pairs, in the order SKIN, EYES, HAIR, BEARD, TORSO, SLEEVES, HANDS, LEGS,
     * BOOTS, BELT, BANDS, GEM, ORNAMENT, CAPE, CAPE_LINING (null leaves a region out), plus PATCH when
     * one-eyed.
     */
    private static VesselLook look(String name, Ornament ornament, int outline, int mote, int smokeFrom,
            int smokeTo, Particle sparkle, Material... mats) {
        Slot[] order = { Slot.SKIN, Slot.EYES, Slot.HAIR, Slot.BEARD, Slot.TORSO, Slot.SLEEVES, Slot.HANDS,
                Slot.LEGS, Slot.BOOTS, Slot.BELT, Slot.BANDS, Slot.GEM, Slot.ORNAMENT, Slot.CAPE, Slot.CAPE_LINING,
                Slot.PATCH };
        Map<Slot, Material> m = new EnumMap<>(Slot.class);
        for (int i = 0; i < mats.length; i++) {
            if (mats[i] != null) m.put(order[i], mats[i]);
        }
        return new VesselLook(name, m, ornament, true, Color.fromRGB(outline), Color.fromRGB(mote),
                Color.fromRGB(smokeFrom), Color.fromRGB(smokeTo), sparkle);
    }

    public static VesselLook thunder() {
        return look("thunder", Ornament.LAUREL, 0x9FD8FF, 0xE6F6FF, 0xBFD9FF, 0x2A3A5A, Particle.END_ROD,
                Material.SMOOTH_SANDSTONE, Material.LIGHT_BLUE_CONCRETE, Material.WHITE_CONCRETE, Material.WHITE_WOOL,
                Material.WHITE_CONCRETE, Material.WHITE_CONCRETE, Material.SMOOTH_SANDSTONE, Material.WHITE_CONCRETE,
                Material.GOLD_BLOCK, Material.GOLD_BLOCK, Material.GOLD_BLOCK, Material.SEA_LANTERN,
                Material.GOLD_BLOCK, Material.BLUE_CONCRETE, Material.WHITE_WOOL);
    }

    public static VesselLook allfather() {
        return look("allfather", Ornament.HAT, 0x8FB4D8, 0xC8D4E0, 0x5A6270, 0x0A0C10, Particle.ENCHANT,
                Material.BIRCH_PLANKS, Material.LIGHT_BLUE_CONCRETE, Material.LIGHT_GRAY_CONCRETE,
                Material.LIGHT_GRAY_WOOL, Material.GRAY_CONCRETE, Material.GRAY_CONCRETE, Material.BIRCH_PLANKS,
                Material.BLACK_CONCRETE, Material.BROWN_CONCRETE, Material.BROWN_CONCRETE, Material.IRON_BLOCK,
                Material.SEA_LANTERN, Material.GRAY_WOOL, Material.BLACK_WOOL, Material.GRAY_WOOL,
                Material.BLACK_CONCRETE);
    }

    public static VesselLook tide() {
        return look("tide", Ornament.TRIDENT, 0x3FE0D0, 0x9FFFF0, 0x2FA3A0, 0x04202A, Particle.BUBBLE_POP,
                Material.PRISMARINE_BRICKS, Material.SEA_LANTERN, Material.WARPED_WART_BLOCK,
                Material.WARPED_WART_BLOCK, Material.DARK_PRISMARINE, Material.DARK_PRISMARINE,
                Material.PRISMARINE_BRICKS, Material.BLUE_CONCRETE, Material.GOLD_BLOCK, Material.GOLD_BLOCK,
                Material.GOLD_BLOCK, Material.SEA_LANTERN, Material.BRAIN_CORAL_BLOCK, Material.CYAN_CONCRETE,
                Material.LIGHT_BLUE_WOOL);
    }

    public static VesselLook underworld() {
        return look("underworld", Ornament.CROWN, 0xFF6A2A, 0xFF9A4A, 0x3A3A40, 0x050505, Particle.FLAME,
                Material.LIGHT_GRAY_CONCRETE, Material.SHROOMLIGHT, Material.BLACK_CONCRETE, Material.BLACK_WOOL,
                Material.BLACK_CONCRETE, Material.BLACK_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.BLACK_WOOL,
                Material.OBSIDIAN, Material.NETHERITE_BLOCK, Material.MAGMA_BLOCK, Material.SHROOMLIGHT,
                Material.NETHERITE_BLOCK, Material.BLACK_CONCRETE, Material.NETHER_WART_BLOCK);
    }

    public static VesselLook warrior() {
        return look("warrior", Ornament.CREST, 0xE0A458, 0xFFE0A0, 0xC07A3A, 0x2A1A0A, Particle.CRIT,
                Material.SMOOTH_SANDSTONE, Material.LIGHT_BLUE_CONCRETE, Material.COPPER_BLOCK, null,
                Material.CUT_COPPER, Material.BROWN_TERRACOTTA, Material.SMOOTH_SANDSTONE, Material.BROWN_TERRACOTTA,
                Material.COPPER_BLOCK, Material.BROWN_CONCRETE, Material.COPPER_BLOCK, Material.SEA_LANTERN,
                Material.RED_WOOL, Material.RED_CONCRETE, Material.RED_WOOL);
    }

    public static VesselLook sun() {
        return look("sun", Ornament.SUNDISK, 0xFFB12E, 0xFFE07A, 0xFFC24A, 0x5A2A00, Particle.WAX_ON,
                Material.TERRACOTTA, Material.GLOWSTONE, Material.LAPIS_BLOCK, null, Material.TERRACOTTA,
                Material.TERRACOTTA, Material.TERRACOTTA, Material.WHITE_CONCRETE, Material.GOLD_BLOCK,
                Material.GOLD_BLOCK, Material.LAPIS_BLOCK, Material.SHROOMLIGHT, Material.GOLD_BLOCK,
                Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE);
    }

    public static VesselLook jackal() {
        return look("jackal", Ornament.EARS, 0xFFD54A, 0xFFE08A, 0x3A2A0A, 0x000000, Particle.SOUL,
                Material.BLACK_CONCRETE, Material.OCHRE_FROGLIGHT, Material.GOLD_BLOCK, null, Material.BLACK_CONCRETE,
                Material.BLACK_CONCRETE, Material.BLACK_CONCRETE, Material.GOLD_BLOCK, Material.GOLD_BLOCK,
                Material.LAPIS_BLOCK, Material.GOLD_BLOCK, Material.LAPIS_BLOCK, Material.BLACK_CONCRETE,
                Material.BLACK_WOOL, Material.LAPIS_BLOCK);
    }

    public static VesselLook grove() {
        return look("grove", Ornament.ANTLERS, 0x8AE04A, 0xC8FF8A, 0x5A9A2A, 0x0A1A05, Particle.COMPOSTER,
                Material.SPRUCE_WOOD, Material.VERDANT_FROGLIGHT, Material.AZALEA_LEAVES, Material.MOSS_BLOCK,
                Material.SPRUCE_WOOD, Material.SPRUCE_WOOD, Material.SPRUCE_WOOD, Material.DARK_OAK_WOOD,
                Material.ROOTED_DIRT, Material.MOSS_BLOCK, Material.MOSS_BLOCK, Material.VERDANT_FROGLIGHT,
                Material.MUSHROOM_STEM, Material.MOSS_BLOCK, Material.FLOWERING_AZALEA_LEAVES);
    }

    public static VesselLook frost() {
        return look("frost", Ornament.SHARDS, 0x9FE4FF, 0xE6F8FF, 0xBFE6FF, 0x1A3A6A, Particle.SNOWFLAKE,
                Material.PACKED_ICE, Material.SEA_LANTERN, Material.SNOW_BLOCK, Material.WHITE_WOOL,
                Material.LIGHT_BLUE_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.PACKED_ICE, Material.BLUE_CONCRETE,
                Material.BLUE_ICE, Material.IRON_BLOCK, Material.BLUE_ICE, Material.SEA_LANTERN, Material.BLUE_ICE,
                Material.BLUE_CONCRETE, Material.WHITE_WOOL);
    }

    public static VesselLook storm() {
        return look("storm", Ornament.HORNS, 0x7FD0FF, 0xC8F4FF, 0x6A7486, 0x0A0E16, Particle.ELECTRIC_SPARK,
                Material.GRAY_CONCRETE, Material.SEA_LANTERN, Material.GRAY_WOOL, null, Material.CYAN_TERRACOTTA,
                Material.CYAN_TERRACOTTA, Material.GRAY_CONCRETE, Material.GRAY_WOOL, Material.POLISHED_DEEPSLATE,
                Material.IRON_BLOCK, Material.LIGHT_BLUE_CONCRETE, Material.SEA_LANTERN, Material.POLISHED_DEEPSLATE,
                Material.GRAY_CONCRETE, Material.LIGHT_BLUE_WOOL);
    }

    public static VesselLook plague() {
        return look("plague", Ornament.HORNS, 0x9AFF4A, 0xC8FF8A, 0x6A8A2A, 0x0A0F05, Particle.SNEEZE,
                Material.BONE_BLOCK, Material.VERDANT_FROGLIGHT, null, null, Material.GREEN_TERRACOTTA,
                Material.GREEN_TERRACOTTA, Material.BONE_BLOCK, Material.GRAY_TERRACOTTA, Material.BONE_BLOCK,
                Material.BROWN_TERRACOTTA, Material.BONE_BLOCK, Material.VERDANT_FROGLIGHT, Material.BONE_BLOCK,
                Material.BLACK_TERRACOTTA, Material.GREEN_TERRACOTTA);
    }

    public static VesselLook celestial() {
        return look("celestial", Ornament.HALO, 0xA8B8FF, 0xE0E8FF, 0x6A7ADA, 0x05081A, Particle.END_ROD,
                Material.BLUE_CONCRETE, Material.SEA_LANTERN, Material.WHITE_CONCRETE, null, Material.BLUE_TERRACOTTA,
                Material.BLUE_TERRACOTTA, Material.BLUE_CONCRETE, Material.BLACK_CONCRETE, Material.IRON_BLOCK,
                Material.IRON_BLOCK, Material.IRON_BLOCK, Material.SEA_LANTERN, Material.QUARTZ_BLOCK,
                Material.BLACK_CONCRETE, Material.BLUE_WOOL);
    }

    public static VesselLook clockwork() {
        return look("clockwork", Ornament.COG, 0x6AD8FF, 0xFFD08A, 0x8A8A8A, 0x1A1410, Particle.SCRAPE,
                Material.RAW_GOLD_BLOCK, Material.SEA_LANTERN, null, null, Material.COPPER_BLOCK, Material.CUT_COPPER,
                Material.IRON_BLOCK, Material.SMOOTH_BASALT, Material.NETHERITE_BLOCK, Material.EXPOSED_COPPER,
                Material.CUT_COPPER, Material.SEA_LANTERN, Material.CUT_COPPER, Material.BROWN_CONCRETE,
                Material.BLUE_CONCRETE);
    }

    public static VesselLook jester() {
        return look("jester", Ornament.JESTER, 0xFF4AE0, 0xFFD54A, 0xB21AA8, 0x2A0A3A, Particle.NOTE,
                Material.WHITE_CONCRETE, Material.PEARLESCENT_FROGLIGHT, Material.PURPLE_WOOL, null,
                Material.PURPLE_CONCRETE, Material.YELLOW_CONCRETE, Material.WHITE_CONCRETE, Material.GREEN_CONCRETE,
                Material.PURPLE_CONCRETE, Material.GOLD_BLOCK, Material.WHITE_WOOL, Material.GOLD_BLOCK,
                Material.PURPLE_WOOL, Material.PURPLE_WOOL, Material.YELLOW_WOOL);
    }

    public static VesselLook moon() {
        return look("moon", Ornament.CRESCENT, 0xC8D8FF, 0xFFFFFF, 0xA8B8E8, 0x0A0E2A, Particle.GLOW,
                Material.CALCITE, Material.LIGHT_BLUE_CONCRETE, Material.WHITE_CONCRETE, null,
                Material.LIGHT_BLUE_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA, Material.CALCITE,
                Material.BLUE_TERRACOTTA, Material.IRON_BLOCK, Material.IRON_BLOCK, Material.IRON_BLOCK,
                Material.SEA_LANTERN, Material.SEA_LANTERN, Material.BLUE_WOOL, Material.LIGHT_GRAY_WOOL);
    }

    public static VesselLook titan() {
        return look("titan", Ornament.SHARDS, 0xFF8A1E, 0xFFB347, 0xFF6A00, 0x1A0A00, Particle.LAVA,
                Material.BLACKSTONE, Material.SHROOMLIGHT, Material.SHROOMLIGHT, Material.MAGMA_BLOCK,
                Material.BLACKSTONE, Material.BLACKSTONE, Material.BLACKSTONE, Material.SMOOTH_BASALT,
                Material.BLACKSTONE, Material.MAGMA_BLOCK, Material.MAGMA_BLOCK, Material.SHROOMLIGHT,
                Material.MAGMA_BLOCK, Material.BLACK_CONCRETE, Material.ORANGE_CONCRETE);
    }

    public static VesselLook abyss() {
        return look("abyss", Ornament.HORNS, 0x2AE8D0, 0x7FFFE8, 0x0E6A6A, 0x000A0A, Particle.SCULK_SOUL,
                Material.DARK_PRISMARINE, Material.OCHRE_FROGLIGHT, Material.WARPED_WART_BLOCK, null, Material.SCULK,
                Material.DARK_PRISMARINE, Material.DARK_PRISMARINE, Material.SCULK, Material.DEEPSLATE,
                Material.OXIDIZED_COPPER, Material.OXIDIZED_COPPER, Material.OCHRE_FROGLIGHT, Material.SCULK,
                Material.BLACK_CONCRETE, Material.WARPED_WART_BLOCK);
    }

    public static VesselLook hollow() {
        return look("hollow", Ornament.SHATTERED, 0xF6E3A8, 0xFFF0C0, 0x2A2A30, 0x000000, Particle.WHITE_ASH,
                Material.SMOOTH_QUARTZ, Material.OCHRE_FROGLIGHT, Material.BLACK_WOOL, null, Material.BLACK_CONCRETE,
                Material.BLACK_CONCRETE, Material.SMOOTH_QUARTZ, Material.BLACK_CONCRETE, Material.SMOOTH_QUARTZ,
                Material.GOLD_BLOCK, Material.GOLD_BLOCK, Material.GOLD_BLOCK, Material.GOLD_BLOCK,
                Material.BLACK_WOOL, Material.OBSIDIAN);
    }

    public static VesselLook prism() {
        return look("prism", Ornament.SHARDS, 0xE8FFFF, 0xFFC8F0, 0x8A6AFF, 0x0A0A1A, Particle.FIREWORK,
                Material.CALCITE, Material.SEA_LANTERN, Material.AMETHYST_BLOCK, null, Material.BLACK_CONCRETE,
                Material.MAGENTA_CONCRETE, Material.CALCITE, Material.CYAN_CONCRETE, Material.AMETHYST_BLOCK,
                Material.DIAMOND_BLOCK, Material.DIAMOND_BLOCK, Material.SEA_LANTERN, Material.DIAMOND_BLOCK,
                Material.BLACK_CONCRETE, Material.MAGENTA_WOOL);
    }

    public static VesselLook bloom() {
        return look("bloom", Ornament.LAUREL, 0xFF9AC8, 0xFFD8E8, 0xFFB8D8, 0x3A1A2A, Particle.SPORE_BLOSSOM_AIR,
                Material.WHITE_TERRACOTTA, Material.PEARLESCENT_FROGLIGHT, Material.PINK_WOOL, null,
                Material.PINK_CONCRETE, Material.PINK_CONCRETE, Material.WHITE_TERRACOTTA, Material.LIME_CONCRETE,
                Material.MOSS_BLOCK, Material.FLOWERING_AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES,
                Material.PEARLESCENT_FROGLIGHT, Material.CHERRY_LEAVES, Material.PINK_WOOL, Material.WHITE_WOOL);
    }
}
