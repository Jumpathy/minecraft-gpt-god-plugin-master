package net.bigyous.gptgodmc.god.vessel;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.util.Vector;

import net.bigyous.gptgodmc.god.Shapes;

/**
 * The god's true form, as pure geometry: a hypercube forever turning itself inside out through the
 * fourth dimension, caged in three gyroscope wheels set with eyes that counter-rotate on different
 * axes, long thin spikes breathing in and out through all of it, and at its heart one great eye.
 * Everything is in blocks around the form's centre (y up), so the game ({@link TrueFormShow}) and the
 * offline previews draw exactly the same thing. Also holds the pure rules of the gaze.
 */
public final class TrueForm {
    /** At most this many motes in one drawn frame (a frame is drawn every 2 ticks). */
    public static final int BUDGET = 350;
    /** How far above the ground its centre hangs (blocks). */
    public static final double HEIGHT = 10;
    /** Ticks it takes to unfold out of a single point, and to fold back into it. */
    public static final int UNFOLD_TICKS = 40;
    public static final int FOLD_TICKS = 30;
    /** Ticks the great eye takes to open fully once the warning is over. */
    public static final int EYE_OPEN_TICKS = 12;
    /** Half the size of its heart, used to widen the gaze cone for mortals right beside it. */
    public static final double CORE_RADIUS = 2.2;

    private static final double TAU = Math.PI * 2;

    /** How a mote is drawn: coloured dust, a bright self-lit spark, a blue soul flame, fire, or an electric spark. */
    public enum Glyph {
        DUST, GLOW, SOUL, FLAME, SPARK
    }

    // ---- What the gaze does ----------------------------------------------------------------------

    /** What happens to a mortal who looks upon the true form. */
    public enum Gaze {
        /** They die. */
        KILL("kill", "dies at once", "or die"),
        /** Taken by a tendril of light, lifted into a storm before the form, and only then unmade. */
        SEIZE("seize", "is seized by a tendril of your light, lifted into a storm before you to behold you, and "
                + "after a few seconds unmade (they die)", "or be taken"),
        /** Hurled back and crushed with dread, but never killed ("You dare impose upon me?"). */
        SMITE("smite", "is hurled back and struck down with dread (never killed)", "or be struck down"),
        /** Struck blind and mad with terror. */
        BLIND("blind", "is struck blind and mad with terror", "or lose your sight");

        public final String id;
        /** What befalls the one who looks, for the god ("any mortal who looks at it ..."). */
        public final String fate;
        /** The end of the warning mortals see ("Do not look upon it — ..."). */
        public final String threat;

        Gaze(String id, String fate, String threat) {
            this.id = id;
            this.fate = fate;
            this.threat = threat;
        }

        /** Whether this ends in the mortal's death. */
        public boolean lethal() {
            return this == KILL || this == SEIZE;
        }

        /** kill, seize, smite or blind (case-insensitive); null or unknown → null. */
        public static Gaze parse(String s) {
            if (s == null) return null;
            String k = s.trim().toLowerCase(java.util.Locale.ROOT);
            for (Gaze g : values()) if (g.id.equals(k)) return g;
            return null;
        }

        /**
         * The server's setting: {@code vessel.true-form.gaze}, else the legacy {@code lethal} flag
         * (true = kill, false = blind), else kill.
         */
        public static Gaze fromConfig(String gaze, Boolean legacyLethal) {
            Gaze g = parse(gaze);
            if (g != null) return g;
            if (legacyLethal != null) return legacyLethal ? KILL : BLIND;
            return KILL;
        }

        /**
         * What the gaze does when the god asks for {@code requested} and the server's setting is
         * {@code configured}: the god may choose freely, except that it may only kill where the server
         * lets the gaze kill. Null = the server's setting.
         */
        public static Gaze allowed(Gaze requested, Gaze configured) {
            if (configured == null) configured = KILL;
            if (requested == null) return configured;
            if (requested.lethal() && !configured.lethal()) return configured;
            return requested;
        }
    }

    // ---- Which shape it takes --------------------------------------------------------------------

