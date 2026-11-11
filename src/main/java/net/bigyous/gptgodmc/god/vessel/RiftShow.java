package net.bigyous.gptgodmc.god.vessel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.destroystokyo.paper.ParticleBuilder;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.client.ModLink;
import net.bigyous.gptgodmc.client.ModSessions;
import net.bigyous.gptgodmc.god.FxScenes;
import net.bigyous.gptgodmc.god.vessel.Rift.Palette;
import net.bigyous.gptgodmc.god.vessel.Rift.Timeline;

/**
 * A rift tearing open in the world on a {@link Timeline}, for a god to step out of (arriving) or back into
 * (leaving). It stands still where it opened, facing the way the god walks (a door in the air: from the
 * side it is a thin bright seam). Each mortal sees the best version they can: with the client mod its own
 * rift ({@link RiftScenes}); with the god's resource pack a painted plane ({@link RiftTextures}) stretched
 * open and shut; otherwise its edges drawn in dust. Everyone without the mod hears it in vanilla sounds and sees its sparks and the motes that
 * drift out of it. At the climax it runs what it was opened for (the body forming, or being swallowed).
 * Server thread only.
 */
public final class RiftShow {
    /** How far away mortals see and hear it (more for a huge god). */
    private final double range;
    private final Location base;
    private final World world;
    /** The way it faces: the way the god walks out (or the god's back, as it walks in). */
    private final float yaw;
    private final double body;
    private final Palette palette;
    private final Timeline when;
    private final boolean arriving;
    private final Runnable atClimax;
    private final String sceneId = "rift-" + Long.toHexString(System.nanoTime());
    private final Set<UUID> modded = new HashSet<>();
    private final Set<UUID> shownPainted = new HashSet<>();
    private final Random rnd = new Random();
    private ItemDisplay painted;
    private BukkitRunnable task;
    private int tick;
    private boolean climaxed;
    private boolean done;

    private RiftShow(Location base, float yaw, double body, Palette palette, Timeline when, boolean arriving,
            Runnable atClimax) {
        this.base = base.clone();
        this.world = base.getWorld();
        this.yaw = yaw;
        this.body = body;
        this.palette = palette;
        this.when = when;
        this.arriving = arriving;
        this.atClimax = atClimax;
        this.range = 96 + body * 8;
    }

    /**
     * Opens a rift standing on the ground at base, sized for a body this many blocks tall, for a god to
     * step out of toward yaw. atClimax runs once, when the body should form (at once if it cannot be shown).
     */
    public static RiftShow open(Location base, float yaw, double body, Palette palette, Runnable atClimax) {
        return start(new RiftShow(base, yaw, body, palette, Rift.ARRIVAL, true, atClimax));
    }

    /** Opens a rift for a god to walk back into; atClimax runs when it should be swallowed. */
    public static RiftShow departure(Location base, float yaw, double body, Palette palette, Timeline when,
            Runnable atClimax) {
        return start(new RiftShow(base, yaw, body, palette, when, false, atClimax));
    }

    private static RiftShow start(RiftShow s) {
        if (s.world == null || !GPTGOD.get().isEnabled()) {
            s.done = true;
            s.climax();
            return s;
        }
        s.begin();
        return s;
    }

    public boolean done() {
        return done;
    }

    private void begin() {
        var layers = arriving ? RiftScenes.arrival(palette, body, yaw) : RiftScenes.departure(palette, body, yaw, when);
        var message = FxScenes.message(sceneId, new double[] { base.getX(), base.getY(), base.getZ() }, -1, 1, false, layers);
        for (Player p : world.getPlayers()) {
            if (!near(p) || !ModLink.has(p, ModSessions.FX)) continue;
            if (ModLink.send(p, ModSessions.FX, message)) modded.add(p.getUniqueId());
        }
        if (VesselPackHost.active()) {
            Location at = base.clone();
            at.setYaw(yaw);
            at.setPitch(0);
            painted = world.spawn(at, ItemDisplay.class, d -> {
                d.getPersistentDataContainer().set(new NamespacedKey(GPTGOD.get(), Vessel.TAG), PersistentDataType.BYTE,
                        (byte) 1);
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(4.0f);
                d.setShadowRadius(0);
                d.setBillboard(Display.Billboard.FIXED);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setItemStack(model());
                d.setTransformation(transformation(0));
            });
            refreshPainted();
        }
        task = new BukkitRunnable() {
            @Override
            public void run() {
                step();
            }
        };
        task.runTaskTimer(GPTGOD.get(), 1L, 1L);
    }

