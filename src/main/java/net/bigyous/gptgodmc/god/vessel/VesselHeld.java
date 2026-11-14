package net.bigyous.gptgodmc.god.vessel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.client.ModRelics;
import net.bigyous.gptgodmc.god.vessel.VesselModel.Bone;
import net.bigyous.gptgodmc.god.vessel.VesselModel.Frame;

/**
 * What the vessel holds in its right hand: a relic from the god's armory, as a display that follows the fist
 * every tick (a sword held ready, raised overhead, swept wide). Players who can see relics (the resource pack
 * or the client mod) see the relic itself; everyone else sees a plain item of its kind in the same grip. While
 * it strikes, its point leaves a trail, and an animation's hit cue ({@link Choreo.Cue#hit}) hands the blade's
 * reach to whoever armed the blow. Like the wings, it owns its entities; the vessel ticks, teleports, shows and
 * forgets it.
 */
public final class VesselHeld {
    /** Where the fist closes around a grip, in the right arm's own space (pixels). */
    static final Vector3f GRIP = new Vector3f(-1f, -8.7f, 0.4f);
    /** The way a held thing points from the fist, in the arm's space: ahead and a little down, held ready. */
    static final Vector3f POINT = new Vector3f(0, -0.6f, 1).normalize();
    /** The flat of a blade faces the arm's side, so its edge leads a swing. */
    static final Vector3f SIDE = new Vector3f(1, 0, 0);

    /**
     * Something held.
     *
     * @param id     what it is (a relic's id, with "__on" for an awakened form)
     * @param name   what it is called, for the god
     * @param shown  the item for those who can see it (the relic's own model)
     * @param plain  the item everyone else sees in its place (a vanilla sword, a hoe)
     * @param length from grip to point, in body pixels (the vessel is 32 tall)
     * @param gripU  where the grip is in the item's picture, 0..1 from its left
     * @param gripV  0..1 from its top
     * @param tipU   where the point is
     * @param tipV   0..1 from its top
     * @param trail  RGB of the trail its point leaves while it strikes
     */
    public record Weapon(String id, String name, ItemStack shown, ItemStack plain, float length, float gripU, float gripV,
            float tipU, float tipV, int trail) {
    }

    /** Where a blow lands: the grip and the point of what is held, in the world. */
    public record Hit(Location grip, Location point) {
    }

    private Weapon weapon;
    private ItemDisplay shown, plain;
    private Transformation sent;
    private Consumer<Hit> armed;
    private long armedUntil, trailUntil;
    private Location lastPoint;

    /** What is held now, or null. */
    public Weapon weapon() {
        return weapon;
    }

    /** Puts something in the hand (null empties it). Its displays appear on the next tick. */
    public void hold(Weapon w) {
        forget();
        weapon = w;
        armed = null;
    }

    /** Readies a blow: the next hit cue (within the time given) hands the blade's reach to it. */
    public void arm(Consumer<Hit> blow, int ticks) {
        armed = blow;
        armedUntil = System.currentTimeMillis() + ticks * 50L;
        trailUntil = armedUntil;
    }

    /** True while a blow is readied and has not landed. */
    public boolean armed() {
        return armed != null && System.currentTimeMillis() < armedUntil;
    }

    /** The hand's length plus what it holds, in body pixels: how far a blow reaches from the shoulder. */
    public float reachPx() {
        return 10f + (weapon == null ? 0 : weapon.length());
    }

    // ---- Placement (pure) ---------------------------------------------------------------------------

    /**
     * The display transformation that puts a weapon's grip in the fist and points it along the arm's ready
     * direction (body space, blocks). The item is drawn flat in its picture's plane, turned half a turn by the
     * client (undone by {@link VesselModel#ITEM_FLIP}), so the picture's grip and point are taken through the
     * same turn first.
     */
    public static Transformation transform(Weapon w, Frame arm, float unit) {
        Vector3f grip = arm.rot().transform(new Vector3f(GRIP)).add(arm.pos()).mul(unit);
        Vector3f dir = arm.rot().transform(new Vector3f(POINT)).normalize();
        Vector3f side = arm.rot().transform(new Vector3f(SIDE));
        // The picture's grip and point after the client's half turn (x and z change sign), in blocks.
        Vector3f g = new Vector3f(0.5f - w.gripU(), 0.5f - w.gripV(), 0);
        Vector3f t = new Vector3f(0.5f - w.tipU(), 0.5f - w.tipV(), 0);
        Vector3f a = new Vector3f(t).sub(g);
        float modelLength = Math.max(0.05f, a.length());
        a.normalize();
        Vector3f b = new Vector3f(0, 0, -1);
        Vector3f c = new Vector3f(a).cross(b).normalize();
        Vector3f bb = new Vector3f(c).cross(a).normalize();
        // Where they go: along the arm's ready direction, the flat facing the arm's side.
        Vector3f A = dir;
        Vector3f B = new Vector3f(side).sub(new Vector3f(A).mul(side.dot(A)));
        if (B.lengthSquared() < 1e-6f) B.set(0, 0, 1);
        B.normalize();
        Vector3f C = new Vector3f(A).cross(B).normalize();
        Matrix3f from = new Matrix3f(a, bb, c), to = new Matrix3f(A, B, C);
        Matrix3f turn = to.mul(from.transpose(new Matrix3f()), new Matrix3f());
        Quaternionf rot = new Quaternionf().setFromNormalized(turn).normalize();
        float scale = w.length() * unit / modelLength;
        Vector3f translation = new Vector3f(grip).sub(rot.transform(new Vector3f(g).mul(scale)));
        return new Transformation(translation, rot, new Vector3f(scale), VesselModel.ITEM_FLIP);
    }

