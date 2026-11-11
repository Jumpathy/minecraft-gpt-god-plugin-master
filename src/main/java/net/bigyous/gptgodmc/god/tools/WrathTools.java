package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Spectacle;
import net.bigyous.gptgodmc.god.Speech;
import net.kyori.adventure.text.Component;

/** Punishment and peril. */
final class WrathTools {
    private WrathTools() {
    }

    static List<GodTool> all() {
        return List.of(
                GodTool.sync("smite",
                        "Call lightning on a mortal. mode='warning' strikes harmlessly beside them (a sign of "
                                + "displeasure); mode='strike' hits them, power times. Escalate: warn first, strike "
                                + "repeat offenders.",
                        Schema.object()
                                .str("player", "The mortal's name.", true)
                                .choice("mode", "warning or strike. Default strike.", false,
                                        List.of("warning", "strike"))
                                .integer("power", "Number of bolts for a strike (1-5). Default 1.", false, 1, 5),
                        WrathTools::smite),
                GodTool.sync("explode",
                        "Cause an explosion at a target: " + Targets.TARGET_HELP + ". Power 4 equals TNT. "
                                + "Destroys blocks unless the server forbids it.",
                        Schema.object()
                                .str("target", "Where.", true)
                                .integer("power", "Strength 1-8. Default 3.", false, 1, 8)
                                .bool("fire", "Set fires. Default false.", false),
                        a -> {
                            Location at = Targets.location(a.str("target"));
                            float power = Math.min(GodSettings.maxExplosionPower(), a.integer("power", 3, 1, 8));
                            boolean fire = a.bool("fire", false);
                            at.getWorld().createExplosion(at, power, fire, GodSettings.explosionsBreakBlocks());
                            net.bigyous.gptgodmc.client.ModFx.wrath(at, power);
                            return ToolResult.ok("Explosion of power %.0f at %s%s.", power, Targets.describe(at),
                                    GodSettings.explosionsBreakBlocks() ? "" : " (terrain protected by the server)");
                        }),
                GodTool.sync("spawn_entity",
                        "Summon creatures near a target (" + Targets.TARGET_HELP + "): a pack of wolves as "
                                + "guardians, a horse as a gift, zombies as a trial, an iron_golem as protection. "
                                + "Optionally name them, and set 'hunt' to a mortal's name to make hostile mobs "
                                + "attack that mortal.",
                        Schema.object()
                                .str("entity", "Entity id, e.g. zombie, wolf, horse, iron_golem, allay.", true)
                                .str("near", "Where they appear.", true)
                                .integer("count", "How many. Default 1.", false, 1, 20)
                                .str("name", "Optional custom name shown above them.", false)
                                .str("hunt", "Optional mortal they will attack.", false),
                        WrathTools::spawnEntity),
                GodTool.sync("fling",
                        "Hurl a mortal (or 'all') through the air with a gust of wind: up into the sky, away "
                                + "(blown back from your vessel, or backward if it is absent) or toward a target. "
                                + "safe (default) grants slow falling so the landing cannot kill.",
                        Schema.object()
                                .str("player", "The mortal's name, or 'all'.", true)
                                .choice("direction", "up, away or toward. Default up.", false,
                                        List.of("up", "away", "toward"))
                                .str("toward", "Where to throw them when direction is toward: "
                                        + Targets.TARGET_HELP + ".", false)
                                .integer("strength", "1 (a hop) to 5 (into the clouds). Default 2.", false, 1, 5)
                                .bool("safe", "Slow falling afterwards so the fall cannot kill. Default true.",
                                        false),
                        WrathTools::fling),
                GodTool.sync("meteor",
                        "Call blazing meteors down from the sky onto a target (" + Targets.TARGET_HELP + "). "
                                + "They streak in trailing fire and smoke over about two seconds, so a quick "
                                + "mortal may dodge, then explode like the explode power (power 4 equals TNT; "
                                + "destroys blocks unless the server forbids it).",
                        Schema.object()
                                .str("target", "Where they strike.", true)
                                .integer("count", "How many meteors, falling one after another. Default 1.", false,
                                        1, 5)
                                .integer("power", "Explosion strength of each, 1-8. Default 3.", false, 1, 8)
                                .bool("fire", "Set fires where they land. Default false.", false),
                        WrathTools::meteor),
                GodTool.sync("earthquake",
                        "Shake the earth around a target for a few seconds: deep rumbling, dust bursting from "
                                + "the ground, mortals jostled and their view shaken. Harmless and breaks nothing: "
                                + "a warning or a show of might.",
                        Schema.object()
                                .str("target", "Where: " + Targets.TARGET_HELP + ".", true)
                                .integer("seconds", "How long it lasts. Default 6.", false, 2, 20)
                                .integer("radius", "How far it reaches, in blocks. Default 10.", false, 3, 24)
                                .bool("nausea", "Also make them dizzy. Default false.", false),
                        WrathTools::earthquake));
    }

