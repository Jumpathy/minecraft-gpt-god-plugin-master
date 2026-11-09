package net.bigyous.gptgodmc.utils;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GameLoop;
import net.bigyous.gptgodmc.loggables.SystemInjectionLoggable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class SudoGodCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (sender instanceof Player player && !player.isOp()) {
            player.sendMessage(Component.text("You do not have permission to use this command.").color(NamedTextColor.RED));
            return false;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /sudogod <message>").color(NamedTextColor.YELLOW));
            return false;
        }

        if (!GameLoop.isRunning) {
            sender.sendMessage(Component.text("The god is not yet awake. Wait for a player to join first.").color(NamedTextColor.RED));
            return false;
        }

        String message = String.join(" ", args);
        EventLogger.addLoggable(new SystemInjectionLoggable(message));
        GameLoop.forceCycle();

        sender.sendMessage(Component.text("Your command has been delivered to the god.").color(NamedTextColor.GOLD));
        return true;
    }
}
