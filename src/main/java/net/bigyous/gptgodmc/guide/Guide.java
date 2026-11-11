package net.bigyous.gptgodmc.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The guide every mortal is handed in a world with a god: what the gods are, how to reach them, what they
 * can do, and (for operators) how to start and run them. Pure: built from plain values, so the same text
 * becomes the written book ({@link GuideBook}) and the client mod's screen (ModGuide), and is unit tested.
 * Sections about switched-off features are left out. Book pages hold about 19 characters by 14 lines, so
 * keep sentences short and command lines narrow.
 */
public final class Guide {
    private Guide() {
    }

    public static final String TITLE = "The Book of the Gods";

    /** One piece of a chapter. */
    public sealed interface Block permits Para, Heading, Cmd {
    }

    /** A paragraph of plain text. */
    public record Para(String text) implements Block {
    }

    /** A small heading inside a chapter. */
    public record Heading(String text) implements Block {
    }

    /** A command (or a key, or a config section) and what it does. */
    public record Cmd(String cmd, String text) implements Block {
    }

    /** op = only operators are shown it. */
    public record Chapter(String id, String title, boolean op, List<Block> blocks) {
    }

    /** A god of the reader's world, for the title page. color is 0xRRGGBB. */
    public record GodLine(String name, String epithet, int color, boolean slain) {
    }

    /** What the reader is and what the server has switched off. */
    public record Context(boolean operator, List<GodLine> gods, Set<Feature> off) {
        public Context {
            gods = gods == null ? List.of() : List.copyOf(gods);
            off = off == null ? Set.of() : Set.copyOf(off);
        }

        public boolean on(Feature f) {
            return !off.contains(f);
        }
    }

    /** Things a server may switch off; their sections are then left out. */
    public enum Feature {
        OATHS, ECOLOGY, GODSLAYER, GODSLAYER_CRAFT, RELICS, SERVANTS, HOLY_DAYS, PURPOSE, APOTHEOSIS, VOICE
    }

    public record Book(String title, List<GodLine> gods, boolean operator, List<Chapter> chapters) {
        public List<Chapter> playerChapters() {
            return chapters.stream().filter(c -> !c.op()).toList();
        }

        public List<Chapter> operatorChapters() {
            return chapters.stream().filter(Chapter::op).toList();
        }
    }

    public static Book build(Context c) {
        List<Chapter> out = new ArrayList<>();
        out.add(gods(c));
        out.add(speaking(c));
        out.add(worship(c));
        if (c.on(Feature.OATHS)) out.add(oaths());
        out.add(powers(c));
        if (c.on(Feature.ECOLOGY)) out.add(land());
        out.add(pantheon(c));
        if (c.on(Feature.GODSLAYER)) out.add(godslayer(c));
        out.add(reference(c));
        if (c.operator()) {
            out.add(opStart());
            out.add(opManager());
            out.add(opGods());
            out.add(opCommand());
            out.add(opWatch());
            out.add(opMore(c));
            out.add(opConfig());
        }
        return new Book(TITLE, c.gods(), c.operator(), List.copyOf(out));
    }

    // ---- For everyone -----------------------------------------------------------------------------

    private static Chapter gods(Context c) {
        List<Block> b = new ArrayList<>();
        b.add(p("This world is watched by a god, played by an AI (Claude, or another model). It is not a "
                + "script: it decides for itself what to say and do."));
        b.add(p("It sees what is said, who dies, what is built and broken, and what mortals do to the land."));
        b.add(p("It remembers every mortal: their deeds, their words, and how much it favors them. Worship and "
                + "good deeds raise favor; insults and crimes lower it."));
        b.add(p("It has a heart: wrath, joy, sorrow, dread and glory, which linger and color what it does."));
        b.add(p("It speaks in chat and on your screen" + (c.on(Feature.VOICE)
                ? ", and aloud to players with the Simple Voice Chat mod." : ".")));
        b.add(p("It has a body, its vessel, that walks and flies. Sometimes it is formless and watches "
                + "unseen."));
        return new Chapter("gods", "What the gods are", false, b);
    }

