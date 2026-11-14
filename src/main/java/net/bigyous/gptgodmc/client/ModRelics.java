package net.bigyous.gptgodmc.client;

import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.god.art.Relics;
import net.bigyous.gptgodmc.god.art.Relics.Relic;
import net.bigyous.gptgodmc.god.art.SpritePainter;
import net.bigyous.gptgodmc.god.art.SpritePainter.Art;
import net.bigyous.gptgodmc.god.art.SpriteSheet;

/**
 * Sends the relics' art to players with the client mod (feature "relics"), so the mod can draw each
 * relic itself the moment it exists, solid and lit by the world like any item, with no resource pack
 * reload. Each
 * player is sent every relic once (and again when it is repainted), a few at a time.
 *
 * <p>The message: {"t":"relic","id","rev","name","kind","w","h","frames","ticks","png","lit","depth"}. png
 * is a base64 PNG of the frames stacked top to bottom (w wide, h*frames tall, always square frames); lit,
 * when present, is the same strip holding only the texels that shine by themselves; depth is base64 of w*h
 * bytes, row by row: how thick each texel is front to back, in texels, centred on the sprite's plane (0 on a
 * drawn texel = a film with no thickness); walls, when present, is a base64 PNG strip of the colours of
 * each texel's walls when it is given thickness: per frame a square of 2w x 2h whose quarters are the walls
 * seen from above, below, the west and the east (top-left, top-right, bottom-left, bottom-right; its frame
 * count may be 1 when the message would be too large); shape, when present, is that form's true shape: a list
 * of still solids in the language of scenes (FxSolid), checked and normalized by RelicShape, in sixteenths of
 * the picture's square (x, y 0..16 from its bottom left, z -8..8): draw it at tick RelicShape.TICK, size 1/16,
 * shifted by -0.5 in x and y, in place of the picture given thickness everywhere but inventories, lit by the
 * world; each part names its material's tile as "texture", and textures holds those tiles (name -> base64 PNG,
 * 16 x 16, repeating, nearest texel), laid on by u and v as RelicShape.texel does (texels the size of the picture's, w across the item): draw a part's faces as
 * texture x vertex colour x the world's light. {"t":"relic_gone","id"} forgets one. The item a relic is carried
 * as has item_model gptgodmc:relic/&lt;id&gt;; a relic with a second, awakened form sends that form's art
 * as a second message under the id &lt;id&gt;__on, and the item's model changes to it while it is awake.
 */
public final class ModRelics {
    public static final String FEATURE = ModSessions.RELICS;
    /** Relics sent to one player in one tick, so a newcomer is not flooded. */
    private static final int PER_TICK = 3;
    /** Per player: the revision of each relic it has been sent. */
    private static final Map<UUID, Map<String, Integer>> SENT = new HashMap<>();

    private ModRelics() {
    }

    /** One relic's art as a message, with all its frames or, if that is too large, only the first; null if even that is. */
    public static JsonObject message(Relic r, Art painted) {
        return message(r.id(), r, painted);
    }

