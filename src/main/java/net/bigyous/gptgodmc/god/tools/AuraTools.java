package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.god.Auras;
import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Rites;
import net.bigyous.gptgodmc.god.vessel.Vessel;

/** Lasting auras round mortals or the god's own body. */
final class AuraTools {
    private AuraTools() {
    }

    static List<GodTool> all() {
        return List.of(GodTool.sync("aura",
                "Wrap a mortal (or your own body) in a lasting aura for a while: healing (a green ring with rising "
                        + "crosses and hearts), holy (a golden halo and ring), flame, frost, shadow, stars, storm or "
                        + "nature. Use it often to make your favor, wrath and blessings visible: a healed mortal "
                        + "glows, a cursed one smoulders, your body shines when you are pleased. kind off removes it.",
                Schema.object()
                        .str("target", "A mortal's name, 'all', or 'vessel' for your own body.", true)
                        .choice("kind", "The aura, or off.", true, withOff())
                        .integer("seconds", "How long it lasts, 5-300. Default 60.", false, 5, 300),
                a -> {
                    if (!GodSettings.ritesEnabled()) {
                        throw new ToolException("The server keeper has turned rites and auras off.");
                    }
                    String target = a.str("target").trim();
                    String kindName = a.str("kind").trim().toLowerCase(java.util.Locale.ROOT);
                    int seconds = a.integer("seconds", 60, 5, 300);
                    String realm = Realm.current().id;
                    if (target.equalsIgnoreCase("vessel")) {
                        String key = "vessel:" + realm;
                        if (kindName.equals("off")) {
                            return ToolResult.ok(Auras.stop(key) ? "Your body's aura fades." : "Your body has no aura.");
                        }
                        Vessel v = God.vessel();
                        if (v == null || !v.hasBody()) throw new ToolException("You have no body to wear an aura.");
                        Rites.Kind k = Auras.kind(kindName);
                        if (!Auras.start(key, k, () -> v.hasBody() ? v.location() : null, null, seconds)) {
                            throw new ToolException("Too many auras shine already; wait or remove one.");
                        }
                        return ToolResult.ok("Your body wears a %s aura for %d s.", kindName, seconds);
                    }
                    List<String> touched = new ArrayList<>();
                    for (Player p : Targets.players(target)) {
                        String key = "player:" + p.getUniqueId();
                        if (kindName.equals("off")) {
                            if (Auras.stop(key)) touched.add(p.getName());
                            continue;
                        }
                        Rites.Kind k = Auras.kind(kindName);
                        if (Auras.start(key, k, () -> p.isOnline() ? p.getLocation() : (Location) null, p, seconds)) {
                            touched.add(p.getName());
                        }
                    }
                    if (touched.isEmpty()) {
                        throw new ToolException(kindName.equals("off") ? "No such aura to remove."
                                : "Too many auras shine already; wait or remove one.");
                    }
                    return kindName.equals("off") ? ToolResult.ok("The aura fades from " + String.join(", ", touched) + ".")
                            : ToolResult.ok("%s %s wrapped in a %s aura for %d s.", String.join(", ", touched),
                                    touched.size() == 1 ? "is" : "are", kindName, seconds);
                }));
    }

    private static List<String> withOff() {
        List<String> out = new ArrayList<>(Auras.KINDS);
        out.add("off");
        return out;
    }
}
