package net.bigyous.gptgodmc.god.tools;

import java.util.List;
import java.util.Locale;

import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.god.DemigodPowers;
import net.bigyous.gptgodmc.god.Demigods;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.Rites;

/** Apotheosis: the god's rarest gift. */
final class ApotheosisTools {
    private ApotheosisTools() {
    }

    static List<GodTool> all() {
        return List.of(GodTool.sync("apotheosis",
                "Your RAREST gift, which most gods never give: raise a mortal to demigod, like a hero of legend. "
                        + "They gain a hero's strength, toughness, speed and leap, ten more hearts, flight, wings of "
                        + "light and a halo, and falls cannot hurt them. Give it only for deeds that will be told for "
                        + "ages, to a mortal you hold in the highest regard, never because they asked. It is limited "
                        + "(one demigod at a time, a long wait between) and the server may forbid it entirely. "
                        + "action unmake takes the divinity away again.",
                Schema.object()
                        .str("player", "The mortal.", true)
                        .choice("action", "raise or unmake.", true, List.of("raise", "unmake"))
                        .str("reason", "The deed that earned it, in a sentence (for raise).", false),
                a -> {
                    String action = a.str("action").toLowerCase(Locale.ROOT);
                    Player p = Targets.player(a.str("player"));
                    long now = System.currentTimeMillis();
                    Demigods d = DemigodPowers.state();
                    if (action.equals("unmake")) {
                        if (d.unmake(p.getName()) == null) throw new ToolException(p.getName() + " is no demigod.");
                        DemigodPowers.save();
                        DemigodPowers.unbless(p);
                        p.getWorld().strikeLightningEffect(p.getLocation());
                        return ToolResult.ok("%s is mortal again: the wings and the strength are gone.", p.getName());
                    }
                    int favor = Memory.get().favorOf(p.getName());
                    String no = d.refusal(GodSettings.apotheosisEnabled(), p.getName(), favor,
                            GodSettings.apotheosisMinFavor(), GodSettings.apotheosisMax(),
                            GodSettings.apotheosisCooldownHours(), now);
                    if (no != null) throw new ToolException(no);
                    String reason = a.str("reason", "");
                    if (reason.isBlank()) throw new ToolException("Name the deed that earns this, in reason.");
                    Persona persona = Persona.current();
                    String god = persona == null ? "the god" : persona.shortName();
                    d.raise(p.getName(), p.getUniqueId().toString(), god, reason,
                            GodSettings.apotheosisMinutes(), now);
                    DemigodPowers.save();
                    // The ascension rite lifts them; at its climax the divinity takes hold.
                    if (!Rites.play(Rites.Kind.ASCENSION, p.getLocation(), p, () -> DemigodPowers.crown(p, god))) {
                        DemigodPowers.crown(p, god);
                    }
                    return ToolResult.ok("%s ascends: a demigod%s. Speak to the world of what they did.", p.getName(),
                            GodSettings.apotheosisMinutes() <= 0 ? " until you unmake them"
                                    : " for " + GodSettings.apotheosisMinutes() + " minutes");
                }));
    }
}
