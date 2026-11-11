package net.bigyous.gptgodmc.god.vessel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Journal;
import net.bigyous.gptgodmc.god.Perception;
import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Rites;
import net.bigyous.gptgodmc.god.Speech;
import net.bigyous.gptgodmc.god.vessel.VesselLook.Slot;
import net.bigyous.gptgodmc.god.vessel.VesselModel.Bone;
import net.bigyous.gptgodmc.god.vessel.VesselModel.Frame;
import net.bigyous.gptgodmc.god.vessel.VesselModel.Motion;
import net.bigyous.gptgodmc.god.vessel.VesselModel.Part;
import net.bigyous.gptgodmc.god.vessel.VesselModel.Pose;
import net.kyori.adventure.text.Component;

/**
 * The god's physical form: a tall figure with player-skin proportions, built from block displays
 * on a jointed rig (see {@link VesselModel}), wrapped in a glowing outline and a drifting aura.
 * It walks, flies, levitates, breathes, turns its head to watch mortals and gestures, and its
 * mood ({@link VesselMood}) changes its posture, height, glow, wings and particles. It is only a
 * puppet — the god perceives everything whether or not the vessel exists. Server thread only.
 */
public final class Vessel {
    public static final String TAG = "vessel";
    static final String OWNER_TAG = "vessel_god";
    /** How high it floats when standing still (blocks). */
    private static final double HOVER = 0.3;
    /** The highest it may be ordered to float (blocks). */
    public static final int MAX_ALTITUDE = 40;
    /** Farthest it flies before simply reappearing at the destination (blocks). */
    private static final double MAX_FLIGHT = 160;

    private enum Mode {
        IDLE, WALK, FLY, FOLLOW
    }

    /**
     * Who sees a piece. With custom textures every textured part exists twice: SKIN (an ItemDisplay
     * wearing the resource pack's model) for players who loaded the pack, BLOCK (the block version)
     * for everyone else. SHARED pieces (horns, or everything with textures off) are seen by all.
     */
    private enum Kind {
        SHARED, BLOCK, SKIN
    }

    private record Piece(Display entity, Part part, Kind kind) {
        Transformation at(Map<Bone, Frame> frames, float unit, float grow) {
            Transformation t = VesselModel.transform(part, frames.get(part.bone()), unit, grow);
            return kind == Kind.SKIN ? VesselModel.toItem(t) : t;
        }
    }

    private final List<Piece> pieces = new ArrayList<>();
    private final Map<Bone, Frame> sentFrames = new EnumMap<>(Bone.class);
    private final Map<Bone, Long> sentAt = new EnumMap<>(Bone.class);
    private final Pose pose = new Pose();
    private Interaction hitbox;
    private TextDisplay bubble;
    /** The god's name above its head (vessel.nameplate), or null. */
    private TextDisplay nameplate;
    private String nameplateText;
    private long bubbleUntil;
    /** "• • •" above its head while the god is thinking (hidden while it speaks). */
    private TextDisplay dots;
    private volatile boolean thinking;
    /** The ground point between its feet (it may float above it). */
    private Location pos;
    private float yaw;
    private Mode mode = Mode.IDLE;
    private Location goal;
    private UUID followTarget;
    private UUID lookTarget;
    private String gesture = "idle";
    private long gestureUntil;
    private long gestureStart;
    private long lastOrderAt;
    /** Game ticks since the plugin started (advances by 2 per update). */
    private long ticks;
    private float walk;
    private float phase;
    private float flight;
    /** Blocks above its usual hover, now and as ordered. */
    private double lift;
    private double altitude;
    /** Its feet's height this update. */
    private double baseY;
    private int growTicks;
    private BukkitTask task;
    private double scale = VesselModel.scaleForSize(VesselModel.DEFAULT_SIZE);
    /** The size the god chose for itself (player heights), or null for the server's vessel.size. */
    private Double chosenSize;
    private VesselLook look = VesselLook.shade();
    private VesselMood mood = VesselMood.CALM;
    private long moodUntil;
    /** The mood shown now comes from the god's heart (its lasting feelings), not a timed choice. */
    private boolean fromHeart;
    private long lastHeartCheck;
    /** The god chose the current mood itself, so automatic reactions leave it alone. */
    private boolean moodChosen;
    /** The god this body belongs to; everything it does on its own timer runs as that god. */
    private final Realm realm;
    /**
     * Set while the arrival storm is gathering and the body has not formed yet; the storm's climax
     * only forms the body if this is still the same token (a vanish, a shutdown or a new storm
     * elsewhere replaces or clears it).
     */
    private Object arrival;
    /** The rift it is tearing its way in through (vessel.arrival: rift), or null. */
    private RiftShow rift;
    /** Where that rift stands: the body forms there and steps out to {@link #pos}. */
    private Location arrivalFrom;
    /** The spot it is stepping out of its rift toward (walked slowly, once the body has condensed). */
    private Location emergeGoal;
    private long emergeAt;
    /** How fast it steps out of its rift, as a share of its walking pace. */
    private static final double EMERGE_PACE = 0.35;
    /**
     * Set while it is walking back into a rift to leave (vessel_vanish): the god already counts as formless,
     * but its body is still there until the rift swallows it at the climax (if this is still the same token).
     */
    private Object departing;
    private RiftShow departRift;
    /** A gesture and words ordered while the body was still forming, played once it stands there. */
    private String pendingGesture;
    private String pendingSpeech;
    private long pendingSpeechAt;
    /** The god's true form while it is revealed (the body is hidden meanwhile), else null. */
    private TrueFormShow trueForm;
    /** Display-entity wings, worn in moods that bring them. */
    private final VesselWings wingRig = new VesselWings();

    /** The god's choice for its wings: auto (they come and go), spread, fold or hide. */
    public VesselWings.Choice wingChoice() {
        return wingRig.choice();
    }

    public void setWingChoice(VesselWings.Choice c) {
        wingRig.choose(c);
    }

    /** Whether its wings are out (spread, folded, or still going away). */
    public boolean wingsShowing() {
        return wingRig.showing();
    }

    public Vessel(Realm realm) {
        this.realm = realm;
    }

    // ---- Public API ----------------------------------------------------------------------------

    /**
     * Whether the god has a presence in the world: its body, the storm in which the body is forming
     * (treated as already standing at that spot), or its true form.
     */
    public boolean isManifested() {
        return pos != null && departing == null && (!pieces.isEmpty() || arrival != null || trueForm != null);
    }

    /** Its body is walking back into a rift to leave the world. */
    public boolean isDeparting() {
        return departing != null;
    }

    /** Whether the body itself stands in the world (not still forming, not hidden by the true form). */
    public boolean hasBody() {
        return pos != null && !pieces.isEmpty();
    }

    /** The arrival storm is gathering and the body forms at its climax. */
    public boolean isMaterializing() {
        return pos != null && arrival != null;
    }

    public boolean inTrueForm() {
        return trueForm != null;
    }

    /** The ground point under the vessel (or under its true form, or where it is forming), or null. */
    public Location location() {
        if (!isManifested()) return null;
        Location l = pos.clone();
        l.setYaw(yaw);
        return l;
    }

    public double scale() {
        return scale;
    }

    /** Its size in multiples of a player's height: the god's own choice, else the server's setting. */
    public double size() {
        double wanted = chosenSize != null ? chosenSize : GodSettings.vesselSize();
        return Math.max(0.5, Math.min(GodSettings.vesselMaxSize(), wanted));
    }

    public boolean sizeChosen() {
        return chosenSize != null;
    }

    /**
     * Sets how large the body is, in player heights (null returns to the server's setting). A body
     * that stands in the world grows or shrinks smoothly where it is; otherwise it applies when the
     * body next forms.
     */
    public void setSize(Double playerHeights) {
        chosenSize = playerHeights;
        double wanted = VesselModel.scaleForSize(size());
        if (!hasBody() || Math.abs(wanted - scale) < 1e-4) {
            if (!hasBody()) scale = wanted;
            return;
        }
        scale = wanted;
        growTicks = 16; // every bone is re-sent, interpolated over this many ticks
        fitHitbox();
        pos.getWorld().spawnParticle(Particle.REVERSE_PORTAL, chestPoint(), 60, 0.4 * scale, 1.0 * scale,
                0.4 * scale, 0.05);
    }

    /** Height of its eyes above {@link #location()}, including how high it floats. */
    public double eyeHeight() {
        if (trueForm != null) return trueForm.centre().getY() - pos.getY();
        double feet = hasBody() ? bodyY() - pos.getY() : HOVER;
        return VesselModel.EYE_PX * unit() + feet;
    }

    public VesselMood mood() {
        return mood;
    }

    public String describe() {
        if (departing != null) {
            return "Formless — your body is stepping back through a rift and will be gone in a moment; mortals still "
                    + "see it go. Use vessel_go to take form again.";
        }
        if (!isManifested()) {
            return "Formless — you are an unseen presence, everywhere at once; mortals see no body. Use vessel_go "
                    + "to take form somewhere (" + (GodSettings.vesselArrival().equals("rift")
                            ? "a rift tears open in the air and you step out of it" : "you condense out of a particle storm")
                    + ").";
        }
        if (trueForm != null) {
            return trueForm.describe() + moodNote();
        }
        String doing = switch (mode) {
            case FOLLOW -> {
                Player p = followTarget == null ? null : Bukkit.getPlayer(followTarget);
                yield p == null ? "drifting" : "following " + p.getName();
            }
            case WALK -> "walking to " + describe(goal);
            case FLY -> "flying to " + describe(goal);
            default -> "standing";
        };
        if (arrival != null) {
            StringBuilder sb = new StringBuilder("Materializing at ").append(describe(pos))
                    .append(rift != null && !rift.done()
                            ? ": a rift is tearing open in the air there; your body forms inside it and steps out in a moment"
                            : ": a storm is gathering there and your body forms out of it in a moment");
            if (mode != Mode.IDLE) sb.append(", then ").append(doing);
            if (pendingGesture != null) sb.append(", then gesture ").append(pendingGesture);
            return sb.append(moodNote()).append('.').toString();
        }
        StringBuilder sb = new StringBuilder("Manifested at ").append(describe(pos)).append(", facing ")
                .append(Perception.facing(yaw)).append(", ").append(doing);
        if (lift > 0.8) sb.append(String.format(Locale.ROOT, ", floating %.0f blocks up", lift));
        if (Math.abs(size() - VesselModel.DEFAULT_SIZE) > 0.01) {
            sb.append(", size ").append(VesselModel.describeSize(size()));
        }
        sb.append(moodNote());
        if (!gesture.equals("idle")) sb.append(", gesture ").append(gesture);
        if (dance != null) sb.append(", performing your animation ").append(dance.name());
        return sb.append('.').toString();
    }

    private String moodNote() {
        if (mood == VesselMood.CALM) return "";
        String s = ", mood " + mood.id() + (fromHeart ? " (from your heart)" : "");
        if (moodUntil > 0) s += " (" + Math.max(1, (moodUntil - System.currentTimeMillis()) / 1000) + "s left)";
        return s;
    }

    /**
     * Takes form at a spot. From formlessness the body condenses out of the arrival storm (a rite) and
     * appears at its climax; until then the vessel counts as standing there, and gestures and words
     * ordered meanwhile wait for the body. An existing body re-forms there instead.
     */
    public void manifest(Location at) {
        if (!GodSettings.vesselEnabled()) return;
        if (net.bigyous.gptgodmc.god.Godslayer.isSlain(realm)) return; // a slain god has no body
        if (realm.dormant()) return; // turned off: no body until turned on
        if (trueForm != null) return; // the true form holds its place until it folds away
        if (departing != null) abortDeparture(); // called back while leaving: it forms anew
        Location spot = safeSpot(groundSpot(at));
        if (arrival != null) {
            if (spot.getWorld().equals(pos.getWorld()) && spot.distanceSquared(pos) < 16) {
                pos = spot; // close enough: the storm already gathering there will do
            } else {
                materialize(spot, at.getYaw()); // a new storm elsewhere; the old one forms nothing
            }
            return;
        }
        if (hasBody()) {
            relocate(spot);
            return;
        }
        materialize(spot, at.getYaw());
    }

