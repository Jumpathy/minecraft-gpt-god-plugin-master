package net.bigyous.gptgodmc.god.vessel;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Realm;

/**
 * Serves the vessel's resource pack ({@link VesselPack}) and keeps track of who loaded it. With
 * vessel.textures: custom the plugin builds the pack at startup, saves a copy to
 * plugins/gptgodmc/resourcepack/, serves it from a tiny HTTP server (vessel.resource-pack.port) or
 * from vessel.resource-pack.url, and offers it to each player as an extra pack next to the server's
 * own. Players whose client loads it see the skinned body; everyone else sees the block version.
 */
public final class VesselPackHost implements Listener {
    /** The pack's id on clients, so it stacks with (and never replaces) a server's own resource pack. */
    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("gptgodmc:vessel-skin".getBytes(StandardCharsets.UTF_8));

    private static volatile VesselPack.Built built;
    private static HttpServer server;
    private static final Set<UUID> loaded = ConcurrentHashMap.newKeySet();

    private VesselPackHost() {
    }

    /** True when bodies should be spawned with their skinned twins. */
    public static boolean active() {
        return built != null;
    }

    /** Whether this player's client has the vessel's textures. */
    public static boolean has(Player p) {
        return built != null && loaded.contains(p.getUniqueId()) && !bedrock(p);
    }

