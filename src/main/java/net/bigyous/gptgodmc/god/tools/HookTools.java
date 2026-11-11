package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.Sync;
import net.bigyous.gptgodmc.god.hooks.Disguises;
import net.bigyous.gptgodmc.god.hooks.LandHistory;
import net.bigyous.gptgodmc.god.hooks.Lands;
import net.bigyous.gptgodmc.god.hooks.Wards;

/**
 * Powers that exist because of other plugins a server may have (god/hooks): ward (WorldGuard; its "who"
 * also reads GriefPrevention and Towny), land_history (CoreProtect), disguise (LibsDisguises). They are
 * always registered, so the tool list stays the same everywhere; on a server without the plugin a power says
 * so in one short sentence and refuses.
 */
final class HookTools {
    private HookTools() {
    }

    static List<GodTool> all() {
        return List.of(
                GodTool.sync("ward", HookTools::wardHelp,
                        Schema.object()
                                .choice("action", "who (whose land a place is), raise, lift, or list your wards.", true,
                                        List.of("who", "raise", "lift", "list"))
                                .str("at", "who, raise: the place: " + Targets.TARGET_HELP + ".", false)
                                .str("name", "raise, lift: the ward's name.", false)
                                .integer("radius", "raise: how far it reaches from its middle, in blocks. Default 8.", false, 1, 64)
                                .strings("effects", "raise: what holds inside it: " + String.join(", ", Wards.EFFECTS) + ".", false, 8)
                                .str("greeting", "raise: words a mortal reads on stepping in.", false)
                                .integer("minutes", "raise: how long it stands. Default 30.", false, 1, 1440),
                        HookTools::ward),
                // Not a main-thread power: it reads CoreProtect's database.
                new GodTool("land_history", HookTools::historyHelp,
                        Schema.object()
                                .choice("action", "lookup (default) or rollback.", false, List.of("lookup", "rollback"))
                                .str("at", "The middle of the place: " + Targets.TARGET_HELP + ".", true)
                                .integer("radius", "How far around it, in blocks. Default 8.", false, 1, 64)
                                .integer("hours", "How far back. Default 6.", false, 1, 720)
                                .str("player", "lookup: only this mortal's acts. rollback: whose changes to take back (required).", false),
                        false, HookTools::history),
                GodTool.sync("disguise", HookTools::disguiseHelp,
                        Schema.object()
                                .str("player", "The mortal's name, or 'all'.", true)
                                .str("as", "The creature they appear as, e.g. pig, bat, chicken, zombie, villager. 'none' gives "
                                        + "their own shape back.", true)
                                .integer("seconds", "How long it lasts. Default 60.", false, 5, 3600),
                        HookTools::disguise));
    }

    // ---- ward ------------------------------------------------------------------------------------------

    static String wardHelp() {
        String who = "Whose land a place is, and wards of your own. action who tells you whether a place lies in a "
                + "protected region, a mortal's claim or a town";
        List<String> readers = Lands.names();
        if (!Wards.installed() || !Wards.enabled()) {
            return who + (readers.isEmpty() ? ". This server has no land protection plugin, so all land is free and you cannot raise wards."
                    : " (" + String.join(", ", readers) + "). This server has no WorldGuard, so you cannot raise wards.");
        }
        return who + ". raise sets land apart for a while: a cube around a place where your chosen effects hold ("
                + String.join(", ", Wards.EFFECTS) + ") and a greeting may be read; never over land that is someone's. "
                + "lift ends one early. At most " + Wards.maxWards() + " wards, radius " + Wards.maxRadius() + ", "
                + Wards.maxMinutes() + " minutes.";
    }

