package net.bigyous.gptgodmc.god;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import net.bigyous.gptgodmc.GPTGOD;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;

/**
 * What being a demigod does: a hero's strength, toughness, speed and leap, ten more hearts, flight,
 * no fall damage (a hard landing is a shockwave), golden wings that beat in flight and a halo.
 * Kept up while they live, given back after death and on joining. Server thread only.
 */
public final class DemigodPowers implements Listener {
    private static Demigods state;
    private static BukkitTask task;
    private static long tickNo;

    private static final PotionEffectType[] BOONS = { PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE,
            PotionEffectType.SPEED, PotionEffectType.JUMP_BOOST, PotionEffectType.REGENERATION,
            PotionEffectType.FIRE_RESISTANCE };
    private static final int[] LEVELS = { 1, 0, 0, 1, 0, 0 };

    private static Path file() {
        return GPTGOD.get().getDataFolder().toPath().resolve("demigods.json");
    }

    public static synchronized Demigods state() {
        if (state == null) state = Demigods.load(file());
        return state;
    }

    public static synchronized void save() {
        state().save(file());
    }

    private static NamespacedKey key() {
        return new NamespacedKey(GPTGOD.get(), "demigod");
    }

    public static void start() {
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(GPTGOD.get(), DemigodPowers::tick, 20, 4);
        DemigodRig.start();
    }

    /** Gives (or renews) the powers. */
    public static void bless(Player p) {
        for (int i = 0; i < BOONS.length; i++) {
            PotionEffect now = p.getPotionEffect(BOONS[i]);
            if (now == null || now.getAmplifier() < LEVELS[i] || !now.isInfinite()) {
                p.addPotionEffect(new PotionEffect(BOONS[i], PotionEffect.INFINITE_DURATION, LEVELS[i], true, false,
                        true));
            }
        }
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null && hp.getModifier(key()) == null) {
            hp.addModifier(new AttributeModifier(key(), 20, AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ANY));
        }
        if (GodSettings.apotheosisFlight() && !p.getAllowFlight()) p.setAllowFlight(true);
    }

    /** Takes the powers away. */
    public static void unbless(Player p) {
        DemigodRig.remove(p);
        for (PotionEffectType t : BOONS) {
            PotionEffect now = p.getPotionEffect(t);
            if (now != null && now.isInfinite()) p.removePotionEffect(t);
        }
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null && hp.getModifier(key()) != null) hp.removeModifier(key());
        if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }
        if (p.getHealth() > p.getAttribute(Attribute.MAX_HEALTH).getValue()) {
            p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
    }

    /** The moment of apotheosis: light, thunder and a title for everyone. */
    public static void crown(Player p, String godName) {
        bless(p);
        if (!p.isDead()) p.setHealth(Math.min(p.getAttribute(Attribute.MAX_HEALTH).getValue(), p.getHealth() + 20));
        Location at = p.getLocation();
        at.getWorld().strikeLightningEffect(at);
        at.getWorld().playSound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2f, 0.8f);
        at.getWorld().playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 2f, 0.6f);
        at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0, 1, 0), 200, 0.6, 1.2, 0.6, 0.6);
        at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 120, 0.3, 2.5, 0.3, 0.15);
        Title t = Title.title(Component.text(p.getName() + " ascends", TextColor.color(0xFFD86B)),
                Component.text("raised to godhood by " + godName, TextColor.color(0xFFF2C0)));
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.getWorld().equals(p.getWorld())) o.showTitle(t);
        }
    }

    private static boolean isDemigod(Player p, long now) {
        Demigods.Entry e = state().find(p.getName(), now);
        return e != null && (e.uuid == null || e.uuid.equals(p.getUniqueId().toString()));
    }

    private static void tick() {
        tickNo++;
        long now = System.currentTimeMillis();
        Demigods s = state();
        if (tickNo % 25 == 0) {
            for (Demigods.Entry gone : s.expire(now)) {
                save();
                Player p = Bukkit.getPlayerExact(gone.name);
                if (p != null) {
                    unbless(p);
                    p.sendMessage(Component.text("Your divinity fades. You are mortal again.", TextColor.color(0xAAAAAA)));
                }
            }
        }
        if (s.demigods.isEmpty()) return;
        for (Demigods.Entry d : List.copyOf(s.demigods)) {
            Player p = d.uuid == null ? Bukkit.getPlayerExact(d.name) : Bukkit.getPlayer(UUID.fromString(d.uuid));
            if (p == null || !p.isOnline() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            if (tickNo % 25 == 0) bless(p); // milk and deaths cannot wash it off
            draw(p);
        }
    }

    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(0xFFD86B), 0.9f);
    /** The wings and halo are display entities ({@link DemigodRig}); flight leaves a trail of light. */
    private static void draw(Player p) {
        if ((p.isFlying() || p.isGliding()) && tickNo % 3 == 0) {
            Location at = p.getLocation();
            at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, 1.0, 0), 1, 0.3, 0.2, 0.3, 0.01);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        if (isDemigod(p, now)) {
            Bukkit.getScheduler().runTaskLater(GPTGOD.get(), () -> bless(p), 20);
        } else {
            AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null && hp.getModifier(key()) != null) unbless(p); // unmade while away
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        DemigodRig.remove(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (isDemigod(p, System.currentTimeMillis())) Bukkit.getScheduler().runTaskLater(GPTGOD.get(), () -> bless(p), 2);
    }

    /** No fall can hurt a demigod; a hard landing shakes the ground and throws back what stands near. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        if (!isDemigod(p, System.currentTimeMillis())) return;
        e.setCancelled(true);
        if (p.getFallDistance() < 6) return;
        Location at = p.getLocation();
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
        at.getWorld().playSound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.5f, 0.8f);
        at.getWorld().spawnParticle(Particle.EXPLOSION, at, 3, 0.8, 0.1, 0.8, 0);
        for (int k = 0; k < 40; k++) {
            double a = k * Math.PI * 2 / 40;
            at.getWorld().spawnParticle(Particle.DUST, at.getX() + Math.cos(a) * 3, at.getY() + 0.2,
                    at.getZ() + Math.sin(a) * 3, 1, 0, 0, 0, 0, GOLD);
        }
        for (Entity other : at.getWorld().getNearbyEntities(at, 4, 2, 4)) {
            if (other == p || !(other instanceof LivingEntity) || other instanceof Player) continue;
            Vector push = other.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (push.lengthSquared() < 0.01) push = new Vector(1, 0, 0);
            other.setVelocity(push.normalize().multiply(1.2).setY(0.6));
        }
    }
}
