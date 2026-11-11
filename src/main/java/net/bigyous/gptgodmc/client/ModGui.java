package net.bigyous.gptgodmc.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.client.ModGuiRequests.Request;
import net.bigyous.gptgodmc.commands.GodCommand;
import net.bigyous.gptgodmc.god.Costs;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.vessel.VesselHeads;
import net.bigyous.gptgodmc.god.vessel.VesselLook;
import net.bigyous.gptgodmc.gui.Feedback;
import net.bigyous.gptgodmc.gui.GodManager;
import net.bigyous.gptgodmc.gui.ModGuiActions;
import net.bigyous.gptgodmc.gui.ModGuiViews;

/**
 * The plugin's half of the client mod's God Manager screen: listens for the screen's requests on
 * gptgod:main, checks gptgodmc.admin on every one, and while a screen is open pushes the part it shows
 * about twice a second, each message only when it changed. Players without the mod (or without the
 * permission) are never sent anything and keep the chest menu. Server thread only.
 */
public final class ModGui {
    private ModGui() {
    }

    private static final long TICKS = 10;
    /** Slow parts (the mortals list, costs) refresh every this many pushes unless something was just done. */
    private static final int SLOW = 4;
    private static final int JOURNAL_CHUNK = 30;
    private static final int JOURNAL_FIRST = 120;

    /** One open screen. */
    private static final class Sub {
        String realm;
        String part = "pantheon";
        Map<String, String> args = Map.of();
        /** Last JSON sent, by part key: a part is only sent again when it changed. */
        final Map<String, String> last = new HashMap<>();
        final Set<String> heads = new HashSet<>();
        /** Journal lines up to this seq were sent for journalRealm; -1 = nothing yet. */
        long journalSeq = -1;
        String journalRealm;
        int pushes;
    }

    private static final Map<UUID, Sub> OPEN = new ConcurrentHashMap<>();
    private static final ModGuiRequests.Limiter LIMITER = new ModGuiRequests.Limiter();
    private static JavaPlugin plugin;
    private static BukkitTask ticker;

    public static void enable(JavaPlugin p) {
        plugin = p;
        Bukkit.getMessenger().registerIncomingPluginChannel(p, ModLink.MAIN,
                (channel, player, message) -> receive(player, message));
        Feedback.screen(ModGui::note);
        ticker = Bukkit.getScheduler().runTaskTimer(p, ModGui::tick, 40L, TICKS);
        java.nio.file.Path usage = p.getDataFolder().toPath().resolve("usage.csv");
        Bukkit.getScheduler().runTaskAsynchronously(p, () -> Costs.loadBefore(usage));
    }

    public static void disable() {
        if (ticker != null) ticker.cancel();
        ticker = null;
        plugin = null;
        Feedback.screen(null);
        OPEN.clear();
    }

    /** Whether this player gets the screen instead of the chest menu: the mod with "gui", and the permission. */
    public static boolean canOpen(Player p) {
        return plugin != null && p != null && ModLink.has(p, ModSessions.GUI) && p.hasPermission(GodManager.PERMISSION);
    }

    /** Whether operators with the mod see API costs (client-mod.show-costs, on unless turned off). */
    static boolean showCosts() {
        return GPTGOD.get().getConfig().getBoolean("client-mod.show-costs", true);
    }

    /**
     * Opens the screen on the god of the player's world (/god manager, the star, the mod's key). Returns
     * false when this player has no screen: the caller then opens the chest menu.
     */
    public static boolean open(Player p) {
        if (!canOpen(p)) return false;
        Realm here = Realm.of(p.getWorld());
        Realm realm = here != null ? here : Realm.primary();
        Sub sub = new Sub();
        sub.realm = realm.id;
        if (!ModLink.send(p, ModSessions.GUI, ModGuiMessages.open(realm.id, Realm.several(), showCosts(),
                p.hasPermission(GodCommand.PANTHEON_PERMISSION)))) {
            return false;
        }
        OPEN.put(p.getUniqueId(), sub);
        push(p, sub, true);
        return true;
    }

