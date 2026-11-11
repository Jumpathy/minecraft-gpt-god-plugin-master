package net.bigyous.gptgodmc.god;

import java.time.Duration;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.bigyous.gptgodmc.GPTGOD;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

/**
 * Worship as mortals see it: the god's commandments, the titles it bestowed and its marks follow a mortal
 * into (and out of) each god's realm. Server thread only.
 */
public final class Devotion implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Titles.apply(p);
        Marks.ensureRunning();
        // A moment later, so the laws are not lost among the join messages.
        Bukkit.getScheduler().runTaskLater(GPTGOD.get(), () -> {
            if (p.isOnline()) showCommandments(p, false);
        }, 60L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        Realm from = Realm.of(event.getFrom());
        Realm to = Realm.of(p.getWorld());
        if (from == to) return;
        Titles.apply(p);
        Marks.ensureRunning();
        if (to != null) showCommandments(p, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Offerings.forget(event.getPlayer().getUniqueId());
    }

    /** After a reload with mortals online: their titles and marks come back. */
    public static void refreshAll() {
        Bukkit.getOnlinePlayers().forEach(Titles::apply);
        Marks.ensureRunning();
    }

    static String godName() {
        Persona p = Persona.current();
        return p == null ? "the god" : p.shortName();
    }

    /**
     * Shows a mortal the laws of the god whose world they stand in.
     *
     * @param always true to answer even when there are none (/commandments)
     */
    public static void showCommandments(Player player, boolean always) {
        List<Realm> gods = Realm.godsOf(player.getWorld());
        if (gods.isEmpty()) {
            if (always) {
                player.sendMessage(Component.text("No god watches over this world, and no law is written here.",
                        Speech.DEEP_VIOLET));
            }
            return;
        }
        for (Realm realm : gods) realm.run(() -> {
            if (Godslayer.isSlain(realm)) {
                // A dead god's laws died with it (they return if it does).
                if (always) {
                    player.sendMessage(Component.text(capital(realm.displayName()) + " is dead; its laws died with "
                            + "it.", Speech.DEEP_VIOLET));
                }
                return;
            }
            List<String> laws = Memory.get().commandments();
            if (laws.isEmpty()) {
                if (always) {
                    player.sendMessage(Component.text(capital(godName()) + " has decreed no commandments. Yet.",
                            Speech.DEEP_VIOLET));
                }
                return;
            }
            sendLaws(player, laws);
        });
    }

    private static void sendLaws(Player player, List<String> laws) {
        player.sendMessage(Component.text("✦ The Commandments of " + godName() + " ✦", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));
        for (int i = 0; i < laws.size(); i++) {
            player.sendMessage(Component.text("  " + roman(i + 1) + ". ", NamedTextColor.GOLD)
                    .append(Component.text(laws.get(i), Speech.VIOLET)));
        }
    }

    /**
     * Tells every mortal in the current realm that the laws changed, with the new list. `headline` is the
     * line shown as the title subtitle ("A new law is written").
     */
    public static void announceCommandments(String headline) {
        List<String> laws = Memory.get().commandments();
        String name = godName();
        for (Player p : Realm.current().players()) {
            p.showTitle(Title.title(Component.text("The Law of " + capital(name), NamedTextColor.GOLD),
                    Component.text(headline, Speech.VIOLET),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofSeconds(1))));
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.9f, 0.5f);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 0.6f);
            if (laws.isEmpty()) {
                p.sendMessage(Component.text("✦ " + capital(name) + " has abolished every commandment. ✦",
                        NamedTextColor.GOLD));
            } else {
                sendLaws(p, laws);
            }
        }
    }

    /** Tells the realm about a title, and refreshes the name everywhere it shows. */
    public static void announceTitle(Player player, String title, String previous, String color) {
        Titles.apply(player);
        Component line;
        if (title == null) {
            line = Component.text("✦ " + player.getName() + " is stripped of the title \"" + previous + "\". ✦",
                    Speech.DEEP_VIOLET);
        } else {
            line = Component.text("✦ By the will of " + godName() + ", ", NamedTextColor.GOLD)
                    .append(Component.text(player.getName() + " shall be known as ", Speech.VIOLET))
                    .append(Titles.styled(player.getName(), NamedTextColor.WHITE, title, color))
                    .append(Component.text(" ✦", NamedTextColor.GOLD));
        }
        for (Player p : Realm.current().players()) {
            p.sendMessage(line);
        }
        player.playSound(player.getLocation(), title == null ? Sound.BLOCK_BEACON_DEACTIVATE
                : Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, title == null ? 0.6f : 1.2f);
    }

    static String capital(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String roman(int n) {
        String[] r = { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }
}
