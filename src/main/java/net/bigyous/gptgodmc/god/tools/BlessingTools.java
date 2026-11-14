package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Speech;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

/** Gifts and mercy. */
final class BlessingTools {
    private BlessingTools() {
    }

    static List<GodTool> all() {
        return List.of(
                GodTool.sync("give_item",
                        "Make an item appear in a mortal's inventory. You may bless it as a relic with a custom "
                                + "name, lore and enchantments (e.g. a sword named \"Oathkeeper\" with sharpness 3). "
                                + "Use Java Edition item ids like golden_apple, diamond_sword, cooked_beef.",
                        Schema.object()
                                .str("player", "The mortal's name.", true)
                                .str("item", "Item id, e.g. diamond_sword.", true)
                                .integer("count", "How many. Default 1.", false, 1, 64)
                                .str("name", "Optional custom name for a relic.", false)
                                .str("lore", "Optional one-line inscription shown under the name.", false)
                                .strings("enchantments", "Optional enchantments as 'id:level', e.g. 'sharpness:3'.",
                                        false, 6),
                        BlessingTools::giveItem),
                GodTool.sync("supply_chest",
                        "Conjure a chest beside a mortal filled with supplies for a project or a reward.",
                        Schema.object()
                                .str("player", "The mortal who receives the chest.", true)
                                .strings("items", "Items as 'id' or 'id:count', e.g. ['oak_log:32','torch:16'].",
                                        true, 27),
                        BlessingTools::supplyChest),
                GodTool.sync("heal",
                        "Restore a mortal (or everyone): full health and hunger, fire extinguished, harmful "
                                + "effects removed.",
                        Schema.object().str("player", "The mortal's name, or 'all'.", true),
                        a -> {
                            List<String> healed = new ArrayList<>();
                            List<String> dead = new ArrayList<>();
                            for (Player p : Targets.players(a.str("player"))) {
                                // Health given to someone on the death screen locks their Respawn button.
                                if (p.isDead()) {
                                    dead.add(p.getName());
                                    continue;
                                }
                                p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
                                p.setFoodLevel(20);
                                p.setSaturation(20f);
                                p.setFireTicks(0);
                                for (PotionEffect e : p.getActivePotionEffects()) {
                                    if (e.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL) {
                                        p.removePotionEffect(e.getType());
                                    }
                                }
                                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 8, 0.5, 0.3,
                                        0.5);
                                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.4f);
                                healed.add(p.getName());
                            }
                            String late = dead.isEmpty() ? "" : (healed.isEmpty() ? "" : " ") + String.join(", ", dead)
                                    + (dead.size() == 1 ? " is" : " are") + " dead and cannot be healed (use revive).";
                            if (healed.isEmpty() && !dead.isEmpty()) return ToolResult.error(late);
                            return ToolResult.ok("Healed: " + String.join(", ", healed) + "." + late);
                        }),
                GodTool.sync("apply_effect",
                        "Bless or curse with a potion effect: e.g. speed, strength, regeneration, resistance, "
                                + "night_vision, haste, luck, glowing, slowness, weakness, blindness, nausea, "
                                + "poison, levitation, darkness, mining_fatigue, hunger. Use 'fire' to set them "
                                + "ablaze for the duration.",
                        Schema.object()
                                .str("player", "The mortal's name, or 'all'.", true)
                                .str("effect", "Effect id.", true)
                                .integer("seconds", "Duration. Default 30.", false, 1, 600)
                                .integer("level", "Strength 1-5. Default 1.", false, 1, 5),
                        BlessingTools::applyEffect),
                GodTool.sync("grant_xp",
                        "Grant (or take, with a negative number) experience levels.",
                        Schema.object()
                                .str("player", "The mortal's name.", true)
                                .integer("levels", "Levels to add or remove.", true, -30, 30),
                        a -> {
                            Player p = Targets.player(a.str("player"));
                            int levels = a.integer("levels", 1, -30, 30);
                            p.giveExpLevels(levels);
                            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 0.8f);
                            return ToolResult.ok("%s is now level %d.", p.getName(), p.getLevel());
                        }),
                GodTool.sync("revive",
                        "Bring a dead mortal back to life at their bed or the world spawn: one still on the death "
                                + "screen rises at once, one left spectating becomes mortal again.",
                        Schema.object().str("player", "The mortal's name.", true),
                        a -> {
                            Player p = Targets.player(a.str("player"));
                            if (p.isDead()) {
                                // On the death screen: the game's own respawn is the only safe way back.
                                p.spigot().respawn();
                                Location at = p.getLocation();
                                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0, 1, 0), 60, 0.5, 1,
                                        0.5);
                                p.playSound(at, Sound.ITEM_TOTEM_USE, 0.8f, 1f);
                                return ToolResult.ok("%s lives again at %s.", p.getName(), Targets.describe(at));
                            }
                            if (p.getGameMode() != GameMode.SPECTATOR) {
                                return ToolResult.error(p.getName() + " is not dead: they already rose again by "
                                        + "themself (health " + Math.round(p.getHealth()) + "/20), so this was not "
                                        + "your doing; do not tell them you brought them back.");
                            }
                            Location bed = p.getRespawnLocation();
                            Location spawn = bed != null && Realm.current().governs(bed.getWorld()) ? bed
                                    : Realm.current().world().getSpawnLocation();
                            p.teleport(spawn);
                            p.setGameMode(GameMode.SURVIVAL);
                            p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
                            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5);
                            p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1f);
                            return ToolResult.ok("%s lives again at %s.", p.getName(), Targets.describe(spawn));
                        }),
                GodTool.sync("teleport",
                        "Move a mortal (or everyone) to a destination: " + Targets.TARGET_HELP + ".",
                        Schema.object()
                                .str("player", "The mortal's name, or 'all'.", true)
                                .str("destination", "Where to send them.", true),
                        a -> {
                            Location dest = Targets.location(a.str("destination")).clone();
                            dest = safeAbove(dest);
                            List<String> moved = new ArrayList<>();
                            int i = 0;
                            for (Player p : Targets.players(a.str("player"))) {
                                Location spot = dest.clone().add(i % 3 - 1, 0, (i / 3) % 3 - 1);
                                p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 40,
                                        0.4, 0.8, 0.4, 0.05);
                                p.teleport(spot);
                                p.playSound(spot, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.7f);
                                moved.add(p.getName());
                                i++;
                            }
                            return ToolResult.ok("Moved %s to %s.", String.join(", ", moved), Targets.describe(dest));
                        }),
                GodTool.sync("take_item",
                        "Take items from a mortal's inventory: confiscate weapons, demand an offering, punish "
                                + "greed. Use item 'held' to take whatever is in their hand.",
                        Schema.object()
                                .str("player", "The mortal's name.", true)
                                .str("item", "Item id, or 'held'.", true)
                                .integer("count", "How many to take. Default 1.", false, 1, 2304),
                        BlessingTools::takeItem));
    }

    static Material material(String id, boolean mustBeItem) throws ToolException {
        Material m = Material.matchMaterial(id.trim().toLowerCase(Locale.ROOT).replace(' ', '_'));
        if (m == null || m.isAir() || (mustBeItem && !m.isItem())) {
            throw new ToolException("Unknown item '" + id + "'. Use Java Edition ids such as diamond_sword, "
                    + "golden_apple, oak_log.");
        }
        return m;
    }

    private static ToolResult giveItem(Args a) throws ToolException {
        Player p = Targets.player(a.str("player"));
        Material m = material(a.str("item"), true);
        int count = a.integer("count", 1, 1, 64);
        ItemStack stack = new ItemStack(m, count);
        ItemMeta meta = stack.getItemMeta();
        List<String> notes = new ArrayList<>();
        if (meta != null) {
            if (a.has("name")) {
                meta.displayName(Component.text(a.str("name"), Speech.VIOLET).decoration(TextDecoration.ITALIC, false));
            }
            if (a.has("lore")) {
                meta.lore(List.of(Component.text(a.str("lore"), Speech.DEEP_VIOLET)));
            }
            for (String spec : a.strings("enchantments")) {
                String[] parts = spec.split("[: ]");
                Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT)));
                if (ench == null) {
                    notes.add("unknown enchantment " + parts[0] + " skipped");
                    continue;
                }
                int level = 1;
                if (parts.length > 1) {
                    try {
                        level = Math.max(1, Math.min(10, Integer.parseInt(parts[1])));
                    } catch (NumberFormatException ignored) {
                        notes.add("bad level for " + parts[0]);
                    }
                }
                meta.addEnchant(ench, level, true);
            }
            stack.setItemMeta(meta);
        }
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(stack);
        leftover.values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1.2, 0), 25, 0.4, 0.6, 0.4, 0.02);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.6f);
        return ToolResult.ok("Gave %d %s to %s%s%s.", count, m.getKey().getKey(), p.getName(),
                leftover.isEmpty() ? "" : " (inventory full, the rest dropped at their feet)",
                notes.isEmpty() ? "" : "; " + String.join("; ", notes));
    }

    private static ToolResult supplyChest(Args a) throws ToolException {
        Player p = Targets.player(a.str("player"));
        List<ItemStack> items = new ArrayList<>();
        List<String> bad = new ArrayList<>();
        for (String spec : a.strings("items")) {
            String[] parts = spec.split("[: ]");
            Material m;
            try {
                m = material(parts[0], true);
            } catch (ToolException e) {
                bad.add(parts[0]);
                continue;
            }
            int count = 1;
            if (parts.length > 1) {
                try {
                    count = Math.max(1, Math.min(m.getMaxStackSize() * 4, Integer.parseInt(parts[1])));
                } catch (NumberFormatException ignored) {
                    count = 1;
                }
            }
            while (count > 0) {
                int stackSize = Math.min(count, m.getMaxStackSize());
                items.add(new ItemStack(m, stackSize));
                count -= stackSize;
            }
        }
        if (items.isEmpty()) {
            return ToolResult.error("No valid items. Unknown: " + String.join(", ", bad));
        }
        Location base = p.getLocation();
        Block spot = findChestSpot(base);
        if (spot == null) {
            return ToolResult.error("There is no free space beside " + p.getName() + " for a chest.");
        }
        spot.setType(Material.CHEST);
        Chest chest = (Chest) spot.getState();
        Map<Integer, ItemStack> overflow = chest.getBlockInventory().addItem(items.toArray(new ItemStack[0]));
        overflow.values().forEach(rest -> spot.getWorld().dropItemNaturally(spot.getLocation().add(0.5, 1, 0.5), rest));
        spot.getWorld().spawnParticle(Particle.WAX_OFF, spot.getLocation().add(0.5, 0.8, 0.5), 40, 0.6, 0.6, 0.6);
        spot.getWorld().playSound(spot.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 0.6f);
        return ToolResult.ok("A chest with %d stack(s) appeared beside %s at %s%s.", items.size(), p.getName(),
                Targets.describe(spot.getLocation()), bad.isEmpty() ? "" : ". Unknown items skipped: " + bad);
    }

    private static Block findChestSpot(Location base) {
        int[][] offsets = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { -1, -1 }, { 1, -1 }, { -1, 1 } };
        for (int[] o : offsets) {
            for (int dy = 0; dy <= 1; dy++) {
                Block b = base.getBlock().getRelative(o[0], dy, o[1]);
                if ((b.getType().isAir() || b.isReplaceable()) && b.getRelative(0, -1, 0).getType().isSolid()) {
                    return b;
                }
            }
        }
        return null;
    }

    private static ToolResult applyEffect(Args a) throws ToolException {
        String id = a.str("effect").toLowerCase(Locale.ROOT).replace(' ', '_').replace("minecraft:", "");
        int seconds = a.integer("seconds", 30, 1, 600);
        int level = a.integer("level", 1, 1, 5);
        List<Player> players = Targets.players(a.str("player"));
        if (id.equals("fire") || id.equals("burning") || id.equals("ignite")) {
            for (Player p : players) {
                p.setFireTicks(seconds * 20);
            }
            return ToolResult.ok("Set ablaze for %ds: %s", seconds, names(players));
        }
        PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(id));
        if (type == null) {
            type = PotionEffectType.getByName(id.toUpperCase(Locale.ROOT));
        }
        if (type == null) {
            return ToolResult.error("Unknown effect '" + id + "'. Try speed, strength, regeneration, slowness, "
                    + "weakness, blindness, levitation, glowing, night_vision, poison.");
        }
        boolean harmful = type.getEffectCategory() == PotionEffectType.Category.HARMFUL;
        for (Player p : players) {
            p.addPotionEffect(new PotionEffect(type, seconds * 20, level - 1, false, true, true));
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4,
                    new Particle.DustOptions(harmful ? Color.fromRGB(0x3A0CA3) : Color.fromRGB(0xE0AAFF), 1.2f));
        }
        return ToolResult.ok("%s %s %d for %ds on %s.", harmful ? "Cursed with" : "Blessed with",
                type.getKey().getKey(), level, seconds, names(players));
    }

    private static ToolResult takeItem(Args a) throws ToolException {
        Player p = Targets.player(a.str("player"));
        String id = a.str("item");
        if (id.equalsIgnoreCase("held") || id.equalsIgnoreCase("hand")) {
            ItemStack held = p.getInventory().getItemInMainHand();
            if (held.getType().isAir()) {
                return ToolResult.error(p.getName() + " holds nothing.");
            }
            String desc = held.getAmount() + " " + held.getType().getKey().getKey();
            p.getInventory().setItemInMainHand(null);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.5f);
            return ToolResult.ok("Took %s from %s's hand.", desc, p.getName());
        }
        Material m = material(id, true);
        int want = a.integer("count", 1, 1, 2304);
        // By kind, not by exact stack: removeItem(new ItemStack(m)) only matches pristine items, so a sword that has
        // been swung once, or anything enchanted or renamed, was "not there".
        ItemStack[] slots = p.getInventory().getContents();
        int[] counts = new int[slots.length];
        for (int i = 0; i < slots.length; i++) counts[i] = slots[i] != null && slots[i].getType() == m ? slots[i].getAmount() : 0;
        int[] from = takeFrom(counts, want, p.getInventory().getHeldItemSlot());
        int taken = 0;
        for (int i = 0; i < slots.length; i++) {
            if (from[i] == 0) continue;
            taken += from[i];
            if (from[i] >= slots[i].getAmount()) p.getInventory().setItem(i, null);
            else slots[i].setAmount(slots[i].getAmount() - from[i]);
        }
        int notFound = want - taken;
        if (taken == 0) {
            return ToolResult.error(p.getName() + " has no " + m.getKey().getKey() + ".");
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.5f);
        p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.01);
        return ToolResult.ok("Took %d %s from %s%s.", taken, m.getKey().getKey(), p.getName(),
                notFound > 0 ? " (they only had that many)" : "");
    }

    /**
     * How many to take from each slot, given how many of the wanted kind each slot holds: the slot in hand first
     * (taking what a mortal wields is the point of a confiscation), then the rest in order, {@code want} in all.
     */
    static int[] takeFrom(int[] counts, int want, int held) {
        int[] from = new int[counts.length];
        int left = Math.max(0, want);
        if (held >= 0 && held < counts.length) {
            from[held] = Math.min(counts[held], left);
            left -= from[held];
        }
        for (int i = 0; i < counts.length && left > 0; i++) {
            if (i == held) continue;
            from[i] = Math.min(counts[i], left);
            left -= from[i];
        }
        return from;
    }

    static Location safeAbove(Location loc) {
        Location l = loc.clone();
        for (int i = 0; i < 8; i++) {
            Block feet = l.getBlock();
            Block head = feet.getRelative(0, 1, 0);
            if (feet.isPassable() && head.isPassable()) {
                return l;
            }
            l.add(0, 1, 0);
        }
        return loc;
    }

    static String names(List<Player> players) {
        return String.join(", ", players.stream().map(Player::getName).toList());
    }
}
