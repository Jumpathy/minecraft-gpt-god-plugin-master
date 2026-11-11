package net.bigyous.gptgodmc.god;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.util.Vector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.bigyous.gptgodmc.EventLogger;

/**
 * Apotheosis, the god's rarest gift: a mortal raised to demigod, with a hero's strength, flight,
 * wings of light and a halo. Who is a demigod (and when the last was raised) is kept in
 * demigods.json, shared by every god on the server. The rules and the wing geometry are pure, so
 * they are unit tested; {@link DemigodPowers} applies them in the world.
 */
public final class Demigods {
    /** One demigod. */
    public static final class Entry {
        public String name;
        public String uuid;
        public long since;
        /** 0 = until unmade. */
        public long until;
        /** The god who raised them. */
        public String by;
        public String reason;

        public boolean lasts(long now) {
            return until == 0 || now < until;
        }
    }

    public List<Entry> demigods = new ArrayList<>();
    public long lastRaisedAt;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ---- The rules ---------------------------------------------------------------------------------

    /**
     * Why a mortal may not be raised right now, or null if they may.
     *
     * @param favor how the god regards them (-10..10)
     */
    public String refusal(boolean enabled, String name, int favor, int minFavor, int max, int cooldownHours, long now) {
        if (!enabled) {
            return "Apotheosis is forbidden on this server (apotheosis.enabled is off). Only the server keeper can "
                    + "allow it.";
        }
        if (find(name, now) != null) return name + " is already a demigod.";
        if (favor < minFavor) {
            return "Only a mortal you hold in the highest regard (favor " + minFavor + " or more) may be raised; "
                    + name + " stands at " + favor + ".";
        }
        if (living(now) >= max) {
            return "There " + (max == 1 ? "is already a demigod" : "are already " + max + " demigods")
                    + " in this world. Unmake one first, or let them be.";
        }
        long wait = lastRaisedAt + cooldownHours * 3_600_000L - now;
        if (lastRaisedAt > 0 && wait > 0) {
            return "Apotheosis is spent. It can be given again in " + EventLogger.ago(Duration.ofMillis(wait)) + ".";
        }
        return null;
    }

    public Entry raise(String name, String uuid, String by, String reason, int minutes, long now) {
        Entry e = new Entry();
        e.name = name;
        e.uuid = uuid;
        e.since = now;
        e.until = minutes <= 0 ? 0 : now + minutes * 60_000L;
        e.by = by;
        e.reason = reason == null ? "" : reason.length() > 200 ? reason.substring(0, 200) : reason;
        demigods.removeIf(d -> d.name.equalsIgnoreCase(name));
        demigods.add(e);
        lastRaisedAt = now;
        return e;
    }

    public Entry unmake(String name) {
        for (Entry d : demigods) {
            if (d.name.equalsIgnoreCase(name)) {
                demigods.remove(d);
                return d;
            }
        }
        return null;
    }

    public Entry find(String name, long now) {
        if (name == null) return null;
        for (Entry d : demigods) {
            if (d.name.equalsIgnoreCase(name) && d.lasts(now)) return d;
        }
        return null;
    }

    public int living(long now) {
        int n = 0;
        for (Entry d : demigods) if (d.lasts(now)) n++;
        return n;
    }

    /** Demigods whose time has run out (removed from the list). */
    public List<Entry> expire(long now) {
        List<Entry> gone = new ArrayList<>();
        for (Entry d : new ArrayList<>(demigods)) {
            if (!d.lasts(now)) {
                demigods.remove(d);
                gone.add(d);
            }
        }
        return gone;
    }

    /** For the god's perception: who is a demigod, and whether the gift can be given again. */
    public String describe(long now, int cooldownHours) {
        StringBuilder sb = new StringBuilder();
        for (Entry d : demigods) {
            if (!d.lasts(now)) continue;
            sb.append("- ").append(d.name).append(", raised ").append(EventLogger.ago(Duration.ofMillis(now - d.since)))
                    .append(" ago by ").append(d.by == null ? "a god" : d.by)
                    .append(d.reason == null || d.reason.isEmpty() ? "" : " (" + d.reason + ")")
                    .append(d.until == 0 ? "" : ", for " + EventLogger.ago(Duration.ofMillis(d.until - now)) + " more")
                    .append('\n');
        }
        long wait = lastRaisedAt + cooldownHours * 3_600_000L - now;
        sb.append(lastRaisedAt > 0 && wait > 0
                ? "The gift is spent for " + EventLogger.ago(Duration.ofMillis(wait)) + ".\n"
                : "It could be given, but almost never should be.\n");
        return sb.toString();
    }

    // ---- Wings of light ------------------------------------------------------------------------------

    /**
     * Points of a pair of feathered wings in body space (x right, y up, z forward; feet at 0), spread
     * by flap from -1 (folded down) to 1 (raised).
     */
    public static List<Vector> wing(double flap, int perFeather) {
        List<Vector> out = new ArrayList<>();
        for (int side = -1; side <= 1; side += 2) {
            for (int row = 0; row < 3; row++) {
                double len = 1.0 - row * 0.22;
                for (int i = 0; i <= perFeather; i++) {
                    double t = i / (double) perFeather * len;
                    double x = side * (0.25 + 1.7 * t);
                    double y = 1.35 + 0.85 * Math.sin(Math.PI * t * 0.95) * (1 - t * 0.3) - row * 0.32 * (1 - t * 0.5)
                            + flap * 0.55 * t;
                    double z = -0.32 - 0.28 * t;
                    out.add(new Vector(x, y, z));
                }
            }
        }
        return out;
    }

    /** Body-space points turned to world offsets for a body facing yaw (degrees, Minecraft style). */
    public static List<Vector> facing(List<Vector> body, float yaw) {
        double r = Math.toRadians(yaw);
        double fx = -Math.sin(r), fz = Math.cos(r); // forward
        double rx = -Math.cos(r), rz = -Math.sin(r); // right
        List<Vector> out = new ArrayList<>(body.size());
        for (Vector v : body) {
            out.add(new Vector(v.getX() * rx + v.getZ() * fx, v.getY(), v.getX() * rz + v.getZ() * fz));
        }
        return out;
    }

    // ---- Keeping it ----------------------------------------------------------------------------------

    public static Demigods load(Path file) {
        try {
            if (Files.exists(file)) {
                Demigods d = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Demigods.class);
                if (d != null) {
                    if (d.demigods == null) d.demigods = new ArrayList<>();
                    return d;
                }
            }
        } catch (IOException | RuntimeException e) {
            net.bigyous.gptgodmc.GPTGOD.LOGGER.warn("Could not read " + file + ": " + e.getMessage());
        }
        return new Demigods();
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            net.bigyous.gptgodmc.GPTGOD.LOGGER.warn("Could not save " + file + ": " + e.getMessage());
        }
    }
}
