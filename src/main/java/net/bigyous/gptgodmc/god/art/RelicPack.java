package net.bigyous.gptgodmc.god.art;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import net.bigyous.gptgodmc.god.art.Relics.Relic;
import net.bigyous.gptgodmc.god.art.SpritePainter.Art;
import net.bigyous.gptgodmc.god.vessel.VesselPack;

/**
 * The relics' part of the god's resource pack: for every relic (and its awakened form) its animated
 * sprite and an item (gptgodmc:relic/&lt;id&gt;) built from it as a solid ({@link RelicForm}: boxes with
 * painted walls, lit like any item; held things with the sword's grip), shown flat as an icon. {@link VesselPack} adds these files to the pack it
 * serves; the pack is rebuilt when a relic is made. Players with the client mod do not need these: the
 * mod is sent each sprite and draws it itself.
 */
public final class RelicPack {
    private RelicPack() {
    }

    public static String modelId(String relicId) {
        return Relics.MODEL_PREFIX + relicId;
    }

    /** Every file the relics add to the pack, by path. */
    public static Map<String, byte[]> files() {
        Map<String, byte[]> out = new LinkedHashMap<>();
        String ns = VesselPack.NAMESPACE;
        for (Relic r : Relics.all()) {
            try {
                put(out, ns, r.kind(), r.id(), Relics.art(r).squared());
                Art on = Relics.artOn(r);
                if (on != null) put(out, ns, r.kind(), r.id() + Relics.ON, on.squared());
            } catch (RuntimeException e) {
                // a relic that cannot be painted is left out of the pack rather than breaking it
            }
        }
        return out;
    }

    /**
     * One form of one relic: its animated sprite and its painted walls (each an animated texture), its
     * solid model, a flat model for its icon, and the item that shows the icon in inventories and the solid
     * form everywhere else. Lit like any item: nothing in it is forced bright.
     */
    private static void put(Map<String, byte[]> out, String ns, String kind, String id, Art art) {
        String path = "relic/" + id, textures = "assets/" + ns + "/textures/item/", models = "assets/" + ns
                + "/models/item/";
        texture(out, textures + path + ".png", SpriteSheet.strip(art.w(), art.h(), art.frames()));
        texture(out, textures + path + WALLS + ".png", SpriteSheet.strip(2 * art.w(), 2 * art.h(),
                RelicForm.wallFrames(art)));
        String front = ns + ":item/" + path;
        String solid = RelicForm.model(art, new RelicForm.Look(front, front + WALLS), handheld(kind));
        out.put(models + path + ".json", utf8(solid != null ? solid : model(kind, front)));
        out.put(models + path + ICON + ".json", utf8(model(kind, front)));
        out.put("assets/" + ns + "/items/" + path + ".json", utf8(solid == null ? item(front) : item(front, front + ICON)));
    }

    /** What a relic's walls texture and its icon model add to its path ("__" never occurs in a relic's id). */
    static final String WALLS = "__walls", ICON = "__icon";

    private static void texture(Map<String, byte[]> out, String path, java.awt.image.BufferedImage strip) {
        out.put(path, SpriteSheet.png(strip));
        out.put(path + ".mcmeta", utf8(animation()));
    }

    /** True for things held like a sword (the tall, turned pose in the hand) rather than like a gem. */
    public static boolean handheld(String kind) {
        return "blade".equals(kind) || "staff".equals(kind);
    }

    /** The plain model (the sprite given Minecraft's usual thickness), for forms too intricate to build from boxes. */
    static String model(String kind, String texture) {
        return """
                {
                  "parent": "minecraft:item/%s",
                  "textures": { "layer0": "%s" }
                }
                """.formatted(handheld(kind) ? "handheld" : "generated", texture);
    }

    /** The item: the flat icon in inventories (gui), the solid model everywhere else. */
    static String item(String model, String icon) {
        return """
                {
                  "model": {
                    "type": "minecraft:select",
                    "property": "minecraft:display_context",
                    "cases": [
                      { "when": "gui", "model": { "type": "minecraft:model", "model": "%s" } }
                    ],
                    "fallback": { "type": "minecraft:model", "model": "%s" }
                  }
                }
                """.formatted(icon, model);
    }

    static String item(String model) {
        return """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "%s"
                  }
                }
                """.formatted(model);
    }

    /**
     * Frames stacked in one image; they are square, so the game finds their size by itself. Stepped, not
     * blended: the painter's frames are pixel art (a glint, a flicker) and the client mod steps them too.
     */
    static String animation() {
        return """
                {
                  "animation": {
                    "frametime": %d,
                    "interpolate": false
                  }
                }
                """.formatted(SpritePainter.FRAME_TICKS);
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
