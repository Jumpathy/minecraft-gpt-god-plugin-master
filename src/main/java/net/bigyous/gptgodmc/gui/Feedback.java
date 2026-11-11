package net.bigyous.gptgodmc.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Where the God Manager's and /god's replies to an operator go. Normally chat. While the client mod's
 * God Manager screen carries out an action for a player, the replies are collected instead and returned
 * in that action's result; while the screen is merely open they are shown in the screen. Players without
 * the mod are never affected: {@link #take} returns false and the caller sends to chat as before.
 */
public final class Feedback {
    private static final Map<UUID, List<String>> CAPTURING = new ConcurrentHashMap<>();
    /** Set by the client mod link: offers a line to an open screen; true when the screen took it. */
    private static volatile BiPredicate<Player, String> screen;

    private Feedback() {
    }

    public static void screen(BiPredicate<Player, String> sink) {
        screen = sink;
    }

    /** Starts collecting this player's replies (until {@link #end}). */
    public static void begin(Player p) {
        CAPTURING.put(p.getUniqueId(), new ArrayList<>());
    }

    /** Stops collecting and returns what was said, in order. */
    public static List<String> end(Player p) {
        List<String> said = CAPTURING.remove(p.getUniqueId());
        return said == null ? List.of() : said;
    }

    /** True when the line was taken (collected or shown in the screen) and must not go to chat. */
    public static boolean take(CommandSender to, String text) {
        if (!(to instanceof Player p)) return false;
        List<String> said = CAPTURING.get(p.getUniqueId());
        if (said != null) {
            synchronized (said) {
                said.add(text);
            }
            return true;
        }
        BiPredicate<Player, String> s = screen;
        return s != null && s.test(p, text);
    }

    public static void forget(UUID player) {
        CAPTURING.remove(player);
    }
}