    private void materialize(Location spot, float facing) {
        net.bigyous.gptgodmc.client.ModFx.arrival(spot);
        closeRift();
        arrivalFrom = null;
        emergeGoal = null;
        pos = spot;
        yaw = facing;
        ensureTicking();
        if (!GodSettings.ritesEnabled()) {
            arrival = null;
            formBody();
            arrivalFlash();
            return;
        }
        Object token = new Object();
        arrival = token;
        Runnable atClimax = () -> realm.run(() -> arrive(token));
        if (GodSettings.vesselArrival().equals("rift")) {
            // A rift tears open just behind the spot, facing whoever is near; the body forms in it and steps out.
            yaw = facingNearest(spot, facing);
            double body = size() * 1.8;
            arrivalFrom = riftSpot(spot, yaw, body);
            Journal.add(Journal.Kind.INFO, "Vessel tearing its way in at " + describe(pos));
            rift = RiftShow.open(arrivalFrom, yaw, body, Rift.Palette.of(currentLook().outline().asRGB()), atClimax);
            return;
        }
        Journal.add(Journal.Kind.INFO, "Vessel materializing at " + describe(pos));
        // The storm brings its own sounds; the body appears at its climax (at once if it cannot be shown).
        boolean shown = Rites.play(Rites.Kind.ARRIVAL, spot.clone(), null, atClimax);
        if (!shown && hasBody()) arrivalFlash();
    }

    /** The storm's or the rift's climax: the body forms, unless the god vanished, moved on or the plugin stopped. */
    private void arrive(Object token) {
        if (arrival != token) return;
        arrival = null;
        Location from = arrivalFrom;
        arrivalFrom = null;
        if (pos == null || trueForm != null || !GPTGOD.get().isEnabled()) return;
        if (from == null || !from.getWorld().equals(pos.getWorld()) || from.distanceSquared(pos) < 0.04) {
            formBody();
            return;
        }
        // It condenses inside the rift, then steps out to where it was going to stand.
        Location to = pos;
        pos = from.clone();
        yaw = (float) Math.toDegrees(-Math.atan2(to.getX() - from.getX(), to.getZ() - from.getZ()));
        formBody();
        if (mode == Mode.IDLE) {
            goal = to;
            emergeGoal = to;
            emergeAt = ticks + Rift.STEP_DELAY;
            mode = Mode.WALK;
        }
    }

    /** Closes a rift still opening (its body never forms). */
    private void closeRift() {
        if (rift != null && !rift.done() && arrival != null) rift.stop();
        rift = null;
    }

    /** Which way to face on arrival: toward the nearest mortal close enough to see it, else the given way. */
    private float facingNearest(Location spot, float fallback) {
        Player best = null;
        double bestD = 128 * 128;
        for (Player p : spot.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            double d = p.getLocation().distanceSquared(spot);
            if (d < bestD && d > 0.25) {
                bestD = d;
                best = p;
            }
        }
        if (best == null) return fallback;
        Location e = best.getLocation();
        return (float) Math.toDegrees(-Math.atan2(e.getX() - spot.getX(), e.getZ() - spot.getZ()));
    }

    /**
     * Where the rift opens: a few steps behind the spot (on the same ground, with room for the body and
     * the way out clear), or the spot itself when there is no room behind it.
     */
    private Location riftSpot(Location spot, float facing, double body) {
        double r = Math.toRadians(facing);
        double fx = -Math.sin(r), fz = Math.cos(r);
        double back = Rift.stepBack(body);
        for (double k : new double[] { 1.0, 0.6 }) {
            double d = back * k;
            Location at = groundSpot(spot.clone().add(-fx * d, 0.5, -fz * d));
            if (Math.abs(at.getY() - spot.getY()) > 0.6) continue;
            at.setY(spot.getY());
            if (!phases() && at.clone().subtract(0, 1, 0).getBlock().isPassable()) continue; // no ground there
            Location half = spot.clone().add(-fx * d / 2, 0, -fz * d / 2);
            if (phases() || (fits(at) && fits(half))) return at;
        }
        return spot.clone();
    }

    private void formBody() {
        baseY = bodyY();
        spawnParts(true);
        ensureTicking();
        Journal.add(Journal.Kind.INFO, "Vessel manifested at " + describe(pos) + " (" + look.name() + ")");
        if (mood != VesselMood.CALM) moodShift();
        if (pendingGesture != null) {
            String g = pendingGesture;
            pendingGesture = null;
            gesture(g);
        }
        if (pendingSpeech != null) {
            String text = pendingSpeech;
            pendingSpeech = null;
            if (System.currentTimeMillis() - pendingSpeechAt < 10_000) showSpeech(text);
        }
    }

    /** The old burst of light, for when no rite plays (rites disabled or none could be shown). */
    private void arrivalFlash() {
        if (!hasBody()) return;
        World w = pos.getWorld();
        Location chest = pos.clone().add(0, 18 * unit(), 0);
        w.spawnParticle(Particle.REVERSE_PORTAL, chest, 160, 0.6, 1.2, 0.6, 0.08);
        w.spawnParticle(Particle.FLASH, chest, 1, Color.WHITE);
        w.playSound(pos, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.6f, 0.5f);
        w.playSound(pos, Sound.BLOCK_BEACON_ACTIVATE, 1.6f, 0.6f);
    }

    /**
     * Becomes formless. The body dissolves in the departure rite (gone at its climax); a storm still
     * gathering simply forms nothing; a revealed true form folds away first.
     */
    public void vanish() {
        pendingGesture = null;
        pendingSpeech = null;
        if (trueForm != null) {
            trueForm.fold(false);
            return;
        }
        if (arrival != null) {
            closeRift();
            arrival = null;
            arrivalFrom = null;
            despawn();
            clearRefs();
            Journal.add(Journal.Kind.INFO, "Vessel vanished before it had formed");
            return;
        }
        if (!hasBody()) return;
        if (departing != null) return; // already leaving
        if (GodSettings.ritesEnabled() && GPTGOD.get().isEnabled() && GodSettings.vesselArrival().equals("rift")
                && !net.bigyous.gptgodmc.god.Godslayer.isSlain(realm)) {
            depart();
            return;
        }
        World w = pos.getWorld();
        Location chest = chestPoint();
        Location ground = pos.clone();
        net.bigyous.gptgodmc.client.ModFx.departure(ground);
        if (GodSettings.ritesEnabled() && GPTGOD.get().isEnabled()) {
            List<Entity> doomed = shrink(4);
            clearRefs();
            boolean shown = Rites.play(Rites.Kind.DEPARTURE, chest, null, () -> doomed.forEach(Entity::remove));
            if (!shown) {
                doomed.forEach(Entity::remove);
                departureFlash(w, chest, ground);
            }
        } else {
            departureFlash(w, chest, ground);
            shrinkAway();
        }
        Journal.add(Journal.Kind.INFO, "Vessel vanished");
    }

    private static void departureFlash(World w, Location chest, Location ground) {
        w.spawnParticle(Particle.REVERSE_PORTAL, chest, 120, 0.5, 1.2, 0.5, 0.1);
        w.playSound(ground, Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.6f);
    }

    /**
     * Leaves through a rift: one tears open a few steps behind it, it turns and walks in at a slow pace,
     * and at the rift's climax the far side swallows it; the rift seals after it. The god counts as formless
     * from the start.
     */
    private void depart() {
        double body = size() * 1.8;
        Location spot = riftSpot(pos, yaw, body);
        double dist = Math.hypot(spot.getX() - pos.getX(), spot.getZ() - pos.getZ());
        double step = 0.26 * scale * EMERGE_PACE; // per run of the timer, every 2 ticks
        int walk = dist < 0.2 ? 0 : (int) Math.ceil(dist / step) * 2;
        Rift.Timeline when = Rift.departure(walk);
        Object token = new Object();
        departing = token;
        stopAnimation();
        gesture = "idle";
        followTarget = null;
        lookTarget = null;
        facingSince = 0;
        if (walk > 0) {
            goal = spot;
            emergeGoal = spot;
            emergeAt = ticks + Rift.DEPART_WALK;
            mode = Mode.WALK;
        } else {
            goal = null;
            mode = Mode.IDLE;
        }
        departRift = RiftShow.departure(spot, yaw + 180, body, Rift.Palette.of(look.outline().asRGB()), when,
                () -> realm.run(() -> swallowed(token)));
        Journal.add(Journal.Kind.INFO, "Vessel stepping back through a rift at " + describe(spot));
    }

    /** The departure's climax: the far side takes the body. */
    private void swallowed(Object token) {
        if (departing != token) return;
        departing = null;
        departRift = null;
        emergeGoal = null;
        if (!hasBody()) {
            clearRefs();
            return;
        }
        net.bigyous.gptgodmc.client.ModFx.departure(pos.clone());
        List<Entity> doomed = shrink(8);
        clearRefs();
        Bukkit.getScheduler().runTaskLater(GPTGOD.get(), () -> doomed.forEach(Entity::remove), 9L);
        Journal.add(Journal.Kind.INFO, "Vessel vanished into its rift");
    }

    /** Called back (or shown its true form) while leaving: the body and its rift go at once. */
    private void abortDeparture() {
        departing = null;
        if (departRift != null) departRift.stop();
        departRift = null;
        emergeGoal = null;
        despawn();
        clearRefs();
    }

    // ---- True form -----------------------------------------------------------------------------

    /**
     * Reveals the true form above a spot for this many seconds (warning included). The body, if any,
     * is hidden meanwhile and returns when the form folds away; from formlessness the god is formless
     * again afterwards.
     */
    public void revealTrueForm(Location where, int seconds, String godName) {
        revealTrueForm(where, seconds, godName, null, null);
    }

    /** As above, in one of its shapes and with what its gaze does (null = the hypercube / the server's setting). */
    public void revealTrueForm(Location where, int seconds, String godName, TrueForm.Form form, TrueForm.Gaze gaze) {
        if (trueForm != null) return;
        if (departing != null) abortDeparture(); // it was leaving: formless after the true form
        boolean hadBody = hasBody() || arrival != null;
        Location spot = groundSpot(where);
        closeRift();
        arrival = null;
        arrivalFrom = null;
        emergeGoal = null;
        pendingGesture = null;
        if (hasBody()) {
            pos.getWorld().spawnParticle(Particle.REVERSE_PORTAL, chestPoint(), 80, 0.5, 1.2, 0.5, 0.1);
        }
        despawn();
        pos = spot;
        mode = Mode.IDLE;
        goal = null;
        followTarget = null;
        lift = 0;
        flight = 0;
        trueForm = new TrueFormShow(spot, seconds, hadBody, godName, form, gaze);
        trueForm.start();
        net.bigyous.gptgodmc.client.ModFx.trueForm(spot);
        ensureTicking();
    }

    /** Folds the true form away early; the body returns if it stood there before. */
    public void withdrawTrueForm() {
        if (trueForm != null) trueForm.fold(true);
    }

    private void endTrueForm() {
        TrueFormShow done = trueForm;
        trueForm = null;
        if (done.returnsBody() && pos != null && GPTGOD.get().isEnabled() && GodSettings.vesselEnabled()) {
            if (bubble != null) {
                bubble.remove();
                bubble = null;
            }
            formBody();
            arrivalFlash();
        } else {
            despawn();
            clearRefs();
            Journal.add(Journal.Kind.INFO, "The true form folded away; the god is formless");
        }
    }

