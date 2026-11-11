package net.bigyous.gptgodmc.god.tools;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;

import net.bigyous.gptgodmc.god.Dropped;
import net.bigyous.gptgodmc.god.GodSettings;

/** Dropping blocks from the sky: an anvil on a blasphemer, a rain of pumpkins, a gift of gold. */
final class DropTools {
    private DropTools() {
    }

    static List<GodTool> all() {
        return List.of(GodTool.sync("drop_block",
                "Drop a block from the sky onto a target: an anvil on a blasphemer, a rain of pumpkins, a "
                        + "dripstone spike, a block of gold as a gift. It really falls as that block, and a mortal "
                        + "may step aside. Use this, never run_commands with summon falling_block (that falls as sand).",
                Schema.object()
                        .str("block", "Block id: anvil, pointed_dripstone, gold_block, pumpkin, cobweb, sand...", true)
                        .str("target", "Where: " + Targets.TARGET_HELP + ".", true)
                        .integer("height", "Blocks above the target it falls from. Default 12.", false, 3, 60)
                        .integer("count", "How many fall, scattered a little. Default 1.", false, 1, 16)
                        .bool("hurts", "Whether it hurts whoever it lands on (heavier and higher hurts more). "
                                + "Default true for anvils and dripstone, false otherwise.", false)
                        .bool("stays", "true = it becomes a block where it lands; false (default) = it shatters "
                                + "on landing and leaves nothing.", false),
                DropTools::drop));
    }

    /** Free air above a point, up to the wanted height (so it never starts inside a ceiling). */
    static int clearHeight(Block from, int wanted) {
        int h = 0;
        Block b = from;
        while (h < wanted && b.getY() < b.getWorld().getMaxHeight() - 1) {
            Block up = b.getRelative(0, 1, 0);
            if (!up.isPassable()) break;
            b = up;
            h++;
        }
        return h;
    }

    /** How much a falling block of this kind hurts per block fallen and at most (hearts are 2). */
    static float[] damage(Material m, int maxHearts) {
        String n = m.name();
        float cap = Math.max(1, maxHearts) * 2f;
        if (n.contains("ANVIL")) return new float[] { 2f, Math.min(cap, 40f) };
        if (n.equals("POINTED_DRIPSTONE")) return new float[] { 2f, Math.min(cap, 40f) };
        return new float[] { 1f, Math.min(cap, 12f) };
    }

    private static ToolResult drop(Args a) throws ToolException {
        Material m = WorldTools.blockMaterial(a.str("block"));
        if (!m.isSolid() && m != Material.COBWEB && m != Material.POINTED_DRIPSTONE && !m.hasGravity()) {
            throw new ToolException(m.getKey().getKey() + " cannot fall as a block. Choose a solid block.");
        }
        Location target = Targets.location(a.str("target"));
        int wanted = a.integer("height", 12, 3, 60);
        int count = Math.min(a.integer("count", 1, 1, 16), GodSettings.maxEntitiesPerSpawn());
        String name = m.name();
        boolean hurts = a.bool("hurts", name.contains("ANVIL") || name.equals("POINTED_DRIPSTONE"));
        boolean stays = a.bool("stays", false);
        BlockData data = m.createBlockData();
        if (data instanceof org.bukkit.block.data.type.PointedDripstone d) {
            d.setVerticalDirection(org.bukkit.block.BlockFace.DOWN);
            data = d;
        }
        float[] dmg = damage(m, GodSettings.dropMaxHearts());
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int dropped = 0, lowest = Integer.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            Location at = target.clone();
            if (count > 1) at.add(r.nextDouble(-2.5, 2.5), 0, r.nextDouble(-2.5, 2.5));
            Block feet = at.getBlock();
            int h = clearHeight(feet.getRelative(0, 1, 0), wanted);
            if (h < 2) continue; // a roof right overhead
            lowest = Math.min(lowest, h);
            Location from = new Location(at.getWorld(), at.getBlockX() + 0.5, feet.getY() + 1 + h, at.getBlockZ() + 0.5);
            BlockData use = data;
            at.getWorld().spawn(from, FallingBlock.class, fb -> {
                fb.setBlockData(use);
                fb.setDropItem(false);
                fb.setCancelDrop(!stays);
                fb.setHurtEntities(hurts);
                if (hurts) {
                    fb.setDamagePerBlock(dmg[0]);
                    fb.setMaxDamage((int) dmg[1]);
                }
                Dropped.mark(fb, stays);
            });
            dropped++;
        }
        if (dropped == 0) {
            throw new ToolException("There is a roof right over " + Targets.describe(target)
                    + "; nothing can fall there. Choose a target under open sky or a higher ceiling.");
        }
        return ToolResult.ok("%d %s %s from %d blocks above %s%s%s. It takes a moment to land, and a mortal who moves "
                + "may dodge it.", dropped, m.getKey().getKey(), dropped == 1 ? "falls" : "fall", lowest,
                Targets.describe(target), hurts ? " (it hurts whoever it hits)" : " (harmless)",
                stays ? "; it stays where it lands" : "; it shatters on landing");
    }
}
