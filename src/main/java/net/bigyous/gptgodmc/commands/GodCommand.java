package net.bigyous.gptgodmc.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.bigyous.gptgodmc.EventLogger;
import net.bigyous.gptgodmc.GPTGOD;
import net.bigyous.gptgodmc.god.Edicts;
import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.GodClient;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Journal;
import net.bigyous.gptgodmc.god.Memory;
import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.UsageLog;
import net.bigyous.gptgodmc.god.build.Builder;
import net.bigyous.gptgodmc.god.tools.GodTool;
import net.bigyous.gptgodmc.god.tools.ToolException;
import net.bigyous.gptgodmc.god.tools.ToolRegistry;
import net.bigyous.gptgodmc.god.vessel.Vessel;
import net.bigyous.gptgodmc.god.vessel.VesselLook;
import net.bigyous.gptgodmc.god.vessel.VesselModel;
import net.bigyous.gptgodmc.god.vessel.VesselMood;
import net.bigyous.gptgodmc.god.vision.Vision;
import net.bigyous.gptgodmc.gui.GodManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** /god — operator controls. Also answers to the old /sudogod, /try, /tokens and /godinfo. */
public final class GodCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("status", "say", "wake", "pause", "resume", "setkey", "model",
            "effort", "persona", "rebirth", "vessel", "view", "tool", "tools", "memory", "forget", "undo", "usage", "realms",
            "reload", "journal", "command", "commands", "manager", "help", "create", "remove", "list", "select",
            "power", "godslayer", "revive", "pardon", "tutorial");
    /** Subcommands that make sense without choosing a god, even when several share the sender's world. */
    private static final java.util.Set<String> ANY_GOD = java.util.Set.of("status", "list", "realms", "help",
            "manager", "gui", "menu", "reload", "setkey", "usage", "tools", "create", "remove", "select", "godslayer",
            "pardon", "tutorial", "guide");
    /** Subcommands /god @all runs for every god. */
    private static final java.util.Set<String> FOR_ALL = java.util.Set.of("pause", "resume", "wake", "say",
            "command");
    /** The god each operator chose with /god select (by sender name; the console is "CONSOLE"). */
    private static final java.util.Map<String, String> SELECTED = new java.util.concurrent.ConcurrentHashMap<>();
    /** Who may create and remove gods (each one costs as much to run as the first). */
    public static final String PANTHEON_PERMISSION = "gptgodmc.pantheon";

    /** One line per subcommand, for /god help. */
    private static final List<String> HELP = List.of(
            "/god status - who the god is and what it is doing",
            "/god manager - open the God Manager menu (also /god gui, /god menu); /god manager item gives the item",
            "/god manager chest - the chest menu, for operators whose client mod would open its own screen",
            "/god command <text> - a BINDING command the god must carry out (it reports back to you)",
            "/god commands - list pending commands; /god command cancel <n|all> - withdraw them",
            "/god say <text> - a suggestion the god may weigh (not binding)",
            "/god wake [text] | pause | resume - attention and thinking",
            "/god persona <archetype|random|list> | rebirth - choose who the god is",
            "/god vessel <summon|follow|fly|rise|mood|gesture|look|dismiss> - its body",
            "/god view <eyes|vessel|overhead|panorama|orbit> [target] [xray] - save a render",
            "/god tool <power> [json] | tools - run one of its powers directly",
            "/god memory | forget | undo [work] | journal [n] | realms | reload",
            "/god create <name|-> [archetype] [lesser|greater|supreme] - a new god in this world",
            "/god remove <name> confirm | list | select <name|none> | power [level]",
            "/god godslayer [player|@a] | preview [variant] - the god-killing blade; /god revive - raise the slain",
            "/god pardon <player> - clear a player's manipulation strikes",
            "/god tutorial [player] - the guide to the gods (anyone may open their own; also /guide)",
            "With several gods: /god @<name> <subcommand>, or /god @all pause|resume|wake|say|command");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            String[] args) {
        // /god tutorial is the one subcommand for everyone (plugin.yml leaves /god open for it).
        if (args.length > 0 && (args[0].equalsIgnoreCase("tutorial") || args[0].equalsIgnoreCase("guide"))) {
            net.bigyous.gptgodmc.guide.Guides.command(sender, Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        if (sender instanceof Player p && !p.hasPermission(GodManager.PERMISSION)) {
            sender.sendMessage(Component.text("Only operators may command the god. /god tutorial (or /guide) "
                    + "opens your guide to the gods.", NamedTextColor.RED));
            return true;
        }
        // Legacy aliases.
        switch (label.toLowerCase(Locale.ROOT)) {
            case "sudogod" -> args = prepend("say", args);
            case "try" -> args = prepend("tool", args);
            case "tokens" -> args = new String[] { "usage" };
            case "godinfo" -> args = new String[] { "status" };
            default -> {
            }
        }
        // Which god is being commanded: "/god @<name> ...", else the one chosen with /god select, else the
        // god of the sender's world. Where several share it, the operator has to say which.
        Realm realm = null;
        boolean chosen = false;
        if (args.length > 0 && args[0].startsWith("@")) {
            String wanted = args[0].substring(1);
            args = Arrays.copyOfRange(args, 1, args.length);
            if (wanted.equalsIgnoreCase("all")) {
                String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                if (!FOR_ALL.contains(sub)) {
                    msg(sender, "/god @all works with: " + String.join(", ", FOR_ALL) + ".");
                    return true;
                }
                String[] words = args;
                for (Realm r : Realm.all()) {
                    msg(sender, "— " + r.displayName() + ":");
                    r.run(() -> dispatch(sender, words));
                }
                return true;
            }
            realm = Realm.byName(wanted);
            if (realm == null) {
                msg(sender, "No god is called " + wanted + ". Gods: " + godNames() + ".");
                return true;
            }
            chosen = true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (realm == null) {
            String picked = SELECTED.get(sender.getName());
            realm = picked == null ? null : Realm.byId(picked);
            chosen = realm != null;
        }
        if (realm == null) {
            List<Realm> here = sender instanceof Player p ? Realm.godsOf(p.getWorld()) : List.of(Realm.primary());
            if (here.isEmpty()) here = List.of(Realm.primary());
            realm = here.get(0);
            if (here.size() > 1 && !ANY_GOD.contains(sub)) {
                msg(sender, "Several gods share this world: " + String.join(", ", here.stream()
                        .map(Realm::displayName).toList()) + ". Say which: /god @<name> " + sub
                        + ", or /god select <name>.");
                return true;
            }
        }
        String[] words = args.length == 0 ? new String[] { "status" } : args;
        realm.run(() -> dispatch(sender, words));
        return true;
    }

    private static String godNames() {
        return String.join(", ", Realm.all().stream().map(r -> r.displayName()
                + (r.displayName().equalsIgnoreCase(r.id) ? "" : " (@" + r.id + ")")).toList());
    }

    private static String realmIds() {
        return String.join(", ", Realm.all().stream().map(r -> r.id).toList());
    }

    /** Runs one subcommand with the chosen god bound as the current one. */
    private void dispatch(CommandSender sender, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        String rest = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        switch (sub) {
            case "status" -> status(sender);
            case "realms" -> {
                if (Realm.several()) {
                    list(sender);
                    return;
                }
                for (Realm r : Realm.all()) {
                    msg(sender, r.id + (r.isPrimary() ? " (primary)" : "") + ": " + String.join(", ",
                            r.worldList().stream().map(org.bukkit.World::getName).toList()) + " — "
                            + r.players().size() + " mortal(s)");
                }
            }
            case "say" -> {
                if (rest.isEmpty()) {
                    msg(sender, "Usage: /god say <directive for the god>");
                } else {
                    God.wake(rest);
                    msg(sender, "Your directive has been delivered; the god will look now.");
                }
            }
            case "wake" -> {
                God.wake(rest.isEmpty() ? null : rest);
                msg(sender, "The god turns its attention to the world.");
            }
            case "pause" -> {
                God.setPaused(true);
                msg(sender, "The god's thoughts are paused (powers and the vessel still work).");
            }
            case "resume" -> {
                God.setPaused(false);
                msg(sender, "The god thinks again.");
            }
            case "setkey" -> setKey(sender, rest);
            case "model" -> {
                if (Realm.several()) {
                    setOwn(sender, "model", rest);
                } else {
                    setConfig(sender, net.bigyous.gptgodmc.god.Llm.anthropic() ? "anthropic.model" : "llm.model",
                            rest, "Model");
                }
            }
            case "effort" -> {
                String level = rest.trim().toLowerCase(Locale.ROOT);
                if (!level.isEmpty() && !level.equals("auto") && !level.equals("default")
                        && !net.bigyous.gptgodmc.god.EffortPicker.LEVELS.contains(level)) {
                    msg(sender, "Effort must be auto, low, medium or high (or default).");
                } else if (Realm.several()) {
                    setOwn(sender, "effort", level);
                } else {
                    setConfig(sender, "anthropic.effort", level, "Effort");
                }
            }
            case "power" -> power(sender, rest);
            case "create" -> create(sender, args);
            case "remove" -> remove(sender, args);
            case "pardon" -> {
                if (args.length < 2) {
                    msg(sender, "Usage: /god pardon <player> - clears their manipulation strikes.");
                } else {
                    net.bigyous.gptgodmc.god.Strikes strikes = net.bigyous.gptgodmc.god.Strikes.get();
                    int n = strikes.pardon(args[1]);
                    strikes.save();
                    msg(sender, n == 0 ? args[1] + " has no strikes." : "Cleared " + n + " strike(s) from " + args[1] + ".");
                }
            }
            case "list" -> list(sender);
            case "select" -> select(sender, rest);
            case "persona" -> rebirth(sender, rest);
            case "rebirth" -> rebirth(sender, "random");
            case "vessel" -> vessel(sender, args);
            case "view" -> view(sender, args);
            case "tool" -> tool(sender, args);
            case "tools" -> msg(sender, "Powers: " + String.join(", ",
                    ToolRegistry.all().stream().map(GodTool::name).toList()));
            case "memory" -> memory(sender);
            case "forget" -> {
                Memory.reset();
                EventLogger.reset();
                msg(sender, "The god's memory is wiped (its identity remains).");
            }
            case "undo" -> runTool(sender, "unbuild", "{\"work\":\"" + rest.replace("\"", "") + "\"}");
            case "usage" -> usage(sender);
            case "journal" -> {
                for (Journal.Entry e : Journal.last(rest.isEmpty() ? 12 : Math.min(60, parseInt(rest, 12)))) {
                    msg(sender, "[" + e.kind().id() + "] " + shorten(e.text(), 240));
                }
            }
            case "command" -> edict(sender, args);
            case "commands" -> listEdicts(sender);
            case "manager", "gui", "menu" -> manager(sender, args);
            case "godslayer" -> msg(sender, net.bigyous.gptgodmc.god.Godslayer.command(sender,
                    Arrays.copyOfRange(args, 1, args.length)));
            case "revive" -> msg(sender, net.bigyous.gptgodmc.god.Godslayer.revive(Realm.current(),
                    sender.getName()));
            case "help" -> HELP.forEach(line -> msg(sender, line));
            case "tutorial", "guide" -> net.bigyous.gptgodmc.guide.Guides.command(sender,
                    Arrays.copyOfRange(args, 1, args.length));
            case "reload" -> {
                GPTGOD.get().reloadConfig();
                GodClient.close();
                Realm.reloadFlags();
                for (Realm r : Realm.all()) {
                    r.run(() -> {
                        God.vessel().refreshNameplate();
                        God.vessel().restyle();
                    });
                }
                msg(sender, "Configuration reloaded. (Changes to which worlds a god rules need a restart.)");
                msg(sender, net.bigyous.gptgodmc.viewer.MindViewer.sync());
            }
            default -> msg(sender, "Subcommands: " + String.join(", ", SUBS)
                    + ". /god help explains them. With several gods: /god @<realm> <subcommand>.");
        }
    }

    private void status(CommandSender s) {
        Persona p = Persona.current();
        Realm me = Realm.current();
        if (me.shared()) {
            msg(s, "Gods of this world: " + String.join(", ", me.domainGods().stream().map(Realm::displayName)
                    .toList()) + ". Showing " + me.displayName() + " (/god @<name> status for another).");
        }
        if (!me.shared() && Realm.several()) {
            msg(s, "Realm: " + Realm.current().id + " — worlds " + String.join(", ", Realm.current().worldList()
                    .stream().map(org.bukkit.World::getName).toList()) + " (all realms: " + realmIds() + ")");
        }
        msg(s, "God: " + (p == null ? "not yet born (it is created when it first awakens)" : p.title() + " — "
                + p.archetype));
        if (p != null) {
            msg(s, "Values: " + String.join(", ", p.likes) + " | Despises: " + String.join(", ", p.dislikes));
            msg(s, "Dials: " + p.dials);
        }
        msg(s, String.format("Awake: %s, paused: %s, mode: %s, model: %s (effort %s), key: %s", God.isAwake(),
                God.isPaused(), GodSettings.brainMode(), GodSettings.model(), GodSettings.effort(),
                GodSettings.apiKeySource()));
        if (Realm.several() || me.power() != net.bigyous.gptgodmc.god.Power.SUPREME) {
            msg(s, "Power: " + me.power().label + " (" + me.power().summary + ")");
        }
        if (God.brain() != null) {
            msg(s, "Thinking now: " + God.brain().isThinking() + (God.brain().lastError() == null ? ""
                    : " | last error: " + shorten(God.brain().lastError(), 200)));
        }
        msg(s, "Voice: " + net.bigyous.gptgodmc.voice.VoiceModule.voiceSummary());
        List<String> heart = Memory.get().heart.summary(System.currentTimeMillis());
        msg(s, "Heart: " + (heart.isEmpty() ? "at peace" : String.join(", ", heart)));
        msg(s, "Mortality: " + me.memory().mortality.describe(GodSettings.godslayerHealth(),
                System.currentTimeMillis()).split(" Only the Godslayer")[0]);
        msg(s, "Vessel: " + God.vessel().describe());
        msg(s, "Free will: " + GodSettings.initiativeLevel().id() + " | doing: "
                + net.bigyous.gptgodmc.god.Initiative.line(me.memory(), System.currentTimeMillis()));
        msg(s, "Pending events: " + EventLogger.pendingCount() + ", builds in progress: " + Builder.pendingJobs());
    }

    private void setKey(CommandSender sender, String key) {
        if (!(sender instanceof ConsoleCommandSender)) {
            msg(sender, "For safety, set the key from the server console (chat commands are logged): gptgod setkey <key>");
            return;
        }
        // "setkey <key>" is for the provider in use; "setkey anthropic|llm <key>" names one.
        String[] parts = key.trim().split("\\s+", 2);
        String which = parts.length == 2 ? parts[0].toLowerCase(Locale.ROOT) : "";
        boolean named = which.equals("anthropic") || which.equals("claude") || which.equals("llm")
                || which.equals("openai");
        if (key.isBlank() || (parts.length == 2 && !named)) {
            msg(sender, "Usage: gptgod setkey <key>   (or: gptgod setkey anthropic <key>, gptgod setkey llm <key>)");
            return;
        }
        boolean forClaude = named ? which.equals("anthropic") || which.equals("claude")
                : net.bigyous.gptgodmc.god.Llm.anthropic();
        String path = forClaude ? "anthropic.api-key" : "llm.api-key";
        GPTGOD.get().getConfig().set(path, (named ? parts[1] : key).trim());
        GPTGOD.get().saveConfig();
        GodClient.close();
        if (God.brain() != null) God.brain().clearBackoff();
        // The Anthropic wording is what the setup scripts and tutorial look for; keep it exactly.
        msg(sender, forClaude && net.bigyous.gptgodmc.god.Llm.anthropic()
                ? "API key saved to config.yml. The god can think."
                : "API key saved to config.yml (" + path + ")." + (GodClient.hasCredentials()
                        ? " The god can think." : " Still missing: " + net.bigyous.gptgodmc.god.Llm.notReady()));
    }

    // ---- Several gods ----------------------------------------------------------------------------

    /** Sets (or with "default", clears) the bound god's own model or effort. */
    private static void setOwn(CommandSender sender, String field, String value) {
        Realm me = Realm.current();
        net.bigyous.gptgodmc.god.GodProfile prof = me.profile();
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            String own = field.equals("model") ? prof.model : prof.effort;
            msg(sender, me.displayName() + "'s " + field + ": " + (own == null ? "default ("
                    + (field.equals("model") ? GodSettings.configuredModel() : GodSettings.configuredEffort()) + ")"
                    : own) + ". /god @" + me.id + " " + field + " <value|default>");
            return;
        }
        String set = v.equalsIgnoreCase("default") ? null : v;
        if (field.equals("model")) prof.model = set;
        else prof.effort = set == null ? null : set.toLowerCase(Locale.ROOT);
        prof.save();
        msg(sender, me.displayName() + "'s " + field + (set == null ? " follows config.yml again." : " set to " + set
                + "."));
    }

    private static void power(CommandSender sender, String level) {
        Realm me = Realm.current();
        if (level.isBlank()) {
            msg(sender, me.displayName() + " is a " + me.power().label + ": " + me.power().summary
                    + ". /god power <lesser|greater|supreme>");
            return;
        }
        net.bigyous.gptgodmc.god.Power p = net.bigyous.gptgodmc.god.Power.parse(level);
        if (p == null) {
            msg(sender, "Power must be lesser, greater or supreme.");
            return;
        }
        me.profile().power = p.id;
        me.profile().save();
        Journal.add(Journal.Kind.INFO, "Power set to " + p.id + " by " + sender.getName());
        msg(sender, me.displayName() + " is now a " + p.label + ": " + p.summary + ". It takes hold from its next "
                + "thought.");
    }

    private static boolean mayShapePantheon(CommandSender sender) {
        if (!(sender instanceof Player p) || p.hasPermission(PANTHEON_PERMISSION)) return true;
        msg(sender, "Creating and removing gods needs " + PANTHEON_PERMISSION + ".");
        return false;
    }

    /** /god create <name|-> [archetype] [power] — a new god in the worlds of the bound god. */
    public static Realm create(CommandSender sender, String[] args) {
        if (!mayShapePantheon(sender)) return null;
        if (args.length < 2) {
            msg(sender, "Usage: /god create <name|-> [archetype|random] [lesser|greater|supreme]. Use - to let it "
                    + "invent its own name. Archetypes: " + String.join(", ", Persona.archetypes().keySet()) + ".");
            return null;
        }
        if (GodSettings.roundsEnabled()) {
            msg(sender, "Gods cannot be added while round mode is on.");
            return null;
        }
        String name = null;
        String archetype = null;
        String power = null;
        for (int i = 1; i < args.length; i++) {
            String raw = args[i].replaceAll("^[,.;:\"']+|[,.;:\"']+$", "");
            if (raw.isEmpty() || raw.equals("-")) continue;
            String a = raw.toLowerCase(Locale.ROOT);
            if (a.equals("name") || a.equals("named") || a.equals("called")) continue;
            if (net.bigyous.gptgodmc.god.Power.parse(a) != null) power = a;
            else if (a.equals("random") || Persona.archetypes().containsKey(a)) archetype = a;
            else if (name == null) name = raw;
            else {
                msg(sender, "The name must be one word (\"" + name + "\" or \"" + raw + "\"?). Archetypes: "
                        + String.join(", ", Persona.archetypes().keySet()) + "; powers: lesser, greater, supreme.");
                return null;
            }
        }
        Realm made;
        try {
            made = Realm.create(Realm.current(), name, archetype == null ? "random" : archetype, power,
                    sender.getName());
        } catch (IllegalArgumentException e) {
            msg(sender, e.getMessage());
            return null;
        }
        Journal.add(Journal.Kind.INFO, "A new god was created beside this one: " + made.id + " (by " + sender.getName()
                + ")");
        God.refreshPresence();
        net.bigyous.gptgodmc.god.TabList.refreshAll();
        msg(sender, "A new god comes into being: " + (name == null ? "it will name itself" : name) + " (@" + made.id
                + ", " + made.power().label.toLowerCase(Locale.ROOT) + (archetype == null ? "" : ", " + archetype)
                + "). It is born when it first awakens among mortals. It thinks on its own, so it costs about as much "
                + "to run as the first god.");
        return made;
    }

    /** /god remove <name> confirm — takes a created god away (its folder is kept in god/removed/). */
    public static void remove(CommandSender sender, String[] args) {
        if (!mayShapePantheon(sender)) return;
        if (args.length < 2) {
            msg(sender, "Usage: /god remove <name> confirm");
            return;
        }
        Realm r = Realm.byName(args[1]);
        if (r == null) {
            msg(sender, "No god is called " + args[1] + ". Gods: " + godNames() + ".");
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            msg(sender, "This removes " + r.displayName() + " for good: its thoughts stop, its body vanishes and its "
                    + "commands are dropped. Its memory is moved to plugins/gptgodmc/god/removed/, not deleted. "
                    + "Type /god remove " + args[1] + " confirm");
            return;
        }
        String name = r.displayName();
        List<Realm> peers = r.peers();
        String problem = Realm.remove(r);
        if (problem != null) {
            msg(sender, problem);
            return;
        }
        SELECTED.values().removeIf(id -> id.equalsIgnoreCase(r.id));
        for (Realm peer : peers) {
            peer.run(() -> EventLogger.addLoggable(new net.bigyous.gptgodmc.loggables.DivineActLoggable(
                    name + ", a god of this world, is no more.", false, null)));
        }
        net.bigyous.gptgodmc.god.TabList.refreshAll();
        msg(sender, name + " is no more.");
    }

    private static void list(CommandSender sender) {
        for (Realm r : Realm.all()) {
            boolean thinking = r.get(() -> God.brain().isThinking());
            String state = !r.get(God::isAwake) ? "asleep" : r.get(God::isPaused) ? "paused"
                    : thinking ? "thinking" : "awake";
            Persona p = r.persona();
            msg(sender, "@" + r.id + " — " + (p == null ? "unborn" : p.title() + " (" + p.archetype + ")") + " · "
                    + r.power().id + " · " + state + " · " + r.players().size() + " mortal(s)"
                    + (r.isHost() ? "" : " · shares the worlds of " + r.host().displayName()));
        }
    }

    private static void select(CommandSender sender, String name) {
        if (name.isBlank() || name.equalsIgnoreCase("none")) {
            SELECTED.remove(sender.getName());
            msg(sender, "No god selected: /god commands go to the god of your world.");
            return;
        }
        Realm r = Realm.byName(name);
        if (r == null) {
            msg(sender, "No god is called " + name + ". Gods: " + godNames() + ".");
            return;
        }
        SELECTED.put(sender.getName(), r.id);
        msg(sender, "Your /god commands now go to " + r.displayName() + " (/god select none to stop).");
    }

    private void setConfig(CommandSender sender, String path, String value, String label) {
        if (value.isBlank()) {
            msg(sender, label + ": " + GPTGOD.get().getConfig().getString(path));
            return;
        }
        GPTGOD.get().getConfig().set(path, value);
        GPTGOD.get().saveConfig();
        msg(sender, label + " set to " + value + ".");
    }

    /** Creates a new god of an archetype (or "random", or lists them). Shared with the God Manager. */
    public static void rebirth(CommandSender sender, String archetype) {
        if (archetype.isBlank() || archetype.equalsIgnoreCase("list")) {
            msg(sender, "Archetypes: " + String.join(", ", Persona.archetypes().keySet())
                    + ". Use /god persona <archetype> (or random) to create a new god.");
            return;
        }
        if (God.brain() == null) return;
        if (!archetype.equalsIgnoreCase("random")) {
            if (!Persona.archetypes().containsKey(archetype.toLowerCase(Locale.ROOT))) {
                msg(sender, "Unknown archetype. Known: " + String.join(", ", Persona.archetypes().keySet()));
                return;
            }
            // Remember the choice so the same kind of god returns after a restart.
            Realm me = Realm.current();
            if (me.isPrimary()) {
                GPTGOD.get().getConfig().set("personality.archetype", archetype.toLowerCase(Locale.ROOT));
                GPTGOD.get().saveConfig();
            } else {
                me.setArchetype(archetype.toLowerCase(Locale.ROOT));
            }
        }
        msg(sender, "A new god is being born (" + archetype + ")…");
        God.brain().submit(() -> {
            Persona p = Persona.rebirth(archetype);
            Memory.reset();
            God.vessel().refreshNameplateLater();
            Bukkit.getScheduler().runTask(GPTGOD.get(), () -> msg(sender, "Born: " + p.title() + " (" + p.archetype
                    + "). Its memory starts fresh."));
        });
    }

    private void vessel(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        String target = args.length > 2 ? args[2] : (sender instanceof Player p ? p.getName() : null);
        switch (action) {
            case "summon", "tp", "go" -> {
                if (target == null) {
                    msg(sender, "Usage: /god vessel summon <player|x y z>");
                    return;
                }
                runTool(sender, "vessel_go", "{\"target\":\"" + target + "\",\"mode\":\"teleport\"}");
            }
            case "follow" -> runTool(sender, "vessel_go", "{\"target\":\"" + target + "\",\"mode\":\"follow\"}");
            case "fly" -> {
                if (target == null) {
                    msg(sender, "Usage: /god vessel fly <player|x y z>");
                    return;
                }
                runTool(sender, "vessel_go", "{\"target\":\"" + target + "\",\"mode\":\"fly\"}");
            }
            case "rise", "altitude" -> {
                int blocks;
                try {
                    blocks = Integer.parseInt(args.length > 2 ? args[2] : "");
                } catch (NumberFormatException e) {
                    msg(sender, "Usage: /god vessel rise <blocks 0-" + Vessel.MAX_ALTITUDE + ">");
                    return;
                }
                runTool(sender, "vessel_go", "{\"target\":\"vessel\",\"mode\":\"fly\",\"altitude\":" + blocks + "}");
            }
            case "mood" -> {
                String mood = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
                if (VesselMood.parse(mood) == null) {
                    msg(sender, "Usage: /god vessel mood <" + String.join("|",
                            Arrays.stream(VesselMood.values()).map(VesselMood::id).toList()) + ">");
                    return;
                }
                runTool(sender, "vessel_mood", "{\"mood\":\"" + VesselMood.parse(mood).id() + "\"}");
            }
            case "dismiss", "vanish" -> runTool(sender, "vessel_vanish", "{}");
            case "gesture" -> runTool(sender, "vessel_gesture", "{\"gesture\":\"" + (target == null ? "raise_arms"
                    : target) + "\"}");
            case "look" -> {
                String look = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
                if (!look.equals("auto") && !VesselLook.NAMES.contains(look)) {
                    msg(sender, "Usage: /god vessel look <" + String.join("|", VesselLook.NAMES) + "|auto>");
                    return;
                }
                GPTGOD.get().getConfig().set("vessel.look", look);
                GPTGOD.get().saveConfig();
                God.vessel().restyle();
                msg(sender, "The vessel now wears the " + look + " look.");
            }
            default -> msg(sender, God.vessel().describe());
        }
    }

    private void view(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "Usage: /god view <eyes|vessel|overhead|panorama|orbit> [target] [xray]");
            return;
        }
        String target = args.length > 2 ? args[2] : (sender instanceof Player p ? p.getName() : "vessel");
        boolean xray = Arrays.asList(args).contains("xray");
        Bukkit.getScheduler().runTaskAsynchronously(GPTGOD.get(), Realm.bound(() -> {
            try {
                Vision.Result r = Vision.render(new Vision.Request(Vision.View.parse(args[1]), target, xray, 0, "auto"));
                Path dir = GPTGOD.get().getDataFolder().toPath().resolve("views");
                Files.createDirectories(dir);
                Path file = dir.resolve(new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + "-"
                        + args[1].toLowerCase(Locale.ROOT) + ".png");
                Files.write(file, r.png());
                Bukkit.getScheduler().runTask(GPTGOD.get(), () -> msg(sender, "Saved " + file + " — " + r.caption()));
            } catch (ToolException e) {
                Bukkit.getScheduler().runTask(GPTGOD.get(), () -> msg(sender, e.getMessage()));
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(GPTGOD.get(), () -> msg(sender, "Render failed: " + e));
            }
        }));
    }

    private void tool(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "Usage: /god tool <name> [json args]");
            return;
        }
        String json = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "{}";
        runTool(sender, args[1], json);
    }

    private static void runTool(CommandSender sender, String name, String json) {
        JsonObject input;
        try {
            input = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            msg(sender, "Arguments must be a JSON object, e.g. {\"player\":\"Steve\"}");
            return;
        }
        runPower(sender, name, input);
    }

    /** Runs one of the god's powers for an operator and tells them what happened. Shared with the God Manager. */
    public static void runPower(CommandSender sender, String name, JsonObject input) {
        GodTool tool = ToolRegistry.get(name);
        if (tool == null) {
            msg(sender, "No power named " + name + ". See /god tools.");
            return;
        }
        String source = "operator command";
        if (tool.mainThread()) {
            msg(sender, ToolRegistry.execute(name, input, source).act());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(GPTGOD.get(), Realm.bound(() -> {
                String act = ToolRegistry.execute(name, input, source).act();
                Bukkit.getScheduler().runTask(GPTGOD.get(), () -> msg(sender, act));
            }));
        }
    }

    /** /god command <text> | /god command cancel <n|all> */
    private void edict(CommandSender sender, String[] args) {
        if (!Edicts.mayCommand(sender)) {
            msg(sender, "Only operators and the console may give the god binding commands.");
            return;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("cancel")
                && (args[2].equalsIgnoreCase("all") || args[2].matches("#?\\d+"))) {
            int n = args[2].equalsIgnoreCase("all") ? 0 : parseInt(args[2].replace("#", ""), -1);
            List<Edicts.Edict> gone = n < 0 ? List.of() : Edicts.cancel(n);
            msg(sender, gone.isEmpty() ? (n == 0 ? "No commands were pending." : "No pending command #" + n + ".")
                    : "Cancelled " + String.join(", ", gone.stream().map(e -> "#" + e.number).toList()) + ".");
            return;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("cancel")) {
            msg(sender, "Usage: /god command cancel <number|all>");
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (text.isEmpty()) {
            msg(sender, "Usage: /god command <what the god must do>, e.g. /god command reveal your true form to all "
                    + "players. Unlike /god say, it must obey. See /god commands.");
            return;
        }
        try {
            Edicts.Edict e = Edicts.issue(text, sender, null);
            msg(sender, "Command #" + e.number + " delivered: the god must carry it out." + Edicts.readiness());
        } catch (IllegalArgumentException e) {
            msg(sender, e.getMessage());
        }
    }

    private void listEdicts(CommandSender sender) {
        List<Edicts.Edict> pending = Edicts.book().pending();
        if (pending.isEmpty()) {
            msg(sender, "No binding commands are pending. Give one with /god command <text>.");
            return;
        }
        long now = System.currentTimeMillis();
        int retries = Edicts.retries();
        for (Edicts.Edict e : pending) {
            String status = e.standing() ? "standing, " + Math.max(1, (e.until - now + 59_999) / 60_000) + " min left"
                    : e.thoughts == 0 ? "not yet seen" : "seen in " + e.thoughts + "/" + retries + " thoughts";
            msg(sender, "#" + e.number + " (" + status + ", from " + e.issuer + "): " + shorten(e.text, 200));
        }
        msg(sender, "Withdraw with /god command cancel <n|all>.");
    }

    /** /god manager [item] */
    private void manager(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg(sender, "The God Manager is a menu for players in game. From the console use /god command <text>.");
            return;
        }
        boolean given = p.hasPermission(GodManager.PERMISSION) && GodManager.giveItem(p);
        if (args.length > 1 && args[1].equalsIgnoreCase("item")) {
            msg(sender, given ? "You hold the God Manager: right-click the star to open the menu."
                    : "You already carry the God Manager star: right-click it to open the menu.");
            return;
        }
        if (given) msg(sender, "The God Manager star is yours: right-click it to open this menu again.");
        // Operators with the client mod get its screen; "/god manager chest" still opens the chest menu.
        boolean chest = args.length > 1 && args[1].equalsIgnoreCase("chest");
        if (!chest && net.bigyous.gptgodmc.client.ModGui.open(p)) return;
        GodManager.open(p);
    }

    private void memory(CommandSender s) {
        Memory m = Memory.get();
        String chronicle = m.renderChronicle();
        msg(s, "Chronicle: " + (chronicle.isEmpty() ? "(empty)" : shorten(chronicle, 600)));
        List<String> online = new ArrayList<>();
        Realm.current().players().forEach(p -> online.add(p.getName()));
        String ledger = m.renderLedger(online);
        msg(s, "Ledger:\n" + (ledger.isEmpty() ? "(empty)" : shorten(ledger, 1200)));
        String quests = m.renderQuests();
        msg(s, "Quests:\n" + (quests.isEmpty() ? "(none)" : quests));
        String works = m.renderWorks();
        msg(s, "Works:\n" + (works.isEmpty() ? "(none)" : works));
    }

    /** Token counts and costs never appear in game, not even to operators: console and usage.csv only. */
    private void usage(CommandSender s) {
        if (!(s instanceof ConsoleCommandSender)) {
            msg(s, "Token counts and costs are kept out of the game. Type \"gptgod usage\" in the server console, "
                    + "or read plugins/gptgodmc/usage.csv.");
            return;
        }
        for (String line : UsageLog.summary()) {
            s.sendMessage(line);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            String[] args) {
        if (!alias.equalsIgnoreCase("god") && !alias.equalsIgnoreCase("gptgod")) return List.of();
        if (sender instanceof Player p && !p.hasPermission(GodManager.PERMISSION)) {
            return args.length == 1 && "tutorial".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("tutorial")
                    : List.of();
        }
        if (args.length == 1 && args[0].startsWith("@")) {
            List<String> ids = new ArrayList<>(Realm.all().stream().map(r -> "@" + r.id).toList());
            if (Realm.several()) ids.add("@all");
            return ids.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length > 1 && args[0].startsWith("@")) {
            return onTabComplete(sender, command, alias, Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 1) {
            return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "persona" -> new ArrayList<>(Persona.archetypes().keySet());
                case "vessel" -> List.of("summon", "follow", "fly", "rise", "mood", "gesture", "look", "dismiss");
                case "view" -> List.of("eyes", "vessel", "overhead", "panorama", "orbit");
                case "tool" -> ToolRegistry.all().stream().map(GodTool::name).toList();
                case "effort" -> List.of("auto", "low", "medium", "high");
                case "command" -> List.of("cancel");
                case "manager", "gui", "menu" -> List.of("item", "chest");
                case "power" -> List.of("lesser", "greater", "supreme");
                case "remove", "select" -> Realm.all().stream().filter(r -> !r.isPrimary() || args[0].equals("select"))
                        .map(r -> r.id).toList();
                case "create" -> List.of("-");
                case "godslayer" -> net.bigyous.gptgodmc.god.Godslayer.complete(Arrays.copyOfRange(args, 1, 2));
                case "tutorial", "guide" -> net.bigyous.gptgodmc.guide.Guides.complete(sender,
                        Arrays.copyOfRange(args, 1, 2));
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("command") && args[1].equalsIgnoreCase("cancel")) {
            List<String> options = new ArrayList<>();
            Realm here = sender instanceof Player p ? Realm.of(p.getWorld()) : null;
            (here != null ? here : Realm.primary())
                    .run(() -> Edicts.book().pending().forEach(e -> options.add(String.valueOf(e.number))));
            options.add("all");
            return options.stream().filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("godslayer")) {
            return net.bigyous.gptgodmc.god.Godslayer.complete(Arrays.copyOfRange(args, 1, 3));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("vessel")) {
            List<String> options = switch (args[1].toLowerCase(Locale.ROOT)) {
                case "mood" -> Arrays.stream(VesselMood.values()).map(VesselMood::id).toList();
                case "gesture" -> VesselModel.GESTURES;
                case "look" -> Stream.concat(VesselLook.NAMES.stream(), Stream.of("auto")).toList();
                default -> null;
            };
            if (options != null) {
                return options.stream().filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
            }
        }
        return null;
    }

    private static String[] prepend(String first, String[] rest) {
        String[] out = new String[rest.length + 1];
        out[0] = first;
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String shorten(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private static void msg(CommandSender s, String text) {
        if (net.bigyous.gptgodmc.gui.Feedback.take(s, text)) return; // shown in the client mod's screen
        s.sendMessage(Component.text(text, NamedTextColor.LIGHT_PURPLE));
    }
}
