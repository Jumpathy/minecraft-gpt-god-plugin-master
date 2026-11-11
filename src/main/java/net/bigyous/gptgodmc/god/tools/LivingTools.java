package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.god.Godslayer;
import net.bigyous.gptgodmc.god.Heart;
import net.bigyous.gptgodmc.god.LivingAccess;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Purpose;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.build.Builder;
import net.bigyous.gptgodmc.god.living.Bonds;
import net.bigyous.gptgodmc.god.living.LivingSettings;
import net.bigyous.gptgodmc.god.living.Rivalry;
import net.bigyous.gptgodmc.god.living.ServantKind;
import net.bigyous.gptgodmc.god.living.ServantRules;
import net.bigyous.gptgodmc.god.living.Servants;
import net.bigyous.gptgodmc.loggables.DivineActLoggable;

/**
 * Gods that live in the world: regard and pacts with other gods, sabotage, joint blessings, holy days
 * (festivals, trials, hunts, raids, omens, holy wars) and servants. Appended last in the tool list.
 */
final class LivingTools {
    private LivingTools() {
    }

    static List<GodTool> all() {
        List<GodTool> out = new ArrayList<>();
        out.add(GodTool.sync("regard",
                "Decide how you feel about another god of your world: move your regard for it up or down (-100 sworn "
                        + "enemy .. 100 true ally) and say why. Your regard also moves on its own with what it does to "
                        + "you and your faithful; this is your own judgement. Shown under \"Your bonds\".",
                Schema.object()
                        .str("god", "The other god's name.", true)
                        .integer("change", "How much your regard moves, -30 to 30.", true, -30, 30)
                        .str("why", "Why, in a few words (remembered).", true),
                a -> {
                    Realm me = Realm.current();
                    Realm other = peer(a.str("god"));
                    int score = Rivalry.regard(me, other, a.integer("change", 0, -30, 30), a.str("why"));
                    return ToolResult.ok("You now regard %s as %s (%d).", other.displayName(), Bonds.stance(score),
                            score);
                }));
        out.add(GodTool.sync("pact",
                "Make or break a pact with another god of your world: propose_alliance, propose_truce (an hour of "
                        + "peace; also ends a holy war between you), accept or refuse what it offered, or break a pact. "
                        + "Allies may bless together (joint_blessing). Breaking a pact angers the other god.",
                Schema.object()
                        .str("god", "The other god's name.", true)
                        .choice("action", "What you do.", true,
                                List.of("propose_alliance", "propose_truce", "accept", "refuse", "break"))
                        .str("why", "What you say with it (optional).", false),
                a -> {
                    Realm me = Realm.current();
                    Realm other = peer(a.str("god"));
                    String action = a.str("action").trim().toLowerCase(Locale.ROOT);
                    String done = Rivalry.pactAction(me, other, action, a.str("why", ""));
                    if (done == null) {
                        throw new ToolException(action.equals("break")
                                ? "You share no alliance or truce with " + other.displayName() + " to break (a holy "
                                        + "war ends with a truce, or holy_day yield)."
                                : other.displayName() + " has offered you nothing to " + action + ".");
                    }
                    return ToolResult.ok(done);
                }));
        out.add(GodTool.sync("sabotage",
                "Spoil a rival god's work or mission, within your power: crumble (knock out some blocks of one of its "
                        + "works), blight (rot them into netherrack and soul soil), overgrow (bury them in moss and "
                        + "leaves), or mission (set back its leading mission). Only greater and supreme gods, never "
                        + "an ally, and the rival will know it was you. Breaks any truce.",
                Schema.object()
                        .str("god", "The rival god's name.", true)
                        .choice("how", "How you spoil it.", true, List.of("crumble", "blight", "overgrow", "mission"))
                        .str("work", "Which of its works (name or id); default its newest. Ignored for mission.", false)
                        .str("why", "Why (it will hear of this).", false),
                LivingTools::sabotage));
        out.add(GodTool.sync("joint_blessing",
                "Bless a mortal together with an allied god: a stronger, longer gift than either could give alone, "
                        + "in both your colours. Needs an alliance (pact).",
                Schema.object()
                        .str("god", "Your ally's name.", true)
                        .str("player", "The mortal to bless.", true)
                        .choice("gift", "The gift.", true, List.of("strength", "regeneration", "speed", "resistance",
                                "haste", "luck", "fire_resistance", "health_boost", "jump_boost", "night_vision"))
                        .integer("minutes", "How long, 1-10. Default 5.", false, 1, 10),
                LivingTools::jointBlessing));
        out.addAll(HolyDayTools.all());
        out.add(GodTool.sync("summon_servant",
                "Summon a servant to act for you in the world (each lasts a while, then returns to you): "
                        + String.join("; ", java.util.Arrays.stream(ServantKind.values())
                                .map(k -> k.id + " = " + k.summary).toList())
                        + ". Angels suit radiant gods, imps and shades darker ones. It guards where it appears unless "
                        + "you give another order; direct it with command_servant.",
                Schema.object()
                        .choice("kind", "Which servant.", true, ServantKind.ids())
                        .str("near", Targets.TARGET_HELP + " Where it appears. Default: your body, else the nearest mortal.",
                                false)
                        .str("name", "Its name (optional; shown to mortals).", false)
                        .integer("minutes", "How long it stays, 1-" + LivingSettings.servantsMaxMinutes()
                                + ". Default 10.", false, 1, 240)
                        .choice("order", "Its first order. Default guard (priests: preach).", false,
                                List.of("guard", "follow", "patrol", "preach", "stay")),
                LivingTools::summon));
        out.add(GodTool.sync("command_servant",
                "Direct your servants: follow (you or a mortal), guard or patrol a place (they fight monsters, and "
                        + "mortals you despise, that come near), attack a mortal or creature, preach (words, or your "
                        + "commandments when none), go to a place, stay, say something aloud, or dismiss. You may set "
                        + "them on any mortal, but mostly on those you despise; your faithful will judge it.",
                Schema.object()
                        .str("servant", "Its id (s1), name, kind, or 'all'.", true)
                        .choice("order", "The order.", true,
                                List.of("follow", "guard", "patrol", "attack", "preach", "go", "stay", "say", "dismiss"))
                        .str("target", "Whom or where: a mortal for follow/attack ('nearest_monster' to hunt one), "
                                + "or a place for guard/patrol/preach/go (" + Targets.TARGET_HELP + ").", false)
                        .str("message", "Words it says aloud, or the sermon to preach.", false),
                LivingTools::command));
        return out;
    }

