package net.bigyous.gptgodmc.god.tools;

import java.util.List;
import java.util.Locale;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.god.Devotion;
import net.bigyous.gptgodmc.god.Marks;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Speech;
import net.bigyous.gptgodmc.god.Titles;
import net.kyori.adventure.text.Component;

/** Powers about worship: commandments, titles and marks. (Offerings arrive as events; answer them freely.) */
final class DevotionTools {
    private DevotionTools() {
    }

    static List<GodTool> all() {
        return List.of(
                GodTool.sync("decree",
                        "Write, revoke or rewrite your commandments: up to " + Memory.MAX_COMMANDMENTS
                                + " standing laws for every mortal in your realm. Each change is proclaimed to "
                                + "them with a title and a bell, they can read the laws any time with "
                                + "/commandments, and newcomers are shown them on arrival. Your laws appear in "
                                + "every perception, so judge mortals by them: reward the obedient, punish "
                                + "transgressors. Keep each law short and concrete (\"Thou shalt not slay the "
                                + "village cows\"); change them rarely so they mean something.",
                        Schema.object()
                                .choice("action", "add = append one law; revoke = strike out law number 'number'; "
                                        + "replace = rewrite the whole list with 'laws' (an empty list abolishes "
                                        + "them all).", true, List.of("add", "revoke", "replace"))
                                .str("text", "For add: the law, one sentence of at most "
                                        + Memory.COMMANDMENT_LENGTH + " characters.", false)
                                .integer("number", "For revoke: the law's number as shown in your perception.",
                                        false, 1, Memory.MAX_COMMANDMENTS)
                                .strings("laws", "For replace: the complete new list, in order.", false,
                                        Memory.MAX_COMMANDMENTS),
                        DevotionTools::decree),
                GodTool.sync("bestow_title",
                        "Bestow a title on a mortal, shown after their name in chat and in the player list for "
                                + "everyone to see (\"Steve the Faithful\", \"Alex Oathbreaker\"); the whole realm "
                                + "hears it proclaimed. It lasts until you change or revoke it, across sessions. "
                                + "A lasting honor or shame: use it for deeds worth remembering. An empty title "
                                + "revokes the current one.",
                        Schema.object()
                                .str("player", "The mortal's name.", true)
                                .str("title", "The title, at most " + Titles.MAX_LENGTH + " characters, written to "
                                        + "follow their name (e.g. \"the Faithful\", \"Oathbreaker\"). Empty to "
                                        + "revoke.", false)
                                .choice("color", "Color of the title. Default gold.", false, Titles.COLORS),
                        DevotionTools::bestowTitle),
                GodTool.sync("mark",
                        "Set a lasting visible mark on a mortal that everyone around can see: 'halo', a small "
                                + "ring of golden light above their head (your favor made visible), or 'curse', "
                                + "a dark smouldering cloud that hangs over them (a sign to shun them). It "
                                + "follows them for the time you choose, even if they leave and return; 'none' "
                                + "removes it. A social signal, not a power: pair it with words.",
                        Schema.object()
                                .str("player", "The mortal's name.", true)
                                .choice("kind", "halo, curse, or none to remove the mark.", true,
                                        List.of("halo", "curse", "none"))
                                .integer("minutes", "How long it lasts. Default 30.", false, 1, 720),
                        DevotionTools::mark));
    }

    private static ToolResult decree(Args a) throws ToolException {
        Memory memory = Memory.get();
        String action = a.str("action").toLowerCase(Locale.ROOT);
        String headline;
        try {
            switch (action) {
                case "add" -> {
                    if (!a.has("text")) throw new ToolException("Give the new law as 'text'.");
                    int n = memory.addCommandment(a.str("text"));
                    headline = "A new law is written: commandment " + n;
                }
                case "revoke" -> {
                    if (!a.has("number")) throw new ToolException("Say which law to strike out with 'number'.");
                    String gone = memory.revokeCommandment(a.integer("number", 1, 1, 99));
                    headline = "A law is struck out: \"" + shorten(gone, 40) + "\"";
                }
                case "replace" -> {
                    List<String> laws = a.strings("laws");
                    memory.replaceCommandments(laws);
                    headline = memory.commandments().isEmpty() ? "Every law is abolished" : "The law is written anew";
                }
                default -> throw new ToolException("Unknown action '" + action + "'; use add, revoke or replace.");
            }
        } catch (IllegalArgumentException e) {
            throw new ToolException(e.getMessage());
        }
        Devotion.announceCommandments(headline);
        String list = memory.renderCommandments();
        return ToolResult.ok("Proclaimed (" + headline + "). Your commandments now:\n"
                + (list.isEmpty() ? "(none)" : list.trim()));
    }

