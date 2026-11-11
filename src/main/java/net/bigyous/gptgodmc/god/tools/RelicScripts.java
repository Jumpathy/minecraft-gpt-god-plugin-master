package net.bigyous.gptgodmc.god.tools;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Guard;
import net.bigyous.gptgodmc.god.Initiative;
import net.bigyous.gptgodmc.god.Journal;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.ScriptLibrary;
import net.bigyous.gptgodmc.god.art.RelicImbue;
import net.bigyous.gptgodmc.god.art.RelicPowers.Power;
import net.bigyous.gptgodmc.god.art.Relics.Relic;
import net.bigyous.gptgodmc.god.script.ScriptLimits;
import net.bigyous.gptgodmc.god.script.ScriptRun;

/**
 * A relic's script power (RelicPowers "script &lt;name&gt; [{args}]"): when the relic strikes, swings, is used,
 * kills or is hurt, one of its maker's saved scripts (the {@link ScriptTools script} power, {@link ScriptLibrary})
 * runs against the same walled-in world ({@link ScriptWorld}), told who holds the relic, what it struck, where
 * they stand and look, and which relic and form set it off. So a relic can do whatever its god can program.
 *
 * <p>It obeys everything the script power obeys (scripts off in config, initiative "watch", the guard's lock on
 * commands; every changing call still passes its power's gate) and spends less: a relic's script is a moment,
 * not a watch, so it gets no on/every handlers and a fraction of a script's budget ({@link #limits}); it runs
 * at most every {@link net.bigyous.gptgodmc.god.art.RelicPowers#SCRIPT_REST} seconds per power (the relic's
 * own cooldown), one at a time per bearer, a few at a time on the whole server. It never runs on the server
 * thread. A mistake goes to the god's journal (at most once a minute per relic), never to the bearer.
 */
public final class RelicScripts {
    private RelicScripts() {
    }

    /** Relic scripts running on the whole server at once. */
    static final int MAX_RUNNING = 4;
    private static final AtomicInteger RUNNING = new AtomicInteger();
    /** Bearers whose relic's script is still running: one at a time each. */
    private static final Set<UUID> BUSY = ConcurrentHashMap.newKeySet();
    /** When each relic last wrote a mistake to its god's journal. */
    private static final Map<String, Long> TOLD = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();

    /**
     * What one run of a relic's script may spend: the god's own script limits, cut down to a moment's work
     * (no handlers at all). Pure.
     */
    public static ScriptLimits limits(ScriptLimits mine) {
        return new ScriptLimits(Math.min(mine.instructions(), 1_000_000), Math.min(mine.seconds(), 2),
                Math.min(mine.waitTicks(), 200), Math.min(mine.hostCalls(), 120), Math.min(mine.powerCalls(), 4),
                Math.min(mine.commands(), 3), Math.min(mine.blocks(), 256), Math.min(mine.entities(), 4), 0, 1, 0);
    }

    /** Why a relic may not be imbued with this script now (phrased for the god), or null. */
    public static String problem(Realm realm, String name) {
        if (!ScriptTools.enabled()) return "the keepers of this server have turned scripts off.";
        if (realm == null) return "there is no god whose scripts it could run.";
        if (ScriptLibrary.of(realm).find(name) == null) {
            return "you have no saved script called '" + name + "' (save one first with the script power and save_as). "
                    + "Your saved scripts: " + ScriptLibrary.of(realm).index(24) + ".";
        }
        return null;
    }

    /** Sets a relic's script going. Server thread; returns at once. */
    public static void fire(Player bearer, Relic relic, Power power, LivingEntity target, boolean awake) {
        Realm realm = RelicImbue.owner(relic);
        if (realm == null || !ScriptTools.enabled()) return;
        if (RUNNING.get() >= MAX_RUNNING || !BUSY.add(bearer.getUniqueId())) return;
        Map<String, Object> context = context(bearer, relic, power, target, awake);
        RUNNING.incrementAndGet();
        Thread t = new Thread(() -> {
            try {
                realm.run(() -> run(realm, relic, power, context));
            } catch (Throwable e) {
                GPTGOD.LOGGER.warn("A relic's script crashed", e);
            } finally {
                BUSY.remove(bearer.getUniqueId());
                RUNNING.decrementAndGet();
            }
        }, "relic-script-" + relic.id());
        t.setDaemon(true);
        try {
            t.start();
        } catch (RuntimeException | Error e) {
            BUSY.remove(bearer.getUniqueId());
            RUNNING.decrementAndGet();
        }
    }

