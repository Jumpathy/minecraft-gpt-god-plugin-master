package net.bigyous.gptgodmc.god.living;

import java.util.List;
import java.util.Locale;

import org.bukkit.entity.Player;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.god.Godslayer;
import net.bigyous.gptgodmc.god.Heart;
import net.bigyous.gptgodmc.god.Journal;
import net.bigyous.gptgodmc.god.Pantheon;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.loggables.DivineActLoggable;

/**
 * Bonds between the gods of one world, in the world: what moves them (each other's acts), the pacts they
 * make (alliance, truce, holy war: always written to both gods), and the operator's "make them friends".
 */
public final class Rivalry {
    /** How long a truce lasts unless broken. */
    public static final long TRUCE_MS = 60 * 60_000L;

    private Rivalry() {
    }

    public static Bonds bonds(Realm r) {
        return r.memory().bonds;
    }

    /** Tells a god something that happened between gods; wakes it when attention is set. */
    static void tell(Realm r, String text, String attention) {
        boolean answers = attention != null && r.mayBeWokenByGods();
        r.run(() -> EventLogger.addLoggable(new DivineActLoggable(text, false, answers ? attention : null)));
    }

    // ---- Seeing each other's acts ----------------------------------------------------------------------

    /** Called after a god's successful act: every other god of its world weighs it. */
    public static void witness(String tool, JsonObject input, String resultText) {
        if (!LivingSettings.rivalryEnabled()) return;
        Realm me = Realm.current();
        if (!me.shared()) return;
        long now = System.currentTimeMillis();
        String words = switch (tool) {
            case "announce", "vessel_say", "commune" -> str(input, "message");
            case "apply_effect" -> str(input, "effect") + (harmful(str(input, "effect")) ? " harm" : "");
            default -> null;
        };
        String victim = str(input, "player");
        if (victim.isBlank()) victim = str(input, "target");
        for (Realm peer : me.peers()) {
            if (Godslayer.isSlain(peer)) continue;
            // A private word only moves the god it was spoken to.
            if ("commune".equals(tool) && !peer.displayName().equalsIgnoreCase(str(input, "god"))) continue;
            boolean namesPeer = words != null && ("commune".equals(tool) || Pantheon.names(words, peer.displayName()));
            int favor = victim.isBlank() || "all".equalsIgnoreCase(victim) ? 0 : peer.memory().favorOf(victim);
            Bonds.Effect e = Bonds.witnessed(tool, words, namesPeer, victim.isBlank() ? null : victim, favor);
            if (e.delta() == 0) continue;
            String why = me.displayName() + " " + e.why();
            bonds(peer).move(me.id, me.displayName(), e.delta(), e.why(), now);
            if (e.delta() <= -8) {
                peer.memory().heart.feel(Heart.Feeling.WRATH, -e.delta(), why, now);
            }
        }
    }

    private static boolean harmful(String effect) {
        String e = effect == null ? "" : effect.toLowerCase(Locale.ROOT);
        return e.contains("poison") || e.contains("wither") || e.contains("slow") || e.contains("blind")
                || e.contains("weak") || e.contains("nausea") || e.contains("hunger") || e.contains("darkness")
                || e.contains("levitation") || e.contains("mining_fatigue") || e.contains("harm");
    }

    /** Every few minutes: gods courting the same devoted mortals grow jealous of each other. */
    public static void contest(long now) {
        if (!LivingSettings.rivalryEnabled()) return;
        for (Realm a : Realm.all()) {
            if (!a.isHost() || !a.shared()) continue;
            List<Realm> gods = a.livingDomainGods();
            List<Player> mortals = a.players();
            for (int i = 0; i < gods.size(); i++) {
                for (int j = i + 1; j < gods.size(); j++) {
                    Realm x = gods.get(i), y = gods.get(j);
                    for (Player p : mortals) {
                        int fx = x.memory().favorOf(p.getName()), fy = y.memory().favorOf(p.getName());
                        Bonds.Effect e = Bonds.contested(p.getName(), fx, fy);
                        if (e.delta() == 0) continue;
                        bonds(x).move(y.id, y.displayName(), e.delta(), e.why(), now);
                        bonds(y).move(x.id, x.displayName(), e.delta(), e.why(), now);
                    }
                }
            }
        }
    }