    public static void forget(UUID player) {
        OPEN.remove(player);
        LIMITER.forget(player);
        Feedback.forget(player);
    }

    private static void receive(Player p, byte[] data) {
        if (plugin == null || !ModLink.has(p, ModSessions.GUI)) return;
        if (!LIMITER.allow(p.getUniqueId(), System.currentTimeMillis())) return;
        Request r = ModGuiRequests.parse(data);
        if (r == null) return;
        if (!p.hasPermission(GodManager.PERMISSION)) {
            OPEN.remove(p.getUniqueId());
            if (!r.type().equals(ModGuiRequests.CLOSE)) {
                ModLink.send(p, ModSessions.GUI, ModGuiMessages.result(r.id(), false, null,
                        "Only operators may command the god.", null, null));
            }
            return;
        }
        try {
            switch (r.type()) {
                case ModGuiRequests.OPEN -> open(p);
                case ModGuiRequests.CLOSE -> OPEN.remove(p.getUniqueId());
                case ModGuiRequests.SUB -> {
                    Sub sub = OPEN.computeIfAbsent(p.getUniqueId(), k -> new Sub());
                    if (r.realm() != null && Realm.byId(r.realm()) != null) sub.realm = r.realm();
                    sub.part = r.part();
                    sub.args = r.args();
                    push(p, sub, true);
                }
                case ModGuiRequests.ACT -> {
                    ModLink.send(p, ModSessions.GUI, ModGuiActions.act(p, r));
                    Sub sub = OPEN.get(p.getUniqueId());
                    if (sub != null) push(p, sub, true);
                }
                default -> {
                }
            }
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("God Manager screen request from " + p.getName() + " failed: " + e);
            ModLink.send(p, ModSessions.GUI, ModGuiMessages.result(r.id(), false, null,
                    "That failed on the server (see its console).", null, null));
        }
    }

    /** A line bound for chat: shown in the open screen instead. False = no screen, send to chat. */
    private static boolean note(Player p, String text) {
        if (!OPEN.containsKey(p.getUniqueId()) || !ModLink.has(p, ModSessions.GUI)) return false;
        return ModLink.send(p, ModSessions.GUI, ModGuiMessages.note(text, "info"));
    }

    private static void tick() {
        if (OPEN.isEmpty()) return;
        for (UUID id : List.copyOf(OPEN.keySet())) {
            Player p = Bukkit.getPlayer(id);
            Sub sub = OPEN.get(id);
            if (p == null || sub == null || !canOpen(p)) {
                forget(id);
                continue;
            }
            try {
                push(p, sub, false);
            } catch (RuntimeException e) {
                GPTGOD.LOGGER.warn("God Manager screen state for " + p.getName() + " failed: " + e);
            }
        }
    }

    /** Sends what the player's page shows, each part only when it changed. now = also the slow parts. */
    private static void push(Player p, Sub sub, boolean now) {
        boolean slow = now || sub.pushes % SLOW == 0;
        sub.pushes++;
        changed(p, sub, "catalog", null, sub.last.containsKey("catalog") ? null : ModGuiViews.catalog());
        changed(p, sub, "pantheon", null, ModGuiViews.pantheon());
        Set<String> looks = new LinkedHashSet<>();
        for (Realm r : Realm.all()) looks.add(ModGuiViews.lookOf(r));
        if (sub.part.equals("looks")) looks.addAll(VesselLook.NAMES);
        heads(p, sub, looks);

        Realm realm = sub.realm == null ? null : Realm.byId(sub.realm);
        switch (sub.part) {
            case "god", "looks" -> {
                if (realm == null) return;
                changed(p, sub, "god", realm.id, ModGuiViews.god(realm));
                if (sub.part.equals("god")) {
                    changed(p, sub, "commands", realm.id, ModGuiViews.commands(realm));
                    journal(p, sub, realm);
                }
            }
            case "commands" -> {
                if (realm == null) return;
                changed(p, sub, "god", realm.id, ModGuiViews.god(realm));
                changed(p, sub, "commands", realm.id, ModGuiViews.commands(realm));
            }
            case "mortals" -> {
                if (realm == null) return;
                if (slow) {
                    int page = 0;
                    try {
                        page = Integer.parseInt(sub.args.getOrDefault("page", "0"));
                    } catch (NumberFormatException e) {
                        // first page
                    }
                    changed(p, sub, "mortals", realm.id, ModGuiViews.mortals(realm, sub.args.get("query"), page));
                }
                changed(p, sub, "mortal", realm.id, ModGuiViews.mortal(realm, sub.args.get("player")));
            }
            case "journal" -> {
                if (realm != null) journal(p, sub, realm);
            }
            case "config" -> {
                for (String tab : net.bigyous.gptgodmc.gui.Advanced.TABS) {
                    changed(p, sub, "config:" + tab, null, ModGuiViews.config(tab));
                }
            }
            case "switches" -> changed(p, sub, "switches", null, ModGuiViews.switches());
            case "costs" -> {
                if (slow) changed(p, sub, "costs", null, ModGuiViews.costs(showCosts()));
            }
            default -> {
            }
        }
    }

