package net.bigyous.gptgodmc.god;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.profile.PlayerTextures;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.loggables.GenericEventLoggable;

/**
 * The god's eye for how mortals look ({@code awareness.avatars}). Each skin is fetched once from Mojang's
 * texture server on a background thread and read by {@link Avatars} (no model call); its look is kept by
 * texture, and each mortal's last texture in avatars.json, so the god hears when someone comes back
 * looking different. Mortals who show up naked or nearly so are told to the god as an event (it is not
 * woken for it); what it makes of that is its own choice.
 */
public final class AvatarWatch implements Listener {
    /** Looks by texture address. */
    private static final Map<String, Avatars.Look> LOOKS = new ConcurrentHashMap<>();
    private static final Map<String, Long> FAILED = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> TOLD_EXPOSED = new ConcurrentHashMap<>();
    private static final long RETRY_MS = 10 * 60_000L, EXPOSED_GAP_MS = 30 * 60_000L;
    private static final int MAX_BYTES = 64 * 1024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ExecutorService pool;
    private static HttpClient http;
    private static Seen seen = new Seen();

    /** What was last seen of each mortal (by lower-case name), kept across restarts. */
    static final class Seen {
        Map<String, Entry> players = new LinkedHashMap<>();

        static final class Entry {
            String texture;
            String look;
            long at;
        }
    }