    private static ToolResult bestowTitle(Args a) throws ToolException {
        String title = Titles.sanitize(a.str("title", ""));
        String color = Titles.color(a.str("color", null));
        Memory memory = Memory.get();
        if (title.isEmpty()) {
            Player online = null;
            String name = a.str("player");
            try {
                online = Targets.player(name);
                name = online.getName();
            } catch (ToolException e) {
                // Someone who left can still lose a title.
                if (memory.titleOf(name) == null) throw e;
            }
            String before = memory.setTitle(name, null, null);
            if (before == null) {
                return ToolResult.error(name + " bears no title from you.");
            }
            if (online != null) {
                Devotion.announceTitle(online, null, before, null);
            }
            return ToolResult.ok("%s is stripped of the title \"%s\".", name, before);
        }
        Player p = Targets.player(a.str("player"));
        if (title.toLowerCase(Locale.ROOT).startsWith(p.getName().toLowerCase(Locale.ROOT) + " ")) {
            // The god wrote "Steve the Faithful"; the name is shown anyway.
            title = title.substring(p.getName().length()).trim();
        }
        if (title.length() > Titles.MAX_LENGTH) {
            throw new ToolException("That title is " + title.length() + " characters; keep it to "
                    + Titles.MAX_LENGTH + " (e.g. \"the Faithful\").");
        }
        String before = memory.setTitle(p.getName(), title, color);
        Devotion.announceTitle(p, title, before, color);
        return ToolResult.ok("%s is now known as \"%s %s\" (%s)%s.", p.getName(), p.getName(), title, color,
                before == null ? "" : ", replacing \"" + before + "\"");
    }

    private static ToolResult mark(Args a) throws ToolException {
        Player p = Targets.player(a.str("player"));
        String kind = a.str("kind").toLowerCase(Locale.ROOT);
        Memory memory = Memory.get();
        long now = System.currentTimeMillis();
        if (kind.equals("none")) {
            String had = memory.activeMark(p.getName(), now);
            memory.setMark(p.getName(), null, 0);
            if (had == null) return ToolResult.error(p.getName() + " bears no mark of yours.");
            p.sendActionBar(Component.text(Marks.HALO.equals(had) ? "The light above you fades."
                    : "The cloud over you lifts.", Speech.VIOLET));
            return ToolResult.ok("The %s on %s is gone.", had, p.getName());
        }
        if (!kind.equals(Marks.HALO) && !kind.equals(Marks.CURSE)) {
            throw new ToolException("Unknown mark '" + kind + "'; use halo, curse or none.");
        }
        int minutes = a.integer("minutes", 30, 1, 720);
        memory.setMark(p.getName(), kind, now + minutes * 60_000L);
        Marks.ensureRunning();
        boolean halo = kind.equals(Marks.HALO);
        p.sendActionBar(Component.text(halo ? "✦ A ring of golden light settles above you ✦"
                : "A dark cloud gathers over your head…", halo ? Speech.VIOLET : Speech.DEEP_VIOLET));
        p.getWorld().spawnParticle(halo ? Particle.END_ROD : Particle.LARGE_SMOKE,
                p.getLocation().add(0, p.getHeight() + 0.4, 0), 16, 0.3, 0.1, 0.3, 0.02);
        if (halo) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.6f);
        } else {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.3f, 0.6f);
        }
        return ToolResult.ok("%s now bears your %s for %d minutes; everyone near them can see it.", p.getName(), kind,
                minutes);
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
