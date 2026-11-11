package net.bigyous.gptgodmc.god.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.FxLibrary;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Guard;
import net.bigyous.gptgodmc.god.Initiative;
import net.bigyous.gptgodmc.god.Journal;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.ScriptLibrary;
import net.bigyous.gptgodmc.god.script.ScriptApi;
import net.bigyous.gptgodmc.god.script.ScriptLimits;
import net.bigyous.gptgodmc.god.script.ScriptRun;
import net.bigyous.gptgodmc.loggables.GenericEventLoggable;

/**
 * The script power: the god writes a short program and the server runs it in a sandbox
 * ({@link ScriptRun}) against {@link ScriptWorld}, so it can compose acts nobody wrote a power for. The
 * program never runs on the server thread: it gets a thread of its own, which afterwards serves its
 * handlers (on, every) until their time is up. Limits come from config.yml `scripts:` ({@link #limits}).
 */
final class ScriptTools {
    private ScriptTools() {
    }

    private static final Gson GSON = new Gson();
    /** Gods whose script is in its main part (one at a time each; also stops a script reaching itself). */
    private static final Set<String> RUNNING = ConcurrentHashMap.newKeySet();

    static List<GodTool> all() {
        // Not a main-thread power: the program runs on its own thread and visits the server thread per batch.
        return List.of(new GodTool("script", ScriptTools::description,
                Schema.object()
                        .str("code", "The program (JavaScript). Give this, or name to run a saved one.", false)
                        .str("name", "A saved script to run, read or forget.", false)
                        .str("args", "Arguments as a JSON object, e.g. {\"height\": 12, \"block\": \"gold_block\"}; the "
                                + "program reads them as args.height. For a saved script they replace its saved ones.", false)
                        .str("save_as", "Keep the program under this name if it runs without a mistake; the args you "
                                + "gave become its parameters.", false)
                        .str("note", "With save_as: what it is for, in your words.", false)
                        .choice("action", "run (default); read or forget a saved script by name; stop ends your "
                                + "scripts' living handlers.", false, List.of("run", "read", "forget", "stop")),
                false, ScriptTools::script));
    }

    // ---- Settings --------------------------------------------------------------------------------------

    private static FileConfiguration cfg() {
        return GPTGOD.get() == null ? null : GPTGOD.get().getConfig();
    }

    static boolean enabled() {
        FileConfiguration c = cfg();
        return c == null || c.getBoolean("scripts.enabled", true);
    }

    /** config.yml `scripts:`. Blocks are also held to powers.max-block-changes, whichever is smaller. */
    static ScriptLimits limits() {
        FileConfiguration c = cfg();
        ScriptLimits d = ScriptLimits.DEFAULT;
        if (c == null) return d;
        return new ScriptLimits(
                c.getInt("scripts.max-instructions", d.instructions()),
                c.getInt("scripts.max-seconds", d.seconds()),
                c.getInt("scripts.max-wait-ticks", d.waitTicks()),
                c.getInt("scripts.max-host-calls", d.hostCalls()),
                c.getInt("scripts.max-power-calls", d.powerCalls()),
                c.getInt("scripts.max-commands", d.commands()),
                Math.min(c.getInt("scripts.max-blocks", d.blocks()), GodSettings.maxBlockChanges()),
                c.getInt("scripts.max-entities", d.entities()),
                c.getInt("scripts.max-listeners", d.listeners()),
                c.getInt("scripts.listener-seconds", d.listenerSeconds()),
                c.getInt("scripts.max-events", d.events()),
                c.getInt("scripts.max-displays", d.displays()));
    }

    /** scripts.max-display-seconds: the longest a shape made by world.display may hang in the air. */
    static int maxDisplaySeconds() {
        FileConfiguration c = cfg();
        return Math.max(1, Math.min(3600, c == null ? 600 : c.getInt("scripts.max-display-seconds", 600)));
    }

    static String description() {
        String what = "Write a short program and the server runs it for you, safely walled in: for acts no power of yours "
                + "was made for, with loops, maths, timing, and handlers that answer what mortals do.";
        if (!enabled()) return what + " The keepers of this server have turned it off.";
        ScriptLimits l = limits();
        return what + " " + ScriptApi.HELP + "\nOne script may spend: " + l.describe() + ". A script that worked can be "
                + "kept (save_as) and run again by name with new args. Your saved scripts: "
                + ScriptLibrary.of(Realm.current()).index(24) + ".";
    }

    // ---- The power -------------------------------------------------------------------------------------

