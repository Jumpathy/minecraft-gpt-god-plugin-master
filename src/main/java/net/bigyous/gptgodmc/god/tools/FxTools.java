package net.bigyous.gptgodmc.god.tools;

import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.google.gson.JsonArray;

import net.bigyous.gptgodmc.god.FxLibrary;
import net.bigyous.gptgodmc.god.FxScenes;
import net.bigyous.gptgodmc.god.FxShow;
import net.bigyous.gptgodmc.god.Realm;

/**
 * The god as its own effects artist: it writes scenes in the effect language, plays them where it likes
 * (for everyone near or as a private vision for one mortal), and keeps the ones worth keeping in its
 * library. Mortals with the client mod see the scene in full; the rest see a plain version.
 */
final class FxTools {
    private FxTools() {
    }

    static List<GodTool> all() {
        return List.of(
                GodTool.sync("fx_cast",
                        "Conjure a visual effect of your own design: light, storms, seals and glyphs, wings, omens, anything "
                                + "you can describe as a scene. It is pure spectacle (it harms and changes nothing), "
                                + "so use it freely and on your own initiative to make your presence, moods and "
                                + "judgements felt, and make each one fit the moment rather than repeating yourself. "
                                + "Mortals with the client mod see it in full; others see a simpler version. "
                                + FxScenes.HELP,
                        Schema.object()
                                .str("at", "Where: " + Targets.TARGET_HELP + ". A player name makes the scene follow "
                                        + "that mortal.", true)
                                .str("scene", "The layers, as a JSON array (see the description).", true)
                                .str("only", "Optional: a player name; only that mortal sees it (a private vision).", false)
                                .integer("scale", "Size in percent, 25-400. Default 100.", false, 25, 400)
                                .str("handle", "Optional short name for this running scene, so you can fx_stop it. "
                                        + "Required for loop.", false)
                                .bool("loop", "Repeat until you stop it (needs handle). Default false.", false)
                                .str("save_as", "Optional: keep this scene in your library under this name.", false)
                                .str("note", "Optional, with save_as: what this effect is for, in your words.", false),
                        FxTools::cast),
                GodTool.sync("fx_replay",
                        () -> "Play an effect from your library again (or 'forget' one). Your library: "
                                + FxLibrary.of(Realm.current()).index(24) + ".",
                        Schema.object()
                                .str("name", "The effect's name in your library.", true)
                                .str("at", "Where: " + Targets.TARGET_HELP + ". Default your vessel.", false)
                                .str("only", "Optional: only this mortal sees it.", false)
                                .integer("scale", "Size in percent, 25-400. Default 100.", false, 25, 400)
                                .str("handle", "Optional handle, to stop it later; required for loop.", false)
                                .bool("loop", "Repeat until stopped. Default false.", false)
                                .bool("forget", "Remove it from your library instead of playing it.", false),
                        FxTools::replay),
                GodTool.sync("fx_stop", "Stop a running effect of yours by its handle, or all of them.",
                        Schema.object().str("handle", "The handle you gave it, or 'all'.", true), FxTools::stop));
    }

    private static ToolResult cast(Args a) throws ToolException {
        JsonArray layers;
        try {
            layers = FxScenes.check(a.str("scene"));
        } catch (FxScenes.Invalid e) {
            throw new ToolException(e.getMessage());
        }
        Realm realm = Realm.current();
        String saved = "";
        if (a.has("save_as")) {
            try {
                FxLibrary.Entry e = FxLibrary.of(realm).keep(a.str("save_as"), a.str("note", null), layers.deepCopy(),
                        System.currentTimeMillis());
                saved = " Kept in your library as '" + e.name + "'.";
            } catch (IllegalArgumentException e) {
                throw new ToolException(e.getMessage());
            }
        }
        return show(a, realm, layers, saved);
    }

    private static ToolResult replay(Args a) throws ToolException {
        Realm realm = Realm.current();
        FxLibrary lib = FxLibrary.of(realm);
        String name = a.str("name");
        if (a.bool("forget", false)) {
            if (!lib.forget(name)) throw new ToolException("You have no effect called '" + name + "'.");
            return ToolResult.ok("Forgotten: '%s'.", FxLibrary.key(name));
        }
        FxLibrary.Entry e = lib.find(name);
        if (e == null) {
            throw new ToolException("You have no effect called '" + name + "'. Your library: " + lib.index(40)
                    + ". Create one with fx_cast and save_as.");
        }
        lib.used(e, System.currentTimeMillis());
        return show(a, realm, e.layers.deepCopy(), "");
    }

    private static ToolResult show(Args a, Realm realm, JsonArray layers, String extra) throws ToolException {
        // A private vision with no place given appears on the mortal it is for.
        String target = a.str("at", a.has("only") ? a.str("only") : "vessel");
        Location at = Targets.location(target);
        Entity follow = Bukkit.getPlayerExact(target.trim());
        Player only = a.has("only") ? Targets.player(a.str("only")) : null;
        boolean loop = a.bool("loop", false);
        String handle = a.has("handle") ? FxLibrary.key(a.str("handle")) : null;
        if (loop && (handle == null || handle.isEmpty())) {
            throw new ToolException("A looping effect needs a handle, so you can stop it with fx_stop.");
        }
        if (FxShow.running(realm) >= FxShow.MAX_RUNNING) {
            throw new ToolException("You already have " + FxShow.MAX_RUNNING + " effects running. Stop one with fx_stop first.");
        }
        double scale = a.integer("scale", 100, 25, 400) / 100.0;
        String unpainted = net.bigyous.gptgodmc.god.FxPaint.of(realm).missing(layers);
        if (unpainted != null) {
            throw new ToolException("You have painted no texture called '" + unpainted + "'. Paint it first with fx_paint, or "
                    + "leave \"texture\" out. Your textures: " + net.bigyous.gptgodmc.god.FxPaint.of(realm).index() + ".");
        }
        String crowded = net.bigyous.gptgodmc.god.FxSealShow.refusal(layers, scale);
        if (crowded != null) throw new ToolException(crowded);
        // Style tokens ($primary, $mood...) become this god's colours as they are now.
        FxShow.Seen seen = FxShow.play(realm, handle, at, follow, scale, loop, FxBrainTools.styled(realm, layers), only);
        int secs = Math.max(1, FxScenes.length(layers) / 20);
        String who = only != null && seen.full() + seen.plain() == 0
                ? only.getName() + " is too far from that place to see it (cast it at or near them)"
                : only != null ? "Only " + only.getName() + " sees it"
                : seen.full() + seen.plain() == 0 ? "No mortal is near enough to see it"
                        : String.format(Locale.ROOT, "%d mortal(s) see it in full, %d in a simpler form", seen.full(), seen.plain());
        return ToolResult.ok("The effect plays at %s (%d layer(s), %s%d s). %s.%s", Targets.describe(at), layers.size(),
                loop ? "looping every " : "", secs, who, extra);
    }

    private static ToolResult stop(Args a) throws ToolException {
        String handle = a.str("handle");
        int n = FxShow.stop(Realm.current(), handle.equalsIgnoreCase("all") ? "all" : FxLibrary.key(handle), true);
        if (n == 0) throw new ToolException("No running effect has the handle '" + handle + "'.");
        return ToolResult.ok("%d effect(s) stopped.", n);
    }
}
