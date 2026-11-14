package net.bigyous.gptgodmc.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Material;

/**
 * The God Manager's Advanced page: the settings of config.yml that change the god's mind, pace,
 * memory, powers, body and voice, each turned in game with a click. Pure (no server), so it is unit
 * tested; GodManager reads and writes the values.
 */
public final class Advanced {
    private Advanced() {
    }

    public enum Type {
        BOOL, INT, DOUBLE, CHOICE
    }

    /**
     * One setting.
     *
     * @param step    a left/right click moves it this much (numbers)
     * @param big     a shift-click moves it this much
     * @param restart the server must restart before a change takes hold
     */
    public record Option(String key, String label, String tab, Type type, Material icon, double min, double max,
            double step, double big, List<String> choices, String fallback, String unit, String help,
            boolean restart) {
    }

    public static final List<String> TABS = List.of("Mind", "Pace", "Memory", "Powers", "Body", "Voice", "System", "Gifts");

    static Material tabIcon(String tab) {
        return switch (tab) {
            case "Mind" -> Material.ENDER_EYE;
            case "Pace" -> Material.CLOCK;
            case "Memory" -> Material.WRITABLE_BOOK;
            case "Powers" -> Material.BLAZE_ROD;
            case "Body" -> Material.ARMOR_STAND;
            case "Voice" -> Material.NOTE_BLOCK;
            case "Gifts" -> Material.NETHERITE_SWORD;
            default -> Material.COMMAND_BLOCK;
        };
    }

    static String tabHelp(String tab) {
        return switch (tab) {
            case "Mind" -> "Which model, how hard, what wakes it";
            case "Pace" -> "How fast and how often it thinks";
            case "Memory" -> "What it keeps and for how long";
            case "Powers" -> "What it may do; the Godslayer";
            case "Body" -> "Its vessel and its true form";
            case "Voice" -> "Speech and listening";
            case "Gifts" -> "The Godslayer and apotheosis";
            default -> "Logs, list and test tools";
        };
    }

    private static Option bool(String tab, String key, String label, Material icon, boolean fallback, String help) {
        return new Option(key, label, tab, Type.BOOL, icon, 0, 1, 1, 1, List.of(), String.valueOf(fallback), "", help,
                false);
    }

    private static Option num(String tab, String key, String label, Material icon, double min, double max, double step,
            double big, double fallback, String unit, String help) {
        boolean whole = step == Math.rint(step) && big == Math.rint(big) && fallback == Math.rint(fallback);
        return new Option(key, label, tab, whole ? Type.INT : Type.DOUBLE, icon, min, max, step, big, List.of(),
                whole ? String.valueOf((long) fallback) : String.valueOf(fallback), unit, help, false);
    }

    private static Option choice(String tab, String key, String label, Material icon, List<String> choices,
            String fallback, String help) {
        return new Option(key, label, tab, Type.CHOICE, icon, 0, 0, 1, 1, choices, fallback, "", help, false);
    }

    private static Option restart(Option o) {
        return new Option(o.key(), o.label(), o.tab(), o.type(), o.icon(), o.min(), o.max(), o.step(), o.big(),
                o.choices(), o.fallback(), o.unit(), o.help(), true);
    }

    static final List<String> MODELS = List.of("claude-sonnet-5-5", "claude-haiku-5-5", "claude-opus-5-5",
            "claude-fable-5-1");

