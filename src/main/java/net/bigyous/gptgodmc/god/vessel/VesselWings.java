package net.bigyous.gptgodmc.god.vessel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.bigyous.gptgodmc.god.DemigodWings;
import net.bigyous.gptgodmc.god.vessel.VesselModel.Bone;
import net.bigyous.gptgodmc.god.vessel.VesselModel.Frame;

/**
 * The vessel's wings as block displays: the demigods' feathered wings, grander, riding the torso,
 * gold and white (angel) or black and blood red (demon). They come and go by themselves (away while
 * the vessel walks, spread when it floats, flies or is in a winged mood) unless the god chose
 * otherwise with vessel_wings. Coming in, they grow out of the back folded, then unfurl with a
 * sweep up and out and a burst of light or embers; going, they furl down the back and sink into it.
 * Spread, they breathe slowly while it hovers and beat hard in flight, a flap on every downstroke;
 * folded, they rise and fall with its breath.
 *
 * The motion and pose are pure ({@link Motion}, {@link #pose}, {@link #place}) for tests and
 * previews. Sounds go through {@link #CUES}: each has a stable id (gptgodmc:vessel.wings.*) the
 * client mod may replace with its own sound; vanilla sounds play meanwhile. The vessel calls
 * {@link #tick} every tick on the server thread.
 */
public final class VesselWings {
    /** Demigod blocks to vessel pixels: a demigod's figure is 1.8 blocks tall, the vessel 32 pixels. */
    static final float PX = VesselModel.HEIGHT_PX / 1.8f;
    /** A god's wings are grander than a demigod's. */
    static final float SPAN = 1.2f;
    /** Ticks between transform updates while the wings move. */
    static final int EVERY = 2;
    /** Ticks to grow out of the back, to unfurl (with its flourish), and to furl. */
    public static final int GROW_TICKS = 6, UNFURL_TICKS = 16, FURL_TICKS = 10;

    /** What the god chose for its wings. */
    public enum Choice {
        /** Come and go with what the body does (the default). */
        AUTO,
        /** Spread, whatever it does. */
        SPREAD,
        /** Folded down the back. */
        FOLD,
        /** Gone. */
        HIDE;

