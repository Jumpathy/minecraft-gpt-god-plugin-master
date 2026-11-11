package net.bigyous.gptgodmc.god.living;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.Godslayer;
import net.bigyous.gptgodmc.god.Heart;
import net.bigyous.gptgodmc.god.Journal;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.loggables.GenericEventLoggable;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

/** Runs the holy days in the world: bars, titles, raids, hunts, festivals, omens, wars and their ends. */
public final class HolyDayRunner {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final String TAG = "holyday";

    private static HolyDays store = new HolyDays();
    private static Path file;
    private static boolean dirty;
    private static long lastSave;

    private static final Map<Integer, BossBar> BARS = new HashMap<>();
    private static final Map<Integer, Set<UUID>> SHOWN = new HashMap<>();
    /** Raid mobs and quarries, by holy day. */
    private static final Map<Integer, List<UUID>> MOBS = new HashMap<>();
    /** Mortals who died while a raid on them ran (they did not survive it). */
    private static final Map<Integer, Set<String>> FALLEN = new HashMap<>();

    private HolyDayRunner() {
    }

    public static HolyDays store() {
        return store;
    }

    static NamespacedKey key() {
        return new NamespacedKey(GPTGOD.get(), TAG);
    }

    // ---- Storage -------------------------------------------------------------------------------------

    static void load(Path where) {
        file = where;
        store = new HolyDays();
        if (where != null && Files.exists(where)) {
            try {
                HolyDays read = GSON.fromJson(Files.readString(where, StandardCharsets.UTF_8), HolyDays.class);
                if (read != null) store = normalized(read);
            } catch (IOException | RuntimeException e) {
                GPTGOD.LOGGER.warn("Could not read " + where + "; holy days start empty", e);
            }
        }
    }

    private static HolyDays normalized(HolyDays h) {
        if (h.days == null) h.days = new ArrayList<>();
        if (h.lastProclaimed == null) h.lastProclaimed = new java.util.LinkedHashMap<>();
        if (h.lastNudged == null) h.lastNudged = new java.util.LinkedHashMap<>();
        for (HolyDays.Day d : h.days) {
            if (d.scores == null) d.scores = new java.util.LinkedHashMap<>();
            if (d.sides == null) d.sides = new java.util.LinkedHashMap<>();
            if (d.sidePoints == null) d.sidePoints = new java.util.LinkedHashMap<>();
            if (d.counts == null) d.counts = new ArrayList<>();
        }
        return h;
    }

    public static void changed() {
        dirty = true;
    }

    static void save(boolean force) {
        long now = System.currentTimeMillis();
        if (file == null || !dirty || (!force && now - lastSave < 20_000)) return;
        dirty = false;
        lastSave = now;
        String json;
        synchronized (store) {
            json = GSON.toJson(store);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            GPTGOD.LOGGER.warn("Could not save " + file, e);
        }
    }

    static void shutdown() {
        for (Integer id : new ArrayList<>(BARS.keySet())) hideBar(id);
        for (Integer id : new ArrayList<>(MOBS.keySet())) clearMobs(id);
        save(true);
    }

    // ---- Proclaiming ----------------------------------------------------------------------------------

    /** Validates and proclaims a holy day for the current god; throws IllegalStateException with the reason. */
    public static HolyDays.Day proclaim(HolyDays.Request r, Realm me, Realm rival, Player target) {
        long now = System.currentTimeMillis();
        String no = store.refusal(r, LivingSettings.eventsMaxPerGod(), LivingSettings.eventsMaxTotal(), now);
        if (no != null) throw new IllegalStateException(no);
        HolyDays.Day d = store.proclaim(r, now);
        changed();
        if (r.kind() == HolyDays.Kind.WAR && rival != null) {
            Rivalry.setPact(me, rival, Bonds.Pact.WAR);
            Rivalry.bonds(rival).move(me.id, me.displayName(), -20, "declared holy war on you", now);
            Rivalry.bonds(me).move(rival.id, rival.displayName(), -10, "you declared holy war on it", now);
            rival.memory().heart.feel(Heart.Feeling.WRATH, 25, me.displayName() + " declared holy war on you", now);
            Rivalry.tell(rival, me.displayName() + " DECLARES HOLY WAR on you: \"" + d.title + "\". " + d.rules
                    + " Mortals choose sides with /side; it " + HolyDays.countdown(d, now) + ". Rally your faithful, "
                    + "award points (holy_day score) and taunt your foe.", me.displayName() + " declared holy war on you");
        }
        Journal.add(Journal.Kind.INFO, "proclaimed holy day #" + d.id + " (" + d.kind + "): " + d.title);
        announce(d, me, now, false);
        if (d.running()) start(d, now);
        return d;
    }

