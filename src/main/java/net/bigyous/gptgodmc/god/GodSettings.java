package net.bigyous.gptgodmc.god;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import net.bigyous.gptgodmc.GPTGOD;

/**
 * Typed, always-live view of config.yml. Values are read on every call so `/god reload`
 * takes effect without restarting the server.
 */
public final class GodSettings {
    public static final String DEFAULT_MODEL = "claude-sonnet-5-5";
    public static final String DEFAULT_MEMORY_MODEL = "claude-haiku-5-5";

    private GodSettings() {
    }

    private static final FileConfiguration DEFAULTS = new org.bukkit.configuration.file.YamlConfiguration();

    /** The live config, or empty defaults when running outside a server (tests). */
    private static FileConfiguration cfg() {
        GPTGOD plugin = GPTGOD.get();
        return plugin == null ? DEFAULTS : plugin.getConfig();
    }

    // ---- Anthropic -------------------------------------------------------------------------

    /** Key from config, falling back to the ANTHROPIC_API_KEY environment variable. */
    public static String apiKey() {
        String key = cfg().getString("anthropic.api-key", "");
        if (key != null && !key.isBlank()) {
            return key.trim();
        }
        String env = System.getenv("ANTHROPIC_API_KEY");
        return env == null ? "" : env.trim();
    }

    public static String apiKeySource() {
        if (!Llm.anthropic()) return Llm.apiKeySource();
        String key = cfg().getString("anthropic.api-key", "");
        if (key != null && !key.isBlank()) {
            return "config.yml";
        }
        String env = System.getenv("ANTHROPIC_API_KEY");
        return env != null && !env.isBlank() ? "ANTHROPIC_API_KEY environment variable" : "none";
    }

    /** The model of the god now bound: its own override (God Manager, /god model), else anthropic.model. */
    public static String model() {
        String own = own(Field.MODEL);
        // A god's own model counts only while its provider is the one in use (see Llm.fits).
        return own != null && Llm.fits(own) ? own : configuredModel();
    }

    /** anthropic.model (or llm.model for another provider), ignoring any god's override. */
    public static String configuredModel() {
        if (!Llm.anthropic()) return Llm.configuredModel();
        return nonBlank(cfg().getString("anthropic.model"), DEFAULT_MODEL);
    }

    public static String memoryModel() {
        if (!Llm.anthropic()) return Llm.memoryModel();
        return nonBlank(cfg().getString("anthropic.memory-model"), DEFAULT_MEMORY_MODEL);
    }

    /** low (default: fastest replies) | medium | high (xhigh/max accepted but slow and expensive). */
    public static String effort() {
        String own = own(Field.EFFORT);
        return own != null ? own.toLowerCase(Locale.ROOT) : configuredEffort();
    }

    /** anthropic.effort, ignoring any god's override. */
    public static String configuredEffort() {
        return nonBlank(cfg().getString("anthropic.effort"), "auto").toLowerCase(Locale.ROOT);
    }

    private enum Field {
        MODEL, EFFORT, VOICE, EFFECT
    }

