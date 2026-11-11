package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.god.Godslayer;
import net.bigyous.gptgodmc.god.Power;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.living.Bonds;
import net.bigyous.gptgodmc.god.living.HolyDayRunner;
import net.bigyous.gptgodmc.god.living.HolyDays;
import net.bigyous.gptgodmc.god.living.LivingSettings;
import net.bigyous.gptgodmc.god.living.Rivalry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Holy days: proclaiming them, and scoring, ending or yielding them. Part of {@link LivingTools}. */
final class HolyDayTools {
    private HolyDayTools() {
    }

    static List<GodTool> all() {
        return List.of(GodTool.sync("proclaim",
                "Proclaim a holy day, announced ahead with a countdown bar and titles, scored while it runs, with the "
                        + "results brought back to you to reward and punish as you promised. You write it: the title, "
                        + "what you ask of mortals (rules), which deeds earn points (counts), the reward and the "
                        + "consequence; you can also award points yourself for anything you judge (holy_day score). "
                        + "Kinds: festival (fireworks, joy), trial (a contest of your design), hunt (a glowing quarry "
                        + "beast is loosed; who slays it wins), raid (waves of raiders on a mortal you dislike; real but "
                        + "survivable, and survivors earn reward), omen (signs for hours or days foretelling a great act "
                        + "you will then perform), war (a holy war on a rival god: mortals pick a side with /side, the "
                        + "side with more points wins), rite (a ceremony). Hold them now and then; mortals love a "
                        + "calendar.",
                Schema.object()
                        .choice("kind", "The kind of holy day.", true,
                                List.of("festival", "trial", "hunt", "raid", "omen", "war", "rite"))
                        .str("title", "Its name, e.g. \"Festival of Embers\" (under 48 characters).", true)
                        .str("rules", "What you ask of mortals, or the omen's words (under 240 characters).", true)
                        .strings("counts", "Deeds that earn points automatically: " + String.join(", ",
                                HolyDays.ACTS.keySet()) + ". Empty: only the points you award.", false, 6)
                        .integer("starts_in_minutes", "Countdown before it begins, 0-60. Default 2.", false, 0, 60)
                        .integer("minutes", "How long it runs: 2-120 (raid 2-10, omen up to 10080 = a week). Default 15.",
                                false, 2, 10080)
                        .str("reward", "What you promise the best (you give it yourself at the end).", false)
                        .str("consequence", "What befalls those who fail or scorn it.", false)
                        .str("target", "raid: the mortal raided. hunt: who the quarry is loosed near (optional).", false)
                        .choice("quarry", "hunt: the beast. Default ravager.", false, HolyDays.QUARRIES)
                        .str("rival", "war: the god you war on.", false)
                        .bool("pvp", "war: whether killing mortals of the other side earns points. Default false.", false),
                HolyDayTools::proclaim),
                GodTool.sync("holy_day",
                        "Run a holy day you are part of: score (award or take points from a mortal, or 'side:<god>' "
                                + "in a war, for anything you judge), end it now (your own only; results come back to "
                                + "you), cancel it (your own; no results), or yield (a holy war: your side loses).",
                        Schema.object()
                                .integer("id", "The holy day's number (#N).", true, 1, 100000)
                                .choice("action", "What you do.", true, List.of("score", "end", "cancel", "yield"))
                                .str("who", "score: the mortal, or side:<god name> in a war.", false)
                                .integer("points", "score: points to give (negative to take), -50 to 50.", false, -50, 50)
                                .str("reason", "score: why (the mortal is told).", false),
                        HolyDayTools::run));
    }

