package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.vessel.Vessel;

/**
 * The god's spectacles: harmless particle art that can animate for a few seconds, falling meteors and
 * earthquakes. Particle shows share one timer and a particle budget so a busy sky cannot lag clients.
 * Server thread only.
 */
public final class Spectacle {
    public enum Shape {
        BURST, RING, SPIRAL, VORTEX, PILLAR, SPHERE, BEAM, RAIN, SHOCKWAVE, CROWN;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Particles the god may paint with, by friendly name. dataFor supplies the data a few of them need. */
    private static final Map<String, Particle> PARTICLES = new LinkedHashMap<>();

    static {
        PARTICLES.put("dust", Particle.DUST);
        PARTICLES.put("flame", Particle.FLAME);
        PARTICLES.put("soul_fire_flame", Particle.SOUL_FIRE_FLAME);
        PARTICLES.put("end_rod", Particle.END_ROD);
        PARTICLES.put("portal", Particle.PORTAL);
        PARTICLES.put("reverse_portal", Particle.REVERSE_PORTAL);
        PARTICLES.put("soul", Particle.SOUL);
        PARTICLES.put("sculk_soul", Particle.SCULK_SOUL);
        PARTICLES.put("totem", Particle.TOTEM_OF_UNDYING);
        PARTICLES.put("heart", Particle.HEART);
        PARTICLES.put("smoke", Particle.LARGE_SMOKE);
        PARTICLES.put("campfire_smoke", Particle.CAMPFIRE_COSY_SMOKE);
        PARTICLES.put("cloud", Particle.CLOUD);
        PARTICLES.put("lava", Particle.LAVA);
        PARTICLES.put("dripping_lava", Particle.DRIPPING_LAVA);
        PARTICLES.put("falling_water", Particle.FALLING_WATER);
        PARTICLES.put("snowflake", Particle.SNOWFLAKE);
        PARTICLES.put("enchant", Particle.ENCHANT);
        PARTICLES.put("witch", Particle.WITCH);
        PARTICLES.put("electric_spark", Particle.ELECTRIC_SPARK);
        PARTICLES.put("glow", Particle.GLOW);
        PARTICLES.put("cherry_leaves", Particle.CHERRY_LEAVES);
        PARTICLES.put("sonic_boom", Particle.SONIC_BOOM);
        PARTICLES.put("explosion", Particle.EXPLOSION);
        PARTICLES.put("firework", Particle.FIREWORK);
        PARTICLES.put("note", Particle.NOTE);
        PARTICLES.put("magic_crit", Particle.ENCHANTED_HIT);
        PARTICLES.put("dragon_breath", Particle.DRAGON_BREATH);
        PARTICLES.put("ash", Particle.ASH);
        PARTICLES.put("crimson_spore", Particle.CRIMSON_SPORE);
        PARTICLES.put("glow_ink", Particle.GLOW_SQUID_INK);
        PARTICLES.put("happy_villager", Particle.HAPPY_VILLAGER);
        PARTICLES.put("angry_villager", Particle.ANGRY_VILLAGER);
        PARTICLES.put("spore_blossom", Particle.FALLING_SPORE_BLOSSOM);
        PARTICLES.put("white_ash", Particle.WHITE_ASH);
    }

    private static final Map<String, Integer> NAMED_COLORS = Map.ofEntries(
            Map.entry("violet", 0xB24BF3), Map.entry("purple", 0x8A2BE2), Map.entry("red", 0xFF2A2A),
            Map.entry("crimson", 0xA00020), Map.entry("orange", 0xFF8A1C), Map.entry("gold", 0xFFD54A),
            Map.entry("yellow", 0xFFF04A), Map.entry("green", 0x3CDC5A), Map.entry("cyan", 0x4AE3FF),
            Map.entry("blue", 0x3A6BFF), Map.entry("white", 0xFFFFFF), Map.entry("black", 0x101010),
            Map.entry("pink", 0xFF7AD9));

    public static final Color VIOLET = Color.fromRGB(0xB24BF3);
    /** At most this many shows at once; the oldest fades when another starts. */
    public static final int MAX_SHOWS = 6;
    /** Particles per frame (2 ticks) shared by every running show: about 300 a tick at most. */
    static final int FRAME_BUDGET = 600;
    private static final int MAX_METEORS = 10;
    private static final int MAX_QUAKES = 2;

