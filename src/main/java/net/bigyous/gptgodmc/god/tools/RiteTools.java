package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Rites;

/**
 * Powers built on {@link Rites}: empowering a mortal's gear in a rite of ascension, rites purely for
 * show, and the rites that play by themselves after gifts, healing and potion effects.
 */
final class RiteTools {
    private RiteTools() {
    }

    static List<GodTool> all() {
        return List.of(
                GodTool.sync("empower",
                        "Upgrade a mortal's armor and/or held tool or weapon to a finer material (leather, gold, "
                                + "chainmail or copper become iron, iron becomes diamond, diamond becomes netherite), "
                                + "keeping its name and enchantments and mending it. The mortal sees a golden "
                                + "summoning circle bloom at their feet, is lifted gently into the air while a "
                                + "spiral of light tightens around them and collapses into their chest, then it "
                                + "bursts in a flash with a triumphant chime as the new gear appears (about 3.5 s; "
                                + "they float down softly). A reward for devotion or great deeds.",
                        Schema.object()
                                .str("player", "The mortal's name.", true)
                                .choice("what", "What to upgrade: armor (default), held (main-hand item) or both.",
                                        false, List.of("armor", "held", "both"))
                                .choice("tier", "The material to raise it to: next (default, one step up), iron, "
                                        + "diamond or netherite. Never downgrades.", false,
                                        List.of("next", "iron", "diamond", "netherite"))
                                .bool("fill_empty", "Also clothe empty armor slots with new pieces of the tier "
                                        + "(iron for next). Default false.", false),
                        RiteTools::empower),
                GodTool.sync("rite",
                        "Perform a choreographed rite of light and sound around a mortal or a place, purely for "
                                + "show (it changes nothing; your powers play fitting rites by themselves). Kinds: "
                                + "ascension (a golden circle, a tightening spiral, light collapsing into them and "
                                + "a triumphant burst, 4 s), anoint (a golden ring descends onto an eight-pointed "
                                + "star, sparkles rise), renewal (green and rose comets spiral up, hearts circle "
                                + "the head), resurrection (a pillar of white and gold light, golden rings and a "
                                + "totem burst), blessing (a violet hexagon and rings rising up the body), curse (a "
                                + "blood-red pentagram tightening, shadow rings sinking, a dark heartbeat), "
                                + "judgement (a red ring closing in, a red beam from the sky, a flash and burst of "
                                + "flame), seal (a great violet summoning circle on the ground, 5 s), arrival (a "
                                + "storm of shadow funneling down with lightning, a flash and a shockwave: how your "
                                + "body manifests), departure (a puff of light and a dark spiral unwinding into the "
                                + "sky). Rites on a mortal follow them.",
                        Schema.object()
                                .choice("kind", "Which rite.", true, Rites.kindNames())
                                .str("target", "Where: " + Targets.TARGET_HELP + ".", true),
                        RiteTools::rite));
    }

    // ---- empower -----------------------------------------------------------------------------

