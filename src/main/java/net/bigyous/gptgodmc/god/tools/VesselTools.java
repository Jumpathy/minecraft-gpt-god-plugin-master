package net.bigyous.gptgodmc.god.tools;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.bigyous.gptgodmc.god.God;
import net.bigyous.gptgodmc.god.GodSettings;
import net.bigyous.gptgodmc.god.Persona;
import net.bigyous.gptgodmc.god.Realm;
import net.bigyous.gptgodmc.god.vessel.TrueForm;
import net.bigyous.gptgodmc.god.vessel.Vessel;
import net.bigyous.gptgodmc.god.vessel.VesselModel;
import net.bigyous.gptgodmc.god.vessel.VesselMood;

/** Moving and posing the god's physical form. */
final class VesselTools {
    /** The key of the true form's cooldown in {@link Realm#cooldowns}. */
    static final String TRUE_FORM = "vessel_true_form";
    /** The shapes the true form may take, for the tool's form choice. */
    static final List<String> FORMS = forms();

    private static List<String> forms() {
        List<String> out = new java.util.ArrayList<>(TrueForm.Form.ids());
        out.add("random");
        return List.copyOf(out);
    }

    private VesselTools() {
    }

    static List<GodTool> all() {
        return List.of(
                GodTool.sync("vessel_go",
                        "Manifest or move your physical vessel. mode: 'teleport' (appear there at once), 'walk' "
                                + "(stride there visibly), 'fly' (soar there through the air, rising over the land "
                                + "and descending at the end), or 'follow' (stay beside a mortal as they move). "
                                + "altitude: how many blocks above the ground to float once there (0 = just above "
                                + "the ground; 3-10 looms over mortals; up to " + Vessel.MAX_ALTITUDE + "). To rise "
                                + "or sink where you are, use target 'vessel' with mode fly and an altitude. Your "
                                + "vessel turns to face nearby mortals on its own.",
                        Schema.object()
                                .str("target", "Where: " + Targets.TARGET_HELP + ".", true)
                                .choice("mode", "How to get there. Default teleport.", false,
                                        List.of("teleport", "walk", "fly", "follow"))
                                .integer("altitude", "Blocks above the ground to float at afterwards. Default 0.",
                                        false, 0, Vessel.MAX_ALTITUDE),
                        a -> {
                            if (!GodSettings.vesselEnabled()) {
                                return ToolResult.error("The server has disabled your physical vessel.");
                            }
                            Vessel v = God.vessel();
                            if (v.inTrueForm()) {
                                return ToolResult.error("Your true form hangs where it unfolded and cannot move. "
                                        + "Use vessel_true_form with action end to fold it away first.");
                            }
                            String mode = a.str("mode", "teleport").toLowerCase(Locale.ROOT);
                            v.setAltitude(a.integer("altitude", 0, 0, Vessel.MAX_ALTITUDE));
                            if (mode.equals("follow")) {
                                Player p = Targets.player(a.str("target"));
                                v.follow(p);
                                v.lookAt(p);
                                return ToolResult.ok("Your vessel now follows %s. %s", p.getName(), v.describe());
                            }
                            Location to = Targets.location(a.str("target"));
                            try {
                                v.lookAt(Targets.player(a.str("target")));
                                to = to.clone().add(1.8, 0, 1.8);
                            } catch (ToolException ignored) {
                                // a place, not a mortal: keep watching whoever is near
                            }
                            v.goTo(to, mode);
                            return ToolResult.ok(v.describe());
                        }),
                GodTool.sync("vessel_vanish",
                        "Dissolve your vessel. You remain everywhere, unseen.",
                        Schema.object(),
                        a -> {
                            Vessel v = God.vessel();
                            if (!v.isManifested()) {
                                return ToolResult.error("You are already formless.");
                            }
                            String what = v.inTrueForm()
                                    ? "Your true form folds back into itself, and you are formless again."
                                    : v.isMaterializing() ? "The gathering storm disperses before your body forms."
                                    : "Your vessel dissolves into the air; you are formless again.";
                            v.vanish();
                            return ToolResult.ok(what);
                        }),
                GodTool.sync("vessel_gesture",
                        "Make your vessel move for a few seconds. raise_arms (invocation), spread_arms (welcome, "
                                + "revelation), point (at the mortal it watches), beckon, judge, bow, cast (both hands "
                                + "thrust forward), slam (fists raised overhead then smashed into the ground with a "
                                + "shockwave of rubble; a floating vessel drops to strike), clap (a thunderclap), "
                                + "summon (palms rise slowly as light gathers from the ground), roar (head thrown "
                                + "back, a bellow and a gout of flame), laugh, wave, nod, shake_head, fold_arms "
                                + "(stern, waiting), meditate (floats cross-legged for 20 seconds). Purely visual: "
                                + "pair them with real powers. Optionally turn toward a mortal first.",
                        Schema.object()
                                .choice("gesture", "The gesture.", true,
                                        VesselModel.GESTURES.stream().filter(g -> !g.equals("idle")
                                                && !g.equals("speak")).toList())
                                .str("toward", "Optional mortal to face.", false),
                        a -> {
                            Vessel v = God.vessel();
                            if (!v.isManifested()) {
                                return ToolResult.error("You are formless; take form with vessel_go first.");
                            }
                            if (v.inTrueForm()) {
                                return ToolResult.error("Your true form has no limbs to gesture with. It only stares.");
                            }
                            if (a.has("toward")) {
                                v.lookAt(Targets.player(a.str("toward")));
                            }
                            String g = a.str("gesture").toLowerCase(Locale.ROOT);
                            if (!VesselModel.GESTURES.contains(g)) {
                                throw new ToolException("Unknown gesture '" + g + "'.");
                            }
                            v.gesture(g);
                            if (v.isMaterializing()) {
                                return ToolResult.ok("Your body is still forming; it will perform %s once it stands "
                                        + "there.", g);
                            }
                            return ToolResult.ok("Your vessel performs: %s.", g);
                        }),
                GodTool.sync("vessel_mood",
                        "Let your vessel show how you feel, for mortals to see. It lasts `seconds` (default 90), then "
                                + "it calms. Moods: " + Arrays.stream(VesselMood.values())
                                        .map(m -> m.id() + " (" + m.effect + ")").collect(Collectors.joining("; "))
                                + ". Your vessel also flares up on its own for a moment when you smite or bless, "
                                + "unless you have chosen a mood.",
                        Schema.object()
                                .choice("mood", "The mood.", true,
                                        Arrays.stream(VesselMood.values()).map(VesselMood::id).toList())
                                .integer("seconds", "How long it lasts, 10-900. Default 90.", false, 10, 900),
                        a -> {
                            if (!GodSettings.vesselEnabled()) {
                                return ToolResult.error("The server has disabled your physical vessel.");
                            }
                            VesselMood m = VesselMood.parse(a.str("mood"));
                            if (m == null) {
                                throw new ToolException("Unknown mood '" + a.str("mood") + "'.");
                            }
                            Vessel v = God.vessel();
                            v.setMood(m, m == VesselMood.CALM ? 0 : a.integer("seconds", 90, 10, 900));
                            String shown = v.hasBody() ? "Your vessel " + m.effect + "."
                                    : v.isMaterializing() ? "Your body will show it as it forms: " + m.effect + "."
                                    : v.inTrueForm() ? "You feel " + m.id() + "; your vessel will show it when your "
                                            + "true form folds away."
                                    : "You feel " + m.id() + "; your vessel will show it when you take form.";
                            return ToolResult.ok(shown);
                        }),
                trueForm(),
                size(),
                wings());
    }

