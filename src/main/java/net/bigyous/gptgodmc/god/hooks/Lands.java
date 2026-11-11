package net.bigyous.gptgodmc.god.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * Whose land a place is, according to the protection plugins a server has: a WorldGuard region, a
 * GriefPrevention claim, a Towny town. With none installed every place is free and this costs nothing.
 * GriefPrevention and Towny are read by reflection (two calls each, checked against their published jars);
 * WorldGuard through {@code WorldGuardBridge}, loaded by name.
 */
public final class Lands {
    private Lands() {
    }

    /**
     * @param plugin who says so: "WorldGuard", "GriefPrevention" or "Towny"
     * @param name   the region's or town's name, or null (a claim has none)
     * @param owner  who owns it, or null when unknown
     */
    public record Claim(String plugin, String name, String owner) {
        /** "Steve's claim (GriefPrevention)", "the town of Eldham, mayor Alex (Towny)", "the region spawn (WorldGuard)". */
        public String describe() {
            return switch (plugin) {
                case "GriefPrevention" -> (owner == null ? "a claim" : owner + "'s claim") + " (GriefPrevention)";
                case "Towny" -> "the town of " + name + (owner == null ? "" : ", mayor " + owner) + " (Towny)";
                default -> "the region " + name + (owner == null ? "" : ", owned by " + owner) + " (" + plugin + ")";
            };
        }
    }

    /** One plugin's answer. */
    public interface Reader {
        String plugin();

        /** The claim at this place, or null. Server thread. */
        Claim at(Location l) throws Exception;
    }

    private static List<Reader> readers;

    /** For tests: the readers to ask instead of the server's plugins (null = look again). */
    static synchronized void use(List<Reader> fake) {
        readers = fake;
    }

    private static synchronized List<Reader> readers() {
        if (readers != null) return readers;
        List<Reader> found = new ArrayList<>();
        if (Hooks.on("worldguard.enabled") && Hooks.has("WorldGuard")) {
            add(found, "WorldGuard", () -> (Reader) Class.forName("net.bigyous.gptgodmc.god.hooks.WorldGuardBridge")
                    .getDeclaredConstructor().newInstance());
        }
        if (Hooks.on("griefprevention") && Hooks.has("GriefPrevention")) add(found, "GriefPrevention", GriefPreventionReader::new);
        if (Hooks.on("towny") && Hooks.has("Towny")) add(found, "Towny", TownyReader::new);
        // Only remembered once the server is up; before that, plugins may still be enabling.
        if (GPTGOD.get() != null && GPTGOD.get().isEnabled()) readers = found;
        return found;
    }

    private interface Maker {
        Reader make() throws Exception;
    }

    private static void add(List<Reader> to, String name, Maker maker) {
        try {
            to.add(maker.make());
        } catch (Exception | LinkageError e) {
            GPTGOD.LOGGER.warn(name + " is installed but the god cannot read its land: " + e);
        }
    }

    /** The protection plugins being read. */
    public static List<String> names() {
        List<String> out = new ArrayList<>();
        for (Reader r : readers()) out.add(r.plugin());
        return out;
    }

    /** hooks.respect-claims: the god's scripts leave claimed land alone. */
    public static boolean respect() {
        return Hooks.on("respect-claims");
    }

    /** The claim this place lies in, or null. Never throws: a plugin that fails is skipped. Server thread. */
    public static Claim at(Location l) {
        for (Reader r : readers()) {
            try {
                Claim c = r.at(l);
                if (c != null) return c;
            } catch (Exception | LinkageError e) {
                complain(r.plugin(), e);
            }
        }
        return null;
    }

    private static long complained;

    private static void complain(String plugin, Throwable e) {
        long now = System.currentTimeMillis();
        if (now - complained < 60_000 || GPTGOD.get() == null) return;
        complained = now;
        GPTGOD.LOGGER.warn("Could not ask " + plugin + " whose land this is: " + e);
    }

    // ---- GriefPrevention -------------------------------------------------------------------------------

    private static final class GriefPreventionReader implements Reader {
        private final Field instance, dataStore;
        private final Method getClaimAt, ownerName, admin;

        GriefPreventionReader() throws ReflectiveOperationException {
            Plugin p = Hooks.plugin("GriefPrevention");
            ClassLoader cl = p.getClass().getClassLoader();
            Class<?> main = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention", true, cl);
            Class<?> claim = Class.forName("me.ryanhamshire.GriefPrevention.Claim", true, cl);
            instance = main.getField("instance");
            dataStore = main.getField("dataStore");
            getClaimAt = dataStore.getType().getMethod("getClaimAt", Location.class, boolean.class, claim);
            ownerName = claim.getMethod("getOwnerName");
            admin = claim.getMethod("isAdminClaim");
        }

        @Override
        public String plugin() {
            return "GriefPrevention";
        }

        @Override
        public Claim at(Location l) throws Exception {
            Object store = dataStore.get(instance.get(null));
            Object claim = store == null ? null : getClaimAt.invoke(store, l, true, null);
            if (claim == null) return null;
            boolean isAdmin = Boolean.TRUE.equals(admin.invoke(claim));
            Object owner = isAdmin ? "the server" : ownerName.invoke(claim);
            return new Claim("GriefPrevention", null, owner == null || owner.toString().isBlank() ? null : owner.toString());
        }
    }

    // ---- Towny -----------------------------------------------------------------------------------------

    private static final class TownyReader implements Reader {
        private final Method getInstance, isWilderness, getTownName, getTown;

        TownyReader() throws ReflectiveOperationException {
            ClassLoader cl = Hooks.plugin("Towny").getClass().getClassLoader();
            Class<?> api = Class.forName("com.palmergames.bukkit.towny.TownyAPI", true, cl);
            getInstance = api.getMethod("getInstance");
            isWilderness = api.getMethod("isWilderness", Location.class);
            getTownName = api.getMethod("getTownName", Location.class);
            getTown = api.getMethod("getTown", Location.class);
        }

        @Override
        public String plugin() {
            return "Towny";
        }

        @Override
        public Claim at(Location l) throws Exception {
            Object api = getInstance.invoke(null);
            if (api == null || Boolean.TRUE.equals(isWilderness.invoke(api, l))) return null;
            Object name = getTownName.invoke(api, l);
            if (name == null) return null;
            String mayor = null;
            try {
                Object town = getTown.invoke(api, l);
                Object m = town == null ? null : town.getClass().getMethod("getMayor").invoke(town);
                if (m != null) mayor = String.valueOf(m.getClass().getMethod("getName").invoke(m));
            } catch (ReflectiveOperationException | RuntimeException e) {
                // the town's name is enough
            }
            return new Claim("Towny", name.toString(), mayor);
        }
    }
}
