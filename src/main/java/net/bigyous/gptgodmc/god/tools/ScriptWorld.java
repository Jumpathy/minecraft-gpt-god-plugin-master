package net.bigyous.gptgodmc.god.tools;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Guard;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Spectacle;
import net.bigyous.gptgodmc.god.Speech;
import net.bigyous.gptgodmc.god.Sync;
import net.bigyous.gptgodmc.god.build.Builder;
import net.bigyous.gptgodmc.god.build.Plan;
import net.bigyous.gptgodmc.god.hooks.Lands;
import net.bigyous.gptgodmc.god.script.ScriptHost;
import net.bigyous.gptgodmc.god.script.ScriptRefusal;
import net.bigyous.gptgodmc.god.script.ScriptValues;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;

/**
 * What a script reaches of the server: {@link ScriptHost} over Paper, for one run of one god's script.
 * <p>
 * Nothing here is stronger than a power the god already has. Each call that changes the world first asks
 * whether the god may use the matching power now ({@link #gate}: its pantheon rank and the injection
 * guard), and keeps to that power's own limits; explosions, creatures and every power() go through
 * {@link ToolRegistry#execute} itself, as the god's own act; blocks are placed by {@link Builder} as one
 * work (mortals' structures refused, undone with unbuild); commands go through the same blocklist and
 * border check as run_commands. All world access happens on the server thread, in one visit per batch.
 */
final class ScriptWorld implements ScriptHost {
    private static final Gson GSON = new Gson();

    private final String label;
    private Memory.Work work;
    private int placed, leftAlone, claimed;
    /** What this script made (creatures and shapes), by the id the script knows it by. It can touch nothing else. */
    private final Map<Integer, UUID> made = new HashMap<>();
    private int nextId;

    /** @param label the name the script's blocks are remembered under, as one of the god's works */
    ScriptWorld(String label) {
        this.label = label;
    }

    /** The work this script's blocks became, or null when it placed none. */
    Memory.Work work() {
        return work;
    }

    int placed() {
        return placed;
    }

    int leftAlone() {
        return leftAlone;
    }

    /** Blocks not placed because the land is someone's (a region, claim or town). */
    int claimed() {
        return claimed;
    }

    // ---- ScriptHost ------------------------------------------------------------------------------------

    @Override
    public String block(String id) throws ScriptRefusal {
        try {
            return data(id).getAsString(true).replace("minecraft:", "");
        } catch (ToolException e) {
            throw new ScriptRefusal(e.getMessage());
        }
    }

    /** "oak_planks", "air", or with a state: "oak_stairs[facing=north]". Never the blocks no power may conjure. */
    private static BlockData data(String id) throws ToolException {
        String k = id.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
        int state = k.indexOf('[');
        String name = (state < 0 ? k : k.substring(0, state)).trim().replace(' ', '_');
        if (name.equals("air")) return Material.AIR.createBlockData();
        Material m = WorldTools.blockMaterial(name);
        if (state < 0) return m.createBlockData();
        try {
            return Bukkit.createBlockData("minecraft:" + name + k.substring(state));
        } catch (IllegalArgumentException e) {
            throw new ToolException("'" + id + "' is not a state " + name + " can have. Write it like oak_stairs[facing=north].");
        }
    }

    @Override
    public List<Outcome> perform(List<Call> calls) {
        try {
            return Sync.call(() -> {
                List<Outcome> out = new ArrayList<>(calls.size());
                for (Call c : calls) out.add(one(c));
                return out;
            }, 15_000);
        } catch (ToolException e) {
            List<Outcome> out = new ArrayList<>(calls.size());
            for (int i = 0; i < calls.size(); i++) out.add(Outcome.no(e.getMessage()));
            return out;
        }
    }

    private Outcome one(Call c) {
        try {
            return Outcome.ok(dispatch(c.fn(), c.args()));
        } catch (ToolException | ScriptRefusal e) {
            return Outcome.no(e.getMessage());
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("A script's call " + c.fn() + " failed", e);
            return Outcome.no("it failed unexpectedly (" + e + ")");
        }
    }