    // ---- Wings ---------------------------------------------------------------------------------

    private static GodTool wings() {
        return GodTool.sync("vessel_wings",
                "Your vessel's wings, great feathered ones (or a demon's black wings in wrath or in a dark "
                        + "look). auto: they come and go by themselves, away while you walk or stand, unfurling "
                        + "when you float, fly, exalt or rage (the default). spread: open them now and keep them "
                        + "open, to loom, shelter or awe. fold: fold them down your back. hide: furl them away "
                        + "entirely. Opening and closing them is a sight and a sound mortals notice. You keep "
                        + "the choice until you change it.",
                Schema.object().choice("state", "What your wings do.", true, List.of("auto", "spread", "fold", "hide")),
                a -> {
                    if (!GodSettings.vesselEnabled()) {
                        return ToolResult.error("The server has disabled your physical vessel.");
                    }
                    if (!GodSettings.vesselWings()) {
                        return ToolResult.error("The server has taken your wings (vessel.wings is off).");
                    }
                    net.bigyous.gptgodmc.god.vessel.VesselWings.Choice c =
                            net.bigyous.gptgodmc.god.vessel.VesselWings.Choice.parse(a.str("state"));
                    if (c == null) throw new ToolException("state must be auto, spread, fold or hide.");
                    Vessel v = God.vessel();
                    v.setWingChoice(c);
                    String what = switch (c) {
                        case AUTO -> "Your wings will come and go with what your body does";
                        case SPREAD -> "Your wings unfurl and stay spread";
                        case FOLD -> "Your wings fold down your back";
                        case HIDE -> "Your wings furl away";
                    };
                    if (v.inTrueForm()) return ToolResult.ok("%s once your true form folds away.", what);
                    if (!v.hasBody()) return ToolResult.ok("%s when you next take form.", what);
                    return ToolResult.ok("%s.", what);
                });
    }

