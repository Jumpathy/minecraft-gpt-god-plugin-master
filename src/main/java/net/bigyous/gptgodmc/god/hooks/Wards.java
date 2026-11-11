package net.bigyous.gptgodmc.god.hooks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * Wards: land a god sets apart for a while, as WorldGuard regions (only on servers with WorldGuard). A ward
 * is a cube around a place with a few effects from a fixed list, an optional greeting, and an end: it is
 * removed when its time is up, also across restarts (plugins/gptgodmc/wards.json, swept every 30 s). Region
 * ids start with {@link #PREFIX}, so the god never touches a region it did not make, and its own wards do not
 * count as someone's claim in {@link Lands}. The rules ({@link #check}, {@link #id}) are pure.
 */
public final class Wards {
    private Wards() {
    }

    public static final String PREFIX = "god_";

    /** What a ward can do. Every one maps to WorldGuard flags in {@code WorldGuardBridge}. */
    public static final List<String> EFFECTS = List.of("no_pvp", "no_mobs", "no_build", "no_entry", "no_explosions", "no_fall",
            "no_hunger", "healing");

    public static final class Refusal extends Exception {
        private static final long serialVersionUID = 1L;

        public Refusal(String message) {
            super(message);
        }
    }

    public static final class Ward {
        public String id;
        public String realm;
        public String name;
        public String world;
        public int x, y, z, radius;
        public List<String> effects;
        public long until;

        public String describe(long now) {
            return String.format(Locale.ROOT, "%s at %d %d %d, radius %d (%s), %d min left", name, x, y, z, radius,
                    String.join(", ", effects), until <= now ? 0 : Math.max(1, Math.round((until - now) / 60_000.0)));
        }
    }

    /** The part that names WorldGuard classes. */
    public interface Bridge {
        void raise(World world, Ward ward, String greeting) throws Refusal;

        boolean lift(World world, String id);
    }

    // ---- Rules (pure) ----------------------------------------------------------------------------------

    /** "god_main_holy_grove": only letters WorldGuard takes. */
    public static String id(String realm, String name) {
        return PREFIX + slug(realm) + "_" + slug(name);
    }

    private static String slug(String s) {
        String k = s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return k.length() > 24 ? k.substring(0, 24) : k;
    }

    /** Validates what the god asked for and gives the effects in their proper form. */
    public static List<String> check(String name, List<String> effects, String greeting, int radius, int minutes, int maxRadius,
            int maxMinutes) throws Refusal {
        if (slug(name).isEmpty()) throw new Refusal("Give the ward a name of letters and numbers.");
        if (radius < 1 || radius > maxRadius) throw new Refusal("A ward's radius is 1 to " + maxRadius + " blocks.");
        if (minutes < 1 || minutes > maxMinutes) throw new Refusal("A ward lasts 1 to " + maxMinutes + " minutes.");
        List<String> out = new ArrayList<>();
        for (String e : effects == null ? List.<String>of() : effects) {
            String k = e.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
            if (!EFFECTS.contains(k)) throw new Refusal("A ward cannot do '" + e + "'. It can: " + String.join(", ", EFFECTS) + ".");
            if (!out.contains(k)) out.add(k);
        }
        if (out.isEmpty() && (greeting == null || greeting.isBlank())) {
            throw new Refusal("A ward needs at least one effect (" + String.join(", ", EFFECTS) + ") or a greeting.");
        }
        return out;
    }

    static List<Ward> expired(List<Ward> all, long now) {
        List<Ward> out = new ArrayList<>();
        for (Ward w : all) if (w.until <= now) out.add(w);
        return out;
    }

    // ---- Settings --------------------------------------------------------------------------------------

    public static boolean enabled() {
        return Hooks.on("worldguard.enabled");
    }

    public static boolean installed() {
        return Hooks.has("WorldGuard");
    }

    public static int maxWards() {
        return Hooks.number("worldguard.max-wards", 6, 0, 32);
    }

    public static int maxRadius() {
        return Hooks.number("worldguard.max-radius", 24, 1, 64);
    }

    public static int maxMinutes() {
        return Hooks.number("worldguard.max-minutes", 120, 1, 1440);
    }

    // ---- The wards that stand (server thread) ----------------------------------------------------------

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static List<Ward> standing;
    private static Bridge bridge;
    private static boolean failed;

    private static synchronized Bridge bridge() {
        if (bridge != null || failed || !installed()) return bridge;
        try {
            bridge = (Bridge) Class.forName("net.bigyous.gptgodmc.god.hooks.WorldGuardBridge").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            failed = true;
            GPTGOD.LOGGER.warn("WorldGuard is installed but the god cannot raise wards: " + e);
        }
        return bridge;
    }

    private static Path file() {
        return GPTGOD.get() == null ? null : GPTGOD.get().getDataFolder().toPath().resolve("wards.json");
    }

    private static synchronized List<Ward> all() {
        if (standing != null) return standing;
        standing = new ArrayList<>();
        Path f = file();
        if (f != null && Files.exists(f)) {
            try {
                List<Ward> read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), new TypeToken<List<Ward>>() {
                }.getType());
                if (read != null) for (Ward w : read) if (w != null && w.id != null && w.id.startsWith(PREFIX)) standing.add(w);
            } catch (IOException | RuntimeException e) {
                // unreadable: start empty
            }
        }
        return standing;
    }

    private static synchronized void save() {
        Path f = file();
        if (f == null) return;
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(all()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // kept in memory; written at the next change
        }
    }

    /** This god's wards. */
    public static synchronized List<Ward> of(String realm) {
        List<Ward> out = new ArrayList<>();
        for (Ward w : all()) if (w.realm.equals(realm)) out.add(w);
        return out;
    }

    /** Raises a ward (replacing this god's ward of the same name). */
    public static synchronized Ward raise(String realm, String name, Location centre, int radius, List<String> effects,
            String greeting, int minutes) throws Refusal {
        if (!enabled()) throw new Refusal("The keepers of this server have turned wards off.");
        Bridge b = bridge();
        if (b == null) throw new Refusal("This server has no WorldGuard plugin, so you cannot raise wards here.");
        List<String> checked = check(name, effects, greeting, radius, minutes, maxRadius(), maxMinutes());
        String id = id(realm, name);
        boolean replacing = all().stream().anyMatch(w -> w.id.equals(id));
        if (!replacing && of(realm).size() >= maxWards()) {
            throw new Refusal("You already keep " + maxWards() + " wards; lift one first.");
        }
        // Never over land that is someone's: the whole square is asked about, every few blocks.
        int step = Math.max(1, radius / 4);
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                Lands.Claim claim = Lands.at(centre.clone().add(dx, 0, dz));
                if (claim != null) throw new Refusal("That would lie over " + claim.describe() + ". Raise it on free land.");
            }
        }
        Ward w = new Ward();
        w.id = id;
        w.realm = realm;
        w.name = slug(name);
        w.world = centre.getWorld().getName();
        w.x = centre.getBlockX();
        w.y = centre.getBlockY();
        w.z = centre.getBlockZ();
        w.radius = radius;
        w.effects = checked;
        w.until = System.currentTimeMillis() + minutes * 60_000L;
        b.raise(centre.getWorld(), w, greeting == null || greeting.isBlank() ? null : greeting.trim());
        all().removeIf(x -> x.id.equals(id));
        all().add(w);
        save();
        return w;
    }

    /** Lifts this god's ward of that name; false when it has none. */
    public static synchronized boolean lift(String realm, String name) {
        String id = id(realm, name);
        Ward found = null;
        for (Ward w : all()) if (w.id.equals(id)) found = w;
        if (found == null) return false;
        remove(found);
        save();
        return true;
    }

    private static void remove(Ward w) {
        Bridge b = bridge();
        World world = Bukkit.getWorld(w.world);
        if (b != null && world != null) {
            try {
                b.lift(world, w.id);
            } catch (RuntimeException | LinkageError e) {
                GPTGOD.LOGGER.warn("Could not lift the ward " + w.id + ": " + e);
            }
        }
        all().remove(w);
    }

    /** Lifts the wards whose time is up. */
    public static synchronized void sweep() {
        if (!installed()) return;
        List<Ward> gone = expired(all(), System.currentTimeMillis());
        if (gone.isEmpty()) return;
        for (Ward w : gone) remove(w);
        save();
    }
}