    // ---- The god's own choices ---------------------------------------------------------------------------

    /** The god changes how it regards another (its own feeling). Returns the new score. */
    public static int regard(Realm me, Realm other, int delta, String why) {
        return bonds(me).move(other.id, other.displayName(), delta, why, System.currentTimeMillis());
    }

    /** Both sides now share this pact. */
    public static void setPact(Realm a, Realm b, Bonds.Pact pact) {
        long now = System.currentTimeMillis();
        long until = pact == Bonds.Pact.TRUCE ? now + TRUCE_MS : 0;
        bonds(a).setPact(b.id, b.displayName(), pact, until, now);
        bonds(b).setPact(a.id, a.displayName(), pact, until, now);
    }

    public static Bonds.Pact pact(Realm a, Realm b) {
        Bonds.Bond bond = bonds(a).find(b.id);
        return bond == null ? Bonds.Pact.NONE : bond.pact(System.currentTimeMillis());
    }

    /**
     * A pact action from a god: propose_alliance, propose_truce, accept, refuse, break. Returns what
     * happened, phrased for the god.
     */
    public static String pactAction(Realm me, Realm other, String action, String why) {
        long now = System.currentTimeMillis();
        Bonds mine = bonds(me), theirs = bonds(other);
        Bonds.Pact current = pact(me, other);
        String note = why == null || why.isBlank() ? "" : " (\"" + Bonds.clip(why, 100) + "\")";
        switch (action) {
            case "propose_alliance", "propose_truce" -> {
                Bonds.Pact p = action.endsWith("alliance") ? Bonds.Pact.ALLIANCE : Bonds.Pact.TRUCE;
                if (current == p) return "You and " + other.displayName() + " already share a " + p.id() + ".";
                Bonds.Bond their = theirs.find(me.id);
                if (their != null && p.id().equals(their.offered)) {
                    return "You already offered " + other.displayName() + " a " + p.id() + "; wait for its answer.";
                }
                // If it has already offered the same, proposing back seals it.
                Bonds.Bond mineBond = mine.find(other.id);
                if (mineBond != null && p.id().equals(mineBond.offered)) return accept(me, other, p, note);
                theirs.offer(me.id, me.displayName(), p, now);
                tell(other, me.displayName() + " offers you " + (p == Bonds.Pact.ALLIANCE ? "an alliance" : "a truce")
                        + note + ". Answer with pact accept or refuse.", me.displayName() + " offers you a pact");
                return "You offered " + other.displayName() + " " + (p == Bonds.Pact.ALLIANCE ? "an alliance" : "a truce")
                        + ". It will answer in its own time.";
            }
            case "accept" -> {
                Bonds.Bond b = mine.find(other.id);
                if (b == null || b.offered == null) return null;
                return accept(me, other, Bonds.Pact.parse(b.offered), note);
            }
            case "refuse" -> {
                Bonds.Bond b = mine.find(other.id);
                if (b == null || b.offered == null) return null;
                String what = b.offered;
                b.offered = null;
                mine.move(other.id, other.displayName(), -3, "you refused its " + what, now);
                theirs.move(me.id, me.displayName(), -6, "refused your " + what, now);
                tell(other, me.displayName() + " refuses your " + what + note + ".", null);
                return "You refused " + other.displayName() + "'s " + what + ".";
            }
            case "break" -> {
                if (current == Bonds.Pact.NONE || current == Bonds.Pact.WAR) return null;
                setPact(me, other, Bonds.Pact.NONE);
                theirs.move(me.id, me.displayName(), current == Bonds.Pact.ALLIANCE ? -30 : -20,
                        "broke your " + current.id() + note, now);
                other.memory().heart.feel(Heart.Feeling.WRATH, 20, me.displayName() + " broke your " + current.id(), now);
                tell(other, me.displayName() + " BREAKS the " + current.id() + " between you" + note + ".",
                        me.displayName() + " broke your pact");
                Journal.add(Journal.Kind.INFO, "broke the " + current.id() + " with " + other.displayName());
                return "The " + current.id() + " with " + other.displayName() + " is broken. It will not forget.";
            }
            default -> {
                return null;
            }
        }
    }