    private static final String[] ARMOR_SUFFIXES = { "_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS" };
    private static final String[] TOOL_SUFFIXES = { "_SWORD", "_PICKAXE", "_AXE", "_SHOVEL", "_HOE", "_SPEAR" };
    private static final Map<String, Integer> ARMOR_RANK = Map.of("LEATHER", 0, "COPPER", 1, "GOLDEN", 1,
            "CHAINMAIL", 2, "IRON", 3, "DIAMOND", 4, "NETHERITE", 5);
    private static final Map<String, Integer> TOOL_RANK = Map.of("WOODEN", 0, "GOLDEN", 0, "STONE", 1, "COPPER", 2,
            "IRON", 3, "DIAMOND", 4, "NETHERITE", 5);
    private static final String[] TIER_BY_RANK = { null, null, null, "IRON", "DIAMOND", "NETHERITE" };
    private static final EquipmentSlot[] ARMOR_SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET };

    /** The rank a tier name stands for (3 iron, 4 diamond, 5 netherite), -1 for "next", null if unknown. */
    private static Integer tierRank(String tier) {
        return switch (tier == null ? "next" : tier.trim().toLowerCase(Locale.ROOT)) {
            case "next", "" -> -1;
            case "iron" -> 3;
            case "diamond" -> 4;
            case "netherite" -> 5;
            default -> null;
        };
    }

    /**
     * The material an armor piece or tool becomes when raised to tier ("next", "iron", "diamond",
     * "netherite"), by name, e.g. LEATHER_HELMET → IRON_HELMET; null when it cannot be raised.
     */
    static String upgraded(String materialName, String tier) {
        if (materialName == null) return null;
        Integer want = tierRank(tier);
        if (want == null) return null;
        String name = materialName.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
        int rank = -1;
        String suffix = null;
        if (name.equals("TURTLE_HELMET")) {
            rank = 3;
            suffix = "_HELMET";
        } else {
            for (String s : ARMOR_SUFFIXES) {
                if (name.endsWith(s) && ARMOR_RANK.containsKey(name.substring(0, name.length() - s.length()))) {
                    rank = ARMOR_RANK.get(name.substring(0, name.length() - s.length()));
                    suffix = s;
                }
            }
            for (String s : TOOL_SUFFIXES) {
                // _PICKAXE also ends with _AXE: the material prefix must be a known one.
                if (suffix == null && name.endsWith(s)
                        && TOOL_RANK.containsKey(name.substring(0, name.length() - s.length()))) {
                    rank = TOOL_RANK.get(name.substring(0, name.length() - s.length()));
                    suffix = s;
                }
            }
        }
        if (suffix == null) return null;
        int target = want == -1 ? (rank < 3 ? 3 : rank + 1) : want;
        if (target > 5 || target <= rank) return null;
        return TIER_BY_RANK[target] + suffix;
    }

    private record Change(EquipmentSlot slot, Material from, Material to) {
        String describe() {
            return (from == null ? "(empty)" : key(from)) + " → " + key(to);
        }
    }

    private static String key(Material m) {
        return m.getKey().getKey();
    }

    private static ToolResult empower(Args a) throws ToolException {
        Player p = Targets.player(a.str("player"));
        String what = a.str("what", "armor").toLowerCase(Locale.ROOT);
        String tier = a.str("tier", "next").toLowerCase(Locale.ROOT);
        if (!List.of("armor", "held", "both").contains(what)) {
            throw new ToolException("'what' must be armor, held or both.");
        }
        if (tierRank(tier) == null) {
            throw new ToolException("'tier' must be next, iron, diamond or netherite.");
        }
        boolean fill = a.bool("fill_empty", false);
        List<EquipmentSlot> slots = new ArrayList<>();
        if (!what.equals("held")) slots.addAll(List.of(ARMOR_SLOTS));
        if (!what.equals("armor")) slots.add(EquipmentSlot.HAND);

        PlayerInventory inv = p.getInventory();
        String fillTier = tier.equals("next") ? "IRON" : tier.toUpperCase(Locale.ROOT);
        List<Change> changes = new ArrayList<>();
        int worn = 0;
        boolean held = false;
        for (EquipmentSlot slot : slots) {
            ItemStack item = inv.getItem(slot);
            boolean empty = item == null || item.getType().isAir();
            if (empty) {
                if (fill && slot != EquipmentSlot.HAND) {
                    Material m = Material.matchMaterial(fillTier + ARMOR_SUFFIXES[slotIndex(slot)]);
                    if (m != null) changes.add(new Change(slot, null, m));
                }
                continue;
            }
            if (slot == EquipmentSlot.HAND) held = true;
            else worn++;
            String up = upgraded(item.getType().name(), tier);
            Material m = up == null ? null : Material.matchMaterial(up);
            if (m != null) changes.add(new Change(slot, item.getType(), m));
        }
        if (changes.isEmpty()) {
            String name = p.getName();
            if (what.equals("armor") && worn == 0) {
                throw new ToolException(name + " wears no armor to upgrade. Pass fill_empty true to clothe them.");
            }
            if (what.equals("held") && !held) {
                throw new ToolException(name + " holds nothing. Use give_item to grant a weapon or tool.");
            }
            if (what.equals("both") && worn == 0 && !held) {
                throw new ToolException(name + " wears no armor and holds nothing. Pass fill_empty true to clothe "
                        + "them.");
            }
            throw new ToolException("Nothing " + name + " " + (what.equals("held") ? "holds" : "wears or holds")
                    + " can be raised to " + tier + ": it is already as fine (or is not armor, a weapon or a "
                    + "tool of wood, stone, copper, gold, iron or diamond).");
        }

        Runnable apply = () -> {
            if (!p.isOnline()) return;
            PlayerInventory now = p.getInventory();
            for (Change c : changes) {
                ItemStack item = now.getItem(c.slot());
                boolean empty = item == null || item.getType().isAir();
                if (c.from() == null) {
                    if (empty) now.setItem(c.slot(), new ItemStack(c.to()));
                    continue;
                }
                if (empty || item.getType() != c.from()) continue; // it changed meanwhile
                ItemStack up = item.withType(c.to());
                up.editMeta(Damageable.class, d -> d.setDamage(0));
                now.setItem(c.slot(), up);
            }
        };
        List<String> lines = changes.stream().map(Change::describe).toList();
        boolean shown = false;
        if (GodSettings.ritesEnabled()) {
            shown = Rites.play(Rites.Kind.ASCENSION, p.getLocation(), p, apply);
            if (shown) {
                // Lift them a little (about 2.3 blocks) so the spiral closes as they rise, then let them
                // drift down softly. Starts 16 ticks in and ends near the climax (tick 68).
                Realm.later(() -> {
                    if (!p.isOnline() || !p.isValid()) return;
                    p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 52, 0, false, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 130, 0, false, false, false));
                }, 16);
            }
        } else {
            apply.run();
        }
        if (!shown) p.playSound(p.getLocation(), "entity.player.levelup", 1f, 1.2f);
        return ToolResult.ok("%s %s: %s.", p.getName(),
                shown ? "rises in a rite of ascension; at its height (about 3.5 s) their gear becomes"
                        : "is empowered",
                String.join(", ", lines));
    }

    private static int slotIndex(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            default -> 3;
        };
    }

    // ---- rite --------------------------------------------------------------------------------

    private static ToolResult rite(Args a) throws ToolException {
        Rites.Kind kind = Rites.kind(a.str("kind"));
        if (kind == null) {
            throw new ToolException("Unknown rite '" + a.str("kind") + "'. Use one of " + Rites.kindNames() + ".");
        }
        if (!GodSettings.ritesEnabled()) {
            throw new ToolException("Rites are disabled on this server. Use particles for spectacle instead.");
        }
        String targetName = a.str("target");
        Location at = Targets.location(targetName);
        Entity anchor = null;
        try {
            anchor = Targets.player(targetName);
        } catch (ToolException ignored) {
            // a place, not a mortal: the rite stays where it is
        }
        if (!Rites.play(kind, at, anchor, null)) {
            throw new ToolException("Too many rites are already under way (at most " + Rites.MAX_RITES
                    + "). Wait a few seconds and try again.");
        }
        return ToolResult.ok("The %s rite unfolds at %s%s (about %.1f s).", kind.id(), Targets.describe(at),
                anchor != null ? ", following " + anchor.getName() : "", kind.frames / 10.0);
    }

    // ---- automatic rites ---------------------------------------------------------------------

    /** Effects whose rite is a curse rather than a blessing (as apply_effect names them). */
    static final Set<String> HARMFUL = Set.of("slowness", "slow", "weakness", "blindness", "nausea", "confusion",
            "poison", "wither", "darkness", "mining_fatigue", "slow_digging", "hunger", "levitation", "fire",
            "burning", "ignite", "bad_omen", "unluck", "instant_damage", "harm");

    /** The rite that follows a power, or null. */
    static Rites.Kind riteFor(String tool, JsonObject input) {
        if (tool == null) return null;
        return switch (tool) {
            case "give_item", "supply_chest" -> Rites.Kind.GIFT;
            case "grant_xp" -> number(input, "levels") > 0 ? Rites.Kind.ANOINT : null;
            case "heal" -> Rites.Kind.HEALING;
            case "teleport" -> Rites.Kind.BLINK;
            case "spawn_entity" -> Rites.Kind.SUMMON;
            case "build", "conjure", "transform_structure" -> Rites.Kind.FORGE;
            case "bestow_title", "set_quest" -> Rites.Kind.SIGIL;
            case "take_item" -> Rites.Kind.DEPARTURE;
            case "revive" -> Rites.Kind.RESURRECTION;
            case "apply_effect" -> {
                String id = text(input, "effect");
                if (id == null) yield null;
                id = id.toLowerCase(Locale.ROOT).replace(' ', '_').replace("minecraft:", "");
                yield HARMFUL.contains(id) ? Rites.Kind.CURSE : Rites.Kind.BLESSING;
            }
            default -> null;
        };
    }

    private static String text(JsonObject input, String key) {
        if (input == null) return null;
        JsonElement e = input.get(key);
        if (e == null || !e.isJsonPrimitive()) return null;
        String s = e.getAsString();
        return s.isBlank() ? null : s.trim();
    }

    private static double number(JsonObject input, String key) {
        try {
            String s = text(input, key);
            return s == null ? 0 : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** At most this many mortals get their own rite when a power touches "all". */
    private static final int MAX_AUTO = 4;

    /** Plays the rite that goes with a power that just succeeded (any thread). */
    static void after(String tool, JsonObject input) {
        Rites.Kind kind = riteFor(tool, input);
        if (kind == null || !GPTGOD.get().isEnabled() || !GodSettings.ritesEnabled()) return;
        String who = null;
        for (String key : new String[] { "player", "for", "target", "near", "at", "structure" }) {
            who = text(input, key);
            if (who != null) break;
        }
        if (who == null) return;
        String name = who;
        Runnable show = () -> {
            try {
                int n = 0;
                for (Player p : Targets.players(name)) {
                    if (n++ >= MAX_AUTO) break;
                    Rites.play(kind, p.getLocation(), p, null);
                }
                return;
            } catch (ToolException notAMortal) {
                // a place, a structure or the vessel: play it there
            }
            try {
                Location at = Targets.location(name);
                if (at != null) Rites.play(kind, at.clone(), null, null);
            } catch (ToolException ignored) {
                // nowhere to show it
            }
        };
        if (Bukkit.isPrimaryThread()) {
            show.run();
        } else {
            Realm.soon(show); // keeps this god bound when it runs
        }
    }
}