    private static ToolResult ward(Args a) throws ToolException {
        Realm realm = Realm.current();
        String action = a.str("action").toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        try {
            switch (action) {
                case "who" -> {
                    Location at = Targets.location(a.str("at"));
                    Lands.Claim claim = Lands.at(at);
                    List<String> mine = new ArrayList<>();
                    for (Wards.Ward w : Wards.of(realm.id)) {
                        if (w.world.equals(at.getWorld().getName()) && Math.abs(w.x - at.getBlockX()) <= w.radius
                                && Math.abs(w.y - at.getBlockY()) <= w.radius && Math.abs(w.z - at.getBlockZ()) <= w.radius) {
                            mine.add(w.name);
                        }
                    }
                    String wards = mine.isEmpty() ? "" : " Your ward " + String.join(", ", mine) + " covers it.";
                    if (claim != null) return ToolResult.ok("%s lies in %s.%s", Targets.describe(at), claim.describe(), wards);
                    return ToolResult.ok("%s is free land%s.%s", Targets.describe(at), Lands.names().isEmpty()
                            ? " (this server has no land protection plugin)" : " (asked: " + String.join(", ", Lands.names()) + ")", wards);
                }
                case "list" -> {
                    List<Wards.Ward> mine = Wards.of(realm.id);
                    if (mine.isEmpty()) return ToolResult.ok("You keep no wards.");
                    StringBuilder sb = new StringBuilder("Your wards:");
                    for (Wards.Ward w : mine) sb.append("\n- ").append(w.describe(now));
                    return ToolResult.ok(sb.toString());
                }
                case "raise" -> {
                    ScriptWorld.gate("conjure");
                    Location at = Targets.location(a.str("at"));
                    Wards.Ward w = Wards.raise(realm.id, a.str("name"), at, a.integer("radius", 8, 1, 64), a.strings("effects"),
                            a.str("greeting", null), a.integer("minutes", 30, 1, 1440));
                    at.getWorld().playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 2f, 0.7f);
                    at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 120, w.radius / 2.0, 2, w.radius / 2.0, 0.02);
                    return ToolResult.ok("The ward stands: %s.", w.describe(now));
                }
                case "lift" -> {
                    String name = a.str("name");
                    if (!Wards.lift(realm.id, name)) throw new ToolException("You keep no ward called '" + name + "'.");
                    return ToolResult.ok("The ward %s is lifted.", name);
                }
                default -> throw new ToolException("The action is who, raise, lift or list.");
            }
        } catch (Wards.Refusal e) {
            throw new ToolException(e.getMessage());
        }
    }

    // ---- land_history ----------------------------------------------------------------------------------

    static String historyHelp() {
        String what = "Read what was done to the land near a place: who broke, placed, used or killed what, and when";
        if (!LandHistory.installed() || !LandHistory.enabled()) {
            return what + ". This server keeps no such record (no CoreProtect plugin), so it is not yours to use here.";
        }
        return what + " (this server's CoreProtect record). rollback takes one mortal's block changes there back, as mercy "
                + "for the wronged or to undo a wrong" + (LandHistory.rollbackAllowed() ? "" : " (turned off by the keepers)")
                + ". Up to " + LandHistory.maxRadius() + " blocks around and " + LandHistory.maxHours() + " hours back.";
    }

    private static ToolResult history(Args a) throws ToolException {
        if (!LandHistory.enabled()) throw new ToolException("The keepers of this server have turned this off.");
        LandHistory.Source source = LandHistory.source();
        if (source == null) throw new ToolException("This server keeps no record of the land (no CoreProtect plugin).");
        if (Bukkit.isPrimaryThread()) throw new ToolException("The land's record cannot be read from here; use it as a power of your own.");
        String action = a.str("action", "lookup").toLowerCase(Locale.ROOT);
        String where = a.str("at");
        Location at = Sync.call(() -> Targets.location(where), 15_000);
        int radius = Math.min(LandHistory.maxRadius(), a.integer("radius", 8, 1, 64));
        int hours = Math.min(LandHistory.maxHours(), a.integer("hours", 6, 1, 720));
        String player = a.str("player", null);
        try {
            if (action.equals("rollback")) {
                if (!LandHistory.rollbackAllowed()) throw new ToolException("The keepers of this server do not let you take changes back.");
                if (player == null) throw new ToolException("Say whose changes to take back (player). Look first with action lookup.");
                if (player.startsWith("#")) throw new ToolException("Only a mortal's changes can be taken back.");
                Sync.call(() -> {
                    ScriptWorld.gate("unbuild");
                    return null;
                }, 15_000);
                int n = source.rollback(at, radius, hours * 3600, player);
                return ToolResult.ok("Took back %d change(s) %s made within %d blocks of %s in the last %d h.", n, player, radius,
                        Targets.describe(at), hours);
            }
            List<LandHistory.Entry> entries = source.lookup(at, radius, hours * 3600, player);
            return ToolResult.ok("Within %d blocks of %s, the last %d h%s:\n%s", radius, Targets.describe(at), hours,
                    player == null ? "" : ", " + player + " only", LandHistory.summarize(entries, System.currentTimeMillis(), 8, 6));
        } catch (RuntimeException | LinkageError e) {
            GPTGOD.LOGGER.warn("CoreProtect lookup failed", e);
            throw new ToolException("The land's record could not be read: " + e.getMessage());
        }
    }

    // ---- disguise --------------------------------------------------------------------------------------

    /** When each disguised mortal's own shape returns (so a newer disguise is not ended by an older timer). */
    private static final Map<UUID, Long> ENDS = new ConcurrentHashMap<>();

    static String disguiseHelp() {
        String what = "Change a mortal's shape for a while: everyone sees them as a creature (a pig for the greedy, a bat, "
                + "a chicken, a zombie), a jest or a humbling that harms nothing";
        if (!Disguises.installed() || !Disguises.enabled()) {
            return what + ". This server lacks the LibsDisguises plugin, so it is not yours to use here.";
        }
        return what + ". At most " + Disguises.maxSeconds() + " seconds; 'none' ends it.";
    }

    private static ToolResult disguise(Args a) throws ToolException {
        if (!Disguises.enabled()) throw new ToolException("The keepers of this server have turned this off.");
        if (!Disguises.usable()) throw new ToolException("This server lacks the LibsDisguises plugin, so you cannot change shapes here.");
        ScriptWorld.gate("apply_effect");
        List<Player> players = Targets.players(a.str("player"));
        String as = a.str("as").toLowerCase(Locale.ROOT).replace(' ', '_').replace("minecraft:", "");
        if (as.equals("none") || as.equals("self") || as.equals("off")) {
            List<String> back = new ArrayList<>();
            for (Player p : players) {
                ENDS.remove(p.getUniqueId());
                if (Disguises.undisguise(p)) back.add(p.getName());
            }
            return ToolResult.ok(back.isEmpty() ? "None of them wore another shape." : "Their own shape returns: " + String.join(", ", back) + ".");
        }
        EntityType type = WrathTools.entityType(as);
        if (!type.isAlive()) throw new ToolException("'" + as + "' is not a living creature. Try pig, bat, chicken, zombie, villager.");
        int seconds = Math.min(Disguises.maxSeconds(), a.integer("seconds", 60, 5, 3600));
        long until = System.currentTimeMillis() + seconds * 1000L;
        List<String> changed = new ArrayList<>();
        for (Player p : players) {
            if (!Disguises.disguise(p, type)) continue;
            changed.add(p.getName());
            ENDS.put(p.getUniqueId(), until);
            p.getWorld().spawnParticle(Particle.POOF, p.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.02);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
            UUID id = p.getUniqueId();
            Bukkit.getScheduler().runTaskLater(GPTGOD.get(), () -> {
                Long end = ENDS.get(id);
                if (end == null || end > System.currentTimeMillis() + 500) return; // ended already, or disguised anew
                ENDS.remove(id);
                Player still = Bukkit.getPlayer(id);
                if (still != null) Disguises.undisguise(still);
            }, seconds * 20L);
        }
        if (changed.isEmpty()) throw new ToolException("LibsDisguises cannot make the shape '" + as + "'.");
        return ToolResult.ok("%s now appear(s) to all as a %s for %d s.", String.join(", ", changed), as, seconds);
    }
}