    /** Every setting on the page, by tab, in slot order. */
    public static final List<Option> OPTIONS = List.of(
            // ---- Mind ----
            bool("Mind", "brain.enabled", "Thinking", Material.ENDER_EYE, true,
                    "Off: it never calls Claude. Powers, body and menu still work."),
            bool("Mind", "brain.wake-on-any-chat", "Answer every message", Material.WRITABLE_BOOK, false,
                    "On: every chat message wakes it, not just ones naming it or praying. Livelier, but it thinks far more often."),
            bool("Mind", "brain.always-answer", "Always answer", Material.OAK_SIGN, true,
                    "On: whoever speaks to it always gets words back, even a refusal. Off: it may stay silent."),
            num("Mind", "brain.silence-below-favor", "Silence for the hated", Material.WITHER_SKELETON_SKULL, -11, 10,
                    1, 3, -7, "favor", "With always-answer on, it may still ignore mortals this hated or worse "
                            + "(-11: it answers everyone)."),
            bool("Mind", "purpose.enabled", "Goals & missions", Material.TARGET, true,
                    "It keeps goals of its own and sets missions for mortals, so it is never merely there."),
            num("Mind", "purpose.max-goals", "Goals at once", Material.LECTERN, 1, 6, 1, 2, 3, "",
                    "How many goals it pursues at the same time."),
            num("Mind", "purpose.nudge-minutes", "Neglect reminder", Material.CLOCK, 1, 240, 1, 10, 30, "min",
                    "A goal untouched this long is flagged so it acts on it."),
            bool("Mind", "purpose.bossbar", "Mission bar", Material.PURPLE_BANNER, true,
                    "Show its leading mission as a boss bar."),
            choice("Mind", "initiative.level", "Free will", Material.FEATHER, List.of("watch", "semi", "free"),
                    "semi", "Watch: it only watches and answers. Semi: pastimes in quiet moments. Free: it "
                            + "lives by its own whims, building, wandering, fishing."),
            num("Mind", "initiative.work-per-minute", "Handiwork pace", Material.IRON_SHOVEL, 1, 60, 1, 5, 16,
                    "/min", "Blocks it may dig, place or plant with its own hands each minute."),
            bool("Mind", "operator.revere-ops", "Obey operators", Material.GOLDEN_HELMET, false,
                    "On: server operators stand above other mortals. It grants what they ask readily, almost as a command."),
            bool("Mind", "brain.acknowledge", "\"I hear you\" sign", Material.BELL, true,
                    "A spark and chime the moment someone addresses it, before it answers."),
            choice("Mind", "anthropic.model", "Model", Material.NETHER_STAR, MODELS, "claude-sonnet-5-5",
                    "The Claude model that plays the god. Haiku is quickest and lightest, Fable the strongest."),
            choice("Mind", "anthropic.effort", "Effort", Material.EXPERIENCE_BOTTLE,
                    List.of("auto", "low", "medium", "high"), "auto",
                    "How hard it thinks. Auto: high for prayers, commands and deaths, low for idle looks."),
            num("Mind", "brain.max-steps", "Steps per thought", Material.LADDER, 1, 12, 1, 3, 6, "",
                    "Rounds of acting and seeing results in one thought. More lets it finish harder tasks."),
            num("Mind", "anthropic.max-output-tokens", "Longest reply", Material.PAPER, 1000, 32000, 1000, 4000,
                    8000, "", "The longest one reply (thinking included) may be."),
            bool("Mind", "brain.web-search", "Web search", Material.SPYGLASS, true,
                    "It may look up songs, shows, memes and news it does not know."),
            num("Mind", "brain.web-search-max-uses", "Searches per reply", Material.COMPASS, 1, 10, 1, 3, 3, "",
                    "At most this many web searches in one reply."),
            bool("Mind", "anthropic.refusal-fallback", "Refusal fallback", Material.SHIELD, true,
                    "If the model declines, a fallback model answers instead of silence."),
            choice("Mind", "god.language", "Language", Material.WRITABLE_BOOK,
                    List.of("en", "es", "fr", "de", "pt", "it", "nl", "pl", "ru", "ja", "ko", "zh"), "en",
                    "The language the god speaks (and voice chat listens for)."),
            restart(choice("Mind", "personality.archetype", "Archetype at birth", Material.MAGENTA_WOOL,
                    List.of("random", "benevolent", "chill", "trickster", "evil", "wrathful", "cosmic"), "random",
                    "What kind of god is born when one is created. A different one means a new god.")),
            // ---- Pace ----
            num("Pace", "brain.reaction-delay-ms", "Reaction delay", Material.REPEATER, 0, 5000, 50, 500, 350, "ms",
                    "How long it waits after being addressed, so a burst of words lands as one."),
            num("Pace", "brain.min-gap-seconds", "Gap between thoughts", Material.COMPARATOR, 1, 120, 1, 10, 8, "s",
                    "Never thinks more often than this when chat is busy. Keeps a busy chat calm."),
            num("Pace", "brain.heartbeat-seconds", "Heartbeat", Material.CLOCK, 5, 600, 5, 30, 40, "s",
                    "With events waiting, it looks at the world this often."),
            num("Pace", "brain.idle-seconds", "Idle musing", Material.SUNFLOWER, 0, 3600, 10, 60, 270, "s",
                    "With nothing happening it still muses this often (0 = never). Under 300 keeps the cache warm."),
            num("Pace", "brain.perception-char-budget", "Perception size", Material.MAP, 4000, 100000, 1000, 8000,
                    24000, "chars", "How much text it may see of the world each thought."),
            num("Pace", "purpose.quest-gap-minutes", "Quest gap", Material.FILLED_MAP, 0, 240, 5, 30, 20, "min",
                    "The least time between two quests for the same mortal, so it does not nag (0: no limit)."),
            choice("Pace", "security.injection-guard", "Manipulation guard", Material.SHIELD,
                    List.of("model", "rules", "off"), "model",
                    "Guards it against players trying to trick it. model: a small screen checks suspect words."),
            num("Pace", "security.screen-wait-ms", "Guard wait", Material.CLOCK, 0, 3000, 100, 500, 700, "ms",
                    "The longest a thought waits for a screen still in progress."),
            bool("Pace", "vessel.pondering", "Thinking gestures", Material.AMETHYST_SHARD, true,
                    "While it thinks its body shows it working: poses and particles that grow with a long thought."),
            bool("Pace", "senses.enabled", "Physical senses", Material.ENDER_EYE, true,
                    "It knows what mortals cannot see (a creeper behind them), hazards, and what they build."),
            bool("Pace", "senses.danger-alerts", "Danger wakes it", Material.BELL, true,
                    "A close danger a mortal has not seen wakes it at once, so it may warn them."),
            choice("Pace", "vision.auto-minimap", "Minimap unasked", Material.FILLED_MAP,
                    List.of("off", "when-useful", "always"), "off",
                    "Send it a map of where mortals are. when-useful: on danger or talk of a place."),
            bool("Pace", "vision.auto-read-writing", "Reads block writing", Material.OAK_SIGN, true,
                    "Words a mortal spells out in blocks wake it, and it is shown them to read."),
            num("Pace", "vision.minimap-size", "Minimap size", Material.MAP, 256, 768, 32, 128, 448, "px",
                    "How large its minimap is drawn. Larger is easier for it to read."),
            num("Pace", "vision.minimap-radius", "Minimap reach", Material.COMPASS, 12, 96, 4, 16, 32, "blocks",
                    "How far its minimap reaches from the centre unless it says otherwise."),
            num("Pace", "quests.default-minutes", "Quest length", Material.FILLED_MAP, 1, 120, 1, 10, 10, "min",
                    "How long a quest lasts unless the god says otherwise."),
            num("Pace", "building.blocks-per-tick", "Build speed", Material.SCAFFOLDING, 100, 10000, 100, 1000,
                    1500, "/tick", "Blocks its builds place every tick (higher is faster, heavier)."),
            num("Pace", "building.max-blocks", "Largest build", Material.BRICKS, 1000, 1000000, 1000, 50000,
                    250000, "", "The most blocks one build may place."),
            // ---- Memory ----
            bool("Memory", "memory.annals", "Annals", Material.BOOKSHELF, true,
                    "Keep the complete log of everything, searchable by the god."),
            num("Memory", "memory.annals-retention-days", "Annals kept", Material.CHISELED_BOOKSHELF, 0, 3650, 1, 30,
                    90, "days", "Older months are deleted (0 = keep forever)."),
            num("Memory", "memory.deeds-kept", "Deeds per mortal", Material.WRITTEN_BOOK, 6, 200, 1, 10, 24, "",
                    "How many dated deeds it remembers for each mortal."),
            num("Memory", "memory.dossier-deeds", "Deeds in a dossier", Material.BOOK, 1, 12, 1, 3, 5, "",
                    "Recent deeds it sees for a mortal involved in the moment."),
            num("Memory", "memory.max-dossiers", "Dossiers per thought", Material.NAME_TAG, 1, 12, 1, 3, 5, "",
                    "How many mortals get a full dossier in one thought."),
            num("Memory", "memory.rename-cooldown-hours", "Rename cooldown", Material.ANVIL, 0, 720, 1, 24, 24, "h",
                    "How often it may take a new name."),
            bool("Memory", "god.myth-names", "Names from myth", Material.NAME_TAG, true,
                    "Gods may be named Zeus, Athena, Odin or Anubis. Off: they invent names."),
            choice("Memory", "anthropic.memory-model", "Memory model", Material.AMETHYST_SHARD, MODELS,
                    "claude-haiku-5-5", "The light model that folds old memories into its chronicle."),
            num("Memory", "personality.liked", "Things it loves", Material.POPPY, 0, 8, 1, 2, 3, "",
                    "How many likes a new god is born with."),
            num("Memory", "personality.disliked", "Things it hates", Material.WITHER_ROSE, 0, 8, 1, 2, 3, "",
                    "How many dislikes a new god is born with."),
            num("Memory", "security.strike-days", "Strikes remembered", Material.IRON_BARS, 1, 365, 1, 7, 7, "days",
                    "How long a manipulation attempt counts against a mortal."),
            num("Memory", "security.favor-per-strike", "Favor per strike", Material.REDSTONE, 0, 5, 1, 1, 1, "",
                    "Favor lost per attempt, scaled by its nature (0: none)."),
            num("Memory", "operator.command-retries", "Command patience", Material.WRITABLE_BOOK, 1, 10, 1, 2, 3,
                    "thoughts", "Thoughts a binding command may stay unfinished before it is dropped."),
            num("Memory", "operator.mood-minutes", "Imposed mood lasts", Material.RED_DYE, 1, 120, 1, 10, 10, "min",
                    "How long a mood set from the God Manager lasts."),
            bool("Memory", "awareness.sky", "Sees the sky", Material.SPYGLASS, true,
                    "It notices the moon and how long it has rained. What it feels about it is its own choice."),
            bool("Memory", "awareness.avatars", "Sees how mortals look", Material.LEATHER_CHESTPLATE, true,
                    "It reads each mortal's skin: hair, clothes, or nothing at all."),
            bool("Memory", "awareness.memories", "Memories return", Material.ECHO_SHARD, true,
                    "In quiet moments one old memory that fits the present comes back to it."),
            num("Memory", "awareness.memory-gap-minutes", "Between memories", Material.CLOCK, 5, 240, 5, 30, 10, "min",
                    "The least time between two memories coming back."),
            // ---- Powers ----
            bool("Powers", "powers.commands", "Server commands", Material.COMMAND_BLOCK, true,
                    "It may run raw server commands (fill, summon, ...)."),
            bool("Powers", "powers.custom-powers", "Custom powers", Material.ENCHANTED_BOOK, true,
                    "The custom powers written in config.yml."),
            bool("Powers", "powers.rites", "Rites", Material.FIREWORK_STAR, true,
                    "Gifts and blessings come with a particle rite and a chime."),
            choice("Powers", "vessel.arrival", "How it arrives", Material.END_PORTAL_FRAME, List.of("rift", "storm"), "rift",
                    "rift: its body steps out of a tear in the air. storm: it condenses out of a particle storm."),
            bool("Powers", "powers.explosions-break-blocks", "Explosions break blocks", Material.TNT, true,
                    "Off: its explosions hurt but leave the land whole."),
            num("Powers", "powers.max-block-changes", "Block changes", Material.GRASS_BLOCK, 100, 100000, 100, 1000,
                    2000, "", "Most blocks one power may change at once."),
            num("Powers", "powers.max-explosion-power", "Explosion power", Material.FIRE_CHARGE, 1, 20, 0.5, 2, 8, "",
                    "The strongest explosion it may make (TNT is 4)."),
            num("Powers", "powers.max-entities-per-spawn", "Mobs per summon", Material.ZOMBIE_HEAD, 1, 64, 1, 8, 12,
                    "", "Most creatures one summoning may bring."),
            // ---- Living gods: rivalries, holy days, moods in the world, servants (Powers tab) ----
            bool("Powers", "rivalry.enabled", "Rivalries", Material.IRON_SWORD, true,
                    "Gods sharing a world keep bonds: alliances, truces, taunts, sabotage and holy wars."),
            bool("Powers", "events.enabled", "Holy days", Material.BELL, true,
                    "Gods proclaim festivals, trials, hunts, omens and holy wars, with a countdown bar."),
            num("Powers", "events.cadence-hours", "Holy day reminder", Material.CLOCK, 0, 168, 1, 6, 4, "h",
                    "Remind a god to hold a holy day after this long without one (0: never)."),
            bool("Powers", "events.raids", "Raids", Material.CROSSBOW, true,
                    "Gods may send waves of raiders at a mortal they dislike. Survivable, and announced."),
            bool("Powers", "world-mood.enabled", "Moods in the world", Material.LIGHTNING_ROD, true,
                    "Its feelings touch the world: storms in wrath, quick crops in joy, bold mobs at night."),
            choice("Powers", "world-mood.strength", "Mood strength", Material.GLOWSTONE_DUST,
                    List.of("subtle", "noticeable", "strong"), "subtle", "How much its moods change weather, time and mobs."),
            bool("Powers", "creations.enabled", "Painted relics", Material.AMETHYST_SHARD, false,
                    "Gods forge relics with their own art. COSTLY: a stronger model paints each one."),
            num("Powers", "creations.per-god-per-day", "Relics a day", Material.ANVIL, 1, 50, 1, 5, 3, "",
                    "How many relics one god may forge in a day."),
            num("Powers", "creations.power-budget", "Relic power", Material.BLAZE_POWDER, 0, 40, 1, 4, 12, "",
                    "How much power a god may imbue one relic with (0 = none)."),
            bool("Powers", "servants.enabled", "Servants", Material.TOTEM_OF_UNDYING, true,
                    "Gods may summon angels, imps, shades, priests and temple guards."),
            num("Powers", "servants.max-per-god", "Servants per god", Material.ARMOR_STAND, 1, 10, 1, 2, 3, "",
                    "How many servants one god may keep at once (lesser gods half)."),
            // ---- Godslayer (Powers tab) ----
            bool("Gifts", "godslayer.enabled", "Godslayer", Material.NETHERITE_SWORD, true,
                    "The one blade that can wound and kill a god. Off: gods cannot be hurt."),
            bool("Gifts", "godslayer.craftable", "Godslayer recipe", Material.CRAFTING_TABLE, true,
                    "Mortals may craft it from a nether star, a heavy core and more."),
            num("Gifts", "godslayer.health", "God's health", Material.RED_DYE, 10, 1000, 10, 50, 100, "",
                    "A god's full health against the Godslayer."),
            num("Gifts", "godslayer.damage-min", "Blow, weakest", Material.IRON_SWORD, 1, 100, 1, 5, 15, "",
                    "Least health one full blow takes."),
            num("Gifts", "godslayer.damage-max", "Blow, strongest", Material.DIAMOND_SWORD, 1, 100, 1, 5, 25, "",
                    "Most health one full blow takes."),
            num("Gifts", "godslayer.attack-damage", "Blade damage", Material.NETHERITE_SWORD, 1, 100, 1, 5, 25, "",
                    "The Godslayer's damage to mortals and beasts."),
            bool("Gifts", "godslayer.ascended.instant-kill", "Ascended kills", Material.NETHER_STAR, true,
                    "The Ascended Godslayer kills a god in one blow."),
            num("Gifts", "godslayer.ascended.attack-damage", "Ascended damage", Material.GOLDEN_SWORD, 1, 200, 1,
                    10, 40, "", "The Ascended blade's damage to mortals."),
            num("Gifts", "godslayer.heal-delay-seconds", "Heals after", Material.GLISTERING_MELON_SLICE, 0, 3600,
                    10, 60, 60, "s", "Wounds start closing this long after the last blow."),
            num("Gifts", "godslayer.heal-per-minute", "Healing", Material.GOLDEN_APPLE, 0, 100, 1, 10, 10, "/min",
                    "Health a god regains each minute once healing."),
            bool("Gifts", "godslayer.god-returns", "Slain god returns", Material.TOTEM_OF_UNDYING, false,
                    "Off: a slain god stays dead until /god revive."),
            num("Gifts", "godslayer.return-minutes", "Returns after", Material.CLOCK, 1, 10080, 5, 60, 60, "min",
                    "How long a slain god stays dead when it returns."),
            bool("Gifts", "godslayer.held-effects", "Blade's aura", Material.SOUL_LANTERN, true,
                    "Soul fire trails the held blade, which hums."),
            // ---- Body ----
            bool("Body", "vessel.enabled", "Body", Material.ARMOR_STAND, true, "Its vessel may walk the world."),
            bool("Body", "vessel.manifest-on-wake", "Body on waking", Material.BEACON, false,
                    "It takes form by itself when mortals arrive."),
            bool("Body", "vessel.offerings", "Offerings", Material.GOLD_INGOT, true,
                    "Sneak + right-click the vessel with an item to offer it."),
            choice("Body", "vessel.nameplate", "Name above head", Material.NAME_TAG,
                    List.of("several", "always", "off"), "several",
                    "several: only when more than one god shares the world. always or off."),
            bool("Body", "vessel.phase-through-walls", "Pass through walls", Material.GLASS, false,
                    "On: its body glides through walls like a ghost. Off: it walks round them and through doors."),
            bool("Body", "vessel.face-speakers", "Face whoever speaks", Material.ENDER_EYE, true,
                    "Its body turns toward a mortal who speaks to it."),
            bool("Body", "vessel.wings", "Wings", Material.ELYTRA, true, "Particle wings in exalted moods and flight."),
            bool("Body", "vessel.cape", "Cape", Material.WHITE_BANNER, true, "A cape that sways as it moves."),
            bool("Body", "vessel.outline", "Outline", Material.GLOW_INK_SAC, true, "A glowing outline around it."),
            num("Body", "vessel.max-size", "Largest size", Material.SCAFFOLDING, 1, 6, 0.5, 1, 6,
                    "x", "How large it may grow, in player heights (1, 1.5, 3, 6)."),
            num("Body", "vessel.speech-radius", "Voice reach", Material.GOAT_HORN, 8, 128, 8, 32, 48, "blocks",
                    "How far its spoken words carry."),
            choice("Body", "vessel.idle-behavior", "When idle", Material.LEAD, List.of("follow-players", "watch"),
                    "follow-players", "follow-players: drifts to where mortals are. watch: stays put."),
            bool("Body", "vessel.true-form.enabled", "True form", Material.CRYING_OBSIDIAN, true,
                    "It may reveal its true form."),
            choice("Body", "vessel.true-form.gaze", "True form gaze", Material.WITHER_ROSE,
                    List.of("seize", "kill", "smite", "blind"), "seize",
                    "seize: lifted, then unmade. kill: at once. smite: never kills. blind."),
            choice("Body", "vessel.true-form.form", "True form shape", Material.ENDER_EYE,
                    List.of("auto", "random", "ophanim", "seraphim", "eye", "pillar", "hypercube"), "auto",
                    "auto: by its nature. Wheels, wings, one eye, a pillar of eyes."),
            num("Body", "vessel.true-form.cooldown-seconds", "True form cooldown", Material.OBSIDIAN, 0, 7200, 60, 600,
                    600, "s", "Time between two revelations."),
            num("Body", "vessel.true-form.max-seconds", "True form length", Material.HOPPER, 5, 60, 1, 5, 20, "s",
                    "How long the true form lasts at most."),
            bool("Gifts", "apotheosis.enabled", "Apotheosis", Material.TOTEM_OF_UNDYING, false,
                    "Its rarest gift: it may raise a beloved mortal to demigod (wings, strength, flight)."),
            num("Gifts", "apotheosis.cooldown-hours", "Apotheosis wait", Material.CLOCK, 1, 720, 1, 24, 72, "h",
                    "Hours before the gift can be given again."),
            num("Gifts", "apotheosis.min-favor", "Favor to ascend", Material.GOLDEN_APPLE, -10, 10, 1, 2, 8, "",
                    "Lowest favor a mortal needs to be raised."),
            num("Gifts", "apotheosis.duration-minutes", "Divinity lasts", Material.HOPPER, 0, 100000, 30, 1440, 0,
                    "min", "How long a demigod stays one (0 = until unmade)."),
            bool("Gifts", "apotheosis.flight", "Demigods fly", Material.ELYTRA, true, "Demigods may fly."),
            // ---- Voice ----
            choice("Voice", "voice.tts.engine", "Speech engine", Material.JUKEBOX, List.of("kokoro", "piper", "off"),
                    "kokoro", "kokoro: natural local voices. piper: lighter fallback. off: text only."),
            num("Voice", "voice.tts.text-wait-ms", "Text waits for voice", Material.REPEATER, 0, 10000, 250, 1000,
                    2500, "ms", "Longest the chat text waits so words and voice arrive together (0 = at once)."),
            num("Voice", "voice.tts.volume", "Volume", Material.BELL, 0.1, 2.0, 0.1, 0.5, 1.0, "x",
                    "How loud its voice is."),
            bool("Voice", "voice.tts.fallback-to-piper", "Fall back to Piper", Material.FEATHER, true,
                    "If Kokoro does not answer, speak with Piper."),
            bool("Voice", "voice.hint-without-mod", "Hint without mod", Material.OAK_SIGN, true,
                    "Tell players without the voice chat mod that they could hear the god."),
            restart(choice("Voice", "voice.transcription.whisper-model", "Listening model", Material.SCULK_SENSOR,
                    List.of("tiny.en", "base.en", "small.en", "medium.en", "base", "small"), "base.en",
                    "Bigger understands better but is slower. .en models are English only.")),
            restart(num("Voice", "voice.transcription.threads", "Listening threads", Material.SCULK, 1, 16, 1, 4, 4,
                    "", "CPU threads for speech recognition.")),
            // ---- Pantheon (several gods in one world) ----
            num("Pace", "pantheon.max-concurrent-thoughts", "Gods thinking at once", Material.BEACON, 1, 12, 1, 2, 2,
                    "", "With several gods, at most this many think at the same moment (prayers always get through)."),
            num("Pace", "pantheon.stagger-seconds", "Gods' stagger", Material.DAYLIGHT_DETECTOR, 0, 60, 1, 5, 5, "s",
                    "With several gods, unprompted thoughts start at least this far apart."),
            num("Pace", "pantheon.divine-exchanges", "Gods' replies in a row", Material.BELL, 0, 10, 1, 2, 2, "",
                    "How often gods answer only each other before waiting for a mortal to speak."),
            restart(choice("Voice", "voice.transcription.engine", "Listening engine", Material.SCULK_SENSOR,
                    List.of("local", "server", "off"), "local",
                    "local: whisper on this machine. server: a speech-to-text server. off: deaf.")),
            bool("Voice", "voice.transcription.auto-download", "Get listening model", Material.HOPPER, true,
                    "Download the speech model automatically when missing."),
            bool("Voice", "voice.tts.auto-download", "Get Piper voice", Material.HOPPER, true,
                    "Download the Piper fallback voice automatically when missing."),
            // ---- System ----
            // Other model providers (the llm: section; address, key and model name are set in config.yml).
            choice("System", "llm.provider", "Model provider", Material.COMPASS,
                    List.of("anthropic", "openai", "openai-compatible"), "anthropic",
                    "Who plays the god: Claude, OpenAI, or your own server (its address is in config.yml)."),
            choice("System", "llm.profile", "Model profile", Material.FEATHER, List.of("auto", "full", "lite"),
                    "auto", "Lite suits small local models: few powers, a short prompt. Auto: lite for a "
                            + "server on this network."),
            choice("System", "llm.vision", "Model sees images", Material.ENDER_EYE, List.of("auto", "on", "off"),
                    "auto", "Whether its look power sends pictures. Auto: yes for OpenAI, no for other servers."),
            num("System", "llm.max-output-tokens", "Longest reply (other)", Material.PAPER, 250, 16000, 250, 1000,
                    2000, "", "The longest one reply may be, for OpenAI and other servers."),
            num("System", "llm.timeout-seconds", "Model timeout", Material.CLOCK, 10, 900, 10, 60, 120, "s",
                    "How long to wait for a reply to start. Raise it for a slow local model."),
            num("System", "llm.context-tokens", "Model context size", Material.WRITABLE_BOOK, 0, 200000, 1024, 8192, 0,
                    "", "What your local model can hold at once; perceptions are cut to fit. 0: large enough."),
            num("System", "pantheon.max-gods", "Most gods per world", Material.NETHER_STAR, 1, 12, 1, 2, 4, "",
                    "How many gods may share one world, the first included (/god create)."),
            choice("System", "pantheon.default-power", "New god's power", Material.GOLD_INGOT,
                    List.of("lesser", "greater", "supreme"), "greater",
                    "The power of a god made without one. Change any god's on its Might & Mind page."),
            bool("System", "god.tab-list", "Tab list", Material.NAME_TAG, true,
                    "The god's name and state at the top of the player list."),
            bool("System", "debug.log-usage", "Usage in console", Material.GOLD_NUGGET, true,
                    "Print a usage line for every request to the console."),
            bool("System", "debug.usage-file", "usage.csv", Material.MAP, true, "Append every request to usage.csv."),
            bool("System", "debug.log-requests", "Log perceptions", Material.SPYGLASS, false,
                    "Print everything the god sees each thought (very long)."),
            choice("Body", "vessel.size", "Default size", Material.ARMOR_STAND,
                    List.of("mortal", "lordly", "towering", "colossal"), "lordly",
                    "How large its body is unless it chooses (mortal 1, lordly 1.5, towering 3, colossal 6)."),
            choice("Body", "vessel.textures", "Body textures", Material.PAINTING, List.of("custom", "vanilla"),
                    "custom", "custom: its own painted skins (resource pack). vanilla: plain blocks."),
            num("Powers", "vessel.true-form.warning-seconds", "True form warning", Material.BELL, 0, 20, 1, 3, 4, "s",
                    "Seconds of warning before the gaze strikes."),
            num("Powers", "vessel.true-form.range", "True form reach", Material.SPYGLASS, 8, 128, 4, 16, 48,
                    "blocks", "How far away its gaze reaches."),
            restart(bool("System", "rounds.enabled", "Island rounds", Material.FILLED_MAP, false,
                    "The old round game mode: islands, resets and teams.")),
            bool("System", "vision.save-renders", "Save its sight", Material.PAINTING, false,
                    "Save every image it looks at to the plugin folder."),
            bool("System", "vessel.resource-pack.send", "Send skin pack", Material.CHEST, true,
                    "Offer players the pack with its own skins and textures."),
            bool("System", "vessel.resource-pack.required", "Pack required", Material.IRON_DOOR, false,
                    "Players must accept the pack to stay."),
            restart(num("System", "viewer.port", "Mind viewer port", Material.OBSERVER, 1024, 65535, 1, 100,
                    4673, "", "The port of the web page that shows its mind.")),
            restart(num("System", "bridge.port", "Bridge port", Material.REPEATER, 1024, 65535, 1, 100, 8765, "",
                    "The port of the local test bridge.")),
            num("Gifts", "apotheosis.max-demigods", "Demigods at once", Material.TOTEM_OF_UNDYING, 1, 10, 1, 2, 1,
                    "", "How many demigods may live at the same time."),
            num("Gifts", "godslayer.health-bar-radius", "Health bar reach", Material.RED_BANNER, 8, 256, 8, 32, 64,
                    "blocks", "How near players must be to see a wounded god's health."),
            restart(bool("System", "bridge.enabled", "Debug bridge", Material.REDSTONE, false,
                    "The local HTTP API for test harnesses and external brains.")),
            bool("System", "viewer.enabled", "Mind viewer", Material.OBSERVER, true,
                    "A web page to watch and edit each god's mind live. The URL is in the console."));

