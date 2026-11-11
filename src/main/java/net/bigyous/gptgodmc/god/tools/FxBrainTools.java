package net.bigyous.gptgodmc.god.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.client.ModLink;
import net.bigyous.gptgodmc.client.ModMessages;
import net.bigyous.gptgodmc.client.ModSessions;
import net.bigyous.gptgodmc.god.FxLibrary;
import net.bigyous.gptgodmc.god.FxRoutines;
import net.bigyous.gptgodmc.god.FxScenes;
import net.bigyous.gptgodmc.god.FxShow;
import net.bigyous.gptgodmc.god.FxStyle;
import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.Realm;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * How the god authors its own presentation beyond single scenes: its visual style (fx_style), its library
 * of effects and routines (library_save / library_run / library_read / library_forget), effects bound to
 * its powers (fx_bind, played by {@link #afterPower} from {@link ToolRegistry#execute}), and omens on one
 * mortal's HUD (fx_omen; the older omen power is a sign in the world for all). None of it is required by any other power.
 */
final class FxBrainTools {
    private FxBrainTools() {
    }

    private static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().create();
    /** Gods with a routine in flight (one at a time each). */
    private static final Set<String> RUNNING = ConcurrentHashMap.newKeySet();

    static List<GodTool> all() {
        return List.of(
                GodTool.sync("fx_style",
                        "Your own visual style, which you may read (no arguments) or rewrite whenever you change: "
                                + "the palette, sprites, shapes and motion you favour, the colour of your sky in each "
                                + "mood, the accent of your mark on mortals' screens, and your sigil. Mortals with the "
                                + "client mod see their sky and HUD follow it. " + FxStyle.TOKEN_HELP + " So an effect "
                                + "written with tokens changes when you restyle yourself or your mood turns. Give only "
                                + "the fields you want to change.",
                        Schema.object()
                                .str("primary", "Your main colour, \"#RRGGBB\".", false)
                                .str("secondary", "Your second colour, \"#RRGGBB\".", false)
                                .str("accent", "A highlight colour, \"#RRGGBB\".", false)
                                .str("shadow", "Your dark colour, \"#RRGGBB\".", false)
                                .str("hud", "The accent of your panel on mortals' screens, \"#RRGGBB\".", false)
                                .strings("sprites", "One to four favourite sprites: " + String.join(", ", FxScenes.SPRITES) + ".",
                                        false, FxStyle.MAX_FAVOURITES)
                                .strings("shapes", "One to four favourite shapes: " + String.join(", ", FxScenes.SHAPES) + ".",
                                        false, FxStyle.MAX_FAVOURITES)
                                .str("motion", "How your effects move, in a few words (e.g. slow and heavy).", false)
                                .str("sigil", "Your mark: a symbol or very short word, at most " + FxStyle.MAX_SIGIL
                                        + " characters.", false)
                                .str("sky", "Sky colours per mood as a JSON object, e.g. {\"wrathful\":\"#B01010\","
                                        + "\"calm\":\"#CFE4FF\"}. Moods: " + String.join(", ", FxStyle.MOODS) + ".", false),
                        FxBrainTools::style),
                GodTool.sync("library_save",
                        "Keep something in your library under a name: a routine (a sequence of your own powers with "
                                + "waits between them, replayed later as one act with library_run), or an effect "
                                + "without playing it (give scene instead of steps; play it with fx_replay). Saving "
                                + "under an existing name replaces it, which is how you refine one. "
                                + FxRoutines.HELP,
                        Schema.object()
                                .str("name", "The name to keep it under (letters, numbers, underscores).", true)
                                .str("steps", "A routine's steps, as a JSON array (see the description).", false)
                                .str("scene", "Instead of steps: an effect's layers, as for fx_cast.", false)
                                .str("note", "What it is for, in your words.", false),
                        FxBrainTools::save),
                // Not a main-thread power: it waits between steps, and each step goes to the server thread itself.
                new GodTool("library_run",
                        () -> "Perform one of your saved routines as a single act: each step runs exactly as if you "
                                + "had used that power yourself, in order, with its waits, and you are told how each "
                                + "went. Your routines: " + FxLibrary.of(Realm.current()).routines(24) + ".",
                        Schema.object().str("name", "The routine's name in your library.", true), false, FxBrainTools::run),
                GodTool.sync("library_read",
                        "Read one entry of your library in full (an effect's layers or a routine's steps, your note, "
                                + "how often you used it, what it is bound to), so you can refine it and save it again.",
                        Schema.object().str("name", "The entry's name.", true), FxBrainTools::read),
                GodTool.sync("library_forget", "Remove an effect or routine from your library (and any binding it had).",
                        Schema.object().str("name", "The entry's name.", true), FxBrainTools::forget),
                GodTool.sync("fx_bind",
                        () -> "Attach one of your saved effects to one of your powers, so it plays at that power's "
                                + "target every time the power succeeds (\"when I smite, play ashen_verdict\"). Leave "
                                + "effect out to unbind. Your bindings: " + FxLibrary.of(Realm.current()).bindingsText() + ".",
                        Schema.object()
                                .str("power", "The power's name, e.g. smite, heal, announce.", true)
                                .str("effect", "The effect in your library to play with it. Omit to unbind.", false),
                        FxBrainTools::bind),
                GodTool.sync("fx_omen",
                        "Put a short line, your sigil, or both on one mortal's screen for a few seconds: a sign meant "
                                + "for them alone (a warning before wrath, a nod to a prayer you liked, a riddle). No "
                                + "one else sees it. Mortals with the client mod see it on their panel of you; others "
                                + "see it above their hotbar.",
                        Schema.object()
                                .str("player", "The mortal who sees it.", true)
                                .str("text", "The line, at most 80 characters. Omit to show only your sigil.", false)
                                .bool("sigil", "Show your sigil with it. Default true.", false)
                                .str("color", "\"#RRGGBB\" or a style token such as $accent or $mood. Default $hud.", false)
                                .integer("seconds", "How long it stays, 2-15. Default 6.", false, 2, 15),
                        FxBrainTools::omen));
    }

    /** The scene as it should be shown now: style tokens replaced by this god's colours and current mood. */
    static JsonArray styled(Realm realm, JsonArray layers) {
        return FxStyle.of(realm).resolve(layers, FxStyle.currentMood(realm));
    }

    // ---- fx_style --------------------------------------------------------------------------------------

    private static ToolResult style(Args a) throws ToolException {
        Realm realm = Realm.current();
        FxStyle s = FxStyle.of(realm);
        List<String> changed;
        try {
            changed = s.apply(a.raw(), true);
        } catch (FxScenes.Invalid e) {
            throw new ToolException(e.getMessage());
        }
        if (changed.isEmpty()) return ToolResult.ok("Your style: %s.", s.describe());
        s.by = "self";
        s.updated = System.currentTimeMillis();
        FxStyle.save(realm, s);
        return ToolResult.ok("Your style is changed (%s). It is now: %s.", String.join(", ", changed), s.describe());
    }

    // ---- The library -----------------------------------------------------------------------------------

    private static ToolResult save(Args a) throws ToolException {
        Realm realm = Realm.current();
        FxLibrary lib = FxLibrary.of(realm);
        String name = a.str("name");
        boolean steps = a.raw().has("steps") && !a.raw().get("steps").isJsonNull();
        boolean scene = a.has("scene");
        if (steps == scene) throw new ToolException("Give either steps (a routine) or scene (an effect), one of them.");
        long now = System.currentTimeMillis();
        try {
            if (scene) {
                FxLibrary.Entry e = lib.keep(name, a.str("note", null), FxScenes.check(a.str("scene")), now);
                return ToolResult.ok("Kept the effect '%s' (%d layer(s)). Play it with fx_replay.", e.name, e.layers.size());
            }
            List<FxRoutines.Step> list = FxRoutines.check(a.raw().get("steps"), n -> ToolRegistry.get(n) != null);
            FxLibrary.Entry e = lib.keepRoutine(name, a.str("note", null), list, now);
            return ToolResult.ok("Kept the routine '%s': %s. Perform it with library_run.", e.name, FxRoutines.outline(list));
        } catch (FxScenes.Invalid | IllegalArgumentException e) {
            throw new ToolException(e.getMessage());
        }
    }

    private static ToolResult read(Args a) throws ToolException {
        FxLibrary lib = FxLibrary.of(Realm.current());
        FxLibrary.Entry e = lib.any(a.str("name"));
        if (e == null) throw new ToolException(missing(lib, a.str("name")));
        StringBuilder sb = new StringBuilder();
        sb.append(e.isRoutine() ? "Routine '" : "Effect '").append(e.name).append("'");
        if (e.note != null) sb.append(" (").append(e.note).append(')');
        sb.append(", used ").append(e.uses).append(" time(s)");
        if (e.bound != null && !e.bound.isEmpty()) sb.append(", bound to ").append(String.join(", ", e.bound));
        sb.append(e.isRoutine() ? ". Steps: " : ". Scene: ").append(PRETTY.toJson(e.isRoutine() ? PRETTY.toJsonTree(e.steps) : e.layers));
        return ToolResult.ok("%s", sb);
    }

    private static ToolResult forget(Args a) throws ToolException {
        FxLibrary lib = FxLibrary.of(Realm.current());
        String name = a.str("name");
        if (!lib.forget(name)) throw new ToolException(missing(lib, name));
        return ToolResult.ok("Forgotten: '%s'.", FxLibrary.key(name));
    }

    private static String missing(FxLibrary lib, String name) {
        return "Your library has nothing called '" + name + "'. Effects: " + lib.index(40) + ". Routines: " + lib.routines(40) + ".";
    }

    private static ToolResult run(Args a) throws ToolException {
        Realm realm = Realm.current();
        FxLibrary lib = FxLibrary.of(realm);
        String name = a.str("name");
        FxLibrary.Entry e = lib.any(name);
        if (e == null) throw new ToolException(missing(lib, name));
        if (!e.isRoutine()) throw new ToolException("'" + e.name + "' is an effect, not a routine: play it with fx_replay.");
        List<FxRoutines.Step> steps = List.copyOf(e.steps);
        if (!RUNNING.add(realm.id)) throw new ToolException("A routine of yours is still being performed; wait for it to end.");
        lib.used(e, System.currentTimeMillis());
        if (Bukkit.isPrimaryThread()) {
            // An operator ran it from the server thread, which must not wait: the steps are scheduled.
            int at = 0;
            for (int i = 0; i < steps.size(); i++) {
                FxRoutines.Step s = steps.get(i);
                at += Math.max(0, s.delay);
                boolean last = i == steps.size() - 1;
                Realm.later(() -> {
                    try {
                        step(s);
                    } finally {
                        if (last) RUNNING.remove(realm.id);
                    }
                }, at);
            }
            return ToolResult.ok("The routine '%s' begins: %s.", e.name, FxRoutines.outline(steps));
        }
        List<String> lines = new ArrayList<>();
        int failed = 0;
        try {
            for (int i = 0; i < steps.size(); i++) {
                FxRoutines.Step s = steps.get(i);
                if (s.delay > 0) Thread.sleep(Math.min(FxRoutines.MAX_DELAY, s.delay) * 50L);
                ToolResult r = step(s);
                if (r.isError()) failed++;
                String text = r.text() == null ? "" : r.text().replace('\n', ' ');
                if (text.length() > 140) text = text.substring(0, 139) + "…";
                lines.add((i + 1) + ". " + s.tool + (r.isError() ? " ✗ " : " → ") + text);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            lines.add("(interrupted before the end)");
        } finally {
            RUNNING.remove(realm.id);
        }
        return ToolResult.ok("You performed '%s' (%d step(s)%s).\n%s", e.name, steps.size(),
                failed == 0 ? "" : ", " + failed + " failed", String.join("\n", lines));
    }

    /** One step, exactly as if the god had used the power (limits, reactions, journal and bound effects included). */
    private static ToolResult step(FxRoutines.Step s) {
        if (FxRoutines.NOT_STEPS.contains(s.tool)) return ToolResult.error("'" + s.tool + "' cannot be part of a routine.");
        return ToolRegistry.execute(s.tool, s.args == null ? new JsonObject() : s.args.deepCopy(), "brain").result();
    }

    // ---- Bound effects ---------------------------------------------------------------------------------

    private static ToolResult bind(Args a) throws ToolException {
        FxLibrary lib = FxLibrary.of(Realm.current());
        String power = a.str("power").toLowerCase(Locale.ROOT);
        if (ToolRegistry.get(power) == null) throw new ToolException("You have no power called '" + power + "'.");
        if (!a.has("effect")) {
            String had = lib.unbind(power);
            if (had == null) throw new ToolException("Nothing is bound to " + power + ". Your bindings: " + lib.bindingsText() + ".");
            return ToolResult.ok("%s no longer plays '%s'.", power, had);
        }
        if (!FxRoutines.bindable(power)) throw new ToolException("An effect cannot be bound to " + power + ".");
        String effect = a.str("effect");
        FxLibrary.Entry e;
        try {
            e = lib.bind(power, effect);
        } catch (IllegalStateException ex) {
            throw new ToolException(ex.getMessage());
        }
        if (e == null) {
            throw new ToolException("You have no effect called '" + effect + "'. Your effects: " + lib.index(40)
                    + ". Create one with fx_cast and save_as, or library_save.");
        }
        return ToolResult.ok("Bound: whenever you use %s, '%s' plays at its target.", power, e.name);
    }

    /** Called by {@link ToolRegistry#execute} after any power succeeds, from any thread. */
    static void afterPower(String tool, JsonObject input) {
        Realm realm = Realm.current();
        FxLibrary lib = FxLibrary.of(realm);
        FxLibrary.Entry e = lib.boundTo(tool);
        if (e == null || e.layers == null || !FxRoutines.bindable(tool) || !GPTGOD.get().isEnabled()) return;
        String who = FxRoutines.targetOf(input);
        Runnable play = () -> {
            Location at = null;
            if (who != null && !who.equalsIgnoreCase("all")) {
                try {
                    at = Targets.location(who);
                } catch (ToolException | RuntimeException ignored) {
                    // not a place this power names: fall back to the god's body
                }
            }
            try {
                if (at == null) at = Targets.location("vessel");
            } catch (ToolException | RuntimeException ignored) {
                return; // formless and aimed nowhere: nothing to show
            }
            if (FxShow.running(realm) >= FxShow.MAX_RUNNING) return;
            Entity follow = who == null ? null : Bukkit.getPlayerExact(who);
            FxShow.play(realm, null, at, follow, 1, false, styled(realm, e.layers), null);
            lib.used(e, System.currentTimeMillis());
        };
        if (Bukkit.isPrimaryThread()) {
            play.run();
        } else {
            Realm.soon(play);
        }
    }

    // ---- Omens -----------------------------------------------------------------------------------------

    private static ToolResult omen(Args a) throws ToolException {
        Realm realm = Realm.current();
        Player p = Targets.player(a.str("player"));
        FxStyle s = FxStyle.of(realm);
        String text = a.str("text", "").replaceAll("[\\u00a7\\p{Cntrl}]+", " ").trim();
        if (text.length() > 80) throw new ToolException("Keep an omen to 80 characters; it is a sign, not a speech.");
        boolean sigil = a.bool("sigil", true);
        if (text.isEmpty() && !sigil) throw new ToolException("An omen needs text, your sigil, or both.");
        String color = a.str("color", "$hud");
        String hex = FxStyle.isColorToken(color) ? s.colorOf(color, FxStyle.currentMood(realm))
                : color.matches("#[0-9a-fA-F]{6}") ? color : null;
        if (hex == null) throw new ToolException("The colour is \"#RRGGBB\" or one of " + FxStyle.COLOR_TOKENS + ".");
        int rgb = FxStyle.rgb(hex);
        int ticks = a.integer("seconds", 6, 2, 15) * 20;
        Persona persona = realm.persona();
        String god = persona == null ? null : persona.shortName();
        if (ModLink.send(p, ModSessions.OMEN, ModMessages.omen(realm.id, god, text.isEmpty() ? null : text,
                sigil ? s.sigil : null, rgb, ticks))) {
            return ToolResult.ok("%s alone sees your omen on their screen for %d s.", p.getName(), ticks / 20);
        }
        String line = (sigil ? s.sigil + (text.isEmpty() ? "" : "  ") : "") + text;
        Component shown = Component.text(line, TextColor.color(rgb));
        new BukkitRunnable() {
            int left = ticks;

            @Override
            public void run() {
                if (left <= 0 || !p.isOnline()) {
                    cancel();
                    return;
                }
                p.sendActionBar(shown);
                left -= 20;
            }
        }.runTaskTimer(GPTGOD.get(), 0L, 20L);
        return ToolResult.ok("%s alone sees your omen above their hotbar for %d s.", p.getName(), ticks / 20);
    }
}
