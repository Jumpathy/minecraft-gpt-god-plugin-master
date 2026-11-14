package net.bigyous.gptgodmc.god.build;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.tools.ToolException;

/** Everything the god can build: its own designs, vanilla structures/templates, and user schematics. */
public final class Blueprints {
    /** Whole vanilla structures generated with /place structure. */
    public static final Map<String, String> VANILLA = new LinkedHashMap<>();

    static {
        VANILLA.put("village_plains", "a plains village: houses, farms, a well, villagers");
        VANILLA.put("village_desert", "a sandstone desert village with villagers");
        VANILLA.put("village_savanna", "an acacia savanna village with villagers");
        VANILLA.put("village_snowy", "a snowy village of spruce and snow");
        VANILLA.put("village_taiga", "a spruce taiga village");
        VANILLA.put("desert_pyramid", "a sandstone desert temple with hidden treasure (and a TNT trap)");
        VANILLA.put("jungle_pyramid", "a mossy jungle temple with traps and treasure");
        VANILLA.put("igloo", "an igloo (sometimes with a secret basement)");
        VANILLA.put("swamp_hut", "a witch's hut on stilts");
        VANILLA.put("pillager_outpost", "a pillager watchtower — pillagers will gather there");
        VANILLA.put("ruined_portal", "a ruined nether portal with loot");
        VANILLA.put("shipwreck_beached", "a wrecked ship on the shore with chests");
        VANILLA.put("trail_ruins", "half-buried ancient ruins");
        VANILLA.put("ancient_city", "a vast deep-dark city with wardens' sculk (huge; best far underground)");
        VANILLA.put("mansion", "a woodland mansion full of illagers (enormous, slow)");
        VANILLA.put("monument", "an ocean monument with guardians (needs deep ocean)");
        VANILLA.put("bastion_remnant", "a piglin bastion (Nether)");
        VANILLA.put("fortress", "a nether fortress (Nether)");
        VANILLA.put("end_city", "an end city with a ship (End)");
    }

    private static List<String> templates;
    private static final Map<String, Plan> schematicCache = new HashMap<>();

    private Blueprints() {
    }

    // ---- Vanilla templates (single pieces such as one village house) ----------------------------

