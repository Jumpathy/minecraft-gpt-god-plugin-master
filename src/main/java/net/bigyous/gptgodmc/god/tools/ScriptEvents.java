package net.bigyous.gptgodmc.god.tools;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.Guard;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.script.ScriptRun;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * What scripts with living handlers hear of the world: a mortal's place (every 5 ticks, for 'enter'), a
 * mortal hurt, a block broken, chat. It only passes facts to {@link ScriptRun#offer}; it watches (monitor
 * priority) and never changes or cancels an event. Registered the first time a script leaves a handler.
 */
final class ScriptEvents implements Listener {
    /** One script whose handlers are alive. */
    record Session(Realm realm, String name, ScriptRun run) {
    }

    private static final Set<Session> LIVE = ConcurrentHashMap.newKeySet();
    private static volatile boolean registered;

    private ScriptEvents() {
    }

    static void add(Session s) {
        LIVE.add(s);
        if (!registered && GPTGOD.get() != null && GPTGOD.get().isEnabled()) {
            synchronized (ScriptEvents.class) {
                if (registered) return;
                registered = true;
            }
            Bukkit.getScheduler().runTask(GPTGOD.get(), () -> {
                Bukkit.getPluginManager().registerEvents(new ScriptEvents(), GPTGOD.get());
                Bukkit.getScheduler().runTaskTimer(GPTGOD.get(), ScriptEvents::places, 5L, 5L);
            });
        }
    }

    static void remove(Session s) {
        LIVE.remove(s);
    }

    /** The living sessions of one god. */
    static java.util.List<Session> of(Realm realm) {
        java.util.List<Session> out = new java.util.ArrayList<>();
        for (Session s : LIVE) if (s.realm() == realm) out.add(s);
        return out;
    }

    private static void places() {
        for (Session s : LIVE) {
            if (!s.run().wants("move")) continue;
            for (Player p : s.realm().players()) {
                if (p.getGameMode() == GameMode.SPECTATOR) continue;
                s.run().offer("move", at(p.getName(), p.getLocation()));
            }
        }
    }

    private static Map<String, Object> at(String player, Location l) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("player", player);
        data.put("x", Math.round(l.getX() * 100) / 100.0);
        data.put("y", Math.round(l.getY() * 100) / 100.0);
        data.put("z", Math.round(l.getZ() * 100) / 100.0);
        return data;
    }

    private static void tell(World world, String event, java.util.function.Supplier<Map<String, Object>> data) {
        if (LIVE.isEmpty()) return;
        Map<String, Object> made = null;
        for (Session s : LIVE) {
            if (!s.run().wants(event) || !s.realm().governs(world)) continue;
            if (made == null) made = data.get();
            s.run().offer(event, made);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (LIVE.isEmpty() || !(event.getEntity() instanceof Player p)) return;
        tell(p.getWorld(), "hit", () -> {
            Map<String, Object> data = at(p.getName(), p.getLocation());
            data.put("damage", Math.round(event.getFinalDamage() * 10) / 10.0);
            data.put("cause", event.getCause().name().toLowerCase(Locale.ROOT));
            if (event instanceof EntityDamageByEntityEvent by) {
                data.put("by", by.getDamager() instanceof Player other ? other.getName()
                        : by.getDamager().getType().getKey().getKey());
            }
            return data;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (LIVE.isEmpty()) return;
        tell(event.getBlock().getWorld(), "break", () -> {
            Map<String, Object> data = at(event.getPlayer().getName(), event.getBlock().getLocation());
            data.put("block", event.getBlock().getType().getKey().getKey());
            return data;
        });
    }

    /** Chat comes off the server thread. A mortal's words reach a script cleaned, as text and nothing more. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (LIVE.isEmpty()) return;
        Player p = event.getPlayer();
        tell(p.getWorld(), "chat", () -> {
            Map<String, Object> data = at(p.getName(), p.getLocation());
            data.put("text", Guard.clean(PlainTextComponentSerializer.plainText().serialize(event.message())));
            return data;
        });
    }
}
