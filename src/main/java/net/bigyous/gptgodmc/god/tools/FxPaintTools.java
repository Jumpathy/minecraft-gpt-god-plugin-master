package net.bigyous.gptgodmc.god.tools;

import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.god.FxLibrary;
import net.bigyous.gptgodmc.god.FxPaint;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.art.Pigment;
import net.bigyous.gptgodmc.god.art.Sprite;
import net.bigyous.gptgodmc.god.art.SpritePainter.Art;
import net.bigyous.gptgodmc.god.art.SpriteSheet;

/** The god paints its own textures, to cover the solids and particles of its effects (see FxPaint). */
final class FxPaintTools {
    private FxPaintTools() {
    }

    static List<GodTool> all() {
        return List.of(GodTool.sync("fx_paint",
                () -> "Paint a texture of your own, then use it in fx_cast: a solid layer's \"texture\":\"name\" covers that "
                        + "solid with it (\"tile\": n or [across, up] repeats it), and a field layer's \"texture\":\"name\" "
                        + "makes each particle that picture. You do not choose pixels' colours: you give a legend from one "
                        + "letter to a material, and rows of those letters; the painting itself (shading, grain, glints, "
                        + "facets, flicker) is done for you, and what burns or shines moves by itself. sprite is JSON: "
                        + "{\"size\":16,\"legend\":{\"s\":\"stone\",\"v\":\"gem #c02040\",\"*\":\"glow #ff5a3a\"},\"rows\":[\"ssssvsss"
                        + "ssssssss\", ...16 strings of 16 letters]}. size is 8 to 32 (rows and letters must match it); '.' is an "
                        + "empty, see-through texel. Materials: " + String.join(", ", Pigment.names(false)) + "; with a colour "
                        + "of your choosing: " + String.join(", ", Pigment.names(true)) + "; a bare #RRGGBB is flat paint; add "
                        + "deep, dark, light or bright to fix a tone. Optional: \"accents\" (rows of + lighter, - darker, * "
                        + "sparkle), \"outline\": soft|dark (shapes' far edges darkened; default none), \"light\": top-left|top|"
                        + "top-right. You are shown the result. Mortals without the client mod see your solids in plain "
                        + "colour. You keep up to " + FxPaint.MAX + "; painting over a name replaces it. Your textures: "
                        + FxPaint.of(Realm.current()).index() + ".",
                Schema.object()
                        .str("name", "The texture's name, a few plain words, e.g. ember_scale.", true)
                        .str("sprite", "The sprite as JSON (see the description). Leave out only with forget.", false)
                        .str("note", "Optional: what this texture is for, in your words.", false)
                        .bool("forget", "Remove the texture of this name instead of painting.", false),
                FxPaintTools::paint));
    }

    private static ToolResult paint(Args a) throws ToolException {
        FxPaint paint = FxPaint.of(Realm.current());
        String name = a.str("name");
        if (a.bool("forget", false)) {
            if (!paint.forget(name)) throw new ToolException("You have no texture called '" + name + "'. Yours: " + paint.index() + ".");
            return ToolResult.ok("Forgotten: the texture '%s'.", FxLibrary.key(name));
        }
        if (!a.has("sprite")) throw new ToolException("Give the sprite to paint: {\"size\":16,\"legend\":{...},\"rows\":[...]}.");
        JsonObject sprite;
        try {
            JsonElement parsed = JsonParser.parseString(a.str("sprite"));
            if (!parsed.isJsonObject()) throw new JsonParseException("it is not an object");
            sprite = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new ToolException("The sprite is not valid JSON: " + e.getMessage());
        }
        FxPaint.Entry e;
        try {
            e = paint.keep(name, sprite, a.str("note", null), System.currentTimeMillis());
        } catch (Sprite.Flaw | IllegalArgumentException flaw) {
            throw new ToolException(flaw.getMessage());
        }
        Art art = paint.art(e);
        int scale = Math.max(4, 192 / Math.max(art.w(), art.h()));
        return ToolResult.image("Painted the texture '" + e.name + "' (" + art.w() + "x" + art.h() + ", shown enlarged). Use it "
                + "in fx_cast with \"texture\":\"" + e.name + "\" on a solid or a field layer.",
                SpriteSheet.png(SpriteSheet.enlarged(art, 0, scale, 0x202028)));
    }
}
