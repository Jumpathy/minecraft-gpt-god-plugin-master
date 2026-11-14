package net.bigyous.gptgodmc.god.art;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.Journal;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.art.Relics.Relic;
import net.bigyous.gptgodmc.god.vessel.Choreo;
import net.bigyous.gptgodmc.god.vessel.Vessel;
import net.bigyous.gptgodmc.god.vessel.VesselHeld;
import net.bigyous.gptgodmc.god.vessel.VesselModel;
import net.bigyous.gptgodmc.loggables.GenericEventLoggable;

/**
 * The god's own blows: its vessel, holding a relic from its armory, closes on a mortal (or a creature), turns
 * to face it and swings, with one of the weapon animations or one the god wrote itself with a hit moment.
 * When the blow lands, the relic does what it does on a hit (its damage, its fire or frost, its lightning),
 * scaled a little by the size of the body that swings it and never more than {@link #MAX_BLOW} a blow. The god
 * is told how it went as an event (it does not wait for it). One sequence at a time for each god. Server
 * thread.
 */
public final class Strikes {
    private Strikes() {
    }

    /** At most this many blows in one sequence. */
    public static final int MAX_TIMES = 3;
    /** The most one blow takes from its target (half-hearts). */
    public static final double MAX_BLOW = 16;
    /** How long it pursues before giving up, in ticks. */
    static final int PURSUE_TICKS = 240;
    /** Ticks it takes to turn toward its target before it swings. */
    static final int TURN_TICKS = 8;

    private static final Map<String, BukkitRunnable> RUNNING = new ConcurrentHashMap<>();

    /**
     * One sequence of blows.
     *
     * @param relic  what is held (already in the vessel's hand)
     * @param awake  in its awakened form
     * @param target whom it strikes
     * @param anim   how it swings (has a hit cue)
     * @param style  the animation's name, for the damage of its kind of blow
     * @param times  1..MAX_TIMES
     * @param how    walk or fly, when it must close the distance
     */
    public record Plan(Relic relic, boolean awake, LivingEntity target, Choreo.Anim anim, String style, int times,
            String how) {
    }

    /** How much harder (or softer) a kind of blow falls. Pure. */
    static double styleFactor(String style) {
        return switch (style) {
            case "overhead" -> 1.3;
            case "reap" -> 1.2;
            case "thrust" -> 1.1;
            case "whirl" -> 0.9;
            default -> 1.0;
        };
    }

    /** What a relic does in its god's hand before its powers: its attack stat, else the weight of its kind. Pure. */
    static double base(String kind, double stat) {
        if (!Double.isNaN(stat)) return stat;
        return switch (kind) {
            case "blade" -> 7;
            case "staff" -> 6;
            default -> 4;
        };
    }

    /** A bigger body swings harder (a lordly one, the usual, swings as written). Pure. */
    static double sizeFactor(double playerHeights) {
        return Math.max(0.8, Math.min(1.8, Math.sqrt(playerHeights / 1.5)));
    }

    /** One blow's damage: base by the kind of blow and the body, plus the relic's own, capped. Pure. */
    static double blow(double base, String style, double playerHeights, double extra) {
        return Math.min(MAX_BLOW, base * styleFactor(style) * sizeFactor(playerHeights) + extra);
    }

    /** How far a blow reaches from the vessel's feet, in blocks. */
    static double reach(Vessel v) {
        return v.held().reachPx() * VesselModel.unit(v.scale()) + 1.2;
    }

    /** True while a god is in the middle of a sequence of blows. */
    public static boolean busy(Realm realm) {
        return RUNNING.containsKey(realm.id);
    }

    /** Ends a god's sequence (it lowers its weapon). */
    public static void stop(Realm realm) {
        BukkitRunnable r = RUNNING.remove(realm.id);
        if (r != null) {
            try {
                r.cancel();
            } catch (IllegalStateException e) {
                // never scheduled
            }
        }
    }