    /** The shapes the true form can take: the hypercube, and four in the manner of the angels of old. */
    public enum Form {
        HYPERCUBE("hypercube", "a hypercube turning inside out through four dimensions, caged in wheels of eyes",
                CORE_RADIUS, HEIGHT),
        OPHANIM("ophanim", "vast wheels within wheels of burning gold, their rims full of eyes, ringed with fire",
                4.6, 14),
        SERAPHIM("seraphim", "six vast burning wings under two haloes: two veil your face, two your feet, two "
                + "beat the air; when your eye opens the veiling wings lift like a crown", 4.2, 15),
        EYE("eye", "the Watcher: a colossal eye that was never an animal's, a ball of void lit like an eclipse, "
                + "its iris wheels of teeth turning against each other round too many bar pupils, among a swarm of "
                + "lesser eyes; it darts from mortal to mortal and its pupils shut to a line on the one it finds",
                5.6, 16),
        PILLAR("pillar", "a shaft of light from the ground into the sky, ringed by tilting wheels of gold full of "
                + "eyes and crowned with greater eyes", 4.0, 14);

        public final String id;
        public final String text;
        /** Half the size of its heart: the gaze cone widens to cover it for mortals right beside it. */
        public final double heart;
        /** How far above the ground its centre hangs (blocks). */
        public final double rise;

        Form(String id, String text, double heart, double rise) {
            this.id = id;
            this.text = text;
            this.heart = heart;
            this.rise = rise;
        }

        /** A form by id (case-insensitive); null or unknown → null. */
        public static Form parse(String s) {
            if (s == null) return null;
            String k = s.trim().toLowerCase(java.util.Locale.ROOT);
            for (Form f : values()) if (f.id.equals(k)) return f;
            return null;
        }

        /** The ids, in order. */
        public static List<String> ids() {
            List<String> out = new ArrayList<>();
            for (Form f : values()) out.add(f.id);
            return out;
        }

        /** The form that suits a god's nature; null for an archetype without one (then it is random). */
        public static Form forArchetype(String archetype) {
            if (archetype == null) return null;
            return switch (archetype.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "benevolent" -> SERAPHIM;
                case "chill" -> OPHANIM;
                case "trickster" -> EYE;
                case "evil" -> EYE;
                case "wrathful" -> PILLAR;
                case "cosmic" -> HYPERCUBE;
                default -> null;
            };
        }

        /**
         * Which form to take: the one asked for, else (for "auto", blank or unknown) the server's setting,
         * else the archetype's, else one at random. "random" always picks at random.
         *
         * @param roll a number in [0, 1) for the random pick
         */
        public static Form choose(String requested, String configured, String archetype, double roll) {
            Form f = parse(requested);
            if (f != null) return f;
            boolean random = "random".equalsIgnoreCase(requested == null ? "" : requested.trim());
            if (!random) {
                f = parse(configured);
                if (f != null) return f;
                random = "random".equalsIgnoreCase(configured == null ? "" : configured.trim());
                if (!random) {
                    f = forArchetype(archetype);
                    if (f != null) return f;
                }
            }
            Form[] all = values();
            int i = (int) Math.floor(Math.max(0, Math.min(0.999999, roll)) * all.length);
            return all[i];
        }
    }

    /** The most a smite takes directly: half a heart. */
    public static final double SMITE_MAX_DAMAGE = 1.0;
    /** A smitten mortal's health never falls below this (half a heart) while the smite's guard lasts. */
    public static final double SMITE_FLOOR = 1.0;
    /** How long a smitten mortal is guarded from dying of anything (the knockback's fall included), ticks. */
    public static final int SMITE_GUARD_TICKS = 200;
    /** What the god booms at whoever looks, when the gaze smites. */
    public static final String SMITE_LINE = "You dare impose upon me?";
    /** What it booms at those who keep looking. */
    public static final List<String> SMITE_AGAIN = List.of("Again you look upon me?", "Avert your eyes, mortal.",
            "Kneel, and look away.");

    /** The direct damage of a smite: half a heart at most, and never enough to take health below the floor. */
    public static double smiteDamage(double health) {
        if (!(health > 0)) return 0;
        return Math.max(0, Math.min(SMITE_MAX_DAMAGE, health - SMITE_FLOOR));
    }