    /** The bound god's own setting for a field, or null to use config.yml. Never throws. */
    private static String own(Field f) {
        try {
            GodProfile p = Realm.current().profile();
            return GodProfile.blankToNull(switch (f) {
                case MODEL -> p.model;
                case EFFORT -> p.effort;
                case VOICE -> p.voice;
                case EFFECT -> p.effect;
            });
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- Several gods in one world (pantheon:) -------------------------------------------------

    /** How many gods may share one set of worlds, the first god included. */
    public static int pantheonMaxGods() {
        return Math.max(1, Math.min(12, cfg().getInt("pantheon.max-gods", 4)));
    }

    /** The power of a god made with /god create when none is given: lesser | greater | supreme. */
    public static String pantheonDefaultPower() {
        return nonBlank(cfg().getString("pantheon.default-power"), "greater");
    }

    /** At most this many gods think at the same moment (prayers to a god are never held back). */
    public static int pantheonMaxConcurrent() {
        return Math.max(1, cfg().getInt("pantheon.max-concurrent-thoughts", 2));
    }

    /** Seconds between two gods starting an unprompted thought. */
    public static int pantheonStaggerSeconds() {
        return Math.max(0, cfg().getInt("pantheon.stagger-seconds", 5));
    }

    /** Thoughts in a row a god may spend answering only other gods before it waits for mortals. */
    public static int pantheonDivineExchanges() {
        return Math.max(0, cfg().getInt("pantheon.divine-exchanges", 2));
    }

    /** Most a god may spend per hour (USD, from the price table); 0 = no limit. Never shown in game. */
    public static double pantheonBudgetPerGod() {
        return Math.max(0, cfg().getDouble("pantheon.budget.usd-per-hour-per-god", 0));
    }

    /** Kokoro voices handed to new gods, skipping ones their peers already use. */
    public static List<String> pantheonVoices() {
        List<String> v = cfg().getStringList("pantheon.voices");
        return v.isEmpty() ? List.of("bm_george", "am_fenrir", "bm_lewis", "am_onyx", "am_michael") : v;
    }

    public static long maxOutputTokens() {
        return Math.max(1024, cfg().getLong("anthropic.max-output-tokens", 8000));
    }

    public static boolean refusalFallback() {
        return cfg().getBoolean("anthropic.refusal-fallback", true);
    }

    public static String baseUrl() {
        return cfg().getString("anthropic.base-url", "");
    }

    // ---- God identity ----------------------------------------------------------------------

    public static String configuredName() {
        return cfg().getString("god.name", "");
    }

    public static String personaExtra() {
        return cfg().getString("god.persona", "");
    }

    public static String language() {
        return nonBlank(cfg().getString("god.language"), "en");
    }

    public static List<String> potentialBehaviors() {
        return cfg().getStringList("personality.potential-behaviors");
    }

    public static int likedCount() {
        return cfg().getInt("personality.liked", 3);
    }

    public static int dislikedCount() {
        return cfg().getInt("personality.disliked", 3);
    }

    public static String briefing() {
        return cfg().getString("personality.briefing", "");
    }

    // ---- Brain -----------------------------------------------------------------------------

    public static boolean brainEnabled() {
        return cfg().getBoolean("brain.enabled", true);
    }

    /** live | external | off */
    public static String brainMode() {
        return brainMode(cfg().getString("brain.mode"));
    }

    /**
     * The mode a config value means. YAML reads a bare {@code off} (or no) as the boolean false and
     * {@code on} (or yes) as true, and Bukkit hands those back as "false" / "true": they mean off and live.
     */
    static String brainMode(String raw) {
        String v = nonBlank(raw, "live").toLowerCase(Locale.ROOT);
        return switch (v) {
            case "false", "no" -> "off";
            case "true", "yes", "on" -> "live";
            default -> v;
        };
    }

    /**
     * After the config is read: a {@code brain.mode} that YAML took for a boolean becomes the word again
     * (in memory), so the plugin's next save writes {@code mode: 'off'} and not {@code mode: false}.
     * Returns true when it changed something.
     */
    public static boolean keepModeWord(FileConfiguration c) {
        if (!c.isSet("brain.mode") || !c.isBoolean("brain.mode")) return false;
        c.set("brain.mode", brainMode(String.valueOf(c.getBoolean("brain.mode"))));
        return true;
    }

    public static int heartbeatSeconds() {
        return Math.max(5, cfg().getInt("brain.heartbeat-seconds", 40));
    }

    public static int minGapSeconds() {
        return Math.max(1, cfg().getInt("brain.min-gap-seconds", 8));
    }

    public static int idleSeconds() {
        return Math.max(0, cfg().getInt("brain.idle-seconds", 270));
    }

    /** Whether the god may search the web (Anthropic's web search server tool, $10 per 1,000 searches). */
    public static boolean webSearch() {
        return cfg().getBoolean("brain.web-search", true);
    }

    /** At most this many searches in one request. */
    public static int webSearchMaxUses() {
        return Math.max(1, Math.min(10, cfg().getInt("brain.web-search-max-uses", 3)));
    }

    /** operator.revere-ops: server operators are marked in perception and treated as above other mortals. */
    public static boolean revereOps() {
        return cfg().getBoolean("operator.revere-ops", false);
    }

    /** brain.always-answer: a mortal who speaks to the god always gets words back, even a refusal. */
    public static boolean alwaysAnswer() {
        return cfg().getBoolean("brain.always-answer", true);
    }

    /** vessel.face-speakers: the vessel turns to face whoever addresses the god. */
    public static boolean faceSpeakers() {
        return cfg().getBoolean("vessel.face-speakers", true);
    }

    /** initiative.level: watch (only watches), semi (pastimes in quiet moments) or free (lives by its whims). */
    public static Initiative.Level initiativeLevel() {
        return Initiative.Level.parse(cfg().getString("initiative.level", "semi"));
    }

    /** initiative.work-per-minute: blocks it may dig, place or plant with vessel_work in a minute. */
    public static int initiativeWorkPerMinute() {
        return Math.max(1, Math.min(60, cfg().getInt("initiative.work-per-minute", 16)));
    }

        /** purpose.enabled: the god keeps goals of its own and sets missions for mortals. */
    public static boolean purposeEnabled() {
        return cfg().getBoolean("purpose.enabled", true);
    }

    public static int purposeMaxGoals() {
        return Math.max(1, Math.min(6, cfg().getInt("purpose.max-goals", 3)));
    }

    public static boolean ecologyEnabled() {
        return cfg().getBoolean("ecology.enabled", true);
    }

    /** Blocks of land sensed around each group of mortals. */
    public static int ecologyRadius() {
        return Math.max(8, Math.min(48, cfg().getInt("ecology.radius", 24)));
    }

    public static long ecologyHalfLifeMs() {
        return Math.max(1, Math.min(240, cfg().getInt("ecology.footprint-half-life-hours", 24))) * 3_600_000L;
    }

    public static boolean ecologyAlerts() {
        return ecologyEnabled() && cfg().getBoolean("ecology.alerts", true);
    }

    /** awareness.sky: the one-line note on the moon and the rain ({@link Sky}). */
    public static boolean awarenessSky() {
        return cfg().getBoolean("awareness.sky", true);
    }

    /** awareness.avatars: how each mortal's skin looks, bare skin included ({@link AvatarWatch}). */
    public static boolean awarenessAvatars() {
        return cfg().getBoolean("awareness.avatars", true);
    }

    /** awareness.memories: an old memory comes back to it in quiet moments ({@link Reminiscence}). */
    public static boolean awarenessMemories() {
        return cfg().getBoolean("awareness.memories", true);
    }

    public static long awarenessMemoryGapMs() {
        return Math.max(5, Math.min(240, cfg().getInt("awareness.memory-gap-minutes", 10))) * 60_000L;
    }

    public static boolean oathsEnabled() {
        return cfg().getBoolean("oaths.enabled", true);
    }

    public static int oathsMaxOpen() {
        return Math.max(1, Math.min(10, cfg().getInt("oaths.max-open", 3)));
    }

    public static long oathsAnswerMs() {
        return Math.max(1, Math.min(120, cfg().getInt("oaths.answer-minutes", 10))) * 60_000L;
    }

    public static boolean mindEnabled() {
        return cfg().getBoolean("mind.enabled", true);
    }

    public static int mindMaxThoughts() {
        return Math.max(3, Math.min(40, cfg().getInt("mind.max-thoughts", 18)));
    }

    /** The most private thoughts shown in one perception. */
    public static int mindRecall() {
        return Math.max(1, Math.min(6, cfg().getInt("mind.recall", 3)));
    }

    /** Minutes between reflections by the memory model; 0 = it never reflects by itself. */
    public static int mindReflectMinutes() {
        return Math.max(0, Math.min(1440, cfg().getInt("mind.reflect-minutes", 30)));
    }

    public static long mindFadeMs() {
        return Math.max(1, Math.min(365, cfg().getInt("mind.fade-days", 7))) * 86_400_000L;
    }

    /** A goal untouched this long is flagged as neglected in the perception. */
    public static int purposeNudgeMinutes() {
        return Math.max(1, Math.min(240, cfg().getInt("purpose.nudge-minutes", 30)));
    }

    public static boolean purposeBar() {
        return cfg().getBoolean("purpose.bossbar", true);
    }

    /** brain.silence-below-favor: with always-answer, mortals at or below this favor may still be ignored. */
    public static int silenceBelowFavor() {
        return Math.max(-11, Math.min(10, cfg().getInt("brain.silence-below-favor", -7)));
    }

    // ---- Apotheosis (raising a mortal to demigod) -----------------------------------------------------

    public static boolean apotheosisEnabled() {
        return cfg().getBoolean("apotheosis.enabled", false);
    }

    public static int apotheosisCooldownHours() {
        return Math.max(1, Math.min(720, cfg().getInt("apotheosis.cooldown-hours", 72)));
    }

    public static int apotheosisMinFavor() {
        return Math.max(-10, Math.min(10, cfg().getInt("apotheosis.min-favor", 8)));
    }

    public static int apotheosisMax() {
        return Math.max(1, Math.min(10, cfg().getInt("apotheosis.max-demigods", 1)));
    }

    /** How long divinity lasts, in minutes; 0 = until the god unmakes them. */
    public static int apotheosisMinutes() {
        return Math.max(0, Math.min(100_000, cfg().getInt("apotheosis.duration-minutes", 0)));
    }

    public static boolean apotheosisFlight() {
        return cfg().getBoolean("apotheosis.flight", true);
    }

    /** vessel.phase-through-walls: the body glides through walls and roofs (off = it respects them). */
    public static boolean vesselPhaseThroughWalls() {
        return cfg().getBoolean("vessel.phase-through-walls", false);
    }

    /** vessel.nameplate: off, several (only when gods share a world) or always. */
    public static String vesselNameplate() {
        String v = nonBlank(cfg().getString("vessel.nameplate"), "several").toLowerCase(Locale.ROOT);
        return v.equals("off") || v.equals("always") ? v : "several";
    }

    /** powers.drop-max-hearts: the most hearts one dropped block may take. */
    public static int dropMaxHearts() {
        return Math.max(1, Math.min(20, cfg().getInt("powers.drop-max-hearts", 8)));
    }

    /** purpose.quest-gap-minutes: the least time between two quests for the same mortal (0 = no limit). */
    public static int questGapMinutes() {
        return Math.max(0, Math.min(240, cfg().getInt("purpose.quest-gap-minutes", 20)));
    }

    public static int maxSteps() {
        return Llm.maxSteps(Math.max(1, Math.min(12, cfg().getInt("brain.max-steps", 6))));
    }

    public static boolean wakeOnAnyChat() {
        return cfg().getBoolean("brain.wake-on-any-chat", false);
    }

    public static List<String> attentionWords() {
        return cfg().getStringList("brain.attention-words");
    }

    public static int perceptionCharBudget() {
        // The lite profile gives events half of its small perception.
        if (Llm.lite()) return Llm.litePerceptionChars() / 2;
        return Math.max(4000, cfg().getInt("brain.perception-char-budget", 24000));
    }

    // ---- Memory: the annals, deeds and dossiers ---------------------------------------------

    /** Keep the complete on-disk log of events and acts (god/annals/*.jsonl). */
    public static boolean annalsEnabled() {
        return cfg().getBoolean("memory.annals", true);
    }

    /** Days of annals to keep (whole months are deleted once they are older); 0 keeps everything. */
    public static int annalsRetentionDays() {
        return Math.max(0, cfg().getInt("memory.annals-retention-days", 90));
    }

    /** Dated deeds kept per mortal before the oldest are folded into their summary. */
    public static int deedsKept() {
        return Math.max(6, Math.min(200, cfg().getInt("memory.deeds-kept", 24)));
    }

    /** Recent deeds shown in a dossier inside the perception (recall shows them all). */
    public static int dossierDeeds() {
        return Math.max(1, Math.min(12, cfg().getInt("memory.dossier-deeds", 5)));
    }

    /** How many full dossiers one perception may carry at most. */
    public static int maxDossiers() {
        return Math.max(1, Math.min(12, cfg().getInt("memory.max-dossiers", 5)));
    }

    /** Hours between two renamings of the god by itself (rename_self). */
    public static int renameCooldownHours() {
        return Math.max(0, cfg().getInt("memory.rename-cooldown-hours", 24));
    }

    /** How long after a prayer or its name the god starts answering (a short pause batches a sentence). */
    public static int reactionDelayMs() {
        return Math.max(0, Math.min(5000, cfg().getInt("brain.reaction-delay-ms", 350)));
    }

    /** Whether mortals get an instant "the god hears you" sign while the answer is being thought up. */
    public static boolean acknowledge() {
        return cfg().getBoolean("brain.acknowledge", true);
    }

    // ---- Powers ----------------------------------------------------------------------------

    public static boolean commandsAllowed() {
        return cfg().getBoolean("powers.commands", true);
    }

    public static List<String> commandBlocklist() {
        return cfg().getStringList("powers.command-blocklist");
    }

    public static int maxBlockChanges() {
        return Math.max(1, cfg().getInt("powers.max-block-changes", 2000));
    }

    public static float maxExplosionPower() {
        return (float) Math.max(0.5, cfg().getDouble("powers.max-explosion-power", 8));
    }

    public static int maxEntitiesPerSpawn() {
        return Math.max(1, cfg().getInt("powers.max-entities-per-spawn", 12));
    }

    public static boolean explosionsBreakBlocks() {
        return cfg().getBoolean("powers.explosions-break-blocks", true);
    }

    public static List<String> forbiddenEntities() {
        return cfg().getStringList("powers.forbidden-entities");
    }

    /** Whether gifts and blessings come with a particle rite (spirals, rings, a chime). */
    public static boolean ritesEnabled() {
        return cfg().getBoolean("powers.rites", true);
    }

    /** Whether the god may use the server owner's custom powers (custom-powers in config.yml). */
    public static boolean customPowersAllowed() {
        return cfg().getBoolean("powers.custom-powers", true);
    }

    /** A god power written by the server owner: commands with placeholders. */
    public record CustomPower(String name, String description, List<String> commands, int cooldownSeconds) {
    }

    /** The enabled custom powers, in config order. */
    public static List<CustomPower> customPowers() {
        return parseCustomPowers(cfg().getConfigurationSection("custom-powers"));
    }

    public static List<CustomPower> parseCustomPowers(ConfigurationSection section) {
        List<CustomPower> out = new ArrayList<>();
        if (section == null) {
            return out;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection p = section.getConfigurationSection(key);
            if (p == null || !p.getBoolean("enabled", true)) {
                continue;
            }
            List<String> commands = new ArrayList<>();
            for (String c : p.getStringList("commands")) {
                if (c != null && !c.isBlank()) {
                    commands.add(c.trim());
                }
            }
            if (commands.isEmpty()) {
                continue;
            }
            out.add(new CustomPower(key.trim().toLowerCase(Locale.ROOT).replace(' ', '_'),
                    nonBlank(p.getString("description"), "(no description)").trim(), List.copyOf(commands),
                    Math.max(0, p.getInt("cooldown-seconds", 0))));
        }
        return out;
    }

    // ---- Vessel ----------------------------------------------------------------------------

    public static boolean vesselEnabled() {
        return cfg().getBoolean("vessel.enabled", true);
    }

    /** The largest the vessel may be, in multiples of a player's height (vessel.max-size). */
    public static double vesselMaxSize() {
        return Math.max(1.0, Math.min(12.0, cfg().getDouble("vessel.max-size", 6.0)));
    }

    /**
     * The vessel's usual size in multiples of a player's height: vessel.size is a name (mortal,
     * lordly, towering, colossal) or a number. Configs from before 0.3 only have vessel.scale
     * (1.0 = 2.5 blocks tall), which is still honored when vessel.size is absent.
     */
    public static double vesselSize() {
        // get(path, null) looks only at the server's own file, not at the bundled defaults.
        Object own = cfg().get("vessel.size", null);
        Double size = own == null ? null
                : net.bigyous.gptgodmc.god.vessel.VesselModel.parseSize(String.valueOf(own));
        Object legacy = cfg().get("vessel.scale", null);
        // 1.2 was the old default, so only a scale the operator changed counts as a choice.
        if (own == null && legacy instanceof Number n && Math.abs(n.doubleValue() - 1.2) > 0.001) {
            size = n.doubleValue() * 2.5 / 1.8;
        }
        if (size == null) size = net.bigyous.gptgodmc.god.vessel.VesselModel.DEFAULT_SIZE;
        return Math.max(0.5, Math.min(vesselMaxSize(), size));
    }

    /** watch | follow-players */
    public static String vesselIdleBehavior() {
        return nonBlank(cfg().getString("vessel.idle-behavior"), "follow-players").toLowerCase(Locale.ROOT);
    }

    /** "auto" (the look's own color) or a hex color like #B24BF3. */
    public static String vesselGlowColor() {
        return nonBlank(cfg().getString("vessel.glow-color"), "auto");
    }

    /** shade | radiant | infernal | void | auto */
    public static String vesselLook() {
        return nonBlank(cfg().getString("vessel.look"), "shade").toLowerCase(Locale.ROOT);
    }

    /** auto | horns | halo | none */
    public static String vesselOrnament() {
        return nonBlank(cfg().getString("vessel.ornament"), "auto").toLowerCase(Locale.ROOT);
    }

    /** custom = the vessel wears the plugin's own textures (a resource pack); vanilla = plain blocks. */
    public static boolean vesselCustomTextures() {
        return !"vanilla".equalsIgnoreCase(nonBlank(cfg().getString("vessel.textures"), "custom").trim());
    }

    /** Port of the little web server that hands out the vessel's resource pack. */
    public static int vesselPackPort() {
        return cfg().getInt("vessel.resource-pack.port", 8770);
    }

    public static String vesselPackBind() {
        return nonBlank(cfg().getString("vessel.resource-pack.bind"), "0.0.0.0").trim();
    }

    /** The host players download from; blank = the address each player connected with. */
    public static String vesselPackPublicHost() {
        String s = cfg().getString("vessel.resource-pack.public-host", "");
        return s == null ? "" : s.trim();
    }

    /** Your own download link for the pack instead of the built-in web server; blank = built-in. */
    public static String vesselPackUrl() {
        String s = cfg().getString("vessel.resource-pack.url", "");
        return s == null ? "" : s.trim();
    }

    /** false = never send it; its files are already inside the server's own resource pack. */
    public static boolean vesselPackSend() {
        return cfg().getBoolean("vessel.resource-pack.send", true);
    }

    public static boolean vesselPackRequired() {
        return cfg().getBoolean("vessel.resource-pack.required", false);
    }

    public static String vesselPackPrompt() {
        return nonBlank(cfg().getString("vessel.resource-pack.prompt"),
                "The god's true appearance (a small texture pack for its body)");
    }

    public static boolean vesselCape() {
        return cfg().getBoolean("vessel.cape", true);
    }

    /** vessel.pondering: its body shows it working while it thinks (poses, particles), not only dots. */
    public static boolean vesselPondering() {
        return cfg().getBoolean("vessel.pondering", true);
    }

    public static boolean vesselWings() {
        return cfg().getBoolean("vessel.wings", true);
    }

    public static boolean vesselOutline() {
        return cfg().getBoolean("vessel.outline", true);
    }

    public static int vesselSpeechRadius() {
        return Math.max(4, cfg().getInt("vessel.speech-radius", 48));
    }

    /** false = the god starts formless and takes a body only when it chooses to (summoned, angered). */
    public static boolean vesselManifestOnWake() {
        return cfg().getBoolean("vessel.manifest-on-wake", false);
    }

    /** vessel.arrival: how it takes form. rift = it steps out of a tear in the air; storm = it condenses out of a particle storm. */
    public static String vesselArrival() {
        String v = nonBlank(cfg().getString("vessel.arrival"), "rift").toLowerCase(Locale.ROOT).trim();
        return v.equals("storm") ? "storm" : "rift";
    }

    /** Whether mortals can hand items to the vessel as offerings. */
    public static boolean offeringsEnabled() {
        return cfg().getBoolean("vessel.offerings", true);
    }

    /** Whether the god may reveal its true form at all. */
    public static boolean trueFormEnabled() {
        return cfg().getBoolean("vessel.true-form.enabled", true);
    }

    /** Whether looking at the true form kills by default. */
    public static boolean trueFormLethal() {
        return trueFormGaze().lethal();
    }

    /**
     * What the true form's gaze does by default (vessel.true-form.gaze: kill, smite or blind; older
     * configs: lethal true/false). The god may choose per reveal, but may only kill where this is kill.
     */
    public static net.bigyous.gptgodmc.god.vessel.TrueForm.Gaze trueFormGaze() {
        var c = cfg();
        Boolean lethal = c.contains("vessel.true-form.lethal", true) ? c.getBoolean("vessel.true-form.lethal") : null;
        String gaze = c.contains("vessel.true-form.gaze", true) || lethal == null
                ? c.getString("vessel.true-form.gaze", "kill") : null;
        return net.bigyous.gptgodmc.god.vessel.TrueForm.Gaze.fromConfig(gaze, lethal);
    }

    /** Whether the true form turns the sky to a storm at midnight for players near it while it lasts. */
    public static boolean trueFormDarkenSky() {
        return cfg().getBoolean("vessel.true-form.darken-sky", true);
    }

    /** The true form's default shape: auto (by the god's nature), random, or a form id. */
    public static String trueFormShape() {
        return nonBlank(cfg().getString("vessel.true-form.form"), "auto").toLowerCase(Locale.ROOT);
    }

    /** How close to dead-on a mortal's gaze must be to count as looking at it. */
    public static double trueFormGazeDegrees() {
        return Math.max(2, Math.min(60, cfg().getDouble("vessel.true-form.gaze-degrees", 14)));
    }

    public static double trueFormRange() {
        return Math.max(8, Math.min(128, cfg().getDouble("vessel.true-form.range", 48)));
    }

    /** Seconds of warning before the gaze starts to kill. */
    public static int trueFormWarningSeconds() {
        return Math.max(1, Math.min(30, cfg().getInt("vessel.true-form.warning-seconds", 4)));
    }

    public static int trueFormMaxSeconds() {
        return Math.max(3, Math.min(120, cfg().getInt("vessel.true-form.max-seconds", 20)));
    }

    public static int trueFormCooldownSeconds() {
        return Math.max(0, cfg().getInt("vessel.true-form.cooldown-seconds", 600));
    }

    // ---- Voice -----------------------------------------------------------------------------

    /** local | server | off */
    public static String transcriptionEngine() {
        return nonBlank(cfg().getString("voice.transcription.engine"), "local").toLowerCase(Locale.ROOT);
    }

    public static String whisperModel() {
        return nonBlank(cfg().getString("voice.transcription.whisper-model"), "base.en");
    }

    public static boolean whisperAutoDownload() {
        return cfg().getBoolean("voice.transcription.auto-download", true);
    }

    public static int whisperThreads() {
        return Math.max(1, cfg().getInt("voice.transcription.threads", 4));
    }

    public static String transcriptionServerUrl() {
        return cfg().getString("voice.transcription.server-url", "http://localhost:8000/v1/audio/transcriptions");
    }

    public static String transcriptionServerKey() {
        return cfg().getString("voice.transcription.server-api-key", "");
    }

    public static String transcriptionServerModel() {
        return nonBlank(cfg().getString("voice.transcription.server-model"), "whisper-1");
    }

    /** kokoro (a local Kokoro server) | openai-compatible | piper | off */
    public static String ttsEngine() {
        return nonBlank(cfg().getString("voice.tts.engine"), "kokoro").toLowerCase(Locale.ROOT);
    }

    /** Tell a player once, in chat, that they need the Simple Voice Chat mod to hear the god. */
    public static boolean voiceHint() {
        return cfg().getBoolean("voice.hint-without-mod", true);
    }

    /** Speak with the built-in Piper when the speech server does not answer. */
    public static boolean ttsFallbackToPiper() {
        return cfg().getBoolean("voice.tts.fallback-to-piper", true);
    }

    public static boolean ttsAutoDownload() {
        return cfg().getBoolean("voice.tts.auto-download", true);
    }

    public static String piperPath() {
        return cfg().getString("voice.tts.piper-path", "");
    }

    /** A Piper voice name like en_US-ryan-medium, or a path to an .onnx file. */
    public static String piperVoice() {
        return nonBlank(cfg().getString("voice.tts.piper-voice"), "en_US-ryan-medium");
    }

    /** auto (follows the god's mood, see {@link #voiceMoodEffects}) | majestic | divine | booming | commanding | demonic | abyssal | ethereal | none */
    public static String voiceEffect() {
        String own = own(Field.EFFECT);
        return own != null ? own.toLowerCase(Locale.ROOT) : configuredVoiceEffect();
    }

    /** voice.tts.effect, ignoring any god's override. */
    public static String configuredVoiceEffect() {
        return nonBlank(cfg().getString("voice.tts.effect"), "auto").toLowerCase(Locale.ROOT);
    }

    /** With effect auto: the effect for each mood (calm, wrathful, ...), from voice.tts.moods. */
    public static java.util.Map<String, String> voiceMoodEffects() {
        java.util.Map<String, String> map = new java.util.HashMap<>(net.bigyous.gptgodmc.voice.VoiceTone.DEFAULT_MOODS);
        ConfigurationSection sec = cfg().getConfigurationSection("voice.tts.moods");
        if (sec != null) {
            for (String mood : sec.getKeys(false)) {
                String effect = sec.getString(mood);
                if (effect != null && !effect.isBlank()) {
                    map.put(mood.toLowerCase(Locale.ROOT), effect.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return map;
    }

    public static double voiceVolume() {
        return Math.max(0.1, Math.min(2.0, cfg().getDouble("voice.tts.volume", 1.0)));
    }

    /** How long a line's text waits for its voice so both arrive together (0 = text at once). */
    /** voice.tts.smooth: hold a line's first words until the rest can be made while they play. */
    public static boolean ttsSmooth() {
        return cfg().getBoolean("voice.tts.smooth", true);
    }

    public static int ttsTextWaitMs() {
        return Math.max(0, Math.min(10_000, cfg().getInt("voice.tts.text-wait-ms", 2500)));
    }

    public static String ttsUrl() {
        return cfg().getString("voice.tts.url", "http://localhost:8880/v1/audio/speech");
    }

    public static String ttsKey() {
        return cfg().getString("voice.tts.api-key", "");
    }

    public static String ttsModel() {
        return nonBlank(cfg().getString("voice.tts.model"), "kokoro");
    }

    /** The voice of the god now bound: its own (God Manager, given at /god create), else voice.tts.voice. */
    public static String ttsVoice() {
        String own = own(Field.VOICE);
        return own != null ? own : configuredTtsVoice();
    }

    /** voice.tts.voice, ignoring any god's override. */
    public static String configuredTtsVoice() {
        return nonBlank(cfg().getString("voice.tts.voice"), "am_michael");
    }

    public static int ttsSampleRate() {
        return cfg().getInt("voice.tts.sample-rate", 24000);
    }

    // ---- Quests / rounds -------------------------------------------------------------------

    public static int questMinutes() {
        return Math.max(1, cfg().getInt("quests.default-minutes", 10));
    }

    public static boolean roundsEnabled() {
        return cfg().getBoolean("rounds.enabled", false);
    }

    public static String startingWorld() {
        return cfg().getString("rounds.starting-world", "");
    }

    public static String gameMode() {
        return nonBlank(cfg().getString("rounds.gamemode"), "SANDBOX").toUpperCase(Locale.ROOT);
    }

    // ---- Bridge / debug --------------------------------------------------------------------

    public static boolean bridgeEnabled() {
        return cfg().getBoolean("bridge.enabled", false);
    }

    public static int bridgePort() {
        return cfg().getInt("bridge.port", 8765);
    }

    /** 127.0.0.1 keeps the bridge local; 0.0.0.0 exposes it on the network (token still required). */
    public static String bridgeBind() {
        return nonBlank(cfg().getString("bridge.bind"), "127.0.0.1");
    }

    public static String bridgeToken() {
        return cfg().getString("bridge.token", "");
    }

    public static boolean logRequests() {
        return cfg().getBoolean("debug.log-requests", false);
    }

    public static String webhookUrl() {
        return cfg().getString("debug.webhook-url", "");
    }

    /** One console line per API request with its tokens and cost. Never shown in game. */
    public static boolean logUsage() {
        return cfg().getBoolean("debug.log-usage", true);
    }

    /** Append every API request to plugins/gptgodmc/usage.csv. */
    public static boolean usageFile() {
        return cfg().getBoolean("debug.usage-file", true);
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    // ---- Legacy migration ------------------------------------------------------------------

    /**
     * Moves settings from the pre-0.2 flat config (OpenAI era) into the new sections.
     * Returns true if anything changed and the file should be saved.
     */
    public static boolean migrateLegacy(FileConfiguration c) {
        boolean changed = false;
        changed |= move(c, "claudeApiKey", "anthropic.api-key", true);
        changed |= move(c, "language", "god.language", false);
        changed |= move(c, "enabled", "brain.enabled", false);
        changed |= move(c, "rate", "brain.heartbeat-seconds", false);
        changed |= move(c, "Rounds", "rounds.enabled", false);
        changed |= move(c, "startingWorld", "rounds.starting-world", false);
        changed |= move(c, "gamemode", "rounds.gamemode", false);
        changed |= move(c, "potentialBehaviors", "personality.potential-behaviors", false);
        changed |= move(c, "likedBehaviors", "personality.liked", false);
        changed |= move(c, "dislikedBehaviors", "personality.disliked", false);
        changed |= move(c, "personalityBriefing", "personality.briefing", true);
        changed |= move(c, "objectiveDecay", "quests.default-minutes", false);
        changed |= move(c, "debugUrl", "debug.webhook-url", true);
        changed |= migrateTrueFormGaze(c);
        keepModeWord(c); // the caller saves either way

        String override = firstNonBlank(c.getString("promptOverride"), c.getString("sandboxPrompt"));
        if (override != null && personaExtraBlank(c)) {
            c.set("god.persona", override);
            changed = true;
        }

        // Old OpenAI text-to-speech keeps working through the OpenAI-compatible TTS engine.
        String openAiKey = c.getString("openAiKey", "");
        // The old `voice: onyx` is a plain value at the path the new voice section uses, so read it first.
        String oldVoice = c.isString("voice") ? c.getString("voice") : "onyx";
        if (c.getBoolean("tts", false) && openAiKey != null && !openAiKey.isBlank() && c.isString("voice")) {
            c.set("voice", null);
            c.set("voice.tts.engine", "openai-compatible");
            c.set("voice.tts.url", "https://api.openai.com/v1/audio/speech");
            c.set("voice.tts.api-key", openAiKey);
            c.set("voice.tts.model", "tts-1");
            c.set("voice.tts.voice", oldVoice);
            changed = true;
        }

        // 0.2.0 wrote the shade look's violet as the outline color; "auto" lets other looks use their own.
        if ("#B24BF3".equalsIgnoreCase(c.getString("vessel.glow-color", ""))) {
            c.set("vessel.glow-color", "auto");
            changed = true;
        }

        for (String dead : List.of("openAiKey", "claudeApiKey", "claude-model-name", "claude-model-token-limit",
                "claude-max-output-tokens", "summary-model-name", "summary-model-token-limit", "gpt-model-name",
                "gpt-model-token-limit", "use-gpt-4", "tts", "voice", "promptOverride", "sandboxPrompt",
                "deathmatchPrompt", "enableDebug", "language", "enabled", "rate", "Rounds", "startingWorld",
                "gamemode", "potentialBehaviors", "likedBehaviors", "dislikedBehaviors", "personalityBriefing",
                "objectiveDecay", "debugUrl")) {
            if (c.isSet(dead) && !c.isConfigurationSection(dead)) {
                c.set(dead, null);
                changed = true;
            }
        }
        return changed;
    }

    // ---- Godslayer ---------------------------------------------------------------------------

    /** Whether the Godslayer can wound gods at all (off: it is only a fine sword). */
    public static boolean godslayerEnabled() {
        return cfg().getBoolean("godslayer.enabled", true);
    }

    public static boolean godslayerCraftable() {
        return cfg().getBoolean("godslayer.craftable", true);
    }

    /** A god's full health. */
    public static double godslayerHealth() {
        return Math.max(1, cfg().getDouble("godslayer.health", 100));
    }

    public static double godslayerDamageMin() {
        return Math.max(0, cfg().getDouble("godslayer.damage-min", 15));
    }

    public static double godslayerDamageMax() {
        return Math.max(godslayerDamageMin(), cfg().getDouble("godslayer.damage-max", 25));
    }

    /** The blade's attack damage against mortals and beasts (a netherite sword does 8). */
    public static double godslayerAttackDamage() {
        return Math.max(1, cfg().getDouble("godslayer.attack-damage", 25));
    }

    /** The Ascended Godslayer kills a god with one blow (off: it wounds like the Godslayer). */
    public static boolean godslayerAscendedInstantKill() {
        return cfg().getBoolean("godslayer.ascended.instant-kill", true);
    }

    /** The Ascended Godslayer's attack damage against mortals and beasts. */
    public static double godslayerAscendedAttackDamage() {
        return Math.max(1, cfg().getDouble("godslayer.ascended.attack-damage", 40));
    }

    public static int godslayerHealDelaySeconds() {
        return Math.max(0, cfg().getInt("godslayer.heal-delay-seconds", 60));
    }

    public static double godslayerHealPerMinute() {
        return Math.max(0, cfg().getDouble("godslayer.heal-per-minute", 10));
    }

    public static boolean godslayerGodReturns() {
        return cfg().getBoolean("godslayer.god-returns", false);
    }

    public static double godslayerReturnMinutes() {
        return Math.max(0, cfg().getDouble("godslayer.return-minutes", 60));
    }

    /** Mortals this close to a wounded god's vessel see its health bar. */
    public static int godslayerBarRadius() {
        return Math.max(8, cfg().getInt("godslayer.health-bar-radius", 64));
    }

    public static boolean godslayerHeldEffects() {
        return cfg().getBoolean("godslayer.held-effects", true);
    }

    /** godslayer.loot, or the default hoard when the list is missing. */
    public static List<String> godslayerLoot() {
        if (!cfg().isList("godslayer.loot")) return GodslayerLoot.DEFAULTS;
        return cfg().getStringList("godslayer.loot");
    }

    private static boolean personaExtraBlank(FileConfiguration c) {
        String current = c.getString("god.persona", "");
        return current == null || current.isBlank();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    /** vessel.true-form.lethal (true/false) becomes vessel.true-form.gaze (kill/blind). */
    static boolean migrateTrueFormGaze(org.bukkit.configuration.Configuration c) {
        if (!c.contains("vessel.true-form.lethal", true)) return false;
        if (!c.contains("vessel.true-form.gaze", true)) {
            c.set("vessel.true-form.gaze", c.getBoolean("vessel.true-form.lethal") ? "kill" : "blind");
        }
        c.set("vessel.true-form.lethal", null);
        return true;
    }

    private static boolean move(FileConfiguration c, String oldKey, String newKey, boolean onlyIfNonBlank) {
        if (!c.isSet(oldKey) || c.isConfigurationSection(oldKey)) {
            return false;
        }
        Object value = c.get(oldKey);
        if (onlyIfNonBlank && (value == null || value.toString().isBlank())) {
            return false;
        }
        c.set(newKey, value);
        return true;
    }
}