    private ItemStack model() {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString(RiftPack.modelId(palette)));
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean near(Player p) {
        return p.getWorld().equals(world) && p.getLocation().distanceSquared(base) <= range * range;
    }

    // ---- Each tick -----------------------------------------------------------------------------------

    private void step() {
        if (done) return;
        tick++;
        try {
            if (tick == when.climax()) climax();
            if (painted != null && painted.isValid()) {
                if (tick % 10 == 0) refreshPainted();
                if (tick % 2 == 0 && tick <= when.shut()) {
                    painted.setInterpolationDelay(0);
                    painted.setInterpolationDuration(2);
                    painted.setTransformation(transformation(tick + 2));
                }
                if (tick == when.shut()) painted.remove();
            }
            if (tick % 2 == 0) draw();
            cues();
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("The rift failed", e);
            climax();
            finish();
            return;
        }
        if (tick >= when.length()) finish();
    }

    private void climax() {
        if (climaxed) return;
        climaxed = true;
        if (atClimax == null) return;
        try {
            atClimax.run();
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("The rift's climax failed", e);
        }
    }

    /** The painted plane at a moment: stretched open by the timeline, centred on the rift's middle. */
    private Transformation transformation(double at) {
        double[] o = when.open(at);
        float w = (float) Math.max(0.001, Rift.width(body) / RiftTextures.TEAR * o[1]);
        float h = (float) Math.max(0.001, Rift.height(body) / RiftTextures.TALL * o[0]);
        return new Transformation(new Vector3f(0, (float) Rift.centre(body), 0), new Quaternionf(), new Vector3f(w, h, 1),
                new Quaternionf());
    }

    /** Pack players without the mod see the painted rift; mod players see their own; the rest see dust. */
    private void refreshPainted() {
        for (Player p : world.getPlayers()) {
            boolean want = VesselPackHost.has(p) && !modded.contains(p.getUniqueId()) && !ModLink.has(p, ModSessions.FX);
            boolean was = shownPainted.contains(p.getUniqueId());
            if (want == was) continue;
            if (want) {
                p.showEntity(GPTGOD.get(), painted);
                shownPainted.add(p.getUniqueId());
            } else {
                p.hideEntity(GPTGOD.get(), painted);
                shownPainted.remove(p.getUniqueId());
            }
        }
    }

    /** A point of the rift's plane: across (blocks, to its right), up from its base, and out of its face. */
    private Location point(double across, double up, double out) {
        double r = Math.toRadians(yaw);
        return new Location(world, base.getX() + Math.cos(r) * across - Math.sin(r) * out, base.getY() + up,
                base.getZ() + Math.sin(r) * across + Math.cos(r) * out);
    }

