package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The loot a slain god drops, from godslayer.loot in config.yml: one line per drop, "item count" or
 * "item min-max" (e.g. "nether_star 3", "diamond_block 8-16"), and "relic" for the unique
 * "Heart of <God>". Pure (item names are checked against the server's materials later), so it is
 * unit tested.
 */
public final class GodslayerLoot {
    private GodslayerLoot() {
    }

    /** The largest stack count one line may ask for (keeps a typo from flooding the world). */
    static final int MAX_COUNT = 256;

    /** One line of the loot list. item is lower case without a namespace; null for the relic. */
    public record Drop(String item, int min, int max) {
        public boolean relic() {
            return item == null;
        }

        /** A count between min and max; roll is 0..1. */
        public int count(double roll) {
            double r = Math.max(0, Math.min(0.999999, roll));
            return min + (int) Math.floor(r * (max - min + 1));
        }
    }

    /** What could not be read, for the console. */
    public record Parsed(List<Drop> drops, List<String> problems) {
    }

    public static final List<String> DEFAULTS = List.of("relic", "nether_star 3", "netherite_ingot 8",
            "netherite_block 1", "enchanted_golden_apple 4", "totem_of_undying 2", "elytra 1", "diamond_block 16",
            "heavy_core 1", "echo_shard 8", "experience_bottle 64");

    public static Parsed parse(List<String> lines) {
        List<Drop> drops = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        if (lines == null) return new Parsed(drops, problems);
        for (String raw : lines) {
            if (raw == null || raw.isBlank()) continue;
            String[] parts = raw.trim().toLowerCase(Locale.ROOT).split("\\s+");
            String item = parts[0];
            if (item.startsWith("minecraft:")) item = item.substring("minecraft:".length());
            if (item.equals("relic") || item.equals("heart")) {
                drops.add(new Drop(null, 1, 1));
                continue;
            }
            if (!item.matches("[a-z0-9_]+")) {
                problems.add("\"" + raw + "\": not an item name");
                continue;
            }
            int min = 1;
            int max = 1;
            if (parts.length > 1) {
                String n = parts[1].startsWith("x") ? parts[1].substring(1) : parts[1];
                try {
                    int dash = n.indexOf('-');
                    if (dash > 0) {
                        min = Integer.parseInt(n.substring(0, dash));
                        max = Integer.parseInt(n.substring(dash + 1));
                    } else {
                        min = max = Integer.parseInt(n);
                    }
                } catch (NumberFormatException e) {
                    problems.add("\"" + raw + "\": the count must be a number like 3 or 2-5");
                    continue;
                }
            }
            if (min > max) {
                int t = min;
                min = max;
                max = t;
            }
            min = Math.max(1, Math.min(MAX_COUNT, min));
            max = Math.max(1, Math.min(MAX_COUNT, max));
            drops.add(new Drop(item, min, max));
        }
        return new Parsed(drops, problems);
    }
}