    /** how: teleport (appear at once), walk (stride there) or fly (soar there through the air). */
    public void goTo(Location target, String how) {
        if (trueForm != null) return; // it holds its place until it folds away
        lastOrderAt = System.currentTimeMillis();
        followTarget = null;
        if (!isManifested()) {
            manifest(target);
            mode = Mode.IDLE;
            return;
        }
        if (arrival != null && !"walk".equals(how) && !"fly".equals(how)) {
            manifest(target); // still forming: the storm gathers there instead
            mode = Mode.IDLE;
            return;
        }
        boolean sameWorld = target.getWorld().equals(pos.getWorld());
        double dist = sameWorld ? target.distance(pos) : Double.MAX_VALUE;
        if ("walk".equals(how) && dist <= 64) {
            goal = safeSpot(groundSpot(target));
            mode = Mode.WALK;
        } else if ("fly".equals(how) && dist <= MAX_FLIGHT) {
            goal = safeSpot(groundSpot(target));
            mode = Mode.FLY;
            pos.getWorld().playSound(pos, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 0.7f);
        } else {
            relocate(safeSpot(groundSpot(target)));
            mode = Mode.IDLE;
        }
    }

    public void goTo(Location target, boolean walkThere) {
        goTo(target, walkThere ? "walk" : "teleport");
    }

    /** How high to float above the ground from now on (0 = just above it). */
    public void setAltitude(double blocks) {
        altitude = Math.max(0, Math.min(MAX_ALTITUDE, blocks));
    }

    public double altitude() {
        return altitude;
    }

    public void follow(Player player) {
        if (trueForm != null) return;
        lastOrderAt = System.currentTimeMillis();
        if (!isManifested()) {
            manifest(besideSpot(player));
        }
        followTarget = player.getUniqueId();
        mode = Mode.FOLLOW;
    }

    public void stay() {
        lastOrderAt = System.currentTimeMillis();
        mode = Mode.IDLE;
        followTarget = null;
    }

    public void lookAt(Player player) {
        lookTarget = player == null ? null : player.getUniqueId();
    }

    /** When it last turned to face someone who spoke to it; for a while its whole body faces them. */
    private long facingSince;

    /** Turns its gaze and its whole body toward a mortal who spoke to it (for about ten seconds). */
    public void faceSpeaker(Player player) {
        lookAt(player);
        facingSince = player == null ? 0 : System.currentTimeMillis();
    }

    // ---- Animations the god writes itself (Choreo) ---------------------------------------------

    private Choreo.Anim dance;
    private long danceStart;
    private int danceTotal;
    private float danceLastAge = -1;
    private Location faceSpot;
    private long faceUntil;

    /** Plays an animation; a loop runs loopSeconds (once otherwise). Returns false when it has no limbs. */
    public boolean animate(Choreo.Anim a, int loopSeconds) {
        if (a == null || trueForm != null || !isManifested()) return false;
        dance = a;
        danceStart = ticks;
        danceLastAge = -1;
        danceTotal = a.loop() ? Math.max(a.length(), Math.min(Choreo.MAX_LOOP_SECONDS, loopSeconds) * 20)
                : a.passTicks();
        if (!gesture.equals("speak")) gesture = "idle";
        return true;
    }

    public void stopAnimation() {
        if (dance == null) return;
        // Ends with a short fade instead of snapping back.
        long age = ticks - danceStart;
        danceTotal = (int) Math.min(danceTotal, age + Choreo.FADE_OUT);
    }

    /** The animation playing now, or null. */
    public String animation() {
        return dance == null ? null : dance.name();
    }

    /** Turns to face a spot for a while (working on a block, watching the sunset). */
    public void face(Location spot, int seconds) {
        faceSpot = spot == null ? null : spot.clone();
        faceUntil = System.currentTimeMillis() + seconds * 1000L;
    }

    private void danceCues(Map<Bone, Frame> frames, float age) {
        for (Choreo.Cue c : Choreo.cuesBetween(dance, danceLastAge, age)) {
            Location at = switch (c.at()) {
                case "rhand" -> bodyPoint(frames, Bone.RIGHT_ARM, -1, -10, 0);
                case "lhand" -> bodyPoint(frames, Bone.LEFT_ARM, 1, -10, 0);
                case "hands" -> midpoint(bodyPoint(frames, Bone.LEFT_ARM, 1, -10, 0),
                        bodyPoint(frames, Bone.RIGHT_ARM, -1, -10, 0));
                case "head" -> bodyPoint(frames, Bone.HEAD, 0, 4, 0);
                case "feet" -> bodyPoint(frames, Bone.BODY, 0, 0.5f, 0);
                case "above" -> bodyPoint(frames, Bone.HEAD, 0, 14, 0);
                default -> bodyPoint(frames, Bone.TORSO, 0, 6, 0);
            };
            World w = pos.getWorld();
            if (c.particle() != null) {
                Particle p = net.bigyous.gptgodmc.god.Spectacle.particle(c.particle());
                if (p != null && p.getDataType() == Void.class) {
                    double spread = 0.25 * scale;
                    w.spawnParticle(p, at, c.count(), spread, spread, spread, 0.02);
                }
            }
            if (c.sound() != null) {
                w.playSound(at, "minecraft:" + c.sound(), org.bukkit.SoundCategory.NEUTRAL, c.volume(), c.pitch());
            }
        }
        danceLastAge = age;
    }

    /** One of {@link VesselModel#GESTURES}; unknown names fall back to idle. */
    public void gesture(String name) {
        String g = name == null ? "idle" : name.toLowerCase(Locale.ROOT);
        if (trueForm != null) return; // the true form has no limbs
        if (arrival != null) {
            pendingGesture = VesselModel.GESTURES.contains(g) ? g : null; // performed once the body has formed
            return;
        }
        if (!g.equals("speak")) stopAnimation();
        gesture(g, VesselModel.GESTURE_TICKS.getOrDefault(g, 70) * 50L);
    }

    private void gesture(String name, long millis) {
        String g = name == null ? "idle" : name.toLowerCase(Locale.ROOT);
        gesture = VesselModel.GESTURES.contains(g) ? g : "idle";
        gestureStart = ticks;
        gestureUntil = System.currentTimeMillis() + millis;
        if (!hasBody()) return;
        World w = pos.getWorld();
        switch (gesture) {
            case "speak", "nod", "shake_head", "laugh", "wave", "idle" -> {
            }
            case "meditate" -> w.playSound(pos, Sound.BLOCK_BEACON_AMBIENT, 1.2f, 0.8f);
            case "summon" -> w.playSound(pos, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.4f, 0.6f);
            default -> w.playSound(pos, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 0.7f);
        }
    }

    /** The god sets its mood. seconds <= 0 keeps it until changed. */
    public void setMood(VesselMood m, int seconds) {
        moodChosen = m != VesselMood.CALM;
        fromHeart = false;
        applyMood(m, seconds);
    }

    /** The mood the god's lasting feelings call for (calm when none is strong). */
    private VesselMood heartMood(long now) {
        try {
            return realm.memory().heart.mood(now);
        } catch (RuntimeException e) {
            return VesselMood.CALM;
        }
    }

    /** Takes on what its heart feels: after a timed mood ends, and as feelings rise and fade. */
    private void followHeart(long now) {
        VesselMood base = heartMood(now);
        if (base == mood) return;
        applyMood(base, 0);
        fromHeart = base != VesselMood.CALM;
        moodChosen = false;
    }

    /**
     * An automatic flourish after one of the god's acts: turn toward the mortal, gesture, and take on
     * a mood for a while, unless the god chose its mood itself.
     */
    public void react(VesselMood m, int seconds, Player toward, String gestureName) {
        if (!isManifested() || trueForm != null) return;
        if (toward != null && toward.getWorld().equals(pos.getWorld())
                && toward.getLocation().distanceSquared(pos) < 64 * 64) {
            lookAt(toward);
        }
        if (gestureName != null && (gesture.equals("idle") || gesture.equals("speak"))) gesture(gestureName);
        if (m == null || (moodChosen && mood != VesselMood.CALM) || mood == m) return;
        applyMood(m, seconds);
    }

    /** Shows or hides the thinking dots over its head. Any thread; drawn by the vessel's timer. */
    public void setThinking(boolean on) {
        thinking = on;
    }

    // ---- Pondering: what its body does while its mind works ---------------------------------------

    /** The trigger of a thought that just began (set from the mind thread, taken up by the timer). */
    private volatile String ponderAsk;
    private Pondering.Plan ponder;
    private long ponderStart;
    /** The tick by which it has faded back to rest (once the thought has ended), or -1. */
    private long ponderEnd = -1;
    /** How far it has taken the pondering pose (0..1): it yields to gestures, speech and walking. */
    private float ponderBlend;
    private int ponderStage = -1;
    /** What it did in its last few thoughts, so it does not repeat itself. */
    private final java.util.ArrayDeque<String> ponderRecent = new java.util.ArrayDeque<>();

    /** A thought has begun: its body will show it working (see {@link Pondering}). Any thread. */
    public void ponder(String trigger) {
        ponderAsk = trigger == null ? "" : trigger;
    }

    private void startPonder(String trigger) {
        Pondering.Why why = Pondering.Why.of(trigger);
        Map<String, Integer> dials = Map.of();
        List<Choreo.Anim> own = new ArrayList<>();
        try {
            Persona p = realm.get(Persona::current);
            if (p != null && p.dials != null) dials = p.dials;
            for (Map.Entry<String, String> e : realm.memory().dances.entrySet()) {
                if (!Pondering.isOwn(e.getKey())) continue;
                try {
                    own.add(Choreo.fromText(e.getKey(), e.getValue()));
                } catch (RuntimeException bad) {
                    // a saved animation that no longer parses is simply not used
                }
            }
        } catch (RuntimeException e) {
            // no persona or memory yet: the built-in poses with an even temper
        }
        boolean complex = why == Pondering.Why.COMMAND || why == Pondering.Why.WOUNDED;
        ponder = Pondering.choose(why, mood, dials, complex, ponderRecent, own, System.nanoTime());
        ponderStart = ticks;
        ponderEnd = -1;
        ponderStage = -1;
        if (ponder.opening() != null) ponderRecent.add(ponder.opening().id());
        if (ponder.working() != null) ponderRecent.add(ponder.working().id());
        while (ponderRecent.size() > 5) ponderRecent.poll();
    }

    /** Takes up a new thought, and lets a finished one fade. Called every tick. */
    private void tickPonder() {
        String ask = ponderAsk;
        if (ask != null) {
            ponderAsk = null;
            if (GodSettings.vesselPondering() && trueForm == null && hasBody()) startPonder(ask);
        }
        if (ponder == null) return;
        if (!thinking && ponderEnd < 0) {
            ponderEnd = ticks + Choreo.FADE_OUT + 4;
            if (ponder.stage(ticks - ponderStart) >= 2 && ponderBlend > 0.5f) {
                // The working is released: what it gathered scatters.
                pos.getWorld().spawnParticle(Particle.DUST, chestPoint(), 14, 0.35 * scale, 0.45 * scale, 0.35 * scale,
                        0, new Particle.DustOptions(mood.outline != null ? mood.outline : look.mote(), 1.1f));
                pos.getWorld().playSound(pos, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.3f);
            }
        }
        if (ponderEnd >= 0 && ticks >= ponderEnd) {
            ponder = null;
            ponderBlend = 0;
        }
    }

    /** Lays the pondering pose over the idle pose, unless it is busy with a gesture, an animation or walking. */
    private void applyPonder(VesselModel.Pose want, double moved) {
        if (ponder == null) return;
        boolean free = gesture.equals("idle") && dance == null && arrival == null && moved <= 0.02;
        ponderBlend += ((free ? 1f : 0f) - ponderBlend) * 0.2f;
        float fade = ponderEnd < 0 ? 1f : Math.max(0f, Math.min(1f, (ponderEnd - ticks) / (float) (Choreo.FADE_OUT + 4)));
        float w = ponderBlend * fade;
        if (w < 0.01f) return;
        long age = ticks - ponderStart;
        Pondering.Act open = ponder.opening(), work = ponder.working(), deep = ponder.deep();
        if (open != null) {
            Choreo.apply(want, open.anim(), age, Choreo.weight(age, open.anim().passTicks()) * w);
        }
        if (age >= ponder.workAt() && work != null) {
            float in = Math.min(1f, (age - ponder.workAt() + 2) / 10f);
            float out = age < ponder.deepAt() || deep == null ? 1f : Math.max(0f, 1f - (age - ponder.deepAt()) / 20f);
            if (in * out > 0) Choreo.apply(want, work.anim(), age - ponder.workAt(), in * out * w);
        }
        if (age >= ponder.deepAt() && deep != null) {
            Choreo.apply(want, deep.anim(), age - ponder.deepAt(), Math.min(1f, (age - ponder.deepAt()) / 20f) * w);
        }
        // Rising in thought never lifts it through a ceiling.
        if (!phases()) {
            float room = (float) (Math.max(0, headroom() - lift) / unit());
            if (want.offset.y > room) want.offset.y = room;
        }
    }

