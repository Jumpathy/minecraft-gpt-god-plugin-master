package net.bigyous.gptgodmc.god.vessel;

import java.util.Locale;

import org.bukkit.Color;
import org.bukkit.Material;

/**
 * How the vessel carries itself: posture, how high it floats, the color of its glow and eyes,
 * wings, and the particles and sounds around it. Pure data, so previews can use it.
 */
public enum VesselMood {
    CALM("at peace: floats just above the ground in its usual glow", 0, null, null, Wings.NONE),
    WRATHFUL("rises into the air with burning red eyes and a red glow, fists clenched, dark wings spread, "
            + "embers, ash and crackling sparks around it, thunder rumbling", 3.0, Color.fromRGB(0xFF2A1A),
            Material.REDSTONE_BLOCK, Wings.DEMON),
    JOYFUL("floats lightly with open arms, sparkling with golden light, music notes and hearts", 1.0,
            Color.fromRGB(0xFFD86B), null, Wings.NONE),
    SORROWFUL("sinks low with its head bowed, weeping, a pale blue glow and drifting souls", 0,
            Color.fromRGB(0x7FB2FF), null, Wings.NONE),
    OMINOUS("hovers in silence, cold glowing eyes, wreathed in black smoke and rising souls", 1.6,
            Color.fromRGB(0x5A1E8C), Material.LIGHT_BLUE_CONCRETE, Wings.NONE),
    EXALTED("ascends high with arms spread, wings of light, a beam of radiance falling to the ground", 5.0,
            Color.fromRGB(0xFFF1B0), Material.SEA_LANTERN, Wings.ANGEL);

    public enum Wings {
        NONE, ANGEL, DEMON
    }

    /** What mortals see, for the god's tool description. */
    public final String effect;
    /** Extra height it floats at, in blocks. */
    public final double rise;
    /** Outline color, or null to keep the look's. */
    public final Color outline;
    /** Eye block, or null to keep the look's. */
    public final Material eyes;
    public final Wings wings;

    VesselMood(String effect, double rise, Color outline, Material eyes, Wings wings) {
        this.effect = effect;
        this.rise = rise;
        this.outline = outline;
        this.eyes = eyes;
        this.wings = wings;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The mood with this name, or null. */
    public static VesselMood parse(String name) {
        if (name == null) return null;
        String n = name.trim().toUpperCase(Locale.ROOT);
        for (VesselMood m : values()) {
            if (m.name().equals(n)) return m;
        }
        return switch (n) {
            case "ANGRY", "WRATH", "FURIOUS", "MAD" -> WRATHFUL;
            case "HAPPY", "JOY", "PLEASED" -> JOYFUL;
            case "SAD", "SORROW", "GRIEF", "MOURNING" -> SORROWFUL;
            case "MENACING", "DARK", "THREATENING" -> OMINOUS;
            case "DIVINE", "RADIANT", "GLORIOUS", "ASCENDED" -> EXALTED;
            case "NEUTRAL", "PEACEFUL", "IDLE" -> CALM;
            default -> null;
        };
    }
}
