package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * Choreographed particle rites: short, scripted shows (a summoning circle, a tightening spiral, a
 * burst) with sound cues and one "climax" moment at which the thing the rite is for actually happens.
 * Server thread only.
 *
 * <p>The choreography ({@link #frame}, {@link #cues}) is pure and deterministic, so the same frames
 * drive the particles in game and the offline previews. The shared {@link Spectacle} timer draws them.
 */
public final class Rites {
    /** At most this many rites at once; a rite refused for lack of room still runs its climax. */
    public static final int MAX_RITES = 16;

    private static final double TAU = Math.PI * 2;

    /** A frame is 2 ticks. The climax is the frame on which the rite's purpose takes effect. */
    public enum Kind {
        ASCENSION(44, 34), ANOINT(18, 10), RENEWAL(24, 14), RESURRECTION(32, 12), BLESSING(16, 8),
        CURSE(26, 16), JUDGEMENT(22, 18), SEAL(50, 0), ARRIVAL(34, 26), DEPARTURE(22, 2),
        // After powers: healing, gifts, summonings, building, teleporting, quests and titles.
        HEALING(26, 14), GIFT(22, 13), SUMMON(30, 22), FORGE(26, 18), BLINK(16, 6), SIGIL(20, 10),
        // Lasting auras: each loops (played again every cycle while the aura lasts).
        AURA_HEALING(20, 0), AURA_HOLY(20, 0), AURA_FLAME(20, 0), AURA_FROST(20, 0), AURA_SHADOW(20, 0),
        AURA_STARS(20, 0), AURA_STORM(20, 0), AURA_NATURE(20, 0);

        public final int frames;
        public final int climax;

        Kind(int frames, int climax) {
            this.frames = frames;
            this.climax = climax;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** A particle with its colour: what one dot of a rite is drawn with. */
    public enum Tint {
        GOLD(Particle.DUST, 0xFFC23A, 1.3f),
        SUN(Particle.DUST, 0xFFF0A0, 1.15f),
        WHITE(Particle.DUST, 0xFFFFFF, 1.0f),
        VIOLET(Particle.DUST, 0xB24BF3, 1.3f),
        INDIGO(Particle.DUST, 0x4B2BD8, 1.3f),
        CYAN(Particle.DUST, 0x4AE3FF, 1.1f),
        LIFE(Particle.DUST, 0x46F06A, 1.2f),
        ROSE(Particle.DUST, 0xFF7AC8, 1.15f),
        BLOOD(Particle.DUST, 0xD0102A, 1.35f),
        SHADOW(Particle.DUST, 0x1A0F24, 1.9f),
        SPARK(Particle.ELECTRIC_SPARK, 0xBFF4FF, 1f),
        STAR(Particle.END_ROD, 0xFFFBEA, 1f),
        EMBER(Particle.FLAME, 0xFF8A1C, 1f),
        SOUL(Particle.SOUL_FIRE_FLAME, 0x52E8F0, 1f),
        HEART(Particle.HEART, 0xFF3355, 1f),
        TOTEM(Particle.TOTEM_OF_UNDYING, 0xD8F04A, 1f),
        SMOKE(Particle.LARGE_SMOKE, 0x5A5560, 1f),
        FLASH(Particle.FLASH, 0xFFFFFF, 1f),
        SPROUT(Particle.HAPPY_VILLAGER, 0x6CFF6C, 1f),
        SNOW(Particle.SNOWFLAKE, 0xE8F6FF, 1f),
        FROST(Particle.DUST, 0xA8E4FF, 1.1f);

        public final Particle particle;
        public final int rgb;
        public final float size;
        private final Object data;

        Tint(Particle particle, int rgb, float size) {
            this.particle = particle;
            this.rgb = rgb;
            this.size = size;
            Class<?> type = particle.getDataType();
            if (type == Particle.DustOptions.class) {
                data = new Particle.DustOptions(Color.fromRGB(rgb), size);
            } else if (type == Color.class) {
                data = Color.fromRGB(rgb);
            } else if (type == Float.class) {
                data = 1f;
            } else {
                data = null;
            }
        }

        /** The data this particle is spawned with (null for plain ones). */
        public Object data() {
            return data;
        }

        public boolean isDust() {
            return particle == Particle.DUST;
        }
    }

    /**
     * One particle of a frame: an offset in blocks from the anchor's feet. fixed dots are placed
     * relative to where the rite began (ground circles stay on the ground while a mortal is lifted). A
     * dot with a velocity flies off at that many blocks per tick.
     */
    public record Dot(double x, double y, double z, Tint tint, boolean fixed, double vx, double vy, double vz) {
        public boolean moving() {
            return vx != 0 || vy != 0 || vz != 0;
        }
    }

    /** A sound played on a frame, by its key (e.g. "block.beacon.activate"). */
    public record Cue(int frame, String sound, float volume, float pitch) {
    }

    private Rites() {
    }

    public static List<String> kindNames() {
        List<String> out = new ArrayList<>();
        for (Kind k : Kind.values()) out.add(k.id());
        return out;
    }

    /** The kind with this id, or null. */
    public static Kind kind(String name) {
        if (name == null) return null;
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (Kind k : Kind.values()) {
            if (k.id().equals(n)) return k;
        }
        return null;
    }

    // ---- Running rites ------------------------------------------------------------------------

    private static final class Running {
        final Kind kind;
        final Location origin;
        final Entity anchor;
        final Runnable atClimax;
        int frame;
        boolean climaxed;

        Running(Kind kind, Location origin, Entity anchor, Runnable atClimax) {
            this.kind = kind;
            this.origin = origin;
            this.anchor = anchor;
            this.atClimax = atClimax;
        }

        void climax() {
            if (climaxed) return;
            climaxed = true;
            runSafely(atClimax);
        }
    }

    private static final List<Running> RITES = new ArrayList<>();

    /**
     * Starts a rite at a spot. When anchored to an entity it follows it. atClimax (may be null) runs
     * once, on the server thread, at the rite's climax frame; if the rite cannot be shown (too many
     * running, no world) it runs at once instead, so the effect is never lost.
     *
     * @return whether the rite is being shown
     */
    public static boolean play(Kind kind, Location at, Entity anchor, Runnable atClimax) {
        if (kind == null || at == null || at.getWorld() == null || RITES.size() >= MAX_RITES) {
            runSafely(atClimax);
            return false;
        }
        RITES.add(new Running(kind, at.clone(), anchor, atClimax));
        Spectacle.wake();
        return true;
    }

    /** Whether any rite is still playing. */
    public static boolean running() {
        return !RITES.isEmpty();
    }

    /** Draws one frame of every running rite within the particle budget; returns how many it spawned. */
    static int tick(int budget) {
        if (RITES.isEmpty()) return 0;
        // A climax may start another rite: walk a snapshot.
        List<Running> now = new ArrayList<>(RITES);
        int each = Math.max(8, budget / now.size());
        int spawned = 0;
        for (Running r : now) {
            try {
                spawned += draw(r, each);
                if (r.frame == r.kind.climax) r.climax();
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("Rite " + r.kind.id() + " failed", e);
                r.climax();
                r.frame = r.kind.frames;
            }
            r.frame++;
        }
        Iterator<Running> it = RITES.iterator();
        while (it.hasNext()) {
            Running r = it.next();
            if (r.frame >= r.kind.frames) {
                r.climax();
                it.remove();
            }
        }
        return spawned;
    }

    /** Ends every rite now, running the climaxes that have not happened yet (plugin shutdown). */
    public static void finishAll() {
        List<Running> now = new ArrayList<>(RITES);
        RITES.clear();
        for (Running r : now) r.climax();
    }

    private static void runSafely(Runnable work) {
        if (work == null) return;
        try {
            work.run();
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("A rite's climax failed", e);
        }
    }

    private static int draw(Running r, int budget) {
        Location origin = r.origin;
        World w = origin.getWorld();
        Location base = origin;
        if (r.anchor != null && r.anchor.isValid() && r.anchor.getWorld().equals(w)) {
            base = r.anchor.getLocation();
        }
        List<Dot> dots = frame(r.kind, r.frame);
        int step = dots.size() <= budget ? 1 : (int) Math.ceil(dots.size() / (double) budget);
        int spawned = 0;
        for (int i = step == 1 ? 0 : r.frame % step; i < dots.size(); i += step) {
            Dot d = dots.get(i);
            Location at = d.fixed() ? origin : base;
            double x = at.getX() + d.x(), y = at.getY() + d.y(), z = at.getZ() + d.z();
            if (d.moving()) {
                // Count 0 makes the offset a direction and "extra" its speed.
                w.spawnParticle(d.tint().particle, x, y, z, 0, d.vx(), d.vy(), d.vz(), 1.0, d.tint().data());
            } else {
                w.spawnParticle(d.tint().particle, x, y, z, 1, 0, 0, 0, 0, d.tint().data());
            }
            spawned++;
        }
        for (Cue c : cues(r.kind)) {
            if (c.frame() == r.frame) {
                w.playSound(base.clone().add(0, 1, 0), c.sound(), c.volume(), c.pitch());
            }
        }
        return spawned;
    }

    // ---- Choreography -------------------------------------------------------------------------

    /** Collects the dots of one frame. */
    private static final class Pen {
        final List<Dot> out = new ArrayList<>();

        void add(List<Vector> pts, Tint t) {
            for (Vector p : pts) out.add(new Dot(p.getX(), p.getY(), p.getZ(), t, false, 0, 0, 0));
        }

        void fixed(List<Vector> pts, Tint t) {
            for (Vector p : pts) out.add(new Dot(p.getX(), p.getY(), p.getZ(), t, true, 0, 0, 0));
        }

        void dot(double x, double y, double z, Tint t) {
            out.add(new Dot(x, y, z, t, false, 0, 0, 0));
        }

        void fly(double x, double y, double z, Tint t, double vx, double vy, double vz) {
            out.add(new Dot(x, y, z, t, false, vx, vy, vz));
        }

        /** Dots thrown out in every direction from (0, y, 0). */
        void burst(int n, double y, double speed, Tint t, double phase) {
            for (Vector d : Shapes.sphere(n, 1, phase)) {
                fly(d.getX() * 0.15, y + d.getY() * 0.15, d.getZ() * 0.15, t, d.getX() * speed, d.getY() * speed,
                        d.getZ() * speed);
            }
        }

        /** A fountain of particles thrown upward from (0, y, 0). */
        void fountain(Random rnd, int n, double y, Tint t) {
            for (int i = 0; i < n; i++) {
                double a = rnd.nextDouble() * TAU;
                double s = 0.08 + rnd.nextDouble() * 0.22;
                fly(0, y, 0, t, Math.cos(a) * s, 0.45 + rnd.nextDouble() * 0.4, Math.sin(a) * s);
            }
        }

        void halo(double y, double r, int n, double phase, Tint t) {
            add(Shapes.ring(r, n, phase, y), t);
        }
    }

    /**
     * Half of a {@link Shapes#magicCircle}, so a big circle can be drawn densely over two frames: the
     * two rings (rings true) or the figures inside them and the rune marks.
     */
    static List<Vector> circleHalf(double r, double phase, double y, double density, boolean rings) {
        List<Vector> all = Shapes.magicCircle(r, phase, y, density);
        int n = Math.max(3, (int) Math.round(TAU * r * density))
                + Math.max(3, (int) Math.round(TAU * r * 0.86 * density));
        return rings ? all.subList(0, n) : all.subList(n, all.size());
    }

    /** Points round a circle of radius r at a density per block, between min and max. */
    private static int around(double r, double density, int min, int max) {
        return Math.max(min, Math.min(max, (int) Math.round(TAU * r * density)));
    }

    /** The dots of frame f of a rite (offsets from the anchor's feet). Pure and deterministic. */
    public static List<Dot> frame(Kind kind, int f) {
        Pen p = new Pen();
        if (f < 0 || f >= kind.frames) return p.out;
        Random rnd = new Random(kind.ordinal() * 7919L + f * 104729L + 11);
        switch (kind) {
            case ASCENSION -> ascension(p, f, rnd);
            case ANOINT -> anoint(p, f, rnd);
            case RENEWAL -> renewal(p, f);
            case RESURRECTION -> resurrection(p, f, rnd);
            case BLESSING -> blessing(p, f);
            case CURSE -> curse(p, f, rnd);
            case JUDGEMENT -> judgement(p, f);
            case SEAL -> seal(p, f, rnd);
            case ARRIVAL -> arrival(p, f);
            case DEPARTURE -> departure(p, f);
            case HEALING -> healing(p, f, rnd);
            case GIFT -> gift(p, f, rnd);
            case SUMMON -> summon(p, f, rnd);
            case FORGE -> forge(p, f, rnd);
            case BLINK -> blink(p, f);
            case SIGIL -> sigil(p, f);
            case AURA_HEALING, AURA_HOLY, AURA_FLAME, AURA_FROST, AURA_SHADOW, AURA_STARS, AURA_STORM, AURA_NATURE ->
                aura(kind, p, f, rnd);
        }
        return p.out;
    }

    /** A plus sign (a healing cross) standing upright at (x, y, z), facing outward from the centre. */
    static List<Vector> plus(double x, double y, double z, double size, int perArm) {
        List<Vector> out = new ArrayList<>();
        double len = Math.hypot(x, z);
        double tx = len < 1e-6 ? 1 : -z / len, tz = len < 1e-6 ? 0 : x / len; // along the ring
        for (int i = -perArm; i <= perArm; i++) {
            double t = size * i / perArm;
            out.add(new Vector(x, y + t, z));
            if (i != 0) out.add(new Vector(x + tx * t, y, z + tz * t));
        }
        return out;
    }

    private static void healing(Pen p, int f, Random rnd) {
        // Green rings pulse outward over the ground, three at a time.
        for (int k = 0; k < 3; k++) {
            double r = 0.3 + ((f + k * 8) % 24) / 24.0 * 2.6;
            p.fixed(Shapes.ring(r, around(r, 3, 12, 48), f * 0.05 + k, 0.05), k == 1 ? Tint.SPROUT : Tint.LIFE);
        }
        // Healing crosses rise round the mortal.
        for (int i = 0; i < 4; i++) {
            double a = i * TAU / 4 + f * 0.09;
            double y = 0.3 + ((f * 0.08 + i * 0.25) % 1.0) * 2.2;
            p.add(plus(Math.cos(a) * 1.2, y, Math.sin(a) * 1.2, 0.22, 2), i % 2 == 0 ? Tint.LIFE : Tint.WHITE);
        }
        if (f == 14) {
            p.add(Shapes.ring(1.0, 8, 0, 1.6), Tint.HEART);
            p.fountain(rnd, 14, 1.0, Tint.SPROUT);
            p.burst(16, 1.0, 0.18, Tint.LIFE, 0.3);
        }
        if (f > 14) p.halo(2.2, 0.45, 12, f * 0.2, Tint.LIFE);
    }

    private static void gift(Pen p, int f, Random rnd) {
        // A beam of light comes down from the sky onto the gift.
        if (f <= 13) {
            double low = Shapes.lerp(8.5, 0.4, Shapes.ease(f / 13.0));
            for (int i = 0; i < 14; i++) {
                double y = low + (8.6 - low) * i / 13.0;
                p.dot(Math.cos(i + f) * 0.12, y, Math.sin(i + f) * 0.12, i == 0 ? Tint.SPARK : Tint.STAR);
            }
            double r = Shapes.lerp(0.3, 1.1, f / 13.0);
            p.fixed(Shapes.ring(r, around(r, 3, 10, 30), f * 0.2, 0.05), Tint.GOLD);
        }
        if (f >= 12 && f <= 17) p.fixed(Shapes.star(8, 3, 1.4, f * 0.08, 0.05, 6), Tint.GOLD);
        if (f == 13) {
            p.dot(0, 0.7, 0, Tint.FLASH);
            p.burst(26, 0.8, 0.22, Tint.SPARK, 0.1);
            p.fountain(rnd, 18, 0.5, Tint.TOTEM);
        }
        if (f > 13) {
            double y = Shapes.lerp(0.2, 1.9, (f - 13) / 8.0);
            p.add(Shapes.ring(0.8, 18, f * 0.3, y), Tint.SUN);
        }
    }

    private static void summon(Pen p, int f, Random rnd) {
        if (f < 27) {
            double r = Shapes.lerp(0.5, 2.6, Shapes.ease(Math.min(1, f / 14.0)));
            p.fixed(circleHalf(r, f * 0.05, 0.05, 2.6, f % 2 == 0), Tint.VIOLET);
        }
        if (f >= 6 && f < 22) {
            for (int i = 0; i < 6; i++) {
                double a = rnd.nextDouble() * TAU, r = rnd.nextDouble() * 1.3;
                p.fly(Math.cos(a) * r, 0.1, Math.sin(a) * r, i % 3 == 0 ? Tint.SOUL : Tint.SHADOW, 0,
                        0.08 + rnd.nextDouble() * 0.1, 0);
            }
            double h = Shapes.lerp(0.3, 2.6, (f - 6) / 16.0);
            p.add(Shapes.ring(0.6, 14, -f * 0.4, h), Tint.INDIGO);
        }
        if (f == 22) {
            p.dot(0, 1.0, 0, Tint.FLASH);
            p.burst(32, 1.0, 0.24, Tint.VIOLET, 0.2);
        }
        if (f > 22) {
            double r = Shapes.lerp(0.8, 3.6, (f - 22) / 7.0);
            p.fixed(Shapes.ring(r, around(r, 3, 14, 60), 0, 0.1), Tint.INDIGO);
        }
    }

    private static void forge(Pen p, int f, Random rnd) {
        if (f <= 18) {
            double t = f / 18.0;
            double r = Shapes.lerp(3.0, 0.8, Shapes.ease(t));
            List<Vector> h = Shapes.helix(3, r, r * 0.8, 0.05, Shapes.lerp(0.5, 3.2, t), 1.1, 14, f * 0.3);
            p.add(h, Tint.SUN);
            p.add(List.of(h.get(13), h.get(27), h.get(41)), Tint.SPARK);
            p.fixed(Shapes.ring(3.0, 60, f * 0.05, 0.05), Tint.GOLD);
        }
        if (f == 18) {
            p.dot(0, 1.6, 0, Tint.FLASH);
            p.burst(28, 1.6, 0.22, Tint.STAR, 0.4);
        }
        if (f > 18) {
            double r = Shapes.lerp(1.0, 4.5, (f - 18) / 7.0);
            p.fixed(Shapes.ring(r, around(r, 2.5, 14, 70), 0, 0.1), Tint.SUN);
        }
    }

    private static void blink(Pen p, int f) {
        if (f <= 6) {
            double r = Shapes.lerp(1.7, 0.2, f / 6.0);
            List<Vector> ball = Shapes.sphere(40, r, f * 0.5);
            for (Vector v : ball) v.setY(1.0 + v.getY());
            p.add(ball, f % 2 == 0 ? Tint.VIOLET : Tint.INDIGO);
        }
        if (f == 6) {
            p.dot(0, 1.0, 0, Tint.FLASH);
            p.burst(24, 1.0, 0.3, Tint.SPARK, 0);
        }
        if (f > 6) {
            double r = Shapes.lerp(0.4, 2.8, (f - 6) / 9.0);
            p.add(Shapes.ring(r, around(r, 3, 12, 50), f * 0.2, 1.0), Tint.VIOLET);
            p.fixed(Shapes.ring(r * 0.8, around(r, 2, 10, 40), -f * 0.2, 0.05), Tint.INDIGO);
        }
    }

    private static void sigil(Pen p, int f) {
        double y = 2.7;
        p.add(Shapes.star(5, 2, 0.9, f * 0.15, y, 7), f >= 10 ? Tint.SUN : Tint.GOLD);
        p.add(Shapes.ring(1.05, 26, -f * 0.1, y), Tint.GOLD);
        if (f == 10) p.burst(18, y, 0.14, Tint.STAR, 0.2);
        if (f >= 10) p.add(Shapes.ring(Shapes.lerp(1.1, 2.0, (f - 10) / 9.0), 24, 0, y), Tint.WHITE);
    }

    /** One cycle of a lasting aura; seamless, so cycles join. */
    private static void aura(Kind kind, Pen p, int f, Random rnd) {
        double a = f * TAU / 20; // one turn per cycle
        if (f == 0) {
            // A breath of light as each cycle begins.
            for (int i = 0; i < 4; i++) {
                double b = a + i * TAU / 4;
                p.fly(Math.cos(b) * 0.5, 1.0, Math.sin(b) * 0.5, Tint.SPARK, Math.cos(b) * 0.05, 0.04,
                        Math.sin(b) * 0.05);
            }
        }
        switch (kind) {
            case AURA_HEALING -> {
                p.add(Shapes.ring(1.0, 24, a, 0.1), Tint.LIFE);
                for (int i = 0; i < 2; i++) {
                    double b = a + i * Math.PI;
                    double y = 0.3 + ((f / 20.0 + i * 0.5) % 1.0) * 2.0;
                    p.add(plus(Math.cos(b) * 0.9, y, Math.sin(b) * 0.9, 0.18, 2), Tint.LIFE);
                }
                if (f % 5 == 0) p.dot(Math.cos(-a) * 0.6, 1.9, Math.sin(-a) * 0.6, Tint.HEART);
                if (f % 2 == 0) p.dot(Math.cos(a * 2) * 0.7, 0.4, Math.sin(a * 2) * 0.7, Tint.SPROUT);
            }
            case AURA_HOLY -> {
                p.halo(2.25, 0.4, 14, a, Tint.GOLD);
                p.add(Shapes.ring(1.0, 22, -a, 0.08), Tint.SUN);
                if (f % 2 == 0) p.dot(Math.cos(a * 3) * 0.8, 0.3 + (f % 10) * 0.2, Math.sin(a * 3) * 0.8, Tint.STAR);
            }
            case AURA_FLAME -> {
                p.add(Shapes.ring(0.85, 18, a, 0.1), Tint.EMBER);
                p.add(Shapes.helix(2, 0.75, 0.4, 0.1, 2.0, 1.0, 9, a * 2), Tint.EMBER);
                if (f % 3 == 0) p.dot(Math.cos(-a) * 0.5, 2.1, Math.sin(-a) * 0.5, Tint.SMOKE);
            }
            case AURA_FROST -> {
                p.add(Shapes.ring(1.1, 22, a, 0.08), Tint.FROST);
                for (int i = 0; i < 4; i++) {
                    double b = a + i * TAU / 4;
                    p.dot(Math.cos(b) * 1.1, 2.4 - ((f + i * 5) % 20) * 0.11, Math.sin(b) * 1.1, Tint.SNOW);
                }
            }
            case AURA_SHADOW -> {
                p.add(Shapes.helix(3, 0.9, 0.6, 0.0, 2.2, 0.8, 8, -a * 2), Tint.SHADOW);
                if (f % 2 == 0) p.dot(Math.cos(a) * 0.7, 0.2, Math.sin(a) * 0.7, Tint.SMOKE);
                if (f % 4 == 0) p.dot(Math.cos(-a) * 0.5, 1.6, Math.sin(-a) * 0.5, Tint.SOUL);
            }
            case AURA_STARS -> {
                p.add(Shapes.lift(Shapes.turn(Shapes.ring(1.1, 16, a, 0), 0.6, 0, 0), 1.1), Tint.STAR);
                p.add(Shapes.lift(Shapes.turn(Shapes.ring(1.1, 16, -a, 0), -0.6, 1.2, 0), 1.1), Tint.WHITE);
            }
            case AURA_STORM -> {
                p.add(Shapes.ring(1.0, 16, a * 2, 0.1), Tint.CYAN);
                for (int i = 0; i < 3; i++) {
                    double b = a * 2 + i * TAU / 3;
                    double y = 0.4 + rnd.nextDouble() * 1.6;
                    p.dot(Math.cos(b) * 0.8, y, Math.sin(b) * 0.8, Tint.SPARK);
                }
            }
            case AURA_NATURE -> {
                p.add(Shapes.helix(2, 1.0, 0.7, 0.0, 2.0, 1.0, 10, a), Tint.LIFE);
                if (f % 2 == 0) p.dot(Math.cos(-a) * 0.9, 0.2 + (f % 10) * 0.18, Math.sin(-a) * 0.9, Tint.SPROUT);
                if (f % 5 == 0) p.dot(Math.cos(a) * 0.4, 2.2, Math.sin(a) * 0.4, Tint.ROSE);
            }
            default -> {
            }
        }
    }

    private static void ascension(Pen p, int f, Random rnd) {
        // The summoning circle grows on the ground (and stays there while the mortal rises).
        if (f < 34) {
            double r = Shapes.lerp(0.4, 2.4, Shapes.ease(f / 12.0));
            p.fixed(circleHalf(r, f * 0.02, 0.05, 2.8, f % 2 == 0), Tint.GOLD);
        }
        // A two-strand spiral that tightens round the mortal, spinning ever faster.
        if (f >= 4 && f <= 31) {
            double t = (f - 4) / 27.0;
            double r = Shapes.lerp(2.3, 0.35, Shapes.ease(t));
            double phase = f * 0.35 + f * f * 0.012;
            double top = Shapes.lerp(0.4, 2.3, Shapes.ease((f - 4) / 5.0));
            int per = 22;
            List<Vector> strands = Shapes.helix(2, r, r, 0.05, top, 1.25, per, phase);
            p.add(strands, Tint.SUN);
            // Bright comet heads leading each strand.
            p.add(List.of(strands.get(per - 1), strands.get(2 * per - 1)), Tint.SPARK);
            p.add(List.of(strands.get(per - 2), strands.get(2 * per - 2)), Tint.WHITE);
        }
        // Light gathers into the chest, faster and faster.
        if (f >= 28 && f <= 33) {
            double t = (f - 28) / 5.0;
            double r = Shapes.lerp(3.0, 0.25, t * t);
            // Squashed a little so its lowest dots stay near the ground while the mortal still rises.
            List<Vector> ball = Shapes.sphere(34, r, f * 0.4);
            for (Vector v : ball) v.setY(1.1 + v.getY() * 0.7);
            p.add(ball, Tint.WHITE);
        }
        if (f == 34) {
            p.dot(0, 1.1, 0, Tint.FLASH);
            p.burst(40, 1.1, 0.22, Tint.STAR, 0.3);
            p.fountain(rnd, 26, 1.0, Tint.TOTEM);
        }
        // A golden ring rolls outward from the waist.
        if (f >= 34 && f <= 39) {
            double r = Shapes.lerp(0.6, 3.8, Shapes.ease((f - 34) / 5.0));
            p.add(Shapes.ring(r, around(r, 3.5, 16, 70), f * 0.1, 1.0), Tint.GOLD);
            if (f <= 36) p.add(Shapes.ring(r * 0.8, around(r * 0.8, 2.0, 10, 30), -f * 0.1, 1.0), Tint.SUN);
        }
        if (f >= 34) p.halo(2.25, 0.35, 12, f * 0.25, Tint.GOLD);
    }

    private static void anoint(Pen p, int f, Random rnd) {
        if (f <= 9) {
            double y = Shapes.lerp(2.6, 0.05, Shapes.ease(f / 9.0));
            p.add(Shapes.ring(0.85, 22, f * 0.3, y), Tint.GOLD);
            p.add(Shapes.ring(0.6, 16, -f * 0.3, Math.min(2.6, y + 0.25)), Tint.WHITE);
        }
        if (f >= 9 && f <= 13) {
            p.add(Shapes.star(8, 3, 1.3, f * 0.06, 0.05, 6), Tint.GOLD);
            p.add(Shapes.ring(1.3, 26, -f * 0.06, 0.05), Tint.SUN);
        }
        if (f == 10) {
            for (int i = 0; i < 22; i++) {
                double a = rnd.nextDouble() * TAU, r = 0.25 + rnd.nextDouble() * 0.6;
                p.fly(Math.cos(a) * r, 0.2 + rnd.nextDouble() * 1.4, Math.sin(a) * r, Tint.STAR, 0,
                        0.06 + rnd.nextDouble() * 0.08, 0);
            }
            p.burst(14, 1.0, 0.16, Tint.SPARK, 0);
        }
        if (f >= 10 && f <= 15) p.halo(2.25, 0.35, 10, f * 0.3, Tint.GOLD);
    }

    private static void renewal(Pen p, int f) {
        if (f <= 19) {
            for (int s = 0; s < 2; s++) {
                Tint t = s == 0 ? Tint.LIFE : Tint.ROSE;
                double dir = s == 0 ? 1 : -1;
                for (int k = 0; k < 9; k++) {
                    double g = f - k * 0.3; // the tail trails behind the head
                    if (g < 0) break;
                    double a = dir * g * 0.55 + s * Math.PI;
                    double y = Shapes.lerp(0, 2.2, g / 19.0);
                    double r = 0.75;
                    p.dot(Math.cos(a) * r, y, Math.sin(a) * r, k == 0 && f % 2 == 0 ? Tint.SPARK : t);
                }
            }
        }
        if (f < 8 && f % 2 == 0) {
            double r = 0.3 + (f / 2 % 4) * 0.45;
            p.add(Shapes.ring(r, around(r, 3, 10, 30), f * 0.2, 0.05), Tint.LIFE);
        }
        if (f == 14) p.burst(26, 1.1, 0.12, Tint.LIFE, 0.5);
        if (f >= 14 && (f - 14) % 3 == 0) p.add(Shapes.ring(0.5, 3, f * 0.4, 2.4), Tint.HEART);
        if (f == 18) p.burst(24, 1.1, 0.18, Tint.SPARK, 0.2);
    }

    private static void resurrection(Pen p, int f, Random rnd) {
        if (f < 26) {
            double top = Math.min(6, 0.6 + f * 0.6);
            double bottom = f < 20 ? 0 : (f - 19) * 1.0;
            if (top > bottom + 0.2) {
                int per = (int) Math.ceil((top - bottom) * 3.4);
                p.add(Shapes.helix(2, 0.45, 0.45, bottom, top, (top - bottom) / 2.0, per, f * 0.45), Tint.WHITE);
                p.add(Shapes.line(new Vector(0, bottom, 0), new Vector(0, top, 0),
                        (int) Math.ceil((top - bottom) * 2.5)), Tint.GOLD);
            }
        }
        for (int start : new int[] { 0, 5, 10 }) {
            int age = f - start;
            if (age < 0 || age >= 8) continue;
            double r = 0.5 + age * 0.55;
            p.add(Shapes.ring(r, around(r, 2.8, 12, 40), start * 0.7, 0.05), Tint.GOLD);
        }
        if (f == 12) {
            p.dot(0, 1.0, 0, Tint.FLASH);
            p.fountain(rnd, 24, 0.8, Tint.TOTEM);
            p.burst(30, 1.0, 0.2, Tint.STAR, 0.1);
        }
        if (f > 12) p.halo(2.25, 0.35, 12, f * 0.25, Tint.GOLD);
    }

    private static void blessing(Pen p, int f) {
        p.add(Shapes.polygon(6, 1.1, f * 0.08, 0.05, 5), Tint.VIOLET);
        Tint[] colors = { Tint.ROSE, Tint.WHITE, Tint.ROSE };
        for (int i = 0; i < 3; i++) {
            int age = f - i * 3;
            if (age < 0 || age > 6) continue;
            double t = age / 6.0;
            double y = Shapes.lerp(0.1, 2.0, Shapes.ease(t));
            double r = Shapes.lerp(0.75, 0.5, t);
            p.add(Shapes.ring(r, 18, f * 0.25 + i, y), colors[i]);
        }
        if (f == 8) p.burst(16, 1.1, 0.1, Tint.STAR, 0);
        if (f == 12) p.burst(22, 1.1, 0.18, Tint.SPARK, 0.4);
    }

    private static void curse(Pen p, int f, Random rnd) {
        if (f <= 19) {
            double r = Shapes.lerp(2.2, 0.9, Shapes.ease(f / 16.0));
            p.add(Shapes.star(5, 2, r, -f * 0.035, 0.05, 9), Tint.BLOOD);
            p.add(Shapes.ring(r * 1.08, around(r, 3, 14, 40), f * 0.05, 0.05), Tint.INDIGO);
        }
        for (int start = 0; start <= 12; start += 4) {
            int age = f - start;
            if (age < 0 || age > 6) continue;
            double y = Shapes.lerp(2.6, 0.1, age / 6.0);
            p.add(Shapes.ring(0.6, 14, -f * 0.2, y), Tint.SHADOW);
        }
        if (f >= 16 && f <= 23) {
            double t = (f - 16) / 7.0;
            double r = Shapes.lerp(1.6, 0.2, t);
            p.add(Shapes.lift(Shapes.sphere(30, r, -f * 0.3), 1.1), Tint.INDIGO);
        }
        if (f == 16) {
            for (int i = 0; i < 8; i++) {
                double a = rnd.nextDouble() * TAU;
                p.fly(Math.cos(a) * 0.3, 0.3, Math.sin(a) * 0.3, Tint.SMOKE, Math.cos(a) * 0.05, 0.04,
                        Math.sin(a) * 0.05);
            }
        }
    }

    private static void judgement(Pen p, int f) {
        if (f < 18) {
            double t = Shapes.ease(f / 17.0);
            double r = Shapes.lerp(3.2, 1.0, t);
            p.add(Shapes.ring(r, around(r, 3.5, 20, 70), f * 0.1, 0.05), Tint.BLOOD);
            p.add(Shapes.polygon(3, r * 0.92, -f * 0.15, 0.05, 9), Tint.WHITE);
            double tip = Shapes.lerp(16, 0, t);
            p.add(Shapes.line(new Vector(0, 16, 0), new Vector(0, tip, 0),
                    Math.max(2, (int) Math.ceil((16 - tip) / 0.6))), Tint.BLOOD);
        }
        if (f == 18) {
            p.dot(0, 1.0, 0, Tint.FLASH);
            p.burst(40, 1.0, 0.2, Tint.EMBER, 0.2);
            p.add(Shapes.ring(1.0, 24, 0, 0.1), Tint.WHITE);
        }
        if (f >= 18) {
            double r = 1.0 + (f - 18) * 0.9;
            p.add(Shapes.ring(r, around(r, 2.2, 16, 40), f * 0.3, 0.15), Tint.EMBER);
        }
    }

    private static void seal(Pen p, int f, Random rnd) {
        double r = Shapes.lerp(0.6, 3.3, Shapes.ease(f / 20.0));
        if (f % 2 == 0) {
            p.fixed(circleHalf(r, f * 0.012, 0.05, 2.6, true), Tint.VIOLET);
            p.fixed(Shapes.polygon(4, r * 0.62, -f * 0.03, 0.08, 9), Tint.WHITE);
        } else {
            p.fixed(circleHalf(r, f * 0.012, 0.05, 2.6, false), Tint.VIOLET);
            for (int i = 0; i < 4; i++) {
                double a = rnd.nextDouble() * TAU;
                p.out.add(new Dot(Math.cos(a) * r, 0.1, Math.sin(a) * r, Tint.STAR, true, 0, 0.05, 0));
            }
        }
        if (f == 0) {
            for (Vector d : Shapes.ring(1, 20, 0, 0)) {
                p.out.add(new Dot(d.getX() * 0.5, 0.1, d.getZ() * 0.5, Tint.STAR, true, d.getX() * 0.08, 0.12,
                        d.getZ() * 0.08));
            }
        }
    }

    private static void arrival(Pen p, int f) {
        if (f < 26) {
            // A funnel of shadow screwing down into the heart of where the body will stand.
            double tight = f / 25.0;
            double rTop = Shapes.lerp(4.5, 2.2, tight);
            int per = 15 + (int) (f * 0.35);
            Tint[] tints = { Tint.SHADOW, Tint.VIOLET, Tint.INDIGO };
            double phase = -(f * 0.3 + f * f * 0.008);
            for (int s = 0; s < 3; s++) {
                for (int i = 0; i < per; i++) {
                    double t = i / (double) (per - 1);
                    double a = phase + s * TAU / 3 + t * 1.6 * TAU;
                    double rr = Shapes.lerp(rTop, 0.12, Math.pow(t, 0.8));
                    double y = Shapes.lerp(7.5, 1.4, t);
                    p.dot(Math.cos(a) * rr, y, Math.sin(a) * rr, tints[s]);
                }
            }
            double gr = Shapes.lerp(5.0, 0.8, Shapes.ease(f / 25.0));
            p.add(Shapes.ring(gr, around(gr, 2.2, 10, 60), f * 0.12, 0.05), Tint.VIOLET);
        }
        for (int strike : new int[] { 8, 16, 22 }) {
            if (f == strike || f == strike + 1) bolt(p, new Random(strike * 31L + 5));
        }
        if (f >= 12 && f < 26) {
            double h = Shapes.lerp(0.3, 2.4, (f - 12) / 13.0);
            p.add(Shapes.helix(2, 0.45, 0.45, 0.05, h, h / 1.6, 4 + (int) (h * 4), f * 0.6), Tint.SPARK);
        }
        if (f == 26) {
            p.dot(0, 1.2, 0, Tint.FLASH);
            p.burst(60, 1.2, 0.25, Tint.STAR, 0.7);
        }
        if (f >= 26 && f <= 31) {
            double r = 0.8 + (f - 26) * 1.05;
            p.add(Shapes.ring(r, around(r, 2.3, 16, 90), f * 0.2, 0.1), Tint.WHITE);
        }
    }

    /** A jagged bolt of sparks from the sky down into the heart. */
    private static void bolt(Pen p, Random rnd) {
        double a = rnd.nextDouble() * TAU;
        Vector from = new Vector(Math.cos(a) * 2.5, 12, Math.sin(a) * 2.5);
        Vector to = new Vector(0, 1.4, 0);
        Vector prev = from;
        int segments = 8;
        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            Vector next = from.clone().multiply(1 - t).add(to.clone().multiply(t));
            if (i < segments) {
                next.add(new Vector(rnd.nextDouble() - 0.5, (rnd.nextDouble() - 0.5) * 0.5, rnd.nextDouble() - 0.5)
                        .multiply(1.3));
            }
            List<Vector> seg = Shapes.line(prev, next, 5);
            p.add(seg.subList(i == 1 ? 0 : 1, seg.size()), Tint.SPARK);
            prev = next;
        }
    }

    private static void departure(Pen p, int f) {
        if (f == 2) p.burst(30, 1.1, 0.15, Tint.STAR, 0.2);
        if (f < 6) p.add(Shapes.ring(1.0, 22, f * 0.2, 0.05), Tint.INDIGO);
        if (f >= 2) {
            double t = (f - 2) / 19.0;
            double r1 = Shapes.lerp(0.6, 2.2, t);
            double top = Shapes.lerp(2.0, 7.0, t);
            double bottom = Shapes.lerp(0, 3.5, t * t);
            int per = (int) Math.round(Shapes.lerp(22, 8, t));
            List<Vector> h = Shapes.helix(3, 0.4 + t * 0.6, r1, bottom, top, 1.3, per, f * 0.4);
            for (int i = 0; i < h.size(); i++) {
                p.add(List.of(h.get(i)), (i / per) % 2 == 0 ? Tint.VIOLET : Tint.SHADOW);
            }
        }
    }

    private static final float[] CHIME = { 0.63f, 0.75f, 0.89f, 1.06f, 1.19f, 1.41f, 1.68f };

    /** The sounds of a rite, in frame order. */
    public static List<Cue> cues(Kind kind) {
        List<Cue> c = new ArrayList<>();
        switch (kind) {
            case ASCENSION -> {
                c.add(new Cue(0, "block.beacon.activate", 1.0f, 1.3f));
                for (int i = 0; i < CHIME.length; i++) {
                    c.add(new Cue(4 + i * 4, "block.note_block.chime", 0.9f, CHIME[i]));
                }
                c.add(new Cue(28, "block.respawn_anchor.charge", 0.9f, 1.5f));
                c.add(new Cue(34, "ui.toast.challenge_complete", 1.0f, 1.0f));
                c.add(new Cue(34, "entity.player.levelup", 1.0f, 1.0f));
                c.add(new Cue(34, "entity.firework_rocket.twinkle", 0.8f, 1.2f));
            }
            case ANOINT -> {
                c.add(new Cue(0, "block.amethyst_block.chime", 1.0f, 1.2f));
                c.add(new Cue(10, "block.enchantment_table.use", 0.9f, 1.3f));
            }
            case RENEWAL -> c.add(new Cue(12, "block.amethyst_block.chime", 1.0f, 0.9f));
            case RESURRECTION -> {
                c.add(new Cue(0, "block.beacon.activate", 1.0f, 1.0f));
                c.add(new Cue(12, "item.totem.use", 0.8f, 1.0f));
            }
            case BLESSING -> {
                c.add(new Cue(0, "block.amethyst_block.chime", 0.9f, 1.5f));
                c.add(new Cue(8, "block.enchantment_table.use", 0.7f, 1.6f));
            }
            case CURSE -> {
                c.add(new Cue(0, "block.respawn_anchor.deplete", 0.9f, 0.6f));
                c.add(new Cue(8, "entity.warden.heartbeat", 1.0f, 0.8f));
                c.add(new Cue(16, "entity.warden.heartbeat", 1.0f, 0.8f));
            }
            case JUDGEMENT -> {
                c.add(new Cue(0, "block.bell.use", 1.0f, 0.5f));
                c.add(new Cue(18, "item.trident.thunder", 0.8f, 0.9f));
            }
            case SEAL -> {
                c.add(new Cue(0, "block.beacon.activate", 1.0f, 0.7f));
                c.add(new Cue(10, "block.beacon.ambient", 1.0f, 0.8f));
                c.add(new Cue(24, "block.enchantment_table.use", 0.8f, 0.7f));
            }
            case ARRIVAL -> {
                c.add(new Cue(0, "entity.warden.sonic_charge", 1.0f, 0.6f));
                for (int f : new int[] { 8, 16, 22 }) c.add(new Cue(f, "item.trident.thunder", 0.6f, 1.3f));
                c.add(new Cue(26, "entity.lightning_bolt.impact", 1.0f, 0.7f));
                c.add(new Cue(26, "block.end_portal.spawn", 0.7f, 0.8f));
            }
            case DEPARTURE -> {
                c.add(new Cue(0, "entity.enderman.teleport", 0.8f, 0.6f));
                c.add(new Cue(2, "block.beacon.deactivate", 1.0f, 0.8f));
            }
            case HEALING -> {
                c.add(new Cue(0, "block.amethyst_block.resonate", 0.9f, 1.4f));
                c.add(new Cue(6, "block.note_block.chime", 0.7f, 1.19f));
                c.add(new Cue(14, "entity.player.levelup", 0.6f, 1.6f));
                c.add(new Cue(14, "block.amethyst_block.chime", 1.0f, 1.3f));
            }
            case GIFT -> {
                c.add(new Cue(0, "block.beacon.power_select", 0.8f, 1.5f));
                c.add(new Cue(13, "entity.item.pickup", 1.0f, 0.7f));
                c.add(new Cue(13, "entity.firework_rocket.twinkle", 0.8f, 1.3f));
            }
            case SUMMON -> {
                c.add(new Cue(0, "block.portal.trigger", 0.5f, 1.4f));
                c.add(new Cue(10, "entity.evoker.prepare_summon", 0.9f, 0.8f));
                c.add(new Cue(22, "entity.evoker.cast_spell", 1.0f, 0.7f));
            }
            case FORGE -> {
                c.add(new Cue(0, "block.anvil.use", 0.6f, 0.6f));
                c.add(new Cue(9, "block.beacon.ambient", 1.0f, 1.2f));
                c.add(new Cue(18, "block.respawn_anchor.charge", 1.0f, 1.2f));
            }
            case BLINK -> {
                c.add(new Cue(0, "entity.enderman.teleport", 0.9f, 1.2f));
                c.add(new Cue(6, "block.respawn_anchor.set_spawn", 0.8f, 1.6f));
            }
            case SIGIL -> {
                c.add(new Cue(0, "block.enchantment_table.use", 0.9f, 1.4f));
                c.add(new Cue(10, "block.bell.resonate", 0.7f, 1.5f));
            }
            case AURA_HEALING, AURA_NATURE -> c.add(new Cue(0, "block.amethyst_block.chime", 0.25f, 1.6f));
            case AURA_HOLY, AURA_STARS -> c.add(new Cue(0, "block.beacon.ambient", 0.25f, 1.4f));
            case AURA_FLAME -> c.add(new Cue(0, "block.fire.ambient", 0.4f, 1.0f));
            case AURA_FROST -> c.add(new Cue(0, "block.powder_snow.step", 0.4f, 0.8f));
            case AURA_SHADOW -> c.add(new Cue(0, "ambient.soul_sand_valley.mood", 0.3f, 0.8f));
            case AURA_STORM -> c.add(new Cue(0, "entity.lightning_bolt.thunder", 0.15f, 1.8f));
        }
        return c;
    }
}