    /**
     * A guarded mortal's health after a blow of {@code damage}: unchanged rules when they survive it,
     * otherwise held at the floor (or where it already was, if lower). Never 0 for a living mortal.
     */
    public static double guardedHealth(double health, double damage) {
        if (!(damage > 0)) return health;
        if (damage < health) return health - damage;
        return Math.min(health, SMITE_FLOOR);
    }

    /** Whether a blow would kill a guarded mortal (so the guard must step in). */
    public static boolean guardNeeded(double health, double damage) {
        return damage >= health;
    }

    /** The line the god booms at a mortal smitten for the {@code n}th time (0 = the first). */
    public static String smiteLine(int n) {
        return n <= 0 ? SMITE_LINE : SMITE_AGAIN.get((n - 1) % SMITE_AGAIN.size());
    }

    /** One particle of the form: where (relative to its centre), what colour (0xRRGGBB), how big. */
    public record Mote(double x, double y, double z, int rgb, float size, Glyph glyph) {
    }

    private TrueForm() {
    }

    /**
     * One frame of the form.
     *
     * @param tick     game ticks since it began (drives every rotation)
     * @param unfold   0 = folded into a point, 1 = fully unfolded
     * @param eyeOpen  0 = the great eye is shut, 1 = wide open
     * @param eyeYaw   where the great eye looks, round the vertical: atan2(dx, dz) of the direction (radians)
     * @param eyePitch how far it looks down (negative = up), radians
     */
    public static List<Mote> frame(int tick, double unfold, double eyeOpen, double eyeYaw, double eyePitch) {
        double u = clamp01(unfold);
        double open = clamp01(eyeOpen);
        double t = tick;
        List<Mote> out = new ArrayList<>(BUDGET + 16);
        if (u <= 0.001) {
            out.add(new Mote(0, 0, 0, 0xFFFFFF, 1.4f, Glyph.GLOW));
            return out;
        }
        // The pieces unfold one after another: the hypercube first, then the wheels, then the spikes.
        double sCube = Shapes.ease(u / 0.55);
        double sRings = Shapes.ease((u - 0.2) / 0.5);
        double sSpikes = Shapes.ease((u - 0.45) / 0.55);
        double sEye = Shapes.ease(u / 0.3);

        tesseract(out, t, sCube, open);
        rings(out, t, sRings, open);
        spikes(out, t, sSpikes, open);
        eye(out, t, sEye, open, eyeYaw, eyePitch);
        return fit(out, BUDGET);
    }

    // ---- The pieces ----------------------------------------------------------------------------

    /** The hypercube, turning through three planes of the fourth axis at once, twisted about its spine. */
    private static void tesseract(List<Mote> out, double t, double s, double open) {
        if (s < 0.02) return;
        double scale = 3.4 * s;
        List<Vector> pts = Shapes.tesseract(t * 0.019, t * 0.0117 + 1.1, t * 0.0079 + 0.4, scale, 5);
        pts = Shapes.turn(pts, t * 0.0043, t * 0.0081, 0.32);
        double twist = 0.45 * Math.sin(t * 0.027);
        int i = 0;
        for (Vector p : pts) {
            // A twist that grows with height: its straight edges seem to bend where they should not.
            double a = twist * p.getY() / 3.0;
            double c = Math.cos(a), sn = Math.sin(a);
            double x = p.getX() * c - p.getZ() * sn, z = p.getX() * sn + p.getZ() * c;
            double r = Math.sqrt(x * x + p.getY() * p.getY() + z * z) / (scale * 1.9);
            // Near the heart it burns white; at the swelling rim it sinks into violet.
            int rgb = mix(0xEFE4FF, 0x9150FF, clamp01(r));
            out.add(new Mote(x, p.getY(), z, rgb, 1.3f, (i % 20 == 0) ? Glyph.GLOW : Glyph.DUST));
            i++;
        }
    }