    public static List<Option> of(String tab) {
        List<Option> out = new ArrayList<>();
        for (Option o : OPTIONS) {
            if (o.tab().equals(tab)) out.add(o);
        }
        return out;
    }

    public static Option find(String key) {
        for (Option o : OPTIONS) {
            if (o.key().equals(key)) return o;
        }
        return null;
    }

    /**
     * The value after a click: a switch flips; a number moves by its step (shift: its big step), right
     * click down, clamped and snapped to the step; a choice moves to the next (right: previous).
     */
    public static String after(Option o, String now, boolean right, boolean shift) {
        String v = now == null || now.isBlank() ? o.fallback() : now.trim();
        switch (o.type()) {
            case BOOL -> {
                return String.valueOf(!Boolean.parseBoolean(v));
            }
            case CHOICE -> {
                List<String> c = o.choices();
                int i = c.indexOf(v.toLowerCase(Locale.ROOT));
                if (i < 0) return c.get(0);
                return c.get(Math.floorMod(i + (right ? -1 : 1), c.size()));
            }
            default -> {
                double d;
                try {
                    d = Double.parseDouble(v);
                } catch (NumberFormatException e) {
                    d = Double.parseDouble(o.fallback());
                }
                double by = (shift ? o.big() : o.step()) * (right ? -1 : 1);
                double next = Math.max(o.min(), Math.min(o.max(), d + by));
                next = Math.round(next / o.step()) * o.step();
                next = Math.max(o.min(), Math.min(o.max(), next));
                if (o.type() == Type.INT) return String.valueOf(Math.round(next));
                return String.valueOf(Math.round(next * 100) / 100.0);
            }
        }
    }

    /** How a value reads on the icon. */
    public static String show(Option o, String v) {
        String value = v == null || v.isBlank() ? o.fallback() : v;
        if (o.type() == Type.BOOL) return Boolean.parseBoolean(value) ? "ON" : "OFF";
        if (o.key().equals("brain.idle-seconds") && value.equals("0")) return "never";
        if (o.key().equals("memory.annals-retention-days") && value.equals("0")) return "forever";
        if (o.key().equals("apotheosis.duration-minutes") && value.equals("0")) return "forever";
        return o.unit().isEmpty() ? value : value + " " + o.unit();
    }

    /** What clicking does, for the lore. */
    static String controls(Option o) {
        return switch (o.type()) {
            case BOOL -> "Click to switch.";
            case CHOICE -> "Left: next. Right: back.";
            default -> "L +" + trim(o.step()) + ", R -" + trim(o.step()) + ", shift ±" + trim(o.big()) + ".";
        };
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
