package net.bigyous.gptgodmc.god.tools;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.vessel.VesselMood;

/**
 * The vessel's body language after the god acts: it turns on the mortal it smites or blesses,
 * gestures, and flares into a matching mood for a few seconds (unless the god chose a mood).
 */
final class VesselReactions {
    private record Reaction(VesselMood mood, int seconds, String gesture) {
    }

    private VesselReactions() {
    }

    private static Reaction forTool(String tool) {
        return switch (tool) {
            case "smite" -> new Reaction(VesselMood.WRATHFUL, 20, "judge");
            case "explode", "fling" -> new Reaction(VesselMood.WRATHFUL, 20, "cast");
            case "meteor" -> new Reaction(VesselMood.WRATHFUL, 25, "raise_arms");
            case "earthquake" -> new Reaction(VesselMood.WRATHFUL, 20, "slam");
            case "take_item" -> new Reaction(VesselMood.OMINOUS, 15, "judge");
            case "heal", "give_item", "supply_chest", "grant_xp", "empower" -> new Reaction(VesselMood.JOYFUL, 15, "spread_arms");
            case "revive" -> new Reaction(VesselMood.EXALTED, 15, "raise_arms");
            default -> null;
        };
    }

    /** Called after a power succeeds, from any thread. */
    static void after(String tool, JsonObject input) {
        Reaction r = forTool(tool);
        if (r == null || !GPTGOD.get().isEnabled()) return;
        String who = input == null ? null
                : input.has("player") ? input.get("player").getAsString()
                : input.has("target") ? input.get("target").getAsString() : null;
        Runnable react = () -> {
            if (!God.vessel().isManifested()) {
                // Formless, the god still feels it: its voice follows the mood (wrath speaks demonic).
                God.vessel().setMood(r.mood(), r.seconds());
                return;
            }
            Player toward = null;
            if (who != null && !who.equalsIgnoreCase("all")) {
                try {
                    toward = Targets.player(who);
                } catch (ToolException ignored) {
                    // a place, not a mortal
                }
            }
            God.vessel().react(r.mood(), r.seconds(), toward, r.gesture());
        };
        if (Bukkit.isPrimaryThread()) {
            react.run();
        } else {
            Realm.soon(react); // keeps this god bound when it runs
        }
    }
}
