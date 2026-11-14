package net.bigyous.gptgodmc.god;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.client.ModFx;
import net.bigyous.gptgodmc.client.ModMessages.Screen;
import net.bigyous.gptgodmc.commands.GodCommand;

/**
 * Calling a slain god back from the God Manager screen: a tear in the air opens a few blocks in front of
 * the operator, widens for a few seconds to a rising hum, and bursts; at that moment the god is revived
 * (the same {@link Godslayer#revive} as /god revive) and, where bodies are allowed, its vessel steps out
 * of the place the rift was. Particles and sounds only: nothing in the world changes. Server thread.
 */
public final class ReviveRite {
    private ReviveRite() {
    }

    /** Ticks from the first tear to the burst. */
    static final int OPEN_TICKS = 56;
    /** Ticks the rift lingers, closing, after the burst. */
    static final int CLOSE_TICKS = 24;
    static final double HEIGHT = 3.6;
    private static final Set<String> RUNNING = ConcurrentHashMap.newKeySet();

    /**
     * How wide the rift is (blocks, edge to edge at its middle) at a tick: it creeps open, gapes at the
     * burst, then closes. Pure.
     */
    static double width(int tick) {
        if (tick < 0) return 0;
        if (tick <= OPEN_TICKS) {
            double t = tick / (double) OPEN_TICKS;
            return 0.15 + 1.25 * t * t;
        }
        double t = Math.min(1, (tick - OPEN_TICKS) / (double) CLOSE_TICKS);
        return 1.4 * (1 - t) * (1 - t);
    }

    /** Half the rift's width at a height (0 at its tips, widest in the middle), for a rift this wide. Pure. */
    static double half(double y, double width) {
        if (y <= 0 || y >= HEIGHT) return 0;
        return 0.5 * width * Math.sin(Math.PI * y / HEIGHT);
    }

    /** Starts the rite for a slain god. Returns what to tell the operator. */
    public static String begin(Realm realm, Player by) {
        String god = realm.displayName();
        if (!Godslayer.isSlain(realm)) return god + " is not dead.";
        if (!RUNNING.add(realm.id)) return "The veil is already tearing for " + god + ".";
        // Four blocks ahead of the operator, on their level, facing them.
        Vector look = by.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1e-6) look = new Vector(0, 0, 1);
        look.normalize();
        Location base = by.getLocation().add(look.clone().multiply(4)).add(0, 0.2, 0);
        Vector side = new Vector(-look.getZ(), 0, look.getX());
        World world = base.getWorld();
        String name = by.getName();
        world.playSound(base, Sound.BLOCK_PORTAL_TRIGGER, SoundCategory.MASTER, 0.9f, 0.5f);
        world.playSound(base, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.MASTER, 1.2f, 0.5f);

        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                try {
                    if (tick <= OPEN_TICKS + CLOSE_TICKS) draw(world, base, side, tick);
                    if (tick < OPEN_TICKS && tick % 12 == 0 && tick > 0) {
                        // A rising charge, one step higher each time.
                        world.playSound(base, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.MASTER, 1.2f,
                                0.5f + tick / (float) OPEN_TICKS * 1.1f);
                        world.playSound(base, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.MASTER, 1.5f, 0.6f + tick / 80f);
                    }
                    if (tick == OPEN_TICKS) burst(world, base, realm, name);
                    if (tick >= OPEN_TICKS + CLOSE_TICKS) {
                        RUNNING.remove(realm.id);
                        cancel();
                    }
                    tick += 2;
                } catch (RuntimeException e) {
                    RUNNING.remove(realm.id);
                    cancel();
                    GPTGOD.LOGGER.warn("The revive rite failed: " + e);
                }
            }
        }.runTaskTimer(GPTGOD.get(), 0L, 2L);
        return "The veil tears open before you: " + god + " is being called back.";
    }

    /** One frame of the rift: bright edges, a dark violet heart, motes drawn in and sparks thrown out. */
    private static void draw(World world, Location base, Vector side, int tick) {
        double w = width(tick);
        if (w <= 0.01) return;
        boolean open = tick >= OPEN_TICKS;
        Particle.DustOptions edge = new Particle.DustOptions(Color.fromRGB(0xE9D5FF), 1.1f);
        Particle.DustOptions heart = new Particle.DustOptions(Color.fromRGB(0x3B0A66), 1.6f);
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        for (double y = 0.1; y < HEIGHT; y += 0.22) {
            // A jagged tear: the edges shiver a little every frame.
            double h = half(y, w) + (rnd.nextDouble() - 0.5) * 0.08;
            for (int sign = -1; sign <= 1; sign += 2) {
                Location at = base.clone().add(side.clone().multiply(sign * h)).add(0, y, 0);
                world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, edge);
            }
            if (h > 0.12 && rnd.nextInt(3) == 0) {
                double inside = (rnd.nextDouble() * 2 - 1) * h * 0.7;
                world.spawnParticle(Particle.DUST, base.clone().add(side.clone().multiply(inside)).add(0, y, 0), 1, 0, 0,
                        0, 0, heart);
            }
        }
        // Motes streaming into the tear from all around.
        Location mid = base.clone().add(0, HEIGHT / 2, 0);
        world.spawnParticle(Particle.REVERSE_PORTAL, mid, open ? 30 : 6 + tick / 6, w * 0.3, HEIGHT * 0.3, w * 0.3, 0.6);
        if (tick % 4 == 0) world.spawnParticle(Particle.SCULK_SOUL, base, 2, 0.5, 0.1, 0.5, 0.03);
        if (open) world.spawnParticle(Particle.END_ROD, mid, 10, 0.2, HEIGHT * 0.3, 0.2, 0.18);
    }

    /** The rift gapes: the god is revived, the sky answers, and its body steps out. */
    private static void burst(World world, Location base, Realm realm, String operator) {
        Location mid = base.clone().add(0, HEIGHT / 2, 0);
        world.spawnParticle(Particle.END_ROD, mid, 120, 0.1, HEIGHT * 0.25, 0.1, 0.45);
        world.spawnParticle(Particle.REVERSE_PORTAL, mid, 200, 0.4, HEIGHT * 0.3, 0.4, 1.2);
        world.spawnParticle(Particle.SONIC_BOOM, mid, 1, 0, 0, 0, 0);
        world.playSound(base, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.MASTER, 1.6f, 0.6f);
        world.playSound(base, Sound.BLOCK_END_GATEWAY_SPAWN, SoundCategory.MASTER, 2.0f, 0.7f);
        world.playSound(base, Sound.ITEM_TOTEM_USE, SoundCategory.MASTER, 1.0f, 0.6f);
        world.playSound(base, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.MASTER, 3.0f, 0.5f);
        ModFx.near(base, 64, Screen.FLASH, 0.7, 14, 0xE9D5FF);
        ModFx.near(base, 64, Screen.SHAKE, 0.6, 24, 0xFFFFFF);
        ModFx.near(base, 96, Screen.PULSE, 0.8, 50, 0xB070FF);

        Godslayer.revive(realm, operator);
        // Its body steps out where the rift was (its own arrival plays there), if bodies are allowed.
        Player by = Bukkit.getPlayerExact(operator);
        if (by != null && GPTGOD.get().getConfig().getBoolean("vessel.enabled", true) && !realm.dormant()) {
            JsonObject in = new JsonObject();
            in.addProperty("target", base.getBlockX() + " " + base.getBlockY() + " " + base.getBlockZ());
            in.addProperty("mode", "teleport");
            realm.run(() -> GodCommand.runPower(by, "vessel_go", in));
        }
    }
}