    private static Chapter speaking(Context c) {
        List<Block> b = new ArrayList<>();
        b.add(cmd("/pray <words>", "Speak to the god. Alone: sense how it regards you."));
        b.add(cmd("/pray <god>, <words>", "Pray to one god by name."));
        b.add(cmd("Its name in chat", "Say its name in chat and it listens."));
        if (c.on(Feature.VOICE)) {
            b.add(cmd("Your voice", "With Simple Voice Chat, it hears you speak."));
        }
        b.add(cmd("Touch its vessel", "Right-click it to pray. Strike it and it knows."));
        b.add(cmd("/gods", "Which gods watch this world."));
        b.add(cmd("/nickname <name>", "Tell the god what to call you."));
        return new Chapter("speak", "Speaking to gods", false, b);
    }

    private static Chapter worship(Context c) {
        List<Block> b = new ArrayList<>();
        b.add(cmd("/offer [amount]", "Offer what you hold."));
        b.add(cmd("Sneak + right-click", "Lay the item in your hand at the vessel's feet."));
        b.add(cmd("/commandments", "The god's laws. It watches who keeps them."));
        b.add(h("Signs of favor"));
        b.add(p("A god may give you a title, mark you with a halo or a curse, and hold rites in your honor."));
        if (c.on(Feature.PURPOSE)) {
            b.add(h("Missions"));
            b.add(p("Gods want things: a temple, a slain dragon, more faithful. They give quests and missions "
                    + "(shown at the top of the screen) and reward those who finish them."));
        }
        if (c.on(Feature.APOTHEOSIS)) {
            b.add(p("For a legendary deed, a god may even raise a mortal it loves to demigod."));
        }
        return new Chapter("worship", "Worship and favor", false, b);
    }

    private static Chapter oaths() {
        List<Block> b = new ArrayList<>();
        b.add(p("Swear an oath before a god and it accepts or refuses it."));
        b.add(cmd("/swear <words>", "Swear an oath (also /vow). /swear <god>, <words> swears to one god."));
        b.add(cmd("/swear", "Alone: the oaths that bind you (also /oaths)."));
        b.add(p("A god may set a watch: never do a thing, or do it N times, perhaps near a place. A breach "
                + "reaches it at once."));
        b.add(p("In the end it judges the oath kept, broken or released, and remembers for ever. An oath it "
                + "never answers lapses."));
        return new Chapter("oaths", "Oaths", false, b);
    }

    private static Chapter powers(Context c) {
        List<Block> b = new ArrayList<>();
        b.add(p("What it does depends on its nature and mood. Among its powers:"));
        b.add(cmd("Blessings", "Healing, gifts, strength, luck."));
        b.add(cmd("Wrath", "Lightning, curses, mobs; for the mightiest, meteors and earthquakes."));
        b.add(cmd("Building", "Temples, statues, whole buildings."));
        b.add(cmd("Vision", "It looks through its eyes, from above, or through stone."));
        b.add(cmd("Music", "It composes songs and gives them as discs."));
        if (c.on(Feature.SERVANTS)) {
            b.add(cmd("Servants", "Seraphs, imps, wraiths and more, at its orders."));
        }
        if (c.on(Feature.RELICS)) {
            b.add(cmd("Relics", "Things it forges itself, with their own look and powers."));
        }
        b.add(cmd("Its true form", "Rarely, it shows its true shape. Do not look at it."));
        return new Chapter("powers", "What a god can do", false, b);
    }

    private static Chapter land() {
        List<Block> b = new ArrayList<>();
        b.add(p("A god sees the land around mortals: herds and pets, villages, fields and forests."));
        b.add(p("It keeps the mark each mortal leaves: trees felled and planted, animals slain and bred, fields "
                + "reaped, villagers killed. The record fades with time."));
        b.add(p("Killing villagers, losing a raid or a slaughter spree draws its eye at once."));
        b.add(h("Tending the land"));
        b.add(p("It may make land flourish (crops ripen, saplings and young grow) or blight it (crops wither, "
                + "saplings die, animals sicken but live)."));
        return new Chapter("land", "The living land", false, b);
    }

