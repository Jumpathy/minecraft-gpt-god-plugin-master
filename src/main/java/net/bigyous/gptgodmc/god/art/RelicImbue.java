package net.bigyous.gptgodmc.god.art;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.client.ModRelics;
import net.bigyous.gptgodmc.god.FxLibrary;
import net.bigyous.gptgodmc.god.FxShow;
import net.bigyous.gptgodmc.god.FxStyle;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.art.RelicPowers.Effect;
import net.bigyous.gptgodmc.god.art.RelicPowers.Power;
import net.bigyous.gptgodmc.god.art.RelicPowers.Stat;
import net.bigyous.gptgodmc.god.art.RelicPowers.Trigger;
import net.bigyous.gptgodmc.god.art.Relics.Relic;
import net.bigyous.gptgodmc.god.art.SpritePainter.Art;
import net.bigyous.gptgodmc.god.vessel.VesselPackHost;
import net.kyori.adventure.text.Component;

/**
 * What an imbued relic does in the world. {@link RelicPowers} is the language and the reckoning; this is
 * the hand that carries it out: the properties a relic lends whoever holds it, what happens when its
 * bearer strikes, swings, uses it, kills or is hurt, what it does while merely held (light carried through
 * the dark, an aura, a hum), waking and sleeping a relic with two forms, and the attacks, each made once
 * with its own look (a sweeping arc, a crescent of light that flies, a beam, a ground shockwave, a whirl,
 * a blade thrown that returns).
 *
 * <p>Nothing here changes the world's blocks: light is shown to nearby players only, bursts and bolts
 * break and burn nothing. Everything runs on the server thread.
 */
public final class RelicImbue implements Listener {
    /** Per player: when each power of each relic may next be used ("id#index" -> epoch ms). */
    private static final Map<UUID, Map<String, Long>> READY = new HashMap<>();
    /** Players a relic has thrown or dropped from a height: their next fall does not hurt, until this time. */
    private static final Map<UUID, Long> SOFT = new HashMap<>();
    /** Per player: where the light it carries is now shown. */
    private static final Map<UUID, Spot> LIGHTS = new HashMap<>();
    /** Relics in flight (thrown), to be taken out of the world if the plugin stops. */
    private static final Set<Entity> FLYING = new HashSet<>();
    private static final Map<String, Integer> GLOW = new ConcurrentHashMap<>();
    private static final EquipmentSlot[] HANDS = { EquipmentSlot.HAND, EquipmentSlot.OFF_HAND };
    /** True while a relic's own power is hurting something, so that hurt does not set its powers off again. */
    private static boolean striking;
    private static int beat;

    private record Spot(World world, int x, int y, int z, int level) {
        Location at() {
            return new Location(world, x, y, z);
        }
    }

    /** A relic in a hand. */
    private record Held(Relic relic, ItemStack stack, boolean awake, EquipmentSlot slot) {
    }

    // ---- Properties -------------------------------------------------------------------------------------

    private static final Attribute[] MANAGED = { Attribute.ATTACK_DAMAGE, Attribute.ATTACK_SPEED, Attribute.MOVEMENT_SPEED,
            Attribute.ARMOR, Attribute.ARMOR_TOUGHNESS, Attribute.MAX_HEALTH, Attribute.ENTITY_INTERACTION_RANGE,
            Attribute.BLOCK_INTERACTION_RANGE, Attribute.KNOCKBACK_RESISTANCE, Attribute.LUCK };

