package net.bigyous.gptgodmc.god.living;

import java.util.List;
import java.util.Locale;

import org.bukkit.Material;

import net.bigyous.gptgodmc.god.DemigodWings;

/**
 * The servants a god can summon. Each is a vanilla mob (its hitbox, its look on Bedrock and for players
 * without the pack) dressed with a few display-entity pieces: wings, a halo, horns, a cloak, a crest.
 * Pure data, so the rig and the rules are unit tested.
 */
public enum ServantKind {
    /** A winged, glowing guardian: a large allay with feathered wings and a halo. */
    ANGEL("angel", "ALLAY", 2.7, true, 40, 6, 0.42, 3.2, DemigodWings.Style.ANGEL, true, false, false,
            "end_rod", 0xFFF2C2, Material.GOLD_BLOCK, "a winged guardian of light: guards, heals the faithful, smites"),
    /** A darting imp: a vex with membrane wings, horns and embers. */
    IMP("imp", "VEX", 1.5, true, 20, 4, 0.5, 2.6, DemigodWings.Style.DEMON, false, true, false, "flame", 0xFF4A1C,
            Material.NETHERITE_BLOCK, "a small darting fiend: harries, steals courage, sets fires in its wake"),
    /** A shade: a wither skeleton in a tattered cloak of shadow, trailing soul smoke. */
    SHADE("shade", "WITHER_SKELETON", 1.0, false, 34, 7, 0.32, 2.8, null, false, false, true, "soul", 0x8E6BFF,
            Material.BLACK_CONCRETE, "a hooded shadow: stalks, frightens, withers"),
    /** A priest: a cleric villager who preaches the god's commandments to mortals nearby. */
    PRIEST("priest", "VILLAGER", 1.0, false, 30, 0, 0.22, 0, null, true, false, false, "enchant", 0xE7C6FF,
            Material.GOLD_BLOCK, "a robed priest: walks among mortals and preaches your commandments and words"),
    /** A temple guard: an iron golem wearing a golden crest. */
    GUARD("guard", "IRON_GOLEM", 1.0, false, 80, 9, 0.3, 3.2, null, false, false, false, "wax_on", 0xF6C64A,
            Material.GOLD_BLOCK, "a towering temple guard: holds a place, crushes intruders and monsters");

    public final String id;
    /** Bukkit EntityType name of the base mob. */
    public final String mob;
    /** The base mob's scale attribute. */
    public final double scale;
    public final boolean flies;
    public final double health;
    /** Damage per strike (0 = never strikes). */
    public final double damage;
    /** Blocks per tick it moves at most. */
    public final double speed;
    /** How far its strikes reach. */
    public final double reach;
    /** Its wings (null = none). */
    public final DemigodWings.Style wings;
    public final boolean halo, horns, cloak;
    /** Its ambient particle (Spectacle's names). */
    public final String particle;
    public final int glow;
    public final Material trim;
    public final String summary;

    ServantKind(String id, String mob, double scale, boolean flies, double health, double damage, double speed,
            double reach, DemigodWings.Style wings, boolean halo, boolean horns, boolean cloak, String particle, int glow,
            Material trim, String summary) {
        this.id = id;
        this.mob = mob;
        this.scale = scale;
        this.flies = flies;
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.reach = reach;
        this.wings = wings;
        this.halo = halo;
        this.horns = horns;
        this.cloak = cloak;
        this.particle = particle;
        this.glow = glow;
        this.trim = trim;
        this.summary = summary;
    }

    /** The base mob's height in blocks (vanilla height times scale). */
    public double height() {
        double h = switch (this) {
            case ANGEL -> 0.6;
            case IMP -> 0.8;
            case SHADE -> 2.4;
            case PRIEST -> 1.95;
            case GUARD -> 2.7;
        };
        return h * scale;
    }

    public static ServantKind parse(String s) {
        if (s == null) return null;
        String v = s.trim().toLowerCase(Locale.ROOT);
        for (ServantKind k : values()) {
            if (k.id.equals(v)) return k;
        }
        return switch (v) {
            case "angels", "seraph", "cherub", "guardian" -> ANGEL;
            case "imps", "demon", "fiend", "devil" -> IMP;
            case "shades", "shadow", "wraith", "ghost", "spirit" -> SHADE;
            case "priests", "cleric", "preacher", "prophet" -> PRIEST;
            case "guards", "temple_guard", "golem", "sentinel", "warden" -> GUARD;
            default -> null;
        };
    }

    public static List<String> ids() {
        return java.util.Arrays.stream(values()).map(k -> k.id).toList();
    }

    /** What a servant may be told to do. */
    public enum Order {
        FOLLOW, GUARD, PATROL, ATTACK, PREACH, GO, STAY;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Order parse(String s) {
            if (s == null) return null;
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return switch (s.trim().toLowerCase(Locale.ROOT)) {
                    case "hunt", "smite", "kill", "fight" -> ATTACK;
                    case "protect", "defend", "hold" -> GUARD;
                    case "wander", "roam" -> PATROL;
                    case "sermon", "speak" -> PREACH;
                    case "move", "walk", "fly" -> GO;
                    case "wait", "halt" -> STAY;
                    default -> null;
                };
            }
        }
    }
}
