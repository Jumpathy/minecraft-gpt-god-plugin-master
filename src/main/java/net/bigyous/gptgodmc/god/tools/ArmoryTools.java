package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.art.Armory;
import net.bigyous.gptgodmc.god.art.Relics;
import net.bigyous.gptgodmc.god.art.Relics.Relic;
import net.bigyous.gptgodmc.god.art.Strikes;
import net.bigyous.gptgodmc.god.vessel.Choreo;
import net.bigyous.gptgodmc.god.vessel.Vessel;
import net.bigyous.gptgodmc.god.vessel.VesselHeld;

/**
 * The god's own relics in its own hand: its armory (relics it keeps for itself), drawing one into its vessel's
 * hand and putting it away, and striking with it. Appended last in {@link ToolRegistry}.
 */
final class ArmoryTools {
    private ArmoryTools() {
    }

    static List<GodTool> all() {
        return List.of(
                GodTool.sync("armory", () -> "Your armory: relics you keep for yourself, to draw into your body's "
                        + "right hand when you want them (a blade, a scythe, a staff) and put away again. keep = add a "
                        + "relic you made; drop = let one go; draw = take one into your hand (your body must have "
                        + "form; it stays in your hand as you move, gesture and dance, until you put it away); "
                        + "sheathe = put it away; list = what you keep and hold. Strike with what you hold using "
                        + "strike. Forge one for yourself with forge_relic (keep: true). In your armory now: "
                        + Armory.describe(Realm.current().id) + ".",
                        Schema.object()
                                .choice("action", "What to do.", true, List.of("draw", "sheathe", "keep", "drop", "list"))
                                .str("relic", "For keep, drop and draw: the relic's name or id.", false)
                                .bool("awake", "For draw: take it in its awakened form, if it has one. Default false.",
                                        false),
                        ArmoryTools::armory),
                GodTool.sync("strike", "Strike a mortal (or a creature) with the relic in your hand: your body "
                        + "closes on them, turns to face them and swings. The blow is the relic's: its damage (its "
                        + "attack_damage, else the weight of its kind), its on_hit powers (fire, frost, lightning...), "
                        + "a little harder from a bigger body, at most " + (int) (Strikes.MAX_BLOW / 2) + " hearts a "
                        + "blow. style: " + String.join(", ", Choreo.WEAPON_STYLES) + " (the reap is a scythe's), or "
                        + "the name of an animation you saved with animate that has a 'hit' cue (the moment it lands: "
                        + "write your own swings, flourishes and combinations). It is wrath, not a game: a god who "
                        + "strikes the innocent is remembered for it. You are told how each blow lands.",
                        Schema.object()
                                .str("target", "The mortal's name, or a creature's kind (the nearest one to your body).",
                                        true)
                                .str("style", "How you swing: slash, overhead, thrust, reap, whirl, or your own "
                                        + "animation with a hit cue. Default: the one for what you hold.", false)
                                .str("relic", "Draw this relic from your armory first (else what you hold).", false)
                                .integer("times", "How many blows (1-" + Strikes.MAX_TIMES + "). Default 1.", false, 1,
                                        Strikes.MAX_TIMES)
                                .choice("approach", "How your body closes the distance. Default walk.", false,
                                        List.of("walk", "fly")),
                        ArmoryTools::strike));
    }

    private static Relic relic(Args a) throws ToolException {
        if (!a.has("relic")) throw new ToolException("Name the relic.");
        Relic r = Relics.find(a.str("relic"));
        if (r == null) {
            throw new ToolException("There is no relic called '" + a.str("relic") + "'. In your armory: "
                    + Armory.describe(Realm.current().id) + ".");
        }
        return r;
    }

    private static Vessel body() throws ToolException {
        Vessel v = Realm.current().vessel();
        if (!v.isManifested()) throw new ToolException("You are formless; take form with vessel_go first.");
        if (v.inTrueForm()) throw new ToolException("Your true form has no hands to hold anything.");
        return v;
    }

    private static ToolResult armory(Args a) throws ToolException {
        Realm realm = Realm.current();
        String action = a.str("action").toLowerCase(Locale.ROOT);
        switch (action) {
            case "keep" -> {
                Relic r = relic(a);
                String no = Armory.keep(realm.id, r);
                if (no != null) throw new ToolException(no);
                return ToolResult.ok("\"%s\" is in your armory. Draw it with armory draw.", r.name());
            }
            case "drop" -> {
                Relic r = relic(a);
                VesselHeld.Weapon held = realm.vessel().held().weapon();
                if (held != null && held.id().startsWith(r.id())) sheathe(realm.vessel());
                if (!Armory.drop(realm.id, r.id())) throw new ToolException("\"" + r.name() + "\" is not in your armory.");
                return ToolResult.ok("\"%s\" has left your armory. It still exists; you may keep it again.", r.name());
            }
            case "draw" -> {
                Vessel v = body();
                Relic r = relic(a);
                return ToolResult.ok(draw(realm, v, r, a.bool("awake", false)));
            }
            case "sheathe" -> {
                Vessel v = body();
                VesselHeld.Weapon held = v.held().weapon();
                if (held == null) return ToolResult.ok("Your hands are already empty.");
                Strikes.stop(realm);
                sheathe(v);
                return ToolResult.ok("You put away %s.", held.name());
            }
            default -> {
                VesselHeld.Weapon held = realm.vessel().held().weapon();
                return ToolResult.ok("In your armory: %s. In your hand: %s.", Armory.describe(realm.id),
                        held == null ? "nothing" : held.name());
            }
        }
    }

