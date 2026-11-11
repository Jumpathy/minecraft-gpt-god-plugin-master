package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.Structure;
import net.bigyous.gptgodmc.StructureManager;
import net.bigyous.gptgodmc.enums.GptGameMode;
import net.bigyous.gptgodmc.utils.NicknameCommand;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Builds the text the god "sees" at the start of each thought. Must run on the server thread. */
public final class Perception {
    private Perception() {
    }

    public static String build(String trigger, EventLogger.Drained drained) {
        return build(trigger, drained, false);
    }

    /** preview: only looking (the debug bridge), so pending operator commands are not counted as seen. */
    public static String build(String trigger, EventLogger.Drained drained, boolean preview) {
        StringBuilder sb = new StringBuilder();
        Realm realm = Realm.current();
        World world = realm.world();
        List<Player> mortals = realm.players();
        sb.append("# Perception — ").append(clock(world)).append("\n");
        sb.append("You looked because: ").append(trigger).append("\n\n");

        if (!drained.directives().isEmpty()) {
            sb.append("## Operator directive (from the server operator — follow it)\n");
            drained.directives().forEach(l -> sb.append(l).append("\n"));
            sb.append("\n");
        }

        String edicts = preview ? Edicts.peek() : Edicts.render();
        if (!edicts.isEmpty()) {
            sb.append(edicts).append("\n");
        }

        sb.append("## The world\n").append(describeWorld(realm, world, mortals)).append("\n");
        sb.append("## Your vessel\n").append(God.vessel().describe()).append("\n\n");
        String peers = describePeers(realm);
        if (!peers.isEmpty()) {
            sb.append("## Other gods of this world (what any mortal could see; never their minds)\n").append(peers)
                    .append("\n");
        }
        sb.append("## Your heart (feelings that build from what mortals do and fade over time)\n")
                .append(realm.memory().heart.describe(System.currentTimeMillis())).append("\n\n");
        sb.append("## Your mortality\n").append(Godslayer.perceive(realm)).append("\n\n");
        sb.append(net.bigyous.gptgodmc.god.living.Living.perceive(realm));

        List<String> online = new ArrayList<>();
        sb.append("## Mortals present\n");
        if (mortals.isEmpty()) {
            sb.append("(none)\n");
        }
        for (Player p : mortals) {
            online.add(p.getName());
            sb.append(describePlayer(p));
        }
        sb.append("\n");
        sb.append(LandSense.perceive(realm, mortals));
        sb.append(freeWill(realm, world, mortals, trigger));

        String structures = describeStructures();
        if (!structures.isEmpty()) {
            sb.append("## Structures mortals have built\n").append(structures).append("\n");
        }
        sb.append(Reminiscence.perceive(realm, mortals, trigger, preview));

        Memory memory = Memory.get();
        memory.seen(online, System.currentTimeMillis());
        sb.append(Musing.perceive(realm, online, drained, trigger, preview));
        sb.append(memorySections(memory, online, drained, realm.conversation(), !preview));
        return sb.toString();
    }

    /** One line per other god of this world: name, nature, power, where its body is and its mood. */
    static String describePeers(Realm realm) {
        StringBuilder sb = new StringBuilder();
        for (Realm peer : realm.peers()) {
            Persona p = peer.persona;
            sb.append("- ").append(p == null ? peer.displayName() + " (not yet awoken)" : p.title() + " ("
                    + p.archetype + ")");
            sb.append(", a ").append(peer.power().id).append(" god");
            Mortality death = peer.memory().mortality;
            if (death.isSlain()) {
                sb.append(" — DEAD: slain by ").append(death.slainBy).append(" with the Godslayer ")
                        .append(Memory.age(System.currentTimeMillis() - death.slainAt))
                        .append(". It cannot think, act, hear or answer; its laws are void.\n");
                continue;
            }
            net.bigyous.gptgodmc.god.vessel.Vessel v = peer.vesselIfCreated();
            Location at = v == null ? null : v.location();
            if (v != null && v.inTrueForm()) {
                sb.append(" — has revealed its true form");
            } else if (at != null && v.isManifested()) {
                sb.append(String.format(" — walks the world at (%d, %d, %d) in %s", at.getBlockX(), at.getBlockY(),
                        at.getBlockZ(), at.getWorld().getName()));
                if (v.mood() != null && v.mood() != net.bigyous.gptgodmc.god.vessel.VesselMood.CALM) {
                    sb.append(", ").append(v.mood().id());
                }
            } else {
                sb.append(" — watches unseen");
            }
            if (!peer.awake) sb.append(" (asleep)");
            sb.append("\n");
        }
        return sb.toString();
    }

