package net.bigyous.gptgodmc.god;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.client.ModLink;
import net.bigyous.gptgodmc.god.fx.FxExpr;
import net.bigyous.gptgodmc.god.fx.FxTrigger;
import net.bigyous.gptgodmc.loggables.GenericEventLoggable;

/**
 * The server's side of a scene's "trigger" layers ({@link FxTrigger}). A mortal with the client mod has their
 * own game watch the condition and tell us when it fires; for everyone else it is watched here, every 2 ticks.
 * Either way the god is told what the mortal did, in the god's own words. A client is only believed about a
 * trigger it was really shown, while that scene can still be running, and no oftener than the trigger allows.
 * Server thread only.
 */
public final class FxTriggers {
    private FxTriggers() {
    }

    static final int MAX_LIVE = 256;
    static final int MAX_MESSAGE = 256;
    /** No mortal sets off triggers faster than this, whatever their game says. */
    static final long PLAYER_GAP_MS = 400;

    private static final class Live {
        String realm;
        FxTrigger spec;
        final Set<UUID> viewers = new HashSet<>();
        long until;
        final Map<UUID, Long> fired = new HashMap<>();
    }

    private static final Map<String, Live> LIVE = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    public static void enable(JavaPlugin plugin) {
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, ModLink.MAIN, (channel, player, message) -> receive(player, message));
    }

    public static void disable() {
        LIVE.clear();
        LAST.clear();
    }

    /** Whether these layers hold a trigger at all. */
    public static boolean any(JsonArray layers) {
        for (JsonElement e : layers) {
            if (e.isJsonObject() && isTrigger(e.getAsJsonObject())) return true;
        }
        return false;
    }

    private static boolean isTrigger(JsonObject l) {
        JsonElement t = l.get("type");
        return t != null && t.isJsonPrimitive() && t.getAsString().equals("trigger");
    }

    /**
     * Readies a scene's triggers for the mod: each gets a key of its own for this showing, by which these
     * viewers' games may report it for as long as the scene can run. Returns the layers to send.
     */
    public static JsonArray arm(Realm realm, JsonArray layers, Collection<Player> viewers, int lengthTicks, boolean loop) {
        if (viewers.isEmpty() || !any(layers)) return layers;
        long now = System.currentTimeMillis();
        LIVE.values().removeIf(l -> l.until < now);
        JsonArray out = layers.deepCopy();
        for (JsonElement e : out) {
            if (!e.isJsonObject() || !isTrigger(e.getAsJsonObject()) || LIVE.size() >= MAX_LIVE) continue;
            JsonObject l = e.getAsJsonObject();
            String key = Long.toString(ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE, 36);
            l.addProperty("key", key);
            FxTrigger spec = FxTrigger.parse(l);
            if (spec == null || spec.tell.isEmpty()) continue;
            Live live = new Live();
            live.realm = realm.id;
            live.spec = spec;
            for (Player p : viewers) live.viewers.add(p.getUniqueId());
            live.until = now + (loop ? 30 * 60_000L : lengthTicks * 50L + 15_000L);
            LIVE.put(key, live);
        }
        return out;
    }

    private static void receive(Player p, byte[] data) {
        if (p == null || data == null || data.length == 0 || data.length > MAX_MESSAGE) return;
        String key;
        try {
            JsonElement root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return;
            JsonObject o = root.getAsJsonObject();
            if (!o.has("t") || !o.get("t").isJsonPrimitive() || !o.get("t").getAsString().equals("trigger")) return;
            if (!o.has("key") || !o.get("key").isJsonPrimitive()) return;
            key = o.get("key").getAsString();
        } catch (RuntimeException e) {
            return;
        }
        Live live = LIVE.get(key);
        long now = System.currentTimeMillis();
        if (live == null || live.until < now || !live.viewers.contains(p.getUniqueId())) return;
        Long before = live.fired.get(p.getUniqueId());
        if (before != null && (live.spec.again == 0 || now - before < live.spec.again * 45L)) return;
        if (!allowed(p, now)) return;
        live.fired.put(p.getUniqueId(), now);
        Realm realm = Realm.byId(live.realm);
        if (realm != null) tell(realm, live.spec, p);
    }

    private static boolean allowed(Player p, long now) {
        Long last = LAST.get(p.getUniqueId());
        if (last != null && now - last < PLAYER_GAP_MS) return false;
        if (LAST.size() > 2000) LAST.clear();
        LAST.put(p.getUniqueId(), now);
        return true;
    }

    /** What the god hears when a trigger fires. */
    static String told(String player, String tell) {
        return player + " " + tell + (tell.endsWith(".") ? "" : ".") + " (a trigger you set in a scene)";
    }

    private static void tell(Realm realm, FxTrigger spec, Player p) {
        if (spec.tell.isEmpty()) return;
        String text = told(p.getName(), spec.tell);
        realm.run(() -> EventLogger.log(() -> new GenericEventLoggable(text, spec.wake ? text : null)));
    }

    /** Watches a scene's triggers for the mortals without the mod; made by FxShow, ticked from its timer. */
    static final class Plain {
        private final Realm realm;
        private final List<FxTrigger> specs = new ArrayList<>();
        private final List<Player> players;
        private final Map<String, FxTrigger.Watch> watches = new HashMap<>();
        private final double[] vars = new double[FxExpr.VARS];

        private Plain(Realm realm, List<Player> players) {
            this.realm = realm;
            this.players = players;
        }

        static Plain of(Realm realm, JsonArray layers, List<Player> plain) {
            if (plain.isEmpty() || !any(layers)) return null;
            Plain out = new Plain(realm, plain);
            for (JsonElement e : layers) {
                if (!e.isJsonObject() || !isTrigger(e.getAsJsonObject())) continue;
                FxTrigger spec = FxTrigger.parse(e.getAsJsonObject());
                if (spec != null) out.specs.add(spec);
            }
            return out.specs.isEmpty() ? null : out;
        }

        /** @param ticks how many ticks passed since the last call */
        void tick(int tick, int ticks, Location here, double scale) {
            for (int k = 0; k < specs.size(); k++) {
                FxTrigger spec = specs.get(k);
                int t = tick - spec.delay;
                if (t < 0 || t > spec.duration) continue;
                for (Player p : players) {
                    if (!p.isOnline() || !p.getWorld().equals(here.getWorld())) continue;
                    Location eye = p.getEyeLocation();
                    Vector look = eye.getDirection();
                    FxExpr.viewer(vars, (eye.getX() - here.getX()) / scale - spec.offset[0], (eye.getY() - here.getY()) / scale - spec.offset[1],
                            (eye.getZ() - here.getZ()) / scale - spec.offset[2], look.getX(), look.getY(), look.getZ(),
                            p.getVelocity().length());
                    FxTrigger.Watch watch = watches.computeIfAbsent(k + ":" + p.getUniqueId(), x -> new FxTrigger.Watch());
                    if (!watch.tick(spec, t, ticks, vars)) continue;
                    if (!allowed(p, System.currentTimeMillis())) continue;
                    tell(realm, spec, p);
                    if (!spec.then.isEmpty()) FxShow.play(realm, null, here.clone(), null, scale, false, spec.then, p);
                }
            }
        }
    }
}
