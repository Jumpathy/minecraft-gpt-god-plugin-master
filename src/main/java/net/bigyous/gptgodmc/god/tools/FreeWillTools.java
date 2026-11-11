package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Initiative;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Spectacle;
import net.bigyous.gptgodmc.god.build.Builder;
import net.bigyous.gptgodmc.god.vessel.Choreo;
import net.bigyous.gptgodmc.god.vessel.Vessel;

/** Free will: animations the god writes, working with its hands like a player, and pastimes it chooses. */
final class FreeWillTools {
    private FreeWillTools() {
    }

    static List<GodTool> all() {
        return List.of(animate(), work(), activity());
    }

    // ---- vessel_animate ---------------------------------------------------------------------------

    private static GodTool animate() {
        return GodTool.sync("vessel_animate",
                "Move your body in an animation you write yourself (a bow, a dance, digging, a sweeping "
                        + "blessing, sitting on a cliff, juggling flames), or replay a saved one or a built-in ("
                        + String.join(", ", Choreo.PRESETS.keySet()) + ") by name; name 'stop' ends it. Each "
                        + "keyframe is one string of tokens, angles in degrees: t=seconds (max 20) body=lean,turn,"
                        + "roll torso=bend(+forward) head=turn,tilt(+down) larm=/rarm=forward,out[,twist] (0,0 "
                        + "hangs, 90 ahead, 180 straight up; out + = away from the body) lleg=/rleg=forward,out "
                        + "(90 = sitting) cape=flare rise=h (figure heights, -0.36 sits on the ground) slide=x,z "
                        + "ease=smooth|linear|snap. Bones a key leaves out keep their idle pose. Cues: t=1 "
                        + "fx=<particle> at=rhand|lhand|hands|head|body|feet|above n=8 sound=<sound id> pitch=1. "
                        + "Values are clamped; it blends in from idle and back out. A loop should end on its "
                        + "first pose. A looping animation you save under a name beginning think_ or ponder_ "
                        + "becomes one of the poses your body takes by itself while you are deep in thought.",
                Schema.object()
                        .str("name", "Its name, to save or replay it ('stop' ends the one playing).", true)
                        .strings("keys", "Keyframes, e.g. 't=0 torso=0 rarm=10,8', 't=0.8 torso=55 head=0,30 "
                                + "rarm=95,-25'. Leave out to replay by name.", false, Choreo.MAX_KEYS)
                        .strings("cues", "Particles and sounds at moments, e.g. 't=1 fx=heart at=hands n=6 "
                                + "sound=entity.player.levelup'.", false, Choreo.MAX_CUES)
                        .bool("loop", "Repeat it (dances, sitting, digging). Default false.", false)
                        .integer("seconds", "How long a loop plays (default 20).", false, 1, Choreo.MAX_LOOP_SECONDS)
                        .bool("save", "Keep it under its name to replay later.", false)
                        .str("toward", "Optional mortal to face.", false),
                a -> {
                    Vessel v = God.vessel();
                    if (!v.isManifested()) throw new ToolException("You are formless; take form with vessel_go first.");
                    if (v.inTrueForm()) throw new ToolException("Your true form has no limbs to move.");
                    String name = Choreo.clean(a.str("name"));
                    Memory mem = Memory.get();
                    if (name.equals("stop")) {
                        v.stopAnimation();
                        return ToolResult.ok("Your body settles back into stillness.");
                    }
                    List<String> keys = lines(a, "keys");
                    Choreo.Anim anim;
                    String source;
                    if (!keys.isEmpty()) {
                        try {
                            anim = Choreo.parse(name, keys, lines(a, "cues"), a.bool("loop", false),
                                    Spectacle.particleNames());
                        } catch (Choreo.Invalid e) {
                            throw new ToolException(e.getMessage());
                        }
                        source = "new";
                        if (a.bool("save", false)) {
                            if (Choreo.PRESETS.containsKey(name)) {
                                throw new ToolException("'" + name + "' is a built-in; save yours under another name.");
                            }
                            synchronized (mem) {
                                if (!mem.dances.containsKey(name) && mem.dances.size() >= Choreo.MAX_SAVED) {
                                    throw new ToolException("You keep " + Choreo.MAX_SAVED + " animations already ("
                                            + String.join(", ", mem.dances.keySet()) + "); reuse one's name.");
                                }
                                mem.dances.put(name, anim.text());
                            }
                            mem.save();
                            source = "new, saved";
                        }
                    } else {
                        String text;
                        synchronized (mem) {
                            text = mem.dances.get(name);
                        }
                        anim = text != null ? Choreo.fromText(name, text) : Choreo.preset(name);
                        source = text != null ? "saved" : "built-in";
                        if (anim == null) {
                            throw new ToolException("You know no animation '" + name + "'. Built-in: "
                                    + String.join(", ", Choreo.PRESETS.keySet()) + "; yours: "
                                    + (mem.dances.isEmpty() ? "none" : String.join(", ", mem.dances.keySet()))
                                    + ". Or write one with keys.");
                        }
                        if (a.has("loop")) {
                            anim = new Choreo.Anim(anim.name(), anim.keys(), anim.cues(), anim.length(),
                                    a.bool("loop", anim.loop()), anim.warnings());
                        }
                    }
                    if (a.has("toward")) v.lookAt(Targets.player(a.str("toward")));
                    int seconds = a.integer("seconds", 20, 1, Choreo.MAX_LOOP_SECONDS);
                    v.animate(anim, seconds);
                    String warn = anim.warnings().isEmpty() ? ""
                            : " Adjusted: " + String.join("; ", anim.warnings().subList(0,
                                    Math.min(5, anim.warnings().size()))) + ".";
                    return ToolResult.ok("Your body performs %s [%s]%s.%s", anim.summary(), source,
                            anim.loop() ? " for " + seconds + "s" : "", warn);
                });
    }