    /** The first announcement, to everyone in the god's worlds (and the rival's). */
    private static void announce(HolyDays.Day d, Realm me, long now, boolean started) {
        String head = d.kind().icon + " " + d.title;
        String sub = started ? "begins now!" : HolyDays.countdown(d, now);
        Component line = Component.text(head + " ", TextColor.color(color(d)), TextDecoration.BOLD)
                .append(Component.text("— " + d.godName + (d.kind() == HolyDays.Kind.WAR ? " wars on " + d.rivalName
                        : " proclaims a " + d.kind().id) + ", " + sub, NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false));
        List<Component> extra = new ArrayList<>();
        if (!d.rules.isBlank()) extra.add(Component.text("  " + d.rules, NamedTextColor.WHITE));
        if (!d.counts.isEmpty()) extra.add(Component.text("  Points for: " + String.join(", ", d.counts.stream()
                .map(c -> c.replace('_', ' ')).toList()), NamedTextColor.YELLOW));
        if (d.reward != null && !d.reward.isBlank()) extra.add(Component.text("  Reward: " + d.reward, NamedTextColor.GOLD));
        if (d.consequence != null && !d.consequence.isBlank()) {
            extra.add(Component.text("  Beware: " + d.consequence, NamedTextColor.RED));
        }
        if (d.kind() == HolyDays.Kind.WAR) {
            extra.add(Component.text("  Choose a side: /side " + d.godName + "  or  /side " + d.rivalName,
                    NamedTextColor.AQUA));
        }
        Title title = Title.title(Component.text(head, TextColor.color(color(d))),
                Component.text(started ? "It begins!" : sub, NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofSeconds(1)));
        for (Player p : viewers(d)) {
            p.sendMessage(line);
            extra.forEach(p::sendMessage);
            p.showTitle(title);
            p.playSound(p.getLocation(), started ? Sound.EVENT_RAID_HORN : Sound.BLOCK_BELL_USE, started ? 0.5f : 1f,
                    started ? 1.2f : 0.7f);
        }
    }

    // ---- The clock ------------------------------------------------------------------------------------

    /** Every second, on the server thread. */
    static void tick() {
        long now = System.currentTimeMillis();
        for (HolyDays.Day d : store.live()) {
            try {
                HolyDays.Step s = store.step(d, now);
                if (s == HolyDays.Step.STARTED) start(d, now);
                else if (s == HolyDays.Step.ENDED) finish(d, null);
                else if (d.running() && now >= d.nextBeat) beat(d, now);
                if (d.live()) showBar(d, now);
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("Holy day #" + d.id + " failed", e);
            }
        }
        // Bars of days that ended elsewhere (cancelled by a tool).
        for (Integer id : new ArrayList<>(BARS.keySet())) {
            HolyDays.Day d = store.find(id);
            if (d == null || !d.live()) hideBar(id);
        }
        save(false);
    }

    private static void start(HolyDays.Day d, long now) {
        changed();
        Realm god = Realm.byId(d.god);
        if (god != null) announce(d, god, now, true);
        d.nextBeat = now + 1000;
        tellGods(d, "Your holy day #" + d.id + " \"" + d.title + "\" has begun.", null);
    }

