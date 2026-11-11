package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.loggables.GenericEventLoggable;

/**
 * Lasting visible marks the god sets on mortals: a ring of golden light above the head, or a dark
 * smouldering cloud. Stored (with their expiry) in the memory of the god who set them, so they survive a
 * relog. One shared timer draws every mark and stops itself when none remain. Server thread only.
 */
public final class Marks {
    public static final String HALO = "halo";
    public static final String CURSE = "curse";
    private static final long PERIOD_TICKS = 10;
    private static BukkitTask task;
    private static int step;

    private Marks() {
    }

    /** Starts the timer if anyone online carries a mark. Safe to call often. */
    public static void ensureRunning() {
        if (task != null || GPTGOD.get() == null || !GPTGOD.get().isEnabled()) return;
        if (!anyMarked()) return;
        task = Bukkit.getScheduler().runTaskTimer(GPTGOD.get(), Marks::tick, 2L, PERIOD_TICKS);
    }

    private static boolean anyMarked() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Realm realm : Realm.godsOf(p.getWorld())) {
                if (realm.memory().activeMark(p.getName(), now) != null) return true;
            }
        }
        return false;
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        step++;
        boolean any = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            // Each god's mark is its own: a mortal may bear one god's halo and another's curse.
            for (Realm realm : Realm.godsOf(p.getWorld())) {
                Memory memory = realm.memory();
                if (memory.expireMark(p.getName(), now)) {
                    String name = p.getName();
                    realm.run(() -> EventLogger.addLoggable(new GenericEventLoggable("The mark you set on " + name
                            + " has faded")));
                    continue;
                }
                String mark = memory.activeMark(p.getName(), now);
                if (mark == null) continue;
                any = true;
                if (p.getGameMode() == GameMode.SPECTATOR || p.isDead() || p.isInvisible()) continue;
                draw(p, mark);
            }
        }
        if (!any && task != null) {
            task.cancel();
            task = null;
        }
    }

    /** The points of a mark around a player whose feet are at the origin and who is `height` tall. */
    public static List<Vector> points(String kind, double height, int step) {
        double phase = step * 0.35;
        if (HALO.equals(kind)) {
            return Shapes.ring(0.32, 8, phase, height + 0.32);
        }
        // A curse: a slow, lopsided ring of smoke just above the head, sagging on one side.
        List<Vector> out = new ArrayList<>();
        for (Vector v : Shapes.ring(0.45, 6, -phase * 0.5, height + 0.2)) {
            out.add(v.setY(v.getY() + 0.12 * Math.sin(Math.atan2(v.getZ(), v.getX()) * 2 + phase)));
        }
        return out;
    }

    /** Kept apart so the geometry above can be used without a server (previews). */
    private static final class Dust {
        static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(0xFFD966), 0.7f);
        static final Particle.DustOptions GLOOM = new Particle.DustOptions(Color.fromRGB(0x2A0A33), 1.3f);
    }

    private static void draw(Player p, String mark) {
        Location feet = p.getLocation();
        World w = p.getWorld();
        double height = p.getHeight();
        for (Vector v : points(mark, height, step)) {
            Location at = feet.clone().add(v);
            if (HALO.equals(mark)) {
                w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, Dust.GOLD);
            } else {
                w.spawnParticle(Particle.DUST, at, 1, 0.03, 0.03, 0.03, 0, Dust.GLOOM);
            }
        }
        if (HALO.equals(mark)) {
            if (step % 4 == 0) {
                w.spawnParticle(Particle.END_ROD, feet.clone().add(0, height + 0.35, 0), 1, 0.2, 0.05, 0.2, 0.002);
            }
        } else if (step % 2 == 0) {
            w.spawnParticle(Particle.LARGE_SMOKE, feet.clone().add(0, height + 0.25, 0), 1, 0.2, 0.05, 0.2, 0.004);
        }
    }
}
