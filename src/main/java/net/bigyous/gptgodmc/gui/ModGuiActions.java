package net.bigyous.gptgodmc.gui;

import java.util.List;
import java.util.Locale;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import com.google.gson.JsonObject;

import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.client.ModGuiMessages;
import net.bigyous.gptgodmc.client.ModGuiRequests;
import net.bigyous.gptgodmc.client.ModGuiRequests.Request;
import net.bigyous.gptgodmc.client.ModGuiRequests.Spec;
import net.bigyous.gptgodmc.commands.GodCommand;
import net.bigyous.gptgodmc.god.GodNames;
import net.bigyous.gptgodmc.god.Journal;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.Power;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.TabList;
import net.bigyous.gptgodmc.god.vessel.VesselLook;
import net.bigyous.gptgodmc.god.vessel.VesselMood;
import net.bigyous.gptgodmc.gui.MenuModel.Action;
import net.bigyous.gptgodmc.gui.MenuModel.Kind;

/**
 * Carries out what the client mod's God Manager screen asks. Each action goes through the chest menu's
 * own code ({@link GodManager#perform} and the /god commands behind it), so the two cannot drift; this
 * class only checks the request first (so a wrong value is refused with a message for the field it came
 * from) and returns what the plugin said as the action's result. Server thread; the caller has already
 * checked gptgodmc.admin.
 */
public final class ModGuiActions {
    private ModGuiActions() {
    }

    /**
     * How one action ended.
     *
     * @param field the input the error belongs to (null = the action as a whole)
     * @param value something the screen needs back (the new god's id, a resolved player name)
     */
    record Outcome(boolean ok, String field, String error, String value) {
        static final Outcome OK = new Outcome(true, null, null, null);

        static Outcome fail(String field, String error) {
            return new Outcome(false, field, error, null);
        }

        static Outcome value(String value) {
            return new Outcome(true, null, null, value);
        }
    }

    /** Does what the request asks and returns the gui_result to send back. */
    public static JsonObject act(Player p, Request r) {
        Spec spec = ModGuiRequests.ACTIONS.get(r.action());
        if (spec == null) return fail(r, null, "Unknown action.");
        Realm named = r.realm() == null ? null : Realm.byId(r.realm());
        if (named == null && r.realm() != null && !r.realm().isBlank()) {
            return fail(r, null, "That god is no longer here.");
        }
        Realm here = Realm.of(p.getWorld());
        Realm realm = named != null ? named : here != null ? here : Realm.primary();
        String missing = ModGuiRequests.missing(r);
        if (missing != null) return fail(r, missing, hint(missing));
        if ((spec.kind() == Kind.DRAFT_CREATE || spec.kind() == Kind.REMOVE_GOD)
                && !p.hasPermission(GodCommand.PANTHEON_PERMISSION)) {
            return fail(r, null, "Creating and removing gods needs " + GodCommand.PANTHEON_PERMISSION + ".");
        }
        String needs = needs(spec, r, realm);
        if (needs != null) {
            return ModGuiMessages.result(r.id(), false, null, needs.equals("name")
                    ? "Type its name (" + confirmName(realm) + ") to confirm." : "Confirm to go ahead.", null, needs);
        }
        Feedback.begin(p);
        Outcome out;
        try {
            Outcome[] box = new Outcome[1];
            realm.run(() -> box[0] = run(p, realm, spec, r));
            out = box[0] == null ? Outcome.fail(null, "Nothing happened.") : box[0];
        } catch (RuntimeException e) {
            GPTGOD.LOGGER.warn("God Manager screen action " + r.action() + " failed: " + e);
            out = Outcome.fail(null, "That failed on the server: " + e.getMessage());
        }
        List<String> said = Feedback.end(p);
        String message = said.isEmpty() ? null : String.join(" ", said);
        if (!out.ok()) {
            return ModGuiMessages.result(r.id(), false, out.field(), out.error() != null ? out.error()
                    : message != null ? message : "That did not work.", null, null);
        }
        JsonObject result = ModGuiMessages.result(r.id(), true, null, null, message, null);
        if (out.value() != null) result.addProperty("value", out.value());
        return result;
    }

