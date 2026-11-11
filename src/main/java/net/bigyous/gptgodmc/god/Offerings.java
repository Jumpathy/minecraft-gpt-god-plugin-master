package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.loggables.OfferingLoggable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Mortals giving the god items: sneak + right-click on the vessel, or /offer to the sky. The items are
 * consumed, the god is told who gave what, and its memory keeps a short record. No automatic reward:
 * the god decides what generosity earns. Server thread only.
 */
public final class Offerings {
    /** Shortest time between two offerings by one mortal. */
    private static final long COOLDOWN_MS = 2_500;
    /** Follow-up offerings within this time do not wake the god again; they wait for its next look. */
    private static final long WAKE_WINDOW_MS = 60_000;

    private static final Map<UUID, Long> lastOffer = new HashMap<>();
    private static final Map<UUID, Long> lastWake = new HashMap<>();
    /** The last offering event of each mortal, so a quick follow-up joins it while the god has not read it. */
    private static final Map<UUID, OfferingLoggable> pending = new HashMap<>();

    private Offerings() {
    }

    /**
     * Offers `amount` of the item in the player's main hand (all of it when amount <= 0).
     *
     * @param where {@link OfferingLoggable#AT_VESSEL} or {@link OfferingLoggable#TO_SKY}
     * @param at    where the gift is received (the vessel's feet, or above the player)
     * @return true when the offering was taken
     */
    public static boolean offer(Player player, int amount, String where, Location at) {
        return offer(player, amount, where, at, null);
    }

    /**
     * @param to the god it is offered to (the owner of the vessel it was laid at), or null for the god of
     *           this world that favors the giver most
     */
    public static boolean offer(Player player, int amount, String where, Location at, Realm to) {
        if (!GodSettings.offeringsEnabled()) {
            player.sendMessage(Component.text("The god accepts no offerings here.", Speech.DEEP_VIOLET));
            return false;
        }
        Realm realm = to != null ? to : Addressing.responder(Realm.godsOf(player.getWorld()), player.getName());
        if (realm == null) {
            player.sendMessage(Component.text("You lift your gift, but no god watches over this world.",
                    Speech.DEEP_VIOLET));
            return false;
        }
        if (Godslayer.isSlain(realm)) {
            player.sendMessage(Component.text(realm.displayName() + " is dead. No one takes your gift.",
                    Speech.DEEP_VIOLET));
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
            player.sendMessage(Component.text("The dead have nothing to give.", Speech.DEEP_VIOLET));
            return false;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir() || hand.getAmount() <= 0) {
            player.sendMessage(Component.text("Hold in your hand what you would offer.", Speech.DEEP_VIOLET));
            return false;
        }
        boolean awake = realm.get(God::isAwake);
        if (!awake) {
            player.sendMessage(Component.text("The god slumbers; your gift would go unseen. You keep it.",
                    Speech.DEEP_VIOLET));
            return false;
        }
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        Long last = lastOffer.get(id);
        if (last != null && now - last < COOLDOWN_MS) {
            player.sendMessage(Component.text("Patience. The smoke of your last offering still rises.",
                    Speech.DEEP_VIOLET));
            return false;
        }
        lastOffer.put(id, now);

        int take = amount <= 0 ? hand.getAmount() : Math.min(amount, hand.getAmount());
        ItemStack gift = hand.clone();
        gift.setAmount(take);
        String what = describe(gift);
        boolean free = player.getGameMode() == GameMode.CREATIVE;
        // Take the items before anything else can fail: nothing after this point throws on purpose.
        if (take >= hand.getAmount()) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(hand.getAmount() - take);
            player.getInventory().setItemInMainHand(hand);
        }

        try {
            flourish(player, gift, at);
        } catch (RuntimeException ignored) {
            // Only the particles failed; the offering itself stands.
        }
        String name = player.getName();
        realm.run(() -> {
            int total = Memory.get().recordOffering(name, what, where);
            Journal.add(Journal.Kind.INFO, name + " offered " + what + (OfferingLoggable.AT_VESSEL.equals(where)
                    ? " at the vessel" : " to the sky"));
            OfferingLoggable fresh = new OfferingLoggable(name, what, where, free, total, false);
            OfferingLoggable before = pending.get(id);
            if (before != null && !before.seen() && before.combine(fresh)) {
                return;
            }
            Long woke = lastWake.get(id);
            boolean wake = woke == null || now - woke > WAKE_WINDOW_MS;
            if (wake) lastWake.put(id, now);
            OfferingLoggable event = new OfferingLoggable(name, what, where, free, total, wake);
            EventLogger.addLoggable(event);
            event.arm();
            pending.put(id, event);
        });
        String verb = OfferingLoggable.AT_VESSEL.equals(where) ? "You lay " + what + " at the vessel's feet."
                : "You lift " + what + " up to the sky.";
        player.sendMessage(Component.text(verb + " It is taken.", Speech.DEEP_VIOLET));
        God.acknowledge(player);
        return true;
    }

    /** Forgets a mortal who left (keeps the maps small). */
    public static void forget(UUID id) {
        lastOffer.remove(id);
        pending.remove(id);
    }

    /** "12 diamond", or "diamond_sword named "Oathkeeper" (sharpness 3, unbreaking 2)". */
    public static String describe(ItemStack stack) {
        StringBuilder sb = new StringBuilder();
        sb.append(stack.getAmount()).append(' ').append(stack.getType().getKey().getKey());
        if (stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta.hasDisplayName() && meta.displayName() != null) {
                String n = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
                if (n.length() > 40) n = n.substring(0, 40) + "…";
                sb.append(" named \"").append(n).append('"');
            }
            List<String> ench = new ArrayList<>();
            meta.getEnchants().forEach((e, lvl) -> ench.add(enchant(e, lvl)));
            if (meta instanceof EnchantmentStorageMeta book) {
                book.getStoredEnchants().forEach((e, lvl) -> ench.add(enchant(e, lvl)));
            }
            if (!ench.isEmpty()) {
                sb.append(" (enchanted: ").append(String.join(", ", ench)).append(')');
            }
        }
        return sb.toString();
    }

    private static String enchant(Enchantment e, int level) {
        return e.getKey().getKey() + " " + level;
    }

    /** A small sign the gift was received: crumbs of the item, a column of light rising, a soft chime. */
    private static void flourish(Player player, ItemStack gift, Location at) {
        World w = at.getWorld();
        ItemStack shown = gift.clone();
        shown.setAmount(1);
        w.spawnParticle(Particle.ITEM, at, 14, 0.2, 0.15, 0.2, 0.06, shown);
        for (int i = 0; i < 6; i++) {
            Location p = at.clone().add((Math.random() - 0.5) * 0.6, i * 0.25, (Math.random() - 0.5) * 0.6);
            // Count 0: the offsets are a direction, so each mote drifts straight up.
            w.spawnParticle(Particle.END_ROD, p, 0, 0, 1, 0, 0.12);
        }
        w.spawnParticle(Particle.WHITE_SMOKE, at, 6, 0.15, 0.1, 0.15, 0.02);
        w.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.7f);
        w.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.35f, 1.6f);
    }
}