    private static void beat(HolyDays.Day d, long now) {
        switch (d.kind()) {
            case FESTIVAL -> {
                d.nextBeat = now + 25_000;
                List<Player> vs = viewers(d);
                if (!vs.isEmpty()) firework(vs.get(ThreadLocalRandom.current().nextInt(vs.size())).getLocation(), d);
            }
            case RAID -> raidWave(d, now);
            case HUNT -> hunt(d, now);
            case OMEN -> omen(d, now);
            case WAR -> {
                d.nextBeat = now + 30_000;
                // Side members glimmer in their god's colours now and then.
                for (Player p : viewers(d)) {
                    String side = d.sides.get(p.getName());
                    if (side == null) continue;
                    org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(
                            side.equalsIgnoreCase(d.god) ? Color.fromRGB(0xFFC94A) : Color.fromRGB(0x7AB8FF), 1.2f);
                    p.getWorld().spawnParticle(org.bukkit.Particle.DUST, p.getLocation().add(0, 0.2, 0), 14, 0.4, 0.05,
                            0.4, 0, dust);
                }
            }
            default -> d.nextBeat = now + 60_000;
        }
    }

    // ---- Kinds ------------------------------------------------------------------------------------------

    private static void firework(Location near, HolyDays.Day d) {
        World w = near.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location at = near.clone().add(rnd.nextDouble(-8, 8), 14 + rnd.nextDouble(4), rnd.nextDouble(-8, 8));
        Firework fw = w.spawn(at, Firework.class, f -> {
            FireworkMeta m = f.getFireworkMeta();
            m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.fromRGB(color(d)), Color.WHITE).withFade(Color.fromRGB(0xFFD86B)).trail(true)
                    .build());
            m.setPower(0);
            f.setFireworkMeta(m);
            f.setPersistent(false);
        });
        fw.detonate();
    }

    private static void raidWave(HolyDays.Day d, long now) {
        int waves = LivingSettings.raidWaves();
        long span = Math.max(30_000, (d.endsAt - d.startsAt) / (waves + 1));
        d.nextBeat = now + span;
        if (d.wave >= waves) return;
        Player target = d.target == null ? null : Bukkit.getPlayerExact(d.target);
        if (target == null || target.isDead() || target.getGameMode() == GameMode.SPECTATOR
                || target.getGameMode() == GameMode.CREATIVE) {
            return; // the wave waits for them
        }
        d.wave++;
        changed();
        List<UUID> mobs = MOBS.computeIfAbsent(d.id, k -> new ArrayList<>());
        mobs.removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            return e == null || !e.isValid();
        });
        int helpers = (int) target.getWorld().getPlayers().stream()
                .filter(p -> p != target && p.getLocation().distanceSquared(target.getLocation()) < 32 * 32).count();
        int count = Math.min(LivingSettings.raidMaxMobs(), 2 + d.wave + Math.min(3, helpers)) - mobs.size();
        List<EntityType> pool = new ArrayList<>(List.of(EntityType.ZOMBIE, EntityType.SKELETON));
        if (d.wave >= 2) pool.addAll(List.of(EntityType.SPIDER, EntityType.HUSK, EntityType.STRAY));
        if (d.wave >= 3) pool.addAll(List.of(EntityType.VINDICATOR, EntityType.WITCH));
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Location at = ground(target.getLocation(), 9 + rnd.nextDouble(7));
            if (at == null) continue;
            EntityType type = pool.get(rnd.nextInt(pool.size()));
            Entity e = at.getWorld().spawnEntity(at, type);
            tag(e);
            e.customName(Component.text("Raider of " + d.godName, NamedTextColor.DARK_RED));
            if (e instanceof LivingEntity le && le.getEquipment() != null) {
                le.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
                le.getEquipment().setHelmetDropChance(0);
            }
            if (e instanceof Mob m) m.setTarget(target);
            at.getWorld().spawnParticle(org.bukkit.Particle.LARGE_SMOKE, at.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
            mobs.add(e.getUniqueId());
        }
        target.getWorld().playSound(target.getLocation(), Sound.EVENT_RAID_HORN, 1f, 0.8f);
        target.sendActionBar(Component.text("⚔ Wave " + d.wave + " of " + waves + " — " + d.godName + "'s raiders come!",
                NamedTextColor.RED));
    }

    private static void hunt(HolyDays.Day d, long now) {
        d.nextBeat = now + 5_000;
        List<UUID> mobs = MOBS.computeIfAbsent(d.id, k -> new ArrayList<>());
        Entity quarry = mobs.isEmpty() ? null : Bukkit.getEntity(mobs.get(0));
        if (quarry != null && quarry.isValid()) {
            // A trail so hunters can follow it.
            quarry.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, quarry.getLocation().add(0, 1, 0), 3, 0.3, 0.4,
                    0.3, 0.01);
            return;
        }
        if (d.wave >= 2) return; // it got away twice: no more quarries
        Player near = d.target == null ? null : Bukkit.getPlayerExact(d.target);
        List<Player> vs = viewers(d);
        if (near == null && !vs.isEmpty()) near = vs.get(ThreadLocalRandom.current().nextInt(vs.size()));
        if (near == null) return;
        Location at = ground(near.getLocation(), 24 + ThreadLocalRandom.current().nextDouble(12));
        if (at == null) return;
        EntityType type;
        try {
            type = EntityType.valueOf((d.quarry == null ? "ravager" : d.quarry).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            type = EntityType.RAVAGER;
        }
        Entity e = at.getWorld().spawnEntity(at, type);
        tag(e);
        e.setGlowing(true);
        e.customName(Component.text("✦ Quarry of " + d.godName + " ✦", NamedTextColor.GOLD));
        e.setCustomNameVisible(true);
        if (e instanceof LivingEntity le) {
            AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(Math.min(120, Math.max(40, hp.getBaseValue() * 3)));
                le.setHealth(hp.getBaseValue());
            }
            le.setRemoveWhenFarAway(false);
        }
        mobs.clear();
        mobs.add(e.getUniqueId());
        d.wave++;
        changed();
        for (Player p : vs) {
            p.sendMessage(Component.text("➶ The quarry is loosed near " + near.getName() + " ("
                    + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ() + ")! Whoever slays it wins "
                    + "the hunt.", NamedTextColor.GOLD));
            p.playSound(p.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 1f, 1f);
        }
    }

    private static void omen(HolyDays.Day d, long now) {
        long span = d.endsAt - d.startsAt;
        // Six to twelve signs over the omen, but never more than one every five minutes.
        long gap = Math.max(5 * 60_000L, Math.min(30 * 60_000L, span / 8));
        d.nextBeat = now + gap;
        List<Player> vs = viewers(d);
        if (vs.isEmpty()) {
            d.nextBeat = now + 60_000;
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Player p = vs.get(rnd.nextInt(vs.size()));
        Location at = ground(p.getLocation(), 30 + rnd.nextDouble(25));
        if (at != null) at.getWorld().strikeLightningEffect(at);
        for (Player v : vs) {
            v.playSound(v.getLocation(), Sound.AMBIENT_CAVE, 1f, 0.5f);
            v.sendActionBar(Component.text("☄ " + (d.rules.isBlank() ? "An omen hangs over the world…" : d.rules),
                    NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, true));
            v.spawnParticle(org.bukkit.Particle.REVERSE_PORTAL, v.getLocation().add(0, 8, 0), 80, 6, 2, 6, 0.05);
        }
    }

    // ---- Ending -----------------------------------------------------------------------------------------

    /** Ends a day (its clock ran out, a god ended it, or a war was yielded) and hands out the results. */
    public static void finish(HolyDays.Day d, String yielded) {
        long now = System.currentTimeMillis();
        if (d.live()) {
            if (d.kind() == HolyDays.Kind.RAID && d.target != null) {
                Player t = Bukkit.getPlayerExact(d.target);
                boolean fell = FALLEN.getOrDefault(d.id, Set.of()).contains(d.target);
                if (t != null && !t.isDead() && !fell && d.wave > 0) store.credit(d.target, "survive", 1, null);
            }
            store.end(d, yielded, now);
        }
        changed();
        clearMobs(d.id);
        hideBar(d.id);
        FALLEN.remove(d.id);
        List<Player> vs = viewers(d);
        List<String> rewarded = new ArrayList<>();
        int xp = LivingSettings.rewardXp();
        if (d.kind() == HolyDays.Kind.WAR) {
            String winner = store.warWinner(d, yielded);
            Realm a = Realm.byId(d.god), b = Realm.byId(d.rival);
            for (Player p : vs) {
                String side = d.sides.get(p.getName());
                if (winner != null && winner.equalsIgnoreCase(side)) {
                    if (xp > 0) p.giveExp(xp);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 180, 0));
                    rewarded.add(p.getName());
                }
            }
            if (a != null && b != null) {
                Rivalry.setPact(a, b, Bonds.Pact.NONE);
                Realm win = winner == null ? null : winner.equalsIgnoreCase(a.id) ? a : b;
                Realm lose = win == null ? null : win == a ? b : a;
                if (win != null) {
                    win.memory().heart.feel(Heart.Feeling.GLORY, 30, "you won the holy war \"" + d.title + "\"", now);
                    lose.memory().heart.feel(Heart.Feeling.WRATH, 20, "you lost the holy war \"" + d.title + "\"", now);
                    lose.memory().heart.feel(Heart.Feeling.SORROW, 15, "you lost the holy war", now);
                    Rivalry.bonds(lose).move(win.id, win.displayName(), -10, "defeated you in holy war", now);
                }
            }
        } else if (xp > 0) {
            List<Map.Entry<String, Double>> r = store.ranking(d);
            for (int i = 0; i < Math.min(3, r.size()); i++) {
                if (r.get(i).getValue() <= 0) break;
                Player p = Bukkit.getPlayerExact(r.get(i).getKey());
                if (p == null) continue;
                p.giveExp(i == 0 ? xp : i == 1 ? xp / 2 : xp / 4);
                rewarded.add(p.getName());
            }
            Realm god = Realm.byId(d.god);
            if (god != null && !store.ranking(d).isEmpty()) {
                god.memory().heart.feel(Heart.Feeling.JOY, d.kind() == HolyDays.Kind.RAID ? 4 : 12,
                        "mortals took part in your " + d.kind().id, now);
            }
        }
        Component end = Component.text(d.kind().icon + " " + d.title + " has ended — " + d.outcome, TextColor.color(color(d)));
        for (Player p : vs) {
            p.sendMessage(end);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1f);
        }
        String results = "Your holy day #" + d.id + " \"" + d.title + "\" has ENDED: " + d.outcome + "."
                + (rewarded.isEmpty() ? "" : " The heavens gave a little experience to " + String.join(", ", rewarded) + ".")
                + (d.kind() == HolyDays.Kind.OMEN ? " The omen is fulfilled: now perform the great act it foretold."
                        : "")
                + (d.reward != null && !d.reward.isBlank() ? " You promised: " + d.reward + "." : "")
                + (d.consequence != null && !d.consequence.isBlank() ? " You warned: " + d.consequence + "." : "")
                + " Keep your word with your powers now.";
        tellGods(d, results, "your holy day \"" + d.title + "\" ended");
        save(true);
    }

    /** Ends the holy war between two gods, if any. yieldedBy: the realm that yielded, or null (no winner). */
    public static void endWar(Realm a, Realm b, Realm yieldedBy) {
        HolyDays.Day d = store.warBetween(a.id, b.id);
        if (d == null) return;
        if (yieldedBy == null) {
            store.cancel(d, System.currentTimeMillis());
            d.outcome = "ended by truce";
            changed();
            clearMobs(d.id);
            hideBar(d.id);
            for (Player p : viewers(d)) {
                p.sendMessage(Component.text("⚔ " + d.title + " ends in a truce between " + d.godName + " and "
                        + d.rivalName + ".", NamedTextColor.AQUA));
            }
            Rivalry.setPact(a, b, Bonds.Pact.TRUCE);
        } else {
            finish(d, yieldedBy.id);
        }
    }

    public static void cancel(HolyDays.Day d) {
        store.cancel(d, System.currentTimeMillis());
        changed();
        clearMobs(d.id);
        hideBar(d.id);
        for (Player p : viewers(d)) {
            p.sendMessage(Component.text(d.kind().icon + " " + d.title + " is called off.", NamedTextColor.GRAY));
        }
        if (d.kind() == HolyDays.Kind.WAR) {
            Realm a = Realm.byId(d.god), b = Realm.byId(d.rival);
            if (a != null && b != null) Rivalry.setPact(a, b, Bonds.Pact.NONE);
        }
    }

    // ---- Scoring from the world -------------------------------------------------------------------------

    /** A mortal did something that may count. */
    static void credit(Player p, String act) {
        List<String> gods = Realm.godsOf(p.getWorld()).stream().map(r -> r.id).toList();
        if (gods.isEmpty()) return;
        if (!store.credit(p.getName(), act, 1, gods).isEmpty()) changed();
    }

    /** A mortal died: raids remember it, and deaths may count against them. */
    static void died(Player p, Player killer) {
        for (HolyDays.Day d : store.live()) {
            if (d.kind() == HolyDays.Kind.RAID && p.getName().equalsIgnoreCase(d.target)) {
                FALLEN.computeIfAbsent(d.id, k -> new HashSet<>()).add(p.getName());
            }
            if (d.kind() == HolyDays.Kind.WAR && killer != null && d.running() && d.pvp) {
                String a = d.sides.get(killer.getName()), b = d.sides.get(p.getName());
                if (a != null && b != null && !a.equals(b)) {
                    store.credit(killer.getName(), "rival_kills", 1, null);
                    changed();
                }
            }
        }
        credit(p, "deaths");
        if (killer != null && killer != p) credit(killer, "player_kills");
    }

    /** A tagged mob died: the quarry falls to its hunter, and the hunt ends. */
    static void mobDied(Entity e, Player killer) {
        for (Map.Entry<Integer, List<UUID>> m : MOBS.entrySet()) {
            if (!m.getValue().remove(e.getUniqueId())) continue;
            HolyDays.Day d = store.find(m.getKey());
            if (d == null || !d.running() || d.kind() != HolyDays.Kind.HUNT) return;
            if (killer != null) {
                store.credit(killer.getName(), "quarry_kill", 1, null);
                for (Player p : viewers(d)) {
                    p.sendMessage(Component.text("➶ " + killer.getName() + " has slain the quarry!", NamedTextColor.GOLD));
                }
            }
            finish(d, null);
            return;
        }
    }

    // ---- Bars -------------------------------------------------------------------------------------------

    private static void showBar(HolyDays.Day d, long now) {
        BossBar bar = BARS.computeIfAbsent(d.id, k -> BossBar.bossBar(Component.empty(), 1f, barColor(d),
                BossBar.Overlay.PROGRESS));
        bar.name(Component.text(store.bar(d, now), TextColor.color(color(d))));
        bar.progress(HolyDays.progress(d, now));
        Set<UUID> shown = SHOWN.computeIfAbsent(d.id, k -> new HashSet<>());
        Set<UUID> next = new HashSet<>();
        for (Player p : viewers(d)) {
            next.add(p.getUniqueId());
            if (!shown.contains(p.getUniqueId())) p.showBossBar(bar);
        }
        for (UUID id : shown) {
            if (next.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(bar);
        }
        SHOWN.put(d.id, next);
    }

    private static void hideBar(int id) {
        BossBar bar = BARS.remove(id);
        Set<UUID> shown = SHOWN.remove(id);
        if (bar == null || shown == null) return;
        for (UUID u : shown) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.hideBossBar(bar);
        }
    }

    private static BossBar.Color barColor(HolyDays.Day d) {
        return switch (d.kind()) {
            case FESTIVAL -> BossBar.Color.YELLOW;
            case RAID, WAR -> BossBar.Color.RED;
            case OMEN -> BossBar.Color.PURPLE;
            case HUNT -> BossBar.Color.GREEN;
            case TRIAL -> BossBar.Color.BLUE;
            case RITE -> BossBar.Color.PINK;
        };
    }

    static int color(HolyDays.Day d) {
        return switch (d.kind()) {
            case FESTIVAL -> 0xFFD86B;
            case RAID -> 0xFF5A4A;
            case WAR -> 0xFF8A3D;
            case OMEN -> 0xB57BFF;
            case HUNT -> 0x8BE07A;
            case TRIAL -> 0x7AB8FF;
            case RITE -> 0xFF9CE0;
        };
    }

    // ---- Helpers ---------------------------------------------------------------------------------------

    /** Mortals who see a day: those in the worlds of the god (and its rival). */
    public static List<Player> viewers(HolyDays.Day d) {
        List<Player> out = new ArrayList<>();
        Realm a = Realm.byId(d.god);
        if (a != null) out.addAll(a.players());
        if (d.rival != null) {
            Realm b = Realm.byId(d.rival);
            if (b != null) {
                for (Player p : b.players()) {
                    if (!out.contains(p)) out.add(p);
                }
            }
        }
        return out;
    }

    private static void tellGods(HolyDays.Day d, String text, String attention) {
        for (String id : new String[] { d.god, d.rival }) {
            if (id == null) continue;
            Realm r = Realm.byId(id);
            if (r == null || Godslayer.isSlain(r)) continue;
            r.run(() -> EventLogger.addLoggable(new GenericEventLoggable(text, attention)));
        }
    }

    private static void tag(Entity e) {
        e.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte) 1);
        e.setPersistent(false);
    }

    static boolean tagged(Entity e) {
        return e.getPersistentDataContainer().has(key(), PersistentDataType.BYTE);
    }

    /** True when this tagged entity belongs to a day still running (strays are removed). */
    static boolean tracked(Entity e) {
        for (List<UUID> ids : MOBS.values()) {
            if (ids.contains(e.getUniqueId())) return true;
        }
        return false;
    }

    private static void clearMobs(int id) {
        List<UUID> ids = MOBS.remove(id);
        if (ids == null) return;
        for (UUID u : ids) {
            Entity e = Bukkit.getEntity(u);
            if (e != null && e.isValid()) {
                e.getWorld().spawnParticle(org.bukkit.Particle.LARGE_SMOKE, e.getLocation().add(0, 1, 0), 12, 0.3, 0.5,
                        0.3, 0.02);
                e.remove();
            }
        }
    }

    /** A standable spot on the surface about `distance` blocks from a point, or null. */
    static Location ground(Location from, double distance) {
        World w = from.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int tries = 0; tries < 8; tries++) {
            double a = rnd.nextDouble(Math.PI * 2);
            int x = (int) Math.floor(from.getX() + Math.cos(a) * distance);
            int z = (int) Math.floor(from.getZ() + Math.sin(a) * distance);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            int y = w.getHighestBlockYAt(x, z);
            // Caves and the Nether: stay near the mortal's height instead of the roof.
            if (Math.abs(y - from.getY()) > 12 || w.getEnvironment() == World.Environment.NETHER) {
                y = (int) from.getY();
                boolean found = false;
                for (int dy = 0; dy < 8 && !found; dy++) {
                    for (int s : new int[] { -1, 1 }) {
                        int yy = y + s * dy;
                        if (w.getBlockAt(x, yy - 1, z).getType().isSolid() && w.getBlockAt(x, yy, z).isPassable()
                                && w.getBlockAt(x, yy + 1, z).isPassable()) {
                            y = yy - 1;
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) continue;
            }
            Material top = w.getBlockAt(x, y, z).getType();
            if (top == Material.WATER || top == Material.LAVA) continue;
            return new Location(w, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }
}