    public static void enable(GPTGOD plugin) {
        pool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "god-avatars");
            t.setDaemon(true);
            return t;
        });
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER)
                .build();
        seen = load();
        plugin.getServer().getPluginManager().registerEvents(new AvatarWatch(), plugin);
        for (Player p : Bukkit.getOnlinePlayers()) want(p, false);
    }

    public static void disable() {
        if (pool != null) pool.shutdownNow();
        pool = null;
        save();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // A moment later: skin plugins and Bedrock's Floodgate may set the skin just after joining.
        Bukkit.getScheduler().runTaskLater(GPTGOD.get(), () -> {
            if (p.isOnline()) want(p, true);
        }, 60L);
    }

    /**
     * Main thread: the words for how a mortal looks, with their armor taken into account, or null while
     * the skin is still being read (or when the god does not look at skins).
     */
    public static String phrase(Player p) {
        if (!GodSettings.awarenessAvatars()) return null;
        Skin s = skinOf(p);
        Avatars.Look look = s.url == null ? Avatars.defaultLook(s.cape) : LOOKS.get(s.url);
        if (look == null) {
            want(p, false);
            return null;
        }
        return Avatars.phrase(look, chestCovered(p), legsCovered(p));
    }

    // ---- Fetching ------------------------------------------------------------------------------------

    record Skin(String url, boolean cape) {
    }

    static Skin skinOf(Player p) {
        try {
            PlayerTextures t = p.getPlayerProfile().getTextures();
            URL skin = t.getSkin();
            return new Skin(skin == null ? null : skin.toString(), t.getCape() != null);
        } catch (RuntimeException e) {
            return new Skin(null, false);
        }
    }

    /** Reads the mortal's skin if it is not known yet; on joining, also tells the god what is notable. */
    private static void want(Player p, boolean joined) {
        if (pool == null || !GodSettings.awarenessAvatars()) return;
        Skin s = skinOf(p);
        UUID id = p.getUniqueId();
        String name = p.getName();
        if (s.url == null) {
            if (joined) noticed(id, name, null, Avatars.defaultLook(s.cape));
            return;
        }
        Avatars.Look known = LOOKS.get(s.url);
        if (known != null) {
            if (joined) noticed(id, name, s.url, known);
            return;
        }
        Long failed = FAILED.get(s.url);
        if (failed != null && System.currentTimeMillis() - failed < RETRY_MS) return;
        if (!allowed(s.url)) return;
        FAILED.put(s.url, System.currentTimeMillis()); // also marks it in flight
        String url = s.url;
        boolean cape = s.cape;
        pool.execute(() -> {
            Avatars.Look look = fetch(url, cape);
            if (look == null) return;
            LOOKS.put(url, look);
            FAILED.remove(url);
            GPTGOD plugin = GPTGOD.get();
            if (plugin == null || !plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player now = Bukkit.getPlayer(id);
                if (now != null) noticed(id, name, url, look);
            });
        });
    }

    /** Only Mojang's texture server is ever asked (a profile cannot send the server anywhere else). */
    static boolean allowed(String url) {
        try {
            URI u = URI.create(url);
            return "textures.minecraft.net".equalsIgnoreCase(u.getHost())
                    && ("http".equalsIgnoreCase(u.getScheme()) || "https".equalsIgnoreCase(u.getScheme()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static final Map<String, BufferedImage> IMAGES = new ConcurrentHashMap<>();

    /** Main thread: the address of a mortal's own skin, or null when they wear a default one. */
    public static String skinUrl(Player p) {
        return skinOf(p).url();
    }

    /** Any thread but the server's: the skin picture at that address (kept after the first read), or null. */
    public static BufferedImage image(String url) {
        if (url == null || !allowed(url)) return null;
        BufferedImage known = IMAGES.get(url);
        if (known != null) return known;
        try {
            HttpClient client = http != null ? http : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            URI u = URI.create(url.replaceFirst("^http:", "https:"));
            HttpResponse<byte[]> res = client.send(HttpRequest.newBuilder(u).timeout(Duration.ofSeconds(8)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200 || res.body().length > MAX_BYTES) return null;
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(res.body()));
            if (img != null) {
                if (IMAGES.size() > 64) IMAGES.clear();
                IMAGES.put(url, img);
            }
            return img;
        } catch (IOException | RuntimeException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static Avatars.Look fetch(String url, boolean cape) {
        try {
            URI u = URI.create(url.replaceFirst("^http:", "https:"));
            HttpRequest req = HttpRequest.newBuilder(u).timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200 || res.body().length > MAX_BYTES) return null;
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(res.body()));
            return img == null ? null : Avatars.read(img, cape);
        } catch (IOException | RuntimeException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // ---- Telling the god ------------------------------------------------------------------------------

    /** Main thread: a changed look, or a mortal who comes naked, is told to the gods of their world. */
    private static void noticed(UUID id, String name, String texture, Avatars.Look look) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        boolean chest = chestCovered(p), legs = legsCovered(p);
        String words = Avatars.phrase(look, chest, legs);
        String key = name.toLowerCase(Locale.ROOT);
        Seen.Entry before = seen.players.get(key);
        String tex = texture == null ? "default" : texture;
        boolean changed = before != null && before.texture != null && !before.texture.equals(tex);
        Seen.Entry now = new Seen.Entry();
        now.texture = tex;
        now.look = Avatars.phrase(look, false, false);
        now.at = System.currentTimeMillis();
        seen.players.put(key, now);
        save();

        long t = System.currentTimeMillis();
        boolean exposed = Avatars.exposed(look, chest, legs);
        if (exposed) {
            Long last = TOLD_EXPOSED.get(id);
            if (last != null && t - last < EXPOSED_GAP_MS) exposed = false;
            else TOLD_EXPOSED.put(id, t);
        }
        if (changed) {
            String was = before.look == null ? "" : " (before: " + before.look + ")";
            EventLogger.log(p.getWorld(), () -> new GenericEventLoggable(name + " comes back looking different: "
                    + words + was + "."));
        } else if (exposed) {
            EventLogger.log(p.getWorld(), () -> new GenericEventLoggable(name + " walks your world as good as naked: "
                    + words + "."));
        }
    }

    private static boolean chestCovered(Player p) {
        ItemStack c = p.getInventory().getChestplate();
        return c != null && !c.getType().isAir() && c.getType() != Material.ELYTRA;
    }

    private static boolean legsCovered(Player p) {
        ItemStack l = p.getInventory().getLeggings();
        return l != null && !l.getType().isAir();
    }

    // ---- Keeping it ----------------------------------------------------------------------------------

    private static Path file() {
        return GPTGOD.get().getDataFolder().toPath().resolve("avatars.json");
    }

    private static Seen load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Seen s = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Seen.class);
                if (s != null && s.players != null) return s;
            }
        } catch (IOException | RuntimeException e) {
            GPTGOD.LOGGER.warn("Could not read avatars.json: " + e.getMessage());
        }
        return new Seen();
    }

    private static synchronized void save() {
        if (GPTGOD.get() == null) return;
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(seen), StandardCharsets.UTF_8);
        } catch (IOException e) {
            GPTGOD.LOGGER.warn("Could not save avatars.json: " + e.getMessage());
        }
    }
}