    private static ToolResult smite(Args a) throws ToolException {
        Player p = Targets.player(a.str("player"));
        String mode = a.str("mode", "strike").toLowerCase(Locale.ROOT);
        Location loc = p.getLocation();
        if (mode.equals("warning")) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            Location beside = loc.clone().add(Math.cos(angle) * 3, 0, Math.sin(angle) * 3);
            beside.setY(beside.getWorld().getHighestBlockYAt(beside) + 1);
            if (Math.abs(beside.getY() - loc.getY()) > 6) {
                beside.setY(loc.getY());
            }
            loc.getWorld().strikeLightningEffect(beside);
            net.bigyous.gptgodmc.client.ModFx.wrath(beside, 1);
            return ToolResult.ok("Lightning struck harmlessly beside %s as a warning.", p.getName());
        }
        int power = a.integer("power", 1, 1, 5);
        for (int i = 0; i < power; i++) {
            Bukkit.getScheduler().runTaskLater(GPTGOD.get(), () -> {
                if (p.isOnline() && !p.isDead()) {
                    p.getWorld().strikeLightning(p.getLocation());
                    net.bigyous.gptgodmc.client.ModFx.wrath(p.getLocation(), 2);
                }
            }, i * 8L);
        }
        return ToolResult.ok("%d bolt(s) of lightning fall on %s (health before: %d).", power, p.getName(),
                Math.round(p.getHealth()));
    }

    private static ToolResult fling(Args a) throws ToolException {
        List<Player> victims = Targets.players(a.str("player"));
        String direction = a.str("direction", "up").toLowerCase(Locale.ROOT);
        int strength = a.integer("strength", 2, 1, 5);
        boolean safe = a.bool("safe", true);
        Location toward = null;
        if (direction.equals("toward")) {
            if (!a.has("toward")) {
                throw new ToolException("Say where to throw them with 'toward'.");
            }
            toward = Targets.location(a.str("toward"));
        }
        Location vessel = God.vessel().location();
        List<String> names = new ArrayList<>();
        for (Player p : victims) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            Location at = p.getLocation();
            Vector v;
            switch (direction) {
                case "away" -> {
                    Vector push;
                    if (vessel != null && vessel.getWorld().equals(at.getWorld())
                            && vessel.distanceSquared(at) < 64 * 64 && vessel.distanceSquared(at) > 0.01) {
                        push = at.toVector().subtract(vessel.toVector());
                    } else {
                        push = at.getDirection().multiply(-1);
                    }
                    push.setY(0);
                    if (push.lengthSquared() < 1e-6) push = new Vector(1, 0, 0);
                    v = push.normalize().multiply(0.5 + 0.4 * strength).setY(0.5 + 0.2 * strength);
                }
                case "toward" -> {
                    if (!toward.getWorld().equals(at.getWorld())) {
                        continue; // cannot be thrown between worlds
                    }
                    Vector pull = toward.toVector().subtract(at.toVector()).setY(0);
                    if (pull.lengthSquared() < 1e-6) pull = new Vector(1, 0, 0);
                    v = pull.normalize().multiply(0.5 + 0.4 * strength).setY(0.55 + 0.2 * strength);
                }
                default -> v = new Vector(0, 0.6 + 0.45 * strength, 0);
            }
            if (v.length() > 3.9) v = v.normalize().multiply(3.9);
            p.setFallDistance(0);
            p.setVelocity(v);
            if (safe) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, (6 + 3 * strength) * 20, 0, true,
                        false));
            }
            World w = at.getWorld();
            w.spawnParticle(Particle.CLOUD, at.clone().add(0, 0.2, 0), 30, 0.5, 0.1, 0.5, 0.12);
            w.spawnParticle(Particle.POOF, at.clone().add(0, 0.3, 0), 6, 0.4, 0.1, 0.4, 0.05);
            w.playSound(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 2f, 0.8f);
            w.playSound(at, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.5f, 1f);
            Spectacle.trail(p, Particle.CLOUD, 30);
            names.add(p.getName());
        }
        if (names.isEmpty()) {
            return ToolResult.error("No living mortal to fling.");
        }
        return ToolResult.ok("A gust hurls %s %s with strength %d%s.", String.join(", ", names),
                direction.equals("toward") ? "toward " + Targets.describe(toward) : direction, strength,
                safe ? " (they will float down safely)" : " — the fall may hurt");
    }

    private static ToolResult meteor(Args a) throws ToolException {
        String targetName = a.str("target");
        Location target = Targets.location(targetName);
        Player chased = null;
        try {
            chased = Targets.player(targetName);
        } catch (ToolException ignored) {
            // a place: every meteor lands around it
        }
        int count = a.integer("count", 1, 1, 5);
        float power = Math.min(GodSettings.maxExplosionPower(), a.integer("power", 3, 1, 8));
        boolean fire = a.bool("fire", false);
        boolean breakBlocks = GodSettings.explosionsBreakBlocks();
        Player prey = chased;
        for (int i = 0; i < count; i++) {
            boolean first = i == 0;
            Bukkit.getScheduler().runTaskLater(GPTGOD.get(), () -> {
                Location aim = prey != null && prey.isOnline() && prey.getWorld().equals(target.getWorld())
                        ? prey.getLocation() : target.clone();
                if (!first) {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    aim.add(r.nextDouble(-4, 4), 0, r.nextDouble(-4, 4));
                }
                Location impact = aim;
                Spectacle.meteor(impact, () -> impact.getWorld().createExplosion(impact, power, fire, breakBlocks));
            }, i * 12L);
        }
        return ToolResult.ok("%d meteor(s) of power %.0f streak down toward %s%s; impact in about two seconds%s.",
                count, power, prey != null ? prey.getName() + " " : "", Targets.describe(target),
                breakBlocks ? "" : " (terrain protected by the server)");
    }

    private static ToolResult earthquake(Args a) throws ToolException {
        Location at = Targets.location(a.str("target"));
        int seconds = a.integer("seconds", 6, 2, 20);
        int radius = a.integer("radius", 10, 3, 24);
        if (!Spectacle.earthquake(at, radius, seconds, a.bool("nausea", false))) {
            return ToolResult.error("The earth is already shaking; wait for it to settle.");
        }
        return ToolResult.ok("The ground heaves around %s for %d seconds (radius %d).", Targets.describe(at),
                seconds, radius);
    }

    static EntityType entityType(String id) throws ToolException {
        String key = id.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace("minecraft:", "");
        EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(key));
        if (type == null) {
            try {
                type = EntityType.valueOf(key.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                type = null;
            }
        }
        if (type == null || !type.isSpawnable() || type == EntityType.PLAYER) {
            throw new ToolException("Unknown or unsummonable entity '" + id + "'. Use ids like zombie, skeleton, "
                    + "wolf, cow, horse, iron_golem, allay, creeper.");
        }
        for (String forbidden : GodSettings.forbiddenEntities()) {
            if (forbidden.equalsIgnoreCase(key)) {
                throw new ToolException("The server forbids you from summoning " + key + ".");
            }
        }
        return type;
    }

    private static ToolResult spawnEntity(Args a) throws ToolException {
        EntityType type = entityType(a.str("entity"));
        Location center = Targets.location(a.str("near"));
        int count = Math.min(GodSettings.maxEntitiesPerSpawn(), a.integer("count", 1, 1, 20));
        String name = a.str("name", null);
        Player prey = a.has("hunt") ? Targets.player(a.str("hunt")) : null;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int spawned = 0, noRoom = 0;
        // Only open spots on solid ground, nearest first; each creature is checked whole after it appears.
        List<Location> spots = Spots.near(center, 4);
        if (spots.isEmpty()) {
            throw new ToolException("There is no open ground near " + Targets.describe(center)
                    + " for a creature to stand on. Choose a place in the open.");
        }
        for (int i = 0; i < count; i++) {
            int first = Math.min(spots.size() - 1, r.nextInt(Math.min(spots.size(), 6 + i * 2)));
            Entity e = center.getWorld().spawnEntity(spots.get(first), type);
            if (!Spots.fit(e, spots, first)) {
                e.remove();
                noRoom++;
                continue;
            }
            if (name != null) {
                e.customName(Component.text(count > 1 ? name + " " + (i + 1) : name, Speech.VIOLET));
                e.setCustomNameVisible(true);
            }
            if (prey != null && e instanceof Mob mob) {
                mob.setTarget(prey);
            }
            spawned++;
        }
        if (spawned == 0) {
            throw new ToolException("No " + type.getKey().getKey() + " fits near " + Targets.describe(center)
                    + ": too little open room. Choose a place in the open.");
        }
        return ToolResult.ok("Summoned %d %s near %s%s%s.", spawned, type.getKey().getKey(), Targets.describe(center),
                prey == null ? "" : ", hunting " + prey.getName(),
                noRoom == 0 ? "" : " (" + noRoom + " had no room to stand)");
    }
}
