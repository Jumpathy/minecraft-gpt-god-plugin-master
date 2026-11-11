package net.bigyous.gptgodmc.god;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.loggables.GenericEventLoggable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;

/**
 * The Godslayer unmakes demigods: one blow of the blade tears the divinity out of a mortal the gods
 * raised, wings, halo and strength with it. The gods notice. Server thread only.
 */
public final class DemigodBane implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlow(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !(e.getDamager() instanceof Player slayer)) return;
        if (!Godslayer.isBlade(slayer.getInventory().getItemInMainHand())) return;
        long now = System.currentTimeMillis();
        Demigods state = DemigodPowers.state();
        Demigods.Entry d = state.find(victim.getName(), now);
        if (d == null) return;
        state.unmake(victim.getName());
        DemigodPowers.save();
        DemigodPowers.unbless(victim);

        Location at = victim.getLocation();
        at.getWorld().strikeLightningEffect(at);
        at.getWorld().playSound(at, Sound.BLOCK_GLASS_BREAK, 2f, 0.5f);
        at.getWorld().playSound(at, Sound.ENTITY_WITHER_HURT, 1.5f, 0.6f);
        at.getWorld().playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, 2f, 0.7f);
        at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 1.4, 0), 120, 1.2, 0.8, 0.6, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(0xFFD86B), 1.2f)); // the wings fall apart
        at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.05);

        Title t = Title.title(Component.text(victim.getName() + " is unmade", TextColor.color(0xFF5555)),
                Component.text("the Godslayer tore away their divinity", TextColor.color(0xAAAAAA)));
        for (Player p : at.getWorld().getPlayers()) p.showTitle(t);
        victim.sendMessage(Component.text("The Godslayer cuts through your divinity. You are mortal again.",
                TextColor.color(0xAAAAAA)));
        String raiser = d.by == null ? "a god" : d.by;
        EventLogger.log(at.getWorld(), () -> new GenericEventLoggable(slayer.getName() + " struck " + victim.getName()
                + " with the Godslayer and tore away the divinity " + raiser + " had given them: "
                + victim.getName() + " is a demigod no more.", "a demigod was unmade by the Godslayer"));
    }
}