    /** The script's own thread, as the relic's god. */
    private static void run(Realm realm, Relic relic, Power power, Map<String, Object> context) {
        if (GodSettings.initiativeLevel() == Initiative.Level.WATCH) return;
        if (Guard.refusal("run_commands") != null) return; // the guard has locked programs, as it locks commands
        ScriptLibrary lib = ScriptLibrary.of(realm);
        ScriptLibrary.Entry saved = lib.find(power.word());
        if (saved == null) {
            tell(relic, "its script '" + power.word() + "' is no longer among your saved scripts, so it does nothing.");
            return;
        }
        JsonObject given = power.data().isEmpty() ? new JsonObject() : JsonParser.parseString(power.data()).getAsJsonObject();
        Map<String, Object> args = GSON.fromJson(ScriptLibrary.merged(saved, given), new TypeToken<LinkedHashMap<String, Object>>() {
        }.getType());
        if (args == null) args = new LinkedHashMap<>();
        args.putAll(context); // what happened is always the truth, whatever a parameter of the same name said
        ScriptWorld host = new ScriptWorld(saved.name + " (" + relic.name() + ")");
        ScriptRun run = new ScriptRun(saved.code, args, limits(ScriptTools.limits()), host);
        ScriptRun.Result r = run.run();
        if (r.listeners() > 0) run.stop("a relic's script is a moment: it keeps no handlers");
        lib.used(saved, System.currentTimeMillis());
        if (!r.ok()) tell(relic, "its script '" + saved.name + "' failed: " + oneLine(r.error(), 300));
    }

    /** Writes to the relic's god's journal, at most once a minute per relic. */
    private static void tell(Relic relic, String what) {
        long now = System.currentTimeMillis();
        Long last = TOLD.get(relic.id());
        if (last != null && now - last < 60_000) return;
        TOLD.put(relic.id(), now);
        Journal.add(Journal.Kind.ERROR, "Relic \"" + relic.name() + "\": " + what);
    }

    /**
     * What the script is told: who holds the relic, what it struck, where they stand and look, which relic.
     * Only plain values (strings, numbers, booleans, maps), as a script's args must be. Server thread.
     */
    static Map<String, Object> context(Player bearer, Relic relic, Power power, LivingEntity target, boolean awake) {
        Map<String, Object> out = new HashMap<>();
        out.put("bearer", bearer.getName());
        Location at = bearer.getLocation();
        out.put("at", block(at));
        Location eye = bearer.getEyeLocation();
        Vector look = eye.getDirection();
        out.put("look", Map.of("x", round(look.getX()), "y", round(look.getY()), "z", round(look.getZ())));
        RayTraceResult hit = bearer.getWorld().rayTraceBlocks(eye, look, 32, FluidCollisionMode.NEVER, true);
        out.put("aim", block(hit != null ? hit.getHitPosition().toLocation(bearer.getWorld())
                : eye.clone().add(look.clone().multiply(32))));
        out.put("target", null);
        if (target != null && !target.isDead()) {
            Map<String, Object> t = new HashMap<>(block(target.getLocation()));
            t.put("type", target.getType().getKey().getKey());
            if (target instanceof Player p) t.put("name", p.getName());
            t.put("health", round(target.getHealth()));
            out.put("target", t);
        }
        out.put("relic", Map.of("id", relic.id(), "name", relic.name(), "awake", awake, "trigger", power.trigger().id));
        return out;
    }

    private static Map<String, Object> block(Location l) {
        return Map.of("x", (double) l.getBlockX(), "y", (double) l.getBlockY(), "z", (double) l.getBlockZ());
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String oneLine(String s, int max) {
        String t = s == null ? "" : s.replace('\n', ' ').trim();
        return t.length() > max ? t.substring(0, max - 1) + "…" : t;
    }
}