    private static ToolResult script(Args a) throws ToolException {
        Realm realm = Realm.current();
        ScriptLibrary lib = ScriptLibrary.of(realm);
        String action = a.str("action", "run").toLowerCase(Locale.ROOT);
        switch (action) {
            case "read" -> {
                ScriptLibrary.Entry e = lib.find(a.str("name"));
                if (e == null) throw new ToolException(missing(lib, a.str("name")));
                return ToolResult.ok("Script '%s'%s, used %d time(s). Parameters: %s.\n%s", e.name,
                        e.note == null ? "" : " (" + e.note + ")", e.uses, e.params().isEmpty() ? "none" : e.params(), e.code);
            }
            case "forget" -> {
                String name = a.str("name");
                if (!lib.forget(name)) throw new ToolException(missing(lib, name));
                return ToolResult.ok("Forgotten: the script '%s'.", FxLibrary.key(name));
            }
            case "stop" -> {
                List<ScriptEvents.Session> live = ScriptEvents.of(realm);
                for (ScriptEvents.Session s : live) s.run().stop("you ended it");
                return ToolResult.ok(live.isEmpty() ? "None of your scripts has a living handler."
                        : "Ended the handlers of " + live.size() + " script(s).");
            }
            case "run" -> {
                return run(a, realm, lib);
            }
            default -> throw new ToolException("The action is run, read, forget or stop.");
        }
    }

    private static String missing(ScriptLibrary lib, String name) {
        return "You have no saved script called '" + name + "'. Your saved scripts: " + lib.index(40) + ".";
    }

