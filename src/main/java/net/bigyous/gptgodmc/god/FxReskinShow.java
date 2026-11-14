package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.god.fx.FxExpr;
import net.bigyous.gptgodmc.god.fx.FxReskin;

/**
 * The server's part of a scene's "reskin" layers: blocks that look like another block are shown as it by
 * telling each viewer's client so (sendBlockChange), and told the truth again when the layer ends or the
 * scene stops. The world is never changed. A block only ever looks like one as solid as itself, so nobody
 * walks into a wall that is not there or falls through a floor that is. Layers that wear a painted texture
 * or a colour are drawn by the client mod; for those, only players without the mod are shown the "as" block.
 * Server thread only.
 */
public final class FxReskinShow {
    private static final class Layer {
        FxReskin spec;
        Material as;
        boolean everyone;
        final Set<Long> shown = new HashSet<>();
        double scanned = -1;
    }

    private final List<Layer> layers = new ArrayList<>();
    private final List<Player> full, plain;
    private final double[] vars = new double[FxExpr.VARS];
    private World world;

    private FxReskinShow(List<Player> full, List<Player> plain) {
        this.full = full;
        this.plain = plain;
    }

    /** The reskins of a scene that the server shows, or null when it has none. */
    public static FxReskinShow of(JsonArray scene, List<Player> full, List<Player> plain) {
        FxReskinShow show = new FxReskinShow(full, plain);
        for (JsonElement e : scene) {
            JsonObject o = e.getAsJsonObject();
            if (!o.has("type") || !"reskin".equals(o.get("type").getAsString())) continue;
            FxReskin spec = FxReskin.parse(o);
            if (spec == null || spec.as.isEmpty()) continue;
            Material as = Material.matchMaterial(spec.as);
            if (as == null || !as.isBlock()) continue;
            Layer l = new Layer();
            l.spec = spec;
            l.as = as;
            // With a texture or a colour as well, the mod draws that and only the others see the plain block.
            l.everyone = spec.texture.isEmpty() && spec.color < 0;
            show.layers.add(l);
        }
        return show.layers.isEmpty() ? null : show;
    }

    private List<Player> viewers(Layer l) {
        if (!l.everyone) return plain;
        List<Player> all = new ArrayList<>(full);
        all.addAll(plain);
        return all;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    /** Call as the scene plays (every other tick): grows, shrinks and ends each layer. */
    public void tick(int tick, Location here, double scale) {
        if (world != null && !world.equals(here.getWorld())) end();
        world = here.getWorld();
        for (Layer l : layers) {
            int t = tick - l.spec.delay;
            if (t < 0 || t > l.spec.duration) {
                restore(l);
                continue;
            }
            double r = Math.min(FxReskin.MAX_RADIUS, l.spec.reach(t, vars) * scale);
            if (Math.abs(r - l.scanned) < 0.4) continue;
            l.scanned = r;
            int cx = (int) Math.floor(here.getX() + l.spec.offset[0] * scale), cy = (int) Math.floor(here.getY() + l.spec.offset[1] * scale),
                    cz = (int) Math.floor(here.getZ() + l.spec.offset[2] * scale);
            int reach = (int) Math.ceil(r), up = l.spec.ball ? reach : l.spec.height;
            Set<Long> now = new HashSet<>();
            BlockData looks = l.as.createBlockData();
            List<Player> to = viewers(l);
            for (int dx = -reach; dx <= reach && now.size() < l.spec.limit; dx++) {
                for (int dz = -reach; dz <= reach && now.size() < l.spec.limit; dz++) {
                    for (int dy = -up; dy <= up && now.size() < l.spec.limit; dy++) {
                        if (!l.spec.inside(dx, dy, dz, r)) continue;
                        int x = cx + dx, y = cy + dy, z = cz + dz;
                        if (y < world.getMinHeight() || y >= world.getMaxHeight() || !l.spec.picks(x, y, z)) continue;
                        Block b = world.getBlockAt(x, y, z);
                        Material m = b.getType();
                        if (m.isAir() || m == l.as || m.isSolid() != l.as.isSolid() || !l.spec.changes(m.getKey().getKey())) continue;
                        long k = key(x, y, z);
                        now.add(k);
                        if (l.shown.add(k)) {
                            Location at = b.getLocation();
                            for (Player p : to) {
                                if (p.isOnline() && p.getWorld().equals(world)) p.sendBlockChange(at, looks);
                            }
                        }
                    }
                }
            }
            // What it no longer reaches looks like itself again.
            for (Iterator<Long> it = l.shown.iterator(); it.hasNext();) {
                long k = it.next();
                if (now.contains(k)) continue;
                truth(l, k);
                it.remove();
            }
        }
    }

    private void truth(Layer l, long k) {
        int x = (int) (k >> 38), y = (int) ((k << 26) >> 52), z = (int) ((k << 38) >> 38);
        Block b = world.getBlockAt(x, y, z);
        for (Player p : viewers(l)) {
            if (p.isOnline() && p.getWorld().equals(world)) p.sendBlockChange(b.getLocation(), b.getBlockData());
        }
    }

    private void restore(Layer l) {
        if (world == null || l.shown.isEmpty()) return;
        for (long k : l.shown) truth(l, k);
        l.shown.clear();
        l.scanned = -1;
    }

    /** Every block looks like itself again (the scene ended or was stopped). */
    public void end() {
        for (Layer l : layers) restore(l);
    }
}
