package net.bigyous.gptgodmc.god;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.persistence.PersistentDataType;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * Blocks the god drops from the sky (the drop_block power). A block marked as fleeting shatters
 * where it lands instead of becoming part of the world, so anvils do not litter the land.
 */
public final class Dropped implements Listener {
    public static NamespacedKey key() {
        return new NamespacedKey(GPTGOD.get(), "god_dropped");
    }

    /** Marks a falling block as the god's: stays = true lets it settle as a block where it lands. */
    public static void mark(FallingBlock fb, boolean stays) {
        fb.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte) (stays ? 1 : 0));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLand(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof FallingBlock fb)) return;
        Byte stays = fb.getPersistentDataContainer().get(key(), PersistentDataType.BYTE);
        if (stays == null || stays == 1) return;
        e.setCancelled(true);
        var at = fb.getLocation();
        at.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 0.3, 0), 30, 0.3, 0.2, 0.3, fb.getBlockData());
        at.getWorld().playSound(at, fb.getBlockData().getSoundGroup().getBreakSound(), 1f, 0.8f);
        if (fb.getBlockData().getMaterial().name().contains("ANVIL")) {
            at.getWorld().playSound(at, Sound.BLOCK_ANVIL_LAND, 1f, 0.9f);
        }
        fb.remove();
    }
}