    /** A list argument; one plain string is split on newlines or '|' (commas belong to values). */
    static List<String> lines(Args a, String key) {
        JsonElement e = a.raw() == null ? null : a.raw().get(key);
        List<String> out = new ArrayList<>();
        if (e == null || e.isJsonNull()) return out;
        if (e.isJsonArray()) {
            JsonArray arr = e.getAsJsonArray();
            for (JsonElement item : arr) {
                if (item != null && item.isJsonPrimitive() && !item.getAsString().isBlank()) {
                    out.add(item.getAsString().trim());
                }
            }
        } else if (e.isJsonPrimitive()) {
            for (String part : e.getAsString().split("[\\n|]")) {
                if (!part.isBlank()) out.add(part.trim());
            }
        }
        return out;
    }

    // ---- vessel_work -------------------------------------------------------------------------------

    private static final Map<String, Deque<Long>> WORK = new HashMap<>();
    private static final Map<String, Long> LAST_CAST = new HashMap<>();

    private static final Set<Material> NEVER = Set.of(Material.BEDROCK, Material.BARRIER, Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK, Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID, Material.JIGSAW, Material.SPAWNER, Material.END_PORTAL,
            Material.END_PORTAL_FRAME, Material.NETHER_PORTAL, Material.END_GATEWAY, Material.TNT, Material.LAVA,
            Material.WATER, Material.LIGHT, Material.REINFORCED_DEEPSLATE, Material.TRIAL_SPAWNER, Material.VAULT,
            Material.RESPAWN_ANCHOR, Material.FIRE, Material.SOUL_FIRE);

    private static final Map<String, Material> CROPS = Map.of("wheat", Material.WHEAT, "wheat_seeds", Material.WHEAT,
            "carrot", Material.CARROTS, "carrots", Material.CARROTS, "potato", Material.POTATOES, "potatoes",
            Material.POTATOES, "beetroot", Material.BEETROOTS, "beetroot_seeds", Material.BEETROOTS,
            "melon", Material.MELON_STEM, "pumpkin", Material.PUMPKIN_STEM);