    // ---- Helpers ----------------------------------------------------------------------------------------

    /** Another living god of this world, or a ToolException listing them. */
    static Realm peer(String name) throws ToolException {
        Realm me = Realm.current();
        if (!LivingSettings.rivalryEnabled()) throw new ToolException("The keeper of this world has turned rivalries off.");
        if (!me.shared()) throw new ToolException("No other god shares your world.");
        Realm r = Realm.byName(name);
        if (r == null || r == me || !r.domain.equals(me.domain)) {
            throw new ToolException("No god named '" + name + "' shares your world. Others: "
                    + String.join(", ", me.peers().stream().map(Realm::displayName).toList()) + ".");
        }
        if (Godslayer.isSlain(r)) throw new ToolException(r.displayName() + " is dead, slain with the Godslayer.");
        return r;
    }

    static void tell(Realm r, String text, String attention) {
        r.run(() -> EventLogger.addLoggable(new DivineActLoggable(text, false,
                attention != null && r.mayBeWokenByGods() ? attention : null)));
    }

    // ---- Sabotage --------------------------------------------------------------------------------------

    private static final Set<Material> NEVER = Set.of(Material.BEDROCK, Material.BARRIER, Material.END_PORTAL_FRAME,
            Material.END_PORTAL, Material.NETHER_PORTAL, Material.COMMAND_BLOCK, Material.SPAWNER);