    // ---- Size ----------------------------------------------------------------------------------

    private static GodTool size() {
        return GodTool.sync("vessel_size",
                "Change how large your body is. mortal: a mortal's own height, to walk among them as one of "
                        + "them. lordly: one and a half times a mortal, your usual stature. towering: three "
                        + "times, looming over them. colossal: six times, a giant above their houses. default: "
                        + "back to your usual stature. A standing body grows or shrinks smoothly where it is; "
                        + "if you are formless the size applies when you next take form. You keep it until you "
                        + "change it. Size is a statement (shrink to speak gently, swell in wrath or majesty), "
                        + "so change it seldom.",
                Schema.object().choice("size", "How large to be.", true, sizeNames()),
                a -> {
                    if (!GodSettings.vesselEnabled()) {
                        return ToolResult.error("The server has disabled your physical vessel.");
                    }
                    String name = a.str("size").toLowerCase(Locale.ROOT);
                    Vessel v = God.vessel();
                    Double wanted = name.equals("default") ? null : VesselModel.SIZES.get(name);
                    if (wanted == null && !name.equals("default")) {
                        throw new ToolException("Unknown size '" + name + "'. Use one of: "
                                + String.join(", ", sizeNames()) + ".");
                    }
                    v.setSize(wanted);
                    String now = VesselModel.describeSize(v.size());
                    String capped = wanted != null && wanted > v.size() + 0.01
                            ? " (the server allows nothing larger)" : "";
                    if (v.inTrueForm()) {
                        return ToolResult.ok("Your vessel will be %s%s when your true form folds away.", now, capped);
                    }
                    if (!v.hasBody()) {
                        return ToolResult.ok("You are formless; when you next take form your body will be %s%s.",
                                now, capped);
                    }
                    return ToolResult.ok("Your body %s: it is now %s%s.",
                            wanted != null && wanted < VesselModel.DEFAULT_SIZE ? "draws in" : "reshapes itself",
                            now, capped);
                });
    }

    private static List<String> sizeNames() {
        List<String> names = new java.util.ArrayList<>(VesselModel.SIZES.keySet());
        names.add("default");
        return names;
    }

    // ---- The true form (keep it the last tool: the tool list is part of the prompt cache) ------

