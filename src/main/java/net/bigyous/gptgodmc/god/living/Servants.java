package net.bigyous.gptgodmc.god.living;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Godslayer;
import net.bigyous.gptgodmc.god.Journal;
import net.bigyous.gptgodmc.god.LivingAccess;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Spectacle;
import net.bigyous.gptgodmc.god.vessel.VesselPackHost;
import net.bigyous.gptgodmc.loggables.GenericEventLoggable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * The gods' servants in the world. Each is a vanilla mob (hitbox, health) with its AI switched off: the
 * plugin steers it. Players whose client loaded the resource pack do not see that mob: they see the
 * servant's own painted body ({@link ServantBody}: item displays wearing {@link ServantPack}'s models)
 * and strike it through an Interaction hitbox that passes their blows on to the mob. Everyone else
 * (no pack, Bedrock) sees the vanilla mob wearing the block rig ({@link ServantRig}). Both rigs ride a
 * root display that follows the mob. Everything is non-persistent and tagged, so a restart leaves
 * nothing behind.
 *
 * <p>Hook for the client mod: every servant carries an invisible ItemDisplay rider whose item (paper) has
 * custom_data {"gptgodmc:servant": kind, "gptgodmc:servant_id": id, "gptgodmc:servant_god": god name,
 * "gptgodmc:servant_mob": mob UUID}; item components are synced to clients, so the mod can find the mob
 * and draw it its own way. The mob and every piece also carry the server-side PDC key gptgodmc:servant.
 */
public final class Servants {
    static final String TAG = "servant";
    private static final List<Servant> ALL = new CopyOnWriteArrayList<>();
    private static int counter;
    private static long tick;

    private Servants() {
    }

    static NamespacedKey key(String k) {
        return new NamespacedKey(GPTGOD.get(), k);
    }

    public static boolean isServantEntity(Entity e) {
        return e.getPersistentDataContainer().has(key(TAG), PersistentDataType.STRING);
    }

    public static Servant of(Entity e) {
        for (Servant s : ALL) {
            if (s.mob.equals(e) || s.root.equals(e) || e.equals(s.hitbox)) return s;
        }
        return null;
    }

    /** The servant whose painted body's hitbox this is, or null. */
    public static Servant ofHitbox(Entity e) {
        if (!(e instanceof Interaction)) return null;
        for (Servant s : ALL) {
            if (e.equals(s.hitbox)) return s;
        }
        return null;
    }

    public static List<Servant> of(Realm r) {
        List<Servant> out = new ArrayList<>();
        for (Servant s : ALL) {
            if (s.god == r) out.add(s);
        }
        return out;
    }

    public static int total() {
        return ALL.size();
    }

