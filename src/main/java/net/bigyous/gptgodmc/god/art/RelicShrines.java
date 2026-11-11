package net.bigyous.gptgodmc.god.art;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.client.ModRelics;
import net.bigyous.gptgodmc.god.art.Relics.Relic;
import net.bigyous.gptgodmc.god.vessel.VesselPackHost;

/**
 * Relics and spirits set in the world: an item display of the relic that floats, bobs and slowly turns
 * (a spirit instead turns to face whoever looks at it), with a few motes about it. One enshrined at a
 * place stays there across restarts (the place is kept in relics/shrines.json; the entity itself is
 * never saved); one set to follow a mortal floats at their shoulder for a while. Players who have
 * neither the resource pack nor the client mod could only be shown a missing model, so they are not
 * shown the display at all. Server thread only.
 */
public final class RelicShrines {
    private static final String TAG = "relic_shrine";
    private static final List<Shrine> ALL = new CopyOnWriteArrayList<>();
    private static Path file;
    private static long tick;

    private RelicShrines() {
    }

    /** One relic in the world. */
    public static final class Shrine {
        final String relic;
        final String world;
        double x, y, z;
        /** Whom it follows, or null when it stays at its place. */
        final UUID follows;
        /** When it leaves (epoch ms), or 0 for never. */
        final long until;
        ItemDisplay display;
        int turn;

        Shrine(String relic, String world, double x, double y, double z, UUID follows, long until) {
            this.relic = relic;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.follows = follows;
            this.until = until;
        }
    }

    private static NamespacedKey key() {
        return new NamespacedKey(GPTGOD.get(), TAG);
    }

    // ---- Keeping the places -----------------------------------------------------------------------------