    private static String accept(Realm me, Realm other, Bonds.Pact p, String note) {
        long now = System.currentTimeMillis();
        setPact(me, other, p);
        bonds(me).move(other.id, other.displayName(), p == Bonds.Pact.ALLIANCE ? 15 : 8, "you made a " + p.id(), now);
        bonds(other).move(me.id, me.displayName(), p == Bonds.Pact.ALLIANCE ? 15 : 8, "accepted your " + p.id(), now);
        if (p == Bonds.Pact.TRUCE) {
            // A truce ends any holy war between them, with no winner.
            HolyDayRunner.endWar(me, other, null);
        }
        tell(other, me.displayName() + " ACCEPTS your " + p.id() + note + ". You are now "
                + (p == Bonds.Pact.ALLIANCE ? "allies" : "at truce for an hour") + ".", me.displayName()
                        + " accepted your pact");
        return "You and " + other.displayName() + " are now " + (p == Bonds.Pact.ALLIANCE ? "allies" : "at truce")
                + ".";
    }

    // ---- The operator ----------------------------------------------------------------------------------------

    /**
     * Sets (a preset word: allies, friends, neutral, rivals, enemies) or nudges (+N / -N) how two gods regard
     * each other, on both sides. Allies also get an alliance; enemies lose any pact. Returns a line for the
     * operator, or throws IllegalArgumentException with the reason.
     */
    public static String operatorSet(Realm a, Realm b, String how, String by) {
        if (a == null || b == null || a == b) throw new IllegalArgumentException("Pick two different gods.");
        if (!a.domain.equals(b.domain)) throw new IllegalArgumentException("Those gods do not share a world.");
        long now = System.currentTimeMillis();
        String h = how == null ? "" : how.trim().toLowerCase(Locale.ROOT);
        String why = "the keeper of this world" + (by == null ? "" : " (" + by + ")") + " willed it";
        Integer preset = Bonds.preset(h);
        if (preset != null) {
            bonds(a).set(b.id, b.displayName(), preset, why, now);
            bonds(b).set(a.id, a.displayName(), preset, why, now);
            Bonds.Pact p = pact(a, b);
            if (preset >= 60 && p != Bonds.Pact.ALLIANCE) {
                HolyDayRunner.endWar(a, b, null);
                setPact(a, b, Bonds.Pact.ALLIANCE);
            } else if (preset < 20 && p == Bonds.Pact.ALLIANCE) {
                setPact(a, b, Bonds.Pact.NONE);
            }
        } else {
            int delta;
            try {
                delta = Integer.parseInt(h.startsWith("+") ? h.substring(1) : h);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Say allies, friends, neutral, rivals, enemies, or +N / -N.");
            }
            delta = Math.max(-200, Math.min(200, delta));
            bonds(a).move(b.id, b.displayName(), delta, why, now);
            bonds(b).move(a.id, a.displayName(), delta, why, now);
        }
        a.memory().save();
        b.memory().save();
        int sa = bonds(a).score(b.id), sb = bonds(b).score(a.id);
        String text = "The keeper of this world has reshaped the bond between you and %s: you are now %s (%d).";
        tell(a, String.format(text, b.displayName(), Bonds.stance(sa), sa), null);
        tell(b, String.format(text, a.displayName(), Bonds.stance(sb), sb), null);
        return a.displayName() + " → " + b.displayName() + ": " + Bonds.stance(sa) + " (" + sa + "); "
                + b.displayName() + " → " + a.displayName() + ": " + Bonds.stance(sb) + " (" + sb + ")";
    }

    // ---- Perception ------------------------------------------------------------------------------------------

    /** "## Your bonds with the other gods" lines, or "" with no peers. */
    public static String perceive(Realm me) {
        if (!LivingSettings.rivalryEnabled() || !me.shared()) return "";
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        Bonds b = bonds(me);
        for (Realm peer : me.peers()) {
            sb.append(b.line(peer.id, peer.displayName(), now)).append('\n');
        }
        return sb.toString();
    }

    static String str(JsonObject o, String key) {
        if (o == null) return "";
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }
}