    /** Three gyroscope wheels, each tilted on its own axis and spinning against the next, set with eyes. */
    private static void rings(List<Mote> out, double t, double s, double open) {
        if (s < 0.02) return;
        double[][] tilt = { { Math.PI / 2, 0 }, { Math.PI / 2, Math.PI / 2 }, { 0.42, 0.95 } };
        int[] colors = { 0xFFC94A, 0xFFB02E, 0xFFE08A };
        for (int k = 0; k < 3; k++) {
            double r = (4.7 + 0.6 * k) * s;
            double dir = k % 2 == 0 ? 1 : -1;
            double spin = dir * t * (0.045 + 0.017 * k);
            double pitch = tilt[k][0] + 0.25 * Math.sin(t * 0.011 + k * 2.0);
            double yaw = t * (0.007 + 0.006 * k) * -dir + k * 1.3;
            double roll = tilt[k][1];
            List<Vector> wheel = Shapes.turn(Shapes.ring(r, 22, spin, 0), pitch, yaw, roll);
            for (int i = 0; i < wheel.size(); i++) {
                Vector p = wheel.get(i);
                out.add(new Mote(p.getX(), p.getY(), p.getZ(), colors[k], 1.15f, Glyph.DUST));
            }
            // An eye rides on each wheel, staring outward.
            for (int e = 0; e < 1; e++) {
                double a = spin + 0.5;
                List<Vector> lid = new ArrayList<>();
                double w = 0.6 * s, h = (0.1 + 0.2 * open) * s;
                for (int j = -2; j <= 2; j++) {
                    double v = j / 2.0;
                    double y = h * (1 - v * v);
                    // In the wheel's plane: along the rim (tangent) and across it (radius).
                    double ta = a + v * w / Math.max(0.5, r);
                    lid.add(new Vector(Math.cos(ta) * (r + y), 0, Math.sin(ta) * (r + y)));
                    if (Math.abs(j) < 2) lid.add(new Vector(Math.cos(ta) * (r - y), 0, Math.sin(ta) * (r - y)));
                }
                for (Vector p : Shapes.turn(lid, pitch, yaw, roll)) {
                    out.add(new Mote(p.getX(), p.getY(), p.getZ(), 0xFFF6E0, 0.9f, Glyph.DUST));
                }
                Vector pupil = Shapes.turn(List.of(new Vector(Math.cos(a) * r, 0, Math.sin(a) * r)), pitch, yaw, roll)
                        .get(0);
                out.add(new Mote(pupil.getX(), pupil.getY(), pupil.getZ(), open > 0.5 ? 0xFF2040 : 0xFFFFFF, 1.3f,
                        open > 0.5 ? Glyph.DUST : Glyph.GLOW));
            }
        }
    }

    /** Long thin spikes through everything, each breathing in and out on its own beat. */
    private static void spikes(List<Mote> out, double t, double s, double open) {
        if (s < 0.02) return;
        int rays = 9, per = 7;
        List<Vector> dirs = Shapes.sphere(rays, 1, t * 0.004);
        dirs = Shapes.turn(dirs, t * 0.0031, -t * 0.0052, 0);
        for (int k = 0; k < rays; k++) {
            Vector d = dirs.get(k);
            double breath = 0.5 + 0.5 * Math.sin(t * 0.085 + k * 2.399);
            double r0 = 1.6 * s;
            double r1 = (5.8 + 2.6 * breath + 0.6 * open) * s;
            for (int j = 0; j < per; j++) {
                double f = (j + 1) / (double) per;
                double r = Shapes.lerp(r0, r1, f * f);
                int rgb = mix(open > 0.5 ? 0xFF5A70 : 0xFF70B0, 0xD08CFF, f);
                boolean tip = j == per - 1;
                out.add(new Mote(d.getX() * r, d.getY() * r, d.getZ() * r, tip ? 0xFFD6E6 : rgb,
                        tip ? 1.0f : 1.15f, tip ? Glyph.GLOW : Glyph.DUST));
            }
        }
    }