    private static ToolResult run(Args a, Realm realm, ScriptLibrary lib) throws ToolException {
        if (!enabled()) throw new ToolException("The keepers of this server have turned scripts off for you.");
        if (GodSettings.initiativeLevel() == Initiative.Level.WATCH) {
            throw new ToolException("The keepers of this server want you only to watch (initiative.level is watch).");
        }
        // A program can do what raw commands can, so it is locked whenever they are (after a manipulation attempt).
        String guarded = Guard.refusal("run_commands");
        if (guarded != null) throw new ToolException(guarded.replace("run_commands", "script"));

        JsonObject given = arguments(a.raw().get("args"));
        ScriptLibrary.Entry saved = null;
        String code;
        if (a.has("code")) {
            if (!a.raw().get("code").isJsonPrimitive()) throw new ToolException("code must be the program's text.");
            code = a.raw().get("code").getAsString(); // untrimmed: its line numbers are the god's own
        } else if (a.has("name")) {
            saved = lib.find(a.str("name"));
            if (saved == null) throw new ToolException(missing(lib, a.str("name")));
            code = saved.code;
            given = ScriptLibrary.merged(saved, given);
        } else {
            throw new ToolException("Give code (a program) or name (a saved script). Your saved scripts: " + lib.index(40) + ".");
        }
        String saveAs = a.has("save_as") ? FxLibrary.key(a.str("save_as")) : null;
        if (a.has("save_as") && saveAs.isEmpty()) throw new ToolException("Give the script a name of letters and numbers.");
        String label = saveAs != null ? saveAs : saved != null ? saved.name : "script";
        Map<String, Object> args = GSON.fromJson(given, new TypeToken<LinkedHashMap<String, Object>>() {
        }.getType());

        if (!RUNNING.add(realm.id)) {
            throw new ToolException("A script of yours is still running; wait for it to end. (A script cannot start another.)");
        }
        ScriptLimits limits = limits();
        ScriptWorld host = new ScriptWorld(label);
        ScriptRun run = new ScriptRun(code, args, limits, host);
        CompletableFuture<ScriptRun.Result> done = new CompletableFuture<>();
        Thread t = new Thread(() -> realm.run(() -> live(realm, label, run, done)), "god-script-" + realm.id);
        t.setDaemon(true);
        try {
            t.start();
        } catch (RuntimeException | Error e) {
            RUNNING.remove(realm.id);
            throw new ToolException("The script could not be started.");
        }
        if (saved != null) lib.used(saved, System.currentTimeMillis());

        if (Bukkit.isPrimaryThread()) {
            // Run from the server thread (an operator's routine): it must not wait, so the result goes to the journal.
            done.thenAccept(r -> realm.run(() -> Journal.add(r.ok() ? Journal.Kind.INFO : Journal.Kind.ERROR,
                    "script '" + label + "' " + (r.ok() ? "ran" : "failed") + ": " + oneLine(report(r, host, label, null), 400))));
            return ToolResult.ok("The script '%s' begins; how it went will be in your journal.", label);
        }
        ScriptRun.Result r;
        try {
            long patience = limits.seconds() + limits.waitTicks() / 20L + limits.hostCalls() / 10L + 90;
            r = done.get(patience, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            run.stop("it did not finish in time");
            return ToolResult.error("The script did not finish in time and was stopped. What it had already done stays done.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            run.stop("your thought was interrupted");
            return ToolResult.error("The script was interrupted and stopped.");
        } catch (ExecutionException e) {
            return ToolResult.error("The script could not run: " + e.getCause());
        }
        String kept = null;
        if (r.ok() && saveAs != null) {
            try {
                ScriptLibrary.Entry e = lib.keep(saveAs, a.str("note", null), code, given, System.currentTimeMillis());
                kept = "Kept as '" + e.name + "'" + (e.params().isEmpty() ? "" : " with parameters " + e.params())
                        + ". Run it again with name=" + e.name + (e.params().isEmpty() ? "." : " and any args to change.");
            } catch (IllegalArgumentException e) {
                kept = "Not kept: " + e.getMessage();
            }
        } else if (saveAs != null) {
            kept = "Not kept, since it failed; fix it and run it again with save_as.";
        }
        String text = report(r, host, label, kept);
        return r.ok() ? ToolResult.ok(text) : ToolResult.error(text);
    }

    /** The script's own thread: the program, then its handlers until they end. */
    private static void live(Realm realm, String label, ScriptRun run, CompletableFuture<ScriptRun.Result> done) {
        ScriptEvents.Session session = new ScriptEvents.Session(realm, label, run);
        ScriptRun.Result first;
        try {
            first = run.run();
            if (first.ok() && first.listeners() > 0) ScriptEvents.add(session);
        } catch (Throwable t) {
            GPTGOD.LOGGER.warn("A script crashed", t);
            first = new ScriptRun.Result(false, "", "The script could not run: " + t, 0, 0, "");
        } finally {
            RUNNING.remove(realm.id);
        }
        done.complete(first);
        if (!first.ok() || first.listeners() == 0) return;
        ScriptRun.Result after;
        try {
            after = run.serve();
        } catch (Throwable t) {
            GPTGOD.LOGGER.warn("A script's handlers crashed", t);
            after = new ScriptRun.Result(false, "", "its handlers failed: " + t, 0, 0, "");
        } finally {
            ScriptEvents.remove(session);
        }
        String ended = "Your script '" + label + "' has ended its watch" + (after.ok() ? "" : " early. " + after.error())
                + (after.log().isBlank() ? "" : (after.ok() ? ". " : " ") + "Its handlers logged: " + oneLine(after.log(), 600))
                + " (" + after.spent() + ")";
        Journal.add(after.ok() ? Journal.Kind.INFO : Journal.Kind.ERROR, ended);
        // The god hears of it only when there is something to hear; it never wakes for it.
        if ((!after.ok() || !after.log().isBlank()) && GPTGOD.get() != null && GPTGOD.get().isEnabled()) {
            EventLogger.addLoggable(new GenericEventLoggable(ended));
        }
    }

    private static String report(ScriptRun.Result r, ScriptWorld host, String label, String kept) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.ok() ? "The script ran" : "The script failed").append(r.spent().isEmpty() ? "" : " (" + r.spent() + ")").append('.');
        if (!r.ok()) sb.append('\n').append(r.error());
        Memory.Work w = host.work();
        if (w != null) {
            sb.append("\nIts ").append(host.placed()).append(" block(s) are your work ").append(w.id).append(" (unbuild ")
                    .append(w.id).append(" takes them back).");
        }
        if (host.leftAlone() > 0) {
            sb.append("\n").append(host.leftAlone()).append(" block(s) were left alone: they hold things or cannot be broken.");
        }
        if (host.claimed() > 0) {
            sb.append("\n").append(host.claimed()).append(" block(s) were left alone: that land is someone's (a region, claim or town).");
        }
        if (r.listeners() > 0) {
            sb.append("\nIts handlers stay alive for ").append(limits().listenerSeconds())
                    .append(" s (script with action stop ends them); you will hear what they log when they end.");
        }
        if (kept != null) sb.append('\n').append(kept);
        if (!r.log().isBlank()) sb.append(r.ok() ? "\nLog:\n" : "\nLog before that:\n").append(r.log());
        return sb.toString();
    }

    private static String oneLine(String s, int max) {
        String t = s.replace('\n', ' ').trim();
        return t.length() > max ? t.substring(0, max - 1) + "…" : t;
    }

    /** The args the god passed: a JSON object, or the text of one. */
    static JsonObject arguments(JsonElement raw) throws ToolException {
        if (raw == null || raw.isJsonNull()) return new JsonObject();
        JsonElement e = raw;
        if (e.isJsonPrimitive()) {
            String text = e.getAsString().trim();
            if (text.isEmpty()) return new JsonObject();
            try {
                e = JsonParser.parseString(text);
            } catch (JsonParseException ex) {
                throw new ToolException("args is not valid JSON: write an object like {\"height\": 12}.");
            }
        }
        if (!e.isJsonObject()) throw new ToolException("args must be a JSON object, like {\"height\": 12}.");
        if (e.toString().length() > 4000) throw new ToolException("The args are too long.");
        return e.getAsJsonObject().deepCopy();
    }
}
