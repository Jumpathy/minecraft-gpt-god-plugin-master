package net.bigyous.gptgodmc.god;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;

import org.bukkit.entity.Player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.client.ModLink;
import net.bigyous.gptgodmc.client.ModMessages;
import net.bigyous.gptgodmc.client.ModSessions;
import net.bigyous.gptgodmc.god.art.Sprite;
import net.bigyous.gptgodmc.god.art.SpritePainter;
import net.bigyous.gptgodmc.god.art.SpritePainter.Art;
import net.bigyous.gptgodmc.god.art.SpriteSheet;

/**
 * A god's own painted textures (fx_paint): textures.json in its folder. The god writes each as a sprite (a
 * legend of materials and rows of letters, {@link Sprite}); {@link SpritePainter} paints it, with no model
 * called. A scene's solid and field layers name one with "texture"; players with the client mod are sent the
 * picture the first time a scene they see uses it ({"t":"texture","id","rev","w","h","frames","ticks","png"}:
 * png is a base64 PNG of the frames stacked top to bottom) and the mod draws it at once, with no resource pack.
 * Everything but {@link #of} and {@link #bind} is pure.
 */
public final class FxPaint {
    public static final int MAX = 24;
    public static final String NAME = "[a-z0-9_]{1,32}";

    public static final class Entry {
        public String name;
        public String note;
        public int rev;
        public long made;
        public JsonObject sprite;
    }

    private static final Map<String, FxPaint> BY_REALM = new HashMap<>();
    /** Per player (as long as they stay online): the revision of each texture their mod has been sent. */
    private static final Map<Player, Map<String, Integer>> SENT = new WeakHashMap<>();

    private final Path file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Art> painted = new HashMap<>();

    public FxPaint(Path file) {
        this.file = file;
        try {
            if (file != null && Files.exists(file)) {
                JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                for (JsonElement e : root.getAsJsonArray("textures")) {
                    JsonObject o = e.getAsJsonObject();
                    Entry t = new Entry();
                    t.name = o.get("name").getAsString();
                    t.note = o.has("note") ? o.get("note").getAsString() : null;
                    t.rev = o.get("rev").getAsInt();
                    t.made = o.has("made") ? o.get("made").getAsLong() : 0;
                    t.sprite = o.getAsJsonObject("sprite");
                    Sprite.parse(t.sprite); // one that can no longer be read is left out
                    entries.put(t.name, t);
                }
            }
        } catch (IOException | RuntimeException | Sprite.Flaw e) {
            if (GPTGOD.LOGGER != null) GPTGOD.LOGGER.warn("Could not read " + file + ": " + e);
        }
    }