    private static final List<Show> SHOWS = new ArrayList<>();
    private static BukkitTask task;
    private static int meteors;
    private static int quakes;

    private Spectacle() {
    }

    // ---- Names ---------------------------------------------------------------------------------

    public static List<String> particleNames() {
        return List.copyOf(PARTICLES.keySet());
    }

    public static Particle particle(String name) {
        if (name == null) return null;
        return PARTICLES.get(name.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "").replace(' ', '_'));
    }

    public static List<String> shapeNames() {
        List<String> out = new ArrayList<>();
        for (Shape s : Shape.values()) out.add(s.id());
        return out;
    }

    public static Shape shape(String name) {
        if (name == null) return null;
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (Shape s : Shape.values()) {
            if (s.id().equals(n)) return s;
        }
        return null;
    }

    /** "#FF2200", "ff2200" or a simple color name; null when unreadable. */
    public static Color color(String text) {
        if (text == null || text.isBlank()) return null;
        String t = text.trim().toLowerCase(Locale.ROOT);
        Integer named = NAMED_COLORS.get(t);
        if (named != null) return Color.fromRGB(named);
        t = t.startsWith("#") ? t.substring(1) : t.startsWith("0x") ? t.substring(2) : t;
        if (!t.matches("[0-9a-f]{6}")) return null;
        return Color.fromRGB(Integer.parseInt(t, 16));
    }

    // ---- Particle shows ------------------------------------------------------------------------

    private static final class Show {
        final Shape shape;
        final Particle particle;
        final Object data;
        final Location base;
        final Entity anchor;
        Location from;
        final Vessel vessel; // the body a beam flows from, or null
        final int size;
        final int frames;
        int frame;

        Show(Shape shape, Particle particle, Object data, Location base, Entity anchor, Location from,
                Vessel vessel, int size, int frames) {
            this.shape = shape;
            this.particle = particle;
            this.data = data;
            this.base = base;
            this.anchor = anchor;
            this.from = from;
            this.vessel = vessel;
            this.size = size;
            this.frames = frames;
        }
    }

    /** The extra data some particles must be spawned with (a color, a strength); null for the plain ones. */
    static Object dataFor(Particle particle, Color color, int size) {
        Class<?> type = particle.getDataType();
        Color c = color == null ? VIOLET : color;
        if (type == Particle.DustOptions.class) return new Particle.DustOptions(c, size >= 8 ? 2.4f : 1.6f);
        if (type == Color.class) return c;
        if (type == Float.class) return 1f;
        return null;
    }

    /**
     * Paints a shape of particles at target. seconds = 0 draws it once; otherwise it animates and, when
     * anchored to an entity, follows it. Returns a note for the god when an older show had to end, else null.
     *
     * @param from beam origin; null means the vessel's chest if it is near, else the sky above the target
     */
    public static String play(Shape shape, Particle particle, Color color, Location target, Entity anchor,
            Location from, int size, int seconds) {
        Object data = dataFor(particle, color, size);
        Vessel vessel = null;
        if (shape == Shape.BEAM && from == null) {
            Vessel body = God.vessel();
            Location v = body.location();
            if (v != null && v.getWorld().equals(target.getWorld()) && v.distanceSquared(target) < 96 * 96) {
                from = v.add(0, 2, 0);
                vessel = body;
            } else {
                from = target.clone().add(0, 30, 0);
            }
        }
        Show show = new Show(shape, particle, data, target.clone(), anchor, from, vessel, size,
                Math.max(1, seconds * 10));
        sound(shape, target);
        if (seconds == 0) {
            draw(show, FRAME_BUDGET / (SHOWS.size() + 1));
            return null;
        }
        String note = null;
        if (SHOWS.size() >= MAX_SHOWS) {
            SHOWS.remove(0);
            note = "(Your oldest spectacle faded to make room.)";
        }
        SHOWS.add(show);
        draw(show, FRAME_BUDGET / SHOWS.size());
        show.frame++;
        wake();
        return note;
    }

