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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.Speech;
import net.bigyous.gptgodmc.god.art.SpritePainter.Art;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The relics the gods have made: each a name, a kind, the words the god asked for it in, and its sprite
 * as the artist wrote it. One file each in plugins/gptgodmc/god/relics/. The art is painted again from the
 * sprite whenever it is needed (the painter always gives the same pixels), so nothing but the sprite is
 * kept. Also the items made from them, and the limits on making more.
 */
public final class Relics {
    /** A relic's item_model is this plus its id; the resource pack and the client mod both go by it. */
    public static final String MODEL_PREFIX = "gptgodmc:relic/";
    /**
     * What a relic's second (awakened) form adds to its id, for its art and model: "starbrand__on". No
     * relic's own id can hold two underscores in a row, so the two never collide.
     */
    public static final String ON = "__on";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * @param god  the id of the god's realm ("samples" for the built-in examples)
     * @param made when, epoch ms
     * @param rev    goes up when the same relic is painted again or imbued anew
     * @param powers what it is imbued with, as written (see {@link RelicPowers}); never null
     */
    public record Relic(String id, String name, String kind, String god, String godName, String look, long made, int rev,
            JsonObject sprite, List<String> powers) {
        public Relic {
            powers = powers == null ? List.of() : List.copyOf(powers);
        }

        /** True when it has a second, awakened form. */
        public boolean twoFormed() {
            return sprite.has("alt_rows") && sprite.get("alt_rows").isJsonArray();
        }
    }

    private static final Map<String, Relic> ALL = new LinkedHashMap<>();
    private static final Map<String, Art> ART = new ConcurrentHashMap<>();
    private static Path folder;

    private Relics() {
    }

    // ---- Keeping them ---------------------------------------------------------------------------------