    /** Takes a relic into the vessel's hand with a flourish (keeping it in the armory if the god made it). */
    private static String draw(Realm realm, Vessel v, Relic r, boolean awake) throws ToolException {
        if (!Armory.has(realm.id, r.id())) {
            String no = Armory.keep(realm.id, r);
            if (no != null) throw new ToolException(no);
        }
        if (awake && !r.twoFormed()) awake = false;
        v.held().hold(Armory.weapon(r, awake));
        Choreo.Anim flourish = Choreo.preset("draw_weapon");
        if (flourish != null) v.animate(flourish, 0);
        Location at = v.location();
        if (at != null) {
            at.getWorld().spawnParticle(Particle.ENCHANT, at.clone().add(0, 2, 0), 40, 0.6, 0.8, 0.6, 1);
            at.getWorld().playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.4f);
        }
        return "You draw " + r.name() + (awake ? " in its awakened form" : "") + "; it is in your right hand. Strike "
                + "with strike; put it away with armory sheathe.";
    }

    private static void sheathe(Vessel v) {
        Location at = v.location();
        v.held().hold(null);
        if (at != null) {
            at.getWorld().spawnParticle(Particle.REVERSE_PORTAL, at.clone().add(0, 1.6, 0), 40, 0.4, 0.6, 0.4, 0.05);
            at.getWorld().playSound(at, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.8f, 0.7f);
        }
    }

    private static ToolResult strike(Args a) throws ToolException {
        Realm realm = Realm.current();
        // It is a blow: those who may not smite may not strike either.
        String refused = PantheonTools.refusal("smite");
        if (refused != null) throw new ToolException(refused.replace("(smite)", "(strike)"));
        Vessel v = body();
        if (a.has("relic")) {
            Relic r = relic(a);
            VesselHeld.Weapon held = v.held().weapon();
            if (held == null || !held.id().startsWith(r.id())) draw(realm, v, r, false);
        }
        VesselHeld.Weapon held = v.held().weapon();
        if (held == null) {
            throw new ToolException("Your hand is empty. Draw a relic first (armory draw, or give strike a relic). In "
                    + "your armory: " + Armory.describe(realm.id) + ".");
        }
        String baseId = held.id().endsWith(Relics.ON) ? held.id().substring(0, held.id().length() - Relics.ON.length())
                : held.id();
        Relic relic = Relics.find(baseId);
        if (relic == null) throw new ToolException("What you held is unmade.");
        LivingEntity target = target(v, a.str("target"));
        if (target instanceof Player p && (p.getGameMode() == org.bukkit.GameMode.CREATIVE
                || p.getGameMode() == org.bukkit.GameMode.SPECTATOR)) {
            throw new ToolException(p.getName() + " is beyond harm right now (creative or spectator).");
        }
        String style = a.str("style", defaultStyle(relic, held)).toLowerCase(Locale.ROOT).trim();
        Choreo.Anim anim = Choreo.WEAPON_STYLES.contains(style) ? Choreo.preset(style) : saved(style);
        if (anim == null) {
            throw new ToolException("You know no swing called '" + style + "'. Built-in: "
                    + String.join(", ", Choreo.WEAPON_STYLES) + "; or one of your animations with a hit cue.");
        }
        if (!Choreo.strikes(anim)) {
            throw new ToolException("'" + style + "' has no hit cue, so no blow would land. Add one (e.g. 't=0.5 hit "
                    + "sound=entity.player.attack.sweep') with animate and save it.");
        }
        int times = a.integer("times", 1, 1, Strikes.MAX_TIMES);
        String how = a.str("approach", "walk").toLowerCase(Locale.ROOT);
        boolean awake = held.id().endsWith(Relics.ON);
        return ToolResult.ok(Strikes.begin(realm, new Strikes.Plan(relic, awake, target, anim, style, times, how)));
    }

    private static String defaultStyle(Relic r, VesselHeld.Weapon held) {
        String words = (r.name() + " " + r.look()).toLowerCase(Locale.ROOT);
        if (words.matches(".*\\b(scythe|sickle|reap\\w*)\\b.*")) return "reap";
        if (words.matches(".*\\b(spear|lance|pike|trident)\\b.*")) return "thrust";
        return switch (r.kind()) {
            case "blade" -> "slash";
            case "staff" -> "overhead";
            default -> "thrust";
        };
    }

    /** One of the god's own saved animations, or null. */
    private static Choreo.Anim saved(String name) {
        Memory mem = Memory.get();
        String clean = Choreo.clean(name), text;
        synchronized (mem) {
            text = mem.dances.get(clean);
        }
        return text == null ? null : Choreo.fromText(clean, text);
    }

    /** A mortal by name, or the nearest living creature of a kind to the vessel. */
    private static LivingEntity target(Vessel v, String name) throws ToolException {
        try {
            return Targets.player(name);
        } catch (ToolException notAMortal) {
            String kind = name.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "").replace(' ', '_');
            Location at = v.location();
            LivingEntity best = null;
            double bestD = Double.MAX_VALUE;
            List<Entity> near = new ArrayList<>(at.getWorld().getNearbyEntities(at, 32, 16, 32));
            for (Entity e : near) {
                if (!(e instanceof LivingEntity l) || e instanceof Player || l.isDead()) continue;
                if (!e.getType().getKey().getKey().equals(kind) && !kind.equals(e.getType().getKey().getKey() + "s")) continue;
                if (net.bigyous.gptgodmc.god.vessel.Vessel.isVesselEntity(e)) continue;
                double d = e.getLocation().distanceSquared(at);
                if (d < bestD) {
                    bestD = d;
                    best = l;
                }
            }
            if (best == null) throw notAMortal;
            return best;
        }
    }
}