    /** Events that make the mortals they name part of this moment (and so worth a full dossier). */
    private static final java.util.regex.Pattern NOTABLE = java.util.regex.Pattern.compile(
            "DEATH:|was killed by|OFFERING|vessel|advancement|challenge|goal|tamed|entered the world|crossed into|quest"
                    + "|oath|killed a villager|slaughtering|raid");

    /**
     * The mortals this thought is about: whoever prayed, spoke, made an offering, touched the vessel or
     * died, or was named in those lines, in that order of importance (online or not).
     */
    static List<String> involved(Memory memory, List<String> online, EventLogger.Drained drained) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        List<String> lines = new ArrayList<>();
        lines.addAll(drained.prayers());
        lines.addAll(drained.directives());
        lines.addAll(drained.speech());
        for (String e : drained.events()) {
            if (NOTABLE.matcher(e).find()) lines.add(e);
        }
        for (String line : lines) {
            for (String n : memory.namesIn(line, online)) {
                if (out.stream().noneMatch(o -> o.equalsIgnoreCase(n))) out.add(n);
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * Everything the god remembers, then what happened since it last looked: works, quests, laws, the
     * ledger (dossiers for the mortals involved now, one line for others), recent acts, events and words.
     * Needs no world access, so it is also built in tests.
     */
    static String memorySections(Memory memory, List<String> online, EventLogger.Drained drained) {
        return memorySections(memory, online, drained, null, false);
    }

    /** "## What you are doing": its own pastime, or whims that suit it (free will); empty when it only watches. */
    static String freeWill(Realm realm, World world, List<Player> mortals, String trigger) {
        try {
            Initiative.Level level = GodSettings.initiativeLevel();
            if (level == Initiative.Level.WATCH) return "";
            Memory memory = realm.memory();
            long now = System.currentTimeMillis();
            List<String> weak = new ArrayList<>();
            String favored = null;
            int bestFavor = 2;
            for (Player p : mortals) {
                boolean mortal = p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
                if (mortal && (p.getHealth() <= 8 || p.getFoodLevel() <= 6)) {
                    weak.add(p.getName() + (p.getHealth() <= 8 ? " (hurt)" : " (hungry)"));
                }
                int f = memory.favorOf(p.getName());
                if (f > bestFavor) {
                    bestFavor = f;
                    favored = p.getName();
                }
            }
            Map<String, Double> heart = new java.util.HashMap<>();
            for (Heart.Feeling f : Heart.Feeling.values()) heart.put(f.id(), memory.heart.level(f));
            Persona persona = Persona.current();
            Map<String, Integer> dials = persona == null ? Map.of() : persona.dials;
            // Its own time: a quiet moment, or its regular watch (nothing is calling for it by name).
            boolean quiet = trigger != null && (trigger.startsWith("a quiet moment")
                    || trigger.startsWith("your regular watch"));
            String body = Initiative.render(memory, dials, heart, new Initiative.Context(quiet, world.getTime(),
                    world.hasStorm(), mortals.size(), weak, favored), level, now);
            String dances = memory.dances.isEmpty() ? "" : "- Animations you made (vessel_animate by name): "
                    + String.join(", ", memory.dances.keySet()) + ".\n";
            if (body.isEmpty() && dances.isEmpty()) return "";
            return "## What you are doing (your own free will: you live here, not merely watch)\n" + body + dances
                    + "\n";
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** @param conversation the realm's timeline (null: show only what was said since the last look) */
    static String memorySections(Memory memory, List<String> online, EventLogger.Drained drained,
            Conversation conversation, boolean markSeen) {
        StringBuilder sb = new StringBuilder();
        String works = memory.renderWorks();
        if (!works.isEmpty()) {
            sb.append("## Your works (things you built)\n").append(works).append("\n");
        }
        String quests = memory.renderQuests();
        sb.append("## Quests you have given\n").append(quests.isEmpty() ? "(none)\n" : quests).append("\n");
        if (GodSettings.apotheosisEnabled()) {
            sb.append("## Demigods (apotheosis, your rarest gift)\n")
                    .append(DemigodPowers.state().describe(System.currentTimeMillis(),
                            GodSettings.apotheosisCooldownHours()))
                    .append("\n");
        }
        if (GodSettings.purposeEnabled()) {
            String purpose = Purpose.render(memory, System.currentTimeMillis(), GodSettings.purposeNudgeMinutes(),
                    !online.isEmpty());
            if (!purpose.isEmpty()) {
                sb.append("## Your purpose (what you want; pursue it, don't merely watch)\n").append(purpose)
                        .append("\n");
            }
        }
        sb.append(commandmentsBlock(memory));
        if (GodSettings.oathsEnabled()) {
            String oaths = Oaths.render(memory, System.currentTimeMillis());
            if (!oaths.isEmpty()) {
                sb.append("## Oaths sworn before you (judge each with resolve_oath)\n").append(oaths).append("\n");
            }
        }
        String ledger = memory.renderLedger(online, involved(memory, online, drained));
        sb.append("## Your ledger (full dossiers on the mortals in this moment; recall(player) for anyone else)\n")
                .append(ledger.isEmpty() ? "(empty)\n" : ledger).append("\n");
        String recent = memory.renderRecent();
        sb.append("## Your recent acts (oldest first)\n").append(recent.isEmpty() ? "(none yet)\n" : recent)
                .append("\n");

        sb.append("## What mortals did since you last looked (oldest first)\n");
        if (drained.events().isEmpty()) {
            sb.append("(nothing notable)\n");
        }
        drained.events().forEach(l -> sb.append(l).append("\n"));
        if (drained.omitted() > 0) {
            sb.append("(").append(drained.omitted()).append(" older minor events were omitted)\n");
        }
        // One timeline of what was said lately, yours included, so you can tell who answered whom.
        String talk = conversation == null ? "" : conversation.render(System.currentTimeMillis(), markSeen);
        sb.append("\n## Conversation (last 10 minutes, oldest first; NEW = since you last looked; \"You\" = your "
                + "own words)\n");
        if (talk.isEmpty()) {
            sb.append(drained.speech().isEmpty() && drained.prayers().isEmpty() ? "(silence)\n" : "");
            // Words logged before the timeline existed (or with no realm) still reach you.
            drained.speech().forEach(l -> sb.append(l).append("\n"));
            drained.prayers().forEach(l -> sb.append(l).append("\n"));
        } else {
            sb.append(talk);
        }
        if (!drained.prayers().isEmpty()) {
            sb.append("\n## Waiting for your answer (each about what THEY said; see the conversation)\n");
            drained.prayers().forEach(l -> sb.append(l).append("\n"));
        }
        return sb.toString();
    }

    /** Your standing laws, always shown (it is small): a heading and a numbered list. */
    static String commandmentsBlock(Memory memory) {
        String laws = memory.renderCommandments();
        return "## Your commandments (mortals can read them with /commandments)\n"
                + (laws.isEmpty() ? "(none decreed)\n" : laws) + "\n";
    }

    /** The title you gave a mortal and the mark you set on them, as shown after their name. */
    static String devotionOf(Memory memory, String name, long now) {
        Memory.Subject s = memory.subjectIfAny(name);
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        if (s.title != null) {
            sb.append(" titled \"").append(s.title).append("\"");
        }
        String mark = memory.activeMark(name, now);
        if (mark != null) {
            long left = Math.max(1, (s.markUntil - now + 59_999) / 60_000);
            sb.append(Marks.HALO.equals(mark) ? " [bears your golden halo" : " [bears your curse cloud")
                    .append(", ").append(left).append(" min left]");
        }
        return sb.toString();
    }

    /** A one-paragraph summary of a player, also used by observe_player. */
    public static String describePlayer(Player p) {
        StringBuilder sb = new StringBuilder();
        Memory memory = Memory.get();
        int favor = memory.favorOf(p.getName());
        sb.append("- ").append(p.getName());
        if (GodSettings.revereOps() && p.isOp()) {
            sb.append(" [OPERATOR: a keeper of this server, above other mortals]");
        }
        String nick = NicknameCommand.getNickname(p);
        if (!nick.isBlank()) {
            sb.append(" (calls themself \"").append(nick).append("\")");
        }
        if (GPTGOD.gameMode == GptGameMode.DEATHMATCH && GPTGOD.SCOREBOARD.getEntityTeam(p) != null) {
            sb.append(" [team ").append(GPTGOD.SCOREBOARD.getEntityTeam(p).getName()).append("]");
        }
        sb.append(devotionOf(memory, p.getName(), System.currentTimeMillis()));
        sb.append(Strikes.get().line(p.getName(), System.currentTimeMillis(), Strikes.days()));
        sb.append(" — favor ").append(favor > 0 ? "+" : "").append(favor).append(" (")
                .append(Memory.favorLabel(favor)).append(")\n");

        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) {
            sb.append("    DEAD (spectating, can be revived)\n");
            return sb.toString();
        }
        double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        Location l = p.getLocation();
        Block feet = l.getBlock();
        boolean underground = feet.getLightFromSky() < 4 && l.getY() < l.getWorld().getHighestBlockYAt(l) - 3;
        // Need is spelled out, so killing for food or fleeing a fight is read for what it is.
        String hurt = Senses.hurt(p.getHealth(), max), hunger = Senses.hunger(p.getFoodLevel());
        sb.append(String.format("    health %d/%d%s, food %d/20%s, level %d, %s | %s (%d, %d, %d), %s, %s%n",
                Math.round(p.getHealth()), Math.round(max), hurt == null ? "" : " (" + hurt + ")", p.getFoodLevel(),
                hunger == null ? "" : " (" + hunger + ")", p.getLevel(),
                p.getGameMode().name().toLowerCase(Locale.ROOT), dimension(l.getWorld()), l.getBlockX(),
                l.getBlockY(), l.getBlockZ(), feet.getBiome().getKey().getKey(),
                underground ? "underground" : (p.isInWater() ? "in water" : "on the surface")));
        sb.append("    holding ").append(item(p.getInventory().getItemInMainHand()));
        List<String> armor = new ArrayList<>();
        for (ItemStack a : p.getInventory().getArmorContents()) {
            if (a != null && !a.getType().isAir()) {
                armor.add(item(a));
            }
        }
        sb.append("; armor: ").append(armor.isEmpty() ? "none" : String.join(", ", armor));
        List<String> effects = new ArrayList<>();
        for (PotionEffect e : p.getActivePotionEffects()) {
            effects.add(e.getType().getKey().getKey());
        }
        if (!effects.isEmpty()) {
            sb.append("; effects: ").append(String.join(", ", effects));
        }
        sb.append("\n    ").append(activity(p));
        if (Senses.enabled()) {
            // What threatens them (and whether they can see it), hazards, and what they are making.
            sb.append(" | ").append(Senses.line(p));
        } else {
            int hostiles = 0;
            for (Entity e : p.getNearbyEntities(12, 6, 12)) {
                if (e instanceof Enemy) {
                    hostiles++;
                }
            }
            sb.append(" | danger: ").append(hostiles == 0 ? "none" : hostiles + " hostile(s) within 12 blocks");
        }
        String near = StructureManager.getClosestStructureToLocation(l).trim();
        if (!near.isEmpty()) {
            sb.append(" | ").append(near.replace("Location: ", ""));
        }
        Location vessel = God.vessel().location();
        if (vessel != null && vessel.getWorld().equals(l.getWorld())) {
            sb.append(" | ").append(Math.round(vessel.distance(l))).append(" blocks from your vessel");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String activity(Player p) {
        List<String> a = new ArrayList<>();
        if (p.isSleeping()) a.add("sleeping");
        if (p.isInsideVehicle() && p.getVehicle() != null) a.add("riding " + p.getVehicle().getName());
        if (p.isGliding()) a.add("gliding");
        if (p.isFlying()) a.add("flying");
        if (p.isSwimming()) a.add("swimming");
        if (p.isSprinting()) a.add("sprinting");
        if (p.isSneaking()) a.add("sneaking");
        if (p.isBlocking()) a.add("blocking with a shield");
        if (p.getFireTicks() > 0) a.add("ON FIRE");
        if (a.isEmpty()) a.add("standing/walking");
        return "activity: " + String.join(", ", a) + ", facing " + facing(p.getLocation().getYaw());
    }

    public static String facing(float yaw) {
        String[] dirs = { "south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east" };
        int idx = Math.floorMod(Math.round(yaw / 45f), 8);
        return dirs[idx];
    }

    private static String describeWorld(Realm realm, World world, List<Player> mortals) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s (%s): %s, weather: %s, difficulty %s%n", world.getName(), dimension(world),
                clock(world), weather(world), world.getDifficulty().name().toLowerCase(Locale.ROOT)));
        sb.append(Sky.line(world));
        // The god's other dimensions: it watches mortals who step through a portal.
        Map<String, Integer> others = new TreeMap<>();
        for (Player p : mortals) {
            if (!p.getWorld().equals(world)) {
                others.merge(dimension(p.getWorld()) + " (" + p.getWorld().getName() + ")", 1, Integer::sum);
            }
        }
        others.forEach((name, count) -> sb.append("Also ").append(count).append(" mortal(s) in ").append(name)
                .append("\n"));
        // Worlds on this server that belong to another god, or to none.
        int beyond = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!realm.governs(p.getWorld())) {
                beyond++;
            }
        }
        if (Realm.several() || beyond > 0) {
            List<String> mine = new ArrayList<>();
            realm.worldList().forEach(w -> mine.add(w.getName()));
            sb.append("Your realm is ").append(String.join(", ", mine))
                    .append(". Other worlds exist beyond it where you have neither sight nor power");
            if (beyond > 0) {
                sb.append("; ").append(beyond).append(" mortal(s) are out there now, out of your reach");
            }
            sb.append(".\n");
        }
        return sb.toString();
    }

