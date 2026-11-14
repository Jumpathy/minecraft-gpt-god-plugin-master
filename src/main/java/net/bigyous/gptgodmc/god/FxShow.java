package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.gson.JsonArray;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.client.ModLink;
import net.bigyous.gptgodmc.client.ModSessions;

/**
 * Shows a scene to the mortals near it: players with the client mod get the scene itself and render it
 * in full; everyone else is shown a plain sketch of it in dust particles and hears its sounds. Server
 * thread only.
 */
public final class FxShow {
    private FxShow() {
    }

    public static final int MAX_RUNNING = 8;
    public static final double RANGE = 96;
    /** Dust particles a plain viewer is sent per step (every 2 ticks). */
    static final int PLAIN_BUDGET = 140;

    /** Who saw what. */
    public record Seen(int full, int plain) {
    }

    private static final Map<String, List<BukkitRunnable>> RUNNING = new HashMap<>();

    public static int running(Realm r) {
        List<BukkitRunnable> l = RUNNING.get(r.id);
        return l == null ? 0 : l.size();
    }

    /**
     * @param id     a handle (for stopping or looping), or null
     * @param follow an entity the scene stays on, or null
     * @param only   show it to this one mortal (a private vision), or null for all nearby
     */
    public static Seen play(Realm realm, String id, Location at, Entity follow, double scale, boolean loop, JsonArray layers,
            Player only) {
        List<Player> full = new ArrayList<>();
        List<Player> plain = new ArrayList<>();
        double range = RANGE * Math.max(1, scale);
        for (Player p : at.getWorld().getPlayers()) {
            if (only != null && !p.equals(only)) continue;
            if (p.getLocation().distanceSquared(at) > range * range) continue;
            (ModLink.has(p, ModSessions.FX) ? full : plain).add(p);
        }
        int length = FxScenes.length(layers);
        if (!full.isEmpty()) {
            // Painted textures the scene uses go out first, to the viewers whose mod has not got them yet.
            var message = FxScenes.message(id, new double[] { at.getX(), at.getY(), at.getZ() },
                    follow == null ? -1 : follow.getEntityId(), scale, loop,
                    FxPaint.bind(realm, FxTriggers.arm(realm, layers, full, length, loop), full));
            for (Player p : full) ModLink.send(p, ModSessions.FX, message);
        }
        // Triggers are watched by a mod player's own game; for the others, from here.
        FxTriggers.Plain triggers = FxTriggers.Plain.of(realm, layers, plain);
        List<FxScenes.Cue> cues = FxScenes.cues(layers);
        Random rnd = new Random();
        String handle = id == null ? "" : id;
        // Blocks that look like other blocks for a while (reskin layers): shown and put right from here.
        FxReskinShow reskins = FxReskinShow.of(layers, full, plain);
        // Seals are flat surfaces in the world itself: every mortal sees the same ones, mod or no mod.
        FxSealShow seals = FxSealShow.of(layers, at, follow, scale, only, loop && id != null && !id.isBlank());
        BukkitRunnable task = new BukkitRunnable() {
            int tick;

            @Override
            public synchronized void cancel() throws IllegalStateException {
                if (reskins != null) reskins.end();
                if (seals != null) seals.end();
                super.cancel();
            }

            @Override
            public void run() {
                Location here = follow != null && follow.isValid() ? follow.getLocation() : at;
                if (reskins != null) reskins.tick(tick, here, scale);
                if (seals != null) seals.tick();
                if (triggers != null) triggers.tick(tick, 2, here, scale);
                for (FxScenes.Cue c : cues) {
                    if (c.tick() != tick && c.tick() != tick + 1) continue;
                    Sound sound = sound(c.key());
                    if (sound == null) continue;
                    for (Player p : plain) {
                        if (p.isOnline()) p.playSound(here, sound, c.volume(), c.pitch());
                    }
                }
                if (!plain.isEmpty()) {
                    for (FxScenes.Dot d : FxScenes.plain(layers, tick, scale, rnd, PLAIN_BUDGET)) {
                        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(d.rgb()), d.size());
                        for (Player p : plain) {
                            if (p.isOnline() && p.getWorld().equals(here.getWorld())) {
                                p.spawnParticle(Particle.DUST, here.getX() + d.x(), here.getY() + d.y(), here.getZ() + d.z(), 1,
                                        0, 0, 0, 0, dust, true);
                            }
                        }
                    }
                }
                tick += 2;
                if (tick > length) {
                    if (loop && !handle.isEmpty()) {
                        tick = 0;
                    } else {
                        finish();
                    }
                }
            }

            void finish() {
                cancel();
                TAGS.remove(this);
                List<BukkitRunnable> l = RUNNING.get(realm.id);
                if (l != null) l.remove(this);
            }
        };
        if (!handle.isEmpty()) stop(realm, handle, false);
        List<BukkitRunnable> list = RUNNING.computeIfAbsent(realm.id, k -> new ArrayList<>());
        TAGS.put(task, new Tag(handle, at.clone(), full));
        list.add(task);
        task.runTaskTimer(GPTGOD.get(), 0L, 2L);
        return new Seen(full.size(), plain.size());
    }

    private record Tag(String id, Location at, List<Player> full) {
    }

    private static final Map<BukkitRunnable, Tag> TAGS = new java.util.IdentityHashMap<>();

    /** Stops the scene with this handle (null or "all" = every scene of this god). Returns how many. */
    public static int stop(Realm realm, String id, boolean tellClients) {
        List<BukkitRunnable> list = RUNNING.get(realm.id);
        if (list == null) return 0;
        boolean all = id == null || id.isBlank() || id.equalsIgnoreCase("all");
        int n = 0;
        for (BukkitRunnable task : new ArrayList<>(list)) {
            Tag tag = TAGS.get(task);
            if (tag == null || !(all || tag.id().equals(id))) continue;
            task.cancel();
            list.remove(task);
            TAGS.remove(task);
            n++;
            if (tellClients || all) {
                for (Player p : tag.full()) {
                    if (p.isOnline()) ModLink.send(p, ModSessions.FX, FxScenes.stop(tag.id()));
                }
            }
        }
        TAGS.keySet().removeIf(t -> t.isCancelled());
        return n;
    }

    public static void stopAll() {
        for (List<BukkitRunnable> l : RUNNING.values()) l.forEach(BukkitRunnable::cancel);
        RUNNING.clear();
        TAGS.clear();
    }

    private static Sound sound(String key) {
        try {
            NamespacedKey k = NamespacedKey.fromString(key);
            return k == null ? null : Registry.SOUNDS.get(k);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