    /** The great eye at the heart: white-hot lids, a crimson iris, a black slit; shut, a seam of light. */
    private static void eye(List<Mote> out, double t, double s, double open, double yaw, double pitch) {
        if (s < 0.02) return;
        double width = 2.1 * s;
        double half = 1.2 * open * s;
        List<Vector> lids = Shapes.eye(width, half, 13);
        // Shapes.eye lists the lids first, then (when open enough) 13 iris points and a 5-point pupil.
        int irisPoints = Math.min(half * 0.72, width * 0.5) > 0.05 ? 18 : 0;
        int lidPoints = lids.size() - irisPoints;
        lids = Shapes.turn(lids, pitch, yaw, 0);
        int n = 0;
        for (Vector p : lids) {
            boolean lid = n < lidPoints;
            int rgb = lid ? 0xFFFFFF : (n >= lids.size() - 5 ? 0x14000A : 0xFF1A33);
            out.add(new Mote(p.getX(), p.getY(), p.getZ(), rgb, lid ? 1.2f : 1.35f, lid ? Glyph.GLOW : Glyph.DUST));
            n++;
        }
    }

    // ---- The gaze ------------------------------------------------------------------------------

    /**
     * Whether a mortal at {@code eye} looking along {@code look} is looking at {@code target}: within
     * {@code range} blocks and with its view within {@code maxDegrees} of dead-on. Ignores walls.
     */
    public static boolean gazes(Vector eye, Vector look, Vector target, double maxDegrees, double range) {
        return gazes(eye, look, target, maxDegrees, range, 0);
    }

    /**
     * As {@link #gazes(Vector, Vector, Vector, double, double)}, but the cone also widens to cover a
     * heart of {@code radius} blocks, so standing right under it and looking at its edge still counts.
     */
    public static boolean gazes(Vector eye, Vector look, Vector target, double maxDegrees, double range,
            double radius) {
        Vector to = target.clone().subtract(eye);
        double dist = to.length();
        if (dist > range) return false;
        double len = look.length();
        if (len < 1e-9) return false;
        if (dist < 1e-6) return true;
        double allowed = maxDegrees;
        if (radius > 0) allowed = Math.max(allowed, Math.toDegrees(Math.atan(radius / dist)));
        double cos = to.dot(look) / (dist * len);
        return cos >= Math.cos(Math.toRadians(Math.min(179, allowed)));
    }

    /** The angle in degrees between where a mortal looks and the direction to the target. */
    public static double angleTo(Vector eye, Vector look, Vector target) {
        Vector to = target.clone().subtract(eye);
        double d = to.length() * look.length();
        if (d < 1e-9) return 0;
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, to.dot(look) / d))));
    }

    // ---- Timing --------------------------------------------------------------------------------

    /**
     * How long the form is held, in seconds, warning included: at least the warning plus two seconds of
     * the open eye, at most the server's maximum.
     */
    public static int holdSeconds(int requested, int warningSeconds, int maxSeconds) {
        int min = warningSeconds + 2;
        int max = Math.max(min, maxSeconds);
        return Math.max(min, Math.min(max, requested));
    }

    /** Seconds until the form may be revealed again (0 = now). */
    public static long cooldownLeft(Long lastUsedMillis, long nowMillis, int cooldownSeconds) {
        if (lastUsedMillis == null || cooldownSeconds <= 0) return 0;
        long left = lastUsedMillis + cooldownSeconds * 1000L - nowMillis;
        return left <= 0 ? 0 : (left + 999) / 1000;
    }

    // ---- Helpers -------------------------------------------------------------------------------

    /** Thins the frame evenly to the budget. */
    static List<Mote> fit(List<Mote> motes, int budget) {
        if (motes.size() <= budget) return motes;
        List<Mote> out = new ArrayList<>(budget);
        double step = motes.size() / (double) budget;
        for (int i = 0; i < budget; i++) out.add(motes.get((int) (i * step)));
        return out;
    }

    static int mix(int a, int b, double t) {
        t = clamp01(t);
        int r = (int) Math.round(Shapes.lerp((a >> 16) & 255, (b >> 16) & 255, t));
        int g = (int) Math.round(Shapes.lerp((a >> 8) & 255, (b >> 8) & 255, t));
        int bl = (int) Math.round(Shapes.lerp(a & 255, b & 255, t));
        return (r << 16) | (g << 8) | bl;
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
    }
}