    private void draw() {
        List<Player> plain = new ArrayList<>(), unmodded = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            if (!near(p) || modded.contains(p.getUniqueId())) continue;
            unmodded.add(p);
            if (!shownPainted.contains(p.getUniqueId())) plain.add(p);
        }
        if (unmodded.isEmpty()) return;
        double bottom = Rift.bottom(body), h = Rift.height(body), w = Rift.width(body);
        double[] o = when.open(tick);
        if (!plain.isEmpty() && tick < when.shut()) {
            // The edges in dust: white-hot points along each side, joined by the god's glow.
            List<Rift.Row> rows = Rift.rim(when, body, tick, RiftTextures.SEED, (int) Math.min(30, 14 + body * 1.5));
            Particle.DustOptions edge = new Particle.DustOptions(Color.fromRGB(palette.rim), (float) Math.min(2.2, 0.8 + body * 0.12));
            Particle.DustOptions glow = new Particle.DustOptions(Color.fromRGB(palette.glow), (float) Math.min(2.6, 1.0 + body * 0.14));
            Rift.Row last = null;
            for (Rift.Row row : rows) {
                double up = bottom + row.up();
                if (up >= -0.2) {
                    dust(point(row.left(), up, 0), edge, plain);
                    dust(point(row.right(), up, 0), edge, plain);
                    if (last != null && bottom + last.up() >= -0.2) {
                        double mid = bottom + (row.up() + last.up()) / 2;
                        dust(point((row.left() + last.left()) / 2, mid, 0), glow, plain);
                        dust(point((row.right() + last.right()) / 2, mid, 0), glow, plain);
                    }
                }
                last = row;
            }
            // Through it, the far side: the god's colour between the edges.
            if (o[1] > 0.3) {
                Particle.DustOptions deep = new Particle.DustOptions(Color.fromRGB(palette.cloudLight), 1.4f);
                for (int i = 0; i < 5; i++) {
                    double v = 0.15 + 0.7 * rnd.nextDouble();
                    double[] s = Rift.shape(v, RiftTextures.SEED);
                    double across = ((s[0] + s[1]) / 2 + (rnd.nextDouble() * 2 - 1) * (s[1] - s[0]) / 2 * 0.7) * w / 2 * o[1];
                    dust(point(across, bottom + v * h, 0), deep, plain);
                }
            }
        }
        // For everyone without the mod: a seam of light while it cracks, the air drawn in while it gathers,
        // sparks off its edges and motes drifting out of the far side while it is open.
        if (tick < when.crack()) {
            for (int i = 0; i < 3; i++) {
                double up = Rift.centre(body) + (rnd.nextDouble() - 0.5) * h * o[0];
                send(new ParticleBuilder(Particle.END_ROD).location(point(0, up, 0)).count(1).offset(0.02, 0.05, 0.02)
                        .speed(0.005), unmodded);
            }
        }
        if (tick < when.climax()) {
            send(new ParticleBuilder(Particle.REVERSE_PORTAL).location(point(0, Rift.centre(body), 0)).count(3)
                    .offset(w * 0.5, h * 0.3, w * 0.5).speed(0.04), unmodded);
        }
        if (tick > when.crack() && tick < when.seal()) {
            if (tick % 4 == 0) {
                List<Rift.Row> rows = Rift.rim(when, body, tick, RiftTextures.SEED, 8);
                Rift.Row row = rows.get(rnd.nextInt(rows.size()));
                double up = bottom + row.up();
                if (up > 0) {
                    send(new ParticleBuilder(Particle.ELECTRIC_SPARK)
                            .location(point(rnd.nextBoolean() ? row.left() : row.right(), up, 0)).count(3)
                            .offset(0.08, 0.08, 0.08).speed(0.05), unmodded);
                }
            }
            // A mote of the far side drifts out of one face or the other and fades.
            double side = rnd.nextBoolean() ? 1 : -1;
            double v = 0.2 + 0.6 * rnd.nextDouble();
            double[] s = Rift.shape(v, RiftTextures.SEED);
            double across = (s[0] + s[1]) / 2 * w / 2 + (rnd.nextDouble() - 0.5) * (s[1] - s[0]) * w / 2 * 0.6 * o[1];
            Location from = point(across, bottom + v * h, 0.1 * side);
            Location toward = point(across * 1.4, bottom + v * h + 0.2, 1.2 * side);
            send(new ParticleBuilder(Particle.END_ROD).location(from).count(0)
                    .offset(toward.getX() - from.getX(), toward.getY() - from.getY(), toward.getZ() - from.getZ())
                    .speed(0.05), unmodded);
        }
    }

    private static void dust(Location at, Particle.DustOptions d, List<Player> to) {
        new ParticleBuilder(Particle.DUST).location(at).count(1).offset(0, 0, 0).speed(0).data(d).force(true).receivers(to)
                .spawn();
    }

    private static void send(ParticleBuilder b, List<Player> to) {
        b.force(true).receivers(to).spawn();
    }

    /** The bursts and sounds on the timeline, for everyone without the mod (the mod's scene has its own). */
    private void cues() {
        int t = tick;
        if (t != 1 && t != when.crack() && t != when.crack() + 2 && t != when.climax() && t != when.seal() && t != when.shut()) {
            return;
        }
        List<Player> to = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            if (near(p) && !modded.contains(p.getUniqueId())) to.add(p);
        }
        if (to.isEmpty()) return;
        Location heart = point(0, Rift.centre(body), 0);
        if (t == 1) play(to, heart, "entity.warden.sonic_charge", arriving ? 0.9f : 0.6f, arriving ? 0.7f : 0.9f);
        if (t == when.crack()) play(to, heart, "block.glass.break", 1.0f, 0.5f);
        if (t == when.crack() + 2) play(to, heart, "block.portal.trigger", 0.5f, 1.4f);
        if (t == when.climax()) {
            if (arriving) {
                play(to, heart, "entity.lightning_bolt.impact", 1.0f, 0.7f);
                play(to, heart, "block.end_portal.spawn", 0.7f, 0.8f);
                send(new ParticleBuilder(Particle.FLASH).location(heart).count(1).data(Color.fromRGB(palette.rim)), to);
                double speed = Math.min(0.4, 0.12 + body * 0.02);
                send(new ParticleBuilder(Particle.END_ROD).location(heart).count(40).offset(0, 0, 0).speed(speed), to);
                ring(to, Math.max(2.5, body), palette.glow);
            } else {
                play(to, heart, "entity.enderman.teleport", 1.0f, 0.5f);
                play(to, heart, "block.beacon.deactivate", 1.0f, 0.7f);
                send(new ParticleBuilder(Particle.REVERSE_PORTAL).location(heart).count(80)
                        .offset(Rift.width(body) * 0.4, Rift.height(body) * 0.3, Rift.width(body) * 0.4).speed(0.08), to);
            }
        }
        if (t == when.seal()) play(to, heart, "block.respawn_anchor.deplete", 0.8f, 1.0f);
        if (t == when.shut()) {
            play(to, heart, "block.beacon.deactivate", 1.0f, 1.3f);
            play(to, heart, "block.amethyst_block.chime", 1.0f, 0.8f);
            send(new ParticleBuilder(Particle.END_ROD).location(heart).count(16).offset(0, 0, 0).speed(0.08), to);
            ring(to, Math.max(1.5, body * 0.6), palette.rim);
        }
    }

    private void ring(List<Player> to, double radius, int rgb) {
        Particle.DustOptions d = new Particle.DustOptions(Color.fromRGB(rgb), 1.6f);
        int n = (int) Math.min(80, 12 + radius * 8);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            dust(new Location(world, base.getX() + Math.cos(a) * radius, base.getY() + 0.15, base.getZ() + Math.sin(a) * radius),
                    d, to);
        }
    }

    private static void play(List<Player> to, Location at, String sound, float volume, float pitch) {
        for (Player p : to) p.playSound(at, sound, volume, pitch);
    }

    // ---- Ending --------------------------------------------------------------------------------------

    /** Closes it at once without its climax (the god vanished or went elsewhere before it formed). */
    public void stop() {
        if (done) return;
        if (painted != null && painted.isValid()) {
            Location heart = point(0, Rift.centre(body), 0);
            world.spawnParticle(Particle.REVERSE_PORTAL, heart, 40, Rift.width(body) * 0.3, Rift.height(body) * 0.3,
                    Rift.width(body) * 0.3, 0.05);
        }
        for (UUID id : modded) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) ModLink.send(p, ModSessions.FX, FxScenes.stop(sceneId));
        }
        finish();
    }

    private void finish() {
        done = true;
        if (task != null) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
                // never scheduled
            }
            task = null;
        }
        if (painted != null) {
            painted.remove();
            painted = null;
        }
    }
}