    private static JsonObject fail(Request r, String field, String error) {
        return ModGuiMessages.result(r.id(), false, field, error, null, null);
    }

    private static String hint(String arg) {
        return switch (arg) {
            case "text" -> "Type something first.";
            case "player" -> "Name a player first.";
            default -> "Missing: " + arg + ".";
        };
    }

    /** The name an operator types to confirm erasing or removing this god: its name, or its id while unborn. */
    static String confirmName(Realm realm) {
        Persona p = realm.persona();
        return p == null ? realm.id : p.shortName();
    }

    /** What confirmation is still missing (see {@link ModGuiRequests#needs}); presets say themselves whether they need one. */
    private static String needs(Spec spec, Request r, Realm realm) {
        if (spec.kind() == Kind.PRESET) {
            for (MenuModel.Preset preset : MenuLayout.PRESETS) {
                if (preset.id().equals(r.arg("id")) && preset.confirm() && !"true".equalsIgnoreCase(r.confirm())) {
                    return "confirm";
                }
            }
            return null;
        }
        return ModGuiRequests.needs(spec, r.confirm(), confirmName(realm));
    }

    /** One action, as the bound god. */
    private static Outcome run(Player p, Realm realm, Spec spec, Request r) {
        String action = r.action();
        switch (action) {
            case "mood" -> {
                VesselMood m = VesselMood.parse(r.text("mood"));
                if (m == null) return Outcome.fail("mood", "Unknown mood.");
                return chest(p, Kind.MOOD, m.id());
            }
            case "look" -> {
                String look = r.text("look").toLowerCase(Locale.ROOT);
                if (!look.equals("auto") && !VesselLook.NAMES.contains(look)) return Outcome.fail("look", "Unknown look.");
                return chest(p, Kind.LOOK, look);
            }
            case "voice" -> {
                String v = r.text("voice");
                if (!v.matches("[a-z]{2}_[a-z0-9_]+")) return Outcome.fail("voice", "Not a Kokoro voice name.");
                return chest(p, Kind.VOICE, v);
            }
            case "voice_effect" -> {
                String e = r.text("effect");
                if (!e.equals("auto") && !net.bigyous.gptgodmc.voice.VoiceTone.isEffect(e)) {
                    return Outcome.fail("effect", "Unknown voice effect.");
                }
                return chest(p, Kind.VOICE_EFFECT, e);
            }
            case "power" -> {
                if (Power.parse(r.text("power")) == null) return Outcome.fail("power", "Unknown power.");
                return chest(p, Kind.POWER, r.text("power"));
            }
            case "model" -> {
                String m = r.text("model");
                if (!m.equals("default") && !MenuLayout.MODELS.contains(m)) return Outcome.fail("model", "Unknown model.");
                return chest(p, Kind.MODEL, m);
            }
            case "effort" -> {
                if (!MenuLayout.EFFORT_SLOTS.containsValue(r.text("effort"))) return Outcome.fail("effort", "Unknown effort.");
                return chest(p, Kind.EFFORT, r.text("effort"));
            }
            case "dial" -> {
                return dial(p, r.text("key"), r.text("value"));
            }
            case "rebirth" -> {
                String a = r.text("archetype").toLowerCase(Locale.ROOT);
                if (!a.equals("random") && !Persona.archetypes().containsKey(a)) {
                    return Outcome.fail("archetype", "Unknown archetype. Known: "
                            + String.join(", ", Persona.archetypes().keySet()));
                }
                return chest(p, Kind.PERSONA, a);
            }
            case "remove_god" -> {
                GodCommand.remove(p, new String[] { "remove", realm.id, "confirm" });
                return Realm.byId(realm.id) == null ? Outcome.OK : Outcome.fail(null, null);
            }
            case "create_god" -> {
                return create(p, r);
            }
            case "command" -> {
                return GodManager.command(p, r.text("text"), 0, null) ? Outcome.OK : Outcome.fail("text", null);
            }
            case "preset" -> {
                for (MenuModel.Preset preset : MenuLayout.PRESETS) {
                    if (preset.id().equals(r.arg("id"))) return chest(p, Kind.PRESET, preset.id());
                }
                return Outcome.fail(null, "Unknown preset.");
            }
            case "cancel_command" -> {
                if (!r.text("n").matches("\\d{1,6}")) return Outcome.fail(null, "Not a command number.");
                return chest(p, Kind.CANCEL_COMMAND, r.text("n"));
            }
            case "mortal_open" -> {
                String name = GodManager.resolveName(r.text("player"));
                if (name == null) return Outcome.fail("player", "\"" + r.text("player") + "\" is not a player name.");
                return Outcome.value(name);
            }
            case "favor" -> {
                int v;
                try {
                    v = Integer.parseInt(r.text("value").trim());
                } catch (NumberFormatException e) {
                    return Outcome.fail("value", "Favor is a whole number from -10 to 10.");
                }
                if (v < -10 || v > 10) return Outcome.fail("value", "Favor is a whole number from -10 to 10.");
                Outcome who = player(r);
                if (who != null) return who;
                GodManager.perform(p, Action.of(Kind.SELECT_PLAYER, r.text("player")));
                return chest(p, Kind.FAVOR, "set:" + v);
            }
            case "note_add", "deed_add" -> {
                Outcome who = player(r);
                if (who != null) return who;
                String text = GodManager.clip(r.text("text"));
                if (action.equals("note_add")) {
                    Memory.get().remember(r.text("player"), text);
                    Feedback.take(p, "It will remember that about " + r.text("player") + ".");
                } else {
                    Memory.get().addDeed(r.text("player"), text);
                    Feedback.take(p, "Recorded among " + r.text("player") + "'s deeds.");
                }
                Memory.get().save();
                return Outcome.OK;
            }
            case "note_del", "deed_del" -> {
                Outcome who = player(r);
                if (who != null) return who;
                String full = fullText(realm, r.text("player"), r.text("at"), r.text("text"), action.equals("note_del"));
                if (full == null) return Outcome.fail(null, "That memory is already gone.");
                GodManager.perform(p, Action.of(Kind.SELECT_PLAYER, r.text("player")));
                return chest(p, spec.kind(), r.text("at") + "|" + full);
            }
            case "notes_clear", "deeds_clear", "forget" -> {
                Outcome who = player(r);
                if (who != null) return who;
                return chest(p, spec.kind(), r.text("player"));
            }
            case "title_set" -> {
                Outcome who = player(r);
                if (who != null) return who;
                String t = r.text("title").trim();
                if (t.length() > 40) return Outcome.fail("title", "A title is at most 40 characters.");
                Memory.get().setTitle(r.text("player"), t.isEmpty() ? null : t, null);
                Memory.get().save();
                try {
                    net.bigyous.gptgodmc.god.Devotion.refreshAll();
                } catch (RuntimeException e) {
                    // names refresh on the next tab-list pass
                }
                Journal.add(Journal.Kind.INFO, "Title of " + r.text("player") + " set by " + p.getName() + ": "
                        + (t.isEmpty() ? "(none)" : t));
                Feedback.take(p, t.isEmpty() ? r.text("player") + " bears no title now."
                        : r.text("player") + " now bears the title " + t + ".");
                return Outcome.OK;
            }
            case "summary_set" -> {
                Outcome who = player(r);
                if (who != null) return who;
                String t = r.text("summary").trim();
                Memory m = Memory.get();
                Memory.Subject s = m.subject(r.text("player"));
                synchronized (m) {
                    s.summary = t.isEmpty() ? null : t;
                }
                m.save();
                Journal.add(Journal.Kind.INFO, "Summary of " + r.text("player") + " rewritten by " + p.getName());
                Feedback.take(p, "It now remembers " + r.text("player") + "'s past as you wrote it.");
                return Outcome.OK;
            }
            case "switch" -> {
                for (MenuModel.Setting s : MenuLayout.SETTINGS) {
                    if (s.key().equals(r.arg("key"))) return chest(p, Kind.TOGGLE_SETTING, s.key());
                }
                return Outcome.fail(null, "Not a quick switch.");
            }
            case "config_set" -> {
                return config(p, r.text("key"), r.text("value"));
            }
            default -> {
                // wake, pause, dormant, calm_heart, summon, fly_to, follow, vanish, true_form, end_true_form, voice_sample
                return chest(p, spec.kind(), null);
            }
        }
    }

