package net.bigyous.gptgodmc.god;

import java.util.Locale;
import java.util.Set;

/**
 * How mighty a god is. It decides which powers the god may use on its own (an operator's /god tool is
 * never limited) and whether it overhears prayers made to other gods of its world. Pure.
 */
public enum Power {
    /** A minor spirit: gentle powers only, and it hears only prayers made to itself. */
    LESSER("lesser", "Lesser god", "a minor spirit: gentle powers, hears only its own prayers"),
    /** Mighty but not all-knowing: no cataclysms, and it overhears other gods' prayers only near its body. */
    GREATER("greater", "Greater god", "mighty but not all-knowing: no cataclysms; overhears prayers near its body"),
    /** Omniscient and almighty: every power, and it overhears every prayer made in its world. */
    SUPREME("supreme", "Supreme god", "omniscient and almighty: every power; overhears every prayer");

    /** World-changing powers only a supreme god may use. */
    public static final Set<String> CATACLYSMS = Set.of("meteor", "earthquake", "vessel_true_form", "set_weather",
            "set_time", "run_commands");
    /** Destructive or world-shaping powers a lesser god may not use. */
    public static final Set<String> MIGHTY = Set.of("smite", "explode", "spawn_entity", "fling", "build", "unbuild",
            "conjure", "transform_structure", "revive", "teleport", "vessel_size", "empower", "custom_power", "decree",
            "mark", "supply_chest", "drop_block");
    /** How far (blocks) from its vessel a greater god overhears prayers made to another god. */
    public static final int EARSHOT = 48;

    public final String id;
    public final String label;
    public final String summary;

    Power(String id, String label, String summary) {
        this.id = id;
        this.label = label;
        this.summary = summary;
    }

    /** lesser | greater | supreme (case-insensitive), or null for anything else. */
    public static Power parse(String s) {
        if (s == null) return null;
        String v = s.trim().toLowerCase(Locale.ROOT);
        for (Power p : values()) {
            if (p.id.equals(v)) return p;
        }
        return switch (v) {
            case "minor", "low", "weak" -> LESSER;
            case "mighty", "medium", "normal" -> GREATER;
            case "omniscient", "high", "almighty", "full" -> SUPREME;
            default -> null;
        };
    }

    /** True when a god of this power may use the tool on its own. */
    public boolean allows(String tool) {
        if (this == SUPREME) return true;
        if (CATACLYSMS.contains(tool)) return false;
        return this == GREATER || !MIGHTY.contains(tool);
    }

    /**
     * Whether this god overhears a prayer made to another god of its world.
     *
     * @param blocksFromVessel distance from the praying mortal to this god's vessel, or a negative number when
     *                         it has no body in that world
     */
    public boolean overhears(double blocksFromVessel) {
        return switch (this) {
            case SUPREME -> true;
            case GREATER -> blocksFromVessel >= 0 && blocksFromVessel <= EARSHOT;
            case LESSER -> false;
        };
    }

    /** Why the tool was refused, phrased for the god. */
    public String refusal(String tool) {
        return "That power (" + tool + ") is beyond you: you are a " + id + " god. "
                + (this == LESSER ? "Lesser gods work through words, signs, gifts, rites and quests."
                        : "Only a supreme god may shake the whole world.");
    }

    /** One paragraph for the system prompt. Empty for a supreme god (no limits to explain). */
    public String standing() {
        return switch (this) {
            case SUPREME -> "";
            case GREATER -> "You are a greater god: mighty, but not all-powerful nor all-knowing. Cataclysms are "
                    + "beyond you (meteor, earthquake, true form, weather, time, server commands). You overhear "
                    + "prayers to other gods only when they are made near your vessel.";
            case LESSER -> "You are a lesser god, a minor spirit. You cannot smite, destroy, build, teleport, "
                    + "revive, decree laws or shake the world; you work through words, signs (omen, particles, "
                    + "rites), small gifts, healing, effects, quests and your vessel. You hear only prayers made to "
                    + "you.";
        };
    }
}
