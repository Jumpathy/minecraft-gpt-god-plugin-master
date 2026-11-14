package net.bigyous.gptgodmc.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.ClientViews;
import net.bigyous.gptgodmc.god.Realm;

/**
 * The plugin's half of the optional GPTGOD client mod: channels, handshake and sending. Players who never
 * send a valid hello (vanilla Java, Bedrock through Geyser) are never sent anything; every send method
 * returns at once for them. Server thread only (except the session lookups).
 */
public final class ModLink implements Listener {
    public static final String HELLO = "gptgod:hello";
    public static final String MAIN = "gptgod:main";

    private static final ModSessions SESSIONS = new ModSessions();
    /** Last JSON sent per player and key ("god:<realm>"...), so state is only sent when it changed. */
    private static final Map<UUID, Map<String, String>> LAST = new HashMap<>();
    private static JavaPlugin plugin;
    private static BukkitTask ticker;

    private ModLink() {
    }

    public static void enable(JavaPlugin p) {
        if (!p.getConfig().getBoolean("client-mod.enabled", true)) return;
        plugin = p;
        Bukkit.getMessenger().registerOutgoingPluginChannel(p, MAIN);
        Bukkit.getMessenger().registerIncomingPluginChannel(p, HELLO,
                (channel, player, message) -> onHello(player, message));
        Bukkit.getPluginManager().registerEvents(new ModLink(), p);
        ticker = Bukkit.getScheduler().runTaskTimer(p, ModLink::tick, 40L, 10L);
        ModGui.enable(p);
        net.bigyous.gptgodmc.god.FxTriggers.enable(p);
    }

    public static void disable() {
        if (ticker != null) ticker.cancel();
        ticker = null;
        plugin = null;
        ModGui.disable();
        LAST.clear();
    }

    public static ModSessions sessions() {
        return SESSIONS;
    }

    /** Whether this player runs the mod and accepted this feature. */
    public static boolean has(Player p, String feature) {
        return p != null && SESSIONS.has(p.getUniqueId(), feature);
    }

    /** Sends a message if the player has the mod and the feature (else does nothing). */
    public static boolean send(Player p, String feature, JsonObject message) {
        if (plugin == null || p == null || !SESSIONS.has(p.getUniqueId(), feature)) return false;
        byte[] bytes = ModMessages.bytes(message);
        if (!SESSIONS.allow(p.getUniqueId(), feature, bytes.length, System.currentTimeMillis())) return false;
        p.sendPluginMessage(plugin, MAIN, bytes);
        return true;
    }

    /** Sends to every mod player with the feature within radius blocks of at (same world). */
    public static void sendNear(Location at, double radius, String feature, JsonObject message) {
        if (plugin == null || at == null || at.getWorld() == null || SESSIONS.players().isEmpty()) return;
        double r2 = radius * radius;
        for (UUID id : SESSIONS.players()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.getWorld().equals(at.getWorld()) && p.getLocation().distanceSquared(at) <= r2) {
                send(p, feature, message);
            }
        }
    }

    private static void onHello(Player player, byte[] message) {
        String version = GPTGOD.get() == null ? "?" : GPTGOD.get().getDescription().getVersion();
        ModSessions.Reply reply = SESSIONS.hello(player.getUniqueId(), message, version);
        // The welcome goes out even on a mismatch (with no features), so the mod can tell its player.
        if (plugin != null && player.getListeningPluginChannels().contains(MAIN)) {
            player.sendPluginMessage(plugin, MAIN, ModMessages.bytes(reply.welcome()));
        }
        LAST.remove(player.getUniqueId());
        if (reply.session() != null) {
            GPTGOD.LOGGER.info(player.getName() + " uses the GPTGOD client mod " + reply.session().modVersion()
                    + " (" + String.join(", ", reply.session().features()) + ")");
            refresh(player);
        }
    }

    /** Asks a client that just registered our channel to say hello (it may have missed the join moment). */
    @EventHandler
    public void onRegister(PlayerRegisterChannelEvent event) {
        if (!MAIN.equals(event.getChannel()) || plugin == null) return;
        JsonObject ping = new JsonObject();
        ping.addProperty("t", "ping");
        ping.addProperty("protocol", ModSessions.PROTOCOL);
        event.getPlayer().sendPluginMessage(plugin, MAIN, ModMessages.bytes(ping));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        SESSIONS.forget(id);
        LAST.remove(id);
        ModGui.forget(id);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        LAST.remove(event.getPlayer().getUniqueId());
    }

    private static void tick() {
        if (SESSIONS.players().isEmpty()) return;
        for (UUID id : SESSIONS.players()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                SESSIONS.forget(id);
                LAST.remove(id);
                continue;
            }
            refresh(p);
        }
    }

    /** Sends the god state, standing and quest for the player's world, each only when it changed. */
    public static void refresh(Player p) {
        if (!has(p, ModSessions.HUD) && !has(p, ModSessions.SKY)) return;
        Map<String, String> last = LAST.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        try {
            for (Realm r : Realm.godsOf(p.getWorld())) {
                changed(p, last, "god:" + r.id, ModMessages.god(ClientViews.god(r, p),
                        net.bigyous.gptgodmc.god.FxStyle.of(r).wire()));
                changed(p, last, "standing:" + r.id, ClientViews.standing(r, p));
                changed(p, last, "quest:" + r.id, ClientViews.quest(r));
            }
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("Client mod state for " + p.getName() + " failed: " + e);
        }
    }

    private static void changed(Player p, Map<String, String> last, String key, JsonObject message) {
        String json = ModMessages.json(message);
        if (json.equals(last.get(key))) return;
        String feature = has(p, ModSessions.HUD) ? ModSessions.HUD : ModSessions.SKY;
        if (send(p, feature, message)) last.put(key, json);
    }
}