    /** The chest menu's own click. */
    private static Outcome chest(Player p, Kind kind, String arg) {
        GodManager.perform(p, Action.of(kind, arg));
        return Outcome.OK;
    }

    /** Null when the player argument is a possible player name, else the failure. */
    private static Outcome player(Request r) {
        return r.text("player").matches("[A-Za-z0-9_.*-]{1,32}") ? null
                : Outcome.fail("player", "\"" + r.text("player") + "\" is not a player name.");
    }

    /** The whole text of the note or deed the screen means (it is shown a clipped copy), or null when gone. */
    private static String fullText(Realm realm, String player, String at, String shown, boolean note) {
        long when;
        try {
            when = Long.parseLong(at.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        MenuModel.Mortal m = GodManager.mortal(player, realm);
        if (m == null) return null;
        for (MenuModel.Entry e : note ? m.notes() : m.deeds()) {
            if (e.at() == when && (e.text().equals(shown) || ModGuiMessages.clip(e.text(), 240).equals(shown))) {
                return e.text();
            }
        }
        return null;
    }

    private static Outcome dial(Player p, String key, String value) {
        if (Persona.current() == null) return Outcome.fail(null, "The god is not born yet.");
        if (key.equals("reset")) return chest(p, Kind.DIAL, "reset");
        if (!Persona.DIALS.contains(key)) return Outcome.fail(key, "Unknown dial.");
        if (value.equals("reset")) return chest(p, Kind.DIAL, key + ":reset");
        int v;
        try {
            v = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return Outcome.fail(key, "A dial is a whole number from 0 to 10.");
        }
        if (v < 0 || v > 10) return Outcome.fail(key, "A dial is a whole number from 0 to 10.");
        int delta = v - Persona.current().dial(key);
        if (delta == 0) return Outcome.OK;
        return chest(p, Kind.DIAL, key + ":" + delta);
    }

    /** Creates a god with /god create's own code, after checking each field so the error names it. */
    private static Outcome create(Player p, Request r) {
        String name = r.text("name").trim();
        String archetype = r.text("archetype").toLowerCase(Locale.ROOT);
        String power = r.text("power").toLowerCase(Locale.ROOT);
        String problem = createProblem(name, archetype, power);
        if (problem != null) {
            int bar = problem.indexOf('|');
            return Outcome.fail(problem.substring(0, bar), problem.substring(bar + 1));
        }
        Realm made = GodCommand.create(p, new String[] { "create", name.isEmpty() ? "-" : name, archetype, power });
        return made == null ? Outcome.fail(name.isEmpty() ? null : "name", null) : Outcome.value(made.id);
    }

    /** "field|message" for the first thing wrong with a new god's name, archetype or power, or null. */
    static String createProblem(String name, String archetype, String power) {
        if (!name.isEmpty()) {
            if (name.contains(" ")) return "name|The name must be one word.";
            String problem = GodNames.operatorProblem(name);
            if (problem != null) return "name|" + problem;
        }
        if (!archetype.equals("random") && !Persona.archetypes().containsKey(archetype)) {
            return "archetype|Unknown archetype. Known: " + String.join(", ", Persona.archetypes().keySet());
        }
        if (Power.parse(power) == null) return "power|Unknown power. One of: lesser, greater, supreme.";
        return null;
    }

    /** Sets one Server config value, refused unless it passes the same bounds the chest menu's clicks keep. */
    private static Outcome config(Player p, String key, String value) {
        Advanced.Option o = Advanced.find(key);
        if (o == null) return Outcome.fail(key, "Not a setting of this menu.");
        if (ModGuiViews.locked(o)) {
            return Outcome.fail(key, o.label() + " needs a restart: change it in config.yml.");
        }
        String problem = valueProblem(o, value);
        if (problem != null) return Outcome.fail(key, problem);
        String after = normal(o, value);
        FileConfiguration cfg = GPTGOD.get().getConfig();
        Object raw = cfg.get(key);
        String before = raw == null ? o.fallback() : String.valueOf(raw);
        if (after.equalsIgnoreCase(before)) return Outcome.OK;
        // From here on: what GodManager.tune does after a click.
        switch (o.type()) {
            case BOOL -> cfg.set(key, Boolean.parseBoolean(after));
            case INT -> cfg.set(key, Long.parseLong(after));
            case DOUBLE -> cfg.set(key, Double.parseDouble(after));
            default -> cfg.set(key, after);
        }
        GPTGOD.get().saveConfig();
        Journal.add(Journal.Kind.INFO, "Setting " + key + " changed by " + p.getName() + ": " + before + " -> " + after);
        GPTGOD.LOGGER.info("[God Manager] " + p.getName() + " set " + key + ": " + before + " -> " + after);
        if (key.equals("god.tab-list")) TabList.refreshAll();
        if (key.startsWith("viewer.")) Feedback.take(p, net.bigyous.gptgodmc.viewer.MindViewer.sync());
        if (key.equals("vessel.enabled") && !Boolean.parseBoolean(after)) {
            for (Realm g : Realm.all()) {
                g.run(() -> {
                    if (net.bigyous.gptgodmc.god.God.vessel().isManifested()) net.bigyous.gptgodmc.god.God.vessel().vanish();
                });
            }
        }
        Feedback.take(p, o.label() + ": " + Advanced.show(o, after) + " (saved to config.yml).");
        return Outcome.OK;
    }

    /** What is wrong with a value for this setting, or null. Pure. */
    static String valueProblem(Advanced.Option o, String value) {
        String v = value == null ? "" : value.trim();
        switch (o.type()) {
            case BOOL -> {
                return v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false") ? null : "Must be on or off.";
            }
            case CHOICE -> {
                return o.choices().contains(v.toLowerCase(Locale.ROOT)) ? null
                        : "Not one of: " + String.join(", ", o.choices()) + ".";
            }
            default -> {
                double d;
                try {
                    d = Double.parseDouble(v);
                } catch (NumberFormatException e) {
                    return "Not a number.";
                }
                if (Double.isNaN(d) || Double.isInfinite(d)) return "Not a number.";
                if (o.type() == Advanced.Type.INT && d != Math.rint(d)) return "Must be a whole number.";
                if (d < o.min() || d > o.max()) {
                    return "Must be between " + trim(o.min()) + " and " + trim(o.max())
                            + (o.unit().isEmpty() ? "" : " " + o.unit()) + ".";
                }
                return null;
            }
        }
    }

    /** A valid value as config.yml stores it. */
    static String normal(Advanced.Option o, String value) {
        String v = value.trim();
        return switch (o.type()) {
            case BOOL, CHOICE -> v.toLowerCase(Locale.ROOT);
            case INT -> String.valueOf(Math.round(Double.parseDouble(v)));
            case DOUBLE -> String.valueOf(Math.round(Double.parseDouble(v) * 100) / 100.0);
        };
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
