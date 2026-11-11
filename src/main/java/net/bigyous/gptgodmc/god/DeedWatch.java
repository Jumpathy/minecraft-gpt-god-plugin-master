package net.bigyous.gptgodmc.god;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Raid;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.raid.RaidStopEvent;
import org.bukkit.event.raid.RaidTriggerEvent;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.living.Servants;
import net.bigyous.gptgodmc.loggables.GenericEventLoggable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Turns what mortals do into {@link WorldDeed}s and hands each to every god of that world: its land
 * ledger ({@link Ecology}) and the oaths sworn before it ({@link Oaths}). Also wakes the gods for
 * villager killings, raids and slaughter, and every second checks "enter" watches and oath terms.
 */
public final class DeedWatch implements Listener {
    private static final Ecology.Spree SLAUGHTER = new Ecology.Spree(8, 120_000, 600_000);
    private static final Ecology.Spree FELLING = new Ecology.Spree(64, 600_000, 1_200_000);
    /** "realm:oath" for enter watches whose swearer stands inside the place now. */
    private static final Set<String> INSIDE = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> NOTICE_ONCE = new ConcurrentHashMap<>();

    // ---- Deeds --------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Material m = b.getType();
        Set<String> tags = blockTags(m);
        Location at = b.getLocation();
        emit(e.getPlayer(), "break_blocks", key(m), tags, at);
        if (Tag.LOGS.isTagged(m)) emit(e.getPlayer(), "fell_trees", key(m), tags, at);
        if (LandSense.crop(m) && LandSense.ripe(b)) emit(e.getPlayer(), "harvest", key(m), tags, at);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Material m = e.getBlock().getType();
        Set<String> tags = blockTags(m);
        Location at = e.getBlock().getLocation();
        emit(e.getPlayer(), "place_blocks", key(m), tags, at);
        if (tags.contains("sapling") || tags.contains("crop")) emit(e.getPlayer(), "plant", key(m), tags, at);
    }

    /** Also receives player deaths (PlayerDeathEvent shares EntityDeathEvent's handlers). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        Entity dead = e.getEntity();
        if (Servants.isServantEntity(dead)) return;
        if (dead instanceof Player p) emit(p, "die", "death", Set.of(), p.getLocation());
        Player killer = e.getEntity().getKiller();
        if (killer == null || killer.equals(dead)) return;
        if (dead instanceof Player p) {
            emit(killer, "kill_player", p.getName(), Set.of("player"), dead.getLocation());
            return;
        }
        Set<String> tags = new HashSet<>();
        if (dead instanceof Animals) tags.add("animal");
        if (dead instanceof Enemy) tags.add("monster");
        if (dead instanceof Villager) tags.add("villager");
        if (dead instanceof IronGolem) tags.add("golem");
        if (dead instanceof Tameable t && t.isTamed()) tags.add("pet");
        emit(killer, "kill_mob", dead.getType().getKey().getKey(), tags, dead.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (e.getBreeder() instanceof Player p) {
            emit(p, "breed", e.getEntity().getType().getKey().getKey(), Set.of("animal"), e.getEntity().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent e) {
        if (e.getOwner() instanceof Player p) {
            emit(p, "tame", e.getEntity().getType().getKey().getKey(), Set.of("animal", "pet"),
                    e.getEntity().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            emit(e.getPlayer(), "fish", "fish", Set.of(), e.getPlayer().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String m = e.getMessage().toLowerCase(Locale.ROOT);
        String act = m.startsWith("/pray ") ? "pray" : m.startsWith("/offer") ? "offer" : null;
        if (act != null) emit(e.getPlayer(), act, act, Set.of(), e.getPlayer().getLocation());
    }

    // ---- Villages under attack ----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRaid(RaidTriggerEvent e) {
        if (!GodSettings.ecologyAlerts()) return;
        Location l = e.getRaid().getLocation();
        String who = e.getPlayer() == null ? "Someone" : e.getPlayer().getName();
        log(e.getWorld(), who + " brought a raid down on the village near " + at(l) + " (they carried a bad omen).",
                "a raid has begun on a village");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRaidEnd(RaidStopEvent e) {
        if (!GodSettings.ecologyAlerts()) return;
        Raid r = e.getRaid();
        Location l = r.getLocation();
        if (r.getStatus() == Raid.RaidStatus.LOSS) {
            log(l.getWorld(), "The raiders overran the village near " + at(l) + ".", "a village fell to raiders");
        } else if (r.getStatus() == Raid.RaidStatus.VICTORY) {
            List<String> heroes = new java.util.ArrayList<>();
            for (java.util.UUID id : r.getHeroes()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) heroes.add(p.getName());
            }
            log(l.getWorld(), "The raid on the village near " + at(l) + " was beaten back"
                    + (heroes.isEmpty() ? "." : " by " + String.join(", ", heroes) + "."), null);
        }
    }

    // ---- Handing deeds to the gods ------------------------------------------------------------------

    private static void emit(Player p, String act, String kind, Set<String> tags, Location l) {
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return;
        if (l.getWorld() == null) return;
        try {
            handle(l.getWorld(), new WorldDeed(p.getName(), act, kind, tags, l.getWorld().getName(), l.getBlockX(),
                    l.getBlockY(), l.getBlockZ(), System.currentTimeMillis()));
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not weigh a deed", e);
        }
    }

    static void handle(World world, WorldDeed d) {
        boolean eco = GodSettings.ecologyEnabled(), oaths = GodSettings.oathsEnabled();
        if (!eco && !oaths) return;
        long hl = GodSettings.ecologyHalfLifeMs();
        for (Realm realm : Realm.godsOf(world)) {
            Memory m = realm.memory();
            if (eco) {
                synchronized (m) {
                    Ecology.record(m.land, d, hl);
                }
            }
            if (oaths) {
                List<Oaths.Notice> n = Oaths.observe(m, d, d.at());
                if (!n.isEmpty()) realm.run(() -> tell(realm, n));
            }
        }
        if (eco && GodSettings.ecologyAlerts()) alerts(world, d);
    }

    private static void alerts(World w, WorldDeed d) {
        String where = " near (" + d.x() + ", " + d.y() + ", " + d.z() + ")";
        if (d.act().equals("kill_mob") && d.is("villager")) {
            log(w, d.player() + " killed a villager" + where + ".", d.player() + " killed a villager");
        } else if (d.act().equals("kill_mob") && d.is("golem")) {
            log(w, d.player() + " slew an iron golem, a village's guardian," + where + ".",
                    d.player() + " slew a village's guardian");
        } else if (d.act().equals("kill_mob") && d.is("animal") && SLAUGHTER.hit(d.player(), d.at())) {
            Player p = Bukkit.getPlayerExact(d.player());
            String need = p == null ? null
                    : Senses.need(p.getFoodLevel(), p.getHealth(),
                            p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
            if (need != null) {
                // Killing to eat or to heal is how mortals live: told plainly, and it does not wake the god.
                log(w, d.player() + " killed 8 animals in two minutes" + where + " while " + need
                        + ": most likely for food.", null);
            } else {
                log(w, d.player() + " is slaughtering animals: 8 in two minutes" + where + ".",
                        d.player() + " is slaughtering animals");
            }
        } else if (d.act().equals("fell_trees") && FELLING.hit(d.player(), d.at())) {
            log(w, d.player() + " is cutting down the woods: 64 logs in ten minutes" + where + ".", null);
        }
    }

    private static void log(World w, String text, String attention) {
        EventLogger.log(w, () -> new GenericEventLoggable(text, attention));
    }

    /** Tells the god (the realm bound now) what came of its oaths, and the swearer what concerns them. */
    static void tell(Realm realm, List<Oaths.Notice> notices) {
        realm.memory().save();
        String god = realm.displayName();
        for (Oaths.Notice n : notices) {
            Oaths.Oath o = n.oath();
            Player p = Bukkit.getPlayerExact(o.swearer);
            switch (n.kind()) {
                case BREACH -> {
                    EventLogger.addLoggable(new GenericEventLoggable(n.text(), o.swearer + " broke an oath"));
                    if (p != null) {
                        p.sendMessage(Component.text("You have broken the oath you swore before " + god + ".",
                                NamedTextColor.DARK_RED));
                        p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 1f, 0.5f);
                    }
                }
                case FULFILLED -> {
                    EventLogger.addLoggable(new GenericEventLoggable(n.text(), o.swearer + " fulfilled an oath"));
                    if (p != null) {
                        p.sendMessage(Component.text("Your oath is fulfilled. " + god + " will judge it.", Speech.VIOLET));
                        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
                    }
                }
                case TERM_ENDED -> EventLogger.addLoggable(new GenericEventLoggable(n.text(),
                        o.swearer + "'s oath has run its term"));
                case UNHEARD -> {
                    EventLogger.addLoggable(new GenericEventLoggable(n.text()));
                    if (p != null) {
                        p.sendMessage(Component.text("Your oath " + o.id + " went unanswered. " + god
                                + " did not take it up.", Speech.DEEP_VIOLET));
                    }
                }
                case CLOSED_BY_TIME -> EventLogger.addLoggable(new GenericEventLoggable(n.text()));
            }
        }
    }

    // ---- Every second: places entered, terms ended ----------------------------------------------------

    public static void start() {
        long[] ticks = { 0 };
        Bukkit.getScheduler().runTaskTimer(GPTGOD.get(), () -> {
            if (!GodSettings.oathsEnabled()) return;
            long now = System.currentTimeMillis();
            boolean terms = ticks[0]++ % 5 == 0;
            for (Realm realm : Realm.all()) {
                try {
                    Memory m = realm.memory();
                    for (Oaths.Oath o : Oaths.enterWatches(m)) watchEntry(realm, o, now);
                    if (terms) {
                        List<Oaths.Notice> n = Oaths.tick(m, now, GodSettings.oathsAnswerMs());
                        if (!n.isEmpty()) realm.run(() -> tell(realm, n));
                    }
                } catch (RuntimeException e) {
                    if (NOTICE_ONCE.putIfAbsent(realm.id, now) == null) GPTGOD.LOGGER.warn("Oath watch failed", e);
                }
            }
        }, 40L, 20L);
    }

    /** A deed only on stepping in, so standing inside a forbidden place is one breach, not one a second. */
    private static void watchEntry(Realm realm, Oaths.Oath o, long now) {
        String key = realm.id + ":" + o.id;
        Player p = Bukkit.getPlayerExact(o.swearer);
        Oaths.Clause c = o.watch;
        boolean inside = p != null && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR
                && p.getWorld().getName().equalsIgnoreCase(c.world)
                && square(p.getLocation().getBlockX() - c.x) + square(p.getLocation().getBlockZ() - c.z)
                        <= square(c.radius);
        if (!inside) {
            INSIDE.remove(key);
            return;
        }
        if (!INSIDE.add(key)) return;
        Location l = p.getLocation();
        WorldDeed d = new WorldDeed(p.getName(), "enter", "place", Set.of(), l.getWorld().getName(), l.getBlockX(),
                l.getBlockY(), l.getBlockZ(), now);
        List<Oaths.Notice> n = Oaths.observe(realm.memory(), d, now);
        if (!n.isEmpty()) realm.run(() -> tell(realm, n));
    }

    private static long square(long v) {
        return v * v;
    }

    // ---- Words --------------------------------------------------------------------------------------

    private static String key(Material m) {
        return m.getKey().getKey();
    }

    private static Set<String> blockTags(Material m) {
        Set<String> tags = new LinkedHashSet<>();
        if (Tag.LOGS.isTagged(m)) tags.add("log");
        if (LandSense.crop(m)) tags.add("crop");
        if (LandSense.sapling(m)) tags.add("sapling");
        if (m.getKey().getKey().endsWith("_ore")) tags.add("ore");
        return tags;
    }

    private static String at(Location l) {
        return "(" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + ")";
    }
}