    @Override
    public Map<String, Object> power(String name, Map<String, Object> args) throws ScriptRefusal {
        if (Bukkit.isPrimaryThread()) throw new ScriptRefusal("powers cannot be used from here");
        JsonObject input = GSON.toJsonTree(args == null ? Map.of() : args).getAsJsonObject();
        ToolResult r = ToolRegistry.execute(name, input, "brain").result();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", !r.isError());
        String text = r.text() == null ? "" : r.text();
        out.put("text", text.length() > 600 ? text.substring(0, 599) + "…" : text);
        return out;
    }

    @Override
    public void sleep(int ticks) throws InterruptedException {
        Thread.sleep(ticks * 50L);
        if (GPTGOD.get() == null || !GPTGOD.get().isEnabled()) throw new InterruptedException("the server is closing");
    }

    // ---- The calls (server thread) ---------------------------------------------------------------------

    private Object dispatch(String fn, List<Object> a) throws ToolException, ScriptRefusal {
        switch (fn) {
            case "world.place":
                place(a);
                return null;
            case "world.block": {
                Location l = block(a, 0);
                return l.getBlock().getType().getKey().getKey();
            }
            case "world.top": {
                World w = world();
                int x = ScriptValues.whole(a, 0, "x"), z = ScriptValues.whole(a, 1, "z");
                loaded(w, x, z);
                return w.getHighestBlockYAt(x, z);
            }
            case "world.at": {
                Location l = Targets.location(ScriptValues.text(a, 0, "target"));
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("x", l.getBlockX());
                out.put("y", l.getBlockY());
                out.put("z", l.getBlockZ());
                out.put("world", l.getWorld().getName());
                return out;
            }
            case "world.lightning": {
                gate("smite");
                Location l = point(a, 0);
                loaded(l.getWorld(), l.getBlockX(), l.getBlockZ());
                if (ScriptValues.flag(a, 3)) l.getWorld().strikeLightningEffect(l);
                else l.getWorld().strikeLightning(l);
                return null;
            }
            case "world.particle":
                particle(a);
                return null;
            case "world.sound": {
                String id = ScriptValues.text(a, 0, "id").toLowerCase(Locale.ROOT);
                if (!id.matches("[a-z0-9_.:/-]{1,96}")) throw new ToolException("'" + id + "' is not a sound id. Ids look like block.bell.use.");
                Location l = point(a, 1);
                float volume = (float) Math.max(0.05, Math.min(4, ScriptValues.num(a, 4, "volume", 1)));
                float pitch = (float) Math.max(0.5, Math.min(2, ScriptValues.num(a, 5, "pitch", 1)));
                l.getWorld().playSound(l, id, volume, pitch);
                return null;
            }
            case "world.time": {
                World w = world();
                if (ScriptValues.has(a, 0)) {
                    gate("set_time");
                    Object v = a.get(0);
                    long t = v instanceof String s ? switch (s.trim().toLowerCase(Locale.ROOT)) {
                        case "dawn" -> 23000L;
                        case "morning" -> 1000L;
                        case "noon" -> 6000L;
                        case "dusk" -> 12000L;
                        case "night" -> 13500L;
                        case "midnight" -> 18000L;
                        default -> throw new ToolException("The time is a number 0-24000, or dawn, morning, noon, dusk, night, midnight.");
                    } : Math.floorMod((long) ScriptValues.num(a, 0, "ticks"), 24000L);
                    w.setTime(t);
                }
                return w.getTime();
            }
            case "world.weather": {
                World w = world();
                if (ScriptValues.has(a, 0)) {
                    gate("set_weather");
                    String kind = ScriptValues.text(a, 0, "kind").toLowerCase(Locale.ROOT);
                    int ticks = 300 * 20;
                    switch (kind) {
                        case "clear" -> {
                            w.setStorm(false);
                            w.setThundering(false);
                            w.setClearWeatherDuration(ticks);
                        }
                        case "rain" -> {
                            w.setStorm(true);
                            w.setThundering(false);
                            w.setWeatherDuration(ticks);
                        }
                        case "thunder" -> {
                            w.setStorm(true);
                            w.setThundering(true);
                            w.setWeatherDuration(ticks);
                            w.setThunderDuration(ticks);
                        }
                        default -> throw new ToolException("The weather is 'clear', 'rain' or 'thunder'.");
                    }
                }
                return w.isThundering() ? "thunder" : w.hasStorm() ? "rain" : "clear";
            }
            case "players.list": {
                List<Object> out = new ArrayList<>();
                for (Player p : Realm.current().players()) out.add(snapshot(p));
                return out;
            }
            case "players.get":
                try {
                    return snapshot(Targets.player(ScriptValues.text(a, 0, "name")));
                } catch (ToolException e) {
                    return null; // nobody of that name is here: the script can test for it
                }
            case "players.effect":
                effect(a);
                return null;
            case "players.title": {
                String title = clip(ScriptValues.text(a, 1, "title"), 80);
                String sub = clip(ScriptValues.text(a, 2, "subtitle", ""), 120);
                Title shown = Title.title(Component.text(title, Speech.VIOLET), Component.text(sub, Speech.DEEP_VIOLET),
                        Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofMillis(800)));
                for (Player p : Targets.players(ScriptValues.text(a, 0, "name"))) p.showTitle(shown);
                return null;
            }
            case "players.tell": {
                String text = clip(ScriptValues.text(a, 1, "text"), 256);
                Persona persona = Realm.current().persona();
                String god = persona == null ? "The god" : persona.shortName();
                Component line = Component.text("✦ " + god + ": ", Speech.DEEP_VIOLET).decoration(TextDecoration.ITALIC, true)
                        .append(Component.text(text, Speech.VIOLET).decoration(TextDecoration.ITALIC, false));
                for (Player p : Targets.players(ScriptValues.text(a, 0, "name"))) p.sendMessage(line);
                return null;
            }
            case "players.push": {
                gate("fling");
                Vector v = new Vector(ScriptValues.num(a, 1, "vx"), ScriptValues.num(a, 2, "vy"), ScriptValues.num(a, 3, "vz"));
                if (v.length() > 3.9) v = v.normalize().multiply(3.9); // as fast as fling may throw
                for (Player p : Targets.players(ScriptValues.text(a, 0, "name"))) {
                    p.setFallDistance(0);
                    p.setVelocity(v);
                }
                return null;
            }
            case "players.teleport": {
                gate("teleport");
                Location to = point(a, 1);
                if (to.getY() < to.getWorld().getMinHeight() || to.getY() > to.getWorld().getMaxHeight() + 64) {
                    throw new ToolException("That is outside the world's height.");
                }
                Location safe = BlessingTools.safeAbove(to);
                for (Player p : Targets.players(ScriptValues.text(a, 0, "name"))) {
                    Location spot = safe.clone();
                    spot.setYaw(p.getLocation().getYaw());
                    spot.setPitch(p.getLocation().getPitch());
                    p.teleport(spot);
                }
                return null;
            }
            case "command": {
                gate("run_commands");
                if (!GodSettings.commandsAllowed()) throw new ToolException("The server has disabled raw commands for you.");
                String text = ScriptValues.text(a, 0, "text");
                if (text.toLowerCase(Locale.ROOT).contains("falling_block")) {
                    throw new ToolException("Do not summon falling_block by command; use power('drop_block', {...}).");
                }
                WorldTools.CommandReport report = WorldTools.dispatch(List.of(text), true, WorldTools::routeToWorld);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("ok", !report.anyFailed());
                out.put("text", clip(report.text(), 600));
                return out;
            }
            case "world.spawn":
                return spawn(a);
            case "world.display":
                return display(a);
            case "world.info": {
                Location l = block(a, 0);
                Block b = l.getBlock();
                String full = b.getBlockData().getAsString();
                int bracket = full.indexOf('[');
                Lands.Claim claim = Lands.at(l);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("block", b.getType().getKey().getKey());
                out.put("state", bracket < 0 ? "" : full.substring(bracket + 1, full.length() - 1));
                out.put("biome", b.getBiome().getKey().getKey());
                out.put("light", (int) b.getLightLevel());
                out.put("sky", (int) b.getLightFromSky());
                out.put("claim", claim == null ? null : claim.describe());
                return out;
            }
            case "world.ray":
                return ray(point(a, 0), new Vector(ScriptValues.num(a, 3, "dx"), ScriptValues.num(a, 4, "dy"), ScriptValues.num(a, 5, "dz")),
                        ScriptValues.num(a, 6, "range", 32), null);
            case "players.looking": {
                Player p = Targets.player(ScriptValues.text(a, 0, "name"));
                return ray(p.getEyeLocation(), p.getEyeLocation().getDirection(), ScriptValues.num(a, 1, "range", 32), p);
            }
            case "world.near":
                return near(a);
            case "entity.get": {
                Entity e = made(a);
                return e == null ? null : describe(e);
            }
            case "entity.move": {
                Entity e = made(a);
                if (e == null) return null;
                Location to = keepFacing(point(a, 1), e);
                loaded(to.getWorld(), to.getBlockX(), to.getBlockZ());
                if (e instanceof Display d) {
                    d.setTeleportDuration((int) Math.max(0, Math.min(59, ScriptValues.num(a, 4, "ticks", 20))));
                    d.teleport(to);
                } else if (e instanceof Mob m) {
                    walk(m, to, Math.max(0.2, Math.min(2.5, ScriptValues.num(a, 4, "speed", 1))), 3);
                } else {
                    e.teleport(to);
                }
                return null;
            }
            case "entity.teleport": {
                Entity e = made(a);
                if (e == null) return null;
                Location to = keepFacing(point(a, 1), e);
                loaded(to.getWorld(), to.getBlockX(), to.getBlockZ());
                if (e instanceof Display d) d.setTeleportDuration(0);
                e.teleport(to);
                return null;
            }
            case "entity.push": {
                Entity e = made(a);
                if (e == null || e instanceof Display) return null;
                Vector v = new Vector(ScriptValues.num(a, 1, "vx"), ScriptValues.num(a, 2, "vy"), ScriptValues.num(a, 3, "vz"));
                if (v.length() > 3.9) v = v.normalize().multiply(3.9);
                e.setVelocity(v);
                return null;
            }
            case "entity.target": {
                Entity e = made(a);
                if (e instanceof Mob m) m.setTarget(ScriptValues.has(a, 1) ? Targets.player(ScriptValues.text(a, 1, "player")) : null);
                return null;
            }
            case "entity.set": {
                Entity e = made(a);
                if (e == null) return null;
                Map<String, Object> o = options(a, 1);
                if (e instanceof Display d) shape(d, o, false);
                else creature(e, o);
                return null;
            }
            case "entity.remove": {
                Entity e = made(a);
                if (e != null) e.remove(); // its id stays known: entity.get then says null, as for anything gone
                return null;
            }
            default:
                throw new ToolException("there is no such call");
        }
    }

    // ---- What the script made: creatures and shapes -----------------------------------------------------

    private int keep(Entity e) {
        made.put(++nextId, e.getUniqueId());
        return nextId;
    }

    /** The thing behind an id, or null when it is gone (dead, removed, its time up). An id never given is a mistake. */
    private Entity made(List<Object> a) throws ToolException, ScriptRefusal {
        int id = ScriptValues.whole(a, 0, "id");
        UUID uuid = made.get(id);
        if (uuid == null) throw new ToolException("this script made nothing with the id " + id);
        Entity e = Bukkit.getEntity(uuid);
        return e == null || !e.isValid() ? null : e;
    }

    private Integer idOf(Entity e) {
        for (Map.Entry<Integer, UUID> m : made.entrySet()) if (m.getValue().equals(e.getUniqueId())) return m.getKey();
        return null;
    }

    /** A creature made this very tick has not touched the ground yet and finds no path: ask again shortly. */
    private static void walk(Mob m, Location to, double speed, int tries) {
        if (!m.isValid() || m.getPathfinder().moveTo(to, speed) || tries <= 0 || GPTGOD.get() == null) return;
        Bukkit.getScheduler().runTaskLater(GPTGOD.get(), () -> walk(m, to, speed, tries - 1), 5L);
    }

    private static Location keepFacing(Location to, Entity e) {
        to.setYaw(e.getLocation().getYaw());
        to.setPitch(e.getLocation().getPitch());
        return to;
    }

    /** As spawn_entity: the same gate, forbidden creatures and count limit; but exactly here, and remembered by id. */
    private Object spawn(List<Object> a) throws ToolException, ScriptRefusal {
        gate("spawn_entity");
        EntityType type = WrathTools.entityType(ScriptValues.text(a, 0, "entity"));
        Location l = point(a, 1);
        loaded(l.getWorld(), l.getBlockX(), l.getBlockZ());
        int count = (int) Math.max(1, Math.min(GodSettings.maxEntitiesPerSpawn(), ScriptValues.num(a, 4, "count", 1)));
        List<Object> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) ids.add(keep(l.getWorld().spawnEntity(l, type)));
        return ids;
    }

    /** A display entity: seen, never touched; no collision, no harm. Gone when its seconds are up, and at a restart. */
    private Object display(List<Object> a) throws ToolException, ScriptRefusal {
        String kind = ScriptValues.text(a, 0, "kind").toLowerCase(Locale.ROOT);
        Class<? extends Display> type = switch (kind) {
            case "block" -> BlockDisplay.class;
            case "item" -> ItemDisplay.class;
            case "text" -> TextDisplay.class;
            default -> throw new ToolException("A shape is 'block', 'item' or 'text'.");
        };
        Location l = point(a, 1);
        loaded(l.getWorld(), l.getBlockX(), l.getBlockZ());
        Map<String, Object> o = options(a, 4);
        if (o.get(kind) == null) throw new ToolException("Say what it shows: {" + kind + ": ...}.");
        Display d = l.getWorld().spawn(l, type, e -> e.setPersistent(false));
        try {
            shape(d, o, true);
        } catch (ToolException | RuntimeException e) {
            d.remove();
            throw e;
        }
        int seconds = (int) Math.max(1, Math.min(ScriptTools.maxDisplaySeconds(), number(o, "seconds", 30)));
        UUID uuid = d.getUniqueId();
        Bukkit.getScheduler().runTaskLater(GPTGOD.get(), () -> {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) e.remove();
        }, seconds * 20L);
        return keep(d);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> options(List<Object> a, int i) throws ToolException {
        Object o = i < a.size() ? a.get(i) : null;
        if (o == null) return Map.of();
        if (!(o instanceof Map)) throw new ToolException("options must be an object, like {scale: 2}");
        return (Map<String, Object>) o;
    }

    private static double number(Map<String, Object> o, String key, double fallback) throws ToolException {
        Object v = o.get(key);
        if (v == null) return fallback;
        if (v instanceof Double d && !d.isNaN() && !d.isInfinite()) return d;
        throw new ToolException(key + " must be a number");
    }

    private static boolean truth(Object v) {
        return v instanceof Boolean b ? b : v instanceof Double d ? d != 0 : v != null;
    }

    /** Applies a shape's options. fresh = it was just made (a block is centred on its place even with no scale given). */
    private static void shape(Display d, Map<String, Object> o, boolean fresh) throws ToolException {
        if (o.get("ticks") != null) {
            d.setInterpolationDelay(0);
            d.setInterpolationDuration((int) Math.max(0, Math.min(100, number(o, "ticks", 0))));
        }
        Color color = null;
        if (o.get("color") != null) {
            color = Spectacle.color(String.valueOf(o.get("color")));
            if (color == null) throw new ToolException("Bad color '" + o.get("color") + "'. Use a hex color like #FF2200.");
        }
        if (d instanceof BlockDisplay b && o.get("block") != null) b.setBlock(data(String.valueOf(o.get("block"))));
        if (d instanceof ItemDisplay i && o.get("item") != null) {
            Material m = Material.matchMaterial(String.valueOf(o.get("item")).trim().toLowerCase(Locale.ROOT).replace(' ', '_'));
            if (m == null || !m.isItem() || m.isAir()) throw new ToolException("Unknown item '" + o.get("item") + "'.");
            i.setItemStack(new ItemStack(m));
        }
        if (d instanceof TextDisplay t) {
            if (o.get("text") != null) {
                t.text(Component.text(clip(String.valueOf(o.get("text")), 200),
                        color == null ? Speech.VIOLET : TextColor.color(color.asRGB())));
            }
            if (fresh && o.get("billboard") == null) t.setBillboard(Display.Billboard.CENTER);
        }
        if (o.get("glow") != null) {
            Object g = o.get("glow");
            Color c = g instanceof String s ? Spectacle.color(s) : null;
            if (g instanceof String && c == null) throw new ToolException("Bad glow color '" + g + "'. Use a hex color like #FF2200.");
            d.setGlowing(truth(g));
            if (c != null) d.setGlowColorOverride(c);
        }
        if (o.get("billboard") != null) {
            try {
                d.setBillboard(Display.Billboard.valueOf(String.valueOf(o.get("billboard")).trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new ToolException("billboard is fixed, vertical, horizontal or center.");
            }
        }
        if (o.get("bright") != null) d.setBrightness(truth(o.get("bright")) ? new Display.Brightness(15, 15) : null);
        boolean turned = o.get("yaw") != null || o.get("pitch") != null || o.get("roll") != null;
        if (!fresh && !turned && o.get("scale") == null) return;
        Transformation old = d.getTransformation();
        Vector3f scale = new Vector3f(old.getScale());
        Object s = o.get("scale");
        if (s instanceof List<?> three && three.size() == 3 && three.stream().allMatch(x -> x instanceof Double)) {
            scale = new Vector3f(size((Double) three.get(0)), size((Double) three.get(1)), size((Double) three.get(2)));
        } else if (s != null) {
            float one = size(number(o, "scale", 1));
            scale = new Vector3f(one, one, one);
        }
        Quaternionf turn = !turned ? new Quaternionf(old.getLeftRotation()) : new Quaternionf().rotateYXZ(
                (float) Math.toRadians(-number(o, "yaw", 0)), (float) Math.toRadians(number(o, "pitch", 0)),
                (float) Math.toRadians(number(o, "roll", 0)));
        Vector3f shift = new Vector3f(old.getTranslation());
        if (d instanceof BlockDisplay) {
            // A block is drawn from its corner: shift it so it turns and grows about its middle.
            shift = turn.transform(new Vector3f(scale).mul(0.5f)).negate();
        }
        d.setTransformation(new Transformation(shift, turn, scale, new Quaternionf(old.getRightRotation())));
    }

    private static float size(double v) {
        return (float) Math.max(0.05, Math.min(16, v));
    }

    private static void creature(Entity e, Map<String, Object> o) {
        if (o.get("name") != null) {
            e.customName(Component.text(clip(String.valueOf(o.get("name")), 48), Speech.VIOLET));
            e.setCustomNameVisible(true);
        }
        if (o.get("glowing") != null) e.setGlowing(truth(o.get("glowing")));
        if (o.get("silent") != null) e.setSilent(truth(o.get("silent")));
        if (o.get("gravity") != null) e.setGravity(truth(o.get("gravity")));
        if (o.get("ai") != null && e instanceof LivingEntity living) living.setAI(truth(o.get("ai")));
        if (o.get("baby") != null && e instanceof Ageable ageable) {
            if (truth(o.get("baby"))) ageable.setBaby();
            else ageable.setAdult();
        }
    }

    private Map<String, Object> describe(Entity e) {
        Location l = e.getLocation();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", e instanceof Player ? "player" : e.getType().getKey().getKey());
        String name = e instanceof Player p ? p.getName()
                : e.customName() == null ? null : PlainTextComponentSerializer.plainText().serialize(e.customName());
        out.put("name", name);
        out.put("x", round(l.getX()));
        out.put("y", round(l.getY()));
        out.put("z", round(l.getZ()));
        if (e instanceof LivingEntity living) out.put("health", round(living.getHealth()));
        out.put("id", idOf(e));
        return out;
    }

    private Object ray(Location from, Vector dir, double range, Entity self) throws ToolException {
        if (dir.lengthSquared() < 1e-9) throw new ToolException("The direction is all zeros.");
        loaded(from.getWorld(), from.getBlockX(), from.getBlockZ());
        RayTraceResult hit = from.getWorld().rayTrace(from, dir.clone().normalize(), Math.max(1, Math.min(64, range)),
                FluidCollisionMode.NEVER, true, 0.2, e -> e != self && !(e instanceof Display) && !(e instanceof Interaction));
        if (hit == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        Vector at = hit.getHitPosition();
        out.put("x", round(at.getX()));
        out.put("y", round(at.getY()));
        out.put("z", round(at.getZ()));
        out.put("block", hit.getHitBlock() == null ? null : hit.getHitBlock().getType().getKey().getKey());
        out.put("face", hit.getHitBlockFace() == null ? null : hit.getHitBlockFace().name().toLowerCase(Locale.ROOT));
        out.put("entity", hit.getHitEntity() == null ? null : describe(hit.getHitEntity()));
        return out;
    }

    private Object near(List<Object> a) throws ToolException, ScriptRefusal {
        Location l = point(a, 0);
        loaded(l.getWorld(), l.getBlockX(), l.getBlockZ());
        double r = Math.max(1, Math.min(32, ScriptValues.num(a, 3, "r")));
        String type = ScriptValues.has(a, 4) ? ScriptValues.text(a, 4, "type").toLowerCase(Locale.ROOT).replace("minecraft:", "") : null;
        List<Entity> found = new ArrayList<>();
        for (Entity e : l.getWorld().getNearbyEntities(l, r, r, r)) {
            // Shapes and hitboxes that are not this script's are other things' furniture (a god's body, a relic).
            if ((e instanceof Display || e instanceof Interaction) && idOf(e) == null) continue;
            String kind = e instanceof Player ? "player" : e.getType().getKey().getKey();
            if (type == null || type.equals(kind)) found.add(e);
        }
        found.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(l)));
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < found.size() && i < 40; i++) out.add(describe(found.get(i)));
        return out;
    }

    /** Refuses when the god may not use this power right now (its rank among gods, or its guard after a manipulation). */
    static void gate(String power) throws ToolException {
        String refused = PantheonTools.refusal(power);
        if (refused == null) refused = Guard.refusal(power);
        if (refused != null) throw new ToolException(refused);
    }

    private static World world() throws ToolException {
        World w = Realm.current().world();
        if (w == null) throw new ToolException("You have no world to act in right now.");
        return w;
    }

    /** Scripts work where the world is awake: they never load or generate far-off land. */
    private static void loaded(World w, int x, int z) throws ToolException {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) {
            throw new ToolException("the land at " + x + ", " + z + " is asleep (no mortal is near it). Work where mortals are.");
        }
    }

    private static Location block(List<Object> a, int from) throws ToolException, ScriptRefusal {
        World w = world();
        int x = ScriptValues.whole(a, from, "x"), y = ScriptValues.whole(a, from + 1, "y"), z = ScriptValues.whole(a, from + 2, "z");
        loaded(w, x, z);
        return new Location(w, x, y, z);
    }

    private static Location point(List<Object> a, int from) throws ToolException, ScriptRefusal {
        double x = ScriptValues.num(a, from, "x"), y = ScriptValues.num(a, from + 1, "y"), z = ScriptValues.num(a, from + 2, "z");
        if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 4096) throw new ToolException("That is outside the world.");
        return new Location(world(), x, y, z);
    }

    private void place(List<Object> placements) throws ToolException {
        gate("build");
        World w = world();
        if (placed + placements.size() > GodSettings.maxBlockChanges()) {
            throw new ToolException("That would change " + (placed + placements.size()) + " blocks; the limit is "
                    + GodSettings.maxBlockChanges() + ".");
        }
        Placement first = (Placement) placements.get(0);
        Location anchor = new Location(w, first.x(), first.y(), first.z());
        Map<String, BlockData> known = new LinkedHashMap<>();
        Plan plan = new Plan();
        int skipped = 0;
        boolean respect = Lands.respect() && !Lands.names().isEmpty();
        for (Object o : placements) {
            Placement p = (Placement) o;
            loaded(w, p.x(), p.z());
            if (p.y() < w.getMinHeight() || p.y() >= w.getMaxHeight()) {
                throw new ToolException("y " + p.y() + " is outside the world (" + w.getMinHeight() + " to " + (w.getMaxHeight() - 1) + ").");
            }
            BlockData d = known.get(p.block());
            if (d == null) {
                d = data(p.block());
                known.put(p.block(), d);
            }
            Block there = w.getBlockAt(p.x(), p.y(), p.z());
            if (respect && Lands.at(there.getLocation()) != null) {
                claimed++;
                continue;
            }
            // What holds things (chests, signs, spawners) and what cannot be broken is never overwritten.
            if (there.getState(false) instanceof TileState || there.getType().getHardness() < 0) {
                skipped++;
                continue;
            }
            plan.set(p.x() - first.x(), p.y() - first.y(), p.z() - first.z(), d.clone());
        }
        leftAlone += skipped;
        if (plan.size() == 0) return;
        if (work == null) work = Builder.build(plan, anchor, label, "script", false, null);
        else Builder.extend(work, plan, anchor);
        placed += plan.size();
    }

    private static void particle(List<Object> a) throws ToolException, ScriptRefusal {
        String name = ScriptValues.text(a, 0, "name");
        Particle particle = Spectacle.particle(name);
        if (particle == null) {
            try {
                particle = Particle.valueOf(name.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", "").replace(' ', '_'));
            } catch (IllegalArgumentException e) {
                throw new ToolException("Unknown particle '" + name + "'. Some you can use: " + String.join(", ", Spectacle.particleNames()) + ".");
            }
        }
        Location l = point(a, 1);
        int count = (int) Math.max(1, Math.min(200, ScriptValues.num(a, 4, "count", 1)));
        double spread = Math.max(0, Math.min(8, ScriptValues.num(a, 5, "spread", 0)));
        Class<?> needs = particle.getDataType();
        if (needs == Void.class) {
            l.getWorld().spawnParticle(particle, l, count, spread, spread, spread, 0.0);
        } else if (needs == Particle.DustOptions.class) {
            Color color = ScriptValues.has(a, 6) ? Spectacle.color(ScriptValues.text(a, 6, "color")) : Color.fromRGB(0xB24BF3);
            if (color == null) throw new ToolException("Bad color '" + a.get(6) + "'. Use a hex color like #FF2200.");
            l.getWorld().spawnParticle(particle, l, count, spread, spread, spread, 0.0, new Particle.DustOptions(color, 1.4f));
        } else {
            throw new ToolException("The particle '" + name + "' needs more than a script can give it. Use dust (with a color) or one of: "
                    + String.join(", ", Spectacle.particleNames()) + ".");
        }
    }

    /** The same reach as apply_effect: 1-600 s, level 1-5, 'fire' sets ablaze. */
    private static void effect(List<Object> a) throws ToolException, ScriptRefusal {
        gate("apply_effect");
        List<Player> players = Targets.players(ScriptValues.text(a, 0, "name"));
        String id = ScriptValues.text(a, 1, "effect").toLowerCase(Locale.ROOT).replace(' ', '_').replace("minecraft:", "");
        int seconds = (int) Math.max(1, Math.min(600, ScriptValues.num(a, 2, "seconds")));
        int level = (int) Math.max(1, Math.min(5, ScriptValues.num(a, 3, "level", 1)));
        if (id.equals("fire") || id.equals("burning") || id.equals("ignite")) {
            for (Player p : players) p.setFireTicks(seconds * 20);
            return;
        }
        PotionEffectType type = id.matches("[a-z0-9_.-]+") ? PotionEffectType.getByKey(NamespacedKey.minecraft(id)) : null;
        if (type == null) {
            throw new ToolException("Unknown effect '" + id + "'. Try speed, strength, regeneration, slowness, weakness, "
                    + "blindness, levitation, glowing, night_vision, poison.");
        }
        for (Player p : players) p.addPotionEffect(new PotionEffect(type, seconds * 20, level - 1, false, true, true));
    }

    private static Map<String, Object> snapshot(Player p) {
        Location l = p.getLocation();
        Vector look = l.getDirection();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", p.getName());
        out.put("x", round(l.getX()));
        out.put("y", round(l.getY()));
        out.put("z", round(l.getZ()));
        out.put("yaw", round(l.getYaw()));
        out.put("pitch", round(l.getPitch()));
        Map<String, Object> dir = new LinkedHashMap<>();
        dir.put("x", round(look.getX()));
        dir.put("y", round(look.getY()));
        dir.put("z", round(look.getZ()));
        out.put("look", dir);
        out.put("health", round(p.getHealth()));
        out.put("food", p.getFoodLevel());
        ItemStack held = p.getInventory().getItemInMainHand();
        out.put("held", held.getType().isAir() ? "air" : held.getType().getKey().getKey());
        out.put("sneaking", p.isSneaking());
        out.put("mode", p.getGameMode().name().toLowerCase(Locale.ROOT));
        out.put("world", l.getWorld().getName());
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String clip(String s, int max) {
        String t = s.replaceAll("[\\u00a7\\p{Cntrl}]+", " ").trim();
        return t.length() > max ? t.substring(0, max - 1) + "…" : t;
    }
}
