package net.bigyous.gptgodmc.utils;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class TokensCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (sender instanceof Player player && !player.isOp()) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        long input = TokenTracker.getTotalInputTokens();
        long output = TokenTracker.getTotalOutputTokens();
        long total = TokenTracker.getTotalTokens();
        int calls = TokenTracker.getApiCallCount();

        sender.sendMessage(Component.text("--- Token Usage This Round ---", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));
        sender.sendMessage(Component.text("Input tokens:  ", NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text(String.format("%,d", input), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Output tokens: ", NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text(String.format("%,d", output), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Total tokens:  ", NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text(String.format("%,d", total), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("API calls:     ", NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text(String.valueOf(calls), NamedTextColor.WHITE)));

        return true;
    }
}
