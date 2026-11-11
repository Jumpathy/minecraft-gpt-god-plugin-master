package net.bigyous.gptgodmc.commands;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.bigyous.gptgodmc.god.Offerings;
import net.bigyous.gptgodmc.god.Speech;
import net.bigyous.gptgodmc.loggables.OfferingLoggable;
import net.kyori.adventure.text.Component;

/** /offer [amount] — lift what you hold up to the god (all of the stack, or just some of it). */
public final class OfferCommand implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only mortals make offerings.");
            return true;
        }
        // "/offer @vorthak 3": offered to that god, when several share this world.
        net.bigyous.gptgodmc.god.Realm to = null;
        if (args.length > 0 && args[0].startsWith("@")) {
            to = net.bigyous.gptgodmc.god.Realm.byName(args[0]);
            if (to == null || !to.governs(player.getWorld())) {
                player.sendMessage(Component.text("No god by that name watches over this world.", Speech.DEEP_VIOLET));
                return true;
            }
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        int amount = 0;
        if (args.length > 0 && !args[0].equalsIgnoreCase("all")) {
            try {
                amount = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                amount = -1;
            }
            if (amount < 1) {
                player.sendMessage(Component.text("Usage: /offer [amount] — offers what you hold in your hand.",
                        Speech.DEEP_VIOLET));
                return true;
            }
        }
        Offerings.offer(player, amount, OfferingLoggable.TO_SKY, player.getLocation().add(0, 2.2, 0), to);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            String[] args) {
        if (args.length == 1 && sender instanceof Player p) {
            int held = p.getInventory().getItemInMainHand().getAmount();
            return held > 1 ? List.of("1", String.valueOf(held), "all") : List.of();
        }
        return List.of();
    }
}
