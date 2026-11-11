package net.bigyous.gptgodmc.god.tools;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Oaths;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Speech;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Oaths mortals swear before the god (/swear): witness or refuse them, then judge them. */
final class OathTools {
    private OathTools() {
    }

    static List<GodTool> all() {
        return List.of(
                GodTool.sync("witness_oath",
                        "Answer an oath a mortal swore before you (/swear). Accept to bind them: restate the terms "
                                + "in your own words, set how long it holds, and optionally a watch the server keeps, "
                                + "so a breach or fulfilment reaches you at once (you judge it with resolve_oath "
                                + "either way). Refuse oaths you find hollow, mocking or beneath you. watch never = any "
                                + "matching deed breaks it; must = they must do it count times. Acts: "
                                + String.join(", ", Oaths.ACTS) + " (enter = set foot in a place). Targets: a "
                                + "mortal's name, a creature (cow, villager) or kind (animal, monster, villager, "
                                + "golem, pet), a block or kind (log, crop, ore, sapling); leave it out for any. "
                                + "What the server cannot see (loyalty, honesty) takes watch none: judge it yourself.",
                        Schema.object()
                                .str("oath", "The oath's id, like o3.", true)
                                .bool("accept", "true to witness and bind it; false to refuse it.", true)
                                .str("terms", "The oath as you bind them to it, in your words; or why you refuse.",
                                        false)
                                .integer("minutes", "How long it holds in minutes; 0 = for as long as they live. "
                                        + "Default 0.", false, 0, 43200)
                                .choice("watch", "What the server watches for you: none, never or must. Default none.",
                                        false, List.of("none", "never", "must"))
                                .choice("act", "The deed watched (with never or must).", false, Oaths.ACTS)
                                .str("target", "Who or what the deed is done to (see above); leave out for any.", false)
                                .integer("count", "With must: how many times. Default 1.", false, 1, 1000)
                                .str("place", "Only deeds near this place count: " + Targets.TARGET_HELP
                                        + ". Needed for enter.", false)
                                .integer("radius", "With place: blocks around it. Default 24.", false, 2, 256),
                        OathTools::witness),
                GodTool.sync("resolve_oath",
                        "Judge an oath you witnessed: kept (honor them and reward them as you see fit), broken "
                                + "(they are forsworn, remembered for ever; punish as you see fit) or released (you "
                                + "free them from it without shame). Judge a broken oath only on what you saw.",
                        Schema.object()
                                .str("oath", "The oath's id, like o3.", true)
                                .choice("outcome", "kept, broken or released.", true,
                                        List.of("kept", "broken", "released"))
                                .str("note", "What you judged, briefly.", false),
                        OathTools::resolve));
    }

    private static ToolResult witness(Args a) throws ToolException {
        requireOn();
        Memory m = Memory.get();
        long now = System.currentTimeMillis();
        String god = Realm.current().displayName();
        Oaths.Oath o;
        try {
            if (!a.bool("accept", true)) {
                o = Oaths.refuse(m, a.str("oath"), a.str("terms", null), now);
                m.save();
                tell(o.swearer, god + " does not accept your oath" + (o.outcome == null ? "." : ": " + o.outcome),
                        NamedTextColor.DARK_GRAY, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.6f);
                return ToolResult.ok("You refused %s's oath %s.", o.swearer, o.id);
            }
            Oaths.Clause watch = null;
            String kind = a.str("watch", "none");
            if (!kind.equalsIgnoreCase("none")) {
                Location at = a.has("place") ? Targets.location(a.str("place")) : null;
                watch = Oaths.clause(kind.toLowerCase(), a.str("act", null), a.str("target", null),
                        a.integer("count", 1, 1, 1000), at == null ? null : at.getWorld().getName(),
                        at == null ? 0 : at.getBlockX(), at == null ? 0 : at.getBlockY(), at == null ? 0 : at.getBlockZ(),
                        at == null ? 0 : a.integer("radius", 24, 2, 256));
            }
            o = Oaths.witness(m, a.str("oath"), a.str("terms", null), a.integer("minutes", 0, 0, 43200), watch, now);
        } catch (Oaths.Refusal r) {
            throw new ToolException(r.getMessage());
        }
        m.addDeed(o.swearer, "swore an oath before you (" + o.id + "): " + o.terms, now);
        m.save();
        tell(o.swearer, god + " witnesses your oath: \"" + o.terms + "\"" + (o.until > 0
                ? " (for " + a.integer("minutes", 0, 0, 43200) + " minutes)" : " (for life)") + ". Keep it.",
                Speech.VIOLET, Sound.BLOCK_BELL_USE, 0.6f);
        return ToolResult.ok("You witnessed %s's oath %s: \"%s\"%s.", o.swearer, o.id, o.terms,
                o.watch == null ? ", unwatched: judge it yourself" : ", watched: " + Oaths.describe(o.watch));
    }

    private static ToolResult resolve(Args a) throws ToolException {
        requireOn();
        Memory m = Memory.get();
        String god = Realm.current().displayName();
        Oaths.Oath o;
        try {
            o = Oaths.resolve(m, a.str("oath"), a.str("outcome").toLowerCase(), a.str("note", null),
                    System.currentTimeMillis());
        } catch (Oaths.Refusal r) {
            throw new ToolException(r.getMessage());
        }
        m.save();
        switch (o.status) {
            case "kept" -> tell(o.swearer, god + " holds your oath kept: \"" + o.terms + "\".", Speech.VIOLET,
                    Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f);
            case "broken" -> tell(o.swearer, god + " names you forsworn: you broke \"" + o.terms + "\".",
                    NamedTextColor.DARK_RED, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f);
            default -> tell(o.swearer, god + " releases you from your oath: \"" + o.terms + "\".", Speech.DEEP_VIOLET,
                    Sound.BLOCK_BEACON_DEACTIVATE, 1f);
        }
        Memory.Subject s = m.subjectIfAny(o.swearer);
        return ToolResult.ok("%s's oath %s is %s.%s%s", o.swearer, o.id, o.status,
                s == null ? "" : " They have kept " + s.oathsKept + " and broken " + s.oathsBroken + " in all.",
                o.status.equals("kept") ? " Reward them if you will." : o.status.equals("broken")
                        ? " Punish them if you will." : "");
    }

    private static void tell(String name, String text, net.kyori.adventure.text.format.TextColor color, Sound sound,
            float pitch) {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) return;
        p.sendMessage(Component.text(text, color));
        p.playSound(p.getLocation(), sound, 0.8f, pitch);
    }

    private static void requireOn() throws ToolException {
        if (!GodSettings.oathsEnabled()) {
            throw new ToolException("The server keeper has turned oaths off (oaths.enabled).");
        }
    }
}
