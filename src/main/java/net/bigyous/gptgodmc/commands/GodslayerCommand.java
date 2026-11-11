package net.bigyous.gptgodmc.commands;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.bigyous.gptgodmc.god.Godslayer;
import net.bigyous.gptgodmc.gui.GodManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** /godslayer [player|@a] | preview [variant]: hands out the blade that wounds gods (/give cannot make it). */
public final class GodslayerCommand implements TabExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            String[] args) {
        if (sender instanceof Player p && !p.hasPermission(GodManager.PERMISSION)) {
            sender.sendMessage(Component.text("Only operators may forge the Godslayer.", NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text(Godslayer.command(sender, args), NamedTextColor.GOLD));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            String[] args) {
        return Godslayer.complete(args);
    }
}