    public static synchronized List<String> templates() {
        if (templates != null) {
            return templates;
        }
        List<String> out = new ArrayList<>();
        try {
            URL url = Bukkit.getServer().getClass().getClassLoader()
                    .getResource("data/minecraft/structures/igloo/top.nbt");
            if (url != null && "jar".equals(url.getProtocol())) {
                String path = url.getPath();
                File jarFile = new File(java.net.URI.create(path.substring(0, path.indexOf('!'))));
                try (JarFile jar = new JarFile(jarFile)) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement().getName();
                        if (name.startsWith("data/minecraft/structures/") && name.endsWith(".nbt")) {
                            out.add(name.substring("data/minecraft/structures/".length(), name.length() - 4));
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not list vanilla structure templates: " + e.getMessage());
        }
        if (out.isEmpty()) {
            out.addAll(List.of("village/plains/houses/plains_small_house_1", "village/plains/houses/plains_temple_3",
                    "village/plains/houses/plains_library_1", "village/desert/houses/desert_temple_1",
                    "village/taiga/houses/taiga_medium_house_1", "village/snowy/houses/snowy_library_1",
                    "igloo/top", "pillager_outpost/watchtower", "ruined_portal/portal_1", "shipwreck/rightsideup_full",
                    "end_city/ship", "bastion/treasure/big_air_full"));
        }
        out.sort(null);
        templates = out;
        return out;
    }

    public static List<String> searchTemplates(String query, int limit) {
        List<String> hits = new ArrayList<>();
        String[] words = query == null ? new String[0] : query.toLowerCase(Locale.ROOT).split("[\\s,]+");
        for (String t : templates()) {
            boolean all = true;
            for (String w : words) {
                if (!w.isBlank() && !t.contains(w)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                hits.add(t);
                if (hits.size() >= limit) break;
            }
        }
        return hits;
    }

    // ---- User schematics ---------------------------------------------------------------------------

    public static File schematicFolder() {
        File dir = new File(GPTGOD.get().getDataFolder(), "schematics");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** name → description (from schematics/catalog.yml when present). */
    public static Map<String, String> schematics() {
        Map<String, String> out = new LinkedHashMap<>();
        File dir = schematicFolder();
        YamlConfiguration catalog = YamlConfiguration.loadConfiguration(new File(dir, "catalog.yml"));
        File[] files = dir.listFiles();
        if (files == null) return out;
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.endsWith(".schem") || n.endsWith(".nbt")) {
                String id = f.getName().substring(0, f.getName().lastIndexOf('.'));
                out.put(id, catalog.getString(id, "") + " [" + n.substring(n.lastIndexOf('.') + 1) + "]");
            }
        }
        return out;
    }

    public static File schematicFile(String id) throws ToolException {
        for (String ext : new String[] { ".schem", ".nbt" }) {
            File f = new File(schematicFolder(), id + ext);
            if (f.isFile()) return f;
        }
        throw new ToolException("No schematic named '" + id + "'. Available: " + schematics().keySet());
    }

    /** Parses a Sponge (.schem, v2 or v3) schematic into a plan centered on its footprint. */
    public static synchronized Plan loadSponge(File file) throws ToolException {
        String cacheKey = file.getAbsolutePath() + "@" + file.lastModified();
        Plan cached = schematicCache.get(cacheKey);
        if (cached != null) return cached;
        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(file.toPath())) {
            root = Nbt.read(in);
        } catch (IOException e) {
            throw new ToolException("Could not read schematic " + file.getName() + ": " + e.getMessage());
        }
        try {
            if (root.get("Schematic") instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> n = (Map<String, Object>) nested;
                root = n;
            }
            int version = root.containsKey("Version") ? Nbt.number(root.get("Version")) : 2;
            int w = Nbt.number(root.get("Width"));
            int h = Nbt.number(root.get("Height"));
            int l = Nbt.number(root.get("Length"));
            if ((long) w * h * l > 600_000) {
                throw new ToolException("That schematic is too large (" + w + "x" + h + "x" + l + ").");
            }
            Map<?, ?> palette;
            byte[] data;
            if (version >= 3) {
                Map<?, ?> blocks = (Map<?, ?>) root.get("Blocks");
                palette = (Map<?, ?>) blocks.get("Palette");
                data = (byte[]) blocks.get("Data");
            } else {
                palette = (Map<?, ?>) root.get("Palette");
                data = (byte[]) root.get("BlockData");
            }
            BlockData[] states = new BlockData[palette.size() + 1];
            for (Map.Entry<?, ?> e : palette.entrySet()) {
                int idx = Nbt.number(e.getValue());
                if (idx >= states.length) states = java.util.Arrays.copyOf(states, idx + 1);
                states[idx] = parseState(e.getKey().toString());
            }
            Plan plan = new Plan();
            int i = 0;
            int pos = 0;
            int ox = w / 2;
            int oz = l / 2;
            while (pos < data.length && i < w * h * l) {
                int value = 0;
                int shift = 0;
                byte b;
                do {
                    b = data[pos++];
                    value |= (b & 0x7F) << shift;
                    shift += 7;
                } while ((b & 0x80) != 0 && pos < data.length);
                BlockData state = value < states.length ? states[value] : null;
                if (state != null) {
                    int x = i % w;
                    int z = (i / w) % l;
                    int y = i / (w * l);
                    plan.set(x - ox, y, z - oz, state);
                }
                i++;
            }
            schematicCache.put(cacheKey, plan);
            return plan;
        } catch (ToolException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ToolException("Unsupported or corrupt schematic " + file.getName() + " (" + e.getMessage()
                    + "). Use WorldEdit .schem (Sponge v2/v3) or vanilla .nbt files.");
        }
    }

    private static BlockData parseState(String state) {
        if (state.endsWith("structure_void")) {
            return null;
        }
        try {
            return Bukkit.createBlockData(state);
        } catch (IllegalArgumentException e) {
            int bracket = state.indexOf('[');
            if (bracket > 0) {
                try {
                    return Bukkit.createBlockData(state.substring(0, bracket));
                } catch (IllegalArgumentException ignored) {
                    // fall through
                }
            }
            return Material.AIR.createBlockData();
        }
    }
}
