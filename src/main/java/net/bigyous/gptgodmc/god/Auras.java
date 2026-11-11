package net.bigyous.gptgodmc.god;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * Lasting auras: a healing ring with rising crosses, a holy halo, flames, frost, shadow, stars, a
 * storm or blooming nature round a mortal or the god's body, for a minute or two. Each is a looping
 * {@link Rites} cycle played again while it lasts. Server thread only.
 */
public final class Auras {
    private Auras() {
    }

    public static final List<String> KINDS = List.of("healing", "holy", "flame", "frost", "shadow", "stars", "storm",
            "nature");
    public static final int MAX_AURAS = 8;
    /** One cycle of an aura rite: 20 frames of 2 ticks. */
    private static final long CYCLE_MS = 2000;

    private record Aura(Rites.Kind kind, Supplier<Location> where, Entity anchor, long until) {
    }

    private static final Map<String, Aura> AURAS = new LinkedHashMap<>();
    private static final Map<String, Long> STARTED = new LinkedHashMap<>();
    private static BukkitTask task;

    public static Rites.Kind kind(String name) {
        if (name == null) return null;
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (!KINDS.contains(n)) return null;
        return Rites.Kind.valueOf("AURA_" + n.toUpperCase(Locale.ROOT));
    }

    /** Starts (or replaces) the aura on this key; returns false when too many are already showing. */
    public static boolean start(String key, Rites.Kind kind, Supplier<Location> where, Entity anchor, int seconds) {
        if (!AURAS.containsKey(key) && AURAS.size() >= MAX_AURAS) return false;
        AURAS.put(key, new Aura(kind, where, anchor, System.currentTimeMillis() + seconds * 1000L));
        STARTED.remove(key);
        if (task == null) task = GPTGOD.get().getServer().getScheduler().runTaskTimer(GPTGOD.get(), Auras::tick, 1, 10);
        tick();
        return true;
    }

    public static boolean stop(String key) {
        STARTED.remove(key);
        return AURAS.remove(key) != null;
    }

    public static int stopAll() {
        int n = AURAS.size();
        AURAS.clear();
        STARTED.clear();
        return n;
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Aura>> it = AURAS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Aura> e = it.next();
            Aura a = e.getValue();
            Location at;
            try {
                at = a.where().get();
            } catch (RuntimeException ex) {
                at = null;
            }
            boolean gone = a.anchor() != null && (!a.anchor().isValid() || a.anchor().isDead());
            if (now >= a.until() || at == null || at.getWorld() == null || gone) {
                it.remove();
                STARTED.remove(e.getKey());
                continue;
            }
            Long last = STARTED.get(e.getKey());
            if (last == null || now - last >= CYCLE_MS) {
                STARTED.put(e.getKey(), now);
                Rites.play(a.kind(), at, a.anchor(), null);
            }
        }
        if (AURAS.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }
}
