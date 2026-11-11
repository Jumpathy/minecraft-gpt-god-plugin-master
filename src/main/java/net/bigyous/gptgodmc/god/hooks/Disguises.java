package net.bigyous.gptgodmc.god.hooks;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * Changing a creature's shape through LibsDisguises (only on servers with that plugin): everyone sees the
 * mortal as a pig, a bat, a zombie. Reached by reflection (four calls, checked against its published jar),
 * so nothing here needs the plugin to compile or to run.
 */
public final class Disguises {
    private Disguises() {
    }

    private static Method getType, isMob, disguiseToAll, undisguiseToAll, isDisguised;
    private static Constructor<?> mobDisguise;
    private static boolean loaded, failed;

    public static boolean installed() {
        return Hooks.has("LibsDisguises");
    }

    public static boolean enabled() {
        return Hooks.on("libsdisguises.enabled");
    }

    public static int maxSeconds() {
        return Hooks.number("libsdisguises.max-seconds", 600, 5, 3600);
    }

    private static synchronized boolean ready() {
        if (loaded) return true;
        if (failed || !installed()) return false;
        try {
            ClassLoader cl = Hooks.plugin("LibsDisguises").getClass().getClassLoader();
            Class<?> api = Class.forName("me.libraryaddict.disguise.DisguiseAPI", true, cl);
            Class<?> type = Class.forName("me.libraryaddict.disguise.disguisetypes.DisguiseType", true, cl);
            Class<?> disguise = Class.forName("me.libraryaddict.disguise.disguisetypes.Disguise", true, cl);
            Class<?> mob = Class.forName("me.libraryaddict.disguise.disguisetypes.MobDisguise", true, cl);
            getType = type.getMethod("getType", EntityType.class);
            isMob = type.getMethod("isMob");
            mobDisguise = mob.getConstructor(type);
            disguiseToAll = api.getMethod("disguiseToAll", Entity.class, disguise);
            undisguiseToAll = api.getMethod("undisguiseToAll", Entity.class);
            isDisguised = api.getMethod("isDisguised", Entity.class);
            loaded = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            failed = true;
            GPTGOD.LOGGER.warn("LibsDisguises is installed but the god cannot use it: " + e);
        }
        return loaded;
    }

    /** Whether the hook can be used right now. */
    public static boolean usable() {
        return enabled() && ready();
    }

    /**
     * Shows this creature to everyone as a mob of that kind. Server thread.
     *
     * @return false when LibsDisguises cannot make that shape (not a living creature, or unknown to it)
     */
    public static boolean disguise(Entity entity, EntityType as) {
        if (!usable()) return false;
        try {
            Object type = getType.invoke(null, as);
            if (type == null || !Boolean.TRUE.equals(isMob.invoke(type))) return false;
            disguiseToAll.invoke(null, entity, mobDisguise.newInstance(type));
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not disguise " + entity.getName() + " as " + as + ": " + e);
            return false;
        }
    }

    /** Gives the creature its own shape back. Returns whether it was disguised. */
    public static boolean undisguise(Entity entity) {
        if (!usable()) return false;
        try {
            if (!Boolean.TRUE.equals(isDisguised.invoke(null, entity))) return false;
            undisguiseToAll.invoke(null, entity);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
