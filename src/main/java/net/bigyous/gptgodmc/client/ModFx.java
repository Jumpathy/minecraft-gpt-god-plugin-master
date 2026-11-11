package net.bigyous.gptgodmc.client;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.client.ModMessages.Screen;

/**
 * One-line hooks the god's effects call so mod players also get screen effects. Each does nothing
 * when no one has the mod. Server thread only.
 */
public final class ModFx {
    private ModFx() {
    }

    private static double[] at(Location l) {
        return new double[] { l.getX(), l.getY(), l.getZ() };
    }

    /** A screen effect for every mod player within radius of where it happens (fading with distance). */
    public static void near(Location where, double radius, Screen kind, double intensity, int ticks, int color) {
        if (where == null || ModLink.sessions().players().isEmpty()) return;
        ModLink.sendNear(where, radius, ModSessions.FX,
                ModMessages.screen(kind, intensity, ticks, color, at(where), radius));
    }

    /** A screen effect right on one player (no falloff). */
    public static void on(Player p, Screen kind, double intensity, int ticks, int color) {
        ModLink.send(p, ModSessions.FX, ModMessages.screen(kind, intensity, ticks, color, null, 0));
    }

    /** The true form's gaze struck this mortal down. */
    public static void smitten(Player p) {
        on(p, Screen.FLASH, 0.9, 10, 0xFFF4D6);
        on(p, Screen.SHAKE, 0.8, 16, 0xFFFFFF);
        on(p, Screen.VIGNETTE, 0.8, 60, 0x8A0010);
        only(p, "gptgodmc:god.smite", 1.5f);
    }

    /** The god's true form unveils itself here. */
    public static void trueForm(Location where) {
        near(where, 96, Screen.PULSE, 0.8, 40, 0xFFE9A8);
        near(where, 64, Screen.SHAKE, 0.4, 30, 0xFFFFFF);
        sound(where, "gptgodmc:god.trueform", 3f, 1f, 128);
    }

    public static void meteorImpact(Location where) {
        near(where, 80, Screen.SHAKE, 1.0, 24, 0xFFFFFF);
        near(where, 48, Screen.FLASH, 0.6, 8, 0xFFB050);
        sound(where, "gptgodmc:god.meteor", 3f, 1f, 128);
    }

    public static void earthquake(Location where, int radius, int seconds) {
        near(where, radius + 24, Screen.SHAKE, 0.55, seconds * 20, 0xFFFFFF);
        sound(where, "gptgodmc:god.quake", 2.5f, 1f, radius + 48);
    }

    /** A god's death (Godslayer): the climax of the death show. */
    public static void godSlain(Location where) {
        godSlain(where, "supernova");
    }

    /** @param manner the death's style (supernova, unraveling, shattering, abyssal): each has its own blast */
    public static void godSlain(Location where, String manner) {
        sound(where, "gptgodmc:god.death." + manner, 4f, 1f, 192);
        near(where, 160, Screen.FLASH, 1.0, 30, 0xFFFFFF);
        near(where, 160, Screen.SHAKE, 1.0, 50, 0xFFFFFF);
        near(where, 160, Screen.VIGNETTE, 0.6, 160, 0x1A0F24);
    }

    /** A Godslayer blow lands on the god's body. */
    public static void godWounded(Location where) {
        near(where, 48, Screen.SHAKE, 0.35, 8, 0xFFFFFF);
        near(where, 24, Screen.FLASH, 0.3, 4, 0xFF3040);
        sound(where, "gptgodmc:god.wound", 1.5f, 1f, 64);
    }

    /** Lightning, explosions and other wrath at a spot. */
    public static void wrath(Location where, double power) {
        near(where, 40 + 16 * power, Screen.SHAKE, Math.min(1, 0.3 + 0.2 * power), 12, 0xFFFFFF);
        near(where, 24 + 8 * power, Screen.FLASH, Math.min(1, 0.2 + 0.15 * power), 5, 0xE8F0FF);
    }

    /**
     * Plays one of the mod's own sounds for the mod players within radius of where, and returns them, so
     * the caller can play its vanilla sound to everyone else only. Empty when no one near has the mod.
     */
    public static java.util.Set<Player> sound(Location where, String id, float volume, float pitch, double radius) {
        java.util.Set<Player> heard = new java.util.HashSet<>();
        if (where == null || where.getWorld() == null || ModLink.sessions().players().isEmpty()) return heard;
        var message = ModMessages.sound(id, at(where), volume, pitch);
        double r2 = radius * radius;
        for (java.util.UUID uuid : ModLink.sessions().players()) {
            Player p = org.bukkit.Bukkit.getPlayer(uuid);
            if (p == null || !p.getWorld().equals(where.getWorld()) || p.getLocation().distanceSquared(where) > r2) continue;
            if (ModLink.send(p, ModSessions.FX, message)) heard.add(p);
        }
        return heard;
    }

    /** The god's body is taking form (out of its rift or its arrival storm); the sound's landing falls on the climax. */
    public static void arrival(Location where) {
        near(where, 64, Screen.PULSE, 0.5, 30, 0xD9A6FF);
        sound(where, "gptgodmc:god.arrival", 2.5f, 1f, 112);
    }

    /** The god's body leaves the world. */
    public static void departure(Location where) {
        sound(where, "gptgodmc:god.departure", 2f, 1f, 96);
    }

    /** A god begins to die: the long fall before the burst. */
    public static void godFalling(Location where) {
        sound(where, "gptgodmc:god.death.fall", 3.5f, 1f, 160);
    }

    /** One of the mod's sounds for one player, at their own position. */
    public static void only(Player p, String id, float volume) {
        if (p == null || !ModLink.has(p, ModSessions.FX)) return;
        Location l = p.getLocation();
        ModLink.send(p, ModSessions.FX, ModMessages.sound(id, new double[] { l.getX(), l.getY() + 1, l.getZ() }, volume, 1));
    }
}
