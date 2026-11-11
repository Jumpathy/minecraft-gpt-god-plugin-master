package net.bigyous.gptgodmc;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.InventoryBlockStartEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.Perception;
import net.bigyous.gptgodmc.god.QuestBoard;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.loggables.AchievementLoggable;
import net.bigyous.gptgodmc.loggables.AttackLoggable;
import net.bigyous.gptgodmc.loggables.BlockBreakLoggable;
import net.bigyous.gptgodmc.loggables.ChatLoggable;
import net.bigyous.gptgodmc.loggables.CombustLoggable;
import net.bigyous.gptgodmc.loggables.CraftLoggable;
import net.bigyous.gptgodmc.loggables.DamageLoggable;
import net.bigyous.gptgodmc.loggables.DeathLoggable;
import net.bigyous.gptgodmc.loggables.DropItemLoggable;
import net.bigyous.gptgodmc.loggables.EatingLoggable;
import net.bigyous.gptgodmc.loggables.EntityLoveLoggable;
import net.bigyous.gptgodmc.loggables.ExplosionLoggable;
import net.bigyous.gptgodmc.loggables.FishingLoggable;
import net.bigyous.gptgodmc.loggables.GenericEventLoggable;
import net.bigyous.gptgodmc.loggables.ItemPickupLoggable;
import net.bigyous.gptgodmc.loggables.KillLoggable;
import net.bigyous.gptgodmc.loggables.MountLoggable;
import net.bigyous.gptgodmc.loggables.PlantLoggable;
import net.bigyous.gptgodmc.loggables.RenameEntityEvent;
import net.bigyous.gptgodmc.loggables.RenameItemLoggable;
import net.bigyous.gptgodmc.loggables.ShootLoggable;
import net.bigyous.gptgodmc.loggables.SleepTogetherLoggable;
import net.bigyous.gptgodmc.loggables.SmeltLoggable;
import net.bigyous.gptgodmc.loggables.SpecialBlockPlaceEventLoggable;
import net.bigyous.gptgodmc.loggables.TameAnimalLoggable;
import net.bigyous.gptgodmc.loggables.UseLoggable;
import net.bigyous.gptgodmc.loggables.WriteOnSignLoggable;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Turns what mortals do into events the god will perceive. */
public class LoggableEventHandler implements Listener {

    // Every event goes to the god who rules the world it happened in. Worlds no god rules log nothing.

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        Player player = event.getPlayer();
        // Every god of the world hears it; each decides for itself whether it was spoken to.
        for (Realm realm : Realm.godsOf(player.getWorld())) {
            realm.run(() -> EventLogger.log(() -> new ChatLoggable(player.getName(), text)));
            // Chat arrives off the server thread; the god's instant "I heard you" has to happen on it.
            if (realm.get(() -> ChatLoggable.addressesGod(player.getName(), text)) && GPTGOD.get().isEnabled()) {
                Bukkit.getScheduler().runTask(GPTGOD.get(), () -> God.acknowledge(realm, player));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        String name = event.getPlayer().getName();
        EventLogger.log(event.getPlayer().getWorld(),
                () -> new GenericEventLoggable(name + " entered the world", name + " arrived"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        EventLogger.log(event.getPlayer().getWorld(),
                () -> new GenericEventLoggable(event.getPlayer().getName() + " left the world"));
    }

    /**
     * A portal within one god's realm (overworld → Nether → End) is just travel: the god keeps
     * watching. Stepping into a world another god rules, or none, is leaving: the old god loses
     * sight of the mortal and the new one sees an arrival.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();
        World to = player.getWorld();
        Realm left = Realm.of(event.getFrom());
        Realm entered = Realm.of(to);
        if (left == entered) {
            EventLogger.log(to, () -> new GenericEventLoggable(name + " traveled to " + Perception.dimension(to)
                    + " (" + to.getName() + ")"));
            return;
        }
        EventLogger.log(event.getFrom(), () -> new GenericEventLoggable(name + " left your realm for another world ("
                + to.getName() + "), beyond your sight and power"));
        EventLogger.log(to, () -> new GenericEventLoggable(name + " crossed into your realm from another world ("
                + event.getFrom().getName() + ")", name + " arrived in your realm"));
        // The quest sidebar follows the mortal to their new god (or goes away in a godless world).
        QuestBoard.show(player);
        God.refreshPresence();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        EventLogger.log(event.getBlock().getWorld(), () -> new BlockBreakLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void pickupItem(PlayerAttemptPickupItemEvent event) {
        EventLogger.log(event.getPlayer().getWorld(), () -> new ItemPickupLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttackEntity(EntityDamageByEntityEvent event) {
        EventLogger.log(event.getEntity().getWorld(), () -> new AttackLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        EventLogger.log(event.getEntity().getWorld(), () -> new DamageLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        EventLogger.log(event.getPlayer().getWorld(), () -> new DropItemLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        EventLogger.log(event.getPlayer().getWorld(), () -> new EatingLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        EventLogger.log(event.getEntity().getWorld(), () -> new DeathLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemFished(PlayerFishEvent event) {
        EventLogger.log(event.getPlayer().getWorld(), () -> new FishingLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlaced(BlockPlaceEvent event) {
        EventLogger.log(event.getBlock().getWorld(), () -> new SpecialBlockPlaceEventLoggable(event));
        EventLogger.log(event.getBlock().getWorld(), () -> new PlantLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        EventLogger.log(event.getBlock().getWorld(), () -> new WriteOnSignLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosion(BlockExplodeEvent event) {
        EventLogger.log(event.getBlock().getWorld(), () -> new ExplosionLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        EventLogger.log(event.getLocation().getWorld(), () -> new ExplosionLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSleep(PlayerBedEnterEvent event) {
        EventLogger.log(event.getPlayer().getWorld(), () -> new SleepTogetherLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUse(PlayerInteractEvent event) {
        EventLogger.log(event.getPlayer().getWorld(), () -> new UseLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        EventLogger.log(event.getEntity().getWorld(), () -> new CombustLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        EventLogger.log(event.getEntity().getWorld(), () -> new MountLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        EventLogger.log(event.getWhoClicked().getWorld(), () -> new CraftLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAchievement(PlayerAdvancementDoneEvent event) {
        EventLogger.log(event.getPlayer().getWorld(), () -> new AchievementLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemNamed(InventoryClickEvent event) {
        EventLogger.log(event.getWhoClicked().getWorld(), () -> new RenameItemLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRename(PlayerInteractEntityEvent event) {
        EventLogger.log(event.getPlayer().getWorld(), () -> new RenameEntityEvent(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        EventLogger.log(event.getEntity().getWorld(), () -> new ShootLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        EventLogger.log(event.getEntity().getWorld(), () -> new TameAnimalLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        EventLogger.log(event.getEntity().getWorld(), () -> new KillLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCook(InventoryBlockStartEvent event) {
        EventLogger.log(event.getBlock().getWorld(), () -> new SmeltLoggable(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityEnterLoveModeEvent event) {
        EventLogger.log(event.getEntity().getWorld(), () -> new EntityLoveLoggable(event));
    }
}
