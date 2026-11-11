package net.bigyous.gptgodmc.god.vessel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Offerings;
import net.bigyous.gptgodmc.god.Speech;
import net.bigyous.gptgodmc.god.Journal;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.loggables.OfferingLoggable;
import net.bigyous.gptgodmc.loggables.PrayerLoggable;
import net.kyori.adventure.text.Component;

/** Mortals touching or striking the vessel, or (sneaking, item in hand) laying an offering at its feet. */
public final class VesselListener implements Listener {
    /** Touches and strikes are throttled separately, so a blow right after a touch still counts. */
    private final Map<UUID, Long> lastTouch = new HashMap<>();
    private final Map<UUID, Long> lastStrike = new HashMap<>();

    private static boolean debounce(Map<UUID, Long> seen, Player p) {
        long now = System.currentTimeMillis();
        Long last = seen.get(p.getUniqueId());
        if (last != null && now - last < 3000) return true;
        seen.put(p.getUniqueId(), now);
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTouch(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !Vessel.isVesselEntity(event.getRightClicked())) return;
        event.setCancelled(true);
        Player p = event.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();
        boolean holding = held != null && !held.getType().isAir();
        if (holding && p.isSneaking() && GodSettings.offeringsEnabled()) {
            // Sneak + right-click while holding something: lay it at the vessel's feet.
            Location feet = event.getRightClicked().getLocation().add(0, 0.3, 0);
            Offerings.offer(p, 0, OfferingLoggable.AT_VESSEL, feet, Vessel.ownerOf(event.getRightClicked()));
            return;
        }
        if (debounce(lastTouch, p)) return;
        if (holding && GodSettings.offeringsEnabled()) {
            p.sendActionBar(Component.text("Sneak and right-click the vessel to offer what you hold", Speech.VIOLET));
        }
        p.getWorld().spawnParticle(Particle.END_ROD, event.getRightClicked().getLocation().add(0, 1.5, 0), 25, 0.3,
                0.8, 0.3, 0.03);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
        tell(p, Vessel.ownerOf(event.getRightClicked()), p.getName() + " touched the vessel",
                PrayerLoggable.touchedVessel(p.getName()));
    }

    /** Reports the gesture to the god whose body this is, and lets it react at once. */
    private static void tell(Player p, Realm realm, String journal, PrayerLoggable prayer) {
        if (realm == null) return;
        realm.run(() -> {
            Journal.add(Journal.Kind.INFO, journal);
            EventLogger.addLoggable(prayer);
        });
        // The other gods of the world see what was done to its body (it does not wake them).
        String god = realm.displayName();
        String seen = journal.replace("the vessel", god + "'s vessel");
        for (Realm peer : realm.peers()) {
            peer.run(() -> EventLogger.addLoggable(new net.bigyous.gptgodmc.loggables.DivineActLoggable(seen, false,
                    null)));
        }
        God.acknowledge(realm, p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onStrike(PrePlayerAttackEntityEvent event) {
        if (!Vessel.isVesselEntity(event.getAttacked())) return;
        event.setCancelled(true);
        Player p = event.getPlayer();
        // Only the Godslayer wounds a god (see god/Godslayer).
        if (net.bigyous.gptgodmc.god.Godslayer.isBlade(p.getInventory().getItemInMainHand())
                && net.bigyous.gptgodmc.god.Godslayer.strike(p, event.getAttacked())) return;
        if (debounce(lastStrike, p)) return;
        ItemStack held = p.getInventory().getItemInMainHand();
        p.getWorld().spawnParticle(Particle.SCULK_SOUL, event.getAttacked().getLocation().add(0, 1.5, 0), 20, 0.3,
                0.8, 0.3, 0.03);
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_ANGRY, 0.6f, 1.4f);
        tell(p, Vessel.ownerOf(event.getAttacked()), p.getName() + " struck the vessel",
                PrayerLoggable.struckVessel(p.getName(),
                held.getType().isAir() ? null : held.getType().getKey().getKey()));
    }
}
