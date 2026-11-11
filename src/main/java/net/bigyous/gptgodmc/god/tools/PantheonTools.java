package net.bigyous.gptgodmc.god.tools;

import java.util.List;

import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Pantheon;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Speech;
import net.bigyous.gptgodmc.loggables.DivineActLoggable;

/** Gods sharing a world: speaking to one another, and seeing one another's acts. Last in the tool list. */
final class PantheonTools {
    private PantheonTools() {
    }

    static List<GodTool> all() {
        return List.of(GodTool.sync("commune",
                "Speak privately to another god of your world, god to god; no mortal hears it. Only useful when "
                        + "other gods share your world (they are listed under \"Other gods of this world\"). Use it "
                        + "to bargain, threaten, ally or mock; you cannot read their minds or command them.",
                Schema.object()
                        .str("god", "The other god's name.", true)
                        .str("message", "What you say to them (under ~200 characters).", true),
                a -> {
                    Realm me = Realm.current();
                    if (!me.shared()) throw new ToolException("No other god shares your world.");
                    Realm to = Realm.byName(a.str("god"));
                    if (to == null || to == me || !to.domain.equals(me.domain)) {
                        throw new ToolException("No god named '" + a.str("god") + "' shares your world. Others: "
                                + String.join(", ", me.peers().stream().map(Realm::displayName).toList()) + ".");
                    }
                    if (net.bigyous.gptgodmc.god.Godslayer.isSlain(to)) {
                        throw new ToolException(to.displayName() + " is dead, slain with the Godslayer. The dead "
                                + "hear nothing.");
                    }
                    String message = Speech.clean(a.str("message"));
                    boolean answers = to.mayBeWokenByGods();
                    String text = me.displayName() + " speaks to you privately, god to god: \"" + message + "\"";
                    to.run(() -> EventLogger.addLoggable(new DivineActLoggable(text, true,
                            answers ? me.displayName() + " spoke to you" : null)));
                    return ToolResult.ok(answers ? to.displayName() + " hears you and may answer."
                            : to.displayName() + " will hear your words when next it looks, but will not answer at "
                                    + "once (gods have spoken enough for now).");
                }));
    }

    /** Lets the other gods of this world see a god's successful act (private acts stay unseen). */
    static void witness(String tool, JsonObject input, ToolResult result) {
        Realm me = Realm.current();
        if (!me.shared()) return;
        try {
            // The other gods' regard for this one moves with what it does (god/living/Rivalry).
            net.bigyous.gptgodmc.god.living.Rivalry.witness(tool, input, result.text());
        } catch (RuntimeException e) {
            net.bigyous.gptgodmc.GPTGOD.LOGGER.warn("Could not weigh the act between gods", e);
        }
        String text = Pantheon.witnessed(me.displayName(), tool, input, result.text());
        if (text == null) return;
        boolean spoken = Pantheon.SPOKEN.contains(tool);
        for (Realm peer : me.peers()) {
            // Naming another god in public draws its eye; anything else waits for its next look.
            String attention = Pantheon.names(text, peer.displayName()) && peer.mayBeWokenByGods()
                    ? me.displayName() + " named you" : null;
            peer.run(() -> EventLogger.addLoggable(new DivineActLoggable(text, spoken, attention)));
        }
    }

    /** Refused when the bound god's power does not reach this tool, else null. */
    static String refusal(String tool) {
        var power = Realm.current().power();
        return power.allows(tool) ? null : power.refusal(tool);
    }

    static int exchanges() {
        return GodSettings.pantheonDivineExchanges();
    }
}