    public static synchronized void load(Path dir) {
        folder = dir;
        ALL.clear();
        ART.clear();
        if (dir == null || !Files.isDirectory(dir)) return;
        List<Relic> found = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.getFileName().toString().endsWith(".json"))::iterator) {
                if (file.getFileName().toString().equals("shrines.json")) continue;
                try {
                    Relic r = read(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
                    if (r == null) continue;
                    Sprite.parse(r.sprite()); // a relic whose sprite no longer parses is left out
                    found.add(r);
                } catch (Exception e) {
                    warn("Could not read relic " + file.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            warn("Could not list the relics: " + e.getMessage());
        }
        found.sort(java.util.Comparator.comparingLong(Relic::made));
        for (Relic r : found) ALL.put(r.id(), r);
    }

    /** A relic from its file's JSON, or null when it is not one. */
    static Relic read(JsonObject o) {
        String id = text(o, "id", "");
        if (!id.matches("[a-z0-9_]{1,40}") || !o.has("sprite") || !o.get("sprite").isJsonObject()) return null;
        List<String> powers = new ArrayList<>();
        if (o.has("powers") && o.get("powers").isJsonArray()) {
            o.getAsJsonArray("powers").forEach(e -> powers.add(e.getAsString()));
        }
        return new Relic(id, text(o, "name", id), text(o, "kind", "trinket"), text(o, "god", ""), text(o, "godName", ""),
                text(o, "look", ""), o.has("made") ? o.get("made").getAsLong() : 0, o.has("rev") ? o.get("rev").getAsInt() : 1,
                o.getAsJsonObject("sprite"), powers);
    }

    static JsonObject write(Relic r) {
        JsonObject o = new JsonObject();
        o.addProperty("id", r.id());
        o.addProperty("name", r.name());
        o.addProperty("kind", r.kind());
        o.addProperty("god", r.god());
        o.addProperty("godName", r.godName());
        o.addProperty("look", r.look());
        o.addProperty("made", r.made());
        o.addProperty("rev", r.rev());
        o.add("sprite", r.sprite());
        com.google.gson.JsonArray powers = new com.google.gson.JsonArray();
        r.powers().forEach(powers::add);
        o.add("powers", powers);
        return o;
    }

    private static String text(JsonObject o, String key, String fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
    }

    private static void warn(String message) {
        if (GPTGOD.get() != null) GPTGOD.LOGGER.warn(message);
    }

    public static synchronized List<Relic> all() {
        return List.copyOf(ALL.values());
    }

    /** A relic by its id or its name (any case), or the one whose name starts with the words given. */
    public static synchronized Relic find(String idOrName) {
        if (idOrName == null) return null;
        String q = idOrName.trim();
        Relic byId = ALL.get(slug(q));
        if (byId != null) return byId;
        for (Relic r : ALL.values()) {
            if (r.name().equalsIgnoreCase(q)) return r;
        }
        for (Relic r : ALL.values()) {
            if (r.name().toLowerCase(Locale.ROOT).startsWith(q.toLowerCase(Locale.ROOT))) return r;
        }
        return null;
    }

    public static Sprite sprite(Relic r) {
        try {
            return Sprite.parse(r.sprite());
        } catch (Sprite.Flaw e) {
            throw new IllegalStateException("Relic " + r.id() + " has a broken sprite: " + e.getMessage(), e);
        }
    }

    /** Its painted frames (painted once, then remembered). */
    public static Art art(Relic r) {
        return ART.computeIfAbsent(r.id() + "#" + r.rev(), k -> SpritePainter.paint(sprite(r), Atelier.halo(r.kind())));
    }

    /** Its second form painted, or null when it has only one. */
    public static Art artOn(Relic r) {
        if (!r.twoFormed()) return null;
        return ART.computeIfAbsent(r.id() + ON + "#" + r.rev(),
                k -> SpritePainter.paint(sprite(r).alt(), Atelier.halo(r.kind())));
    }

    /** A name as an id: lower case letters, digits and underscores. */
    public static String slug(String name) {
        String s = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return s.length() > 32 ? s.substring(0, 32).replaceAll("_+$", "") : s;
    }

    /** Keeps a new relic with no powers. */
    public static Relic add(String god, String godName, String name, String kind, String look, Sprite sprite, long now) {
        return add(god, godName, name, kind, look, sprite, List.of(), now);
    }

    /** Replaces what a relic is imbued with; every copy of it follows. Returns the relic as it now is, or null. */
    public static synchronized Relic imbue(String id, List<String> powers) {
        Relic old = ALL.get(id);
        if (old == null) return null;
        Relic r = new Relic(old.id(), old.name(), old.kind(), old.god(), old.godName(), old.look(), old.made(),
                old.rev() + 1, old.sprite(), powers);
        ALL.put(id, r);
        write(folder, r);
        return r;
    }

    private static void write(Path dir, Relic r) {
        if (dir == null) return;
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(r.id() + ".json"), GSON.toJson(write(r)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            warn("Could not save relic " + r.id() + ": " + e.getMessage());
        }
    }

    /** Keeps a new relic (or a new painting of one with the same name by the same god) and writes its file. */
    public static synchronized Relic add(String god, String godName, String name, String kind, String look, Sprite sprite,
            List<String> powers, long now) {
        String base = slug(name).isEmpty() ? "relic" : slug(name), id = base;
        Relic old = ALL.get(id);
        int rev = 1;
        if (old != null && old.god().equals(god)) {
            rev = old.rev() + 1;
        } else {
            for (int n = 2; ALL.containsKey(id); n++) id = base + "_" + n;
        }
        Relic r = new Relic(id, name, kind, god, godName, look, now, rev, sprite.toJson(), powers);
        ALL.put(id, r);
        write(folder, r);
        return r;
    }

    public static synchronized boolean remove(String id) {
        Relic r = ALL.remove(id);
        if (r == null) return false;
        ART.keySet().removeIf(k -> k.startsWith(id + "#") || k.startsWith(id + ON + "#"));
        if (folder != null) {
            try {
                Files.deleteIfExists(folder.resolve(id + ".json"));
            } catch (IOException e) {
                warn("Could not delete relic " + id + ": " + e.getMessage());
            }
        }
        return true;
    }

    // ---- Limits ---------------------------------------------------------------------------------------

    /** Why this god may not make another relic now (phrased for the god), or null. */
    public static synchronized String refusal(String god, long now) {
        return refusal(all(), god, now, ArtSettings.enabled(), ArtSettings.perGodPerDay(), ArtSettings.cooldownMinutes(),
                ArtSettings.maxTotal());
    }

    static String refusal(List<Relic> all, String god, long now, boolean enabled, int perDay, int cooldownMinutes,
            int maxTotal) {
        if (!enabled) {
            return "The keeper of this world has not lit your forge (creations.enabled is off). Give ordinary gifts "
                    + "with give_item instead.";
        }
        int today = 0, kept = 0;
        long last = 0;
        for (Relic r : all) {
            if (r.god().equals("samples")) continue;
            kept++;
            if (!r.god().equals(god)) continue;
            last = Math.max(last, r.made());
            if (now - r.made() < 24 * 3_600_000L) today++;
        }
        if (kept >= maxTotal) {
            return "The world holds as many relics as it can bear (" + maxTotal + "). Give or show one that exists, or "
                    + "ask the keeper to unmake one.";
        }
        if (today >= perDay) {
            return "Your forge is spent for today (" + perDay + " relic" + (perDay == 1 ? "" : "s") + " in a day). Give "
                    + "one you have already made.";
        }
        long wait = last + cooldownMinutes * 60_000L - now;
        if (wait > 0) {
            return "Your forge is still cooling; it can be lit again in " + Math.max(1, wait / 60_000) + " minute"
                    + (wait / 60_000 <= 1 ? "" : "s") + ".";
        }
        return null;
    }

    // ---- Items ----------------------------------------------------------------------------------------

    private static NamespacedKey key() {
        return new NamespacedKey(GPTGOD.get(), "relic");
    }

    private static NamespacedKey key(String name) {
        return new NamespacedKey(GPTGOD.get(), name);
    }

    static Material base(String kind) {
        return switch (kind == null ? "" : kind) {
            case "blade" -> Material.IRON_SWORD;
            case "staff" -> Material.STICK;
            case "tome" -> Material.BOOK;
            case "spirit" -> Material.PAPER;
            default -> Material.AMETHYST_SHARD;
        };
    }

    /**
     * The relic as an item: an ordinary item of its kind that carries the relic's model, name and maker.
     * A blade is a real sword that never breaks; the rest are keepsakes.
     */
    public static ItemStack item(Relic r) {
        ItemStack stack = new ItemStack(base(r.kind()));
        dress(stack, r, false);
        return stack;
    }

    /** True when a relic item is in its second, awakened form. */
    public static boolean awake(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || GPTGOD.get() == null) return false;
        Byte on = stack.getItemMeta().getPersistentDataContainer().get(key("relic_on"), PersistentDataType.BYTE);
        return on != null && on == 1;
    }

    /** The revision of the relic an item was last dressed as (0 when it is not a relic). */
    public static int revOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || GPTGOD.get() == null) return 0;
        Integer rev = stack.getItemMeta().getPersistentDataContainer().get(key("relic_rev"), PersistentDataType.INTEGER);
        return rev == null ? 0 : rev;
    }

    /**
     * Makes an item stack this relic as it now is: its name, lore, model (the awakened one when on and it
     * has two forms) and what it is imbued with. Used when it is made, when it wakes or sleeps, and when
     * a copy made before the relic was changed is next held.
     */
    public static void dress(ItemStack stack, Relic r, boolean on) {
        boolean awake = on && r.twoFormed();
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(r.name(), Speech.VIOLET).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : wrap(r.look(), 38, 3)) {
            lore.add(Component.text(line, Speech.DEEP_VIOLET).decoration(TextDecoration.ITALIC, true));
        }
        if (r.godName() != null && !r.godName().isBlank()) {
            lore.add(Component.text("Made by " + r.godName(), Speech.VIOLET).decoration(TextDecoration.ITALIC, false));
        }
        for (String line : RelicPowers.lore(r.powers())) {
            lore.add(Component.text(line, net.kyori.adventure.text.format.TextColor.color(0x9FE8FF))
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.setItemModel(NamespacedKey.fromString(MODEL_PREFIX + r.id() + (awake ? ON : "")));
        meta.getPersistentDataContainer().set(key(), PersistentDataType.STRING, r.id());
        meta.getPersistentDataContainer().set(key("relic_rev"), PersistentDataType.INTEGER, r.rev());
        meta.getPersistentDataContainer().set(key("relic_on"), PersistentDataType.BYTE, (byte) (awake ? 1 : 0));
        meta.setMaxStackSize(1);
        if ("blade".equals(r.kind())) meta.setUnbreakable(true);
        RelicImbue.dress(meta, r, awake);
        stack.setItemMeta(meta);
    }

    /** The relic an item is, or null. */
    public static Relic of(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || GPTGOD.get() == null) return null;
        String id = stack.getItemMeta().getPersistentDataContainer().get(key(), PersistentDataType.STRING);
        return id == null ? null : find(id);
    }

    /** Breaks text into at most `lines` lines of about `width` characters, ending with an ellipsis if cut. */
    static List<String> wrap(String text, int width, int lines) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            line.append(line.length() > 0 ? " " : "").append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        if (out.size() <= lines) return out;
        List<String> cut = new ArrayList<>(out.subList(0, lines));
        cut.set(lines - 1, cut.get(lines - 1) + "…");
        return cut;
    }

    /** For reading a relic's sprite out of JSON text (the operator command and tests). */
    public static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
