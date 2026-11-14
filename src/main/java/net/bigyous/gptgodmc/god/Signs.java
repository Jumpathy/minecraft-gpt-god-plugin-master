package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.tools.Targets;
import net.kyori.adventure.text.Component;

/**
 * Signs: when a god's power touches a mortal or the land near them, the mortals it touches read who did
 * it, in one line above the hotbar ("✦ Thyvoru healed you", "✦ MusicMaker raised a ward here"). Without
 * it a heal, an effect, a ward or a change of weather looks like the server acting on its own, most of
 * all with several gods about ({@code god.signs}, on by default). Only acts a mortal would notice have a
 * sign; the god's thoughts, looks, notes and words have none. A judgment shows the judged mortal only which way
 * their favor moved, never the reason. The wording is pure and unit tested.
 */
public final class Signs {
    private Signs() {
    }

    /** What the mortal it was done to reads after the god's name. */
    static final Map<String, String> TO_YOU = Map.ofEntries(
            Map.entry("heal", "healed you"), Map.entry("apply_effect", "laid an effect on you"),
            Map.entry("give_item", "gave you a gift"), Map.entry("grant_xp", "granted you experience"),
            Map.entry("revive", "raised you from death"), Map.entry("teleport", "moved you"),
            Map.entry("take_item", "took something from you"), Map.entry("smite", "struck at you"),
            Map.entry("fling", "flung you"), Map.entry("mark", "set a mark on you"),
            Map.entry("bestow_title", "gave you a title"), Map.entry("empower", "empowered you"),
            Map.entry("disguise", "changed how you look"), Map.entry("labyrinth", "passed a sentence on you"),
            Map.entry("drop_block", "dropped something on you"), Map.entry("apotheosis", "raised you up"),
            Map.entry("give_relic", "gave you a relic"));

    /** What mortals near the place read after the god's name. */
    static final Map<String, String> NEAR_YOU = Map.ofEntries(
            Map.entry("explode", "set off an explosion"), Map.entry("meteor", "called down a meteor"),
            Map.entry("earthquake", "shook the earth"), Map.entry("spawn_entity", "summoned creatures"),
            Map.entry("ward", "raised a ward here"), Map.entry("tend", "touched the living land"),
            Map.entry("worldedit", "reshaped the land"), Map.entry("build", "is building here"),
            Map.entry("unbuild", "took a work away"), Map.entry("conjure", "conjured something"),
            Map.entry("transform_structure", "transformed a structure"),
            Map.entry("supply_chest", "left a chest of supplies"), Map.entry("summon_servant", "sent a servant"),
            Map.entry("sabotage", "spoiled a rival's work"), Map.entry("drop_block", "dropped something from the sky"),
            Map.entry("smite", "called down lightning"));

    /** What every mortal of the world reads. */
    static final Map<String, String> EVERYWHERE = Map.of("set_weather", "changed the weather", "set_time",
            "turned the hours");

    static final double NEAR = 40;
    private static final Map<String, Long> SHOWN = new ConcurrentHashMap<>();

    /** The line a mortal reads, or null when this power has no sign for them. Pure. */
    public static String line(String god, String tool, boolean toThem, boolean nearThem) {
        String did = toThem ? TO_YOU.get(tool) : null;
        if (did == null && (nearThem || toThem)) did = NEAR_YOU.get(tool);
        if (did == null) did = EVERYWHERE.get(tool);
        if (did == null) return null;
        return "✦ " + (god == null || god.isBlank() ? "A god" : god) + " " + did + " ✦";
    }

    public static boolean has(String tool) {
        return TO_YOU.get(tool) != null || NEAR_YOU.containsKey(tool) || EVERYWHERE.containsKey(tool)
                || JUDGE.equals(tool);
    }

    /** The power that moves a mortal's favor: without a sign the god's regard changed where no mortal could see. */
    static final String JUDGE = "judge";

    /** What a judged mortal reads, by which way their favor moved; null when it did not move. Pure. */
    public static String favorLine(String god, int change) {
        if (change == 0) return null;
        String who = god == null || god.isBlank() ? "A god" : god;
        return "✦ " + who + (change > 0 ? " looks on you with favor ✦" : " is displeased with you ✦");
    }

    /** After a power succeeded (any thread): shows its sign to the mortals it touched. */
    public static void after(String tool, JsonObject input) {
        GPTGOD plugin = GPTGOD.get();
        if (plugin == null || !plugin.isEnabled() || !has(tool)) return;
        if (!plugin.getConfig().getBoolean("god.signs", true)) return;
        Realm realm = Realm.current();
        Runnable show = () -> realm.run(() -> show(realm, tool, input));
        if (Bukkit.isPrimaryThread()) show.run();
        else Bukkit.getScheduler().runTask(plugin, show);
    }

    private static void show(Realm realm, String tool, JsonObject input) {
        String god = realm.displayName();
        World world = realm.world();
        List<Player> them = new ArrayList<>();
        String who = str(input, "player");
        if (who != null) {
            if (who.equalsIgnoreCase("all")) them.addAll(world.getPlayers());
            else {
                Player p = Bukkit.getPlayerExact(who);
                if (p != null) them.add(p);
            }
        }
        if (JUDGE.equals(tool)) {
            int change = input != null && input.has("change") && input.get("change").isJsonPrimitive()
                    ? (int) Math.signum(input.get("change").getAsDouble()) : 0;
            // "+" and "-" are told apart, so praise right after blame is not swallowed as a repeat.
            for (Player p : them) tell(p, god, tool + (change > 0 ? "+" : "-"), favorLine(god, change));
            return;
        }
        for (Player p : them) tell(p, god, tool, line(god, tool, true, false));
        if (EVERYWHERE.containsKey(tool)) {
            for (Player p : world.getPlayers()) tell(p, god, tool, line(god, tool, false, false));
            return;
        }
        if (!NEAR_YOU.containsKey(tool)) return;
        Location at = them.isEmpty() ? place(input) : them.get(0).getLocation();
        if (at == null || at.getWorld() == null) return;
        for (Player p : at.getWorld().getPlayers()) {
            if (them.contains(p) && TO_YOU.get(tool) != null) continue;
            if (p.getLocation().distanceSquared(at) <= NEAR * NEAR) tell(p, god, tool, line(god, tool, false, true));
        }
    }

    private static void tell(Player p, String god, String tool, String line) {
        if (line == null) return;
        // The same act repeated (a script's forty explosions) is said once.
        String key = p.getUniqueId() + "|" + god + "|" + tool;
        long now = System.currentTimeMillis();
        Long last = SHOWN.get(key);
        if (last != null && now - last < 4000) return;
        SHOWN.put(key, now);
        if (SHOWN.size() > 500) SHOWN.values().removeIf(t -> now - t > 60_000);
        p.sendActionBar(Component.text(line, Speech.VIOLET));
    }

    /** Where a power was aimed, when its arguments say so. */
    private static Location place(JsonObject input) {
        for (String k : List.of("target", "at", "near", "where", "location", "from", "to")) {
            String s = str(input, k);
            if (s == null) continue;
            try {
                Location l = Targets.location(s);
                if (l != null) return l;
            } catch (Exception e) {
                // not a place
            }
        }
        try {
            return God.vessel().location();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String str(JsonObject o, String key) {
        if (o == null) return null;
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return null;
        String s = e.getAsString().trim();
        return s.isEmpty() ? null : s.toLowerCase(Locale.ROOT).equals("null") ? null : s;
    }
}