    public static String clock(World world) {
        long time = world.getTime();
        int hours = (int) ((time / 1000 + 6) % 24);
        int minutes = (int) ((time % 1000) * 60 / 1000);
        String phase;
        if (time < 1000) phase = "dawn";
        else if (time < 6000) phase = "morning";
        else if (time < 9000) phase = "midday";
        else if (time < 12000) phase = "afternoon";
        else if (time < 13000) phase = "dusk";
        else if (time < 18000) phase = "night";
        else if (time < 23000) phase = "deep night";
        else phase = "before dawn";
        return String.format("day %d, %02d:%02d (%s)", world.getFullTime() / 24000 + 1, hours, minutes, phase);
    }

    private static String weather(World world) {
        if (world.hasStorm() && world.isThundering()) return "thunderstorm";
        if (world.hasStorm()) return "rain";
        return "clear";
    }

    public static String dimension(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> "the Nether";
            case THE_END -> "the End";
            default -> "overworld";
        };
    }

    private static String describeStructures() {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        Realm realm = Realm.current();
        for (String name : StructureManager.getStructures()) {
            Structure s = StructureManager.getStructure(name);
            Location c = s.getLocation();
            if (!realm.governs(c.getWorld())) {
                continue;
            }
            if (shown++ >= 12) {
                sb.append("(more structures omitted)\n");
                break;
            }
            sb.append(String.format("- %s: %d blocks, built by %s, centered at (%d, %d, %d)%n", name, s.getSize(),
                    s.getBuilder() == null ? "unknown" : s.getBuilder().getName(), c.getBlockX(), c.getBlockY(),
                    c.getBlockZ()));
        }
        return sb.toString();
    }

    public static String item(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "nothing";
        }
        String id = stack.getType().getKey().getKey();
        String amount = stack.getAmount() > 1 ? stack.getAmount() + "x " : "";
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            return amount + id + " named \""
                    + PlainTextComponentSerializer.plainText().serialize(stack.getItemMeta().displayName()) + "\"";
        }
        return amount + id;
    }
}
