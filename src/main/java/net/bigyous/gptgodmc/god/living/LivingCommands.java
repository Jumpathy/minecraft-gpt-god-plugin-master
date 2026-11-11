package net.bigyous.gptgodmc.god.living;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.loggables.GenericEventLoggable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** /side (choose a side in a holy war), /holydays (what is proclaimed), /bonds (operator: how gods regard each other). */
public final class LivingCommands implements TabExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "side" -> side(sender, args);
            case "holydays" -> holydays(sender);
            case "bonds" -> bonds(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void msg(CommandSender s, String text, NamedTextColor c) {
        s.sendMessage(Component.text(text, c));
    }

    // ---- /side ---------------------------------------------------------------------------------------

    private static void side(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg(sender, "Only a mortal in the world can choose a side.", NamedTextColor.RED);
            return;
        }
        List<HolyDays.Day> wars = wars(p);
        if (wars.isEmpty()) {
            msg(p, "No holy war rages in this world.", NamedTextColor.GRAY);
            return;
        }
        if (args.length == 0) {
            for (HolyDays.Day d : wars) {
                String mine = d.sides.get(p.getName());
                msg(p, "⚔ " + d.title + ": " + d.godName + " vs " + d.rivalName + " — you fight for "
                        + (mine == null ? "no one yet" : mine.equalsIgnoreCase(d.god) ? d.godName : d.rivalName)
                        + ". /side <god> to choose.", NamedTextColor.GOLD);
            }
            return;
        }
        Realm god = Realm.byName(String.join(" ", args));
        HolyDays.Day war = null;
        for (HolyDays.Day d : wars) {
            if (god != null && d.involves(god.id)) war = d;
        }
        if (god == null || war == null) {
            msg(p, "That god is not at war here. Wars: " + String.join(", ", wars.stream()
                    .map(d -> d.godName + " vs " + d.rivalName).toList()), NamedTextColor.RED);
            return;
        }
        String no = HolyDayRunner.store().join(war, p.getName(), god.id);
        if (no != null) {
            msg(p, no, NamedTextColor.RED);
            return;
        }
        HolyDayRunner.changed();
        msg(p, "You now fight for " + god.displayName() + " in " + war.title + ".", NamedTextColor.GOLD);
        p.showTitle(net.kyori.adventure.title.Title.title(Component.text("⚔ For " + god.displayName() + "!",
                NamedTextColor.GOLD), Component.text(war.title, NamedTextColor.GRAY)));
        String text = p.getName() + " has joined YOUR side in the holy war \"" + war.title + "\".";
        god.run(() -> EventLogger.addLoggable(new GenericEventLoggable(text)));
        Realm other = Realm.byId(war.god.equalsIgnoreCase(god.id) ? war.rival : war.god);
        if (other != null) {
            String t2 = p.getName() + " has joined " + god.displayName() + "'s side in the holy war \"" + war.title + "\".";
            other.run(() -> EventLogger.addLoggable(new GenericEventLoggable(t2)));
        }
    }

    private static List<HolyDays.Day> wars(Player p) {
        List<HolyDays.Day> out = new ArrayList<>();
        List<Realm> here = Realm.godsOf(p.getWorld());
        for (HolyDays.Day d : HolyDayRunner.store().live()) {
            if (d.kind() != HolyDays.Kind.WAR) continue;
            if (here.stream().anyMatch(r -> d.involves(r.id))) out.add(d);
        }
        return out;
    }

    // ---- /holydays -----------------------------------------------------------------------------------

    private static void holydays(CommandSender sender) {
        long now = System.currentTimeMillis();
        List<Realm> here = sender instanceof Player p ? Realm.godsOf(p.getWorld()) : Realm.all();
        int n = 0;
        for (HolyDays.Day d : HolyDayRunner.store().live()) {
            if (here.stream().noneMatch(r -> d.involves(r.id))) continue;
            n++;
            msg(sender, d.kind().icon + " #" + d.id + " " + d.title + " (" + d.kind().id + " of " + d.godName + ") — "
                    + HolyDays.countdown(d, now), NamedTextColor.GOLD);
            if (!d.rules.isBlank()) msg(sender, "  " + d.rules, NamedTextColor.WHITE);
            if (!d.counts.isEmpty()) msg(sender, "  Points for: " + String.join(", ", d.counts), NamedTextColor.YELLOW);
            if (d.reward != null && !d.reward.isBlank()) msg(sender, "  Reward: " + d.reward, NamedTextColor.GOLD);
        }
        if (n == 0) msg(sender, "No holy day is proclaimed here.", NamedTextColor.GRAY);
    }

    // ---- /bonds (operator) ---------------------------------------------------------------------------

    private static void bonds(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gptgodmc.admin")) {
            msg(sender, "Only the keeper of the world may shape the bonds of gods.", NamedTextColor.RED);
            return;
        }
        if (args.length == 0) {
            int shown = 0;
            for (Realm r : Realm.all()) {
                for (Realm peer : r.peers()) {
                    msg(sender, Rivalry.bonds(r).line(peer.id, peer.displayName(), System.currentTimeMillis())
                            .replaceFirst("^- ", r.displayName() + " → "), NamedTextColor.AQUA);
                    shown++;
                }
            }
            if (shown == 0) msg(sender, "No two gods share a world.", NamedTextColor.GRAY);
            msg(sender, "/bonds <god> <god> <allies|friends|neutral|rivals|enemies|+N|-N>", NamedTextColor.GRAY);
            return;
        }
        if (args.length < 3) {
            msg(sender, "/bonds <god> <god> <allies|friends|neutral|rivals|enemies|+N|-N>", NamedTextColor.RED);
            return;
        }
        Realm a = Realm.byName(args[0]), b = Realm.byName(args[1]);
        if (a == null || b == null) {
            msg(sender, "No god named " + (a == null ? args[0] : args[1]) + ".", NamedTextColor.RED);
            return;
        }
        try {
            msg(sender, Rivalry.operatorSet(a, b, args[2], sender.getName()), NamedTextColor.GREEN);
        } catch (IllegalArgumentException e) {
            msg(sender, e.getMessage(), NamedTextColor.RED);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        List<String> gods = Realm.all().stream().map(Realm::displayName).toList();
        List<String> out = new ArrayList<>();
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (name.equals("side") && args.length == 1) out.addAll(gods);
        if (name.equals("bonds") && (args.length == 1 || args.length == 2)) out.addAll(gods);
        if (name.equals("bonds") && args.length == 3) out.addAll(List.of("allies", "friends", "neutral", "rivals",
                "enemies", "+20", "-20"));
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
