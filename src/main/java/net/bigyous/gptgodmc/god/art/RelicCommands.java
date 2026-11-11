package net.bigyous.gptgodmc.god.art;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.art.Relics.Relic;
import net.bigyous.gptgodmc.god.tools.RelicTools;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * /relics, for operators (gptgodmc.admin): see what the gods have made, turn the forge on or off (with
 * the warning about what it costs), hand out or show a relic, unmake one, try the built-in examples for
 * free, or commission one directly.
 */
public final class RelicCommands implements TabExecutor {
    private static final TextColor VIOLET = TextColor.color(0xC77DFF), GREY = TextColor.color(0xB8B0C8);
    private static final List<String> SUBS = List.of("list", "on", "off", "give", "show", "dismiss", "delete", "samples",
            "forge", "imbue", "powers");

    private static void say(CommandSender to, String text) {
        to.sendMessage(Component.text(text, GREY));
    }

    private static void warnTo(CommandSender to) {
        to.sendMessage(Component.text("Warning: ", NamedTextColor.RED).append(Component.text(ArtSettings.warning(),
                NamedTextColor.GOLD)));
        String problem = ArtSettings.modelProblem(ArtSettings.model(), false);
        if (problem != null) to.sendMessage(Component.text(problem, NamedTextColor.RED));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gptgodmc.admin")) {
            say(sender, "Only the keepers of this world may tend the gods' forge.");
            return true;
        }
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "on", "off" -> {
                boolean on = sub.equals("on");
                GPTGOD.get().getConfig().set("creations.enabled", on);
                GPTGOD.get().saveConfig();
                if (on) {
                    warnTo(sender);
                    RelicsModule.warn();
                } else {
                    say(sender, "The gods' forge is cold: they make no new relics. The ones that exist remain.");
                }
            }
            case "give" -> {
                Relic r = relic(sender, args);
                if (r == null) return true;
                Player to = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player p ? p : null;
                if (to == null) say(sender, "Give it to whom? /relics give <relic> <player>");
                else say(sender, RelicTools.hand(to, r));
            }
            case "show" -> {
                Relic r = relic(sender, args);
                if (r == null) return true;
                if (!(sender instanceof Player p)) {
                    say(sender, "Stand where it should float and run this in game.");
                    return true;
                }
                try {
                    RelicShrines.enshrine(r, p.getLocation());
                    say(sender, "\"" + r.name() + "\" floats here until /relics dismiss " + r.id() + ".");
                } catch (IllegalStateException e) {
                    say(sender, e.getMessage());
                }
            }
            case "dismiss" -> {
                Relic r = relic(sender, args);
                if (r != null) say(sender, RelicShrines.dismiss(r.id()) + " showing(s) of \"" + r.name() + "\" removed.");
            }
            case "delete" -> {
                Relic r = relic(sender, args);
                if (r == null) return true;
                RelicShrines.dismiss(r.id());
                Relics.remove(r.id());
                RelicsModule.changed();
                say(sender, "\"" + r.name() + "\" is unmade. Copies mortals still hold lose their look.");
            }
            case "samples" -> {
                // The built-in examples, made without asking any model: a free way to see relics in game.
                int made = 0;
                for (Samples.Sample s : Samples.ALL) {
                    if (Relics.find(Relics.slug(s.name())) != null) continue;
                    Relics.add("samples", "the first artist", s.name(), s.kind(), s.note(), s.sprite(),
                            List.of(s.powers()), System.currentTimeMillis());
                    made++;
                }
                RelicsModule.changed();
                say(sender, made + " example relic(s) added (no model was asked, nothing was spent). /relics give "
                        + Relics.slug(Samples.BLADE.name()) + " to hold one; " + Relics.slug(Samples.KINDLED.name())
                        + " is sculpted, wakes on a right-click and is imbued.");
            }
            case "imbue" -> imbue(sender, args);
            case "powers" -> {
                Relic r = relic(sender, args);
                if (r == null) return true;
                if (r.powers().isEmpty()) say(sender, "\"" + r.name() + "\" holds no powers. " + RelicPowers.HELP);
                else {
                    sender.sendMessage(Component.text("\"" + r.name() + "\" is imbued with:", VIOLET));
                    for (String line : r.powers()) say(sender, "  " + line);
                }
            }
            case "forge" -> forge(sender, args);
            default -> list(sender);
        }
        return true;
    }

    private static Relic relic(CommandSender sender, String[] args) {
        Relic r = args.length > 1 ? Relics.find(args[1]) : null;
        if (r == null) say(sender, "Which relic? " + (Relics.all().isEmpty() ? "None exist yet (/relics samples adds "
                + "the examples)." : String.join(", ", Relics.all().stream().map(Relic::id).toList())));
        return r;
    }

    private static void list(CommandSender sender) {
        sender.sendMessage(Component.text("The gods' forge is " + (ArtSettings.enabled() ? "LIT" : "cold") + " ("
                + ArtSettings.model() + ", " + ArtSettings.effort() + " effort; " + Relics.all().size() + " of "
                + ArtSettings.maxTotal() + " relics).", VIOLET));
        for (Relic r : Relics.all()) {
            say(sender, "  " + r.id() + ": \"" + r.name() + "\" (" + r.kind() + (r.twoFormed() ? ", two forms" : "")
                    + (r.powers().isEmpty() ? "" : ", " + r.powers().size() + " powers") + ") by " + r.godName());
        }
        for (String s : RelicShrines.describe()) say(sender, "  in the world: " + s);
        say(sender, "/relics on|off · give <relic> [player] · show <relic> · dismiss <relic> · delete <relic> · "
                + "samples · forge <kind> <name> | <look> · powers <relic> · imbue <relic> <power; power; ...|clear>");
        if (ArtSettings.enabled()) warnTo(sender);
    }

    /** /relics imbue <relic> <power; power; ...> (or "clear"): the keeper sets what a relic does, at no cost. */
    private static void imbue(CommandSender sender, String[] args) {
        Relic r = relic(sender, args);
        if (r == null) return;
        String rest = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
        if (rest.isEmpty()) {
            say(sender, "/relics imbue " + r.id() + " <power; power; ...>   or   /relics imbue " + r.id() + " clear");
            say(sender, RelicPowers.HELP);
            return;
        }
        List<String> lines = new ArrayList<>();
        if (!rest.equalsIgnoreCase("clear")) {
            for (String part : rest.split(";")) {
                if (!part.isBlank()) lines.add(part.trim());
            }
        }
        try {
            RelicPowers.Reading reading = RelicTools.powers(lines, r.twoFormed(), RelicImbue.owner(r));
            Relics.imbue(r.id(), lines);
            say(sender, lines.isEmpty() ? "\"" + r.name() + "\" holds no powers now."
                    : String.format(Locale.ROOT, "\"%s\" is imbued with %d power(s), %.1f of %.0f. Copies already held "
                            + "change when next held.", r.name(), reading.powers().size(), reading.cost(),
                            ArtSettings.powerBudget()));
        } catch (net.bigyous.gptgodmc.god.tools.ToolException e) {
            say(sender, e.getMessage());
        }
    }

    /** /relics forge <kind> <name words> | <look words>: a commission from the operator, in the first god's name. */
    private static void forge(CommandSender sender, String[] args) {
        String rest = String.join(" ", Arrays.copyOfRange(args, Math.min(args.length, 2), args.length));
        int bar = rest.indexOf('|');
        if (args.length < 3 || bar < 1 || !Atelier.KINDS.containsKey(args[1].toLowerCase(Locale.ROOT))) {
            say(sender, "/relics forge <" + String.join("|", Atelier.KINDS.keySet()) + "> <name> | <what it looks like>");
            return;
        }
        if (!ArtSettings.enabled()) {
            say(sender, "The forge is cold. /relics on lights it (read the warning first).");
            return;
        }
        String kind = args[1].toLowerCase(Locale.ROOT), name = rest.substring(0, bar).trim(),
                look = rest.substring(bar + 1).trim();
        Realm realm = Realm.current();
        warnTo(sender);
        say(sender, "Commissioning \"" + name + "\". This takes a minute or more.");
        Thread t = new Thread(Realm.bound(() -> {
            String answer;
            try {
                Atelier.Made made = Atelier.artist.make(new Atelier.Brief(name, kind, look, ""));
                Relic r = Relics.add(realm.id, realm.displayName(), name, kind, look, made.sprite(),
                        System.currentTimeMillis());
                RelicsModule.changed();
                answer = "\"" + r.name() + "\" is made (id " + r.id() + ", " + made.attempts() + " attempts). /relics give "
                        + r.id();
            } catch (Atelier.Failed e) {
                answer = e.getMessage();
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("A commissioned relic failed", e);
                answer = "The commission failed: " + e;
            }
            String told = answer;
            Bukkit.getScheduler().runTask(GPTGOD.get(), () -> say(sender, told));
        }), "god-relic-commission");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("gptgodmc.admin")) return out;
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(last)) out.add(s);
        } else if (args.length >= 3 && args[0].equalsIgnoreCase("imbue")) {
            String typed = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            String word = typed.substring(typed.lastIndexOf(';') + 1).trim();
            if (!word.contains(":")) {
                for (String t : List.of("stat:", "held:", "on_hit:", "on_swing:", "on_use:", "on_kill:", "on_hurt:", "clear")) {
                    if (t.startsWith(last)) out.add(t);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("forge")) {
            for (String k : Atelier.KINDS.keySet()) if (k.startsWith(last)) out.add(k);
        } else if (args.length == 2) {
            for (Relic r : Relics.all()) if (r.id().startsWith(last)) out.add(r.id());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
            }
        }
        return out;
    }
}
