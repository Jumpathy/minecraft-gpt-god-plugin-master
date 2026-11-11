package net.bigyous.gptgodmc.god;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.loggables.GenericEventLoggable;

/**
 * The god's physical awareness, worked out by the server so it costs the god only a line or two:
 * what threatens each mortal and whether they can see it (a creeper behind them), hazards they
 * stand in, and what they have been building or digging in the last minutes. An unseen, close
 * danger also wakes the god (rarely), so it may warn them. The judgments are pure and unit tested.
 */
public final class Senses implements Listener {
    public static boolean enabled() {
        return GPTGOD.get().getConfig().getBoolean("senses.enabled", true);
    }

    public static boolean alerts() {
        return enabled() && GPTGOD.get().getConfig().getBoolean("senses.danger-alerts", true);
    }

    // ---- Threats (pure) -----------------------------------------------------------------------------

    /** One hostile near a mortal. */
    public record Threat(String kind, double distance, String side, boolean visible, boolean hunting) {
        /** It can hurt them before they notice: close, and behind them or out of sight. */
        public boolean unseen() {
            return !visible || side.equals("behind");
        }
    }

    /** Where something is relative to where a mortal faces: ahead, left, right or behind. */
    public static String side(float yaw, double dx, double dz) {
        double facing = Math.toRadians(yaw);
        double fx = -Math.sin(facing), fz = Math.cos(facing);
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) return "ahead";
        double dot = (fx * dx + fz * dz) / len;
        double cross = (fx * dz - fz * dx) / len;
        if (dot > 0.5) return "ahead";
        if (dot < -0.5) return "behind";
        return cross > 0 ? "right" : "left";
    }

    /** How pressing an unseen threat is: 0 none, 1 worth a line, 2 worth waking the god. */
    public static int urgency(List<Threat> threats, double health) {
        int level = 0;
        int hunting = 0;
        for (Threat t : threats) {
            if (t.hunting()) hunting++;
            boolean deadly = t.kind().equals("creeper") || t.kind().equals("warden");
            if (t.unseen() && t.distance() <= (deadly ? 7 : 4)) level = Math.max(level, deadly ? 2 : 1);
            if (t.kind().equals("warden") && t.distance() <= 16) level = 2;
        }
        if (hunting >= 3 && health <= 8) level = 2;
        return level;
    }

    /** One short phrase for the perception: "creeper 4 blocks BEHIND, unseen; 2 zombies ahead". */
    public static String describe(List<Threat> threats) {
        if (threats.isEmpty()) return "none";
        List<Threat> sorted = new ArrayList<>(threats);
        sorted.sort((a, b) -> Double.compare(a.distance(), b.distance()));
        List<String> out = new ArrayList<>();
        Map<String, Integer> rest = new LinkedHashMap<>();
        for (Threat t : sorted) {
            if (t.unseen() && t.distance() <= 10 && out.size() < 2) {
                out.add(t.kind() + " " + Math.round(t.distance()) + " blocks " + (t.side().equals("behind")
                        ? "BEHIND them" : "to their " + t.side() + ", out of sight") + (t.hunting() ? ", hunting them"
                                : "") + " (they have not seen it)");
            } else {
                rest.merge(t.kind(), 1, Integer::sum);
            }
        }
        if (!rest.isEmpty()) {
            List<String> r = new ArrayList<>();
            rest.forEach((k, n) -> r.add(n == 1 ? k : n + " " + k + "s"));
            out.add(String.join(", ", r) + " in view or further off");
        }
        return String.join("; ", out);
    }

    // ---- What they need (pure) ---------------------------------------------------------------------

    /** A word for an empty stomach, or null when they are fed: "getting hungry", "hungry" (cannot sprint), "starving". */
    public static String hunger(int food) {
        if (food <= 0) return "starving";
        if (food <= 6) return "hungry";
        return food <= 12 ? "getting hungry" : null;
    }

    /** "badly hurt" at a third of their health or less, "hurt" at half, else null. */
    public static String hurt(double health, double max) {
        if (max <= 0) return null;
        if (health <= max * 0.35) return "badly hurt";
        return health <= max * 0.5 ? "hurt" : null;
    }

    /** Why a mortal must take from the land right now ("hungry", "badly hurt"), or null when they are well. */
    public static String need(int food, double health, double max) {
        String hunger = hunger(food), hurt = hurt(health, max);
        return hunger == null ? hurt : hurt == null ? hunger : hunger + " and " + hurt;
    }

    // ---- What they are making (pure) ----------------------------------------------------------------

    /** One block a mortal placed or broke. */
    public record Change(long at, String material, int x, int y, int z, boolean placed) {
    }

    /** A sentence about recent work, or null when there was too little to say anything. */
    public static String describeWork(List<Change> changes, long now) {
        List<Change> placed = new ArrayList<>(), broken = new ArrayList<>();
        for (Change c : changes) {
            if (now - c.at() > WINDOW_MS) continue;
            (c.placed() ? placed : broken).add(c);
        }
        List<String> out = new ArrayList<>();
        if (placed.size() >= 6) out.add("building: " + work(placed, true));
        if (broken.size() >= 10) out.add("digging: " + work(broken, false));
        return out.isEmpty() ? null : String.join(" | ", out);
    }

    private static String work(List<Change> list, boolean placed) {
        Map<String, Integer> mats = new LinkedHashMap<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Change c : list) {
            mats.merge(c.material(), 1, Integer::sum);
            minX = Math.min(minX, c.x());
            maxX = Math.max(maxX, c.x());
            minY = Math.min(minY, c.y());
            maxY = Math.max(maxY, c.y());
            minZ = Math.min(minZ, c.z());
            maxZ = Math.max(maxZ, c.z());
        }
        List<Map.Entry<String, Integer>> top = new ArrayList<>(mats.entrySet());
        top.sort((a, b) -> b.getValue() - a.getValue());
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(3, top.size()); i++) names.add(top.get(i).getValue() + " " + top.get(i).getKey());
        int w = maxX - minX + 1, l = maxZ - minZ + 1, h = maxY - minY + 1;
        String shape = placed ? shape(w, l, h, list.size(), mats.keySet()) : dig(w, l, h, list.get(0).y(),
                list.get(list.size() - 1).y());
        return list.size() + " blocks (" + String.join(", ", names) + "), " + w + "x" + l + " wide, " + h + " high, near ("
                + (minX + maxX) / 2 + ", " + minY + ", " + (minZ + maxZ) / 2 + "): " + shape;
    }

    /** A guess at what is being built from its outline and materials. */
    public static String shape(int w, int l, int h, int blocks, java.util.Set<String> materials) {
        String all = String.join(" ", materials);
        if (all.contains("farmland") || all.contains("wheat") || all.contains("carrots") || all.contains("potatoes")
                || all.contains("sapling")) {
            return "looks like a farm or garden";
        }
        if (all.contains("rail")) return "looks like a railway";
        if (all.contains("redstone") || all.contains("piston") || all.contains("repeater") || all.contains("hopper")) {
            return "looks like a redstone contraption";
        }
        if (all.contains("tnt")) return "they are placing TNT";
        int big = Math.max(w, l), small = Math.min(w, l);
        if (h >= 6 && h >= big * 2) return "looks like a tower or pillar";
        if (h <= 2 && small >= 3) return blocks >= w * l * 0.6 ? "looks like a floor or platform" : "looks like a layout or foundation";
        if (small <= 2 && big >= 6 && h <= 3) return all.contains("fence") || all.contains("wall") ? "looks like a fence or wall" : "looks like a wall, bridge or path";
        if (small >= 4 && h >= 3) {
            return (all.contains("glass") || all.contains("door") || all.contains("stairs")) ? "looks like a house taking shape"
                    : "looks like the walls of a building";
        }
        return "something small so far";
    }

    private static String dig(int w, int l, int h, int firstY, int lastY) {
        if (h >= 6 && h > Math.max(w, l)) return lastY < firstY ? "a shaft going down" : "a shaft going up";
        if (Math.max(w, l) >= 8 && h <= 4) return "a tunnel";
        if (Math.min(w, l) >= 4 && h >= 3) return "clearing out a room or quarry";
        return "digging about";
    }

    // ---- Watching the world -------------------------------------------------------------------------

    static final long WINDOW_MS = 180_000;
    private static final int KEEP = 300;
    private static final Map<UUID, Deque<Change>> WORK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_ALERT = new ConcurrentHashMap<>();
    private static final long ALERT_GAP_MS = 60_000;

    private static void note(Player p, Block b, Material type, boolean placed) {
        Deque<Change> d = WORK.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
        synchronized (d) {
            d.add(new Change(System.currentTimeMillis(), type.getKey().getKey(), b.getX(), b.getY(), b.getZ(), placed));
            while (d.size() > KEEP) d.poll();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        note(e.getPlayer(), e.getBlock(), e.getBlock().getType(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        note(e.getPlayer(), e.getBlock(), e.getBlock().getType(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        WORK.remove(e.getPlayer().getUniqueId());
        LAST_ALERT.remove(e.getPlayer().getUniqueId());
    }

    /** What a mortal placed and broke in the last minutes, oldest first (for seeing what they are building). */
    public static List<Change> recentWork(UUID player) {
        Deque<Change> d = WORK.get(player);
        if (d == null) return List.of();
        synchronized (d) {
            return new ArrayList<>(d);
        }
    }

    /** The hostiles round a mortal, with where they are and whether the mortal can see them. Server thread. */
    public static List<Threat> threats(Player p) {
        List<Threat> out = new ArrayList<>();
        Location l = p.getLocation();
        for (Entity e : p.getNearbyEntities(16, 8, 16)) {
            if (!(e instanceof Enemy) || !(e instanceof LivingEntity mob) || mob.isDead()) continue;
            Location m = mob.getLocation();
            double dx = m.getX() - l.getX(), dz = m.getZ() - l.getZ();
            boolean hunting = e instanceof Mob mm && p.equals(mm.getTarget());
            out.add(new Threat(e.getType().getKey().getKey(), m.distance(l), side(l.getYaw(), dx, dz),
                    p.hasLineOfSight(mob), hunting));
        }
        return out;
    }

    /** Hazards a mortal stands in or beside, as a short phrase, or null. Server thread. */
    public static String hazards(Player p) {
        List<String> out = new ArrayList<>();
        if (p.getFireTicks() > 0) out.add("ON FIRE");
        if (p.getRemainingAir() < p.getMaximumAir() / 3 && p.isInWater()) out.add("running out of air");
        if (p.getFreezeTicks() > 60) out.add("freezing");
        Location l = p.getLocation();
        Block feet = l.getBlock();
        boolean lava = false;
        for (int dx = -2; dx <= 2 && !lava; dx++) {
            for (int dz = -2; dz <= 2 && !lava; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (feet.getRelative(dx, dy, dz).getType() == Material.LAVA) {
                        lava = true;
                        break;
                    }
                }
            }
        }
        if (lava) out.add("lava within 2 blocks");
        // A long drop right where they are heading.
        org.bukkit.util.Vector dir = l.getDirection().setY(0);
        if (dir.lengthSquared() > 1e-6 && p.isOnGround()) {
            dir.normalize();
            Block ahead = l.clone().add(dir.multiply(1.5)).getBlock();
            int drop = 0;
            Block b = ahead;
            while (drop < 24 && b.getY() > b.getWorld().getMinHeight() && b.isPassable() && !b.isLiquid()) {
                b = b.getRelative(0, -1, 0);
                drop++;
            }
            if (drop >= 8) out.add("a " + (drop >= 24 ? "24+" : String.valueOf(drop)) + "-block drop right ahead");
        }
        if (feet.getLightLevel() <= 3 && feet.getLightFromSky() <= 3) out.add("in darkness");
        return out.isEmpty() ? null : String.join(", ", out);
    }

    /** The perception line for one mortal: danger (with what they cannot see), hazards and recent work. */
    public static String line(Player p) {
        StringBuilder sb = new StringBuilder("danger: ").append(describe(threats(p)));
        String hz = hazards(p);
        if (hz != null) sb.append(" | hazards: ").append(hz);
        Deque<Change> d = WORK.get(p.getUniqueId());
        if (d != null) {
            List<Change> copy;
            synchronized (d) {
                copy = new ArrayList<>(d);
            }
            String work = describeWork(copy, System.currentTimeMillis());
            if (work != null) sb.append("\n    ").append(work);
        }
        return sb.toString();
    }

    /** Every two seconds: a close danger a mortal has not seen wakes the gods of that world (at most once a minute each). */
    public static void start() {
        Bukkit.getScheduler().runTaskTimer(GPTGOD.get(), () -> {
            if (!alerts()) return;
            long now = System.currentTimeMillis();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
                Long last = LAST_ALERT.get(p.getUniqueId());
                if (last != null && now - last < ALERT_GAP_MS) continue;
                List<Threat> threats;
                try {
                    threats = threats(p);
                } catch (RuntimeException e) {
                    continue;
                }
                if (urgency(threats, p.getHealth()) < 2) continue;
                LAST_ALERT.put(p.getUniqueId(), now);
                String what = describe(threats);
                String name = p.getName();
                EventLogger.log(p.getWorld(), () -> new GenericEventLoggable(name + " is in danger they may not see: "
                        + what + " (health " + Math.round(p.getHealth()) + ").",
                        name + " is in danger they cannot see: " + what.toLowerCase(Locale.ROOT)));
            }
        }, 100, 40);
    }
}