    private static GodTool work() {
        return GodTool.sync("vessel_work",
                "Work with your vessel's own hands like a mortal, within reach (about 6 blocks, more when "
                        + "larger; walk there with vessel_go first). dig breaks one block (it drops), place sets "
                        + "one block, plant sows crops, flowers or a sapling on the ground at 'at' (count up to 9 "
                        + "fills a patch around it; dirt is tilled), fish casts into nearby water and reels in a "
                        + "catch at your feet or for a mortal. Plays a fitting animation. Mortals' structures "
                        + "and containers are never touched; a few blocks a minute at most.",
                Schema.object()
                        .choice("action", "What to do.", true, List.of("dig", "place", "plant", "fish"))
                        .str("at", "Block coordinates 'x y z' (dig/place: that block; plant: the ground).", false)
                        .str("block", "place: the block (e.g. lantern, oak_planks, poppy). plant: wheat, carrot, "
                                + "potato, beetroot, melon, pumpkin, a flower or a sapling.", false)
                        .integer("count", "plant: how many spots (1-9) in a patch around 'at'.", false, 1, 9)
                        .str("for", "fish: a mortal to give the catch to.", false),
                a -> {
                    Vessel v = God.vessel();
                    if (!v.isManifested() || v.isMaterializing()) {
                        throw new ToolException("You need a body to work with your hands; take form with vessel_go.");
                    }
                    if (v.inTrueForm()) throw new ToolException("Your true form has no hands.");
                    if (GodSettings.initiativeLevel() == Initiative.Level.WATCH) {
                        throw new ToolException("The keepers of this server want you only to watch "
                                + "(initiative.level is watch).");
                    }
                    String action = a.str("action").toLowerCase(Locale.ROOT);
                    Location body = v.location();
                    return switch (action) {
                        case "fish" -> fish(v, body, a.str("for", null));
                        case "dig", "place", "plant" -> {
                            if (!a.has("at")) throw new ToolException("Say where: at = 'x y z'.");
                            Location at = Targets.parseCoords(a.str("at"));
                            if (at == null) throw new ToolException("'at' must be block coordinates 'x y z'.");
                            if (!at.getWorld().equals(body.getWorld())) {
                                throw new ToolException("That is in another world from your vessel.");
                            }
                            double reach = 4.5 + 1.5 * v.size();
                            double dist = Math.hypot(at.getBlockX() + 0.5 - body.getX(), at.getBlockZ() + 0.5 - body.getZ());
                            if (dist > reach || Math.abs(at.getY() - body.getY()) > reach) {
                                throw new ToolException(String.format(Locale.ROOT, "That is %.0f blocks away; your "
                                        + "hands reach %.0f. Walk there first (vessel_go to '%d %d %d').", dist, reach,
                                        at.getBlockX(), at.getBlockY() + 1, at.getBlockZ()));
                            }
                            v.face(at.clone().add(0.5, 0.5, 0.5), 6);
                            yield switch (action) {
                                case "dig" -> dig(v, at.getBlock());
                                case "place" -> place(v, at.getBlock(), a.str("block", null));
                                default -> plant(v, at.getBlock(), a.str("block", "wheat"), a.integer("count", 1, 1, 9));
                            };
                        }
                        default -> throw new ToolException("Unknown action '" + action + "'.");
                    };
                });
    }

    /** Takes n units of this minute's allowance, or refuses. */
    private static void spend(int n) throws ToolException {
        String id = Realm.current().id;
        long now = System.currentTimeMillis();
        Deque<Long> q = WORK.computeIfAbsent(id, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > 60_000) q.pollFirst();
        int cap = GodSettings.initiativeWorkPerMinute();
        if (q.size() + n > cap) {
            throw new ToolException("Your hands are weary: at most " + cap + " blocks a minute. Rest a moment, "
                    + "or use build for larger works.");
        }
        for (int i = 0; i < n; i++) q.addLast(now);
    }

    private static void guard(Block b) throws ToolException {
        String mortal = Builder.mortalStructureAt(b.getLocation());
        if (mortal != null) {
            throw new ToolException("That block belongs to " + mortal + ", a mortal's structure; leave it be.");
        }
        if (b.getState() instanceof TileState) {
            throw new ToolException("That " + pretty(b.getType()) + " holds things or a purpose; leave it be.");
        }
    }

    private static ToolResult dig(Vessel v, Block b) throws ToolException {
        Material m = b.getType();
        if (m.isAir() || b.isLiquid()) throw new ToolException("There is nothing solid there to dig.");
        if (NEVER.contains(m) || m.getHardness() < 0 || m.getHardness() >= 50) {
            throw new ToolException("Your hands will not break " + pretty(m) + ".");
        }
        guard(b);
        spend(1);
        playPreset(v, "dig", 2);
        World w = b.getWorld();
        Location c = b.getLocation().add(0.5, 0.5, 0.5);
        w.spawnParticle(Particle.BLOCK, c, 24, 0.3, 0.3, 0.3, 0, b.getBlockData());
        b.breakNaturally(true);
        return ToolResult.ok("You dig out the %s at %d %d %d; it drops at the spot.", pretty(m), b.getX(), b.getY(),
                b.getZ());
    }

