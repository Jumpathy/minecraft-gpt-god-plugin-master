package net.bigyous.gptgodmc.god;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.ZipFile;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.vessel.Vessel;
import net.bigyous.gptgodmc.god.vessel.VesselMood;
import net.bigyous.gptgodmc.god.vessel.VesselPackHost;
import net.bigyous.gptgodmc.loggables.GodslayerLoggable;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;

/**
 * The Godslayer: the one blade that wounds a god. Its blows land on the vessel (see VesselListener),
 * take health from the god whose body it is ({@link Mortality}, kept in that god's memory), and at
 * 0 health the god dies in a storm of lightning and drops a hoard. A slain god does not think, has
 * no body and answers no prayers until it returns (godslayer.god-returns) or /god revive. Also owns
 * the blade item, its recipe, the boss bar over a wounded god and the soul fire trailing the blade.
 * Everything here runs on the server thread.
 */
public final class Godslayer implements Listener {
    private static NamespacedKey bladeKey;
    private static NamespacedKey relicKey;
    private static NamespacedKey recipeKey;
    private static NamespacedKey damageKey;
    private static NamespacedKey speedKey;
    private static BukkitTask ticker;
    private static BukkitTask aura;
    private static final Map<String, BossBar> BARS = new HashMap<>();
    private static final Map<String, Set<UUID>> VIEWERS = new HashMap<>();
    private static final Map<UUID, Long> LAST_BLOW = new HashMap<>();
    private static final Map<UUID, Long> NEXT_HUM = new HashMap<>();
    /** Which entries the served pack holds, checked once each against a ready pack. */
    private static final Map<String, Boolean> IN_PACK = new HashMap<>();
    private static long auraTicks;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final TextColor BLOOD = TextColor.color(0xD01818);
    private static final TextColor EMBER = TextColor.color(0xFF8A1E);

    private Godslayer() {
    }

    // ---- Lifecycle -----------------------------------------------------------------------------

