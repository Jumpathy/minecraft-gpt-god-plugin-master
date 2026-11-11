package net.bigyous.gptgodmc.god.vision;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Color;
import org.bukkit.Material;

/**
 * Per-material colors and how light passes through each block. Built once on the server
 * thread from Minecraft's own map colors, with overrides where map colors mislead (ores
 * are map-colored as plain stone, glass has no map color, ...).
 */
public final class Palette {
    public static final byte EMPTY = 0;
    public static final byte SOLID = 1;
    public static final byte WATER = 2;
    public static final byte GLASS = 3;
    public static final byte GLOW = 4;

    private static volatile Palette instance;

    final int[] rgb;
    final byte[] kind;
    final boolean[] xrayHidden;
    final boolean[] precious;

    private static final Map<String, Integer> OVERRIDES = Map.ofEntries(
            Map.entry("DIAMOND", 0x5DECF5), Map.entry("EMERALD", 0x17DD62), Map.entry("GOLD", 0xFCEE4B),
            Map.entry("IRON", 0xD8AF93), Map.entry("COPPER", 0xE07B50), Map.entry("COAL", 0x2A2A2A),
            Map.entry("REDSTONE", 0xFF2020), Map.entry("LAPIS", 0x1E46C8), Map.entry("ANCIENT_DEBRIS", 0x6E463C),
            Map.entry("NETHER_QUARTZ", 0xE6DCD2), Map.entry("AMETHYST", 0x9A5CC6));

    private static final Set<String> TERRAIN = Set.of("STONE", "DEEPSLATE", "DIRT", "GRASS_BLOCK", "GRAVEL", "SAND",
            "SANDSTONE", "RED_SAND", "ANDESITE", "DIORITE", "GRANITE", "TUFF", "CALCITE", "NETHERRACK", "BASALT",
            "BLACKSTONE", "END_STONE", "SOUL_SAND", "SOUL_SOIL", "CLAY", "COARSE_DIRT", "ROOTED_DIRT", "MUD", "PODZOL",
            "MYCELIUM", "DIRT_PATH", "SMOOTH_BASALT", "DRIPSTONE_BLOCK", "POINTED_DRIPSTONE", "COBBLED_DEEPSLATE",
            "TERRACOTTA", "SNOW_BLOCK", "PACKED_ICE", "ICE", "BLUE_ICE", "POWDER_SNOW", "MAGMA_BLOCK",
            "CRIMSON_NYLIUM", "WARPED_NYLIUM", "MOSS_BLOCK", "WATER", "BUBBLE_COLUMN", "KELP", "KELP_PLANT",
            "SEAGRASS", "TALL_SEAGRASS", "GLOW_LICHEN", "SCULK", "SMOOTH_STONE", "INFESTED_STONE",
            "INFESTED_DEEPSLATE");

    private Palette(int size) {
        rgb = new int[size];
        kind = new byte[size];
        xrayHidden = new boolean[size];
        precious = new boolean[size];
    }

    /** An empty palette of the given size, for tests. */
    static Palette blank(int size) {
        return new Palette(size);
    }

    /** Must be called on the server thread the first time. */
    public static Palette get() {
        Palette p = instance;
        if (p == null) {
            synchronized (Palette.class) {
                if (instance == null) {
                    instance = build();
                }
                p = instance;
            }
        }
        return p;
    }

    @SuppressWarnings("deprecation")
    private static Palette build() {
        Material[] all = Material.values();
        Palette p = new Palette(all.length);
        for (Material m : all) {
            int i = m.ordinal();
            if (m.isLegacy() || !m.isBlock() || m.isAir()) {
                p.kind[i] = EMPTY;
                continue;
            }
            String name = m.name();
            int color = 0;
            try {
                Color c = m.createBlockData().getMapColor();
                color = c == null ? 0 : c.asRGB();
            } catch (RuntimeException ignored) {
                color = 0;
            }
            byte k = SOLID;
            if (name.equals("WATER") || name.equals("BUBBLE_COLUMN") || name.contains("SEAGRASS") || name.startsWith("KELP")) {
                k = WATER;
                color = 0x3F76E4;
            } else if (name.equals("LAVA")) {
                k = GLOW;
                color = 0xFF7A1C;
            } else if (name.contains("GLASS")) {
                k = GLASS;
                if (color == 0) {
                    color = 0xDDF4FF;
                }
            } else if (isLightSource(name)) {
                k = GLOW;
            } else if (!m.isSolid() && !m.isOccluding() && isFoliageNoise(name)) {
                k = EMPTY;
            } else if (name.equals("BARRIER") || name.equals("LIGHT") || name.equals("STRUCTURE_VOID")) {
                k = EMPTY;
            }
            for (Map.Entry<String, Integer> o : OVERRIDES.entrySet()) {
                if ((name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS") || name.startsWith("BUDDING_AMETHYST")
                        || name.equals("AMETHYST_CLUSTER")) && name.contains(o.getKey())) {
                    color = o.getValue();
                    p.precious[i] = true;
                }
            }
            if (name.equals("CHEST") || name.equals("TRAPPED_CHEST") || name.equals("BARREL")
                    || name.endsWith("SHULKER_BOX") || name.equals("ENDER_CHEST")) {
                color = name.equals("ENDER_CHEST") ? 0x1F3B3A : 0xDC9628;
                p.precious[i] = true;
            }
            if (name.equals("SPAWNER") || name.equals("TRIAL_SPAWNER")) {
                color = 0xDC00DC;
                p.precious[i] = true;
            }
            if (color == 0 && k != EMPTY) {
                color = m.isSolid() ? 0x7F7F7F : 0xBBBBBB;
            }
            p.rgb[i] = color;
            p.kind[i] = k;
            p.xrayHidden[i] = TERRAIN.contains(name);
        }
        return p;
    }

    private static boolean isLightSource(String n) {
        return n.equals("GLOWSTONE") || n.equals("SEA_LANTERN") || n.equals("SHROOMLIGHT") || n.endsWith("LANTERN")
                || n.endsWith("TORCH") || n.endsWith("FROGLIGHT") || n.equals("JACK_O_LANTERN") || n.equals("BEACON")
                || n.equals("END_ROD") || n.endsWith("CAMPFIRE") || n.equals("FIRE") || n.equals("SOUL_FIRE")
                || n.equals("REDSTONE_LAMP") || n.equals("CRYING_OBSIDIAN") || n.equals("NETHER_PORTAL")
                || n.equals("END_PORTAL") || n.equals("RESPAWN_ANCHOR") || n.endsWith("CANDLE");
    }

    private static boolean isFoliageNoise(String n) {
        return n.equals("SHORT_GRASS") || n.equals("GRASS") || n.equals("TALL_GRASS") || n.equals("FERN")
                || n.equals("LARGE_FERN") || n.equals("DEAD_BUSH") || n.endsWith("VINES") || n.equals("VINE")
                || n.equals("TRIPWIRE") || n.equals("HANGING_ROOTS") || n.equals("NETHER_SPROUTS")
                || n.equals("CRIMSON_ROOTS") || n.equals("WARPED_ROOTS") || n.equals("REDSTONE_WIRE")
                || n.toLowerCase(Locale.ROOT).endsWith("_coral_fan");
    }
}