        public static Choice parse(String s) {
            if (s == null) return null;
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "auto", "default" -> AUTO;
                case "spread", "open", "out" -> SPREAD;
                case "fold", "folded", "close" -> FOLD;
                case "hide", "hidden", "away", "none" -> HIDE;
                default -> null;
            };
        }
    }

    /** What the wings should be doing now. */
    public enum Want {
        HIDDEN, FOLDED, SPREAD
    }

    /** A moment worth a sound and a burst of particles. */
    public enum Cue {
        NONE, APPEAR, UNFURL, FURL, VANISH, BEAT;

        /** The sound id the client mod may provide, e.g. gptgodmc:vessel.wings.unfurl.angel. */
        public String soundId(DemigodWings.Style style) {
            return "gptgodmc:vessel.wings." + name().toLowerCase(Locale.ROOT) + "."
                    + (style == DemigodWings.Style.DEMON ? "demon" : "angel");
        }
    }

    /** Looks whose wings, out of a winged mood, are a demon's. */
    static final Set<String> DEMONIC_LOOKS = Set.of("shade", "infernal", "underworld", "storm", "plague", "titan",
            "abyss", "jackal");

    // ---- Rules (pure) ------------------------------------------------------------------------------

    /**
     * What the wings should do. Left to themselves (AUTO) they spread in a winged mood (exalted,
     * wrathful), when the body floats (air: 0 on the ground to 1 aloft) or flies, and are away while it
     * walks or stands.
     */
    public static Want want(Choice choice, boolean allowed, VesselMood.Wings moodWings, float air, float flight) {
        if (!allowed) return Want.HIDDEN;
        return switch (choice == null ? Choice.AUTO : choice) {
            case SPREAD -> Want.SPREAD;
            case FOLD -> Want.FOLDED;
            case HIDE -> Want.HIDDEN;
            case AUTO -> (moodWings != null && moodWings != VesselMood.Wings.NONE) || air > 0.5f || flight > 0.3f
                    ? Want.SPREAD : Want.HIDDEN;
        };
    }

    /** The wings a mood brings, else the look's own: a demon's for the dark looks, an angel's otherwise. */
    public static DemigodWings.Style style(VesselMood.Wings moodWings, String look) {
        if (moodWings == VesselMood.Wings.ANGEL) return DemigodWings.Style.ANGEL;
        if (moodWings == VesselMood.Wings.DEMON) return DemigodWings.Style.DEMON;
        return look != null && DEMONIC_LOOKS.contains(look) ? DemigodWings.Style.DEMON : DemigodWings.Style.ANGEL;
    }

    /** The wing style a mood brings, or null for none. */
    public static DemigodWings.Style style(VesselMood.Wings wings) {
        return wings == null || wings == VesselMood.Wings.NONE ? null : style(wings, null);
    }

    /**
     * The wings' state from tick to tick: how far they have grown out of the back (grow), how far they
     * are spread (open), and the unfurling flourish. {@link #step} moves it one tick toward a want and
     * says which moment, if any, just began.
     */
    public static final class Motion {
        public float grow, open;
        /** Ticks into the unfurl flourish, or -1. */
        public int unfurl = -1;
        private boolean opening, closing;

        public Cue step(Want want) {
            Cue cue = Cue.NONE;
            boolean shown = want != Want.HIDDEN;
            // Hiding folds first, then sinks into the back.
            float growGoal = shown || open > 0.02f ? 1 : 0;
            float openGoal = want == Want.SPREAD && grow >= 0.6f ? 1 : 0;
            if (grow == 0 && growGoal > 0) cue = Cue.APPEAR;
            float g = grow;
            grow = toward(grow, growGoal, 1f / GROW_TICKS);
            if (g > 0 && grow == 0) cue = Cue.VANISH;
            if (openGoal > open && !opening) {
                opening = true;
                closing = false;
                unfurl = 0;
                cue = Cue.UNFURL;
            } else if (openGoal < open && !closing) {
                closing = true;
                opening = false;
                unfurl = -1;
                cue = Cue.FURL;
            }
            open = toward(open, openGoal, openGoal > open ? 1f / (UNFURL_TICKS * 0.75f) : 1f / FURL_TICKS);
            if (open == openGoal) {
                opening = false;
                closing = false;
            }
            if (unfurl >= 0 && ++unfurl > UNFURL_TICKS) unfurl = -1;
            return cue;
        }

        /** The flourish, 0..1..0 over the unfurl. */
        public float flourish() {
            return unfurl < 0 ? 0 : (float) Math.sin(Math.PI * unfurl / UNFURL_TICKS);
        }

        /** Nothing to show: fully sunk into the back. */
        public boolean gone() {
            return grow <= 0;
        }

        private static float toward(float v, float goal, float step) {
            return goal > v ? Math.min(goal, v + step) : Math.max(goal, v - step);
        }
    }

    // ---- Pose (pure) --------------------------------------------------------------------------------

    /**
     * The wings' pose. Folded they rise and fall with the breath; spread they beat slowly while it
     * hovers, deep and quick in flight; unfurling they sweep high and forward, then settle.
     */
    public static DemigodWings.Pose pose(Motion m, float time, float flight, DemigodWings.Style style) {
        float rate = (style == DemigodWings.Style.DEMON ? 0.13f : 0.09f) * (1 + 1.6f * flight);
        float s = (float) Math.sin(time * rate);
        float amp = 0.18f + 0.4f * flight;
        float o = smooth(m.open);
        float f = m.flourish();
        float breath = 0.04f * (float) Math.sin(time * 0.07f);
        float raise = lerp(0.42f + breath, 0.3f + amp * s, o) + 0.6f * f;
        float sweep = lerp(1.32f, 0.4f - 0.1f * s - 0.12f * flight, o) - 0.45f * f;
        return new DemigodWings.Pose(Math.min(1, m.open + 0.25f * f), raise, sweep, 0, 0);
    }

    /** The older form: a pose for a given spread, without growing or flourish. */
    public static DemigodWings.Pose pose(float open, float time, float flight, DemigodWings.Style style) {
        Motion m = new Motion();
        m.grow = 1;
        m.open = open;
        return pose(m, time, flight, style);
    }

    /** The wing boxes in the vessel's body space (blocks at this unit), following the torso. */
    public static List<DemigodWings.Piece> place(DemigodWings.Pose pose, DemigodWings.Style style, Frame torso,
            float unit) {
        return place(pose, style, torso, unit, 1);
    }

    /** The same, grown this far (0..1) out of the root on the back. */
    public static List<DemigodWings.Piece> place(DemigodWings.Pose pose, DemigodWings.Style style, Frame torso,
            float unit, float grow) {
        Vector3f root = DemigodWings.ROOT;
        float g = Math.max(0.02f, smooth(grow));
        List<DemigodWings.Piece> out = new ArrayList<>(DemigodWings.WING_PIECES);
        for (DemigodWings.Piece p : DemigodWings.wings(pose, style)) {
            // Grown around the root, in pixels, then into the torso's space (it pivots at the waist).
            Vector3f q = new Vector3f(p.center()).sub(root).mul(SPAN * g).add(root).mul(PX).sub(0, 12, 0);
            Vector3f c = torso.rot().transform(q).add(torso.pos()).mul(unit);
            Quaternionf r = new Quaternionf(torso.rot()).mul(p.rot());
            out.add(new DemigodWings.Piece(p.kind(), p.material(), c,
                    new Vector3f(p.size()).mul(SPAN * PX * unit * g), r));
        }
        return out;
    }

    /** The far end of each flight feather (body space), where light and embers fly off. */
    static List<Vector3f> tips(List<DemigodWings.Piece> pieces) {
        List<Vector3f> out = new ArrayList<>();
        for (DemigodWings.Piece p : pieces) {
            if (p.kind() != DemigodWings.Kind.PRIMARY) continue;
            out.add(p.rot().transform(new Vector3f(0, p.size().y / 2, 0)).add(p.center()));
        }
        return out;
    }

    // ---- In the world ------------------------------------------------------------------------------

    private final List<BlockDisplay> parts = new ArrayList<>();
    private final Transformation[] sent = new Transformation[DemigodWings.WING_PIECES];
    private final Motion motion = new Motion();
    private Choice choice = Choice.AUTO;
    private DemigodWings.Style style;
    private Color glow;
    private float lastBeat;

    public Choice choice() {
        return choice;
    }

    public void choose(Choice c) {
        choice = c == null ? Choice.AUTO : c;
    }

    /** Whether any wings are showing (or still going away). */
    public boolean showing() {
        return !parts.isEmpty();
    }

    /**
     * Brings the wings in or out, animates them and plays their moments. allowed is false in a true
     * form or when the server turned wings off; glow is the outline color, or null for none.
     */
    void tick(Consumer<Display> setup, Location body, Map<Bone, Frame> frames, float unit, boolean allowed,
            VesselMood.Wings moodWings, String look, float air, float flight, float time, long ticks, Color glow) {
        Want want = want(choice, allowed, moodWings, air, flight);
        if (want == Want.HIDDEN && parts.isEmpty() && motion.gone()) return;
        if (!parts.isEmpty() && (!parts.get(0).isValid() || !parts.get(0).getWorld().equals(body.getWorld()))) {
            remove();
        }
        DemigodWings.Style wanted = style(moodWings, look);
        Cue cue = motion.step(want);
        if (parts.isEmpty()) {
            if (motion.gone()) return;
            style = wanted;
            spawn(setup, body, frames, unit, time, flight, glow);
        } else if (wanted != style && want != Want.HIDDEN) {
            restyle(wanted);
            motion.unfurl = 0;
            cue = Cue.UNFURL;
        }
        if (motion.gone()) {
            effects(Cue.VANISH, body, frames, unit, time, flight);
            remove();
            return;
        }
        if (!Objects.equals(glow, this.glow)) {
            this.glow = glow;
            for (BlockDisplay d : parts) {
                d.setGlowing(glow != null);
                if (glow != null) d.setGlowColorOverride(glow);
            }
        }
        // A flap on every downstroke while spread and aloft.
        float beat = (float) Math.sin(time * (style == DemigodWings.Style.DEMON ? 0.13f : 0.09f) * (1 + 1.6f * flight));
        if (cue == Cue.NONE && motion.open > 0.9f && (air > 0.5f || flight > 0.3f) && lastBeat > 0 && beat <= 0) {
            cue = Cue.BEAT;
        }
        lastBeat = beat;
        boolean moving = motion.open > 0 && motion.open < 1 || motion.grow < 1 || motion.unfurl >= 0;
        if (cue != Cue.NONE) effects(cue, body, frames, unit, time, flight);
        if (ticks % EVERY != 0 && !moving) return;
        List<DemigodWings.Piece> pieces = current(frames, unit, time, flight);
        for (int i = 0; i < pieces.size() && i < parts.size(); i++) {
            Transformation t = DemigodWings.transform(pieces.get(i), 0);
            if (!DemigodWings.differs(t, sent[i])) continue;
            BlockDisplay d = parts.get(i);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(moving ? 1 : EVERY);
            d.setTransformation(t);
            sent[i] = t;
        }
        // In flight, now and then a feather of light (or an ember) drifts off a wingtip.
        if (flight > 0.3f && motion.open > 0.9f && ticks % 5 == 0) {
            List<Vector3f> tips = tips(pieces);
            Location at = world(body, tips.get((int) (ticks / 5 % tips.size())));
            if (style == DemigodWings.Style.DEMON) {
                body.getWorld().spawnParticle(Particle.SMOKE, at, 1, 0.05, 0.05, 0.05, 0.01);
            } else {
                body.getWorld().spawnParticle(Particle.END_ROD, at, 1, 0.05, 0.05, 0.05, 0.005);
            }
        }
    }

    private List<DemigodWings.Piece> current(Map<Bone, Frame> frames, float unit, float time, float flight) {
        return place(pose(motion, time, flight, style), style, frames.get(Bone.TORSO), unit, motion.grow);
    }

    private void spawn(Consumer<Display> setup, Location body, Map<Bone, Frame> frames, float unit, float time,
            float flight, Color glow) {
        this.glow = glow;
        List<DemigodWings.Piece> pieces = current(frames, unit, time, flight);
        for (int i = 0; i < pieces.size(); i++) {
            DemigodWings.Piece p = pieces.get(i);
            Transformation t = DemigodWings.transform(p, 0);
            sent[i] = t;
            parts.add(body.getWorld().spawn(body, BlockDisplay.class, b -> {
                setup.accept(b);
                b.setBlock(p.material().createBlockData());
                b.setTransformation(t);
                if (glow != null) {
                    b.setGlowing(true);
                    b.setGlowColorOverride(glow);
                }
            }));
        }
    }

    /** Swaps every piece's block for another style's (angel to demon), keeping the pose. */
    private void restyle(DemigodWings.Style to) {
        style = to;
        List<DemigodWings.Piece> pieces = DemigodWings.wings(new DemigodWings.Pose(1, 0, 0, 0, 0), to);
        for (int i = 0; i < pieces.size() && i < parts.size(); i++) {
            parts.get(i).setBlock(pieces.get(i).material().createBlockData());
        }
    }

    // ---- Moments ------------------------------------------------------------------------------------

    /** A sound of the vessel's wings: its id for the client mod, and the vanilla sounds that stand in. */
    record Voice(Sound sound, float volume, float pitch) {
    }

    /** The vanilla sounds of each moment (angel, demon). */
    static List<Voice> voices(Cue cue, DemigodWings.Style style, float flight) {
        boolean demon = style == DemigodWings.Style.DEMON;
        return switch (cue) {
            case APPEAR -> demon
                    ? List.of(new Voice(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 0.6f),
                            new Voice(Sound.ITEM_FIRECHARGE_USE, 0.6f, 0.5f))
                    : List.of(new Voice(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 1.4f),
                            new Voice(Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.8f));
            case UNFURL -> demon
                    ? List.of(new Voice(Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1.4f, 0.6f),
                            new Voice(Sound.ENTITY_ENDER_DRAGON_FLAP, 1.4f, 0.55f),
                            new Voice(Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.5f))
                    : List.of(new Voice(Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1.4f, 1.2f),
                            new Voice(Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 1.3f),
                            new Voice(Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.7f));
            case FURL -> List.of(new Voice(Sound.ENTITY_PHANTOM_FLAP, 1.0f, demon ? 0.6f : 1.1f),
                    new Voice(Sound.ITEM_ARMOR_EQUIP_ELYTRA, 0.9f, demon ? 0.5f : 0.9f));
            case VANISH -> demon
                    ? List.of(new Voice(Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 0.6f))
                    : List.of(new Voice(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.6f));
            case BEAT -> List.of(new Voice(Sound.ENTITY_ENDER_DRAGON_FLAP, 0.45f + 0.5f * flight,
                    demon ? 0.7f : 1.2f));
            case NONE -> List.of();
        };
    }

    private void effects(Cue cue, Location body, Map<Bone, Frame> frames, float unit, float time, float flight) {
        World w = body.getWorld();
        Location back = world(body, place(new DemigodWings.Pose(0, 0.42f, 1.32f, 0, 0), style,
                frames.get(Bone.TORSO), unit, 0.02f).get(0).center());
        // Players with the client mod hear its own wing sounds; everyone else these vanilla stand-ins.
        java.util.Set<org.bukkit.entity.Player> mod = net.bigyous.gptgodmc.client.ModFx.sound(back, cue.soundId(style),
                cue == Cue.BEAT ? 0.5f + 0.6f * flight : 1.2f, 1f, 48);
        for (Voice v : voices(cue, style, flight)) {
            if (mod.isEmpty()) {
                w.playSound(back, v.sound(), v.volume(), v.pitch());
                continue;
            }
            for (org.bukkit.entity.Player p : w.getPlayers()) {
                if (!mod.contains(p) && p.getLocation().distanceSquared(back) < 64 * 64) p.playSound(back, v.sound(), v.volume(), v.pitch());
            }
        }
        boolean demon = style == DemigodWings.Style.DEMON;
        Particle.DustOptions dust = new Particle.DustOptions(demon ? Color.fromRGB(0xC0141A) : Color.fromRGB(0xFFF2C0),
                1.4f);
        double r = 6 * unit;
        switch (cue) {
            case APPEAR -> {
                w.spawnParticle(Particle.DUST, back, 16, r * 0.4, r * 0.4, r * 0.4, 0, dust);
                w.spawnParticle(demon ? Particle.LARGE_SMOKE : Particle.END_ROD, back, 10, r * 0.3, r * 0.3, r * 0.3,
                        0.02);
            }
            case UNFURL -> {
                // A burst from every wingtip of the opened wings, flung outward.
                Motion open = new Motion();
                open.grow = 1;
                open.open = 1;
                for (Vector3f tip : tips(place(pose(open, time, flight, style), style, frames.get(Bone.TORSO), unit))) {
                    Location at = world(body, tip);
                    w.spawnParticle(Particle.DUST, at, 6, r * 0.25, r * 0.25, r * 0.25, 0, dust);
                    if (demon) {
                        w.spawnParticle(Particle.FLAME, at, 2, r * 0.2, r * 0.2, r * 0.2, 0.02);
                        w.spawnParticle(Particle.LARGE_SMOKE, at, 2, r * 0.2, r * 0.2, r * 0.2, 0.02);
                    } else {
                        w.spawnParticle(Particle.END_ROD, at, 3, r * 0.2, r * 0.2, r * 0.2, 0.04);
                    }
                }
                w.spawnParticle(demon ? Particle.SMOKE : Particle.FIREWORK, back, 24, r * 0.5, r * 0.5, r * 0.5, 0.08);
            }
            case FURL -> w.spawnParticle(demon ? Particle.ASH : Particle.WHITE_ASH, back, 30, r, r, r * 0.5, 0.01);
            case VANISH -> w.spawnParticle(Particle.DUST, back, 12, r * 0.25, r * 0.25, r * 0.25, 0, dust);
            default -> {
            }
        }
    }

    /** A body-space point (blocks) of a part standing at body (with its yaw) in the world. */
    private static Location world(Location body, Vector3f p) {
        Vector3f v = new Quaternionf().rotationY((float) Math.toRadians(-body.getYaw())).transform(new Vector3f(p));
        return new Location(body.getWorld(), body.getX() + v.x, body.getY() + v.y, body.getZ() + v.z);
    }

    /** Moves the wings with the body (the vessel calls this when it moves its other parts). */
    void teleport(Location body) {
        for (BlockDisplay d : parts) {
            if (d.isValid()) d.teleport(body);
        }
    }

    List<Entity> entities() {
        return new ArrayList<>(parts);
    }

    /** Removes the wings at once. */
    void remove() {
        parts.forEach(Entity::remove);
        forget();
    }

    /** Drops the references (the vessel removes the entities itself); the god's choice is kept. */
    void forget() {
        parts.clear();
        java.util.Arrays.fill(sent, null);
        motion.grow = 0;
        motion.open = 0;
        motion.unfurl = -1;
        motion.opening = false;
        motion.closing = false;
        style = null;
        glow = null;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smooth(float t) {
        float c = Math.max(0, Math.min(1, t));
        return c * c * (3 - 2 * c);
    }
}