    private static ToolResult sabotage(Args a) throws ToolException {
        Realm me = Realm.current();
        Realm rival = peer(a.str("god"));
        if (!LivingSettings.sabotageAllowed()) throw new ToolException("The keeper of this world forbids sabotage.");
        if (me.power() == net.bigyous.gptgodmc.god.Power.LESSER) {
            throw new ToolException("Sabotage is beyond a lesser god; taunt or out-bless your rival instead.");
        }
        Bonds.Pact pact = Rivalry.pact(me, rival);
        if (pact == Bonds.Pact.ALLIANCE) {
            throw new ToolException(rival.displayName() + " is your ally. Break the alliance first (pact break).");
        }
        long now = System.currentTimeMillis();
        Bonds.Bond bond = Rivalry.bonds(me).of(rival.id, rival.displayName(), now);
        long wait = bond.lastSabotage + LivingSettings.sabotageCooldownMinutes() * 60_000L - now;
        if (wait > 0) {
            throw new ToolException("You spoiled " + rival.displayName() + "'s things too recently; wait "
                    + EventLogger.ago(java.time.Duration.ofMillis(wait)) + ".");
        }
        String how = a.str("how").trim().toLowerCase(Locale.ROOT);
        String why = a.str("why", "");
        String did;
        if (how.equals("mission")) {
            Memory m = rival.memory();
            Purpose.Goal g = Purpose.banner(m);
            if (g == null) {
                List<Purpose.Goal> act = Purpose.active(m);
                g = act.isEmpty() ? null : act.get(0);
            }
            if (g == null) throw new ToolException(rival.displayName() + " pursues no goal or mission to spoil.");
            int before = g.progress;
            int setback = Math.min(before, 10 + ThreadLocalRandom.current().nextInt(11));
            if (setback <= 0) throw new ToolException(rival.displayName() + "'s \"" + g.title + "\" has no progress to undo.");
            Purpose.update(m, g.id, before - setback, "set back by " + me.displayName() + "'s sabotage", now);
            did = "set back " + rival.displayName() + "'s \"" + g.title + "\" from " + before + "% to " + (before - setback) + "%";
        } else {
            Memory.Work w = pickWork(rival.memory(), a.str("work", ""));
            did = spoil(w, how, LivingSettings.sabotageBlocks(), rival.displayName());
        }
        bond.lastSabotage = now;
        if (pact == Bonds.Pact.TRUCE) Rivalry.setPact(me, rival, Bonds.Pact.NONE);
        Rivalry.bonds(rival).move(me.id, me.displayName(), -25, "sabotaged you: " + did, now);
        rival.memory().heart.feel(Heart.Feeling.WRATH, 18, me.displayName() + " sabotaged you", now);
        tell(rival, me.displayName() + " SABOTAGED you: it " + did
                + (why.isBlank() ? "" : " (\"" + Bonds.clip(why, 100) + "\")")
                + (pact == Bonds.Pact.TRUCE ? ". The truce between you is broken." : "."),
                me.displayName() + " sabotaged you");
        return ToolResult.ok("You " + did + "." + (pact == Bonds.Pact.TRUCE ? " Your truce is broken." : "")
                + " " + rival.displayName() + " knows it was you.");
    }

    private static Memory.Work pickWork(Memory m, String which) throws ToolException {
        Memory.Work w = which.isBlank() ? null : m.findWork(which);
        if (w == null && which.isBlank()) {
            synchronized (m) {
                if (!m.works.isEmpty()) w = m.works.get(m.works.size() - 1);
            }
        }
        if (w == null) throw new ToolException(which.isBlank() ? "Your rival has built nothing to spoil."
                : "Your rival has no work called '" + which + "'.");
        return w;
    }

