package net.bigyous.gptgodmc.gui;

import java.util.List;
import java.util.Map;

import org.bukkit.Material;

import net.bigyous.gptgodmc.god.vessel.VesselMood;

/**
 * The God Manager's data: pages, icons and what clicking them means. Pure (no server), so the layout
 * and click decisions can be unit tested and previewed offline.
 */
public final class MenuModel {
    public static final int ROWS = 6;
    public static final int SIZE = ROWS * 9;
    /**
     * Bottom row: back (to the page's parent), home (to the hub, on pages two or more levels down), close,
     * and the confirm/cancel pair when a dangerous click waits for a second one.
     */
    public static final int BACK_SLOT = 45;
    public static final int CONFIRM_SLOT = 47;
    public static final int HOME_SLOT = 48;
    public static final int CLOSE_SLOT = 49;
    public static final int CANCEL_SLOT = 51;
    public static final int HEADER_SLOT = 4;

    private MenuModel() {
    }

    /**
     * The pages, as a tree: the HUB (every god, and what applies to the whole server) at the root, each
     * god's own page (MAIN) below it, and that god's pages below that.
     */
    public enum Page {
        /** The root: every god, Create a god, Quick switches, Server config. */
        HUB("God Manager", null, true),
        /** One god's own page. */
        MAIN("God", HUB, false),
        PERSONALITY("Personality", MAIN, false),
        DIALS("Nature", MAIN, false),
        MIGHT("Might & Mind", MAIN, false),
        MOOD("Mood & heart", MAIN, false),
        PRESENCE("Presence", MAIN, false),
        LOOKS("Looks", MAIN, false),
        VOICE("Voice", MAIN, false),
        COMMAND("Commands", MAIN, false),
        PLAYERS("Players", MAIN, false),
        PLAYER("Player", PLAYERS, false),
        /** How this god stands with the other gods of its world (only when it shares one). */
        BONDS("Bonds", MAIN, false),
        CREATE("New god", HUB, true),
        SETTINGS("Quick switches", HUB, true),
        ADVANCED("Server config", HUB, true);

        public final String title;
        /** Where Back leads (null for the hub). */
        public final Page parent;
        /** True for pages about the whole server rather than one god. */
        public final boolean serverWide;

        Page(String title, Page parent, boolean serverWide) {
            this.title = title;
            this.parent = parent;
            this.serverWide = serverWide;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** Clicks from the hub: 0 for the hub, 1 for a god's page or a server page, and so on. */
        public int depth() {
            return parent == null ? 0 : parent.depth() + 1;
        }
    }

    /** What a click does. */
    public enum Kind {
        NONE, OPEN, CLOSE, CONFIRM, CANCEL_CONFIRM,
        WAKE, TOGGLE_PAUSE,
        PERSONA, MOOD,
        SUMMON, FLY_TO, FOLLOW, VANISH, TRUE_FORM, END_TRUE_FORM,
        DELIVER, PRESET, CANCEL_COMMAND,
        TOGGLE_SETTING,
        SELECT_PLAYER, PLAYERS_PAGE, NAME_PLAYER, FAVOR, ADD_NOTE, ADD_DEED, CLEAR_NOTES, CLEAR_DEEDS,
        FORGET_PLAYER, DELETE_NOTE, DELETE_DEED,
        LOOK, CALM_HEART, DIAL,
        VOICE, VOICE_EFFECT, VOICE_SAMPLE,
        TAB, TUNE,
        SELECT_GOD, CREATE_GOD, REMOVE_GOD, POWER, MODEL, EFFORT,
        TOGGLE_DORMANT, BOND,
        DRAFT_ARCHETYPE, DRAFT_POWER, DRAFT_NAME, DRAFT_NO_NAME, DRAFT_CREATE
    }

    /** One god of the world, as the Pantheon page lists it. */
    public record Deity(String id, String name, String epithet, String archetype, String power, String state,
            boolean current, boolean removable) {
    }

    /**
     * The gods sharing this world, and the edited god's might and mind.
     *
     * @param model  its own model, or null when it follows the server-wide one (configModel)
     * @param effort its own effort, or null when it follows configEffort
     */
    public record Gods(List<Deity> list, String power, String model, String effort, String configModel,
            String configEffort, boolean removable, List<Peer> peers) {
        public static final Gods NONE = new Gods(List.of(), "supreme", null, null, "claude-sonnet-5-5", "auto",
                false);

        public Gods(List<Deity> list, String power, String model, String effort, String configModel,
                String configEffort, boolean removable) {
            this(list, power, model, effort, configModel, configEffort, removable, List.of());
        }

        public Gods withPeers(List<Peer> now) {
            return new Gods(list, power, model, effort, configModel, configEffort, removable, now);
        }
    }