    /**
     * Bedrock players (through Geyser/Floodgate) cannot use a Java resource pack, though Geyser may
     * still report it as loaded; they always get the block body. Checked by reflection, no dependency.
     */
    static boolean bedrock(Player p) {
        UUID id = p.getUniqueId();
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object inst = api.getMethod("getInstance").invoke(null);
            if ((Boolean) api.getMethod("isFloodgatePlayer", UUID.class).invoke(inst, id)) return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Floodgate not installed
        }
        try {
            Class<?> api = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object inst = api.getMethod("api").invoke(null);
            return (Boolean) api.getMethod("isBedrockPlayer", UUID.class).invoke(inst, id);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    public static void start() {
        if (!GodSettings.vesselCustomTextures()) return;
        Bukkit.getPluginManager().registerEvents(new VesselPackHost(), GPTGOD.get());
        // Painting the atlases takes a moment; do it off the main thread.
        Thread t = new Thread(VesselPackHost::prepare, "god-vessel-pack");
        t.setDaemon(true);
        t.start();
    }

    private static void prepare() {
        try {
            VesselPack.Built pack = VesselPack.build();
            Path dir = GPTGOD.get().getDataFolder().toPath().resolve("resourcepack");
            Files.createDirectories(dir);
            Files.write(dir.resolve("gptgodmc-vessel.zip"), pack.zip());
            Files.writeString(dir.resolve("gptgodmc-vessel.sha1"), pack.hashHex(), StandardCharsets.UTF_8);
            if (GodSettings.vesselPackUrl().isBlank()) serve(pack);
            built = pack;
            GPTGOD.LOGGER.info("Vessel resource pack ready (" + pack.zip().length / 1024 + " KB, sha1 "
                    + pack.hashHex().substring(0, 12) + "), " + where());
            Bukkit.getScheduler().runTask(GPTGOD.get(), () -> {
                // A body that formed before the pack was ready gets its skinned twin now.
                for (Realm r : Realm.all()) {
                    r.run(() -> {
                        if (r.vessel().hasBody()) r.vessel().restyle();
                    });
                }
                for (Player p : Bukkit.getOnlinePlayers()) offer(p);
            });
        } catch (Exception e) {
            GPTGOD.LOGGER.error("Could not prepare the vessel's resource pack; it keeps its block look", e);
        }
    }

    /**
     * Builds the pack again after its contents changed (a relic was made or unmade) and, if it differs,
     * offers the new one to every online player who is not skipped; their game reloads its resources.
     * Does nothing until the first pack is ready, or when custom textures are off.
     */
    public static void republish(java.util.function.Predicate<Player> skip) {
        if (built == null) return;
        Thread t = new Thread(() -> {
            try {
                VesselPack.Built pack = VesselPack.build();
                VesselPack.Built before = built;
                if (before == null || pack.hashHex().equals(before.hashHex())) return;
                Path dir = GPTGOD.get().getDataFolder().toPath().resolve("resourcepack");
                Files.createDirectories(dir);
                Files.write(dir.resolve("gptgodmc-vessel.zip"), pack.zip());
                Files.writeString(dir.resolve("gptgodmc-vessel.sha1"), pack.hashHex(), StandardCharsets.UTF_8);
                built = pack;
                GPTGOD.LOGGER.info("Resource pack rebuilt (" + pack.zip().length / 1024 + " KB, sha1 "
                        + pack.hashHex().substring(0, 12) + ")");
                Bukkit.getScheduler().runTask(GPTGOD.get(), () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!skip.test(p)) offer(p);
                    }
                });
            } catch (Exception e) {
                GPTGOD.LOGGER.error("Could not rebuild the resource pack; players keep the one they have", e);
            }
        }, "god-vessel-pack");
        t.setDaemon(true);
        t.start();
    }

    private static String where() {
        if (!GodSettings.vesselPackUrl().isBlank()) return "players download it from " + GodSettings.vesselPackUrl();
        if (!GodSettings.vesselPackSend()) return "not sent (vessel.resource-pack.send: false)";
        return "served on port " + GodSettings.vesselPackPort();
    }

    private static void serve(VesselPack.Built pack) throws IOException {
        String bind = GodSettings.vesselPackBind();
        int port = GodSettings.vesselPackPort();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(bind), port), 0);
        server.setExecutor(Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "god-vessel-pack-http");
            t.setDaemon(true);
            return t;
        }));
        // Whatever pack is current when it is asked for (a relic made later rebuilds it).
        server.createContext("/", ex -> handle(ex, built != null ? built : pack));
        server.start();
    }

    private static void handle(HttpExchange ex, VesselPack.Built pack) throws IOException {
        try (ex) {
            String path = ex.getRequestURI().getPath();
            boolean get = ex.getRequestMethod().equals("GET"), head = ex.getRequestMethod().equals("HEAD");
            if (!(get || head) || !path.startsWith("/gptgodmc-vessel") || !path.endsWith(".zip")) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            ex.getResponseHeaders().set("Content-Type", "application/zip");
            ex.sendResponseHeaders(200, head ? -1 : pack.zip().length);
            if (get) {
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(pack.zip());
                }
            }
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        built = null;
        loaded.clear();
    }

    /** The address this player should download the pack from. */
    static String urlFor(Player p) {
        String own = GodSettings.vesselPackUrl();
        if (!own.isBlank()) return own;
        String host = GodSettings.vesselPackPublicHost();
        if (host.isBlank()) {
            // The address the player typed to reach this server is one they can reach.
            InetSocketAddress virtual = p.getVirtualHost();
            host = virtual != null ? virtual.getHostString() : "";
        }
        if (host.isBlank()) host = Bukkit.getIp();
        if (host.isBlank() || host.equals("0.0.0.0")) host = "localhost";
        return url(host, GodSettings.vesselPackPort(), built.hashHex());
    }

    static String url(String host, int port, String hash) {
        if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]";
        return "http://" + host + ":" + port + "/gptgodmc-vessel-" + hash.substring(0, 12) + ".zip";
    }

    private static void offer(Player p) {
        VesselPack.Built pack = built;
        if (pack == null || !p.isOnline()) return;
        if (!GodSettings.vesselPackSend() || bedrock(p)) return;
        p.addResourcePack(PACK_ID, urlFor(p), pack.sha1(), GodSettings.vesselPackPrompt(),
                GodSettings.vesselPackRequired());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        loaded.remove(p.getUniqueId());
        // Without our own pack the files ride in the server's pack, which loads before joining.
        if (!GodSettings.vesselPackSend()
                && p.getResourcePackStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            loaded.add(p.getUniqueId());
        }
        Vessel.refreshViewer(p);
        // A moment after joining, so the pack prompt does not race the login screens.
        Bukkit.getScheduler().runTaskLater(GPTGOD.get(), () -> offer(p), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        loaded.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        // With send: false the vessel's files are expected inside the server's own pack, whatever its id.
        if (GodSettings.vesselPackSend() && !PACK_ID.equals(e.getID())) return;
        Player p = e.getPlayer();
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.add(p.getUniqueId());
            case DECLINED, DISCARDED -> loaded.remove(p.getUniqueId());
            case FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL -> {
                loaded.remove(p.getUniqueId());
                GPTGOD.LOGGER.warn(p.getName() + " could not load the vessel's resource pack (" + e.getStatus()
                        + " from " + urlFor(p) + "); they see its block look. Check vessel.resource-pack.");
            }
            default -> {
                return;
            }
        }
        if (Bukkit.isPrimaryThread()) Vessel.refreshViewer(p);
        else Bukkit.getScheduler().runTask(GPTGOD.get(), () -> Vessel.refreshViewer(p));
    }
}