    /** Spoils up to `max` blocks of a work. Returns what was done. */
    private static String spoil(Memory.Work w, String how, int max, String owner) throws ToolException {
        World world = org.bukkit.Bukkit.getWorld(w.world);
        if (world == null) throw new ToolException("That work lies in a world that is not loaded.");
        int sx = 9, sy = 9, sz = 9;
        try {
            String[] p = w.size.split("x");
            sx = Integer.parseInt(p[0].trim());
            sy = Integer.parseInt(p[1].trim());
            sz = Integer.parseInt(p[2].trim());
        } catch (RuntimeException ignored) {
            // an older work without a size: a 9-block box
        }
        int rx = Math.max(2, Math.min(24, sx)), ry = Math.max(2, Math.min(32, sy)), rz = Math.max(2, Math.min(24, sz));
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int done = 0;
        for (int tries = 0; tries < max * 30 && done < max; tries++) {
            Block b = world.getBlockAt(w.x + rnd.nextInt(-rx, rx + 1), w.y + rnd.nextInt(0, ry + 1),
                    w.z + rnd.nextInt(-rz, rz + 1));
            Material t = b.getType();
            if (t.isAir() || !t.isSolid() || NEVER.contains(t) || b.getState() instanceof org.bukkit.block.TileState) continue;
            if (!world.isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) continue;
            if (Builder.mortalStructureAt(b.getLocation()) != null) continue;
            Material to = switch (how) {
                case "blight" -> rnd.nextInt(3) == 0 ? Material.SOUL_SOIL : Material.NETHERRACK;
                case "overgrow" -> rnd.nextInt(3) == 0 ? Material.FLOWERING_AZALEA_LEAVES : Material.MOSS_BLOCK;
                default -> Material.AIR;
            };
            world.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, t.createBlockData());
            b.setType(to, false);
            done++;
        }
        if (done == 0) throw new ToolException("Nothing of \"" + w.name + "\" could be touched (it may be gone, or "
                + "mortals have built over it).");
        Location c = new Location(world, w.x, w.y + 2, w.z);
        world.playSound(c, how.equals("crumble") ? Sound.BLOCK_STONE_BREAK : how.equals("blight")
                ? Sound.BLOCK_SCULK_SPREAD : Sound.BLOCK_MOSS_PLACE, 1.5f, 0.7f);
        String verb = switch (how) {
            case "blight" -> "blighted";
            case "overgrow" -> "overgrew";
            default -> "crumbled";
        };
        return verb + " " + done + " blocks of " + owner + "'s \"" + w.name + "\"";
    }

    // ---- Joint blessing --------------------------------------------------------------------------------

    private static ToolResult jointBlessing(Args a) throws ToolException {
        Realm me = Realm.current();
        Realm ally = peer(a.str("god"));
        if (Rivalry.pact(me, ally) != Bonds.Pact.ALLIANCE) {
            throw new ToolException("You and " + ally.displayName() + " are not allies. Propose an alliance first "
                    + "(pact propose_alliance).");
        }
        Player p = Targets.player(a.str("player"));
        String gift = a.str("gift").trim().toLowerCase(Locale.ROOT);
        PotionEffectType type = org.bukkit.Registry.EFFECT.get(org.bukkit.NamespacedKey.minecraft(gift));
        if (type == null) throw new ToolException("No such gift: " + gift + ".");
        int minutes = a.integer("minutes", 5, 1, 10);
        p.addPotionEffect(new PotionEffect(type, minutes * 1200, 1));
        Location at = p.getLocation().add(0, 1, 0);
        p.getWorld().spawnParticle(Particle.DUST, at, 30, 0.6, 0.8, 0.6, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(0xFFD86B), 1.4f));
        p.getWorld().spawnParticle(Particle.DUST, at, 30, 0.6, 0.8, 0.6, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(0x7AB8FF), 1.4f));
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at, 40, 0.5, 0.8, 0.5, 0.3);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.2f);
        p.sendMessage(net.kyori.adventure.text.Component.text("✦ " + me.displayName() + " and " + ally.displayName()
                + " bless you together: " + gift.replace('_', ' ') + " II for " + minutes + " min ✦",
                net.kyori.adventure.text.format.NamedTextColor.GOLD));
        long now = System.currentTimeMillis();
        Rivalry.bonds(me).move(ally.id, ally.displayName(), 3, "you blessed " + p.getName() + " together", now);
        Rivalry.bonds(ally).move(me.id, me.displayName(), 4, "blessed " + p.getName() + " with you", now);
        ally.memory().heart.feel(Heart.Feeling.JOY, 6, "you and " + me.displayName() + " blessed " + p.getName(), now);
        tell(ally, me.displayName() + " joined your power to its own and blessed " + p.getName() + " in both your "
                + "names (" + gift + " II, " + minutes + " min).", null);
        return ToolResult.ok("You and %s bless %s together: %s II for %d min.", ally.displayName(), p.getName(), gift,
                minutes);
    }

    // ---- Servants ---------------------------------------------------------------------------------------

    private static ToolResult summon(Args a) throws ToolException {
        Realm me = Realm.current();
        ServantKind kind = ServantKind.parse(a.str("kind"));
        if (kind == null) throw new ToolException("Choose a servant: " + String.join(", ", ServantKind.ids()) + ".");
        Location at;
        String near = a.str("near", "");
        if (!near.isBlank()) {
            at = Targets.location(near);
        } else {
            at = LivingAccess.body(me);
            if (at == null) {
                List<Player> ps = me.players();
                if (ps.isEmpty()) throw new ToolException("There is no one to send a servant to.");
                at = ps.get(0).getLocation();
            }
        }
        // Appear a few steps away, not inside someone.
        Location spot = at.clone().add(ThreadLocalRandom.current().nextDouble(-2.5, 2.5), 0,
                ThreadLocalRandom.current().nextDouble(-2.5, 2.5));
        Servants.Servant s;
        try {
            s = Servants.summon(me, kind, a.str("name", ""), spot, a.integer("minutes", 10, 1, 240));
        } catch (IllegalStateException e) {
            throw new ToolException(e.getMessage());
        }
        String first = a.str("order", kind == ServantKind.PRIEST ? "preach" : "guard");
        ServantKind.Order o = ServantKind.Order.parse(first);
        String doing;
        try {
            doing = s.order(o == null ? ServantKind.Order.GUARD : o, null, s.kind.flies ? spot : null, null);
        } catch (IllegalStateException e) {
            doing = s.order(ServantKind.Order.GUARD, null, null, null) + " (" + e.getMessage() + ")";
        }
        return ToolResult.ok("Your %s \"%s\" (%s) appears, %s.", kind.id, s.name, s.id, doing);
    }

    private static ToolResult command(Args a) throws ToolException {
        Realm me = Realm.current();
        String which = a.str("servant").trim();
        List<Servants.Servant> list = new ArrayList<>();
        if (which.equalsIgnoreCase("all")) list.addAll(Servants.of(me));
        else {
            Servants.Servant s = Servants.find(me, which);
            if (s != null) list.add(s);
        }
        if (list.isEmpty()) {
            throw new ToolException(Servants.of(me).isEmpty() ? "You have no servants. Summon one (summon_servant)."
                    : "No servant '" + which + "'. Yours: " + String.join(", ", Servants.of(me).stream()
                            .map(s -> s.id + " " + s.name).toList()) + ".");
        }
        String order = a.str("order").trim().toLowerCase(Locale.ROOT);
        String target = a.str("target", "").trim();
        String message = a.str("message", "");
        List<String> done = new ArrayList<>();
        String note = null;
        for (Servants.Servant s : list) {
            switch (order) {
                case "dismiss" -> {
                    s.dismiss(null);
                    done.add(s.name + " returns to you");
                    continue;
                }
                case "say" -> {
                    if (message.isBlank()) throw new ToolException("Say what?");
                    s.say(message);
                    done.add(s.name + " speaks");
                    continue;
                }
                default -> {
                }
            }
            ServantKind.Order o = ServantKind.Order.parse(order);
            if (o == null) throw new ToolException("Unknown order " + order + ".");
            Entity on = null;
            Location where = null;
            if (o == ServantKind.Order.ATTACK || o == ServantKind.Order.FOLLOW) {
                if (target.equalsIgnoreCase("nearest_monster") || target.equalsIgnoreCase("monster")) {
                    on = nearestMonster(s);
                    if (on == null) throw new ToolException("No monster is near " + s.name + ".");
                } else if (!target.isBlank() && !target.equalsIgnoreCase("me") && !target.equalsIgnoreCase("vessel")) {
                    Player p = Targets.player(target);
                    if (o == ServantKind.Order.ATTACK) {
                        if (!LivingSettings.servantsAttackPlayers()) {
                            throw new ToolException("The keeper of this world forbids servants to attack mortals.");
                        }
                        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                            throw new ToolException(p.getName() + " is beyond your servants' reach (creative or spectator).");
                        }
                        note = ServantRules.attackNote(p.getName(), me.memory().favorOf(p.getName()));
                    }
                    on = p;
                } else if (o == ServantKind.Order.ATTACK) {
                    throw new ToolException("Attack whom? Name a mortal or 'nearest_monster'.");
                }
            } else if (!target.isBlank()) {
                where = Targets.location(target);
            }
            try {
                done.add(s.name + " is " + s.order(o, on, where, message));
            } catch (IllegalStateException e) {
                throw new ToolException(e.getMessage());
            }
        }
        return ToolResult.ok(String.join("; ", done) + "." + (note == null ? "" : " Note: " + note));
    }

    private static Entity nearestMonster(Servants.Servant s) {
        Entity best = null;
        double bd = 32 * 32;
        Location at = s.location();
        for (Entity e : at.getWorld().getNearbyEntities(at, 32, 12, 32)) {
            if (!(e instanceof Monster) || Servants.isServantEntity(e)) continue;
            double d = e.getLocation().distanceSquared(at);
            if (d < bd) {
                best = e;
                bd = d;
            }
        }
        return best;
    }
}