    private static ToolResult proclaim(Args a) throws ToolException {
        if (!LivingSettings.eventsEnabled()) throw new ToolException("The keeper of this world has turned holy days off.");
        Realm me = Realm.current();
        HolyDays.Kind kind = HolyDays.Kind.parse(a.str("kind"));
        if (kind == null) throw new ToolException("Unknown kind; choose festival, trial, hunt, raid, omen, war or rite.");
        boolean lesser = me.power() == Power.LESSER;
        Realm rival = null;
        Player target = null;
        int minutes = a.integer("minutes", kind == HolyDays.Kind.OMEN ? 120 : kind == HolyDays.Kind.RAID ? 4 : 15, 2,
                10080);
        switch (kind) {
            case RAID -> {
                if (lesser) throw new ToolException("Raids are beyond a lesser god.");
                if (!LivingSettings.raidsAllowed()) throw new ToolException("The keeper of this world forbids raids.");
                target = Targets.player(a.str("target", ""));
                if (!me.players().contains(target)) throw new ToolException(target.getName() + " is not in your world.");
                int grace = LivingSettings.raidGraceMinutes();
                if (target.getTicksLived() < grace * 1200) {
                    throw new ToolException(target.getName() + " has only just arrived; let them settle (" + grace
                            + " minutes) before you raid them.");
                }
                minutes = Math.max(2, Math.min(10, minutes));
            }
            case HUNT -> {
                String t = a.str("target", "");
                if (!t.isBlank()) target = Targets.player(t);
                minutes = Math.min(minutes, 120);
            }
            case WAR -> {
                if (lesser) throw new ToolException("A lesser god cannot wage holy war.");
                rival = LivingTools.peer(a.str("rival", ""));
                Bonds.Pact p = Rivalry.pact(me, rival);
                if (p == Bonds.Pact.ALLIANCE || p == Bonds.Pact.TRUCE) {
                    throw new ToolException("You are bound to " + rival.displayName() + " by a " + p.id()
                            + ". Break it first (pact break), if you dare.");
                }
                minutes = Math.max(5, Math.min(120, minutes));
            }
            case OMEN -> {
                minutes = Math.max(10, minutes);
            }
            default -> minutes = Math.min(minutes, 120);
        }
        List<String> counts = new ArrayList<>();
        for (String c : a.strings("counts")) {
            String k = c.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            if (!k.isEmpty() && !counts.contains(k)) counts.add(k);
        }
        HolyDays.Request req = new HolyDays.Request(kind, me.id, me.displayName(), a.str("title"), a.str("rules", ""),
                counts, a.str("reward", ""), a.str("consequence", ""), target == null ? null : target.getName(),
                a.str("quarry", "ravager"), rival == null ? null : rival.id, rival == null ? null : rival.displayName(),
                a.bool("pvp", false), a.integer("starts_in_minutes", 2, 0, 60), minutes);
        HolyDays.Day d;
        try {
            d = HolyDayRunner.proclaim(req, me, rival, target);
        } catch (IllegalStateException e) {
            throw new ToolException(e.getMessage());
        }
        return ToolResult.ok("Holy day #%d proclaimed: %s \"%s\", %s, running %s.%s", d.id, kind.id, d.title,
                HolyDays.countdown(d, System.currentTimeMillis()), HolyDays.clock(d.endsAt - d.startsAt),
                kind == HolyDays.Kind.WAR ? " Urge mortals to /side with you." : "");
    }

    private static ToolResult run(Args a) throws ToolException {
        Realm me = Realm.current();
        HolyDays store = HolyDayRunner.store();
        int id = a.integer("id", 0, 1, 100000);
        HolyDays.Day d = store.find(id);
        if (d == null || !d.involves(me.id)) throw new ToolException("You have no holy day #" + id + ".");
        if (!d.live()) throw new ToolException("Holy day #" + id + " is already over (" + d.status + ").");
        String action = a.str("action").trim().toLowerCase(Locale.ROOT);
        boolean mine = me.id.equalsIgnoreCase(d.god);
        switch (action) {
            case "score" -> {
                if (!d.running()) throw new ToolException("\"" + d.title + "\" has not begun yet.");
                String who = a.str("who", "").trim();
                if (who.isEmpty()) throw new ToolException("Score whom?");
                int points = a.integer("points", 0, -50, 50);
                if (points == 0) throw new ToolException("Give some points (or take some).");
                String reason = a.str("reason", "");
                double total;
                String shown;
                if (who.toLowerCase(Locale.ROOT).startsWith("side:")) {
                    if (d.kind() != HolyDays.Kind.WAR) throw new ToolException("Only a holy war has sides.");
                    Realm side = Realm.byName(who.substring(5).trim());
                    if (side == null || !d.involves(side.id)) throw new ToolException("That god is not in this war.");
                    total = store.award(d, "side:" + side.id, points);
                    shown = side.displayName() + "'s side";
                } else {
                    Player p = Targets.player(who);
                    total = store.award(d, p.getName(), points);
                    shown = p.getName();
                    p.sendActionBar(Component.text((points > 0 ? "+" : "") + points + " from " + me.displayName()
                            + (reason.isBlank() ? "" : ": " + reason), points > 0 ? NamedTextColor.GOLD : NamedTextColor.RED));
                }
                HolyDayRunner.changed();
                return ToolResult.ok("%s now has %s points in \"%s\".", shown, HolyDays.fmt(total), d.title);
            }
            case "end" -> {
                if (!mine) throw new ToolException("Only " + d.godName + " may end it" + (d.kind() == HolyDays.Kind.WAR
                        ? "; you may yield, or offer a truce (pact propose_truce)." : "."));
                HolyDayRunner.finish(d, null);
                return ToolResult.ok("\"%s\" ends: %s.", d.title, d.outcome);
            }
            case "cancel" -> {
                if (!mine) throw new ToolException("Only " + d.godName + " may call it off.");
                HolyDayRunner.cancel(d);
                return ToolResult.ok("\"%s\" is called off.", d.title);
            }
            case "yield" -> {
                if (d.kind() != HolyDays.Kind.WAR) throw new ToolException("Only a holy war can be yielded.");
                Realm other = Realm.byId(mine ? d.rival : d.god);
                if (other == null || Godslayer.isSlain(other)) {
                    HolyDayRunner.cancel(d);
                    return ToolResult.ok("Your foe is gone; the war is over.");
                }
                HolyDayRunner.endWar(me, other, me);
                return ToolResult.ok("You yield the holy war to %s.", other.displayName());
            }
            default -> throw new ToolException("Unknown action " + action + ".");
        }
    }
}
