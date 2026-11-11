package net.bigyous.gptgodmc.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.god.Addressing;
import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Speech;
import net.bigyous.gptgodmc.loggables.PrayerLoggable;
import net.kyori.adventure.text.Component;

/** /pray <words> — speak directly to the god. With no words, sense how the god regards you. */
public final class PrayCommand implements CommandExecutor {
    private final Map<UUID, Long> lastPrayer = new HashMap<>();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only mortals pray.");
            return true;
        }
        // Prayers go to a god of the world you stand in (the one you name, when several share it).
        String said = String.join(" ", args).trim();
        Addressing.Target target = Addressing.prayerTarget(player, said);
        if (target == null) {
            player.sendMessage(Component.text("Your words fall into silence. No god watches over this world.",
                    Speech.DEEP_VIOLET));
            return true;
        }
        Realm realm = target.god();
        String words = target.named() && target.words().equals(said) && args.length == 1 ? "" : target.words();
        realm.run(() -> pray(player, words.isEmpty() ? new String[0] : words.split(" ")));
        // The other gods may overhear it, as far as their power reaches; it was not made to them.
        if (!words.isEmpty()) Addressing.overhear(player, realm, words);
        return true;
    }

    private void pray(Player player, String[] args) {
        if (net.bigyous.gptgodmc.god.Godslayer.isSlain(Realm.current())) {
            player.sendMessage(Component.text(Realm.current().displayName() + " is dead. Your words fall into "
                    + "silence; no one answers.", Speech.DEEP_VIOLET));
            return;
        }
        if (args.length == 0) {
            int favor = Memory.get().favorOf(player.getName());
            String feeling = favor <= -6 ? "You feel a cold, crushing hatred watching you."
                    : favor <= -3 ? "You feel watched, and judged harshly."
                    : favor < 3 ? "You feel the god's gaze pass over you, undecided."
                    : favor < 6 ? "A faint warmth settles on you. The god is pleased."
                    : "You feel radiant. You are among the god's chosen.";
            player.sendMessage(Component.text(feeling, Speech.VIOLET));
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastPrayer.get(player.getUniqueId());
        if (last != null && now - last < 5000) {
            player.sendMessage(Component.text("Patience. Your last prayer still echoes.", Speech.DEEP_VIOLET));
            return;
        }
        lastPrayer.put(player.getUniqueId(), now);
        String message = String.join(" ", args);
        EventLogger.addLoggable(PrayerLoggable.prayer(player.getName(), message));
        player.sendMessage(Component.text("You pray: " + message, Speech.DEEP_VIOLET));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        if (!God.isAwake()) {
            player.sendMessage(Component.text("…but the god slumbers.", Speech.DEEP_VIOLET));
            return;
        }
        // The real answer takes a second or two to think; this shows at once that the prayer landed.
        God.acknowledge(Realm.current(), player);
    }
}
