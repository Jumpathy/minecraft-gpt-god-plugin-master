package net.bigyous.gptgodmc.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.bigyous.gptgodmc.god.Devotion;
import net.bigyous.gptgodmc.god.Realm;

/** /commandments — read the laws of the god whose world you stand in. */
public final class CommandmentsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            String[] args) {
        if (sender instanceof Player player) {
            Devotion.showCommandments(player, true);
            return true;
        }
        // The console reads the main god's laws.
        if (net.bigyous.gptgodmc.god.Godslayer.isSlain(Realm.primary())) {
            sender.sendMessage(Realm.primary().displayName() + " is dead; its laws died with it.");
            return true;
        }
        String laws = Realm.primary().memory().renderCommandments();
        sender.sendMessage(laws.isEmpty() ? "The god has decreed no commandments." : "Commandments:\n" + laws.trim());
        return true;
    }
}