    /** The particles and sounds of its working. */
    private void ponderFx(Map<Bone, Frame> frames) {
        if (ponder == null || ponderBlend < 0.35f || ponderEnd >= 0) return;
        long age = ticks - ponderStart;
        int stage = ponder.stage(age);
        World w = pos.getWorld();
        if (stage != ponderStage) {
            ponderStage = stage;
            switch (stage) {
                case 1 -> w.playSound(pos, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.5f);
                case 2 -> w.playSound(pos, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.9f);
                case 3 -> w.playSound(pos, Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 0.7f);
                default -> {
                }
            }
        }
        if (stage < 1 || ticks % 2 != 0) return;
        if (stage >= 2 && ticks % 90 == 0) w.playSound(pos, Sound.BLOCK_BEACON_AMBIENT, 0.5f, 1.2f);
        List<Pondering.Dot> dots = new ArrayList<>(Pondering.motif(ponder.motif(), ticks, stage, ponder.seed()));
        if (stage >= 2) dots.addAll(Pondering.motif(ponder.second(), ticks, stage - 1, ponder.seed() + 7));
        double h = size() * 1.8;
        Particle.DustOptions accent = new Particle.DustOptions(mood.outline != null ? mood.outline : look.mote(), 0.9f);
        Location feet = new Location(w, pos.getX(), baseY, pos.getZ());
        Location chest = null, head = null, lhand = null, rhand = null;
        for (Pondering.Dot d : dots) {
            Location at = switch (d.at()) {
                case CHEST -> chest != null ? chest : (chest = chestPoint());
                case HEAD -> head != null ? head : (head = bodyPoint(frames, Bone.HEAD, 0, 8, 0));
                case LHAND -> lhand != null ? lhand : (lhand = bodyPoint(frames, Bone.LEFT_ARM, 1, -10, 0));
                case RHAND -> rhand != null ? rhand : (rhand = bodyPoint(frames, Bone.RIGHT_ARM, -1, -10, 0));
                default -> feet;
            };
            double x = at.getX() + d.x() * h, y = at.getY() + d.y() * h, z = at.getZ() + d.z() * h;
            switch (d.ink()) {
                case ACCENT -> w.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, accent);
                case LIGHT -> w.spawnParticle(Particle.END_ROD, x, y, z, 1, 0, 0, 0, 0);
                case SPARK -> w.spawnParticle(Particle.ELECTRIC_SPARK, x, y, z, 1, 0.02, 0.02, 0.02, 0.02);
                case EMBER -> w.spawnParticle(Particle.SMALL_FLAME, x, y, z, 1, 0, 0, 0, 0.002);
                case SMOKE -> w.spawnParticle(Particle.SMOKE, x, y, z, 1, 0.05, 0.01, 0.05, 0.002);
                case SOUL -> w.spawnParticle(Particle.SOUL_FIRE_FLAME, x, y, z, 1, 0, 0, 0, 0.004);
                case GLYPH -> w.spawnParticle(Particle.ENCHANT, x, y, z, 2, 0.05, 0.05, 0.05, 0.3);
            }
        }
    }

    /** What its body is doing in thought, for status lines ("weaving, with tendrils"), or null. */
    public String pondering() {
        Pondering.Plan p = ponder;
        if (p == null || p.working() == null) return null;
        return p.working().id() + " with " + p.motif();
    }

    private void thinkingDots() {
        boolean working = ponder != null && ponderBlend > 0.5f && ticks - ponderStart >= ponder.workAt();
        boolean want = thinking && bubble == null && arrival == null && pos != null && !working;
        if (!want) {
            if (dots != null) {
                dots.remove();
                dots = null;
            }
            return;
        }
        int n = (int) ((ticks / 8) % 3) + 1;
        Component text = Component.text("•" + " •".repeat(n - 1), Speech.VIOLET);
        if (dots == null || !dots.isValid()) {
            dots = pos.getWorld().spawn(speechSpot(), TextDisplay.class, t -> {
                setupDisplay(t);
                t.setBillboard(Display.Billboard.CENTER);
                t.setBackgroundColor(Color.fromARGB(110, 25, 8, 45));
                t.text(text);
            });
        } else if (ticks % 8 == 0) {
            dots.text(text);
        }
        if (ticks % 4 == 0) dots.teleport(speechSpot());
    }

    public void showSpeech(String text) {
        if (!isManifested()) return;
        if (arrival != null) {
            pendingSpeech = text; // shown once the body has formed
            pendingSpeechAt = System.currentTimeMillis();
            return;
        }
        if (bubble == null || !bubble.isValid()) {
            bubble = pos.getWorld().spawn(speechSpot(), TextDisplay.class, t -> {
                setupDisplay(t);
                t.setBillboard(Display.Billboard.CENTER);
                t.setLineWidth(220);
                t.setBackgroundColor(Color.fromARGB(170, 25, 8, 45));
            });
        }
        bubble.text(Component.text(text, Speech.VIOLET));
        bubbleUntil = System.currentTimeMillis() + 4000 + text.length() * 60L;
        if (hasBody() && (gesture.equals("idle") || gesture.equals("speak"))) {
            gesture("speak", Math.min(6000, 1500 + text.length() * 45L));
        }
    }

    /** Re-dresses the vessel after its look settings change. */
    public void restyle() {
        if (!hasBody()) return;
        Location at = pos.clone();
        despawn();
        pos = at;
        spawnParts(true);
        pos.getWorld().spawnParticle(Particle.REVERSE_PORTAL, chestPoint(), 80, 0.5, 1.2, 0.5, 0.08);
    }

    public void shutdown() {
        if (trueForm != null) {
            trueForm.abort();
            trueForm = null;
        }
        if (rift != null) rift.stop();
        rift = null;
        if (departRift != null) departRift.stop();
        departRift = null;
        departing = null;
        arrival = null;
        arrivalFrom = null;
        emergeGoal = null;
        pendingGesture = null;
        pendingSpeech = null;
        if (task != null) {
            task.cancel();
            task = null;
        }
        entities().forEach(Entity::remove);
        clearRefs();
    }

    /** Removes vessel pieces left behind by a crash. */
    public static void cleanupStrays() {
        NamespacedKey key = new NamespacedKey(GPTGOD.get(), TAG);
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                    e.remove();
                }
            }
        }
    }

    public static boolean isVesselEntity(Entity e) {
        return e.getPersistentDataContainer().has(new NamespacedKey(GPTGOD.get(), TAG), PersistentDataType.BYTE);
    }

    /** The god whose body this entity is part of, or null (a stray, or not a vessel). */
    public static Realm ownerOf(Entity e) {
        String id = e.getPersistentDataContainer().get(new NamespacedKey(GPTGOD.get(), OWNER_TAG),
                PersistentDataType.STRING);
        if (id != null) return Realm.byId(id);
        return isVesselEntity(e) ? Realm.of(e.getWorld()) : null;
    }

    /** The god this vessel belongs to. */
    public Realm realm() {
        return realm;
    }

    /** Safe from any thread. */
    public void refreshNameplateLater() {
        if (GPTGOD.get().isEnabled()) {
            Bukkit.getScheduler().runTask(GPTGOD.get(), () -> realm.run(() -> {
                refreshNameplate();
                // An "auto" look follows the archetype, which may have just changed.
                if (hasBody() && !currentLook().equals(look)) restyle();
            }));
        }
    }

    /**
     * The vessel wears no name: the god's name appears only in chat and in the player list. Kept
     * so callers that announce a new persona have one place to call.
     */
    public void refreshNameplate() {
    }

    // ---- Entities ------------------------------------------------------------------------------

    private float unit() {
        return VesselModel.unit(scale);
    }

    /** The configured look, with ornament, cape and outline overrides applied. */
    private VesselLook currentLook() {
        Persona p = realm.get(Persona::current);
        // The god's own look (God Manager), else its realms.gods.<id>.look, else the server-wide vessel.look.
        String wanted = realm.lookSetting();
        // "auto" reads the god's epithet and name first ("the Drowned King" wears tide), then its archetype.
        String hint = p == null ? null : (p.epithet == null ? "" : p.epithet) + " " + (p.name == null ? "" : p.name);
        VesselLook l = VesselLook.resolve(wanted, p == null ? null : p.archetype, hint);
        // vessel.ornament: auto keeps the look's own; any ornament name (horns, halo, crown, antlers...) overrides it.
        VesselLook.Ornament ornament = VesselLook.Ornament.parse(GodSettings.vesselOrnament(), l.ornament());
        return l.with(ornament, GodSettings.vesselCape(), parseColor(GodSettings.vesselGlowColor(), l.outline()));
    }

    private Color glowColor() {
        return mood.outline != null ? mood.outline : look.outline();
    }

    private Material materialFor(Slot slot) {
        if (slot == Slot.EYES && mood.eyes != null) return mood.eyes;
        return look.material(slot);
    }

    private void spawnParts(boolean growIn) {
        scale = VesselModel.scaleForSize(size());
        look = currentLook();
        World w = pos.getWorld();
        baseY = bodyY();
        Location base = bodyLocation();
        Map<Bone, Frame> frames = VesselModel.frames(pose);
        float unit = unit();
        boolean outline = GodSettings.vesselOutline();
        Color glow = glowColor();
        boolean skins = VesselPackHost.active();
        float grow = growIn ? 0.02f : 1f;
        for (Part part : VesselModel.parts(look)) {
            Material mat = materialFor(part.slot());
            boolean twin = skins && part.piece().textured();
            Piece block = new Piece(null, part, twin ? Kind.BLOCK : Kind.SHARED);
            BlockDisplay e = w.spawn(base, BlockDisplay.class, b -> {
                setupDisplay(b);
                b.setBlock(mat.createBlockData());
                b.setTransformation(block.at(frames, unit, grow));
                if (twin) b.setVisibleByDefault(false);
                if (outline) {
                    b.setGlowing(true);
                    b.setGlowColorOverride(glow);
                }
            });
            pieces.add(new Piece(e, part, block.kind()));
            if (!twin) continue;
            Piece skin = new Piece(null, part, Kind.SKIN);
            ItemDisplay i = w.spawn(base, ItemDisplay.class, d -> {
                setupDisplay(d);
                d.setVisibleByDefault(false);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setItemStack(skinItem(skinKey(part)));
                d.setTransformation(skin.at(frames, unit, grow));
                if (outline) {
                    d.setGlowing(true);
                    d.setGlowColorOverride(glow);
                }
            });
            pieces.add(new Piece(i, part, Kind.SKIN));
        }
        for (Player p : Bukkit.getOnlinePlayers()) showTo(p);
        sentFrames.clear();
        sentAt.clear();
        if (growIn) {
            growTicks = 14;
        } else {
            sentFrames.putAll(frames);
            frames.keySet().forEach(b -> sentAt.put(b, ticks));
        }
        hitbox = w.spawn(hitboxSpot(), Interaction.class, i -> {
            tag(i);
            i.setPersistent(false);
            i.setResponsive(true);
        });
        fitHitbox();
    }

    /** The atlas region a part wears: its piece, or a mood's eyes. */
    private String skinKey(Part part) {
        if (part.piece() == VesselSkin.Piece.EYE && mood.eyes != null
                && VesselSkin.MOOD_EYES.contains(mood.id())) {
            return VesselSkin.eyeKey(mood.id());
        }
        return part.piece().id();
    }

    /** An item that shows the resource pack's model for a region of the current look. */
    private ItemStack skinItem(String key) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString(VesselPack.modelId(look.name(), key)));
        stack.setItemMeta(meta);
        return stack;
    }

    /** Shows a player the skinned body if their client loaded the vessel's resource pack, else the blocks. */
    public void showTo(Player player) {
        boolean skinned = VesselPackHost.has(player);
        for (Piece p : pieces) {
            if (p.kind() == Kind.SHARED || !p.entity().isValid()) continue;
            if ((p.kind() == Kind.SKIN) == skinned) player.showEntity(GPTGOD.get(), p.entity());
            else player.hideEntity(GPTGOD.get(), p.entity());
        }
    }

    /** Re-picks which body a player sees in every realm, after their resource pack status changed. */
    public static void refreshViewer(Player player) {
        for (Realm r : Realm.all()) r.vessel().showTo(player);
    }

    /** Sizes the box mortals touch and strike to the body. */
    private void fitHitbox() {
        if (hitbox == null || !hitbox.isValid()) return;
        float unit = unit();
        hitbox.setInteractionWidth(10 * unit + 0.2f);
        hitbox.setInteractionHeight((float) (VesselModel.HEIGHT_PX * unit + HOVER));
    }

    /** Recolors the eyes and glow for the current mood. */
    private void dress() {
        boolean outline = GodSettings.vesselOutline();
        Color glow = glowColor();
        Material eyes = materialFor(Slot.EYES);
        for (Piece p : pieces) {
            if (!p.entity().isValid()) continue;
            if (p.part().slot() == Slot.EYES) {
                if (p.entity() instanceof ItemDisplay i) i.setItemStack(skinItem(skinKey(p.part())));
                else if (eyes != null && p.entity() instanceof BlockDisplay b) b.setBlock(eyes.createBlockData());
            }
            if (outline) p.entity().setGlowColorOverride(glow);
        }
    }

    private void setupDisplay(Display d) {
        tag(d);
        d.setPersistent(false);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(2.0f);
        d.setShadowRadius(0);
        d.setTeleportDuration(4);
    }

    private void tag(Entity e) {
        e.getPersistentDataContainer().set(new NamespacedKey(GPTGOD.get(), TAG), PersistentDataType.BYTE, (byte) 1);
        // Which god this body belongs to, when several share a world.
        e.getPersistentDataContainer().set(new NamespacedKey(GPTGOD.get(), OWNER_TAG), PersistentDataType.STRING,
                realm.id);
    }

    private List<Entity> entities() {
        List<Entity> all = new ArrayList<>();
        pieces.forEach(p -> all.add(p.entity()));
        if (hitbox != null) all.add(hitbox);
        if (bubble != null) all.add(bubble);
        if (dots != null) all.add(dots);
        if (nameplate != null) all.add(nameplate);
        all.addAll(wingRig.entities());
        return all;
    }

    /** Removes the entities at once but keeps orders and position. */
    private void despawn() {
        entities().forEach(Entity::remove);
        pieces.clear();
        wingRig.forget();
        hitbox = null;
        bubble = null;
        dots = null;
        nameplate = null;
        nameplateText = null;
    }

    /** Shrinks every piece into the chest, then removes them. */
    private void shrinkAway() {
        List<Entity> doomed = shrink(10);
        clearRefs();
        Bukkit.getScheduler().runTaskLater(GPTGOD.get(), () -> doomed.forEach(Entity::remove), 11L);
    }

    /** Starts every piece shrinking into the chest over this many ticks; returns all the entities. */
    private List<Entity> shrink(int ticksLong) {
        Map<Bone, Frame> frames = VesselModel.frames(pose);
        float unit = unit();
        for (Piece p : pieces) {
            if (!p.entity().isValid()) continue;
            p.entity().setInterpolationDelay(0);
            p.entity().setInterpolationDuration(ticksLong);
            p.entity().setTransformation(p.at(frames, unit, 0.02f));
        }
        return entities();
    }

    private void clearRefs() {
        pieces.clear();
        wingRig.forget();
        hitbox = null;
        bubble = null;
        dots = null;
        nameplate = null;
        nameplateText = null;
        pos = null;
        mode = Mode.IDLE;
        followTarget = null;
        lift = 0;
        flight = 0;
    }

    private void ensureTicking() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(GPTGOD.get(), () -> realm.run(this::tick), 2L, 2L);
        }
    }

    // ---- Moods ---------------------------------------------------------------------------------

    private void applyMood(VesselMood m, int seconds) {
        VesselMood old = mood;
        mood = m;
        moodUntil = m == VesselMood.CALM || seconds <= 0 ? 0 : System.currentTimeMillis() + seconds * 1000L;
        if (m == VesselMood.CALM) moodChosen = false;
        if (!hasBody() || old == m) return;
        dress();
        moodShift();
        Journal.add(Journal.Kind.INFO, "Vessel mood: " + m.id());
    }

    /** The burst of sound and light as a mood takes hold. */
    private void moodShift() {
        World w = pos.getWorld();
        Location chest = chestPoint();
        switch (mood) {
            case WRATHFUL -> {
                w.playSound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.6f, 0.55f);
                w.playSound(pos, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.6f);
                w.spawnParticle(Particle.DUST, chest, 140, 1.2, 1.4, 1.2, 0,
                        new Particle.DustOptions(Color.fromRGB(0xC0140F), 2.2f));
                w.spawnParticle(Particle.LAVA, chest, 14, 0.8, 0.8, 0.8, 0);
                w.spawnParticle(Particle.FLASH, chest, 1, Color.WHITE);
                groundRing(Color.fromRGB(0xFF2A1A), 3.5, 2f);
                if (gesture.equals("idle")) gesture("roar");
            }
            case JOYFUL -> {
                w.playSound(pos, Sound.ENTITY_PLAYER_LEVELUP, 1.2f, 0.6f);
                w.spawnParticle(Particle.END_ROD, chest, 50, 0.8, 1.2, 0.8, 0.06);
                w.spawnParticle(Particle.HEART, chest.clone().add(0, 1, 0), 8, 0.9, 0.6, 0.9);
            }
            case SORROWFUL -> {
                w.playSound(pos, Sound.BLOCK_BEACON_DEACTIVATE, 1.4f, 0.5f);
                w.spawnParticle(Particle.SOUL, chest, 20, 0.8, 1, 0.8, 0.02);
            }
            case OMINOUS -> {
                w.playSound(pos, Sound.AMBIENT_CAVE, 1.6f, 0.6f);
                w.playSound(pos, Sound.ENTITY_WARDEN_HEARTBEAT, 1.4f, 0.5f);
                w.spawnParticle(Particle.LARGE_SMOKE, chest, 60, 1, 1.4, 1, 0.02);
                w.spawnParticle(Particle.SCULK_SOUL, chest, 20, 1, 1.2, 1, 0.03);
            }
            case EXALTED -> {
                w.playSound(pos, Sound.BLOCK_BEACON_POWER_SELECT, 1.6f, 0.7f);
                w.playSound(pos, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.6f, 0.5f);
                w.spawnParticle(Particle.FLASH, chest, 1, Color.WHITE);
                w.spawnParticle(Particle.END_ROD, chest, 90, 1, 1.6, 1, 0.12);
                groundRing(Color.fromRGB(0xFFE9A0), 3.5, 1.6f);
            }
            default -> {
                w.playSound(pos, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.6f);
                w.spawnParticle(Particle.REVERSE_PORTAL, chest, 40, 0.5, 1, 0.5, 0.05);
            }
        }
    }

    // ---- Movement and animation ----------------------------------------------------------------

    private void tick() {
        ticks += 2;
        if (trueForm != null) {
            if (trueForm.tick()) endTrueForm();
            if (bubble != null && System.currentTimeMillis() > bubbleUntil) {
                bubble.remove();
                bubble = null;
            }
            return;
        }
        if (!hasBody()) return;
        if (!pieces.get(0).entity().isValid()) {
            // Its chunk unloaded: re-form if anyone is around, otherwise fade away.
            Location keep = pos;
            despawn();
            if (keep.isChunkLoaded() && !keep.getWorld().getPlayers().isEmpty()) {
                pos = keep;
                spawnParts(false);
            } else {
                clearRefs();
                return;
            }
        }
        long now = System.currentTimeMillis();
        if (moodUntil > 0 && now >= moodUntil) {
            moodUntil = 0;
            followHeart(now);
            if (mood != VesselMood.CALM && !fromHeart) applyMood(VesselMood.CALM, 0);
        } else if (moodUntil == 0 && (fromHeart || mood == VesselMood.CALM) && now - lastHeartCheck > 5000) {
            lastHeartCheck = now;
            followHeart(now);
        }

        Location before = pos.clone();
        Location target = null;
        double speed = 0.26 * scale;
        switch (mode) {
            case WALK -> {
                target = goal;
                if (goal == null || pos.distance(goal) < 0.3) {
                    mode = Mode.IDLE;
                    target = null;
                    emergeGoal = null;
                } else if (goal == emergeGoal) {
                    // Stepping out of its rift: it condenses first, then strides out slowly.
                    if (ticks < emergeAt) target = null;
                    else speed *= EMERGE_PACE;
                }
            }
            case FLY -> {
                target = goal;
                double d = goal == null ? 0 : pos.distance(goal);
                if (goal == null || d < 0.4) {
                    mode = Mode.IDLE;
                    target = null;
                } else {
                    speed = Math.max(0.15, Math.min(0.75, d * 0.1)) * scale;
                }
            }
            case FOLLOW -> {
                Player p = followTarget == null ? null : Bukkit.getPlayer(followTarget);
                if (p == null || p.getGameMode() == GameMode.SPECTATOR) {
                    mode = Mode.IDLE;
                } else if (!p.getWorld().equals(pos.getWorld()) || p.getLocation().distance(pos) > 48) {
                    relocate(besideSpot(p));
                    return;
                } else {
                    Location beside = besideSpot(p);
                    double d = beside.distance(pos);
                    if (d > 1.0) {
                        target = beside;
                        speed = Math.min(1.0, 0.18 + d * 0.07) * scale;
                    }
                }
            }
            default -> {
                if (departing == null) idleBehavior(); // leaving: it stands in its rift until it is taken
            }
        }
        // Walking goes around walls and through doorways; with no way through, it rises over them.
        boolean walkingNow = target != null && (mode == Mode.WALK || mode == Mode.FOLLOW);
        if (walkingNow) {
            target = route(target);
        } else {
            path = null;
            soaring = false;
        }
        if (target != null) {
            double dx = target.getX() - pos.getX();
            double dy = target.getY() - pos.getY();
            double dz = target.getZ() - pos.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double step = Math.min(speed, dist);
            if (dist > 1e-6) {
                Location next = pos.clone().add(dx / dist * step, dy / dist * step, dz / dist * step);
                if (phases() || fits(next)) {
                    pos = next;
                    blockedSince = 0;
                } else {
                    // A wall or a roof: rise over it, or slip sideways along it; stuck too long, it steps through space.
                    Location up = pos.clone().add(0, Math.max(0.2, step), 0);
                    Location slide = pos.clone().add(dx / dist * step, 0, 0);
                    Location slide2 = pos.clone().add(0, 0, dz / dist * step);
                    Location stepUp = next.clone();
                    stepUp.setY(Math.max(next.getY(), Math.ceil(target.getY() - 0.01)));
                    if (stepUp.getY() - pos.getY() <= 1.05 && fits(stepUp)) pos = stepUp; // a step up, not a wall
                    else if (fits(up) && (mode == Mode.FLY || soaring)) pos = up;
                    else if (fits(slide)) pos = slide;
                    else if (fits(slide2)) pos = slide2;
                    if (blockedSince == 0) blockedSince = System.currentTimeMillis();
                    if (System.currentTimeMillis() - blockedSince > 2500) {
                        blockedSince = 0;
                        Location dest = mode == Mode.FOLLOW ? target : goal != null ? goal : target;
                        relocate(safeSpot(dest));
                        mode = mode == Mode.FOLLOW ? Mode.FOLLOW : Mode.IDLE;
                        path = null;
                        return;
                    }
                }
            }
        }
        unsqueezeWhenRoomy();
        double toGoal = goal == null ? 0 : Math.hypot(goal.getX() - pos.getX(), goal.getZ() - pos.getZ());
        if (((mode == Mode.FLY && toGoal > 3) || (walkingNow && soaring)) && pos.isChunkLoaded()) {
            // Soar over hills and trees instead of through them.
            int ground = pos.getWorld().getHighestBlockYAt(pos.getBlockX(), pos.getBlockZ()) + 1;
            if (pos.getY() < ground) pos.setY(ground);
        }

        // Height: orders, mood and gestures decide how high it floats.
        double wantLift = Math.max(altitude, mood.rise);
        double rate = 0.07;
        if (gesture.equals("meditate")) wantLift = Math.max(wantLift, 1.0);
        if (mode == Mode.FLY) {
            if (toGoal > 6) wantLift = Math.max(wantLift, Math.min(14, 3 + toGoal * 0.15));
            rate = 0.12;
        }
        if (gesture.equals("slam")) {
            wantLift = 0;
            rate = 0.3;
        }
        wantLift = Math.max(0, Math.min(wantLift, pos.getWorld().getMaxHeight() - pos.getY() - 4));
        if (!phases()) wantLift = Math.min(wantLift, headroom());
        lift += Math.max(-0.45, Math.min(0.45, (wantLift - lift) * rate));
        if (Math.abs(wantLift - lift) < 0.01) lift = wantLift;

        // Stride or flight: how much it walks or soars, and where in the step cycle it is.
        double hx = pos.getX() - before.getX(), hz = pos.getZ() - before.getZ();
        double moved = Math.sqrt(hx * hx + hz * hz);
        boolean airborne = lift > 0.9 || mode == Mode.FLY;
        float wantWalk = airborne ? 0 : (float) Math.min(1, moved / (0.1 * scale));
        walk += (wantWalk - walk) * 0.3f;
        phase += (float) Math.min(1.0, moved / (12 * unit()) * 2.2);
        float wantFly = airborne ? (float) Math.min(1, moved / (0.32 * scale)) : 0;
        flight += (wantFly - flight) * 0.18f;
        baseY = bodyY();

        // Facing: move where it goes, turn its head toward whoever it watches, and its body after.
        float targetYaw = yaw;
        if (moved > 0.02) {
            targetYaw = (float) Math.toDegrees(-Math.atan2(hx, hz));
        }
        Vector3f lookDir = null;
        Vector3f pointDir = null;
        Player watch = departing != null ? null : watched(); // leaving, it does not turn back
        if (watch != null) {
            Location eye = watch.getEyeLocation();
            if (moved <= 0.02) {
                float watchYaw = (float) Math.toDegrees(-Math.atan2(eye.getX() - pos.getX(), eye.getZ() - pos.getZ()));
                float rel = wrap(watchYaw - yaw);
                boolean facing = facingSince > 0 && System.currentTimeMillis() - facingSince < 10_000
                        && lookTarget != null && lookTarget.equals(watch.getUniqueId());
                if (facing) {
                    targetYaw = watchYaw; // someone spoke to it: it turns fully toward them
                } else if (Math.abs(rel) > 45) {
                    targetYaw = watchYaw - Math.signum(rel) * 20;
                }
            }
            lookDir = toBody(eye.getX() - pos.getX(), eye.getY() - (baseY + VesselModel.EYE_PX * unit()),
                    eye.getZ() - pos.getZ());
            Vector3f shoulder = toWorld(-5 * unit(), 22 * unit(), 0);
            pointDir = toBody(eye.getX() - pos.getX() - shoulder.x, eye.getY() - 0.4 - (baseY + shoulder.y),
                    eye.getZ() - pos.getZ() - shoulder.z);
        }
        if (faceSpot != null && now < faceUntil && moved <= 0.02 && faceSpot.getWorld() == pos.getWorld()) {
            targetYaw = (float) Math.toDegrees(-Math.atan2(faceSpot.getX() - pos.getX(), faceSpot.getZ() - pos.getZ()));
        }
        yaw = approachAngle(yaw, targetYaw, moved > 0.02 ? 18f : 8f);

        if (!gesture.equals("idle") && now >= gestureUntil) {
            gesture = "idle";
        }
        long age = ticks - gestureStart;
        float air = (float) Math.max(0, Math.min(1, (lift - 0.25) / 1.0));
        Motion motion = new Motion(gesture, age, time(), walk, phase, air, flight, mood, lookDir, pointDir);
        Pose want = VesselModel.pose(motion);
        tickPonder();
        applyPonder(want, moved);
        float danceAge = ticks - danceStart;
        if (dance != null && danceAge >= danceTotal) dance = null;
        if (dance != null) Choreo.apply(want, dance, danceAge, Choreo.weight(danceAge, danceTotal));
        pose.approach(want, VesselModel.snappy(gesture) || dance != null ? 0.6f : 0.4f);
        Map<Bone, Frame> frames = VesselModel.frames(pose);
        updateParts(frames);
        wingRig.tick(this::setupDisplay, bodyLocation(), frames, unit(), trueForm == null && GodSettings.vesselWings(),
                mood.wings, look == null ? null : look.name(), air, flight, time(), ticks,
                GodSettings.vesselOutline() ? glowColor() : null);

        if (ticks % 4 == 0) {
            Location body = bodyLocation();
            for (Piece p : pieces) {
                p.entity().teleport(body);
            }
            wingRig.teleport(body);
            hitbox.teleport(hitboxSpot());
        }
        if (bubble != null) {
            if (now > bubbleUntil) {
                bubble.remove();
                bubble = null;
            } else if (ticks % 4 == 0) {
                bubble.teleport(speechSpot());
            }
        }
        thinkingDots();
        if (ticks % 4 == 0) updateNameplate();
        gestureEffects(frames, age);
        if (dance != null) danceCues(frames, danceAge);
        aura(frames);
        ponderFx(frames);
    }

    /** Animation time in ticks, wrapped so float precision stays fine on long-running servers. */
    private float time() {
        return (float) (ticks % 200_000L);
    }

    /**
     * Sends the joints that moved. Bones whose pose barely changed are skipped, so a still vessel costs
     * almost nothing; each update interpolates over roughly the time since the last one, so motion stays smooth.
     */
    private void updateParts(Map<Bone, Frame> frames) {
        int forced = growTicks;
        growTicks = 0;
        EnumSet<Bone> dirty = EnumSet.noneOf(Bone.class);
        Map<Bone, Integer> duration = new EnumMap<>(Bone.class);
        for (Map.Entry<Bone, Frame> e : frames.entrySet()) {
            Bone b = e.getKey();
            Frame last = sentFrames.get(b);
            if (forced == 0 && last != null && !moved(last, e.getValue())) continue;
            long gap = ticks - sentAt.getOrDefault(b, ticks - 2);
            dirty.add(b);
            duration.put(b, forced > 0 ? forced : (int) Math.max(3, Math.min(6, gap)));
            sentFrames.put(b, e.getValue());
            sentAt.put(b, ticks);
        }
        if (dirty.isEmpty()) return;
        float unit = unit();
        for (Piece p : pieces) {
            Bone b = p.part().bone();
            if (!dirty.contains(b) || !p.entity().isValid()) continue;
            p.entity().setInterpolationDelay(0);
            p.entity().setInterpolationDuration(duration.get(b));
            p.entity().setTransformation(p.at(frames, unit, 1f));
        }
    }

    private static boolean moved(Frame a, Frame b) {
        return Math.abs(a.rot().dot(b.rot())) < 0.99997f || a.pos().distanceSquared(b.pos()) > 0.0025f;
    }

    private void idleBehavior() {
        if (!"follow-players".equals(GodSettings.vesselIdleBehavior())) return;
        if (System.currentTimeMillis() - lastOrderAt < 45_000) return;
        Player nearest = nearestPlayer(96);
        if (nearest != null && nearest.getLocation().distance(pos) > 10) {
            goal = besideSpot(nearest);
            mode = Mode.WALK;
            lastOrderAt = System.currentTimeMillis();
        }
    }

    /** Who its head follows: whoever it was told to watch, else the nearest mortal. */
    private Player watched() {
        Player p = lookTarget == null ? null : Bukkit.getPlayer(lookTarget);
        if (p != null && p.getWorld().equals(pos.getWorld()) && p.getLocation().distanceSquared(pos) < 64 * 64) {
            return p;
        }
        return nearestPlayer(24);
    }

    // ---- Effects -------------------------------------------------------------------------------

    /** Sounds and particles that belong to a gesture: the slam's shockwave, the clap's thunder… */
    private void gestureEffects(Map<Bone, Frame> frames, long age) {
        int impact = VesselModel.impactTick(gesture);
        boolean hit = impact >= 0 && age >= impact && age - 2 < impact;
        World w = pos.getWorld();
        switch (gesture) {
            case "slam" -> {
                if (hit) slamImpact();
            }
            case "clap" -> {
                if (!hit) return;
                Location hands = midpoint(bodyPoint(frames, Bone.LEFT_ARM, 1, -9, 0),
                        bodyPoint(frames, Bone.RIGHT_ARM, -1, -9, 0));
                w.spawnParticle(Particle.SONIC_BOOM, hands, 1);
                w.spawnParticle(Particle.FLASH, hands, 1, Color.WHITE);
                w.spawnParticle(Particle.POOF, hands, 16, 0.3, 0.3, 0.3, 0.15);
                w.playSound(hands, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.6f, 0.7f);
                w.playSound(hands, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.2f, 0.5f);
            }
            case "summon" -> {
                // Light gathers from a ring on the ground and streams upward.
                ThreadLocalRandom r = ThreadLocalRandom.current();
                double radius = 2.5 * scale;
                for (int i = 0; i < 4; i++) {
                    double a = r.nextDouble(Math.PI * 2);
                    Location at = new Location(w, pos.getX() + Math.cos(a) * radius, pos.getY() + 0.2,
                            pos.getZ() + Math.sin(a) * radius);
                    w.spawnParticle(i % 2 == 0 ? Particle.END_ROD : Particle.SOUL_FIRE_FLAME, at, 0, 0, 1, 0, 0.18);
                }
                w.spawnParticle(Particle.ENCHANT, chestPoint(), 6, 1, 1, 1, 0.8);
                if (hit) {
                    Location hands = midpoint(bodyPoint(frames, Bone.LEFT_ARM, 1, -9, 0),
                            bodyPoint(frames, Bone.RIGHT_ARM, -1, -9, 0));
                    w.spawnParticle(Particle.FLASH, hands, 1, Color.WHITE);
                    w.spawnParticle(Particle.END_ROD, hands, 70, 0.4, 0.4, 0.4, 0.2);
                    w.playSound(pos, Sound.BLOCK_BEACON_POWER_SELECT, 1.6f, 0.8f);
                }
            }
            case "roar" -> {
                if (hit) {
                    w.playSound(pos, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.6f);
                    w.playSound(pos, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.5f);
                }
                if (age >= 6 && age <= 34) {
                    // Breath pours from its mouth.
                    Location mouth = bodyPoint(frames, Bone.HEAD, 0, 2, 4.5f);
                    Vector3f dir = worldDir(frames.get(Bone.HEAD), 0, 0.15f, 1);
                    Particle breath = mood == VesselMood.WRATHFUL ? Particle.FLAME : Particle.SOUL_FIRE_FLAME;
                    for (int i = 0; i < 3; i++) {
                        w.spawnParticle(breath, mouth, 0, dir.x, dir.y, dir.z, 0.35 + i * 0.1);
                    }
                    w.spawnParticle(Particle.LARGE_SMOKE, mouth, 0, dir.x, dir.y, dir.z, 0.25);
                }
            }
            case "meditate" -> {
                if (ticks % 10 == 0) {
                    w.spawnParticle(Particle.ENCHANT, chestPoint(), 8, 0.8, 0.8, 0.8, 0.6);
                }
            }
            default -> {
            }
        }
    }

    /** Fists hit the ground: a boom and a ring of dust and rubble rolling outward. */
    private void slamImpact() {
        World w = pos.getWorld();
        Location ground = pos.clone().add(0, 0.15, 0);
        Block below = pos.clone().add(0, -0.5, 0).getBlock();
        BlockData rubble = below.getType().isSolid() ? below.getBlockData() : Material.DIRT.createBlockData();
        w.playSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.55f);
        w.playSound(pos, Sound.BLOCK_ANVIL_LAND, 0.9f, 0.5f);
        w.spawnParticle(Particle.EXPLOSION, ground.clone().add(0, 0.4, 0), 2, 0.4, 0.1, 0.4, 0);
        w.spawnParticle(Particle.BLOCK, ground, 60, 1.2, 0.2, 1.2, 0.2, rubble);
        double maxRadius = 7 * scale;
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (++step > 10) {
                    cancel();
                    return;
                }
                double r = maxRadius * step / 10.0;
                int n = (int) Math.max(12, Math.PI * 2 * r / 0.6);
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    Location at = ground.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                    w.spawnParticle(Particle.BLOCK, at, 2, 0.1, 0.05, 0.1, 0, rubble);
                    if (i % 3 == 0) {
                        w.spawnParticle(Particle.CLOUD, at, 0, Math.cos(a), 0.15, Math.sin(a), 0.12);
                    }
                }
            }
        }.runTaskTimer(GPTGOD.get(), 1L, 1L);
    }

    private void aura(Map<Bone, Frame> frames) {
        World w = pos.getWorld();
        float u = unit();
        double x = pos.getX(), y = baseY, z = pos.getZ();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // Two motes circling the body.
        Particle.DustOptions mote = new Particle.DustOptions(mood.outline != null ? mood.outline : look.mote(), 1.0f);
        for (int k = 0; k < 2; k++) {
            double a = ticks * 0.11 + k * Math.PI;
            double rad = 9.5 * u;
            double h = (11 + 8 * Math.sin(ticks * 0.031 + k * 2.1)) * u;
            w.spawnParticle(Particle.DUST, x + Math.cos(a) * rad, y + h, z + Math.sin(a) * rad, 1, 0, 0, 0, 0, mote);
        }
        switch (mood) {
            case WRATHFUL -> {
                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y + 14 * u, z, 3, 6 * u, 10 * u, 6 * u, 0,
                        new Particle.DustTransition(Color.fromRGB(0x9E0F0F), Color.fromRGB(0x140000), 2.0f));
                // Flames lick from its fists.
                w.spawnParticle(Particle.SMALL_FLAME, bodyPoint(frames, Bone.LEFT_ARM, 1, -10, 0), 1, 0.05, 0.05,
                        0.05, 0.01);
                w.spawnParticle(Particle.SMALL_FLAME, bodyPoint(frames, Bone.RIGHT_ARM, -1, -10, 0), 1, 0.05, 0.05,
                        0.05, 0.01);
                if (ticks % 4 == 0) w.spawnParticle(Particle.ASH, x, y + 16 * u, z, 10, 2.5, 2.0, 2.5, 0);
                if (ticks % 6 == 0) {
                    w.spawnParticle(Particle.ELECTRIC_SPARK, bodyPoint(frames, Bone.HEAD, 0, 9, 0), 3, 5 * u, 3 * u,
                            5 * u, 0.05);
                }
                if (ticks % 10 == 0) w.spawnParticle(Particle.LAVA, x, y + 12 * u, z, 1, 6 * u, 6 * u, 6 * u, 0);
                if (ticks % 20 == 0) groundRing(Color.fromRGB(0xFF2A1A), 2.2 * scale, 1.4f);
                if (ticks % 40 == 0) w.playSound(pos, Sound.ENTITY_WARDEN_HEARTBEAT, 1.4f, 0.6f);
                if (ticks % 240 == 0) w.playSound(pos, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 0.5f);
            }
            case JOYFUL -> {
                if (ticks % 4 == 0) {
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y + 14 * u, z, 1, 6 * u, 9 * u, 6 * u, 0,
                            new Particle.DustTransition(Color.fromRGB(0xFFD86B), Color.WHITE, 1.2f));
                }
                if (ticks % 6 == 0) w.spawnParticle(Particle.END_ROD, x, y + 16 * u, z, 1, 7 * u, 9 * u, 7 * u, 0.01);
                if (ticks % 10 == 0) w.spawnParticle(Particle.WAX_ON, x, y + 16 * u, z, 2, 7 * u, 9 * u, 7 * u, 0);
                if (ticks % 16 == 0) {
                    Location head = bodyPoint(frames, Bone.HEAD, r.nextFloat(-6, 6), 12, r.nextFloat(-3, 3));
                    w.spawnParticle(Particle.NOTE, head, 0, r.nextDouble(), 0, 0, 1);
                }
                if (ticks % 30 == 0) w.spawnParticle(Particle.HEART, bodyPoint(frames, Bone.HEAD, 0, 13, 0), 1);
                if (ticks % 60 == 0) w.playSound(pos, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
            }
            case SORROWFUL -> {
                // Tears fall from its eyes.
                if (ticks % 4 == 0) {
                    w.spawnParticle(Particle.FALLING_WATER, bodyPoint(frames, Bone.HEAD, 2, 3, 4.4f), 1);
                    w.spawnParticle(Particle.FALLING_WATER, bodyPoint(frames, Bone.HEAD, -2, 3, 4.4f), 1);
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y + 14 * u, z, 1, 6 * u, 9 * u, 6 * u, 0,
                            new Particle.DustTransition(Color.fromRGB(0x7FB2FF), Color.fromRGB(0x2B3A55), 1.4f));
                }
                if (ticks % 12 == 0) w.spawnParticle(Particle.SOUL, x, y + 6 * u, z, 1, 6 * u, 4 * u, 6 * u, 0.01);
                if (ticks % 120 == 0) w.playSound(pos, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.5f);
            }
            case OMINOUS -> {
                w.spawnParticle(Particle.LARGE_SMOKE, x, y + 12 * u, z, 1, 5 * u, 9 * u, 5 * u, 0.005);
                if (ticks % 6 == 0) w.spawnParticle(Particle.SCULK_SOUL, x, y + 8 * u, z, 1, 6 * u, 6 * u, 6 * u, 0.02);
                if (ticks % 8 == 0) {
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, bodyPoint(frames, Bone.HEAD, 2, 3.5f, 4.4f), 1, 0, 0, 0, 0);
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, bodyPoint(frames, Bone.HEAD, -2, 3.5f, 4.4f), 1, 0, 0, 0,
                            0);
                }
                if (ticks % 20 == 0) groundRing(Color.fromRGB(0x2A0D45), 2.5 * scale, 1.6f);
                if (ticks % 80 == 0) w.playSound(pos, Sound.ENTITY_WARDEN_HEARTBEAT, 0.9f, 0.5f);
                if (ticks % 300 == 0) w.playSound(pos, Sound.AMBIENT_CAVE, 0.8f, 0.6f);
            }
            case EXALTED -> {
                if (ticks % 4 == 0) {
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y + 14 * u, z, 2, 7 * u, 10 * u, 7 * u, 0,
                            new Particle.DustTransition(Color.WHITE, Color.fromRGB(0xFFD54A), 1.4f));
                    // A beam of light falls from its feet to the ground.
                    Particle.DustOptions beam = new Particle.DustOptions(Color.fromRGB(0xFFF4D0), 1.6f);
                    for (double d = 0; d < y - pos.getY(); d += 0.7) {
                        w.spawnParticle(Particle.DUST, x, y - d, z, 1, 0.15, 0.1, 0.15, 0, beam);
                    }
                }
                if (ticks % 8 == 0) w.spawnParticle(Particle.END_ROD, x, y + 16 * u, z, 2, 8 * u, 10 * u, 8 * u, 0.03);
                if (ticks % 20 == 0) groundRing(Color.fromRGB(0xFFE9A0), 2.8 * scale, 1.4f);
                if (ticks % 60 == 0) w.playSound(pos, Sound.BLOCK_BEACON_AMBIENT, 1.2f, 1.2f);
            }
            default -> {
                // A haze of smoke around the figure.
                if (ticks % 4 == 0) {
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y + 14 * u, z, 2, 5 * u, 9 * u, 5 * u, 0,
                            new Particle.DustTransition(look.smokeFrom(), look.smokeTo(), 1.6f));
                }
                if (ticks % 12 == 0) {
                    w.spawnParticle(look.sparkle(), x, y + 30 * u, z, 1, 6 * u, 4 * u, 6 * u, 0.01);
                }
                if (ticks % 180 == 0) {
                    w.playSound(pos, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.5f);
                }
            }
        }
        if (flight > 0.3) {
            // A trail behind it as it flies.
            Location feet = bodyPoint(frames, Bone.BODY, 0, 2, -2);
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, feet, 2, 0.25, 0.25, 0.25, 0,
                    new Particle.DustTransition(look.smokeFrom(), look.smokeTo(), 1.8f));
            if (ticks % 4 == 0) w.spawnParticle(look.sparkle(), feet, 1, 0.2, 0.2, 0.2, 0.01);
            if (flight > 0.5 && ticks % 16 == 0) w.playSound(feet, Sound.ENTITY_PHANTOM_FLAP, 0.9f, 0.6f);
        }
    }

    /** A ring of light on the ground beneath the vessel. */
    private void groundRing(Color color, double radius, float size) {
        World w = pos.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        int n = (int) Math.max(16, Math.PI * 2 * radius / 0.5);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            w.spawnParticle(Particle.DUST, pos.getX() + Math.cos(a) * radius, pos.getY() + 0.1,
                    pos.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0, dust);
        }
    }

    /** Appears somewhere else at once: dissolves here and re-forms there. */
    private void relocate(Location to) {
        if (pos != null) {
            pos.getWorld().spawnParticle(Particle.REVERSE_PORTAL, chestPoint(), 80, 0.4, 1.2, 0.4, 0.08);
            pos.getWorld().playSound(pos, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        }
        despawn();
        pos = to.clone();
        spawnParts(true);
        to.getWorld().spawnParticle(Particle.REVERSE_PORTAL, chestPoint(), 80, 0.4, 1.2, 0.4, 0.08);
        to.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
    }

    // ---- Geometry helpers ----------------------------------------------------------------------

    /** The height its feet float at: higher and bobbing when still, lower while striding, plus its lift. */
    private double bodyY() {
        float still = 1 - walk;
        double hover = mood == VesselMood.SORROWFUL ? 0.1 : HOVER;
        double bobSpeed = mood == VesselMood.JOYFUL ? 0.13 : 0.08;
        double bob = (0.07 + 0.1 * Math.min(1, lift)) * Math.sin(ticks * bobSpeed) * still;
        return pos.getY() + 0.06 + (hover - 0.06) * still + bob + lift;
    }

    private Location bodyLocation() {
        Location l = pos.clone();
        l.setY(baseY);
        l.setYaw(yaw);
        l.setPitch(0);
        return l;
    }

    private Location hitboxSpot() {
        Location l = pos.clone();
        if (lift > 0.2) l.setY(baseY - 0.1);
        return l;
    }

    /** Where the speech bubble floats: above the head, or above the true form. */
    private Location speechSpot() {
        if (trueForm != null) return trueForm.centre().add(0, 9.5, 0);
        return textSpot(VesselModel.HORN_TOP_PX + 4).add(0, nameplate != null ? 0.95 : 0.45, 0);
    }

    /** Whether its name floats above its head: always, never, or only when several gods share its world. */
    private boolean wantsNameplate() {
        String m = GodSettings.vesselNameplate();
        if (m.equals("off") || !hasBody() || trueForm != null) return false;
        if (m.equals("always")) return true;
        return pos != null && net.bigyous.gptgodmc.god.Realm.livingGodsOf(pos.getWorld()).size() > 1;
    }

    /** Creates, updates, moves or removes the name above its head. */
    private void updateNameplate() {
        if (!wantsNameplate()) {
            if (nameplate != null) {
                nameplate.remove();
                nameplate = null;
                nameplateText = null;
            }
            return;
        }
        Persona p = realm.get(Persona::current);
        String name = p == null ? "The unborn god" : p.shortName();
        String epithet = p == null || p.epithet == null || p.epithet.isBlank() ? "" : p.epithet;
        String text = name + "\n" + epithet + "\n" + mood.id();
        Location at = textSpot(VesselModel.HORN_TOP_PX + 2).add(0, 0.3, 0);
        if (nameplate == null || !nameplate.isValid()) {
            nameplate = pos.getWorld().spawn(at, TextDisplay.class, t -> {
                setupDisplay(t);
                t.setBillboard(Display.Billboard.CENTER);
                t.setLineWidth(240);
                t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                t.setShadowed(true);
            });
            nameplateText = null;
        } else {
            nameplate.teleport(at);
        }
        if (!text.equals(nameplateText)) {
            nameplateText = text;
            net.kyori.adventure.text.format.TextColor color = net.bigyous.gptgodmc.god.TabList.nameColor(
                    p == null ? null : p.archetype, mood);
            net.kyori.adventure.text.Component c = Component.text(name, color)
                    .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
            if (!epithet.isEmpty()) {
                c = c.append(Component.newline()).append(Component.text(epithet,
                        net.kyori.adventure.text.format.NamedTextColor.GRAY));
            }
            nameplate.text(c);
        }
    }

    private Location textSpot(float heightPx) {
        Location l = pos.clone();
        l.setY(baseY + heightPx * unit());
        return l;
    }

    private Location chestPoint() {
        return worldPoint(new Vector3f(0, 18, 0));
    }

    /** A point on a bone (its own space, pixels) in the world. */
    private Location bodyPoint(Map<Bone, Frame> frames, Bone bone, float x, float y, float z) {
        Frame f = frames.get(bone);
        return worldPoint(f.rot().transform(new Vector3f(x, y, z)).add(f.pos()));
    }

    /** A body-space point (pixels) in the world. */
    private Location worldPoint(Vector3f px) {
        float u = unit();
        Vector3f w = toWorld(px.x * u, px.y * u, px.z * u);
        return new Location(pos.getWorld(), pos.getX() + w.x, baseY + w.y, pos.getZ() + w.z);
    }

    /** A direction on a bone, in the world. */
    private Vector3f worldDir(Frame f, float x, float y, float z) {
        Vector3f d = f.rot().transform(new Vector3f(x, y, z).normalize());
        return toWorld(d.x, d.y, d.z);
    }

    private static Location midpoint(Location a, Location b) {
        return a.clone().add(b).multiply(0.5);
    }

    /** A world offset expressed in the body's frame (x = its left, z = ahead). */
    private Vector3f toBody(double dx, double dy, double dz) {
        double r = Math.toRadians(yaw);
        return new Vector3f((float) (dx * Math.cos(r) + dz * Math.sin(r)), (float) dy,
                (float) (-dx * Math.sin(r) + dz * Math.cos(r)));
    }

    private Vector3f toWorld(double bx, double by, double bz) {
        double r = Math.toRadians(yaw);
        return new Vector3f((float) (bx * Math.cos(r) - bz * Math.sin(r)), (float) by,
                (float) (bx * Math.sin(r) + bz * Math.cos(r)));
    }

    // ---- Finding its way -----------------------------------------------------------------------------

    private java.util.List<Pathing.Step> path;
    private int pathIdx;
    private Location pathFor;
    private long pathAt;
    /** No walking route: it floats over what is in the way. */
    private boolean soaring;

    /** Blocks of room its body needs: its real height (a lordly body is nearly three blocks tall). */
    private int clearance() {
        return Math.max(2, (int) Math.ceil(size() * 1.8 - 0.05));
    }

    /** vessel.phase-through-walls: the old way, gliding through anything. */
    private static boolean phases() {
        return GodSettings.vesselPhaseThroughWalls();
    }

    /** When it last bumped into something it could not get past. */
    private long blockedSince;
    /** Its chosen size before it shrank to fit indoors (null = not squeezed). */
    private Double squeezedFrom;
    private boolean squeezed;
    private long lastRoomCheck;

    /** Whether its body fits at this point: every block of its height is open (and loaded). */
    private boolean fits(Location at) {
        if (at.getWorld() == null || !at.isChunkLoaded()) return false;
        int x = at.getBlockX(), z = at.getBlockZ(), y0 = (int) Math.floor(at.getY() + 0.05);
        int top = (int) Math.ceil(at.getY() + size() * 1.8 - 0.1);
        for (int y = y0; y < top; y++) {
            org.bukkit.block.Block b = at.getWorld().getBlockAt(x, y, z);
            if (!b.isPassable() || b.getType() == org.bukkit.Material.LAVA) return false;
        }
        return true;
    }

    /** How far it may float before its head meets a ceiling (in blocks). */
    private double headroom() {
        if (pos.getWorld() == null || !pos.isChunkLoaded()) return 0;
        double body = size() * 1.8;
        int x = pos.getBlockX(), z = pos.getBlockZ();
        int base = (int) Math.floor(pos.getY() + 0.05);
        for (int h = 0; h < 24; h++) {
            org.bukkit.block.Block b = pos.getWorld().getBlockAt(x, base + h, z);
            if (!b.isPassable()) return Math.max(0, h - body - 0.1);
        }
        return 24;
    }

    /** Open ground near a wanted spot that its body fits on (nearest first), or the spot itself. */
    private Location safeSpot(Location want) {
        if (want == null || want.getWorld() == null || phases()) return want;
        WorldGrid g = new WorldGrid(want.getWorld());
        int clear = clearance();
        int wx = want.getBlockX(), wy = (int) Math.floor(want.getY() + 0.01), wz = want.getBlockZ();
        for (int r = 0; r <= 6; r++) {
            Location best = null;
            double bestD = Double.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    Pathing.Step s = Pathing.settle(g, wx + dx, wy + 1, wz + dz, clear);
                    if (s == null || Math.abs(s.y() - wy) > 4) continue;
                    double d = dx * dx + dz * dz + (s.y() - wy) * (s.y() - wy);
                    if (d < bestD) {
                        bestD = d;
                        best = new Location(want.getWorld(), s.x() + 0.5, s.y(), s.z() + 0.5, want.getYaw(), 0);
                    }
                }
            }
            if (best != null) return best;
        }
        // Nowhere its full height fits (a cramped place): any spot a mortal could stand.
        if (clear > 2) {
            Pathing.Step s = Pathing.settle(g, wx, wy + 1, wz, 2);
            if (s != null) return new Location(want.getWorld(), s.x() + 0.5, s.y(), s.z() + 0.5, want.getYaw(), 0);
        }
        return want;
    }

    /** Shrinks to a mortal's size to pass through a doorway its full size cannot. */
    private void squeeze() {
        if (squeezed || size() <= 1.05) return;
        squeezedFrom = sizeChosen() ? size() : null;
        squeezed = true;
        setSize(1.0);
    }

    /** Back to its own size once there is room around it again. */
    private void unsqueezeWhenRoomy() {
        if (!squeezed) return;
        long now = System.currentTimeMillis();
        if (now - lastRoomCheck < 1500) return;
        lastRoomCheck = now;
        double want = squeezedFrom != null ? squeezedFrom : GodSettings.vesselSize();
        int need = Math.max(2, (int) Math.ceil(want * 1.8 - 0.05));
        WorldGrid g = new WorldGrid(pos.getWorld());
        int x = pos.getBlockX(), y = (int) Math.floor(pos.getY() + 0.05), z = pos.getBlockZ();
        if (Pathing.standable(g, x, y, z, need) || !Pathing.standable(g, x, y, z, 2)) {
            squeezed = false;
            setSize(squeezedFrom);
            squeezedFrom = null;
        }
    }

    /** The next point to walk to on the way to target: a step of a route around walls, or target itself. */
    private Location route(Location target) {
        if (target.getWorld() == null || !target.getWorld().equals(pos.getWorld())) return target;
        double flat = Math.hypot(target.getX() - pos.getX(), target.getZ() - pos.getZ());
        if (flat < 1.2) {
            soaring = false;
            return target;
        }
        long now = System.currentTimeMillis();
        boolean moved = pathFor == null || pathFor.distanceSquared(target) > 4;
        if (pathAt == 0 || (moved && now - pathAt > 500) || now - pathAt > 3000) {
            pathAt = now;
            pathFor = target.clone();
            pathIdx = 0;
            int clear = clearance();
            WorldGrid g = new WorldGrid(pos.getWorld());
            Pathing.Step from = Pathing.settle(g, pos.getBlockX(), (int) Math.floor(pos.getY() + 0.01), pos.getBlockZ(),
                    clear);
            Pathing.Step to = Pathing.settle(g, target.getBlockX(), (int) Math.floor(target.getY() + 0.01),
                    target.getBlockZ(), clear);
            path = from == null || to == null || flat > 64 ? null : Pathing.find(g, from, to, clear, 2500);
            if (path == null && !phases() && clear > 2 && flat <= 64) {
                Pathing.Step from2 = Pathing.settle(g, pos.getBlockX(), (int) Math.floor(pos.getY() + 0.01),
                        pos.getBlockZ(), 2);
                Pathing.Step to2 = Pathing.settle(g, target.getBlockX(), (int) Math.floor(target.getY() + 0.01),
                        target.getBlockZ(), 2);
                List<Pathing.Step> narrow = from2 == null || to2 == null ? null : Pathing.find(g, from2, to2, 2, 2500);
                if (narrow != null) {
                    squeeze(); // a doorway too small for its full height: it shrinks to pass
                    path = narrow;
                }
            }
            soaring = path == null;
        }
        if (soaring || path == null) return target;
        while (pathIdx < path.size()) {
            Pathing.Step s = path.get(pathIdx);
            if (Math.hypot(s.x() + 0.5 - pos.getX(), s.z() + 0.5 - pos.getZ()) < 0.35
                    && Math.abs(s.y() - pos.getY()) < 0.6) {
                pathIdx++;
            } else {
                break;
            }
        }
        if (pathIdx >= path.size()) return target;
        Pathing.Step s = path.get(pathIdx);
        return new Location(pos.getWorld(), s.x() + 0.5, s.y(), s.z() + 0.5);
    }

    /** The ground under a point. */
    private static Location groundSpot(Location at) {
        Location l = at.clone();
        Block b = l.getBlock();
        for (int i = 0; i < 6 && b.isPassable() && b.getY() > l.getWorld().getMinHeight(); i++) {
            b = b.getRelative(0, -1, 0);
        }
        l.setY(b.isPassable() ? at.getY() : b.getY() + 1);
        return l;
    }

    /** A spot beside and slightly ahead of a player, so they can see the vessel. */
    private Location besideSpot(Player p) {
        Location l = p.getLocation();
        double yawRad = Math.toRadians(l.getYaw());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        Location spot = l.clone().add(fx * 2.0 + fz * 2.5, 0, fz * 2.0 - fx * 2.5);
        spot.setY(l.getY());
        return safeSpot(spot);
    }

    private Player nearestPlayer(double radius) {
        Player best = null;
        double bestDist = radius * radius;
        for (Player p : pos.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            double d = p.getLocation().distanceSquared(pos);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private static float wrap(float degrees) {
        return ((degrees % 360) + 540) % 360 - 180;
    }

    private static float approachAngle(float from, float to, float maxStep) {
        float diff = wrap(to - from);
        return from + Math.max(-maxStep, Math.min(maxStep, diff));
    }

    private static String describe(Location l) {
        return l == null ? "?" : String.format("(%d, %d, %d in %s)", l.getBlockX(), l.getBlockY(), l.getBlockZ(),
                l.getWorld().getName());
    }

    private static Color parseColor(String hex, Color fallback) {
        if (hex == null || hex.isBlank() || hex.equalsIgnoreCase("auto")) return fallback;
        try {
            return Color.fromRGB(Integer.parseInt(hex.replace("#", "").trim(), 16));
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