    private static ToolResult place(Vessel v, Block b, String blockName) throws ToolException {
        if (blockName == null) throw new ToolException("Which block? (block = e.g. lantern, oak_planks)");
        Material m = Material.matchMaterial(blockName.trim());
        if (m == null || !m.isBlock() || m.isAir()) throw new ToolException("'" + blockName + "' is not a block.");
        if (NEVER.contains(m) || m.name().contains("COMMAND") || m.name().endsWith("SHULKER_BOX")) {
            throw new ToolException("You may not place " + pretty(m) + " with your hands.");
        }
        if (!b.getType().isAir() && !b.isReplaceable()) {
            throw new ToolException("There is already " + pretty(b.getType()) + " there; choose an empty spot.");
        }
        guard(b);
        for (Player p : b.getWorld().getPlayers()) {
            if (p.getBoundingBox().overlaps(b.getBoundingBox().expand(0.01)) || p.getLocation().getBlock().equals(b)) {
                throw new ToolException(p.getName() + " stands there.");
            }
        }
        spend(1);
        playPreset(v, "place", 0);
        b.setType(m, true);
        b.getWorld().spawnParticle(Particle.END_ROD, b.getLocation().add(0.5, 0.6, 0.5), 6, 0.25, 0.25, 0.25, 0.01);
        return ToolResult.ok("You set %s at %d %d %d.", pretty(m), b.getX(), b.getY(), b.getZ());
    }

