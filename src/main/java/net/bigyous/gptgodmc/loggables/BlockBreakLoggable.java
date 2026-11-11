package net.bigyous.gptgodmc.loggables;

import java.util.Locale;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.event.block.BlockBreakEvent;

import net.bigyous.gptgodmc.StructureManager;
import net.bigyous.gptgodmc.Structure;

/**
 * Mining and destruction. Precious ores and breaking someone else's structure are called out,
 * everything else is folded into "broke N blocks".
 */
public class BlockBreakLoggable extends BaseLoggable {
    private static final Set<Material> PRECIOUS = Set.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.ANCIENT_DEBRIS, Material.GOLD_ORE,
            Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.SPAWNER, Material.BUDDING_AMETHYST);

    private final String player;
    private final String block;
    private final String victimStructure;
    private final String victimBuilder;
    private final boolean notable;
    private int count = 1;

    public BlockBreakLoggable(BlockBreakEvent event) {
        this.player = event.getPlayer().getName();
        Material type = event.getBlock().getType();
        this.block = type.name().toLowerCase(Locale.ROOT);
        String structureName = StructureManager.getStructureThatContains(event.getBlock().getLocation());
        Structure structure = structureName == null ? null : StructureManager.getStructure(structureName);
        if (structure != null && structure.getBuilder() != null
                && !structure.getBuilder().getName().equals(player)) {
            this.victimStructure = structureName;
            this.victimBuilder = structure.getBuilder().getName();
        } else {
            this.victimStructure = null;
            this.victimBuilder = null;
        }
        this.notable = PRECIOUS.contains(type) || Tag.BEDS.isTagged(type) || victimStructure != null;
    }

    @Override
    public String getLog() {
        if (victimStructure != null) {
            return String.format("%s destroyed %d block(s) of %s's structure %s", player, count, victimBuilder,
                    victimStructure);
        }
        if (notable) {
            return String.format("%s mined %d %s", player, count, block);
        }
        return String.format("%s broke %d block(s) (latest: %s)", player, count, block);
    }

    @Override
    public boolean combine(Loggable l) {
        if (!(l instanceof BlockBreakLoggable other) || !other.player.equals(player)) {
            return false;
        }
        boolean sameKind = victimStructure != null
                ? victimStructure.equals(other.victimStructure)
                : other.victimStructure == null && notable == other.notable && (!notable || block.equals(other.block));
        if (sameKind) {
            count += other.count;
        }
        return sameKind;
    }
}