    /** Starts the shared frame timer if it is not running (shows and {@link Rites} both use it). */
    static void wake() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(GPTGOD.get(), Spectacle::tick, 2L, 2L);
        }
    }

    private static void tick() {
        if (SHOWS.isEmpty() && !Rites.running() && !Godslayer.deathsRunning()) {
            task.cancel();
            task = null;
            return;
        }
        // Rites draw first, then a god's death; the shows share what is left (at least a quarter).
        int used = Rites.tick(FRAME_BUDGET);
        used += Godslayer.tickDeaths(Math.max(FRAME_BUDGET / 2, FRAME_BUDGET - used));
        int left = Math.max(FRAME_BUDGET / 4, FRAME_BUDGET - used);
        if (SHOWS.isEmpty()) return;
        int each = left / SHOWS.size();
        Iterator<Show> it = SHOWS.iterator();
        while (it.hasNext()) {
            Show s = it.next();
            draw(s, each);
            if (++s.frame >= s.frames) it.remove();
        }
    }

    private static void draw(Show s, int budget) {
        Location base = s.base;
        if (s.anchor != null && s.anchor.isValid() && s.anchor.getWorld().equals(base.getWorld())) {
            base = s.anchor.getLocation();
        }
        World w = base.getWorld();
        double lift = switch (s.shape) {
            case BURST, SPHERE, BEAM -> 1.0;
            case RING, SHOCKWAVE -> 0.15;
            case CROWN -> 2.15;
            default -> 0;
        };
        Location c = base.clone().add(0, lift, 0);
        Vector beam = null;
        if (s.shape == Shape.BEAM) {
            if (s.vessel != null) {
                Location v = s.vessel.location();
                if (v != null && v.getWorld().equals(w)) s.from = v.add(0, 2, 0);
            }
            beam = s.from.toVector().subtract(c.toVector());
        }
        for (Vector p : points(s.shape, s.frame, s.frames, s.size, beam, budget)) {
            double x = c.getX() + p.getX(), y = c.getY() + p.getY(), z = c.getZ() + p.getZ();
            Vector motion = motion(s.shape, p);
            if (motion == null) {
                w.spawnParticle(s.particle, x, y, z, 1, 0, 0, 0, 0, s.data);
            } else {
                // Count 0 makes the offset a direction and "extra" its speed.
                w.spawnParticle(s.particle, x, y, z, 0, motion.getX(), motion.getY(), motion.getZ(),
                        motion.length() == 0 ? 0 : 1, s.data);
            }
        }
    }

    /** How a point drifts after it appears, or null to stay put. */
    private static Vector motion(Shape shape, Vector p) {
        return switch (shape) {
            case BURST -> p.lengthSquared() < 1e-6 ? null : p.clone().normalize().multiply(0.06);
            case SHOCKWAVE -> {
                Vector flat = new Vector(p.getX(), 0, p.getZ());
                yield flat.lengthSquared() < 1e-6 ? null : flat.normalize().multiply(0.08);
            }
            case VORTEX, PILLAR -> new Vector(0, 0.04, 0);
            case RAIN -> new Vector(0, -0.35, 0);
            default -> null;
        };
    }

    /**
     * Offsets from the show's center for one frame. frame runs 0..frames-1; frames = 1 means a single
     * instant draw (fuller, since it is not repeated). Never returns more than max points.
     *
     * @param beam for BEAM, the vector from the center (the target) to where the beam starts
     */
    static List<Vector> points(Shape shape, int frame, int frames, double s, Vector beam, int max) {
        max = Math.max(4, max);
        List<Vector> out = new ArrayList<>();
        boolean instant = frames <= 1;
        double spin = frame * 0.22;
        switch (shape) {
            case BURST -> {
                double[] radii = instant ? new double[] { 0.35 * s, 0.7 * s, s }
                        : new double[] { s * ((frame % 8) + 1) / 8.0 };
                for (double r : radii) {
                    sphere(out, clamp((int) Math.ceil(12 * r * r), 10, max / radii.length), r, spin);
                }
            }
            case RING -> circle(out, s, clamp((int) Math.ceil(Math.PI * 2 * s * 3), 12, max), spin * 0.5, 0);
            case SPIRAL -> {
                double r = Math.max(0.8, s * 0.45);
                double h = Math.max(2, s * 1.2);
                double turns = Math.max(1.5, h / 2.5);
                int each = clamp((int) (h * 5), 8, max / 2);
                for (int k = 0; k < 2; k++) {
                    for (int i = 0; i < each; i++) {
                        double t = i / (double) Math.max(1, each - 1);
                        double a = spin * 2 + k * Math.PI + t * turns * Math.PI * 2;
                        out.add(new Vector(Math.cos(a) * r, t * h, Math.sin(a) * r));
                    }
                }
            }
            case VORTEX -> {
                double h = Math.max(3, s * 1.5);
                int n = clamp((int) (h * 12), 18, max);
                for (int i = 0; i < n; i++) {
                    double u = (i / (double) n + frame * 0.035) % 1.0;
                    double y = u * h;
                    double r = 0.25 + s * 0.6 * u;
                    double a = y * 1.4 - spin * 3 + (i % 3) * Math.PI * 2 / 3;
                    out.add(new Vector(Math.cos(a) * r, y, Math.sin(a) * r));
                }
            }
            case PILLAR -> {
                double h = s * 3;
                double r = 0.5 + s * 0.04;
                int n = clamp((int) (h * 5), 12, max);
                for (int i = 0; i < n; i++) {
                    double u = (i / (double) n + frame * 0.05) % 1.0;
                    double a = i * 2.39996 + spin;
                    out.add(new Vector(Math.cos(a) * r, u * h, Math.sin(a) * r));
                }
            }
            case SPHERE -> sphere(out, clamp((int) (10 * s * s), 24, max), s, spin * 0.4);
            case BEAM -> {
                Vector b = beam == null ? new Vector(0, 30, 0) : beam;
                int n = clamp((int) Math.ceil(b.length() * 4), 4, max);
                for (int i = 0; i < n; i++) {
                    out.add(b.clone().multiply(i / (double) (n - 1)));
                }
            }
            case RAIN -> {
                Random r = new Random(frame * 7919L + 17);
                int n = clamp((int) (s * s * 1.2 * (instant ? 3 : 1)), 8, max);
                double top = s + 6;
                for (int i = 0; i < n; i++) {
                    double rad = s * Math.sqrt(r.nextDouble());
                    double a = r.nextDouble() * Math.PI * 2;
                    out.add(new Vector(Math.cos(a) * rad, r.nextDouble() * top, Math.sin(a) * rad));
                }
            }
            case SHOCKWAVE -> {
                double[] radii = instant ? new double[] { s / 3, s * 2 / 3, s }
                        : new double[] { s * ((frame % 10) + 1) / 10.0 };
                for (double r : radii) {
                    circle(out, r, clamp((int) Math.ceil(Math.PI * 2 * r * 2.5), 8, max / radii.length), spin, 0);
                }
            }
            case CROWN -> {
                double r = 0.35 + 0.05 * s;
                circle(out, r, 18, spin * 0.6, 0);
                for (int k = 0; k < 5; k++) {
                    double a = spin * 0.6 + k * Math.PI * 2 / 5;
                    for (int j = 1; j <= 3; j++) {
                        out.add(new Vector(Math.cos(a) * r, j * 0.12, Math.sin(a) * r));
                    }
                }
            }
        }
        if (out.size() > max) {
            List<Vector> thin = new ArrayList<>(max);
            for (int i = 0; i < max; i++) thin.add(out.get((int) ((long) i * out.size() / max)));
            return thin;
        }
        return out;
    }

    private static void circle(List<Vector> out, double r, int n, double phase, double y) {
        for (int i = 0; i < n; i++) {
            double a = phase + i * Math.PI * 2 / n;
            out.add(new Vector(Math.cos(a) * r, y, Math.sin(a) * r));
        }
    }

    /** Evenly spread points on a sphere (Fibonacci lattice), turned by phase around the vertical. */
    private static void sphere(List<Vector> out, int n, double r, double phase) {
        for (int i = 0; i < n; i++) {
            double y = 1 - 2 * (i + 0.5) / n;
            double ring = Math.sqrt(Math.max(0, 1 - y * y));
            double a = i * 2.39996 + phase;
            out.add(new Vector(Math.cos(a) * ring * r, y * r, Math.sin(a) * ring * r));
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(Math.min(lo, hi), Math.min(hi, v));
    }

    private static void sound(Shape shape, Location at) {
        World w = at.getWorld();
        switch (shape) {
            case BURST -> w.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.5f, 0.8f);
            case RING -> w.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.2f);
            case SPIRAL -> w.playSound(at, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 2f, 0.7f);
            case VORTEX -> w.playSound(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 2f, 0.6f);
            case PILLAR -> w.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 2f, 0.8f);
            case SPHERE -> w.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.5f, 0.8f);
            case BEAM -> {
                w.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 2f, 1.6f);
                w.playSound(at, Sound.ENTITY_GUARDIAN_ATTACK, 1.5f, 0.6f);
            }
            case RAIN -> w.playSound(at, Sound.WEATHER_RAIN_ABOVE, 1.5f, 0.8f);
            case SHOCKWAVE -> {
                w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
                w.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.8f);
            }
            case CROWN -> {
                w.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2f, 1f);
                w.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
            }
        }
    }

    // ---- Trails --------------------------------------------------------------------------------

    /** Particles streaming from an entity's feet for a while (e.g. a mortal hurled through the air). */
    public static void trail(Entity e, Particle particle, int ticks) {
        new BukkitRunnable() {
            int left = ticks;

            @Override
            public void run() {
                if (!e.isValid() || (left -= 2) <= 0) {
                    cancel();
                    return;
                }
                Location l = e.getLocation();
                e.getWorld().spawnParticle(particle, l.getX(), l.getY() + 0.2, l.getZ(), 4, 0.2, 0.1, 0.2, 0.01);
            }
        }.runTaskTimer(GPTGOD.get(), 2L, 2L);
    }

    // ---- Meteors -------------------------------------------------------------------------------

    /**
     * Sends a blazing meteor from high in the sky down onto impact (about 1.8 s), trailing fire and smoke.
     * onImpact runs on the server thread when it lands. Returns false when the sky is already too full.
     */
    public static boolean meteor(Location impact, Runnable onImpact) {
        if (meteors >= MAX_METEORS) return false;
        meteors++;
        World w = impact.getWorld();
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        Location start = impact.clone().add(Math.cos(angle) * 22, 46, Math.sin(angle) * 22);
        BlockDisplay rock = w.spawn(start, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setBlock(Material.MAGMA_BLOCK.createBlockData());
            d.setBrightness(new Display.Brightness(15, 15));
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(0xFF6A00));
            d.setViewRange(4f);
            d.setTeleportDuration(2);
            d.setTransformation(spin(0));
        });
        w.playSound(impact, Sound.ENTITY_GHAST_SHOOT, 3f, 0.5f);
        w.playSound(impact, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 3f, 0.5f);
        final int frames = 18;
        Vector path = impact.toVector().subtract(start.toVector());
        new BukkitRunnable() {
            int f;

            @Override
            public void run() {
                f++;
                double t = Math.min(1, f / (double) frames);
                double eased = t * t * 0.35 + t * 0.65;
                Location p = start.clone().add(path.clone().multiply(eased));
                if (t >= 1 || !rock.isValid()) {
                    rock.remove();
                    cancel();
                    meteors--;
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, impact, 1, 0, 0, 0, 0, null, true);
                    w.spawnParticle(Particle.LAVA, impact, 30, 1.2, 0.4, 1.2, 0, null, true);
                    w.spawnParticle(Particle.FLAME, impact, 80, 0.5, 0.3, 0.5, 0.2, null, true);
                    w.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, impact, 14, 1, 0.3, 1, 0.03, null, true);
                    w.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.5f);
                    net.bigyous.gptgodmc.client.ModFx.meteorImpact(impact);
                    onImpact.run();
                    return;
                }
                rock.teleport(p);
                rock.setInterpolationDelay(0);
                rock.setInterpolationDuration(2);
                rock.setTransformation(spin(f));
                w.spawnParticle(Particle.FLAME, p, 10, 0.35, 0.35, 0.35, 0.02, null, true);
                w.spawnParticle(Particle.LARGE_SMOKE, p, 4, 0.3, 0.3, 0.3, 0.02, null, true);
                w.spawnParticle(Particle.LAVA, p, 1, 0.2, 0.2, 0.2, 0, null, true);
                if (f % 2 == 0) {
                    w.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, p, 1, 0.2, 0.2, 0.2, 0.01, null, true);
                }
                if (f % 6 == 0) {
                    w.playSound(p, Sound.ITEM_FIRECHARGE_USE, 2.5f, 0.5f);
                }
            }
        }.runTaskTimer(GPTGOD.get(), 2L, 2L);
        return true;
    }

    /** A tumbling 1.8-block rock centered on its entity. */
    private static Transformation spin(int frame) {
        Quaternionf q = new Quaternionf().rotateAxis(frame * 0.45f, new Vector3f(1, 1, 0.3f).normalize());
        Vector3f corner = q.transform(new Vector3f(-0.9f, -0.9f, -0.9f));
        return new Transformation(corner, q, new Vector3f(1.8f), new Quaternionf());
    }

    // ---- Earthquakes ---------------------------------------------------------------------------

    /**
     * Shakes the ground around center: rumbling, dust bursting from the ground blocks, mortals jostled and
     * their view shaken. Breaks nothing. Returns false when the earth is already shaking too much.
     */
    public static boolean earthquake(Location center, int radius, int seconds, boolean nausea) {
        if (quakes >= MAX_QUAKES) return false;
        quakes++;
        World w = center.getWorld();
        w.playSound(center, Sound.ENTITY_WARDEN_EMERGE, 3f, 0.6f);
        net.bigyous.gptgodmc.client.ModFx.earthquake(center, radius, seconds);
        if (nausea) {
            for (Player p : nearby(center, radius)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, (seconds + 4) * 20, 0, true, false));
            }
        }
        final int frames = seconds * 10;
        new BukkitRunnable() {
            int f;

            @Override
            public void run() {
                if (f++ >= frames) {
                    cancel();
                    quakes--;
                    return;
                }
                ThreadLocalRandom r = ThreadLocalRandom.current();
                int n = Math.min(36, radius * 2);
                for (int i = 0; i < n; i++) {
                    double d = radius * Math.sqrt(r.nextDouble());
                    double a = r.nextDouble(Math.PI * 2);
                    Block g = ground(w, center.getBlockX() + (int) Math.round(Math.cos(a) * d),
                            center.getBlockY(), center.getBlockZ() + (int) Math.round(Math.sin(a) * d));
                    if (g == null) continue;
                    w.spawnParticle(Particle.BLOCK, g.getX() + 0.5, g.getY() + 1.05, g.getZ() + 0.5, 8, 0.45,
                            0.05, 0.45, 0.1, g.getBlockData());
                    if (i == 0 && f % 2 == 0) {
                        w.spawnParticle(Particle.POOF, g.getX() + 0.5, g.getY() + 1.1, g.getZ() + 0.5, 2,
                                0.3, 0.1, 0.3, 0.02);
                        w.playSound(g.getLocation(), g.getBlockData().getSoundGroup().getBreakSound(), 0.9f, 0.6f);
                    }
                }
                if (f % 10 == 1) {
                    w.playSound(center, Sound.ENTITY_WARDEN_DIG, 2.5f, 0.5f);
                    w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.5f);
                }
                if (f % 3 == 0) {
                    for (Player p : nearby(center, radius)) {
                        double strength = 1 - 0.6 * p.getLocation().distance(center) / radius;
                        p.sendHurtAnimation((float) r.nextDouble(360));
                        if (p.isFlying() || !p.isOnGround()) continue;
                        p.setVelocity(p.getVelocity().add(new Vector(r.nextDouble(-0.14, 0.14) * strength,
                                0.08 + r.nextDouble(0.1) * strength, r.nextDouble(-0.14, 0.14) * strength)));
                        p.setFallDistance(0);
                    }
                }
            }
        }.runTaskTimer(GPTGOD.get(), 1L, 2L);
        return true;
    }

    private static List<Player> nearby(Location center, int radius) {
        List<Player> out = new ArrayList<>();
        for (Player p : center.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (p.getLocation().distanceSquared(center) <= (double) radius * radius) out.add(p);
        }
        return out;
    }

    /** The top solid block near (x, y, z), searching a few blocks up and down; null in open air. */
    private static Block ground(World w, int x, int y, int z) {
        Block b = w.getBlockAt(x, y, z);
        if (b.getType().isSolid()) {
            for (int i = 0; i < 8 && b.getRelative(0, 1, 0).getType().isSolid(); i++) b = b.getRelative(0, 1, 0);
            return b;
        }
        for (int i = 0; i < 10 && b.getY() > w.getMinHeight(); i++) {
            b = b.getRelative(0, -1, 0);
            if (b.getType().isSolid()) return b;
        }
        return null;
    }
}