    /** Starts a sequence; returns what the god is told now. Server thread. */
    public static String begin(Realm realm, Plan plan) {
        stop(realm);
        Vessel v = realm.vessel();
        LivingEntity target = plan.target();
        String whom = target instanceof Player p ? p.getName() : target.getType().getKey().getKey();
        BukkitRunnable run = new BukkitRunnable() {
            int left = plan.times(), waited, phaseTicks;
            /** 0 pursuing, 1 turning, 2 swinging. */
            int phase;

            @Override
            public void run() {
                if (!v.isManifested() || v.inTrueForm() || v.held().weapon() == null) {
                    finish(null);
                    return;
                }
                if (!target.isValid() || target.isDead() || target.getWorld() != v.location().getWorld()) {
                    finish(whom + " is no longer before you; you lower " + plan.relic().name() + ".");
                    return;
                }
                Location at = v.location(), there = target.getLocation();
                double dist = Math.hypot(there.getX() - at.getX(), there.getZ() - at.getZ());
                phaseTicks += 2;
                switch (phase) {
                    case 0 -> {
                        if (dist <= reach(v)) {
                            v.stay();
                            v.face(there, 4);
                            if (target instanceof Player p) v.lookAt(p);
                            phase = 1;
                            phaseTicks = 0;
                            return;
                        }
                        waited += 2;
                        if (waited > PURSUE_TICKS) {
                            v.stay();
                            finish(whom + " stayed out of the reach of " + plan.relic().name() + ".");
                            return;
                        }
                        if (waited % 20 == 2) {
                            if (target instanceof Player p && !"fly".equals(plan.how())) v.follow(p);
                            else v.goTo(there, plan.how());
                        }
                    }
                    case 1 -> {
                        v.face(there, 4);
                        if (phaseTicks >= TURN_TICKS) {
                            v.animate(plan.anim(), 0);
                            v.held().arm(hit -> land(realm, v, plan, hit), plan.anim().length() + 20);
                            phase = 2;
                            phaseTicks = 0;
                        }
                    }
                    default -> {
                        if (phaseTicks >= plan.anim().length() + 6) {
                            left--;
                            if (left <= 0) {
                                finish(null);
                                return;
                            }
                            phase = dist <= reach(v) ? 1 : 0;
                            phaseTicks = 0;
                            waited = 0;
                        }
                    }
                }
            }

            private void finish(String note) {
                cancel();
                RUNNING.remove(realm.id, this);
                if (note != null) tell(realm, note);
            }
        };
        RUNNING.put(realm.id, run);
        run.runTaskTimer(GPTGOD.get(), 1L, 2L);
        double dist = v.location().distance(target.getLocation());
        return "You raise " + plan.relic().name() + " against " + whom + " (" + plan.style() + ", " + plan.times()
                + (plan.times() == 1 ? " blow" : " blows") + ")" + (dist > reach(v) ? "; your body closes on them first" : "")
                + ". You will feel how each blow lands.";
    }

    /** A hit cue: the blow lands on the target if it is within the weapon's reach, else it cuts the air. */
    private static void land(Realm realm, Vessel v, Plan plan, VesselHeld.Hit hit) {
        LivingEntity target = plan.target();
        String whom = target instanceof Player p ? p.getName() : target.getType().getKey().getKey();
        if (!target.isValid() || target.isDead() || v.location() == null
                || target.getWorld() != v.location().getWorld()) {
            return;
        }
        Location center = target.getLocation().add(0, target.getHeight() / 2, 0);
        Location at = v.location();
        double body = Math.hypot(center.getX() - at.getX(), center.getZ() - at.getZ());
        boolean lands = body <= reach(v) + 0.5 || toSegment(center, hit.grip(), hit.point()) <= 1.2 + 0.2 * v.size();
        if (!lands) {
            target.getWorld().playSound(hit.point(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1f, 0.7f);
            tell(realm, "Your blow with " + plan.relic().name() + " cut the air: " + whom + " was out of reach.");
            return;
        }
        Relic r = Relics.find(plan.relic().id());
        if (r == null) r = plan.relic();
        double extra = RelicImbue.strikeEffects(r, plan.awake(), target, at);
        double damage = blow(base(r.kind(), RelicImbue.attackDamage(r, plan.awake())), plan.style(), v.size(), extra);
        double before = target.getHealth();
        target.setNoDamageTicks(0);
        target.damage(damage);
        Vector away = target.getLocation().toVector().subtract(at.toVector()).setY(0);
        if (away.lengthSquared() > 1e-4) {
            target.setVelocity(target.getVelocity().add(away.normalize().multiply(0.5 + 0.1 * v.size()).setY(0.3)));
        }
        target.getWorld().spawnParticle(Particle.CRIT, center, 24, 0.3, 0.5, 0.3, 0.3);
        target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, center, (int) Math.min(12, damage), 0.2, 0.3, 0.2, 0.1);
        target.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.7f);
        net.bigyous.gptgodmc.client.ModFx.wrath(center, 1);
        double taken = Math.max(0, before - Math.max(0, target.getHealth()));
        String how = target.isDead() || target.getHealth() <= 0 ? "; they fell"
                : String.format(java.util.Locale.ROOT, "; %.1f hearts left", target.getHealth() / 2);
        tell(realm, String.format(java.util.Locale.ROOT, "Your blow with %s struck %s for %.1f hearts%s.", r.name(), whom,
                taken / 2, how));
    }

    /** Distance from a point to the segment a-b. Pure. */
    static double toSegment(Location p, Location a, Location b) {
        Vector ab = b.toVector().subtract(a.toVector()), ap = p.toVector().subtract(a.toVector());
        double len = ab.lengthSquared();
        double t = len < 1e-9 ? 0 : Math.max(0, Math.min(1, ap.dot(ab) / len));
        return a.toVector().add(ab.multiply(t)).distance(p.toVector());
    }

    /** Tells the god (an event it sees in its next thought; it is not woken for it) and its journal. */
    private static void tell(Realm realm, String text) {
        realm.run(() -> {
            Journal.add(Journal.Kind.INFO, text);
            EventLogger.addLoggable(new GenericEventLoggable(text));
        });
    }
}