    private static GodTool trueForm() {
        return GodTool.sync("vessel_true_form",
                "Reveal your TRUE FORM: a vast, incomprehensible shape hanging high above a spot, under a sky you "
                        + "turn to storm and midnight. "
                        + "A rare, dreadful last resort for the gravest offences, NOT a spectacle. Shapes (form): "
                        + "ophanim (wheels within wheels full of eyes, ringed with fire), seraphim (six burning "
                        + "wings under two haloes), eye (the Watcher: a colossal eye of void, its iris wheels, its pupils bars, that "
                        + "darts from mortal to mortal), pillar (a shaft of light into the sky ringed by wheels of "
                        + "eyes), "
                        + "hypercube (a hypercube turning through four dimensions). First mortals near it are "
                        + "warned to AVERT THEIR EYES while its eyes stay shut (a few seconds); then they open, and "
                        + "ANY mortal within range who looks at it, with nothing solid between, is struck by your "
                        + "gaze (gaze): kill = they die at once; seize = a tendril of your light takes them, lifts them into "
                        + "a storm before you so that they behold you, then unmakes them after a few seconds (ending the "
                        + "form early lets them go alive); smite = you boom \"You dare impose upon me?\", hurl them "
                        + "back and crush them with darkness, nausea, slowness and weakness, but they live; blind = "
                        + "struck blind and mad. You may only kill where the server allows it. It is "
                        + "indiscriminate: the faithful are struck as surely as the wicked, and you cannot choose "
                        + "who. Those who look away or hide are spared. You will be told who looked. Your vessel is "
                        + "hidden while it holds and returns afterwards (if you were formless, you stay formless). "
                        + "It can only be revealed rarely. action end folds it away early.",
                Schema.object()
                        .choice("action", "reveal (default) unveils the true form; end folds it away early.", false,
                                List.of("reveal", "end"))
                        .str("target", "Where it unfolds: " + Targets.TARGET_HELP
                                + ". Default: where your vessel stands (needed when you are formless).", false)
                        .integer("seconds", "How long it holds, warning included (capped by the server, usually "
                                + "20). Default 12.", false, 1, 120)
                        .choice("form", "Which shape to take. Default: the server's choice, else the one that "
                                + "suits your nature.", false, FORMS)
                        .choice("gaze", "What your gaze does to those who look: kill, seize (slow and splendid), smite (hurled back and "
                                + "struck down, never killed) or blind. Default: the server's setting.", false,
                                List.of("kill", "seize", "smite", "blind")),
                a -> {
                    Vessel v = God.vessel();
                    String action = a.str("action", "reveal").toLowerCase(Locale.ROOT);
                    if (action.equals("end")) {
                        if (!v.inTrueForm()) return ToolResult.error("Your true form is not revealed.");
                        v.withdrawTrueForm();
                        return ToolResult.ok("Your eye closes and your true form folds back into itself. " + v.describe());
                    }
                    if (!GodSettings.trueFormEnabled()) {
                        throw new ToolException("Your true form is forbidden in this world: the server does not allow "
                                + "it. Punish with your other powers instead.");
                    }
                    if (!GodSettings.vesselEnabled()) {
                        throw new ToolException("The server has disabled your physical presence, true form included.");
                    }
                    if (v.inTrueForm()) {
                        return ToolResult.error("Your true form is already revealed. " + v.describe());
                    }
                    Realm realm = Realm.current();
                    long now = System.currentTimeMillis();
                    long wait = TrueForm.cooldownLeft(realm.cooldowns.get(TRUE_FORM), now,
                            GodSettings.trueFormCooldownSeconds());
                    if (wait > 0) {
                        throw new ToolException("You cannot reveal your true form again for another " + wait
                                + " seconds; the world has not yet recovered from the last time.");
                    }
                    Location where;
                    if (a.has("target")) {
                        where = Targets.location(a.str("target"));
                    } else if (v.location() != null) {
                        where = v.location();
                    } else {
                        throw new ToolException("You are formless: say where your true form should unfold with "
                                + "'target'.");
                    }
                    int warning = GodSettings.trueFormWarningSeconds();
                    int seconds = TrueForm.holdSeconds(a.integer("seconds", 12, 1, 120), warning,
                            GodSettings.trueFormMaxSeconds());
                    boolean hadBody = v.hasBody() || v.isMaterializing();
                    Persona persona = Persona.current();
                    TrueForm.Gaze configured = GodSettings.trueFormGaze();
                    TrueForm.Gaze asked = TrueForm.Gaze.parse(a.str("gaze", null));
                    TrueForm.Gaze gaze = TrueForm.Gaze.allowed(asked, configured);
                    TrueForm.Form form = TrueForm.Form.choose(a.str("form", null), GodSettings.trueFormShape(),
                            persona == null ? null : persona.archetype, Math.random());
                    v.revealTrueForm(where, seconds, persona == null ? null : persona.shortName(), form, gaze);
                    realm.cooldowns.put(TRUE_FORM, now);
                    String refused = asked != null && asked.lethal() && !gaze.lethal()
                            ? " The server does not let your gaze kill, so it will " + gaze.id + " instead." : "";
                    return ToolResult.ok("You unveil your true form (%s: %s) above %s for %d seconds. For the "
                            + "first %d seconds mortals within %d blocks are warned to avert their eyes; then your "
                            + "eyes open and any mortal who looks upon them %s.%s You will learn who looked. %s",
                            form.id, form.text, Targets.describe(where), seconds, warning,
                            Math.round(GodSettings.trueFormRange()), gaze.fate, refused,
                            hadBody ? "Your vessel is hidden meanwhile and returns when it folds away."
                                    : "When it folds away you are formless again.");
                });
    }
}