    /** The same for one form of a relic: `id` is the relic's own, or that plus "__on" for its awakened form. */
    public static JsonObject message(String id, Relic r, Art painted) {
        Art art = painted.squared();
        int all = art.frames().length;
        // Smaller and smaller until it fits: every frame; walls in one frame; one frame of each; no walls.
        for (int[] tryout : new int[][] { { all, all }, { all, 1 }, { 1, 1 }, { 1, 0 } }) {
            int frames = tryout[0], wallFrames = tryout[1];
            JsonObject o = new JsonObject();
            o.addProperty("t", "relic");
            o.addProperty("id", id);
            o.addProperty("rev", r.rev());
            o.addProperty("name", r.name());
            o.addProperty("kind", r.kind());
            o.addProperty("w", art.w());
            o.addProperty("h", art.h());
            o.addProperty("frames", frames);
            o.addProperty("ticks", SpritePainter.FRAME_TICKS);
            o.addProperty("png", strip(art, art.frames(), frames));
            if (art.lit() != null) o.addProperty("lit", strip(art, art.lit(), frames));
            o.addProperty("depth", Base64.getEncoder().encodeToString(art.depth()));
            com.google.gson.JsonArray shape = shape(id, r);
            if (shape != null) {
                o.add("shape", shape);
                JsonObject tiles = new JsonObject();
                for (var t : net.bigyous.gptgodmc.god.art.RelicShape.tiles(shape).entrySet()) {
                    tiles.addProperty(t.getKey(), Base64.getEncoder().encodeToString(SpriteSheet.png(SpriteSheet.strip(
                            net.bigyous.gptgodmc.god.art.RelicTiles.SIZE, net.bigyous.gptgodmc.god.art.RelicTiles.SIZE,
                            new int[][] { t.getValue() }))));
                }
                o.add("textures", tiles);
            }
            if (wallFrames > 0 && art.walls() != null) {
                int[][] walls = net.bigyous.gptgodmc.god.art.RelicForm.wallFrames(art);
                o.addProperty("walls", Base64.getEncoder().encodeToString(SpriteSheet.png(SpriteSheet.strip(2 * art.w(),
                        2 * art.h(), java.util.Arrays.copyOf(walls, wallFrames)))));
            }
            if (ModMessages.bytes(o).length <= ModSessions.MAX_BYTES) return o;
        }
        return null;
    }

    /** The true shape of one form of a relic, checked, or null when it has none (or it no longer builds). */
    static com.google.gson.JsonArray shape(String id, Relic r) {
        try {
            net.bigyous.gptgodmc.god.art.Sprite s = Relics.sprite(r);
            com.google.gson.JsonArray raw = id.endsWith(Relics.ON) ? (s.alt() == null ? null : s.alt().shape()) : s.shape();
            return raw == null ? null : net.bigyous.gptgodmc.god.art.RelicShape.normalized(raw);
        } catch (RuntimeException | net.bigyous.gptgodmc.god.art.RelicShape.Flaw e) {
            return null;
        }
    }

    private static String strip(Art art, int[][] frames, int count) {
        int[][] some = count == frames.length ? frames : java.util.Arrays.copyOf(frames, count);
        return Base64.getEncoder().encodeToString(SpriteSheet.png(SpriteSheet.strip(art.w(), art.h(), some)));
    }

    public static JsonObject gone(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "relic_gone");
        o.addProperty("id", id);
        return o;
    }

    /** Twice a second: sends each mod player what it has not been sent yet, and what has been unmade. */
    public static void tick() {
        Set<UUID> online = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!ModLink.has(p, FEATURE)) continue;
            online.add(p.getUniqueId());
            Map<String, Integer> sent = SENT.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
            Set<String> alive = new HashSet<>();
            int budget = PER_TICK;
            for (Relic r : Relics.all()) {
                alive.add(r.id());
                Integer have = sent.get(r.id());
                if (budget == 0 || (have != null && have == r.rev())) continue;
                JsonObject message, awake = null;
                try {
                    message = message(r, Relics.art(r));
                    Art on = Relics.artOn(r);
                    if (on != null) awake = message(r.id() + Relics.ON, r, on);
                } catch (RuntimeException e) {
                    sent.put(r.id(), r.rev()); // unpaintable: do not try again every tick
                    continue;
                }
                if (message == null || ModLink.send(p, FEATURE, message)) {
                    if (awake != null) ModLink.send(p, FEATURE, awake);
                    sent.put(r.id(), r.rev());
                    budget--;
                }
            }
            sent.keySet().removeIf(id -> {
                if (alive.contains(id)) return false;
                ModLink.send(p, FEATURE, gone(id));
                ModLink.send(p, FEATURE, gone(id + Relics.ON));
                return true;
            });
        }
        // A player who left, or whose mod said hello again, is sent everything afresh next time.
        SENT.keySet().retainAll(online);
    }

    /** True when this player's mod draws relics itself (it needs no resource pack for them). */
    public static boolean draws(Player p) {
        return ModLink.has(p, FEATURE);
    }

    public static void clear() {
        SENT.clear();
    }
}
