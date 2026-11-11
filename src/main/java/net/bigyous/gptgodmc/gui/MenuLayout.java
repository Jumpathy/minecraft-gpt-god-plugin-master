package net.bigyous.gptgodmc.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;

import net.bigyous.gptgodmc.god.TabList;
import net.bigyous.gptgodmc.god.vessel.VesselMood;
import net.bigyous.gptgodmc.gui.MenuModel.Action;
import net.bigyous.gptgodmc.gui.MenuModel.Archetype;
import net.bigyous.gptgodmc.gui.MenuModel.Decision;
import net.bigyous.gptgodmc.gui.MenuModel.Icon;
import net.bigyous.gptgodmc.gui.MenuModel.Kind;
import net.bigyous.gptgodmc.gui.MenuModel.Menu;
import net.bigyous.gptgodmc.gui.MenuModel.Page;
import net.bigyous.gptgodmc.gui.MenuModel.Pending;
import net.bigyous.gptgodmc.gui.MenuModel.Preset;
import net.bigyous.gptgodmc.gui.MenuModel.Setting;
import net.bigyous.gptgodmc.gui.MenuModel.State;

/** Which icon goes in which slot of each page, and what a click on a slot means. Pure. */
public final class MenuLayout {
    static final int WHITE = 0xFFFFFF;
    static final int GOLD = 0xFFAA00;
    static final int GREEN = 0x55FF55;
    static final int RED = 0xFF5555;
    static final int AQUA = 0x55FFFF;
    static final int VIOLET = 0xC77DFF;
    static final int GRAY = 0xAAAAAA;
    static final int YELLOW = 0xFFFF55;

    /** Inner slots (inside the glass frame), rows 1 to 4, left to right. */
    static final int[] INNER = inner();

    /** The boolean options on the Settings page, in order. */
    public static final List<Setting> SETTINGS = List.of(
            new Setting("vessel.enabled", "The god's body", true,
                    "Whether the god can take a physical vessel at all."),
            new Setting("vessel.manifest-on-wake", "Body on waking", false,
                    "Take a body as soon as the first mortal arrives (off: it begins formless)."),
            new Setting("vessel.offerings", "Offerings", true, "Mortals may hand items to the vessel as offerings."),
            new Setting("vessel.true-form.enabled", "True form allowed", true,
                    "Whether the god may reveal its true form at all."),
            new Setting("powers.rites", "Rites", true, "Gifts and blessings come with a particle rite and a chime."),
            new Setting("powers.custom-powers", "Custom powers", true,
                    "The god may use the custom powers written in config.yml."),
            new Setting("powers.commands", "Server commands", true,
                    "The god may run server commands (minus the blocklist)."),
            new Setting("powers.explosions-break-blocks", "Explosions break blocks", true,
                    "Meteors, smiting and blasts may break blocks."),
            new Setting("brain.acknowledge", "\"I hear you\" sign", true,
                    "Mortals get an instant sign that the god heard them, before it answers."),
            new Setting("god.tab-list", "God in the tab list", true,
                    "Show the god's name and state at the top of the player list."));

    /** One-click binding commands. */
    public static final List<Preset> PRESETS = List.of(
            new Preset("speak", Material.OAK_SIGN, "Speak to everyone now",
                    "Speak now to every mortal in your realm: a message of your own choosing, in your own voice.",
                    false),
            new Preset("bless", Material.GOLDEN_APPLE, "Bless everyone online",
                    "Bless every mortal now in your realm with a gift or blessing of your choosing, each one.", false),
            new Preset("punish_killer", Material.IRON_SWORD, "Punish the last killer",
                    "Find the mortal who most recently killed another player and punish them fittingly now. If no "
                            + "mortal has killed another player, report that with command_done.",
                    false),
            new Preset("appear", Material.ARMOR_STAND, "Appear before them",
                    "Take your body and appear before the mortals now; let them see and hear you.", false),
            new Preset("quest", Material.MAP, "Give a new quest",
                    "Give the mortals a new quest now, with a fitting reward.", false),
            new Preset("true_form", Material.ENDER_EYE, "Reveal true form to all",
                    "Reveal your true form now where every mortal in your realm can see it.", true));

    private MenuLayout() {
    }

