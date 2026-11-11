package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * A demigod's wings and halo in the world: {@link DemigodWings#COUNT} block displays riding one
 * invisible root display that follows the player. Moving costs one teleport of the root (the pieces
 * ride along on the client); the pieces' transforms are sent, interpolated, only when the pose or the
 * facing changes: every 2 ticks in flight, never while a demigod stands still. Entities are
 * non-persistent and PDC-tagged; a rig is taken down when its player stops being a demigod, dies,
 * turns invisible or spectates, leaves, or changes worlds. Server thread only.
 */
public final class DemigodRig {
    static final String TAG = "demigod_rig";

    private static final Map<UUID, DemigodRig> RIGS = new HashMap<>();
    private static BukkitTask task;
    private static long tick;

    private final BlockDisplay root;
    private final List<BlockDisplay> parts = new ArrayList<>(DemigodWings.COUNT);
    private final Transformation[] sent = new Transformation[DemigodWings.COUNT];
    private Location lastAt;
    private float open, beat, lastYaw, lastPitch;
    private String lastMode = "";
    private long lastShape;

    // ---- Lifecycle --------------------------------------------------------------------------------

    /** Clears rigs left by a reload and starts following demigods every tick. */
    public static void start() {
        cleanupStrays();
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(GPTGOD.get(), DemigodRig::tickAll, 1, 1);
    }

    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        RIGS.values().forEach(DemigodRig::destroy);
        RIGS.clear();
    }

    /** Takes a player's wings and halo down at once (they are rebuilt next tick if still a demigod). */
    public static void remove(Player p) {
        DemigodRig r = RIGS.remove(p.getUniqueId());
        if (r != null) r.destroy();
    }

    public static boolean isRigEntity(Entity e) {
        return e.getPersistentDataContainer().has(key(), PersistentDataType.BYTE);
    }

    private static void cleanupStrays() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntitiesByClass(BlockDisplay.class)) {
                if (isRigEntity(e)) e.remove();
            }
        }
    }

    private static NamespacedKey key() {
        return new NamespacedKey(GPTGOD.get(), TAG);
    }

    // ---- Every tick -------------------------------------------------------------------------------

    private static void tickAll() {
        tick++;
        Demigods state = DemigodPowers.state();
        if (RIGS.isEmpty() && state.demigods.isEmpty()) return;
        long now = System.currentTimeMillis();
        Set<UUID> keep = new HashSet<>();
        for (Demigods.Entry d : List.copyOf(state.demigods)) {
            if (!d.lasts(now)) continue;
            Player p = d.uuid == null ? Bukkit.getPlayerExact(d.name) : Bukkit.getPlayer(UUID.fromString(d.uuid));
            if (p == null || !shown(p)) continue;
            keep.add(p.getUniqueId());
            DemigodRig r = RIGS.get(p.getUniqueId());
            if (r != null && !r.fits(p)) {
                r.destroy();
                r = null;
            }
            if (r == null) {
                r = new DemigodRig(p);
                RIGS.put(p.getUniqueId(), r);
            }
            r.follow(p);
        }
        RIGS.entrySet().removeIf(e -> {
            if (keep.contains(e.getKey())) return false;
            e.getValue().destroy();
            return true;
        });
    }

    private static boolean shown(Player p) {
        return p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR && !p.isInvisible()
                && !p.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    // ---- One rig -----------------------------------------------------------------------------------

    private DemigodRig(Player p) {
        Location at = feet(p);
        World w = at.getWorld();
        root = w.spawn(at, BlockDisplay.class, b -> {
            setup(b);
            // Close to how long other clients take to catch up with a moving player.
            b.setTeleportDuration(3);
        });
        Mode m = Mode.of(p);
        open = m.target();
        lastYaw = m.yaw();
        lastPitch = p.getLocation().getPitch();
        lastMode = m.name();
        lastShape = tick;
        List<DemigodWings.Piece> pieces = DemigodWings.pieces(pose(p, m));
        for (int i = 0; i < pieces.size(); i++) {
            DemigodWings.Piece piece = pieces.get(i);
            Transformation t = DemigodWings.transform(piece, m.yaw());
            BlockDisplay d = w.spawn(at, BlockDisplay.class, b -> {
                setup(b);
                b.setBlock(piece.material().createBlockData());
                b.setTransformation(t);
            });
            sent[i] = t;
            parts.add(d);
            root.addPassenger(d);
        }
        lastAt = at;
    }

    private static void setup(Display d) {
        d.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte) 1);
        d.setPersistent(false);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setShadowRadius(0);
        d.setShadowStrength(0);
    }

    /** Still the same world and every piece still riding. */
    private boolean fits(Player p) {
        if (!root.isValid() || !root.getWorld().equals(p.getWorld())) return false;
        if (tick % 20 != 0) return true;
        for (BlockDisplay d : parts) {
            if (!d.isValid() || d.getVehicle() != root) return false;
        }
        return true;
    }

    private void destroy() {
        for (BlockDisplay d : parts) d.remove();
        parts.clear();
        root.remove();
    }

    private static Location feet(Player p) {
        Location at = p.getLocation();
        at.setYaw(0);
        at.setPitch(0);
        return at;
    }

    /** How the player is moving right now. */
    private record Mode(String name, boolean flying, boolean gliding, boolean swimming, boolean sneaking, float yaw) {
        static Mode of(Player p) {
            boolean flying = p.isFlying(), gliding = p.isGliding(), swimming = p.isSwimming();
            boolean sneaking = p.isSneaking() && !flying;
            String name = gliding ? "glide" : swimming ? "swim" : flying ? "fly" : sneaking ? "sneak" : "stand";
            // The wings sit on the body, which lags the head; gliding and swimming bodies follow the look.
            float yaw = gliding || swimming ? p.getLocation().getYaw() : p.getBodyYaw();
            return new Mode(name, flying, gliding, swimming, sneaking, yaw);
        }

        float target() {
            return flying || gliding ? 1 : 0;
        }
    }

    private DemigodWings.Pose pose(Player p, Mode m) {
        return DemigodWings.pose(open, beat, m.flying(), m.gliding(), m.swimming(), m.sneaking(),
                p.getLocation().getPitch());
    }

    private void follow(Player p) {
        Location at = feet(p);
        if (lastAt == null || at.distanceSquared(lastAt) > 1e-6) {
            root.teleport(at); // keeps its riders (Paper 1.21.10+)
            lastAt = at;
        }
        Mode m = Mode.of(p);
        float target = m.target();
        boolean easing = open != target;
        open = target > open ? Math.min(target, open + 0.12f) : Math.max(target, open - 0.12f);
        float step = (float) (2 * Math.PI) / (m.flying() ? DemigodWings.BEAT_TICKS : m.gliding() ? 40 : 80);
        beat = (beat + step) % (float) (2 * Math.PI);

        float pitch = p.getLocation().getPitch();
        boolean leaning = m.gliding() || m.swimming();
        boolean changed = Math.abs(wrap(m.yaw() - lastYaw)) > 2 || !m.name().equals(lastMode)
                || (leaning && Math.abs(pitch - lastPitch) > 3);
        int every = m.flying() || easing ? 2 : m.gliding() ? 4 : 20;
        if (!changed && tick - lastShape < every) return;
        int duration = changed && every > 2 ? 2 : every;
        lastYaw = m.yaw();
        lastPitch = pitch;
        lastMode = m.name();
        lastShape = tick;
        List<DemigodWings.Piece> pieces = DemigodWings.pieces(pose(p, m));
        for (int i = 0; i < pieces.size() && i < parts.size(); i++) {
            Transformation t = DemigodWings.transform(pieces.get(i), m.yaw());
            if (!DemigodWings.differs(t, sent[i])) continue;
            BlockDisplay d = parts.get(i);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(duration);
            d.setTransformation(t);
            sent[i] = t;
        }
    }

    private static float wrap(float degrees) {
        float d = degrees % 360;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }
}