    private static void changed(Player p, Sub sub, String key, String realm, JsonObject message) {
        if (message == null) return;
        String k = realm == null ? key : key + "@" + realm;
        String json = ModMessages.json(message);
        if (json.equals(sub.last.get(k))) return;
        if (ModLink.send(p, ModSessions.GUI, message)) sub.last.put(k, json);
    }

    /** New journal lines of this god since the last push (a fresh view starts with the latest). */
    private static void journal(Player p, Sub sub, Realm realm) {
        boolean fresh = sub.journalSeq < 0 || !realm.id.equals(sub.journalRealm);
        List<ModGuiMessages.JournalLine> lines = ModGuiViews.journal(realm, fresh ? 0 : sub.journalSeq,
                fresh ? JOURNAL_FIRST : JOURNAL_CHUNK * 3);
        if (fresh) {
            sub.journalRealm = realm.id;
            sub.journalSeq = 0;
        }
        if (lines.isEmpty() && !fresh) return;
        for (int i = 0; i == 0 || i < lines.size(); i += JOURNAL_CHUNK) {
            List<ModGuiMessages.JournalLine> chunk = lines.subList(Math.min(i, lines.size()),
                    Math.min(lines.size(), i + JOURNAL_CHUNK));
            if (!ModLink.send(p, ModSessions.GUI, ModGuiMessages.journal(realm.id, chunk, fresh && i == 0))) {
                if (fresh && i == 0) sub.journalSeq = -1; // try the whole first view again
                return;
            }
            if (!chunk.isEmpty()) sub.journalSeq = chunk.get(chunk.size() - 1).seq();
        }
    }

    /** Sends the head images this screen lacks (painted off the server thread, a dozen per message). */
    private static void heads(Player p, Sub sub, Set<String> looks) {
        Set<String> need = new LinkedHashSet<>();
        for (String look : looks) {
            if (sub.heads.add(look)) need.add(look);
        }
        if (need.isEmpty() || plugin == null) return;
        JavaPlugin pl = plugin;
        UUID id = p.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(pl, () -> {
            Map<String, byte[]> painted = new LinkedHashMap<>();
            for (String look : need) painted.put(look, VesselHeads.head(look));
            Bukkit.getScheduler().runTask(pl, () -> {
                Player still = Bukkit.getPlayer(id);
                if (still == null || OPEN.get(id) != sub) return;
                Map<String, byte[]> batch = new LinkedHashMap<>();
                for (Map.Entry<String, byte[]> e : painted.entrySet()) {
                    batch.put(e.getKey(), e.getValue());
                    if (batch.size() == 12) {
                        sendHeads(still, sub, batch);
                        batch = new LinkedHashMap<>();
                    }
                }
                if (!batch.isEmpty()) sendHeads(still, sub, batch);
            });
        });
    }

    private static void sendHeads(Player p, Sub sub, Map<String, byte[]> batch) {
        if (!ModLink.send(p, ModSessions.GUI, ModGuiMessages.heads(batch))) {
            sub.heads.removeAll(batch.keySet()); // over the rate limit: asked for again on the next push
        }
    }
}