    public static void enable(GPTGOD plugin) {
        bladeKey = new NamespacedKey(plugin, "godslayer");
        relicKey = new NamespacedKey(plugin, "godslayer_relic");
        recipeKey = new NamespacedKey(plugin, "godslayer");
        damageKey = new NamespacedKey(plugin, "godslayer_damage");
        speedKey = new NamespacedKey(plugin, "godslayer_speed");
        showKey = new NamespacedKey(plugin, "godslayer_show");
        try {
            ShapedRecipe recipe = new ShapedRecipe(recipeKey, blade());
            recipe.shape("ENE", "IHI", "DBD");
            recipe.setIngredient('E', Material.ECHO_SHARD);
            recipe.setIngredient('N', Material.NETHER_STAR);
            recipe.setIngredient('I', Material.NETHERITE_INGOT);
            recipe.setIngredient('H', Material.HEAVY_CORE);
            recipe.setIngredient('D', Material.DRAGON_BREATH);
            recipe.setIngredient('B', Material.BLAZE_ROD);
            Bukkit.removeRecipe(recipeKey);
            Bukkit.addRecipe(recipe);
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not add the Godslayer recipe", e);
        }
        plugin.getServer().getPluginManager().registerEvents(new Godslayer(), plugin);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, Godslayer::tick, 40L, 20L);
        aura = Bukkit.getScheduler().runTaskTimer(plugin, Godslayer::auraTick, 40L, 2L);
        GodslayerLoot.Parsed loot = GodslayerLoot.parse(GodSettings.godslayerLoot());
        for (String p : loot.problems()) GPTGOD.LOGGER.warn("godslayer.loot: " + p);
    }

    public static void disable() {
        if (ticker != null) ticker.cancel();
        if (aura != null) aura.cancel();
        finishDeaths();
        for (BossBar bar : BARS.values()) {
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
        }
        BARS.clear();
        VIEWERS.clear();
        if (recipeKey != null) Bukkit.removeRecipe(recipeKey);
    }

    // ---- The blade -----------------------------------------------------------------------------

    /** A new Godslayer. */
    public static ItemStack blade() {
        return blade(false);
    }

    /** A new Godslayer, or the Ascended Godslayer (one blow ends a god). */
    public static ItemStack blade(boolean ascended) {
        ItemStack it = new ItemStack(Material.NETHERITE_SWORD);
        it.editMeta(meta -> {
            if (ascended) {
                meta.displayName(MM.deserialize("<!italic><bold><gradient:#fff4c8:#ffd23a:#ffffff:#6af0ff:#ffd23a>"
                        + "Ascended Godslayer</gradient></bold>"));
                meta.lore(List.of(
                        MM.deserialize("<!italic><gray>The Godslayer, reforged in the"),
                        MM.deserialize("<!italic><gray>light of a god it slew."),
                        Component.empty(),
                        MM.deserialize("<!italic><gradient:#ffd23a:#ffffff>One blow ends a god.</gradient>"),
                        MM.deserialize("<!italic><gold><italic>Heaven itself fears its edge.")));
            } else {
                meta.displayName(MM.deserialize(
                        "<!italic><bold><gradient:#4a0000:#ff1f1f:#ffb000:#ff1f1f:#4a0000>Godslayer</gradient></bold>"));
                meta.lore(List.of(
                        MM.deserialize("<!italic><dark_gray>Forged from a dying star"),
                        MM.deserialize("<!italic><dark_gray>and the heart of a storm."),
                        Component.empty(),
                        MM.deserialize("<!italic><gradient:#ff1f1f:#ff8a1e>It thirsts for divine blood.</gradient>"),
                        MM.deserialize("<!italic><gray>The only blade that wounds a god."),
                        MM.deserialize("<!italic><dark_red><italic>What it slays stays slain.")));
            }
            meta.setEnchantmentGlintOverride(true);
            meta.setUnbreakable(true);
            meta.setRarity(ItemRarity.EPIC);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(damageKey, attackFor(ascended) - 1,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(speedKey, -2.4,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.getPersistentDataContainer().set(bladeKey, PersistentDataType.BYTE, (byte) (ascended ? 2 : 1));
            String model = modelFor(ascended);
            if (model != null) meta.setItemModel(NamespacedKey.fromString(model));
        });
        return it;
    }

    private static double attackFor(boolean ascended) {
        return ascended ? GodSettings.godslayerAscendedAttackDamage() : GodSettings.godslayerAttackDamage();
    }

    /** The blade's item_model when the served pack holds it, else null (the plain netherite sword look). */
    private static String modelFor(boolean ascended) {
        if (ascended) return packHas(GodslayerArt.ITEM_FILE_ASCENDED) ? GodslayerArt.MODEL_ASCENDED : null;
        return modelReady() ? GodslayerArt.MODEL : null;
    }

    public static boolean isBlade(ItemStack it) {
        return tier(it) > 0;
    }

    public static boolean isAscended(ItemStack it) {
        return tier(it) == 2;
    }

    /** 0 = not a Godslayer, 1 = the Godslayer, 2 = the Ascended Godslayer. */
    static int tier(ItemStack it) {
        if (bladeKey == null || it == null || it.getType() != Material.NETHERITE_SWORD || !it.hasItemMeta()) {
            return 0;
        }
        Byte b = it.getItemMeta().getPersistentDataContainer().get(bladeKey, PersistentDataType.BYTE);
        return b == null ? 0 : b == 2 ? 2 : 1;
    }

    /**
     * True when the vessel resource pack is served and holds the blade's model. Before that the blade
     * keeps the netherite sword's look (a missing model would show the purple-black cube).
     */
    static boolean modelReady() {
        return packHas(GodslayerArt.ITEM_FILE);
    }

    /** True when the vessel pack is served and contains this file (a missing model shows a purple cube). */
    public static boolean packHas(String entry) {
        if (!VesselPackHost.active()) return false;
        return IN_PACK.computeIfAbsent(entry, e -> {
            Path zip = GPTGOD.get().getDataFolder().toPath().resolve("resourcepack").resolve("gptgodmc-vessel.zip");
            if (!Files.exists(zip)) return false;
            try (ZipFile z = new ZipFile(zip.toFile())) {
                return z.getEntry(e) != null;
            } catch (java.io.IOException ex) {
                GPTGOD.LOGGER.debug("Could not read the vessel pack", ex);
                return false;
            }
        });
    }

    /** Gives a blade made before the pack was ready its own look (or takes it away). */
    /**
     * Keeps a blade up to date: its look once the pack is ready, and its attack damage when the setting
     * changed since it was made.
     */
    private static void refreshModel(Player p, ItemStack it) {
        boolean ascended = isAscended(it);
        ItemMeta meta = it.getItemMeta();
        boolean changed = false;
        String model = modelFor(ascended);
        NamespacedKey want = model == null ? null : NamespacedKey.fromString(model);
        NamespacedKey has = meta.hasItemModel() ? meta.getItemModel() : null;
        if (want == null ? has != null : !want.equals(has)) {
            meta.setItemModel(want);
            changed = true;
        }
        double dmg = attackFor(ascended) - 1;
        AttributeModifier current = null;
        var mods = meta.getAttributeModifiers(Attribute.ATTACK_DAMAGE);
        if (mods != null) {
            for (AttributeModifier m : mods) {
                if (m.getKey().equals(damageKey)) current = m;
            }
        }
        if (current == null || Math.abs(current.getAmount() - dmg) > 1e-6) {
            if (current != null) meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE, current);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(damageKey, dmg,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            changed = true;
        }
        if (changed) it.setItemMeta(meta);
    }

    /** Hands blades out: /god godslayer and /godslayer. Returns what to tell the sender. */
    public static String give(org.bukkit.command.CommandSender sender, String who) {
        return give(sender, who, false);
    }

    public static String give(org.bukkit.command.CommandSender sender, String who, boolean ascended) {
        List<Player> to = new ArrayList<>();
        if (who == null || who.isBlank()) {
            if (!(sender instanceof Player p)) return "From the console, name a player: godslayer <player|@a>";
            to.add(p);
        } else if (who.equalsIgnoreCase("@a") || who.equalsIgnoreCase("all")) {
            to.addAll(Bukkit.getOnlinePlayers());
        } else {
            Player p = Bukkit.getPlayerExact(who);
            if (p == null) p = Bukkit.getPlayer(who);
            if (p == null) return "No player online is called " + who + ".";
            to.add(p);
        }
        for (Player p : to) {
            for (ItemStack left : p.getInventory().addItem(blade(ascended)).values()) {
                p.getWorld().dropItem(p.getLocation(), left);
            }
            p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, SoundCategory.PLAYERS, 0.8f, 0.6f);
            p.sendMessage(ascended
                    ? Component.text("The Ascended Godslayer is in your hands. One blow ends a god.", EMBER)
                    : Component.text("A Godslayer is in your hands. Even gods can bleed.", BLOOD));
        }
        return "Gave " + (ascended ? "an Ascended Godslayer" : "a Godslayer") + " to " + (to.size() == 1 ? to.get(0).getName() : to.size() + " players") + ".";
    }

    /** /godslayer and /god godslayer: [player|@a], or preview [variant]. */
    public static String command(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("preview")) {
            return preview(sender, args.length > 1 ? args[1] : null);
        }
        if (args.length > 0 && (args[0].equalsIgnoreCase("ascended") || args[0].equalsIgnoreCase("upgraded"))) {
            return give(sender, args.length > 1 ? args[1] : null, true);
        }
        return give(sender, args.length == 0 ? null : args[0]);
    }

    /** Tab completion for those arguments. */
    public static List<String> complete(String[] args) {
        if (args.length == 0) return List.of();
        String typed = args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("preview");
            options.add("ascended");
            options.add("@a");
            for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("ascended")) {
            options.add("@a");
            for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("preview")) {
            for (DeathShow.Variant v : DeathShow.Variant.values()) options.add(v.id());
            options.add("random");
        }
        return options.stream().filter(s -> s.toLowerCase(java.util.Locale.ROOT).startsWith(typed)).toList();
    }

    // ---- Wounds --------------------------------------------------------------------------------

    public static boolean isSlain(Realm r) {
        return r != null && r.memory().mortality.isSlain();
    }

    /**
     * A blow of the blade on a vessel's part. Returns true when the blade handled it (the caller does
     * nothing more), false when it should count as an ordinary strike (the Godslayer is switched off).
     */
    public static boolean strike(Player p, Entity hit) {
        if (!GodSettings.godslayerEnabled()) return false;
        Realm r = Vessel.ownerOf(hit);
        if (r == null) return true;
        long now = System.currentTimeMillis();
        Long last = LAST_BLOW.get(p.getUniqueId());
        if (last != null && now - last < 450) return true;
        LAST_BLOW.put(p.getUniqueId(), now);
        Mortality m = r.memory().mortality;
        double max = GodSettings.godslayerHealth();
        double dmg = Mortality.damageFor(GodSettings.godslayerDamageMin(), GodSettings.godslayerDamageMax(),
                p.getAttackCooldown(), ThreadLocalRandom.current().nextDouble());
        if (isAscended(p.getInventory().getItemInMainHand()) && GodSettings.godslayerAscendedInstantKill()) {
            dmg = m.health(max) + 1; // one blow ends it
        }
        Mortality.Wound w = m.wound(dmg, p.getName(), max, now);
        if (w == null) return true;
        r.memory().save();
        Vessel v = r.vessel();
        Location at = v.location() != null ? v.location() : hit.getLocation();
        net.bigyous.gptgodmc.client.ModFx.godWounded(at);
        if (w.killed()) {
            slay(r, p, at);
            return true;
        }
        String hp = Mortality.fmt(w.health()) + "/" + Mortality.fmt(max);
        String god = r.displayName();
        r.run(() -> {
            Journal.add(Journal.Kind.INFO, p.getName() + " wounded the vessel with the Godslayer (" + hp + ")");
            EventLogger.addLoggable(GodslayerLoggable.wound(p.getName(), hp));
        });
        for (Realm peer : r.peers()) {
            peer.run(() -> EventLogger.addLoggable(GodslayerLoggable.godWounded(god, p.getName(), hp)));
        }
        woundEffects(at.clone().add(0, v.eyeHeight() * 0.75, 0), p);
        v.react(VesselMood.WRATHFUL, 30, p, "roar");
        p.sendActionBar(Component.text("☠ You wounded " + god + " — " + hp + " ☠", BLOOD));
        updateBar(r, now);
        return true;
    }

    private static void woundEffects(Location chest, Player p) {
        World w = chest.getWorld();
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, chest, 40, 0.4, 0.8, 0.4, 0.08);
        w.spawnParticle(Particle.DUST, chest, 50, 0.5, 0.9, 0.5, 0, new Particle.DustOptions(Color.fromRGB(0xB00000),
                1.6f));
        w.spawnParticle(Particle.DAMAGE_INDICATOR, chest, 12, 0.4, 0.6, 0.4, 0.2);
        w.spawnParticle(Particle.FLASH, chest, 1, Color.fromRGB(0xFF3030));
        w.playSound(chest, Sound.ENTITY_WITHER_HURT, SoundCategory.HOSTILE, 1.6f, 0.5f);
        w.playSound(chest, Sound.ITEM_TRIDENT_THUNDER, SoundCategory.HOSTILE, 0.7f, 1.6f);
        w.playSound(chest, Sound.ENTITY_ELDER_GUARDIAN_HURT, SoundCategory.HOSTILE, 1.2f, 0.5f);
    }

    // ---- Death ---------------------------------------------------------------------------------

    private static void slay(Realm r, Player killer, Location base) {
        String god = r.displayName();
        String by = killer.getName();
        long now = System.currentTimeMillis();
        Vessel v = r.vessel();
        double eye = v.eyeHeight();
        Location ground = base.clone();
        Location chest = base.clone().add(0, eye * 0.75, 0);
        r.run(() -> {
            Journal.add(Journal.Kind.INFO, god + " was slain by " + by + " with the Godslayer");
            Memory mem = r.memory();
            mem.addDeed(by, "SLEW YOU with the Godslayer", now);
            mem.heart.feel(Heart.Feeling.DREAD, 70, by + " slew you with the Godslayer", now);
            mem.heart.feel(Heart.Feeling.WRATH, 60, by + " slew you", now);
            EventLogger.addLoggable(GodslayerLoggable.slain(by));
            mem.save();
        });
        for (Realm peer : r.peers()) {
            peer.run(() -> EventLogger.addLoggable(GodslayerLoggable.godSlain(god, by)));
        }
        hideBar(r);
        GPTGOD.LOGGER.info(god + " was slain by " + by + " with the Godslayer");

        Title title = Title.title(
                MM.deserialize("<bold><gradient:#ff1f1f:#ffb000>" + MiniMessage.miniMessage().escapeTags(god)
                        + " has been slain</gradient></bold>"),
                Component.text("by " + by, NamedTextColor.GRAY),
                Title.Times.times(java.time.Duration.ofMillis(300), java.time.Duration.ofSeconds(5),
                        java.time.Duration.ofSeconds(2)));
        Component chat = Component.text("☠ ", BLOOD)
                .append(Component.text(god, EMBER).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" has been slain by " + by + " with the Godslayer. ☠", BLOOD));
        TabList.refreshSoon();

        // The death show: the wound, the collapse, the burst, the afterglow (see DeathShow).
        Persona persona = r.persona();
        DeathShow.Variant variant = DeathShow.pick(persona == null ? null : persona.archetype, r.lookSetting(),
                r.id.hashCode());
        net.bigyous.gptgodmc.client.ModFx.godFalling(ground);
        Map<Integer, Runnable> marks = new HashMap<>();
        marks.put(0, () -> r.run(() -> v.react(VesselMood.SORROWFUL, 10, killer.isOnline() ? killer : null,
                "roar")));
        marks.put(12, () -> r.run(() -> v.gesture("bow")));
        marks.put(20, () -> r.run(() -> v.gesture("shake_head")));
        marks.put(DeathShow.COLLAPSE, () -> Spectacle.earthquake(ground.clone(), 16, 3, false));
        marks.put(DeathShow.VANISH, () -> r.run(v::vanish));
        marks.put(DeathShow.BURST, () -> {
            net.bigyous.gptgodmc.client.ModFx.godSlain(ground, variant.name().toLowerCase(java.util.Locale.ROOT));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(title);
                p.sendMessage(chat);
            }
            if (killer.isOnline()) {
                killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1f,
                        0.8f);
            }
        });
        marks.put(DeathShow.LOOT, () -> dropLoot(chest, god, by));
        playDeath(variant, ground, v.size() * 1.8, marks);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, SoundCategory.MASTER, 0.6f, 0.5f);
        }
    }

    // ---- The death show ------------------------------------------------------------------------

    /** One death show being played. */
    private static final class Playing {
        final DeathShow.Variant variant;
        final Location ground;
        final double h;
        final List<Rites.Cue> cues;
        final List<DeathShow.Bolt> bolts;
        final List<DeathShow.Firework> fireworks;
        final Map<Integer, Runnable> marks;
        final DeathStage stage;
        int frame;

        Playing(DeathShow.Variant variant, Location ground, double h, Map<Integer, Runnable> marks) {
            this.variant = variant;
            this.ground = ground;
            this.h = h;
            this.cues = DeathShow.cues(variant);
            this.bolts = DeathShow.bolts(variant, h);
            this.fireworks = DeathShow.fireworks(variant, h);
            this.marks = marks;
            DeathStage built = null;
            try {
                built = new DeathStage(variant, ground, h);
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("The death show's pieces could not be set up", e);
            }
            this.stage = built;
        }
    }

    private static final List<Playing> DEATHS = new ArrayList<>();
    private static NamespacedKey showKey;

    /** Starts a death show on the ground at `ground` for a body h blocks tall. */
    static void playDeath(DeathShow.Variant variant, Location ground, double h, Map<Integer, Runnable> marks) {
        DEATHS.add(new Playing(variant, ground.clone(), h, marks == null ? Map.of() : marks));
        Spectacle.wake();
    }

    static boolean deathsRunning() {
        return !DEATHS.isEmpty();
    }

    /** One frame of every death show, within the particle budget; returns how many particles it drew. */
    static int tickDeaths(int budget) {
        if (DEATHS.isEmpty()) return 0;
        int each = Math.max(60, budget / DEATHS.size());
        int spawned = 0;
        for (Playing s : new ArrayList<>(DEATHS)) {
            try {
                spawned += drawDeath(s, each);
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("The death show failed at frame " + s.frame, e);
            }
            runMark(s.marks.get(s.frame));
            s.frame++;
            if (s.frame >= DeathShow.FRAMES) {
                // Whatever was still to come (e.g. the loot) happens now.
                for (Map.Entry<Integer, Runnable> m : s.marks.entrySet()) {
                    if (m.getKey() >= s.frame) runMark(m.getValue());
                }
                if (s.stage != null) s.stage.clear();
                DEATHS.remove(s);
            }
        }
        return spawned;
    }

    private static void runMark(Runnable mark) {
        if (mark == null) return;
        try {
            mark.run();
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("A death show step failed", e);
        }
    }

    /** On shutdown: whatever a death still owed (the loot) is given now. */
    private static void finishDeaths() {
        for (Playing s : new ArrayList<>(DEATHS)) {
            for (Map.Entry<Integer, Runnable> m : s.marks.entrySet()) {
                if (m.getKey() >= s.frame && m.getKey() == DeathShow.LOOT) runMark(m.getValue());
            }
            if (s.stage != null) s.stage.clear();
        }
        DEATHS.clear();
    }

    private static int drawDeath(Playing s, int budget) {
        World w = s.ground.getWorld();
        int f = s.frame;
        if (s.stage != null) {
            try {
                s.stage.frame(f);
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("The death show's pieces failed at frame " + f, e);
            }
        }
        List<Rites.Dot> dots = DeathShow.frame(s.variant, f, s.h);
        int step = dots.size() <= budget ? 1 : (int) Math.ceil(dots.size() / (double) budget);
        int spawned = 0;
        double gx = s.ground.getX(), gy = s.ground.getY(), gz = s.ground.getZ();
        for (int i = step == 1 ? 0 : f % step; i < dots.size(); i += step) {
            Rites.Dot d = dots.get(i);
            if (d.moving()) {
                w.spawnParticle(d.tint().particle, gx + d.x(), gy + d.y(), gz + d.z(), 0, d.vx(), d.vy(), d.vz(), 1.0,
                        d.tint().data(), true);
            } else {
                w.spawnParticle(d.tint().particle, gx + d.x(), gy + d.y(), gz + d.z(), 1, 0, 0, 0, 0,
                        d.tint().data(), true);
            }
            spawned++;
        }
        Location ear = s.ground.clone().add(0, s.h * 0.6, 0);
        for (Rites.Cue c : s.cues) {
            if (c.frame() == f) w.playSound(ear, c.sound(), SoundCategory.MASTER, c.volume(), c.pitch());
        }
        for (DeathShow.Bolt b : s.bolts) {
            if (b.frame() != f) continue;
            Location at = s.ground.clone().add(b.x(), 0, b.z());
            at.setY(w.getHighestBlockYAt(at) + 1);
            w.strikeLightningEffect(at);
        }
        for (DeathShow.Firework fw : s.fireworks) {
            if (fw.frame() == f) firework(s.ground.clone().add(fw.x(), fw.y(), fw.z()), fw);
        }
        if (f == DeathShow.DARK_FROM) {
            double r = DeathShow.reach(s.h) * 3;
            int ticks = (DeathShow.DARK_TO - DeathShow.DARK_FROM) * 2 + 20;
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(s.ground) <= r * r) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0, false, false, false));
                }
            }
        }
        return spawned;
    }

    /** A firework that bursts where it is spawned and hurts no one (see onFireworkHit). */
    private static void firework(Location at, DeathShow.Firework spec) {
        FireworkEffect.Type type;
        try {
            type = FireworkEffect.Type.valueOf(spec.type());
        } catch (IllegalArgumentException e) {
            type = FireworkEffect.Type.BALL;
        }
        FireworkEffect effect = FireworkEffect.builder().with(type)
                .withColor(spec.colors().stream().map(Color::fromRGB).toList())
                .withFade(spec.fades().stream().map(Color::fromRGB).toList()).flicker(true).trail(true).build();
        Firework fw = at.getWorld().spawn(at, Firework.class, e -> {
            FireworkMeta meta = e.getFireworkMeta();
            meta.addEffect(effect);
            meta.setPower(0);
            e.setFireworkMeta(meta);
            e.getPersistentDataContainer().set(showKey, PersistentDataType.BYTE, (byte) 1);
        });
        fw.detonate();
    }

    /**
     * /god godslayer preview: plays a death show at the current god's body (left standing) or in front
     * of the player. Nothing dies and nothing drops.
     */
    public static String preview(CommandSender sender, String which) {
        boolean named = which != null && !which.isBlank();
        DeathShow.Variant variant = named ? DeathShow.Variant.parse(which) : null;
        if (named && variant == null && !which.equalsIgnoreCase("random")) {
            return "Death shows: " + String.join(", ", Arrays.stream(DeathShow.Variant.values())
                    .map(DeathShow.Variant::id).toList()) + ", or random.";
        }
        Realm r = Realm.current();
        if (variant == null) {
            if (named) {
                DeathShow.Variant[] all = DeathShow.Variant.values();
                variant = all[ThreadLocalRandom.current().nextInt(all.length)];
            } else {
                Persona persona = r.persona();
                variant = DeathShow.pick(persona == null ? null : persona.archetype, r.lookSetting(), r.id.hashCode());
            }
        }
        Vessel v = r.vesselIfCreated();
        Location ground;
        double h = 2.7;
        if (v != null && v.location() != null) {
            ground = v.location();
            h = v.size() * 1.8;
        } else if (sender instanceof Player p) {
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
            ground = p.getLocation().add(dir.normalize().multiply(12));
            ground.setY(p.getWorld().getHighestBlockYAt(ground) + 1);
        } else {
            return "Summon the god's body first, or run this as a player.";
        }
        playDeath(variant, ground, h, Map.of());
        return "Playing the " + variant.id() + " death show (" + DeathShow.FRAMES / 10.0
                + " s). Nothing dies and nothing drops.";
    }

    @EventHandler
    public void onFireworkHit(EntityDamageByEntityEvent event) {
        if (showKey != null && event.getDamager() instanceof Firework fw
                && fw.getPersistentDataContainer().has(showKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    /** The hoard: godslayer.loot, flung out of where the god died. Items never burn or despawn. */
    static void dropLoot(Location at, String god, String by) {
        World w = at.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<ItemStack> stacks = new ArrayList<>();
        for (GodslayerLoot.Drop d : GodslayerLoot.parse(GodSettings.godslayerLoot()).drops()) {
            if (d.relic()) {
                stacks.add(relic(god, by));
                continue;
            }
            Material mat = Material.matchMaterial(d.item());
            if (mat == null || !mat.isItem() || mat.isAir()) {
                GPTGOD.LOGGER.warn("godslayer.loot: no item called " + d.item());
                continue;
            }
            int n = d.count(rnd.nextDouble());
            int stack = Math.max(1, mat.getMaxStackSize());
            while (n > 0) {
                int k = Math.min(stack, n);
                stacks.add(new ItemStack(mat, k));
                n -= k;
            }
        }
        for (ItemStack s : stacks) {
            Item item = w.dropItem(at, s);
            double a = rnd.nextDouble(Math.PI * 2);
            double speed = 0.25 + rnd.nextDouble(0.3);
            item.setVelocity(new Vector(Math.cos(a) * speed, 0.45 + rnd.nextDouble(0.35), Math.sin(a) * speed));
            item.setGlowing(true);
            item.setInvulnerable(true);
            item.setUnlimitedLifetime(true);
        }
    }

    /** The unique trophy: "Heart of <God>". */
    public static ItemStack relic(String god, String by) {
        ItemStack it = new ItemStack(Material.HEART_OF_THE_SEA);
        String g = MiniMessage.miniMessage().escapeTags(god);
        it.editMeta(meta -> {
            meta.displayName(MM.deserialize("<!italic><bold><gradient:#7b2cbf:#ff1f1f:#ffb000>Heart of " + g
                    + "</gradient></bold>"));
            meta.lore(List.of(
                    MM.deserialize("<!italic><gray>Torn from a god as it fell."),
                    MM.deserialize("<!italic><dark_gray>Slain by " + MiniMessage.miniMessage().escapeTags(by) + ","),
                    MM.deserialize("<!italic><dark_gray>" + LocalDate.now()),
                    Component.empty(),
                    MM.deserialize("<!italic><dark_red><italic>It still beats.")));
            meta.setEnchantmentGlintOverride(true);
            meta.setRarity(ItemRarity.EPIC);
            meta.getPersistentDataContainer().set(relicKey, PersistentDataType.STRING, god);
        });
        return it;
    }

    // ---- Return --------------------------------------------------------------------------------

    /**
     * Brings a slain god back (by = the operator, or null when time restored it). Returns what to tell
     * the operator.
     */
    public static String revive(Realm r, String by) {
        Mortality m = r.memory().mortality;
        String god = r.displayName();
        if (!m.isSlain()) return god + " is not dead.";
        long now = System.currentTimeMillis();
        String slayer = m.slainBy;
        String when = Memory.age(now - m.slainAt);
        m.revive(now);
        r.memory().save();
        r.run(() -> {
            Journal.add(Journal.Kind.INFO, god + " returned from death" + (by == null ? "" : " (revived by " + by
                    + ")"));
            EventLogger.addLoggable(GodslayerLoggable.returned(slayer, when, by));
        });
        for (Realm peer : r.peers()) {
            peer.run(() -> EventLogger.addLoggable(GodslayerLoggable.godReturned(god)));
        }
        GPTGOD.LOGGER.info(god + " returned from death");
        Title title = Title.title(
                MM.deserialize("<bold><gradient:#7b2cbf:#c77dff:#ffffff>" + MiniMessage.miniMessage().escapeTags(god)
                        + " has returned</gradient></bold>"),
                Component.text("Death could not hold it", NamedTextColor.GRAY));
        for (Player p : r.players()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, SoundCategory.MASTER, 0.8f, 0.6f);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.MASTER, 0.5f, 0.7f);
        }
        TabList.refreshSoon();
        return god + " returns from death.";
    }

    // ---- Timers --------------------------------------------------------------------------------

    /** Every second: wounds close, health bars follow, the dead come back when their time is up. */
    private static void tick() {
        long now = System.currentTimeMillis();
        for (Realm r : Realm.all()) {
            Mortality m = r.memory().mortality;
            if (m.isSlain()) {
                hideBar(r);
                if (m.returnDue(now, GodSettings.godslayerGodReturns(), GodSettings.godslayerReturnMinutes())) {
                    revive(r, null);
                }
                continue;
            }
            m.heal(now, GodSettings.godslayerHealDelaySeconds() * 1000L, GodSettings.godslayerHealPerMinute());
            updateBar(r, now);
        }
    }

    private static void updateBar(Realm r, long now) {
        Mortality m = r.memory().mortality;
        Vessel v = r.vesselIfCreated();
        Location at = v == null ? null : v.location();
        boolean show = m.hurt() && at != null && !m.isSlain();
        Set<UUID> viewers = VIEWERS.computeIfAbsent(r.id, k -> new HashSet<>());
        if (!show && viewers.isEmpty()) return;
        double max = GodSettings.godslayerHealth();
        BossBar bar = BARS.computeIfAbsent(r.id, k -> BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED,
                BossBar.Overlay.NOTCHED_10));
        if (show) {
            double hp = m.health(max);
            bar.progress((float) Math.max(0, Math.min(1, hp / max)));
            bar.name(Component.text("☠ ", BLOOD)
                    .append(Component.text(r.displayName(), EMBER).decoration(TextDecoration.BOLD, true))
                    .append(Component.text("  " + Mortality.fmt(hp) + " / " + Mortality.fmt(max) + " ☠", BLOOD)));
        }
        double radius = GodSettings.godslayerBarRadius();
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean near = show && p.getWorld().equals(at.getWorld())
                    && p.getLocation().distanceSquared(at) <= radius * radius;
            if (near && viewers.add(p.getUniqueId())) p.showBossBar(bar);
            else if (!near && viewers.remove(p.getUniqueId())) p.hideBossBar(bar);
        }
    }

    private static void hideBar(Realm r) {
        Set<UUID> viewers = VIEWERS.get(r.id);
        BossBar bar = BARS.get(r.id);
        if (viewers == null || bar == null || viewers.isEmpty()) return;
        for (UUID id : viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(bar);
        }
        viewers.clear();
    }

    /** Every 2 ticks: soul fire and smoke trail from every held blade; now and then it hums. */
    private static void auraTick() {
        auraTicks++;
        boolean effects = GodSettings.godslayerHeldEffects();
        long now = System.currentTimeMillis();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack main = p.getInventory().getItemInMainHand();
            ItemStack off = p.getInventory().getItemInOffHand();
            boolean inMain = isBlade(main);
            boolean inOff = !inMain && isBlade(off);
            if (!inMain && !inOff) continue;
            if (auraTicks % 10 == 0) refreshModel(p, inMain ? main : off);
            if (!effects || p.getGameMode() == GameMode.SPECTATOR || p.isInvisible()) continue;
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
            dir.normalize();
            Vector right = new Vector(-dir.getZ(), 0, dir.getX());
            double side = inMain ? 1 : -1;
            Location hand = p.getLocation().add(0, p.isSneaking() ? 0.75 : 0.95, 0)
                    .add(right.clone().multiply(0.4 * side)).add(dir.clone().multiply(0.3));
            Vector blade = dir.clone().multiply(0.45).add(new Vector(0, 0.8, 0));
            World w = p.getWorld();
            if (isAscended(inMain ? main : off)) {
                // The ascended blade burns white-gold and sheds light instead of smoke.
                for (int i = 0; i < 2; i++) {
                    Location pt = hand.clone().add(blade.clone().multiply(rnd.nextDouble()));
                    w.spawnParticle(Particle.DUST, pt, 1, 0.03, 0.03, 0.03, 0,
                            new Particle.DustOptions(Color.fromRGB(i == 0 ? 0xFFD23A : 0xFFF4C8), 0.8f));
                }
                if (auraTicks % 2 == 0) {
                    w.spawnParticle(Particle.END_ROD, hand.clone().add(blade.clone().multiply(rnd.nextDouble())), 1,
                            0.02, 0.02, 0.02, 0.01);
                }
                if (auraTicks % 5 == 0) {
                    w.spawnParticle(Particle.ELECTRIC_SPARK, hand.clone().add(blade), 2, 0.05, 0.05, 0.05, 0.05);
                }
                Long next = NEXT_HUM.get(p.getUniqueId());
                if (next == null || now >= next) {
                    if (next != null) {
                        w.playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 1f, 1.4f);
                        w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS,
                                0.6f, 0.6f);
                    }
                    NEXT_HUM.put(p.getUniqueId(), now + 5000 + rnd.nextLong(7000));
                }
                continue;
            }
            for (int i = 0; i < 2; i++) {
                Location pt = hand.clone().add(blade.clone().multiply(rnd.nextDouble()));
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, pt, 1, 0.02, 0.02, 0.02, 0.004);
            }
            if (auraTicks % 3 == 0) {
                w.spawnParticle(Particle.SMOKE, hand.clone().add(blade.clone().multiply(rnd.nextDouble())), 1, 0.03,
                        0.03, 0.03, 0.01);
                w.spawnParticle(Particle.DUST, hand.clone().add(blade.clone().multiply(rnd.nextDouble())), 1, 0.03,
                        0.03, 0.03, 0, new Particle.DustOptions(Color.fromRGB(0x8A0000), 0.7f));
            }
            if (auraTicks % 15 == 0) {
                w.spawnParticle(Particle.SCULK_SOUL, hand.clone().add(blade), 1, 0.05, 0.05, 0.05, 0.02);
            }
            Long next = NEXT_HUM.get(p.getUniqueId());
            if (next == null || now >= next) {
                if (next != null) hum(p, rnd);
                NEXT_HUM.put(p.getUniqueId(), now + 6000 + rnd.nextLong(9000));
            }
        }
    }

    private static void hum(Player p, ThreadLocalRandom rnd) {
        Location at = p.getLocation();
        switch (rnd.nextInt(4)) {
            case 0 -> p.getWorld().playSound(at, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.9f, 0.5f);
            case 1 -> p.getWorld().playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 0.8f, 0.6f);
            case 2 -> p.getWorld().playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, SoundCategory.PLAYERS, 0.8f,
                    0.5f);
            default -> p.getWorld().playSound(at, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.PLAYERS, 0.12f,
                    0.5f);
        }
    }

    // ---- What the god sees ---------------------------------------------------------------------

    /** Its health, death record and the blade-bearers among the mortals of its worlds. Server thread. */
    public static String perceive(Realm r) {
        if (!GodSettings.godslayerEnabled()) return "Nothing in this world can wound you.";
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder(r.memory().mortality.describe(GodSettings.godslayerHealth(), now));
        Vessel v = r.vesselIfCreated();
        Location at = v == null ? null : v.location();
        List<String> bearers = new ArrayList<>();
        for (Player p : r.players()) {
            boolean holding = isBlade(p.getInventory().getItemInMainHand())
                    || isBlade(p.getInventory().getItemInOffHand());
            boolean carrying = holding;
            if (!carrying) {
                for (ItemStack it : p.getInventory().getContents()) {
                    if (isBlade(it)) {
                        carrying = true;
                        break;
                    }
                }
            }
            if (!carrying) continue;
            String where = "";
            if (at != null && p.getWorld().equals(at.getWorld())) {
                where = ", " + (int) Math.round(p.getLocation().distance(at)) + " blocks from your vessel";
            }
            boolean ascended = false;
            for (ItemStack it : p.getInventory().getContents()) {
                if (isAscended(it)) ascended = true;
            }
            String which = ascended && GodSettings.godslayerAscendedInstantKill()
                    ? "the ASCENDED Godslayer: one blow kills you" : "a Godslayer";
            bearers.add(p.getName() + " (" + (holding ? "HOLDING " : "carrying ") + which + where + ")");
        }
        if (bearers.isEmpty()) {
            sb.append("\nNo mortal here carries a Godslayer.");
        } else {
            sb.append("\nGODSLAYER BEARERS: ").append(String.join(", ", bearers))
                    .append(". Their blade can wound and kill you.");
        }
        return sb.toString();
    }

    // ---- Events --------------------------------------------------------------------------------

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        if (!(event.getRecipe() instanceof Keyed k) || !k.getKey().equals(recipeKey)) return;
        event.getInventory().setResult(GodSettings.godslayerCraftable() ? blade() : null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (GodSettings.godslayerCraftable() && recipeKey != null) event.getPlayer().discoverRecipe(recipeKey);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        LAST_BLOW.remove(id);
        NEXT_HUM.remove(id);
        for (Set<UUID> v : VIEWERS.values()) v.remove(id);
    }
}
