package net.bigyous.gptgodmc.commands;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Speech;
import net.bigyous.gptgodmc.god.TabList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** /gods — which gods watch over the world you stand in, and how to pray to one of them. */
public final class GodsCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Gods: " + String.join(", ", Realm.all().stream()
                    .filter(r -> !net.bigyous.gptgodmc.god.Godslayer.isSlain(r)).map(Realm::displayName).toList()));
            return true;
        }
        List<Realm> gods = Realm.livingGodsOf(player.getWorld());
        if (gods.isEmpty()) {
            player.sendMessage(Component.text("No god watches over this world.", Speech.DEEP_VIOLET));
            return true;
        }
        player.sendMessage(Component.text(gods.size() == 1 ? "✦ The god of this world ✦" : "✦ The gods of this world ✦",
                NamedTextColor.GOLD));
        for (Realm r : gods) {
            Persona p = r.persona();
            String name = p == null ? "A god not yet awoken" : p.title();
            if (net.bigyous.gptgodmc.god.Godslayer.isSlain(r)) {
                var death = r.memory().mortality;
                name = "☠ " + r.displayName() + " — slain by " + death.slainBy + " (dead)";
            }
            player.sendMessage(Component.text("  " + name, TabList.archetypeColor(p == null ? null : p.archetype)));
        }
        if (gods.size() > 1) {
            player.sendMessage(Component.text("Pray to one by name: /pray <name>, <words>. Unnamed prayers go to the "
                    + "god who favors you most; the others may overhear.", Speech.DEEP_VIOLET));
        }
        return true;
    }
}
