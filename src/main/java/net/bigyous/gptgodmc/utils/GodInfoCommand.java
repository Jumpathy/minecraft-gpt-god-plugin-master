package net.bigyous.gptgodmc.utils;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.bigyous.gptgodmc.GPT.Personality;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class GodInfoCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (sender instanceof Player player && !player.isOp()) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        List<String> likes = Personality.getLikes();
        List<String> dislikes = Personality.getDislikes();

        if (likes.isEmpty() && dislikes.isEmpty()) {
            sender.sendMessage(Component.text("The god has not yet awakened. No personality has been generated.", NamedTextColor.GRAY));
            return true;
        }

        sender.sendMessage(Component.text("--- Divine Personality (Eyes Only) ---", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));

        sender.sendMessage(Component.text("Likes:", NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true));
        for (String like : likes) {
            sender.sendMessage(Component.text("  \u2022 " + like, NamedTextColor.GREEN));
        }

        sender.sendMessage(Component.text("Dislikes:", NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true));
        for (String dislike : dislikes) {
            sender.sendMessage(Component.text("  \u2022 " + dislike, NamedTextColor.RED));
        }

        return true;
    }
}