    /**
     * Another god of the same world, as the Bonds page shows it.
     *
     * @param score  -100..100
     * @param stance sworn enemy | rival | wary | neutral | cordial | friend | ally
     * @param pact   none | alliance | truce | war
     * @param why    the last reason the bond moved, or null
     */
    public record Peer(String id, String name, String power, int score, String stance, String pact, String why) {
    }

    /** The Advanced page: the tab shown and the current value of every setting on it (by config key). */
    public record Tuning(String tab, Map<String, String> values) {
    }

    /** The god's voice: the TTS engine, the Kokoro voice and the effect (auto = by its mood). */
    public record VoiceNow(String engine, String voice, String effect) {
    }

    /** One personality dial of the living god: its value now and where its archetype starts it (0..10). */
    public record Dial(String key, int value, int base) {
    }

    /**
     * One click's meaning.
     *
     * @param arg     the page id, archetype, mood, preset id, command number or config key
     * @param confirm when not null, the first click only asks (this text) and a second click on the
     *                confirm icon carries it out
     */
    public record Action(Kind kind, String arg, String confirm) {
        public static final Action NONE = new Action(Kind.NONE, null, null);

        public static Action of(Kind kind, String arg) {
            return new Action(kind, arg, null);
        }

        public static Action open(Page page) {
            return new Action(Kind.OPEN, page.id(), null);
        }

        public Action confirming(String prompt) {
            return new Action(kind, arg, prompt);
        }
    }

    /**
     * One slot's icon.
     *
     * @param rgb        colour of the name
     * @param viewerHead show the viewer's own head (material is PLAYER_HEAD)
     * @param owner      show this player's head instead (null = none)
     * @param art        the painted icon in the plugin's resource pack ({@link MenuIcons}), shown only to
     *                   players who loaded it; null = the vanilla item for everyone
     */
    public record Icon(Material material, String name, int rgb, List<String> lore, boolean glow, Action action,
            boolean viewerHead, String owner, String art) {
        public static Icon of(Material material, String name, int rgb, List<String> lore, Action action) {
            return new Icon(material, name, rgb, lore, false, action, false, null, null);
        }

        public Icon glowing(boolean on) {
            return new Icon(material, name, rgb, lore, on, action, viewerHead, owner, art);
        }

        public Icon head() {
            return new Icon(Material.PLAYER_HEAD, name, rgb, lore, glow, action, true, null, art);
        }

        public Icon headOf(String player) {
            return new Icon(Material.PLAYER_HEAD, name, rgb, lore, glow, action, false, player, art);
        }

        /** Shows the painted icon with this key to players who have the pack. */
        public Icon art(String key) {
            return new Icon(material, name, rgb, lore, glow, action, viewerHead, owner, key);
        }
    }

    /** One remembered note or deed, with how long ago it was. */
    public record Entry(long at, String text, String age) {
    }

    /**
     * What the god remembers about one mortal, as the Players pages show it.
     *
     * @param notes newest first
     * @param deeds newest first
     * @param seen  how long ago they were last seen ("3h"), or null
     */
    public record Mortal(String name, int favor, boolean online, String title, String summary, List<Entry> notes,
            List<Entry> deeds, int offerings, String seen) {
    }

    /** A laid-out page: icons by slot (empty slots are absent). */
    public record Menu(Page page, String title, Map<Integer, Icon> icons) {
        public Icon at(int slot) {
            return icons.get(slot);
        }
    }

    /** A god about to be created: its name (null = it names itself), archetype and power. */
    public record Draft(String name, String archetype, String power) {
    }

    public record Archetype(String key, String description) {
    }

    /** A pending binding command as the menu lists it. */
    public record Pending(int number, String text, boolean standing, String status) {
    }

    /** A boolean config option the Settings page can flip. */
    public record Setting(String key, String label, boolean fallback, String description) {
    }

    /** A one-click binding command on the Commands page. */
    public record Preset(String id, Material material, String name, String command, boolean confirm) {
    }