    /**
     * Writes what the relic lends its bearer onto an item: its properties in the form it is in (a blade
     * always has a sword's damage and speed, its own or iron's), and the group its cooldowns are shown by.
     */
    static void dress(ItemMeta meta, Relic r, boolean awake) {
        for (Attribute a : MANAGED) meta.removeAttributeModifier(a);
        Map<Stat, Double> want = new EnumMap<>(Stat.class);
        for (Power p : RelicPowers.of(r)) {
            if (p.trigger() == Trigger.STAT && fits(p, awake)) want.put(p.stat(), p.amount());
        }
        boolean blade = "blade".equals(r.kind());
        if (blade || want.containsKey(Stat.ATTACK_DAMAGE) || want.containsKey(Stat.ATTACK_SPEED)) {
            // A player's bare hand strikes for 1, four times a second; an item's modifiers are added to that.
            want.putIfAbsent(Stat.ATTACK_DAMAGE, blade ? 6.0 : 1.0);
            want.putIfAbsent(Stat.ATTACK_SPEED, 1.6);
        }
        want.forEach((stat, v) -> {
            switch (stat) {
                // The two ids the game itself uses, so the tooltip reads like any sword's.
                case ATTACK_DAMAGE -> meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                        NamespacedKey.minecraft("base_attack_damage"), v - 1, AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND));
                case ATTACK_SPEED -> meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                        NamespacedKey.minecraft("base_attack_speed"), v - 4, AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND));
                case SPEED -> add(meta, Attribute.MOVEMENT_SPEED, stat, v / 100, AttributeModifier.Operation.ADD_SCALAR);
                case ARMOR -> add(meta, Attribute.ARMOR, stat, v, AttributeModifier.Operation.ADD_NUMBER);
                case TOUGHNESS -> add(meta, Attribute.ARMOR_TOUGHNESS, stat, v, AttributeModifier.Operation.ADD_NUMBER);
                case MAX_HEALTH -> add(meta, Attribute.MAX_HEALTH, stat, v, AttributeModifier.Operation.ADD_NUMBER);
                case REACH -> {
                    add(meta, Attribute.ENTITY_INTERACTION_RANGE, stat, v, AttributeModifier.Operation.ADD_NUMBER);
                    add(meta, Attribute.BLOCK_INTERACTION_RANGE, stat, v, AttributeModifier.Operation.ADD_NUMBER);
                }
                case KNOCKBACK_RESIST -> add(meta, Attribute.KNOCKBACK_RESISTANCE, stat, v / 100,
                        AttributeModifier.Operation.ADD_NUMBER);
                case LUCK -> add(meta, Attribute.LUCK, stat, v, AttributeModifier.Operation.ADD_NUMBER);
            }
        });
        // Its own cooldown group: the game then draws the cooldown sweep over this relic alone.
        try {
            UseCooldownComponent cool = meta.getUseCooldown();
            cool.setCooldownGroup(group(r));
            cool.setCooldownSeconds(1f);
            meta.setUseCooldown(cool);
        } catch (RuntimeException e) {
            // a server without the component: cooldowns still hold, they are only not drawn
        }
    }

    private static void add(ItemMeta meta, Attribute attribute, Stat stat, double amount, AttributeModifier.Operation op) {
        if (amount == 0) return;
        meta.addAttributeModifier(attribute, new AttributeModifier(new NamespacedKey(GPTGOD.get(), "relic_" + stat.id()
                + (attribute == Attribute.BLOCK_INTERACTION_RANGE ? "_block" : "")), amount, op, EquipmentSlotGroup.HAND));
    }

    private static NamespacedKey group(Relic r) {
        return new NamespacedKey(GPTGOD.get(), "relic/" + r.id());
    }

    private static boolean fits(Power p, boolean awake) {
        return p.when().isEmpty() || p.when().equals(awake ? "on" : "off");
    }

    // ---- Checking what only the server knows ------------------------------------------------------------

    /** The god whose library a relic's fx and scripts come from: its maker, or for the examples the first god. */
    public static Realm owner(Relic r) {
        Realm realm = Realm.byId(r.god());
        return realm != null ? realm : Realm.primary();
    }

    /** What is wrong with powers that read correctly but name things this server does not have, or null. */
    public static String problem(List<Power> powers, Realm realm) {
        for (Power p : powers) {
            if (p.effect() == null) continue;
            switch (p.effect()) {
                case POTION -> {
                    PotionEffectType type = potion(p.word());
                    if (type == null) return "\"" + p.text() + "\": there is no potion effect called " + p.word() + ".";
                    if (type.isInstant() || p.word().endsWith("omen")) {
                        return "\"" + p.text() + "\": " + p.word() + " cannot be held in a relic; use damage or heal.";
                    }
                }
                case SOUND -> {
                    if (sound(p.word()) == null) return "\"" + p.text() + "\": there is no sound called " + p.word() + ".";
                }
                case AURA -> {
                    if (!p.word().isEmpty() && particle(p.word()) == null) {
                        return "\"" + p.text() + "\": " + p.word() + " is not a simple particle. Try end_rod, flame, "
                                + "soul_fire_flame, enchant, snowflake, electric_spark, glow, cherry_leaves, portal, or "
                                + "give a #color.";
                    }
                }
                case SCRIPT -> {
                    String no = net.bigyous.gptgodmc.god.tools.RelicScripts.problem(realm, p.word());
                    if (no != null) return "\"" + p.text() + "\": " + no;
                }
                case FX -> {
                    FxLibrary.Entry e = realm == null ? null : FxLibrary.of(realm).find(p.word());
                    if (e == null || e.layers == null) {
                        return "\"" + p.text() + "\": you have no saved effect called \"" + p.word() + "\" (save one with "
                                + "library_save first; a routine cannot be bound to a relic).";
                    }
                }
                default -> {
                }
            }
        }
        return null;
    }

    private static PotionEffectType potion(String name) {
        try {
            return Registry.EFFECT.get(NamespacedKey.minecraft(name));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Sound sound(String key) {
        try {
            NamespacedKey k = NamespacedKey.fromString(key);
            return k == null ? null : Registry.SOUNDS.get(k);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A particle that needs nothing but a place, by name; null for the rest. */
    private static Particle particle(String name) {
        try {
            Particle p = Particle.valueOf(name.replace("minecraft:", "").toUpperCase(Locale.ROOT));
            return p.getDataType() == Void.class ? p : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- Who holds what ---------------------------------------------------------------------------------

    private static Held held(Player p, EquipmentSlot slot) {
        ItemStack stack = p.getInventory().getItem(slot);
        if (stack == null || stack.getType().isAir()) return null;
        Relic r = Relics.of(stack);
        return r == null || r.powers().isEmpty() ? null : new Held(r, stack, Relics.awake(stack), slot);
    }

    /** The colour of a relic's own light: the average of what shines in it (awake, if it has two forms). */
    static int glow(Relic r) {
        return GLOW.computeIfAbsent(r.id() + "#" + r.rev() + "#" + r.made(), k -> {
            try {
                Art on = Relics.artOn(r);
                Art art = on != null && on.lit() != null ? on : Relics.art(r);
                if (art.lit() == null) return 0xFFFFFF;
                long red = 0, green = 0, blue = 0, n = 0;
                for (int argb : art.lit()[0]) {
                    if ((argb >>> 24) < 128) continue;
                    red += (argb >> 16) & 255;
                    green += (argb >> 8) & 255;
                    blue += argb & 255;
                    n++;
                }
                return n == 0 ? 0xFFFFFF : (int) (red / n) << 16 | (int) (green / n) << 8 | (int) (blue / n);
            } catch (RuntimeException e) {
                return 0xFFFFFF;
            }
        });
    }

    private static boolean ready(Player p, Relic r, int index, Power power, long now, boolean spend) {
        if (power.cooldown() <= 0) return true;
        Map<String, Long> mine = READY.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        String key = r.id() + "#" + index;
        Long at = mine.get(key);
        if (at != null && now < at) return false;
        if (spend) mine.put(key, now + power.cooldown() * 1000L);
        return true;
    }

    private static boolean lucky(Power p) {
        return p.chance() >= 100 || ThreadLocalRandom.current().nextInt(100) < p.chance();
    }

    // ---- When things happen -----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!striking && event.getDamager() instanceof Player p
                && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getEntity() instanceof LivingEntity foe) {
            Held h = held(p, EquipmentSlot.HAND);
            if (h != null) fire(p, h, Trigger.HIT, foe, event);
        }
        if (event.getEntity() instanceof Player p) {
            Entity by = event.getDamager();
            if (by instanceof Projectile shot && shot.getShooter() instanceof Entity shooter) by = shooter;
            LivingEntity foe = by instanceof LivingEntity l && !l.equals(p) ? l : null;
            for (EquipmentSlot slot : HANDS) {
                Held h = held(p, slot);
                if (h != null) fire(p, h, Trigger.HURT, foe, null);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player p = event.getEntity().getKiller();
        if (p == null || p.equals(event.getEntity())) return;
        Held h = held(p, EquipmentSlot.HAND);
        if (h != null) fire(p, h, Trigger.KILL, event.getEntity(), null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerArmSwingEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Held h = held(event.getPlayer(), EquipmentSlot.HAND);
        if (h != null) fire(event.getPlayer(), h, Trigger.SWING, null, null);
    }

    @EventHandler
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player p)) return;
        Long until = SOFT.remove(p.getUniqueId());
        if (until != null && System.currentTimeMillis() < until) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        READY.remove(id);
        SOFT.remove(id);
        Spot s = LIGHTS.remove(id);
        if (s != null) show(s, false);
    }

    /**
     * Right-click. A relic that can wake is woken by it; once awake, a click sets off what it does and
     * sneaking with a click puts it back to sleep. (A relic whose only use is waking toggles on every click.)
     */
    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        Held h = held(p, EquipmentSlot.HAND);
        if (h == null) return;
        Block clicked = event.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable() && !p.isSneaking()) return; // a door is a door
        List<Power> powers = RelicPowers.of(h.relic());
        boolean toggles = false, acts = false;
        for (Power pw : powers) {
            if (pw.trigger() != Trigger.USE) continue;
            if (pw.effect() == Effect.TOGGLE) toggles = true;
            else if (pw.effect() != Effect.SOUND && pw.effect() != Effect.FX && fits(pw, h.awake())) acts = true;
        }
        if (!toggles && !acts) {
            // Only sounds or scenes: they still play.
            fire(p, h, Trigger.USE, null, null);
            return;
        }
        boolean toggling = toggles && (!acts || p.isSneaking());
        long now = System.currentTimeMillis();
        boolean toggled = false, heard = false;
        int rest = 0;
        long wait = 0;
        for (int i = 0; i < powers.size(); i++) {
            Power pw = powers.get(i);
            if (pw.trigger() != Trigger.USE || !fits(pw, h.awake())) continue;
            boolean show = pw.effect() == Effect.SOUND || pw.effect() == Effect.FX;
            if (pw.effect() == Effect.TOGGLE ? !toggling : show ? toggles && !toggling : toggling) continue;
            if (!ready(p, h.relic(), i, pw, now, true)) {
                Long at = READY.get(p.getUniqueId()).get(h.relic().id() + "#" + i);
                if (!show && at != null) wait = Math.max(wait, at - now);
                continue;
            }
            if (!lucky(pw)) continue;
            if (pw.effect() == Effect.TOGGLE) {
                toggled = true;
                continue;
            }
            heard |= pw.effect() == Effect.SOUND;
            if (!show) rest = Math.max(rest, pw.cooldown());
            act(p, h, pw, null, null);
        }
        if (toggled) toggle(p, h, !heard);
        if (rest >= 2) {
            try {
                p.setCooldown(h.stack(), rest * 20);
            } catch (RuntimeException e) {
                // not drawn, still kept
            }
        } else if (!toggled && rest == 0 && wait > 1000) {
            p.sendActionBar(Component.text(h.relic().name() + " gathers itself (" + (wait + 999) / 1000 + "s)",
                    net.bigyous.gptgodmc.god.Speech.DEEP_VIOLET));
        }
    }

    private static void fire(Player p, Held h, Trigger trigger, LivingEntity foe, EntityDamageByEntityEvent hit) {
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        List<Power> powers = RelicPowers.of(h.relic());
        long now = System.currentTimeMillis();
        for (int i = 0; i < powers.size(); i++) {
            Power pw = powers.get(i);
            if (pw.trigger() != trigger || pw.effect() == Effect.TOGGLE || !fits(pw, h.awake())) continue;
            if (!ready(p, h.relic(), i, pw, now, true) || !lucky(pw)) continue;
            try {
                act(p, h, pw, foe, hit);
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("Relic power failed: " + pw.text(), e);
            }
        }
    }

    // ---- Waking and sleeping ----------------------------------------------------------------------------

    private static void toggle(Player p, Held h, boolean ownSound) {
        boolean on = !h.awake();
        ItemStack stack = h.stack();
        Relics.dress(stack, h.relic(), on);
        p.getInventory().setItem(h.slot(), stack);
        World w = p.getWorld();
        Location hand = p.getEyeLocation().subtract(0, 0.35, 0);
        Vector fwd = hand.getDirection();
        int rgb = glow(h.relic());
        if (on) {
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(rgb), 0.9f);
            for (double d = 0.5; d < 2.2; d += 0.18) {
                w.spawnParticle(Particle.DUST, hand.clone().add(fwd.clone().multiply(d)), 1, 0.02, 0.02, 0.02, 0, dust);
            }
            w.spawnParticle(Particle.ELECTRIC_SPARK, hand.clone().add(fwd.clone().multiply(0.8)), 8, 0.15, 0.15, 0.15, 0.4);
            if (ownSound) {
                w.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.9f);
                w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
            }
        } else {
            w.spawnParticle(Particle.SMOKE, hand.clone().add(fwd.clone().multiply(0.9)), 8, 0.1, 0.1, 0.1, 0.01);
            if (ownSound) w.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.9f);
        }
    }

    // ---- What a power does ------------------------------------------------------------------------------

    private static void act(Player p, Held h, Power pw, LivingEntity foe, EntityDamageByEntityEvent hit) {
        World w = p.getWorld();
        int rgb = pw.color() >= 0 ? pw.color() : glow(h.relic());
        double n = pw.amount();
        switch (pw.effect()) {
            case DAMAGE -> {
                if (hit != null) hit.setDamage(hit.getDamage() + n);
            }
            case IGNITE -> {
                if (foe != null) foe.setFireTicks(Math.max(foe.getFireTicks(), (int) n * 20));
            }
            case FREEZE -> {
                if (foe == null) return;
                foe.setFreezeTicks(Math.min(foe.getMaxFreezeTicks() + (int) n * 20, foe.getFreezeTicks() + 140 + (int) n * 20));
                foe.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) n * 20, 1, true, true, true));
                w.spawnParticle(Particle.SNOWFLAKE, middle(foe), 18, 0.3, 0.5, 0.3, 0.02);
                w.playSound(foe.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.5f, 1.7f);
            }
            case POTION -> {
                PotionEffectType type = potion(pw.word());
                if (type == null || type.isInstant()) return;
                PotionEffect effect = new PotionEffect(type, pw.seconds() * 20, (int) n - 1, true, true, true);
                switch (pw.aim()) {
                    case "foe" -> {
                        if (foe != null) foe.addPotionEffect(effect);
                    }
                    case "near" -> near(p, p.getLocation(), 5).forEach(e -> e.addPotionEffect(effect));
                    default -> p.addPotionEffect(effect);
                }
            }
            case HEAL -> {
                p.heal(n);
                w.spawnParticle(Particle.HEART, p.getLocation().add(0, 2.1, 0), 3, 0.3, 0.1, 0.3, 0);
            }
            case KNOCKBACK -> {
                if (foe == null) return;
                Vector away = foe.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                if (away.lengthSquared() < 1e-4) away = p.getLocation().getDirection().setY(0);
                foe.setVelocity(foe.getVelocity().add(away.normalize().multiply(0.45 * n).setY(0.28)));
            }
            case LAUNCH -> {
                if (foe != null) foe.setVelocity(foe.getVelocity().setY(0.45 + 0.25 * n));
            }
            case SHIELD -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 20, (int) Math.ceil(n / 4) - 1, true,
                        true, true));
                ring(w, p.getLocation().add(0, 1, 0), 0.9, rgb, 0.9f, 16);
                w.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 0.8f, 1.5f);
            }
            case SOUND -> {
                Sound s = sound(pw.word());
                if (s != null) w.playSound(p.getLocation(), s, pw.trigger() == Trigger.HELD ? 0.35f : 0.9f, (float) pw.pitch());
            }
            case FX -> fx(p, h.relic(), pw, foe);
            case SCRIPT -> net.bigyous.gptgodmc.god.tools.RelicScripts.fire(p, h.relic(), pw, foe, h.awake());
            case LIGHTNING -> {
                Location at = foe != null ? foe.getLocation() : aim(p, 24);
                w.strikeLightningEffect(at);
                if (hit != null) hit.setDamage(hit.getDamage() + n);
                for (LivingEntity e : near(p, at, 2.5)) {
                    if (hit == null || !e.equals(foe)) hurt(p, e, n);
                }
            }
            case BURST -> {
                Location at = foe != null ? middle(foe) : aim(p, 16);
                w.spawnParticle(Particle.EXPLOSION, at, 1);
                Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(rgb), 1.6f);
                w.spawnParticle(Particle.DUST, at, 40, 1.1, 1.1, 1.1, 0, dust);
                w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
                if (hit != null) hit.setDamage(hit.getDamage() + n);
                for (LivingEntity e : near(p, at, 3)) {
                    if (hit != null && e.equals(foe)) continue;
                    hurt(p, e, n);
                    push(e, at, 0.6, 0.35);
                }
            }
            case DASH -> {
                Vector dir = p.getLocation().getDirection();
                dir.setY(Math.max(0.12, dir.getY() * 0.5));
                p.setVelocity(dir.normalize().multiply(Math.min(2.4, 0.4 + n * 0.16)));
                soften(p);
                w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_CHARGE, 0.8f, 1.5f);
                streak(p, rgb, 8);
            }
            case BLINK -> blink(p, n, rgb);
            case LEAP -> {
                Vector dir = p.getLocation().getDirection().setY(0);
                if (dir.lengthSquared() > 1e-4) dir.normalize().multiply(0.35);
                p.setVelocity(dir.setY(0.55 + 0.27 * n));
                soften(p);
                ring(w, p.getLocation().add(0, 0.1, 0), 1.2, rgb, 1f, 20);
                w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.9f, 1.1f);
                streak(p, rgb, 10);
            }
            case PULL -> pull(p, n, rgb);
            case BOLT -> bolt(p, pw.word());
            case SLASH -> slash(p, n, rgb, false);
            case TRAIL -> slash(p, 0, rgb, true);
            case WAVE -> wave(p, n, rgb);
            case BEAM -> beam(p, n, rgb);
            case SLAM -> slam(p, n, rgb);
            case SPIN -> spin(p, n, rgb);
            case THROW -> fling(p, h, n, rgb);
            default -> {
            }
        }
    }

    /**
     * A blow of the relic in its god's own hand (the vessel's strike, not a mortal's): what it does on a hit,
     * as far as that needs no mortal bearer (extra damage, fire, frost, a potion on the foe, knockback, a
     * launch, lightning, a burst, a saved effect). Returns the extra damage. Server thread.
     *
     * @param from where the blow comes from (the vessel), for knockback
     */
    public static double strikeEffects(Relic r, boolean awake, LivingEntity foe, Location from) {
        double extra = 0;
        World w = foe.getWorld();
        for (Power pw : RelicPowers.of(r)) {
            if (pw.trigger() != Trigger.HIT || !fits(pw, awake) || !lucky(pw)) continue;
            double n = pw.amount();
            switch (pw.effect()) {
                case DAMAGE -> extra += n;
                case IGNITE -> foe.setFireTicks(Math.max(foe.getFireTicks(), (int) n * 20));
                case FREEZE -> {
                    foe.setFreezeTicks(Math.min(foe.getMaxFreezeTicks() + (int) n * 20, foe.getFreezeTicks() + 140 + (int) n * 20));
                    foe.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) n * 20, 1, true, true, true));
                    w.spawnParticle(Particle.SNOWFLAKE, middle(foe), 18, 0.3, 0.5, 0.3, 0.02);
                }
                case POTION -> {
                    PotionEffectType type = potion(pw.word());
                    if (type != null && !type.isInstant() && !pw.aim().equals("self")) {
                        foe.addPotionEffect(new PotionEffect(type, pw.seconds() * 20, (int) n - 1, true, true, true));
                    }
                }
                case KNOCKBACK -> {
                    Vector away = foe.getLocation().toVector().subtract(from.toVector()).setY(0);
                    if (away.lengthSquared() > 1e-4) {
                        foe.setVelocity(foe.getVelocity().add(away.normalize().multiply(0.45 * n).setY(0.28)));
                    }
                }
                case LAUNCH -> foe.setVelocity(foe.getVelocity().setY(0.45 + 0.25 * n));
                case LIGHTNING -> {
                    w.strikeLightningEffect(foe.getLocation());
                    extra += n;
                }
                case BURST -> {
                    w.spawnParticle(Particle.EXPLOSION, middle(foe), 1);
                    w.playSound(foe.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
                    extra += n * 0.5;
                }
                case FX -> fx(null, r, pw, foe);
                default -> {
                }
            }
        }
        return extra;
    }

    /** The attack damage a relic lends in a form (its stat), or NaN when it sets none. */
    public static double attackDamage(Relic r, boolean awake) {
        double v = Double.NaN;
        for (Power pw : RelicPowers.of(r)) {
            if (pw.trigger() == Trigger.STAT && pw.stat() == Stat.ATTACK_DAMAGE && fits(pw, awake)) v = pw.amount();
        }
        return v;
    }

    private static void fx(Player p, Relic r, Power pw, LivingEntity foe) {
        Realm realm = owner(r);
        if (realm == null) return;
        FxLibrary.Entry e = FxLibrary.of(realm).find(pw.word());
        if (e == null || e.layers == null || FxShow.running(realm) >= FxShow.MAX_RUNNING) return;
        Entity on = foe != null && !foe.isDead() ? foe : p;
        FxShow.play(realm, null, on.getLocation(), on, 1, false,
                FxStyle.of(realm).resolve(e.layers, FxStyle.currentMood(realm)), null);
    }

    // ---- Helpers ----------------------------------------------------------------------------------------

    private static Location middle(LivingEntity e) {
        return e.getLocation().add(0, e.getHeight() / 2, 0);
    }

    /** Living things a relic's power may strike near a place: not its bearer, their pets, or anyone merely watching. */
    private static List<LivingEntity> near(Player p, Location at, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : at.getWorld().getNearbyEntities(at, radius, radius, radius)) {
            if (!(e instanceof LivingEntity l) || e.equals(p) || e.isDead() || e.isInvulnerable()) continue;
            if (e instanceof Player other && (other.getGameMode() == GameMode.CREATIVE
                    || other.getGameMode() == GameMode.SPECTATOR)) {
                continue;
            }
            if (e instanceof Tameable pet && p.equals(pet.getOwner())) continue;
            if (e.getType() == org.bukkit.entity.EntityType.ARMOR_STAND) continue;
            if (middle(l).distanceSquared(at) > radius * radius) continue;
            out.add(l);
        }
        return out;
    }

    private static void hurt(Player p, LivingEntity e, double amount) {
        if (amount <= 0) return;
        striking = true;
        try {
            e.damage(amount, p);
        } finally {
            striking = false;
        }
    }

    private static void push(LivingEntity e, Location from, double out, double up) {
        Vector away = e.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (away.lengthSquared() < 1e-4) away = new Vector(0, 0, 0);
        else away.normalize().multiply(out);
        e.setVelocity(e.getVelocity().add(away.setY(up)));
    }

    /** Where the bearer is looking: the first solid thing within reach, or the end of their reach. */
    private static Location aim(Player p, double reach) {
        Location eye = p.getEyeLocation();
        RayTraceResult hit = p.getWorld().rayTraceBlocks(eye, eye.getDirection(), reach, FluidCollisionMode.NEVER, true);
        if (hit != null) return hit.getHitPosition().toLocation(p.getWorld());
        return eye.clone().add(eye.getDirection().multiply(reach));
    }

    private static void soften(Player p) {
        p.setFallDistance(0);
        SOFT.put(p.getUniqueId(), System.currentTimeMillis() + 8000);
    }

    private static int lighter(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return (r + (255 - r) * 2 / 3) << 16 | (g + (255 - g) * 2 / 3) << 8 | (b + (255 - b) * 2 / 3);
    }

    private static void ring(World w, Location c, double radius, int rgb, float size, int points) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(rgb), size);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            w.spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * radius, c.getY(), c.getZ() + Math.sin(a) * radius, 1, 0, 0,
                    0, 0, dust);
        }
    }

    /** A streak of light left behind the bearer for a few ticks. */
    private static void streak(Player p, int rgb, int ticks) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(rgb), 1.1f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!p.isOnline() || tick++ >= ticks) {
                    cancel();
                    return;
                }
                p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 0.9, 0), 6, 0.2, 0.4, 0.2, 0, dust);
            }
        }.runTaskTimer(GPTGOD.get(), 1, 1);
    }

    /** The bearer's view as three axes: ahead, to the right, and up across the view. */
    private static Vector[] frame(Location eye) {
        Vector fwd = eye.getDirection().normalize();
        Vector right = fwd.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-4) right = new Vector(1, 0, 0);
        right.normalize();
        return new Vector[] { fwd, right, right.clone().crossProduct(fwd).normalize() };
    }

    // ---- The attacks ------------------------------------------------------------------------------------

    /** A sweeping arc in front of the bearer, drawn over three ticks; thin = only a trail of light, no blow. */
    private static void slash(Player p, double amount, int rgb, boolean thin) {
        Location base = p.getEyeLocation().subtract(0, 0.35, 0);
        Vector[] f = frame(base);
        double tilt = (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.1;
        Vector side = f[1].clone().multiply(Math.cos(tilt)).add(f[2].clone().multiply(Math.sin(tilt)));
        boolean leftward = ThreadLocalRandom.current().nextBoolean();
        World w = p.getWorld();
        Particle.DustOptions core = new Particle.DustOptions(Color.fromRGB(rgb), thin ? 0.7f : 1.25f);
        Particle.DustOptions edge = new Particle.DustOptions(Color.fromRGB(lighter(rgb)), thin ? 0.5f : 0.8f);
        double reach = thin ? 2.1 : 2.9, width = thin ? 0.3 : 0.9;
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                for (int i = 0; i < 9; i++) {
                    double a = Math.toRadians(-75 + (tick * 9 + i) * 150.0 / 26) * (leftward ? -1 : 1);
                    Vector d = f[0].clone().multiply(Math.cos(a)).add(side.clone().multiply(Math.sin(a)));
                    for (double r = reach - width; r <= reach + 1e-6; r += 0.3) {
                        w.spawnParticle(Particle.DUST, base.clone().add(d.clone().multiply(r)), 1, 0, 0, 0, 0,
                                r > reach - 0.05 ? edge : core);
                    }
                }
                if (++tick == 3) cancel();
            }
        }.runTaskTimer(GPTGOD.get(), 0, 1);
        if (thin) return;
        w.spawnParticle(Particle.SWEEP_ATTACK, base.clone().add(f[0].clone().multiply(1.8)), 1);
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.25f);
        for (LivingEntity e : near(p, base, reach + 0.6)) {
            Vector to = middle(e).toVector().subtract(base.toVector());
            if (to.lengthSquared() < 1e-4 || to.normalize().dot(f[0]) > 0.25) hurt(p, e, amount);
        }
    }

    /** A crescent of light that leaves the relic and flies ahead, striking what it passes through. */
    private static void wave(Player p, double amount, int rgb) {
        Location start = p.getEyeLocation().subtract(0, 0.4, 0);
        Vector[] f = frame(start);
        World w = p.getWorld();
        Particle.DustOptions core = new Particle.DustOptions(Color.fromRGB(rgb), 1.4f);
        Particle.DustOptions edge = new Particle.DustOptions(Color.fromRGB(lighter(rgb)), 0.9f);
        Set<UUID> struck = new HashSet<>();
        w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.7f, 1.6f);
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 0.8f);
        new BukkitRunnable() {
            double gone = 1.2;

            @Override
            public void run() {
                for (int step = 0; step < 2; step++) {
                    Location c = start.clone().add(f[0].clone().multiply(gone));
                    if (gone > 16 || !c.getBlock().isPassable()) {
                        w.spawnParticle(Particle.DUST, c, 24, 0.5, 0.5, 0.5, 0, core);
                        w.playSound(c, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8f, 1.3f);
                        cancel();
                        return;
                    }
                    for (int i = -7; i <= 7; i++) {
                        double s = i / 7.0;
                        Location at = c.clone().add(f[1].clone().multiply(s * 1.6)).subtract(f[0].clone().multiply(s * s));
                        w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, Math.abs(i) > 5 ? edge : core);
                    }
                    if (step == 0) w.spawnParticle(Particle.END_ROD, c, 2, 0.5, 0.1, 0.5, 0.01);
                    for (LivingEntity e : near(p, c, 1.9)) {
                        if (struck.add(e.getUniqueId())) hurt(p, e, amount);
                    }
                    gone += 0.6;
                }
            }
        }.runTaskTimer(GPTGOD.get(), 0, 1);
    }

    /** A straight beam to whatever the bearer looks at, through everything living in its way. */
    private static void beam(Player p, double amount, int rgb) {
        Location eye = p.getEyeLocation().subtract(0, 0.25, 0);
        Vector fwd = eye.getDirection().normalize();
        World w = p.getWorld();
        RayTraceResult wall = w.rayTraceBlocks(eye, fwd, 24, FluidCollisionMode.NEVER, true);
        double length = wall == null ? 24 : Math.max(1, wall.getHitPosition().distance(eye.toVector()));
        Particle.DustOptions core = new Particle.DustOptions(Color.fromRGB(lighter(rgb)), 0.9f);
        Particle.DustOptions halo = new Particle.DustOptions(Color.fromRGB(rgb), 1.5f);
        w.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 1.8f);
        w.playSound(p.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 0.6f, 1.6f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                for (double d = 0.8; d < length; d += 0.35) {
                    Location at = eye.clone().add(fwd.clone().multiply(d));
                    w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, core);
                    if (tick == 0 && ((int) (d * 3)) % 3 == 0) w.spawnParticle(Particle.DUST, at, 1, 0.06, 0.06, 0.06, 0, halo);
                }
                if (++tick == 4) cancel();
            }
        }.runTaskTimer(GPTGOD.get(), 0, 1);
        Location end = eye.clone().add(fwd.clone().multiply(length));
        w.spawnParticle(Particle.END_ROD, end, 12, 0.1, 0.1, 0.1, 0.08);
        Location mid = eye.clone().add(fwd.clone().multiply(length / 2));
        for (LivingEntity e : near(p, mid, length / 2 + 1.5)) {
            Vector to = middle(e).toVector().subtract(eye.toVector());
            double along = Math.max(0, Math.min(length, to.dot(fwd)));
            if (eye.toVector().add(fwd.clone().multiply(along)).distanceSquared(middle(e).toVector()) < 0.95 * 0.95) {
                hurt(p, e, amount);
            }
        }
    }

    /** The bearer strikes the ground (dropping onto it first if airborne) and a ring breaks outward. */
    private static void slam(Player p, double amount, int rgb) {
        boolean airborne = p.getLocation().subtract(0, 0.2, 0).getBlock().isPassable();
        if (airborne) {
            p.setVelocity(new Vector(0, -1.7, 0));
            soften(p);
        }
        new BukkitRunnable() {
            int waited;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                boolean down = !p.getLocation().subtract(0, 0.2, 0).getBlock().isPassable();
                if (!down && waited++ < 40) return;
                cancel();
                if (down) quake(p, amount, rgb);
            }
        }.runTaskTimer(GPTGOD.get(), airborne ? 2 : 0, 1);
    }

    private static void quake(Player p, double amount, int rgb) {
        Location c = p.getLocation();
        World w = p.getWorld();
        double radius = Math.min(6, 2.5 + amount * 0.35);
        BlockData ground = c.clone().subtract(0, 0.2, 0).getBlock().getBlockData();
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.7f);
        w.playSound(c, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.6f);
        for (LivingEntity e : near(p, c.clone().add(0, 0.5, 0), radius)) {
            if (Math.abs(e.getLocation().getY() - c.getY()) > 2.5) continue;
            hurt(p, e, amount);
            push(e, c, 0.5, 0.5);
        }
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(rgb), 1.5f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                double r = radius * (tick + 1) / 8.0;
                int points = (int) Math.max(12, r * 10);
                for (int i = 0; i < points; i++) {
                    double a = Math.PI * 2 * i / points;
                    Location at = c.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r);
                    w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
                    if (i % 3 == 0 && !ground.getMaterial().isAir()) {
                        w.spawnParticle(Particle.BLOCK, at, 2, 0.1, 0.05, 0.1, 0, ground);
                    }
                }
                if (++tick == 8) cancel();
            }
        }.runTaskTimer(GPTGOD.get(), 0, 1);
    }

    /** A whirl about the bearer: two arms of light go twice round and everything close is struck and thrown back. */
    private static void spin(Player p, double amount, int rgb) {
        World w = p.getWorld();
        Particle.DustOptions core = new Particle.DustOptions(Color.fromRGB(rgb), 1.3f);
        Particle.DustOptions edge = new Particle.DustOptions(Color.fromRGB(lighter(rgb)), 0.8f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    cancel();
                    return;
                }
                Location c = p.getLocation().add(0, 1.0, 0);
                for (int arm = 0; arm < 2; arm++) {
                    for (int i = 0; i < 6; i++) {
                        double a = Math.PI * arm + (tick * 6 + i) * Math.PI * 4 / 60;
                        for (double r = 1.4; r <= 3.01; r += 0.4) {
                            w.spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * r, c.getY() + Math.sin(a * 2) * 0.15,
                                    c.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0, r > 2.9 ? edge : core);
                        }
                    }
                }
                if (tick % 4 == 0) w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 0.9f + tick * 0.04f);
                if (tick == 2) {
                    for (LivingEntity e : near(p, c, 3.4)) {
                        hurt(p, e, amount);
                        push(e, c, 0.7, 0.3);
                    }
                }
                if (++tick == 10) cancel();
            }
        }.runTaskTimer(GPTGOD.get(), 0, 1);
    }

    /** The relic leaves the hand, spins out to where the bearer looks and comes back, striking on both passes. */
    private static void fling(Player p, Held h, double amount, int rgb) {
        Location start = p.getEyeLocation().subtract(0, 0.3, 0);
        Vector fwd = start.getDirection().normalize();
        World w = p.getWorld();
        RayTraceResult wall = w.rayTraceBlocks(start, fwd, 12, FluidCollisionMode.NEVER, true);
        double reach = wall == null ? 12 : Math.max(1.5, wall.getHitPosition().distance(start.toVector()) - 0.6);
        Location far = start.clone().add(fwd.clone().multiply(reach));
        ItemStack shown = h.stack().clone();
        Location spawnAt = start.clone();
        spawnAt.setYaw(start.getYaw() + 90);
        spawnAt.setPitch(0);
        ItemDisplay d = w.spawn(spawnAt, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.getPersistentDataContainer().set(new NamespacedKey(GPTGOD.get(), "relic_fx"), PersistentDataType.BYTE, (byte) 1);
            e.setItemStack(shown);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setShadowRadius(0);
            e.setTeleportDuration(1);
            e.setVisibleByDefault(false);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.9f), new Quaternionf()));
        });
        for (Player viewer : w.getPlayers()) {
            if (VesselPackHost.has(viewer) || ModRelics.draws(viewer)) viewer.showEntity(GPTGOD.get(), d);
        }
        FLYING.add(d);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(rgb), 1.0f);
        w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 0.9f, 1.3f);
        Set<UUID> out = new HashSet<>(), back = new HashSet<>();
        int outTicks = (int) Math.max(5, Math.round(reach * 0.9));
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick++;
                Location hand = p.isOnline() ? p.getEyeLocation().subtract(0, 0.3, 0) : start;
                boolean returning = tick > outTicks;
                Location at;
                if (!returning) {
                    double t = tick / (double) outTicks;
                    at = start.clone().add(fwd.clone().multiply(reach * (1 - (1 - t) * (1 - t))));
                } else {
                    double t = Math.min(1, (tick - outTicks) / (double) outTicks);
                    at = far.clone().add(hand.toVector().subtract(far.toVector()).multiply(t * t));
                }
                boolean done = !d.isValid() || !p.isOnline() || !hand.getWorld().equals(w)
                        || (returning && (at.distanceSquared(hand) < 1.0 || tick > outTicks * 2 + 10));
                if (done) {
                    cancel();
                    FLYING.remove(d);
                    d.remove();
                    if (p.isOnline()) w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RETURN, 0.8f, 1.4f);
                    return;
                }
                at.setYaw(spawnAt.getYaw());
                at.setPitch(0);
                d.teleport(at);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(1);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationZ(tick * 1.25f),
                        new Vector3f(0.9f), new Quaternionf()));
                w.spawnParticle(Particle.DUST, at, 3, 0.15, 0.15, 0.15, 0, dust);
                if (tick % 3 == 0) w.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.35f, 1.7f);
                for (LivingEntity e : near(p, at, 1.4)) {
                    if ((returning ? back : out).add(e.getUniqueId())) hurt(p, e, amount);
                }
            }
        }.runTaskTimer(GPTGOD.get(), 0, 1);
    }

    private static void blink(Player p, double blocks, int rgb) {
        Location eye = p.getEyeLocation();
        Vector fwd = eye.getDirection().normalize();
        World w = p.getWorld();
        RayTraceResult wall = w.rayTraceBlocks(eye, fwd, blocks, FluidCollisionMode.NEVER, true);
        double reach = wall == null ? blocks : wall.getHitPosition().distance(eye.toVector()) - 0.4;
        Location from = p.getLocation();
        for (double d = reach; d >= 1; d -= 0.5) {
            Location to = from.clone().add(fwd.clone().multiply(d));
            if (!to.getBlock().isPassable() || !to.clone().add(0, 1, 0).getBlock().isPassable()) continue;
            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(rgb), 1.2f);
            w.spawnParticle(Particle.DUST, from.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0, dust);
            w.spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.4);
            p.teleport(to);
            soften(p);
            w.spawnParticle(Particle.DUST, to.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0, dust);
            w.spawnParticle(Particle.REVERSE_PORTAL, to.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.05);
            w.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.3f);
            return;
        }
    }

    private static void pull(Player p, double range, int rgb) {
        Location eye = p.getEyeLocation();
        World w = p.getWorld();
        RayTraceResult found = w.rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, 0.6,
                e -> e instanceof LivingEntity && !e.equals(p) && near(p, e.getLocation().add(0, 1, 0), 3).contains(e));
        if (found == null || !(found.getHitEntity() instanceof LivingEntity foe)) return;
        Vector to = eye.toVector().subtract(middle(foe).toVector());
        double far = to.length();
        if (far < 1.5) return;
        foe.setVelocity(to.normalize().multiply(Math.min(2.2, 0.5 + far * 0.13)).setY(Math.max(0.3, to.getY() * 0.1 + 0.3)));
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(rgb), 0.8f);
        Vector step = to.clone().normalize().multiply(0.4);
        Location at = middle(foe);
        for (double d = 0; d < far; d += 0.4) w.spawnParticle(Particle.DUST, at.add(step), 1, 0, 0, 0, 0, dust);
        w.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.9f, 0.7f);
    }

    private static void bolt(Player p, String kind) {
        Vector fwd = p.getEyeLocation().getDirection();
        switch (kind) {
            case "fireball" -> {
                SmallFireball f = p.launchProjectile(SmallFireball.class, fwd.multiply(1.2));
                f.setIsIncendiary(false); // it burns what it hits, never the land
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.1f);
            }
            case "arrow" -> {
                Arrow a = p.launchProjectile(Arrow.class, fwd.multiply(2.6));
                a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.9f, 1.2f);
            }
            case "snowball" -> {
                p.launchProjectile(Snowball.class, fwd.multiply(1.6));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.9f, 1f);
            }
            default -> {
                p.launchProjectile(WindCharge.class, fwd.multiply(1.4));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.9f, 1f);
            }
        }
    }

    // ---- While held -------------------------------------------------------------------------------------

    /** Four times a second: what relics do merely by being held, and copies of a changed relic catching up. */
    public static void tick() {
        beat++;
        Set<UUID> lit = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            int light = 0;
            for (EquipmentSlot slot : HANDS) {
                ItemStack stack = p.getInventory().getItem(slot);
                if (stack == null || stack.getType().isAir()) continue;
                Relic r = Relics.of(stack);
                if (r == null) continue;
                boolean awake = Relics.awake(stack);
                if (Relics.revOf(stack) != r.rev()) {
                    // The relic was imbued anew (or repainted) after this copy was made.
                    Relics.dress(stack, r, awake);
                    p.getInventory().setItem(slot, stack);
                    awake = Relics.awake(stack);
                }
                for (Power pw : RelicPowers.of(r)) {
                    if (pw.trigger() != Trigger.HELD || !fits(pw, awake)) continue;
                    switch (pw.effect()) {
                        case LIGHT -> light = Math.max(light, (int) pw.amount());
                        case POTION -> {
                            PotionEffectType type = potion(pw.word());
                            if (beat % 4 == 0 && type != null && !type.isInstant()) {
                                p.addPotionEffect(new PotionEffect(type, Math.max(12, pw.seconds()) * 20, (int) pw.amount() - 1,
                                        true, false, true));
                            }
                        }
                        case AURA -> aura(p, pw, r);
                        case SOUND -> {
                            Sound s = sound(pw.word());
                            if (beat % 10 == 0 && s != null) {
                                p.getWorld().playSound(p.getLocation(), s, 0.35f, (float) pw.pitch());
                            }
                        }
                        default -> {
                        }
                    }
                }
            }
            if (light > 0 && carry(p, light)) lit.add(p.getUniqueId());
        }
        LIGHTS.entrySet().removeIf(e -> {
            if (lit.contains(e.getKey())) return false;
            show(e.getValue(), false);
            return true;
        });
    }

    private static void aura(Player p, Power pw, Relic r) {
        Location at = p.getLocation().add(0, 1.0, 0);
        Particle named = pw.word().isEmpty() ? null : particle(pw.word());
        if (named != null) {
            p.getWorld().spawnParticle(named, at, 2, 0.35, 0.5, 0.35, 0.01);
        } else {
            p.getWorld().spawnParticle(Particle.DUST, at, 3, 0.35, 0.5, 0.35, 0,
                    new Particle.DustOptions(Color.fromRGB(pw.color() >= 0 ? pw.color() : glow(r)), 0.8f));
        }
    }

    /**
     * Light that goes where the bearer goes: a light block shown (to players nearby, never set in the
     * world) in the air at their head. True when a light is showing.
     */
    private static boolean carry(Player p, int level) {
        Block b = p.getEyeLocation().getBlock();
        if (!b.getType().isAir()) b = p.getLocation().getBlock();
        Spot old = LIGHTS.get(p.getUniqueId());
        if (!b.getType().isAir()) {
            return old != null; // no air to light just here: the last place stays lit a moment longer
        }
        if (old != null && old.world().equals(b.getWorld()) && old.x() == b.getX() && old.y() == b.getY()
                && old.z() == b.getZ() && old.level() == level) {
            if (beat % 8 == 0) show(old, true); // again, for whoever has come near since
            return true;
        }
        if (old != null) show(old, false);
        Spot now = new Spot(b.getWorld(), b.getX(), b.getY(), b.getZ(), level);
        LIGHTS.put(p.getUniqueId(), now);
        show(now, true);
        return true;
    }

    private static void show(Spot s, boolean on) {
        Location at = s.at();
        if (!s.world().isChunkLoaded(s.x() >> 4, s.z() >> 4)) return;
        BlockData data;
        if (on) {
            data = Material.LIGHT.createBlockData();
            if (data instanceof org.bukkit.block.data.type.Light light) light.setLevel(Math.max(1, Math.min(15, s.level())));
        } else {
            data = at.getBlock().getBlockData();
        }
        for (Player viewer : s.world().getPlayers()) {
            if (viewer.getLocation().distanceSquared(at) < 72 * 72) viewer.sendBlockChange(at, data);
        }
    }

    /** Puts out every carried light and takes thrown relics out of the air (the plugin is stopping). */
    public static void shutdown() {
        LIGHTS.values().forEach(s -> show(s, false));
        LIGHTS.clear();
        FLYING.forEach(Entity::remove);
        FLYING.clear();
        READY.clear();
        SOFT.clear();
        GLOW.clear();
    }
}