    public static void load(Path shrines) {
        file = shrines;
        ALL.clear();
        // Displays left by a crash or a reload are swept; the kept places are set up again below.
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntitiesByClass(ItemDisplay.class)) {
                if (e.getPersistentDataContainer().has(key(), PersistentDataType.STRING)) e.remove();
            }
        }
        if (shrines == null || !Files.isRegularFile(shrines)) return;
        try {
            for (JsonElement e : JsonParser.parseString(Files.readString(shrines, StandardCharsets.UTF_8)).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                ALL.add(new Shrine(o.get("relic").getAsString(), o.get("world").getAsString(), o.get("x").getAsDouble(),
                        o.get("y").getAsDouble(), o.get("z").getAsDouble(), null, 0));
            }
        } catch (IOException | RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not read the relics' places: " + e.getMessage());
        }
    }

    private static void save() {
        if (file == null) return;
        JsonArray out = new JsonArray();
        for (Shrine s : ALL) {
            if (s.follows != null) continue;
            JsonObject o = new JsonObject();
            o.addProperty("relic", s.relic);
            o.addProperty("world", s.world);
            o.addProperty("x", s.x);
            o.addProperty("y", s.y);
            o.addProperty("z", s.z);
            out.add(o);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            GPTGOD.LOGGER.warn("Could not save the relics' places: " + e.getMessage());
        }
    }

    public static int count() {
        return ALL.size();
    }

    // ---- Setting and removing ---------------------------------------------------------------------------

    /** Sets a relic to float at a place for good. Throws IllegalStateException with the reason it cannot. */
    public static void enshrine(Relic r, Location at) {
        room();
        ALL.add(new Shrine(r.id(), at.getWorld().getName(), at.getX(), at.getY() + 1.3, at.getZ(), null, 0));
        save();
    }

    /** Sets a relic to float at a mortal's shoulder for some minutes. */
    public static void follow(Relic r, Player p, int minutes) {
        room();
        Location l = p.getLocation();
        ALL.add(new Shrine(r.id(), l.getWorld().getName(), l.getX(), l.getY() + 2, l.getZ(), p.getUniqueId(),
                System.currentTimeMillis() + minutes * 60_000L));
    }

    private static void room() {
        if (ALL.size() >= ArtSettings.worldMax()) {
            throw new IllegalStateException("The world already holds " + ALL.size() + " relics and spirits, as many as "
                    + "the keeper allows. Dismiss one first.");
        }
    }

    /** Takes every showing of a relic out of the world; returns how many there were. */
    public static int dismiss(String relicId) {
        int n = 0;
        for (Shrine s : ALL) {
            if (!s.relic.equals(relicId)) continue;
            remove(s);
            n++;
        }
        if (n > 0) save();
        return n;
    }

    private static void remove(Shrine s) {
        if (s.display != null) {
            Location at = s.display.getLocation();
            at.getWorld().spawnParticle(Particle.END_ROD, at, 12, 0.2, 0.3, 0.2, 0.03);
            s.display.remove();
        }
        ALL.remove(s);
    }

    public static void shutdown() {
        for (Shrine s : ALL) {
            if (s.display != null) s.display.remove();
            s.display = null;
        }
    }

    // ---- The clock --------------------------------------------------------------------------------------

    /** Every 2 ticks. */
    public static void tick() {
        tick += 2;
        long now = System.currentTimeMillis();
        for (Shrine s : ALL) {
            Relic r = Relics.find(s.relic);
            Player lead = s.follows == null ? null : Bukkit.getPlayer(s.follows);
            if (r == null || (s.until > 0 && now > s.until) || (s.follows != null && lead == null)) {
                remove(s);
                if (s.follows == null) save();
                continue;
            }
            if (lead != null) {
                // At the right shoulder, a little behind.
                Location l = lead.getLocation();
                Vector back = l.getDirection().setY(0);
                if (back.lengthSquared() < 1e-4) back = new Vector(0, 0, 1);
                back.normalize();
                Vector right = new Vector(-back.getZ(), 0, back.getX());
                s.x = l.getX() + right.getX() * 0.7 - back.getX() * 0.4;
                s.y = l.getY() + 2.0;
                s.z = l.getZ() + right.getZ() * 0.7 - back.getZ() * 0.4;
            }
            World w = lead != null ? lead.getWorld() : Bukkit.getWorld(s.world);
            if (w == null) continue;
            Location at = new Location(w, s.x, s.y, s.z);
            if (s.display != null && (!s.display.isValid() || !s.display.getWorld().equals(w))) {
                s.display.remove();
                s.display = null;
            }
            if (s.display == null) {
                if (!at.isChunkLoaded() || tick % 20 != 0) continue;
                s.display = spawn(r, at);
                continue;
            }
            if (lead != null) s.display.teleport(at);
            if (tick % 20 == 0) {
                pose(s, r);
                viewers(s);
                w.spawnParticle("spirit".equals(r.kind()) ? Particle.END_ROD : Particle.ENCHANT, at, 2, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    private static float size(Relic r) {
        return "spirit".equals(r.kind()) ? 1.5f : 0.9f;
    }

    private static ItemDisplay spawn(Relic r, Location at) {
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.getPersistentDataContainer().set(key(), PersistentDataType.STRING, r.id());
            d.setItemStack(Relics.item(r));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setShadowRadius(0);
            d.setTeleportDuration(4);
            // A spirit turns to face whoever looks at it; a relic turns slowly on its own.
            if ("spirit".equals(r.kind())) d.setBillboard(Display.Billboard.VERTICAL);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(size(r)),
                    new Quaternionf()));
        });
    }

    /** Once a second: the next quarter turn and the next rise or fall, smoothed over the second. */
    private static void pose(Shrine s, Relic r) {
        s.turn++;
        float bob = (s.turn & 1) == 0 ? 0.08f : -0.08f;
        Quaternionf spin = "spirit".equals(r.kind()) ? new Quaternionf()
                : new Quaternionf().rotationY((float) (s.turn * Math.PI / 2));
        s.display.setInterpolationDelay(0);
        s.display.setInterpolationDuration(20);
        s.display.setTransformation(new Transformation(new Vector3f(0, bob, 0), spin, new Vector3f(size(r)),
                new Quaternionf()));
    }

    /** Hides it from players who could only see a missing model, and shows it to those who now can see it. */
    private static void viewers(Shrine s) {
        for (Player p : s.display.getWorld().getPlayers()) {
            boolean sees = VesselPackHost.has(p) || ModRelics.draws(p);
            if (sees) p.showEntity(GPTGOD.get(), s.display);
            else p.hideEntity(GPTGOD.get(), s.display);
        }
    }

    /** What is set where, for the operator's list and the god's tools. */
    public static List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Shrine s : ALL) {
            Player lead = s.follows == null ? null : Bukkit.getPlayer(s.follows);
            out.add(s.relic + (lead != null ? " follows " + lead.getName()
                    : String.format(" at (%d, %d, %d) in %s", (int) s.x, (int) s.y, (int) s.z, s.world)));
        }
        return out;
    }
}