    /** Everything the layout needs to know about the god, gathered on the server thread. */
    public record State(String godName, String epithet, String archetype, boolean awake, boolean paused,
            boolean hasBody, boolean trueForm, VesselMood mood, List<Archetype> archetypes, List<Pending> pending,
            Map<String, Boolean> settings, String realm, Action confirm, List<Mortal> mortals, Mortal selected,
            int listPage, String look, List<String> heart, List<Dial> dials, VoiceNow voice, Tuning tuning,
            Gods gods, Draft draft, List<String> aware) {
        public State(String godName, String epithet, String archetype, boolean awake, boolean paused,
                boolean hasBody, boolean trueForm, VesselMood mood, List<Archetype> archetypes, List<Pending> pending,
                Map<String, Boolean> settings, String realm, Action confirm, List<Mortal> mortals, Mortal selected,
                int listPage, String look, List<String> heart, List<Dial> dials, VoiceNow voice, Tuning tuning,
                Gods gods, Draft draft) {
            this(godName, epithet, archetype, awake, paused, hasBody, trueForm, mood, archetypes, pending, settings,
                    realm, confirm, mortals, selected, listPage, look, heart, dials, voice, tuning, gods, draft, null);
        }

        /** What moved its heart lately, the sky and the memory that last came back to it (god/Awareness). */
        public State withAware(List<String> now) {
            return new State(godName, epithet, archetype, awake, paused, hasBody, trueForm, mood, archetypes, pending,
                    settings, realm, confirm, mortals, selected, listPage, look, heart, dials, voice, tuning, gods,
                    draft, now);
        }

        public State(String godName, String epithet, String archetype, boolean awake, boolean paused,
                boolean hasBody, boolean trueForm, VesselMood mood, List<Archetype> archetypes, List<Pending> pending,
                Map<String, Boolean> settings, String realm, Action confirm, List<Mortal> mortals, Mortal selected,
                int listPage, String look, List<String> heart, List<Dial> dials, VoiceNow voice, Tuning tuning,
                Gods gods) {
            this(godName, epithet, archetype, awake, paused, hasBody, trueForm, mood, archetypes, pending, settings,
                    realm, confirm, mortals, selected, listPage, look, heart, dials, voice, tuning, gods, null);
        }

        /** The god being made on the New god page. */
        public State withDraft(Draft now) {
            return new State(godName, epithet, archetype, awake, paused, hasBody, trueForm, mood, archetypes, pending,
                    settings, realm, confirm, mortals, selected, listPage, look, heart, dials, voice, tuning, gods,
                    now, aware);
        }

        public Draft draftOrDefault() {
            return draft == null ? new Draft(null, "random", "greater") : draft;
        }

        public State(String godName, String epithet, String archetype, boolean awake, boolean paused,
                boolean hasBody, boolean trueForm, VesselMood mood, List<Archetype> archetypes, List<Pending> pending,
                Map<String, Boolean> settings, String realm, Action confirm, List<Mortal> mortals, Mortal selected,
                int listPage, String look, List<String> heart, List<Dial> dials, VoiceNow voice, Tuning tuning) {
            this(godName, epithet, archetype, awake, paused, hasBody, trueForm, mood, archetypes, pending, settings,
                    realm, confirm, mortals, selected, listPage, look, heart, dials, voice, tuning, null);
        }

        /** The gods of this world and the edited god's power, model and effort. */
        public State withGods(Gods now) {
            return new State(godName, epithet, archetype, awake, paused, hasBody, trueForm, mood, archetypes, pending,
                    settings, realm, confirm, mortals, selected, listPage, look, heart, dials, voice, tuning, now,
                    draft, aware);
        }

        /** Never null, for the layout. */
        public Gods pantheon() {
            return gods == null ? Gods.NONE : gods;
        }
        public State(String godName, String epithet, String archetype, boolean awake, boolean paused,
                boolean hasBody, boolean trueForm, VesselMood mood, List<Archetype> archetypes, List<Pending> pending,
                Map<String, Boolean> settings, String realm, Action confirm, List<Mortal> mortals, Mortal selected,
                int listPage, String look, List<String> heart, List<Dial> dials, VoiceNow voice) {
            this(godName, epithet, archetype, awake, paused, hasBody, trueForm, mood, archetypes, pending, settings,
                    realm, confirm, mortals, selected, listPage, look, heart, dials, voice, null);
        }

        /** The Advanced page's tab and values. */
        public State withTuning(Tuning now) {
            return new State(godName, epithet, archetype, awake, paused, hasBody, trueForm, mood, archetypes, pending,
                    settings, realm, confirm, mortals, selected, listPage, look, heart, dials, voice, now, gods);
        }

        public State(String godName, String epithet, String archetype, boolean awake, boolean paused,
                boolean hasBody, boolean trueForm, VesselMood mood, List<Archetype> archetypes, List<Pending> pending,
                Map<String, Boolean> settings, String realm, Action confirm, List<Mortal> mortals, Mortal selected,
                int listPage, String look, List<String> heart, List<Dial> dials) {
            this(godName, epithet, archetype, awake, paused, hasBody, trueForm, mood, archetypes, pending, settings,
                    realm, confirm, mortals, selected, listPage, look, heart, dials, null);
        }

        /** The god's voice, for the Voice page. */
        public State withVoice(VoiceNow now) {
            return new State(godName, epithet, archetype, awake, paused, hasBody, trueForm, mood, archetypes, pending,
                    settings, realm, confirm, mortals, selected, listPage, look, heart, dials, now, tuning, gods, draft,
                    aware);
        }

        public State(String godName, String epithet, String archetype, boolean awake, boolean paused,
                boolean hasBody, boolean trueForm, VesselMood mood, List<Archetype> archetypes, List<Pending> pending,
                Map<String, Boolean> settings, String realm, Action confirm, List<Mortal> mortals, Mortal selected,
                int listPage, String look, List<String> heart) {
            this(godName, epithet, archetype, awake, paused, hasBody, trueForm, mood, archetypes, pending, settings,
                    realm, confirm, mortals, selected, listPage, look, heart, List.of());
        }

        /** The living god's personality dials, for the Nature page. */
        public State withDials(List<Dial> now) {
            return new State(godName, epithet, archetype, awake, paused, hasBody, trueForm, mood, archetypes, pending,
                    settings, realm, confirm, mortals, selected, listPage, look, heart, now, voice, tuning, gods, draft,
                    aware);
        }

        public State(String godName, String epithet, String archetype, boolean awake, boolean paused,
                boolean hasBody, boolean trueForm, VesselMood mood, List<Archetype> archetypes, List<Pending> pending,
                Map<String, Boolean> settings, String realm, Action confirm, List<Mortal> mortals, Mortal selected,
                int listPage) {
            this(godName, epithet, archetype, awake, paused, hasBody, trueForm, mood, archetypes, pending, settings,
                    realm, confirm, mortals, selected, listPage, "auto", List.of());
        }

        /** The configured vessel look (shade, radiant, infernal, void or auto) and the heart's summary lines. */
        public State withSoul(String lookNow, List<String> heartNow) {
            return new State(godName, epithet, archetype, awake, paused, hasBody, trueForm, mood, archetypes, pending,
                    settings, realm, confirm, mortals, selected, listPage, lookNow, heartNow, dials, voice,
                    tuning, gods, draft, aware);
        }

        public State(String godName, String epithet, String archetype, boolean awake, boolean paused,
                boolean hasBody, boolean trueForm, VesselMood mood, List<Archetype> archetypes, List<Pending> pending,
                Map<String, Boolean> settings, String realm, Action confirm) {
            this(godName, epithet, archetype, awake, paused, hasBody, trueForm, mood, archetypes, pending, settings,
                    realm, confirm, List.of(), null, 0);
        }

        /** The mortals for the Players pages (online first), the one opened, and the list page shown. */
        public State withPlayers(List<Mortal> list, Mortal open, int page) {
            return new State(godName, epithet, archetype, awake, paused, hasBody, trueForm, mood, archetypes, pending,
                    settings, realm, confirm, list, open, page, look, heart, dials, voice, tuning, gods, draft, aware);
        }

        public boolean born() {
            return godName != null;
        }

        public boolean setting(String key, boolean fallback) {
            Boolean v = settings.get(key);
            return v == null ? fallback : v;
        }

        public State withConfirm(Action a) {
            return new State(godName, epithet, archetype, awake, paused, hasBody, trueForm, mood, archetypes, pending,
                    settings, realm, a, mortals, selected, listPage, look, heart, dials, voice, tuning, gods, draft,
                    aware);
        }
    }

    /**
     * The result of one click.
     *
     * @param run     an action to carry out now, or null
     * @param open    the page to show next, or null to stay
     * @param confirm the click now waiting for confirmation (null = none)
     */
    public record Decision(Action run, Page open, Action confirm) {
    }
}