    private static ToolResult plant(Vessel v, Block ground, String what, int count) throws ToolException {
        String k = what.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "").replace(' ', '_');
        Material crop = CROPS.get(k);
        Material plant = crop;
        if (plant == null) {
            Material m = Material.matchMaterial(k);
            if (m != null && (Tag.SAPLINGS.isTagged(m) || Tag.SMALL_FLOWERS.isTagged(m))) plant = m;
        }
        if (plant == null) {
            throw new ToolException("You cannot plant '" + what + "'. Crops: wheat, carrot, potato, beetroot, melon, "
                    + "pumpkin; or a flower or sapling.");
        }
        List<Block> spots = new ArrayList<>();
        int[][] ring = { { 0, 0 }, { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { -1, -1 }, { 1, -1 }, { -1, 1 } };
        for (int[] o : ring) {
            if (spots.size() >= count) break;
            Block g = ground.getRelative(o[0], 0, o[1]);
            Block above = g.getRelative(0, 1, 0);
            if (!above.getType().isAir()) continue;
            if (Builder.mortalStructureAt(g.getLocation()) != null) continue;
            boolean soil = crop != null ? (g.getType() == Material.FARMLAND || tillable(g.getType()))
                    : (Tag.DIRT.isTagged(g.getType()) || g.getType() == Material.FARMLAND);
            if (soil) spots.add(g);
        }
        if (spots.isEmpty()) {
            throw new ToolException("No open soil there: crops need dirt or grass with air above, flowers and "
                    + "saplings dirt, grass or moss. The ground at 'at' is " + pretty(ground.getType()) + ".");
        }
        spend(spots.size());
        playPreset(v, "plant", 0);
        for (Block g : spots) {
            if (crop != null && g.getType() != Material.FARMLAND) g.setType(Material.FARMLAND, false);
            g.getRelative(0, 1, 0).setType(plant, false);
            g.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, g.getLocation().add(0.5, 1.3, 0.5), 5, 0.3, 0.2,
                    0.3, 0);
        }
        return ToolResult.ok("You plant %d %s%s around %d %d %d.", spots.size(), pretty(plant),
                spots.size() < count ? " (only that many open spots)" : "", ground.getX(), ground.getY(), ground.getZ());
    }

    private static boolean tillable(Material m) {
        return m == Material.DIRT || m == Material.GRASS_BLOCK || m == Material.DIRT_PATH || m == Material.COARSE_DIRT
                || m == Material.ROOTED_DIRT;
    }

    private static ToolResult fish(Vessel v, Location body, String forName) throws ToolException {
        String id = Realm.current().id;
        long now = System.currentTimeMillis();
        Long last = LAST_CAST.get(id);
        if (last != null && now - last < 12_000) throw new ToolException("Your line is still in the water.");
        Block water = null;
        double best = Double.MAX_VALUE;
        World w = body.getWorld();
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                for (int dy = -4; dy <= 2; dy++) {
                    Block b = w.getBlockAt(body.getBlockX() + dx, body.getBlockY() + dy, body.getBlockZ() + dz);
                    if (b.getType() != Material.WATER || !b.getRelative(0, 1, 0).getType().isAir()) continue;
                    double d = dx * dx + dz * dz + dy * dy;
                    if (d < best && d >= 4) {
                        best = d;
                        water = b;
                    }
                }
            }
        }
        if (water == null) throw new ToolException("There is no open water near your vessel; walk to a shore first.");
        Player to = forName == null ? null : Targets.player(forName);
        LAST_CAST.put(id, now);
        Location spot = water.getLocation().add(0.5, 1.0, 0.5);
        v.face(spot, 8);
        playPreset(v, "cast_line", 0);
        ItemStack catchItem = rollCatch();
        long delay = 50 + ThreadLocalRandom.current().nextInt(40);
        Bukkit.getScheduler().runTaskLater(GPTGOD.get(), () -> {
            if (!spot.isChunkLoaded()) return;
            w.spawnParticle(Particle.SPLASH, spot, 30, 0.3, 0.1, 0.3, 0.1);
            w.spawnParticle(Particle.FISHING, spot, 12, 0.2, 0.05, 0.2, 0.05);
            w.playSound(spot, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1f, 1f);
            Location feet = v.location();
            if (to != null && to.isOnline()) {
                to.getInventory().addItem(catchItem).values().forEach(r -> to.getWorld().dropItem(to.getLocation(), r));
                to.playSound(to.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
            } else if (feet != null) {
                w.dropItem(feet.add(0, 0.5, 0), catchItem);
            }
        }, delay);
        return ToolResult.ok("You cast your line into the water at %d %d %d. In a few seconds you reel in %s%s.",
                water.getX(), water.getY(), water.getZ(), pretty(catchItem.getType()),
                to != null ? " and hand it to " + to.getName() : ", which lands at your feet");
    }

    private static ItemStack rollCatch() {
        double r = ThreadLocalRandom.current().nextDouble();
        if (r < 0.5) return new ItemStack(Material.COD);
        if (r < 0.75) return new ItemStack(Material.SALMON);
        if (r < 0.83) return new ItemStack(Material.TROPICAL_FISH);
        if (r < 0.9) return new ItemStack(Material.PUFFERFISH);
        Material[] treasure = { Material.NAUTILUS_SHELL, Material.NAME_TAG, Material.SADDLE, Material.LILY_PAD,
                Material.BOW, Material.EXPERIENCE_BOTTLE };
        return new ItemStack(treasure[ThreadLocalRandom.current().nextInt(treasure.length)]);
    }

    private static void playPreset(Vessel v, String name, int loopSeconds) {
        Choreo.Anim a = Choreo.preset(name);
        if (a != null) v.animate(a, Math.max(1, loopSeconds));
    }

    private static String pretty(Material m) {
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    // ---- activity ------------------------------------------------------------------------------------

    private static GodTool activity() {
        return GodTool.sync("activity",
                "Your own pastimes, chosen of your free will and carried across thoughts: begin one (kind: "
                        + String.join(", ", Initiative.Kind.ids()) + "), record each step you take with "
                        + "progress, and end it when done or bored. Do the pastime with real powers (vessel_go, "
                        + "vessel_work, build, vessel_animate, vessel_say...); this only remembers it.",
                Schema.object()
                        .choice("action", "begin, progress or end.", true, List.of("begin", "progress", "end"))
                        .choice("kind", "begin: what kind of pastime.", false, Initiative.Kind.ids())
                        .str("about", "begin: in a few words, e.g. 'A cottage for Steve by the lake'.", false)
                        .str("plan", "begin: the steps you mean to take.", false)
                        .str("for", "begin: the mortal or place it is for or about.", false)
                        .str("note", "progress/end: what you just did or how it ended.", false),
                a -> {
                    if (GodSettings.initiativeLevel() == Initiative.Level.WATCH) {
                        throw new ToolException("The keepers of this server want you only to watch "
                                + "(initiative.level is watch).");
                    }
                    Memory mem = Memory.get();
                    long now = System.currentTimeMillis();
                    String action = a.str("action").toLowerCase(Locale.ROOT);
                    Initiative.Activity act;
                    try {
                        act = switch (action) {
                            case "begin" -> Initiative.begin(mem, Initiative.Kind.parse(a.str("kind", null)),
                                    a.str("about", null), a.str("plan", null), a.str("for", null), now);
                            case "progress" -> Initiative.progress(mem, a.str("note", null), now);
                            case "end" -> Initiative.end(mem, a.str("note", null), now);
                            default -> throw new ToolException("action must be begin, progress or end.");
                        };
                    } catch (Initiative.Refusal e) {
                        throw new ToolException(e.getMessage());
                    }
                    mem.save();
                    return switch (action) {
                        case "begin" -> ToolResult.ok("You take up %s: \"%s\". Start now: %s.", act.kindOf().label,
                                act.about, act.kindOf().how);
                        case "progress" -> ToolResult.ok("Noted (step %d of \"%s\").", act.steps, act.about);
                        default -> ToolResult.ok("You are done with \"%s\". Choose another whim when the mood "
                                + "takes you.", act.about);
                    };
                });
    }
}
