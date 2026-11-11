package net.bigyous.gptgodmc.god.living;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

/** Holy-day scoring from what mortals do, and the servants' bodies (deaths, blows, touches, strays). */
public final class LivingListener implements Listener {
    private final Map<UUID, Long> lastPrayer = new HashMap<>();

    // ---- Deaths -------------------------------------------------------------------------------------

    /** Also receives player deaths (PlayerDeathEvent shares EntityDeathEvent's handlers). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        Entity dead = e.getEntity();
        Player killer = e.getEntity().getKiller();
        Servants.Servant s = Servants.of(dead);
        if (s != null) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            Entity by = killer;
            if (by == null && dead.getLastDamageCause() instanceof EntityDamageByEntityEvent d) by = d.getDamager();
            s.slain(by);
            return;
        }
        if (HolyDayRunner.tagged(dead)) HolyDayRunner.mobDied(dead, killer);
        if (dead instanceof Player p) {
            HolyDayRunner.died(p, killer);
            return;
        }
        if (killer == null) return;
        if (dead instanceof Monster) HolyDayRunner.credit(killer, "hostile_kills");
        else if (dead instanceof Animals) HolyDayRunner.credit(killer, "animal_kills");
    }

    // ---- Deeds ---------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (counts(e.getPlayer())) HolyDayRunner.credit(e.getPlayer(), "blocks_placed");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!counts(e.getPlayer())) return;
        HolyDayRunner.credit(e.getPlayer(), "blocks_mined");
        if (WorldMoodRunner.CROPS.contains(e.getBlock().getType()) && e.getBlock().getBlockData() instanceof Ageable a
                && a.getAge() >= a.getMaximumAge()) {
            HolyDayRunner.credit(e.getPlayer(), "crops_harvested");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) HolyDayRunner.credit(e.getPlayer(), "fish_caught");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (e.getBreeder() instanceof Player p) HolyDayRunner.credit(p, "animals_bred");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String m = e.getMessage().toLowerCase(java.util.Locale.ROOT);
        String act = m.startsWith("/pray ") ? "prayers" : m.startsWith("/offer") ? "offerings" : null;
        if (act == null) return;
        long now = System.currentTimeMillis();
        Long last = lastPrayer.get(e.getPlayer().getUniqueId());
        if (act.equals("prayers") && last != null && now - last < 30_000) return;
        if (act.equals("prayers")) lastPrayer.put(e.getPlayer().getUniqueId(), now);
        HolyDayRunner.credit(e.getPlayer(), act);
    }

    private static boolean counts(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    // ---- Servants ------------------------------------------------------------------------------------

    /**
     * Players with the resource pack do not see a servant's mob, only its painted body: a blow on that
     * body's hitbox is a real attack on the mob (weapon, charge, enchantments and knockback as usual).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStrikeBody(PrePlayerAttackEntityEvent e) {
        Servants.Servant s = Servants.ofHitbox(e.getAttacked());
        if (s == null) return;
        e.setCancelled(true);
        if (s.mob.isValid() && !s.mob.isDead()) e.getPlayer().attack(s.mob);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity attacker = e.getDamager();
        if (attacker instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) attacker = shooter;
        Servants.Servant victim = Servants.of(e.getEntity());
        Servants.Servant striker = Servants.of(attacker);
        // A god's servants never harm one another.
        if (victim != null && striker != null && victim.god == striker.god) {
            e.setCancelled(true);
            return;
        }
        if (victim != null) victim.hurt();
        if (victim != null && attacker instanceof Player p && victim.kind.damage > 0
                && victim.order != ServantKind.Order.ATTACK && LivingSettings.servantsAttackPlayers()
                && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)) {
            // Struck, it answers the blow.
            try {
                victim.order(ServantKind.Order.ATTACK, p, null, null);
                victim.orderUntil = System.currentTimeMillis() + 20_000;
            } catch (IllegalStateException ignored) {
                // it does not fight
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTouch(PlayerInteractEntityEvent e) {
        Servants.Servant s = Servants.of(e.getRightClicked());
        if (s == null) return;
        e.setCancelled(true); // no trading with priests, no handing items to angels
        if (s.kind == ServantKind.PRIEST) s.lastPreach = 0;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) {
        if (Servants.of(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (Servants.isServantEntity(en) && Servants.of(en) == null && en.getVehicle() == null) en.remove();
            else if (HolyDayRunner.tagged(en) && !HolyDayRunner.tracked(en)) en.remove();
        }
    }
}
