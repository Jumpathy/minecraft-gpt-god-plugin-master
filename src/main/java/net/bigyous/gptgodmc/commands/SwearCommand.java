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
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Godslayer;
import net.bigyous.gptgodmc.god.Guard;
import net.bigyous.gptgodmc.god.Oaths;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Speech;
import net.bigyous.gptgodmc.loggables.OathLoggable;
import net.kyori.adventure.text.Component;

/** /swear [god,] <words> — swear an oath before a god. With no words, the oaths that bind you. */
public final class SwearCommand implements CommandExecutor {
    private final Map<UUID, Long> last = new HashMap<>();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only mortals swear oaths.");
            return true;
        }
        if (!GodSettings.oathsEnabled()) {
            player.sendMessage(Component.text("No god takes oaths here.", Speech.DEEP_VIOLET));
            return true;
        }
        String said = String.join(" ", args).trim();
        if (said.isEmpty()) {
            list(player);
            return true;
        }
        Addressing.Target target = Addressing.prayerTarget(player, said);
        if (target == null) {
            player.sendMessage(Component.text("No god watches over this world to hear your oath.", Speech.DEEP_VIOLET));
            return true;
        }
        Realm realm = target.god();
        String words = target.words().trim();
        realm.run(() -> swear(player, realm, words));
        return true;
    }

    private void swear(Player player, Realm realm, String words) {
        if (Godslayer.isSlain(realm)) {
            player.sendMessage(Component.text(realm.displayName() + " is dead. No one hears your oath.",
                    Speech.DEEP_VIOLET));
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = last.get(player.getUniqueId());
        if (prev != null && now - prev < 10_000) {
            player.sendMessage(Component.text("Your last oath still echoes. Wait a moment.", Speech.DEEP_VIOLET));
            return;
        }
        Oaths.Oath o;
        try {
            o = Oaths.swear(realm.memory(), player.getName(), Guard.clean(words), GodSettings.oathsMaxOpen(), now);
        } catch (Oaths.Refusal r) {
            player.sendMessage(Component.text(r.getMessage(), Speech.DEEP_VIOLET));
            return;
        }
        last.put(player.getUniqueId(), now);
        realm.memory().save();
        EventLogger.addLoggable(new OathLoggable(player.getName(), o.id, words));
        player.sendMessage(Component.text("You swear before " + realm.displayName() + ": \"" + o.words + "\" ("
                + o.id + "). It awaits the god's answer.", Speech.VIOLET));
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.8f, 0.7f);
        if (God.isAwake()) God.acknowledge(realm, player);
    }

    private static void list(Player player) {
        long now = System.currentTimeMillis();
        boolean any = false;
        for (Realm realm : Realm.godsOf(player.getWorld())) {
            String mine = Oaths.listFor(realm.memory(), player.getName(), now);
            if (mine.isEmpty()) continue;
            any = true;
            player.sendMessage(Component.text("Oaths you swore before " + realm.displayName() + ":", Speech.VIOLET));
            for (String line : mine.split("\n")) player.sendMessage(Component.text("  " + line, Speech.DEEP_VIOLET));
        }
        if (!any) {
            player.sendMessage(Component.text("No oath binds you. Swear one with /swear <what you swear>.",
                    Speech.DEEP_VIOLET));
        }
    }
}