    private static int[] inner() {
        int[] out = new int[28];
        int i = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                out[i++] = row * 9 + col;
            }
        }
        return out;
    }

    // ---- Pages ---------------------------------------------------------------------------------

    public static Menu build(Page page, State s) {
        Map<Integer, Icon> icons = new LinkedHashMap<>();
        frame(icons, page);
        icons.put(MenuModel.HEADER_SLOT, page.serverWide ? serverHeader(page, s) : header(s));
        switch (page) {
            case HUB -> hub(icons, s);
            case MAIN -> main(icons, s);
            case PERSONALITY -> personality(icons, s);
            case MOOD -> mood(icons, s);
            case PRESENCE -> presence(icons, s);
            case COMMAND -> command(icons, s);
            case SETTINGS -> settings(icons, s);
            case PLAYERS -> players(icons, s);
            case PLAYER -> player(icons, s);
            case DIALS -> dials(icons, s);
            case VOICE -> voice(icons, s);
            case ADVANCED -> advanced(icons, s);
            case LOOKS -> looks(icons, s);
            case CREATE -> create(icons, s);
            case MIGHT -> might(icons, s);
            case BONDS -> bonds(icons, s);
        }
        // Back always leads to the page's parent; Home, on pages two or more clicks deep, to the hub.
        if (page.parent != null) {
            icons.put(MenuModel.BACK_SLOT, Icon.of(Material.ARROW, "Back", WHITE,
                    List.of("To " + crumb(page.parent, s) + "."), Action.open(page.parent)).art("back"));
        }
        if (page.depth() >= 2) {
            icons.put(MenuModel.HOME_SLOT, Icon.of(Material.NETHER_STAR, "Home", VIOLET,
                    List.of("To the God Manager: every god,", "Quick switches, Server config."),
                    Action.open(Page.HUB)).art("home"));
        }
        icons.put(MenuModel.CLOSE_SLOT, Icon.of(Material.BARRIER, "Close", RED, List.of(), Action.of(Kind.CLOSE, null))
                .art("close"));
        if (s.confirm() != null) {
            icons.put(MenuModel.CONFIRM_SLOT, Icon.of(Material.LIME_CONCRETE, "Confirm", GREEN,
                    wrap(s.confirm().confirm() + " Click to do it."), Action.of(Kind.CONFIRM, null)).glowing(true));
            icons.put(MenuModel.CANCEL_SLOT, Icon.of(Material.RED_CONCRETE, "Cancel", RED, List.of("Do nothing."),
                    Action.of(Kind.CANCEL_CONFIRM, null)));
        }
        return new Menu(page, title(page, s), icons);
    }

    /** The breadcrumb in the chest's title: "God Manager", "Vaelor", "Vaelor › Mood & heart", "God Manager › Server config". */
    static String title(Page page, State s) {
        return switch (page) {
            case HUB, MAIN -> crumb(page, s);
            case PLAYER -> godCrumb(s) + " › " + (s.selected() == null ? "Player" : s.selected().name());
            default -> crumb(page.parent, s) + " › " + page.title;
        };
    }

    /** What a page is called on its own: the god's name for its page, else the page's title. */
    static String crumb(Page page, State s) {
        return switch (page) {
            case MAIN -> godCrumb(s);
            case PLAYERS -> godCrumb(s) + "'s mortals";
            default -> page.title;
        };
    }

    private static String godCrumb(State s) {
        String name = s.born() ? s.godName() : "Unborn god";
        return name.length() > 16 ? name.substring(0, 15) + "…" : name;
    }

    private static void frame(Map<Integer, Icon> icons, Page page) {
        Material pane = switch (page) {
            case HUB -> Material.ORANGE_STAINED_GLASS_PANE;
            case MAIN -> Material.PURPLE_STAINED_GLASS_PANE;
            case PERSONALITY -> Material.MAGENTA_STAINED_GLASS_PANE;
            case MOOD -> Material.RED_STAINED_GLASS_PANE;
            case PRESENCE -> Material.CYAN_STAINED_GLASS_PANE;
            case COMMAND -> Material.YELLOW_STAINED_GLASS_PANE;
            case SETTINGS -> Material.GRAY_STAINED_GLASS_PANE;
            case PLAYERS -> Material.LIME_STAINED_GLASS_PANE;
            case PLAYER -> Material.GREEN_STAINED_GLASS_PANE;
            case DIALS -> Material.PINK_STAINED_GLASS_PANE;
            case VOICE -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case ADVANCED -> Material.BLACK_STAINED_GLASS_PANE;
            case LOOKS -> Material.MAGENTA_STAINED_GLASS_PANE;
            case CREATE -> Material.ORANGE_STAINED_GLASS_PANE;
            case MIGHT -> Material.BLUE_STAINED_GLASS_PANE;
            case BONDS -> Material.YELLOW_STAINED_GLASS_PANE;
        };
        Icon glass = Icon.of(pane, " ", GRAY, List.of(), Action.NONE);
        for (int slot = 0; slot < MenuModel.SIZE; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == MenuModel.ROWS - 1 || col == 0 || col == 8) icons.put(slot, glass);
        }
    }

    private static Icon header(State s) {
        List<String> lore = new ArrayList<>();
        if (s.born()) {
            if (s.epithet() != null && !s.epithet().isBlank()) lore.add(s.epithet());
            lore.add("Archetype: " + s.archetype());
        } else {
            lore.add("Not yet born: it is created");
            lore.add("when it first awakens.");
        }
        lore.add("State: " + (s.setting("god.dormant", false) ? "turned off" : !s.awake()
                ? "asleep (no mortals near)" : s.paused() ? "awake, thoughts paused"
                : "awake"));
        lore.add("Body: " + (s.trueForm() ? "true form revealed" : s.hasBody() ? "walks the world" : "formless"));
        if (s.mood() != null && s.mood() != VesselMood.CALM) lore.add("Mood: " + s.mood().id());
        if (s.heart() != null) {
            lore.add("Heart: " + (s.heart().isEmpty() ? "at peace" : s.heart().get(0)));
            if (s.heart().size() > 1) lore.add("       " + s.heart().get(1));
        }
        if (s.realm() != null) lore.add("Realm: " + s.realm());
        int rgb = TabList.nameColor(s.archetype(), s.mood()).value();
        return Icon.of(Material.NETHER_STAR, s.born() ? s.godName() : "The unborn god", rgb, lore, Action.NONE)
                .glowing(true);
    }

    /** The header of the hub and the server pages: what they are, not one god. */
    private static Icon serverHeader(Page page, State s) {
        List<String> lore = new ArrayList<>();
        int many = s.pantheon().list().size();
        switch (page) {
            case HUB -> {
                lore.add(many <= 1 ? "One god rules this server." : many + " gods on this server.");
                lore.add("Click a god to manage it.");
                lore.add("Below: settings for the server.");
            }
            case CREATE -> {
                lore.add("A new god beside the others,");
                lore.add("with its own name and memory.");
            }
            default -> {
                lore.add("Applies to the whole server,");
                lore.add("every god alike. Saved to");
                lore.add("config.yml.");
            }
        }
        return Icon.of(Material.NETHER_STAR, page.title, VIOLET, lore, Action.NONE).glowing(true).art("server");
    }

    // ---- The hub --------------------------------------------------------------------------------

    /** Hub: where each god is listed (rows 1 and 2) and the server's buttons (row 4). */
    public static final int CREATE_GOD_SLOT = 37;
    public static final int SWITCHES_SLOT = 39;
    public static final int CONFIG_SLOT = 41;
    public static final int HELP_SLOT = 43;

    private static void hub(Map<Integer, Icon> icons, State s) {
        List<MenuModel.Deity> list = s.pantheon().list();
        if (list.isEmpty()) {
            // The server's one god, before the pantheon is known (and in old samples).
            icons.put(INNER[0], godIcon(new MenuModel.Deity(s.realm() == null ? "main" : s.realm(), s.godName(),
                    s.epithet(), s.archetype(), "supreme", s.awake() ? "awake" : "asleep", true, false)));
        }
        for (int i = 0; i < list.size() && i < 14; i++) icons.put(INNER[i], godIcon(list.get(i)));
        icons.put(CREATE_GOD_SLOT, Icon.of(Material.EMERALD, "Create a god", GREEN,
                List.of("A new god beside these, with its", "own name, memory, body and voice.",
                        "Pick its nature and power."),
                Action.open(Page.CREATE)).art("create"));
        icons.put(SWITCHES_SLOT, Icon.of(Material.COMPARATOR, "Quick switches", GRAY,
                List.of("The 10 most used on/off settings:", "bodies, offerings, rites, server",
                        "commands, the tab list...", "For every god on the server."),
                Action.open(Page.SETTINGS)).art("switches"));
        icons.put(CONFIG_SLOT, Icon.of(Material.COMMAND_BLOCK, "Server config", VIOLET,
                List.of("Every setting of config.yml, in", "8 tabs: mind, pace, memory,",
                        "powers, bodies, voice, system,", "gifts. For every god alike."),
                Action.open(Page.ADVANCED)).glowing(true).art("config"));
        icons.put(HELP_SLOT, Icon.of(Material.KNOWLEDGE_BOOK, "How this menu works", WHITE,
                List.of("Top: each god. Click one for its", "personality, mood, body, voice,",
                        "commands and memories.", "Bottom: settings for the server.",
                        "Back (bottom left) goes up one", "page; Home comes back here."),
                Action.NONE).art("help"));
    }

    private static Icon godIcon(MenuModel.Deity d) {
        List<String> lore = new ArrayList<>();
        if (d.epithet() != null && !d.epithet().isBlank()) lore.addAll(wrap(d.epithet(), 2));
        lore.add("Archetype: " + (d.archetype() == null ? "not yet chosen" : d.archetype()));
        lore.add("Power: " + d.power());
        lore.add("State: " + d.state());
        lore.add("Click to manage this god.");
        int rgb = TabList.archetypeColor(d.archetype()).value();
        return Icon.of(powerMaterial(d.power()), d.name() == null ? "An unborn god (@" + d.id() + ")" : d.name(), rgb,
                lore, Action.of(Kind.SELECT_GOD, d.id())).glowing("awake".equals(d.state()))
                .art("god_" + (d.power() == null ? "supreme" : d.power()));
    }

    // ---- One god's page -------------------------------------------------------------------------

    /**
     * A god's page, one row per group: who it is (row 1), how it shows itself (row 2), what it does
     * (row 3), and turning it on, off or away (row 4).
     */
    static final int PERSONALITY_SLOT = 11, NATURE_SLOT = 13, MIGHT_SLOT = 15;
    static final int MOOD_SLOT = 19, PRESENCE_SLOT = 21, LOOKS_SLOT = 23, VOICE_SLOT = 25;
    static final int COMMANDS_SLOT = 28, PLAYERS_SLOT = 30, BONDS_SLOT = 32, WAKE_SLOT = 34;
    static final int PAUSE_SLOT = 38;

    private static void main(Map<Integer, Icon> icons, State s) {
        MenuModel.Gods gods = s.pantheon();
        // Who it is.
        icons.put(PERSONALITY_SLOT, Icon.of(Material.MAGENTA_WOOL, "Personality", 0xFF77FF,
                List.of("Its archetype: " + (s.born() ? s.archetype() : "unborn") + ".",
                        "Choosing another makes a NEW god", "(its memory starts fresh)."),
                Action.open(Page.PERSONALITY)).art("personality"));
        icons.put(NATURE_SLOT, Icon.of(Material.AMETHYST_SHARD, "Nature", 0xFF77FF,
                List.of("Fine-tune it: generosity, wrath,", "humor, mercy... Same god, same",
                        "memories."),
                Action.open(Page.DIALS)).art("nature"));
        icons.put(MIGHT_SLOT, Icon.of(Material.DIAMOND_SWORD, "Might & Mind", AQUA,
                List.of("Power: " + gods.power(), "Mind: " + shortModel(gods.model() == null ? gods.configModel()
                        : gods.model()), "Effort: " + (gods.effort() == null ? gods.configEffort() : gods.effort())),
                Action.open(Page.MIGHT)).art("might"));
        // How it shows itself.
        icons.put(MOOD_SLOT, Icon.of(Material.RED_WOOL, "Mood & heart", RED,
                List.of("Make it wrathful, joyful, ominous...", "or calm what it has come to feel."),
                Action.open(Page.MOOD)).art("mood"));
        icons.put(PRESENCE_SLOT, Icon.of(Material.CYAN_WOOL, "Presence", AQUA,
                List.of("Summon its body, have it follow you,", "vanish, or reveal its true form."),
                Action.open(Page.PRESENCE)).art("presence"));
        String look = s.look() == null ? "auto" : s.look();
        icons.put(LOOKS_SLOT, Icon.of(lookMaterial(look), "Looks", VIOLET,
                List.of("Now: " + (look.equals("auto") ? "by its nature" : look) + ".",
                        "Choose from " + (lookOrder().size() - 1) + " looks for its body."),
                Action.open(Page.LOOKS)).art("looks"));
        icons.put(VOICE_SLOT, Icon.of(Material.JUKEBOX, "Voice", AQUA,
                List.of("Choose how the god sounds", "in voice chat, and hear it."),
                Action.open(Page.VOICE)).art("voice"));
        // What it does.
        int waiting = s.pending().size();
        icons.put(COMMANDS_SLOT, Icon.of(Material.WRITABLE_BOOK, "Commands", YELLOW,
                List.of("Give it a command it must obey.", waiting == 0 ? "None pending." : waiting + " pending."),
                Action.open(Page.COMMAND)).glowing(waiting > 0).art("commands"));
        icons.put(PLAYERS_SLOT, Icon.of(Material.PLAYER_HEAD, "Players & memories", GREEN,
                List.of("Who it loves and who it hates:", "change favor, add or erase what it",
                        "remembers about each mortal."),
                Action.open(Page.PLAYERS)).head());
        List<MenuModel.Peer> peers = gods.peers() == null ? List.of() : gods.peers();
        if (!peers.isEmpty()) {
            List<String> bl = new ArrayList<>();
            bl.add("How it stands with the other");
            bl.add("gods of its world: allies,");
            bl.add("friends, rivals or enemies.");
            for (int i = 0; i < Math.min(3, peers.size()); i++) {
                MenuModel.Peer p = peers.get(i);
                bl.add(clip(p.name(), 16) + ": " + p.stance());
            }
            icons.put(BONDS_SLOT, Icon.of(Material.IRON_CHAIN, "Bonds", GOLD, bl, Action.open(Page.BONDS)).art("bonds"));
        }
        icons.put(WAKE_SLOT, Icon.of(Material.BELL, "Wake now", GOLD,
                List.of("It looks at its world right now."), Action.of(Kind.WAKE, null)).art("wake"));
        // On, off and away.
        icons.put(PAUSE_SLOT, s.paused()
                ? Icon.of(Material.LIME_CONCRETE, "Resume thoughts", GREEN,
                        List.of("Its thoughts are paused.", "Click to let it think again."),
                        Action.of(Kind.TOGGLE_PAUSE, null)).art("resume")
                : Icon.of(Material.ORANGE_CONCRETE, "Pause thoughts", GOLD,
                        List.of("Stop it thinking (powers and body", "still work). Click again to resume."),
                        Action.of(Kind.TOGGLE_PAUSE, null)).art("pause"));
        boolean off = s.setting("god.dormant", false);
        icons.put(DORMANT_SLOT, off
                ? Icon.of(Material.REDSTONE_TORCH, "Turn the god on", GREEN,
                        List.of("It is turned off: no thoughts,", "no body. Click to wake it again."),
                        Action.of(Kind.TOGGLE_DORMANT, null)).glowing(true).art("power_on")
                : Icon.of(Material.LEVER, "Turn the god off", RED,
                        List.of("No thoughts and no body until you", "turn it on. Its memory is kept."),
                        Action.of(Kind.TOGGLE_DORMANT, null)).art("power_off"));
        if (gods.removable()) {
            String name = s.born() ? s.godName() : "This god";
            icons.put(REMOVE_GOD_SLOT, Icon.of(Material.TNT, "Remove this god", RED,
                    List.of("Its thoughts stop and its body", "vanishes. Its memory is kept in",
                            "god/removed/. Click, then confirm."),
                    Action.of(Kind.REMOVE_GOD, null).confirming(name + " is removed for good (its memory is kept in "
                            + "god/removed/).")).art("remove"));
        } else if (s.born()) {
            // The first god cannot be removed; it can be erased and born anew.
            icons.put(REMOVE_GOD_SLOT, Icon.of(Material.TNT, "Erase this god", RED,
                    List.of("Its name, nature and every memory", "are wiped; a new god is born.",
                            "Click, then confirm."),
                    Action.of(Kind.PERSONA, "random").confirming(s.godName() + " is erased for good: its name, "
                            + "nature and every memory. A new god is born in its place.")).art("remove"));
        }
    }

    private static void personality(Map<Integer, Icon> icons, State s) {
        List<Archetype> list = s.archetypes();
        int max = Math.min(list.size(), 21);
        for (int i = 0; i < max; i++) {
            Archetype a = list.get(i);
            boolean current = s.born() && a.key().equalsIgnoreCase(s.archetype());
            List<String> lore = new ArrayList<>(wrap(a.description() == null ? "" : a.description(), 4));
            lore.add(current ? "This is the god now." : "Click, then confirm: a new god is");
            if (!current) lore.add("born and its memory starts fresh.");
            icons.put(INNER[i], Icon.of(wool(a.key(), i), title(a.key()), TabList.archetypeColor(a.key()).value(), lore,
                    Action.of(Kind.PERSONA, a.key()).confirming("A new " + a.key() + " god replaces the current one; "
                            + "its memory starts fresh."))
                    .glowing(current));
        }
        icons.put(42, Icon.of(Material.PAPER, "Keep it, just adjust?", 0xFF77FF,
                List.of("Every choice here makes a new god.", "To adjust this one and keep its",
                        "memories, go Back and open Nature."),
                Action.NONE));
        icons.put(40, Icon.of(Material.WHITE_WOOL, "Random", WHITE,
                List.of("A god of any archetype.", "Click, then confirm."),
                Action.of(Kind.PERSONA, "random").confirming("A new god of a random archetype replaces the current "
                        + "one; its memory starts fresh.")));
    }

    /** The mood buttons and their slots. */
    static final Map<VesselMood, Integer> MOOD_SLOTS = moodSlots();

    private static Map<VesselMood, Integer> moodSlots() {
        Map<VesselMood, Integer> m = new LinkedHashMap<>();
        m.put(VesselMood.WRATHFUL, 20);
        m.put(VesselMood.JOYFUL, 21);
        m.put(VesselMood.SORROWFUL, 22);
        m.put(VesselMood.OMINOUS, 23);
        m.put(VesselMood.EXALTED, 24);
        m.put(VesselMood.CALM, 31);
        return m;
    }

    private static void mood(Map<Integer, Icon> icons, State s) {
        for (Map.Entry<VesselMood, Integer> e : MOOD_SLOTS.entrySet()) {
            VesselMood m = e.getKey();
            boolean current = s.mood() == m || (m == VesselMood.CALM && s.mood() == null);
            List<String> lore = new ArrayList<>();
            if (m == VesselMood.CALM) {
                lore.add("Lifts any mood you imposed:");
                lore.add("it acts by its own nature again.");
            } else {
                lore.add("For a while its body shows it and");
                lore.add("it must let the mood colour every");
                lore.add("word and act (a binding command).");
            }
            if (current) lore.add("Its mood now.");
            if (m == VesselMood.CALM) lore.add("(Its lasting feelings stay.)");
            icons.put(e.getValue(), Icon.of(moodMaterial(m), m == VesselMood.CALM ? "Calm the god" : "Make " + m.id(),
                    m == VesselMood.CALM ? GREEN : TabList.nameColor(null, m).value(), lore, Action.of(Kind.MOOD, m.id())).glowing(current));
        }

        List<String> heart = new ArrayList<>();
        heart.add("What it has felt lately: feelings");
        heart.add("that build from what mortals do,");
        heart.add("or that it chose, and that fade.");
        List<String> now = s.heart() == null ? List.of() : s.heart();
        if (now.isEmpty()) heart.add("  at peace.");
        for (String line : now) heart.add("  " + line);
        // What moved it, the sky over its world and the memory that last came back to it.
        for (String line : s.aware() == null ? List.<String>of() : s.aware()) heart.addAll(wrap(line, 3));
        heart.add("Click to let all of it go.");
        icons.put(HEART_SLOT, Icon.of(Material.HEART_OF_THE_SEA, "Calm its heart", 0xFF88AA, heart,
                Action.of(Kind.CALM_HEART, null)).glowing(!now.isEmpty()));
    }

    static final int HEART_SLOT = 32;

    // ---- Advanced ---------------------------------------------------------------------------------

    /** Slots for the settings of one tab (three inner rows). */
    static final int[] TUNE_SLOTS = { 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42,
            43 };

    private static void advanced(Map<Integer, Icon> icons, State s) {
        MenuModel.Tuning t = s.tuning() == null ? new MenuModel.Tuning("Mind", Map.of()) : s.tuning();
        String tab = Advanced.TABS.contains(t.tab()) ? t.tab() : "Mind";
        for (int i = 0; i < Advanced.TABS.size(); i++) {
            String name = Advanced.TABS.get(i);
            boolean on = name.equals(tab);
            icons.put(10 + i, Icon.of(Advanced.tabIcon(name), name, on ? GOLD : GRAY,
                    List.of(Advanced.tabHelp(name), on ? "Shown below." : "Click to show."),
                    Action.of(Kind.TAB, name)).glowing(on));
        }
        List<Advanced.Option> list = Advanced.of(tab);
        for (int i = 0; i < list.size() && i < TUNE_SLOTS.length; i++) {
            Advanced.Option o = list.get(i);
            String v = t.values().get(o.key());
            String value = v == null || v.isBlank() ? o.fallback() : v;
            boolean changed = !value.equalsIgnoreCase(o.fallback());
            List<String> lore = new ArrayList<>(wrap(o.help(), 3));
            lore.add("Default: " + Advanced.show(o, o.fallback()));
            lore.add(Advanced.controls(o));
            lore.add(o.restart() ? "Takes hold after a restart." : "Takes hold at once.");
            int color = o.type() == Advanced.Type.BOOL ? (Boolean.parseBoolean(value) ? GREEN : RED) : VIOLET;
            icons.put(TUNE_SLOTS[i], Icon.of(o.icon(), o.label() + ": " + Advanced.show(o, value), color, lore,
                    Action.of(Kind.TUNE, o.key())).glowing(changed));
        }
    }

    // ---- Voice ------------------------------------------------------------------------------------

    /** Kokoro voices offered on the Voice page, in slot order, with a short description. */
    static final Map<Integer, String[]> VOICE_SLOTS = Map.ofEntries(
            Map.entry(10, new String[] { "am_michael", "Warm, deep American man." }),
            Map.entry(11, new String[] { "am_onyx", "Dark, low American man." }),
            Map.entry(12, new String[] { "am_fenrir", "Rough, strong American man." }),
            Map.entry(13, new String[] { "am_adam", "Plain American man." }),
            Map.entry(14, new String[] { "am_eric", "Calm American man." }),
            Map.entry(15, new String[] { "bm_george", "Grave old British man." }),
            Map.entry(16, new String[] { "bm_lewis", "Rich British man." }),
            Map.entry(19, new String[] { "af_heart", "Clear, warm American woman." }),
            Map.entry(20, new String[] { "af_bella", "Bright American woman." }),
            Map.entry(21, new String[] { "af_nicole", "Soft, whispering woman." }),
            Map.entry(22, new String[] { "af_sky", "Light American woman." }),
            Map.entry(23, new String[] { "bf_emma", "Gentle British woman." }),
            Map.entry(24, new String[] { "bf_isabella", "Elegant British woman." }),
            Map.entry(25, new String[] { "bm_fable", "Storyteller British man." }));

    /** Voice effects, in slot order (auto and none are on the bottom row). */
    static final Map<Integer, String> EFFECT_SLOTS = Map.ofEntries(
            Map.entry(28, "majestic"), Map.entry(29, "divine"), Map.entry(30, "booming"),
            Map.entry(31, "commanding"), Map.entry(32, "demonic"), Map.entry(33, "abyssal"),
            Map.entry(34, "ethereal"), Map.entry(39, "none"), Map.entry(40, "auto"));

    static final int SAMPLE_SLOT = 41;

    static String effectText(String e) {
        return switch (e) {
            case "majestic" -> "A king's voice in a great hall.";
            case "divine" -> "Warm and vast, from everywhere.";
            case "booming" -> "From the sky: huge and echoing.";
            case "commanding" -> "Close, hard and certain.";
            case "demonic" -> "Low, doubled and menacing.";
            case "abyssal" -> "From a deep pit: dark and vast.";
            case "ethereal" -> "Airy, shimmering, many-voiced.";
            case "none" -> "The plain voice, no effect.";
            default -> "Follows its mood: demonic in";
        };
    }

    static Material effectMaterial(String e) {
        return switch (e) {
            case "majestic" -> Material.BELL;
            case "divine" -> Material.TOTEM_OF_UNDYING;
            case "booming" -> Material.GOAT_HORN;
            case "commanding" -> Material.IRON_SWORD;
            case "demonic" -> Material.WITHER_SKELETON_SKULL;
            case "abyssal" -> Material.SCULK_SHRIEKER;
            case "ethereal" -> Material.PHANTOM_MEMBRANE;
            case "none" -> Material.PAPER;
            default -> Material.CLOCK;
        };
    }

    private static final Material[] DISCS = { Material.MUSIC_DISC_13, Material.MUSIC_DISC_CAT,
            Material.MUSIC_DISC_BLOCKS, Material.MUSIC_DISC_CHIRP, Material.MUSIC_DISC_FAR, Material.MUSIC_DISC_MALL,
            Material.MUSIC_DISC_MELLOHI, Material.MUSIC_DISC_STAL, Material.MUSIC_DISC_STRAD, Material.MUSIC_DISC_WARD,
            Material.MUSIC_DISC_WAIT, Material.MUSIC_DISC_OTHERSIDE, Material.MUSIC_DISC_PIGSTEP, Material.MUSIC_DISC_5 };

    private static void voice(Map<Integer, Icon> icons, State s) {
        MenuModel.VoiceNow v = s.voice() == null ? new MenuModel.VoiceNow("kokoro", "am_michael", "auto") : s.voice();
        boolean kokoro = !"piper".equals(v.engine()) && !"off".equals(v.engine());
        int i = 0;
        for (Map.Entry<Integer, String[]> e : new java.util.TreeMap<>(VOICE_SLOTS).entrySet()) {
            String id = e.getValue()[0];
            boolean current = id.equalsIgnoreCase(v.voice());
            List<String> lore = new ArrayList<>();
            lore.add(e.getValue()[1]);
            lore.add(current ? "The god speaks with this now." : "Click: speak with it, hear it.");
            if (!kokoro) lore.add("Needs voice.tts.engine: kokoro.");
            icons.put(e.getKey(), Icon.of(DISCS[i++ % DISCS.length], "Voice: " + id, AQUA, lore,
                    Action.of(Kind.VOICE, id)).glowing(current));
        }
        String effect = v.effect() == null ? "auto" : v.effect();
        for (Map.Entry<Integer, String> e : new java.util.TreeMap<>(EFFECT_SLOTS).entrySet()) {
            String fx = e.getValue();
            boolean current = fx.equals(effect);
            List<String> lore = new ArrayList<>();
            lore.add(effectText(fx));
            if (fx.equals("auto")) lore.add("wrath, commanding when ominous.");
            lore.add(current ? "Its voice sounds like this now." : "Click: use it, hear it.");
            icons.put(e.getKey(), Icon.of(effectMaterial(fx), fx.equals("auto") ? "Effect: by its mood"
                    : "Effect: " + fx, VIOLET, lore, Action.of(Kind.VOICE_EFFECT, fx)).glowing(current));
        }
        icons.put(SAMPLE_SLOT, Icon.of(Material.NOTE_BLOCK, "Hear it now", GOLD,
                List.of("The god says a line to you.", "Needs the Simple Voice Chat mod."),
                Action.of(Kind.VOICE_SAMPLE, null)));
    }

    // ---- Nature (personality dials) -------------------------------------------------------------

    /** What a dial means at its low and high ends (each under 33 characters). */
    static final Map<String, String[]> DIAL_ENDS = Map.ofEntries(
            Map.entry("generosity", new String[] { "Stingy: rarely grants a wish.", "Lenient: gives freely." }),
            Map.entry("wrath", new String[] { "Forgiving, slow to punish.", "Quick to anger and to smite." }),
            Map.entry("chattiness", new String[] { "Silent: speaks rarely.", "Talks often and at length." }),
            Map.entry("humor", new String[] { "Solemn and serious.", "Jokes, teases and plays." }),
            Map.entry("meddling", new String[] { "Watches; acts when called.", "Meddles in everything." }),
            Map.entry("patience", new String[] { "Snaps at the second request.", "Tolerates endless asking." }),
            Map.entry("mercy", new String[] { "Harsh, lasting punishments.", "Light punishment; forgives." }),
            Map.entry("pride", new String[] { "Humble; shrugs off insults.", "Every slight is an affront." }),
            Map.entry("curiosity", new String[] { "Ignores what mortals do.", "Studies mortals, asks things." }),
            Map.entry("vanity", new String[] { "Indifferent to worship.", "Craves praise and offerings." }));

    static final int DIAL_RESET_SLOT = 49 - 9;

    /** Where dial i sits: two rows of five (rows 1 and 3), centred. At most 10 dials. */
    static int dialSlot(int i) {
        int row = i < 5 ? 1 : 3;
        return row * 9 + 2 + (i % 5);
    }

    static String bar(int v) {
        return "■".repeat(Math.max(0, v)) + "□".repeat(Math.max(0, 10 - v)) + " " + v + "/10";
    }

    static Material dialMaterial(String key) {
        return switch (key) {
            case "generosity" -> Material.EMERALD;
            case "wrath" -> Material.BLAZE_POWDER;
            case "chattiness" -> Material.BOOK;
            case "humor" -> Material.COOKIE;
            case "meddling" -> Material.PISTON;
            case "patience" -> Material.CLOCK;
            case "mercy" -> Material.GOLDEN_APPLE;
            case "pride" -> Material.GOLDEN_HELMET;
            case "curiosity" -> Material.SPYGLASS;
            case "vanity" -> Material.GOLD_INGOT;
            default -> Material.COMPASS;
        };
    }

    private static void dials(Map<Integer, Icon> icons, State s) {
        List<MenuModel.Dial> list = s.dials() == null ? List.of() : s.dials();
        if (!s.born() || list.isEmpty()) {
            icons.put(22, Icon.of(Material.GRAY_DYE, "Not yet born", GRAY,
                    List.of("Its nature is set when it is", "born; come back once it wakes."), Action.NONE));
            return;
        }
        int n = Math.min(10, list.size());
        for (int i = 0; i < n; i++) {
            MenuModel.Dial d = list.get(i);
            String[] ends = DIAL_ENDS.getOrDefault(d.key(), new String[] { "Low.", "High." });
            List<String> lore = new ArrayList<>();
            lore.add(bar(d.value()));
            lore.add("0: " + ends[0]);
            lore.add("10: " + ends[1]);
            lore.add(d.value() == d.base() ? "As its archetype made it." : "Its archetype gave it " + d.base() + ".");
            lore.add("Left-click +1, right-click -1.");
            lore.add("Shift-click: its archetype's.");
            icons.put(dialSlot(i), Icon.of(dialMaterial(d.key()), title(d.key()) + " " + d.value(), VIOLET, lore,
                    Action.of(Kind.DIAL, d.key() + ":1")).glowing(d.value() != d.base()));
        }
        boolean changed = list.stream().anyMatch(d -> d.value() != d.base());
        icons.put(DIAL_RESET_SLOT, Icon.of(Material.RECOVERY_COMPASS, "Back to its archetype", GOLD,
                changed ? List.of("Every dial returns to where its", "archetype set it.", "Same god, same memories.")
                        : List.of("Nothing has been changed."),
                Action.of(Kind.DIAL, "reset")));
    }

    /**
     * A click on a dial: left adds one, right takes one away, shift returns that dial to its
     * archetype's value. Other icons ignore the button.
     */
    public static Decision click(Menu m, int slot, Action waiting, boolean right, boolean shift) {
        Decision d = click(m, slot, waiting);
        Action a = d.run();
        if (a != null && a.kind() == Kind.TUNE && a.arg() != null) {
            return new Decision(Action.of(Kind.TUNE, a.arg() + "|" + (right ? "R" : "L") + (shift ? "S" : "")),
                    d.open(), null);
        }
        if (a != null && a.kind() == Kind.BOND && a.arg() != null && a.arg().endsWith("|+20") && right) {
            return new Decision(Action.of(Kind.BOND, a.arg().substring(0, a.arg().length() - 4) + "|-20"), d.open(),
                    null);
        }
        if (a == null || a.kind() != Kind.DIAL || a.arg() == null || !a.arg().endsWith(":1")) return d;
        String key = a.arg().substring(0, a.arg().length() - 2);
        String change = shift ? "reset" : right ? "-1" : "1";
        return new Decision(Action.of(Kind.DIAL, key + ":" + change), d.open(), null);
    }

    /** A dial after a click: "generosity:1" on 9 → 10; reset → base. Clamped to 0..10. */
    public static int dialAfter(int value, int base, String change) {
        if (change == null) return value;
        if (change.equals("reset")) return base;
        try {
            return Math.max(0, Math.min(10, value + Integer.parseInt(change.trim())));
        } catch (NumberFormatException e) {
            return value;
        }
    }

    /** The Looks page: "auto" first, then every look, in the inner slots. */
    static List<String> lookOrder() {
        List<String> out = new ArrayList<>();
        out.add("auto");
        out.addAll(net.bigyous.gptgodmc.god.vessel.VesselLook.NAMES);
        return out;
    }

    static Material lookMaterial(String look) {
        return switch (look) {
            case "shade" -> Material.GRAY_CONCRETE;
            case "radiant" -> Material.GLOWSTONE;
            case "infernal" -> Material.MAGMA_BLOCK;
            case "void" -> Material.PURPLE_CONCRETE;
            case "thunder" -> Material.LIGHTNING_ROD;
            case "allfather" -> Material.FEATHER;
            case "tide" -> Material.PRISMARINE;
            case "underworld" -> Material.SOUL_LANTERN;
            case "warrior" -> Material.GOLDEN_HELMET;
            case "sun" -> Material.SUNFLOWER;
            case "jackal" -> Material.GOLD_BLOCK;
            case "grove" -> Material.OAK_SAPLING;
            case "frost" -> Material.BLUE_ICE;
            case "storm" -> Material.TRIDENT;
            case "plague" -> Material.BONE;
            case "celestial" -> Material.NETHER_STAR;
            case "clockwork" -> Material.CLOCK;
            case "jester" -> Material.CARVED_PUMPKIN;
            case "moon" -> Material.WHITE_STAINED_GLASS;
            case "titan" -> Material.FIRE_CHARGE;
            case "abyss" -> Material.SCULK;
            case "hollow" -> Material.WITHER_SKELETON_SKULL;
            case "prism" -> Material.AMETHYST_CLUSTER;
            case "bloom" -> Material.FLOWERING_AZALEA;
            default -> Material.ARMOR_STAND;
        };
    }

    private static void looks(Map<Integer, Icon> icons, State s) {
        String look = s.look() == null ? "auto" : s.look();
        List<String> order = lookOrder();
        for (int i = 0; i < order.size() && i < INNER.length; i++) {
            String l = order.get(i);
            boolean current = l.equals(look);
            List<String> lore = new ArrayList<>(wrap(lookText(l), 2));
            lore.add(current ? "Its body wears this now." : "Click: its body changes at once.");
            icons.put(INNER[i], Icon.of(lookMaterial(l), l.equals("auto") ? "By its nature" : title(l), VIOLET, lore,
                    Action.of(Kind.LOOK, l)).glowing(current));
        }
    }

    static String lookText(String look) {
        if (look.equals("auto")) return "Whatever suits its name and archetype.";
        String d = net.bigyous.gptgodmc.god.vessel.VesselLook.description(look);
        return d == null || d.isBlank() ? look : d;
    }

    static Material moodMaterial(VesselMood m) {
        return switch (m) {
            case WRATHFUL -> Material.RED_CONCRETE;
            case JOYFUL -> Material.YELLOW_CONCRETE;
            case SORROWFUL -> Material.LIGHT_BLUE_CONCRETE;
            case OMINOUS -> Material.BLACK_CONCRETE;
            case EXALTED -> Material.WHITE_CONCRETE;
            case CALM -> Material.LIME_CONCRETE;
        };
    }

    /** The standing command a mood button gives. */
    public static String moodCommand(VesselMood m, int minutes) {
        String span = "For the next " + minutes + " minutes you are ";
        return switch (m) {
            case WRATHFUL -> span + "WRATHFUL: your patience is spent. Let fury colour every word and act; meet "
                    + "insolence and sin swiftly and harshly with your powers.";
            case JOYFUL -> span + "JOYFUL: the world delights you. Let warmth and generosity colour every word and "
                    + "act; praise, bless and play with the mortals.";
            case SORROWFUL -> span + "SORROWFUL: you grieve. Speak softly and sadly, act sparingly, and let mourning "
                    + "colour all you do.";
            case OMINOUS -> span + "OMINOUS: silent and menacing. Speak little, in cold portents and threats, and let "
                    + "dread colour every act.";
            case EXALTED -> span + "EXALTED: in the fullness of your glory. Speak with majesty and show your power in "
                    + "grand, awe-inspiring deeds.";
            case CALM -> "You are calm again: act by your own nature.";
        };
    }

    /** The settings map key that marks the true form's gaze (kill, smite, blind) in the menu state. */
    public static String gazeKey(String gaze) {
        return "vessel.true-form.gaze:" + gaze;
    }

    /** What the true form's gaze does by default: kill, smite or blind. */
    static String gaze(State s) {
        for (String g : List.of("kill", "seize", "smite", "blind")) if (s.setting(gazeKey(g), false)) return g;
        return s.setting("vessel.true-form.lethal", true) ? "kill" : "blind";
    }

    private static void presence(Map<Integer, Icon> icons, State s) {
        boolean bodyOn = s.setting("vessel.enabled", true);
        String off = bodyOn ? null : "Bodies are off (Quick switches).";
        icons.put(20, Icon.of(Material.PLAYER_HEAD, "Summon its body to me", AQUA,
                lore("Its vessel appears beside you.", off), Action.of(Kind.SUMMON, null)).head());
        icons.put(21, Icon.of(Material.FEATHER, "Fly to me", AQUA,
                lore("It soars over the land to you.", off), Action.of(Kind.FLY_TO, null)));
        icons.put(22, Icon.of(Material.LEAD, "Follow me", AQUA, lore("Its vessel walks after you.", off),
                Action.of(Kind.FOLLOW, null)));
        icons.put(24, Icon.of(Material.GLASS, "Vanish", GRAY, List.of("Its body fades; it watches unseen."),
                Action.of(Kind.VANISH, null)));
        boolean allowed = s.setting("vessel.true-form.enabled", true) && bodyOn;
        String gaze = gaze(s);
        List<String> tf = new ArrayList<>();
        tf.add("Its true form unfolds above you.");
        switch (gaze) {
            case "smite" -> {
                tf.add("Whoever looks is hurled back and");
                tf.add("struck down (never killed):");
            }
            case "blind" -> tf.add("Whoever looks at it is blinded:");
            case "seize" -> {
                tf.add("Whoever looks is taken up by its");
                tf.add("light and unmade, you too:");
            }
            default -> tf.add("Whoever looks at it DIES, you too:");
        }
        tf.add("look away. Rare: it has a cooldown.");
        if (!allowed) tf.add("Forbidden in Quick switches.");
        icons.put(30, Icon.of(allowed ? Material.CRYING_OBSIDIAN : Material.GRAY_DYE,
                "Reveal true form: " + gaze, 0xB070FF,
                tf, Action.of(Kind.TRUE_FORM, null).confirming("The true form unfolds above you"
                        + switch (gaze) {
                            case "smite" -> " and strikes down whoever looks at it.";
                            case "blind" -> " and blinds whoever looks at it.";
                            case "seize" -> " and takes up and unmakes whoever looks at it.";
                            default -> " and kills whoever looks at it.";
                        }))
                .glowing(s.trueForm()));
        icons.put(32, Icon.of(Material.OBSIDIAN, "End true form", GRAY,
                List.of(s.trueForm() ? "Fold its true form away now." : "Its true form is not revealed."),
                Action.of(Kind.END_TRUE_FORM, null)));
    }

    private static void command(Map<Integer, Icon> icons, State s) {
        icons.put(10, Icon.of(Material.WRITABLE_BOOK, "Deliver a command", YELLOW,
                List.of("Closes this menu: type the command", "in chat (only the god hears it).",
                        "Type cancel to stop. It must obey."),
                Action.of(Kind.DELIVER, null)).glowing(true));
        for (int i = 0; i < PRESETS.size(); i++) {
            Preset p = PRESETS.get(i);
            List<String> lore = new ArrayList<>(wrap("\"" + p.command() + "\"", 5));
            lore.add(p.confirm() ? "Click, then confirm." : "Click to command it.");
            Action a = Action.of(Kind.PRESET, p.id());
            if (p.confirm()) a = a.confirming("The god must: " + p.command());
            icons.put(11 + i, Icon.of(p.material(), p.name(), GOLD, lore, a));
        }
        List<Pending> pending = s.pending();
        icons.put(22, Icon.of(Material.BOOK, "Pending commands: " + pending.size(), WHITE,
                List.of(pending.isEmpty() ? "None: the god owes you nothing." : "Below. Click one to cancel it."),
                Action.NONE));
        int[] slots = { 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43 };
        for (int i = 0; i < Math.min(pending.size(), slots.length); i++) {
            Pending p = pending.get(i);
            List<String> lore = new ArrayList<>(wrap(p.text(), 5));
            lore.add(p.status());
            lore.add("Click to cancel it.");
            icons.put(slots[i], Icon.of(p.standing() ? Material.CLOCK : Material.PAPER, "#" + p.number()
                    + (p.standing() ? " (standing)" : ""), WHITE, lore,
                    Action.of(Kind.CANCEL_COMMAND, String.valueOf(p.number()))));
        }
    }

    private static void settings(Map<Integer, Icon> icons, State s) {
        icons.put(INNER[INNER.length - 1], Icon.of(Material.COMMAND_BLOCK, "More: Server config", VIOLET,
                List.of("Every setting, in 8 tabs: gods,", "Godslayer, voice, ports, logs."),
                Action.open(Page.ADVANCED)).glowing(true).art("config"));
        for (int i = 0; i < SETTINGS.size() && i < INNER.length; i++) {
            Setting set = SETTINGS.get(i);
            boolean on = s.setting(set.key(), set.fallback());
            List<String> lore = new ArrayList<>(wrap(set.description(), 3));
            lore.add(set.key());
            lore.add(on ? "Click to turn OFF." : "Click to turn ON.");
            icons.put(INNER[i], Icon.of(on ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                    set.label() + ": " + (on ? "ON" : "OFF"), on ? GREEN : RED, lore,
                    Action.of(Kind.TOGGLE_SETTING, set.key())));
        }
    }

    // ---- Players and memories ------------------------------------------------------------------

    /** Mortals per page of the Players list (the 28 inner slots). */
    public static final int PER_PAGE = 28;
    public static final int PREV_SLOT = 46;
    public static final int NEXT_SLOT = 52;
    public static final int NAME_SLOT = 53;
    /** The favor buttons on a mortal's page: slot → "set:N" or "add:N". */
    static final Map<Integer, String> FAVOR_BUTTONS = favorButtons();

    private static Map<Integer, String> favorButtons() {
        Map<Integer, String> m = new LinkedHashMap<>();
        m.put(19, "set:-10");
        m.put(20, "add:-5");
        m.put(21, "add:-1");
        m.put(22, "set:0");
        m.put(23, "add:1");
        m.put(24, "add:5");
        m.put(25, "set:10");
        return m;
    }

    /** The favor a "set:N" / "add:N" button leads to from the current favor (clamped to -10..10). */
    public static int favorAfter(int current, String button) {
        int n = Integer.parseInt(button.substring(4));
        int v = button.startsWith("set:") ? n : current + n;
        return Math.max(-10, Math.min(10, v));
    }

    static int favorColor(int favor) {
        if (favor <= -6) return 0xAA0000;
        if (favor <= -3) return RED;
        if (favor < 3) return WHITE;
        if (favor < 6) return YELLOW;
        return GOLD;
    }

    static String favorText(int favor) {
        return (favor > 0 ? "+" : "") + favor + " (" + net.bigyous.gptgodmc.god.Memory.favorLabel(favor) + ")";
    }

    private static void players(Map<Integer, Icon> icons, State s) {
        List<MenuModel.Mortal> all = s.mortals();
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int page = Math.max(0, Math.min(s.listPage(), pages - 1));
        if (all.isEmpty()) {
            icons.put(22, Icon.of(Material.BOOK, "No mortals yet", WHITE,
                    List.of("The god keeps a record on every", "mortal it notices. None so far.",
                            "Name one below to start a record."),
                    Action.NONE));
        }
        for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < all.size(); i++) {
            MenuModel.Mortal m = all.get(page * PER_PAGE + i);
            List<String> lore = new ArrayList<>();
            lore.add("Favor " + favorText(m.favor()));
            if (m.title() != null && !m.title().isBlank()) lore.add("Title: " + m.title());
            lore.add(m.online() ? "Online now" : m.seen() == null ? "Not seen lately" : "Last seen " + m.seen() + " ago");
            lore.add(m.notes().size() + " notes, " + m.deeds().size() + " deeds"
                    + (m.summary() != null && !m.summary().isBlank() ? ", a remembered past" : ""));
            lore.add("Click to change how it feels");
            lore.add("about them and what it remembers.");
            icons.put(INNER[i], Icon.of(Material.PLAYER_HEAD, m.name(), favorColor(m.favor()), lore,
                    Action.of(Kind.SELECT_PLAYER, m.name())).headOf(m.name()).glowing(m.online()));
        }
        if (page > 0) {
            icons.put(PREV_SLOT, Icon.of(Material.SPECTRAL_ARROW, "Previous page", WHITE,
                    List.of("Page " + page + " of " + pages + "."), Action.of(Kind.PLAYERS_PAGE, String.valueOf(page - 1))));
        }
        if (page < pages - 1) {
            icons.put(NEXT_SLOT, Icon.of(Material.SPECTRAL_ARROW, "Next page", WHITE,
                    List.of("Page " + (page + 2) + " of " + pages + "."),
                    Action.of(Kind.PLAYERS_PAGE, String.valueOf(page + 1))));
        }
        icons.put(NAME_SLOT, Icon.of(Material.NAME_TAG, "Name a mortal", GREEN,
                List.of("Closes the menu: type a player's", "name in chat to open their record,",
                        "even one the god has never met."),
                Action.of(Kind.NAME_PLAYER, null)));
    }

    private static void player(Map<Integer, Icon> icons, State s) {
        MenuModel.Mortal m = s.selected();
        if (m == null) {
            icons.put(22, Icon.of(Material.BARRIER, "No mortal chosen", RED, List.of("Go back and pick one."),
                    Action.open(Page.PLAYERS)));
            return;
        }
        String who = m.name();
        List<String> dossier = new ArrayList<>();
        dossier.add("Favor " + favorText(m.favor()));
        if (m.title() != null && !m.title().isBlank()) dossier.add("Title: " + m.title());
        dossier.add(m.online() ? "Online now" : m.seen() == null ? "Not seen lately" : "Last seen " + m.seen() + " ago");
        if (m.offerings() > 0) dossier.add(m.offerings() + " offerings made");
        if (m.summary() != null && !m.summary().isBlank()) {
            dossier.add("Their past, as it remembers it:");
            dossier.addAll(wrap(m.summary(), 5));
        }
        icons.put(10, Icon.of(Material.PLAYER_HEAD, who, favorColor(m.favor()), dossier, Action.NONE).headOf(who)
                .glowing(m.online()));

        icons.put(12, Icon.of(Material.WRITABLE_BOOK, "Add a note", GREEN,
                wrap("Closes the menu: type a thought the god will hold about " + who + " in chat. It reads it "
                        + "as its own memory, e.g. \"He mocked you; never forgive him.\""),
                Action.of(Kind.ADD_NOTE, who)));
        icons.put(13, Icon.of(Material.WRITTEN_BOOK, "Record a deed", GREEN,
                wrap("Closes the menu: type something " + who + " did, in chat. It is kept, dated today, among "
                        + "their deeds."),
                Action.of(Kind.ADD_DEED, who)));
        icons.put(14, Icon.of(Material.SHEARS, "Clear notes", GOLD,
                wrap("Erase all " + m.notes().size() + " of its notes about " + who + ". Click, then confirm."),
                Action.of(Kind.CLEAR_NOTES, who).confirming("The god forgets all its notes about " + who + ".")));
        icons.put(15, Icon.of(Material.FLINT_AND_STEEL, "Clear deeds", GOLD,
                wrap("Erase " + who + "'s " + m.deeds().size() + " deeds and the summary of their past. Click, "
                        + "then confirm."),
                Action.of(Kind.CLEAR_DEEDS, who).confirming("The god forgets what " + who + " did and their past.")));
        icons.put(16, Icon.of(Material.LAVA_BUCKET, "Forget " + who, RED,
                List.of("Erase everything: favor, title,", "notes, deeds, offerings, words.",
                        "(Its annals, the permanent log,", "still record what happened.)", "Click, then confirm."),
                Action.of(Kind.FORGET_PLAYER, who).confirming("The god forgets " + who + " entirely.")));

        for (Map.Entry<Integer, String> e : FAVOR_BUTTONS.entrySet()) {
            String b = e.getValue();
            int to = favorAfter(m.favor(), b);
            int n = Integer.parseInt(b.substring(4));
            String name = switch (b) {
                case "set:-10" -> "Despise them";
                case "set:0" -> "Neutral";
                case "set:10" -> "Make them chosen";
                default -> (n > 0 ? "+" : "") + n + " favor";
            };
            Material mat = switch (b) {
                case "set:-10" -> Material.REDSTONE_BLOCK;
                case "add:-5" -> Material.RED_WOOL;
                case "add:-1" -> Material.PINK_WOOL;
                case "set:0" -> Material.WHITE_WOOL;
                case "add:1" -> Material.LIME_WOOL;
                case "add:5" -> Material.GREEN_WOOL;
                default -> Material.GOLD_BLOCK;
            };
            List<String> lore = new ArrayList<>();
            lore.add("Now: " + favorText(m.favor()));
            lore.add("After: " + favorText(to));
            lore.add(switch (b) {
                case "set:-10" -> "It owes them not even an answer.";
                case "set:10" -> "Dearest to it of all mortals.";
                case "set:0" -> "It sets aside what it felt.";
                default -> "Click to change its favor.";
            });
            icons.put(e.getKey(), Icon.of(mat, name, favorColor(to), lore, Action.of(Kind.FAVOR, b))
                    .glowing(b.startsWith("set:") && to == m.favor()));
        }

        entries(icons, 28, m.notes(), Material.PAPER, "Note", "No notes", Kind.DELETE_NOTE, who);
        entries(icons, 37, m.deeds(), Material.MAP, "Deed", "No deeds", Kind.DELETE_DEED, who);
    }

    /** Up to 7 notes or deeds in one row, newest first; clicking one (then confirm) erases it. */
    private static void entries(Map<Integer, Icon> icons, int firstSlot, List<MenuModel.Entry> list, Material mat,
            String what, String none, Kind kind, String who) {
        if (list.isEmpty()) {
            icons.put(firstSlot + 3, Icon.of(Material.GRAY_DYE, none, GRAY,
                    List.of("It remembers no " + what.toLowerCase(Locale.ROOT) + "s about " + who + "."), Action.NONE));
            return;
        }
        for (int i = 0; i < Math.min(7, list.size()); i++) {
            MenuModel.Entry e = list.get(i);
            List<String> lore = new ArrayList<>(wrap(e.text(), 5));
            lore.add("Click, then confirm, to erase it.");
            String shortText = e.text().length() > 60 ? e.text().substring(0, 59) + "…" : e.text();
            icons.put(firstSlot + i, Icon.of(mat, what + (e.age() == null ? "" : ", " + e.age() + " ago"), WHITE, lore,
                    Action.of(kind, e.at() + "|" + e.text()).confirming("The god forgets: \"" + shortText + "\"")));
        }
    }

    // ---- Clicks --------------------------------------------------------------------------------

    /** What a click on `slot` means, given the click (if any) waiting for confirmation. */
    public static Decision click(Menu menu, int slot, Action waiting) {
        Icon icon = menu.at(slot);
        if (icon == null || icon.action() == null || icon.action().kind() == Kind.NONE) {
            return new Decision(null, null, waiting);
        }
        Action a = icon.action();
        return switch (a.kind()) {
            case CONFIRM -> new Decision(waiting == null ? null : Action.of(waiting.kind(), waiting.arg()),
                    waiting != null && waiting.kind() == Kind.FORGET_PLAYER ? Page.PLAYERS
                            : waiting != null && waiting.kind() == Kind.REMOVE_GOD ? Page.HUB : null,
                    null);
            case SELECT_GOD -> new Decision(a, Page.MAIN, null);
            case CANCEL_CONFIRM -> new Decision(null, null, null);
            case OPEN -> new Decision(null, Page.valueOf(a.arg().toUpperCase(Locale.ROOT)), null);
            case SELECT_PLAYER -> new Decision(a, Page.PLAYER, null);
            case PLAYERS_PAGE -> new Decision(a, Page.PLAYERS, null);
            default -> a.confirm() != null ? new Decision(null, null, a) : new Decision(a, null, null);
        };
    }

    // ---- Might & Mind, New god -------------------------------------------------------------------

    /** The models the Might & Mind page offers besides the server-wide one. */
    public static final List<String> MODELS = List.of("claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5");
    static final Map<Integer, String> MODEL_SLOTS = Map.of(19, "default", 21, MODELS.get(0), 23, MODELS.get(1),
            25, MODELS.get(2));
    static final Map<Integer, String> EFFORT_SLOTS = Map.of(29, "default", 30, "auto", 31, "low", 32, "medium",
            33, "high");
    static final Map<Integer, String> POWER_SLOTS = Map.of(11, "lesser", 13, "greater", 15, "supreme");
    /** A god's page: remove (or erase) it, and turn it off or on. */
    public static final int REMOVE_GOD_SLOT = 42;
    public static final int DORMANT_SLOT = 40;

    static String shortModel(String model) {
        if (model == null) return "?";
        String m = model.replace("claude-", "");
        return m.replaceFirst("^([a-z]+)-(\\d+)-(\\d+)$", "$1 $2.$3");
    }

    static Material powerMaterial(String power) {
        return switch (power == null ? "" : power) {
            case "lesser" -> Material.COPPER_INGOT;
            case "greater" -> Material.GOLD_INGOT;
            default -> Material.DIAMOND;
        };
    }

    static String powerText(String power) {
        return switch (power == null ? "" : power) {
            case "lesser" -> "A minor spirit: gentle powers only, and it hears only prayers made to it.";
            case "greater" -> "Mighty but not all-knowing: no meteor, earthquake, true form, weather, time or "
                    + "server commands; it overhears other gods' prayers only near its body.";
            default -> "Omniscient and almighty: every power, and it overhears every prayer made in its world.";
        };
    }

    /** The New god page: archetype (row 1), power (row 3), name (row 4), then Create. */
    static final int DRAFT_NAME_SLOT = 38, DRAFT_NO_NAME_SLOT = 39, DRAFT_CREATE_SLOT = 42, DRAFT_RANDOM_SLOT = 22;
    static final Map<Integer, String> DRAFT_POWER_SLOTS = Map.of(29, "lesser", 31, "greater", 33, "supreme");

    private static void create(Map<Integer, Icon> icons, State s) {
        MenuModel.Draft d = s.draftOrDefault();
        List<Archetype> kinds = s.archetypes();
        for (int i = 0; i < kinds.size() && i < 7; i++) {
            String k = kinds.get(i).key();
            boolean on = k.equalsIgnoreCase(d.archetype());
            List<String> lore = new ArrayList<>(wrap(kinds.get(i).description(), 3));
            lore.add(on ? "Chosen." : "Click to choose.");
            icons.put(10 + i, Icon.of(wool(k, i), title(k), TabList.archetypeColor(k).value(), lore,
                    Action.of(Kind.DRAFT_ARCHETYPE, k)).glowing(on));
        }
        boolean anyNature = "random".equalsIgnoreCase(d.archetype());
        icons.put(DRAFT_RANDOM_SLOT, Icon.of(Material.WHITE_WOOL, "Random nature", WHITE,
                List.of("Any nature, chosen at its birth.", anyNature ? "Chosen." : "Click to choose."),
                Action.of(Kind.DRAFT_ARCHETYPE, "random")).glowing(anyNature));
        for (Map.Entry<Integer, String> e : new java.util.TreeMap<>(DRAFT_POWER_SLOTS).entrySet()) {
            boolean on = e.getValue().equals(d.power());
            List<String> lore = new ArrayList<>(wrap(powerText(e.getValue()), 3));
            lore.add(on ? "Chosen." : "Click to choose.");
            icons.put(e.getKey(), Icon.of(powerMaterial(e.getValue()), title(e.getValue()) + " god", GOLD, lore,
                    Action.of(Kind.DRAFT_POWER, e.getValue())).glowing(on));
        }
        icons.put(DRAFT_NAME_SLOT, Icon.of(Material.NAME_TAG, d.name() == null ? "Give it a name" : "Name: " + d.name(),
                AQUA, List.of(d.name() == null ? "Click, then type just the name" : "Click to type another name.",
                        d.name() == null ? "in chat (one word)." : "One word, in chat."),
                Action.of(Kind.DRAFT_NAME, null)).glowing(d.name() != null));
        icons.put(DRAFT_NO_NAME_SLOT, Icon.of(Material.PAPER, "Let it name itself", WHITE,
                List.of("It invents its own name at birth.", d.name() == null ? "Chosen." : "Click to choose."),
                Action.of(Kind.DRAFT_NO_NAME, null)).glowing(d.name() == null));
        List<String> sum = new ArrayList<>();
        sum.add("Name: " + (d.name() == null ? "its own choice" : d.name()));
        sum.add("Nature: " + d.archetype());
        sum.add("Power: " + d.power());
        sum.add("Click to bring it into being.");
        icons.put(DRAFT_CREATE_SLOT, Icon.of(Material.EMERALD_BLOCK, "Create this god", GREEN, sum,
                Action.of(Kind.DRAFT_CREATE, null)).glowing(true));
    }

    private static void might(Map<Integer, Icon> icons, State s) {
        MenuModel.Gods g = s.pantheon();
        for (Map.Entry<Integer, String> e : new java.util.TreeMap<>(POWER_SLOTS).entrySet()) {
            String p = e.getValue();
            boolean current = p.equals(g.power());
            List<String> lore = new ArrayList<>(wrap(powerText(p), 4));
            lore.add(current ? "It is this now." : "Click to make it so.");
            icons.put(e.getKey(), Icon.of(powerMaterial(p), title(p) + " god", GOLD, lore,
                    Action.of(Kind.POWER, p)).glowing(current));
        }
        for (Map.Entry<Integer, String> e : new java.util.TreeMap<>(MODEL_SLOTS).entrySet()) {
            String m = e.getValue();
            boolean isDefault = m.equals("default");
            boolean current = isDefault ? g.model() == null : m.equals(g.model());
            List<String> lore = new ArrayList<>();
            lore.add(isDefault ? "Follows the server: " + shortModel(g.configModel()) : switch (m) {
                case "claude-haiku-5-5" -> "Quick and light.";
                case "claude-opus-5-5" -> "The deepest, slowest mind.";
                default -> "Balanced: the usual mind.";
            });
            if (isDefault) lore.add("(set in Server config)");
            lore.add(current ? "It thinks with this now." : "Click to think with it.");
            Material mat = isDefault ? Material.GRAY_DYE : switch (m) {
                case "claude-haiku-5-5" -> Material.FEATHER;
                case "claude-opus-5-5" -> Material.ENCHANTED_BOOK;
                default -> Material.BOOK;
            };
            icons.put(e.getKey(), Icon.of(mat, isDefault ? "Mind: server default" : "Mind: " + shortModel(m), VIOLET,
                    lore, Action.of(Kind.MODEL, m)).glowing(current));
        }
        for (Map.Entry<Integer, String> e : new java.util.TreeMap<>(EFFORT_SLOTS).entrySet()) {
            String f = e.getValue();
            boolean isDefault = f.equals("default");
            boolean current = isDefault ? g.effort() == null : f.equals(g.effort());
            List<String> lore = new ArrayList<>();
            lore.add(switch (f) {
                case "default" -> "Follows the server: " + g.configEffort();
                case "auto" -> "Harder when spoken to.";
                case "low" -> "Fastest replies.";
                case "medium" -> "More thought per reply.";
                default -> "Deepest thought, slowest.";
            });
            lore.add(current ? "It thinks this hard now." : "Click to use it.");
            Material mat = switch (f) {
                case "default" -> Material.GRAY_DYE;
                case "auto" -> Material.CLOCK;
                case "low" -> Material.LIGHT_BLUE_DYE;
                case "medium" -> Material.BLUE_DYE;
                default -> Material.PURPLE_DYE;
            };
            icons.put(e.getKey(), Icon.of(mat, isDefault ? "Effort: server default" : "Effort: " + f, AQUA, lore,
                    Action.of(Kind.EFFORT, f)).glowing(current));
        }
    }

    // ---- Bonds between gods -----------------------------------------------------------------------

    /** The preset buttons of each peer's row, by column: what the click sets. */
    static final List<String> BOND_PRESETS = List.of("allies", "friends", "neutral", "rivals", "enemies");
    /** Peers shown, one row each (rows 1 to 4). */
    static final int BOND_ROWS = 4;

    static int bondPeerSlot(int row) {
        return (row + 1) * 9 + 1;
    }

    static int bondPresetSlot(int row, int preset) {
        return (row + 1) * 9 + 3 + preset;
    }

    private static void bonds(Map<Integer, Icon> icons, State s) {
        List<MenuModel.Peer> peers = s.pantheon().peers() == null ? List.of() : s.pantheon().peers();
        if (peers.isEmpty()) {
            icons.put(22, Icon.of(Material.GRAY_DYE, "No other gods here", GRAY,
                    List.of("It rules its world alone, so", "it has no bonds to set."), Action.NONE));
            return;
        }
        for (int row = 0; row < Math.min(BOND_ROWS, peers.size()); row++) {
            MenuModel.Peer p = peers.get(row);
            List<String> lore = new ArrayList<>();
            lore.add("Bond: " + p.stance() + " (" + (p.score() > 0 ? "+" : "") + p.score() + ")");
            if (p.pact() != null && !p.pact().equals("none")) lore.add("Pact: " + p.pact());
            if (p.why() != null && !p.why().isBlank()) {
                lore.add("Lately:");
                lore.addAll(wrap(p.why(), 3));
            }
            lore.add("Left-click +20, right-click -20.");
            int rgb = p.score() >= 30 ? GREEN : p.score() <= -30 ? RED : WHITE;
            icons.put(bondPeerSlot(row), Icon.of(powerMaterial(p.power()), p.name(), rgb, lore,
                    Action.of(Kind.BOND, p.id() + "|+20")).glowing(p.score() >= 60 || p.score() <= -60));
            for (int i = 0; i < BOND_PRESETS.size(); i++) {
                String how = BOND_PRESETS.get(i);
                boolean now = switch (how) {
                    case "allies" -> p.stance().equals("ally");
                    case "friends" -> p.stance().equals("friend");
                    case "neutral" -> p.stance().equals("neutral");
                    case "rivals" -> p.stance().equals("rival");
                    default -> p.stance().equals("sworn enemy");
                };
                Material mat = switch (how) {
                    case "allies" -> Material.LIME_BANNER;
                    case "friends" -> Material.POPPY;
                    case "neutral" -> Material.WHITE_BANNER;
                    case "rivals" -> Material.IRON_SWORD;
                    default -> Material.NETHERITE_SWORD;
                };
                String text = switch (how) {
                    case "allies" -> "Sworn allies: they stand together.";
                    case "friends" -> "Warm and friendly to each other.";
                    case "neutral" -> "Neither friend nor foe.";
                    case "rivals" -> "They compete and needle each other.";
                    default -> "Bitter enemies: open hostility.";
                };
                int color = switch (how) {
                    case "allies", "friends" -> GREEN;
                    case "neutral" -> WHITE;
                    default -> RED;
                };
                icons.put(bondPresetSlot(row, i), Icon.of(mat, title(how) + " with " + clip(p.name(), 14), color,
                        List.of(text, "Both gods feel it at once.", now ? "They are this now." : "Click to make it so."),
                        Action.of(Kind.BOND, p.id() + "|" + how)).glowing(now));
            }
        }
    }

    private static String clip(String text, int max) {
        if (text == null) return "?";
        return text.length() > max ? text.substring(0, max - 1) + "…" : text;
    }

    // ---- Helpers -------------------------------------------------------------------------------

    private static Material wool(String archetype, int index) {
        return switch (archetype.toLowerCase(Locale.ROOT)) {
            case "benevolent" -> Material.YELLOW_WOOL;
            case "chill" -> Material.LIGHT_BLUE_WOOL;
            case "trickster" -> Material.LIME_WOOL;
            case "evil" -> Material.RED_WOOL;
            case "wrathful" -> Material.ORANGE_WOOL;
            case "cosmic" -> Material.PURPLE_WOOL;
            default -> List.of(Material.PINK_WOOL, Material.CYAN_WOOL, Material.MAGENTA_WOOL, Material.GREEN_WOOL,
                    Material.BLUE_WOOL, Material.BROWN_WOOL, Material.LIGHT_GRAY_WOOL).get(index % 7);
        };
    }

    static String title(String key) {
        if (key == null || key.isEmpty()) return "";
        return Character.toUpperCase(key.charAt(0)) + key.substring(1).replace('_', ' ').replace('-', ' ');
    }

    private static List<String> lore(String line, String warning) {
        return warning == null ? List.of(line) : List.of(line, warning);
    }

    static List<String> wrap(String text) {
        return wrap(text, 6);
    }

    /** Splits text into lore lines of about 36 characters, at most maxLines (the last one ends in …). */
    static List<String> wrap(String text, int maxLines) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) continue;
            if (line.length() > 0 && line.length() + 1 + word.length() > 36) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        if (out.size() > maxLines) {
            List<String> cut = new ArrayList<>(out.subList(0, maxLines));
            String last = cut.get(maxLines - 1);
            cut.set(maxLines - 1, (last.length() > 34 ? last.substring(0, 34).trim() : last) + " …");
            return cut;
        }
        return out;
    }
}
