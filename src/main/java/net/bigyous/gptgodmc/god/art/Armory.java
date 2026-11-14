package net.bigyous.gptgodmc.god.art;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.art.Relics.Relic;
import net.bigyous.gptgodmc.god.art.SpritePainter.Art;
import net.bigyous.gptgodmc.god.vessel.VesselHeld;

/**
 * The relics each god keeps for itself, to draw into its vessel's hand when it wants them (a blade to strike
 * a mortal who has gone too far, a staff to lead a rite). armory.json beside the relics: god id to relic ids.
 * Also what a relic is in the hand ({@link #weapon}): where its grip and point are in its picture, how long it
 * is against the body, and the plain item stood in for it before eyes that cannot see relics.
 */
public final class Armory {
    /** Relics one god may keep at once. */
    public static final int MAX = 8;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, List<String>> KEPT = new LinkedHashMap<>();
    private static Path file;

    private Armory() {
    }

    public static synchronized void load(Path where) {
        file = where;
        KEPT.clear();
        if (where == null || !Files.exists(where)) return;
        try {
            Map<String, List<String>> read = GSON.fromJson(Files.readString(where, StandardCharsets.UTF_8),
                    new TypeToken<LinkedHashMap<String, List<String>>>() {
                    }.getType());
            if (read != null) read.forEach((god, ids) -> {
                if (god != null && ids != null) KEPT.put(god, new ArrayList<>(ids));
            });
        } catch (IOException | RuntimeException e) {
            if (GPTGOD.get() != null) GPTGOD.LOGGER.warn("Could not read the gods' armory: " + e.getMessage());
        }
    }

    private static void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(KEPT), StandardCharsets.UTF_8);
        } catch (IOException e) {
            if (GPTGOD.get() != null) GPTGOD.LOGGER.warn("Could not save the gods' armory: " + e.getMessage());
        }
    }

    /** The relics a god keeps, as they now are (any since unmade are left out). */
    public static synchronized List<Relic> of(String god) {
        List<Relic> out = new ArrayList<>();
        for (String id : KEPT.getOrDefault(god, List.of())) {
            Relic r = Relics.find(id);
            if (r != null && r.id().equals(id)) out.add(r);
        }
        return out;
    }

    public static synchronized boolean has(String god, String relicId) {
        return KEPT.getOrDefault(god, List.of()).contains(relicId);
    }

    /** Keeps a relic for a god. Why not (phrased for the god), or null when kept. */
    public static synchronized String keep(String god, Relic r) {
        if (!r.god().equals(god) && !r.god().equals("samples")) {
            return "\"" + r.name() + "\" was made by " + r.godName() + "; you may keep only what you made.";
        }
        List<String> mine = KEPT.computeIfAbsent(god, k -> new ArrayList<>());
        mine.removeIf(id -> Relics.find(id) == null);
        if (mine.contains(r.id())) return null;
        if (mine.size() >= MAX) {
            return "Your armory holds " + MAX + " relics already; let one go first.";
        }
        mine.add(r.id());
        save();
        return null;
    }

    public static synchronized boolean drop(String god, String relicId) {
        List<String> mine = KEPT.get(god);
        boolean had = mine != null && mine.remove(relicId);
        if (had) save();
        return had;
    }

    /** "Skysplinter (blade), Gravecall (staff)" or "nothing yet". */
    public static String describe(String god) {
        List<Relic> all = of(god);
        if (all.isEmpty()) return "nothing yet";
        List<String> names = new ArrayList<>();
        for (Relic r : all) names.add(r.name() + " (" + r.kind() + (r.twoFormed() ? ", two forms" : "") + ")");
        return String.join(", ", names);
    }

    // ---- In the hand --------------------------------------------------------------------------------

    /** How long a held relic of each kind is against the vessel's body (pixels; the body is 32 tall). */
    public static float length(String kind) {
        return switch (kind) {
            case "blade" -> 15f;
            case "staff" -> 24f;
            case "spirit" -> 8f;
            default -> 7f;
        };
    }

    /** What a relic is in the vessel's hand, in the form asked for (awake only if it has that form). */
    public static VesselHeld.Weapon weapon(Relic r, boolean awake) {
        boolean on = awake && r.twoFormed();
        Art art = (on ? Relics.artOn(r) : Relics.art(r)).squared();
        float[] ends = ends(art, "blade".equals(r.kind()) || "staff".equals(r.kind()));
        ItemStack shown = Relics.item(r);
        if (on) Relics.dress(shown, r, true);
        return new VesselHeld.Weapon(r.id() + (on ? Relics.ON : ""), r.name(), shown, new ItemStack(plain(r)),
                length(r.kind()), ends[0], ends[1], ends[2], ends[3], RelicImbue.glow(r));
    }

    /**
     * Where a thing is held and where it points, in its picture (u, v from the top left, 0..1): a held thing
     * drawn on the diagonal is gripped at its lowest-left texel and points to its highest-right; anything else
     * is held at the foot of its middle and points up. Pure.
     */
    public static float[] ends(Art art, boolean diagonal) {
        int w = art.w(), h = art.h();
        int gx = -1, gy = -1, tx = -1, ty = -1;
        double low = Double.MAX_VALUE, high = -Double.MAX_VALUE;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!art.drawn(x, y)) continue;
                double along = diagonal ? x + (h - 1 - y) : (h - 1 - y) * 4 - Math.abs(x - (w - 1) / 2.0);
                if (along < low) {
                    low = along;
                    gx = x;
                    gy = y;
                }
                if (along > high) {
                    high = along;
                    tx = x;
                    ty = y;
                }
            }
        }
        if (gx < 0) return new float[] { 0.1f, 0.9f, 0.9f, 0.1f };
        return new float[] { (gx + 0.5f) / w, (gy + 0.5f) / h, (tx + 0.5f) / w, (ty + 0.5f) / h };
    }

    /** The vanilla item that stands in for a relic before eyes that cannot see it: the nearest thing in kind. */
    static Material plain(Relic r) {
        String words = (r.name() + " " + r.look()).toLowerCase(Locale.ROOT);
        if (words.matches(".*\\b(scythe|sickle|reap\\w*)\\b.*")) return Material.NETHERITE_HOE;
        if (words.matches(".*\\b(axe|hatchet)\\b.*")) return Material.NETHERITE_AXE;
        if (words.matches(".*\\b(spear|trident|lance|pike)\\b.*")) return Material.TRIDENT;
        if (words.matches(".*\\b(hammer|mace|maul|club)\\b.*")) return Material.MACE;
        return switch (r.kind()) {
            case "blade" -> Material.NETHERITE_SWORD;
            case "staff" -> Material.BLAZE_ROD;
            case "tome" -> Material.ENCHANTED_BOOK;
            default -> Material.AMETHYST_SHARD;
        };
    }
}
