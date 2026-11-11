package net.bigyous.gptgodmc.god.living;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.bigyous.gptgodmc.god.Godslayer;
import net.bigyous.gptgodmc.god.Heart;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Spectacle;

/**
 * Applies {@link WorldMood} every ten seconds: for each world, the strongest feeling among its living gods
 * shapes it. Weather changes at most every ten minutes, time drifts a little, darkness pulses are rare and
 * brief, and only a few mobs and crops are touched near mortals. Server thread only.
 */
public final class WorldMoodRunner {
    static final long PERIOD_MS = 10_000;

    private static final Map<String, Long> LAST_WEATHER = new HashMap<>();
    private static final Map<UUID, Long> LAST_DARK = new HashMap<>();
    /** What each god's mood is doing now (for its perception). */
    private static final Map<String, WorldMood.Effects> NOW = new HashMap<>();

    static final Set<Material> CROPS = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.MELON_STEM, Material.PUMPKIN_STEM, Material.SWEET_BERRY_BUSH, Material.COCOA,
            Material.TORCHFLOWER_CROP, Material.PITCHER_CROP);

    private WorldMoodRunner() {
    }

    /** What the god's mood is doing to the world, in one line ("" when nothing). */
    public static String describe(Realm r) {
        WorldMood.Effects e = NOW.get(r.id);
        return e == null ? "" : e.describe();
    }

    static void tick() {
        NOW.clear();
        if (!LivingSettings.worldMoodEnabled()) return;
        double strength = WorldMood.strength(LivingSettings.worldMoodStrength());
        if (strength <= 0) return;
        long now = System.currentTimeMillis();
        for (Realm host : Realm.all()) {
            if (!host.isHost() || !net.bigyous.gptgodmc.god.LivingAccess.awake(host)) continue;
            // The strongest living feeling among the gods of this world wins.
            Realm best = null;
            Heart.Feeling feeling = null;
            double level = 0;
            for (Realm g : host.livingDomainGods()) {
                if (net.bigyous.gptgodmc.god.LivingAccess.paused(g) || g.dormant() || Godslayer.isSlain(g)) continue;
                Heart.Feeling f = g.memory().heart.dominant(now);
                if (f == null) continue;
                double l = g.memory().heart.level(f);
                if (l > level) {
                    best = g;
                    feeling = f;
                    level = l;
                }
            }
            if (best == null) continue;
            for (World w : host.worldList()) {
                List<Player> mortals = near(best, w);
                if (mortals.isEmpty()) continue;
                boolean night = w.getEnvironment() == World.Environment.NORMAL && isNight(w);
                WorldMood.Effects e = WorldMood.of(feeling, level, strength, night);
                if (e.calm()) continue;
                if (w.equals(host.world())) NOW.put(best.id, e);
                apply(w, e, mortals, now);
            }
        }
        LAST_DARK.entrySet().removeIf(x -> now - x.getValue() > 10 * 60_000L);
    }

    static boolean isNight(World w) {
        long t = w.getTime() % 24000;
        return t >= 13000 && t <= 23000;
    }

    /** The mortals the god's mood reaches in a world: near its body if it has one there, else all of them. */
    private static List<Player> near(Realm god, World w) {
        List<Player> out = new ArrayList<>();
        Location at = net.bigyous.gptgodmc.god.LivingAccess.body(god);
        double r = LivingSettings.worldMoodRadius();
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (at != null && at.getWorld().equals(w) && p.getLocation().distanceSquared(at) > r * r) continue;
            out.add(p);
        }
        return out;
    }

    private static void apply(World w, WorldMood.Effects e, List<Player> mortals, long now) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        boolean overworld = w.getEnvironment() == World.Environment.NORMAL;
        // Weather.
        if (overworld && LivingSettings.worldMoodWeather() && e.weather() != WorldMood.Weather.KEEP
                && Boolean.TRUE.equals(w.getGameRuleValue(GameRules.ADVANCE_WEATHER))
                && WorldMood.weatherDue(LAST_WEATHER.getOrDefault(w.getName(), 0L), now)) {
            boolean changed = switch (e.weather()) {
                case STORM -> {
                    if (w.isThundering()) yield false;
                    w.setStorm(true);
                    w.setThundering(true);
                    w.setWeatherDuration(6000);
                    w.setThunderDuration(6000);
                    yield true;
                }
                case RAIN -> {
                    if (w.hasStorm()) yield false;
                    w.setStorm(true);
                    w.setWeatherDuration(6000);
                    yield true;
                }
                case CLEAR -> {
                    if (!w.hasStorm()) yield false;
                    w.setStorm(false);
                    w.setThundering(false);
                    w.setClearWeatherDuration(6000);
                    yield true;
                }
                default -> false;
            };
            if (changed) LAST_WEATHER.put(w.getName(), now);
        }
        // Time: the day hurries or lingers a little.
        if (overworld && LivingSettings.worldMoodTime() && e.timeDrift() != 0
                && Boolean.TRUE.equals(w.getGameRuleValue(GameRules.ADVANCE_TIME))) {
            int d = WorldMood.drift(e.timeDrift(), PERIOD_MS / 60_000.0);
            if (d != 0) w.setFullTime(Math.max(0, w.getFullTime() + d));
        }
        for (Player p : mortals) {
            boolean exposed = p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
            // Darkness: a brief pulse, now and then.
            if (e.darkness() && exposed && now - LAST_DARK.getOrDefault(p.getUniqueId(), 0L) >= WorldMood.DARKNESS_GAP_MS
                    && rnd.nextDouble() < 0.5) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 70, 0, true, false, false));
                LAST_DARK.put(p.getUniqueId(), now);
            }
            if (e.boldMobs() && exposed && LivingSettings.worldMoodMobs()) emboldenNear(p);
            if (e.cropTicks() > 0 && LivingSettings.worldMoodCrops()) growNear(p, e.cropTicks(), rnd);
            if (e.ambient() != null && LivingSettings.worldMoodAmbient() && rnd.nextDouble() < 0.35) ambient(p, e.ambient(), rnd);
        }
    }

    private static void emboldenNear(Player p) {
        int n = 0;
        for (Entity e : p.getNearbyEntities(20, 8, 20)) {
            if (!(e instanceof Monster m) || n >= 5) continue;
            n++;
            m.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 240, 0, true, false, false));
            if (m.getTarget() == null && m.hasLineOfSight(p)) m.setTarget(p);
        }
    }

    private static void growNear(Player p, int perMinute, ThreadLocalRandom rnd) {
        // Per ten seconds: a sixth of the per-minute growth, rounded at random.
        double want = perMinute / 6.0;
        int steps = (int) want + (rnd.nextDouble() < want - (int) want ? 1 : 0);
        Location at = p.getLocation();
        World w = p.getWorld();
        for (int tries = 0; tries < 48 && steps > 0; tries++) {
            Block b = w.getBlockAt(at.getBlockX() + rnd.nextInt(-12, 13), at.getBlockY() + rnd.nextInt(-3, 4),
                    at.getBlockZ() + rnd.nextInt(-12, 13));
            if (!CROPS.contains(b.getType()) || !(b.getBlockData() instanceof Ageable age)) continue;
            if (age.getAge() >= age.getMaximumAge()) continue;
            age.setAge(age.getAge() + 1);
            b.setBlockData(age);
            w.spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.6, 0.5), 4, 0.25, 0.2, 0.25, 0);
            steps--;
        }
    }

    private static void ambient(Player p, WorldMood.Ambient a, ThreadLocalRandom rnd) {
        Particle particle = Spectacle.particle(a.particle());
        Location at = p.getLocation().add(rnd.nextDouble(-4, 4), 1.5 + rnd.nextDouble(2), rnd.nextDouble(-4, 4));
        if (particle != null && particle.getDataType() == Void.class) {
            p.spawnParticle(particle, at, 10, 2, 1, 2, 0.01);
        }
        if (rnd.nextDouble() < 0.4) p.playSound(at, a.sound(), 0.18f, a.pitch());
    }
}