    private static Chapter pantheon(Context c) {
        List<Block> b = new ArrayList<>();
        b.add(p("A world may have several gods, each with its own name, nature, memory and body. They become "
                + "allies, rivals or enemies."));
        b.add(p("A prayer that names a god goes to it; others go to the god who favors you most."));
        if (c.on(Feature.HOLY_DAYS)) {
            b.add(h("Holy days"));
            b.add(p("Gods proclaim festivals, trials, hunts, raids, omens and holy wars, with a countdown "
                    + "bar, points for chosen deeds, and rewards."));
            b.add(cmd("/holydays", "What the gods have proclaimed."));
            b.add(cmd("/side <god>", "Choose a side in a holy war. Alone: see the wars."));
        }
        return new Chapter("pantheon", "Many gods", false, b);
    }

    private static Chapter godslayer(Context c) {
        List<Block> b = new ArrayList<>();
        b.add(p("One blade can wound a god: the Godslayer. Wounds close slowly. A god struck down dies, and "
                + "stays dead until it returns or is raised."));
        if (c.on(Feature.GODSLAYER_CRAFT)) {
            b.add(h("Forging it"));
            b.add(p("Top: echo shard, nether star, echo shard. Middle: netherite ingot, heavy core, netherite "
                    + "ingot. Bottom: dragon's breath, blaze rod, dragon's breath."));
        }
        b.add(p("Gods know the blade. Think before you raise it."));
        return new Chapter("godslayer", "The Godslayer", false, b);
    }

    private static Chapter reference(Context c) {
        List<Block> b = new ArrayList<>();
        b.add(cmd("/pray [god,] [words]", "speak to a god"));
        b.add(cmd("/offer [amount]", "offer what you hold"));
        if (c.on(Feature.OATHS)) b.add(cmd("/swear [god,] [words]", "swear or list oaths"));
        b.add(cmd("/commandments", "the god's laws"));
        b.add(cmd("/gods", "the gods of this world"));
        if (c.on(Feature.HOLY_DAYS)) {
            b.add(cmd("/holydays", "festivals and wars"));
            b.add(cmd("/side [god]", "pick a side in a war"));
        }
        b.add(cmd("/nickname <name>", "your name to the god"));
        b.add(cmd("/guide", "this book again"));
        return new Chapter("reference", "Quick reference", false, b);
    }

    // ---- For operators ----------------------------------------------------------------------------

    private static Chapter opStart() {
        List<Block> b = new ArrayList<>();
        b.add(h("The API key"));
        b.add(cmd("gptgod setkey <key>", "In the server console, not in game. Saved in config.yml."));
        b.add(p("Or set ANTHROPIC_API_KEY before starting the server, or paste it into anthropic.api-key and "
                + "run /god reload. Other models (OpenAI, local servers): the llm: section."));
        b.add(p("The god wakes when the first player arrives."));
        b.add(cmd("/god status", "Who the god is and what it is doing."));
        b.add(cmd("/god help", "Lists every subcommand."));
        return new Chapter("op-start", "Starting a god", true, b);
    }

    private static Chapter opManager() {
        List<Block> b = new ArrayList<>();
        b.add(p("A menu for all an operator does: personality, mood, body, voice, commands, memories, pausing "
                + "and server settings."));
        b.add(cmd("/god manager", "Open it (also /god gui, /god menu)."));
        b.add(cmd("/god manager item", "The nether star that opens it."));
        b.add(cmd("/god manager chest", "The chest menu, for client mod users."));
        b.add(p("With the GPTGOD client mod it is a real screen; its key is G."));
        return new Chapter("op-manager", "The God Manager", true, b);
    }

    private static Chapter opGods() {
        List<Block> b = new ArrayList<>();
        b.add(cmd("/god create <name|-> [archetype] [power]", "A new god in your world (- lets it name "
                + "itself). Power: lesser, greater or supreme."));
        b.add(cmd("/god remove <name> confirm", "Take a god away. Its memory is kept in god/removed."));
        b.add(cmd("/god list", "Every god on the server."));
        b.add(cmd("/god select <name|none>", "Which god your /god commands go to."));
        b.add(cmd("/god @<name> <subcommand>", "Speak to one god once. /god @all reaches every god (pause, "
                + "resume, wake, say, command)."));
        b.add(cmd("/god power [level]", "What it may do and hear."));
        b.add(p("Each god costs as much to run as the first (pantheon: section). Creating and removing gods "
                + "needs gptgodmc.pantheon."));
        b.add(h("Realms"));
        b.add(p("The realms: section decides which worlds a god rules: rule-all-worlds, extra-worlds, and "
                + "(experimental) one god per world. Needs a restart."));
        return new Chapter("op-gods", "Creating and removing gods", true, b);
    }

