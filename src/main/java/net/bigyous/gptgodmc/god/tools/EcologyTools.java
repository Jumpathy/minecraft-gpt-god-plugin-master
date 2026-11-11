package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.LandSense;
import net.bigyous.gptgodmc.god.living.Servants;

/** The god's hand on the living land: tend (flourish or blight). */
final class EcologyTools {
    private static final int MAX_PLANTS = 500;
    private static final int MAX_ANIMALS = 16;

    private EcologyTools() {
    }

    static List<GodTool> all() {
        return List.of(GodTool.sync("tend",
                "Govern the living land around a place (" + Targets.TARGET_HELP + "). flourish: crops ripen, "
                        + "saplings grow into trees, young animals grow up and the grown ones breed. blight: crops "
                        + "wither back to seed, saplings die to dead bushes and the animals sicken for a while (none "
                        + "die). It spreads outward from the center over a few seconds. Bless the faithful's fields, "
                        + "restore a stripped forest, or punish those who abuse the land.",
                Schema.object()
                        .str("target", "Where: " + Targets.TARGET_HELP + ".", true)
                        .choice("mode", "flourish or blight.", true, List.of("flourish", "blight"))
                        .integer("radius", "Radius in blocks. Default 10.", false, 3, 24),
                EcologyTools::tend));
    }

    private record Plant(Block block, double dist) {
    }

    private static ToolResult tend(Args a) throws ToolException {
        if (!GodSettings.ecologyEnabled()) {
            throw new ToolException("The server keeper has turned the living land off (ecology.enabled).");
        }
        Location c = Targets.location(a.str("target"));
        boolean bless = a.str("mode").equalsIgnoreCase("flourish");
        int r = a.integer("radius", 10, 3, 24);
        World w = c.getWorld();
        List<Plant> plants = new ArrayList<>();
        int cx = c.getBlockX(), cy = c.getBlockY(), cz = c.getBlockZ();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r || !w.isChunkLoaded((cx + dx) >> 4, (cz + dz) >> 4)) continue;
                for (int dy = -6; dy <= 6; dy++) {
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    Material m = b.getType();
                    if (LandSense.crop(m) || LandSense.sapling(m)) {
                        plants.add(new Plant(b, Math.sqrt(dx * dx + dy * dy + dz * dz)));
                    }
                }
            }
        }
        plants.sort(Comparator.comparingDouble(Plant::dist));
        if (plants.size() > MAX_PLANTS) plants = new ArrayList<>(plants.subList(0, MAX_PLANTS));
        List<Animals> herd = new ArrayList<>();
        for (Entity e : w.getNearbyEntities(c, r, 6, r)) {
            if (e instanceof Animals an && !Servants.isServantEntity(e) && herd.size() < MAX_ANIMALS) herd.add(an);
        }
        if (plants.isEmpty() && herd.isEmpty()) {
            throw new ToolException("Nothing grows within " + r + " blocks of " + Targets.describe(c)
                    + ": no crops, saplings or animals. Choose fields, a grove or a herd (survey_area shows them).");
        }
        int crops = 0, saplings = 0, animals = herd.size();
        for (Plant p : plants) {
            if (LandSense.sapling(p.block().getType())) saplings++;
            else crops++;
        }
        w.playSound(c, bless ? Sound.BLOCK_AMETHYST_BLOCK_RESONATE : Sound.BLOCK_SCULK_SPREAD, 1.5f,
                bless ? 1.2f : 0.6f);
        List<Plant> wave = plants;
        new BukkitRunnable() {
            double ring = 0;
            int next = 0;

            @Override
            public void run() {
                ring += 1.5;
                while (next < wave.size() && wave.get(next).dist() <= ring) {
                    Block b = wave.get(next++).block();
                    if (bless) flourish(b);
                    else blight(b);
                }
                boolean last = ring >= r + 1.5;
                for (java.util.Iterator<Animals> it = herd.iterator(); it.hasNext();) {
                    Animals an = it.next();
                    if (!an.isValid()) {
                        it.remove();
                    } else if (last || an.getLocation().distance(c) <= ring) {
                        it.remove();
                        if (bless) bless(an);
                        else sicken(an);
                    }
                }
                if (last) cancel();
            }
        }.runTaskTimer(GPTGOD.get(), 1L, 2L);
        return ToolResult.ok("%s spreads from %s over %d blocks: %d crops, %d saplings and %d animals.",
                bless ? "Your blessing" : "Your blight", Targets.describe(c), r, crops, saplings, animals);
    }

    private static void flourish(Block b) {
        Material before = b.getType();
        if (LandSense.sapling(before)) {
            for (int i = 0; i < 8 && b.getType() == before; i++) b.applyBoneMeal(BlockFace.UP);
        } else if (b.getBlockData() instanceof Ageable age && age.getAge() < age.getMaximumAge()) {
            age.setAge(age.getMaximumAge());
            b.setBlockData(age);
        }
        b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.7, 0.5), 5, 0.3, 0.3, 0.3, 0);
    }

    private static void blight(Block b) {
        if (LandSense.sapling(b.getType())) {
            b.setType(Material.DEAD_BUSH, false);
        } else if (b.getBlockData() instanceof Ageable age && age.getAge() > 0) {
            age.setAge(0);
            b.setBlockData(age);
        }
        b.getWorld().spawnParticle(Particle.ASH, b.getLocation().add(0.5, 0.6, 0.5), 12, 0.3, 0.3, 0.3, 0);
        b.getWorld().spawnParticle(Particle.SMOKE, b.getLocation().add(0.5, 0.5, 0.5), 2, 0.2, 0.2, 0.2, 0.01);
    }

    private static void bless(Animals an) {
        if (!an.isAdult()) an.setAdult();
        else if (an.canBreed()) an.setLoveModeTicks(600);
        an.getWorld().spawnParticle(Particle.HEART, an.getLocation().add(0, an.getHeight() + 0.3, 0), 3, 0.3, 0.2,
                0.3, 0);
    }

    private static void sicken(Animals an) {
        an.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 160, 0));
        an.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 600, 1));
        an.getWorld().spawnParticle(Particle.SQUID_INK, an.getLocation().add(0, an.getHeight() / 2, 0), 6, 0.3, 0.3,
                0.3, 0.01);
        an.getWorld().playSound(an.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.4f, 0.5f);
    }
}