    /** Removes tagged entities that are not live servants (from a crash or reload). */
    static void cleanupStrays() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (isServantEntity(e) && of(e) == null && !riding(e)) e.remove();
            }
        }
    }

    private static boolean riding(Entity e) {
        Entity v = e.getVehicle();
        return v != null && of(v) != null;
    }

    static void shutdown() {
        for (Servant s : ALL) s.destroy(false);
        ALL.clear();
    }

    // ---- Summoning ------------------------------------------------------------------------------------

    /** Summons a servant for a god at a place; throws IllegalStateException with the reason. */
    public static Servant summon(Realm god, ServantKind kind, String name, Location at, int minutes) {
        String no = ServantRules.refusal(LivingSettings.servantsEnabled(), kind, god.power().id, of(god).size(),
                ALL.size(), LivingSettings.servantsPerGod(), LivingSettings.servantsTotal());
        if (no != null) throw new IllegalStateException(no);
        Location spot = HolyDayRunner.ground(at, 0.5);
        if (spot == null) spot = at.clone();
        if (kind.flies) spot.add(0, 1.2, 0);
        Servant s = new Servant(god, kind, cleanName(name, kind, god), spot,
                ServantRules.minutes(minutes, LivingSettings.servantsMaxMinutes()));
        ALL.add(s);
        return s;
    }

    private static String cleanName(String name, ServantKind kind, Realm god) {
        String n = name == null ? "" : name.replaceAll("[^\\p{L}\\p{N} '\\-]", "").trim();
        if (n.length() > 24) n = n.substring(0, 24);
        if (n.isEmpty()) n = Character.toUpperCase(kind.id.charAt(0)) + kind.id.substring(1) + " of " + god.displayName();
        return n;
    }

    /** Finds a god's servant by id or name, or null. */
    public static Servant find(Realm god, String idOrName) {
        if (idOrName == null) return null;
        String q = idOrName.trim().toLowerCase(Locale.ROOT);
        for (Servant s : of(god)) {
            if (s.id.equals(q) || s.name.toLowerCase(Locale.ROOT).equals(q)) return s;
        }
        for (Servant s : of(god)) {
            if (s.name.toLowerCase(Locale.ROOT).contains(q) || s.kind.id.equals(q)) return s;
        }
        return null;
    }

    /** Dismisses every servant of a god (it died, slept or was removed). */
    public static void dismissAll(Realm god, String why) {
        for (Servant s : of(god)) s.dismiss(why);
    }

    // ---- The clock ------------------------------------------------------------------------------------

    /** Every 2 ticks. */
    static void tickAll() {
        tick += 2;
        long now = System.currentTimeMillis();
        for (Servant s : ALL) {
            try {
                if (!s.alive()) {
                    s.faded("faded when the land around it fell asleep");
                } else if (now >= s.expiresAt) {
                    s.dismiss("its time in the world is over");
                } else if (Godslayer.isSlain(s.god) || s.god.isRemoved()) {
                    s.dismiss("its god is gone");
                } else {
                    s.tick(now);
                }
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("Servant " + s.id + " failed; dismissing it", e);
                s.destroy(true);
            }
        }
    }

    // ---- Perception -------------------------------------------------------------------------------------

    public static String perceive(Realm god) {
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        for (Servant s : of(god)) {
            Location l = s.mob.getLocation();
            sb.append("- ").append(s.id).append(' ').append(s.kind.id).append(" \"").append(s.name).append("\": ")
                    .append(s.doing()).append(String.format(" at (%d, %d, %d)", l.getBlockX(), l.getBlockY(), l.getBlockZ()))
                    .append(", ").append(Math.round(s.mob.getHealth())).append('/').append(Math.round(s.kind.health))
                    .append(" hp, ").append(HolyDays.clock(s.expiresAt - now)).append(" left\n");
        }
        return sb.toString();
    }

    // ---- One servant -------------------------------------------------------------------------------------

    public static final class Servant {
        public final String id;
        public final Realm god;
        public final ServantKind kind;
        public final String name;
        final Mob mob;
        final BlockDisplay root;
        final List<BlockDisplay> parts = new ArrayList<>();
        final Transformation[] sent;
        /** Its own painted body, for players with the resource pack (empty when textures are off). */
        final List<ItemDisplay> skin = new ArrayList<>();
        Transformation[] sentSkin = new Transformation[0];
        /** What those players strike and touch instead of the hidden mob, or null. */
        Interaction hitbox;
        /** Which body each player was last shown: true = the painted one. */
        final Map<UUID, Boolean> viewers = new HashMap<>();
        int skinViewers, plainViewers;
        /** The tick its last blow began (for the swing), and when a hurt flash ends. */
        long strikeTick = Long.MIN_VALUE, hurtUntil;
        final TextDisplay bubble;
        long expiresAt;
        long born;
        ServantKind.Order order = ServantKind.Order.GUARD;
        ServantKind.Order before = ServantKind.Order.GUARD;
        UUID target;
        long orderUntil;
        Location spot;
        Location waypoint;
        String sermon;
        int sermonIndex;
        long lastStrike, lastPreach, lastHeal, lastWaypoint, bubbleUntil;
        int stuck;
        float yaw;
        boolean moving;

        Servant(Realm god, ServantKind kind, String name, Location at, int minutes) {
            this.id = "s" + (++counter);
            this.god = god;
            this.kind = kind;
            this.name = name;
            this.born = System.currentTimeMillis();
            this.expiresAt = born + minutes * 60_000L;
            this.spot = at.clone();
            this.yaw = at.getYaw();
            World w = at.getWorld();
            @SuppressWarnings("unchecked")
            Class<? extends Mob> type = (Class<? extends Mob>) EntityType.valueOf(kind.mob).getEntityClass();
            mob = w.spawn(at, type, m -> setupMob(m));
            Location feet = mob.getLocation();
            feet.setYaw(0);
            feet.setPitch(0);
            root = w.spawn(feet, BlockDisplay.class, b -> {
                tag(b);
                b.setTeleportDuration(2);
            });
            List<ServantRig.Piece> pieces = ServantRig.pieces(kind, 0, false);
            sent = new Transformation[pieces.size()];
            for (int i = 0; i < pieces.size(); i++) {
                ServantRig.Piece p = pieces.get(i);
                Transformation t = ServantRig.transform(p, yaw);
                BlockDisplay d = w.spawn(feet, BlockDisplay.class, b -> {
                    tag(b);
                    b.setBlock(p.material().createBlockData());
                    b.setTransformation(t);
                    if (kind.glow != 0 && (p.part().equals("halo") || p.part().startsWith("wing_primary"))) {
                        b.setGlowing(kind == ServantKind.ANGEL);
                        b.setGlowColorOverride(Color.fromRGB(kind.glow));
                    }
                });
                sent[i] = t;
                parts.add(d);
                root.addPassenger(d);
            }
            spawnSkin(w, feet);
            // The client mod's hook: an invisible item rider whose custom_data names the servant.
            ItemDisplay marker = w.spawn(feet, ItemDisplay.class, d -> {
                tag(d);
                ItemStack item = new ItemStack(Material.PAPER);
                ItemMeta meta = item.getItemMeta();
                var pdc = meta.getPersistentDataContainer();
                pdc.set(key("servant"), PersistentDataType.STRING, kind.id);
                pdc.set(key("servant_id"), PersistentDataType.STRING, id);
                pdc.set(key("servant_god"), PersistentDataType.STRING, god.displayName());
                pdc.set(key("servant_mob"), PersistentDataType.STRING, mob.getUniqueId().toString());
                item.setItemMeta(meta);
                d.setItemStack(item);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0, 0, 0),
                        new Quaternionf()));
            });
            root.addPassenger(marker);
            bubble = w.spawn(feet, TextDisplay.class, t -> {
                tag(t);
                t.setBillboard(Display.Billboard.CENTER);
                t.text(Component.empty());
                t.setSeeThrough(false);
                t.setBackgroundColor(Color.fromARGB(0x60, 0, 0, 0));
                float above = skin.isEmpty() ? (float) kind.height() + 0.75f
                        : Math.max((float) kind.height() + 0.75f, ServantBody.top(kind) + 0.3f);
                t.setTransformation(new Transformation(new Vector3f(0, above, 0), new Quaternionf(),
                        new Vector3f(0.8f, 0.8f, 0.8f), new Quaternionf()));
            });
            root.addPassenger(bubble);
            refreshViewers();
            w.spawnParticle(particle(), at.clone().add(0, kind.height() / 2, 0), 40, 0.5, kind.height() / 2, 0.5, 0.05);
            w.playSound(at, kind == ServantKind.ANGEL ? Sound.BLOCK_BEACON_ACTIVATE
                    : kind == ServantKind.IMP ? Sound.ENTITY_BLAZE_SHOOT
                            : kind == ServantKind.SHADE ? Sound.ENTITY_WARDEN_EMERGE
                                    : kind == ServantKind.GUARD ? Sound.ENTITY_IRON_GOLEM_REPAIR : Sound.BLOCK_BELL_USE,
                    1f, kind == ServantKind.ANGEL ? 1.4f : 0.8f);
        }

        /** Spawns the painted body and its hitbox, hidden until a player is found to have the pack. */
        private void spawnSkin(World w, Location feet) {
            if (!LivingSettings.servantsTextured() || !VesselPackHost.active()) return;
            List<ServantBody.Part> body = ServantBody.parts(kind, ServantBody.Motion.idle(0));
            sentSkin = new Transformation[body.size()];
            boolean outline = GodSettings.vesselOutline();
            for (int i = 0; i < body.size(); i++) {
                ServantBody.Part p = body.get(i);
                Transformation t = ServantBody.transform(p, yaw);
                ItemDisplay d = w.spawn(feet, ItemDisplay.class, e -> {
                    tag(e);
                    e.setVisibleByDefault(false);
                    e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    ItemStack item = new ItemStack(Material.PAPER);
                    ItemMeta meta = item.getItemMeta();
                    meta.setItemModel(NamespacedKey.fromString(ServantPack.modelId(kind, p.key())));
                    item.setItemMeta(meta);
                    e.setItemStack(item);
                    e.setTransformation(t);
                    if (p.shape() == ServantSkin.Shape.GLOW) e.setBillboard(Display.Billboard.VERTICAL);
                    if (outline && ServantSkin.def(kind, p.key()).outline()) {
                        e.setGlowing(true);
                        e.setGlowColorOverride(Color.fromRGB(kind.glow));
                    }
                });
                sentSkin[i] = t;
                skin.add(d);
                root.addPassenger(d);
            }
            hitbox = w.spawn(feet, Interaction.class, i -> {
                tag(i);
                i.setVisibleByDefault(false);
                i.setInteractionWidth((float) Math.max(mob.getWidth(), 0.8));
                i.setInteractionHeight((float) Math.max(mob.getHeight(), ServantBody.top(kind) - 0.3));
                i.setResponsive(true);
            });
            root.addPassenger(hitbox);
        }

        /**
         * Shows each player in its world the painted body (and its hitbox) if their client loaded the
         * resource pack, else the vanilla mob and its block rig.
         */
        void refreshViewers() {
            if (skin.isEmpty()) {
                plainViewers = 1;
                return;
            }
            GPTGOD plugin = GPTGOD.get();
            // What a player was shown stays with them while they are online, wherever they wander.
            viewers.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
            int painted = 0, plain = 0;
            for (Player p : mob.getWorld().getPlayers()) {
                boolean has = VesselPackHost.has(p);
                if (has) painted++;
                else plain++;
                Boolean was = viewers.put(p.getUniqueId(), has);
                if (was != null && was == has) continue;
                if (was == null && !has) continue; // what an entity shows by default
                for (ItemDisplay d : skin) {
                    if (has) p.showEntity(plugin, d);
                    else p.hideEntity(plugin, d);
                }
                if (has) p.showEntity(plugin, hitbox);
                else p.hideEntity(plugin, hitbox);
                for (BlockDisplay d : parts) {
                    if (has) p.hideEntity(plugin, d);
                    else p.showEntity(plugin, d);
                }
                if (has) p.hideEntity(plugin, mob);
                else p.showEntity(plugin, mob);
            }
            skinViewers = painted;
            plainViewers = plain;
        }

        /** A blow landed on it: a flash of red in its outline and a few sparks. */
        void hurt() {
            Location at = mob.getLocation().add(0, kind.height() * 0.6, 0);
            at.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, at, 3, 0.25, 0.3, 0.25, 0.05);
            if (skin.isEmpty() || !GodSettings.vesselOutline()) return;
            hurtUntil = tick + 8;
            tint(Color.fromRGB(0xFF3020));
        }

        private void tint(Color c) {
            for (ItemDisplay d : skin) {
                if (d.isGlowing()) d.setGlowColorOverride(c);
            }
        }

        private void setupMob(Mob m) {
            m.getPersistentDataContainer().set(key(TAG), PersistentDataType.STRING, kind.id);
            m.getPersistentDataContainer().set(key("servant_god"), PersistentDataType.STRING, god.id);
            m.setPersistent(false);
            m.setRemoveWhenFarAway(false);
            m.setAware(false);
            m.setGravity(false);
            m.setCanPickupItems(false);
            m.customName(Component.text(name, TextColor.color(kind.glow)));
            m.setCustomNameVisible(false);
            set(m, Attribute.MAX_HEALTH, kind.health);
            m.setHealth(kind.health);
            set(m, Attribute.SCALE, kind.scale);
            set(m, Attribute.KNOCKBACK_RESISTANCE, 1);
            if (kind == ServantKind.ANGEL) m.setGlowing(true);
            if (m instanceof Vex v) v.setLimitedLifetime(false);
            if (m instanceof Villager v) {
                v.setProfession(Villager.Profession.CLERIC);
                v.setVillagerLevel(5);
            }
            if (m.getEquipment() != null) {
                m.getEquipment().clear();
                if (kind == ServantKind.SHADE) {
                    m.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_HOE));
                    m.getEquipment().setItemInMainHandDropChance(0);
                }
            }
            m.setRotation(yaw, 0);
        }

        private static void set(LivingEntity e, Attribute a, double v) {
            AttributeInstance i = e.getAttribute(a);
            if (i != null) i.setBaseValue(v);
        }

        private void tag(Entity e) {
            e.getPersistentDataContainer().set(key(TAG), PersistentDataType.STRING, kind.id);
            e.setPersistent(false);
            if (e instanceof Display d) {
                d.setBrightness(new Display.Brightness(15, 15));
                d.setShadowRadius(0);
                d.setShadowStrength(0);
            }
        }

        public Location location() {
            return mob.getLocation();
        }

        boolean alive() {
            return mob.isValid() && !mob.isDead() && root.isValid();
        }

        Particle particle() {
            Particle p = Spectacle.particle(kind.particle);
            return p != null && p.getDataType() == Void.class ? p : Particle.END_ROD;
        }

        // ---- Orders -------------------------------------------------------------------------------------

        /** Gives an order; returns what it will do, phrased for the god. */
        public String order(ServantKind.Order o, Entity on, Location where, String words) {
            long now = System.currentTimeMillis();
            switch (o) {
                case ATTACK -> {
                    if (on == null) throw new IllegalStateException("Name who it should attack.");
                    if (kind.damage <= 0) throw new IllegalStateException("A " + kind.id + " does not fight.");
                    if (order != ServantKind.Order.ATTACK) before = order;
                    target = on.getUniqueId();
                    orderUntil = now + (on instanceof Player ? 60_000L : 120_000L);
                }
                case FOLLOW -> {
                    target = on == null ? null : on.getUniqueId();
                    if (on == null && LivingAccess.body(god) == null) {
                        throw new IllegalStateException("Follow whom? You have no body to follow; name a mortal.");
                    }
                }
                case GUARD, PATROL, STAY, GO -> {
                    spot = where != null ? where.clone() : mob.getLocation();
                    target = null;
                    waypoint = null;
                }
                case PREACH -> {
                    spot = where != null ? where.clone() : mob.getLocation();
                    sermon = words == null || words.isBlank() ? null : Bonds.clip(words, 200);
                    lastPreach = 0;
                }
            }
            order = o;
            if (o != ServantKind.Order.PREACH && words != null && !words.isBlank()) say(words);
            return doing();
        }

        public String doing() {
            Entity t = target == null ? null : Bukkit.getEntity(target);
            String tn = t == null ? null : t instanceof Player p ? p.getName() : t.getType().name().toLowerCase(Locale.ROOT);
            return switch (order) {
                case ATTACK -> "attacking " + (tn == null ? "nothing" : tn);
                case FOLLOW -> "following " + (tn == null ? "your body" : tn);
                case GUARD -> "guarding " + where(spot);
                case PATROL -> "patrolling around " + where(spot);
                case PREACH -> "preaching near " + where(spot);
                case GO -> "going to " + where(spot);
                case STAY -> "waiting at " + where(spot);
            };
        }

        private static String where(Location l) {
            return l == null ? "here" : "(" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + ")";
        }

        /** Shows words above it and in chat to mortals nearby. */
        public void say(String words) {
            String text = Bonds.clip(words, 180);
            bubble.text(Component.text(text, TextColor.color(0xFFFFFF)));
            bubbleUntil = System.currentTimeMillis() + 6000;
            Component line = Component.text("[" + name + "] ", TextColor.color(kind.glow))
                    .append(Component.text(text, TextColor.color(0xE8E8E8)));
            for (Player p : mob.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(mob.getLocation()) < 24 * 24) p.sendMessage(line);
            }
        }

        // ---- Every 2 ticks ------------------------------------------------------------------------------

        void tick(long now) {
            if (bubbleUntil > 0 && now > bubbleUntil) {
                bubble.text(Component.empty());
                bubbleUntil = 0;
            }
            if (tick % 10 == 0) refreshViewers();
            if (hurtUntil != 0 && tick >= hurtUntil) {
                hurtUntil = 0;
                tint(Color.fromRGB(kind.glow));
            }
            Location here = mob.getLocation();
            Entity t = target == null ? null : Bukkit.getEntity(target);
            if (order == ServantKind.Order.ATTACK && (t == null || !t.isValid() || t.isDead() || now > orderUntil
                    || (t instanceof Player p && (!p.isOnline() || p.getGameMode() == GameMode.SPECTATOR
                            || p.getGameMode() == GameMode.CREATIVE)))) {
                order = before;
                target = null;
                t = null;
            }
            // Guards and patrols defend their ground from monsters (and from those their god despises).
            if (kind.damage > 0 && (order == ServantKind.Order.GUARD || order == ServantKind.Order.PATROL
                    || order == ServantKind.Order.FOLLOW) && tick % 20 == 0) {
                Entity threat = threat(here);
                if (threat != null) {
                    before = order;
                    order = ServantKind.Order.ATTACK;
                    target = threat.getUniqueId();
                    orderUntil = now + 30_000;
                    t = threat;
                }
            }
            Location dest = destination(here, t, now);
            move(here, dest, t);
            if (order == ServantKind.Order.ATTACK && t instanceof LivingEntity le) strike(le, now);
            if (kind == ServantKind.PRIEST || order == ServantKind.Order.PREACH) preach(now);
            if (kind == ServantKind.ANGEL && now - lastHeal > 10_000) heal(now);
            if (tick % 6 == 0) aura(here);
            shape();
        }

        /** The air about it: motes of light, embers, souls, letters, sparks. */
        private void aura(Location here) {
            World w = here.getWorld();
            double h = kind.height();
            Location mid = here.clone().add(0, h * 0.6, 0);
            w.spawnParticle(particle(), mid, 1, 0.35, h * 0.35, 0.35, 0.01);
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            switch (kind) {
                case ANGEL -> {
                    // Light falls from under its wings.
                    Vector side = new Vector(Math.cos(Math.toRadians(yaw)), 0, Math.sin(Math.toRadians(yaw)))
                            .multiply((rnd.nextBoolean() ? 1 : -1) * rnd.nextDouble(0.6, 1.6));
                    w.spawnParticle(Particle.END_ROD, here.clone().add(side).add(0, h * 0.7, 0), 0, 0, -0.04, 0, 1);
                }
                case IMP -> {
                    w.spawnParticle(Particle.SMALL_FLAME, mid, 1, 0.2, 0.25, 0.2, 0.01);
                    if (rnd.nextInt(4) == 0) w.spawnParticle(Particle.LAVA, mid, 1, 0.1, 0.1, 0.1, 0);
                }
                case SHADE -> {
                    w.spawnParticle(Particle.SCULK_SOUL, here.clone().add(0, 0.25, 0), 1, 0.3, 0.1, 0.3, 0.01);
                    w.spawnParticle(Particle.ASH, mid, 3, 0.4, h * 0.4, 0.4, 0);
                }
                case PRIEST -> w.spawnParticle(Particle.ENCHANT, here.clone().add(0, h * 0.75, 0), 3, 0.5, 0.4, 0.5,
                        0.6);
                case GUARD -> {
                    if (rnd.nextInt(3) == 0) {
                        w.spawnParticle(Particle.ELECTRIC_SPARK, here.clone().add(0, h * 0.58, 0), 2, 0.3, 0.2, 0.3,
                                0.02);
                    }
                }
            }
        }

        private Entity threat(Location here) {
            double r = order == ServantKind.Order.PATROL ? 14 : 10;
            Location c = order == ServantKind.Order.FOLLOW ? here : spot;
            if (c == null || !c.getWorld().equals(here.getWorld())) c = here;
            Entity best = null;
            double bestD = r * r;
            for (Entity e : c.getWorld().getNearbyEntities(c, r, 6, r)) {
                boolean hostile = e instanceof Monster && !isServantEntity(e);
                boolean hated = e instanceof Player p && LivingSettings.servantsAttackPlayers()
                        && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                        && god.memory().favorOf(p.getName()) <= -5;
                if (!hostile && !hated) continue;
                double d = e.getLocation().distanceSquared(c);
                if (d < bestD) {
                    best = e;
                    bestD = d;
                }
            }
            return best;
        }

        private Location destination(Location here, Entity t, long now) {
            switch (order) {
                case ATTACK -> {
                    return t == null ? here : t.getLocation();
                }
                case FOLLOW -> {
                    Location lead = t != null && t.isValid() ? t.getLocation() : LivingAccess.body(god);
                    if (lead == null) return here;
                    Vector back = lead.getDirection().setY(0);
                    if (back.lengthSquared() < 1e-4) back = new Vector(0, 0, 1);
                    back.normalize().multiply(-2.5).add(new Vector(1.5 * (id.hashCode() % 2 == 0 ? 1 : -1), 0, 0));
                    return lead.clone().add(back);
                }
                case PATROL -> {
                    if (waypoint == null || now - lastWaypoint > 9000 || here.distanceSquared(waypoint) < 2) {
                        ThreadLocalRandom rnd = ThreadLocalRandom.current();
                        waypoint = spot.clone().add(rnd.nextDouble(-14, 14), 0, rnd.nextDouble(-14, 14));
                        lastWaypoint = now;
                    }
                    return waypoint;
                }
                case PREACH -> {
                    Player near = nearestPlayer(here, 16);
                    if (near != null && near.getLocation().distanceSquared(here) > 16) {
                        Vector to = here.toVector().subtract(near.getLocation().toVector());
                        if (to.lengthSquared() > 1e-4) to.normalize().multiply(3);
                        return near.getLocation().add(to);
                    }
                    return spot != null && spot.getWorld().equals(here.getWorld()) && here.distanceSquared(spot) > 64
                            ? spot : here;
                }
                case GO -> {
                    if (spot != null && here.getWorld().equals(spot.getWorld()) && here.distanceSquared(spot) < 2) {
                        order = ServantKind.Order.STAY;
                    }
                    return spot;
                }
                default -> {
                    // GUARD and STAY: hold the spot, drifting a little.
                    if (spot == null || !spot.getWorld().equals(here.getWorld())) return here;
                    return here.distanceSquared(spot) > (order == ServantKind.Order.GUARD ? 9 : 1) ? spot : here;
                }
            }
        }

        private void move(Location here, Location dest, Entity t) {
            if (dest == null) return;
            if (!dest.getWorld().equals(here.getWorld()) || dest.distanceSquared(here) > 48 * 48) {
                blink(dest);
                return;
            }
            Vector to = dest.toVector().subtract(here.toVector());
            double keep = order == ServantKind.Order.ATTACK ? kind.reach * 0.7 : 0.6;
            double flat = Math.hypot(to.getX(), to.getZ());
            double step = Math.min(kind.speed * 2, Math.max(0, flat - keep));
            moving = step > 0.02;
            Location next = here.clone();
            float face = yaw;
            if (flat > 1e-3) face = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
            if (t != null && order == ServantKind.Order.ATTACK || (!moving && order == ServantKind.Order.PREACH)) {
                Entity look = t != null ? t : nearestPlayer(here, 10);
                if (look != null) {
                    Vector v = look.getLocation().toVector().subtract(here.toVector());
                    face = (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ()));
                }
            }
            if (moving) next.add(to.getX() / flat * step, 0, to.getZ() / flat * step);
            if (kind.flies) {
                double ground = groundY(next, here.getY());
                double hover = kind == ServantKind.IMP ? 2.0 : 1.3;
                double want = Math.max(ground + hover, order == ServantKind.Order.ATTACK ? dest.getY() + 0.6 : ground + hover);
                double bob = 0.12 * Math.sin((tick + id.hashCode()) * 0.12);
                double dy = want + bob - here.getY();
                next.setY(here.getY() + Math.max(-0.25, Math.min(0.25, dy)));
            } else {
                double y = walkY(next, here.getY());
                if (Double.isNaN(y)) {
                    // A wall: try sidestepping, then blink over it after a while.
                    if (++stuck > 15) {
                        blink(dest);
                        stuck = 0;
                    }
                    next = here.clone();
                } else {
                    stuck = 0;
                    next.setY(y);
                }
            }
            yaw = approach(yaw, face, 25);
            next.setYaw(yaw);
            next.setPitch(0);
            if (next.distanceSquared(here) > 1e-6 || Math.abs(here.getYaw() - yaw) > 0.5) {
                mob.teleport(next);
                mob.setBodyYaw(yaw);
            }
            Location feet = mob.getLocation();
            feet.setYaw(0);
            feet.setPitch(0);
            if (feet.distanceSquared(root.getLocation()) > 1e-6) root.teleport(feet);
        }

        private void blink(Location dest) {
            Location here = mob.getLocation();
            here.getWorld().spawnParticle(Particle.REVERSE_PORTAL, here.clone().add(0, kind.height() / 2, 0), 30, 0.3,
                    0.6, 0.3, 0.05);
            Location to = HolyDayRunner.ground(dest, 2);
            if (to == null) to = dest.clone();
            if (kind.flies) to.add(0, 1.3, 0);
            to.setYaw(yaw);
            // Riders do not follow across worlds: take them off, move everything, put them back.
            boolean sameWorld = to.getWorld().equals(here.getWorld());
            List<Entity> riders = new ArrayList<>(root.getPassengers());
            if (!sameWorld) riders.forEach(root::removePassenger);
            mob.teleport(to);
            Location feet = to.clone();
            feet.setYaw(0);
            root.teleport(feet);
            if (!sameWorld) {
                for (Entity r : riders) {
                    r.teleport(feet);
                    root.addPassenger(r);
                }
            }
            to.getWorld().spawnParticle(Particle.REVERSE_PORTAL, to.clone().add(0, kind.height() / 2, 0), 30, 0.3, 0.6,
                    0.3, 0.05);
            to.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.4f);
        }

        /** The ground under a point, searching a little up and down from the last height. */
        private static double groundY(Location at, double from) {
            World w = at.getWorld();
            int x = at.getBlockX(), z = at.getBlockZ();
            int y0 = (int) Math.floor(from);
            for (int dy = 0; dy <= 8; dy++) {
                if (w.getBlockAt(x, y0 - dy - 1, z).getType().isSolid()) return y0 - dy;
            }
            return y0 - 8;
        }

        /** The height a walker stands at after a step, or NaN if the step is blocked. */
        private double walkY(Location at, double from) {
            World w = at.getWorld();
            int x = at.getBlockX(), z = at.getBlockZ();
            int y0 = (int) Math.floor(from + 0.01);
            int tall = (int) Math.ceil(kind.height());
            for (int dy : new int[] { 0, 1, -1, -2, -3 }) {
                int y = y0 + dy;
                if (!w.getBlockAt(x, y - 1, z).getType().isSolid()) continue;
                boolean clear = true;
                for (int k = 0; k < tall && clear; k++) clear = w.getBlockAt(x, y + k, z).isPassable();
                if (clear) return y;
            }
            // Nothing solid below (falling, water): sink gently.
            if (w.getBlockAt(x, y0 - 1, z).isPassable() && w.getBlockAt(x, y0, z).isPassable()) return from - 0.4;
            return Double.NaN;
        }

        private void strike(LivingEntity t, long now) {
            if (now - lastStrike < 1300) return;
            Location here = mob.getLocation();
            if (!t.getWorld().equals(here.getWorld())) return;
            double reach = kind.reach + (kind.scale > 1.5 ? 0.6 : 0);
            Location tc = t.getLocation().add(0, t.getHeight() / 2, 0);
            Location mc = here.clone().add(0, kind.height() / 2, 0);
            if (tc.distance(mc) > reach + t.getWidth() / 2) return;
            lastStrike = now;
            strikeTick = tick;
            mob.swingMainHand();
            t.damage(kind.damage, mob);
            World w = here.getWorld();
            switch (kind) {
                case ANGEL -> {
                    w.spawnParticle(Particle.END_ROD, tc, 18, 0.2, 0.4, 0.2, 0.08);
                    w.playSound(tc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.6f);
                }
                case IMP -> {
                    w.spawnParticle(Particle.FLAME, tc, 14, 0.2, 0.3, 0.2, 0.04);
                    t.setFireTicks(Math.max(t.getFireTicks(), 50));
                    w.playSound(tc, Sound.ENTITY_BLAZE_HURT, 0.8f, 1.6f);
                }
                case SHADE -> {
                    w.spawnParticle(Particle.SOUL, tc, 12, 0.2, 0.4, 0.2, 0.03);
                    t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
                    w.playSound(tc, Sound.ENTITY_WITHER_SKELETON_HURT, 0.8f, 0.6f);
                }
                case GUARD -> {
                    t.setVelocity(t.getVelocity().add(new Vector(0, 0.55, 0)));
                    w.playSound(tc, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 1f);
                }
                default -> {
                }
            }
        }

        private void preach(long now) {
            if (now - lastPreach < 45_000) return;
            Player near = nearestPlayer(mob.getLocation(), 16);
            if (near == null) return;
            lastPreach = now;
            String words = sermon;
            if (words == null) {
                List<String> laws = god.memory().commandments();
                if (laws.isEmpty()) {
                    words = "Hear me! " + god.displayName() + " watches over you. Pray, and be heard.";
                } else {
                    sermonIndex = sermonIndex % laws.size();
                    words = "Hear the law of " + god.displayName() + ": " + laws.get(sermonIndex++);
                }
            }
            say(words);
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 0.8f, 0.8f);
        }

        private void heal(long now) {
            lastHeal = now;
            for (Player p : mob.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(mob.getLocation()) > 8 * 8) continue;
                if (god.memory().favorOf(p.getName()) < 5) continue;
                AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                double cap = max == null ? 20 : max.getValue();
                if (p.getHealth() >= cap) continue;
                p.setHealth(Math.min(cap, p.getHealth() + 2));
                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 2, 0.3, 0.2, 0.3, 0);
            }
        }

        private Player nearestPlayer(Location at, double r) {
            Player best = null;
            double bd = r * r;
            for (Player p : at.getWorld().getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR) continue;
                double d = p.getLocation().distanceSquared(at);
                if (d < bd) {
                    best = p;
                    bd = d;
                }
            }
            return best;
        }

        /**
         * Updates the pieces of whichever rigs someone can see (wingbeat, sway, stride, swing, turning);
         * only pieces that moved are sent.
         */
        private void shape() {
            long age = tick;
            if (plainViewers > 0) {
                List<ServantRig.Piece> pieces = ServantRig.pieces(kind, age, moving);
                for (int i = 0; i < pieces.size() && i < parts.size(); i++) {
                    Transformation t = ServantRig.transform(pieces.get(i), yaw);
                    if (!differs(t, sent[i])) continue;
                    BlockDisplay d = parts.get(i);
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(2);
                    d.setTransformation(t);
                    sent[i] = t;
                }
            }
            if (skinViewers > 0) {
                long since = tick - strikeTick;
                int strike = strikeTick != Long.MIN_VALUE && since < ServantBody.STRIKE_TICKS ? (int) since : -1;
                List<ServantBody.Part> body = ServantBody.parts(kind, new ServantBody.Motion(age, moving, strike));
                for (int i = 0; i < body.size() && i < skin.size(); i++) {
                    Transformation t = ServantBody.transform(body.get(i), yaw);
                    if (!differs(t, sentSkin[i])) continue;
                    ItemDisplay d = skin.get(i);
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(2);
                    d.setTransformation(t);
                    sentSkin[i] = t;
                }
            }
        }

        // ---- Ending -------------------------------------------------------------------------------------

        /** Sends it away with a flourish, and tells its god why. */
        public void dismiss(String why) {
            if (!ALL.remove(this)) return;
            if (mob.isValid()) {
                Location at = mob.getLocation().add(0, kind.height() / 2, 0);
                at.getWorld().spawnParticle(particle(), at, 30, 0.4, kind.height() / 2, 0.4, 0.06);
                at.getWorld().playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.3f);
            }
            destroy(false);
            if (why != null) tellGod("Your " + kind.id + " \"" + name + "\" has left: " + why + ".", null);
        }

        void faded(String why) {
            if (!ALL.remove(this)) return;
            destroy(false);
            tellGod("Your " + kind.id + " \"" + name + "\" " + why + ".", null);
        }

        /** It was slain in the world. */
        void slain(Entity by) {
            if (!ALL.remove(this)) return;
            Location at = mob.getLocation().add(0, kind.height() / 2, 0);
            at.getWorld().spawnParticle(Particle.EXPLOSION, at, 1);
            at.getWorld().spawnParticle(particle(), at, 50, 0.5, 0.6, 0.5, 0.1);
            destroy(false);
            String who = by instanceof Player p ? p.getName() : by == null ? "something" : by.getType().name()
                    .toLowerCase(Locale.ROOT);
            tellGod("Your " + kind.id + " \"" + name + "\" was SLAIN by " + who + ".", by instanceof Player
                    ? who + " slew your servant" : null);
            Journal.add(Journal.Kind.INFO, "servant " + name + " slain by " + who);
        }

        void destroy(boolean removeFromList) {
            if (removeFromList) ALL.remove(this);
            for (BlockDisplay d : parts) d.remove();
            for (ItemDisplay d : skin) d.remove();
            if (hitbox != null) hitbox.remove();
            for (Entity e : new ArrayList<>(root.getPassengers())) e.remove();
            bubble.remove();
            root.remove();
            if (mob.isValid()) mob.remove();
        }

        private void tellGod(String text, String attention) {
            god.run(() -> EventLogger.addLoggable(new GenericEventLoggable(text, attention)));
        }
    }

    static boolean differs(Transformation a, Transformation b) {
        if (a == null || b == null) return true;
        return a.getTranslation().distanceSquared(b.getTranslation()) > 1e-5
                || a.getScale().distanceSquared(b.getScale()) > 1e-5
                || Math.abs(a.getLeftRotation().dot(b.getLeftRotation())) < 0.99999f;
    }

    static float approach(float from, float to, float max) {
        float d = ((to - from) % 360 + 540) % 360 - 180;
        if (Math.abs(d) <= max) return to;
        return from + Math.signum(d) * max;
    }
}