    private static Chapter opCommand() {
        List<Block> b = new ArrayList<>();
        b.add(cmd("/god command <text>", "BINDING: the god must carry it out, and reports back to you."));
        b.add(cmd("/god commands", "Pending ones. /god command cancel <n|all> withdraws them."));
        b.add(cmd("/god say <text>", "A suggestion it may weigh. Not binding."));
        b.add(cmd("/god wake [text]", "Make it look at the world now."));
        b.add(cmd("/god pause", "Stop its thinking (powers still work). /god resume undoes it."));
        b.add(cmd("/god persona <archetype>", "Choose who it is (random, or list). /god rebirth: a new god."));
        b.add(cmd("/god vessel <action>", "Its body: summon, follow, fly, rise, mood, gesture, look, dismiss."));
        b.add(cmd("/god tool <power> [json]", "Run one of its powers yourself. /god tools lists them."));
        return new Chapter("op-command", "Commanding a god", true, b);
    }

    private static Chapter opWatch() {
        List<Block> b = new ArrayList<>();
        b.add(cmd("/god journal [n]", "Its recent thoughts, acts and errors."));
        b.add(cmd("/god memory", "Its chronicle, mortals, quests and works."));
        b.add(cmd("/god view <kind>", "A picture of what it sees: eyes, vessel, overhead, panorama, orbit."));
        b.add(h("The mind viewer"));
        b.add(p("A web page with every thought, prompt and tool call, where memory can be edited. On by "
                + "default at http://localhost:4673 (viewer: section)."));
        b.add(h("Costs"));
        b.add(p("Costs never appear in chat. Type gptgod usage in the console, or read usage.csv in the "
                + "plugin folder. The client mod's manager has a Costs page."));
        return new Chapter("op-watch", "Watching a god", true, b);
    }

    private static Chapter opMore(Context c) {
        List<Block> b = new ArrayList<>();
        b.add(cmd("/god reload", "Reload config.yml."));
        b.add(cmd("/god undo [work]", "Unbuild what the god built."));
        b.add(cmd("/god forget", "Wipe its memory (not its identity)."));
        b.add(cmd("/god pardon <player>", "Clear manipulation strikes."));
        b.add(cmd("/god model | effort", "Its model; how hard it thinks."));
        b.add(cmd("/bonds", "How gods regard each other; set it."));
        b.add(cmd("/relics", "The gods' forge (off by default: costly)."));
        if (c.on(Feature.GODSLAYER)) {
            b.add(cmd("/godslayer [player]", "Give the blade (ascended: kills in one blow; preview)."));
            b.add(cmd("/god revive", "Raise a slain god."));
        }
        b.add(cmd("/guide <player>", "Hand someone this book."));
        return new Chapter("op-more", "More commands", true, b);
    }

    private static Chapter opConfig() {
        List<Block> b = new ArrayList<>();
        b.add(p("In config.yml, by section:"));
        b.add(cmd("anthropic, llm", "Key, model, effort; other providers."));
        b.add(cmd("god, personality", "Name, language, archetypes, dials."));
        b.add(cmd("brain, initiative", "How often it thinks and acts alone."));
        b.add(cmd("powers, custom-powers", "Turn powers off; add your own."));
        b.add(cmd("vessel, voice", "Its body and looks; speech, hearing."));
        b.add(cmd("pantheon, realms", "Several gods; their worlds."));
        b.add(cmd("operator, security", "Commands; anti-manipulation."));
        b.add(cmd("ecology, oaths, purpose", "The land, oaths, goals, quests."));
        b.add(cmd("godslayer, creations", "The blade; relics (costly)."));
        b.add(cmd("events, rivalry, servants", "Holy days, bonds, servants."));
        b.add(cmd("viewer, client-mod, guide", "Mind viewer, the mod, this book."));
        return new Chapter("op-config", "Config sections", true, b);
    }

    // ---- Helpers ----------------------------------------------------------------------------------

    private static Para p(String text) {
        return new Para(text);
    }

    private static Heading h(String text) {
        return new Heading(text);
    }

    private static Cmd cmd(String cmd, String text) {
        return new Cmd(cmd, text);
    }
}
