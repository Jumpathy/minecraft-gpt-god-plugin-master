package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.god.vessel.Vessel;
import net.bigyous.gptgodmc.loggables.DivineActLoggable;

/**
 * Which of a world's gods a mortal is speaking to. A prayer may name its god ("/pray Vorthak, help me"
 * or "/pray @vorthak help me"); words aimed at no god in particular are answered by the god who favors
 * that mortal most. The other gods may overhear, as far as their {@link Power} lets them.
 */
public final class Addressing {
    private Addressing() {
    }

    /**
     * @param god   the god the words go to
     * @param words what is left once the god's name is taken off the front
     * @param named true when the mortal named the god
     */
    public record Target(Realm god, String words, boolean named) {
    }

    /** The god a prayer from this mortal goes to, or null where no god rules. Server thread. */
    public static Target prayerTarget(Player player, String text) {
        List<Realm> gods = Realm.godsOf(player.getWorld());
        if (gods.isEmpty()) return null;
        if (gods.size() == 1) return new Target(gods.get(0), text, false);
        Pantheon.Address a = Pantheon.addressed(names(gods), text);
        if (a.index() < 0) a = Pantheon.addressed(ids(gods), text);
        if (a.index() >= 0) return new Target(gods.get(a.index()), a.words(), true);
        return new Target(responder(gods, player.getName()), text, false);
    }

    /** The god who answers words aimed at none of them: the one that favors the mortal most. */
    public static Realm responder(List<Realm> all, String player) {
        List<Realm> gods = new ArrayList<>();
        for (Realm g : all) {
            if (!Godslayer.isSlain(g)) gods.add(g);
        }
        if (gods.isEmpty()) return all.isEmpty() ? null : all.get(0); // every god is dead: the first one says so
        int[] favor = new int[gods.size()];
        for (int i = 0; i < gods.size(); i++) {
            try {
                favor[i] = gods.get(i).memory().favorOf(player);
            } catch (RuntimeException e) {
                favor[i] = 0;
            }
        }
        int i = Pantheon.responder(favor);
        return i < 0 ? null : gods.get(i);
    }

    /**
     * Whether words in chat (or voice) should make the bound god answer: they name it, or they use a
     * general attention word (or wake-on-any-chat is on) and no other god is named and this god is the
     * one that answers this mortal.
     *
     * @param general the words carry a general attention word, or every chat line wakes the god
     */
    public static boolean answers(String player, String message, boolean general) {
        Realm me = Realm.current();
        if (Pantheon.names(message, me.displayName())) return true;
        if (!general) return false;
        if (!me.shared()) return true;
        List<Realm> gods = me.domainGods();
        for (Realm g : gods) {
            if (g != me && !Godslayer.isSlain(g) && Pantheon.names(message, g.displayName())) return false;
        }
        return responder(gods, player) == me;
    }

    /**
     * Lets the other gods of `to`'s worlds overhear a prayer, if their power reaches: a supreme god hears
     * every prayer, a greater one only near its vessel, a lesser one never. They are told it was not made
     * to them, and it does not wake them. Server thread.
     */
    public static List<Realm> overhear(Player player, Realm to, String words) {
        List<Realm> heard = new ArrayList<>();
        if (to == null) return heard;
        String god = to.displayName();
        for (Realm g : to.peers()) {
            if (!g.awake || Godslayer.isSlain(g) || !g.power().overhears(distanceToVessel(g, player))) continue;
            g.run(() -> EventLogger.addLoggable(DivineActLoggable.overheard(player.getName(), god, words)));
            heard.add(g);
        }
        return heard;
    }

    /** How far the mortal is from the god's body, or -1 when it has none in their world. */
    static double distanceToVessel(Realm g, Player player) {
        Vessel v = g.vesselIfCreated();
        if (v == null || !v.isManifested()) return -1;
        Location at = v.location();
        Location p = player.getLocation();
        if (at == null || at.getWorld() == null || !at.getWorld().equals(p.getWorld())) return -1;
        return at.distance(p);
    }

    private static List<String> names(List<Realm> gods) {
        List<String> out = new ArrayList<>();
        for (Realm g : gods) out.add(g.displayName());
        return out;
    }

    private static List<String> ids(List<Realm> gods) {
        List<String> out = new ArrayList<>();
        for (Realm g : gods) out.add(g.id.toLowerCase(Locale.ROOT));
        return out;
    }
}