    public static synchronized FxPaint of(Realm r) {
        return BY_REALM.computeIfAbsent(r.id, k -> new FxPaint(r.file("textures.json")));
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized Entry find(String name) {
        return name == null ? null : entries.get(FxLibrary.key(name));
    }

    /** "veins (the altar's stone), ember_scale", or "none yet". */
    public synchronized String index() {
        if (entries.isEmpty()) return "none yet";
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.name);
            if (e.note != null && !e.note.isBlank()) sb.append(" (").append(e.note).append(')');
        }
        return sb.toString();
    }

    /**
     * Keeps a texture under a name (painting over one of that name). A texture is drawn edge to edge, so
     * unless the sprite says otherwise no shape's far edge is darkened.
     *
     * @throws Sprite.Flaw              what is wrong with the sprite, said to its artist
     * @throws IllegalArgumentException a bad name, or no room left
     */
    public synchronized Entry keep(String name, JsonObject sprite, String note, long now) throws Sprite.Flaw {
        String key = FxLibrary.key(name);
        if (!key.matches(NAME)) throw new IllegalArgumentException("A texture's name is a few plain words, e.g. 'ember_scale'.");
        JsonObject s = sprite.deepCopy();
        if (!s.has("outline")) s.addProperty("outline", "none");
        Sprite parsed = Sprite.parse(s);
        Entry e = entries.get(key);
        if (e == null) {
            if (entries.size() >= MAX) {
                throw new IllegalArgumentException("You keep " + MAX + " textures already (" + index() + "); paint over one of "
                        + "them or forget one first.");
            }
            e = new Entry();
            e.name = key;
            entries.put(key, e);
        }
        e.sprite = s;
        e.rev++;
        e.made = now;
        if (note != null && !note.isBlank()) e.note = note.trim().length() > 80 ? note.trim().substring(0, 80) : note.trim();
        painted.put(key, SpritePainter.paint(parsed, false));
        save();
        return e;
    }

    public synchronized boolean forget(String name) {
        String key = FxLibrary.key(name);
        painted.remove(key);
        if (entries.remove(key) == null) return false;
        save();
        return true;
    }

    /** The texture painted (once; the painter always paints the same picture from the same sprite). */
    public synchronized Art art(Entry e) {
        return painted.computeIfAbsent(e.name, k -> {
            try {
                return SpritePainter.paint(Sprite.parse(e.sprite), false);
            } catch (Sprite.Flaw flaw) {
                throw new IllegalStateException(flaw.getMessage());
            }
        });
    }

    private void save() {
        if (file == null) return;
        JsonArray list = new JsonArray();
        for (Entry e : entries.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", e.name);
            if (e.note != null) o.addProperty("note", e.note);
            o.addProperty("rev", e.rev);
            o.addProperty("made", e.made);
            o.add("sprite", e.sprite);
            list.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("textures", list);
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            if (GPTGOD.LOGGER != null) GPTGOD.LOGGER.warn("Could not save " + file + ": " + ex);
        }
    }

    // ---- Scenes and messages ---------------------------------------------------------------------------

    /** The texture names a scene's layers use. */
    public static Set<String> names(JsonArray layers) {
        Set<String> out = new TreeSet<>();
        for (JsonElement e : layers) {
            JsonElement t = e.isJsonObject() ? e.getAsJsonObject().get("texture") : null;
            if (t != null && t.isJsonPrimitive() && !t.getAsString().isBlank()) out.add(FxLibrary.key(t.getAsString()));
        }
        return out;
    }

    /** The first texture a scene names that this god has not painted, or null. */
    public String missing(JsonArray layers) {
        for (String name : names(layers)) {
            if (find(name) == null) return name;
        }
        return null;
    }

    /** A copy of the layers with each texture name made this god's own on the wire: "main.veins". */
    public static JsonArray wired(JsonArray layers, String realm) {
        JsonArray out = layers.deepCopy();
        for (JsonElement e : out) {
            if (!e.isJsonObject() || !e.getAsJsonObject().has("texture")) continue;
            JsonElement t = e.getAsJsonObject().get("texture");
            if (t.isJsonPrimitive()) e.getAsJsonObject().addProperty("texture", id(realm, t.getAsString()));
        }
        return out;
    }

    public static String id(String realm, String name) {
        return FxLibrary.key(realm) + "." + FxLibrary.key(name);
    }

    /** One texture as a message for the mod: every frame, or only the first when that would be too large. */
    public static JsonObject message(String id, int rev, Art art) {
        for (int frames : new int[] { art.frames().length, 1 }) {
            JsonObject o = new JsonObject();
            o.addProperty("t", "texture");
            o.addProperty("id", id);
            o.addProperty("rev", rev);
            o.addProperty("w", art.w());
            o.addProperty("h", art.h());
            o.addProperty("frames", frames);
            o.addProperty("ticks", SpritePainter.FRAME_TICKS);
            o.addProperty("png", Base64.getEncoder().encodeToString(SpriteSheet.png(SpriteSheet.strip(art.w(), art.h(),
                    java.util.Arrays.copyOf(art.frames(), frames)))));
            if (ModMessages.bytes(o).length <= ModSessions.MAX_BYTES) return o;
        }
        return null;
    }

    /**
     * Readies a scene for the mod: sends each viewer the textures it uses that their mod does not have yet, and
     * returns the layers with those textures' names as the mod knows them.
     */
    public static JsonArray bind(Realm realm, JsonArray layers, Collection<Player> viewers) {
        Set<String> names = names(layers);
        if (names.isEmpty()) return layers;
        FxPaint paint = of(realm);
        for (String name : names) {
            Entry e = paint.find(name);
            if (e == null) continue;
            String id = id(realm.id, name);
            JsonObject message = null;
            for (Player p : viewers) {
                Map<String, Integer> sent = SENT.computeIfAbsent(p, k -> new HashMap<>());
                Integer have = sent.get(id);
                if (have != null && have == e.rev) continue;
                try {
                    if (message == null) message = message(id, e.rev, paint.art(e));
                } catch (RuntimeException ex) {
                    break; // unpaintable: the layer is drawn plain
                }
                if (message == null || ModLink.send(p, ModSessions.FX, message)) sent.put(id, e.rev);
            }
        }
        return wired(layers, realm.id);
    }
}
