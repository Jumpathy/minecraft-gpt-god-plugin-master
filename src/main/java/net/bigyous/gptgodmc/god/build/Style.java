package net.bigyous.gptgodmc.god.build;

import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;

import net.bigyous.gptgodmc.god.Persona;

/** A material palette so the same design can look holy, sinister, cozy or ancient. */
public record Style(String id, Material base, Material floor, Material wall, Material pillar, Material trim,
        Material roof, Material roofSlab, Material glass, Material light, Material accent, Material door,
        Material path) {

    public static final Map<String, Style> STYLES = Map.of(
            "violet", new Style("violet", Material.POLISHED_BLACKSTONE, Material.PURPUR_BLOCK, Material.PURPUR_BLOCK,
                    Material.PURPUR_PILLAR, Material.AMETHYST_BLOCK, Material.PURPUR_STAIRS, Material.PURPUR_SLAB,
                    Material.PURPLE_STAINED_GLASS, Material.SOUL_LANTERN, Material.CRYING_OBSIDIAN,
                    Material.DARK_OAK_DOOR, Material.POLISHED_BLACKSTONE),
            "divine", new Style("divine", Material.SMOOTH_QUARTZ, Material.QUARTZ_BLOCK, Material.QUARTZ_BRICKS,
                    Material.QUARTZ_PILLAR, Material.GOLD_BLOCK, Material.QUARTZ_STAIRS, Material.QUARTZ_SLAB,
                    Material.WHITE_STAINED_GLASS, Material.LANTERN, Material.GOLD_BLOCK, Material.BIRCH_DOOR,
                    Material.SMOOTH_SANDSTONE),
            "dark", new Style("dark", Material.BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS,
                    Material.POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BASALT, Material.CRYING_OBSIDIAN,
                    Material.BLACKSTONE_STAIRS, Material.BLACKSTONE_SLAB, Material.RED_STAINED_GLASS,
                    Material.SOUL_LANTERN, Material.RED_NETHER_BRICKS, Material.CRIMSON_DOOR,
                    Material.POLISHED_BLACKSTONE),
            "rustic", new Style("rustic", Material.COBBLESTONE, Material.SPRUCE_PLANKS, Material.OAK_PLANKS,
                    Material.OAK_LOG, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_STAIRS, Material.SPRUCE_SLAB,
                    Material.GLASS, Material.LANTERN, Material.MOSSY_COBBLESTONE, Material.SPRUCE_DOOR,
                    Material.DIRT_PATH),
            "stone", new Style("stone", Material.COBBLED_DEEPSLATE, Material.POLISHED_ANDESITE, Material.STONE_BRICKS,
                    Material.DEEPSLATE_TILES, Material.MOSSY_STONE_BRICKS, Material.DEEPSLATE_TILE_STAIRS,
                    Material.DEEPSLATE_TILE_SLAB, Material.GLASS, Material.LANTERN, Material.CHISELED_STONE_BRICKS,
                    Material.DARK_OAK_DOOR, Material.GRAVEL));

    /** "auto" picks a style that suits the god's archetype. */
    public static Style resolve(String requested) {
        String key = requested == null ? "auto" : requested.toLowerCase(Locale.ROOT);
        if (STYLES.containsKey(key)) {
            return STYLES.get(key);
        }
        Persona p = Persona.current();
        String archetype = p == null || p.archetype == null ? "" : p.archetype;
        return STYLES.get(switch (archetype) {
            case "evil" -> "dark";
            case "chill" -> "rustic";
            case "benevolent" -> "divine";
            case "wrathful" -> "stone";
            default -> "violet";
        });
    }
}
