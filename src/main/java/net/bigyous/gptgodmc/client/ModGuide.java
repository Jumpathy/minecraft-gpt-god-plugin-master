package net.bigyous.gptgodmc.client;

import org.bukkit.entity.Player;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.guide.Guide;

/**
 * The guide for players with the client mod (feature "guide"): the whole book in one message, which the
 * mod shows in its own screen. {@link #message} is pure and checked against the golden files
 * client-mod/src/test/resources/protocol/guide-*.json.
 *
 * <pre>
 * {"t":"guide","open":true,"title":"...","operator":false,
 *  "gods":[{"name":"Vaelor","epithet":"the Ember Judge","color":"#FF5A30"}],
 *  "chapters":[{"id":"gods","title":"...","op":true?,"blocks":[{"p":"text"},{"h":"heading"},{"cmd":"/pray","text":"..."}]}]}
 * </pre>
 */
public final class ModGuide {
    private ModGuide() {
    }

    /** The guide as one message. open = show the screen now (else the mod only keeps it). */
    public static JsonObject message(Guide.Book book, boolean open) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "guide");
        o.addProperty("open", open);
        o.addProperty("title", book.title());
        o.addProperty("operator", book.operator());
        JsonArray gods = new JsonArray();
        for (Guide.GodLine g : book.gods()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", g.name());
            if (g.epithet() != null && !g.epithet().isBlank()) j.addProperty("epithet", g.epithet());
            j.addProperty("color", ModMessages.hex(g.color()));
            if (g.slain()) j.addProperty("slain", true);
            gods.add(j);
        }
        o.add("gods", gods);
        JsonArray chapters = new JsonArray();
        for (Guide.Chapter c : book.chapters()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", c.id());
            j.addProperty("title", c.title());
            if (c.op()) j.addProperty("op", true);
            JsonArray blocks = new JsonArray();
            for (Guide.Block b : c.blocks()) {
                JsonObject k = new JsonObject();
                switch (b) {
                    case Guide.Para p -> k.addProperty("p", p.text());
                    case Guide.Heading h -> k.addProperty("h", h.text());
                    case Guide.Cmd cmd -> {
                        k.addProperty("cmd", cmd.cmd());
                        k.addProperty("text", cmd.text());
                    }
                }
                blocks.add(k);
            }
            j.add("blocks", blocks);
            chapters.add(j);
        }
        o.add("chapters", chapters);
        return o;
    }

    /** Whether this player's guide item opens the mod's screen. */
    public static boolean has(Player p) {
        return ModLink.has(p, ModSessions.GUIDE);
    }

    /** Opens the screen on this book. False when the player has no mod (or it refused): give the book. */
    public static boolean open(Player p, Guide.Book book) {
        return ModLink.send(p, ModSessions.GUIDE, message(book, true));
    }
}