    /** Where the weapon's point is, in body space (pixels). */
    public static Vector3f pointPx(Weapon w, Frame arm) {
        Vector3f grip = arm.rot().transform(new Vector3f(GRIP)).add(arm.pos());
        return grip.add(arm.rot().transform(new Vector3f(POINT)).normalize().mul(w.length()));
    }

    /** Where the grip is, in body space (pixels). */
    public static Vector3f gripPx(Frame arm) {
        return arm.rot().transform(new Vector3f(GRIP)).add(arm.pos());
    }

    // ---- In the world -------------------------------------------------------------------------------

    /** Every tick of the vessel: spawns, follows the fist, draws the trail of a blow in flight. */
    void tick(Consumer<Display> setup, Location body, Map<Bone, Frame> frames, float unit, boolean showing) {
        if (weapon == null || !showing) {
            if (shown != null || plain != null) forget();
            return;
        }
        Frame arm = frames.get(Bone.RIGHT_ARM);
        Transformation now = transform(weapon, arm, unit);
        if (shown == null || !shown.isValid() || plain == null || !plain.isValid()) {
            forget();
            World w = body.getWorld();
            shown = w.spawn(body, ItemDisplay.class, d -> dress(setup, d, weapon.shown(), now));
            plain = w.spawn(body, ItemDisplay.class, d -> dress(setup, d, weapon.plain(), now));
            for (Player p : Bukkit.getOnlinePlayers()) showTo(p);
            sent = now;
        } else if (sent == null || moved(sent, now)) {
            for (ItemDisplay d : new ItemDisplay[] { shown, plain }) {
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(3);
                d.setTransformation(now);
            }
            sent = now;
        }
        if (System.currentTimeMillis() < trailUntil) {
            Location point = world(body, pointPx(weapon, arm), unit);
            if (lastPoint != null && lastPoint.getWorld() == point.getWorld() && lastPoint.distanceSquared(point) < 64) {
                Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(weapon.trail() & 0xFFFFFF), 1.1f);
                int steps = (int) Math.min(8, Math.max(2, lastPoint.distance(point) * 3));
                for (int i = 0; i <= steps; i++) {
                    Location at = lastPoint.clone().add(point.clone().subtract(lastPoint).toVector().multiply(i / (double) steps));
                    body.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
                }
            }
            lastPoint = point;
        } else {
            lastPoint = null;
        }
        if (armed != null && System.currentTimeMillis() >= armedUntil) armed = null;
    }

    private static void dress(Consumer<Display> setup, ItemDisplay d, ItemStack item, Transformation at) {
        setup.accept(d);
        d.setVisibleByDefault(false);
        d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        d.setItemStack(item);
        d.setTransformation(at);
    }

    private static boolean moved(Transformation a, Transformation b) {
        return Math.abs(a.getLeftRotation().dot(b.getLeftRotation())) < 0.99995f
                || a.getTranslation().distanceSquared(b.getTranslation()) > 1e-5f;
    }

    /** The world point of a body-space point (pixels), for a body standing at `body` (its yaw included). */
    static Location world(Location body, Vector3f px, float unit) {
        double r = Math.toRadians(body.getYaw());
        double bx = px.x * unit, by = px.y * unit, bz = px.z * unit;
        return new Location(body.getWorld(), body.getX() + bx * Math.cos(r) - bz * Math.sin(r), body.getY() + by,
                body.getZ() + bx * Math.sin(r) + bz * Math.cos(r));
    }

    /** A hit cue of the vessel's animation: the readied blow, if any, lands now. */
    void hit(Location body, Map<Bone, Frame> frames, float unit) {
        if (weapon == null || armed == null) return;
        Consumer<Hit> blow = armed;
        armed = null;
        Frame arm = frames.get(Bone.RIGHT_ARM);
        Hit h = new Hit(world(body, gripPx(arm), unit), world(body, pointPx(weapon, arm), unit));
        try {
            blow.accept(h);
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("A blow of the god's weapon failed", e);
        }
    }

    void teleport(Location body) {
        if (shown != null && shown.isValid()) shown.teleport(body);
        if (plain != null && plain.isValid()) plain.teleport(body);
    }

    /** Shows the relic to a player who can see relics, and the plain stand-in to everyone else. */
    void showTo(Player p) {
        if (shown == null || plain == null) return;
        boolean sees = VesselPackHost.has(p) || ModRelics.draws(p);
        if (shown.isValid()) {
            if (sees) p.showEntity(GPTGOD.get(), shown);
            else p.hideEntity(GPTGOD.get(), shown);
        }
        if (plain.isValid()) {
            if (sees) p.hideEntity(GPTGOD.get(), plain);
            else p.showEntity(GPTGOD.get(), plain);
        }
    }

    List<Entity> entities() {
        List<Entity> out = new ArrayList<>();
        if (shown != null) out.add(shown);
        if (plain != null) out.add(plain);
        return out;
    }

    /** Removes the displays (the weapon stays held; it reappears when the body does). */
    void forget() {
        if (shown != null) shown.remove();
        if (plain != null) plain.remove();
        shown = null;
        plain = null;
        sent = null;
        lastPoint = null;
    }
}
